# Async Tabletop Wizards — agent guide

Native Android (Kotlin, Compose) two-player cooperative tabletop for playing card
games (MTG first) manually and asynchronously, with a Spring Boot + PostgreSQL
server. The app records and shows what happened; players interpret the rules.

## Goal right now

Finish the Android client (see `docs/roadmap.md`) without breaking what already
works; alongside it, the Arcanum Automatum rules libraries (see below). Do not
build anything outside the task you were given.

## Where things are

| Path | What |
| --- | --- |
| `core/`, `protocol/` | Kotlin Multiplatform shared model, game commands/state, wire types |
| `client-storage/` | Client storage: shared models and logic, the Android SQLDelight implementation, catalog/art download |
| `androidApp/` | Compose app (`org.asyncttwiz.android`) |
| `server/` | Spring Boot server, Flyway migrations, jOOQ |
| `e2e/` | End-to-end tests: feature flows and every owner-reported app bug (see Rules) |
| `arcanum-api/`, `arcanum-parser/`, `arcanum-engine/` | Arcanum Automatum: generic MTG rules libraries (card definitions, oracle-text parser, engine); spec `docs/specs/arcanum-automatum.md` |
| `arcanum-tools/` | JVM developer tools for Arcanum (oracle-text pattern frequencies, supported-set check) |
| `docs/features/` | **Feature catalog**: every current behaviour, one line each, with IDs (`TBL-012`) |
| `docs/roadmap.md` | Long-term product direction |
| `docs/backlog.md` | Approved changes not built yet; an item you build is deleted there in the same commit |
| `docs/specs/` | Specs of larger approved changes not built yet; deleted once built |

Read the catalog files your task touches before changing code. Read only the
spec sections your task needs; where the spec and the catalog disagree, the
catalog wins. Where neither says, ask rather than pick.

## Protecting existing features

- Every catalog entry is a feature the owner wants kept. A task changes or
  removes an entry only when the task names that behaviour. If your work would
  change any other entry, stop and ask instead of choosing.
- Your task describes the changes agreed with the owner. Use the catalog as the
  reference for current behaviour and update the entries the change touches (or
  add new ones) yourself, following the agreed direction, in the same commit as
  the code. The owner reviews catalog changes after implementation, before release.
- If you must decide something the agreed direction doesn't cover, choose the
  plainest option and list it under "choices made" in your final reply.
- If the app turns out to differ from an entry, don't reword the entry to
  match the app (or the app to match the entry); report it and let the owner decide.
- Tests that protect an entry mention its ID in the test name or a comment
  above it (`// TBL-012`). Changing or deleting such a test requires the entry
  to change in the same commit.
- Screenshot tests compare against committed images in
  `androidApp/src/test/screenshots/`. You may re-record them freely
  (`./scripts/gradle.sh :androidApp:testDebugUnitTest
  -Pscreenshots=record --tests <Class>`); list the changed images in your final
  reply. Every
  committed screenshot is captured with the `captureShot` caption helper; its
  caption says in one plain sentence what the picture shows and how the test got there.
  New Automatum UI gets an entry (one representative screenshot) in
  `androidApp/src/test/screenshots/gallery.tsv`, the list `scripts/aa-gallery.sh` builds the gallery page from.
- Changed catalog entries block releases until the owner has reviewed them
  (`scripts/review.sh catalog`). Only the owner approves: never run
  `scripts/review.sh approve` or move the `catalog-approved` tag yourself.
  Changed screenshots are reviewed by the owner in the gallery
  (`scripts/aa-gallery.sh`); never move its `gallery-reviewed` tag yourself.
  Items meant to be seen as a sequence share a name in gallery.tsv's sixth column.

## Arcanum libraries

- Generic MTG code with no ATW knowledge: they never depend on ATW modules
  (`verifyModuleBoundaries`), and ATW uses them only through an integration the
  owner approves per phase.
- Each library keeps KDoc on its public API and a short `README.md` describing its
  interface and status, kept current (an exception to the no-documentation rule).
- One pinned Comprehensive Rules version, `ComprehensiveRules.EFFECTIVE_DATE` in
  `arcanum-api`. Tests that encode a rule cite its number in that version in the
  test name or a comment above it (`// CR 613.4c`). Such a test changes only when
  the pin moves and that rule changed, when it encoded the rule wrongly (owner,
  2026-11-10: anything not according to the rules is fixed without asking; name
  each such test and why in your final reply), or when the owner decides.
- Test cards are invented: our own names and costs with wording copied from real
  oracle templates or merged from several cards. Never commit real card data,
  MTGJSON files or the rules text; tools read them from local files.
- The parser grows one wording group at a time, decided together with the owner,
  except in set work, which adds the wordings its set's cards need.
