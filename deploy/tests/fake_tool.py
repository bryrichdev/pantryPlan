#!/usr/bin/env python3
"""External-system doubles for running the real shell scripts without AWS/Docker."""
import json
import os
from pathlib import Path
import sys

name = Path(sys.argv[0]).name
args = sys.argv[1:]
root = Path(os.environ["TEST_ROOT"])
mode = os.environ.get("TEST_MODE", "success")
with (root / "calls").open("a") as log:
    log.write(json.dumps([name, *args]) + "\n")

def fail(code):
    print(f"An error occurred ({code})", file=sys.stderr)
    sys.exit(1)

if name in ("sleep", "flock", "cloud-init", "mountpoint"):
    sys.exit(1 if mode == "no_mount" and name == "mountpoint" else 0)
if name == "aws":
    if args[:2] == ["ssm", "get-parameter"]:
        print("secret-for-test" if "db_password" in " ".join(args) else "app.example.test")
    elif args[:2] == ["ecr", "get-login-password"]:
        print("token")
    elif args[:2] == ["ecr", "describe-images"]:
        if mode == "missing":
            fail("ImageNotFoundException")
        if mode == "denied":
            fail("AccessDeniedException")
        print(json.dumps({"imageDetails": [{"imageDigest": "sha256:" + "a" * 64}]}))
    elif args[:2] == ["ecr", "batch-get-image"]:
        print(json.dumps({"manifests": [
            {"digest": "sha256:" + "b" * 64, "platform": {"os": "linux", "architecture": "arm64"}},
            {"digest": "sha256:" + "c" * 64, "platform": {"os": "unknown", "architecture": "unknown"}}
        ]}))
    elif args[:2] == ["ecr", "start-image-scan"]:
        if mode == "scan_quota":
            fail("LimitExceededException")
        print("{}")
    elif args[:2] == ["ecr", "describe-image-scan-findings"]:
        status = {"scan_failed": "FAILED", "scan_timeout": "IN_PROGRESS"}.get(mode, "COMPLETE")
        print(json.dumps({"imageScanStatus": {"status": status},
                          "imageScanFindings": {"findingSeverityCounts": {"CRITICAL": int(mode == "critical")}}}))
    else:
        raise AssertionError(args)
elif name == "docker":
    if args[:2] == ["compose", "ps"]:
        print("old-container")
    elif args[0] == "inspect":
        print("sha256:previous")
    elif args[0] == "login":
        sys.stdin.read()
    elif args[:2] == ["compose", "up"]:
        restoring = "pantryprep-rollback:previous" in Path(".env").read_text()
        (root / "restoring").write_text(str(restoring))
        if mode == "up_failure" and not restoring:
            sys.exit(1)
    elif args[:3] == ["compose", "exec", "-T"] and mode == "proxy_delay":
        marker = root / "proxy_attempt"
        if not marker.exists():
            marker.touch()
            sys.exit(1)
elif name == "curl":
    restoring = (root / "restoring").exists() and (root / "restoring").read_text() == "True"
    failed = mode == "health_failure" or (mode == "public_failure" and args[-1].startswith("https"))
    print("503" if (failed and not restoring) or mode == "rollback_failure" else "200", end="")
else:
    raise AssertionError(name)
