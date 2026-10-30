#!/usr/bin/env bash
# =============================================================================
# Intégrate · Producción en Hetzner Cloud (ADR-0027, ADR-0031)
# -----------------------------------------------------------------------------
# Uso (desde la raíz del repo):
#   bash infra/scripts/hetzner.sh status            # servidor, contenedores, memoria y estado HTTPS
#   bash infra/scripts/hetzner.sh deploy            # construye la imagen, la envía por SSH, reinicia solo la API y espera la salud
#   bash infra/scripts/hetzner.sh rollback          # vuelve a la imagen anterior (la que había antes del último deploy)
#   bash infra/scripts/hetzner.sh logs [líneas]     # logs recientes de la API
#
# Lee infra/env/.env.hetzner (local, ignorado por Git): HCLOUD_TOKEN=...  y, opcional, HETZNER_HOST=<ip>.
# Sin HETZNER_HOST, busca el servidor `integrate-api` con el token. Llave SSH: HETZNER_SSH_KEY (por defecto ~/.ssh/id_ed25519).
# SEC-DIR-004: nunca imprime el token ni las variables de la API (viven en ~/integrate/.env del servidor).
# =============================================================================
set -euo pipefail

ENV_FILE="${HETZNER_ENV:-infra/env/.env.hetzner}"
SSH_KEY="${HETZNER_SSH_KEY:-$HOME/.ssh/id_ed25519}"
SERVER_NAME="${HETZNER_SERVER_NAME:-integrate-api}"
SSH_USER="${HETZNER_SSH_USER:-integrate}"
IMAGE="integrate-api"

[ -f "$ENV_FILE" ] || { echo "No existe $ENV_FILE (HCLOUD_TOKEN=...). Ver docs/despliegue/demo-gratuita.md (§1)" >&2; exit 2; }
HCLOUD_TOKEN="$(sed -n 's/^HCLOUD_TOKEN=//p' "$ENV_FILE" | tr -d '\r"[:space:]')"
HETZNER_HOST="${HETZNER_HOST:-$(sed -n 's/^HETZNER_HOST=//p' "$ENV_FILE" | tr -d '\r"[:space:]')}"

resolve_host() {
  [ -n "$HETZNER_HOST" ] && return 0
  [ -n "$HCLOUD_TOKEN" ] || { echo "Falta HCLOUD_TOKEN o HETZNER_HOST en $ENV_FILE" >&2; exit 2; }
  HETZNER_HOST="$(curl -s -m 30 -H "Authorization: Bearer $HCLOUD_TOKEN" "https://api.hetzner.cloud/v1/servers?name=$SERVER_NAME" \
    | python -c "import sys,json; s=json.load(sys.stdin).get('servers',[]); print(s[0]['public_net']['ipv4']['ip'] if s else '')")"
  [ -n "$HETZNER_HOST" ] || { echo "No encontré el servidor '$SERVER_NAME' en Hetzner." >&2; exit 1; }
}

ssh_run() { ssh -i "$SSH_KEY" -o BatchMode=yes -o StrictHostKeyChecking=accept-new "$SSH_USER@$HETZNER_HOST" "$@"; }

public_url() { echo "https://$(printf '%s' "$HETZNER_HOST" | tr '.' '-').sslip.io"; }

wait_healthy() {
  local url code; url="$(public_url)/api/v1/privacy-policies/current"
  for i in $(seq 1 40); do
    code="$(curl -s -m 15 -o /dev/null -w '%{http_code}' "$url" || true)"
    if [ "$code" = "200" ]; then echo "API sana ($url → 200)"; return 0; fi
    echo "esperando la API ($i/40, HTTP ${code:-000})"; sleep 6
  done
  echo "La API no respondió 200 a tiempo: revise 'logs' o use 'rollback'." >&2; return 1
}

cmd="${1:-}"
case "$cmd" in
  status)
    resolve_host
    echo "Servidor: $HETZNER_HOST   URL: $(public_url)"
    ssh_run 'cd ~/integrate && docker compose -f compose.vps.yml ps && docker stats --no-stream --format "{{.Name}} {{.MemUsage}}" && free -m | sed -n 2,3p'
    echo "HTTP: $(curl -s -m 15 -o /dev/null -w '%{http_code}' "$(public_url)/api/v1/privacy-policies/current")"
    ;;
  deploy)
    resolve_host
    echo "== construyendo la imagen"
    docker build -t "$IMAGE:vps" backend-api
    echo "== guardando la imagen actual como 'previous' (para rollback)"
    ssh_run "docker tag $IMAGE:vps $IMAGE:previous 2>/dev/null || true"
    echo "== enviando la imagen nueva"
    docker save "$IMAGE:vps" | gzip -1 | ssh_run 'gunzip | docker load'
    echo "== reiniciando solo la API"
    ssh_run 'cd ~/integrate && docker compose -f compose.vps.yml up -d api'
    wait_healthy
    ;;
  rollback)
    resolve_host
    ssh_run "docker image inspect $IMAGE:previous >/dev/null 2>&1" || { echo "No hay imagen 'previous' en el servidor." >&2; exit 1; }
    ssh_run "docker tag $IMAGE:previous $IMAGE:vps && cd ~/integrate && docker compose -f compose.vps.yml up -d api"
    wait_healthy
    ;;
  logs)
    resolve_host
    ssh_run "docker logs --tail ${2:-80} integrate-api-1 2>&1"
    ;;
  *)
    sed -n '3,12p' "$0"; exit 2 ;;
esac
