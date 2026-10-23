#!/usr/bin/env python3
"""Offline dashboard regressions: no live journals, network or agent calls."""

from copy import deepcopy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SPEC = importlib.util.spec_from_file_location('progress', Path(__file__).with_name('implementation-progress.py'))
P = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(P)
NOW = 1_792_740_000


def state():
    return {'tasks': [{'id': 'I12', 'batch': 'B3', 'status': 'pending'},
                      {'id': 'I13', 'batch': 'B4', 'status': 'pending'}],
            'batches': [{'id': 'B3', 'status': 'pending', 'review': {'completed': {}}},
                        {'id': 'B4', 'status': 'pending', 'review': {'completed': {}}}],
            'findings': [], 'runs': [], 'externalReleaseGates': []}


class Transitions(unittest.TestCase):
    def test_split_checkpoint_and_ancestor_closure_not_extra_work(self):
        before = state()
        after = deepcopy(before)
        after['tasks'][0].update(children=['I12a', 'I12b'], status='in-progress')
        after['tasks'] += [{'id': 'I12a', 'batch': 'B3', 'status': 'implemented'},
                           {'id': 'I12b', 'batch': 'B3', 'status': 'pending'}]
        events = P.state_events(before, after, NOW)
        self.assertEqual([(e['kind'], e['target']) for e in events], [('items', 'I12a')])
        final = deepcopy(after)
        final['tasks'][0]['status'] = 'implemented'
        final['tasks'][-1]['status'] = 'implemented'
        self.assertEqual([e['target'] for e in P.state_events(after, final, NOW)], ['I12b'])
        checkpoint = deepcopy(after)
        checkpoint['tasks'][0]['troubleshooting'] = {'progress': 'passing affected test'}
        self.assertEqual(P.state_events(after, checkpoint, NOW), [])

    def test_independent_review_and_verified_fix_counts(self):
        before = state()
        after = deepcopy(before)
        after['batches'][0]['review']['completed'] = {'journey': 'abc', 'recovery': 'abc'}
        after['findings'] = [{'id': 'defect', 'title': 'Lost data', 'status': 'fixed-awaiting-check'}]
        events = P.state_events(before, after, NOW)
        self.assertEqual([e['kind'] for e in events], ['reviews', 'reviews'])
        final = deepcopy(after)
        final['findings'][0]['status'] = 'verified'
        final['runs'] = ['docs/agent/implementation-runs/verifier.json']
        event, = P.state_events(after, final, NOW)
        self.assertEqual(event['kind'], 'fixes')
        self.assertEqual(event['record'], final['runs'][0])

    def test_reopen_is_visible(self):
        before = state()
        before['tasks'][0]['status'] = 'implemented'
        before['batches'][0]['review']['completed'] = {'journey': 'abc'}
        events = P.state_events(before, state(), NOW)
        self.assertEqual([e['kind'] for e in events], ['reopened', 'reopened'])

    def test_reanchoring_review_is_not_another_completion(self):
        before = state()
        before['batches'][0]['review']['completed'] = {'journey': 'abc'}
        after = deepcopy(before)
        after['batches'][0]['review']['completed']['journey'] = 'def'
        self.assertEqual(P.state_events(before, after, NOW), [])


