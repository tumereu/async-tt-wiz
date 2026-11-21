package arcanum.tools

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals

/** The rules reminders' coverage check of setSupport ([glossaryGaps]), on invented card records shaped as MTGJSON's. */
class GlossaryCoverageTest {
    private fun face(name: String, text: String, vararg keywords: String) = JsonParser.parseString(
        """{"name": ${quote(name)}, "text": ${quote(text)}, "keywords": [${keywords.joinToString { quote(it) }}]}""").asJsonObject
    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    @Test
    fun aCardWhoseRulesWordsAreExplainedHasNoGaps() {
        assertEquals(emptyList(), glossaryGaps(listOf(face("Ember Lookout", "Flying\nWard {2}\nWhen this creature enters, create a Food token.",
            "Flying", "Ward", "Food"))))
        // Words inside reminder text only, words within longer ones, ability words and words the data lists wrongly
        assertEquals(emptyList(), glossaryGaps(listOf(face("Moss Sage", "Dredge 3 (If you would draw a card, you may mill three cards instead.)\n" +
            "Landfall — Whenever a land you control enters, create a Royal Role token attached to target creature you control.\n" +
            "Damage already dealt to it is healed.", "Dredge", "Mill", "Landfall", "Role token", "Heal"))))
    }

    // TBL-209 (owner 2026-11-21, E6): a keyword the parser reads must be named in the card's text, reminder text included, whatever
    // the card data lists
    @Test
    fun keywordsTheParserReadsAreNamedInTheText() {
        assertEquals(emptyList(), glossaryGaps(listOf(face("Gale Rider", "Flying\nOther creatures you control lose trample.")),
            setOf("flying", "trample")))
        assertEquals(emptyList(), glossaryGaps(listOf(face("Bog Strider", "Swampwalk")), setOf("landwalk")))
        assertEquals(listOf("Haste: read by the parser, not named in its text"),
            glossaryGaps(listOf(face("Quick Hound", "This creature can attack as soon as it comes under your control.")), setOf("haste")))
        // The parser's view of an invented card
        val definition = arcanum.parser.OracleParser.parseCard(arcanum.api.Layout.Normal, listOf(arcanum.parser.OracleCard(name = "Gale Rider", manaCost = "{1}{W}",
            typeLine = "Creature — Bird", text = "Flying, vigilance", power = "1", toughness = "1")))
        assertEquals(setOf("flying", "vigilance"), keywordsRead(definition))
    }

    @Test
    fun listsRulesWordsWithoutAnExplanation() {
        assertEquals(listOf("Moonbend: no explanation"), glossaryGaps(listOf(face("Tide Caller", "Moonbend {2}: Draw a card.", "Moonbend"))))
    }
}
