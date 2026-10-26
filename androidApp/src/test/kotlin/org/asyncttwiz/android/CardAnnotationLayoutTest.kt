package org.asyncttwiz.android

import org.junit.Test
import kotlin.test.*

class CardAnnotationLayoutTest {
    @Test fun fullHeightLinesAndEveryCounterStayInsideFaceAtAllTextExtremes() {
        for (title in listOf(.75f, 1f, 2f)) for (note in listOf(.75f, 1f, 2f))
            for (value in listOf(.75f, 1f, 2f)) for (count in 0..6) for (hasNote in listOf(false, true)) {
                val noteHeight = 8.3f * note + 2f
                val counterHeight = maxOf(10f * value, 8.3f * value + 2f)
                val layout = cardAnnotationLayout(maxOf(11f, 7f * title + 3f),
                    40f - maxOf(10f, 7f * value + 3f), noteHeight, counterHeight, hasNote, count)
                assertEquals(count, layout.counters.size)
                assertEquals(hasNote, layout.note != null)
                layout.note?.let { assertEquals(noteHeight, it.height) }
                layout.counters.forEach { assertEquals(counterHeight, it.height) }
                val slots = listOfNotNull(layout.note) + layout.counters
                for ((index, slot) in slots.withIndex()) {
                    assertTrue(slot.width > 0f && slot.x >= 1f && slot.right <= 27f)
                    assertTrue(slot.y >= 0f && slot.bottom <= 40f)
                    for (other in slots.drop(index + 1)) assertNull(slot.intersection(other), "$slot / $other")
                }
            }
    }

    @Test fun moreThanTwoCountersUseExactlyTwoRows() {
        for (count in 1..6) {
            val counters = cardAnnotationLayout(11f, 30f, 10f, 10f, false, count).counters
            assertEquals(if (count > 2) 2 else 1, counters.map { it.y }.distinct().size)
            if (count > 2) assertEquals((count + 1) / 2, counters.count { it.y == counters.first().y })
        }
    }
}
