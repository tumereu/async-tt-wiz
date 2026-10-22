"""Combined controller regressions; mocks are not actual Android acceptance."""
import copy
import json
from pathlib import Path
import unittest
from unittest.mock import patch

from e2e import devices as d, device_app as a, device_routes as r, device_handoff as h, environment as e
import test_device_routes as route_tests


class CombinedHandoffTest(unittest.TestCase):
    def setUp(self):
        route_tests.DeviceRoutesTest.setUp(self)
        # Keep real directory/record validation in these tests, not a forged path.
        self.env['directory'] = '/tmp/opencode/atw-e2e-' + 'e' * 32
        self.route = copy.deepcopy(self.record)
        self.route['environment'] = r.identity(dict(self.env, directory=str(self.envdir)))
        self.route['environment']['directory'] = self.env['directory']
        self.apk = {'sha256': 'f' * 64, 'package': a.PACKAGE, 'activity': a.ACTIVITY}
        self.intent = {'owner': self.value['owner'], 'serial': 'emulator-5564',
                       'environmentDirectory': self.env['directory'], 'environment': self.route['environment'], 'apk': self.apk}
        self.infrastructure = {'device': {'serial': 'emulator-5564'}, 'routes': {'environment': self.route['environment']},
                               'scope': 'transport only'}
        self.installed = {'serial': 'emulator-5564', 'apk': self.apk}
        self.app = {'screenshot': {'sha256': '0' * 64}, 'apk': self.apk}

    def save(self):
        d.write_json(h.file(self.value), self.intent)
        d.write_json(r.file(self.value), self.route)

    def stale(self):
        for name in ('ready.json', 'app-ready.json', 'routes-ready.json', 'combined-ready.json'):
            (self.directory / name).write_text('stale success')

    def no_success(self):
        for name in ('ready.json', 'app-ready.json', 'routes-ready.json', 'combined-ready.json'):
            self.assertFalse((self.directory / name).exists(), name)

    def test_failed_prepare_keeps_intent_before_target_or_apk_check_and_never_downgrades(self):
        for failure in ('target', 'apk'):
            h.file(self.value).unlink(missing_ok=True)
            self.stale()
            def target(path):
                self.assertTrue(h.file(self.value).exists())
                self.no_success()
                if failure == 'target':
                    raise d.Refusal('Unavailable attached environment')
                return self.env
            with patch.object(r, 'target', side_effect=target), patch.object(a, 'verify_apk', side_effect=d.Refusal('invalid APK')), \
                    patch.object(r, 'attach') as attach:
                with self.assertRaises(d.Refusal):
                    h.prepare(self.value, self.env['directory'], '/invalid.apk', 1)
                attach.assert_not_called()
            with patch.object(d, 'infrastructure', return_value={'device': {'serial': 'emulator-5564'}}):
                with self.assertRaises(d.Refusal):
                    d.wait(self.value, 1)
            self.no_success()
            self.assertTrue(h.file(self.value).exists())

    def test_prepare_orders_intent_attach_install_launch_and_does_not_reset(self):
        calls = []
        def attach(*args):
            saved = h.load(self.value)
            self.assertEqual(saved['apk'], self.apk)
            self.no_success()
            calls.append('attach')
        def operation(value, name, timeout, *args):
            calls.append(name)
            if name == 'launch-app':
                d.write_json(self.directory / 'ready.json', dict(self.infrastructure, app=self.app))
        with patch.object(r, 'target', return_value=self.env), patch.object(r, 'identity', return_value=self.intent['environment']), \
                patch.object(a, 'verify_apk', return_value=self.apk), patch.object(r, 'attach', side_effect=attach), \
                patch.object(a, 'operation', side_effect=operation), patch.object(e, 'reset') as reset:
            result = h.prepare(self.value, self.env['directory'], '/verified.apk', 1)
        self.assertEqual(calls, ['attach', 'install-app', 'launch-app'])
        self.assertEqual(result['app'], self.app)
        reset.assert_not_called()

    def test_changed_target_apk_or_environment_is_not_adopted_or_overwritten(self):
        self.save()
        before = h.file(self.value).read_bytes()
        for env, apk in ((dict(self.env, owner='changed'), self.apk), (self.env, dict(self.apk, sha256='changed'))):
            with patch.object(r, 'target', return_value=env), \
                    patch.object(r, 'identity', return_value=dict(self.intent['environment'], owner=env['owner'])), \
                    patch.object(a, 'verify_apk', return_value=apk), patch.object(r, 'attach') as attach, \
                    patch.object(a, 'operation') as operation:
                with self.assertRaises(d.Refusal):
                    h.prepare(self.value, self.env['directory'], '/verified.apk', 1)
                attach.assert_not_called()
                operation.assert_not_called()
                self.assertEqual(h.file(self.value).read_bytes(), before)
        with self.assertRaises(d.Refusal):
            h.prepare(self.value, '/tmp/opencode/atw-e2e-' + 'c' * 32, '/verified.apk', 1)
        self.assertEqual(h.file(self.value).read_bytes(), before)

    def test_wait_rechecks_actual_installed_bytes_and_current_ui_every_time(self):
        self.save()
        self.stale()
        with patch.object(d, 'infrastructure', return_value=self.infrastructure), \
                patch.object(a, 'app_record', return_value=self.installed), patch.object(a, 'installed') as installed, \
                patch.object(a, 'observe', return_value=self.app) as observe, patch.object(r, 'mappings'), \
                patch.object(r, 'target', return_value=self.env), patch.object(r, 'identity', return_value=self.intent['environment']), \
                patch.object(d, 'check_windows'), patch.object(d, 'windows'):
            first = d.wait(self.value, 1)
            second = d.wait(self.value, 1)
        self.assertEqual(installed.call_count, 2)
        self.assertEqual(observe.call_count, 2)
        self.assertNotEqual(installed.call_args_list[0].args[-1], installed.call_args_list[1].args[-1])
        self.assertEqual(second, json.loads((self.directory / 'combined-ready.json').read_text()))
        self.assertEqual(second, json.loads((self.directory / 'ready.json').read_text()))
        self.assertEqual(first['app'], self.app)

    def test_failed_startup_missing_install_or_changed_installed_apk_removes_all_success(self):
        self.save()
        for record, failure in ((self.installed, d.Refusal('stale startup')), (None, FileNotFoundError()),
                                (dict(self.installed, apk={'sha256': 'other'}), None)):
            self.stale()
            with patch.object(d, 'infrastructure', return_value=self.infrastructure), \
                    patch.object(a, 'app_record', side_effect=failure if record is None else None, return_value=record), \
                    patch.object(a, 'installed'), patch.object(a, 'observe', side_effect=failure) as observe:
                with self.assertRaises((d.Refusal, FileNotFoundError)):
                    d.wait(self.value, 1)
            self.no_success()
            self.assertTrue(h.file(self.value).exists())

    def test_unavailable_environment_lost_routes_relay_or_failed_services_never_fall_back(self):
        self.save()
        for reason in ('changed attached identity', 'missing owned route', 'reused relay PID', 'Appium unavailable'):
            self.stale()
            with patch.object(d, 'infrastructure', side_effect=d.Refusal(reason)), patch.object(a, 'observe') as observe:
                with self.assertRaises(d.Refusal):
                    d.wait(self.value, 1)
                observe.assert_not_called()
            self.no_success()

    def test_successful_transport_to_different_environment_is_not_combined_success(self):
        self.save()
        changed = copy.deepcopy(self.infrastructure)
        changed['routes']['environment']['owner'] = 'changed'
        with patch.object(d, 'infrastructure', return_value=changed), patch.object(a, 'app_record') as app:
            with self.assertRaises(d.Refusal):
                d.wait(self.value, 1)
            app.assert_not_called()
        self.no_success()

    def test_post_capture_lost_mapping_or_changed_environment_invalidates_new_app_success(self):
        self.save()
        for changed in (False, True):
            with patch.object(d, 'infrastructure', return_value=self.infrastructure), \
                    patch.object(a, 'app_record', return_value=self.installed), patch.object(a, 'installed'), \
                    patch.object(a, 'observe', return_value=self.app), \
                    patch.object(r, 'mappings', side_effect=None if changed else d.Refusal('lost route')), \
                    patch.object(r, 'target', return_value=self.env), patch.object(r, 'identity', return_value={'changed': True}):
                with self.assertRaises(d.Refusal):
                    d.wait(self.value, 1)
            self.no_success()

    def test_unroute_detaches_failed_preparation_without_routes_only_after_foreign_check(self):
        self.save()
        r.file(self.value).unlink()
        self.stale()
        with patch.object(d, 'windows'), patch.object(d, 'check_windows'), \
                patch.object(d, 'check_mappings', side_effect=d.Refusal('foreign route')):
            with self.assertRaises(d.Refusal):
                r.unroute(self.value)
        self.assertTrue(h.file(self.value).exists())
        self.no_success()
        with patch.object(d, 'windows'), patch.object(d, 'check_windows'), patch.object(d, 'check_mappings'):
            r.unroute(self.value)
        self.assertFalse(h.file(self.value).exists())
        self.assertTrue((self.directory / 'combined-detached.json').exists())
        with patch.object(d, 'infrastructure', return_value={'scope': 'services only'}):
            self.assertEqual(d.wait(self.value, 1), {'scope': 'services only'})

    def test_unroute_existing_routes_clears_combined_intent_and_success(self):
        self.save()
        self.stale()
        with patch.object(d, 'windows'), patch.object(d, 'check_windows'), \
                patch.object(r, 'mappings', return_value={'reverse': []}):
            r.unroute(self.value)
        self.assertFalse(h.file(self.value).exists())
        self.assertFalse(r.file(self.value).exists())
        self.no_success()

    def test_foreign_or_symlink_combined_record_never_accepted(self):
        self.save()
        self.intent['owner'] = 'foreign'
        d.write_json(h.file(self.value), self.intent)
        with self.assertRaises(d.Refusal):
            h.load(self.value)
        h.file(self.value).unlink()
        h.file(self.value).symlink_to(r.file(self.value))
        with self.assertRaises(d.Refusal):
            h.load(self.value)

    def test_app_operations_cannot_leave_partial_ready_when_combined_recheck_fails(self):
        self.save()
        for operation in ('launch-app', 'capture-app'):
            self.stale()
            with patch.object(d, 'infrastructure', return_value=self.infrastructure), \
                    patch.object(a, 'app_record', return_value=self.installed), patch.object(a, 'installed'), \
                    patch.object(a, 'observe', return_value=self.app), patch.object(d, 'adb', return_value=type('Reply', (), {'stdout': 'Status: ok'})()), \
                    patch.object(d, 'wait', side_effect=d.Refusal('environment lost after startup')):
                with self.assertRaises(d.Refusal):
                    a.operation(self.value, operation, 1)
            self.no_success()

    def test_app_capture_with_combined_intent_requires_full_wait_not_only_old_ui(self):
        self.save()
        with patch.object(d, 'infrastructure', return_value=self.infrastructure), \
                patch.object(a, 'app_record', return_value=self.installed), patch.object(a, 'installed'), \
                patch.object(a, 'observe', return_value={'old': 'first observation'}), \
                patch.object(d, 'wait', return_value={'app': self.app}) as wait, patch.object(d, 'adb') as adb:
            self.assertEqual(a.operation(self.value, 'capture-app', 1), self.app)
            wait.assert_called_once_with(self.value, 1)
            adb.assert_not_called()


if __name__ == '__main__':
    unittest.main()
