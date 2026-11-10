package org.asyncttwiz.android

import arcanum.engine.decision.Decision
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Automatum screenshot gallery's list (owner, 2026-11-06): `src/test/screenshots/gallery.tsv` names one
 * representative screenshot per Automatum UI item (scripts/aa-gallery.sh builds the page from it). Each listed
 * screenshot and its caption must be there, each catalog ID it names must be in docs/features, and every engine decision
 * kind the app shows must have an item. Items meant to be seen as a sequence (owner, 2026-11-10) share a name in the
 * optional sixth column and follow one another; the gallery shows a sequence whole. Numbers increase and are never
 * reused, so they stay with their pictures.
 */
class AutomatumGalleryTest {
    private class Item(val number: Int, val name: String, val screenshot: String, val ids: List<String>, val decisions: List<String>,
        val sequence: String)

    private val items: List<Item> by lazy {
        val lines = File("$SCREENSHOTS/gallery.tsv").readLines().filter { it.isNotBlank() }
        assertEquals("number\titem\tscreenshot\tcatalog\tdecisions\tsequence", lines.first(), "gallery.tsv's header")
        lines.drop(1).map { line ->
            val cells = line.split('\t')
            assertTrue(cells.size in 4..6, "four to six tab-separated cells (the decisions and sequence may be left out): $line")
            Item(cells[0].toInt(), cells[1], cells[2], cells[3].split(' ').filter(String::isNotBlank),
                cells.getOrElse(4) { "" }.split(' ', ',').filter(String::isNotBlank), cells.getOrElse(5) { "" }.trim())
        }
    }

    @Test fun aSequencesItemsFollowOneAnother() {
        val positions = items.withIndex().filter { it.value.sequence.isNotEmpty() }.groupBy({ it.value.sequence }, { it.index })
        val broken = positions.filter { (_, at) -> at != (at.first()..at.last()).toList() }.keys
        assertTrue(broken.isEmpty(), "a sequence's items must follow one another in gallery.tsv: $broken")
    }

    /** The decision kinds the devices answer themselves, never shown to a player. */
    private val answeredByDevices = setOf("Random", "HostFill", "OtherView")

    @Test fun everyListedScreenshotIsThere() {
        // Numbers stay with their pictures (owner, 2026-11-10): increasing and never reused, so an item taken out leaves a gap
        assertEquals(items.map { it.number }.sorted().distinct(), items.map { it.number }, "item numbers increase down the list, each once")
        assertEquals(items.size, items.map { it.name }.toSet().size, "item names are unique")
        val missing = items.filter { !File("$SCREENSHOTS/${it.screenshot}").isFile || !File("$SCREENSHOTS/${it.screenshot.removeSuffix(".png")}.txt").isFile }
        assertTrue(missing.isEmpty(), "gallery.tsv lists screenshots (or captions) that aren't there: ${missing.map { it.screenshot }}")
    }

    @Test fun everyCatalogIdIsInTheCatalog() {
        val defined = File("../docs/features").listFiles { f -> f.name.endsWith(".md") }!!.flatMap { file ->
            file.readLines().mapNotNull { Regex("""^- \*\*([A-Z]{3}-\d{3})\*\*""").find(it)?.groupValues?.get(1) }
        }.toSet()
        val unknown = items.flatMap { item -> item.ids.filter { it !in defined }.map { "${item.number} ${item.name}: $it" } }
        assertTrue(unknown.isEmpty(), "gallery.tsv names catalog IDs that aren't in docs/features: $unknown")
        assertTrue(items.all { it.ids.isNotEmpty() }, "every item names its catalog entries")
    }

    @Test fun everyDecisionKindTheAppShowsHasAnItem() {
        val kinds = Decision::class.java.declaredClasses.filter { Decision::class.java.isAssignableFrom(it) && !it.isInterface }
            .map { it.simpleName }.toSet()
        assertTrue(kinds.size > 10 && answeredByDevices.all { it in kinds }, "decision kinds found: $kinds")
        val listed = items.flatMap { it.decisions }.toSet()
        assertTrue((listed - kinds).isEmpty(), "gallery.tsv names decision kinds the engine doesn't have: ${listed - kinds}")
        val uncovered = kinds - answeredByDevices - listed
        assertTrue(uncovered.isEmpty(), "decision kinds the app shows without a gallery item (add one to gallery.tsv): $uncovered")
    }
}
