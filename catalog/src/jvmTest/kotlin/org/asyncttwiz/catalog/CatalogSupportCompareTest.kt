package org.asyncttwiz.catalog

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The release's guard against dropping support for cards (owner, 2026-11-19): `compare-support` lists the cards the old
 * catalog marks as supported (SYS-082) and the new one doesn't, and refuses unless the count is confirmed. */
class CatalogSupportCompareTest {
    private val directory = createTempDirectory("catalog-support").toFile()

    @AfterTest fun clean() { directory.deleteRecursively() }

    /** A catalog of invented cards in [name]'s directory (`current` naming it); [supported] the names Automatum supports. */
    private fun catalog(name: String, cards: List<String>, supported: Set<String>): File {
        val faces = cards.mapIndexed { i, card -> CatalogFaceRecord(i = i, name = card, automatum = card in supported) }
        val header = CatalogHeader(CardCatalogFormat.FORMAT, "5.3.0+20261031", "2026-10-31", emptyList(), 1, 1,
            sets = 0, faces = faces.size, printings = 0, recipes = 0)
        val out = File(directory, name)
        val file = CardCatalogFiles.write(CardCatalog(header, emptyList(), faces, emptyList(), emptyList()), out)
        File(out, "current").writeText(file.name.removeSuffix(CardCatalogFiles.SUFFIX) + "\n")
        return out
    }

    private fun compare(vararg args: String): Pair<Int, String> {
        val out = ByteArrayOutputStream()
        val code = CatalogCommand(PrintStream(out, true), directory).run(listOf("compare-support") + args)
        return code to out.toString()
    }

    private val old by lazy { catalog("old", listOf("Grove Tender", "Ash Courier", "Mire Wisp", "Lone Herald"), setOf("Grove Tender", "Ash Courier", "Mire Wisp")) }

    @Test fun `a new catalog supporting every card the old one did passes`() {
        val new = catalog("new", listOf("Grove Tender", "Ash Courier", "Mire Wisp", "Lone Herald", "Dawn Lancer"),
            setOf("Grove Tender", "Ash Courier", "Mire Wisp", "Lone Herald", "Dawn Lancer"))
        val (code, output) = compare("--old", old.path, "--new", new.path)
        assertEquals(0, code, output)
        assertTrue("No card loses Automatum support" in output, output)
    }

    @Test fun `cards no longer supported, or gone, are listed and stop the release`() {
        val new = catalog("new", listOf("Grove Tender", "Mire Wisp", "Lone Herald"), setOf("Grove Tender", "Lone Herald"))
        val (code, output) = compare("--old", old.path, "--new", new.path)
        assertEquals(3, code, output)
        assertTrue("2 cards Automatum supports" in output, output)
        assertTrue("  Ash Courier\n  Mire Wisp\n" in output, output)
        assertFalse("Grove Tender" in output, output)
    }

    @Test fun `only a confirmation naming exactly the dropped count lets it through`() {
        val new = catalog("new", listOf("Grove Tender", "Ash Courier", "Mire Wisp"), setOf("Grove Tender"))
        val (wrong, message) = compare("--old", old.path, "--new", new.path, "--allow", "1")
        assertEquals(3, wrong)
        assertTrue("names 1 cards, not 2" in message, message)
        val (right, output) = compare("--old", old.path, "--new", new.path, "--allow", "2")
        assertEquals(0, right, output)
        assertTrue("Allowed" in output, output)
    }

    @Test fun `a catalog file can be named directly, and one whose name isn't its checksum is refused`() {
        val new = catalog("new", listOf("Grove Tender", "Ash Courier", "Mire Wisp"), setOf("Grove Tender", "Ash Courier", "Mire Wisp"))
        val file = File(old, File(old, "current").readText().trim() + CardCatalogFiles.SUFFIX)
        assertEquals(0, compare("--old", file.path, "--new", new.path).first)
        val renamed = File(directory, "1".repeat(64) + CardCatalogFiles.SUFFIX).also { file.copyTo(it) }
        assertTrue(runCatching { compare("--old", renamed.path, "--new", new.path) }.isFailure)
        assertEquals(1, compare("--old", File(directory, "missing").path, "--new", new.path).first)
    }
}
