resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db"
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds"
  description = "Allow Postgres access to the ajoledger RDS instance"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "Postgres from the backend instance only"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# Forces TLS on every connection, so credentials are never in plaintext even in-VPC.
resource "aws_db_parameter_group" "postgres_force_ssl" {
  name   = "${var.project_name}-postgres-force-ssl"
  family = "postgres16"

  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "pending-reboot"
  }
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project_name}-db"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage = var.db_allocated_storage
  storage_encrypted = true

  db_name  = "ajoledger"
  username = "ajoledger"

  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.postgres_force_ssl.name
  publicly_accessible    = false

  backup_retention_period = 1
  multi_az                = false
  skip_final_snapshot     = true
  deletion_protection     = false
}
