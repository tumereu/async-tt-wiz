#!/usr/bin/env bash
# Merges set/<code> into master in the main checkout, under the machine-wide merge lock
# (~/projects/atw-cache/locks/master-merge.lock), so sets merging in parallel take turns. If master moved since the
# branch's last checks, master is brought into set/<code> first and the checks rerun (not parserDiff: outside changes were
# judged before, and against the set's base snapshot they would now also list what other sets merged meanwhile).
#   merge-to-master.sh <code>           the close step: before the lock, and again after bringing master in, the automatum
#                                       check below (automatum tests, parser/engine Wasm compile and tests, about 10 minutes);
#                                       after bringing master in also the parser and engine suites with the long classes,
#                                       setSupport and the tools tests
#   merge-to-master.sh <code> --chunk   an accepted green chunk mid-set (set-tests.sh --full ran the automatum and Wasm
#                                       compiles already): after bringing master in, one lighter check (parser and engine
#                                       suites without the long classes, automatum and Wasm compiles)
# After merging, the base snapshot is retaken from the merged commit, so the next chunk's comparison shows only its changes.
# Run from the set's worktree root. Exit 0 merged; 3 main checkout not ready; 4 conflict with master (resolve it through
# sync-master.sh, rerun set-tests.sh --full, or the close checks in full mode, then run this again); 5 checks failed after
# bringing master in (fix them on the branch); 1 merged, but the snapshot failed (sync-master.sh retakes it).
set -u
mode=full
case "$#:${2:-}" in 1:) ;; 2:--chunk) mode=chunk ;; *) echo "Usage: merge-to-master.sh <code> [--chunk]" >&2; exit 2 ;; esac
code=$(echo "$1" | tr 'A-Z' 'a-z'); CODE=$(echo "$1" | tr 'a-z' 'A-Z'); branch=set/$code
T=$(cd "$(dirname "$0")" && pwd)
[ "$(git rev-parse --abbrev-ref HEAD)" = "$branch" ] || { echo "Run it in the worktree of $branch" >&2; exit 2; }
[ -z "$(git status --porcelain --untracked-files=no)" ] || { echo "Commit first: the worktree has changes" >&2; exit 2; }
main=$(git worktree list --porcelain | sed -n '1s/^worktree //p')
# The app's Arcanum integration switches exhaustively on engine types (statuses and other sealed members): a new member
# breaks the app build, which no set test compiles. Its tests also hold the architecture checks over the shared code
# (no Java 21 list calls, no runtime class names outside logs and texts), and the parser and engine must compile and
# pass their tests on Wasm (the web client), where type inference, library calls and collections can differ from the
# JVM and every test class shares one process (about 10 minutes).
automatum() {
  "$T/gradle-slot.sh" :automatum:jvmTest :arcanum-parser:compileKotlinWasmJs :arcanum-engine:compileKotlinWasmJs \
    :arcanum-parser:wasmJsNodeTest :arcanum-engine:wasmJsNodeTest && return
  echo "automatum tests (architecture checks included) or the parser/engine Wasm compile or tests failed: a new engine type or sealed member automatum must handle, a check over shared code, or a Wasm-only failure (types, JVM-only calls, invented test cards defined twice differently)? Fix it on this branch" >&2
  exit 5
}
checks() {
  if [ $mode = chunk ]; then
    "$T/gradle-slot.sh" -Parcanum.skipLongTests=true :arcanum-parser:jvmTest :arcanum-engine:jvmTest :automatum:compileKotlinJvm \
      :arcanum-parser:compileKotlinWasmJs :arcanum-engine:compileKotlinWasmJs --continue || exit 5
  else
    "$T/gradle-slot.sh" :arcanum-parser:jvmTest :arcanum-engine:jvmTest :arcanum-tools:setSupport :arcanum-tools:test --continue || exit 5
    automatum
  fi
}
[ $mode = full ] && automatum
L=$HOME/projects/atw-cache/locks; mkdir -p "$L"
exec 7>"$L/master-merge.lock"
echo "Waiting for the merge lock..."; flock 7
# Others (the main session's own work) may commit to master without the lock, so check again right before merging.
for i in $(seq 1 30); do
  if ! git merge-base --is-ancestor master HEAD; then
    echo "master moved since the last checks: merging it in"
    if ! git merge --no-edit master; then git merge --abort; echo "Conflict with master" >&2; exit 4; fi
    checks
  fi
  if [ "$(git -C "$main" rev-parse --abbrev-ref HEAD)" = master ] && [ ! -e "$(git -C "$main" rev-parse --git-path MERGE_HEAD)" ] &&
     git merge-base --is-ancestor master HEAD; then
    if git -C "$main" merge --no-ff --no-edit "$branch" -m "Merge branch '$branch'"; then
      merged=$(git -C "$main" rev-parse HEAD)
      echo "Merged $branch into master: $(git rev-parse --short "$merged")"
      flock -u 7
      # The merged commit's tree is this branch's: its snapshot is the base the next chunk compares against (master may
      # move on meanwhile; sync-master.sh brings that in and retakes the snapshot).
      "$T/base-snapshot.sh" "$CODE" --from "$merged" || exit 1
      exit 0
    fi
    [ -e "$(git -C "$main" rev-parse --git-path MERGE_HEAD)" ] && git -C "$main" merge --abort
  fi
  echo "Main checkout $main not ready (not on master, a merge in progress, master moved, or local changes in the way); retrying in a minute" >&2
  sleep 60
done
echo "Main checkout still not ready after 30 minutes" >&2; exit 3
