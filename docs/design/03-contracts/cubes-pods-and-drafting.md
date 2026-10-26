# Cubes, pods, drafting, and limited decks

## Status and scope

**Confirmed design, approved by the owner on 2026-10-26.** This document records
the collaborative design discussion and approval to write the specification. It
does not claim implementation or authorize implementation outside an assigned task.
The Draftmancer comparison at the end is reference material, not additional scope.

The initial feature supports reusable player-owned cubes and MTG set drafting in
pods of up to 16 members, prioritizing use with two or three people. A pod has one
completed draft, personal deckbuilding, and any number of independent 1v1 games.
A cancelled attempt may be restarted in the same pod. Multiplayer games remain a
hypothetical future feature; a multi-member pod is not a multiplayer game.

Initial modes are **Booster**, **Glimpse/Burn**, and **Grid**. No bots, solo draft,
tournament pairings, or scheduled rounds are required. One person may create and
prepare a pod while inviting others; starting requires at least two participants.

This is an explicit addition to the older import-only collection and two-player
lobby scope in [accounts, decks, and lobby](accounts-decks-and-lobby.md). Private
draft/deck workspaces and limited-deck submission checks are specific to this
feature. They do not turn the manual tabletop into a rules engine or redefine its
inspection/ownership behavior. Existing games and saved-deck workflows remain
available independently of pods.

## 1. Concepts and ownership

| Concept | Meaning |
| --- | --- |
| Saved cube | Account-owned, named, reusable counted card list with resolved definitions/printings and optional import provenance. |
| Pod / table | The same concept: creator, accepted members, invitations, source snapshot, settings, draft attempt, member decks, and associated games. Use a consistent UI label. |
| Source snapshot | The selected cube revision/content or set-generation source data retained for this pod. Later source edits do not mutate it. |
| Draft attempt | Server-owned seating, generated card instances, hidden order/packs, progress, and awarded copies. Cancellation invalidates the attempt. |
| Acquired pool | All concrete copies awarded to one member. Each belongs to exactly one of Main, Side, or Trash while building. |
| Submitted pod deck | Immutable Main/Side selection and supplied-basic counts from a completed draft. Trash is excluded from game eligibility. |
| Pod game | An ordinary two-player game with independent copies of its two submitted decks and a retained source-pod association. |

Collection is a library of saved objects, not physical-card stock ownership.
Drafting does not consume the saved cube, and multiple pods may use the same cube.
Within one draft, however, cube quantities are finite physical-copy quantities.

Distinguish logical card identity, selected printing/immutable definition, and
individual draft-copy identity. Two copies of the same printing remain two copies.
Printing/art/language variants and linked card faces must not accidentally become
extra copies or extra sampling weight. Existing definition digests and snapshot
patterns can be reused without treating a digest as a permanent Oracle identity.

## 2. Collection and cube import

### Saved cubes

Provide Cubes alongside saved decks in the collection. A cube can be created from:

- A decklist in the existing deck-import syntax, including quantities and optional
  set/collector identifiers. The editing surface is a text field, like decks today.
- A Cube Cobra URL or cube ID, converted into the same resolved counted list.

Owners can inspect, rename, replace the list, and delete their cubes. There is no
card-search/quantity-editor cube builder in this initial scope. The draft deckbuilder
described below does not become a cube editor.

Import the mainboard by default. Offer explicit inclusion of a supplied sideboard;
do not silently include maybeboard or other Cube Cobra boards. Show total copies
and resolved cards before saving. Preserve requested printings where available.
If only a printing is unavailable, another printing of the same card may be used,
with substitutions shown before saving. Unknown or unresolved cards block saving
and identify the offending entries; no silent dropping or partial import.

Cube Cobra import takes a card-list snapshot, not an external live reference.
Retain its source URL/ID for provenance. Custom draft scripts, custom sheet/layout
recipes, arbitrary custom cards, and draft-affecting card scripts are not implied
by this importer. Unsupported content must be reported, not silently simulated.

Moxfield/Archidekt and other exported lists can use compatible text import. Direct
URL adapters for those services are not part of the approved initial integrations.

### Snapshot isolation

