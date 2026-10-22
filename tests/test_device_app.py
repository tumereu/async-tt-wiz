"""Focused controller failures, NOT Android/app acceptance (see actual checker)."""
import json
from pathlib import Path
import sqlite3
import struct
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

from e2e import device_app as a
from e2e import devices as d


class NormalAppControllerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir='/tmp/opencode', prefix='atw-app-unit-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.value = {'directory': str(self.root), 'owner': 'a' * 64,
                      'config': {'androidHome': str(self.root), 'linuxAdb': '/explicit/adb', 'adbPort': 15038}}
        tools = self.root / 'build-tools/36.0.0'
        tools.mkdir(parents=True)
        for name in ('aapt2', 'apksigner'):
            (tools / name).touch()
        self.apk = self.root / 'input.apk'
        self.apk.write_bytes(b'unit-only fake APK; never installed')
        self.badging = (f"package: name='{a.PACKAGE}' versionCode='1' versionName='0.1.0'\n"
                        f"minSdkVersion:'29'\ntargetSdkVersion:'36'\napplication-debuggable\nlaunchable-activity: name='{a.ACTIVITY}'\n")
        self.signature = 'Signer #1 certificate SHA-256 digest: ' + 'b' * 64 + '\n'
        self.expected = {'sha256': a.sha(self.apk), 'bytes': self.apk.stat().st_size, 'package': a.PACKAGE,
                         'activity': a.ACTIVITY, 'minSdk': 29, 'targetSdk': 36, 'versionCode': 1,
                         'versionName': '0.1.0', 'signerSha256': ['b' * 64]}
        self.serial = 'emulator-5564'

    @staticmethod
    def reply(text, code=0):
        return subprocess.CompletedProcess([], code, text)

    def verify(self, badging=None, manifest='E: manifest', signature=None):
        with patch.object(d, 'run', side_effect=[self.reply(badging or self.badging), self.reply(manifest),
                                               self.reply(signature if signature is not None else self.signature)]):
            return a.verify_apk(self.value, self.apk)

    def test_verified_package_launcher_api_and_signer(self):
        self.assertEqual(self.verify(), self.expected)

    def test_wrong_package_launcher_api_testonly_or_unsigned_refused(self):
        for text in (self.badging.replace(a.PACKAGE, 'foreign.package'),
                     self.badging.replace(a.ACTIVITY, a.PACKAGE + '.TestActivity'),
                     self.badging.replace("minSdkVersion:'29'", "minSdkVersion:'30'"),
                     self.badging.replace('application-debuggable', '')):
            with self.subTest(badging=text), self.assertRaises(d.Refusal):
                self.verify(badging=text)
        for manifest in ('testOnly=true', 'E: instrumentation'):
            with self.assertRaises(d.Refusal):
                self.verify(manifest=manifest)
        with self.assertRaises(d.Refusal):
            self.verify(signature='no valid signer identity')

    def test_invalid_missing_relative_or_symlink_apk_refused(self):
        link = self.root / 'link.apk'
        link.symlink_to(self.apk)
        for path in (link, Path('relative.apk'), self.root / 'absent.apk'):
            with self.subTest(path=path), self.assertRaises(d.Refusal), patch.object(d, 'run') as run:
                a.verify_apk(self.value, path)
            run.assert_not_called()

    def record(self):
        (self.root / 'normal.apk').write_bytes(self.apk.read_bytes())
        record = {'owner': self.value['owner'], 'serial': self.serial, 'apk': self.expected}
        d.write_json(self.root / 'app-installed.json', record)
        return record

    def test_install_failure_revokes_prior_install_and_ui_success(self):
        self.record()
        (self.root / 'app-ready.json').write_text('stale')
        with patch.object(d, 'infrastructure', return_value={'device': {'serial': self.serial}}), \
                patch.object(a, 'verify_apk', side_effect=d.Refusal('invalid APK')), patch.object(d, 'adb') as adb:
            with self.assertRaises(d.Refusal):
                a.operation(self.value, 'install-app', 1, str(self.apk))
            adb.assert_not_called()
        self.assertFalse((self.root / 'app-installed.json').exists())
        self.assertFalse((self.root / 'app-ready.json').exists())

    def test_install_command_failure_never_publishes_install(self):
        with patch.object(a, 'verify_apk', return_value=self.expected), patch.object(d, 'adb', side_effect=d.Refusal('install failure')):
            with self.assertRaises(d.Refusal):
                a.install(self.value, self.serial, self.apk)
        self.assertFalse((self.root / 'app-installed.json').exists())

    def test_changed_retained_apk_foreign_owner_or_guest_refused(self):
        for field in ('owner', 'serial', 'bytes'):
            record = self.record()
            if field == 'bytes':
                (self.root / 'normal.apk').write_text('changed')
            else:
                record[field] = 'foreign'
                d.write_json(self.root / 'app-installed.json', record)
            with patch.object(d, 'infrastructure', return_value={'device': {'serial': self.serial}}), patch.object(d, 'adb') as adb:
                with self.assertRaises(d.Refusal):
                    a.operation(self.value, 'launch-app', 1)
                adb.assert_not_called()

    def test_installed_bytes_must_match_even_with_right_package(self):
        def binary(value, serial, path, *args, **kwargs):
            path.write_bytes(b'other signed APK')
        with patch.object(d, 'adb', return_value=self.reply('package:/data/app/abc/base.apk\n')), \
                patch.object(a, 'binary_adb', side_effect=binary), \
                patch.object(a, 'verify_apk', return_value=dict(self.expected, sha256='c' * 64)):
            with self.assertRaises(d.Refusal):
                a.installed(self.value, self.serial, self.expected, self.root)

    def test_install_changed_copy_refused_before_mutation(self):
        with patch.object(a, 'verify_apk', side_effect=[self.expected, dict(self.expected, sha256='changed')]), patch.object(d, 'adb') as adb:
            with self.assertRaises(d.Refusal):
                a.install(self.value, self.serial, self.apk)
            adb.assert_not_called()

    def test_failed_am_launch_and_capture_remove_stale_handoff(self):
        self.record()
        for operation in ('launch-app', 'capture-app'):
            (self.root / 'app-ready.json').write_text('stale')
            with patch.object(d, 'infrastructure', return_value={'device': {'serial': self.serial}}), patch.object(a, 'installed'), \
                    patch.object(d, 'adb', return_value=self.reply('Error: unable to resolve Intent')), \
                    patch.object(a, 'observe', side_effect=d.Refusal('failed capture')):
                with self.assertRaises(d.Refusal):
                    a.operation(self.value, operation, 1)
            self.assertFalse((self.root / 'app-ready.json').exists())

    def tree(self, extra=None):
        root = ET.Element('hierarchy')
        for text in [a.TITLE, a.SUBTITLE] + ([extra] if extra else []):
            ET.SubElement(root, 'node', {'package': a.PACKAGE, 'text': text})
        return root

    def test_opening_failure_and_foreign_text_cannot_mean_storage_ready(self):
        self.assertTrue(a.visible(self.tree()))
        for text in ('Opening local storage…', 'Local storage could not be opened. Your data has not been erased.',
                     'Local data needs a newer app. Update the app without uninstalling.', 'Retry'):
            self.assertFalse(a.visible(self.tree(text)))
        root = self.tree()
        for node in root:
            node.set('package', 'foreign')
        self.assertFalse(a.visible(root))

    def test_startup_observation_timeout_is_bounded(self):
        path = self.root / 'hierarchy.xml'
        with patch.object(a, 'hierarchy', return_value=(path, self.tree('Opening local storage…'))), \
                patch.object(a, 'foreground', return_value=(True, {})), \
                patch.object(a.time, 'monotonic', side_effect=[0, 0, 2]), patch.object(a.time, 'sleep'):
            with self.assertRaises(d.Refusal):
                a.observe(self.value, self.serial, self.root, 1)
        self.assertFalse((self.root / 'app-ready.json').exists())

    def test_hierarchy_failure_retains_output_and_removes_only_its_unique_guest_file(self):
        for malformed in (True, False):
            def binary(value, serial, target, *args):
                target.write_bytes(b'<broken')
                if not malformed:
                    raise d.Refusal('failed binary transfer')
            with patch.object(d, 'adb', return_value=self.reply('')) as adb, patch.object(a, 'binary_adb', side_effect=binary):
                with self.assertRaises(d.Refusal):
                    a.hierarchy(self.value, self.serial, self.root, int(malformed))
                dump, cleanup = adb.call_args_list
                remote = dump.args[-1]
                self.assertRegex(remote, r'^/data/local/tmp/atw-[a-f0-9]{32}\.xml$')
                self.assertEqual(cleanup.args, (self.value, '-s', self.serial, 'shell', 'rm', '-f', remote))
                self.assertFalse(cleanup.kwargs['check'])

    def test_success_publishes_actual_hierarchy_pixels_and_readonly_storage(self):
        self.record()
        path = self.root / 'hierarchy.xml'
        path.write_text(ET.tostring(self.tree(), encoding='unicode'))
        def binary(value, serial, target, *args):
            target.write_bytes(b'\x89PNG\r\n\x1a\n' + b'\0' * 4 + b'IHDR' + struct.pack('>II', 720, 1280) + b'\0' * 100)
        with patch.object(a, 'hierarchy', return_value=(path, self.tree())), patch.object(a, 'foreground', return_value=(True, {'pid': '123'})), \
                patch.object(a, 'binary_adb', side_effect=binary), patch.object(a, 'storage', return_value={'readOnly': True}):
            observed = a.observe(self.value, self.serial, self.root, 1)
        self.assertEqual(observed, json.loads((self.root / 'app-ready.json').read_text()))
        self.assertEqual(observed['hierarchy']['sha256'], a.sha(path))
        self.assertEqual(observed['screenshot']['sha256'], a.sha(self.root / 'screen.png'))
        self.assertEqual(observed['screenshot']['width'], 720)
        self.assertEqual(observed['apk'], self.expected)
        self.assertTrue(observed['storage']['readOnly'])

    def test_bad_screenshot_or_lost_foreground_never_publishes_ready(self):
        self.record()
        path = self.root / 'hierarchy.xml'
        path.write_text(ET.tostring(self.tree(), encoding='unicode'))
        for invalid_png in (True, False):
            def binary(value, serial, target, *args):
                target.write_bytes(b'bad PNG' if invalid_png else b'\x89PNG\r\n\x1a\n' + b'\0' * 4 + b'IHDR' + struct.pack('>II', 720, 1280) + b'\0' * 100)
            with patch.object(a, 'hierarchy', return_value=(path, self.tree())), \
                    patch.object(a, 'foreground', side_effect=[(True, {}), (False, {})]), \
                    patch.object(a, 'binary_adb', side_effect=binary), patch.object(a, 'storage') as storage:
                with self.assertRaises(d.Refusal):
                    a.observe(self.value, self.serial, self.root, 1)
                storage.assert_not_called()
            self.assertFalse((self.root / 'app-ready.json').exists())

    def test_readonly_db_diagnostic_does_not_modify_capture(self):
        source = self.root / 'source.db'
        with sqlite3.connect(source) as db:
            for name in a.TABLES:
                db.execute('CREATE TABLE ' + name + ' (id TEXT)')
            db.execute('PRAGMA user_version=2')
        before = a.sha(source)
        def binary(value, serial, target, *args):
            target.write_bytes(source.read_bytes())
        with patch.object(d, 'adb', side_effect=[self.reply(''), self.reply('123')]), patch.object(a, 'binary_adb', side_effect=binary):
            result = a.storage(self.value, self.serial, self.root)
        self.assertEqual(result['sha256'], before)
        self.assertEqual(result['rowCounts'], dict.fromkeys(a.TABLES, 0))
        self.assertTrue(result['readOnly'])

    def test_active_journal_refused_before_raw_copy(self):
        with patch.object(d, 'adb', return_value=self.reply('-rw------- 1 u u 4096 date replica.db-wal')), patch.object(a, 'binary_adb') as binary:
            with self.assertRaises(d.Refusal):
                a.storage(self.value, self.serial, self.root)
            binary.assert_not_called()

    def test_binary_timeout_retains_partial_artifact_not_decoded_as_text(self):
        def failure(*args, **kwargs):
            kwargs['stdout'].write(b'\xff\x00partial')
            raise subprocess.TimeoutExpired(args[0], 1, stderr=b'timeout')
        with patch.object(d.socket, 'create_connection'), patch.object(a.subprocess, 'run', side_effect=failure):
            with self.assertRaises(d.Refusal):
                a.binary_adb(self.value, self.serial, self.root / 'partial', 'exec-out', 'screencap', '-p', timeout=1)
        record = json.loads((self.root / 'commands.jsonl').read_text())
        self.assertTrue(record['timedOut'])
        self.assertEqual(record['sha256'], a.sha(self.root / 'partial'))
        self.assertNotIn('output', record)

    def test_launch_retains_data_and_capture_does_not_launch(self):
        self.record()
        for operation in ('launch-app', 'capture-app'):
            with patch.object(d, 'infrastructure', return_value={'device': {'serial': self.serial}}), patch.object(a, 'installed'), \
                    patch.object(d, 'adb', return_value=self.reply('Status: ok\n')) as adb, patch.object(a, 'observe', return_value={'ok': True}):
                a.operation(self.value, operation, 1)
                commands = [c.args[2:] for c in adb.call_args_list]
                self.assertEqual(commands, [] if operation == 'capture-app' else [
                    (self.serial, 'shell', 'am', 'force-stop', a.PACKAGE),
                    (self.serial, 'shell', 'am', 'start', '-W', '-n', a.COMPONENT)])


if __name__ == '__main__':
    unittest.main()
