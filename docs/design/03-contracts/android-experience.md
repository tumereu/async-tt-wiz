# 5. Android experience

Status: proposed interaction architecture except confirmed requirements noted
below, including D-177's resume/playback and bottom timeline behavior. D-290/D-291
authorize a limited [UI review packet](../02-experience/ui/README.md): the original
non-gameplay pilot plus a live/history gameplay extension, in portrait and
landscape with compiled review documents. Its layouts remain reviewable;
full gameplay visual-state coverage/final design and the UI gate remain incomplete.
D-273 selects Android
10+ (API 29), signed APK distribution and manual updates as specified below.

Confirmed card gestures and proposed drag/pile/intent details are defined in
[Game board and controls](board-and-controls.md), the primary tabletop-input
contract. In normal mode, single tap opens Actions, double tap taps/untaps outside
Hand, hold
inspects, and drag moves with snapping (D-267). Select/Ping retain single-tap mode actions; removing their
double-tap recognizers is confirmed by D-281. This corrects the old opposite card mapping.
In Hand, double tap sends lands to the land row, permanent-only cards to Stack
then auto-resolves to Battlefield as a separate history group, and other cards to Stack.
The [visual action language](visual-action-language.md) defines the proposed
Stack area, action-linked ping controls, literal grouped log presentation,
and optional notes/chat. Ordinary flow should not require typing what just
happened, and the engine must not guess what the actions mean in a game's rules.
D-112–D-114 additionally require combat/Stack projections, visual attacker/blocker
grouping (D-139), and contextual Stack actions. Their behavior is being specified in
[the primary contract](combat-stack-and-projections.md). D-115 reserves new
UI drafts, final layouts, and concrete control choices for delivery stage 3;
D-290/D-291 explicitly permit the limited pilot and gameplay extension without
approving final geometry, passing the full UI gate or authorizing application code.

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
card actions are now settled by D-242: keep existing sources linked across
locations, including Graveyard/Exile; only token disappearance freezes read-only
last-live source information, without source-card editing or recreating a token.
Stack projection creation from Graveyard/Exile is supported. Exact rendering and
record mappings remain UI/technical work. D-126 keeps surviving blockers
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
the full path remains available. Undo removes one eligible latest-own mutation,
not a whole invocation/group (D-231/D-232). D-176 makes each
node one individual mutation, superseding combined-node mappings such as D-132.
Groups control playback/navigation and concurrent animation, not publication or
the node pointed to by the client. Missing-area/out-of-combat handling remains
open; button wording/placement is stage 3.

## Android support, distribution and upgrades — D-273

**Confirmed:** initially distribute versioned **signed APK downloads**, without
requiring Google Play. Users install updates through Android's normal installer;
there is no custom in-app updater requirement. GitHub Releases is an example host,
not a separately selected release-hosting service or production deployment task.
Keep a stable package name/signing identity so compatible updates preserve app
data; keep signing keys out of Git and the distributed APK's embedded secrets.

- **Minimum Android 10 (API 29).** Target phones and tablets, including otherwise
  supported devices without Google Play services. Verify dependency compatibility
  against that floor; do not silently raise it to match a library/test tool.
  Exact compile/target SDK, device matrix and dependency versions remain technical
  work; this does not promise every device meets measured performance targets.
- Compatible upgrades retain valid login, accepted downloads/definitions/results
  and saved personal data through verified migrations, without uninstall/data clear
  or routine login. D-278 permits outgoing-memory loss on process replacement;
  admitted server work still arrives once and unadmitted work is not recreated.
  D-286 requires no separate speculative-disclosure recovery. D-268 explicit
  revocation/key loss remains distinct from upgrading.
- Version the network protocol separately from APK releases. Different app
  versions may participate together when their protocol/semantic behavior is
  compatible; matching APK version numbers are not a game-join requirement.
- When an update is required for incompatibility, explain it and block unsupported
  requests before mutation, not erase pending work, fabricate rejection of an
  unknown-outcome request or silently reinterpret it. After a compatible upgrade,
  recover original outcomes/identities under the existing rules. Preserve already
  supported downloaded read-only access rather than forcing a destructive reset.

Define supported upgrade/compatibility paths and verify migrations. Exact wire
negotiation/error fields, compatibility windows and update-message presentation
remain technical/UI work, not indefinite support for every old protocol or a new
automatic updater. See [release/compatibility design](../04-technical/android-distribution-and-compatibility.md).

## 5.1 Navigation and screens

