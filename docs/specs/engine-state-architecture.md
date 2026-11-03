# Engine state as the game state — proposal (I-D54)

Status: **approved by the owner** (2026-11-03), queued. Not to be started until the current round is done and deployed to
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
    per-player play settings (Full control, Skip when I have no choice, Autotap), early answers (I-D33), the shared attack
    staging / block draft before it's declared, result suggestions and their answers.
- **History = a tree of events; each event has two optional parts, applied and reverted together:**
  - *engine part*: the player's input(s) plus the engine's own reversible `Change` list (`GameState.apply/revert`, D59);
  - *table part*: a list of reversible table changes (before → after).
  Both parts are always reversible, so undo, redo, branching and timeline playback work exactly as now: they walk events.
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
  again). Branching from an older point stays an explicit action as today.
- **Trust the client, even with randomness:** random answers (shuffles, coin flips, random choices — D25 host answers)
  are made on the device and sent with the input, so the local result is final at once; the server uses them as given.
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
behaviour; catalog entries change only where wording names internals.

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
- **Pi 5: not measured** (the Pi is production; running a benchmark there wasn't authorized). Estimate from single-thread
  benchmarks (Cortex-A76 2.4 GHz vs Zen 3 4.6 GHz, about 2.5–3× in Geekbench; a JVM with less cache and memory bandwidth
  more): **3–4× slower**, so typical inputs ~3 ms median and ~10 ms p95, heavy ones 50–400 ms, a cold-start game's first
  inputs up to ~0.5 s. Fine for a turn-based async game; the owner can confirm with the spike test's numbers on the Pi.

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

**For the owner:** (1) the wipe's timing (kept for the switch rather than run now); (2) §8's agreed results and
simultaneous pre-game decisions need new engine inputs (engine work, phase 3+); (3) Autotap becomes a shared table
setting per §2 (today it is per device); (4) undo across more than 4 turn starts loads a checkpoint (≤ 63 events).
