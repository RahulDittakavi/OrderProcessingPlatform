output "instance_id" {
  value = aws_instance.app.id
}

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "deployer_user_name" {
  description = "Create an access key for this user in the console, then store it as GitHub secrets."
  value       = aws_iam_user.deployer.name
}

output "github_repo_variables" {
  description = "Create these as GitHub repo VARIABLES (Settings > Secrets and variables > Actions > Variables)."
  value = {
    AWS_REGION     = var.region
    INSTANCE_ID    = aws_instance.app.id
    ECR_REPOSITORY = aws_ecr_repository.app.name
  }
}

output "get_public_ip" {
  description = "The public IP changes on every stop/start. Fetch the current one with this."
  value       = "aws ec2 describe-instances --region ${var.region} --instance-ids ${aws_instance.app.id} --query 'Reservations[0].Instances[0].PublicIpAddress' --output text"
}
