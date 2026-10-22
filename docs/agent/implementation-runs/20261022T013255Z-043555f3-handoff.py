"""Generate this unit's report from actual retained command results, not guesses."""
import json
from pathlib import Path
import shlex
import subprocess

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'docs/agent/implementation-runs/20261022T013255Z-043555f3'
REPORT = PREFIX + '.json'
checks = []
for path in sorted(ROOT.glob(PREFIX + '-*.json')):
    if path.name.endswith(('-handoff.json', '-validation.json')):
        continue
    result = json.loads(path.read_text())
    assert set(result) == {'command', 'exitCode', 'output'}, path
    diagnostic = result['exitCode'] != 0 or '-topology' in path.name
    check = {'kind': 'diagnostic' if diagnostic else 'required',
             'command': shlex.join(result['command']), 'exitCode': result['exitCode'],
             'evidence': str(path.relative_to(ROOT))}
    support = [PREFIX + '-record.py']
    support.extend(arg for arg in result['command'] if arg.startswith(PREFIX) and arg.endswith('.py'))
    check['supportPaths'] = list(dict.fromkeys(support))
    checks.append(check)
for name in ('handoff', 'validation'):
    checks.append({'kind': 'required', 'command': 'python3 ' + PREFIX + '-' + name + '.py',
                   'exitCode': 0, 'evidence': PREFIX + '-' + name + '.json',
                   'supportPaths': [PREFIX + '-record.py', PREFIX + '-' + name + '.py']})
