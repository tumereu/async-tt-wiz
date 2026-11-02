package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Designation
import arcanum.api.ObjectMark
import arcanum.api.TurnStep
import arcanum.engine.Pending
import arcanum.engine.change.Change
import arcanum.engine.decision.WaitingTrigger
import arcanum.engine.state.Bindings
import arcanum.engine.state.CardId
import arcanum.engine.state.CombatState
import arcanum.engine.state.ControlSince
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.DelayedTrigger
import arcanum.engine.state.EffectObject
import arcanum.engine.state.LoggedChange
import arcanum.engine.state.ManaUnit
import arcanum.engine.state.ObjectFacts
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.TargetRef
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.asyncttwiz.protocol.EngineRecord
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.MtgStackItem
import org.asyncttwiz.protocol.MtgStackKind
import org.asyncttwiz.protocol.PoolMana
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TypedCounter

/**
 * How engine values are kept in ATW's v36 fields (integration spec §4.5): typed counter kinds as names, mana as
 * [PoolMana], steps as [TurnPosition], and the opaque JSON of ledgers, records and Stack items. Both directions live
 * here so [EngineState.from] and [EngineBatch] can't drift apart.
 */
internal object EngineCodec {
    val json = Json { encodeDefaults = false }

    fun <T> encode(serializer: KSerializer<T>, value: T): String = json.encodeToString(serializer, value)
    fun <T> decode(serializer: KSerializer<T>, value: String): T = json.decodeFromString(serializer, value)

    // ---- Steps (the two lists name the same twelve steps in the same order, CR 500.1)

    private val steps = listOf(TurnStep.Untap, TurnStep.Upkeep, TurnStep.Draw, TurnStep.PrecombatMain,
        TurnStep.BeginningOfCombat, TurnStep.DeclareAttackers, TurnStep.DeclareBlockers, TurnStep.CombatDamage,
        TurnStep.EndOfCombat, TurnStep.PostcombatMain, TurnStep.End, TurnStep.Cleanup)

    fun step(position: TurnPosition): TurnStep = steps[position.ordinal]
    fun position(step: TurnStep): TurnPosition = TurnPosition.entries[steps.indexOf(step)]

    // ---- Counter kinds: "+1/+1", "loyalty", "charge", "keyword:{…}"

    private val ptName = Regex("([+-]\\d+)/([+-]\\d+)")
    private val namedKinds: Map<String, CounterKind> by lazy {
        listOf(CounterKind.Shield, CounterKind.Stun, CounterKind.Finality, CounterKind.Rad, CounterKind.Hone,
            CounterKind.Time, CounterKind.Level, CounterKind.Loyalty, CounterKind.Lore, CounterKind.Defense,
            CounterKind.Poison, CounterKind.Energy).associateBy(::serialName)
    }

    private fun serialName(kind: CounterKind): String =
        json.encodeToJsonElement(CounterKind.serializer(), kind).jsonObject["type"]!!.jsonPrimitive.content

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
    fun counter(typed: TypedCounter) = CounterEntry(counterKind(typed.kind), typed.count, typed.timestamp)
    fun counters(entries: List<CounterEntry>): List<TypedCounter> = entries.map(::counter).sortedBy { it.kind }

    /** The engine keeps its own order of counter kinds; ATW sorts them by name. Decoding uses the engine's order where
     * the ledger says it (see [CardLedger.counterOrder]), else ATW's. */
    fun counterOrder(entries: List<CounterEntry>): List<String>? =
        entries.map { counterName(it.kind) }.takeIf { it != it.sorted() }

    fun ordered(counters: List<TypedCounter>, order: List<String>?): List<CounterEntry> {
        val byName = counters.associateBy { it.kind }
        val names = order?.filter { it in byName }.orEmpty() + counters.map { it.kind }.filter { order?.contains(it) != true }
        return names.map { counter(byName.getValue(it)) }
    }

