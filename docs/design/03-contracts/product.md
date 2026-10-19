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
| P-003 | Individual mutations form sensible navigation/playback groups. One group is one playback step with concurrent animation, not one tree node (D-176/D-177). |
| P-004 | An opponent can interrupt at an action boundary and react. |
| P-005 | History is a tree, with exactly one active branch in each game. |
| P-006 | An interruption makes its new branch active; no approval vote is required. |
| P-007 | Players can delete the interruption and return to the earlier continuation. |
| P-008 | Game edits have undo/recovery support; submitted game-wide chat (D-94) and acceptance of an ending suggestion / return to lobby (D-212) are non-undoable. Pending result suggestions remain undoable (D-211). D-224 preserves every later mutation's relevant-state (from, to) tuple within hard N on removal/reinsertion; revealing targets are excluded. Undo targets only the latest still-applied own action; Redo is last-undone-first; neither skips a blocked target; only own new play clears your queue (D-227–D-231). D-232 Undo/Redo edit the existing tree, not create branches or preserve the pre-Undo path as an alternative. Exact per-mutation state, N, pointers, device scope/anchors, affected references/side branches and retention after queue clearing remain open. D-222 activation and D-223 filtering apply to actual existing branches, not required copies of Undo-edited paths. |
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
| P-020 | Single tap taps a card; double tap opens all available actions; hold shows large art/text. |
| P-021 | The board has distinct areas, including special Library, Graveyard, and Exile piles. Library is ordered; Graveyard/Exile are unordered with no order-related actions, even as visual placement (D-225/D-226). |
| P-022 | Game flow is understandable mostly without writing explanations; optional text input and messages remain supported. |
| P-023 | Pings retain specific recorded timeline context rather than floating independently of play. D-168/D-170 clarify that same-author pings add no group-start marker and need no separate owner-action picker; author changes start a new group and effective boundaries remain preserved. |
| P-024 | The engine is generic and rules-agnostic; MTG is a manual-play use case, not hard-coded mechanics. |
| P-025 | Present literal actions and pings as recorded; players infer their game meaning. |
| P-026 | Area names, card metadata, and pings must not imply game-specific state transitions or automatic side effects. |
| P-027 | Turns and MTG phases/steps have first-class engine tracking, not only arbitrary markers, notes, or tokens. D-105 removes priority tracking and its UI from this release, superseding that part of D-96 and all of D-104. Players judge priority/interruption legality; the engine does not gate edits or branches on it. |
| P-028 | Include a temporary combat area with attacker/blocker projections in visual paired-slot groups, without individual attacker↔blocker assignments inside a pair (D-112 refined by D-137/D-139). Projection creation preserves original battlefield positions; later card actions through projections edit/move the same real cards (D-123). |
| P-029 | Name the former Cast area Stack; allow battlefield permanents to project there to signal activated abilities without moving their originals (D-113). |
| P-030 | Offer contextual Stack quick actions: actual instant/sorcery → Graveyard, actual permanent → Battlefield, projection → remove projection. Design exact behavior case by case before implementation (D-114). |
| P-031 | Stage 1 must cover all agreed behavior, including combat/Stack cases. Stage 2 adversarial review precedes stage 3 UI drafts; stage 4+ implementation is iterative. Do not replace behavior design with UI drafts or technical-schema decisions now (D-115). |
| P-032 | Combat-area creation, attacker declaration, and tapping are separate actions. Phase-appropriate multiselect Declare + tap / Declare without tapping shortcuts also create the area when absent (D-116/D-117). |
| P-033 | Blockers can be dragged to individual attackers or blank unassigned slots; attacker spacing grows with assigned blocker count (D-118). |
| P-034 | Pings show event-wide order numbers (D-119). Links between projections disappear when endpoint projections disappear (D-120). |
| P-035 | No targeted action is quick-action-exclusive: combined shortcuts and constituent actions remain reachable through full targeted action paths, such as context actions (D-121). |
| P-036 | Removing combat area clears its projections/projection links without changing originals' current state (D-122). Card actions through projections always affect the real card, including moves to real destinations (D-123); projections are not independently editable card copies. |
| P-037 | A source leaving the battlefield loses its combat projections (D-124), but its Stack effects remain until explicitly removed/resolved even if a token source disappears (D-125/D-174). Lifetime is area-specific, not determined by which view invoked the move. |
| P-038 | When an attacker leaves, surviving cards stay grouped in their existing slots, with real cards unchanged; the final attacker's departure leaves a blocker-only pair (D-126 refined by D-139). D-138 permits visual reflow with stable membership/order, removes fully empty pairs, and inserts a pair on between-pair drops without regrouping survivors. |
| P-039 | Combat area persists until explicitly closed, not automatically on ordinary phase/step changes or End turn. Provide combined advance out of combat + close, while retaining separate operations and full non-quick access (D-127). Offer the quick action only at End of combat, advancing to Postcombat main (D-128). |
| P-040 | Each tree node is one individual, deterministically reversible mutation, such as moving/tapping one card. Bulk and combined controls issue ordered individual mutations; groups never determine node granularity or publication. D-176 supersedes older combined-node and MTG-reaction-window mappings. Undo follows one parent node (D-159/D-160). |
| P-041 | Non-token cards do not disappear during the game: they move between locations and have no Remove/delete action, including through bulk, correction or projection paths. Approved token disappearance/removal, projection removal and retained-history Undo remain distinct (D-174). |
| P-042 | Returning after an interruption starts at the last local state, animates rollback to the fork, then plays the new continuation automatically or step by step. Playback can be paused; a horizontal bottom timeline permits left/right scrubbing to an exact mutation where play can continue (D-177). |
| P-043 | Login is required before the main menu; registration is available. Main menu offers Logout, Manage decks and Play (D-188). Users can search by username and save contacts without mutual friend-request approval; Friends label/placement remains tentative (D-196). Support the same account on multiple devices, with saved decks, contacts and games synchronized between them (D-218). |
| P-044 | Saved deck card-list editing is import-only, not a full manual deck builder (D-189). Naming/renaming and choosing cover art are separately supported (D-199). A game lobby separately permits deck selection and mainboard/sideboard management. |
| P-045 | Play lists current games including pending invitations. Invite an added user; the recipient has Accept/Decline in Play, and accepting opens the same lobby for them (D-195). For the inviter it is already an open lobby, allowing deck preparation/Ready before acceptance; Cancel game cancels the pending invite. Decline retains the lobby for reinvitation (D-208). Games start once both players press Ready, not upon creation or invite acceptance (D-190). |
| P-046 | Do not draw opening hands automatically on game start (D-192). Provide return-whole-hand → shuffle → draw N (default 7) or pre-return hand size minus one as Library context actions and quick actions only during turn 1's Untap step (D-193/D-194). |
| P-047 | Register with username, email and password; log in with email/password; reset forgotten passwords by email. Other users see usernames, not account emails (D-197). Stay logged in across app restarts; if the session expires, ask for login again, then return to the same game and recover pending already-sent actions without manual resend or duplicate execution (D-217). |
| P-048 | Import common MTG plain-text deck exports rather than a custom syntax, with optional printing details and mainboard/sideboard notation. Flag unknown/ambiguous names without substitution, preserve input and require resolving errors before saving (D-198). |
| P-049 | Save named decks, allow renaming and choose a cover from a deck's cards. Re-import offers Replace or Save as new (D-199). Deleting a saved deck removes it from the saved collection; both editing and deleting preserve copies already selected in lobbies or games (D-221). |
| P-050 | Automatically shuffle each player's Library on game start and initialize each player at 20 life, with no starting-life setting required now. Do not draw automatically; retain normal in-game life edits (D-200). |
| P-051 | Choose the starter in the lobby, offering either player or Random; begin turn 1 at Beginning/Untap with that starter (D-108/D-201). |
| P-052 | Initially in the lobby, mainboard is in Library, sideboard in a separate Sideboard area, and other card areas are empty. Sideboard is unavailable during play, not a hidden accessible pile; play uses the prepared mainboard without merging in sideboard cards or deleting the saved sideboard (D-202). |
| P-053 | Freely move cards between mainboard and sideboard in the lobby without format-legality, size or one-for-one exchange restrictions. Preserve game-local edits and own-Ready clearing; no in-game Sideboard access (D-191/D-203). |
| P-054 | Changing the lobby starter selection clears only the changing player's Ready and preserves the other player's Ready; the changing player must ready again before game start (D-204). |
| P-055 | Provide push notifications for new invitations and grouped opponent activity without revealing covered card names (D-219). Grouping must not delay immediate action synchronization or require group completion. R1 uses Android notification channel settings; both in-app global/per-game mute settings are post-R1 (D-220). Exact cadence, permissions/defaults, channel categories, foreground suppression and multiple-device routing remain open. |

