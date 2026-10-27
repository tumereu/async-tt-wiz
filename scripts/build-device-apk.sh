#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if (( $# < 1 || $# > 2 )) || [[ $1 != debug && $1 != release ]]; then
    echo "Usage: $0 debug|release [device-serial]" >&2
    echo "Set ATW_API_ORIGIN to the public HTTPS origin." >&2
    exit 2
fi
variant=$1
serial=${2:-}
origin=${ATW_API_ORIGIN:?Set ATW_API_ORIGIN to the public HTTPS origin}
[[ $origin == https://* ]] || { echo "The API origin must use HTTPS." >&2; exit 2; }

source scripts/lib/device-adb.sh
atw_find_phone "$serial"

task=assembleRelease
[[ $variant != debug ]] || task=assembleDebug
./scripts/gradle.sh ":androidApp:$task" -PatwSelfHosted=true \
    "-PatwApiOrigin=$origin" "-PatwResetOrigin=$origin" \
    "-PatwAccountSwitcher=$([[ $variant == debug ]] && echo true || echo false)" \
    -PatwCatalogTestOrigin=

apk="$PWD/androidApp/build/outputs/apk/$variant/androidApp-$variant.apk"
[[ -f $apk ]] || { echo "Expected APK not found: $apk" >&2; exit 1; }
atw_install_apk "$apk" "$variant"
echo "Installed. API: $origin"
