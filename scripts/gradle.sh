#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# This WSL host needs IPv4-scoped tooling sockets. Do not alter the product network
# stack or machine-wide settings; test/worker JVMs inherit only this invocation.
if grep -qi microsoft /proc/sys/kernel/osrelease 2>/dev/null; then
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djava.net.preferIPv4Stack=true"
fi
# Run builds (and the Gradle/Kotlin daemons and test JVMs they start) in their own capped user slice, so
# memory pressure kills a build instead of the whole terminal session that launched it.
# ATW_NO_BUILD_SLICE=1 opts out.
if [[ -z ${ATW_NO_BUILD_SLICE:-} ]] && ! grep -q atw-build.slice /proc/self/cgroup 2>/dev/null &&
    command -v systemd-run >/dev/null && systemctl --user cat atw-build.slice >/dev/null 2>&1; then
    exec systemd-run --user --scope --quiet --collect --slice=atw-build.slice ./gradlew "$@"
fi
exec ./gradlew "$@"
