resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "jwt_secret" {
  name = "${var.project_name}/jwt-secret"
}

resource "aws_secretsmanager_secret_version" "jwt_secret" {
  secret_id     = aws_secretsmanager_secret.jwt_secret.id
  secret_string = random_password.jwt_secret.result
}

# The backend connects as its own role with this static password, so RDS's weekly
# rotation of the master password never invalidates a running container.
resource "random_password" "db_app_password" {
  length  = 40
  special = false
}

resource "aws_secretsmanager_secret" "db_app_password" {
  name = "${var.project_name}/db-app-password"
}

resource "aws_secretsmanager_secret_version" "db_app_password" {
  secret_id     = aws_secretsmanager_secret.db_app_password.id
  secret_string = random_password.db_app_password.result
}

# Stripe values come from the Stripe dashboard, not Terraform. These resources just
# create the container with a placeholder; set the real value once via:
#   aws secretsmanager put-secret-value --secret-id ajoledger/stripe-api-key --secret-string "sk_live_..."
#   aws secretsmanager put-secret-value --secret-id ajoledger/stripe-webhook-secret --secret-string "whsec_..."
# `ignore_changes` below stops `terraform apply` from overwriting that value afterwards.
resource "aws_secretsmanager_secret" "stripe_api_key" {
  name = "${var.project_name}/stripe-api-key"
}

resource "aws_secretsmanager_secret_version" "stripe_api_key" {
  secret_id     = aws_secretsmanager_secret.stripe_api_key.id
  secret_string = "REPLACE_ME"

  lifecycle {
    ignore_changes = [secret_string]
  }
}

resource "aws_secretsmanager_secret" "stripe_webhook_secret" {
  name = "${var.project_name}/stripe-webhook-secret"
}

resource "aws_secretsmanager_secret_version" "stripe_webhook_secret" {
  secret_id     = aws_secretsmanager_secret.stripe_webhook_secret.id
  secret_string = "REPLACE_ME"

  lifecycle {
    ignore_changes = [secret_string]
  }
}

# Same placeholder pattern as Stripe. Set the real key once via:
#   aws secretsmanager put-secret-value --secret-id ajoledger/resend-api-key --secret-string "re_..."
resource "aws_secretsmanager_secret" "resend_api_key" {
  name = "${var.project_name}/resend-api-key"
}

resource "aws_secretsmanager_secret_version" "resend_api_key" {
  secret_id     = aws_secretsmanager_secret.resend_api_key.id
  secret_string = "REPLACE_ME"

  lifecycle {
    ignore_changes = [secret_string]
  }
}
