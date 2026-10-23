#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if (( $# < 1 || $# > 2 )); then
    echo "Usage: $0 <https-api-origin> [https-reset-origin]" >&2
    exit 2
fi

# Gradle validates origins and resolves signing references from properties or env.
# Reset links use the API origin unless a separate HTTPS origin is supplied.
./scripts/gradle.sh :androidApp:assembleRelease -PatwSelfHosted=true \
    "-PatwApiOrigin=$1" "-PatwResetOrigin=${2:-$1}"

mkdir -p androidApp/build/self-hosting
cp androidApp/build/outputs/apk/release/androidApp-release.apk \
    androidApp/build/self-hosting/async-tt-wiz.apk
echo "Sideload APK: $PWD/androidApp/build/self-hosting/async-tt-wiz.apk"
