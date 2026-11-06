# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Build order (owner, 2026-11-05: one implementor at a time, small items batched, full suites after each batch)

2026-11-06 playtest round 2: worker A = playback stop on return, assumed blocks branch; worker B = Bushy Bodyguard walkthrough + question panel audit, then pinned inspect, damage preview badge, rotation leftover.

## Ready to build

- Playback stop on return (owner playtest, 2026-11-06, bug): when your catch-up playback is stopped at a decision of yours and you leave the app (background, or reopen the game), coming back shows that same stop, live, without "<name> has made moves", also when the other player has events after it. E2e first (foreground and reopen, with and without their later events). Changes nothing in HIS-094/HIS-095 (they already say so).
- Assumed blocks answered differently (owner playtest, 2026-11-06, bug): after you assumed blocks (CMB-040) and the defender declared different ones, your table shows the branch (HIS-097: take-back animation, "Bob blocked differently — your later actions were taken back"), not "<name> has made moves". E2e first.
- Pinned inspect (owner playtest, 2026-11-06): opens by swiping either way (right too, for cards at the side of the table); the card always ends at the left edge with the change lines right of it; an unchanged card shows a faded centred "No changes"; each change is its own chip/line block (no change split confusingly over lines), slightly smaller text; shrinking reuses the loaded picture, scaled while drawing, never "Downloading artwork". Changes TBL-131.
- Combat damage preview badge (owner playtest, 2026-11-06): one badge per creature so it fits at any zoom: a red splash with the damage number ("3", no minus) when it survives, a black skull with the number inside when it dies. Changes CMB-029.
- Rotation leftover (owner playtest, 2026-11-06, bug): tapping a combat copy shows "Rotation not queued. Reconnect and try again." — the old by-hand rotate path still runs; remove it entirely so taps do only what CMB-003/CMB-032 say. E2e first.

## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).

## Specified, not started

(none)

## Needs discussion

- Token look (owner playtest, 2026-11-06): consider a different look and feel for tokens; inspect currently clips a token's borders.
- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

## Later (nice to have)

- Play your first turn while the opponent is still deciding on mulligans, without rollbacks when they mulligan, as long as nothing you do interacts with their hand or library (owner, 2026-11-05; replaces the dropped assumed keep). Not high priority.

- Choose a card's printing (art) in decks and cubes.
