package arcanum.parser

import arcanum.api.CardType
import arcanum.api.FaceDefinition
import arcanum.api.ManaCost
import arcanum.api.PrintedNumber
import arcanum.api.Subtype
import arcanum.api.Supertype

/**
 * One card face as printed: what the parser reads. [text] is the oracle text, with reminder text; [power],
 * [toughness], [loyalty] and [defense] as printed ("2", "*", "1+*", "X"); [colorIndicator] as the colours' letters
 * ("U", "WB"), on faces that have one (the back face of a double-faced card, CR 204).
 */
data class OracleCard(
    val name: String,
    val manaCost: String?,
    val typeLine: String,
    val text: String = "",
    val power: String? = null,
    val toughness: String? = null,
    val loyalty: String? = null,
    val defense: String? = null,
    // chunk 56
    val colorIndicator: String? = null,
)

/** Reads and writes type lines ("Legendary Artifact Creature — Human Wizard", CR 205). */
object TypeLine {
    data class Parsed(val supertypes: List<Supertype>, val types: List<CardType>, val subtypes: List<Subtype>)

    /** Null when a word before the dash is neither a supertype nor a card type. */
    fun parse(line: String): Parsed? {
        val (left, right) = line.split(" — ", limit = 2).let { it[0] to it.getOrNull(1) }
        val supertypes = mutableListOf<Supertype>()
        val types = mutableListOf<CardType>()
        for (word in left.split(' ').filter { it.isNotEmpty() }) {
            val supertype = SUPERTYPES[word]
            val type = CARD_TYPES[word]
            when {
                supertype != null -> supertypes += supertype
                type != null -> types += type
                else -> return null
            }
        }
        val subtypes = right?.split(' ')?.filter { it.isNotEmpty() }?.map(::Subtype).orEmpty()
        return Parsed(supertypes, types, subtypes)
    }

    fun render(face: FaceDefinition): String {
        val left = (face.supertypes.map { s -> SUPERTYPES.entries.first { it.value == s }.key } +
            face.types.map { t -> CARD_TYPES.entries.first { it.value == t }.key }).joinToString(" ")
        return if (face.subtypes.isEmpty()) left else left + " — " + face.subtypes.joinToString(" ") { it.name }
    }

    private val SUPERTYPES = mapOf("Basic" to Supertype.Basic, "Legendary" to Supertype.Legendary, "Snow" to Supertype.Snow,
        "World" to Supertype.World)
    private val CARD_TYPES = mapOf("Artifact" to CardType.Artifact, "Battle" to CardType.Battle, "Creature" to CardType.Creature,
        "Enchantment" to CardType.Enchantment, "Instant" to CardType.Instant, "Kindred" to CardType.Kindred, "Land" to CardType.Land,
        "Planeswalker" to CardType.Planeswalker, "Sorcery" to CardType.Sorcery)
}

/** Reads a printed P/T, loyalty or defense ("2", "*", "1+*", "X"). */
object PrintedNumbers {
    fun parse(text: String?): PrintedNumber? = when {
        text == null -> null
        text == "*" -> PrintedNumber.Star()
        text == "X" -> PrintedNumber.X
        text.endsWith("+*") -> text.dropLast(2).toIntOrNull()?.let { PrintedNumber.Star(it) }
        else -> text.toIntOrNull()?.let { PrintedNumber.Fixed(it) }
    }

    fun render(number: PrintedNumber?): String? = when (number) {
        null -> null
        is PrintedNumber.Fixed -> number.value.toString()
        is PrintedNumber.Star -> if (number.plus == 0) "*" else "${number.plus}+*"
        PrintedNumber.X -> "X"
    }
}

/** Reads the characteristics of [card]; [ManaCost.parse] reads its mana cost. Throws when the printed values aren't
 * readable, since every real card's are. */
internal fun characteristics(card: OracleCard): FaceDefinition {
    val types = requireNotNull(TypeLine.parse(card.typeLine)) { "unreadable type line: ${card.typeLine}" }
    return FaceDefinition(
        name = card.name,
        manaCost = card.manaCost?.let { requireNotNull(ManaCost.parse(it)) { "unreadable mana cost: $it" } },
        colorIndicator = card.colorIndicator?.map { letter -> requireNotNull(arcanum.api.Color.of(letter)) { "unreadable colour indicator: ${card.colorIndicator}" } },
        supertypes = types.supertypes, types = types.types, subtypes = types.subtypes,
        power = PrintedNumbers.parse(card.power), toughness = PrintedNumbers.parse(card.toughness),
        loyalty = PrintedNumbers.parse(card.loyalty), defense = PrintedNumbers.parse(card.defense),
    )
}
