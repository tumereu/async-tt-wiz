# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Build order (owner, 2026-11-05: one implementor at a time, small items batched, full suites after each batch)

Nothing running.

## Ready to build


## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).

## Specified, not started

(none)

## Needs discussion

- **IMPORTANT: Control mode and assumptions (owner, 2026-11-06; to decide at the computer).** Found by worker H: in Control the other phone assumes your pass right after your own spell (your device holds priority there, Normal/Quick fold the pass into the action), so casting a second spell races that assumption. Discussion so far:
  - Rejected: Control as a game event (toggling during playback would land at odd points in the history) and Control as a shared server setting that refuses the other phone's assumptions (one player's Control would make the other wait everywhere).
  - Owner's direction: Control stays private. (1) Any action you take that you could respond to in Control (cast, activate, choices that put things on the Stack) carries "holding priority" in its input: the other phone never assumes your pass there (still offers Assume), and the server knows you are the one to act at the head. (2) In Control your own phone assumes nothing for the opponent automatically: it waits with Assume, as your own things wait for your pass, so A can leave the game "waiting for B's reaction" after triggering B's obvious combo, and B is notified.
  - Owner: on your own turn, waiting with Assume at the opponent's point leaves you the actor the server expects; resolving it yourself resolves it for both (if not, a bug). Only off-turn triggers can annoy the other player. To check against the code: today such a wait makes the head the opponent's decision (AsyncPlay.waitsFor: whoever's attention the head has), which would make it their move and notify them.
  - Open: whether (2) waits at every point or only with something on the Stack (empty-Stack step passes during your turn, and today's always-assumed points at your end step and right after your attack, TRN-070); your own off-turn triggers caused by the other player (nothing carries the mark, so their phone assumes); how this relates to catch-up playback's own Control toggle (HIS-095) — the owner thinks one of us misunderstands this; clarify first.
  - Also check: whether a wrong guess today gives a clean branch or your second spell refused ("That wasn't taken") — an e2e test of the race.
- Opening a game from its very first event (found 2026-11-06, worker A): loading a game's recent events starting at its root gave a wrong board (turn 1 shown with the keep decision open, Keep ignored); the load now stops at the first event, which avoids it, but why loading from the root goes wrong is unexplained. Investigate before relying on root loads.
- Token look (owner playtest, 2026-11-06): consider a different look and feel for tokens; inspect currently clips a token's borders.
- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later; owner 2026-11-06: not now, after the End turn speed work): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

## Later (nice to have)

- Remembered trigger orders (owner, 2026-11-06): conveniences like "always resolve these triggers in this order".
- Play your first turn while the opponent is still deciding on mulligans, without rollbacks when they mulligan, as long as nothing you do interacts with their hand or library (owner, 2026-11-05; replaces the dropped assumed keep). Not high priority.

- Choose a card's printing (art) in decks and cubes.
