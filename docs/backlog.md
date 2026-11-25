# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Ready to build


### Landscape hand as a turning fan (owner, 2026-11-25; after the draft bots; replaces the look of gallery 571)

- In landscape your hand becomes a fan like the desktop's (WEB-073, WEB-074, WEB-103): between your piles and the action area (TBL-144's layout otherwise kept), cards turned a little and overlapping, standing over the battlefield and partly below the screen's bottom, no hand-row background; cards from other zones at its right end after the divider (ZON-079). Portrait keeps its row.
- As many cards as fit with each still easy to tap (about 9 on a narrow phone, 12 on a wide one) make a still fan. With more, a sideways drag turns the fan like a wheel (a flick carries on a little and slows down); cards turned away sink below the screen's bottom along the curve, no fade. It stops at the ends (the first card no further right than the fan's right end, the last no further left than its left end; never wraps; the hand keeps its order, ZON-081). A small "+N" at an end with cards turned away; the hand count stays. Up or down drags a card as now (ZON-077); tap, double-tap and hold unchanged. A raised card stays raised when turned away. A drawn card lands in its place; if that is turned away the "+N" counts it, and the fan doesn't turn by itself.
- Catalog: ZON-081, TBL-144, TBL-238 (the hand row part) and new entries; gallery 571 re-recorded; e2e covers turning and playing from a turned fan.

### Draft history ratings review (owner, 2026-11-25; after the landscape fan; needs the bots' rating table)

- Every draft history dialog gets 17Lands ratings once your own draft is done (DRF-119), only in a single-set pod whose set has ratings (same table and rule as the draft bots); nowhere before that. Booster and Glimpse's draft history (DRF-165, DRF-207), Arena's draft history (DRF-289) and the other modes' Picks dialog (Winston, Winchester, Grid, Rochester, Rotisserie, DRF-160, DRF-227; only cards shown face up get a chip, card backs none). Each rated card shows a small chip centred on its bottom edge with its GIH WR ("58.4%"); unrated cards none. The chip's colour is its rank within the set in five steps (top 10%, next 15%, middle, next 15%, bottom 10%), never green or red (keep/burn colours): gold at the top down to grey; a couple of colour options in the gallery for the owner to choose. A "By rating" chip in each dialog's header (beside "Returning packs only", DRF-209, where it shows) sorts best first: each pick's offer (keeps ✓ and burns 🔥 stay marked wherever they land), or each opponent's picks; remembered per device. Verify in the code which modes have which dialog (the catalog says DRF-228). Gallery (owner): one entry per draft mode's history dialog in its post-draft state with ratings (Booster, Glimpse, Custom booster, Arena, Winston, Winchester, Grid, Rochester, Rotisserie), plus the colour options. Purpose: self-evaluating picks.

### Earlier

Playtest and gallery notes (owner, 2026-11-11):

- Engine non-null hygiene (owner, 2026-11-11, lower priority): the engine's ~107 `!!` are read in the owner's engine review (Needs discussion), not swept blindly.

Queue (owner, 2026-11-09 evening), in any order: the items below. The evidence selection's running total is not wanted for now (owner).

Parser report decisions (owner, 2026-11-09, relayed by session async-tt-wiz-74), queued after the engine items; not built ahead of them without asking the owner:

- Support set by set, the rest (SYS-082 is built: the catalog's marks count a card as supported by a printing in a supported set or on the single-card list, `arcanum-tools/src/main/resources/arcanum/tools/supported-cards.txt`, first the owner's Powered Synergy Cube; the server's set pods and the app follow them). Still to do: compare the list with the owner's own edited prod cube, in case they changed anything else; and check how a game or a draft already holding cards that are no longer supported behaves, raising that with the owner rather than deciding. This changes what players can use: catalog entries change and e2e covers it.

Old forms only read now, found at the wipe (2026-11-09; read, not run; can be done any time after the wipe release):

- `EngineEventText.hidden`'s line-only branch (a rewind's own line start): no game holds such an event after V74 and no caller found makes one. `GameDevice.finish` and `Authority.make` don't refuse one, though: a draft sent empty while a line is due would make it, so refuse that first. Its test `anOldRewindLineStartIsShownNowhere` names HIS-086, whose entry stays as it is; agree with the owner how that test goes.

## Waiting for a repro or more detail (owner playtests, from 2026-11-05)

Each owner-reported bug gets an e2e test that fails before the fix.

- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Cost phrase highlight (STK-047; owner 2026-11-12, Demonmail Hauberk's "Sacrifice a creature" while picking the sacrifice): didn't reproduce on 2026-11-12 (the parser's spans and the pick bar light "a creature" as STK-047 says). Owner 2026-11-13: wait until it happens again; it may have been a misreading of the UI or a UX problem that further playtesting shows.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).
- Parked until the owner has tested 0.3.0: the bubbles saying why something shook may get in the way; maybe the shake alone is enough (TBL-212).
- Parked until the owner has tested 0.3.0: a stack whose cards are all tapped counts "0/3" (TBL-054).
- Parked until the owner has tested 0.3.0: the desktop quick look works on every draft card (every mode's cards and the Picks dialog), not only the pack you pick from (WEB-117).

## Specified, not started

- Booster randomness check (owner 2026-11-13; lowest priority, after all other queued work, for peace of mind): a sealed DFT pod in prod ("Vroom vroooomm!", 9 packs) gave one pool Lifecraft Engine ×3, Demonic Junker ×2, Full Throttle ×2. The recipe and sheet weights were checked (MTGJSON play booster, rares weight 8, mythics 4, seed from SecureRandom through RandomV1's SHA-256 stream), and the pattern is about 1 in 230 for a pool of 12 rares, so nothing points at a bug. Check the real generator statistically anyway: generate about 100,000 DFT sealed pools (9 packs) with `SetBoosters.generate` from the pinned catalog's DFT recipe, test each sheet's card frequencies against its weights (chi-squared per sheet, rare-or-mythic split, wildcard and foil slot rarities), test independence between packs and slots (no correlation between consecutive draws), and report where the observed pool falls in the distribution of repeats. A JVM tool or test reading the local pinned MTGJSON file (no real card data committed); report the numbers to the owner in plain words. No code change unless the check finds a fault.

## Needs discussion

- Own-picks history for the other modes (owner, 2026-11-25): Winston, Winchester, Grid, Rochester and Rotisserie's History icon opens only the opponents' picks (DRF-160), not your own picks with what you were offered as Booster's (DRF-207) and Arena's (DRF-289) do; discuss giving them that.
- Owner's own review of the Arcanum engine (owner, 2026-11-11): the owner reads the engine code by hand and discusses improvements with Claude, looking for things that slipped in while unreviewed, like the per-creature `Engine.combatCost`. The public API part was discussed on 2026-11-12 and settled in the engine API rework (merged 2026-11-12). Still open: reading the engine's code itself, including its ~107 `!!`. Nothing there is changed before that discussion.


- Warn before a useless ability (report 82PG8P): warn, never block, before paying for an ability that would do nothing right now (Frodo's first ability when he is no longer a Citizen; legal, checked when it resolves, and the type may still change in response).

- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains (board wipe, big turn changes, 40–80 ms on the Pi), and on a tentative line nothing may be shown before the whole outcome passed the information rule; if the own-action measurements (SYS-030) still show chains as the slow cases, a plain callback on `perform` handing over changes as they are recorded rather than a channel. Owner to decide.
- The step arrow's pass worked out ahead of time (optional, decided later; owner 2026-11-06: not now, after the End turn speed work): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide on the own-action measurements (SYS-030).

- Summoning-sick permanents in the same battlefield group as their non-sick copies (owner idea, 2026-11-07; owner testing first): one group regardless of sickness with a "2 sick" badge, attacks picking a non-sick member first, so groups don't reshuffle after the turn.

- Choosing hand cards in the hand itself (owner, 2026-11-24; discuss and analyse first, not to be built now): when a choice asks for cards from your hand (a discard while something resolves, a cost discarding or exiling a hand card), picking them straight from the hand row might be better than picking them in the choice panel. General rule meanwhile (owner): whenever you can't interact with your hand cards, they are dimmed (ZON-095), never during animations: avoiding flicker matters more. Questions for the analysis: how chosen cards look in the hand, how a choice with a count (discard two) or a minimum/maximum is confirmed, how it works with the hand band scrolled or crowded, on the desktop, and for choices that mix hand cards with cards from other zones.

- Rule references the compiler can check (owner, 2026-11-15; parked, to think about, not now): replace plain-text `// CR 613.4c` citations with annotations naming the rule number, store each cited rule's text or a checksum of it, and have a build job list which rules the code relies on and which of them changed when the Comprehensive Rules pin moves, so a renumbered or reworded rule can't go unnoticed (today's citations are comments the compiler doesn't check; the 2026-11-15 pin move uses a one-off citation delta tool in arcanum-tools instead).

## Needs design

- Stats colours (owner, 2026-11-19): separate stats for cast costs and for cast costs + abilities (colour identity), instead of today's one view by colour identity (DRF-230, DRF-117).
- Multicolour cards in the Stats colours (owner, 2026-11-19): show split colours, or add a multicolour card to each of its colours' stats, instead of one Multicolor bucket (DRF-230, DRF-117).

## Later (nice to have)

- Repeating a loop ("infinite" combos; owner 2026-11-14): record a sequence of actions as a macro and repeat it N times, with the engine handling large numbers (mana, life, damage, counters) and many tokens (a counted token pile instead of a million objects). **Don't build until properly specified** (owner: "needs more design").

- Automatic answers (owner wishlist, 2026-11-12): remembered answers to "you may" choices (always yes / always no per ability), perhaps for other repeated choices too, like the remembered trigger orders below.

- Remembered trigger orders (owner, 2026-11-06): conveniences like "always resolve these triggers in this order".

- Choose a card's printing (art) in decks and cubes.

- Desktop zoom range (owner, 2026-11-25): if the 4× desktop battlefield cards turn out a problem, allow zooming further in both directions, past today's least 0.325× and largest 2× (WEB-084, TBL-010). Not a blocker.
