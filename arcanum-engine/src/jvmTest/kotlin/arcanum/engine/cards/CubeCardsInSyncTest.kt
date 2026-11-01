package arcanum.engine.cards

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The engine tests use the invented cube cards of the parser tests by name (D61). Their printed fields are copied from
 * the `card(…)` and `cardFaces(…)` calls of `arcanum-parser/…/cube/Chunk*Test.kt` into the generated
 * `commonTest/…/cards/CubeCardTexts.kt` (common test code can't read files). This test checks the copy is current;
 * `./scripts/gradle.sh :arcanum-engine:jvmTest -Parcanum.regenerate=true --tests '*CubeCardsInSyncTest*'` rewrites it.
 */
class CubeCardsInSyncTest {
    private val chunks = File("../arcanum-parser/src/commonTest/kotlin/arcanum/parser/cube")
    private val generated = File("src/commonTest/kotlin/arcanum/engine/cards/CubeCardTexts.kt")

    @Test
    fun generatedCardListMatchesTheParserCubeTests() {
        val cards = CubeCardExtractor.extract(chunks)
        assertTrue(cards.size >= 498, "found only ${cards.size} cube cards in $chunks")
        val expected = CubeCardExtractor.render(cards)
        if (System.getProperty("arcanum.regenerate") == "true") generated.writeText(expected)
        assertEquals(expected, generated.readText(), "CubeCardTexts.kt is out of date: regenerate it (see this test's KDoc)")
    }

    @Test
    fun everyCubeCardNameIsUnique() {
        val names = CubeCardExtractor.extract(chunks).map { card -> card.faces.joinToString(" // ") { it.name } }
        assertEquals(names.distinct(), names)
    }
}

/** Reads the cube test cards out of the parser tests' Kotlin source, without compiling it. */
object CubeCardExtractor {
    data class Face(
        val name: String, val cost: String?, val type: String, val text: String,
        val pt: String?, val loyalty: String?, val colorIndicator: String?,
    )

    data class Card(val layout: String, val faces: List<Face>, val chunk: String)

    private val PARAMETERS = listOf("name", "cost", "type", "text", "pt", "loyalty", "colorIndicator")

    fun extract(dir: File): List<Card> = dir.listFiles { f -> f.name.matches(Regex("Chunk\\d+.*Test\\.kt")) }!!
        .sortedBy { it.name }
        .flatMap { file -> extract(file.readText(), file.nameWithoutExtension) }

    fun extract(source: String, chunk: String): List<Card> {
        val multi = calls(source, "cardFaces")
        val inMulti = multi.flatMap { (start, end) -> calls(source.substring(start, end), "card").map { (s, e) -> (s + start) to (e + start) } }.toSet()
        val cards = mutableListOf<Pair<Int, Card>>()
        multi.forEach { (start, end) ->
            val body = source.substring(start, end)
            val layout = Regex("""layout\s*=\s*Layout\.(\w+)""").find(body)?.groupValues?.get(1) ?: error("no layout in $chunk")
            val faces = calls(body, "card").map { (s, e) -> face(body.substring(s, e), chunk) }
            cards += start to Card(layout, faces, chunk)
        }
        calls(source, "card").filter { it !in inMulti }.forEach { (start, end) ->
            cards += start to Card("Normal", listOf(face(source.substring(start, end), chunk)), chunk)
        }
        return cards.sortedBy { it.first }.map { it.second }
    }

    /** The argument spans (inside the parentheses) of every call `name(` not preceded by an identifier character or a
     * dot, skipping the function's own declaration. */
    private fun calls(source: String, name: String): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        Regex("""(?<![\w.])${Regex.escape(name)}\(""").findAll(source).forEach { match ->
            if (source.substring(maxOf(0, match.range.first - 4), match.range.first) == "fun ") return@forEach
            if (inStringOrComment(source, match.range.first)) return@forEach
            val open = match.range.last
            result += (open + 1) to closing(source, open)
        }
        return result
    }

    private fun inStringOrComment(source: String, index: Int): Boolean {
        var i = 0
        while (i < index) {
            when {
                source.startsWith("//", i) -> i = source.indexOf('\n', i).let { if (it < 0) source.length else it }
                source.startsWith("/*", i) -> i = source.indexOf("*/", i + 2) + 2
                source.startsWith("\"\"\"", i) -> i = source.indexOf("\"\"\"", i + 3) + 3
                source[i] == '"' -> i = endOfString(source, i)
                else -> i++
            }
            if (i > index) return true
        }
        return false
    }

    private fun endOfString(source: String, start: Int): Int {
        var i = start + 1
        while (source[i] != '"') i += if (source[i] == '\\') 2 else 1
        return i + 1
    }

    /** The index of the parenthesis closing the one at [open]. */
    private fun closing(source: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < source.length) {
            when {
                source.startsWith("\"\"\"", i) -> { i = source.indexOf("\"\"\"", i + 3) + 3; continue }
                source[i] == '"' -> { i = endOfString(source, i); continue }
                source[i] == '(' -> depth++
                source[i] == ')' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        error("unclosed call at $open")
    }

    private fun face(arguments: String, chunk: String): Face {
        val values = mutableMapOf<String, String?>()
        splitArguments(arguments).forEachIndexed { index, argument ->
            val named = Regex("""^\s*(\w+)\s*=\s*(.*)$""", RegexOption.DOT_MATCHES_ALL).find(argument)
            val (key, value) = if (named != null && named.groupValues[1] in PARAMETERS) named.groupValues[1] to named.groupValues[2]
                else PARAMETERS[index] to argument
            values[key] = literal(value.trim(), chunk)
        }
        val text = values["text"] ?: ""
        return Face(
            name = values["name"] ?: error("card without name in $chunk"),
            cost = values["cost"],
            type = values["type"] ?: error("card without type in $chunk"),
            // As the parser tests' card(): multi-line text is written indented, one rules line per source line.
            text = if (text.startsWith("\n")) text.trimIndent() else text,
            pt = values["pt"], loyalty = values["loyalty"], colorIndicator = values["colorIndicator"],
        )
    }

    private fun splitArguments(arguments: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        var i = 0
        while (i < arguments.length) {
            when {
                arguments.startsWith("\"\"\"", i) -> { i = arguments.indexOf("\"\"\"", i + 3) + 3; continue }
                arguments[i] == '"' -> { i = endOfString(arguments, i); continue }
                arguments[i] == '(' || arguments[i] == '[' || arguments[i] == '{' -> depth++
                arguments[i] == ')' || arguments[i] == ']' || arguments[i] == '}' -> depth--
                arguments[i] == ',' && depth == 0 -> { parts += arguments.substring(start, i); start = i + 1 }
            }
            i++
        }
        parts += arguments.substring(start)
        return parts.filter { it.isNotBlank() }
    }

    private fun literal(value: String, chunk: String): String? = when {
        value == "null" -> null
        value.startsWith("\"\"\"") && value.endsWith("\"\"\"") -> value.removeSurrounding("\"\"\"").also {
            require('$' !in it) { "template in a card text in $chunk" }
        }
        value.startsWith("\"") && value.endsWith("\"") -> unescape(value.substring(1, value.length - 1), chunk)
        else -> error("card argument isn't a literal in $chunk: $value")
    }

    private fun unescape(text: String, chunk: String): String = buildString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            require(c != '$') { "template in a card field in $chunk: $text" }
            if (c == '\\') {
                when (val e = text[i + 1]) {
                    'n' -> append('\n'); 't' -> append('\t'); '"' -> append('"'); '\\' -> append('\\'); '\'' -> append('\'')
                    '$' -> append('$')
                    'u' -> { append(text.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                    else -> error("unknown escape \\$e in $chunk")
                }
                i += 2
            } else { append(c); i++ }
        }
    }

    fun render(cards: List<Card>): String = buildString {
        appendLine("// Generated from arcanum-parser/src/commonTest/kotlin/arcanum/parser/cube/Chunk*Test.kt by CubeCardsInSyncTest (jvmTest).")
        appendLine("// Do not edit: ./scripts/gradle.sh :arcanum-engine:jvmTest -Parcanum.regenerate=true --tests '*CubeCardsInSyncTest*'")
        appendLine("package arcanum.engine.cards")
        appendLine()
        appendLine("/** The invented cube cards of the parser tests (D15, D46): printed fields as written there, by chunk. */")
        appendLine("internal object CubeCardTexts {")
        val byChunk = cards.groupBy { it.chunk }
        appendLine("    val all: List<CubeCardText> by lazy {")
        appendLine("        listOf(")
        byChunk.keys.forEach { appendLine("            ${functionName(it)}(),") }
        appendLine("        ).flatten()")
        appendLine("    }")
        byChunk.forEach { (chunk, list) ->
            appendLine()
            appendLine("    private fun ${functionName(chunk)}() = listOf(")
            list.forEach { card ->
                appendLine("        CubeCardText(")
                appendLine("            \"${card.layout}\",")
                card.faces.forEach { f ->
                    val args = listOf("\"${escape(f.name)}\"", f.cost.quoted(), "\"${escape(f.type)}\"", "\"${escape(f.text)}\"",
                        f.pt.quoted(), f.loyalty.quoted(), f.colorIndicator.quoted())
                    appendLine("            face(${args.joinToString(", ")}),")
                }
                appendLine("        ),")
            }
            appendLine("    )")
        }
        appendLine("}")
    }

    private fun functionName(chunk: String) = chunk.replaceFirstChar { it.lowercase() }.removeSuffix("Test")

    private fun String?.quoted(): String = if (this == null) "null" else "\"${escape(this)}\""

    private fun escape(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n")
}
