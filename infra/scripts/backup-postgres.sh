#!/usr/bin/env bash
set -euo pipefail
umask 077

environment=${1:-}
case "$environment" in
  pruebas|produccion) ;;
  *) echo 'Uso: backup-postgres.sh pruebas|produccion' >&2; exit 2 ;;
esac

: "${BACKUP_BUCKET:?Falta BACKUP_BUCKET}"
: "${BACKUP_AGE_RECIPIENT:?Falta BACKUP_AGE_RECIPIENT}"
: "${BACKUP_NOTIFY_OK_CONFIG:?Falta BACKUP_NOTIFY_OK_CONFIG}"
: "${BACKUP_NOTIFY_FAIL_CONFIG:?Falta BACKUP_NOTIFY_FAIL_CONFIG}"
: "${PGSERVICEFILE:?Falta PGSERVICEFILE}"
: "${PGPASSFILE:?Falta PGPASSFILE}"

stamp=$(date -u +%Y%m%dT%H%M%SZ)
phase=prepare
scratch=

notify() {
  curl --disable --fail --silent --output /dev/null --max-time 20 \
    --config "$1" >/dev/null 2>&1
}

finish() {
  result=$?
  trap - EXIT
  if [[ -n "$scratch" ]]; then
    rm -f -- "$scratch/$stamp.dump.age" "$scratch/$stamp.sha256"
    rmdir -- "$scratch"
  fi
  if (( result != 0 )); then
    printf 'backup_failed environment=%s snapshot=%s phase=%s exit=%s\n' "$environment" "$stamp" "$phase" "$result" >&2
    notify "$BACKUP_NOTIFY_FAIL_CONFIG" ||
      printf 'backup_alert_failed environment=%s snapshot=%s\n' "$environment" "$stamp" >&2
  fi
  exit "$result"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

scratch=$(mktemp -d)
archive="$stamp.dump.age"
destination="s3://$BACKUP_BUCKET/postgres/$environment/$stamp"
aws_args=()
if [[ -n "${BACKUP_S3_ENDPOINT:-}" ]]; then
  aws_args=(--endpoint-url "$BACKUP_S3_ENDPOINT")
fi

phase=dump_and_encrypt
pg_dump --no-password --format=custom --dbname="service=integrate-backup-$environment" 2>/dev/null |
  age --encrypt --recipient "$BACKUP_AGE_RECIPIENT" --output "$scratch/$archive" 2>/dev/null

phase=checksum
(cd "$scratch" && sha256sum "$archive" > "$stamp.sha256")

phase=upload_archive
aws "${aws_args[@]}" s3 cp "$scratch/$archive" "$destination.dump.age" --only-show-errors >/dev/null 2>&1
phase=upload_manifest
aws "${aws_args[@]}" s3 cp "$scratch/$stamp.sha256" "$destination.sha256" --only-show-errors >/dev/null 2>&1

phase=notify_success
notify "$BACKUP_NOTIFY_OK_CONFIG"
printf 'backup_published environment=%s snapshot=%s\n' "$environment" "$stamp"
