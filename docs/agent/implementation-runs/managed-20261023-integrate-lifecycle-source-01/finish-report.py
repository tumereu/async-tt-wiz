"""Use the shared report writer, extending its path inventory across this run's saved checkpoints."""
import importlib.util
import hashlib
import json
from pathlib import Path
import shlex
import subprocess

root = Path.cwd()
context = json.loads(Path('/tmp/opencode/managed-20261023-integrate-lifecycle-source-01/context.json').read_text())
details = json.loads(Path(__file__).with_name('report-details.json').read_text())
for check in details['checks']:
    record = json.loads(Path(check['evidence']).read_text())
    check.update(command=shlex.join(record['command']), exitCode=record['exitCode'])
    if check['kind'] == 'required':
        assert check['exitCode'] == 0
apk = Path('androidApp/build/outputs/apk/debug/androidApp-debug.apk')
details['artifact'] = {'path': str(apk), 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
                       'meaning': 'Normal non-FCM APK produced in phase3; no installation/native claim.'}
spec = importlib.util.spec_from_file_location('shared_evidence', root / 'scripts/implementation-evidence.py')
shared = importlib.util.module_from_spec(spec)
spec.loader.exec_module(shared)
report = shared.make_report(root, context, details)
changed = subprocess.check_output(['git', 'diff', context['startCommit'], '--name-only', '-z']).decode().split('\0')
untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '-z']).decode().split('\0')
report['changedPaths'] = sorted(set(changed + untracked + [context['report']]) - {''})
shared.write(root, context['report'], report)
print(f"Wrote custom cumulative source-integration report: {context['report']}; {len(report['changedPaths'])} exact paths")
