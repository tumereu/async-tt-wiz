# 2. Domain model

Status: proposed logical model. Exact wire types and physical schema remain
specification work; do not treat this as a finished database definition.

## 2.1 Separation of state

Maintain three distinct state classes:

1. **Board state:** cards/objects, labeled areas/containers, participants, named
   values, orientation, generic visual relationships, and first-class turn/
   phase/step tracking (D-96/D-105) at an action boundary. No priority state.
2. **Shared history/control state:** immutable action nodes, branches, the active
   active game-state node pointer, archive status, publication/control records,
   and retained descendants (including hidden alternatives).
3. **Personal presentation state:** review cursor, selected objects, camera,
   locally exposed card faces, accessibility settings, and draft workspace.

Reviewing an earlier boundary does not mutate the active board. A branch switch
does not destroy a player's review context or private draft. A different camera
position is not a new game-history action.

“Draft” describes recoverable pending/failed work or incomplete form input, not
an editing mode that must be entered and sent. Completed game actions
automatically enter the durable synchronization queue.

Confirmed user-text normalization (D-59): trim leading and trailing whitespace
from all user-entered text when an edit is completed, including corner/counter
text, labels, names, notes, and messages. Preserve internal whitespace and line
breaks; this is edge trimming, not collapsing or rewriting the content. Apply
trimming before empty-value validation and text-pattern matching, and record
the resulting text. Unfinished input is not a completed edit. Empty counter
text removes the counter; emptiness behavior for other fields is field-specific,
not automatic deletion everywhere. This rule does not silently rewrite existing
history or authorize normalization of external catalog/import data.

## 2.2 Logical entities

| Entity | Identity and essential data |
| --- | --- |
| Game | Stable ID, participants, setup configuration, lifecycle state, active game-state node ID and path/branch context, control revision. The active node need not be a retained branch tip (D-159). |
| Participant | Stable ID, account reference, display name; neither seat owns exclusive editing rights. |
| Card definition | Source/catalog ID or custom definition, version, names, faces, display text/data, default corner text (D-56), image references; never executable rules. |
| Card instance | Stable game-local object ID, definition reference, optional participant associations, container, explicit editable state. |
| Projection | Live virtual occurrence of a source card, not a Clone/token or second containment. Creation preserves source placement; card edits/moves affect the real card (D-123). Source departure removes combat occurrences (D-124), not Stack effects, which require explicit removal/resolution (D-125). Exact identity/schema/missing-source behavior and link/ping routing remain open. |
| Combat slot pair | One visual combat group with attacker-side and blocker-side projection membership/order (D-137/D-139). No individual attacker↔blocker assignments or inferred all-to-all blocking rules. D-138 supplies empty-pair removal/insertion/reflow; exact schema remains open. |
| Other object | Stable ID, visual kind, labels, generic attributes; standees, tokens, notes, or other player-created pieces. |
| Area/container | Stable ID, label, generic layout/ordering/display settings, optional participant association. |
| Action node | Stable ID, parent ID, actor, literal operation kind, operations, deterministic outcome, schema version. |
| Group | Stable ID, ordered contiguous action IDs on one path, anchor action ID where applicable, literal/optional user title, actor, open/closed presentation status. |
| Branch | Stable ID, fork boundary, head, origin branch, creation reason (interruption/conflict/etc.), author, title, archive status. |
| Control operation | Stable ID, actor, ordered server revision, operation kind, prior/new references, undo linkage. |
| Random outcome | Stable ID, requested operation, concrete result, source/version metadata; retained with its action. |
| Review position | Participant, viewed branch/path, boundary, known active revision; not a promise of rules acceptance. |
| Draft | Local ID, base boundary/revision, pending/failed actions or incomplete input, grouping, queue dependencies, sync status. |
| Message | Stable ID, author, content, context boundary, edit/delete revisions. |
| Board placement | Object ID, logical area/grid position, optional visual pile ID and order; independent of camera/device pixels. |
| Visual pile | Stable ID, one zone/area, ordered member IDs, logical anchor; not a rules attachment, merged object, or action group. |
| Ping | Stable ID, author, owning group/event ID (D-75), source action/effective-boundary context, referenced destination, and event-wide sequence shown as an order number (D-119). One standard marker/no user label/style controls (D-77). Exact ordering/edit/revision/schema behavior remains open. |
| Generic link | Stable ID, supported endpoints, binary present/absent relationship; one standard connector/no user labels/styles (D-71). D-120 adds projection-to-projection endpoints whose links disappear with an endpoint projection. Other mixed projection endpoints remain open; no inferred game effect. |

