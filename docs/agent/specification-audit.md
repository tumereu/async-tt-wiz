# Specification audit and interpretation risks

This is editorial/agent context, not new product behavior. It records findings
from the plain-language summary and tier reorganization.

## Gap pruning and batch review — D-237

The owner corrected repeated "gap" questions whose answers followed from agreed
rules, then requested pruning and a standing instruction for future sessions.
The efficient review format is a **batch of identified cases with spec-derived
resolutions and sources**, allowing revision/acceptance together. Do not confuse
that batch review with a new approval requirement for every derived consequence.
Mark truly unresolved choices/proposals separately.

The [pruned list](../design/02-experience/gaps.md) now contains 28 worked
spec-derived cases (plus D-238's resolved review-cursor case), four concrete
remaining product/scope decision areas, and separate contract, technical, later-UI
and platform/service work. The
[Q-register](decisions.md#93-release-blocking-questions) replaces its mixed question
queue with a classification preserving every Q-ID. Neither list claims exhaustive
behavior coverage or authorizes implementation/a delivery-stage transition.

| Former broad "gap" bucket | Pruned disposition |
| --- | --- |
| Setup/import/lobby/lifecycle | Existing selection/copy isolation, Ready, start ordering, shuffling and no-opening-draw outcomes are derived, not new questions. D-239 corrects the competing-result-suggestion gap label: normal server order/applicability keeps the first applicable action and discards the later incompatible request, not latest-suggestion replacement. Unapproved extra tooling/formats remain scope choices; initialization/validation/default mechanics need contract/UI/technical completion. |
| Groups/pings | Apply approved author/category/companion/context rules to worked sequences first. Escalate only a demonstrated unresolved precedence conflict; categories, leading/compound cases and numbering are not automatically separate policy questions. |
| Undo/Redo/references | Core replay/target/order/clearing/fork/device/server-request rules are settled. Dependent comments/references block removal under D-224; their deletion/reattachment is not an open alternative. D-238 now settles personal viewing of a deleted node: fall back to its parent, repeatedly if needed to reach a surviving ancestor/root. Anchor/ID/cache/replay-field mappings are contract/technical work. |
| Online concurrency/reconnect | Arrival-order acceptance, immediate predictions, individual outcomes, difference-only reconciliation and automatic already-sent recovery are approved. Protocol/unsent-request/animation cases need explicit contract coverage against those rules, not routine safeguards or another offline queue. |
| Projections/Stack/combat | Source-versus-occurrence routing, cleanup/retention, slot reuse/placement and source-state preservation are approved. Inspection information after a token source disappears is a genuine remaining case. Apply existing rules to ordinary variants; geometry/control presentation is later UI. |
| Inspection/communication | Capture/association and read-only offline compatibility need specification work, without inventing offline shared events. Chat/comment edit/delete is a real feature choice; an existing dependent comment blocking Undo is not. |
| Empty/invalid/multi-target actions | Actual-mutation/no-invented-node, no-skipping/structural validity, selected-target applicability and entry-reset rules determine ordinary outcomes. Complete tables/tests; numeric/resource bounds and feedback details belong in technical/UI work. |
| Product/platform/privacy | Additional authoring/coordination scope and user-visible recovery/erasure/local-data promises remain explicit choices. Device targets, service/provider/rights, notifications, operations and accessibility presentation retain their proper decision stages. Already-deferred R1 cuts are not gaps. |

Before promoting any older "open" wording into a question, show the concrete
case, the unresolved competing outcomes and why approved rules do not decide it.
If they do decide it, document the resolution in the batch and add/check coverage.
An ambiguity found while completing that work can still become a real gap; this
pruning neither silently approves proposals nor hides incomplete contracts.

## Current precedence and remaining interpretation risks

- **D-232 confirms actual tree editing, not new branches.** Undo removes and Redo
  reinserts a mutation in the existing tree. The assistant's revised-continuation/
  untouched-original proposal was rejected. Do not preserve an original explorer
  alternative, duplicate the suffix as a branch, require immutable ancestry or
  force a post-Undo fork. D-233/D-234 now block edits across forks, D-235 synchronizes
  per-player stacks and D-236 defines server Undo requests. Dependent comments/pings
  already fail replay if their context would change. D-238 settles personal
  deleted-node viewing with parent fallback; IDs/cache/recovery and removed-record
  retention need their
  own contract/technical or product-privacy work, not blanket reference questions.
- **D-224 revises the universal parent-pointer Undo interpretation.** Selective
  mutation removal/reinsertion requires every later mutation's relevant-state
  `(from, to)` tuple to stay identical under a hard suffix-work cap N. Per-mutation
  definitions, N/counting, reinsertion bookkeeping and node/cache/group mappings
  need specification work. D-235/D-236 settle device scope/concurrent Undo. Revealing
  targets are not selectively undoable; no compensating edit is appended. This
  does not restore D-181-style old-field locking to live actions or approve the
  old 25-rule batch. Older pointer/remembered-head fixtures are rewind context,
  not grounds to reject selective editing or silently reuse stale-pointer policies.
- **D-227–D-231 settle strict personal Undo/Redo targeting, not rewritten history.**
  Undo targets the latest still-applied own action even after opponent activity;
  preserve later opponent mutations under replay. Never skip a blocked own target
  or undo the opponent's. Redo is last-undone-first and a blocked next entry disables
  it without skipping/discarding. Only own new play clears your queue. These rules
  combine with D-232–D-236's tree/fork/device/server rules. Insertion representation
  and activation integration still need contracts, not fresh approval of dependent
  comments blocking Undo or reattaching side branches.
- **Earlier D-159/D-160 replaced earlier Undo semantics.** Pointer-rewind Undo is one shared
  parent-node pointer move, regardless of author; composites can require multiple
  presses. D-222 refines Redo/filtering: remember the last active head for Redo
  toward it, graphical any-node activation, and default hiding only for single-player/
  no-exposure inactive continuations. Mixed-player/exposure paths stay visible in
  full. D-223 explicitly confirms the event-comment visibility exception even for
  single-player/no-exposure continuations; hide only those without comments too. The decision
  register preserves original D-90/D-91/D-95 and D-131/D-151 wording for provenance,
  not as current requirements. No last-exposure cutoff, whole-invocation Undo or
  suffix-rollback preview remains. D-161's fixed-node stale-Undo rule belongs to
  that older model; D-236 supersedes it for current selective server Undo requests.
  Remembered-head scope is not an active gap in the replacement stack design.
  Activation/archive contracts and later tree presentation remain distinct work.
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
  needs deliberate UI intent and an explicit server-message flag/boundary;
  D-232 Undo/Redo instead edit the tree without branching intent. D-187 normally
  applies ordinary actions to current state with limited movement/Stack guards,
  not general old-field checks.
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
