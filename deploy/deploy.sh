#!/usr/bin/env bash
# Runs on the EC2 host through SSM Run Command, sent by the CI deploy job.
# Usage: deploy.sh <image-uri> <aws-region>
set -euo pipefail

image="$1"
region="$2"
app_dir=/opt/pantryplan
registry="${image%%/*}"

cd "$app_dir"

# The first deploy can arrive while first-boot setup is still running.
cloud-init status --wait > /dev/null || true

db_password=$(aws ssm get-parameter --region "$region" \
  --name /pantryplan/prod/db_password --with-decryption \
  --query Parameter.Value --output text)
site_address=$(aws ssm get-parameter --region "$region" \
  --name /pantryplan/prod/site_address \
  --query Parameter.Value --output text)

umask 077
cat > .env <<EOF
APP_IMAGE=$image
SITE_ADDRESS=$site_address
POSTGRES_PASSWORD=$db_password
EOF
umask 022

aws ecr get-login-password --region "$region" \
  | docker login --username AWS --password-stdin "$registry"

docker compose pull --quiet
docker compose up -d --remove-orphans

echo "Waiting for the app to answer on /login"
for _ in $(seq 1 36); do
  if curl -fsS -o /dev/null http://127.0.0.1:8080/login; then
    echo "Deployed $image"
    # Drop images no container uses, older than a week. Keeps the disk clear.
    docker image prune -af --filter "until=168h" > /dev/null
    exit 0
  fi
  sleep 5
done

echo "App did not become healthy within 3 minutes" >&2
docker compose ps >&2
docker compose logs --tail 100 app >&2
exit 1