- Adding a set ("set work"): follow `arcanum-tools/set-work/README.md`; its worker
  brief, prompts and techniques are beside it (an owner-approved exception to the
  no-process-documentation rule). Engine code must be right for 3+ players, and anything
  found that isn't according to the rules is fixed, whichever set it's in.

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
  `:server:test`, `:androidApp:testDebugUnitTest`, `:androidApp:assembleDebug`,
  `:arcanum-parser:jvmTest`, `:arcanum-engine:jvmTest`, `:arcanum-tools:test`, `:e2e:test` (slow, Docker;
  see End-to-end coverage).
  `wasmJsNodeTest` of `:core`, `:protocol`, `:automatum`, `:arcanum-api`, `:arcanum-parser` and `:arcanum-engine` runs their common tests on Kotlin/Wasm (Node); the engine's skips its four long random-game classes unless `-Parcanum.wasmLongGames=true` (about 8 more minutes): before each release, ask the owner whether to include them.
  `:arcanum-tools:setSupport` checks every card of the supported sets (`supported-sets.txt`); it runs in the full-suite batch and in set work's fix and close steps, and reads the pinned MTGJSON file (`catalog/pin.properties`; `scripts/catalog-pin.sh verify`), never committed: without it, it stops and says how to get it.
  `:androidApp:compileDebugAndroidTestKotlin` compiles the on-device tests (androidApp/src/androidTest), which nothing else builds; it runs in the full-suite batch.
  After changing dependencies, write the locks twice: plain, and with the Firebase identifiers (`source scripts/lib/fcm.sh; atw_fcm_args ~/.config/atw-firebase/google-services-test.json org.asyncttwiz.android.test; ./scripts/gradle.sh resolveAndLockAll --write-locks "${fcm_args[@]}"`), or the Firebase test and release builds fail their locks.
  `AndroidRegexCheckTest` (in `:androidApp:testDebugUnitTest`) fails on regex braces Android's ICU rejects but the JVM accepts.
  While implementing, run the tasks relevant to what you changed. After a batch
  of implementations is merged, all the tasks above run together on the merged
  result, and failures are fixed before the batch is done.
- If a check fails, fix the cause. If the same failure persists after two real
  attempts, or the failure is environmental (network, Docker, emulator), stop
  and report it instead of retrying.
- **End-to-end coverage (owner rule, 2026-11-04):** every app problem the owner reports is reproduced by an
  end-to-end test in `e2e/` that fails before the fix, in the same commit as the fix; every new feature gets
  end-to-end coverage of its overall flow. Not every MTG combination needs one; the feature flows and every bug
  we've had do. E2E tests are slow: while implementing, run only your new or relevant ones. The full e2e run is
  a prod release gate: it runs when implementation slows down and nothing is queued, and a release needs a fully
  green run. One test: `./scripts/gradle.sh :e2e:test --tests '*CoreLoopTest*'` (a failure leaves both phones'
  screens and the app log in `e2e/build/e2e/failures/`). The two-emulator smoke (`scripts/smoke-emulators.sh`) is
  its short half on the shrunk build.
- **Logging with problem reports (owner rule, 2026-11-09):** whoever works on a problem report also adds log statements to all the code related to it, so the next report of that area explains itself. Logging here means what reaches the problem report (the app log, the Automatum log and bundle): check it does, and that the volume won't push out what matters. Hidden game information may be logged, cards in hand included: players can't easily read it, and the client is trusted anyway. Passwords, tokens and email addresses still never are. Undo, rewinds and the timeline are a recurring source of bugs, so log them fully: what was asked, from where to where, what was taken back or refused and why.
- Never weaken or delete a test just to make it pass. A test may change only
  when it encodes behaviour your task names (see "Protecting existing features").
- Exactly one version of the apps and server exists at a time: the server serves
  only the current app, and an older app only shows the update screen. Never add
  code, tests or docs that keep anything older working (older apps, older stored
  formats, older events or settings, version windows, fallback readers). A change
  that deletes or invalidates existing games (e.g. a new stored format) needs the
  owner's permission first.
- Every user-visible change gets a release note: one file per change,
  `release/notes/<YYYY-MM-DD>-<slug>.txt`, holding one line `add: <text>`,
  `mod: <text>` or `fix: <text>`, or `set: <full set name>` for a set
  Automatum newly supports (listed first, under New sets). Plain words a
  player understands, no technical terms. Internal-only work needs no note.
- Commit your work on your current branch with a descriptive message when done.
  Do not push, merge, rebase other branches or touch other worktrees.
- Finish with a short plain-text summary: what you built, what you tested, what
  is still missing or uncertain, and which catalog entries you added, changed or
  removed. No more than ~20 lines.
