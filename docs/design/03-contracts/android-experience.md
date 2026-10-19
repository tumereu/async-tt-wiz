# 5. Android experience

Status: proposed interaction architecture except confirmed requirements noted
below, including D-177's resume/playback and bottom timeline behavior. Annotated
wireframes, exact layouts, visual design, and Android support versions remain open.

Confirmed card gestures and proposed drag/pile/intent details are defined in
[Game board and controls](board-and-controls.md), the primary tabletop-input
contract. Single tap taps, double tap opens actions, hold inspects, and drag moves
with snapping. These replace the earlier undecided tap-versus-inspect mapping.
The [visual action language](visual-action-language.md) defines the proposed
Stack area, action-linked ping controls, literal grouped log presentation,
and optional notes/chat. Ordinary flow should not require typing what just
happened, and the engine must not guess what the actions mean in a game's rules.
D-112–D-114 additionally require combat/Stack projections, visual attacker/blocker
grouping (D-139), and contextual Stack actions. Their behavior is being specified in
[the primary contract](combat-stack-and-projections.md). D-115 reserves new
UI drafts, final layouts, and concrete control choices for delivery stage 3;
current work is stage 1 behavior specification.

D-116–D-118 separate combat-area creation/declaration/tapping, add phase-appropriate
multiselect declaration combinations, blocker-to-attacker drops/blank slots, and
adaptive spacing. These user-specified requirements guide later drafts, not a
finished screen. D-119 adds event-wide ping order numbers; D-120 removes links
with their projection endpoints. D-121 requires another full action path for
every targeted combined/individual quick action; quick visibility is not access.

D-122 removes combat projections/links without moving/untapping originals or
undoing prior edits. D-123 makes card actions through projections affect the real
card, including destination drops such as Graveyard. They are live views with
shared card fields, not independent snapshots. Exact occurrence cleanup versus
token-source Remove routing remains open; non-token cards have no Remove action
(D-174). D-124 removes combat projections when their
source leaves the battlefield; D-125 leaves Stack effects until explicit removal/
resolution, even after token-source removal. Missing-source Stack display/inspection/
card actions remain behavior work, not UI drafts. D-126 keeps surviving blockers
in their existing slot after attacker removal. D-139 retains the remaining visual
group with other attackers, or a blocker-only pair if the final attacker leaves;
there are no individual attacker↔blocker assignments inside a pair. D-138 replaces literal
fixed positions with stable slot membership/order: visual reflow is allowed,
fully empty pairs disappear immediately, and between-pair drops insert a pair
containing the moved cards. Do not automatically regroup survivors; pairs with
either side occupied remain. Exact bounds/rendering are stage 3.

D-127 adds a combined advance out of combat + close area quick action. Ordinary
phase/step changes and End turn leave the area open until explicit closure.
Standalone advance/close and non-quick access to the combination remain available.
D-128 shows the quick action only at End of combat, advancing to Postcombat main;
the full path remains available. D-159/D-160 set Undo to one parent node,
regardless of author, replacing whole-invocation/group scope. D-176 makes each
node one individual mutation, superseding combined-node mappings such as D-132.
Groups control playback/navigation and concurrent animation, not publication or
the node pointed to by the client. Missing-area/out-of-combat handling remains
open; button wording/placement is stage 3.

## 5.1 Navigation and screens

**Confirmed entry/setup (D-188–D-190):** login before the main menu, available
registration, Logout/Manage decks/Play, adding users, current games including
pending invites, import-only deck editing, and a lobby with deck selection,
mainboard/sideboard management and both-player Ready. The
[primary contract](accounts-decks-and-lobby.md) distinguishes these requirements
from open details. Friends as a separate menu name is tentative. Other screen
responsibilities below remain proposed unless separately confirmed.

