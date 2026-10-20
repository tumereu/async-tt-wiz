# Grouping scenario matrix and playtest contract

**Specification only.** D-251 requests category-based rules with a full scenario
matrix for future real-backend/normal-Android E2E tests and later playtest tuning.
No tests or runtime configuration/editor are implemented here. The normative
[grouping contract](../03-contracts/history-grouping.md#deterministic-sequence-resolution)
owns the algorithm; changes to a pairing update that rule, this matrix and stable
acceptance outcomes together, rather than accumulating per-button exceptions.

## Category mapping

"Tap"/T denotes semantic orientation mutations, not pointer taps. Under D-267,
normal double-tap tap/untap emits the existing individual orientation mutations;
single-tap Actions emits no grouping input. Category counts/rules do not change.

| Key | Literal operations | Matching key / context |
| --- | --- | --- |
| T | Tap, untap; each member of Untap all or selected orientation edits | Pending tap run; only taps/pings permit a further tap to stay. |
| M | Actual-card moves/reorders outside Stack | Ordered origin/destination tuple, including same-region rearrangement. |
| I | Real-card Stack entry/exit, effect creation, explicit effect removal | Isolated anchor: never merge independent I operations, even same source. |
| E | Corner/counter/note/face/cover edits, life/player counters, links, token Remove | Ordinary edit family, across fields/targets. |
| C | Catalog/Blank token creation and Clone | Creation family; interleaved E customization can join without replacing C. |
| K | Combat-area creation, declaration, occurrence layout/removal | Combat family; closure is F, source taps are T. |
| L | Stack layout/order adjustments | Layout family, not I. |
| V | Explicit Inspect other side, View/Reveal opening, standalone exposure record | Each opening/disclosure is a distinct anchor, not an ordinary hold. |
| H | Shuffle all | Same named Library identity. |
| F | View/Reveal dismissal, combat closure | Pending forward segment, not backward into its displayed state. |
| R | Roll die, Flip coin | Pending forward random-tool run, separate from completed prior play. |
| P | Ping/Unping via mode/context, each selected destination | Backward companion, scoped to its recording group. |
| B | Event comment, Next step/direct tracking/End turn | Backward companion; not a substantive anchor or a ping. |
| S | Pending win/loss/draw suggestion | Strict singleton: no preceding or following event joins it, including companions (D-257). |
| O | Leading/root annotation-only group | Group context, not an additional operation. |

Own-area hold/visible inspection, selection/mode changes, pan/zoom, navigation and
Chat have no grouping input. Controls such as Undo/Redo and explicit activation
are not new board anchors: recompute/use their actual resulting path. Mandatory
resets/cleanup remain part of the triggering mutation/category. A revealing move
remains M/I, not an extra V opening merely because it includes exposure.

## Complete category-transition matrix

Rows describe the **current group context**, not simply the last command. Columns
are incoming operations, all by the same author on the same path/incarnation.
These are authoritative server-created events, not UI-defined groups (D-254).
Clients send requests and may predict this deterministic result; menu opening
binds targets, not group membership. T-602 covers a request spanning a changed
predicted group without adding another grouping policy or stale-group guard.

- `+`: append to current group; `|`: start a new group immediately.
- `k`: append only if matching family key (move tuple or Shuffle target), else `|`.
- `t`: append only if the whole current group contains taps/pings and nothing
  else; otherwise `|`. This includes a leading pings-only group.
- Filling a pending T/F/R segment with an anchor sets that anchor's category/key.
  F/R without an anchor remain pending; P/B never replace the context key. S is
  never absorbed by a pending segment; any event after S starts a new segment.
- **Author change overrides every cell with `|`.** A first operation makes a
  leading group; there is no fake preceding event.

| Current context ↓ / incoming → | T | M | I | E | C | K | L | V | H | F | R | P | B | S |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| T — pending tap run | t | + | + | + | + | + | + | + | + | + | + | + | + | \| |
| M — non-Stack move | \| | k | \| | \| | \| | \| | \| | \| | \| | \| | \| | + | + | \| |
| I — isolated Stack action | \| | \| | \| | \| | \| | \| | \| | \| | \| | \| | \| | + | + | \| |
| E — ordinary edits | \| | \| | \| | + | \| | \| | \| | \| | \| | \| | \| | + | + | \| |
| C — creation/customization | \| | \| | \| | + | + | \| | \| | \| | \| | \| | \| | + | + | \| |
| K — combat edits | \| | \| | \| | \| | \| | + | \| | \| | \| | \| | \| | + | + | \| |
| L — Stack layout | \| | \| | \| | \| | \| | \| | + | \| | \| | \| | \| | + | + | \| |
| V — logged opening/exposure | \| | \| | \| | \| | \| | \| | \| | \| | \| | \| | \| | + | + | \| |
| H — Shuffle | \| | \| | \| | \| | \| | \| | \| | \| | k | \| | \| | + | + | \| |
| F — pending closure | \| | + | + | + | + | + | + | + | + | + | + | + | + | \| |
| R — pending random run | \| | + | + | + | + | + | + | + | + | + | + | + | + | \| |
| O — leading annotation(s) | t | \| | \| | \| | \| | \| | \| | \| | \| | \| | \| | + | + | \| |
| S — ending suggestion | \| | \| | \| | \| | \| | \| | \| | \| | \| | \| | \| | \| | \| | \| |

This gives 182 context/operation cells, not 182 claimed executed tests. Expand `k`
into same/different keys and `t` into pure taps/pings versus a backward-comment/
tracking-contaminated run. For every cell cover representative real controls;
family-mapping coverage must include all operations in the table, not just a
single convenient button. Explicit invariant-excluded combinations are model
fixtures only, never fabricated UI setup. Excluded features are not test inputs.
In particular, the single-pending-suggestion rule prevents an accepted S→S pair
in ordinary current play: test rejection of the second request, not a fabricated
two-suggestion UI state. The S row/column express unconditional singleton isolation
where events are otherwise applicable. P/B after S start their own segment, not
companions inside S. Acceptance/return is outside this gameplay-event matrix.

## Multi-step traces (same author unless stated)

Each trace is a named test family. Assert **every prefix**, not only final `|`
separators. `move a`/`move a` means matching tuples, not necessarily the same card.

| Trace | Sequence and expected grouping | Specific observation |
| --- | --- | --- |
| GM-01 | `move a \| tap; move a` | New tap boundary survives a matching following move; replaces D-250/T-304's old oracle. |
| GM-02 | `edit \| tap; edit` | Same correction for the edit family. |
| GM-03 | `move; ping X \| tap; ping X; tap; Stack entry` | At the first tap, old X overlay is not inherited. New context offers Ping X, not Unping old X. The Stack anchor fills the tap segment without another reset. |
| GM-04 | `tap; ping X; tap; Unping X; move \| tap; move` | Pure tap/ping run remains together; later tap after its anchor starts fresh. |
| GM-05 | `move; ping X \| tap; ping Y; Unping Y; move` | First group's X remains; the second toggle removes only Y. No retroactive overlay at any earlier prefix. |
| GM-06 | `tap; comment \| tap; move` and `tap; tracking \| tap; move` | Backward companions join but are not taps/pings; subsequent tap starts a new run. |
| GM-07 | `move a; ping; move a \| move b` | Pings preserve the current move key; a different tuple still splits. |
| GM-08 | `tap; effect X; ping \| effect X \| real card → Stack` | Independent Stack anchors stay separate despite same source/target category. |
| GM-09 | `create token; note; Clone; counter \| move` | C survives interleaved E; ordinary `edit \| create token` has the opposite boundary. |
| GM-10 | `create combat; declare; reposition; remove occurrence \| close; move` | K membership and forward closure differ; cleanup never edits source cards. |
| GM-11 | `Stack entry \| layout; layout \| remove effect` | I/L/I separation. |
| GM-12 | `move \| View \| Reveal \| dismiss; tracking; Stack entry` | Distinct V anchors; F owns its backward companion and following anchor. |
| GM-13 | `edit \| roll; flip; tracking; edit` | Random run starts separately and fills forward; stored results do not reroll. |
| GM-14 | `Shuffle A; ping; Shuffle A \| Shuffle B` | H compares target identity, not simply operation name. |
| GM-15 | `View \| dismiss \| tap; move` | The new tap boundary wins; no backward migration of dismissal to View. |
| GM-16 | `ping X; tap; ping Y; tap; move` from root | Leading P qualifies for T extension; no owner-action picker. |
| GM-17 | `comment \| tap; move` from root | Leading B does not qualify for pure T extension. |
| GM-18 | `A:tap; ping \| B:ping \| A:move` | Author boundary overrides every companion/matching-family cell; mode remains off on revisit. |
| GM-19 | `tap; [own hold]; ping; tap; move` | Own hold has no event/separator/Undo barrier; opponent hold adds no hidden disclosure. Explicit Inspect other side is V instead. |
| GM-20 | `ping X; ping Y; Unping X; ping X` in one context | Visible Y retains ordinal 2; new X gets 3. Retrying either addition creates no ordinal. |
| GM-21 | Historical ping/comment recorded after current G, referring to inactive E | Recording sequence controls grouping; route to E does not move records into E or activate it. |
| GM-22 | Eligible Undo/Redo before/between groups containing pings | Preserve recorded toggle scopes, presence, ordinals and effective contexts. Block a tree edit whose relevant semantic context would change; never silently reinterpret Unping. |
| GM-23 | `edit \| suggest draw \| ping; tracking` | The suggestion admits no backward companions. Following P/B are in a new context, with no inherited ping overlay; a rejected duplicate suggestion creates no event/group. |
| GM-24 | `tap \| suggest win \| edit`; `roll \| suggest loss`; `View \| dismiss \| suggest draw` | Singleton S overrides pending T/R/F forward absorption. Retain every real boundary; do not merge the prior board into the suggestion. |
| GM-25 | Opponent catches up through final board changes, then the singleton suggestion | Show the final board before the acceptance dialog. A newly received pending suggestion cannot cover earlier unreviewed groups with the dialog. Sync is not delayed; removed/inactive historical suggestions are not actionable prompts. |

## Full E2E expansion and playtest evidence

Follow [the normal-app E2E boundary](e2e-strategy.md): reset before each independent
variant; helpers create only accounts/saved decks. Both participants build game,
cards, effects, pings and history via real UI. Setup actions are visible declared
preconditions, not backend-injected history. Use synthetic faces/art and valid
three-member ordinary piles. Establish a known author/group boundary before the
measured sequence so setup does not accidentally supply its first anchor.

For every cell/trace, verify after each prefix on actor and observer:

1. Immediate mutation/event visibility and exact selectable node/effective boundary.
2. Group start/continuation and current family/key; no publication wait for an anchor.
3. Ping/Unping menu label, addressed record/context, marker presence and ordinal.
   Repeat with destination already present/absent, two targets, mixed selections,
   context command versus Ping mode and accessible controls.
4. Ping mode exits only on an actual new event; pending-segment anchor attachment
   does not reset it. Revisit restores overlays without enabling mode.
5. Incremental sync and later full download yield identical group/ping outcomes;
   an observer's paused exact cursor stays on its node. App restart preserves records.
6. Repeat with author change and eligible Undo/Redo/replacement play. Assert
   ordinary eligibility, reference barriers and no new branches from regrouping.

For playtest revisions, report category pair/context, trace/prefix, observed
grouping versus desired grouping and affected ping/mode behavior. Update the
central rule and all affected oracles only after owner selection. This planning
does not promise independent in-app per-pair settings or manual group editing.
