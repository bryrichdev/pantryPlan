locals {
  app_url = var.domain != "" ? "https://${var.domain}" : "http://${aws_eip.app.public_ip}"
}

output "public_ip" {
  description = "Elastic IP. Point the domain's A record here."
  value       = aws_eip.app.public_ip
}

output "app_url" {
  value = local.app_url
}

output "instance_id" {
  value = aws_instance.app.id
}

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "github_deploy_role_arn" {
  value = aws_iam_role.github_deploy.arn
}

output "dns_instructions" {
  value = (
    var.domain != ""
    ? "Create an A record: ${var.domain} -> ${aws_eip.app.public_ip}. Caddy issues the certificate once it resolves."
    : "No domain set. The app is served over plain HTTP at http://${aws_eip.app.public_ip}."
  )
}

output "github_variables_commands" {
  description = "Run these once from the repo to give the workflow its settings."
  value       = <<-EOT
    gh variable set AWS_REGION     --repo ${var.github_repository} --body ${var.aws_region}
    gh variable set AWS_ROLE_ARN   --repo ${var.github_repository} --body ${aws_iam_role.github_deploy.arn}
    gh variable set ECR_REPOSITORY --repo ${var.github_repository} --body ${aws_ecr_repository.app.name}
    gh variable set INSTANCE_ID    --repo ${var.github_repository} --body ${aws_instance.app.id}
    gh variable set APP_URL        --repo ${var.github_repository} --body ${local.app_url}
  EOT
}
