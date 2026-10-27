# Cubes, pods, drafting, and limited decks

## Status and scope

**Owner-approved design, 2026-10-26** (first written from an opencode design chat,
then reviewed and refined with the owner the same day). Not yet implemented.

Players save **cubes** in their collection, gather in a **pod** of 2–8 members,
draft asynchronously, build decks from their drafted pools, and challenge each
other to ordinary 1v1 games. The tabletop stays two-player; a pod is not a
multiplayer game. There are no bots, no solo drafting, no deadlines, and no
tournament pairings.

Avatars shown in pods come from [profiles and avatars](profiles-and-avatars.md).

### Delivery phases

Each phase is usable on its own.

1. **Profiles**: avatars, username/avatar change, mandatory catalog download
   ([separate doc](profiles-and-avatars.md)).
2. **Cube drafting**: cubes (paste and Cube Cobra import), pods, notifications,
   Booster and Glimpse with "packs open at once", pick screen, Build, Stats,
   Submit, challenges through the normal lobby with basic-land steppers.
3. **Grid** mode with "grids open at once".
4. **MTG set boosters** (per-set MTGJSON download and cache).

The motivating case for phase 2: Caleb Gannon's Synergy Cube (540 cards) drafted
by two players in Glimpse with 18 packs/player, keep 1, burn 4. That uses
18 × 2 × 15 = 540 cards, the whole cube; each player makes 54 picks.

## 1. Concepts

| Concept | Meaning |
| --- | --- |
| Cube | Account-owned, named, counted card list with resolved printings. Synced across the account's devices like decks. |
| Pod | Name, creator, members, invitations, source snapshot, settings, draft state, members' pools and decks, and the games started from it. UI name is always "Pod". |
| Source snapshot | The cube contents or set data copied into the pod when chosen. Later cube edits or set redownloads do not change it. |
| Pool | All card copies one member drafted. During building each copy is in exactly one of Main, Side, Trash. |
| Pod deck | A member's submitted, locked Main + Side + basic-land counts. |
| Pod game | An ordinary 1v1 game whose decks come from two pod decks. It shows the pod's name and survives the pod. |

A cube is a reusable list, not physical stock: drafting does not consume it, and
several pods can use the same cube. **Within one draft**, cube quantities are
finite physical copies (see section 4). Two copies of one printing are two copies;
printings, languages, and card faces never add copies.

## 2. Cubes in the collection

**Manage decks** gets two tabs: **Decks | Cubes**.

A cube is created from either:

- **Pasted text** in the existing deck-import syntax (quantities, optional
  set/collector numbers), with an **Include sideboard** checkbox (off by default).
  Editing a cube is the same text field, prefilled, exactly like decks today.
- **Cube Cobra URL or cube ID.** The phone fetches the list, takes the mainboard
  only, and resolves it against the local catalog like a pasted list. The result
  is a snapshot; the Cube Cobra ID is kept only as provenance.

Before saving, show total copies. A requested printing that is missing may be
replaced by another printing of the same card; list substitutions before saving.
Unknown cards block saving and name the offending lines; nothing is silently
dropped.

Cube row actions: open (card list and count), edit text, rename, delete, and
**Draft this cube**, which creates a pod with this cube selected. There is no
card-by-card cube editor. Other cube sites (Moxfield, Archidekt, CubeTutor
exports, Lucky Paper) go through pasted text.

## 3. Pods

### Navigation

Main menu: Games · **Pods** · Manage decks · Contacts · Settings.

**Pods** lists your pods and pod invitations, sorted like Games ("Your pick"
first). **+ New pod** asks for a name (the existing random fantasy name
suggestions) and a source: one of your cubes or an MTG set (phase 4). Quick
continue on the home screen also lists pods where it is your pick.

Inside a pod a top strip always shows members' avatars with status badges
(picking · N packs waiting, waiting, building, submitted). Below it:

| Phase | Content |
| --- | --- |
| Setup | One page: source, mode, settings (Advanced collapsed), sideboard limit, members and invitations. Creator: **Start** (enabled at ≥2 accepted members). Overflow: Destroy (creator) / Leave (others). |
| Drafting | Tabs **Draft · Build · Stats**. Overflow: Cancel draft and Destroy (creator) / Leave (others). |
| After the draft | Tabs **Build · Stats · Games**. |

Default is Draft during drafting, Build after completion until submission, then Games.

### Setup, membership, and lifetime

- Up to **8 members** including the creator. The creator invites contacts or any
  user by username. Accepting an invitation does not add a contact.
