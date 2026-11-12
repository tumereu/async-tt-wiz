package arcanum.tools

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Keyword
import arcanum.engine.CardSupport
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a card needs from the set's workers. */
internal sealed interface Verdict {
    val code: String
    /** The parser doesn't read it yet. */
    data object Unread : Verdict { override val code = "UNREAD" }
    /** It reads and gets tests. */
    data object Test : Verdict { override val code = "TEST" }
    /** All its abilities' shapes are tested and nothing connects them: no new test. */
    data object Repeat : Verdict { override val code = "REPEAT" }
}

/** A card's [verdict], the failing condition as its [reason] (the first in detail, the others by letter) and each ability's shape id. */
internal class CardVerdict(val verdict: Verdict, val reason: String, val shapes: List<String>)

/** Keywords that mean the same whatever else the card says, played through an ability a scenario can be seen to resolve, or simple. */
private val INDEPENDENT_KEYWORDS = SIMPLE_KEYWORDS + setOf("ward", "cycling", "typecycling")

/**
 * The rule for skipping a card's tests: `REPEAT` only when every condition holds; anything else is `TEST`, and when a condition
 * can't be judged it counts as failing. The owner's rule: many unnecessary tests are fine to catch one uncovered case.
 *
 * (a) it reads and the engine leaves nothing to the players; (b) normal layout, one face; (c) every ability's shape is covered by
 * parser and engine; (d) each printed line parsed alone gives the same shapes as the whole card; (e) no bound name or link across
 * abilities; (f) with two or more non-keyword abilities, none reads or changes the card's own state in a way another could
 * affect; (g) no keyword whose meaning depends on the rest of the card.
 */
internal fun judge(layout: String, faces: List<OracleCard>, covered: CoveredShapes): CardVerdict {
    val definition = parsedCard(layout, faces)
    val abilities = definition?.faces?.flatMap { it.abilities }.orEmpty()
    val shapes = abilities.map { shapeId(abilityShape(it)) }
    if (definition == null || abilities.any { it is Ability.Unparsed }) return CardVerdict(Verdict.Unread, "(a) the parser doesn't read it", shapes)
    val failures = listOfNotNull(
        CardSupport.manualParts(definition).firstOrNull()?.let { "(a) the engine leaves it to the players: $it" },
        if (layout == "normal" && faces.size == 1) null else "(b) layout $layout, ${faces.size} faces",
        uncovered(abilities, covered),
        if (faces.size == 1) linesConnected(faces.single(), abilities) else "(d) not judged on several faces",
        linkedAcrossAbilities(abilities, faces),
        ownStateShared(abilities),
        dependentKeyword(abilities),
    )
    if (failures.isEmpty()) return CardVerdict(Verdict.Repeat, "", shapes)
    val others = failures.drop(1).map { it.take(3) }.distinct()
    return CardVerdict(Verdict.Test, failures.first() + if (others.isEmpty()) "" else " (also ${others.joinToString("")})", shapes)
}

/** The card as a host reads it; null when its layout or printed values can't be read. */
internal fun parsedCard(layout: String, faces: List<OracleCard>): CardDefinition? = try {
    OracleParser.layout(layout, faces)?.let { OracleParser.parseCard(it, faces) }
} catch (e: RuntimeException) {
    null
} catch (e: StackOverflowError) {
    null
}

private fun printedText(ability: Ability): String = (abilityJson(ability) as? JsonObject)?.get("text").let { (it as? JsonPrimitive)?.content } ?: abilityKind(abilityJson(ability))

private fun uncovered(abilities: List<Ability>, covered: CoveredShapes): String? {
    for (ability in abilities) {
        val id = shapeId(abilityShape(ability))
        if (!covered.parser(id)) return "(c) no parser test of: ${printedText(ability)}"
        if (!covered.engine(id)) return "(c) no scenario plays: ${printedText(ability)}"
    }
    return null
}

/** (d) A line parsed alone must give what the whole card gives: otherwise the parser connected the lines. */
internal fun linesConnected(face: OracleCard, abilities: List<Ability>): String? {
    val whole = abilities.map(::abilityShape)
    val alone = abilityBlocks(face.text).flatMap { line ->
        val parsed = try {
            OracleParser.parse(face.copy(text = line)).abilities
        } catch (e: RuntimeException) {
            return "(d) a line doesn't parse alone: $line"
        }
        if (parsed.any { it is Ability.Unparsed }) return "(d) a line doesn't read alone: ${line.replace("\n", " / ")}"
        parsed.map(::abilityShape)
    }
    return if (alone == whole) null else "(d) parsed line by line the card differs: the parser connected its lines"
}

