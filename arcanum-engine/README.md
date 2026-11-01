# arcanum-engine

The rules engine: computes derived state (layers, P/T, keywords), applies rules where
nothing needs deciding, asks for decisions where something does, and returns
MTG-level changes. It owns the API its callers use and knows nothing about any host
app. Design: `docs/specs/arcanum-automatum.md` §4.1 and §7 (§7.0 is the API, D56–D61).

## Interface

```kotlin
Engine.view(state): View                         // characteristics after layers (CR 613), rules in force
Engine.options(state, player): List<Input>       // what a player may do now
Engine.perform(state, input): Outcome            // Outcome(changes, pending); never writes `state`
state.apply(change); state.revert(change)        // the only mutators (D56, D59)
```

- `state/` — `GameState` (serializable, mutable): players in turn order with their zones,
  life, counters, mana pool; every card/token object by `ObjectId` (`Card(card, zoneChanges)`
  or `StackItem(id)`, D21/D35) with owner, controller, zone, face, status, counters, damage,
  attachment, timestamp (D34), control start (CR 302.6), card memory and per-instance facts
  (D22/D41); unknown objects have no definition (D60); Stack items with targets, modes, X,
  names and snapshots; effect objects and delayed triggers (D19, D26); turn, step, priority;
  ledgers; the tagged event log (D33); the open `Pending`. `TokenIds`: v5 UUID token ids.
- `change/` — `Change` (sealed, serializable): every change carries before- and after-values
  and its `Meaning`s (cast, died, sacrificed, …); events are logged for "this turn" questions.
  `Change.Reverted` takes a change back inside a batch (cancelling a cast). `Changes` builds
  changes from the state they apply to (a zone change makes new ids and timestamps).
- `input/` — `Input`: `PlayLand`, `Cast(card, way)`, `Activate`, `PassPriority`,
  `DeclareAttackers`, `DeclareBlockers`, `Answer`, `Cancel`. Each names the acting player.
- `decision/` — `Decision` (data only, with id and deciding player) and its `Answer`;
  `Continuation`, the open flow the host stores opaquely (D58).
- `view/` — `View`/`ObjectView`/`Characteristics`, `AbilityOrigin` (an ability's path, D42/D44);
  the layer system (`Layers`, `Copiable`), where abilities function (`Functioning`, CR 113.6),
  subtype lists (CR 205.3).
- `query/` — `Evaluator`: what objects, players, filters, values and conditions of a definition
  mean in a game; `History` answers "this turn" questions from the log.
- `casting/`, `resolution/`, `trigger/`, `sba/`, `combat/`, `turn/`, `mana/` — one object each
  that `Perform` (the `perform` flow: input, then state-based actions and triggers until
  nothing happens, then priority, CR 117.5) calls.

## Status

- Done: the API types, game state, reversible changes (apply/revert tested for every kind),
  the derived view: layers 1 (copy effects with exceptions, face down, split cards), 2, 4
  (incl. CR 305.6/305.7 land types and 205.3d), 5, 6 (granted and lost abilities, keyword
  counters, keyword expansions with label rules), 7a–7d with counters; CDAs first,
  timestamps and dependencies (CR 613.6–613.8); static abilities where they function,
  their conditions; effect objects; rules that are not characteristics per object, player
  and game.
- Not yet (each throws "not implemented yet"): `options`, and every `perform` flow:
  casting and activating, mana payment, resolution and replacements, triggers and the event
  matching behind "this turn" (`History` answers "nothing happened"), state-based actions,
  combat, priority and steps. Also missing: text-changing effects (layer 3, no mechanic
  exists), copy effects from static abilities (effect objects only), the Inspect breakdown of
  where a value comes from.

## Tests

`commonTest` has a scenario harness and the invented cube cards by name:

```kotlin
@Test
fun anthemPumpsOtherElves() = scenario {
    // CR 613.4c
    alice.battlefield("Elvish Hierophant", "Elvish Seer")
    bob.battlefield("Umbral Elves")
    expect {
        "Elvish Seer".power shouldBe 2
        "Umbral Elves".power shouldBe 1
    }
}
```

Setup (`alice.battlefield/hand/library/graveyard/exile`, `life`, `counters`, `turn(…)`,
`attach`, `effect`, N players), moves (`alice.casts(…, target = …)`, `activates`, `plays`,
`passPriority()`, `allPass()`, `advanceTo(step)`, `answers`, `attacks`, `blocks`, hand-made
`change`/`move`), expectations (`expect { … }`). Random results are scripted with
`random(…)` or seeded; hidden cards are filled in by the harness. Every scenario checks D59:
reverting all its changes restores the state after setup.

The cube cards come from the parser's `cube/Chunk*Test.kt` files, copied by a test into
`cards/CubeCardTexts.kt`; after the parser tests change, regenerate with
`./scripts/gradle.sh :arcanum-engine:jvmTest -Parcanum.regenerate=true --tests '*CubeCardsInSyncTest*'`.
`cards/InventedCards.kt` holds hand-written definitions for structures the cube lacks.

## Rules

- Decisions are data, not callbacks: the engine keeps no session between calls.
- No hidden memory: anything to remember is returned to the caller as a visible object.
- Tests cite Comprehensive Rules numbers of the pinned version (`// CR 613.4c`).
- Generic MTG code: no dependency on any ATW module (the parser is a test-only dependency);
  Kotlin Multiplatform common code.