**Confirmed entry/setup (D-188–D-190 / D-292):** login before the main menu, available
registration, shared Menu/Games navigation with fixed Logout footer, adding users, current games including
pending invites, import-only deck editing, and a lobby with deck selection,
mainboard/sideboard management and both-player Ready. The
[primary contract](accounts-decks-and-lobby.md) distinguishes these requirements
from open details. D-292 selects Contacts as the destination name. Other screen
responsibilities below remain proposed unless separately confirmed.

**Confirmed fullscreen/presentation — D-292:** no dedicated app-title bar,
Android status bar or permanent system-navigation strip; keep necessary hamburger,
gameplay phase/history controls compact in the screen. D-294 removes all app
Back/home arrows, keeping native Android Back. Non-dialog screens have no X/close
button; only dialogs/popups may have dismiss controls, and table popups float
without close buttons. Real errors use the floating transient error, never inline
text that shifts layout. Loading reserves space or uses a full-area placeholder;
deck-import validation has its own fixed inline error area. Do not reserve
mock OS-bar space. Safe cutout placement and transient OS/accessibility/keyboard
behavior still apply. App name is **Async Tabletop Wizards**, subtitle
**Specialized tabletop simulator for asynchronous wizardry**. Sign-in uses the
same centered bounded card in both orientations, scrolling at short height rather
than a landscape intro/form split. Remove redundant explanatory/marketing copy,
including reset-session/sign-in helpers, without changing actual authentication.
Landscape non-gameplay shows persistent Menu/Games navigation; portrait and all
gameplay use its hamburger drawer. See the primary navigation contract and
[cumulative UI feedback](../02-experience/ui/feedback.md).

**D-293 refinements:** no top Back on authentication screens (in-form links/native
Back suffice); provisional ATW logo above main-menu options. Type-matching icons
on indented navigation/Logout and matching main-menu destinations; top-level
categories remain text. Buttons normally use icon + label, or accessible icon-only
actions in the tight gameplay area; state values are not removed. Game rows have
required game names (D-294), opponent/date/state details and a right-chevron Open; long press/accessibility opens
Cancel for setup or Delete for active play. Active deletion needs the extra
both-players/no-restore confirmation and server lifecycle rules in the primary
contract. Reinvite belongs inside the lobby. Deck import is a bottom action;
existing-deck Edit → Import decklist defaults to Overwrite, with additive Merge.
Deck rows have small art after quantity and full inspection on press. Preparation
uses distinct one/all drag handles, with equivalent native accessibility actions,
not persistent Move buttons. These are requirements for later native verification,
not a claim the inert captures implement gestures or accessibility.

**D-294 title/selection refinements:** hamburger shares a row with the visible title,
also in landscape (persistent navigation still replaces it on non-gameplay screens).
System Back leaves unfinished import/editor input unsaved; lobby Cancel is a game action,
not a screen close control;
lobby title is the game name and Reinvite belongs in the opponent slot. No Ready
leaving helper. Dialog close X is top-right; related button pairs fill 50/50.
Cover selection has a border, no checkbox; deck deletion says only it cannot be
undone. Deck picker uses personal border selection and one bottom confirmation,
not per-row Select. Preparation **1 / ∞** markers are 24 px wide, an explicit compact
drag-target exception with native accessible equivalents, not overlapping invisible
targets or permission to shrink ordinary 48 px buttons. The current review retires
G00 and shows a real table under U01; historical raw artifacts remain provenance.
New creation requires a name with fantasy adjective + thing suggestions; the
   server's creation date survives later incarnations; rows display last activity.

**D-294 follow-ups:** the compact **1 / ∞** affordances are tappable buttons as
well as drag origins, with gesture arbitration preventing double transfers. A
reserved plain-text slot shows the latest actual displayed move and fades over
about 1–2 s without shifting content; new moves replace/restart it. When a tap
empties a row, quickly fade that row and slide the following rows upward. That
intentional list reflow is not feedback-driven layout movement. Preserve captured
targets through animation, immediate request/prediction and honest reconciliation;
native accessibility/reduced-motion equivalents remain required.

D-191 additionally confirms that changing your deck/mainboard/sideboard clears
your own Ready, and lobby deck changes do not modify the saved deck.