- Only the creator edits source and settings. Members see them read-only.
- **Start** needs at least 2 accepted members; there is no Ready step. Start
  freezes members and settings; invitations still pending lapse.
- Start shuffles seating randomly and generates the draft on the server. Nobody
  needs to be online for the draft to proceed.
- One completed draft per pod.

| Action | Result |
| --- | --- |
| Member leaves during setup | Removed; pod stays in setup. |
| Member leaves during the draft | Confirmation: "This cancels the draft for all N players and discards all picks." Then removed, and the pod returns to setup. |
| Creator cancels the draft | Same, without anyone leaving. |
| Member leaves after the draft | Removed; their pending challenges are withdrawn. If only one member remains, the pod is destroyed. |
| Creator destroys the pod | Confirmation, then the pod is gone for everyone. |

Cancelling discards everyone's picks and builds and keeps source, settings, and
remaining members; setup allows invitations again. Starting again generates a
fresh shuffle from the same source snapshot.

**Started games survive any leave, cancel, or destroy.** They keep their own copies
of decks and card definitions and stay in the normal Games list. Unaccepted
challenges from a destroyed pod or a departed member are withdrawn.

## 4. Draft modes

### Booster and Glimpse/Burn

Glimpse is a preset of the same pack-passing mode.

| Setting | Booster | Glimpse | Where |
| --- | --- | --- | --- |
| Packs per player | 3 | 9 | Main |
| Cards per pack | 15 | 15 | Main (cubes and synthetic set packs; set recipes fix their own size) |
| Keep per pick | 1 | 1 | Advanced |
| Burn per pick | 0 | 2 | Advanced |
| Discard remaining at | 0 | 0 | Advanced |
| Packs open at once | 1 | 1 | Advanced |
| Color balancing | On | On | Advanced (cubes) |

Rules:

- **Seating** is random. A **round** opens *k* packs per player (k = packs open at
  once). All packs of a round pass the same direction; direction flips each round.
  With 2 players, packs simply go back and forth. If k does not divide the pack
  count, the last round opens the remaining packs.
- Each player has a **queue** of packs waiting for them and acts only on the
  oldest. After a pick the pack goes to the next seat's queue. The next round opens
  only when every pack of the current round is empty.
- A pick takes **keep** cards into the pool (into Main) and removes **burn** cards
  from the draft. Burned cards never enter anyone's pool.
- **End of a pack:** if remaining cards ≤ keep, the server awards them
  automatically when the pack arrives (no pick screen). If remaining ≤ keep +
  burn, the player chooses only the keeps; the rest burn on Confirm.
- If a pick leaves the pack at or below "discard remaining at", the rest is
  discarded.
- A confirmed pick cannot be undone.

Why "packs open at once": with k = 1 and 2 players, each exchange gives one pick.
With k = 3 each player picks three packs in a row before waiting, cutting round
trips by three. Synergy example with k = 3: 6 rounds, 9 picks each per round.

### Grid (phase 3)

Draftmancer's Grid, unchanged in geometry:

- 2–4 players, 3×3 grid of cards, default 18 grids.
- On your turn take every remaining card of one row or column. An empty line is
  not a choice.
- Seating random; the starting player rotates each grid; turns follow seating.
- With 3–4 players, empty positions refill after the first selection.
- Option for 2 players: **two picks each** per grid (order ABAB, next grid BABA),
  which also refills after the first selection.
- After all selections, leftovers are discarded and the next grid is dealt.
- **Grids open at once** (default 1, Advanced): several grids run in parallel,
  each with its own rotating starter. You act in the oldest grid where it is your
  turn.
- The board and everyone's selections are public to pod members.

### Sideboard limit

Pod setting, default **unlimited**; optionally a finite number (0 allowed).

## 5. Card sources

### Cubes

- Copies are drawn **without replacement** from the whole cube for the entire
  draft. Burned, discarded, and leftover cards do not return.
- Setup shows the supply check ("540 / 540 cards used") and blocks Start when the
  cube is too small. Using exactly every card is valid. Grid needs at most
  9 + refills per grid.
- **Color balancing** (default on for Booster/Glimpse): when supply allows, each
  pack gets at least one card of each color, then fills randomly. A heuristic,
  never a quota; it cannot create cards.

### MTG sets (phase 4)

