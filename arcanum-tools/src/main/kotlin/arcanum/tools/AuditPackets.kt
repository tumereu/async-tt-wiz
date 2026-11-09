package arcanum.tools

import arcanum.api.CardDefinition
import java.util.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull

/** A wrong change to a model, of the kinds an auditor must catch. */
internal sealed interface ModelMutation {
    val code: String
    /** `+1/+1` becomes `-1/-1` and back, as counters or as a change of power and toughness. */
    data object SwapPlusMinus : ModelMutation { override val code = "swap +1/+1 and -1/-1" }
    data object TargetCount : ModelMutation { override val code = "target count" }
    data object WhoIsAffected : ModelMutation { override val code = "who is affected" }
    data object FilterType : ModelMutation { override val code = "type in a filter" }
    data object DurationChanged : ModelMutation { override val code = "duration" }
    data object ConditionDropped : ModelMutation { override val code = "condition dropped" }

    companion object {
        val all: List<ModelMutation> = listOf(SwapPlusMinus, TargetCount, WhoIsAffected, FilterType, DurationChanged, ConditionDropped)
    }
}

/** [model] (a face's abilities, or a whole card) with [mutation] applied at its first place; null when it has no such place. */
internal fun mutateModel(model: JsonElement, mutation: ModelMutation): JsonElement? {
    var done = false
    fun edit(e: JsonElement, key: String?): JsonElement {
        if (done) return e
        return when (e) {
            is JsonObject -> mutateHere(e, key, mutation)?.also { done = true } ?: JsonObject(e.mapValues { (k, v) -> edit(v, k) })
            is JsonArray -> JsonArray(e.flatMap { item ->
                val spliced = if (!done && mutation == ModelMutation.ConditionDropped) ifWithoutCondition(item) else null
                if (spliced != null) { done = true; spliced } else listOf(edit(item, key))
            })
            else -> e
        }
    }
    val out = edit(model, null)
    return if (done && out != model) out else null
}

private fun mutateHere(o: JsonObject, key: String?, mutation: ModelMutation): JsonObject? = when (mutation) {
    ModelMutation.SwapPlusMinus -> negatedPowerToughness(o)
    ModelMutation.TargetCount -> if (key == "targets" && "min" in o && "max" in o) changedCount(o) else null
    ModelMutation.WhoIsAffected -> if (key in WHO_KEYS) when (o.typeOrNull()) {
        "you" -> typed("opponent")
        "opponent" -> typed("you")
        else -> null
    } else null
    ModelMutation.FilterType -> if (o.typeOrNull() == "type") otherCardType(o) else null
    ModelMutation.DurationChanged -> if (key == "duration") when (o.typeOrNull()) {
        "end-of-turn" -> typed("your-next-turn")
        null -> null
        else -> typed("end-of-turn")
    } else null
    ModelMutation.ConditionDropped -> CONDITION_KEYS.firstOrNull { it in o }?.takeIf { o.typeOrNull() != "if" && o.typeOrNull() != "as-long-as" }?.let { k -> JsonObject(o - k) }
}

private val WHO_KEYS = setOf("players", "controller", "player", "whose")
private val CONDITION_KEYS = listOf("interveningIf", "condition", "triggersOnlyIf")
private val OTHER_TYPE = mapOf("creature" to "artifact", "artifact" to "enchantment", "enchantment" to "creature", "land" to "creature", "planeswalker" to "creature",
    "instant" to "sorcery", "sorcery" to "instant", "battle" to "planeswalker")

private fun typed(type: String) = JsonObject(mapOf("type" to JsonPrimitive(type)))

/** A kept step of `{"type":"if"}` with no "otherwise": its steps always happen. */
private fun ifWithoutCondition(item: JsonElement): List<JsonElement>? {
    val o = item as? JsonObject ?: return null
    if (o.typeOrNull() != "if" || "otherwise" in o) return null
    return (o["then"] as? JsonArray)?.toList()
}

