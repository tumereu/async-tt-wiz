package arcanum.tools

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.FaceDefinition
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Where the parser migration tools read and write: the local files outside the repository; Gradle can override each. */
internal object ParserToolPaths {
    private val cache = File(System.getProperty("user.home"), "projects/atw-cache")
    val mtgjson get() = File(System.getProperty("arcanum.mtgjson") ?: File(cache, "AllPrintings.json.gz").path)
    val cube get() = File(System.getProperty("arcanum.cube") ?: File(cache, "arcanum-cube-cards.json").path)
    val outputs get() = File(System.getProperty("arcanum.parserOutputs") ?: File(cache, "parser-migration").path)
    val parserSources get() = File(System.getProperty("arcanum.parserSources") ?: "../arcanum-parser/src/commonMain/kotlin")
    val setPlans get() = File(System.getProperty("arcanum.setPlans") ?: File(cache, "set-plans").path)
}

/** Which promise covers a card: a card of a supported set or of the cube mustn't change without a task naming it. */
internal sealed interface CardScope {
    val code: String
    data object SupportedSet : CardScope { override val code = "supported" }
    data object Cube : CardScope { override val code = "cube" }
    data object HeldOut : CardScope { override val code = "held-out" }

    companion object {
        fun of(code: String): CardScope = listOf(SupportedSet, Cube, HeldOut).first { it.code == code }
    }
}

/** One card of the corpus: one printing per oracle id, its faces in printed order. */
internal class CorpusCard(val id: String, val name: String, val layout: String, val faces: List<OracleCard>, val scope: CardScope)

/** Every local card the parser tools measure, sorted by name, and the MTGJSON version they came from. */
internal class ParserCorpus(val version: String, val cards: List<CorpusCard>)

/** What a parse of a card gave: its canonical model (one line of JSON) and whether nothing was left unread. */
internal class CardModel(val read: Boolean, val json: String)

private val JSON = Json

/** Whether [face] is read whole: it parses and no ability stays [Ability.Unparsed]. */
internal fun faceReads(face: OracleCard): Boolean = try {
    OracleParser.parse(face).reads()
} catch (e: RuntimeException) {
    false
} catch (e: StackOverflowError) {
    false
}

private fun FaceDefinition.reads() = abilities.none { it is Ability.Unparsed }

/**
 * The model of a card printed with [faces] under the card-data layout name [layout], as a host reads it
 * ([OracleParser.layout], [OracleParser.parseCard]). A layout the parser has no [arcanum.api.Layout] for, or printed
 * values it can't read, make the card unread with its faces or the error as its model.
 */
internal fun cardModel(layout: String, faces: List<OracleCard>): CardModel = try {
    val known = OracleParser.layout(layout, faces)
    if (known == null) {
        val parsed = JSON.encodeToString(ListSerializer(FaceDefinition.serializer()), faces.map(OracleParser::parse))
        CardModel(false, """{"unknownLayout":"$layout","faces":$parsed}""")
    } else {
        val definition = OracleParser.parseCard(known, faces)
        CardModel(definition.faces.all { it.reads() }, JSON.encodeToString(CardDefinition.serializer(), definition))
    }
} catch (e: RuntimeException) {
    CardModel(false, JSON.encodeToString(mapOf("error" to (e.message ?: e::class.simpleName.orEmpty()))))
} catch (e: StackOverflowError) {
    CardModel(false, """{"error":"stack overflow"}""")
}

/** Set types and layouts that aren't playable cards (report §9); a card printed only in these isn't in the corpus. */
private val EXCLUDED_SET_TYPES = setOf("funny", "memorabilia", "token", "minigame", "alchemy", "vanguard", "treasure_chest")
private val EXCLUDED_LAYOUTS = setOf("token", "double_faced_token", "art_series", "emblem", "planar", "scheme", "vanguard",
    "augment", "host", "reversible_card", "sticker")

/**
 * The corpus from a local AllPrintings file and the cube's card list: one printing per oracle id (the newest in a set and
 * layout that isn't excluded). Printed in a supported set, the card is [CardScope.SupportedSet]; otherwise listed in
 * the cube, [CardScope.Cube]; otherwise [CardScope.HeldOut]. A cube card the corpus leaves out (an Un-card) is read
 * from the cube's own list, so it is protected too. Without a [cube] file (a fresh clone), the names of [fallbackCube]
 * (the repository's `supported-cards.txt`) are the cube: names only, so none is read from a list of its own.
 */
