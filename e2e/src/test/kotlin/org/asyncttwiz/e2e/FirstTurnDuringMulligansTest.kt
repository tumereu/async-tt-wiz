package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The first turn during the other player's mulligans (owner, 2026-11-15; TRN-027, TRN-124, HIS-115, HIS-193, HIS-194, HIS-116, HIS-117, HIS-197): Alice keeps
 * while Bob hasn't opened the game, presses Assume keep and plays her turn 1. Bob's game stays in his opening, showing
 * nothing of her turn, until he keeps; his Keep confirms her turn, his Mulligan makes it again on top of his new hand,
 * standing as far as it comes out the same for her and taken back from her first action that doesn't.
 */
class FirstTurnDuringMulligansTest : E2e() {
    /** HIS-117: until Bob keeps, nothing of Alice's turn reaches his table (checked at every round of the phones). */
    private var bobKept = false
    private val lag = mutableListOf<String>()

    private fun watchBobsLag() {
        everyRun += {
            val shown = runCatching { bob.game?.shown }.getOrNull()
            if (!bobKept && shown != null) {
                val s = shown.engine
                if (s.turn.number != 0) lag += "turn ${s.turn.number} shown"
                if (s.battlefield.isNotEmpty()) lag += "a battlefield card shown: ${s.battlefield}"
                if (s.player(alice.me).life != 20 || s.player(bob.me).life != 20) lag += "a life change shown"
            }
        }
    }

    /** Alice opens the game alone, keeps, presses Assume keep (her phone never assumes it by itself, even with "Always
     * assume resolves") and is in her Main 1; Bob's app is up, the game not opened. */
    private fun aliceAssumesBobsKeep(aliceDeck: TestDeck, bobDeck: TestDeck = TestDeck.thirds(), deal: Deal.(TestAccount, TestAccount) -> Boolean) {
        startGame(aliceDeck, bobDeck, aliceSettings = PlaySettings(assume = AssumeResolves.Always), deal = deal)
        watchBobsLag()
        alice.openGame(game, this)
        alice.tap("Keep")
        waitUntil("Assume keep on Alice's main button") { alice.showsControl("Assume keep") }
        assertEquals(0, alice.state.turn.number, "nothing assumed by itself")
        alice.tap("Assume keep")
        waitUntil("Alice's Main 1 on her Assume keep") { alice.inOwnMain() }
    }

    private fun alicePlaysMountainAndCub() {
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Cub", this)
        waitUntil("the Cub on Alice's battlefield") { alice.field("Ember Cub").isNotEmpty() && alice.inOwnMain() }
        waitUntil("all of Alice's on the server") { alice.engine { device.session.pending.isEmpty() } }
    }

    /** Bob opens the game: his opening, with his Keep and Mulligan; nothing of Alice's turn. */
    private fun bobOpens() {
        bob.openGame(game, this)
        // Taking taps: greyed while Alice's events play back on his table
        waitUntil("Bob's opening quick actions") { bob.showsEnabledControl("Mulligan") && bob.showsEnabledControl("Keep") }
        assertEquals(0, bob.state.turn.number)
        assertTrue(bob.state.player(alice.me).hand.size == 7 && bob.state.battlefield.isEmpty(), "Bob sees nothing of her turn")
    }

    private fun handNames(player: E2ePlayer): List<String> = player.state.let { s -> s.player(player.me).hand.map { cardName(s, it) ?: "?" } }.sorted()

    /** The mulligans on [player]'s phone, in the line's order. */
    private fun mulligans(player: E2ePlayer): List<org.asyncttwiz.protocol.game.GameEvent> = player.engine { device.session.events }.filter { e ->
        e.engine?.steps.orEmpty().any { s -> (s.input as? Input.Answer)?.answer == Answer.Option("Mulligan") ||
            (s.input as? Input.SetupAnswer)?.answer == Answer.Option("Mulligan") } }

