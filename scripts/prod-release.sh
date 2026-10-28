#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD
ORIGIN=https://atw.dotfresh.dev
KEYS=$HOME/.config/atw-release
STATE=${XDG_DATA_HOME:-$HOME/.local/share}/atw-release
PI_SETUP=${ATW_PI_SETUP:-$HOME/projects/pi5-setup}
GITHUB_REPO=tumereu/async-tt-wiz
GITHUB_API=https://api.github.com/repos/$GITHUB_REPO
GITHUB_UPLOADS=https://uploads.github.com/repos/$GITHUB_REPO
umask 077

die() { echo "$*" >&2; exit 1; }
usage() { die "Usage: $0 init-keystore | release <full-commit-sha | tag> | install"; }
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
github-selftest) (( $# == 1 )) || usage ;; # Developer check of the GitHub mirror steps; publishes nothing lasting.
*) usage ;;
esac
mkdir -p "$STATE"
STATE=$(cd "$STATE" && pwd -P)
exec 9>"$STATE/release.lock"
flock -n 9 || die "Another production release/install is running."
source "$ROOT/scripts/lib/device-adb.sh"

install_phone() {
    if atw_find_phone "${ATW_ADB_SERIAL:-94d4fd56}"; then
        local log
        log=$(atw_install_apk "$1" release 2>&1) && { echo "$log"; return; }
        echo "$log"
        # A debug/test-signed app with the production package id cannot be upgraded in place.
        [[ $log == *INSTALL_FAILED_UPDATE_INCOMPATIBLE* ]] || return 1
        read -r -p "The installed org.asyncttwiz.android has a different signature. Uninstall it (deletes its local data and catalog)? [y/N] " answer
        [[ $answer == [yY] ]] || { echo "Left the existing app installed."; return 1; }
        "${adb_command[@]}" -s "$serial" uninstall org.asyncttwiz.android
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

# GitHub Releases mirror: every production APK is also published there as a second browser download.
# All steps are idempotent so an interrupted release can simply be rerun. Temporary files live in $GH_DIR.
gh_auth() {
    local token=$KEYS/github-token
    [[ -f $token ]] || die "Missing $token: a GitHub token that may create releases in $GITHUB_REPO."
    [[ $(stat -c %a "$token") == 600 ]] || die "The GitHub token must have mode 600: $token"
    # curl reads the token from a private config file, so it never appears on a command line.
    python3 - "$token" "$GH_DIR/github-auth" <<'PY' || die "The GitHub token file is empty or malformed: $token"
import pathlib, re, sys
token = pathlib.Path(sys.argv[1]).read_text().strip()
if not re.fullmatch(r"[A-Za-z0-9_]{20,255}", token): sys.exit(1)
pathlib.Path(sys.argv[2]).write_text(f'header = "Authorization: Bearer {token}"\n')
PY
}

# Prints the HTTP status; the body is left in $GH_DIR/github-response.
gh_call() {
    local method=$1 url=$2; shift 2
    curl --silent --show-error --max-time 900 --config "$GH_DIR/github-auth" -o "$GH_DIR/github-response" -w '%{http_code}' \
        -H 'Accept: application/vnd.github+json' -H 'X-GitHub-Api-Version: 2022-11-28' -X "$method" "$@" "$url"
}
gh_fail() { die "GitHub: $1 failed (HTTP $2): $(head -c 400 "$GH_DIR/github-response" 2>/dev/null)"; }
gh_json() { python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$GH_DIR/github-response" "$1"; }

# Refuses when tag $1 exists, locally or on origin, at a commit other than $2. Sets gh_remote_tag.
gh_tag_check() {
    local tag=$1 commit=$2 local_commit refs
    if local_commit=$(git rev-parse --quiet --verify "refs/tags/$tag^{commit}"); then
        [[ $local_commit == "$commit" ]] || die "Tag $tag already points to $local_commit, not $commit."
    fi
    refs=$(git ls-remote --tags origin "refs/tags/$tag" "refs/tags/$tag^{}") || die "Cannot reach origin to check tag $tag."
    # An annotated tag is listed twice; its peeled (^{}) line names the commit.
    gh_remote_tag=$(awk -v t="refs/tags/$tag" '$2 == t && !p { c = $1 } $2 == t "^{}" { c = $1; p = 1 } END { print c }' <<<"$refs")
    [[ -z $gh_remote_tag || $gh_remote_tag == "$commit" ]] || die "Tag $tag on origin points to $gh_remote_tag, not $commit."
}

# Makes tag $1 exist at commit $2 both locally and on origin (pushing it publishes that commit).
gh_tag() {
    gh_tag_check "$1" "$2"
    if [[ -n $gh_remote_tag ]]; then
        git rev-parse --quiet --verify "refs/tags/$1" >/dev/null ||
            git fetch --quiet --no-tags origin "refs/tags/$1:refs/tags/$1"
    else
        git rev-parse --quiet --verify "refs/tags/$1" >/dev/null || git tag -a "$1" -m "$3" "$2"
        git push --quiet origin "refs/tags/$1" || die "Pushing tag $1 to origin failed."
    fi
}

# Downloads a public URL anonymously ($4 attempts, 10 s apart) and checks it is exactly the expected file.
# After a same-named asset is replaced, github.com keeps redirecting to the old copy for about two minutes.
gh_verify() {
    local url=$1 size=$2 sha=$3 attempts=${4:-1} attempt
    for (( attempt = 1; attempt <= attempts; attempt++ )); do
        rm -f -- "$GH_DIR/github-download"
        if curl --fail --silent --show-error --location --proto-redir =https --max-time 900 -o "$GH_DIR/github-download" "$url" &&
            [[ $(stat -c %s "$GH_DIR/github-download") == "$size" ]] &&
            [[ $(sha256sum "$GH_DIR/github-download" | cut -d' ' -f1) == "$sha" ]]; then
            rm -f -- "$GH_DIR/github-download"
            return 0
        fi
        (( attempt == attempts )) || { echo "Public GitHub download not current yet; retrying ($attempt/$attempts)."; sleep 10; }
    done
    rm -f -- "$GH_DIR/github-download"
    return 1
}

# Publishes file $3 as async-tabletop-wizards-<name $4>.apk in the release for tag $1 (at commit $2),
# reusing an identical existing asset, and sets gh_url to its verified public download URL.
gh_publish() {
    local tag=$1 commit=$2 file=$3 name=$4 prerelease=$5 size sha asset payload status release existing id existing_size existing_url
    size=$(stat -c %s "$file")
    sha=$(sha256sum "$file" | cut -d' ' -f1)
    asset=async-tabletop-wizards-${name//[^0-9A-Za-z._-]/_}.apk
    gh_tag "$tag" "$commit" "Release $name"
    payload=$(python3 - "$tag" "$name" "$sha" "$prerelease" <<'PY'
import json, sys
tag, name, sha, prerelease = sys.argv[1:]
print(json.dumps(dict(tag_name=tag, name=f"Async Tabletop Wizards {name}",
    body=f"Android app {name}.\n\nSHA-256 of the APK: `{sha}`", prerelease=prerelease == "true",
    make_latest="false" if prerelease == "true" else "true")))
PY
)
    status=$(gh_call GET "$GITHUB_API/releases/tags/$tag")
    case $status in
    200) release=$(gh_json id)
        status=$(gh_call PATCH "$GITHUB_API/releases/$release" --data-binary "$payload")
        [[ $status == 200 ]] || gh_fail "updating release $tag" "$status" ;;
    404) status=$(gh_call POST "$GITHUB_API/releases" --data-binary "$payload")
        [[ $status == 201 ]] || gh_fail "creating release $tag" "$status"
        release=$(gh_json id)
        echo "Created GitHub release $tag." ;;
    *) gh_fail "looking up release $tag" "$status" ;;
    esac
    [[ $release =~ ^[0-9]+$ ]] || die "GitHub: unexpected release id."
    existing=$(python3 - "$GH_DIR/github-response" "$asset" <<'PY'
import json, sys
found = [a for a in json.load(open(sys.argv[1]))["assets"] if a["name"] == sys.argv[2]]
print(f'{found[0]["id"]} {found[0]["size"]} {found[0]["browser_download_url"]}' if found else "")
PY
)
    gh_url=
    if [[ -n $existing ]]; then
        read -r id existing_size existing_url <<<"$existing"
        if [[ $existing_size == "$size" ]] && gh_verify "$existing_url" "$size" "$sha"; then
            echo "GitHub release $tag already has an identical $asset."
            gh_url=$existing_url
        else
            echo "Replacing the different $asset in GitHub release $tag."
            status=$(gh_call DELETE "$GITHUB_API/releases/assets/$id")
            [[ $status == 204 ]] || gh_fail "deleting the old $asset" "$status"
        fi
    fi
    if [[ -z $gh_url ]]; then
        echo "Uploading $asset to GitHub release $tag..."
        status=$(gh_call POST "$GITHUB_UPLOADS/releases/$release/assets?name=$asset" \
            -H 'Content-Type: application/vnd.android.package-archive' --upload-file "$file")
        [[ $status == 201 ]] || gh_fail "uploading $asset" "$status"
        gh_url=$(gh_json browser_download_url)
        gh_verify "$gh_url" "$size" "$sha" 30 || die "GitHub: the public download of $asset does not match the built APK."
    fi
    [[ $gh_url == https://* && ${#gh_url} -le 500 ]] || die "GitHub: unexpected download URL $gh_url"
    echo "GitHub download verified: $gh_url"
}

# Deletes a self-test release and its tag from GitHub and locally; refuses anything but a test tag.
gh_delete_test() {
    local tag=$1 status
    [[ $tag == test-mirror-* ]] || die "Refusing to delete $tag."
    status=$(gh_call GET "$GITHUB_API/releases/tags/$tag")
    if [[ $status == 200 ]]; then
        status=$(gh_call DELETE "$GITHUB_API/releases/$(gh_json id)")
        [[ $status == 204 ]] || echo "Could not delete GitHub release $tag (HTTP $status); delete it by hand." >&2
    fi
    if [[ -n $(git ls-remote --tags origin "refs/tags/$tag") ]]; then
        git push --quiet origin ":refs/tags/$tag" || echo "Could not delete tag $tag on origin; delete it by hand." >&2
    fi
    if git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null; then git tag -d "$tag" >/dev/null; fi
    echo "Removed test release $tag."
}

if [[ $1 == github-selftest ]]; then
    # Exercises create, upload, reuse and replace against a throwaway prerelease, then deletes it again.
    GH_DIR=$(mktemp -d "${TMPDIR:-/tmp}/atw-github-test-XXXXXX")
    tag=test-mirror-$(date +%Y%m%d%H%M%S)
    trap 'gh_delete_test "$tag" || true; rm -rf -- "$GH_DIR"' EXIT
    gh_auth
    # The tag points at a commit origin already has, so the test publishes nothing new.
    base=$(git ls-remote origin refs/heads/master | cut -f1)
    git cat-file -e "$base^{commit}" 2>/dev/null || die "Fetch origin first: $base is not available locally."
    printf 'mirror test 1\n' > "$GH_DIR/one"
    printf 'mirror test 9\n' > "$GH_DIR/same-size"
    printf 'mirror test, longer\n' > "$GH_DIR/longer"
    for file in one one same-size longer; do
        echo "== $file"
        gh_publish "$tag" "$base" "$GH_DIR/$file" test true
    done
    echo "GitHub self-test passed."
    exit
fi

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
        built=$STATE/releases/$commit/latest.json
        github=$(python3 -c 'import json, sys; print(json.load(open(sys.argv[1])).get("githubUrl", ""))' "$built")
        rm -f -- "$download/app.apk"
        if [[ $github == https://* ]] &&
            curl --fail --silent --show-error --location --proto-redir =https --max-time 600 "$github" -o "$download/app.apk" &&
            verify_apk "$download/app.apk" "$built"; then
            echo "Server download unavailable; using the GitHub release of $commit."
            install_phone "$download/app.apk"
        else
            apk=$STATE/releases/$commit/app.apk
            verify_apk "$apk" "$built"
            echo "Published APK unavailable; using last built production APK ($commit)."
            install_phone "$apk"
        fi
    fi
    rm -f -- "$download/app.apk" "$download/latest.json"
    rmdir "$download"
    exit
fi

commit=$2
if [[ ! $commit =~ ^[0-9a-f]{40}$ ]]; then
    # A tag from scripts/prep-release.sh (v1.2.3, or just 1.2.3) names the approved commit;
    # abbreviated SHAs stay refused.
    tag=$2
    git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null || tag=v$2
    commit=$(git rev-parse --quiet --verify "refs/tags/$tag^{commit}") ||
        die "Release requires an owner-approved tag (e.g. v1.2.3) or full 40-character commit SHA."
    echo "Tag $tag is commit $commit."
fi
[[ $(git rev-parse --verify "$commit^{commit}") == "$commit" ]] || die "Commit not found."
for file in release.p12 store-password key-password; do
    [[ -f $KEYS/$file ]] || die "Missing $KEYS/$file. Initialize and back up the production signing key first."
    [[ $(stat -c %a "$KEYS/$file") == 600 ]] || die "Signing files must have mode 600: $KEYS/$file"
done
command -v direnv >/dev/null || die "direnv is required for the existing Pi SSH/deployment environment."
[[ -f $PI_SETUP/async-tt-wiz.yml ]] || die "Pi setup checkout not found at $PI_SETUP."
scratch=$(mktemp -d "${TMPDIR:-/tmp}/atw-prod-release-XXXXXX")
worktree=$scratch/source
cleanup() {
    # Remove only the disposable worktree this invocation created, including its build output.
    if [[ -d $worktree ]]; then git -C "$ROOT" worktree remove --force "$worktree"; fi
    rm -f -- "$scratch/published.json" "$scratch"/github-*
    rmdir "$scratch"
}
trap cleanup EXIT
git worktree add --detach "$worktree" "$commit"
read -r code name < <(python3 - "$worktree/release/version.json" <<'PY'
import json, sys
last = json.load(open(sys.argv[1]))["allocations"][-1]
print(last["versionCode"], last["versionName"])
PY
)
# The GitHub Releases mirror needs a token and the release tag v<versionName> at this commit.
GH_DIR=$scratch
gh_auth
gh_tag_check "v$name" "$commit"
status=$(curl --silent --max-time 20 -o "$scratch/published.json" -w '%{http_code}' "$ORIGIN/app/latest") || true
case "$status" in
200) published=$(python3 - "$scratch/published.json" <<'PY'
import json, sys
code = json.load(open(sys.argv[1]))["versionCode"]
assert isinstance(code, int) and code > 0
print(code)
PY
) ;;
404) published=0 ;;
000|502|503|504|530) # Backend down (e.g. first release after a wipe; Cloudflare answers 502, or 530 without a tunnel): never go below a code this machine already built.
    published=$(python3 - "$STATE/releases" <<'PY'
import json, pathlib, sys
codes = [json.loads(p.read_text())["versionCode"] for p in pathlib.Path(sys.argv[1]).glob("*/latest.json")]
print(max(codes, default=0))
PY
)
    echo "Production backend unreachable; assuming published versionCode $published." ;;
*) die "Production update check returned HTTP $status; release refused." ;;
esac
(( code > published )) || die "VersionCode $code must be greater than published $published. Allocate a new code in release/version.json and approve that commit."
export ATW_RELEASE_STORE_FILE=$KEYS/release.p12
export ATW_RELEASE_STORE_PASSWORD_FILE=$KEYS/store-password
export ATW_RELEASE_KEY_ALIAS=atw-release
export ATW_RELEASE_KEY_PASSWORD_FILE=$KEYS/key-password
source "$ROOT/scripts/lib/fcm.sh"
atw_fcm_args "$ATW_FIREBASE/google-services-prod.json" org.asyncttwiz.android
# Firebase identifiers add the "fcm" flavor, which renames the task and output directory.
variant=release apk_dir=release
if (( ${#fcm_args[@]} )); then variant=fcmRelease apk_dir=fcm/release; fi
"$worktree/scripts/gradle.sh" ":androidApp:assemble${variant^}" -PatwSelfHosted=true \
    "-PatwApiOrigin=$ORIGIN" "-PatwResetOrigin=$ORIGIN" -PatwAccountSwitcher=false -PatwCatalogTestOrigin= "${fcm_args[@]}"
artifact=$STATE/releases/$commit
mkdir -p "$artifact"
cp "$worktree/androidApp/build/outputs/apk/$apk_dir/"*.apk "$artifact/app.apk"
python3 - "$artifact/app.apk" "$worktree/androidApp/build/outputs/apk/$apk_dir/output-metadata.json" "$code" "$artifact/latest.json" <<'PY'
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
built_name=$(python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))["versionName"])' "$artifact/latest.json")
[[ $built_name == "$name" ]] || die "Built versionName $built_name differs from release/version.json ($name)."
# A GitHub failure stops the release before anything reaches the Pi; rerunning is safe.
gh_publish "v$name" "$commit" "$artifact/app.apk" "$name" false
python3 - "$artifact/latest.json" "$gh_url" <<'PY'
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
path.write_text(json.dumps(json.loads(path.read_text()) | dict(githubUrl=sys.argv[2])) + "\n")
PY
# ATW_VERSION and controller source both pin the server build to the approved commit.
# The playbook publishes the APK only after the backend and public ingress checks succeed.
(
    cd "$PI_SETUP"
    ATW_VERSION=$commit direnv exec . ansible-playbook async-tt-wiz.yml \
        -e "atw_controller_source=$worktree" -e "atw_app_release_artifact=$artifact"
)
echo "Published production $commit (versionCode $code)."
install_phone "$artifact/app.apk"
