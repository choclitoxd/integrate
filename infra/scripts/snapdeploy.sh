#!/usr/bin/env bash
# =============================================================================
# Intégrate · Ayudante de la API de SnapDeploy (ADR-0026): cargar variables de golpe, despertar, estado, logs
# -----------------------------------------------------------------------------
# Uso (desde la raíz del repo):
#   export SNAPDEPLOY_TOKEN='sd_pat_...'     # token personal con alcance "deploy" (API Keys), SOLO en esta terminal
#   bash infra/scripts/snapdeploy.sh create            # crea el contenedor YA con las variables (sin construir aún)
#   bash infra/scripts/snapdeploy.sh env --dry-run     # muestra qué variables se enviarían (solo nombres)
#   bash infra/scripts/snapdeploy.sh env               # reemplaza TODAS las variables de un contenedor existente
#   bash infra/scripts/snapdeploy.sh wake              # despierta el contenedor sin abrir el navegador
#   bash infra/scripts/snapdeploy.sh status            # estado, URL y horas gastadas
#   bash infra/scripts/snapdeploy.sh logs [líneas]     # logs recientes de ejecución
# Opciones por entorno: SNAPDEPLOY_NAME (nombre del contenedor, por defecto integrate-api),
#   ENV_FILE (por defecto infra/env/.env.demo).
# SEC-DIR-004: el token nunca se escribe en disco ni se imprime; los valores de las variables tampoco.
# La API de SnapDeploy nunca devuelve valores ("(set)"), solo acepta los nuevos.
# =============================================================================
set -u
BASE="${SNAPDEPLOY_BASE:-https://snapdeploy.dev}"   # solo para pruebas locales
NAME="${SNAPDEPLOY_NAME:-integrate-api}"
ENV_FILE="${ENV_FILE:-infra/env/.env.demo}"
CMD="${1:-}"; shift || true

# Variables del .env.demo que NO son del backend en SnapDeploy (túnel/compose local) o ya vienen en la imagen.
SKIP='^(TUNNEL_TOKEN|API_IMAGE_TAG|DEMO_API_PORT|JAVA_TOOL_OPTIONS|SERVER_PORT|PORT|HOST)$'

[ -n "${CMD}" ] || { sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
[ -n "${SNAPDEPLOY_TOKEN:-}" ] || { [ "$CMD" = env ] && [ "${1:-}" = "--dry-run" ] || { echo "Falta SNAPDEPLOY_TOKEN (export SNAPDEPLOY_TOKEN='sd_pat_...')"; exit 2; }; }

HTTP=""; BODY=""
api() {  # api METHOD PATH [json-body]  -> deja el código en $HTTP y el cuerpo en $BODY (llamar SIN subshell)
  local m="$1" p="$2" b="${3:-}" out
  if [ -n "$b" ]; then
    out=$(printf '%s' "$b" | curl -s -w '\n%{http_code}' -X "$m" -H "X-API-Key: $SNAPDEPLOY_TOKEN" -H 'Content-Type: application/json' \
      -H 'X-SnapDeploy-Client: script/integrate' --data-binary @- "$BASE$p")
  else
    out=$(curl -s -w '\n%{http_code}' -X "$m" -H "X-API-Key: $SNAPDEPLOY_TOKEN" -H 'X-SnapDeploy-Client: script/integrate' "$BASE$p")
  fi
  HTTP="${out##*$'\n'}"; BODY="${out%$'\n'*}"
}
pyj() { PYTHONIOENCODING=utf-8 python -c "$@"; }

container_id() {  # resuelve el id por nombre con /bootstrap
  api GET /api/mobile/bootstrap
  [ "$HTTP" = 200 ] || { echo "No se pudo leer la cuenta (HTTP $HTTP). ¿Token válido, no vencido y con alcance deploy?" >&2; return 1; }
  printf '%s' "$BODY" | pyj "
import sys,json
d=json.load(sys.stdin); n='$NAME'
c=[x for x in d.get('containers',[]) if x.get('name')==n]
if not c: print('NOENCONTRADO:'+','.join(x.get('name','?') for x in d.get('containers',[])),file=sys.stderr); sys.exit(1)
print(c[0]['containerId'])
" || { echo "No hay un contenedor llamado '$NAME'. Cree el contenedor en el panel o use SNAPDEPLOY_NAME." >&2; return 1; }
}

build_env_json() {  # JSON {"NOMBRE":"valor"} desde el .env; omite vacías, comentarios y las de SKIP
  ENV_FILE="$ENV_FILE" SKIP="$SKIP" pyj "
import os,re,json,sys
skip=re.compile(os.environ['SKIP']); d={}
for l in open(os.environ['ENV_FILE'],encoding='utf-8-sig').read().splitlines():
    m=re.match(r'^([A-Z][A-Z0-9_]*)=(.*)$',l)
    if not m or skip.match(m.group(1)): continue
    v=m.group(2).strip()
    if len(v)>=2 and v[0]==v[-1] and v[0] in '\"\\'': v=v[1:-1]
    if v=='' : continue
    d[m.group(1)]=v
print(json.dumps({'environmentVariables':d}))
"
}

validate_env() {  # deja el JSON en $JSON y sale con error si faltan obligatorias o hay placeholders
    [ -f "$ENV_FILE" ] || { echo "No existe $ENV_FILE"; return 2; }
    JSON=$(build_env_json) || return 2
    printf '%s' "$JSON" | pyj "
import sys,json,re
d=json.load(sys.stdin)['environmentVariables']
bad=[k for k,v in d.items() if re.search(r'YOUR_|PLACEHOLDER|SAME_VALUE|<[^>]+>',v)]
print(f'{len(d)} variables listas para enviar:'); print('  '+', '.join(sorted(d)))
if bad: print('AVISO: parecen placeholders sin reemplazar ->',', '.join(sorted(bad))); sys.exit(3)
need=['DATABASE_URL','DATABASE_USERNAME','DATABASE_PASSWORD','FLYWAY_USER','FLYWAY_PASSWORD','JWT_SIGNING_KEY','JWT_SIGNING_KEY_ID','VOUCHER_SIGNING_KEY','VOUCHER_SIGNING_KEY_ID','SENSITIVE_DATA_KEY_V1','SENSITIVE_DATA_KEY_CURRENT_VERSION','FRONTEND_BASE_URL','PAYMENT_PROVIDER','AWS_ENDPOINT_URL','AWS_REGION','AWS_S3_BUCKET_NAME','AWS_S3_SENSITIVE_BUCKET_NAME','SMTP_HOST','SMTP_PORT','MAIL_FROM']
miss=[k for k in need if k not in d]
if miss: print('FALTAN obligatorias ->',', '.join(miss)); sys.exit(3)
" ; rc=$?; [ $rc -eq 0 ] || return $rc
}

case "$CMD" in
  create)
    validate_env || exit $?
    CREATE=$(printf '%s' "$JSON" | NAME="$NAME" pyj "
import sys,json,os
d=json.load(sys.stdin); d.update({'name':os.environ['NAME'],'image':'pending','memory':512}); print(json.dumps(d))")
    api POST /api/mobile/containers "$CREATE"; OUT="$BODY"
    case "$HTTP" in
      200|201) printf '%s' "$OUT" | pyj "
