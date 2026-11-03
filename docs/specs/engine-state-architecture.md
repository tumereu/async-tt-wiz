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
