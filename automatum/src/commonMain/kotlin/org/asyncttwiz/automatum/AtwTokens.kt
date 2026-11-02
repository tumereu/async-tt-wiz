package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CostPart
import arcanum.api.FaceDefinition
import arcanum.api.HasAmount
import arcanum.api.HasCost
import arcanum.api.Keyword
import arcanum.api.KeywordKind
import arcanum.api.Layout
import arcanum.api.PredefinedTokens
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
 * token's own definition in its ledger ([CardLedger.definition]); this card data shows it and carries it for manual play
 * and manual copies (I-D2): one ATW face per engine face where ATW shows faces separately (transform, modal
 * double-faced), one face otherwise, with its printed values and rules text ([rulesText]).
 */
internal object AtwTokens {
    /** A fixed namespace for these face ids. */
    private const val NAMESPACE = "2e9b7c41-6d0a-4f3e-8b52-91c7a4d0e6f8"

    /** I-D13: the card data an ability-only Stack entry shows (`StackAction.AddAbility`): one face named after its
     * source ([source], "Ability" when unknown) with the ability's own text as its rules text. */
    fun ability(source: String?, ability: Ability?): SubmittedDefinition {
        val name = source?.takeIf { it.isNotBlank() } ?: "Ability"
        val text = ability?.let(::text).orEmpty()
        val face = DefinitionFace(CommandIds.uuidV5(NAMESPACE, "ability/$name/$text"), name, typeLine = "Ability", rulesText = text)
        val card = AtwCard(name, listOf(face), token = true)
        return SubmittedDefinition(DeckJson.digest(card), card)
    }

    fun submitted(definition: CardDefinition, token: Boolean = true): SubmittedDefinition {
        val separate = definition.layout == Layout.Transform || definition.layout == Layout.ModalDoubleFaced
        val shown = if (separate || definition.faces.size == 1) definition.faces
            else listOf(definition.faces.first().copy(name = definition.faces.joinToString(" // ") { it.name }))
        val ids = shown.mapIndexed { i, face -> CommandIds.uuidV5(NAMESPACE, "${face.name}/$i/${shown.size}") }
        val faces = shown.mapIndexed { i, face ->
            DefinitionFace(ids[i], face.name, otherFaceIds = ids - ids[i], typeLine = typeLine(face, token),
                manaCost = face.manaCost?.toString().orEmpty(), rulesText = rulesText(face), power = number(face.power), toughness = number(face.toughness),
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

    /**
     * The face's rules text as oracle text writes it, so players see what a token does (and play it by hand), and ATW's
     * own parse of the card data (I-D2) reads its abilities back: the text each ability was parsed from; keywords by
     * name (runs of plain keywords on one line, "Flying, haste"), with their number or mana cost; a predefined token's
     * ability as CR 111.10 words it. An ability with no text of its own (a keyword with another kind of value, an
     * ability an effect built) is left out: the token's ledger still holds the engine's whole definition.
     */
    fun rulesText(face: FaceDefinition): String {
        PredefinedTokens.names.firstOrNull { PredefinedTokens.face(it) == face }?.let { name -> return PREDEFINED[name].orEmpty() }
        val lines = mutableListOf<String>()
        var keywords = mutableListOf<String>()
        fun flush() {
            if (keywords.isNotEmpty()) lines += keywords.first() + keywords.drop(1).joinToString("") { ", " + it.replaceFirstChar(Char::lowercase) }
            keywords = mutableListOf()
        }
        for (ability in face.abilities) {
            if (ability is Keyword && ability is KeywordKind) { keywords += ability.text; continue }
            flush()
            text(ability)?.let { lines += it }
        }
        flush()
        return lines.joinToString("\n")
    }

    private fun text(ability: Ability): String? = when (ability) {
        is Keyword -> when (ability) {
            is HasAmount -> "${ability.text} ${ability.amount}"
            is HasCost -> (ability.cost.parts.singleOrNull() as? CostPart.Mana)?.let { "${ability.text} ${it.cost}" }
            else -> null
        }
        is Ability.Spell -> ability.text
        is Ability.Activated -> ability.text
        is Ability.Triggered -> ability.text
        is Ability.Static -> ability.text
        is Ability.SpecialAction -> ability.text
        is Ability.Unparsed -> ability.text
        else -> null
    }

    /** The predefined tokens' abilities (CR 111.10), by [PredefinedTokens] name. */
    private val PREDEFINED = mapOf(
        "Treasure" to "{T}, Sacrifice this token: Add one mana of any color.",
        "Food" to "{2}, {T}, Sacrifice this token: You gain 3 life.",
        "Gold" to "Sacrifice this token: Add one mana of any color.",
        "Shard" to "{2}, Sacrifice this token: Scry 1, then draw a card.",
        "Clue" to "{2}, Sacrifice this token: Draw a card.",
        "Blood" to "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.",
    )

    private fun number(value: PrintedNumber?): String = when (value) {
        null -> ""
        is PrintedNumber.Fixed -> "${value.value}"
        is PrintedNumber.Star -> if (value.plus == 0) "*" else "${value.plus}+*"
        PrintedNumber.X -> "X"
    }
}
