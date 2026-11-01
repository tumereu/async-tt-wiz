package arcanum.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json

class DefinitionsTest {
    @Test
    fun definitionsRoundTripThroughReadableJson() {
        val face = FaceDefinition("Ironbound Courier", listOf(
            Keyword.Flying, Keyword.Crew(2), Keyword.Equip(ManaCost.parse("{2}{W}")!!),
            Keyword.Suspend(4, ManaCost.parse("{1}{R}")!!), UnparsedAbility("Whenever Ironbound Courier attacks, scry 1.")))
        val json = Json.encodeToString(FaceDefinition.serializer(), face)
        assertEquals(
            """{"name":"Ironbound Courier","abilities":[{"type":"flying"},{"type":"crew","amount":2},""" +
                """{"type":"equip","cost":"{2}{W}"},{"type":"suspend","amount":4,"cost":"{1}{R}"},""" +
                """{"type":"unparsed","text":"Whenever Ironbound Courier attacks, scry 1."}]}""",
            json)
        assertEquals(face, Json.decodeFromString(FaceDefinition.serializer(), json))
    }

    @Test
    fun keywordAmountsCanNotBeNegative() {
        assertFailsWith<IllegalArgumentException> { Keyword.Crew(-1) }
    }
}
