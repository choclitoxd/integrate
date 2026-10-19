#!/usr/bin/env bash
# =============================================================================
# Intégrate · Verifica el acceso a los buckets de Backblaze B2 (ADR-0024 §4, guía de despliegue)
# Uso (desde la raíz del repo):  bash infra/scripts/check-b2.sh [infra/env/.env.demo]
# Lee SOLO las variables AWS_* del archivo (local, ignorado por Git) y las pasa a un contenedor
# amazon/aws-cli. Nunca imprime los valores de las llaves. Crea y borra un objeto de prueba.
# =============================================================================
set -u
ENV_FILE="${1:-infra/env/.env.demo}"
[ -f "$ENV_FILE" ] || { echo "No existe $ENV_FILE"; exit 2; }

get() { grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- | tr -d '\r' | sed -e 's/^"//' -e 's/"$//'; }
ENDPOINT=$(get AWS_ENDPOINT_URL); REGION=$(get AWS_REGION)
B1=$(get AWS_S3_BUCKET_NAME); B2=$(get AWS_S3_SENSITIVE_BUCKET_NAME)
KID=$(get AWS_ACCESS_KEY_ID); KEY=$(get AWS_SECRET_ACCESS_KEY)
for v in ENDPOINT REGION B1 B2 KID KEY; do [ -n "${!v}" ] || { echo "Falta una variable AWS_* en $ENV_FILE ($v)"; exit 2; }; done
case "$ENDPOINT" in https://*) ;; *) echo "AWS_ENDPOINT_URL debe empezar por https://"; exit 2;; esac
case "$ENDPOINT" in *"$REGION"*) ;; *) echo "AVISO: la región '$REGION' no aparece en el endpoint '$ENDPOINT'";; esac

aws() {  # MSYS_NO_PATHCONV: Git Bash en Windows no debe reescribir rutas como /tmp
  MSYS_NO_PATHCONV=1 docker run --rm -e AWS_ACCESS_KEY_ID="$KID" -e AWS_SECRET_ACCESS_KEY="$KEY" \
    -e AWS_DEFAULT_REGION="$REGION" -e AWS_EC2_METADATA_DISABLED=true -e AWS_REQUEST_CHECKSUM_CALCULATION=when_required \
    -e AWS_RESPONSE_CHECKSUM_VALIDATION=when_required amazon/aws-cli --endpoint-url "$ENDPOINT" "$@"
}
fail=0
ok() { echo "  OK   $1"; }
bad() { echo "  FALLA $1"; fail=1; }

for B in "$B1" "$B2"; do
  echo "== bucket: $B"
  OBJ="integrate-check-$(date +%s).txt"
  if aws s3api head-bucket --bucket "$B" >/dev/null 2>&1; then ok "existe y la llave llega a él"; else bad "head-bucket (nombre, región/endpoint o permisos de la llave)"; continue; fi
  if echo "prueba integrate" | docker run --rm -i -e AWS_ACCESS_KEY_ID="$KID" -e AWS_SECRET_ACCESS_KEY="$KEY" -e AWS_DEFAULT_REGION="$REGION" \
      -e AWS_EC2_METADATA_DISABLED=true -e AWS_REQUEST_CHECKSUM_CALCULATION=when_required -e AWS_RESPONSE_CHECKSUM_VALIDATION=when_required \
      amazon/aws-cli --endpoint-url "$ENDPOINT" s3 cp - "s3://$B/$OBJ" >/dev/null 2>&1; then ok "escritura (PutObject)"; else bad "escritura (¿la llave es Read and Write?)"; continue; fi
  GOT=$(aws s3 cp "s3://$B/$OBJ" - 2>/dev/null)
  [ "$GOT" = "prueba integrate" ] && ok "lectura (GetObject)" || bad "lectura"
  # Acceso anónimo: debe ser rechazado (bucket privado). Estilo ruta, igual que la app.
  CODE=$(curl -s -o /dev/null -w '%{http_code}' "$ENDPOINT/$B/$OBJ")
  case "$CODE" in 401|403|404) ok "privado (acceso anónimo rechazado: HTTP $CODE)";; 200) bad "PÚBLICO: el objeto se lee sin credenciales; cambie el bucket a Private";; *) bad "acceso anónimo inesperado (HTTP $CODE)";; esac
  aws s3 rm "s3://$B/$OBJ" >/dev/null 2>&1 && ok "borrado del objeto de prueba" || bad "borrado (¿permiso deleteFiles?)"
done
echo
[ $fail -eq 0 ] && echo "RESULTADO: todo correcto." || echo "RESULTADO: hay fallos (ver arriba)."
exit $fail
