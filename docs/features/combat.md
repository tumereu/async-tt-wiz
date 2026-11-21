# Combat

The combat band and its lanes, attack targets, declaring attackers and blockers, block arrows, attack costs and
choices, Assume blockers, combat damage and its division, early answers, and the combat holds of playback.

## How it works

Combat happens in a band between the two battlefields that is always there and opens when the first attacker is
staged. Every target you may attack (the other player, their planeswalkers and battles) has its own lane with a chip
naming it. Attackers and blockers in the band are copies of the real cards, which stay where they are. You declare
attackers by tapping or dragging your creatures into a lane, and blockers by tapping a creature and then the attacker
it blocks; each block is an arrow. Costs and choices to attack or block show as chips under the copies and are paid
when the main button declares. While the defender is away, the attacker may assume their blocks. Before damage both
players see what it would do; afterwards the band shows what it did, and a creature dealing damage to several may
need its damage divided. In playback, your view holds on the attackers and on the damage so a combat stays readable.

## The combat band

- **CMB-001** Combat happens in the combat band between the two battlefields (CMB-007), which is always there: collapsed, it is as tall as one row of target chips and empty. (r64)
- **CMB-053** The band opens by itself when the first attacker is staged (there is no tap to open it) and Automatum collapses it again itself (SYS-069); in playback it opens only when someone actually attacked. (r64)
- **CMB-054** Opening and closing move the other player's battlefield (with its cards) at the card move speed, away from yours (up on your screen) and back, while yours stays where it was with the band collapsed, so your attacker selection doesn't move; the chips on the band's edge by their battlefield move with it. (r64)
- **CMB-055** When the band closes, what sat on it or by it (the combat copies, the target chips with the damage on them, the life-gain heart, the lane separators, the copies' chips and badges) fades out where it was instead of moving with the band, while the band collapses at the card move speed; on both players' tables, the attacker's too, also when the end of combat comes in the same board as your own action (TBL-125). (r64)
- **CMB-056** The band is part of the table: a cream area (the old combat area's colour, #f5f1df) across the table's whole width between the battlefields, with no panel of its own; its lanes lie in its middle. It pans and zooms with the battlefields (TBL-001); nothing of it floats over them. The band takes combat drops. (r64)
- **CMB-007** Rows for the attacking player: the target chips on top (each with its value on it, CMB-013), then blockers, then (after a gap for the block arrows, CMB-031; "Choose blockers" sits in it until the first block, CMB-034) attackers, each lane under its chip, with dashed slots for the next attacker or blocker while they are placed (CMB-069, CMB-077); the defender sees it mirrored (chips at the bottom, by their battlefield). (r64)
- **CMB-008** Pairs are packed groups without dividers between them (only lane separators), centred as a group and re-centred when added; cards sit centred in their slot (one attacker over two blockers is centred), with about two card-border widths of margin. (r40)
- **CMB-014** Lane separators in the open band are 2dp, fairly dark, scaling with card size, run through the chip row, and keep about 2dp of space around cards and chips. (r55)

## Attack targets and lanes

- **CMB-012** While combat is open (and as chips while you declare attackers), every attackable target always has its own lane in the same attacker/blocker rows: the defending player first, then each face-up planeswalker and battle in battlefield order, including your own Sieges (protected by the opponent). (r63)
- **CMB-058** An empty lane is as wide as its chip, at least one card slot. Every lane with attackers keeps a card slot's room on each side of them, on both players' tables and for the whole combat, and places them as if there were no slot (while you declare attackers the slot sits in the room after them, CMB-069), so neither choosing another target nor declaring moves them, however many lanes hold attackers: they stay where they are when the declaration ends and the slot goes. (r79)
- **CMB-013** Each lane's header is its target's chip, centred over the lane: crossed swords, a round avatar (the defending player's avatar, or their initial in their colour; the card's art; "?" for a face-down card) and the name, white with an edge in the target side's colour. (r57)
- **CMB-059** A chip's name is at most 12 letters (a longer name is cut with "…"; "Face-down card" for a hidden target). Holding a chip shows the full name over it and highlights the target's card or the player's life square while held. (r57)
- **CMB-060** After the name the chip shows the target's life, loyalty or defense counters (none for a hidden target), collapsed or open. (r57)
- **CMB-061** With the damage preview (CMB-029) the chip shows what the target would be left with, "20 → 17" in red, the chip widening about its centre; its lane always keeps room for that, negative results included ("3 → −2"), so nothing moves when the preview shows. (r57)
- **CMB-062** Chips keep their own size, zooming with the band, and fade in when they first show (CMB-052). Lanes widen to fit their chips; a too-wide band pans with the table. (r57)
- **CMB-051** While you declare attackers, a chip for every target you may attack fades in on the band (CMB-013), your own view only. (r64)
- **CMB-052** The target chips fade in once in a combat, when they first show (yours as you declare, the defender's as the attackers show), and never again in it: not as it goes on to the blocks or the damage. (r64)
- **CMB-046** A planeswalker or battle that left the battlefield keeps its lane, its chip reading "<name> (gone)", without a value: its attackers stay attacking but attack nothing and deal no combat damage (CR 506.4c). (r55)
- **CMB-039** A battle is attacked through its lane (CMB-012) as Automatum allows: its header shows its defense counters (CMB-060), its protector is the defending player of the creatures attacking it and the one who blocks them, and combat damage to it removes that many defense counters (CR 310.6, 310.9). (r47)

## Combat copies

- **CMB-002** Attackers and blockers are combat copies of real cards: the real card stays where it is. A card has at most one copy per combat area. (r60)
- **CMB-042** Combat cards are exactly the battlefield cards' size at the same zoom; they zoom with the band (CMB-056), so zooming out (or landscape's opening view, TBL-121) shrinks them with the battlefields. (r55)
- **CMB-003** Combat copies are always upright and can't be tapped. A first tap only selects one (CMB-032); tapping the selected copy takes it out of combat (CMB-004) while it is being declared: a staged attacker is unstaged, a placed blocker removed (also one placed to assume, CMB-083); otherwise it deselects it. While a legal target glows (STK-047; also while a hand card's cast panel still chooses its way, ZON-113) a copy glows with its creature, and a tap on it picks that creature, as a tap on the creature itself does. (r80)
- **CMB-063** A combat copy has no menu (no Combat menu, TBL-116) and there is no "Attack →" (CMB-048). Double-tap on a copy does nothing; hold inspects. (r57)
- **CMB-032** Tapping a combat copy selects it (not while targets are picked, CMB-003): a violet frame, with the copies it fights framed in amber and its arrows amber; a tap on the selected copy acts as CMB-003 says. Tapping the empty table clears the selection. The selection is the viewer's own and lasts only for the combat step it was made in. (r80)
- **CMB-004** Taking a copy out of combat (CMB-003) takes it out of the declaration Automatum waits for; the real card is unchanged. (r56)
- **CMB-006** When the real card leaves the battlefield, all its combat copies disappear; surviving blockers of a removed attacker stay in place. (r60)
- **CMB-035** A real Battlefield card in combat carries a grey "attacking" or "blocking" chip over its top edge, however small the card; a staged attacker carries none, its lane already names its target, and no chip under a combatant names whom it attacks or deals damage to (its lane shows it). (r74)
- **CMB-064** Attackers' copies show violet badges for the combat keywords Automatum works out (flying, reach, menace, trample, first strike, double strike, deathtouch, lifelink, vigilance) on their side away from the arrows. (r74)
- **CMB-065** These chips and badges (CMB-035, CMB-064) stack one per line, never wider than the card, and stay readable at any zoom: on a card narrower than full size (44 dp) the chip, its icon and its text scale down with the card, the text shrinking further and then clipped where it still doesn't fit, never left out. (r74)

## Declaring attackers

- **CMB-066** While attackers are awaited, one target is active: its chip filled violet and, once the band is open, its lane framed in violet (first the defending player's; tapping a chip makes its target the active one). (r63)
- **CMB-028** Tapping one of your creatures while attackers are awaited stages it as an attacker of the active target (its copy appears in that lane), tapping it again unstages it (in a stack a tap stages the next one, CMB-071); one that can't attack that target isn't staged and Automatum's reason shows. (r63)
- **CMB-067** Dragging works the same as tapping (onto a chip's lane, also in the collapsed band), also to retarget (CMB-015). (r63)
- **CMB-015** Dropping a staged attacker anywhere into another lane, also an empty one, retargets its whole pair (blockers stay attached); dropped in its own lane it stays. Combat drags do nothing else: no new pairs at dividers, no reordering or swapping, no role changes. (r60)
- **CMB-068** While attackers are awaited, the chips glow softly in violet as something to tap, more while a finger is down on one, and your creatures that may attack glow red-orange from behind (the cyan glow stays "can activate"; TBL-117). (r63)
- **CMB-069** Once the band is open the active lane has a dashed slot in the attackers' row, a card's size in red-orange: centred in an empty lane, else after its attackers (in the room CMB-058 keeps). It is brighter and faintly filled while the creature you are dragging may attack that target, and a drop on it stages the creature there. The collapsed band has no slot: it stays one chip row (CMB-001). (r63)
- **CMB-048** While attackers are declared the quick actions are "Rest → <target>" and "Clear", and the main button reads "Attack (N)" ("No attack" with nothing staged) and declares what the lanes hold as one step. Only the lanes pick targets: no card menu has "Attack →". (r52)
- **CMB-047** While attackers are awaited nobody holds priority: a tap on your creature only stages or unstages it, never activating anything (TBL-122), and card menus show their ability rows greyed ("Nobody holds priority now."); a mana ability an attack cost needs is paid as the main button declares (CMB-075). To act before attacks, Control stops at the beginning of combat (STK-036). (r59)
- **CMB-095** While you declare attackers or blockers a red-orange chip at the table's top says "Declaring attackers: abilities can't be used now" ("Declaring blockers: …" for blocks), fading in as declaring starts and out as it ends; the table has no frame. (r80; owner, 2026-11-21)
- **CMB-097** Meanwhile the combat band between the battlefields, collapsed or open, turns orange and glows: a soft band of light like draft Confirm's while Auto is on (DRF-294) crosses it from left to right about every 2.4 s, under the combat copies, lanes and chips; on the desktop table too. (owner, 2026-11-21)
- **CMB-096** While you declare attackers or blockers the cards on your battlefield that can't take part are dimmed: non-creatures and creatures that can't attack (or block) now; those that can keep their red-orange glow (CMB-068, CMB-076), and staged attackers and placed blockers stay undimmed. (r80)
- **CMB-070** While attackers or blockers are declared a stack (TBL-053) doesn't regroup or shift: its staged attackers (or placed blockers) move to the stack's right end together in one pile, each taken on top of those before (its own ×N counting them), with a wash and edge in red-orange (blockers in the block arrows' grey-blue); its other members stay exactly where they were (the ×N counting them). (r59)
- **CMB-071** While attackers or blockers are declared, a tap on a stack, its staged members too, stages (or picks as the blocker to place) its topmost member not yet taken (TBL-130); unstaging stays on the combat copy (CMB-003). (r59)

## Attack choices and costs

- **CMB-036** A staged attacker with enlist has an "Enlist" chip (a person-plus icon) under its copy: tapping it makes the creatures that may be enlisted (untapped, not staged, without summoning sickness) glow violet, and tapping one enlists it (one per enlist ability). The chip then reads "+N" (their power), filled violet, the enlisted card gets a violet "enlisted" chip, and tapping the chip again takes it back; any other tap stops picking. (r47)
- **CMB-072** Attacking taps the enlisted creature and adds its power until end of turn (CR 702.154a). Holding the Enlist chip says what enlist does. (r47)
- **CMB-037** A staged attacker's optional costs to attack ("You may exert this creature as it attacks", CR 508.1g) are toggle chips under its copy ("Exert" with an empty or checked box, filled violet when on); holding one shows the ability's text. (r47)
- **CMB-038** A staged attacker with banding has a "Band" chip (a link icon): tap it, then tap other staged attackers to put them in its band or take them out (instead of unstaging them), until the chip or the empty table is tapped again. (r49)
- **CMB-073** A band of two or more is joined by a violet bar, each copy with a filled "Band" chip, and attacks as a band (CR 702.22c); the main button greys with Automatum's reason when it isn't allowed. A blocker dragged onto one member blocks the whole band once declared. (r49)
- **CMB-045** Attack choices (enlist, optional costs, banding) are dropped when their attacker is unstaged or on Clear, last only for the declare attackers step they were made in, and travel with the shared staging (CMB-043), so the defender sees them. (r49)
- **CMB-044** A cost the rules make a creature pay to attack or block (CR 508.1h, 509.1d) shows as an amber chip under its copy ("{2}", "2 life", "Sacrifice"): its own share of the costs the whole staged declaration asks, as Automatum works them out (one chip for each, so a blocker blocking two attackers that each make it pay shows two); holding it shows the ability. The other player sees the chips but can't change them. (r64)
- **CMB-074** An X the board defines in an attack or block cost ("{X} for each creature they control that's attacking you, where X is the number of enchantments you control") shows as its amount ("{3}"), worked out again whenever the staging or the board changes it, never the letter X. (r64)
- **CMB-075** All attack and block costs (optional ones too, CMB-037) are paid as the main button declares: mana by Autotap when it's on, otherwise (and for any other payment choice) through Automatum's choice (STK-030); an unpayable cost greys the main button (TRN-063). (r64)

## Sharing the staging

- **CMB-043** Combat staging is shared as it is made: each change is an event of its own that the other player's table shows (as they play back), and the declaration ends it in its own event. (r52)
- **CMB-081** Behind the newest event (a decision stop in playback) the staging stays on its player's device and goes out only as the declaration, which ends the shared staging too: in its own event when it answers otherwise than assumed, in an event of its own right after when it confirms the assumption, so no old staging is left for a later combat. (r52)
- **CMB-082** An undo or rewind (yours, or the other player's approved) that takes the game back out of the step the staging was made in drops it, the shared staging and the staging kept on its player's device alike, so it never shows again when the game comes back to that step. (r52)

## Declaring blockers

- **CMB-034** While blockers are awaited, tapping one of your creatures selects it as the blocker to place (CMB-032) and the attackers it may block glow violet; tapping an attacker then blocks it (an arrow), tapping one it already blocks removes that block; dragging does the same. A placed blocker's copy is selected by its first tap and leaves combat on a tap while selected (CMB-003). (r63)
- **CMB-076** While blockers are awaited, under a finger an attacker's violet glow grows a little and brightens (TBL-117), and your creatures that may block glow red-orange from behind, as attackers do (CMB-068). (r63)
- **CMB-077** For the whole declare blockers step each attacker no blocker sits on yet has a dashed slot a card's size in the blockers' row, in red-orange: brighter and faintly filled on the attackers the creature selected or being dragged may block; a drop on it, or a tap on it with a creature selected, blocks that attacker. It goes once a blocker sits there, and a further blocker still goes onto the attacker or into that pair. (r63)
- **CMB-078** "Choose blockers" in small bold words sits on the band, in the gap between the card rows, until the first block. None of this (these words, the dashed slots, the glows) adds to the band's height. (r63)
- **CMB-079** While blockers are awaited the quick action is "Clear"; the main button reads "Block (N)" or "No blocks" and declares. The staging is shared (CMB-043). (r63)
- **CMB-031** Every block is an arrow from the blocker to the attacker it blocks, drawn in the gap between the blocker and attacker rows. A blocker sits in the pair of the first attacker it blocks; one blocking several attackers is one card with an arrow to each (no second copy), and an attacker blocked only by an arrow counts as blocked. (r49)
- **CMB-080** Before your declare blockers step your own creatures can't be dragged as blockers (while the other player stages or after their attack): a drag on one pans the table, as on any battlefield card (ZON-077), and nothing of it reaches Automatum. Only the player declaring drags their own creatures into the band, or the attacker the defender's to assume them (CMB-040). (r63)

## Assume blockers

- **CMB-040** Assume blockers: when you attack, the defender's blocks are a wait (TRN-070), whether they are away or online, and meanwhile your table is at once in assume mode on the combat band's lanes: no panel and no tap to enter it. (r61)
- **CMB-083** In assume mode you place the defender's creatures as blockers by tapping or dragging, exactly as the defender would and with the same look (CMB-034: those that may block glow red-orange, the dashed slots, "Choose blockers"): a placed blocker's copy is selected by its first tap and removed by a tap while selected; "Clear" is the quick action. (r61)
- **CMB-084** The main button reads "Assume no blockers" while none is placed and "Assume blockers" once one is; only pressing it assumes the blocks shown (Automatum's reason shows if it refuses them); nothing is ever assumed by itself. (r61)
- **CMB-085** Nothing of the blocks placed to assume is shared; blocks with a cost to block are never assumed. (r61)
- **CMB-049** The blocks placed to assume show on the lanes as the defender's would, with their arrows and the damage preview (CMB-029); holding a card inspects it. (r53)

## Damage preview

- **CMB-029** In the declare blockers step (also while a combat damage division is open, and in the first-strike damage step) both players see on the combat area what combat damage would do if nobody does anything more, with the blocks being arranged and the suggested divisions. What a target would be left with shows on its chip (CMB-061). (r57)
- **CMB-086** The damage preview puts one badge over the middle of each copy that would take damage or die, sized with the card so it fits at any zoom: a red splash with the damage ("3", no minus) when it survives, a black skull with the damage in its cranium when it dies. (r57)
- **CMB-087** A damage badge keeps its size whatever the number: a longer number is drawn smaller to fit, centred where the eye expects it (the splash's middle, the round cranium above the jaw). (r57)

## Dividing combat damage

- **STK-033** A combat damage division (the attacker's first, then the defender's, CR 510.1) is the dividing player's open choice (STK-030) whenever it can go more than one way (several creatures, or trample with more than lethal damage), also when every creature gets lethal damage anyway and only the spare damage could go elsewhere. Nobody divides for another player: the panel shows whose damage it is as the dealing creature's card at its top left (CMB-091). (r78)
- **STK-101** The suggested division is lethal damage to each in turn, the rest to the player with trample (shown on the panel's bottom line, CMB-093), else onto the last creature. A face-down creature the divider can't see shows as "A hidden card". (r78)
- **CMB-033** The damage division panel opens attached to the combat band (under it when it fits there whole, else above it when it fits there, else over the lower part of the table as tall as it needs, so its rows show; never docked above the hand) and shows the dividing player one division at a time ("1 of 2" when the step has more). (r55)
- **CMB-091** At the panel's top left is the creature dealing the damage as its small card with its power in a corner badge; beside it the title "Divide N damage", "Among what it fights" (" · first-strike damage step" there) and chips for its keywords that change what the damage does (first strike, double strike, trample, deathtouch, lifelink, infect, wither). (r55)
- **CMB-092** Then come the "In order" / "Free" switch with the hint beside it, and a row per creature (its card drawn small, its name, in order its place and a handle to drag it) whose damage is a chip with the number: red with ☠ when it is lethal (with deathtouch 1 is), grey when not; in Free the chip sits between − and +. (r55)
- **CMB-093** Then the eye, and with trample beside it on the same bottom line what tramples over as a compact chip, "Tramples 2 → Bob" with the defender's round avatar (a planeswalker's or battle's tiny art), red with ☠ when it is lethal to them, grey when not: nothing to choose, it is what the creatures don't get. (r55)
- **CMB-094** The panel opens filled in with the suggestion (STK-101), and the main button's OK deals the damage (TRN-063). An early division (STK-034) opens attached to the band the same way, with the same rows. (r55)
- **STK-034** Early answers are only your own and only at the newest event: a combat damage division of yours later in the same damage step may be given early in its rows (In order or Free, CMB-092). It is an event of its own that both tables show, kept for that creature's division in that damage step only (a first-strike step's isn't used again in the regular step, which asks anew), and applied when the division opens if it still fits. (r49)

## What combat damage did

- **CMB-050** After combat damage the band shows what it did until the combat ends, live and in catch-up playback, the damage's hold included (HIS-113), the first-strike and regular damage steps adding up. The life deltas (HIS-031) stay as they are. (r64)
- **CMB-088** Each attack target that combat damage reached (the defending player, a planeswalker, a battle) has one red splash like the damage preview's (CMB-086) just right of its chip with its net total from all lanes (for a player: the life it lost to that damage minus the life it gained at the same moment); nothing shows per lane. (r64)
- **CMB-089** Combat life gain on the band has an icon of its own, a green heart with a white "+N": a player whose gain at that moment was bigger shows it just right of their chip instead of the splash, and the attacking player's life gained with the damage (lifelink; they are never dealt combat damage, CR 510.1) shows on the band's edge facing their battlefield, under the defending player's chip's total, and as a filled green "+N" at their life square's top right. (r64)
- **CMB-090** Each creature combat damage was dealt to (attackers and blockers) carries the damage preview's badge (CMB-086) with what it was dealt in this combat: the red splash while it survives, the black skull while it lies on the battlefield with lethal damage in the damage's hold (HIS-113); in a first-strike damage step the preview's badges show instead. (r64)

## Combat holds in playback

- **HIS-113** A combat stays readable: whoever's combat it is, live and in catch-up playback, your view holds once when the attackers show and once on the damage (each combat damage step that deals any), each for the "Show plays and combat" time (LOB-082; at 0, Off, no hold). Declaring no attackers holds nothing. (r63)
- **HIS-146** During a combat hold your view waits (HIS-128): the later events are held as at a playback stop and your phone makes none of its own steps until it is done. (r63)
- **HIS-147** The damage's hold shows the board right after the damage is dealt: damage marked and life changed, what it did on the band and to each creature (CMB-050: a splash, or a skull on each it destroys), the creatures it destroys still on the battlefield; they die once the hold ends. Nothing shows for it but the board itself. (r63)
- **HIS-148** During a combat hold taps and double taps on the table act as at any other time, and a finger held there pauses it until lifted (HIS-126); no touch ends it early: the main button skips it (TRN-165). (r77)
- **HIS-149** When one board brings both, the attackers' step holds first (TBL-125). Combat holds and plays shown come in order. A combat hold ends when you enter timeline mode, when it is taken back, or when you went on past it on another device of yours. (r63)
