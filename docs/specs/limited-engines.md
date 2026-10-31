# Spec: limited engines (more draft modes and Sealed)

Owner-approved 2026-10-31. Delivered as one change; when built, its behaviour moves into
`docs/features/` and this file is deleted. Draftmancer's modes are the reference for rules; where a
mode is the same engine with other settings (Booster/Glimpse), it is one engine with presets.

## Engines

A pod's mode chips become: **Booster**, **Grid**, **Rochester**, **Rotisserie**, **Winston**,
**Winchester**, **Sealed**. Every engine takes a cube or a set as its source (DRF-018) and ends in the
usual Build, Stats and pod games (DRF-093–DRF-124). Each engine has its own settings with standard
defaults; numeric settings allow up to about 5× their default. Built-in presets (Booster, Glimpse)
are listed first in the preset list and can't be edited or deleted; a pod's mode reads the engine name.

| Engine | Players | Defaults (range) |
| --- | --- | --- |
| Booster | 2–8 | as today (Glimpse is a preset) |
| Grid | 2–4 | as today |
| Rochester | 2–8 | 3 packs per player (1–15), 15 cards per pack (5–30), packs open at once: one per player (1–all) |
| Rotisserie | 2–8 | 45 cards per player (5–225) |
| Winston | 2–4 | 3 packs per player (1–15), 3 piles (2–6) |
| Winchester | 2–4 | 3 packs per player (1–15), 4 piles (2–8) |
| Sealed | 2–8 | set: 6 boosters per player (1–30); cube: 45 cards per player (5–225) |

### Rochester (simultaneous packs)
- Packs are opened face up; everyone sees every open pack. Each pack is picked in snake order
  starting from its opener (who rotates), one card per turn, until it is empty.
- To cut async rounds, several packs are open at once (default: one per player, like a booster round).
  You pick in every open pack where it is your turn; the next round opens when the current packs are empty.
- The pick screen shows all open packs in one scrolling view, packs separated by a divider with the
  pack's header ("Your pick", "Waiting for <name>"). Picked cards stay in place, greyed, with the
  picker's small avatar on the card (as in Grid, DRF-085).

### Rotisserie
- The whole pool (cube: players × cards per player, drawn at random; set: enough boosters opened) lies
  face up. Players pick one card per turn in snake order until each has their cards.
- The pick screen is one scrollable panel of the whole pool with a search field using the catalog search
  syntax (card lists spec); picked cards are greyed with the picker's avatar. Tap a card, confirm.
- Turns are sequential; no parallel boosting.

### Winston
- Cards from the packs are shuffled into a face-down stack; there are N piles (default 3), each starting
  with one card. On your turn you look at pile 1: take it, or put it back with one more card from the
  stack and look at the next pile. If you pass every pile, you take the top card of the stack. Each
  player plays out their whole turn before the next player starts.
- **What you've seen stays visible to you**: once you've looked at a pile, those cards stay face up for
  you in that pile (cards added later stay face down until you look again). A draft can last days, so
  nobody has to remember piles.
- Turns are sequential; no parallel boosting.

### Winchester
- N face-up piles (default 4). Each turn, every pile gets one card from the stack, then the player whose
  turn it is takes one whole pile. Sequential turns.

### Sealed
- No draft phase: at start every player gets a pool (set: their boosters opened; cube: random cards
  without replacement) and goes straight to Build.

## Other players' picks
Winston, Winchester and Grid get a **Picks** view per opponent: the cards they took, in order, newest
first. A card shows face up if you have seen it (always in Winchester and Grid; in Winston only cards
you had seen in a pile), otherwise as a card back. Rochester and Rotisserie show picks on the board itself.

## Turns and notifications
Turn-based engines use the existing "Your pick" notification, reminder (LOB-076 for pods) and pod
status texts for "your turn"; waiting texts name who the pod waits for (DRF-071).

## Player limits
- Start is refused when more members have joined than the engine allows, with the reason.
- Auto-start can't be armed while members plus pending invitations exceed the limit.
- Changing settings normally keeps an armed auto-start (today's behaviour), but a change that makes the
  pod exceed the new engine's limit disarms it and tells the creator why ("Auto-start off — <pod>").
