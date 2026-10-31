# Async Tabletop Wizards — agent guide

Native Android (Kotlin, Compose) two-player cooperative tabletop for playing card
games (MTG first) manually and asynchronously, with a Spring Boot + PostgreSQL
server. The app records and shows what happened; players interpret the rules.

## Goal right now

Finish the Android client (see `docs/roadmap.md`) without breaking what already
works. Do not build anything outside the task you were given.

## Where things are

| Path | What |
| --- | --- |
| `core/`, `protocol/` | Kotlin Multiplatform shared model, game commands/state, wire types |
| `client-storage/` | Android SQLDelight storage, catalog/art download |
| `androidApp/` | Compose app (`org.asyncttwiz.android`) |
| `server/` | Spring Boot server, Flyway migrations, jOOQ |
| `e2e/` | Device/journey tests (optional; slow) |
| `docs/features/` | **Feature catalog**: every current behaviour, one line each, with IDs (`TBL-012`) |
| `docs/roadmap.md` | Long-term product direction |
| `docs/backlog.md` | Approved changes not built yet; an item you build is deleted there in the same commit |
| `docs/specs/` | Specs of larger approved changes not built yet; deleted once built |
| `docs/design/` | Older, larger spec. Tier 1 `01-product/overview.md` → Tier 3 contracts in `03-contracts/` |
| `docs/design/02-experience/ui/` | UI mockups: `images/*.png` (portrait/landscape), `review.md` notes, `feedback.md` owner feedback |

Read the catalog files your task touches before changing code. Read only the
spec sections your task needs; where the spec and the catalog disagree, the
catalog wins. Where neither says, ask rather than pick.

## Protecting existing features

- Every catalog entry is a feature the owner wants kept. A task changes or
  removes an entry only when the task names that behaviour. If your work would
  change any other entry, stop and ask instead of choosing.
- When the owner approves a new or changed behaviour, update its catalog entry
  in the same commit as the code.
- Tests that protect an entry mention its ID in the test name or a comment
  above it (`// TBL-012`). Changing or deleting such a test requires the entry
  to change in the same commit.
- Screenshot tests compare against committed images in
  `androidApp/src/test/screenshots/`. Re-record only for an intended visual
  change (`./scripts/gradle.sh :androidApp:testDebugUnitTest -Pscreenshots=record
  --tests <Class>`) and list the changed images in your final reply.

## Rules

- Shared game/lobby/history state is server-authoritative; the client sends
  requests and may show provisional prediction. Only personal UI (camera,
  selection, open menus, review cursor) is purely local.
- Write production code and proportionate tests (unit/integration for logic,
  server tests for endpoints). **Do not** write reports, evidence files, JSON
  ledgers, coverage maps or process documentation. The feature catalog and the
  build checks that guard it are the exception.
- Do not edit files outside your task's scope; do not refactor unrelated code.
- Build/test with `./scripts/gradle.sh <tasks>` (wraps gradlew for WSL). Useful
  tasks: `:protocol:jvmTest`, `:core:jvmTest`, `:client-storage:test`,
  `:server:test`, `:androidApp:testDebugUnitTest`, `:androidApp:assembleDebug`.
  Run only the tasks relevant to what you changed.
- If a check fails, fix the cause. If the same failure persists after two real
  attempts, or the failure is environmental (network, Docker, emulator), stop
  and report it instead of retrying.
- Never weaken or delete a test just to make it pass. A test may change only
  when it encodes behaviour your task names (see "Protecting existing features").
- Only one compatible set of apps and server is maintained; do not keep old app
  versions working. A change that deletes or invalidates existing games (e.g. a
  migration, or retiring an old rules version) needs the owner's permission first.
- Every user-visible change gets a release note: one file per change,
  `release/notes/<YYYY-MM-DD>-<slug>.txt`, holding one line `add: <text>`,
  `mod: <text>` or `fix: <text>`. Plain words a player understands, no
  technical terms. Internal-only work needs no note.
- Commit your work on your current branch with a descriptive message when done.
  Do not push, merge, rebase other branches or touch other worktrees.
- Finish with a short plain-text summary: what you built, what you tested, what
  is still missing or uncertain, and which catalog entries you added, changed or
  removed. No more than ~20 lines.
