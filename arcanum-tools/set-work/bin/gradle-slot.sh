#!/usr/bin/env bash
# Runs ./scripts/gradle.sh "$@" once one of the machine-wide heavy-job slots is free, so parallel workers don't run more
# Gradle builds at once than the machine's memory takes. Slots: $ARCANUM_GRADLE_SLOTS (default 2), shared by every
# checkout on this machine through lock files in ~/projects/atw-cache/locks. Waits up to 3 hours.
# Run from the repository (or worktree) root.
#   gradle-slot.sh <gradle tasks and options>
set -u
if [ ! -x ./scripts/gradle.sh ] || [ ! -f ./settings.gradle.kts ]; then
  echo "gradle-slot.sh: run it from the repository root (no ./scripts/gradle.sh in $PWD)" >&2
  exit 2
fi
slots=${ARCANUM_GRADLE_SLOTS:-2}
[[ $slots =~ ^[1-9][0-9]*$ ]] || { echo "gradle-slot.sh: ARCANUM_GRADLE_SLOTS must be a positive number, not '$slots'" >&2; exit 2; }
L=$HOME/projects/atw-cache/locks
mkdir -p "$L"
end=$((SECONDS + 10800))
while :; do
  for ((n = 1; n <= slots; n++)); do
    exec 9>"$L/heavy-$n.lock"
    if flock -n 9; then ./scripts/gradle.sh "$@"; rc=$?; flock -u 9; exit $rc; fi
  done
  ((SECONDS > end)) && { echo "No Gradle slot free for 3 hours" >&2; exit 75; }
  sleep 20
done
