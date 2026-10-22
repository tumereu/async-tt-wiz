# 14. Session handoff — implementation authorized

Updated 2026-10-22. This is continuation context, not a new source of requirements.
Confirmed decisions are in [the register](decisions.md); detailed contracts
and acceptance scenarios take precedence over this summary.

## Active overnight continuation — read first after compaction

**This conversation is the running implementation supervisor.** The owner has
reaffirmed that if only one thing survives context limits/compaction, it must be
continued five-session execution through the night. Do not turn a compacted
context, routine batch pause or repaired controller exit into a final response.
Read the latest [batch assessment](supervised-batches-2026-10-22.md), inspect actual
Git/ownership/STOP/retained-run state and continue. At an idle clean boundary run
`python3 scripts/implementation-loop.py run --max-runs 5 --timeout-seconds 86400`
as a blocking shell call with `timeout: 0`; assess actual journey progress/drift,
repair/recover safely as needed, and repeat without routine owner approval.
Never start alongside an existing worker/controller. Preserve explicit later owner
pause, exact high model, original total ceiling, scoped grants, safe saves, reviews,
final full-suite/fixes and release gates. Noncritical details belong to the recorded
final/owner review, not an early detour. The latest saved state—not old snapshots
below—selects work. Last completed supervision batch is 8; I09 invitations are
delivered, I10 preparation is next, and 2,931 original-budget sessions remain.

## Latest owner execution direction — D-307/D-308

**Owner resume — D-308:** the owner explicitly resumed after the committed workflow
repair. Run five-session batches, each unit capped at 86,400 seconds, with no shell
tool timeout. At each controller exit inspect actual delivered user journeys and
sidetracking, refocus the next tasks/prompt when needed, and continue automatically
through main delivery including final full-suite testing/fixes. Preserve the original
scope/model/total ceiling/reviews/release gates. Noncritical findings should be
retained for final qualification or later owner review rather than trapping early
agents in detail loops; do not falsify completion or waive required outcomes. See
[current supervision/batch assessments](supervised-batches-2026-10-22.md). This resume
supersedes the paused/no-restart state recorded below. Check actual stop/ownership
state before any launch; recover only a retained, safely reconciled owned run.

The owner approves [delivery-first execution](delivery-first-execution.md): minimize
real elapsed time to deliver the whole agreed software. Run/fix affected tests as
features are built; reserve the full suite and its fixes for final qualification.
No repeated green full-suite/clean-build/emulator fault-matrix runs merely to be
sure or to close every nested parent. Reuse valid evidence, audit missing seams,
prioritize actual user journeys and reject invented theoretical blockers. Keep
independent capability reviews, fresh targeted fix verification, security/data
integrity, truthful results and all release gates.

The prepared branch has been applied at a safe paused boundary; see the
[application/recovery record](delivery-first-application-2026-10-22.md). Newer I01f1b
work is preserved. Pending I01f3 is now final B8 work before I34, not a prerequisite
to account entry. I01f2 retains actual one-device startup/storage checks. Next is
I01f1's short child-evidence reconciliation, I01f2, remaining foundation evidence
closures, then I02 and the real capabilities. The stale active journal was recovered
through the driver; **STOP remains set and no worker was started**. Do not restart
the supervisor without owner resume or repeat global qualification during closures.

Earlier "next task" statements below are historical checkpoints. Read the latest
saved progress record for actual task status, not those historical snapshots.

## Saving edits — D-178

The owner now requires commit and push after every completed edit batch, before
replying, without a separate request. Use `./scripts/save.sh <edited paths...>`
with its fixed message `Update project`; do not spend time crafting messages.
Run applicable checks, inspect staged changes and exclude unrelated work. With
no arguments the helper uses the existing index; with no new changes it still
pushes, allowing a retry. Report failures without force pushing/bypassing checks.
This supersedes D-57's occasional-checkpoint cadence. See [AGENTS.md](../../AGENTS.md).

## Current authorization — D-304

