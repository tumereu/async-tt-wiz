# Async Tabletop Wizards

Two-player Magic: The Gathering games, live or asynchronous, on Android and in a
desktop browser. Every game is played by the rules: the Arcanum Automatum engine
works out the board, costs, targets, priority, resolution and triggers, the server
decides the game state, and each player makes only their own decisions; nothing is
changed by hand (feature catalog SYS-001, SYS-069). Decks, cubes and pod drafts
are built in the app.

- **Android app** — Kotlin, Jetpack Compose (`androidApp/`, the shell)
- **Desktop web client** — Kotlin/Wasm, served by the server (`webApp/`)
- **Shared UI** — the Compose screens of both clients (`app-ui/`), the device's
  game on engine state (`app-engine/`)
- **Server** — Spring Boot, PostgreSQL, Flyway, jOOQ (`server/`)
- **Shared** — Kotlin Multiplatform game model and protocol (`core/`, `protocol/`),
  the mapping onto the rules engine (`automatum/`), the card catalog (`catalog/`)
- **Client storage** — SQLDelight, card catalog and art download (`client-storage/`)
- **Arcanum Automatum** — generic MTG rules libraries: card definitions, oracle-text
  parser, engine (`arcanum-api/`, `arcanum-parser/`, `arcanum-engine/`)

## Docs

- [Feature catalog](docs/features/README.md) — what the app does today; start here
- [Docs index](docs/README.md) — roadmap, backlog, specs

## Build and test

Requires JDK 21, the Android SDK (platform 37) and Docker (server tests use
Testcontainers PostgreSQL).

```sh
./scripts/gradle.sh :protocol:jvmTest :client-storage:test :server:test :androidApp:testDebugUnitTest
./scripts/gradle.sh :androidApp:assembleDebug
./scripts/gradle.sh :webApp:webDist        # the desktop web client, into webApp/build/web
```

`AGENTS.md` lists the full set of test tasks.

`scripts/gradle.sh` wraps `./gradlew` and applies a WSL networking workaround.

## Play with a friend (self-hosting)

See the [self-hosting guide](deploy/README.md) for the Docker stack, HTTPS setup
an APK you can both sideload and the desktop web client the server serves.
Public-domain and private Tailscale setups work.

## Run locally

Requires the build prerequisites above, Bash, Python 3, curl, OpenSSL, `setsid`,
Docker and `adb` on PATH. Start a Windows Android emulator yourself; it must
appear as `device` in WSL's `adb devices`. No production services or real-card
downloads are used.

```sh
./scripts/dev-server.sh up       # PostgreSQL + Mailpit + background server/catalog
./scripts/dev-app.sh             # build, install, reverse ports, launch on all devices
# Or select devices: ./scripts/dev-app.sh emulator-5554 emulator-5556
```

API and web client: http://127.0.0.1:8080 (the web client once built with
`:webApp:webDist`; an unknown path returns 404); readiness:
http://127.0.0.1:8081/health/ready; captured reset mail:
http://127.0.0.1:18025. The server's card catalog is built from a tiny synthetic
prism/v1 set of invented cards; the app reads it at http://127.0.0.1:8080/v1/catalog
and its Scryfall metadata and art from http://127.0.0.1:18081/api/v5/. The adapter
rebinds art URLs without modifying the checked-in catalog/gzip/checksum.
Use two emulators (or an emulator and the web client) and register two accounts
for a two-player session.

`up` reuses the database and any running server; after server changes, run `down`
then `up` to rebuild. Logs, PID files and generated secrets live in gitignored
`build/dev/`; `./scripts/dev-server.sh logs` follows server/catalog logs.
`down` stops server/catalog/Mailpit but leaves PostgreSQL running with its named
volume `atw-dev-db-data` on loopback port 55432. `reset` also removes the database
container and volume (all local accounts/games); it does not clear installed app
data. Run `up` afterward and clear app storage if its old login is stale.
Do not delete `build/dev/` while retaining the database: it holds its password.

All listeners are loopback-only; `dev-app.sh` reverses ports 8080 and 18081 per
device. Reset mail is captured, never sent externally. Its HTTPS link host
`reset.atw.invalid` is a dev-only placeholder (no HTTPS website is hosted); test
the captured link in the app with
`adb -s SERIAL shell am start -a android.intent.action.VIEW -d 'FULL_LINK' org.asyncttwiz.android`.

Magic: The Gathering is a reference for game compatibility, not a claim of
affiliation.
