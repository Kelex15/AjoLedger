#!/bin/bash
# Reads config from /opt/ajoledger/config.env so the same script runs on first boot
# and on every later redeploy triggered via SSM. Secrets are fetched fresh each run.
set -euo pipefail

source /opt/ajoledger/config.env

aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "${ECR_REPO_URL%%/*}"

DB_PASSWORD=$(aws secretsmanager get-secret-value --region "$AWS_REGION" \
  --secret-id "$DB_SECRET_ARN" --query SecretString --output text | jq -r .password)
JWT_SECRET=$(aws secretsmanager get-secret-value --region "$AWS_REGION" \
  --secret-id "$JWT_SECRET_ARN" --query SecretString --output text)
STRIPE_API_KEY=$(aws secretsmanager get-secret-value --region "$AWS_REGION" \
  --secret-id "$STRIPE_API_KEY_ARN" --query SecretString --output text)
STRIPE_WEBHOOK_SECRET=$(aws secretsmanager get-secret-value --region "$AWS_REGION" \
  --secret-id "$STRIPE_WEBHOOK_SECRET_ARN" --query SecretString --output text)

docker network create app-net 2>/dev/null || true
docker volume create caddy_data 2>/dev/null || true

docker pull "$ECR_REPO_URL:latest"

docker rm -f backend 2>/dev/null || true
docker run -d \
  --name backend \
  --network app-net \
  --restart unless-stopped \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SERVER_PORT=8080 \
  -e DATABASE_URL="jdbc:postgresql://${DB_ADDRESS}:5432/ajoledger?sslmode=require" \
  -e DATABASE_USERNAME=ajoledger \
  -e DATABASE_PASSWORD="$DB_PASSWORD" \
  -e JWT_SECRET="$JWT_SECRET" \
  -e STRIPE_API_KEY="$STRIPE_API_KEY" \
  -e STRIPE_WEBHOOK_SECRET="$STRIPE_WEBHOOK_SECRET" \
  -e CORS_ALLOWED_ORIGINS="$CORS_ORIGIN" \
  -e FRONTEND_BASE_URL="$CORS_ORIGIN" \
  -e SECURE_COOKIES=true \
  -e GOOGLE_OAUTH_CLIENT_ID="$GOOGLE_OAUTH_CLIENT_ID" \
  -e COOKIE_DOMAIN="$COOKIE_DOMAIN" \
  "$ECR_REPO_URL:latest"

mkdir -p /opt/ajoledger/caddy
cat > /opt/ajoledger/caddy/Caddyfile <<CADDYFILE
${DOMAIN} {
    reverse_proxy backend:8080
}
CADDYFILE

docker rm -f caddy 2>/dev/null || true
docker run -d \
  --name caddy \
  --network app-net \
  --restart unless-stopped \
  -p 80:80 -p 443:443 \
  -v /opt/ajoledger/caddy/Caddyfile:/etc/caddy/Caddyfile \
  -v caddy_data:/data \
  caddy:2
