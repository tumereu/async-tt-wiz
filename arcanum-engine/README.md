# arcanum-engine

The rules engine: computes derived state (layers, P/T, keywords), applies rules where
nothing needs deciding, asks for decisions where something does, and returns
MTG-level changes. It owns the API its callers use and knows nothing about any host
app. Design: `docs/specs/arcanum-automatum.md` §4.1 and §7 (§7.0 is the API, D56–D61).

## Interface

```kotlin
Engine.view(state): View                         // characteristics after layers (CR 613), rules in force
Engine.options(state, player): List<Input>       // what a player may do now: exact (every input listed is accepted)
Engine.perform(state, input): Outcome            // Outcome(changes, pending); never writes `state`
Engine.newGame(seats, startingLife): GameState   // a game before it begins; Input.StartGame starts it (CR 103)
Engine.result(state): GameResult?                // Won(player) or Draw once the game is over (CR 104)
Engine.visible(state, player): Set<ObjectId>     // the objects whose identity a player may know now (D60)
state.apply(change); state.revert(change)        // the only mutators (D56, D59)
```

`options` is exact: `perform` accepts every input it lists, and an open decision lists an answer whenever one is found
(random results and host fills are the host's, D25, D60). Answers are listed to every player (P7), except answers
naming cards hidden from that player (D60); for choices of several
objects, targets or modes a working sample is listed, not every combination. A cast or activation is listed when it can
be begun, everything known at its start can be done (its first targets, the objects its costs use, a mode with what it
needs) and mana is found for its cost as it stands then (X as 0); `OptionsFuzzTest` checks the contract on seeded random
games of cube cards. `revert` takes changes back last first; after a host drops older turns' events
(`GameState.forgetOlderTurns`) undo still reaches those turns, but "this turn" questions there see none of their events.

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
  `DeclareAttackers`, `DeclareBlockers`, `Answer`, `Cancel(player)`, `StartGame(options)`, `Concede`. Each names the
  acting player (answers excepted: anyone may answer, P7); only the player casting or activating may cancel.
- `decision/` — `Decision` (data only, with id and deciding player) and its `Answer`;
  `Continuation`, the open flow the host stores opaquely (D58).
- `view/` — `View`/`ObjectView`/`Characteristics`, `AbilityOrigin` (an ability's path, D42/D44);
  the layer system (`Layers`, `Copiable`), where abilities function (`Functioning`, CR 113.6),
  subtype lists (CR 205.3); `KeywordMeanings`: a keyword's abilities, its expansion or, for a keyword not automated
  yet, abilities that stop with a `Manual` step at its natural moment (cast, enters, attacks, dies, …; an activated
  keyword's cost is paid by the engine and only its effect is by hand), so no keyword is silently ignored (D61).
- `query/` — `Evaluator`: what objects, players, filters, values and conditions of a definition
  mean in a game; `History` answers "this turn" questions from the log.
- `setup/` — starting and ending a game: `Seat` (player, deck, sideboard), `StartOptions`, the setup stages
  (`Continuation.Setup`), `GameResult` and `GameState.result`, conceding and players leaving (CR 800.4).
- `casting/`, `resolution/`, `trigger/`, `sba/`, `combat/`, `turn/`, `mana/` — one object each
  that `Perform` (the `perform` flow: input, then state-based actions and triggers until
  nothing happens, then priority, CR 117.5) calls.

## Starting and ending a game

`Engine.newGame(seats)` makes the state before the game: each seat's deck is its library in the order given, its
sideboard is outside the game (CR 103.2a), life is the starting life (CR 103.4), and the starting player skips their
first draw in a two-player game (CR 103.8). `Input.StartGame(StartOptions(…))` then runs CR 103 as stages, each
committed once done (D58) and redone with its answers when it waits (D29): the starting player (given, chosen by a given
chooser, or chosen by a player a host-answered die roll picks, CR 103.1); companion reveals in turn order when the deck
meets the condition (CR 103.2b); the host's shuffles (D25); opening hands; mulligans (CR 103.5: declarations in turn
order, mulligans at once, then each puts as many cards on the bottom as mulligans taken, the first free in multiplayer,
103.5c); turn 1 from its upkeep with priority. Opening-hand actions (CR 103.6) have no cube card yet: the stage list
marks where they go.

`Input.Concede(player)` is accepted at any time (CR 104.3a). `GameState.result` is `Won` when one player is left
(CR 104.2a) and `Draw` when the last players lose at once (CR 104.4a); then `options` are empty and `perform` refuses
input. In a game that goes on, a player who lost or conceded leaves it (CR 800.4a): their objects leave the game, what
they control is exiled, their abilities on the Stack and waiting triggers go, priority they held passes on, and their
turn goes on without them (CR 800.4j).

## Hidden information

The state is one viewer's view (`GameState.viewer`, D60; null: full information, as a host knows the game, the way
tests play). One rule, in the event pipeline and the effects that look:

- **Filled when it matters or becomes visible:** a card put into a graveyard, the Stack, exile or the battlefield face
  up, a face-down card leaving or turning face up (CR 708.9), a revealed card, one looked at or searched, one drawn
  into the viewer's hand, one picked at random (a host `Random`, CR 701.20c), one its controller may look at any time
  (CR 401.5, checked before priority): the engine asks `Decision.HostFill` for exactly those cards (`public`, or for
  its `player` only) before going on, so replacements and triggers see them (madness, "whenever a land card is put
  into your graveyard"). Reveal-until turns over one card at a time.
- **Forgotten when hidden from the viewer:** a card put into a hand the viewer can't see or turned face down where the
  viewer can't look (`Change.Forgotten`); a shuffle or a random order into a library asks the host for new ids
  (`RandomKind.Objects.newIds`) instead of an order, and the cards become unknown under them, so their order can't be
  tracked (CR 701.24a, D38). A card put into a library known stays known until it is shuffled. With full information
  nothing is forgotten.
- **What each player may see:** a face-down permanent is a nameless 2/2 creature even unseen (CR 708.2a); `options`
  offers another player no answer naming cards hidden from them; `Engine.visible(state, player)` says what a host may
  show each player. A pending decision over cards hidden from others (a search's matches) is shown only to its player,
  and a fill that isn't public, asked in another player's view, is done in that player's view (D31).

Every answer, a player's or the host's, is checked against its decision before anything happens (`AnswerCheck`: kind,
allowed values, counts, uniqueness, zones, exactly the asked fills, new ids): a wrong one is refused with
`IllegalArgumentException`.

## Status

- Done: the API types, game state, reversible changes (apply/revert tested for every kind),
  the derived view: layers 1 (copy effects with exceptions, face down, split cards), 2, 4
  (incl. CR 305.6/305.7 land types and 205.3d), 5, 6 (granted and lost abilities, keyword
  counters, keyword expansions with label rules), 7a–7d with counters; CDAs first,
  timestamps and dependencies (CR 613.6–613.8); static abilities where they function,
  their conditions; effect objects; rules that are not characteristics per object, player
  and game.
- Done, the game flow: `options` (exact, see above); priority
  (CR 117) and steps with their turn-based actions (untap with phasing and stun, the draw and
  the first-turn skip flag `GameState.skipFirstDraw`, cleanup discard (through the event pipeline, so madness
  applies) and damage removal, another cleanup step after priority in one (CR 514.3a),
  durations ending, mana emptying), N players and extra turns; playing lands (extra land plays,
  permissions, modal double-faced lands); casting (CR 601.2) and activating (CR 602, 606) as a
  continuation: modes, optional costs, X, targets with hexproof/shroud/protection, division,
  "A or B" costs, objects for costs, mana payment with a suggested tapping (§7.8), cancel; the
  events of paying (sacrifice, discard, exile, return, mill, counters put, delve's exile) go through
  the event pipeline, so replacements apply (unearth, madness, counter replacements), and a
  replacement decision while paying waits (`Choices.paying`, D29);
  alternative costs and permissions (evoke, flashback, escape, overload, retrace, morph, "rather
  than pay"), bestow (an Aura spell and permanent while bestowed, a creature once it stops being, CR 702.103b, e, f),
  splice (CR 702.47: cards in hand offered as the spell is cast, their text added after its own, their costs added, a
  card whose target names clash with the spell's not offered), collect evidence as a cost (CR 701.59a), convoke, delve, cost changes (CR 118.7), Phyrexian, hybrid, snow, restricted mana;
  mana abilities without the Stack (their other effects at once, a part needing a decision
  by hand; "in any combination of colors" asks a colour per mana), triggered mana abilities at
  once (CR 605.4a); special actions (suspend, turning face up); combat:
  declarations with their restrictions and as many requirements as they allow (CR 508.1d, 509.1c),
  the last blockers declaration making attackers blocked or unblocked in the same change (CR 509.1h),
  removal from combat checked after every event (CR 506.4), first/double strike steps, trample,
  deathtouch, lifelink, infect, wither, toxic, dividing damage as its controller chooses whenever
  more than one division is legal (CR 510.1c–d, 702.19b; `Decision.Divide.suggested` gives one). A spell becomes cast with the
  `StackItemAdded` carrying `Meaning.Cast`; mode targets with a repeated slot name are kept
  under `name@position` (`targetKey`).
- Done, what happens (`action/`, `resolution/`, `trigger/`, `sba/`): the event pipeline
  (`Actions`; every game action of effects, state-based and turn-based actions): zone changes
  with new ids, last known information and "as enters" steps, counters a permanent or token enters
  with put on it through counter replacements (CR 122.6, one entry per kind), damage with
  protection, prevention effects and shields (amounts left, "can't be prevented"), infect, wither,
  deathtouch, lifelink, toxic and excess; life (paying life is losing it, through life-loss replacements, CR 119.4), counters, tapping, tokens (D35 ids), draws,
  mills, discards (cards put into one library position together in their owner's order, CR
  401.4), host-ordered shuffles (D25) and host fills and forgetting (D60, see Hidden information); mana costs paid while resolving
  ("unless … pays", ward) with a payment decision, not asked when they can't be paid (CR
  118.3). Replacement effects
  (CR 614, 616): own replacements first, control, copy, transformed, then the affected player's
  order when it matters, prevention effects among them (CR 616.1: not asked when one prevents all
  and the others only change the amount, or when only prevention is left: shields last); each once per event; "instead", modifications, optional ones, linked
  "when applied" triggers, one-use shields and regeneration (D26). Resolution (CR 608): target
  re-check and fizzling, partial legality, every `Step` and every `Effect` (keyword actions
  without a table entry are done directly or by hand), effect objects with locked sets (CR
  611.2c), delayed and reflexive triggers (snapshots of objects that already left, CR 608.2h), "until"
  returns as a one-shot effect right after the event without the Stack (CR 610.3), copies of spells, casting from an
  effect (CR 608.2g) with the casting flow's own plan (face, modes, optional costs, X, targets, cost objects, mana;
  without paying its mana cost with X 0, for another cost such as madness, or for its own; not cast without legal
  targets), the card going to the graveyard/exile/battlefield (an
  Adventure to exile, castable from there as the creature, CR 715.4); mana paid on resolution as a
  `ManaPayment` decision (by hand when no payment is found). Permanents enter with their printed
  loyalty or defense (CR 306.5b, 310.4b); Sagas get lore counters as they enter and after the
  draw step (CR 714.3); levelers' level abilities count level counters (CR 711). A step that needs a decision is
  taken back and redone with the answers so far (D29); earlier steps stay done (D31).
  Triggers: detection as each event is recorded, looking back for CR 603.10a events, batches,
  "nth this turn" (counting batches for "one or more"), per-turn limits (counting waiting triggers),
  intervening and trigger-only conditions (an object that left is in no zone, CR 400.7), state triggers,
  chapters, "triggers an additional time"; waiting triggers in `GameState.triggered`, put on
  the Stack in APNAP order with their order, modes ("not chosen (this turn)") and targets.
  State-based actions (CR 704.5a–q,
  v, Sagas 714.4) as one event, the legend rule as a decision. `History`: "this turn" counts.
  Events outside resolution (land plays, the draw step's draw, combat damage) go through
  `Events.run` and wait with `Continuation.Event`.
- Done, the game around it (`setup/`): starting a game (CR 103) with companions and London mulligans, conceding,
  the end of the game (CR 104) and players leaving a multiplayer game (CR 800.4a, j, k).
- Not yet: additional combat
  phases and skipped turns (by hand), several keyword actions (manifest, cloak, venture, …),
  cycling/crewing/saddling/expending/day-night events, `CountersPut.causedBy`, `nthThisTurn`'s
  `during`, "do this only once each turn", repeated target slot names of repeated modes
  (bound under the first); in the flow: attack costs and
  "as it attacks" costs, battles as attack targets,
  other keyword actions as costs (behold, forage, exert), "next spell" cost changes used
  up. Also missing: text-changing
  effects (layer 3, no mechanic exists), copy effects from static abilities (effect objects
  only), the Inspect breakdown of where a value comes from. Around the game: opening-hand actions (CR 103.6) and
  actions "any time you could mulligan" (103.5b), effects that change the starting hand size, team and other variant
  starts; a player who leaves while a flow of theirs other than a cast waits for a decision (the decision stays open,
  CR 800.4h is not applied); restarting a game (CR 104.1).

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
`attach`, `effect`, N players), moves (`alice.casts(…, target = …, x, modes, kicked, option,
choosing, paying)`, `activates`, `plays`, `passPriority()`, `allPass()`, `advanceTo(step)`
(declaring no attackers or blockers on the way), `answers`, `attacks`, `blocks`, hand-made
`change`/`move`), expectations (`expect { … }`). A card name means the card where the move looks for it (`casts`: the
player's hand first; `activates`: their permanents; targets, attacks and expectations: the battlefield, then the Stack;
choices: the decision's options, a name given twice meaning two cards); elsewhere a name in several zones fails unless
`card(name, zone = …)` or an index says which. `effect(…)` locks its objects as a resolution does. Random results are scripted with
`random(…)` or seeded; hidden cards (`hidden = true`, or everything a `viewer(player)` can't see) are filled in by the
harness, which is the host and also gives new ids for shuffles in a viewer's game. Scenarios about resolution can skip casting: `alice.castsWithoutCosts(…)`,
`activatesWithoutCosts(…)`, then `resolve()` / `resolveStack()` / `settle()` / `beginStep(…)`
run resolution, state-based actions and triggers without priority. Every scenario checks D59:
reverting all its changes restores the state after setup.

`cube/Chunk*ScenariosTest.kt` play each invented cube card the way a player would (cast with its
costs, abilities activated, triggers triggered) and check the outcome a Magic player expects.

`review/Review2LongGames.kt` plays whole games from `Engine.newGame` with random 40-card cube decks by a seeded random
policy over `Engine.options`, checking D56/D58/D59 and zone, id, counter and timestamp invariants after every input; with
a viewer it plays on that player's view, the driver being the host, and checks that public cards are known and hidden
ones aren't (D60). Two seeds and one seed on each player's view run in the normal suite, more with
`-Parcanum.longGames=N` (`-Parcanum.longGames.players=3`, `-Parcanum.longGames.from=S`,
`-Parcanum.longGames.viewer=alice`, `-Parcanum.longGames.malformed=true` to also try odd answers of every kind at every
decision) and `--tests '*Review2LongGamesJvmTest*'`. `hidden/ViewerKnowledgeTest` plays the hidden-information rule on
both players' views with every host answer through JSON; `decision/AnswerCheckTest` the answer checks. `review/Review*Test.kt` are the adversarial
reviews' scenarios; failing ones are `@Ignore`d with their finding number.

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