**Active supervision — D-306:** the owner is going to bed and explicitly requests
the planning-helper fix followed by blocking driver runs with 3,000 sessions and
1,209,600 seconds per session, no shell-tool timeout. After every exit, check real
completion; if unfinished, autonomously repair/test/save/push/recover and restart.
Only done, still implementing or quota exhaustion is the intended overnight state,
not a recoverable controller halt. Preserve this assignment across compaction.
Adjust recorded plans/dependencies around forbidden/external operations to finish
all feasible scope without dropping blocked obligations or waiving tests/reviews/
release gates. See [authorization](implementation-authorization.md#supervised-unattended-execution--d-306)
and [planning recovery](loop-recovery-2026-10-21.md). The helper fix has 91 passing
runner regressions; I01c1 is next. No competing driver may be started.

**Current execution clarification — D-305:** the owner explicitly requests best-
effort autonomous completion along the spec, including whole/multiple fresh
troubleshooting sessions and necessary infrastructure/orchestration repairs.
Do not halt at the first build/tool/save failure or abandon a feature because
external credentials, rights, signing/hardware or production access are unavailable.
Implement everything possible within the grants, test locally/synthetically and
retain only truly external qualification as unverified. The
[unit contract](implementation-agent.md#autonomous-troubleshooting-and-orchestration-repairs--d-305)
now supports evidence-backed checkpoints and scoped driver/prompt repairs; grants,
model, save/push integrity, independent reviews and release truthfulness remain.
85 runner and six save regressions pass; this change did not launch a live loop.
I01a's preserved work is revalidated in the
[save recovery](save-recovery-2026-10-21.md); I01b remains next, I01/B1 and release
gates open. Historical paragraphs below describe their original checkpoints.

The owner tried the driver, saw “implementation not authorized,” and explicitly
authorized it. [D-304's durable record](implementation-authorization.md) now backs
`active/authorized` state and stage 4 implementation of the agreed whole R1. Earlier
no-implementation/current-stage-1 instructions in historical sections below are
superseded **for authorization only**; do not ask for that approval again or infer
approval of excluded/unselected behavior. All tests, grouped reviews, fresh fix
verification, D-303 final mock comparison, scoped permissions and release gates stay.

All 36 tasks remain pending at authorization recording; no application session was
launched here. The operator starts the driver with their selected positive budgets.
I01 qualifies actual build/test/device commands and inventories resources; missing
hardware/permissions are honest affected-work blockers, not completed gates. Continue
independent approved work where safe. External release readiness is not authorized
or verified merely by permission to implement.

## Preparation evidence — D-300–D-303

**Latest owner-requested follow-up repairs:**
[IR-08–IR-13](implementation-loop-followup-review-2026-10-21.md) are corrected with
**74 passing deterministic runner tests**: first group-review coverage cannot be
replaced by targeted verification; reviewer/verifier runtime/build changes are
rejected; gate inventory/authority is retained; safe independent children/reviews
and blocked planning work; note churn is rejected; group reviews can be split and
checkpointed with exact code anchors and remaining/blocked scopes retained.
The repair batch added review accounting/test-only boundaries/gate authority while
the application was still draft/not-authorized; D-304 above now authorizes it. See
[current qualification](implementation-runner.md#current-follow-up-repair-qualification--2026-10-21)
for evidence/limits. **Eight fresh live sessions passed on the repaired driver**:
two implementations (last-task defect discovery), fix, verify, review planning,
two bounded review checkpoints and completion. Every role was exact high, idle,
committed and pushed; verification did not skip first review. An independent audit/
fresh fixture-suite repeat passed, and completed restart launched no session. The
304-file private archive/digest is in the operations record. Actual build/device permissions, hardware,
and external release prerequisites remain separate from that authorization.
Owner follow-up D-303 adds a required final B8 interface-to-mock comparison: I36 prepares
the complete current-state/orientation inventory, the independent reviewer compares
all interfaces, and fresh fix/verify units correct and recheck discrepancies before
closure. This supplements I32, not another implementation task or activation.

**Earlier owner-requested readiness repairs:**
[IR-01–IR-07](implementation-loop-review-2026-10-21.md) are corrected. **48 automated
runner tests pass**: bounded subsets no longer charge untouched findings; counters
survive failed verification; role/target transitions prevent self-verification and
unrelated advancement; check outputs must match committed bytes; recovery requires
the owning worktree/branch/state. Planning-only child splits, scoped reopenings,
early critical repairs and independent lanes remain covered. I29 now explicitly
requires actor confirmation, not opponent consent; I19 owns persistent generic
Link/remove-Link/endpoint cleanup independently of pings/source relationships.
See [current qualification](implementation-runner.md#current-review-repair-qualification--2026-10-21)
for the driver digest and limits: the earlier live six-session/timeout cycle was
not rerun for this repair batch. Hardware/build-permission/activation/external
release prerequisites remain pending and explicit. No application code, approval
or state activation was introduced; the original findings are retained as provenance.

The owner wants an outer loop launching a fresh agent for one implement/review/
fix/targeted-verification unit, then validate/commit/push/exit before the next.
Review coherent groups, not every task; focus on observable usability,
responsiveness/performance, crashes, accepted-state/recovery issues and harmful
races in a **casual cooperative simulator**, not style nitpicks, competitive
guarantees or contrived reset/message-order cases. Basic privacy/security and
agreed behavior/tests stay. See [loop contract](implementation-loop.md).

The owner authorized missing test-tool installation and isolated environment
smokes, then asked to wait for the UI agent before drafting tasks. Tests finished;
the UI agent was idle and Git clean at `f3bd2cc65f6ca885dc48daeba4355a2ba9bb00df`.
The [draft plan](implementation-plan.md) therefore uses the completed D-299/F01–F88
packet: **36 task selections / eight capability-review groups**. Treat UI as a
good-enough initial baseline expected to improve in real use, not perfect/frozen
art, approval of extra product behavior or automatic gate/stage advancement.

[Environment qualification](e2e-environment-smoke-2026-10-21.md): local WSL lacks
KVM, but Windows WHPX emulators work through loopback remote ADB/Appium. Kotlin/
JUnit native touch + Docker-network UI checks passed with one, two and three
instances; all three passed again after wipe/cold boot and Compose reset alongside
real Testcontainers/PostgreSQL/JDBC. Scope Java build/test processes to IPv4 on
this host; detach Windows launchers and use Windows-native emulator shutdown.
The initial fixture-browser reset failure/evidence and fixes are retained.
All owned test services/emulators were stopped; tools/evidence remain outside Git.
These are tooling tests, **not normal-product APK/backend/Canvas acceptance**.

D-302 separately authorized building/qualifying the thin runner, with
**`openai/gpt-6.1-sol#high` for every role**, no fallback. The driver is now
[scripts/implementation-loop.py](../../scripts/implementation-loop.py); read
[operations and qualification](implementation-runner.md) and the
[one-unit prompt](implementation-agent.md). It creates fresh sessions, validates
report/state/Git handoffs and actual pushes, and stops on failed/interrupted work
without discarding it. Explicit run-count/wall-time budgets, per-session
permissions, graceful stop and manual recovery are provided. No global OpenCode
configuration was changed; permissions are not an OS sandbox or billing cap.

Earlier qualification: **25 automated tests passed**; six real fresh sessions completed
implement F1 → implement F2 → grouped review B1 → fix R1 → verify R1 → complete,
all pinned high, idle and committed/pushed to the disposable remote. The seeded
arithmetic defect tests orchestration, not blind review quality. Later guard-only
changes passed automated tests/current-code trace audit rather than another full
live cycle. Completed-state restart launched no session. Real eight-second
timeout stopped after 8.43 seconds, outcome interrupted, no active session remained.
The real project execution guard refused to start. Private evidence archive:
`~/.local/share/async-tt-wiz-e2e-tools/runner-20261021/` (232 files; manifest SHA-256
`70562b7553c8bc1c842e30093d94afeab496a6219db9b8fd17aafebf4038acbc`).

At the preparation checkpoint, `implementation-state.json` was draft/not-authorized
and no app code/scaffolding was built. D-304 now provides the missing explicit owner
authorization. The current state/authorization record above governs execution;
tooling smokes alone would not have supplied that approval.

## Completed UI feedback iteration — D-299

Read cumulative F01–F88 before further UI work; full HTML/PDF remains at the exact
port-8443 URL, replaced and verified each batch. Player absent values are **blank**,
zero is 0; card-counter dash examples unchanged. End buttons/combined progression
use tracked ending-player fill. Short action-name tooltips only for unassigned
icon/value button holds; consume tap, no release action/inspection/edit. Existing
Undo/Play/progression/inherited game-row holds and card gestures retain priority.

H06 is compact (320 px portrait/360 px landscape) with pannable group glyphs,
active path/current ring, local selection and one Make current action. Small view
menu replaces a toolbar. Owner delegated R1 representative ranking: primary
history-grouping contract plus checked group-icon-order.json document fixtures.
Anchor beats companion; lowest rank/first-effective tie, actual event icon/author.
Group tap selects exact end, **not representative ID** (Reveal C1 represents the
group ending Comment C2); preserve exact timeline cursors, fork junctions/internal
current and captured activation through group growth/reranking. Graph selection
is local; server activation, Redo/filter/full-tree rules unchanged.

H07 archive/return UI is withdrawn as unnecessary for switching branches, **not
retirement of the older archive product/protocol contract**. Retain captures and
source revision a784890; switching neither archives nor deletes either path.
G13 replaces it with an Inspect tooltip snapshot. **45 primary + four continuations
= 49 states / 98 captures**. Round8 sources/composition remain inert documentation,
no app/cloud work or gate authorization. Primary contracts/summaries and future
T-710–T-715 align. Consult validation.md for actual artifact checks/hosting limits;
native gestures, grouped-fork layout and server behavior are future verification.

## Previous UI feedback iteration — D-298

Read cumulative F01–F82 before further UI work. Full HTML/PDF stays at the exact
existing port-8443 review.html URL, replaced and verified after every batch.
Round 7 centers fitting/pannable hands; reserves four quick positions and documents
every situation/icon/scope/full path. **Latest follow-up: Next step/End turn are
one slot (tap/hold), hold consumes tap; three contextual positions remain.** This
supersedes D-107's separate one-tap End turn and our initial two-slot progression
plan. Explicit picker/Table actions/accessibility End turn stays; no priority/pass.

Combat has only faded vertical dividers, no empty drawn card boxes/+; tapped River
and source/Stack occurrences demonstrate local stable-source relationship emphasis
without selection expansion. Small 28 px player circles retain separate 48 px hit
allocations and span full portrait strip including Stack-side width. Indicator has
exact-step icon/phase color/turn-player border, no phase words; turn number separate.
G11's five-row/twelve-position picker preserves player/number on direct selection;
bottom End opponent's turn changes tracking normally. Background-strength trial
does not fade cards/change permissions. Do not invent a first-strike tracking step.

Card panel has no title/X, two-position details and packed icons; seven P/T squares
are Set,+/+,−/−,•/+,•/−,+/•,−/•. All counters form three paired Set-value-circle,+,−
rows with dividers; player menu keeps title/X. Full legends explain general and
conditional icons. G12 explains Table actions (former Game actions). H06/H07 add
graphical review versus shared activation/continuation and reversible archive/
return/restore; existing history/Redo/filter/full-tree rules remain, no new policy.

**45 primary + four continuations = 49 states / 98 captures.** Round7 shared inert
sources/capture composition/local-font document legends are documentation, not
native handlers/cloud edits or implementation/gate authorization. Primary contracts,
summaries and future T-703–T-709 align; T-202/T-204/T-702 correct superseded layouts.
Validate, publish full edition in place, commit/push before replying as usual.

## Previous UI feedback iteration — D-297

Read cumulative F01–F68 before UI edits; keep the full HTML/PDF at the same exact
port-8443 review.html URL, replace in place and verify. No app/cloud implementation
or gate/stage change. Round 6 adds login logo, small subordinate search-results
label, both players' colored readiness (green check Ready), compact pannable hands,
landscape own Hand in quick-action row and proposed covered opponent Hand in the
existing top row. Neither hand takes battlefield space. Combat pairs fit their
wider side plus tiny margins; one-card insertion targets before/between/after
active groups (one open/empty) are UI affordances, not retained empty pairs.

Player counters are circles spread over full row, prior visibility rules intact.
Timeline square stays at exact viewport center even at endpoints with unused half
empty; H04/H05 show this. Chat has direct button/unread bubble and G09 panel, local
viewing only, no receipts/presence. G10 shows 24-card pannable hands. Card actions
are grouped square icons with direct +/−/Set and inline owner choices, no category
submenus. Latest follow-up accepts dialog instead of initial floating-only request;
persistent title-corner current-state preview remains visible while actions scroll.
G06 now shows updated retained dialog (owner riley, 2/4, blue 3), not a chooser.
Outside/X/native Back dismiss; existing repeated-edit/Graveyard lifetimes persist.

**41 primary + four continuations = 45 states / 90 captures.** Shared inert sources
use cumulative round6 CSS; doc capture composes snapshots/scroll/center offsets,
not application handlers. Primary account/board/combat/history/model summaries and
future T-696–T-702 align. Preserve earlier provenance/copy suggestions; validate,
publish full artifact in place, save and push every completed batch.

## Previous UI feedback iteration — D-296

Read cumulative F01–F57 before UI edits. Full HTML/PDF stays at the exact port-8443
review.html URL, replaced in place and verified after UI batches. No app/cloud
implementation or stage/gate change. Round 5 merges search/results in one card;
landscape Stack is between side player info; every table card now uses one zoom
(40 px in review examples, not a native minimum), no per-area shrinking. Top-left
circular total badges unify special piles. Timeline icons keep author color and
faint effective-group dividers. Counter rows sit below zones; portrait per-player
rows hide only for all empty/zero, retaining menu/zero access. Tap life opens
life/color -/+ /Set. Drag up/down previews signed delta locally, valid release
sends one relative request; no per-frame writes, stale Set or tap double-dispatch.

History Play/Pause has text, fills portrait space and uses attached landscape
Previous/Next. Hold Play while paused equals retained top/accessibility continue,
without tooltip/tutorial or Continuing from banner. Armed UI looks live at the
selected state; intent remains explicit and first successful mutation alone forks.
G07/G08/G08b add player menu and positive/negative previews: **37 primary + four
continuations = 41 states / 82 captures**. Source templates and static landscape
framing/Stack placement are materialized only by doc capture tooling; no working
gesture claim. Primary contracts, summaries, protocol and future T-690–T-695 align.
Preserve earlier feedback/provenance/copy suggestions; validate, save and push.

## Previous UI feedback iteration — D-295

Read cumulative F01–F47 before UI edits. Full review remains at the exact existing
port-8443 review.html URL, replaced in place with PDF after edits. No app code,
cloud edit or stage/gate transition. Round 4 selects naming dialog/select-all,
one deck-delete title, games-list-only reconnect escape, unruled/equal-footprint
fields, red opponent area, owner borders/Change owner, life squares without
printed names/Life, bottom-right P/T, central corner-peek Stack (unlimited entries,
top+2 visible), reveal placement and dense event-type-icon nodes. Combat between
library rows with Stack beside it is **tentative**, not final geometry.

Owner is shared card metadata initialized creator/bringer, explicitly editable,
not controller/placement/permission. Moves/resets preserve it; projections derive
live source owner, frozen effects retain last-live owner. Board/model/protocol/
storage/summary and future T-685–T-689 align. Native gestures are not run by inert
sources. Shared table-round4.html/CSS uses captureVariant only in doc tooling;
old derivatives/cloud exports remain provenance. G04/G05/G06 add card actions,
token/ping/reveals and owner chooser: **35 primary + three continuations = 38
states / 76 captures**. Preserve all earlier unsuperseded feedback and remaining
copy suggestions. Rebuild/verify full serving and save/push every completed batch.

## Previous UI feedback iteration — D-294

Read cumulative F01–F36 before UI edits. Current full packet remains at the exact
port-8443 review.html URL, rebuilt in place with PDF after every batch. Preserve
earlier decisions except explicit supersessions; do not implement or pass a gate.

Selected in Round 3: games require names; offer fantasy/wizarding adjective + thing
suggestions (Azure Spark / Crackling Mist). P05 proposes editable prefill/reroll;
final Create & invite creates one game+first invitation with the chosen name.
Server creation date/name belong to the persistent game, survive later incarnations
and retries, and appear beside state/in game titles across devices. No rename or
unique-name feature inferred. Protocol/storage/contracts and T-681–T-684 align.

No app Back/home arrows anywhere (native Back remains). Titles/hamburger share a
row; import/editor Exit without saving and lobby Cancel are upper-right. Related
pairs fill 50/50; dialog X upper-right. Border-only cover selection; deck-deletion
copy only says cannot be undone. Lobby uses game title/date, Reinvite in opponent
slot, no leaving helper under Ready. Deck picker highlights a personal draft and
confirms once at bottom (no per-row Select/request/Ready clearing). Narrow 24 px
**1 / ∞** controls prioritize name space, explicitly superseding 48 px-wide
grip+count prototypes; ordinary buttons and accessible alternatives remain. Follow-ups
make them direct one/all tap buttons **as well as** drag origins, with no double
dispatch. Latest actual displayed move is plain text in a reserved fixed slot,
fading over ~1–2 s without layout shift. A tap emptying a row quickly fades it and
slides following rows up, independent of feedback layout. Requests/prediction never
wait for animation; preserve captured identities and normal reconciliation.

P05 replaces retired G00; L04b adds a faded-feedback snapshot:
**32 primary states + three continuations / 70 captures**.
U01 now has a real table backdrop. Retired/local/raw sources are provenance outside
current review, not app implementation or new cloud edits. Earlier copy suggestions
remain suggestions except selected D-294 removals/supersessions. Always rebuild
the full packet, verify served bytes and save/push the completed documentation batch.

## Previous UI feedback iteration — D-293 (retains D-292)

Round 2 F11–F22 is in [cumulative feedback](../design/02-experience/ui/feedback.md).
No authentication top Back; in-form/native Back suffice. Provisional ATW hat logo
above menu options. Matching icons on indented navigation/Logout/main destinations;
icon-plus-text buttons generally, icon-only tight gameplay controls with named
actions and meaningful state values. Bare game names/right-chevron Open; Reinvite
inside lobby. Long press/accessibility context offers setup Cancel or active Delete.
Owner clarification: active deletion needs an **extra confirmation** and removes
the **shared game for both players**, not hide-for-me. Primary lifecycle contract
and protocol define derived server ordering/incarnation/retry/access safeguards.

Bottom new-import creates a new deck. Existing deck **Edit → Import decklist** has
Overwrite(default)/Merge toggle; owner confirms Merge **adds quantities per section**.
Atomic resolution/save, dedup and selected-copy isolation persist. Preparation has
distinct one/all drag handles, no visible Move buttons; existing per-card requests
and native accessibility equivalents. Art thumbnail after quantity opens full
inspection, with lazy/text fallback and normal gameplay disclosure boundaries.

Packet now has **32 primary states + two scroll continuations / 68 captures**.
P03/P04/D07/D08 are new local inert states; D02/D04/L04 are locally recomposed with
old derivatives retained. No cloud edit or application implementation is claimed.
Every pictured state/orientation has a copy-audit disposition in review.md; its
**suggested** further deletions are not owner-approved removals. Earlier decisions
persist except explicit supersessions. Full review.html/PDF must be rebuilt and
published at the same port-8443 URL, never a subset/versioned link. No gate pass or
general stage transition. T-677–T-680 are future native-app verification, not tests run.

## Previous UI feedback iteration — D-292

Before UI edits, read [cumulative feedback](../design/02-experience/ui/feedback.md).
All decisions persist unless explicitly changed; append new rounds/supersessions.
AGENTS.md now requires rebuilding/publishing the **full** HTML at the exact same
`https://desktop-b2kalvm.tail27cccc.ts.net:8443/review.html` URL after edits. The
document service serves the repository UI directory directly; compare served
bytes with the rebuilt file. Preserve routes and report remote-access limits.

Selected: fullscreen without OS/title bars (necessary controls stay), app name
Async Tabletop Wizards / subtitle Specialized tabletop simulator for asynchronous
wizardry; same centered login card both orientations; no reset-session explanation
copy. Shared Menu/Games hamburger: Menu opens main menu with indented Manage decks
and Contacts only; Games opens browser with every current game/setup row. Logout
fixed below scrolling navigation. Landscape non-gameplay keeps navigation visible,
gameplay does not. Main menu has Games/Manage decks/Contacts, no notification-settings
or direct Logout links or redundant descriptions. Browser combines games/setup in
one list **before** Invitations. Current primary contracts/oracles replace old
Logout/Play/Friends-placement wording; actual auth/lifecycle behavior is unchanged.

All 27 local source derivatives apply this round; N02 is a new locally composed
open-navigation state. No new cloud edit is claimed; raw sources/prompts stay as
historical provenance. Total 28 primary sources + two scroll continuations = 60
captures / 30 states. Earlier counts/layout prose below are historical. D-291
gameplay direction stays; no implementation, general stage transition or passed gate.

## Earlier gameplay UI extension — D-291

The owner now authorizes extending the same Stitch project and Markdown/HTML/PDF
review packet with the gameplay screen, in both orientations. Read the clarified
[gameplay brief](../design/02-experience/gameplay-screen-proposal.md) and G01–G03 /
H01–H03 in the packet. The original non-gameplay-only limit below is superseded
for these design artifacts, not for application implementation or a passed gate.

Owner answers: own Hand hovers below the land band above the fixed bottom controls;
central Stack/combat lanes; hold Undo for Redo with explicit menu/accessibility
alternative; dragging a playing timeline pauses then scrubs. Existing grouped
Previous/Next and exact-mutation scrub remain distinct. Accidental-branch concern
is satisfied by existing eligible Undo of the first child after a fork; do not
invent Undo across the fork or automatic return to the displaced continuation.
Recompute after each Undo: if the last extra continuation disappears, a former
fork is not a permanent lock; retained linear-future replay checks still apply.
Opponent top Hand, life grouping and exact controls/geometry remain proposals.
The packet now has 27 primary sources plus two scrolled continuations / 58 captures.
It has a 34-page PDF, with gameplay on pages 27–32. Six initial and six corrected
cloud gameplay resources are retained in the same project; the manifest links
both versions. Checked captures use substantially locally recomposed inert
derivatives, also supplied as references for the corrected cloud pass, not a
claim that the service's final output is pixel-identical.
See artifact validation for the rebuilt PDF and private route checks. No code,
general stage transition, final UI approval or exhaustive state coverage implied.

**Review access repair:** the owner reported that the old `/ui-review` URLs opened
OpenCode. The document service saw no matching remote request, so browser-side
same-origin navigation/cache/service-worker interception is suspected, not proven.
Preferred URL is now `https://desktop-b2kalvm.tail27cccc.ts.net:8443/review.html`
(or `review.pdf`). Added only a tailnet-private HTTPS 8443 → document service
`127.0.0.1:8765`; original port-443 OpenCode root and legacy review path are intact.
The manifest/PDF links use the new origin. Local config/files are checked; phone
access still needs the owner's confirmation. No Drive upload, public hosting or
OpenCode browser-data clearing was performed. The original path setup below is
historical, not the preferred current review URL.

## Original UI pilot — D-290

The owner explicitly requested both portrait and landscape support/mockups and a
Stitch-based **non-gameplay** pilot, with gameplay omitted or empty. The
[UI draft index](../design/02-experience/ui/README.md) links the Markdown packet,
embedded captures, source/provenance and compiled HTML/PDF review editions.
Exact layouts are proposals, not approved behavior; the gameplay and full UI gates
remain incomplete. Do not interpret the older blanket “no UI work” wording below
as a ban on this specifically authorized pilot, or this pilot as implementation
authorization/a passed general stage gate.

Google Cloud CLI is permanently installed per-user at
`~/.local/opt/google-cloud-sdk`, exposed through `~/.local/bin`. The owner completed
Google OAuth and approved creating `async-tt-wiz-ui-20261021` and enabling Stitch,
without attaching billing or paid resources. Private Stitch design project:
`6617750535460836476`. Authentication material stays outside the repository and
outside hosted review content. Preserve the existing root Tailscale Serve route;
use a separate path for this packet, never Funnel/public hosting.

The packet has 21 primary drafts, two scrolled continuations, 46 captures and a
27-page PDF. Local user service `async-tt-wiz-ui-review.service` serves only the
UI review directory at `127.0.0.1:8765`; Tailscale proxies `/ui-review` to it,
preserving the root `127.0.0.1:49374` route. The loopback files and route config
were checked; this host cannot self-connect to its tailnet HTTPS endpoint, so
phone/other-peer access still needs the owner's check. See the packet's artifact
validation note. No public hosting or claim of full UI/device/state coverage.

## Latest follow-up review simplifications — D-289

The owner discussed the second architecture review, rejected its overengineering
and requested these changes. The selected stack and specification-only phase stay:

- Nodes have stable locally known UUIDs retained on server acceptance. Requests
  reference those IDs; read/cache revisions/ordinals do not require guessed future
  values or an acknowledgement barrier. No remapping/provisional-reference protocol.
- Ordinary authentication suffices: reset/processed server Logout invalidates old
  sessions for new requests; already-authorized/in-flight/admitted work may finish.
  Remove special reset/login/rehash/HTTP/game-commit ordering, account authorization
  lock hierarchy and execution-time session fences. Local Logout is immediate,
  remote revocation best effort; no durable logout credential/revocation workflow.
- The needed card metadata fits a regular complete HTTP POST. Remove application
  definition-upload chunks/sessions/manifests/completion routes/tables. Libraries
  handle transport framing/streaming. Keep account-private validation/content IDs,
  atomic saved-deck/prepared-copy attachment and no extra user Send. This does not
  remove complete-history baseline chunking or catalog-update staging.
- Deleted-cursor parent/ancestor fallback was already specified; reaffirm after
  pruned-delta full recovery using retained parent metadata, not a new gap/service.
- Shared-socket slowing under overload is acceptable; do not redesign isolation.

Primary contracts/protocol/storage/auth/runtime, summaries and test oracles reflect
this directly. Correct T-656/T-666/T-667; T-671–T-673 add UUID/fallback/overload traces.
The review's stronger security/Logout/transport findings are withdrawn as blockers;
do not resurrect older D-288 race/upload machinery from the historical sections.
No implementation/UI work or passed review gate is implied. Run documentation
checks and save/commit/push this batch normally.

## Earlier architecture-review discussion and repairs — D-285–D-288

The owner requested an adversarial technical review, discussed all eight findings,
corrected the overcomplicated sequencing/exposure/race framing, and asked to apply
the agreed outcomes. The selected stack stays; no implementation/stage transition.

- **D-285:** simple local FIFO over ordered WebSocket/TCP, no contiguous client
  admission numbers or missing-message gate. Keep stable IDs, durable server inbox/
  arrival ordinals and stream fences; terminal failure does not block independent
  later work. Retained uncertain requests recover normally, restart stays D-278.
- **D-286:** ordinary reconciliation for inspection/View/Reveal. Server Draw-first
  may turn predicted Reveal A/B/C into accepted B/C/D. Extra speculative knowledge
  is acceptable; **remove separate disclosure reports/provenance reconstruction even
  while memory survives**, not only after crash. Missing specific inspection context
  rejects normally. Accepted records/reference protections stay; accepted trays do
  not refill, and bound Draw/move targets are not substituted. Earlier F-03/G-03's
  special recovery remedy is superseded, not an unresolved owner question.
- **D-287:** create+first-invite differs from reinvite-to-exact-existing-game. Cancel
  first discards reinvite; cancel afterward removes it too. No revival/replacement,
  transport redesign or new cancellation policy; retained dedup records are not an
  available cancelled lobby. Creation retries return original IDs/outcome.
- **D-288:** standard login/reset verifier-version/epoch check before session issuance/
  rehash and retained admitted-session metadata; atomic HTTP business outcomes and
  account-private definition uploads before any game; compact canonical Stack digest
  guard and practical workload/backpressure/streamed-read defaults. Token/card moves
  already differ normally: disappearing tokens do not become destination-prefix
  anchors. Existing F-04/G-04 gets the missing explicit example, not new token policy.

Primary contracts, technical profiles, summaries and old contradictory test oracles
are updated directly. T-664–T-670 add the concrete traces. Generated schemas/build
locks, executable tests and measurements still await authorization. Do not build
the superseded exposure side channel or sequence-gap recovery to satisfy older
handoff/history prose. Normal documentation checks and `scripts/save.sh` apply.

## Earlier remaining-point completion — D-278–D-284

The owner asked to finish remaining specifications and will open an independent
review session themselves. Do not spawn reviewers, automatically start UI work or
implement/scaffold anything. The current batch is ready for that review, not a
proof every defect is absent or a review/UI gate pass. Older "next" directions and
technical-open labels below are historical where this section supersedes them.

Owner choices collected together:

- **D-278:** lost local requests are acceptable. The app can crash even before
  persistence; on startup sync the server and resume there. Follow-up explicitly
  selected server-first restart and **allow lost exposure records**. No required
  crash-durable outgoing/prediction/exposure journal or disk-before-display gate.
  Same-process reconnect keeps original IDs/guards/random inputs and withdrawal
  flags; process death drops the overlay/queue, never recreates it. Logout also
  clears volatile account work/private downloads and fences callbacks. Server inbox/
  outcomes stay durable, so admitted late effects still arrive normally. The owner
  accepts that lost unreported hidden inspection weakens prior exposure safeguards;
  server-retained records govern Undo/Redo, not inferred knowledge. No offline play.
- **D-279:** password reset revokes all sessions **after reset succeeds**, not when
  email is requested/sent/opened. Failure/expired/reused links do not revoke.
- **D-280:** MTGJSON records + Scryfall lazy printing/face artwork, bounded startup
  metadata update check. Provider/upstream rights remain a release check.
- **D-281:** no Select/Ping double-tap recognizer, two immediate independent mode
  taps. Normal single-tap Actions / double-tap orientation remains D-267.
- **D-282:** Invitations/Game activity channels, per-game coalescing, all logged-in
  registered installations, local game-activity suppression while displaying that
  game. FCM optional, polling best effort, no R1 in-app mute/read receipt.
- **D-283:** no automatic age expiry of required current-game data/outcomes;
  production erasure requests/timelines/backups are explicit external operator
  responsibilities, not an R1 deletion UI/legal clearance/shared cascade permission.

**D-284 records the instruction to finish technical details**, not individual owner
endorsement of each encoding/engineering parameter. The following initial technical
contracts implement the selected behavior and are reviewable/revisable technical
choices, not new product proposals or executed code:

- [Protocol v1](../design/04-technical/protocol-v1.md): JSON/UUID/canonical bytes,
  stream fencing/sequence/readiness, request/receipt/outcome/revision correlation,
  deterministic predicted output IDs, exact-reference controls, baseline crossing
  pending effects, HTTP surface/version compatibility and complete gzip chunk export.
- [Storage mechanics](../design/04-technical/storage-mechanics.md): logical tables/
  indexes, single-service serialized per-game ingress, DB writer ownership,
  account→game→stream locking, atomic inbox execution, replay/reference repair,
  N=1000 effective successors plus CPU/effect/memory budgets, leases/pins/cache-window
  defaults, crash matrix and retention/erasure boundary. Server durability is not
  weakened by D-278 client simplification.
- [Runtime/verification profile](../design/04-technical/runtime-and-verification-profile.md):
  domain-separated SHA-256 counter generator, unbiased sampling/Fisher–Yates and
  canonical member order, numeric text grammar, JVM21/PostgreSQL17/API29 + SDK36
  baseline, module/container/configuration/health/migration/release contracts,
  explicit input/backpressure/cache defaults and device/workload/performance targets.
  Exact dependency patch lockfiles are later clean-build qualification, not a
  guessed version matrix or more framework selection.
- Auth/catalog/notification documents complete opaque Keystore-protected tokens,
  Argon2id/reset/SMTP/race ordering, staged MTGJSON/Scryfall safe downloads and
  permission/routing/suppression/dedup/poll/FCM defaults. No analytics upload SDK.
- [Literal random/UUID vectors](../design/05-verification/random-v1-vectors.md) were
  independently calculated with Python stdlib, not executed Kotlin/app tests.
  T-647–T-663 supplement corrected old crash/exposure/gesture/provider/upgrade oracles.

The primary history/account/domain/Android contracts, product summaries and gap
disposition now state the supersession. Remaining generated OpenAPI/JSON Schema,
DDL/build catalogs, executable fixtures and measured qualification are artifacts
of the later authorized implementation loop; their semantics are specified here,
not license to adopt conflicting library defaults. Exact controls/layout/easing/
accessibility presentation remain the UI gate. Production operations/erasure/rights
checks are not declared fulfilled. Save this documentation batch using normal
checks and `scripts/save.sh`, with no unrelated work.

## Earlier prediction cascade, late acceptance and live/playback — D-277

The owner confirms the three-layer prediction model and stresses that after local
moves M1 A→B, M2 B→C, M3 C→D, incorporating expected M1 must leave the displayed D
unchanged. Insert newly accepted remote work before re-evaluating remaining active
predictions in local order. An independent note leaves D; remote B→X invalidates
M2, then M3, so withdraw those predicted effects and animate the net displayed D→X.
Only actual guard failures cascade: independent valid work (or a later same-card
move whose original source is supplied by remote work) stays. No blanket suffix or
sequencing-dependency cancellation, substituted intent or new randomness.

The owner explicitly clarifies that if the server later accepts a locally cancelled
move, the update simply arrives like opponent/other-own-device work. "Cancel" is
**prediction withdrawal**, not recall/final rejection of already-sent/admitted
requests. Keep immutable request identity/random/exposure/outcome recovery and
active/withdrawn disposition separate. Withdrawn predictions do not speculatively
reappear just because later state becomes applicable; actual acceptance takes the
ordinary committed-update path with correct original author/ID, not resubmission,
suppression or resurrected predicted suffix. A final rejection of an already-
withdrawn effect needs no second rollback. This does not settle arbitrary unsent-
buffer policy or permit offline actions.

Personal **live** mode applies coherent remote updates immediately, then rebases
active predictions. Returning from background with unpresented updates enters
**playback**, from last presented/reviewed context even if background sync already
downloaded the data. Playback advances by groups while accepted sync continues;
paused cursor remains stable with normal deleted-node fallback. Catch-up or
deliberate gameplay continuation returns to live prediction; pause/scrub alone does
not create historical continuation intent. Same-path catch-up needs no rollback;
displaced-path catch-up keeps D-177's fork traversal. Own expected confirmations/
receipt bookkeeping alone do not create a catch-up replay. End/lobby/incarnation
rules still govern; no ended-session archive, cross-device progress or presence.

Animation is always previous displayed board → next selected target. Three same-
card moves in one live batch or one playback group are one original-to-final
transition, not queued waypoints; separate playback groups remain staged even if
downloaded together. Retarget active animation from current drawn geometry, with
matching hit/accessibility bounds. Every semantic node/group/reference/random/
exposure record remains, including net-zero endpoints; synchronization never waits
for animation/group completion. Exact UI/timing/autoplay preference/storage/result-
delta correlation and no-saved-boundary first-open presentation remain completion.

[Prediction/playback design](../design/04-technical/prediction-and-playback.md)
and the primary history mode/rebase/endpoint sections make this explicit; T-642–T-646
supplement corrected existing D-177/D-180/D-185 cases. Remove the old generic live-
follow exclusion where it would contradict these now-included modes; other D-241
scope exclusions remain. No app/scaffold/executable tests or stage transition is
authorized. D-267 Select/Ping double taps and D-271 startup MTGJSON checks remain
tentative. Do not automatically advance to a new topic until the owner asks.

## Earlier complete current-game tree download acceptance — D-276

The owner corrects the proposed active-path/lazy-offshoot approach: games likely
have one long path and short offshoots, so the **whole branched tree** should be
downloadable, perhaps less than twice the main path size. They accept the revised
proposal and request "apply+next". Initial download and fresh-baseline/full recovery
now target the complete retained current-game tree, current board/control/lifecycle
state, retained initial data and required definitions/reference/control payloads.
Include inactive, hidden and archived-retained alternatives and retained futures
beyond an internal shared pointer; visibility filtering is not download filtering.

This supersedes older D-264–D-266-era "full sync is not a mandatory whole-tree
download" and minimal-baseline/lazy-history **default**, including older handoff
summaries below. It does not reverse the no-routine-full-tree constraint: normal
play/reconnect with usable deltas still sends only changes. Art stays separate/lazy.
Compression/chunking/paging completes all tree pages automatically, not only those
the user visits. Compact shared topology/mutation records are not duplicated full
board snapshots per node or a recursive all-in-memory tree blob. Partial staging
must remain visibly/internally incomplete; safe resume/restart and coherent install/
catch-up preserve original request/random/exposure/account/personal recovery.

The expected `<2x` overhead is a heuristic, not a measured guarantee/history cap or
permission to omit larger offshoots. No finished-session archive/offline gameplay
scope is added. Exact wire fields/typed patch representation, compression/chunk
limits and baseline lease/pinning/resource policy remain technical work; acceptance
does not finalize the earlier exact protocol proposal wholesale. See
[transfer design](../design/04-technical/sync-and-storage.md#complete-current-game-transfer--d-276).
Current design prose and T-616–T-618/T-627 are corrected; T-640/T-641 add complete
tree/offline-read-only review and interrupted/large transfer coverage. No app,
executable tests or stage transition is authorized. Next: propose client prediction/
reconciliation mechanics without reopening D-185's agreed difference-only behavior.

## Earlier durable request ordering/recovery acceptance — D-275

The owner accepts the preceding proposal and requests "apply+next". Select stable
unique request/semantic payload identity, PostgreSQL-backed durable per-game inbox
admission/order, atomic individual execution/results/relevant revisions/outbox and
obsolete-connection fencing on reconnect. Each request retains authenticated actor,
game/lifecycle context, action/references/guards/random input and explicit historical
intent where applicable. Changed intent/scope under the same identity is misuse;
same-account credential/connection renewal is not changed gameplay payload.

Receipt requires inbox commit and means pending, not successful mutation or replica
catch-up. Pending duplicates keep one queue position; final duplicates recover the
original accepted/rejected/no-op result, not revalidation against later state.
Server admission order governs per-game serial processing, preserving client send
order/interleaving without timestamp/dependency reconstruction or routine ack waits;
unrelated games run independently. One constituent rejection does not cancel later
valid commands or make bulk gestures atomic. No separate message broker is required.

Reconnect queries/retransmits original uncertain requests and separately catches
up accepted state. Timeout is unknown, never a fresh ID/roll or invented rejection.
Race-safe stream-scoped replacement fences new admissions from obsolete transports,
preserving already-admitted records and other devices. Preserve original unadmitted
recovery order and account/incarnation/preparation/invitation fences; old results
cannot reapply state/Redo resets over newer play. Existing exposure/Logout duties
remain. This does not blanket-authorize never-transmitted buffers/offline gameplay.

[Request ordering/recovery](../design/04-technical/request-ordering-and-recovery.md)
separates selected mechanics from exact SQL/locking/worker/fence/resume/result
schemas, semantic identity comparison, retention/backpressure and provisional-
reference/readiness completion. T-636–T-639 supplement stable prior tests; old
inbox/receipt/open-ordering prose is corrected. No app/schema/executable tests or
stage transition is authorized. Next: propose bounded typed deltas, paged history
and coherent leased baseline transfer mechanics without treating all earlier
wire/schema/lease proposals as already approved. D-267 mode double taps and D-271
startup MTGJSON checks remain tentative.

## Earlier Shuffle and Random-starter acceptance — D-274

The owner accepts the preceding proposal and requests "apply+next". Each new
connected in-game Shuffle saves a fresh client seed with its original request;
client/server use the same explicitly versioned Fisher–Yates/unbiased-sampling
implementation over a stable canonical ordering of actual execution-state member
IDs. The server uses current Library members and the supplied seed, not a second
sample/client snapshot; record the concrete accepted order. Uncontended mulligans
agree without acknowledgement waits; changed membership may invalidate already-
bound Draw requests under ordinary guards, never silently substitute extra cards.

Retry/prediction preserves seed/request identity; history/Redo/accepted recovery
uses the recorded concrete permutation. New invocations get fresh input even after
Undo, though chance may repeat outcomes. For game start the server acquires fresh
independent inputs for both initial Libraries and resolves Random starter fairly
once at the actual start transition. Record chosen participant/orders with setup;
same-start recovery cannot resample/reset life, require additional post-readiness
presence/confirmation or add opening draws. Fresh incarnations with Random selected
get fresh resolutions; lobby default/edit/option-retention completion is separate.

The [selected randomness design](../design/04-technical/sync-and-storage.md#selected-shuffle-and-setup-randomness--d-274)
keeps exact generator/entropy/seed/ID encoding, unbiased bounded sampling and version
registry as technical completion, not an unselected overall mechanism. Old explicit
"Shuffle still proposed"/"Random timing open" prose is updated, including stable
T-438/T-439/T-472; T-634/T-635 supplement it. No seed epoch/commit–reveal/competitive
anti-cheat, test seed picker, executable code or stage transition is authorized.
Next: present a request-ordering/recovery protocol proposal for comments, without
promoting prior durable-inbox/connection-fencing details to approved requirements.
D-267 mode double taps and D-271 startup MTGJSON checks remain tentative.

## Earlier Android distribution/support acceptance — D-273

The owner accepts the preceding release/compatibility proposal and requests
"apply+next". Select versioned signed APK downloads, manual updates through
Android's normal installer, stable package/signing identity and **Android 10+ /
API 29**. GitHub Releases was an example, not a selected host. No required Play
Store/custom in-app updater. Test phones/tablets and an otherwise-supported
non-Google device; dependency/automation selection cannot silently raise the floor.

Compatible upgrades preserve valid login/downloads/original pending request and
exposure recovery through verified supported migrations. APK releases and protocol
versions are distinct; compatible different APK versions may play together. For
unsupported protocol/semantic combinations explain the needed update and refuse
unsupported operations before mutation, without wiping data, converting a pending
request to a new invocation or inventing rejection of an unknown prior outcome.
Exact negotiation/error/window schemas, SDK/dependencies, reference devices,
release/signing mechanics and update presentation remain technical/UI completion.

See [release/compatibility design](../design/04-technical/android-distribution-and-compatibility.md)
and T-631–T-633. D-272 external production deployment/backups remains unchanged.
No app/scaffold/build automation/executable tests or stage transition is authorized.
Next discussion: propose the still-unselected deterministic Shuffle/random-starter
mechanism, without marking it approved by this release-policy acceptance. D-267
Select/Ping double taps and D-271 startup MTGJSON checks remain tentative.

## Earlier Compose deployment and external operations scope — D-272

The owner may first deploy on a Raspberry Pi; the important requirement is Docker
Compose deployability. Production deployment and backups belong in a **separate
repository**. Do not keep VPS/cloud choice, host provisioning, production Compose
manifests, certificate/proxy setup or backup/restore automation in this project's
implementation backlog, and do not create or work in the other repository now.

Keep application image/container/configuration/secret-input/health/runtime and
persistent-state/migration/compatibility/recovery interfaces here. Existing D-262/
D-263 Compose E2E and Testcontainers integration remain here too, with isolated
synthetic environments, not production manifests/data/secrets. The external repo
owns production orchestration, monitoring/alert infrastructure, deployment/rollback
and backup/restore policy/execution; privacy/recovery obligations still constrain
that integration. Do not infer zero data loss or a recovery deadline.

Recommend a 64-bit OS and compatible ARM64 JVM/PostgreSQL images for the Pi trial;
exact model/resources/architecture matrix and measured capacity remain unselected.
No ARM32/all-Pi guarantee, mandatory VPS, Kubernetes, Caddy or WAL-backup tooling
is approved. See [operations boundary](../design/04-technical/architecture-and-operations.md#container-delivery-and-external-operations--d-272)
and T-630. No application/container/Compose scaffolding, infrastructure automation,
executable tests or delivery-stage transition is authorized.

## Earlier arbitrary client definitions, lazy art and stale catalogs — D-271

The owner confirms lazy image resolution/download with local caching, asks how
stale clients handle an opponent's unknown card, and then explicitly accepts that
the client supplies arbitrary deck/card definitions to the server before start.
Server validation is structural/security/resource/reference validation, not an
official-card/MTGJSON/format-legality check. Retain accepted snapshots and supply
those exact definitions to the opponent; differing supplied content under the same
claimed name/external ID must not overwrite another deck/game/global catalog.
This clarifies D-270's earlier caveat about arbitrary input: data may be arbitrary,
but it is not executable rules, a mutable global official catalog or a new authoring
UI. Normal deck preparation/Ready/copy-isolation remains; no extra manual Send.

Existing retained-definition/server-authority rules derive the stale-catalog answer:
required game definitions come from the service even if the recipient lacks them
in its general catalog, including new token definitions after start. Missing
required transfer data is incomplete recovery, not a dropped/rejected accepted
action or duplicate execution. New deck imports need resolved definitions under
D-198, not artwork or the newest full catalog; D-65 optional blank defaults remain.
Art bytes load lazily and use text fallback without blocking save/Ready/play.

The owner's startup MTGJSON update check is tentative ("probably"). The
[catalog design](../design/04-technical/card-data-and-art.md) recommends small
Meta.json source date/version checks, bounded/cached/throttled and nonblocking for
usable games, with staged safe updates. Provider selection/cadence/network/update
UI remains unfinalized; the prior Scryfall-only recommendation is an alternative,
not secretly selected. T-627/T-628 cover accepted outcomes; startup T-629 is
conditional and all automated data/sources stay synthetic. No application,
executable tests, authoring UI or delivery-stage transition is authorized.

## Earlier client card-data/art downloads and Cockatrice research — D-270

The owner wants card records/art downloaded from public sources and stored locally
by the client, and asks where Cockatrice's installer/updater obtains its records.
D-270 selects direct client downloads/local reuse, not a provider/format, mandatory
backend catalog/image proxy, whole-image-library install or separate updater UI.
Preserve server-authoritative essential game definitions/defaults/history and
existing downloaded read-only access; a client cache/update is not game authority.

[Card data/art research](../design/04-technical/card-data-and-art.md) records upstream
Cockatrice commit `829152f`: Oracle imports MTGJSON v5 AllPrintings (compressed where
supported), checks Meta.json, and downloads token XML separately from GitHub. Image
URL defaults start with Scryfall CDN/API and then Gatherer; configured historical
fallbacks are not proof of current endpoint availability. Recommend considering
Scryfall bulk records plus supplied image URLs for a simpler single-provider path,
but neither that provider nor MTGJSON is selected yet. Public availability/client
downloads do not settle licenses, attribution or underlying artwork rights. Exact
update/cache/printing-face resolution and source validation remain technical work.
T-625/T-626 specify synthetic normal-path download/local reuse and safe updates;
no executable tests, application or stage transition is authorized.

## Earlier optional FCM and polling fallback — D-269

The owner accepts FCM only as optional and suggests AlarmManager polling as a
fallback in a pinch. App use/login/game sync must work without Google Play services,
Play Store distribution or FCM setup on the non-FCM path. D-219 invitation/grouped
opponent activity stays in scope; optional push/polling changes delivery, not game
authority or covered-card privacy. Local polling notifications obey D-220 Android
permissions/channels, not a bypass of disabled notifications. Android background
execution is best effort; foreground refresh/recovery cannot depend on it.

[Notifications/background work](../design/04-technical/notifications-and-background-work.md)
records recommended WorkManager polling (already selected for appropriate background
work under D-263), bounded authorized summaries/cursors, deduplication and account/
session-fenced callbacks. Periodic WorkManager has a 15-minute minimum, not a delivery
deadline; AlarmManager repeats are inexact and do not bypass Doze/network limits.
No exact-alarm permission/cadence, always-on service, new provider/mute screen or
SDK/build-flavor matrix is approved. Prior two-channel/foreground-suppression/all-
device-routing proposals remain unapproved. Verification T-622–T-624 and updated
T-486–T-489 include non-Google/no-FCM operation, delayed background work, actual OS
display, settings/privacy and cross-account races. No executable tests, app code
or delivery-stage transition is authorized. D-267 mode gestures and unapproved
D-268 security/reset mechanics remain tentative as described below.

## Earlier permanent-login convenience requirement — D-268

The owner wants an auth token that lasts forever, or saved credentials plus
automatic reauthentication unless explicitly logged out. Use the non-expiring
revocable-token route: preserve login through app/service restarts, reconnect and
long inactivity without idle/absolute age expiry, and do not store the password.
Logout clears the local login and must not be undone by auto-reauthentication;
device sessions remain independent. This does not bypass explicit security
revocation/account authorization, or promise recovery after local token/app-data/
Keystore-key loss. Invalid access can require login; preserve D-217 same-account
game/pending-outcome recovery, D-241 account-private cleanup and protected exposures.

The [authentication document](../design/04-technical/authentication-and-sessions.md)
separates that requirement from recommended opaque-token/hash/Keystore mechanics.
The preceding Argon2id/rate-limit/reset-token/reset-revokes-all suggestions have
not all been approved by this focused correction; reset session policy/provider
remain proposals. Do not conflate short-lived reset links or socket/network
timeouts with login-token expiry. Older normative expiry/lifetime-open prose and
T-396/T-480–T-482/T-543 are updated; T-621 covers no-time-expiry/device isolation.
Mode gesture proposal D-267 remains tentative. No application/scaffold/executable
test implementation or delivery-stage transition is authorized.

## Earlier scene/input acceptance and gesture correction — D-267

The owner accepts the canvas scene/input proposal: shared engine → displayed
live prediction OR personal historical boundary → scene/layout → animation/camera
transforms → Canvas. Scene objects are view data with stable semantic identities,
not another game model. Drawing/hit testing/selection/accessibility/test targeting
share visible geometry, including during animation. Use one gesture coordinator;
previews/animation never emit per-frame requests, determine event order or delay
sending. Preserve captured identities/cancellation and difference-only correction.
Compose menus/editors surround Canvas; desktop adapters can differ. Exact timings,
geometry, performance budgets and final layouts remain design/UI work.

**Confirmed correction:** normal single tap opens card Actions; double tap performs
one selection-aware tap/untap, not the opposite. Preserve D-35/D-36 orientation
scope and D-248 menu scope: single-tapping an unselected card opens only its Actions
without clearing the prior selection; double-tapping it to rotate clears that
selection as before. A double tap must not first flash the single-tap menu or emit
two orientation toggles. Hold/drag, special pile controls and semantic grouping
remain unchanged. Count two physical taps in interaction budgets. Current design
tiers, stable older tests and E2E helpers are updated directly, not just covered
by a precedence note; T-619 adds scene/gesture verification.

**Tentative:** owner says Select/Ping modes perhaps have no double-tap action.
Recommend no double-tap recognizer: each tap immediately performs its mode action,
so two rapid taps toggle twice rather than coalescing, opening Actions or rotating.
Do not mark this as confirmed yet. The old D-34 double-tap Actions shortcut in
selection mode is explicitly under review; T-068 no longer mandates it and T-620
is conditional. Existing hold/drag and explicit accessible Actions remain available;
exact control placement is not selected. No new implementation/UI drafts or
delivery-stage transition is authorized.

## Earlier checkpoint clarification and retention confirmation — D-266

The owner asks whether checkpoints are caches/optimizations and confirms the
preceding design on that basis. Yes: checkpoints are rebuildable materialized
state, not the sole durable history. Preserve retained initial/root state,
essential definitions and recorded tree/mutation/control data; eviction may slow
reconstruction but cannot remove reviewable history or change recorded results.
Fresh baseline transfer can regenerate a checkpoint from that basis, independently
of the rolling synchronization-delta window and request/outcome recovery.

This confirms the bounded sync-revision retention direction from D-265 while
keeping gameplay history separate; exact count/age/byte budgets and baseline
lease/protocol fields remain technical design. Do not keep describing that overall
direction as needing owner approval or select a history-expiry policy. Checkpoint
eviction verification supplements the existing integrity/baseline cases. No
implementation or stage transition is authorized.

## Earlier storage acceptance and revision-retention discussion — D-265

The owner accepts PostgreSQL/jOOQ relational structure with versioned JSONB
payloads and separate tree topology/mutations/current state/revisions/outcomes/
outbox/checkpoints; selects **SQLDelight over SQLite** for the client. Four local
logical categories separate accepted replica, durable requests/exposures, disposable
caches and personal state; database schemas/generated types need not be shared
with the common engine. Flyway server and SQLDelight client migrations preserve
pending obligations/recorded outcomes. The [storage document](../design/04-technical/storage-architecture.md)
is the primary layout description; exact tables/indexes/versions/encryption remain.

The owner then suggests perhaps capping stored sync revisions per game and requires
full sync if the needed revision is unavailable; they are unsure about doing that
to gameplay history. Record the rolling delta window/count as **proposed**, not an
approved history cap or exact budget. The [baseline proposal](../design/04-technical/sync-and-storage.md#bounded-revision-window-and-baseline-recovery--d-265)
defines an oldest resumable client-base revision, explicit baseline-required result,
coherent current-state/control baseline plus paged history coverage, atomic install
and further catch-up. Full sync is not full-tree download. Pruning/lease races must
restart safely; exact pinning/cap/resource details remain design work. Preserve
pending request/exposure identities, recorded randomness, account/incarnation fences,
personal review fallback and required playback. Request outcomes/deduplication are
independently retained; missing old deltas never imply a command did not execute.

Retained current-game branches/mutations, Redo/exposures/references cannot expire
because a sync revision leaves its window. No new finished-game archive guarantee,
permanent retention/account-erasure policy or deleted-record disposal is approved.
T-617/T-618 and test architecture add specified verification, not executable tests.
No implementation/scaffold or delivery-stage transition is authorized.

## Earlier integrity agreement and next discussion — D-264

The owner accepts revisions as the convergence foundation plus lightweight
snapshot/checkpoint state digests, not per-gameplay-node hashes/Merkle trees or a
mandatory event chain. Recovery needs base/result revisions, gap/duplicate handling,
atomic local state/cursor writes, committed-delta or coherent-checkpoint catch-up
and periodic durable-head checks to detect a missing final update. Eventual
convergence assumes restored connectivity/access and correct successful recovery;
a correct revision alone does not prove materialized state correct. Compare
canonical semantic checkpoint state at the same explicit scope/boundary/revision/
version, excluding UI/predictions; preserve requests/exposures during repair.
"Synced" means the last verified head, not perpetual freshness. Hashes cannot
detect a semantic bug shared by both engines. Exact hash/encoding/cadence remains
technical design. The older D-259 chain is an unselected alternative; T-610 is
conditional, while T-614–T-616 specify the current baseline/scaling outcomes.

The owner's preceding concern also requires scalable transfer: no routine full-tree
round-trip. Separate topology/mutations, typed tree patches, paged revision-pinned
history reads and subtree cache invalidation have been presented as concrete
proposals, not an approved exact schema/wire format. Earlier durable inbox/FIFO/
connection-fencing proposals likewise remain detailed proposals; this integrity
acceptance does not approve every preceding mechanism wholesale.

The owner requests the next topic: present server/client storage and a SQLDelight
versus Room recommendation for comments. SQLite is already selected, but its access
library is not; do not silently finalize it. Continue documentation only, with no
application/scaffold/executable-test implementation or delivery-stage transition.

## Earlier stack and architecture-direction acceptance — D-263

The owner says the recommendations are good, accepting the preceding architecture/
technology batch. Record selections, not another approval queue: Kotlin/JVM Spring
Boot modular monolith, PostgreSQL/Flyway with jOOQ, Compose Canvas plus styled
Compose shell, local SQLite, WorkManager for appropriate Android background
recovery, HTTPS/JSON accounts/catalog/reads and ordered gameplay WebSocket with
durable revision recovery, Ktor Client/kotlinx.serialization, Gradle Kotlin DSL,
OpenAPI HTTP plus explicit socket schemas, Spring Security/revocable sessions and
Keystore-backed Android credentials. Select Kotlin/JUnit/Appium Java client/
UiAutomator2/Docker Compose E2E, native Compose component tests and Testcontainers
for smaller integration tests. Shared types/processing and server authority remain.

Preserve actual qualifications: Room Multiplatform versus SQLDelight is still an
alternative pair, not a selected library; Coil is conditional on canvas integration.
Exact protocol/schema/version details, scene/input/harness mechanisms, random
algorithms, session policies and operational budgets remain design work. The
shared Shuffle seed/canonical-permutation mechanism is still a proposal requiring
completion/verification, not proved by accepting explicit random inputs. Catalog/
image/email/push providers, support targets, hosting/operator budget, distribution
and retention/privacy expectations are not selected by this acceptance.

The technical index and focused docs now reflect the selections directly. Follow
the agreed completion order: sync/ordering → server/client durability → scene/input/
testability → security/adapters/deployment → versions/CI/performance. Do not leave
old "not selected" prose in current design, or treat accepting tools as completion
of protocols/design gates. This remains documentation-only; no application code,
executable harness, UI drafts or delivery-stage transition is authorized.

## Earlier E2E environment requirement and tool recommendation — D-262

The owner requires E2E/scenario tests with backend + database in Docker and at
least one normal Android app connected. This is not a retreat from D-244: one
instance can cover independent entry/deck smoke scenarios, but full gameplay
journeys still drive both players through separate Android UIs; same-account
multi-device cases add a third instance. No API-bot opponent is authorized.

The [test architecture](../design/04-technical/test-architecture.md#recommended-full-stack-scenario-system--d-262-environment)
now recommends Docker Compose for E2E services, with a host-side Kotlin/JUnit
runner using the Appium Java client and UiAutomator2 driver. These tools are
**proposed, not selected** by the environment request. Appium controls normal-app
UI/gestures, with explicit canvas accessibility/targeting support; no hidden action
API. The emulator can run on an accelerated host/CI worker rather than in Docker.
Use Testcontainers for smaller integration tests if adopted, not a competing owner
of the same E2E environment. Preserve full per-test reset/readiness, synthetic
data/art, setup-only users/decks and in-scenario recovery continuity. Exact tool
versions, device/port allocation, networking/TLS and deterministic race control
remain technical work. No executable harness or implementation is authorized.

## Earlier shared-code and database-access agreement — D-261

The owner requires the **same** Kotlin game-state objects and processing code for
actual server execution and client prediction, not duplicated/equivalent engines
or optional server reuse. They permit die/coin sampling locally and select jOOQ.
Shared command processing is deterministic given state/intent/random inputs;
server access, execution-state selection/order, acceptance and transactions remain
authoritative. jOOQ/schema types stay outside the shared semantic model.

Local die/coin samples bind to the original connected request, are durably captured
before display, and are validated/reused rather than independently rerolled by the
server. Retries/recomputation/replay keep original inputs/accepted outcomes; preserve
existing exposure recovery, no offline actions or automatic replacement roll.
Generator/encoding remain technical work. A shared per-request Shuffle random
input/versioned canonical permutation is **proposed**, not selected; G-05's
uncontended-success/current-membership/no-ack-barrier obligation still needs exact
protocol and verification. Random-starter acquisition is not finalized here.

The focused client/server/test and sync documents, primary domain/randomness and
composition contracts, T-599/T-611–T-613 and E2E journey record the boundary. Tests
and implementation remain plans only. Spring Boot/PostgreSQL/Flyway remain the
preceding recommended candidates; this message does not separately finalize them.
No application code, executable harness or delivery-stage transition is authorized.

## Earlier architecture discussion — D-260

The owner requests technology agreement and focused client/server/test architecture
documents now. Kotlin Multiplatform is selected for Android-first delivery with a
shared core reusable by possible iOS/desktop clients; desktop may have different
UI/controls. Extra platforms are options, not initial-delivery commitments.

The owner expects the game area probably needs to be entirely canvas-rendered for
card dragging/animation, with styled Compose surrounding menus as an option. Exact
renderer/component libraries remain proposals. Spring Boot, PostgreSQL and Flyway
are nominated with a request to compare alternatives, not yet final selections.
The [technical index](../design/04-technical/README.md) now links initial
client/server/test architecture proposals. Recommend Kotlin/JVM Spring Boot,
PostgreSQL and Flyway SQL migrations; keep data access, protocol, test frameworks,
versions and operational details unselected. Canvas requires explicit normal-app
accessibility/UI-test semantics and real gesture verification; it does not waive
D-244's real-app/real-service/synthetic-data acceptance boundary.

This is documentation planning only, not completed design gates or authorization
for implementation, UI drafts, executable tests or a delivery-stage transition.
Outstanding behavior-review findings below remain separate work.

## Earlier confirmations — shared switches and server/UI authority, D-258/D-259

**Subsequent review request:** the owner asks for an adversarial review of the
current specs and a walkthrough. The [current report](adversarial-review-2026-10-20-current.md)
reviews `f249d3f` and records G-01–G-07: historical bulk continuation handoff,
ordered first-application recovery, existing F-03/F-04 late-exposure/mixed-token
completion, a randomness/prediction proof obligation, leading-ping prose versus
the explicit O/T classifier, and existing F-06 active drift plus the root README.
No new owner-only policy choice is established; distinguish derived repairs from
technical mechanisms. This batch changes review/continuity records only, not
contracts or acceptance oracles. F-01 stays withdrawn, F-02/F-05 stay resolved;
no implementation or stage transition is authorized.

The owner confirms the scoped Redo rule: successful deliberate activation of a
different node/branch, interruption from the past or return to another continuation
clears **both players' Redo queues for this game**, atomically with the server
operation. Archive-and-return qualifies if it switches shared history; metadata-only
archival, personal review, failed/same-current-target no-ops and ordinary Undo/Redo
do not. Ordinary own new play clears own Redo; ordinary opponent play does not.
Retries recover the original outcome without clearing newer entries or reactivating
old state. F-02 is resolved by unavailable Redo, not the advance-to-A proposal.
The old D-234 stranded-but-retained fork example and T-527/T-528 are updated directly.

The owner also clarifies that synchronization is the default for almost all semantic
game state, not a per-feature device add-on. The primary
[state ownership matrix](../design/03-contracts/domain-model.md#state-ownership-and-request-boundary--d-259)
separates server board/history/control/communication/lifecycle, UI-only camera/
selection/modes/review/open menus, accepted replicas, optimistic shared-action
predictions and device-held cache/recovery data. All shared changes use requests
with provisional local application for latency hiding. Logged inspection stays
shared even when its inspector is a local panel; read fetching is not shared UI.
AGENTS.md carries the default so future agents do not invent per-device variants.

The owner raises blockchain-style event checksums as a technical option. The
[revision-chain proposal](../design/04-technical/sync-and-storage.md#proposed-revision-checksums-and-correction-points--d-259)
chains accepted publication/control revisions, **not immutable gameplay parents**.
It distinguishes accepted replica from UI/predictions, same-head tree changes,
missing/divergent bases, matching checkpoints and verified snapshot/suffix repair.
A latest hash alone does not locate divergence; checksum mismatch is not a new
whole-head action lock or proof of rejection. Hash/encoding/protocol remain design.

T-605–T-609, corrected older T-IDs and full-stack journeys specify the confirmed
outcomes; T-610 is conditional technical-proposal verification. No executable
tests/app exist or implementation/stage transition is authorized. F-03/F-04/F-06
still have derived repair work, not automatically new owner decisions.

## Earlier review discussion — conservative Undo/Redo and ending groups, D-257

The owner treats Undo/Redo as convenient tools, not the only way to obtain a
desired board/event chain. Prefer disallowing surprising complex cases over
special recovery behavior. Their tentative idea that head changes clear Redo
still needs scope clarification: deliberate activation/interruption/return versus
ordinary event advancement and Undo/Redo themselves, and which players' stacks.
Do not silently replace D-227/D-228 or implement F-02's advance-to-A recommendation.

**F-05 is resolved:** ending suggestions never group with anything, including
preceding taps/closures/random runs or following pings/comments/tracking. Strict
singleton S groups let group catch-up show the final board before the acceptance
dialog; receipt must not cover earlier unreviewed play with it. Sync stays immediate.
Acceptance/return is still separate and non-undoable, with no extra confirmation.
The category matrix is now 182 cells and GM-01–GM-25; T-603/T-604 and the real-app
E2E plans cover singleton boundaries and dialog ordering. No executable tests/app
exist. F-03/F-04/F-06 still have derived repair work; no implementation is authorized.

## Earlier phase/autonomy direction — D-256

The owner wants the specification phase to seek **all possible inconsistencies**
so the eventual implementation loop can proceed autonomously to the agreed end.
Review systematically across tiers, operations, feature composition, lifecycle,
concurrency, failure/recovery and test oracles; do not stop at this review's list
or leave concrete ambiguities to coding. Aim for exhaustive coverage, not a claim
that every possible defect has been disproved. Retract/correct mistaken findings.

After the design gates and explicit implementation authorization, the loop should
run through the whole approved scope without routine per-slice approval or
continuation prompts. Implement/test/review/fix/validate/save, then proceed to the
next approved slice until the agreed completion criteria hold. Resolve derived
specification and constrained technical details autonomously; escalate real owner
choices/blockers rather than guess, continuing independent approved work safely.
See [the delivery loop](delivery-workflow.md#85-later-specification-driven-implementation-loop)
and AGENTS.md. **No implementation/stage transition is authorized now.**

The owner next requests a walkthrough of the remaining findings. F-01 is withdrawn;
F-02–F-06 are not approved by this process instruction. Present concrete outcomes,
sources and derived resolutions separately from any genuine remaining choices.

## Earlier review direction — clarity for later implementation, D-255

The owner explicitly welcomes finding and correcting ambiguities: the eventual
implementation will use a long loop, so clear specifications matter. Do not read
the F-01 correction as an instruction to stop reviewing or dismiss unclear wording
merely because its intended policy is settled. Correct documentation defects and
make derived outcomes explicit; distinguish them from actual undecided behavior
requiring owner input. D-237's no-redundant-policy-question rule remains. This is
also recorded in AGENTS.md, not approval of remaining review proposals, application
code or a delivery-stage transition.

## Earlier owner correction — server events and grouping, D-254

The owner's response concerns **the ping finding F-01**, not every finding in the
follow-up review. UI actions send requests; the server creates events, which are
grouped by deterministic logic. The assistant wrongly extended menu target binding
into authoritative group binding. F-01 is withdrawn, not another owner question.

The grouping/annotation/composition and sync contracts now make group membership
derived output, never a menu-bound request parameter or stale-group guard. Preserve
D-248's target IDs, explicit Ping/Unping semantics and historical references without
using them to assign a new event's group. T-602 and its E2E variant cover an
intervening tablet event; no executable tests/app exist. Other findings remain
unselected. No UI/implementation/stage transition is authorized.

## Earlier October 20 review repairs — D-251–D-253

**Subsequent review, not new decisions:** the owner requested another adversarial
consistency/unspecified-area review. The
[post-repair report](adversarial-review-2026-10-20-follow-up.md) records F-01–F-06
against `57f6a75`: captured ping scope versus current recording event, Redo's shared
pointer at an activated insertion boundary, delayed exposure after tree revision,
mixed-token ordered lowering, pending-result grouping and residual active drift.
F-01 was subsequently withdrawn under D-254 above. Other proposed resolutions are
not selected behavior; attempt derivation under D-237 before asking about any
remaining choice. The original review batch changed no contracts/oracles.

The owner discussed S-01–S-10 in the
[October 20 review](adversarial-review-2026-10-20.md#documentation-follow-up-after-owner-discussion).
Read that disposition and the current primary contracts; older handoff sections
below retain historical context and are not a current question queue.

- D-251 **replaces D-250**: first tap after substantive play starts a new group.
  Further taps/pings stay in a taps/pings-only group; its following anchor joins.
  Another tap after that anchor starts a new group, even between matching moves.
  Use the [category contract](../design/03-contracts/history-grouping.md) and
  [full planned matrix](../design/05-verification/grouping-matrix.md): now 182 transition
  cells plus key/content variants, 25 longer traces after D-257, and every-prefix ping/mode
  assertions. Later owner-approved playtest tuning updates these centrally, not
  an in-app manual group editor or an implementation-stage transition.
- D-252 revises blanket covered-hold logging: own-area hold shows real faces with
  no event; opponent holds do not disclose hidden information. Context **Inspect
  other side** logs viewing without flipping; **Flip** changes shared presentation.
  Own-area downloaded holds are read-only offline, while new logged inspection
  still requires a connection. Existing printed-face/cover distinctions remain.
- D-253 corrects full-path parity: **individual constituents** need full paths.
  Combined shortcuts can be quick-only, including advance-and-close. Do not
  recreate the assistant's out-of-context combined Next-step recommendation or
  require a Cleanup meaning; confirmed Library-context mulligans remain.

Derived completion is centralized in
[operation composition](../design/03-contracts/operation-composition.md): one
execution state before guards, preparation-round fencing, current-state Reset/
Clone/whole-Library Shuffle reads, ordered individual lowering and bound-side
composite scope/interleaving. The
[reference matrix](../design/03-contracts/domain-model.md#28-reference-lifetime-and-reachability)
separates live cleanup, historical absence and unreachable token-Library-tray cases.
Exposure-only events explicitly participate in latest-own barriers/Redo clearing,
recording-context filtering and replay work, without an automatic gameplay branch.
Protect actual logged exposure captured online before transmission across Logout,
not merely requests with a sent flag. Ordinary private downloads still clear.

T-579–T-601, corrected stable older T-IDs and full-stack journey variants specify
the adversarial outcomes. No executable app/tests have been created or run.
Representation, budgets, UI geometry, provider/privacy/service work remain in
their lanes; ask only about a demonstrated new product gap. Validate and save
each completed documentation batch under D-178. No implementation is authorized.

## Earlier review repairs — D-245–D-250

Owner discussed the October 19 adversarial review and corrected the proposed
residual-choice batch. Read the
[review disposition](adversarial-review-2026-10-19.md#documentation-follow-up--2026-10-20)
and current primary contracts rather than treating original review proposals as
selected behavior. All R-01–R-15 now have documented outcomes/technical boundaries.

- D-245: personal view position never affects Undo eligibility. Latest-own target
  remains on the shared current timeline; validate all retained successors even
  beyond an explicitly activated internal shared node. Any fork at/after the
  target blocks removal; unchanged relevant tuples and hard N still apply.
- D-246: offline use creates no game events, including new covered/other-face
  inspection requiring exposure logging. Recorded downloaded visible content
  remains readable. No deferred offline exposure queue. Actual online exposure
  survives a rejected prediction/restart; capture it before display and recover
  already-initiated records without creating automatic gameplay branches.
- D-247: Stack comparison concerns ordered current real-card/effect-entry
  identities, including independent occurrences/source identities. Not rendered
  source fields, zone, live/frozen status, links, layout coordinates or personal
  presentation. The assistant's extra frozen-status/link guards were not selected.
- D-248: selected context card binds captured selection; unselected card binds
  only itself while preserving selection. Capture scope when menu/editor opens;
  no redirection after selection change or disappearance. Same accessible rule.
- D-249: trimming was for card text/text-value controls, not universal credentials/
  account/deck/import/message normalization. Preserve passwords' whitespace and
  Unicode consistently through registration/login/reset; other security policy
  remains separate.
- D-250: intervening taps never create an additional group split. Apply existing
  anchor and forward/backward rules; no new companion policy/manual editor.

Derived repairs include target-transition fidelity before Redo suffix checks,
invariant-closed mandatory cleanup/capture-first token disappearance, preflight
versus individual bulk conflicts, source/occurrence normalization, separate
recording/reference annotation contexts, fresh-play incarnation recovery fencing,
and cost accounting beyond suffix count. Old normative Undo/scope/test drift was
corrected directly, not merely covered with a precedence note. T-554–T-578 and
E2E journey variants record adversarial outcomes; no executable tests/app exist.

Continue remaining contract/technical/UI completion in its proper lane; ask only
about a demonstrated new gap. **Documentation only: no implementation, UI drafts
or delivery-stage transition is authorized.** Validate and save any subsequent
edit batch under D-178 as usual.

## Latest test/data direction — D-243/D-244

Owner requires dynamic game data for licensing reasons and dummy data/art in
automated tests. P-056 and the
[dynamic-data boundary](../design/04-technical/architecture-and-operations.md#dynamic-game-data--d-243)
keep definitions/metadata/images replaceable outside app/backend code, through
the same normal loading path. Preserve retained essential game data/per-face
defaults and manual-play semantics; this does not decide production rights or
select a provider.

Owner requires full E2E scenarios with a running real backend and the normal
Android app, backend/user state reset between tests, user/deck initialization
convenience calls allowed, otherwise real UI actions only. The
[test strategy](../design/05-verification/e2e-strategy.md) records that boundary,
setup-only users/saved decks (not contacts/lobbies/history), both players driven
through Android UI, isolation/reset design, synthetic catalog/art coverage and
initial complete journeys. T-549–T-553 cover data-path and harness obligations.
Restarts/reconnects within a test preserve its state; reset before the next
independent test. Lower-level tests supplement, not replace full-stack acceptance.
Framework, endpoint/reset implementation, CI/device matrix and external-service
harnesses remain technical design. **Documentation only; no application code,
test harness implementation or delivery-stage transition is authorized.**

## Latest Stack source/inspection decision — D-242

Owner accepts last-live inspection but corrects the proposed boundary: **only
token disappearance unlinks/freezes a Stack projection** (T-545–T-548). An
existing real source remains linked wherever it moves, including Graveyard and
Exile; ability projections can be created from those zones too, leaving the source
in place. Do not freeze on Battlefield departure or at effect creation. Source
edits/write-through and normal destination resets stay live while the source exists.

When a token disappears through Remove or a configured destination, retain its
last live displayed/inspectable card state as read-only source information for
surviving Stack effects. No replacement actual card/Clone, editing the snapshot
or editing the nonexistent source. Preserve source identity/provenance, cover/
inspection conventions and recorded history/retry outcomes. Effect occurrences
remain independently removable. Combat stays linked and cleans up on source
departure from Battlefield. No automatic card-rule calculation is approved.

The previously last identified product gap in the pruned batch is now resolved.
Continue contract/test completion and consistency work automatically; ask only
about a demonstrated new missing outcome/contradiction. Privacy/service and later
UI/technical work remain in their lanes. **No stage transition or implementation
is authorized.** The all-Stack-creation-snapshot and freeze-on-ordinary-departure
proposals were not selected.

## Latest remaining-gap batch — D-241

Owner said **otherwise good**, accepting the batch except inspection, for which
they requested a concrete gameplay scenario. Record the acceptance, not approval
of the inspection proposal. Confirmed outcomes (T-541–T-544):

- No dedicated event-comment Edit/Delete; use ordinary eligible Undo or another
  comment, without bypassing replay/dependency or other normal checks.
- No trash/restore for deleted saved decks or cancelled lobbies. Selected deck
  copies and current-game branch restoration remain intact.
- Explicit Logout removes private downloaded content but preserves protected
  recovery records for already-sent requests; never expose one account's data to
  another. Server retention/account erasure remains privacy/service design.
- Exclude remaining proposed authoring/coordination extras from R1: custom-piece/
  layout editors, fuller deck editing/export, manual group editing, branch
  comparison/renaming, response requests/read receipts, presence, extra areas/
  formats and additional alert categories. Preserve confirmed features and prior
  D-214/D-220 deferrals. No expansion roadmap or stage transition is implied.

At that checkpoint source-disappearance inspection remained undecided; D-242
above now settles it. Do not manufacture extra owner questions from contract/
test, technical or later-UI work. Continue automatically in reviewable batches;
the owner should not need to prompt for each item. No implementation is authorized.

## Latest chat scope — D-240

Owner selected **simple scope for chat, no planned expansions** (T-540; updated
T-184). Submitted game-wide messages have no Edit/Delete or gameplay Undo;
corrections use another message. Do not treat editing/deletion as deferred or
add a chat expansion roadmap. This answer specifically names chat: preserve
event-comment ordinary Undo without silently approving or excluding dedicated
comment-revision tools. Draft editing, pending delivery and account erasure/data
retention remain distinct. The gap list and Q-26/Q-31 separate settled chat scope
from unapproved event-comment tools. No implementation or stage transition is authorized.

## Latest result-action race correction — D-239

Owner rejected the assistant's competing-result-suggestion question as **not a
gap**: actions that cannot both apply follow normal server processing order;
the first applicable action takes effect and the later incompatible request is
discarded (T-538/T-539). Do not implement the assistant's latest-suggestion-replaces
proposal, keep several competing pending suggestions or infer acceptance from a
second suggestion. Existing other-player acceptance remains required. Acceptance
of a removed suggestion cannot apply. This applies D-184/D-187, not a new blanket
conflict lock: compatible actions still apply normally. Delivery retries preserve
the original outcome. The pruned gaps and Q-10 now classify this as derived behavior,
not another owner choice. Do not manufacture the next question from an old label.
No implementation or delivery-stage transition is authorized.

## Latest deleted-node review decision — D-238

Owner confirmed: **if you are viewing a node that gets deleted, you fall up to
its parent** (T-535–T-537). Remain in personal review, not a detached old snapshot,
a jump to the live head or a new branch. Repeat the rule if the parent is also
deleted; coalesced deletions resolve to the nearest surviving ancestor/root.
Cursors on surviving nodes stay there. A personal cursor neither blocks eligible
Undo nor adds a shared mutation when falling back. Exact IDs/version/cache
handling remains technical work. The pruned gaps and Q-05/Q-31 now mark this
outcome settled; D-240 above also settles chat correction scope. Dedicated
event-comment tools and the other genuine gaps remain separate.
No implementation or delivery-stage transition is authorized.

## Latest gap-pruning instruction — D-237

Owner rejected repeated questions whose answers follow from agreed rules and
requested pruning plus a standing instruction: **look only for true gaps/edge
cases**. Read the [pruned gap list](../design/02-experience/gaps.md) and
[classified Q-register](decisions.md#93-release-blocking-questions), not old
"open" inventories as a next-question queue. Establish a concrete missing
outcome/scope choice or a genuine ambiguity/contradiction after applying current
rules. State why those rules do not decide it before asking. Record derived
consequences and tests without requesting fresh approval; keep technical and
later UI decisions in their own lanes. A later comment referring to a move
already blocks removing that move under D-224 (T-534); do not reopen it.
Owner further requested the efficient review format: **batch identified edge
cases with their spec-derived resolutions and sources**, then allow revision or
acceptance together. Do not turn that review into independent approval questions
for already-derived consequences. Mark genuinely unresolved choices/proposals
separately from existing-spec outcomes.

Core Undo/Redo needs contract/test completion, not another fundamental policy
question merely because a schema or field mapping is unwritten. Remaining real
product choices are listed with concrete examples; scope proposals remain
unapproved, and the pruning does not authorize a stage transition. The usual
continue-with-the-next-question convention applies only when a true gap has
actually been established. Otherwise complete/audit the specification or report
that no new owner decision was found. This instruction is also in AGENTS.md.

## Latest server Undo/concurrent-device correction — D-236

Owner rejected the assistant's apply-once/discard-the-other-device proposal:
**Undo is an Undo request to the server; two requests are like pressing Undo
twice** (T-531–T-533). Each distinct request targets the then-current latest own
action in server arrival order under ordinary eligibility. If both successive
candidates are eligible, remove both. Do not pin requests to the clients' same
seen node or suppress the second as stale. A blocked/absent next target makes no
further removal; never skip it. Delivery retries of one request still recover
one original outcome, not another Undo. This supersedes D-161 fixed-node stale
Undo assumptions for the current selective request design.

Concurrent same-account gameplay must work using normal ordering/acceptance;
special multi-device UX is not a priority. Do not introduce exclusive-device
sessions, collision confirmations or routine acknowledgement barriers. Move on
from tiny device-UX cases to remaining historical references/cursors, relevant
state or insertion anchors. Other control races and protocol details remain open.
No implementation or stage transition is authorized.

## Latest cross-device Undo/Redo scope — D-235

Owner approved synchronized Undo/Redo stacks across devices, adding **it is shared
game state** (T-529/T-530). Each account has its own stack per game, not a local
device stack or one stack shared between opponents. Undo on phone can be Redone
on tablet under current checks; latest-own targeting spans the account's devices.
Successful Undo/Redo and own-new-play clearing synchronize between them. Opponent
actions do not clear your stack. No transfer of pending-request queues, drafts or
review cursors is implied. D-236 above settles concurrent Undo requests and
rejects the assistant's same-target stale-suppression proposal. Exact sync timing
and other control races remain open. No implementation or stage transition is authorized.

## Latest Redo fork boundary — D-234

Owner approved blocking Redo reinsertion before a newly created fork while
retaining the stack, and explicitly observed that the historical mutation then
has no way to be applied again with current controls (T-527/T-528). Accept this
limitation, not a requirement for recovery controls. Do not skip/discard the entry,
retarget it to the current head, rewrite all branches or create a recovery branch.
Opponent branching still does not clear the queue; own new gameplay still does.
A similar fresh action is not restoration of the historical mutation. Exact
anchors and archive/activation integration remain open, not promised escape hatches.
D-235 above settles same-account/device stack scope. Historical references
and review cursors affected by otherwise eligible linear edits remain open. No implementation
or stage transition is authorized.

## Latest Undo fork boundary — D-233

Owner chose **no Undo of a branching point**, then added **or past one**, for
simplicity (T-524–T-526). If the latest own target itself or any node between it
and the current active head has multiple children, disable Undo even if replay
would be safe. Never skip the target or reattach/replay alternatives to permit
removal. Explorer filtering does not change the boundary. Actions strictly after
the latest fork still use the normal latest-own/replay/exposure/N checks.
This settles Undo's side-branch handling; D-234 above settles Redo across a new
fork while an entry is queued. Archive/restore effects, explicit activation
integration, historical references, cursors and concurrency remain open.
No implementation is authorized.

## Latest tree-edit correction — D-232

Owner rejected the assistant's proposal to create a revised continuation beside
an untouched original: **Undo/Redo actually edit the tree, not create branches**
(T-522/T-523). Eligible Undo of A on root → A → B produces root → B; eligible Redo
reinserts A into that tree. Labels describe logical mutations, not fixed node IDs.
Do not keep the pre-Undo path as an explorer alternative or duplicate the suffix
as a new branch. Removed mutations are available in the personal Redo stack.
Clearing it does not restore the removed node; storage retention/recovery after
clearing remains open. Older immutable-tree/original-path-retention/automatic
post-Undo-fork requirements are superseded for these controls. Genuine explicit
interruption branches and D-223 filtering of actual alternatives remain distinct.

D-224 safety/N, D-231 latest-own targeting and D-227–D-230 strict stack/clearing
rules remain. D-233 above now blocks Undo at/before a fork. Comment/ping references,
review cursors, node IDs/versioning, caches and concurrency still need contracts. Do not
silently delete unrelated branches/comments, retarget historical references or
select a physical-storage rewrite scheme. The owner rejected the assistant's
proposal to reattach and validate all affected branches; do not reopen that
settled Undo case. No implementation is authorized.

## Latest strict Undo/Redo target order — D-230/D-231

Owner approved **disable Redo on a blocked next entry, keeping the stack intact**,
and added **only the most recent action can be undone** (T-518/T-519). D-231
clarifies **your most recent still-applied action, even after later opponent
actions** (T-520/T-521). Never target the opponent's action or fall back to it
when no own action remains. Later opponent mutations stay applied and must pass
D-224's unchanged relevant-state replay checks within N. Never skip
a blocked Undo/Redo target to reach an older eligible entry; no arbitrary earlier
Undo picker. D-224's replay/exposure/N rules remain. Successful Undo exposes the
preceding own candidate. Latest-own scope is settled; do not reopen it or revert
to author-independent parent-pointer-only Undo. Personal review and explicit
historical activation remain separate; integration with activation is still open.
Insertion anchors, device synchronization, concurrency and affected-history handling
remain open. No implementation or delivery-stage transition is authorized.
D-232 above settles editing the existing tree, not preserving an untouched original.

## Latest Redo order — D-229

Owner approved **last-undone-first Redo**, like a normal Undo/Redo stack (T-517).
Undo A then B means successful Redo B then A, following Undo-operation order,
not original timeline sorting. Each reinsertion still passes D-224's checks/N;
D-227/D-228 clearing rules remain. Insertion anchors remain open. D-230 above
settles the blocked-next-entry case: disable Redo and retain the stack, without
skipping/discarding entries.
No implementation or delivery-stage transition is authorized.

## Latest Redo queue invalidation — D-227/D-228

After Undo, **only your own new gameplay edit clears your Redo queue**, even
if the removed mutation could still be safely reinserted (T-514). The owner chose
clearing over retaining it. D-232 does not require an original explorer path or
retention of removed records after clearing; that storage policy remains open.
Confirmed case is a successful new gameplay mutation by the
player who used Undo. D-228 confirms opponent actions **do not clear your queue**
(T-515/T-516). Retain entries and apply current D-224 replay checks/N; an opponent
action may block reinsertion without clearing the queue. Clearing is player-specific,
not a shared game reset. Same-account/device queue scope/synchronization,
rejected/no-op requests and exact optimistic/server clearing timing remain open.
Do not infer that personal review/navigation or Redo reinsertion itself clears
remaining entries. D-229 above now settles normal last-undone-first order;
insertion anchors remain open, while D-230 settles an unavailable next entry.
No implementation is authorized.

## Latest container-order clarification — D-225/D-226

Owner approved independent Graveyard arrivals not blocking selective Undo,
explicitly because **Library is ordered; Graveyard is unordered** (T-510/T-511).
D-226 extends unordered behavior to Exile and confirms **no order-related actions
for Graveyard/Exile**, including top/bottom insertion/selection, reorder, shuffle,
or visual-placement variants (T-512/T-513). Ordinary moves into each have no
insertion position; moves of specific cards from either into Library can still
choose Library top/bottom. Personal type/name browser sorting is presentation,
not a container-order action. Older all-three-piles top/bottom wording is superseded.
Relevant state includes order dependencies for top-of-Library draws: never draw
a different card or silently take the recorded target from beneath another card.
Independent Graveyard/Exile arrivals do not depend on unrelated contents/absolute
indices. All other D-224 checks, exposure restrictions and N still apply. Exact
collapsed-pile presentation and other-container ordering remain separate. Do not
reopen the resolved Graveyard/Exile controls or add MTG rules enforcement. Next
useful topic: continue mutation relevant-state cases, then Redo insertion anchors
and queue behavior; do not settle remaining controls without owner approval.

## Latest selective Undo/Redo safety rule — D-224

The owner reopened pointer/control design and explored own-action selective Undo,
without compensating actions. Latest confirmed rule (T-501–T-509): **a mutation
may be deleted/inserted only if every subsequent mutation has exactly the same
relevant-state `(from, to)` tuple in the original and candidate timelines**.
Define relevant state per mutation and compare both endpoints of every later
transition, not equal deltas, replay applicability or just final board equality.
Owner examples: same-counter +1 then +1 blocks removing the first; move then tap
of the same card, and tap A then untap B, allow it when otherwise eligible.
This strict replay test is **not D-187 live action locking**; do not revive the
superseded 25-rule table or append a compensating mutation.

Use a **hard limit N on the suffix replay/check work** to prevent a single own
move followed by e.g. a thousand opponent actions causing slow eligibility.
Exact N/counting unit remain open; never validate a prefix and accept an unchecked
tail. The preceding selective design excludes information-revealing Undo targets.
Tree replay reuses recorded outcomes, not new random draws/UI revelations.

Primary contract: [selective Undo/Redo](../design/03-contracts/history-and-undo.md#selective-mutation-undoredo--d-224).
This revises the earlier universal parent-pointer-only/remembered-head model;
older fixtures/sections now describe rewind context, not settled selective-Undo
behavior. D-230/D-231 above settle latest-own/no-skipping selection. Insertion
target preconditions, Redo queue device scope/anchors, pointer/view-follow/control mapping, pending
requests, concurrency and node/reference/grouping rewrite remain open. The owner
asked to be drilled about cases, not to finalize controls prematurely. Next useful
topic: define relevant state for mutation kinds, starting with move versus tap
and ordered-pile interactions. No UI drafts, code or stage transition are authorized.

## Earlier branching clarification — D-222/D-223

Owner selected branching as the next topic and replaced the assistant's
one-child/mandatory-fork-choice Redo proposal. **Root contains initial state;
mutation nodes form a tree. Undo remembers the last active head; Redo follows
toward it.** Multiple Undo presses retain the destination, even with other
children. Changed play/interruption after Undo creates a new active continuation;
retain the old one and its recorded outcomes. A **graphical tree explorer** permits
review and explicit activation of **any retained branch/node**, including internal
nodes/root; mere review never activates shared state (T-493–T-499).

Hide inactive continuations only when they contain **one player's actions and
no hidden-information exposure**, behind a separate explorer toggle, **unless
they contain an event comment**. **D-223 confirms the event-comment exception**:
keep the whole continuation visible even with one author/no exposure (T-500 and
corrected T-186/T-292/T-323/T-354/T-499). Mixed-player/exposure/event-comment paths
stay visible in full, including ordinary tails. The old blanket undone-node
retention requirement is superseded by D-232 tree editing; do not create an
original alternative solely to satisfy this earlier rewind description.
D-224 above now takes precedence for selective Undo/Redo; earlier remembered-head
scope does not settle the new reinsertion queue. Do not reopen the confirmed comment exception.

Primary contract: [history section 3.0](../design/03-contracts/history-and-undo.md#30-current-undo-model--parent-pointer-navigation-d-159d-160).
Remembered-head user/device scope, updates after explicit activation, concurrent/
stale Redo/activation, nested/pre-fork presentation and archive/restore details
remain open. D-161 stale Undo and D-179 deliberate intent remain; no offline
mutations, finished-game archives, implementation or stage transition are authorized.

## Latest connection-loss presentation and recovery — D-215/D-216

The owner specified a **Reconnecting... dialog when disconnected**, then clarified
that it **blocks gameplay actions** (T-475/T-476). D-182 already requires a
connection for gameplay/shared Undo/branch controls; do not create offline actions
behind the dialog. Preserve downloaded read-only access and D-183's normal online
responsiveness: waiting for an acknowledgement alone is not a reason for this
dialog. D-216 then separately approves **automatic recovery of already-sent actions
after reconnect**, without manual resending or executing anything twice
(T-477–T-479). Preserve original request identities/conditions, recover actual
outcomes and reuse recorded committed/random results. D-185 difference-only
reconciliation and D-161 stale Undo remain; do not retry rejected requests as new
actions, cancel whole queues or permit offline gameplay. Exact navigation/
dismissal, readiness, outcome lookup/retry protocol, recovery ordering and
online-buffered-but-unsent handling remain open, not the automatic recovery policy.

## Latest session continuity — D-217

The owner approved **staying logged in across app restarts** and, if the session
expires, **asking for login again, then returning to the same game and recovering
pending actions** (T-480–T-482). Preserve same-account game/request context and
D-216 automatic already-sent recovery, without manual resend/duplicate execution;
expiry alone is not rejection. Primary contract: [session continuity](../design/03-contracts/accounts-decks-and-lobby.md#session-continuity-and-reauthentication--d-217).
Exact session lifetime/refresh/storage, failed/cancelled reauthentication, account
switching, Logout's local-data behavior and expired-session offline access remain
open; downloaded read-only access remains required.

## Latest multiple-device support — D-218

The owner approved **the same account on multiple devices, with saved decks,
contacts and games synchronized between them** (T-483–T-485). Primary contract:
[account data](../design/03-contracts/accounts-decks-and-lobby.md#multiple-devices-and-account-data--d-218).
Preserve saved-deck/lobby/game-copy isolation, existing games/participants and
connected-only/no-duplicate recovery contracts. Exact simultaneous same-account
editing, concurrent deck/contact conflicts, device/session revocation, sync timing
and personal-device-state transfer remain open. Do not infer transfer of drafts,
local pending queues, review/navigation cursors, settings or all cached downloads.
No implementation or stage transition is authorized.

## Latest notifications — D-219

The owner approved **push notifications for new invitations and grouped opponent
activity, without revealing covered card names** (T-486–T-488). Primary contract:
[notifications](../design/03-contracts/android-experience.md#59-notifications).
Consolidate related activity rather than one push per primitive mutation; do not
delay immediate action sync or require a group-completion tap. Exact cadence,
permissions/defaults, channel categories, foreground suppression and multiple-device
routing remain open, as do additional dedicated branch-switch/response-request
alerts and unread/read behavior.

**D-220 (T-489/T-490):** the owner deferred **both in-app global and per-game mute
settings to post-R1**. **R1 uses Android notification channel settings**; respect
those choices without an app-specific mute/settings screen. Do not defer D-219
pushes, treat the settings as an R1 gap or infer one Android channel per game.

## Latest saved-deck deletion and editing isolation — D-221

The owner approved deleting a saved deck from the collection while preserving
copies already selected in lobbies/games, then added **editing works the same
way** (T-491/T-492), reaffirming D-199. Keep game-local preparation and active play;
no replacement deck is required merely because its saved source was deleted.
Primary contract: [saved-deck isolation](../design/03-contracts/accounts-decks-and-lobby.md#saved-deck-deletion-and-copy-isolation--d-221).
Exact deletion confirmation/recovery/retention, backup erasure and concurrent
delete/edit/select races remain open. Do not infer trash/restore, gameplay Undo,
full manual deck editing or deletion of in-game cards/history. The owner has now
chosen branching next (D-222 above), not more account/deck/settings questions.

## Current concurrency correction — D-179

**Latest setup decisions — D-188–D-214:** owner requires login before the main
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
automatic starting shuffle is now confirmed by D-200 below. See T-406–T-413.
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
See T-428–T-432. Naming defaults/duplicates, deletion safeguards/recovery/retention/
concurrent races (D-221 settles its scope/isolation), cover fallback/re-import
metadata retention and remaining dialect/extra-section/printing/count/resolution
details remain open. D-200 corrects the next setup proposal: automatically shuffle
Libraries on game start; each player starts at 20 life, with no pregame life
editability required now. Preserve in-game life edits and no automatic draw.
Shuffling is a game-start operation, not every Untap entry/resume; retries reuse
recorded orders. See T-433–T-436. D-201 separately approves choosing the starter in
the lobby, offering either player or Random; start turn 1 at Beginning/Untap with
that starter and retain random outcomes for retries/review (T-437–T-439). Defaults,
edit access and random timing/reselection remain open. D-202
approves initial placement specifically in the lobby: mainboard → Library,
sideboard → separate Sideboard, all other card areas empty. Owner explicitly
excludes Sideboard during play: no area/browser/view/edit/move access, not merely
covered/collapsed. Use the prepared mainboard on start; do not merge remaining
sideboard cards into Library or delete the saved sideboard. See T-440–T-443.
D-203 explicitly confirms free card transfers between mainboard/sideboard in the
lobby, without legality, size or one-for-one constraints. Keep own-Ready clearing
and saved-deck isolation under D-191; see T-444/T-445.
D-204 corrects the assistant's next proposal: starter-selection changes clear
only the changing player's Ready, preserving the other's. Do not clear both;
the changing player must ready again (T-446/T-447). D-205 separately approves
all four lobby-transition recommendations: Ready requires your own selected deck
without legality/size checks; manual withdrawal is allowed until start; accepted
Ready persists when leaving/disconnecting without both players online; server
arrival order decides start-versus-edit/withdrawal, and retries never start or
shuffle again (T-448–T-454). Apply a lobby change received first before checking
readiness; reject late lobby changes after start without altering/reopening play.
This is not offline Ready creation or a simultaneous-presence requirement.
D-206 corrects the next proposal: leaving the lobby and cancelling the game are
separate actions. Leaving alone must not cancel; retain D-205's Ready persistence.
Do not implement the assistant's leave-means-cancel recommendation or treat the
other proposed invite expiry/cancellation/listing/reinvite defaults as approved.
D-207 separately approves either player cancelling an unstarted game without
the other's approval (T-456). Do not restrict this to the inviter or require an
opponent response. Cancellation-versus-start races and presentation/retention
remain open, as do pending-invitation lifecycle and started-game completion.
D-208 corrects the invitation model: for the inviter, the invite is already an
open lobby where they can select/prepare their deck and ready before acceptance.
Cancel game cancels the pending invite. The owner's follow-up explicitly says
recipient Decline does not destroy the lobby: retain it and inviter preparation/
readiness, and allow reinvitation to the same lobby (T-457–T-460). Acceptance
opens that lobby for the recipient, not an automatic Ready or game start. Do not
require acceptance before inviter preparation or turn Decline into cancellation.
D-209 separately approves all three latest defaults: pending invites never
expire automatically; cancelled games leave current games; declined lobbies stay
available there to the inviter (T-461–T-463). List removal does not decide deletion,
archives or restore guarantees. Duplicate handling, recipient-side decline
presentation/feedback, cancellation races and retention/archive behavior remain
open; earlier unapproved details are not silently adopted.
D-210's initial confirmed/non-undoable End game was revised immediately by D-211:
**suggest your win/loss or a draw as undoable board state; the other player must
accept, then both return to lobby** (T-464–T-468, corrected T-025). No win-history/
scorekeeping feature is required; the purpose is shared understanding. Do not
keep D-210's blanket Undo prohibition, unilateral confirmation, or assume an
additional dialog. Suggestions use normal mutation/Undo. D-212 separately confirms
**acceptance/return to lobby is not undoable** (T-469, corrected T-025/T-467).
Do not reopen ended play through Undo or apply non-undoability to pending suggestions.
This is a specific U-001 exception, not a revival of D-210. D-213 approves all
three return-to-lobby recommendations: restore last lobby-prepared decks and
mainboard/sideboard choices, clear both Ready and require both players to ready
again for a fresh game (T-470–T-472). Use game-local preparation, not final
gameplay state or updated saved decks. The fresh start uses ordinary setup,
without automatic opening draw; do not auto-restart on previous readiness.
D-214 defers the proposed finished-game action-history archives/read-only review
to **optional additional development, not R1** (T-473/T-474). Keep it as future
scope, not an R1 blocker or implementation commitment. Current-game history,
retained branches, Undo, exposure/comment preservation and downloaded read-only
access are still required. Do not interpret the cut as deletion authorization or
a permanent-retention promise. Cross-game starter-selection retention/random
timing remains open; D-239 above settles incompatible result-action races through
normal ordering/applicability. D-215/D-216 settle
disconnected gameplay blocking and automatic recovery of already-sent actions;
exact recovery mechanics remain R1 design work; D-217 settles session persistence
and same-game return/recovery after expired-session login, not
finished-session history variants. Narrower invite
races/presentation and additional optional areas remain open. Do not reopen invitation
expiry, starter options, initial placement or tiny cover-art variants.
Exact search/contact removal/privacy, remaining invitation lifecycle, starter
defaults/access/random timing, lobby/game history, account flows and other initial values remain open.
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
This earlier filtering summary is refined by D-222: hiding requires a single-player/
no-exposure continuation; mixed-player/exposure paths stay visible, and the older
comment exception is explicitly confirmed by D-223. D-176 subsequently corrects
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

**Historical D-242-era working agreement:** the stage/no-code statements in this
section are superseded by D-304's current authorization above. Retained for provenance,
not a prohibition on the authorized implementation loop.

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
  After recording an answer, continue only with a demonstrated true gap/edge case
  (D-237), not another consequence of approved rules. There is no question quota.
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

- Current documentation includes **D-001–D-242** and **T-001–T-548**.
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
  source selection, record mappings and concurrency need contract completion;
  D-242 settles missing-token-source behavior.
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
  departure. D-242 keeps existing sources linked across locations, including
  Graveyard/Exile, and preserves read-only last-live state only on token
  disappearance. No source-card edits apply to the frozen copy/nonexistent token;
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

- **Q-33–Q-35 / G-11–G-13:** remaining combat role-change/entry mappings, D-242's
  recorded Stack token-fallback context, duplicate-source multiselection,
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
- Undo moves the shared pointer one parent back, regardless of author, remembering
  the last active head for Redo (D-222). Retain all descendants; hide only single-player/
  no-exposure inactive continuations behind the explorer toggle. Mixed-player/
  exposure/event-comment paths stay visible in full (D-223).
  Reviewing does not activate; explicit graphical-tree activation can select any
  retained node/branch. Submitted game-wide Chat is not undoable (D-94); event
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
