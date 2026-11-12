package arcanum.tools

import arcanum.engine.CardSupport
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import com.google.gson.JsonObject
import kotlin.system.exitProcess

/**
 * Checks that the parser and engine play every card of each supported set (`supported-sets.txt` beside this class)
 * fully by the rules: each card's faces are read from the pinned MTGJSON AllPrintings file ([PinnedMtgJson]) as a host's
 * card data gives them ([OracleParser.layout], [OracleParser.parseCard]) and [CardSupport.manualParts] must find nothing left
 * to the players, the check a host makes to say "this card is supported". Un-cards, online-only and rebalanced printings
 * are skipped; reprints of one card (one oracle id) are checked once. Prints every unsupported card with the parts left
 * to the players and exits with 1 when there is any. Args: [set codes, comma-separated; default every supported set].
 */
fun main(args: Array<String>) {
    val supported = supportedSets()
    val wanted = args.getOrNull(0)?.split(',')?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }.orEmpty()
    val unknown = wanted - supported.keys
    require(unknown.isEmpty()) { "Not in supported-sets.txt: ${unknown.joinToString()}" }
    val codes = wanted.ifEmpty { supported.keys.toList() }
    val results = HashMap<String, Pair<Int, List<Pair<String, List<String>>>>>()
    val version = forEachPinnedSet(codes) { code, records ->
        val cards = setCards(records)
        results[code] = cards.size to cards.mapNotNull { card -> card.handParts().takeIf { it.isNotEmpty() }?.let { card.name to it } }
    }
    var failed = 0
    var total = 0
    for (code in codes) {
        val (count, unsupported) = results.getValue(code)
        total += count
        failed += unsupported.size
        println("$code ${supported.getValue(code)}: $count cards, ${unsupported.size} unsupported")
        unsupported.forEach { (name, parts) -> println("  $name\n" + parts.joinToString("\n") { "    - $it" }) }
    }
    println("$total cards in ${codes.size} sets, $failed unsupported (MTGJSON $version)")
    if (failed > 0) exitProcess(1)
}

/** The supported sets in file order: code → name. */
internal fun supportedSets(): Map<String, String> {
    val text = checkNotNull(object {}.javaClass.getResource("/arcanum/tools/supported-sets.txt")) { "supported-sets.txt missing" }.readText()
    return text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        .associate { it.substringBefore(' ') to it.substringAfter(' ', "").trim() }
}

/** The cards supported one by one (`supported-cards.txt` beside this class), by name: a whole name or a front face's. */
internal fun supportedCards(): Set<String> {
    val text = checkNotNull(object {}.javaClass.getResource("/arcanum/tools/supported-cards.txt")) { "supported-cards.txt missing" }.readText()
    return text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
}

/** One card of a set: its card-data layout and its faces' records in printed order. */
internal class SetCard(val name: String, val layout: String, val faces: List<JsonObject>) {
    /** The parts of the card the engine leaves to the players, as a host reads its card data (empty: supported). */
    fun handParts(): List<String> {
        val printed = faces.map(::oracleCard)
        val layout = OracleParser.layout(layout, printed) ?: return listOf("unreadable: faces of layout $layout")
        val definition = try {
            OracleParser.parseCard(layout, printed)
        } catch (e: RuntimeException) {
            return listOf("unreadable: ${e.message ?: e::class.simpleName}") // printed values the parser can't read
        }
        return CardSupport.manualParts(definition)
    }
}

/** A face's printed values as a host's card data gives them to the parser. */
internal fun oracleCard(record: JsonObject): OracleCard {
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

/** The set's cards (its card records [cards]), once per oracle id (the first printing), skipping Un-cards, online-only
 * and rebalanced printings. */
internal fun setCards(cards: List<JsonObject>): List<SetCard> {
    val records = playableRecords(cards)
    val byId = records.associateBy { it.get("uuid").asString }
    val seen = HashSet<String>()
    return frontRecords(records).mapNotNull { card ->
        if (!seen.add(oracleId(card))) return@mapNotNull null
        SetCard(card.get("name").asString, card.get("layout")?.asString ?: "normal", printedFaces(card, byId))
    }
}

/** A set's card records without Un-cards, online-only and rebalanced printings. */
internal fun playableRecords(cards: List<JsonObject>): List<JsonObject> =
    cards.filterNot { card -> listOf("isFunny", "isOnlineOnly", "isRebalanced").any { card.get(it)?.asBoolean == true } }

/** The records that start a card: one-faced cards and front faces. */
internal fun frontRecords(records: List<JsonObject>): List<JsonObject> = records.filter { (it.get("side")?.asString ?: "") in setOf("", "a") }

/** What identifies a card across its printings. */
internal fun oracleId(card: JsonObject): String = card.getAsJsonObject("identifiers")?.get("scryfallOracleId")?.asString ?: card.get("name").asString

/** The records of [card]'s faces in printed order, from its set's records [byId]. */
internal fun printedFaces(card: JsonObject, byId: Map<String, JsonObject>): List<JsonObject> {
    val others = card.getAsJsonArray("otherFaceIds")?.mapNotNull { byId[it.asString] }.orEmpty()
    return (others + card).distinctBy { it.get("uuid").asString }
        .sortedWith(compareBy<JsonObject> { it.get("side")?.asString ?: "" }.thenBy { it.get("uuid").asString })
}
