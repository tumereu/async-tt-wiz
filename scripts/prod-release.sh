#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD
ORIGIN=https://pi5-8gb.tail27cccc.ts.net:10000
KEYS=$HOME/.config/atw-release
STATE=${XDG_DATA_HOME:-$HOME/.local/share}/atw-release
PI_SETUP=${ATW_PI_SETUP:-$HOME/projects/pi5-setup}
umask 077

die() { echo "$*" >&2; exit 1; }
usage() { die "Usage: $0 init-keystore | release <full-commit-sha> | install"; }
(( $# >= 1 )) || usage
case "$1" in
init-keystore)
    (( $# == 1 )) || usage
    [[ ! -e $KEYS && ! -L $KEYS ]] || die "Refusing: $KEYS already exists. Do not replace the production key."
    keytool=${JAVA_HOME:+$JAVA_HOME/bin/}keytool
    command -v "$keytool" >/dev/null || die "Set JAVA_HOME to a JDK, or put keytool on PATH."
    mkdir -p "$(dirname "$KEYS")"
    mkdir -m 700 "$KEYS" || die "Refusing to overwrite an existing signing directory."
    openssl rand -hex 32 > "$KEYS/store-password"
    cp "$KEYS/store-password" "$KEYS/key-password"
    "$keytool" -genkeypair -keystore "$KEYS/release.p12" -storetype PKCS12 \
        -storepass:file "$KEYS/store-password" -keypass:file "$KEYS/key-password" \
        -alias atw-release -keyalg RSA -keysize 4096 -validity 36500 \
        -dname 'CN=Async Tabletop Wizards' -noprompt
    chmod 600 "$KEYS"/*
    echo "!!! BACK UP $KEYS (KEYSTORE AND BOTH PASSWORD FILES) NOW !!!"
    echo "!!! LOSING THIS KEY MEANS EVERY USER MUST UNINSTALL THE APP, LOSING LOCAL DATA !!!"
    exit ;;
release) (( $# == 2 )) || usage ;;
install) (( $# == 1 )) || usage ;;
*) usage ;;
esac
mkdir -p "$STATE"
STATE=$(cd "$STATE" && pwd -P)
exec 9>"$STATE/release.lock"
flock -n 9 || die "Another production release/install is running."
source "$ROOT/scripts/lib/device-adb.sh"

install_phone() {
    if atw_find_phone "${ATW_ADB_SERIAL:-94d4fd56}"; then
        atw_install_apk "$1" release
    else
        echo "Phone not connected/authorized; installation skipped. Run scripts/prod-release.sh install later."
    fi
}

verify_apk() {
    python3 - "$1" "$2" <<'PY'
import hashlib, json, pathlib, sys
apk, latest = map(pathlib.Path, sys.argv[1:])
m = json.loads(latest.read_text())
assert apk.stat().st_size == m["size"], "APK size mismatch"
with apk.open("rb") as stream:
    assert hashlib.file_digest(stream, "sha256").hexdigest() == m["sha256"], "APK checksum mismatch"
PY
}

if [[ $1 == install ]]; then
    download=$(mktemp -d "$STATE/download-XXXXXX")
    if curl --fail --silent --show-error --max-time 15 "$ORIGIN/app/latest" -o "$download/latest.json" &&
        curl --fail --silent --show-error --max-time 600 "$ORIGIN/app/apk" -o "$download/app.apk" &&
        verify_apk "$download/app.apk" "$download/latest.json"; then
        install_phone "$download/app.apk"
    else
        [[ -f $STATE/last-built ]] || die "Published APK unavailable and no last-built APK exists."
        read -r commit < "$STATE/last-built"
        [[ $commit =~ ^[0-9a-f]{40}$ ]] || die "Invalid last-built revision."
        apk=$STATE/releases/$commit/app.apk
        verify_apk "$apk" "$STATE/releases/$commit/latest.json"
        echo "Published APK unavailable; using last built production APK ($commit)."
        install_phone "$apk"
    fi
    rm -f -- "$download/app.apk" "$download/latest.json"
    rmdir "$download"
    exit
fi

commit=$2
[[ $commit =~ ^[0-9a-f]{40}$ ]] || die "Release requires an explicit full 40-character owner-approved commit SHA."
[[ $(git rev-parse --verify "$commit^{commit}") == "$commit" ]] || die "Commit not found."
for file in release.p12 store-password key-password; do
    [[ -f $KEYS/$file ]] || die "Missing $KEYS/$file. Initialize and back up the production signing key first."
    [[ $(stat -c %a "$KEYS/$file") == 600 ]] || die "Signing files must have mode 600: $KEYS/$file"
done
command -v direnv >/dev/null || die "direnv is required for the existing Pi SSH/deployment environment."
[[ -f $PI_SETUP/async-tt-wiz.yml ]] || die "Pi setup checkout not found at $PI_SETUP."
scratch=$(mktemp -d "${TMPDIR:-/tmp/opencode}/atw-prod-release-XXXXXX")
worktree=$scratch/source
cleanup() {
    # Remove only the disposable worktree this invocation created, including its build output.
    if [[ -d $worktree ]]; then git -C "$ROOT" worktree remove --force "$worktree"; fi
    rm -f -- "$scratch/published.json"
    rmdir "$scratch"
}
trap cleanup EXIT
git worktree add --detach "$worktree" "$commit"
code=$(python3 - "$worktree/release/version.json" <<'PY'
import json, sys
print(json.load(open(sys.argv[1]))["allocations"][-1]["versionCode"])
PY
)
status=$(curl --silent --show-error --max-time 20 -o "$scratch/published.json" -w '%{http_code}' "$ORIGIN/app/latest") ||
    die "Cannot read published production version; release refused."
case "$status" in
200) published=$(python3 - "$scratch/published.json" <<'PY'
import json, sys
code = json.load(open(sys.argv[1]))["versionCode"]
assert isinstance(code, int) and code > 0
print(code)
PY
) ;;
404) published=0 ;;
*) die "Production update check returned HTTP $status; release refused." ;;
esac
(( code > published )) || die "VersionCode $code must be greater than published $published. Allocate a new code in release/version.json and approve that commit."
export ATW_RELEASE_STORE_FILE=$KEYS/release.p12
export ATW_RELEASE_STORE_PASSWORD_FILE=$KEYS/store-password
export ATW_RELEASE_KEY_ALIAS=atw-release
export ATW_RELEASE_KEY_PASSWORD_FILE=$KEYS/key-password
"$worktree/scripts/gradle.sh" :androidApp:assembleRelease -PatwSelfHosted=true \
    "-PatwApiOrigin=$ORIGIN" "-PatwResetOrigin=$ORIGIN" -PatwAccountSwitcher=false -PatwCatalogTestOrigin=
artifact=$STATE/releases/$commit
mkdir -p "$artifact"
cp "$worktree/androidApp/build/outputs/apk/release/androidApp-release.apk" "$artifact/app.apk"
python3 - "$artifact/app.apk" "$worktree/androidApp/build/outputs/apk/release/output-metadata.json" "$code" "$artifact/latest.json" <<'PY'
import hashlib, json, pathlib, sys
apk, output = map(pathlib.Path, sys.argv[1:3])
build = json.loads(output.read_text())
entry, = build["elements"]
assert build["applicationId"] == "org.asyncttwiz.android"
assert entry["versionCode"] == int(sys.argv[3])
with apk.open("rb") as stream:
    digest = hashlib.file_digest(stream, "sha256").hexdigest()
pathlib.Path(sys.argv[4]).write_text(json.dumps(dict(versionCode=entry["versionCode"],
    versionName=entry["versionName"], sha256=digest, size=apk.stat().st_size)) + "\n")
PY
printf '%s\n' "$commit" > "$STATE/last-built.new"
mv "$STATE/last-built.new" "$STATE/last-built"
# ATW_VERSION and controller source both pin the server build to the approved commit.
(
    cd "$PI_SETUP"
    ATW_VERSION=$commit direnv exec . ansible-playbook async-tt-wiz.yml -e "atw_controller_source=$worktree"
)
# No publication before the entire playbook (including public health checks) succeeds.
direnv exec "$PI_SETUP" bash -s -- "$artifact/app.apk" "$artifact/latest.json" <<'SH'
set -euo pipefail
[[ ${PI_HOST:-} =~ ^[A-Za-z0-9._:-]+$ && ${PI_USER:-} =~ ^[A-Za-z0-9_-]+$ ]] || {
    echo 'PI_HOST/PI_USER must match the existing inventory SSH environment.' >&2; exit 1;
}
target=$PI_USER@$PI_HOST
hash=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["sha256"])' "$2")
[[ $hash =~ ^[0-9a-f]{64}$ ]]
remote=$(ssh -o BatchMode=yes "$target" mktemp -d /tmp/atw-release.XXXXXXXX)
[[ $remote =~ ^/tmp/atw-release\.[A-Za-z0-9]+$ ]]
scp -o BatchMode=yes "$1" "$target:$remote/$hash.apk"
scp -o BatchMode=yes "$2" "$target:$remote/latest.json"
ssh -o BatchMode=yes "$target" bash -s -- "$remote" "$hash" <<'REMOTE'
set -euo pipefail
source=$1
hash=$2
destination=/srv/async-tt-wiz/app-release
sudo -n test -d "$destination"
sudo -n install -m 0644 "$source/$hash.apk" "$destination/$hash.apk.new"
sudo -n mv "$destination/$hash.apk.new" "$destination/$hash.apk"
sudo -n install -m 0644 "$source/latest.json" "$destination/latest.json.new"
sudo -n mv "$destination/latest.json.new" "$destination/latest.json"
rm -f -- "$source/$hash.apk" "$source/latest.json"
rmdir "$source"
REMOTE
SH
echo "Published production $commit (versionCode $code)."
install_phone "$artifact/app.apk"
