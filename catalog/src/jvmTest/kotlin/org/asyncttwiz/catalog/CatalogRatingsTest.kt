package org.asyncttwiz.catalog

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The draft bots' card ratings (DRF-312): counted from game data, kept in a pinned local file, baked into the catalog. */
class CatalogRatingsTest {
    private val fixtures = File(System.getProperty("catalog.fixture") ?: "../tests/fixtures/card-catalog")
    private fun lines(name: String) = File(fixtures, name).readLines().filter { it.isNotBlank() && !it.startsWith("#") }

    // DRF-312: a card's games in hand are the games it was in the opening hand or drawn (not only in the deck), and its wins those won
    @Test fun `game data counts each card's games in hand and their wins`() {
        val csv = """
            |expansion,event_type,won,"opening_hand_Ajax, Pale Rider","drawn_Ajax, Pale Rider","deck_Ajax, Pale Rider",opening_hand_Test Bear,drawn_Test Bear
            |TST,PremierDraft,True,1,0,1,0,0
            |TST,PremierDraft,False,0,1,1,0,2
            |TST,PremierDraft,True,0,0,1,1,0
            |TST,PremierDraft,True,0,0,0,0,1
            |""".trimMargin()
        val counts = CatalogRatings.gameData(csv.byteInputStream())
        assertEquals(CatalogRatings.Count(2, 1), counts["Ajax, Pale Rider"], "in the deck but never in hand doesn't count")
        assertEquals(CatalogRatings.Count(3, 2), counts["Test Bear"])
        // GIH WR in tenths of a percent, only from enough games
        assertEquals(emptyMap(), CatalogRatings.table(counts))
        assertEquals(mapOf("A" to 667), CatalogRatings.table(mapOf("A" to CatalogRatings.Count(300, 200), "B" to CatalogRatings.Count(199, 150))))
    }

    // DRF-312: the ratings file keeps its sets' counts, when each was updated and the sets without data
    @Test fun `the ratings file reads back what was written`() {
        val directory = createTempDirectory("ratings").toFile()
        try {
            val ratings = CatalogRatings.Ratings(mapOf("TST" to mapOf("Test Bear" to CatalogRatings.Count(1000, 540))),
                mapOf("TST" to "2026-11-01"), listOf("TWL"))
            val file = File(directory, "ratings.tsv.gz")
            CatalogRatings.write(ratings, file)
            assertEquals(ratings, CatalogRatings.read(file))
            val again = File(directory, "again.tsv.gz").also { CatalogRatings.write(ratings, it) }
            assertEquals(CardCatalogFiles.sha256(file), CardCatalogFiles.sha256(again), "the same data gives the same bytes")
        } finally {
            directory.deleteRecursively()
        }
    }

    // DRF-312: the catalog bakes each set's ratings under its own card names; too few games and unknown names are left out
    @Test fun `the catalog build bakes the ratings into their sets`() {
        val ratings = CatalogRatings.read(File(fixtures, "ratings.tsv"))
        val catalog = CatalogBuilder(lines("supported-sets.txt").map { it.substringBefore(' ') }.toSet(), lines("supported-cards.txt").toSet(),
            ratings.sets.mapValues { CatalogRatings.table(it.value) }).build { File(fixtures, "AllPrintings.json").inputStream() }.catalog
        assertEquals(mapOf("Test Bear" to 540, "Test Bolt" to 600, "Test Caller" to 500, "Test Knight" to 580), catalog.set.getValue("TST").ratings)
        assertEquals(emptyMap(), catalog.set.getValue("TWL").ratings)
        val line = CardCatalogFormat.encode(catalog.set.getValue("TST"))
        assertEquals(catalog.set.getValue("TST"), CardCatalogFormat.decode(line))
        assertFalse("ratings" in CardCatalogFormat.encode(catalog.set.getValue("TWL")), "a set without ratings writes none")
    }

    // DRF-312: the release builds with the pinned ratings only; ratings-move pins a file and keeps the MTGJSON pin
    @Test fun `the catalog command checks the ratings against the pin`() {
        val directory = createTempDirectory("ratings-pin").toFile()
        try {
            val allPrintings = File(fixtures, "AllPrintings.json")
            val ratings = File(directory, "ratings.tsv.gz").also { CatalogRatings.write(CatalogRatings.read(File(fixtures, "ratings.tsv")), it) }
            val pin = File(directory, "pin.properties")
            fun run(vararg args: String): Pair<Int, String> {
                val out = ByteArrayOutputStream()
                val code = CatalogCommand(PrintStream(out, true), directory).run(args.toList() + listOf("--pin", pin.path,
                    "--file", allPrintings.path, "--sets", File(fixtures, "supported-sets.txt").path,
                    "--cards", File(fixtures, "supported-cards.txt").path, "--ratings", ratings.path))
                return code to out.toString()
            }
            CatalogPin("5.3.0+20261031", "2026-10-31", CardCatalogFiles.sha256(allPrintings)).write(pin)
            assertEquals(0, run("ratings-move").first)
            val pinned = CatalogPin.read(pin)
            assertEquals(CardCatalogFiles.sha256(ratings), pinned.ratingsSha256)
            assertEquals(CardCatalogFiles.sha256(allPrintings), pinned.allPrintingsSha256)
            val (verified, said) = run("verify")
            assertEquals(0, verified, said)
            assertTrue("is the pinned card ratings file" in said, said)
            val (built, log) = run("build", "--out", File(directory, "out").path)
            assertEquals(0, built, log)
            assertTrue("Card ratings: TST 4" in log, log)
            assertTrue("no 17Lands data for TWL" in log, log)

            pinned.copy(ratingsSha256 = "2".repeat(64)).write(pin)
            val (refused, message) = run("build", "--out", File(directory, "out2").path)
            assertEquals(1, refused)
            assertTrue("isn't the pinned card ratings file" in message && "scripts/catalog-pin.sh ratings-move" in message, message)
            assertFalse(File(directory, "out2").exists(), "nothing built from other ratings")
            // Moving the MTGJSON pin keeps the ratings pin
            assertEquals(0, run("move").first)
            assertEquals("2".repeat(64), CatalogPin.read(pin).ratingsSha256)
        } finally {
            directory.deleteRecursively()
        }
    }
}
