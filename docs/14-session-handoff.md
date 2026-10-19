# 14. Session handoff — resume behavior specification

Updated 2026-10-19. This is continuation context, not a new source of requirements.
Confirmed decisions are in [the register](09-decisions.md); detailed contracts
and acceptance scenarios take precedence over this summary.

## Current stage and working agreement

- **Documentation only. No application implementation or scaffolding exists or
  is authorized.** Stage 1 behavior specification is incomplete.
- Delivery order (D-115): **1. behavior specification → 2. adversarial review and
  refinement → 3. UI drafts/decisions → 4+. iterative implementation**. No stage
  transition is authorized. Do not start UI drafts instead of finishing behavior.
- Specify all agreed release behavior now, including conveniences and recovery;
  do not defer required behavior to coding. Final schemas/APIs/provider/hosting
  choices are not blanket prerequisites for finishing stage 1.
- Continue case by case, using a small contextualized numbered question/batch.
  Offer a concrete proposal where useful; allow approval together or exceptions.
  After recording an answer, normally ask the next question without waiting for
   the owner to request it. The previous pause ended when design resumed on 2026-10-19.
- Keep questions and updates concise; explain only the relevant distinction.
  Do not repeatedly reopen confirmed decisions or ask separate approval for a
  default already established by a general principle.
- New user-specified gestures/spatial behavior are requirements now; exact screen
  drafts, geometry, menus, and accessibility layouts follow in stage 3.
- Synchronize the decision register, primary contracts, worklist, and acceptance
  scenarios. Commit/push at natural checkpoints, not necessarily every answer.
- Do not delegate/spawn agents without an applicable explicit instruction.

## Checkpoint and first reads

- Current documentation includes **D-001–D-140** and **T-001–T-257**.
- The 2026-10-18 handoff checkpoint was `68c4793`, recording D-132 (one advance-
  and-close event) and D-133 (no duplicate attacker on repeated declaration).
  The 2026-10-19 resumption adds D-134–D-140; inspect git status/log for the current
  committed versus working state.
- Before editing, inspect `git status` and preserve any new user work.
- Read [combat/Stack/projections](13-combat-stack-and-projections.md), especially
   sections 13.2 and 13.4–13.17; [latest decisions and open questions](09-decisions.md);
  and [history/undo](03-history-and-undo.md), especially sections 3.2, 3.4, 3.7.
- Then consult [the capability worklist](12-touch-action-review.md),
  [controls](10-board-and-controls.md), [ping presentation](11-visual-action-language.md),
  [domain](02-domain-model.md), [actions](04-game-actions.md), and
  [acceptance scenarios](08-verification-and-delivery.md).

## Latest confirmed behavior — do not reopen

### Combat entry, interaction, and spatial stability

- D-116: creating the combat area, declaring an attacker projection, and tapping
  its real source are separate supported user actions. Plain declaration does
  **not** tap. An empty area and an untapped declared attacker are valid.
- D-117: phase-appropriate multiselect offers **Declare + tap** and **Declare
  without tapping**. Both create the combat area if absent. Without-tapping
  preserves orientation, not forcibly untaps. Plain/single-card auto-creation and
  exact declaration quick-action visibility remain undecided.
- D-118: drag blockers to specific attackers or valid blank/unassigned slots.
  Attacker spacing grows with assigned blocker count. No block/damage legality
  engine; no ordinary three-card pile limit automatically imposed on combat.
- D-126/D-138/D-139: when an attacker leaves, surviving cards stay grouped in
  their slots; the final attacker's departure leaves a blocker-only pair. No
  individual assignment cleanup or transfer to a side collection/other pairs. Real
  sources remain unchanged. D-138 explicitly supersedes literal fixed tabletop
  positions: preserve slot membership/order, permitting visual reflow from
  resizing/insertion/removal. Exact rendering is stage 3.
- D-133: declaring already-declared attackers again keeps
  their existing projections and adds only newly declared sources. Preserve
  occurrence identities, placements, and associations; do not recreate/duplicate.
  The selected +tap variant still taps selected sources; without-tapping preserves
  orientation. Other mixed/empty/no-change cases remain open.
