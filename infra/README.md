# PantryPlan deployment

PantryPlan runs on one EC2 Graviton instance (`t4g.small`). Docker Compose runs three containers on it:

- **caddy**: terminates HTTPS with an automatic Let's Encrypt certificate.
- **app**: the Spring Boot image from ECR.
- **db**: Postgres 17. Its data sits on a separate EBS volume with daily snapshots.

GitHub Actions tests every push. A push to `main` also builds an arm64 image, pushes it to ECR and deploys it through SSM Run Command. GitHub signs in to AWS with OIDC, so there are no stored AWS keys. The instance has no SSH port.

```
push ──> test (mvnw verify + Testcontainers)
           │ main only
           ▼
         build arm64 image ──> ECR
           │
           ▼
         SSM Run Command ──> EC2: write compose/Caddyfile, pull, up -d, health check
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

Merge to `main` or push to it. The first run creates the `production` environment in GitHub. Watch the **Deploy** job. Its "Host output" group shows the server log.

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