Selecting a cube for a pod retains its resolved contents and revision. Editing or
deleting the saved cube, or changing the remote Cube Cobra list, cannot change that
pod. Explicitly changing the source in setup creates a replacement snapshot and
clears draft readiness. Draft cancellation retains the selected source snapshot;
it does not silently fetch a newer external list.

## 3. Card sources and generation

### Physical-style cubes

- Shuffle/sample finite copies **without replacement** across the whole attempt.
  Quantities are supplies, not probability weights that can produce unlimited copies.
- Burned cards, discarded packs, and discarded grid leftovers do not return to the
  supply during that attempt. A later fresh attempt starts from the full snapshot.
- Color balancing defaults **on for booster-based drafting**, including Glimpse.
  It is a configurable heuristic, not an exact color quota, and cannot manufacture
  cards or violate quantities. Grid uses its configured source without a requirement
  to balance each row, column, or diagonal.
- Validate sufficient supply before starting, including Grid refill requirements.
  An insufficient cube is an actionable setup error; never silently enable
  replacement, replenish it, or shorten the agreed draft.

### MTG set sources

Support all MTG catalog sets that have usable eligible card records, rather than
only Arena sets or current Standard sets. Tokens are excluded. A set without a
conventional booster product remains usable through approximate generation when
it has eligible cards; a set with no eligible cards cannot start a draft.

Use public MTGJSON Draft/Play booster recipes where available. These describe
weighted pack compositions, sheets, and source-set dependencies, including bonus
sheets. Published recipes determine their pack sizes. Approximate generation is
acceptable: when a recipe is unavailable, offer labeled synthetic packs using
rarity categories where possible and unrestricted random eligible cards when the
required categories are absent. Do not represent synthetic packs as authentic
retail collation. Set generation has no shared finite physical-stock limit.

The source selector must distinguish selecting a set/product from selecting a
saved cube. Exact additional product choices, mixed-set-per-round controls, and
fallback rarity ratios remain generation/UI implementation design, not a promise
to expose every Draftmancer retail-product option in the initial feature.

The app already downloads MTGJSON, but its current compact catalog discards
rarity and booster recipes. Generation needs retained rarity, relevant set/card
eligibility metadata, product recipes, and referenced printing definitions. This
requires backfilling existing installations, not only parsing new sets. See the
implementation boundary in section 10.

## 4. Pod setup, membership, and lifetime

### Setup and starting

- A pod has a creator and at most 16 accepted members, including the creator.
- Invitations can target contacts or non-contacts. Accepting an invitation does
  not save a contact or mark the recipient Ready to draft.
- The creator controls source and draft settings. Members can inspect setup and
  mark themselves Ready. Material setup/membership changes clear readiness.
- Normal Start requires all accepted participants to be Ready. Creator-only
  **Force start** bypasses readiness, including for absent accepted participants.
  It does not bypass invitation acceptance, the two-player minimum, the capacity
  limit, card availability, or configuration validation.
- Start freezes participating membership and settings. Pending invitees are not
  participants and cannot join that attempt afterward. No late replacement seats.
- Starting creates a fresh shuffled attempt with persistent server-owned state.
  A participant need not be connected for the draft to exist or for another
  participant's valid action to be processed.

### Leaving, cancellation, and destruction

| Action/state | Result |
| --- | --- |
| Non-creator leaves during setup | Remove that member; remaining pod stays in setup and readiness is invalidated. |
| Non-creator leaves during drafting | Remove that member, cancel the entire attempt, and return remaining members to setup. |
| Creator destroys the pod | End its drafting/deckbuilding/challenge coordination and remove it as an active pod. |
| Member leaves after drafting | Retain other members' completed pools/decks; departing member cannot create new pod challenges. |
| Post-draft departure leaves only one member | Automatically destroy the pod. |

The creator uses Destroy rather than leaving and transferring ownership. Ownership
transfer is not required. A one-member pod in setup is allowed; the automatic
destruction rule applies after drafting, not while initially recruiting members.

Draft cancellation discards **everyone's picks and draft-time deckbuilding**, clears
readiness, and retains source/settings for the remaining members. Setup permits
invitations again. Restart generates fresh random state; it does not resume the old
packs or reuse their ordering. One completed draft per pod still holds.

**Started games survive every pod leave/cancel/destroy operation.** Their players,
definitions, decks, preparation copies, and history cannot depend on continued pod
membership or cascade deletion of pod records. Unstarted challenges involving a
departed player or destroyed pod cannot subsequently launch a game. The distinction
between game preparation and started play must remain explicit in lifecycle code.

