package org.asyncttwiz.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardAppearanceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() { context.getSharedPreferences("card-appearance", Context.MODE_PRIVATE).edit().clear().commit() }

    // TBL-034, TBL-035
    @Test fun defaultsPersistAcrossInstancesAndCustomizationStartsAtMaster() {
        val preferences = CardAppearancePreferences(context)
        for (area in CardArea.entries) assertEquals(1f, preferences.read().size(area))
        for (part in CardTextScalePart.entries) assertEquals(1f, preferences.read().text(part))
        assertFalse(preferences.read().customizeAreas)
        assertFalse(preferences.read().customizeText)
        preferences.setSize(1.4f); preferences.setText(1.2f)
        preferences.customizeAreas(true); preferences.customizeText(true)
        for (area in CardArea.entries) assertEquals(1.4f, preferences.read().size(area))
        for (part in CardTextScalePart.entries) assertEquals(1.2f, preferences.read().text(part))
        preferences.setSize(2f, CardArea.HAND); preferences.setText(.75f, CardTextScalePart.TITLE)
        val reopened = CardAppearancePreferences(context)
        assertEquals(2f, reopened.read().size(CardArea.HAND))
        assertEquals(1.4f, reopened.read().size(CardArea.STACK))
        assertEquals(.75f, reopened.read().text(CardTextScalePart.TITLE))
        reopened.customizeAreas(false); reopened.customizeText(false)
        for (area in CardArea.entries) assertEquals(1.4f, reopened.read().size(area))
        for (part in CardTextScalePart.entries) assertEquals(1.2f, reopened.read().text(part))
    }

    // TBL-036
    @Test fun changesNotifyOtherInstancesAndBoundsRejectNonfiniteInput() {
        val a = CardAppearancePreferences(context)
        val b = CardAppearancePreferences(context)
        var observed = b.read()
        val stop = b.observe { observed = b.read() }
        a.setSize(9f)
        assertEquals(3f, observed.cardSize)
        a.setText(-1f)
        assertEquals(.5f, b.read().textSize)
        a.setText(Float.NaN)
        assertEquals(1f, b.read().textSize)
        stop()
        for (text in listOf("", "NaN", "Infinity", "oops")) assertNull(parseCardScale(text), text)
        assertEquals(.5f, parseCardScale("-1"))
        assertEquals(.5f, parseCardScale("0.499"))
        assertEquals(3f, parseCardScale(" 3.001 "))
        assertEquals(1.234f, parseCardScale("1.234"))
        a.customizeAreas(true); a.customizeText(true)
        for (area in CardArea.entries) { a.setSize(-5f, area); assertEquals(.5f, b.read().size(area))
            a.setSize(9f, area); assertEquals(3f, b.read().size(area)) }
        for (part in CardTextScalePart.entries) { a.setText(-5f, part); assertEquals(.5f, b.read().text(part))
            a.setText(9f, part); assertEquals(3f, b.read().text(part)) }
    }

    // TBL-034
    @Test fun areaSizesAreIndependentOfZoomAndKeepHudRegionsSeparateAtBothExtremes() {
        val f = TableStatesFixture
        val initial = f.combat()
        val library = initial.board.containers.first { it.zone == PlayZone.LIBRARY && it.accountId == f.a }
        val play = initial.copy(board = initial.board.copy(contentsDisplays = listOf(
            ContentsDisplay("sample-panel", f.a, library.id, ContentsSelection.TOP, library.cards.size, false, library.cards))))
        for (landscape in listOf(false, true)) for (scale in listOf(.5f, 3f)) for (zoom in listOf(.65f, 2f)) {
            val width = if (landscape) 892f else 412f
            val height = if (landscape) 412f else 892f
            val appearance = CardAppearance(cardSize = scale, textSize = scale)
            val scene = TablePresentation.scene(f.game, tableBoard(play), f.a, width, height, landscape,
                TableLayoutState(camera = TableCamera(zoom = zoom)), appearance = appearance)
            assertEquals(28f * scale * zoom, scene.cardWidth, .001f)
            assertEquals(40f * scale * zoom, scene.cardHeight, .001f)
            assertTrue((scene.items.map { it.bounds } + scene.viewports.map { it.bounds }).all {
                it.width.isFinite() && it.height.isFinite() && it.width >= 0f && it.height >= 0f })
            assertEquals(scale, scene.appearance.text(CardTextScalePart.TITLE))
            // Landscape Temp piles are fitted into the 48dp counter row instead.
            for (temp in scene.items.filter { landscape && it.temp }) assertTrue(temp.bounds.height <= 44f, temp.id)
            for (item in scene.items.filter { it.kind == TableItemKind.CARD && it.zone == PlayZone.HAND ||
                    it.kind == TableItemKind.PILE && !(landscape && it.temp) }) {
                assertEquals(28f * scale, item.bounds.width, item.id)
                assertEquals(40f * scale, item.bounds.height, item.id)
                val port = scene.viewports.first { it.id == item.viewport }.bounds
                if (!landscape || item.zone == PlayZone.HAND) {
                    assertTrue(item.bounds.y >= port.y && item.bounds.bottom <= port.bottom, "${item.id}: ${item.bounds} / $port")
                } else {
                    // Tall landscape docks and Stack use the sidebar scroller, not overlapping rows.
                    assertTrue(item.visible.all { it.intersection(port) == it })
                    if (port.height < item.bounds.height) assertTrue(scene.viewports.single { it.id == "sidebar" }.maxScrollY > 0)
                }
            }
            val hands = scene.items.filter { it.kind == TableItemKind.HAND }
            for (panel in scene.cardPanels) {
                assertTrue(panel.bounds.right <= width)
                assertTrue(hands.all { it.bounds.intersection(panel.bounds) == null })
                for (row in panel.rows) {
                    assertEquals(28f * scale, row.bounds.width)
                    assertEquals(40f * scale, row.bounds.height)
                }
            }
            val stack = scene.items.single { it.kind == TableItemKind.PILE && it.zone == PlayZone.STACK }
            assertTrue(hands.all { hand -> stack.visible.all { it.intersection(hand.bounds) == null } },
                "Visible Stack fragments must not cover a hand; off-screen sidebar content is clipped")
            if (landscape) {
                val sidebar = scene.viewports.single { it.id == "sidebar" }
                val bottom = TablePresentation.scene(f.game, tableBoard(play), f.a, width, height, true,
                    TableLayoutState(scrollY = mapOf("sidebar" to sidebar.maxScrollY)), appearance = appearance)
                for (item in bottom.items.filter { it.kind == TableItemKind.PILE && it.owner == f.a && it.zone != PlayZone.STACK }) {
                    val port = bottom.viewports.single { it.id == item.viewport }.bounds
                    assertTrue(item.bounds.y >= port.y && item.bounds.bottom <= port.bottom,
                        "Own piles must become fully visible when the sidebar is scrolled: ${item.bounds} / $port")
                }
            }
        }
    }

}
