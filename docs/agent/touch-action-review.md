# 12. Step-by-step touch action review

Status: delivery stage 1, behavior specification (D-115). This worklist retains
earlier touch decisions as requirements/history, but current new decisions focus
on behavior. Adversarial review/refinement is stage 2, UI drafts/concrete controls
stage 3, and iterative implementation stage 4+. No implementation is authorized.

**Latest acceptance correction (D-187):** normally accept ordinary actions unless
truly impossible. No general previous orientation/text/Set comparisons. Card
moves keep source/destination guards; Stack entries/new projections send current
client Stack state and reject if different from current server Stack state.
Compare-and-apply atomically; no opponent/seen detector or whole-game-head lock.
Use predicted pre-action Stack state for later local queued entries without
acknowledgement waits. Snapshot encoding/equality representation and provisional
references remain technical design. D-187 supersedes D-181's old-field checks,
D-186's Set comparison and the old 25-rule review below; preserve deltas,
unrelated fields, explicit historical intent and D-185 reconciliation. Do not
restart the old numbered review or treat it as approved. See T-387–T-395.

**Setup decisions (D-188–D-190):** required login and available registration;
main-menu Logout/Manage decks/Play; adding users (Friends name tentative); Play
lists current games/pending invites. Deck editing is import-list-only. A game
begins in a lobby with deck selection and mainboard/sideboard management, and
starts once both players press Ready. See the
[primary contract](../design/03-contracts/accounts-decks-and-lobby.md) and
T-396–T-405. D-191 confirms that changing your deck/mainboard/sideboard clears
your own Ready and lobby changes leave the saved deck untouched. Next substantive
setup gaps: actual starting operations, remaining invitation lifecycle and readiness/
start-versus-edit behavior. D-192 excludes automatic opening draws and requires
mulligan quick actions. D-193 specifies shuffle-and-draw N (default seven) and
current hand size minus one. D-194 confirms both return the whole hand first,
then shuffle, then draw; use pre-return hand size for the minus-one variant.
Both are Library context actions and quick actions only during turn 1's Untap.
Do not reopen hand return or Beginning-versus-Untap visibility. Per-card ordering,
concurrent count timing/count edges remain open
(T-406–T-413).
D-195 confirms inviting an added user, recipient Accept/Decline in Play and lobby
entry on acceptance, not Ready or game start (T-414–T-416). D-196 confirms username
search and saving users as contacts without mutual friend approval (T-417/T-418).
Exact search/contact removal/privacy and decline/cancel/reinvite/persistence
details remain open. D-197 confirms username/email/password registration,
email/password login, email password reset and other users seeing usernames, not
emails (T-419–T-422). Exact reset/session/verification and field validation remain
open; do not reopen the credential/recovery-channel choices. D-198 confirms common
exported MTG text decklists, optional printing/sideboard notation, flagged
unknown/ambiguous names without substitution, preserved input and no saving until
errors are resolved (T-423–T-427). Do not invent a custom grammar or restrict cards
to Arena. D-199 confirms named/renameable decks, cover art from one of a deck's
cards, re-import Replace/Save as new, and unchanged already selected lobby/game
copies (T-428–T-432). Do not reopen these choices or interpret metadata controls
as a full card-list editor. Remaining import/metadata/deletion details are open;
next useful substantial topic is remaining initial arrangement and starter
selection, not a tiny cover-art questionnaire. D-200 confirms automatic Library
shuffles on game start and 20 starting life, with no pregame life editor required;
normal in-game life edits remain (T-433–T-436). The assistant's no-auto-shuffle/
editable-starting-life proposal is not current behavior. Other proposed initial
placement and starter-access/default details were not approved by that correction.
Do not resume the assistant's automatic draw-seven/manual-only
mulligans proposal or silently approve full deck
editing/export, a separate host Start, or the assistant's immediate-table proposal.

**Current model (D-176/D-177):** one individual mutation per node, including
ordered individual operations from bulk controls. Every mutation immediately
extends/branches the chosen path and publishes. Groups are playback steps with
concurrent animation, not gameplay/publication units. Resume at the last local
state, animate rollback to an interruption, then automatic or manual forward
playback; pause and drag the horizontal bottom timeline to select an exact node.
Do not resume the assistant's unapproved three-rule precedence questionnaire or
ask again whether bulk/group constituents publish. Earlier combined-node mappings
are superseded; existing shortcut effects, order and placement remain.

