#!/usr/bin/env bash
# Usage: bash <staged-release>/deploy.sh <image@sha256:digest> <aws-region> <public-url>
set -Eeuo pipefail

image="${1:?image is required}"
region="${2:?region is required}"
public_url="${3:?public URL is required}"
release_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
app_dir=${PANTRYPREP_DEPLOY_DIR:-/opt/pantryplan}
registry="${image%%/*}"
[[ "$image" =~ @sha256:[0-9a-f]{64}$ ]] || { echo "Deploy by digest only" >&2; exit 1; }
[[ "$release_dir" != "$app_dir" ]]
cd "$app_dir"
umask 077
# Serialize on the host: a timed-out Actions runner can leave SSM alive.
exec 9>"$app_dir/.deploy.lock"
flock -w 60 9
cloud-init status --wait > /dev/null
mountpoint -q /data

db_password=$(aws ssm get-parameter --region "$region" \
  --name /pantryplan/prod/db_password --with-decryption \
  --query Parameter.Value --output text)
site_address=$(aws ssm get-parameter --region "$region" \
  --name /pantryplan/prod/site_address --query Parameter.Value --output text)
printf 'APP_IMAGE=%s\nSITE_ADDRESS=%s\nPOSTGRES_PASSWORD=%s\n' \
  "$image" "$site_address" "$db_password" > "$release_dir/.env"
docker compose --project-directory "$release_dir" -f "$release_dir/compose.yaml" \
  --env-file "$release_dir/.env" config --quiet
aws ecr get-login-password --region "$region" \
  | docker login --username AWS --password-stdin "$registry"
docker pull "$image"

backup_dir=$(mktemp -d "$app_dir/rollback.XXXXXX")
previous_image=""
if [[ -f compose.yaml ]]; then
  container=$(docker compose ps -aq app)
  if [[ -n "$container" ]]; then
    previous_image=$(docker inspect --format '{{.Image}}' "$container")
    # Preserve the actual image even if its old mutable tag moved.
    docker image tag "$previous_image" pantryprep-rollback:previous
    for file in compose.yaml Caddyfile .env; do
      cp -p "$file" "$backup_dir/$file"
    done
    sed 's|^APP_IMAGE=.*|APP_IMAGE=pantryprep-rollback:previous|' \
      "$backup_dir/.env" > "$backup_dir/.env.restore"
  fi
fi

health_check() {
  local attempt code
  for attempt in $(seq 1 24); do
    code=$(curl -sS --connect-timeout 3 --max-time 5 -o /dev/null -w '%{http_code}' \
      http://127.0.0.1:8080/login) || code=000
    if [[ "$code" == 200 ]]; then
      code=$(curl -sS --connect-timeout 3 --max-time 5 -o /dev/null -w '%{http_code}' \
        "${public_url%/}/login") || code=000
      [[ "$code" == 200 ]] && return 0
    fi
    sleep 5
  done
  return 1
}

reload_proxy() {
  local attempt
  # A newly created Caddy container may not have opened its admin listener yet.
  for attempt in $(seq 1 12); do
    if docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile; then
      return 0
    fi
    sleep 2
  done
  return 1
}

recover() {
  local failure=$?
  trap - ERR
  set +e
  docker compose logs --tail 100 app >&2
  if [[ -n "$previous_image" ]]; then
    echo "Release failed; restoring the previous image and configuration." >&2
    cp -p "$backup_dir/compose.yaml" compose.yaml
    cp -p "$backup_dir/Caddyfile" Caddyfile
    cp -p "$backup_dir/.env.restore" .env
    if docker compose up -d --pull never --remove-orphans \
        && reload_proxy \
        && health_check; then
      echo "Previous release restored." >&2
    else
      echo "ROLLBACK FAILED. Inspect $backup_dir and the Compose logs." >&2
    fi
  else
    echo "First deployment failed; no previous release exists." >&2
  fi
  exit "$failure"
}

trap recover ERR
for file in compose.yaml Caddyfile .env; do
  cp "$release_dir/$file" "$file"
done
# Keep already-installed DB/proxy versions stable during an app release.
docker compose up -d --pull missing --remove-orphans
reload_proxy
health_check
trap - ERR
echo "Deployed $image"
# Backup bundles contain credentials and remain root-only for recovery.
docker image prune -f --filter "until=168h" > /dev/null || true