class Rendering(unittest.TestCase):
    def render(self, events=(), records=None, width=58, **kwargs):
        return '\n'.join(P.render(state(), events, records or {}, {}, NOW, width, **kwargs))

    def test_all_four_windows_and_boundaries(self):
        events = [dict(kind='items', target='I12', when=NOW - hours * 3600)
                  for hours in (1, 2, 4, 8, 9, -1)]
        text = self.render(events)
        self.assertIn('Work items: 1 / 2 / 3 / 4', text)
        self.assertIn('Review scopes: 0 / 0 / 0 / 0', text)
        self.assertIn('Verified fixes: 0 / 0 / 0 / 0', text)

    def test_all_parallel_roles_waiting_and_unknown_runtime(self):
        records = {}
        for i, role in enumerate(('implement', 'review', 'fix', 'verify', 'integration')):
            records[str(i)] = dict(selection={'mode': role, 'target': 'I13'},
                                   _status='running', _times=(NOW - 65, None))
        records['saved'] = dict(target='I12', role='implement', _status='saved',
                                _times=(NOW - 100, NOW - 20))
        text = self.render(records=records, details=True)
        self.assertIn('5 lanes', text)
        for role in ('Build', 'Review', 'Fix', 'Verify', 'Integrate'):
            self.assertIn(role + ':', text)
        self.assertIn('1m20s await:', text)
        self.assertIn('1m05s Build:', text)
        self.assertNotIn('I13', text)
        self.assertIn('2 scoped work items', text.replace('0/2', '2'))

    def test_awaiting_integration_is_one_compact_line(self):
        records = {'saved': dict(target='I13', role='implement', _status='saved',
                                  _times=(NOW - 100, NOW - 20))}
        text = self.render(records=records)
        current = text.split('-- CURRENT', 1)[1].split('-- RECENT', 1)[0].splitlines()[1:]
        self.assertEqual(current, ['1m20s await: Ordered gameplay engine and server'])
        self.assertNotIn('Ran ', text)

    def test_short_titles_and_no_ids_or_control_sequences(self):
        events = [dict(kind='items', target='I12', when=NOW - 100)]
        text = self.render(events)
        self.assertIn('Lobby completion', text)
        self.assertIn('ran unknown', text)
        self.assertNotIn('I12', text)
        self.assertNotIn('\x1b', text)
        self.assertEqual(P.clean('a\x1b[31m\nBOOM'), 'a [31m BOOM')

    def test_narrow_output_never_hides_lanes(self):
        records = {str(i): dict(target='I13', role='implement', _status='running',
                                _times=(NOW, None)) for i in range(14)}
        text = self.render(records=records, width=20)
        self.assertIn('14 lanes', ' '.join(text.split()))
        self.assertEqual(text.count('Build:'), 14)
        self.assertTrue(all(len(line) < 20 and line.isascii() for line in text.splitlines()))

    def test_runtime_uses_explicit_times_not_managed_run_name(self):
        record = {'runId': 'managed-20261023-i13-01',
                  'reservedAt': '2026-10-23T01:00:00+00:00',
                  'launchedAt': '2026-10-23T01:01:00Z'}
        snapshot = {'time': {'created': 1792717260000, 'idle': 1792717320000}}
        start, end = P.run_times(record, snapshot)
        self.assertEqual(end - start, 60)
        self.assertEqual(P.elapsed(None), 'unknown')


