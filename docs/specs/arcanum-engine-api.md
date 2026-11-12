# Arcanum engine API rework

Approved by the owner on 2026-11-12. Delete this file in the commit that finishes the last phase.

Why: the engine was designed as an optional assistant to a manual board. ATW is now fully rules-simulated, but the
engine's surface still reflects the old design and has grown set by set:
- `Engine` is a flat list of about 40 functions, among them `speed`, `ringTemptations`, `dungeonsCompleted`,
  `dayOrNight`, `classLevel` and `ventureRoom`.
- Mechanic state is stored under text keys (`ledger["speed"]`, `memory["$paired-with"]`) and in fields tagged with
  set codes (`// fra`, `// hob`).
- `GameState` is a public data class with `var` fields.

The goal is a small core API, the rest grouped by purpose, typed status lists for mechanic state, and a state that
only the engine can change. Magic stays complex, so the API will too. What has to go is set-specific surface and
values stored under text keys.

## What stays as it is

- D56: `perform(state, input)` never writes the state it is given; it returns `Outcome(changes, pending)`.
- D58: an unfinished cast or resolution is a serializable `Continuation` inside the state (`pending`).
- D59: every change carries its before- and after-values, `apply` and `revert` are the only mutators, and every
  scenario checks that reverting all changes gives back the starting state.
- Engine behaviour. Every rule test keeps its meaning. A test changes only where it calls an API that moved or
  changed shape, and keeps its CR citation. No catalog entry changes. The parser and `arcanum-api` change only
  where they refer to moved engine API.
- The module boundary: Arcanum stays generic MTG code with no ATW knowledge (AGENTS.md, `verifyModuleBoundaries`).
- Inside the engine, the state stays mutable collections copied once per `perform`. Do not switch to persistent or
  immutable collections; it would cost speed for no gain.

## Public API after the rework

```kotlin
object Engine {                                    // the core, nothing else
    fun newGame(setup: GameSetup): GameState
    fun view(state: GameState): View
    fun options(state: GameState, player: PlayerId, prefs: Preferences = Preferences()): List<Input>
    fun perform(state: GameState, input: Input, prefs: Preferences = Preferences()): Outcome
    fun result(state: GameState): GameResult?
}
data class GameSetup(val seats: List<Seat>, val startingLife: Int = 20, val skipFirstDraw: Boolean = seats.size == 2)
data class Preferences(val noSkipWhenNoChoice: Set<PlayerId> = emptySet())   // replaces PlayerState.skipWhenNoChoice
```

The rest of today's `Engine` moves into objects grouped by who uses it. Overloads that take a precomputed `view`
(engine-responsiveness R2/R3) keep their overloads. The names below are suggestions; pick others where they clash
with an existing object, such as the internal `casting.Casting`.

| Group | Functions |
| --- | --- |
| Cast and activation screens | `castCosts`, `selection`, `finishable`, `finishableWith`, `fillPayment`, `announcedMayPay`, `anyReduction`, `refusal` |
| Combat declarations | `attackCosts`, `blockCosts` |
| Inspect | `explain` (both), `restrictions`, `lastKnown` |
| Host | `shouldPassFor`, `passCheck`, `decider`, `loopKey`, `visible`, `revealed`, `unknownOrder`, `reorderUnknown` |
| Card support | `manualParts`, `SUPPORT_VERSION` |
| Test probes | `layersWorkedOut`, plus the new counters from Phase 0 |

These functions are removed. Their values come from the view's status lists (see View below):
- `speed`, `ringTemptations`, `dungeonsCompleted`
- `dayOrNight`
- `classLevel`, `ventureRoom`

`otherViewDecision` goes in Phase 3.

There is no public way to build a game in the middle. Hosts start with `newGame` and then perform inputs.