    // ---- Mana: the colour as a letter, the rest of the unit as JSON when there is any

    fun mana(unit: ManaUnit): PoolMana {
        val rest = unit.copy(color = null)
        return PoolMana(unit.color?.symbol?.toString() ?: "C", if (rest == ManaUnit(null)) "" else encode(ManaUnit.serializer(), rest))
    }

    fun mana(pool: PoolMana): ManaUnit {
        val color = if (pool.color == "C") null else Color.of(pool.color.single()) ?: error("Not a mana colour: ${pool.color}")
        val rest = if (pool.data.isEmpty()) ManaUnit(null) else decode(ManaUnit.serializer(), pool.data)
        return rest.copy(color = color)
    }

    // ---- Engine records (§4.3): each its own, by id

    const val TURN = "turn"
    const val COMBAT = "combat"
    const val LEDGER = "ledger"
    const val TRIGGERED = "triggered"
    private const val EFFECT = "effect:"
    private const val DELAYED = "delayed:"

    fun effectRecord(effect: EffectObject) =
        EngineRecord(EFFECT + effect.id.value, EngineRecordKind.Effect, effect.timestamp, encode(EffectObject.serializer(), effect))

    fun delayedRecord(trigger: DelayedTrigger) = EngineRecord(DELAYED + trigger.id.value, EngineRecordKind.DelayedTrigger,
        trigger.timestamp, encode(DelayedTrigger.serializer(), trigger))

    fun isEffect(record: EngineRecord) = record.kind == EngineRecordKind.Effect
    fun isDelayed(record: EngineRecord) = record.kind == EngineRecordKind.DelayedTrigger
    fun effect(record: EngineRecord): EffectObject = decode(EffectObject.serializer(), record.data)
    fun delayed(record: EngineRecord): DelayedTrigger = decode(DelayedTrigger.serializer(), record.data)

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

    fun stackItem(item: MtgStackItem): StackItem = decode(StackItem.serializer(), item.data)

    /** The entry of a spell being cast (CR 601.2a): its card is on the Stack, its item not made yet; no [MtgStackItem.data]. */
    fun casting(card: MtgRef, controller: String, timestamp: Long) =
        MtgStackItem(id = card, kind = MtgStackKind.Spell, controller = controller, timestamp = timestamp)

    fun target(target: TargetRef, refs: (ObjectId) -> MtgRef): MtgRef = when (target) {
        is TargetRef.Object -> refs(target.id)
        is TargetRef.Player -> MtgRef.Player(target.id.value)
    }
}

/**
 * What the engine knows about a card object that ATW's own fields don't hold (D22, D41), kept in [org.asyncttwiz.protocol.PlayCard.ledger]
 * as JSON; empty when every field is its default. Fields named "…Override" hold a value only where ATW's own field
 * can't say it (a face ATW shows as one card face, a controller outside the Battlefield).
 */
@Serializable
internal data class CardLedger(
    /** The engine's card id when it isn't ATW's: a token's (D35), which ATW names by its own id. */
    val card: String? = null,
    /** A token's definition as the engine made it (ATW's token card data only shows it). */
    val definition: CardDefinition? = null,
    val faceOverride: Int? = null,
    /** The engine's controller field where ATW's container doesn't say it ("": none). */
    val controllerOverride: String? = null,
    val ownerOverride: String? = null,
    val phasedOut: Boolean = false,
    val marks: List<ObjectMark> = emptyList(),
    val deathtouchDamage: Boolean = false,
    /** Who has controlled it since when (CR 302.6) where ATW's [org.asyncttwiz.protocol.PlayCard.enteredTurn] doesn't
     * say it ([Derived.controlSince]); [noControlSince]: none although ATW's would say one. */
    val controlSince: ControlSince? = null,
    val noControlSince: Boolean = false,
    val memory: Bindings = Bindings.EMPTY,
    val facts: ObjectFacts = ObjectFacts(),
    val goadedBy: List<PlayerId> = emptyList(),
    /** The engine's order of counter kinds when it isn't by name (ATW sorts [org.asyncttwiz.protocol.PlayCard.typedCounters]). */
    val counterOrder: List<String>? = null,
) {
    fun text(): String = if (this == EMPTY) "" else EngineCodec.encode(serializer(), this)

    companion object {
        val EMPTY = CardLedger()
        fun of(text: String): CardLedger = if (text.isEmpty()) EMPTY else EngineCodec.decode(serializer(), text)
    }
}

