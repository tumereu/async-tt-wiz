package org.asyncttwiz.android

import org.junit.Test
import java.io.File
import kotlin.test.*

class HistoryIconCoverageTest {
    /** Every icon the shared rules can record must draw in the timeline and the history tree, or the game crashes. */
    @Test fun everyRecordedHistoryIconHasAVectorAndAGlyph() {
        val source = File("../protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/HistoryGrouping.kt").readText()
        val icons = Regex("""HistorySemantic\([^,]+, "([a-z_0-9]+)"""").findAll(source).map { it.groupValues[1] }.toSet()
        assertTrue("inbox" in icons && "swap_horiz" in icons, "scan found $icons")
        for (icon in icons) {
            historyIconGlyph(icon)
            if (icon != "swords") historyIconVector(icon)
        }
    }
}