D-129 distinguishes user invocation, tree event and presentation group. D-159/
D-160 replace whole-invocation Undo with one move to the active node's parent,
regardless of author. Multiple events in a composite require multiple presses;
presentation split/join never changes parent links. D-131's distinct-MTG-reaction-
point requirement remains, without runtime priority/legality inference. Retain
invocation/actor context across constituent nodes. D-132 makes advance-and-close
one event and consequently one Undo, restoring both effects together.

Live cursor/activity presence is ephemeral transport data, not a persistent
game-state entity. Proposed placement, pile, and marker behavior is defined in
[the board contract](board-and-controls.md). Exact schemas remain open.

D-92 makes every new die/coin invocation a fresh draw, even after undo, replacing
D-88's opponent-action seed reset and the pending gameplay draw-position model.
The same recorded operation is not resampled during review/redo or transport
retries. Retain concrete outcomes independently from board undo; fresh draws can
coincidentally match. D-93 applies the same fresh-new/fixed-recorded distinction
to Shuffle's concrete order. Exact generator/source, input validation, and offline
publication remain open; no gameplay seed-epoch schema is required by D-92/D-93.

D-159/D-160 retain every undone node. After replacement play, ordinary abandoned
continuations are hidden by a presentation filter and available through Show
hidden branches. A qualifying exposure or event comment keeps the whole abandoned
continuation visible, including trailing ordinary nodes; there is no last-exposure
cutoff. The active pointer is independent from retained tips and personal review.
Explicit activation of a retained node permits Redo with original outcomes; review
alone does not activate it. D-161 binds Undo to a specific node-to-parent transition;
if no longer valid, silently discard it and reconcile local state, including after
offline queuing. Exact pre-fork/forward presentation, non-Undo branch controls and
protocol/schema details remain Q-16/Q-31.

D-89/D-91 exposure coverage remains: library View/Reveal, covered hand/card-face
views including covered-face holds, die/coin results, and any actual hidden-info
exposure including draws/moves/face changes. Covered summaries do not automatically
disclose identities. Exposure node placement/coalescing, schema, durable capture
and publication remain Q-31; no separate permanent-marker entity is required.

D-94/D-95 distinguish non-undoable game-wide chat from event-linked comment
nodes on the ordinary tree path. The current path is already a branch; no
comment-only fork entity/activation mechanism is required. Undo retains that
whole abandoned continuation visibly if it contains an event comment (D-160).
Exact message/
comment schemas, group association, revisions/edit/delete, and unsent-delivery
policies remain open; do not give all Message records one shared Undo policy.

The [visual action language](visual-action-language.md) presents literal
operations and associated pings together. No cast/target legality objects,
rules-completion states, or mechanical intent are inferred. D-112–D-120 do permit
explicit combat declarations/assignments and ability-signaling projections, not
an automatic rules engine. A custom piece's label remains just data.

D-70/D-71 confirm persistent generic links between a source card and a picked
card/player endpoint, presented with one standard connector and no user labels
or style selection in the initial release. The relationship is binary: linked
or not. Referenced endpoints track object identity/position; moving one card
changes the line's display endpoint, not the other card's placement or values.
Links remain distinct
from pings, ordinary stacks, and game-specific attachment/target entities.
D-71 confirms one link per selected source to the picked endpoint and removal
through a card's Links list, without affecting card state. D-72 makes endpoint
pairs undirected and unique: A-B is B-A. Repeated Link preserves an existing
pair, while explicit removal unlinks; selection Link creates only missing pairs.
Exact no-op history, rendering, self-link presentation, and concurrent uniqueness
enforcement remain open; no label/style metadata is required initially.

