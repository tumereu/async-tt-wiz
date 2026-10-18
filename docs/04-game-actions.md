# 4. Game action catalog

Status: generic engine boundary confirmed; detailed coverage and per-action
schemas proposed. MTG examples are uses of primitives, not recognized mechanics.

## 4.1 Shared action envelope

Every game action specifies:

- Stable action ID and parent boundary; actor and originating client operation.
- Literal operation kind and optional user label/note.
- Typed generic operations, stable referenced object IDs, structural preconditions.
- All changed values or sufficient deterministic data to derive them.
- Random outcome references where applicable.
- A human-readable summary, detailed before/after inspection, and schema version.

The same action system supports normal play and state corrections. Convenience
commands are compositions of documented primitives, not a second hidden rules
engine. Players can edit either side of the table.

A completed action applies locally and automatically synchronizes. Multi-field
actions may require choosing objects, values, or destinations; completing those
inputs invokes the action without a second generic confirmation/Send gate.
Incomplete input does not stream malformed or half-selected game actions.

## 4.2 Coverage matrix

| ID | Capability | Required manual behavior |
| --- | --- | --- |
| A-001 | Create game | Invite a second person; choose starting configuration and decks. |
| A-002 | Card-list import/edit | Import lists, resolve catalog entries, edit quantities, faces/printings, and generic labels/groupings without legality rules. |
| A-003 | Instantiate objects | Create listed cards in explicitly selected containers; expose setup for editing. |
| A-004 | Setup edits | Choose/configure starting arrangement, labels, and values; players perform any redraw/return process using ordinary moves. |
| A-005 | Move cards | Move one/many objects between containers/areas and choose insertion order; no rule-dependent side effects. |
| A-006 | Ordered-pile operations | Select top/bottom/N, move to an explicit destination, inspect/reveal, search, reorder, shuffle the whole pile, and choose randomly. D-48 excludes library top/bottom shuffle shortcuts. |
| A-007 | Row/fan operations | View, arrange, cover/uncover, select, and move cards; labels such as Hand do not alter semantics. |
| A-008 | Orientation/faces | Tap/untap presentation, rotate, choose face, cover/uncover, and edit labels. D-100/D-110 make Untap all literal for the turn player's battlefield, with no skip flag/inferred exclusions; manually re-tap exceptions. Both standalone/+advance variants preserve unrelated fields. |
| A-009 | Participant association | Manually assign/edit labeled participant associations; moving a card does not infer ownership/control changes. |
| A-010 | Counters | Colored text-bearing counters on cards/participants; per-color +1/-1/Set, no Add/Initialize. D-58–D-68 fix palette/uniqueness, zero/empty/trim, inline retained menus, applicability skipping, and absent +/- creation as `1`/`-1`. No recolor; exact hit regions/formatting remain open. |
| A-011 | Numerical values | Adjust/set arbitrary named values. Life, Mana, Energy, and similar names are uninterpreted labels. |
| A-012 | Labeled-area movement | Move cards to/from areas such as Stack (renamed from Cast, D-113); record actual origin/destination, not inferred casting/payment/resolution. D-112–D-114 separately add explicitly chosen projections and contextual Stack actions. |
| A-013 | Labeled pieces | Create/duplicate a generic note, marker, or piece with optional text/icon; players can use these for abilities/triggers without engine recognition. |
| A-014 | Pile order/edit | Reorder, duplicate, move, or remove selected objects in any pile; no built-in stack-resolution command. |
| A-015 | Group/event-scoped pings | D-74–D-80 confirm event-scoped mode/toggles, retained review, selection-preserving switching, consumed ordinary taps, and one standard marker. Context Ping/Unping uses the menu-trigger card's state and applies uniformly to its selected-set scope without mode changes. Location matching, routing/menu lifetime, and revisions remain open. |
| A-016 | Custom objects/copies | D-81–D-86 confirm single automatically placed catalog tokens (including one Blank entry), token-producing Clone with copied fields but no links/pings, and removal. No quantity/preview/Repeat shortcut or dedicated custom-token form. Exact placement/initialization, association fields, broader authoring, and boundaries remain open. |
| A-017 | Generic visual links | D-70–D-73 confirm binary undirected unique pairs, context picking, fan-out/removal, and independent movement. Repeated Link preserves pairs; no labels/styles or separate initial Attach/follow tool. Joint movement uses explicit multi-selection. |
| A-018 | Descriptive attributes | Override instance labels, text, faces, and named attributes without interpreting game meaning. D-52/D-53 add plain corner text with pattern shortcuts; D-69 adds multiline card notes with short on-card text, optional long-text ellipsis, full inspection, and empty-text removal. |
| A-019 | Board markers/connectors | D-119 adds event-wide ping order numbers. D-120 supports links between projections that disappear with endpoints. D-112/D-118 separately require explicit assigned/unassigned blocker projections and adaptive combat spacing; no inferred legality/damage from pings/links. |
| A-020 | Manual value changes | Edit one/many explicitly selected values; no damage calculation or automatic distribution. |
| A-021 | Manual object changes | D-84/D-85 remove tokens moved into configured Library/Graveyard/Exile across all move controls; mixed sets move ordinary cards normally. Context Remove applies to ordinary cards/tokens and selections, no pile move/routine confirmation, with automatic sync and undo/history. No other inferred token expiry or rules effects. Exact routing/references/boundaries remain open. |
| A-022 | Turn/phase/step tracking | D-96–D-103 require MTG tracking/shortcuts and turn context. D-106–D-108 add one-tap End turn/no number editor and starter/turn-1 setup. D-105 removes priority; D-109 leaves extra turns manual/approximately counted. D-111 advances combined shortcuts despite no untap changes or empty Library, with no inferred loss. Exact controls/schema/boundaries remain open. History/undo/branches/offline/sync apply; arbitrary markers remain separate. |
| A-023 | Named tracking sets | Add arbitrary per-object/per-participant named values/labels; sufficient for manual commander-style tracking without that mechanic in the engine. |
| A-024 | Custom status | Generic notes/icons/named trackers represent unusual or future mechanics; the engine does not recognize their labels. |
| A-025 | Faces and visual composites | Show/select faces, overlap/group pieces, preserve component identities; no automatic meld/merge/phase/transform rules. |
| A-026 | Additional containers | Access/configure extra labeled containers and move cards between them; no outside-game or sideboarding restrictions. |
| A-027 | Randomness | D-87/D-92/D-93 confirm one-die Roll, Flip coin, fresh draws for new die/coin/Shuffle invocations (including after undo), and auto-synced stored results/orders reused for review/redo/retries. No opponent-action seed reset. Other random controls and exact generator/source/input/offline protocols remain open. |
| A-028 | Communication | D-94 game-wide Chat is not undoable. D-95 event comments are ordinary current-path nodes retained even after undo, without a special comment-only branch mechanism. Exact comment/group association, revision/edit/delete, and unsent-chat policies remain open; card notes/pings and other proposed communication tools stay distinct. |
| A-029 | Correction | Edit any supported tabletop field, add/remove mistaken objects, and bulk-correct state with a preview. |
| A-030 | Completion | Concede, record win/loss/draw or agreed result, reopen via undo, start rematch retaining completed history. |
| A-031 | History controls | D-89–D-91: ordinary undos vanish from the visible tree; retain an undone path only through its last actual hidden-information exposure, including draw/move/face changes. Preserve internal recovery and the requested rollback state. Review/interrupt/switch/archive/restore/rename and draft recovery remain; exact cutoff display/dependency/redo protocols are open. |
| A-032 | Export | Export decks and a complete game/history archive with declared information exposure. Exact formats remain open. |
| A-033 | Table layout | Drag and snap individual cards or selected sets, form/split/reorder visual piles, and recover previous placements. |
| A-034 | Combat projections/assignment | D-116 separates area creation/declaration/tap; D-117 adds multiselect combinations/auto-area; D-118 assigned/unassigned blockers and spacing. Creation preserves source placement, but D-123 routes later card edits/moves to the real card. D-122 removes combat projections/links without changing originals. Exact reference lifecycle/combined boundaries remain open. |
| A-035 | Stack projections | D-113 permits battlefield permanents to virtually occupy Stack to signal activated abilities, without moving originals. Distinct from Clone/token creation. Exact entry/multiplicity/source-update controls remain open. |
| A-036 | Contextual Stack actions | D-114 requires instant/sorcery card → Graveyard, permanent card → Battlefield, projection → remove projection. Exact category/face/default handling, destination routing, and controls need case-by-case design; no automatic resolution. |

