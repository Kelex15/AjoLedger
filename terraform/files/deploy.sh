#!/bin/bash
# Reads config from /opt/ajoledger/config.env so the same script runs on first boot
# and on every later redeploy triggered via SSM. Secrets are fetched fresh each run.
set -euo pipefail

source /opt/ajoledger/config.env

aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "${ECR_REPO_URL%%/*}"

DB_MASTER_PASSWORD=$(aws secretsmanager get-secret-value --region "$AWS_REGION" \
  --secret-id "$DB_SECRET_ARN" --query SecretString --output text | jq -r .password)
DB_APP_PASSWORD=$(aws secretsmanager get-secret-value --region "$AWS_REGION" \
  --secret-id "$DB_APP_SECRET_ARN" --query SecretString --output text)
JWT_SECRET=$(aws secretsmanager get-secret-value --region "$AWS_REGION" \
  --secret-id "$JWT_SECRET_ARN" --query SecretString --output text)
STRIPE_API_KEY=$(aws secretsmanager get-secret-value --region "$AWS_REGION" \
  --secret-id "$STRIPE_API_KEY_ARN" --query SecretString --output text)
STRIPE_WEBHOOK_SECRET=$(aws secretsmanager get-secret-value --region "$AWS_REGION" \
  --secret-id "$STRIPE_WEBHOOK_SECRET_ARN" --query SecretString --output text)
PAYPAL_CLIENT_ID=$(aws secretsmanager get-secret-value --region "$AWS_REGION" \
  --secret-id "$PAYPAL_CLIENT_ID_ARN" --query SecretString --output text)
PAYPAL_CLIENT_SECRET=$(aws secretsmanager get-secret-value --region "$AWS_REGION" \
  --secret-id "$PAYPAL_CLIENT_SECRET_ARN" --query SecretString --output text)

# The master password is only used here, right after fetching it; the backend runs as
# ajoledger_app. Idempotent, so it also re-syncs the app password on every deploy.
# Ownership transfer is scoped to public: on RDS the master also owns template1 and
# postgres, which REASSIGN OWNED would hand over too. Extension members stay put.
PGPASSWORD="$DB_MASTER_PASSWORD" APP_DB_PASSWORD="$DB_APP_PASSWORD" psql \
  "host=${DB_ADDRESS} dbname=ajoledger user=ajoledger sslmode=require" \
  -v ON_ERROR_STOP=1 -q <<'SQL'
\getenv app_password APP_DB_PASSWORD
SELECT 'CREATE ROLE ajoledger_app'
  WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ajoledger_app') \gexec
ALTER ROLE ajoledger_app WITH LOGIN PASSWORD :'app_password';
GRANT ajoledger_app TO ajoledger;
ALTER DATABASE ajoledger OWNER TO ajoledger_app;
SELECT format('ALTER TABLE %s OWNER TO ajoledger_app', c.oid::regclass)
  FROM pg_class c
  WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r', 'p')
    AND c.relowner = 'ajoledger'::regrole \gexec
SELECT format('ALTER FUNCTION %s OWNER TO ajoledger_app', p.oid::regprocedure)
  FROM pg_proc p
  WHERE p.pronamespace = 'public'::regnamespace AND p.proowner = 'ajoledger'::regrole
    AND NOT EXISTS (SELECT FROM pg_depend d WHERE d.classid = 'pg_proc'::regclass
                      AND d.objid = p.oid AND d.deptype = 'e') \gexec
SQL

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
  -e DATABASE_USERNAME=ajoledger_app \
  -e DATABASE_PASSWORD="$DB_APP_PASSWORD" \
  -e JWT_SECRET="$JWT_SECRET" \
  -e STRIPE_API_KEY="$STRIPE_API_KEY" \
  -e STRIPE_WEBHOOK_SECRET="$STRIPE_WEBHOOK_SECRET" \
  -e PAYPAL_CLIENT_ID="$PAYPAL_CLIENT_ID" \
  -e PAYPAL_CLIENT_SECRET="$PAYPAL_CLIENT_SECRET" \
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