/** The engine's player facts ATW doesn't hold, in [org.asyncttwiz.protocol.PlayPlayer.ledger]. */
@Serializable
internal data class PlayerLedger(
    val startingLife: Int = 20,
    val lost: Boolean = false,
    val designations: List<Designation> = emptyList(),
    val ledger: Map<String, Int> = emptyMap(),
    val counterOrder: List<String>? = null,
) {
    fun text(): String = if (this == PlayerLedger()) "" else EngineCodec.encode(serializer(), this)

    companion object {
        fun of(text: String): PlayerLedger = if (text.isEmpty()) PlayerLedger() else EngineCodec.decode(serializer(), text)
    }
}

/** Turn facts ATW's turn doesn't hold (record [EngineCodec.TURN]). */
@Serializable
internal data class TurnExtras(
    val landsPlayed: Map<PlayerId, Int> = emptyMap(),
    /** Null: as [Derived.turnStarted] says (each player began the latest of their alternating turns). */
    val turnStarted: Map<PlayerId, Int>? = null,
    val extraTurns: List<PlayerId> = emptyList(),
    val skipFirstDraw: Boolean = true,
)

/** The open flow of a shared call (record [org.asyncttwiz.protocol.Automatum.CONTINUATION_ID]): the engine's pending
 * decision and continuation, and whose view of the game it was made in (D60): a flow is resumed in that view. */
@Serializable
data class FlowRecord(val viewer: String? = null, val pending: Pending)

/** One step of the engine's tagged-event log (D33) carried in a history event's [org.asyncttwiz.protocol.MtgTag.data]. */
@Serializable
internal sealed interface LogOp {
    @Serializable @SerialName("add") data class Add(val entry: LoggedChange) : LogOp
    /** The last logged event is taken back (a cancelled cast, [Change.Reverted]). */
    @Serializable @SerialName("drop") data object Drop : LogOp
}

/** What an automated history event's tag carries for the engine: its part of the event log. */
@Serializable
internal data class TagData(val log: List<LogOp> = emptyList())

/**
 * How a tag writes its [TagData] (D33): every card object the log names carries the card's whole definition, and what
 * everyone saw of it its printed abilities again. Where that definition is the game's own card data of the ATW card the
 * object is (any card but a token), the tag writes a reference instead ([CARD] for the definition, [PRINTED] for a
 * printed ability as the definition has it), and reading the tag puts the definition back from the board ([of]).
 * Tags stay small (a board wipe of complex creatures fits the server's limits) and name no hidden card (a face-down
 * spell, a card moved within a library): the reference says only which card, which the event's actions name anyway.
 * Tokens' definitions stay written out: a token that left the game is on no board to read it from.
 */
internal class TagCodec(private val definition: (CardId) -> CardDefinition?) {
    private val definitions = mutableMapOf<CardId, JsonElement?>()
    private fun definitionJson(card: CardId): JsonElement? =
        definitions.getOrPut(card) { definition(card)?.let { EngineCodec.json.encodeToJsonElement(CardDefinition.serializer(), it) } }

    fun encode(data: TagData): String = rewrite(EngineCodec.json.encodeToJsonElement(TagData.serializer(), data), compact = true).toString()

