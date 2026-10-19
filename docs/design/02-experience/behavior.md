# User-facing behavior

This is the Tier 2 behavior summary, not a new set of decisions. It explains
confirmed behavior; broader proposals and gaps are [listed separately](gaps.md).
Start with [Tier 1's general principles](../01-product/overview.md). Use the
[Tier 3 contract index](../03-contracts/README.md) for exact detail; approval
provenance is retained separately in [agent records](../../agent/decisions.md).

## The whole idea

**A shared tabletop that remembers what happened.** Two people play manually on
Android, together or hours apart. Move things directly, use shortcuts for common
chores, review the other person's actions, and interrupt or undo without losing
work. The players know the game rules; the app does not adjudicate them.

The general rules are:

1. **Act directly.** Drag to place, tap to tap, use an action menu for the rest.
2. **Selection expands the scope, not the toolset.** The same card actions work
   on one card or several.
3. **Change only what the action says.** Moving normally preserves card state;
   the few explicit exceptions are listed below.
4. **A projection is another view of the same card, not another card.**
5. **Each mutation saves and syncs immediately.** One node is one change, such as
   moving or tapping one card. Bulk controls issue individual operations in a
   sensible order. No gameplay Send, group completion or animation wait.
6. **History can fork; work must not disappear.** Review is not consent. Undo is
   the normal way to fix a mistake.

## Confirmed behavior

### Before the table: accounts, decks, and lobby

- Log in to reach the main menu; registration is available. The menu offers
  Logout, Manage decks, and Play. Search by username and save users as contacts,
  without mutual friend-request approval. **Friends** as a separate menu name
  is still tentative.
- Register with username, email and password; log in with email/password and
  reset forgotten passwords by email. Other users see your username, not your email.
- Play shows current games, including pending invitations.
- Invite an added user to a game. They can Accept or Decline in Play; accepting
  opens the lobby, not active play or an automatic Ready.
- Saved deck card-list editing currently uses import, rather than a full manual
  deck-building editor. Paste common MTG text exports, with quantities,
  names, optional set/collector details and standard mainboard/sideboard notation.
  Unknown/ambiguous cards are flagged without substitution. Keep the original
  text for correction; all import errors must be resolved before saving.
- Name/rename saved decks and choose one of a deck's cards as its cover art.
  Re-import can Replace an existing deck or Save as new. Changing a saved deck
  does not change copies already selected in lobbies or games.
- A game begins in a lobby. Players choose decks and manage their mainboards/
  sideboards there, then each presses Ready. Once both are ready, the game starts.
  Starting automatically shuffles both Libraries and gives each player 20 life;
  no starting-life setting is required. Life can still be edited during play.
  Choose the starter in the lobby: either player or Random.
  Changing that selection clears only your own Ready, not the other player's.
  Initially in the lobby, mainboard is in Library, sideboard in a separate
  Sideboard area, and all other card areas are empty. Freely move cards between
  mainboard and sideboard there, without legality, size or one-for-one restrictions.
  **Sideboard is lobby-only: it cannot be browsed or used during the game.**
  Start with the prepared mainboard;
  do not add the remaining sideboard cards to Library or delete the saved sideboard.
  Changing your deck or mainboard/sideboard clears your own Ready; lobby deck
  changes affect only this game, not the saved deck.
  Ready requires your own selected deck, without legality or deck-size checks;
  you can withdraw it until start. Accepted Ready persists when you leave or
  disconnect, so both players need not be online together. Start-versus-change
  follows server arrival order: apply a lobby change received first, or reject a
  late lobby change after start without altering the game. Retrying never starts
  or shuffles the game again. Leaving the lobby and cancelling the game are
  separate actions; leaving alone does not cancel it. Further setup and invitation
  lifecycle details are open in
  the [accounts/decks/lobby contract](../03-contracts/accounts-decks-and-lobby.md).
- Starting the game does **not** automatically draw opening hands. Mulligan quick
  actions return the whole hand to Library, shuffle, then draw N (default 7) or
  the pre-return hand size minus one. Both are Library context actions, also
  quick actions **only during turn 1's Untap step**. These explicit actions shuffle
  again even though the Libraries were already shuffled on game start.

### 1. A cooperative table, not a rules referee

- Either participant can edit either side, including inspecting hidden cards.
  Covered hands/cards and library order are presentation conventions, not security
  against the opponent. Routine logs/previews must not unnecessarily spoil them.
- Card text, labels, taps, pings, and moves do not automatically pay costs, apply
  damage, validate targets, create triggers, or resolve effects. No priority
  tracking or Pass button. Explicit phase tracking and the shortcuts below are
  conveniences, not a rules engine.
- Selection, pan/zoom, and ordinary navigation are personal, not board edits.
  Looking at history does not change the active game.

### 2. Touch and selection

| Interaction | Result |
| --- | --- |
| Drag a card | Move that card; if selected, move the selected set. |
| Tap a card normally | Tap/untap it; if selected, apply to the selected set. |
| Double tap | Open available actions, without also tapping the card. |
| Hold | Inspect large art/text, without moving or tapping the card. |
| Drag from empty space | Draw a replacement selection rectangle. |
| Tap empty space | Clear selection, without changing selection-edit mode. |
| Two-finger drag / pinch | Pan / zoom. |

- Rectangle selection includes any intersecting **visible** card portion, not
  hidden pile members. It replaces the old selection.
- Select mode lets card taps add/remove individual cards instead of tapping them.
  Switching it off preserves selection; inspection/actions still work inside it.
  Ping mode and Select mode are mutually exclusive, preserving selection.
- Normally, touching an unselected card to tap/drag clears the previous selection
  and affects only that card. Tapping a selected card sets the whole selection
  tapped if the touched card was untapped, or untapped if it was tapped. It does
  not independently invert mixed states. Tapped means 90° clockwise; untapping
  returns it.
- Successful selected moves preserve selection and mode. Cancelled/invalid drags
  restore placement and pre-drag selection and create no board edit.
- Selections get the same action controls as single cards. Relative edits use
  each card's state; Set supplies common text. Skip cards on which the individual
  action is unusable; disable only if none qualify. This never permits silently
  dropping invalid IDs or partially executing an invalid placement.
- Targeted quick actions and their component actions also have a full action path;
  they are not available exclusively through a phase-specific shortcut.

### 3. Cards, placement, and ordinary piles

- Drops snap to grid positions or slots. Drop onto an ordinary card to form/join
  a diagonal-peek visual pile. Drag a member out to split it; drag it onto its
  own pile to bring it to the top. Other members retain their relative order.
- An ordinary pile contains **at most three actual cards**. An overflowing drop
  is rejected in full, not split or partially moved. Special piles and selections
  do not have this three-card limit. Ordinary piles have no separate browser,
  Select stack command, or automatic whole-pile drag.
- Cards retain identity and unrelated state when moved; links do not make other
  cards follow. Moving does not infer ownership/control changes.
- Face down/up and choosing a printed face are separate context actions, also
  available for selections where applicable. Inspection shows the face despite
  covering, and both printed faces for multi-faced cards, without changing shared
  presentation. Switching printed face alone preserves current corner text.
- **Explicit move exceptions:** entering configured Library, Graveyard, or Exile
  removes card counters and restores default corner text; tokens disappear instead
  of staying in those piles. These rules apply across move controls. Do not infer
  other resets, untapping, or face changes.

### 4. Library, Graveyard, Exile, and viewing

- Drop onto a special pile to put a card on top. Context Move offers top/bottom of
  all three piles. Drag its top-card surface to move the top card; tap the pile
  to open contents and actions together.
- Contents browsers have name search/filtering and inspection. Initial defaults
  are one main-type column per card, Other for unknown types, alphabetical names,
  horizontal column scrolling and vertical scrolling within columns. These display
  choices never reorder the pile and are explicitly revisable.
- The full Library browser hides underlying order. Explicit View/Reveal top or
  bottom N shows actual source order, marking the bottommost card for bottom N.
- **View is logged/shared:** faces for the person viewing, backs for the other.
  **Reveal:** faces for both. Neither moves or copies cards.
- These displays retain the original referenced cards/order until explicitly
  dismissed; they do not refill. Moved cards remain referenced with updated
  locations. Either participant may dismiss; dismissal is logged, not a reversal
  of card moves or deletion of historical displays.
- Draw one needs no quantity picker. Draw N moves top N into Hand. Also support
  View/Reveal bottom N and Move top/bottom N to a chosen destination. N starts at
  one with +/- and numeric entry, clamped to the available count. Moves from one
  ordered source preserve source order at either destination end.
- Library shuffle uses the whole pile, not top/bottom-N subsets; viewing does not
  shuffle. The explicit shuffle-and-draw actions above are additional compositions.

### 5. Text, counters, notes, and links

- Card corner text is one editable string, not separate power/toughness/loyalty
  fields. Whole numbers get +/-1/Set; corner whole-number pairs such as `2/3` get
  left/right/both +/-1 and Set. Other text, decimals, and symbolic pairs get Set
  only initially. Negative numbers are allowed; nothing is clamped to zero.
- Cards and players can have one counter per color: red, blue, green, yellow,
  purple, gray. Each holds arbitrary text and offers +1/-1/Set, without a separate
  Add step or recolor tool. Missing-counter +1/-1 creates `1`/`-1`; nonnumeric
  counters cannot be incremented. Zero stays visible; empty text removes it.
- Corner text may be explicitly blank. Reset restores the retained default for
  the selected printed face without changing counters. Defaults use available
  printed P/T, loyalty, or defense; missing metadata means blank, not guessed data.
  Defaults are retained with the game for offline use and provider stability.
- Notes use a multiline editor; short text appears on the card, longer text may
  be ellipsized but remains fully readable. Empty text removes the note. Notes
  survive ordinary moves, including the reset destinations.
- Completed user text is trimmed at its ends; internal spaces/newlines remain.
  Counter and corner +/- and Set keep/return to the action menu for repetition;
  successful Move to Graveyard dismisses it.
- Link → pick a card/player creates a persistent visual connector; selection
  creates one from each selected source. Links are undirected and unique;
  repeating Link keeps them, removal is explicit via the Links list. No labels,
  styles, attachment/follow behavior, or game effects. Projection-to-projection
  links disappear when an endpoint occurrence disappears.

### 6. Tokens and random tools

- Create → Token opens a searchable catalog, including Blank. Picking creates
  one independent token automatically on the table: no quantity, placement, or
  confirmation step. Customize Blank through ordinary text/counter/note edits.
- Clone creates one independent **token** per selected card, even from an ordinary
  card. Copy face, covering, orientation, corner text, counters, notes, and retained
  defaults, but not links/pings. No Repeat last token or separate custom-token form.
- Non-token cards never disappear during play: they move between locations and
  have no Remove/delete action. Remove is available for tokens only, without a
  pile move or routine confirmation. Token removal/disappearance remains recoverable.
- Roll die gives one result, default d6 with optional sides; Flip coin gives one
  heads/tails result. Record who requested what and the outcome, without changing
  cards/counters. Every new roll/flip/shuffle is fresh, including after Undo.
  Review, Redo, and retries reuse the stored result/order; chance may repeat it.

### 7. Combat and Stack

- Combat is a separate area **between the two play areas**, with blockers above
  attackers. Opposing slots form visual pairs; several cards may sit side-by-side
  on either side. There are no individual attacker–blocker assignments or automatic
  damage calculations. Spacing adapts to contents.
- Drag battlefield cards into combat to create projections; originals stay put
  and are not implicitly tapped. Dragged cards go **where dropped**, all taking
  that slot's role. Reuse existing occurrences: one per real card per combat area.
  Insert between cards, append in unused slot space, or insert a pair between pairs.
- Declaration shortcuts add each new attacker in its own pair **to the right**.
  Already-declared attackers stay put. Declaring an existing blocker moves its same
  occurrence to a new attacker pair. Declaration creates an absent area.
  Declare + tap explicitly taps sources; without tapping preserves orientation.
  Both multiselect quick offerings appear only at Declare attackers; full actions
  remain accessible elsewhere. Area creation, plain declaration, and tapping also
  exist separately.
- Existing internal drag-order defaults: keep source-slot order; across combat
  slots use blockers left-to-right then attackers left-to-right; real battlefield
  selections use visual order top-to-bottom then left-to-right. **These never
  override the drop target. No projections-first mixed-selection rule is approved.**
- Remove from combat removes selected occurrences/incident links, not real cards
  or Stack effects. When cards leave a pair, survivors stay grouped; remove the
  pair only if both sides are empty. Reflow is allowed, regrouping is not.
- Combat remains open, even empty, until explicitly closed. Phase changes/End turn
  do not close it. Closing removes combat projections/links without changing real
  cards. At End of combat, an advance-and-close quick action goes to Postcombat
  main; advance and close also exist separately and through a full combined path.
- Stack contains real cards or explicitly created ability projections. Unlimited
  independent Stack effects may reference the same source. Contextual shortcuts
  move a real instant/sorcery to Graveyard, a real permanent to Battlefield, or
  remove one projection without removing its source or sibling effects.
- **All card edits through projections affect the real card**, including a move
  into a real destination. Removing/repositioning an occurrence is different from
  editing/moving its source or removing a token source. Source departure from
  Battlefield removes its
  combat occurrences, but **not Stack effects**, even if a token source disappears.

### 8. Turns and shortcuts

- Choose either player or Random as starter in the lobby; begin turn 1 at Untap.
  Entering the step does not execute card actions; the game-start shuffle is a
  separate setup operation.
  Track turn player/number and this fixed MTG sequence:
  **Untap → Upkeep → Draw → Precombat main → Beginning of combat → Declare
  attackers → Declare blockers → Combat damage → End of combat → Postcombat
  main → End → Cleanup.**
- Next step changes tracking only. Tap the phase/step indicator to jump directly,
  without executing skipped operations. Cleanup → Next step and one-tap End turn
  switch players, increment the turn number, and go to Untap without untapping or
  drawing. End turn stays beside Next step and is available at any step.
- During Untap: Untap all for the turn player's battlefield, alone or +advance to
  Upkeep. No inferred exceptions/skip flags; manually re-tap exceptions.
  During Draw: Draw one, alone or +advance to Precombat main. Standalone actions
  preserve tracking; entering any step never executes these actions automatically.
- Combined shortcuts still advance with nothing to untap or an empty Library;
  empty draws do not cause an automatic loss. No turn-number editor or extra-turn
  scheduler; handle extra turns manually, accepting approximate counting.

### 9. Pings, communication, review, and recovery

- Pings are numbered visual pointers within one recorded event/group, not rules
  targets. Ping mode toggles only the touched destination, suppressing normal tap
  effects. Changing events exits the mode; revisiting shows recorded pings without
  re-enabling it. Pings do not persist as overlays on later events.
- Context Ping/Unping works without changing mode or another target tap. The
  menu-opening card's ping state determines the uniform action for its selection.
  One standard marker, no labels/styles; automatic numbers span the whole event.
- Ordinary actions/pings should explain play without typing. Optional notes,
  event comments, and game-wide chat remain separate. Submitted chat is not
  undoable. Event comments are tree nodes retained after Undo, not a special
  comment-only branch workflow.
- Actions sync immediately and form meaningful review groups automatically;
   grouping only controls navigation/playback and representation. Each group is
   one playback step whose changes animate concurrently, while each individual
   mutation remains a separate node. The client's state pointer identifies a
   mutation, never a group. Reviewing is not accepting legality or waiving a response.
- When returning after the opponent interrupted your continuation, start at your
   last local state and animate rollback fairly quickly to the interruption.
   Then watch automatic playback or step forward through groups. You can pause
   automatic playback; the horizontal timeline at the bottom can be dragged
   left/right to select the exact mutation where you want to continue. Merely
   navigating does not publish another game edit or change the shared active line.
- Core grouping rules: taps group forward with the next action; pings join the
  existing group; actual-card moves group by the same ordered origin/destination
  tuple, including Battlefield → Battlefield rearrangements and taps interleaved
  between matching moves. Real-card Stack moves and each Stack projection creation
  remain separately visible, with taps/pings as companions. Ordinary card edits,
  including manual face/cover changes, token removal, player values/counters and
  persistent-link creation/removal group together. Token creation and Clone group
  together with interleaved customization edits. Event comments join the previous
   group without starting a new one; combat-area creation and projection
   creation/repositioning/removal group together. Each explicit View/Reveal opening
   starts a separate review group. Stack rearrangements group together separately
   from entries/removals; individual Stack-effect removals stay separately visible.
  Display dismissal/combat closure belongs with the next action, not the displayed
  state's group. Same-target Shuffles group; dice/coin runs start separately from
  prior play, then group with the following action. Phase/turn tracking joins the
  previous group. Same-author pings and Undo do not add group-start markers;
  resuming continues prior grouping context.
  Author changes always start a new group, overriding all other rules—even for
  pings or forward/backward companions. Remaining categories and mixed-sequence
  precedence are explicitly open in the
  [grouping contract](../03-contracts/history-grouping.md).
- History retains alternatives with **one active line**. Interrupt at a recorded
  boundary by explicitly choosing to continue there, then start a new active
  continuation without approval voting. Mere scrubbing or editing a stale view
  does not authorize branching; Undo supplies continuation intent automatically.
  Keep the old future; never automatically replay it onto the response. An interruption
  can be left by explicitly returning to its retained original continuation;
  branch deletion is recoverable archival. Normal Undo follows the active node's
  parent, not automatically the old future's tip.
- Ordinary edits apply at the server's current head unless genuinely impossible
  (D-187). Do not reject taps, notes, Set or other ordinary edits because their
  previous field values changed. Absolute edits write the requested value;
  relative edits use current applicable data, preserving unrelated fields.
  Moves retain source/destination guards: an empty-space move needs the card at
  its stated source and an empty destination. Stack additions also need protection
  against silently covering a concurrent entry: include the current local Stack
  state in the addition request and reject if it differs from the server's current
  Stack state. This applies to real-card entry and projection creation, without
  separate seen/opponent detection, priority enforcement or a whole-game-head lock.
  Rejection/staleness never creates a branch. Historical
  continuation still requires an explicit flag/boundary and makes its new branch
  current; Undo supplies the intent automatically (D-179).
- Undo moves the shared active state to its parent node, regardless of who made
  that node. Retain descendants; explicitly returning to one through the tree can
   redo its state, while merely reviewing it does not make it current. Bulk and
   combined shortcuts create individual mutation nodes, not one whole-invocation
   node. Undo follows one parent per press even when several mutations animate
   together. An empty draw contributes no invented card-move node.
- Playing differently after Undo retains the abandoned continuation. Hide ordinary
  alternatives by default, with a show-hidden-branches toggle; never delete them
  just because play takes another path. Exposure and event-comment paths remain
  visible in full, including ordinary trailing steps; there is no last-exposure/
  comment cutoff. Covered inspection, View/Reveal,
  revealing draws/moves/face changes, and die/coin results count as exposure.
  Already-visible inspection does not. Undo cannot erase a person's knowledge.
- Undo targets the specific node-to-parent step you invoked. If that step becomes
  invalid because shared state changed, silently discard it and reconcile the
  local state—no warning, notification or automatic retry against another node.
  Undo requires a connection in initial delivery (D-182); it cannot be queued
  while offline. Recovery of an online request whose response is lost remains
  distinct from making offline actions.

### 10. Offline and failure behavior

- Local actions update the board immediately to hide network latency. If an
  action is rejected, animate its change backward as if undone; that animation
  explains the correction. It is not a shared Undo or branch. Incoming mutations
  from the other player are also applied with animations, preserving personal
  historical review positions (D-180). Animation never delays synchronization.
- Reconcile the current board to accepted history plus remaining valid local
  predictions, animating only the necessary correction (D-185). A failed move
  followed by a successful tap returns the card while keeping it tapped; do not
  briefly undo/replay accepted work or erase the opponent's changes. Keep valid
  pending predictions and correct genuinely invalid later ones. Acknowledgements
  alone add no animation. This does not change deliberate history traversal or
  D-177 interruption-resume playback.
- Initial delivery requires a connection to make game actions, including Undo
  and shared branch controls. Downloaded games remain readable/reviewable offline,
  but do not create offline mutations or a gameplay queue to replay on reconnect
  (D-182). Show connection and online-pending/accepted/rejected states honestly.
  Preserve recoverable unfinished input and records of already-started online
  requests; this does not authorize offline action submission.
- Online actions have a small local send queue and are sent concurrently. Each
  succeeds or fails individually under its own checks; one failure does not cancel
  another valid action or the whole sequence. Continue normal local interaction
  immediately rather than waiting for acknowledgements, hiding network latency
  wherever practical (D-183). This is not extended offline gameplay.
- Send each client's actions in the order made; the server checks/applies actions
  in arrival order, with other-player actions allowed to interleave (D-184).
  Ordered delivery replaces sequencing dependency metadata, not individual
  validation. Later actions still get checked when an earlier action fails;
  sending and local interaction do not wait for acknowledgements.
- Existing-counter +/- applies to the current number, so two concurrent +1s both
  count. Set writes the requested value without a previous-value comparison
  (D-187 revising D-186). Arithmetic still requires usable numeric data. The
  [limited-locking policy](../03-contracts/optimistic-locking.md) supersedes the
  25-rule proposal; it is not approval of that batch's other details.
- Sync resumes automatically; retries must not duplicate actions or random results.
  Structural errors remain explicit recoverable failures, not excuses to lose
  selected cards or merge competing boards. Exact handling of a connection lost
  during an online request remains open; missing acknowledgement is not rejection.
- Essential stored card data remains usable offline; missing images can fall back
  to text. Sync, history, and notifications must not unnecessarily expose covered
  identities. Reviewing a retained line does not activate it.

## Scope and unresolved behavior

See [scope proposals and genuine gaps](gaps.md). Detailed feature contracts are
in [Tier 3](../03-contracts/README.md); agent interpretation notes and handoffs are
kept separately, not mixed into the player-facing design.

**Current status:** documentation only; this overview approves no new behavior
and does not authorize implementation.
