package arcanum.parser

import arcanum.api.FaceDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An ability quoted inside another (a token's: 'create a … token with "…"'; one a card grants: 'creatures you control have
 * "…"') keeps its printed words as its text, as a card's own line does, so a player reads what the token or permanent can
 * do (CR 113.1a, 111.4; owner fault 2PTQG5).
 */
class QuotedAbilityTextTest {
    /** The texts of the abilities inside [face]'s own abilities (not its own lines), in the order they are written. */
    private fun quotedTexts(face: FaceDefinition): List<String?> {
        val found = mutableListOf<String?>()
        fun walk(element: JsonElement, top: Boolean) {
            when (element) {
                is JsonObject -> {
                    val type = (element["type"] as? JsonPrimitive)?.content
                    val ability = type in setOf("triggered", "activated", "static") && ("instructions" in element || "mechanics" in element)
                    if (ability && !top) found += element["text"]?.jsonPrimitive?.content
                    element.values.forEach { walk(it, false) }
                }
                is JsonArray -> element.forEach { walk(it, false) }
                else -> Unit
            }
        }
        face.abilities.forEach { walk(Json.encodeToJsonElement(arcanum.api.Ability.serializer(), it), true) }
        return found
    }

    // CR 111.4: a token's quoted ability keeps its words
    @Test
    fun aTokensQuotedAbilityKeepsItsText() {
        val face = OracleParser.parse(OracleCard("Kiln Tinker", "{2}{W}", "Creature — Dwarf Artificer",
            "Whenever you cast an artifact spell, you may pay {2}. If you do, create a 0/0 colorless Construct artifact creature token with " +
                "\"This token gets +1/+1 for each artifact you control.\"", power = "3", toughness = "3"))
        assertEquals(listOf("This token gets +1/+1 for each artifact you control."), quotedTexts(face))
    }

    // CR 113.1a: an ability a permanent grants in quotes keeps its words
    @Test
    fun aGrantedQuotedAbilityKeepsItsText() {
        val face = OracleParser.parse(OracleCard("Forge Captain", "{3}{U}", "Creature — Human Artificer",
            "Artifact creatures you control have \"Whenever this creature deals combat damage to a player, you may pay {1}. If you do, " +
                "create a 1/1 colorless Servo artifact creature token.\"", power = "2", toughness = "4"))
        assertEquals(listOf("Whenever this creature deals combat damage to a player, you may pay {1}. If you do, create a 1/1 colorless Servo " +
            "artifact creature token."), quotedTexts(face))
    }
}