D-121 applies across the action catalog: targeted combined shortcuts and their
constituent actions must also be reachable through a full action path. Quick
visibility by phase/step or selection mode is not exclusive action access;
structural/applicability validation remains. Exact menu paths are stage 3.

D-123 applies normal card actions through projections to the real source card,
not a local copy. This includes moves into Graveyard/other destinations with
normal resets and token-removal exceptions. Projection/area lifecycle removal is
distinct from source-card Remove; exact entry remains under review. D-124 removes
combat projections when the real card leaves the battlefield, regardless of
entry point. D-125 retains Stack effects until explicit removal/resolution,
including when their source moves or ceases to exist. A real-card move through a
Stack projection does not itself remove that effect. D-126 keeps surviving
blockers unassigned at their existing positions when their attacker disappears;
area shrinkage after blocker removal must not shift/compact remaining blockers.
Missing-source Stack presentation/actions remain under review in the primary
projection contract; no automatic effect execution is introduced.

D-127 adds advance out of combat + close combat area as an explicit combined
shortcut, with a full non-quick path and standalone advance/close still available.
Ordinary Next step, direct phase changes, and End turn leave the area open.
Closure uses D-122 without source edits or Stack-effect cleanup. D-128 offers the
quick action only at End of combat and advances to Postcombat main. Full non-quick
access remains; out-of-combat/missing-area handling and combined boundaries still
need design. Ordinary Next step at End of combat does not close the area.

