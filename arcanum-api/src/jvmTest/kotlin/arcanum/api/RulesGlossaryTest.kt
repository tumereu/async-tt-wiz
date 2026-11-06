package arcanum.api

import kotlinx.serialization.SerialName
import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The rule explainers' glossary: every keyword and keyword action explained (listing a sealed type's subtypes needs JVM
 * reflection, so this check lives in jvmTest). */
class RulesGlossaryTest {
    @Test
    fun everyKeywordHasAnExplanationUnderItsPrintedName() {
        val missing = KeywordKind.all.filter { RulesGlossary.of(it) == null }.map { it.id }
        assertTrue(missing.isEmpty(), "keywords without an explanation: $missing")
        assertEquals(KeywordKind.all.map { it.id }.toSet(), RulesGlossary.keywords.keys, "no explanation of a keyword that isn't one")
        for (kind in KeywordKind.all) assertEquals(kind.text, RulesGlossary.of(kind)!!.name, kind.id)
    }

    @Test
    fun everyKeywordActionHasAnExplanation() {
        val ids = KeywordAction::class.sealedSubclasses.map { assertNotNull(it.findAnnotation<SerialName>(), "${it.simpleName}").value }
        val missing = ids.filter { RulesGlossary.action(it) == null }
        assertTrue(missing.isEmpty(), "keyword actions without an explanation: $missing")
        // The keyword actions that are effects of their own
        for (id in listOf("scry", "surveil", "mill", "fight")) assertNotNull(RulesGlossary.action(id), id)
        assertEquals(ids.toSet() + setOf("scry", "surveil", "mill", "fight"), RulesGlossary.actions.keys)
    }

    @Test
    fun explanationsArePlainSentencesOfTheirOwn() {
        val entries = RulesGlossary.keywords.values + RulesGlossary.actions.values
        for (entry in entries) {
            assertTrue(entry.text.length in 20..260 && entry.text.endsWith("."), "${entry.id}: ${entry.text}")
            // Not reminder text: no parentheses around the whole of it, no "CR" rule numbers
            assertTrue(!entry.text.startsWith("(") && !Regex("""\b\d{3}\.\d""").containsMatchIn(entry.text), entry.id)
        }
        assertEquals(entries.size, entries.map { it.text }.toSet().size, "every explanation is its own")
    }

    @Test
    fun namedFindsWholeWordsLongestFirstInTheOrderNamed() {
        fun ids(text: String) = RulesGlossary.named(text).map { it.id }
        assertEquals(listOf("flying"), ids("flying"))
        assertEquals(listOf("flying"), ids("Flying"))
        assertEquals(listOf("trample"), ids("+3/+3, trample until end of turn"))
        assertEquals(listOf("forage"), ids("Forage"))
        assertEquals(listOf("crew"), ids("Crew 3"))
        assertEquals(listOf("collect-evidence"), ids("Collect evidence 6"))
        assertEquals(listOf("protection"), ids("Protection from red"))
        assertEquals(listOf("double-strike"), ids("Double strike"))
        assertEquals(listOf("manifest-dread"), ids("Manifest dread"))
        assertEquals(listOf("flying", "first-strike"), ids("Flying, first strike"))
        assertEquals(listOf("flying"), ids("loses flying"))
        assertEquals(listOf("start-your-engines"), ids("Start your engines!"))
        // Whole words only: flashback isn't flash, a +1/+1 counter names nothing
        assertEquals(listOf("flashback"), ids("Flashback {2}{R}"))
        assertEquals(emptyList(), ids("+1/+1 counter ×2"))
        assertEquals(emptyList(), ids("Attached to Ember Cub"))
        assertEquals("trample", RulesGlossary.first("Trample")?.id)
        // Variants name their keyword
        assertEquals(listOf("morph"), ids("Megamorph {2}{G}"))
        assertEquals(listOf("kicker"), ids("Multikicker {1}"))
        assertEquals(listOf("cycling"), ids("Swampcycling {2}"))
        assertEquals(listOf("cycling"), ids("Cycling {2}"))
        assertEquals(listOf("landwalk"), ids("Islandwalk"))
    }
}
