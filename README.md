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

Magic: The Gathering and Cockatrice are references for game compatibility and
interaction design, not claims of affiliation.
