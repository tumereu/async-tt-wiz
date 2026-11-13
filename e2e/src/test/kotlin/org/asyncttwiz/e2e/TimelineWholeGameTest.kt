package org.asyncttwiz.e2e

import org.asyncttwiz.android.timeline
import org.asyncttwiz.android.historyLoading
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import arcanum.engine.change.Change
import arcanum.engine.input.Input
import org.asyncttwiz.android.undoPoints
import org.asyncttwiz.protocol.game.GameEvent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner, prod playtest 2026-11-07: "Timeline doesn't reach to the beginning of the game? Why not? We can remove any undo
 * limit restrictions of 50 moves also." A phone holds only the recent part of a long game; the timeline and the Undo
 * dialog load older history from the server as they go back, to the game's first event, and an undo to an early point
 * works.
 */
class TimelineWholeGameTest : E2e() {
    private val mountains = TestDeck("Mountains", listOf(TestCards.mountain to 40))

    /** Alice and Bob play along (a Mountain and End turn each turn, passing otherwise) to turn [turns], then settle. */
    private fun longGame(turns: Int) {
        startGame(mountains, mountains)
        bothOpen()
        waitUntil("turn $turns on both tables", timeoutSeconds = 300, along = listOf(alice, bob)) {
            alice.state.turn.number >= turns && bob.state.turn.number >= turns
        }
        waitUntil("both tables settled at the head") {
            quiet() && listOf(alice, bob).all { !it.shown.behind && it.engine { device.session.pending.isEmpty() } }
        }
    }

    /** Whether [e] is the game's first event (the server's start). */
    private fun start(e: GameEvent) = e.engine?.steps.orEmpty().any { it.input is Input.StartGame }

    /** The turn the first event of [player]'s own line was made in (before any older history is loaded). */
    private fun firstTurnHeld(player: E2ePlayer): Int = player.shown.held.firstNotNullOf { e ->
        e.engine?.changes?.filterIsInstance<Change.Step>()?.firstOrNull()?.before?.number }

    /** The strip's node that takes a position (accessibility's set progress), under [under] when given. */
    private fun E2ePlayer.strip(under: String? = null): SemanticsNodeInteraction {
        val scrub = SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)
        return node(if (under == null) scrub else scrub and hasAnyAncestor(hasTestTag(under)))
    }

    /** Moves [player]'s strip to its oldest event until nothing older loads: the timeline grows as it goes. */
    private fun scrubToStart(player: E2ePlayer, under: String? = null, oldest: () -> Boolean) {
        repeat(60) {
            if (oldest()) return
            player.strip(under).performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
            run()
            waitUntil("${player.name}'s older history loaded") { !player.game!!.historyLoading }
        }
        assertTrue(oldest(), "${player.name} reached the oldest event")
    }

    // HIS-111, HIS-004, HIS-012 (owner 2026-11-07): after a dozen turns Alice's phone holds only their recent part; in
    // timeline mode, going back past it loads older history page by page, and the strip reaches the game's first event,
    // with its board (the opening) shown, still in timeline mode
    @Test fun theTimelineReachesTheGamesFirstEvent() {
        longGame(turns = 12)
        assertTrue(firstTurnHeld(alice) >= 3, "Alice's phone holds only the recent turns: from turn ${firstTurnHeld(alice)}")
        alice.openTimeline(this)
        waitUntil("Alice in timeline mode") { alice.showsTimelineLook() && alice.showsControl("Previous") }
        val game = alice.game!!
        scrubToStart(alice) { game.shown!!.olderComplete && cursor(alice) == 0 }
        assertTrue(alice.showsTimelineLook(), "still in timeline mode: ${alice.screen()}")
        val first = game.timeline.events(game, names()).first()
        assertTrue(game.event(first.id)?.let(::start) == true, "the game's first event: ${first.description}")
        assertEquals("The game began", first.description)
        val board = game.reviewPlay(first.id)
        assertEquals(0, board?.engine?.state?.turn?.number, "the opening's board at the game's first event")
    }

    /** The position of the event [player]'s strip (under [under] when given) has chosen. */
    private fun cursor(player: E2ePlayer, under: String? = null): Int =
        player.strip(under).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current.toInt()

    // HIS-111, HIS-099, HIS-100, HIS-108 (owner 2026-11-07: no limit on how far back): after a dozen turns, Alice holds Undo and
    // drags the strip back to the game's first point (older history loads as it goes): going back there asks Bob; his
    // dialog shows the board it goes back to, he accepts, and the game is back in the opening on both phones
    @Test fun theUndoDialogGoesBackToTheGamesFirstPoint() {
        longGame(turns = 12)
        assertTrue(firstTurnHeld(alice) >= 3, "Alice's phone holds only the recent turns: from turn ${firstTurnHeld(alice)}")
        alice.hold("Undo")
        waitUntil("Alice's Undo dialog") { alice.shows(hasTestTag("undo-scroller")) }
        val game = alice.game!!
        fun points() = undoPoints(game.shown!!, game.timeline.events(game, names()))
        scrubToStart(alice, "undo-scroller") { game.shown!!.olderComplete && cursor(alice, "undo-scroller") == 0 }
        val point = points().first()
        assertTrue(point.event.parentId.let { p -> game.shown!!.older.any { it.id == p && start(it) } },
            "the first point is just after the game's first event: ${point.shown.description}")
        waitUntil("the first point chosen, asking Bob") {
            alice.showsText("Ask ${bob.name}") && runCatching { alice.node(hasTestTag("rewind-confirm")).assertIsEnabled() }.isSuccess
        }
        alice.node(hasTestTag("rewind-confirm")).performClick()
        waitUntil("Bob asked to accept the undo, with the board it goes back to", along = listOf(alice)) {
            bob.shows(hasTestTag("request-accept")) && bob.shows(hasTestTag("undo-board"))
        }
        assertEquals(point.target, bob.shown.request?.target, "the request goes back to the first point")
        assertTrue(bob.showsText("The game goes back to the opening.", substring = true), bob.screen())
        bob.node(hasTestTag("request-accept")).performClick()
        waitUntil("the server back at the first point") { serverGame()?.headId == point.target }
        waitUntil("both phones back in the opening") {
            listOf(alice, bob).all { it.game?.shown?.let { s -> s.tip == point.target && s.engine.turn.number == 0 } == true }
        }
    }
}
