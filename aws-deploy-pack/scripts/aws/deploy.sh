#!/usr/bin/env bash
# Runs on the EC2 instance (invoked by the CD workflow through SSM).
# Pulls images for IMAGE_TAG from ECR, starts the stack, rolls back on failure.
set -euo pipefail

: "${ECR_REPO_URL:?}" "${IMAGE_TAG:?}" "${AWS_REGION:?}"
export ECR_REPO_URL IMAGE_TAG AWS_REGION

cd /opt/app
COMPOSE=(docker compose -f docker-compose.yaml -f aws-deploy-pack/docker-compose.aws.yaml)
LAST_GOOD=/opt/app/.last_good_tag

# First deploy only: generate secrets so we never run with the default passwords.
if [ ! -f .env ]; then
  umask 077
  {
    echo "DB_PASSWORD=$(openssl rand -hex 12)"
    echo "GRAFANA_ADMIN_PASSWORD=$(openssl rand -hex 8)"
  } > .env
  echo "Generated /opt/app/.env (read it via an SSM session if you need the Grafana password)."
fi

aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "${ECR_REPO_URL%%/*}"

deploy() {
  local tag="$1"
  echo "=== Deploying tag: ${tag} ==="
  IMAGE_TAG="$tag" "${COMPOSE[@]}" pull --quiet \
    && IMAGE_TAG="$tag" "${COMPOSE[@]}" up -d --no-build --remove-orphans --wait --wait-timeout 600
}

if deploy "$IMAGE_TAG"; then
  echo "$IMAGE_TAG" > "$LAST_GOOD"
  "${COMPOSE[@]}" ps
  docker image prune -af --filter "until=72h" >/dev/null || true
  echo "=== Deploy OK ==="
else
  echo "=== Deploy FAILED. Recent logs: ===" >&2
  IMAGE_TAG="$IMAGE_TAG" "${COMPOSE[@]}" ps >&2 || true
  IMAGE_TAG="$IMAGE_TAG" "${COMPOSE[@]}" logs --tail=30 >&2 || true

  if [ -f "$LAST_GOOD" ] && [ "$(cat "$LAST_GOOD")" != "$IMAGE_TAG" ]; then
    PREV="$(cat "$LAST_GOOD")"
    echo "=== Rolling back to ${PREV} ===" >&2
    if deploy "$PREV"; then
      echo "=== Rollback OK (running ${PREV}) ===" >&2
    else
      echo "=== Rollback ALSO failed ===" >&2
    fi
  fi
  exit 1
fi
