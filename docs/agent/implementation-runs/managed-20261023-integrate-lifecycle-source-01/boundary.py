"""Check this candidate's lease and immutable imports; not behavioral verification."""
import hashlib
import json
from pathlib import Path
import subprocess

context = json.loads(Path('/tmp/opencode/managed-20261023-integrate-lifecycle-source-01/context.json').read_text())
def git(*args):
    return subprocess.check_output(['git', *args])

pin = 'e84f93557cd5093858e499af5eb747650d70b99f'
base = context['startCommit']
paths = set(git('diff', base, '--name-only').decode().splitlines())
paths.update(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
run = 'docs/agent/implementation-runs/' + context['runId']
prefixes = ['docs/agent/implementation-runs/managed-20261023-' + n for n in
            ('i12a-01', 'i12b-01', 'i27-01', 'i29-01', 'integrate-lifecycle-01', 'verify-lifecycle-01', 'toolkit-ui-01', 'activity-runtime-01')]
assert not git('ls-files', '--unmerged').strip()
assert all(p in context['ownedPaths'] or p == run + '.json' or p.startswith(run + '/') or
           any(p == n + '.json' or p.startswith(n + '/') for n in prefixes) for p in paths)
controls = git('ls-tree', '-r', '--name-only', base, '--', 'scripts', 'AGENTS.md',
               'docs/agent/implementation-state.json', 'docs/agent/implementation-permissions.json',
               'docs/agent/implementation-authorization.md').decode().splitlines()
for p in controls:
    assert git('hash-object', '--path=' + p, p).strip() == git('rev-parse', base + ':' + p).strip(), p
preserved = ['protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/' + name for name in
             ('Gameplay.kt', 'HistoryGrouping.kt', 'GameplayHistory.kt', 'GameResultLifecycle.kt')]
preserved += ['server/src/main/kotlin/org/asyncttwiz/server/game/PlayStore.kt',
              'androidApp/src/main/kotlin/org/asyncttwiz/android/ResultActions.kt',
              'androidApp/src/main/kotlin/org/asyncttwiz/android/ResultScreen.kt', 'e2e/environment.py']
for p in preserved:
    assert Path(p).read_bytes() == git('show', base + ':' + p), p
imports = {}
for p in sorted(paths):
    namespace = next((n for n in prefixes if p == n + '.json' or p.startswith(n + '/')), None)
    if namespace:
        source = ('919fd5110fcb849ae9094ea399279f109fe38c6e' if namespace.endswith('activity-runtime-01') else
                  'a6bff156f3c9acb3c9b16e6694121030a197a3ca' if namespace.endswith('toolkit-ui-01') else pin)
        assert Path(p).read_bytes() == git('show', source + ':' + p), p
        imports[p] = hashlib.sha256(Path(p).read_bytes()).hexdigest()
v13 = 'server/src/main/resources/db/migration/V13__game_chat.sql'
assert Path(v13).read_bytes() == git('show', pin + ':' + v13)
print(json.dumps({'scope': 'Lease, canonical bytes and imported evidence only; not self-verification',
                  'base': base, 'observedHEAD': git('rev-parse', 'HEAD').decode().strip(),
                  'canonicalStateSha256': hashlib.sha256(Path(context['state']).read_bytes()).hexdigest(),
                  'unchangedControlsAndScripts': len(controls), 'importedReportEvidenceSha256': imports,
                  'preservedConsolidationAndResetSource': preserved,
                  'v13Sha256': hashlib.sha256(Path(v13).read_bytes()).hexdigest(),
                  'migrations': sorted(p.name for p in Path(v13).parent.glob('*.sql')),
                  'changedPaths': sorted(paths)}, indent=2))
