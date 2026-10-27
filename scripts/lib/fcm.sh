#!/usr/bin/env bash
# Sourced by APK builders. Firebase files live outside the repo; without them builds stay non-FCM (polling only).
ATW_FIREBASE=${ATW_FIREBASE:-$HOME/.config/atw-firebase}

# Fills fcm_args with the four public Gradle identifiers for one package, or leaves it empty.
atw_fcm_args() {
    local services=$1 package=$2
    fcm_args=()
    [[ -f $services ]] || return 0
    mapfile -t fcm_args < <(python3 -I - "$services" "$package" <<'PY'
import json, sys
services, package = sys.argv[1:]
data = json.load(open(services))
client = next(c for c in data["client"] if c["client_info"]["android_client_info"]["package_name"] == package)
print("-PatwFcmApplicationId=" + client["client_info"]["mobilesdk_app_id"])
print("-PatwFcmProject=" + data["project_info"]["project_id"])
print("-PatwFcmApiKey=" + client["api_key"][0]["current_key"])
print("-PatwFcmSender=" + data["project_info"]["project_number"])
PY
    )
    (( ${#fcm_args[@]} == 4 )) || { echo "Could not read Firebase identifiers for $package from $services." >&2; return 1; }
}