private val LINK_TYPES = setOf("remembered", "remembered-name", "remembered-option", "linked-to", "remember", "forget", "haunt", "same-as")
private val BINDING_KEYS = setOf("bind", "postBind", "preBind", "foundBind", "restBind", "chosenBind", "otherBind", "remember")
private val LINK_WORDS = Regex("""(?i)\b(?:exiled with|the chosen|chosen (?:type|color|name|card|player|creature|number|mode|quality)|remembered?|noted?|secretly|the named|linked|haunted|for as long as)\b|\bas .{0,40} enters,? (?:you )?choose""")

/** (e) A name one ability binds and another reads, or anything else that links abilities. */
internal fun linkedAcrossAbilities(abilities: List<Ability>, faces: List<OracleCard>): String? {
    faces.firstNotNullOfOrNull { LINK_WORDS.find(it.text)?.value }?.let { return "(e) its text links abilities: \"$it\"" }
    for (ability in abilities) {
        val json = abilityJson(ability)
        val found = HashSet<String>()
        val bound = HashSet<String>()
        val read = HashSet<String>()
        fun walk(e: JsonElement) {
            when (e) {
                is JsonObject -> {
                    val type = e.typeOrNull()
                    if (type in LINK_TYPES) found += type!!
                    if (type == "bound") (e["name"] as? JsonPrimitive)?.content?.let { read += it }
                    if (type == "choose") (e["name"] as? JsonPrimitive)?.content?.let { bound += it }
                    e.forEach { (k, v) ->
                        if (k in BINDING_KEYS) (v as? JsonPrimitive)?.content?.let { bound += it }
                        walk(v)
                    }
                }
                is JsonArray -> e.forEach(::walk)
                else -> Unit
            }
        }
        walk(json)
        if (found.isNotEmpty()) return "(e) it uses ${found.sorted().first()}"
        (read - bound).firstOrNull()?.let { return "(e) it reads a name it doesn't bind: $it" }
    }
    return null
}

/** The parts of a card's own state abilities can depend on, for (f). */
internal sealed interface OwnState {
    val label: String
    data object Counters : OwnState { override val label = "counters" }
    data object PowerToughness : OwnState { override val label = "power and toughness" }
    data object Abilities : OwnState { override val label = "abilities" }
    /** Its entering again. */
    data object Entering : OwnState { override val label = "zone (entering)" }
    /** Its leaving the battlefield, which damage can cause. */
    data object Leaving : OwnState { override val label = "zone (leaving)" }
    data object Attachment : OwnState { override val label = "attachment" }
    data object Tapped : OwnState { override val label = "tapped state" }
}

internal class OwnStateUse(val reads: Set<OwnState>, val writes: Set<OwnState>, val unsure: String?)

private val ALL_STATE: Set<OwnState> = setOf(OwnState.Counters, OwnState.PowerToughness, OwnState.Abilities, OwnState.Entering, OwnState.Leaving, OwnState.Attachment, OwnState.Tapped)

/** What an effect, cost or continuous rule of this type could change on the card itself, whoever else it may name. */
private val WRITES: Map<String, Set<OwnState>> = buildMap {
    fun add(state: OwnState, vararg types: String) = types.forEach { put(it, (get(it) ?: emptySet()) + state) }
    add(OwnState.Counters, "put-counters", "remove-counters", "move-counters", "put-counters-of", "put-event-counter-kinds", "one-more-of-each-kind",
        "enters-with-counters", "proliferate", "monstrosity", "adapt", "bolster", "support", "endure", "explore", "connive", "blight", "double")
    add(OwnState.PowerToughness, "modify-pt", "set-pt", "switch-pt", "7a-pt-defining", "7b-pt-set", "7c-pt-modify", "7d-pt-switch", "monstrosity", "adapt", "bolster", "support", "endure", "explore", "connive", "put-counters", "remove-counters", "blight")
    add(OwnState.Abilities, "gain-abilities", "lose-abilities", "gain-abilities-of", "cant-have", "change-text", "copy-of", "enters-with-abilities", "enters-as-copy", "enters-as", "transform", "flip", "turn-face-up", "turn-face-down", "manifest", "cloak", "copy", "set-name", "types", "colors", "copy-card")
    add(OwnState.Entering, "move", "phase-out", "phase-in", "exchange-control", "meld")
    add(OwnState.Leaving, "move", "destroy", "sacrifice", "exile", "return-to-hand", "damage", "fight", "phase-out")
    add(OwnState.Attachment, "attach", "unattach")
    add(OwnState.Tapped, "tap", "untap", "tap-objects", "exert", "doesnt-untap", "cant-become-untapped", "enters-tapped", "enters-untapped", "attacks-without-tapping", "tap-creatures", "tap-artifacts")
}

