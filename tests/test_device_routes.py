"""Routing failures/ownership tests. Mocks are not Android transport acceptance."""
import base64
import contextlib
import copy
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

from e2e import devices as d, device_routes as r, environment as e
import test_e2e_devices as service_tests


class DeviceRoutesTest(unittest.TestCase):
    def setUp(self):
        service_tests.DeviceControllerTest.setUp(self)
        self.envdir = self.root / 'environment'
        self.envdir.mkdir()
        self.env = {'directory': str(self.envdir), 'owner': 'b' * 64,
                    'ports': {'backend': 19080, 'source': 19082},
                    'backendOrigin': 'http://127.0.0.1:19080', 'sourceOrigin': 'http://127.0.0.1:19082'}
        d.write_json(self.envdir / 'instance.json', self.env)
        self.record = {'owner': self.value['owner'], 'serial': 'emulator-5564',
                       'environment': r.identity(self.env), 'mappings': [
                           {'role': role, 'local': f'tcp:{port}', 'remote': f'tcp:{port}',
                            'state': 'created', 'transport': 'host-14'} for role, port in self.env['ports'].items()]}

    def save(self):
        d.write_json(r.file(self.value), self.record)

    def completed(self, output='', code=0):
        return subprocess.CompletedProcess([], code, output)

    def refuse(self, call, *args, **kwargs):
        with self.assertRaises(d.Refusal):
            call(*args, **kwargs)

    def inventory(self):
        return '\n'.join(m['transport'] + ' ' + m['local'] + ' ' + m['remote'] for m in self.record['mappings'])

    def test_exact_owned_set_only_and_complete_requires_both(self):
        self.save()
        with patch.object(d, 'adb', side_effect=[self.completed(), self.completed(self.inventory())]):
            self.assertEqual(len(d.check_mappings(self.value, 'emulator-5564')['reverse']), 2)
        with patch.object(d, 'adb', side_effect=[self.completed(), self.completed()]):
            self.refuse(r.mappings, self.value, 'emulator-5564', complete=True)

    def test_forward_foreign_serial_extra_reverse_changed_target_transport_refused(self):
        self.save()
        for forward, reverse in [('foreign-serial tcp:1 tcp:2', self.inventory()),
                                 ('', self.inventory() + '\nhost-14 tcp:18000 tcp:18000'),
                                 ('', self.inventory().replace('tcp:19080 tcp:19080', 'tcp:19080 tcp:8081')),
                                 ('', self.inventory().replace('host-14', 'host-15'))]:
            with self.subTest(forward=forward, reverse=reverse), patch.object(d, 'adb', side_effect=[
                    self.completed(forward), self.completed(reverse)]) as adb:
                self.refuse(r.mappings, self.value, 'emulator-5564')
                self.assertEqual(adb.call_count, 2)  # No removal or replacement.

    def test_changed_owner_serial_ports_and_service_collision_refused(self):
        original = copy.deepcopy(self.record)
        for key in ('owner', 'serial'):
            self.record = dict(original, **{key: 'foreign'})
            self.save()
            self.refuse(r.load, self.value)
        self.record = copy.deepcopy(original)
        self.record['environment']['ports']['source'] = self.config['systemPort']
        self.save()
        self.refuse(r.load, self.value)

    def test_only_exact_same_port_public_loopback_origins(self):
        for origin in ('http://0.0.0.0:19080', 'http://127.0.0.1:8081', 'http://localhost:19080',
                       'https://127.0.0.1:19080', 'http://user:secret@127.0.0.1:19080'):
            with self.subTest(origin=origin), patch.object(e, 'directory_path', return_value=self.envdir), \
                    patch.object(e, 'load', return_value=dict(self.env, backendOrigin=origin)):
                self.refuse(r.target, self.envdir)

    def test_cleaned_and_wrong_compose_target_refused(self):
        (self.envdir / 'cleaned.json').write_text('{}')
        with patch.object(e, 'directory_path', return_value=self.envdir), patch.object(e, 'load') as load:
            self.refuse(r.target, self.envdir)
            load.assert_not_called()
        self.refuse(r.target, '/tmp/opencode/atw-device-' + 'f' * 32)

    def test_changed_attached_environment_hash_owner_or_ports_refused(self):
        self.save()
        for key, changed in (('owner', 'c' * 64), ('ports', {'backend': 19090, 'source': 19092})):
            with patch.object(r, 'target', return_value=dict(self.env, **{key: changed})):
                self.refuse(r.attached, self.value)
        (self.envdir / 'instance.json').write_text('changed input bytes')
        with patch.object(r, 'target', return_value=self.env):
            self.refuse(r.attached, self.value)

    def test_no_rebind_creation_records_intent_before_mutation(self):
        made = []
        def adb(value, *args, **kwargs):
            record = r.load(value)
            if '--no-rebind' in args:
                self.assertFalse((self.directory / 'ready.json').exists())
                self.assertEqual(record['mappings'][-1]['state'], 'creating')
                made.append('host-14 ' + args[-2] + ' ' + args[-1])
                return self.completed()
            self.assertEqual(args[-2:], ('reverse', '--list'))
            return self.completed('\n'.join(made))
        with patch.object(d, 'wait', return_value={'scope': 'mock'}), patch.object(d, 'check_mappings'), \
                patch.object(r, 'target', return_value=self.env), patch.object(r, 'listeners', return_value=[]), \
                patch.object(e, 'locked', return_value=contextlib.nullcontext()), patch.object(e, 'wait'), \
                patch.object(d, 'adb', side_effect=adb):
            r.route(self.value, self.envdir, 1)
        self.assertEqual(len(r.load(self.value)['mappings']), 2)
        self.assertTrue(all(m['state'] == 'created' for m in r.load(self.value)['mappings']))

    def test_partial_creation_failure_retains_only_successful_owned_mapping(self):
        with patch.object(d, 'wait'), patch.object(d, 'check_mappings'), patch.object(r, 'target', return_value=self.env), \
                patch.object(r, 'listeners', return_value=[]), patch.object(e, 'locked', return_value=contextlib.nullcontext()), \
                patch.object(e, 'wait'), patch.object(d, 'adb', side_effect=[self.completed(),
                    self.completed('host-14 tcp:19080 tcp:19080'), self.completed('busy', 1)]):
            self.refuse(r.route, self.value, self.envdir, 1)
        partial = r.load(self.value)
        self.assertEqual([m['role'] for m in partial['mappings']], ['backend'])
        self.assertFalse((self.directory / 'ready.json').exists())
        self.assertFalse((self.directory / 'routes-ready.json').exists())

    def test_command_timeout_retains_ambiguous_intent_and_never_adopts_matching_mapping(self):
        with patch.object(d, 'wait'), patch.object(d, 'check_mappings'), patch.object(r, 'target', return_value=self.env), \
                patch.object(r, 'listeners', return_value=[]), patch.object(e, 'locked', return_value=contextlib.nullcontext()), \
                patch.object(e, 'wait'), patch.object(d, 'adb', side_effect=d.Refusal('bounded timeout')):
            self.refuse(r.route, self.value, self.envdir, 1)
        self.assertEqual(r.load(self.value)['mappings'][0]['state'], 'creating')
        with patch.object(d, 'adb', side_effect=[self.completed(), self.completed('host-14 tcp:19080 tcp:19080')]):
            self.refuse(r.mappings, self.value, 'emulator-5564')

    def test_busy_guest_port_and_unavailable_environment_create_no_routes(self):
        for busy in (True, False):
            with self.subTest(busy=busy), patch.object(d, 'wait'), patch.object(d, 'check_mappings'), \
                    patch.object(r, 'target', return_value=self.env), \
                    patch.object(r, 'listeners', return_value=[{'address': '127.0.0.1', 'port': 19080}] if busy else []), \
                    patch.object(e, 'locked', return_value=contextlib.nullcontext()), \
                    patch.object(e, 'wait', side_effect=d.Refusal('unavailable')), patch.object(d, 'adb') as adb:
                self.refuse(r.route, self.value, self.envdir, 1)
                adb.assert_not_called()
                self.assertFalse(r.file(self.value).exists())

    def test_service_port_collision_fails_before_persisting_attachment(self):
        self.env['ports']['source'] = self.config['systemPort']
        with patch.object(d, 'wait'), patch.object(d, 'check_mappings'), patch.object(r, 'target', return_value=self.env), \
                patch.object(r, 'listeners', return_value=[]), patch.object(e, 'locked', return_value=contextlib.nullcontext()), \
                patch.object(e, 'wait'), patch.object(d, 'adb') as adb:
            self.refuse(r.route, self.value, self.envdir, 1)
            adb.assert_not_called()
        self.assertFalse(r.file(self.value).exists())

    def test_existing_attachment_is_not_overwritten(self):
        self.save()
        before = r.file(self.value).read_bytes()
        with patch.object(d, 'wait') as wait:
            self.refuse(r.route, self.value, '/wrong-target', 1)
            wait.assert_not_called()
        self.assertEqual(r.file(self.value).read_bytes(), before)

    def test_wait_never_downgrades_attached_routes_and_invalidates_stale_success(self):
        self.save()
        for name in ('ready.json', 'routes-ready.json', 'combined-ready.json'):
            (self.directory / name).write_text('old success')
        snapshot = service_tests.DeviceControllerTest.snapshot(self)
        with patch.object(d, 'windows', return_value=snapshot), patch.object(d, 'check_device', return_value={'api': '29'}), \
                patch.object(d, 'appium_status', return_value={'value': {'ready': True}}), \
                patch.object(r, 'validate', side_effect=d.Refusal('broken route')):
            self.refuse(d.wait, self.value, 1)
        for name in ('ready.json', 'routes-ready.json', 'combined-ready.json'):
            self.assertFalse((self.directory / name).exists())

    def test_route_validation_checks_current_environment_and_refuses_reused_host_relay_pid(self):
        self.save()
        (self.directory / 'routes-ready.json').write_text('old success')
        with patch.object(r, 'attached', return_value=(self.record, self.env)), patch.object(r, 'mappings'), \
                patch.object(r, 'listeners', return_value=[{'address': '127.0.0.1', 'port': p} for p in self.env['ports'].values()]), \
                patch.object(e, 'locked', return_value=contextlib.nullcontext()), \
                patch.object(e, 'wait', side_effect=d.Refusal('Reused/foreign relay PID refused')), patch.object(r, 'probe') as probe:
            self.refuse(r.validate, self.value, 1)
            probe.assert_not_called()
        self.assertFalse((self.directory / 'routes-ready.json').exists())

    def test_non_loopback_guest_listener_refused(self):
        self.save()
        with patch.object(r, 'attached', return_value=(self.record, self.env)), patch.object(r, 'mappings'), \
                patch.object(r, 'listeners', return_value=[{'address': '0.0.0.0', 'port': 19080}]), patch.object(e, 'wait') as wait:
            self.refuse(r.validate, self.value, 1)
            wait.assert_not_called()

    def test_unroute_removes_exact_owned_present_routes_without_requiring_live_environment(self):
        self.save()
        with patch.object(d, 'windows', return_value=service_tests.DeviceControllerTest.snapshot(self)), \
                patch.object(d, 'adb', side_effect=[self.completed(), self.completed(self.inventory()),
                    self.completed(), self.completed(), self.completed(), self.completed()]) as adb, \
                patch.object(r, 'target', side_effect=AssertionError('must not need environment')):
            r.unroute(self.value)
        self.assertEqual([c.args[1:] for c in adb.call_args_list if '--remove' in c.args], [
            ('-s', 'emulator-5564', 'reverse', '--remove', 'tcp:19080'),
            ('-s', 'emulator-5564', 'reverse', '--remove', 'tcp:19082')])
        self.assertFalse(r.file(self.value).exists())

    def test_unroute_missing_routes_is_idempotent_and_never_removes_foreign(self):
        self.save()
        with patch.object(d, 'windows', return_value=service_tests.DeviceControllerTest.snapshot(self)), \
                patch.object(d, 'adb', side_effect=[self.completed(), self.completed('foreign tcp:1 tcp:2')]) as adb:
            self.refuse(r.unroute, self.value)
            self.assertEqual(adb.call_count, 2)
        with patch.object(d, 'windows', return_value=service_tests.DeviceControllerTest.snapshot(self)), \
                patch.object(d, 'adb', return_value=self.completed()) as adb:
            r.unroute(self.value)
            self.assertFalse(any('--remove' in c.args for c in adb.call_args_list))

    def test_http_bytes_status_framing_and_injection_refusal(self):
        raw = b'HTTP/1.1 200 Result\r\nContent-Type: image/png\r\nContent-Length: 3\r\n\r\n\x00\xffx'
        with patch.object(d, 'adb', return_value=self.completed(base64.b64encode(raw).decode())):
            status, body, _ = r.device_http(self.value, 'emulator-5564', 19082, '/prism/v1/art/a.png')
            self.assertEqual((status, body), (200, b'\x00\xffx'))
        for raw in (b'', b'HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nabc'):
            with patch.object(d, 'adb', return_value=self.completed(base64.b64encode(raw).decode())):
                self.refuse(r.device_http, self.value, 'emulator-5564', 19082, '/')
        with patch.object(d, 'adb') as adb:
            self.refuse(r.device_http, self.value, 'emulator-5564', 19082, '/; echo injection')
            adb.assert_not_called()

    def test_native_shutdown_waits_for_delayed_exact_wsl_proxy_exit(self):
        expected = {'pid': 1234, 'startTicks': '1', 'cmdline': '00'}
        (self.directory / 'launchers.jsonl').write_text(json.dumps(expected) + '\n')
        with patch.object(d, 'process_identity', side_effect=[expected, expected, None]) as identity, \
                patch.object(d.time, 'sleep') as sleep, patch.object(d.os, 'kill') as kill:
            d.drain_launchers(self.value)
            self.assertEqual(identity.call_count, 3)
            self.assertEqual(sleep.call_count, 2)
            kill.assert_not_called()

    def test_native_shutdown_never_signals_reused_proxy_and_timeout_is_not_cleaned(self):
        expected = {'pid': 1234, 'startTicks': '1', 'cmdline': '00'}
        (self.directory / 'launchers.jsonl').write_text(json.dumps(expected) + '\n')
        with patch.object(d, 'process_identity', return_value=dict(expected, startTicks='2')), patch.object(d.os, 'kill') as kill:
            d.drain_launchers(self.value)
            kill.assert_not_called()
        with patch.object(d, 'process_identity', return_value=expected), \
                patch.object(d.time, 'monotonic', side_effect=[0, 16]), patch.object(d.os, 'kill') as kill:
            self.refuse(d.drain_launchers, self.value)
            kill.assert_not_called()
        self.assertFalse((self.directory / 'cleaned.json').exists())


if __name__ == '__main__':
    unittest.main()
