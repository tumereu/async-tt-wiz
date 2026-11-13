#!/usr/bin/env bash
# The close step's merge: under the machine-wide merge lock (~/projects/atw-cache/locks/master-merge.lock), brings master
# into set/<code> once more if it moved since the close checks (then reruns the parser and engine suites with the long
# classes, setSupport, the tools tests and the automatum compile; not parserDiff), and merges set/<code> into master in
# the main checkout. Sets closing in parallel take turns.
# Run from the set's worktree root. Exit 0 merged; 3 main checkout not ready; 4 conflict with master (resolve it by
# merging master yourself, rerun the close checks, then run this again); 5 checks failed after bringing master in.
#   merge-to-master.sh <code>
set -u
[ $# = 1 ] || { echo "Usage: merge-to-master.sh <code>" >&2; exit 2; }
code=$(echo "$1" | tr 'A-Z' 'a-z'); branch=set/$code
T=$(cd "$(dirname "$0")" && pwd)
[ "$(git rev-parse --abbrev-ref HEAD)" = "$branch" ] || { echo "Run it in the worktree of $branch" >&2; exit 2; }
[ -z "$(git status --porcelain --untracked-files=no)" ] || { echo "Commit first: the worktree has changes" >&2; exit 2; }
main=$(git worktree list --porcelain | sed -n '1s/^worktree //p')
# The app's Arcanum integration switches exhaustively on engine types (statuses and other sealed members): a new member
# breaks the app build, which no set test compiles. Checked before the lock and again after bringing master in.
automatum() {
  "$T/gradle-slot.sh" :automatum:compileKotlinJvm && return
  echo "automatum doesn't compile: a new engine type or sealed member it must handle? Fix it in automatum on this branch" >&2
  exit 5
}
automatum
L=$HOME/projects/atw-cache/locks; mkdir -p "$L"
exec 7>"$L/master-merge.lock"
echo "Waiting for the merge lock..."; flock 7
# Others (the main session's own work) may commit to master without the lock, so check again right before merging.
for i in $(seq 1 30); do
  if ! git merge-base --is-ancestor master HEAD; then
    echo "master moved since the close checks: merging it in"
    if ! git merge --no-edit master; then git merge --abort; echo "Conflict with master" >&2; exit 4; fi
    # The tests only: parserDiff's outside changes were judged in the close step, and against the set's base snapshot they
    # would now also list what other sets merged meanwhile, so a set with an accepted cross-set rules fix could never merge.
    "$T/gradle-slot.sh" :arcanum-parser:jvmTest :arcanum-engine:jvmTest :arcanum-tools:setSupport :arcanum-tools:test --continue || exit 5
    automatum
  fi
  if [ "$(git -C "$main" rev-parse --abbrev-ref HEAD)" = master ] && [ ! -e "$(git -C "$main" rev-parse --git-path MERGE_HEAD)" ] &&
     git merge-base --is-ancestor master HEAD; then
    if git -C "$main" merge --no-ff --no-edit "$branch" -m "Merge branch '$branch'"; then
      echo "Merged $branch into master: $(git -C "$main" rev-parse --short HEAD)"; exit 0
    fi
    [ -e "$(git -C "$main" rev-parse --git-path MERGE_HEAD)" ] && git -C "$main" merge --abort
  fi
  echo "Main checkout $main not ready (not on master, a merge in progress, master moved, or local changes in the way); retrying in a minute" >&2
  sleep 60
done
echo "Main checkout still not ready after 30 minutes" >&2; exit 3
