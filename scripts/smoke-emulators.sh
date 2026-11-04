#!/usr/bin/env bash
# Two-emulator smoke of the core game loop against the WSL test env (scripts/test-env.sh, API :18090).
# Installs the same R8-minified, non-debuggable "ATW Test" build the phone gets; only its API origin
# (http://127.0.0.1:18090 through adb reverse) and version code differ. Never touches the phone,
# the test server or Gradle daemons. Output goes to ~/projects/atw-cache/smoke/<timestamp>/.
#
#   scripts/smoke-emulators.sh up [--no-build]   start/prepare emulators, build, install, new run, sign in a/b
#   scripts/smoke-emulators.sh build             build the smoke APK only
#   scripts/smoke-emulators.sh install [APK]     install (default: last built smoke APK) on both
#   scripts/smoke-emulators.sh new-run           start a new output directory (clears logcat)
#   scripts/smoke-emulators.sh signin            sign in a@a.com on device a and b@b.com on device b
#   scripts/smoke-emulators.sh shot LABEL        screenshot both devices
#   scripts/smoke-emulators.sh rec LABEL [SEC]   record both screens for SEC seconds (default 10)
#   scripts/smoke-emulators.sh rec-start LABEL   start recording both (max 180 s) ... rec-stop pulls the videos
#   scripts/smoke-emulators.sh ui a|b [NAME]     dump the UI tree and list visible texts with tap centres
#   scripts/smoke-emulators.sh tap a|b X Y       tap a point (device pixels)
#   scripts/smoke-emulators.sh tap-text a|b RE [N]  tap the Nth (0 = first, -1 = last) node whose text or
#                                                description matches RE ("text:RE"/"desc:RE" match only one)
#   scripts/smoke-emulators.sh wait-text a|b RE [SEC]  wait until such a node is shown
#   scripts/smoke-emulators.sh type a|b TEXT     type text into the focused field
#   scripts/smoke-emulators.sh key a|b KEY       send a key event (BACK, ENTER, ...)
#   scripts/smoke-emulators.sh logcat            save both logcats and list app errors/crashes
#   scripts/smoke-emulators.sh dir               print the current run directory
# Devices: a = $ATW_SMOKE_A (emulator-5554, AVD atw-smoke-api29-a), b = $ATW_SMOKE_B (emulator-5556, ...-b).
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD
A=${ATW_SMOKE_A:-emulator-5554}
B=${ATW_SMOKE_B:-emulator-5556}
SMOKE_ROOT=${ATW_SMOKE_DIR:-$HOME/projects/atw-cache/smoke}
CACHE=$HOME/projects/atw-cache
TEST_STATE=${XDG_DATA_HOME:-$HOME/.local/share}/atw-test
PKG=org.asyncttwiz.android.test
API_PORT=18090
PROXY_PORT=3128
PASSWORD=${ATW_SMOKE_PASSWORD:-12345678}
EMULATOR_EXE='C:\Android\emulator\emulator.exe'

serial_of() {
    case $1 in
    a | "$A") echo "$A" ;;
    b | "$B") echo "$B" ;;
    *) echo "Unknown device '$1' (use a or b)." >&2; return 2 ;;
    esac
}
name_of() { [[ $1 == "$A" ]] && echo a || echo b; }
both() { local fn=$1; shift; "$fn" "$A" "$@"; "$fn" "$B" "$@"; }

run_dir() {
    [[ -L $SMOKE_ROOT/latest ]] || new_run >/dev/null
    readlink -f "$SMOKE_ROOT/latest"
}

new_run() {
    local dir
    dir=$SMOKE_ROOT/$(date +%Y%m%d-%H%M%S)
    mkdir -p "$dir"
    ln -sfn "$dir" "$SMOKE_ROOT/latest"
    for s in "$A" "$B"; do adb -s "$s" logcat -c </dev/null || true; done
    echo "$dir"
}

# --- emulators -------------------------------------------------------------------------------------

online() { adb -s "$1" get-state </dev/null 2>/dev/null | grep -qx device; }

