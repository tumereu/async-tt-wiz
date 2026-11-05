# Live, catch-up playback and timeline; the main button; assumptions by setting only

Owner, 2026-11-05 (design discussed and approved). Not built. Building it moves the behaviour into the catalog and
deletes this file.

## 1. Three modes

- **Live:** you are at the newest event and play. Unchanged.
- **Catch-up playback:** you come back after the other player has made moves (a fresh load, or the app back from the
  background, HIS-035). It looks and behaves like live play while their moves play out.
- **Timeline:** you deliberately look at the history (strip icon, Table actions › Timeline). Read-only, as today: grey
  bars, dimmed board, "Timeline mode" label, Pause/Play, Previous/Next, the strip (HIS-004–HIS-016). It never stops
  for decisions and you never act in it. Leaving it returns to where you were (live, or catch-up playback paused).

## 2. Catch-up playback

- **Start.** Your table as you left it, and in the board's centre a large text you tap: "<name> has made moves ·
  Tap to watch". No count or preview of what is coming. No Play/Pause button at the bottom (HIS-103 goes); the event
  strip shows as in live play.
- **Looks like live.** No dimming, no grey bars, no "Timeline mode" label, no other "you're catching up" indicator; the
  other player's actions animate as if they happened now (HIS-021 pacing, HIS-104 fast-forwarding).
- **Stops.** Playback stops before an assumed answer about you where Auto-skip would stop (where you could respond, and
  your decisions), or at every point where you get priority while **Full control** is on. Auto-skip always applies in
  playback, whatever your live response mode (the other player isn't waiting, so stopping leaks nothing). Normal
  doesn't exist in playback.
- **At a stop** the table is live, exactly like live play: Resolve, casting, activating, the main button (§4).
  Answering as assumed confirms it and playback goes on by itself; anything else is a branch (HIS-097). (Owner found
  that resolving didn't work at playback stops: this must work.)
- **Pause.** A tap on the table while playback runs pauses it: playback goes on to the next point where you get
  priority (where Full control would stop) and stops there, live. The centre text ("<name> has made moves · Tap to
  watch") resumes it.
- **Quick actions in playback:** one toggle "Full control" (on/off). "Always assume resolves" and the three-way response
  mode toggle aren't shown in playback; live play keeps them (TRN-059).
- **End.** Reaching the newest event, you are live; nothing announces it.
- New events of the other player arriving meanwhile join the playback.
- **Branch take-back** (HIS-106) keeps its timeline look while going back, then continues as this playback.

## 3. Assumptions only by your setting, never by presence

The other player's presence (online / away, HIS-096) no longer decides whether their answer is assumed:
- **Their pauses** (a pass at a point where they could respond): with "Always assume resolves" on, assumed at once; with
  it off, a wait with Assume on the main button (§4), whether they are online or away. So you can leave a spell up for
  an away player who you expect to respond.
- **Blocks:** never assumed by the device alone; the Assume blockers screen (CMB-040) opens only from the main button,
  online or away.
- Points only Full control stops at (your upkeep, your beginning of combat, their own trigger on top) stay assumed at
  once (HIS-096), as today; the opening is never assumed (TRN-027).
- Presence still shows (the dot) and still matters for notifications; the 60 s grace no longer matters for assuming.
- "Always assume resolves" keeps its default (off).

## 4. The main button

- One button in the step arrow's place holds whatever goes on now: the step arrow's actions (TRN-063: Next, To combat,
  End turn, Attack (N) / No attack, Block (N) / No blocks), **Resolve** (STK-029: was under the Stack), **Assume** and
  **Assume blockers** (TRN-070, STK-028: was under the Stack). Nothing sits under the Stack any more.
- It is twice as wide as a quick action (TRN-016); the quick actions share the rest of the row. Owner checks it on the
  screenshot tests.
- The combat band (backlog) keeps only the lanes and damage division in the band; its go-on actions are this button.

## Catalog entries this changes

HIS-006, HIS-008, HIS-013, HIS-035, HIS-094, HIS-095, HIS-096, HIS-103 (removed), HIS-104, HIS-105, HIS-106, STK-027,
STK-028, STK-029, STK-036, STK-037, TRN-016, TRN-059, TRN-063, TRN-066, TRN-070, CMB-040; async-play.md choices about
"live", grace and assuming by presence.
