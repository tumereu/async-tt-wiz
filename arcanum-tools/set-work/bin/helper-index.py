#!/usr/bin/env python3
"""Writes <plan>/helpers.md: one line per test helper (signature only) from the parser's shared card DSL, the newest set's helper
file and the engine's Scenario API, so set workers don't grep for helper names. Usage: helper-index.py <worktree> <plan> <newest set code>."""
import re,sys,os
if len(sys.argv) != 4: sys.exit('Usage: helper-index.py <worktree> <plan dir> <newest supported set code>')
wt,plan,newest=sys.argv[1],sys.argv[2],sys.argv[3].lower()
files=[('Parser card DSL (import arcanum.parser.cube.*)','arcanum-parser/src/commonTest/kotlin/arcanum/parser/cube/CubeCard.kt'),
 (f'Newest set helpers (copy to <Code>Cards.kt in your set package, then add yours)',f'arcanum-parser/src/commonTest/kotlin/arcanum/parser/sets/{newest}/{newest.capitalize()}Cards.kt'),
 ('Engine scenarios','arcanum-engine/src/commonTest/kotlin/arcanum/engine/scenario/Scenario.kt')]
out=['# Test helpers: signatures only (open the file at the line for the body)','']
for title,rel in files:
    p=os.path.join(wt,rel)
    if not os.path.exists(p): continue
    out+=[f'## {title}: {rel}']
    for n,l in enumerate(open(p,encoding='utf8'),1):
        s=l.rstrip()
        if (re.match(r'(internal )?(infix )?(fun|val|var|class|object|data class) ',s) or re.match(r'    (infix )?fun ',s)) and 'private' not in s:
            sig=re.split(r'\s=\s|\s\{\s*$',s.strip())[0]
            out.append(f'{n}: {sig[:170]}')
    out.append('')
open(os.path.join(plan,'helpers.md'),'w').write('\n'.join(out)+'\n')
print(len(out),'lines')
