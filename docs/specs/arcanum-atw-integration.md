# Arcanum Automatum in ATW: integration spec

How ATW hosts the Arcanum libraries (rules engine `arcanum-engine`, parser, card model). Library-side decisions
are in `arcanum-automatum.md` (§2, engine API §7.0). This spec is approved in direction (owner, 2026-11-02); each
phase below becomes a rules-version change (SYS-031) whose catalog entries are written when it is built and reviewed
by the owner before release.

## 1. Decisions (owner, 2026-11-02)

| # | Decision |
|---|---|
| I-D1 | **Phases in order** I0 plumbing → I1 view → I2 casting and resolving → I3 abilities, lands, combat, steps → I4 Automatum-only controls (§3). I1 is the first thing players get. |
| I-D2 | **Card definitions are parsed on the device from the game's own card data** (the `CardDefinition` each game holds for its cards, which the server shares with both players), cached by that data's digest and the parser version. Every card in a game is covered and both players parse the same text; the catalog isn't involved. Everything meaningful about a card travels with the game, so fields the engine needs (oracle text, colour indicator, …) are part of that data (owner, 2026-11-02; replaces "from the client catalog"). Server-delivered definitions and hand-written overrides come later. |
| I-D3 | **Automatic passing** (now part of the single I-D18 setting; the host passes for a player when `Engine.shouldPassFor(state, player)` says so): when `Engine.options` shows a player can't respond to anything, the engine passes priority for them at once. |
| I-D4 | **The Automatum switch** sits in the table's top bar (§5); playtesting decides whether it moves into Table actions. |
| I-D5 | **Players are chosen as targets from their life squares** (TRN-030); avatars aren't on the table. |
| I-D6 | **One cast panel:** the panel that chooses modes, X and optional costs also shows the suggested mana tapping for the current choices, updated as they change; tapping a mana source in it changes the tapping. Targets are chosen on the table in between (CR 601.2b–h order: choices, targets, payment). |
| I-D7 | **ATW keeps its own game state; the engine is a pure function over it.** ATW builds an engine state for each call, sends the engine's changes as ordinary actions, and stores the few engine fields of §4.3. The engine state is always rebuildable from ATW state (P3). |
| I-D8 | **A flow** is everything from one player action (cast, activate, pass priority, change step, answer) until the engine returns with nothing left to decide; it is open exactly while there is a continuation, and may take several engine calls (one per decision). |
| I-D9 | **Switching Automatum off during a flow undoes the flow** (mechanism replaced by I-D19): the game goes back to the event before the flow began (its own batches only, which ATW's undo rule always allows since nothing else could change meanwhile), then Automatum is off; the player redoes it by hand. Undone random results are asked again on redo; seen information stays seen. |
| I-D10 | **The game can be inspected during a flow:** an eye icon hides and shows the flow's overlays, and hold-to-inspect works on everything underneath. Only changes are blocked (D32), never viewing. |
| I-D11 | **Private and shared flows:** casting a spell and activating an ability are private — the acting client applies the steps locally and keeps the continuation locally, sending nothing until the cast completes; then everything (card to the Stack, choices, mana, targets) goes as one batch, and cancel/undo before that only drops local steps. Everything else (resolution, triggers, combat, steps) is shared — each step is sent as its own batch as it happens, so both players follow it and either may answer (D11). Which kinds are private is a table playtesting adjusts; the engine is the same either way. Costs accepted: nobody answers a private flow for the caster; local progress is lost if the app closes mid-cast (could be saved on the device later). |
| I-D12 | **Extra turns:** ATW's turn-change event names whose turn it is, so the engine's turn order (incl. extra turns, CR 500.7) maps onto it (owner, 2026-11-02). |
| I-D13 | **Stack entries without a card:** an ability whose source is gone (e.g. a delayed trigger of a resolved spell) is an ability-only Stack entry showing the ability's text (owner, 2026-11-02). |
| I-D14 | **Automated batches may exceed the 100-action cap** (e.g. up to 1000), so one engine call stays one all-or-nothing batch (SYS-010) even for board wipes and mass untaps (owner, 2026-11-02). |
| I-D15 | **Turning Automatum on is refused** while the Stack holds manual entries or Temp piles are open, with a message saying why (owner, 2026-11-02). |
| I-D16 | **ATW's sideboard is "outside the game"** for the engine (companion, CR 702.139; CR 400.11a) (owner, 2026-11-02). |
| I-D17 | **The engine sees both hands** (owner, 2026-11-02): ATW doesn't hide information from clients, so the state a client builds has every hand card known; only library contents (ATW's unknown library order, filled by the server) and face-down cards stay unknown (D60). This lets the acting client pass or answer for an offline player where the rules leave them nothing to decide, with fewer hand-overs to the other player's view. |
| I-D18 | **One per-player setting, "Skip when I have no choice", on by default** (owner, 2026-11-02; replaces the separate automatic-passing setting of I-D3), stored per player in the game so the acting client follows the other player's choice. **On:** the engine passes priority and skips questions for that player whenever they have nothing to do — fastest, but a skip may reveal something about their hidden cards (e.g. "discard a land card or …" skipped because no land is in hand; a spell resolving at once because nothing in hand could respond). **Off:** when a pass or a skipped question depends on cards only that player can see (hand, face-down cards), the engine asks them instead (a priority window, or the question with nothing to choose, to dismiss). Things that never depend on hidden cards stay automatic either way (P5). The engine knows which cards each player can see, so it can tell when a skip depends on hidden ones. |
| I-D19 | **Switching off during a flow reverts it on the server** (owner, 2026-11-02; replaces I-D9's use of undo): the switch sends a "revert flow" request; the server undoes the events back to the flow's start in order, whoever made them, as one dedicated server process (not a player's Undo, which only takes back the player's own events). |
| I-D20 | **The undo window keeps counting events** (owner, 2026-11-02); it may be raised if playtesting shows automated batches push a player's own actions (e.g. a life edit) out of reach. |
| I-D21 | **Deck card data is refreshed for Automatum** (owner, 2026-11-02): stored deck card data made before the new fields (`parts`, `colorIndicator`) is rebuilt from the catalog (a forced catalog/deck refresh if needed; decks may be wiped if a refresh can't be done). A card that still can't be read plays with its printed values and manual abilities instead of blocking the game. |
| I-D22 | **History events are natural branching points** (owner, 2026-11-02): the minimum is one event each time a player could take an action. Engine bookkeeping (restamps, ledgers, records) is folded into its batch's visible event, and a priority pass is shown only when a player passed themselves (automatic passes stay inside the event); timeline stepping skips what is folded. Today's many events come from the manual board not knowing what can be responded to; further event cleanup may follow from Automatum. |
| I-D23 | **No branching while a flow is open** (owner, 2026-11-02): starting a branch is refused, like other changes (D32). Since the pause review (decision A, to be confirmed): only from a point inside the flow; a choice open only on the live line is left behind with it. |
| I-D24 | **Switching off clears the open flow** (reverted per I-D19) and keeps effect records, so their durations still apply when Automatum is switched on again; a manual "Clear Automatum data" action sits in the switch's hold panel (owner, 2026-11-02). |
| I-D25 | **Emblems** show as a chip by the player's life square with the emblem's text; playtesting decides whether other UI is needed (owner, 2026-11-02). |
| I-D26 | **Redo assigns fresh timestamps** when it re-applies an event, so stamp order follows real event order (owner, 2026-11-02; a starting point — undo/redo's final shape is decided by playtesting once Automatum is complete). |
| I-D27 | **Hidden information is about what is shown, not what the client receives** (owner, 2026-11-02): ATW clients may hold hidden identities (opponent's hand, face-down cards) in their data; what matters is that the UI never shows them to a player who shouldn't see them. Wherever the engine or the integration could either keep data from the client or just not show it, choose the path that is easiest to implement and maintain. |
| I-D28 | **Automatum on allows only what the engine considers legal; off allows any board manipulation** (owner, 2026-11-02). With Automatum on, manual board edits are not offered (e.g. the P/T corner shows the computed value and isn't editable; keywords in notes are just notes); each phase blocks the manual ways as the engine takes them over, until I4 where only engine-legal actions remain. With it off, the board can be changed in every current (and possible new) manual way. Switching back on does a best-effort recovery from the board and history (`EngineState.from`); what can't be recovered is lost and the game may then play differently — accepted. Example: "if you sacrificed a creature this turn" can't be known from a manual move to the graveyard, so it recovers as no sacrifice; "if a card left your graveyard this turn" can be read from manual moves (D33 tags), so it recovers. Refusing to switch on stays where the state would be unmanageable (I-D15). |
| I-D29 | **Combat damage flow (I3)** (owner, 2026-11-02): in the declare-blockers step the table shows the damage that would happen with the suggested divisions, and players may respond; passing priority confirms. In the damage step the engine asks for the divisions (CR 510.1: attacker first, then defender, public): each is pre-filled with a suggestion and changeable when other legal divisions exist; automatic when the player has no choice and "Skip when I have no choice" is on; either player may accept for the other to keep async games moving (D11). UX speed-up: the defender may make and confirm their choice at the same time as the attacker, and sees whether the attacker is still deciding; the choices are still recorded and shown in rules order (attacker's first). Damage is then dealt at once (CR 510.2). |
| I-D30 | **ATW's model and UI may change freely for Automatum** (owner, 2026-11-02): we don't retrofit the engine onto ATW's current design; ATW and its UI are modified and extended as Automatum needs. |
| I-D31 | **A first-strike combat damage step is its own step** in ATW (CR 510.4), added when it occurs. |
| I-D32 | **Combat area redesign** (owner, 2026-11-02): attackers and blockers get "drag here" slots with arrows showing who blocks whom (so one creature can block several attackers); with Automatum off a drag just moves a projection. Needs its own design session before it's built; until then Automatum combat uses today's lanes for one-to-one blocks. |
| I-D33 | **A "pre-event" system:** undoable early answers a player can give before their decision opens (e.g. the defender's damage division, I-D29), applied when the decision opens (owner, 2026-11-02). |
| I-D34 | **The move follows whoever must act** with Automatum on: the priority holder or the deciding player (owner, 2026-11-02). |
| I-D35 | **Automatum events are acting points** (owner, 2026-11-02; extends I-D22): ideally each point where a player can act is one event, everything automatic before it (turn-based actions, triggers placed, state-based actions) folded into it. Automatum events may be entirely separate from manual events or combined events — whichever is simplest. |
| I-D36 | **Drags are Automatum interactions while on** (owner, 2026-11-02; I-D28): dragging a land to the battlefield plays it, dragging a spell to the battlefield (or the Stack) casts it, and other drags map to the engine action they mean or are refused. |
| I-D37 | **Manual edits during a "do this by hand" step:** while the engine waits on a `Manual` decision, manual edits (P/T, counters, moves) are allowed until the player answers "done" — low priority; the goal is Automatum good enough that it never needs switching off (owner, 2026-11-02). |
| I-D38 | **Combat area redesign** (owner, 2026-11-02; I-D32): owner-approved design in the combat redesign brief — one active target lane for staging attackers ("Rest → target"), tap-select/tap-assign blockers with auto-deselect after 1 (or N for "can block N"), arrows for every block with a gap between rows, selection highlights what a creature fights, preview stays during normal priority (no "let damage happen" prompt), damage division one dialog per pairing with a meaningful choice, modes "In order" (default, drag) and "Free" (steppers), skipped when no choice matters. |
| I-D39 | **No damage automation with Automatum off** (owner, 2026-11-02): with Automatum off players do everything by hand (the combat layout still communicates attacks and blocks); today's "Deal combat damage"/auto-resolve damage is removed. Automatum is the improved version of it. |
| I-D40 | **Acting for the other player asks first** (owner, 2026-11-02; review item A): the step button or any priority pass made for the other player shows an explicit confirmation. Later maybe a game setting (before or during a game) to allow or forbid acting for the opponent. |
| I-D41 | **Pause points and response windows** (owner, 2026-11-02; review item B): when the opponent does something you could react to, you're shown it with a short pause (1–3 s, progress bar) in which you can react easily. "Full control" (per player) adds pause points (e.g. your own spells). Both online: the reacting player may hold and think as long as they like; the acting player has a setting to proceed at once or to wait, but waits at most the response time. Opponent offline (async): the acting player never waits. Timeline playback with Automatum on stops at the same pause points, with no time limit. A reaction after the acting player moved on uses branching (a longer reaction time on one side than the other). Testing decides if it feels good. |
| I-D42 | **Switching Automatum on drops turn-scoped engine data** (owner, 2026-11-02; review item C): leftover combat, passes, waiting triggers and early answers are dropped — best effort. Automatum off is mainly a "bug → report → fix by hand → continue" escape and for un-cards; the goal is that people play with it on. |
| I-D43 | **Logging for reproduction** (owner, 2026-11-02): engine and ATW Automatum logic log plenty, captured in error/problem reports, so a weird game situation can be reported, reproduced as an automated test and fixed. |

Earlier library decisions that shape this: one switch for the whole game (D20); the engine may refuse moves while on
(D32); anyone may decide for anyone (D11, P7); results are ordinary actions in one all-or-nothing batch (P1, SYS-010);
randomness and hidden cards are answered by the host (D25, D60).

## 2. How a call works

1. A player acts (taps, double-taps, answers a choice). The client builds `EngineState.from(playState, viewer = me)`.
2. `Engine.perform(state, input)` returns `Outcome(changes, pending)`.
3. If the engine stops for **randomness** (shuffle, coin, random pick) or a **host fill** (identity of a hidden card),
   the client answers at once inside the same call chain: randomness with ATW's own seeded randomness, fills from
   ATW's server-filled card data (the existing hidden-card fill of libraries and hands). A private look by the other
   player (`Decision.OtherView`) is handed to that player's client.
4. The client maps the changes to `PlayAction`s and sends them, with the continuation update, as **one batch**
   (SYS-010): in a shared flow one engine call is one batch and one Undo step (D59); a private flow (I-D11) keeps its
   calls on the device and sends them all as one batch when it completes.
5. If `pending` names a player decision, it is stored (the continuation, §4.3) and shown to the deciding player; the
   other player sees "Bob is choosing…" with "Choose for him" (D11).

**Who sends:** the client whose player acted or answered (D31). While a continuation is open, other changes are
refused (D32), except undo of the open flow's latest step. Switching Automatum off is always allowed and is the way
out of anything stuck.

## 3. Phases

### I0 — Plumbing (invisible)

- **Definitions on the device (I-D2):** parse each game card's faces (the game's own card data) with
  `OracleParser.parseCard` when a game needs it; cache the result by the card data's digest and the parser version.
  Unparsed abilities stay manual.
- **State mapping** `EngineState.from(playState, viewer)`: players, zones and order, cards (owner, controller,
  face, face down, tapped, counters (typed, §4.2), damage, attachments), turn and step, combat lanes, plus the new
  fields of §4. Hidden cards are unknown objects (D60).
- **Change mapping:** every engine `Change` to `PlayAction`s with the Magic tag (D33) and `CausedBy` for history
  text ("Storm Spark dealt 3 damage to Grizzly Cub").
- **The Automatum switch** as a shared game event. No engine version per game (D62): one app/server version at a
  time with forced updates; new event kinds bump the gameplay rules version (SYS-031).
- **Tests:** replay engine scenarios (the cube scenarios and review scenarios) through ATW's mapping and actions;
  the state rebuilt from ATW must equal the engine's own `apply` after every call, and undo of each batch must give
  back the previous state.

### I1 — The board knows the rules (first release)

- With Automatum on, cards show the engine's view: computed P/T in the corner (TBL-021), granted keywords, changed
  types and colours.
- **Inspect** explains each value: "2/2 printed, +1/+1 Glorious Anthem, +3/+3 Giant Growth until end of turn".
  Effect objects show on the cards they affect, "(until end of turn)" as today (TBL-110).
- With Automatum on, the P/T corner shows the computed value and isn't editable; keywords in notes are just notes
  (I-D28). Other manual play is unchanged in I1.
- **Built (app only, no rules change):** `ComputedBoard` (module `automatum/`) builds the engine state with full
  information (I-D27) on a worker whenever the board changes and maps `Engine.view` onto Battlefield cards: P/T of
  creatures, keywords gained and lost, changed types and colours (against `ObjectView.base`, the values after layer 1),
  typed counters; `Engine.explain` (engine) gives Inspect's breakdown. The switch and its hold panel (§5) are in the top
  bar. Catalog: TBL-110, TBL-112–TBL-114, TRN-054, TRN-055, SYS-055, SYS-063, HIS-091.

### I2 — Casting and resolving

- **Starting a cast:** double-tapping a hand card (ZON-004) or "Cast" in the hand menu casts it; a land is played.
  The card really moves to the Stack (D35).
- **The cast panel (I-D6):** a floating panel by the Stack, one section per choice in rules order:
  - face or mode(s), X, optional and alternative costs ("Kicker {4}" as a toggle, "Pay 4 life instead"), each with
    its card text;
  - **mana**: the sources the engine suggests tapping, shown tapped, and the other untapped sources; tapping one
    toggles it; "Cast" stays disabled until the tapping pays the cost. The suggestion follows the choices above it.
  - **targets** are chosen on the table between the choices and the payment: the panel shrinks to a slim bar
    ("Storm Spark — choose any target"), legal targets glow, players are picked from their life squares (I-D5); the
    panel then returns with the mana section.
  - "Cancel" (or Back) rewinds the whole cast (D58) as one Undo. "Accept automatically" (D3) skips sections whose
    suggestion leaves nothing to decide.
- **Priority:** after a cast the caster passes automatically ("cast and hold priority" by long-press, later). The
  opponent sees "Alice cast Storm Spark — Respond / Let it resolve". With automatic passing on (I-D3) and nothing
  they could do, they pass at once and the spell resolves.
- **Resolution choices** use the same panel for whoever decides ("You may draw a card", "Choose a card to
  discard"), with the card's own sentence as the prompt; the other player sees "… is choosing" and may choose for
  them (D11). State-based actions and triggers happen by themselves; trigger order and targets are asked only when
  there is a choice (P5).
- **Today's manual actions stay** in this phase; those that would clash with an open flow are refused (D32).
- **Built (I2a, casting):** `CastFlow` (module `automatum/`) runs a cast or land play as a private flow on the device,
  replayed from its start for every answer (so an earlier choice can be changed and the later ones kept while they fit)
  and planned into one batch when complete; `HandPlays` lists the engine's casts and land plays per hand card. Until the
  engine runs the steps (I3), `HostPriority` settles priority before a call (empty Stack: the active player; else the
  holder keeps it) and a turn record counts lands for its turn only. App: double-tap and the hand menu's Cast/Play,
  the cast panel (sections, target bar over the own hand row with glowing targets, mana section, eye, Accept
  automatically on the device), other actions refused while it is open. The card stays where it is with its Stack entry
  (D35 deferred to the owner's review); the spell doesn't resolve by itself (I2b). Catalog: ZON-004, ZON-005, ZON-007,
  STK-004, STK-023–STK-025, SYS-067.
- **Built (I2b, priority and resolution):** `SharedFlow` (module `automatum/`) runs priority passes and answers as
  shared flows on full information (I-D17, I-D27), each call one batch, the open decision in the continuation record;
  `SharedFlow.autoPass` passes while `Engine.shouldPassFor` says so but never on an empty Stack (steps stay manual until
  I3). A cast's batch carries the caster's automatic pass and what follows (`CastFlow.sent`); a decision after the cast
  itself (trigger order, targets) ends the private flow and goes out as the shared one. App: the respond prompt, the
  decision panel (cast panel sections shared), "… is choosing" / "Choose for them" and "Pass for them" with a
  confirmation, hidden cards named only when the viewer may see them; game actions and casting refused while a decision
  is open; step and turn changes refused while the Stack holds anything. Catalog: STK-023, STK-026–STK-032, SYS-067.

### I3 — Abilities, lands, combat, steps

- The card menu lists the card's own activated abilities (with their text) first, then the manual actions.
- Mana abilities in the cast panel and the card menu; land plays through the engine.
- Combat lanes (CMB-*) get the engine's attack and block legality, damage steps and damage division (a suggestion
  to accept).
- The step button (TRN-007) moves through steps with the engine: untap, draw, cleanup and "at the beginning of …"
  triggers happen by themselves.
- **Built (I3 groundwork, module `automatum/`, no UI yet):** `StepFlow.pass(call, player, until)` is the step button: the
  priority holder passes ("End turn": the host goes on passing for them this turn and declares no attackers, or only
  what must attack), then the host's automatic passes (`StepFlow.automatic`, I-D18) run to the next point where a player
  could act; steps passed on the way are `ChooseStep(through = true)`, the last one moves the move (I-D22, TRN-014).
  `StepFlow.answer` answers a shared flow's decision (cleanup discard, triggers). `CombatFlow`: `choices` (who may
  attack what and block whom, with the least declaration requirements make), `attackRefusal`/`blockRefusal` (the
  engine's reason, cards by name), `declareAttackers`/`declareBlockers` from lane data (`LaneAttack`, `LaneBlock`;
  `lanes(area)` reads them from a combat area), `division`/`divide` (I-D29, the suggestion when no shares are given),
  `preview` (the damage, life changes and deaths if everyone passes with the suggested divisions, also for blocks still
  being arranged) and `firstStrikeStep`. `HostPriority` keeps priority as the engine left it (a holder other than the
  active player on an empty Stack without passes is taken as left from a turn changed by hand). `SharedFlow.autoPass`
  (I2b) still stops on an empty Stack; the app moves to `StepFlow` when it runs the steps through the engine.
- **Built (I3a, steps and turns):** the step arrow and the phase dialog with Automatum on call `SharedFlow.step`
  (`StepFlow.pass` with `Until.Pass`, `EndOfTurn`, or `Step(position)` for a later step chosen in the dialog; the holder
  passes whoever pressed it, D11); with a declaration awaited Next step declares what the lanes hold
  (`SharedFlow.declare`), End turn and a step after combat attack with nothing. `SharedFlow.autoPass` is gone: every
  shared flow and a cast's batch end with `StepFlow.automatic`, so the steps run on to the next acting point. Manual
  step and turn changes are refused while on (I-D28, TRN-057; STK-029 while the Stack holds anything). First-strike
  damage is ATW's `TurnPosition.FIRST_STRIKE_DAMAGE` (I-D31): the planner names it from the engine's combat record
  right after the step begins; manual play never passes it (`TurnPosition.MANUAL`, `nextManual`); the phase dialog
  shows it while that combat had one (`CombatFlow.hadFirstStrikeStep`). I-D34: the planner ends each batch with a
  `PassAttention` when the move isn't with who must act. I-D35: every member of an automated batch after its first is
  folded (`MtgFacts.folds` honours any tagged member but the first; the tag now survives the reducer's own rewrites of
  a move), and priority the host gives before the input waits for the input's first member (#11). #4: a turn changed
  while off ends earlier turns' "until end of turn/combat" effects in `EngineState.from`. Engine work for shared flows
  and uncached hand plays runs on the Automatum worker. I-D37: while the open decision is `Manual`, the table's manual
  edits aren't refused (casting and step changes are). The acting-point event reads as its first member ("Priority →
  bob" for a pass) until the engine's history lines exist. Catalog: TRN-001, TRN-008, TRN-014, TRN-056–TRN-058,
  STK-026, STK-029, STK-032, SYS-067, HIS-092.
- **Built (I3b, drags, interim combat, pre-events):** drags are engine actions (I-D36, `AutomatumDrags`): a hand card onto
  the Battlefield or Stack plays or casts it, a creature into the combat area joins the declaration's draft, moving within
  its own Battlefield or hand stays, other drags and life drags are refused (menus keep their manual moves until I4).
  Combat on today's lanes (I-D32 deferred): while a declaration is awaited the lanes are its draft (`EngineState.from`
  reads lanes without the engine's combat record in the declare attackers step as undeclared); Attack/Block declare them
  through `CombatFlow`, "Deal damage" is the priority holder's pass; `CombatFlow.shown` gives the damage preview (also of
  blocks being arranged), "+N" for a blocker of several attackers and the divisions still to come. Divisions are the shared
  flow's choice, pre-filled with the suggestion, answered through `CombatFlow.divide`. Pre-events (I-D33, `PreAnswers`):
  an early answer is an engine record of its own kind (`EngineRecordKind.PreAnswer`, not part of the planned board's
  digest) set by an ordinary event; `EngineCall` applies it when its decision opens and every automated batch removes it.
  A combat declared by hand is dealt with the engine's P/T and keywords (i12review #5). A Stack entry below the top is
  picked as a target in Manage Stack. Catalog: ZON-077, TRN-031, CMB-017/018/020/022/025, CMB-028–CMB-030, STK-015,
  STK-023, STK-033, STK-034, TBL-051.
- **Built (i3review fixes):** pre-events are keyed to their decision (`PreAnswer.Division` with its turn and damage step,
  CR 510.4; only the open division's step is offered early) and kept by automated batches while that decision can still
  open (`PreAnswers.kept`); the batch in which it opened (applied or refused, `EngineCall.preAnswered`) or the first after
  the creature left combat or its step ended removes it. One that reaches the server just before the batch opening its
  decision stays beside the open decision: `SharedFlow.decision` shows it as the suggestion and the client of whoever
  made the latest event answers with it at once (`StepFlow.answerEarly`). Declared attackers get a pair each (the planner
  splits drafted pairs). The combat record carries its turn and counts only while it is the board's combat
  (`EngineCodec.currentCombat`: same turn, a combat step, the area open); passes in a row carry where they were counted
  (`TurnExtras.passesAt`) and restart after a step changed by hand. "By hand" is read from the continuation record
  (`EngineState.byHand`). Panels name objects in public zones only when visible to the viewer (a face-down spell, blocker
  or source); combat drafts are checked against the last shown combat and engine reasons worked out off the main thread.
- **Built (combat area redesign, I-D32, I-D38, I-D39):** a blocker's further blocks are `CombatOccurrence.alsoBlocks`
  (pair ids), set by `CombatAction.SetBlocks` (the full list a card blocks, its copy in the first pair); lanes, the engine
  state and the planner read and write them (`CombatStackRules.blocks`). The staging and block drafts stay shared lane
  events; selection, the active target lane and the eye are the viewer's own (`CombatController`). `CombatShown` carries
  the engine's refusal of the draft and of declaring nothing; `CombatChoices.capacity` how many attackers a blocker can
  block. The division dialog's rules are `Divisions` (in order, free refusal, order-free); an order-free division is
  answered with the suggestion by the dividing player's device or that of whoever made the latest event. The manual
  combat damage tools are gone (I-D39). Catalog: CMB-003, CMB-007–CMB-009, CMB-013, CMB-014, CMB-020, CMB-022,
  CMB-027–CMB-029, CMB-031–CMB-035, TRN-020, TRN-024, TRN-059, TBL-082, STK-033, STK-034; CMB-023–CMB-026, CMB-030
  removed.
- **Built (combat review fixes, 2026-11-02; decisions made for the owner overnight, to be confirmed):** A — the combat
  area is an overlay: a panel sized to its content over the middle of the table (`TableScene.combatPanel`); opening it
  moves no battlefield, it covers the battlefield cards under it, only it takes combat drops, and "▾" folds it away
  (`TableLayoutState.combatHidden`, the viewer's own; "Combat ▴" brings it back). Its cards are at least
  `COMBAT_MIN_CARD` (44dp) wide (`TableScene.combatCardWidth`), the preview's badges sit over a card's middle, chips
  stack one per line no wider than the card. B — order-free divisions are still made without asking, except when the
  dividing player has "Skip when I have no choice" off: the dialog opens, in Free (`askedInDialog`). #8 — an order-free
  division is answered by the client of whoever made the latest event; the dividing player's only after
  `ORDER_FREE_FALLBACK_MILLIS` without an answer. #10 — "In order" and the engine's suggestion leave the excess on the
  last creature. #11 — `SetAutomatum(true)` keeps priority with its passes in a row (`TurnExtras.passesAt` restarts them
  after a step changed by hand). #3 (CR 506.4c) — with Automatum on a pair keeps an attack target that left the
  battlefield (`CombatStackRules.closeInvariants`; by hand only a target leaving in that very change falls back to the
  player's lane); its lane reads "→ name (gone)", and `EngineState` reads it as an attacker with its target removed.
  #4 (CR 510.4) — a combat read from the lanes in `FIRST_STRIKE_DAMAGE` is the first of two damage steps with the
  current first and double strikers as having dealt damage. #5 — headers show loyalty/defense counters with Automatum
  on. #6 — combat messages name face-down creatures "A face-down creature" (`combatName`). #7 — the selection belongs
  to the combat step it was made in. #9 — the early division panel has Inspect. #12 — tapping an attacker's real card
  with a blocker selected blocks it; the combat record's reads are kept per board (`AutomatumBoards.combatRecord`).
  Catalog: CMB-007, CMB-013, CMB-029, CMB-032–CMB-035, STK-034, SYS-070.
- **Built (loops):** `StepFlow.automatic` keeps `Engine.loopKey` of each state it passes in with something on the Stack;
  the same key again with only forced passes since (CR 104.4b, 732.4) stops the batch with `Input.MandatoryLoop`, whose
  `Decision.Manual` the shared-flow panel shows ("The game is a draw: …"); answering it makes the engine's game a draw.
  A call that goes round too often inside the engine stops with a "Possible loop" `Decision.Manual` (engine safety cap).

### I4 — Automatum-only controls

- With Automatum on, menus show the engine's actions; the manual actions move behind a "Manual…" entry. Moves that
  break the rules are refused (D32). Hold-to-inspect and the other gestures stay as they are.
- **Built (I4):** `CardPlays` (module `automatum/`) lists, per card the viewer can see, the engine's casts, land plays,
  activated abilities (mana abilities, special actions) with their printed text, legal ones with the engine's input and
  the others with a reason (`CardPlay.Refusal`, tried with `Engine.perform`); `CastFlow` takes `Input.Activate` (the
  cast panel's flow, "Activate"; an ability that went on the Stack is passed on like a cast, a mana ability or special
  action keeps priority). App: card menus with Automatum on have the engine's rows above the cells that have no game
  effect and "Manual…" (`CardRingPages.automatumPages`), zone menus "Manual…" (`AutomatumZoneMenu`); "Manual…" opens
  today's menu only during a `Manual` decision (I-D37). Every manual `PlayAction` is refused by the toolkit
  (`AutomatumDrags.manual`) except while a `Manual` decision is open and inside Automatum's own routes (moving within
  one's own Battlefield or hand, the combat draft); refused controls stay greyed and say why on tap
  (`MenuControl.refused`, `LocalRefusal`): Life panel, Table actions, quick actions, Stack bubbles and Manage Stack.
  Catalog: TBL-043, TBL-046, TBL-071, TBL-114–TBL-116, ZON-007, ZON-013, ZON-077, ZON-078, TRN-010, TRN-031, TRN-059,
  CMB-025, STK-035, SYS-069.
- **Built (I-D40, I-D42):** `SharedFlowController.step` (every step route: step button, arrow, phase dialog, quick
  actions, combat Commit attack/Commit blocks/Pass) works out who would act; for the other player nothing is sent and
  `ActingFor` waits for the confirmation dialog (`ActingForDialog`: pass, end turn, a later step, declare attackers or
  blockers), dropped when the table changed. Answers and "Pass for them" already asked; casts and early divisions are
  the viewer's own; combat drafts are reversible lane edits and don't ask. `SetAutomatum(true)` drops the `combat`,
  `triggered` and pre-answer records and sets passes in a row to 0 (`Automatum.turnScoped`), recorded in the sealed
  delta so Undo of the switch restores them. Catalog: TRN-004, TRN-057, TRN-061, SYS-055, SYS-070.

- **Built (I-D41, pause points):** `PausePoints` (module `automatum/`) is the rule: a stop is the holder's own main phase on
  an empty Stack; a pause is another player's spell or ability on top of the Stack, the defending player's priority in
  the declare attackers step and both players' in the declare blockers step (once creatures attack); with
  `PlayPlayer.fullControl` (`MtgAction.SetFullControl`, the player's own) every other step with priority and their own
  spells too, and a cast keeps its caster's priority. `StepFlow.automatic` passes for a holder with neither (after
  `Engine.shouldPassFor`), and stops yielding at a state it yielded in before. `PausePoints.at(board)` reads the same rule
  from ATW's board alone. App: `AutomatumPauseDriver` on the live table — the viewer's pause (`PauseRole.Yours`) shows an
  emptying bar for the device's response time and holds the live table on it (`HistoryPlayback.holdPause`, also when the
  next step arrived with it: `passedFor`); the device whose step made it passes when it runs out (`PauseRole.Theirs`: at
  once when the other player isn't online or "Proceed at once", `SharedFlowController.autoPass`). Hold / Respond arm a
  continuation at the pause (`TableHistoryController.holdAt`), so a late reaction is a branch; Let it resolve there
  returns to live when the game has gone on. A pass made for another player starts its own history group, so the pause
  ends one; timeline Auto stops at the viewer's pauses (`HistoryPlayback.stopAtPause`). Settings → Automatum
  (`PauseSettings`, device per account; Full control copied into each game). Choices: response time and wait are the
  acting device's own (the reacting device shows its own time); "online" is the green presence dot; the opponent's
  beginning of combat is a pause only with Full control (the end step: see the review fixes below). Catalog: STK-026,
  STK-036–STK-039, TRN-057, SYS-071, HIS-093, LOB-079, LOB-113.
- **Built (pause points review fixes, 2026-11-03; decisions made for the owner overnight, to be confirmed):** A — a
  late reaction may branch while the live game has an open choice when that choice is only on the line being left
  (I-D23 is about forks inside one flow on the same line): `GameplayTree.play` refuses a fork only when the branch point's
  board has a flow open (SYS-062). B — the other player's end step is a pause by default (`PausePoints.of`), subject to
  Skip when I have no choice like other pauses, so end-of-turn instants work without Full control. C — the hold signal:
  no change (playtesting decides). Fixes: a fork reseeds the libraries' unknown order (HIS-042), so the app plans
  Automatum's batches from a held or past point on the branch's start (`TableToolkitController.acting`,
  `GameplayTree.forksAt`/`branchStart`), and `GameplayRules.change` also takes a batch's `MtgTag.board` digest of the
  branch point before its reseed (`plannedBefore`, a batch sent before the device saw the game go on); beginning a cast,
  ability or Respond at the viewer's pause holds it (`PauseController.holdForReaction`, bar running or not); no
  automatic pass for the viewer under their own open cast; a device passes by itself at most once per pause
  (`PauseController.passedAt`, so Undo of it stays); the viewer's own device passes at their pause when the other player
  isn't online; Full control goes into a game only after a change on the device the game hasn't taken
  (`PauseSettings.fullControlChanged`, `FullControlSync` per game in the device's preferences), else the game's value
  wins; one playback stop per pause (`HistoryPlayback.pauseKey`, also after an auto-pause or a ping); a pass made for the
  holder reads "alice passed for bob". Test boards shuffle their libraries (#13). #9 (a mana ability at an empty-Stack
  pause) is fixed by the final cast review's #3 (`HostPriority.kept`). Catalog: STK-036–STK-039, SYS-062,
  SYS-071, HIS-091, HIS-093.
- **Built (final cast review fixes, 2026-11-03; owner questions decided overnight, vision-aligned, to be confirmed):**
  - **Card names and types (#1, decision A):** never built from the game's card data (it holds the other player's hidden
    deck and hand, I-D27). "Choose a card name" is a text field (any name; the engine takes any non-blank name) with
    suggestions from the device's card catalog (`CardNameSection`, `CatalogCardNames`); "choose a creature type" with no
    given list offers every creature type of the pinned rules (`OracleParser.subtypes` through
    `CardDefinitions.ruleSubtypes`, filtered by `Subtypes.kindOf`) with a field narrowing it (`CreatureTypeSection`). The
    flows' listed answers keep only names and types of objects the deciding player may see (`ShownAnswers`).
  - **Library top (#8, decision B):** the Library menu shows the engine's rows for the top card when the engine offers any
    (`libraryTopRows` above "Manual…", ZON-078).
  - **Mana pool undo (#4, decision C):** fixed without a rules-version change or game wipe: a sequence delta's gap is
    found at the first copy of its anchor where the removed run and the following neighbour fit
    (`GameplayDeltas.patch`), so a pool holding equal mana (no identity) undoes (D59).
  - #2: a fused cast is labelled "Reel // Rend (fused)" (`CastFlow.wayLabel`, `fusedName`). #3 (CR 117.3c): the turn
    record's `passesAt` also marks where the engine left priority with a player other than the active one, and
    `HostPriority.kept` keeps it there (only a step changed by hand settles it to the active player). #5: repeatable and
    pawprint modes (`ModesSection`: counts, "−", Clear, pawprint costs and total, OK only within the limit). #6:
    `CastFlow.sent` passes on a land play, mana ability or special action whose trigger went on the Stack, as after a
    cast. #7 root cause: the engine asks a shuffle's random order in the state before the step's staged changes, so the
    host shuffled the library with the searched cards still in it while ATW's `ShuffleLibrary` shuffled it without them;
    `RandomKind.Objects.library` (arcanum-engine) now names the library as it is shuffled and `EngineCall` orders by it.
    Also a shuffle of at most one card (no random decision) is said with a fixed seed. #9: `SharedFlow.prepared` works
    out the listed answers and the suggestion's payment on the worker, `listedAnswers` is cached per flow, `pays`
    remembers its latest answer, and `SharedFlowController.toggleMana` runs on `work`. Catalog: ZON-004, ZON-078,
    STK-023, STK-040, STK-041, TBL-115, SYS-067.

## 4. Shared state ATW adds

### 4.1 MTG facts ATW models and shows

- **Zone-change count per card** (D21): the engine's object id is the card id plus this count; it goes up on every
  move between zones and on a shuffle (CR 701.20d). Stored, not derived from history (SYS-026).
- **Timestamp per card** and a game-wide counter (D34): set on entering a zone, attaching, turning face up/down and
  transforming; assigned by ATW with or without Automatum; never goes back (undo leaves it).
- **Priority holder and passes in a row** (D57).
- **Structured Stack items** (D19): ability, source, controller, targets by slot, modes, X. Today's text marks
  (STK-016) stay for manual entries.
- **Mana pool per player** (empties between steps, CR 106.4).
- **Damage marked** on permanents.
- **MTG tags on history events** (D33) and `CausedBy` for history texts. Manual moves are tagged wherever the move
  alone decides the meaning (died, entered, left the battlefield).

### 4.2 Typed counters

+1/+1, −1/−1, loyalty and named counters (time, lore, charge, …). Today's six colour counters stay as manual markers
the engine ignores (TBL-078).

### 4.3 Engine data ATW only stores (opaque JSON)

- **Last known information** inside the Stack item or event it belongs to.
- **Ledgers**, one blob per object (D22): mana spent, X, kicked, activations this turn, card memory.
- **Effect objects and delayed triggers** (D19, D26), each its own record.
- **The open continuation**, one per game while a flow is open (D58).

Kept per object or flow, never in one game-wide blob, so ATW's undo rule doesn't block undo of earlier batches.
Visible in an Automatum debug view (P2).

### 4.4 Zones

Look and reveal panels are not zone changes; temp piles are a manual tool Automatum doesn't support (D38).

### 4.5 Where it lives (built in I0, rules v36)

All in `protocol/` (`Automatum.kt` and the model files); the server stores and checks them with the shared reducer.

- **Switch:** `BoardState.automatum`, changed by `PlayAction.Mtg(MtgAction.SetAutomatum(on))`; the event's author is who.
- **Per card** (`PlayCard`): `zoneChanges` (engine id `ObjectId.Card(card id, zoneChanges)`), `timestamp`,
  `typedCounters: List<TypedCounter(kind, count, timestamp)>`, `attachedTo: MtgRef?`, `ledger` (opaque). Damage is
  the existing `PlayCard.damage` (TBL-024); the six colour counters stay manual.
- **Per player** (`PlayPlayer`): `manaPool: List<PoolMana(color W/U/B/R/G/C, data)>`, `typedCounters`, `ledger`.
- **Game** (`BoardState`): `priority: MtgPriority(holder, passes)?`; `engineRecords: List<EngineRecord(id, kind,
  timestamp?, data)>`, kinds effect, delayed trigger, game ledger, turn and combat extras (lands played, first-strike
  step, …), waiting triggers, and the continuation (id `continuation`, shared flows only).
- **Stack:** `StackEffect.item: MtgStackItem(id, kind, controller, ability, source, targets by slot, modes, values
  ("X"), timestamp, data)`; null on manual entries. The entry itself is today's `StackAction.AddEffect`.
- **Events:** `MtgAction.SetPriority`, `SetManaPool`, `SetCounter` (card object or player), `Attach`, `Restamp`
  (zone-change count and timestamp as the engine made them), `SetLedger`, `SetStackItem`, `SetRecord`. Each carries
  `before` and `after`; it is refused ("inapplicable") when the board no longer holds `before`, so a batch built on
  another state fails whole (SYS-010). A card named as `MtgRef.Card(id, zoneChanges)` must still be that object.
- **Tags:** `GameAction.Play.tag` / `GameAction.PlayBatch.tags` (one per member) carry `MtgTag(meanings, causedBy:
  MtgCause(controller, source, name, item), data)`, recorded as `GameplayMutation.tag` and sealed with the event.
  A batch is automated when its members carry tags; the server checks only their form (P8). A manual single-card
  zone change is tagged with the meanings the move alone defines (`MtgFacts.implied`: entered, left-battlefield,
  died, put-into-graveyard, exiled).
- **Automatic in every v36 event** (`MtgFacts.after`), with or without Automatum: a zone change (between
  containers, except Battlefield to Battlefield) counts up, stamps and clears typed counters, attachment and ledger;
  a shuffle (Shuffle, Mulligan, a closing Search or shuffle-rest panel) counts up the library's cards; attaching,
  face up/down and another face on the Battlefield stamp; a step or turn change empties mana pools.
- **Timestamps and undo:** the clock is not stored. A new stamp is one more than the highest stamp on the board
  before and after the event (`MtgFacts.clock`: cards, counters, Stack items, records), in card order within an
  event. No event writes the clock, so undoing an earlier event never clashes with a later one's stamps; replay keeps
  recorded stamps. After an undo the clock may fall below a stamp once given, but only stamps of objects on the
  board are compared and later stamps are always higher, so stamp order is event order (D34's intent). A redo
  (I-D26, `MtgFacts.freshStamps`) shifts the stamps its event gave above the board's current clock when another
  stamp reached them meanwhile; stamps are capped at 2^53 − 1 (an event needing more is refused).
- **Undo rule:** each value is its own delta entity or path (`automatum`, `priority`, one `record` per id, card and
  player fields), so a later event blocks undo only through the records and fields it touched.
- **I0 review decisions (built):** `PlayAction.BeginTurn(player)` (I-D12); `StackAction.AddAbility(card data,
  controller)`, an entry whose shown card is already gone (I-D13); automated batches (tagged members) up to
  `PlayBatches.MAX_AUTOMATED` = 1000 members, game messages up to 8 MiB (I-D14); `SetAutomatum(true)` refused over
  manual Stack entries or filled Temp piles and stamping unstamped Battlefield/Exile cards, `SetAutomatum(false)`
  closing the continuation (I-D15, I-D24, #11); `PlayPlayer.skipWhenNoChoice` with `MtgAction.SetSkipWhenNoChoice`
  for the actor (I-D18); `GameAction.RevertFlow` (`FlowRevert`, I-D19); `MtgAction.ClearRecords` (I-D24); forks
  refused while a continuation is open (I-D23); `MtgTag.board`, the planned board's digest on a batch's first member
  (#4); `MtgTag.folded`, honoured on a batch's later bookkeeping members (`MtgFacts.folds`: SetPriority, Restamp,
  SetLedger, SetStackItem, SetRecord but emblems) as `HistorySemantic.folded`, which joins the group before it and
  which the timeline never shows or stops at (I-D22); `EngineRecordKind.Emblem` with `EngineRecord.player` and
  `text` (I-D25).

### 4.6 The mapping (built in I0, module `automatum/`)

- `EngineState.from(playState, definitions, viewer, log)` builds the engine state; `EngineCall` performs and answers
  the host's decisions; `EngineBatch.plan` turns the call's changes into one batch, run through ATW's reducer while
  planning (without recording, `GameplayRules.preview`; the plan's state is the acting client's prediction), and
  refuses a batch the server's checks or one game message wouldn't take. Engine data that can't be read (another app
  version, P8) is reported as unsupported, never a crash. Tests: every engine call round-trips (rebuilt state =
  engine's own result; undo restores the board).
- **Spells:** as STK-001/003 say, the card stays where it was cast from; its entry's item is the spell (a spell
  being cast has an item without data until the engine makes it, CR 601.2a). Abilities are entries of their source.
- **Tokens:** ATW makes its own token card, with the token's printed values and rules text (I-D2); its ledger keeps
  the engine's token id and definition (D35). A token (or copy of a card) that leaves the Battlefield or the Stack stops
  existing in ATW at once (TBL-103); the engine keeps it in its new zone until state-based actions are next checked (CR
  704.5d), so while a flow waits for a decision mid-resolution (a destroyed token's "its controller may search") the
  continuation record keeps those objects and their place (`FlowRecord.lingering`), never shown to players; without an
  open decision such an object is unmappable.
- **Copies of cards** (CR 707.12, made where the card is): an ATW token card made in its owner's Exile
  (`CreateToken(definition, containerId)`), showing the copied card's own card data marked as a token's (as ATW's
  manual copy, `CloneToken`); its ledger keeps the engine's id, definition and `copyOfCard`. Cast, it stays in Exile
  with its entry pointing at it (STK-001); like ATW's tokens it stops existing outside the Battlefield and the Stack
  (CR 704.5e, TBL-103). A copy made in another zone is unmappable.
- **Ledgers** hold only what ATW's fields don't say (controller, face of a split card, phasing, marks, memory, facts,
  control start where ATW's entered turn doesn't say it, …). Records: `effect:<id>`, `delayed:<id>`, `ledger`,
  `turn` (lands played, extra turns), `combat` (the engine's combat; the lanes show it), `triggered`, `continuation`
  (the pending decision and the view it was made in: a shared flow goes on in that view).
- **Event log** (D33): each automated event's tag carries its part of the engine's log; `EngineLog.from(tags, state,
  definitions)`. A card's definition and its printed abilities are written as references to the game's card data of
  that card (tokens' written out), so tags stay small and name no hidden card.
- **Order:** the Battlefield and Exile are rebuilt in timestamp order; the timestamp counter is ATW's clock.
- **I0 review decisions (built, lane 2b):** a card without an engine definition (the device couldn't read it) gets
  `PrintedCards.definition`, its printed values and its rules text as one `Ability.Unparsed` (I-D21); a face-down cast
  has a face-down entry (`AddEffect(faceDown)`, STK-005) and the spell's object reads `faceDown` from it, and the
  continuation names cards' definitions by reference like tags (#5); an emblem is the record `emblem:<id>` holding
  its object, shown by its text (the quoted ability of what made it when its abilities carry no text, I-D25); the
  planner tags bookkeeping and priority changes other than a player's own pass (`EngineCall.perform(input,
  automatic)`, `ownPasses`) folded (I-D22). Saved decks' and cubes' old card data is rebuilt from the catalog once per
  account (`CardDataRefresh`, `CardDataRefreshRun`, I-D21).
- **Logging for reproduction (built, I-D43):** `Engine.perform(state, input, trace)` reports each call to an
  `EngineTrace`; `AutomatumLog` keeps per game the latest entries (engine calls, batch plans, flows, early answers, the
  switch, refusals, server refusals of automated batches, replica rebuilds) and the latest engine calls (start state and
  inputs); problem and crash reports carry it as a `ReproBundle` (card definitions, latest board and its engine state,
  calls, entries; gzip+base64 in the reports' `automatum` field). The automatum tests' `ReproBundles` loads and replays
  one (`-Pautomatum.bundle=<file>` with `ReproFromFileTest`).

## 5. The Automatum switch

- A round sigil button in the table's top bar, beside Table actions (I-D4).
- **Off:** a dim outlined rune at about 40% opacity.
- **On:** the rune filled with a violet-to-cyan gradient and a soft outer glow that slowly breathes (about 3 s); one
  small spark orbits it.
- **Tap:** switches Automatum for the whole game, as one shared event both players see; history: "Alice turned
  Automatum on". Turning it on with manual Stack entries or other state the engine can't take over asks first (what
  happens to them is decided when I0 is built).
- **Hold:** a small status panel ("Automatum on · 2 abilities are manual") and the debug view of engine data.

## 6. Catalog entries this will change (by phase)

- I0: new entries for the switch, timestamps, zone-change counts, tags (history texts).
- I1: TBL-021–023 (P/T corner), TBL-086 (keywords), Inspect entries.
- I2: ZON-004/005/007 (hand double-tap and menu), STK-001–006 (spells move to the Stack, D35), SYS-010, SYS-036
  (refusals while on, D32).
- I3: card menu entries, CMB-*, TRN-007.
- I4: every menu entry that moves behind "Manual…".

## 7. Open (decide when the phase is built)

- What happens to stored effect objects, Stack entries and counters when the switch is turned on or off.
- Where the debug view lives; who may see it.
- Secret choices (answers one player can't read) stay manual until ATW can store them.
