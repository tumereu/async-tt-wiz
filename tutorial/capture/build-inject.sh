#!/usr/bin/env bash
# Builds raw/inject.jar (dex) from Inject.java with the Android SDK.
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
rm -rf raw/java && mkdir -p raw/java
javac -source 8 -target 8 -nowarn -cp "$SDK/platforms/android-36/android.jar" -d raw/java Inject.java
"$SDK/build-tools/36.0.0/d8" --min-api 29 --output raw/inject.jar raw/java/Inject.class
