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
import arcanum.engine.state.GameObject
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
import org.asyncttwiz.protocol.BoardState
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

    // ---- Steps (CR 500.1; ATW's twelve manual positions name the engine's twelve steps in the same order, and the
    // first of two combat damage steps, CR 510.4, is ATW's own position, I-D31)

    private val steps = listOf(TurnStep.Untap, TurnStep.Upkeep, TurnStep.Draw, TurnStep.PrecombatMain,
        TurnStep.BeginningOfCombat, TurnStep.DeclareAttackers, TurnStep.DeclareBlockers, TurnStep.CombatDamage,
        TurnStep.EndOfCombat, TurnStep.PostcombatMain, TurnStep.End, TurnStep.Cleanup)

    fun step(position: TurnPosition): TurnStep =
        if (position == TurnPosition.FIRST_STRIKE_DAMAGE) TurnStep.CombatDamage else steps[TurnPosition.MANUAL.indexOf(position)]

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

    private const val EMBLEM = "emblem:"

    /** An emblem (CR 114, I-D25): the engine's command-zone object in [EngineRecord.data], and what ATW shows of it:
     * whose it is and its rules [text]. */
    fun emblemRecord(emblem: GameObject, text: String) = EngineRecord(emblemId(emblem), EngineRecordKind.Emblem,
        emblem.timestamp, encode(GameObject.serializer(), emblem), player = emblem.owner.value, text = text)

    fun emblemId(emblem: GameObject) = EMBLEM + (emblem.id as ObjectId.Card).card.value

    /** The emblem's rules text as its abilities write it ([AtwTokens.rulesText]); empty when they carry no text. */
    fun emblemText(emblem: GameObject): String =
        emblem.definition?.faces.orEmpty().joinToString("\n") { AtwTokens.rulesText(it) }.trim()

    fun isEmblem(record: EngineRecord) = record.kind == EngineRecordKind.Emblem
    /** finalreview-sync #2 (I-D26, CR 613.7): an object's stamp is ATW's field, which a redo moves; the copy in the engine
     * data keeps the stamp it was recorded with. */
    fun emblem(record: EngineRecord): GameObject = decode(GameObject.serializer(), record.data)
        .let { o -> record.timestamp?.takeIf { it != o.timestamp }?.let { o.copy(timestamp = it) } ?: o }

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

    fun isEffect(record: EngineRecord) = record.kind == EngineRecordKind.Effect
    fun isDelayed(record: EngineRecord) = record.kind == EngineRecordKind.DelayedTrigger
    /** The effect object of [record], stamped as ATW's field says (finalreview-sync #2, as [emblem]). */
    fun effect(record: EngineRecord): EffectObject = decode(EffectObject.serializer(), record.data)
        .let { e -> record.timestamp?.takeIf { it != e.timestamp }?.let { e.copy(timestamp = it) } ?: e }
    /** The delayed trigger of [record], stamped as ATW's field says (finalreview-sync #2, as [emblem]). */
    fun delayed(record: EngineRecord): DelayedTrigger = decode(DelayedTrigger.serializer(), record.data)
        .let { d -> record.timestamp?.takeIf { it != d.timestamp }?.let { d.copy(timestamp = it) } ?: d }

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

    /** The engine's Stack item, stamped as ATW's field says (finalreview-sync #2, as [emblem]). */
    fun stackItem(item: MtgStackItem): StackItem = decode(StackItem.serializer(), item.data)
        .let { i -> if (i.timestamp == item.timestamp) i else i.copy(timestamp = item.timestamp) }

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
    /** The engine's card id when it isn't ATW's: a token's or a copy's of a card (D35), which ATW names by its own id. */
    val card: String? = null,
    /** A token's or a copy's definition as the engine made it (ATW's token card data only shows it). */
    val definition: CardDefinition? = null,
    /** ATW's token card is the engine's copy of a card (CR 707.12), not a token: it ceases to exist outside the Stack and
     * the Battlefield (CR 704.5e), which ATW's tokens do too (TBL-103). */
    val copyOfCard: Boolean = false,
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
    /** A battle's protector (CR 310.9; CMB-012: the player who blocks for it). */
    val protector: PlayerId? = null,
    /** The engine's order of counter kinds when it isn't by name (ATW sorts [org.asyncttwiz.protocol.PlayCard.typedCounters]). */
    val counterOrder: List<String>? = null,
    /** A merged permanent's other components (mutate, CR 730.2, 702.140c), ATW card ids topmost first: above its own card and
     * under it. Each component's card lies on the same Battlefield, its zone-change count and timestamp the engine's of the
     * component as it merged ([arcanum.engine.state.MergedPart]); it is no permanent of its own ([EngineState.from]). */
    val mergedAbove: List<String> = emptyList(),
    val mergedBelow: List<String> = emptyList(),
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
    /** The turn [landsPlayed] counts (ATW's turn number): a turn changed by hand (I2–I3, before Automatum runs the
     * turns) starts with no lands played. Null in records made before it was kept. */
    val turn: Int? = null,
    /** Where the passes in a row of the game's priority were counted ([at]: ATW's turn number and step), while there are
     * any (#10): a step or turn changed by hand since starts the count again; also while a player other than the active
     * one holds priority, so the host keeps it with them in that step ([HostPriority.kept]). Null otherwise. */
    val passesAt: String? = null,
    // gtc
    /** Additional phases still to come this turn (the engine's `TurnState.extraPhases`, CR 500.8), with [phasesTurn]. */
    val extraPhases: List<TurnStep> = emptyList(),
    /** Where the turn's own order goes on once the additional phases are over (the engine's `TurnState.resumeAt`). */
    val resumeAt: TurnStep? = null,
    /** The turn (ATW's turn number) [extraPhases] and [resumeAt] belong to: a later turn has none (finalreview-combat #1). */
    val phasesTurn: Int? = null,
    /** [at] the declare attackers step whose attack the defending player has been paused for already
     * ([PausePoints.attackAnswered], finalreview-combat #7); null otherwise. */
    val attackAnswered: String? = null,
    /** Players' turns still to be skipped (the engine's `TurnState.skippedTurns`, CR 614.10). */
    val skippedTurns: List<PlayerId> = emptyList(),
    /** Control of players waiting for their next turn (the engine's `TurnState.controlNext`, CR 723.1). */
    val controlNext: List<arcanum.engine.state.PlayerControl> = emptyList(),
    /** The player controlled during this turn (the engine's `TurnState.controlled`, CR 723), with the turn (ATW's turn number)
     * it belongs to in [controlledTurn]: a later turn has none. */
    val controlled: arcanum.engine.state.PlayerControl? = null,
    val controlledTurn: Int? = null,
) {
    /** [controlled] when it is of [turn]. */
    fun controlled(turn: Int): arcanum.engine.state.PlayerControl? = controlled?.takeIf { controlledTurn == turn }

    /** [extraPhases] and [resumeAt] when they are of [turn], else none. */
    fun phases(turn: Int): Pair<List<TurnStep>, TurnStep?> =
        if (phasesTurn == turn) extraPhases to resumeAt else emptyList<TurnStep>() to null

    companion object {
        fun at(turn: Any, position: TurnPosition) = "$turn/${position.name}"
    }
}

