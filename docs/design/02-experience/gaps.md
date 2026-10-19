# Scope proposals and genuine gaps

This accompanies the [behavior overview](behavior.md). Proposals are not approved
requirements. These are missing outcomes or scope decisions, not a demand for a
question about every button; existing general rules should resolve ordinary cases.
The detailed [question register](../../agent/decisions.md#93-release-blocking-questions)
retains the original Q-IDs.

## Broader draft scope — not all confirmed

These appear in the product/action/UI drafts, but their exact first-release
obligation or behavior is still proposed:

- D-188–D-190 confirm login/registration, main-menu Logout/Manage decks/Play,
  adding users, current games/pending invites, import-only deck management and
  a lobby with deck selection, mainboard/sideboard management and both-player
  Ready. D-195 confirms inviting an added user, Accept/Decline in Play and opening
  the lobby on acceptance. D-196 confirms username search and saving contacts
  without mutual approval. D-197 confirms username/email/password registration,
  email/password login, password reset by email and usernames rather than emails
  visible to other users. Remaining account/invite details, fuller deck editing/export, configurable
  starting arrangements and extra in-game areas remain open or proposed. D-192
  excludes automatic opening draws. D-193 specifies shuffle-and-draw N (default 7)
  and current-hand-size-minus-one actions in the Library context and as opening
  quick actions. D-194 confirms both return the whole hand before shuffling and
  drawing, with quick visibility only at turn 1's Untap. Remaining shortcut
  count/sequencing details are open. D-202 excludes in-game Sideboard access;
  sideboard preparation is lobby-only.
- D-198 confirms pasted common MTG text decklists (Arena/MTGO-style), optional
  printing details, standard mainboard/sideboard notation, flagged unresolved
  names without substitution, preserved input and no saving until errors are
  resolved. Less-common dialects/extra sections, default printings, import limits
  remain open; there is no custom list format. D-199 confirms named/renameable
  decks, a cover chosen from a deck's cards, Replace/Save as new on re-import and
  unchanged already selected lobby/game copies. D-221 confirms deletion from the
  saved collection and reiterates that both editing/deleting preserve selected
  lobby/game copies. Default/duplicate names, cover fallback, replacement metadata,
  deletion confirmation/recovery/retention and concurrent delete/edit/select races
  remain open.
- D-200 confirms automatic Library shuffling on game start and 20 starting life,
  with no pregame life setting required. No automatic opening draw; in-game life
  edits remain. D-201 confirms lobby starter selection with either player or
  Random. D-202 confirms initial lobby mainboard → Library, sideboard → separate
  Sideboard, other card areas empty, and no Sideboard access during play.
  D-203 confirms free lobby transfers between mainboard/sideboard, without legality,
  size or one-for-one restrictions; D-191 still clears own Ready and isolates saved decks.
  D-204 confirms starter-selection changes clear only the changing player's Ready,
  preserving the other player's. D-205 confirms own-deck selection as the Ready
  prerequisite without legality/size checks, manual withdrawal before start,
  Ready persistence without both players online, server arrival-order handling
  of start-versus-change and one-time start recovery. Further setup values/optional
  areas, starter defaults/edit access and random timing/reselection remain open.
  D-206 separates leaving the lobby from explicit game cancellation; leaving
  alone does not cancel. D-207 allows either player to cancel an unstarted game
  without the other's approval. Cancellation-versus-start races and retention/
  archive behavior, along with the remaining invitation lifecycle, are still open.
  D-208 makes a sent invite an already-open lobby for the inviter, with deck
  preparation/Ready before acceptance and Cancel game cancelling the pending
  invite. Decline preserves that lobby for reinvitation, not game cancellation.
  D-209 confirms no automatic pending-invite expiry, cancelled games leaving
  current games and declined lobbies remaining available there to the inviter.
- Arbitrary named player/card values, participant associations, generic pieces/
  markers/custom definitions, and a broader correction tool. Their general purpose
  is manual representation; exact authoring controls and scope are not settled.
- D-211 supersedes D-210: **suggest your win/loss or a draw as undoable board state;
  the other player accepts, then both return to lobby**. No win-history/scorekeeping
  feature is required. D-212 makes acceptance/return to lobby non-undoable while
  pending suggestions remain undoable. Competing/replaced suggestions,
  stale acceptance, starter-selection retention/random
  resolution across games and complete game/history export remain open. D-213
  restores the last prepared decks/mainboard/sideboard on return, clears both
  Ready and requires both to ready again for a fresh game.
- **D-214: optional future feature, not R1:** finished-game action-history archives
  and read-only review, separate from each fresh game's history. This is not an
  R1 gap or a win-history/statistics feature. Current-game history, retained
  branches, Undo and offline review remain included; data-retention/deletion
  guarantees are not selected by deferring finished-game review.
- Detailed review navigation, branch comparison/rename/switch/archive controls,
  optional manual group editing, live-follow, cross-device progress, response
  requests, additional notification behavior, and ephemeral presence. D-219
  confirms push notifications for new invitations and grouped opponent activity,
  without covered card names. D-220 uses Android notification channel settings
  for R1; both in-app global/per-game mute settings are post-R1, not release gaps.
  Cadence, permissions/defaults, channel categories, foreground suppression and
  multiple-device routing remain open.
- Exact screen layouts, accessible alternatives, Android/device support, storage
  settings, and recovery-center flows. Accessibility/no-gesture-only access is the
  intended direction, not a finished interaction specification.

The draft review flow distinguishes the shared current board, your historical
view, and local pending work. It offers previous/next actions/groups, jump to a
boundary/current board, and optional live-follow; incoming updates do not silently
move a historical cursor. Proposed branch-switch notices preserve your view and
offer a return/divergence control. D-219 confirms invitation/grouped-opponent-activity
push notifications without covered card names, coalesced without delaying sync.
Additional dedicated branch-change/response-request alerts remain proposed;
R1 notification control uses Android channel settings (D-220), with exact channel
categories and delivery details still open, not in-app mute settings to design for R1.
Private-game membership still restricts outsiders; full access applies to the
two participants, not everybody. Detailed protocols and controls remain proposed.

The documented release direction excludes inferred rules/legality, competitive
anti-cheat, matchmaking/rankings/tournaments/clocks, public spectators/chat, AI
opponents, multiplayer, non-Android gameplay clients, and automatic replay of an
old future after a response. Exact overall release scope still needs sign-off.

## Genuine gaps

1. **Setup and release scope.** From invitation/deck import to a playable table:
   D-188–D-191 settle authenticated navigation, adding users, import-only decks
   and lobby preparation with both-player Ready. Deck/mainboard/sideboard changes
   clear that player's Ready and do not change the saved deck. D-195 confirms
   added-user invitations with Accept/Decline in Play and lobby entry on acceptance.
   D-196 confirms username search and saving contacts without mutual approval.
   D-208 confirms inviter access/preparation/Ready before acceptance, cancellation
   through Cancel game and a retained lobby after Decline with reinvitation.
   Remaining: exact search/contact removal/privacy, decline presentation/feedback,
   pending-invite duplicates, cancellation races and retention/archive behavior,
   remaining import dialect/printing/count details and saved-deck naming defaults/
   deletion confirmation/recovery/retention and concurrent races, cover fallback/
   replacement metadata (D-221 settles deletion scope and edit/delete copy isolation),
   readiness effects of further setup controls, remaining lobby cancellation/
   retention, further setup values/optional areas and
   starter-selection defaults/edit access/random timing (lobby
   selection with either player or Random, starting shuffle and life 20 are settled).
   D-205 settles Ready prerequisites/withdrawal, persistence without simultaneous
   online presence, start-versus-change ordering and one-time start recovery. Also open:
   remaining mulligan per-card sequencing/concurrent count timing/count edges,
   missing cards,
   starter-selection retention/random resolution after
   returning to lobby, export,
   and which broader authoring/correction tools ship. D-211 settles undoable
   win/loss/draw suggestions and other-player acceptance returning both to lobby;
   D-212 settles non-undoable acceptance; competing/stale suggestions remain open. No
   win-history feature is required, and D-210's blanket non-undoability is superseded.
   D-213 settles restored lobby-prepared decks/mainboard/sideboard, both Ready
   cleared on return and both-player readiness required for a fresh game.
   D-214 excludes finished-session history review from R1 as optional future work;
   current-game history/recovery remains in scope.
   D-192 excludes automatic opening draws; mulligan quick actions are required,
   not optional scope or a manual-moves-only procedure.
2. **History groups and pings.** D-162–D-175 establish the core sequence/marker
   model, including real-card Stack isolation, same-tuple tap interleaving, grouped
   card/combat edits, forward display/combat closure and no ping/Undo markers.
   Manual face/cover edits join card edits; author changes override every other
   grouping rule. Token removal joins card edits; dice/coin runs attach forward
   separately from prior play; phase/turn tracking joins the previous group.
   Player values/counters and link edits share the ordinary edit family; event
   comments join the previous group without starting a new one. Token creation
   and Clone group together with interleaved customization. Non-token Remove is
   forbidden, not an open grouping case. Each explicit View/Reveal opening starts
   a separate group; combat-area creation and occurrence removal join combat edits.
   Stack rearrangements group separately from entries/removals; individual effect
   removals stay separately visible. D-176 fixes the underlying model: one mutation
   per node; bulk controls issue individual ordered operations, all immediately
   published. D-177 makes groups concurrent-animation playback steps, never nodes.
   Remaining: same-author precedence, inspection/participant-association categories, leading-only/first
   contexts, mixed-tuple/compound/extra-location cases and numbering mutations.
   No separate ping ownership picker is needed. See the
   [grouping rules and remaining inventory](../03-contracts/history-grouping.md).
3. **Undo/Redo pointer controls and visibility.** D-159 confirms one parent-node
   step regardless of author and retained forward history. D-222 confirms a root
   initial state/mutation tree, remembering the last active head on Undo and Redo
   toward it, new active branches on changed play, and a graphical explorer with
   explicit activation of any branch/node. No mandatory child picker is approved.
   Hide only single-player/no-exposure inactive continuations behind the explorer
   toggle; mixed-player/exposure paths stay visible in full. The older event-comment
   visibility exception is preserved pending clarification against that condition.
   Remaining: remembered-head scope across users/devices and updates after explicit
   activation, concurrent/stale Redo/activation, detailed pre-fork/nested presentation,
   archive/restore controls and separate empty-interruption semantics. D-161 targets a specific Undo transition,
   silently discarding it if no longer valid and reconciling local state. The
   historical-rollback preview proposal no longer defines Undo. D-177 specifies
   personal resume traversal: last local state → animated rollback to fork →
   grouped forward playback, with pause and exact scrubbing on a bottom timeline.
   This traversal is not shared Undo/activation.
4. **Concurrent online actions and connection loss.** D-182 removes offline game
   actions and later queue replay from initial delivery; downloaded history remains
   readable offline. Offline queue recovery is not an initial-delivery obligation.
   Online latency hiding remains, including recovery of requests that
   may already have reached the server when the connection drops. D-216 confirms
   automatic recovery of already-sent actions after reconnect, without manual
   resending or duplicate execution. Exact readiness, outcome lookup/retry
   protocol/recovery ordering, online-buffered but not-yet-sent actions and
   animation scheduling remain open, not whether recovery is automatic.
   D-215 confirms a **Reconnecting... dialog blocking gameplay actions when
   disconnected**, including shared Undo/branch controls under D-182. Dialog
   navigation/dismissal and exact reconnect readiness remain open. D-216 separately
   approves automatic recovery for already-sent online actions; neither decision
   introduces a routine online acknowledgement barrier or offline gameplay.
   D-183 confirms a small local online send queue, concurrent transmission and
   individual action success/failure with no routine acknowledgement gate on
   interaction. D-184 fixes client send order and server arrival-order processing,
   without sequencing dependency metadata or acknowledgement waits. Remaining
   queue work is exact sizing/saturation, ordered-transport selection, provisional
   references and animation scheduling—not whether concurrent sending, individual
   outcomes or arrival-order processing are supported.
   D-179 replaces automatic conflict branching with explicit UI/message intent;
   Undo supplies that intent automatically. D-187 now normally accepts ordinary
   actions at current state unless impossible, removing D-181's old-field checks
   and D-186's Set comparison. Moves retain source/destination guards; Stack
   additions send current client Stack state and reject if the server's Stack
   state differs. The Stack comparator is settled, not an opponent/seen detector;
   remaining work is exact snapshot representation, provisional references and
   precise move guards, not a general previous-value table. The
   [old 25-rule proposal](../03-contracts/optimistic-locking.md) is superseded,
   not approved. D-180/D-185 immediate feedback and difference-only reconciliation
   remain: preserve accepted work and valid pending predictions, correct genuinely
   invalid ones, and do not animate mere acknowledgements. D-177 deliberate
   history/interruption playback is distinct. Remaining work also includes exact
   bookkeeping/scheduling, connection-loss recovery mechanics, simultaneous explicit
   interruptions and stale non-Undo controls; old conflict-branch/locking proposals
   are not release requirements.
5. **References and projection controls.** Occurrence removal versus token-source
   removal (non-token cards cannot be deleted); surviving Stack effects after
   token disappearance; duplicate-source selections; mixed link/ping endpoints;
   removed tokens in View/Reveal displays.
6. **Combat/Stack entry and destinations.** Dragging before combat exists,
   remaining between-pair targeting, attack destination/player association,
   real-card versus projection Stack entry/order, quick-exit fallbacks for
   unknown/multi-face data, and missing-area/out-of-step combined actions.
   Special sorting policies do not solve these gaps.
7. **Inspection and communication history.** Exposure logging/coalescing without
   unwanted gameplay branching; View/Reveal dismissal/Undo; covered historical
   summaries; comment/chat edit/delete, pending delivery, and response requests.
   Read-only offline inspection that would create a new shared exposure record
   needs a policy consistent with D-182, not an implicit offline publication queue.
8. **Shared action details.** A consistent policy for empty/no-change/stale/invalid
   inputs and affected/skipped feedback; menu scope on unselected cards; token
   auto-placement/initialization; numeric grammar/limits; same-pile reorder versus
   entry resets; ambiguous metadata/defaults. Use shared contracts, not hundreds
   of special cases.
9. **Product/platform choices.** Registration fields, email/password login,
   email password reset and username/email privacy are confirmed. D-217 confirms
   staying logged in across restarts and, after session expiry, logging in again
   to return to the same game and recover pending already-sent actions. Field
   validation, verification, exact session/recovery mechanics, logout/account
   switching and expired-session offline access remain open. D-218 confirms the
   same account on multiple devices with saved decks, contacts and games synced;
   exact simultaneous-edit conflicts, device/session revocation, sync timing and
   personal-device-state transfer remain open, along
   with hosting, catalog/image rights,
   offline storage/retention, device/language/accessibility support, remaining
   notification/privacy behavior and deletion/export guarantees. D-220 defers both
   in-app global/per-game notification mute settings beyond R1; use Android channel
   settings for R1. Protocols, randomness source,
   APIs/migrations, backups, and performance follow in the relevant design stage.

Known wording/status drift is tracked separately in the
[agent specification audit](../../agent/specification-audit.md).
