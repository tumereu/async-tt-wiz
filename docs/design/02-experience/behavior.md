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
- Stay logged in across app restarts. If your session expires, log in again,
  then return to the same game and automatically recover pending already-sent
  actions without manual resending or duplicate execution (D-217). Exact session
  mechanics and expired-session offline navigation still need contracts. Explicit
  Logout removes private downloaded content, preserving protected recovery records
  for already-sent requests and logged exposure durably initiated online before
  transmission; another account must never access/publish that data (D-241).
  Session expiry alone is not deliberate Logout. Server retention/account-erasure
  obligations remain privacy/service design.
- Use the same account on multiple devices, with saved decks, contacts and games
  synchronized between them (D-218). Existing lobby/game deck copies stay isolated
  from saved-deck changes. Exact simultaneous-edit conflict handling and transfer
  of personal device state are not settled by this decision.
- Play shows current games, including pending invitations.
- Invite an added user to a game. For you, it is already an open lobby: select/
  prepare your deck and ready while waiting. Cancel game also cancels the pending
  invite. The recipient can Accept or Decline in Play; accepting opens the same
  lobby for them, not active play or an automatic Ready. Declining leaves your
  lobby and preparation/readiness intact, and you can reinvite them. Pending
  invites never expire automatically. Cancelled games leave current games, with
  no trash/restore feature for cancelled lobbies (D-241);
  declined lobbies remain available to the inviter there.
- Saved deck card-list editing currently uses import, rather than a full manual
  deck-building editor. Paste common MTG text exports, with quantities,
  names, optional set/collector details and standard mainboard/sideboard notation.
  Unknown/ambiguous cards are flagged without substitution. Keep the original
  text for correction; all import errors must be resolved before saving.
- Name/rename saved decks and choose one of a deck's cards as its cover art.
  Re-import can Replace an existing deck or Save as new. Changing a saved deck
  does not change copies already selected in lobbies or games.
- Delete a saved deck to remove it from your collection. **Both editing and
  deleting saved decks preserve already selected lobby/game copies** (D-221),
  including their game-local preparation and active play. There is no trash/restore
  for deleted saved decks (D-241); confirmation presentation and server retention
  remain UI/privacy/service work.
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
  separate actions; leaving alone does not cancel it. Either player can cancel an
  unstarted game without the other's approval. Further setup and invitation
  lifecycle details are open in
  the [accounts/decks/lobby contract](../03-contracts/accounts-decks-and-lobby.md).
  Lobby requests bind their original preparation round: delayed old Ready, edits
  or cancellation cannot first apply after play ends and the same lobby reopens.
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
- Individual constituent actions have full paths outside quick windows. Combined
  shortcuts may be quick-only (D-253); existing Library-context mulligans remain.

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
  available for selections where applicable. Own-area hold shows real faces
  without logging; opponent-area hold conceals hidden information. Context
  Inspect other side logs viewing without flipping; Flip separately changes
  shared presentation (D-252). Switching printed face preserves current corner text.
- **Explicit move exceptions:** entering configured Library, Graveyard, or Exile
  removes card counters and restores default corner text; tokens disappear instead
  of staying in those piles. These rules apply across move controls. Do not infer
  other resets, untapping, or face changes.

### 4. Library, Graveyard, Exile, and viewing

- Library is ordered: drops insert on top and Context Move offers top/bottom.
  Graveyard and Exile are unordered: move cards into them without an insertion
  position. They have no order-related actions, including top/bottom, reorder or
  shuffle, even as visual placement (D-226). Drag Library's top-card surface to
  move its top card; a visible Graveyard/Exile card is addressed by identity, not
  position. Tap a pile to open contents and actions together.
- Contents browsers have name search/filtering and inspection. Initial defaults
  are one main-type column per card, Other for unknown types, alphabetical names,
  horizontal column scrolling and vertical scrolling within columns. These display
  choices never change container membership or Library order and are explicitly
  revisable.
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
  ordered source into an ordered destination preserve source order at either end;
  Graveyard/Exile destinations have no order to preserve.
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
  the face selected when processed, without changing counters. Defaults use available
  printed P/T, loyalty, or defense; missing metadata means blank, not guessed data.
  Defaults are retained with the game for offline use and provider stability.
