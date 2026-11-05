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
- **Groups.** A stack of identical permanents and a permanent with the cards attached to it (auras, equipment, a
  merged mutate creature's other cards) are both a group; one tap rule covers both. An attachment's peeking strip is
  never a touch target on its own (too small on a phone).
- **One meaning: act at once.** When a tap on a group can mean only one thing, it does it at once: exactly one member
  can do what the tap would do now (activate, one-tap mana, stage an attacker, select a blocker), or exactly one member
  is a legal pick while targeting or choosing. In a stack of identical cards that are all usable, the members count as
  one meaning: the tap acts on one of them, an untapped one first. Examples: tapping a land stack taps one land; tapping
  an equipped creature when only the creature has something usable activates the creature.
- **Several meanings: expand first.** When it could mean more than one thing (the creature and its equipment both have
  usable abilities; several members are legal targets that differ), the first tap expands the group in place, its
  members side by side with the usable or pickable ones glowing; the second tap acts on the member tapped. Tapping
  elsewhere, or acting, collapses it. A member with several usable abilities then opens its ability chips as a single
  card does (TBL-124).
- **Glow.** A collapsed group glows (TBL-117) when any member is usable or pickable now.
- **Holding** a group inspects all its members, one after another (as TBL-111 does for piles).
- **Animations.** Cards still fly to and from their spot (TBL-040); joining or leaving a stack, and a row closing a gap,
  animate.

## Catalog entries this changes

TBL-002 (column pitch → groups), TBL-003, TBL-004, TBL-006, TBL-007, TBL-008, TBL-012 (fit), TBL-053, TBL-054,
TBL-101, TBL-102, TBL-111, TBL-117 (group glow), TBL-121 ("column 0"), TBL-122 and TBL-124 (taps on groups), SYS-041
(drags into any column), SYS-074, ZON-077 (moves within your own Battlefield), STK-032 ("positions on the table still
change": gone).

## Open, decide while building (plainest option, list under "choices made")

- Exactly which state differences split a stack beyond the list above.
- Whether the opponent's battlefield mirrors the rows (creatures nearest the middle on both sides: yes).
