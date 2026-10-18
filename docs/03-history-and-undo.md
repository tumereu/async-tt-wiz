# 3. History, review, and undo

This is the core behavioral contract. Branching, recovery, instant sync, low-tap
UX, and automatic conflict branching are owner decisions; detailed mechanisms
below remain proposed where not explicitly confirmed.

## 3.1 Three positions that must never be confused

- **Shared head:** latest published boundary on the active branch.
- **Review cursor:** boundary currently displayed to one participant.
- **Local head:** boundary including that participant's completed actions awaiting
  synchronization. Incomplete form input is not a game-state boundary.

The UI labels which state it displays. “Current board” means shared head, not
“the actions both people have reviewed.” A review cursor may lag the shared
head by many actions. Advancing it does not write another game-state action.

Instant synchronization updates downloaded history and the shared-head indicator;
it does not automatically advance a historical review cursor. An optional live
follow mode can track the shared head. Local edits advance the author's local
view immediately, with pending status until acknowledged.

The published future is a proposed continuation in the players' shared play,
even though it is the selected shared branch in the storage sense. The other
participant may interrupt any of it later.

## 3.2 Atomic actions and interruption boundaries

An action corresponds to one explicit generic operation, not every internal
field write. Examples: move a card, rotate it, add a ping, or change a named
counter. Do not combine separate gestures because the engine guesses they form
a game mechanic. A deliberately selected generic bulk operation can be atomic.

An interruption is possible at the root or after any published atomic action,
including an action inside a group. There is no boundary inside an atomic
action. Before committing a compound action, the player can inspect its parts;
when intermediate response points matter, record separate actions instead.

The engine does not recognize response windows, casting, payment, or resolution.
Preserve boundaries between independently completed operations so participants
can choose the right interruption point themselves.

Example sequence, with only literal records:

1. Rotate card A.
2. Move card B from Hand to Cast.
3. Add a ping to the opponent's player marker, associated with action 2.
4. Move card B from Cast to Graveyard.
5. Change the counter labeled Life from 20 to 17.

The opponent may stop after action 3. Actions 4–5 remain on the old branch when
the opponent creates another continuation. The engine never calls the sequence
“cast B at opponent and resolve”; that interpretation belongs to the players.

## 3.3 Immediate action synchronization

**Confirmed:** completed game actions synchronize automatically, without Send.

Proposed action lifecycle:

1. The user completes a direct operation or the necessary inputs for an action.
2. Persist its state transition and outbound operation atomically, and display
   the result locally without waiting for a network round trip.
3. If connected and dependencies permit, begin publication immediately. Do not
   wait for a group to end, a timer, screen exit, or another user action.
4. The service validates structural preconditions and commits history, branch
   changes, and notification outbox records atomically.
5. Mark the local result synchronized on acknowledgement; otherwise retain it
   visibly as pending, retryable, or failed work.

Publishing submits the intended base boundary, continuation context, revision
preconditions, action data, and group references. “Publish” names a protocol
operation, not an extra interaction. Transport batching is allowed only without
deliberately waiting to gather a group; every action boundary remains intact.

- An action at the unchanged active head extends that continuation.
- The first completed action at an earlier boundary creates and activates a new
  branch atomically when synchronized. Further actions extend that new line.
- A concurrent game-edit conflict automatically creates a branch at the intended
  base, preserving both continuations. It does not wait for a conflict dialog.
  See [synchronization](06-sync-and-storage.md).
- Offline actions use the same local workflow and synchronize automatically on
  reconnect. Local branch activation remains provisional until acknowledged.
- Incomplete selections/forms may be cancelled without shared effects. Once an
  action is completed, use Undo rather than assuming it is still private.
- Do not require a pre-send review, editing mode, group completion, branch name,
  or explanation before synchronizing.

Immediate means starting synchronization as soon as connectivity and operation
dependencies allow, not promising zero network latency or Android background
execution that the platform cannot guarantee. Exact latency targets remain open.

## 3.4 Sensible grouping

