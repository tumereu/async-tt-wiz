package org.asyncttwiz.android

import kotlin.test.Test
import kotlin.test.assertEquals

/** SYS-150 (owner, 2026-11-17: "No autopan"): opening the cast panel never pans the table; the board stays where you left it. */
class CastPanelNoPanTest {
    // SYS-150, STK-156: the cast panel's payment outlines its sources and lays them in a pile of their own; the camera and the
    // battlefields stay exactly where they were, an untouched camera in landscape (TBL-121) and a panned one alike
    @Test fun thePaymentsSourcesMoveNoCamera() {
        val play = TableFixture.play(board = 5, hand = 2)
        val paying = play.board.cards.filter { it.containerId == TableFixture.container("a", org.asyncttwiz.protocol.PlayZone.BATTLEFIELD) }
            .take(2).map { it.id }.toSet()
        for (landscape in listOf(false, true)) for (camera in listOf(TableCamera(), TableCamera(30f, -40f, 1.2f))) {
            val before = TableFixture.scene(play, landscape, TableLayoutState(camera = camera))
            val after = TableFixture.scene(play, landscape, TableLayoutState(camera = camera, paying = paying))
            assertEquals(before.camera, after.camera, "landscape=$landscape camera=$camera")
            assertEquals(before.fields.map { it.bounds }, after.fields.map { it.bounds }, "landscape=$landscape camera=$camera")
        }
    }

    // SYS-150: a panel opening over the table's bottom (the cast panel, its target bar) only lets the table pan further; it
    // pans nothing by itself, and closing it neither
    @Test fun aPanelOpeningOrClosingPansNothing() {
        val world = TableWorld(TableBounds(0f, 0f, 400f, 400f), TableBounds(0f, 0f, 400f, 800f))
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        var panel = 0f
        gestures.panelInset = { panel }
        gestures.update(TableScene(emptyList(), emptyList(), world = world, camera = world.clamp(TableCamera(0f, 120f))), true)
        panel = 180f
        gestures.tick(0); gestures.tick(1000)
        panel = 0f
        gestures.tick(2000); gestures.tick(3000)
        assertEquals(emptyList(), events.filterIsInstance<TableIntent.Camera>())
    }
}