Tests still need to set up boards directly:
- The engine's own `Scenario` builder is in the same module, so it can use `internal` access.
- Tests in other modules (protocol, automatum, androidApp, server; for example `EndTurnBenchmark`, which puts cards
  onto the battlefield) use a small setup API marked with a `@RequiresOptIn` annotation, such as
  `@ArcanumTestSetup`. Only test source sets may opt in. Add a build check that fails when main code opts in, next
  to `verifyModuleBoundaries`.

## State after the rework

`GameState` fields can be read from outside but changed only by `apply` and `revert`: make the constructor and
setters `internal`, and expose the mutable collections as `List` and `Map`. Keep the serialized field names unless a
phase changes the format anyway.

```kotlin
class GameState internal constructor(…) {
    val players: List<PlayerState>                 // turn order
    val objects: Map<ObjectId, GameObject>
    val battlefield: List<ObjectId>; val stack: List<ObjectId>; val exile: List<ObjectId>; val command: List<ObjectId>
    val stackItems: Map<ObjectId, StackItem>
    val effects: Map<EffectId, EffectObject>       // effects of resolved spells and abilities still in force
    val delayedTriggers: Map<EffectId, DelayedTrigger>
    val turn: TurnState; val priority: PriorityState; val combat: CombatState?
    val statuses: List<GameStatus>                 // DayNight
    val pending: Pending?
    internal val history: History                  // tagged events (D33), card-defined ledgers (D22)
    internal val bookkeeping: Bookkeeping          // timestamp counter, input count, waiting triggers, library places
    fun apply(change: Change); fun revert(change: Change)
}

data class PlayerState(
    val id: PlayerId, val life: Int, val startingLife: Int,
    val library: List<ObjectId>, val hand: List<ObjectId>, val graveyard: List<ObjectId>, val outside: List<ObjectId>,
    val counters: List<CounterEntry>,
    val manaPool: List<ManaUnit>,
    val loss: LossReason?,                         // replaces lost + lossReason
    val statuses: List<PlayerStatus> = emptyList(),
)

data class GameObject(
    val id: ObjectId.Card, val owner: PlayerId, val controller: PlayerId?, val zone: Zone,
    val definition: CardDefinition,                // non-null once Phase 3 removes viewer states
    val face: Int, val token: Boolean,
    val tapped: Boolean, val faceDown: Boolean, val phasedOut: Boolean,   // statuses of CR 110.5, read everywhere
    val counters: List<CounterEntry>, val damage: Int, val attachedTo: TargetRef?,
    val timestamp: Long, val controlSince: ControlSince?,
    val marks: List<ObjectMark>,                   // flags defined as data in arcanum-api (MarkRules)
    val statuses: List<ObjectStatus> = emptyList(),
    val cast: CastFacts? = null,
    val linked: Bindings,                          // CR 607 linked-ability memory only
    internal val uses: UseCounts,                  // activations, resolutions, triggers, modes per turn
)
```

Rules for sorting existing state:
- **Field:** every permanent can have it and the engine reads it on most passes. These stay fields: tapped, face
  down, phased out, counters, damage, attachment, timestamp and control start.
- **`marks`:** stays as it is. It holds flags defined as data in arcanum-api, which the parser adds without engine
  code.
- **`ObjectStatus`, `PlayerStatus`, `GameStatus`:** sealed and serializable, stored only when not at the default.
  These are for mechanics the engine codes by hand, with their values. Adding a mechanic means adding a subclass;
  `Engine` and the state classes don't change.
- **`CastFacts`:** facts about how the object was cast. From today's `ObjectFacts`: `castFrom`, `x`, `manaSpent`,
  `paid`, `alternative`, `modesChosen`, `chosenPlayer`, `paidWith`, `fused`, `manaSources`,
  `phyrexianPaidWithLife`.
- **`UseCounts`, internal:** per-turn counts the rules read (`activations`, `resolutions`, `triggers`, `modeTurns`).
- **Internal bookkeeping:** the engine's own scratch keys (the `$…` keys in ledgers, memory and `Bindings`, such as
  `$item`, `$from`, `$excess:` and `$drew-from-empty-library`) get typed internal homes, out of the public state.