### Asynchrony and notices

There are no pick deadlines, automatic picks, offline eviction timers, or bot
replacement. Accepted progress survives all participants disconnecting. Returning
opens the latest authoritative Draft/Build/Games state directly, not a "since your
last action" recap or playback of all intervening draft choices.

Cancellation and destruction get explicit notices, including for affected users
who were absent. Do not introduce routine draft-turn/completion recap alerts in
place of the selected current-state presentation. Existing 1v1 game notification
behavior remains independent; notice transport uses the app's notification design.

## 5. Draft modes and settings

All counts are integers. Keep counts and pack/grid counts must be positive;
burn/discard thresholds are nonnegative. Limits, supply checks, and applicable
settings must be validated by the server, including on Force start. Show effective
settings rather than silently inheriting stale settings from a previous pod.

### Booster and Glimpse/Burn

| Setting | Booster default | Glimpse default | Behavior |
| --- | --- | --- | --- |
| Packs per player | 3 | 9 | Number of pack rounds. |
| Cube/synthetic cards per pack | 15 | 15 | Editable where the generator permits; published recipes determine their sizes. |
| Keep per selection | 1 | 1 | Cards awarded from the current pack. |
| Burn per selection | 0 | 2 | Additional selected cards removed from circulation. |
| Discard remaining at | 0 | 0 | Discard a passed remainder at or below this size. |
| Color balancing | On | On | Finite-supply heuristic for generated packs. |

Expose ordinary defaults first and additional pick/burn controls under Advanced.
Glimpse is a preset of the same pack-passing interaction, not a different pool
ownership model. Players select kept and, when applicable, burned cards before
committing their action. Kept cards enter Main; burning never puts cards in Trash.
Near pack exhaustion, required keep/burn quantities are capped to available cards,
keeping before burning, rather than making the last selection impossible.

Each player works on their own available pack; this is not a single global turn.
Packs pass through the randomized seating order, reversing direction between pack
rounds. With two players, packs pass back and forth. Complete all pack rounds to
finish. Opponents' pack contents and private acquired pools are not broadcast.

### Grid

Grid generalizes Draftmancer's fixed 3×3 format. It can use pod participant counts
from 2–16 **when the selected dimensions/refill settings support the required
selections**; copying Draftmancer's hard 2–4 limit is not necessary for this variant.

| Setting | Default | Allowed behavior |
| --- | --- | --- |
| Width × height | 3×3 | Each axis independently editable from 2 through 8. |
| Grids | 18 | Positive count. |
| Selections per player per grid | 1 | Positive count; everyone receives the same configured number. |
| Diagonals selectable | Off | Enable the two full corner-to-corner diagonals on square grids only. |
| Refill | Never at 2p; after first selection at 3+ | Never / after first selection / after every selection. |

1. Deal a public rectangular grid; future cards and refill supply stay hidden.
2. Randomize initial seating. Rotate the starting seat each new grid, then proceed
   in seating order. For multiple selections/player, repeat that order, not snake
   order: two players with two selections use ABAB, then BABA on the next grid.
3. Choose a row, column, or enabled full diagonal and take every remaining card on
   that line. Gaps do not invalidate a line; a completely empty line is not a choice.
   Short partial diagonals and rectangular diagonals are not selectable.
4. When required by the refill policy, refill empty positions before the next
   selection. Do not refill after the final selection of a grid.
5. After everyone has made the configured selections, discard all leftovers and
   deal the next grid. Finish after the configured number of grids.

The layout and refill policy must guarantee enough nonempty selections even when
players take long lines. Setup cannot rely on players deliberately leaving cards
for later seats. Likewise reserve enough finite cube supply for the maximum refill
demand permitted by the settings, rather than failing halfway through the draft.
Larger membership may require a larger grid or refill after every selection; the
default 3×3 settings need not be valid for all 16 participants.

On mobile, tap a line selector to highlight the cards, then **Confirm selection**.
Provide visible row/column/diagonal targets rather than requiring a diagonal swipe
across card tiles. Public selections remain public; changing tabs does not pause
the shared draft.

## 6. Deckbuilding and submission

### Three categories and gestures

