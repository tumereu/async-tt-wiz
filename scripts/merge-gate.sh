#!/usr/bin/env bash
# The merge gate: the only way master moves (.githooks/reference-transaction refuses every other update of it).
# Merges <branch> into master in a separate verify worktree, runs the checks below on that merge commit there, and only
# if all pass makes master point at exactly that commit. On a failure master is untouched and the log says what failed.
#   scripts/merge-gate.sh <branch or commit> [-m <merge message>] [--extra '<gradle tasks and options>']...
# Checks, cheapest first, stopping at the first failure (each a Gradle run through gradle-slot.sh, logged on its own):
#   jvm      the JVM suites of the modules the merge changes and of every module depending on them (the module graph is
#            read from verifyModuleBoundaries in build.gradle.kts; a change outside the modules and docs means all), the
#            engine's long random-game classes left out (scripts/full-verify.sh runs them)
#   extra    each --extra argument, as given (set work's own checks)
#   android  :androidApp:compileDebugAndroidTestKotlin, :androidApp:testDebugUnitTest (screenshots verify there)
#   web      :webApp:webDist, the deployable web client
#   e2e      the e2e smoke set (E2E_SMOKE below) plus the e2e test classes the merge adds or changes
# A merge that changes only documentation (docs/, release/notes/, *.md) runs only checkFeatureCatalog.
# One gate at a time: it holds the merge lock ($ATW_LOCKS/master-merge.lock) from merging to moving master. If the
# checkout that has master checked out has local changes in the merge's way, it retries for 30 minutes.
# Environment: ATW_GATE_WORKTREE (default ~/projects/atw-wt/merge-gate-<repository folder>), ATW_GATE_LOGS (default
# ~/projects/atw-cache/merge-gate; one folder per run), ATW_LOCKS (default ~/projects/atw-cache/locks), ATW_GRADLE_SLOT
# (default arcanum-tools/set-work/bin/gradle-slot.sh: the machine-wide two heavy-build slots).
# After moving master it pushes master to origin (never forced; a failed push is reported, master stays moved).
# Exit 0 merged (or already in master); 2 usage or setup; 3 master or its checkout not ready; 4 conflict with master;
# 5 a check failed.
set -u
usage() { sed -n '2,/^set -u/p' "$0" | sed '$d; s/^# \{0,1\}//' >&2; exit 2; }

# The e2e smoke set: the core loop, a combat with its close animation (CMB-001), undo, the catch-up and the rewind on
# return, and the web client (WebGameFlowTest: a game's turns in the browser against a phone).
E2E_SMOKE=(
    org.asyncttwiz.e2e.CoreLoopTest
    org.asyncttwiz.e2e.CombatAnimationTest
    org.asyncttwiz.e2e.QuickUndoTest
    org.asyncttwiz.e2e.CatchUpPlaybackTest
    org.asyncttwiz.e2e.RewindOnReturnTest
    org.asyncttwiz.e2e.WebSmokeTest
    org.asyncttwiz.e2e.WebGameFlowTest
)
# Each module's JVM suite; modules without one are covered by the android, web and e2e checks.
declare -A SUITE=(
    [:core]=:core:jvmTest [:protocol]=:protocol:jvmTest [:client-storage]=:client-storage:test
    [:automatum]=:automatum:jvmTest [:catalog]=:catalog:jvmTest [:server]=:server:test
    [:arcanum-api]=:arcanum-api:jvmTest [:arcanum-parser]=:arcanum-parser:jvmTest
    [:arcanum-engine]=:arcanum-engine:jvmTest [:arcanum-tools]=:arcanum-tools:test
    [:app-engine]=:app-engine:test [:app-ui]=:app-ui:test
)

tip=; message=; extras=()
while [ $# -gt 0 ]; do
    case $1 in
        -m|--message) [ $# -ge 2 ] || usage; message=$2; shift 2 ;;
        --extra) [ $# -ge 2 ] || usage; extras+=("$2"); shift 2 ;;
        -h|--help|-*) usage ;;
        *) [ -z "$tip" ] || usage; tip=$1; shift ;;
    esac
done
[ -n "$tip" ] || usage

common=$(git rev-parse --path-format=absolute --git-common-dir) || exit 2
main=$(git worktree list --porcelain | sed -n '1s/^worktree //p')
tip_sha=$(git rev-parse -q --verify "$tip^{commit}") || { echo "merge-gate: no branch or commit '$tip'" >&2; exit 2; }
if git show-ref -q --verify "refs/heads/$tip"; then label="Merge branch '$tip'"; else label="Merge $(git rev-parse --short "$tip_sha")"; fi
[ -n "$message" ] && label=$message
wt=${ATW_GATE_WORKTREE:-$HOME/projects/atw-wt/merge-gate-$(basename "$main")}
locks=${ATW_LOCKS:-$HOME/projects/atw-cache/locks}
slug=$(printf '%s' "$tip" | tr -c 'A-Za-z0-9._-' '-')
run=${ATW_GATE_LOGS:-$HOME/projects/atw-cache/merge-gate}/$(date +%Y%m%d-%H%M%S)-$slug
mkdir -p "$run" "$locks" || exit 2
log=$run/gate.log
say() { echo "merge-gate: $*" | tee -a "$log"; }
begin=$SECONDS

