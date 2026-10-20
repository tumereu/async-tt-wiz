# 1. Product contract

## 1.1 Intent

Build a generic, rules-agnostic board/card-game engine with a native Android
client. Make it practical for two cooperative people to play without being online
at the same time. Manual Magic: The Gathering is the initial use case, not the
engine's domain model. Preserve the freedom of a shared tabletop while making
asynchronous play understandable and mistakes easy to repair.

Players interpret card text, pings, area labels, and the sequence of physical
actions. The software records those facts literally. It must not infer that a
move into an area named “Stack” casts a spell, that a ping declares a legal target,
or that a later move resolves anything. This excludes semantic inference, not
merely automatic rules enforcement.

D-112–D-114 add explicit combat assignments, virtual card projections, and
contextual Stack actions. These are required manual-play conveniences to design
case by case, not automatic interpretation of card text or generic moves.

## 1.2 Confirmed requirements

| ID | Requirement |
| --- | --- |
| P-001 | The gameplay client is native Android. |
| P-002 | Asynchronous play is a primary workflow, not an added turn timer. |
| P-003 | Individual mutations form sensible navigation/playback groups. One group is one step animated as the previous-board → group-end-board difference, concurrently across affected objects/fields, not queued same-card waypoints or one tree node (D-176/D-177/D-277). |
| P-004 | An opponent can interrupt at an action boundary and react. |
| P-005 | History is a tree, with exactly one active branch in each game. |
| P-006 | An interruption makes its new branch active; no approval vote is required. |
| P-007 | Players can delete the interruption and return to the earlier continuation. |
| P-008 | Eligible Undo removes your latest still-applied mutation; Redo reinserts last-undone-first with its recorded relevant transition. Preserve every later retained event's relevant (from, to) tuple within N; no branching at/after the target and no personal-view-head dependence (D-245). Revealing/blocked targets cannot be removed/skipped. Ordinary own new play clears own Redo; ordinary opponent play does not. Successful deliberate shared-history activation/interruption/return clears both players' per-incarnation Redo queues atomically (D-258); normal Undo/Redo and UI review do not trigger that reset. Stacks are server-owned shared game state (D-259), not per-device histories. Edit the existing tree without an original explorer copy. Submitted chat and accepted ending/return to lobby have no gameplay Undo. Exact anchor/version/storage/cost mechanics and removed-record retention remain technical/privacy work. |
| P-009 | Either participant can manipulate any participant's tabletop state. |
| P-010 | Hidden information is accessible to everyone; cooperation, not secrecy enforcement, is the trust model. |
| P-011 | The first release fully targets two-player games. |
| P-012 | Initial delivery requires a connection for game actions, including shared Undo/branch controls. Downloaded games remain readable offline, but offline action creation and later queue synchronization are excluded (D-182, superseding D-09's offline-action scope). |
| P-013 | D-115 delivery sequence: complete behavior specification, adversarial review/refinement, UI drafts/decisions, then iterative implementation. We are still in stage 1; no implementation is authorized. |
| P-014 | Cover the manual actions needed to play MTG, rather than a small subset limited to a demonstration game. |
| P-015 | Every individual mutation immediately saves locally and synchronizes automatically; there is no gameplay Send step, bulk/group publication gate or animation dependency (D-176). D-179 determines its accepted parent. D-183 requires a small local online send queue, concurrent transmission and per-action outcomes without routine acknowledgement waits. D-184 requires clients to send in action order and the server to process in arrival order, not reconstruct order from dependency metadata. |
| P-016 | Minimize taps and other user actions throughout the UX. |
| P-017 | Historical branching requires deliberate UI continuation intent and an explicit message flag (D-179); D-232 Undo/Redo instead edit the existing tree without branching. Otherwise apply ordinary actions to current state unless impossible, with optimistic guards limited to movement and Stack additions (D-187). Stack-entry requests include current client Stack state and reject if server Stack state differs. No previous orientation/text/Set checks; concurrency/rejection never creates a branch. |
| P-018 | Drag cards to move them; drops snap to grid locations or slots. |
| P-019 | Cards can form piles with underlying cards peeking out diagonally. |
| P-020 | Normal card mode: single tap opens Actions; double tap taps/untaps without also opening Actions; hold inspects (D-267). Select/Ping retain their own single-tap behavior; the proposed removal of mode-specific double-tap recognition is tentative. |
| P-021 | The board has distinct areas, including special Library, Graveyard, and Exile piles. Library is ordered; Graveyard/Exile are unordered with no order-related actions, even as visual placement (D-225/D-226). |
| P-022 | Game flow is understandable mostly without writing explanations; optional text input and messages remain supported. |
| P-023 | Pings retain specific recorded timeline context rather than floating independently of play. D-168/D-170 clarify that same-author pings add no group-start marker and need no separate owner-action picker; author changes start a new group and effective boundaries remain preserved. |
| P-024 | The engine is generic and rules-agnostic; MTG is a manual-play use case, not hard-coded mechanics. |
| P-025 | Present literal actions and pings as recorded; players infer their game meaning. |
| P-026 | Area names, card metadata, and pings must not imply game-specific state transitions or automatic side effects. |
| P-027 | Turns and MTG phases/steps have first-class engine tracking, not only arbitrary markers, notes, or tokens. D-105 removes priority tracking and its UI from this release, superseding that part of D-96 and all of D-104. Players judge priority/interruption legality; the engine does not gate edits or branches on it. |
| P-028 | Include a temporary combat area with attacker/blocker projections in visual paired-slot groups, without individual attacker↔blocker assignments inside a pair (D-112 refined by D-137/D-139). Projection creation preserves original battlefield positions; later card actions through projections edit/move the same real cards (D-123). |
| P-029 | Name the former Cast area Stack; allow ability projections without moving their originals (D-113). D-242 includes sources in Graveyard and Exile as well as Battlefield and keeps existing sources live-linked across locations. |
| P-030 | Offer contextual Stack quick actions: actual instant/sorcery → Graveyard, actual permanent → Battlefield, projection → remove projection. Design exact behavior case by case before implementation (D-114). |
| P-031 | Stage 1 must cover all agreed behavior, including combat/Stack cases. Stage 2 adversarial review precedes stage 3 UI drafts; stage 4+ implementation is iterative. Do not replace behavior design with UI drafts or technical-schema decisions now (D-115). |
| P-032 | Combat-area creation, attacker declaration, and tapping are separate actions. Phase-appropriate multiselect Declare + tap / Declare without tapping shortcuts also create the area when absent (D-116/D-117). |
| P-033 | Blockers can be dragged to individual attackers or blank unassigned slots; attacker spacing grows with assigned blocker count (D-118). |
| P-034 | Pings show event-wide order numbers (D-119). Links between projections disappear when endpoint projections disappear (D-120). |
| P-035 | D-253 corrects D-121: individual constituent actions retain full paths outside quick windows; combined shortcuts can be quick-only. Preserve explicitly confirmed Library-context mulligan access. |
| P-036 | Removing combat area clears its projections/projection links without changing originals' current state (D-122). Card actions through projections affect an existing real source, including moves to real destinations (D-123/D-242); projections are not independently editable card copies. Only token disappearance leaves read-only last-live source information for surviving Stack effects, not a replacement card. |
| P-037 | A source leaving the battlefield loses its combat projections (D-124), but its Stack effects remain until explicitly removed/resolved (D-125/D-174). D-242 keeps existing sources linked across locations; only token disappearance freezes read-only last-live source state. Lifetime is area-specific, not determined by which view invoked the move. |
| P-038 | When an attacker leaves, surviving cards stay grouped in their existing slots, with real cards unchanged; the final attacker's departure leaves a blocker-only pair (D-126 refined by D-139). D-138 permits visual reflow with stable membership/order, removes fully empty pairs, and inserts a pair on between-pair drops without regrouping survivors. |
| P-039 | Combat area persists until explicitly closed, not automatically on phase changes/End turn. Advance-and-close is an End-of-combat quick-only combination reaching Postcombat main normally; advance and close have separate full paths (D-127/D-128/D-253). |
| P-040 | Each tree node is one individual, deterministically reversible mutation including mandatory invariant-preserving effects. Bulk and combined controls issue ordered individual mutations; groups never determine node granularity or publication. D-176 supersedes combined-node mappings. Each successful Undo removes one eligible latest-own mutation (D-231/D-232). |
| P-041 | Non-token cards do not disappear during the game: they move between locations and have no Remove/delete action, including through bulk, correction or projection paths. Approved token disappearance/removal, projection removal and retained-history Undo remain distinct (D-174). |
| P-042 | D-277 live mode applies remote changes immediately; return from background with unpresented updates enters grouped playback from local presentation context. A displaced path first rolls back to the fork under D-177, then follows new groups. Catch-up or deliberate gameplay continuation returns to live; mere pause/scrub does not. A horizontal bottom timeline still selects exact mutation boundaries, and accepted synchronization never waits for playback. |
| P-043 | Login is required before the main menu; registration is available. Main menu offers Logout, Manage decks and Play (D-188). Users can search by username and save contacts without mutual friend-request approval; Friends label/placement remains tentative (D-196). Support the same account on multiple devices, with saved decks, contacts and games synchronized between them (D-218). |
| P-044 | Saved deck card-list editing is import-only, not a full manual deck builder (D-189). Naming/renaming and choosing cover art are separately supported (D-199). A game lobby separately permits deck selection and mainboard/sideboard management. |
| P-045 | Play lists current games including pending invitations. Invite an added user; the recipient has Accept/Decline in Play, and accepting opens the same lobby for them (D-195). For the inviter it is already an open lobby, allowing deck preparation/Ready before acceptance; Cancel game cancels the pending invite. Decline retains the lobby for reinvitation (D-208). Games start once both players press Ready, not upon creation or invite acceptance (D-190). |
| P-046 | Do not draw opening hands automatically on game start (D-192). Provide return-whole-hand → shuffle → draw N (default 7) or pre-return hand size minus one as Library context actions and quick actions only during turn 1's Untap step (D-193/D-194). |
| P-047 | Register with username, email and password; log in with email/password; reset forgotten passwords by email. Other users see usernames, not account emails (D-197). D-268 requires persistent login without idle/absolute expiry, using a non-expiring revocable token rather than saving the password. Explicit Logout stops automatic login. If the token is unavailable or access explicitly invalidated, same-account login returns to the same game and recovers pending actions without manual resend/duplicate execution (D-217). |
| P-048 | Import common MTG plain-text deck exports rather than a custom syntax, with optional printing details and mainboard/sideboard notation. Flag unknown/ambiguous names without substitution, preserve input and require resolving errors before saving (D-198). |
| P-049 | Save named decks, allow renaming and choose a cover from a deck's cards. Re-import offers Replace or Save as new (D-199). Deleting a saved deck removes it from the saved collection; both editing and deleting preserve copies already selected in lobbies or games (D-221). |
| P-050 | Automatically shuffle each player's Library on game start and initialize each player at 20 life, with no starting-life setting required now. Do not draw automatically; retain normal in-game life edits (D-200). |
| P-051 | Choose the starter in the lobby, offering either player or Random; begin turn 1 at Beginning/Untap with that starter (D-108/D-201). D-274 resolves Random fairly on the server once at actual game start, recording the chosen participant with independent initial Library shuffle outcomes; retry/reconnect never resamples that start. |
| P-052 | Initially in the lobby, mainboard is in Library, sideboard in a separate Sideboard area, and other card areas are empty. Sideboard is unavailable during play, not a hidden accessible pile; play uses the prepared mainboard without merging in sideboard cards or deleting the saved sideboard (D-202). |
| P-053 | Freely move cards between mainboard and sideboard in the lobby without format-legality, size or one-for-one exchange restrictions. Preserve game-local edits and own-Ready clearing; no in-game Sideboard access (D-191/D-203). |
| P-054 | Changing the lobby starter selection clears only the changing player's Ready and preserves the other player's Ready; the changing player must ready again before game start (D-204). |
| P-055 | Provide notifications for new invitations/grouped opponent activity without covered card names or delaying immediate sync (D-219). FCM is optional; periodic server polling is an acceptable best-effort fallback, not a fixed delivery deadline, and app/game synchronization requires neither Google Play services nor Play Store distribution (D-269). R1 uses Android notification channel settings for either path; in-app global/per-game mute remains post-R1 (D-220). Exact cadence/scheduler, permissions/defaults, channels, foreground suppression and multiple-device routing remain design work. |
| P-056 | Card definitions/metadata/art are dynamic data, not bundled licensed assets (D-243); clients download/cache public data (D-270). D-271 confirms lazy images and permits arbitrary client-supplied deck definitions: server validates structure/security, not official-card authenticity, retains accepted snapshots and shares them despite stale/empty recipient catalogs. No mandatory backend image proxy or all-images-up-front requirement. Imports need resolved records, not artwork/latest-catalog parity. Preserve essential game data/defaults/history and server authority; no new authoring UI/executable rules. Provider/update/cache/rights remain separate; startup MTGJSON checks are tentative. Tests use synthetic data/art. |
| P-057 | D-251: a tap starts a new group unless the current group contains only taps/pings; the next substantive action joins that run. D-257 is an explicit exception: ending suggestions are strict singleton groups, never absorbing preparatory events or later companions; show the final board before the acceptance dialog during group playback. Use category rules and a full scenario matrix for E2E planning/playtest revisions, not individual-button rules or a new manual group editor. |
| P-058 | D-252: own-area hold shows real faces with no game event; opponent-area hold does not show hidden information. Context Inspect other side logs viewing without flipping; Flip is a separate shared-state operation. |
| P-059 | D-259: semantic board/history/control, communication and lifecycle state is server-authoritative, changed through requests with provisional local application for latency hiding. Personal non-request UI state is device-local. Replicas, predictions, caches and recovery queues do not create independently authoritative local games; the domain ownership matrix defines the boundary, including logged inspection versus UI-only navigation. |
| P-060 | Android 10+ / API 29 on phones/tablets, including otherwise-supported non-Google devices. Initially distribute versioned signed APKs and update manually through Android's installer, not a custom in-app updater. Stable package/signing identity and verified supported migrations preserve login/downloads/pending recovery on compatible upgrades. APK and protocol versions are separate; compatible mixed app versions can play together. Incompatibility explains the needed update and safely blocks unsupported requests without wiping work or fabricating unknown outcomes. Exact compatibility windows and release host remain technical work (D-273). |
| P-061 | D-274: each new in-game Shuffle saves a fresh client seed with its request. Client/server share versioned Fisher–Yates/unbiased sampling over canonical actual member IDs, recording the accepted concrete order. Seed/identity persist through retry/prediction; recorded order persists through history/Redo/recovery. Initial shuffles use independent server inputs. Preserve changed-membership/current-state acceptance and bound Draw fidelity, no offline invocation, acknowledgement barrier, arbitrary result array, seed epochs or competitive commit–reveal requirement. Generator/encoding/version registry remain technical completion. |
| P-062 | D-275: stable request identity/intent, PostgreSQL-backed durable per-game admission order and atomic individual outcomes. Receipt means pending, not accepted gameplay or replica catch-up. Pending duplicates keep one inbox slot; completed duplicates return original results; different content under one identity is misuse. Reconnect recovers original outcomes/requests with stream-scoped obsolete-connection fences, preserving admitted work, other devices, original send order and account/lifecycle boundaries. No timeout-as-rejection, new invocation on retry, queue-wide rejection, offline play or separate message-broker requirement. Exact wire/schema/locking/fencing/limits/retention remain technical work. |
| P-063 | D-276: initial download/fresh-baseline full recovery includes the complete retained current-game tree, every retained branch/future, current board/control state and required definitions. Compression/chunking fetches all pages automatically, not default lazy branches or duplicated full-board snapshots per node. Normal synchronization transfers changes only; art remains lazy. Mark partial transfers incomplete; preserve pending recovery/UI and finish coherently before claiming complete history. Expected short offshoots/<2x overhead are not a guaranteed bound or cap. No offline gameplay, history truncation, recursive storage blob or finished-game archive scope is added. |
| P-064 | D-277: expected old acknowledgements do not rewind newer predicted actions. Apply accepted remote changes before rechecking active predictions in order; withdraw failing effects and genuinely invalid descendants, not independently valid work. Withdrawn predictions stay out, but sent requests retain actual-outcome recovery; late server acceptance is an ordinary incoming change with original author/ID, not suffix resurrection. Live mode updates immediately; foreground with unseen updates uses grouped playback until catch-up or deliberate gameplay continuation. Pause/scrub is not historical continuation. Animate previous displayed board to next target, coalescing same-card moves within a live batch or one playback group while preserving separate group stages and every mutation boundary. Modes/progress are personal; synchronization/authority/exposure rules remain. |

The [accounts, decks, and lobby contract](accounts-decks-and-lobby.md) owns these
entry/setup behaviors and their remaining decisions.
The [Android notification contract](android-experience.md#59-notifications) owns
notification behavior and remaining proposals.
The [dynamic-data boundary](../04-technical/architecture-and-operations.md#dynamic-game-data--d-243)
details P-056; the [E2E strategy](../05-verification/e2e-strategy.md) owns D-244's
real-backend/normal-Android test boundary, per-test reset and setup-only exceptions.

## 1.3 Proposed first-release boundaries

**Confirmed scope override — D-241:** the owner excludes the remaining proposed
authoring/coordination extras from R1: custom-piece/layout editors, fuller deck
editing/export, manual group editing, branch comparison/renaming, response
requests/read receipts, presence, extra areas/import formats and additional alert
categories. Broader draft inventories below or elsewhere do not approve those
features. Preserve every already confirmed capability: catalog/Blank tokens and
Clone, direct placement, ordinary text/counter/link tools, automatic grouping,
tree review/activation, common text import, deck metadata/lobby preparation and
invitation/grouped-activity pushes remain included. No expansion roadmap is
implied, and prior explicit deferrals under D-214/D-220 remain unchanged.

Included:

- Private two-player tables, card/deck import, editable setup, manual play,
  undoable result suggestions with other-player acceptance returning both to lobby
  (D-211), and fresh games from restored lobby preparation (D-213).
  Finished-session action-history archives/review are optional future work, not R1
  (D-214); current-game history remains included.
  D-188–D-190 confirm authenticated entry, adding users, import-only deck
  management and lobby mainboard/sideboard preparation with both-player Ready;
  they do not approve a full deck editor. D-211 uses proposed win/loss/draw solely
  for shared ending coordination, without required win-history/scorekeeping.
  D-212 makes acceptance non-undoable, not pending suggestions. D-213 restores
  last lobby-prepared decks/mainboard/sideboard, clears both Ready and requires
  both to ready again for a fresh game. Cross-game starter-option retention remains
  separate; D-274 resolves Random once at every actual start selecting it. D-214
  defers finished-session history review.
- Confirmed areas/piles, card movement/orientation/faces, catalog/Blank tokens and
  Clone, ordinary card text and card/player counter/life tools, links and pings.
  No custom-piece, named-value, layout or general correction editor (D-241).
- Individual action playback, automatic groups, branching, eligible undo/redo,
  shared history, and contextual communication.
- Persistent games, local offline storage, reconnection, invitations, and
  notifications.
- The approved manual MTG areas: Library, Hand, Battlefield, Graveyard, Exile,
  Stack, temporary View/Reveal/combat and lobby-only Sideboard. No extra-area
  editor or optional Command area in R1; labels do not install a rules engine.
- D-55/D-56 add a requested move/reset convenience on entry to configured
  Graveyard/Exile/Library: remove card counters and restore database-default
  corner text (printed P/T for creatures). This is a declared composition of
  generic edits. D-64/D-65 condition other metadata on source availability and
  retain per-game/per-face defaults, with blank editable fallback for missing
  data; provider choice remains open.
- D-83/D-84 make Clone create token instances even from ordinary cards. Tokens
  moved into configured Library/Graveyard/Exile disappear from current state
  across move controls (D-85), with synchronized history/undo recovery. This is
  another declared destination
  convenience, not inferred card-text/type/label behavior or historical erasure.
- D-96, as narrowed by D-105, adds first-class turn and phase/step tracking.
  This supersedes the earlier marker-only scope, without authorizing inferred
  card rules. Remaining progression details and touch controls need review.
- D-97 allows hard-coded MTG phases for this tracking; no configurable phase
  system or phase editor is required. This explicit scope choice does not add
  card-script processing or inferred effects.
- D-98 includes individual MTG steps. D-99 requires phase/step-dependent quick
  actions. D-100 includes standalone Untap all/Draw one and one-press variants
  that also advance tracking. D-101 advances to the next step: Untap → Upkeep;
  Draw → Precombat main at the phase boundary. Other shortcuts/progression rules
  still need review.
- D-102 adds standalone Next step without card edits and tap-indicator direct
  phase/step choice for skips/corrections without intermediate card operations.
- D-103 tracks/displays turn player and number. Next step from Cleanup starts
  the other player's Untap and increments the number, without untapping/drawing.
- D-106 adds quick End turn from any phase/step using that same next-turn
  transition, without skipped card operations. No manual turn-number editor.
  D-107 keeps it visible beside Next step, one tap/no routine confirmation, with
  Undo for mistakes.
- D-108 chooses the starting player during setup and begins turn 1 at
  Beginning/Untap without automatic card operations.
- D-109 leaves extra turns to manual step control, with no dedicated action or
  scheduler. Turn counting may be approximate; no correction editor is needed.
- D-110 makes Untap all literal, without skip-untap flags or inferred exclusions;
  players manually re-tap exceptions.
- D-111 lets combined shortcuts advance with nothing to untap or an empty
  Library. Empty draws move no card and do not automatically cause a loss.
- D-112–D-114 require combat/Stack projections, blocker assignments, and contextual
  Stack actions. [Their primary contract](combat-stack-and-projections.md)
  separates confirmed capabilities from pending per-case controls/lifecycles.
- D-105 removes engine priority state, indicators, Pass controls, and priority-
  legality validation. Players queue actions and use interruption/branching to
  respond; legal timing is their responsibility. Turn/phase/step controls remain.

Not included in the proposed first release:

- Inferred casting/targeting/activation/combat,
  inferred rule-based resets beyond declared conveniences, trigger detection,
  legal-target validation, or card-script execution. D-96 separately requires
  explicit turn/phase/step tracking; remaining progression details are open.
  D-105 excludes priority tracking, UI, and legality validation from this release.
  Explicit combat/Stack projection conveniences under D-112–D-114 are included;
  the blanket exclusion does not remove their required case-by-case design.
- Matchmaking, rankings, tournaments, clocks, wagering, public spectators,
  public chat, AI opponents, or competitive anti-cheat.
- Multiplayer UI and coordination, even if identifiers are not hard-coded to
  “player one” and “player two.”
- Browser, iOS, or desktop gameplay clients.
- Simultaneous hidden-choice cryptographic protocols.
- Automated arbitrary historical sequence replay after a branch changes.

Detailed first-release coverage still needs approval. The generic/no-inference
boundary is confirmed. “All MTG actions” means the generic primitives let players
represent their own play, not that the engine recognizes those mechanics.
[The action catalog](game-actions.md) defines that distinction.

## 1.4 Product principles

1. **Freedom with visibility.** Allow corrections; show who changed what.
2. **Recovery over punishment.** A mistaken tap never silently destroys a line.
3. **No false consent.** Reviewing an action is not a rules approval or a binding
   promise not to interrupt later.
4. **Groups explain; actions determine state.** Group boundaries do not restrict
   valid interruption points.
5. **No secret security claims.** A face-down card is a display convention.
6. **Branch deliberately; validate ordinary edits.** Explicitly continuing from
   the past preserves the original future and makes the new line current. Ordinary
   edits normally apply to current state unless impossible. Only movement and
   Stack additions have optimistic conflict guards (D-187); rejection never
   creates an accidental branch. Ordinary absolute edits may replace a field
   changed meanwhile, while preserving unrelated state.
7. **The board is understandable without the chat.** Actions carry sufficient
   structured details to explain state changes.
8. **Mobile-first interaction.** No required right-click, hover, keyboard, or
   precision-only gesture.
9. **Act, sync, undo.** A completed action applies locally and synchronizes
   automatically. Prefer direct manipulation and easy undo over routine dialogs,
   explicit batching, or repeated confirmation. Irreversible deletion still needs
    an explicit choice. D-236 processes distinct Undo requests as successive
    server-side presses; delivery retries recover one original outcome.
10. **Show facts, not inferred intent.** Present moves, pings, rotations, and value
    changes clearly enough that players usually need no narration. Do not turn
    those facts into an inferred spell/target/effect summary. Text remains optional.

## 1.5 Terminology

| Term | Meaning |
| --- | --- |
| Participant | One of the two people in a game; may edit either side. |
| Mutation/node | One individual state change with deterministic forward/rollback data and one parent, e.g. moving or tapping one card. |
| Invocation | One use of a control, which may issue many individual mutations (including bulk operations). |
| Action | Context-dependent shorthand; specify whether it means an invocation, mutation or playback step rather than treating them as interchangeable. |
| Group | A contiguous sequence of mutations represented as one navigation/playback step with net previous-board → group-end-board animation. Never a state pointer or publication unit. |
| Area | A labeled board region with explicit generic layout/container settings, not a rules zone inferred from its name. |
| Ping | An action-linked visual pointer/marker. It changes no card or game mechanic. |
| Boundary | The root state or the state after one individual mutation. |
| Review cursor | The boundary a particular participant is currently viewing. |
| Playback step | A grouped animation/navigation step over mutation nodes; exact boundaries remain selectable through the paused timeline. |
| Branch | A named continuation from an existing boundary. |
| Active branch | The single selected path ending at the shared active game-state pointer, which may have retained descendants. |
| Head | A continuation's furthest recorded node. Explicit historical activation can select an earlier shared state; D-232 Undo/Redo instead edit the tree. Current board does not mean furthest published descendant. |
| Draft | Recoverable local work not yet shared: pending actions, failed work, or incomplete input. Not a required pre-send phase. |
| Interrupt | Explicitly choose to continue from an earlier boundary, then create and activate that continuation; the message carries that intent (D-179). D-232 Undo/Redo instead edit the existing tree without interruption intent. |
| Publish | Automatically synchronize a completed local action to shared history. A protocol operation, not a required button. |
| Optimistic lock | Exceptional stale-intent guard for movement or Stack additions, not a default previous-value check on ordinary edits (D-187). Failure rejects that action without a conflict branch. |
| Archive/delete | Reversibly hide a branch, without erasing its contents. |
| Undo | Remove your latest still-applied mutation from the existing tree when no branching follows and every later retained event preserves its relevant (from, to) tuple within N. Personal viewing does not change eligibility. Revealing/blocked targets cannot be skipped. |
| Redo | Reinsert the most recently undone mutation at its recorded boundary, preserving its own recorded transition and every later relevant tuple under the same structural/work limits. |
| Play incarnation | One fresh play session/root within a persistent lobby. Requests, result references and per-player Undo/Redo stacks belong to that incarnation, never a later fresh game. |

## 1.6 Success criteria

A pair can complete a game without external board-state tools. Either player
can catch up after hours away, stop before any later recorded action,
respond, and later undo that response without losing the original line. The
same workflow survives app termination, network interruption, and concurrent
activity without unreported lost work.
