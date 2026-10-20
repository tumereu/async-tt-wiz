# Scope proposals and genuine gaps

This accompanies the [confirmed behavior overview](behavior.md). Pruned under
D-237: an unwritten detail or an old "open" label is not automatically a product
gap. Apply current agreed rules first. Proposals remain unapproved; this pruning
does not add/remove release behavior or authorize a delivery-stage transition.

For efficient review, the identified edge cases below are batched with their
spec-derived resolutions and sources. Review/revise them together rather than
ask a new approval question for each consequence. Cases without a spec-derived
answer are listed separately. This is a triage of the identified backlog, not a
claim that every possible edge case has been verified.

## Spec-derived edge-case batch

These are consequences of existing requirements, **not new policy proposals**.
References point to the primary contracts; D-IDs identify the approved basis.

| Case | Resolution from the current spec | Basis |
| --- | --- | --- |
| A move is followed by a comment referring to that move. | Undo of the move is blocked: the comment's relevant context would change. Do not delete or reattach it to permit Undo. | [Replay dependencies](../03-contracts/history-and-undo.md#selective-mutation-undoredo--d-224), D-224; T-534. |
| A move is followed by an independent tap of the same card. | Keep the tap and allow removing the move if all other checks pass; unrelated position is not tap state. | D-224. |
| An increment is followed by another increment of the same counter. | Removing the earlier increment changes the later before/after tuple, so Undo is blocked even though the delta is unchanged. | D-224. |
| Removing a Library move would change which card was on top for a later draw. | Block Undo; neither draw another card nor silently take the recorded card from beneath the top. | D-225/D-226. |
| Independent cards arrive in Graveyard or Exile. | Unrelated contents/indices do not create order dependencies. These containers are unordered and have no order-related controls. Other replay checks still apply. | D-225/D-226. |
| Your latest own action is blocked/revealing, or no own action remains. | Undo is unavailable; do not skip it, pick an earlier action or undo the opponent's. | D-224/D-230/D-231. |
| Your latest own action has later opponent actions after it. | It remains the Undo candidate; preserve the later actions under the unchanged-tuple test and N. | D-231. |
| Two distinct Undo requests arrive from your devices. | Process like two presses against successive current own candidates; both can succeed. A blocked/absent next candidate permits no further removal. | D-236. |
| One Undo request is retransmitted after a lost response. | Recover its original result once; a retry is not another press. | D-216/D-236. |
| You Undo on one device and continue on another. | Use the same per-player, per-game stack and current checks, not a device-local stack or one stack for both players. | D-235. |
| Ordinary own new gameplay follows Undo; separately, ordinary opponent gameplay follows Undo. | Own successful new play clears own Redo even if reinsertion would be safe. Ordinary opponent play retains it but may block Redo. Deliberate shared-history switches instead clear both queues under D-258. | D-227/D-228/D-235/D-258. |
| A rejected/no-change request follows Undo. | No successful new gameplay mutation occurred, so it does not trigger own-new-play stack clearing. Prediction/acknowledgement bookkeeping must converge to that outcome. | D-176/D-227. |
| The next Redo entry is blocked but an older one is safe. | Disable Redo and retain the stack; never skip/discard the next entry. Successful Redo order is last-undone-first. | D-229/D-230. |
| An eligible action is removed from a linear path. | Edit the existing tree; do not preserve an original explorer alternative, duplicate the suffix or append compensation. The removed action is held for Redo. | D-232. |
| The history node you are viewing is deleted. | Fall back to its parent, remaining in personal review. Repeat along the parent chain if necessary to reach a surviving ancestor/root; do not retain a detached snapshot, jump to live play or create a branch. Cursors on surviving nodes stay there. | [Review cursor fallback](../03-contracts/history-and-undo.md#selective-mutation-undoredo--d-224), D-238; T-535–T-537. |
| Undo would remove a fork or reach back before one, including a hidden alternative. | Block Undo. Explorer filtering is not branch removal. Actions strictly after the latest fork remain subject to ordinary eligibility. | D-233. |
| An explicit interruption creates/activates a new fork after Undo. | The deliberate shared-history switch clears both Redo queues atomically. The old stranded-but-retained example is superseded; no old entry is reinserted before the fork, at the head or through a recovery branch. | D-258 revises D-228/D-234; T-527/T-528. |
| A real source leaves Battlefield, including a disappearing token. | Remove its combat occurrences/incident links; retain independent Stack effects. Existing sources stay live-linked across locations. Only token disappearance freezes the last live source state as read-only information, not an editable replacement card. | [Projection lifetime](../03-contracts/combat-stack-and-projections.md#135-confirmed-area-specific-lifetime-d-124d-125), D-120/D-124/D-125/D-242. |
| A Stack effect is created from Graveyard/Exile, or its source moves there. | Creation leaves the source in place; source/effect card fields remain linked while the source exists, with normal Stack-addition guards and destination resets. Never freeze merely on Battlefield departure or at effect creation. | [Stack source contract](../03-contracts/combat-stack-and-projections.md#live-stack-sources-and-token-disappearance-snapshot--d-242), D-242; T-545–T-548. |
| A projection is edited, repositioned or removed. | Card edits affect the real source; occurrence layout/removal does not delete or independently edit that source. Non-token cards have no Remove action. | D-123/D-174. |
| A card referenced by View/Reveal moves elsewhere. | Keep the original reference and update its location; do not refill the display or copy the card. | D-46/D-47. |
| A bulk/combined shortcut changes several things, or draws from an empty Library. | Record individual actual mutations in sensible order, not one atomic invocation node. Empty draw adds no invented move; a combined tracking advance still happens. | D-176; [turn shortcuts](behavior.md#8-turns-and-shortcuts), D-111. |
| Some selected targets are individually inapplicable; separately, a placement is structurally invalid. | Skip individually unusable targets under D-67, but do not drop invalid IDs or partially execute an invalid placement. An overflowing ordinary pile drop is rejected in full. | D-39/D-66/D-67. |
| An ordinary move enters Library/Graveyard/Exile. | Apply the approved counter/corner resets and token disappearance; otherwise preserve unrelated card state. Same-container visual rearrangement is not a new container entry. | D-55/D-56/D-84; [move behavior](behavior.md#3-cards-placement-and-ordinary-piles). |
| A lobby change races game start, or a start response is retried. | Server arrival order determines change-before-start versus rejection-after-start. Start/shuffle once, reuse recorded outcomes, and never add an automatic opening draw. | D-192/D-200/D-205. |
| A player tries to use Sideboard during play. | There is no in-game Sideboard access, not merely a covered area. Saved/lobby preparation remains intact. | D-202/D-213. |
| Saved-deck editing/deletion occurs after a copy was selected. | Preserve the selected lobby/game copy and its preparation; no replacement source deck is required. | D-191/D-199/D-221. |
| A new random action is requested; separately, recorded results are reviewed/retried. | New invocations sample freshly; review/Redo/retry reuses stored results. This does not permit selective Undo of a revealing target. | D-87/D-92/D-93/D-224. |
| A result is suggested but not accepted; separately, the opponent accepts it. | A pending suggestion is undoable board state; acceptance is not undoable and returns both to the prepared lobby with Ready cleared. Submitted game-wide chat is also not gameplay-undoable. | D-94/D-211/D-212/D-213. |
| Concurrent result suggestions or an acceptance racing removal/end of play cannot both apply. | Process in normal server arrival order: the first applicable action takes effect; discard the later incompatible request. Do not replace the first suggestion, keep multiple pending suggestions, infer acceptance or retarget a failed request. Compatible actions still use their normal acceptance rules. | [Ending play](../03-contracts/accounts-decks-and-lobby.md#result-suggestion-acceptance-and-return-to-lobby--d-211), D-184/D-187/D-239; T-538/T-539. |
| A submitted chat message needs correction. | Send another message; submitted chat has no Undo, Edit or Delete. Simple chat has no planned expansions, not a deferred editing/deletion roadmap. This does not change event-comment Undo or decide account erasure/retention. | [Optional text](../03-contracts/visual-action-language.md#116-optional-text), D-94/D-240; T-540. |
| An event comment needs correction. | No dedicated Edit/Delete. Use ordinary eligible Undo or add another comment; dependency and other Undo checks still apply. | [Optional text](../03-contracts/visual-action-language.md#116-optional-text), D-241; T-541. |
| A saved deck is deleted or an unstarted lobby cancelled. | No trash/restore feature. Preserve already selected lobby/game deck copies; do not confuse these exclusions with current-game branch restoration or permanent server erasure. | [Account/deck/lobby contract](../03-contracts/accounts-decks-and-lobby.md), D-241; T-542. |
| A player explicitly logs out or switches accounts. | Logout removes private downloaded content but preserves protected already-sent recovery records. Never expose one account's private content/recovery records to another. Server retention/account erasure remains privacy/service design. | [Logout](../03-contracts/accounts-decks-and-lobby.md#logout-and-account-private-downloads--d-241), D-241; T-543. |
| Personal review or explicit internal-node activation leaves a retained future. | Personal viewing never changes the Undo target/checks. A fork anywhere at/after the target blocks removal; validate the whole retained linear successor chain, including beyond the shared pointer, under N and unchanged tuples. | D-245; T-558. |
| Redo has a surviving anchor but different relevant input and no later suffix. | Block reinsertion unless its own recorded relevant transition can be restored exactly; no fresh arithmetic/default/source snapshot. Retain the stack without skipping. | Recorded replay/D-224/D-230; T-562/T-563. |
| Cached hidden faces are available offline. | No new event-producing inspection. D-252 own-area hold reads real downloaded faces without an event; opponent holds conceal hidden sides and explicit logged inspection needs a connection. Already-initiated online recovery remains separate. | D-246/D-252; T-559–T-561. |
| Stack source fields/location/frozen status change without changing current entries. | Do not reject an addition on that basis; compare ordered real-card/effect-entry identities, not rendered source state, links or coordinates. | D-247; T-568/T-569. |
| Actions is opened on unselected C while A/B are selected. | Bind C only, preserving selection. Selected trigger binds the selection; capture target IDs at menu/editor opening and never redirect to a later selection. | D-248; T-570/T-571. |
| A password contains boundary whitespace or Unicode. | Card-text trimming is not credential normalization. Preserve password input consistently for registration/login/reset; account-field validation remains separate. | D-249; T-572/T-573. |
| Taps occur between otherwise matching grouping anchors. | D-251 replaces D-250: first tap starts a new group unless the current group is taps/pings-only. Its next anchor joins; never merge backward across that tap boundary. | D-251; T-304/T-574, grouping matrix. |
| A Stack/move operation explicitly continues at history while live state differs. | Select the historical execution state before all guards/reads; do not compare the displaced head's Stack or source. | D-179; T-579/T-580. |
| A delayed old lobby request first arrives after ending/return to the same lobby. | Bind the original preparation round; no old Ready/edit/cancel acquires fresh-round intent. Recover committed old outcomes without reapplication. | D-205/D-213/idempotency; T-581/T-582. |
| Logged exposure is captured online but not sent before Logout. | Protect the outstanding account-scoped recovery obligation, clear ordinary downloads and prevent another account reading/publishing it. | D-246/D-241; T-600/T-601. |
| Cleanup exposes separate Close/Next step actions but not advance-and-close. | This is allowed: individual actions need full paths; the combination can be quick-only. | D-253; T-233/T-235/T-597. |
| A Ping menu/request spans another device's intervening event. | The menu captures targets, not a group. The server processes requests and creates events, grouped deterministically. Use its current derived recording context; explicit Unping of an absent marker is a no-op, not an old-group amendment, Ping conversion or group-mismatch rejection. | D-254; T-602. |
| An ending suggestion follows a tap/closure/random run or precedes pings/comments/tracking. | Always a strict singleton S group, with boundaries before and after. Group catch-up shows the final board before the acceptance dialog; receipt does not obscure earlier unreviewed groups or delay sync. | D-257; T-603/T-604, GM-23–GM-25. |
| A deliberate activation/interruption/return succeeds; separately it is retried, rejected or a no-op. | Successful shared-history switches clear both per-incarnation Redo queues as one server operation. Retry recovers the original result without clearing newer entries; no-op/rejection/UI review does not trigger a reset. | D-258; T-605–T-607. |
| A client has a locally displayed board, open menu, cache and pending action. | Server owns semantic game state; accepted replica, prediction/recovery and UI-only state are distinct. Completed shared actions use requests with local latency hiding; menu/camera/selection/review choices are personal. | [Ownership matrix](../03-contracts/domain-model.md#state-ownership-and-request-boundary--d-259), D-259; T-608/T-609. |

## Genuine gaps

**No unresolved product choices remain in this identified/pruned batch.** D-241
settled correction/recovery/privacy/scope outcomes; D-242 settles Stack source
inspection: stay linked while the source exists, including Graveyard/Exile, and
freeze read-only last-live state only on token disappearance. Combat stays linked
and keeps its Battlefield-departure cleanup. Q-28/Q-33/Q-35 now track contract/
test and technical/UI completion, not another approval of that policy.

This is a batch status, not a certification that all behavior has been verified.
Server retention/account-erasure obligations remain explicit privacy/service work,
not silently resolved by the no-trash/Logout choices. Other reference mappings
must be completed against existing rules, escalating only a demonstrated new gap.

The October 19 review was reconciled with D-245–D-250. The October 20 review is
now reconciled with **D-251–D-253**, which replace the earlier tap/hold/full-combined
wording where stated, plus derived command/lifecycle/reference/durability repairs.
See the [latest disposition](../../agent/adversarial-review-2026-10-20.md#documentation-follow-up-after-owner-discussion)
and [grouping E2E matrix](../05-verification/grouping-matrix.md).
Technical completion and additional consistency audits remain; this is not an
exhaustiveness claim or approval of implementation/UI/stage transition.

The subsequent follow-up review's **F-01 is withdrawn under D-254**: its UI-bound
ping-group premise was wrong. UI requests do not define groups; server-created
events are grouped deterministically. Corrected contracts and T-602 record this,
not another owner choice. **F-05 is resolved by D-257's strict singleton ending
groups**, rather than the proposed E-family mapping. F-03/F-04/F-06 have derived
repair work; see the
[review disposition](../../agent/adversarial-review-2026-10-20-follow-up.md#outcome).

**F-02 is resolved by D-258:** successful deliberate shared-history switches clear
both queues, while ordinary events, Undo/Redo and UI-only review retain their
distinct rules. No advance-to-A exception or blanket clear-on-every-event rule is
approved. D-259 makes server ownership the default, with a primary classification
matrix rather than a separate cross-device decision for each semantic state.
Checksum chaining is a technical option, not a new gameplay choice; its proposed
revision/checkpoint design must coexist with editable trees and latency hiding.

This is not a certification that behavior specification is complete. Further
edge cases may be found, but an owner question needs a concrete unresolved
outcome or conflicting requirements, not merely a new example of an existing rule.

## Specification work — not fresh product questions

| Work lane | What remains | How to handle it |
| --- | --- | --- |
| **Contract/test completion (stage 1).** | Operation context/input-binding, ordered lowering, reference lifetime and exposure participation now have shared contracts. Complete further operation-specific mappings/traceability against those rules; execute nothing until implementation is authorized. Grouping has a planned category/prefix matrix for later E2E/playtest tuning. | Write spec-derived outcomes in reviewable batches. Escalate only an actual ambiguity/contradiction. Do not reopen tap runs, side-sensitive holds, quick-only combinations or each dependency/empty-source variant. |
| **Technical design.** | Replay N/counting, insertion-anchor encoding, node IDs/versioning, cached-state rewrites, request identities/ordering/recovery, Stack snapshot equality representation, randomness source, numeric/resource limits, import normalization/provider compatibility and schema/API details. | Specify and validate against approved observable outcomes in the relevant technical work. A missing representation/parameter is not a missing usability model. Online-created unsent/recovery cases still need explicit contract coverage; first check existing connected-only, pending-work and no-duplicate requirements. |
| **Later UI decisions (stage 3).** | Exact screen/menu names, control placement, gesture arbitration/drop geometry, accessible alternatives, feedback, browser rendering, animation timing and notification permission/channel presentation. | Preserve approved gestures, placement rules, immediate sync and read-only access. Do not present every layout/default as an unfinished stage-1 behavior decision. A real missing state transition still belongs in the product lane. |
| **Platform/service/release constraints.** | Supported Android/device/language/distribution targets; hosting/catalog rights, security/privacy, retention/erasure, cache policy, notification delivery and operational/performance objectives. | Keep them visible for their proper decision/design stage. Product promises and scope need owner approval; technical choices remain technical work. Neither is an excuse to invent a new gameplay rule. |

D-272 settles the deployment scope: Docker Compose deployability is required;
production deployment/hosting/backups are owned by a separate repository, with a
Raspberry Pi as a possible first trial. Application container/configuration,
migration/compatibility and persistent-state/recovery interfaces remain technical
work here, as does the existing Compose E2E environment. Do not turn cloud-provider,
host provisioning or backup automation into new owner questions/application tasks.
D-273 settles initial Android support/distribution: API 29 / Android 10+, signed APK
downloads and manual installer updates, with compatible upgrade/mixed-version
behavior. Reference-device budgets, exact build/signing/release mechanics and
protocol compatibility windows remain technical work, not a fresh question about
whether Play Store publication or a custom in-app updater is required.

The [classified Q-register](../../agent/decisions.md#93-release-blocking-questions)
keeps stable tracking IDs while replacing the original mixed question queue.
Older "open" wording elsewhere is not an automatic owner-question queue. Known
drift and the pruning rationale are in the [specification audit](../../agent/specification-audit.md).

## Broader draft scope — D-241 release exclusion

The remaining proposed authoring/coordination extras are excluded from R1 by
D-241, not a queue of individual feature questions. In particular:

- Arbitrary named values/participant associations, custom non-card pieces/markers,
  general authoring/layout or correction tools beyond the approved tabletop tools.
- Full manual deck authoring/export, additional import formats/sections, configurable
  starting arrangements or extra in-game areas beyond approved scope.
- Optional manual group editing, branch comparison/renaming, live-follow or
  cross-device personal review progress, response requests/read receipts, additional
  alert categories, presence and recovery-center features.

Generic primitives and capability inventories do not approve these features.
No expansion roadmap is implied. Preserve all confirmed capabilities and the
previously explicit D-214/D-220 deferrals; do not remove existing automatic
grouping, token/Clone controls, direct placement or current-game branch recovery.
Existing confirmed accounts/import/lobby/gameplay/history/push behavior remains in
the [overview](behavior.md); it is not another scope question here.

**Already deferred, not R1 gaps:** finished-game history archives/review are
optional future development (D-214); in-app global/per-game notification mutes
are post-R1, using Android channel settings in R1 (D-220).

The release direction still excludes automatic rules/legality enforcement,
competitive anti-cheat, matchmaking/rankings/tournaments/clocks, public spectators/
chat, AI opponents, multiplayer and non-Android gameplay clients. Offline gameplay
creation/replay is excluded by D-182; downloaded read-only access remains.
