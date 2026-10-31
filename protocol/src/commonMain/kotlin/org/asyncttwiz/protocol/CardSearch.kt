package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/*
 * Scryfall-style card search shared by every client: the parser turns query text into a tree, the matcher
 * evaluates it against plain card data. Storage layers may prefilter with their own indexes but the matcher
 * decides. Nothing here touches a database or a platform API.
 */

/** Colours as a bitmask: W=1, U=2, B=4, R=8, G=16 (DraftColor ordinals). */
object CardColors {
    const val ALL = 31
    /** Letters such as "WU" or provider arrays such as ["W","U"]; unknown letters are ignored. */
    fun mask(letters: Iterable<String>): Int = letters.fold(0) { mask, letter ->
        mask or (DraftColor.entries.firstOrNull { it.name.equals(letter.trim(), ignoreCase = true) }?.let { 1 shl it.ordinal } ?: 0)
    }
    fun count(mask: Int): Int = (mask and ALL).countOneBits()
}

/** One face as printed. Colours are the face's own colours (colour indicators and devoid included). */
@Serializable
data class CardSearchFace(
    val name: String,
    val typeLine: String = "",
    val text: String = "",
    val manaCost: String = "",
    val colors: Int = 0,
    val power: String? = null,
    val toughness: String? = null,
    val loyalty: String? = null,
)

/** One printing's searchable traits: lower-case set code and provider rarity (common, uncommon, …). */
@Serializable
data class CardSearchPrinting(val set: String, val rarity: String)

/**
 * One card (oracle identity). Faces are in printed order, front first. [legal] holds the formats where the
 * card is legal or restricted. [printings] are distinct set/rarity pairs, preferred printing first.
 */
@Serializable
data class CardSearchCard(
    val name: String,
    val layout: String = "normal",
    val faces: List<CardSearchFace>,
    val identity: Int = 0,
    val legal: Set<String> = emptySet(),
    val printings: List<CardSearchPrinting> = emptyList(),
) {
    /** Mana value from the mana cost: split and aftermath cards add both halves, other cards use the front face. */
    val manaValue: Double get() = CardSearchRules.manaValue(layout, faces)
    /** Colour sets compared by c: — one per face for double-faced layouts, otherwise all faces together. */
    val colorUnits: List<Int> get() = CardSearchRules.colorUnits(layout, faces)
    val extra: Boolean get() = layout in CardSearchRules.EXTRA_LAYOUTS
}

object CardSearchRules {
    /** Shown only with include:extras (or is:<layout>), as on Scryfall. */
    val EXTRA_LAYOUTS = setOf("planar", "scheme", "vanguard")
    /** Each face is a separate object with its own colours; other multi-part cards combine their parts. */
    val FACE_COLOR_LAYOUTS = setOf("transform", "modal_dfc", "meld", "reversible_card")
    private val COMBINED_COST_LAYOUTS = setOf("split", "aftermath")

    /** Formats known to the provider data, in a fixed order (storage may use it for bitmasks). */
    val FORMATS = listOf("standard", "future", "historic", "timeless", "gladiator", "pioneer", "modern", "legacy",
        "pauper", "vintage", "penny", "commander", "oathbreaker", "standardbrawl", "brawl", "alchemy",
        "paupercommander", "duel", "oldschool", "premodern", "predh", "competitivebrawl")
    internal val FORMAT_ALIASES = mapOf("edh" to "commander", "pdh" to "paupercommander", "historicbrawl" to "brawl")

    /** Scryfall's rarity order, used by r< and r>. */
    val RARITIES = listOf("common", "uncommon", "rare", "special", "mythic", "bonus")

