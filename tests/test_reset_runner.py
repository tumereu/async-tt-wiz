"""Affected owned-peer safety/mail checks; actual native journey is separately opt-in."""
import base64
import json
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

    def test_complete_three_peer_set_preflights_before_publication_and_refuses_foreign_discovery(self):
        values = [self.value]
        for index, owner in enumerate(('b' * 64, 'c' * 64), 1):
            directory = self.root / ('peer-' + str(index))
            directory.mkdir(mode=0o700)
            values.append(dict(self.value, directory=str(directory), owner=owner,
                               config=dict(self.config, consolePort=5564 + index * 2, adbPort=15038 + index,
                                           appiumPort=14733 + index, systemPort=18210 + index)))
        for value in values:
            d.write_json(Path(value['directory']) / 'instance.json', value)

        def snapshot(value, operation):
            self.assertEqual(operation, 'snapshot')
            result = controller_tests.DeviceControllerTest.snapshot(self)
            result['state'].update(name=Path(value['directory']).name, avd=Path(value['directory']).name,
                                   owner=value['owner'])
            result['listeners'][0]['port'] = value['config']['adbPort']
            return result

        with patch.object(d, 'directory_path', side_effect=Path), patch.object(d, 'windows', side_effect=snapshot):
            runner.pair(*values[:2])  # Existing two-peer API still publishes exactly one peer each.
            files = [Path(value['directory']) / 'observed-peers.json' for value in values]
            original = [file.read_bytes() for file in files[:2]]
            with patch.object(d, 'check_windows', side_effect=[None, None, d.Refusal('foreign third snapshot')]):
                with self.assertRaises(d.Refusal):
                    runner.pair_owned(*values)
            self.assertEqual([file.read_bytes() for file in files[:2]], original)
            self.assertFalse(files[2].exists())

            runner.pair_owned(*values)
            members = {'emulator-' + str(value['config']['consolePort']): value for value in values}
            fields = [[serial, 'device'] for serial in members]
            with patch.object(d, 'adb', side_effect=lambda value, *args: subprocess.CompletedProcess(
                    [], 0, Path(members[args[1]]['directory']).name + '\n')):
                for value, file in zip(values, files):
                    self.assertEqual(json.loads(file.read_text()), {'owner': value['owner'], 'peers': {
                        serial: {'owner': peer['owner'], 'directory': peer['directory']}
                        for serial, peer in members.items() if peer['owner'] != value['owner']}})
                    self.assertEqual(d.selected_devices(value, fields), [
                        ['emulator-' + str(value['config']['consolePort']), 'device']])
                    with self.assertRaises(d.Refusal):
                        d.selected_devices(value, fields + [['emulator-5570', 'device']])

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
