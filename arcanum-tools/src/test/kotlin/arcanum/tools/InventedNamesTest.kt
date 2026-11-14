package arcanum.tools

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The invented test cards of the parser and engine tests (AGENTS.md, "Arcanum libraries"), read from their sources: one name
 * has one definition within each suite, and no invented name is a real card's.
 *
 * The engine tests share one registry of invented cards by name (`Cards.invent`, `Cards.inventFaces`, `Cards.define`, the
 * generated cube texts and the hand-written cards), so two tests defining one name differently fail by test order, which
 * differs between the JVM and Wasm. Identical repeats are fine. Real names come from `~/projects/atw-cache/all-card-names.txt`
 * (written by set-work's setup.sh); without it that check is skipped with a note.
 */
class InventedNamesTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
    private val engine by lazy { InventedCardScan.scan(File(root, "arcanum-engine/src/commonTest"), engine = true, root) }
    private val parser by lazy { InventedCardScan.scan(File(root, "arcanum-parser/src/commonTest"), engine = false, root) }

    @Test
    fun `the engine tests define each invented card name once`() = assertOneDefinition(engine)

    @Test
    fun `the parser tests define each invented card name once`() = assertOneDefinition(parser)

    @Test
    fun `no invented card has a real card's name`() {
        val file = File(System.getProperty("user.home"), "projects/atw-cache/all-card-names.txt")
        if (!file.isFile) {
            println("InventedNamesTest: $file is missing (set-work's setup.sh writes it); real names not checked")
            return
        }
        val real = HashSet<String>()
        file.forEachLine { line -> if (line.isNotBlank()) { real += line; real += line.split(" // ") } }
        val clashes = (engine + parser).flatMap { def -> def.names().filter { it in real && it !in BASICS }.map { "\"$it\" at ${def.at}" } }
        assertEquals(emptyList(), clashes.distinct(), "invented card names that are real cards' names: rename the invented card")
    }

    private fun assertOneDefinition(defs: List<InventedCardScan.Definition>) {
        val conflicts = defs.groupBy { it.name }.filterValues { same -> same.distinctBy { it.content }.size > 1 }
            .map { (name, same) -> "\"$name\": " + same.distinctBy { it.content }.joinToString(" / ") { it.at } }
        assertEquals(emptyList(), conflicts, "invented card names defined twice differently: rename the card in the newer test")
    }

    private companion object {
        val BASICS = listOf("Plains", "Island", "Swamp", "Mountain", "Forest").flatMap { listOf(it, "Snow-Covered $it") }.toSet() + "Wastes"
    }
}

/** Finds invented card definitions in Kotlin test sources by their helper calls, with literal arguments evaluated. */
internal object InventedCardScan {
    /** One printed face; a field that isn't a literal (a call, an unknown template) holds its source text after [SOURCE]. */
    data class Face(
        val name: String?, val cost: String?, val type: String?, val text: String?, val pt: String?,
        val loyalty: String?, val defense: String?, val colorIndicator: String?,
    )

    /** A definition under [name]: a layout and faces, or (written by hand in the model) its source text. */
    data class Definition(val name: String, val content: Any, val faces: List<Face>, val at: String) {
        fun names(): List<String> = (listOf(name) + faces.mapNotNull { it.name }).filter { !it.startsWith(SOURCE) }.distinct()
    }

    const val SOURCE = "\u0001"

    private val CALL = Regex("""(?<![\w.])(?:arcanum\.(?:engine\.cards|parser)\.)?(Cards\.invent|Cards\.inventFaces|Cards\.define|card|OracleCard|face|CubeCardText|FaceDefinition)\s*\(""")
    private val FACE_CALL = Regex("""(?<![\w.])(?:arcanum\.parser\.)?(OracleCard|face)\s*\(""")
    private val NAMED = Regex("""^\s*([A-Za-z_]\w*)\s*=(?!=)\s*""")
    private val CONSTANT = Regex("""\bval\s+(\w+)\s*(?::\s*String\s*)?=\s*(?=")""")
    private val INVENT = listOf("name", "cost", "type", "text", "pt", "loyalty", "colorIndicator")
    private val ORACLE = listOf("name", "manaCost", "typeLine", "text", "power", "toughness", "loyalty", "defense", "colorIndicator")

