#!/usr/bin/env bash
set -euo pipefail
umask 077

if [[ $# -ne 5 ]]; then
  echo 'Uso: restore-postgres.sh COPIA.dump.age MANIFIESTO.sha256 IDENTIDAD_AGE SOCKET_LOCAL DB_AISLADA' >&2
  exit 2
fi
archive=$1
manifest=$2
identity=$3
socket=$4
database=$5
filename=${archive##*/}
if [[ ! "$filename" =~ ^[0-9]{8}T[0-9]{6}Z\.dump\.age$ || ! "$socket" = /* ||
      ! "$database" =~ ^integrate_restore_[a-z0-9_]{1,40}$ || ! -d "$socket" ]]; then
  echo 'Se requiere una copia fechada, un socket Unix local y una base integrate_restore_* nueva.' >&2
  exit 2
fi

read -r expected name < "$manifest"
actual=$(sha256sum -- "$archive")
if [[ ! "$expected" =~ ^[a-f0-9]{64}$ || "$name" != "$filename" || "${actual%% *}" != "$expected" ]]; then
  echo 'La copia cifrada no coincide con su manifiesto.' >&2
  exit 1
fi

# El destino explícito prevalece sobre cualquier configuración libpq del operador.
unset PGSERVICE PGHOSTADDR PGOPTIONS
export PGHOST="$socket" PGDATABASE="$database"
existing=$(psql -X -w -At -v ON_ERROR_STOP=1 -c \
  "SELECT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname NOT IN ('public', 'information_schema') AND nspname NOT LIKE 'pg_%')
   OR EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public')
   OR EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname = 'public')
   OR EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace WHERE n.nspname = 'public');")
if [[ "$existing" != f ]]; then
  echo 'El destino debe ser una base vacía creada para el simulacro.' >&2
  exit 1
fi

# Descifrar completo antes de escribir en el destino. TMPDIR debe ser privado y cifrado o tmpfs.
scratch=$(mktemp -d)
trap 'rm -f -- "$scratch/archive.dump"; rmdir -- "$scratch"' EXIT
age --decrypt --identity "$identity" --output "$scratch/archive.dump" "$archive"
pg_restore --no-password --single-transaction --exit-on-error --dbname="$database" "$scratch/archive.dump"
printf 'restore_loaded snapshot=%s database=%s\n' "$filename" "$database"