start_emulator() {
    local serial=$1 avd=$2 port=${1#emulator-}
    online "$serial" && return 0
    echo "Starting $avd as $serial"
    powershell.exe -NoProfile -NonInteractive -Command \
        "\$env:ANDROID_AVD_HOME = Join-Path \$env:LOCALAPPDATA 'async-tt-wiz-e2e-smoke\\avds'; Start-Process -FilePath '$EMULATOR_EXE' -ArgumentList '-avd','$avd','-ports','$port,$((port + 1))','-no-snapshot-save','-no-boot-anim'" </dev/null
}

wait_boot() {
    local serial=$1 i
    for ((i = 0; i < 180; i++)); do
        if online "$serial" && [[ $(adb -s "$serial" shell getprop sys.boot_completed </dev/null 2>/dev/null | tr -d '\r') == 1 ]]; then
            return 0
        fi
        sleep 2
    done
    echo "$serial did not boot." >&2
    return 1
}

prepare() {
    local s=$1
    adb -s "$s" shell wm size 1080x2340 </dev/null
    adb -s "$s" shell wm density 420 </dev/null
    adb -s "$s" shell ime disable com.android.inputmethod.latin/.LatinIME </dev/null >/dev/null 2>&1 || true
    adb -s "$s" shell settings put global http_proxy "127.0.0.1:$PROXY_PORT" </dev/null
    adb -s "$s" shell settings put system screen_off_timeout 1800000 </dev/null
    adb -s "$s" shell svc power stayon true </dev/null
    # The catalog and card art come from mtgjson/scryfall through the host's forward proxy.
    adb -s "$s" reverse "tcp:$API_PORT" "tcp:$API_PORT" </dev/null >/dev/null
    adb -s "$s" reverse "tcp:$PROXY_PORT" "tcp:$PROXY_PORT" </dev/null >/dev/null
    adb -s "$s" shell input keyevent KEYCODE_WAKEUP </dev/null
    # A dev build left running against a stopped dev server reconnects every second: quiet it (data kept).
    adb -s "$s" shell am force-stop org.asyncttwiz.android </dev/null
}

check_host() {
    curl --silent --fail --max-time 3 http://127.0.0.1:18091/health/ready >/dev/null || {
        echo "Test env is not running (scripts/test-env.sh status). This script never starts it." >&2; return 1;
    }
    if ! ss -ltn "sport = :$PROXY_PORT" | grep -q LISTEN; then
        echo "Starting the emulator forward proxy on :$PROXY_PORT"
        nohup setsid python3 -I "$CACHE/tools/proxy.py" "$PROXY_PORT" >>"$CACHE/tools/proxy.log" 2>&1 </dev/null &
        sleep 1
    fi
}

devices_up() {
    start_emulator "$A" atw-smoke-api29-a
    start_emulator "$B" atw-smoke-api29-b
    wait_boot "$A"; wait_boot "$B"
    both prepare
}

# --- build and install ----------------------------------------------------------------------------

