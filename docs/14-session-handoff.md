# 14. Session handoff — resume behavior specification

Updated 2026-10-18. This is continuation context, not a new source of requirements.
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
  the owner to request it. The owner explicitly requested a pause for this handoff.
- Keep questions and updates concise; explain only the relevant distinction.
  Do not repeatedly reopen confirmed decisions or ask separate approval for a
  default already established by a general principle.
- New user-specified gestures/spatial behavior are requirements now; exact screen
  drafts, geometry, menus, and accessibility layouts follow in stage 3.
- Synchronize the decision register, primary contracts, worklist, and acceptance
  scenarios. Commit/push at natural checkpoints, not necessarily every answer.
- Do not delegate/spawn agents without an applicable explicit instruction.

## Checkpoint and first reads

- This checkpoint includes **D-001–D-133** and **T-001–T-242**.
- Last previously pushed checkpoint was `0fcdd58`; the commit containing this
  handoff also records D-132 (one advance-and-close event) and D-133 (no duplicate
  attacker on repeated declaration). Use `git log -1` for its actual hash.
- Before editing, inspect `git status` and preserve any new user work.
- Read [combat/Stack/projections](13-combat-stack-and-projections.md), especially
  sections 13.2 and 13.4–13.11; [latest decisions and open questions](09-decisions.md);
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
- D-126: when an attacker disappears, surviving blockers become unassigned
  **at their existing physical/tabletop positions**. Do not move them to a side
  collection, compact, or recenter them. The area may shrink as blockers disappear
  (for example after moves to Graveyard), but survivors stay in place. Real source
  cards remain unchanged by assignment cleanup. Exact rendering is stage 3.
- D-133, the latest answer: declaring already-declared attackers again keeps
  their existing projections and adds only newly declared sources. Preserve
  occurrence identities, placements, and associations; do not recreate/duplicate.
  The selected +tap variant still taps selected sources; without-tapping preserves
  orientation. Other mixed/empty/no-change cases and role coexistence remain open.

### Shared card state and area-specific projection lifetime

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

Continue source multiplicity case by case, now that repeated attacker declaration
is settled. A useful small next question is:

> 1. For Stack, should the same real card be allowed to have several independent
> projections, representing repeated activations, each removed/resolved separately?

This is only a suggested continuation. Do not mistake the question for a decision.
Attacker/blocker coexistence and blocker cardinality are separate cases; D-133 is
not a universal one-projection-per-card constraint. Alternatively continue those
combat cases first, but do not reopen repeated-attacker duplicate behavior.

## Remaining behavior work, not an implementation backlog

- **Q-33–Q-35 / G-11–G-13:** projection multiplicity/role coexistence, missing-source
  Stack reference/display/inspection/actions, duplicate-source multiselection,
  cover/exposure, link/ping target routing, explicit source-Remove versus occurrence
  Remove, and reference cleanup/undo/offline/concurrent behavior.
- Combat: exact declaration quick visibility, single-card entry with no area,
  empty/otherwise-mixed/inapplicable selections, attack destination, blocker drag
  origin/cardinality/order/reassignment/removal, capacity, repeated-combat/area
  reuse, no-change records, and remaining combined-action event mappings.
- Stack: actual-card versus projection entry, placement/order, repeated effects,
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
