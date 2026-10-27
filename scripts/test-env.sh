#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD
STATE=${XDG_DATA_HOME:-$HOME/.local/share}/atw-test
ORIGIN=https://desktop-b2kalvm.tail27cccc.ts.net:10443
umask 077
mkdir -p "$STATE/server"
STATE=$(cd "$STATE" && pwd -P)
chmod 700 "$STATE" "$STATE/server"

running() {
    local pid
    [[ -f $STATE/server.pid ]] || return 1
    read -r pid < "$STATE/server.pid"
    [[ $pid =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null &&
        ps -p "$pid" -o args= | grep -Fq -- "$STATE/server/"
}

stop_server() {
    local pid i
    if running; then
        read -r pid < "$STATE/server.pid"
        kill "$pid"
        for ((i=0; i<150; i++)); do
            running || break
            sleep 0.2
        done
        if running; then echo "Could not stop test server (PID $pid)." >&2; return 1; fi
    fi
    rm -f "$STATE/server.pid"
}

keepalive() {
    grep -qi microsoft /proc/sys/kernel/osrelease || return 0
    # A simple distro name also avoids Windows command-line quoting ambiguities.
    [[ ${WSL_DISTRO_NAME:-} =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Missing/unsupported WSL_DISTRO_NAME." >&2; return 1; }
    if [[ $1 == down ]]; then
        # Killing wsl.exe alone can leave its Linux child running. Only this user's
        # exact tagged sleep is ours; never terminate a distro or other WSL sessions.
        pkill -u "$(id -u)" -f '^atw-test-keepalive infinity$' || [[ $? == 1 ]]
    fi
    powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass \
        -File "$(wslpath -w "$ROOT/scripts/test-env-keepalive.ps1")" \
        -Action "$1" -Distro "$WSL_DISTRO_NAME" -PidFile "$(wslpath -w "$STATE/keepalive.windows.pid")"
}

case "${1:-up}" in
logs) touch "$STATE/server.log"; exec tail -n 80 -F "$STATE/server.log" ;;
status)
    if running; then echo "Test server: running (PID $(cat "$STATE/server.pid"))"; else echo "Test server: stopped"; fi
    curl --silent --fail --max-time 3 http://127.0.0.1:18091/health/ready || true
    echo
    docker ps -a --filter 'name=^atw-test-(db|mail)$' --format '{{.Names}}: {{.Status}}'
    keepalive status
    exit ;;
up|down) ;;
*) echo "Usage: $0 [up|down|logs|status]" >&2; exit 2 ;;
esac
exec 9>"$STATE/deploy.lock"
flock -n 9 || { echo "Another test environment command is running." >&2; exit 1; }

if [[ ${1:-up} == down ]]; then
    stop_server
    for name in atw-test-mail atw-test-db; do
        if docker container inspect "$name" >/dev/null 2>&1; then docker stop "$name" >/dev/null; fi
    done
    keepalive down
    echo "Test server, containers and keep-alive stopped; data and Tailscale routes kept."
    exit
fi

# Build before interrupting the existing server. The live JVM uses only its own
# immutable install copy, never Gradle's mutable server/build/install directory.
mkdir -p "$STATE/app-release"
# Reserve before building, so even rapid runs or a backwards clock never reuse a code.
test_code=$(python3 - "$STATE/test-version-code" "$STATE/app-release/latest.json" <<'PY'
import json, pathlib, sys, time
counter, latest = map(pathlib.Path, sys.argv[1:])
previous = int(counter.read_text()) if counter.exists() else 0
published = json.loads(latest.read_text())["versionCode"] if latest.exists() else 0
code = max(int(time.time()) - 1577836800, previous + 1, published + 1)
assert 0 < code <= 2100000000
counter.write_text(str(code) + "\n")
print(code)
PY
)
./scripts/gradle.sh :server:installDist :androidApp:assemblePersonalTest "-PatwTestVersionCode=$test_code"
APK=$ROOT/androidApp/build/outputs/apk/personalTest/androidApp-personalTest.apk
[[ -f $APK ]]
version=$(date -u +%Y%m%dT%H%M%S)-$(git rev-parse --short=12 HEAD)
snapshot=$(mktemp -d "$STATE/server/$version-XXXXXX")
cp -a server/build/install/server/. "$snapshot/"

[[ -s $STATE/db-password ]] || openssl rand -hex 24 > "$STATE/db-password"
[[ -s $STATE/reset-mail-key ]] || openssl rand -base64 32 > "$STATE/reset-mail-key"
chmod 600 "$STATE/db-password" "$STATE/reset-mail-key"
export DB_URL=jdbc:postgresql://127.0.0.1:55433/atw_test DB_USER=atw_test
export DB_PASSWORD_FILE="$STATE/db-password" RESET_MAIL_KEY_FILE="$STATE/reset-mail-key"
export PUBLIC_BASE_URL=$ORIGIN RESET_BASE_URL=$ORIGIN
export SERVER_ADDRESS=127.0.0.1 SERVER_PORT=18090 HEALTH_ADDRESS=127.0.0.1 HEALTH_PORT=18091
export APP_RELEASE_DIR="$STATE/app-release"
export SMTP_HOST=127.0.0.1 SMTP_PORT=11026 SMTP_TLS=local-plaintext SMTP_FROM=test@atw.invalid
unset SMTP_USERNAME_FILE SMTP_PASSWORD_FILE
if grep -qi microsoft /proc/sys/kernel/osrelease; then
    export JAVA_OPTS="${JAVA_OPTS:+$JAVA_OPTS }-Djava.net.preferIPv4Stack=true"
