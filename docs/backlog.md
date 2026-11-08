# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Build order (owner, 2026-11-05: one implementor at a time, small items batched, full suites after each batch)

2026-11-08: beta6 and beta7 batches all built (gameplay notes, draft notes, catalog review follow-ups, draft history / Winston tables / Rotisserie queue, Side + Maybe, single-version rest, invitation notifications).

2026-11-08 beta7 playtest notes: start after the KX8GYS hotfix is deployed. Bug first, then the table batch; the draft stats and card states batches each have their own implementor. All merged, full suites and e2e green → prod release (owner approved in advance).

## Ready to build

Bug (owner, 2026-11-08; reports P2XUER, 3WWPA8):

- A player's first main phase was skipped, cause not found: in turn 13 of a beta7 game their phone's passes from main 1 to declare attackers reached the server 30.0 s after the main phase began (server 11:33:44.290 → 11:34:14.284), and they say they didn't press. It doesn't reproduce (`MainPhaseStaysTest`: the same race with the other player's End turn, then 35 s at main 1) and the device has no way of its own to do it, so what is left is a press they didn't register, made at 11:34:14 or (if the phone's uplink stalled) just as main 1 arrived. Nothing to build until it happens again: main-button presses are now in the app log with what the button said and the step, and a pass the device itself makes at your main phase is a warning (LOB-089). At the next report read those lines around the passes; a press line means the tap happened (then the Your move cue and the main phase undo point below are the answer), no press line with passes means a fault to fix.

Main button presses on the record (owner, 2026-11-08; HIS-101, HIS-084, HIS-099, HIS-005):

- The first pass of a main button press records what was pressed ("To combat", "To end step", "End turn") as a nullable field of its event, as `mode` is; End turn's other passes stay plain. Undo points and their texts read that field instead of the phone's memory, so "You went to combat" and "You ended the turn" survive a restart, reopening the game and another device. The event doesn't record the result: that is in its changes. Older events have no mark and aren't points; existing games keep working.

Small batch, beta9 playtest notes (owner, 2026-11-08):

- Draft history (DRF-165): holding a card shows it over the history dialog (today the preview is drawn outside the dialog, behind it; e2e). The History button also shows during Build and after it, not only while drafting.
- Panel text (STK-047, STK-044, STK-023): a panel's sentence shows up to 4 lines (today 2, AutomatumDock); when it is still cut, a tap on it shows it whole, scrolling past half the table's height.
- Card list editor (LOB-105, LOB-107, DRF-003): a filter field at the top of the Cards tab with search's syntax and chips (LOB-108, LOB-119), sections showing only what matches; +1, -1, Remove all and Move to... get icons.

Table batch 2, beta9 playtest notes (owner, 2026-11-08):

- Hold to pause (HIS-021, HIS-112, HIS-113, HIS-094): a finger held on the table pauses whatever your view is waiting on: a play shown, a combat hold, catch-up playback between events. It goes on when lifted; only your view waits, never the game. A pause icon shows in a corner meanwhile, with a quick fade in and out. Holding a shown card also inspects it. Skipping a shown play or combat hold becomes a double tap; a single tap does nothing, so it isn't triggered by accident.
- Declaring blockers like attackers (CMB-028, CMB-034, CMB-007, CMB-012, CMB-040): creatures that may block glow red-orange as attackers do. For the whole step each attacker has a dashed blocker slot on its lane, brighter on the attackers a selected or dragged creature may block, and a drop target. Declaring attackers gets the same dashed slot on the active lane. A short "Choose blockers" label on the band until the first block. Try it and see: the whole step was the owner's call, the risk a busier band.

Pod chat (owner, 2026-11-08; new entries; LOB-066, LOB-072, DRF-012), after the batches above:

- One chat per pod for its members, kept as long as the pod, with a chat button and unread dot on the pod screens; a notification on the Draft activity channel. In a pod game the chat panel has two tabs, Game and Pod. Pod messages show no bubbles on the table, but they do light the dot on the table's chat button, and a tab with a message you haven't seen is highlighted while you are on the other (dismissing a game message's bubble counts as seeing it). Needs server storage, its limits as a game chat's. No "Read" marks unless the implementor finds them plain for several members; list it under choices.

Castability, costs and payment (owner, 2026-11-08, report KX8GYS; spec docs/specs/castability.md, reviewed by session async-tt-wiz-dc before merge):

- Deny a cast only with proof, count every mana ability, pay with any mana ability at every payment, smallest cost + {?}, selections narrowed by what can be paid, partial Autotap override that never blocks a cast, no-false-denial tests (TBL-117, TBL-118, TBL-122, ZON-079, ZON-080, STK-023, STK-024, STK-047, STK-052, SYS-053).

Arcanum engine generalisation (owner, 2026-11-08; after castability merges; reviewed by session async-tt-wiz-74 before each merge; probes ~/projects/atw-cache/engine-genericity-probes-2026-11-08.kt; every card of `:arcanum-tools:setSupport` stays supported; mutate, banding, day/night and the Saga/Aura/Army/Human checks are not in scope). Stored games hold definitions, states and events in a strict format (`EngineJson`). The owner approved one wipe for the release carrying all four items, so the format breaks only once: each item only adds, keeping existing games loading; a last step, after all four, removes the old stored types and fields (per-verb `EventPattern` and event types, `ObjectFacts.foretoldTurn` and `plottedTurn`, `ObjectMark` types and fields) with a single wipe migration. The owner deploys that release themselves, and its summary says plainly that it wipes games. In this order, one implementor at a time:

- Copiable values include layout and all faces; designations stay on the object. Fixes: a permanent becoming a copy of a Room has both doors locked and unlockable (CR 709.5b, c, e); a token copy of a Room has two locked halves (709.5b, d); a copy of a preparation creature can become prepared (722.2b, 722.3a); a token copy of a double-faced permanent can transform (707.8a). Tokens and copies already in stored games keep their single face.
- Generic trigger events: one `KeywordActionDone(action, players, objects)` and one `GotMark(mark, objects)`, so a new verb or mark needs no new event type; "whenever" triggers on explore, proliferate, investigate, discover, collect evidence, exert and foretell, and on gaining marks, with general parser wordings kept small.
- A mark rules table beside KeywordExpansions, and one shared exile-cast permission path (Foretell, Plot, Prepare). Fixes: a card foretold through granted foretell is castable later (CR 702.143a); "becomes plotted" works on an exiled card; foretell's cost and timing can be changed.
- With any item or alone (nothing stored): `ObjectFilter.ChosenName` and `RememberedName` match either name of a split card (CR 709.4a; `Evaluator.kt` compares the whole "A // B" name; probe `aSplitCardHasTheChosenNameIfEitherHalfHasIt`).
- Special actions as a general concept: through the normal step runner without the stack, offered from one list, replacing `Payment.finishSpecialAction`'s allow-list and the `Decision.Manual` fallback, with one turn-face-up path. Fixes: Manifested clears when turned face up (CR 701.40a); Cloaked clears too (701.58a).

Small, not urgent (owner, 2026-11-08):

- Opponent's modes and X on the Stack, shown and tested (STK-057, STK-018; test and screenshots only, no entry change): screenshots of the other player's modal spell and an X spell, each with the battlefield line and the Stack list, one gallery.tsv entry each; a unit test that the line and the Stack list show the chosen modes (X is already covered by StackLineTest). If the app doesn't show them as the entries say, report it.

## Ready after the e2e framework (owner playtest, 2026-11-05)

Start none of these until the E2E framework above works. Each owner-reported bug gets an e2e test that fails before the fix.

- Fix: the "Rest → <target>" combat quick action declares the attack; it must only stage the remaining attackers (CMB-028; no entry change). It did not reproduce in 3 scenarios: keep it until it happens again with more detail.
- Stack ability token (STK-046): its art is clipped with black at the bottom; the owner describes it later (holding it to inspect is covered by e2e StackAbilityTest).

## Specified, not started

## Needs discussion

- "Can't do" explainer (owner, 2026-11-08; TBL-118 today says nothing by design): a short-lived chip or bubble rising from a shaken card, saying in one plain line why ("Costs more than you can pay"). Maybe for every shake, starting from castability's unselectable targets. Where it shows and how long are undecided; the engine's refusal reasons are technical, so the wording needs its own step. Also (report 82PG8P): warn, never block, before paying for an ability that would do nothing right now (Frodo's first ability when he is no longer a Citizen; legal, checked when it resolves, and the type may still change in response).

- Opening a game from its very first event (found 2026-11-06, worker A): loading a game's recent events starting at its root gave a wrong board (turn 1 shown with the keep decision open, Keep ignored); the load now stops at the first event, which avoids it, but why loading from the root goes wrong is unexplained. Investigate before relying on root loads.
- Token look (owner playtest, 2026-11-06): consider a different look and feel for tokens (Inspect now shows them with normal corners).
- Arcanum engine: a streaming `perform` — called with a state, it returns a channel/stream of updates (changes in order, decisions as they come) instead of one finished `Outcome`, so the host can show the quick first results at once and keep the UI responsive while a long chain (many triggers, state-based actions, loops) resolves. A candidate fix for UI-feels-slow cases; keep every update reversible (D59) and the engine stateless; undo mid-stream = discard the stream's remaining updates and revert the applied ones (owner, 2026-11-04). Architecture check (2026-11-04) recommends not now: it speeds up only long chains; if R1's measurements after R2–R3 still show chains as the slow cases, a plain callback on `perform` rather than a channel (`engine-responsiveness.md`, last section). Owner to decide.
- R5 of `engine-responsiveness.md` (optional, decided later; owner 2026-11-06: not now, after the End turn speed work): while the player looks at the board, the idle engine thread works out their step-arrow pass on a probe, so the tap only applies the result (frame-fast passes, some battery cost). Decide after R1–R3's measurements.

- Summoning-sick permanents in the same battlefield group as their non-sick copies (owner idea, 2026-11-07; owner testing first): one group regardless of sickness with a "2 sick" badge, attacks picking a non-sick member first, so groups don't reshuffle after the turn.

## Later (nice to have)

- Remembered trigger orders (owner, 2026-11-06): conveniences like "always resolve these triggers in this order".
- Play your first turn while the opponent is still deciding on mulligans, without rollbacks when they mulligan, as long as what you did comes out the same on top of their real opening (owner, 2026-11-05; replaces the dropped assumed keep). Not high priority. Spec draft for the owner's review: `docs/specs/first-turn-during-mulligans.md` (2026-11-08).

- Choose a card's printing (art) in decks and cubes.
