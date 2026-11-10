#!/usr/bin/env python3
"""Writes a set plan's audit findings into its chunks: every incorrect or unsure call in <plan>/audit/results-*.tsv on an
entry that isn't seeded (audit/seeded.tsv) becomes an 'Audit (incorrect|unsure): <reason>' line in its card's entry in
chunks/NN.md. Flagged cards in no chunk (REPEAT cards) become TEST in plan.tsv and go to a new chunk, numbered one past the
highest existing one. Idempotent: a line already in the card's entry isn't added again, and a second run adds no chunk.
Usage: audit-apply.py <plan dir>"""
import glob, os, re, sys

if len(sys.argv) != 2: sys.exit('Usage: audit-apply.py <plan dir>')
P = sys.argv[1]; A = os.path.join(P, 'audit')
if not os.path.isfile(os.path.join(A, 'seeded.tsv')): sys.exit(f'No {A}/seeded.tsv: is this a plan folder?')
seeded = {l.split('\t')[0] for l in open(os.path.join(A, 'seeded.tsv'), encoding='utf8').read().splitlines() if l and not l.startswith('#')}
entries = {}
for b in glob.glob(os.path.join(A, 'batch-*.md')):
    for m in re.finditer(r'^## (E\d+) (.+?)\n(.*?)(?=^## E|\Z)', open(b, encoding='utf8').read(), re.M | re.S):
        entries[m.group(1)] = (m.group(2), m.group(3))
found = {}
for f in sorted(glob.glob(os.path.join(A, 'results-*.tsv'))):
    for l in open(f, encoding='utf8').read().splitlines():
        p = l.split('\t')
        if len(p) < 2 or p[0] in seeded or p[0] not in entries or p[1].strip() not in ('incorrect', 'unsure'): continue
        found[entries[p[0]][0]] = (p[1].strip(), (p[2] if len(p) > 2 else '').strip(), p[0])
chunk_files = sorted(glob.glob(os.path.join(P, 'chunks', '*.md')))
chunks = {}
for f in chunk_files:
    for c in re.findall(r'^## (.+)$', open(f, encoding='utf8').read(), re.M): chunks.setdefault(c, f)
added = kept = 0
new = []
for c, (v, r, e) in sorted(found.items()):
    line = f'Audit ({v}): {r}'
    if c not in chunks:
        new.append((c, v, r, e)); continue
    t = open(chunks[c], encoding='utf8').read()
    m = re.search(r'^## ' + re.escape(c) + r'\n.*?(?=\n\n|\n?\Z)', t, re.M | re.S)
    if line in m.group(0).split('\n'):
        kept += 1; continue
    t = t[:m.end()] + '\n' + line + t[m.end():]
    open(chunks[c], 'w', encoding='utf8').write(t)
    added += 1
    print('chunk', os.path.basename(chunks[c]), c, v)
if new:
    numbers = [int(m.group(1)) for f in chunk_files if (m := re.match(r'(\d+)', os.path.basename(f)))]
    n = max(numbers, default=0) + 1
    out = [f'# Chunk {n}: audit findings on REPEAT cards', f'{len(new)} cards, 0 unread; each read but doubted by the audit', '']
    plan = open(os.path.join(P, 'plan.tsv'), encoding='utf8').read().splitlines()
    names = {c for c, _, _, _ in new}
    for i, l in enumerate(plan):
        p = l.split('\t')
        if len(p) > 2 and p[0] in names and p[1] == 'REPEAT': p[1] = 'TEST'; p[2] = 'audit'; plan[i] = '\t'.join(p)
    open(os.path.join(P, 'plan.tsv'), 'w', encoding='utf8').write('\n'.join(plan) + '\n')
    for c, v, r, e in new:
        body = entries[e][1].split('Model:')[0].replace('Oracle:\n', '').strip()
        out += [f'## {c}', body, 'Verdict: TEST: audit', f'Audit ({v}): {r}', '']
        print(f'chunk {n:02d}.md (new)', c, v)
    open(os.path.join(P, 'chunks', f'{n:02d}.md'), 'w', encoding='utf8').write('\n'.join(out))
print(f'{len(found)} findings: {added} lines added, {kept} already there, {len(new)} REPEAT cards to a new chunk')