**Concurrency correction (D-179):** historical continuation requires a deliberate
UI choice and explicit message flag/boundary; Undo supplies intent automatically.
Ordinary actions validate action-specific from/to conditions at the current head,
appending if valid and rejecting otherwise. Concurrent changes/stale views do not
create branches. This supersedes D-13/P-11 and implicit edit-from-review branching,
not the mutation/playback model above. Remaining work: per-action lock conditions,
rejection recovery/composition, dependent queued work and simultaneous explicit
interruptions. See [history](../design/03-contracts/history-and-undo.md), Q-23 and
T-349–T-357; no final UI/wire-schema decision or implementation is authorized.
D-180 confirms feedback: immediate optimistic local state, Undo-like animated
reversal on rejection and animated incoming opponent mutations. This is not a
shared Undo/branch or approval of the assistant's independent/dependent queue
policy. Exact queue recovery/composition and per-action locks remain open;
see history section 3.3 and T-358–T-362.
D-181 confirms relevant-state-only locking: tap checks previous orientation, note
edit checks previous text; movement alone does not invalidate either if still
applicable. Preserve unrelated current fields and structural/existence validation.
Remaining category-specific conditions and pending-queue recovery/composition are
open; this is not approval of a separate queue policy. See T-363–T-366.

**Initial-delivery scope (D-182):** game actions/shared controls require a
connection. Downloaded games remain read-only offline; offline actions and later
queue replay are excluded, superseding earlier offline-play/Undo/random-generation
references below. D-179–D-181 continue to apply online. Narrow in-flight request
recovery, connection readiness and overlapping online predictions remain open;
the assistant's queued-sequence proposal was not approved. Do not restart a full
offline queue-design discussion. See T-367–T-370.

D-183 clarifies the online pipeline: a small local send queue, concurrent sending,
individual success/failure and immediate local interaction without routine network
waits. D-182 does not prohibit that queue or require one in-flight request. A
failed action does not cancel another valid action or the whole queue. The
assistant's pause-until-resolved interaction gate is unapproved. Exact queue
sizing/saturation, ordering/dependencies, animation composition and connection-loss
recovery remain open, not blanket stop-and-wait. See T-371–T-374.

D-184 settles ordering: each client sends in action order and the server checks/
applies in arrival order. No sequencing dependency metadata or server-side client-
order reconstruction; no acknowledgement wait between sends. Other-player actions
may interleave, and later actions retain individual checks after a rejection.
Exact ordered transport, provisional references and reconnect recovery remain
open, not the basic ordering policy. See T-375–T-377.

D-185 settles ordinary reconciliation composition: use accepted history plus
remaining valid local predictions, animating only actual corrections without
visible rewind/replay of unaffected work. A failed move followed by a successful
tap returns the card while keeping it tapped. Preserve accepted local/opponent
work and valid pending predictions; correct invalid later predictions too.
Acknowledgement alone adds no animation; do not fabricate server outcomes or
rewrite requests. Exact bookkeeping/scheduling remains open, and D-177 deliberate
history/interruption playback is unchanged. See T-378–T-382.

Historical D-186 confirmed current-value counter deltas and a Set comparison;
D-187 keeps deltas but removes that comparison. The requested
[25-rule review](../design/03-contracts/optimistic-locking.md) was superseded by
the new permissive baseline, not approved. Do not continue its pending numbered
review or infer its absent-counter/cleanup/Clone/other detailed policies.

This is a review worklist, not a second interaction contract. Approved decisions
must be reflected in the relevant primary document, decision register, and
acceptance scenarios. Cockatrice capabilities are references, not automatic
first-release requirements. Unanswered proposals below remain unapproved.

Review style (D-33): batch straightforward likely-yes choices into small related
numbered sets, allowing approval together or exceptions by number. Do not
fragment every routine default into a separate conversational turn.