    /** `is:` values: every playable provider layout plus Scryfall's multi-face shorthands. */
    val IS_VALUES: Map<String, Set<String>> = buildMap {
        listOf("normal", "split", "aftermath", "flip", "transform", "modal_dfc", "meld", "leveler", "class", "case", "saga",
            "adventure", "mutate", "prototype", "augment", "host", "planar", "scheme", "vanguard", "reversible_card", "prepare")
            .forEach { put(it, setOf(it)) }
        put("split", setOf("split", "aftermath"))
        put("dfc", setOf("transform", "modal_dfc", "meld"))
        put("mdfc", setOf("modal_dfc"))
        put("tdfc", setOf("transform"))
    }

    fun manaValue(layout: String, faces: List<CardSearchFace>): Double =
        if (layout in COMBINED_COST_LAYOUTS) faces.sumOf { PodDeckManaCost.parse(it.manaCost).value }
        else faces.firstOrNull()?.let { PodDeckManaCost.parse(it.manaCost).value } ?: 0.0

    fun colorUnits(layout: String, faces: List<CardSearchFace>): List<Int> =
        if (layout in FACE_COLOR_LAYOUTS && faces.isNotEmpty()) faces.map { it.colors }
        else listOf(faces.fold(0) { mask, face -> mask or face.colors })

    /** Numeric reading of a printed power/toughness/loyalty: "*" and "X" are 0, "1+*" is 1, "∞" is infinite. */
    fun statValue(raw: String?): Double? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        value.toDoubleOrNull()?.let { return it }
        if ('∞' in value) return Double.POSITIVE_INFINITY
        return Regex("[-+]?[0-9]+(?:\\.[0-9]+)?").find(value)?.value?.toDoubleOrNull() ?: 0.0
    }

    /** Case-insensitive, accent-insensitive text for name matching (Æther = aether, Lim-Dûl = lim-dul). */
    fun foldName(text: String): String {
        val lower = text.lowercase()
        val out = StringBuilder(lower.length)
        for (c in lower) when (c) {
            'á', 'à', 'â', 'ä', 'ã', 'å', 'ā' -> out.append('a')
            'é', 'è', 'ê', 'ë', 'ē' -> out.append('e')
            'í', 'ì', 'î', 'ï', 'ī' -> out.append('i')
            'ó', 'ò', 'ô', 'ö', 'õ', 'ø', 'ō' -> out.append('o')
            'ú', 'ù', 'û', 'ü', 'ū' -> out.append('u')
            'ý', 'ÿ' -> out.append('y')
            'ñ' -> out.append('n')
            'ç' -> out.append('c')
            'æ' -> out.append("ae")
            'œ' -> out.append("oe")
            'ß' -> out.append("ss")
            '’', '‘' -> out.append('\'')
            else -> out.append(c)
        }
        return out.toString()
    }

    /** Rules and type text compare case-insensitively; typographic apostrophes equal straight ones. */
    fun foldText(text: String): String = text.lowercase().replace('’', '\'').replace('‘', '\'')
}

enum class CardSearchOp(val symbol: String) { EQ("="), NE("!="), LT("<"), LE("<="), GT(">"), GE(">=") }
enum class CardSearchStat { MANA_VALUE, POWER, TOUGHNESS, LOYALTY }

/** The parsed query. Leaves hold normalized values (folded text, lower-case codes, resolved operators). */
sealed interface CardSearchNode {
    data class And(val terms: List<CardSearchNode>) : CardSearchNode
    data class Or(val terms: List<CardSearchNode>) : CardSearchNode
    data class Not(val term: CardSearchNode) : CardSearchNode
    /** Bare words and quoted phrases: the name contains [text]; `!name` must equal the full or a face name. */
    data class Name(val text: String, val exact: Boolean = false) : CardSearchNode
    data class TypeLine(val text: String) : CardSearchNode
    /** `~` in [text] stands for the face's own name. */
    data class OracleText(val text: String) : CardSearchNode
    /** Colour [mask] compared with [op]; [count] instead compares the number of colours. */
    data class Colors(val identity: Boolean, val op: CardSearchOp, val mask: Int = 0, val count: Int? = null) : CardSearchNode
    /** Compare a stat with a number, or with [other] on the same face (pow>tou). */
    data class Stat(val stat: CardSearchStat, val op: CardSearchOp, val value: Double = 0.0, val other: CardSearchStat? = null) : CardSearchNode
    /** Printing-level: index into CardSearchRules.RARITIES. */
    data class Rarity(val op: CardSearchOp, val rank: Int) : CardSearchNode
    /** Printing-level: lower-case set code. */
    data class SetCode(val code: String) : CardSearchNode
    data class Format(val format: String) : CardSearchNode
    data class Layout(val layouts: Set<String>) : CardSearchNode
}

