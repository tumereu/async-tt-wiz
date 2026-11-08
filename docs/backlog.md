# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Build order (owner, 2026-11-05: one implementor at a time, small items batched, full suites after each batch)

2026-11-08, one at a time: (1) built: branch stop, landscape hand, View deck; (2) built: presets, set Redownload, Winchester tables, Stats mana sources; (3) built: catalog review follow-ups (single-version parts that would invalidate stored data wait for the owner).

## Ready to build

Catalog review follow-ups (owner, 2026-11-08; the catalog was cleaned on w/catalog-cleanup, these change code):

- Single version, rest (owner approved 2026-11-08 after a read-only prod check: 1 booster pod in setup, 0 grid or cube pods, 0 games with a null starter, 3 deck-save request records, 6 game seats with the three assume flags, no account defaults; for the release after beta6, as its third batch): delete GridEngine's plain rotation and resume and the grid boards' older-server defaults (DRF-079's "Pods started before this change keep plain rotation"), the preparation snapshot's null-means-Random reading, and the deck service's pre-I08 request identity; convert "Assume resolves" from its three stored flags to one stored value with a Flyway migration of engine_seat.settings and account.play_defaults (nothing lost, games untouched); phones redownload their card records once in that update (a client database migration), then the older-index-version check and the "downloaded again once" parts of LOB-110 and LOB-125 go. Cubes stored as plain arrays are the current format, not a leftover: keep them.

Invitation notifications (owner, 2026-11-08, for beta7):

- Challenge and invitation notices (DRF-137, LOB-072, LOB-077, DRF-123, LOB-025): a pod challenge sent while the recipient is still building notifies at once (Invitations channel) "<pod>: <name> challenged you · submit your deck to accept", opening the pod's Games tab; when they then submit their deck the held challenge becomes acceptable with no notification (today releaseChallenges in PodService records one). A challenge to someone who already submitted notifies as now. Clearer texts: "New invitation" becomes "<name> invited you · <game name>" (a pod challenge: "<pod>: <name> challenged you"), "New pod invitation" becomes "<name> invited you to <pod>".

Small, not urgent (owner, 2026-11-08):

- Opponent's modes and X on the Stack, shown and tested (STK-057, STK-018; test and screenshots only, no entry change): screenshots of the other player's modal spell and an X spell, each with the battlefield line and the Stack list, one gallery.tsv entry each; a unit test that the line and the Stack list show the chosen modes (X is already covered by StackLineTest). If the app doesn't show them as the entries say, report it.

## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).

## Specified, not started

## Needs discussion

- Opening a game from its very first event (found 2026-11-06, worker A): loading a game's recent events starting at its root gave a wrong board (turn 1 shown with the keep decision open, Keep ignored); the load now stops at the first event, which avoids it, but why loading from the root goes wrong is unexplained. Investigate before relying on root loads.
- Token look (owner playtest, 2026-11-06): consider a different look and feel for tokens (Inspect now shows them with normal corners).
- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later; owner 2026-11-06: not now, after the End turn speed work): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

- Summoning-sick permanents in the same battlefield group as their non-sick copies (owner idea, 2026-11-07; owner testing first): one group regardless of sickness with a "2 sick" badge, attacks picking a non-sick member first, so groups don't reshuffle after the turn.

## Later (nice to have)

- Remembered trigger orders (owner, 2026-11-06): conveniences like "always resolve these triggers in this order".
- Play your first turn while the opponent is still deciding on mulligans, without rollbacks when they mulligan, as long as what you did comes out the same on top of their real opening (owner, 2026-11-05; replaces the dropped assumed keep). Not high priority. Spec draft for the owner's review: `docs/specs/first-turn-during-mulligans.md` (2026-11-08).

- Choose a card's printing (art) in decks and cubes.
