# Undo

Taking back your own actions: the Undo button, free undo versus approval, the concern rule, how an undo shows, the
Undo dialog and undo requests.

## How it works

A tap of Undo takes back your latest own action; holding it opens the Undo dialog, where you can pick any earlier
point of the game. An undo is free when nothing after that point is a real input of the other player's and nothing
there showed you something new; otherwise the other player must approve it, so an undo never changes a line they are
on without their consent. The other player's passes (unless a game setting says they count) and the combat staging or early answers that your
undo doesn't touch never stand in its way. An undo that takes back library cards drawn or seen says that the libraries reshuffle. An undo shows at once, as one animation from the board before to the board after. There
is no redo, and undo is only a convenience: no feature works by undoing something.

## The Undo button

- **SYS-006** Undo is only a convenience. No feature works by undoing something (withdrawing a result suggestion, for example, is its own event), so a refused undo never blocks a feature. (r18)
- **HIS-083** The Undo button sits at the left end of the bottom bar: tap = Undo (HIS-084); hold = the Undo dialog to pick any earlier point (HIS-099), with a small haptic tick. There is no Redo. (r48)
- **HIS-084** Undo goes back to just before your latest own meaningful action, with your later assumptions and every pass after it (passes are never the target, HIS-101, except your tap leaving your own main phase; an End turn is one action): at once when free (HIS-087), else the Undo dialog opens with that point chosen (HIS-099). There is no limit on how many events go. (r61)
- **HIS-198** After Undo you land where you acted and your device passes nothing for you there until you act, also when the other player's table-only events (staging, early answers) arrive meanwhile. Undo again goes back past your action before. (r61)
- **HIS-199** With your cast or activation open (its panel or target bar, nothing sent yet) Undo only drops it, taking nothing back (STK-025). (r61)
- **HIS-200** Your OK on a game's result is never the point Undo goes back to (TRN-065): it goes back past the result, with every OK on it. (r61)
- **HIS-085** One acting point (a cast with its choices, a declaration with its payment) undoes as one. (r48)
- **TRN-023** Your opening's Mulligan and Keep (TRN-062) are each one Undo step, however many changes they make; the settings in the quick actions (TRN-059) are no events at all (SYS-071). (r60)
- **HIS-086** Undo only deletes the events after the point: the game is exactly as it was there, and no timeline event ever says the game went back (the new line's reshuffle, HIS-042, happens with the next event). (r55)
- **HIS-107** An Undo tapped while your own action is still being worked out or sent (right after Attack or a cast, while your events wait for the server) is never refused: no shake and no "That wasn't taken". It takes back the action you last made when you tapped (never one you make, or that arrives, after the tap). When it is free, the undone board shows at once and the undo follows the action to the server; otherwise (the action still being made again after a lost race, or the undo needing approval) it waits for the action first, your device making nothing more for you meanwhile, and when by then the undo isn't free, the Undo dialog opens with that point chosen. (r53)
- **HIS-201** A move of yours waiting to be made again after the other player's event came first (SYS-077) is simply not made again, and any undo of yours ends such moves. (r53)

## Free undo or approval

- **HIS-087** Undo is free unless something after the point is a real input of the other player's or showed you something new; otherwise it needs their approval (HIS-099). (r49)
- **HIS-202** For a free undo, the other player's passes never count as their real input (unless the game's setting says so, HIS-232), whether they tapped them, their device made them or yours assumed them and they confirmed (owner problem report VXSGKH), nor do their table-only events that stay (SYS-058). An assumption of yours about their real input (a block, a keep) counts as their input once they have seen it. (r78)
- **HIS-232** With the game setting "Undo past the other player's passes needs a request" on (LOB-135), their passes count as their real input after all: going back past one they tapped, their device made, or you assumed and they have seen needs their approval (HIS-099). (r78)
- **HIS-203** What shows you something new: a card you couldn't see (your draw, look, scry, search, a reveal, also when a resolution their pass let through shows it), a random result, or a wait for the other player's choice from a source you can't see. (r49)
- **ZON-026** Undoing your own look, scry or reveal needs the other player's approval (an undo request, HIS-087). (r48)
- **HIS-204** In your opening, an undo back past the other player's assumed keep about you counts only the events before that keep: their line after it is made again, not taken back (HIS-115). (r49)
- **HIS-089** Undo never changes a line the other player is on without their approval. A refused undo says why in plain words; an undo with nothing to undo shakes the button ("Nothing to undo yet.", TBL-212). (r78)