- Notes use a multiline editor; short text appears on the card, longer text may
  be ellipsized but remains fully readable. Empty text removes the note. Notes
  survive ordinary moves, including the reset destinations.
- Completed card text/counter input is edge-trimmed; interior spaces/newlines
  remain. This is not universal credential/account/deck/import/message normalization
  (D-249); passwords preserve whitespace/Unicode consistently.
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
- Clone creates one independent **token** per selected live source when processed,
  even from an ordinary card. Copy face, covering, orientation, corner text, counters, notes, and retained
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
  main. The combination can be quick-only; advance and close remain separately
  available at other steps without an invented out-of-combat combined meaning.
- Stack contains real cards or explicitly created ability projections. Unlimited
  independent Stack effects may reference the same source. Contextual shortcuts
  move a real instant/sorcery to Graveyard, a real permanent to Battlefield, or
  remove one projection without removing its source or sibling effects.
- Stack ability projections can also be created from Graveyard or Exile, leaving
  the source in place (D-242). Keep them live-linked wherever the real source
  moves; a non-token card's zone change does not freeze the projection. Card edits
  through it still affect that same real source, including ordinary move resets.
  Only token disappearance leaves an unlinked, read-only copy of the source's
  last live state, not its state at effect creation. Keep that information
  inspectable without recreating a token or allowing edits to the frozen copy.
  Removing an effect still removes only that occurrence; combat remains live-linked
  and is cleaned up when its source leaves Battlefield. No automatic rules math.
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
  undoable and cannot be edited or deleted after submission. Send another
  message to correct it. Chat stays simple, with no planned expansions (D-240).
  Event comments have no dedicated Edit/Delete either: use ordinary eligible Undo
  or another comment to correct them (D-241).
  Event comments are ordinary tree nodes, not a special comment-only
  branch workflow. References affected by D-232 tree edits remain to be specified;
  do not assume an original alternative is created to retain their old context.
- Actions sync immediately and form meaningful review groups automatically.
    UI actions send requests, the server creates events, and deterministic logic
    groups those events (D-254). Opening Actions captures its targets, not a group;
    local predictions cannot dictate authoritative group membership.
    Grouping only controls navigation/playback and representation. Each group is
   one playback step whose changes animate concurrently, while each individual
   mutation remains a separate node. The client's state pointer identifies a
   mutation, never a group. Reviewing is not accepting legality or waiving a response.
- When returning after the opponent interrupted your continuation, start at your
   last local state and animate rollback fairly quickly to the interruption.
   Then watch automatic playback or step forward through groups. You can pause
   automatic playback; the horizontal timeline at the bottom can be dragged
   left/right to select the exact mutation where you want to continue. Merely
   navigating does not publish another game edit or change the shared active line.
- Core grouping rules: a tap begins a new group unless the current group contains
   only taps/pings; the following substantive action joins that run (D-251), except
   for an ending suggestion. **Every ending suggestion is a strict singleton group
   with no preparatory or following companions** (D-257), overriding the matching/
   companion rules below.
  Pings join the existing group; actual-card moves group by the same ordered origin/destination
  tuple, including Battlefield → Battlefield rearrangements, but never merge back
  across the new tap boundary. Real-card Stack moves and each Stack projection creation
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
  pings or forward/backward companions. The
  [category contract](../03-contracts/history-grouping.md) and
  [scenario matrix](../05-verification/grouping-matrix.md) define pairings,
  prefix growth and planned E2E/playtest coverage. Ping scopes/ordinals cannot
  silently change when presentation is recomputed.
- History retains alternatives with **one active line**. Interrupt at a recorded
  boundary by explicitly choosing to continue there, then start a new active
  continuation without approval voting. Mere scrubbing or editing a stale view
  does not authorize branching. D-232 Undo/Redo edit the tree without branching.
  Keep the old future; never automatically replay it onto the response. An interruption
  can be left by explicitly returning to its retained original continuation;
  branch deletion is recoverable archival. Undo does not automatically activate
  the displaced future.
