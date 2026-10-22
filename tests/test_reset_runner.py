"""Affected two-device safety/mail checks; actual native journey is separately opt-in."""
import base64
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

from e2e import reset_runner as runner
from e2e import devices as d
import test_e2e_devices as controller_tests


class ResetRunnerTest(unittest.TestCase):
    def test_same_device_or_different_backend_refused_before_any_reset(self):
        first = {'owner': 'one'}
        with patch.object(runner.e, 'reset') as reset:
            with self.assertRaises(d.Refusal):
                runner.run(first, first)
            with patch.object(runner.r, 'attached', side_effect=[({}, {'owner': 'env-one'}), ({}, {'owner': 'env-two'})]):
                with self.assertRaises(d.Refusal):
                    runner.run(first, {'owner': 'two'})
            reset.assert_not_called()

    def test_mail_observation_only_reads_the_private_sink(self):
        link = 'https://reset.synthetic.invalid/reset-password?token=' + 'A' * 43
        message = ('Content-Type: text/plain; charset=UTF-8\r\n\r\n' + link + '\r\n').encode()
        environment = {'owner': 'synthetic'}
        with patch.object(runner.r, 'attached', return_value=({}, environment)), \
                patch.object(runner.e, 'admin', return_value=[{'bodyBase64': base64.b64encode(message).decode()}]) as admin, \
                patch.object(runner.e, 'deadline', side_effect=lambda check, _: check()):
            self.assertEqual(runner.observe({}), link)
            admin.assert_called_once_with(environment, '/admin/messages')

    def test_missing_multiple_or_foreign_mail_fails_observation(self):
        for messages in ([], [{}, {}], [{'bodyBase64': base64.b64encode(b'\r\nhttps://evil.invalid/reset-password?token=' + b'A' * 43).decode()}]):
            with patch.object(runner.r, 'attached', return_value=({}, {})), \
                    patch.object(runner.e, 'admin', return_value=messages), \
                    patch.object(runner.e, 'deadline', side_effect=lambda check, _: check()):
                with self.assertRaises(d.Refusal):
                    runner.observe({})


class OwnedPeerTest(unittest.TestCase):
    def setUp(self):
        controller_tests.DeviceControllerTest.setUp(self)

    def test_unpaired_unknown_and_changed_peer_are_refused_without_adoption(self):
        fields = [['emulator-5564', 'device'], ['emulator-5566', 'device']]
        with self.assertRaises(d.Refusal):
            d.selected_devices(self.value, fields)
        peer = dict(self.value, directory=str(self.root / 'peer'), owner='b' * 64,
                    config=dict(self.value['config'], consolePort=5566))
        d.write_json(self.directory / 'observed-peers.json', {'owner': self.value['owner'], 'peers': {
            'emulator-5566': {'directory': peer['directory'], 'owner': peer['owner']}}})
        with patch.object(d, 'directory_path', side_effect=lambda path: path), patch.object(d, 'load', return_value=peer), \
                patch.object(d, 'windows', return_value={}), patch.object(d, 'check_windows') as check, \
                patch.object(d, 'adb', return_value=subprocess.CompletedProcess([], 0, 'peer\n')) as adb:
            self.assertEqual(d.selected_devices(self.value, fields), fields[:1])
            check.assert_called_once()
            self.assertEqual(adb.call_args.args[1:], ('-s', 'emulator-5566', 'shell', 'getprop', 'ro.kernel.qemu.avd_name'))
            with self.assertRaises(d.Refusal):
                d.selected_devices(self.value, fields + [['emulator-5568', 'device']])
            peer['owner'] = 'changed'
            with self.assertRaises(d.Refusal):
                d.selected_devices(self.value, fields)
            peer['owner'] = 'b' * 64
            Path(peer['directory']).mkdir()
            Path(peer['directory'], 'cleaned.json').write_text('{}')
            self.assertEqual(d.selected_devices(self.value, [fields[0], ['emulator-5566', 'offline']]), fields[:1])
            with self.assertRaises(d.Refusal):
                d.selected_devices(self.value, fields)  # A connected cleaned peer cannot be adopted.


if __name__ == '__main__':
    unittest.main()
