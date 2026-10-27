# Play with a friend

This runs one server and PostgreSQL on your own Linux/Docker host. Both Android
devices connect to the same **HTTPS origin**; gameplay also needs WebSocket
upgrades at `/v1/socket`. No registry, push service or Google services are required.
The server is a manual tabletop, not a rules engine.

## 1. Build and configure

Requirements: JDK 21, Android SDK platform 36, Bash, OpenSSL, Docker with Compose,
and enough disk space for Gradle/Docker plus the phone's public card catalog.
Run commands from the repository root. The image build uses a disposable
Testcontainers PostgreSQL for jOOQ generation, not your deployment database.

```sh
./scripts/build-server-image.sh
cp deploy/.env.example deploy/.env
mkdir -p deploy/secrets
chmod 700 deploy/secrets
(umask 077; openssl rand -hex 24 > deploy/secrets/db-password)
(umask 077; openssl rand -base64 32 > deploy/secrets/reset-mail-key)
# Compose file secrets preserve host modes. The server (UID 10001) and PostgreSQL
# (UID 999) both need to read the DB password. The owner-only parent directory
# protects these files on the host; Docker mounts only each required file read-only.
chmod 444 deploy/secrets/db-password deploy/secrets/reset-mail-key
```

Do this secret initialization **once**, not on each start. Edit `deploy/.env`:
set `PUBLIC_BASE_URL` to the HTTPS origin selected below, without a trailing slash
or path. `RESET_BASE_URL` defaults to it. Optional SMTP settings enable password
reset mail; registration, sign-in and play work without a working mail relay.
Without SMTP configuration, reset requests still return the privacy-preserving
202 response, but mail cannot be delivered. Do not rely on resets until tested.

For authenticated SMTP, put the username and password in separate UTF-8 files
(`deploy/secrets/smtp-username` and `smtp-password`, with the same directory/modes),
set the relay host/port/from/TLS settings in `.env`, and add
`-f deploy/compose.smtp.yml` **after** `-f deploy/compose.yml` to every Compose
command. Use `starttls` (usually port 587) or `tls` (usually 465). Never send
credentials to a plaintext relay. An unauthenticated trusted TLS relay needs only
the base Compose file. Files strip one final LF/CRLF, preserving other whitespace.

## 2. Provide HTTPS (choose one)

### Public domain and reverse proxy

Point a domain's DNS at the host, allow inbound TCP 80/443, and install Caddy on
the **host**, outside this Compose stack. Adapt
[`Caddyfile.example`](Caddyfile.example) into Caddy's configuration and reload it.
Caddy automatically obtains/renews a publicly trusted certificate and proxies
HTTP and WebSockets to `127.0.0.1:8080`. Set, for example:

```dotenv
PUBLIC_BASE_URL=https://tabletop.example.com
```

If you change `API_PORT`, also change Caddy's upstream. Other proxies must preserve
`Authorization` and WebSocket `Upgrade`/`Connection` negotiation, and allow
long-lived connections. Do not log authorization headers, request bodies or
password-reset URL queries. The server does not trust forwarded peer headers;
IP rate limits behind a proxy are shared, so avoid aggressive repeated logins.
Do not expose PostgreSQL, the health listener or plaintext API to the internet.

### Private Tailscale play

Install/sign in to Tailscale on the host and **both phones**, invite your friend
to the same tailnet, and allow access through your tailnet policy. Enable HTTPS
certificates in Tailscale's admin settings, then on the host run:

```sh
tailscale serve --https=443 http://127.0.0.1:8080
```

Use the HTTPS `https://<machine>.<tailnet>.ts.net` origin reported by Serve for
`PUBLIC_BASE_URL` and the APK build. Serve passes WebSocket upgrades through.
Keep the command running, or use `tailscale serve --bg --https=443
http://127.0.0.1:8080` for persistent serving. No router port forwarding or
Tailscale Funnel (public access) is needed. Both phones must keep Tailscale
connected. Replace 8080 in the command if you change `API_PORT`.

## 3. Start the stack

```sh
docker compose -f deploy/compose.yml --env-file deploy/.env config --quiet
docker compose -f deploy/compose.yml --env-file deploy/.env up -d --wait
docker compose -f deploy/compose.yml --env-file deploy/.env ps -a
```

The default project is `atw-selfhost`. PostgreSQL has a project-scoped named
volume and no host port. `migrate` must exit successfully before `server` starts;
it is a one-shot, not a daemon with a health status. Database and server restart
unless stopped. Only the API is published, to **host loopback** (default 8080).
The health listener stays on container loopback and is probed inside the image:

```sh
docker compose -f deploy/compose.yml --env-file deploy/.env exec server \
  curl --fail --silent http://127.0.0.1:8081/health/ready
docker compose -f deploy/compose.yml --env-file deploy/.env logs --tail=50 migrate server
```

The API root intentionally returns 404; it is not a health endpoint. HTTPS can
be checked with `curl -i https://YOUR-HOST/account` (401 is expected without a
token). Test a two-device game to verify the proxy's WebSocket path as well.

## 4. Build and share the APK

```sh
./scripts/build-release-apk.sh https://YOUR-HOST
# Optional separate reset origin, matching RESET_BASE_URL:
./scripts/build-release-apk.sh https://YOUR-HOST https://RESET-HOST
```

