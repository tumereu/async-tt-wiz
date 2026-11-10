#!/usr/bin/env bash
# Runs a set's parser and engine tests in one Gradle call (through gradle-slot.sh) and prints only counts and failures.
#   set-tests.sh <code> [extra --tests patterns...]   e.g. set-tests.sh inr '*MechanicWordingsTest*'
#   set-tests.sh <code> --full                        the whole parser and engine suites (invented card names are global) and parserDiff
# Run from the worktree root. The base snapshot is ~/projects/atw-cache/set-plans/<CODE>/parser-base.snapshot (base-snapshot.sh
# writes it when the set starts); a --full run's parserDiff output goes to <plan>/full-run/ (replaced each run).
set -u
[ $# -ge 1 ] || { echo "Usage: set-tests.sh <code> [--full] ['<test pattern>' ...]" >&2; exit 2; }
code=$(echo "$1" | tr 'A-Z' 'a-z'); CODE=$(echo "$1" | tr 'a-z' 'A-Z'); shift
full=0; extra=()
for a in "$@"; do if [ "$a" = --full ]; then full=1; else extra+=(--tests "$a"); fi; done
T=$(cd "$(dirname "$0")" && pwd); plan=$HOME/projects/atw-cache/set-plans/$CODE
log=$(mktemp); trap 'rm -f "$log"' EXIT
out=$plan/full-run
if [ $full = 1 ]; then
  [ -f "$plan/parser-base.snapshot" ] || { echo "No $plan/parser-base.snapshot: take it first with $T/base-snapshot.sh $CODE" >&2; exit 2; }
  rm -rf "$out"; mkdir -p "$out"
fi
rm -rf arcanum-parser/build/test-results/jvmTest arcanum-engine/build/test-results/jvmTest
# a --tests filter that matches nothing fails Gradle, so a set's filter is added only once its test folder exists
pt=(); et=()
[ -d "arcanum-parser/src/commonTest/kotlin/arcanum/parser/sets/$code" ] && pt=(--tests "arcanum.parser.sets.$code.*")
[ -d "arcanum-engine/src/commonTest/kotlin/arcanum/engine/sets/$code" ] && et=(:arcanum-engine:jvmTest --tests "arcanum.engine.sets.$code.*" "${extra[@]}")
if [ $full = 1 ]; then
  cp "$plan/parser-base.snapshot" "$out/base.snapshot"
  "$T/gradle-slot.sh" :arcanum-parser:jvmTest :arcanum-engine:jvmTest "${extra[@]}" \
    :arcanum-tools:parserDiff --args="compare $out/base.snapshot" -PparserOutputs="$out" --continue >"$log" 2>&1
else
  "$T/gradle-slot.sh" :arcanum-parser:jvmTest "${pt[@]}" "${extra[@]}" "${et[@]}" --continue >"$log" 2>&1
fi
rc=$?
# Kotlin's incremental caches sometimes fail with "FqNames can't be derived": rerun once without incremental compilation
if grep -q "FqNames can't be derived\|Could not perform incremental compilation" "$log"; then
  echo "(incremental compile glitch: rerunning once with kotlin.incremental=false)"
  if [ $full = 1 ]; then
    "$T/gradle-slot.sh" -Pkotlin.incremental=false -Pkotlin.incremental.multiplatform=false :arcanum-parser:jvmTest :arcanum-engine:jvmTest "${extra[@]}" \
      :arcanum-tools:parserDiff --args="compare $out/base.snapshot" -PparserOutputs="$out" --continue >"$log" 2>&1
  else
    "$T/gradle-slot.sh" -Pkotlin.incremental=false -Pkotlin.incremental.multiplatform=false :arcanum-parser:jvmTest "${pt[@]}" "${extra[@]}" "${et[@]}" --continue >"$log" 2>&1
  fi
  rc=$?
fi
grep -E "^e: |error:|Compilation error|BUILD (SUCCESSFUL|FAILED)|No tests found|What went wrong" "$log" | head -20
python3 -I - "$CODE" "$out" "$full" <<'EOF'
import glob,sys,re,os,gzip,json
import xml.etree.ElementTree as ET
code,out,full=sys.argv[1],sys.argv[2],sys.argv[3]=='1'
for mod in ('arcanum-parser','arcanum-engine'):
    n=f=0;fails=[]
    for x in glob.glob(f'{mod}/build/test-results/jvmTest/*.xml'):
        r=ET.parse(x).getroot()
        for tc in r.iter('testcase'):
            n+=1
            e=tc.find('failure') if tc.find('failure') is not None else tc.find('error')
            if e is not None:
                f+=1; msg=(e.get('message') or e.text or '').strip().splitlines()
                fails.append(f"  {tc.get('classname').split('.')[-1]}.{tc.get('name')}: {' | '.join(msg[:3])[:400]}")
    print(f'{mod}: {n} tests, {f} failed')
    if fails: print('\n'.join(fails[:25]))
if full:
    p=os.path.join(out,'parser-diff.txt')
    if not os.path.exists(p): print('parserDiff: no output (see Gradle errors above)'); sys.exit()
    secs={};cur=None
    for l in open(p):
        if l.strip() and not l.startswith(' '): cur=l.strip(); secs[cur]=[]
        elif re.match(r'  \S',l) and cur: secs[cur].append(re.sub(r' (at \d+|\[held-out\])$','',l.strip()))
    d=json.load(gzip.open(os.path.expanduser('~/projects/atw-cache/AllPrintings.json.gz')))['data']
    mine={c['name'] for c in d.get(code,{}).get('cards',[])}
    for s,names in secs.items():
        if s.startswith('Supported sets'):
            other=[x for x in names if x not in mine]
            print(f'parserDiff supported cards changed outside {code}: {len(other)}', ', '.join(other[:30]))
        elif s.startswith('Cube') or s.startswith('Any card, stopped'):
            print(f'parserDiff {s.split(":")[0]}: {len(names)}', ', '.join(names[:30]))
        elif s.startswith('Held out, read differently'):
            print(f'parserDiff held-out read differently: {len(names)} (review these):', ', '.join(names[:40]))
    print('full diff:',p)
EOF
exit $rc