| Screen | Main responsibilities |
| --- | --- |
| Login/registration | Login is required to reach the main menu; registration is available (D-188). Method, session persistence and recovery remain open. |
| Main menu | Logout, Manage decks and Play (D-188). Users can add users; separate Friends naming/placement remains tentative. |
| Games (Play) | Current games including pending invitations (D-190). Completed-game listing, catch-up state, pending work and branch-change presentation remain proposed. |
| Game lobby/invite | Initially a lobby: choose a deck, manage mainboard/sideboard, each player presses Ready; both ready starts the game (D-189/D-190). D-108 requires starter selection and turn 1 at Untap; exact setup/start operations and invitation flow remain open. |
| Deck management | Import card lists only for now (D-189), not a full manual deck editor or an approved export feature. Import resolution and saved-deck details remain open. |
| Table | Shared board, MTG tracking/Untap/Draw variants, manual phase/step choice, turn player/number, and one-tap End turn beside Next step (D-96–D-107). No priority or number editor. D-109 uses manual steps for extra turns with approximate counting, no dedicated action/scheduler. Tracking edits shared state, not review; exact geometry/setup remain open. |
| Card/object inspector | Automatically show readable face art/text despite face-down table presentation, and both printed faces for multi-faced cards; modified state, available actions, attachments/history. Inspection does not change shared presentation. |
| Zone browser / pile actions | Pile tap opens contents and pile context actions together. Contents use card-type category columns with name search/filtering and hold inspection (D-50). Explicit View/Reveal is shared/logged: View shows faces to its actor and backs to the other participant; Reveal shows faces to both. Full-library contents hide overall order; explicit top/bottom-N displays preserve source order rather than type-grouping. Grouping/filtering never reorder stored contents; exact columns and covered opponent layout remain open. |
| Action/group review | Before/after changes, action stepping, interrupt at this boundary. |
| History tree | Forks, active path, archived lines, compare, switch, return, restore. |
| Sync/recovery center | Pending/rejected/failed work, explicit interruptions, retry/export/recover. |
| Game communication | Context-linked messages, response requests, game activity. |
| Settings/storage | Notifications, accessibility, cached data, account and data management. |

D-69 card notes show short text directly on the card; long text may be
ellipsized as the initial presentation, with full text in inspection/editor.
Keep the complete trimmed note in state; exact placement and fitting thresholds
remain wireframe work. Edit note uses the card context menu/multiline editor,
with selection parity and removal on empty trimmed text.

D-51 approves initial contents-browser defaults: one main-type column per card
(Other for unclassified cards), alphabetical ordering within columns, horizontal
column scrolling, and vertical scrolling within each column on narrow screens.
These are a revisable starting point for wireframe review/usability testing, not
a permanent layout constraint. Exact category mapping and covered opponent
presentation remain open.

No essential action may require leaving Android for an external editing tool.

## 5.2 Persistent table context

The table must always make these distinguishable:

- Which participant's perspective and zones are shown.
- Active branch name and whether this view is on it.
- Displayed boundary versus shared head.
- Historical review, local pending state, or synchronized current-board view.
- Connection/synchronization state and number of pending actions.
- Undo/redo availability and any operation awaiting confirmation.

D-159/D-160: Undo moves the shared active pointer one parent back and retains all
descendants. After replacement play, hide ordinary abandoned continuations behind
Show hidden branches; exposure/comment continuations stay visible in full,
including ordinary tails. Covered summaries do not automatically disclose identities.
Reviewing a retained node does not activate it; explicit activation allows forward
return/Redo with its original state/results. Exact pre-fork/forward presentation,
exposure coalescing and non-Undo branch controls remain Q-31; these are not pings
or permanent board markers.

D-94/D-95 distinguish game-wide Chat (not undoable once submitted) from event-
context comments (nodes on the ordinary current tree path, retained after undo).
The path is already a branch; do not add a special comment-only fork workflow.
Undo moves one parent back while retaining the whole comment-bearing abandoned
continuation visibly (D-160), without a cutoff. Exact event/group association,
revision/edit/delete, pending chat delivery, and editor submission remain open.

Use text/icons as well as color. A prominent “Return to active game” control is
available when viewing history or an inactive branch; it must not discard drafts.

## 5.3 Catch-up flow

**Confirmed D-177:**

1. Start at the player's last local state. If the opponent interrupted that
   continuation, animate rollback fairly quickly along its mutations to the fork.
2. Play forward along the new continuation automatically or step by step. A
   group is one playback step whose changes animate concurrently; the underlying
   state still advances through individual mutation nodes.
3. Let the player interrupt automatic playback. When paused, show the horizontal
   event timeline at the bottom; dragging left/right selects an exact mutation
   boundary, including one inside a group.
