#!/usr/bin/env python3
"""Scores a set plan's audit against its seeded entries (<plan>/audit/seeded.tsv, the deliberately wrong copies).
Each calibration file (audit/calibration-*.tsv, e.g. calibration-01-haiku.tsv) is scored on its own, then all
results-*.tsv together. Per scoring: the share of seeded entries called incorrect (calibration: keep the cheaper model
at 90% or more), the seeded entries it missed, and the other incorrect calls, each to check against the oracle text and
the CR (calibration: at most 10% of them may be false alarms), plus the other unsure calls.
Only seeded entries of the batches a file answers count. Usage: audit-score.py <plan dir>"""
import glob, os, re, sys

if len(sys.argv) != 2: sys.exit('Usage: audit-score.py <plan dir>')
A = os.path.join(sys.argv[1], 'audit')
if not os.path.isfile(os.path.join(A, 'seeded.tsv')): sys.exit(f'No {A}/seeded.tsv: is this a plan folder?')
seeded = {}
for l in open(os.path.join(A, 'seeded.tsv'), encoding='utf8').read().splitlines():
    p = l.split('\t')
    if len(p) >= 3 and not l.startswith('#'): seeded[p[0]] = (p[1], p[2])
names, batch_of = {}, {}
for b in sorted(glob.glob(os.path.join(A, 'batch-*.md'))):
    for e, c in re.findall(r'^## (E\d+) (.+)$', open(b, encoding='utf8').read(), re.M):
        names[e] = c; batch_of[e] = os.path.basename(b)

def read(files):
    res = {}
    for f in files:
        for l in open(f, encoding='utf8').read().splitlines():
            p = l.split('\t')
            if len(p) >= 2 and p[0].startswith('E'): res[p[0]] = (p[1].strip(), p[2] if len(p) > 2 else '')
    return res

def score(title, files):
    res = read(files)
    batches = {batch_of.get(e) for e in res}
    scope = [e for e in seeded if batch_of.get(e) in batches]
    entries = [e for e in names if batch_of[e] in batches]
    print(f'=== {title}: {len(res)} answers for {len(entries)} entries in {len(batches)} batches')
    caught = [e for e in scope if res.get(e, ('',))[0] == 'incorrect']
    unsure = [e for e in scope if res.get(e, ('',))[0] == 'unsure']
    missed = [e for e in scope if e not in caught and e not in unsure]
    share = 100 * len(caught) / len(scope) if scope else 0
    other = sorted((e, r) for e, (v, r) in res.items() if e not in seeded and v == 'incorrect')
    other_unsure = sorted((e, r) for e, (v, r) in res.items() if e not in seeded and v == 'unsure')
    print(f'seeded entries called incorrect: {len(caught)} of {len(scope)} ({share:.0f}%); unsure {len(unsure)}, called correct or unanswered {len(missed)}')
    print(f'other incorrect calls to check against the oracle text and the CR: {len(other)}')
    for e in missed + unsure: print('  missed seeded', e, names.get(e), '|', seeded[e][1], '|', res.get(e, ('unanswered', ''))[0])
    for e, r in other: print('  incorrect', e, names.get(e), '|', r[:230])
    for e, r in other_unsure: print('  unsure', e, names.get(e), '|', r[:200])

cal = sorted(glob.glob(os.path.join(A, 'calibration-*.tsv')))
for f in cal: score(os.path.basename(f), [f])
results = sorted(glob.glob(os.path.join(A, 'results-*.tsv')))
if results: score('results-*.tsv', results)
if not cal and not results: print(f'No calibration-*.tsv or results-*.tsv in {A} yet')