Share `androidApp/build/self-hosting/async-tt-wiz.apk` with your friend and enable
Android's install-from-this-source permission to sideload it (Android 10+).
Both origins must be HTTPS origins without paths, queries or trailing slashes.
The reset origin defaults to the API origin. No trust bypass or HTTP fallback is
added. Configure `atwArtContact` in your user Gradle properties with an operator
contact URL/email before using the public artwork provider; catalog/art traffic
goes directly from phones to MTGJSON/Scryfall, never through your server.

Signing follows the existing Gradle inputs (all four or none):

| Gradle property | Environment variable |
| --- | --- |
| `atwReleaseStoreFile` | `ATW_RELEASE_STORE_FILE` |
| `atwReleaseStorePasswordFile` | `ATW_RELEASE_STORE_PASSWORD_FILE` |
| `atwReleaseKeyAlias` | `ATW_RELEASE_KEY_ALIAS` |
| `atwReleaseKeyPasswordFile` | `ATW_RELEASE_KEY_PASSWORD_FILE` |

Use user-level `~/.gradle/gradle.properties` or environment variables; keystore and
password files must already exist **outside the checkout**. No release keys are
generated. With release signing, the app keeps `org.asyncttwiz.android`.
Without it, the helper explicitly selects a **local mode of the release variant**:
AGP's existing debug signer, package `org.asyncttwiz.android.local`, version suffix
`-local`, no account switcher, and the same release HTTPS-only networking. Ordinary
release builds without this opt-in remain unsigned.

The local APK is for trusted private sideloading, not store distribution. Keep the
same local debug keystore for updates; another machine's key cannot update it.
Switching to a different signer needs uninstalling (losing local data), or installing
the distinct release package. Keep both players on the same app/server revision.
Verified password-reset app links additionally need an `assetlinks.json` association
for the actual package and signing certificate. Without it, use the HTTPS browser
reset form; reset emails are not needed to create accounts or start a game.

Each player registers and signs in. Download card records in Manage decks, import
and save a decklist, then add the other player's username in Contacts. Create a
named invitation, accept it on the other phone, select decks, choose a starter and
press Ready on both devices. Initial catalog acquisition is large and needs consent;
missing artwork falls back to text. The app records manual actions and history.

## Stop, upgrade and preserve data

```sh
# Stops containers; retains database and secrets.
docker compose -f deploy/compose.yml --env-file deploy/.env down
# After updating source, rebuild and restart; migrations run before the new server.
./scripts/build-server-image.sh
docker compose -f deploy/compose.yml --env-file deploy/.env up -d --wait
```

For upgrades, stop the old stack first and back up PostgreSQL plus external secrets
before migrating; forward migrations are not a downgrade mechanism. Never rotate
the DB password by merely editing its file (PostgreSQL initializes it only on an
empty volume). Keep the reset-mail key stable: pending encrypted mail requires it,
and a wrong key blocks readiness. Backups contain private account/game data.

`down --volumes` **permanently removes the database**; use it only for disposable
stacks. To run beside the development stack, use a distinct project and host port,
for example `API_PORT=18180 docker compose -p atw-selfhost-test -f deploy/compose.yml
--env-file deploy/.env up -d --wait`. Use that same project, port and files for
inspection/teardown. Never stop or remove the `atw-dev-*` resources.

## Personal WSL test environment

Run `scripts/test-env.sh` to build the current working tree, replace the detached
test server, and install **ATW Test** alongside production on the connected phone.
Requires the build tools above, `curl`, `flock`, Tailscale, and Windows PowerShell/WSL.
`ATW_ADB_SERIAL=94d4fd56`, `ATW_ADB_PORT`, and `ATW_ADB` override phone discovery.
Without an authorized phone the server still starts and the APK path is printed;
install later with `adb install -r androidApp/build/outputs/apk/personalTest/androidApp-personalTest.apk`.

- `scripts/test-env.sh status` / `logs`: inspect readiness and follow server logs.
- `scripts/test-env.sh down`: stop only test processes/containers and the hidden
  Windows WSL keep-alive; retain data, secrets and the Tailscale listener.
- API: `https://desktop-b2kalvm.tail27cccc.ts.net:10443` (tailnet only).
  Only Serve port 10443 is updated; existing 443/8443 listeners are untouched.
- Local readiness: `http://127.0.0.1:18091/health/ready`; Mailpit:
  `http://127.0.0.1:18026`; PostgreSQL: `127.0.0.1:55433/atw_test`.
- State/secrets/logs: `${XDG_DATA_HOME:-~/.local/share}/atw-test` (secrets mode 600).
  Named volumes `atw-test-db-data` and `atw-test-mail-data` persist across runs.
  Each start migrates before serving from an independent install snapshot (last
  two retained), so rebuilding the repo cannot break the live JVM.

The hidden Windows `wsl.exe` session prevents WSL idle shutdown after all terminals
close; shutdown/reboot still stops it. Run the script again afterward.
For local verification without changing Serve or installing on a phone:
`ATW_TEST_SKIP_TAILSCALE=1 ATW_TEST_SKIP_INSTALL=1 scripts/test-env.sh`.
