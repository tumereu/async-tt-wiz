# Engine state as the game state — proposal (I-D54)

Status: **approved by the owner** (2026-11-03); built through the switch-over (I-D56 step D2, §14), the rest of the deletion left. Originally: not to be started until the current round is done and deployed to
the test environment, so UI testing can go on while this is built. Follows from I-D52 (Automatum always on).

## 1. Why

Today ATW keeps its own full game model (cards, zones, P/T, counters, Stack, combat) and translates every engine call
into ATW actions (`EngineBatch.plan`); every call rebuilds the engine's state from ATW's model plus stored engine records
(`EngineState.from`), and a round-trip check guards the translation. Most bugs the reviews found lived in that layer
(tokens leaving mid-resolution, first-strike state lost on rebuild, ledgers, records, lost turn data). With Automatum
always on there is no second source of truth to keep in step with: the engine's state *is* the game.

## 2. The model

- **Game state = engine state + table state.**
  - *Engine state*: Arcanum's `GameState` — everything the rules care about (zones, objects, counters, damage, turn,
    priority, Stack, combat, the "this turn" event log, pending decision and continuation).
  - *Table state*: what only ATW cares about — card placement on the battlefield (position, grouping/rows, order),
    per-player play settings (Full control, Skip when I have no choice, Autotap; *superseded: private settings on the
    server, outside the table state, `async-play.md` §13*), early answers (I-D33), the shared attack
    staging / block draft before it's declared, result suggestions and their answers.
- **History = a tree of events; each event has two optional parts, applied and reverted together:**
  - *engine part*: the player's input(s) plus the engine's own reversible `Change` list (`GameState.apply/revert`, D59);
  - *table part*: a list of reversible table changes (before → after).
  Both parts are always reversible, so undo, redo, branching and timeline playback work exactly as now: they walk events.
  *(Superseded by `async-play.md` §8, §10: no redo, branches only where a real answer differs from an assumed one, undo
  free or by request.)*
  The undo rule stays (an event can be undone if no later event changed what it changed — each part reports what it
  touched).
- **Display** comes from `Engine.view(state)` (computed characteristics) and `Engine.visible(state, viewer)` (what the
  viewer may see, I-D27 display rule) plus the table state for placement. Animations come from the change list.
- **Bookkeeping moves into the engine**: every "if you've done X this turn", counters, damage, turn/priority, combat — all
  engine state; ATW only renders it.

## 3. Who runs the engine (owner, 2026-11-03)

- **Inputs, not results:** a client sends *engine inputs* (cast this, pay with these, choose these targets, pass, declare
  attackers…) together with its table changes. The same input goes to the **local engine** (for instant feedback,
  latency hiding everywhere) and to the **server engine**.
- **The server is authoritative about order and legality**: it keeps each active game's engine state in memory, applies
  inputs in arrival order on top of the event they were made on, runs the engine, stores the event, and broadcasts it.
  If another player's input arrived first and the parent moved, yours is refused; your device then syncs to the server's
  state when the newer events arrive (local pending inputs are dropped — the player sees the board update and can act
  again). Branching from an older point stays an explicit action as today. *(Superseded: `async-play.md` §8.)*
- **Trust the client, even with randomness:** random answers (shuffles, coin flips, random choices — D25 host answers)
  are made on the device and sent with the input, so the local result is final at once; the server uses them as given.
  *(Superseded: the server checks them against the line's stream, `async-play.md` §9.)*
- **Server gate** becomes: "is this a legal engine input on this state (plus well-formed table changes by an allowed
  player)?" — the separate ATW rules gate and its protocol action types go away.

## 4. Storage and loading

- Events store the engine part in the engine's own serialized change format plus the inputs (for the reproduction
  bundle, I-D43). Card definitions of a game are stored once per game (not per event).
- No rebuild from root (owner rule; games reach 10k+ actions): engine-state checkpoints along the tree, as today's
  position checkpoints; a state at a node = nearest checkpoint + replay of the events after it.
- The server keeps active games' states warm in memory; a cold load = checkpoint + replay.
- One compatible app/server set (D62): stored games depend on the engine's change format; a format change may wipe games
  (owner accepted for this refactor).

## 5. What goes away

