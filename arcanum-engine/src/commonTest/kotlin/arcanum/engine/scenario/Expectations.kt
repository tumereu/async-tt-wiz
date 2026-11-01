package arcanum.engine.scenario

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.KeywordKind
import arcanum.api.Mechanic
import arcanum.api.Subtype
import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.view.ObjectView
import arcanum.engine.view.View
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * What a scenario's `expect { … }` block can check, read from one [View]:
 *
 * ```
 * expect {
 *     "Lineage Druid".power shouldBe 2
 *     "Lineage Druid" hasKeyword Keyword.Flying
 *     bob.life shouldBe 17
 *     bob.graveyard has "Storm Spark"
 *     alice.hand.size shouldBe 3
 *     stack.isEmpty()
 *     log mentions "Storm Spark"
 * }
 * ```
 *
 * A card name means the first card of that name in setup order; `card("Mountain", 1)` the second.
 */
class Expectations internal constructor(private val scenario: Scenario, val view: View) {
    val alice: ScenarioPlayer get() = scenario.alice
    val bob: ScenarioPlayer get() = scenario.bob
    val carol: ScenarioPlayer get() = scenario.carol
    val dave: ScenarioPlayer get() = scenario.dave

    infix fun <T> T.shouldBe(expected: T) = assertEquals(expected, this)

    /** The [index]th card named [name] (from 0, in setup order): `card("Mountain", 1).tapped shouldBe true`. */
    fun card(name: String, index: Int = 0): CardRef = CardRef(scenario.card(name, index), "$name #$index")

    private val String.ref: CardRef get() = CardRef(scenario.card(this), this)

    private fun CardRef.view(): ObjectView = view[id] ?: fail("$label isn't in the view")

    // ---- Characteristics (after layers, CR 613)

    val String.power: Int? get() = ref.power
    val String.toughness: Int? get() = ref.toughness
    val String.loyalty: Int? get() = ref.view().characteristics?.loyalty
    val String.colors: List<Color> get() = ref.colors
    val String.types: List<CardType> get() = ref.types
    val String.subtypes: List<Subtype> get() = ref.subtypes
    val String.controller: PlayerId? get() = ref.controller
    val String.zone: Zone get() = ref.zone
    val String.tapped: Boolean get() = ref.tapped
    val String.view: ObjectView get() = ref.view()
    fun String.counters(kind: CounterKind): Int = ref.counters(kind)

    val CardRef.power: Int? get() = view().characteristics?.power
    val CardRef.toughness: Int? get() = view().characteristics?.toughness
    val CardRef.colors: List<Color> get() = view().characteristics?.colors.orEmpty()
    val CardRef.types: List<CardType> get() = view().characteristics?.types.orEmpty()
    val CardRef.subtypes: List<Subtype> get() = view().characteristics?.subtypes.orEmpty()
    val CardRef.controller: PlayerId? get() = view().controller
    val CardRef.zone: Zone get() = view().zone
    val CardRef.tapped: Boolean get() = view().obj?.tapped == true
    fun CardRef.counters(kind: CounterKind): Int = view().obj?.counters(kind) ?: 0

    infix fun String.hasKeyword(kind: KeywordKind) = ref.hasKeyword(kind)
    infix fun String.lacksKeyword(kind: KeywordKind) = ref.lacksKeyword(kind)
    infix fun CardRef.hasKeyword(kind: KeywordKind) =
        assertTrue(view().characteristics?.hasKeyword(kind) == true, "$label should have ${kind.text}")
    infix fun CardRef.lacksKeyword(kind: KeywordKind) =
        assertTrue(view().characteristics?.hasKeyword(kind) != true, "$label shouldn't have ${kind.text}")

    /** Whether a rule applies to it that isn't a characteristic (D24): `"X" hasMechanic { it is Mechanic.CantBlock }`. */
    infix fun String.hasMechanic(test: (Mechanic) -> Boolean) =
        assertTrue(ref.view().has(test), "$this should have that mechanic: ${ref.view().mechanics.map { it.mechanic }}")
    infix fun String.lacksMechanic(test: (Mechanic) -> Boolean) =
        assertTrue(!ref.view().has(test), "$this shouldn't have that mechanic: ${ref.view().mechanics.map { it.mechanic }}")

    // ---- Zones and history

    val stack: ZoneQuery get() = ZoneQuery(scenario, "the Stack", scenario.state.stack)

    val log: LogQuery get() = LogQuery(scenario.applied.map { ChangeText.describe(it, scenario) })
}

/** One card in an expectation. */
class CardRef(val id: ObjectId, val label: String)

/** The cards of one zone, by name. */
class ZoneQuery(private val scenario: Scenario, private val label: String, private val ids: List<ObjectId>) {
    val names: List<String?> get() = ids.map { id -> scenario.state.obj(id)?.let { scenario.nameOf(it) } ?: "ability $id" }
    val size: Int get() = ids.size

    infix fun has(name: String) = assertTrue(name in names, "$label should have $name: $names")
    infix fun lacks(name: String) = assertTrue(name !in names, "$label shouldn't have $name: $names")

    /** Asserts the zone is empty. */
    fun isEmpty() = assertTrue(ids.isEmpty(), "$label should be empty: $names")

    override fun toString(): String = "$label: $names"
}

/** The scenario's changes as history lines ([ChangeText]). */
class LogQuery(val lines: List<String>) {
    infix fun mentions(text: String) = assertTrue(lines.any { text in it }, "The log should mention \"$text\":\n${lines.joinToString("\n")}")
}

/** Plain history lines for changes, for `log mentions …` (D33: changes carry their meaning). */
object ChangeText {
    fun describe(change: Change, scenario: Scenario): String = when (change) {
        is Change.ZoneChange -> change.moves.joinToString("; ") { m ->
            "${scenario.nameOf(m.before) ?: m.before.id} moved from ${m.from} to ${m.to}" + meanings(m.meanings)
        }
        is Change.Status -> "${name(change.id, scenario)} ${change.flag} ${change.before} → ${change.after}" + meanings(change.meanings)
        is Change.Counters -> "${change.holder} ${change.kind} counters ${change.before?.count ?: 0} → ${change.after?.count ?: 0}"
        is Change.Life -> "${change.player} life ${change.before} → ${change.after}" + meanings(change.meanings)
        is Change.Damage -> change.dealt.joinToString("; ") { "${name(it.source, scenario)} dealt ${it.amount} damage to ${it.recipient}" }
        else -> change.toString()
    }

    private fun name(id: ObjectId, scenario: Scenario) = scenario.state.obj(id)?.let { scenario.nameOf(it) } ?: id.toString()

    private fun meanings(meanings: List<arcanum.engine.change.Meaning>) =
        if (meanings.isEmpty()) "" else " (${meanings.joinToString { it::class.simpleName ?: "?" }})"
}

/** Where two states differ, as JSON lines, for failure messages. */
object StateDiff {
    private val json = Json { prettyPrint = true }

    fun between(expected: GameState, actual: GameState): String {
        val a = json.encodeToString(GameState.serializer(), expected).lines()
        val b = json.encodeToString(GameState.serializer(), actual).lines()
        val first = a.indices.firstOrNull { it >= b.size || a[it] != b[it] } ?: return "(same JSON; the difference is in map order only)"
        return (maxOf(0, first - 5)..minOf(maxOf(a.size, b.size) - 1, first + 5)).joinToString("\n") { i ->
            "${a.getOrElse(i) { "" }.padEnd(70)} | ${b.getOrElse(i) { "" }}"
        }
    }
}
