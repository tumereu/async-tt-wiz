# 12. Step-by-step touch action review

Status: delivery stage 1, behavior specification (D-115). This worklist retains
earlier touch decisions as requirements/history, but current new decisions focus
on behavior. Adversarial review/refinement is stage 2, UI drafts/concrete controls
stage 3, and iterative implementation stage 4+. No implementation is authorized.

This is a review worklist, not a second interaction contract. Approved decisions
must be reflected in the relevant primary document, decision register, and
acceptance scenarios. Cockatrice capabilities are references, not automatic
first-release requirements. Unanswered proposals below remain unapproved.

Review style (D-33): batch straightforward likely-yes choices into small related
numbered sets, allowing approval together or exceptions by number. Do not
fragment every routine default into a separate conversational turn.

Review continuity (D-57): end design updates with the next contextualized
question/batch rather than waiting for “next?”. Commit and push at natural
checkpoints while keeping proposed versus approved behavior explicit. This does
not change the documentation-only implementation gate.

## 12.1 Reference and review sequence

Reference: Cockatrice client source snapshot
[`b849a57`](https://github.com/Cockatrice/Cockatrice/tree/b849a57dab584b184e804527c2333e0688b9e590),
checked 18 October 2026, especially the in-game player menus, player actions,
card/arrow gesture handlers, zone controls, and token dialogs. The source-backed
inventory was presented in conversation. This worklist splits that inventory
into smaller touch-design decisions; it does not claim feature parity.

Proposed sequence; groups can be split or reordered during review:

| Group | Scope | Review status |
| --- | --- | --- |
| G-01 | Move one card: reposition, cross-area move, ordinary pile entry/exit, menu alternative. | Core choices confirmed in D-21–D-24; detailed lifecycle/hit testing remain open. |
| G-02 | Select multiple cards and apply bulk movement. | Core selection choices recorded in D-25–D-31; detailed gesture/cancellation and bulk movement contracts remain open. |
| G-03 | Tap/untap and bulk orientation. | D-35/D-36 confirm selection-aware tapping; D-100/D-110 add literal turn-player battlefield Untap all without skip flags or inferred exclusions. Players re-tap exceptions manually; exact boundaries remain open. |
| G-04 | Inspect, face-down/up, printed faces, and reveal presentation. | D-37/D-38 confirm context face changes and automatic face/both-face inspection; exact targeting/layout and logging remain open. |
| G-05 | Ordinary piles: capacity, browse, reorder, split, and whole-pile controls. | D-39/D-40 confirm the cap, overflow, no browser/Select stack, and ordinary drag controls; geometric details remain open. |
| G-06 | Ordered containers: browse, top/bottom/N-card moves, insertion, search, shuffle, and redraw shortcuts. | D-41–D-51 confirm displays, shortcuts, clamping/order, direct top-card drag, search/filtering, and initial column/sort/scroll defaults; category mapping and exact controls remain under review. |
| G-07 | Card/player named values, counters, annotations, and bulk edits. | D-52–D-69 confirm corner/counter/note capabilities and selection applicability; exact geometry, formatting, limits, and history/bulk boundaries remain open. |
| G-08 | Attachments/relationships, visual links, and action-associated pings. | D-70–D-80 confirm binary links/no Attach tool and event-scoped pings including mode/context controls; the menu-trigger card determines Ping/Unping for the selection. Detailed geometry, menu lifetime, revisions, association, and grouping remain later review. |
| G-09 | Create, duplicate, remove, and repeat creation of cards/tokens/pieces. | D-81–D-86 confirm token creation/Clone/removal and one Blank catalog token instead of a custom form. Exact placement, initialization, references, and history boundaries remain later review; no wider custom authoring approved. |
| G-10 | Other table tools: dice/coins, turn/phase/step tracking, markers, messages, and optional convenience calculations. | D-87/D-89–D-111 confirm existing tools/tracking/shortcuts. No priority or extra-turn action. No-further-phase-shortcuts proposal rejected; required combat/Stack work continues in G-11–G-13 under D-112–D-115. |
| G-11 | Temporary combat area, attacker/blocker projections, and per-attacker blocker assignment. | Required by D-112; current behavior review starts with source tapping when adding an attacker. Entry gestures/layout wait for stage 3. |
| G-12 | Stack naming, real cards versus activated-ability projections, and source/reference behavior. | D-113 renames Cast to Stack and requires virtual permanent occurrences without moving originals. Exact behavior cases remain open. |
| G-13 | Contextual Stack quick actions for real cards and projections. | D-114 requires instant/sorcery → Graveyard, permanent → Battlefield, projection → remove projection. Category/routing/lifecycle cases remain open; no automatic resolution. |

Detailed action grouping, interruption boundaries, and playback follow capability
and touch-control review. Existing confirmed history/sync requirements remain in
force; this sequencing does not approve any pending grouping proposal.

## 12.2 G-01: Move one card

Primary contract: [board and controls, sections 10.4–10.6](10-board-and-controls.md#104-dragging-and-snapping).

Already confirmed:

- Drag a card to move it; drops snap to a grid location or designated slot.
- Either participant can move either participant's cards.
- Moves are literal, preserve unrelated state, automatically queue/sync, and
   support undo. Labels such as Stack do not introduce mechanical side effects.
- Double tap opens the card's available actions.

Decisions from this review (D-21–D-24):

| Capability | Control | Status |
| --- | --- | --- |
| Reposition one card or move it between areas. | Direct drag with a visible destination preview; no selection mode, hold prerequisite, or routine confirmation. | Drag/snap confirmed; detailed lifecycle proposed in section 10.4. |
| Add one card to an ordinary visual pile. | Drop onto another card to form/join its visual stack; not an attachment. | Confirmed; exact hit regions/highlighting remain open. |
| Take one card out of an ordinary pile. | Drag one card unless multiple cards have been selected. | Confirmed drag scope; D-40 removes the ordinary-stack browser and uses normal selection/drag for whole-stack movement. Exact hit geometry remains open. |
| Drop onto library, graveyard, or exile. | Insert on top of that configured pile. | Confirmed; D-49 fixes order from one ordered source. Other multi-card order remains open. |
| Move without dragging. | Context Move in the double-tap sheet offers at least top/bottom of library, graveyard, and exile. | Confirmed destination set; picker layout and further destinations remain open. |

The owner explicitly allowed dragging multiple selected cards. That does not
approve a dedicated whole-pile handle, any selection gesture, bulk ordering, or
atomic/sequential grouping. These remain separate decisions.

## 12.3 G-02: Select multiple cards

Primary contracts: [board controls](10-board-and-controls.md) and
[bulk actions](04-game-actions.md#45-bulk-actions-and-selections).

Confirmed starting point: multiple selected cards can be dragged. A selection
itself is local presentation, not a shared game edit.

The owner's latest suggestion distinguishes direct rectangle selection from a
separate mode for adding/removing individual cards. Two-finger dragging handles
panning in either case. This supersedes the assistant's earlier interpretation
that all rectangle selection would require entering multi-select mode.

Current mapping (P-16), with core selection choices confirmed through D-35 and
remaining details still under review:

1. Drag one finger from empty table space to draw a rectangle; release to select
   cards. No mode-entry tap is required. Confirmed: the new rectangle replaces
   the existing selection rather than adding to it. Selection neither moves nor
   rotates cards.
2. Use two-finger dragging to pan, whether or not selection-edit mode is active.
   Pinch zoom is confirmed alongside it by D-34.
3. Enter a separate selection-edit mode to tap individual cards into/out of the
   selection, allowing disconnected sets or holes within a rectangle. Normal
   card taps outside that mode retain tap/untap behavior.
4. Drag a selected card to move the selected set. Show selection highlighting,
   selected count, and selection-edit mode clearly.
5. Confirmed (D-31): a visible Select toggle enters/exits selection editing;
   switching it off preserves the selected set and restores normal tap/untap.
   Empty-space tap clears selection independently (D-30). Mode exit never undoes
   completed moves or acts as a Send gate. A separate Clear button is not required
   by these decisions.

This combines a no-mode quick rectangle with precise arbitrary-set editing.
Back-button behavior, accessible alternatives, and exact gesture timing remain
to be specified. D-34 confirms hold/double-tap retain Inspect/Actions inside
selection-edit mode without selection changes or constituent single-tap edits.

Confirmed follow-up (D-25): a new rectangle replaces the existing selection;
individual adjustments use selection-edit mode. Rectangle replacement does not
depend on whether selection-edit mode is active.

Confirmed inclusion (D-26): any overlap selects a card, including a small
partially intersected portion; full enclosure is not required.

Confirmed visual-stack selection (D-27): select only members whose visible
portions overlap the rectangle. Do not select an entire pile implicitly or use
obscured footprints. An exposed underlying edge can qualify; a completely
obscured card cannot. Visible covered backs do not expose identity.

Confirmed unselected-card drag (D-28): clear the old selection and move only the
dragged card. Do not move the old set or add the dragged card to it. D-34 confirms
cancelled/invalid drags restore the pre-drag selection and placement.

Confirmed post-move selection (D-29): after successfully moving a selected set,
keep it selected until explicitly cleared or replaced. Completing the move does
not itself clear selection or require a selection-exit/Send step.

Confirmed clearing (D-30): tap empty table space to clear selection, leaving the
current normal/selection-edit mode unchanged. An empty-space drag remains
rectangle selection, not clearing by a tap. Neither changes shared card state.

Confirmed mode control (D-31): use a visible Select toggle. Turning it off keeps
the selection and restores normal card-tap behavior. Clearing selection remains
the separate empty-space tap, not an automatic consequence of toggling the mode.

Follow-up details remain open: exact visible-card geometry, second-finger
gesture arbitration, select-all entry points, covered/overlapping cards,
cross-area selections, multi-card ordering, and bulk action boundaries.

## 12.4 G-03: Tap/untap one card or a selected set

Primary contract: [board gestures and ordinary piles](10-board-and-controls.md).
Single tap for tapped presentation is already confirmed; taps within
selection-edit mode instead add/remove a card from selection.

Confirmed choices (D-35, correcting D-32):

- Outside selection-edit mode, tapping a selected card retains selection and
  taps/untaps all selected cards. Tapping an unselected card clears selection and
  taps/untaps only that card. Pile membership alone does not expand the affected
  set. A tap with no selection affects only the addressed card.
- Tapped presentation is 90 degrees clockwise from untapped, or back to untapped.
- Inside selection-edit mode, card taps add/remove selection membership. The
  earlier approved explicit Tap selected / Untap selected commands remain for
  that mode and set a group's presentation uniformly. The proposal to make those
  commands available outside the mode was rejected; use card taps there.

Confirmed mixed-orientation behavior (D-36): follow the touched card's pre-tap
presentation. Touch an untapped selected card to set the whole set tapped;
touch a tapped selected card to set the whole set untapped. Do not toggle each
member independently. Keep selection and unrelated fields intact.

Later D-100/D-110 confirm turn-player battlefield Untap all with no skip-untap
flag; exceptions are manually re-tapped, never inferred from card text. Other
wider-set/all-table shortcuts, rotation controls, and bulk boundaries remain open.

## 12.5 Grouped defaults: selection and gesture consistency

The owner requested batching likely-yes questions (D-33) and named an exception
to item 5. Items 1–4 are recorded as accepted (D-34); item 5 is rejected and
replaced by D-35. Primary behavior is described
in [board controls, section 10.3](10-board-and-controls.md#103-gesture-arbitration).

1. Pinch zoom alongside two-finger pan, in either mode.
2. Hold/double tap retain Inspect/Actions in selection-edit mode without changing
   selection or emitting single-tap edits.
3. Moving selected cards does not switch selection-edit mode on or off.
4. Cancelled/invalid drags restore placement and pre-drag selection, including
   the old set cleared by picking up an unselected card.
5. Rejected: Tap selected / Untap selected available with the mode off. Instead,
   normal card taps operate on the selection if the touched card is selected;
   otherwise they clear selection and tap/untap only the touched card (D-35).

Remaining meaningful tradeoffs (not approved by this batch) include cross-area
selections, selected-set movement order/placement, bulk interruption boundaries,
and optional whole-table shortcuts. D-110 rejects dedicated skip-untap flags.

## 12.6 G-04: Inspect, face-down/up, and printed faces

Already confirmed: hold for large art/text; either participant can inspect all
information. Covered presentation is not a permission boundary.

Confirmed in D-37/D-38; primary contract is
[board controls](10-board-and-controls.md#inspection-face-down-presentation-and-printed-faces):

1. Context Turn face down / Turn face up for one card or an explicit selection.
2. Context Switch printed face for cards with multiple playable faces, distinct
   from turning face down/up and sideways tapping. No inferred game meaning.
3. Hold-to-inspect automatically shows art/text even when the card is face down;
   multi-faced cards automatically show both printed faces. No extra reveal or
   face-switch step is required. This is the existing inspector referred to as
   hover-inspect in review, not a new hardware-hover requirement.
4. Face/cover edits preserve unrelated fields and selection. Inspection also
   preserves shared face/cover state, orientation, placement, and selection.

D-89 later confirms covered-face inspection logging. Exact trigger/coalescing
(Q-31), face/selection command targeting, bulk boundaries, and special-pile
reveal conventions remain separate questions.

## 12.7 G-05: Ordinary visual-stack controls

Confirmed capacity and overflow (D-39, formerly P-17):

- Maximum three actual member cards per ordinary visual stack, rather than a
  three-card display fan hiding an arbitrarily large stack.
- Special Library/Graveyard/Exile containers remain uncapped by this layout
  limit. Multi-selection may include more than three cards across the table.
- Confirmed overflow policy: show an invalid destination preview for
  a drop exceeding capacity and restore pre-drag placement/selection on release.
  No automatic splitting, displacement, or partial movement of a selected set.

The owner referenced Cockatrice's three-card stacks. The checked source uses
three grid positions per ordinary stack in table-zone mapping; attached-card
arrangements are separate. This is design reference, not a requirement for our
generic engine to inherit Cockatrice's implementation details.

Confirmed compact controls (D-40):

1. No separate ordinary-stack browser. Use visible portions and ordinary
   gestures; precise exposure/zoom/hit geometry still needs specification.
2. No Select stack action; use rectangle selection and, if needed, selection-edit
   mode to refine membership. Move selected cards with ordinary drags.
3. Bring an individual member to the top by an ordinary drag onto its current
   stack; preserve other members' relative order and unrelated fields.
4. Split an individual member out with an ordinary drag to an empty grid cell.
   Existing selected-set drag scope still applies.

The earlier proposed browser, browser reorder handles, and Select stack action
are superseded. P-13's dedicated stack handle proposal is withdrawn; no such
extra control is specified. Special-container browsers are unaffected.

## 12.8 G-06: Special-pile browsing and direct controls

Primary contract is
[special-pile controls](10-board-and-controls.md#106-special-piles-library-graveyard-exile):

Confirmed in the owner's revisions (D-41–D-46):

1. A pile tap opens contents and pile context actions together.
2. Full-library browsing does not show library order and does not reorder it.
   This replaces the assistant's proposed default actual-order view for Library.
3. Library commands include View top N and Reveal top N. Shown/revealed cards
   support hold inspection and context actions such as move to library bottom.
4. Both View and Reveal are shared/logged. View shows faces to the participant
   initiating it and backs to the other; Reveal shows faces to both. The earlier
   local-only View proposal was rejected. This is not opponent access control.
5. View/Reveal top N preserve actual source order, including the backs shown to
   the other participant for View. Full-library viewing remains orderless.

Confirmed temporary-display and shortcut choices (D-46, formerly P-18):

- Both commands use shared temporary displays with the confirmed face/back
  presentation. Show references to the actual cards rather than
  copies or moving them out of the library merely to view/reveal them. Context
  actions edit those originals.
- Draw N is an explicit preset top-N move to the associated Hand destination,
  not a game-rule action inferred from a drag or label.
- Keep shared View/Reveal displays until explicit dismissal, not a timer. History,
  offline/review, deletion/removal, and undo/branching still require specification
  (Q-28); D-47 fixes membership, source-move updates, and dismissal scope.

Reference displays, Draw N, and explicit-dismissal lifetime are now approved,
alongside top-N order and shared View. Detailed schema, deletion/removal,
dismissal UI/history/undo, inputs/validation, and action boundaries still require
review.

Confirmed display behavior (D-47):

1. Keep a tray tied to the original shown card IDs/source order; do not refill
   automatically from the library's changing top N.
2. Keep moved cards in the tray with their updated location shown, rather than
   removing them. Entries remain references, not duplicate cards.
3. Log shared tray dismissal; remove only its active display, preserving cards,
   completed moves, and historical records.
4. Either participant can dismiss the shared tray.

Confirmed library-shortcut scope (D-48):

1. Quick Draw one alongside Draw N.
2. View bottom N / Reveal bottom N, using the same shared display model;
   bottom-subset direction is now fixed by D-49.
3. Move top N / Move bottom N to an explicitly selected destination.
4. Shuffle all only. The owner rejected top/bottom shuffle shortcuts. No
   automatic cleanup shuffle after viewing.

Confirmed N-card defaults (D-49):

1. Bottom-N displays retain normal top-to-bottom source order with the bottommost
   card marked, rather than reversing the subset.
2. N-input starts at one with +/- and numeric entry. Quick Draw one skips this
   picker; completing inputs has no extra Send/confirmation gate.
3. Clamp N to the available source count, replacing the proposed rejection.
   Clamping alone does not authorize skipping selected IDs; D-67 separately
   permits action-inapplicability skipping.
   Empty sources, count feedback, and stale/offline validation remain open.
4. A move from one ordered source preserves moved-card source order at either
   destination end and preserves existing destination members' relative order.
   Cross-source selection and action/interruption boundaries remain separate.

Confirmed pile interactions (D-50):

1. Drag a special pile's top-card surface to move the actual top card; tapping
   still opens contents and actions. Exact hit regions/thresholds remain open.
2. Contents browsers use card-type category columns like Cockatrice. The owner
   replaced the proposed top-to-bottom Graveyard/Exile browser list. Grouping
   does not reorder piles; the full library remains orderless and explicit
   top/bottom-N displays remain source-ordered, not type-grouped.
3. Name search/filtering and hold inspection in contents browsers. Filtering
   does not change stored order or move cards. Opening/View remains logged;
   this does not decide per-keystroke logging or exact search controls.

Approved initial-version browser-column defaults (D-51):

The owner accepts these for the initial version but anticipates possible
adjustment. Keep them as the current baseline and revisit during wireframe review
and usability testing; they are not a permanent layout commitment.

1. One entry per card in its catalog/preset main type column; Other for unknown
   types. Exact multi-type precedence remains to be specified.
2. Alphabetical name order within columns, independent of source order;
   duplicate-name ties must not reveal library positions.
3. Horizontal column scrolling and vertical scrolling within each column on
   narrow screens, rather than shrinking cards to unreadable sizes.

Covered View's opponent-facing category labels/layout remain open: grouping must
not silently authorize exposure of faces/names or other card metadata.

Incoming top-drop behavior and top/bottom Context Move destinations are already
confirmed. Other visible-card drags, column details, other bulk conveniences,
actual reorder controls, and exact browser gesture handling
remain separate proposals, not automatically approved with this revision.

## 12.9 G-07: Named values and counters

Primary contracts: [additional touch controls](10-board-and-controls.md#108-additional-controls-needed-for-complete-play)
and [participant/table values](02-domain-model.md#25-participanttable-values-and-markers).

Existing generic requirements remain authoritative: edits by either participant
and no inferred game rules. Moves preserve state except for the declared
D-55/D-56 Graveyard/Exile/Library reset composition. The owner replaced the
initial unapproved tracker-control batch with this model (D-52):

1. Optional bottom-right card text, used for P/T, loyalty, or arbitrary content.
   Context actions offer quick increment/decrement/set; exact handling of P/T
   pairs versus other text remains open.
2. Separate colored counters, each carrying its own text, on cards and players.
   D-58 selects the initial six-color palette and one counter per color/target.
3. Counter text is arbitrary; when numeric it supports quick inc/dec/set.
   Counter edits do not automatically change corner text or card characteristics.

The previous always-visible +/- tracker layout, Counters submenu route, and
tap-number-to-edit gesture are not approved by this replacement. Preserve normal
card tap/untap, double-tap actions, and hold inspection.

Confirmed text-first clarification (D-53): corner text is a single plain string,
not separate P/T or loyalty fields. Its current text is pattern-matched to offer
quick actions, which rewrite that same string. Pattern recognition does not
install a game-specific type or a separately authoritative numeric value.
Numeric counter conveniences likewise operate on the counter's text. Exact
patterns/format preservation and action sets still need specification.

Confirmed menu and move-reset updates (D-54–D-56):

- Counter inc/dec keeps the context menu open for repeated adjustments; edits
  queue/sync immediately. A successful context Move to Graveyard dismisses it.
  Other actions' menu lifetimes are not settled by these examples.
- Entering configured Graveyard/Exile/Library removes card counters and restores
  database-default corner text, overriding prior blanket preservation for these
  fields only. Represent generic changes, not rules-based card incarnations.
- The repeated destination was clarified as Library. The card database supplies
  default corner text; for creatures it is printed P/T. Reset replaces the
  current field with that default, not a prior manual override. Defaults for
  D-64/D-65 settle available type metadata, blank fallback, and retained per-face
  defaults. Exact source/custom mapping, same-container behavior, and undo
  composition remain open in Q-29.

Confirmed text-pattern quick actions (D-62):

1. Signed whole-number corner/counter text gets +/-1 and exact Set, without
   zero-clamping; e.g. `0` -> `-1`.
2. Corner text matching a pair of signed whole numbers separated by `/` gets
   left/right/both +/-1 actions and Set; both +1 makes `2/3` -> `3/4` as text.
3. Other text, including decimals and symbolic/mixed pairs such as `*/*` or
   `2/*`, gets Set only initially. No arithmetic expression evaluation or rules
   interpretation. Exact matching/format preservation and limits remain open.

Confirmed counter appearance/lifecycle (D-58):

1. Initial palette: red, blue, green, yellow, purple, and gray, with readable
   contrasting text. Exact styling/accessibility treatments still need design.
2. One counter per color on a single target; multiple same-color counters on
   one card/player were rejected. Different targets can use the same colors.
3. Zero remains visible; completed empty text removes the counter. Do not
   publish removal from temporary unfinished editor input. D-59 trims text
   first, so completed whitespace-only input removes the counter. A separate
   Remove shortcut remains open. Graveyard/Exile/Library entry still removes
   card counters under D-55/D-56.

Confirmed global trim rule (D-59): edge-trim all user-entered text on completion,
including corner/counter text, names/labels, notes, and messages. Preserve
internal spaces/line breaks. Empty-value validation and pattern matching operate
on trimmed text. D-67 supersedes D-60's initialization/color-choice interaction.

Confirmed counter vocabulary (D-67, replacing D-60's initialization interaction):

1. +1 color, -1 color, and Set color, with no Add/Initialize action.
2. Numeric counter edits are relative; nonnumeric arithmetic is inapplicable.
   D-68 resolves absent arithmetic: +1 creates `1`, -1 creates `-1`.
3. No recolor action, as already confirmed. Counter uniqueness/lifecycle remain.

Confirmed counter-menu touch behavior (D-61, updated by D-67):

1. Inline counter controls in the card context menu, no separate Counters
   submenu. Every color has -1/+1/Set; unusable arithmetic is disabled for a
   single target. No separate Add/Initialize controls.
2. Tap a player's panel to open equivalent controls for that participant.
3. Completed Set and +/- keep/return to the context menu. Empty trimmed Set
   removes the counter, not the color's controls. Move to Graveyard dismisses.

Confirmed corner-text editing (D-63):

1. Corner-text +/- and completed Set retain/return to the context menu.
2. Empty trimmed corner text leaves a blank overlay, not an automatic default
   restoration; printed art and card presence are unaffected.
3. Reset corner text restores the database default only, preserving counters and
   other card state. This differs from destination-entry resets that also remove
   counters. Exact Reset menu lifetime remains open.

Conditionally approved database-default mappings (D-64):

1. Planeswalker default corner text is its printed starting loyalty, if supplied.
2. Battle default corner text is its printed defense, if supplied.
3. Cards without an applicable printed value default to an empty string.

Creature P/T defaults are already confirmed by D-56. These are descriptive
catalog/import mappings to one plain string, not game-rule behavior or automatic
counter creation. The owner made approval conditional on available provider
data; no provider is selected. See [source research](07-architecture-and-operations.md#73-card-catalog-and-decks).
D-65 settles current-face Reset, blank fallback, and retained game defaults.
Overlapping types, custom authoring, and exact source-version mapping remain open.

Confirmed default-data behavior (D-65):

1. Missing provider metadata: blank editable corner text, no blocked manual play
   or guessed printed values.
2. Retain resolved defaults with the game for offline Reset and stability across
   provider updates.
3. Multi-faced definitions have per-face defaults; Reset uses the selected face.
   Switching face alone preserves the current corner text.

Confirmed mass-selection control parity (D-66):

- Offer exactly the individual card's action controls, targeting every selected
  card; no separate reduced bulk menu. Show selection scope/count.
- Apply edits to each card's own state: relative arithmetic for compatible
  values, common entered text for Set, and each card's own current-face default
  for Reset. Supported move/face/etc. context actions likewise target selection.
- D-67 settles partial applicability: skip targets where the individual action
  would be disabled; enable for selection if at least one target is applicable,
  disable only if all are inapplicable. Nonnumeric counters are skipped for
   arithmetic, not overwritten. D-68 resolves absent-counter arithmetic; exact
   eligibility/error tables and affected/skipped rendering remain Q-30.

Confirmed absent-counter outcomes (D-68):

1. +1 color creates absent counters with `1`.
2. -1 color creates absent counters with `-1`.
3. Set nonempty trimmed text creates/updates the chosen color on each target;
   empty removes where present and leaves absence unchanged.

Rules 1/2 use zero only as an arithmetic starting point, not an intermediate
visible counter or a separate initialization action.

Confirmed card-note/annotation support (D-69):

1. Edit note in card actions opens optional multiline text editing. This is a
   persistent card note, not corner text or an action-linked message/ping.
   Selection parity applies: enter the same note for every selected card.
2. Short notes appear directly on the card, replacing the proposed indicator-only
   presentation. Long notes can be ellipsized initially; inspect/editor always
   shows full stored text. Exact fitting, placement, and thresholds remain open;
   never truncate stored note content.
3. Empty trimmed text removes note/its display only. Moves retain notes under the
   existing unchanged-field contract, including Library/Graveyard/Exile entry.
   Reminder text does not enforce game behavior.

Exact numeric recognition/limits, styling, counter
layout/hit regions, remaining bulk details, and action/history boundaries
remain for later review.

## 12.10 G-08: Persistent links and relationships

Primary contracts: [additional touch controls](10-board-and-controls.md#108-additional-controls-needed-for-complete-play)
and [links versus pings](11-visual-action-language.md#114-pings-point-they-do-not-declare-mechanics).

These are persistent board relationships, not action-associated pings. Linking
does not classify a relationship as an aura/equipment attachment, target, combat
assignment, or other game mechanic. Dropping onto an ordinary card still forms
a visual stack under D-23, not an implicit link.

Confirmed persistent-link touch behavior (D-70):

1. Card context Link -> tap another card/player endpoint, without also invoking
   the target's ordinary tap action. Provide accessible endpoint picking too.
2. Persistent standard connector, distinct from pings/stacks. D-71 removes the
   earlier optional-label and line/arrow-style choices: linked or not suffices.
3. Line endpoints follow referenced objects visually; moving a card does not
   move the other card merely because linked. D-71 confirms removal/fan-out;
   D-73 excludes a separate initial Attach/follow tool.

Confirmed simple-link selection/removal (D-71):

1. Selection Link -> one endpoint creates one link from each selected card to
   that endpoint; selected A/B -> C yields A-C and B-C. No merged object.
   D-72 fixes duplicate-pair behavior; self-link presentation and boundaries
   remain open.
2. Links in the card context menu lists connections and offers removal, without
   label/style editing. Thin-line tapping is not the only route;
   covered endpoint presentation stays consistent with other controls.
3. Link removal changes only the relationship, not cards/fields/placement.
   Creation/removal are shared recorded operations with ordinary undo/recovery.

Confirmed binary-link semantics (D-72):

1. Undirected unique endpoint pairs: A-B and B-A are the same link.
2. Link existing pairs is idempotent, not a toggle. Explicit removal unlinks;
   mass Link creates missing pairs and preserves existing ones. Exact no-op
   history remains open.

Confirmed initial attachment/follow-movement scope (D-73): no separate Attach/
follow tool. Use binary links for relationships and existing multi-selection for
joint movement; dragging one linked card alone does not move its neighbors.
Anchor/follower grouping is outside initial scope, not awaiting implementation
defaults. No card-type/overlap inference is introduced.

Confirmed ping interaction direction (D-74): persistent ping mode, analogous to
tap/selection-edit interaction, replaces the proposed one-shot action-adjacent
command. Entering local mode does not itself create a ping/shared edit. Each
durable ping still references a recorded action; exact association is open.

Confirmed scope/lifetime (D-75): pinging is a toggleable state within one event.
Pings link to only that group/event and do not remain as overlays on later board
contexts. Their records remain viewable at that step in the event tree, including
downloaded offline review. Group/event denotes the visible grouped history unit;
exact atomic-action mapping and grouping heuristics remain later work. This does
not create persistent binary links or require acknowledgment before hiding pings.

Confirmed event-scoped toggle details (D-76):

1. Within the event, tap an already-pinged destination to toggle it off; tap an
   unpinged destination to toggle it on. Completed changes auto-queue/sync.
2. Switching event exits Ping input mode; no pings carry to the new event.
   Revisiting the old event shows recorded pings without enabling input mode.

Repeated taps toggle destination presence rather than adding duplicates; exact
location matching/hit tolerance remains open.

Confirmed mode interaction/marker controls (D-77):

1. Ping and selection-edit modes are mutually exclusive, retaining selection
   when switching. Ping only the touched destination, not the selected set.
2. Ping taps consume ordinary tap effects: no tap/untap, selection edits/clearing,
   or ordinary player controls. Other gesture arbitration remains open.
3. One standard marker, no dedicated ping label/style picker or required typing
   initially; optional notes/chat remain separate.

D-78 adds a Ping context action alongside the mode. D-79/D-80 confirm direct
behavior and trigger-card-directed selection scope; menu lifetime remains open.

Confirmed context-Ping behavior (D-79, selected-set rule revised by D-80):

1. Individual card/player context Ping toggles that destination in the current
   event directly, without a second target tap or changing Ping input mode.
2. Show only Ping or Unping based on the card used to open the selection-scoped
   menu: unpinged -> Ping all selected; pinged -> Unping all selected. Do not
   independently invert members or use an any/all-selected test (D-80).
   Exact context routing remains Q-30.

Detailed revisions, association, other gestures, and grouping remain later
review. Earlier required one-shot and P-15 drag-handle proposals are replaced by
the mode direction; D-78 separately adds a context shortcut.

## 12.11 G-09: Creation, copies, and removal

Primary contracts: [additional controls](10-board-and-controls.md#108-additional-controls-needed-for-complete-play)
and [action catalog](04-game-actions.md#42-coverage-matrix).

Confirmed token creation (D-81, simplified by D-82):

1. Table Create -> Token opens a searchable catalog; the user explicitly chooses
   a token definition. No card-text inference, provider selection, or general
   board/custom-piece editor is implied.
2. Choosing a definition creates one fresh-ID token automatically placed on the
   table; no quantity, preview, manual placement step, or extra confirm/Send.
   Reposition afterward using ordinary drag if desired.
3. Clone creates token instances from ordinary cards or tokens under D-83/D-84.
   The explicit destination-removal exception below replaces blanket no-expiry
   language; no other game rules are inferred.

D-82 replaces quantity input and rejects the pending placement-preview/picker
batch. Automatic location selection, initialization, missing/offline definitions,
custom creation/removal details, and history boundaries remain later review.
Ordinary stack limits and preservation of unrelated cards still apply. D-83
removes the separate Repeat last token shortcut.

Confirmed Clone behavior (D-83/D-84):

1. Clone creates an independent token instance with the current printed face, cover/
   orientation, corner text, counters, notes, and retained defaults; no links or
   event pings. Preserve source state and place automatically without a picker.
2. Selection-scoped Clone creates one new instance per selected card under
   D-66, not a quantity dialog. Exact routing/boundaries remain open.
3. Omit separate Repeat last token initially; Clone handles extra instances,
   while Create -> Token selects a different definition.

D-84/D-85 confirm that both catalog tokens and Clone outputs disappear from current
state when moved into configured Library/Graveyard/Exile; they do not remain
in the destination. Retain history/undo recovery. Explicit per-instance token
status, not card text/type/labels, determines this behavior. Exact records and
removed-reference handling remain open.

Confirmed removal controls (D-85):

1. Apply token disappearance equally to context Move and other explicit moves
   into those three destinations. Mixed sets move ordinary cards normally and
   remove tokens; do not skip tokens or drop unrelated objects.
2. Card/token context Remove supports selection scope, removes from current state
   rather than moving to a pile, and auto-queues/syncs with undo/history and no
   routine confirmation dialog.

Confirmed blank-token scope (D-86): one Blank token entry in the normal catalog
replaces the proposed custom-token name/corner form. Creation is the same one-
token automatic placement flow. Existing corner-text/counter/note controls and
Clone/Remove apply afterward; no dedicated custom-token/image authoring. Exact
blank presentation/initialization and broader authoring scope remain later work.

## 12.12 G-10: Other table tools

Primary contracts: [additional controls](10-board-and-controls.md#108-additional-controls-needed-for-complete-play),
[action catalog](04-game-actions.md#42-coverage-matrix), and
[randomness/history](03-history-and-undo.md#310-randomness-and-branching).

Confirmed basic randomness controls (D-87):

1. Roll die: one die, default six sides, with an optional sides control. No count
   dialog or game-specific roll mode initially. Picker/bounds/default memory open.
2. Flip coin: one heads/tails result, no configuration step.
3. Shared history shows actor/request/result; completed operations auto-queue/
   sync. Review/redo reuse recorded results, not new draws. No automatic card or
   value edits. Exact source/algorithm, offline policy, display, and boundaries
   remain later review.

D-92 supersedes D-88 and the pending seed-sequence proposal: each explicit new
die roll/coin flip is a fresh draw, including after undo. No opponent-action seed
reset or gameplay draw-position reuse. Coincidentally equal results are allowed.
Review/redo/replay and transport retries of the same operation reuse recorded
results. Exact generator/source and offline/concurrent protocols remain open.

D-93 confirms new Shuffle also samples a fresh order, including after undo;
review/redo/replay/retries reuse the recorded order. A repeated permutation by
chance is valid. No new shuffle shortcut or hidden-order exposure classification
is implied; exact generator/input/offline protocols remain later work.

Confirmed D-89/D-90: ordinary undo removes its action from the visible event tree
as if it never happened; information-gaining undo leaves a history branch. Keep
internal redo/recovery without exposing ordinary undone branches. Library View/
Reveal, covered-hand/card-face views including covered-face holds, and die/coin
results are covered; ordinary visible-face holds/every search keystroke are not.
Summaries do not automatically show covered identities. Branch review does not
activate it. Primary contract:
[information-gaining undo branches](03-history-and-undo.md#information-gaining-actions-and-undo-branches).

Confirmed mixed-sequence/exposure rules (D-91):

1. Retain the undone path only through its last information-gaining action,
   including ordinary steps before/between exposures. Omit trailing ordinary
   undone actions from the visible tree; no exposure means no undone branch.
   Keep internal recovery and restore the requested rollback state, not the
   last exposure's resulting board. Group rendering/atomicity remains open.
2. Any actual hidden-information exposure qualifies, including draws/moves/face
   changes, not just View/hold. Do not infer personal knowledge or game rules.

Exact event coalescing, redo reconciliation, schema/publication, collaborator/
branch/offline handling, and retention remain Q-31. This replaces the earlier
separate permanent-audit-marker proposal with retained information branches.

Confirmed optional communication direction (D-94/D-95):

1. Table Chat opens game-wide conversation; ordinary message submission is
   separate from gameplay publication and never a prerequisite to play/sync.
2. Group/event context Note/Reply opens an associated multiline editor. Text is
   optional, distinct from card notes/pings, and trimmed only on completion under
   D-59. No unfinished text publication. Exact threading, revisions/removal,
   submission controls, and historical/branch association remain later review.

D-94: submitted game-wide chat is not undoable; exact edit/delete, unsent delivery,
and editor drafts remain separate. D-95: event comments are ordinary nodes on
the current path, already a branch. Undo moves back but retains the existing
path through the comment. No special comment-only branch/activation mechanics.
Comments qualify as retention checkpoints alongside information gains under
D-91; card notes/pings remain distinct. Exact event/group association and
revision/dependency protocols remain later review.

D-96/D-105 require first-class turn/phase/step engine state. The proposed omission
and token/note/counter-only workaround are rejected. History, undo, branches,
offline queuing, and synchronization apply; detailed state and transitions are
Q-32, not permission to infer game rules or begin implementation.

D-97 replaces the configurable-sequence/MTG-preset proposal with hard-coded MTG
phases: Beginning → Precombat main → Combat → Postcombat main → Ending. No phase
editor or generic phase-definition system required. No inferred card effects.

D-98 confirms individual MTG steps inside those phases; see the domain sequence.
D-99 confirms phase/step-dependent quick-action offerings. D-100 confirms the
first mappings below; other shortcut maps remain under review. None executes
card operations merely on entering a phase/step.

Confirmed first shortcuts (D-100):

1. Untap step: Untap all for the turn player's battlefield cards.
2. Draw step: Draw one from the turn player's Library to their Hand, using the
   approved literal draw/move operation.
3. Both have an additional one-press variant performing the operation and
   advancing tracking. Standalone versions leave tracking unchanged.

D-101 settles next-step advancement: Untap → Upkeep and Draw → Precombat main,
not a whole-phase skip. Do not execute further card operations on arrival.
Exact participant association, remaining no-op/error records, atomic/group
boundaries, other shortcuts, and touch layout remain open. D-111 settles combined
advancement for valid empty/no-change cases.
D-110 settles that no skip-untap flag or inferred exclusion applies.

Confirmed progression controls (D-102):

1. Standalone Next step advances tracking without card edits.
2. Tap the phase/step indicator to choose a phase/step directly for skips or
   corrections, without executing intermediate card operations.

Grouping and exact layout remain later review; no priority state under D-105.
These are shared game edits with normal undo/branch/queue/sync, not history
navigation. Direct phase/step choice does not settle turn/player corrections.

Confirmed turn tracking (D-103):

1. Track/display whose turn it is and the turn number.
2. Next step from Cleanup starts the other player's Untap and increments the
   turn number, with no automatic untapping/drawing.

Increment per individual player's turn, not per round. History/undo/branches/
offline/sync apply. Review alone does not advance a turn. D-106 adds End turn
from any phase/step: other player's Beginning/Untap at number +1, without card
operations, using the Cleanup-to-next-turn transition. It rejects a manual
turn-number editor, not undo restoration. D-108 settles starter/initial turn;
D-109 uses manual steps for extra turns with an approximate count. Setup details
and exact controls remain later review.

D-105 removes priority tracking, display, Pass actions, and priority-legality
validation, superseding D-104 and D-96's priority portion. Players queue actions
and interrupt/branch when they judge timing permits it; the engine does not
decide priority or infer it from cards/phases. Cooperative access and structural
validation remain. Turn/phase/step tracking and approved shortcuts remain.

Priority-default/tap/correction proposals are withdrawn, not pending blockers.
No no-holder or resolution-state UI/model is required for priority.

The turn-indicator player/number editor proposal is withdrawn. No manual number
editing. D-109 rejects dedicated extra-turn controls or count reconciliation.

Confirmed End turn touch control (D-107): visible beside Next step, one tap with
no routine confirmation, using Undo for mistakes. No menu or hold prerequisite.
Exact geometry/hit targets remain open, not permission for an extra routine step.

Confirmed setup (D-108): choose starting player during setup; start turn 1 at
Beginning/Untap without automatic untapping/drawing. This is initial tracking,
not an opening-hand/setup rules procedure. Exact screen/defaults and change/
recovery boundaries remain later review.

D-109 rejects Start another turn and extra-turn scheduling. Players manually
choose/repeat steps with the existing picker; it preserves player/number rather
than inferring another turn. Inaccurate turn counting is acceptable. Normal
End turn/Cleanup advancement still switches players and increments the number.

Confirmed D-110: Untap all simply untaps the turn player's battlefield cards,
without a dedicated skip-untap flag. Re-tap exceptions manually; no exclusions
are inferred from card text/notes/markers. Applies to both shortcut variants.

Confirmed shortcut edge cases (D-111):

1. Untap all + advance advances even with no tapped/affected cards.
2. Draw one + advance on an empty Library draws nothing but still advances;
   no automatic loss or other rule effect.

Valid no-change/empty cases only, not structural failures. Record/sync the actual
step transition without inventing a card change. Standalone actions do not
advance. Exact no-op records, feedback, and atomic/group boundaries remain open.

The no-more-phase-shortcuts proposal is rejected. D-112–D-114 require combat/
Stack projections, explicit blocker assignment, and contextual Stack actions.
Review all behavior cases before implementation, not only generic substitutes.
The [primary contract](13-combat-stack-and-projections.md) tracks common source/
projection semantics, combat lifecycle, and Stack entry/exit separately.

Next G-11 behavior proposal, not yet approved: creating an attacker projection
also taps its source by default; offer a without-tapping variant for exceptions.
No card-text inference. Exact gestures/layout are stage 3 under D-115, not the
current question. Blocker associations, multi-source behavior, lifecycle, and
history boundaries remain stage 1 work after this batch.