Groups affect presentation and notifications, never atomicity or interruptibility.

The [literal-action presentation](11-visual-action-language.md) groups related
recorded operations and their action-linked pings. For example, a move to an
area labeled Cast and its pings can appear as one expandable unit without being
classified as a cast. Completed constituent actions still sync immediately and
keep their own boundaries; the unit is not one mutable historical node. Later
changes cannot retroactively alter an earlier boundary. Notes/messages remain
optional.

Under D-75 pings belong to just one group/event. Their overlays are not carried
onto later event states, but the records remain viewable when reviewing that
step in the event tree, including offline for downloaded games. This visibility
rule does not merge atomic boundaries or settle automatic group formation.
Exact within-group effective boundaries and later ping revisions remain open.

Proposed grouping rules:

- Group using explicit interaction context, contiguous actions, associated
  pings, author/session boundaries, and optional manual split/join. No card-text,
  area-name, or game-mechanics inference and no required intent-selection dialog.
- Start a new group at an explicit user split, a context change according to
  approved generic heuristics, or an interruption. Do not combine authors.
- A group can be open and grow as actions synchronize. An opponent can review
  it immediately without waiting for closure. Closing a group is presentation
  metadata, not a gameplay commit or required user action.
- The user can optionally rename, split, or join adjacent compatible groups
  after publication. These reversible metadata edits synchronize automatically;
  they do not change action payloads, boundaries, or review positions.
- Never infer a priority pass, spell resolution, or turn completion from elapsed
  time, a card animation, or a group ending.
- Boundaries and action summaries remain visible when a group is collapsed.
- A review control opens the next group but advances one action at a time.
  “View group result” is an explicit shortcut, not the only navigation mode.

Exact generic grouping heuristics remain an open specification item. They must
work without extra routine taps or interpreting game mechanics. A threshold may
close a presentation group or coalesce notifications, but must not delay
synchronization or imply a game-rule event.

## 3.5 Interruption and active-branch selection

For a path `root → A → B → C → D`, interrupting after B with X creates
`root → A → B → X`. The old path through C and D remains recoverable. On
successful publication, the branch ending at X is active.

Proposed application of the same selection rule to automatic conflicts: a newly
created conflict branch becomes active when committed. Service commit order,
not device timestamps, serializes simultaneous branch activations. The displaced
line is retained, labeled, and available through one-step return/undo.

Activation occurs on branch creation, not every subsequent append. If another
line later becomes active, already queued descendants continue on their assigned
branch without repeatedly reclaiming active selection. This avoids automatic
branch-selection ping-pong. A participant can explicitly reactivate that line.

The operation records:

- Fork boundary B, original continuation/branch, and its current head D.
- Author and optional reason, new branch ID, reaction actions, and new head X.
- Previous active selection, expected revision, and resulting control revision.

No second-player approval is required. A visible activity entry explains the
switch. A participant reviewing the old continuation is notified and can choose
to inspect it or navigate to the divergence; the UI must not silently teleport
their cursor past unreviewed actions.

Either participant can also activate an existing non-archived branch explicitly.
Activation changes the selected continuation, not the contents of either line.

The preserved suffix is not automatically replayed on top of a reaction. It may
refer to objects, positions, or values that are no longer present. A future reuse
tool must preview and validate every copied action; automatic replay is out of
the proposed first release.

## 3.6 Review progress

Review controls: previous action, next action, previous/next group, jump to a
selected boundary, view shared head, and interrupt here.

- Review progress is personal and may synchronize across a person's devices.
- Own actions are distinguishable from unseen actions by the other person.
- Reading an action does not prevent a later interruption at an older boundary.
- Branch changes preserve review positions on retained paths.
- The next required catch-up starts at the last common reviewed boundary on the
  active path; actions on an abandoned suffix do not count as seen on a new path.
- A manual jump to the head marks actions as skipped/viewed in summary, not as
  individually stepped through. Whether to expose this distinction to the other
  participant remains a product decision.
