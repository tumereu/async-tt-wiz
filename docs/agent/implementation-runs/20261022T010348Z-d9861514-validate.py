"""Final bounded-unit state/evidence/paths/docs check, not independent review."""
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[3]
REPORT = 'docs/agent/implementation-runs/20261022T010348Z-d9861514.json'
STATE = 'docs/agent/implementation-state.json'
EVIDENCE = 'docs/agent/implementation-runs/20261022T010348Z-d9861514-validation.json'


def command(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True)


def main():
    report = json.loads((ROOT / REPORT).read_text())
    state = json.loads((ROOT / STATE).read_text())
    baseline = json.loads(command('git', 'show', report['startCommit'] + ':' + STATE))
    assert command('git', 'rev-parse', 'HEAD').strip() == report['startCommit']
    assert report['mode'] == 'implement' and report['target'] == 'I01f1b1' and report['outcome'] == 'progress'
    assert report['findingIds'] == report['newFindingIds'] == report['reviewedUnitIds'] == []
    assert state['activeRun'] is None and state['authorization'] == 'authorized' and state['mode'] == 'active'
    assert state['runs'] == baseline['runs'] + [REPORT]
    tasks = {t['id']: t for t in state['tasks']}
    old_tasks = {t['id']: t for t in baseline['tasks']}
    assert tasks.keys() == old_tasks.keys()
    for key in tasks:
        if key == report['target']:
            assert old_tasks[key]['status'] == 'pending' and tasks[key]['status'] == 'implemented'
            assert tasks[key]['implementationRecord'] == REPORT and tasks[key]['qualification']
            for field in ('id', 'batch', 'outcome', 'checks', 'dependsOn'):
                assert tasks[key][field] == old_tasks[key][field]
        else:
            assert tasks[key] == old_tasks[key], key
    for key in state:
        if key not in ('tasks', 'runs'):
            assert state[key] == baseline[key], key
    assert not state['findings']
    assert all(tasks[dep]['status'] == 'implemented' for dep in tasks[report['target']]['dependsOn'])
    for parent in ('I01f1b', 'I01f1', 'I01f', 'I01'):
        assert tasks[parent]['status'] == 'in-progress'
    assert state['batches'][0]['status'] == 'pending'
    listed = report['changedPaths']
    assert len(listed) == len(set(listed))
    actual = set(command('git', 'diff', '--name-only', report['startCommit']).splitlines())
    actual.update(command('git', 'ls-files', '--others', '--exclude-standard').splitlines())
    assert actual | {EVIDENCE} == set(listed), (actual ^ set(listed))
    assert not command('git', 'diff', '--cached', '--name-only').strip(), 'Unexpected staged work'
    assert not command('git', 'diff', '--check')
    for check in report['checks']:
        assert check['evidence'] in listed
        for support in check.get('supportPaths', []):
            assert support in listed and support.startswith(REPORT[:-5] + '-') and (ROOT / support).is_file()
        if check['evidence'] == EVIDENCE:
            continue  # capture-check creates this exact output after successful exit.
        evidence = json.loads((ROOT / check['evidence']).read_text())
        assert evidence['exitCode'] == check['exitCode']
        assert check['kind'] != 'required' or check['exitCode'] == 0
        assert hashlib.sha256(evidence['output'].encode()).hexdigest() == evidence['outputSha256']
    for path in listed:
        if path.endswith('.md'):
            text = (ROOT / path).read_text()
            for link in re.findall(r'\]\(([^)]+)\)', text):
                if '://' not in link and not link.startswith('#'):
                    assert ((ROOT / path).parent / link.split('#')[0]).exists(), (path, link)
    # Retain the exact initial absence diagnostic omitted from the checker's first
    # failure printout; this reads only this run's owned synthetic command record.
    first = Path('/tmp/opencode/atw-device-d986151400004af58b9f3861313df8c1')
    entries = [json.loads(line) for line in (first / 'commands.jsonl').read_text().splitlines()]
    absence = [c for c in entries if c['command'][-3:] == ['pm', 'path', 'org.asyncttwiz.android']]
    assert len(absence) == 1 and absence[0]['exitCode'] == 1 and absence[0]['output'] == ''
    assert (first / 'cleaned.json').is_file()
    print('ACTUAL FIRST CHECKER ABSENT-PACKAGE RESULT', json.dumps(absence[0]))
    print('PASS: only selected child implemented, parents/review/findings/gates/model/grants unchanged; exact owned path set, passing required retained outputs, diagnostic failures preserved, same-URL UI publication not applicable (no UI-document edits), local documentation links and whitespace valid.')


if __name__ == '__main__':
    main()
