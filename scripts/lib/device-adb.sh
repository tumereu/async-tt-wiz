#!/usr/bin/env bash
# Sourced by device builders. Never restart the Windows or Linux ADB servers.
atw_find_phone() {
    serial=${1:-${ATW_ADB_SERIAL:-}}
    adb_command=()
    local devices
    local -a physical
    if [[ -n ${ATW_ADB:-} ]]; then
        adb_command=("$ATW_ADB")
    elif grep -qi microsoft /proc/sys/kernel/osrelease && command -v adb.exe >/dev/null; then
        adb_command=(adb.exe)
        if [[ -z ${ATW_ADB_PORT:-} ]] && command -v powershell.exe >/dev/null; then
            ATW_ADB_PORT=$(powershell.exe -NoProfile -NonInteractive -Command \
                'Get-CimInstance Win32_Process -Filter "Name='"'"'adb.exe'"'"'" | ForEach-Object { if ($_.CommandLine -match "-L tcp:(\d+)") { $Matches[1] } }' \
                | tr -d '\r' | sort -u) || return 1
            [[ $ATW_ADB_PORT != *$'\n'* ]] || {
                echo "Multiple Windows ADB servers found; set ATW_ADB_PORT explicitly." >&2; return 1;
            }
        fi
    else
        adb_command=(adb)
    fi
    if [[ -n ${ATW_ADB_PORT:-} ]]; then
        [[ $ATW_ADB_PORT =~ ^[0-9]+$ ]] || { echo "Invalid ATW_ADB_PORT." >&2; return 2; }
        adb_command+=(-H "${ATW_ADB_HOST:-127.0.0.1}" -P "$ATW_ADB_PORT")
    fi
    devices=$("${adb_command[@]}" devices | tr -d '\r') || return 1
    if [[ -z $serial ]]; then
        mapfile -t physical < <(awk '$2 == "device" && $1 !~ /^emulator-/ { print $1 }' <<< "$devices")
        if (( ${#physical[@]} != 1 )); then
            echo "Connect and authorize exactly one physical device, or set ATW_ADB_SERIAL:" >&2
            echo "$devices" >&2
            return 1
        fi
        serial=${physical[0]}
    fi
    [[ $serial != emulator-* ]] || { echo "A physical phone is required." >&2; return 1; }
    awk -v serial="$serial" '$1 == serial && $2 == "device" { found=1 } END { exit !found }' <<< "$devices" || {
        echo "Device $serial is not connected and authorized." >&2; return 1;
    }
}

atw_install_apk() {
    local apk=$1 variant=$2
    if [[ ${adb_command[0]} == *.exe ]]; then
        apk=$(wslpath -w "$apk") || return 1
    fi
    echo "Transferring and installing $variant on $serial (existing app data is preserved)."
    "${adb_command[@]}" -s "$serial" push "$apk" "/sdcard/Download/async-tt-wiz-$variant.apk" || return 1
    "${adb_command[@]}" -s "$serial" install -r "$apk"
}
