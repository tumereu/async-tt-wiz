package arcanum.tools

import arcanum.engine.Engine
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.zip.GZIPInputStream
import kotlin.system.exitProcess

/**
 * Checks that the parser and engine play every card of each supported set (`supported-sets.txt` beside this class)
 * fully by the rules: each card's faces are read from its set's MTGJSON file as a host's card data gives them
 * ([OracleParser.layout], [OracleParser.parseCard]) and [Engine.manualParts] must find nothing left to the players,
 * the check a host makes to say "this card is supported". Un-cards, online-only and rebalanced printings are skipped;
 * reprints of one card (one oracle id) are checked once. The set files are kept in a cache outside the repository and
 * fetched again only when MTGJSON's version changes (its Meta.json is checked at most once a day; without a network the
 * cache is used). Prints every unsupported card with the parts left to the players and exits with 1 when there is any.
 * Args: <cache directory> [set codes, comma-separated; default every supported set].
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Usage: SetSupport <cache directory> [CODE,CODE,...]" }
    val cache = File(args[0]).apply { mkdirs() }
    val supported = supportedSets()
    val wanted = args.getOrNull(1)?.split(',')?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }.orEmpty()
    val unknown = wanted - supported.keys
    require(unknown.isEmpty()) { "Not in supported-sets.txt: ${unknown.joinToString()}" }
    val codes = wanted.ifEmpty { supported.keys.toList() }
    val sets = SetCache(cache)
    var failed = 0
    var total = 0
    for (code in codes) {
        val file = sets.file(code)
        val cards = setCards(file)
        val unsupported = cards.mapNotNull { card -> card.handParts().takeIf { it.isNotEmpty() }?.let { card.name to it } }
        total += cards.size
        failed += unsupported.size
        println("$code ${supported.getValue(code)}: ${cards.size} cards, ${unsupported.size} unsupported")
        unsupported.forEach { (name, parts) -> println("  $name\n" + parts.joinToString("\n") { "    - $it" }) }
    }
    println("$total cards in ${codes.size} sets, $failed unsupported (MTGJSON ${sets.version ?: "version unknown"})")
    if (failed > 0) exitProcess(1)
}

/** The supported sets in file order: code → name. */
private fun supportedSets(): Map<String, String> {
    val text = checkNotNull(object {}.javaClass.getResource("/arcanum/tools/supported-sets.txt")) { "supported-sets.txt missing" }.readText()
    return text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        .associate { it.substringBefore(' ') to it.substringAfter(' ', "").trim() }
}

/** MTGJSON set files in [dir], fetched again when MTGJSON's version (Meta.json, checked at most once a day) changes. */
private class SetCache(private val dir: File) {
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build()
    private val meta = File(dir, "Meta.json")

    /** MTGJSON's current version, from a Meta.json at most a day old; null when it can't be known. */
    val version: String? by lazy {
        if (!meta.exists() || System.currentTimeMillis() - meta.lastModified() > DAY) {
            try {
                download("$BASE/Meta.json", meta)
            } catch (e: Exception) {
                System.err.println("MTGJSON Meta.json not fetched (${e.message}); using the cached set files")
            }
        }
        runCatching { JsonParser.parseString(meta.readText()).asJsonObject.getAsJsonObject("data").get("version").asString }.getOrNull()
    }

    fun file(code: String): File {
        val file = File(dir, "$code.json.gz")
        val stamp = File(dir, "$code.version")
        val current = version
        if (file.exists() && (current == null || stamp.takeIf { it.exists() }?.readText()?.trim() == current)) return file
        try {
            download("$BASE/$code.json.gz", file)
            stamp.writeText(current.orEmpty())
        } catch (e: Exception) {
            check(file.exists()) { "$code: MTGJSON set file not fetched and none cached in $dir (needs network on the first run): ${e.message}" }
            System.err.println("$code: MTGJSON set file not fetched again (${e.message}); using the cached one")
        }
        return file
    }

    private fun download(url: String, to: File) {
        val part = File(to.parentFile, to.name + ".part")
        val response = http.send(HttpRequest.newBuilder(URI(url)).timeout(Duration.ofMinutes(5)).build(),
            HttpResponse.BodyHandlers.ofFile(part.toPath()))
        if (response.statusCode() != 200) { part.delete(); error("HTTP ${response.statusCode()} for $url") }
        Files.move(part.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        const val BASE = "https://mtgjson.com/api/v5"
        const val DAY = 24 * 60 * 60 * 1000L
    }
}

/** One card of a set: its card-data layout and its faces' records in printed order. */
private class SetCard(val name: String, val layout: String, val faces: List<JsonObject>) {
    /** The parts of the card the engine leaves to the players, as a host reads its card data (empty: supported). */
    fun handParts(): List<String> {
        val printed = faces.map(::oracleCard)
        val layout = OracleParser.layout(layout, printed) ?: return listOf("unreadable: faces of layout $layout")
        val definition = try {
            OracleParser.parseCard(layout, printed)
        } catch (e: RuntimeException) {
            return listOf("unreadable: ${e.message ?: e::class.simpleName}") // printed values the parser can't read
        }
        return Engine.manualParts(definition)
    }
}

/** A face's printed values as a host's card data gives them to the parser. */
private fun oracleCard(record: JsonObject): OracleCard {
    fun field(key: String) = record.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    return OracleCard(
        name = field("faceName").ifBlank { field("name") },
        manaCost = field("manaCost").ifBlank { null },
        typeLine = field("type"),
        text = field("text"),
        power = field("power").ifBlank { null },
        toughness = field("toughness").ifBlank { null },
        loyalty = field("loyalty").ifBlank { null },
        defense = field("defense").ifBlank { null },
        colorIndicator = colorIndicator(record),
    )
}

/** A face's colour indicator (CR 204): its colours, when it has no mana cost. */
private fun colorIndicator(record: JsonObject): String? {
    if (record.get("manaCost")?.takeIf { it.isJsonPrimitive }?.asString?.isNotBlank() == true) return null
    return record.getAsJsonArray("colors")?.map { it.asString }?.filter { it.length == 1 && it in "WUBRG" }?.distinct()
        ?.sortedBy { "WUBRG".indexOf(it) }?.joinToString("")?.takeIf { it.isNotEmpty() }
}

/** The set's cards, once per oracle id (the first printing), skipping Un-cards, online-only and rebalanced printings. */
private fun setCards(file: File): List<SetCard> {
    val root = GZIPInputStream(file.inputStream().buffered(1 shl 16)).reader(Charsets.UTF_8).use { JsonParser.parseReader(it) }
    val records = root.asJsonObject.getAsJsonObject("data").getAsJsonArray("cards").map { it.asJsonObject }
        .filterNot { card -> listOf("isFunny", "isOnlineOnly", "isRebalanced").any { card.get(it)?.asBoolean == true } }
    val byId = records.associateBy { it.get("uuid").asString }
    val seen = HashSet<String>()
    return records.filter { (it.get("side")?.asString ?: "") in setOf("", "a") }.mapNotNull { card ->
        val oracle = card.getAsJsonObject("identifiers")?.get("scryfallOracleId")?.asString ?: card.get("name").asString
        if (!seen.add(oracle)) return@mapNotNull null
        val others = card.getAsJsonArray("otherFaceIds")?.mapNotNull { byId[it.asString] }.orEmpty()
        val faces = (others + card).distinctBy { it.get("uuid").asString }
            .sortedWith(compareBy<JsonObject> { it.get("side")?.asString ?: "" }.thenBy { it.get("uuid").asString })
        SetCard(card.get("name").asString, card.get("layout")?.asString ?: "normal", faces)
    }
}