- D-135: at most one projection per real source per combat
  area across attacker/blocker roles. Dragging an attacker to a blocker position
  moves that same occurrence, not a copy, preserving occurrence identity and real
  source state. This is a UX constraint, not legality enforcement or a limit on
  Stack effects. The proposal allowing separate simultaneous attacker/blocker
  occurrences is rejected. Other entry/role-change paths remain open.
- D-136/D-139: an attacker dragged into a blocker role leaves other cards grouped
  in their slots, with a blocker-only pair if the final attacker leaves. Real
  sources are unchanged by this layout/role change alone.
- D-137: combat has N pairs of opposing attacker/blocker slots,
  supporting multiple projections on either side, not one attacker per pair.
  Empty sides remain valid. Slot size grows/shrinks visually as cards are added/
  removed; no inferred legality/damage/source-card changes. D-139 settles grouping
  without individual assignments; do not introduce all-to-all blocking rules.
- D-138 confirms former P-19: remove a pair immediately only when
  both sides are empty, closing gaps. Retain attacker-only/blocker-only pairs.
  Between-pair drops insert a new pair containing the dragged cards, not implicitly
  an entire origin pair; preserve other pairs' contents and relative order. Remove
  a fully emptied origin by the same rule. Visual reflow/shifts are allowed while
  preserving surviving slot membership/order, explicitly replacing earlier literal
  fixed-position requirements. Combat stays open with no pairs (D-127). Mixed-role
  routing, edge/empty-area targeting, and exact event/concurrency cases remain open.
- D-139: each pair is one visual combat group, with no individual
  attacker↔blocker assignments inside it. Players can use placement and optional
  pings for finer details. Remaining cards stay grouped when one attacker leaves;
  the final attacker's departure leaves a blocker-only pair. This refines earlier
  per-attacker assignment/cardinality/cleanup wording. History/undo/offline retains
  slot membership/roles/order, not a hidden assignment graph. No mandatory pings
  or inferred blocking/damage rules.
- D-140, latest answer: dragging a real battlefield card into an attacker/blocker
  slot creates its projection there, or retargets its existing occurrence in that
  area with preserved identity, rather than duplicating/recreating. The original
  stays in its battlefield position/location with unchanged orientation; no implicit
  tap/untap or actual-card move into combat. Destination side determines role;
  remaining cards retain grouping and fully empty origins disappear normally.
  Multiselect/other-view routing, absent-area entry, same-target no-change records,
  and event/concurrency details remain open. Repeated declaration is distinct from
  drag retargeting and retains existing attacker placement under D-133.

### Shared card state and area-specific projection lifetime

- D-134: allow unlimited independent Stack projections,
  including several from one source, with no per-source or total count limit.
  Repeated explicit creation adds occurrences rather than deduplicating them.
  Remove/resolve each separately without removing siblings or the real source;
  remove its incident links normally. Card fields remain shared (D-123), and all
  effects survive source departure/removal (D-125). Placement/order, duplicate-
  source selection, missing-source behavior, and concurrency remain open.
- D-123: projections are **live views of the same real card**, not independent
  editable copies/snapshots. Card actions through them always affect that real
  card: orientation, corners, counters, notes, printed face/cover, and moves.
  Dragging a projection into Graveyard moves the actual source there with normal
  validation/insertion/reset/token-removal behavior. Creating a projection leaves
  the original in place; this does not permanently pin source location.
- Occurrence placement/assignment/removal is distinct from a card edit. Do not
  turn Remove projection, Stack effect removal, or combat-area closure into
  source-card deletion. Exact card-Remove versus occurrence-Remove entry and
  link/ping routing still need decisions.
- D-124: **anything taking the real card off the battlefield removes its combat
  projections**, whether invoked through the original or any projection, including
  explicit source Remove/token disappearance. Battlefield repositioning alone
  does not qualify. Removed projections' incident links disappear.
