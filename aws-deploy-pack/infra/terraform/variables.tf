variable "region" {
  description = "This AWS account is pinned to the Sydney region."
  type        = string
  default     = "ap-southeast-2"
}

variable "name_prefix" {
  type    = string
  default = "opp-poc"
}

variable "github_repository" {
  description = "GitHub owner/repository allowed to assume the deploy role."
  type        = string
  default     = "RahulDittakavi/OrderProcessingPlatform"
}

variable "github_branch" {
  description = "Repository branch allowed to assume the deploy role."
  type        = string
  default     = "main"
}

variable "instance_type" {
  description = "4 GB RAM minimum. If the account rejects it, pick another type from the EC2 console."
  type        = string
  default     = "t3.medium"
}

variable "root_volume_gb" {
  type    = number
  default = 30
}

variable "enable_auto_stop" {
  description = "Safety net: stop the instance every night even if you forget."
  type        = bool
  default     = true
}

variable "auto_stop_cron" {
  type    = string
  default = "cron(30 0 * * ? *)" # 00:30 IST daily
}
