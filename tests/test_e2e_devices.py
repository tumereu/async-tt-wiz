"""Device-controller checks; fake process results here are NOT device acceptance."""
import contextlib
import copy
import fcntl
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from e2e import devices as d


class DeviceControllerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='atw-device-unit-', dir='/tmp/opencode')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        main = self.root / 'tools/node_modules/appium/build/lib/main.js'
        main.parent.mkdir(parents=True)
        main.write_text('test-only placeholder; never launched')
        (main.parents[2] / 'package.json').write_text(json.dumps({'name': 'appium', 'version': '3.8.0'}))
        home = self.root / 'driver-home'
        driver = home / 'node_modules/appium-uiautomator2-driver/package.json'
        driver.parent.mkdir(parents=True)
        driver.write_text(json.dumps({'name': 'appium-uiautomator2-driver', 'version': '8.7.0'}))
        adb = self.root / 'sdk/platform-tools/adb'
        adb.parent.mkdir(parents=True)
        adb.touch()
        self.config = {'node': '/usr/bin/python3', 'appiumMain': str(main), 'appiumHome': str(home),
                       'androidHome': str(self.root / 'sdk'), 'linuxAdb': str(adb),
                       'powershell': '/usr/bin/python3', 'windowsSdk': r'C:\Android',
                       'adbPort': 15038, 'consolePort': 5564, 'appiumPort': 14733, 'systemPort': 18210}
        self.directory = self.root / 'instance'
        self.directory.mkdir(mode=0o700)
        self.value = {'directory': str(self.directory), 'owner': 'a' * 64, 'config': self.config}

    def refuse(self, call, *args):
        with self.assertRaises(d.Refusal):
            call(*args)

    def test_explicit_config_validates_without_launch(self):
        self.assertEqual(self.config, d.validate_config(self.config))

    def test_ambient_listener_disappearance_is_reported_without_false_failure(self):
        listener = {'address': '::1', 'pid': 16360, 'port': 44379}
        before = {'listeners': [listener], 'androidProcesses': []}
        original = copy.deepcopy(before)
        self.assertEqual(d.inventory_changes(before, {'listeners': [], 'androidProcesses': []}),
                         {'listeners': {'removed': [listener], 'added': []}})
        self.assertEqual(before, original)

    def test_inventory_delta_reports_changed_process_identity_and_container_churn(self):
        before = {'androidProcesses': [{'pid': 7, 'created': 'old'}], 'container': ['old-container']}
        after = {'androidProcesses': [{'pid': 7, 'created': 'new'}], 'container': ['new-container']}
        self.assertEqual(d.inventory_changes(before, after), {
            'androidProcesses': {'removed': before['androidProcesses'], 'added': after['androidProcesses']},
            'container': {'removed': ['old-container'], 'added': ['new-container']},
        })

    def test_inventory_order_and_dictionary_order_do_not_manufacture_changes(self):
        before = {'listeners': [{'pid': 1, 'port': 2}, {'pid': 3, 'port': 4}]}
        after = {'listeners': [{'port': 4, 'pid': 3}, {'port': 2, 'pid': 1}]}
        self.assertEqual(d.inventory_changes(before, after), {})

    def test_duplicate_ports_default_adb_and_console_range_refused(self):
        for changes in ({'systemPort': 15038}, {'systemPort': 5565}, {'adbPort': 5037},
                        {'consolePort': 5563}, {'consolePort': 15554}, {'adbPort': True}, {'adbPort': 65535}):
            with self.subTest(changes=changes):
                self.refuse(d.validate_config, dict(self.config, **changes))

    def test_missing_unknown_or_relative_tool_configuration_refused(self):
        for changes in ({'node': 'node'}, {'node': '/not-an-installed-tool'},
                        {'androidHome': '.'}, {'windowsSdk': r'C:\Android; stop-process'}, {'extra': 1}):
            with self.subTest(changes=changes):
                self.refuse(d.validate_config, dict(self.config, **changes))
        del self.config['systemPort']
        self.refuse(d.validate_config, self.config)

    def test_unqualified_driver_or_global_home_refused(self):
        driver = Path(self.config['appiumHome']) / 'node_modules/appium-uiautomator2-driver/package.json'
        driver.write_text(json.dumps({'name': 'appium-uiautomator2-driver', 'version': '9.0.0'}))
        self.refuse(d.validate_config, self.config)
        self.refuse(d.validate_config, dict(self.config, appiumHome=str(Path.home() / '.appium')))

    def test_appium_adb_and_controller_adb_must_match(self):
        self.refuse(d.validate_config, dict(self.config, linuxAdb='/usr/bin/python3'))

    def test_directory_traversal_symlink_and_foreign_root_refused(self):
        for path in ('relative', '/tmp/atw-device-' + 'a' * 32, '/tmp/opencode/atw-device-a',
                     '/tmp/opencode/../atw-device-' + 'a' * 32):
            self.refuse(d.directory_path, path)
        self.assertEqual(Path('/tmp/opencode/atw-device-' + 'a' * 32),
                         d.directory_path('/tmp/opencode/atw-device-' + 'a' * 32))

    def test_global_controller_lock_contention_is_nonblocking(self):
        with patch.object(d, 'BASE', self.root), d.lock():
            self.refuse(lambda: contextlib.ExitStack().enter_context(d.lock()))

    def test_loading_rejects_foreign_or_public_instance(self):
        (self.directory / 'instance.json').write_text(json.dumps(self.value))
        self.assertEqual(d.load(self.directory), self.value)
        self.directory.chmod(0o755)
        self.refuse(d.load, self.directory)
        self.directory.chmod(0o700)
        value = dict(self.value, directory='/somebody-else')
        (self.directory / 'instance.json').write_text(json.dumps(value))
        self.refuse(d.load, self.directory)

    def snapshot(self):
        processes = [{'pid': n, 'created': str(n), 'command': 'owned', 'executable': 'owned'} for n in range(1, 4)]
        return {'state': {'name': self.directory.name, 'avd': self.directory.name,
                          'owner': self.value['owner'], 'processes': processes},
                'currentProcesses': copy.deepcopy(processes),
                'listeners': [{'port': 15038, 'address': '127.0.0.1', 'pid': 1}]}

    def test_windows_foreign_avd_reused_pid_or_missing_process_refused(self):
        good = self.snapshot()
        d.check_windows(self.value, good)
        for field in ('owner', 'avd', 'name'):
            bad = copy.deepcopy(good)
            bad['state'][field] = 'foreign'
            self.refuse(d.check_windows, self.value, bad)
        bad = copy.deepcopy(good)
        bad['currentProcesses'][0]['created'] = 'reused'
        self.refuse(d.check_windows, self.value, bad)
        bad = copy.deepcopy(good)
        bad['currentProcesses'].pop()
        self.refuse(d.check_windows, self.value, bad)

    def test_adb_wildcard_or_missing_listener_refused(self):
        for address in ('0.0.0.0', '::', '::1'):
            bad = self.snapshot()
            bad['listeners'][0]['address'] = address
            self.refuse(d.check_windows, self.value, bad)
        bad['listeners'] = []
        self.refuse(d.check_windows, self.value, bad)

    def completed(self, text, code=0):
        return subprocess.CompletedProcess([], code, text)

    def device_adb(self, responses):
        def reply(value, *args, **kwargs):
            if args == ('devices', '-l'):
                return self.completed('List of devices attached\nemulator-5564 device product:test\n')
            if args == ('forward', '--list') or args[-2:] == ('reverse', '--list'):
                return self.completed('')
            return self.completed(responses.get(tuple(args[3:]), ''))
        return reply

    def ready_properties(self):
        return {('getprop', 'ro.kernel.qemu.avd_name'): self.directory.name,
                ('getprop', 'sys.boot_completed'): '1', ('getprop', 'ro.build.version.sdk'): '29',
                ('pm', 'list', 'packages'): 'package:android\npackage:com.android.settings\n',
                ('getprop', 'ro.build.fingerprint'): 'synthetic-test-fingerprint'}

    def test_actual_boot_and_package_manager_required(self):
        properties = self.ready_properties()
        with patch.object(d, 'adb', side_effect=self.device_adb(properties)):
            result = d.check_device(self.value)
            self.assertTrue(result['bootCompleted'] and result['packageManager'])
        for key in (('getprop', 'sys.boot_completed'), ('pm', 'list', 'packages')):
            pending = dict(properties)
            pending[key] = ''
            with patch.object(d, 'adb', side_effect=self.device_adb(pending)):
                self.assertIsNone(d.check_device(self.value))

    def test_foreign_serial_or_mismatched_avd_api_gms_refused(self):
        with patch.object(d, 'adb', return_value=self.completed('List of devices attached\nemulator-5554 device\n')):
            self.refuse(d.check_device, self.value)
        for key, value in ((('getprop', 'ro.kernel.qemu.avd_name'), 'foreign'),
                           (('getprop', 'ro.build.version.sdk'), '28'),
                           (('pm', 'list', 'packages'), 'package:android\npackage:com.google.android.gms\n')):
            props = dict(self.ready_properties())
            props[key] = value
            with patch.object(d, 'adb', side_effect=self.device_adb(props)):
                self.refuse(d.check_device, self.value)

    def test_offline_or_absent_device_not_ready(self):
        for text in ('List of devices attached\n', 'List of devices attached\nemulator-5564 offline\n'):
            with patch.object(d, 'adb', return_value=self.completed(text)):
                self.assertIsNone(d.check_device(self.value))

    def test_reused_appium_pid_never_signalled(self):
        recorded = {'pid': 1234, 'startTicks': '1', 'cmdline': ('node\0' + self.config['appiumMain'] + '\0' + str(self.directory)).encode().hex()}
        (self.directory / 'appium.json').write_text(json.dumps(recorded))
        with patch.object(d, 'process_identity', return_value=dict(recorded, startTicks='2')):
            self.refuse(d.appium_identity, self.value)

    def test_real_loopback_listener_must_belong_to_owned_pid(self):
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            listener.listen()
            self.config['appiumPort'] = listener.getsockname()[1]
            with patch.object(d, 'appium_identity', return_value={'pid': os.getpid()}):
                self.assertTrue(d.appium_listener(self.value))
            with patch.object(d, 'appium_identity', return_value={'pid': os.getppid()}):
                self.refuse(d.appium_listener, self.value)

    def test_readiness_timeout_invalidates_stale_handoff(self):
        (self.directory / 'ready.json').write_text('stale success')
        with patch.object(d, 'windows', return_value=self.snapshot()), patch.object(d, 'check_device', return_value=None), \
                patch.object(d, 'appium_status', return_value={'value': {'ready': True}}), \
                patch.object(d.time, 'monotonic', side_effect=[0, 0, 2]), patch.object(d.time, 'sleep'):
            self.refuse(d.wait, self.value, 1)
        self.assertFalse((self.directory / 'ready.json').exists())

    def test_readiness_does_not_retry_foreign_identity(self):
        with patch.object(d, 'windows', return_value=self.snapshot()), \
                patch.object(d, 'check_device', side_effect=d.Refusal('foreign')) as check:
            self.refuse(d.wait, self.value, 1)
            self.assertEqual(check.call_count, 1)

    def test_failed_appium_connection_never_writes_ready(self):
        with patch.object(d, 'windows', return_value=self.snapshot()), patch.object(d, 'check_device', return_value={'api': '29'}), \
                patch.object(d, 'appium_status', side_effect=ConnectionRefusedError()), \
                patch.object(d.time, 'monotonic', side_effect=[0, 0, 2]), patch.object(d.time, 'sleep'):
            self.refuse(d.wait, self.value, 1)
        self.assertFalse((self.directory / 'ready.json').exists())

    def test_success_rechecks_ownership_before_ready(self):
        with patch.object(d, 'windows', return_value=self.snapshot()) as win, \
                patch.object(d, 'check_device', return_value={'api': '29'}), \
                patch.object(d, 'appium_status', return_value={'value': {'ready': True}}):
            d.wait(self.value, 1)
        self.assertEqual(win.call_count, 2)
        self.assertEqual(json.loads((self.directory / 'ready.json').read_text())['owner'], self.value['owner'])

    def test_cleanup_captures_before_mutation_and_foreign_failure_stops(self):
        (self.directory / 'ready.json').write_text('stale')
        with patch.object(d, 'capture', side_effect=d.Refusal('foreign')), patch.object(d.os, 'kill') as kill, \
                patch.object(d, 'windows') as win:
            self.refuse(d.cleanup, self.value)
            kill.assert_not_called()
            win.assert_not_called()
        self.assertFalse((self.directory / 'ready.json').exists())

    def test_no_default_adb_fallback_when_dedicated_server_is_down(self):
        with patch.object(d.socket, 'create_connection', side_effect=ConnectionRefusedError()), patch.object(d, 'run') as run:
            with self.assertRaises(ConnectionRefusedError):
                d.adb(self.value, 'devices')
            run.assert_not_called()

    def test_foreign_forward_and_reverse_mappings_refused_without_removal(self):
        for index, output in enumerate(('emulator-5564 tcp:18210 tcp:6790\n', 'UsbFfs tcp:18080 tcp:18080\n')):
            with self.subTest(index=index), patch.object(d, 'adb', side_effect=[
                    self.completed(output if index == 0 else ''), self.completed(output if index == 1 else '')]) as adb:
                self.refuse(d.check_mappings, self.value, 'emulator-5564')
                self.assertEqual([c.args[1:] for c in adb.call_args_list],
                                 [('forward', '--list')] + ([] if index == 0 else
                                 [('-s', 'emulator-5564', 'reverse', '--list')]))

    def test_empty_mapping_inventory_is_explicit(self):
        with patch.object(d, 'adb', return_value=self.completed('')):
            self.assertEqual(d.check_mappings(self.value, 'emulator-5564'), {'forward': [], 'reverse': []})

    def test_foreign_adb_listener_pid_refused(self):
        snapshot = self.snapshot()
        snapshot['listeners'][0]['pid'] = 999
        self.refuse(d.check_windows, self.value, snapshot)

    def test_boot_and_package_manager_timeouts_record_distinct_stages(self):
        for key, stage in ((('getprop', 'sys.boot_completed'), 'android-boot'),
                           (('pm', 'list', 'packages'), 'package-manager')):
            with self.subTest(stage=stage):
                props = dict(self.ready_properties())
                props[key] = ''
                (self.directory / 'ready.json').write_text('stale')
                with patch.object(d, 'windows', return_value=self.snapshot()), \
                        patch.object(d, 'adb', side_effect=self.device_adb(props)), \
                        patch.object(d, 'appium_status', return_value={'value': {'ready': True}}), \
                        patch.object(d.time, 'monotonic', side_effect=[0, 0, 2]), patch.object(d.time, 'sleep'):
                    self.refuse(d.wait, self.value, 1)
                self.assertEqual(json.loads((self.directory / 'readiness-progress.json').read_text())['stage'], stage)
                self.assertFalse((self.directory / 'ready.json').exists())

    def test_windows_launch_failure_keeps_owner_and_never_starts_appium(self):
        directory = self.root / 'fresh'
        with patch.object(d, 'BASE', self.root), patch.object(d, 'linux_listening', return_value=False), \
                patch.object(d, 'windows', side_effect=[{'listeners': []}, d.Refusal('detached launch failed')]), \
                patch.object(d.subprocess, 'Popen') as launch:
            self.refuse(d.provision, directory, self.config, 1)
            launch.assert_not_called()
        self.assertTrue((directory / 'instance.json').exists())
        self.assertFalse((directory / 'ready.json').exists())

    def test_busy_windows_and_linux_ports_refused_before_start(self):
        for system in ('windows', 'linux'):
            directory = self.root / system
            baseline = {'listeners': [{'port': 15038}]} if system == 'windows' else {'listeners': []}
            with patch.object(d, 'windows', return_value=baseline) as windows, \
                    patch.object(d, 'linux_listening', return_value=system == 'linux'):
                self.refuse(d.provision, directory, self.config, 1)
                self.assertEqual(windows.call_count, 1)

    def test_unfinished_instance_ports_reserved_before_start(self):
        other = self.root / ('atw-device-' + 'b' * 32)
        other.mkdir()
        (other / 'instance.json').write_text(json.dumps(self.value))
        with patch.object(d, 'BASE', self.root), patch.object(d, 'windows', return_value={'listeners': []}) as windows, \
                patch.object(d, 'linux_listening', return_value=False):
            self.refuse(d.provision, self.root / 'fresh', self.config, 1)
            self.assertEqual(windows.call_count, 1)

    def test_cleanup_refuses_foreign_mapping_before_any_signal(self):
        snapshot = self.snapshot()
        snapshot['currentProcesses'][0]['executable'] = r'C:\Android\platform-tools\adb.exe'
        with patch.object(d, 'capture'), patch.object(d, 'windows', return_value=snapshot), \
                patch.object(d, 'adb', side_effect=[self.completed('List of devices attached\nemulator-5564 device\n'),
                                                 self.completed('foreign tcp:1 tcp:2\n')]), \
                patch.object(d.os, 'kill') as kill:
            self.refuse(d.cleanup, self.value)
            kill.assert_not_called()

    def test_cleanup_partial_windows_start_without_adb_is_recoverable(self):
        with patch.object(d, 'capture'), patch.object(d, 'windows', return_value={'currentProcesses': []}), \
                patch.object(d, 'appium_identity', return_value=None), patch.object(d, 'linux_listening', return_value=False), \
                patch.object(d, 'adb') as adb:
            d.cleanup(self.value)
            adb.assert_not_called()
        self.assertTrue((self.directory / 'cleaned.json').exists())

    def test_detached_appium_launch_failure_retains_windows_ownership(self):
        directory = self.root / 'fresh'
        snapshot = self.snapshot()
        snapshot['state']['name'] = snapshot['state']['avd'] = directory.name
        with patch.object(d, 'BASE', self.root), patch.object(d.secrets, 'token_hex', return_value=self.value['owner']), \
                patch.object(d, 'linux_listening', return_value=False), \
                patch.object(d, 'windows', side_effect=[{'listeners': []}, snapshot]), \
                patch.object(d, 'run', return_value=self.completed('version')), \
                patch.object(d.subprocess, 'Popen', side_effect=OSError('controlled launch failure')):
            with self.assertRaises(OSError):
                d.provision(directory, self.config, 1)
        self.assertTrue((directory / 'instance.json').exists())
        self.assertTrue((directory / 'started-windows.json').exists())
        self.assertFalse((directory / 'ready.json').exists())

    def test_command_timeout_retains_partial_output(self):
        with patch.object(d.subprocess, 'run', side_effect=subprocess.TimeoutExpired(['adb'], 1, output=b'partial\r\n')):
            self.refuse(d.run, self.value, ['adb'], 1)
        record = json.loads((self.directory / 'commands.jsonl').read_text())
        self.assertEqual(record['output'], 'partial\r\n')
        self.assertEqual(record['timedOutAfterSeconds'], 1)

    def test_existing_instance_cli_refusal_preserves_ready_bytes(self):
        (self.directory / 'ready.json').write_text('existing success')
        with patch.object(d, 'directory_path', return_value=self.directory), patch.object(d, 'lock', contextlib.nullcontext), \
                patch.object(d.sys, 'argv', ['controller', '--directory', str(self.directory), 'provision', '--config', 'unused']):
            self.refuse(d.main)
        self.assertEqual((self.directory / 'ready.json').read_text(), 'existing success')


if __name__ == '__main__':
    unittest.main()