- The source picker lists sets from the local catalog that have non-token cards.
- Choosing a set downloads **that set's MTGJSON file** (and the files of any
  sets its booster recipe pulls from). Files are cached on the device permanently.
  Sets released in the last ~60 days are refreshed automatically, like the
  catalog's incremental policy; every set has a manual **Redownload** in the picker.
  (MTGJSON files carry a daily build date, so a real change cannot be detected
  cheaply.)
- The creator's phone uploads the set's cards and booster recipe as the pod's
  source snapshot, like deck definitions today. The server generates packs from it
  and needs no catalog of its own. No catalog format change or re-download.
- Recipe preference: Play Booster, then Draft Booster, then the set's default
  booster. Without a recipe, packs are labeled **approximate**: 10 commons,
  3 uncommons, 1 rare (mythic 1 in 8), 1 random card; if rarities are missing,
  random cards. Set packs have no finite supply.

## 6. Screens

### Pick screen (Booster/Glimpse)

```
┌──────────────────────────────┐
│ ◉A ◉B   avatars + badges      │
│ Pack 4/18 · 10 · +2   ─●── 3  │  fixed
│ ┌────┐┌────┐┌────┐            │
│ │▓✓▓▓││    ││░🔥░│            │  scrolls
│ └────┘└────┘└────┘ …          │
├──────────────────────────────┤
│ ✓ [■]                         │  green tray, fixed
│ 🔥 [■][■]                     │  red tray, fixed
│          [ Confirm ]          │
└──────────────────────────────┘
```

- Header row: pack progress, cards left, packs waiting after this one, and a
  **cards-per-row slider** (1 to all; default 3 portrait, 5 landscape; saved per
  device and orientation).
- The card list scrolls vertically; everything else is fixed.
- **Swipe right = keep, left = burn.** The card follows the finger, tinted green
  or red, and commits past a threshold. Swiping a marked card the same direction
  unmarks it; the opposite direction switches it. Marking more than allowed drops
  the earliest mark of that kind. With burn 0, left swipe does nothing.
- **Tap** opens a small menu: Keep / Burn / Unmark as applicable.
- **Hold** inspects the card full size, in the list and in the trays.
- Marked cards keep their place in the list with a green/red border and icon.
- Keep and burn **trays** above Confirm (green/red background, small cards).
  Tapping a tray card unmarks it. The burn tray is hidden when burn is 0 and is
  pre-filled when the rest of the pack burns automatically.
- **Confirm** is enabled when the counts are exactly right. After Confirm the next
  queued pack appears immediately.
- No pack waiting: "Waiting for B's pick" with B's avatar and a shortcut to Build.
- No double-tap shortcut.

### Grid screen

The board fills the width, with arrow buttons left of each row and above each
column. Tap an arrow to highlight the line, then **Confirm**. Gaps show as empty
slots. A log line above the board ("B took row 2: …") expands to the grid's
earlier selections. Arrows are disabled when it is not your turn. Header shows
grid progress and grids waiting. Hold inspects.

### Build

One vertically scrolling page with three sections, **Main**, **Side**, **Trash**,
each with a sticky header and count. Trash starts expanded (it is last).

```
┌─────────────────────────────────────┐
│ Columns ───●── 7                     │
│ Main 23 + 17 = 40                  │
│ Basics  W[3] U[0] B[4] R[0] G[10]    │
│ Land│ 0 │ 1 │ 2 │ 3 │ 4 │ 5+         │
│ [c] │   │[c]│[c]│[c]│[c]│[c]         │
│ SIDE 27                              │
│ Land│ 0 │ 1 │ 2 │ …                  │
│ TRASH 4                              │
└─────────────────────────────────────┘
```

- **Columns** sit side by side and always fit the width; there is no sideways
  scrolling. A **column slider** (2–10, default 7, saved per device and
  orientation) sets them: Lands first, then mana value 0, 1, 2, …, the last column
  holding that value and above. 7 = Lands, 0, 1, 2, 3, 4, 5+; 3 = Lands, 0, 1+;
  2 = Lands, everything else. Empty columns keep their place. All sections share
  the columns. The control is centered in the title bar; tinted columns include
  their mana-value headers, with 2dp gaps and 12dp outer padding. Widths animate
  and changed column contents fade.
- A card is in Lands if its (front) face is a land; otherwise by mana value
  (X counts as 0). Within a column: color order W U B R G, multicolor, colorless,
  then name.
- Cards are shown **whole**, never overlapped. Duplicates combine into one tile
  with a ×N badge; actions move one copy.
