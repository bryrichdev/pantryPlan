#!/usr/bin/env bash
# Only ImageNotFoundException means a build is needed; other errors fail closed.
set -euo pipefail
error_file=$(mktemp)
trap 'rm -f "$error_file"' EXIT
if aws ecr describe-images --repository-name "$ECR_REPOSITORY" \
    --image-ids "imageTag=$IMAGE_TAG" --output json > /dev/null 2>"$error_file"; then
  echo "exists=true" >> "$GITHUB_OUTPUT"
elif grep -q '(ImageNotFoundException)' "$error_file" && [[ -z "${ROLLBACK_TAG:-}" ]]; then
  echo "exists=false" >> "$GITHUB_OUTPUT"
else
  cat "$error_file" >&2
  echo "Cannot resolve the release; rollback images must already exist." >&2
  exit 1
fi