- Ordinary edits apply at the server's current head unless genuinely impossible
  (D-187). Do not reject taps, notes, Set or other ordinary edits because their
  previous field values changed. Absolute edits write the requested value;
  relative edits use current applicable data, preserving unrelated fields.
  Moves retain source/destination guards: an empty-space move needs the card at
  its stated source and an empty destination. Stack additions also need protection
  against silently covering a concurrent entry: include the current local Stack
  state in the addition request and reject if it differs from the server's current
  Stack state in the operation's execution context. Explicit historical continuation
  compares at its chosen boundary, not the displaced head. This applies to real-card
  entry and projection creation, without
  separate seen/opponent detection, priority enforcement or a whole-game-head lock.
  D-247 compares the ordered current real-card/effect entries, including distinct
  occurrence/source identities—not card text/counters, source movement/freezing,
  links, layout coordinates or personal presentation.
  Rejection/staleness never creates a branch. Historical
  continuation still requires an explicit flag/boundary and makes its new branch
   current. D-232 Undo/Redo edit the existing tree rather than supply interruption
   intent or create branches.
- **Latest Undo/Redo revision (D-224):** selective deletion/reinsertion is allowed
  only when **every later mutation's relevant-state `(from, to)` tuple stays
  identical** in both timelines. This is stricter than live action applicability:
  successive increments of the same counter prevent removing the earlier one,
  but a move followed by a tap of the same card can be independent. Set a hard
  limit N on the suffix work; the value/counting unit and per-mutation relevant
  states still need definition. Selective Undo excludes information-revealing
   targets. No compensating edit is appended. Redo also requires the target's exact
   recorded relevant input/result at its insertion boundary, not fresh arithmetic
   or defaults. Encoding and resource budgets remain technical work.
- **Tree editing (D-232):** Undo removes and Redo reinserts a mutation in the
  existing tree. Neither creates a new branch or preserves the old path as an
  alternative. Later actions replay in the edited tree, under D-224. Removed
  mutations are held for Redo, not placed in an explorer branch. D-233 below
  prevents Undo at or before a fork. A dependent comment/ping blocks removal of
  its referenced mutation under the replay rule; deleting or retargeting it is
  not an alternative. If the history node you are viewing is deleted, fall back
  to its parent, staying in personal review (D-238). Repeat if that parent is
  also deleted, up to the nearest surviving ancestor/root. Do not keep a
  detached old snapshot, jump to live play or create a branch. Storage retention after
  Redo-queue clearing remains open. Explicit interruptions can still branch.
- **Redo after new play (D-227/D-228):** after Undo, only your own new gameplay
  edit clears your Redo queue, even if the removed mutations could still be
  safely reinserted. Opponent actions retain your queue, but can make reinsertion
  fail the replay checks or exceed N. No original explorer path is required by
  queue clearing (D-232). Rejected/no-op requests do not create a successful new
  gameplay mutation and therefore do not clear it; exact clearing bookkeeping
  remains technical work.
- **Across devices (D-235):** your per-game Undo/Redo stack is shared game state,
  synchronized across your devices. Undo on your phone and Redo on your tablet
  under the same eligibility checks. Your latest own action can come from either
  device; new gameplay from either clears your stack. The opponent retains a
  separate stack, and their actions do not clear yours. Personal review cursors
  are separate.
- **Concurrent devices (D-236):** Undo sends a request to the server. Two separate
  Undo requests, even from devices showing the same action, count as two presses:
  each targets your latest own candidate at its server-processing turn.
  A blocked or absent next candidate stops further removal, without skipping it.
  Retrying the same request never executes another Undo. Concurrent play across
  your devices uses normal server ordering and acceptance, without special
  collision safeguards or a single-active-device restriction. Special multi-device
  UX is not a priority; exact sync/protocol and other control races remain open.
- **Redo order (D-229):** restore the most recently undone mutation first, as in
  a normal Undo/Redo stack. Every reinsertion must still pass the replay checks
  within N. If that next entry is blocked, disable Redo and retain the stack;
  never skip to an older entry (D-230). Insertion anchors remain open.
