#!/usr/bin/env python3
"""Qualify save.sh against disposable local Git remotes; no model/product work."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]


class SaveTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="atw-save-test-", dir="/tmp/opencode")
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.repo = self.base / "repo"
        self.repo.mkdir()
        self.remote = self.base / "remote.git"
        self.git("init", "--bare", str(self.remote))
        self.git("init", "-b", "main")
        for key, value in [("user.name", "Save Fixture"),
                           ("user.email", "save-fixture@example.invalid"),
                           ("core.autocrlf", "false"), ("core.whitespace", "blank-at-eol,blank-at-eof,space-before-tab")]:
            self.git("config", key, value)
        self.git("remote", "add", "origin", str(self.remote))
        (self.repo / "scripts").mkdir()
        shutil.copy2(ROOT / "scripts/save.sh", self.repo / "scripts/save.sh")
        shutil.copy2(ROOT / ".gitattributes", self.repo / ".gitattributes")
        (self.repo / "tracked.txt").write_text("baseline\n")
        self.git("add", ".")
        self.git("commit", "-m", "Fixture baseline")
        self.git("push", "-u", "origin", "main")
        self.start = self.git("rev-parse", "HEAD")

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.repo,
                                       stderr=subprocess.STDOUT).decode().strip()

    def save(self, *paths):
        return subprocess.run(["bash", "scripts/save.sh", *paths], cwd=self.repo,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

    def assert_pushed(self):
        self.assertEqual(self.git("rev-parse", "HEAD"),
                         self.git("--git-dir=" + str(self.remote), "rev-parse", "refs/heads/main"))

    def test_crlf_wrapper_is_normalized_and_pushes(self):
        data = b"@echo off\r\necho Gradle\r\n"
        (self.repo / "gradlew.bat").write_bytes(data)
        (self.repo / "unrelated.txt").write_text("leave this alone\n")
        result = self.save("gradlew.bat")
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual(self.git("show", "HEAD:gradlew.bat"), "@echo off\necho Gradle")
        self.assertEqual((self.repo / "gradlew.bat").read_bytes(), data)
        self.assertEqual(self.git("diff", "--check", self.start, "HEAD"), "")
        self.assertEqual(self.git("status", "--porcelain"), "?? unrelated.txt")
        self.assert_pushed()

    def test_real_trailing_whitespace_in_crlf_still_fails(self):
        (self.repo / "gradlew.bat").write_bytes(b"@echo off \r\n")
        result = self.save("gradlew.bat")
        self.assertEqual(result.returncode, 2, result.stdout)
        self.assertIn(b"staged whitespace validation failed", result.stdout)
        self.assertEqual(self.git("rev-parse", "HEAD"), self.start)
        self.assertEqual(self.git("diff", "--cached", "--name-only"), "gradlew.bat")
        self.assert_pushed()

    def test_previously_staged_crlf_is_renormalized_after_attribute_repair(self):
        (self.repo / ".gitattributes").write_text("*.pdf binary\n")
        (self.repo / "gradlew.bat").write_bytes(b"@echo off\r\n")
        self.git("add", "gradlew.bat")
        self.assertIn(b"\r\n", subprocess.check_output(["git", "show", ":gradlew.bat"], cwd=self.repo))
        shutil.copy2(ROOT / ".gitattributes", self.repo / ".gitattributes")
        result = self.save(".gitattributes", "gradlew.bat")
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual(subprocess.check_output(["git", "show", "HEAD:gradlew.bat"], cwd=self.repo), b"@echo off\n")
        self.assert_pushed()

    def test_deletion_and_literal_special_character_paths(self):
        (self.repo / "tracked.txt").unlink()
        (self.repo / "owned[1].txt").write_text("owned content\n")
        (self.repo / "owned1.txt").write_text("unrelated content \n")
        result = self.save("tracked.txt", "owned[1].txt")
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual(self.git("status", "--porcelain"), "?? owned1.txt")
        self.assertEqual(self.git("ls-tree", "--name-only", "HEAD", "tracked.txt"), "")
        self.assert_pushed()

    def test_no_paths_preserves_unstaged_changes_and_retries_push(self):
        (self.repo / "tracked.txt").write_text("staged version\n")
        self.git("add", "tracked.txt")
        (self.repo / "tracked.txt").write_text("unstaged version\n")
        result = self.save()
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual(self.git("show", "HEAD:tracked.txt"), "staged version")
        self.assertEqual((self.repo / "tracked.txt").read_text(), "unstaged version\n")
        self.assert_pushed()
        head = self.git("rev-parse", "HEAD")
        result = self.save()
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual(self.git("rev-parse", "HEAD"), head)
        self.assert_pushed()

    def test_push_failure_can_be_retried_without_another_commit(self):
        hook = self.remote / "hooks/pre-receive"
        hook.write_text("#!/bin/sh\nexit 1\n")
        hook.chmod(0o755)
        (self.repo / "tracked.txt").write_text("owned change\n")
        result = self.save("tracked.txt")
        self.assertNotEqual(result.returncode, 0)
        head = self.git("rev-parse", "HEAD")
        self.assertNotEqual(head, self.start)
        self.assertEqual(self.git("--git-dir=" + str(self.remote), "rev-parse", "refs/heads/main"), self.start)
        hook.unlink()  # Only the owned disposable fixture's deliberate failure hook.
        result = self.save("tracked.txt")
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual(self.git("rev-parse", "HEAD"), head)
        self.assert_pushed()


if __name__ == "__main__":
    unittest.main()
