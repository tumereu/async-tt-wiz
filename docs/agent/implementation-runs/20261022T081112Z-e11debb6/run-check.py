"""Read-only I04a source/evidence/transition/docs/dependency handoff; no new product qualification."""
import base64
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import urllib.request
import xml.etree.ElementTree as ET


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


root = Path.cwd()
prefix = Path('docs/agent/implementation-runs/20261022T081112Z-e11debb6')
report_path = str(prefix) + '.json'
report = json.loads(Path(report_path).read_text())
before = json.loads(git('show', report['startCommit'] + ':docs/agent/implementation-state.json'))
after = json.loads(Path('docs/agent/implementation-state.json').read_text())
api = runpy.run_path('scripts/implementation-loop.py')
choice = {'mode': 'implement', 'target': 'I04a', 'findingIds': []}
assert git('rev-parse', 'HEAD') == report['startCommit']
assert git('branch', '--show-current') == 'master'
assert not git('diff', '--cached', '--name-only')
assert before['authorization'] == 'authorized' and 'D-304' in Path(before['authorizationRecord']).read_text()
assert api['selection'](before) == choice
api['validate_state'](after)
api['validate_transition'](before, after, choice, report)
assert after['activeRun'] is None and after['runs'] == before['runs'] + [report_path]
for key in before:
    if key not in {'tasks', 'runs'}:
        assert before[key] == after[key], key
for old, new in zip(before['tasks'], after['tasks'], strict=True):
    if old['id'] != 'I04a':
        assert old == new, old['id']
    else:
        assert new['status'] == 'implemented' and new['implementationRecord'] == report_path
assert api['selection'](after) == {'mode': 'implement', 'target': 'I04b', 'findingIds': []}
print('PASS supplied baseline/master, D-304 authorization/priority, I04a-only implemented transition, next I04b; all other tasks/reviews/findings/model/grants/release gates unchanged')

actual = set(git('diff', '--name-only').splitlines()) | set(git('ls-files', '--others', '--exclude-standard').splitlines())
assert actual == set(report['changedPaths']), (actual ^ set(report['changedPaths']))
runner = object.__new__(api['Runner'])
runner.root = root
runner.state_path = Path('docs/agent/implementation-state.json')
runner.state_relative = str(runner.state_path)
runner.role_paths(before, choice, report, actual, report_path)
for path in actual:
    data = Path(path).read_bytes()
    assert b'\r' not in data and data.endswith(b'\n'), path
    assert all(line.rstrip() == line for line in data.splitlines()), path
subprocess.run(['git', 'diff', '--check'], check=True)
for path in ('server/README.md', 'e2e/README.md'):
    text = Path(path).read_text()
    assert text.count('```') % 2 == 0, path
    for target in re.findall(r'\]\(([^)]+)\)', text):
        if '://' not in target and not target.startswith('#'):
            assert (Path(path).parent / target.split('#')[0]).exists(), (path, target)
print('PASS exact owned paths, role scope, Markdown local links/fences, newline/whitespace and unchanged UI packet (no publication needed)')

# Recover actual JUnit bytes, retaining red originals as diagnostics, not a manufactured whole green run.
results = {}
for name in ('initial-junit.json', 'reset-junit.json', 'affected-recheck-junit.json'):
    entries = json.loads((prefix / name).read_text())
    for filename, encoded in entries.items():
        data = base64.b64decode(encoded, validate=True)
        suite = ET.fromstring(data)
        tests = suite.findall('testcase')
        failures = sum(t.find('failure') is not None or t.find('error') is not None for t in tests)
        skipped = sum(t.find('skipped') is not None for t in tests)
        assert skipped == 0
        results.setdefault(name, {'tests': 0, 'failures': 0})
        results[name]['tests'] += len(tests)
        results[name]['failures'] += failures
        for test in tests:
            key = (suite.attrib['name'], test.attrib['name'])
            if test.find('failure') is None and test.find('error') is None:
                results.setdefault('greenAssertions', set()).add(key)
    if name != 'initial-junit.json':
        assert results[name]['failures'] == 0
