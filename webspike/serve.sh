#!/usr/bin/env bash
# Web spike (throwaway): builds the spike pages and serves them at http://localhost:${PORT:-18765}/
#   /                       links
#   /bench/?mode=main       engine benchmark on the page's main thread (part A in a browser)
#   /bench/?mode=worker     engine in a dedicated Web Worker + page<->worker round trips (part B)
#   /table/                 Compose Multiplatform table, ~150 cards with Scryfall images (part C); /table/?auto = auto-pan
# Phone (Chrome on Android): PHONE=1 also runs `adb.exe -P 15138 reverse tcp:$PORT tcp:$PORT` and opens the index on the
# phone; otherwise run that reverse yourself and open http://localhost:$PORT/ there. NO_BUILD=1 skips the Gradle build.
set -euo pipefail
cd "$(dirname "$0")/.."
PORT=${PORT:-18765}
if [[ -z ${NO_BUILD:-} ]]; then
    ./scripts/gradle.sh :webspike-bench:wasmJsProductionExecutableCompileSync :webspike-table:wasmJsBrowserDistribution --write-locks -q
fi
SITE=webspike/build/site
rm -rf "$SITE"
mkdir -p "$SITE/bench" "$SITE/table"
BENCH=webspike/bench/build/compileSync/wasmJs/main/productionExecutable/optimized
cp "$BENCH"/webspike-bench.mjs "$BENCH"/webspike-bench.wasm "$BENCH"/webspike-bench.import-object.mjs "$BENCH"/webspike-bench.js-builtins.mjs "$SITE/bench/"
cp webspike/bench/src/wasmJsMain/resources/bench.html "$SITE/bench/index.html"
cp -r webspike/table/build/dist/wasmJs/productionExecutable/. "$SITE/table/"
rm -f "$SITE"/table/*.map
cp webspike/index.html "$SITE/index.html"
if [[ -n ${PHONE:-} ]]; then
    adb.exe -P 15138 reverse "tcp:$PORT" "tcp:$PORT"
    adb.exe -P 15138 shell am start -a android.intent.action.VIEW -d "http://localhost:$PORT/" com.android.chrome
fi
echo "Serving $SITE at http://localhost:$PORT/"
exec python3 -m http.server "$PORT" --bind "${BIND:-127.0.0.1}" --directory "$SITE"
