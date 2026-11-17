#!/usr/bin/env bash
# Merges set/<code> into master through the merge gate (scripts/merge-gate.sh, the only way master moves): the gate merges
# master and the branch in its verify worktree, runs the app's checks there (the JVM suites of the modules the merge
# changes and their dependents, :androidApp:testDebugUnitTest and the on-device tests' compile, the web build, the e2e
# smoke set) plus this set's own checks below, and moves master to that tested merge only if all pass. One gate runs at a
# time (the merge lock ~/projects/atw-cache/locks/master-merge.lock), so sets merging in parallel take turns.
#   merge-to-master.sh <code>           the close step: before the gate, the automatum check below on the branch (automatum
#                                       tests, parser/engine Wasm compile and tests, about 10 minutes); in the gate on the
#                                       merge, the parser and engine suites with the long classes, setSupport, the tools
#                                       tests and the automatum check again
#   merge-to-master.sh <code> --chunk   an accepted green chunk mid-set (set-tests.sh --full ran the automatum and Wasm
#                                       compiles already): in the gate, the parser and engine Wasm compiles
# After merging, set/<code> is fast-forwarded to the merge (its tree is master's) and the base snapshot is retaken from
# it, so the next chunk's comparison shows only its changes.
# Run from the set's worktree root. Exit 0 merged; 2 usage or setup; 3 master or its checkout not ready (retry in a few
# minutes); 4 conflict with master (resolve it through sync-master.sh, rerun set-tests.sh --full, or the close checks in
# full mode, then run this again); 5 checks failed on the merge (fix them on the branch; the gate's log names them);
# 1 merged, but the snapshot failed (sync-master.sh retakes it).
set -u
mode=full
case "$#:${2:-}" in 1:) ;; 2:--chunk) mode=chunk ;; *) echo "Usage: merge-to-master.sh <code> [--chunk]" >&2; exit 2 ;; esac
code=$(echo "$1" | tr 'A-Z' 'a-z'); CODE=$(echo "$1" | tr 'a-z' 'A-Z'); branch=set/$code
T=$(cd "$(dirname "$0")" && pwd)
[ "$(git rev-parse --abbrev-ref HEAD)" = "$branch" ] || { echo "Run it in the worktree of $branch" >&2; exit 2; }
[ -z "$(git status --porcelain --untracked-files=no)" ] || { echo "Commit first: the worktree has changes" >&2; exit 2; }
# The app's Arcanum integration switches exhaustively on engine types (statuses and other sealed members): a new member
# breaks the app build. Its tests also hold the architecture checks over the shared code (no Java 21 list calls, no
# runtime class names outside logs and texts), and the parser and engine must compile and pass their tests on Wasm (the
# web client), where type inference, library calls and collections can differ from the JVM and every test class shares
# one process (about 10 minutes).
automatum=":automatum:jvmTest :arcanum-parser:compileKotlinWasmJs :arcanum-engine:compileKotlinWasmJs :arcanum-parser:wasmJsNodeTest :arcanum-engine:wasmJsNodeTest"
if [ $mode = full ]; then
  if ! "$T/gradle-slot.sh" $automatum; then
    echo "automatum tests (architecture checks included) or the parser/engine Wasm compile or tests failed: a new engine type or sealed member automatum must handle, a check over shared code, or a Wasm-only failure (types, JVM-only calls, invented test cards defined twice differently)? Fix it on this branch" >&2
    exit 5
  fi
  extras=(--extra ":arcanum-parser:jvmTest :arcanum-engine:jvmTest :arcanum-tools:setSupport :arcanum-tools:test --continue"
          --extra "$automatum")
else
  extras=(--extra ":arcanum-parser:compileKotlinWasmJs :arcanum-engine:compileKotlinWasmJs")
fi
root=$(git rev-parse --show-toplevel)
"$root/scripts/merge-gate.sh" "$branch" "${extras[@]}"
rc=$?
case $rc in
  0) ;;
  4) echo "Conflict with master" >&2; exit 4 ;;
  5) echo "Checks failed on the merge with master: the gate's log above names them; fix them on $branch" >&2; exit 5 ;;
  *) exit $rc ;;
esac
merged=$(git rev-parse master)
git merge-base --is-ancestor HEAD "$merged" || { echo "$branch is not in master after the gate" >&2; exit 3; }
git merge -q --ff-only "$merged" || exit 1
echo "Merged $branch into master: $(git rev-parse --short "$merged")"
# The merged commit's tree is this branch's: its snapshot is the base the next chunk compares against (master may move on
# meanwhile; sync-master.sh brings that in and retakes the snapshot).
"$T/base-snapshot.sh" "$CODE" --from "$merged" || exit 1
