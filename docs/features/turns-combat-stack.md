# Turns, combat and Stack

Turn and step tracking, whose move it is, the top and bottom game bars, life, game start and result suggestions (`TRN`), the combat area (`CMB`) and the Stack (`STK`).

### Turn structure

- **TRN-004** While the other player must act, the main button (TRN-063) holds: nothing where nothing of yours follows before their next real action (their own main phase with an empty Stack, their declaration of attackers, their choices and, before your own opening is done, their mulligan); "Assume resolves" (something on the Stack) or "Assume pass" (an empty Stack: nothing to resolve) where they hold priority anywhere else and your phone doesn't assume their pass by itself (HIS-096, TRN-070); "Assume no blockers" / "Assume blockers" while they declare blockers (CMB-040; nothing where their blocks can't be assumed); "Assume keep" while they decide on their opening once your own is done (TRN-070); a greyed Assume where assuming would show you hidden cards or a random result (TRN-071); and "Cancel request" while your own undo request is open (HIS-100). Where it holds nothing, its place shows a quiet status in plain words instead of a button: grey words, nothing to tap, so the pause doesn't read as lag: "Bob's answer…" (they may respond or act), "Bob is choosing…", "Bob is blocking…", "Bob's mulligan…", "Bob's move…" (their main phase or their attack); none while your own events are on their way or an undo request or a result suggestion is answered in its dialog. (r76)

### Top bar


### Whose turn and whose move


### Bottom bar and quick actions

- **TRN-023** Your opening's Mulligan and Keep (TRN-062) are each one Undo step, however many changes they make; the settings in the quick actions (TRN-059) are no events at all (SYS-071). (r60)

### Game start and mulligans


### Life


### Emblems


### Opponent presence


### Waiting, assumptions and results

- **TRN-066** The wait: while the other player must act (online or away), a semi-transparent dark band right under their hand (in portrait under their row, which holds it, ZON-001; on the desktop table at the battlefields' top, from their seat header to the battlefields' right edge, WEB-016), edge to edge and fading out toward both sides only over a narrow margin, every line of its words on its solid middle (in portrait and landscape alike), in white text of up to 3 lines (never taking a touch; in landscape too; not while a play is shown, HIS-112), says why: "<name> may respond to <spell>" (an ability as "<source>'s ability", STK-046), "<name> may act · <step>", "<name> is declaring blockers", "<name> is deciding on a mulligan", "<name> is choosing · <card>" (the card only when you can see it), "Waiting for <name>", which, when they are behind you (you went ahead on assumptions about them they haven't confirmed), says where they are instead: "Bob is at turn 5 · their combat" (a combat step as "combat", any other step by its name, "your" in your own turn). (r64)
- **TRN-070** Where the other player's answer may be assumed (HIS-096), the main button holds "Assume resolves" (something on the Stack, STK-028), "Assume pass" (nothing on the Stack, as after their blocks), "Assume no blockers" / "Assume blockers" (CMB-040), or "Assume keep" (their opening, once your own opening is done, TRN-027), each in the Assume violet with the Assume icon (TRN-063), while they must act, whether they are online or away, with no timeout, also at points they began, where only their mode decides whether your phone waits (HIS-096); holding "Assume resolves" resolves the whole Stack (TRN-017), holding any other Assume is its tap; greyed where assuming would show you hidden information or a random result (TRN-071). Unless "Assume resolves" is Never, their pause at your end step and right after you attack is never a wait (HIS-096). A tap of Assume and your phone's own assumption are the same press (HIS-109). Your phone never assumes the other player's keep by itself: only a press of Assume keep does. (r76)
- **TRN-067** Information rule: while your line rests on an unconfirmed assumption, nothing you do or assume may show you a card you couldn't see (the other player's hand or library, face-down cards, random discards; never your own library) or a random result (coin, die, random choice), apart from what the game's settings allow (LOB-135: by default the other player's library cards, not their hand's; with "Allow assumes that reveal hands" their hand cards too, a random discard of theirs included; never face-down cards or other random results). Such an action is refused before it shows: the card shakes and holding it says "Wait for the other player to catch up: this would show you hidden cards or a random result." Where assuming the other player's answer would do so, the wait's Assume shows greyed (TRN-071). The server checks the same for cards, with the game's settings. (r58)
- **TRN-071** Under the information rule (TRN-067, with the game's settings LOB-135: a reveal they allow neither stops nor greys anything), your phone's own passes after an action of yours stop before one that would show you something (you keep priority there), and such an assumption isn't made: the wait reads "Waiting for <name> · going on would show you hidden cards or a random result" (TRN-066) and the main button holds the wait's Assume ("Assume resolves", "Assume pass") greyed: a tap shakes it and holding it says "Assuming this would show you hidden cards or a random result: wait for <name>." (r74)
- **TRN-068** The unconfirmed mark: while actions of yours rest on answers the other player hasn't confirmed yet, a small violet dot (no number) sits at the main button's corner; a tap or hold says "<name> hasn't seen your last moves yet; if <name> answers differently, they'll be taken back". Only you see it. (r50)

### Combat area


### Attack targets


### Blocks and arrows


### Declaring attackers and blockers


### Putting cards on the Stack


### Stack display


### Stack list


### Casting


### Priority and resolution

- **STK-028** The other player sees the wait "Bob may respond to Storm Spark" (TRN-066) with "Assume resolves" on the main button where it may be assumed (a tap assumes Bob lets it resolve), and no Pass for Bob (TRN-015). (r54)
