# arcanum-engine

The rules engine: computes derived state (layers, P/T, keywords), applies rules where
nothing needs deciding, asks for decisions where something does, and returns
MTG-level changes. It owns the API its callers use and knows nothing about any host
app. Design: `docs/specs/arcanum-automatum.md` §4.1 and §7 (§7.0 is the API, D56–D61).

## Interface

```kotlin
Engine.view(state): View                         // characteristics after layers (CR 613), rules in force
Engine.explain(state, id): Explanation?          // where one object's characteristics come from, effect by effect
Engine.options(state, player): List<Input>       // what a player may do now: exact (every input listed is accepted)
Engine.perform(state, input, trace?): Outcome    // Outcome(changes, pending); never writes `state`; trace: see Tracing
Engine.newGame(seats, startingLife): GameState   // a game before it begins; Input.StartGame starts it (CR 103)
Engine.result(state): GameResult?                // Won(player) or Draw once the game is over (CR 104)
Engine.visible(state, player): Set<ObjectId>     // the objects whose identity a player may know now (D60)
Engine.shouldPassFor(state, player): Boolean     // a host passes priority for this player now (I-D3, I-D18)
Engine.decider(state, player): PlayerId          // who makes this player's decisions now (CR 723; see Controlling another player)
Engine.loopKey(state): LoopKey                   // the state without ids, timestamps, history: finding loops (CR 104.4b)
Engine.SUPPORT_VERSION                           // raised when what the engine leaves to the players changes
Engine.otherViewDecision(state, filled)          // what a player decides in their view while this view waits (D31)
state.apply(change); state.revert(change)        // the only mutators (D56, D59)
```

