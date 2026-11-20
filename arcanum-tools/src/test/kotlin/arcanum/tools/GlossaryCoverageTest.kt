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

    @Test
    fun listsRulesWordsWithoutAnExplanation() {
        assertEquals(listOf("Moonbend: no explanation"), glossaryGaps(listOf(face("Tide Caller", "Moonbend {2}: Draw a card.", "Moonbend"))))
    }
}
