package org.asyncttwiz.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import arcanum.api.TurnStep
import arcanum.engine.change.Change
import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TurnState
import org.asyncttwiz.protocol.game.EnginePart
import org.asyncttwiz.protocol.game.EngineStep
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TRN-074 (owner, 2026-11-08): the turn banner: when it shows, in order with the plays shown, and what it looks like. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TurnBannerTest {
    @get:Rule val compose = createComposeRule()
    private val game = TableFixture.game
    private val play = TableFixture.play(hand = 3, board = 2)
    private val names = mapOf("a" to "Alice", "b" to "Bob")
    private val a = PlayerId("a")
    private val b = PlayerId("b")

    private fun turn(number: Int, active: PlayerId) = TurnState(number, active, TurnStep.Untap)
    private fun newTurn(active: PlayerId = b) = Change.Step(turn(3, a), turn(4, active))
    private fun event(id: String, vararg changes: Change) = GameEvent(id, "p", "a", EnginePart(0, listOf(EngineStep(null, changes.toList()))))
    private fun transition(vararg events: GameEvent, serial: Long = 1, jump: Boolean = false, backward: List<GameEvent> = emptyList(),
        undone: EngineGame.Undone? = null) = EngineGame.Transition(serial, "tip", jump, emptyList(), emptySet(), events.toList(), backward, undone = undone)

    // TRN-074: going forward over a turn change names whose turn begins; the opening's first turn too; a step isn't one; a jump
    // (opening a game), a take-back and an undo have none
    @Test fun aTurnChangeGoingForwardMakesABanner() {
        assertEquals("b", TurnBanners.of(transition(event("e", newTurn())))?.player)
        assertEquals("a", TurnBanners.of(transition(event("e", newTurn()), event("f", Change.Step(turn(4, b), turn(5, a)))))?.player, "the newest")
        assertNull(TurnBanners.of(transition(event("e", Change.Step(turn(3, a), turn(3, a).copy(step = TurnStep.Upkeep))))))
        assertNull(TurnBanners.of(transition(event("e", newTurn()), jump = true)), "opening a game")
        assertNull(TurnBanners.of(transition(backward = listOf(event("e", newTurn())))), "a take-back")
        assertNull(TurnBanners.of(transition(event("e", newTurn()), undone = EngineGame.Undone("a", null, null))), "an undo")
    }

    private val entry = ObjectId.Card(CardId("c"), 0)
    private fun play(event: String) = GameDevice.PlayShow(event, b, entry, GameDevice.PlayKind.Triggered, CardId("c"))

    // TRN-074, HIS-112: in order with the plays shown: an upkeep trigger after the turn change waits for the banner, a play made
    // before it shows first, and a banner holds nothing: it ends by itself
    @Test fun itFollowsThePlaysInOrder() {
        val queue = TurnBannerQueue()
        // No play open: the next play waits for it
        queue.post(TurnBanners.of(transition(event("e", newTurn())))!!, null)
        assertTrue(queue.holdsPlays)
        queue.done(queue.current!!)
        assertFalse(queue.holdsPlays)
        // A play of an event before the turn change's: the banner after it
        queue.post(TurnBanners.of(transition(event("d"), event("e", newTurn()), serial = 2))!!, play("d"))
        assertEquals(true, queue.current?.afterPlays)
        assertFalse(queue.holdsPlays, "the play shows first")
        queue.started(queue.current!!)
        assertTrue(queue.holdsPlays, "showing now: the next play waits for it")
        queue.drop()
        assertNull(queue.current)
    }

    private fun render(queue: TurnBannerQueue, landscape: Boolean = false) {
        val board = engineTableBoard(play, "a")
        val toolkit = TableToolkitController("a", { board }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme {
            EngineFixtureTable(play, "a") {
                Box(Modifier.fillMaxSize()) {
                    TableSurface(game, board, "a", landscape, Modifier.fillMaxSize(), connected = true, onIntent = toolkit::handle, toolkit = toolkit)
                    TurnBannerOverlay(queue, false, "a", names, Modifier.align(Alignment.Center))
                }
            }
        } }
        compose.waitForIdle()
    }

    private val banner = hasTestTag(TURN_BANNER_TAG)

    // TRN-074: "Your turn" whole, then faded and gone in about 1.2 s
    @Test fun yourTurnShowsAndFades() {
        val queue = TurnBannerQueue().apply { post(TurnBanners.of(transition(event("e", newTurn(a))))!!, null) }
        compose.mainClock.autoAdvance = false
        render(queue)
        compose.onNode(banner).assertContentDescriptionEquals("Your turn")
        compose.onRoot().captureShot("$SCREENSHOTS/turn-banner/your-turn.png",
            "Portrait table just as Alice's turn begins: Your turn in her green at the screen's centre, over the board, on its way to fading out.")
        compose.mainClock.advanceTimeBy(TURN_BANNER_WHOLE_MILLIS + TURN_BANNER_FADE_MILLIS + 100L)
        compose.waitForIdle()
        compose.onNode(banner).assertDoesNotExist()
        assertNull(queue.current)
    }

    // TRN-074: the other player's name in their colour, in landscape
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun theirTurnInLandscape() {
        val queue = TurnBannerQueue().apply { post(TurnBanners.of(transition(event("e", newTurn())))!!, null) }
        compose.mainClock.autoAdvance = false
        render(queue, landscape = true)
        compose.onNode(banner).assertContentDescriptionEquals("Bob's turn")
        compose.onRoot().captureShot("$SCREENSHOTS/turn-banner/their-turn-landscape.png",
            "Landscape table just as Bob's turn begins: Bob's turn in his red at the screen's centre, on its way to fading out.")
    }

    // TRN-074: it takes no touches: a tap at the screen's centre reaches what lies under it
    @Test fun itTakesNoTouches() {
        var tapped = false
        val queue = TurnBannerQueue().apply { post(TurnBanners.of(transition(event("e", newTurn())))!!, null) }
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            Box(Modifier.fillMaxSize().clickable { tapped = true }) { TurnBannerOverlay(queue, false, "a", names, Modifier.align(Alignment.Center)) }
        } }
        compose.onNode(banner, useUnmergedTree = true).assertExists()
        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(50)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(50)
        assertTrue(tapped)
    }
}