`options` is exact: `perform` accepts every input it lists, and an open decision lists an answer whenever one is found
(random results and host fills are the host's, D25, D60). Answers are listed to every player (P7), except answers
naming cards hidden from that player (D60); for choices of several
objects, targets or modes a working sample is listed, not every combination. A cast or activation is listed when it can
be begun, everything known at its start can be done (its first targets, the objects its costs use, a mode with what it
needs), a payment is found for its mana cost as it stands then (X as 0; convoke and delve counted) and its next decisions
have answers that lead to its end (looked ahead within a bound: a target that depends on X or on an earlier target).
Inside a cast or activation only answers after which it can still be finished are listed, so no decision it reaches is a
dead end; X is listed at the least values that work, up to what can be paid. `OptionsFuzzTest` checks the contract on
seeded random games of cube cards, failing on any open player decision without a listed answer. `revert` takes changes back last first; after a host drops older turns' events
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
  changes from the state they apply to (a zone change makes new ids and timestamps). Changes made by a spell's or
  ability's instructions carry `CausedBy` (its source, the name it had, controller, Stack item) for history lines;
  damage carries its source as it was (`DamageDealt.sourceSeen`).
- `input/` — `Input`: `PlayLand`, `Cast(card, way)`, `Activate`, `PassPriority`,
  `DeclareAttackers`, `DeclareBlockers`, `Answer`, `Cancel(player)`, `StartGame(options)`, `Concede`, and the host's
  `MandatoryLoop` (see Loops). Each names the acting player (answers excepted: anyone may answer, P7); only the player
  casting or activating may cancel.
- `decision/` — `Decision` (data only, with id and deciding player) and its `Answer`;
  `Continuation`, the open flow the host stores opaquely (D58). Decisions carry text for people (`Texts`): a "you
  may", a choice or a cost to pay reads as the sentence of the ability's printed text, modes and triggers to order
  as their printed lines (with the trigger's `AbilityOrigin`), replacements to order as their ability's text; plain
  words where a definition has no text.
- `view/` — `View`/`ObjectView`/`Characteristics` (`ObjectView.base`: the values after layer 1 only, to compare
  with), `AbilityOrigin` (an ability's path, D42/D44); `Explanation` (`Engine.explain`, a host's Inspect): the values
  after layer 1, then each continuous effect that changed the object in the order applied, with its layer, timestamp,
  values before and after and its `EffectSource` (a static ability, an effect object, counters);
  the layer system (`Layers`, `Copiable`), where abilities function (`Functioning`, CR 113.6),
  subtype lists (CR 205.3); `KeywordMeanings`: a keyword's abilities, its expansion or, for a keyword not automated
  yet, abilities that stop with a `Manual` step at its natural moment (cast, enters, attacks, dies, …; an activated
  keyword's cost is paid by the engine and only its effect is by hand, saddle's excepted), so no keyword is silently ignored (D61).
  `ViewCache`: inside `perform` and `options` the engine's own working copy keeps its view until a change that can
  make it differ (a host's state never caches; `Engine.view` works it out afresh). Within a layer, dependencies are
  tried only for pairs where one can exist (CR 613.8a) and resolutions are kept until the board changes in what they read.
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
they control is exiled, their abilities on the Stack and waiting triggers go, their face-down permanents and spells are
revealed (CR 708.9; in a viewer's state those it doesn't know stay unknown, as leaving can't wait for the host),
priority they held passes on, and their turn goes on without them (CR 800.4j).

## Loops

- **Safety cap:** one `perform` call makes at most `Loops.MAX_ROUNDS` rounds of state-based actions and triggers (200)
  and `Loops.MAX_CHANGES` changes (20,000, checked between rounds); then it stops with the loop choice ("Possible
  loop: …", `Continuation.Loop(LoopStop.Cap)`) instead of hanging on a chain of mandatory triggers that never gives
  priority. No cube, Bloomburrow or Duskmourn scenario needs more than a handful of rounds.
- **The loop choice** is a `Decision.ChooseOption` with options `LoopStop.CONTINUE` and `LoopStop.DRAW` and a `text`
  saying why: Continue goes on (at the cap, with the next round in a new call); Draw logs `GameEvent.MandatoryLoop`
  and every remaining player leaves the game together (`GameResult.Draw`, CR 104.4b).
- **Mandatory loops (CR 104.4b, 732.4):** the engine can't see what happens between calls, so the host finds them. A
  host passing for players (`shouldPassFor`) keeps `Engine.loopKey` of each state it passes in with something on the
  Stack (the state as JSON without its log, ledgers, "this turn" counts of uses and timestamps, cards by name, ids
  replaced by their place in the zones; hashed); meeting a key again with only such passes since, it performs
  `Input.MandatoryLoop`. The engine waits on the loop choice saying the game is in a loop that makes it a draw
  (`LoopStop.Mandatory(key)`). A loop through an optional action (a pass with something else to do, a decision
  answered) isn't one.

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
  show each player. A pending decision over cards hidden from others (a search's matches) is shown only to its player.
- **A host keeping one state per player** forwards each player's inputs to the other states:
  - Another player's `PlayLand`, `Cast` or `Activate` of a card the viewer doesn't know asks `HostFill` (public) for
    exactly that card, which becomes public as it is played (CR 305.1, 601.2a, 602.2a), then goes on
    (`Continuation.Played`; cancelling the cast forgets it again). `options` doesn't list such plays in the viewer's
    state (it can't check them); the host takes them from the player's own state.
  - A resolution that reaches cards only another player may look at (their search, scry, surveil, look, a choice
    among cards hidden from the viewer) is done in that player's view (D31): it waits on `Decision.OtherView(player,
    objects)`. The host has the player decide in their own state and answers `Answer.OtherView(filled, answers)`: the
    identities of `objects` and the answers given there (anyone's decisions and random results, in order; never host
    fills), all at once or as they come. Each step is worked out with them and committed without them
    (`Knowledge.forgetHidden`: the identities are in neither the state nor the changes; a card the viewer then sees,
    put into a public zone or revealed, is filled in as it would have been asked; triggers are found as with the
    identities). The state keeps only the answers and which cards are hidden, so the identities come with every
    answer; while more are needed the engine asks `OtherView` again. Once nothing is hidden the resolution goes on as
    usual. `Engine.otherViewDecision(state, filled)` gives a host that doesn't keep the player's state the decision
    they face.
  - Another player's companion check at the start (CR 103.2b) is theirs, made in their own state: the viewer's state
    doesn't look at their deck and offers every card of theirs outside the game that may be a companion; the one they
    reveal (the host forwards their choice) is filled in publicly.
  - Foretelling (CR 702.143) is a special action the rules give a card in a hand (`AbilityOrigin.Rule("foretell")`): another
    player's state takes it with the card unknown, as the host forwards it, so the card isn't revealed; the foretold card is
    face down in exile, `ObjectMark.Foretold`, known to its owner only (`Knowledge.mayKnow`), and filled publicly as it is cast.
  - Not yet: a face-down cast of a card unknown to the view is filled publicly (CR 708.2 would keep it unknown; the
    face-down cost comes from the card's own ability, which the view can't read).

## Controlling another player (CR 723)

`Effect.ControlNextTurn` records `PlayerControl(player, controller, combatOnly)` in `TurnState.controlNext`; as that player's
next turn actually begins it becomes `TurnState.controlled` (723.1; the last one created wins, 723.1a; a skipped turn isn't
taken, so it waits, 723.1b), until the next turn begins; "during their next combat phase" (723.2) applies only in that turn's
combat phase, this turn's when it is their turn and its combat is still to come. The controlled player stays the active player
and every decision, option and input stays theirs (`Decision.player`, `Input.player`), so only their resources pay (723.3,
723.5a). The engine's part for a host:

- `Engine.decider(state, player)`: who makes `player`'s decisions now: the controller, else `player` (a controller goes on
  making their own, 723.8; a player controlling themselves decides as usual, 723.9; nobody controls a player who left).
- `Engine.visible(state, controller)` adds what the controlled player may see of objects in the game (their hand, face-down
  permanents they control, cards they may look at), not their cards outside the game (723.4). `Knowledge.privateTo` (the
  "skip when I have no choice" rule) still counts only a player's own sight.
- `Engine.shouldPassFor` follows the decider's "Skip when I have no choice" setting.

What the host does (the ATW side, a follow-up): route each decision of the controlled player and their priority, casts,
activations, attacks and blocks to the decider (`options(state, player)` are offered to the decider, inputs performed as
`player`); show the decider the controlled player's hidden cards (`visible`; a host keeping one state per player shows the
decider the controlled player's own state, or fills those cards into the decider's view); never let the controller concede
for them (723.6: `Input.Concede` only from the player themself) or make choices no rule or object calls for (723.5b); and show
both players who is controlling whom while `TurnState.controlled` is in force.

## Skip when I have no choice (I-D18)

Each player has a setting in the state, `PlayerState.skipWhenNoChoice` (on by default; a host changes it with
`Change.SkipWhenNoChoice`, `Changes.skipWhenNoChoice`). The engine never passes priority by itself: a host asks
`Engine.shouldPassFor(state, player)` after each outcome and performs `PassPriority` while it says so. It is true when the
player holds priority, nothing is pending and `options` offers nothing but passing (mana abilities aside); on, that is
enough; off, only when the player has no card only they may see (`Knowledge.privateTo`: hand, face-down permanents and
spells, cards only they may look at; not cards outside the game), so an instant they can't afford is theirs to pass
with, and an empty hand isn't. While such cards are unknown in the state (another player's view, D60) it is false.

Inside a flow, a question with nothing to choose ("you may discard a land card" with no land card in hand, "choose a
creature card in your hand" with none, a choice the rules force, CR 609.3) is skipped on; off, when the skip depends on
the player's hidden cards, they are asked it: the choice with only its forced answer (`ChooseObjects` offering exactly
the forced cards, none or all, as min and max), or a cost they can't pay as `Decision.May(payable = false)`, only "no"
accepted. A skip depends on hidden cards when a card of the choice's pool in a hidden zone (hand, outside the game, a
library card only they may look at; a face-down permanent is a public 2/2, CR 708.2a) could be offered or not depending
on what card it is (`Evaluator.identityDecides`: types, colours, names, abilities, P/T and mana value could go either
way; zone, owner, controller, status and counters are read). Such cards still unknown in the state are filled in for
the player first (in another player's view, as a step done in their view, D31), on or off, so the choice is worked out
on what they are. Skips that depend on nothing hidden ("discard a card" with one card in hand, "sacrifice a creature"
with one creature, a search of a library nobody sees) stay automatic either way (P5). Covered: choices of objects during
resolution (`Step.Choose`), discards with a filter, costs paid on resolution and their choices, the companion reveal at
the start; not casting and activating, which the player does themselves.

Every answer, a player's or the host's, is checked against its decision before anything happens (`AnswerCheck`: kind,
allowed values, counts, uniqueness, zones, exactly the asked fills, new ids): a wrong one is refused with
`IllegalArgumentException`.

## Tracing

`Engine.perform(state, input, trace)` reports each call to an `EngineTrace` sink for a host's logs and bug reports: one
`TraceEntry` after the call, `Performed` (the input, number of changes, rounds of state-based actions and triggers, the
pending decision's shape `PendingShape`, a loop stop, whether the game ended, microseconds) or `Refused` (the input, the
exception's message and class; the exception is still thrown). Entries are serializable, so a host can keep them with a
report and replay their inputs on a saved state. The default `EngineTrace.None` reads no clock and makes no entry.

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
  "A or B" costs, objects for costs, mana payment with a suggested tapping (§7.8; convoke and delve when mana alone doesn't pay), cancel; the
  events of paying (sacrifice, discard, exile, return, mill, counters put, delve's exile) go through
  the event pipeline, so replacements apply (unearth, madness, counter replacements), and a
  replacement decision while paying waits (`Choices.paying`, D29);
  alternative costs and permissions (evoke, flashback, escape, overload, retrace, morph, "rather
  than pay"), bestow (an Aura spell and permanent while bestowed, a creature once it stops being, CR 702.103b, e, f),
  splice (CR 702.47: cards in hand offered as the spell is cast, their text added after its own, their costs added, a
  card whose target names clash with the spell's not offered), collect evidence as a cost (CR 701.59a), convoke, delve, cost changes (CR 118.7; "the next … spell" effects apply to and are used up by the spells they wait for,
  CR 611.2f), Phyrexian, hybrid, snow, restricted mana;
  mana abilities without the Stack (their other effects at once, a part needing a decision
  by hand; "in any combination of colors" asks a colour per mana), triggered mana abilities at
  once (CR 605.4a); special actions (suspend, turning face up); combat:
  declarations with their restrictions and as many requirements as they allow (CR 508.1d, 509.1c; goaded creatures
  attack, a player other than the goader if able, until the goader's next turn, CR 701.15),
  the last blockers declaration making attackers blocked or unblocked in the same change (CR 509.1h),
  removal from combat checked after every event (CR 506.4; an attacked planeswalker or battle that leaves or stops being
  one leaves its attackers attacking nothing, CR 506.4c), first/double strike steps, trample,
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
  401.4), host-ordered shuffles (D25; the random decision names the whole library as it is shuffled, `RandomKind.Objects.library`) and host fills and forgetting (D60, see Hidden information); mana costs paid while resolving
  ("unless … pays", ward) with a payment decision, not asked when they can't be paid (CR
  118.3). Replacement effects
  (CR 614, 616): own replacements first, control, copy, transformed, then the affected player's
  order when it matters, prevention effects among them (CR 616.1: not asked when one prevents all
  and the others only change the amount, or when only prevention is left: shields last); each once per event; "instead", modifications, optional ones, linked
  "when applied" triggers, one-use shields and regeneration (D26). Resolution (CR 608): target
  re-check and fizzling (the card leaving through `Events.run`, so a replacement's order or a host fill waits), partial
  legality, every `Step` and every `Effect` (keyword actions
  without a table entry are done directly or by hand), effect objects with locked sets (CR
  611.2c), delayed and reflexive triggers (snapshots of objects that already left, CR 608.2h), "until"
  returns as a one-shot effect right after the event without the Stack (CR 610.3), copies of spells (a spell of its own:
  "this spell" is the copy, with its own facts — X, modes, costs paid copied; not cast, no mana spent; moved, it ceases
  to exist, CR 707.10, 707.10a; a copy of a permanent spell becomes a token as it resolves, not "created", with the
  copy's facts, CR 707.10f, 608.3f, 111.13), redirection only to a permanent still a creature, planeswalker or battle (CR
  614.9), casting from an
  effect (CR 608.2g) with the casting flow's own plan (face, modes, optional costs, X, targets, cost objects, mana;
  without paying its mana cost with X 0, for another cost such as madness, or for its own or an alternative cost the
  card has or another object gives it, within its limit each turn, the player choosing which (CR 601.2b, 118.9; none
  when the effect's terms are one, CR 118.9a, and none face down); not cast without legal targets), the card going to the graveyard/exile/battlefield (an
  Adventure to exile, castable from there as the creature, CR 715.4); mana paid on resolution as a
  `ManaPayment` decision, asked only when a payment is found (CR 118.3), by hand when its amount isn't known there ("its
  mana cost", an unannounced X). Permanents enter with their printed
  loyalty or defense (CR 306.5b, 310.4b); Sagas get lore counters as they enter and after the
  draw step (CR 714.3); levelers' level abilities count level counters (CR 711). A step that needs a decision is
  taken back and redone with the answers so far (D29); earlier steps stay done (D31).
  Triggers: detection as each event is recorded, looking back for CR 603.10a events (objects new with such an event
  see only its parts that don't look back, and "from anywhere" abilities the others' moves, CR 603.6c), batches,
  "nth this turn" (counting batches for "one or more"), per-turn limits (counting waiting triggers),
  intervening and trigger-only conditions (an object that left is in no zone, CR 400.7), state triggers,
  chapters, "triggers an additional time"; keyword events: cycling (the card discarded for a cycling cost, also a
  discard, triggering from where it went, CR 702.29c–d), becoming crewed (a crew ability resolving, CR 702.122e),
  becoming saddled (saddle marks it until the cleanup step, CR 702.171), expending (the mana spent on spells this turn
  reaching N, CR 700.14); waiting triggers in `GameState.triggered`, put on
  the Stack in APNAP order with their order, modes ("not chosen (this turn)") and targets.
  State-based actions (CR 704.5a–q,
  v, Sagas 714.4) as one event, the legend rule as a decision. `History`: "this turn" counts.
  Events outside resolution (land plays, the draw step's draw, combat damage) go through
  `Events.run` and wait with `Continuation.Event`.
- Done, the game around it (`setup/`): starting a game (CR 103) with companions, London mulligans and opening-hand
  actions ("you may begin the game with it on the battlefield", CR 103.6a), conceding,
  the end of the game (CR 104) and players leaving a multiplayer game (CR 800.4a, j, k).
- Done, Duskmourn (`room/`, scenarios in `commonTest/…/sets/dsk/`): Rooms (CR 709.5: casting a door unlocks it,
  locked halves lose their text, unlocking as a special action, unlock and full-unlock triggers), manifest dread and
  turning a manifested card face up, impending, survival, delirium, eerie, behold as a cost, "must be blocked if
  able", "can't attack or block alone", untapping in other players' untap steps, no loss at 0 life, a set maximum
  hand size, hexproof and ward set aside, targets with one controller, choices "with different" qualities; an
  alternative cost another object gives the spells its controller casts from a zone, once each turn (the spell is
  tested as cast from where its card was, CR 601.2a); "Do this only once each turn" (CR 603.2h); "It's an
  enchantment" applying as the permanent returns (CR 611.2e); prevention effects with an additional effect, also for
  damage that can't be prevented and in the affected player's order (CR 615.5, 615.12, 616.1); "repeat this
  process"; copies of cards created in their zone and cast, ceasing to exist elsewhere than the Stack and the
  battlefield (CR 707.12, 704.5e).
- Done, Bloomburrow (scenarios in `commonTest/…/sets/blb/`): offspring, gift (the promised player, gift targets only
  when promised, "whenever you give a gift"), forage as an effect and as a cost, Class levels, pawprint modes, valiant
  and expend, counters put by your effects, leaving the battlefield without dying, a spell's static ability reading its
  own targets for its cost (CR 601.2f); losing all abilities and base power and toughness until your next turn, a land
  type for as long as a counter stays, the first instant or sorcery each turn costing less, a repeating delayed trigger
  that blinks a creature, entering as a copy within the mana spent, one card of each card type (an object standing for
  one of its types), a punisher repeated per counter, exiled cards played until your next end step; "this turn"
  conditions of static abilities read spells that have resolved as they last existed (CR 608.2h); finality counters (CR 122.1h), emblems' static abilities (CR 114.4), the nth
  spell of a kind cast this turn (the spell a permanent was judged as that permanent), spells with {X} in their mana
  costs, creatures tapped rather than paying an activated ability's mana.
- Done, Return to Ravnica (scenarios in `commonTest/…/sets/rtr/`): all 254 cards play. Detain, populate, overload (the
  "each" text, X announced for it too), scavenge, unleash; costs to attack ("can't attack you unless their controller pays
  {X} for each", CR 508.1h: the mana is paid as attackers are declared, a declaration that can't be paid isn't made or
  offered, CR 508.1d); costs of objects that share a quality (two cards with the same name); a spell exiled from the Stack
  as a cost; a discard caused by an opponent's spell or ability (its cause is part of the proposed event, so replacements
  see it); "a creature that dealt damage this turn" (the event role); two colours chosen and the spell's colours among
  them counted.
- Done, Gatecrash and Dragon's Maze (scenarios in `commonTest/…/sets/gtc/` and `sets/dgm/`): all cards play. Battalion,
  bloodrush, extort and evolve ("this creature evolves"), cipher (the card exiled encoded as the spell resolves, the
  creature's granted trigger reading the effect's names: a granted triggered ability sees the names of the effect that
  granted it), fuse (both halves of a split card cast from the hand as one spell, `CastWay.fused`: both halves'
  characteristics on the Stack, each half's targets kept apart as two modes, a mode's own slots bound under their names
  as its steps begin); additional combat phases (CR 500.8, `TurnState.extraPhases`); text-changing effects (layer 3,
  colour words and basic land types, CR 612); targets chosen at random (the host rolls among the legal ones); "can only
  attack alone"; "attacks a player and isn't blocked"; draws replaced except the draw step's first; "a counter is put"
  triggering once per counter; counters removed from among several creatures; "enters as a Mutant" for tokens too;
  "except it has this ability"; a triggered mana ability adding a type the land produced.
- Done, Reality Fracture (scenarios in `commonTest/…/sets/fra/`): all 285 cards play. Preparation cards (`prepare/`, CR
  722: becoming prepared makes the prepare spell's copy in exile, which its controller may cast while the permanent stays
  prepared, and casting it unprepares the permanent; the copy goes as the permanent stops being prepared), empower Jace,
  beholding as a cost paid on resolution, split second, loyalty abilities at instant speed, planeswalkers kept at 0 loyalty,
  "the legend rule doesn't apply", entering that doesn't trigger, negative power assigning damage, hexproof until combat damage,
  "last turn" questions, "the next spell can't be countered" applied to that spell, one target for each opponent in triggered
  abilities too, a token replacement, an effect lasting until a card is cast (through its casting), copies of cards cast as
  permanent spells becoming tokens (CR 111.13), the game ending when a player wins (CR 104.2b), "if it wasn't attacking" as
  it last existed, and counters put naming who put them.
- Done, The Hobbit (scenarios in `commonTest/…/sets/hob/`): all 193 cards play. Storied (the enduring story given with the
  state-based actions' checks, any time its condition holds, CR 702.195a, c), recruit, amass making the Army the named subtype
  (CR 701.47a) and done by another player, hone counters (CR 122.1j), a free alternative cost for the first equip ability each turn,
  the sources of the mana spent on a spell ("mana from a Treasure"), attack batches by total power, permissions that work only while
  a condition holds, two piles with the face-up one revealed, a permission's cost reading the spell it lets be cast ("life equal to
  its mana value"), names a spell's own replacement binds read by its later steps ("exile it instead … you may cast that card"),
  and "the nth draw each turn" counted per player.
- Done, Marvel Super Heroes (scenarios in `commonTest/…/sets/msh/`): all 281 cards play. Power-up's cost reduced by the
  permanent's mana cost the turn it entered (hybrid symbols too, CR 702.193b) and its extra uses, teamwork tapping creatures by total
  power (CR 702.194), connive and its replacement, plan counters, harness and ∞, sneak (castable in your declare blockers step, entering
  tapped and attacking what the returned creature attacked, CR 702.190), "choose up to X" modes, targets with a total mana value,
  "can't become untapped", damage that heals other damage, ward paid with poison counters, modal double-faced cards that transform
  (CR 701.27a), and alternative costs for "the next spell" effects (a card tested as the spell it would be, CR 601.2a). Controlling
  another player's next turn (CR 723): see Controlling another player.
- Done, Secrets of Strixhaven (scenarios in `commonTest/…/sets/sos/`): all 271 cards play. Paradigm (the names of spells that
  resolved this game kept per player in the player ledger, CR 702.192a), increment, converge and opus (mana spent and its colours),
  infusion, repartee, stun counters, casualty and miracle (also when another object gives them; a spell has the abilities other objects
  give it as its costs are determined, CR 601.2a–b), skipped turns (CR 614.10: `TurnState.skippedTurns`), "can't be copied", exiling
  until a total mana value, "until the end of their next turn" for another player, and 2ˣ.
- Done, Teenage Mutant Ninja Turtles (scenarios in `commonTest/…/sets/tmt/`): all 190 cards play. Mutagen tokens, sneak's "if
  his sneak cost was paid" (this turn too), sneak given to cards in a graveyard and casting them from there only with it (the alternative
  cost the card had where it was cast from is kept as it moves to the Stack, CR 601.2a, b; `MayPlay.viaAlternative` is enforced),
  disappear, the card types among spells cast this turn (`Measure.Distinct`), creatures that crewed a Vehicle this turn (tapping for a
  crew cost is recorded with `Meaning.Crewed`, CR 702.122b), an equip cost paid once each turn, searching outside the game.
- Done, Lorwyn Eclipsed (scenarios in `commonTest/…/sets/ecl/`): all 273 cards play. Blight as a cost (the source may blight itself;
  not payable without a creature, CR 701.68b), as an optional or alternative additional cost and as an effect for each player; behold
  of several objects; "behold … and exile it" with the exiled card kept by the permanent the spell becomes (cost-kept cards become its
  card memory, CR 607.2a); a creature type chosen with the costs and read by later cost parts and the spell; mana of each colour among
  permanents; X limited by a value as it is announced; losing all creature types; targets that share a quality; cards returned "under
  their owners' control" each under its own owner; counters on a card a replacement exiles ("with a dream counter on it"); and repeated
  processes inside each-player loops done one by one ("until they have exiled cards with total mana value 5").
- Done, Marvel's Spider-Man (scenarios in `commonTest/…/sets/spm/`): all 193 cards play. Web-slinging (also given to legendary spells by
  another permanent: a keyword with an alternative cost given to spells is on the cards as the spells they would be, CR 601.2a, b) and
  "the returned creature" read by the permanent; mayhem with and without a cost and given to graveyard cards at their mana costs;
  modified permanents; copies of spells with exceptions (layer 1, the token a permanent spell's copy becomes too); an entering copy's
  linked trigger naming the copied card; the excess damage of a fight; lands played from a zone; triggers on any of the first n events;
  turn counts (from the latest turn each player began); unspent mana of a colour kept; mana of any type for spells cast with a permission
  (`MayPlay.manaAsAnyColor`); targets with different names; suspected creatures (menace and can't block, CR 701.60c).
- Done, Avatar: The Last Airbender (scenarios in `commonTest/…/sets/tla/`): all 281 cards play (another player's next combat phase or
  turn controlled, CR 723, and foretell, CR 702.143, are the engine's since 2026-11-03). Airbend (the owner may cast the card from exile
  for {2}), earthbend (the land returns tapped when it dies or is exiled), waterbend
  costs paid by tapping artifacts and creatures (only the waterbend cost's generic mana), firebending's mana kept until end of combat,
  unspent mana kept or turned red, the bend triggers, triggered abilities of attackers copied, names set by effects, extra combats for
  some attackers, mana of any type spent through a permission, a spell's own "as though it had flash" from the hand, the spell being
  cast counted as the next one cast ("the first … spell you cast each turn costs less"), what a sacrificed Aura enchanted and the
  counters of a permanent that left read as they last were, and a {T} cost's source not suggested for its mana.
- Done, keywords that used to be by hand (2026-11-03, scenarios in `view/AutomatedKeywords*Test`, `casting/ForetellTest`,
  `casting/AlternativeWaysTest`, `turn/ControlPlayerTest`, `turn/DayNightAndSpeedTest`): the expansions of `KeywordExpansionsMore`
  (arcanum-api README) and engine code for foretell (`foretell/Foretell`: the special action and the foretold card's permission),
  plot (`foretell/Plot`: plotted cards cast free on a later turn as a sorcery, CR 702.170d), disguise and cloak (face-down with ward
  {2}, `Copiable.faceDown`), manifest, backup (the abilities printed below it, `KeywordMeanings.faceDependent`), entwine (every
  mode may be chosen), prototype (copiable values, CR 718.3), aftermath (each half from its zone), a permission that casts
  transformed (disturb, CR 712.11a), day and night (`turn/DayNight`: game ledger, untap check CR 731.2, daybound and nightbound
  transforming and entering transformed), speed (`turn/Speed`: player ledger, start your engines! as a state-based action, the
  inherent once-each-turn trigger as a sourceless repeating delayed trigger, max speed), compleated (Phyrexian symbols paid with
  life, `ObjectFacts.phyrexianPaidWithLife`), read ahead (the lore counters chosen as it enters; only that chapter triggers that
  turn), doubling mana, time travel, and controlling another player (above).
- Done, by-hand mechanics, second round (2026-11-03, scenarios in `ring/TheRingTest`, `combat/EnlistTest`, `soulbond/SoulbondTest`,
  `resolution/HauntTest`, `casting/ReadWordingsScenariosTest`): the Ring tempting you (`ring/TheRing`, CR 701.54: the emblem The Ring
  with the abilities its temptation count gives, `Value.RingTemptations`; the Ring-bearer, `ObjectMark.RingBearer`, ended by another
  choice or another player gaining control; `EventPattern.RingTempts`, the chosen creature as its object); enlist (CR 702.154, an
  optional cost to attack, CR 508.1g: `Attack.enlisted` names the creatures tapped, one per enlist ability, checked with the
  declaration; the linked trigger reads `EventPattern.Enlists`; `options` doesn't list enlisting, a host adds it to a declaration;
  two enlist abilities on one creature both trigger for each creature enlisted, a simplification of 702.154d);
  soulbond (`soulbond/Soulbond`, CR 702.95: `Effect.Pair`, `ObjectFilter.Paired`, `Objects.PairedWith`, the pair kept in both
  creatures' memory and ended with the state-based actions, 702.95e); haunt (CR 702.55: `Effect.Haunt` exiles the card haunting the target, kept as
  `Objects.LinkedTo(haunt)`; a spell's own trigger on leaving the Stack is found on its card). The parser reads their wordings
  and spree and tiered modes, "partner with", "if this spell was bargained", "if tribute wasn't paid" and "If [this] would be put
  into a graveyard from anywhere, exile it instead".
- By hand still (D61; `KeywordMeanings.moments`, effects `act.manual` asks for), each for a reason:
  - Keywords: epic (copying, each upkeep, a spell that has left the Stack: copies are made from Stack items only), assist (another
    player paying part of the mana: the payment flow has one payer), mutate (merging permanents, CR 730: an object represented by
    several cards, through zone changes, layers and hidden information), banding (bands and the defending player assigning damage,
    CR 702.22) and hidden agenda (conspiracies, outside normal play).
  - Keyword actions: venture into the dungeon and the initiative (dungeon cards, outside the game and the deck, and their rooms;
    the parser doesn't read them either).
  - Steps: mana paid on resolution whose amount isn't known there ("its mana cost", an unannounced X), the rest of a mana ability
    whose other effect needs a decision, the rest of a special action the engine doesn't do (none of the keywords' any more), and
    the parser's `Step.Manual` / `CostPart.Manual` for wordings it marks so.
  - Parser side (`Ability.Unparsed`, not the engine's): a haunt card's modal "… or the creature it haunts dies, choose one —", the
    Ring's rarer wordings ("whenever you choose a creature as your Ring-bearer", "your Ring-bearer" as an object), and the keyword
    actions above other than earthbend, airbend, behold, blight, connive, forage, recruit, the Ring and the cube's (discover,
    manifest, cloak, support, learn, endure and time travel are done by the engine once read).
- Not yet: venture (above), `nthThisTurn`'s
  `during`; in the flow: costs to attack other than mana and enlist, and
  "as it attacks" costs (CR 508.1g–h), battles as attack targets (protectors, CR 310.8, 506.2),
  other keyword actions as costs (exert); no cube card has any of these. Also missing: copy effects from static
  abilities (effect objects only). Around the game:
  actions "any time you could mulligan" (103.5b), effects that change the starting hand size, team and other variant
  starts; a decision of a player who left (the decision stays open; CR 800.4f–h, a cost not paid or another player
  choosing, are not applied); restarting a game (CR 104.1).

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
both players' views with every host answer through JSON; `hidden/SkipWhenNoChoiceTest` the "Skip when I have no choice"
setting (I-D18) both ways on every view; `decision/AnswerCheckTest` the answer checks. `review/Review*Test.kt` are the adversarial
reviews' scenarios; failing ones are `@Ignore`d with their finding number. Review 3 adds `review/Review3Host.kt`, a host
written against the public API only that keeps the full-information state and each player's view in step (D60), four
themed 40-card cube decks (`Review3Decks`), their long games (`-Parcanum.review3.games=N --tests '*Review3CubeDecksJvmTest*'`)
and printed timings on large boards (`-Parcanum.review3.perf=true --tests '*Review3PerformanceJvmTest*' -i`).
`view/ShortcutsTest` plays games with `Layers.checkShortcuts`, which checks every view kept, every resolution kept and
every dependency not tried against working it out in full.

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