D-120 additionally supports links between projection endpoints. Such links
disappear when an endpoint projection disappears, not silently retarget to the
real source cards. Preserve recorded history/undo/branch recovery. Exact cleanup
composition, concurrent endpoint removal, and mixed endpoint kinds remain open;
see [projection behavior](combat-stack-and-projections.md#132-confirmed-combat-action-model).

D-119 requires event-wide ping order numbers, distinct from generic link state.
No damage-order rules entity is implied; numbering mutations and group-edit
behavior remain under review in the primary ping contract.

D-73 excludes a separate initial attachment/follow-group model. Binary links
do not imply parent/child placement or neighbor movement; move multiple cards
together through explicit selection. No anchor/follower entity is required in
the initial schema merely to represent aura/equipment-like relationships.

An authenticated account and a game participant are separate concepts. Account
creation, guest support, and invite credentials are unresolved decisions.

## 2.3 Card and object state

Every card instance has:

- A stable identity across moves. Explicitly creating/duplicating an object gets
  a new ID; moving it between containers does not create a rules incarnation.
- One container/location, with an explicit position when ordered.
- Optional manually assigned participant associations and arbitrary labeled values.
- Face/side, covered/uncovered presentation, orientation/tapped presentation,
  counters, optional bottom-right corner text, and notes.
- Explicit per-instance token status for catalog-token creation and Clone output
  (D-84), independent of printed card types, catalog labels, or user text.
- Optional generic links and editable descriptive attributes.

D-81/D-82 confirm catalog-token creation as one independent instance with a fresh
stable ID, automatically placed; no quantity or placement-preview model is
needed. Additional instances can be created through a Clone card context action.
D-83 copies current face/cover/orientation, corner text, counters, notes, and
retained defaults, without links/pings; selection-scoped Clone creates one new
instance per selected card. D-84 marks every Clone output as a token, including
when its source is an ordinary card. Source state is unchanged. Repeat last
token is omitted. Exact association-field copying, catalog-instance initialization,
automatic location, and retained/offline definition contracts remain open.

D-86 adds one Blank token definition to the normal catalog instead of a separate
custom-token authoring model/form. It creates the same token instance kind;
existing corner-text/counter/note edits customize it after creation. Exact blank
presentation/initialization remains open, without approving custom-template or
image-authoring entities.

D-84/D-85 explicitly remove token instances moved into configured Library,
Graveyard, or Exile from current state, rather than retaining them in the pile.
This is declared destination behavior, not card-text/type/label-driven inference.
Retain historical identity and prior fields for undo/recovery; exact reference
handling and record composition remain open. D-85 applies across move controls:
mixed selections move ordinary cards normally and remove tokens. It also confirms
context Remove of ordinary cards/tokens, including selections, without a pile
move or routine confirmation. Completed removals auto-queue/sync and retain
undo/history. No broader token expiry rule is inferred.

A move normally changes only specified container/position/layout fields and
preserves counters, corner text, orientation, notes, links, and participant
associations. D-84/D-85 token-removing moves are a separate exception; preservation
below applies to objects retained in current state. D-55/D-56 supersede that
blanket preservation for entry to configured
Graveyard/Exile/Library: remove card counters and restore the corner text's
database-provided default as declared generic changes accompanying the move.
Exact face/default selection and missing-default behavior remain open. Do not
infer other resets, change stable
identity, or derive behavior merely from a container's label or game rules.

Pings belong to one group/event and display only in that context (D-75); advancing
to a later event hides overlays without deleting records. Reviewing the original
step in the event tree restores its applicable pings. This is communication
context, not a persistent binary link or a rule-dependent lifetime.
D-76 makes destination taps toggle ping presence rather than add duplicates.
Changing events exits local Ping input mode; historical display does not
automatically re-enable it. Exact location matching and revision schema remain
open, without discarding recoverable earlier ping states.
Historical pings store their action/boundary context. Moving or deleting a
referenced card does not retarget a ping to a different object; show its original
context when the object is absent from the viewed state. The engine does not
decide whether a relationship is still valid in a game's rules.

Catalog data is descriptive. Editing an instance's label or displayed values
does not change its source definition or other instances.

Confirmed card text/counter distinction (D-52):

- A card may have arbitrary text displayed in its bottom-right corner. Players
  can use it for P/T, loyalty, or any other text. Context actions provide quick
  increment/decrement/set conveniences; exact handling of a number versus text
  such as `2/3` remains to be specified. This is editable instance presentation,
  not a typed power/toughness or loyalty rules model.
- Counters are separate colored markers, each with its own attached text. Offer
  red, blue, green, yellow, purple, and gray (D-58); exact styling remains open.
  Both cards and participants can have these counters, at most one per color
  on each target. Counter text can be arbitrary; numeric text supports quick
  increment/decrement/set. Numeric recognition, layout, and exact controls
  remain open.
- Changing a counter never automatically changes corner text, printed card data,
  or other values. P/T-like text and counter colors have no mechanical meaning.

D-58 fixes counter presence semantics: numeric text `0` remains visible; a
completed edit to empty text removes the counter. Temporary incomplete editor
input is not itself a completed shared edit. D-59 trims text first, so a
whitespace-only completed counter value removes it. A separate Remove shortcut
remains open. Color uniqueness is per target, not global: different targets may
each have their own red counter.

D-67 supersedes D-60's separate color-choice/initialization interaction: each
color has +1, -1, and Set actions, without Add/Initialize. Arithmetic on existing
whole-number text changes it relatively; nonnumeric arithmetic is inapplicable.
D-68 makes absent-counter arithmetic applicable: +1 creates text `1`, -1 creates
text `-1`. Set creates/updates for nonempty trimmed text, removes existing
counters for empty text, and leaves absence unchanged for empty text. Absence is
zero only as an arithmetic starting point, not a visible intermediate counter.
No recolor action is needed, and target/color uniqueness remains.

D-67 also fixes selection applicability: evaluate the chosen action per target,
skip targets on which it would be unusable, and enable the mass action if at
least one selected target is applicable. Disable only if none are applicable.
This is explicit applicability skipping, not permission to silently drop invalid
IDs, lose edits, or bypass structural drop-validation guarantees. Exact affected/
skipped presentation and action/error schemas remain open.

D-53 clarifies that corner text is one plain text field, not separate P/T,
loyalty, or typed numeric fields. Offer quick actions based on patterns matched
against its current text. A match such as `2/3` can expose pair-oriented text
adjustments without creating a P/T entity; invoking an action rewrites that same
string. Direct editing may change which actions are available. There is no
separately authoritative parsed value or persistent game-specific field type.
D-62 confirms initial quick actions: signed whole-number corner/counter strings
get +/-1 without zero-clamping; corner slash-pairs of signed whole numbers get
left/right/both +/-1. Other text, including decimals and symbolic/mixed pairs,
has Set only. Counter text follows the same text-first principle. Exact
matching/format preservation and numeric limits remain open.

D-56 requires the card database to provide a default corner string. For
creatures this is their printed P/T, formatted as text (for example `2/3`).
The tabletop edits that string generically; it need not know why a definition
supplies it. Reset restores the database default, not the last manually entered
text or a calculated value. D-64 conditionally approves other defaults when
supplied by the eventual source. D-65 fixes blank fallback, retained per-game
defaults, and per-face Reset selection. Exact source/version mapping and custom
definition authoring remain open.

D-63 distinguishes blank from default corner text: completing an empty trimmed
Set leaves a blank corner overlay. Reset corner text explicitly restores the
database default without editing counters or other state. Neither operation
changes printed art; no typed P/T interpretation is needed.

D-69 confirms optional card notes/annotations, separate from corner text,
counters, and action-linked communication. Preserve the complete trimmed note;
short notes render directly on the card and long notes may be ellipsized for
display, with full text always available in inspection/editor. Empty completed
text removes the note. Moves retaining the instance, including ordinary-card
Graveyard/Exile/Library entry, preserve notes under the unrelated-field contract.
D-84 token removal retains prior notes in recoverable history, not a live card
in the destination. Reminder text has no game behavior.

Conditionally approved database defaults (D-64):

1. Planeswalkers: printed starting loyalty as default corner text, if supplied.
2. Battles: printed defense as default corner text, if supplied.
3. Cards without an applicable printed value: empty default corner text.

These extend D-56's creature P/T mapping using available descriptive database/
import metadata, not tabletop state types or rules. No source is selected and
no colored counters are created automatically. A card genuinely having no
applicable printed value is distinct from unavailable/incomplete provider data;
D-65 uses a blank editable fallback for the latter. Overlapping-type/default
mapping, custom definition authoring, and exact source-version selection remain
open. Source feasibility is recorded in [catalog research](../04-technical/architecture-and-operations.md#73-card-catalog-and-decks).

Confirmed missing/offline/multi-face defaults (D-65):

1. If default metadata is unavailable, use blank editable corner text rather
   than block manual play or guess the missing printed value.
2. Retain the resolved default with the game's card definition so offline Reset
   works and later provider updates do not silently change existing defaults.
3. For multi-faced cards, retain per-face defaults; Reset uses the selected
   printed face's default. Face switching alone preserves the current corner
   string under the unchanged-field contract rather than implicitly resetting it.

## 2.4 Areas, containers, and ordering

Generic configuration supports free-grid areas, designated slots, ordered piles,
and hand-like rows/fans. Display labels and ordering/cover settings are separate
fields. Renaming a pile to Library, Graveyard, Exile, Stack, or another label cannot change
engine behavior.

The initial MTG-oriented layout can include those labels, two participant play
areas, hand rows, and optional Command/extra areas. A different layout uses the
same primitives; a general-purpose layout editor's first-release scope remains
open. The engine must not require MTG-specific enums to load or replay a board.

D-112/D-113 require a temporary combat area and the shared Stack (renamed from
Cast), supporting projections whose creation preserves actual battlefield
placement. Later card actions through a projection edit/move the real card
(D-123). A virtual occurrence does not violate single actual
containment or create a Clone/token. Explicit blocker associations and Stack
occurrence kinds are permitted manual-play data, not inferred card rules.
D-114 adds metadata/occurrence-dependent user-invoked Stack actions, not
automatic resolution. Exact removal/lifecycle, identities, occurrence-versus-card
operations, and history/offline/concurrency behavior still need design;
see [the primary contract](combat-stack-and-projections.md).

D-123 settles that source and projections share live card state: no independent
projection corner text, counters, notes, faces/cover, or orientation. Card actions
through either view operate on the real source. A projection dragged to a real
destination moves that source with ordinary validation/reset/token-removal
behavior. Duplicate-source selection, card-Remove versus occurrence-Remove entry,
and link/ping endpoint routing remain separate behavior questions.

D-122 removes the combat area and its projections/projection links without
changing source cards' current state. It neither untaps attackers nor rolls back
prior edits made through projections. D-127 requires explicit closure, with an
advance-out-of-combat + close shortcut; ordinary phase/turn changes do not close
the area. Exact cleanup/history/concurrency composition remains open.

D-124/D-125 distinguish occurrence lifetime by role. Any removal of a real card
from the battlefield removes its combat projections and their incident links,
regardless of the operation's entry point. The same source departure/removal
leaves Stack effect occurrences intact until explicit removal/resolution, even
if the source was moved through that Stack projection or a token ceased to
exist. A surviving effect need not have a currently existing actual source card;
missing-source presentation/inspection and card-action applicability need design.
Do not fabricate a replacement card. Exact cleanup/history/concurrency composition
also remains open.

D-126/D-139 retain surviving blockers in their existing slot when an attacker
leaves: remaining attackers keep the group together; the final attacker's
departure leaves a valid blocker-only pair. No individual assignment edges exist
or need cleanup. Do not move blockers into another collection/column.
D-138 replaces literal fixed
tabletop positions with stable slot membership/order: pairs may visually reflow
as they resize or other pairs are inserted/removed, without regrouping survivors.
Real source cards remain unchanged by assignment cleanup. Retain placement and
assignment context for normal undo/history/branch/offline recovery; exact schema
and area-bound geometry remain later design.

D-133 makes repeated attacker declaration reuse the existing attacker occurrence
for each already-declared source, preserving identity/placement/associations and
creating occurrences only for new attackers. D-135 separately sets at most one
occurrence per source per combat area across attacker/blocker roles. Dragging an
attacker to a blocker position moves/changes the role of that same occurrence,
preserving identity rather than creating a duplicate. Role/placement changes alone
leave the real source unchanged. Assignment cleanup and other role-change/entry
paths remain open, except D-136/D-139 retain the remaining group on an attacker's
role change, leaving a blocker-only origin if its final attacker leaves. This is a UX
constraint, not legality enforcement or a universal
one-projection-per-source constraint across all areas.
Normal selected-source tap/without-tapping behavior still applies; exact no-change
records remain open.

D-134 allows unlimited independent Stack occurrences, including several from the
same source, with no per-source or total projection-count cap. Repeated creation
adds another occurrence; explicit removal/resolution of one leaves its siblings
and source unchanged. Independence applies to occurrence identity/lifetime, not
card fields: all still reference the same live source state under D-123. Source
departure/removal leaves all those Stack effects intact under D-125. Duplicate-
source multiselection and missing-source behavior still require review.

D-137 organizes combat into paired opposing attacker/blocker slots, supporting
multiple projections on either side (and valid empty sides), with visual slot
growth/shrinkage as cards are added/removed. This is not a one-attacker-per-pair
invariant. D-139 makes each pair one visual group without individual attacker↔
blocker assignments; players can communicate finer details through placement and
optional event pings, not an inferred all-to-all rules relationship. D-138 removes
fully empty pairs immediately, closing gaps; pairs with either side occupied
remain. Between-pair drops insert a new pair with the moved projections, retaining
other pairs' contents and relative order. Empty origins follow the same removal
rule; an area with no pairs stays open (D-127). Layout alone does not edit sources
or transfer surviving cards between pairs. See the primary combat contract,
section 13.15, for pending within-slot order, edge-drop, and event/concurrency cases.

D-140: a real battlefield card dragged into an attacker/blocker slot creates its
combat occurrence there, or retargets its existing occurrence in that area while
preserving identity. Destination side determines role; the original stays in its
containing location/battlefield position with unchanged orientation. This does
not tap/untap, move the actual card into combat, or create a Clone. Other cards
retain grouped membership/order; fully empty origin pairs disappear (D-138/D-139).
Other-view applicability, absent-area entry, no-change records, and exact
event/concurrency composition remain open.

D-141 applies real battlefield-card drag entry to a selected set: all occurrences
join the same target slot and side, creating or reusing per source under D-140,
not one pair per source. Real source state remains unchanged; reused identities
and other cards' grouping are preserved, with fully empty origins removed normally.
D-142 extends the common target to combat-projection/mixed-role/mixed-view drags:
all go into the destination slot side-by-side, taking its role rather than keeping
old roles or splitting across paired sides. Single projection drags work in either
role direction. Preserve source state and occurrence uniqueness; this is not an
ordinary overlapping pile. D-143 inserts at a between-card drop position or appends
on an unused-space drop, preserving other cards' relative order. Ordering the moved
collection itself and other-view applicability remain open, as do duplicate-source
view handling and exact event/concurrency cases.

D-144 supplies a Remove from combat context action for one or several selected
combat occurrences. Remove only those projections/incident links, not real cards
or Stack effects. Preserve surviving slot membership/order; remove fully empty
pairs without closing the area. Whole-user-action Undo restores occurrences,
roles/slots/order/links and pairs removed by cleanup. Source-card Remove and
real-source/mixed-selection access remain distinct review cases.

D-145 gives each newly declared attacker its own pair when using declaration
shortcuts, preserving already-declared attackers' existing slots/grouping/order.
Do not apply this per-source default to drags into a chosen slot (D-142).
D-146 auto-creates an absent combat area for single-card declaration; reuse an
existing area without clearing/replacing it. Plain declaration preserves source
orientation/position/location. D-152 converts an existing blocker included in a
declaration shortcut by moving its same occurrence into its own new attacker pair.
Keep identity/incident links, normal variant tapping, and remaining origin grouping;
remove only fully empty origins. Order among multiple new pairs, absent-area drag targeting, and
exact concurrency remain open; D-148/D-149 fix one-event/one-Undo declaration mappings.

D-153 appends newly created declaration pairs after surviving existing pairs;
keep existing pairs' contents/order and already-declared attackers' slots.
D-154 preserves source-slot relative order within a collection dragged from one
combat slot, including a subset or same-slot reorder. Insert/append the sequence
under D-143 without copies/source edits. D-155 fixes blockers above attackers in
two rows; D-156 orders a cross-slot combat drag by selected blockers left-to-right,
then selected attackers left-to-right, preserving each slot's order. The entire
sequence takes the destination role. D-157 orders real battlefield cards dragged
together into combat by their pre-drag visual order, top-to-bottom then
left-to-right, not selection-tap order. Create/reuse occurrences in that sequence
without rearranging/tapping originals. Mixed-view order, order among multiple new
pairs, and irregular/overlapping battlefield layout ordering remain open.
D-158 fixes the spatial distinction: combat is a separate area between the two
play areas. Drags create/reuse projections at their drop target; declaration quick
actions place new attackers to the right of existing combat cards/pairs. Neither
placement mode moves the real sources; explicit +tap alone changes orientation.

Ordered moves name an insertion position; “top” and “bottom” do not depend on
screen orientation. Multi-card moves specify the moved cards' order. Display
sorting is distinct from changing actual container order.

Confirmed library-browser convention (D-42): the full contents view does not
expose actual library order. Its order-independent presentation does not alter
the underlying ordered container. In contrast, View/Reveal top N show source
order (D-45). Both View and Reveal are shared logged operations; View shows
faces to its initiating participant and backs to the other, while Reveal shows
faces to both (D-44). D-50 selects card-type category columns for contents
browsers; grouping is a metadata-based presentation, not game-rule behavior or
a stored container reorder. Explicit top/bottom-N displays remain source-ordered.
D-51 initially assigns each card to one main-type column (Other if unclassified)
and sorts by name independently of stored order. These presentation defaults may
be revised; exact category mapping and covered opponent-facing grouping remain
open. These are presentation conventions, not access control.

Confirmed initial-layout movement defaults: a drop onto a configured Library,
Graveyard, or Exile pile inserts at its logical top; Context Move offers top and
bottom for each. A drop onto an ordinary card forms/joins a visual pile, not a
mechanical attachment or merged identity. See the primary
[movement contract](board-and-controls.md#104-dragging-and-snapping).
D-49 preserves relative order for moves from one ordered source; cross-source
ordering remains open. Do not infer randomization or other ordering behavior
from the Cockatrice reference.

A staged search or selection is a draft workspace, not a second location for
the same card. Completing the selection through its action control creates one
atomic transition or an explicitly described sequence and immediately queues
synchronization. There is no second generic confirmation or Send step.
Cancelling incomplete input changes no shared game state.

The special Library/Graveyard/Exile slots represent configured pile containers.
An ordinary overlapping pile in a free-grid area is only a visual arrangement;
piling or unpiling does not change unrelated object values or links. Shared
placement is replayable/undoable; personal pan/zoom and sorting are not moves.

Confirmed ordinary-stack layout limit (D-39): at most three actual member cards.
This limit is not applied to the special ordered-container piles or to selection
size. A placement exceeding it is invalid rather than partially applied or
automatically split; see [the primary pile contract](board-and-controls.md#105-ordinary-battlefield-piles).

Confirmed View/Reveal reference model (D-46): a temporary shared display
references existing card IDs without becoming a second containing location or
creating duplicate card instances. An action on a displayed reference edits the
original object. The display persists until explicit dismissal. D-47 fixes its
shown card IDs/source order: do not auto-refill as the source changes. Moved
cards retain entries with updated location indicators. Either participant may
dismiss the display; log dismissal and preserve cards and historical records.
Do not implement a new zone or copy-on-reveal model. Exact source-context schema,
historical rendering/undo, deletion/removal behavior, and detailed controls remain
open.

## 2.5 Participant/table values and markers

Support arbitrary labeled numeric/text values and participant/board markers.
Players may label them Life, Mana, Commander damage, or anything else. These
arbitrary values remain distinct from D-96/D-105's first-class turn/phase/step
tracking, and are not a payment or role/permission system.

Changing one value does not clear another, advance a turn, move cards, or grant
consent. No game-specific effect follows from setting a marker or label.

D-96, narrowed by D-105, requires tracking for turns and MTG phases/steps. Store
that tracking as game state at each action boundary so that history, replay, undo,
branches, offline queuing, and synchronization handle it like other game edits.
Do not replace it with arbitrary labels or blank tokens. Exact fields, initial
values, remaining transition/correction commands, and action boundaries remain open.
Do not add priority-holder, pass-cycle, or no-holder/resolving tracking state.
The requirement alone does not approve a particular transition state machine or
infer changes from card movements, pings, grouping, or elapsed time.

D-97 fixes the phase vocabulary/order to MTG rather than a configurable phase
definition system: Beginning → Precombat main → Combat → Postcombat main →
Ending. No phase editor or alternate phase-sequence configuration is required.
Hard-coded phase tracking does not by itself implement phase-entry effects,
automatic untapping/drawing, priority cycles, or automatic progression.

D-98 includes individual MTG steps, not only phases. The standard sequence is:

| Phase | Steps in order |
| --- | --- |
| Beginning | Untap → Upkeep → Draw |
| Precombat main | No separate steps |
| Combat | Beginning of combat → Declare attackers → Declare blockers → Combat damage → End of combat |
| Postcombat main | No separate steps |
| Ending | End → Cleanup |

Track the phase and, where applicable, its current step at each action boundary.
Exact handling of skipped/repeated steps and extra phases/combat damage steps,
manual corrections, and initialization remains Q-32. Do not infer these from
card text or require a generic phase-configuration system to support tracking.

D-99 requires quick-action offerings to depend on the tracked phase/step. This
is a contextual presentation requirement, not automatic execution on step entry.
D-100 confirms Untap all for the turn player's battlefield during Untap, and
Draw one from their Library to Hand during Draw. Each has a standalone form
that preserves tracking and a one-press operation-plus-advance variant.
Record actual edits and any advancement under normal history/undo/queue/sync
contracts. D-101 advances to the next step, not the next whole phase: Untap →
Upkeep, and Draw → Precombat main at the phase boundary. Do not skip Upkeep/Draw
after Untap or execute another card operation merely on entering the next step.
Exact participant association, remaining no-op/failure presentation,
concurrency/partial-group behavior, and other shortcuts remain under review; no new
card-rule inference is implied.

D-110 makes Untap all literal for the turn player's battlefield cards, with no
dedicated skip-untap field/flag. Card text, notes, and markers do not install
exclusions. Players manually re-tap exceptions. Standalone and +advance variants
use the same scope and preserve unrelated card state. Exact participant
association, remaining no-op presentation, and concurrency/partial-group behavior
remain under review. D-150 settles the normal-step combined event boundary below.

D-111 confirms valid no-change/empty-source behavior for combined shortcuts:
Untap all + advance still goes to Upkeep when all scoped cards are already
untapped or there are none. Draw one + advance on an empty Library moves no
card but still goes to Precombat main, without an automatic loss or other rule
effect. Record the actual step transition; no card effect is fabricated. This
does not waive structural validation for missing IDs/corrupt state or change
standalone variants, which preserve tracking. D-150/D-151 fix event counts;
exact no-change feedback and concurrency/partial-group handling remain open.

D-150 records Untap all + advance during Untap as one event: actual scoped
untapping plus Untap → Upkeep. D-151 records Draw one + advance during Draw as
two events: actual draw portion while tracking remains Draw, then Draw →
Precombat main. The intermediate state is genuinely reviewable/interruptible.
Each node is one Undo under D-159/D-160: Untap + advance needs one press, Draw +
advance needs two. The first Draw Undo keeps the card drawn and restores Draw
tracking; the second returns to pre-draw state. Empty sources retain actual no-card-change
outcomes rather than fabricated draws; see history section 3.11 for exposure
retention and remaining partial-group/out-of-step/concurrency cases.

D-102 adds standalone Next step (tracking only, no card edits) and direct
phase/step choice through the indicator for skips/corrections, without executing
intermediate card operations. Both edit shared tracking under normal undo,
branch, queue, and sync contracts; neither is a history-review cursor change.
Direct phase/step choice does not itself settle turn/player corrections.
Exact action/group boundaries remain under review; priority is not tracked.

D-103 requires turn-player identity and turn number in shared tracking and its
presentation. Next step from Cleanup starts the other player's Beginning/Untap
and increments the turn number by one per individual player's turn, not per
round. It does not untap or draw cards automatically. History, replay, undo,
branches, offline queuing, and sync preserve this turn context. Reviewing a
boundary does not advance the turn. D-109 accepts an approximate counter when
players manually repeat steps for extra turns; no exact rules-turn count is required.

D-106 requires a quick End turn action available from any phase/step, using the
same next-turn transition: other player, turn number +1, Beginning/Untap. It does
not execute skipped step operations, untap/draw cards, or mark the game complete.
No manual turn-number editor is needed; numbering follows transitions, and undo/
replay/branch review restores the number at the relevant boundary normally.
D-107 keeps End turn visible beside Next step and invokes it in one tap without
routine confirmation, relying on Undo. Exact geometry and atomic/group boundaries
remain under review; D-109 omits dedicated extra-turn/player-count corrections.

D-108 chooses the starting player during setup and initializes their turn 1 at
Beginning/Untap, without automatically changing cards. This initializes shared
turn context and does not decide opening-hand/setup procedures. Exact setup
controls/defaults, change/recovery boundaries, and schema details remain open.

D-109 rejects a dedicated Start another turn action or scheduler. Players use
the existing phase/step picker manually; changing steps does not automatically
change turn player/number. The displayed counter may therefore be inaccurate
for rules-level extra turns, which is acceptable. No extra-turn count recovery,
automatic inference/reconciliation, or manual number editor is required. Normal
End turn/Cleanup progression retains its other-player/number +1 transition.

D-105 supersedes the priority part of D-96 and D-104: no engine priority tracking
or user-visible priority controls in this release. Participants decide legal MTG
timing themselves and respond by interrupting the recorded action path. Do not
validate interruption/edit eligibility against priority, derive it from phase,
or require a Pass record before queuing work or creating a branch. Existing
cooperative access and structural validation remain unchanged. Turn/phase/step
tracking and the approved shortcuts/progression controls remain in scope.

D-52 confirms that participants, like cards, may have colored counters carrying
arbitrary text, with quick increment/decrement/set for numeric text. It does not
approve the earlier proposed always-visible +/- tracker layout or tap-number
editing gesture. D-61/D-67/D-68 subsequently confirm player-panel access,
inline +1/-1/Set, menu retention, and absent-counter outcomes. Exact geometry
and formatting remain open.

## 2.6 Invariants

| ID | Invariant |
| --- | --- |
| D-001 | Each live board piece belongs to one container/location or a documented non-contained visual-object category. |
| D-002 | Object IDs, action IDs, and operation IDs are never reused for unrelated entities. |
| D-003 | Action parent links form an acyclic rooted tree. An action has one parent. |
| D-004 | Exactly one non-archived branch is active for an existing game. |
| D-005 | A branch's head descends from its fork boundary. |
| D-006 | Every group contains an ordered contiguous segment on one action path; groups do not overlap. |
| D-007 | Applying the same root state and stored action path yields identical game state. |
| D-008 | An atomic action either applies fully or does not apply. |
| D-009 | Ordered containers contain a well-defined, duplicate-free object sequence. |
| D-010 | Invalid IDs and malformed container/pile structures are rejected or explicitly repaired, never silently dropped. Generic visual links are not constrained by game-specific relationship rules. |
| D-011 | Either participant can issue the same tabletop editing operations. |
| D-012 | Hidden presentation never implies access-control protection between participants. |
| D-013 | Only explicitly requested generic fields change; labels, card text, pings, and area membership cannot trigger inferred game mechanics. |
| D-014 | Each card/participant has at most one counter of each supported color. Numeric `0` is present counter text; completed empty text removes the counter (D-58). |
| D-015 | At most one live undirected binary link exists per endpoint pair; A-B and B-A identify the same relationship (D-72). |

Structural validation protects data integrity only. Negative named values,
unusual arrangements, self/cyclic visual links, and moves players consider
illegal in their game are not rejected merely on game-rule grounds.

## 2.7 Generic extension data

Support free-form text, named counters, custom cards/tokens, annotations, and
explicit state edits so unsupported or newly printed mechanics do not block
play. Such edits still have typed, reversible changes and a readable history
summary. “Edit anything” is not permission to submit arbitrary executable code
or overwrite an unvalidated database blob.
