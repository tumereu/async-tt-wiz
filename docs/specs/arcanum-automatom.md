# Arcanum Automatom — design draft

**Status: draft for owner review (2026-11-01).** Not approved, not on the roadmap or
backlog, and nothing is built from it. Breaking parts (new shared state, catalog
changes) need the owner's go-ahead per phase.

## 1. What it is

An optional rules-automation layer for Magic: The Gathering on top of the existing
manual table. It has three parts:

1. **Card definitions**: a defined, typed format describing what a card's abilities
   do (costs, targets, effects, conditions, durations).
2. **Parser**: our own parser that turns MTGJSON oracle text into card definitions.
3. **Engine**: computes derived board state (P/T, keywords, …), offers assisted
   actions (Cast, Activate, Resolve, put triggers on the Stack) with choice UI, and
   turns a confirmed resolution into ordinary game actions.

The game stays a board-game simulator underneath. When automation is wrong, a card
is unsupported or a situation is unusual, players fix it with the manual actions
that exist today.

### Non-goals

- A rules referee. The engine never refuses or blocks a player action (SYS-036,
  SYS-037); it only computes and offers.
- Using Forge, XMage or other engines as a runtime, or Forge card scripts as data
  (GPL-3; see §12). Forge may be used as a cross-check only.
- An LLM step in the parser.
- Automated mana payment in the first phases (players tap lands by hand).
- Un-cards (MTGJSON `isFunny`) and digital-only mechanics (Alchemy: seek,
  perpetually, …). They stay manual permanently.

## 2. Owner decisions so far (2026-11-01)

| # | Decision |
|---|---|
| D1 | The engine is an **assistant**, not a referee. |
| D2 | Automation of shared, derived state (e.g. computed P/T) is **per game and shared**: both players see the same board. Each player separately chooses whether to **use assisted controls** (personal). If automation works well enough, the game-wide off switch may later be removed. |
| D3 | Mana is tapped **manually** at first. Auto-tapping mana sources is a potential later feature. |
| D4 | Async play uses **our normal Stack/attention rules**. Live play is open (§9). |
| D5 | **Our own parser** from oracle text; no Forge data, no LLM fallback. |
| D6 | Coverage is prioritised by **how often** a pattern comes up; rare and silly cards stay manual. |

## 3. Principles

- **P1 Results are ordinary actions.** A resolution produces a batch of normal
  recorded actions (moves, damage, life, counters, tokens, …), sent as one
  all-or-nothing batch (SYS-010) and taken back by one Undo. History, undo, branches
  and playback keep working as today; replaying a game never needs the engine.
- **P2 No hidden engine memory.** Everything the engine must remember between
  actions is a visible shared board object that players can inspect, edit and
  remove: an until-end-of-turn effect, a chosen colour, "exiled with", a pending
  delayed trigger, Stack targets. A manual fix is therefore always possible and the
  engine never disagrees with what players see.
- **P3 Derived state is a pure function** of the current board, the card definitions
  and the visible effect objects. It is recomputed, never stored, so it stays correct
  after any manual edit, undo, branch or timeline step, and never replays history
  (SYS-026).
- **P4 Everything can fall back to manual.** Each ability is automated or manual;
  manual abilities behave exactly as today. A player can switch any card to manual.
- **P5 Shared code.** Card model, parser output loader and engine live in Kotlin
  Multiplatform shared code (new module, e.g. `rules/`), so the Android app predicts
  locally within SYS-025 and the future web client reuses it.
- **P6 The server stays authoritative over actions, not over automation.** Since any
  player may do anything manually, the server does not need to re-check that an
  automated batch followed the rules. It validates the actions as it does today, plus
  the new shared objects (§8).

## 4. Architecture

```
MTGJSON oracle text ──► parser (offline tool, JVM) ──► card definitions (versioned data)
                                                            │ served by server, cached by app
                                                            ▼
board state (PlayState) + definitions + effect objects ──► engine (KMP shared)
        ├─ derive(): computed view per object (P/T, types, colours, keywords, abilities)
        ├─ offers(): assisted actions available to a player (Cast, Activate, Resolve, …)
        ├─ choices(): what an offer needs (mode, X, targets, order, opponent's choice)
        └─ resolve(): confirmed offer + choices ──► batch of ordinary PlayActions
```

- **Parser** runs offline (dev tool or server job), not on devices.
- **Definitions** are dynamic data like other card data (D-243): delivered by the
  server, versioned, never bundled into the app. Updating a definition improves
  future automation without an app release; accepted events never change.
