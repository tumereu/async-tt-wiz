#!/usr/bin/env python3
"""Appends 'Nearest tests' to each card of a set plan's chunk files: for every ability line, the existing parser test and engine
scenario whose oracle-text string is most alike (word overlap), as file:line, so a chunk worker copies a close example
instead of searching. Usage: chunk-pointers.py <worktree> <plan dir>. Idempotent (replaces earlier 'Nearest tests' blocks)."""
import glob, os, re, sys
if len(sys.argv) != 3: sys.exit('Usage: chunk-pointers.py <worktree> <plan dir>')

wt, plan = sys.argv[1], sys.argv[2]
roots = {'parser': wt + '/arcanum-parser/src/commonTest/kotlin', 'engine': wt + '/arcanum-engine/src/commonTest/kotlin'}
WORD = re.compile(r"[a-z+\-/0-9{}]+")

def words(s):
    s = re.sub(r'\([^)]*\)', '', s.lower())  # reminder text
    return set(WORD.findall(s)) - {'a', 'an', 'the', 'of', 'to', 'and', 'this', 'that', 'it', 'its', 'you'}

index = {k: [] for k in roots}
for kind, root in roots.items():
    for f in glob.glob(root + '/**/*.kt', recursive=True):
        rel = os.path.relpath(f, wt)
        for n, line in enumerate(open(f, encoding='utf8'), 1):
            t = line.strip().strip('",').strip()
            if t.startswith('text = "'): t = t[8:]
            if len(t) < 25 or not re.search(r'[A-Z][a-z]+ .*(target|creature|card|you|player|damage|counter|spell|enters|dies|\{)', t): continue
            w = words(t)
            if len(w) >= 4: index[kind].append((w, rel, n, t[:110]))

def nearest(kind, text):
    w = words(text)
    if len(w) < 2: return None
    best = max(index[kind], key=lambda e: len(w & e[0]) / len(w | e[0]), default=None)
    if not best: return None
    score = len(w & best[0]) / len(w | best[0])
    return (score, best) if score >= 0.35 else None

for f in sorted(glob.glob(plan + '/chunks/*.md')):
    src = open(f, encoding='utf8').read()
    src = re.sub(r'\nNearest tests:\n(?:  .*\n)*', '\n', src)
    out = []
    for block in re.split(r'(?m)^(?=## )', src):
        if not block.startswith('## '): out.append(block); continue
        lines = block.split('\n')
        body = []
        for l in lines[2:]:
            if l.startswith(('Verdict:', 'Explain:', 'Audit')): break
            if l and l != '//' and not re.match(r'^(\{|Legendary|Creature|Artifact|Enchantment|Land|Instant|Sorcery|Planeswalker|Battle)', l): body.append(l)
        found, seen = [], set()
        for l in body:
            for kind in ('parser', 'engine'):
                r = nearest(kind, l)
                if r and (r[1][1], r[1][2]) not in seen:
                    seen.add((r[1][1], r[1][2])); found.append(f'  {kind} {r[1][1]}:{r[1][2]}  {r[1][3]}')
        if found:
            # put the block before the card's next section (keep a trailing blank line)
            text = block.rstrip('\n')
            block = text + '\nNearest tests:\n' + '\n'.join(found[:6]) + '\n\n'
        out.append(block)
    open(f, 'w', encoding='utf8').write(''.join(out))
    print(os.path.basename(f), 'done')