Build is available throughout drafting, including while waiting for other people.
Newly awarded copies enter Main without disturbing existing allocations. Each
copy is in exactly one category; moving it is an authoritative personal-workspace
edit, not a new award or a change to the cube supply.

| Current category | Right / accept | Left / deny |
| --- | --- | --- |
| Main | Side | Trash |
| Side | Main | Trash |
| Trash | Main | Side |

This implements the ordered priority **Main > Side > Trash**: accept chooses the
more important of the two alternatives, deny the less important. Gestures move an
identified copy. Provide explicit accessible moves as an alternative to swiping.

Show category cards in mana-value/CMC columns, with counts and card inspection.
Trash means unused but retained; it is recoverable until submission. It is not
destruction, a draft burn, or public grid leftovers. Swipe feedback must make the
destination clear so the same gesture's category-dependent result is understandable.

### Basic lands and statistics

Supplied ordinary basic lands use numerical steppers, not card tiles in these
columns. They contribute to the playable deck count and all relevant statistics.
Keep their supply separate from finite drafted card instances so changing a basic
count cannot manufacture another cube card. Drafted basics remain acquired copies.

Provide main-deck and sideboard counts, mana curve, creature/noncreature/land counts,
colored mana-pip distribution as a pie chart, and a separate mana-source breakdown.
Pip demand and sources are different statistics. Display labels/values alongside
charts; statistics assist the player rather than certify that a mana base works.
Treatment of hybrid/Phyrexian symbols and conditional mana sources must be explicit
in the eventual calculation design, not silently presented as exact rules analysis.

### Submission and locking

Submission becomes available after the entire draft completes. Before accepting it,
the server checks:

- Main contains **at least 40 cards**, including supplied basics.
- Every nonsupplied copy comes from that player's acquired pool and is used once.
- Side is within the pod's sideboard maximum, if finite. Default is **unlimited**;
  a finite nonnegative count, including zero, can be selected in setup.
- Supplied ordinary basics are unlimited. There is no four-copy restriction and
  no configurable deck-minimum or broader format-legality engine in this feature.

While building, an incomplete/oversized allocation can be edited; it cannot be
submitted until these checks pass. A finite sideboard forces surplus acquired
cards into Trash. With an unlimited sideboard, every unused acquired card may be
kept available for future games.

Submit locks the pod's Main, Side, and supplied-basic counts. Trash is excluded
from eligibility for later games. There is no post-submission pod-deck rebuilding
or unlock flow in the initial scope. Members submit independently: no table-wide
ready gate waits for the slowest builder before two submitted players can play.

## 7. Challenges and independent games

Any two current members with submitted decks may arrange a 1v1 game. There are no
automatic pairings, rounds, or limits of one active game per member/pair. Explicit
separate challenges may produce concurrent games between the same two people.
Invitation acceptance, game preparation, and starting play remain separate steps.

Each challenge/game uses independent copies of the two immutable submitted decks.
Preparation offers **Main / Side only**, plus supplied-basic steppers. Trash and
arbitrary saved decks cannot supply additional cards. Players may sideboard freely
within their eligible pools, including substantial rebuilding when Side is unlimited.
Validate the same minimum, pool membership, and sideboard maximum at Ready; a
preparation edit clears that player's Ready. Both Ready starts the existing 1v1
tabletop lifecycle. Sideboard remains unavailable during play.

New challenges always begin from the submitted pod deck, not the allocation used
in another game. Game-local changes cannot update the pod deck or other parallel
games. Existing rematch preparation can retain that game's last prepared allocation
without becoming a pod-wide edit; subsequent validation still uses the original
eligible pool and pod deckbuilding constraints.

Started games remain reachable through the ordinary Games destination even if
their pod is destroyed. Retain enough independent card definitions and provenance
to load those games without a live cube or pod record. This does not create a new
finished-game archive or tournament-results feature.

## 8. High-level screens

