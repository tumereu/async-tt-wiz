# Arcanum Automatum — design draft

**Status: phase 0 approved (2026-11-01).** Developed alongside the Android
improvements, before the web client (roadmap). Phase 0 builds the libraries in this
repository without integrating them into ATW. Each later phase, and every new shared
state or catalog change, needs the owner's go-ahead.

## 1. What it is

An optional rules-automation layer for Magic: The Gathering on top of the existing
manual table, built as three generic libraries and an ATW integration:

1. **arcanum-api**: the card definition models, a typed format describing what a
   card's abilities do (costs, targets, effects, conditions, durations). Shared by
   the parser and the engine, so definitions can be written to files and loaded
   without the parser, or written by hand.
2. **arcanum-parser**: our own parser from MTGJSON oracle text to card definitions.
3. **arcanum-engine**: computes derived board state (P/T, keywords, …), applies
   rules automatically where nothing needs deciding, asks for decisions where
   something does, and returns the results as MTG-level changes. It owns the API
   its callers use.
4. **ATW integration**: maps the ATW table to the engine's API and the engine's
   results back to ordinary ATW actions, and provides the UI.

The game stays a board-game simulator underneath. When automation is wrong, a card
is unsupported or a situation is unusual, players fix it with the manual actions
that exist today.

### Non-goals

- A rules referee. The engine never refuses or blocks a player action (SYS-036,
  SYS-037); it only computes, applies and asks.
- Any ATW knowledge in the arcanum libraries (§4.1).
- Using Forge, XMage or other engines as a runtime, or Forge card scripts as data
  (GPL-3; see §12). Forge may be used as a cross-check only.
- An LLM step in the parser.
- Un-cards (MTGJSON `isFunny`) and digital-only mechanics (Alchemy: seek,
  perpetually, …). They stay manual permanently.

## 2. Owner decisions so far (2026-11-01)

