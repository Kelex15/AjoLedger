variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Short name used to prefix resource names"
  type        = string
  default     = "ajoledger"
}

variable "domain_name" {
  description = "Root domain you registered (e.g. ajoledger.xyz). Backend is served on api.<domain_name>."
  type        = string
}

variable "github_repo" {
  description = "GitHub repo allowed to assume the CI/CD deploy role, as \"owner/repo\""
  type        = string
  default     = "Kelex15/AjoLedger"
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "RDS allocated storage in GB"
  type        = number
  default     = 20
}

variable "ec2_instance_type" {
  description = "Instance type for the backend host (Graviton/arm64 family)"
  type        = string
  default     = "t4g.small"
}

variable "google_oauth_client_id" {
  description = "Google OAuth web client id"
  type        = string
}