Review continuity (D-57): end design updates with the next contextualized
question/batch rather than waiting for “next?”. D-178 replaces occasional
checkpoints: validate, commit and push after every completed edit batch using
`./scripts/save.sh <edited paths...>` with its fixed message. Keep proposed
versus approved behavior explicit. This does not change the documentation-only
implementation gate.

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
| G-03 | Tap/untap and bulk orientation. | D-35/D-36 confirm selection-aware tapping; D-100/D-110 add literal turn-player battlefield Untap all without skip flags or inferred exclusions. Players re-tap exceptions manually; D-176 records individual orientation mutations. |
| G-04 | Inspect, face-down/up, printed faces, and reveal presentation. | D-37/D-38 confirm context face changes and automatic face/both-face inspection; exact targeting/layout and logging remain open. |
| G-05 | Ordinary piles: capacity, browse, reorder, split, and whole-pile controls. | D-39/D-40 confirm the cap, overflow, no browser/Select stack, and ordinary drag controls; geometric details remain open. |
| G-06 | Ordered containers: browse, top/bottom/N-card moves, insertion, search, shuffle, and redraw shortcuts. | D-41–D-51 confirm displays, shortcuts, clamping/order, direct top-card drag, search/filtering, and initial column/sort/scroll defaults; category mapping and exact controls remain under review. |
| G-07 | Card/player named values, counters, annotations, and bulk edits. | D-52–D-69 confirm corner/counter/note capabilities and selection applicability; D-176 fixes individual target mutations for bulk edits. Exact geometry, formatting and limits remain open. |
| G-08 | Attachments/relationships, visual links, and action-associated pings. | D-70–D-80 confirm binary links/no Attach tool and event-scoped pings. D-119 adds event-wide order numbers; D-120 projection links disappear with endpoints. Numbering mutation, endpoint routing, grouping, and exact geometry remain open. |
| G-09 | Create, duplicate, remove, and repeat creation of cards/tokens/pieces. | D-81–D-86 confirm token creation/Clone/removal and one Blank catalog token instead of a custom form. Exact placement, initialization, references, and history boundaries remain later review; no wider custom authoring approved. |
| G-10 | Other table tools: dice/coins, turn/phase/step tracking, markers, messages, and optional convenience calculations. | D-87/D-89–D-111 confirm existing tools/tracking/shortcuts. No priority or extra-turn action. No-further-phase-shortcuts proposal rejected; required combat/Stack work continues in G-11–G-13 under D-112–D-115. |
| G-11 | Temporary combat area, attacker/blocker projections, and paired-slot grouping. | Confirmed: separate actions/declaration variants, shared source state, occurrence reuse/uniqueness, paired grouping/cleanup/reflow, uniform slot drops/insertion, context removal, declaration defaults/auto-area/visibility, and shortcut event mappings. D-152 settles existing-blocker declaration conversion. Moved-collection/new-pair order, edge/between-pair/absent-area drag targets, other-view applicability, and additional single-card quick offerings remain open. See primary contracts/decision register; UI drafts remain stage 3. |
| G-12 | Stack naming, real cards versus activated-ability projections, and source/reference behavior. | D-113 requires Stack virtual occurrences; D-123 shares real-card edits/moves. D-125 retains effects despite source departure/removal until explicit removal/resolution. D-134 allows unlimited independent projections, including from one source. Missing-source display/actions and other entry/reference cases remain open. |
| G-13 | Contextual Stack quick actions for real cards and projections. | D-114 requires instant/sorcery → Graveyard, permanent → Battlefield, projection → remove projection. D-125 requires explicit effect removal/resolution independent of source departure. Category/routing/missing-source cases remain open; no automatic resolution. |

Detailed action grouping, interruption boundaries, and playback follow capability
and touch-control review. Existing confirmed history/sync requirements remain in
force; this sequencing does not approve any pending grouping proposal.

## 12.2 G-01: Move one card