4. Continue playback or explicitly choose to continue play at that historical
   node before editing. D-179 requires deliberate continuation intent; navigation
   alone does not supply it. Undo supplies it automatically. Ordinary edits
   otherwise use D-187 current-state acceptance with limited move/Stack guards,
   not automatic historical forks.
5. At the latest mutation, identify the current state without implying priority
   passing, legality approval or consent.

This supersedes the old proposal that normal Next must advance one mutation
instead of one group. Exact animation timing, automatic/manual default and final
control/accessibility layouts remain design work. Pausing playback never pauses
publication or loses pending work. See [the primary resume contract](history-and-undo.md#312-resume-playback-and-exact-timeline-navigation-d-177).

If a branch changes during review, show a persistent non-destructive notice
with “Go to divergence” and “Keep inspecting this line.” Avoid automatically
changing the board under a user's finger.

## 5.4 Interruption flow

1. Navigate to the boundary immediately before the action to interrupt.
2. Explicitly choose to go back and continue from that state, then perform the
   reaction using ordinary board controls. This deliberate UI action supplies
   an interruption flag and chosen boundary in the server message (D-179).
   Undo supplies continuation intent in the background without another prompt.
3. The first completed reaction applies locally and automatically synchronizes
   branch creation/activation. Show the preserved original line and offer
    explicit **Return to original continuation** and **Delete branch and return**.
    Normal Undo moves to the parent/fork; it does not jump to the original future.
4. Further reaction actions synchronize as they happen, grouped automatically.

The UI must distinguish inspection from deliberately continuing here. Exact
control naming/layout remains stage 3; a historical cursor or passive warning
alone is not authorization to branch. Ordinary hold
inspection alone does not create a branch. Explicit View/Reveal is logged;
its exact history/branch association remains Q-28. Optional naming and
explanations never block play.

An interruption with no reaction actions may still be useful as a rollback or
response request. Exact naming and whether an empty branch is allowed must be
decided before implementation.

## 5.5 Editing and grouping flow

- Provide direct quick controls for frequent operations, alongside inspection
   and action sheets for less common operations. Do not force every tap/untap or
   life adjustment through an inspector and confirmation dialog.
- D-52 distinguishes optional bottom-right card text from colored counters with
  their own text on cards and players. Card text supports quick context
  inc/dec/set; numeric counter text also supports quick inc/dec/set. Both accept
  arbitrary text. D-58/D-61 settle the palette and inline counter-menu controls;
  D-62 settles initial integer/slash-pair shortcuts; exact geometry remains open.
  Earlier +/- tracker and tap-number layouts were not approved.
- D-53 clarifies that quick-action availability is derived from patterns in the
  current plain text. A corner string such as `2/3` is not separate P/T fields;
  quick actions rewrite the same string. D-62 offers +/-1 for whole numbers,
  left/right/both +/-1 for corner slash-pairs of whole numbers, and Set only for
  everything else initially. No zero-clamping. Exact grammar/formatting and
  numeric limits remain to be specified.
- D-63 keeps the context menu open after corner-text +/- and completed Set.
  Empty trimmed corner text leaves a blank overlay, not the default; Reset
  corner text explicitly restores the database default without changing counters
  or other state. Printed art is unaffected. Exact Reset menu lifetime remains
  open.
- D-54 keeps the context menu open after counter increment/decrement, allowing
  repeated automatically queued edits. A successful context Move to Graveyard
  dismisses it. Other actions require explicit menu-lifetime decisions; remaining
  open is not a draft/Send gate.
- D-58 selects red/blue/green/yellow/purple/gray counters, one per color on each
  card/player. Show numeric `0`; completed empty text removes the counter.
  Temporary unfinished editor input does not itself publish deletion. Exact
  styling remains open. D-59 trims all user-entered
  text on completion, so whitespace-only counter input removes it. Preserve
  internal spacing/newlines and apply text-pattern matching after trimming.
- D-67 replaces D-60's initialization/color-choice flow with per-color +1/-1/Set,
  no Add/Initialize. No recolor action is needed. D-61 confirms inline card-menu
  controls and player-panel taps for equivalent controls. Counter +/- and
  completed Set retain/return to the menu; empty trimmed Set removes the counter,
  not the color's controls. No separate Counters submenu; exact hit regions and
  formatting remain open. D-68 creates absent counters directly as `1` for +1 or
  `-1` for -1; nonempty Set creates/updates and empty removes or leaves absent.
- D-55/D-56 remove card counters and restore database-default corner text on
  entry to configured Graveyard/Exile/Library. For creatures the default is
  printed P/T text. D-64 conditionally approves loyalty/defense defaults where
  the eventual source supplies them, with empty defaults for no applicable
  printed value. D-65 uses blank editable fallback for missing metadata and
   retained per-game/per-face defaults for provider-independent Reset. Reset is
   an online game action under D-182 and uses the selected
  face; switching faces alone preserves current text. Provider/source mapping
  remains open. Preserve unrelated fields and stable identity.
- Multi-select has visible selected count and a clear cancel action.
- D-81/D-82 add table Create -> Token with searchable catalog choice. Choosing
  creates one token with a new identity, automatically placed; no quantity,
  placement preview/picker, or extra confirmation. Ordinary drag can reposition
  afterward. D-83/D-84 make Clone create token copies with current face/cover/
  orientation, corner text, counters, notes/defaults, but no links/pings; one
  per selected source. Omit Repeat last token. Exact automatic location,
  catalog-instance initial state, association copying, and offline/missing data
  remain open. No creation from card-text inference.
- D-84/D-85 remove tokens moved into configured Library/Graveyard/Exile from the
  current table rather than retaining them in the pile. This includes clones of
  ordinary cards. All move controls share this behavior; mixed sets move ordinary
  cards normally and remove tokens. D-174 restricts context Remove to tokens and
  token selections, without a pile move or routine confirmation. Non-token cards
  remain in the game and move between locations; do not offer deletion through
  any normal, bulk, correction or projection path. Queue/sync and retain
  undo/history; exact mixed-set records, routing/menu lifetime, and reference
  presentation remain under review.
- D-86 provides a Blank token entry in the ordinary catalog, not a separate
  custom-token form. Create one through the same automatic placement flow, then
  use normal corner text/counters/notes, Clone, or Remove. No image upload or
  dedicated custom-token editor is approved; blank presentation/initialization
  remains detailed design work.
- D-87 adds table Roll die (one die, default six sides, optional sides control)
  and Flip coin (one heads/tails result, no configuration). Automatically queue/
  sync actor/request/result to shared history, reusing results for review/redo.
  No automatic card/counter changes. Exact picker geometry, bounds, source/
   algorithm and display remain under review; new invocations require a connection
   in initial delivery (D-182).
- D-92 makes new Roll die/Flip coin invocations fresh draws, including after
  undo, without requiring an opponent action. This replaces D-88 seed resets.
  History/review/redo and same-operation sync retries retain recorded results.
   Equal results by chance are valid; exact generator/source/online protocols
  remain under review. D-93 confirms new Shuffle also samples a fresh order,
  while review/replay/redo/retries retain its recorded order. No new shuffle
  shortcuts or hidden-order exposure classification is implied.
- D-74 makes pinging a persistent interaction mode, not a one-shot command from
  an action menu. Mode entry alone creates no ping/shared edit. Durable pings
  retain recorded context. D-75 makes pinging toggleable within one group/event;
  its pings disappear from later board contexts but remain viewable at that step
  in the event tree, including downloaded offline review. D-76 makes destination
  taps toggle ping presence and exits input mode on event change. Revisiting an
  event displays its recorded pings without re-enabling input mode. D-77 makes
  Ping and selection-edit modes mutually exclusive while retaining selection;
  destination taps only ping the touched destination, suppressing ordinary tap
  effects. One standard marker, no label/style picker or required typing. D-78
  adds a Ping context action. D-79 makes it directly toggle the individual
  card/player without changing mode. D-80 displays only Ping or Unping according
  to the card used to open the menu and applies it uniformly to the selected set,
  rather than testing whether any/all selected cards are pinged. Location
  matching, context routing/menu lifetime, and action mapping remain open.
  These are not persistent binary links.
- D-66 gives mass selections the same card-action controls as individual cards,
  applying the action across the selected set with visible scope/count. Relative
  edits use each card's own value and corner Reset each card's own default.
  D-67 skips targets where the individual action is unusable and disables a mass
  control only when none are applicable. D-68 resolves missing-counter arithmetic;
  mixed rendering and context targeting still require Q-30 outcomes; no reduced menu.
- D-70 adds card context Link -> pick a card/player endpoint. Endpoint picking
  consumes the tap without ordinary tap/untap or player-menu effects. Persistent
  connectors follow endpoint positions without moving linked cards. D-71 uses
  binary links with no labels/styles, one per selected source to a picked target,
  and removal via a card's Links list. D-72 makes links undirected/unique per pair;
  repeated Link preserves an existing connection instead of toggling it off.
  Exact geometry/cancel/no-op history remain open; links are not pings/attachments.
- D-73 keeps initial movement tooling simple: no separate Attach/follow-group
  controls. Binary links do not make neighbors move; existing multi-selection
  handles deliberate joint movement.
- Drag/drop is the primary card-movement interaction, with grid/slot snapping.
  Confirmed: drag one card unless multiple are selected; special-pile drops go
  on top; dropping onto an ordinary card forms/joins its visual stack. The
  double-tap sheet includes Move to at least the top/bottom of library,
  graveyard, and exile. Detailed controls are in the primary board contract;
  selection gestures remain open. Explicit Move/reorder controls also support
  accessibility and precision.
- A compact activity strip shows the automatic current group, pending status,
  and undo. No gameplay Send/Done button is required to share actions.
- Group labels and boundaries are optionally editable after automatic
  publication; labels never alter game state.
- Completed actions are already queued when leaving a game. Navigation must not
  add a keep/send/discard prompt; synchronization continues subject to Android
  limits and resumes automatically when possible.
- Incomplete form input and pending/failed work survive process death. A form
  that would lose meaningful unfinished input offers local recovery, not a
  generic publication gate.

## 5.6 Undo and destructive-looking actions

- A persistent undo entry point complements any transient snackbar.
- Show the exact object/action/branch affected, not merely “Undo.”
- Normal Undo moves one parent node back, regardless of author; composite actions
  may require multiple presses. It is not selective historical rollback.
- Branch “Delete” wording must disclose “recoverable from archived branches.”
- Undo captures a specific node-to-parent transition (D-161). If it becomes invalid,
  silently discard it and reconcile local state: no toast, warning, notification,
  confirmation or retry chore. Never retarget it to unseen work. The same rule
  applies after reconnect; non-Undo branch-control policies remain open.
- Redo that would switch branches is labeled accordingly.

## 5.7 Offline, pending, and conflict states

**Confirmed D-182:** initial delivery requires a connection for game actions and
shared controls, including Undo and branch activation. Offline use is read-only
for downloaded games/history; there is no offline gameplay queue to replay later.
Keep personal review/navigation separate from shared controls.

**Confirmed D-183:** use a small local send queue, send online actions concurrently
and reconcile each action's success/failure individually. Apply interactions
locally immediately; normal controls must not routinely wait for previous server
acknowledgements. One rejection does not cancel another valid action or the whole
queue. Use D-180 animations for individual rejections and incoming changes; keep
pending versus accepted state honest without making latency a blocking workflow.
Exact queue sizing/saturation, reference handling and visual composition
remain open, not a reason to impose stop-and-wait on normal play.

D-184 preserves each client's action order through ordered sending and server
arrival-order processing, without sequencing dependency metadata. Multiple actions
remain in flight; sending and local feedback never routinely wait for previous
acknowledgements. Other-player arrivals may interleave, and every action still
receives its own validation/result even after an earlier rejection.

**Confirmed D-185:** reconcile the current board against accepted history plus
remaining valid local predictions, animating only the actual correction. Preserve
accepted local/opponent changes and valid pending predictions; correct genuinely
invalid later predictions too. A failed move followed by a successful tap moves
the card back while keeping it tapped, without a visible untap/replay or stale
snapshot restoration. Acknowledgement alone adds no animation. Exact scheduling
and reduced-motion presentation remain later design; D-177 deliberate history
traversal/interruption playback remains unchanged.

Use explicit states: downloaded/current, offline/read-only, incomplete input,
online-pending/sending, synchronized, rejected, explicitly branched, and uncertain
delivery/failed. Exact connection-readiness and mid-request-loss flow remain open;
do not equate an unknown server outcome with rejection.

The user can read cached cards and games offline, not continue play. Unavailable
images fall back to text; an unavailable card definition must not prevent inspection of
an existing object's stored essential data. Joining an unknown game or importing
unknown catalog entries may require connectivity and must say so.

An online-pending action is not labeled delivered. D-187 normally applies ordinary
edits to current state unless impossible, without previous orientation/text/Set
comparisons. Move source/destination guards and Stack-state comparison are
exceptions: Stack additions send the client's current Stack state and reject if
the server's Stack state differs. No seen/opponent detector, priority model or
whole-game-head lock. D-179 still prohibits accidental branches from stale views
or concurrency.
D-180 requires immediate local state updates to hide latency;
on rejection, animate the predicted change backward as if undone. The animation
explains the correction without a required text explanation or acknowledgement.
This does not publish a shared Undo, create a branch or undo the opponent's work.
Animate incoming opponent mutations too; preserve personal historical cursors.
Preserve recoverable rejected work; exact request recovery, prediction bookkeeping
and animation scheduling remain open within D-185's confirmed behavior.
D-161's silent stale-Undo discard remains specific to Undo, not a blanket policy
for rejected ordinary actions.

An explicitly requested historical continuation makes its branch current and
preserves the displaced line for inspection/return. Normal Undo moves one parent
back, not automatically to the displaced future. Retry does not duplicate actions
or branch activation. Preserve personal review positions and local work when the
active line changes; detailed queued-continuation handling remains open.

## 5.8 Accessibility and device behavior

Required proposals for approval:

- TalkBack names, roles, and actions for zones, cards, counters, branch changes,
  selections, and playback controls.
- Scalable text; adequate contrast; minimum platform-recommended touch targets.
- No color-only meaning, gesture-only commands, or time-limited-only recovery.
- Reduced-motion option and readable non-animated change summaries.
- Portrait-first phone layout; landscape, tablets, foldables, split-screen, and
  large text must preserve access to every operation.
- State survives rotation, backgrounding, process termination, and sign-in
  refresh without losing drafts.
- System Back exits transient selection/sheets first, then screen navigation;
  it does not undo game actions.

## 5.9 Notifications

Notify for grouped activity, explicit interruptions/branch switches,
invitations, and explicit response requests, subject to preferences.
Consolidate related actions; do not send one push per primitive change.
Notification coalescing must not delay action synchronization or wait for an
explicit group-completion tap. Each committed action is available immediately.

Notification text does not reveal covered card identities by default. Deep links
open the referenced game/boundary but check current branch status and access.
Stale notifications are harmless pointers to history, not instructions to replay
an operation. Push failure cannot affect game-state correctness.

## 5.10 Low-interaction design contract

**Confirmed principle:** minimize taps and other user actions, not merely screen
count. Include scrolling, navigation, typing, and repetitive selection in the
cost of a workflow. Do not move interaction cost into mandatory setup chores.

Proposed rules:

- No routine Save, Send, confirm-action, start-group, end-group, or branch-name
  step for game edits.
- One activation for a directly exposed quick action such as tap/untap, a life
  increment/decrement, next/previous review action, or latest safe undo.
- Multi-select/bulk operations and remembered display preferences reduce
  repetition. Defaults are visible and editable; card text, area names, and
  pings never cause inferred game-rule choices or edits.
- Convenience actions may combine related field edits but must preserve distinct
  response boundaries as defined in the history contract. Fewer taps must not
  remove an opponent's chance to react.
- Use undo for routine recoverable mistakes. Reserve blocking confirmation for
  irreversible data erasure or other specifically documented risks. Ordinary
  concurrent edits validate at the current head and append or reject; historical
  branching requires explicit continuation intent (D-179).
- Provide discoverable, accessible alternatives to shortcuts and gestures. A
  shortcut's tap savings cannot make the normal flow unusable.
- Every common journey receives an explicit starting state and interaction
  budget in its wireframe/action contract. Evaluate the whole journey, not only
  the final button.

The main card gestures are confirmed in the board contract. Exact gesture timing,
special-pile hit targets, additional shortcuts, and journey budgets remain design
work. Double tap must not also emit a single-tap game edit; hold must not tap or
drag the card accidentally.
