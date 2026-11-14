# Stack animation (owner, 2026-11-14)

Approved plan for how a spell or ability on its way to and through the Stack animates, on the phone and desktop layouts
alike (shared code). It fixes the double animation (owner playtest 2026-11-14: a play flew to the middle and, at the same
time, straight to the Stack, so it looked as if two entries went on the Stack) and replaces any fixed pause while a small
card waits on the Stack: **an entry stays on the Stack only as long as its own animation takes**, and drawing its target
arrows counts as part of that animation. Changes HIS-112, STK-058, TBL-125 and the entries that point at them.

All times are at Card move speed 1× and scale with it (TBL-038), except the middle hold, which is the "Show plays and
combat" time (LOB-082: 1.2 s by default, 0 = Off).

## Pre-resolve and post-resolve

An entry's animation has two parts:

- **Pre-resolve** (steps 1–4): the fly-in, the middle hold, the flight to the Stack and the target arrows. These play as
  soon as the entry goes on the Stack or becomes its top entry, before anyone has passed.
- **Post-resolve** (step 5): what its effect does. This plays only once the game actually resolves it.

Between the two the game may wait: for the other player's response or pass, an assumption (Assume resolves), a choice.
That wait is the game's, not the animation's, and it can add delay: the pre-resolve part has played, the arrows stay drawn
as long as the wait lasts (STK-058), and the post-resolve part follows as soon as the entry resolves. Without a wait (an
assumed or automatic pass already in hand) step 5 follows step 4 at once.

## One entry, step by step

1. **Fly-in (about 0.25 s).** The other player's spells and abilities and both players' triggers fly from their source to
   the middle of the board, growing from small to large: a battlefield card (one out of sight from the board's edge in
   its direction), the pile it is in or was cast from, the other player's hand fan, an emblem's chip. Until it lands in
   step 3 its Stack entry isn't shown and nothing else moves to the Stack for it: no card move of its own from hand to the
   Stack, no entry appearing in the Stack list, HUD or dock. Your own casts and activations skip steps 1–3: their card
   makes one move from your hand (or its source) to its Stack entry, as now (HIS-112 keeps no middle show for them).
2. **Middle hold (the "Show plays and combat" time).** Every play holds, whatever it is, plain creature spells included,
   with its line under it naming it and its targets (STK-057). As today: a repeat of the same ability in one chain shows
   35% shorter each time down to 0.15 s; plays put on the Stack in one go fly in about 50 ms apart, pile up and show top
   first; a double tap or double click skips it, a held finger or Alt pauses it, holding the card inspects it.
3. **Flight to the Stack (about 0.25 s, accelerating).** It shrinks into its entry's place: the dock's list on the desktop
   table (the list's top when that entry is scrolled out of view), the HUD on the phone. The entry appears exactly as it
   lands, taking the flying card's place; the list shifts only then.
4. **Targeting, when it is the top entry about to resolve** (only an entry with targets). Not necessarily when it lands:
   with two triggers on the Stack the top one targets and resolves first, then the next. Its arrows (STK-058, your own
   entries' too) grow from the entry to each target in about 0.25 s, several targets about 80 ms apart, each target's
   highlight fading in as its arrow arrives; then about 0.4 s with the arrows drawn. An entry without targets skips this
   step.
5. **Resolution (post-resolve: as long as its effect's animation, nothing more).** The entry leaves the Stack and its
   effect plays in event order with the animations that already exist (TBL-125): a P/T change or counter pops on the card
   (about 0.3 s); damage marks the card or the life square ticks (about 0.35 s); a token flies out of the resolving entry
   (about 0.3 s); a permanent spell's card moves to the battlefield, a card to its graveyard, exile, hand or library (one
   card move); a tap or untap turns (about 0.2 s); an instant or sorcery's card goes to its graveyard in step with its
   effect. The arrows fade as the effect reaches each target. Then the next play or entry goes on.

For a sense of the totals: your own pump spell is its card's move to the Stack and the pop (about 0.6 s); the other
player's plain creature is about 0.25 + 1.2 + 0.25 s and then its move to the battlefield; their Shock at your creature
the same plus about 0.65 s of arrows before the damage shows.

## Chains, pauses, take-backs

- Repeats in one chain (a board wipe's "whenever a creature dies" triggers) shorten steps 4 and 5 with the same 35%
  factor as the middle hold, down to about 0.1 s for the arrows and 0.15 s for the read.
- A held finger or Alt pauses whichever step plays (HIS-021, WEB-025); a double tap or double click skips it (WEB-030).
- An entry taken back (undo, a branch, a rewind) ends at once and leaves nothing on screen; entering timeline mode ends the
  one playing.
- Live play and catch-up playback look the same. Combat holds (HIS-113) are unchanged.
- Any fixed pause found holding a small card on the Stack today is removed, and its cause named in the commit.

## Logging

Each entry logs every step's start and length and, between steps 4 and 5, what the game waited for (a priority stop, an
assumption, a choice, a held finger), so the next report about animation timing shows what held and for how long.

## Tests

Unit tests for the step order and times (each step's length from the settings, repeats, the skipped targeting step, the
entry appearing only at landing), an e2e test showing one Stack entry per play on both phones (no second entry or move
while the play shows) and the arrows before an assumed resolution, and gallery shots of steps 2–4.
