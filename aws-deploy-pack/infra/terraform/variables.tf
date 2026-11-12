variable "region" {
  description = "This AWS account is pinned to the Sydney region."
  type        = string
  default     = "ap-southeast-2"
}

variable "name_prefix" {
  type    = string
  default = "opp-poc"
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

variable "allowed_cidr" {
  description = "Your public IP as a /32 (run: curl ifconfig.me). Only this CIDR can reach the app ports."
  type        = string

  validation {
    condition     = can(cidrhost(var.allowed_cidr, 0)) && var.allowed_cidr != "0.0.0.0/0"
    error_message = "Use your own IP like 203.0.113.7/32, not 0.0.0.0/0."
  }
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