exec 7>"$locks/master-merge.lock"
say "waiting for the merge lock ($locks/master-merge.lock)"
flock 7
base=$(git rev-parse refs/heads/master)
if git merge-base --is-ancestor "$tip_sha" "$base"; then say "$tip is already in master"; exit 0; fi

# The verify worktree: detached, kept between runs (its build outputs make the next run incremental).
git worktree prune
if [ ! -e "$wt/.git" ]; then
    say "creating the verify worktree $wt"
    git worktree add -q --detach "$wt" "$base" >>"$log" 2>&1 || { say "could not create $wt"; exit 2; }
else
    [ "$(git -C "$wt" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)" = "$common" ] ||
        { say "$wt is not a worktree of this repository (set ATW_GATE_WORKTREE)"; exit 2; }
    git -C "$wt" merge --abort >/dev/null 2>&1
    { git -C "$wt" reset -q --hard && git -C "$wt" clean -qfd && git -C "$wt" checkout -q --detach "$base"; } >>"$log" 2>&1 ||
        { say "could not reset $wt to master"; exit 2; }
fi
if ! git -C "$wt" merge -q --no-ff --no-edit -m "$label" "$tip_sha" >>"$log" 2>&1; then
    say "conflict with master in: $(git -C "$wt" diff --name-only --diff-filter=U | tr '\n' ' ')"
    git -C "$wt" merge --abort >/dev/null 2>&1
    say "master untouched: merge master into $tip, resolve, and run the gate again"
    exit 4
fi
merge=$(git -C "$wt" rev-parse HEAD)
say "testing $label: $(git rev-parse --short "$base") + $(git rev-parse --short "$tip_sha") = $(git rev-parse --short "$merge") in $wt"
say "logs: $run"

# What the merge changes on master, and which checks that calls for.
mapfile -t changed < <(git diff --name-only "$base" "$merge")
graph=$(python3 - "$wt/build.gradle.kts" "${changed[@]}" <<'PY'
import re, sys
text = open(sys.argv[1]).read()
block = text[text.index('tasks.register("verifyModuleBoundaries")'):]
block = block[:block.index("subprojects.forEach")]
# Both maps (dependencies, and those allowed only in tests): a test-only dependency's change reaches those tests too.
deps = {}
for module, items in re.findall(r'"(:[^"]+)" to (?:setOf\(([^)]*)\)|emptySet\(\))', block):
    deps.setdefault(module, set()).update(re.findall(r'"(:[^"]+)"', items))
modules = set(deps) | {d for ds in deps.values() for d in ds}
dirs = sorted(((m[1:].replace(":", "/") + "/", m) for m in modules), key=lambda p: -len(p[0]))
docs = lambda p: p.startswith(("docs/", "release/notes/")) or p.endswith(".md")
touched, everything, onlydocs = set(), False, True
for path in sys.argv[2:]:
    if docs(path):
        continue
    onlydocs = False
    owner = next((m for d, m in dirs if path.startswith(d)), None)
    if owner is None:
        everything = True
    else:
        touched.add(owner)
if onlydocs:
    print("DOCS")
    sys.exit()
affected = set(modules) if everything else set(touched)
while True:
    more = {m for m, ds in deps.items() if ds & affected} - affected
    if not more:
        break
    affected |= more
print(" ".join(sorted(affected)))
PY
) || { say "could not read the module graph from build.gradle.kts"; exit 2; }