    /** HIS-117 and the information rule (I-D56 §5): Bob saw nothing of her turn before keeping, neither phone a card it
     * couldn't see. */
    private fun assertNothingLeaked() {
        assertTrue(lag.isEmpty(), "Bob saw her turn before keeping: $lag")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "leaks: ${alice.leaks.leaks} ${bob.leaks.leaks}")
    }

    /** Alice casts [name] at Bob (her target bar, his life square) and it resolves on his assumed pass. */
    private fun aliceCastsAtBob(name: String) {
        alice.playFromHand(name, this)
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose target player") and isHeading()) }
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("$name resolved") {
            if (alice.showsRevealOk()) alice.revealOk()
            alice.state.player(alice.me).graveyard.any { cardName(alice.state, it) == name } && alice.inOwnMain() && !alice.showsRevealOk()
        }
        waitUntil("all of Alice's on the server") { alice.engine { device.session.pending.isEmpty() } }
    }

    // TRN-027, TRN-062, HIS-042, HIS-192 (spec §8 scenario 1): both open the game; Alice mulligans twice while Bob hasn't declared, each
    // new hand showing at once (the hand her own random stream deals); Bob mulligans once (his own stream's hand, whatever she
    // did); the events read in the order made, on both phones
    @Test fun eachPlayersMulligansAreTheirOwn() {
        startGame()
        watchBobsLag()
        bothOpen()
        for (n in 1..2) {
            alice.tap("Mulligan")
            waitUntil("Alice's hand after her mulligan $n") { handNames(alice) == deal.mulliganHand(alice.account, n).sorted() }
        }
        assertEquals(deal.hand(bob.account).sorted(), handNames(bob), "Bob's hand untouched")
        bob.tap("Mulligan")
        waitUntil("Bob's hand after his mulligan") { handNames(bob) == deal.mulliganHand(bob.account, 1).sorted() }
        waitUntil("all three mulligans on both phones") { mulligans(alice).size == 3 && mulligans(bob).size == 3 }
        assertEquals(listOf(alice.id, alice.id, bob.id), mulligans(alice).map { it.author }, "in the order made")
        assertEquals(mulligans(alice).map { it.id }, mulligans(bob).map { it.id })
        assertTrue(!alice.refusalSeen && !bob.refusalSeen, "nothing refused: ${alice.shownTexts} ${bob.shownTexts}")
        assertNothingLeaked()
    }

    // HIS-115, HIS-195, SYS-077 (spec §8 scenario 6): Alice's Mountain is on its way (her phone's sending held) when Bob's mulligan
    // reaches the server: the server refuses the land as stale, her phone makes it again on the event made from the one it
    // was made on, and it ends up played once, with no note
    @Test fun aLandOnItsWayDuringAReapplyIsPlayedOnce() {
        aliceAssumesBobsKeep(TestDeck.thirds()) { a, _ -> "Mountain" in hand(a) }
        waitUntil("all of Alice's on the server") { alice.engine { device.session.pending.isEmpty() } }
        bobOpens()
        alice.link.holdOutgoing()
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on Alice's table, on its way") { alice.field("Mountain").isNotEmpty() && alice.engine { device.session.pending.isNotEmpty() } }
        bob.tap("Mulligan")
        waitUntil("Bob's mulligan taken") { mulligans(bob).isNotEmpty() && bob.engine { device.session.pending.isEmpty() } }
        alice.link.releaseOutgoing()
        waitUntil("Alice's Mountain on the server") {
            alice.engine { device.session.pending.isEmpty() && device.session.events.any { it.carriedFrom != null } } &&
                alice.engine { device.session.events }.count { e -> e.engine?.steps.orEmpty().any { it.input is Input.PlayLand } } == 1 &&
                alice.field("Mountain").isNotEmpty()
        }
        assertEquals(1, alice.field("Mountain").size, "played once")
        assertTrue(!alice.refusalSeen, "no note: ${alice.shownTexts}")
        waitUntil("Bob in his opening with a new hand") { bob.showsControl("Keep") }
        bobKept = true
        bob.tap("Keep")
        waitUntil("Alice's Mountain on Bob's table", along = listOf(bob)) {
            if (bob.showsWatch()) bob.tapToWatch()
            bob.state.battlefield.count { cardName(bob.state, it) == "Mountain" } == 1
        }
        assertNothingLeaked()
    }

    // HIS-115, HIS-193, HIS-084, HIS-087 (spec §8 scenario 7): Alice casts her Ember Cub on Bob's assumed keep; Bob mulligans and her
    // turn stands; her Undo then takes the Cub back at once, as any free undo (nothing of Bob's comes after it); Bob keeps and
    // his playback shows her Mountain, never the Cub
    @Test fun anUndoWhileBobDecides() {
        aliceAssumesBobsKeep(TestDeck.thirds()) { a, _ -> "Mountain" in hand(a) && "Ember Cub" in hand(a) }
        alicePlaysMountainAndCub()
        bobOpens()
        bob.tap("Mulligan")
        waitUntil("Alice told her turn stands") { alice.told("${bob.name} took a mulligan · your turn stands") }
        alice.tap("Undo")
        waitUntil("the Cub back in Alice's hand, on the server too") {
            alice.field("Ember Cub").isEmpty() && alice.hand("Ember Cub").isNotEmpty() && alice.engine { device.session.pending.isEmpty() } &&
                serverGame()?.headId == alice.engine { device.session.head }
        }
        assertTrue(alice.field("Mountain").isNotEmpty(), "the Mountain stays")
        assertTrue(!alice.refusalSeen, "no refusal: ${alice.shownTexts}")
        waitUntil("Bob in his opening with a new hand") { bob.showsControl("Keep") }
        bobKept = true
        bob.tap("Keep")
        waitUntil("Alice's turn on Bob's table", along = listOf(bob)) {
            if (bob.showsWatch()) bob.tapToWatch()
            bob.state.battlefield.any { cardName(bob.state, it) == "Mountain" } && !bob.engine { device.session.behind }
        }
        assertTrue(bob.state.battlefield.none { cardName(bob.state, it) == "Ember Cub" }, "no Cub")
        assertTrue(bob.leaks.shown.none { e -> e.engine?.steps.orEmpty().any { it.input is Input.Cast } }, "Bob never saw the Cub cast")
        assertNothingLeaked()
    }

    // HIS-115, HIS-193, HIS-194, HIS-116 (spec §8 scenario 9): Alice plays a Mountain and casts Ember Echo at Bob (seven cards in his hand: seven
    // damage). His free mulligan keeps seven cards: her turn stands ("your turn stands"), his life still 13 on her table. His
    // mulligan to six: the Echo is taken back from its cast (with no note, HIS-185), the Mountain stays, his life is 20
    // again; Bob, still deciding, sees none of it
    @Test fun aCountOfTheOpponentsHandStandsOnlyWhileItIsTheSame() {
        aliceAssumesBobsKeep(TestDeck("Echoes", listOf(TestCards.mountain to 20, TestCards.echo to 20))) { a, _ ->
            "Mountain" in hand(a) && "Ember Echo" in hand(a) }
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        aliceCastsAtBob("Ember Echo")
        assertEquals(13, alice.state.player(bob.me).life, "seven damage")
        bobOpens()
        bob.tap("Mulligan")
        waitUntil("Alice told her turn stands") { alice.told("${bob.name} took a mulligan · your turn stands") }
        assertEquals(13, alice.state.player(bob.me).life, "seven cards either way")
        waitUntil("Bob in his opening with his second hand") { mulligans(bob).size == 1 && bob.engine { device.session.pending.isEmpty() } && bob.showsEnabledControl("Mulligan") }
        bob.tap("Mulligan")
        waitUntil("her Echo taken back on Alice's device") { alice.branchLogged() }
        waitUntil("Alice back before the Echo") {
            alice.state.player(bob.me).life == 20 && alice.state.player(alice.me).graveyard.none { cardName(alice.state, it) == "Ember Echo" }
        }
        assertTrue(!alice.toldATakeBack(bob), "HIS-185: no note: ${alice.shownTexts}")
        assertTrue(alice.field("Mountain").isNotEmpty(), "the Mountain stands")
        assertEquals(0, bob.state.turn.number, "Bob still in his opening")
        assertNothingLeaked()
    }

    // HIS-115, HIS-194, CR 103.6a (spec §8 scenario 10): Bob's first hand has no Ember Vanguard; Alice assumes his keep and plays a
    // Mountain. Bob's mulligan brings him one: after the keep the game asks him whether it begins on the battlefield (a real
    // wait), so nothing of her turn comes out the same and all of it goes back (with no note, HIS-185). Bob keeps and puts the Vanguard onto the battlefield; her turn 1 begins again with it there
    @Test fun anOpeningThatChangesTheBoardTakesTheWholeTurnBack() {
        val vanguards = TestDeck("Vanguards", listOf(TestCards.mountain to 34, TestCards.vanguard to 6))
        aliceAssumesBobsKeep(TestDeck.thirds(), vanguards) { a, b ->
            "Mountain" in hand(a) && "Ember Vanguard" !in hand(b) && "Ember Vanguard" in mulliganHand(b, 1) }
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        waitUntil("all of Alice's on the server") { alice.engine { device.session.pending.isEmpty() } }
        bobOpens()
        bob.tap("Mulligan")
        waitUntil("her Mountain taken back on Alice's device") { alice.branchLogged() }
        waitUntil("Alice's whole turn back") { alice.field("Mountain").isEmpty() && alice.state.turn.number == 0 }
        assertTrue(!alice.toldATakeBack(bob), "HIS-185: no note: ${alice.shownTexts}")
        waitUntil("Bob's new hand, with the Vanguard") { "Ember Vanguard" in handNames(bob) && bob.showsControl("Keep") }
        bobKept = true
        bob.tap("Keep")
        val inChoice = hasAnyAncestor(hasContentDescription("Automatum choice"))
        waitUntil("Bob asked about the Vanguard") { bob.shows(hasContentDescription("Ember Vanguard", substring = true) and inChoice) }
        bob.nodes(hasContentDescription("Ember Vanguard", substring = true) and inChoice)[0].performClick()
        run(skip = java.time.Duration.ofMillis(300))
        if (bob.showsControl("OK")) bob.tap("OK")
        waitUntil("the Vanguard on the battlefield, Alice's turn 1 again", along = listOf(bob)) {
            alice.field("Ember Vanguard", owner = bob.me).isNotEmpty() && alice.inOwnMain()
        }
        assertTrue(alice.field("Mountain").isEmpty(), "her Mountain still in her hand")
        assertNothingLeaked()
    }

    // HIS-115, HIS-196, HIS-087, HIS-204 (spec §3.6): Bob mulligans twice (the second owes a card to the bottom); Alice presses Assume keep and
    // plays a Mountain and an Ember Cub; Bob puts a card on the bottom (her turn made again on it) and then taps Undo: free
    // (it showed him nothing new; her turn after it isn't taken back), he is at his bottom choice again, and her turn is made
    // again on that point and stands, her table not going back; he still sees nothing of it until he keeps
    @Test fun bobsUndoInHisOpeningMakesHerTurnAgain() {
        startGame(aliceSettings = PlaySettings(assume = AssumeResolves.Always), deal = { a, _ -> "Mountain" in hand(a) && "Ember Cub" in hand(a) })
        watchBobsLag()
        alice.openGame(game, this)
        alice.tap("Keep")
        bob.openGame(game, this)
        val bottomScreen = hasContentDescription("Keep or bottom")
        val panels = bottomScreen or hasContentDescription("Automatum choice")
        for (n in 1..2) {
            // Taking taps: greyed while Alice's Keep plays back on his table
            waitUntil("Bob's Mulligan $n") { bob.showsEnabledControl("Mulligan") && bob.engine { device.session.pending.isEmpty() } && mulligans(bob).size == n - 1 }
            bob.tap("Mulligan")
        }
        waitUntil("the bottom of Bob's second mulligan asked") { mulligans(bob).size == 2 && bob.shows(panels) }
        waitUntil("Assume keep on Alice's main button") { mulligans(alice).size == 2 && alice.showsControl("Assume keep") }
        alice.tap("Assume keep")
        waitUntil("Alice's Main 1 on her Assume keep") { alice.inOwnMain() }
        alicePlaysMountainAndCub()
        fun bottomOne() {
            val hand = bob.state.player(bob.me).hand.toList()
            bob.nodes(hasContentDescription(cardName(bob.state, hand.first())!!) and hasAnyAncestor(panels))[0].performClick()
            waitUntil("the card picked for the bottom") {
                bob.state.player(bob.me).hand.size == 6 || bob.shows(hasText("Bottom · 1") and hasAnyAncestor(panels))
            }
            if (bob.state.player(bob.me).hand.size == 7) bob.tap("OK")
            waitUntil("six cards in Bob's hand, on the server") { bob.state.player(bob.me).hand.size == 6 && bob.engine { device.session.pending.isEmpty() } }
        }
        bottomOne()
        waitUntil("her turn made again on his bottom choice") { alice.engine { device.session.events.any { it.carriedFrom != null } && device.session.pending.isEmpty() } }
        val made = alice.engine { device.session.events.count { it.carriedFrom != null } }
        val told = alice.takeBacksLogged()
        bob.tap("Undo")
        waitUntil("Bob at his bottom choice again") { bob.state.player(bob.me).hand.size == 7 && bob.shows(panels) }
        waitUntil("her turn made again on the undo's point") {
            alice.engine { device.session.pending.isEmpty() && device.session.events.last().carriedFrom != null } &&
                serverGame()?.headId == alice.engine { device.session.head } && serverGame()?.lines == 1
        }
        assertTrue(alice.field("Mountain").isNotEmpty() && alice.field("Ember Cub").isNotEmpty(), "her turn stands on her table")
        assertEquals(told, alice.takeBacksLogged(), "nothing taken back")
        assertTrue(!alice.toldATakeBack(bob), "nothing told taken back: ${alice.shownTexts}")
        assertTrue(!alice.refusalSeen && !bob.refusalSeen, "nothing refused")
        assertTrue(made > 0)
        assertEquals(0, bob.state.turn.number, "Bob sees nothing of her turn")
        bottomOne()
        bobKept = true
        bob.tap("Keep")
        waitUntil("Alice's turn on Bob's table", along = listOf(bob)) {
            if (bob.showsWatch()) bob.tapToWatch()
            bob.state.battlefield.any { cardName(bob.state, it) == "Ember Cub" }
        }
        assertNothingLeaked()
    }

    // TRN-027, TRN-124, TRN-070, HIS-096, HIS-183, HIS-117, HIS-197: Bob keeps after Alice played: nothing is made again, her events stay, and her turn
    // reaches him only after his Keep
    @Test fun bobsKeepConfirmsTheTurnPlayedOnIt() {
        aliceAssumesBobsKeep(TestDeck.thirds()) { a, _ -> "Mountain" in hand(a) && "Ember Cub" in hand(a) }
        alicePlaysMountainAndCub()
        val line = alice.engine { device.session.events.map { it.id } }
        bobOpens()
        bobKept = true
        bob.tap("Keep")
        waitUntil("Alice's turn on Bob's table", along = listOf(bob)) {
            if (bob.showsWatch()) bob.tapToWatch()
            bob.state.battlefield.any { cardName(bob.state, it) == "Ember Cub" }
        }
        assertEquals(line, alice.engine { device.session.events.map { it.id } }.take(line.size), "her events stand as they were")
        assertTrue(alice.engine { device.session.events }.none { it.carriedFrom != null }, "nothing made again")
        assertTrue(lag.isEmpty(), "Bob saw her turn before keeping: $lag")
    }

    // HIS-115, HIS-193, HIS-116, HIS-117: Bob mulligans (the first is free): Alice's turn comes out the same and stands: her table
    // doesn't go back, "Bob took a mulligan · your turn stands"; Bob is in his opening again with a new hand and sees
    // nothing until he keeps
    @Test fun bobsMulliganLeavesTheTurnStanding() {
        aliceAssumesBobsKeep(TestDeck.thirds()) { a, _ -> "Mountain" in hand(a) && "Ember Cub" in hand(a) }
        alicePlaysMountainAndCub()
        val bobsHand = alice.state.player(bob.me).hand.toList()
        bobOpens()
        bob.tap("Mulligan")
        waitUntil("Alice told her turn stands") { alice.told("${bob.name} took a mulligan · your turn stands") }
        assertTrue(alice.field("Mountain").isNotEmpty() && alice.field("Ember Cub").isNotEmpty(), "her turn stays on her table")
        assertTrue(alice.engine { device.session.events }.any { it.carriedFrom != null }, "made again")
        assertTrue(!alice.refusalSeen, "nothing refused: ${alice.shownTexts}")
        waitUntil("Bob in his opening with a new hand") {
            bob.showsControl("Keep") && bob.state.player(bob.me).hand.toList() != bobsHand && bob.state.turn.number == 0
        }
        bobKept = true
        bob.tap("Keep")
        waitUntil("Alice's turn on Bob's table", along = listOf(bob)) {
            if (bob.showsWatch()) bob.tapToWatch()
            bob.state.battlefield.any { cardName(bob.state, it) == "Ember Cub" }
        }
        assertTrue(lag.isEmpty(), "Bob saw her turn before keeping: $lag")
    }

    // HIS-115, HIS-194, HIS-116, HIS-097, HIS-117: Alice plays a Mountain, then Ember Augury at Bob (she sees his top two cards). Bob
    // mulligans: her table goes back only to the Augury (with no note, HIS-185), the Mountain stays, the Augury is back in her hand; Bob never sees it
    @Test fun aRevealThatComesOutDifferentlyIsTakenBack() {
        aliceAssumesBobsKeep(TestDeck("Auguries", listOf(TestCards.mountain to 20, TestCards.augury to 20))) { a, _ ->
            "Mountain" in hand(a) && "Ember Augury" in hand(a) }
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        val auguries = alice.hand("Ember Augury").size
        alice.playFromHand("Ember Augury", this)
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose target player") and isHeading()) }
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("the Augury resolved: Bob's top two cards shown to Alice") {
            if (alice.showsRevealOk()) alice.revealOk()
            alice.state.player(alice.me).graveyard.any { cardName(alice.state, it) == "Ember Augury" } && alice.inOwnMain() && !alice.showsRevealOk()
        }
        waitUntil("all of Alice's on the server") { alice.engine { device.session.pending.isEmpty() } }
        bobOpens()
        bob.tap("Mulligan")
        waitUntil("her Augury taken back on Alice's device") { alice.branchLogged() }
        waitUntil("Alice back before the Augury") {
            alice.state.player(alice.me).graveyard.none { cardName(alice.state, it) == "Ember Augury" } && alice.hand("Ember Augury").size == auguries
        }
        assertTrue(!alice.toldATakeBack(bob), "HIS-185: no note: ${alice.shownTexts}")
        assertTrue(alice.field("Mountain").isNotEmpty(), "the Mountain stands")
        bobKept = true
        bob.tap("Keep")
        waitUntil("Bob caught up", along = listOf(bob)) {
            if (bob.showsWatch()) bob.tapToWatch()
            bob.state.battlefield.any { cardName(bob.state, it) == "Mountain" }
        }
        assertTrue(bob.leaks.shown.none { e -> e.engine?.steps.orEmpty().any { (it.input as? arcanum.engine.input.Input.Cast) != null } },
            "Bob never saw the Augury cast")
        assertTrue(lag.isEmpty(), "Bob saw her turn before keeping: $lag")
    }
}
