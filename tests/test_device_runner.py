"""Focused session ownership/fresh-scenario tests, not actual device evidence."""
import json
import contextlib
import subprocess
import unittest
from unittest.mock import patch

from e2e import device_session as s, device_runner as runner, devices as d, device_routes as r
import test_e2e_devices as controller_tests


class DeviceRunnerTest(unittest.TestCase):
    def setUp(self):
        controller_tests.DeviceControllerTest.setUp(self)
        self.run = self.directory / ('runner-' + 'b' * 32)
        self.run.mkdir()
        self.session = {'owner': self.value['owner'], 'serial': 'emulator-5564',
                        'sessionId': '12345678-abcd-abcd-abcd-123456789abc', 'systemPort': 18210,
                        'forward': ['emulator-5564', 'tcp:18210', 'tcp:6790']}
        d.write_json(self.directory / 'runner-current.json', {'owner': self.value['owner'], 'run': self.run.name})
        d.write_json(self.run / 'session.json', self.session)
        self.live = {'udid': 'emulator-5564', 'systemPort': 18210, 'adbPort': 15038, 'remoteAdbHost': '127.0.0.1'}

    def completed(self, output='', code=0):
        return subprocess.CompletedProcess([], code, output)

    def test_live_exact_session_allows_only_its_forward(self):
        with patch.object(d, 'adb', return_value=self.completed(' '.join(self.session['forward']))), \
                patch.object(s, 'request', return_value={'capabilities': self.live}):
            self.assertEqual(s.forwards(self.value, 'emulator-5564'), [self.session['forward']])

    def test_wrong_serial_port_target_extra_mapping_and_closed_session_refused(self):
        original = ' '.join(self.session['forward'])
        for output in (original.replace('5564', '5566'), original.replace('18210', '18211'),
                       original.replace('6790', '6791'), original + '\nforeign tcp:9 tcp:8'):
            with patch.object(d, 'adb', return_value=self.completed(output)), patch.object(s, 'request') as request:
                with self.assertRaises(d.Refusal):
                    s.forwards(self.value, 'emulator-5564')
                request.assert_not_called()
        (self.run / 'session-closed.json').write_text('{}')
        with patch.object(d, 'adb', return_value=self.completed(original)), patch.object(s, 'request'):
            with self.assertRaises(d.Refusal):
                s.forwards(self.value, 'emulator-5564')

    def test_changed_live_adb_owner_refused(self):
        for key, value in (('udid', 'emulator-5566'), ('adbPort', 5037), ('remoteAdbHost', 'foreign'), ('systemPort', 18211)):
            with patch.object(d, 'adb', return_value=self.completed(' '.join(self.session['forward']))), \
                    patch.object(s, 'request', return_value={'capabilities': dict(self.live, **{key: value})}):
                with self.assertRaises(d.Refusal):
                    s.forwards(self.value, 'emulator-5564')

    def test_changed_record_owner_and_traversal_refused(self):
        for current in ({'owner': 'foreign', 'run': self.run.name},
                        {'owner': self.value['owner'], 'run': '../foreign'}):
            d.write_json(self.directory / 'runner-current.json', current)
            with self.assertRaises(d.Refusal):
                s.record(self.value)

    def test_recovery_deletes_only_recorded_session_and_checks_forward_removed(self):
        responses = [{'capabilities': self.live}, None, None]
        with patch.object(s, 'request', side_effect=responses) as request, \
                patch.object(s, 'forwards', return_value=[self.session['forward']]), \
                patch.object(d, 'adb', return_value=self.completed()):
            s.close(self.value)
        self.assertEqual(request.call_args_list[1].args[1:], ('DELETE', '/session/' + self.session['sessionId']))
        self.assertTrue((self.run / 'session-closed.json').exists())

    def test_changed_live_transport_is_not_deleted(self):
        for change in ({'udid': 'foreign'}, {'systemPort': 5037}):
            with patch.object(s, 'request', return_value={'capabilities': dict(self.live, **change)}) as request:
                with self.assertRaises(d.Refusal):
                    s.close(self.value)
                self.assertEqual(request.call_count, 1)

    def test_no_live_session_still_refuses_a_surviving_unknown_forward(self):
        with patch.object(s, 'request', return_value=None), patch.object(d, 'adb', return_value=self.completed('unknown')):
            with self.assertRaises(d.Refusal):
                s.close(self.value)

    def test_fresh_refuses_active_session_before_any_clear_or_reset(self):
        with patch.object(s, 'request', return_value={'capabilities': self.live}), patch.object(d, 'adb') as adb, \
                patch.object(runner.e, 'reset') as reset:
            with self.assertRaises(d.Refusal):
                runner.fresh(self.value, 1)
            adb.assert_not_called()
            reset.assert_not_called()

    def test_failed_compose_reset_prevents_android_clear(self):
        d.write_json(self.directory / 'ready.json', {'stale': True})
        with patch.object(s, 'idle'), patch.object(r, 'attached', return_value=({}, {'directory': str(self.directory)})), \
                patch.object(runner.e, 'locked', return_value=contextlib.nullcontext()), \
                patch.object(runner.e, 'reset', side_effect=d.Refusal('reset failed')), patch.object(d, 'adb') as adb:
            with self.assertRaises(d.Refusal):
                runner.fresh(self.value, 1)
            adb.assert_not_called()
            self.assertFalse((self.directory / 'ready.json').exists())

    def test_fresh_requires_successful_clear_and_absent_old_db_before_launch(self):
        ready = {'routes': {}, 'app': {}, 'device': {'serial': 'emulator-5564'}}
        def launch(*args):
            d.write_json(self.directory / 'ready.json', ready)
        for clear, absent, valid in [('Failure', 'No such file', False), ('Success', 'still exists', False),
                                     ('Success', 'No such file', True)]:
            with patch.object(s, 'idle'), patch.object(r, 'attached', return_value=({}, {'directory': str(self.directory)})), \
                    patch.object(runner.e, 'locked', return_value=contextlib.nullcontext()), \
                    patch.object(runner.e, 'reset'), patch.object(d, 'check_windows'), patch.object(d, 'windows'), \
                    patch.object(d, 'check_mappings'), patch.object(d, 'adb', side_effect=[self.completed(),
                        self.completed(clear), self.completed(absent, 1)]), \
                    patch.object(runner.a, 'operation', side_effect=launch) as operation:
                if valid:
                    self.assertEqual(runner.fresh(self.value, 1), ready)
                else:
                    with self.assertRaises(d.Refusal):
                        runner.fresh(self.value, 1)
                    operation.assert_not_called()

    def test_run_publishes_success_only_after_cleanup_and_invalidates_stale_handoff(self):
        for failing in ('gradle', 'close', None):
            def gradle(*args, **kwargs):
                result = self.root / 'e2e/build/test-results/deviceTest/TEST-startup.xml'
                result.parent.mkdir(parents=True, exist_ok=True)
                result.write_text('<testsuite><testcase classname="NativeStartupTest" name="startup"/></testsuite>')
                return self.completed(code=1 if failing == 'gradle' else 0)
            ready = {'device': {'serial': 'emulator-5564'}, 'appium': 'http://127.0.0.1:14733',
                     'routes': {'backend': 'http://127.0.0.1:18080'}}
            d.write_json(self.directory / 'ready.json', {'stale': True})
            with patch.object(d, 'ROOT', self.root), patch.object(runner, 'fresh', return_value=ready), \
                    patch.object(s, 'close', side_effect=[None, d.Refusal('close failed')] if failing == 'close' else None), \
                    patch.object(d, 'check_mappings'), patch.object(runner.subprocess, 'run', side_effect=gradle):
                if failing:
                    with self.assertRaises(d.Refusal):
                        runner.run(self.value, 'startup', 1)
                else:
                    self.assertEqual(runner.run(self.value, 'startup', 1)['tests'], 1)
            current = json.loads((self.directory / 'runner-current.json').read_text())
            self.assertEqual((self.directory / current['run'] / 'passed.json').exists(), failing is None)
            self.assertFalse((self.directory / 'ready.json').exists())

    def test_auth_and_accounts_pin_endpoint_and_exact_test_scopes(self):
        ready = {'device': {'serial': 'emulator-5564'}, 'appium': 'http://127.0.0.1:14733',
                 'routes': {'backend': 'http://127.0.0.1:18080'}}
        apk = self.root / 'androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk'
        apk.parent.mkdir(parents=True, exist_ok=True)
        apk.write_bytes(b'owned instrumentation fixture')
        for mode in ('accounts', 'auth'):
            def gradle(command, **kwargs):
                self.assertIn('-PatwApiOrigin=http://127.0.0.1:18080', command)
                if mode == 'accounts':
                    self.assertEqual(command[-2:], ['--tests', '*NativeAccountsTest'])
                    result = self.root / 'e2e/build/test-results/deviceTest/TEST-accounts.xml'
                    classes = ['NativeAccountsTest']
                else:
                    self.assertEqual(command[-1], '-Pandroid.testInstrumentationRunnerArguments.class=' +
                                     ','.join('org.asyncttwiz.android.' + name for name in runner.AUTH_CLASSES))
                    result = self.root / 'androidApp/build/outputs/androidTest-results/connected/TEST-auth.xml'
                    classes = ['StartupTest'] * 2 + ['ProtectedSessionStoreTest'] * 2 + ['AccountEntryTest']
                result.parent.mkdir(parents=True, exist_ok=True)
                result.write_text('<testsuite>' + ''.join(f'<testcase classname="{name}" name="{i}"/>'
                                  for i, name in enumerate(classes)) + '</testsuite>')
                return self.completed()
            with patch.object(d, 'ROOT', self.root), patch.object(runner, 'fresh', return_value=ready), \
                    patch.object(s, 'close'), patch.object(d, 'check_mappings'), \
                    patch.object(d, 'adb', return_value=self.completed('Success')), \
                    patch.object(runner.subprocess, 'run', side_effect=gradle):
                self.assertEqual(runner.run(self.value, mode, 1)['tests'], 1 if mode == 'accounts' else 5)
