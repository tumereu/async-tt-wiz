package arcanum.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CrDeltaTest {
    // Invented rules texts in the published layout; no real rules text is committed.
    private val older = RulesText.parse(
        """
        |These rules are effective as of September 25, 2026.
        |100. Widgets
        |100.1. Widgets are things.
        |100.1a A widget is blue (see rule 101.1).
        |Example: A widget sits on a table.
        |101. Gadgets
        |101.1 Gadgets have levels.
        |101.2 The existence of gadgets is copiable.
        |103.1 A rule whose meaning changes.
        |104.1 A rule that goes away.
        """.trimMargin(),
    )
    private val newer = RulesText.parse(
        listOf(
            "\uFEFFThese rules are effective as of November 6, 2026.", "100. Widgets", "100.1. Widgets are things.",
            "100.1a A widget is blue (see rule 102.1).", "101. Doodads", "101.1 Doodads are new.\u2028An unnumbered paragraph.", "102. Gadgets",
            "102.1 Gadgets have levels.", "102.2 The existence of gadgets is copiable.", "103. Others", "103.1 A rule whose meaning changed.",
            "103.2 The existence of gadgets is copiable.",
        ).joinToString("\r\n"),
    )

    private fun change(rule: String, numbering: Numbering, checked: List<CheckedRule> = emptyList()) =
        crDelta(older, newer, numbering, listOf(Citation(rule, "Some.kt", 1)), checked).cited.getValue(rule).first

    @Test fun readsDatesAndRulesInEitherLayoutWithoutExamples() {
        assertEquals("2026-09-25", older.effectiveDate)
        assertEquals("2026-11-06", newer.effectiveDate)
        assertEquals("A widget is blue (see rule 101.1).", older.rules["100.1a"])
        assertEquals("Doodads are new.", newer.rules["101.1"])
        assertEquals("Gadgets", newer.rules["102"])
    }

    @Test fun findsEveryNumberOfACitationList() {
        val text = "// CR 613.4c, 613.5 and 702.2: x\nfun `CR 100 applies`() = Unit // (CR 101.1; 101.2) not 999 or CR text\n" +
            "// CR 601.2b (an optional cost), 723.1; 6.5 and 100 aren't rules\n// 702.2 without a CR before it isn't read"
        assertEquals(listOf("613.4c" to 1, "613.5" to 1, "702.2" to 1, "100" to 2, "101.1" to 2, "101.2" to 2, "601.2b" to 3, "723.1" to 3),
            citationsIn(text, "A.kt").map { it.rule to it.line })
    }

    @Test fun olderNumbersThatMovedAreRenumbered() {
        assertEquals(RuleChange.Unchanged, change("100.1", Numbering.Older))
        assertEquals(RuleChange.Renumbered(listOf("102.1"), "Gadgets have levels."), change("101.1", Numbering.Older))
        assertEquals(RuleChange.Renumbered(listOf("102"), "Gadgets"), change("101", Numbering.Older))
        // the same text is in 102.2 and 103.2: the section 101 went to settles it
        assertEquals(RuleChange.Renumbered(listOf("102.2"), "The existence of gadgets is copiable."), change("101.2", Numbering.Older))
    }

    @Test fun aRuleWhoseOnlyChangeIsItsReferencesIsSettled() {
        val change = change("100.1a", Numbering.Older)
        assertIs<RuleChange.ReferencesOnly>(change)
        assertTrue(change.settled)
    }

    @Test fun changedRemovedAndUnknownRulesNeedALook() {
        for (numbering in listOf(Numbering.Older, Numbering.Newer)) {
            assertEquals(RuleChange.TextChanged("A rule whose meaning changes.", "A rule whose meaning changed."), change("103.1", numbering))
            assertIs<RuleChange.Removed>(change("104.1", numbering))
            assertEquals(RuleChange.Unknown, change("999.1", numbering))
        }
        assertTrue(crDelta(older, newer, Numbering.Older, listOf(Citation("103.1", "A.kt", 1)), emptyList()).needsLook)
    }

    @Test fun inTheNewerNumberingAMovedRuleIsUnchangedAndANewOneIsNew() {
        assertEquals(RuleChange.Unchanged, change("102.1", Numbering.Newer))
        assertEquals(RuleChange.Unchanged, change("102", Numbering.Newer))
        assertEquals(RuleChange.OnlyInNew("Doodads are new."), change("101.1", Numbering.Newer))
        assertIs<RuleChange.ReferencesOnly>(change("100.1a", Numbering.Newer))
    }

    @Test fun newRulesAreTheOnesSaidNowhereBefore() {
        assertEquals(listOf("101", "101.1", "103", "103.1"), crDelta(older, newer, Numbering.Newer, emptyList(), emptyList()).newRules.map { it.first })
    }

    @Test fun aCheckedChangeForTheNewerVersionIsSettled() {
        val checked = change("103.1", Numbering.Newer, listOf(CheckedRule("103.1", "2026-11-06", "still encoded")))
        assertIs<RuleChange.Checked>(checked)
        assertTrue(checked.settled)
        assertFalse(crDelta(older, newer, Numbering.Newer, listOf(Citation("103.1", "A.kt", 1)), listOf(CheckedRule("103.1", "2026-11-06", "ok"))).needsLook)
        // a check against another version doesn't count
        assertIs<RuleChange.TextChanged>(change("103.1", Numbering.Newer, listOf(CheckedRule("103.1", "2026-09-25", "old"))))
    }

    @Test fun renumberingRewritesOnlyCitations() {
        val text = "// CR 101.1, 100.1 and 101 (see 101.1)\nval x = 101.1\n"
        assertEquals("// CR 102.1, 100.1 and 102 (see 102.1)\nval x = 101.1\n", renumbered(text, mapOf("101.1" to "102.1", "101" to "102")))
    }
}
