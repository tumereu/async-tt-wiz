# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Build order (owner, 2026-11-05: one implementor at a time, small items batched, full suites after each batch)

Owner playtest of 0.2.0-beta4, 2026-11-07 (overnight; push master after each merge; no deploys):

- C1 Play show flight (HIS-112): after the show time it flies in about 0.25 s (scaled by Card move speed) with an accelerating curve to the Stack's top card place and size; its line goes as the flight starts, the card fades out over the flight's second half and is gone when it lands, removed at once on this screen without waiting for the device's next board (cause of the "stuck" look: the overlay waited for the worker thread's publish).
- C2 Game end dialog (TRN-065, TRN-064, LOB-046, STK-044, LOB-111): every ending (rules, concession, agreement) shows each player a dialog in the middle of the screen: "You won" / "You lost" / "Draw", the reason ("Bob's life reached 0", "You drew from an empty library", "Bob got 10 poison counters", "<card>'s effect", "Bob conceded", "Agreed: <reason>", "Both players lost at once", "An endless loop"), the eye and OK; a rules result stays greyed "waiting for <name> to catch up" until both saw it (finality unchanged). It stays until that player taps OK (every reopen, never dismissed in the background); the player whose own action ended it (the conceder, the one accepting a suggestion) has OK'd already. Your OK takes you to the lobby; the next game can start only once both have OK'd (server keeps each player's OK).
- C3 Pinned inspect polish (TBL-131, TBL-104): the line between cards spans the whole width in pinned mode and during the slide; change blocks keep their final layout through the slide (no relayout at its end); faint ‹‹ ›› hints centred at the left and right edges of in-game inspects where pinning works.
- C4 Tokens in Inspect (TBL-104, TBL-106): Inspect draws tokens with normal card corners (no cut corners; TBL-018 stays for the table).

## Ready to build


## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).

## Specified, not started

## Needs discussion

- Opening a game from its very first event (found 2026-11-06, worker A): loading a game's recent events starting at its root gave a wrong board (turn 1 shown with the keep decision open, Keep ignored); the load now stops at the first event, which avoids it, but why loading from the root goes wrong is unexplained. Investigate before relying on root loads.
- Token look (owner playtest, 2026-11-06): consider a different look and feel for tokens (Inspect's clipped corners: C4).
- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later; owner 2026-11-06: not now, after the End turn speed work): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

- Summoning-sick permanents in the same battlefield group as their non-sick copies (owner idea, 2026-11-07; owner testing first): one group regardless of sickness with a "2 sick" badge, attacks picking a non-sick member first, so groups don't reshuffle after the turn.

## Later (nice to have)

- Remembered trigger orders (owner, 2026-11-06): conveniences like "always resolve these triggers in this order".
- Play your first turn while the opponent is still deciding on mulligans, without rollbacks when they mulligan, as long as nothing you do interacts with their hand or library (owner, 2026-11-05; replaces the dropped assumed keep). Not high priority.

- Choose a card's printing (art) in decks and cubes.
