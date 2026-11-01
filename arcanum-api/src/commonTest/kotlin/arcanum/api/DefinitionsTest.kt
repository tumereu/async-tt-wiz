package arcanum.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json

class DefinitionsTest {
    @Test
    fun definitionsRoundTripThroughReadableJson() {
        val face = FaceDefinition("Ironbound Courier", abilities = listOf(
            Keyword.Flying, Keyword.Crew(2), Keyword.Equip(Cost.mana(ManaCost.parse("{2}{W}")!!)),
            Keyword.Suspend(4, Cost.mana(ManaCost.parse("{1}{R}")!!)), Ability.Unparsed("Whenever Ironbound Courier attacks, scry 1.")))
        val json = Json.encodeToString(FaceDefinition.serializer(), face)
        assertEquals(
            """{"name":"Ironbound Courier","abilities":[{"type":"flying"},{"type":"crew","amount":2},""" +
                """{"type":"equip","cost":{"parts":[{"type":"mana","cost":"{2}{W}"}]}},""" +
                """{"type":"suspend","amount":4,"cost":{"parts":[{"type":"mana","cost":"{1}{R}"}]}},""" +
                """{"type":"unparsed","text":"Whenever Ironbound Courier attacks, scry 1."}]}""",
            json)
        assertEquals(face, Json.decodeFromString(FaceDefinition.serializer(), json))
    }

    @Test
    fun keywordAmountsCanNotBeNegative() {
        assertFailsWith<IllegalArgumentException> { Keyword.Crew(-1) }
    }
}