| Surface | Content and primary actions |
| --- | --- |
| Collection → Cubes | Named cubes, total card counts, inspect/import/edit/rename/delete, Create pod. |
| Cube import/edit | Decklist text or Cube Cobra URL/ID; section inclusion; resolved preview, line errors, printing substitutions; save. |
| Pod list/entry | Current pods and invitations; open latest state. Creating a pod can also start with a set source rather than a saved cube. |
| Pod Overview / Setup | Source, members/pending invitations, mode-specific controls, sideboard maximum, readiness; creator Start/Force start/Destroy; member Leave. |
| Draft tab | Current pack or grid, selection requirements, progress, whose action is needed; preview and commit. |
| Build tab | Main/Side/Trash, mana-value columns, swipes and explicit moves, basic steppers, statistics; Submit after draft completion. |
| Games tab | Members' submitted/building status, challenge eligible members, pending challenges and independent games. |

Draft and Build remain separately accessible while the attempt is running. Changing
tabs, sorting, highlighting a candidate selection, and opening statistics are local
UI actions. Awarding cards, moving cards between categories, changing basic counts,
submitting, and accepting challenges are persisted server actions.

Use existing compact navigation, card inspection, and accessible interaction
patterns. The specification chooses screen responsibilities, not pixel layouts.

## 9. Privacy, authority, and recovery invariants

- Each member's acquired pool, work-in-progress allocation, and submitted deck are
  private. Other members receive counts/status and only card information made
  public by the draft mode. The creator has no organizer exception exposing
  everyone's private cards. Knowing Grid picks does not reveal their allocation.
- Withhold private records/hidden card identities from unauthorized responses;
  cosmetic hiding of a shared payload is insufficient. Existing preparation
  payloads must not be reused unchanged if they disclose both decks to pod members.
  Actual 1v1 play follows its separately maintained tabletop privacy contract.
- Generate/shuffle, award, burn/discard, and advance progress authoritatively.
  No client owns the hidden supply or decides another participant's award.
- Save draft state independently of connected clients. Retain concrete generated
  instances/order and source/settings versions, rather than relying solely on
  replaying a random seed against a changing catalog.
- Use request identities and attempt/workspace revisions so retrying a pick,
  submit, start, leave, or challenge cannot duplicate its effect. Different
  intentional challenges between the same pair remain permitted.
- A request from a cancelled attempt cannot affect its replacement. Server ordering
  resolves last-pick versus leave, Start versus setup edit, and Ready versus edit
  races; clients converge to the accepted lifecycle rather than reviving old state.
- Committed work survives reconnection. Unsent local selection and prediction are
  not durable offline actions; existing connected-action boundaries still apply.

## 10. Implementation boundary and details to refine

Reuse account-scoped saved-object patterns, resolved definitions, prepared-copy
isolation, command identities, and the existing 1v1 game lifecycle. Add a pod/draft
domain instead of turning the two-player tabletop itself into an n-player game.

Relevant current implementation locations:

- [DeckImport.kt](../../../client-storage/src/main/kotlin/org/asyncttwiz/storage/DeckImport.kt):
  counted-list resolution.
- [MtgJsonCatalog.kt](../../../client-storage/src/main/kotlin/org/asyncttwiz/storage/MtgJsonCatalog.kt)
  and [CatalogDownloads.kt](../../../client-storage/src/main/kotlin/org/asyncttwiz/storage/CatalogDownloads.kt):
  current compact catalog and full/incremental MTGJSON downloads.
- [Preparation.kt](../../../protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/Preparation.kt):
  concrete-copy Main/Side transfer model.
- [DeckService.kt](../../../server/src/main/kotlin/org/asyncttwiz/server/decks/DeckService.kt):
  account-scoped saved definitions/decks.
- [PreparationStore.kt](../../../server/src/main/kotlin/org/asyncttwiz/server/game/PreparationStore.kt):
  isolated preparation snapshots.

The catalog currently lives on the client; the server has submitted definitions,
not a complete searchable MTGJSON set catalog. Implementation must deliberately
provide authoritative generation data, either through server acquisition or an
accepted, validated source snapshot. Merely calling the existing client catalog
from a new server picker is not an available implementation. Recipe UUIDs can
reference other sets and must be resolved before admitting a draft.

These are engineering refinements, not additional approved product features:

- Exact generation-data acquisition/storage, catalog backfill, recipe selection,
  synthetic rarity ratios, and color-balance algorithm.
- Finite-supply/Grid-capacity validation and bounded resource limits for editable
  counts whose UI maximum has not been specified.
- Precise eligible-printing treatment for supplemental/digital/promo sets and
  unavailable recipe dependencies; never silently advertise unsupported generation.
