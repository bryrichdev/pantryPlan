# PantryPrep deployment

PantryPrep runs on one EC2 Graviton instance (`t4g.small`). Docker Compose runs three containers on it:

- **caddy**: terminates HTTPS with an automatic Let's Encrypt certificate.
- **app**: the Spring Boot image from ECR.
- **db**: Postgres 17. Its data sits on a separate EBS volume with daily snapshots.

GitHub Actions tests every push and pull request targeting `main`. A push to `main` also builds an arm64 image (or reuses the existing commit image), checks its ECR scan, and deploys it by digest through SSM Run Command. GitHub signs in to AWS with OIDC, so there are no stored AWS keys. The instance has no SSH port.

```
push ──> test (mvnw verify + Testcontainers)
           │ main only
           ▼
         build/reuse arm64 image ──> ECR scan (no CRITICAL findings)
           │
           ▼
         SSM Run Command ──> EC2: snapshot config/image, deploy, health check
                                  └─ failure: restore previous release
```

Estimated cost in us-east-1: about $19/month. That covers the instance (~$12), 30 GB of gp3 (~$2.40), the public IPv4 address (~$3.65), snapshots and ECR (~$1).

## One-time setup

You need the AWS CLI, Terraform 1.10 or later, and the GitHub CLI (`gh`). Run everything from your Mac.

### 1. Create the state bucket

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
BUCKET=pantryplan-tfstate-$ACCOUNT_ID
aws s3api create-bucket --bucket "$BUCKET" --region us-east-1
aws s3api put-bucket-versioning --bucket "$BUCKET" --versioning-configuration Status=Enabled
```

Outside us-east-1, add `--create-bucket-configuration LocationConstraint=<region>` to `create-bucket`.

### 2. Configure and apply

```bash
cd infra
cp backend.hcl.example backend.hcl                 # set the bucket name
cp terraform.tfvars.example terraform.tfvars       # set domain if you have one
terraform init -backend-config=backend.hcl
terraform apply
```

Check IAM > Identity providers first. If `token.actions.githubusercontent.com` already exists, set `create_github_oidc_provider = false`.

### 3. Give the workflow its settings

```bash
terraform output -raw github_variables_commands | bash
```

This sets five repository variables: `AWS_REGION`, `AWS_ROLE_ARN`, `ECR_REPOSITORY`, `INSTANCE_ID` and `APP_URL`. None of them are secrets.

### 4. Deploy

Configure the `production` environment in GitHub to allow only `main`. Enable required reviewers if your repository plan supports them, and require the Test check in the main branch ruleset. These settings live in GitHub, not this workflow.

Merge to `main` or push to it. Watch the **Deploy** job. Its "Host output" group shows the server log. The workflow requires `APP_URL`, immutable ECR tags, and ECR basic scanning. It checks the runnable arm64 image's findings separately from the provenance attestation and blocks on CRITICAL findings, failed scans, or scan timeouts. Basic scanning covers OS vulnerabilities; this is not a Maven dependency vulnerability gate.

### 5. Add the domain later

1. Set `domain` in `terraform.tfvars` and run `terraform apply`.
2. Create an A record from the domain to the `public_ip` output. At Cloudflare, set it to **DNS only** (grey cloud). Caddy needs direct traffic to get its certificate.
3. Re-run `terraform output -raw github_variables_commands | bash` to update `APP_URL`.
4. Redeploy: re-run the last workflow run on `main`, or push again. Caddy picks up the new address on that deploy.

## Operations

| Task | How |
|---|---|
| Shell on the server | `aws ssm start-session --target <instance_id>` (needs the Session Manager plugin) |
| App logs | In a session: `cd /opt/pantryplan && sudo docker compose logs -f app` |
| Roll back | Actions > CI/CD > Run workflow on `main`. Enter an earlier commit SHA as `image_tag`. |
| Database shell | `sudo docker compose exec db psql -U pantryplan` |
| Replace the server | `terraform apply -replace=aws_instance.app`, then redeploy. The data volume reattaches. |
| Restore a backup | Create a volume from the snapshot in the instance's AZ. Then run `terraform state rm aws_volume_attachment.data aws_ebs_volume.data`, `terraform import aws_ebs_volume.data <new-volume-id>` and `terraform apply -replace=aws_instance.app`. Redeploy. |

The data volume has `prevent_destroy`. `terraform destroy` stops on it on purpose. To tear everything down, remove that lifecycle block first.

The database password is generated once and stored in SSM Parameter Store at `/pantryplan/prod/db_password`. Postgres applies it only when it creates the database. Do not taint `random_password.db`.

## Applying the pipeline fixes to the existing deployment

PantryPrep is the public brand and Java package name. Physical production identities intentionally retain `pantryplan`: the Terraform backend key, ECR repository, IAM roles, security group, SSM parameters, deployment directory, Compose project, and database role/name. Renaming those would create replacements or disconnect the application from existing data. The GitHub repository and OIDC subject defaults also retain the existing repository identity; update those together only after an actual repository rename.

Before merging these fixes, run `terraform plan` from `infra` using the existing backend configuration. Review it, then apply it to enable ECR tag immutability and grant the deployment role permission to read/start scans. The plan must use the existing state and must not replace the database volume, repository, or instance. Do not initialize against a new state key or bucket. If resources were already migrated to new physical names, reconcile that state first instead of applying the legacy defaults.

The workflow checks immutability before it builds or touches the server, so missing infrastructure prerequisites fail safely. A rerun reuses an existing commit image instead of overwriting it. All actions are pinned to verified commit SHAs; Dependabot proposes updates weekly.

`V12` is restored byte-for-byte to its original production content. `V16` updates the audit function to accept both prefixes, allowing the previous application image to run against the upgraded schema. CI rejects changes to existing migrations, with an exact-checksum exception for this V12 restoration, and tests an upgrade from V15. If a new database was already initialized with the accidentally edited V12, verify its schema and history separately before proceeding; do not automatically repair production history.

Each deploy stages files under `/opt/pantryplan/release.*`, takes a host lock, requires the data mount, saves the old configuration, and tags the actual running image locally for recovery. If Compose startup, Caddy reload, or local/public health checks fail on the host, it restores the previous image and files and verifies health again. The Actions run still fails so the failed release remains visible. Root-only `rollback.*` directories retain the saved configuration for inspection. There is no previous release to restore on a first deployment.

The final runner-side smoke test separately detects external routing issues. If only that test fails after the host checks passed, investigate DNS/CDN routing using its logs; it does not roll back an otherwise healthy host. Rollback never reverses database migrations: future schema changes must remain compatible with the previous application. Manual rollback uses the selected old image with the current deployment scripts/configuration and must pass the same scan gate.

App deployments pull only the app image and missing infrastructure images. Existing Postgres/Caddy images are not automatically upgraded. Plan those upgrades separately.