## The concern rule

- **SYS-058** Undo's concern rule: undoing goes back past the other player's later table-only events (combat staging, an early answer, a suggestion) that touched nothing the undone events changed, and those stay (for combat staging see SYS-140); their passes go with the undo without asking them (unless the game's setting counts them, HIS-232); any later real input of theirs needs their approval (HIS-087, HIS-099). There is no redo. (r78)
- **SYS-140** Combat staging stays after an undo only while the game still awaits the declaration it stages there: an undo or rewind back out of that declare attackers or blockers step drops it (CMB-043). (r49)

## How an undo shows

- **HIS-236** After a free undo whose events taken back touched library cards (HIS-235), its fading text (HIS-168) has a second line, for the player who undid and the other player alike: "Your library reshuffled: the next draws may change." ("Bob's library", "Both libraries"). An approved request's undo doesn't repeat it. (r78)
- **HIS-108** An undo, yours or the other player's of events you had seen, shows its whole result at once and animates once, straight from the board before to the board after (a cast spell flies from the battlefield straight back to the hand), at the card move speed with no playback pause: no boards, step texts, main button or quick-action states in between, while "Undid: <action>" fades at the top (HIS-168). (r59)
- **HIS-205** Live (the game open, in sight, at the head) the other player's undo shows at once. Coming back to it (the app in the background, the game not open, a notification, a cold start) opens the game exactly as you left it, and nothing moves until you tap: the centre text says "<name> undid moves · Tap to watch" (HIS-158), and the tap plays the undo, then their new events as catch-up playback with its stops. Their undo of only events you never saw is a plain catch-up. (r59)
- **HIS-206** An undo of the other player's that took back more than 200 events you had seen opens at its result in an app started anew. (r59)

## The Undo dialog

- **HIS-099** Holding Undo, or an Undo that isn't free, opens "Undo". Its points are exactly the timeline's events shown to you, back to the game's first (HIS-111): yours and the other player's, no passes (HIS-101), your End turn that began with a pass as one point ("You ended the turn") and your tap leaving your own main phase as one ("You went to combat"); "The game began" itself is not a point. A point means going back to just before that event. (r76)
- **HIS-207** At the Undo dialog's top a small view of the board at the chosen point as you see it (after a new choice it follows once the choice rests a quarter second, dimmed meanwhile); under it the timeline's strip of the points with their icons and turn and step lines (HIS-005), the chosen one boxed in the centre; then "Go back to just before" and the chosen event's text. (r76)
- **HIS-208** Dragging the Undo dialog's strip left or right chooses an earlier or later point one event at a time; a tap on an event chooses it, holding one shows its text. The dialog opens at the point before your latest own action (HIS-084), or the newest point when there is none. (r76)
- **HIS-209** The Undo dialog ends with Undo when the undo is free or, when the other player must approve (going back past a real input of theirs, not a pass, or past something that showed you something new, HIS-087), the line "Undoing requires confirmation from <name>.", an optional message and "Ask <name>". (r76)
- **HIS-235** When what an undo takes back touched library cards (a card drawn, milled, searched for, revealed or looked at where it lies, put into a library, a library shuffled or reordered), the Undo dialog asking the other player (HIS-209) and their "<name> asks to undo" (HIS-100) say "Undoing this reshuffles your library: the next cards drawn may change." ("Bob's library", "both libraries"). (r78)

## Undo requests

- **HIS-100** The other player gets "<name> asks to undo" with the message, where the game goes back to as far as they have seen ("and actions you haven't seen yet are taken back" when past that), a small view of that board (their own newest one when the point is past what they've seen) and Deny / Accept, never how many events go. (r52)
- **HIS-210** An undo request is the other player's move until they answer or it is cancelled (LOB-072). While it is open neither player acts (the wait says so; the requester has Cancel request there); looking, playback and conceding still work. (r52)