- Supplied basic variants and their catalog identities; special basics such as
  snow lands must not be silently treated as an unlimited ordinary-basic supply.
- Statistic calculations and sparse/large-grid presentation within the approved
  controls; notification delivery and retained cancellation/destruction notices.

## Appendix A. Draftmancer reference and scope decisions

Research baseline: upstream commit
[`c757d1b73c370f88135cb699091f58d8fc3a094b`][dm-commit], dated 2026-10-14,
inspected on 2026-10-26, plus official format/data documentation. The deployed
draftmancer.com revision was not established. Defaults below describe that source,
not this app's defaults unless explicitly adopted above. UI bounds and server
validation are not always identical; upstream validation defects are not requirements.

### Modes and their parameters

| Mode | Player/use notes | Parameters and upstream defaults |
| --- | --- | --- |
| Booster | Private passing packs; at least two total seats including any bots. | Packs/player 3; custom-list pack size 15; keep 1; burn 0; discard threshold 0; optional first-pick-only multi-pick. |
| Glimpse/Burn | Booster variant suitable for small groups. | Suggested packs/player 9; burn 2; other ordinary pick controls apply. |
| Supreme | Solo only. | 18 packs; take 2 from each and discard the rest. UI packs 6–64 and picks 1–64. |
| Team Draft | Advertised six-player 3v3 arrangement; ordinary draft algorithm. | Boolean team mode, alternating teams, team-related bracket; disables bot seats. |
| Winston | At least two humans; private inspected piles. | Total packs 3×players; piles 3 (2–8); remove basics true. Take/skip piles, adding cards on skips; final skip draws a hidden card. |
| Winchester | At least two humans; public piles. | Packs/player 3 (UI 1–12); remove basics true; four piles fixed. Take one pile, add a card to every pile. |
| Grid | Upstream 2–4 humans; fixed 3×3. | 18 grids (UI 6–32); two-picks/player option false and 2p-only; regular-pack generation versus grid-sized packs. Refill after first selection for 3–4p or two-picks mode. |
| Rochester | At least two humans; one public booster at a time. | Shared packs/player and generation controls, no dedicated parameters; one card per turn in snake order. |
| Rotisserie | At least two humans; entire public pool. | Singleton versus standard-pack source; singleton quota 45 cards/player, exact-pool-size false; standard packs/player inherited. Snake picks. |
| Housman | At least two humans; private hands and public exchanges. | Hand 5, center 9, exchanges/player/round 3, rounds 9 (each UI 1–24); remove basics true; classic/snake order, default classic. |
| Solomon | Exactly two humans; split and choose public piles. | Cards/round 8, rounds 10 (each UI 1–24); remove basics true. Divider alternates; pile sizes unrestricted. |
| Minesweeper | At least two humans; revealed positions reveal orthogonal neighbors when taken. | 4 grids (1–99), width 10/height 9 (1–40), 9 picks/player/grid (1–1600), snake/wrap order; reveal center/corners true, borders false. Removes basics. |
| Silent Auction | Simultaneous secret bids on a public pack. | Packs 3×players (UI 1–128); funds 200; reserve 0; first/second price, default first; ordered funds/cards high/low tiebreakers, default higher funds then fewer cards, finally random. |
| Sealed | Private independent pools, not picking. | Packs/player 6 (UI 4–100); optional per-pack set selections. |
| Team Sealed | Shared team pools and claim/release allocation. | Packs/team 12 (UI 4–100); optional per-pack sets; team assignment. No automatic all-allocated finish. |
| Jumpstart variants | Independent packet combinations. | Choose product: Jumpstart, 2022, Foundations, Avatar, Historic Horizons, Super Jump. Two packets; choice variants offer three per stage. Counts fixed, not generic options. |
| Jump In! | Independent conditioned packet choices. | Selected supported set pool and presets (Current/All/None/older groupings); two packets from offers of three. Color-choice rules are built in. |

Source: [mode menu][dm-menu], [mode dialogs][dm-components],
[server handlers][dm-server], and [session algorithms][dm-session]. Actual Jump In!
set lists are hardcoded/version-specific; a preset named Current is not proof of
current Arena rotation. Only the first three modes are initial app scope, and our
custom Grid deliberately goes beyond the upstream geometry/options.

### Shared parameters and pack-generation controls

