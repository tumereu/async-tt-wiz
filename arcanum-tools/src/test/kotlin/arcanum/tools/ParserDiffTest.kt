package arcanum.tools

import arcanum.parser.OracleCard
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParserDiffTest {
    private val dir: File = createTempDirectory("parser-diff").toFile()

    @AfterTest fun clean() {
        dir.deleteRecursively()
    }

    private fun line(name: String, scope: CardScope, read: Boolean = true, model: String = """{"abilities":["$name"]}""") =
        ModelLine("id-$name", name, scope, read, model)

    private val sword = line("Test Sword", CardScope.SupportedSet)
    private val bear = line("Test Bear", CardScope.Cube)
    private val sprite = line("Test Sprite", CardScope.HeldOut)
    private val ghoul = line("Test Ghoul", CardScope.HeldOut, read = false)
    private val all = listOf(sword, bear, sprite, ghoul)

    private fun snapshot(lines: List<ModelLine>, mtgjson: String = "1") = snapshotOf(40, mtgjson, lines)
    private fun replaced(card: ModelLine, by: ModelLine) = all.map { if (it === card) by else it }

    /** The gate as the tool runs it: the exit code from the snapshot file and the current cards. */
    private fun gate(before: List<ModelLine>, current: List<ModelLine>, mtgjson: String = "1"): Int {
        val file = File(dir, "models.snapshot")
        writeSnapshot(snapshot(before), file)
        return gateResult(readSnapshot(file), snapshot(current, mtgjson), File(dir, "diff.txt"))
    }

    @Test fun theSameModelsPass() {
        assertEquals(0, gate(all, all))
        val diff = diffSnapshots(snapshot(all), snapshot(all))
        assertFalse(diff.failed)
        assertEquals(0, diff.changes)
    }

    @Test fun aChangedModelInASupportedSetFailsTheGate() {
        val current = replaced(sword, sword.copy(model = """{"abilities":["changed"]}"""))
        assertEquals(1, gate(all, current))
        assertEquals(listOf("Test Sword"), diffSnapshots(snapshot(all), snapshot(current)).supported.map { it.name })
    }

    @Test fun aChangedModelOfACubeCardFailsTheGate() {
        val current = replaced(bear, bear.copy(model = """{"abilities":["changed"]}"""))
        assertEquals(1, gate(all, current))
        assertEquals(listOf("Test Bear"), diffSnapshots(snapshot(all), snapshot(current)).cube.map { it.name })
    }

    @Test fun aHeldOutCardThatStopsBeingReadFailsTheGate() {
        val current = replaced(sprite, sprite.copy(read = false, model = """{"abilities":["unparsed"]}"""))
        assertEquals(1, gate(all, current))
        assertEquals(listOf("Test Sprite"), diffSnapshots(snapshot(all), snapshot(current)).stoppedReading.map { it.name })
    }

    @Test fun aReadCardThatIsGoneFailsTheGate() {
        assertEquals(1, gate(all, all - sprite))
    }

    @Test fun aSupportedCardThatStopsBeingReadIsInBothLists() {
        val current = replaced(sword, sword.copy(read = false, model = "{}"))
        val diff = diffSnapshots(snapshot(all), snapshot(current))
        assertEquals(1, gate(all, current))
        assertEquals(listOf("Test Sword"), diff.supported.map { it.name })
        assertEquals(listOf("Test Sword"), diff.stoppedReading.map { it.name })
    }

    @Test fun aCardNowInASupportedSetCountsAsSupported() {
        val current = replaced(sprite, sprite.copy(scope = CardScope.SupportedSet, model = "{}"))
        assertEquals(1, gate(all, current))
    }

    @Test fun heldOutChangesAreListedSeparatelyAndDontFail() {
        val spriteChanged = sprite.copy(model = """{"abilities":["other"]}""")
        val ghoulRead = ghoul.copy(read = true, model = """{"abilities":["read"]}""")
        val current = listOf(sword, bear, spriteChanged, ghoulRead, line("Test Imp", CardScope.HeldOut, read = false))
        val before = all + line("Test Imp", CardScope.HeldOut, read = false, model = """{"abilities":["less"]}""")
        assertEquals(0, gate(before, current))
        val diff = diffSnapshots(snapshot(before), snapshot(current))
        assertEquals(listOf("Test Ghoul"), diff.heldOutNewlyRead.map { it.name })
        assertEquals(listOf("Test Sprite"), diff.heldOutReadDifferently.map { it.name })
        assertEquals(listOf("Test Imp"), diff.heldOutUnreadChanged.map { it.name })
        assertEquals(3, diff.changes)
        val report = File(dir, "diff.txt").readText()
        assertTrue("Held out, newly read (for review): 1" in report, report)
        assertTrue("Test Ghoul" in report && "Test Sprite" in report && "Test Imp" in report, report)
    }

    @Test fun aNewCardIsListedAndDoesntFail() {
        val imp = line("Test Imp", CardScope.HeldOut)
        assertEquals(0, gate(all, all + imp))
        assertEquals(listOf("Test Imp"), diffSnapshots(snapshot(all), snapshot(all + imp)).added.map { it.name })
    }

    @Test fun otherCardDataIsRefusedRatherThanCompared() {
        assertEquals(2, gate(all, all, mtgjson = "2"))
    }

    @Test fun theFirstDifferenceShowsWhereModelsPart() {
        val change = ChangedCard(sword, sword.copy(model = """{"abilities":["Test Spear"]}"""))
        assertTrue("was:" in change.firstDifference() && "Test Spear" in change.firstDifference(), change.firstDifference())
    }

    @Test fun aSnapshotIsSortedAndReadsBackLineForLine() {
        val file = File(dir, "a.snapshot")
        writeSnapshot(snapshot(all.reversed()), file)
        val other = File(dir, "b.snapshot")
        writeSnapshot(snapshot(all), other)
        assertContentEquals(file.readBytes(), other.readBytes())
        val back = readSnapshot(file)
        assertEquals(listOf("Test Bear", "Test Ghoul", "Test Sprite", "Test Sword"), back.lines.map { it.name })
        assertEquals(all.toSet(), back.lines.toSet())
        assertEquals("1", back.mtgjson)
        assertEquals(40, back.parser)
    }

    @Test fun aSnapshotCutShortIsRefused() {
        val file = File(dir, "cut.snapshot")
        writeSnapshot(snapshot(all), file)
        file.writeText(file.readLines().dropLast(1).joinToString("\n") + "\n")
        assertTrue(runCatching { readSnapshot(file) }.isFailure)
    }

    private fun face(name: String, text: String, type: String = "Sorcery") = OracleCard(name, null, type, text, null, null, null, null, null)

    @Test fun modelsOfTheRealParserAreRepeatableAndSayWhatIsRead() {
        val read = cardModel("normal", listOf(face("Test Bolt", "Draw a card.")))
        assertTrue(read.read)
        assertEquals(read.json, cardModel("normal", listOf(face("Test Bolt", "Draw a card."))).json)
        assertFalse(cardModel("normal", listOf(face("Test Mumble", "Mumble at the moon."))).read)
    }

    @Test fun aRealCardThatChangesWordingFailsTheGateAsChangedOrUnread() {
        val before = cardModel("normal", listOf(face("Test Bolt", "Draw a card.")))
        val unread = cardModel("normal", listOf(face("Test Bolt", "Mumble at the moon.")))
        val was = ModelLine("b", "Test Bolt", CardScope.SupportedSet, before.read, before.json)
        assertEquals(1, gate(listOf(was), listOf(was.copy(read = unread.read, model = unread.json))))
    }

    @Test fun aLayoutTheParserLacksLeavesTheCardUnread() {
        val model = cardModel("mystery", listOf(face("Test Half", "Draw a card."), face("Test Other", "Draw a card.")))
        assertFalse(model.read)
        assertTrue("unknownLayout" in model.json, model.json)
    }

    @Test fun printedValuesThatCantBeReadAreUnreadNotAFailure() {
        val model = cardModel("normal", listOf(OracleCard("Test Bad", "not a cost", "Sorcery", "", null, null, null, null, null)))
        assertFalse(model.read)
        assertTrue("unreadable mana cost" in model.json, model.json)
    }
}
