#!/usr/bin/env python3
"""Read-only implementation dashboard (stdlib only; no network or agent calls).

Usage: watch -t -n 10 'python3 ~/atw-progress.py'
       python3 scripts/implementation-progress.py --details --width 100

Totals use pushed integration state, not speculative worker proposals. Rolling
windows use first-parent state commit times, never file modification times or
commit/report counts. Items vary in size: counts are not effort estimates/ETAs.
"""

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import textwrap
import time


DEFAULT_REPO = Path('/home/tuomas/projects/async-tt-wiz')
STATE = 'docs/agent/implementation-state.json'
TITLES = dict(zip((f'I{i:02}' for i in range(1, 37)), (
    'Build, storage and test foundation', 'Registration and login',
    'Navigation, logout and resume', 'Password reset and account journeys',
    'Catalog and card-art downloads', 'Deck import and preview',
    'Deck collection and management', 'Deck overwrite and merge',
    'Contacts, invitations and game creation', 'Deck preparation and sideboard',
    'Ready and initial game start', 'Lobby completion and two-player journeys',
    'Ordered gameplay engine and server', 'Responsive sync and prediction',
    'Table rendering, targeting and gestures', 'Basic table actions and recovery',
    'Card/player counters, notes and inspection', 'Library, draw and mulligan',
    'Tokens, clones and links', 'Turns, phases and quick actions',
    'Combat and Stack', 'Timeline, catch-up and event comments',
    'Undo and Redo', 'History forks and branch switching',
    'Branch archive and restore', 'Full history download and recovery',
    'Chat and unread messages', 'Game results and rematch',
    'Shared game deletion', 'Notifications and background activity',
    'Integrated multiplayer journeys', 'Whole-game usability and accessibility',
    'Performance and memory qualification', 'Final full-suite qualification',
    'Device compatibility and upgrades', 'Delivery evidence and final UI review',
)))
TITLES.update(I12a='Setup cancellation and lifecycle recovery',
              I12b='Cancellation UI and two-player lobby journeys',
              I11a='Shared initial board and server Ready/start',
              I11b='Native Ready controls and initial table',
              I10b1='Ordered sideboard transfers and prediction',
              I10b2='Native sideboard gestures and feedback',
              I01f3='Three-device and cold-boot qualification')
GROUPS = dict(zip((f'B{i}' for i in range(1, 9)), (
    'Accounts and resume', 'Deck import and management',
    'Prepare and start a game', 'Shared tabletop',
    'Manual tools, combat and Stack', 'History, catch-up and corrections',
    'Chat, results and notifications', 'Final testing and delivery',
)))
GATES = {'rights-attribution': 'Data/art rights and attribution',
         'release-signing-distribution': 'Release signing and distribution',
         'reference-device-performance': 'Physical reference-phone performance',
         'external-operator-interface-readiness': 'Operator readiness'}
LIVE = {'reserved', 'starting', 'running'}
WAITING = {'saved', 'succeeded', 'completed', 'awaiting-integration', 'ready-for-integration'}