- **Engine** is called by the app for prediction and UI. The server may call
  `derive()` where it needs computed values (e.g. later checks), but nothing
  requires it.

## 5. Card definition format

A typed tree mirroring the Comprehensive Rules, serialised as JSON for delivery,
with a compact text form for hand-written fixes and tests. The text form below is
illustrative; exact syntax is decided in phase 0.

### Building blocks

| Block | Covers | CR |
|---|---|---|
| Ability kinds | Spell, Activated, Triggered, Static, Keyword, Replacement, Mana | 113, 601–605 |
| Cost | mana, {T}, sacrifice, discard, pay life, remove counters | 118, 602 |
| Target slots | named slots with a filter and count ("target creature you don't control") | 115 |
| Effects | damage, destroy, exile, draw, discard, mill, move, counters, tokens, life, tap/untap, P/T change, gain keyword, copy, counter spell, search, … | 701 |
| Filters | object selectors ("other artifact creatures you control", "nonland permanent an opponent controls") | 109, 205 |
| Values | numbers, X, counts ("the number of Elves you control"), references to named targets | 107 |
| Durations | until end of turn, until your next turn, for as long as ~ remains, permanent | 611 |
| Conditions | intervening if, "if you control …", kicker paid, threshold-style counts | 603.4 |
| Modes | choose one / two / one or more, with per-mode targets | 700.2 |

### Examples

```
# Lightning Bolt — "Lightning Bolt deals 3 damage to any target."
spell:
  target t: any
  effect: damage(source=~, to=t, amount=3)

# Glorious Anthem — "Creatures you control get +1/+1."
static:
  affects: creatures(controller=you)
  layer 7c: pt(+1, +1)

# "Other artifact creatures you control get +1/+1."
static:
  affects: creatures(controller=you, types=[artifact], other=true)
  layer 7c: pt(+1, +1)

# Giant Growth — "Target creature gets +3/+3 until end of turn."
spell:
  target t: creature
  effect: pt(t, +3, +3, until=endOfTurn)     # resolves into a visible effect object

# Elvish Visionary — "When ~ enters, draw a card." (normalised self-reference)
triggered:
  when: enters(~)
  effect: draw(you, 1)

# Llanowar Elves — "{T}: Add {G}."
mana:
  cost: tap(~)
  produce: G                                 # shown as a hint; mana stays manual (D3)
```

### Per-card status

Each definition records, per ability: `auto`, `partial` (some effects automated,
the rest shown as a reminder to do by hand) or `manual`, plus the definition version
and the parser version that produced it. Hand-written overrides take precedence over
parser output and survive re-parsing.

## 6. Parser

Our own, from MTGJSON oracle text. The plan:

1. **Measure first.** Normalise every card's oracle text (self-references → `~`: the
   card name, and the newer "this creature" / "this spell" wording; numbers,
   mana symbols and type words → placeholders), split into abilities (one per line;
   keyword lines split at commas) and count each resulting pattern across all cards.
   Optionally weight by how often cards appear in our decks and cubes. The sorted list
   is the build order (D6).
2. **Keywords** via a table of keyword abilities (CR 702) with parameters (Ward {2},
   Toxic 2, Equip {3}, …), cross-checked with MTGJSON's `keywords` field.
3. **Grammar** for the rest: a hand-written recursive-descent parser over tokens
   (no generated parser needed), built from the frequent patterns down. Modern oracle
   wording is highly regular; older cards are re-templated in Oracle, so one grammar
   covers most of them.
4. **Unparsed spans are kept**, never guessed. An ability that doesn't parse fully
   becomes `manual` (or `partial` when the unparsed part is a separable sentence),
   with the original text attached for the UI.
5. **Round-trip check.** A renderer turns definitions back into English; the test
   suite compares it with the oracle text for the parsed corpus to catch wrong
   parses. A golden corpus of hand-checked cards protects behaviour.
6. **Coverage report** per pattern for the developer (a dev tool output, not a
   product document).

Out of scope for parsing: `isFunny` cards, digital-only mechanics, and text the
engine cannot represent yet (left manual until the engine supports it).

## 7. Engine

### 7.1 Derived view (layers)

For each object the engine computes characteristics with the layer system (CR 613):
copy (1), control (2), text (3), type (4), colour (5), abilities (6), P/T (7a–7d),
with timestamps and dependencies within layers. Inputs are only the current board,
the definitions and visible effect objects (P3).

The view feeds the existing card rendering: computed P/T in the corner (TBL-021),
granted keywords, changed types/colours. A tap on the P/T can explain where the value
comes from ("2/2 printed, +1/+1 Glorious Anthem, +3/+3 Giant Growth until end of turn").

