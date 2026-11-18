output "instance_id" {
  value = aws_instance.app.id
}

output "aws_region" {
  value = var.region
}

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "aws_deploy_role_arn" {
  description = "GitHub Actions role ARN; set this as the AWS_DEPLOY_ROLE_ARN repository variable."
  value       = aws_iam_role.github_actions.arn
}

output "github_repo_variables" {
  description = "Create these as GitHub repo VARIABLES (Settings > Secrets and variables > Actions > Variables)."
  value = {
    AWS_REGION          = var.region
    INSTANCE_ID         = aws_instance.app.id
    ECR_REPOSITORY      = aws_ecr_repository.app.name
    AWS_DEPLOY_ROLE_ARN = aws_iam_role.github_actions.arn
  }
}