    fun scan(dir: File, engine: Boolean, root: File): List<Definition> =
        dir.walk().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.flatMap { file ->
            val source = file.readText()
            val code = mask(source)
            val constants = constants(source, code)
            val out = mutableListOf<Definition>()
            var consumed = 0
            for (call in CALL.findAll(code)) {
                if (call.range.first < consumed) continue
                val before = code.substring(maxOf(0, call.range.first - 6), call.range.first)
                if (before.endsWith("fun ") || before.endsWith("class ")) continue
                val (args, end) = arguments(source, code, call.range.last + 1)
                val at = "${file.relativeTo(root).path}:${source.substring(0, call.range.first).count { it == '\n' } + 1}"
                val value = { s: String -> value(s, constants) }
                val definition: Definition? = when (val kind = call.groupValues[1]) {
                    "Cards.invent" -> if (!engine) null else face(INVENT, args, value).let { Definition(it.name ?: "", "Normal" to listOf(it), listOf(it), at) }
                    "Cards.inventFaces", "CubeCardText" -> if (!engine) null else {
                        consumed = end
                        val faces = args.drop(1).mapNotNull { arg ->
                            val argCode = mask(arg)
                            FACE_CALL.find(argCode)?.let { m ->
                                face(if (m.groupValues[1] == "OracleCard") ORACLE else INVENT, arguments(arg, argCode, m.range.last + 1).first, value)
                            }
                        }
                        val layout = args.firstOrNull()?.let(value)
                        if (faces.isEmpty() || layout == null || layout.startsWith(SOURCE)) null
                        else Definition(faces.joinToString(" // ") { it.name ?: SOURCE }, layout to faces, faces, at)
                    }
                    "Cards.define" -> if (!engine) null else {
                        consumed = end
                        Definition(args.firstOrNull()?.let(value) ?: SOURCE, "written" to normal(args.drop(1).joinToString(",")), emptyList(), at)
                    }
                    "FaceDefinition" -> if (!engine || file.name != "InventedCards.kt") null else {
                        consumed = end
                        val name = bind(args, listOf("name"))["name"]?.let(value) ?: SOURCE
                        Definition(name, "written" to normal(args.joinToString(",")), emptyList(), at)
                    }
                    "card", "OracleCard" -> if (engine) null else face(if (kind == "OracleCard") ORACLE else INVENT, args, value)
                        .let { Definition(it.name ?: "", it, listOf(it), at) }
                    else -> null
                }
                if (definition != null && definition.name.isNotEmpty() && SOURCE !in definition.name) out += definition
            }
            out
        }.toList()

    private fun face(params: List<String>, args: List<String>, value: (String) -> String?): Face {
        val bound = bind(args, params)
        fun get(key: String, default: String? = null) = bound[key]?.let(value) ?: default.takeIf { key !in bound }
        val pt = if (params === ORACLE) {
            val (p, t) = get("power") to get("toughness")
            if (p == null && t == null) null else "$p/$t"
        } else get("pt")
        return Face(get("name"), get(if (params === ORACLE) "manaCost" else "cost"), get(if (params === ORACLE) "typeLine" else "type"),
            get("text", ""), pt, get("loyalty"), get("defense"), get("colorIndicator"))
    }

    private fun bind(args: List<String>, params: List<String>): Map<String, String> {
        val bound = mutableMapOf<String, String>()
        var position = 0
        for (arg in args) {
            if (arg.isBlank()) continue
            val named = NAMED.find(arg)
            if (named != null) bound[named.groupValues[1]] = arg.substring(named.range.last + 1)
            else { params.getOrNull(position)?.let { bound[it] = arg }; position++ }
        }
        return bound
    }

    /** `val x = "literal"` in the file, for templates like `$tail`; a name given two values is left out. */
    private fun constants(source: String, code: String): Map<String, String> {
        val found = mutableMapOf<String, String?>()
        for (m in CONSTANT.findAll(code)) {
            val start = m.range.last + 1
            val end = literalEnd(source, start)
            val v = value(source.substring(start, end), emptyMap())?.takeIf { !it.startsWith(SOURCE) }
            val name = m.groupValues[1]
            found[name] = if (name in found && found[name] != v) null else v
        }
        @Suppress("UNCHECKED_CAST")
        return found.filterValues { it != null } as Map<String, String>
    }

    private fun literalEnd(s: String, start: Int): Int = if (s.startsWith("\"\"\"", start)) {
        var j = s.indexOf("\"\"\"", start + 3)
        while (j >= 0 && j + 3 < s.length && s[j + 3] == '"') j++
        if (j < 0) s.length else j + 3
    } else {
        var j = start + 1
        while (j < s.length && s[j] != '"') {
            when {
                s[j] == '\\' -> j += 2
                s.startsWith("\${", j) -> j = templateEnd(s, j + 2)
                else -> j++
            }
        }
        minOf(j + 1, s.length)
    }

