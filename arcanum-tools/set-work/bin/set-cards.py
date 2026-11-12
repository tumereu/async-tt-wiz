#!/usr/bin/env python3
"""Prints a set's cards (every face: name, mana cost, type line, oracle text) from <plan>/cards.json, the set's extract of the
pinned AllPrintings. The extract is written on first use: loading AllPrintings takes about 4.6 GB outside the build's memory
cap, so it happens once per set and at most once at a time on this machine (lock ~/projects/atw-cache/locks/allprintings.lock).
Never load AllPrintings.json.gz yourself; use this.
Usage: set-cards.py <CODE> [card name ...]   (no names: every card of the set; --names: only the card names, one per line)"""
import fcntl, gzip, json, os, sys

if len(sys.argv) < 2: sys.exit(__doc__)
code = sys.argv[1].upper()
args = sys.argv[2:]
cache = os.path.expanduser('~/projects/atw-cache')
plan = os.path.join(cache, 'set-plans', code)
extract = os.path.join(plan, 'cards.json')


def write_extract():
    os.makedirs(os.path.join(cache, 'locks'), exist_ok=True)
    with open(os.path.join(cache, 'locks', 'allprintings.lock'), 'w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if os.path.exists(extract): return  # another process wrote it while this one waited
        mtgjson = os.path.join(cache, 'AllPrintings.json.gz')
        if not os.path.isfile(mtgjson): sys.exit(f'No {mtgjson}: run arcanum-tools/set-work/bin/setup.sh first')
        with gzip.open(mtgjson, 'rt', encoding='utf8') as f: s = json.load(f)['data'].get(code)
        if not s: sys.exit(f'No set {code} in {mtgjson}')
        cards = {}
        for c in s.get('cards', []):
            face = {k: c.get(k) for k in ('faceName', 'manaCost', 'type', 'text', 'power', 'toughness', 'loyalty', 'defense', 'side')
                    if c.get(k) is not None}
            entry = cards.setdefault(c['name'], {'name': c['name'], 'layout': c.get('layout'), 'faces': []})
            if face not in entry['faces']: entry['faces'].append(face)
        os.makedirs(plan, exist_ok=True)
        tmp = extract + '.tmp'
        with open(tmp, 'w', encoding='utf8') as f: json.dump(list(cards.values()), f, ensure_ascii=False, indent=0)
        os.replace(tmp, extract)


if not os.path.exists(extract): write_extract()
cards = json.load(open(extract, encoding='utf8'))
if args == ['--names']:
    for c in cards: print(c['name'])
    sys.exit()
wanted = {a.lower() for a in args}
shown = 0
for c in cards:
    if wanted and c['name'].lower() not in wanted and not any(f.get('faceName', '').lower() in wanted for f in c['faces']): continue
    shown += 1
    print(f"== {c['name']} ({c['layout']})")
    for f in c['faces']:
        stats = '/'.join(str(f[k]) for k in ('power', 'toughness') if k in f) or f.get('loyalty') or f.get('defense') or ''
        print(f"  {f.get('faceName', c['name'])} {f.get('manaCost', '')} | {f.get('type', '')} {stats}".rstrip())
        for line in (f.get('text') or '').splitlines(): print('    ' + line)
if wanted and shown < len(wanted): print(f'({len(wanted) - shown} of the names not found in {code})', file=sys.stderr)
