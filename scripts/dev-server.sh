#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD
DEV=$ROOT/build/dev
mkdir -p "$DEV"
umask 077
# Apply the same WSL IPv4 socket workaround as gradle.sh to the installed JVM too.
if grep -qi microsoft /proc/sys/kernel/osrelease 2>/dev/null; then
    export JAVA_OPTS="${JAVA_OPTS:+$JAVA_OPTS }-Djava.net.preferIPv4Stack=true"
fi

# Check the command too: a stale pid file must not stop an unrelated process.
running() {
    local pid marker="$ROOT/server/build/install/server"
    [[ $1 != catalog ]] || marker="$ROOT/tests/fixtures/catalogs"
    [[ -f $DEV/$1.pid ]] || return 1
    read -r pid < "$DEV/$1.pid"
    [[ $pid =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null &&
        ps -p "$pid" -o args= | grep -Fq -- "$marker"
}

stop() {
    local name pid
    for name in server catalog; do
        if running "$name"; then
            read -r pid < "$DEV/$name.pid"
            kill "$pid"
            for ((i=0; i<100; i++)); do
                running "$name" || break
                sleep 0.2
            done
            if running "$name"; then echo "Could not stop $name (PID $pid)" >&2; exit 1; fi
        fi
        rm -f "$DEV/$name.pid"
    done
    if docker container inspect atw-dev-mail >/dev/null 2>&1; then
        docker stop atw-dev-mail >/dev/null
    fi
}

wait_http() {
    local name=$1 url=$2
    for ((i=0; i<60; i++)); do
        if ! running "$name"; then break; fi
        if curl --silent --fail --max-time 2 "$url" >/dev/null; then return; fi
        sleep 1
    done
    echo "$name failed to become ready; see $DEV/$name.log" >&2
    tail -n 20 "$DEV/$name.log" >&2
    exit 1
}

case "${1:-up}" in
down) stop; echo "Server, catalog and mail stopped; PostgreSQL/data kept."; exit ;;
reset)
    stop
    if docker container inspect atw-dev-db >/dev/null 2>&1; then docker rm -f atw-dev-db >/dev/null; fi
    if docker volume inspect atw-dev-db-data >/dev/null 2>&1; then docker volume rm atw-dev-db-data >/dev/null; fi
    echo "Local database wiped. Run scripts/dev-server.sh up to recreate it."
    exit ;;
logs) touch "$DEV/server.log" "$DEV/catalog.log"; exec tail -n 50 -F "$DEV/server.log" "$DEV/catalog.log" ;;
up) ;;
*) echo "Usage: $0 [up|down|reset|logs]" >&2; exit 2 ;;
esac

[[ -s $DEV/db-password ]] || openssl rand -hex 24 > "$DEV/db-password"
[[ -s $DEV/reset-mail-key ]] || openssl rand -base64 32 > "$DEV/reset-mail-key"
chmod 600 "$DEV/db-password" "$DEV/reset-mail-key"
export DB_URL=jdbc:postgresql://127.0.0.1:55432/atw_dev DB_USER=atw_dev
export DB_PASSWORD_FILE="$DEV/db-password" PUBLIC_BASE_URL=http://127.0.0.1:8080
export SERVER_ADDRESS=127.0.0.1 SERVER_PORT=8080 HEALTH_ADDRESS=127.0.0.1 HEALTH_PORT=8081
export RESET_MAIL_KEY_FILE="$DEV/reset-mail-key" RESET_BASE_URL=https://reset.atw.invalid
export SMTP_HOST=127.0.0.1 SMTP_PORT=11025 SMTP_TLS=local-plaintext SMTP_FROM=dev@atw.invalid
unset SMTP_USERNAME_FILE SMTP_PASSWORD_FILE

if ! docker container inspect atw-dev-db >/dev/null 2>&1; then
    image=$(sed -n 's/^postgres=//p' server/database-images.properties)
    docker run -d --name atw-dev-db -p 127.0.0.1:55432:5432 \
        -v atw-dev-db-data:/var/lib/postgresql/data \
        --mount "type=bind,src=$DB_PASSWORD_FILE,dst=/run/secrets/db-password,readonly" \
        -e POSTGRES_USER=atw_dev -e POSTGRES_DB=atw_dev \
        -e POSTGRES_PASSWORD_FILE=/run/secrets/db-password "$image" >/dev/null
fi
docker start atw-dev-db >/dev/null
for ((i=0; i<60; i++)); do
    if docker exec atw-dev-db pg_isready -U atw_dev -d atw_dev >/dev/null 2>&1; then break; fi
    sleep 1
done
docker exec atw-dev-db pg_isready -U atw_dev -d atw_dev

if ! docker container inspect atw-dev-mail >/dev/null 2>&1; then
    docker run -d --name atw-dev-mail -p 127.0.0.1:11025:1025 -p 127.0.0.1:18025:8025 \
        axllent/mailpit:v1.27.0 >/dev/null
fi
docker start atw-dev-mail >/dev/null

if ! running catalog; then
    nohup setsid "$ROOT/scripts/dev-catalog.sh" > "$DEV/catalog.log" 2>&1 < /dev/null &
    echo $! > "$DEV/catalog.pid"
    sleep 1
fi
wait_http catalog http://127.0.0.1:18081/api/v5/Meta.json

if ! running server; then
    ./scripts/gradle.sh :server:installDist
    server/build/install/server/bin/server migrate
    nohup setsid "$ROOT/server/build/install/server/bin/server" serve > "$DEV/server.log" 2>&1 < /dev/null &
    echo $! > "$DEV/server.pid"
    sleep 1
fi
wait_http server http://127.0.0.1:8081/health/ready
echo "API: http://127.0.0.1:8080 (an unknown path returns 404)"
echo "Health: http://127.0.0.1:8081/health/ready"
echo "Catalog: http://127.0.0.1:18081/api/v5/ (synthetic prism/v1)"
echo "Mailpit: http://127.0.0.1:18025 | PostgreSQL: 127.0.0.1:55432/atw_dev"
echo "Background logs/PIDs/secrets: $DEV; use $0 logs or $0 down"