/** Parts of an ability that say which objects or when, not what it does. */
private val NOT_DOING = setOf("trigger", "condition", "interveningIf", "triggersOnlyIf", "filter", "targets", "objects", "players", "what", "of", "from", "to",
    "event", "amount", "value", "affects", "rules", "count", "duration")

private val OWN_STATE_WORDS = Regex("""(?i)\b(?:counters? on (?:this|it)|(?:this \w+|its)(?:'s)? (?:power|toughness)|(?:this \w+|it) (?:is|was|isn't|has) (?:tapped|untapped|attached|equipped|enchanted|attacking|blocking)|if (?:this \w+|it) (?:is|was|has)|as long as this|equal to (?:this \w+|its))\b""")

/** Which of the card's own state one ability reads and could change; [OwnStateUse.unsure] names what couldn't be told. */
internal fun ownStateUse(ability: Ability): OwnStateUse {
    val json = abilityJson(ability)
    val reads = HashSet<OwnState>()
    val writes = HashSet<OwnState>()
    var unsure: String? = null

    fun doings(e: JsonElement) {
        when (e) {
            is JsonObject -> {
                WRITES[e.typeOrNull()]?.let { writes += it }
                e.forEach { (k, v) -> if (k !in NOT_DOING) doings(v) }
            }
            is JsonArray -> e.forEach(::doings)
            else -> Unit
        }
    }
    doings(json)

    /** [path]: the enclosing objects, nearest first. */
    fun selfUse(path: List<Pair<JsonObject, String>>) {
        val (parent, key) = path.firstOrNull() ?: return
        val type = parent.typeOrNull()
        when {
            // The event it triggers on: which part of its state that event is about.
            type == "is" && key == "objects" -> {
                when (val event = path.drop(1).firstOrNull { it.first.typeOrNull() in EVENT_READS }?.first) {
                    null -> unsure = unsure ?: "the card itself in \"${path.drop(1).firstOrNull()?.first?.typeOrNull()}\""
                    else -> reads += if (event.typeOrNull() == "zone-change") {
                        if ((event["to"] as? JsonObject)?.typeOrNull() == "battlefield") setOf(OwnState.Entering) else setOf(OwnState.Leaving)
                    } else EVENT_READS.getValue(event.typeOrNull())
                }
            }
            type == "attached-to" || type == "attachments" || type == "attached" -> reads += OwnState.Attachment
            type == "object-is" -> reads += ALL_STATE // a condition on the card: which part of it isn't worth telling apart
            type == "of" -> reads += when ((parent["property"] as? JsonObject)?.typeOrNull() ?: (parent["what"] as? JsonObject)?.typeOrNull()) {
                "mana-value", "mana-symbols", "creature-types" -> emptySet()
                "power", "toughness", "power-for" -> setOf(OwnState.PowerToughness, OwnState.Counters)
                "counters" -> setOf(OwnState.Counters)
                else -> ALL_STATE
            }
            type == "move" && key == "untilLeaves" || type == "until-leaves" -> reads += OwnState.Leaving
            // What it does to itself is in the table of writes; how it was cast isn't state.
            type in SELF_ONLY_NAMED -> Unit
            // "Objects" of a continuous rule or one-shot apply: the mechanics say what changes.
            type == "objects" || type == "damage" && key == "source" -> Unit
            else -> unsure = unsure ?: "the card itself in \"$type.$key\""
        }
    }

    fun walk(e: JsonElement, path: List<Pair<JsonObject, String>>) {
        when (e) {
            is JsonObject -> {
                if (e.typeOrNull() == "self" || e.typeOrNull() == "self-after") selfUse(path)
                e.forEach { (k, v) -> if (k != "span") walk(v, listOf(e to k) + path) }
            }
            is JsonArray -> e.forEach { walk(it, path) }
            else -> Unit
        }
    }
    walk(json, emptyList())
    val text = (json as? JsonObject)?.get("text").let { (it as? JsonPrimitive)?.content }.orEmpty()
    if (unsure == null && reads.isEmpty() && OWN_STATE_WORDS.containsMatchIn(text)) unsure = "its text reads its own state: \"${OWN_STATE_WORDS.find(text)!!.value}\""
    return OwnStateUse(reads, writes, unsure)
}

