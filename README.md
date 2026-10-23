# Async Tabletop Wizards

A native Android shared tabletop for two cooperative players to play card games
manually, together or asynchronously. Magic: The Gathering is the first use case;
the app records and presents what happened, the players interpret the rules
(in the spirit of Cockatrice, without rules enforcement).

- **Android app** — Kotlin, Jetpack Compose (`androidApp/`)
- **Server** — Spring Boot, PostgreSQL, Flyway, jOOQ (`server/`)
- **Shared** — Kotlin Multiplatform game model and protocol (`core/`, `protocol/`)
- **Client storage** — SQLDelight, card catalog and art download (`client-storage/`)

## Docs

- [Product principles](docs/design/01-product/overview.md) — start here
- [Design index](docs/README.md) — experience, contracts, technical design
- [UI mockups](docs/design/02-experience/ui/README.md) — portrait/landscape images in `images/`
- [Decision log](docs/decisions.md)

## Build and test

Requires JDK 21, the Android SDK (platform 36) and Docker (server tests use
Testcontainers PostgreSQL).

```sh
./scripts/gradle.sh :protocol:jvmTest :client-storage:test :server:test :androidApp:testDebugUnitTest
./scripts/gradle.sh :androidApp:assembleDebug
```

`scripts/gradle.sh` wraps `./gradlew` and applies a WSL networking workaround.

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

API: http://127.0.0.1:8080 (root returns 404); readiness:
http://127.0.0.1:8081/health/ready; captured reset mail:
http://127.0.0.1:18025. The app uses a tiny synthetic prism/v1 catalog at
http://127.0.0.1:18081/api/v5/, including Scryfall metadata and art. The adapter
rebinds art URLs without modifying the checked-in catalog/gzip/checksum.
Use two emulators and register two accounts in the app for a two-player session.

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

Magic: The Gathering and Cockatrice are references for game compatibility and
interaction design, not claims of affiliation.
