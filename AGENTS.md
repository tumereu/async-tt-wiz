# Async Tabletop Wizards — agent guide

Native Android (Kotlin, Compose) two-player cooperative tabletop for playing card
games (MTG first) manually and asynchronously, with a Spring Boot + PostgreSQL
server. The app records and shows what happened; players interpret the rules.

## Goal right now

Deliver a **working R1**: real app + real backend, features usable end to end.
Working software first; UI polish comes later. Do not build anything outside the
task you were given.

## Where things are

| Path | What |
| --- | --- |
| `core/`, `protocol/` | Kotlin Multiplatform shared model, game commands/state, wire types |
| `client-storage/` | Android SQLDelight storage, catalog/art download |
| `androidApp/` | Compose app (`org.asyncttwiz.android`) |
| `server/` | Spring Boot server, Flyway migrations, jOOQ |
| `e2e/` | Device/journey tests (optional; slow) |
| `docs/design/` | The spec. Tier 1 `01-product/overview.md` → Tier 3 contracts in `03-contracts/` |
| `docs/design/02-experience/ui/` | UI mockups: `images/*.png` (portrait/landscape), `review.md` notes, `feedback.md` owner feedback |

Read only the spec sections your task needs. The spec is large and partly
over-specified; when it conflicts with simplicity, prefer the simplest behavior
that satisfies the product overview, and mention the choice in your final reply.

## Rules

- Shared game/lobby/history state is server-authoritative; the client sends
  requests and may show provisional prediction. Only personal UI (camera,
  selection, open menus, review cursor) is purely local.
- Write production code and proportionate tests (unit/integration for logic,
  server tests for endpoints). **Do not** write reports, evidence files, JSON
  ledgers, coverage maps, validation scripts, or process documentation.
- Do not edit files outside your task's scope; do not refactor unrelated code.
- Build/test with `./scripts/gradle.sh <tasks>` (wraps gradlew for WSL). Useful
  tasks: `:protocol:jvmTest`, `:core:jvmTest`, `:client-storage:test`,
  `:server:test`, `:androidApp:testDebugUnitTest`, `:androidApp:assembleDebug`.
  Run only the tasks relevant to what you changed.
- If a check fails, fix the cause. If the same failure persists after two real
  attempts, or the failure is environmental (network, Docker, emulator), stop
  and report it instead of retrying.
- Never weaken or delete a test just to make it pass, unless the test encodes
  behavior your task explicitly changes.
- Commit your work on your current branch with a descriptive message when done.
  Do not push, merge, rebase other branches or touch other worktrees.
- Finish with a short plain-text summary: what you built, what you tested, what
  is still missing or uncertain. No more than ~20 lines.
