# 14. Session handoff — resume behavior specification

Updated 2026-10-19. This is continuation context, not a new source of requirements.
Confirmed decisions are in [the register](decisions.md); detailed contracts
and acceptance scenarios take precedence over this summary.

## Saving edits — D-178

The owner now requires commit and push after every completed edit batch, before
replying, without a separate request. Use `./scripts/save.sh <edited paths...>`
with its fixed message `Update project`; do not spend time crafting messages.
Run applicable checks, inspect staged changes and exclude unrelated work. With
no arguments the helper uses the existing index; with no new changes it still
pushes, allowing a retry. Report failures without force pushing/bypassing checks.
This supersedes D-57's occasional-checkpoint cadence. See [AGENTS.md](../../AGENTS.md).

## Current concurrency correction — D-179

**Latest setup decisions — D-188–D-199:** owner requires login before the main
menu; registration is available. Main menu has Logout, Manage decks and Play.
Users can add users; Friends as a separate name/menu remains tentative. Play
shows current games including pending invites. Deck editor currently supports
list import only, not a full manual deck editor/export. Each game initially has
a lobby where players choose decks and manage mainboard/sideboard. Both players
press Ready, then the game starts; the assistant's immediate-table proposal was
not approved. Primary contract:
[accounts, decks, and lobby](../design/03-contracts/accounts-decks-and-lobby.md);
T-396–T-405. D-191 approves both follow-ups: changing your chosen deck or mainboard/
sideboard clears your own Ready, preserving the other player's; lobby deck changes
affect only that game, not the saved deck. Do not reopen these choices. D-192:
owner rejected automatic opening draws and requires mulligan quick actions,
not manual moves alone. D-193 specifies two actions: shuffle-and-draw N, default
seven, and shuffle-and-draw current hand size minus one. Both are Library context
actions. D-194 settles both clarifications: return all hand cards to Library before
shuffling/drawing, and quick visibility only during turn 1's Untap step, not all
Beginning steps or each player's first turn. The minus-one count uses pre-return
hand size. Do not reopen those choices, approve manual bottoming or fix N to seven.
Per-card mutation order, concurrent count timing and count edges remain open;
automatic starting shuffle was not separately approved. See T-406–T-413.
D-195 confirms the next invitation proposal: choose an added user to invite;
recipient Accept/Decline in Play; accepting opens the lobby, not Ready or active
play. See T-414–T-416. D-196 approves username search and saving users as contacts
without mutual friend-request approval. It does not automatically add you to the
other user's contacts or accept a game invite. See T-417/T-418. D-197 confirms
all four account recommendations: register with username/email/password; log in
with email/password; forgotten-password reset by email; other users see username,
not email. Exact validation/verification/reset/session mechanics remain open, not
the selected method/channel/privacy. See T-419–T-422. D-198: owner approved paste
import, optional sideboard, unknown/ambiguous errors without substitution,
preserved original text and no saving until resolved, then said to use a standard
format exported by many MTG tools. Primary contract specifies common Arena/MTGO-
style text conventions with optional set/collector details and standard sideboard
notation, backed by ManaBox/Wizards references. No bespoke format, Arena-only card
pool, full editor/export, URL/CSV/XML import or provider is selected. See
T-423–T-427. D-199: owner approved named saved decks, re-import Replace/Save as new
and unchanged already selected lobby/game copies; explicitly added naming/
renaming and choosing one of a deck's cards as cover art. Card-list editing remains
import-only; metadata controls do not approve a full deck builder or art upload.
See T-428–T-432. Naming defaults/duplicates, deletion, cover fallback/re-import
metadata retention and remaining dialect/extra-section/printing/count/resolution
details remain open. Next useful substantial topic: initial table arrangement/
values and starter selection, not tiny cover-art variants.
Exact search/contact removal/privacy, remaining invitation lifecycle, other readiness
details, start-versus-edit races, account flows and initial values remain open.
D-108 starter/turn-1 Untap and all gameplay sync/history
rules remain. No UI drafts, application code or stage transition are authorized.

