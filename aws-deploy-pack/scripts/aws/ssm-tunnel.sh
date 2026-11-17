#!/usr/bin/env bash
set -euo pipefail

service="${1:-}"
case "$service" in
  order-desk) port=9000 ;;
  grafana) port=3000 ;;
  prometheus) port=9090 ;;
  eureka) port=8761 ;;
  *)
    echo "Usage: $0 {order-desk|grafana|prometheus|eureka}" >&2
    exit 2
    ;;
esac

if ! command -v aws >/dev/null 2>&1; then
  echo "AWS CLI is required to start an SSM tunnel." >&2
  exit 1
fi

if ! command -v session-manager-plugin >/dev/null 2>&1; then
  echo "The AWS Session Manager plugin is required to open an SSM tunnel." >&2
  exit 1
fi

terraform_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../infra/terraform" && pwd)"
instance_id="${INSTANCE_ID:-}"
region="${AWS_REGION:-}"

if [[ -z "$instance_id" || -z "$region" ]]; then
  if ! command -v terraform >/dev/null 2>&1; then
    echo "Set INSTANCE_ID and AWS_REGION, or install Terraform and use a local Terraform state." >&2
    exit 1
  fi
  if [[ -z "$instance_id" ]]; then
    if ! instance_id="$(terraform -chdir="$terraform_dir" output -raw instance_id)"; then
      echo "Could not read instance_id from Terraform state; set INSTANCE_ID explicitly." >&2
      exit 1
    fi
  fi
  if [[ -z "$region" ]]; then
    if ! region="$(terraform -chdir="$terraform_dir" output -raw aws_region)"; then
      echo "Could not read aws_region from Terraform state; set AWS_REGION explicitly." >&2
      exit 1
    fi
  fi
fi

if [[ -z "$instance_id" || -z "$region" ]]; then
  echo "Both INSTANCE_ID and AWS_REGION must be available to start the tunnel." >&2
  exit 1
fi

echo "Forwarding localhost:${port} to ${service} on ${instance_id}; press Ctrl+C to stop."
exec aws ssm start-session \
  --target "$instance_id" \
  --region "$region" \
  --document-name AWS-StartPortForwardingSession \
  --parameters "{\"portNumber\":[\"${port}\"],\"localPortNumber\":[\"${port}\"]}"