apk_path() {
    local f
    for f in "$ROOT"/androidApp/build/outputs/apk/fcm/personalTest/*.apk "$ROOT"/androidApp/build/outputs/apk/personalTest/*.apk; do
        [[ -f $f ]] && { echo "$f"; return 0; }
    done
    return 1
}

build() {
    local code variant=personalTest
    # Above the published test release (so no update prompt), below any later test-env deploy. Read only:
    # the test env's own version counter is never written here.
    code=$(python3 -I - "$TEST_STATE/app-release/latest.json" <<'PY'
import json, pathlib, sys, time
latest = pathlib.Path(sys.argv[1])
published = json.loads(latest.read_text())["versionCode"] if latest.exists() else 0
print(max(int(time.time()) - 1577836800, published + 1))
PY
    )
    source "$ROOT/scripts/lib/fcm.sh"
    atw_fcm_args "$ATW_FIREBASE/google-services-test.json" "$PKG"
    if (( ${#fcm_args[@]} )); then variant=fcmPersonalTest; fi
    rm -rf "$ROOT/androidApp/build/outputs/apk/fcm/personalTest" "$ROOT/androidApp/build/outputs/apk/personalTest"
    ./scripts/gradle.sh ":androidApp:assemble${variant^}" "-PatwTestVersionCode=$code" \
        "-PatwSmokeApiOrigin=http://127.0.0.1:$API_PORT" "${fcm_args[@]}" >&2
    apk_path
}

install_one() {
    local s=$1 apk=$2 out
    if ! out=$(adb -s "$s" install -r "$apk" </dev/null 2>&1); then
        if grep -q -e INSTALL_FAILED_UPDATE_INCOMPATIBLE -e INSTALL_FAILED_VERSION_DOWNGRADE <<<"$out"; then
            echo "$s: replacing an incompatible/newer ATW Test install (its data is lost)"
            adb -s "$s" uninstall "$PKG" </dev/null >/dev/null
            adb -s "$s" install "$apk" </dev/null
        else
            echo "$out" >&2; return 1
        fi
    fi
    adb -s "$s" shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS </dev/null >/dev/null 2>&1 || true
    # A process that outlived the upgrade can keep stale network gids (socket EPERM): restart it.
    adb -s "$s" shell am force-stop "$PKG" </dev/null
}

launch() { adb -s "$1" shell am start -W -n "$PKG/org.asyncttwiz.android.MainActivity" </dev/null >/dev/null; }

# --- UI helpers -------------------------------------------------------------------------------------

# Dumps the UI tree; prints "x y|text|description" for every node with a text or description.
ui() {
    local s=$1 name=${2:-} dir xml i
    dir=$(run_dir)
    xml=$dir/ui-$(name_of "$s")${name:+-$name}.xml
    for ((i = 0; i < 5; i++)); do
        if adb -s "$s" exec-out uiautomator dump /dev/tty </dev/null 2>/dev/null | sed 's#UI hierchary dumped to: /dev/tty##' >"$xml" &&
            grep -q '<hierarchy' "$xml"; then
            break
        fi
        sleep 0.5
    done
    python3 -I - "$xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for node in ET.parse(sys.argv[1]).iter("node"):
    text, desc = node.get("text", ""), node.get("content-desc", "")
    if not (text or desc):
        continue
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}|{text}|{desc}")
PY
}

# Prints the tap centre of the Nth (default first; -1 = last) node matching RE.
find_text() {
    local s=$1 re=$2 nth=${3:-0}
    ui "$s" >/dev/null
    python3 -I - "$(run_dir)/ui-$(name_of "$s").xml" "$re" "$nth" <<'PY'
import re, sys, xml.etree.ElementTree as ET
# "desc:RE" matches only content descriptions, "text:RE" only texts, plain RE either.
kind, _, raw = sys.argv[2].partition(":") if sys.argv[2].startswith(("desc:", "text:")) else ("", "", sys.argv[2])
pattern = re.compile(raw)
hits = [node for node in ET.parse(sys.argv[1]).iter("node")
        if (kind != "desc" and pattern.search(node.get("text", ""))) or
        (kind != "text" and pattern.search(node.get("content-desc", "")))]
try:
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", hits[int(sys.argv[3])].get("bounds")))
except IndexError:
    sys.exit(1)
print((x1 + x2) // 2, (y1 + y2) // 2)
PY
}

tap() { adb -s "$1" shell input tap "$2" "$3" </dev/null; }

tap_text() {
    local s=$1 at
    at=$(find_text "$s" "$2" "${3:-0}") || { echo "$(name_of "$s"): no node matches '$2'" >&2; return 1; }
    # shellcheck disable=SC2086
    tap "$s" $at
}

wait_text() {
    local s=$1 re=$2 seconds=${3:-30} end=$((SECONDS + ${3:-30}))
    while ((SECONDS < end)); do
        find_text "$s" "$re" >/dev/null && return 0
        sleep 1
    done
    echo "$(name_of "$s"): '$re' not shown within ${seconds}s" >&2
    return 1
}

type_text() { adb -s "$1" shell input text "${2// /%s}" </dev/null; }
# Empties the focused text field.
clear_field() {
    local dels
    dels=$(printf 'KEYCODE_DEL %.0s' {1..60})
    # shellcheck disable=SC2086
    adb -s "$1" shell input keyevent KEYCODE_MOVE_END $dels </dev/null
}
key() { adb -s "$1" shell input keyevent "KEYCODE_${2#KEYCODE_}" </dev/null; }

shot_one() {
    local s=$1 label=$2 file
    file=$(run_dir)/$(date +%H%M%S)-$label-$(name_of "$s").png
    adb -s "$s" exec-out screencap -p </dev/null >"$file"
    echo "$file"
}

rec_start() {
    local label=$1 limit=${2:-180} dir s
    dir=$(run_dir)
    for s in "$A" "$B"; do
        adb -s "$s" shell rm -f /sdcard/smoke.mp4 </dev/null
        adb -s "$s" shell screenrecord --bit-rate 6000000 --time-limit "$limit" /sdcard/smoke.mp4 </dev/null >/dev/null 2>&1 &
        echo "$! $(date +%H%M%S)-$label-$(name_of "$s").mp4" >"$dir/.rec-$(name_of "$s")"
    done
}

rec_stop() {
    local dir s pid file
    dir=$(run_dir)
    for s in "$A" "$B"; do
        [[ -f $dir/.rec-$(name_of "$s") ]] || continue
        read -r pid file <"$dir/.rec-$(name_of "$s")"
        adb -s "$s" shell pkill -INT screenrecord </dev/null || true
        while kill -0 "$pid" 2>/dev/null; do sleep 0.3; done
        sleep 1
        adb -s "$s" pull /sdcard/smoke.mp4 "$dir/$file" </dev/null >/dev/null
        rm -f "$dir/.rec-$(name_of "$s")"
        echo "$dir/$file"
    done
}

logcat_save() {
    local dir s n pids
    dir=$(run_dir)
    for s in "$A" "$B"; do
        n=$(name_of "$s")
        adb -s "$s" logcat -d -v threadtime </dev/null >"$dir/logcat-$n.txt"
        pids=$(adb -s "$s" shell pidof "$PKG" </dev/null | tr -d '\r' || true)
        echo "== $n: $PKG warnings/errors (pid ${pids:-not running}), crashes and ANRs"
        # shellcheck disable=SC2086
        python3 -I - "$dir/logcat-$n.txt" "$PKG" $pids <<'PY' | tail -n 60
import sys
path, package, pids = sys.argv[1], sys.argv[2], set(sys.argv[3:])
for line in open(path, errors="replace"):
    parts = line.split()
    if len(parts) < 6:
        continue
    ours = parts[2] in pids and parts[4] in ("W", "E", "F")
    crash = ("FATAL" in line or "ANR in" in line or "AndroidRuntime" in line) and package in line
    if ours or crash:
        print(line.rstrip())
PY
    done
}

# Signs one device in, starting from whatever screen a fresh install shows.
signin_one() {
    local s=$1 email=$2
    launch "$s"
    wait_text "$s" '(?i)sign in|e-?mail|lobby|games' 60
    if ! find_text "$s" '(?i)^e-?mail' >/dev/null; then
        echo "$(name_of "$s"): already signed in (or no sign-in form shown)"; return 0
    fi
    tap_text "$s" 'desc:(?i)^e-?mail$'
    clear_field "$s"
    type_text "$s" "$email"
    tap_text "$s" 'desc:(?i)^password$'
    clear_field "$s"
    type_text "$s" "$PASSWORD"
    # The heading also reads "Sign in"; the button is the last match.
    tap_text "$s" 'text:(?i)^sign in$' -1
}

case "${1:-}" in
up)
    check_host
    devices_up
    apk=
    if [[ ${2:-} == --no-build ]]; then apk=$(apk_path); else apk=$(build | tail -n 1); fi
    both install_one "$apk"
    dir=$(new_run)
    signin_one "$A" a@a.com
    signin_one "$B" b@b.com
    echo "Run directory: $dir" ;;
build) build ;;
install) apk=${2:-$(apk_path)}; both install_one "$apk" ;;
devices) check_host; devices_up ;;
new-run) new_run ;;
signin) signin_one "$A" a@a.com; signin_one "$B" b@b.com ;;
shot) shot_one "$A" "${2:?label}"; shot_one "$B" "$2" ;;
rec) rec_start "${2:?label}" "${3:-10}"; sleep "${3:-10}"; rec_stop ;;
rec-start) rec_start "${2:?label}" ;;
rec-stop) rec_stop ;;
ui) ui "$(serial_of "${2:?a|b}")" "${3:-}" ;;
tap) tap "$(serial_of "${2:?a|b}")" "${3:?x}" "${4:?y}" ;;
tap-text) tap_text "$(serial_of "${2:?a|b}")" "${3:?regex}" "${4:-0}" ;;
wait-text) wait_text "$(serial_of "${2:?a|b}")" "${3:?regex}" "${4:-30}" ;;
type) type_text "$(serial_of "${2:?a|b}")" "${3:?text}" ;;
key) key "$(serial_of "${2:?a|b}")" "${3:?key}" ;;
logcat) logcat_save ;;
dir) run_dir ;;
*) awk 'NR > 1 && !/^#/ { exit } NR > 1 { print }' "$0" >&2; exit 2 ;;
esac
