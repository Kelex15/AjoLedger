data "aws_caller_identity" "current" {}

# Created by hand via the CLI rather than Terraform, so these are lookups. Declaring them as
# resources would fail on apply, the names already exist.
data "aws_secretsmanager_secret" "paypal_client_id" {
  name = "${var.project_name}/paypal-client-id"
}

data "aws_secretsmanager_secret" "paypal_client_secret" {
  name = "${var.project_name}/paypal-client-secret"
}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}
