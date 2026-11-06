# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Build order (owner, 2026-11-05: one implementor at a time, small items batched, full suites after each batch)

2026-11-06 playtest round 2: worker A = playback stop on return, assumed blocks branch; worker B = Bushy Bodyguard walkthrough + question panel audit, then pinned inspect, damage preview badge, rotation leftover; worker A follow-up = undo after combat back-and-forth; worker C (after B) = response mode names/colours, main button width, slow action indicator, deck service burst, Automatum screenshot gallery; worker B again = trigger order always asked + combat copy taps (not blocking the a+c deploy); next free worker = End turn engine speed. Deploy to test when A and C are merged and suites pass.

## Ready to build

- Response modes and colours (owner playtest, 2026-11-06): rename Full control / Normal / Auto-skip to Control / Normal / Quick everywhere (quick action, Settings, texts; playback's "Full control" toggle becomes "Control"); the response mode and Assume resolves cells each fill in a colour by how much control you keep: deep blue = Control / Assume: Never, violet = Normal / Assume: No mana, amber = Quick / Assume: Always, white text with the icon. Changes TRN-059, STK-036, HIS-095, LOB-113 and entries naming the modes.
- Main button width (owner playtest, 2026-11-06): the bottom-right main button keeps one fixed width (e.g. 112 dp) whatever quick actions show; the quick actions share the rest equally. Changes TRN-017.
- Automatum screenshot gallery (owner, 2026-11-06): a phone-friendly page at https://desktop-b2kalvm.tail27cccc.ts.net/aa-gallery/ with one representative screenshot per Automatum UI item (number, name, caption, catalog IDs; items changed since the screenshots-approved tag first with a "changed" badge; tap for full size; page shows its commit). A list file next to the screenshots names each item and its shot; a build check fails when a listed shot is missing or any engine decision kind has no entry; AGENTS.md gains the rule that new Automatum UI gets an entry. A script rebuilds the page; the coordinator runs it after merges. Complements, never replaces, the screenshot review.
- Deck service burst (owner playtest, 2026-11-06, bug): choosing a deck once showed "The service is busy. Wait a moment, then retry." — the server's limit is 600 deck-service requests per account per minute, so the app sent a burst or looped. E2e first: count deck-service requests while choosing a deck in the lobby (fails above a handful); find and fix the burst. No entry change expected.
- Slow action indicator (owner, 2026-11-06): the main button (End turn, Next step, Resolve, Attack/Block) and card taps that send an action show a "working" sign (e.g. a shimmer on the main button) only when the result hasn't arrived ~150 ms after the press, and once shown it stays at least ~300 ms (no flicker; instant actions look as today); press feedback itself is immediate. Tune thresholds against the app's own action timing logs; tests use the test clock (no elapsed-time asserts). Not for playback. Changes TRN-017/TRN-063 (new clause).
- End turn engine speed (owner, 2026-11-06): End turn takes ~660–700 ms on the owner's phone, ~490–540 ms of it in the engine (report KWKB3Y, "Own action what=endTurn"). Profile End turn and step passes on a realistic board (JVM benchmark/profiler; desktop numbers relative), find the biggest costs (e.g. recomputing layers at every step) and take the easy gains, keeping behaviour identical (all arcanum/protocol suites green); report before/after numbers. R5 (precomputing passes while idle) stays a later option, not now.
- Trigger order always asked (owner, 2026-11-06): when two or more of your triggers go on the Stack at the same time, you are always asked their order, in every response mode (today Quick/Auto-skip orders them itself); triggers that are exactly identical need no question. Doesn't block a redeploy. Changes STK-036, STK-030 and the order-triggers entry.
- Combat copy taps (owner, 2026-11-06): a first tap on a combat copy (staged attacker, or a placed blocker, also in assume blockers) only selects it (CMB-032); a second tap on the selected copy unstages the attacker / removes the blocker (no Combat menu in between). Today a single tap on a staged attacker's copy unstages it. Doesn't block a redeploy. Changes CMB-003, CMB-032, CMB-034, CMB-040, TBL-116.

## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).

## Specified, not started

(none)

## Needs discussion

- Token look (owner playtest, 2026-11-06): consider a different look and feel for tokens; inspect currently clips a token's borders.
- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later; owner 2026-11-06: not now, after the End turn speed work): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

## Later (nice to have)

- Remembered trigger orders (owner, 2026-11-06): conveniences like "always resolve these triggers in this order".
- Play your first turn while the opponent is still deciding on mulligans, without rollbacks when they mulligan, as long as nothing you do interacts with their hand or library (owner, 2026-11-05; replaces the dropped assumed keep). Not high priority.

- Choose a card's printing (art) in decks and cubes.
