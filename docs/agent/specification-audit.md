# Specification audit and interpretation risks

This is editorial/agent context, not new product behavior. It records findings
from the plain-language summary and tier reorganization.

- **D-159/D-160 replace earlier Undo semantics.** Normal Undo is one shared
  parent-node pointer move, regardless of author; composites can require multiple
  presses. D-222 refines Redo/filtering: remember the last active head for Redo
  toward it, graphical any-node activation, and default hiding only for single-player/
  no-exposure inactive continuations. Mixed-player/exposure paths stay visible in
  full. D-223 explicitly confirms the event-comment visibility exception even for
  single-player/no-exposure continuations; hide only those without comments too. The decision
  register preserves original D-90/D-91/D-95 and D-131/D-151 wording for provenance,
  not as current requirements. No last-exposure cutoff, whole-invocation Undo or
  suffix-rollback preview remains. D-161 silently discards no-longer-valid specific
  Undo transitions and reconciles local state, with no notification clutter.
  Remembered-head user/device scope, activation updates, concurrent/stale Redo/
  activation, archive/restore and exact pre-fork/nested presentation remain open.
- **D-176/D-177 correct the mutation/playback model.** One node is one individual
  mutation, including individual operations from bulk commands. Every mutation
  immediately extends/branches the chosen path and publishes. Older combined-node
  mappings (D-130/D-132/D-144/D-148–D-150) and MTG-reaction-window node sizing are
  superseded; historical decision rows are provenance, not exceptions. Groups
  are concurrent-animation playback steps, never state pointers/publication units.
  Resume from the last local state, animate backward to the interruption, then
  forward through groups. Pause and scrub exact mutations on the bottom timeline.
  The assistant's last three-rule precedence proposal was not approved.
- **D-179 replaces automatic conflict forks.** Historical D-13/conflict references
  do not authorize branching on stale ordinary edits. Historical continuation
  needs deliberate UI intent and an explicit server-message flag/boundary; Undo
  supplies intent automatically. D-187 now normally applies ordinary actions to
  current state with limited movement/Stack guards, not general old-field checks.
  D-176/D-177 still define mutation units
  and playback, not unconditional fixed-parent publication from a review cursor.
  P-11's conflict-activation protocol and the assistant's four-rule proposal are
  superseded/unapproved; exact guarded movement/Stack representation and recovery remain open.
- **D-187 replaces broad optimistic locking.** Reject ordinary actions only when
  genuinely impossible; previous orientation/note/Set values are not locks.
  D-181's comparisons and D-186's Set comparison are superseded. Card moves retain
  source/destination guards; real-card Stack entries/new Stack projections send
  current client Stack state and reject if server Stack state differs. No separate
  unseen-opponent detector or whole-game-head lock. The 25-rule proposal is
  historical/unapproved, not a list to restore; snapshot representation and
  provisional references remain technical design, not a missing comparator choice.
- **D-182 cuts offline actions from initial delivery.** Downloaded history remains
  read-only offline. Earlier offline-play, Undo, Reset and random-action queue
  references are superseded, including historical decision scope; do not restore
  a full offline replay protocol to solve online latency. D-180 prediction and
  animation remain. Narrow recovery of an already-started online request with an
  unknown outcome is still needed, not permission for new disconnected actions.
- **Approved and proposed are mixed.** Detailed drafts sometimes say “must” even
  though the enclosing design is proposed. Acceptance rows are specifications,
  not passing application tests; no app exists yet.
- **Some wording is stale.** Older references still say individual blocker
  assignments or call now-decided behavior open: product requirement P-033 still
  describes drops onto individual attackers, and the older board-area sketch
  leaves combat layout unsettled. The current model is paired groups, two rows,
  drop-directed placement, and rightward quick-action placement. Old prose must
  not resurrect superseded rules.
- **“Action”, “event”, and “group” are overloaded.** A user invocation, an
  interruption boundary, a display group, and an Undo unit are not always the
  same thing. Their relationship needs one consistent explanation/contract.
- **There are real exceptions to the simple model.** The three-card ordinary-pile
  cap, move-triggered counter/text resets, disappearing tokens, projection
  write-through, and retained information-exposure history are already approved.
  List them explicitly rather than hiding them behind “just a tabletop.”
- **Prior sorting approvals do not decide placement.** They only order cards
  within a dragged collection. They do not authorize another sorting feature,
  change the destination, or justify endless follow-up questions.

The reorganization preserves existing contract text and stable IDs. It does not
claim to fix all wording drift or finish the behavior specification. Start with
the [product principles](../design/01-product/overview.md) and
[behavior overview](../design/02-experience/behavior.md); substantive gaps are in
[the gap list](../design/02-experience/gaps.md).
