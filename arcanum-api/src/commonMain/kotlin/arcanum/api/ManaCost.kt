package arcanum.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** The five colours (CR 105.1), with their mana symbol letters. Serialised as the letter ("W"). */
@Serializable(with = ColorSerializer::class)
sealed class Color(val symbol: Char) {
    data object White : Color('W')
    data object Blue : Color('U')
    data object Black : Color('B')
    data object Red : Color('R')
    data object Green : Color('G')

    companion object {
        /** All five, in the order W U B R G. Lazy: when a colour object is used before this class, the colours are not
         * all initialised yet while this companion is. */
        val all: List<Color> by lazy { listOf(White, Blue, Black, Red, Green) }
        fun of(symbol: Char): Color? = all.firstOrNull { it.symbol == symbol }
    }
}

/** One mana symbol (CR 107.4). Written as in oracle text by [ManaCost.toString]. */
sealed interface ManaSymbol {
    /** {0}, {1}, {2}, … (CR 107.4b) */
    data class Generic(val amount: Int) : ManaSymbol
    /** {W} {U} {B} {R} {G} (CR 107.4a) */
    data class Colored(val color: Color) : ManaSymbol
    /** {C}: specifically colourless mana (CR 107.4c) */
    data object Colorless : ManaSymbol
    /** {S}: snow mana (CR 107.4h) */
    data object Snow : ManaSymbol
    /** {X}, {Y}, {Z} (CR 107.3) */
    data class Variable(val name: Char) : ManaSymbol
    /** {W/U} and the other two-colour hybrid symbols (CR 107.4e) */
    data class Hybrid(val first: Color, val second: Color) : ManaSymbol
    /** {2/W} and the other monocoloured hybrid symbols (CR 107.4e) */
    data class GenericHybrid(val amount: Int, val color: Color) : ManaSymbol
    /** {C/W} and the other colourless hybrid symbols: pay {C} or the colour (CR 107.4) */
    data class ColorlessHybrid(val color: Color) : ManaSymbol
    /** {W/P} and the other Phyrexian symbols (CR 107.4f) */
    data class Phyrexian(val color: Color) : ManaSymbol
    /** {W/U/P} and the other hybrid Phyrexian symbols (CR 107.4f) */
    data class PhyrexianHybrid(val first: Color, val second: Color) : ManaSymbol
}

/** A mana cost such as {2}{W}{W}: its symbols in printed order. Serialised as that printed text. */
@Serializable(with = ManaCostSerializer::class)
data class ManaCost(val symbols: List<ManaSymbol>) {
    override fun toString(): String = symbols.joinToString("") { "{${text(it)}}" }

    companion object {
        /** Parses printed mana symbols ("{2}{W/U}{G/P}"); null when [text] holds anything else or no symbols. */
        fun parse(text: String): ManaCost? {
            if (text.isEmpty() || !WHOLE.matches(text)) return null
            val symbols = SYMBOL.findAll(text).map { symbol(it.groupValues[1]) }.toList()
            return if (symbols.any { it == null }) null else ManaCost(symbols.filterNotNull())
        }

        private val WHOLE = Regex("""(?:\{[^{}]+\})+""")
        private val SYMBOL = Regex("""\{([^{}]+)\}""")

        private fun symbol(text: String): ManaSymbol? {
            text.toIntOrNull()?.let { return if (it >= 0) ManaSymbol.Generic(it) else null }
            val parts = text.split('/')
            return when (parts.size) {
                1 -> when (val c = text.singleOrNull()) {
                    'C' -> ManaSymbol.Colorless
                    'S' -> ManaSymbol.Snow
                    'X', 'Y', 'Z' -> ManaSymbol.Variable(c)
                    null -> null
                    else -> Color.of(c)?.let { ManaSymbol.Colored(it) }
                }
                2 -> {
                    val first = parts[0].singleOrNull()?.let(Color::of)
                    val second = parts[1].singleOrNull()?.let(Color::of)
                    when {
                        first != null && parts[1] == "P" -> ManaSymbol.Phyrexian(first)
                        first != null && second != null && first != second -> ManaSymbol.Hybrid(first, second)
                        parts[0] == "C" && second != null -> ManaSymbol.ColorlessHybrid(second)
                        second != null -> parts[0].toIntOrNull()?.let { ManaSymbol.GenericHybrid(it, second) }
                        else -> null
                    }
                }
                3 -> {
                    val first = parts[0].singleOrNull()?.let(Color::of)
                    val second = parts[1].singleOrNull()?.let(Color::of)
                    if (first != null && second != null && first != second && parts[2] == "P") {
                        ManaSymbol.PhyrexianHybrid(first, second)
                    } else null
                }
                else -> null
            }
        }

        private fun text(symbol: ManaSymbol): String = when (symbol) {
            is ManaSymbol.Generic -> symbol.amount.toString()
            is ManaSymbol.Colored -> symbol.color.symbol.toString()
            ManaSymbol.Colorless -> "C"
            ManaSymbol.Snow -> "S"
            is ManaSymbol.Variable -> symbol.name.toString()
            is ManaSymbol.Hybrid -> "${symbol.first.symbol}/${symbol.second.symbol}"
            is ManaSymbol.GenericHybrid -> "${symbol.amount}/${symbol.color.symbol}"
            is ManaSymbol.ColorlessHybrid -> "C/${symbol.color.symbol}"
            is ManaSymbol.Phyrexian -> "${symbol.color.symbol}/P"
            is ManaSymbol.PhyrexianHybrid -> "${symbol.first.symbol}/${symbol.second.symbol}/P"
        }
    }
}

internal object ManaCostSerializer : KSerializer<ManaCost> {
    override val descriptor = PrimitiveSerialDescriptor("arcanum.api.ManaCost", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ManaCost) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): ManaCost {
        val text = decoder.decodeString()
        return ManaCost.parse(text) ?: throw IllegalArgumentException("Not a mana cost: $text")
    }
}

internal object ColorSerializer : KSerializer<Color> {
    override val descriptor = PrimitiveSerialDescriptor("arcanum.api.Color", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Color) = encoder.encodeString(value.symbol.toString())
    override fun deserialize(decoder: Decoder): Color {
        val text = decoder.decodeString()
        return text.singleOrNull()?.let(Color::of) ?: throw IllegalArgumentException("Not a colour: $text")
    }
}