import sys,json
try:
    d=json.load(sys.stdin); print('OK: contenedor creado ->',d.get('name'),'| id:',d.get('containerId'),'| url:',d.get('url'))
except Exception: print('OK (respuesta no interpretable)')"
        echo "Siguiente paso, en el panel de SnapDeploy: abra el contenedor, enlace el repositorio de GitHub (rama main) con root directory 'backend-api' y despliegue.";;
      400) echo "HTTP 400: datos inválidos:"; printf '%s' "$OUT" | cut -c1-300; exit 1;;
      402) echo "HTTP 402: límite del plan."; printf '%s' "$OUT" | cut -c1-300; exit 1;;
      403) echo "HTTP 403: el token necesita alcance 'deploy'."; exit 1;;
      *) echo "HTTP $HTTP al crear el contenedor."; printf '%s' "$OUT" | cut -c1-300; exit 1;;
    esac ;;
  env)
    validate_env || exit $?
    [ "${1:-}" = "--dry-run" ] && { echo "(--dry-run: no se envió nada)"; exit 0; }
    CID=$(container_id) || exit 1
    api PUT "/api/mobile/containers/$CID/env" "$JSON" >/dev/null
    case "$HTTP" in 200|204) echo "OK: variables reemplazadas en '$NAME' (reinicio rodante en curso). Siga con: logs";;
      402) echo "HTTP 402: límite del plan (¿horas agotadas?)."; exit 1;; 403) echo "HTTP 403: el token necesita alcance 'deploy'."; exit 1;; *) echo "HTTP $HTTP al enviar las variables."; exit 1;; esac ;;
  wake)
    CID=$(container_id) || exit 1
    api POST "/api/mobile/containers/$CID/wake" >/dev/null
    case "$HTTP" in 200|202|204) echo "Despertando '$NAME'. Espere ~1 min más el arranque de la JVM y use: status";; 402) echo "HTTP 402: horas del mes agotadas."; exit 1;; *) echo "HTTP $HTTP al despertar."; exit 1;; esac ;;
  status)
    CID=$(container_id) || exit 1
    api GET "/api/mobile/containers/$CID"
    printf '%s' "$BODY" | pyj "
import sys,json
d=json.load(sys.stdin); print('contenedor:',d.get('name'),'| estado:',d.get('status'),'| url:',d.get('url'),'| puerto:',d.get('port'))" || echo "(respuesta no interpretable, HTTP $HTTP)"
    api GET /api/mobile/bootstrap
    printf '%s' "$BODY" | pyj "
import sys,json
u=json.load(sys.stdin).get('usage',{}); print('horas usadas:',u.get('hoursUsed'),'de',u.get('hoursLimit'),'| restantes:',u.get('remainingHours'))" 2>/dev/null ;;
  logs)
    CID=$(container_id) || exit 1
    api GET "/api/mobile/containers/$CID/logs?lines=${1:-80}"
    printf '%s' "$BODY" | pyj "
import sys,json
try:
    d=json.load(sys.stdin); L=d if isinstance(d,list) else d.get('logs',d)
    print('\n'.join(L) if isinstance(L,list) else L)
except Exception: print('(respuesta no interpretable)')" ;;
  *) echo "Comando desconocido: $CMD (env | wake | status | logs)"; exit 2 ;;
esac
