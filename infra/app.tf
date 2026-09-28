# Image registry and runtime configuration for the app.

resource "aws_ecr_repository" "app" {
  name                 = "pantryprep"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the 15 most recent images for rollback"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 15
      }
      action = { type = "expire" }
    }]
  })
}

# Postgres sets this password only when it first creates the database.
# Never taint or rotate this resource without also changing it in Postgres.
resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_ssm_parameter" "db_password" {
  name  = "/pantryprep/prod/db_password"
  type  = "SecureString"
  value = random_password.db.result
}

resource "aws_ssm_parameter" "site_address" {
  name  = "/pantryprep/prod/site_address"
  type  = "String"
  value = var.domain != "" ? var.domain : ":80"
}