The [accounts, decks, and lobby contract](accounts-decks-and-lobby.md) owns these
entry/setup behaviors and their remaining decisions.
The [Android notification contract](android-experience.md#59-notifications) owns
notification behavior and remaining proposals.

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
  both to ready again for a fresh game. Cross-game starter-selection retention/
  random resolution remains open; D-214 defers finished-session history review.
- Configurable areas and ordered piles, card movement/orientation/faces, custom
  objects, named counters/values, generic links/markers, and free-form correction.
- Individual action playback, suggested editable groups, branching, undo/redo,
  shared history, and contextual communication.
- Persistent games, local offline storage, reconnection, invitations, and
  notifications.
- Layout/data presets for manual MTG play, including areas named Library, Hand,
  Graveyard, Exile, Stack, and optional Command/extra areas. Labels/configuration
  do not install a rules engine or mechanical meaning.
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
   an explicit choice. D-161 silently discards an Undo transition if no longer
   valid and reconciles client state; non-Undo branch-control policies remain open.
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
| Group | A contiguous sequence of mutations represented as one navigation/playback step with concurrent animation. Never a state pointer or publication unit. |
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
| Undo | Move the shared active game-state pointer to its parent node, regardless of author, retaining descendants for forward return. |

## 1.6 Success criteria

A pair can complete a game without external board-state tools. Either player
can catch up after hours away, stop before any later recorded action,
respond, and later undo that response without losing the original line. The
same workflow survives app termination, network interruption, and concurrent
activity without unreported lost work.
