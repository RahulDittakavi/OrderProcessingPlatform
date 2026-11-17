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

if ! command -v terraform >/dev/null 2>&1; then
  echo "Terraform is required to read the instance ID and region." >&2
  exit 1
fi

if ! command -v session-manager-plugin >/dev/null 2>&1; then
  echo "The AWS Session Manager plugin is required to open an SSM tunnel." >&2
  exit 1
fi

terraform_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../infra/terraform" && pwd)"
instance_id="$(terraform -chdir="$terraform_dir" output -raw instance_id)"
region="$(terraform -chdir="$terraform_dir" output -raw aws_region)"

echo "Forwarding localhost:${port} to ${service} on ${instance_id}; press Ctrl+C to stop."
exec aws ssm start-session \
  --target "$instance_id" \
  --region "$region" \
  --document-name AWS-StartPortForwardingSession \
  --parameters "{\"portNumber\":[\"${port}\"],\"localPortNumber\":[\"${port}\"]}"