private fun negatedPowerToughness(o: JsonObject): JsonObject? {
    val type = o.typeOrNull()
    if (type != "modify-pt" && !(("power" in o) && ("toughness" in o) && (o["power"] as? JsonPrimitive)?.intOrNull != null)) return null
    fun negated(e: JsonElement?): JsonElement? = when {
        e is JsonPrimitive && e.intOrNull != null -> JsonPrimitive(-e.int)
        e is JsonObject && e.typeOrNull() == "fixed" -> JsonObject(e + ("value" to JsonPrimitive(-(e["value"] as JsonPrimitive).int)))
        else -> null
    }
    val power = negated(o["power"]) ?: return null
    val toughness = negated(o["toughness"]) ?: return null
    if (power == o["power"] && toughness == o["toughness"]) return null
    return JsonObject(o + ("power" to power) + ("toughness" to toughness))
}

private fun changedCount(slot: JsonObject): JsonObject? {
    fun fixed(e: JsonElement?) = ((e as? JsonObject)?.takeIf { it.typeOrNull() == "fixed" }?.get("value") as? JsonPrimitive)?.intOrNull
    val max = fixed(slot["max"]) ?: return null
    val min = fixed(slot["min"]) ?: return null
    val to = if (max == 1) 2 else 1
    fun value(n: Int) = JsonObject(mapOf("type" to JsonPrimitive("fixed"), "value" to JsonPrimitive(n)))
    return JsonObject(slot + ("max" to value(to)) + ("min" to value(if (min == max) to else minOf(min, to))))
}

private fun otherCardType(filter: JsonObject): JsonObject? {
    val ref = filter["cardType"] as? JsonObject ?: return null
    if (ref.typeOrNull() != "literal") return null
    val type = (ref["cardType"] as? JsonObject)?.typeOrNull() ?: return null
    val other = OTHER_TYPE[type] ?: return null
    return JsonObject(filter + ("cardType" to JsonObject(ref + ("cardType" to typed(other)))))
}

/**
 * A model as a person reads it: the fields of no meaning gone (printed text, positions), `name(args)` for every object. The defaults
 * are already gone in the JSON. A literal or a type is written bare. Keeps what differs apart: equal renderings mean equal meanings.
 */
internal fun renderModel(e: JsonElement): String = when (e) {
    is JsonArray -> e.joinToString(", ", "[", "]", transform = ::renderModel)
    is JsonPrimitive -> e.content
    is JsonObject -> {
        val fields = e.filter { (k, v) -> k != "type" && k !in NO_MEANING && !(k == "rules" && v == DEFAULT_RULES) }
        val type = e.typeOrNull()
        when {
            type == "fixed" && fields.keys == setOf("value") -> renderModel(fields.getValue("value"))
            type == "literal" && fields.size == 1 -> renderModel(fields.values.single())
            type == "type" && fields.keys == setOf("cardType") -> renderModel(fields.getValue("cardType"))
            type == "do" && fields.keys == setOf("effect") -> renderModel(fields.getValue("effect"))
            type == null && fields.keys == setOf("name") -> renderModel(fields.getValue("name"))
            fields.isEmpty() -> type ?: "{}"
            fields.size == 1 && type != null -> renderModel(fields.values.single()).let { if (it.startsWith("(")) type + it else "$type($it)" }
            else -> (type ?: "") + fields.entries.joinToString(", ", "(", ")") { (k, v) -> "$k=${renderModel(v)}" }
        }
    }
}

/** An activated ability's usual rules: any time, by its controller. */
private val DEFAULT_RULES = Json.parseToJsonElement("""{"timing":{"type":"any"},"activatedBy":{"type":"you"}}""")

private val NO_MEANING = setOf("text", "span", "abilityWord", "printed", "requirement")

/** A card's entry in an audit batch: its printed text and model, the abilities alone, one to a line, with its characteristics above them. */
internal class AuditEntry(val id: String, val card: String, val oracle: String, val model: String)

