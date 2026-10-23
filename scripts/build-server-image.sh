#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

./scripts/gradle.sh :server:installDist

# A dedicated, ignored context keeps source, caches and operator secrets out of Docker.
context=server/build/docker-context
mkdir -p "$context/lib"
rm -f "$context"/lib/*.jar
cp server/build/install/server/lib/*.jar "$context/lib/"
cp server/Dockerfile "$context/Dockerfile"
cp server/image.dockerignore "$context/.dockerignore"
docker build --tag async-tt-wiz-server:local "$context"
