#!/usr/bin/env bash
# =============================================================================
# Intégrate · PWA en Cloudflare Pages (ADR-0024, ADR-0031)
# -----------------------------------------------------------------------------
# Uso (desde la raíz del repo):
#   bash infra/scripts/pages.sh deploy produccion            # compila y publica en https://integrate-demo.pages.dev
#   bash infra/scripts/pages.sh deploy pruebas               # compila y publica en https://pruebas.integrate-demo.pages.dev
#   bash infra/scripts/pages.sh set-origin produccion <url>  # destino del proxy /api de producción (secreto API_ORIGIN)
#   bash infra/scripts/pages.sh set-origin pruebas <url>     # destino del proxy /api de la vista previa
#
# Lee infra/env/.env.cloudflare (CLOUDFLARE_API_TOKEN y CLOUDFLARE_ACCOUNT_ID) y, si existe, la llave pública de Google
# Maps de infra/env/.env.google-maps (VITE_GOOGLE_MAPS_EMBED_KEY). Nada se imprime.
# Compilar exige VITE_API_BASE_URL=/api y, en Git Bash, MSYS_NO_PATHCONV=1 (si no, /api se vuelve una ruta de Windows).
# =============================================================================
set -euo pipefail

PROJECT="${PAGES_PROJECT:-integrate-demo}"
CF_ENV="${CLOUDFLARE_ENV_FILE:-infra/env/.env.cloudflare}"
MAPS_ENV="${GOOGLE_MAPS_ENV_FILE:-infra/env/.env.google-maps}"

[ -f "$CF_ENV" ] || { echo "No existe $CF_ENV (CLOUDFLARE_API_TOKEN y CLOUDFLARE_ACCOUNT_ID)." >&2; exit 2; }
read_var() { sed -n "s/^$1=//p" "$2" | head -1 | tr -d '\r"[:space:]'; }
export CLOUDFLARE_API_TOKEN="$(read_var CLOUDFLARE_API_TOKEN "$CF_ENV")" CLOUDFLARE_ACCOUNT_ID="$(read_var CLOUDFLARE_ACCOUNT_ID "$CF_ENV")" WRANGLER_SEND_METRICS=false

target_branch() { case "${1:-}" in produccion) echo main;; pruebas) echo pruebas;; *) echo "Entorno inválido: use 'produccion' o 'pruebas'." >&2; exit 2;; esac; }

cmd="${1:-}"
case "$cmd" in
  deploy)
    branch="$(target_branch "${2:-}")"
    key=""; [ -f "$MAPS_ENV" ] && key="$(read_var VITE_GOOGLE_MAPS_EMBED_KEY "$MAPS_ENV")"
    echo "== compilando (entorno: ${2}, rama de Pages: $branch)"
    ( cd frontend-pwa && MSYS_NO_PATHCONV=1 VITE_API_BASE_URL=/api VITE_GOOGLE_MAPS_EMBED_KEY="$key" npm run -s build )
    if grep -q "Program Files" frontend-pwa/dist/assets/index-*.js 2>/dev/null; then
      echo "El bundle contiene la ruta de Git (Program Files): falta MSYS_NO_PATHCONV=1. No se publica." >&2; exit 1
    fi
    echo "== publicando"
    ( cd frontend-pwa && npx --yes wrangler pages deploy dist --project-name "$PROJECT" --branch "$branch" --commit-dirty=true ) | tail -3
    ;;
  set-origin)
    env_name="${2:-}"; url="${3:-}"; target_branch "$env_name" >/dev/null
    case "$url" in https://*) ;; *) echo "La URL debe empezar por https://" >&2; exit 2;; esac
    if [ "$env_name" = "produccion" ]; then
      printf '%s' "$url" | ( cd frontend-pwa && npx --yes wrangler pages secret put API_ORIGIN --project-name "$PROJECT" ) | tail -2
    else
      # `wrangler pages secret put` solo escribe producción: la vista previa se fija con la API de Cloudflare.
      body="$(URL="$url" python -c "import json,os; print(json.dumps({'deployment_configs':{'preview':{'env_vars':{'API_ORIGIN':{'type':'secret_text','value':os.environ['URL']}}}}}))")"
      code="$(curl -s -m 30 -o /dev/null -w '%{http_code}' -X PATCH -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" -H 'Content-Type: application/json' \
        -d "$body" "https://api.cloudflare.com/client/v4/accounts/$CLOUDFLARE_ACCOUNT_ID/pages/projects/$PROJECT")"
      [ "$code" = "200" ] && echo "API_ORIGIN de la vista previa actualizado." || { echo "Cloudflare respondió HTTP $code." >&2; exit 1; }
    fi
    echo "Hace falta un despliegue nuevo para que la función tome el valor."
    ;;
  *)
    sed -n '3,10p' "$0"; exit 2 ;;
esac