/** What it says about the card when an event is about the card: tapped or not (attacking), power (damage), and so on. */
private val EVENT_READS: Map<String?, Set<OwnState>> = mapOf(
    "zone-change" to setOf(OwnState.Leaving),
    "attack" to setOf(OwnState.Tapped),
    "block" to setOf(OwnState.Tapped),
    "becomes-blocked" to setOf(OwnState.Tapped),
    "unblocked" to setOf(OwnState.Tapped),
    "tap" to setOf(OwnState.Tapped),
    "untap" to setOf(OwnState.Tapped),
    "damage" to setOf(OwnState.PowerToughness, OwnState.Leaving),
    "counters" to setOf(OwnState.Counters),
    "counters-removed" to setOf(OwnState.Counters),
    "attach" to setOf(OwnState.Attachment),
    "unattach" to setOf(OwnState.Attachment),
    "becomes-target" to emptySet(),
    "cast" to emptySet(),
    "activate" to emptySet(),
    "transform" to setOf(OwnState.Abilities),
    "sacrifice" to setOf(OwnState.Leaving),
    "destroyed" to setOf(OwnState.Leaving),
    "discard" to emptySet(),
    "cycle" to emptySet(),
)

/** Types whose naming the card itself says nothing about state another ability changes: what they do to it is in [WRITES]. */
private val SELF_ONLY_NAMED = setOf("put-counters", "remove-counters", "move-counters", "sacrifice", "exile", "discard", "return-to-hand", "move", "transform", "attach", "untap", "tap",
    "paid", "cast-x", "cast-from", "mana-spent", "paid-with", "times-paid", "regenerate", "endure", "connive", "explore", "fight", "exert", "copy", "copy-spell", "owner-of", "controller-of", "reveal", "union", "targets", "defending-player-of", "phase-out", "put-counters-of")

/** (f) Two abilities depend on each other through the card's own state when one could change what the other reads or changes. */
internal fun ownStateShared(abilities: List<Ability>): String? {
    val nonKeyword = abilities.filter { it !is Keyword }
    if (nonKeyword.size < 2) return null
    val uses = nonKeyword.map(::ownStateUse)
    uses.firstNotNullOfOrNull { it.unsure }?.let { return "(f) unsure: $it" }
    for ((i, a) in uses.withIndex()) for ((j, b) in uses.withIndex()) {
        if (i == j) continue
        val shared = a.writes.intersect(b.reads + b.writes)
        if (shared.isNotEmpty()) return "(f) two abilities share its ${shared.first().label}"
    }
    return null
}

/** What part of the card's own state a keyword changes how it behaves. */
private val KEYWORD_STATE: Map<String, Set<OwnState>> = mapOf(
    "haste" to setOf(OwnState.Tapped),
    "vigilance" to setOf(OwnState.Tapped),
    "defender" to setOf(OwnState.Tapped),
    "first-strike" to setOf(OwnState.PowerToughness),
    "double-strike" to setOf(OwnState.PowerToughness),
    "indestructible" to setOf(OwnState.Leaving),
)

/** (g) A keyword outside the ones known to be independent, or a simple one that meets the state another ability uses. */
internal fun dependentKeyword(abilities: List<Ability>): String? {
    val keywords = abilities.filterIsInstance<Keyword>().map { it.kind.id }
    keywords.firstOrNull { it !in INDEPENDENT_KEYWORDS }?.let { return "(g) keyword $it may depend on the rest of the card" }
    val uses = abilities.filter { it !is Keyword }.map(::ownStateUse)
    for (keyword in keywords) {
        val state = KEYWORD_STATE[keyword] ?: continue
        val met = uses.flatMap { it.reads + it.writes }.intersect(state)
        if (met.isNotEmpty()) return "(g) keyword $keyword meets its ${met.first().label}"
    }
    return null
}
