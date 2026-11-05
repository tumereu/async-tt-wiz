# Engine responsiveness and the old model's removal (architecture check, 2026-11-04)

Status: **approved by the owner (2026-11-04), not started.** Comes from the architecture check after the I-D54/I-D56
switch-over. Tasks R1–R4, R6, R7 below, built in that order (R1 first: its measurements may reorder R2–R3; R6 after R2, R7 any
time). R5 (working out the step arrow's pass ahead of time) is optional and decided later (`docs/backlog.md`, "Needs
discussion").

## Why

SYS-025 asks for an own action's local result in under 20 ms (80 ms ceiling). On the phone it takes 25–68 ms today. The
rules engine's own work is a small part of that (a typical `Engine.perform` was ~1–2 ms median on the Pi, engine-state
§9). The rest is the host around it:
- **Layers worked out again and again:** `Engine.view` keeps nothing between calls. One own action works out the
  layers of the same state inside `perform`, twice in `EngineProjection.project` (`Engine.visible`, then `Engine.view`),
  once in `EngineChanges.powerToughness`, and twice more in `Knowledge.revealed` while the line is tentative.
- **Each board is built more than once:** `EngineGame.preview()`, then `after()` → `publish()` (often the same model),
  sometimes once more after `advance`; each build deep-copies the state (`EngineView`).
- **The old model's glue again on a second thread:** for every new `PlayState`, `AutomatumBoards` (thread
  `automatum-board`) turns the board back into engine state (`EngineState.from`, `EngineCall`, `HostPriority.settle`)
  and works out `ComputedBoard` (layers again), `HandPlays` (`Engine.options`) and `CardPlays` (options, the layers,
  and one `Engine.perform` per greyed ability for its reason). Until it is done the table shows the previous board's
  computed values (P/T).
- **The 1-second tick repeats searches:** `EngineGame.tick` → `GameDevice.advance` → `point()` runs `shouldPassFor` /
  `options` on an unchanged state every second while waiting; a tap arriving then waits behind it.
- **The probe misattributes:** on the early path (`EngineGame.early`) the clock stops after `preview()`, so "acted"
  includes building the board, "board" is always 0 (`timing.shown(timing.acted)`), and the frame on screen and the
  computed board's arrival aren't counted.

## R1. Measure an own action properly

- The probe (`EngineGame.Timing`, SYS-030) splits an own action into: wait for the engine thread, the engine's work
  (`perform` and the inputs that follow it), the information-rule check, building the board, the main thread showing it
  (the next frame drawn after the board is published, e.g. `Choreographer`), and the computed board (`ComputedBoard`,
  hand plays) arriving. The slow-action log line and the recorded report carry every part.
- Run on the slow phone over a normal game (passes, casts, combat, a tentative line) and record the medians and worst
  cases in the task's final reply (not in a file). Not a timing test (no elapsed-time asserts, owner rule).
- Catalog: SYS-030 (what a slow action report holds), if its wording names the parts.

## R2. Work out the layers once per state

- **Arcanum (generic, KDoc, README):** `Outcome` carries the view of the state after its changes when the engine already
  has it (its working copy's view cache), or `Engine.perform` accepts the caller's view of the given state (`Perform.run`
  already takes one inside); `Engine.visible` and `Engine.revealed` get overloads taking a view. Results equal to
  today's (tests compare with `Engine.view` worked out afresh; `Layers.checkShortcuts` style check in tests).
- **ATW:** `GameModel` keeps the engine state's view and each viewer's visible set until its next `apply`/`revert`
  (a change counter); `EngineProjection`, the transition's P/T (`EngineChanges.powerToughness`), the information rule's
  "before" visibility, `GameDevice`'s point and fold checks and the server's `refresh` read them from there.
- Expected: 3–5 fewer layer computations per own action. Catalog: none (behaviour identical).

## R3. Build the board once, on the engine thread

- `after()` reuses the board `preview()` published when the model, table state and tip are unchanged (only status
  fields published anew).
- `GameDevice.advance` remembers its decision per point (tip, the other player's liveness, the grace running out), so
  a tick at an unchanged point does no engine search; the live wait's grace still ends on time.
- The engine thread works out, from R2's one view, what the table's second thread works out today: the computed card
  values (`ComputedBoard`'s P/T, keywords, type and colour changes, until-end-of-turn marks) and the player's plays
  (`HandPlays`, `CardPlays`; card menus may stay lazy but run on the engine thread, cached per tip), and publishes them
  with `EngineGame.Shown`. The table reads them from there; `AutomatumBoards` no longer rebuilds engine state from
  `PlayState` for engine games. The computed values arrive with the board, never the previous board's.
- `CardPlays`' reason for a greyed ability without one `perform` per ability, if the engine can say it cheaply
  (an Arcanum helper giving the refusal of an input without performing it; generic, KDoc).
- Catalog: SYS-025 holds again (the point of R1–R3); no entry changes unless R1 shows SYS-025's wording names
  something else.

## R4. A render model instead of `PlayState`, then delete the old model

- The table draws a render model made by the engine thread from `GameModel` + `TableState` (+ R3's computed values and
  plays) instead of `EngineProjection`'s `PlayState` with `BoardState.engine` attached. The cast, choice and combat
  controllers read the engine state and table state directly, not through `EngineState.from` / `EngineCall` /
  `HostPriority`.
- Then delete what only the old model reached: the app's
  old table path and history machinery, the I-D41 UI, `HistoryText`, the play replica in `GameSession`; protocol's old
  play actions, `GameplayRules`/`HistoryContinuation`/`AutomatumOnly`/`GameplayTree`/`TreeSync`/`RetainedIndex` and
  `PlayState`'s game fields; automatum's `EngineBatch`, `StepFlow`/`PausePoints` passing for others, `HostPriority`,
  `EngineState.from`'s building; a migration dropping the old model's empty tables (`gameplay_mutation`,
  `gameplay_group`, `gameplay_tree_control`, `gameplay_endpoint_*`, `game_play_mutation`; deletes no game).
- Tests citing entries that still hold on engine state (e.g. LOB-066, HIS-018–HIS-020) get engine-path replacements in
  the same commit as the deletion, not just deletion. Screenshots re-recorded where the table's drawing changes.
- Catalog: HIS-063–HIS-069, SYS-004, SYS-010, SYS-061 (today's model's texts and batches) go with their code; every
  other entry keeps its behaviour.
- Large: split into commits per layer (render model in the table; controllers; deletion app / protocol / automatum /
  database), suites green after each.

## R6. Server: cheaper refreshes (after R2)

- `EngineGames.refresh` after an `EngineSeen` (or settings) works out only the reporting seat's next stop, resume point
  and wait (the other seat's can't change by it); after a submission, rewind or request, both as today.
- Measure the heap of a game kept in memory (a typical turn-10 and a turn-40 state, with the 4 kept turns' cuts), and
  set the warm capacity (32 today) from it for the Pi's `-Xmx`.
- Cut opening a game's database round trips (43 today, `RequestCostTest`) where reads can be joined; the test's numbers
  change with it.
- Catalog: none.

## R7. Close out the built specs

- Built (2026-11-05): `engine-state-architecture.md` is deleted, and `async-play.md` holds only the choices still
  waiting for the owner's confirmation.

## Not in this spec

- The streaming `perform` (backlog "Needs discussion") would speed up only long chains (board wipe, big turn changes,
  40–80 ms on the Pi), not the typical case; the player can't act before the chain reaches a decision, and on a
  tentative line nothing may be shown before the whole outcome passed the information rule. Recommendation: not now;
  if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` handing over
  changes as they are recorded rather than a channel. Owner to decide.
