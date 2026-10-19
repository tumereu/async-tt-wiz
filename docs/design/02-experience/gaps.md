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
| Own new gameplay follows Undo; separately, opponent gameplay follows Undo. | Own successful new gameplay clears your stack across devices even if Redo would be safe. Opponent actions retain it, but may block Redo. | D-227/D-228/D-235. |
| A rejected/no-change request follows Undo. | No successful new gameplay mutation occurred, so it does not trigger own-new-play stack clearing. Prediction/acknowledgement bookkeeping must converge to that outcome. | D-176/D-227. |
| The next Redo entry is blocked but an older one is safe. | Disable Redo and retain the stack; never skip/discard the next entry. Successful Redo order is last-undone-first. | D-229/D-230. |
| An eligible action is removed from a linear path. | Edit the existing tree; do not preserve an original explorer alternative, duplicate the suffix or append compensation. The removed action is held for Redo. | D-232. |
| The history node you are viewing is deleted. | Fall back to its parent, remaining in personal review. Repeat along the parent chain if necessary to reach a surviving ancestor/root; do not retain a detached snapshot, jump to live play or create a branch. Cursors on surviving nodes stay there. | [Review cursor fallback](../03-contracts/history-and-undo.md#selective-mutation-undoredo--d-224), D-238; T-535–T-537. |
| Undo would remove a fork or reach back before one, including a hidden alternative. | Block Undo. Explorer filtering is not branch removal. Actions strictly after the latest fork remain subject to ordinary eligibility. | D-233. |
| A new fork would put a queued Redo insertion before it. | Block Redo and retain the stack, even if replay would be safe. No recovery route is promised for the stranded mutation. | D-234. |
| A real source leaves Battlefield, including a disappearing token. | Remove its combat occurrences/incident links; keep independent Stack effects until explicitly removed. Do not invent a replacement source for actions on a nonexistent token. Inspection details are a separate open case below. | [Projection lifetime](../03-contracts/combat-stack-and-projections.md#135-confirmed-area-specific-lifetime-d-124d-125), D-120/D-124/D-125/D-187. |
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

## Genuine gaps

These are concrete remaining product choices identified in this pruning. Existing
rules constrain them but do not uniquely select the missing outcome. They are
not resolved by the table above; no option is silently approved here.

| Missing outcome | Why current rules do not finish it | Primary contract / tracking |
| --- | --- | --- |
| **Communication correction.** May submitted chat/comments be edited or deleted, and what revision context remains? | Chat non-undoability and comment dependency/grouping rules do not decide a separate message-edit/delete feature. Ordinary Undo of an eligible comment remains governed by existing rules. | [Optional text](../03-contracts/visual-action-language.md#116-optional-text), Q-26/Q-31. |
| **Competing result suggestions.** What happens when another result is suggested while one is already pending? | Other-player acceptance and non-undoable return to lobby are settled; one replaceable suggestion versus several pending suggestions is not. Do not reopen acceptance requirements or invent scorekeeping. | [Ending play](../03-contracts/accounts-decks-and-lobby.md#result-suggestion-acceptance-and-return-to-lobby--d-211), Q-10. |
| **Inspection after source disappearance.** What information remains inspectable in a surviving Stack effect/reference display after its token source disappears? | Keeping the occurrence/reference is settled, as is the impossibility of editing a nonexistent source. Last-known card state versus only retained reference/printed information is not specified; do not silently recreate the token. | [Projection lifetime](../03-contracts/combat-stack-and-projections.md#135-confirmed-area-specific-lifetime-d-124d-125), Q-28/Q-33/Q-35. |
| **User-visible recovery and privacy promises.** Is saved-deck/cancelled-game deletion recoverable, and what downloaded account data survives Logout/account switching or erasure? | Copy isolation and removal from current lists do not promise trash/restore, permanent deletion or local-data retention. Those are real recovery/privacy choices; backup/storage mechanics are separate. | [Accounts/decks/lobby](../03-contracts/accounts-decks-and-lobby.md), Q-09/Q-18/Q-19. |
| **Remaining release scope.** Which still-proposed authoring/coordination capabilities belong in R1? | A generic data model or an old draft does not approve custom non-card pieces, arbitrary authoring/layout tools, fuller deck editing/export, manual group editing, response requests/read receipts or extra areas/import formats. Review the scope bundle rather than design every hypothetical tool. | [Broader draft scope](#broader-draft-scope--not-all-confirmed), Q-11/Q-13/Q-14/Q-27. |

This is not a certification that behavior specification is complete. Further
edge cases may be found, but an owner question needs a concrete unresolved
outcome or conflicting requirements, not merely a new example of an existing rule.

## Specification work — not fresh product questions

| Work lane | What remains | How to handle it |
| --- | --- | --- |
| **Contract/test completion (stage 1).** | Per-mutation relevant fields/preconditions, sensible bulk ordering, exact guards, reference/cleanup mappings, no-op/failure outcomes and traceable scenarios. Audit grouping precedence/first-context sequences and offline inspection against existing rules. | Write spec-derived outcomes in reviewable batches and test them. Escalate only an actual ambiguity/contradiction. Do not ask for each move/tap/annotation dependency, empty draw, absent source or selected-target variant. |
| **Technical design.** | Replay N/counting, insertion-anchor encoding, node IDs/versioning, cached-state rewrites, request identities/ordering/recovery, Stack snapshot equality representation, randomness source, numeric/resource limits, import normalization/provider compatibility and schema/API details. | Specify and validate against approved observable outcomes in the relevant technical work. A missing representation/parameter is not a missing usability model. Online-created unsent/recovery cases still need explicit contract coverage; first check existing connected-only, pending-work and no-duplicate requirements. |
| **Later UI decisions (stage 3).** | Exact screen/menu names, control placement, gesture arbitration/drop geometry, accessible alternatives, feedback, browser rendering, animation timing and notification permission/channel presentation. | Preserve approved gestures, placement rules, immediate sync and read-only access. Do not present every layout/default as an unfinished stage-1 behavior decision. A real missing state transition still belongs in the product lane. |
| **Platform/service/release constraints.** | Supported Android/device/language/distribution targets; hosting/catalog rights, security/privacy, retention/erasure, cache policy, notification delivery and operational/performance objectives. | Keep them visible for their proper decision/design stage. Product promises and scope need owner approval; technical choices remain technical work. Neither is an excuse to invent a new gameplay rule. |

The [classified Q-register](../../agent/decisions.md#93-release-blocking-questions)
keeps stable tracking IDs while replacing the original mixed question queue.
Older "open" wording elsewhere is not an automatic owner-question queue. Known
drift and the pruning rationale are in the [specification audit](../../agent/specification-audit.md).

## Broader draft scope — not all confirmed

Do not silently include or exclude these because they appeared in an older draft:

- Arbitrary named values/participant associations, custom non-card pieces/markers,
  general authoring/layout or correction tools beyond the approved tabletop tools.
- Full manual deck authoring/export, additional import formats/sections, configurable
  starting arrangements or extra in-game areas beyond approved scope.
- Optional manual group editing, branch comparison/renaming, live-follow or
  cross-device personal review progress, response requests/read receipts, additional
  alert categories, presence and recovery-center features.

Generic primitives and capability inventories do not approve these features.
Existing confirmed accounts/import/lobby/gameplay/history/push behavior remains in
the [overview](behavior.md); it is not another scope question here.

**Already deferred, not R1 gaps:** finished-game history archives/review are
optional future development (D-214); in-app global/per-game notification mutes
are post-R1, using Android channel settings in R1 (D-220).

The release direction still excludes automatic rules/legality enforcement,
competitive anti-cheat, matchmaking/rankings/tournaments/clocks, public spectators/
chat, AI opponents, multiplayer and non-Android gameplay clients. Offline gameplay
creation/replay is excluded by D-182; downloaded read-only access remains.
