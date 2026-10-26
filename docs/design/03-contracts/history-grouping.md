# Automatic history grouping — action inventory

**Status:** D-251 confirms the tap-run correction and category-based scenario
matrix. It supersedes D-250 and D-164's matching-moves-across-taps exception.
Other confirmed family rules remain, except D-257 makes every ending suggestion a
strict singleton group with no companions. This is not an action schema, an in-app
group editor or an implementation authorization.

D-267 changes the normal physical card gesture outside Hand to double-tap tap/untap and
single-tap Actions. Here "tap" is the resulting orientation mutation, not a raw
pointer tap: one double-tap orientation operation is not two grouping inputs, and merely
opening Actions contributes none. Existing grouping rules are unchanged.

[History section 3.4](history-and-undo.md#34-sensible-grouping) owns the invariants:
immediate publication, effective node boundaries, latest-own tree-editing Undo and no inferred
game mechanics. [The action catalog](game-actions.md#42-coverage-matrix) lists
capabilities; it is not yet a complete list of fully specified commands/events.

## Mutation tree versus playback groups (D-176/D-177)

Playtests r2/r6 correct Undo scope: a quick action or multiselect performed at once
is one move; independent invocations are not combined by display grouping. Manual
whose-move pass is a separate event/Undo, displayed with its predecessor; automatic
step-based move changes are bundled invisibly in the tracking event.

One tree node is one individual mutation, such as moving or tapping one card.
Every mutation immediately extends or branches the chosen path and publishes.
Bulk controls issue individual operations in a sensible recorded order. The
client's state points to a mutation node (or root), never a group.

Groups only control navigation/animation/representation: one group is one
playback step with concurrent animation of its changes. Pause playback and scrub
the horizontal bottom timeline to select an exact mutation, including within a
group. Group membership never decides whether to publish or combine mutations.
D-277 clarifies animation: compute a playback group's endpoint board and animate
the net difference from the preceding displayed boundary. Multiple moves of the
same card within that group are one original-to-final transition, not sequential
waypoints; separate groups remain separate staged transitions even when downloaded
together. In live mode apply remote batches immediately without group-closure
waiting. Every mutation/reference/random/exposure boundary remains recorded;
animation coalescing never changes the grouping algorithm or publication timing.
The remaining grouping questions below concern presentation only; they are not
questions about atomic gameplay units or which changes enter the tree.

## R1 representative group icons — D-299

**Superseded by owner playtest r6b:** use the group's LAST event's icon and author
color, and select its last event on tree tap. Recompute after tree changes. No
significance ranking, anchor preference or first-event tie break. The old ranking
and linked JSON fixture below are historical documentation provenance, not current
requirements or verification oracles.

The owner delegates the initial significance order. This is a **chosen R1 UI
ranking**, revisable through normal design feedback, not a change to grouping,
event types, tree ordering, Undo or game rules. The compact explorer draws one
group glyph, reusing the **actual representative event's existing icon** and
author color; it never invents a “cast,” damage or payment interpretation.

Resolve from the group's **currently effective prefix** at the viewed boundary:

1. If the group contains substantive anchors (I/V/C/K/M/H/L/E/S categories below),
   consider those events only. Otherwise consider the available companion/tap run.
   Token customization remains an ordinary field event for this display ranking;
   creation wins over its subsequent edits without changing their grouping.
2. Lowest rank in the applicable table wins. Ties use the **first event in
   effective recorded path order**, not object name, timestamp, last writer or
   arbitrary iteration order. Root is a separate initial-state marker, not a
   fabricated event/group.
3. Use that event's literal type icon, not an icon based on its rank label. Event
   details retain all members/counts/changes. In particular, a Draw or ordinary
   Move keeps its existing event-type icon; do not infer casting from card text.
4. Recompute after append/Undo/Redo/recovery/regrouping. Before an anchor arrives,
   show the best existing companion; do not borrow an icon from a future event.
   A representative changing never moves a group start, changes ping scope or
   retargets a user's captured activation boundary.

| Anchor rank | Literal event/context | Rationale |
| --- | --- | --- |
| 10 | Ending suggestion S | Already a strict singleton; preserve the visible outcome request. |
| 20 | Stack entry or explicit Stack removal I | Each is already an isolated anchor; show the entry/removal, not its taps/pings. |
| 30 | Explicit Inspect/View/Reveal opening V | Preserve the intentional information action. |
| 40 | Token or Clone creation C | Creation is more useful than its interleaved customization fields. |
| 50 | Combat membership: entry/declaration, role change, occurrence removal K | Show actual participation changes before layout/empty-area setup. |
| 60 | Empty combat-area creation K | A real area transition when no membership event dominates. |
| 70 | Actual cross-zone move M | Concrete card movement, including literal Draw where recorded as such. |
| 80 | Shuffle H | Recorded library-order change, not an inferred random game effect. |
| 90 | Explicit token removal E | A piece disappears, unlike an ordinary metadata adjustment. |
| 100 | Face/cover edit E | Visible presentation change, not an inferred activation. |
| 110 | Life or colored player-counter edit E | Prominent participant value change; no damage inference. |
| 120 | Card corner/counter edit E | Concrete card values before ancillary metadata. |
| 130 | Card owner edit E | Explicit owner metadata, not controller/permission. |
| 140 | Persistent link edit E | Relationship change, not a rules target. |
| 150 | Card text/note edit E | Plain text; do not parse it to promote its importance. |
| 160 | Combat slot/order/layout change K | Position-only change within the combat family. |
| 170 | Stack layout/order change L | No automatic resolution or priority inferred from layout. Hand's permanent-only double-tap shortcut explicitly enters Stack then auto-resolves to Battlefield as a separate history group. |
| 180 | Same-zone placement/rearrangement M | Useful fallback for a layout-only group. |

If there is **no anchor**, use this companion ranking:

| Companion rank | Event/context |
| --- | --- |
| 10 | Combat closure F |
| 20 | View/Reveal display dismissal F |
| 30 | Die/coin tool result R |
| 40 | Tap/untap orientation T |
| 50 | Turn transition B |
| 60 | Phase/step tracking B |
| 80 | Ping/Unping P |

Thus taps → Stack entry → ping → Next step shows the **Stack entry** icon;
token creation → corner/counter/owner edits shows **creation**; card-counter →
life edit shows the **life event**; tap/ping-only shows **orientation**. A random/
closure run takes its following real anchor once present. These are display
choices over already-derived groups, never permission to combine invalid groups
or absorb an ending singleton. Ordinary authorship boundaries still win.

[Literal review fixtures](../02-experience/ui/group-icon-order.json) record these
ranks, representative IDs and prefix/tie examples used by documentation capture.
They are not application configuration or a substitute for the future grouping
engine/tests. Rank keys are documentation categories, **not new wire event kinds
or client-supplied group definitions**. Accepted mutation family/fields supply them;
unknown/invalid event data follows compatibility/recovery handling, not guessed
MTG semantics or a generic icon silently replacing a known type.

## What the grouping rule needs to consider

The owner's examples rule out one invocation = one group and a rule based only
on the immediately preceding action:

- A sequence of taps may belong with a following substantial action.
- Pings can naturally annotate the preceding action instead.
- Repeated battlefield rearrangements may belong together, without absorbing a
  later Hand → Stack move into the same group.

Resolve groups from the recorded sequence and current category context, not just
the preceding operation. D-251 now makes the first tap after substantive play a
new group immediately. Further taps/pings can grow that run, and its following
substantive action joins it. A later anchor never moves that run back into the
preceding group. This replaces the earlier provisional-tap regrouping rule.

“Substantial” or “anchor” is review vocabulary, not a new engine-recognized cast,
payment or resolution. We must enumerate the literal cases that play that role.
Operation kind, affected objects, actual origin/destination and invocation context
may distinguish cases; do not read card text or infer why a card was tapped.

The owner clarified the model as **group-start markers on the timeline** (D-168).
A same-author ping does not insert a new marker; no extra user-facing “which action owns this
ping?” workflow is wanted. Its place/effective boundary in the recorded timeline
still determines when it exists. Grouping never retroactively exposes a ping at
an earlier node. This replaces the assistant's proposed separate ownership
question, not the preservation of recorded ping context/numbering.

### Deterministic sequence resolution

**Server event authority — D-254:** UI actions send requests, not group definitions.
The server processes those requests in the agreed arrival order and creates the
authoritative individual events. Deterministic logic groups those events using
the rules below. A client may predict the same result for responsiveness, but
neither opening a menu nor sending a request binds a group or forces a marker.
Current group context means the context derived from the authoritative event
prefix, not a client-selected group. Historical reference targets remain separate
semantic data; they do not assign the new recording event to an old group.

1. Work on one path/incarnation in effective recorded order. Author changes always
   start a new group, including for companions. Local unlogged inspection, mode
   changes and Chat are not grouping inputs. **Before applying any same-author
   companion/anchor rule, isolate each ending suggestion (S): it never joins the
   preceding group and no following event joins its group** (D-257). Pending taps,
   closures or random-tool runs remain separate; P/B after S start a new segment.
2. A tap/untap starts a new group unless the current same-author group contains
   only taps and pings. In that case append it. Pings alone in a leading group
   also qualify. A tracking record is not a ping: it attaches backward
   but makes a following tap start a new group.
3. A taps/pings-only run accepts its following substantive action as anchor,
   except an ending suggestion, which is always separate.
   Thereafter the group uses that anchor's family/key; another tap starts a new
   run. Do not merge the preceding move/edit group back across the tap boundary,
   even if the following move/edit matches it.
4. Pings/tracking append to the preceding same-author segment, except
   after a singleton ending suggestion. With no attachable predecessor use a leading
   group. Closures and dice/coin runs start a forward
   segment, separate from completed prior play, and absorb the following anchor
   unless it is an ending suggestion. They can fill a pending tap/forward segment
   rather than create an empty extra
   boundary. Backward companions remain there. A tap still applies rule 2.
5. Other anchors join only matching families/keys below. Stack entries/removals
   and explicit inspection/View/Reveal openings each require a distinct anchor
   group, but can fill a segment containing only forward companions. Token
   creation groups also accept their interleaved customization edits. An ordinary
   edit group does not absorb a subsequent token creation merely by proximity.
6. Publish every effective record immediately. A trailing companion segment is
   already reviewable; no future anchor is required to finalize Ping/Unping.
   Extending a prefix does not relocate earlier markers, change ping ownership,
    reset an input mode through retroactive regrouping. Recompute display groups
    and active ping numbers after tree edits; neither is a concerned-field dependency.
    Batch/incremental receipt gives the same result.

Examples (one author; `|` is a group boundary):

- `move H→B | tap; move H→B` has two groups (D-251).
- `move H→B | tap; Stack entry`: the tap starts the new segment; the entry fills it.
- `edit | tap; edit` has two groups.
- `move; ping X | tap; ping X; tap; Stack entry` has two independent ping contexts.
- `tap; ping; tap; move | tap; move` has two groups, not one matching-move group.
- `View | dismiss; tracking; Stack entry`: closure starts forward, tracking attaches
  to that preceding pending segment; it does not merge the View with the entry.
- `edit | roll; tracking; edit`: the random run starts separately and attaches
  forward; the backward tracking companion belongs to that run's segment.

These apply D-251 and the existing family/companion rules, not an approval workflow.
Excluded manual group editors are not a fallback for ambiguous runtime grouping.

### Categories, keys and stable ping meaning

The [grouping matrix](../05-verification/grouping-matrix.md) is the pairwise and
multi-step E2E specification. Category rules live here; the matrix gives named
traces and every-prefix assertions so later owner-approved playtest changes can
update one rule and its affected tests together, not hard-code each button pair.

Use the **current group's** anchor family and key, not merely its last operation:
M = non-Stack moves keyed by ordered origin/destination; I = isolated Stack
entry/removal; E = ordinary edits; C = token/Clone creation plus customization;
K = combat edits; V = explicit logged inspection/View/Reveal;
H = Shuffle keyed by target; F = pending closure; R = pending dice/coin run;
T = pending tap run; P = ping; B = backward tracking; S = strict singleton
ending suggestion, unlike companion-accepting I. These are literal categories,
not game-rule interpretation. Accepted revealing board edits keep
their ordinary category; a standalone actual-exposure record uses V. Local own-area
hold inspection under D-252 has no category because it creates no event.

At each authoritative prefix, deterministic grouping determines the recording
context for Ping/Unping. Starting a tap group therefore starts an unpinged context;
it does not Unping X in the preceding move group. A context menu binds target
identities under D-248, **not a group** (D-254). The server applies the requested
Ping/Unping operation in its current derived recording context; a changed client
prediction neither targets an old group's marker nor adds a stale-group rejection
guard. Explicit Unping does not become Ping: if the destination is already absent
in that context, it changes nothing and fabricates no event under D-176.
Destination taps in Ping mode are P, never simultaneous T operations. A genuinely
new group exits Ping mode; adding an anchor to an existing tap/forward segment
does not.

Record Ping/Unping targets explicitly. For Undo/Redo a ping concerns ONLY that
target card, projection or player, not the recording node/ancestry or display
group. Projection targets remain projections. Grouping is recomputed bookkeeping.
Number currently active pings in event-group order starting at 1: ping A, ping B,
unping A leaves B numbered 1; re-add A follows B as 2. Retries add nothing.
Selection Ping uses normalized display target order and skips present targets.
Active markers loop an expanding actor-colored ring about every 1.2 s.

## Confirmed grouping rules (D-163–D-175)

**Author boundary (D-170):** a different
action author always starts a new group. No forward/backward companion, matching
category or no-artificial-reset rule may bridge that author boundary.

**Owner playtest r2 exception:** manual whose-move pass displays with the event
before it, while remaining its own event/Undo move and retaining its actor. An
automatic step-based move change is part of the tracking event, never a separate
pass/group input. This does not introduce any other auto-pass.

**Singleton boundary (D-257):** ending suggestions never group with anything,
including same-author preparatory or backward companions. This overrides every
family/companion row below; author separation remains intact.

| Incoming action/context | Confirmed behavior |
| --- | --- |
| Ending suggestion | D-257: a strict singleton S group. Never absorb preceding taps/closures/random runs or following pings/tracking. Group playback exposes the preceding final board before the suggestion's acceptance dialog. Acceptance/return is a separate lifecycle transition. |
| Tap actions | D-251: begin a new group unless the current group contains only taps/pings. The next substantive action joins the tap run except singleton S; another tap after an anchor begins a new group, even between matching moves. |
| Pings | Same-author pings join the existing group and insert no group-start marker (D-168), except after singleton S, where a new segment is required. If the author changes, D-170 starts a new group even for a ping. No separate ownership-selection step. |
| Actual-card moves outside Stack | Group moves with the same ordered (origin, destination) tuple. Hand, Battlefield, Exile, Graveyard and Library are the listed non-Stack locations. Different directions are different tuples. |
| Same-location card moves | The tuple model includes repeated Battlefield → Battlefield rearrangements; it does not require a separate grouping rule for each card or physical position. |
| Projection creation onto Stack | Never group it with actual-card moves or another projection creation. Each Stack projection can group with taps and pings. Do not group repeated effects merely because they share a source. |
| Stack entry | Every route creates a projection (never a real-card Stack move); each entry is isolated with the established tap/ping companions. Library entry is blocked. |
| Ordinary card edits and token removal | D-165/D-169/D-171 group card edits together, including counter/corner/text/note, manual face/cover edits and explicit token removal across cards. More-specific move/tap/Stack rules still apply. Player-explicit spells/abilities stay separately represented on Stack. Token creation/Clone is covered below. |
| Token creation and Clone | D-173: group token creation and Clone together, including interleaved customization edits: `[create token; edit; Clone; edit]`. Keep fresh independent token identities and every node; this is not projection creation. Do not infer a rule for arbitrary mixed categories. |
| Player values/counters and persistent links | D-172: player value/counter edits and persistent-link creation/removal join the same ordinary edit family as card edits and token removal, not separate per-target/per-field families. Example: `[card counter; life change; link creation; card note]`. This does not classify participant-association edits or infer damage/targets. |
| Combat-area creation and projection edits | Combat projections group together; D-166 includes repositioning of any kind, including role/slot/order changes. D-175 explicitly adds combat-area creation and occurrence removal to this family: `[create combat; declare; reposition; remove occurrence]`. Closure remains the separate forward case below; removal does not delete a real source. |
| Explicit inspection/View/Reveal openings | Each explicit logged opening is a distinct V anchor, e.g. `[moves] → [View top 3] → [Reveal bottom 2]`. Pending forward companions can join it; a second opening cannot. D-252 own-area hold inspection is unlogged, so never splits a group. |
| Explicit Stack-effect removal | D-175: each removal stays separately visible, like individual Stack entries: `[remove effect A] → [remove effect B]`. Existing companion rules still apply. D-176 makes bulk removal an ordered sequence of individual removal mutations, not an atomic bulk node; grouping remains presentation. Remove occurrences, not sources or sibling effects. |
| View/Reveal display dismissal and combat closure | D-166: do not attach the dismissal/closure backward to the displayed-area group. Put it with the following action. Group-level review shows the display/combat state, then the next step applies closure plus the following action. Keep the closure node itself inspectable. |
| Shuffle | D-167 groups successive Shuffles of the same target together. Different targets do not match; this is target identity, not simply both being Libraries. |
| Dice/coin tools | D-167/D-171: dice and coin invocations form one random-tool run, separate from prior play and grouped with the following action. Example: `[real card → Stack] \| [roll/flip run; following effect]`, not `[real card → Stack; roll] \| [effect]`. The effect remains a player-explicit action, not inferred from the result. |
| Phase/turn tracking | D-171: Next step, direct phase/step changes and End turn join the previous group, except singleton S under D-257. Author changes still start a new group. Keep constituent nodes and approved combined-action mappings; Draw + advance retains its two real boundaries. |
| Undo, replacement play and resuming | Recompute display groups and active ping ordinals; those are not concerned state. Preserve semantic targets and the authoritative concerned-field tuples, including recorded Library reseed input. |
| Different action authors | D-170: highest-precedence new-group boundary, overriding all other rules including pings. This is the actor, not which participant's cards were edited. |

These are literal presentation rules, not casting/payment/ability interpretation.
**D-174 scope restriction:** non-token cards never disappear during play and have
no Remove/delete action. There is no grouping case to decide for that rejected
action. Token disappearance/removal and occurrence-only projection removal remain
distinct supported operations.
They preserve immediate publication and every effective node boundary. An existing
group can grow without a Done button; waiting for a next action is not permission
to delay publication of taps or other completed actions.

### Tentative generalization — not yet approved

- **Other temporary areas:** View/Reveal dismissal and combat closure are
  confirmed forward cases. Applying the same model to every future temporary
  area's closure is a proposed generalization, not approval of new area types.

### MTG face-change rules check — evidence, not a new app rule

Checked 2026-10-19 against the official
[Comprehensive Rules effective 2026-09-25](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt),
linked from [Wizards' rules page](https://magic.wizards.com/en/rules):

- CR 116.1, 116.2b and 702.37e: turning a morphed face-down permanent face up by
  paying its morph cost is a special action, **does not use the stack**, and cannot
  itself be responded to.
- CR 702.37c: casting the face-down spell uses the stack and can be responded to.
- CR 603.3: an ability triggered by turning face up is put on the stack when a
  player would next receive priority and can be responded to normally. It is
  distinct from the face-up action. CR 117.3c gives the acting player priority
  again after a special action; this does not erase later response opportunities.
- Transforming/changing printed face is not synonymous with morph. A spell or
  activated/triggered ability that instructs a face change may be responded to
  before resolution; players do not receive priority during its resolution
  (CR 117.2e). Thus “every face change is a respondable activation” is incorrect.

**Confirmed D-169 after owner approval:** group ordinary manual face/cover edits with card edits;
record any player-explicit spell/ability separately on Stack under the existing
Stack isolation rule. Do not automatically detect triggers, pay morph costs or
infer response legality from a face edit. The rules evidence and the subsequent
owner approval are distinct; neither removes exposure retention.
Grouping is presentation, never a claim that reactions between
retained nodes are impossible.

### Remaining mapping work

1. **Action mapping:** use the category/prefix matrix above; D-251 replaces tap
   transparency. Further playtest revisions need an explicit rule/test update.
2. **Other action families:** arbitrary participant-association authoring remains
   excluded, but D-295 explicitly adds card-owner editing. It is an ordinary
   card-field edit in E (D-165/D-169/D-172); initial owner is part of creation, not
   a second automatic mutation. No new owner-edit grouping rule/picker.
   Covered-face inspection is a distinct exposure event with its viewed and
   recording contexts when explicitly invoked under D-252; own-area holds are
   unlogged. It is not an ordinary card-field edit. D-175 settles
   explicit View/Reveal openings, combat-area creation/removal and Stack
   rearrangement/removal grouping. Do not turn “all card edits” into a universal rule
   overriding the separately approved move/projection actions.
3. **No next/previous group:** use the leading/pending segments above. Exact
   marker rendering remains later UI work; no fake follow-up event is needed.
4. **Compound/location coverage:** map each actual shortcut constituent to the
   categories above and operation-composition ordering. Extra locations are
   excluded by D-241; do not make them runtime inputs. Further irregular spatial
   ties/rendering remain technical/UI work, not new node granularity.
5. **Historical additions:** distinguish recording and referenced contexts under
   the annotation contract. No manual group editing/event-ownership picker in R1;
   this is unrelated to D-295's card-owner action.

## Inventory to review

The older inventory below is superseded where it mentions real-card Stack moves,
Stack rearrangement, bottom-N displays, archive/restore or unrestricted owner edits.
Use projection-only Stack (no reorder), the nine-cell panel menu, local pile views,
and token-only Change owner in the primary board/Stack contracts. Reveal+Hand/Top
is explicitly TWO events in TWO groups: flip inside the same panel, then move,
with normal pacing and no special dwell. Closing Search shuffles as one display
group. The last panel card leaving closes it in that same event (r11).

These categories cover the current catalog, including proposed capabilities.
Excluded capability rows below are historical inventory only, never R1 algorithm
inputs or deliverables (D-241). Several catalog rows span categories; commands use the same literal
operation. A grouping category does not settle an unfinished action's state
changes or event mapping.

| Category | Literal cases to cover | Catalog coverage | Confirmed direction / remaining question |
| --- | --- | --- | --- |
| Orientation | Tap, untap, rotate; single/selected cards; standalone Untap all. | A-008 | D-251 tap-run boundary; per-card bulk nodes use the same T category. Tracking attaches backward. |
| Same-area layout | Battlefield repositioning; ordinary-pile join/split/reorder; Hand arrangement; same-container reorder. Combat layout is listed separately below. | A-005, A-007, A-014, A-025, A-033 | D-163: actual-card moves group by ordered tuple, including Battlefield → Battlefield. Interleaving and mixed tuples remain open. |
| Actual-card transfer | Hand → Stack, Stack → Battlefield/Graveyard, other area/container moves; Draw one/N and top/bottom-N moves; single/selected cards. | A-005, A-006, A-007, A-012, A-014, A-026, A-036 | D-163/D-164: non-Stack moves group by tuple, including interleaved taps; real-card Stack remains isolated. Mixed tuples/extra locations remain open. |
| Combat occurrences/area | Create/close combat; declare with/without tapping; create/reuse projection by drag; role/slot/order changes; Remove from combat. | A-019, A-034 | D-163/D-166/D-175: area creation and projection creation/repositioning/removal group together; closure groups forward. D-176 records individual mutations, not combined invocation nodes. |
| Stack occurrences | Create an ability projection, rearrange occurrences, remove one effect; keep actual-card exits under transfer. | A-035, A-036 | D-163: each Stack projection creation is isolated from moves/other projection creation, but groups with taps/pings. D-175: rearrangements group separately from entries/removals; each explicit effect removal stays separately visible. D-176: bulk removal issues individual mutations. |
| Card text/counters | Counter +/-/Set/remove; corner +/-/Set/Reset; card note and other descriptive-field edits. | A-010, A-018, A-020, A-024 | D-165/D-169/D-171: ordinary card/face/cover edits and token removal group together, not restricted to one card/field. |
| Participant values/associations | Confirmed life/counter tools and D-295 explicit card-owner edit; broader named tracking/association authoring remains excluded. | A-009, A-010, A-011, A-020, A-023 | D-172: in-scope field/value/counter edits join E across cards/players/fields. No inferred damage/owner changes or excluded-authoring inputs; explicit owner editing is E, default owner is part of creation. |
| Shared faces/cover | Turn face down/up; switch printed face. | A-007, A-008, A-018, A-025 | D-169: ordinary manual face/cover edits group with card edits; explicit Stack effects stay separate. General composite authoring is excluded from R1. |
| Inspection and reference displays | Explicit Inspect other side; full contents View; View/Reveal top/bottom N; dismissal. Own-area hold and permitted visible-face navigation are unlogged. | A-006, A-007 | V anchors and F dismissal under the category contract; exposure participation is in history. |
| Creation/removal | Catalog token, Blank token, Clone, explicit token Remove; broader custom pieces only if approved. | A-003, A-013, A-014, A-016, A-021, A-024 | D-171: token removal joins ordinary card edits. D-173: token creation/Clone groups together with interleaved customization. D-174 forbids non-token Remove entirely; it is not an open grouping case. Distinguish token removal from occurrence removal. |
| Persistent links | Create/remove binary links, including selected-source fan-out and occurrence endpoints. | A-017, A-019 | D-172: link creation/removal joins the ordinary edit family, including card/player edits. Links remain persistent connectors, not pings or inferred targets. |
| Pings | Ping/Unping through mode/context; selected-set changes; historical references. | A-015, A-019 | No new marker except author separation; leading/root group and effective recording/reference contexts follow the sequence/annotation contracts. No owner picker. |
| Game chat | The only comment channel; no event-comments UI. | A-028 | Outside ordinary board grouping and gameplay Undo. |
| Tracking and combined shortcuts | Next step, phase/step choice, End turn, Untap/Draw + advance, advance-and-close. | A-008, A-022, A-034 | Tracking attaches backward to the preceding same-author segment, including a pending forward segment. Preserve individual invariant-closed nodes. |
| Random tools | Roll die, Flip coin, Shuffle all; other random selection only if approved. | A-006, A-027 | D-167/D-171: same-target Shuffle groups; dice/coin runs start separately from prior play and attach forward. Stored results/exposure retention are unchanged. |
| Setup/lifecycle and ending | Confirmed lobby preparation/Ready and D-211 undoable result suggestion with separate acceptance/return. | A-001–A-004, A-026, A-029, A-030 | D-257 maps pending ending suggestions to singleton S groups, never the E family or a companion-accepting anchor. Acceptance is outside gameplay grouping. Further lobby/game-history representation remains contract work; excluded setup/custom authoring is not an algorithm input. |
| Outside ordinary board grouping | Personal selection/pan/zoom/search/navigation; game-wide Chat; Undo/explicit activation/archive/restore; group metadata; deck-library operations and export. | A-002, A-007, A-028, A-031, A-032 | Separate local actions, communication and shared controls from board-action input. Decide which change grouping context; do not manufacture board-action nodes for them. |

Explicit corrections use the category of their actual operation, not a catch-all
“correction” heuristic. A move's automatic resets/token disappearance/projection
cleanup remain its recorded effects; they are not extra user invocations to
classify separately unless an approved event mapping says so.

## How to review the rules without an enormous pairwise questionnaire

1. Apply the confirmed rules rather than reopening real-card Stack isolation,
   same-tuple tap interleaving, combat rearrangements, closure direction, Undo
   continuation or the highest-precedence author boundary (D-170).
2. Do not reopen D-169/D-171/D-172's approved edit-family membership,
   backward tracking or forward dice/coin runs. Resolve only genuine remaining
   same-author precedence conflicts with concrete mixed sequences.
3. Finish remaining action categories and first/leading-only/historical contexts
   without introducing an unnecessary ping ownership-selection workflow.
4. Cover the other categories in small related batches. Split a category only
   where a concrete sequence requires different outcomes.
5. Test the resulting rules against mixed sequences and context changes (different
   authors/paths, Undo/replacement play, historical annotations, reconnect and a
   group growing while the other participant reviews). Different authors never
   share a group (D-163); other unapproved separators remain open.

For each approved case, record: incoming action/subcase; matching current-group
contents; append/new-group result; precedence if several conditions match; and
an example with its expected groups. Then add acceptance cases. Unknown or
unfinished action mappings must stay explicit rather than silently falling back
to “one invocation = one group.” Runtime fallback policy still needs design.

We may summarize prior group contents for implementation later, but first specify
the behavior against the actual sequence. No timer, delayed publication, required
Done button or card-rule inference is approved by this inventory.
