# Shared operation context and composition

This contract completes the existing D-176/D-179/D-183/D-184/D-187 rules for
S-01/S-02/S-05/S-06/S-08 of the October 20 review. D-251–D-253 record the owner's
grouping, inspection and shortcut corrections. These are documentation contracts,
not an API/schema choice or implementation authorization.

## Select execution state before validating an operation

1. Resolve account/game access and the request's original lifecycle scope:
   persistent lobby, preparation round or play incarnation, as applicable.
   Recover an already-processed request's original outcome before considering a
   new application. Reuse never grants a request fresh intent.
2. An ordinary request uses the authoritative shared state at its processing turn.
   An explicitly requested historical continuation uses the selected retained
   boundary's current authoritative revision. Validate that boundary separately;
   a removed/unavailable boundary is not permission to choose its parent or head.
3. On **that one execution state**, evaluate applicability, source/destination
   movement guards, D-247 Stack equality, live derived reads and structural checks,
   then construct the actual transition atomically. Never borrow the live head's
   Stack, source location or fields to validate a historical operation.
4. Ordinary success extends current play. Historical success creates/activates the
   explicitly requested continuation, retains the displaced future and clears both
   players' Redo queues atomically under D-258. Rejection
   creates neither a board mutation nor an automatic branch. A retry recovers the
   original outcome without reactivating its branch or overwriting newer state.

Example: at B Stack is empty; live C contains effect E. Deliberately creating F
at B compares expected `[]` with B's `[]`, producing a branch containing F, while
C/E survives. An ordinary addition at C with expected `[]` fails. A historical
move can similarly use a source present at B but absent at C; it still fails if
invalid at B. No whole-head unchanged-value lock is introduced.

## Bind identities and choices, not predicted output snapshots

Capture menu/editor target IDs under D-248 and normalize their identity domain
once. Keep explicit text, chosen face, destination, quantity and invocation scope.
For an already-bound target, ordinary derived reads use the execution state above;
changed fields are not a new rejection reason. Predictions are provisional output,
not instructions to overwrite current state with an obsolete snapshot.

| Operation | Bound intent | Authoritative live reads and outcome |
| --- | --- | --- |
| Set / chosen orientation, cover or printed face | Target/field and explicit desired value. | Write that value if applicable; preserve other fields and record actual before/after. |
| Numeric +/- | Target/field and delta. | Read current usable number; apply delta once. |
| Corner Reset | Target and Reset operation, not the face seen when the menu opened. | Read currently selected face and its retained default. F1 changing to F2 means Reset F2, without a stale-text/face lock. |
| Clone | Same live source ID and Clone operation; new identity is independent. | Copy that source's current approved fields/defaults at processing. Changed counters/notes do not reject or force an old copy. Missing/frozen-only source is inapplicable; no substitute. |
| Shuffle all | Named Library, new invocation identity and original fresh client seed (D-274). | Shared versioned Fisher–Yates/unbiased sampling over canonical IDs of **all current accepted members**, including arrivals/excluding departures; supplied seed, not an independent server reroll. Preserve unrelated fields; no old-membership lock. Record the concrete accepted permutation. |
| Specific-card move / top/bottom-N move | Captured card identities/order, source and chosen destination/edge/gap. | Apply the movement guards on the execution state. Never substitute another card to repair a stale selection. A Draw still needs its recorded chosen card at the top. |
| Ping / Unping | Captured destination identities, requested operation and any explicit historical reference; no bound recording group. | Process in server order and derive recording context from authoritative events under deterministic grouping (D-254). Preserve explicit Ping/Unping semantics; an already-satisfied operation creates no invented event. A stale group prediction is not an extra guard or authority to change old-group markers. |
| Explicit inspection / View / Reveal | Specific inspection binds target/face/context. Contents View/Reveal binds source, top/bottom/all and requested count, not predicted members. | Resolve contents members/order/count clamp in the selected execution state; freeze accepted display references afterward under D-47. Specific missing/inapplicable targets reject normally. Reconcile different/rejected predictions without separate disclosure reports or old-context reconstruction (D-286). Own-area hold is the unlogged D-252 exception. |
| Next step / End turn | Relative tracking operation. | Compute from current tracking when this constituent is processed, not a stale replacement tuple. |

