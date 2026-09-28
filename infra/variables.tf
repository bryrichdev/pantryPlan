variable "aws_region" {
  description = "AWS region for every resource."
  type        = string
  default     = "us-east-1"
}

variable "domain" {
  description = "Public hostname, e.g. pantryplan.example.com. Leave empty to serve plain HTTP on the Elastic IP until a domain exists."
  type        = string
  default     = ""
}

variable "github_repository" {
  description = "owner/repo, used for the gh variable commands in the outputs."
  type        = string
  default     = "bryrichdev/pantryPlan"
}

variable "github_oidc_sub_prefix" {
  description = "Subject prefix GitHub puts in this repo's OIDC tokens. Read it with: gh api repos/OWNER/REPO/actions/oidc/customization/sub"
  type        = string
  default     = "repo:bryrichdev@316645314/pantryPlan@1379856595"
}

variable "create_github_oidc_provider" {
  description = "Set false if this AWS account already has the token.actions.githubusercontent.com OIDC provider."
  type        = bool
  default     = true
}

variable "instance_type" {
  description = "Must be a Graviton (arm64) type. CI builds a linux/arm64 image."
  type        = string
  default     = "t4g.small"

  validation {
    condition     = can(regex("^[a-z]+[0-9]+g[a-z]*\\.", var.instance_type))
    error_message = "Use a Graviton instance type (t4g, m7g, etc.). The image is built for arm64."
  }
}

variable "data_volume_size_gb" {
  description = "Size of the EBS volume holding Postgres data and TLS certificates."
  type        = number
  default     = 10
}

variable "snapshot_retention_count" {
  description = "Number of daily data-volume snapshots to keep."
  type        = number
  default     = 7
}

variable "vpc_cidr" {
  description = "CIDR for the dedicated VPC."
  type        = string
  default     = "10.40.0.0/16"
}
