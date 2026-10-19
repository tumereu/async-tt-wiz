# Automatic history grouping — action inventory

**Status:** D-162–D-174 confirm the rules identified below. Tentative owner ideas
and assistant recommendations remain explicitly proposed. This is not a final
action schema or an expansion of release scope.

[History section 3.4](history-and-undo.md#34-sensible-grouping) owns the invariants:
immediate publication, effective node boundaries, one-parent Undo and no inferred
game mechanics. [The action catalog](game-actions.md#42-coverage-matrix) lists
capabilities; it is not yet a complete list of fully specified commands/events.

## What the grouping rule needs to consider

The owner's examples rule out one invocation = one group and a rule based only
on the immediately preceding action:

- A sequence of taps may belong with a following substantial action.
- Pings can naturally annotate the preceding action instead.
- Repeated battlefield rearrangements may belong together, without absorbing a
  later Hand → Stack move into the same group.

Design the rules in this form:

> Given incoming action X and current group [a, b, c, …], append X or begin a
> new group. Consider the existing group's contents, not merely its final node.

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

## Confirmed grouping rules (D-163–D-174)

**First rule, higher precedence than every other row (D-170):** a different
action author always starts a new group. No forward/backward companion, matching
category or no-artificial-reset rule may bridge that author boundary.

| Incoming action/context | Confirmed behavior |
| --- | --- |
| Tap actions | Taps group forward with the next action. D-164 also confirms a tap between two same-tuple moves does not split their group: `[Hand → Battlefield A; tap B; Hand → Battlefield C]` is one group. |
| Pings | Same-author pings join the existing group and insert no group-start marker (D-168). If the author changes, D-170 starts a new group even for a ping. No separate ownership-selection step. |
| Actual-card moves outside Stack | Group moves with the same ordered (origin, destination) tuple. Hand, Battlefield, Exile, Graveyard and Library are the listed non-Stack locations. Different directions are different tuples. |
| Same-location card moves | The tuple model includes repeated Battlefield → Battlefield rearrangements; it does not require a separate grouping rule for each card or physical position. |
| Projection creation onto Stack | Never group it with actual-card moves or another projection creation. Each Stack projection can group with taps and pings. Do not group repeated effects merely because they share a source. |
| Real-card Stack moves | D-164 also isolates real-card Stack actions rather than grouping them by move tuple, so individual entries remain separately visible like Stack projections. Taps/pings remain the established companions. No casting/activation label or rules effect is inferred. |
| Ordinary card edits and token removal | D-165/D-169/D-171 group card edits together, including counter/corner/text/note, manual face/cover edits and explicit token removal across cards. More-specific move/tap/Stack rules still apply. Player-explicit spells/abilities stay separately represented on Stack. Token creation/Clone is covered below. |
| Token creation and Clone | D-173: group token creation and Clone together, including interleaved customization edits: `[create token; edit; Clone; edit]`. Keep fresh independent token identities and every node; this is not projection creation. Do not infer a rule for arbitrary mixed categories. |
| Player values/counters and persistent links | D-172: player value/counter edits and persistent-link creation/removal join the same ordinary edit family as card edits and token removal, not separate per-target/per-field families. Example: `[card counter; life change; link creation; card note]`. This does not classify participant-association edits or infer damage/targets. |
| Event comments | D-172: join the preceding group without starting a new one, e.g. `[Stack entry; comment]`. Author changes still start a new group. Comments keep their own retained nodes; they do not merge Stack entries or become game-wide Chat. Historical placement and revision/edit/delete remain open. |
| Combat projection creation/repositioning | Combat projections group together; D-166 extends this to combat repositioning of any kind, including role/slot/order changes. Projection removal and area creation are not silently classified as repositioning. |
| View/Reveal display dismissal and combat closure | D-166: do not attach the dismissal/closure backward to the displayed-area group. Put it with the following action. Group-level review shows the display/combat state, then the next step applies closure plus the following action. Keep the closure node itself inspectable. |
| Shuffle | D-167 groups successive Shuffles of the same target together. Different targets do not match; this is target identity, not simply both being Libraries. |
| Dice/coin tools | D-167/D-171: dice and coin invocations form one random-tool run, separate from prior play and grouped with the following action. Example: `[real card → Stack] | [roll/flip run; following effect]`, not `[real card → Stack; roll] | [effect]`. The effect remains a player-explicit action, not inferred from the result. |
| Phase/turn tracking | D-171: Next step, direct phase/step changes and End turn join the previous group. Author changes still start a new group. Keep constituent nodes and approved combined-action mappings; Draw + advance retains its two real boundaries. |
| Undo, replacement play and resuming | D-168: continue the previous grouping context rather than inserting an artificial group-start marker because of Undo or navigation/resuming. Classify subsequent actions by the ordinary rules at their actual base; do not change retained nodes or merge different paths. |
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

### Remaining interactions to decide

1. **Remaining same-author precedence:** author changes are already settled by
   D-170. Specify how forward companions meet isolated Stack entries or a
   backward tracking edit, rather than allowing both to erase the intended stop.
2. **Other action families:** participant-association edits,
   View/Reveal opening
   and inspection, combat-area creation and combat/Stack occurrence removal still
   need explicit categories. Do not turn “all card edits” into a universal rule
   overriding the separately approved move/projection actions.
3. **No next/previous group:** how to present a leading-only dismissal/random run
   when no following action has arrived, and a first ping when there is no prior
   marker. This must not delay saving/sync or require a fake follow-up action.
4. **Remaining compound/location cases:** mixed-tuple bulk moves, optional extra
   locations and grouped constituents of shortcuts without changing event counts.
5. **Historical additions/manual group edits:** retain the no-new-marker ping/
   Undo behavior and path-local context; finish any optional manual metadata
   semantics without a new ownership picker or ordinary grouping chores.

## Inventory to review

These categories cover the current catalog, including proposed capabilities.
Several catalog rows span categories; several commands use the same literal
operation. A grouping category does not settle an unfinished action's state
changes or event mapping.

| Category | Literal cases to cover | Catalog coverage | Confirmed direction / remaining question |
| --- | --- | --- | --- |
| Orientation | Tap, untap, rotate; single/selected cards; standalone Untap all. | A-008 | D-163/D-164: forward taps; taps between same-tuple moves do not split. Compound Untap/tracking precedence remains open. |
| Same-area layout | Battlefield repositioning; ordinary-pile join/split/reorder; Hand arrangement; same-container reorder. Combat layout is listed separately below. | A-005, A-007, A-014, A-025, A-033 | D-163: actual-card moves group by ordered tuple, including Battlefield → Battlefield. Interleaving and mixed tuples remain open. |
| Actual-card transfer | Hand → Stack, Stack → Battlefield/Graveyard, other area/container moves; Draw one/N and top/bottom-N moves; single/selected cards. | A-005, A-006, A-007, A-012, A-014, A-026, A-036 | D-163/D-164: non-Stack moves group by tuple, including interleaved taps; real-card Stack remains isolated. Mixed tuples/extra locations remain open. |
| Combat occurrences/area | Create/close combat; declare with/without tapping; create/reuse projection by drag; role/slot/order changes; Remove from combat. | A-019, A-034 | D-163/D-166: projection creation/repositioning group; closure groups forward. Area creation/occurrence removal remain open. |
| Stack occurrences | Create an ability projection, rearrange occurrences, remove one effect; keep actual-card exits under transfer. | A-035, A-036 | D-163: each Stack projection creation is isolated from moves/other projection creation, but groups with taps/pings. Layout/removal classification remains open. |
| Card text/counters | Counter +/-/Set/remove; corner +/-/Set/Reset; card note and other descriptive-field edits. | A-010, A-018, A-020, A-024 | D-165/D-169/D-171: ordinary card/face/cover edits and token removal group together, not restricted to one card/field. |
| Participant values/associations | Player counters and named values, named tracking sets, manual participant associations. | A-009, A-010, A-011, A-020, A-023 | D-172: value/counter edits join the ordinary edit family across cards/players/fields. Participant-association edits remain open. No inferred damage or ownership changes. |
| Shared faces/cover | Turn face down/up; switch printed face; explicit visual-composite changes. | A-007, A-008, A-018, A-025 | D-169: ordinary manual face/cover edits group with card edits; explicit spells/abilities stay separate on Stack. See rules evidence above. Broader visual-composite scope remains separately proposed. |
| Inspection and reference displays | Logged covered-face inspection; full contents View; View/Reveal top/bottom N; dismiss a display. Already-visible inspection/navigation is not automatically a recorded exposure. | A-006, A-007 | D-166: dismissal groups forward, not with opening. Opening/inspection categories remain open. |
| Creation/removal | Catalog token, Blank token, Clone, explicit token Remove; broader custom pieces only if approved. | A-003, A-013, A-014, A-016, A-021, A-024 | D-171: token removal joins ordinary card edits. D-173: token creation/Clone groups together with interleaved customization. D-174 forbids non-token Remove entirely; it is not an open grouping case. Distinguish token removal from occurrence removal. |
| Persistent links | Create/remove binary links, including selected-source fan-out and occurrence endpoints. | A-017, A-019 | D-172: link creation/removal joins the ordinary edit family, including card/player edits. Links remain persistent connectors, not pings or inferred targets. |
| Pings | Ping/Unping through mode or context action; selected-set changes; explicit historical association. | A-015, A-019 | D-168/D-170: no ping-start marker or ownership-selection step unless the higher-precedence author change requires a new group. First/root context and numbering remain open. |
| Event comments | Submit an event-linked comment; revision/edit/delete behavior is separately unfinished. | A-028 | D-172: join the preceding group without a new group-start marker, subject to D-170. Comments remain ordinary retained nodes. Historical placement and revision/edit/delete remain open. |
| Tracking and combined shortcuts | Next step, direct phase/step choice, End turn, Untap/Draw + advance, advance-and-close; use existing constituent event mappings. | A-008, A-022, A-034 | D-171: tracking joins the previous group, subject to D-170. Same-author compound precedence remains open. Keep approved nodes. |
| Random tools | Roll die, Flip coin, Shuffle all; other random selection only if approved. | A-006, A-027 | D-167/D-171: same-target Shuffle groups; dice/coin runs start separately from prior play and attach forward. Stored results/exposure retention are unchanged. |
| Setup/lifecycle | Setup configuration/initialization/correction, result/concession, rematch; import/custom authoring remains proposed scope. | A-001–A-004, A-026, A-029, A-030 | Which enter ordinary game history, and which need dedicated setup/lifecycle presentation? First settle the underlying scope where unfinished. |
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
   backward comments/tracking or forward dice/coin runs. Resolve only genuine remaining
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
