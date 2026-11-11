#!/usr/bin/env bash
# Ejecutar dentro de un contenedor desechable PostgreSQL Alpine con bash y age.
set -euo pipefail
umask 077

scripts=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
scratch=$(mktemp -d)
cd "$scratch"
export PGHOST=/var/run/postgresql
export PGUSER=postgres
export PGDATABASE=postgres
createdb -w integrate_synthetic
psql -X -w -d integrate_synthetic -v ON_ERROR_STOP=1 <<'SQL'
CREATE SCHEMA prueba;
CREATE TABLE prueba.evidencia (id integer PRIMARY KEY, nota text NOT NULL);
INSERT INTO prueba.evidencia VALUES (1, 'dato sintetico');
SQL
createdb -w -T template0 integrate_restore_simulacro

export PGSERVICEFILE="$scratch/pg_service.conf" PGPASSFILE="$scratch/pgpass"
printf '[integrate-backup-pruebas]\nhost=%s\nuser=%s\ndbname=integrate_synthetic\n' "$PGHOST" "$PGUSER" > "$PGSERVICEFILE"
touch "$PGPASSFILE"
age-keygen -o identity >/dev/null 2>&1
export BACKUP_AGE_RECIPIENT=$(age-keygen -y identity)
export BACKUP_BUCKET=simulacro BACKUP_NOTIFY_OK_CONFIG=ok BACKUP_NOTIFY_FAIL_CONFIG=failed
export RECOVERY_TEST_OBJECTS="$scratch/objects" RECOVERY_TEST_ALERTS="$scratch/alerts"
mkdir -p "$RECOVERY_TEST_OBJECTS" bin
cat > bin/aws <<'SH'
#!/usr/bin/env bash
set -eu
[[ "$1 $2" = 's3 cp' ]]
cp -- "$3" "$RECOVERY_TEST_OBJECTS/${4##*/}"
SH
cat > bin/curl <<'SH'
#!/usr/bin/env bash
printf '%s\n' "${@: -1}" >> "$RECOVERY_TEST_ALERTS"
SH
chmod 700 bin/aws bin/curl
export PATH="$scratch/bin:$PATH"

bash "$scripts/backup-postgres.sh" pruebas
[[ $(cat "$RECOVERY_TEST_ALERTS") = ok ]]
copies=("$RECOVERY_TEST_OBJECTS"/*.dump.age)
[[ ${#copies[@]} -eq 1 && -f "${copies[0]}" ]]
archive=${copies[0]}
manifest="${archive%.dump.age}.sha256"
bash "$scripts/restore-postgres.sh" "$archive" "$manifest" identity "$PGHOST" integrate_restore_simulacro
[[ $(psql -X -w -At -d integrate_restore_simulacro -c 'SELECT nota FROM prueba.evidencia WHERE id = 1') = 'dato sintetico' ]]

if bash "$scripts/restore-postgres.sh" "$archive" "$manifest" identity "$PGHOST" integrate_restore_simulacro; then
  echo 'El script aceptó un destino ocupado.' >&2
  exit 1
fi
printf 'corrupcion' >> "$archive"
if bash "$scripts/restore-postgres.sh" "$archive" "$manifest" identity "$PGHOST" integrate_restore_simulacro; then
  echo 'El script aceptó una copia corrupta.' >&2
  exit 1
fi

sed -i 's/dbname=integrate_synthetic/dbname=inexistente/' "$PGSERVICEFILE"
if bash "$scripts/backup-postgres.sh" pruebas; then
  echo 'El respaldo ocultó el fallo de pg_dump.' >&2
  exit 1
fi
[[ $(tail -n 1 "$RECOVERY_TEST_ALERTS") = failed ]]
printf '%s\n' 'OK: restauración, rechazo de destino ocupado y de copia corrupta, y señal de fallo.'
