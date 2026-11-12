#!/usr/bin/env bash
# Runs a command while holding the machine-wide plan lock (~/projects/atw-cache/locks/plan.lock). coveredShapes writes one
# machine-wide file (set-plans/covered-shapes.tsv) that setPlan reads, so sets planned in parallel take turns.
#   plan-lock.sh <command ...>
set -u
L=$HOME/projects/atw-cache/locks; mkdir -p "$L"
exec 8>"$L/plan.lock"
flock 8
"$@"
