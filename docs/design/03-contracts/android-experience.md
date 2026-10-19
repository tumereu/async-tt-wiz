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

D-191 additionally confirms that changing your deck/mainboard/sideboard clears
your own Ready, and lobby deck changes do not modify the saved deck.

D-195 confirms invitations to added users, recipient Accept/Decline in Play,
and opening the lobby on acceptance, without automatically readying or starting.
D-196 confirms username search and saving users as contacts without mutual
friend-request approval; this does not bypass game-invitation acceptance.
D-197 confirms username/email/password registration, email/password login,
password reset by email, and usernames—not emails—visible to other users.

D-218 confirms the same account on multiple devices, with saved decks, contacts
and games synchronized between them. Preserve saved-deck versus lobby/game-copy
isolation. Exact simultaneous-edit conflicts, device/session revocation, sync
timing and transfer of personal device state remain open; see the
[multiple-device contract](accounts-decks-and-lobby.md#multiple-devices-and-account-data--d-218).

| Screen | Main responsibilities |
| --- | --- |
| Login/registration | Login before main menu (D-188). Register with username/email/password; login with email/password; forgotten-password reset by email (D-197). Stay logged in across restarts; after session expiry, ask for login again, then return to the same game and recover pending already-sent actions without manual resend/duplicates (D-217). D-241 removes private downloads on explicit Logout, preserving protected already-sent recovery records and forbidding cross-account access. Validation, verification, exact session/reset/recovery mechanics and expired-session navigation need completion. |
| Main menu | Logout, Manage decks and Play (D-188). Username search/add saves contacts without mutual approval (D-196); separate Friends naming/placement remains tentative. |
| Games (Play) | Current games including pending invitations, with recipient Accept/Decline and lobby entry on Accept (D-190/D-195). Finished-session history archives/review are optional future work, not R1 (D-214). Catch-up state, pending work and branch-change presentation remain proposed. |
| Game lobby/invite | Invite/accept into lobby (D-195), prepare mainboard in Library and sideboard in separate Sideboard, other card areas initially empty (D-202). Select either player or Random as starter (D-201), both press Ready (D-189/D-190). Start shuffles Libraries and initializes life 20 without a required starting-life setting or automatic draw (D-200); begin turn 1 at Untap (D-108). Sideboard is unavailable during play, including browser/move paths (D-202). Remaining setup/lifecycle details are open. |
| Deck management | Import common MTG text; flag unresolved names, preserve input and block saving until resolved (D-189/D-198). Name/rename decks, choose a cover from their cards, and re-import with Replace/Save as new (D-199). Delete saved decks from the collection; both editing/deleting leave selected lobby/game copies unchanged (D-221). D-241 excludes fuller deck editing/export and deleted-deck trash/restore. Approved-import/metadata, confirmation presentation, server retention and concurrent-race contracts need completion. |
| Table | Shared board, MTG tracking/Untap/Draw variants, manual phase/step choice, turn player/number, and one-tap End turn beside Next step (D-96–D-107). No priority or number editor. D-109 uses manual steps for extra turns with approximate counting, no dedicated action/scheduler. Tracking edits shared state, not review; exact geometry/setup remain open. |
| Card/object inspector | Automatically show readable face art/text despite face-down table presentation, and both printed faces for multi-faced cards; modified state, available actions, attachments/history. Inspection does not change shared presentation. |
| Zone browser / pile actions | Pile tap opens contents and pile context actions together. Contents use card-type category columns with name search/filtering and hold inspection (D-50). Explicit View/Reveal is shared/logged: View shows faces to its actor and backs to the other participant; Reveal shows faces to both. Full-library contents hide overall order; explicit top/bottom-N displays preserve source order rather than type-grouping. Grouping/filtering never reorder stored contents; exact columns and covered opponent layout remain open. |
| Action/group review | Before/after changes, action stepping, interrupt at this boundary. |
| History tree | Forks, active path, archived lines, compare, switch, return, restore. |
| Sync/recovery center | Pending/rejected/failed work, explicit interruptions, retry/export/recover. |
| Game communication | Context-linked messages, response requests, game activity. |
| Settings/storage | Notifications, accessibility, cached data, account and data management. |

**Ending play (D-211)** uses a proposed win/loss/draw as shared, undoable board
state. The other player must accept; acceptance returns both players to the lobby.
No win-history/scorekeeping feature is required. This supersedes D-210's unilateral
confirmation-dialog/non-undoable End game. D-212 separately makes acceptance/return
to lobby non-undoable, not the pending suggestion. Concurrent incompatible result
actions follow normal server ordering/applicability: the first applicable action
takes effect and the later incompatible request is discarded (D-239), not a
replacement policy. Finished-session history archives/review are optional
future work, excluded from R1 (D-214), not a required archive screen or lobby path;
current-game history remains included. D-213 restores
last lobby-prepared decks/mainboard/sideboard on return, clears both Ready and
requires both players to ready again for a fresh game. Starter-selection retention/
random resolution across games remains open. Do not infer an extra confirmation dialog.

For the inviter, a pending invitation is already an open lobby: allow deck
preparation/Ready before acceptance, and use Cancel game to cancel its pending
invite. Recipient Decline retains that lobby for reinvitation; Accept opens the
same lobby for the recipient without automatically readying them (D-208).

Lobby preparation permits free transfers between mainboard and sideboard, without
legality, size or one-for-one constraints (D-203). These clear own Ready and leave
saved decks unchanged (D-191); Sideboard remains unavailable during play (D-202).
Changing the lobby starter selection likewise clears only the changing player's
Ready, preserving the other player's (D-204). D-205 permits manual withdrawal until
start and requires your own selected deck to ready, without legality/size checks.
Accepted Ready persists when leaving/disconnecting; both players need not be
online together. Server arrival order settles start-versus-change; late lobby
changes are rejected after start and retries never start/shuffle again. See the
[primary setup contract](accounts-decks-and-lobby.md).

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

**Latest revision D-224:** selective removal/reinsertion requires every subsequent
mutation's relevant-state `(from, to)` tuple to stay identical across timelines,
within a hard suffix-work cap N. Information-revealing targets are not selectively
undoable. D-230/D-231 allow Undo only for your most recent still-applied action,
even after opponent activity; never skip a blocked own target or undo the opponent's.
Redo is last-undone-first, blocked entries disable it without skipping, and only
your own new gameplay edit clears your queue (D-227–D-230). Per-mutation relevant
state, N/counting, pointer/view-follow behavior, queue device scope/insertion
anchors and final control mapping remain open. The
earlier parent-step/remembered-head controls below are rewind context, not the
complete current definition of Undo/Redo; live action acceptance is unchanged.

**D-232:** Undo/Redo edit the existing tree by removal/reinsertion, without creating
branches or keeping the pre-Undo path as an alternative. Removed mutations are
available in the Redo stack, not a required explorer branch. Effects on review
cursors, comments/pings, cached states and existing side branches remain open;
do not silently switch historical context or assume an immutable old path remains.

Earlier D-159/D-222 rewind model: Undo moves the shared active pointer one parent back and remembers
the last active head; Redo follows the path toward that head with original
states/results. Retain all descendants. Changed play branches and becomes active.
A **graphical tree explorer** allows review and explicit activation of any retained
branch/node, including internal nodes/root; reviewing alone does not activate.
Hide only single-player inactive continuations without exposure or event comments
behind a separate Show hidden branches toggle. Mixed-player/exposure/event-comment
continuations stay visible in full (D-223), even for a one-author/no-reveal comment
path. Covered summaries do not automatically disclose identities.
Exact pre-fork/nested presentation, remembered-head user/device scope, concurrent/
stale Redo/activation and exposure coalescing remain open; these are not pings or
permanent board markers.

D-94/D-95 distinguish game-wide Chat (not undoable once submitted) from event-
context comments (nodes on the ordinary current tree path, retained after undo).
The path is already a branch; do not add a special comment-only fork workflow.
Undo moves one parent back while retaining the whole comment-bearing abandoned
continuation visibly under D-160, explicitly reaffirmed by D-223 even without
exposure/other-player actions. D-240 keeps chat simple: submitted messages have no
Edit/Delete, corrections use another message, and no expansions are planned.
D-241 excludes dedicated event-comment Edit/Delete; correct with eligible
ordinary Undo or another comment. Exact event/group association, pending chat
delivery and editor submission remain separate work. Other proposed screens/tools
are subject to D-241's R1 exclusions, not approved by this inventory.

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
   alone does not supply it. D-232 Undo/Redo edit the tree without supplying it. Ordinary edits
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
   D-232 Undo/Redo are separate tree edits, not this branch-creation flow.
3. The first completed reaction applies locally and automatically synchronizes
   branch creation/activation. Show the preserved original line and offer
    explicit **Return to original continuation** and **Delete branch and return**.
     D-232 Undo edits the existing tree; it is not Return to original continuation.
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
  Confirmed: drag one card unless multiple are selected; Library drops go on top;
  Graveyard/Exile drops add unordered membership; dropping onto an ordinary card
  forms/joins its visual stack. The double-tap sheet includes Move to Library
  top/bottom, Graveyard, and Exile. Graveyard/Exile have no order-related actions,
  including visual-placement variants (D-226). Detailed controls are in the primary
  board contract; selection gestures remain open. Explicit Move and, for ordered
  containers only, reorder controls also support accessibility and precision.
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

**D-224 scope:** the parent-step/remembered-head entries below describe the
earlier rewind controls. Selective removal/reinsertion uses its stricter suffix
tuple test; do not assume the same control mapping or stale-request behavior.

- A persistent undo entry point complements any transient snackbar.
- Show the exact object/action/branch affected, not merely “Undo.”
- Normal Undo moves one parent node back, regardless of author; composite actions
  may require multiple presses. It is not selective historical rollback.
- Branch “Delete” wording must disclose “recoverable from archived branches.”
- Undo captures a specific node-to-parent transition (D-161). If it becomes invalid,
  silently discard it and reconcile local state: no toast, warning, notification,
  confirmation or retry chore. Never retarget it to unseen work. The same rule
  applies after reconnect; non-Undo branch-control policies remain open.
- D-222 Redo follows the remembered last active head, not an arbitrary child or
  mandatory fork picker. Deliberately selecting another branch/node is a separate
  graphical-tree activation; exact control labels/layout remain later UI work.

## 5.7 Offline, pending, and conflict states

**Confirmed D-182:** initial delivery requires a connection for game actions and
shared controls, including Undo and branch activation. Offline use is read-only
for downloaded games/history; there is no offline gameplay queue to replay later.
Keep personal review/navigation separate from shared controls.

**Confirmed D-215:** while disconnected, show a **Reconnecting...** dialog that
**blocks gameplay actions**, including shared Undo/branch controls. Do not create
offline gameplay behind it. This connection-loss dialog is not a routine gate
while online actions await acknowledgements, and it does not mean in-flight
requests were rejected. Downloaded read-only access remains in scope; exact
dialog navigation/dismissal and reconnect readiness remain open. D-216 separately
confirms automatic recovery of already-sent requests below.

**Confirmed D-216:** after reconnecting, automatically recover already-sent online
actions without manual resending or duplicate execution. Recover actual outcomes
using original request identities/conditions; an already-committed action/random
result must not execute/sample again. Use normal difference-only reconciliation
for accepted/rejected outcomes, without retrying a rejection as a fresh action
or cancelling other valid work. This does not enable offline actions. Exact
recovery protocol/ordering/readiness and buffered-but-unsent handling remain open.

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
delivery/failed. Exact connection readiness and mid-request-loss recovery mechanics
remain open within D-216's automatic/no-duplicate/no-manual-resend behavior;
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

**Confirmed D-219:** provide **push notifications for new invitations and grouped
opponent activity**, **without revealing covered card names**. Consolidate related
opponent activity rather than sending one push per primitive change. Existing
immediate-publication rules remain: coalescing must not delay synchronization or
wait for a group-completion tap; each committed action is available immediately.

**Confirmed R1 settings boundary (D-220):** use **Android's notification channel
settings** for notification control in R1. Both proposed **in-app global mute**
and **in-app per-game mute** settings are **post-R1**, not release requirements.
Respect the user's Android notification settings; do not require an app-specific
mute/settings screen for R1. This does not defer D-219's push notifications or
require a separate Android channel for every game.

Exact grouping window/cadence, notification permissions/defaults, Android channel
categories, foreground suppression and multiple-device routing remain open.
This approval does not separately confirm additional notification categories for
explicit branch switches or response requests, or define an unread/read model.

**Remaining proposals:** additional branch-switch/response-request alerts and
their preference behavior remain unapproved; do not turn these into R1 in-app
settings contrary to D-220. Notification previews should avoid covered identities
beyond the confirmed no-covered-card-names rule. Deep links open the referenced
game/boundary but check current branch status and access.
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