- **History:** ledgers named by card definitions (D22, `Evaluator.resolvedKey`) stay a ledger keyed by name.

What moves into status lists:

| Today | After |
| --- | --- |
| `player.ledger["speed"]` | `PlayerStatus.Speed(value)` |
| `player.ledger["ring-tempted"]` | `PlayerStatus.RingTempted(times)` |
| `player.ledger["dungeons-completed"]` | `PlayerStatus.DungeonsCompleted(count)` |
| `PlayerState.designations` | `PlayerStatus.Designated(designation)` (`Designation` stays in arcanum-api) |
| `state.ledger[DAY_NIGHT]` | `GameStatus.DayNight(night)` |
| `memory[CLASS_LEVEL]` | `ObjectStatus.ClassLevel(level)` (the view shows 1 when none is stored) |
| `memory[Dungeons.ROOM]` | `ObjectStatus.VentureRoom(room)` |
| `memory[PAIRED_WITH]`, `memory[PAIRED_BY]` | `ObjectStatus.PairedWith(other)` (keep whatever the "by" side encodes) |
| `memory[BEARER_OF]` | `ObjectStatus.RingBearer(player)` |
| `copyOfCard`, `prepareSpellOf`, `deathtouchDamage`, `goadedBy`, `mergedAbove`/`mergedBelow`, `protector`, `flippedStatus`, `gainedInCopying`, `fixedCopies` | one `ObjectStatus` each (`Merged(above, below)` together) |
| `ObjectFacts.dealtCombatDamage`, `dealtDamage`, `heldPhasedOut`, `marksGained`, `turnedFaceUp` | `ObjectStatus` entries |
| `PlayerState.skipWhenNoChoice` | removed; `Preferences` passed to `options`, `perform` and the pass checks |

Find the rest with the set markers (`// dsk gaps`, `// fra`, … about 52 in engine main code) and the `Change.Ledger`
and `Change.Memory` call sites. Sort each by the rules above. Where one doesn't fit, choose the plainest option and
list it under "choices made".

`Change` gets one kind per status family, `StatusChanged(holder, before: Status?, after: Status?)`, plus
`CastFactsChanged` and internal bookkeeping changes. These replace `Ledger` for engine keys, `PlayerDesignation`,
`SkipWhenNoChoice`, `Memory` for engine keys, and `Facts`. `Lost` carries the `LossReason`. Changes stay MTG-level
and keep their `Meaning`s and `CausedBy`.

`Preferences` must give the same answers on every host that performs for a player: the device, the other player's
device and the server. ATW already stores the setting with the play (`protocol/.../Play.kt`), and the other player
may hold it (I-D27). Pass it to every engine call that used to read it from the state.

## View after the rework

```kotlin
class View(val objects: Map<ObjectId, ObjectView>, val players: Map<PlayerId, PlayerView>, val game: GameView)
data class ObjectView(
    val id: ObjectId, val owner: PlayerId, val controller: PlayerId?, val zone: Zone,   // controller after layer 2
    val characteristics: Characteristics, val base: Characteristics, val copiable: CopiableValues,
    val mechanics: List<AppliedMechanic>,          // "can't block", "hexproof from X", each with source and duration
    val summoningSick: Boolean,
    val statuses: List<ObjectStatus>,              // stored statuses plus derived ones (ClassLevel)
    val obj: GameObject,
)
data class PlayerView(val id: PlayerId, val life: Int, val mechanics: List<AppliedMechanic>, val statuses: List<PlayerStatus>)
data class GameView(val statuses: List<GameStatus>, val rules: List<AppliedMechanic>)  // rules = today's View.rules
```

ATW's `automatum/.../PlayerStates.kt` and `CardStates.kt` read these statuses with one exhaustive `when` each,
instead of calling the removed `Engine` functions. Their chips and texts stay exactly as they are.

## Performance guard