D-133: repeated declaration reuses existing attacker projections rather than
duplicating them; add only new attackers from the selected sources. Declare + tap
still taps its selected sources, while without-tapping preserves orientation.
Other mixed/empty-source cases, no-change records, attacker/blocker coexistence,
and Stack multiplicity remain under review.

D-129 distinguishes undo units from tree events. D-131 sets the general rule:
one Undo reverses one user action and its whole group, including a combined
shortcut, unless an exception is approved. Where MTG permits reactions at distinct
points, preserve those as multiple events/interruption boundaries within that
user action; do not require more user invocations or undo taps. Specify the event
mapping per action without runtime priority/legality enforcement. D-130 confirms
whole undo for advance-and-close, restoring prior step and complete combat area;
D-132 records that combination as one event because area closure adds no distinct
MTG reaction point. Other combinations use the default undo rule but still need
their event mappings, not an undecided undo scope.

D-53 clarifies A-018's corner-text editing: store one plain string and offer
quick actions according to patterns matched in its current text. Actions rewrite
the string rather than editing a P/T or loyalty entity. Numeric counter text uses
the same text-first principle. D-62 confirms whole-number +/-1, corner
slash-pair left/right/both +/-1, and Set-only for other text (including decimals
and symbolic/mixed pairs). Exact matching/format preservation and limits remain
open; negative values are not zero-clamped.

D-63 confirms blank corner text as a valid explicit value and Reset corner text
as a separate action restoring the database default without changing counters
or other state. Corner +/- and completed Set retain/return to the context menu.
Exact Reset menu lifetime remains open.

D-59 applies edge trimming to all completed user-text edits before validation,
empty-value handling, and pattern matching. Preserve internal spaces/newlines.
For counters, whitespace-only text therefore removes the counter while ` 0 `
becomes visible `0`; do not extend deletion-on-empty to unrelated fields.

