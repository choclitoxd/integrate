#!/usr/bin/env bash
# =============================================================================
# Intégrate · Ayudante de la API de SnapDeploy (ADR-0026): cargar variables de golpe, despertar, estado, logs
# -----------------------------------------------------------------------------
# Uso (desde la raíz del repo):
#   export SNAPDEPLOY_TOKEN='sd_pat_...'     # token personal con alcance "deploy" (API Keys), SOLO en esta terminal
#   bash infra/scripts/snapdeploy.sh create            # crea el contenedor YA con las variables (sin construir aún)
#   bash infra/scripts/snapdeploy.sh link [--branch <rama>]  # enlaza el repo de GitHub (rama main por defecto; ADR-0031: `pruebas`), root directory backend-api, y lanza el build
#   bash infra/scripts/snapdeploy.sh env --dry-run     # muestra qué variables se enviarían (solo nombres)
#   bash infra/scripts/snapdeploy.sh env               # reemplaza TODAS las variables de un contenedor existente
#   bash infra/scripts/snapdeploy.sh wake              # despierta el contenedor sin abrir el navegador
#   bash infra/scripts/snapdeploy.sh status            # estado, URL y horas gastadas
#   bash infra/scripts/snapdeploy.sh logs [líneas]     # logs recientes de ejecución
#   bash infra/scripts/snapdeploy.sh deploys           # últimos despliegues y el error/log de build del más reciente
#   bash infra/scripts/snapdeploy.sh vars              # qué variables tiene el contenedor (solo NOMBRES) y cuáles faltan
# Más simple: ponga el token en infra/env/.env.snapdeploy y no exporte nada. Opciones por entorno: SNAPDEPLOY_NAME (nombre del contenedor, por defecto integrate-api),
#   ENV_FILE (por defecto infra/env/.env.demo).
# SEC-DIR-004: el token nunca se escribe en disco ni se imprime; los valores de las variables tampoco.
# La API de SnapDeploy nunca devuelve valores ("(set)"), solo acepta los nuevos.
# =============================================================================
set -u
BASE="${SNAPDEPLOY_BASE:-https://snapdeploy.dev}"   # solo para pruebas locales
ENV_FILE="${ENV_FILE:-infra/env/.env.demo}"
# El token y el nombre pueden venir del entorno o de infra/env/.env.snapdeploy (local, ignorado por Git:
# líneas SNAPDEPLOY_TOKEN=sd_pat_... y SNAPDEPLOY_NAME=...; también vale un archivo con solo el token).
TOKEN_FILE="${TOKEN_FILE:-infra/env/.env.snapdeploy}"
if [ -f "$TOKEN_FILE" ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in
      SNAPDEPLOY_TOKEN=*) [ -n "${SNAPDEPLOY_TOKEN:-}" ] || SNAPDEPLOY_TOKEN="${line#*=}";;
      SNAPDEPLOY_NAME=*)  [ -n "${SNAPDEPLOY_NAME:-}" ]  || SNAPDEPLOY_NAME="${line#*=}";;
      sd_pat_*|sk_*)      [ -n "${SNAPDEPLOY_TOKEN:-}" ] || SNAPDEPLOY_TOKEN="$line";;
    esac
  done < "$TOKEN_FILE"
  SNAPDEPLOY_TOKEN="${SNAPDEPLOY_TOKEN%\"}"; SNAPDEPLOY_TOKEN="${SNAPDEPLOY_TOKEN#\"}"
  SNAPDEPLOY_TOKEN="$(printf '%s' "$SNAPDEPLOY_TOKEN" | tr -d '[:space:]')"
  case "${SNAPDEPLOY_TOKEN:-}" in sd_pat_*|sk_*|"") ;; *) echo "El token de $TOKEN_FILE no parece válido (debe empezar por sd_pat_)."; exit 2;; esac
fi
NAME="${SNAPDEPLOY_NAME:-integrate-api}"
CMD="${1:-}"; shift || true

# Variables del .env.demo que NO son del backend en SnapDeploy (túnel/compose local) o ya vienen en la imagen.
SKIP='^(TUNNEL_TOKEN|API_IMAGE_TAG|DEMO_API_PORT|SERVER_PORT|PORT|HOST)$'

[ -n "${CMD}" ] || { sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
[ -n "${SNAPDEPLOY_TOKEN:-}" ] || { [ "$CMD" = env ] && [ "${1:-}" = "--dry-run" ] || { echo "Falta el token: péguelo en $TOKEN_FILE (línea SNAPDEPLOY_TOKEN=sd_pat_...) o use export SNAPDEPLOY_TOKEN='sd_pat_...'"; exit 2; }; }

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
  link)
    CID=$(container_id) || exit 1
    REPO="${SNAPDEPLOY_REPO:-$(git remote get-url origin 2>/dev/null | sed -E 's#^(https://github.com/|git@github.com:)##; s#\.git$##')}"
    BRANCH="${SNAPDEPLOY_BRANCH:-main}"; ROOT="${SNAPDEPLOY_ROOT:-backend-api}"
    # `link --branch pruebas` (ADR-0031): el entorno de pruebas sigue su propia rama y no se reconstruye con cada push a main.
    if [ "${1:-}" = "--branch" ]; then BRANCH="${2:-}"; [ -n "$BRANCH" ] || { echo "Falta el nombre de la rama: link --branch <rama>"; exit 2; }; fi
    [ -n "$REPO" ] || { echo "No pude deducir el repositorio: defina SNAPDEPLOY_REPO=usuario/repo"; exit 2; }
    echo "Enlazando $REPO (rama $BRANCH, root directory $ROOT) al contenedor '$NAME'..."
    api POST /api/mobile/github/link "$(REPO="$REPO" BRANCH="$BRANCH" ROOT="$ROOT" CID="$CID" pyj "
