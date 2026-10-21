#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# This WSL host needs IPv4-scoped tooling sockets. Do not alter the product network
# stack or machine-wide settings; test/worker JVMs inherit only this invocation.
if grep -qi microsoft /proc/sys/kernel/osrelease 2>/dev/null; then
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djava.net.preferIPv4Stack=true"
fi
exec ./gradlew "$@"