    /** @throws IllegalArgumentException when the tag can't be read (another shape, or a card the board doesn't have). */
    fun decode(text: String): TagData =
        EngineCodec.json.decodeFromJsonElement(TagData.serializer(), rewrite(EngineCodec.json.parseToJsonElement(text), compact = false))

    /** The card object named by [obj]'s [key] ("c:<card>#<count>"). */
    private fun cardOf(obj: JsonObject, key: String): CardId? {
        val id = (obj[key] as? JsonPrimitive)?.takeIf { it.isString && it.content.startsWith("c:") } ?: return null
        return (runCatching { EngineCodec.json.decodeFromJsonElement(ObjectId.serializer(), id) }.getOrNull() as? ObjectId.Card)?.card
    }

    /** Face [face]'s printed ability [index] in [definition]'s JSON. */
    private fun printed(definition: JsonElement?, face: Int, index: Int): JsonElement? =
        ((((definition as? JsonObject)?.get("faces") as? JsonArray)?.getOrNull(face) as? JsonObject)?.get("abilities") as? JsonArray)?.getOrNull(index)

    /**
     * [element] with references written ([compact]) or read back. A card object's "definition" is its card's; an
     * "ability" with a printed "origin" is one of [holder]'s: the card of the nearest enclosing object (a Stack item's
     * source, else an object's own card), as a seen snapshot's abilities and a Stack item's ability are.
     */
    private fun rewrite(element: JsonElement, compact: Boolean, holder: CardId? = null): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map { rewrite(it, compact, holder) })
        is JsonObject -> {
            val card = cardOf(element, "id")
            val here = cardOf(element, "source") ?: card ?: holder
            val origin = element["origin"] as? JsonObject
            val face = (origin?.get("face") as? JsonPrimitive)?.content?.toIntOrNull()
            val index = (origin?.get("index") as? JsonPrimitive)?.content?.toIntOrNull()
            val printedAt = if ((origin?.get("type") as? JsonPrimitive)?.content == "printed" && face != null && index != null && here != null)
                Triple(here, face, index) else null
            JsonObject(element.mapValues { (key, value) ->
                when {
                    card != null && key == "definition" -> if (compact) {
                        if (value is JsonObject && value == definitionJson(card)) JsonPrimitive(CARD) else value
                    } else if (value == JsonPrimitive(CARD)) definitionJson(card) ?: throw IllegalArgumentException("no card data for $card")
                    else value
                    printedAt != null && key == "ability" -> {
                        val (of, f, i) = printedAt
                        if (compact) {
                            if (value is JsonObject && value == printed(definitionJson(of), f, i)) JsonPrimitive(PRINTED) else value
                        } else if (value == JsonPrimitive(PRINTED))
                            printed(definitionJson(of), f, i) ?: throw IllegalArgumentException("no printed ability $f.$i of $of")
                        else value
                    }
                    else -> rewrite(value, compact, here)
                }
            })
        }
        else -> element
    }

    companion object {
        const val CARD = "@card"
        const val PRINTED = "@printed"

        /** References resolved against [state]'s cards and [definitions] (by ATW definition digest): a card that isn't a
         * token, by its id. The same function writes and reads a tag, so it reads back exactly what was written. */
        fun of(state: org.asyncttwiz.protocol.PlayState, definitions: Map<String, CardDefinition>): TagCodec {
            val cards = state.board.cards.associateBy { it.id }
            return TagCodec { id -> cards[id.value]?.takeIf { !it.token }?.let { definitions[it.definition] } }
        }
    }
}

/** The waiting triggers record ([EngineCodec.TRIGGERED]). */
internal val triggeredSerializer: KSerializer<List<WaitingTrigger>> = kotlinx.serialization.builtins.ListSerializer(WaitingTrigger.serializer())
internal val ledgerSerializer: KSerializer<Map<String, Int>> =
    kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.serializer<String>(), kotlinx.serialization.serializer<Int>())
internal val combatSerializer: KSerializer<CombatState> = CombatState.serializer()

