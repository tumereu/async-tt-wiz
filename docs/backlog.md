# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Ready to build

- Keywords and EOT keywords move to Everyday; Face down/up and Switch face move to More (TBL-076 moves under More, TBL-085/086 under Everyday). (TBL-073, TBL-082) Approved text: TBL-073 "Everyday holds, in order: Inspect, Note, P/T, Stack, Declare, Keywords, EOT keywords, Graveyard, Exile, Clone, Ping, Hand, Library top, Library bottom, Shuffle into library, EOT P/T, Battlefield. (r36)" TBL-082 "More holds: Owner, Other side, Link, Unlink, single P/T step buttons, Reset P/T, until-end-of-turn P/T step buttons, Face down/up, Switch face, Temp 1, Deal to target, Deal to target or creatures, and Set damage last. (r36)"
- EOT P/T step icons: normal icon plus a small hourglass in the top-right corner, nothing clipping. (TBL-083) Approved text: TBL-083 "P/T step buttons read "+/·", "−/·", "·/+", "·/−" (· = side unchanged); the until-end-of-turn ones show the same icon with a small hourglass in its top-right corner, not touching the symbols or the label; all stay visible, greyed out on cards without P/T. (r36)"
- Battlefield, hand and panel menus have Temp 1 only; a Temp pile's Look panel keeps Temp 1–4. (ZON-042, ZON-007, TBL-089, ZON-058) Approved text: ZON-042 "Battlefield, hand and panel menus have only Temp 1, moving the cards into their owner's Temp 1 and creating it if needed; cards in a panel showing a Temp pile (its Look) have Temp 1–4. Temps 2–4 are otherwise filled from a Temp pile's own menu (ZON-041) or by dragging onto them. Tokens never go into Temp piles. (r36)" ZON-007 "Tapping a hand card opens a one-page hand menu: Inspect, Play, Face down, Reveal, Battlefield, Discard, Exile, Library top, Library bottom, Shuffle into library, Temp 1, Discard random, Reveal random, Exile random, Random to library bottom. (r36)" TBL-089 "The hand card menu is one page: Inspect, Play, Face down, Reveal, Battlefield, Discard, Exile, Library top, Library bottom, Shuffle into library, Temp 1, Discard random, Reveal random, Exile random, Random to library bottom. (r36)" ZON-058 "Tapping a panel card opens one menu right of the panels, top row lined up with the cards: Inspect, Other side, Ping, Hand, Reveal hand, Battlefield, Tapped, Stack, Graveyard, Exile, Library top, Reveal top, Library bottom, Shuffle into library, Temp 1 (Temp 1–4 in a Temp pile's panel). (r36)"
- Hold preview scrolls at least 1:1, sped up so 90% of the screen height covers the whole preview; turning back scrolls at once. (TBL-104) Approved text: TBL-104 "The hold preview is compact with no title; mana and tap symbols are drawn as symbols, and it closes on release. Moving the finger up or down scrolls it by at least as much as the finger moves, sped up so that a drag over 90% of the screen height scrolls the whole preview from top to bottom; movement past either end is not stored, so turning back scrolls at once. (r36)"
- Holding a battlefield pile card also previews the cards under it. (new TBL entry) Approved text: new TBL entry (number 111) "Holding a card that lies on top of other cards in a battlefield pile (TBL-053) previews it and then each card under it, in pile order, one below the other in the same scrolling preview; each shows only what the table shows of it (a face-down card stays hidden from the opponent). (r36)"
- TBL-091 points at CMB-003 (the app's combat copy menu). (TBL-091) Approved text: TBL-091 "The combat copy menu is described in CMB-003. (r36)"

## Specified, not started

(nothing right now)

## Needs discussion

(nothing right now)

## Later (nice to have)

- Choose a card's printing (art) in decks and cubes.