class LocalRepository(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='progress-test-', dir='/tmp/opencode')
        self.repo = Path(self.temp.name)
        self.git('init', '-b', 'master')
        self.git('config', 'user.name', 'Dashboard test')
        self.git('config', 'user.email', 'dashboard@example.invalid')
        self.git('config', 'commit.gpgsign', 'false')
        self.git('config', 'remote.origin.url', str(self.repo / 'unused-remote'))
        self.git('config', 'remote.origin.fetch', '+refs/heads/*:refs/remotes/origin/*')
        self.git('config', 'branch.master.remote', 'origin')
        self.git('config', 'branch.master.merge', 'refs/heads/master')
        (self.repo / P.STATE).parent.mkdir(parents=True)

    def tearDown(self):
        self.temp.cleanup()

    def git(self, *args, when=None):
        env = dict(os.environ)
        if when is not None:
            env.update(GIT_AUTHOR_DATE=f'@{when} +0000', GIT_COMMITTER_DATE=f'@{when} +0000')
        return subprocess.run(['git', '-C', str(self.repo), *args], check=True,
                              capture_output=True, text=True, env=env).stdout.strip()

    def commit(self, value, when, pushed=True):
        (self.repo / P.STATE).write_text(json.dumps(value, ensure_ascii=False) + '\n')
        self.git('add', P.STATE)
        self.git('commit', '-m', 'State', when=when)
        if pushed:
            self.git('update-ref', 'refs/remotes/origin/master', 'HEAD')

    def test_window_baseline_and_pushed_state_exclude_dirty_and_unpushed(self):
        before = state()
        before['tasks'][0]['outcome'] = 'Unicode \u00e4 and \u2192 retained in state'
        self.commit(before, NOW - 9 * 3600)
        after = deepcopy(before)
        after['tasks'][0]['status'] = 'implemented'
        self.commit(after, NOW - 3600)
        unpushed = deepcopy(after)
        unpushed['tasks'][1]['status'] = 'implemented'
        self.commit(unpushed, NOW - 100, pushed=False)
        (self.repo / P.STATE).write_text('currently being rewritten {')
        actual, events, _, _ = P.pushed_history(self.repo, NOW)
        self.assertEqual(actual, after)
        self.assertEqual([e['target'] for e in events], ['I12'])
        text = '\n'.join(P.dashboard(self.repo, now=NOW))
        self.assertIn('1/2 milestones', text)
        self.assertIn('unaccepted edits excluded', text)
        self.assertIn('journal unavailable', text)

    def test_worker_branch_completions_do_not_count_before_acceptance(self):
        self.commit(state(), NOW - 9 * 3600)
        self.git('switch', '-c', 'worker')
        worker = state()
        worker['tasks'][0]['status'] = 'implemented'
        self.commit(worker, NOW - 3600, pushed=False)
        self.git('switch', 'master')
        # A merge can contain speculative report/state history. Only resulting
        # integration state transitions, never side-branch commits, count.
        self.git('merge', '--no-ff', '-s', 'ours', 'worker', '-m', 'Reports only', when=NOW - 100)
        self.git('update-ref', 'refs/remotes/origin/master', 'HEAD')
        actual, events, _, _ = P.pushed_history(self.repo, NOW)
        self.assertEqual(actual, state())
        self.assertEqual(events, [])

    def test_parallel_journal_dedup_and_final_snapshot(self):
        journal = self.repo / '.git/implementation-loop'
        (journal / 'dispatch').mkdir(parents=True)
        lease = dict(runId='lane', target='I13', role='implement', status='running',
                     reservedAt='2026-10-23T01:00:00Z')
        (journal / 'dispatch/lane.json').write_text(json.dumps(lease))
        (journal / 'orchestrator.json').write_text(json.dumps({'status': 'running', 'leases': {'lane': lease}}))
        (journal / 'active.json').write_text(json.dumps(lease))
        (journal / 'lane').mkdir()
        (journal / 'lane/session.json').write_text(json.dumps({
            'outcome': 'succeeded', 'time': {'created': 1792717200000, 'idle': 1792717260000}}))
        warnings = set()
        _, records = P.journal_records(journal, warnings)
        self.assertEqual(list(records), ['lane'])
        self.assertEqual(records['lane']['_status'], 'saved')
        self.assertEqual(P.runtime({'record': 'docs/agent/implementation-runs/lane.json'}, records), '1m00s')
        self.assertFalse(warnings)

    def test_partial_metadata_warns_without_losing_other_lanes(self):
        journal = self.repo / '.git/implementation-loop'
        (journal / 'dispatch').mkdir(parents=True)
        (journal / 'orchestrator.json').write_text('{')
        (journal / 'dispatch/lane.json').write_text(json.dumps(dict(runId='lane', status='running')))
        warnings = set()
        _, records = P.journal_records(journal, warnings)
        self.assertIn('lane', records)
        self.assertTrue(warnings)

    def test_acceptance_runtime_resolves_original_worker(self):
        self.commit(state(), NOW - 100)
        report = 'docs/agent/implementation-runs/accepted.json'
        path = self.repo / report
        path.parent.mkdir(parents=True)
        path.write_text(json.dumps({'workerProposal': {'report': 'docs/agent/implementation-runs/worker.json'}}))
        self.git('add', report)
        self.git('commit', '-m', 'Acceptance', when=NOW)
        records = {'worker': {'_times': (NOW - 3600, NOW - 60)}}
        events = [{'record': report}]
        warnings = set()
        P.event_runtimes(self.repo, 'HEAD', events, records, warnings)
        self.assertEqual(events[0]['runtime'], '59m00s')
        self.assertFalse(warnings)


if __name__ == '__main__':
    unittest.main()