- **Undo target order (D-230/D-231):** only your most recent still-applied action can be
  undone. If it is blocked, disable Undo rather than skip to an older eligible
  action. Later opponent actions remain applied and must pass the replay checks;
  their presence alone does not block Undo. Never Undo an opponent's action,
  including when you have no own action to undo. Personal review and explicit
  historical activation remain distinct controls.
- **Fork boundary (D-233):** Undo cannot remove a branching point or reach back
  past one, even if replay would be safe. Disable Undo when the latest own target
   is a fork or has any later retained fork; do not skip it
  or reattach alternatives. Actions after the latest fork remain eligible under
   the usual checks. Hiding a branch in the explorer does not bypass this rule.
  D-245 clarifies that personal viewing never changes eligibility. Check the
  complete retained successor chain even beyond an explicitly activated internal
  shared node; dependent future events still block Undo and all count toward N.
- **Redo fork boundary (D-234):** if a new fork would require reinserting an
  undone action before it, disable Redo and retain the stack. Do not skip the
  entry, move it to the current head or create a recovery branch. The historical
  mutation can become unusable with the current controls; retaining it does not
  guarantee a recovery path. Opponent branching does not clear the stack; your
  own new gameplay edit still does. Exact anchors and archive/activation
  integration remain open, not promised ways around the boundary.
- **Container order (D-225/D-226):** Library is ordered; Graveyard/Exile are
  unordered. Undo cannot change which card a later top-of-Library draw takes.
  Independent moves
  into Graveyard/Exile need not block each other merely because unrelated contents
  or display positions differ. No Graveyard/Exile order-related controls remain.
- Bulk/combined controls create individual invariant-closed mutation nodes. A
  source move includes mandatory resets/disappearance/combat-link-pair cleanup;
  no published intermediate orphan is allowed. Token disappearance captures its
  last-live state first. Independent card edits/taps/tracking remain separate.
- A **graphical tree explorer** supports personal review and explicit shared
  activation of **any retained branch/node**, including the root/internal states.
- Hide inactive continuations only when they contain **one player's actions and
  no hidden-information exposure**, available through a separate Show hidden
  branches toggle (D-222). **An event comment also keeps the whole continuation
  visible, even for one player's no-reveal path** (D-223). Mixed-player/exposure/
  event-comment paths remain visible in full; this filter deletes nothing. It does
  not require preserving a path removed by D-232 Undo. Explicit logged inspection, View/Reveal,
  revealing draws/moves/face changes, and die/coin results count as exposure.
  Already-visible inspection does not. Undo cannot erase a person's knowledge.
- No offline operation may create a game event (D-246), including covered-face/
  other-face inspection requiring new exposure logging. Own-area hold is unlogged
  and may read downloaded real faces; opponent holds conceal hidden information.
  Downloaded recorded visible content and personal traversal remain readable.
  Online exposure is captured
  before display and survives rejected predictions/restart without an automatic
  gameplay branch; recovering already-initiated records is not offline play.
- Historical pings/comments record at current shared context while referring to
  the selected historical context separately. Do not insert them into old history,
  activate it, merge non-contiguous groups or render future overlays at old boundaries.
  Later-added references are separately identified with their recording event.
- A successful logged inspection is an own revealing action even without a card
  edit: it blocks skipping back through Undo and clears that player's Redo. Own-area
  hold does neither. Exposure-only records use recording-context branch membership,
  retain their viewed-context route and do not create an automatic gameplay branch.

### 10. Ending play

- **Suggest your win, your loss, or a draw.** The suggestion is shared,
  **undoable board state**, not an immediate end to the game.
- **The other player must accept.** Only then do **both players return to the
   lobby**. Your own confirmation cannot replace their acceptance.
- **Show the final board before acceptance.** The suggestion is a group by itself;
  group playback reaches the preceding final board before its acceptance dialog.
  An incoming suggestion must not obscure earlier unreviewed groups with that
  dialog. Sync remains immediate; this is not an extra confirmation/review vote
  or permission to accept a removed/historical suggestion (D-257).
