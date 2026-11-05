# Async play: choices waiting for the owner (I-D56)

Async play (I-D56) and engine state as the game state (I-D54) are built: every game is played that way, and the
behaviour is in the feature catalog (`docs/features/`). What is left here are the choices made while specifying and
building them that the owner hasn't confirmed yet, one line each with the catalog entries they shape. Choices the
catalog already states plainly are confirmed in the catalog review; they are listed only where they deserve a separate
look. The full build logs (and the section numbers older code comments cite, e.g. "async-play §29") are in git history:
`git show b524217c:docs/specs/async-play.md` and `git show b524217c:docs/specs/engine-state-architecture.md`.

When the owner confirms or changes a line, delete it here (a change also goes to `docs/backlog.md`). Delete this file
once nothing is left.

## Undo and information

1. Random results (coin flips, die rolls, random choices; not a shuffle's order) count as "something new shown to you":
   undo past them needs the other player's approval (HIS-087), and the information rule treats them like hidden cards
   (TRN-067, TRN-071). The server checks only cards, not random results (a modified client gains nothing it couldn't by
   waiting).
2. A wait for the other player's decision from a source you can't see makes undo past it a request (HIS-087).
3. An Undo that isn't free opens the Undo dialog with its target chosen; rewinds have no event-count limit (HIS-084,
   HIS-099). A target further back than the device keeps (64 events before the earlier local head) always needs a
   request.
4. While an undo request is open, answers at decision stops wait; playback, and the confirmations it makes without
   asking, go on (HIS-100). An open undo request is the other player's move: listed, notified "Your move" and reminded
   like any move (TRN-058, LOB-072, LOB-075).
5. The game secret (where every line's random results come from) is held by both devices, trusting the unmodified
   client (HIS-042).

## Assumptions, confirmation and branches

7. Live = the green presence dot; a wait begun while the other player was live turns async 60 s after they were last
   live (HIS-096). Open: whether this definition holds up in playtesting.
8. "Always assume resolves" (off by default) covers passes only, never blocks or keeps; blocks are never assumed by the
   device alone (the Assume blockers screen opens, CMB-040).
9. One-answer questions that depend on the other player's hidden cards are real waits for the acting device; the other
   player's own device answers them under Auto-skip (STK-036, SYS-063).
10. Order-free combat damage divisions are made automatically for every player, whatever their response mode
    (STK-033).
11. A confirmation compares only the decider's first input with the assumed one; the rest of their answer is their next
    action (HIS-105).
12. Accepted tell: the assumer's unconfirmed mark (TRN-068) goes away when the other player's playback confirms, which
    shows where their Auto-skip stopped (the async form of I-D18's live timing).
13. A concession made while behind discards the line beyond the conceder's local head (TRN-064).
14. Discarded lines are shown to nobody (kept only for problem reports) and the game tree dialog is gone (HIS-043).
15. A draft (a cast or combat staging in progress) survives the other player's events arriving, but a branch, a rewind
    or an event that changes what it was made on drops it, with one note; a branch or take-back note shows once as a
    dark chip at the top for 6 s.
16. Your own card moves and other table-only actions made while you are behind are shown when playback reaches them;
    when the game moved on they are made again on the new head, or dropped if they no longer apply (a card gone, a
    changed suggestion).
17. A device that can't follow a change exactly (a revert more than 4 turns back, a branch or rewind while its own branch
    is pending) loads the game again.

## Response modes, settings and the move

18. Response mode per player per game, default Auto-skip; Autotap is private like the response mode, not a shared
    table setting (TRN-059, SYS-005).
19. Your device's passes by your response mode are your own inputs, folded into the event that reached them (HIS-092).
20. The move is never passed by hand: it is whom the game waits for (TRN-006, TRN-058). A waiting result suggestion is
    the answerer's move like an undo request. When the game waits for nobody (both have seen a result not yet
    confirmed) the list says "Move not available" and the table's pill and bars show the turn player's.
21. Pausing playback: the button reads "Pausing" between the tap and the stop; at a stop the pause made, the playback
    panel stays (strip and Play), while a stop by the response mode shows the live controls (HIS-103).
22. Staging is shared, but behind the newest event (a decision stop in playback) it stays on the device and goes out
    only as the declaration; the other player's staging, card places and early answers play back in 0.12 s (CMB-043,
    HIS-104).
23. Timeline mode shows only the events the device holds (from the kept part of the line up to your local head), not
    the whole game; the async panels hide there and nothing flashes, only cards move (HIS-012).

## Notifications

24. A move notification that wasn't sent yet is withdrawn when the other player goes on (assuming after all, cancelling
    an undo request); one already shown stays on the phone; a notice that also tells a new message or a branch stays,
    no longer as "Your move" (LOB-073).
25. The player who played on assumptions and left gets the app's own reminders (LOB-076); the server's 5-minute reminder
    and a branch notice's reminder arm only while the game waits for the recipient (LOB-075, LOB-116).

## Open points

26. Early answers are keyed per creature's division for the turn; the key doesn't tell the first-strike and regular
    combat damage steps apart (STK-034).
27. Pass / End turn latency: revisit now that R1–R3 of `engine-responsiveness.md` are built.
28. Whether async rounds per game dropped compared with before (the server logs them).