Primary contract: [board and controls, sections 10.4–10.6](../design/03-contracts/board-and-controls.md#104-dragging-and-snapping).

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

Primary contracts: [board controls](../design/03-contracts/board-and-controls.md) and
[bulk actions](../design/03-contracts/game-actions.md#45-bulk-actions-and-selections).

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

Primary contract: [board gestures and ordinary piles](../design/03-contracts/board-and-controls.md).
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
wider-set/all-table shortcuts and rotation controls remain open; D-176 fixes
individual orientation mutations for bulk operations.

## 12.5 Grouped defaults: selection and gesture consistency

The owner requested batching likely-yes questions (D-33) and named an exception
to item 5. Items 1–4 are recorded as accepted (D-34); item 5 is rejected and
replaced by D-35. Primary behavior is described
in [board controls, section 10.3](../design/03-contracts/board-and-controls.md#103-gesture-arbitration).

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
[board controls](../design/03-contracts/board-and-controls.md#inspection-face-down-presentation-and-printed-faces):

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
[special-pile controls](../design/03-contracts/board-and-controls.md#106-special-piles-library-graveyard-exile):

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

Primary contracts: [additional touch controls](../design/03-contracts/board-and-controls.md#108-additional-controls-needed-for-complete-play)
and [participant/table values](../design/03-contracts/domain-model.md#25-participanttable-values-and-markers).

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
data; no provider is selected. See [source research](../design/04-technical/architecture-and-operations.md#73-card-catalog-and-decks).
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

Primary contracts: [additional touch controls](../design/03-contracts/board-and-controls.md#108-additional-controls-needed-for-complete-play)
and [links versus pings](../design/03-contracts/visual-action-language.md#114-pings-point-they-do-not-declare-mechanics).

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

Current grouping rules are in the [grouping contract](../design/03-contracts/history-grouping.md):
D-168/D-170 give same-author pings no group-start marker, with author changes
always splitting. D-172 puts persistent-link creation/removal in the ordinary
card/player edit family. Historical association, numbering mutations, other
gestures and remaining grouping cases still need review. Earlier required
one-shot and P-15 drag-handle proposals are replaced by the mode direction;
D-78 separately adds a context shortcut.

Confirmed display/projection grouping (D-175): each explicit View/Reveal opening
starts a separate review group. Combat-area creation and combat occurrence
removal join combat projection creation/repositioning. Stack rearrangements group
together separately from entries/removals; each explicit effect removal stays
separately visible. Existing companion/author rules and forward dismissal/closure
remain. D-176 makes bulk removals individual mutations. Inspection-record attachment and remaining
same-author precedence are open; grouping does not change underlying action effects.

## 12.11 G-09: Creation, copies, and removal

Primary contracts: [additional controls](../design/03-contracts/board-and-controls.md#108-additional-controls-needed-for-complete-play)
and [action catalog](../design/03-contracts/game-actions.md#42-coverage-matrix).

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

Confirmed removal controls (D-85, restricted by D-174):

1. Apply token disappearance equally to context Move and other explicit moves
   into those three destinations. Mixed sets move ordinary cards normally and
   remove tokens; do not skip tokens or drop unrelated objects.
2. Token context Remove supports token-selection scope, removes from current state
   rather than moving to a pile, and auto-queues/syncs with undo/history and no
   routine confirmation dialog. Non-token cards have no Remove/delete action;
   bulk, correction and projection paths cannot delete them (D-174). Exact mixed
   selection offerings/routing remain open within that restriction.

Confirmed blank-token scope (D-86): one Blank token entry in the normal catalog
replaces the proposed custom-token name/corner form. Creation is the same one-
token automatic placement flow. Existing corner-text/counter/note controls and
Clone/Remove apply afterward; no dedicated custom-token/image authoring. Exact
blank presentation/initialization and broader authoring scope remain later work.

## 12.12 G-10: Other table tools

Primary contracts: [additional controls](../design/03-contracts/board-and-controls.md#108-additional-controls-needed-for-complete-play),
[action catalog](../design/03-contracts/game-actions.md#42-coverage-matrix), and
[randomness/history](../design/03-contracts/history-and-undo.md#310-randomness-and-branching).

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

Confirmed D-159/D-160: Undo moves the shared active pointer one parent back,
regardless of author. Retain all descendants. After replacement play, ordinary
abandoned continuations hide behind Show hidden branches; exposure/comment
continuations stay visible in full. Library View/
Reveal, covered-hand/card-face views including covered-face holds, and die/coin
results are covered; ordinary visible-face holds/every search keystroke are not.
Summaries do not automatically show covered identities. Branch review does not
activate it. Primary contract:
[information-gaining undo branches](../design/03-contracts/history-and-undo.md#information-gaining-actions-and-undo-branches).

D-161 binds Undo to a specific node-to-parent transition. If it is no longer
valid, silently discard it and reconcile local state, without notification,
confirmation or retry clutter. Do not retarget to unseen work. Concurrent/offline
requests use the same rule; delivery retries retain their original idempotent
outcomes. Other queued work is retained; continuations made after Undo carry
D-179's background historical-continuation intent and chosen boundary.

Confirmed branch-level/exposure rules (D-91, refined by D-160):

1. Retain the whole abandoned continuation. Any exposure or event comment keeps
   it fully visible, including trailing ordinary nodes; otherwise hide it behind
   a toggle. Never trim the visible path at its last exposure/comment (D-160).
   Visibility does not change the active pointer or delete retained history.
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
Exact participant association, remaining no-change/error feedback, partial-group/
concurrency cases, other shortcuts, and touch layout remain open. D-111 settles
empty/no-change advancement; D-150/D-151 fix normal-step event boundaries.
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
The [primary contract](../design/03-contracts/combat-stack-and-projections.md) tracks common source/
projection semantics, combat lifecycle, and Stack entry/exit separately.

Confirmed D-116–D-118: area creation, attacker declaration, and tapping are
separate actions. Plain declaration does not tap. Phase-appropriate multiselect
Declare + tap / without-tapping both auto-create the area. Blockers can be
dragged to attackers or blank unassigned slots; attackers get more space with
more assigned blockers. D-147 settles declaration quick visibility. Remaining
empty/mixed/source-applicability cases and combined event boundaries remain open;
subsequent decisions below settle occurrence reuse and common slot routing.

D-119 adds event-wide ping order numbers; D-120 adds projection-to-projection
links that disappear with endpoints. D-121 guarantees non-quick full paths to
all targeted combined/individual actions, regardless of quick visibility.
See the primary projection and ping contracts for pending details.

Confirmed D-122: removing combat area clears its projections/projection links
without changing originals' current state, including orientation. It does not
undo previous edits. Automatic/manual lifetime remains undecided.

D-123: card actions through any projection always affect the real card. These
are live shared fields, not independent copies; dragging a projection to
Graveyard moves the actual card with ordinary destination resets/token-removal
behavior. Creating/assigning projections still preserves source placement.

D-124/D-125 replace a universal projected-move cleanup proposal: anything taking
the real card off the battlefield removes its combat projections/incident links,
but its Stack effects remain until explicitly removed/resolved. This is independent
of which occurrence/source invoked the move; Stack effects survive token-source removal
too. Missing-source display/inspection/card-action applicability remains open.

Confirmed D-126/D-139: when an attacker leaves, surviving cards stay grouped in
their slots; the final attacker's departure leaves a blocker-only pair, not a
side collection. No individual assignment cleanup; real cards remain unchanged.
D-138 replaces the earlier literal fixed-position requirement with stable slot
membership/order while permitting visual reflow. Exact geometry is stage 3.

Confirmed D-127: combat area stays open until explicitly closed, including across
ordinary phase/step changes and End turn. Provide advance out of combat + close
as a combined quick action, preserving separate advance/close and a full non-quick
path. D-122 governs closure; individual source-departure cleanup still applies.
D-128 fixes quick visibility to End of combat, the last combat step, advancing
to Postcombat main. Do not offer the quick action at earlier combat steps; keep
full non-quick access under D-121. Out-of-combat/missing-area handling and combined
boundaries remain open.

D-159/D-160 Undo follows one parent regardless of author. D-176 supersedes
D-132/D-148–D-150's combined-node mappings and MTG-reaction-window node sizing:
record each individual area/occurrence/card/tracking mutation, including those
issued by a shortcut. Bulk operations issue individual operations in a sensible
recorded order. D-177 groups concurrent-animation playback steps only; exact
mutation states remain selectable on the paused bottom timeline.

Confirmed D-133: repeated attacker declaration keeps existing attacker projections
and adds only new attackers; Declare + tap still taps its selected sources.
Other entry and no-change record cases remain separate; D-135 settles combat
occurrence uniqueness and attacker-to-blocker drag below.
Confirmed D-134: Stack allows unlimited independent projections, including several
from the same source. Repeated creation adds occurrences rather than reusing them;
remove/resolve each separately without removing siblings or the source. Card
fields remain shared; source departure/removal leaves all Stack effects intact.
Ordering, duplicate-source selection, missing-source behavior, and concurrency
remain separate review cases.
Confirmed D-135: at most one projection per real card per combat area, across
attacker/blocker roles. Dragging an attacker to a blocker position moves that
same occurrence, preserving identity and leaving real source state unchanged.
This UX constraint rejects independent simultaneous combat-role occurrences;
unlimited Stack effects remain allowed. D-136 settles former-attacker blocker
cleanup; D-142 settles reverse projection drags. Existing-source handling through
non-drag controls remains open.
Confirmed D-136/D-139: dragging an attacker into a blocker role leaves the other
cards grouped in their slots, with a blocker-only pair if the last attacker leaves.
Confirmed D-137: combat uses paired opposing slots, with multiple attackers and
multiple blockers on their respective sides; slot size grows/shrinks visually as
cards are added/removed. D-139 settles visual grouping without per-card assignments.
Confirmed D-138 (formerly P-19): immediately remove a pair only when both sides
are empty, closing the gap; retain pairs with either side occupied. Between-pair
drops insert a new pair with the moved cards, not implicitly an entire origin
pair, preserving other pairs' contents/relative order. Remove fully empty origins
by the same rule. Allow visual reflow while preserving surviving slot membership/
order, replacing literal fixed positions. Combat with no pairs stays open. D-142
settles target-slot role routing; between-pair side targeting and exact event/
concurrency cases remain open.
Confirmed D-139: each pair is one visual combat group without individual attacker↔
blocker assignments. Placement and optional pings communicate finer details; no
inferred all-to-all blocking rules or mandatory pings. Removing/role-changing one
attacker leaves remaining cards grouped; the final attacker's departure leaves a
blocker-only pair. Recovery retains paired membership/roles/order, not an assignment graph.
Confirmed D-140: dragging a real battlefield card into either combat side creates
its projection there or retargets its existing occurrence in that area, preserving
identity. Leave original position/location/orientation unchanged; no implicit
tap/untap or actual card move into combat. Destination side determines role;
other cards retain grouping, with fully empty origins removed. D-141 settles
selected real-card common-target routing; D-142 settles mixed combat-role/view
target-slot routing. Other-view applicability, absent-area
entry, no-change records, and event/concurrency details remain open.
Confirmed D-141: all selected real battlefield cards dragged onto one combat slot
join that same slot and side, creating/reusing each source's occurrence, not one
pair per source. Preserve reused identities and real source state; other cards
stay grouped and fully empty origins disappear normally. One selected drag uses
parent-node Undo (D-159). D-142 extends this to mixed combat-role/view selections;
duplicate-source views, moved-collection order, and event/concurrency remain open;
D-143 settles within-slot insertion versus append.
Confirmed D-142: paired attacker/blocker slots are drop destinations, not card-role
filters. Drag any collection onto one slot and all go there side-by-side with that
slot's role. This includes mixed roles, either direction of projection-role change,
and real battlefield/combat-projection mixtures. Reject proposed mixed-role
preservation/splitting; do not form an ordinary overlapping pile. Preserve source
state/occurrence uniqueness and other cards' grouping, removing fully empty origins.
D-143 settles within-slot insertion versus append. Moved-collection ordering,
other-view applicability, duplicate-source selection, edge/empty-area targeting,
and event/concurrency details remain open.
Confirmed D-143: dropping between cards inside a combat slot inserts there;
dropping into unused slot space appends. Preserve other cards' relative order.
Distinguish this from between-pair drops creating a new pair. Uniform target-slot
role routing, source preservation, occurrence reuse, and parent-node Undo
remain unchanged. Moved-collection order, hit regions, no-change records, and
event/concurrency composition remain open.
Confirmed D-144: Remove from combat is a context action for one or several selected
combat projections. Remove only those occurrences/incident links, preserving real
source state, Stack effects, other cards' grouping/order, and the open combat area.
Clean up fully empty pairs normally. D-176 records individual occurrence removals;
per-mutation Undo/traversal retains roles/slots/order/links and cleanup context.
Real-source/mixed-selection access, token-source
Remove entry, drag-back behavior, menu layout, and event/concurrency remain open.
Non-token source Remove is unavailable under D-174, not an unfinished feature.
Confirmed D-145: declaration shortcuts place each newly declared attacker in its
own pair by default; existing attackers retain slots/grouping/order, including
existing multi-attacker groups. Collection drags still share the chosen slot.
Order among multiple new pairs and exact concurrency remain open. D-153 settles
append placement; D-152 settles existing
blockers below; D-148/D-149 settle declaration event counts.
Confirmed D-146: single-card declaration auto-creates an absent combat area like
multiselect shortcuts, without implicit tapping/source movement or a separate
creation action. Reuse an existing area without clearing/replacing it; manual
area creation remains available. D-147 settles multiselect quick visibility;
absent-area drag targeting and additional single-card quick offerings remain open.
Confirmed D-147: show both Declare + tap and Declare without tapping as multiselect
quick actions only during Declare attackers. Keep full context-action access to
the combinations/constituents at other steps under D-121; no implicit execution,
additional single-card offerings, participant/turn locks, or runtime legality checks.
Remaining selection feedback and event/concurrency mappings remain open.
Current shortcut mutation policy (D-176, superseding D-148–D-150): declarations
issue distinct area/individual occurrence mutations and individual source taps
for +tap; without-tapping preserves orientation. Untap all issues individual
untaps followed by any advance. Draw + advance records an actual card move, if
any, followed by advancement; an empty Library creates no draw mutation. Every
mutation immediately publishes and is one parent-step Undo. No extra tap/Send;
normal exposure retention remains. Exact cleanup sequencing, feedback,
dependencies/concurrency and out-of-step behavior remain open.
Confirmed D-152: declaration shortcuts convert a currently projected blocker by
moving its existing occurrence into its own new attacker pair, retaining identity/
incident links, not duplicating/recreating. Apply normal +tap/without-tapping;
existing attackers retain slots and other origin cards retain grouping. Remove
only fully empty origins. Keep D-176's individual mutation nodes; order among
multiple new pairs, source-feedback/duplicate-view cases, and concurrency remain open.
Confirmed D-153: append newly created declaration pairs after existing pairs,
preserving surviving existing contents/order and retained attackers' slots.
Order among several new pairs created together remains open.
Confirmed D-154: when cards are dragged together from one combat slot, preserve
their pre-drag relative order within the moved set, not selection-tap order.
Insert/append that sequence under D-143, including subsets and same-slot reorder,
without duplicating occurrences or editing sources. Other cards retain order.
Mixed-view order, multiple-new-pair order, no-change presentation,
and exact concurrency remain open.
Confirmed D-155: combat has two horizontal rows, blockers on the upper row and
attackers on the lower row. Opposing slots remain paired across the rows with
side-by-side contents and existing cleanup. D-156 separately approves row-first
cross-slot drag ordering, not pair-first/attacker-first traversal.
Confirmed D-156: cross-slot combat drags use selected blockers left-to-right,
then selected attackers left-to-right, preserving each slot's pre-drag order.
Insert/append the sequence together under D-143; all take the destination role
under D-142. Preserve identities/links/source state and remaining grouping/order;
apply empty-pair cleanup and per-mutation Undo (D-176). Mixed-view order,
new-pair order, duplicate-source views, and exact concurrency remain open.
Confirmed D-157: real battlefield cards dragged together into combat use pre-drag
visual order, top-to-bottom then left-to-right, not selection-tap order. Create/
reuse their occurrences in that sequence and insert/append in the chosen slot,
preserving originals and established role/identity/link/cleanup/Undo behavior.
Mixed/duplicate views, new declaration pairs, irregular/overlapping battlefield
layout ordering, and concurrency remain open.
Pause/resume context is in the [session handoff](session-handoff.md).
Confirmed D-158: combat is a separate area between the two play areas. Drags
place created/reused projections at the drop target without moving originals;
declaration quick actions add new attackers to the right of existing cards/pairs.
Existing attackers stay put. Do not confuse internal collection order with
destination placement. Withdraw the unapproved mixed-view projections-first
sorting proposal and stop requesting speculative sorting decisions.
No UI draft/implementation authorized.
