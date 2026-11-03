# Async play: assumptions, waiting, branching and undo (I-D56)

Status: **draft for adversarial review, then owner review** (owner design, 2026-11-03). Builds on I-D52 (Automatum
always on) and I-D54 (`engine-state-architecture.md`: engine state is the game state, the server is authoritative,
clients send inputs). Refines I-D55 and replaces parts of I-D40, I-D41 and the free-branching history features (§9).

## 1. Goal

Play two-player games well both live and asynchronously with as few **async rounds** per game as possible (a KPI to
measure in playtests), while keeping play mistake-free and the interface clear. Players don't decide for each other;
the acting player may **assume** the opponent's answer where that's crucial for progressing asynchronously. An
assumption is never confirmed on the opponent's behalf: the opponent's real answer confirms it or branches the game.
We trust the (unmodified) client, but not the players: no one can see more than the rules allow by playing ahead.

## 2. Terms

- **Response mode** (per player, changeable at any time, also when coming back to a game; MTG Arena-like):
  **Full control** (a chance to respond at every point where you get priority), **Normal** (chances at common situations:
  the opponent's spells and abilities, combat declarations that involve you, your own main phases — today's default pause
  points), **Auto-skip** (Normal, but never offered when you can't respond — today's "Skip when I have no choice").
- **Live / async:** whether a player is following the game right now. Start: *live = online* (presence); playtesting may
  refine it (e.g. this game's table in the foreground, or not idle).
- **Head:** the newest event of the game's main line (server). **Local head:** the newest event a player's client has
  shown/played back to that player.
- **Assumed input:** an input the acting player's client submits **for the opponent**, flagged "assumed by <player>".
  Only the kinds in §3 can be assumed.
- **Tentative events:** events that depend on at least one unconfirmed assumed input.

## 3. What can be assumed

- **"Let it resolve" / no response (a priority pass)** at any point where the opponent would get a chance to respond.
  Which points those are depends on the **assumption mode** the acting player picks (setting, quick to toggle):
  - *As if Full control* — assume passes at every point where the opponent gets priority.
  - *As if Normal* — assume passes only at the Normal points (the opponent might still have Full control; if their
    real mode stops them at a point the assumption skipped, they decide there and that's a branch only if they act).
  - *Never* — don't assume automatically.
  A manual **"Assume resolves"** button is always available while waiting on the opponent.
- **Blocks:** at the defender's declare-blockers step, **"Assume blockers"** lets the attacker set the blocks they expect
  (including no blocks), then continue. Hidden from the defender.
- More kinds may be added when playtesting shows the need (each added explicitly to this list).
- Nothing else is assumable: the opponent's other choices (their "may"s, choices on resolution, their triggers' targets,
  discards, damage division as defender, mulligans…) are always real waits.

## 4. When assumptions happen

- **Opponent live:** after your input that gives the opponent a chance to respond, your client **waits** for their
  answer — no timeout, they have all the time they want. You may press "Assume resolves" (or "Assume blockers") to go on.
  A setting lets you assume automatically even when the opponent is live (with the chosen assumption mode).
- **Opponent async:** your client assumes automatically and instantly (with the chosen assumption mode).
- **Information rule:** you can't act on an assumption if your next action would **reveal to you information hidden from
  you** — at least the opponent's hand; per a game setting also the contents of libraries. Such an action requires the
  opponent's real answers up to that point: the client stops and waits for them (no "Assume resolves" offered there).
  Together with the per-branch reshuffle of unknown order (§6), this ensures no one gains information by playing ahead.
- **Game results:** a result reached on a tentative line is final only when every assumption before it is confirmed
  (I-D53's OK comes after that).

## 5. The opponent's side

- The opponent's local head may be behind the head. Live or after being away, they **play back** the events in order and
  can pause at any time. Coming back after being away, they first get the chance to set their response mode, then start
  the playback.
- Playback stops at each of their decision points **before** showing anything after it. They answer as usual.
- **Confirmation:** each real input is compared, in order, with the assumed inputs. A match confirms it and playback
  continues. The **first mismatch** is a branch point: the game branches there with the real input; every tentative event
  after it is discarded.
- **Discarded lines are never shown** to the player whose real answer differed (they'd reveal the assumer's plans and
  cards). The assumer is notified ("Bob responded to <spell> — your later actions were taken back").
- A player can never view or play back beyond their own local head, nor see the opponent's assumptions.

## 6. Branching

- Branching happens **only** when a player's real answer differs from an assumption, at the point they are paused at /
  deciding. There is no free branching from the timeline or the game tree.
- Branching resets the shuffle seed for unknown order (as today, per branch), so cards seen in a discarded line don't
  stay known.
- The server's main line moves to the new branch; inputs made on the old line are refused (stale parent, I-D54) and that
  client resyncs.

## 7. Undo

- You may undo **your own** actions freely while no opponent action (real, or an assumption already confirmed) is more
  recent than them.
- Otherwise — and always by **holding** Undo — Undo opens a dialog to choose the point to rewind to, with a view of that
  state and an optional message. Every opponent gets the request dialog with the same view and the message and must
  accept or deny; while a request is open neither player can make moves until it is answered (the requester may cancel).
- **Redo is removed.**

## 8. Timeline and game tree

View-only: see "how the game looked on turn X" and how things played out, up to your own local head. No branching, no
playing ahead.

## 9. What this replaces (catalog and specs must be updated consistently)

- I-D41 response windows (the 1–3 s pause bar and the acting player's proceed/wait setting) → waiting without timeout +
  "Assume resolves" + assumption modes.
- I-D40 "Pass for them", "Choose for them", acting-for confirmations → assumed inputs (§3) only; no acting for others.
- I-D55 (in design) → this spec.
- Free branching (live branch rewind, branching from timeline/tree, auto-rewind settings), redo, and the undo rule's
  "anyone's event" path → §6, §7.
- Pause points' "Hold" → pausing the playback.

## 10. UI (initial; MTG Arena as the reference)

- An indicator while you have unconfirmed assumptions: how many assumptions, and how many actions you've taken since the
  first one.
- "Assume resolves" and "Assume blockers" buttons where they apply; assumption mode as a quick toggle (with the response
  mode toggles) and in Settings.
- Waiting state when the opponent must answer.
- Undo request dialog (target point picker with preview + message) and the incoming request dialog.
- Notification when an assumption broke.

## 11. Open points

- "Live" definition (online vs foreground vs idle) — playtesting.
- Whether libraries count as hidden for the information rule — game setting; default to decide.
- Whether the assumer can view their own discarded line afterwards (view-only).
