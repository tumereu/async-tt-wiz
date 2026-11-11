package org.asyncttwiz.automatum

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.state.CombatState
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.ManaUnit
import arcanum.engine.state.GameObject
import arcanum.engine.state.ObjectId
import arcanum.engine.state.StackItem
import arcanum.engine.state.TargetRef
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.EngineRecord
import org.asyncttwiz.protocol.Faults
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.MtgStackItem
import org.asyncttwiz.protocol.MtgStackKind
import org.asyncttwiz.protocol.PoolMana
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TypedCounter

/**
 * How engine values are shown in ATW's board fields (integration spec §4.5): typed counter kinds as names, mana as
 * [PoolMana], steps as [TurnPosition], and the JSON of records and Stack items ([EngineProjection]).
 */
internal object EngineCodec {
    val json = Json { encodeDefaults = false }

    fun <T> encode(serializer: KSerializer<T>, value: T): String = json.encodeToString(serializer, value)
    fun <T> decode(serializer: KSerializer<T>, value: String): T = json.decodeFromString(serializer, value)

    // ---- Steps (CR 500.1; ATW's twelve manual positions name the engine's twelve steps in the same order, and the
    // first of two combat damage steps, CR 510.4, is ATW's own position, I-D31)

    private val steps = listOf(TurnStep.Untap, TurnStep.Upkeep, TurnStep.Draw, TurnStep.PrecombatMain,
        TurnStep.BeginningOfCombat, TurnStep.DeclareAttackers, TurnStep.DeclareBlockers, TurnStep.CombatDamage,
        TurnStep.EndOfCombat, TurnStep.PostcombatMain, TurnStep.End, TurnStep.Cleanup)

    /** ATW's position of [step]; [firstStrike]: the combat damage step is the first-strike one (CR 510.4). */
    fun position(step: TurnStep, firstStrike: Boolean = false): TurnPosition =
        if (firstStrike && step == TurnStep.CombatDamage) TurnPosition.FIRST_STRIKE_DAMAGE else TurnPosition.MANUAL[steps.indexOf(step)]

    // ---- Counter kinds: "+1/+1", "loyalty", "charge", "keyword:{…}"

    private val ptName = Regex("([+-]\\d+)/([+-]\\d+)")
    private val namedKinds: Map<String, CounterKind> by lazy {
        listOf(CounterKind.Shield, CounterKind.Stun, CounterKind.Finality, CounterKind.Rad, CounterKind.Hone,
            CounterKind.Time, CounterKind.Level, CounterKind.Loyalty, CounterKind.Lore, CounterKind.Defense,
            CounterKind.Poison, CounterKind.Energy).associateBy(::serialName)
    }

    private fun serialName(kind: CounterKind): String =
        json.encodeToJsonElement(CounterKind.serializer(), kind).jsonObject["type"]?.jsonPrimitive?.content ?: kind.toString().also {
            Faults.report("EngineCodec", "counter kind without a serial name", mapOf("kind" to it))
        }

    private fun signed(n: Int) = if (n < 0) "$n" else "+$n"

    fun counterName(kind: CounterKind): String = when (kind) {
        is CounterKind.PowerToughness -> "${signed(kind.power)}/${signed(kind.toughness)}"
        is CounterKind.Keyword -> "keyword:" + encode(CounterKind.serializer(), kind)
        is CounterKind.Named -> if (kind.name in namedKinds || kind.name.startsWith("keyword:") || kind.name.startsWith("named:") ||
            ptName.matches(kind.name)) "named:${kind.name}" else kind.name
        else -> serialName(kind)
    }

