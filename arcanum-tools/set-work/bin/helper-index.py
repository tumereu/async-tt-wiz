#!/usr/bin/env python3
"""Writes <plan>/helpers.md: one line per test helper (signature only) from the parser's shared card DSL, the newest set's helper
file and the engine's Scenario API, so set workers don't grep for helper names. The newest set's helper file is the set helper
file (arcanum-parser/src/commonTest/kotlin/arcanum/parser/sets/<code>/<Code>Cards.kt) most recently changed in the worktree's git
history, or the one of <set code> when given. Usage: helper-index.py <worktree> <plan> [<set code>]."""
import glob, os, re, subprocess, sys
if len(sys.argv) not in (3, 4): sys.exit('Usage: helper-index.py <worktree> <plan dir> [<set code>]')
wt, plan = sys.argv[1], sys.argv[2]
sets_dir = 'arcanum-parser/src/commonTest/kotlin/arcanum/parser/sets'
if len(sys.argv) == 4:
    code = sys.argv[3].lower()
    helper = f'{sets_dir}/{code}/{code.capitalize()}Cards.kt'
    if not os.path.isfile(os.path.join(wt, helper)): sys.exit(f'helper-index.py: set {code} has no helper file {helper} in {wt}')
else:
    def changed(rel):
        out = subprocess.run(['git', '-C', wt, 'log', '-1', '--format=%ct', '--', rel], capture_output=True, text=True, check=True).stdout.strip()
        return int(out) if out else 0
    candidates = [os.path.relpath(p, wt) for p in glob.glob(os.path.join(wt, sets_dir, '*', '*Cards.kt'))]
    dated = sorted(((changed(rel), rel) for rel in candidates), reverse=True)
    if not dated or dated[0][0] == 0: sys.exit(f'helper-index.py: no set helper file {sets_dir}/*/*Cards.kt committed in {wt}')
    helper = dated[0][1]
files = [('Parser card DSL (import arcanum.parser.cube.*)', 'arcanum-parser/src/commonTest/kotlin/arcanum/parser/cube/CubeCard.kt'),
         ('Newest set helpers (the set\'s first chunk copies this file to <Code>Cards.kt in its set package; later chunks add to it)', helper),
         ('Engine scenarios', 'arcanum-engine/src/commonTest/kotlin/arcanum/engine/scenario/Scenario.kt')]
out = ['# Test helpers: signatures only (open the file at the line for the body)', '']
for title, rel in files:
    p = os.path.join(wt, rel)
    if not os.path.exists(p): continue
    out += [f'## {title}: {rel}']
    for n, l in enumerate(open(p, encoding='utf8'), 1):
        s = l.rstrip()
        if (re.match(r'(internal )?(infix )?(fun|val|var|class|object|data class) ', s) or re.match(r'    (infix )?fun ', s)) and 'private' not in s:
            sig = re.split(r'\s=\s|\s\{\s*$', s.strip())[0]
            out.append(f'{n}: {sig[:170]}')
    out.append('')
open(os.path.join(plan, 'helpers.md'), 'w').write('\n'.join(out) + '\n')
print(f'{len(out)} lines; newest set helpers: {helper}')