internal fun readCorpus(
    mtgjson: File,
    cube: File,
    supported: Set<String>,
    fallbackCube: () -> Set<String> = ::supportedCards,
): ParserCorpus {
    class Entry(val sets: MutableSet<String> = HashSet(), var date: String = "", var code: String = "", var card: CorpusCard? = null)
    val entries = HashMap<String, Entry>()
    val seenSets = HashSet<String>()
    val version = forEachSet(mtgjson) { set ->
        seenSets += set.code
        val excluded = set.type in EXCLUDED_SET_TYPES || set.onlineOnly
        val records = playableRecords(set.cards)
        val byId = records.associateBy { it.get("uuid").asString }
        for (record in frontRecords(records)) {
            val id = oracleId(record)
            val entry = entries.getOrPut(id) { Entry() }
            entry.sets += set.code
            val layout = record.get("layout")?.asString ?: "normal"
            if (excluded || layout in EXCLUDED_LAYOUTS) continue
            val newer = entry.card == null || set.date > entry.date || (set.date == entry.date && set.code < entry.code)
            if (newer) {
                entry.date = set.date
                entry.code = set.code
                val faces = printedFaces(record, byId).map(::oracleCard)
                entry.card = CorpusCard(id, record.get("name").asString, layout, faces, CardScope.HeldOut)
            }
        }
    }
    val absent = supported - seenSets
    check(absent.isEmpty()) { "Supported sets not in $mtgjson, so nothing would protect them: ${absent.sorted().joinToString()}. Use a newer AllPrintings." }
    val cubeCards = if (cube.exists()) {
        JsonParser.parseString(cube.readText()).asJsonObject.entrySet().associate { (name, faces) ->
            name to faces.asJsonArray.map { it.asJsonObject }
        }
    } else {
        fallbackCube().associateWith { emptyList() }
    }
    fun inCube(card: CorpusCard) = card.name in cubeCards || card.name.substringBefore(" // ") in cubeCards
    val kept = entries.values.mapNotNull { entry ->
        val card = entry.card ?: return@mapNotNull null
        val scope = when {
            entry.sets.any { it in supported } -> CardScope.SupportedSet
            inCube(card) -> CardScope.Cube
            else -> CardScope.HeldOut
        }
        CorpusCard(card.id, card.name, card.layout, card.faces, scope)
    }
    val names = kept.flatMap { listOf(it.name, it.name.substringBefore(" // ")) }.toSet()
    val onlyInCube = cubeCards.filter { (name, faces) -> name !in names && faces.isNotEmpty() }.map { (name, faces) ->
        CorpusCard("cube:$name", name, faces.first().get("layout")?.takeIf { it.isJsonPrimitive }?.asString ?: "normal",
            faces.map(::oracleCard), CardScope.Cube)
    }
    return ParserCorpus(version, (kept + onlyInCube).sortedWith(compareBy({ it.name }, { it.id })))
}

internal class SetRecords(val code: String, val type: String, val date: String, val onlineOnly: Boolean, val cards: List<JsonObject>)

/** Streams AllPrintings set by set ([only] those sets, when given; the others are skipped unread), holding one set's
 * cards at a time; returns the file's MTGJSON version. */
internal fun forEachSet(file: File, only: Set<String>? = null, each: (SetRecords) -> Unit): String {
    var version = "unknown"
    openJson(file).use { stream ->
        JsonReader(stream.reader(Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "meta" -> version = JsonParser.parseReader(reader).asJsonObject.get("version")?.asString ?: version
                    "data" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val code = reader.nextName()
                            if (only != null && code !in only) reader.skipValue() else each(readSet(code, reader))
                        }
                        reader.endObject()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
    }
    return version
}

private fun readSet(code: String, reader: JsonReader): SetRecords {
    var type = ""
    var date = ""
    var onlineOnly = false
    var cards: JsonElement? = null
    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "type" -> type = reader.nextString()
            "releaseDate" -> date = reader.nextString()
            "isOnlineOnly" -> onlineOnly = reader.nextBoolean()
            "cards" -> cards = JsonParser.parseReader(reader)
            else -> reader.skipValue()
        }
    }
    reader.endObject()
    return SetRecords(code, type, date, onlineOnly, cards?.asJsonArray?.map { it.asJsonObject }.orEmpty())
}

private fun openJson(file: File): InputStream {
    val raw = file.inputStream().buffered(1 shl 16)
    return if (file.name.endsWith(".gz")) GZIPInputStream(raw, 1 shl 16) else raw
}
