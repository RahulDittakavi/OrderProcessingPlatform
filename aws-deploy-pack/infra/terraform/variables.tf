variable "region" {
  type    = string
  default = "ap-south-1"
}

variable "name_prefix" {
  type    = string
  default = "opp-poc"
}

variable "instance_type" {
  description = "4 GB RAM minimum. Pick a type the EC2 console marks as free-plan eligible."
  type        = string
  default     = "t3.medium"
}

variable "root_volume_gb" {
  type    = number
  default = 30
}

variable "allowed_cidr" {
  description = "Your public IP as a /32 (run: curl ifconfig.me). Only this CIDR can reach the app ports."
  type        = string

  validation {
    condition     = can(cidrhost(var.allowed_cidr, 0)) && var.allowed_cidr != "0.0.0.0/0"
    error_message = "Use your own IP like 203.0.113.7/32, not 0.0.0.0/0."
  }
}

variable "github_repo" {
  description = "owner/repo that is allowed to deploy via OIDC"
  type        = string
  default     = "RahulDittakavi/OrderProcessingPlatform"
}

variable "create_github_oidc_provider" {
  description = "Set false if this account already has the GitHub OIDC provider (only one allowed per account)."
  type        = bool
  default     = true
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
