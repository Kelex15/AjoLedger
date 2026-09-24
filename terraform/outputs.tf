output "ecr_repository_url" {
  value = aws_ecr_repository.backend.repository_url
}

output "ec2_public_ip" {
  description = "Add an A record at your registrar: api.<domain_name> -> this IP"
  value       = aws_eip.backend.public_ip
}

output "ec2_instance_id" {
  description = "Set this as the EC2_INSTANCE_ID secret in the GitHub repo"
  value       = aws_instance.backend.id
}

output "rds_endpoint" {
  value = aws_db_instance.main.address
}

output "rds_master_secret_arn" {
  description = "Secrets Manager ARN holding the auto-generated RDS master password"
  value       = aws_db_instance.main.master_user_secret[0].secret_arn
}

output "github_actions_role_arn" {
  description = "Set this as the AWS_ROLE_ARN secret in the GitHub repo"
  value       = aws_iam_role.github_actions_deploy.arn
}
