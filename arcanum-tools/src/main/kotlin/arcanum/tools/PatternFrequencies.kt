package arcanum.tools

import arcanum.parser.OracleFace
import arcanum.api.Ability
import arcanum.parser.OracleNormalizer
import arcanum.parser.OracleParser
import arcanum.parser.Pattern
import arcanum.parser.PatternKind
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Counts normalised oracle-text patterns ([OracleNormalizer]) over every card face in a local MTGJSON file and writes
 * them, most frequent first, as TSV. Reads AllPrintings (data → set → cards) or AtomicCards (data → name → faces).
 * Skips Un-cards (isFunny), online-only printings and rebalanced Alchemy cards. Also runs [OracleParser] over every
 * face and reports how much it reads, with the keyword-like lines it still leaves unparsed in a second file next to
 * the first (unparsed-keywords.tsv). Args: <mtgjson file> <output tsv>.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: PatternFrequencies <MTGJSON .json or .json.gz> <output .tsv>" }
    val faces = readFaces(File(args[0]))
    val creatureTypes = faces.values.filter { it.creature }.flatMap { it.subtypes }.toSet()
    val counts = HashMap<Pattern, Int>()
    val examples = HashMap<Pattern, String>()
    val facePatterns = faces.values.map { face ->
        OracleNormalizer.patterns(OracleFace(face.name, face.text, face.keywords), creatureTypes).also { list ->
            list.forEach { counts.merge(it, 1, Int::plus); examples.putIfAbsent(it, face.name) }
        }
    }
    val ranked = counts.entries.sortedWith(compareByDescending<Map.Entry<Pattern, Int>> { it.value }.thenBy { it.key.text })
    val rank = ranked.withIndex().associate { (index, entry) -> entry.key to index + 1 }
    val lines = counts.values.sum()
    val withText = facePatterns.filter { it.isNotEmpty() }
    var abilities = 0
    var parsed = 0
    var facesParsed = 0
    val unparsedKeywords = HashMap<String, Int>()
    faces.values.filter { it.text.isNotBlank() }.forEach { face ->
        val definition = OracleParser.parse(face.name, face.text)
        val unparsed = definition.abilities.filterIsInstance<Ability.Unparsed>()
        abilities += definition.abilities.size
        parsed += definition.abilities.size - unparsed.size
        if (unparsed.isEmpty()) facesParsed++
        unparsed.forEach { u ->
            OracleNormalizer.patterns(OracleFace(face.name, u.text, face.keywords), creatureTypes)
                .filter { it.kind == PatternKind.KEYWORD }.forEach { unparsedKeywords.merge(it.text, 1, Int::plus) }
        }
    }
    val out = File(args[1]).apply { parentFile.mkdirs() }
    File(out.parentFile, "unparsed-keywords.tsv").bufferedWriter().use { w ->
        w.appendLine("count\tkeyword pattern")
        unparsedKeywords.entries.sortedByDescending { it.value }.forEach { (text, count) -> w.appendLine("$count\t$text") }
    }
    out.bufferedWriter().use { w ->
        w.appendLine("# ${faces.size} card faces, ${withText.size} with rules text, $lines pattern lines, ${counts.size} distinct patterns")
        w.appendLine("# parser: $parsed of $abilities abilities read (${percent(parsed, abilities)}), $facesParsed of ${withText.size} faces with text fully read (${percent(facesParsed, withText.size)})")
        listOf(50, 100, 250, 500, 1000, 2000).forEach { top ->
            val coveredLines = ranked.take(top).sumOf { it.value }
            val coveredFaces = withText.count { list -> list.all { rank.getValue(it) <= top } }
            w.appendLine("# top $top patterns: ${percent(coveredLines, lines)} of lines, ${percent(coveredFaces, withText.size)} of faces with text fully covered")
        }
        w.appendLine("rank\tcount\tkind\tpattern\texample")
        ranked.forEach { (pattern, count) ->
            w.appendLine("${rank.getValue(pattern)}\t$count\t${pattern.kind}\t${pattern.text}\t${examples.getValue(pattern)}")
        }
    }
    println("Wrote ${counts.size} patterns from ${faces.size} faces to $out")
}

private fun percent(part: Int, whole: Int) = if (whole == 0) "0%" else "%.1f%%".format(100.0 * part / whole)

private class Face(val name: String, val text: String, val keywords: List<String>, val creature: Boolean,
    val subtypes: List<String>)

/** Card faces keyed by face name; the first usable printing of each face wins. */
private fun readFaces(file: File): Map<String, Face> {
    val faces = LinkedHashMap<String, Face>()
    JsonReader(open(file).reader(Charsets.UTF_8).buffered(1 shl 16)).use { reader ->
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() != "data") { reader.skipValue(); continue }
            reader.beginObject()
            while (reader.hasNext()) {
                reader.nextName()
                when (reader.peek()) {
                    JsonToken.BEGIN_ARRAY -> readCards(reader, faces) // AtomicCards: name → faces
                    else -> { // AllPrintings: set code → set
                        reader.beginObject()
                        while (reader.hasNext()) if (reader.nextName() == "cards") readCards(reader, faces) else reader.skipValue()
                        reader.endObject()
                    }
                }
            }
            reader.endObject()
        }
        reader.endObject()
    }
    return faces
}

private fun readCards(reader: JsonReader, faces: MutableMap<String, Face>) {
    reader.beginArray()
    while (reader.hasNext()) {
        var name = ""; var faceName: String? = null; var text = ""; var skip = false
        var keywords = emptyList<String>(); var types = emptyList<String>(); var subtypes = emptyList<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "name" -> name = reader.nextString()
                "faceName" -> faceName = reader.nextString()
                "text" -> text = reader.nextString()
                "keywords" -> keywords = strings(reader)
                "types" -> types = strings(reader)
                "subtypes" -> subtypes = strings(reader)
                "isFunny", "isOnlineOnly", "isRebalanced" -> if (reader.nextBoolean()) skip = true
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        val key = faceName ?: name
        if (!skip && key.isNotEmpty() && key !in faces) {
            faces[key] = Face(key, text, keywords, "Creature" in types || "Kindred" in types || "Tribal" in types, subtypes)
        }
    }
    reader.endArray()
}

private fun strings(reader: JsonReader): List<String> {
    val list = mutableListOf<String>()
    reader.beginArray()
    while (reader.hasNext()) list += reader.nextString()
    reader.endArray()
    return list
}

private fun open(file: File): InputStream {
    val raw = file.inputStream().buffered(1 shl 16)
    return if (file.name.endsWith(".gz")) GZIPInputStream(raw, 1 shl 16) else raw
}