- **Acceptance is not undoable.** Undo can remove a pending suggestion, but cannot
  reverse acceptance/return to lobby or reopen that ended play session.
- Concurrent incompatible result actions follow normal server processing order:
  the first applicable action takes effect; the later action cannot apply and is
  discarded (D-239). A second conflicting suggestion does not replace the first
  or count as accepting it. Acceptance of an already removed suggestion cannot apply.
- This just ensures both understand what is happening; no win-history or
  scorekeeping feature is required. End turn, leaving the screen and cancelling
  an unstarted game remain separate actions.
- On return, restore the last lobby-prepared decks and mainboard/sideboard
  choices, not the final in-game card state or updated saved decks. **Clear both
  players' Ready**; both must ready again before a fresh game starts. Ordinary
  starting setup applies again, including fresh shuffles, 20 life and no automatic draw.
- **Finished-game action-history review is optional future scope, not R1.**
  This does not defer current-game history/Undo/branching or decide deletion of
  finished-session data. The future feature would be read-only, separate from a
  fresh game's history, with no reopening or effect on current play.
- Starter-selection retention/random resolution across games remains open in the
  [lifecycle contract](../03-contracts/accounts-decks-and-lobby.md).

### 11. Offline and failure behavior

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
- When disconnected, show a **Reconnecting... dialog that blocks gameplay
  actions**, including shared Undo/branch controls. This does not turn ordinary
  online acknowledgements into a blocking dialog or declare in-flight requests
  rejected. Downloaded read-only access remains; dialog navigation/dismissal and
  exact reconnect-readiness behavior are not yet settled.
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
  **After reconnecting, automatically recover already-sent online actions** without
  manual resending or executing anything twice. Recover their actual outcomes
  under the original request identities/conditions; do not apply a committed
  action again or retry a rejection as a fresh action. Preserve difference-only
  reconciliation and other valid work. Missing acknowledgement is not rejection.
  Structural errors remain recoverable failures, not excuses to lose selected
  cards or merge competing boards. Exact recovery protocol/readiness and handling
  of buffered but not-yet-sent online actions remain open; offline gameplay is excluded.
- Essential stored card data remains usable offline; missing images can fall back
  to text. Sync, history, and notifications must not unnecessarily expose covered
  identities. Reviewing a retained line does not activate it.
- Game/card definitions, metadata and artwork are dynamically supplied rather
  than a licensed catalog bundled into the app/backend (D-243). Loading updated
  data must not silently change existing game/history meanings or retained face
  defaults. This does not add automatic rules or a custom-data editor; provider
  rights and delivery mechanisms remain separate. See the
  [dynamic-data boundary](../04-technical/architecture-and-operations.md#dynamic-game-data--d-243).

### 12. Notifications

- Provide push notifications for **new invitations and grouped opponent activity**,
  without revealing covered card names (D-219). Group related activity rather than
  sending one push per primitive mutation; grouping must not delay game sync or
  require a group-completion tap.
- For R1, use **Android notification channel settings**. Both in-app global and
  per-game mute settings are **post-R1**, not required app settings (D-220).
  Respect Android settings; push notifications remain in R1.
- Exact cadence, permissions/defaults, channel categories, foreground suppression,
  multiple-device routing and additional alert categories remain open. See the
  [notification contract](../03-contracts/android-experience.md#59-notifications).

## Scope and unresolved behavior

The remaining proposed authoring/coordination extras are excluded from R1
(D-241), including custom-piece/layout editors, fuller deck editing/export,
manual group editing, branch comparison/renaming, response requests/read receipts,
presence, extra areas/formats and additional alert categories. Existing confirmed
tools remain included; this adds no expansion roadmap or stage authorization.

See [scope proposals and genuine gaps](gaps.md). Detailed feature contracts are
in [Tier 3](../03-contracts/README.md); agent interpretation notes and handoffs are
kept separately, not mixed into the player-facing design.

**Current status:** documentation only; this overview approves no new behavior
and does not authorize implementation.
