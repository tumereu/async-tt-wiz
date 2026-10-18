# 5. Android experience

Status: proposed interaction architecture. Annotated wireframes, exact layouts,
visual design, and Android support versions remain to be specified.

Confirmed card gestures and proposed drag/pile/intent details are defined in
[Game board and controls](10-board-and-controls.md), the primary tabletop-input
contract. Single tap taps, double tap opens actions, hold inspects, and drag moves
with snapping. These replace the earlier undecided tap-versus-inspect mapping.
The [visual action language](11-visual-action-language.md) defines the proposed
labeled Cast area, action-linked ping controls, literal grouped log presentation,
and optional notes/chat. Ordinary flow should not require typing what just
happened, and the engine must not guess what the actions mean in a game's rules.

## 5.1 Navigation and screens

| Screen | Main responsibilities |
| --- | --- |
| Welcome/account | Explain cooperative access; authenticate or resume; account recovery. |
| Games | Active/completed games; opponent, catch-up state, pending work, branch changes. |
| New game/invite | Participant invitation, setup, trust disclosure, format/deck selection. |
| Deck library/editor | Import, resolve names, edit quantities/zones, preview, export. |
| Table | Shared board, state context, direct action tools, undo/review controls. |
| Card/object inspector | Automatically show readable face art/text despite face-down table presentation, and both printed faces for multi-faced cards; modified state, available actions, attachments/history. Inspection does not change shared presentation. |
| Zone browser / pile actions | Pile tap opens contents and pile context actions together. Contents use card-type category columns with name search/filtering and hold inspection (D-50). Explicit View/Reveal is shared/logged: View shows faces to its actor and backs to the other participant; Reveal shows faces to both. Full-library contents hide overall order; explicit top/bottom-N displays preserve source order rather than type-grouping. Grouping/filtering never reorder stored contents; exact columns and covered opponent layout remain open. |
| Action/group review | Before/after changes, action stepping, interrupt at this boundary. |
| History tree | Forks, active path, archived lines, compare, switch, return, restore. |
| Sync/recovery center | Pending/failed work, automatic conflict branches, retry/export/recover. |
| Game communication | Context-linked messages, response requests, game activity. |
| Settings/storage | Notifications, accessibility, cached data, account and data management. |

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

Use text/icons as well as color. A prominent “Return to active game” control is
available when viewing history or an inactive branch; it must not discard drafts.

## 5.3 Catch-up flow

1. Open a game at the last meaningful review position, with an option to view
   the latest shared board immediately.
2. Show “N groups / M actions to review” on the active path.
3. Expand a group to show literal constituent actions and their pings.
4. Next advances one atomic action, animating a clear before/after delta.
5. The player can step back, inspect any zone/card, jump, or interrupt here.
6. After the latest action, label the view “At shared head”; do not imply that
   the player has passed priority or agreed every action was legal.

If a branch changes during review, show a persistent non-destructive notice
with “Go to divergence” and “Keep inspecting this line.” Avoid automatically
changing the board under a user's finger.

## 5.4 Interruption flow

1. Navigate to the boundary immediately before the action to interrupt.
2. Perform the reaction using ordinary board controls. The review context
   identifies the fork; a separate **Respond here** step is optional.
3. The first completed reaction applies locally and automatically synchronizes
   branch creation/activation. Show the preserved original line and offer
   **Undo interruption** and **Delete branch and return**.
4. Further reaction actions synchronize as they happen, grouped automatically.

Use a persistent, lightweight “Editing here starts a new branch” indicator in
historical views rather than a routine confirmation dialog. Ordinary hold
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
- D-60 creates an unused counter color with text `1`; an existing color opens
  editing actions without changing its text. No recolor action is needed in the
  initial release. D-61 confirms inline card-menu counter controls and player
  panel taps for equivalent controls. Add and completed Set retain/return to the
  menu; empty trimmed Set removes its counter row and frees that color. No
  separate Counters submenu is required; exact sizing/hit regions remain open.
- D-55/D-56 remove card counters and restore database-default corner text on
  entry to configured Graveyard/Exile/Library. For creatures the default is
  printed P/T text. Defaults for other types/faces/missing data remain open;
  preserve unrelated fields and stable identity.
- Multi-select has visible selected count and a clear cancel action.
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
- Ordinary local mistakes are one-step undo; historical rollback previews the
  displaced later work.
- Branch “Delete” wording must disclose “recoverable from archived branches.”
- If a collaborator has changed the active line, an old undo button must resolve
  against the new revision and request confirmation rather than apply blindly.
- Redo that would switch branches is labeled accordingly.

## 5.7 Offline, pending, and conflict states

Use explicit states: downloaded/current, offline/stale, incomplete input, queued,
sending, synchronized, automatically branched, and failed/retryable.

The user can continue with cached cards and games offline. Unavailable images
fall back to text; an unavailable card definition must not prevent inspection of
an existing object's stored essential data. Joining an unknown game or importing
unknown catalog entries may require connectivity and must say so.

A queued action is not labeled delivered. A concurrent game edit automatically
branches and synchronizes, with a non-blocking notice showing the divergence,
active line, and quick return/undo. No accept/rebase/discard dialog is required.
Both lines remain inspectable. Retry does not duplicate actions or branches.

If the user is still editing a line that has become inactive, preserve their
local view and show “Editing an inactive branch” with an optional activate
control. Do not silently move their cursor, rebase their actions, or activate
their branch again with each queued action. Structural errors or lost access
are failures, not conflicts that branching can fix.

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

Notify for grouped activity, interruptions/branch switches, automatic conflict
branches, invitations, and explicit response requests, subject to preferences.
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
  expanded-impact historical rollback, irreversible data erasure, or other
  specifically documented risks. Ordinary concurrent game edits auto-branch.
- Provide discoverable, accessible alternatives to shortcuts and gestures. A
  shortcut's tap savings cannot make the normal flow unusable.
- Every common journey receives an explicit starting state and interaction
  budget in its wireframe/action contract. Evaluate the whole journey, not only
  the final button.

The main card gestures are confirmed in the board contract. Exact gesture timing,
special-pile hit targets, additional shortcuts, and journey budgets remain design
work. Double tap must not also emit a single-tap game edit; hold must not tap or
drag the card accidentally.
