# Prediction, live mode and grouped playback

## Selected behavior — D-277

The owner confirms immediate multi-action prediction, rebasing remote updates
before pending actions, cascading local invalidation, live versus catch-up playback,
and previous-board → next-board animation. The
[history contract](../03-contracts/history-and-undo.md#prediction-rebasing-and-cascading-invalidation--d-277)
owns reconciliation; its [mode rules](../03-contracts/history-and-undo.md#live-and-playback-presentation--d-277)
and [animation rules](../03-contracts/history-and-undo.md#endpoint-based-board-animation--d-277)
own presentation. This is not a second authoritative local game or implementation
authorization. D-185's difference-only correction remains the governing invariant.

## Three distinct layers

1. **Accepted replica:** durable server-confirmed tree/current state/control and
   revision, with D-276 complete current-game history after full download.
2. **Request journal:** original request identities/intent/guards/random inputs,
   delivery/admission/final-result bookkeeping and protected exposure recovery.
   Active versus withdrawn prediction is separate from final request outcome.
3. **Displayed target:** live accepted state plus still-applicable active pending
   predictions, or the chosen revision-qualified historical boundary in playback.
   The scene/animation layer consumes this target, not network mutation waypoints.

Use the same Kotlin processor for prediction and authoritative semantics. Keep
accepted-base storage separate from pending intent; derive/rebuild prediction from
the current materialized base rather than replaying the entire downloaded tree on
every update. Persist original requests/exposures before showing their effects;
ordinary transmission and local interaction never wait for earlier acknowledgements.

## Accepted updates and pending overlay

- Process server revisions coherently in their actual order. Match own committed
  effects/results to original request IDs; an expected acknowledgement of move 1
  after local moves 1/2/3 advances the base, not the displayed board back to move 1.
- Incorporate accepted remote work in the base first, then evaluate remaining
  active original predictions in local order. Apply each eligible prediction to
  the working overlay. Omit a failing prediction and check the next against the resulting
  state; cascades follow actual guards, not a dependency-graph or blanket suffix
  cancellation. Preserve independent still-valid predictions and immutable intent.
- An independent remote note edit under three pending moves leaves the final
  predicted position intact. A remote move B→X after accepted A→B invalidates
  pending B→C, then C→D; target the final accepted/rebased X immediately. If the
  remote move instead supplies a later request's original source, that later move
  may remain applicable despite an earlier one failing.
- A removed local prediction is **not a recalled/rejected server command**. Retain
  every already-sent/admitted original request and recover its actual outcome under
  D-275. Do not substitute references, rewrite guards, issue Undo, regenerate random
  input or discard actual exposure. Final server results can differ from this
  earlier local forecast and are reconciled normally.
- Track withdrawn predictions separately from the immutable request journal.
  Once withdrawn they do not re-enter the speculative overlay merely because a
  later accepted state would make their original guards applicable. Preserve that
  disposition through restart/recovery; only actual accepted effects can bring
  those moves back, not replay of the cancelled prediction sequence.
- If the server subsequently accepts a withdrawn move, treat the committed delta
  like any incoming opponent/other-device change: apply it once in revision order,
  rebase still-active predictions and animate live or include its actual recorded
  group in playback. Original author/request identity stays correct; there is no
  fabricated remote actor, fresh request, special suppression or suffix resurrection.
  A rejected withdrawn move has no extra predicted effect left to roll back.
- Receipt does not remove a prediction. If an accepted final result arrives before its
  committed changes, avoid dropping the visual effect before accepted state can
  represent it. If the delta arrives first, identify its own command to avoid a
  duplicate overlay while resolving remaining result bookkeeping. Publish a
  coherent base/pending/display transition, not transient intermediate rewinds.
  A known final rejection/no-op retires its outstanding visual effect as applicable;
  it does not wait for an invented gameplay delta that will never exist.
  Exact result/delta correlation/SQL transaction representation remains design.

Recomputation is semantic and prompt; animation does not gate it. Persist accepted
data/cursor atomically and retain independent request/exposure recovery. New state
must not silently alter the account/play-incarnation/preparation context, create
offline work or authorize generic replay/cancellation of never-transmitted buffers.

## Live and playback coordination

- **Live:** apply coherent accepted remote changes on arrival and immediately
  rebuild pending prediction; groups still organize history, not a live delay gate.
  This also applies to remote edits from another same-account device.
- **Playback:** accepted state keeps updating immediately while the displayed
  historical boundary progresses by groups, automatically or stepwise. Incoming
  data can extend the known continuation without jumping a paused cursor or
  waiting for group closure. D-238 handles deletion of the viewed node.
- Foregrounding with unpresented updates enters playback. Persist presentation
  boundary/mode separately from accepted sync revision, because background delivery
  may already have advanced the latter. Expected own confirmations/receipt-only
  traffic are not newly unseen board play. If the saved display includes local
  predictions, resolve their identities against accepted history/recovery before
  choosing a valid catch-up anchor; never fabricate published nodes for predictions.
- For a retained displaced path, use D-177's rollback to common ancestry then
  forward groups. A same-path continuation starts from the retained local boundary
  without an artificial rollback. Boundary identity includes relevant tree/control
  revision; equal node ID alone does not prove equal state after a tree edit.
- On reaching the currently known continuation, hand off to live without losing
  racing arrivals. Deliberate gameplay continuation at a chosen boundary also
  returns to live prediction with explicit D-179 intent; mere pause/scrub does not.
  Failure cannot silently rewrite that historical intent into ordinary play.
- Modes cannot defer server lifecycle changes or reopen ended play as an R1
  archive. D-212/D-213 end/return navigation and old-incarnation fences still apply.
  Mode/progress is local, not cross-device review receipts or server-owned state.

## Endpoint animation, not per-mutation choreography

First compute the next selected target, then animate from the previous displayed
scene. Three same-card moves received in one live presentation update animate
once from original to final position. The same holds inside one playback group;
different playback groups remain separate transitions even in one network batch.
Do not delay synchronization to accumulate events or erase their intermediate
mutation boundaries. Net-zero position changes need no invented motion.

Retarget an unfinished animation from current rendered geometry to the new target,
not via an obsolete pose. Drawing/hit testing/accessibility share that geometry;
interpolation never becomes a stored board state or affects conflict/randomness.
Pause/scrub selects real nodes, not interpolated poses. Exposure/random/reference
records are not inferred away merely because endpoints coincide. Timing, easing,
reduced motion, exact mode controls/autoplay defaults and first-open presentation
without a saved boundary remain UI/technical completion.