The following inventory explains the investigated option families. It is not a
requirement to expose all of them in this app.

| Family | Upstream fields/options and fresh defaults |
| --- | --- |
| Seats/session | `ownerIsPlayer=true`, `maxPlayers=8`, `bots=0`, `randomizeSeatingOrder=false`; manual seating; `isPublic=false`, empty description, `allowSpectators=false`. |
| Assistance | `disableBotSuggestions=false`; separate bot seats and pick suggestions. |
| Pool | `setRestriction` defaults to the latest configured Arena set; multiple sets make a mixed pool; `ignoreCollections=true`. Opted-in collection restrictions intersect participating collections. |
| Normal booster makeup | `colorBalance=true`, `mythicPromotion=true`, `foil=false`, `useBoosterContent=false`; manual `boosterContent` defaults common 10/uncommon 3/rare 1/bonus 1, with actual slots/land treatment dependent on set. |
| Copy limits | `maxDuplicates` off; UI enabling defaults common 8/uncommon 4/rare 2/mythic 1, per generated draft pool. |
| Custom cube generation | `useCustomCardList=false`, `cardsPerBooster=15`, replacement false, duplicate protection true; refill-on-empty false. Duplicate protection is best-effort per printing ID within a pack, not Oracle/name singleton enforcement. |
| Exact packs | `usePredeterminedBoosters=false`; upload exact packs separated by blank lines, with explicit shuffle operation. Takes precedence over other generation. |
| Pack-round sources | `boostersPerPlayer=3`; `customBoosters` entries default/current source, explicit set, random, or shared-random; distribution regular / shuffle each player's packs / shuffle entire pool / stagger by seat. |
| Pick rules | `pickedCardsPerRound=1`, `burnedCardsPerRound=0`, `doubleMastersMode=false` (configured multi-pick only on first pick), `discardRemainingCardsAt=0`. |
| Timing/review | `maxTimer=75` seconds (zero disables), `tournamentTimer=false`, `reviewTimer=0`, `hidePicks=false`. Ordinary-draft timers do not generally apply to alternate modes. |
| Logs/integrations | `personalLogs=true`, `draftLogRecipients=everyone` (also delayed/owner/none), `draftLogUnlockTimer=0`, `sendResultsToCubeCobra=true`; separate spectator/organizer access. |
| Peripheral bracket | `bracketLocked=false`; team-draft bracket integration. |

`preferredCollation` exists in source but is marked unused/not exposed; older help
mentions a maximum-rarity control that is not a current setting. Do not present
either as working functionality. Source: [defaults and generation][dm-session],
[validators][dm-props], and [settings UI][dm-menu].

### Advanced cube format and imports

Draftmancer's [custom-list format][dm-format] supports much more than a flat cube:

- Counted printing-ID sheets; random, print-run (`groupSize`), or striped
  (`length`, stripe-width weights) collation.
- Weighted layouts with named slots, card counts, optional foil, and weighted
  sheet alternatives; ordered/cycling `predeterminedLayouts`.
- `layoutWithReplacement=true`, `withReplacement=false`,
  `duplicateProtection=true`, `refillWhenEmpty=false`, `colorBalance=true`,
  `boostersPerPlayer`, and per-pack/per-selection `boosterSettings` picks/burns.
- Presentation/provenance settings `showSlots`, `showSheets`, `name`, `cubeCobraID`,
  `cardBack`, `cardTitleHeightFactor`; source also supports `botModel`.
- Custom card definitions and supported draft effects, including adding cards or
  packs, extra picks, and burning a voucher after it grants cards.

Replacement makes quantities sampling weights rather than finite physical supply;
refill can replenish exhausted sheets. These are different semantics from our
approved finite cube. Exact-printing duplicate protection is not a global singleton
guarantee, and identical cards in different sheets can have independent supplies.

Current direct import supports Cube Cobra. Its UI defaults to matching versions
and sending results back; experimental custom-format import is off. The inspected
converter imports only the first custom format and not all custom draft actions.
CubeArtisan references remain in places but its direct importer is absent from
this revision. CubeTutor is closed; archived exports remain useful. Moxfield and
Archidekt text exports are useful, but direct API/URL access is a separate commitment.
See [Cube Cobra conversion][dm-cobra] and [import handlers][dm-server].

