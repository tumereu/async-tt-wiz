package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.PrintedNumber
import arcanum.api.Supertype
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.CardDefinition as AtwCard

/**
 * ATW card data for an engine definition: what ATW shows of a token the engine creates (D35). The engine keeps the
 * token's own definition in its ledger ([CardLedger.definition]), so this only shows it: one ATW face per engine face
 * where ATW shows faces separately (transform, modal double-faced), one face otherwise; no rules text yet.
 */
internal object AtwTokens {
    /** A fixed namespace for these face ids. */
    private const val NAMESPACE = "2e9b7c41-6d0a-4f3e-8b52-91c7a4d0e6f8"

    fun submitted(definition: CardDefinition, token: Boolean = true): SubmittedDefinition {
        val separate = definition.layout == Layout.Transform || definition.layout == Layout.ModalDoubleFaced
        val shown = if (separate || definition.faces.size == 1) definition.faces
            else listOf(definition.faces.first().copy(name = definition.faces.joinToString(" // ") { it.name }))
        val ids = shown.mapIndexed { i, face -> CommandIds.uuidV5(NAMESPACE, "${face.name}/$i/${shown.size}") }
        val faces = shown.mapIndexed { i, face ->
            DefinitionFace(ids[i], face.name, otherFaceIds = ids - ids[i], typeLine = typeLine(face, token),
                manaCost = face.manaCost?.toString().orEmpty(), power = number(face.power), toughness = number(face.toughness),
                loyalty = number(face.loyalty), defense = number(face.defense),
                colorIndicator = face.colorIndicator?.let { colors -> Color.all.filter { it in colors }.joinToString("") { "${it.symbol}" } }
                    ?.takeIf { it.isNotEmpty() })
        }
        val layout = when (definition.layout) {
            Layout.Transform -> "transform"
            Layout.ModalDoubleFaced -> "modal_dfc"
            else -> "normal"
        }
        val card = AtwCard(shown.first().name, faces, layout = layout, token = token)
        return SubmittedDefinition(DeckJson.digest(card), card)
    }

    private fun typeLine(face: FaceDefinition, token: Boolean): String {
        fun word(element: kotlinx.serialization.json.JsonElement) =
            element.jsonObject["type"]!!.jsonPrimitive.content.replaceFirstChar { it.uppercase() }
        val supertypes = face.supertypes.map { word(EngineCodec.json.encodeToJsonElement(Supertype.serializer(), it)) }
        val types = face.types.map { word(EngineCodec.json.encodeToJsonElement(CardType.serializer(), it)) }
        val front = ((if (token) listOf("Token") else emptyList()) + supertypes + types).joinToString(" ")
        return if (face.subtypes.isEmpty()) front else front + " — " + face.subtypes.joinToString(" ") { it.name }
    }

    private fun number(value: PrintedNumber?): String = when (value) {
        null -> ""
        is PrintedNumber.Fixed -> "${value.value}"
        is PrintedNumber.Star -> if (value.plus == 0) "*" else "${value.plus}+*"
        PrintedNumber.X -> "X"
    }
}