| # | Decision |
|---|---|
| D1 | The engine is an **assistant**, not a referee. |
| D2 | Derived shared state (e.g. computed P/T) is **per game and shared**: both players see the same board. If automation works well enough, the game-wide off switch may later be removed. |
| D3 | Casting opens a **cast dialog**: choose targets and the mana sources to tap, with a suggested tapping. The dialog can be set to **accept automatically**. |
| D4 | Async play uses **our normal Stack/attention rules**. Live play is open (§9). |
| D5 | **Our own parser** from oracle text; no Forge data, no LLM fallback. |
| D6 | Coverage is prioritised by **how often** a pattern comes up; rare and silly cards stay manual. |
| D7 | Assists **happen automatically** unless a player has something to decide at that moment. No "accept" steps for things without a decision. One **"Automatum off"** switch turns the assists off; no per-assist settings. |
| D8 | **Three generic libraries** (arcanum-api, arcanum-parser, arcanum-engine) with no ATW knowledge; the engine talks in MTG terms; ATW integrates them. The owner reviews their code and documentation at least at the interface level. |
| D9 | Phase 0 (§13) is as described; the order of later phases may change as work goes on. |
| D10 | The open questions (§15) are expected to be settled by playtesting. |
| D11 | Any decision (a trigger's targets, trigger order, an opponent's choice during resolution) can be made **by either player on the other's behalf**. Such a decision pauses the other player's automatic playback by default, and the UI makes clear you are acting for someone else. The phone test (SYS-036) still applies. |
| D12 | **Autoplay decisions** (§7.10) go on the roadmap: stored answers, applied deterministically by exactly one client even while their player is offline. |
| D13 | Arcanum is developed **alongside the Android improvements, before the web client**. The libraries live in this repository; phase 0 does not integrate them into ATW. |
| D14 | The parser is built **case by case, AI-assisted**: one effect or a group of similar wordings at a time, deciding together how to parse it, then implementing it. The owner reviews code periodically. (D5's "no LLM fallback" is about the parser itself, not how it is developed.) |
| D15 | **Test data is our own:** invented card names with real-MTG-like wording (a real card's text with a different name and mana cost, or merges of several cards). The frequency analysis runs on a local MTGJSON download that is never committed (D-243). |
| D16 | Library rules: KDoc and a short README per library; tests cite Comprehensive Rules numbers and are protected like catalog-linked tests; a build check keeps the libraries free of ATW modules. |
| D17 | Types in the libraries are **sealed hierarchies rather than enums with values**, unless that causes serialization or multiplatform problems. |
| D18 | **Card model:** a card is a layout and faces; a face is its characteristics plus abilities; abilities are the CR kinds (spell, activated, triggered, static) plus keywords. **Effects** (one-shot) and **mechanics** (continuous rules the engine applies) are separate; each mechanic defines how duplicates combine. **Keywords** stay keyword abilities with their identity (for "with flying", "loses flying"); one shared table in arcanum-api maps every keyword to abilities built from mechanics when definitions are loaded (deathtouch 1:1, flying → general "can't be blocked except by"). Ability words are labels on ordinary abilities. |
| D19 | **Resolved references (API review, scenario 1):** card definitions never name specific objects; what they select is worked out at the CR-defined moment (targets when put on the Stack, other sets on resolution). What the engine stores names objects and players by id: a **Stack item** (ability of which card, controller, targets, X, modes) and an **effect object** (mechanics, locked-in affected objects, duration, source, controller, timestamp). Effect objects are arcanum types; the host stores and shows them without knowing their contents. Which parts lock in follows the CR (characteristic and control changes lock their set, rule changes keep applying to new objects, numbers lock at resolution, CR 611.2c, 608.2h). "You" is the controller of the Stack item or effect object, captured when it is created; for static abilities, the source's controller (CR 109.5). |
| D20 | **One switch: Automatum on or off for the whole game** (replaces the separate game, player and card switches of D2, D7 and P4). No fine-grained control such as editing parts of an effect object. Turning it on may come with further restrictions (e.g. no manually created Stack entries) for anything that can't be brought in line with automation; these become clear when that part is built. |
| D21 | **Object identity (API review, scenario 3):** an object id is `ObjectId(card, zoneChanges)`, worked out by the engine: every zone change makes a new id (CR 400.7), so the engine knows the ids a resolution creates before the host applies it. A stale id (its object left the zone) reads as last known information and can't be acted on (CR 608.2h, 400.7). Effects that produce objects name them: one `bind` when the effect doesn't change ids (create token, copy, search), one bind per id stage when it does (`preBind`/`postBind` for a single move; more if an effect changes an id more than once). Zone-change triggers offer the "before" and "after" ids. Stored Stack items and effect objects carry snapshots of the stale ids they refer to. "Enters with counters" is part of the move, not a later step (CR 122.6). |
| D22 | **Game history as ledgers (API review, scenario 4):** the engine stays stateless; what it needs from earlier in the game ("a creature died this turn", "your second spell", activation limits, mana spent, "if it was kicked") is kept by the host as **ledgers** at three scopes: game, player, and object instance (keyed by object id, so a zone change starts fresh). The engine defines the ledger keys and how each event changes them; changes are increments, decrements and set additions/removals where possible, so undo works. "This turn" ledgers reset at the turn's start, and the previous turn's are kept for "last turn" texts. A resolving permanent spell keeps the cast part of its ledger (CR 400.7d). If undo removes something a later automated result depended on, players fix it by hand. |
| D23 | **Easiest first on definition updates (for now):** a definition update may break or wipe running games until the owner says otherwise. Stack items carry a copy of the ability they run (granted abilities have no place on the card anyway); other references to an ability use its position. Schema versioning of stored definitions is parked. |
| D24 | **Object model ("properties"):** everything an object has: its **characteristics** (CR 109.3: name, mana cost, colours, types, P/T, loyalty, defense, and its **abilities**: spell, activated, triggered, static, with keywords as labels on these), **applied mechanics** from effects of other sources that are not abilities ("can't be blocked" from an Aura, CR 113.12), **status** (tapped, flipped, face down, phased out), and counters, damage, controller, attachments and designations. Mechanics that are only the meaning of a keyword (deathtouch, lifelink, first strike, …) are **keyword-only**: they come only from the keyword table, so "has deathtouch" is always a granted ability that "loses all abilities" removes (CR 613.1f). |
| D25 | **Randomness is a decision answered by the host** (API review, scenario 7): coin flips, die rolls and random picks pause resolution like a player choice, but the host answers at once, inside the same batch, by the client sending it. Shuffles are effects the host performs with its own shuffle; the engine asks which cards are where only when an effect looks at them. Answers are addressed by position (D23). **Secret choices** ("each player secretly chooses") stay manual for now: they need a stored answer one player can't read, which the host doesn't have yet. |
| D26 | **Shields are replacement effect objects** (API review, scenario 8): "prevent the next N damage" (CR 615.7) and regeneration (CR 701.19a) replace the event as it happens, never as triggers. Each is an effect object holding a replacement and what is left of it (damage amount or uses), changed by decrements so undo works; it ends when used up or at its duration. Created by `PreventDamage` and by `Regenerate`, a keyword action in the shared table; static "regenerates if it would be destroyed" (CR 701.19b) is the same replacement in a static ability without a count. Needs effects `RemoveFromCombat` and `RemoveAllDamage` and a "can't be regenerated" mechanic (CR 701.19c); `uses` moves from mechanics into the effect object. |
| D27 | **Numbers from a group say how they combine** (API review, scenario 9): one value shape `Of(property, objects, how)` with `how` = single ("its power"), total, greatest or least, always written out; an empty group gives 0; `single` over several objects is a definition error. Replaces the separate power/toughness/mana value/counter values. |
| D28 | **Combining mechanics** (API review, scenario 10): only identical instances are redundant (CR 702.16m). Blocking restrictions are separate mechanics: `CantBeBlocked`, `CanBeBlockedOnlyBy` (flying, fear, intimidate), `CantBeBlockedBy`, `MinimumBlockers` (menace); "can block any number" is its own mechanic. A land's mana abilities come from its basic land types after type changes (CR 305.6, 305.7), never from reminder text. Copy exceptions ("except it's a 1/1") are their own type and become part of what the copy is (CR 707.9b), so copies of the copy carry them. |
| D29 | **Several replacements and questions mid-event** (API review, scenario 11): the engine applies a card's own replacements, then control, copy and back-face replacements in CR order (CR 614.15, 616.1a–d), then asks the affected player to order the rest, only when two or more apply (autoplay decisions can store an order). A question in the middle of an event ("enter as a copy of …", replacement order) is an addressed decision like any other; the engine redoes the action from its start with the answers so far. While it waits, **nothing from that action is applied** (for now). |
| D30 | **Structural gaps closed before the parser rounds** (API review, scenario 12): target counts are values (X targets, any number); a trigger can have several events and stays one ability ("enters or attacks"); cost parts name the objects they used ("the sacrificed creature", with its last known information); divided amounts are read per target; choices can be made from a bound group ("from among them"); inside a granted ability "this creature" is the object that has it, and its controller activates it; mana spent can be counted per colour; "until the end of your next turn"; "can't be activated". Other gaps (emblems, Class, Room, the Ring, votes, missing keywords, …) are added by the parser rounds in frequency order (D6, D14). |
| D31 | **Resolution sequences** (API review 2, scenario 13; replaces D29's "nothing applied while waiting"): resolving a Stack item, casting a spell (CR 601.2, with mana abilities allowed inside and cancel rewinding the whole cast) and other automated sequences advance one committed step at a time. The current sequence (step, answers, bound objects, snapshots) is game state, so undo works per step. The engine is called with the state and the sequence position and returns events to apply plus the new position, a decision for a player, or "done". A choice opens a blocking dialog; the opponent sees it greyed out with "Choose for them" (D11), hidden cards behind extra confirmations. The client of the player whose choice (or action) started the step computes and sends everything up to the next decision in one batch; a batch also ends at an event whose hidden cards the server fills in, and the same client continues once they are known. Results only one player can see lead to that player's choices (miracle, madness). The escape from a stuck sequence is switching Automatum off. |
| D32 | **Automatum on may block moves** (exception to the phone test SYS-036, only while it is on): an open sequence blocks other changes, and rule-breaking moves (an extra land, casting without enough mana) may be refused. It stays an assistant because anyone can switch it off at any time; "play anyway" actions are considered later. |
| D33 | **Turn history from tagged events** (API review 2, scenario 14; replaces the "this turn" part of D22's ledgers): the host's own recorded events are the log. Events the engine produces carry their Magic meaning (died, sacrificed, destroyed, discarded, cast, combat damage, …) and a snapshot of what everyone could see of the objects involved; that is also better history text for players. "This turn" and "last turn" questions read the tagged events of those turns on the current branch, so undo and branches need nothing extra. Manual moves are tagged only with what the move itself defines unambiguously (D42); play with Automatum off is untagged and doesn't count. The goal is Automatum good enough to stay on all game, off only for rare cards. Per-object facts (mana spent, X, kicked, activations this turn) stay stored on the object (D22). |
| D34 | **Object timestamps** (API review 2, scenario 15; CR 613.7): every object has a timestamp, an integer from one game-wide counter in game state that goes up each time an object enters a zone, becomes attached, turns face up or down, or transforms. The host assigns it itself, with or without Automatum, like the zone-change count. The counter **never goes back**: undo doesn't lower it (gaps are harmless, and a shared counter that undo reverts would block undoing earlier actions); it could simply be the sequence number of the event that set the timestamp. Objects entering at once are stamped in batch order without asking (CR 613.7m). |
| D35 | **Ids for everything** (API review 2, scenario 16; extends D21): a sealed id type with `Card(card, zoneChanges)` for cards and tokens alike, and `StackItem(id)` for abilities and spell copies on the Stack (CR 109.1, 707.10). The engine makes new token ids itself as name-based UUIDs (version 5) from the Stack item's id and the token's number, so both clients compute the same ids and later steps of the same batch can use them. Merged permanents (mutate, meld) stay manual for now. The CR 400.7 exceptions (effects that continue on the permanent a spell becomes, cast permissions that follow the card onto the Stack, …) are one fixed list in the engine. |
| D36 | **Paying and choosing inside effects** (API review 2, scenario 17): `Step.Pay(cost, payer)` ("you may pay … If you do") and `Step.Unless(payer, cost, steps)` around any effect (replaces `Counter.unlessPaid`), with mana abilities allowed while paying (CR 608.2g); cost amounts can be values ("{1} for each"). "When you do" is a reflexive trigger that becomes its own Stack item, targets chosen then (CR 603.12). "As ~ enters" steps (pay, choose) run inside the entering event, not on the Stack (CR 614.12); "When ~ enters" stays a normal trigger. Replacement effects are chosen one at a time, the rest checked again after each (CR 616.1f; corrects D29); stored autoplay answers are preference lists. Counters with rules meaning are typed: keyword counters, shield, stun, finality, rad, hone, time, and any +X/+Y (CR 122.1). |
| D37 | **Further model fixes** (API review 2, scenario 18): for-each over players can collect every choice first and then do one combined event (CR 608.2e); reveal-until with the found card and "the rest" bound; damage and life effects `bind` the amount actually dealt, gained or lost (read with `Value.Bound`; excess damage with `Value.Excess`, CR 120.4a); an explicit "X except Y" reference for "the rest"; effect objects and delayed triggers freeze the names they use (choices, colours, types, objects) when created (CR 603.7c, 608.2h); names live in the Stack item, cost names included, and "if you do" means done; protection and hexproof-from check an ability's source (CR 702.16b, 702.11d), one ability per quality; setting a land's basic type removes all its own abilities (CR 305.7) and replaces only land types (CR 205.1a); a special-action ability kind (morph face up, foretell, plot, suspend; CR 116.2), with a face-down spell's cost fixed at {3}; the prepare layout waits for the parser rounds; checks become typed reference checks, with typed event roles. |
| D38 | **Versions, zones, keywords at runtime, structural Forge concepts** (API review 2, scenario 19): a game stores the definition and engine version it uses; a client on another version can't make Automatum actions until it updates (manual play still works) and the server refuses automated batches from another version. Look/reveal panels are not zone changes; shuffling makes the reordered library cards new objects (CR 701.20d). Keywords expand while the engine derives the view (layer 6), so keyword counters, copies and "loses all abilities" work. Structural additions: effects on the next spell matching a filter (CR 611.2f), "triggers an additional time", replacements that redirect damage, and mana that carries a rider onto the spell it pays for. |
| D39 | **Minor rules and model fixes** (API review 2, minor items): one canonical form for each thing (tapping, phasing, turning face down and transforming have their own effects, `Mark` only sets marks like monstrous or suspected; gaining control is `ApplyMechanics(ControlledBy)`; an aftermath card is a split card with the aftermath keyword); `Cast` defaults to paying; summoning sickness is a filter worked out from control (CR 302.6), not a status; a loyalty cost implies sorcery timing and once per turn (CR 606.3); combat damage rules are typed; "kicked with its [A] kicker" names the cost; flashback only for instant and sorcery spells and "its mana cost" as a cost; colourless hybrid mana symbols and loyalty X; an effect object locks per mechanic, so "+1/+1 and can't be blocked" locks only the +1/+1 set; random results follow ATW's existing trust model for random actions. |
| D40 | **"If you do" checks the choice to pay** (API review 3, scenario 20; corrects D37's "if you do means done"): in "[do X]. If you do / don't / can't" and "you may [do X]. If you do", X is a cost paid on resolution (CR 118.12) and can't be paid in part (CR 601.2h). `Step.Pay` (optional, or `mandatory` for "[do X]. If you can't") binds one of paid, declined or couldn't pay; "if you do" reads paid, "if you can't" reads couldn't pay. The KDoc says such actions are written as `Pay`, never as `May` plus an effect. "… this way" checks what the effect's own binding holds. Which wordings map to `Pay` is decided in the parser rounds. From here on review items are marked API or parser. |
| D41 | **Play vs cast, as-enters, own replacements, trigger limits** (API review 3, scenario 21): one permission mechanic says play or cast (lands only with play, CR 305.1), with filter, "top card only", cost, timing and "spend mana as though any colour"; it belongs to whoever got it (an opponent's library too); `MayPlayTopOfLibrary` goes. "As ~ enters" steps may only pay, choose, check conditions and modify how it enters (tapped, with counters, with abilities, as a copy); entering tapped is not becoming tapped (CR 603.2e, 110.5b). A spell's or ability's instructions can carry its own replacements, applied only while it resolves and chosen first (CR 614.15, 616.1a). Triggered abilities can be limited per turn ("triggers only once each turn", "do this only once each turn", CR 603.2h); delayed triggers have a duration instead of `once` (CR 603.7b). Per-object turn facts (activations, triggers) are stored per turn number, so nothing resets at turn start. |
| D42 | **API review 3 fixes** (applied directly, owner preference): blocking events say once per combat or per creature and name the creature on the other side (CR 509.3); "through the next untap step of its controller" duration; prevention only through prevention mechanics and effects, plus "damage can't be prevented" (CR 615.12); dice, coins (answered by the host, D25), exchange control, moving counters, changing targets, two piles; discarding specific cards; "the affected object/player" inside static abilities and effect objects (Coat of Arms); event-scoped mechanics keep their own scope; one permission mechanic `MayPlay`; granted abilities' "you" is the controller of the object they're on (CR 109.5); "enters with abilities"; put onto the battlefield attacking (CR 508.4); counters of any kind; domain; chosen-colour mana; coloured cost reductions; maximum blockers, attack costs, attacking despite defender; events for copy, scry, surveil, crime, playing a land, attaching, rolling, flipping; Omen layout (CR 720); characteristic-defining P/T is layer 7a by being in a characteristic-defining ability; secret choices have no flag (manual, D25). Definition checks now work per ability, check nested triggers against their own events, and cover "as enters" steps, empty replacements, impossible counts, stray chapter abilities, characteristic-defining abilities and misplaced references. **Amendments:** D34: counters get timestamps per kind (CR 613.7c), and simultaneous stamps go in turn order, active player first (CR 613.7m). D31: the sequence state holds the queue of triggers waiting to be put on the Stack with their snapshots (CR 603.3), and state-based actions are never checked between its steps (CR 704.3); any client that can see everything the next step needs may continue it (the server keeps the first), and a step that checks triggers on a card only one player can see is computed by that player's client. D23/D35: abilities are identified by origin (printed: card, face, position; granted: effect object, position; keyword: its keyword's reference, position). Copies of spells copy the original's cost names (CR 707.10); token ids from mana abilities, special actions and replacements are seeded from the event that creates them. Mana in the pool keeps its source, restrictions and riders. Undo per step follows ATW's own-actions rule (SYS-038). **D33 amended (owner approved 2026-11-01):** manual moves are tagged with their meaning wherever the move alone defines it unambiguously (died, entered, left the battlefield, put into a graveyard from anywhere, CR 700.4); how it happened (sacrificed, destroyed, discarded) needs automation. |
| D43 | **Workflow** (owner, 2026-11-01): first define the API until it can express everything, clearly; then pick a set or cube and parse all of its cards, each with an automated test that the owner reviews as oracle text next to its parse; the engine API and ATW integration are decided after or in parallel. |
| D44 | **API review 4 fixes** (applied directly): cast facts name their object (`Paid`, `CastFrom`, `ManaSpent`, `TimesPaid` with `of`; filters for cast/kicked spells; cast events from a zone; CR 400.7d). Abilities on the Stack can be filtered by kind, source and label; targeting restrictions match the spell or ability itself (its controller) plus, separately, the quality of its source (hexproof CR 702.11b/d, protection 702.16a/b). Keywords that label an ability (boast, solved, exhaust, max speed, power-up) are an `AbilityLabel` on it, expanded by `withLabelRules`; "activate only once"; trigger-only conditions. `Step.Pay` takes a cost or any steps (CR 118.12); costs can be "A or B" and keyword actions (behold, collect evidence, forage, exert), choose by total ("total power 3 or more"), and keep exiled cards as card memory (CR 607.2q). "Enchanted player"; computed token P/T; effects that change the rules without an affected set (Fog, CR 611.2c); "this turn" counts say events, objects or amount; triggers can count the nth event this turn (within a step) and batch thresholds; `ForEach(together)` goes step by step (CR 608.2e); "put onto the battlefield" defaults to the putting player's control (CR 110.2a) and "enters under X's control" is its own modification; casting can play a land; the source's new object and the object under test can be referenced; keyword actions that target name a slot; "you may exert as it attacks"; modes with conditional maximum, "not chosen before", empty slots of unchosen modes (CR 700.2c); more effects and events (double, bolster, support, detain, endure, cloak, time travel, the Ring, flip, skip a turn, distributed counters, wider searches, tokens attached or attacking, kept mana, ability-use counts, crewed, saddled, expend, excess damage). One way each: "you may X. If you do" is always a `Pay`, "this way" is `NonEmpty`; any target is `Recipients(target)`; fixed mana with a count replaces colourless amounts; timing is any or sorcery, other limits are conditions; `MayPlay` takes its cards from `affects`; repeat never wraps an effect that has a count. **Amendments:** D33: one tagged event is one draw, one simultaneous zone change, one damage event. D34: stamping simultaneous objects in batch order within each player is a simplification of CR 613.7m (the active player may choose). D38: CR 701.20d makes only revealed cards new objects on a shuffle; treating every shuffled card as new is a deliberate over-approximation. D42: an ability's origin is a path (the ability that granted it, then its position), which covers static grants, token faces and copy exceptions. |
| D45 | **API review 5 fixes** (applied directly; sample 57 of 75 real cards expressible, 76%): what a creature attacks (`AttackTarget`: chosen, same as another attacker, or given; the defender role; the defending player of a given attacker, CR 506.2, 508.4); conditions over some or every player with `Players.Tested` (multiplayer, CR 102); subtypes and colours as references (written out, chosen, or of objects), used in filters, type and colour changes, copy exceptions and mana; batch triggers read every object, the total amount and every player of the batch (CR 603.2c); keyword variants share their base keyword's kind (hexproof from, multikicker, megamorph like typecycling; CR 702.11d, 702.33c, 702.37b); a shuffle can leave out cards just found (CR 701.24b); casting bans (`CantCast`), "can't be sacrificed", attack limits, "can't draw more than one card each turn"; effects on the permanent a spell becomes (`SpellGets`, CR 400.7d); reordering cards in place (CR 401.4); `MayPlay` with additional cost, without paying, transformed; extra combat with a main phase; exchanging life totals; starting life; keyword actions and their cost parts name what they used; alternative costs in `Paid`. Canonical forms, each enforced by a definition check: "unless" is `Unless`; "you may [action]. If you do" is `Pay`; "you may put one from among them" is a choice of zero or one; counts against a number are `Exists` / `HappenedThisTurn`; "any target" is read whole with `Recipients.target`, which is only for such slots; "without paying" is `withoutPaying`; keyword costs come only from keywords; counters divided among targets use the target slot (CR 601.2d); "each player [does]" loops are together (CR 101.4); boast, exhaust and power-up label activated abilities only. **Amendment to D38/D44:** a shuffle's cards become new objects except cards left out of it. **Review method (owner, 2026-11-01):** every card a reviewer has tried is kept in a growing list outside the repository (`~/projects/atw-cache/arcanum-review-cards.tsv`); each new review retests all of them and adds new ones. |
| D46 | **Scope of the API (owner, 2026-11-01; refines D30 and D43):** the API's *architecture* must allow every card; specific cases (new keywords, keyword actions, layouts, effects) can be added as they come up. **First target:** everything the current version of Caleb Gannon's Powered Synergy Cube needs (the cube the owner plays), list saved outside the repository at `~/projects/atw-cache/powered-synergy-cube-2026-11-01.txt` (from Cube Cobra `synergy`). Structural items parked under D30 (emblems, Rooms, votes, prepare) are designed when a review shows they need structure; leaves are added when needed. |
| D47 | **API review 6 fixes** (applied directly; retested cards 87%, new 72%, 82% of 192): `Step.Bind` fixes a reference without a choice, for delayed triggers ("destroy that creature at end of combat", CR 603.7c); "the next spell" effects use `Affected.NextSpells` and `uses` is only for replacement and prevention shields; batch triggers can batch per player; target slots can be one per player (`perPlayer`, `Players.SlotPlayer`); player conditions are one shape, `PlayerExists(PlayerFilter)` with `PlayerFilter.Where(condition)` and `Players.Tested` (replacing `AnyPlayer`/`AllPlayers`); a card's costs see its spell's target slots (CR 601.2c, 601.2f); "defending player" is only `DefendingPlayerOf` (`Players.Defending` removed); someone else choosing what is discarded is `Choose` + `Discard(objects)`; a single effect for each player takes the players directly, loops are for several steps; chosen subtypes and colours are `Chosen` (this resolution) or `Remembered` (card memory), and `ColorRef.Of` is for mana and colour-setting only; `SpellGets` mechanics see the spell as `Objects.Affected`; `Objects.LinkedTo(keyword)` for keyword-linked memory (hideaway, haunt, craft); costs can name objects (`CostObjects.These`); `CastAsThoughFlash` for players, `MayPlay.perTurn` (Lurrus); emblems (`CreateEmblem`, CR 114); companion as a keyword (CR 702.139); reveal-until can put cards into a zone; mana "of any type that land produced" and "when that mana is spent"; scry and surveil name their cards; `ModeChosen`; attack restrictions affect the attackers; citation fixes. **Cube coverage (D46):** of the parked structural items the cube needs only emblems (Kaito Shizuki), companion and a once-per-turn graveyard cast (Lurrus of the Dream-Den), player-wide flash (Aluren) and level counters (Joraga Treespeaker), all now expressible. Rooms, prepare, votes, Class levels, Gift, Craft, Offering and deck-construction rules beyond companion fit the architecture (layouts, marks, special actions, choices, costs) and are designed when a target needs them. |
| D48 | **Cube-wide review fixes** (review 7: 378 of 500 cube cards expressible, 75 more blocked only by a missing keyword or token entry; verdicts kept in `~/projects/atw-cache/arcanum-cube-verdicts.tsv`): X of a card's own spell is `Value.CastX` in its enters abilities, replacements and statics (CR 107.3a, 107.3m); keyword expansions for every keyword the cube uses (storm, persist, ninjutsu and commander ninjutsu, unearth, changeling, affinity, evoke, madness, modular, delve, convoke, dredge, suspend, exalted, retrace, crew, morph, megamorph, escalate, cleave, sunburst, rebound, decayed, level up, partner, reconfigure, fabricate, shadow, living weapon, cascade, battle cry, training, hideaway, buyback, escape, overload, bestow, splice, companion), with the leaves they need (spells cast before, modes chosen, other ways to pay mana, `Timing.AsThisCard`, unattach, outside the game); a keyword-action table (investigate, connive, bolster) and the predefined tokens of CR 111.10; overload carries its "each" instructions; who may play, cast or activate (`MayPlay.holder`, `Cast.caster`, `ActivationRules.activatedBy`, CR 602.2), and effects inside `May`/`ForEach` are done by that player; having another object's activated abilities, scoped any-colour mana; card memory adds up (CR 607.2a); discard events have before/after; delayed triggers fire once and may expire, or repeat; event causes; target legality and simultaneous steps; `Timing.Instant`; unlimited land plays; fixed new targets; random objects; "can't spend mana"; token replacements that also create other tokens; per-turn unchosen modes; boast twice; distinct numbers; any-number cost choices whose names cost reductions can read; graveyard-only cast replacements; "if you searched this way"; this ability's own cost; chosen vs remembered names; `CantAttack.fromDefender`. Checks: "unless" only for costs; attack and discard roles; per-opponent counts found structurally; "discard your hand" and −X canonical; expansion output checked without the definition-only rules. Mons's Goblin Waiters (half mana) and Ineffable Blessing (flavor text) stay manual; the cube's 3 Un-cards are an exception to §6's parsing scope. |
| D49 | **Cube recheck fixes** (review 8: 484 of 500 cube cards expressible; with these fixes 498, the other two manual by D48): a replacement can carry a linked trigger (`whenApplied`, madness's "exiled this way", CR 702.35a, 603.11); "as it resolves" moves (`ZoneChange.resolving`, rebound, buyback, CR 608.2n); `Objects.Where` narrows references wherever they are (connive, now per conniving object, CR 701.50); reconfigure stops being a creature while attached (702.151b); the unattach event names what it left (`EventRole.From`); "this ability costs less" is `ActivationRules.costChange`; permissions can refer to their holder (`Players.Holder`, Aluren); special actions have conditions (morph only face down, the revealed companion); megamorph's counter goes on as it turns face up; cascade's resulting-spell check; suspend's haste only for creatures and until control is lost; sunburst ignores type-changing effects; hideaway's look permission sits on the exiled card. Checks added: undefined chosen names, searches and target slots; X as `CastX` only outside the spell; single values over remembered groups; `Each` narrowed from a reference; "you may choose one" is a zero-or-one choice; every `Pay` is read by "if you do"; a third "discard your hand" form; cast without paying or for a cost, not both; the sacrificed source is `Self`; "instead of entering" isn't an as-enters modification; repeating delayed triggers need a duration; "for as long as it remains there" is `Indefinitely`; conditional "can't be prevented" is a static; "exactly one" comparisons are allowed. Per-keyword tests pin the CR sentences that were wrong. |
| D50 | **Who searches (owner question, 2026-11-01):** the rules let any player search any library (CR 701.23; Bribery searches an opponent's), so `Effect.Search` names the `searcher` (default you) apart from the `library`. "Its controller may search their library … put that card onto the battlefield" (Path to Exile, Boseiju) is searcher = library = that player, and the card enters under that player's control. |
| D51 | **Defaults (owner, 2026-11-01):** a field may default only to an empty or absent value (`null`, `false`, empty list), meaning the feature isn't used. Every other field is required, so definitions and tests state who, how many and which objects (no defaults like `Players.You`, `Value.Fixed(1)`, `ObjectFilter.Any`, `Objects.Self`). To apply after the running parser helpers are merged. |

## 3. Principles

- **P1 Results are ordinary actions.** In ATW, a resolution becomes a batch of normal
  recorded actions (moves, damage, life, counters, tokens, …), sent as one
  all-or-nothing batch (SYS-010) and taken back by one Undo. History, undo, branches
  and playback keep working as today; replaying a game never needs the engine.
- **P2 No hidden engine memory.** Everything the engine must remember between calls
  is returned to the caller as a visible MTG object that the caller stores and passes
  back: a continuous effect with its duration, a chosen colour, "exiled with", a
  delayed trigger, Stack targets. In ATW these are shared board objects players can
  inspect, edit and remove, so a manual fix is always possible.
- **P3 Derived state is a pure function** of the given game state and card
  definitions. It is recomputed, never stored, so it stays correct after any manual
  edit, undo, branch or timeline step, and never replays history (SYS-026).
- **P4 Everything can fall back to manual.** Each ability is automated or manual;
  manual abilities behave exactly as today. With Automatum off, everything is manual (D20).
- **P5 Automatic unless there is a decision (D7).** Things that need no choice
  (state-based actions, a trigger without targets, options or ordering, cleanup of
  ended effects) just happen. A choice appears only when a player has one to make.
- **P6 Shared code.** All three libraries are Kotlin Multiplatform common code, so the
  Android app predicts locally within SYS-025 and the future web client reuses them.
- **P7 Anyone may decide for anyone (D11).** A decision belongs to a player by the
  rules, but either player can make it, as with every manual action (SYS-036).
- **P8 The server stays authoritative over actions, not over automation.** Since any
  player may do anything manually, the server does not need to re-check that an
  automated batch followed the rules. It validates the actions as it does today, plus
  the new shared objects (§8).

## 4. Architecture

```
MTGJSON oracle text ──► arcanum-parser ──► card definitions (arcanum-api models)
                                               │ files / server data, hand-written overrides
                                               ▼
ATW table ──► ATW integration ──► engine game state (MTG terms) ──► arcanum-engine
    ▲               │                                                  │
    │               └── UI: cast dialog, choices, Inspect details      │
    └── ordinary ATW actions ◄── MTG-level changes + decision requests ◄┘
```

### 4.1 Library boundaries

- **arcanum-api** holds only the card definition models (§5) and their
  serialisation, with a schema version. No game state.
- **arcanum-parser** depends on arcanum-api. Input: oracle text and card fields
  (types, P/T, keywords). Output: definitions plus a per-ability parse status.
- **arcanum-engine** depends on arcanum-api and defines its own caller API:
  - **Game state in MTG terms**: players, zones, objects with their printed
    characteristics, counters, damage, tapped/face-down state, attachments, the
    Stack with its choices, turn and step, and the stored effect objects (P2).
    Objects the caller can't see are **unknown objects** (an opponent's hand card,
    an unknown library card), explicitly marked as such.
  - **Calls**: derive the view; list what a player can do; start an action (cast,
    activate, resolve, attack damage); react to events that just happened.
  - **Results**: either a **decision request** (choose targets for slot `t` among
    …, choose mode, choose mana sources, order triggers, choose a card to discard),
    or a list of **MTG-level changes** (move object, deal damage, gain life, add
    counter, create token, tap, add effect object, …).
  - Each decision request names the player who decides it by the rules. The engine
    accepts the answer whoever gives it (P7); who actually answered is the
    caller's business.
  - **Decisions are data, not callbacks.** The caller answers a request by calling
    again with the answers so far. The engine keeps no session between calls, so a
    decision can wait for days, cross devices or be answered by the other player.
    This is the main difference from Forge's and XMage's blocking decision model,
    and what makes the engine usable for async play and in other apps.
- **ATW integration** (in ATW's shared code) is the only part that knows ATW: it
  builds the engine game state from `PlayState`, maps MTG-level changes to
  `PlayAction`s, stores effect objects as shared ATW state, and turns decision
  requests into UI.
- **arcanum-tools** (JVM, never shipped) holds developer tools such as the pattern
  frequency analysis; it depends only on arcanum-parser.
- A build check (`verifyModuleBoundaries`) keeps the arcanum modules free of ATW
  dependencies. They start as modules in this repository and can move to their own
  repository later.

### 4.2 Snapshots first

The integration builds a fresh engine game state for each call. A board of tens of
permanents is small; if measurements show it is too slow for SYS-025, the
integration can keep and update the snapshot incrementally without changing the
engine API.

### 4.3 Rules version (Comprehensive Rules drift)

The Comprehensive Rules are revised several times a year, usually with each set
release; each version has an effective date. Most numbers stay put, but rules are
added, reworded, and now and then removed or renumbered (2024 example: damage
assignment order was removed from combat). Oracle wording changes too (errata,
"enters the battlefield" → "enters").

- The libraries name **one pinned CR version** (its effective date) in one place:
  `ComprehensiveRules.EFFECTIVE_DATE` in arcanum-api, currently **2026-09-25**.
  Code and tests cite rule numbers of that version (`// CR 613.4c`).
- **Updating the pin** is a deliberate task: download the old and new CR text
  locally (not committed), let a small developer script list the rule numbers that
  were added, changed, removed or renumbered and where the code and tests cite them,
  then review exactly those places. Test changes then follow the protection rule
  (D16) because the rule they encode changed.
- **Oracle wording drift** shows up when the frequency analysis runs on a newer
  MTGJSON: new or changed patterns appear and cards change parse status. Our test
  cards (D15) don't change on their own.
- In ATW, a rules update never changes past games: recorded events don't depend on
  the engine (P1).

### 4.4 Where definitions come from

Definitions are dynamic data like other card data (D-243): produced by the parser
(as a developer tool or a server job, open question), merged with hand-written
overrides, versioned, delivered by the server and cached by the app. Updating a
definition improves future automation without an app release; accepted events never
change.

## 5. Card definition model (arcanum-api)

The model is the Kotlin code in `arcanum-api` (overview in `Definitions.kt`, examples
in `ExampleCardsTest`). In short (D18):

```
CardDefinition: layout + faces
FaceDefinition: name, mana cost, colour indicator, super/card/subtypes, P/T, loyalty,
                defense, abilities
Ability: Spell | Activated | Triggered | Static | Keyword | Unparsed
  Spell/Activated/Triggered → Instructions: targets, modes, steps (Do effect, May, If,
                              ForEach, Repeat, Choose, Manual)
  Activated → Cost + activation rules; Triggered → Trigger + intervening if
  Static → mechanics applied to objects/players/game while a condition holds
Effect   (one-shot, CR 610): damage, destroy, move, draw, counters, tokens, life,
          ApplyMechanics (continuous effect with a duration), delayed triggers, …
Mechanic (continuous, CR 611): layer effects, rules the engine asks about (deathtouch,
          lifelink, trample…), combat restrictions, permissions and costs, game rules,
          replacement and prevention; each states its layer and duplicate stacking
Keyword: kept as a keyword ability; KeywordExpansions maps it to abilities
Building blocks: Cost, Trigger/EventPattern, Objects/ObjectFilter, Players/
          PlayerFilter, Value, Condition, Duration, ManaProduction, TokenSpec
```

Card memory (CR 607 linked abilities, chosen colours) is named data on the source
(`Effect.Remember`, `Step.Choose(remember = true)`), returned to the host as a
visible object (P2). Text the model can't express stays as `Ability.Unparsed`,
`Step.Manual` or `CostPart.Manual`, so a card can be partly automated.

### Per-card status

An ability is automated when it has no `Unparsed`/`Manual` parts and its keyword has
an expansion; otherwise it is shown for handling by hand.

## 6. Parser (arcanum-parser)

Our own, from MTGJSON oracle text. The plan:

1. **Measure first.** Normalise every card's oracle text (self-references → `~`: the
   card name, and the newer "this creature" / "this spell" wording; numbers,
   mana symbols and type words → placeholders), split into abilities (one per line;
   keyword lines split at commas) and count each resulting pattern across all cards.
   Optionally weight by how often cards appear in our decks and cubes. The sorted list
   is the build order (D6).
2. **Keywords** via a table of keyword abilities (CR 702) with parameters (Ward {2},
   Toxic 2, Equip {3}, …), cross-checked with MTGJSON's `keywords` field.
3. **Grammar** for the rest: a hand-written recursive-descent parser over tokens,
   built from the frequent patterns down. Modern oracle wording is highly regular;
   older cards are re-templated in Oracle, so one grammar covers most of them.
4. **Unparsed spans are kept**, never guessed. An ability that doesn't parse fully
   becomes `manual` (or `partial` when the unparsed part is a separable sentence),
   with the original text attached for the UI.
5. **Round-trip check.** A renderer turns definitions back into English; the test
   suite compares it with the test card text to catch wrong parses. A golden corpus
   of our own test cards (D15) protects behaviour.
6. **Coverage numbers** per pattern for the developer (a tool output, not a product
   document).

**Workflow (D14).** One effect or a group of similar wordings at a time, usually in
frequency order: look at the real wordings in the local analysis, decide together how
they parse and what definition they produce, write test cards for them, implement.

Out of scope for parsing: `isFunny` cards, digital-only mechanics, and text the
engine cannot represent yet (left manual until the engine supports it).

## 7. Engine (arcanum-engine) and its use in ATW

### 7.1 Derived view (layers)

For each object the engine computes characteristics with the layer system (CR 613):
copy (1), control (2), text (3), type (4), colour (5), abilities (6), P/T (7a–7d),
with timestamps and dependencies within layers. Inputs are only the given game state,
the definitions and the stored effect objects (P2, P3).

In ATW the view feeds the existing card rendering: computed P/T in the corner
(TBL-021), granted keywords, changed types and colours. **Inspect** shows where each
value comes from ("2/2 printed, +1/+1 Glorious Anthem, +3/+3 Giant Growth until end
of turn").

**Manual values and the view.** Today P/T is a free manual corner (TBL-021–023),
keywords are note text (TBL-086) and counters are six colours (TBL-078). Proposal:
- A manual P/T edit while automation is on becomes a **player-set P/T effect** in
  layer 7b ("set to"), visible and removable like any effect object.
- **Effect objects have durations**: until end of turn, until your next turn, for as
  long as a source remains, permanent, and others the definitions need. Players can
  set any of them by hand; until end of turn stays the only quick one (the EOT
  controls, TBL-079/082), the others are in a less direct place.
- **Typed counters** are added (+1/+1, −1/−1, loyalty, and named counters). Colour
  counters stay as manual markers the engine ignores.
- Keywords written in notes are read as manually granted keywords.

### 7.2 Casting and activating

Double-tapping a hand card (today "play to Stack", ZON-004/STK-004) **casts** it
when its spell ability is automated, and activated abilities get the same flow from
the card menu. The **cast dialog** (D3) asks, in rules order (CR 601.2):
mode(s), X, targets per named slot, non-mana costs, and the **mana sources to tap**,
pre-filled with a suggested tapping (§7.8). Confirming taps the sources, pays the
other costs and puts a structured entry on the Stack, as one Undo.

A **"Accept automatically"** option skips the dialog whenever its suggestions
leave nothing to decide. Whether a single legal target counts as "nothing to decide"
is a playtest question (§15).

### 7.3 Resolution

Resolving a Stack entry evaluates its definition against the current state with the
entry's recorded choices: illegal targets are dropped (CR 608.2b) and the spell
fizzles when all are gone. Choices made during resolution ("you may", "choose a
card") are asked from the right player; everything else applies automatically (P5).
Effects that need hidden information (search, reveal, random discard) produce the
existing library/hand actions where the server fills in the hidden part.

Effects with a duration create **effect objects** (§8) that end on their own:
until-end-of-turn effects in the cleanup step, "for as long as" effects when their
source leaves.

**When resolution starts** follows D4: in async play the normal Stack/attention
flow. Today a permanent spell resolves by itself right after a double-tap (STK-004)
and the opponent's way to respond is to go back; the same could apply to every entry
with nothing left to decide. This is a playtest question (§15).

### 7.4 Triggers

The engine checks triggers **per event**, not by comparing whole boards: for each
change that just happened (a card moved, damage dealt, a step began), it looks at the
abilities that could trigger on it. It needs the object **as it was just before** the
event as well as after, because "leaves the battlefield" and "dies" triggers look back
in time (CR 603.10): "Whenever another creature you control dies" must see the
creature's controller and types from when it was still on the battlefield. Events
that happen together (a board wipe) are checked together. ATW events already store
how to apply and revert them (SYS-027), so the before-state of one event is cheap.

Following P5, a trigger with no targets, options or ordering choice goes onto the
Stack by itself. Only triggers that need a decision (targets, modes, ordering several
simultaneous triggers) ask their controller. A "you may" in a trigger is decided on
resolution; the trigger goes on the Stack either way (CR 603.5). The
Stack itself still has no reordering (STK-021); order is chosen when triggers are put
on.

Delayed triggers ("at the beginning of the next end step, return it") are visible
effect objects (P2).

### 7.5 State-based actions

State-based actions (CR 704) apply automatically in the same batch as the action
that caused them (P5): lethal damage, 0 toughness, 0 life, +1/+1 and −1/−1 counter
annihilation, auras with no legal object. Those with a choice (legend rule) ask
the player. All are taken back with the batch's Undo.

### 7.6 Combat

Combat lanes already exist (CMB-*). The engine adds combat damage: first strike /
double strike damage steps, trample excess, deathtouch counting as lethal, lifelink;
menace/flying/reach shown as warnings (never blocks). An attacker blocked by several
creatures divides its damage among them as its controller chooses (current rules, no
damage assignment order); when there is only one sensible way, it applies
automatically.

### 7.7 Hidden information

The engine sees only what its caller sees (unknown objects, §4.1). It never reveals
anything by itself: an effect that needs hidden information is either done through a
server-filled action or asked from the player who can see it. Choices the opponent
must make (e.g. "discard a card") are shared pending decisions (§8); deciding one for
them follows §7.9.

### 7.8 Mana payment

The engine suggests which sources pay a cost: matching each mana symbol to an
untapped source that can produce it, preferring sources that keep the most other
options open. It must handle hybrid and Phyrexian mana, X, generic costs, sources
with several colours, and sources with conditions or extra costs (treasures,
"spend only on creature spells", summoning-sick creatures). The player can change the
suggestion in the cast dialog. Mana that is added but not spent at once (rituals) is
kept in a visible **mana pool** until it empties at the end of the step (P2).

### 7.9 Deciding for another player (D11)

Every pending decision is shown to the player it belongs to, and the other player can
open and answer it too, like any manual action:
- The UI makes clear you are acting on someone else's behalf (who the decision belongs
  to, and that you are making it for them) before you confirm.
- History records it as your action made for them ("Chose Grizzly Bears as the target
  for bob's trigger").
- By default it pauses the other player's automatic playback at that event, so they
  see what was decided for them.
- A decision that needs hidden information of the other player (e.g. which card they
  discard from their hand) needs the same extra step to reach that information as
  today (SYS-036): it is never shown to you by default.

### 7.10 Autoplay decisions (later, D12)

A player can store answers to decisions ahead of time, so they are made even while
that player is offline. First kind: "always put these triggers on the Stack in this
order". Later possibly more complex ones ("always yes" for a "you may", a fixed
target choice for an ability).

- **Stored in the game state** as shared objects (P2), each with its owner, what
  decision it matches (decision kind, source ability) and the answer. Created from
  the decision dialog ("Always do this") and listed where they can be removed.
- **In the engine API (generic):** the caller passes the stored answers in with the
  game state. When a decision request matches one, the engine applies it and carries
  on as if the player had answered, so every caller gets the same result from the
  same input.
- **Exactly one client applies it, always the same one:** the client that sends the
  action that caused the decision. The automatic answer and its results go into that
  action's own all-or-nothing batch (SYS-010). There is no separate "pending, then
  picked up by whichever client is online" step, so two clients can never race to
  resolve the same decision. Every decision in ATW is caused by some action (a move,
  a step change, a resolution), so this client is always defined.
- **Deterministic:** the engine has no randomness of its own (random parts use the
  existing seeded actions) and its result depends only on the game state, the stored
  answers and the definitions.
- **Only with visible information:** a stored answer is applied only when the
  applying client can see everything the answer needs. Otherwise the decision stays
  pending for its player.
- **Changes in flight (decided):** if a player changes their stored answers while the
  other player's batch that used the old ones is on its way, the server takes things
  in arrival order: the batch is accepted with the old answer, and the change counts
  from the next decision on. Nothing is refused.
- **Visibility to the opponent:** which stored answers (and which details of them) the
  opponent can see is worked out in playtesting (§15).

## 8. New shared state in ATW

Each of these is a rules-version bump (SYS-031) and needs owner approval of its
catalog entries.

| Object | Purpose |
|---|---|
| Automatum setting per game | The one on/off switch (D20); an event, so both players see the same board. |
| Structured Stack entry | Which ability of which card, chosen modes, X, targets by named slot, controller. Today's text marks (STK-016) stay for manual entries. |
| Effect objects | Continuous effects with source, affected objects, layer, timestamp and duration; shown on the cards they affect (D19). Whether they can be removed by hand while Automatum is on is open (D20). |
| Card memory | Chosen colour/type/name, "exiled with ~", attached-to, kicked, etc. |
| Pending decisions | Triggers waiting for a choice, and choices made during resolution, each with the player it belongs to. Answered asynchronously, by that player or on their behalf (§7.9). |
| Autoplay decisions | Answers a player stored ahead of time (§7.10), later phase. |
| Mana pool | Mana added but not yet spent (§7.8). |
| Typed counters | +1/+1, −1/−1, loyalty, named counters (§7.1). |
| Automation flag on events | Marks an action batch as produced by automation, for history texts ("Lightning Bolt dealt 3 damage to Grizzly Bears"). |

## 9. Turns and priority

- **Async play (D4):** today's Stack and attention flow. No new priority system.
- **Live play: open.** Options discussed:
  1. No response window: the caster resolves when they like; the opponent responds
     by rewinding (live branch rewind exists).
  2. A short window while the opponent is present: a countdown with a "Wait!" button
     that holds the Stack; no reaction lets it resolve.
  3. A player who doesn't react in time continues in timeline mode at that point and
     can still respond by branching (builds on the existing auto-rewind settings).
  Leaning: 2 + 3, window length a setting. Needs playtesting.

## 10. Toggles

- **Automatum on/off** (shared, one per game, D20). Off: everything behaves as today.
  On: computed values, effect objects, structured Stack entries, automatic triggers,
  state-based actions, the cast dialog and automatic resolution. What happens to
  stored effect objects and Stack entries when it is switched, and which manual
  actions are restricted while it is on, is decided when this part is built.

## 11. Fit with existing rules

- **History, undo, branches:** unchanged, because results are ordinary actions (P1).
  Undo of an automated batch is one Undo (SYS-004, SYS-010).
- **No full replay (SYS-026):** the derived view is computed from the current state
  only; triggers look at one event's before and after.
- **Speed (SYS-025):** the derived view for a typical board and a resolution are small
  computations; the engine must meet the 20 ms budget and is covered by the existing
  slow-action reports.
- **Rule versions (SYS-031):** recorded events never depend on definitions.
- **Player access (SYS-036/037):** every assisted action and every decision is
  available to both players on any card, like manual actions ("phone test", §7.9).

## 12. Licences (summary)

- MTGJSON is MIT; card text belongs to Wizards (Fan Content Policy): no change from today.
- Forge is GPL-3. Its scripts are not used as data; translated definitions would be a
  derivative that must be distributed under GPL-3. Forge may be used only as a
  cross-check (comparing behaviour), which carries no obligations.
- XMage is MIT: may be read as a reference; copied code keeps its notice.
- The arcanum libraries' own licence is decided if they are ever published.

## 13. Phases

Phase 0 is fixed; the order and content of later phases may change as work goes on
(D9).

| Phase | Content | New shared state |
|---|---|---|
| 0 | arcanum-api format, arcanum-parser skeleton, pattern frequency measurement, test-card corpus, pinned CR version, arcanum-engine API skeleton with MTG-term tests. No app changes. | none |
| 1 | Derived view: static P/T and keyword effects, layers, typed counters, Inspect details; game automation toggle; combat damage. | toggle, typed counters, effect objects |
| 2 | Cast dialog with targets and suggested mana tapping, accept automatically; Activate; resolution of simple spells and abilities (damage, destroy, exile, draw, counters, tokens, pump with durations, bounce); "Automatum off". | structured Stack entries, mana pool, automation flag |
| 3 | Triggers (enters, dies, attacks, upkeep, end step), trigger ordering, delayed triggers, state-based actions. | pending decisions, card memory |
| 4 | Opponent choices, modal spells with per-mode targets, replacement effects, copy effects, counterspells. | |
| Later | Autoplay decisions (§7.10); auto-tap preferences beyond the suggested tapping; removing the game-wide off switch. | autoplay decisions |

Parser coverage grows alongside every phase, in frequency order.

## 14. Catalog and spec entries this would change

- **SYS-001** "no rules referee … the app never works out costs, targets or
  resolution" — would allow an optional assistant that does.
- **SYS-002** "Nothing is guessed from card names or rules text" — definitions come
  from rules text (parsed, not guessed).
- **P-056** (`docs/design/03-contracts/product.md`) "No authoring UI/executable rules".
- **TBL-021–024, TBL-078–079, TBL-082, TBL-085–086** — P/T, counters, durations and
  keywords when automation is on (§7.1).
- **ZON-004 / STK-004** — double tap becomes Cast (§7.2).

Exact new entry texts are written per phase and approved by the owner before building.

## 15. Open questions (to settle in playtesting, D10)

1. Live-play response windows (§9).
2. Whether every entry with nothing left to decide resolves by itself like STK-004,
   with going back as the way to respond (§7.3).
3. Whether a single legal target counts as "nothing to decide" for automatic
   acceptance (§7.2).
4. Manual P/T edits as a layer-7b "set" effect, typed counters next to colour
   counters, keywords from notes (§7.1).
5. Turning game automation off mid-game: write computed values into the manual
   fields so nothing jumps, or fall back to the stored manual values.
6. Where the parser runs: a developer tool producing a data set, or a server job on
   catalog import.
7. Autoplay decisions: exact rules and scenarios for what the opponent can see of
   your stored answers (§7.10).
8. Whether the timeline shows derived values with the definitions of the time (needs
   definition versions per game) or with current definitions.
