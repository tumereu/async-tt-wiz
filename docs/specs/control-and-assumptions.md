# Control mode and assumptions (owner, 2026-11-06)

Status: **approved by the owner (2026-11-06), not started.** Replaces the backlog's "Control mode and assumptions"
discussion. Building it moves the behaviour into the catalog (STK-036, HIS-095, HIS-096, TRN-059, TRN-070, SYS-071,
SYS-077, LOB-113, plus a new entry for rule 6) and deletes this file.

## Why

In live play your response mode decides where your own phone passes for you. The other player's phone assumes your
answers so the game goes on while you are away (HIS-096). Today your mode is private, so their phone can't know you
want to respond. Example (found by worker H): in Control you cast a spell and keep priority, their phone assumes your
pass at once, and your second spell is refused with "That wasn't taken". The fix is to let each phone read the other
player's mode, and to make the Assume setting mirror the response modes.

## Rules

1. **Response mode (your own passes).** Control, Normal and Quick stay as STK-036 defines them. Each player's current
   mode is a per-game value kept on the server, outside undo and history, and both phones learn it at once when you
   change it. Your own stops (live play and catch-up playback alike) come only from your current mode, never from the
   other player's settings.
2. **Playback keeps its Control toggle** (HIS-095, TRN-059), because the other player can trigger your effects during
   their turn and you may want to respond to them. The toggle is a shortcut to that same per-game value: it updates the
   current value. Old events being watched are not rewritten; in playback your current value is used in place of the
   value they recorded.
3. **Recorded mode.** Each event records the response mode it was made under. For an assumption, that is the mode of
   the player it is about. Only the other player's phone reads it, to know the opponent's mode. Your own stops never
   read it (rule 1).
4. **Assume setting (your assumptions about the opponent)** has four values, defined from the response modes. Never and
   Normal match the matching response mode by definition: when a response mode's stops change later, these change with
   them.
   - **Never:** waits with Assume at every point where they get priority (mirrors Control). It never assumes on its
     own, not even at your end step or right after your attack.
   - **Normal (new, the default):** assumes wherever Normal mode would pass, and waits where it stops. Exception: their
     pause at your end step and right after your attack (before blocks) is assumed, as today (TRN-070).
   - **No mana:** as Normal, and also assumes at their pauses while your table shows them no mana in their pool and
     nothing they could activate (mana abilities included). Cards in their hand don't count, as today.
   - **Always:** assumes every pass.
5. **Combining the two.** Your phone assumes their pass when your setting assumes it **or** their response mode would
   pass it. Their Quick is judged on their real hand: by choosing Quick they give that information out, just as in live
   play. You can't get it any other way: no Assume setting of yours uses their hidden cards. So against an opponent in
   Normal or Quick, Never and Normal behave the same; against an opponent in Control, Never respects their Control and
   Normal ignores it.
6. **Automation is button presses.** An automatic pass is exactly a press of Resolve (or the step arrow), and an
   automatic assumption is exactly a press of Assume. Both go through the same code path. So the player an assumption is
   about stays at their decision and may act there: passing confirms it, and anything else is a branch (HIS-097). This
   includes a live player whose move arrives after the other phone's assumption, such as a second spell while holding
   priority in Control. It branches; it is not refused as "That wasn't taken" (SYS-077 changes).

## Unchanged

- Blocks are assumed only from the Assume blockers screen (CMB-040). Decisions and the opening keep are real waits
  (TRN-027).
- The information rule (TRN-071) still refuses an assumption that would show hidden cards or a random result.
- Whose move: a wait at your priority is your move, and you are notified (`NextStop.waitsFor`).
- The opponent's mode is never shown on screen. Changes in what their phone does may reveal it; the owner accepts that.
- Response-mode default stays Quick. The Assume default becomes Normal (LOB-113, for new games). The stored Never of
  existing games and accounts stays Never.

## Tests

E2E (each fails before the change):
- You are in Control and the opponent is on Always: you cast a spell, then cast a second one while holding priority.
  The second spell is taken and their line branches.
- The opponent is on Never and their spell puts your own trigger on top, with you holding priority: their phone waits,
  and it is your move with a notification.
- You are in Control and the opponent is on Normal: after your own cast, their phone waits for you instead of assuming
  your pass.
- The opponent is in Quick with no possible response: your phone on Never assumes their pass at once.

Unit tests cover the full grid of Assume setting × opponent mode in `GameDevice.point`.