import os,json
print(json.dumps({'containerId':os.environ['CID'],'repoFullName':os.environ['REPO'],'deployBranch':os.environ['BRANCH'],'rootDirectory':os.environ['ROOT']}))")"
    case "$HTTP" in
      200|201) printf '%s' "$BODY" | pyj "
import sys,json
d=json.load(sys.stdin); print('OK: enlazado. Build lanzado:',d.get('buildTriggered'),'| deploymentId:',d.get('deploymentId'))"
        echo "Siga el build con: deploys   (tarda 4-5 min) y luego: logs";;
      *) echo "HTTP $HTTP al enlazar:"; printf '%s' "$BODY" | cut -c1-300
         case "$BODY" in *"already has a linked repository"*) echo "Para cambiar de rama hay que desenlazar primero, y SnapDeploy no lo permite con llaves de API: en su panel (web o móvil) abra el contenedor, desenlace el repositorio y vuelva a ejecutar este comando.";; esac
         exit 1;; esac ;;
  vars)
    CID=$(container_id) || exit 1
    api GET "/api/mobile/containers/$CID"
    [ "$HTTP" = 200 ] || { echo "HTTP $HTTP al leer el contenedor."; exit 1; }
    printf '%s' "$BODY" | NEED="$(grep -oE "'[A-Z0-9_]+'" <<<"$(sed -n "/need=\[/p" "$0")" | tr -d "'" | tr '\n' ' ')" pyj "
import sys,json,os,re
d=json.load(sys.stdin)
names=set()
def walk(o,key=''):
    if isinstance(o,dict):
        for k,v in o.items():
            if re.search(r'env',k,re.I):
                if isinstance(v,dict): names.update(v.keys())
                elif isinstance(v,list):
                    for i in v:
                        if isinstance(i,str): names.add(i.split('=')[0])
                        elif isinstance(i,dict):
                            n=i.get('name') or i.get('key')
                            if n: names.add(n)
            walk(v,k)
    elif isinstance(o,list):
        for i in o: walk(i,key)
walk(d)
names={n for n in names if re.match(r'^[A-Za-z_][A-Za-z0-9_]*$',n)}
need=os.environ['NEED'].split()
print('Contenedor:',d.get('name'),'| estado:',d.get('status'))
print(f'{len(names)} variables definidas en el contenedor:'); print('  '+', '.join(sorted(names)) if names else '  (ninguna)')
miss=[k for k in need if k not in names]
print('FALTAN obligatorias ->',', '.join(miss) if miss else 'ninguna')
if not names: print('(si esperaba variables y sale vacío, la API pudo no incluirlas: revise Container Settings en el panel)')
" || echo "(respuesta no interpretable, HTTP $HTTP)" ;;
  deploys)
    CID=$(container_id) || exit 1
    api GET "/api/mobile/containers/$CID/deployments?limit=5"
    [ "$HTTP" = 200 ] || { echo "HTTP $HTTP al leer los despliegues."; exit 1; }
    OUT=$(printf '%s' "$BODY" | pyj "
import sys,json
d=json.load(sys.stdin); L=d if isinstance(d,list) else d.get('deployments',d.get('items',[]))
print('Últimos despliegues (más reciente primero):')
for x in L[:5]:
    when=x.get('createdAt') or x.get('startedAt') or x.get('createdDate') or ''
    msg=(x.get('commitMessage') or x.get('commit') or x.get('message') or '')
    print('  -',x.get('status'),'|',when,'|',str(msg)[:70],'|',x.get('initiatedByLabel') or x.get('initiatedBy') or '')
print((L[0].get('deploymentId') or L[0].get('id') or '') if L else '')
")
    LAST="${OUT##*$'\n'}"; echo "${OUT%$'\n'*}"
    if [ -n "$LAST" ]; then
      api GET "/api/mobile/deployments/$LAST"
      printf '%s' "$BODY" | pyj "
import sys,json
d=json.load(sys.stdin)
print(); print('Detalle del más reciente:',d.get('status'),'| código:',d.get('errorCode'),'| imagen/commit:',d.get('commitSha') or d.get('commit') or '')
if d.get('errorMessage'): print('Error:',d['errorMessage'])
t=d.get('buildLogsTail')
if t: print('--- últimas líneas del build ---'); print('\n'.join(t.splitlines()[-40:]) if isinstance(t,str) else t)
" || echo "(detalle no interpretable, HTTP $HTTP)"
    fi ;;
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
