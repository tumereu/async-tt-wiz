#!/usr/bin/env bash
# Brings master into set/<code> before a chunk, so general fixes other sets merged reach this set at once: merges master
# if it moved since the branch last took it, then retakes the base snapshot from master (base-snapshot.sh --from master)
# unless the current one is already of that commit, so set-tests.sh --full shows only this branch's changes.
# Run from the set's worktree root. Exit 0 done or nothing to do; 2 not in the set's worktree or uncommitted changes;
# 4 conflict with master (merge aborted: `git merge master`, resolve it, a parser VERSION conflict taking the higher
# number plus one, commit, then run this again); 1 the snapshot failed.
#   sync-master.sh <CODE>
set -u
[ $# = 1 ] || { echo "Usage: sync-master.sh <CODE>" >&2; exit 2; }
code=$(echo "$1" | tr 'A-Z' 'a-z'); CODE=$(echo "$1" | tr 'a-z' 'A-Z'); branch=set/$code
T=$(cd "$(dirname "$0")" && pwd)
[ "$(git rev-parse --abbrev-ref HEAD)" = "$branch" ] || { echo "Run it in the worktree of $branch" >&2; exit 2; }
[ -z "$(git status --porcelain --untracked-files=no)" ] || { echo "Commit first: the worktree has changes" >&2; exit 2; }
if git merge-base --is-ancestor master HEAD; then
  echo "master is already in $branch"
else
  echo "Merging master ($(git rev-parse --short master)) into $branch"
  if ! git merge --no-edit master; then
    git merge --abort
    echo "Conflict with master: run 'git merge master', resolve it (a parser VERSION conflict takes the higher number plus one), commit, then run this again" >&2
    exit 4
  fi
fi
# base-snapshot.sh records which commit a --from snapshot is of; retake it only when master differs from that.
ref=$HOME/projects/atw-cache/set-plans/$CODE/parser-base.ref
if [ "$(cat "$ref" 2>/dev/null)" = "$(git rev-parse master)" ]; then
  echo "The base snapshot is already master's"
  exit 0
fi
"$T/base-snapshot.sh" "$CODE" --from master || exit 1
