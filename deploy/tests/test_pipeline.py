import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

DEPLOY = Path(__file__).resolve().parents[1]


class PipelineTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        for name in ("aws", "docker", "curl", "sleep", "flock", "cloud-init", "mountpoint"):
            tool = self.bin / name
            shutil.copy(DEPLOY / "tests/fake_tool.py", tool)
            tool.chmod(0o755)
        self.output = self.root / "output"
        self.output.touch()
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
                        TEST_ROOT=str(self.root), GITHUB_OUTPUT=str(self.output),
                        ECR_REPOSITORY="pantryplan", IMAGE_TAG="d" * 40, REGISTRY="registry.test")

    def run_script(self, script, mode="success", args=()):
        return subprocess.run(["bash", str(script), *args], env=dict(self.env, TEST_MODE=mode),
                              capture_output=True, text=True, timeout=30)

    def calls(self):
        return [json.loads(line) for line in (self.root / "calls").read_text().splitlines()]

    def test_reuses_existing_image(self):
        result = self.run_script(DEPLOY / "find-image.sh")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("exists=true", self.output.read_text())

    def test_missing_image_can_build(self):
        result = self.run_script(DEPLOY / "find-image.sh", "missing")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("exists=false", self.output.read_text())

    def test_missing_rollback_must_not_build(self):
        self.env["ROLLBACK_TAG"] = "d" * 40
        result = self.run_script(DEPLOY / "find-image.sh", "missing")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", self.output.read_text())

    def test_access_denied_is_not_missing(self):
        result = self.run_script(DEPLOY / "find-image.sh", "denied")
        self.assertNotEqual(0, result.returncode)

    def test_scan_uses_runtime_digest_and_deploys_index_digest(self):
        result = self.run_script(DEPLOY / "verify-image.sh")
        self.assertEqual(0, result.returncode, result.stderr)
        scan = next(call for call in self.calls() if "describe-image-scan-findings" in call)
        self.assertIn("imageDigest=sha256:" + "b" * 64, scan)
        self.assertIn("image=registry.test/pantryplan@sha256:" + "a" * 64, self.output.read_text())

    def test_daily_scan_quota_uses_completed_findings(self):
        result = self.run_script(DEPLOY / "verify-image.sh", "scan_quota")
        self.assertEqual(0, result.returncode, result.stderr)

    def test_scan_blocks_critical_failed_and_timeout(self):
        for mode in ("critical", "scan_failed", "scan_timeout", "denied"):
            with self.subTest(mode=mode):
                result = self.run_script(DEPLOY / "verify-image.sh", mode)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual("", self.output.read_text())

    def prepare_deploy(self, existing=True):
        app = self.root / "app"
        release = self.root / "release"
        app.mkdir()
        release.mkdir()
        self.env["PANTRYPREP_DEPLOY_DIR"] = str(app)
        for file in ("deploy.sh", "compose.yaml", "Caddyfile"):
            shutil.copy(DEPLOY / file, release / file)
        if existing:
            (app / ".env").write_text("APP_IMAGE=registry.test/old:tag\nPOSTGRES_PASSWORD=old-secret\n")
            (app / "compose.yaml").write_text("old compose\n")
            (app / "Caddyfile").write_text("old caddy\n")
        return app, release

    def deploy(self, release, mode):
        return self.run_script(release / "deploy.sh", mode,
                               ("registry.test/app@sha256:" + "a" * 64, "us-east-1", "https://app.example.test"))

    def test_healthy_release_stays_running(self):
        app, release = self.prepare_deploy()
        result = self.deploy(release, "success")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("sha256:" + "a" * 64, (app / ".env").read_text())
        self.assertEqual("False", (self.root / "restoring").read_text())
        self.assertNotIn("secret-for-test", result.stdout + result.stderr)

    def test_failed_startup_restores_previous_files_and_image(self):
        app, release = self.prepare_deploy()
        result = self.deploy(release, "up_failure")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Previous release restored", result.stderr)
        self.assertEqual("old compose\n", (app / "compose.yaml").read_text())
        self.assertEqual("old caddy\n", (app / "Caddyfile").read_text())
        self.assertIn("APP_IMAGE=pantryprep-rollback:previous", (app / ".env").read_text())
        self.assertIn("POSTGRES_PASSWORD=old-secret", (app / ".env").read_text())

    def test_failed_local_health_restores_previous_release(self):
        _, release = self.prepare_deploy()
        result = self.deploy(release, "health_failure")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Previous release restored", result.stderr)

    def test_failed_public_health_restores_previous_release(self):
        _, release = self.prepare_deploy()
        result = self.deploy(release, "public_failure")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Previous release restored", result.stderr)

    def test_proxy_startup_delay_is_retried(self):
        _, release = self.prepare_deploy()
        result = self.deploy(release, "proxy_delay")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue((self.root / "proxy_attempt").exists())

    def test_failed_rollback_is_reported(self):
        _, release = self.prepare_deploy()
        result = self.deploy(release, "rollback_failure")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("ROLLBACK FAILED", result.stderr)
        self.assertNotIn("Previous release restored", result.stderr)

    def test_first_deploy_failure_has_no_fake_rollback(self):
        _, release = self.prepare_deploy(existing=False)
        result = self.deploy(release, "up_failure")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("no previous release", result.stderr)
        self.assertNotIn("Previous release restored", result.stderr)

    def test_missing_data_mount_aborts_before_changing_stack(self):
        app, release = self.prepare_deploy()
        result = self.deploy(release, "no_mount")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("old compose\n", (app / "compose.yaml").read_text())
        self.assertFalse(any(call[0] == "docker" for call in self.calls()))
