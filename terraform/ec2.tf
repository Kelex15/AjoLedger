# Always resolves to the latest Amazon Linux 2023 AMI for arm64 (Graviton,
# cheaper than x86), so we never have to hand-track AMI ids per region.
data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

resource "aws_security_group" "ec2" {
  name        = "${var.project_name}-ec2"
  description = "Public HTTP/HTTPS only - no SSH, admin access goes through SSM Session Manager"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_iam_role" "ec2" {
  name = "${var.project_name}-ec2"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# Lets us reach the instance via SSM Session Manager for debugging, with no
# SSH port open and no key pair to lose track of.
resource "aws_iam_role_policy_attachment" "ec2_ssm" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "ec2_ecr" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy" "ec2_secrets" {
  name = "${var.project_name}-ec2-secrets-read"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["secretsmanager:GetSecretValue"]
      Resource = [
        aws_secretsmanager_secret.jwt_secret.arn,
        aws_secretsmanager_secret.stripe_api_key.arn,
        aws_secretsmanager_secret.stripe_webhook_secret.arn,
        data.aws_secretsmanager_secret.paypal_client_id.arn,
        data.aws_secretsmanager_secret.paypal_client_secret.arn,
        aws_db_instance.main.master_user_secret[0].secret_arn,
        aws_secretsmanager_secret.db_app_password.arn,
      ]
    }]
  })
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${var.project_name}-ec2"
  role = aws_iam_role.ec2.name
}

resource "aws_instance" "backend" {
  ami                    = data.aws_ssm_parameter.al2023_ami.value
  instance_type          = var.ec2_instance_type
  iam_instance_profile   = aws_iam_instance_profile.ec2.name
  vpc_security_group_ids = [aws_security_group.ec2.id]
  subnet_id              = data.aws_subnets.default.ids[0]

  user_data = templatefile("${path.module}/user_data.yaml.tftpl", {
    deploy_script             = file("${path.module}/files/deploy.sh")
    aws_region                = var.aws_region
    ecr_repo_url              = aws_ecr_repository.backend.repository_url
    db_address                = aws_db_instance.main.address
    domain                    = "api.${var.domain_name}"
    cors_origin               = "https://app.${var.domain_name}"
    jwt_secret_arn            = aws_secretsmanager_secret.jwt_secret.arn
    stripe_api_key_arn        = aws_secretsmanager_secret.stripe_api_key.arn
    stripe_webhook_secret_arn = aws_secretsmanager_secret.stripe_webhook_secret.arn
    paypal_client_id_arn      = data.aws_secretsmanager_secret.paypal_client_id.arn
    paypal_client_secret_arn  = data.aws_secretsmanager_secret.paypal_client_secret.arn
    db_secret_arn             = aws_db_instance.main.master_user_secret[0].secret_arn
    db_app_secret_arn         = aws_secretsmanager_secret.db_app_password.arn
    google_oauth_client_id    = var.google_oauth_client_id
    cookie_domain             = var.domain_name
  })

  # Without this, EC2 has no API to update user_data on a running instance, so
  # Terraform would just update its own state and silently leave the real box unchanged.
  user_data_replace_on_change = true

  tags = {
    Name = "${var.project_name}-backend"
  }
}

resource "aws_eip" "backend" {
  instance = aws_instance.backend.id
  domain   = "vpc"
}