    fun counterKind(name: String): CounterKind {
        ptName.matchEntire(name)?.let { return CounterKind.PowerToughness(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        namedKinds[name]?.let { return it }
        if (name.startsWith("keyword:")) return decode(CounterKind.serializer(), name.removePrefix("keyword:"))
        return CounterKind.Named(name.removePrefix("named:"))
    }

    fun counter(entry: CounterEntry) = TypedCounter(counterName(entry.kind), entry.count, entry.timestamp)
    fun counters(entries: List<CounterEntry>): List<TypedCounter> = entries.map(::counter).sortedBy { it.kind }

    // ---- Mana: the colour as a letter, the rest of the unit as JSON when there is any

    fun mana(unit: ManaUnit): PoolMana {
        val rest = unit.copy(color = null)
        return PoolMana(unit.color?.symbol?.toString() ?: "C", if (rest == ManaUnit(null)) "" else encode(ManaUnit.serializer(), rest))
    }

    // ---- Engine records (§4.3): each its own, by id

    const val COMBAT = "combat"

    private const val EMBLEM = "emblem:"

    /** An emblem (CR 114, I-D25): the engine's command-zone object in [EngineRecord.data], and what ATW shows of it:
     * whose it is and its rules [text]. */
    fun emblemRecord(emblem: GameObject, text: String) = EngineRecord(emblemId(emblem), EngineRecordKind.Emblem,
        emblem.timestamp, encode(GameObject.serializer(), emblem), player = emblem.owner.value, text = text)

    fun emblemId(emblem: GameObject) = EMBLEM + (emblem.id as ObjectId.Card).card.value

    /** The emblem's rules text as its abilities write it ([AtwTokens.rulesText]); empty when they carry no text. */
    fun emblemText(emblem: GameObject): String =
        emblem.definition?.faces.orEmpty().joinToString("\n") { AtwTokens.rulesText(it) }.trim()

    /** The engine's combat (record [COMBAT]) in turn [turn] (ATW's turn number): the combat's JSON with the turn added. */
    fun combatRecord(combat: CombatState, turn: Int): EngineRecord {
        val element = json.encodeToJsonElement(combatSerializer, combat) as JsonObject
        return EngineRecord(COMBAT, EngineRecordKind.Combat, data = json.encodeToString(JsonElement.serializer(),
            JsonObject(element + (COMBAT_TURN to JsonPrimitive(turn)))))
    }

    /** A combat record's combat and its turn (null in records made before the turn was kept). */
    fun combat(record: EngineRecord): Pair<CombatState, Int?> {
        val element = json.parseToJsonElement(record.data) as JsonObject
        val turn = (element[COMBAT_TURN] as? JsonPrimitive)?.content?.toIntOrNull()
        return json.decodeFromJsonElement(combatSerializer, JsonObject(element - COMBAT_TURN)) to turn
    }

    /**
     * The engine's combat on [board] while it is that board's combat (#1, I-D28: a combat finished by hand while
     * Automatum was off isn't Automatum's any more): its record is of this turn, the turn is in a combat step from
     * declare attackers to end of combat, and the combat area is open. Null otherwise.
     * @throws Exception when the record can't be read.
     */
    fun currentCombat(board: BoardState): CombatState? {
        val record = board.engineRecords.singleOrNull { it.id == COMBAT } ?: return null
        if (board.combat == null || TurnPosition.of(board.turn) !in COMBAT_STEPS) return null
        val (combat, turn) = combat(record)
        return combat.takeIf { turn == null || turn == board.turn.number.toIntOrNull() }
    }

    private const val COMBAT_TURN = "turn"
    private val COMBAT_STEPS = setOf(TurnPosition.DECLARE_ATTACKERS, TurnPosition.DECLARE_BLOCKERS, TurnPosition.FIRST_STRIKE_DAMAGE,
        TurnPosition.COMBAT_DAMAGE, TurnPosition.END_COMBAT)

    fun stackKind(item: StackItem): MtgStackKind = when (item.kind) {
        arcanum.engine.state.StackItemKind.Spell -> MtgStackKind.Spell
        is arcanum.engine.state.StackItemKind.SpellCopy -> MtgStackKind.SpellCopy
        arcanum.engine.state.StackItemKind.Activated -> MtgStackKind.Activated
        arcanum.engine.state.StackItemKind.Triggered -> MtgStackKind.Triggered
    }

    /** A Stack item as ATW keeps it: its structured fields for ATW to show ([refs] names cards by ATW's ids), and the
     * whole engine item in [MtgStackItem.data], which is what the engine reads back. */
    fun stackItem(item: StackItem, refs: (ObjectId) -> MtgRef): MtgStackItem = MtgStackItem(
        id = refs(item.id), kind = stackKind(item), controller = item.controller.value, ability = item.origin?.key,
        source = item.source?.let(refs),
        targets = item.targets.mapValues { (_, targets) -> targets.map { target(it, refs) } },
        modes = item.modes, values = item.announced, timestamp = item.timestamp,
        data = encode(StackItem.serializer(), item))

    /** The engine's Stack item an entry carries ([MtgStackItem.data]), stamped with its timestamp. */
    fun stackItem(item: MtgStackItem): StackItem = decode(StackItem.serializer(), item.data)
        .let { i -> if (i.timestamp == item.timestamp) i else i.copy(timestamp = item.timestamp) }

    fun target(target: TargetRef, refs: (ObjectId) -> MtgRef): MtgRef = when (target) {
        is TargetRef.Object -> refs(target.id)
        is TargetRef.Player -> MtgRef.Player(target.id.value)
    }
}

internal val combatSerializer: KSerializer<CombatState> = CombatState.serializer()

