package org.asyncttwiz.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The desktop table's battlefield card size and zoom limits (WEB-084, TBL-010, TBL-121, TBL-012). */
class DesktopZoomTest {
    private val windows = listOf(1440f to 900f, 1920f to 1080f)

    /** The desktop table in a [width] × [height] window (the surface under its top row), Alice and Bob with three cards
     * each on their battlefields and Alice seven in hand, untouched ([camera] null: the opening view) or at [camera]. */
    private fun desktop(width: Float, height: Float, camera: TableCamera? = null): TableScene {
        val board = tableBoard(TableFixture.play(board = 3, hand = 7))
        return TablePresentation.scene(TableFixture.game, board, "a", width, height - DesktopTable.topRow(width), true,
            TableLayoutState(camera ?: TableCamera()), appearance = DesktopCardAppearance, landscapeHome = true,
            handRow = HandRow.cards(board, "a"), desktop = DesktopFrame())
    }

    private fun assertRowsInView(scene: TableScene, what: String) {
        val viewport = scene.world!!.viewport
        assertEquals(2, scene.fields.size)
        for (field in scene.fields) assertTrue(field.gridBounds.y >= viewport.y - .5f && field.gridBounds.bottom <= viewport.bottom + .5f,
            "$what: all three rows of ${field.owner}'s battlefield in view: ${field.gridBounds} in $viewport")
    }

    // WEB-084, WEB-022 (owner 2026-11-24): the desktop layout draws battlefield cards 4× the phone's with the Card size slider at
    // 1×, the phone's staying 1×
    @Test fun desktopBattlefieldCardsAreFourTimesThePhones() {
        assertEquals(1f, DesktopCardAppearance.cardSize)
        assertEquals(4f, DesktopCardAppearance.battlefield)
        assertEquals(1f, CardAppearance().battlefield)
        val scene = desktop(1440f, 900f)
        assertEquals(4f * 28f, scene.cardWidth / scene.camera.zoom, .5f)
    }

    // TBL-010, WEB-084, TBL-121 (owner 2026-11-24): the desktop table's least zoom is half the phone's, so its 4× battlefield
    // cards' six rows fit a 1440 × 900 and a 1920 × 1080 window: the table opens zoomed out just enough to show them all
    // (above the phone's 0.65× least zoom they wouldn't fit), their column 0 right of both seat headers
    @Test fun theDesktopTableOpensWithAllSixRowsInView() {
        assertEquals(.325f, DesktopTable.MIN_ZOOM, .0001f)
        for ((width, height) in windows) {
            val opened = desktop(width, height)
            val world = opened.world!!
            assertEquals(DesktopTable.MIN_ZOOM, world.minZoom)
            assertTrue(opened.camera.zoom > DesktopTable.MIN_ZOOM && opened.camera.zoom < TablePresentation.MIN_ZOOM,
                "${width}x$height: opened below the phone's least zoom, above the desktop's: ${opened.camera}")
            assertRowsInView(opened, "${width}x$height opening")
            assertTrue(opened.fields.all { kotlin.math.abs(it.originX - world.leftInset - world.viewport.x) < 1f },
                "column 0 right of the seat headers: ${opened.fields.map { it.originX }}, inset ${world.leftInset}")
        }
    }

    // TBL-012, TBL-010, WEB-084: Fit table shows all six rows; zoomed out as far as it goes (a pinch or the wheel) the rows fit too
    @Test fun fitAndTheLeastZoomShowAllSixRows() {
        for ((width, height) in windows) {
            val zoomedIn = desktop(width, height, TableCamera(zoom = 2f))
            val fit = zoomedIn.world!!.fit()
            assertTrue(fit.zoom < TablePresentation.MIN_ZOOM, "${width}x$height: $fit")
            assertRowsInView(desktop(width, height, fit), "${width}x$height fitted")
            val events = mutableListOf<TableIntent>()
            val gestures = TableGestureCoordinator({ events += it })
            gestures.update(zoomedIn, true)
            gestures.zoomAt(zoomedIn.world!!.viewport.center, .01f)
            val least = events.filterIsInstance<TableIntent.Camera>().last().camera
            assertEquals(DesktopTable.MIN_ZOOM, least.zoom, .0001f, "${width}x$height: zoomed out to the desktop's least zoom")
            assertRowsInView(desktop(width, height, least), "${width}x$height at the least zoom")
            events.clear()
            gestures.zoomAt(zoomedIn.world!!.viewport.center, 100f)
            assertEquals(TablePresentation.MAX_ZOOM, events.filterIsInstance<TableIntent.Camera>().last().camera.zoom, .0001f,
                "${width}x$height: zoomed in to 2×")
        }
    }

    // TBL-010: the phone table's least zoom stays 0.65×
    @Test fun thePhonesLeastZoomStays() {
        for (landscape in listOf(false, true)) {
            val scene = TableFixture.scene(landscape = landscape)
            assertEquals(.65f, scene.world!!.minZoom)
            assertEquals(.65f, scene.world!!.normalize(TableCamera(zoom = .1f)).zoom)
        }
    }
}