**Manual values and the view.** Today P/T is a free manual corner (TBL-021–023),
keywords are note text (TBL-086) and counters are six colours (TBL-078). Proposal:
- A manual corner edit while automation is on becomes a **player-set P/T effect** in
  layer 7b ("set to"), visible and removable like any effect object. The EOT P/T
  controls create an until-end-of-turn 7c effect.
- **Typed counters** are added (+1/+1, −1/−1, loyalty, and named counters). Colour
  counters stay as manual markers the engine ignores.
- Keywords written in notes are read as manually granted keywords.

### 7.2 Offers and choices

With assisted controls on, the engine offers actions where the definitions allow:
Cast (hand, or other zones when an ability permits), Activate, Resolve top of Stack,
Put trigger on the Stack, Assign combat damage. A choice flow then asks for what is
needed, in rules order (CR 601.2): mode(s), X, targets per named slot, costs to pay
(non-mana costs automated; mana costs shown, paid by hand), then confirm.

Proposal for gestures: the double tap on a hand card becomes Cast for a fully
automated card (it is "play to Stack" today, ZON-004/STK-004); the card menu gets
the assisted actions. Exact gestures are UI design work for phase 2.

### 7.3 Resolution

Resolving a Stack entry evaluates its definition against the current board with
the entry's recorded choices: illegal targets are dropped (CR 608.2b) and the spell
fizzles when all are gone. The result is a batch of ordinary actions shown as a short
preview ("Deal 3 damage to Grizzly Bears") and then sent. Effects that need hidden
information (search, reveal, random discard) produce the existing library/hand
actions where the server fills in the hidden part.

Effects with a duration create **effect objects** (§8). They end on their own:
"until end of turn" effects are cleared by the cleanup step, "for as long as" effects
when their source leaves.

### 7.4 Triggers

After each recorded action the engine checks the before/after states against the
triggered abilities on the board (enters, dies, attacks, beginning of upkeep, …) and
shows **pending triggers** to their controller. The controller puts them on the
Stack (ordering them first if there are several) or dismisses them. The Stack itself
still has no reordering (STK-021); order is chosen when triggers are put on.

Delayed triggers ("at the beginning of the next end step, return it") are visible
effect objects (P2).

### 7.5 State-based actions

After each action the engine checks state-based actions (CR 704): lethal damage,
0 toughness, 0 life, legend rule, +1/+1 and −1/−1 counter annihilation, auras with no
legal object. With automation on, the obvious ones (lethal damage, 0 toughness,
counter annihilation) are added to the same batch; the ones that need a choice
(legend rule) are offered. All are undoable as part of the batch.

### 7.6 Combat

Combat lanes already exist (CMB-*). The engine adds computed combat damage:
first strike / double strike damage steps, trample excess, deathtouch counting as
lethal, lifelink, menace/flying/reach shown as warnings (never blocks). Damage
assignment order for several blockers is a choice for the attacker.

### 7.7 Hidden information

The engine on a device sees only what that player sees. It never reveals anything
by itself: an effect that would need hidden information is either done through a
server-filled action or offered to the player who can see it. Pending choices for
the opponent (e.g. "discard a card") are shared objects that only that player
answers (§8).

## 8. New shared state

Each of these is a rules-version bump (SYS-031) and needs owner approval of its
catalog entries.