**Latest acceptance correction — D-187:** owner changed their mind: normally
accept ordinary actions unless genuinely impossible. Do not compare old
orientation, note text, counter Set or other ordinary field values. Absolute
edits apply requested values to current state; relative edits use current
applicable data, preserving unrelated fields and actual before/after history.
Only card movement and Stack additions/projection creation have optimistic
conflict guards. Moves retain source/destination checks. Owner clarified the
Stack rule: **send current client Stack state with the request; compare against
current server Stack state and reject if different**. Compare-and-apply atomically;
no opponent/seen/read-receipt detector or whole-game-head lock. Later queued
entries use locally predicted pre-action Stack state without acknowledgement
waits. Exact snapshot encoding/equality representation and provisional-ID mapping
remain technical design. The [25-rule batch](../design/03-contracts/optimistic-locking.md)
is superseded, not approved or awaiting numbered review. Preserve D-161 Undo,
D-179 deliberate branches and D-183–D-185 pipeline/reconciliation. Do not infer
new feature scope or implementation authorization. See T-387–T-395 and corrected
T-363–T-366/T-384; D-186 deltas remain, but its Set comparison is superseded.

**Latest reconciliation clarification — D-185:** current-board reconciliation
targets accepted history plus remaining valid local predictions and animates only
the actual correction, not visible rewind/replay of unaffected work. For a failed
move followed by a successful tap, return the card while keeping it tapped.
Preserve accepted local/opponent work and valid pending predictions; correct
genuinely invalid later predictions too. Acknowledgement alone adds no animation. Do not fabricate server
results or rewrite in-flight requests; exact bookkeeping/scheduling remains open.
D-177 deliberate history/interruption playback is unchanged. No implementation
or stage transition is authorized. See T-378–T-382.

**Latest ordering clarification — D-184:** clients send actions in action order;
the server validates/applies in arrival order. Preserve order in delivery rather
than add sequencing dependency metadata or reconstruct client order on the server.
Multiple actions remain in flight without acknowledgement waits (D-183). Other-
player arrivals can interleave; every action gets its own check/result, including
after an earlier rejection. Exact transport, provisional-reference mapping and
reconnect recovery remain open, not the ordering policy. Semantic references and
idempotency remain; no application code or stage transition is authorized.
See T-375–T-377.

**Latest clarification — D-183:** allow a small local online send queue, with
individual actions sent concurrently and applying/failing individually. Keep
normal interaction responsive through immediate local updates; users should not
notice network delay for most, ideally all, controls. A rejection is not whole-
queue/sequence cancellation, and online-only does not mean no queue or one request
at a time. The assistant's pause-until-resolved interaction-gate proposal was not
approved. D-182 still excludes extended offline gameplay. Exact sizing/saturation,
wire ordering/new-object dependencies, animation composition and real connection-
loss recovery remain open; do not turn those gaps into a routine acknowledgement
barrier or another tiny per-control questionnaire. See T-371–T-374.

**Latest scope correction — D-182:** initial delivery requires a connection to
make game actions, including shared Undo/branch controls. Downloaded history is
still readable offline, but there is no offline gameplay creation/queue replay.
This supersedes D-09's offline-action scope and older offline gameplay/Undo/random
execution obligations below. Do not continue designing a full offline queue or
record the assistant's queued-sequence proposal as approved. D-180 optimistic
latency hiding and animation, D-179 interruption and D-181 locking remain online.
Connection readiness, online-pending limits and lost-response/in-flight recovery
remain narrow gaps; do not infer one action at a time, immediate failure on loss,
or permission to discard potentially committed work. Offline inspection creating
new exposure records also needs a read-only-compatible policy. No implementation
or later delivery stage is authorized. See T-367–T-370.

**D-180 follow-up:** immediately update local state to hide latency. If rejected,
animate that change backward as if undone; animation explains the correction,
not a required textual-error/acknowledgement flow. Animate incoming opponent
mutations too. Local rejection rollback is not shared Undo, interruption intent,
a new mutation or branch. Preserve personal review positions and D-177 grouping;
sync/acceptance does not wait for animation. Detailed composition with later
pending work remains open. The assistant's proposal to proceed with independent
queued actions and retain dependent ones was not approved by this answer.
See history section 3.3 and T-358–T-362.

**Historical D-181:** relevant-field preservation and no whole-card replacement
remain. D-187 supersedes previous orientation/note comparisons; ordinary actions
normally apply to current state, with actual applicability/structure checks.
Do not restore the historical comparisons from older summaries/tests.

The owner rejected automatic conflict branching. An interruption must be a
deliberate UI choice to go back and continue from a chosen state, carried by an
explicit flag and boundary in the server message. Undo supplies continuation
intent in the background, without another user step. Scrubbing, a stale view or
editing an inactive line alone is not permission to branch.