data class CardSearchError(val message: String, val start: Int, val end: Int)

sealed interface CardSearchParse {
    data class Valid(val query: CardSearchQuery) : CardSearchParse
    data class Invalid(val error: CardSearchError) : CardSearchParse
}

class CardSearchQuery(val root: CardSearchNode?, val includeExtras: Boolean = false) {
    /** True when the query names set or rarity, which belong to a single printing. */
    val printingSpecific: Boolean = root?.let(::printingLevel) ?: false

    fun matches(card: CardSearchCard): Boolean = match(card) != null

    /** Null when the card does not match; otherwise the index in [CardSearchCard.printings] of the first printing
     * that satisfies the query (0 when the query has no printing terms). */
    fun match(card: CardSearchCard): Int? {
        if (card.extra && !includeExtras) return null
        val node = root ?: return 0
        if (!printingSpecific || card.printings.isEmpty()) return if (eval(node, card, null)) 0 else null
        card.printings.forEachIndexed { index, printing -> if (eval(node, card, printing)) return index }
        return null
    }

    private fun printingLevel(node: CardSearchNode): Boolean = when (node) {
        is CardSearchNode.And -> node.terms.any(::printingLevel)
        is CardSearchNode.Or -> node.terms.any(::printingLevel)
        is CardSearchNode.Not -> printingLevel(node.term)
        is CardSearchNode.Rarity, is CardSearchNode.SetCode -> true
        else -> false
    }

    private fun eval(node: CardSearchNode, card: CardSearchCard, printing: CardSearchPrinting?): Boolean = when (node) {
        is CardSearchNode.And -> node.terms.all { eval(it, card, printing) }
        is CardSearchNode.Or -> node.terms.any { eval(it, card, printing) }
        is CardSearchNode.Not -> !eval(node.term, card, printing)
        is CardSearchNode.Name -> {
            val full = CardSearchRules.foldName(card.name)
            if (node.exact) full == node.text || card.faces.any { CardSearchRules.foldName(it.name) == node.text }
            else node.text in full
        }
        is CardSearchNode.TypeLine -> card.faces.any { node.text in CardSearchRules.foldText(it.typeLine) }
        is CardSearchNode.OracleText -> card.faces.any { face ->
            val needle = if ('~' in node.text) node.text.replace("~", CardSearchRules.foldText(face.name)) else node.text
            needle in CardSearchRules.foldText(face.text)
        }
        is CardSearchNode.Colors -> {
            val units = if (node.identity) listOf(card.identity) else card.colorUnits
            units.any { mask -> if (node.count != null) compare(CardColors.count(mask).toDouble(), node.op, node.count.toDouble())
                else compareColors(mask, node.op, node.mask) }
        }
        is CardSearchNode.Stat -> if (node.stat == CardSearchStat.MANA_VALUE) compare(card.manaValue, node.op, node.value)
            else card.faces.any { face ->
                val left = stat(face, node.stat)
                val right = node.other?.let { stat(face, it) } ?: node.value
                left != null && right != null && compare(left, node.op, right)
            }
        is CardSearchNode.Rarity -> printing != null && CardSearchRules.RARITIES.indexOf(printing.rarity.lowercase())
            .let { it >= 0 && compare(it.toDouble(), node.op, node.rank.toDouble()) }
        is CardSearchNode.SetCode -> printing != null && printing.set.lowercase() == node.code
        is CardSearchNode.Format -> node.format in card.legal
        is CardSearchNode.Layout -> card.layout in node.layouts
    }

