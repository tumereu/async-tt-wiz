#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

devices=("$@")
if ((${#devices[@]} == 0)); then
    mapfile -t devices < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
fi
if ((${#devices[@]} == 0)); then
    echo "No authorized devices. Start a Windows emulator, then check adb devices." >&2
    exit 1
fi
for serial in "${devices[@]}"; do
    [[ $(adb -s "$serial" get-state) == device ]]
done

./scripts/gradle.sh :androidApp:assembleDebug \
    -PatwApiOrigin=http://127.0.0.1:8080 \
    -PatwCatalogTestOrigin=http://127.0.0.1:18081 \
    -PatwResetOrigin=https://reset.atw.invalid

for serial in "${devices[@]}"; do
    echo "Installing and launching on $serial"
    adb -s "$serial" reverse tcp:8080 tcp:8080
    adb -s "$serial" reverse tcp:18081 tcp:18081
    adb -s "$serial" install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
    adb -s "$serial" shell am start -S -W -n org.asyncttwiz.android/.MainActivity
    adb -s "$serial" shell pidof org.asyncttwiz.android
done
