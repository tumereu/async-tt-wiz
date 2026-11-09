package arcanum.tools

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParserMetricsTest {
    private fun rule(regex: String, replacement: String, skip: String? = null) =
        PerturbationRule("noun", "test", Regex(regex), replacement, skip?.let(::Regex))

    @Test fun eachOccurrenceIsSwappedAlone() {
        val text = "Exile target creature. Exile target land.\nExile target artifact."
        assertEquals(listOf(
            "Destroy target creature. Exile target land.\nExile target artifact.",
            "Exile target creature. Destroy target land.\nExile target artifact.",
            "Exile target creature. Exile target land.\nDestroy target artifact.",
        ), rule("""\bExile target\b""", "Destroy target").substitutions(text))
    }

    @Test fun aSkippedLineIsLeftAlone() {
        assertEquals(listOf("Draw a card.\nExile it."), rule("""\bdraw\b""", "Draw", skip = "gets").substitutions("draw a card.\nExile it."))
        assertEquals(emptyList(), rule("""\bdraw\b""", "Draw", skip = "a card").substitutions("draw a card."))
    }

    @Test fun groupsAreFilledIn() {
        assertEquals(listOf("Surveil 2."), rule("""\bScry (\d)\b""", "Surveil $1").substitutions("Scry 2."))
    }

    @Test fun reminderTextIsNotASwap() {
        assertEquals(emptyList(), rule("""\bdraw\b""", "mill").substitutions("Flying (Then you draw.)"))
        assertEquals(listOf("mill"), rule("""\bdraw\b""", "mill").substitutions("draw (Then you draw.)"))
    }

    @Test fun aSwapToTheSameWordingIsNoSwap() {
        assertEquals(emptyList(), rule("""\bgain \d life\b""", "gain 7 life").substitutions("You gain 7 life."))
    }

    @Test fun theCommittedRulesCoverEveryClass() {
        val rules = perturbationRules()
        assertEquals(setOf("noun", "verb", "event", "number", "duration", "cost", "condition"), rules.map { it.kind }.toSet())
        assertEquals(rules.size, rules.map { it.name }.toSet().size, "each rule has its own name")
        assertEquals(listOf("Target creature gets +1/+1 until your next turn."),
            rules.single { it.kind == "duration" }.substitutions("Target creature gets +1/+1 until end of turn."))
    }

    @Test fun aSwapOnACardCountsAsReadOrNot() {
        val face = arcanum.parser.OracleCard("Test Bolt", null, "Sorcery", "Draw a card.", null, null, null, null, null)
        val card = CorpusCard("b", "Test Bolt", "normal", listOf(face), CardScope.SupportedSet)
        val read = perturb(listOf(card), listOf(rule("""\bDraw a card\b""", "Draw two cards"))).single()
        assertEquals(1 to 1, read.total to read.read)
        assertEquals(emptyList(), read.sameModel)
        val unread = perturb(listOf(card), listOf(rule("""\bDraw a card\b""", "Mumble at the moon"))).single()
        assertEquals(1 to 0, unread.total to unread.read)
    }

    @Test fun abilitiesGoOnePerLineWithTheirModes() {
        assertEquals(listOf("Flying", "Choose one —\n• Draw a card.\n• Scry 2.", "When this enters, gain 2 life."),
            abilityBlocks("Flying (It flies.)\nChoose one —\n• Draw a card.\n• Scry 2.\n\n(Just a reminder.)\nWhen this enters, gain 2 life."))
    }

    @Test fun theGridsHaveTheirSizes() {
        val sizes = parserGrids().groupingBy { it.grid }.eachCount()
        assertEquals(mapOf("G1" to 260, "G1b" to 78, "G2" to 96, "G3" to 126, "G4" to 62, "G5" to 48, "G6" to 48, "G7" to 522, "G8" to 88,
            "G9" to 180, "G10" to 61, "G11" to 38), sizes)
    }

    @Test fun aGridCellIsACard() {
        val cell = parserGrids().first { it.group == "G6 duration: pump" }
        assertEquals("{T}: Target creature gets +2/+2 until end of turn.", cell.text)
        assertTrue(faceReads(cell.card))
    }

    @Test fun usageBucketsCoverEveryCount() {
        assertEquals(listOf(2, 1, 2, 2, 1, 2), usageTable(listOf(0, 0, 1, 2, 3, 4, 9, 10, 50, 5000)))
    }

    @Test fun theProbeCountsAPatternOncePerCard() {
        val usage = RegexProbe.compiled("probe test (.+)")
        assertEquals(usage, RegexProbe.compiled("probe test (.+)"), "a recompiled pattern is the same pattern")
        usage.matched() // no card yet
        assertEquals(0, usage.allCards)
        RegexProbe.startCard(1, supported = true)
        usage.matched()
        usage.matched()
        RegexProbe.startCard(2, supported = false)
        usage.matched()
        RegexProbe.stop()
        usage.matched()
        assertEquals(2, usage.allCards)
        assertEquals(1, usage.supportedCards)
    }

    @Test fun theProbeCountsAPatternWithACardsNameOnce() {
        assertEquals(RegexProbe.compiled("\\QTest Bolt\\E (attack)"), RegexProbe.compiled("\\QOther Name\\E (attack)"))
    }

    @Test fun regexCallsAreCountedWithTheirDistinctPatterns() {
        val dir = createTempDirectory("regex-literals").toFile()
        try {
            File(dir, "A.kt").writeText("val a = Regex(\"\"\"Draw (.+)\\.\"\"\")\nval b = Regex(\"\"\"Draw (.+)\\.\"\"\")\nval c = Regex(\"x\\\"y\")\nfun typeRegex(t: String) = 1\n")
            File(dir, "B.kt").writeText("val d by lazy { Regex(Regex.escape(name) + \" attack\") }\nval e = Regex(\n    \"\"\"on the next line\"\"\")\n")
            File(dir, "C.txt").writeText("Regex(\"not kotlin\")")
            val counted = regexLiterals(dir)
            assertEquals(5, counted.total)
            assertEquals(4, counted.distinct)
        } finally {
            dir.deleteRecursively()
        }
    }
}