Optimisations already made must survive. These tests already count work and must stay green:
- `KeptViewsTest`, `ShortcutsTest`
- `GameModelViewsTest`, `GameDeviceTest`
- `EngineLayersOnceTest`, `EngineBoardOnceTest`
- the server's `EngineGameServiceTest` (`seatsWorkedOut`) and `WarmGameHeapTest`

Owner, 2026-11-12: two more guards for this rework. Neither asserts on time.

1. **An exact work-count test, written in Phase 0 before anything else changes.** It runs a few fixed scenarios:
   End turn on `EndTurnBenchmark`'s heavy board, a cast with targets and payment, and the 30-creature board wipe of
   `MassDeathTriggersScenariosTest`. For each it asserts exact counts of full layer runs, state copies
   (`deepCopy`), option searches, and the byte sizes of the stored events and of a checkpoint (`EngineJson`).
   - If a count goes down, update the number in the same commit.
   - If a count goes up, the test fails. Find the cause; don't just update the number.
   - Byte sizes may change in Phases 3–4, where the format changes. Give old and new sizes in the final reply.
     Growth over 5% needs the owner's approval.
2. **A benchmark comparison before and after every phase.** Before Phase 0, record master's numbers in
   `~/projects/atw-cache/engine-api-bench/`, not in the repo. The benchmarks:
   - `EndTurnBenchmark`, `MassTriggerBenchmark` (`-Patw.bench=true`)
   - `MassDeathTriggersBenchmarkJvmTest` (`-Parcanum.massTriggers.perf=true`)
   - the server's `EngineBenchmark` jar on the desktop

   Rerun them at the end of each phase. A case more than 15% slower at the median blocks the phase until the
   profile explains it and it is fixed. A rerun that happens to come out clean doesn't clear it.

## Phases

Each phase ends with the relevant suites green, the work-count test green and the benchmark comparison done.

- **Phase 0, guards.** The work-count test, the probes it needs (a `deepCopy` counter, an option-search counter,
  next to `layersWorkedOut`) and the benchmark baseline. Nothing else changes.
- **Phase 1, split `Engine`.** Move the functions into the groups above, keep the core five, update every call site
  (automatum, protocol, server, androidApp, arcanum-tools, tests) and the engine README. No change to behaviour or
  format. Merges to master by itself.
- **Phase 2, lock down `GameState`.** Internal constructor and setters, read-only collections, the
  `@ArcanumTestSetup` opt-in API and its build check, and other modules' tests moved to it. The serialized format
  must not change, so `GameState` JSON stays byte-identical. Merges to master by itself.
- **Phase 3, remove the per-viewer states (D60).** First confirm that ATW performs only on full-information states
  and uses viewer states nowhere but display (`protocol/.../EngineView.kt` builds them; find every reader). If it
  depends on them anywhere, stop and report to the owner. Otherwise remove:
  - `GameState.viewer` and the null definition of unknown objects
  - the `HostFill` and `OtherView` decisions and their answers
  - `Change.Filled` and `Change.Forgotten`
  - `otherViewDecision`
  - the new ids of shuffled hidden cards, and EngineRun's handling of all these

  `visible` and `revealed` stay for display. This phase changes the format.
- **Phase 4, typed state.** Status lists, `CastFacts`, `UseCounts`, internal bookkeeping, `loss`, `Preferences`,
  the new `Change` kinds, and the view additions with ATW's `PlayerStates`/`CardStates` reading them. This phase
  changes the format.
- **Phase 5, documentation.** Bring the engine README and `docs/specs/arcanum-automatum.md` §7.0 up to date. They
  should describe this API; mention old decisions only where it matters. Delete this spec.

Phases 3 and 4 change the stored format: engine changes in `GameEvent` and the state in checkpoints. Build them on
one branch and merge them to master together, so that **one release wipes started games once**. The owner approved
that single wipe on 2026-11-12. Do it with a Flyway migration that deletes started plays, as V41 did. Release note:
one `mod:` line in plain words saying games in progress were ended by this update. No production deployment may
carry Phase 3 without Phase 4. Do not add readers for the old format (single-version rule).
