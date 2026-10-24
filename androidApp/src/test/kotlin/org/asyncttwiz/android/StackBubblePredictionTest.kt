package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1080dp-h2340dp-port-mdpi")
class StackBubblePredictionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deviceCardDropShowsBubblesBeforeAcceptanceAndRetainsFocusAcrossLaggingRenderedBoundary() = drop(false)
    @Test fun deviceProjectionDropShowsBubblesBeforeAcceptanceAndRetainsFocusAcrossLaggingRenderedBoundary() = drop(true)

    private fun drop(projection: Boolean) {
        val f = BasicTableFixture()
        f.connect()
        try {
            val ui = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay)
            val destination = if (projection) TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 0)
                else TableDestination.Container(f.container(PlayZone.HAND), PlayZone.HAND)
            assertTrue(f.move(40, destination, true))
            if (projection) assertTrue(ui.declare(listOf(f.id(40)), tap = false))
            val setupCount = f.commands().size
            var setupState = f.root
            f.commands().forEachIndexed { index, command ->
                val setup = f.update(setupState, command, index + 1)
                setupState = setup.play!!
                f.message(setup); f.message(f.result(setup))
            }
            val before = f.play
            var presented by mutableStateOf(before)
            val density = 2.625f
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    MaterialTheme {
                        TableSurface(f.game, presented, f.actor, false, Modifier.fillMaxSize(), connected = true,
                            onIntent = { intent -> ui.handle(intent).also { presented = f.play } }, toolkit = ui)
                    }
                }
            }
            val scene = TablePresentation.scene(f.game, before, f.actor, 1080f / density, 2340f / density, false)
            val viewId = if (projection) CombatStackRules.occurrences(before.board.combat).single().id else f.id(40)
            val start = scene.items.single { it.cardId == viewId }.bounds.center
            val pile = scene.items.single { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }
            compose.onNodeWithContentDescription("Shared table").performTouchInput {
                down(Offset(start.x * density, start.y * density))
                moveTo(Offset(pile.bounds.center.x * density, pile.bounds.center.y * density), 80)
                up()
            }
            compose.onNodeWithContentDescription("Mark text · drag up or down").assertExists()
            val entry = f.play.board.stackEffects.single().id
            compose.runOnIdle {
                assertEquals(entry, ui.stackBubbleEntry)
                assertEquals(before, f.lobby.state.acceptedPlay)
            }
            // Deliver the real accepted replacement, while the history index still renders the old
            // boundary for a frame. This must not clear the prediction's personal bubble focus.
            compose.runOnIdle {
                var accepted = before
                f.commands().drop(setupCount).forEachIndexed { index, command ->
                    val revision = f.update(accepted, command, index + setupCount + 1)
                    accepted = revision.play!!
                    f.message(f.result(revision)); f.message(revision)
                }
                presented = before
            }
            compose.runOnIdle { assertEquals(entry, ui.stackBubbleEntry); presented = f.play }
            compose.onNodeWithContentDescription("Mark text · drag up or down").assertExists()
            compose.runOnIdle {
                assertEquals(entry, ui.stackBubbleEntry)
                assertEquals(f.play.board, f.lobby.state.acceptedPlay!!.board)
            }
        } finally { f.lobby.close(); f.worker.drain() }
    }
}
