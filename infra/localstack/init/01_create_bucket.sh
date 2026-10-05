#!/bin/bash
# Intégrate · Hook "ready" de LocalStack: crea el bucket de vouchers (idempotente).
set -euo pipefail

bucket="${AWS_S3_BUCKET_NAME:?AWS_S3_BUCKET_NAME no definida}"

if awslocal s3api head-bucket --bucket "$bucket" 2>/dev/null; then
  echo "[integrate] bucket s3://$bucket ya existe"
else
  awslocal s3 mb "s3://$bucket"
  awslocal s3api put-public-access-block --bucket "$bucket" \
    --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
  echo "[integrate] bucket s3://$bucket creado"
fi