- D-125: **Stack effects remain until explicitly removed/resolved**, even when
  their source moves/leaves/is removed, and even when the move was through that
  Stack projection. Do not remove a Stack effect as a side effect of source
  departure. Missing-source display/inspection/card-action applicability is open;
  retaining the effect does not fabricate a new actual card.
- D-120: projection-to-projection links use occurrence endpoints, disappear with
  a removed endpoint, and never silently retarget to real sources. Other mixed
  endpoint kinds and exact picking/cleanup/concurrency still need review.

### Combat closure and combined-action history

- D-122: closing combat clears that area's projections/incident projection links
  while leaving real cards' current positions/orientation/other state unchanged.
  It does not untap attackers, undo prior edits, or clear Stack effects.
- D-127: combat stays open until **explicitly closed**, including across ordinary
  Next step, direct phase changes, End turn, and becoming empty. There is a
  combined **advance out of combat + close area** shortcut. Advance alone and
  close alone remain supported.
- D-128: that quick action appears **only at End of combat**, the last combat
  step, and advances to **Postcombat main**, without changing turn player/number.
  It is not an early-combat prominent skip. Ordinary Next step goes to the same
  next step but leaves the area open. Missing-area and full-path out-of-combat
  behavior remain open; do not invent a later-phase rewind policy.
- D-121: no targeted action is quick-exclusive. Combined targeted shortcuts and
  constituent actions remain reachable through full paths (for example context
  actions); phase/multiselect conditions govern quick visibility, not underlying
  access. Structural/applicability validation still applies. Keep this when
  interpreting the End of combat quick-action restriction.
- D-129 distinguishes user invocation, undo unit, tree event, and atomic boundary.
  **D-131 is the current general principle:** Undo reverses **one user action and
  its whole group**. A user action must have **multiple events wherever MTG permits
  reactions at distinct points**, with real review/interruption boundaries. One
  Undo may span those events. Specify mappings/exceptions individually; do not
  leave every shortcut's default undo scope undecided or assume every action
  necessarily has multiple events. This refines the earlier case-by-case-only
  interpretation of undo scope.
- Reaction-point design does **not** authorize runtime rules/priority adjudication,
  Pass, priority-holder state, or card-text interpretation. Players judge timing;
  specified events preserve places to review/interrupt/branch.
- D-130/D-132: advance-and-close is **one event listing both effects and one Undo**
  restoring the prior step and complete area/projections/assignments/links/placement.
  Review/interruption is before or after it, not between its effects: closing
  the presentation area adds no separate MTG reaction point. Do not generalize
  this one-event mapping to other combinations.
- D-119: pings show automatic order numbers across **all pings in the same grouped
  event**, not per attacker/source/purpose. No automatic damage interpretation.

## Suggested next question — not yet asked or approved

Continue multiselect drag routing after D-140 settles real single-source entry.
A useful small next question is:

> 1. When several selected real battlefield cards are dragged onto one combat
> slot, should they all join that same target slot and side, rather than each
> receiving its own pair? Proposal: yes, applying create/reuse per source.

This is only a suggested continuation. Do not mistake the question for a decision.
This does not settle mixed source/projection or existing attacker/blocker selection
routing, absent-area entry, or final drop geometry. Projection-drag reverse role
changes remain separate. Do not reopen visual grouping, slot
lifecycle/reflow, combat occurrence uniqueness, repeated attackers, or Stack multiplicity.

## Remaining behavior work, not an implementation backlog

- **Q-33–Q-35 / G-11–G-13:** remaining combat role-change/entry cases, missing-source
  Stack reference/display/inspection/actions, duplicate-source multiselection,
  cover/exposure, link/ping target routing, explicit source-Remove versus occurrence
  Remove, and reference cleanup/undo/offline/concurrent behavior.
- Combat: exact declaration quick visibility, single-card entry with no area,
   empty/otherwise-mixed/inapplicable selections, attack destination, other-view
   entry/within-side order/removal controls, multiselect/mixed-role side routing/
   edge drops, capacity, repeated-combat/area reuse, no-change records, and
   combined-action event mappings.