### Deckbuilding and async differences

Draftmancer lets players build during drafting: acquired cards enter Deck/Main,
with Sideboard as the other organizational partition. Clicking/dragging moves
copies, including a pick-to-sideboard shortcut; bulk actions swap or move all cards.
Its pool UI offers mana value/color/rarity/type sorting, columns, optional two-row
layout, and name/type search that dims nonmatches. It provides statistics and a
sample-hand tool.

Added basics are separate counters, included in exports/sample hands rather than
card tiles. Automatic lands fill toward a default 40-card target using a color
heuristic, not full colored-pip/source optimization. The target is not a minimum-size
legality gate. Export options include Arena text, names-only text, MTGO, collector
numbers, and external handoffs; full export parity is not initial app scope.

Its ready check is not final deck submission. Deck edits update logs; default logs
are shared with everyone. Our private workspaces, explicit locked submission,
construction checks, Trash category, and independent game snapshots are deliberate
differences. See [deckbuilder UI][dm-builder] and [client behavior][dm-client].

Ordinary Draftmancer sessions generally expire about ten minutes after everyone
disconnects; Rotisserie receives special long-lived treatment. Disabling pick
timers alone is not durable asynchronous support. We retain authoritative pod and
workspace state without requiring an online host. Source: [session cleanup][dm-server].

### Public booster data

Draftmancer uses Scryfall card data, [taw/magic-sealed-data][sealed-data], and custom
set-specific factories, referencing the [Collation Project][collation]. It is not
simply a direct MTGJSON-recipe consumer. Its paper-data factory resolves supplied
set/collector identifiers against its own card database; see [factory][dm-paper].

MTGJSON already provides a suitable public source for this app. Set-level optional
`booster` maps product names to configurations. `boosters[].weight` selects pack
compositions; each composition's `contents` gives sheet draw counts. Sheet card
UUID values are sampling weights, not finite inventory. `sourceSetCodes` declares
cross-set dependencies. Sheets can specify foil, color balancing, duplicates, and
fixed content. These recipes approximate products, not complete physical box/print
run correlations. Sources: [set model][mtgjson-set], [configuration][mtgjson-config],
and [sheets][mtgjson-sheet].

[dm-commit]: https://github.com/Senryoku/Draftmancer/commit/c757d1b73c370f88135cb699091f58d8fc3a094b
[dm-menu]: https://github.com/Senryoku/Draftmancer/blob/c757d1b73c370f88135cb699091f58d8fc3a094b/client/src/App.vue
[dm-components]: https://github.com/Senryoku/Draftmancer/tree/c757d1b73c370f88135cb699091f58d8fc3a094b/client/src/components
[dm-server]: https://github.com/Senryoku/Draftmancer/blob/c757d1b73c370f88135cb699091f58d8fc3a094b/src/server.ts
[dm-session]: https://github.com/Senryoku/Draftmancer/blob/c757d1b73c370f88135cb699091f58d8fc3a094b/src/Session.ts
[dm-props]: https://github.com/Senryoku/Draftmancer/blob/c757d1b73c370f88135cb699091f58d8fc3a094b/src/Session/SessionProps.ts
[dm-format]: https://draftmancer.com/cubeformat.html
[dm-cobra]: https://github.com/Senryoku/Draftmancer/blob/c757d1b73c370f88135cb699091f58d8fc3a094b/src/cubeCobraIntegration.ts
[dm-builder]: https://github.com/Senryoku/Draftmancer/blob/c757d1b73c370f88135cb699091f58d8fc3a094b/client/src/App.vue#L1242-L1562
[dm-client]: https://github.com/Senryoku/Draftmancer/blob/c757d1b73c370f88135cb699091f58d8fc3a094b/client/src/App.ts
[dm-paper]: https://github.com/Senryoku/Draftmancer/blob/c757d1b73c370f88135cb699091f58d8fc3a094b/src/PaperBoosterFactory.ts
[sealed-data]: https://github.com/taw/magic-sealed-data
[collation]: https://www.lethe.xyz/mtg/collation/
[mtgjson-set]: https://mtgjson.com/data-models/set/
[mtgjson-config]: https://mtgjson.com/data-models/booster/booster-config/
[mtgjson-sheet]: https://mtgjson.com/data-models/booster/booster-sheet/