D-195 confirms invitations to added users, recipient Accept/Decline in Play,
and opening the lobby on acceptance, without automatically readying or starting.
D-196 confirms username search and saving users as contacts without mutual
friend-request approval; this does not bypass game-invitation acceptance.
New game opens Choose opponent, permits inviting contacts only, and contacts can
be removed. Deck import is paste plus Save with fixed-area inline errors; the
saved deck view is the review, not a separate pre-save step.
D-197 confirms username/email/password registration, email/password login,
password reset by email, and usernames—not emails—visible to other users.

D-218 confirms the same account on multiple devices, with saved decks, contacts
and games synchronized between them. Preserve saved-deck versus lobby/game-copy
isolation. Exact simultaneous-edit conflicts, device/session revocation, sync
timing and transfer of personal device state remain open; see the
[multiple-device contract](accounts-decks-and-lobby.md#multiple-devices-and-account-data--d-218).

| Screen | Main responsibilities |
| --- | --- |
| Playtest supersession | The Main menu, Zone browser, History tree and Settings rows below are historical where they conflict with this paragraph: Settings is in menu/hamburger and holds Card records, Log out, version/commit and playback pause; debug account switching stays in hamburger. Main menu has up to three ranked active-game shortcuts using shared last-activity rows. Look/Reveal/Search use one game-wide left panel, plus one local searchless Graveyard/Exile view per viewer. The timeline launches the full-screen group tree; tap reviews the last event and closes, with no Make current/archive/restore. See the primary board, account and history contracts. |
| Login/registration | Login before main menu (D-188). Register with username/email/password; login with email/password; forgotten-password reset by email (D-197). D-268 keeps login across restarts/long inactivity with a non-expiring revocable token, not a stored password or idle/absolute timeout. After explicit invalidation/token loss, same-account login returns to the same game and recovers pending actions without manual resend/duplicates (D-217). Explicit Logout clears login/private downloads while protecting recovery records/account isolation (D-241). Validation, token/reset/recovery mechanics and reauthentication-required navigation need completion. |
| Main menu / shared navigation | Main-menu content: Games, Pods, Manage decks, Contacts, Settings; no redundant subtitles. Portrait puts the small ATW logo at the right of the hamburger row; landscape keeps the large logo. Games and Pods each have one inset panel joined to their button (square top, rounded bottom), bare rows and separators, hidden when empty. Each independently shows at most three items: your actions oldest first (including pod picks/build), other active items newest first (including accepted game setup), then invitations newest first only if space remains; ties use ID. Shared navigation: clickable Menu/Games categories, indented destinations/current games (D-292). Username search/add saves contacts without mutual approval (D-196). |
| Games (formerly Play) | One current Games list including in-play and setup/lobby states, followed by Invitations (D-292), with recipient Accept/Decline and same-lobby entry on Accept (D-190/D-195). Finished-session history archives/review are optional future work, not R1 (D-214). Catch-up state, pending work and branch-change presentation remain proposed. |
| Drawer Pods | Pods is a bold clickable top-level section directly after Games and its rows, not an indented Menu destination. All member pods appear beneath it using the game-row style: avatars, name, short status and chevron. A row opens that pod and closes the drawer; the heading opens the pod list. Cached rows stay visible without loaders during background refresh. |
| Menu invitation actions | Incoming game and pod invitations have compact check/close Accept/Decline icon buttons at the row end, using the list's response actions. Tapping the invitation row opens its Games/Pods list, not an unaccepted lobby/pod. |
| Game lobby/invite | Invite/accept into lobby (D-195), prepare mainboard in Library and sideboard in separate Sideboard, other card areas initially empty (D-202). Select either player or Random as starter (D-201), both press Ready (D-189/D-190). Start shuffles Libraries and initializes life 20 without a required starting-life setting or automatic draw (D-200); begin turn 1 at Untap (D-108). Sideboard is unavailable during play, including browser/move paths (D-202). Remaining setup/lifecycle details are open. |
| Deck management | Import common MTG text; flag unresolved names, preserve input and block saving until resolved (D-189/D-198). Name/rename and choose a cover (D-199). D-293 replaces generic Replace/Save as new with bottom new-import and contextual Edit → Import decklist, default Overwrite / additive Merge. Art thumbnails after quantities open full inspection. Editing/deleting preserves selected lobby/game copies (D-221). No full deck builder/export or trash/restore (D-241). |
| Table | Shared board, MTG tracking/Untap/Draw variants, manual phase/step choice and turn player/number. D-298 supersedes separate D-107 controls with one quick square: tap Next step, hold End turn, hold consumes tap; explicit picker/Table actions/accessibility End turn remains. No priority or number editor. D-109 uses manual steps for extra turns with approximate counting, no dedicated action/scheduler. Tracking edits shared state, not review; geometry remains reviewable. |
| Card/object inspector | D-252: own-area hold shows real faces without an event; opponent-area hold respects hidden presentation. Context Inspect other side logs viewing without flipping; Flip is a separate shared edit. Accessible controls follow the same disclosure rules. |
| Zone browser / pile actions | Pile tap opens contents and pile context actions together. Contents use card-type category columns with name search/filtering and hold inspection (D-50). Explicit View/Reveal is shared/logged: View shows faces to its actor and backs to the other participant; Reveal shows faces to both. Full-library contents hide overall order; explicit top/bottom-N displays preserve source order rather than type-grouping. Grouping/filtering never reorder stored contents; exact columns and covered opponent layout remain open. |
| Action/group review | Before/after changes, action stepping, interrupt at this boundary. |
| History tree | Forks, active path, archived lines, review/explicit activation, return and restore. No branch comparison/renaming (D-241). |
| Synchronization status | Pending/rejected status and automatic already-sent recovery within ordinary flows. No dedicated recovery-center/export screen (D-241). |
| Game communication | Game chat is the only comment channel; contextual pings/card notes remain separate. No event-comments UI, Event details dialog or response requests/read receipts/presence. |
| Settings/storage | Notifications, accessibility, cached data, account and data management. |

**Ending play (D-211)** uses a proposed win/loss/draw as shared, undoable board
state. D-257 makes each suggestion a strict singleton group: group catch-up shows
the final board before the suggestion's acceptance dialog, rather than putting
the dialog over earlier unreviewed play. No other event joins that group, even a
tap/ping/tracking companion. Sync does not wait for playback. The other
player must accept; acceptance returns both players to the lobby.
No win-history/scorekeeping feature is required. This supersedes D-210's unilateral
confirmation-dialog/non-undoable End game. D-212 separately makes acceptance/return
to lobby non-undoable, not the pending suggestion. Concurrent incompatible result
actions follow normal server ordering/applicability: the first applicable action
takes effect and the later incompatible request is discarded (D-239), not a
replacement policy. Finished-session history archives/review are optional
future work, excluded from R1 (D-214), not a required archive screen or lobby path;
current-game history remains included. D-213 restores
last lobby-prepared decks/mainboard/sideboard on return, clears both Ready and
requires both players to ready again for a fresh game. Starter-option retention
across games remains separate; D-274 resolves Random once at actual start when
selected. Do not infer an extra confirmation dialog or a pre-start random roll.

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

Search and local Graveyard/Exile views group by type/name and copies by name (×N).
Panels use Stack-sized cards and the fewest fitting columns up to three; when both
are open, shared left/local right use at most two each. Look/Reveal retain source
order and allow insertion reordering. Local pile viewing is not shared or logged.

Card menus in Graveyard, Exile and Library (including Look/Search and left panels)
offer the existing Stack projection action. Library sources require rules v21;
the source stays in its zone, as with the existing Battlefield Stack action.
The live Library projection reveals its source while at least one such entry remains.
At turn 1 Untap both players get Mulligan N and Mulligan −1 for their own Hand/
Library; the non-turn player's bar contains only these actions, without advance.
Mulligan N opens a number dialog (initially 7, remembered locally per game/player)
and submits once on confirmation; −1 immediately draws one fewer than Hand size.
Library menus use the same dialog/preference. Other players' game deck titles are
replaced with “Deck” client-side; your own title remains visible.

No essential action may require leaving Android for an external editing tool.

## 5.2 Persistent table context

Gameplay Back first closes the top menu/dialog, then history mode, without a
continuation chooser. On the bare table require a second Back within 2 seconds,
showing “Press back again to leave” after the first; only the second leaves.
This is the gameplay-specific exception to generic screen navigation.

### Short-name button tooltips — D-299

Holding an **icon-only button without an assigned hold action** shows just its
short action name: the text it would have as a labeled button, e.g. Inspect,
Library top, Set red counter or Choose step. Value-only action squares use the
same rule where the value stands in for an action label. Do not show a tutorial,
long explanation, shortcut hint, request metadata or a concealed card's name.

- A recognized tooltip hold consumes the tap. Release dismisses without also
  clicking, changing a value, opening an inspector or logging hidden-side viewing.
  Do not execute the button on press-down before hold/click arbitration finishes.
- **Assigned hold actions have priority**, including Undo→Redo, Next step→End
  turn, paused Play→historical continuation, and game-row context actions inherited
  by their child controls. They never also show a name tooltip or dispatch a tap.
  Cards' own hold inspection and drag gestures are not replaced by button tooltips.
- Moving beyond normal gesture slop cancels pending tooltip recognition. Normal
  drag/pan recognition still owns its completed gesture; showing/dismissing a tip
  cannot add a release click or duplicate a transfer/value-drag request. Cancel,
  leaving, native Back or a lost target dismisses without a game action.
- Keep the tip inside visible bounds and brief. Full accessible action names and
  actual availability remain exposed without requiring a timed hold. Tooltip state
  is personal UI, not sync/history/presence or an extra confirmation. Hold timing
  and exact styling remain UI implementation/verification details.

Tooltips sit above the button (below near the top edge), never under the finger.
Inspect hold is 350 ms; compact titleless hover info scrolls with finger up/down,
has no enlarge-art control and renders native mana/tap symbols. Every custom
button has press feedback; mutating holds give a tiny haptic tick, inspect does not.
The full inspector uses full card images, including every face-up panel card.

This supersedes earlier broad “no tooltip” wording only for these short names;
it does not restore shortcut tutorials or attach a tooltip to a real hold command.

### Shared and personal table context

The table must always make these distinguishable:

- Which participant's perspective and zones are shown.
- Active branch name and whether this view is on it.
- Displayed boundary versus shared head.
- Historical review, local pending state, or synchronized current-board view.
- Connection/synchronization state and number of pending actions.
- Undo/redo availability and any operation awaiting confirmation.

D-259 makes the boundary explicit: open menus/editors, camera, selection/modes and
personal review are device-local UI. Semantic board/history/control/communication/
lifecycle state is server-authoritative and synchronized through requests, with
provisional client application for latency hiding. Accepted replicas, prediction
overlays and durable request recovery are not UI-owned games; see
[the ownership matrix](domain-model.md#state-ownership-and-request-boundary--d-259).

**Authoritative owner revision, 2026-10-25:** selective removal/reinsertion is allowed
iff every later game-tree event can apply with identical concerned-field
`(before, after)` tuples across both trees. Concerned fields include reads/dependencies
as well as edits. Game state/events alone decide eligibility, never information
gained or seen; blanket fork/work-limit refusals are superseded.
D-230/D-231 target your most recent still-applied action,
even after opponent activity; never skip a blocked own target or undo the opponent's.
Redo is last-undone-first and blocked entries disable it without skipping. Ordinary
own new gameplay clears own Redo; ordinary opponent play does not (D-227–D-230).
D-258 deliberate shared-history switches clear both players' queues atomically;
UI review, same-current-target no-ops and normal Undo/Redo do not trigger a blanket
reset. Per-mutation relevant
state refinements, N/counting, pointer mechanics, insertion anchors and final
control layout remain technical/UI work. D-235 settles shared per-account stacks. The
earlier parent-step/remembered-head controls below are rewind context, not the
complete current definition of Undo/Redo; live action acceptance is unchanged.

**D-232:** Undo/Redo edit the existing tree by removal/reinsertion, without creating
branches or keeping the pre-Undo path as an alternative. Removed mutations are
available in the Redo stack, not a required explorer branch. Effects on review
cursors follow D-238 parent fallback; pings retain their relevant
contexts and can block dependent removals. Cache/ID mechanics remain technical;
do not assume an immutable old path remains.

A **graphical tree explorer** allows review of any retained
branch/node, including internal nodes/root; reviewing alone does not activate.
Hide only single-player inactive continuations without exposure
behind a separate Show hidden branches button whose color/icon shows state.
Mixed-player/exposure continuations stay visible in full.
Covered summaries do not automatically disclose identities.
Exact pre-fork/nested presentation, concurrent activation and exposure coalescing
remain technical/UI work; these are not pings or
permanent board markers.

D-94 keeps game-wide Chat outside gameplay Undo. Game chat is the only comment
channel; there is no event-comments UI or Event details dialog. D-240 keeps
chat simple: submitted messages have no
Edit/Delete, corrections use another message, and no expansions are planned.
Pending chat
delivery and editor submission remain separate work. Other proposed screens/tools
are subject to D-241's R1 exclusions, not approved by this inventory.

Use text/icons as well as color. History toggle-off after moving the cursor offers
Continue from here / Back to latest; outside dismissal stays in history, at head
there is no chooser, and Back exits directly. Hold transport branches immediately
without a dialog; the cancellable top-bar fork icon replaces any continuation banner.

## 5.3 Catch-up flow

**Confirmed D-277:** live mode applies remote updates immediately, then re-evaluates
pending local predictions; playback mode keeps receiving accepted data but displays
history by event groups. Only OPENING a game behind its head automatically enters
timeline playback from the per-(game, account) last-seen node, or hidden empty root
without one. No cap/fast-forward: play every group at normal pacing. Debug bookmarks
survive Logout/account switches. On divergence show “Branched” about 1 s, animate
rollback to the common ancestor then forward. Live updates, resume, inspection and
menu actions stay live. Catching up or deliberately continuing gameplay returns
to live prediction. Pause/scrub alone does not. Mode/progress is personal; do not
teleport a paused review cursor, create read receipts or block synchronization.

**Confirmed D-177:**

1. Start at the player's last local state. If the opponent interrupted that
   continuation, animate rollback fairly quickly along its mutations to the fork.
2. Play forward along the new continuation automatically or step by step. A
   group is one playback step animated as previous-board → group-end board under
   D-277; multiple same-card moves in the group use one endpoint transition. The
   underlying ordered mutation states remain available for exact navigation.
3. Let the player pause automatic playback. When paused, show the horizontal
   event timeline at the bottom; dragging left/right selects an exact mutation
   boundary, including one inside a group.
4. Continue playback or explicitly choose to continue play at that historical
   node before editing. D-179 requires deliberate continuation intent; navigation
   alone does not supply it. D-232 Undo/Redo edit the tree without supplying it. Ordinary edits
   otherwise use D-187 current-state acceptance with limited move/Stack guards,
   not automatic historical forks.
5. At the currently known latest mutation, return to live without implying priority
   passing, legality approval or consent, and without losing racing arrivals.

This supersedes the old proposal that normal Next must advance one mutation
instead of one group. Pause AFTER animation: default 1.5 s, Settings 100 ms–10 s,
local per account. Timeline overlays without resizing/moving the board: clip its
bottom only and keep the full HUD above it, with dimmed field/gray top and bottom bars and faint
Timeline mode label. Pausing playback never pauses
publication or loses pending work. See [the primary resume contract](history-and-undo.md#312-resume-playback-and-exact-timeline-navigation-d-177).

R17: the play strip has no whole-area click/ripple. Its icon-only “Timeline mode”
button enters review; event taps show a fading description (~1.8 s), holds keep it
until release. Horizontal panning is local and never moves the board/review cursor;
each new event quickly animates the strip back to its displayed play boundary.
Armed historical play retains the same strip and controls geometry.

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

Explicit activation can select a retained earlier state without reaction play.
No separate empty-interruption/response-request feature is an R1 deliverable
(D-241); it is not an outstanding required screen/control decision.

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
  card/player. Numeric zero is the same as empty everywhere; empty/zero controls
  show faint outlines. All six player counters stay visible in portrait's 3×2 grid
  on the player row beside packed Library/Graveyard/Exile. Completed empty text removes the counter.
  Temporary unfinished editor input does not itself publish deletion. Exact
  styling remains open. D-59/D-249 trim completed card-text/counter
  input, not credentials or arbitrary other text; whitespace-only counter input removes it. Preserve
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
  mixed rendering remains later UI work; D-248 captures selected/unselected
  context-card scope when opening Actions, without a reduced bulk menu.
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
   forms/joins its visual stack. The single-tap Actions sheet includes Move to Library
  top/bottom, Graveyard, and Exile. Graveyard/Exile have no order-related actions,
  including visual-placement variants (D-226). Detailed controls are in the primary
  board contract; selection gestures remain open. Explicit Move and, for ordered
  containers only, reorder controls also support accessibility and precision.
- A compact activity strip shows the automatic current group, pending status,
  and undo. No gameplay Send/Done button is required to share actions.
- Groups are automatic; no manual label/boundary editor in R1 (D-241).
- Completed actions are already queued when leaving a game. Navigation must not
  add a keep/send/discard prompt; synchronization continues subject to Android
  limits and resumes automatically when possible.
- D-278: process death may lose pending/failed actions and the last unsaved input;
  save unfinished form drafts/personal progress best effort, but restart from synced
  server state without outgoing replay. No generic publication or disk-write gate.

## 5.6 Undo and destructive-looking actions

Use the [current selective contract](history-and-undo.md#selective-mutation-undoredo--d-224),
not retired parent-pointer/remembered-head controls.

- A persistent undo entry point complements any transient snackbar.
- Show the exact object/action/branch affected, not merely “Undo.”
- Undo removes your latest eligible still-applied mutation. Composite actions may
  require multiple presses; targets blocked by the concerned-field test cannot be skipped.
  Personal viewing never changes the target or truncates retained-future checks.
- Branch “Delete” wording must disclose “recoverable from archived branches.”
- Distinct Undo requests are successive server-ordered presses (D-236), not
  captured-parent transitions. Retrying one request recovers its original outcome.
- Redo is last-undone-first reinsertion, not remembered-head traversal. A blocked
  next entry disables Redo without discarding/skipping it. Explicit graphical-tree
  activation remains separate; exact control labels/layout remain later UI work.

## 5.7 Offline, pending, and conflict states

**Confirmed D-182:** initial delivery requires a connection for game actions and
shared controls, including Undo and branch activation. Offline use is read-only
for downloaded games/history; there is no offline gameplay queue to replay later.
Keep personal review/navigation separate from shared controls.
No offline operation may create a game event (D-246). Downloaded visible content
and recorded outcomes remain readable, but a new covered/other-face inspection
requiring exposure logging needs a connection, even if its data is cached.
Own-area holds instead read downloaded real faces without an event (D-252);
opponent-area holds never automatically disclose hidden information.

**Confirmed D-215:** while disconnected, show a **Reconnecting...** dialog that
**blocks gameplay actions**, including shared Undo/branch controls. Do not create
offline gameplay behind it. This connection-loss dialog is not a routine gate
while online actions await acknowledgements, and it does not mean in-flight
requests were rejected. Downloaded read-only access remains in scope; exact
dialog navigation/dismissal and reconnect readiness remain open. D-216 separately
confirms automatic recovery of already-sent requests below.

**Confirmed D-216, limited by D-278:** after same-process reconnect, automatically recover already-sent online
actions without manual resending or duplicate execution. Recover actual outcomes
using original request identities/conditions; an already-committed action/random
result must not execute/sample again. Use normal difference-only reconciliation
for accepted/rejected outcomes, without retrying a rejection as a fresh action
or cancelling other valid work. This does not enable offline actions.
D-275 selects stable identity, durable per-game admission/order and fenced outcome
recovery. Exact wire/readiness, queue limits and buffered-but-unsent handling remain
completed in [protocol v1](../04-technical/protocol-v1.md); restart discards client
outgoing memory and syncs server state. No offline replay authorization.

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

D-277 makes this explicit for three locally predicted moves: an expected first-move
acknowledgement leaves the displayed third-move position intact. Incorporate a new
remote change before replaying original pending predictions; omit a failing middle
move, then any later move whose original guards now fail, while keeping independent
valid predictions. "Cancelled" here removes predicted visual effects, not an already-
sent request or its recovery obligation. Compute the target promptly and animate
only the net old-display → new-target difference, not a sequence of rollback poses.
Live batches coalesce same-card moves to their endpoint; playback preserves group
staging. An in-progress animation retargets from current visible geometry. See the
[primary reconciliation example](history-and-undo.md#prediction-rebasing-and-cascading-invalidation--d-277).

Hand additions/removals slide the remaining cards in either player's hand over
220 ms at default move speed, scaled by the existing move-speed preference.
Cards still flying into a hand stay outside its clipped reflow until they land,
including when another change retargets their flight.
History jumps and catch-up fast paths skip this motion with the other scene animations.
Landscape uses a compact side lane with 48 dp minimum pile targets and tightly
spaced piles; the Stack is centred between the two pile rows, with counters facing
the outer edges. The lane expands with the effective Piles size (including the
global card-size fallback), keeping all three piles and their touch targets inside.
Portrait geometry is unchanged.

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
on rejection, animate the net displayed-board → reconciled-target difference under
D-185/D-277, not a queue of reverse operations over newer valid work. The animation
explains the correction without a required text explanation or acknowledgement.
This does not publish a shared Undo, create a branch or undo the opponent's work.
Animate incoming opponent mutations too; preserve personal historical cursors.
Preserve recoverable rejected work; exact request recovery, prediction bookkeeping
and animation scheduling remain technical work within D-185/D-277's behavior.
D-236 server-ordered Undo remains separate from ordinary actions and their
reconciliation; D-161 captured-parent Undo is retired.

An explicitly requested historical continuation makes its branch current and
preserves the displaced line for inspection/return. Normal Undo removes the latest
eligible own mutation, not automatically activating the displaced future. Retry does not duplicate actions
or branch activation. Preserve personal review positions and local work when the
active line changes; detailed queued-continuation handling remains open.

## 5.8 Accessibility and device behavior

Required proposals for approval:

- TalkBack names, roles, and actions for zones, cards, counters, branch changes,
  selections, and playback controls.
- Scalable text; adequate contrast; minimum platform-recommended touch targets.
- No color-only meaning, gesture-only commands, or time-limited-only recovery.
- Reduced-motion option and readable non-animated change summaries.
- **Portrait and landscape are both required (D-290)**, with separate mockup
  captures of both, not a portrait-only design stretched sideways. Tablets,
  foldables, split-screen and large text must preserve access to every operation;
  the pilot does not claim these additional variants have been validated.
- Rotation/backgrounding with a living process preserves state; sign-in refresh
  retains same-account in-memory work. D-278 process termination may lose outgoing
  work and unreported exposure; saved accepted data/UI drafts remain separately
  best-effort recoverable, not a guarantee on every keystroke.
- System Back exits transient selection/sheets first, then screen navigation;
  it does not undo game actions.

## 5.9 Notifications

**Confirmed D-219:** provide **push notifications for new invitations and grouped
opponent activity**, **without revealing covered card names**. Consolidate related
opponent activity rather than sending one push per primitive change. Existing
immediate-publication rules remain: coalescing must not delay synchronization or
wait for a group-completion tap; each committed action is available immediately.

**Confirmed D-269: FCM is optional.** Google Play services, Firebase registration
and Play Store distribution must not be prerequisites for using the app or
synchronizing games. FCM may provide background push on compatible installations;
without it, periodic server polling is an acceptable fallback for detecting the
same invitations/grouped opponent activity and posting local notifications.
The owner suggests AlarmManager polling as a possible mechanism, not a selected
exact-alarm policy or delivery deadline. Background checks are best effort under
Android scheduling/network/battery restrictions; reopening the app recovers current
server records even if no push or poll ran. Exact scheduler/cadence remains
technical design. Polling must not download the whole gameplay tree each time.

**Confirmed R1 settings boundary (D-220):** use **Android's notification channel
settings** for notification control in R1. Both proposed **in-app global mute**
and **in-app per-game mute** settings are **post-R1**, not release requirements.
Respect the user's Android notification settings; do not require an app-specific
mute/settings screen for R1. D-219's notification categories remain in scope with
D-269's optional push/polling delivery; no separate channel per game is required.
Polling-generated notifications obey the same permission/channel settings and
covered-card privacy rules, not a workaround for disabled notifications.

D-282 confirms Invitations/Game activity channels, per-game coalescing, routing
to all logged-in registered installations and local game-activity suppression while
that game is actively displayed, including review/playback. Background is not active
display. D-284's [notification profile](../04-technical/notifications-and-background-work.md)
defines bounded windows, polling, data-only push, retries/dedup and permission defaults.
This does not add branch-switch/response alerts or a shared unread/read model.

**Excluded from R1 (D-241):** additional branch-switch/response-request alerts and
their preference controls; these are not a required design backlog or roadmap.
Notification previews should avoid covered identities
beyond the confirmed no-covered-card-names rule. Deep links open the referenced
game/boundary but check current branch status and access.
Stale notifications are harmless pointers to history, not instructions to replay
an operation. Push/poll failure cannot affect game-state correctness. Notification
delivery and background results remain bound to the originating account/session;
delayed work or deliveries after Logout/account switching cannot expose that
account's private content to another account (D-241).

## 5.10 Low-interaction design contract

**Confirmed principle:** minimize taps and other user actions, not merely screen
count. Include scrolling, navigation, typing, and repetitive selection in the
cost of a workflow. Do not move interaction cost into mandatory setup chores.

Proposed rules:

- No routine Save, Send, confirm-action, start-group, end-group, or branch-name
  step for game edits.
- One activation for a directly exposed quick action such as tap/untap, a life
  increment/decrement, next/previous review action, or latest safe undo.
  D-267's direct normal-card tap/untap gesture uses two physical taps; interaction
  budgets must count those touches rather than relabel it as one physical tap.
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
work. Outside Hand, a normal double tap must emit one tap/untap operation without also opening
the single-tap Actions menu; hold must not tap or drag the card accidentally.
D-281 confirms no Select/Ping double-tap recognizer: two independent immediate taps.