    private fun stat(face: CardSearchFace, stat: CardSearchStat): Double? = when (stat) {
        CardSearchStat.POWER -> CardSearchRules.statValue(face.power)
        CardSearchStat.TOUGHNESS -> CardSearchRules.statValue(face.toughness)
        CardSearchStat.LOYALTY -> CardSearchRules.statValue(face.loyalty)
        CardSearchStat.MANA_VALUE -> null
    }

    companion object {
        const val MAX_LENGTH = 1000
        fun parse(text: String): CardSearchParse = CardSearchParser(text).parse()

        fun compare(left: Double, op: CardSearchOp, right: Double): Boolean = when (op) {
            CardSearchOp.EQ -> left == right
            CardSearchOp.NE -> left != right
            CardSearchOp.LT -> left < right
            CardSearchOp.LE -> left <= right
            CardSearchOp.GT -> left > right
            CardSearchOp.GE -> left >= right
        }

        /** Set comparison: <= subset, >= superset, = same colours. */
        fun compareColors(card: Int, op: CardSearchOp, query: Int): Boolean {
            val subset = card and query.inv() and CardColors.ALL == 0
            val superset = query and card.inv() and CardColors.ALL == 0
            return when (op) {
                CardSearchOp.EQ -> subset && superset
                CardSearchOp.NE -> !(subset && superset)
                CardSearchOp.LE -> subset
                CardSearchOp.LT -> subset && !superset
                CardSearchOp.GE -> superset
                CardSearchOp.GT -> superset && !subset
            }
        }
    }
}

private class CardSearchParser(private val text: String) {
    private class Fail(val error: CardSearchError) : Exception(error.message)
    private enum class Kind { LPAREN, RPAREN, NOT, OR, AND, TERM }
    private class Token(val kind: Kind, val start: Int, val end: Int, val key: String? = null, val op: String? = null,
        val value: String = "", val quoted: Boolean = false, val exact: Boolean = false)

    private val tokens = mutableListOf<Token>()
    private var position = 0
    private var includeExtras = false
    private var terms = 0

    fun parse(): CardSearchParse = try {
        if (text.length > CardSearchQuery.MAX_LENGTH) fail("Search is too long.", 0, text.length)
        lex()
        val root = if (tokens.isEmpty()) null else parseOr(0)
        peek()?.let { fail("Unmatched )", it.start, it.end) }
        CardSearchParse.Valid(CardSearchQuery(root, includeExtras))
    } catch (failure: Fail) {
        CardSearchParse.Invalid(failure.error)
    }

    private fun fail(message: String, start: Int, end: Int): Nothing = throw Fail(CardSearchError(message, start, end))

    // Lexer ------------------------------------------------------------------------------------------

