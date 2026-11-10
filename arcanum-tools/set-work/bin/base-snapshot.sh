#!/usr/bin/env bash
# Takes a set's starting point: the parser's model of every local card (parserDiff snapshot), written to
# ~/projects/atw-cache/set-plans/<CODE>/parser-base.snapshot, which `set-tests.sh <code> --full` compares against. A previous
# one is kept as parser-base.previous.snapshot.
#   base-snapshot.sh <CODE>                 snapshot of the current worktree's parser
#   base-snapshot.sh <CODE> --from <ref>    snapshot of <ref>'s parser (e.g. master), in a temporary detached worktree
# Run from the repository (or worktree) root.
set -u
usage() { echo "Usage: base-snapshot.sh <CODE> [--from <ref>]" >&2; exit 2; }
[ $# = 1 ] || { [ $# = 3 ] && [ "$2" = --from ]; } || usage
CODE=$(echo "$1" | tr 'a-z' 'A-Z'); ref=${3:-}
[[ $CODE =~ ^[A-Z0-9]+$ ]] || usage
if [ ! -x ./scripts/gradle.sh ] || [ ! -f ./settings.gradle.kts ]; then
  echo "base-snapshot.sh: run it from the repository root (no ./scripts/gradle.sh in $PWD)" >&2
  exit 2
fi
T=$(cd "$(dirname "$0")" && pwd)
C=$HOME/projects/atw-cache; plan=$C/set-plans/$CODE
mkdir -p "$plan"
tmp=$(mktemp -d "$plan/.snapshot.XXXXXX")
wt=
cleanup() {
  [ -n "$wt" ] && git worktree remove --force "$wt" >/dev/null 2>&1
  rm -rf -- "$tmp"
}
trap cleanup EXIT
dir=$PWD
if [ -n "$ref" ]; then
  git rev-parse --verify --quiet "$ref^{commit}" >/dev/null || { echo "base-snapshot.sh: no commit '$ref'" >&2; exit 2; }
  wt=$(mktemp -d "$C/snapshot-worktree.XXXXXX") && rmdir "$wt"
  git worktree add --detach --quiet "$wt" "$ref" || { wt=; echo "base-snapshot.sh: can't make a worktree of $ref" >&2; exit 1; }
  dir=$wt
  echo "Snapshot of $ref ($(git rev-parse --short "$ref")) in a temporary worktree"
fi
log=$tmp/gradle.log
(cd "$dir" && "$T/gradle-slot.sh" :arcanum-tools:parserDiff --args=snapshot -PparserOutputs="$tmp" >"$log" 2>&1)
rc=$?
if [ $rc != 0 ] || [ ! -s "$tmp/parser-models.snapshot" ]; then
  grep -E "^e: |error:|What went wrong|BUILD FAILED" -A3 "$log" | head -30
  echo "base-snapshot.sh: the snapshot failed; $plan/parser-base.snapshot is unchanged" >&2
  exit 1
fi
grep -E "^Parsed " "$log"
[ -f "$plan/parser-base.snapshot" ] && mv -f -- "$plan/parser-base.snapshot" "$plan/parser-base.previous.snapshot"
mv -- "$tmp/parser-models.snapshot" "$plan/parser-base.snapshot"
echo "Wrote $plan/parser-base.snapshot"