- Stack: actual-card versus projection entry, placement/order,
  quick-exit metadata/categories/multiface/missing data, destination routing and
  manual overrides. No ability picker/mandatory ability text is approved.
- Pings: ordering capture, Unping/re-add numbering/gaps, selected-set ordering,
  group split/join/coalescing, historical numbers, and projection targeting.
- **Q-04/Q-31:** per-action reaction-point event mappings/exceptions, action identity
  versus editable presentation groups, optional group split/join undo targeting,
  partial-group interruptions, dependency/collaborator safeguards, exposure/comment
  cutoff rendering/coalescing, redo reconciliation, durable capture/publication,
  offline/archive presentation, and retention. One-Undo scope must not erase
  required intermediate history or hide reaction points.
- **Q-17:** random source/algorithm/bounds/offline/concurrent/idempotent/audit details.
  New roll/flip/Shuffle samples freshly even after Undo; replay/retry/redo uses
  recorded results/orders. Equal outcomes by chance are allowed.
- Older pending work remains: token placement/initialization/reference handling,
  View/Reveal lifecycle/history, metadata mappings/default refresh/reset edges,
  applicability versus structural errors, numeric grammar/no-op records, grouping/
  playback, communication edit/delete, and remaining setup/product behavior.
- Use the full [open-question register](09-decisions.md) and capability worklist;
  this is not an exhaustive replacement. Do not silently drop required scope.

## Baseline constraints easy to accidentally regress

- Two cooperative participants initially; either can edit all state and inspect
  all information. Covered presentation is not opponent access control.
- Downloaded games readable offline. Completed actions auto-queue/sync without
  Send, group completion, or required chat/narration. Preserve one active branch,
  interruption/conflict alternatives, reversible branch controls, and recovery.
- No inferred casting/targets/payment/damage/resolution/legality. Explicit MTG
  steps, combat assignments, projections, and contextual Stack conveniences are
  nevertheless required. No priority tracking/UI/Pass (D-105).
- No turn-number editor or extra-turn scheduler; manual repeated steps may make
  counting approximate. End turn is one tap beside Next step. Separate Untap/
  Draw and +advance variants remain; no inferred skip-untap exceptions or loss
  from an empty Library. Those combinations still need reaction-event mappings.
- Retained cards entering configured Library/Graveyard/Exile lose counters and
  restore retained current-face default corner text; tokens disappear instead.
  Keep history/undo. Other unrelated move fields remain unchanged unless approved.
- Ordinary visual piles hold at most three actual cards, but do not apply that
  limit automatically to combat groups, Stack, reference trays, or special piles.
- Generic links, pings, combat assignments, and source-card state are distinct.
  No Attach/follow-group feature. Clone creates fresh independent tokens, not
  projections; a token catalog selection creates one token immediately.
- Undo restores the requested rollback state. Retain the original undone path
  through its last actual hidden-information exposure **or event comment**, not
  trailing ordinary steps; retain internal recovery. Reviewing it does not activate
  it. Submitted game-wide Chat is not undoable (D-94); event comments are ordinary
  current-path nodes retained after undo (D-95).
- Trim leading/trailing whitespace on completed user-entered text; preserve
  internal spacing/newlines. Counter/notes/corner semantics remain field-specific.
- Card-provider research is feasibility only; no provider/terms/caching policy is
  selected. Earlier all-technical-artifacts-first gates are superseded by D-115.

## Validation and shutdown note

- For documentation checkpoints: run `git diff --check`, check local Markdown
  target files, and ensure decision/scenario table IDs are unique and contiguous.
  Check for obsolete proposals contradicting the latest confirmed answer.
- There are no application tests/builds to run yet. Documentation checks do not
  mean stage 1, adversarial review, UI decisions, or implementation are complete.
- The owner requested saving/committing/pushing and then shutting down this
  computer including its Windows host **for this handoff session only**. The
  environment is WSL2, Ubuntu-26.04; Windows PowerShell/shutdown executables are
  accessible. Do not automatically repeat shutdown in a future session. This
  document does not itself certify that shutdown succeeded.
