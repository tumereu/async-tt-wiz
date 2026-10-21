#!/usr/bin/env python3
"""Diagnostic readiness probes; exit zero is not a qualification pass.

Uses the existing controlled fake agents and disposable local Git remotes only.
Prints observed behavior, including currently accepted invalid handoffs. Does not
launch OpenCode sessions, change the real state, or implement application code.
"""
import contextlib
import copy
import importlib.util
import io
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('qualification', ROOT / 'scripts/test-implementation-loop.py')
q = importlib.util.module_from_spec(spec)
spec.loader.exec_module(q)
loop = q.loop
results = {}


def run_fixture(name, setup=None, transform=None, count=10):
    fixture = q.GitFixture()
    try:
        if setup:
            setup(fixture)
        client = q.FakeOpenCode(fixture, issue=False)
        runner = loop.Runner(fixture.root, client=client)
        client.runner = runner
        if transform:
            client.transform = lambda context, state, report: transform(fixture, context, state, report)
        with contextlib.redirect_stdout(io.StringIO()):
            result = runner.run(count, 30)
        state = fixture.state()
        results[name] = {
            'result': result['status'], 'calls': client.calls,
            'tasks': state['tasks'], 'batches': state['batches'],
            'gates': state.get('externalReleaseGates'),
            'next': loop.selection(state), 'runs': len(state['runs']),
            'clean': not fixture.git('status', '--short'),
            'pushed': fixture.git('rev-parse', 'HEAD') == fixture.git(
                'ls-remote', 'origin', 'refs/heads/main').split()[0],
        }
        if (fixture.root / 'arithmetic.py').exists():
            results[name]['verifiedCode'] = (fixture.root / 'arithmetic.py').read_text()
            anchor = state['batches'][0]['reviewedCommit']
            results[name]['anchoredCode'] = fixture.git('show', anchor + ':arithmetic.py')
    finally:
        fixture.close()


def last_task_finding(fixture, context, state, report):
    if context['selection']['mode'] == 'implement' and context['selection']['target'] == 'F2':
        state['findings'].append({
            'id': 'R1', 'batch': 'B1', 'status': 'open', 'disposition': 'actionable'})
        state['batches'][0]['status'] = 'changes-requested'
        report['newFindingIds'] = ['R1']


run_fixture('first-group-review-skipped', transform=last_task_finding)


def gated(fixture):
    state = fixture.state()
    state['externalReleaseGates'] = [
        {'id': 'reference-device-performance', 'status': 'unverified'}]
    fixture.write(loop.STATE, state)
    fixture.commit()


def drop_gate(fixture, context, state, report):
    if context['selection']['mode'] == 'implement':
        state['externalReleaseGates'] = []


run_fixture('external-gate-deleted', setup=gated, transform=drop_gate)


def metadata(fixture, context, state, report):
    state['tasks'][0].update(
        status='in-progress', note='Planning again ' + str(len(state['runs'])))
    state['batches'][0]['status'] = 'pending'


run_fixture('metadata-only-progress', transform=metadata, count=5)


def pending_verification(fixture):
    state = fixture.state()
    for task in state['tasks']:
        task['status'] = 'implemented'
    state['batches'][0]['status'] = 'fixes-awaiting-check'
    state['findings'] = [{
        'id': 'R1', 'batch': 'B1', 'status': 'fixed-awaiting-check',
        'disposition': 'actionable'}]
    fixture.write('arithmetic.py', 'print(2 - 3)\n')
    fixture.write(loop.STATE, state)
    fixture.commit()


def repair_while_verifying(fixture, context, state, report):
    fixture.write('arithmetic.py', 'print(2 + 3)\n')
    report['changedPaths'].append('arithmetic.py')
    result = loop.run_command(['python3', 'arithmetic.py'], fixture.root)
    assert result == '5'
    fixture.write(report['checks'][0]['evidence'], result + '\n')
    report['checks'][0]['command'] = 'python3 arithmetic.py (assert stdout == 5)'


run_fixture('verifier-repairs-and-certifies', setup=pending_verification,
            transform=repair_while_verifying, count=1)

state = q.initial_state()
state['tasks'][0].update(status='in-progress', children=['F1a', 'F1b'])
state['tasks'] += [
    {'id': 'F1a', 'batch': 'B1', 'status': 'blocked'},
    {'id': 'F1b', 'batch': 'B1', 'status': 'pending', 'dependsOn': [], 'independent': True},
]
results['independent-child-starved'] = {
    'next': loop.selection(state), 'independentChild': state['tasks'][-1]}

state = q.initial_state()
state['tasks'][0]['status'] = 'blocked'
state['tasks'][1]['status'] = 'implemented'
state['tasks'].append({
    'id': 'F3', 'batch': 'B2', 'status': 'implemented', 'dependsOn': [], 'independent': True})
state['batches'].append({'id': 'B2', 'status': 'ready-for-review', 'reviewedCommit': None})
results['independent-group-review-starved'] = {
    'next': loop.selection(state), 'readyBatch': state['batches'][-1]}

fixture = q.GitFixture()
try:
    state = fixture.state()
    state['tasks'][0]['status'] = 'blocked'
    fixture.write(loop.STATE, state)
    fixture.commit()
    client = q.FakeOpenCode(fixture, issue=False)
    runner = loop.Runner(fixture.root, client=client)
    client.runner = runner

    def discover_lane(context, state, report):
        state['tasks'][1].update(dependsOn=[], independent=True)
        report['taskAdjustments'] = [{
            'id': 'F2', 'reason': 'Independent approved task does not need blocked F1'}]

    client.transform = discover_lane
    try:
        with contextlib.redirect_stdout(io.StringIO()):
            runner.run(1, 30)
        result = 'accepted'
    except loop.Halt as error:
        result = str(error)
    results['blocked-selector-cannot-record-independent-lane'] = {
        'result': result, 'calls': client.calls}
finally:
    fixture.close()

before = q.initial_state()
for task in before['tasks']:
    task['status'] = 'implemented'
before['batches'][0]['status'] = 'ready-for-review'
after = copy.deepcopy(before)
after['batches'][0]['reviewCoverage'] = [
    'entry-flow-complete; privacy/restart pending']
try:
    loop.validate_state(after)
    loop.validate_transition(before, after, loop.selection(before), {
        'startCommit': 'code', 'findingIds': []})
    result = 'accepted'
except loop.Halt as error:
    result = str(error)
results['partial-group-review-rejected'] = {'result': result}

print(json.dumps(results, indent=2))
