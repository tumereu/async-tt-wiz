# 2. Domain model

Status: proposed logical model. Exact wire types and physical schema remain
specification work; do not treat this as a finished database definition.

## 2.1 Separation of state

Maintain three distinct state classes:

1. **Board state:** cards/objects, labeled areas/containers, participants, named
   values, orientation, and generic visual relationships at an action boundary.
2. **Shared history/control state:** immutable action nodes, branches, the active
   branch pointer, archive status, publication records, and undo records.
3. **Personal presentation state:** review cursor, selected objects, camera,
   locally exposed card faces, accessibility settings, and draft workspace.

Reviewing an earlier boundary does not mutate the active board. A branch switch
does not destroy a player's review context or private draft. A different camera
position is not a new game-history action.

“Draft” describes recoverable pending/failed work or incomplete form input, not
an editing mode that must be entered and sent. Completed game actions
automatically enter the durable synchronization queue.

## 2.2 Logical entities

| Entity | Identity and essential data |
| --- | --- |
| Game | Stable ID, participants, setup configuration, lifecycle state, active branch ID, control revision. |
| Participant | Stable ID, account reference, display name; neither seat owns exclusive editing rights. |
| Card definition | Source/catalog ID or custom definition, version, names, faces, display text/data, default corner text (D-56), image references; never executable rules. |
| Card instance | Stable game-local object ID, definition reference, optional participant associations, container, explicit editable state. |
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
| Ping | Stable ID, author, associated action ID, optional group ID, effective context boundary, referenced object/participant/area or board location, optional visual style/label, edit/delete revisions. |
| Generic link | Stable ID, object/participant endpoints, optional user label, visual style; no inferred game effect. |

Live cursor/activity presence is ephemeral transport data, not a persistent
game-state entity. Proposed placement, pile, and marker behavior is defined in
[the board contract](10-board-and-controls.md). Exact schemas remain open.

The [visual action language](11-visual-action-language.md) presents literal
operations and associated pings together. There are no cast/target declarations,
ability objects, rules-completion states, or inferred mechanical intent in the
engine. A custom piece can be labeled anything, but its label is just data.

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
- Optional generic links and editable descriptive attributes.

A move normally changes only specified container/position/layout fields and
preserves counters, corner text, orientation, notes, links, and participant
associations. D-55/D-56 supersede that blanket preservation for entry to configured
Graveyard/Exile/Library: remove card counters and restore the corner text's
database-provided default as declared generic changes accompanying the move.
Exact face/default selection and missing-default behavior remain open. Do not
infer other resets, change stable
identity, or derive behavior merely from a container's label or game rules.

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
  a small selectable color palette; exact colors remain open. Both cards and
  participants can have these counters. Counter text can be arbitrary; numeric
  text supports quick increment/decrement/set. Numeric recognition, zero/removal,
  layout, and exact editing controls remain open.
- Changing a counter never automatically changes corner text, printed card data,
  or other values. P/T-like text and counter colors have no mechanical meaning.

D-53 clarifies that corner text is one plain text field, not separate P/T,
loyalty, or typed numeric fields. Offer quick actions based on patterns matched
against its current text. A match such as `2/3` can expose pair-oriented text
adjustments without creating a P/T entity; invoking an action rewrites that same
string. Direct editing may change which actions are available. There is no
separately authoritative parsed value or persistent game-specific field type.
Exact patterns, parsing/format preservation, and the offered action set remain
to be specified. Counter text follows the same text-first principle for its
numeric conveniences.

D-56 requires the card database to provide a default corner string. For
creatures this is their printed P/T, formatted as text (for example `2/3`).
The tabletop edits that string generically; it need not know why a definition
supplies it. Reset restores the database default, not the last manually entered
text or a calculated value. Exact defaults for other card types, multi-faced
definitions, custom/missing data, and historical version selection remain open.

## 2.4 Areas, containers, and ordering

Generic configuration supports free-grid areas, designated slots, ordered piles,
and hand-like rows/fans. Display labels and ordering/cover settings are separate
fields. Renaming a pile to Library, Graveyard, Exile, Cast, or Stack cannot change
engine behavior.

The initial MTG-oriented layout can include those labels, two participant play
areas, hand rows, and optional Command/extra areas. A different layout uses the
same primitives; a general-purpose layout editor's first-release scope remains
open. The engine must not require MTG-specific enums to load or replay a board.

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
[movement contract](10-board-and-controls.md#104-dragging-and-snapping).
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
automatically split; see [the primary pile contract](10-board-and-controls.md#105-ordinary-battlefield-piles).

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
Players may label them Life, Mana, Turn, Phase, Priority, Commander damage, or
anything else. They remain ordinary manually edited values, not a turn state
machine, payment system, or role/permission system.

Changing one value does not clear another, advance a turn, move cards, or grant
consent. No game-specific effect follows from setting a marker or label.

D-52 confirms that participants, like cards, may have colored counters carrying
arbitrary text, with quick increment/decrement/set for numeric text. It does not
approve the earlier proposed always-visible +/- tracker layout or tap-number
editing gesture; exact player-counter controls remain to be reviewed.

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

Structural validation protects data integrity only. Negative named values,
unusual arrangements, self/cyclic visual links, and moves players consider
illegal in their game are not rejected merely on game-rule grounds.

## 2.7 Generic extension data

Support free-form text, named counters, custom cards/tokens, annotations, and
explicit state edits so unsupported or newly printed mechanics do not block
play. Such edits still have typed, reversible changes and a readable history
summary. “Edit anything” is not permission to submit arbitrary executable code
or overwrite an unvalidated database blob.
