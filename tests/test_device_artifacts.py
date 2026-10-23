"""Host-only artifact ownership/storage regressions; never connects to a device."""
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from e2e import device_artifacts as artifacts, devices as d


class DeviceArtifactsTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(dir='/tmp/opencode', prefix='atw-artifact-unit-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.value = {'owner': 'a' * 64, 'directory': '/tmp/opencode/atw-device-' + 'b' * 32}
        for patcher in (patch.object(d, 'ROOT', self.root), patch.object(d, 'BASE', self.root / 'approved-tmp'),
                        patch.dict(os.environ, {}, clear=False)):
            patcher.start()
            self.addCleanup(patcher.stop)
        os.environ.pop('ATW_DEVICE_ARTIFACT_ROOT', None)

    def test_default_is_private_worktree_build_not_device_tmpfs(self):
        path = artifacts.directory(self.value)
        self.assertEqual(path.parent, self.root / 'e2e/build/device-artifacts')
        self.assertEqual(path.stat().st_mode & 0o777, 0o700)
        self.assertEqual(json.loads((path / 'owner.json').read_text()), self.value_to_identity())
        self.assertEqual(artifacts.directory(self.value), path)

    def value_to_identity(self):
        return {'owner': self.value['owner'], 'deviceDirectory': self.value['directory']}

    def test_explicit_root_stays_within_existing_grants(self):
        for root in (self.root / 'e2e/build/custom-artifacts', d.BASE / 'explicit-artifacts'):
            with patch.dict(os.environ, ATW_DEVICE_ARTIFACT_ROOT=str(root)):
                self.assertEqual(artifacts.directory(self.value).parent, root)

    def test_outside_build_and_approved_tmp_relative_or_traversal_refused(self):
        for root in ('relative', '/var/tmp/not-granted', str(self.root / 'not-build'),
                     str(self.root / 'e2e/build/../outside'), str(d.BASE), str(self.root / 'e2e/build')):
            with self.subTest(root=root), patch.dict(os.environ, ATW_DEVICE_ARTIFACT_ROOT=root), self.assertRaises(d.Refusal):
                artifacts.directory(self.value)

    def test_distinct_owners_keep_partial_and_completed_artifacts_without_cleanup(self):
        first = artifacts.directory(self.value)
        partial = first / 'partial.apk'
        partial.write_bytes(b'partial evidence')
        second = artifacts.directory(dict(self.value, owner='c' * 64))
        self.assertNotEqual(first, second)
        self.assertEqual(partial.read_bytes(), b'partial evidence')
        self.assertEqual(artifacts.directory(self.value), first)

    def test_changed_device_identity_owner_record_and_public_namespace_refused(self):
        path = artifacts.directory(self.value)
        with self.assertRaises(d.Refusal):
            artifacts.directory(dict(self.value, directory='/foreign'))
        (path / 'owner.json').write_text('{}')
        with self.assertRaises(d.Refusal):
            artifacts.directory(self.value)
        (path / 'owner.json').write_text(json.dumps(self.value_to_identity()))
        path.chmod(0o755)
        with self.assertRaises(d.Refusal):
            artifacts.directory(self.value)

    def test_symlink_root_and_owner_record_refused_without_touching_target(self):
        target = self.root / 'target'
        target.mkdir()
        link = self.root / 'e2e/build/link'
        link.parent.mkdir(parents=True)
        link.symlink_to(target, target_is_directory=True)
        with patch.dict(os.environ, ATW_DEVICE_ARTIFACT_ROOT=str(link)), self.assertRaises(d.Refusal):
            artifacts.directory(self.value)
        self.assertEqual(list(target.iterdir()), [])
        path = artifacts.directory(self.value)
        (path / 'owner.json').unlink()
        foreign = target / 'owner.json'
        foreign.write_text(json.dumps(self.value_to_identity()))
        (path / 'owner.json').symlink_to(foreign)
        with self.assertRaises(d.Refusal):
            artifacts.directory(self.value)


if __name__ == '__main__':
    unittest.main()