Otherwise apply the action at the current server head under D-187's permissive
baseline and limited guards. For movement, both source/destination conditions
must hold. A move into empty space
requires the card still at its stated source and the destination still empty.
Compatible intervening work does not invalidate the action; append at the current
head if valid, reject if not, with no implicit fork. Approved pile joins need their
own destination conditions; this example is not a blanket ban on occupied targets.
An explicitly requested interruption makes its new branch current without voting.

D-179 supersedes D-13/P-11 and implicit edit-from-review/always-fixed-parent
readings of D-176/D-177, not one mutation per node, immediate publication or
personal animated playback. The assistant's preceding four-rule conflict proposal
was not approved. Do not resume the no-ping-pong conflict-branch questionnaire.
Remaining substantial work: precise move guards and Stack snapshot representation, exact reconciliation
bookkeeping/scheduling within D-185, connection loss during requests,
simultaneous explicit interruptions and stale non-Undo controls. Exact wire fields
and final continuation-control layout are not yet decided. Primary behavior is
[history sections 3.3/3.5](../design/03-contracts/history-and-undo.md#33-immediate-action-synchronization);
verification adds T-349–T-357. D-161's silent stale-Undo discard remains specific
to Undo, not ordinary rejected actions.

## Current correction — mutation tree and playback, D-176/D-177

The owner corrected the assistant's underlying model. **One node is one individual
mutation**, such as moving or tapping one card, deterministically reversible in
both directions. Every mutation immediately becomes a leaf extending/branching
the chosen node/path and publishes. Bulk controls issue individual operations in
a sensible recorded order. A client's state points to a mutation, not a group.

Groups only control navigation/representation: one playback step with concurrent
animation. On return after an opponent's interruption, start at the last local
state, animate rollback fairly quickly to the fork, then play the new continuation
automatically or step by step. Automatic playback is interruptible. While paused,
show a horizontal bottom timeline draggable left/right to select the exact node
where play can continue. Traversal is personal, not shared Undo/publication.

D-176 supersedes older atomic bulk/combined-node mappings in D-130/D-132/D-144/
D-148–D-150 and MTG-reaction-window node sizing. Keep shortcut effects, existing
order/placement defaults, validation, node-level Undo and retained alternatives.
Record actual mutations only: empty draws fabricate no node. Cleanup/reset
decomposition/order still needs consistent contracts, not discretionary atomic
bulk mappings. D-177 supersedes next-mutation-only normal playback; groups animate
concurrently while exact intermediate mutation states remain selectable.

The assistant's preceding three-rule forward/backward precedence proposal was
**not approved**. Do not repeat tiny grouping/publication questions or assume
groups are gameplay units. Re-anchor the larger asynchronous-play batch in this
model before proposing substantive remaining behavior. Primary contracts:
[history](../design/03-contracts/history-and-undo.md),
[grouping](../design/03-contracts/history-grouping.md); T-337–T-348.

## Current batch — asynchronous play loop, D-175

The owner approved planning a larger behavior-only batch: review/grouping,
inspection/annotations, interruption/forward return, offline/competing edits and
staying oriented. No implementation or stage transition is authorized.

D-175 confirms the first four-rule bundle: each explicit View/Reveal opening
starts a separate review group; combat-area creation/removal joins combat edits;
Stack rearrangements group separately from entries/removals; each explicit
Stack-effect removal stays separately visible. Keep author/companion rules and
forward dismissal/closure. D-176 resolves bulk removal as individual mutations. See the primary
[grouping contract](../design/03-contracts/history-grouping.md) and T-330–T-336.
The later correction above supersedes the planned precedence questionnaire.
Do not reopen the approved display/projection presentation rules.

## Earlier discussion — D-159–D-161

The owner replaced whole-invocation Undo with one shared active-pointer move to
the parent tree node, regardless of author. Retain undone descendants and permit
explicit forward activation through the tree; review alone remains personal.
After replacement play, ordinary abandoned continuations are hidden, not deleted,
and available through a show-hidden-branches toggle. D-176 subsequently corrects
node granularity to individual mutations; an actual Draw + advance takes two
Undo steps, while an empty draw adds no node. The older D-131/D-151
Undo-scope decisions are superseded. D-160 confirms multiple presses for
composites and keeps the whole abandoned continuation visible if it contains
exposure or an event comment, including ordinary tails. There is no cutoff.
The primary current rule is
[history section 3.0](../design/03-contracts/history-and-undo.md#30-current-undo-model--parent-pointer-navigation-d-159d-160).

D-161: Undo captures a specific node-to-parent transition; if no longer valid,
discard it silently and reconcile client state, including on reconnect. No toast,
warning, notification, confirmation or retry chore. Do not retarget to unseen work
or lose other queued edits. Preserve idempotent outcomes on delivery retry.

This Undo-design checkpoint has passed Markdown target/anchor, whitespace and
decision/scenario-ID checks; repeat them for any further changes. Do not reopen
parent-node scope, multiple composite Undo presses, retained alternatives,
whole-branch visibility or silent stale-Undo discard. Other history gaps remain:
automatic grouping/ping association, explicit forward/pre-fork presentation,
non-Undo branch controls and exposure capture/coalescing. The next substantial
design topic should be automatic groups and ping association, not tiny Undo variants.
The owner requested commit/push after the design work and explicitly approved
including the existing uncommitted tier reorganization with these Undo changes.
No implementation or later-stage transition is authorized.

## Grouping-design discussion — D-162–D-174

The owner rejected one invocation = one group. Use incoming action type and
current group contents, with group-start markers on the timeline. D-163–D-174
confirm forward taps; pings add no group-start marker or ownership-picker step;
non-Stack card moves group by ordered location tuple, including taps interleaved
between matching moves; real-card Stack moves and each Stack projection remain
separately visible with tap/ping companions; ordinary card/face/cover edits and
token removal, player values/counters and persistent-link creation/removal group
together; event comments join the preceding group without starting a new one;
combat projection creation/repositioning groups together; display dismissal/combat closure
groups forward, not backward; same-target Shuffles group; dice/coin runs start
separately from prior play and group with the following action; phase/turn tracking
joins the previous group. Undo/resuming does not itself insert group markers. D-170 settles the
author rule as highest precedence over every grouping rule, including pings and
forward/backward companions. B's ping after A's action starts B's group; no
mixed-author group or ownership picker is needed.

The [grouping inventory](../design/03-contracts/history-grouping.md) covers the
existing capability catalog, including proposed scope, and separates ordinary
game actions from exposure/annotation/control/local activities. Exact action/event
mappings are not all complete. Do not reopen resolved real-card Stack, tap
interleaving, combat repositioning, closure direction or ping ownership questions.
Do not reopen the author-change precedence question resolved by D-170.
D-171 records approval of the token-removal, forward dice/coin and backward
tracking recommendations together. Do not reopen them as tentative. Applying the
closure rule to all future temporary-area types is still only a proposed
generalization; existing View/Reveal/combat closure is confirmed.
D-172 approves the player-value/counter, persistent-link and event-comment rules.
Participant-association edits and historical/comment-revision semantics remain
open. D-173 approves token creation/Clone grouping together with interleaved
customization: `[create token; edit; Clone; edit]`. D-174 rejects non-token Remove
entirely: cards do not disappear during the game, they move between locations.
This supersedes D-85's ordinary-card context Remove and later source-deletion
assumptions; do not ask for its grouping or reintroduce it through bulk,
correction or projection controls. Token Remove/disappearance and occurrence-only
removal remain. Next review inspection/projection grouping without reopening
token creation/Clone, the approved edit family or token removal.

The owner requested an MTG face-change rules check. Official Comprehensive Rules
effective 2026-09-25 were checked: CR 116.1/116.2b/702.37e make morph face-up a
non-stack special action that cannot itself be responded to; the face-down spell
and resulting triggered abilities use the stack. CR 603.3/117.2e distinguish
trigger placement/resolution timing. The grouping page contains source links and
the recommendation subsequently approved in D-169: group ordinary face/cover
edits with card edits, representing explicit spells/abilities separately on Stack. No automatic
trigger detection, priority state or legality inference is authorized.
Do not demand approval of every possible pair or invent rules from card text.
Keep remaining policies explicitly proposed. Core grouping cases have
T-297–T-329 acceptance scenarios. Detailed node review/Undo/interruption remains
even when normal group review applies dismissal plus the next action together.
No grouping decision authorizes a publication delay or deleting an intermediate
state. Continue from remaining action categories and rule precedence, not tiny
questions about already-settled tuples or which user-facing owner to pick for pings.
The Undo/tier-reorganization checkpoint was committed/pushed as `815a912`;
the grouping checkpoint through D-171/T-320 was committed/pushed as `5a707aa`.
D-172–D-174 are subsequent discussion work; inspect git status/log for their working state.
Follow D-178: validate, commit and push after every completed edit batch.
Remaining action families and same-author compound precedence are explicitly open;
this is not completion of grouping design or authorization to implement.

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
- Latest owner correction: the sorting questions became unnecessarily elaborate.
  Re-anchor in D-158's spatial model. Do not resume speculative mixed-view sorting
  questions or turn incidental collection-order details into product requirements.
- Latest owner request: summarize the whole current design as simply as possible
  without losing behavior, and identify genuine gaps. The result is
  [Current design, in plain language](../design/02-experience/behavior.md). This is a
  consolidation/audit, not approval of proposals or a new round of tiny questions.
- Follow-up owner request: organize the design into high-to-low tiers, with agent
  decisions/handoffs separately. The [tiered index](../README.md) is now the entry
  point; product principles, experience, contracts, technical proposals and
  verification live under `docs/design/`. Agent/process material lives here.
  Scope/gaps are now a separate Tier 2 page; audit notes and delivery gates are
  separate agent pages. Original chapter numbers and requirement IDs are retained.
- New user-specified gestures/spatial behavior are requirements now; exact screen
  drafts, geometry, menus, and accessibility layouts follow in stage 3.
- Synchronize the decision register, primary contracts, worklist, and acceptance
  scenarios. Validate, commit and push after every completed edit batch (D-178).
- Do not delegate/spawn agents without an applicable explicit instruction.

## Checkpoint and first reads

- Current documentation includes **D-001–D-199** and **T-001–T-432**.
- The 2026-10-18 handoff checkpoint was `68c4793`, recording D-132 (one advance-
  and-close event) and D-133 (no duplicate attacker on repeated declaration).
  The 2026-10-19 checkpoints `fe0f146` and `c23632c` record D-134–D-140 and
  D-141–D-146 respectively. Checkpoint `7b33885` records D-147–D-151 and `8ba9419`
  records D-152–D-156. Current documentation adds D-157/D-158; inspect git status/log
  for the latest working state.
- Before editing, inspect `git status` and preserve any new user work.
- Start with [product principles](../design/01-product/overview.md),
  [user-facing behavior](../design/02-experience/behavior.md), and
  [scope/gaps](../design/02-experience/gaps.md). Understand the whole design before
  resuming a detailed feature conversation.
- For combat-specific detail, read [combat/Stack/projections](../design/03-contracts/combat-stack-and-projections.md), especially
   sections 13.2 and 13.4–13.31; [latest decisions and open questions](decisions.md);
   and [history/undo](../design/03-contracts/history-and-undo.md), especially sections 3.2, 3.4, 3.7, 3.11.
- Then consult [the capability worklist](touch-action-review.md),
  [controls](../design/03-contracts/board-and-controls.md), [ping presentation](../design/03-contracts/visual-action-language.md),
  [domain](../design/03-contracts/domain-model.md), [actions](../design/03-contracts/game-actions.md), and
  [acceptance scenarios](../design/05-verification/acceptance.md).

## Latest confirmed behavior — do not reopen

### Combat entry, interaction, and spatial stability

- D-116: creating the combat area, declaring an attacker projection, and tapping
  its real source are separate supported user actions. Plain declaration does
  **not** tap. An empty area and an untapped declared attacker are valid.
- D-117: phase-appropriate multiselect offers **Declare + tap** and **Declare
  without tapping**. Both create the combat area if absent. Without-tapping
  preserves orientation, not forcibly untaps. D-146 also auto-creates for single-
  card declaration; D-147 fixes multiselect quick visibility to Declare attackers.
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
  fixed-position requirements. Combat stays open with no pairs (D-127). D-142
  settles target-slot role routing; edge/between-pair/empty-area targeting and
  exact event/concurrency cases remain open.
- D-139: each pair is one visual combat group, with no individual
  attacker↔blocker assignments inside it. Players can use placement and optional
  pings for finer details. Remaining cards stay grouped when one attacker leaves;
  the final attacker's departure leaves a blocker-only pair. This refines earlier
  per-attacker assignment/cardinality/cleanup wording. History/undo/offline retains
  slot membership/roles/order, not a hidden assignment graph. No mandatory pings
  or inferred blocking/damage rules.
- D-140: dragging a real battlefield card into an attacker/blocker
  slot creates its projection there, or retargets its existing occurrence in that
  area with preserved identity, rather than duplicating/recreating. The original
  stays in its battlefield position/location with unchanged orientation; no implicit
  tap/untap or actual-card move into combat. Destination side determines role;
  remaining cards retain grouping and fully empty origins disappear normally.
  Other-view applicability, absent-area entry, same-target no-change records,
  and event/concurrency details remain open. Repeated declaration is distinct from
  drag retargeting and retains existing attacker placement under D-133.
- D-141: selected real battlefield cards dragged onto one combat
  slot all join that same slot and side, creating/reusing each source's occurrence,
  not one pair per source. Preserve reused identities and real source state; other
  cards retain grouping, fully empty origins disappear normally. One selected
  drag uses parent-node Undo (D-159); event mapping remains open. D-142 extends
  routing to mixed combat-role/view drags. Duplicate-source views and within-slot
  insertion order remain open.
- D-142: combat is simply paired slots, one attacker and
  one blocker/defender slot. Drag any collection onto a slot and all go there
  side-by-side, taking that slot's role regardless of old roles/views. **Reject
  the assistant's proposed mixed-role preservation/splitting rule.** Includes
  single projection drags in either role direction and mixed real battlefield/
  combat-projection collections. Create/reuse per source, preserving identity/
  uniqueness and real source state; remaining groups/empty-origin cleanup stay
  unchanged. Do not form an ordinary overlapping pile. Moved-collection ordering,
  other-view applicability, duplicate-source view handling, edge/between-pair
  targeting, and event/concurrency details remain open.
- D-143: within a combat slot, dropping between existing cards
  inserts there; dropping into unused slot space appends. Preserve other cards'
  relative order. This is not a between-pair drop creating a pair. Target-role
  routing/create-or-reuse/source preservation/normal Undo remain unchanged.
  Ordering the moved collection itself, exact hit regions, no-change records,
  and event/concurrency composition remain open.
- D-144: **Remove from combat is a context action** for one or
  several selected combat projections. Remove only targeted occurrences/incident
  links, leaving real source state and Stack effects unchanged. Preserve other
  grouping/order and apply fully-empty-pair cleanup without area closure. D-176
  records individual occurrence removals with per-node Undo and retained
  slots/roles/order/links/cleanup context.
  Source-card Remove and real-source/mixed-selection access remain separate;
  drag-back-to-source behavior and final menu layout are not decided.
- D-145: declaration shortcuts give each newly declared attacker
  its own pair; already-declared attackers retain their slots/grouping/order,
  including multi-attacker groups. Not the same as dropping a collection into a
  chosen slot. D-153 fixes append placement; order among new pairs remains open.
  D-152 settles existing blockers.
- D-146: declaring a single card auto-creates an absent combat area
  without a separate creation action or implicit tap/source movement. Reuse an
  existing area without clearing/replacing it. Manual creation remains available;
  absent-area drag targeting and additional single-card quick offerings remain open.
- D-147: both multiselect declaration quick actions appear only
  during Declare attackers. Keep full context-action paths for the combinations/
  constituents at other steps under D-121. No implicit execution, new single-card
  quick offerings, participant/turn-player locks, or runtime legality/priority checks.
- D-176 supersedes D-148/D-149/D-150's combined-node mappings. Declaration
  shortcuts issue individual area/occurrence changes and individual real-source
  taps for +tap. Without-tapping preserves orientation. Untap all issues individual
  untaps followed by any tracking advance. Each mutation publishes immediately;
  one Undo follows one parent, not the whole shortcut.
- Draw + advance records an actual card move, if any, followed by advancement.
  D-176 supersedes an empty no-change draw node. No extra tap/Send or publication
  gate. D-160 retains an abandoned draw-exposure continuation including its
  ordinary tail. Exact feedback/concurrency/out-of-step behavior remains open.
- D-152: a declaration shortcut including an existing blocker moves
  that same occurrence into its own new attacker pair, preserving identity/links.
  Apply variant tapping normally; existing attackers retain slots/grouping and
  other origin cards stay grouped. Remove only fully empty origins. Preserve
  individual mutation nodes under D-176, retaining prior roles/slots/order/pairs/
  source orientation as applicable. Order among new pairs and source-feedback/duplicate-
  view/concurrent handling remain open.
- D-153: newly created declaration pairs append after surviving
  existing pairs; preserve existing contents/order and retained attackers' slots.
  Applies to fresh attackers and converted blockers. Ordering multiple new pairs
  among themselves remains open.
- D-154: preserve current relative order when dragging a collection
  from one combat slot, including subsets/same-slot reorders. Use source-slot order,
  not selection-tap order, and insert/append that sequence under D-143. Preserve
  other cards' order, identities/source state, and normal Undo. D-156 settles
  cross-slot combat order; D-157 real battlefield order. Mixed-view and multiple-new-pair order remain open.
- D-155: combat has two horizontal rows, blockers above and
  attackers below. Each opposing slot pair spans the rows. This clarifies spatial
  layout only; the previous proposed pair-by-pair, attacker-first cross-slot drag
  ordering was not approved. D-156 separately approves row-first ordering.
- D-156: cross-slot combat drags use selected blockers left-to-right,
  then selected attackers left-to-right, preserving each slot's pre-drag order.
  Insert/append that sequence together; all take the destination role. Preserve
  identities/links/source state, remaining grouping/order, normal empty-pair cleanup,
  and parent-node Undo (D-159). D-157 settles real battlefield order. Mixed-view order, order among new declaration
  pairs, duplicate-source views, and exact concurrency remain open.
- D-157: real battlefield cards dragged together into combat use
  their pre-drag visual order, top-to-bottom then left-to-right, not selection-tap
  order or their prior combat placement. Create/reuse and insert/append occurrences
  in that sequence without changing originals. Normal destination-role routing,
  identity/links/grouping/cleanup/Undo remain. Mixed/duplicate views, new declaration
  pairs, irregular/overlapping layout ordering, and concurrency remain open.
- D-158, latest correction: combat is a separate area between the player's play
  area and the opponent's. Battlefield drags create/reuse projections where dropped,
  leaving originals in place. Declaration quick actions add new attackers to the
  right of existing combat cards/pairs; already-declared attackers stay put.
  Collection-order defaults never override the drop target. The projections-first
  mixed-selection sorting proposal is withdrawn, not approved or awaiting approval.

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
   source-card deletion. Non-token cards cannot be deleted (D-174). Exact
   token-Remove versus occurrence-Remove entry and
  link/ping routing still need decisions.
- D-124: **anything taking the real card off the battlefield removes its combat
  projections**, whether invoked through the original or any projection, including
   explicit token Remove/token disappearance. Battlefield repositioning alone
  does not qualify. Removed projections' incident links disappear.
- D-125: **Stack effects remain until explicitly removed/resolved**, even when
   their source moves/leaves or a token source disappears, and even when the move was through that
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
- D-176 distinguishes invocation, individual mutation node and playback group.
  **D-159/D-160 are the current Undo principle:** one press moves the shared active
  pointer to its parent regardless of author. Node granularity is not based on
  MTG reaction windows. Groups only control concurrent-animation playback steps
  and representation; they never change parents, publication or Undo stepping.
- Reaction-point design does **not** authorize runtime rules/priority adjudication,
  Pass, priority-holder state, or card-text interpretation. Players judge timing;
  specified events preserve places to review/interrupt/branch.
- D-176 supersedes D-130/D-132: advance-and-close issues distinct tracking and
  area/occurrence mutations, not one combined node/Undo. Exact cleanup sequencing
  remains to be specified consistently; no hidden atomic bulk fallback.
- D-119: pings show automatic order numbers across **all pings in the same grouped
  event**, not per attacker/source/purpose. No automatic damage interpretation.

## Continuation after owner correction

Start with the [product principles](../design/01-product/overview.md) and
[plain-language behavior](../design/02-experience/behavior.md). Keep the
small set of general usability rules central; distinguish substantive missing
outcomes from details that can share one consistent default. Do not automatically
turn the gap list into one owner question per edge case.

The owner challenged the relevance of the mixed-view sorting proposal. It is
withdrawn. No ordering question is pending. The next continuation should review
the existing contracts against D-158's simple model, not request another sorting
rule: separate central combat area, projections rather than moved originals,
drag placement at the drop target, quick declarations at the right edge.

Preserve earlier approved collection-order defaults only as internal order within
the dropped collection; they never determine a different destination. Do not
reopen uniform destination roles, paired grouping/two rows, occurrence uniqueness,
source preservation, existing-attacker reuse, or approved event/Undo mappings.

## Remaining behavior work, not an implementation backlog

- **Q-33–Q-35 / G-11–G-13:** remaining combat role-change/entry cases, missing-source
  Stack reference/display/inspection/actions, duplicate-source multiselection,
   cover/exposure, link/ping target routing, token-source Remove versus occurrence-only
  access/mixed-selection routing, and cleanup/undo/offline/concurrent behavior.
- Combat: absent-area drag targeting, additional single-card quick offerings,
  empty/otherwise-mixed/inapplicable selections, attack destination, other-view
  applicability/other removal paths, edge/between-pair targeting, mixed-view/
  irregular/overlapping battlefield ordering and ordering new pairs among themselves, capacity,
  repeated-combat/area reuse, no-change presentation, and remaining event/concurrency
  cases, including partial-group interruption/undo and out-of-step mappings.
- Stack: actual-card versus projection entry, placement/order,
  quick-exit metadata/categories/multiface/missing data, destination routing and
  manual overrides. No ability picker/mandatory ability text is approved.
- Pings: ordering capture, Unping/re-add numbering/gaps, selected-set ordering,
  group split/join/coalescing, historical numbers, and projection targeting.
- **Q-04/Q-31:** remaining per-action reaction-point event mappings, exposure/comment
  node placement/coalescing, non-Undo branch controls, forward/
  pre-fork presentation, durable capture/publication, archive presentation and
  retention. Presentation split/join never changes parent links or Undo stepping.
  Parent-node Undo must not erase intermediate history or hide reaction points.
- **Q-17:** random source/algorithm/bounds/offline/concurrent/idempotent/audit details.
  New roll/flip/Shuffle samples freshly even after Undo; replay/retry/redo uses
  recorded results/orders. Equal outcomes by chance are allowed.
- Older pending work remains: token placement/initialization/reference handling,
  View/Reveal lifecycle/history, metadata mappings/default refresh/reset edges,
  applicability versus structural errors, numeric grammar/no-op records, grouping/
  playback, communication edit/delete, and remaining setup/product behavior.
- Use the full [open-question register](decisions.md) and capability worklist;
  this is not an exhaustive replacement. Do not silently drop required scope.

## Baseline constraints easy to accidentally regress

- Two cooperative participants initially; either can edit all state and inspect
  all information. Covered presentation is not opponent access control.
- Downloaded games readable offline, but making game actions requires a connection
  in initial delivery (D-182); no offline gameplay queue/replay. Online actions
  auto-sync without Send, group completion, or required chat/narration. Preserve
  one active branch,
  retained alternatives, reversible branch controls and recovery. D-179 permits
  only deliberate historical branching; ordinary concurrent edits validate
  action-specific from/to conditions at the current head and append or reject.
- No inferred casting/targets/payment/damage/resolution/legality. Explicit MTG
  steps, combat assignments, projections, and contextual Stack conveniences are
  nevertheless required. No priority tracking/UI/Pass (D-105).
- No turn-number editor or extra-turn scheduler; manual repeated steps may make
  counting approximate. End turn is one tap beside Next step. Separate Untap/
   Draw and +advance variants remain; no inferred skip-untap exceptions or loss
   from an empty Library. D-176 fixes individual-mutation +advance sequences; exact
   no-change feedback, partial-group/concurrency, and out-of-step mappings remain open.
- Retained cards entering configured Library/Graveyard/Exile lose counters and
  restore retained current-face default corner text; tokens disappear instead.
  Keep history/undo. Other unrelated move fields remain unchanged unless approved.
- Ordinary visual piles hold at most three actual cards, but do not apply that
  limit automatically to combat groups, Stack, reference trays, or special piles.
- Generic links, pings, combat assignments, and source-card state are distinct.
  No Attach/follow-group feature. Clone creates fresh independent tokens, not
  projections; a token catalog selection creates one token immediately.
- Undo moves the shared pointer one parent back, regardless of author. Retain all
  descendants. After replacement play, hide ordinary abandoned continuations behind
  Show hidden branches; exposure/comment continuations stay visible in full, with
  no cutoff (D-159/D-160). Reviewing does not activate; explicit tree activation
  allows forward return. Submitted game-wide Chat is not undoable (D-94); event
  comments remain ordinary current-path nodes (D-95).
- Trim leading/trailing whitespace on completed user-entered text; preserve
  internal spacing/newlines. Counter/notes/corner semantics remain field-specific.
- Card-provider research is feasibility only; no provider/terms/caching policy is
  selected. Earlier all-technical-artifacts-first gates are superseded by D-115.

## Validation and shutdown note

- For documentation checkpoints: run `git diff --check`, check local Markdown
  target files, and ensure decision/scenario table IDs are unique and contiguous.
  Search the tiered directories recursively, including local heading fragments;
  there are no longer flat numbered files directly under `docs/`.
  Check for obsolete proposals contradicting the latest confirmed answer.
- There are no application tests/builds to run yet. Documentation checks do not
  mean stage 1, adversarial review, UI decisions, or implementation are complete.
- The owner requested saving/committing/pushing and then shutting down this
  computer including its Windows host **for this handoff session only**. The
  environment is WSL2, Ubuntu-26.04; Windows PowerShell/shutdown executables are
  accessible. Do not automatically repeat shutdown in a future session. This
  document does not itself certify that shutdown succeeded.