assert results['initial-junit.json'] == {'tests': 27, 'failures': 5}
assert results['reset-junit.json'] == {'tests': 5, 'failures': 0}
assert results['affected-recheck-junit.json'] == {'tests': 2, 'failures': 0}
green = results.pop('greenAssertions')
print('PASS lossless actual JUnit: ' + json.dumps(results, sort_keys=True))
print(f'PASS {len(green)} distinct server green assertions across original and affected rechecks; initial five failures retained, no skips or full-suite rerun claim')
for name in ('image-build.json', 'compose-reset.json', 'environment-tests.json', 'affected-recheck.json'):
    check = json.loads((prefix / name).read_text())
    assert check['exitCode'] == 0
    assert hashlib.sha256(check['output'].encode()).hexdigest() == check['outputSha256']
print('PASS passing required build/Compose/environment/affected output bytes and retained immutable local image manifest')

manifest = json.loads((prefix / 'image-manifest.json').read_text())
for filename, expected in manifest['libraries'].items():
    assert hashlib.sha256(Path('server/build/install/server/lib', filename).read_bytes()).hexdigest() == expected
print('PASS tested local image payload matches current installed locked runtime JARs (not a new image run): ' + manifest['imageId'])

for group, artifact, version in (('org.eclipse.angus', 'jakarta.mail', '2.0.5'),
                                 ('org.eclipse.angus', 'angus-activation', '2.0.3'),
                                 ('jakarta.activation', 'jakarta.activation-api', '2.1.4')):
    base = 'https://repo.maven.apache.org/maven2/' + group.replace('.', '/') + '/' + artifact + '/' + version + '/' + artifact + '-' + version
    with urllib.request.urlopen(base + '.jar', timeout=30) as response:
        jar = response.read()
    local = Path('server/build/install/server/lib', artifact + '-' + version + '.jar').read_bytes()
    assert hashlib.sha256(jar).digest() == hashlib.sha256(local).digest()
    with urllib.request.urlopen(base + '.pom', timeout=30) as response:
        pom = response.read()
    xml = ET.fromstring(pom)
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    license_pom = base + '.pom'
    license_bytes = pom
    for _ in range(6):
        licenses = [(item.findtext('m:name', namespaces=ns), item.findtext('m:url', namespaces=ns))
                    for item in xml.findall('m:licenses/m:license', ns)]
        if licenses:
            break
        parent = xml.find('m:parent', ns)
        assert parent is not None
        parent_group = parent.findtext('m:groupId', namespaces=ns)
        parent_artifact = parent.findtext('m:artifactId', namespaces=ns)
        parent_version = parent.findtext('m:version', namespaces=ns)
        assert '${' not in parent_version
        license_pom = ('https://repo.maven.apache.org/maven2/' + parent_group.replace('.', '/') + '/' +
                       parent_artifact + '/' + parent_version + '/' + parent_artifact + '-' + parent_version + '.pom')
        with urllib.request.urlopen(license_pom, timeout=30) as response:
            license_bytes = response.read()
        xml = ET.fromstring(license_bytes)
    assert licenses
    print('DEPENDENCY PROVENANCE ' + json.dumps({'coordinate': f'{group}:{artifact}:{version}', 'pom': base + '.pom',
        'pomSha256': hashlib.sha256(pom).hexdigest(), 'jarSha256': hashlib.sha256(jar).hexdigest(),
        'licensePom': license_pom, 'licensePomSha256': hashlib.sha256(license_bytes).hexdigest(), 'licenses': licenses}, sort_keys=True))
print('PASS public POM/license/artifact provenance, not legal/rights clearance or production SMTP delivery')

hashes = {p: hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in sorted(actual)
          if not p.startswith('docs/agent/implementation-runs/') and p != 'docs/agent/implementation-state.json'}
print('FINAL SOURCE INPUT IDENTITIES ' + json.dumps(hashes, sort_keys=True))
print('RETAINED OBLIGATIONS: I04b native A03/A04/cold-warm links/two-device B1 journey; I04 child audit; B1 independent capability review; I34 full suite including positive local TLS/auth transport; I35 compatibility; I36 complete UI comparison; external operator HTTPS/SMTP/proxy/rights/signing/hardware gates. No full cross-feature P/T/F pass claimed.')
