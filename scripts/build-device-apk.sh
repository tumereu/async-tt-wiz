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

# Windows owns physical USB devices on WSL. Its ADB may intentionally use a
# different port from the Linux/forwarded server on 5037. Never restart either.
adb_command=()
if [[ -n ${ATW_ADB:-} ]]; then
    adb_command=("$ATW_ADB")
elif grep -qi microsoft /proc/sys/kernel/osrelease && command -v adb.exe >/dev/null; then
    adb_command=(adb.exe)
    if [[ -z ${ATW_ADB_PORT:-} ]] && command -v powershell.exe >/dev/null; then
        ATW_ADB_PORT=$(powershell.exe -NoProfile -Command \
            'Get-CimInstance Win32_Process -Filter "Name='"'"'adb.exe'"'"'" | ForEach-Object { if ($_.CommandLine -match "-L tcp:(\d+)") { $Matches[1] } }' \
            | tr -d '\r' | sort -u)
        [[ $ATW_ADB_PORT != *$'\n'* ]] || {
            echo "Multiple Windows ADB servers found; set ATW_ADB_PORT explicitly." >&2; exit 1;
        }
    fi
else
    adb_command=(adb)
fi
if [[ -n ${ATW_ADB_PORT:-} ]]; then
    [[ $ATW_ADB_PORT =~ ^[0-9]+$ ]] || { echo "Invalid ATW_ADB_PORT." >&2; exit 2; }
    adb_command+=(-H "${ATW_ADB_HOST:-127.0.0.1}" -P "$ATW_ADB_PORT")
fi
devices=$("${adb_command[@]}" devices | tr -d '\r')
if [[ -z $serial ]]; then
    mapfile -t physical < <(awk '$2 == "device" && $1 !~ /^emulator-/ { print $1 }' <<< "$devices")
    if (( ${#physical[@]} != 1 )); then
        echo "Connect and authorize exactly one physical device, or supply its serial:" >&2
        echo "$devices" >&2
        exit 1
    fi
    serial=${physical[0]}
fi
awk -v serial="$serial" '$1 == serial && $2 == "device" { found=1 } END { exit !found }' <<< "$devices" || {
    echo "Device $serial is not connected and authorized." >&2; exit 1;
}

task=assembleRelease
[[ $variant != debug ]] || task=assembleDebug
./scripts/gradle.sh ":androidApp:$task" -PatwSelfHosted=true \
    "-PatwApiOrigin=$origin" "-PatwResetOrigin=$origin" \
    "-PatwAccountSwitcher=$([[ $variant == debug ]] && echo true || echo false)" \
    -PatwCatalogTestOrigin=

apk="$PWD/androidApp/build/outputs/apk/$variant/androidApp-$variant.apk"
[[ -f $apk ]] || { echo "Expected APK not found: $apk" >&2; exit 1; }
if [[ ${adb_command[0]} == *.exe ]]; then
    apk=$(wslpath -w "$apk")
fi
echo "Transferring and installing $variant on $serial (existing app data is preserved)."
"${adb_command[@]}" -s "$serial" push "$apk" "/sdcard/Download/async-tt-wiz-$variant.apk"
"${adb_command[@]}" -s "$serial" install -r "$apk"
echo "Installed. API: $origin"
