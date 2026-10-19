# 4. Game action catalog

Status: generic engine boundary confirmed; detailed coverage and per-action
schemas proposed. MTG examples are uses of primitives, not recognized mechanics.

**Initial-delivery scope (D-182):** making game actions and shared controls
requires a connection, including new random actions and Undo. Downloaded history
is readable offline, but there is no offline action creation/queue replay. Online
optimistic updates and animation remain. D-187 applies ordinary actions at the
current head unless impossible; optimistic guards are limited to movement and
Stack additions, not previous-value checks for ordinary fields.

## 4.1 Shared action envelope

Every game action specifies:

- Stable action ID and parent boundary; actor and originating client operation.
- One literal mutation kind and optional user label/note (D-176).
- Typed reversible change, stable referenced object IDs, structural preconditions.
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
| A-001 | Create game | Invite an added user; recipient has Accept/Decline in Play, with Accept opening the same lobby for them (D-195). For the inviter, it is already an open lobby with deck preparation/Ready while waiting; Cancel game cancels the pending invite, while Decline retains the lobby for reinvitation (D-208). Play lists current games/pending invites. Lobby has deck selection/mainboard/sideboard management; both players press Ready to start (D-189/D-190). Remaining invitation lifecycle/setup details are open. |
| A-002 | Card-list import and saved decks | Import-only card-list editing (D-189). D-198 requires common MTG text with printing/sideboard notation, flagged unresolved names, preserved input and no saving until resolved. D-199 adds naming/renaming, cover art from a deck's cards and Replace/Save as new re-import. D-221 removes deleted saved decks from the collection and confirms both editing/deleting preserve already selected lobby/game copies. Full manual card-list editing/export are not implied. Remaining dialect/printing/count/metadata and deletion confirmation/recovery/retention/concurrent races are open; lobby mainboard/sideboard management is separate. |
| A-003 | Instantiate objects and lobby preparation | Initial lobby placement: mainboard → Library, sideboard → separate Sideboard; all other card areas empty (D-202). Freely transfer cards between mainboard/sideboard without legality/size/one-for-one restrictions (D-203); own Ready clears and saved decks stay unchanged (D-191). Start uses the prepared mainboard; Sideboard is unavailable during play. Further custom instantiation/setup remains proposed where not separately confirmed. |
| A-004 | Setup edits and mulligan shortcuts | Lobby starter selection offers either player or Random (D-201); changing it clears only the changing player's Ready, preserving the other's (D-204). Start shuffles Libraries and initializes life 20 without a required starting-life editor (D-200). Further initial setup remains proposed where not confirmed. No automatic draw (D-192). D-193/D-194 add return-whole-hand → shuffle → draw N (default 7) or pre-return hand size minus one, via Library context and turn-1 Untap quick actions. Sequencing/count edges remain open; ordinary in-game life/move/draw/shuffle controls remain. |
| A-005 | Move cards | Move one/many objects between containers/areas; choose insertion order only for ordered destinations, not Graveyard/Exile (D-226). No rule-dependent side effects. |
| A-006 | Ordered-pile operations | Select top/bottom/N, move to an explicit destination, inspect/reveal, search, reorder, shuffle the whole pile, and choose randomly. D-48 excludes library top/bottom shuffle shortcuts. D-226 excludes all order-related actions for unordered Graveyard/Exile; ordinary browsing and specific-card moves remain. |
| A-007 | Row/fan operations | View, arrange, cover/uncover, select, and move cards; labels such as Hand do not alter semantics. |
| A-008 | Orientation/faces | Tap/untap presentation, rotate, choose face, cover/uncover, and edit labels. D-100/D-110 make Untap all literal for the turn player's battlefield, with no skip flag/inferred exclusions; manually re-tap exceptions. Both standalone/+advance variants preserve unrelated fields. |
| A-009 | Participant association | Manually assign/edit labeled participant associations; moving a card does not infer ownership/control changes. |
| A-010 | Counters | Colored text-bearing counters on cards/participants; per-color +1/-1/Set, no Add/Initialize. D-58–D-68 fix palette/uniqueness, zero/empty/trim, inline retained menus, applicability skipping, and absent +/- creation as `1`/`-1`. No recolor; exact hit regions/formatting remain open. |
| A-011 | Numerical values | Adjust/set arbitrary named values. Life, Mana, Energy, and similar names are uninterpreted labels. |
| A-012 | Labeled-area movement | Move cards to/from areas such as Stack (renamed from Cast, D-113); record actual origin/destination, not inferred casting/payment/resolution. D-112–D-114 separately add explicitly chosen projections and contextual Stack actions. |
| A-013 | Labeled pieces | Create/duplicate a generic note, marker, or piece with optional text/icon; players can use these for abilities/triggers without engine recognition. |
| A-014 | Pile order/edit | Reorder, duplicate or move selected objects in any pile. Remove is token-only under D-174; non-token cards remain in the game. No built-in stack-resolution command. |
| A-015 | Group/event-scoped pings | D-74–D-80 confirm event-scoped mode/toggles, retained review, selection-preserving switching, consumed ordinary taps, and one standard marker. Context Ping/Unping uses the menu-trigger card's state and applies uniformly to its selected-set scope without mode changes. Location matching, routing/menu lifetime, and revisions remain open. |
| A-016 | Custom objects/copies | D-81–D-86 confirm single automatically placed catalog tokens (including one Blank entry), token-producing Clone with copied fields but no links/pings, and removal. No quantity/preview/Repeat shortcut or dedicated custom-token form. Exact placement/initialization, association fields, broader authoring, and boundaries remain open. |
| A-017 | Generic visual links | D-70–D-73 confirm binary undirected unique pairs, context picking, fan-out/removal, and independent movement. Repeated Link preserves pairs; no labels/styles or separate initial Attach/follow tool. Joint movement uses explicit multi-selection. |
| A-018 | Descriptive attributes | Override instance labels, text, faces, and named attributes without interpreting game meaning. D-52/D-53 add plain corner text with pattern shortcuts; D-69 adds multiline card notes with short on-card text, optional long-text ellipsis, full inspection, and empty-text removal. |
| A-019 | Board markers/connectors | D-119 adds event-wide ping order numbers. D-120 supports links between projections that disappear with endpoints. D-112/D-118 separately require explicit assigned/unassigned blocker projections and adaptive combat spacing; no inferred legality/damage from pings/links. |
| A-020 | Manual value changes | Edit one/many explicitly selected values; no damage calculation or automatic distribution. |
| A-021 | Manual object changes | D-84/D-85 remove tokens moved into configured Library/Graveyard/Exile across all move controls; mixed sets move ordinary cards normally. D-174 restricts context Remove to tokens: non-token cards never disappear during play and have no Remove/delete action. Token Remove has no pile move/routine confirmation, with automatic sync and undo/history. No other inferred token expiry or rules effects. Exact mixed-selection routing/references/boundaries remain open. |
| A-022 | Turn/phase/step tracking | D-96–D-103 require MTG tracking/shortcuts and turn context. D-106–D-108 add one-tap End turn/no number editor and starter/turn-1 setup. D-105 removes priority; D-109 leaves extra turns manual/approximately counted. D-111 advances combined shortcuts despite no untap changes or empty Library, with no inferred loss. Exact controls/schema/boundaries remain open. History/undo/branches/online-only sync apply (D-182); arbitrary markers remain separate. |
| A-023 | Named tracking sets | Add arbitrary per-object/per-participant named values/labels; sufficient for manual commander-style tracking without that mechanic in the engine. |
| A-024 | Custom status | Generic notes/icons/named trackers represent unusual or future mechanics; the engine does not recognize their labels. |
| A-025 | Faces and visual composites | Show/select faces, overlap/group pieces, preserve component identities; no automatic meld/merge/phase/transform rules. |
| A-026 | Additional containers | Extra-container configuration/access remains proposed where not separately confirmed. D-202 excludes in-game Sideboard browsing, editing or moves; generic extra-container paths must not expose the lobby sideboard. |
| A-027 | Randomness | D-87/D-92/D-93 confirm one-die Roll, Flip coin, fresh draws for new die/coin/Shuffle invocations (including after undo), and auto-synced stored results/orders reused for review/redo/retries. No opponent-action seed reset. D-182 requires a connection for new random actions, not downloaded replay. Other random controls and exact generator/source/input/online protocols remain open. |
| A-028 | Communication | D-94 game-wide Chat is not undoable. D-95 event comments are ordinary current-path nodes retained even after undo, without a special comment-only branch mechanism. D-172 groups comments with preceding same-author play, subject to D-170. Historical association, revision/edit/delete and unsent-chat policies remain open; card notes/pings and other proposed communication tools stay distinct. |
| A-029 | Correction | Edit supported tabletop fields and bulk-correct state with a preview. D-174 forbids non-token card deletion even as a correction; token removal and existing move/Undo controls remain available. Broader authoring/correction scope remains proposed. |
| A-030 | Suggest ending / accept | Suggest your win/loss or a draw as shared, undoable board state. The other player must accept, then both return to lobby (D-211 superseding D-210). Acceptance is not undoable (D-212), unlike pending suggestions. Restore last lobby-prepared decks/mainboard/sideboard, clear both Ready and require both to ready again for a fresh game (D-213). No win-history/scorekeeping feature is required. Finished-session action-history review is optional future work, not R1 (D-214); current-game history is unchanged. Competing/replaced/stale suggestions and cross-game starter-selection retention/random resolution remain open. |
| A-031 | History controls | D-224 preserves every later relevant-state (from, to) tuple within hard N on removal/reinsertion; revealing targets are excluded. Undo targets only the latest still-applied own action, even after opponent activity; Redo is last-undone-first; neither skips a blocked target; only own new play clears your queue (D-227–D-231). D-232 Undo/Redo edit the existing tree without branches or an untouched original alternative. Per-mutation state, N, pointers, device scope/anchors, affected references/side branches, retention after queue clearing and concurrency remain open. Live D-187 acceptance is unchanged. D-222 graphical activation/personal review and D-223 filtering apply to actual existing branches. |
| A-032 | Export | Export decks and a complete game/history archive with declared information exposure. Exact formats remain open. |
| A-033 | Table layout | Drag and snap individual cards or selected sets, form/split/reorder visual piles, and recover previous placements. |
| A-034 | Combat projections/grouping | D-116 separates area creation/declaration/tap; D-117 adds multiselect combinations/auto-area. D-137/D-139 refine earlier assignment wording to visual paired-slot groups with no individual attacker↔blocker edges. Creation preserves source placement; D-123 routes later card edits/moves to the real card. D-122 removes combat projections/links without changing originals. Exact reference/combined-event handling remains open. |
| A-035 | Stack projections | D-113 permits battlefield permanents to virtually occupy Stack to signal activated abilities, without moving originals. D-134 allows unlimited independent projections, including several from one source, each removed/resolved separately. Distinct from Clone/token creation; card fields remain shared (D-123). Exact entry/order and remaining reference behavior stay open. |
| A-036 | Contextual Stack actions | D-114 requires instant/sorcery card → Graveyard, permanent card → Battlefield, projection → remove projection. Exact category/face/default handling, destination routing, and controls need case-by-case design; no automatic resolution. |

