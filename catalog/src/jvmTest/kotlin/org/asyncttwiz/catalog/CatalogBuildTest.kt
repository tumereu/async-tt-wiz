package org.asyncttwiz.catalog

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.DeckValidation
import org.asyncttwiz.protocol.SetCardRarity

/** The card catalog built from the invented test cards (tests/fixtures/card-catalog), docs/specs/web-client.md section 3. */
class CatalogBuildTest {
    private val fixtures = File(System.getProperty("catalog.fixture") ?: "../tests/fixtures/card-catalog")
    private val allPrintings = File(fixtures, "AllPrintings.json")

    private fun lines(name: String) = File(fixtures, name).readLines().filter { it.isNotBlank() && !it.startsWith("#") }

    private fun build(cards: Set<String> = lines("supported-cards.txt").toSet()): CatalogBuilder.Built =
        CatalogBuilder(lines("supported-sets.txt").map { it.substringBefore(' ') }.toSet(), cards).build { allPrintings.inputStream() }

    private val catalog by lazy { build().catalog }

    private fun face(name: String) = catalog.faces.single { it.name == name }

    // SYS-081
    @Test fun `the catalog holds every English printing, each face once, the set list and the draftable sets' recipes`() {
        assertEquals("5.3.0+20261031", catalog.header.mtgjsonVersion)
        assertEquals(listOf("BAD", "BON", "DCK", "OFF", "TST", "TWL"), catalog.sets.map { it.code })
        // Test Bear is printed in TST and OFF: one face, two printings; the Japanese printing is left out
        val bear = face("Test Bear")
        assertEquals(setOf("TST", "OFF"), catalog.printings.filter { catalog.faces[it.face] == bear }.map { it.set }.toSet())
        assertEquals(1, catalog.printings.count { catalog.faces[it.face].name == "Plain Bear" })
        assertEquals(listOf("G"), bear.identity)
        assertEquals("Creature — Bear", bear.type)
        assertTrue(bear.legal and (1L shl catalog.header.formats.indexOf("modern")) != 0L)
        assertEquals(0L, bear.legal and (1L shl catalog.header.formats.indexOf("pauper")))
        val knight = catalog.printings.single { catalog.faces[it.face].name == "Test Knight" }
        assertEquals("rare", knight.rarity)
        assertNotNull(knight.scryfall)
        // LOB-125: a card's tokens and a token's makers
        val token = catalog.printings.single { it.token }
        assertEquals(listOf(token.id), catalog.printings.single { catalog.faces[it.face].name == "Test Caller" }.tokens)
        assertEquals(listOf("test caller"), token.makers)
        // DRF-088's kinds of set; only draftable sets keep a recipe
        assertEquals(listOf("BAD", "BON", "OFF", "TST", "TWL"), catalog.sets.filter { it.draftable }.map { it.code })
        assertEquals(listOf("BAD", "BON", "TST", "TWL"), catalog.recipes.map { it.set })
        assertEquals(catalog.header.faces, catalog.faces.size)
        assertEquals(catalog.header.printings, catalog.printings.size)
    }

    // SYS-082 (section 3.8): a card is supported when it is in a listed set or on the single-card list, and nothing of it is left to the players
    @Test fun `the support marks follow the lists and what Automatum leaves to the players`() {
        assertTrue(face("Test Bear").automatum, "in a listed set")
        assertTrue(face("Listed Lone").automatum, "on the single-card list")
        assertFalse(face("Plain Bear").automatum, "neither")
        assertFalse(face("Glorp Wizard").automatum, "listed, but its text can't be read")
        assertFalse(catalog.faces.single { it.name == "Elemental" }.automatum, "tokens carry no mark")
        val sets = catalog.set
        assertTrue(sets.getValue("TST").automatum)
        assertTrue(sets.getValue("TWL").automatum, "a meld pair whose result packs never hold")
        assertFalse(sets.getValue("BAD").automatum)
        assertEquals(listOf("Glorp Wizard"), sets.getValue("BAD").unsupported)
        assertEquals(listOf("Plain Bear"), sets.getValue("OFF").unsupported)
        assertFalse(sets.getValue("DCK").automatum, "not a kind of set that is drafted")
        val built = build()
        assertEquals(listOf("Glorp Wizard"), built.listed.filter { it.parts.isNotEmpty() }.map { it.name })
        assertEquals(listOf("Nobody Here"), build(setOf("Listed Lone", "Nobody Here")).unknownNames)
    }