    private fun lex() {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> { tokens += Token(Kind.LPAREN, i, i + 1); i++ }
                c == ')' -> { tokens += Token(Kind.RPAREN, i, i + 1); i++ }
                c == '-' && i + 1 < text.length && !text[i + 1].isWhitespace() && text[i + 1] != ')' -> {
                    tokens += Token(Kind.NOT, i, i + 1); i++
                }
                c == '-' -> fail("Nothing to exclude after -", i, i + 1)
                c == '!' && text.getOrNull(i + 1) != '=' -> {
                    val (value, quoted, end) = value(i + 1)
                    if (value.isBlank()) fail("Write a card name after !", i, end)
                    tokens += Token(Kind.TERM, i, end, value = value, quoted = quoted, exact = true); i = end
                }
                c == '"' -> {
                    val (value, _, end) = value(i)
                    tokens += Token(Kind.TERM, i, end, value = value, quoted = true); i = end
                }
                else -> {
                    val keyed = Regex("([A-Za-z]+)(!=|<=|>=|<|>|:|=)").matchAt(text, i)
                    if (keyed != null) {
                        val after = keyed.range.last + 1
                        val (value, quoted, end) = value(after)
                        tokens += Token(Kind.TERM, i, end, keyed.groupValues[1].lowercase(), keyed.groupValues[2], value, quoted)
                        i = end
                    } else {
                        val end = wordEnd(i)
                        val word = text.substring(i, end)
                        tokens += when (word.lowercase()) {
                            "or" -> Token(Kind.OR, i, end)
                            "and" -> Token(Kind.AND, i, end)
                            else -> Token(Kind.TERM, i, end, value = word)
                        }
                        i = end
                    }
                }
            }
        }
    }

    private fun wordEnd(from: Int): Int {
        var end = from
        while (end < text.length && !text[end].isWhitespace() && text[end] != '(' && text[end] != ')') end++
        return end
    }

    /** A quoted value (an unclosed quote runs to the end, so typing stays valid) or a bare word. */
    private fun value(from: Int): Triple<String, Boolean, Int> {
        if (text.getOrNull(from) != '"') return wordEnd(from).let { Triple(text.substring(from, it), false, it) }
        val out = StringBuilder()
        var i = from + 1
        while (i < text.length && text[i] != '"') {
            if (text[i] == '\\' && i + 1 < text.length && text[i + 1] == '"') i++
            out.append(text[i]); i++
        }
        return Triple(out.toString(), true, minOf(i + 1, text.length))
    }

    // Parser -----------------------------------------------------------------------------------------

    private fun peek(): Token? = tokens.getOrNull(position)
    private fun next(): Token = tokens[position++]

    private fun parseOr(depth: Int): CardSearchNode {
        val alternatives = mutableListOf(parseAnd(depth))
        while (peek()?.kind == Kind.OR) {
            val or = next()
            val following = peek()
            if (following == null || following.kind == Kind.RPAREN || following.kind == Kind.OR)
                fail("Write a search term after or", or.start, or.end)
            alternatives += parseAnd(depth)
        }
        return alternatives.singleOrNull() ?: CardSearchNode.Or(alternatives)
    }

    private fun parseAnd(depth: Int): CardSearchNode {
        val terms = mutableListOf<CardSearchNode>()
        while (true) {
            val token = peek() ?: break
            if (token.kind == Kind.RPAREN || token.kind == Kind.OR) break
            if (token.kind == Kind.AND) {
                next()
                val following = peek()
                if (terms.isEmpty() || following == null || following.kind in setOf(Kind.RPAREN, Kind.OR, Kind.AND))
                    fail("Write a search term on both sides of and", token.start, token.end)
                continue
            }
            terms += parseUnary(depth)
        }
        if (terms.isEmpty()) {
            val token = peek()
            when (token?.kind) {
                Kind.OR -> fail("Write a search term before or", token.start, token.end)
                Kind.RPAREN -> fail(if (depth == 0) "Unmatched )" else "Empty parentheses", token.start, token.end)
                else -> fail("Write a search term", text.length, text.length)
            }
        }
        return terms.singleOrNull() ?: CardSearchNode.And(terms)
    }

    private fun parseUnary(depth: Int): CardSearchNode {
        val token = next()
        return when (token.kind) {
            Kind.NOT -> {
                if (peek() == null) fail("Nothing to exclude after -", token.start, token.end)
                val operand = peek()!!
                if (operand.kind == Kind.OR || operand.kind == Kind.AND) fail("Nothing to exclude after -", token.start, token.end)
                CardSearchNode.Not(parseUnary(depth))
            }
            Kind.LPAREN -> {
                if (depth >= 16) fail("Too many nested parentheses", token.start, token.end)
                val inner = parseOr(depth + 1)
                // An unclosed parenthesis at the end closes itself, so partial queries still search.
                if (peek()?.kind == Kind.RPAREN) next()
                inner
            }
            Kind.RPAREN -> fail("Unmatched )", token.start, token.end)
            Kind.TERM -> {
                if (++terms > 100) fail("Too many search terms", token.start, token.end)
                term(token)
            }
            Kind.OR, Kind.AND -> fail("Write a search term", token.start, token.end)
        }
    }

    // Terms ------------------------------------------------------------------------------------------

    private fun term(token: Token): CardSearchNode {
        val value = token.value.replace(Regex("\\s+"), " ").trim()
        val key = token.key ?: return if (value.isEmpty()) fail("Write a card name", token.start, token.end)
            else CardSearchNode.Name(CardSearchRules.foldName(value), token.exact)
        val raw = token.op!!
        if (key !in KEYS) fail("Unknown filter \"$key$raw\"", token.start, token.end)
        if (value.isEmpty()) fail("Write a value after $key$raw", token.start, token.end)
        fun op(colon: CardSearchOp): CardSearchOp = when (raw) {
            ":", "=" -> if (raw == ":") colon else CardSearchOp.EQ
            "!=" -> CardSearchOp.NE
            "<" -> CardSearchOp.LT
            "<=" -> CardSearchOp.LE
            ">" -> CardSearchOp.GT
            else -> CardSearchOp.GE
        }
        fun equalityOnly(node: CardSearchNode): CardSearchNode = when (raw) {
            ":", "=" -> node
            "!=" -> CardSearchNode.Not(node)
            else -> fail("$key: compares only with : or =", token.start, token.end)
        }
        return when (key) {
            "t", "type" -> equalityOnly(CardSearchNode.TypeLine(CardSearchRules.foldText(value)))
            "o", "oracle" -> equalityOnly(CardSearchNode.OracleText(CardSearchRules.foldText(value)))
            "c", "color", "colors", "colour", "colours" -> colors(token, value, identity = false, op(CardSearchOp.GE))
            "id", "identity", "ci" -> colors(token, value, identity = true, op(CardSearchOp.LE))
            "mv", "cmc", "manavalue" -> stat(token, CardSearchStat.MANA_VALUE, value, op(CardSearchOp.EQ))
            "pow", "power" -> stat(token, CardSearchStat.POWER, value, op(CardSearchOp.EQ))
            "tou", "toughness" -> stat(token, CardSearchStat.TOUGHNESS, value, op(CardSearchOp.EQ))
            "loy", "loyalty" -> stat(token, CardSearchStat.LOYALTY, value, op(CardSearchOp.EQ))
            "r", "rarity" -> {
                val rank = rarity(value) ?: fail("Unknown rarity \"$value\". Use common, uncommon, rare, mythic, special or bonus.",
                    token.start, token.end)
                CardSearchNode.Rarity(op(CardSearchOp.EQ), rank)
            }
            "s", "set", "e", "edition" -> {
                if (!Regex("[A-Za-z0-9]{1,16}").matches(value)) fail("Write a set code, like s:neo", token.start, token.end)
                equalityOnly(CardSearchNode.SetCode(value.lowercase()))
            }
            "f", "format", "legal" -> {
                val lower = value.lowercase()
                val format = CardSearchRules.FORMAT_ALIASES[lower] ?: lower.takeIf { it in CardSearchRules.FORMATS }
                    ?: fail("Unknown format \"$value\"", token.start, token.end)
                equalityOnly(CardSearchNode.Format(format))
            }
            "is" -> {
                val lower = value.lowercase()
                val layouts = CardSearchRules.IS_VALUES[lower] ?: fail("Unknown is: value \"$value\"", token.start, token.end)
                if (layouts.any { it in CardSearchRules.EXTRA_LAYOUTS }) includeExtras = true
                equalityOnly(CardSearchNode.Layout(layouts))
            }
            "include" -> {
                if (value.lowercase() != "extras" || raw != ":" && raw != "=") fail("Use include:extras", token.start, token.end)
                includeExtras = true
                CardSearchNode.And(emptyList())
            }
            else -> fail("Unknown filter \"$key$raw\"", token.start, token.end)
        }
    }

    private fun rarity(value: String): Int? {
        val lower = value.lowercase()
        return CardSearchRules.RARITIES.indexOfFirst { it == lower || it.first().toString() == lower }.takeIf { it >= 0 }
    }

    private fun stat(token: Token, stat: CardSearchStat, value: String, op: CardSearchOp): CardSearchNode {
        val other = when (value.lowercase()) {
            "pow", "power" -> CardSearchStat.POWER
            "tou", "toughness" -> CardSearchStat.TOUGHNESS
            "loy", "loyalty" -> CardSearchStat.LOYALTY
            else -> null
        }
        if (other != null) {
            if (stat == CardSearchStat.MANA_VALUE) fail("Compare mana value with a number", token.start, token.end)
            return CardSearchNode.Stat(stat, op, other = other)
        }
        val number = value.toDoubleOrNull()?.takeIf { it.isFinite() } ?: fail("Write a number after ${token.key}${token.op}", token.start, token.end)
        return CardSearchNode.Stat(stat, op, number)
    }

    private fun colors(token: Token, value: String, identity: Boolean, op: CardSearchOp): CardSearchNode {
        val lower = value.lowercase()
        lower.toIntOrNull()?.let { count ->
            if (count !in 0..5) fail("A card has 0 to 5 colours", token.start, token.end)
            return CardSearchNode.Colors(identity, if (token.op == ":") CardSearchOp.EQ else op, count = count)
        }
        if (lower == "m" || lower == "multicolor" || lower == "multicolour") return when (token.op) {
            ":", "=" -> CardSearchNode.Colors(identity, CardSearchOp.GE, count = 2)
            "!=" -> CardSearchNode.Colors(identity, CardSearchOp.LT, count = 2)
            else -> fail("Use c:m or -c:m for multicoloured cards", token.start, token.end)
        }
        val mask = COLOR_NAMES[lower] ?: lower.fold(0) { mask, letter ->
            val bit = "wubrg".indexOf(letter)
            if (bit < 0) fail("Unknown colour \"$value\". Use letters w u b r g, c for colourless or m for multicoloured.",
                token.start, token.end)
            mask or (1 shl bit)
        }
        // c:c means colourless (Scryfall), not "at least no colours".
        val resolved = if (mask == 0 && token.op == ":") CardSearchOp.EQ else op
        return CardSearchNode.Colors(identity, resolved, mask)
    }

    private companion object {
        val KEYS = setOf("t", "type", "o", "oracle", "c", "color", "colors", "colour", "colours", "id", "identity", "ci",
            "mv", "cmc", "manavalue", "pow", "power", "tou", "toughness", "loy", "loyalty", "r", "rarity",
            "s", "set", "e", "edition", "f", "format", "legal", "is", "include")
        val COLOR_NAMES: Map<String, Int> = run {
            fun m(letters: String) = letters.fold(0) { mask, letter -> mask or (1 shl "wubrg".indexOf(letter)) }
            mapOf("c" to 0, "colorless" to 0, "colourless" to 0,
                "white" to m("w"), "blue" to m("u"), "black" to m("b"), "red" to m("r"), "green" to m("g"),
                "azorius" to m("wu"), "dimir" to m("ub"), "rakdos" to m("br"), "gruul" to m("rg"), "selesnya" to m("gw"),
                "orzhov" to m("wb"), "izzet" to m("ur"), "golgari" to m("bg"), "boros" to m("rw"), "simic" to m("gu"),
                "bant" to m("gwu"), "esper" to m("wub"), "grixis" to m("ubr"), "jund" to m("brg"), "naya" to m("rgw"),
                "abzan" to m("wbg"), "jeskai" to m("urw"), "sultai" to m("bgu"), "mardu" to m("rwb"), "temur" to m("gur"),
                "glint" to m("ubrg"), "dune" to m("wbrg"), "ink" to m("wurg"), "witch" to m("wubg"), "yore" to m("wubr"),
                "wubrg" to m("wubrg"), "rainbow" to m("wubrg"))
        }
    }
}
