package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import org.asyncttwiz.android.EnginePlaybackPace
import org.asyncttwiz.android.EngineGame
import org.asyncttwiz.android.TablePresentation
import org.asyncttwiz.android.playbackDelay
import org.asyncttwiz.android.playbackWait
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner playtest decisions 2026-11-04 evening (async-play §29.1, §29.3, §29.6; moved from the interim
 * EngineCatchUpPlaybackTest) on two phones and the server: coming back, playback waits paused for the centre text with no
 * dialog; the opening (mulligans) is never played back; an empty combat goes by at once; an attack plays back in the
 * combat area.
 */
class CatchUpPlaybackTest : E2e() {
    /** Mountains and Storm Sparks, as the interim fixture's decks. */
    private val deck = TestDeck("Mountains", listOf(TestCards.mountain to 28, TestCards.spark to 12))

    /** Bob keeps and closes the app; Alice takes a mulligan, keeps with a card to the bottom and plays on to her first
     * main phase, where she plays a Mountain. Her phone assumes his passes at his pauses ("Always assume resolves",
     * HIS-096). */
    private fun aliceMulligansAndPlaysOn() {
        startGame(deck, deck, aliceSettings = org.asyncttwiz.protocol.game.PlaySettings(alwaysAssumeResolves = true))
        bothOpen()
        bob.tap("Keep")
        waitUntil("Bob's keep on Alice's table") { alice.shown.held.any { it.author == bob.id } }
        away(bob, alice)
        val first = alice.state.player(alice.me).hand.toList()
        alice.tap("Mulligan")
        waitUntil("Alice's new hand") { alice.state.player(alice.me).hand.toList() != first && alice.showsControl("Keep") }
        alice.tap("Keep")
        waitUntil("the Keep or bottom panel, or Alice's Main 1 (the first mulligan is free, TRN-062)") {
            alice.showsText("Keep or bottom") || alice.inOwnMain()
        }
        if (alice.showsText("Keep or bottom")) {
            // A tap moves the first card to Bottom, then OK
            val card = cardName(alice.state, alice.state.player(alice.me).hand.first())!!
            alice.nodes(hasContentDescription(card)).onFirst().performClick()
            waitUntil("the card in Bottom") { alice.shows(androidx.compose.ui.test.hasContentDescription("OK") and androidx.compose.ui.test.isEnabled()) }
            alice.tap("OK")
        }
        waitUntil("Alice's Main 1") { alice.inOwnMain() }
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield, all of it on the server") {
            alice.field("Mountain").isNotEmpty() && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }
    }

    /** Bob's app starts again and opens the game (loaded from the server), recording every board his table shows. */
    private fun bobComesBack(): MutableList<EngineGame.Shown> {
        bob = restart(bob)
        val shown = mutableListOf<EngineGame.Shown>()
        everyRun += { bob.game?.shown?.let { s -> if (shown.lastOrNull()?.version != s.version) shown += s } }
        bob.openGame(game, this)
        if (shown.isEmpty()) shown += bob.shown
        return shown
    }

    // HIS-033, HIS-094 (§29.1): coming back during the other player's opening, there is no playback of it: Alice's
    // mulligan and keep with its bottom card show at once (one board); past the opening the catch-up starts paused, no
    // dialog, nothing played until the centre text is tapped
    @Test fun theOpeningShowsAtOnceThenTheCatchUpWaitsPausedForPlay() {
        aliceMulligansAndPlaysOn()
        val keep = alice.shown.line.first { it.author == bob.id }
        val boards = bobComesBack()
        waitUntil("Bob's catch-up") { bob.status == GameDevice.Status.CatchUp }
        val openingBoard = assertNotNull(boards.firstOrNull { it.transition?.forward?.isNotEmpty() == true })
        val opening = openingBoard.transition!!.forward
        // His table opened at his keep (the opening played from it), and nothing paused before the opening was shown
        assertEquals(keep.id, opening.first().parentId, "Bob's local head is his keep")
        assertTrue(boards.takeWhile { it !== openingBoard }.none { it.status == GameDevice.Status.CatchUp }, "no paused catch-up in the opening")
        assertTrue(opening.size >= 2, "Alice's mulligan and keep in one board: ${opening.size}")
        assertTrue(opening.any { e -> e.engine?.steps.orEmpty().any { (it.input as? Input.Answer)?.answer == Answer.Option("Mulligan") ||
            (it.input as? Input.SetupAnswer)?.answer == Answer.Option("Mulligan") } }, "her mulligan among them")
        assertEquals(1, openingBoard.engine.turn.number, "the first turn reached at once")
        // Past the opening: paused until the centre text
        val head = bob.shown.localHead
        repeat(3) { run(skip = java.time.Duration.ofSeconds(2)) }
        assertEquals(GameDevice.Status.CatchUp, bob.status)
        assertEquals(head, bob.shown.localHead, "nothing played while paused")
        assertTrue(bob.shown.behind)
        assertTrue(bob.showsWatch())
        bob.tapToWatch()
        waitUntil("playing") { bob.status.let { it is GameDevice.Status.Playing || it is GameDevice.Status.Stopped } || bob.shown.localHead != head }
        waitUntil("the playback done or stopped") { bob.status !is GameDevice.Status.Playing }
        assertTrue(bob.shown.localHead != head, "the tap played Alice's turn back")
        assertTrue(bob.leaks.leaks.isEmpty() && alice.leaks.leaks.isEmpty())
    }

    // HIS-104 (§29.6): Alice's combat with no attackers changes nothing Bob can see: its events go by at once in his
    // playback; with playback's Full control on they keep the playback delay. CMB-001 (owner playtest 2026-11-06): the
    // combat band never opens in it (nobody attacked)
    @Test fun anEmptyCombatIsSkipped() {
        aliceMulligansAndPlaysOn()
        alice.hold("To combat")
        waitUntil("Alice's turn over") { alice.state.turn.active == bob.me && alice.engine { device.session.pending.isEmpty() } }
        val boards = bobComesBack()
        waitUntil("Bob's catch-up") { bob.status == GameDevice.Status.CatchUp }
        val from = bob.shown.localHead
        bob.tapToWatch()
        waitUntil("Bob caught up") {
            val s = bob.shown
            if (s.status is GameDevice.Status.Stopped) bob.playAlong()
            !s.behind && s.status !is GameDevice.Status.Playing && s.status !is GameDevice.Status.Stopped && s.status != GameDevice.Status.CatchUp
        }
        // What the playback showed: the main line from where Bob's table stood to where it stands now
        val line = bob.shown.held
        val shown = line.drop(line.indexOfFirst { it.id == from } + 1)
        assertTrue(shown.isNotEmpty())
        val combat = shown.filter { e -> e.engine?.changes.orEmpty().any { it is Change.Combat } ||
            e.engine?.steps.orEmpty().any { it.input is Input.DeclareAttackers } }
        // Alice has no creatures: her combat begins and ends with no attackers
        assertTrue(combat.any { e -> e.engine?.changes.orEmpty().any { it is Change.Combat && it.after?.attackers?.isEmpty() == true } },
            "Alice's empty combat shown: ${shown.map { e -> e.engine?.changes?.filterIsInstance<Change.Combat>() }}")
        for (event in combat) {
            assertTrue(EnginePlaybackPace.quiet(event), "an empty combat shows nothing: ${event.engine?.changes}")
            assertEquals(0L, playbackWait(event, playbackDelay(1f), ResponseMode.AutoSkip))
            assertEquals(playbackDelay(1f), playbackWait(event, playbackDelay(1f), ResponseMode.FullControl))
        }
        for (board in boards) assertNull(TablePresentation.scene(game, board.board, bob.id, 412f, 892f, false, reviewing = true).combatPanel,
            "the band stayed collapsed")
    }

    // HIS-021, CMB-001, CMB-007 (§29.6): Alice attacks while Bob is away (her phone assuming his pause after it); his
    // catch-up shows the attack in the combat band, which opens for it as in live combat, and keeps the playback delay
    // after it
    @Test fun anAttackPlaysBackInTheCombatArea() {
        startGame(TestDeck.thirds(TestCards.cub), deck, aliceSettings = org.asyncttwiz.protocol.game.PlaySettings(alwaysAssumeResolves = true),
            deal = { a, b -> "Mountain" in hand(a) && "Ember Cub" in hand(a) && "Mountain" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Cub", this)
        waitUntil("the Cub on the battlefield", along = listOf(bob)) { alice.field("Ember Cub").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Alice's next Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number >= 3 }
        // Bob is away from here on
        away(bob, alice)
        val attacker = alice.field("Ember Cub").first()
        alice.tap("To combat")
        waitUntil("Alice declaring attackers") { alice.state.turn.step == TurnStep.DeclareAttackers }
        alice.stageAttacker("Ember Cub", this)
        waitUntil("the Cub staged") { alice.stepArrow() == "Attack (1)" }
        alice.tap("Attack (1)")
        waitUntil("the attack on the server") {
            alice.state.turn.step != TurnStep.DeclareAttackers && alice.engine { device.session.pending.isEmpty() }
        }
        val boards = bobComesBack()
        waitUntil("Bob's catch-up") { bob.status == GameDevice.Status.CatchUp }
        val shown = recordShown { bob }
        bob.tapToWatch()
        fun attack(): GameEvent? = shown.firstOrNull { e ->
            e.engine?.steps.orEmpty().any { (it.input as? Input.DeclareAttackers)?.attacks?.isNotEmpty() == true } }
        waitUntil("the attack played back") { attack() != null && boards.any { it.board.board.combat?.pairs?.isNotEmpty() == true } }
        val shownAttack = assertNotNull(attack(), "the attack played back")
        assertTrue(!EnginePlaybackPace.quiet(shownAttack))
        assertEquals(playbackDelay(1f), playbackWait(shownAttack, playbackDelay(1f), ResponseMode.AutoSkip))
        val play = boards.first { it.board.board.combat?.pairs?.isNotEmpty() == true }.board
        val id = atw(attacker)
        val pair = assertNotNull(play.board.combat).pairs.single()
        assertEquals(id, pair.attackers.single().sourceId)
        val scene = TablePresentation.scene(game, play, bob.id, 412f, 892f, false, reviewing = true)
        val copy = assertNotNull(scene.items.firstOrNull { it.viewport == "combat" && it.sourceId == id }, "the attacker in the combat area")
        assertTrue(copy.visible.isNotEmpty(), "the attacker's copy shows")
        assertNotNull(scene.combatPanel, "the combat band opened with the attack")
    }
}