D-121 applies across the action catalog: targeted combined shortcuts and their
constituent actions must also be reachable through a full action path. Quick
visibility by phase/step or selection mode is not exclusive action access;
structural/applicability validation remains. Exact menu paths are stage 3.

D-123 applies normal card actions through projections to the real source card,
not a local copy. This includes moves into Graveyard/other destinations with
normal resets and token-removal exceptions. Projection/area lifecycle removal is
distinct from token-source Remove; non-token cards have no Remove action (D-174).
Exact token/occurrence entry remains under review. D-124 removes
combat projections when the real card leaves the battlefield, regardless of
entry point. D-125 retains Stack effects until explicit removal/resolution,
including when their source moves or a token source ceases to exist. A real-card move through a
Stack projection does not itself remove that effect. D-126 keeps surviving
blockers in their existing slot when an attacker disappears; D-139 retains the
remaining group, with a blocker-only pair when its final attacker leaves;
D-138 permits visual reflow while preserving surviving slot membership/order,
replacing the earlier literal fixed-position requirement.
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
Other mixed/empty-source cases and no-change records remain under review. D-135
limits each source to one projection per combat area across attacker/blocker
roles: dragging an attacker to a blocker position moves the existing occurrence,
not a copy or the real source. D-136/D-139 keep the other cards in their slots,
grouped with any remaining attackers, without source edits. Other entry/role-change cases remain open.
D-137 requires paired attacker/blocker slots supporting several projections on
each side and visual resizing as cards are added/removed. D-139 makes each pair
one visual group without individual attacker↔blocker assignments; players can use
placement/pings for finer details. D-138 removes a pair immediately only when both sides
are empty, closing the gap; between-pair drops insert a new pair with the moved
cards, retaining other pairs' contents/order. Fully empty origins disappear too;
an area with no pairs stays open. D-142 settles uniform target-slot routing for
mixed-role selections rather than preserving prior roles.
D-140 confirms real battlefield-card drag entry into either combat side: create
the source's projection there or retarget its existing occurrence, preserving
identity and the original's position/location/orientation. This drag does not
tap/untap or move the actual source into combat. D-141 sends all selected real
battlefield cards to the same target slot and side, creating/reusing per source,
not one pair each. D-142 extends this to combat projections and mixed real/combat
projection selections: all go into the chosen slot side-by-side with its role,
never split by earlier roles or overlapped into an ordinary pile. Source state
and occurrence uniqueness remain unchanged. D-143 inserts on between-card drops
inside a slot and appends on unused-space drops, preserving other cards' relative
order. Mixed-view ordering, other-view applicability, and absent-area entry remain open;
repeated declaration still follows D-133 rather than drag retargeting.
D-144 adds Remove from combat as a context action for one or several selected
combat projections, leaving real cards/Stack effects unchanged. Remove incident
links and fully empty pairs normally, preserving remaining grouping/order and the
open area. D-176 issues individual occurrence-removal mutations; each has its
own parent-step Undo and retained slot/link/cleanup context;
token-source Remove and real-source/mixed-selection access remain separate cases;
no non-token source deletion is available (D-174).
D-152 handles declarations of existing blockers: move each existing occurrence
into its own new attacker pair, preserving identity/links rather than duplicating
it. Apply the chosen tapping variant, retain other origin grouping, and remove
only fully empty origins. Existing attackers remain in their slots; each variant
uses individual mutation nodes under D-176, superseding the older combined mapping.
D-153 appends new declaration pairs after surviving existing pairs. D-154 keeps
the pre-drag order of cards moved together from one combat slot, inserting/
appending that sequence under D-143, including same-slot reorders. Preserve
other cards' order, occurrence identity, source state, and existing Undo scope;
D-156 orders cross-slot combat drags by selected blockers left-to-right, then
selected attackers left-to-right, retaining each slot's order. Insert/append the
sequence together; all take the destination role. D-157 orders real battlefield
cards dragged together into combat by pre-drag visual order, top-to-bottom then
left-to-right, not selection-tap order. Create/reuse occurrences in that sequence
without changing originals. Mixed-view, multiple-new-pair, and irregular/
overlapping battlefield layout ordering remain separate cases.
D-158 clarifies the baseline: combat is a separate area between the play areas.
Battlefield drags create/reuse projections where dropped, leaving originals in
place. Declaration quick actions put new attackers to the right of existing
combat cards/pairs; existing attackers stay put. Internal collection-order
defaults never override the drop target. No projections-first mixed-drag rule
was approved; that proposal is withdrawn.
D-145 gives each newly declared attacker its own pair for declaration shortcuts,
keeping existing attackers' slots/grouping/order. Collection drags still share
one target slot (D-142). D-146 auto-creates the combat area for single-card
declaration if absent, without implicit tapping or source movement; reuse an
existing area. D-147 shows both multiselect declaration quick actions only during
Declare attackers; full context-action access remains at other steps (D-121).
Order among multiple new pairs, absent-area drag targeting, and additional single-card quick
offerings remain open; do not infer legality or editing locks from quick visibility.
D-176 supersedes D-148/D-149/D-150's combined-node mappings: area creation,
individual occurrence changes, individual source taps/untaps and tracking edits
are distinct mutations, even from one shortcut. Each mutation immediately extends
or branches the selected path, publishes, and supplies one parent-step Undo.
Draw + advance retains separate actual move and tracking mutations; an empty
Library contributes no draw mutation. No extra invocation tap/Send is needed.
Empty/no-change sources still advance under D-111 without fabricated card changes.
See history sections 3.11–3.12 for mutation sequences and grouped playback.
D-134 separately allows unlimited independent Stack
projections, including repeated effects from one source; removing one leaves the
others and the source unchanged.