    // SYS-082: a reversible printing (the same card on both sides) counts as the card it shows
    @Test fun `a reversible printing is the card it shows`() {
        val reversible = catalog.faces.filter { it.layout == "reversible_card" }
        assertEquals(2, reversible.size)
        assertTrue(reversible.all { it.automatum }, "Test Bear is supported, and so are its reversible printing's faces")
        // It never makes its set's packs unsupported on its own: only the special guest from OFF does
        assertEquals(listOf("Plain Bear"), catalog.set.getValue("BON").unsupported)
        assertTrue(build().listed.none { it.name == "Test Bear // Test Bear" })
        // In packs it is Test Bear, a card of one face
        val printing = catalog.printings.single { catalog.faces[it.face].layout == "reversible_card" && catalog.faces[it.face].side == "a" }
        val definition = CatalogCards.definition(catalog, printing)
        assertEquals("Test Bear", definition.name)
        assertEquals("normal", definition.layout)
        assertEquals(1, definition.faces.size)
        assertEquals(printing.scryfall, definition.faces.single().scryfallId)
        assertTrue(DeckValidation.definition(definition))
    }

    // SYS-081 (section 3.3): the version is the checksum of the content, so the same input gives the same file
    @Test fun `the same input gives the same catalog, and it reads back whole`() {
        val first = ByteArrayOutputStream().also { CardCatalogFiles.write(catalog, it) }.toByteArray()
        val again = ByteArrayOutputStream()
        val checksum = CardCatalogFiles.write(build().catalog, again)
        assertTrue(first.contentEquals(again.toByteArray()))
        val (read, readChecksum) = CardCatalogFiles.read(first.inputStream())
        assertEquals(checksum, readChecksum)
        assertEquals(catalog.records().toList(), read.records().toList())
        // A change in what the build extracts is a new version, even from the same file
        assertTrue(checksum != CardCatalogFiles.write(build(emptySet()).catalog, ByteArrayOutputStream()))
    }

    // DRF-169 (section 3.7): set packs from the catalog
    @Test fun `a set's source comes from the catalog with its recipe and valid card data`() {
        val tst = assertNotNull(CatalogSetSources.build(catalog, "TST"))
        assertEquals("Test Set", tst.snapshot.setName)
        assertEquals(4, tst.snapshot.cards.size, "the set's cards, never its token")
        assertEquals(SetCardRarity.RARE, tst.snapshot.cards.single { c -> tst.definitions.single { it.digest == c.definition }.definition.name == "Test Knight" }.rarity)
        assertEquals(setOf("common", "uncommon", "rare"), tst.snapshot.recipe!!.sheets.keys)
        assertTrue(tst.definitions.all { DeckValidation.definition(it.definition) && DeckJson.digest(it.definition) == it.digest })
        assertTrue(CatalogSetSources.validate(tst.snapshot).isEmpty())
        // 7ZZC89: the meld result is no card of its own; its fronts are
        val twl = assertNotNull(CatalogSetSources.build(catalog, "TWL"))
        val names = twl.definitions.map { it.definition.name }
        assertTrue("Bramble Twin // Thornwall Colossus" in names)
        assertEquals(22, twl.snapshot.cards.size)
        assertNull(CatalogSetSources.build(catalog, "NOPE"))
    }

    // SYS-081 (section 3.4): the release builds the catalog from the pinned file and stops on a pin mismatch
    @Test fun `the catalog command stops on a raw file or a catalog that isn't the pinned one`() {
        val directory = createTempDirectory("catalog-pin").toFile()
        try {
            val raw = CardCatalogFiles.sha256(allPrintings)
            val checksum = CardCatalogFiles.write(catalog, ByteArrayOutputStream())
            val pin = File(directory, "pin.properties")
            fun run(vararg args: String): Pair<Int, String> {
                val out = ByteArrayOutputStream()
                val code = CatalogCommand(PrintStream(out, true), directory).run(args.toList() + listOf("--pin", pin.path,
                    "--file", allPrintings.path, "--sets", File(fixtures, "supported-sets.txt").path,
                    "--cards", File(fixtures, "supported-cards.txt").path))
                return code to out.toString()
            }
            CatalogPin("5.3.0+20261031", "2026-10-31", raw, checksum).write(pin)
            assertEquals(0, run("verify").first)
            val (built, _) = run("build", "--out", File(directory, "out").path)
            assertEquals(0, built)
            assertEquals(checksum, File(directory, "out/current").readText().trim())
            assertTrue(File(directory, "out/$checksum.jsonl.gz").isFile)

            CatalogPin("5.3.0+20261031", "2026-10-31", raw, "0".repeat(64)).write(pin)
            val (mismatch, said) = run("build", "--out", File(directory, "out2").path)
            assertEquals(1, mismatch)
            assertTrue("the pin says ${"0".repeat(64)}" in said, said)

            CatalogPin("5.3.0+20261031", "2026-10-31", "1".repeat(64), checksum).write(pin)
            val (other, message) = run("build", "--out", File(directory, "out3").path)
            assertEquals(1, other)
            assertTrue("isn't the pinned MTGJSON file" in message && "scripts/catalog-pin.sh move" in message, message)
            assertFalse(File(directory, "out3").exists(), "nothing built from another file")

            val (moved, _) = run("move")
            assertEquals(0, moved)
            assertEquals(CatalogPin("5.3.0+20261031", "2026-10-31", raw, checksum), CatalogPin.read(pin))
        } finally {
            directory.deleteRecursively()
        }
    }
}
