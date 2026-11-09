#!/usr/bin/env bash
set -euo pipefail
umask 077

if [[ $# -ne 5 ]]; then
  echo 'Uso: publicar-politica.sh pruebas|produccion VERSION TITULO URL_HTTPS ARCHIVO_APROBADO' >&2
  exit 2
fi
environment=$1 version=$2 title=$3 url=$4 approved=$5
if [[ ! "$environment" =~ ^(pruebas|produccion)$ || ! "$version" =~ ^v[0-9]+\.[0-9]+$ ||
      -z "$title" || ! "$url" =~ ^https://[^/@]+/ || ! -s "$approved" ]]; then
  echo 'Se requiere el entorno explícito, versión vN.N, título, URL HTTPS y el archivo aprobado.' >&2
  exit 2
fi

: "${PGSERVICEFILE:?Falta PGSERVICEFILE}"
: "${PGPASSFILE:?Falta PGPASSFILE}"
copy=$(mktemp)
trap 'rm -f -- "$copy"' EXIT
curl --disable --fail --silent --show-error --proto '=https' --max-time 60 --max-filesize 10485760 \
  --output "$copy" -- "$url"
if ! cmp --silent -- "$approved" "$copy"; then
  echo 'El contenido de la URL difiere del archivo aprobado.' >&2
  exit 1
fi
hash=$(sha256sum < "$copy")
psql -X -w -d "service=integrate-policy-$environment" -v ON_ERROR_STOP=1 \
  -v version="$version" -v title="$title" -v url="$url" -v digest="${hash%% *}" <<'SQL'
BEGIN;
LOCK TABLE auth.privacy_policies IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE publication (version text, title text, url text, digest text) ON COMMIT DROP;
INSERT INTO publication VALUES (:'version', :'title', :'url', :'digest');
DO $publish$
BEGIN
    IF EXISTS (SELECT 1 FROM auth.privacy_policies p JOIN publication n USING (version)) THEN
        IF EXISTS (
            SELECT 1 FROM auth.privacy_policies p JOIN publication n USING (version)
             WHERE p.is_current AND p.document_url = n.url AND p.content_sha256 = n.digest AND p.title = n.title
        ) THEN
            RETURN;
        END IF;
        RAISE EXCEPTION 'Use una versión nueva. Las versiones publicadas son inmutables.';
    END IF;
    UPDATE auth.privacy_policies SET is_current = false WHERE is_current;
    INSERT INTO auth.privacy_policies (version, title, document_url, content_sha256, effective_from, is_current)
    SELECT version, title, url, digest, now(), true FROM publication;
END
$publish$;
COMMIT;
SQL