- Notifications and read markers are not simulated MTG priority passes.
- Newly synchronized actions can extend an open group while it is being
  reviewed. Update unseen counts without resetting the cursor or requiring the
  participant to reopen the group.

## 3.7 Full undo contract

Requirement U-001: **Every user-authored logical game edit has a discoverable
undo or recovery path.** This includes state edits, random actions, draft edits,
publication, grouping, messages, setup corrections, branch switching, branch
archival, and game result markers.

Undo preserves auditability. “Delete” and “discard” are reversible logical
operations, not necessarily physical removal of recovery records. D-90 separates
the visible event tree from internal redo/recovery: undo an ordinary non-
information-gaining action by restoring the preceding tree as if that action
never happened, without leaving an ordinary undone-action branch. Undo an
information-gaining action by reverting its board effect while retaining it as
a visible history branch. D-89 defines the confirmed exposure coverage below.
Redo/recovery remains required; hidden recovery retention does not require
ordinary undone nodes to remain in the visible tree. Exact schemas are open.

| Original operation | Required recovery behavior |
| --- | --- |
| Pending action not yet transmitted | Restore preceding local state; remove its queued effect safely; retain redo result. |
| In-flight action with unknown outcome | Show undo intent immediately; establish the original outcome and synchronize the necessary compensating operation without a second ordinary undo tap. |
| Published latest action/group | Restore its predecessor boundary. Apply D-90 visible-tree behavior: ordinary undo disappears from the tree; information-gaining undo retains a history branch. Exact mixed-group rules remain open. |
| Historical action with later dependent actions | Preview rollback to its predecessor and retain recovery for the affected suffix, without blind inversion of later work. Visible branch retention follows D-90; exact mixed-suffix classification remains under review. |
| Interruption | Restore the previously selected continuation, including its preserved suffix. |
| Activate another branch | Restore the previous branch selection. |
| Delete/archive branch | Restore the branch and its metadata; restore selection if explicitly requested. |
| Group metadata edit | Restore previous title/boundaries without changing action payloads. |
| Message edit/delete | Restore the previous content/version. |
| Game completion/concession marker | Reopen the game at the prior logical state. |
| Die/coin result | Restore prior board state while retaining the information-gaining action/result as a history branch (D-89/D-90); redo reuses that result. Other random-operation classification remains open. |

Undo operates against a known control revision. If other work happened after
the targeted operation, show the affected continuation and require explicit
confirmation. Do not apply a blind inverse patch across unrelated later edits.

Undoing an old life change, for example, can rewind the continuation before that
change. If the intent is to keep subsequent play and repair life now, offer a
separate **correct current state** action. Label these as different operations.

An undo itself is recorded and recoverable internally, without requiring an
ordinary undone-action node/branch in the visible event tree (D-90). A record
stores logical prior/new references needed for restoration; it does not depend on
guessing an inverse from a human-readable description.

Undo automatically synchronizes. Rapid undo must work before, during, and after
acknowledgement. Queue dependencies and unknown delivery outcomes cannot make
an apparent cancellation silently reappear as a published action. If later
collaborator work expands the rollback's impact, preserve that work and show the
historical-rollback choice rather than blindly execute a queued inverse. This is
distinct from an ordinary game edit, whose conflict automatically branches.

## 3.8 Deleting an active interruption

“Delete this branch and return” is a single atomic control operation:

1. Show the branch to be archived and the exact return continuation and board.
2. Archive the selected branch and select the chosen return branch together.
3. Leave all action nodes and descendants recoverable.
4. Offer Undo, which restores both archive state and branch selection.

Default return target: the continuation displaced by this interruption, at its
currently retained head. If that target has changed, is archived, or is missing
from local storage, do not guess: show a selection/restore flow. The exact root
branch cannot be archived if doing so would leave no active branch.

Archiving a branch does not implicitly archive its descendant branches; show
descendants and offer an explicit, reversible multi-branch operation if desired.
Shared prefix actions are never deleted by branch archival.

## 3.9 Limits of undo