Retry, review and historical Redo reuse accepted concrete results/inputs; they do
not rerun live Reset, Clone, arithmetic or randomness. Redo fidelity remains
stricter than this live acceptance rule. D-261 permits local die/coin samples reused
by the same client/server processing code; acceptance remains server-authoritative.
D-274 selects fresh request-bound client seeds and shared versioned Fisher–Yates/
unbiased sampling over canonical current-member IDs. Exact generator/encoding/
versioning remain technical design; uncontended shuffle-and-draw predictions agree
without a routine acknowledgement barrier, while not omitting/resurrecting changed
members. See the
[random-input design](../04-technical/sync-and-storage.md#selected-shuffle-and-setup-randomness--d-274).

## Ordered multi-object lowering

Use this transformation for an ordered destination (Library, ordinary visual pile,
combat slot), preserving each container's distinct capacity/containment rules.
Graveyard/Exile have no insertion order. This is an ordered sequence of individual
operations, never an atomic multi-card node or server reservation.

1. Normalize/deduplicate moved identities in their approved pre-action order S.
   Use source order within one ordered source/slot, the approved spatial order
   across sources, and the existing occurrence/source domains. Do not reverse S
   merely to implement repeated single-card top insertion.
2. Resolve the intended destination edge/gap **after excluding all members of S
   already there**. Keep the untouched neighbor identities, not an index into a
   list the invocation itself will change. The intended final order is unchanged
   destination members with S inserted at that edge/gap. Preflight capacity and
   structure for the entire placement; invalid placement emits nothing.
3. Emit members in S order. The first uses the normalized edge or immediately
   after the untouched left gap anchor (start if none); each later member inserts
   immediately after the preceding successfully placed member. Each transition
   removes that member from its current source and inserts it once. For local
   gap validation, exclude the invocation's still-pending moved members, but not
   unrelated arrivals; require the chosen untouched gap/edge and emitted-prefix
   anchors to remain valid. No anonymous neighboring card may replace an anchor.
4. If a member is already exactly in its required position, retain its identity
   as the prefix anchor without fabricating a node. An unchanged final layout
   emits no moves. Unmoved members retain relative order at every boundary.
5. Source checks reflect the actual command. Draw N emits the original top cards
   top-first, each requiring that card now on top. Moving a captured bottom-N
   slice top-to-bottom checks that recorded slice/frontier, not a false requirement
   that its first member be the bottom card. Specific-card moves check the bound
   source identity/placement, not an invented Draw dependency.
6. Each move rechecks its own conditions at arrival-order processing. If a prefix
   anchor or source was invalidated, reject that member rather than redirect it.
   Later members are still checked under their own original conditions. Accepted
   siblings and interleaved opponent work remain; no compensating rollback.
    Mandatory empty-pair/link/source cleanup belongs to the triggering transition.

For destination-prefix construction, distinguish **placement**, **already-placed
no-op**, and **token disappearance**. A token sent to a configured disappearance
destination performs its normal individual move/removal but never occupies that
destination and never becomes the next member's insertion anchor. Keep the last
actual placed/no-op member, or the original edge/gap if none. This does not skip a
token's operation or excuse a formerly live anchor invalidated by the opponent.

Examples using Library/combat slots (not four-member ordinary piles), top/left first:

- `[A,B]` onto top of `[X,Y]`: A produces `[A,X,Y]`; B inserts after A,
  producing `[A,B,X,Y]`. Draw A then B from a source `[A,B,C]` has valid top
  checks at both boundaries. Bottom insertion yields `[X,Y,A]`, then `[X,Y,A,B]`.
- In `[A,B,C,D]`, move `[B,D]` into the reduced gap A/C: B is already correctly
  placed (no node); D produces `[A,B,D,C]`. Move them to start instead: B produces
  `[B,A,C,D]`, D produces `[B,D,A,C]`. No duplicate or capacity bypass.
- If an opponent removes A after the first top insertion, B's after-A insertion
   fails rather than becoming a fresh top drop. Do not undo A's accepted move.
- Move `[token T, ordinary C]` to Library top `[X]`: T disappears, then C yields
  `[C,X]`, not an after-T failure. Move `[C,T,D]`: intermediate Library states are
  `[C,X]`, `[C,X]` (T's disappearance), `[C,D,X]`. The same rule applies to bottom
  insertion and mulligan returns. C's later concurrent removal still invalidates
  D's bound after-C insertion; it does not authorize a new edge.

Record actual anchors, before/after order and mandatory effects for exact inverse
traversal and D-224 replay dependencies. Wire encoding of invocation membership
and gaps is technical work, not permission to choose different intermediate boards.

## Composite commands and quick-action scope

D-253 corrects D-121: **individual actions** retain full action paths outside their
quick windows. A combined shortcut may be quick-only. Do not invent a Cleanup
meaning for advance-and-close or add a phase legality gate to Close/Next step.
Explicitly confirmed Library-context mulligan commands remain available there.

Capture the affected configured participant region/containers and target identities
at invocation. Untap uses the chosen turn player's configured Battlefield region,
not original deck ownership or inferred control; a card moved there is in scope.
Draw uses that participant's bound Library and Hand. An opponent turn change must
not redirect remaining constituents to the other side. Actual card fields and
each relative tracking transition still use processing-time reads.

| Command | Availability and captured scope | Individual sequence |
| --- | --- | --- |
| Untap all + advance | Quick at Untap, for the invocation's turn-player region. Standalone Untap remains available separately. | Captured cards in visual order, each explicit untap; then relative Next step on then-current tracking. Empty/already-untapped scope still advances. |
| Draw one + advance | Quick at Draw, with bound Library/Hand and predicted top card. Standalone Draw remains available separately. | Guarded card move if the Library was nonempty, then relative Next step. Initially empty Library emits no move but still advances. |
| Advance out of combat + close | Quick-only at End of combat. | Relative Next step, then closure of the captured combat-area ID with mandatory occurrence/link/pair cleanup. Normally reaches Postcombat main. An absent area makes closure a no-op, not failure of advancement; never close a replacement area. |
| Declare with tapping | Quick at Declare attackers; plain declaration and Tap have full paths. | Ensure/create the area, each source's declaration/reuse in approved spatial order, then each explicit tap, retaining individual valid boundaries. No source movement or inferred legality. |
| Mulligan N / hand-size-minus-one | Confirmed Library context access; quick only turn 1 Untap. Bind that Library/Hand, initial whole-hand identities and requested count. | Return captured hand cards in displayed order using ordered lowering, Shuffle the then-current whole Library, then guarded top-first draws. Minus-one is max(0, pre-return hand size − 1); explicit N is nonnegative and available-card-clamped. No bottoming/keep gate. |

All constituent operations are sent in order and may interleave with the opponent.
No-change constituents fabricate no node; valid later constituents still run after
an earlier individual rejection. Draw identities are selected from the preceding
prediction, reconciled to authoritative outcomes without silently changing in-flight
requests; a differing accepted Shuffle may invalidate predicted draws, not authorize
substitution. There is no new acknowledgement barrier, all-or-nothing composition,
or automatic extra draw to repair a failed member. Record the actual partial result.

If tracking interleaves between untaps and the final Next step, Next step advances
the tracking it actually receives; it does not overwrite it with captured Upkeep.
The scope remains the initially bound side. Reopening menus later creates fresh
intent, not retargeting of the old invocation. Exact feedback/control layout and
provisional-reference mapping remain technical/UI work.
