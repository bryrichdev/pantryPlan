#!/usr/bin/env bash
# Scan the runnable arm64 manifest, not the OCI index/provenance manifest.
# Requires ECR basic scanning; enhanced scanning has different status semantics.
set -euo pipefail
details=$(aws ecr describe-images --repository-name "$ECR_REPOSITORY" \
  --image-ids "imageTag=$IMAGE_TAG" --output json)
digest=$(jq -er '.imageDetails[0].imageDigest' <<< "$details")
manifest=$(aws ecr batch-get-image --repository-name "$ECR_REPOSITORY" \
  --image-ids "imageDigest=$digest" --query 'images[0].imageManifest' --output text)
if jq -e '.manifests' <<< "$manifest" > /dev/null; then
  runtime_digest=$(jq -er '[.manifests[] | select(.platform.os == "linux" and .platform.architecture == "arm64")] | if length == 1 then .[0].digest else error("Expected one linux/arm64 image") end' <<< "$manifest")
else
  runtime_digest=$digest
fi
[[ "$digest" =~ ^sha256:[0-9a-f]{64}$ && "$runtime_digest" =~ ^sha256:[0-9a-f]{64}$ ]]
error_file=$(mktemp)
trap 'rm -f "$error_file"' EXIT
# Refresh older rollback images; if today's scan already ran, use its results.
if ! aws ecr start-image-scan --repository-name "$ECR_REPOSITORY" \
    --image-id "imageDigest=$runtime_digest" > /dev/null 2>"$error_file"; then
  if ! grep -q '(LimitExceededException)' "$error_file"; then
    cat "$error_file" >&2
    exit 1
  fi
fi
for _ in $(seq 1 60); do
  if findings=$(aws ecr describe-image-scan-findings --repository-name "$ECR_REPOSITORY" \
      --image-id "imageDigest=$runtime_digest" --output json 2>"$error_file"); then
    status=$(jq -er '.imageScanStatus.status' <<< "$findings")
    case "$status" in
      COMPLETE)
        critical=$(jq -er '.imageScanFindings.findingSeverityCounts.CRITICAL // 0' <<< "$findings")
        if (( critical > 0 )); then
          echo "Release blocked: $critical CRITICAL vulnerabilities in $runtime_digest" >&2
          exit 1
        fi
        echo "Scan passed for $runtime_digest"
        echo "image=$REGISTRY/$ECR_REPOSITORY@$digest" >> "$GITHUB_OUTPUT"
        exit 0
        ;;
      IN_PROGRESS|PENDING) ;;
      *) echo "Release blocked: ECR scan status $status" >&2; exit 1 ;;
    esac
  elif ! grep -q '(ScanNotFoundException)' "$error_file"; then
    cat "$error_file" >&2
    exit 1
  fi
  sleep 5
done
echo "Release blocked: ECR scan did not complete within five minutes." >&2
exit 1
