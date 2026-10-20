# Action acceptance and limited optimistic locking

Status: **D-187 confirmed**, replacing broad previous-value locking and the former
25-rule proposal. This remains behavior specification, not implementation or
approval of the superseded batch's individual feature/edge-case policies.

## Current acceptance policy — D-187

**Separate selective-Undo validation (D-224):** removing/reinserting a historical
mutation requires every later mutation's relevant-state `(from, to)` tuple to
remain identical across the original and candidate timelines, within a hard
suffix-work limit N. This is intentionally stricter than ordinary online action
acceptance below: two current-value increments may both apply live, while removing
the first is forbidden if it changes the second's tuple. It does **not** restore
general old-field locks to live play or approve the old 25-rule batch. Exact
per-mutation relevant state and N remain open; see
[selective Undo/Redo](history-and-undo.md#selective-mutation-undoredo--d-224).

- **Normally apply the action to current state.** Reject an ordinary game action
  only when performing that requested operation is genuinely impossible. Access,
  valid input/references, current applicability and structural invariants still
  apply; do not bypass the three-card pile cap, delete non-token cards, invent
  missing objects or perform arithmetic on unusable text.
- **No general previous-value comparison.** Taps, notes, face/cover/text edits,
  counter Set and other ordinary edits do not fail merely because the old value
  changed. Preserve the operation's existing meaning: absolute edits write the
  requested value, relative edits use current applicable data. A Set prepared at
  5 may write 10 after another request changed the counter to 6. Record 6 → 10,
  not the stale predicted 5 → 10. Preserve unrelated fields and actual Undo data.
- **Exceptional guard: card movement.** Retain the chosen card's expected source
  and destination conditions. An empty-space move needs that card still at its
  stated source and the target still empty; do not move another occupant, choose
  a different card or silently relocate the destination. Approved pile joins
  retain their own structural/capacity rules; exact remaining movement guards
  are not approved merely by preserving this exception.
- **Exceptional guard: Stack additions/projection creation.** Send the client's
  current Stack state with a move into Stack or a new Stack projection. The
  server compares it with current authoritative Stack state; if different, reject.
  If equal and the action is structurally possible, apply it. Compare-and-apply
  is atomic and follows arrival order. This prevents accidentally covering a
  concurrent opponent entry without a separate opponent/seen/read-receipt model;
  the predicate is state equality, not who changed it. It is not priority/legality
  enforcement or a whole-game-head check. Each later queued addition uses its
  locally predicted pre-action Stack state, including earlier queued additions,
  without acknowledgement waits. D-247 defines the semantic comparison below;
  encoding and provisional-ID mapping remain technical design.
- **Keep the existing online pipeline.** Small local queue, ordered sends with
  multiple actions in flight, arrival-order application and individual outcomes
  remain D-183/D-184. Most ordinary actions succeed. Rejections still use D-185
  difference-only animated reconciliation; acknowledgement alone does not animate.
- **Keep deliberate history semantics.** D-179 explicit historical continuation
  and D-236 server-ordered latest-own Undo requests remain distinct. The general
  acceptance policy does not authorize implicit forks or the old batch's proposed
  non-Undo branch-control policies. D-161 captured-parent Undo is retired.

### Semantic Stack comparison — D-247

“Current Stack state” is the ordered sequence of current Stack entries: stable
real-card IDs and distinct effect-occurrence IDs with their source identities.
Several effects from one source remain several entries; equal card definitions
or the same top card do not make different sequences equal. Entry, removal and
order changes therefore fail a stale addition's comparison.

Do not compare rendered/live source fields (text, counters, orientation, face or
cover), source location, live-versus-frozen inspection status, persistent links,
layout coordinates or personal camera/sort settings. None changes which cards/
effects are on Stack. A source move/reset or token disappearance that leaves its
effect entries present does not alone reject an addition. The new addition still
needs its own valid source, movement guards and structural applicability.

Canonical encoding, equivalent provisional/authoritative IDs and atomic comparison
are technical design constrained by this logical projection, not extra field locks.

D-187 explicitly supersedes D-181's previous orientation/note comparisons and
D-186's previous-value Set check. Relevant-field preservation and current-value
counter deltas remain; this does not turn the old batch's absent-counter,
cleanup, Clone, shuffle, projection, tracking or lifecycle rules into approved
requirements. Exact no-change/edge-case semantics and payloads still follow the
existing operation contracts or require specification, not speculative locks.

## Superseded review proposal — not current requirements

The owner replaced the overall policy instead of approving/correcting the
25-item batch. Keep it below as historical proposal context only. Its old status
labels refer to that earlier discussion; they do not override D-187 or require
another numbered review. Do not restore its general comparisons or treat its
unapproved alternatives as acceptance criteria.

<details>
<summary>Former 25-rule batch (superseded)</summary>

[History and Undo](history-and-undo.md#33-immediate-action-synchronization) owns
the confirmed publication/reconciliation model. This document collects the
action-specific locking review in one place. Proposed rules cover the generic
operations in [the action catalog](game-actions.md#42-coverage-matrix); applying
a rule to a capability whose release scope is still proposed does not approve
that capability.

## Review rules

1. **Common checks — confirmed.** At the current head, check access, referenced
   identities, existence, applicability and structural invariants, plus only the
   action's relevant from/to conditions. Never lock the whole board/card just
   because its revision changed. Check and apply atomically in arrival order;
   send in client order with multiple actions in flight. Failure rejects that
   individual action without an implicit branch. Preserve unrelated current fields.

2. **Absolute field edits — partly confirmed, extension proposed.** Orientation
   and note edits compare the previous orientation/text (D-181). Extend this to
   face, cover, corner text, labels, participant associations and other supported
   scalar fields: compare only the old field being replaced, then write the chosen
   new value. Send an explicit desired state, not a toggle of whatever is current.
   Movement alone does not invalidate these edits. Validate that a chosen face/
   association still exists; do not lock unrelated fields.

3. **Existing counters — confirmed D-186.** +/- applies its delta to the current
   numeric value rather than comparing the previously seen number. Two +1s on 5
   produce 7. Set compares the previous stored value before replacing it;
   concurrent changes make stale Set fail. Existing numeric-pattern/applicability
   checks remain: a delta cannot do arithmetic on nonnumeric counter text. Record
   the actual accepted before/after values for deterministic history and Undo.

4. **Absent/removed counters — proposed.** Address a counter by target and color.
   For a delta, current absence starts from zero under D-68; if another request
   has since created a numeric counter, adjust that current number. Thus two +1s
   starting from absence produce 2. This also means a late delta can recreate a
   counter removed meanwhile. Nonnumeric current text rejects arithmetic. Set
   prepared against absence requires continued absence; empty Set removing an
   existing counter requires its previous text. No stale overwrite or recoloring.

5. **Other relative numeric edits — proposed.** Extend current-value delta
   semantics to supported named numeric trackers and corner-text +/- shortcuts.
   Require the current text to retain the applicable pattern: integer or integer/
   integer pair. For a pair, adjust only the selected side(s) of the current pair,
   preserving the other side. Reject if the pattern became incompatible; never
   evaluate symbolic expressions. Absolute Set still follows item 2.

6. **Moving a specific object — partly confirmed, detail proposed.** Check the
   exact object and stated source. For free placement that includes its source
   grid/pile placement; for extraction of a specifically chosen card from an
   ordered container, source membership suffices, not an unchanged numeric index.
   Check destination existence, compatible structure and capacity. Empty-space
   moves require that target to remain empty (D-179). Ignore orientation/text/
   counters unless that individual mutation explicitly changes them. Never
   substitute another card, source or destination.

7. **Pile insertion and occupied drops — proposed.** Explicit top/bottom means
   the current top/bottom of the named destination; unrelated additions do not
   invalidate it. Joining an ordinary visual pile requires that same target pile/
   anchor and available capacity, preserving the three-card cap. An explicit
   between-card drop names the neighboring IDs and requires that gap still exist;
   reject rather than guess a nearby insertion point.

8. **Reordering — proposed.** Check the moved identities' expected source
   placement/order context and destination gap/edge. Preserve untouched members'
   relative order. Do not require an unchanged whole container for a local reorder
   if its source/destination context still holds. Same-container reordering is not
   destination entry and does not trigger entry resets.

9. **Top/bottom/N-card actions — proposed.** Fix the selected IDs and source order
   from the invoking client's view. Each emitted card action checks its expected
   remaining source slice/edge and chosen identity, accounting for preceding
   local predictions and the approved top-to-bottom order for bottom-N moves;
   do not silently draw a replacement card. A selected View/
   Reveal slice checks those ordered IDs, not the unseen rest of the container.
   A stale subset fails rather than reclamping/reselecting against changed state.
   Normal count clamping at invocation remains D-49, not server retargeting.

10. **Shuffle/full-order replacement — proposed.** Require the same ordered
    member IDs as the request's input, since the whole order is being replaced.
    Counter/text/orientation changes do not invalidate a shuffle. Store one
    concrete resulting permutation and reuse it for retries/replay; never include
    newly arrived cards, omit removed cards or resample to repair a stale request.

11. **Reset and destination-entry cleanup — proposed.** A standalone corner
    Reset checks previous corner text plus the selected face/retained default it
    uses. Destination-entry conveniences remain ordered individual mutations:
    movement uses item 6, each counter removal checks that counter's old text,
    and corner reset checks the text/default it replaces. Do not clear newly
    added counters absent from the requested cleanup. A move may succeed while a
    later cleanup mutation fails. Mandatory structural cleanup must still preserve
    established source/projection/link invariants; exact mutation decomposition is
    separate from this lock proposal, not permission for invalid intermediate state.

12. **Creation and Clone — proposed.** Creation requires a fresh ID, valid chosen
    definition and compatible destination. Automatic placement chooses an available
    spot in current state instead of failing solely because a predicted free spot
    filled; an explicitly chosen destination is not relocated. Clone additionally
    checks that its source exists and the fields being copied still match the
    snapshot selected for copying. Source movement alone is irrelevant; changed
    copied fields reject rather than silently clone different data. Copies retain
    their approved independent token identity and exclude links/pings.

13. **Token/occurrence removal — proposed.** Token Remove requires the same token
    at its expected placement; occurrence Remove requires the same occurrence at
    its expected area/slot/role. Movement/retargeting conflicts; unrelated text/
    counter edits do not. Preserve actual current removed data for Undo, not stale
    fields from the request. Non-token cards cannot be removed. Removing one Stack
    occurrence does not affect siblings or its source. A separately submitted
    removal of an already-absent target fails; a retry returns its recorded result.

14. **Persistent links — proposed.** Link means ensure that exact endpoint pair is
    linked, never toggle: both endpoints must exist and be eligible, and already-
    linked is a no-op (D-72). Unlink removes the specifically selected relationship
    ID; already absent is a no-op, but it must not remove a replacement relationship
    created since the original was removed. Movement of endpoints does not conflict.
    Preserve endpoint-lifetime cleanup and unique undirected pairs.

15. **Pings — proposed.** Convert the local toggle to explicit Ping/Unping before
    sending. Fix the author/context/effective boundary and destination identity;
    do not retarget a stale ping to the newest event. Ensure-present is a no-op if
    that same scoped ping already exists; removal addresses the selected ping ID,
    not a later replacement. Target movement does not invalidate a reference.
    Validate endpoints against the recorded context; adding to retained history
    does not activate a gameplay branch. Number additions in accepted order within
    their context, without locking unrelated card fields. This does not settle
    the separate historical numbering/group-edit behavior.

16. **Combat/Stack projection edits — proposed.** Card edits through projections
    use the real card's field/movement locks; occurrence edits use the occurrence's
    own expected area, role, slot and insertion context. Creating a combat
    occurrence checks source battlefield eligibility and uniqueness; if another
    request created an occurrence for that source meanwhile, reuse only if the
    desired placement is already achieved, otherwise reject instead of stealing
    its new placement. A deliberately reused occurrence is moved under its old
    placement checks. Every new Stack effect has its own fresh identity, so two
    creations from one source can both succeed. Later source departure does not
    erase surviving Stack effects (D-125). A data-dependent contextual exit checks
    the face/category used to choose the explicit operation; no substitute exit.

17. **Combat-area lifecycle — proposed.** Ensure-open reuses an existing open
    area, without clearing it. Closing targets that area ID and checks its expected
    projection membership/roles/slots/order, so a concurrent declaration or
    rearrangement prevents stale closure. Unrelated source-card notes/counters do
    not prevent closing. Never close a replacement area opened after the original
    closed. This does not define absent-area drag UX or change cleanup effects.

18. **Turn/phase/step — proposed.** Next step, End turn and direct phase/step
    changes compare the previous tracking tuple: turn player, turn number, phase
    and step. Two requests to advance from the same step cause one transition,
    not two. Unrelated card edits do not invalidate tracking. Combined shortcuts
    retain separate card/occurrence/tracking mutations and individual outcomes;
    freeze their selected targets rather than silently expanding Untap all to
    cards that joined later. Do not add priority or game-rule legality checks.

19. **View/Reveal, inspection and dismissal — proposed.** Opening fixes exact
    referenced IDs; top/bottom selection uses item 9. Specific-card inspection
    requires an inspectable referenced card, not unchanged position or notes.
    Do not change displayed IDs to follow source contents. Dismiss targets the
    exact display ID and is a no-op if it is already dismissed; source changes do
    not prevent dismissal or cause a replacement display to close. Actual exposure
    is never undone by rejection; preserve truthful exposure history. Exact exposure
    capture/coalescing and offline inspection policy remain separate open work.

20. **Die/coin and other supported independent random results — proposed.** Check
    valid explicit inputs and operation identity, not unrelated board state. Each
    new invocation gets one fresh result, while retries reuse its recorded result
    (D-92). Shuffle and random selection also require their applicable container/
    selected-object locks; independence of a die roll does not waive those checks.
    No protocol for selecting a random source/provider is approved here.

21. **Comments/chat/shared annotations — proposed.** New records use fresh IDs and
    an explicit valid context, without locking unrelated board state. Event
    comments remain ordinary current-path nodes; chat remains separate and is not
    undone by gameplay Undo. Where editing/deletion is supported, check that
    record's previous content/revision, not the game head; target its exact ID.
    Group association edits check the membership/boundaries they change, not
    unrelated cards. This does not approve unfinished message/group-edit features
    or their historical placement policies.

22. **Bulk/combined actions — confirmed foundation, target freezing proposed.**
    Freeze chosen identities/parameters at invocation; each emitted individual
    mutation uses its own rule, based on its predicted predecessor state. Do not
    rebuild the selection at server arrival or make the sequence atomic. Keep
    established applicability skipping and whole-drop structural preflight, but
    after sending do not cancel valid siblings because one action fails. No
    sequencing dependency graph or acknowledgement wait (D-176/D-183/D-184).

23. **History controls — mixed.** Undo's exact node-to-parent transition and silent
    stale discard are confirmed (D-161). Explicit interruption intent and chosen
    historical boundary are confirmed (D-179); proposed simultaneous handling is
    normal arrival order, with each accepted explicit interruption making its
    continuation current while retaining every displaced line. For non-Undo
    forward activation/return, propose checking the captured active pointer and
    exact target node; reject rather than override newly arrived play. Archive/
    restore checks target branch identity, previous archive status and branch tip,
    so it does not silently hide a newly extended line. Stale non-Undo controls
    reconcile as rejected controls, not D-161's special silent-Undo policy.

24. **Setup, configuration and lifecycle — proposed where supported.** Initial
    game/object creation uses fresh IDs; seat/invite claims check the relevant
    available seat/invite state. Setup/deck/configuration fields and completion/
    reopening/result fields check only previous values and applicable lifecycle
    conditions. For container deletion or structural reconfiguration, also compare
    affected membership/references/layout, not just its label; never lose cards or
    silently relocate them. Rematch creates a new game referencing retained
    history, not a mutation of the completed game's board. This approves neither
    a general layout/deck editor nor unsettled lifecycle/identity features.

25. **Personal state, no-change and retries — mixed.** Selection, pan/zoom,
    sorting/filtering, personal review and export reads need no shared optimistic
    lock and do not activate branches. Shared exposure is item 19, not exempt
    merely because it began with inspection. Proposed no-change handling: after
    required checks pass, record no fabricated mutation for no actual change;
    do not turn a failed previous-value check into success just because the desired
    value is already present. Explicit ensure-present/absent exceptions above are
    intentional. A retry with the same identity returns its recorded outcome,
    never duplicates work or rerolls randomness. Rejections use D-185 difference-
    only reconciliation, not shared Undo or a new historical branch.

## Coverage and review boundary

All coverage rows A-001–A-036 map to these rules: creation/setup/import/lifecycle
(12, 24), fields/values/associations (2–5, 11), movement/layout/containers (6–10,
24), links/pings/communication (14–15, 21), token/copy/removal (11–13), projections/
combat/Stack (13, 16–18), exposure/randomness (9–10, 19–20), bulk/correction (1–18,
22), history (23), and personal reads/export (25). General checks in 1 apply to
every shared mutation. This is coverage of locking behavior, not a claim that
all feature details are complete.

The review request for this list was superseded by D-187. Exact payloads, ordered
transport, queue capacity, mutation cleanup decomposition, animation scheduling
and connection-loss recovery remain separate specification work.

</details>
