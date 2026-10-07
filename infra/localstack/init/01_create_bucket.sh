#!/bin/bash
# Intégrate · Hook "ready" de LocalStack: crea los buckets (idempotente).
#   - AWS_S3_BUCKET_NAME: vouchers.
#   - AWS_S3_SENSITIVE_BUCKET_NAME: documentos que revelan la condición de víctima (ADR-0007 §5).
#     Recibe subidas directas desde la PWA con URL prefirmada, por eso lleva regla CORS para PUT.
set -euo pipefail

create_private_bucket() {
  local bucket="$1"
  if awslocal s3api head-bucket --bucket "$bucket" 2>/dev/null; then
    echo "[integrate] bucket s3://$bucket ya existe"
  else
    awslocal s3 mb "s3://$bucket"
    awslocal s3api put-public-access-block --bucket "$bucket" \
      --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
    echo "[integrate] bucket s3://$bucket creado"
  fi
}

create_private_bucket "${AWS_S3_BUCKET_NAME:?AWS_S3_BUCKET_NAME no definida}"

sensitive="${AWS_S3_SENSITIVE_BUCKET_NAME:?AWS_S3_SENSITIVE_BUCKET_NAME no definida}"
create_private_bucket "$sensitive"
awslocal s3api put-bucket-cors --bucket "$sensitive" --cors-configuration '{
  "CORSRules": [{
    "AllowedOrigins": ["http://localhost:5173", "http://127.0.0.1:5173"],
    "AllowedMethods": ["PUT"],
    "AllowedHeaders": ["Content-Type"],
    "MaxAgeSeconds": 300
  }]
}'
echo "[integrate] CORS de subida aplicado a s3://$sensitive"
