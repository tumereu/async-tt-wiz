# I13 bounded implementation handoff

## Authority and scope

Managed implement session `ses_e98df46eaffeXupNj0btJGs3eD`, branch
`managed/managed-20261023-i13-01`, start/dependency
`a4b444e192776f5d165fb9188fc6e526f697620e`. Supplied context records the exact
`openai/gpt-6.1-sol#high` model, isolated-host/owned-Testcontainers bundle and lease.
Supervisor additionally leased GenerateJooq.java and GameStartTest.kt for the
necessary discovered-forward-migration guards. No authoritative state/journal,
integration checkout, other lane, device, deployment or global build setting changed.
The private common-directory journal was not accessible to this worker; dispatch
context and explicit supervisor amendments are the lease inputs, not claimed
independent inspection of the supervisor's live journal.

I13 is speculative authorized development before B3 review, not prerequisite
acceptance. Only I13 is proposed implemented. B4 remains pending (I14–I16 and
independent capability review are not covered). The first API/code push was
`7641ac3`; the complete implementation/check batch was pushed as `097bb9d`.

## Executed assertions (not blanket requirement coverage)

- Shared codec/reducer/replay: explicit tap/life edits, current-value compatible
  increments and Set actual from/to; text arithmetic beyond machine Long,
  nonnumeric refusal; scope/unknown previous-value operand rejection; UUID-derived
  node/pile identities; actual individual mutation codec and forward/inverse replay.
- Movement: bound exact card/source and top-card Draw, no substitute; exact gaps,
  empty cell versus existing pile, stable pile identity and capacity 3, source
  placement conflict; unordered special-zone membership without unrelated members
  in relevant tuples. Moves preserve fields; entry conveniences lower to separately
  requested clear-counter/reset mutations, Reset reads retained actual face/default.
- Relevant tuples: position-independent tap survives removing an earlier move;
  dependent numeric increment cannot replay on different input; independent bin
  arrivals do not create a false dependency; exact multi-pile inverse and changed
  retained-default refusal. These are replay foundations, not a shared Undo feature.
- Stack: semantic ordered real-entry checksum includes play ID; changing orientation,
  face/cover/note is irrelevant; stale ordered entry addition rejects; subsequent
  request with predicted updated checksum applies, without routine ack gating.
- Real PostgreSQL: durable FIFO receipts/pending dedup, compatible opponent edits,
  stale draw refusal, original-result/intent collision behavior, noOp/rejection
  without mutation/revision; actual replayable relational mutation/group rows;
  103 individual edits paged 100+3 with authorized same-play access.
- Late SQL fault: node/group/materialized state/revision/outcome/outbox all roll
  back. Ordinary writer recovery is awaited; production GameHub resumes the original
  pending request once after restart; final resume/retry and another restart retain
  the concrete mutation. All owned PostgreSQL/container volumes were cleaned.
- Retained V8 preparation migration and synthetic V9 current-play payload fixture:
  discovered forward set applied, old data/identities/outcomes remain, absent new
  default fields decode, first mutation links to the retained root. Running a new
  executable on an unmigrated database is correctly not used as a fixture bypass.
- Actual two authenticated sockets plus HTTP: production account/deck/lobby/start,
  pipelined draw/tap/life before receipts match the shared predictor, opponent life
  records actual input, semantic Stack guards, coherent revisions/results/history
  and replay, original retry, stranger refusal and no competing HTTP game write.
- jOOQ uses the actual discovered forward migration count (10 here, with V10 absent),
  retains strict migration validation/cleanup, includes the new tables, and passes
  byte-identical generation from two independently migrated owned databases.

Final affected checks: `placement-replay-check.json` + retained matching JUnit
(18 protocol tests and one newly affected PostgreSQL ordering test),
`server-affected-recheck.json` + retained matching JUnit (two repaired PostgreSQL
tests, actual socket/HTTP journey, deterministic codegen). Earlier `server-check.json`
retains two passing PostgreSQL tests and the passing V8 migration assertion alongside
two failed tests; the failed cases alone were rechecked. Do not call that aggregate
red run green. All required affected assertions ultimately passed; no broad suite,
clean rebuild, device journey, independent review or final qualification was run.

## Retained failures and their resolution

- Direct gradlew failed WSL daemon networking; the existing scripts/gradle.sh IPv4
  wrapper passes under the requested one worker / 1280 MiB JVM invocation.
- Standalone pre-migration validate refuses pending migrations under the strict
  profile. Codegen now discovers pending count, uses normal validate-on-migrate,
  then strict post-validation and verifies no pending forward remains.
- A prepared-only output-role helper rejected `pile`; gameplay uses its published
  UUIDv5(request, `pile/0`) role directly. Two gap fixture expectations were corrected
  to the contracted reduced gap/already-placed no-op, not by weakening production guards.
- Kotlin cross-module smart cast fixed via local mutation binding.
- Fault test waits for actual ownership recovery; V9 migration test uses a retained
  JDBC serialization fixture rather than trying to ready a V11 executable on V9.
  Failed raw output/JUnit stays committed in this run, without relabelling as passes.

## Explicit deferred owners and known baseline full-suite work

I14 owns accepted-replica/volatile prediction coordinator, incremental/baseline
installation, fencing and fault recovery presentation. I15/I16 own Canvas/native
placement/gestures and two-device responsive table journeys. I17–I21 own remaining
manual fields/tools, ordered/bulk lowering, tokens/disappearance, links, tracking,
combat and effect entries, extending these same types/processor. I22 owns full
grouping/icon families and playback; I23–I26 own actual shared Undo/Redo, historical
continuation/branches and complete-tree recovery. I33/I34 own performance/full-suite
qualification; B4 independent review and final B8 interface comparison remain required.

Supervisor-identified stale migration-count assertions remain outside this lease.
These exact source anchors were inspected, **not executed or newly reproduced here**:
FoundationDatabaseTest.kt:124,156 (hard-coded V1–V5 version lists);
accounts/AccountRepositoryTest.kt:27 (2 forwards), accounts/PasswordResetTest.kt:56
(1 forward), decks/DeckHttpTest.kt:498 (2), lobby/LobbyHttpTest.kt:205 (2),
game/PreparedDeckTest.kt:349 (2), game/GameDurabilityTest.kt:197 (3). All are under
`server/src/test/kotlin/org/asyncttwiz/server/` (FoundationDatabaseTest under
`database/`). The previously known count mismatch failures are not repaired or
waived by this unit. **I34 final full-suite repair owner** must use the discovered
validated pending forward set while retaining all original data/identity/outcome
assertions, retain actual diagnostics, and run/fix the final suite. This unit repaired
only leased GameStartTest's schema-sensitive count and executed its retained-data test.
