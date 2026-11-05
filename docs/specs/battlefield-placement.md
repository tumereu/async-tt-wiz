# Battlefield placement: automatic rows and stacks (MTG Arena style)

Owner, 2026-11-05. Approved direction, not built. Building it moves the behaviour into the catalog and deletes this file.

Nobody places battlefield cards by hand any more: the app lays each battlefield out from the game state, the way MTG
Arena does. "Try the Arena way first" — details may change after playtesting.

## Layout

- **Rows.** Creatures in the row nearest the middle (the combat band side). Lands in the row nearest the player's own
  edge, starting from the left; other permanents (artifacts, enchantments, planeswalkers, battles, non-creature
  others) in the same back row, from the right. A permanent that is several types sits where its first type in the
  shared order (SYS-051) puts it, a creature always in the creature row; face-down permanents in the creature row.
- **Stable order.** Within a row, groups keep the order they arrived in. A card moves only when its group or row changes
  (it becomes a creature, gets a counter, an aura is attached…); nothing re-sorts by itself.
- **Too wide.** A row wider than the screen is panned to, as today (infinite battlefield rule, SYS-041); nothing shrinks
  to fit.

## Stacks

- **Identical permanents form one stack:** same card (name and face), same controller, same state that matters for
  play — counters, damage, attachments, face up/down, in combat, summoning sickness shown or not, P/T as worked out.
  Tapped and untapped cards stay in the SAME stack (Arena): a tapped member rotates inside the stack.
- **Lands** fan slightly sideways so the count is visible at a glance; **other stacks** (tokens above all) lie tight with
  a "×N" count badge.
- **Attachments.** Auras and equipment lie behind the card they're attached to, peeking out above it; a merged mutate
  creature is drawn the same way, its other cards peeking out under the top card (replaces SYS-074's separate card).
- No size limit per stack (replaces the 3-card piles, TBL-053/TBL-101).

## Touch

- **No dragging on the battlefield.** A drag that starts on a battlefield card pans the table (or does nothing); casting
  by dragging a hand card and dragging into the combat band stay.
- **Tap on a stack** (activating, one-tap mana, staging an attacker, selecting a blocker) acts on ONE member: an untapped
  one first (one that can do it). Holding a stack inspects its members, one after another (as TBL-111 does for piles).
- **Targeting and choices on a stack** (a target, "choose a creature", a blocker's attacker): the first tap expands the
  stack in place so its members sit side by side; the second tap picks the member. Tapping elsewhere collapses it.
- **Animations.** Cards still fly to and from their spot (TBL-040); joining or leaving a stack, and a row closing a gap,
  animate.

## Catalog entries this changes

TBL-002 (column pitch → groups), TBL-003, TBL-004, TBL-006, TBL-007, TBL-008, TBL-012 (fit), TBL-053, TBL-054,
TBL-101, TBL-102, TBL-111, TBL-121 ("column 0"), SYS-041 (drags into any column), SYS-074, ZON-077 (moves within your
own Battlefield), STK-032 ("positions on the table still change": gone).

## Open, decide while building (plainest option, list under "choices made")

- Exactly which state differences split a stack beyond the list above.
- Whether the opponent's battlefield mirrors the rows (creatures nearest the middle on both sides: yes).