/** The open flow of a shared call (record [org.asyncttwiz.protocol.Automatum.CONTINUATION_ID]): the engine's pending
 * decision and continuation, whose view of the game it was made in (D60): a flow is resumed in that view, and the
 * [lingering] objects of its unfinished resolution. */
@Serializable
data class FlowRecord(val viewer: String? = null, val pending: Pending, val lingering: List<Lingering> = emptyList())

/**
 * A token, or a copy of a card, that left the Battlefield or the Stack while the decision a flow waits for opened
 * (a destroyed token whose ability asks "may search" before it finishes): the engine keeps it in its new zone until
 * state-based actions are next checked (CR 111.7, 704.5d, 704.5e) and reads it there (last known information, CR
 * 608.2h), but ATW's token stopped existing at once (TBL-103), so players never see it there. [index]: its place in its
 * owner's library, hand or graveyard in the engine's order (bottom first); null in Exile (timestamp order).
 */
@Serializable
data class Lingering(val obj: GameObject, val index: Int? = null)

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

    fun encode(data: TagData): String = encode(TagData.serializer(), data)

    /** [value] with its cards' definitions by reference: engine data shared with both players (a shared flow's
     * continuation) names no hidden card either. */
    fun <T> encode(serializer: KSerializer<T>, value: T): String =
        rewrite(EngineCodec.json.encodeToJsonElement(serializer, value), compact = true).toString()

    /** What [encode] wrote. @throws IllegalArgumentException when it can't be read. */
    fun <T> decode(serializer: KSerializer<T>, text: String): T =
        EngineCodec.json.decodeFromJsonElement(serializer, rewrite(EngineCodec.json.parseToJsonElement(text), compact = false))

    /** @throws IllegalArgumentException when the tag can't be read (another shape, or a card the board doesn't have). */
    fun decode(text: String): TagData = decode(TagData.serializer(), text)

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
            // A card the device couldn't read has its printed values' definition (I-D21), as EngineState.from gives it
            val printed = mutableMapOf<String, CardDefinition?>()
            return TagCodec { id -> cards[id.value]?.takeIf { !it.token }?.let { card -> definitions[card.definition]
                ?: printed.getOrPut(card.definition) { PrintedCards.definition(state, card) } } }
        }
    }
}

/** The waiting triggers record ([EngineCodec.TRIGGERED]). */
internal val triggeredSerializer: KSerializer<List<WaitingTrigger>> = kotlinx.serialization.builtins.ListSerializer(WaitingTrigger.serializer())
internal val ledgerSerializer: KSerializer<Map<String, Int>> =
    kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.serializer<String>(), kotlinx.serialization.serializer<Int>())
internal val combatSerializer: KSerializer<CombatState> = CombatState.serializer()