D-129 distinguishes invocations, tree events and presentation groups. D-159/
D-160 replace whole-invocation Undo with one parent-node move regardless of author;
composites may take several presses. D-176 fixes individual mutation nodes rather
than D-131's MTG-reaction-window criterion; no runtime timing/legality enforcement
or extra invocation taps are introduced.
Presentation grouping cannot change Undo stepping. D-176 supersedes D-132's
combined advance-and-close node: its distinct mutations remain individually
recorded. Specify their consistent sequence without choosing atomic bulk nodes.

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
[the board contract](board-and-controls.md) and
[literal presentation](visual-action-language.md). Stack replaces the former
Cast label. Grouping a move there with pings does not classify it as casting.
Explicit projection/assignment/quick-action capabilities (A-034–A-036) are now
required; their [primary contract](combat-stack-and-projections.md) must be
specified case by case rather than excluded by the earlier generic-only wording.

Confirmed touch movement for A-005/A-006/A-033 is specified in
[board controls, section 10.4](board-and-controls.md#104-dragging-and-snapping):
single-card drag unless multiple cards are selected, top insertion on Library
drops, unordered membership additions for Graveyard/Exile, ordinary-card drops
forming/joining visual stacks, and Context Move to Library top/bottom, Graveyard,
or Exile. D-226 excludes order-related Graveyard/Exile actions, including visual
placement variants. Multi-card order and the
remaining bulk-action details below are not yet approved; D-49 separately fixes
relative order for multi-card moves from one ordered source.

Library UI decisions D-41–D-43 add combined contents/actions on pile tap,
order-independent full-library browsing, and explicit View top N / Reveal top N
commands with context actions on revealed cards. D-46 confirms the temporary
shared reference-display model, explicit-dismissal lifetime, and Draw N shortcut in
[special-pile controls](board-and-controls.md#106-special-piles-library-graveyard-exile).
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
cross-source order and precise validation remain open. D-176 fixes individual
card-mutation nodes for bulk moves.

D-50/D-226 support direct top-card dragging from Library and specific-visible-card
dragging from unordered Graveyard/Exile. Contents browsers have type-category
columns, name search/filtering, and hold inspection. Column
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
move/face changes. D-160 keeps the whole abandoned continuation visible when it
contains exposure or an event comment, without a last-exposure cutoff. D-222 also
keeps mixed-player continuations visible and limits hiding to single-player/
no-exposure paths, while D-223 confirms event comments remain a visibility exception. Exact
coalescing, event placement, forward presentation and non-Undo branch controls
remain Q-31.
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
grouping and remaining per-action validation details remain open; D-176 fixes
individual mutation boundaries.

**Confirmed D-176:** a bulk control issues individual operations in a sensible
recorded order. Moving/tapping several cards, applying edits, creating Clones,
or removing occurrences does not create one multi-target atomic node. Each
individual mutation immediately becomes a tree leaf and publishes. All
intermediate mutation boundaries remain available for traversal, Undo and play.
Grouping may animate those changes concurrently as one playback step (D-177),
but never merges their nodes or makes a group the client's state pointer.

Use approved source-order and spatial defaults without another ordering picker.
Record the chosen sequence so forward/backward traversal is deterministic.
Existing applicability skipping, destination order, and whole-drop structural
validation remain: an overflowing drop can be rejected before creating any
mutations without making a valid bulk move one atomic history operation.

Quantity-based N-card commands clamp to the available source count (D-49).
Quantity clamping itself does not permit substituting or dropping selected
objects; D-67 separately permits skipping targets on which the chosen action is
inapplicable. Empty candidate lists/sources, duplicate selections, stale objects,
and destination conflicts still require explicit outcomes. The precise
per-action error table, including count feedback and boundary validation, is a
required follow-up specification artifact.

## 4.6 Required per-action specification template

The [limited-locking policy](optimistic-locking.md) records D-187's replacement
for the former 25-rule review: ordinary actions normally apply to current state;
only movement and Stack additions have optimistic conflict guards. Changed old
orientation/text/counter values are not rejection reasons for ordinary edits,
including Set. The former batch's detailed category rules were not approved.

Before implementation, every row above must be expanded into:

1. Entry points and affected screens.
2. Inputs, defaults, validation, and confirmation/cancellation behavior.
3. Atomic operations and exact state transition.
4. Ordering, visibility, and exactly which generic fields change or stay intact.
5. Review rendering and valid interruption boundaries.
6. D-232 tree-editing Undo/Redo eligibility/effects, affected historical references,
   and separate explicit-branch/control recovery.
7. Offline behavior, preconditions, concurrency conflicts, and errors.
8. Accessibility interaction and acceptance examples.
9. Tap/action budget from the normal starting context, including selection,
   navigation, text entry, and confirmation. Justify each required interaction;
   prefer direct controls and undo over repeated prompts.

The generic correction tool must not excuse poor direct workflows for moving,
rotating, managing piles/counters, or adding pings. Manual MTG scenarios remain
coverage tests of those workflows, not engine-defined mechanics.