def read_json(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def optional_json(path, warnings):
    try:
        return read_json(path)
    except FileNotFoundError:
        return {}
    except (OSError, ValueError):
        warnings.add('Some journal metadata is unreadable; retry next refresh')
        return {}


def git(repo, *args, input=None):
    output = subprocess.run(['git', '-C', str(repo), *args], input=input,
                            check=True, capture_output=True, text=True, timeout=10,
                            env={**os.environ, 'GIT_OPTIONAL_LOCKS': '0'}).stdout
    return output if args[0] == 'cat-file' else output.strip()


def stamp(value):
    if isinstance(value, (int, float)):
        return value / 1000 if value > 100_000_000_000 else value
    try:
        result = datetime.fromisoformat(value.replace('Z', '+00:00'))
        return result.replace(tzinfo=result.tzinfo or timezone.utc).timestamp()
    except (AttributeError, TypeError, ValueError):
        return None


def elapsed(seconds):
    if seconds is None or seconds < 0:
        return 'unknown'
    seconds = int(seconds)
    if seconds < 60:
        return f'{seconds}s'
    if seconds < 3600:
        return f'{seconds // 60}m{seconds % 60:02}s'
    return f'{seconds // 3600}h{seconds % 3600 // 60:02}m'


def clean(value):
    return ' '.join(''.join(c if c.isascii() and c.isprintable() else ' '
                            for c in str(value)).split())


def description(value):
    # Internal task identifiers are not useful owner-facing descriptions.
    return clean(re.sub(r'\b[IB]\d+[a-z0-9]*(?:-[A-Z]\d+)?\b', '', str(value)))


def title(target, tasks):
    if target in TITLES:
        return TITLES[target]
    if target in GROUPS:
        return GROUPS[target]
    task = tasks.get(target, {})
    return textwrap.shorten(description(task.get('title') or task.get('outcome') or
                                        'Unspecified work').split('. ')[0],
                            width=125, placeholder='...')


def state_events(before, after, when):
    """Count actual scoped transitions; splits/checkpoints/ancestor closure aren't extra items."""
    events = []
    old = {t['id']: t for t in before.get('tasks', [])}
    for task in after.get('tasks', []):
        previous = old.get(task['id'], {})
        if task.get('children'):
            continue
        was_done = previous.get('status') == 'implemented'
        is_done = task.get('status') == 'implemented'
        if is_done and not was_done:
            events.append(dict(kind='items', target=task['id'], when=when,
                               record=task.get('implementationRecord')))
        elif was_done and not is_done:
            events.append(dict(kind='reopened', target=task['id'], when=when))
    old_batches = {b['id']: b for b in before.get('batches', [])}
    for batch in after.get('batches', []):
        previous = old_batches.get(batch['id'], {})
        checked = batch.get('review', {}).get('completed', {})
        old_checked = previous.get('review', {}).get('completed', {})
        for unit in checked.keys() - old_checked.keys():
            events.append(dict(kind='reviews', target=batch['id'], when=when, unit=unit))
        for unit in old_checked.keys() - checked.keys():
            events.append(dict(kind='reopened', target=batch['id'], when=when, unit=unit))
    old_findings = {f['id']: f for f in before.get('findings', [])}
    for finding in after.get('findings', []):
        previous = old_findings.get(finding['id'], {})
        if finding.get('status') == 'verified' and previous.get('status') != 'verified':
            events.append(dict(kind='fixes', target=finding['id'], when=when,
                               name=finding.get('title'), record=finding.get('verificationRecord')))
        elif previous.get('status') == 'verified' and finding.get('status') != 'verified':
            events.append(dict(kind='reopened', target=finding['id'], when=when,
                               name=finding.get('title')))
    new_records = [r for r in after.get('runs', []) if r not in before.get('runs', [])]
    if len(new_records) == 1:
        for event in events:
            if not event.get('record'):
                event['record'] = new_records[0]
    return events


def pushed_history(repo, now):
    """Read snapshots in one cat-file process; first-parent excludes unaccepted worker history."""
    ref = git(repo, 'rev-parse', '--symbolic-full-name', '@{upstream}')
    head = git(repo, 'rev-parse', ref)
    history = [row.split() for row in git(
        repo, 'log', '--first-parent', '--format=%H %ct', head, '--', STATE).splitlines()]
    if not history:
        raise ValueError('No pushed implementation-state history')
    recent = [i for i, (_, when) in enumerate(history) if float(when) >= now - 8 * 3600]
    # Include the state BEFORE the oldest window boundary, not only states inside it.
    count = min(len(history), max(recent, default=0) + 2)
    history = history[:count]
    payload = git(repo, 'cat-file', '--batch',
                  input=''.join(f'{commit}:{STATE}\n' for commit, _ in history))
    # Size is a byte count; don't slice decoded Unicode by character count.
    data = payload.encode('utf-8')
    states = []
    for _, when in history:
        header, data = data.split(b'\n', 1)
        size = int(header.split()[-1])
        states.append((float(when), json.loads(data[:size])))
        data = data[size + 1:]
    events = []
    for i in range(len(states) - 2, -1, -1):
        events.extend(state_events(states[i + 1][1], states[i][1], states[i][0]))
    return states[0][1], events, head, ref


def selection(record):
    return record.get('selection') or {'target': record.get('target'),
                                       'mode': record.get('role')}


def run_times(record, snapshot):
    times = snapshot.get('time', {})
    start = stamp(record.get('launchedAt')) or stamp(times.get('created')) or stamp(record.get('reservedAt'))
    if start is None:
        match = re.search(r'\d{8}T\d{6}Z', record.get('runId', ''))
        if match:
            start = datetime.strptime(match[0], '%Y%m%dT%H%M%SZ').replace(tzinfo=timezone.utc).timestamp()
    end = stamp(times.get('idle')) or stamp(record.get('finishedAt')) or stamp(record.get('savedAt'))
    return start, end


def journal_records(journal, warnings):
    orchestrator = optional_json(journal / 'orchestrator.json', warnings)
    records = {}
    for path in journal.glob('*/result.json'):
        record = optional_json(path, warnings)
        if record.get('runId'):
            records[record['runId']] = record
    # Dispatch records supplement the orchestrator; its matching lease wins.
    for path in (journal / 'dispatch').glob('*.json'):
        record = optional_json(path, warnings)
        if record.get('runId') and record.get('status'):
            records[record['runId']] = record
    records.update(orchestrator.get('leases', {}))
    active = optional_json(journal / 'active.json', warnings)
    if active.get('runId') and active['runId'] not in records:
        records[active['runId']] = active
    for run_id, record in records.items():
        record.setdefault('runId', run_id)
        snapshot = optional_json(journal / run_id / 'session.json', warnings)
        # Managed controllers can retain a final snapshot inline or in dispatch.
        snapshot = record.get('sessionMetadata') or snapshot or record.get('serverCreatedMetadata', {})
        record['_times'] = run_times(record, snapshot)
        status = record.get('status', 'unknown')
        if status in LIVE and snapshot.get('time', {}).get('idle'):
            status = 'saved' if snapshot.get('outcome') == 'succeeded' else 'needs-attention'
        record['_status'] = status
    return orchestrator, records


def runtime(event, records):
    record = records.get(Path(event.get('record') or '').stem, {})
    start, end = record.get('_times', (None, None))
    return elapsed(end - start) if start is not None and end is not None else 'unknown'


def event_runtimes(repo, head, events, records, warnings):
    """Acceptance reports name original workers; don't time the supervisor as the worker."""
    reports = {}
    for event in events:
        event['runtime'] = runtime(event, records)
        path = event.get('record')
        if event['runtime'] != 'unknown' or not path:
            continue
        if path not in reports:
            try:
                reports[path] = json.loads(git(repo, 'show', f'{head}:{path}'))
            except (ValueError, subprocess.SubprocessError):
                reports[path] = {}
                warnings.add('Some accepted-result timing metadata is unavailable')
        reference = reports[path].get('workerProposal', {}).get('report')
        if reference:
            event['runtime'] = runtime({'record': reference}, records)


def render(state, events, records, orchestrator, now, width, recent=5, details=False):
    tasks = {t['id']: t for t in state['tasks']}
    main = [t for t in tasks.values() if re.fullmatch(r'I\d+', t['id'])]
    leaves = [t for t in tasks.values() if not t.get('children')]
    remaining = [t for t in leaves if t.get('status') != 'implemented']
    lines = []

    def add(text=''):
        lines.extend(textwrap.wrap(clean(text), width=width - 1,
                                   subsequent_indent='  ', break_long_words=True,
                                   break_on_hyphens=False) or [''])

    def brief(text):
        if details:
            add(text)
        else:
            lines.append(textwrap.shorten(clean(text), width=width - 1, placeholder='...'))

    def section(name):
        add('-- ' + name + ' --')

    section(f'ATW PROGRESS | {datetime.fromtimestamp(now).strftime("%H:%M:%S")}')
    add(f'{sum(t.get("status") == "implemented" for t in main)}/{len(main)} milestones done; '
        f'{len(main) - sum(t.get("status") == "implemented" for t in main)} remain')
    add(f'{len(leaves) - len(remaining)}/{len(leaves)} scoped work items done; {len(remaining)} remain')
    batches = state.get('batches', [])
    issues = [f for f in state.get('findings', []) if f.get('status') in {'open', 'blocked', 'fixed-awaiting-check'}
              and f.get('disposition') in {'actionable', 'blocked-owner/environment'}]
    gates = [g for g in state.get('externalReleaseGates', []) if g.get('status') != 'passed']
    brief(f'Reviews {sum(b.get("status") == "reviewed" for b in batches)}/{len(batches)}; '
          f'{len(issues)} defects; {len(gates)} release gates left')

    section('COMPLETED | last 1h / 2h / 4h / 8h')
    windows = []
    for hours in (1, 2, 4, 8):
        windows.append([e for e in events if now - hours * 3600 <= e['when'] <= now])
    for label, kind in (('Work items', 'items'), ('Review scopes', 'reviews'), ('Verified fixes', 'fixes')):
        add(label + ': ' + ' / '.join(str(sum(e['kind'] == kind for e in w)) for w in windows))
    if any(e['kind'] == 'reopened' for e in windows[-1]):
        add('Reopened: ' + ' / '.join(str(sum(e['kind'] == 'reopened' for e in w)) for w in windows))

    current = [r for r in records.values() if r['_status'] in LIVE]
    waiting = [r for r in records.values() if r['_status'] in WAITING]
    attention = [r for r in records.values() if r['_status'] in {'rejected', 'blocked', 'failed', 'needs-attention'}]
    section(f'CURRENT | {len(current)} lanes; {len(waiting)} awaiting integration')
    if orchestrator and (details or orchestrator.get('status') != 'running'):
        add('Supervisor: ' + orchestrator.get('status', 'unknown'))
    for record in sorted(current + waiting + attention, key=lambda r: r['_times'][0] or 0):
        choice = selection(record)
        target = choice.get('target')
        name = title(target, tasks) if target in TITLES or target in tasks or target in GROUPS else description(
            record.get('proposedOutcome') or 'Unspecified work')
        role = {'implement': 'Build', 'review': 'Review', 'fix': 'Fix', 'verify': 'Verify',
                'integrate': 'Integrate', 'integration': 'Integrate'}.get(choice.get('mode'), 'Work')
        start, end = record['_times']
        status = record['_status']
        if status in LIVE:
            brief(f'{elapsed(now - start if start is not None else None)} {role}: {name}')
            log = record.get('log')
            if details and log:
                try:
                    add(f'  Last log activity {elapsed(max(0, now - Path(log).stat().st_mtime))} ago')
                except OSError:
                    add('  Log activity unavailable')
        elif status in WAITING:
            brief(f'{elapsed(end - start if start is not None and end is not None else None)} await: {name}')
        else:
            brief(f'{status}: {name}; '
                  f'ran {elapsed(end - start if start is not None and end is not None else None)}')
            if not details:
                add(f'  Ran {elapsed(end - start if start is not None and end is not None else None)}')
        if details and record.get('sessionID'):
            add('  Session: ' + record['sessionID'])
        if details and record.get('proposedOutcome'):
            add('  Scope: ' + description(record['proposedOutcome']))
    if not current and not waiting and not attention:
        add('No active work recorded')
    if details:
        add('Lane status is journal metadata, not a live session-health check.')

    section('RECENT ACCEPTED RESULTS')
    recent_events = sorted((e for e in events if e['when'] <= now), key=lambda e: e['when'], reverse=True)[:recent]
    for event in recent_events:
        label = {'items': 'Delivered', 'reviews': 'Reviewed', 'fixes': 'Verified fix',
                 'reopened': 'Reopened'}[event['kind']]
        brief(f'{label}: {event.get("name") or title(event["target"], tasks)}')
        add(f'  {elapsed(now - event["when"])} ago; ran {event.get("runtime", runtime(event, records))}')
    if not recent_events:
        add('No accepted completions in available recent history')

    section('WHAT REMAINS')
    for batch in batches:
        outstanding = [t for t in remaining if t.get('batch') == batch['id']]
        if not outstanding and batch.get('status') == 'reviewed':
            continue
        brief(f'{len(outstanding)} left + '
              f'{"review done" if batch.get("status") == "reviewed" else "review needed"}: '
              f'{GROUPS.get(batch["id"], "Capability")}')
        # Main milestones explain scope without exposing meaningless numeric IDs.
        roots = [t for t in main if t.get('batch') == batch['id'] and t.get('status') != 'implemented']
        names = [title(t['id'], tasks) for t in roots]
        names += [title(t['id'], tasks) for t in outstanding
                  if t['id'] not in {r['id'] for r in roots} and not any(
                      t['id'].startswith(r['id']) for r in roots)]
        if names and details:
            add('  ' + '; '.join(names))
        if details:
            for task in outstanding:
                add(f'  {title(task["id"], tasks)}: {task.get("status", "unknown")}')
    blocked = [t for t in remaining if t.get('status') == 'blocked']
    for task in blocked:
        add(f'Blocked: {title(task["id"], tasks)} - {description(task.get("blocker", "reason unavailable"))}')
    for finding in issues:
        add(f'Defect ({finding["status"]}): {finding.get("title", "Unnamed defect")}')
    if gates and details:
        add('Release gates: ' + '; '.join(GATES.get(g['id'], g.get('title') or g['id'].replace('-', ' ')) for g in gates))
    add('Pushed scopes, not effort/ETA. Lane status: recorded.')
    if details:
        add('Saved workers are NOT completed work. Windows use first-parent state commit times.')
    else:
        add('--details shows full scopes, gates and log activity.')
    return lines


def dashboard(repo, now=None, width=78, recent=5, details=False):
    now = time.time() if now is None else now
    warnings = set()
    state, events, head, ref = pushed_history(repo, now)
    common = Path(git(repo, 'rev-parse', '--git-common-dir'))
    journal = (common if common.is_absolute() else repo / common) / 'implementation-loop'
    orchestrator, records = journal_records(journal, warnings)
    event_runtimes(repo, head, events, records, warnings)
    lines = render(state, events, records, orchestrator, now, width, recent, details)
    if (journal / 'STOP').exists():
        lines.insert(1, 'STOP REQUESTED: no new launches; active work may drain')
    if git(repo, 'rev-parse', 'HEAD') != head or git(repo, 'diff', 'HEAD', '--', STATE):
        warnings.add('Checkout differs from pushed state; unaccepted edits excluded from totals')
    if not journal.exists():
        warnings.add('Activity journal unavailable; running work may be missing')
    for warning in sorted(warnings):
        lines.extend(textwrap.wrap('Warning: ' + warning, width=width - 1))
    return lines


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--repo', type=Path, default=DEFAULT_REPO)
    parser.add_argument('--width', type=int, default=min(100, shutil.get_terminal_size((58, 35)).columns))
    parser.add_argument('--recent', type=int, default=5, help='number of recent accepted results')
    parser.add_argument('--details', action='store_true', help='include individual remaining scopes and session IDs')
    args = parser.parse_args()
    if args.width < 20 or args.recent < 0:
        parser.error('--width must be at least 20; --recent must be nonnegative')
    try:
        lines = dashboard(args.repo.resolve(), width=args.width, recent=args.recent, details=args.details)
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as error:
        lines = ['Progress temporarily unavailable; retry next refresh', clean(error)]
    for line in lines:
        for wrapped in textwrap.wrap(clean(line), width=args.width - 1) or ['']:
            print(wrapped)


if __name__ == '__main__':
    main()
