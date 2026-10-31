package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CardInspectionUiTest {
    @get:Rule val compose = createComposeRule()

    // TBL-104
    @Test fun tableHoldScrollsToBottomAndBackWithoutRelease() = tableHold(false)

    // TBL-104
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeTableHoldScrollsInfoWithoutRelease() = tableHold(true)

    /** A resting finger still reports tiny moves every frame. Each one restarts the hold clock outside
     * composition; the peek must still open once the hold time has passed since the press. */
    @Test fun jitteringHoldStillPeeksAtTheHoldDeadline() {
        val f = TableStatesFixture
        val play = f.combat()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme {
            TableSurface(f.game, play, f.a, false, Modifier.fillMaxSize(), connected = true,
                toolkit = toolkit, onIntent = toolkit::handle)
        } }
        val scene = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false)
        val card = scene.items.first { it.owner == f.a && it.kind == TableItemKind.CARD && it.zone == PlayZone.BATTLEFIELD && it.visible.isNotEmpty() }
        val point = card.visible.first().center
        val table = compose.onNodeWithContentDescription("Shared table")
        compose.mainClock.autoAdvance = false
        table.performTouchInput { down(Offset(point.x, point.y)) }
        val pressed = compose.mainClock.currentTime
        var sign = 1f
        // Each injected move's delay also advances the paused main clock.
        fun jitter(until: Long) {
            while (compose.mainClock.currentTime - pressed < until) {
                table.performTouchInput { moveBy(Offset(.5f * sign, 0f), delayMillis = 16) }
                sign = -sign
            }
        }
        jitter(CardPeekHoldMillis - 64)
        compose.runOnIdle { assertNull(toolkit.peek) }
        jitter(CardPeekHoldMillis + 160)
        compose.runOnIdle { assertNotNull(toolkit.peek) }
        table.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertNull(toolkit.peek) }
    }

    private fun tableHold(landscape: Boolean) {
        val f = TableStatesFixture
        val play = f.combat().let { p -> p.copy(definitions = p.definitions.map { definition ->
            definition.copy(definition = definition.definition.copy(faces = definition.definition.faces.map { face ->
                face.copy(manaCost = "{2}{W/U}{W/U/P}", rulesText = (1..30).joinToString("\n") { "Ability $it: {T}: Add {W}." })
            }))
        }) }
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { action, _ -> sent += action; true })
        compose.setContent { MaterialTheme {
            TableSurface(f.game, play, f.a, landscape, Modifier.fillMaxSize(), connected = true,
                toolkit = toolkit, onIntent = toolkit::handle)
        } }
        val scene = TablePresentation.scene(f.game, play, f.a, if (landscape) 892f else 412f, if (landscape) 412f else 892f, landscape)
        val card = scene.items.first { it.owner == f.a && it.kind == TableItemKind.CARD && it.zone == PlayZone.BATTLEFIELD && it.visible.isNotEmpty() }
        val point = card.visible.first().center
        val table = compose.onNodeWithContentDescription("Shared table")
        compose.mainClock.autoAdvance = false
        table.performTouchInput { down(Offset(point.x, point.y)) }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Enlarge artwork").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close card inspection").assertDoesNotExist()
        compose.onNodeWithText("{2}{W/U}{W/U/P}").assertExists() // Original tokens remain accessible.
        val content = compose.onNodeWithContentDescription("Card inspection content")
        fun scroll() = content.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(0f, scroll().value())
        table.performTouchInput { moveBy(Offset(0f, -10_000f)) }
        compose.waitForIdle()
        assertTrue(scroll().value() > 0f)
        assertEquals(scroll().maxValue(), scroll().value())
        compose.onNodeWithText("Ability 30: {T}: Add {W}.").assertIsDisplayed()
        table.performTouchInput { moveBy(Offset(0f, 40f)) }
        compose.waitForIdle()
        assertTrue(scroll().value() < scroll().maxValue(), "Reverse immediately even after overscrolling")
        table.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        content.assertDoesNotExist()
        compose.runOnIdle { assertNull(toolkit.peek); assertNull(toolkit.inspection) }
        assertTrue(sent.isEmpty())
    }

    // TBL-105
    @Test fun holdingAFaceUpCardInTimelineModePeeksAndPausesPlaybackWhichReleaseDoesNotResume() {
        val f = TableStatesFixture
        val play = f.combat()
        val playback = LiveTableFixture.playback()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme {
            // The table screen's own hook: an inspection pauses the timeline.
            TableSurface(f.game, play, f.a, false, Modifier.fillMaxSize(), connected = false, toolkit = toolkit,
                onIntent = toolkit::handle, runtime = TableRuntimeHooks(reviewing = true, pauseForInspection = { playback.pause() }))
        } }
        val scene = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false)
        val card = scene.items.first { it.owner == f.a && it.kind == TableItemKind.CARD && it.zone == PlayZone.BATTLEFIELD &&
            !it.covered && it.visible.isNotEmpty() }
        val point = card.visible.first().center
        val table = compose.onNodeWithContentDescription("Shared table")
        compose.runOnIdle { playback.play() }
        val cursor = playback.position.cursorId
        assertEquals(HistoryMode.PLAYBACK, playback.position.mode)
        assertTrue(playback.position.playing)
        // Playing, then already paused: the hold preview works either way and leaves the timeline paused.
        repeat(2) { round ->
            compose.mainClock.autoAdvance = false
            table.performTouchInput { down(Offset(point.x, point.y)) }
            compose.waitForIdle(); compose.mainClock.advanceTimeBy(CardPeekHoldMillis + 250)
            compose.runOnIdle {
                assertEquals(card.cardId, toolkit.peek?.cardId, "round $round")
                assertFalse(playback.position.playing, "round $round")
            }
            table.performTouchInput { up() }
            compose.mainClock.autoAdvance = true
            compose.runOnIdle {
                assertNull(toolkit.peek)
                assertFalse(playback.position.playing, "release does not resume, round $round")
                assertEquals(HistoryMode.PLAYBACK, playback.position.mode)
                assertEquals(cursor, playback.position.cursorId)
            }
        }
    }

    @Test fun explicitInspectionRetainsTitleAndArtworkControls() {
        val controller = CardInspectionController().apply {
            open(InspectedCard(CardDefinition("Inspector header", listOf(DefinitionFace("face", "Face name",
                 manaCost = "{0}{G}", rulesText = "{Q}: Add {S}{E}. Unknown {FOO}.", power = "2", toughness = "3"))), listOf(null)))
        }
        compose.setContent { MaterialTheme { CardInspection(controller) } }
        compose.onNodeWithText("Inspector header").assertExists()
        compose.onNodeWithText("Enlarge artwork").assertExists()
        compose.onNodeWithText("{Q}: Add {S}{E}. Unknown {FOO}.").assertExists()
        compose.onNodeWithText("2/3").assertExists()
        compose.onNodeWithText("Power / Toughness", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Close card inspection").performClick()
        compose.runOnIdle { assertNull(controller.state) }
    }
}