`automatum/` mapping (EngineState.from, EngineBatch.plan, round-trip checks, records, ledgers, lingering-object hacks,
flow records duplicating the engine's continuation); ATW's duplicate game model in `protocol` (PlayCard/PlayPlayer game
fields the engine owns, MtgAction/EngineRecord types, the Automatum batch rules); the server's ATW gameplay rules gate.
The app renders from engine views instead of ATW's model.

## 6. What stays

The table UI, layout and behaviour (I-D52 addendum), all Automatum panels and controls, the timeline/history/branching UX,
chat, presence, pause points (I-D41 — still client-side policy deciding when to send Pass), notifications, problem
reports (now with the engine inputs as the reproduction bundle), lobby/decks/cubes/drafts. Players should see the same
behaviour; catalog entries change only where wording names internals. *(Superseded by I-D56: the branching UX and the
I-D41 pause points went; see `async-play.md`.)*

## 7. Plan (after owner approval and the current round)

1. **Spike:** run the engine on the server JVM; measure perform cost on the Pi for typical and heavy calls (stop if not
   viable and discuss).
2. **Event model & storage:** event = engine part + table part; checkpoints; serialization; wipe migration.
3. **Server authority:** in-memory engine states, input application in order, refusal on stale parent, broadcast.
4. **Client:** local engine on the same events, provisional result, resync on refusal.
5. **App rendering** from `Engine.view`/`visible` + table state; animations from changes.
6. **Delete** the mapping layer, duplicate model and old gate; then an adversarial review (two-device races, branching,
   undo, reconnect, performance on the Pi) and fixes.

## 8. Owner decisions on the open points (2026-11-03)

- **Game results live in the engine:** concede is an engine input (CR 104.3a), and the engine reports "player X won /
  lost / draw" (rules losses, cards that win or lose the game, agreed results recorded through an engine input). ATW's
  result flow (I-D53 confirm) renders what the engine reports.
- **Attack staging / block draft stay shared** (table events); showing some of that information to the other player can
  be delayed later if wanted.
- **Mulligans and other pre-game steps:** as engine inputs (extend the engine for simultaneous decisions) — proposed,
  not objected.

## 9. Built (I-D54 phase 1–2, 2026-11-03)

**Phase 1 — spike: viable.** `:server` depends on `:arcanum-engine`/`:arcanum-api` (and `:protocol` on the engine;
`verifyModuleBoundaries` updated, Arcanum still depends on nothing of ATW). `EngineOnServerSpikeTest`
(`./scripts/gradle.sh :server:test -Patw.engineSpike=true --tests '*EngineOnServerSpikeTest*'`) runs `Engine.perform`
in the server's test JVM (JDK 21, 512 MiB heap) on this dev machine (Ryzen 5 5600X, WSL, shared with other builds:
heavy cases varied up to ~5× between two runs; both shown):
- **Typical** (12 whole games of the themed cube decks, 15.5k inputs, warm JIT): median 0.8 ms, p95 3 ms, p99 5–6 ms,
  max 20 ms per input. Pass 0.55 ms median; cast 1.4; mana payment answer 2.5; activate 1.4. Cold JVM (first game after
  start): median 1.6 ms, max 110 ms. Applying the changes: microseconds.
- **Heavy** (constructed boards, median of 5): turn change on 90 permanents (cleanup, untap 45, upkeep, draw) 18–105 ms;
  board wipe killing 60 creatures (190 changes) 39–46 ms; cast + pay on a 61-permanent lord board 19–43 ms; resolving
  there 9–11 ms; 20 blockers and combat damage 2.4–2.9 ms; ten triggers 0.9 ms.
- **Storage:** event JSON median 0.6 KB, p95 3.4 KB, max 56 KB (a shuffle lists the library), about 1.2 KB per input;
  definitions by reference save a third. A checkpoint of a turn-58 state with the log kept to this and last turn is
  142 KB, 7 ms to write or read (with the whole log it would be 1.3 MB, 70–85 ms); the decks' definitions 55 KB once.
- **Pi 5: measured, about 2× slower** than this desktop (the estimate was 3–4×). `./scripts/gradle.sh
  :server:engineBenchmarkJar` builds `server/build/benchmark/engine-benchmark.jar` (15 MB: the spike's classes, the
  invented cards, engine/api/parser/protocol and Kotlin libraries) holding the spike's seeded games as recorded inputs;
  `java -jar` replays them (the random player's option listing, ~10× what is measured, isn't redone): a cold game, 2
  warm-up games, 4 measured whole games (3,966 inputs), then the checkpoints (11 runs) and heavy cases (20 runs each).
  Run once on 2026-11-03 in the production server container's JRE (Temurin 21.0.12, aarch64, 4 cores, `-Xmx512m`,
  `nice 19`, 44 s, no throttling, then removed), and the same jar here (23 s, other builds running). Fewer warm-up and
  measured games than the spike test, so compare the columns with each other rather than with the bullets above.
  Milliseconds, median / p95 / max:

  | Case | Desktop (Ryzen 5 5600X) | Pi 5 |
  | --- | --- | --- |
  | Typical input, all kinds (p99 7.1 / 13.4) | 1.1 / 3.9 / 21 | 2.2 / 7.5 / 51 |
  | Pass priority (2,638 of them) | 0.7 / 3.1 / 17 | 1.5 / 6.1 / 51 |
  | Mana payment answer | 4.1 / 8.0 / 9.7 | 7.4 / 16 / 19 |
  | Cold JVM, first game's inputs | 2.2 / 8.8 / 116 | 4.9 / 17 / 138 |
  | Turn change, 90 permanents (untap 45, upkeep, draw) | 22 / 25 / 25 | 37 / 39 / 41 |
  | Board wipe, 60 creatures die (190 changes) | 40 / 44 / 46 | 61 / 73 / 82 |
  | Cast + pay on the 61-permanent lord board | 34 / 43 / 44 | 38 / 61 / 62 |
  | Resolving there (SBA, layers) | 6.7 / 9.5 / 9.6 | 13 / 14 / 15 |
  | 20 blockers and combat damage | 2.3 / 3.2 / 8.0 | 4.0 / 6.1 / 7.4 |
  | Checkpoint as stored (134 KB), encode | 8.9 / 10 / 13 | 14 / 17 / 19 |
  | Checkpoint as stored, decode | 8.7 / 12 / 13 | 19 / 20 / 22 |
  | Checkpoint with the whole log (729 KB), encode / decode median | 48 / 54 | 83 / 122 |

  So on the Pi a typical input costs ~2 ms (p95 ~8 ms), the heaviest constructed cases 40–80 ms, a cold read of a
  stored checkpoint ~20 ms: fine for a turn-based async game.