| Object | Purpose |
|---|---|
| Automation setting per game | Game-wide on/off for derived state (D2); an event, so both players see the same board. |
| Structured Stack entry | Which ability of which card, chosen modes, X, targets by named slot, controller. Today's text marks (STK-016) stay for manual entries. |
| Effect objects | Continuous effects with source, affected objects, layer, timestamp and duration; shown on the cards they affect; removable by hand. |
| Card memory | Chosen colour/type/name, "exiled with ~", attached-to, kicked, etc. |
| Pending triggers | Triggers waiting for their controller to put them on the Stack or dismiss them. |
| Pending choices | A choice another player must make during resolution (opponent discards, opponent chooses a mode). Answered asynchronously. |
| Typed counters | +1/+1, −1/−1, loyalty, named counters (§7.1). |
| Automation flag on events | Marks an action batch as produced by automation, for history texts ("Lightning Bolt dealt 3 damage to Grizzly Bears"). |
| Per-card manual switch | A shared flag that turns automation off for one card (its statics stop applying, its triggers aren't offered). |

## 9. Turns and priority

- **Async play (D4):** today's Stack and attention flow. Casting puts the entry on the
  Stack; the opponent responds or resolves when they next act. No new priority system.
- **Live play: open.** Options discussed:
  1. No response window: the caster resolves when they like; the opponent responds
     by rewinding (live branch rewind exists).
  2. A short window while the opponent is present: a countdown with a "Wait!" button
     that holds the Stack; no reaction lets it resolve.
  3. A player who doesn't react in time continues in timeline mode at that point and
     can still respond by branching (builds on the existing auto-rewind settings).
  Leaning: 2 + 3, window length a setting. Needs playtesting.

## 10. Toggles

- **Game: "Automation"** (shared, D2). Turns derived state, triggers, state-based
  actions and structured Stack entries on or off for the whole game. Turning it off
  keeps the board as it is: computed values are written into the manual fields so
  nothing jumps. Turning it on starts computing from the current board. Possibly
  removed later.
- **Player: "Assisted controls"** (personal, SYS-005). Whether this player's gestures
  and menus offer Cast/Resolve/Activate and the choice flows, or behave as today.
- **Card: "Manual"** (shared, §8).

## 11. Fit with existing rules

- **History, undo, branches:** unchanged, because results are ordinary actions (P1).
  Undo of an automated batch is one Undo (SYS-004, SYS-010).
- **No full replay (SYS-026):** derived view is computed from the current board only;
  trigger detection looks at one action's before/after.
- **Speed (SYS-025):** the derived view for a typical board (tens of permanents, a
  handful of effects) and a resolution are small computations; the engine must meet
  the 20 ms budget and is covered by the existing slow-action reports.
- **Rule versions (SYS-031):** recorded events never depend on definitions. Viewing an
  old position in the timeline computes the view with the current definitions; small
  differences there are accepted.
- **Player access (SYS-036/037):** every assisted action is available to both
  players on any card, like manual actions ("phone test").

## 12. Licences (summary)

- MTGJSON is MIT; card text belongs to Wizards (Fan Content Policy): no change from today.
- Forge is GPL-3. Its scripts are not used as data; translated definitions would be a
  derivative that must be distributed under GPL-3. Forge may be used only as a
  cross-check (comparing behaviour), which carries no obligations.
- XMage is MIT: may be read as a reference; copied code keeps its notice.

## 13. Phases

| Phase | Content | New shared state |
|---|---|---|
| 0 | Card format, parser skeleton, pattern frequency measurement, golden corpus, `rules/` module. No app changes. | none |
| 1 | Derived view: static P/T and keyword effects, layers, typed counters, explanation on tap; game Automation toggle; combat damage suggestion. | toggle, typed counters, effect objects (player-set P/T) |
| 2 | Cast/Activate/Resolve for simple spells and abilities (damage, destroy, exile, draw, counters, tokens, pump until end of turn, bounce); choice flows; assisted controls. | structured Stack entries, effect objects, automation flag |
| 3 | Triggers (enters, dies, attacks, upkeep, end step), trigger ordering, delayed triggers, state-based actions. | pending triggers, card memory |
| 4 | Opponent choices, modal spells with per-mode targets, replacement effects, copy effects, counterspells. | pending choices |
| Later | Auto-tap mana sources, mana cost checking, removing the game-wide off switch. | |

Parser coverage grows alongside every phase, in frequency order.

## 14. Catalog and spec entries this would change

- **SYS-001** "no rules referee … the app never works out costs, targets or
  resolution" — would allow an optional assistant that does.
- **SYS-002** "Nothing is guessed from card names or rules text" — definitions come
  from rules text (parsed, not guessed).
- **P-056** (`docs/design/03-contracts/product.md`) "No authoring UI/executable rules".
- **TBL-021–024, TBL-078–079, TBL-085–086** — P/T, counters and keywords when
  automation is on (§7.1).
- **ZON-004 / STK-004** — double tap becomes Cast with assisted controls (§7.2).

Exact new entry texts are written per phase and approved by the owner before building.

## 15. Open questions

1. Live-play response windows (§9).
2. Manual P/T edits as a layer-7b "set" effect, typed counters next to colour
   counters, keywords from notes (§7.1) — acceptable?
3. Turning automation off: write computed values into manual fields (proposed) or
   just drop back to the stored manual values?
4. Which state-based actions apply on their own and which are only offered (§7.5).
5. Where the parser runs: a developer tool producing a checked-in or uploaded data
   set, or a server job on catalog import.
6. Whether the timeline should show derived values as they were (would need
   definition versions per game) or with current definitions (proposed).