slot=${ATW_GRADLE_SLOT:-$wt/arcanum-tools/set-work/bin/gradle-slot.sh}
n=0
stage() { # stage <name> <gradle arguments...>
    local name=$1; shift
    n=$((n + 1))
    local out=$run/$n-$name.log t0=$SECONDS rc
    say "[$name] gradle $*"
    # The gate compiles with a Kotlin daemon of its own: sharing the machine's daemon with workers' builds ran it out of
    # memory on the web build (2026-11-19), a failure that says nothing about the branch.
    set -- "-Pkotlin.daemon.jvmargs=-Xmx5g -Datw.kotlin.daemon=merge-gate" "$@"
    (cd "$wt" && "$slot" "$@") >"$out" 2>&1
    rc=$?
    # The kept worktree's Kotlin incremental caches can go stale after a large merge ("Incremental compilation failed");
    # that says nothing about the branch, so the caches are cleared and the stage runs once more, its time included.
    if [ $rc != 0 ] && grep -q 'Incremental compilation failed' "$out"; then
        say "[$name] stale Kotlin build cache after $(( (SECONDS - t0) / 60 ))m $(( (SECONDS - t0) % 60 ))s: clearing build/kotlin and running the stage once more"
        mv "$out" "$out.stale-cache"
        find "$wt" -path '*/build/kotlin' -type d -prune -not -path '*/node_modules/*' -exec rm -rf {} +
        (cd "$wt" && "$slot" "$@") >"$out" 2>&1
        rc=$?
    elif [ $rc != 0 ] && grep -q -e 'Not enough memory to run compilation' -e 'Gradle build daemon disappeared unexpectedly' "$out"; then
        # The compiler or the Gradle daemon ran out of memory or was killed (the machine busy with other builds): one more
        # run, its time included.
        say "[$name] the build ran out of memory or its daemon was killed after $(( (SECONDS - t0) / 60 ))m $(( (SECONDS - t0) % 60 ))s: running the stage once more"
        mv "$out" "$out.out-of-memory"
        (cd "$wt" && "$slot" "$@") >"$out" 2>&1
        rc=$?
    fi
    local waited; waited=$(sed -n 's/^gradle-slot: waited \([0-9]*\)s.*/\1/p' "$out" | head -1)
    say "[$name] $([ $rc = 0 ] && echo passed || echo "FAILED (exit $rc)") in $(( (SECONDS - t0) / 60 ))m $(( (SECONDS - t0) % 60 ))s${waited:+ (${waited}s of it waiting for a build slot)}"
    [ $rc = 0 ] && return 0
    {
        echo "---- $out (failed tests and Gradle's reason; the whole log is there)"
        grep -E ' FAILED$|^e: ' "$out" | grep -v '^> Task' | head -40
        sed -n '/^\* What went wrong:/,/^\* Try:/p' "$out" | head -30
        [ "$name" = e2e ] && echo "e2e failure screens and app logs: $wt/e2e/build/e2e/failures/"
    } | tee -a "$log"
    say "master untouched ($(git rev-parse --short "$base")); the merge stays checked out in $wt for a look"
    say "total $(( (SECONDS - begin) / 60 ))m $(( (SECONDS - begin) % 60 ))s"
    exit 5
}

if [ "$graph" = DOCS ]; then
    say "documentation only: the catalog check"
    stage catalog checkFeatureCatalog
else
    suites=()
    for m in $graph; do [ -n "${SUITE[$m]:-}" ] && suites+=("${SUITE[$m]}"); done
    say "changed modules and their dependents: ${graph:-none}"
    [ ${#suites[@]} -gt 0 ] && stage jvm -Parcanum.skipLongTests=true "${suites[@]}"
    for x in "${extras[@]}"; do
        # shellcheck disable=SC2086 # one --extra holds several Gradle words
        stage extra $x
    done
    stage android :androidApp:compileDebugAndroidTestKotlin :androidApp:testDebugUnitTest
    stage web :webApp:webDist
    e2e=("${E2E_SMOKE[@]}")
    for path in "${changed[@]}"; do
        [[ $path == e2e/src/test/kotlin/*Test.kt && -f $wt/$path ]] || continue
        class=${path#e2e/src/test/kotlin/}; class=${class%.kt}; class=${class//\//.}
        [[ " ${e2e[*]} " == *" $class "* ]] || e2e+=("$class")
    done
    args=(); for class in "${e2e[@]}"; do args+=(--tests "$class"); done
    stage e2e :e2e:test "${args[@]}"
fi

# Every check passed: master moves to exactly the tested commit, through the checkout that has master checked out (so
# its files follow), or directly when none has.
for _ in $(seq 1 60); do
    if [ "$(git rev-parse refs/heads/master)" != "$base" ]; then
        say "master moved during the checks without the gate (now $(git rev-parse --short refs/heads/master)): nothing merged; run the gate again"
        exit 3
    fi
    holder=$(git worktree list --porcelain | awk '/^worktree /{w=substr($0, 10)} $0 == "branch refs/heads/master" {print w}')
    if [ -z "$holder" ]; then
        ATW_MERGE_GATE_SHA=$merge git update-ref -m "merge-gate: $label" refs/heads/master "$merge" "$base" >>"$log" 2>&1 && break
    elif [ ! -e "$(git -C "$holder" rev-parse --git-path MERGE_HEAD)" ] &&
         ATW_MERGE_GATE_SHA=$merge git -C "$holder" merge -q --ff-only "$merge" >>"$log" 2>&1; then
        break
    fi
    say "the checkout of master (${holder:-none}) is not ready (a merge in progress, or local changes in the merge's way); retrying in 30 s"
    sleep 30
done
if [ "$(git rev-parse refs/heads/master)" != "$merge" ]; then
    say "gave up after 30 minutes: master untouched; the tested merge is $merge"
    exit 3
fi
say "master is now $(git rev-parse --short "$merge"): $label"
# Origin is the backup (AGENTS.md: push after every commit)
if git push --quiet origin refs/heads/master:refs/heads/master >>"$log" 2>&1; then
    say "pushed master to origin"
else
    say "pushing master to origin failed (see $log); push it by hand: git push origin master"
fi
say "total $(( (SECONDS - begin) / 60 ))m $(( (SECONDS - begin) % 60 ))s"