- **Swipes**: right moves to the more important of the other two categories, left
  to the less important (Main > Side > Trash):

  | In | Right | Left |
  | --- | --- | --- |
  | Main | Side | Trash |
  | Side | Main | Trash |
  | Trash | Main | Side |

  While dragging, the destination shows ("→ Side"); section counts update immediately.
- **Tap** opens a menu with the two destinations. **Hold** inspects.
- **Basics**: one compact W/U/B/R/G count row opens one dialog with all five
  steppers, scrollable in short windows with Done always visible. Edits predict immediately and revert on rejection. Basics count toward Main but are not tiles. They are unlimited and do not
  come from the cube. Snow and other special basics are not supplied.
- New picks enter Main without moving anything else.
- Build is available during the draft. Every move and basic change is saved on the
  server (it is the member's private workspace).
- A fixed 48dp bottom bar always shows **Submit deck (N)**, disabled until valid
  and the draft is complete; after submitting it shows **✓ Submitted** at the same height.
  Submitted card categories and basic counts are read-only.
- The header pen edits the owner's private deck name, default **<pod name> deck**.
  `PUT /v1/pods/{id}/build/name` takes requestId, draftAttemptId and name (1–120
  characters, nonblank, no controls), including after submission. PodDetail returns
  only the caller's deckName. Challenges snapshot each name in owner-only game-head
  metadata (ownPodDeckName), never shared decks, revisions or replay events; it
  survives pod deletion and labels the owner's lobby and game deck only.

### Stats

A tab next to Build, for **Main including basics**: mana curve of nonland cards,
creature / noncreature / land counts in one row, and paired thin pip-demand % /
mana-source % bars per color. Curve buckets are 0–6 and 7+, with counts above bars.
No chart captions, explanatory text or footnotes. Hybrid pips count half
to each color, Phyrexian pips to their color, generic and X are ignored; nonland
mana sources are not counted. These are aids, not rules analysis.

## 7. Submitting and games

**Submit** becomes available when the whole draft is complete. The server checks:

- Main has **at least 40 cards** including basics.
- Every card comes from your pool and is used once.
- Side is within the pod's sideboard limit.

There is no four-copy rule and no other legality check. Submit asks for
confirmation, then **locks** Main, Side, and basic counts. Trash is excluded from
all later games. With a finite limit, surplus cards must go to Trash before
submitting. Members submit independently; nobody waits for the slowest builder.

**Games tab:** the owner's deck name, members with avatars and status, a filled
crossed-swords icon button (accessible name **Challenge**) next to
each member who has submitted (once you have too), and this pod's games, which
open as ordinary games.
Challenge failures appear as transient overlays without shifting content; uncertain
challenges retain a retry icon even after the error disappears or the pod is gone.

**Challenge** sends an ordinary game invitation (name suggestion, acceptance, and
lobby as today). Differences in the lobby:

- Each player's deck is fixed to their pod deck; no saved-deck choice.
- Sideboarding uses the existing Main/Side screen over the submitted Main + Side.
  Trash and saved decks are not available.
- **Basic-land steppers** are added to the lobby.
- Ready validates ≥40 Main and the sideboard limit; any edit clears Ready.

Every challenge starts from the submitted pod deck; lobby changes affect only that
game. Rematch keeps that game's last lobby setup, as today. Any two submitted
members can have any number of games, including parallel games with each other.

## 8. Notifications

| Event | Notification | In the app |
| --- | --- | --- |
| Pod invitation | Invitations channel | Pods list |
| A pack reaches you while none were waiting (Grid: your turn) | "Your pick in *Pod*", one per pod, updated not stacked | Pod row "Your pick · 2 packs", highlighted like "Your move"; Quick continue |
| More packs while some are waiting | none | count updates |
| Draft complete | Once: "Draft complete: build your deck" | pod opens on Build |
| Challenge | existing game-invitation notification | existing |
| Draft cancelled / pod destroyed | yes, naming who did it | notice on next open |

Draft notifications use a new **Draft activity** channel. There are no recaps:
opening a pod shows its current state.

## 9. Privacy and server authority

- The server sends each member only **their own** pack, pool, build, and pod deck.
  Others get status and counts only. Grid boards and selections are public to the
  pod. The creator has no extra visibility.
- Pod games follow the normal lobby and game visibility rules.
- The server owns seating, shuffling, pack generation, queues, awards, burns, and
  progress, and stores them; it does not regenerate from a seed against a changing
  catalog.
- Every request carries an identity so retries never duplicate a pick, submit,
  start, leave, or challenge. Requests for a cancelled draft are rejected.
- Unsent local selections (marks, slider) are not saved; confirmed actions are.

## 10. Implementation notes

- Add a pod/draft domain on the server beside games; do not make the 1v1 tabletop
  n-player. Pod games are created through the existing game/lobby lifecycle with a
  pod-deck source.
- Reuse: `DeckImport.kt` (list resolution), `DeckService.kt` (account-scoped
  saved objects and definitions), `Preparation.kt` / `PreparationStore.kt`
  (Main/Side preparation), the activity notification and Quick continue code, and
  the artwork repository.
- Cubes upload resolved definitions like decks; the server never needs a catalog.
- Supplied basics use one fixed default printing per land type for now.

## As built (2026-10-26)

Concrete choices made during implementation, recorded for the owner's review:

- Setting ranges: packs per player 1–30, cards per pack 1–30, keep ≥ 1, burn ≥ 0,
  keep + burn ≤ pack size, packs open at once 1–packs. Grid: 1–60 grids, 2–4
  players, "two picks each" only with 2 players. Cubes up to 3000 copies.
- Submit also enforces the game's 1000-card limit (Main + Side + basics).
- Supplied basics use fixed English M21 printings.
- Approximate set packs scale the 10C/3U/1R(M)/1-any layout to the configured
  pack size; recipe packs keep their recipe sizes. Set packs dealt into grids take
  generated pack cards in order.
- Set files: cached permanently; sets released in the last ~60 days are refreshed
  at most daily; every set has Redownload.
- Pick screen defaults 3 cards per row (portrait) / 5 (landscape); Build defaults
  to 7 columns. Both are per-device settings.

## Wishlist (not planned)

- Custom Grid: width/height, full diagonals, refill policies, more players.
- Reveal everyone's pools and pick order after the draft (draft log).
- Choosing art for supplied basics; Wastes and snow basics.
- Build sorting by color or type; pinch-to-zoom for the slider.
- Sealed, Winston, Rochester, and other Draftmancer modes; bots.
- Cube editing card by card; direct Moxfield/Archidekt URL import.

## Appendix: Draftmancer reference

Researched at upstream commit [`c757d1b`][dm-commit] on 2026-10-26.

| Mode | Notes |
| --- | --- |
| Booster | Private passing packs. Packs/player 3, pack size 15, pick 1, burn 0, discard-remaining 0. |
| Glimpse/Burn | Booster preset for small groups: 9 packs, burn 2. |
| Grid | 2–4 players, fixed 3×3, 18 grids, row/column picks, refill after first selection at 3–4p or in the 2p two-picks option. |
| Supreme | Solo only. |
| Winston, Winchester, Solomon, Housman, Rochester, Rotisserie, Minesweeper, Silent Auction | Alternative pick mechanics (piles, public packs, auctions). |
| Sealed, Team Sealed, Jumpstart variants | Pools without picking. |

Relevant differences from Draftmancer:

- Draftmancer sessions expire about ten minutes after everyone disconnects;
  pods persist indefinitely.
- Draftmancer colour-balances cubes by default and supports cube replacement and
  refill; our cubes are always finite.
- Its deckbuilder has Deck/Sideboard only, overlapping piles with hover, basic
  counters, and an auto-land helper targeting 40 cards (not a legality check).
  Ready there is not a deck submission, and draft logs are shared by default.
- It builds set boosters from Scryfall data plus [magic-sealed-data][sealed-data]
  and set-specific code; we use MTGJSON booster recipes
  ([booster model][mtgjson-booster]), whose sheet weights are sampling weights,
  not finite stock.

Sources: [settings UI][dm-menu], [session logic][dm-session], [custom cube
format][dm-format], [Cube Cobra import][dm-cobra].

[dm-commit]: https://github.com/Senryoku/Draftmancer/commit/c757d1b73c370f88135cb699091f58d8fc3a094b
[dm-menu]: https://github.com/Senryoku/Draftmancer/blob/c757d1b73c370f88135cb699091f58d8fc3a094b/client/src/App.vue
[dm-session]: https://github.com/Senryoku/Draftmancer/blob/c757d1b73c370f88135cb699091f58d8fc3a094b/src/Session.ts
[dm-format]: https://draftmancer.com/cubeformat.html
[dm-cobra]: https://github.com/Senryoku/Draftmancer/blob/c757d1b73c370f88135cb699091f58d8fc3a094b/src/cubeCobraIntegration.ts
[sealed-data]: https://github.com/taw/magic-sealed-data
[mtgjson-booster]: https://mtgjson.com/data-models/booster/