    /** The index after the `}` closing a `${…}` template whose expression starts at [start] (it may hold string literals). */
    private fun templateEnd(s: String, start: Int): Int {
        var depth = 0
        var j = start
        while (j < s.length) {
            when (s[j]) {
                '"' -> { j = literalEnd(s, j); continue }
                '{' -> depth++
                '}' -> if (depth == 0) return j + 1 else depth--
            }
            j++
        }
        return s.length
    }

    /** A Kotlin argument of string literals joined by `+`, maybe `.trimIndent()`, evaluated as `Cards.invent` reads it (text
     * starting with a line break is trimmed); `null` for `null`; anything else its source text after [SOURCE]. */
    private fun value(src: String, constants: Map<String, String>): String? {
        var t = src.trim()
        if (t == "null") return null
        val asSource = SOURCE + normal(t)
        var trim = false
        if (t.endsWith(".trimIndent()")) { t = t.removeSuffix(".trimIndent()").trimEnd(); trim = true }
        val out = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            when {
                c.isWhitespace() || c == '+' -> i++
                t.startsWith("\"\"\"", i) -> {
                    val end = literalEnd(t, i)
                    if (!template(t.substring(i + 3, end - 3), raw = true, constants, out)) return asSource
                    i = end
                }
                c == '"' -> {
                    val end = literalEnd(t, i)
                    if (!template(t.substring(i + 1, end - 1), raw = false, constants, out)) return asSource
                    i = end
                }
                else -> return asSource
            }
        }
        val r = out.toString()
        return if (trim || r.startsWith("\n")) r.trimIndent() else r
    }

    /** Appends a string literal's body; false when it holds a template other than a known constant. */
    private fun template(body: String, raw: Boolean, constants: Map<String, String>, out: StringBuilder): Boolean {
        var j = 0
        while (j < body.length) {
            val c = body[j]
            if (!raw && c == '\\') {
                val n = body[j + 1]
                if (n == 'u') { out.append(body.substring(j + 2, j + 6).toInt(16).toChar()); j += 6; continue }
                out.append(when (n) { 'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'; else -> n }); j += 2; continue
            }
            if (c == '$' && j + 1 < body.length && (body[j + 1] == '{' || body[j + 1].isLetter() || body[j + 1] == '_')) {
                val braced = body[j + 1] == '{'
                val end = if (braced) (templateEnd(body, j + 2) - 1).coerceAtLeast(j + 2) else (j + 1 until body.length).firstOrNull { !body[it].isLetterOrDigit() && body[it] != '_' } ?: body.length
                val name = if (braced) body.substring(j + 2, end) else body.substring(j + 1, end)
                out.append(constants[name] ?: return false)
                j = if (braced) end + 1 else end
                continue
            }
            out.append(c); j++
        }
        return true
    }

    /** The arguments of the call whose `(` ends before [start], split at top-level commas, and the index after its `)`. */
    private fun arguments(source: String, code: String, start: Int): Pair<List<String>, Int> {
        val args = mutableListOf<String>()
        var depth = 0
        var from = start
        for (i in start until code.length) {
            when (code[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> if (depth == 0) { args += source.substring(from, i); return args to i + 1 } else depth--
                ',' -> if (depth == 0) { args += source.substring(from, i); from = i + 1 }
            }
        }
        return args to code.length
    }

    /** [s] with comments blanked and string and character literals' insides replaced, so code structure can be searched. */
    private fun mask(s: String): String {
        val out = s.toCharArray()
        var i = 0
        fun blank(from: Int, to: Int, keepLines: Boolean, with: Char = ' ') {
            for (k in from until minOf(to, s.length)) if (!(keepLines && s[k] == '\n')) out[k] = with
        }
        while (i < s.length) {
            when {
                s.startsWith("//", i) -> { val j = s.indexOf('\n', i).let { if (it < 0) s.length else it }; blank(i, j, false); i = j }
                s.startsWith("/*", i) -> { val j = s.indexOf("*/", i + 2).let { if (it < 0) s.length else it + 2 }; blank(i, j, true); i = j }
                s[i] == '"' -> { val j = literalEnd(s, i); val q = if (s.startsWith("\"\"\"", i)) 3 else 1; blank(i + q, j - q, true, '_'); i = j }
                s[i] == '\'' -> {
                    var j = i + 1
                    while (j < s.length && s[j] != '\'') { if (s[j] == '\\') j++; j++ }
                    blank(i + 1, j, false, '_'); i = j + 1
                }
                else -> i++
            }
        }
        return String(out)
    }

    private fun normal(s: String) = s.trim().replace(Regex("""\s+"""), " ")
}