internal fun renderDefinition(json: JsonElement): String {
    val card = json as JsonObject
    return (card["faces"] as JsonArray).joinToString("\n") { faceElement ->
        val face = faceElement as JsonObject
        val heading = listOfNotNull(
            (face["name"] as? JsonPrimitive)?.content,
            (face["manaCost"] as? JsonPrimitive)?.content,
            (face["supertypes"] as? JsonArray)?.joinToString(" ", transform = ::renderModel),
            (face["types"] as? JsonArray)?.joinToString(" ", transform = ::renderModel),
            (face["subtypes"] as? JsonArray)?.joinToString(" ", transform = ::renderModel),
            if ("power" in face) "${renderModel(face.getValue("power"))}/${renderModel(face.getValue("toughness"))}" else null,
            face["loyalty"]?.let { "loyalty ${renderModel(it)}" },
            face["defense"]?.let { "defense ${renderModel(it)}" },
        ).joinToString(" | ")
        val abilities = (face["abilities"] as? JsonArray).orEmpty().joinToString("\n") { "- ${renderModel(it)}" }
        listOf(heading, abilities).filter { it.isNotEmpty() }.joinToString("\n")
    }
}

/** One seeded wrong entry: its [entry] id, which card it copies and how it was changed. */
internal class SeededEntry(val entry: String, val card: String, val mutation: ModelMutation)

/** The audit of a set: every entry, ordered at random but the same for one card list, cut into batches; which ones are wrong copies. */
internal class AuditPlan(val batches: List<List<AuditEntry>>, val seeded: List<SeededEntry>)

/** A card that reads, for the audit. */
internal class AuditCard(val name: String, val oracle: String, val definition: CardDefinition)

/**
 * Builds the audit: an entry per card, plus wrong copies of about [share] of the entries, each in another batch than its original
 * and with a model no original has. Seeded by the card list alone, so the same cards give the same audit.
 */
internal fun auditPlan(cards: List<AuditCard>, share: Double = 0.08, batchSize: Int = 25): AuditPlan {
    val random = Random(cards.map { it.name }.sorted().joinToString("\n").hashCode().toLong())
    val sorted = cards.sortedBy { it.name }
    class Draft(val card: AuditCard, val model: String, val mutation: ModelMutation?)
    val drafts = sorted.mapTo(ArrayList()) { Draft(it, renderDefinition(Json.encodeToJsonElement(CardDefinition.serializer(), it.definition)), null) }
    val wanted = Math.round(sorted.size * share / (1 - share)).toInt()
    // Each kind of change in turn, so the wrong copies vary; a card goes to the first kind it can take.
    var kind = 0
    for (card in sorted.shuffled(random)) {
        if (drafts.size - sorted.size >= wanted) break
        val json = Json.encodeToJsonElement(CardDefinition.serializer(), card.definition)
        val original = renderDefinition(json)
        for (step in ModelMutation.all.indices) {
            val mutation = ModelMutation.all[(kind + step) % ModelMutation.all.size]
            val model = mutateModel(json, mutation)?.let(::renderDefinition)
            if (model != null && model != original) {
                drafts += Draft(card, model, mutation)
                kind = ModelMutation.all.indexOf(mutation) + 1
                break
            }
        }
    }
    drafts.shuffle(random)
    // A copy never sits in its original's batch: it would give itself away.
    val width = drafts.size.toString().length.coerceAtLeast(4)
    val perBatch = batchSize.coerceAtLeast(1)
    fun batchOf(index: Int) = index / perBatch
    for (i in drafts.indices) {
        val draft = drafts[i]
        val clash = drafts.indices.any { j -> j != i && batchOf(j) == batchOf(i) && drafts[j].card === draft.card }
        if (!clash) continue
        val swap = drafts.indices.firstOrNull { j ->
            batchOf(j) != batchOf(i) && drafts[j].card !== draft.card &&
                drafts.indices.none { k -> k != j && batchOf(k) == batchOf(j) && drafts[k].card === draft.card } &&
                drafts.indices.none { k -> k != i && batchOf(k) == batchOf(i) && drafts[k].card === drafts[j].card }
        }
        if (swap != null) { drafts[i] = drafts[swap]; drafts[swap] = draft }
    }
    val entries = drafts.mapIndexed { i, d -> AuditEntry("E" + (i + 1).toString().padStart(width, '0'), d.card.name, d.card.oracle, d.model) }
    val seeded = drafts.indices.mapNotNull { i -> drafts[i].mutation?.let { SeededEntry(entries[i].id, drafts[i].card.name, it) } }
    return AuditPlan(entries.chunked(perBatch), seeded)
}