changed = set(subprocess.check_output(['git', 'diff', '--name-only'], cwd=ROOT, text=True).splitlines())
changed.update(subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], cwd=ROOT, text=True).splitlines())
changed.update({REPORT, PREFIX + '-handoff.json', PREFIX + '-validation.json'})
report = {
    'runId': '20261022T013255Z-043555f3', 'sessionID': 'ses_e9ec29c1bffer68kQmuY4aj5nw',
    'startCommit': '82932bf9978a8218868fde311d82f4b6aa983c5d',
    'mode': 'implement', 'target': 'I01f1b2a', 'findingIds': [], 'newFindingIds': [], 'reviewedUnitIds': [],
    'outcome': 'progress',
    'summary': 'Completed I01f1b2a public-only owned route lifecycle. Two complete fresh API29 non-GMS WHPX/real-Compose transport qualifications pass backend 404, both catalogs/metadata/printing/PNG byte hashes/MIME and unchanged art origins, eight private-route 404s, blocked container egress, actual broken/unavailable/foreign-route failure/recovery and exact cleanup/unrelated inventory. Same-port reverse reaches the existing WSL public forwarder through dedicated Windows ADB; no added relay or product trust/networking change. Twenty focused/121 total Python tests, locked shared/host JVM tests/build/boundaries, full actual native services regression and unchanged 875-item coverage/fixtures pass. Missing manifests, initial HTTP probe mistakes, stale Windows broker baselines, a disproved diagnosis and delayed WSL proxy-exit failures remain diagnostic with concrete repairs/passing qualification. Only this child closes; combined APK handoff I01f1b2b, all ancestors, Kotlin/instrumentation/UI/product journeys, B1 review and release gates remain open.',
    'selectionReason': 'Clean master matched the supplied startCommit; D-304 explicitly authorizes the application plan. Findings are empty and B1 review is not due until I01-I04. I01f1b2a is the earliest ready child after implemented I01f1b1 and I01e; its measured exact-owned transport is a prerequisite to I01f1b2b combined normal-APK/environment handoff and I01f2 Kotlin/device tests. No priority skipped or parent/review obligation reduced.',
    'checks': checks, 'changedPaths': sorted(changed),
    'sourceIds': {
        'infrastructurePortionsOnly': ['P-001', 'T-551', 'T-630', 'T-662'],
        'laterBehaviorConstraints': ['P-059', 'P-063', 'P-065', 'P-066', 'T-632', 'T-647', 'T-649'],
        'accounting': 'Unchanged P-001-P-072/T-001-T-715/F01-F88 ledger; no product scenario/UI requirement is marked executed by these infrastructure checks.'
    },
    'contractSections': [
        'AGENTS.md and docs/agent/implementation-authorization.md: D-304 authorization, D-305 repair, exact owned save and one bounded unit',
        'docs/agent/implementation-plan.md: I01 foundation, B1 independent review after I01-I04 and retained parent qualification',
        'docs/agent/implementation-loop.md: earliest-ready selection, truthful evidence and independent review boundaries',
        'docs/design/04-technical/test-architecture.md: required device endpoints only, explicit ports and real-system versus lower-layer checks',
        'docs/design/05-verification/e2e-strategy.md: synthetic data, no product-action injection, retained restart and evidence before cleanup',
        'e2e/README.md and e2e/devices-README.md: same-port loopback origins, private administration, ownership and reset versus restart'
    ],
    'diagnostics': [
        'Initial provisioning had no server/adapter build manifests. Ordinary strictly locked real-server build and pinned adapter builder supplied them. The later :e2e:clean intentionally removed the adapter manifest again; existence probe established this and the ordinary builder repaired it. No hand-written manifest or bypass.',
        'Initial shell HTTP measurements used HTTP/1.0 (synthetic adapter requires 1.1) and netcat EOF half-close (existing relay closes both directions on EOF). HTTP/1.1 plus bounded held stdin produced real device responses. Runtime relay/app code was not changed; actual complete transport checks assert complete framing/bytes/hashes.',
        'An intermediate transport repeat failed its unchanged unrelated Windows-listener assertion after prior Python socket regressions. The first guess that only two owned Compose ports disappeared was disproved and retained. Read-only diagnosis found expired mirrored-loopback entries on the still-alive dllhost broker with no Linux listeners; a diagnosis-script process-name casing error is retained too. No broker/network mutation or inventory exclusion was made. A fresh sequential complete transport repeat passes the identical strict assertion.',
        'The first native services regression saw the just-cleaned synthetic source proxy expire during its unrelated baseline. Passive exact-known-endpoint drain preflight passes; no listener is removed by the preflight. A subsequent native repeat then exposed delayed WSL launcher exit immediately after native processes stopped. Post-failure observation showed the exact proxies exited naturally. Cleanup now waits at most 15 seconds for recorded identity exit without signalling any process; delayed/reused/timeout unit checks and the complete unchanged native lifecycle checker pass.',
        'Existing normal-app mock tests print Python ResourceWarning for two test SQLite connections; all 121 tests execute and pass, with no skipped/red test relabeled. No unrelated storage/product change was made.'
    ],
    'limitations': [
        'Transport infrastructure only: no APK installed by this child, combined normal-APK/environment handoff, Kotlin/Appium driver session/input, Android instrumentation, normal downloader/auth/gameplay/Canvas acceptance or seeded product state. I01f1b2b owns combined startup/reset/restart qualification; I01f2 still owns all 14 actual storage instrumentation tests.',
        'No additional relay is necessary on the measured host. Existing I01e relay PID/start/command remains recorded and checked by its owner. Other hosts must qualify actual connectivity; no global networking, wildcard listener, private DB/SMTP/admin route or production TLS-trust relaxation.',
        'A timeout/interruption after route intent but before positive creation confirmation cannot authorize adopting/removing a matching ambiguous mapping. Such an intent fails closed for evidence-backed recovery; absent intents/ordinary partial successful sets can be explicitly unrouted safely. No remove-all or default ADB adoption.',
        'All owned device/native/watchdog/WSL/Appium/Compose/relay resources are cleaned, including failed checks and checker-created sibling instances; actual XML/command outputs retained. Synthetic owner IDs are not production credentials. Build caches/private AVD evidence may remain. Stale mirrored-loopback broker behavior remains a host caveat; never kill or reconfigure that unrelated broker.',
        'All parent outcomes/checks, other tasks, batch review coverage/findings, runner/model/grants/authorization and externalReleaseGates remain unchanged. No UI document changed, so same-URL UI publication is not applicable. No subagent, second loop, deployment, paid resource, physical/current/tablet/upgrade/arm64 or release claim.'
    ]
}
(ROOT / REPORT).write_text(json.dumps(report, indent=2) + '\n')
print('Generated report from', len(checks), 'actual retained/check-output obligations and', len(changed), 'exact owned paths; only I01f1b2a marked implemented.')