- A reveal can be reversed on the board but cannot erase a person's memory.
- Delivered push notifications cannot reliably be retracted.
- Exported files or screenshots cannot be recalled.
- An external account deletion or legally required data erasure is not a
  recoverable in-game operation. Its policy must explicitly disclose that.
- Navigation has Back/history behavior rather than shared game undo.

The UI must explain these boundaries instead of promising physical reversibility.
No history compaction or retention policy may silently remove the promised
in-game undo capability.

### Information-gaining actions and undo branches

Confirmed D-89/D-90: information-gaining actions leave branches in history when
undone; ordinary undo restores the visible tree as if its action never happened.
This replaces the earlier separate persistent-audit-marker proposal with history
branch retention, without requiring permanent board markers or ping overlays.

Confirmed exposure coverage (D-89): library View/Reveal, otherwise-covered hand/
card-face viewing, covered-face hold inspection, and die/coin results. Ordinary
inspection of already-visible faces and every search keystroke are not included.
This records what the interface showed/generated, not what a player remembers.
Covered-face inspection logs an exposure without requiring a board-state edit
or automatic activation of a new gameplay continuation; exact record placement
remains open.

Retain actor, operation/source context, exposed references or concrete result,
and a route to the original step in the retained history. Distinguish undone/
inactive-line status. Inspecting that branch does not itself activate it or
reapply board changes. Branch archival cannot erase the promised exposure
record; exact discoverability and archive presentation remain open.

Ordinary summaries report that viewing occurred without automatically showing
covered card names/faces to the other participant. Original context remains
inspectable under cooperative full access; rolls retain actual recorded results.
"Permanent" here means retained across normal undo/branch operations, not an
exemption from eventual game/account erasure. Exact triggers/coalescing,
historical/offline views, durable capture/publication, schemas, and retention
remain Q-31. No competitive anti-cheat guarantee is implied.

Next mixed/exposure-edge batch, proposed and not yet approved:

1. If an undone group/suffix includes any information-gaining action, retain the
   whole affected sequence as a history branch, including its ordinary actions;
   if none, remove the undone sequence from the visible tree while retaining
   internal redo/recovery. This does not settle atomic boundaries/group formation.
2. Count explicit draws/moves/face changes that show previously-covered card
   faces as information-gaining too, even without View/hold. Exact exposure
   triggers remain structural presentation facts, not inferred game knowledge.

## 3.10 Randomness and branching

D-87 confirms die/coin results as automatically queued/synchronized records of
actor, request, and actual result. Review/redo reuse that concrete result rather
than rerolling; these tools do not automatically change card or counter state.
Exact random source/algorithm, offline execution policy, and action-boundary
protocols still require review; touch entry is in the board contract.

D-88 confirms that the roll seed resets when the other participant takes
actions. That reset governs future roll generation; it does not rewrite concrete
results already recorded, including when reviewing/redoing them. Exact qualifying
actions, seed scope/ownership, reset ordering under offline/concurrent edits,
branch association, and whether coin/shuffle share this lifecycle remain open.

Next seed-sequence question, proposed and not yet approved: consecutive new rolls
advance a deterministic seeded sequence; undo/retry at the same draw position
and unchanged seed epoch reuses that draw instead of obtaining fresh entropy.
An opponent-action reset starts a new sequence for future rolls. The alternative
is that every roll repeats the same result until that reset; D-88 alone does not
settle this choice or the draw-position/history protocol.

Shuffle, die roll, coin flip, and random selection are explicit actions with
stored outcomes. Re-rendering, reopening, replaying, or redoing an action must
not draw new randomness.

A fresh random action gets a fresh identity and an audit entry; a new identity
does not by itself authorize a new seed or entropy draw (D-88). Undoing and then
choosing to shuffle again is a visible new action, not invisible rerolling. Full
information access means this is not a competitive fairness guarantee.

Offline and online execution must store concrete outcomes that deterministic
replay can apply identically. The algorithm and entropy policy must be specified
before implementation; see the open decisions.
