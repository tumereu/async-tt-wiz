# 4. Game action catalog

Status: generic engine boundary confirmed; detailed coverage and per-action
schemas proposed. MTG examples are uses of primitives, not recognized mechanics.

**Initial-delivery scope (D-182):** making game actions and shared controls
requires a connection, including new random actions and Undo. Downloaded history
is readable offline, but there is no offline action creation/queue replay. Online
optimistic updates and animation remain. D-187 applies ordinary actions at the
current head unless impossible; optimistic guards are limited to movement and
Stack additions, not previous-value checks for ordinary fields.

Game connection Hello/Welcome also exchange `gameplayRulesVersion`, independently
of engine/read format 1; an absent field means rules version 1. The shared current
rules constant defaults new recorded transitions and the client Hello. A server
refuses older clients with non-retryable `gameplayRulesUpgradeRequired` before any
stream/event delivery; the client persistently shows “Update the app to continue
this game” without reconnecting. A newer client shows “Server update pending”,
submits no commands, and only retries negotiation every 30 seconds. Existing
recorded transition versions and replay semantics remain unchanged.

**R1 scope gate — D-241:** remaining proposed authoring/coordination extras in
this broad catalog are excluded from R1, not approved by being listed. See the
[product boundary](product.md#13-proposed-first-release-boundaries). Preserve
confirmed controls, automatic grouping and current-game history/branch recovery.
Event-comments UI and saved-deck/cancelled-lobby trash/restore
are excluded; Logout cleanup/account isolation follows the account contract.

## 4.1 Shared action envelope

Rules v20 random Hand discard/exile/library-bottom route to the chosen card's
owner's corresponding zone, retaining the chosen identity and destination in the
recorded event. RemoveToken prunes any author-owned contents/reveal tray and closes
it when empty in the same transition, just like moving a displayed card.

Every game action specifies:

- Stable action ID and parent boundary; actor and originating client operation.
- Persistent lobby identity and the specific play incarnation. Requests/recovery,
  Undo/Redo stacks and result references never cross a fresh-start boundary.
- One literal mutation kind and optional user label/note (D-176).
- Typed reversible change, stable referenced object IDs, structural preconditions.
- All changed values or sufficient deterministic data to derive them.
- Random outcome references where applicable.
- A human-readable summary, detailed before/after inspection, and schema version.

This envelope describes the **accepted server record**, not authority for a client
to choose the accepted parent, before/after state or playback group. D-259 separates
the request, predicted result, accepted replica and UI-only state in the
[ownership matrix](domain-model.md#state-ownership-and-request-boundary--d-259).
Personal camera/menu/selection state is not shared action payload, except that
explicitly captured target identities or historical references carry semantic intent.

The same action system supports normal play and state corrections. Convenience
commands are compositions of documented primitives, not a second hidden rules
engine. Players can edit either side of the table.

A completed action applies locally and automatically synchronizes. Multi-field
actions may require choosing objects, values, or destinations; completing those
inputs invokes the action without a second generic confirmation/Send gate.
Incomplete input does not stream malformed or half-selected game actions.

## 4.2 Coverage matrix

**Playtest amendments:** all Stack entry routes are projections (Battlefield,
Hand, Graveyard, Exile; Library blocked), with shared marks and Manage Stack's
Remove/Battlefield/Graveyard/Copy/Controller actions, no reorder. Top-only normal
zone drag removes its projection too. Copies disable Battlefield/Graveyard.
Change owner is token-only; Hand/Library/Graveyard/Exile route to owner, either
battlefield remains allowed. Library inventory is Draw/Reveal/Look/Mill/Exile
1/N…, Search/Shuffle and Mulligan 7/−1; bottom-N/Move-N routes below are retired.
One shared game-wide panel plus one local pile view per viewer replaces old
reference trays and logged Graveyard/Exile browsing. Reveals end on panel exit.
These supersede conflicting matrix/older capability paragraphs below; see
[board controls](board-and-controls.md#106-special-piles-library-graveyard-exile).

| ID | Capability | Required manual behavior |
| --- | --- | --- |
| A-001 | Create game | Choose an added user, name the game (required; procedural fantasy adjective + thing suggestions) and Create & invite atomically (D-294). Retain server creation date/name through retries and later incarnations; display name/title and date beside state. Recipient Accept/Decline; Accept opens same lobby (D-195). Inviter can prepare/Ready while waiting; Cancel removes pending invite, Decline retains lobby (D-208). Reinvite in opponent slot; title-row Cancel. Deck picker highlights a personal draft and confirms at bottom; both Ready starts (D-189/D-190/D-294). |
| A-002 | Card-list import and saved decks | Import-only editing (D-189), common MTG text/printing/sideboard notation, unresolved errors block save (D-198). Name/rename and in-deck cover (D-199). D-293 replaces generic replacement selection: bottom new-import creates a deck; contextual Edit → Import decklist offers Overwrite(default)/additive Merge with section separation and deduplicated atomic save. Art after quantity opens full inspection. Editing/deleting preserves selected lobby/game copies (D-221). No full builder/export or trash/restore (D-241). Lobby preparation is separate and uses one/all drag handles. |
| A-003 | Instantiate objects and lobby preparation | Initial lobby: mainboard → Library, sideboard → separate Sideboard; other areas empty (D-202). Freely transfer without legality/size/one-for-one restrictions (D-203); own Ready clears, saved decks unchanged (D-191). D-294 compact 1/∞ taps move directly to the other section and also support drag, with no double dispatch. Fixed latest-move text fades without layout shift; emptied rows quickly fade and following rows slide up, without delaying requests. Start uses prepared mainboard; Sideboard unavailable during play. |
| A-004 | Setup edits and mulligan shortcuts | Lobby starter selection offers either player or Random (D-201); changing it clears only the changing player's Ready, preserving the other's (D-204). Start shuffles Libraries and initializes life 20 without a required starting-life editor (D-200). Further initial setup remains proposed where not confirmed. No automatic draw (D-192). D-193/D-194 add return-whole-hand → shuffle → draw N (default 7) or pre-return hand size minus one, via Library context and turn-1 Untap quick actions. Sequencing/count edges remain open; ordinary in-game life/move/draw/shuffle controls remain. |
| A-005 | Move cards | Move one/many objects between containers/areas; choose insertion order only for ordered destinations, not Graveyard/Exile (D-226). No rule-dependent side effects. |
| A-006 | Ordered-pile operations | R1: approved Library top/bottom/N moves, Draw, View/Reveal, contents browsing/search and Shuffle all. No generic random-choice tool or new reorder editor is authorized by this row. D-48 excludes top/bottom shuffle shortcuts; D-226 excludes all Graveyard/Exile order controls. |
| A-007 | Row/fan operations | View, arrange, cover/uncover, select, and move cards; labels such as Hand do not alter semantics. |
| A-008 | Orientation/faces | Tap/untap presentation, rotate, choose face, cover/uncover, and edit labels. D-100/D-110 make Untap all literal for the turn player's battlefield, with no skip flag/inferred exclusions; manually re-tap exceptions. Both standalone/+advance variants preserve unrelated fields. |
| A-009 | Participant association / card owner | D-295 explicitly adds the narrow real-card/token owner field and Change owner action: creator/bringer default, either participant may edit, movement/reset preserves owner, live projections derive source owner. This supersedes D-241's exclusion only for this field, not a controller/arbitrary-association editor. Captured-scope per-card edits use normal server ordering, eligible field Undo/Redo and no old-owner guard; see board owner contract. |
| A-010 | Counters | Colored text-bearing counters on cards/participants; per-color +1/-1/Set, no Add/Initialize. D-58–D-68 fix palette/uniqueness, zero/empty/trim, inline retained menus, applicability skipping, and absent +/- creation as `1`/`-1`. No recolor; exact hit regions/formatting remain open. |
| A-011 | Numerical values | R1: confirmed life and colored player-counter tools. Arbitrary named-value authoring is excluded (D-241); no inferred mana/energy mechanics. |
| A-012 | Labeled-area movement | Move cards to/from areas such as Stack (renamed from Cast, D-113); record actual origin/destination, not inferred casting/payment/resolution. D-112–D-114 separately add explicitly chosen projections and contextual Stack actions. |
| A-013 | Labeled pieces | Excluded from R1: arbitrary non-card piece/marker editor (D-241). Confirmed catalog/Blank tokens and Clone remain A-016, not a generic authoring tool. |
| A-014 | Pile order/edit | R1: approved ordinary-pile drags and ordered-location moves/reorders only. Clone uses A-016; Remove is token-only. No unrestricted any-pile reorder/duplicate tool, Graveyard/Exile order control or inferred resolution. |
| A-015 | Group/event-scoped pings | D-74–D-80 confirm event-scoped mode/toggles, retained review, selection-preserving switching, consumed ordinary taps, and one standard marker. Context Ping/Unping uses the menu-trigger card's state and applies uniformly to its selected-set scope without mode changes. Location matching, routing/menu lifetime, and revisions remain open. |
| A-016 | Tokens/copies | D-81–D-86 confirm single automatically placed catalog/Blank tokens, Clone copying confirmed source fields without links/pings, and token removal. No quantity/preview/Repeat shortcut or dedicated custom-token form. Broader authoring is excluded (D-241); placement/initialization/record encoding remain contract/technical work. |
| A-017 | Generic visual links | D-70–D-73 confirm binary undirected unique pairs, context picking, fan-out/removal, and independent movement. Repeated Link preserves pairs; no labels/styles or separate initial Attach/follow tool. Joint movement uses explicit multi-selection. |
| A-018 | Descriptive attributes | R1 confirmed card corner text/pattern shortcuts, printed face/cover and multiline notes with short on-card text/full inspection/empty removal. Generic named-attribute authoring is excluded (D-241); never interpret text as mechanics. |
| A-019 | Board markers/connectors | D-119 adds event-wide ping order numbers. D-120 supports links between projections that disappear with endpoints. D-112/D-118 separately require explicit assigned/unassigned blocker projections and adaptive combat spacing; no inferred legality/damage from pings/links. |
| A-020 | Manual value changes | Edit one/many explicitly selected values; no damage calculation or automatic distribution. |
| A-021 | Manual object changes | D-84/D-85 remove tokens moved into configured Library/Graveyard/Exile across all move controls; mixed sets move ordinary cards normally. D-174 restricts context Remove to tokens: non-token cards never disappear during play and have no Remove/delete action. Token Remove has no pile move/routine confirmation, with automatic sync and undo/history. No other inferred token expiry or rules effects. Exact mixed-selection routing/references/boundaries remain open. |
| A-022 | Turn/phase/step tracking | D-96–D-103 require MTG tracking/shortcuts and turn context. D-106/D-108 add End turn/no number editor and starter/turn-1 setup. D-298 supersedes D-107 placement: tap Next step/hold End turn share one quick slot, hold consumes tap; explicit picker/Table actions/accessibility End turn remains. Icon/color phase indicator and phase-row picker preserve existing tracking semantics. D-105 removes priority; D-109 leaves extra turns manual/approximately counted. D-111 advances combined shortcuts despite no untap changes or empty Library, with no inferred loss. History/undo/branches/online-only sync apply (D-182); arbitrary markers remain separate. |
| A-023 | Named tracking sets | Excluded from R1: arbitrary named tracker/set authoring (D-241). Ordinary confirmed card/player text/counters remain. |
| A-024 | Custom status | R1 card notes/corner text/counters only. Custom icon/piece/named-tracker authoring is excluded (D-241); no inferred mechanics. |
| A-025 | Faces and visual composites | R1: printed face/cover controls and ordinary card placement. General composite/layout authoring is excluded (D-241); no inferred meld/merge/transform. |
| A-026 | Additional containers | Excluded from R1: extra-container authoring/access beyond confirmed areas (D-241). Sideboard is lobby-only, never reachable during play (D-202). |
| A-027 | Randomness | D-87/D-92/D-93 require fresh samples for new die/coin/Shuffle invocations and stored results/orders for review/Redo/retry. D-261 permits request-bound local die/coin samples reused by shared processing. D-274 selects fresh saved client seeds for in-game Shuffle and shared versioned Fisher–Yates/unbiased sampling over canonical actual member IDs; server records the concrete order, not a second roll. Server acquires independent fresh setup-shuffle inputs and resolves Random starter once at actual start. Authority/recovery stays server-owned; no offline invocation, gameplay seed epoch, commit–reveal or new random controls. Exact generator/seed/ID encoding/version registry remain technical design. |
| A-028 | Communication | Game chat is the only comment channel, with no event-comments UI or Event details dialog. D-94 game-wide Chat is not undoable; D-240 keeps chat simple with no sent-message Edit/Delete or planned expansions. Corrections use another message. Unsent-chat handling needs contract completion; card notes/pings stay distinct. |
| A-029 | Correction | R1 uses existing supported field/move/token/Undo controls. No separate general correction/authoring tool or mandatory preview (D-241). Non-token deletion is forbidden (D-174). |
| A-030 | Suggest ending / accept | Suggest your win/loss or a draw as shared, undoable board state. The other player must accept, then both return to lobby (D-211 superseding D-210). Acceptance is not undoable (D-212), unlike pending suggestions. Restore last lobby-prepared decks/mainboard/sideboard, clear both Ready and require both to ready again for a fresh game (D-213). No win-history/scorekeeping feature is required. Finished-session action-history review is optional future work, not R1 (D-214); current-game history is unchanged. Concurrent incompatible result actions use ordinary server ordering/applicability: the first applicable action takes effect and the later incompatible request is discarded (D-239), not replacement or multiple pending suggestions. Cross-game starter-option retention remains separate; D-274 resolves Random once at actual start when selected. |

Round 18: a pending live suggestion locks gameplay on both clients, including during
history review; chat and personal history navigation (including the timeline toggle)
remain available. A compact on-table outcome panel follows the displayed board,
including catch-up and timeline scrubbing; it is not a modal or minimizing banner.
Its buttons work only at the live endpoint. The author Withdraws with an ordinary
event, not Undo; the opponent Accepts or Declines.
Rules v22 adds exact-ID DeclineResult (opponent-only) and WithdrawResult
(suggester-only) events clearing the pending shared suggestion, even after other
edits; this does not add a server-side gameplay freeze. Their recorded result
tuples participate in ordinary replay/Undo. Older recorded events are unchanged.

Card-menu defaults: Everyday omits EOT note; Counters places Note, EOT note and
Reset counters in column five beside three pairs of counter widgets. Reset counters
is one atomic batch of existing ClearCardCounter actions, preserving P/T and other
marks. More has three fixed rows (owner/other side/links/EOT note; four single-axis
P/T adjustments/reset; four EOT adjustments/empty). Extras use More 2 onward;
saved layouts remain supported, including actions intentionally repeated across pages.
| A-031 | History controls | Latest-own removal and last-undone-first recorded-transition reinsertion edit the existing tree, not an original-path copy. The authoritative owner rule (2026-10-25) allows Undo/Redo iff every later game-tree event can apply with identical concerned-field before/after tuples, including reads/dependencies; information gained or seen never decides eligibility. No skipping blocked targets. Ordinary own successful play clears own Redo; ordinary opponent play does not. D-258 deliberate shared-history switches clear both players' queues atomically; normal Undo/Redo and personal UI review do not trigger a blanket reset. D-259 makes controls/stacks server-owned request-driven game state, not per-device history. Validate all retained successors, including beyond activated internal nodes; personal viewing and forks do not independently restrict eligibility. Resource/anchor/version mechanics remain technical. Graphical activation/filtering applies to genuine alternatives. |
| A-032 | Export | Not an R1 deliverable: deck export is excluded (D-241); finished-game history archives/review are deferred optional work (D-214). No export roadmap is implied. |
| A-033 | Table layout | Drag and snap individual cards or selected sets, form/split/reorder visual piles, and recover previous placements. |
| A-034 | Combat projections/grouping | D-116 separates area creation/declaration/tap; D-117 adds multiselect combinations/auto-area. D-137/D-139 refine earlier assignment wording to visual paired-slot groups with no individual attacker↔blocker edges. Creation preserves source placement; D-123 routes later card edits/moves to the real card. D-122 removes combat projections/links without changing originals. Exact reference/combined-event handling remains open. |
| A-035 | Stack projections | D-113/D-242 support ability projections from Battlefield, Graveyard and Exile without moving originals. D-134 allows unlimited independent occurrences, including several from one source, each removed/resolved separately. Existing source fields stay live-linked across locations with write-through edits (D-123/D-242); only token disappearance freezes read-only last-live source information, not a replacement card/Clone. Source-card actions cannot edit the frozen copy or nonexistent token. Preserve normal Stack-addition guards; exact entry/order/record mappings need completion. |
| A-036 | Contextual Stack actions | D-114 requires instant/sorcery card → Graveyard, permanent card → Battlefield, projection → remove projection. Exact category/face/default handling, destination routing, and controls need case-by-case design; no automatic resolution. |

D-296 extends A-010/A-011/A-020 with life-tap context controls and release-only
relative delta-N drags on life/player-counter affordances. Preview/cancel is local;
one completed nonzero drag is one ordinary field mutation, not N step requests.
Current numeric server state governs arithmetic; existing menu controls, absent/
nonnumeric handling, grouping, Undo and request recovery apply. See the primary
player-value section in board controls.

**Player status (rules v26).** Each player has an optional short status
(`PlayPlayer.status`, empty omitted on the wire): trimmed, at most 24 characters,
one line without control characters. `SetStatus(text)` sets the ACTOR's own status
(empty clears it); it is an ordinary recorded event with history text ("Status →
Wait" / "Status cleared"), Undo/Redo under the general rule (its concerned tuple is
that player's status), normal game activity for the opponent, and it never changes
attention or turn. Every new play (including later plays of the same game) starts
both players with the opening status "Mulliganing" (exact text). While the viewer's
status is exactly that, their quick actions are Mull N, Mull −1 and Ready (clears the
status); the starting player's turn-1 shortcuts stay hidden while either player still
has it. A bubble by each life square shows the status; tapping your own life total
offers presets (Wait, Ready, Thinking…, Got a response, No responses, BRB, GG), free
text and Clear; the opponent's panel shows their full status read-only.

**Graveyard/Exile arrival order (rules v27).** Graveyard and Exile remember the
order cards arrived in: the newest card is the top of the pile (shown on the table)
and when it leaves the next card down shows. Cards moved as one action land in the
order they are moved, so the last one ends on top (milling 3 puts the third milled
card on top; a Look/Reveal rest goes away in panel order). Cards already in a pile
keep their order (piles from older rules stay as they were); moving a card within
its own pile keeps its place. Pile order is a concerned tuple like Hand/Library order
for Undo. There are still no order controls (D-226), and the pile's inspect panel
keeps its type sections with cards alphabetical within each type. Events recorded
under older rules replay unchanged.

**Siege battles as attack targets (rules v28).** Every printed battle is a Siege:
its caster controls it and an opponent protects it, so the caster attacks it. A
combat pair may target a face-up Planeswalker on the defending player's battlefield
or a face-up Battle on the attacking (turn) player's own battlefield; a Battle on
the defender's battlefield and any face-down card are not targets. Types are read
from the type part of the type line (before "—"), so a "Battlemage" subtype is not a
Battle. The combat area shows lanes in the order player, defender's planeswalkers,
attacker's battles (each in battlefield order), and Attack → lists the same order.
Combat damage to a battle target still lowers its corner (defense). Events recorded
under older rules keep the old check (Planeswalker or Battle text on the defender's
battlefield) for replay and Undo.

**Declare blockers is a history boundary (rules v29).** A step change (Next step
or a chosen step) whose result is Declare blockers is its own history group: it
never joins the previous group and nothing joins it. The timeline therefore always
stops there, and the defender can continue from Declare blockers even when the
turn player moved straight on (for example "Assume no blocks", which records
Declare blockers and Combat damage as two separate events). Events recorded under
older rules group exactly as before.

D-121 applies across the action catalog: targeted combined shortcuts and their
constituent actions must also be reachable through a full action path. Quick
visibility by phase/step or selection mode is not exclusive action access;
structural/applicability validation remains. Exact menu paths are stage 3.

D-298's [quick-action catalog and icon legends](../02-experience/ui/review.md#quick-action-catalog-and-icon-legends)
map all currently specified phase/selection/explicit-Stack-target situations,
their icons, captured scope and full-action alternatives. The four-position
layout is a UI trial (three contextual positions plus tap-Next/hold-End), not a
new cap or new inferred phase action. **Table actions** explicitly contains the
full table-level tools; Library/card/player tools keep their existing direct
routes. The graphical tree UI separates personal review, arming continuation,
personal review and explicit continuation under the primary history rules.
Branch archiving/restore and Make current are removed (r6b).

D-123 applies normal card actions through projections to the real source card,
not a local copy. This includes moves into Graveyard/other destinations with
normal resets and token-removal exceptions. Projection/area lifecycle removal is
distinct from token-source Remove; non-token cards have no Remove action (D-174).
Exact token/occurrence entry remains under review. D-124 removes
combat projections when the real card leaves the battlefield, regardless of
entry point. D-125 retains Stack effects until explicit removal/resolution,
including when their source moves or a token source ceases to exist. A real-card move through a
Stack projection does not itself remove that effect. D-126 keeps surviving
blockers in their existing slot when an attacker disappears; D-139 retains the
remaining group, with a blocker-only pair when its final attacker leaves;
D-138 permits visual reflow while preserving surviving slot membership/order,
replacing the earlier literal fixed-position requirement.
Missing-source Stack presentation/actions remain under review in the primary
projection contract; no automatic effect execution is introduced.

D-127 adds advance out of combat + close combat area as an explicit combined
shortcut. D-253 permits it to be quick-only, with standalone advance/close available.
Ordinary Next step, direct phase changes, and End turn leave the area open.
Closure uses D-122 without source edits or Stack-effect cleanup. D-128 offers the
quick action only at End of combat and normally advances to Postcombat main.
Publish tracking then invariant-closed closure; absent area makes closure a no-op.
Individual full paths, not the combination, remain available at other steps.
Ordinary Next step at End of combat does not close the area.

D-133: repeated declaration reuses existing attacker projections rather than
duplicating them; add only new attackers from the selected sources. Declare + tap
still taps its selected sources, while without-tapping preserves orientation.
Other mixed/empty-source cases and no-change records remain under review. D-135
limits each source to one projection per combat area across attacker/blocker
roles: dragging an attacker to a blocker position moves the existing occurrence,
not a copy or the real source. D-136/D-139 keep the other cards in their slots,
grouped with any remaining attackers, without source edits. Other entry/role-change cases remain open.
D-137 requires paired attacker/blocker slots supporting several projections on
each side and visual resizing as cards are added/removed. D-139 makes each pair
one visual group without individual attacker↔blocker assignments; players can use
placement/pings for finer details. D-138 removes a pair immediately only when both sides
are empty, closing the gap; between-pair drops insert a new pair with the moved
cards, retaining other pairs' contents/order. Fully empty origins disappear too;
an area with no pairs stays open. D-142 settles uniform target-slot routing for
mixed-role selections rather than preserving prior roles.
D-140 confirms real battlefield-card drag entry into either combat side: create
the source's projection there or retarget its existing occurrence, preserving
identity and the original's position/location/orientation. This drag does not
tap/untap or move the actual source into combat. D-141 sends all selected real
battlefield cards to the same target slot and side, creating/reusing per source,
not one pair each. D-142 extends this to combat projections and mixed real/combat
projection selections: all go into the chosen slot side-by-side with its role,
never split by earlier roles or overlapped into an ordinary pile. Source state
and occurrence uniqueness remain unchanged. D-143 inserts on between-card drops
inside a slot and appends on unused-space drops, preserving other cards' relative
order. Mixed-view ordering, other-view applicability, and absent-area entry remain open;
repeated declaration still follows D-133 rather than drag retargeting.
D-144 adds Remove from combat as a context action for one or several selected
combat projections, leaving real cards/Stack effects unchanged. Remove incident
links and fully empty pairs normally, preserving remaining grouping/order and the
open area. D-176 issues individual occurrence-removal mutations; each has its
own selective-Undo eligibility and retained slot/link/cleanup context;
token-source Remove and real-source/mixed-selection access remain separate cases;
no non-token source deletion is available (D-174).
D-152 handles declarations of existing blockers: move each existing occurrence
into its own new attacker pair, preserving identity/links rather than duplicating
it. Apply the chosen tapping variant, retain other origin grouping, and remove
only fully empty origins. Existing attackers remain in their slots; each variant
uses individual mutation nodes under D-176, superseding the older combined mapping.
D-153 appends new declaration pairs after surviving existing pairs. D-154 keeps
the pre-drag order of cards moved together from one combat slot, inserting/
appending that sequence under D-143, including same-slot reorders. Preserve
other cards' order, occurrence identity, source state, and existing Undo scope;
D-156 orders cross-slot combat drags by selected blockers left-to-right, then
selected attackers left-to-right, retaining each slot's order. Insert/append the
sequence together; all take the destination role. D-157 orders real battlefield
cards dragged together into combat by pre-drag visual order, top-to-bottom then
left-to-right, not selection-tap order. Create/reuse occurrences in that sequence
without changing originals. Mixed-view, multiple-new-pair, and irregular/
overlapping battlefield layout ordering remain separate cases.
D-158 clarifies the baseline: combat is a separate area between the play areas.
Battlefield drags create/reuse projections where dropped, leaving originals in
place. Declaration quick actions put new attackers to the right of existing
combat cards/pairs; existing attackers stay put. Internal collection-order
defaults never override the drop target. No projections-first mixed-drag rule
was approved; that proposal is withdrawn.
D-145 gives each newly declared attacker its own pair for declaration shortcuts,
keeping existing attackers' slots/grouping/order. Collection drags still share
one target slot (D-142). D-146 auto-creates the combat area for single-card
declaration if absent, without implicit tapping or source movement; reuse an
existing area. D-147 shows both multiselect declaration quick actions only during
Declare attackers; full context-action access remains at other steps (D-121).
Order among multiple new pairs, absent-area drag targeting, and additional single-card quick
offerings remain open; do not infer legality or editing locks from quick visibility.
D-176 supersedes D-148/D-149/D-150's combined-node mappings: area creation,
individual occurrence changes, individual source taps/untaps and tracking edits
are distinct mutations, even from one shortcut. Each mutation immediately extends
or branches the selected path and publishes; Undo checks one latest-own mutation.
Draw + advance retains separate actual move and tracking mutations; an empty
Library contributes no draw mutation. No extra invocation tap/Send is needed.
Empty/no-change sources still advance under D-111 without fabricated card changes.
See history sections 3.11–3.12 for mutation sequences and grouped playback.
D-134 separately allows unlimited independent Stack
projections, including repeated effects from one source; removing one leaves the
others and the source unchanged.

D-129 distinguishes invocations, tree events and presentation groups. Current
D-224/D-231/D-232 Undo removes one eligible latest-own mutation; composites may
take several presses. D-176 fixes individual mutation nodes rather
than D-131's MTG-reaction-window criterion; no runtime timing/legality enforcement
or extra invocation taps are introduced.
Presentation grouping cannot change Undo stepping. D-176 supersedes D-132's
combined advance-and-close node: its distinct mutations remain individually
recorded. Specify their consistent sequence without choosing atomic bulk nodes.

D-53 clarifies A-018's corner-text editing: store one plain string and offer
quick actions according to patterns matched in its current text. Actions rewrite
the string rather than editing a P/T or loyalty entity. Numeric counter text uses
the same text-first principle. D-62 confirms whole-number +/-1, corner
slash-pair left/right/both +/-1, and Set-only for other text (including decimals
and symbolic/mixed pairs). Exact matching/format preservation and limits remain
open; negative values are not zero-clamped.

D-63 confirms blank corner text as a valid explicit value and Reset corner text
as a separate action restoring the database default without changing counters
or other state. Corner +/- and completed Set retain/return to the context menu.
Exact Reset menu lifetime remains open.

D-59/D-249 apply edge trimming to completed card-text/counter edits before validation,
empty-value handling, and pattern matching. Preserve internal spaces/newlines.
For counters, whitespace-only text therefore removes the counter while ` 0 `
becomes visible `0`; do not extend deletion-on-empty to unrelated fields.
This is not an account, credential, deck-import or message normalization rule.

Pings, actual object edits, and ephemeral presence remain distinct; see
[the board contract](board-and-controls.md) and
[literal presentation](visual-action-language.md). Stack replaces the former
Cast label. Grouping a move there with pings does not classify it as casting.
Explicit projection/assignment/quick-action capabilities (A-034–A-036) are now
required; their [primary contract](combat-stack-and-projections.md) must be
specified case by case rather than excluded by the earlier generic-only wording.

Confirmed touch movement for A-005/A-006/A-033 is specified in
[board controls, section 10.4](board-and-controls.md#104-dragging-and-snapping):
single-card drag unless multiple cards are selected, top insertion on Library
drops, unordered membership additions for Graveyard/Exile, ordinary-card drops
forming/joining visual stacks, and Context Move to Library top/bottom, Graveyard,
or Exile. D-226 excludes order-related Graveyard/Exile actions, including visual
placement variants. Multi-card order and the
remaining bulk-action details below are not yet approved; D-49 separately fixes
relative order for multi-card moves from one ordered source.

Library UI decisions D-41–D-43 add combined contents/actions on pile tap,
order-independent full-library browsing, and explicit View top N / Reveal top N
commands with context actions on revealed cards. D-46 confirms the temporary
shared reference-display model, explicit-dismissal lifetime, and Draw N shortcut in
[special-pile controls](board-and-controls.md#106-special-piles-library-graveyard-exile).
Draw N's user-selected preset label denotes literal top-N
movement to a configured Hand destination, not an inferred game mechanic.

D-44/D-45 clarify that View and Reveal are both shared logged operations, not
local-only inspection: View presents faces to its actor and backs to the other
participant; Reveal presents faces to both. Top-N displays preserve source
order, unlike the orderless full-library browser. These are literal display
operations, not inferred game mechanics; exact history/display schemas remain
open in Q-28.

D-48 confirms quick Draw one, View/Reveal bottom N, and Move top/bottom N to an
explicit destination. The library offers Shuffle all only: no Shuffle top N or
Shuffle bottom N shortcuts. D-49 confirms top-to-bottom bottom-N displays, an
N-picker starting at one with +/- and numeric entry, clamping to available count,
and source-order preservation for moves from one ordered source. Empty sources,
cross-source order and precise validation remain open. D-176 fixes individual
card-mutation nodes for bulk moves.

D-50/D-226 support direct top-card dragging from Library and specific-visible-card
dragging from unordered Graveyard/Exile. Contents browsers have type-category
columns, name search/filtering, and hold inspection. Column
grouping is display-only and does not replace explicit ordered top/bottom-N
displays. D-51 approves initial one-category-per-card, alphabetical-column, and
narrow-screen scroll defaults as a revisable starting point. Exact category
mapping and covered opponent layout remain open.

These IDs retain coverage references from the earlier draft; game-specific
operation proposals have been replaced by generic capabilities. Worked MTG
examples must demonstrate that these primitives are sufficient, not add hidden
mechanic-specific entities to the engine.

## 4.3 Rules that must not be inferred

- No casting, activation, targeting, combat, damage, cost payment, priority,
  resolution, or turn-progression model is inferred from physical actions.
- D-112–D-114 allow explicit combat associations/projection roles and contextual
  Stack choices. These do not infer rule effects from ordinary moves or card
  text. The earlier no-specialized-state language does not exclude them.
- D-96/D-105 separately require explicit engine-level turn/phase/step tracking.
  Do not interpret the no-inference rule as excluding that state. Exact explicit
  remaining transitions and correction behavior remain under review. D-105
  removes priority tracking altogether; players judge interruption timing.
- A ping only creates a visual communication record; it never creates a legal
  target or another rule-specific relationship.
- Moving normally preserves orientation, counters, corner text, labels,
  participant associations, and generic links. D-55/D-56 add declared counter
  removal and restoration of database-default corner text on entry to configured
  Graveyard/Exile/Library. For creatures the default is printed P/T as a string;
  D-64/D-65 settle conditional other defaults, blank fallback, and retained
  per-face defaults. Exact source/custom mapping remains open. Unrelated fields and
  identity persist for retained objects. D-84/D-85 separately remove tokens moved
  into those destinations, retaining historical identity/fields for recovery.
- Removing a piece, clearing a value, or rotating selected cards is always an
  explicitly selected generic edit, not an automatic consequence of card text.
- Game-specific card types, “token” labels, or area names do not trigger expiry,
  destruction, cleanup, or validation. D-84 is an explicit exception using
  instance token status and configured destinations, not labels or printed type.
- Any later convenience composition must name all generic changes explicitly.
   User-defined labels cannot make it an inferred engine mechanic. Whether such
   saved compositions are first-release scope remains open.

D-55/D-56 specify an approved destination-entry move/reset convenience, not
blanket approval of user-authored compositions or rules-driven cleanup. Record
its actual counter/text changes for review and recovery, not just a move label.

## 4.4 Hidden information conventions

Hands, library order, and face-down cards are covered by default in ordinary
table presentation. Either participant can explicitly inspect them. Show a clear
trust-model explanation during setup and on first hidden-zone inspection.

A card whose face the viewer cannot see shows no values anywhere (P/T, EOT P/T,
damage, notes, counters; menus show "—") and offers no value edits: its stored
values may be printed defaults. Exception (rules v23): a face-down Battlefield card
(morph/manifest/disguise/cloak) has a PUBLIC 2/2 corner, and its P/T, counters,
damage and notes are visible to and editable by both players. Turning it face up
resets the corner to the shown face's printed default; other marks stay.

Face-down casting (rules v24): Hand menu "Face down" moves the selected own hand
cards themselves (not projections, which would reveal a hand source) onto the
Stack top face down, one batch (`MoveCard` with `faceDown = true`). A face-down
card moving Stack -> Battlefield without an explicit `faceDown` stays face down
and gets the public 2/2 (every resolve path: bubbles, Manage Stack, drag); other
destinations from the Stack turn it face up as before. Library menu "Manifest top"
moves the top card onto its owner's Battlefield main row face down (2/2) in one
move. No step discloses the face. Retained v23 events replay as recorded.
Being always a 2/2 permanent, a face-down cast then auto-resolves to the owner's
non-land Battlefield row exactly like a permanent hand double-tap: a separate
history group (the opponent's playback pauses at the Stack entry) in the same
batch/invocation, so one Undo removes both. An opponent never sees a face-down
card's front (any zone except Hand/Library, not even via Inspect other side); own
side follows D-252 (zone holder, or owner on the Stack), whose inspector marks
the art "Face down".

Temp pile (rules v23): each player has one hidden "Temp" pile (a LIBRARY-zone
container flagged `temp`, never that player's Library) for effects such as "put
your graveyard on the bottom of your library in random order". It is created by
the first move into its deterministic ID, so older games gain it without
migration. Entering cards reset like any card entering a hidden zone. Controls:
Shuffle, Look (logged like a Library look), All to library top/bottom (one batch,
owner-routed), and moving cards out as from a Library top.

**Numbered Temp piles (rules v29).** Each player has Temp piles 1–4, individual
piles with fixed numbers (Temp 3 can exist without Temp 2). Pile 1 keeps the v23
ID and is always on; piles 2–4 have deterministic IDs
`uuidV5(playId, "container/$accountId/TEMP/$n")` and a `tempNumber` on the
container (omitted for pile 1, so older boards and events are unchanged). A pile
2–4 is turned on by `setTempPile(accountId, number, on)` (creates the empty pile)
or by the first move into it; turning it off removes the container and is refused
while it holds cards or something is shown from it. Pile 1 is never turned on or
off. Either player may toggle either player's piles. The pile menu adds All to
Temp N (the whole pile in order onto that pile's top, one batch, creating it if
needed) and on/off buttons for piles 2–4.

Covering a card does not remove its identity from synchronized data. No tooltip,
notification, default log summary, or background preview should unnecessarily
spoil a covered card. Explicit inspection can expose it.

Explicit View/Reveal is shared/logged (D-44). D-252 replaces blanket covered-hold
logging: own-area hold shows real faces without an event; opponent-area hold
does not expose hidden information. Explicit Inspect other side is a logged
inspection, distinct from Flip's shared-state edit. Accepted revealing
draw/move/face changes remain recorded. D-286 reconciles revealing predictions to
the server outcome without separate reports for temporary extra glimpses; missing
inspection context rejects normally, without reconstructing it. Revealing accepted
targets use the same concerned-field Undo/Redo rule; genuine retained alternatives, not Undo-created copies, are filtered by
exposure/author participation. D-160 keeps the whole alternative visible when it
contains exposure, without a last-exposure cutoff. D-222 also
keeps mixed-player continuations visible and limits hiding to single-player/
no-exposure paths. Exact
coalescing, event placement, forward presentation and non-Undo branch controls
remain Q-31.
What a player remembers is not an Undo/Redo eligibility input.

## 4.5 Bulk actions and selections

### Target identity normalization

Bind menu scope under D-248 before resolving targets. Each operation declares its
identity domain; normalize once before emitting individual mutations (R-09):

- Card operations (text/counter/orientation/face edits, moves to real destinations,
  token-source Remove and Clone) resolve live views to their real source IDs and
  deduplicate aliases. Selecting X and two of its effects applies +1 once, moves
  X once, or creates one Clone, not three. Different real card/token IDs remain
  distinct even when their definitions/fields are equal. Clone captures source
  identity, then copies its current accepted fields under the
  [live-input rules](operation-composition.md#bind-identities-and-choices-not-predicted-output-snapshots);
  it cannot clone a frozen snapshot.
- Occurrence operations (effect removal/layout, Remove from combat) address distinct
  occurrence IDs. Two effects from X are two removals, not one source deletion.
  Combat create/reuse resolves by source+area, preserving its one-occurrence rule.
- Ping/link endpoints refer to the explicitly targeted card/occurrence identity,
  not silently its source. Historical references retain their own event/object
  identity and context. Do not transfer an occurrence link to the source.
- A frozen effect has no editable live source: source-card edits/moves/Clone are
  inapplicable through it; occurrence layout/removal and recorded inspection remain
  available. D-67 may skip such individually unusable targets in a mixed scope;
  malformed/unknown IDs are errors, not silently omitted aliases.

Preserve the first occurrence in the already-approved source/spatial sequence
when deduplicating; do not introduce a new selection-order picker. Applicability
is evaluated on normalized targets, then each emitted operation receives its own
server-time checks. Explicit repeated invocations still create independent effects.

D-66 confirms control parity: a mass selection gets the same card-action
controls as an individual card, applying the chosen action across the selected
set rather than a separate reduced bulk menu. Operate on each card's own state
for relative edits/default Reset and supply common text for Set. Normal
unrelated-field guarantees and declared move/reset compositions still apply.

D-67 rejects the prior all-compatible gate: apply the chosen action where it is
usable, skip selected targets where its individual action would be disabled,
and disable the mass control only if none are applicable. This does not authorize
arithmetic on nonnumeric text, loss of selected objects, or ignoring invalid IDs
and structural drop conflicts. D-68 resolves absent counters: +/-1 creates
`1`/`-1`; nonempty Set creates/updates and empty removes or leaves absent.
Exact per-action eligibility/error tables and affected/skipped rendering remain Q-30;
grouping and remaining per-action validation details remain open; D-176 fixes
individual mutation boundaries.

**Confirmed D-176:** a bulk control issues individual operations in a sensible
recorded order. Moving/tapping several cards, applying edits, creating Clones,
or removing occurrences does not create one multi-target atomic node. Each
individual mutation immediately becomes a tree leaf and publishes. All
intermediate mutation boundaries remain available for traversal, Undo and play.
Grouping may animate those changes concurrently as one playback step (D-177),
but never merges their nodes or makes a group the client's state pointer.

Use approved source-order and spatial defaults without another ordering picker.
Record the chosen sequence so forward/backward traversal is deterministic.
The [ordered-lowering algorithm](operation-composition.md#ordered-multi-object-lowering)
defines each source check/destination anchor and intermediate board, including
same-container subsets and top insertion without reversal.
Existing applicability skipping, destination order, and whole-drop structural
validation remain: an overflowing drop can be rejected before creating any
mutations without making a valid bulk move one atomic history operation.

**Whole-drop prevalidation versus later conflicts (R-03):** an initially invalid
selected placement is rejected in full before emitting moves; never take only
the members that fit. A valid invocation is not an atomic server reservation:
opponent actions may interleave, and each move rechecks source/destination/capacity.
Accepted moves remain accepted if a later move fails. Do not compensate, split
the pile, erase interleaved work or cancel the whole queue. Difference-only
reconciliation corrects failed placements while retaining successful ones and
the selected IDs/mode (D-29/D-34); invalid preflight restores pre-drag selection.

Example: pile X receives selected Y/Z. Y commits, opponent W commits, then Z
fails the three-card cap. X/Y/W stay; Z returns to its authoritative source.
If Y/Z both commit first, W fails instead. If Z was moved from its stated source
by the opponent, reject Z's move while retaining Y and the opponent's move.

Quantity-based N-card commands clamp to the available source count (D-49).
Quantity clamping itself does not permit substituting or dropping selected
objects; D-67 separately permits skipping targets on which the chosen action is
inapplicable. Empty candidate lists/sources, duplicate selections, stale objects,
and destination conflicts still require explicit outcomes. The precise
per-action error table, including count feedback and boundary validation, is a
required follow-up specification artifact.

## 4.6 Required per-action specification template

The [limited-locking policy](optimistic-locking.md) records D-187's replacement
for the former 25-rule review: ordinary actions normally apply to current state;
only movement and Stack additions have optimistic conflict guards. Changed old
orientation/text/counter values are not rejection reasons for ordinary edits,
including Set. The former batch's detailed category rules were not approved.

Before implementation, every **R1** capability above must be expanded into:

1. Entry points and affected screens.
2. Inputs, defaults, validation, and confirmation/cancellation behavior.
3. Atomic operations and exact state transition.
4. Ordering, visibility, and exactly which generic fields change or stay intact.
5. Review rendering and valid interruption boundaries.
6. D-232 tree-editing Undo/Redo eligibility/effects, affected historical references,
   and separate explicit-branch/control recovery.
7. Offline behavior, preconditions, concurrency conflicts, and errors.
8. Accessibility interaction and acceptance examples.
9. Tap/action budget from the normal starting context, including selection,
   navigation, text entry, and confirmation. Justify each required interaction;
   prefer direct controls and undo over repeated prompts.
10. State ownership: shared request/accepted event or control result versus UI-only
    interaction, predicted application, accepted-replica revision and recovery data.
    Use D-259's matrix; do not invent local-only variants of semantic game state.

The generic correction tool must not excuse poor direct workflows for moving,
rotating, managing piles/counters, or adding pings. Manual MTG scenarios remain
coverage tests of those workflows, not engine-defined mechanics.