Pings, actual object edits, and ephemeral presence remain distinct; see
[the board contract](10-board-and-controls.md) and
[literal presentation](11-visual-action-language.md). Stack replaces the former
Cast label. Grouping a move there with pings does not classify it as casting.
Explicit projection/assignment/quick-action capabilities (A-034–A-036) are now
required; their [primary contract](13-combat-stack-and-projections.md) must be
specified case by case rather than excluded by the earlier generic-only wording.

Confirmed touch movement for A-005/A-006/A-033 is specified in
[board controls, section 10.4](10-board-and-controls.md#104-dragging-and-snapping):
single-card drag unless multiple cards are selected, top insertion on special
pile drops, ordinary-card drops forming/joining visual stacks, and Context Move
to the top/bottom of library, graveyard, and exile. Multi-card order and the
remaining bulk-action details below are not yet approved; D-49 separately fixes
relative order for multi-card moves from one ordered source.

Library UI decisions D-41–D-43 add combined contents/actions on pile tap,
order-independent full-library browsing, and explicit View top N / Reveal top N
commands with context actions on revealed cards. D-46 confirms the temporary
shared reference-display model, explicit-dismissal lifetime, and Draw N shortcut in
[special-pile controls](10-board-and-controls.md#106-special-piles-library-graveyard-exile).
Draw N's user-selected preset label denotes literal top-N
movement to a configured Hand destination, not an inferred game mechanic.

D-44/D-45 clarify that View and Reveal are both shared logged operations, not
local-only inspection: View presents faces to its actor and backs to the other
participant; Reveal presents faces to both. Top-N displays preserve source
order, unlike the orderless full-library browser. These are literal display
operations, not inferred game mechanics; exact history/display schemas remain
open in Q-28.

D-48 confirms quick Draw one, View/Reveal bottom N, and Move top/bottom N to an
explicit destination. The library offers Shuffle all only: no Shuffle top N or
Shuffle bottom N shortcuts. D-49 confirms top-to-bottom bottom-N displays, an
N-picker starting at one with +/- and numeric entry, clamping to available count,
and source-order preservation for moves from one ordered source. Empty sources,
cross-source order, precise validation, and bulk boundaries remain open.

D-50 confirms direct top-card dragging from special piles and contents browsers
with type-category columns, name search/filtering, and hold inspection. Column
grouping is display-only and does not replace explicit ordered top/bottom-N
displays. D-51 approves initial one-category-per-card, alphabetical-column, and
narrow-screen scroll defaults as a revisable starting point. Exact category
mapping and covered opponent layout remain open.

These IDs retain coverage references from the earlier draft; game-specific
operation proposals have been replaced by generic capabilities. Worked MTG
examples must demonstrate that these primitives are sufficient, not add hidden
mechanic-specific entities to the engine.

## 4.3 Rules that must not be inferred

- No casting, activation, targeting, combat, damage, cost payment, priority,
  resolution, or turn-progression model is inferred from physical actions.
- D-112–D-114 allow explicit combat associations/projection roles and contextual
  Stack choices. These do not infer rule effects from ordinary moves or card
  text. The earlier no-specialized-state language does not exclude them.
- D-96/D-105 separately require explicit engine-level turn/phase/step tracking.
  Do not interpret the no-inference rule as excluding that state. Exact explicit
  remaining transitions and correction behavior remain under review. D-105
  removes priority tracking altogether; players judge interruption timing.
- A ping only creates a visual communication record; it never creates a legal
  target or another rule-specific relationship.
- Moving normally preserves orientation, counters, corner text, labels,
  participant associations, and generic links. D-55/D-56 add declared counter
  removal and restoration of database-default corner text on entry to configured
  Graveyard/Exile/Library. For creatures the default is printed P/T as a string;
  D-64/D-65 settle conditional other defaults, blank fallback, and retained
  per-face defaults. Exact source/custom mapping remains open. Unrelated fields and
  identity persist for retained objects. D-84/D-85 separately remove tokens moved
  into those destinations, retaining historical identity/fields for recovery.
- Removing a piece, clearing a value, or rotating selected cards is always an
  explicitly selected generic edit, not an automatic consequence of card text.
- Game-specific card types, “token” labels, or area names do not trigger expiry,
  destruction, cleanup, or validation. D-84 is an explicit exception using
  instance token status and configured destinations, not labels or printed type.
- Any later convenience composition must name all generic changes explicitly.
   User-defined labels cannot make it an inferred engine mechanic. Whether such
   saved compositions are first-release scope remains open.

D-55/D-56 specify an approved destination-entry move/reset convenience, not
blanket approval of user-authored compositions or rules-driven cleanup. Record
its actual counter/text changes for review and recovery, not just a move label.

## 4.4 Hidden information conventions

Hands, library order, and face-down cards are covered by default in ordinary
table presentation. Either participant can explicitly inspect them. Show a clear
trust-model explanation during setup and on first hidden-zone inspection.

Covering a card does not remove its identity from synchronized data. No tooltip,
notification, default log summary, or background preview should unnecessarily
spoil a covered card. Explicit inspection can expose it.

Explicit View/Reveal is confirmed shared/logged (D-44). D-89 includes covered-
face hold inspection in exposure history; ordinary already-visible-face holds
are not included. D-90 retains information-gaining undos as history branches.
Any actual hidden-information exposure qualifies under D-91, including draw/
move/face changes. Retain an undone path only through its last information gain.
Exact coalescing, event placement, cutoff display, and redo remain Q-31.
Inspection itself cannot be made epistemically reversible.

## 4.5 Bulk actions and selections

D-66 confirms control parity: a mass selection gets the same card-action
controls as an individual card, applying the chosen action across the selected
set rather than a separate reduced bulk menu. Operate on each card's own state
for relative edits/default Reset and supply common text for Set. Normal
unrelated-field guarantees and declared move/reset compositions still apply.

D-67 rejects the prior all-compatible gate: apply the chosen action where it is
usable, skip selected targets where its individual action would be disabled,
and disable the mass control only if none are applicable. This does not authorize
arithmetic on nonnumeric text, loss of selected objects, or ignoring invalid IDs
and structural drop conflicts. D-68 resolves absent counters: +/-1 creates
`1`/`-1`; nonempty Set creates/updates and empty removes or leaves absent.
Exact per-action eligibility/error tables and affected/skipped rendering remain Q-30;
interruption/grouping boundaries remain open.

Multi-select operations identify affected objects and all explicit changed fields.
Atomic bulk movement is permitted when intermediate states are not intended
response points. Users can choose sequential actions when the distinction
matters, such as separate top-card moves with a reaction between them.

Quantity-based N-card commands clamp to the available source count (D-49).
Quantity clamping itself does not permit substituting or dropping selected
objects; D-67 separately permits skipping targets on which the chosen action is
inapplicable. Empty candidate lists/sources, duplicate selections, stale objects,
and destination conflicts still require explicit outcomes. The precise
per-action error table, including count feedback and boundary validation, is a
required follow-up specification artifact.

## 4.6 Required per-action specification template

Before implementation, every row above must be expanded into:

1. Entry points and affected screens.
2. Inputs, defaults, validation, and confirmation/cancellation behavior.
3. Atomic operations and exact state transition.
4. Ordering, visibility, and exactly which generic fields change or stay intact.
5. Review rendering and valid interruption boundaries.
6. Undo/redo and historical rollback behavior.
7. Offline behavior, preconditions, concurrency conflicts, and errors.
8. Accessibility interaction and acceptance examples.
9. Tap/action budget from the normal starting context, including selection,
   navigation, text entry, and confirmation. Justify each required interaction;
   prefer direct controls and undo over repeated prompts.

The generic correction tool must not excuse poor direct workflows for moving,
rotating, managing piles/counters, or adding pings. Manual MTG scenarios remain
coverage tests of those workflows, not engine-defined mechanics.