**Phase 2 — event model and storage (protocol package `org.asyncttwiz.protocol.game`; nothing uses it yet, the old
model is untouched):**
- `TableState` (placements by engine object id, per-player `PlaySettings` Full control/Autotap, early answers, shared
  attack/block drafts as the engine's `Attack`/`Block`, result suggestion as the engine's `GameResult`, who confirmed the
  engine's result) changed only by `TableChange`s with before/after values, checked on apply like the engine's changes.
- `GameEvent(id, parentId, author, engine: EnginePart?, table)`; `EnginePart(timestampBefore, steps)`, each
  `EngineStep(input, changes)` (input null: a host change such as Skip when I have no choice). One event = one acting
  point (I-D35): `EventBuilder` performs the input, then the device's random answers, then adds the table part.
  `GameModel` (engine `GameState` + `TableState`) applies and reverts events all-or-nothing.
- **Design adjustments** (needed for "the same state however reached", which the server/client and checkpoint/replay
  equality rest on; the engine's decision ids read its timestamp counter and log size):
  - one **full-information** engine state on the server and on devices (I-D27: hidden information is display; shown
    through `Engine.visible`), so no per-viewer states, host fills or other-view answers;
  - reverting an event puts the engine's **timestamp counter** back (`EnginePart.timestampBefore`; the engine itself
    never moves it back);
  - after each event the engine **log is cut to this and last turn** (`forgetOlderTurns`; keeps checkpoints and the
    engine's log lookups small). A model keeps the last 4 turns' cuts in memory to revert across turn starts; beyond
    that it reports `stale` and the history takes the state from a checkpoint.
- **Undo rule:** `Concerns.of(event)` (what it writes and reads: object presence/state by card, players, the rest of
  the engine, table parts; an engine input reads the whole engine state); `UndoRule.blocking/undo`. So an engine event
  can be undone past later table-only events that don't touch its objects, never past a later engine input.
- **Checkpoints:** `GameHistory` (every 64 depths; a cold state = one checkpoint + at most 63 events; a state at hand
  moves along the tree by revert/apply, bounded, else from a checkpoint); `HistoryStore`, `InMemoryHistory`.
- **Serialization:** `EngineJson` (kotlinx JSON of the engine's own types, defaults left out; card definitions of the
  game's decks stored once by digest and referenced as `"#sha256:…"`; tokens' stay written out). `EventFormatJvmTest`
  keeps a frozen 60-event sample that must still read, write back byte for byte and apply: when the engine's format
  changes it fails (then a reader or a wipe).
- **Storage:** V56 `engine_play` (definitions, root), `engine_event` (tree, depth, accept order), `engine_checkpoint`;
  `EngineHistoryStore` (one recursive query per cold read). Unused until phase 3.
- **Wipe:** `server/src/main/resources/db/pending/wipe_plays_for_engine_state.sql` (V54's wipe plus the engine tables),
  tested against the schema; it becomes the next `V<n>` in the release that switches the server over, so test-env games
  aren't wiped twice.
- **Tests:** apply/revert round trips per event (engine and table parts, mismatches change nothing), reverting back
  across turns, branching and checkpoint states equal to direct ones with bounded work, undo-rule cases, JSON round
  trips and the frozen sample, the PostgreSQL store and the wipe.

**Phase 3–4 (2026-11-03): built behind a seam; every game still starts and plays on today's model.**
- **Engine inputs (§8)** in `arcanum-engine`: `Input.AgreedResult(result)` ends the game as the players agreed (a draw,
  CR 104.4i; a win: the others concede, CR 104.3a; the host vouches, never listed by `options`);
  `StartOptions.simultaneousMulligans` with `Input.SetupAnswer(player, answer)`: each round's mulligan declarations and
  bottom choices in any order (the open one is still the next in turn order and is asked again with a new id; the
  mulligans are still taken together; listed by `options`). Concede was already an engine input.
- **Submissions** (`protocol` `game/Submission.kt`): `Submission(id, parentId, acts, table)`: inputs, not results
  (`Act.Perform(input)`, `Act.SkipWhenNoChoice`), random answers among them; `Submission.of(event)` for a device's own
  event. `Authority.make` is the gate: the engine is the only rules check; who may submit which input is the seam
  `WhoActs` (today `WhoActs.Anyone`, the all-actions behaviour; I-D55 — acting only for oneself, proceeding on assumed
  answers that branch when the real one differs — is being refined by the owner and plugs in there; `Authority.mayMake`
  filters what a device offers of `Engine.options`); always a player's own: conceding,
  settings, early answers, making a suggestion, agreeing a result (an `AgreedResult` only answers the other player's
  suggestion of it), confirming a result the engine reports. `Acceptance.accept`: arrival order on the live line,
  `stale` when the parent isn't its last event, unless a deliberate branch; the state from the warm one (moved) or a
  checkpoint. `Attention.of(state)`: whose move it is from the engine.
- **Server** (`EngineGames`): warm states per play (LRU of 32; taken out while a submission is worked on, put back
  after commit), `GameAction.Submit` through the game inbox (one ordering, same request identities), the event stored
  (`EngineHistoryStore`) and broadcast as `GameRevision.engineEvent` (engine format, definitions by reference);
  `GameHead.engine` = `EngineSnapshot(definitions, rootId, headId, state)`; the live line's last event = the event
  accepted last. Refusals: `stale`, `refusedByEngine`, `notEngineState`. The game's start is the server's own first
  event (`StartGame` with the lobby's starter, simultaneous mulligans, server random results). **Seam:** a play is
  engine state only when the test seam `engineSeats` gives its seats (null in the service). Protocol minor 5.
- **Device** (`protocol` `game/GameSession.kt`, not wired into the app yet): the shown model = confirmed events + own
  pending events (provisional); drafts (a private flow) sent as one submission; `received` confirms the own event,
  applies another player's (dropping pending, so the player sees the board update), or says `Reload` (another
  branch, or a revert that needs a checkpoint); `refused` drops the refused event and those on it.
- **Tests:** engine (`SimultaneousMulligansTest`, agreed results in `GameEndTest`); protocol `AuthorityTest`
  (device event = server event, stale race and resync, refusals, agreed result, concession, branches, seeded games of
  two devices in step with the server, drafts); server `EngineGameServiceTest` (inbox order, stale refusal, broadcast,
  warm and cold head, concession, attention; old-model plays refuse submissions).

**Left for the switch-over** (items 1, 3 and 5 built behind the seam in I-D56 step A, below):
1. *Engine definitions on the server:* **built** (`EngineDecks`, `protocol` `game/GameCards`, the reading moved from
   `client-storage`); left: turn `ATW_ENGINE_GAMES` on (every game then starts as engine state).
2. *App, phase 4–5:* the app's socket session loads `GameHead.engine` into a `GameSession`, sends `GameAction.Submit`,
   feeds `engineEvent`s and refusals in, and the table renders from `Engine.view`/`visible` + `TableState` with
   animations from the change list, keeping every current UI behaviour (the largest part: today's table renders
   `PlayState`/`BoardState` everywhere), including the 2026-11-03 playtest fixes (step arrow words from its planned
   tap, hidden in the opponent's turn unless you act, local prediction; view-only phase dialog; no timeline flicker on
   opening; the mana pool row) and their tests; pause points, early answers, drafts, settings as table changes; Autotap becomes
   a shared table setting (§9 point 3). *(Built in steps C–D1, §12–§13; superseded in part by I-D56: response modes
   instead of pause points, private settings outside the table state.)*
3. *Game end:* **built** (`PlayStore.endEngine`: a concession at once, any other result once confirmed after both
   local heads reached it, I-D56 §11); left: the app sends them instead of `GameAction.Concede`/`AcceptResult`.
4. *History reads:* timeline/history pages, undo (`UndoRule`) and explicit branching on engine events; the
   reproduction bundle (I-D43) from the stored inputs.
5. *Before the wipe, so the stored format changes once:* **built** (I-D56 step A below; the format change is the one
   the wipe covers).
6. *Then* move `db/pending/wipe_plays_for_engine_state.sql` to the next `V<n>` (test env first) and delete the old
   model: `automatum/` mapping, `PlayState`/`BoardState` game fields, `AutomatumOnly`, the manual protocol actions.

**For the owner:** (1) the wipe's timing (kept for the switch rather than run now); (2) §8's agreed results and
simultaneous pre-game decisions need new engine inputs (engine work, phase 3+); (3) Autotap becomes a shared table
setting per §2 (today it is per device; *answered the other way: private per player, `async-play.md` §16 choice 12*); (4) undo across more than 4 turn starts loads a checkpoint (≤ 63 events).

## 10. Built (I-D56 step A, 2026-11-03): stored format, engine prerequisites, server rules

Behind the seam: every game still starts on today's model unless the test seam or `ATW_ENGINE_GAMES=1` starts it as
engine state; nothing is wiped yet. This replaces §2's "undo, redo, branching … work exactly as now", §3's "branching
from an older point" and "random answers as given", and the phase 3–4 notes' `WhoActs.Anyone`, deliberate branches,
`TableChange.Settings`/`Act.SkipWhenNoChoice` and `Attention.of(state)` at the head, as `async-play.md` §17 lists.

- **Engine (`arcanum-engine`, generic, KDoc and CR-cited tests in `LibraryPlacesTest`):** `PlayerState.libraryPlaces`
  (known exact places and random-order groups since the last shuffle, kept by the recorder as `Change.LibraryPlaces`:
  cards put at a known place, looked at or revealed where they lie; a shuffle forgets them); with full information
  `Act.fill` records `Change.Shown(to, ids)` (what a player looked at; changes nothing); `Engine.revealed(before,
  changes, viewer)` (the cards an outcome shows a viewer at any moment, by card; the review's helper);
  `Engine.unknownOrder`/`reorderUnknown` (a host's new order of the unknown parts only; cards a player may look at
  stay); `Engine.blockCosts` (CR 509.1d).
- **Stored format (`protocol` `game`):** `EngineStep.assumedBy`; `TableState.secret` (32 bytes the server makes at
  the play's start, in the shared state both devices hold: owner's decision, I-D27) and `TableState.line` (`Line`:
  number and random answers used), moved by `TableChange.LineMoved` that `EventBuilder` adds itself; `LineRandom`
  (the k-th random answer of line n from SHA-256 of secret, n, k); `EventBuilder.startLine` (a new line's first step
  reshuffles both libraries' unknown parts, one answer per part); private `PlaySettings` (`ResponseMode` Full control /
  Normal / Auto-skip, Always assume resolves, Autotap) out of the table and engine state; the shared engine state
  keeps every player's `skipWhenNoChoice` off. `Act.NewLine(number)` starts a branch's line. Frozen sample
  re-recorded once.
- **Server rules (`protocol`):** `WhoActs` (own inputs by `Engine.decider`; engine-automatic answers: a pass
  `Engine.shouldPassFor` gives on the shared state, an order-free combat damage division with the suggestion, the
  decider's own early answer by `EarlyAnswer.keyOf`; random answers checked against the line's stream; assumed
  inputs of §3's kinds only, first in their event); `Acceptance` (§8: local heads, duplicate confirmation, branch with
  the next line, concession while behind, `stale`, `rewindRequested`); `InformationRule.revealing` (own library
  exempt) checked whenever the author's line is tentative or the event assumes; `Stops`, `NextStop` per response
  mode; `Rewind.requestNeeded` (free undo vs request); the result confirmed only once every local head reached it;
  `MainLine`.
- **Server (`EngineGames`, V57):** `engine_play.lines` (the line counter) and the open undo request;
  `engine_event.created_at`; `engine_seat` (local head, private settings, next stop and its time);
  `engine_async_round` (§15). Actions `EngineSeen`, `EngineRewind`, `EngineRequestRewind`, `EngineAnswerRewind`,
  `EngineCancelRewind`, `EngineSettings`; `Submit` lost `branch`; `EngineSnapshot`/`EngineUpdate` carry local heads,
  the reader's own settings, the request and the counter. Whose move it is comes per viewer from `engine_seat` (heads,
  revisions via `visibleRevision`, the lobby list); a player is notified only when their next stop is new. Protocol
  minor 6.
- **Tests:** `LibraryPlacesTest` (engine); `AuthorityTest` with `AsyncServer` (two devices at protocol level: assume →
  confirm, assume → mismatch → branch with the discarded line off the main line, line counter, info-rule stop and the
  real pass after it, next stops by mode, free undo vs request, reshuffle keeping a scried card's known place,
  concession while behind, result confirmation, seeded games of own inputs in step with the server);
  `EngineGameServiceTest` (the same through the service, per-viewer attention, async rounds, private settings,
  decks parsed on the server).

## 11. Built (I-D56 step B, 2026-11-04): the device core

Behind the seam, in `protocol` `game`; not wired into the app. Replaces the phase 3–4 note's `GameSession.received`
applying another player's event at once (now buffered past the local head, `async-play.md` §7).
- **`GameSession`** (data): the main line from a recent point (`lineStart`) to the head, the shown state at the local
  head plus the device's own pending events, the buffer past the local head (`showNext`), drafts (a draft at the local
  head while events are buffered starts a new line: a branch), `received` (confirmed / buffered / main line changed
  behind the shown state: taken back to the branch point / reload), `rewound`, `refused`, `stateAt`, `headModel`, `trim`.
- **`GameDevice`** (policy, speaking only `GameAction`s and engine revisions; times are the caller's): `status`
  (catch-up, playing, stopped at a decision stop, your move, waiting with the bar's reason and Assume offer, over),
  `step`/`playToStop`/`play`, `advance` (the player's own passes by mode and End turn, engine-automatic answers and
  public forced passes folded, assumptions or waits per §4 with the 60 s grace, the information-rule stop), `act` (at the
  head, or at a stop: the same input confirms, another branches; concession at any time), `begin`/`send` for flows,
  `assume`, `table` (at the head; staging behind it kept; stale ones re-made), suggestions, `confirmResult`,
  `earlyAnswer`, `settings`, `undo` (free or request), `requestUndo`/`answerRequest`/`cancelRequest`/`requestPreview`
  (frozen while open), `revision`/`refused` (notes: branched, taken back, request needed, refused), `take` (outbox with
  throttled `EngineSeen`), `repro`; `GameDevice.load(EngineSnapshot)`.
- **`GameRepro`** (I-D43): the device's bundle; `check()` replays it through `Authority.make`.
- **Protocol/server:** `EngineSnapshot.baseId/base/events` (the state at the earliest local head and the events after
  it); `WhoActs.automaticAnswer` (one source for device and server); `Acceptance.Accepted.authorHead` (a table-only
  event at the head doesn't move a local head that is behind).
- **Tests** (`protocol` commonTest): `GameDeviceTest` (scenarios: async keep assumed and confirmed on catch-up, Full
  control stops, branch and the assumer's take-back, live wait with grace and Always assume resolves, the
  information-rule stop, free undo and the request freeze, table-only events at the head, the bundle) and
  `DeviceSimulationTest` (seeded whole games of two devices through `DeviceSim`, live, async and mixed, with the leak
  oracle on every shown event and device = server state and bundle replay at the end).

**Next:** step C (app with I-D54 item 2, rendering from `GameDevice`; catalog entries per `async-play.md` §17), step D
(switch-over: the wipe as `V<n>`, delete the old model, adversarial review).

## 12. Built (I-D56 step C, 2026-11-04): the app

Behind a debug switch (`EnginePath`, Settings → Automatum → "Engine games (test)", debug builds only, off by default);
the server still starts engine games only with `ATW_ENGINE_GAMES=1` or the test seam. Choices: `async-play.md` §20.
- **Session:** `GameSession` loads `GameHead.engine` into an `EngineGame` (the app's wrapper of `GameDevice`), feeds it
  `GameRevision.engineEvent`/`engine` and refusals of its own actions, and sends its outbox (`Submit`, `EngineSeen`,
  rewinds, requests, settings) on the ordinary command queue; a device that can't follow re-subscribes for a new head.
  The open table ticks it every second (the other player's presence for the live wait and grace).
- **Rendering:** `automatum` `EngineProjection` makes the table's board (`PlayState`) from `GameModel` for one viewer
  (`Engine.visible`, placements, Stack entries, combat lanes or the player's own staging, players' pools and counters,
  the opening's statuses) and attaches `protocol` `game/EngineView` (`BoardState.engine`, transient): `EngineState.from`
  returns its state (full, or as a viewer sees it) instead of building one, `HostPriority` leaves it alone and
  `Automatum.flowOpen` reads its pending decision. So the table, panels, menus, computed P/T and combat previews are the
  same code as today's. `EngineCall.inputs` records the player's own inputs of a flow, which the cast and choice
  controllers send through the device instead of planning a batch.
- **UI:** `EngineTableScreen` (no history machinery), `EngineTableRoutes` (step arrow, opening, staging and places,
  undo), `EngineAsyncHost` (waiting bar, Assume blockers, catch-up, playback, decision-stop line, result line,
  indicator, notes, undo dialog, incoming request), `EngineToggleCells` (response mode, Always assume resolves,
  Autotap), Settings defaults for new games.
- **Tests:** `automatum` `EngineProjectionTest`; app `EngineGameTest` (opening, assumed keep and passes, echo
  confirmation, the other player's catch-up, stop and confirmation) and `EngineTableScreenshotTest` (five screenshots).

**Left for step D** (switch-over, wipe, deleting the old model) and what step C didn't build (*status: items 1, 3–11 and 13
done in D1–D2, §13–§14; what is still left is listed at the end of §14*):
1. Switch: `ATW_ENGINE_GAMES` on (test env first), the app's engine path the default (drop `EnginePath`), the wipe
   `db/pending/wipe_plays_for_engine_state.sql` as the next `V<n>`.
2. Delete the old model: the `automatum` mapping (`EngineState.from`'s building, `EngineBatch`, records and ledgers,
   `StepFlow`/`PausePoints` passing for others, `HostPriority`), `PlayState`'s game fields and gameplay actions,
   `AutomatumOnly`, the manual protocol actions for plays (`Play`, `PlayBatch`, `Undo`, `Redo`, `AcceptResult`), the
   app's old table path (`TableScreen`'s history branch, `TableHistoryController` continuation, `HistoryPlayback`
   branching, `HistoryExplorer`, `GameplayReplica`/`TreeCatchUp` for plays) and the I-D41 UI (`AutomatumPause`,
   `PauseBarHost`, `ActingForDialog`, `PauseController`, the old Response time / When the opponent can react / Full
   control / Skip settings); then slim `EngineProjection`'s `PlayState` into a render model.
3. The timeline on engine events: the strip, timeline mode and playback panel, event texts, icons and groups
   (HIS-001–031, HIS-061–077 on engine events; the engine table shows no strip yet), and the event text chip.
4. Flashes and delta tags from the engine's change list (HIS-031); card motion already animates from the board.
5. Token pictures (SYS-072) for the engine's tokens (they show the plain frame).
6. Early answers (STK-034, I-D33) and attack choices (CMB-036–CMB-038) as table changes; today they are only staged on
   the device. Shared attack staging if the owner wants it (choice §20.4).
7. Assume blockers with edited blocks; a board preview in the incoming request; the Undo dialog's target picker as the
   timeline strip.
8. New-game defaults per account on the server (LOB-113; today per device).
9. The branch notification (one Game activity notification per branch, §8).
10. Problem reports with `GameDevice.repro` (I-D43) for engine games.
11. Engine work off the main thread (the projection and the device's steps run on it now; check SYS-025 on a slow phone).
12. End-to-end runs on two devices against a server with `ATW_ENGINE_GAMES=1` (not run in step C), then the adversarial
    review of two-device races, branches, undo requests and reconnects.
13. The spec rows `async-play.md` §17 lists for `arcanum-atw-integration.md` and `arcanum-automatum.md` (they change when
    the switch-over ships), and the catalog's README note on the two paths.

**Adversarial review of steps A and B (2026-11-04):** fixed; see `async-play.md` §21 (table-only events commute, drafts
and confirmations, concession, random results, per-viewer times, batched main-line reads with a per-play event cache,
warm state kept on refusals, server checks).

## 13. Built (I-D56 step D1, 2026-11-04): feature parity of the engine path

Still behind the debug switch (`EnginePath`); today's model stays the default and nothing is wiped. Items 3–10 and 12 of
§12's list, the way `async-play.md` §22 records the choices:
- **Timeline** (item 3): `EngineTimeline` (app) lists the main line the device holds up to the local head and the
  device's own pending events, one event per acting point, groups by turn; `EngineEventText` gives each event its text
  and icon from its input, outcome and table part. The strip (`EngineStrip`, HIS-001–HIS-005), timeline mode
  (`EngineReviewPanel`: the board at a held event via `EngineGame.reviewPlay`, Previous / Auto / Next, the gear) and the
  event chip (`EventTextFeed.post`) run on it, view-only.
- **Flashes and motion** (item 4): `EngineGame.Transition` (each new board's forward and backward events since the board
  before, or a jump on a load) feeds `TableChanges.applyEngine`: life, P/T (from `Engine.view`), step and turn, face,
  control and shuffles (`EngineChanges`); card motion animates only when not a jump.
- **Token pictures** (item 5): `EngineProjection` takes a `TokenArt` and draws each engine token with its best matching
  picture (SYS-072), the token's maker from the engine's log; the device keeps each token's picture once found.
- **Shared staging, attack choices, early answers** (item 6, I-D54 §8): `EngineDrafts` (automatum) turns what the combat
  controls stage into `TableChange.AttackDraft` (attacks as the engine's `Attack`, choices included), `BlockDraft` and the
  viewer's own `EarlyAnswered` divisions, sent as table-only events at the head (kept on the device behind it); the
  projection shows the table's drafts as lanes and the records the controls read; a declaration clears its draft in its
  own event (`EngineGame.send`). Drafts show only in their step.
- **Assume blockers with edited blocks** (item 7): the screen lists the defender's untapped creatures beside each
  attacker; OK assumes the chosen blocks. **Board previews**: both undo dialogs draw the board at the point (the request
  dialog as far as the viewer has seen).
- **New-game defaults on the server** (item 8): `AccountSettings.playDefaults` (V58 `account.play_defaults`), set from
  Settings; `EngineGames.start` gives each seat its account's defaults; the device no longer gives them.
- **Branch notification** (item 9): `EngineGames.Outcome.branched`; the assumer's Game activity carries
  `GameNoticeContent.branched` (V58 `activity_summary.branched`), LOB-116.
- **Problem reports** (item 10): `AutomatumReports` sends the open engine game's `GameRepro` (`EngineGame.repro`, fitted
  to the report's bound) instead of today's Automatum bundle.
- **Two devices end to end through the app layer** (item 12, simulated): `EngineAppSim` (app tests) is one in-memory
  server (the main line with checkpoints, `Acceptance`, `Rewind`, local heads, requests) exchanging `GameRevision`s and
  refusals with two `EngineGame`s; `EngineTwoDeviceTest` plays seeded whole games (live, async, mixed: openings,
  assumptions, combat with shared staging and assumed blocks, catch-ups, branches, undo requests, results, the strip and
  reviewed boards every round) with the leak oracle, and checks each device against the server and its bundle's replay;
  `EngineSharedStagingTest` checks the staging both tables show and a life flash.

**Left for step D** (with §12's items 1, 2 and 13; *item 11 done in D2, §14*):
- Item 11, engine work off the main thread: not built. The device's own steps (`advance`: assumptions with their
  information-rule probes, folds), the server's events and the projection still run on the main thread. Moving them needs
  one engine thread that owns the `GameDevice` and publishes immutable snapshots (status, indicator, request, the
  projected board, the timeline) for the UI, which today reads the device directly in about 40 places; the player's own
  actions keep their synchronous local result (SYS-025). Measure on a slow phone first.
- Item 12 against a real server and devices (`ATW_ENGINE_GAMES=1` on the test environment) and the adversarial review.
- The Undo dialog's target picker as the timeline strip (§10 says so; it is still the list of short texts).
- An early division can't tell the first-strike damage step from the regular one (`EarlyAnswer.keyOf` has no step):
  given in the first-strike step it would answer the regular step's division too if it still fits.

## 14. Built (I-D56 step D2, 2026-11-04): the switch-over

Every game is an engine game; the choices are recorded in `async-play.md` §23.
- **Switch (§12 item 1):** the server starts every play as engine state, its decks parsed on the server (`EngineDecks`;
  lobby games, pod challenges and draft games all start through readiness); `ATW_ENGINE_GAMES` and the `engineDecks`
  seam are gone (`engineSeats` stays for tests that give their own seats). The app plays every engine head on its
  `EngineGame` (`EnginePath` removed); Settings → Automatum shows only the play defaults of new games.
- **Wipe:** `V59__engine_state_wipe_plays.sql` (the parked wipe): every game that started a play goes with everything
  that names it; accounts, decks, cubes, pods and drafts stay. Test environment first; production only when approved.
- **Deleted on the server (§12 item 2, server part):** the old gameplay path (`GameplayStore`, `GameplayEndpoint`'s
  checks, `GameplayMaintenance` and the `checkpoint-gameplay` command, `EndpointCollections`, baseline export and the
  history pages, the Automatum-only gate and its `automated` test seam, the old concession and result acceptance).
  `Play`, `PlayBatch`, `Undo`, `Redo`, `AcceptResult` and `Concede` are refused as `inapplicable`. `PlayStore` keeps the
  play row (its starting `PlayState`: ids, containers, players, card data), reads it without the old integrity checks,
  and records how an engine play ended in it (LOB-111's notice reads it). The old model's tests are deleted; lobby, pod
  and result tests end games by an engine concession (`EngineTestActions.concession`).
- **Engine work off the main thread (§12 item 11):** `EngineWorker.Background` is one engine thread that owns every
  `GameDevice`: the server's events, the device's own steps after an action (passes, assumptions with their probes),
  loads and the projection run there, and each change publishes an immutable `EngineGame.Shown` the table reads. The
  player's own action waits for its local result on the engine thread (SYS-025); dialogs read the device with
  `EngineGame.read`. Tests and simulations run `EngineWorker.Direct`. Not measured on a slow phone yet.

**Left after step D2** (deleting what nothing reaches any more but is still woven into code the engine path uses):
1. *App, today's table path:* `TableScreen`'s old body (shown only for the moment before an engine game first loads),
   `TableHistoryController`, `HistoryPlayback`, `HistoryExplorer`, `TreeAnnotationProjection`, the play replica in
   `GameSession` (`GameplayReplica`, `TreeCatchUp`'s play part, `TreeDownload`, `SessionHistoryReview`), the old
   gameplay and result actions of `LobbyTableActions`, the I-D41 UI (`AutomatumPause`, `PauseBarHost`,
   `ActingForDialog`, `PauseController`, `PauseSettings`/`FullControlSync`), the old history texts (`HistoryText`) and
   their ~40 test files (some cite entries that still hold on engine state, e.g. LOB-066, HIS-018–HIS-020: they need
   engine-path replacements, not just deletion).
2. *Protocol:* the old play actions above (refused by the server, still built by the app's old path), the manual
   `PlayAction`s, `GameplayRules`/`HistoryContinuation`/`AutomatumOnly`/`GameplayTree`/`TreeSync`/`RetainedIndex`
   and the rest of the old history model. `GameplayRules.preview` still stages combat lanes for the engine table
   (`EngineTableRoutes.local`), and the table renders `PlayState`: both go once `EngineProjection`'s `PlayState` is
   slimmed into a render model.
3. *automatum:* `EngineBatch`, `StepFlow`/`PausePoints` passing for others, `HostPriority`, `EngineState.from`'s
   building (the flows still branch on `BoardState.engine`).
4. *Database:* the old model's tables (`gameplay_mutation`, `gameplay_group`, `gameplay_tree_control`,
   `gameplay_endpoint_*`, `game_play_mutation`) stay empty after V59; a later migration can drop them.
5. *Catalog:* HIS-063–HIS-069, SYS-004, SYS-010 and SYS-061 describe today's model's texts and batches; they go with
   the code and tests above.
6. Measure the engine thread on a slow phone (SYS-025, SYS-030 reports); the timeline-mode board and the undo
   dialogs' boards are still worked out while the main thread waits (`EngineGame.read`/`reviewPlay`).
7. End-to-end runs on two devices against the test environment, then the adversarial review (§12 item 12).

**Playtest fixes (2026-11-04, switch-review #1, #10, #11):** a revision of an active play the device hasn't loaded (the
start, or what a resubscription replays before the head) waits for the engine head instead of the old tree catch-up; the
app's connection has no baseline/tree download any more, and a game that would need one says "This game can't be opened
on this device" (SYS-017) instead of reconnecting. A head past the revisions the device received reloads it. Only the
open game is loaded on the engine thread (list games keep their newest head until opened). An own action's board is
published before the device's own steps that follow it, and a probe logs (and with reports on, records) an own action
slower than 20 ms with its wait for the engine thread, its work and its board. The wait is faint words over the
battlefield with Assume in the step arrow's place (TRN-066); the indicator is a dot at the arrow's corner (TRN-068).
Then (owner decision for #11): the games list reads summaries changed since a token (V60 marks each game write with its
transaction id; SYS-075), only the open game's subscription carries its play and engine state (SubscribeGame.engine;
heartbeat heads are summaries), a game no longer open is let go on the device, and nothing else of the list is read while
a game is open. Engine.passCheck answers both no-choice questions from one search, and an own action's board is published
at GameDevice.act's performed point, before what follows by itself is folded in (Acted.Made).