fi

if ! docker container inspect atw-test-db >/dev/null 2>&1; then
    image=$(sed -n 's/^postgres=//p' server/database-images.properties)
    docker run -d --name atw-test-db -p 127.0.0.1:55433:5432 \
        -v atw-test-db-data:/var/lib/postgresql/data \
        --mount "type=bind,src=$DB_PASSWORD_FILE,dst=/run/secrets/db-password,readonly" \
        -e POSTGRES_USER=atw_test -e POSTGRES_DB=atw_test \
        -e POSTGRES_PASSWORD_FILE=/run/secrets/db-password "$image" >/dev/null
fi
docker start atw-test-db >/dev/null
for ((i=0; i<60; i++)); do
    if docker exec atw-test-db pg_isready -U atw_test -d atw_test >/dev/null 2>&1; then break; fi
    sleep 1
done
docker exec atw-test-db pg_isready -U atw_test -d atw_test
if ! docker container inspect atw-test-mail >/dev/null 2>&1; then
    docker run -d --name atw-test-mail -p 127.0.0.1:11026:1025 -p 127.0.0.1:18026:8025 \
        -v atw-test-mail-data:/data -e MP_DATABASE=/data/mailpit.db axllent/mailpit:v1.27.0 >/dev/null
fi
docker start atw-test-mail >/dev/null
keepalive up
stop_server
"$snapshot/bin/server" migrate > "$STATE/migrate.log" 2>&1 || {
    tail -n 30 "$STATE/migrate.log" >&2; exit 1;
}
(
    cd "$snapshot"
    exec nohup setsid "$snapshot/bin/server" serve
) > "$STATE/server.log" 2>&1 < /dev/null 9>&- &
echo $! > "$STATE/server.pid"
# Give the launcher time to exec Java before checking its command-line marker.
sleep 1
ready=false
for ((i=0; i<90; i++)); do
    if ! running; then break; fi
    if curl --silent --fail --max-time 2 http://127.0.0.1:18091/health/ready >/dev/null; then ready=true; break; fi
    sleep 1
done
if [[ $ready != true ]]; then
    echo "Test server did not become ready; see $STATE/server.log" >&2
    tail -n 30 "$STATE/server.log" >&2
    stop_server
    exit 1
fi

# Immutable APK first, atomic metadata last; a running client never sees a partial release.
python3 - "$APK" "$ROOT/androidApp/build/outputs/apk/personalTest/output-metadata.json" "$APP_RELEASE_DIR" "$test_code" <<'PY'
import hashlib, json, os, pathlib, shutil, sys
apk, output, root = map(pathlib.Path, sys.argv[1:4])
entry, = json.loads(output.read_text())["elements"]
assert entry["versionCode"] == int(sys.argv[4])
with apk.open("rb") as stream:
    digest = hashlib.file_digest(stream, "sha256").hexdigest()
target = root / (digest + ".apk")
shutil.copyfile(apk, root / "apk.part")
os.replace(root / "apk.part", target)
metadata = dict(versionCode=entry["versionCode"], versionName=entry["versionName"], sha256=digest, size=target.stat().st_size)
(root / "latest.json.new").write_text(json.dumps(metadata) + "\n")
os.replace(root / "latest.json.new", root / "latest.json")
PY

# Prune only our timestamped install snapshots, after the new server is healthy.
mapfile -t versions < <(find "$STATE/server" -mindepth 1 -maxdepth 1 -type d -name '20*-*' ! -path "$snapshot" | sort -r)
for old in "${versions[@]:1}"; do
    rm -r -- "$old"
done
echo "Test API: $ORIGIN | Health: http://127.0.0.1:18091/health/ready"
echo "Mailpit: http://127.0.0.1:18026 | PostgreSQL: 127.0.0.1:55433/atw_test"
echo "APK: $APK"
echo "State, secrets and logs: $STATE"
if [[ ${ATW_TEST_SKIP_TAILSCALE:-0} != 1 ]]; then
    # Update only this listener; never reset Serve or change 443/8443.
    tailscale serve --bg --https=10443 http://127.0.0.1:18090
fi
if [[ ${ATW_TEST_SKIP_INSTALL:-0} != 1 ]]; then
    source "$ROOT/scripts/lib/device-adb.sh"
    if atw_find_phone && atw_install_apk "$APK" personalTest; then
        echo "Installed ATW Test."
    else
        echo "Phone installation skipped/failed; the test server is still running." >&2
        echo "Install later: ATW_ADB_SERIAL=94d4fd56 scripts/test-env.sh (or adb install -r '$APK')."
    fi
fi
