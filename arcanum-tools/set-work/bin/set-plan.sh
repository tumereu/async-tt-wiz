#!/usr/bin/env bash
# Step 2 of the set procedure under the plan lock: coveredShapes, then setPlan for <CODE>. coveredShapes (6-8 minutes) is
# skipped when covered-shapes.tsv was made from the same parser, engine and tools sources (stamp next to it), which is the
# case for sets started in parallel from the same master. Run from the worktree root.
#   set-plan.sh <CODE>
set -u
[ $# = 1 ] || { echo "Usage: set-plan.sh <CODE>" >&2; exit 2; }
CODE=$(echo "$1" | tr 'a-z' 'A-Z')
T=$(cd "$(dirname "$0")" && pwd)
plans=$HOME/projects/atw-cache/set-plans; mkdir -p "$plans"
run() {
  local srcs=(arcanum-api/src arcanum-parser/src arcanum-engine/src arcanum-tools/src)
  local stamp=""
  if [ -z "$(git status --porcelain -- "${srcs[@]}")" ]; then
    stamp=$(for s in "${srcs[@]}"; do git rev-parse "HEAD:$s"; done | sha1sum | cut -c1-40)
  fi
  if [ -n "$stamp" ] && [ -f "$plans/covered-shapes.tsv" ] && [ "$(cat "$plans/covered-shapes.stamp" 2>/dev/null)" = "$stamp" ]; then
    echo "coveredShapes: up to date for these sources, skipped"
  else
    rm -f "$plans/covered-shapes.stamp"
    "$T/gradle-slot.sh" :arcanum-tools:coveredShapes || return $?
    [ -n "$stamp" ] && echo "$stamp" >"$plans/covered-shapes.stamp"
  fi
  "$T/gradle-slot.sh" :arcanum-tools:setPlan --args="$CODE"
}

L=$HOME/projects/atw-cache/locks; mkdir -p "$L"
exec 8>"$L/plan.lock"
flock 8
run
