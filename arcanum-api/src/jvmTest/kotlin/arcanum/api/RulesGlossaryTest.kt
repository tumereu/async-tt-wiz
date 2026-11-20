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

    // The rules reminders explain every token kind the rules define
    @Test
    fun everyPredefinedTokenHasAnExplanationUnderItsPrintedName() {
        val missing = PredefinedTokens.names.filter { RulesGlossary.token(it) == null }
        assertTrue(missing.isEmpty(), "token kinds without an explanation: $missing")
        assertEquals(PredefinedTokens.names.toSet() + "Role", RulesGlossary.tokens.keys, "no explanation of a token kind that isn't one")
    }

    // TBL-209, CR 111.10 (owner 2026-11-20): a token kind's explanation says what the token is, as reminder text does: its
    // card type, then each ability the rules give it in quotes (nested quotes single)
    @Test
    fun tokenExplanationsSayWhatTheTokenIsAndQuoteItsAbilities() {
        for (name in PredefinedTokens.names) {
            val text = RulesGlossary.token(name)!!.text
            val face = PredefinedTokens.face(name)!!
            val type = if (CardType.Creature in face.types) "creature" else if ("Role" in name) "Aura" else face.types.single().toString().lowercase()
            assertTrue(Regex("""^It's an? .*\b$type token\b""").containsMatchIn(text), "$name: $text")
            for (ability in face.abilities) {
                val said = when (ability) {
                    is Ability.Activated -> ability.text
                    is Ability.Static -> ability.text
                    is Ability.Triggered -> ability.text
                    else -> null
                } ?: continue
                assertTrue("\"${said.replace('"', '\'')}\"" in text, "$name quotes \"$said\": $text")
            }
        }
        assertEquals("It's a 2/2 black Zombie creature token named Walker.", RulesGlossary.token("Walker")!!.text)
        // CR 303.7a's Role rule, in its own words, closes every Role's
        for (name in PredefinedTokens.names.filter { it.endsWith(" Role") } + "Role") {
            val text = RulesGlossary.token(name)!!.text
            assertTrue("If you put another Role on" in text && text.endsWith("goes to the graveyard."), "$name: $text")
        }
    }

    // Every ability label (exhaust, max speed, ...) is explained, solved as the state it is
    @Test
    fun everyAbilityLabelHasAnExplanation() {
        val ids = AbilityLabel::class.sealedSubclasses.map { assertNotNull(it.findAnnotation<SerialName>(), "${it.simpleName}").value }
        val missing = ids.filter { RulesGlossary.terms[it] == null && RulesGlossary.state(it) == null }
        assertTrue(missing.isEmpty(), "ability labels without an explanation: $missing")
    }

    @Test
    fun explanationsArePlainSentencesOfTheirOwn() {
        val entries = RulesGlossary.keywords.values + RulesGlossary.actions.values + RulesGlossary.tokens.values + RulesGlossary.terms.values
        for (entry in entries) {
            // A token kind's may end in the quoted ability it has (owner 2026-11-20: told as reminder text tells it)
            assertTrue(entry.text.length in 20..260 && (entry.text.endsWith(".") || entry.text.endsWith(".\"")), "${entry.id}: ${entry.text}")
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
