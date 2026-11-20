#!/usr/bin/env bash
# Every suite AGENTS.md lists, then the full e2e run, on a commit (default master) in a verify worktree, with the logs in
# <log folder>. The coordinator runs it whenever the machine is idle, not only before a release; a release needs it green.
#   scripts/full-verify.sh <log folder> [<commit, default master>] [--long]
#     --long  adds the engine's four long random-game classes on Kotlin/Wasm (-Parcanum.wasmLongGames=true, about 8
#             minutes more; before each release, ask the owner whether to include them)
# Logs: suites.log and e2e.log (each ends with its exit code), and summary.txt. The worktree is detached at the commit
# and kept between runs ($ATW_VERIFY_WORKTREE, default ~/projects/atw-wt/full-verify-<repository folder>). Builds go
# through the machine-wide heavy-build slots (arcanum-tools/set-work/bin/gradle-slot.sh). :arcanum-tools:setSupport reads
# the pinned MTGJSON file (scripts/catalog-pin.sh verify). The e2e run starts even when the suites failed.
# Exit 0 all green; 1 something failed (see summary.txt); 2 usage or setup.
set -u
usage() { sed -n '2,/^set -u/p' "$0" | sed '$d; s/^# \{0,1\}//' >&2; exit 2; }
logs=; ref=master; long=()
for a in "$@"; do
    case $a in
        --long) long=(-Parcanum.wasmLongGames=true) ;;
        -h|--help|-*) usage ;;
        *) if [ -z "$logs" ]; then logs=$a; elif [ "$ref" = master ]; then ref=$a; else usage; fi ;;
    esac
done
[ -n "$logs" ] || usage
mkdir -p "$logs" && logs=$(cd "$logs" && pwd) || exit 2
commit=$(git rev-parse -q --verify "$ref^{commit}") || { echo "full-verify: no commit '$ref'" >&2; exit 2; }
common=$(git rev-parse --path-format=absolute --git-common-dir) || exit 2
main=$(git worktree list --porcelain | sed -n '1s/^worktree //p')
wt=${ATW_VERIFY_WORKTREE:-$HOME/projects/atw-wt/full-verify-$(basename "$main")}
git worktree prune
if [ ! -e "$wt/.git" ]; then
    git worktree add -q --detach "$wt" "$commit" || exit 2
else
    [ "$(git -C "$wt" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)" = "$common" ] ||
        { echo "full-verify: $wt is not a worktree of this repository (set ATW_VERIFY_WORKTREE)" >&2; exit 2; }
    git -C "$wt" reset -q --hard && git -C "$wt" clean -qfd && git -C "$wt" checkout -q --detach "$commit" || exit 2
fi
slot=$wt/arcanum-tools/set-work/bin/gradle-slot.sh
# Its own Kotlin daemon (the merge gate has its own too): in the shared one, other builds wear the heap down until the
# web build's Wasm compile runs out of memory. A run that still runs out of memory runs once more in a fresh daemon
# (--continue: what passed is up to date, so only the rest runs again).
kotlin="-Pkotlin.daemon.jvmargs=-Xmx6g -Datw.kotlin.daemon=full-verify"
run() {
    local log=$1; shift
    "$slot" "$kotlin" --continue "$@" > "$log" 2>&1
    local rc=$?
    if [ $rc != 0 ] && grep -q 'Incremental compilation failed' "$log"; then
        # A stale Kotlin incremental cache in the kept worktree (the merge gate retries the same way)
        echo "full-verify: $(basename "$log") hit a stale Kotlin incremental cache; clearing it and running once more" | tee -a "$summary"
        mv "$log" "$log.stale-cache"
        find "$wt" -path '*/build/kotlin' -type d -prune -not -path '*/node_modules/*' -exec rm -rf {} +
        "$slot" "$kotlin" --continue "$@" > "$log" 2>&1
        rc=$?
    fi
    if [ $rc != 0 ] && grep -q -e 'Not enough memory to run compilation' -e 'Gradle build daemon disappeared unexpectedly' -e 'java.lang.OutOfMemoryError' "$log"; then
        echo "full-verify: $(basename "$log") ran out of memory; running it once more in a fresh Kotlin daemon" | tee -a "$summary"
        mv "$log" "$log.out-of-memory"
        pgrep -f -- '^[^ ]*/java .*-Datw.kotlin.daemon=full-verify .*KotlinCompileDaemon' | xargs -r kill
        "$slot" "$kotlin" --continue "$@" > "$log" 2>&1
        rc=$?
    fi
    return $rc
}
cd "$wt" || exit 2
summary=$logs/summary.txt
echo "full-verify of $(git log -1 --format='%h %s' "$commit") in $wt, started $(date '+%F %T')" | tee "$summary"
t0=$SECONDS
run "$logs/suites.log" :protocol:jvmTest :core:jvmTest :client-storage:test :server:test :androidApp:testDebugUnitTest \
    :androidApp:assembleDebug :webApp:webDist :arcanum-api:jvmTest :arcanum-parser:jvmTest :arcanum-engine:jvmTest \
    :arcanum-tools:test :automatum:jvmTest :catalog:jvmTest :arcanum-tools:setSupport :androidApp:compileDebugAndroidTestKotlin \
    :core:wasmJsNodeTest :protocol:wasmJsNodeTest :automatum:wasmJsNodeTest :arcanum-api:wasmJsNodeTest \
    :arcanum-parser:wasmJsNodeTest :arcanum-engine:wasmJsNodeTest :app-engine:test :app-engine:wasmJsNodeTest :app-ui:test \
    :app-ui:wasmJsBrowserTest verifyModuleBoundaries "${long[@]}"
suites=$?
echo "SUITES rc=$suites" >> "$logs/suites.log"
echo "suites: $([ $suites = 0 ] && echo green || echo "FAILED (exit $suites)") in $(( (SECONDS - t0) / 60 )) min" | tee -a "$summary"
[ $suites = 0 ] || grep -E ' FAILED$|^> Task .* FAILED' "$logs/suites.log" | head -40 | tee -a "$summary"
t1=$SECONDS
run "$logs/e2e.log" :e2e:test
e2e=$?
echo "E2E rc=$e2e" >> "$logs/e2e.log"
echo "e2e: $([ $e2e = 0 ] && echo green || echo "FAILED (exit $e2e)") in $(( (SECONDS - t1) / 60 )) min" | tee -a "$summary"
[ $e2e = 0 ] || { grep -E ' FAILED$' "$logs/e2e.log" | head -40; echo "failure screens and app logs: $wt/e2e/build/e2e/failures/"; } | tee -a "$summary"
[ $suites = 0 ] && [ $e2e = 0 ]
