# Specification audit and interpretation risks

This is editorial/agent context, not new product behavior. It records findings
from the plain-language summary and tier reorganization.

- **D-159/D-160 replace earlier Undo semantics.** Normal Undo is one shared
  parent-node pointer move, regardless of author; composites can require multiple
  presses. After replacement play, retain ordinary abandoned continuations behind
  a toggle and keep whole exposure/comment continuations visible. The decision
  register preserves original D-90/D-91/D-95 and D-131/D-151 wording for provenance,
  not as current requirements. No last-exposure cutoff, whole-invocation Undo or
  suffix-rollback preview remains. D-161 silently discards no-longer-valid specific
  Undo transitions and reconciles local state, with no notification clutter.
  Non-Undo branch controls and exact pre-fork/forward presentation remain open.
- **D-176/D-177 correct the mutation/playback model.** One node is one individual
  mutation, including individual operations from bulk commands. Every mutation
  immediately extends/branches the chosen path and publishes. Older combined-node
  mappings (D-130/D-132/D-144/D-148–D-150) and MTG-reaction-window node sizing are
  superseded; historical decision rows are provenance, not exceptions. Groups
  are concurrent-animation playback steps, never state pointers/publication units.
  Resume from the last local state, animate backward to the interruption, then
  forward through groups. Pause and scrub exact mutations on the bottom timeline.
  The assistant's last three-rule precedence proposal was not approved.
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
