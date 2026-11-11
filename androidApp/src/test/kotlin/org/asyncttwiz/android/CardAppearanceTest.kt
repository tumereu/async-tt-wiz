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
import org.asyncttwiz.appui.AppPlatform

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardAppearanceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() { context.getSharedPreferences("card-appearance", Context.MODE_PRIVATE).edit().clear().commit() }

    // TBL-034, TBL-035
    @Test fun defaultsPersistAcrossInstancesAndCustomizationStartsAtMaster() {
        val preferences = CardAppearancePreferences(AppPlatform.current)
        for (area in CardArea.entries) assertEquals(1f, preferences.read().size(area))
        for (part in CardTextScalePart.entries) assertEquals(1f, preferences.read().text(part))
        assertFalse(preferences.read().customizeAreas)
        assertFalse(preferences.read().customizeText)
        preferences.setSize(1.4f); preferences.setText(1.2f)
        preferences.customizeAreas(true); preferences.customizeText(true)
        for (area in CardArea.entries) assertEquals(1.4f, preferences.read().size(area))
        for (part in CardTextScalePart.entries) assertEquals(1.2f, preferences.read().text(part))
        preferences.setSize(2f, CardArea.HAND); preferences.setText(.75f, CardTextScalePart.TITLE)
        val reopened = CardAppearancePreferences(AppPlatform.current)
        assertEquals(2f, reopened.read().size(CardArea.HAND))
        assertEquals(1.4f, reopened.read().size(CardArea.STACK))
        assertEquals(.75f, reopened.read().text(CardTextScalePart.TITLE))
        reopened.customizeAreas(false); reopened.customizeText(false)
        for (area in CardArea.entries) assertEquals(1.4f, reopened.read().size(area))
        for (part in CardTextScalePart.entries) assertEquals(1.2f, reopened.read().text(part))
    }

    // TBL-036
    @Test fun changesNotifyOtherInstancesAndBoundsRejectNonfiniteInput() {
        val a = CardAppearancePreferences(AppPlatform.current)
        val b = CardAppearancePreferences(AppPlatform.current)
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
                // TBL-009: in landscape the piles fit their bar row, at most 32 dp tall, and the Stack the room between the hands
                val barPile = landscape && item.zone != PlayZone.HAND && item.zone != PlayZone.STACK
                val pileScale = if (barPile) minOf(scale, .8f) else scale
                val fan = item.cardTransform
                if (fan != null) {
                    // ZON-001: the other player's hand in portrait is a fan in their row, its cards the piles' size times FAN_CARD
                    assertTrue(!landscape && item.owner != f.a, item.id)
                    assertEquals(28f * scale * TablePresentation.FAN_CARD, fan.width, .001f, item.id)
                    assertEquals(40f * scale * TablePresentation.FAN_CARD, fan.height, .001f, item.id)
                } else if (landscape && item.zone == PlayZone.STACK) {
                    assertTrue(item.bounds.height <= 40f * scale + .001f && item.bounds.height + 24f <= scene.world!!.viewport.height + .001f, item.id)
                    assertEquals(item.bounds.height * .7f, item.bounds.width, .001f, item.id)
                } else {
                    assertEquals(28f * pileScale, item.bounds.width, .001f, item.id)
                    assertEquals(40f * pileScale, item.bounds.height, .001f, item.id)
                }
                val port = scene.viewports.first { it.id == item.viewport }.bounds
                assertTrue(item.bounds.y >= port.y && item.bounds.bottom <= port.bottom, "${item.id}: ${item.bounds} / $port")
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
                "Visible Stack fragments must not cover a hand")
        }
    }

}
