package org.asyncttwiz.automatum

import arcanum.engine.Inspect
import org.asyncttwiz.protocol.Faults
import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Keyword
import arcanum.api.Layer
import arcanum.api.Zone
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.view.Characteristics
import arcanum.engine.view.EffectSource
import arcanum.engine.view.ExplainedEffect
import arcanum.engine.view.View
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.CardVisibility
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.SubmittedDefinition

/**
 * What a card on the Battlefield shows while Automatum is on (integration spec §3 I1): the engine's view of it next to
 * its values after layer 1 (printed, copied or face down, [arcanum.engine.view.ObjectView.base]).
 *
 * - [power]/[toughness]: after every continuous effect; null when the engine gives it none (not a creature, CR 208.3).
 * - [untilEndOfTurn]: an until-end-of-turn effect changes its power or toughness.
 * - [gained]/[lost]: keyword abilities it has that its base values don't, and the other way round (layer 6).
 * - [typeLine]/[colors]: its type line and colours when an effect changed them (layers 4, 5); null when unchanged.
 * - [counters]: its typed counters (§4.2), each kind once ([ShownCounter]).
 * - [primary]: TBL-021: the counter its rules make primary, for a permanent without P/T: a planeswalker's loyalty, a battle's
 *   defense, a Saga's lore (count 0 when it has none); null for one with P/T or with no such counter kind.
 * - [keywords]: every keyword ability it has.
 * - [states]: TBL-133: the states the rules keep on it ("Ring-bearer", "Goaded", "Monstrous", [CardStates.words]).
 */
data class ComputedCard(
    val cardId: String,
    val power: Int?,
    val toughness: Int?,
    val basePower: Int?,
    val baseToughness: Int?,
    val untilEndOfTurn: Boolean = false,
    val gained: List<String> = emptyList(),
    val lost: List<String> = emptyList(),
    val typeLine: String? = null,
    val colors: String? = null,
    val counters: List<ShownCounter> = emptyList(),
    val primary: ShownCounter? = null,
    /** Every keyword ability it has now ("Flying", "First strike"), as the engine works them out (layer 6). */
    val keywords: List<String> = emptyList(),
    val states: List<String> = emptyList(),
) {
    /** The computed P/T as the corner shows it ("3/3"); for a permanent without P/T its primary counter's count (TBL-021:
     * "4" loyalty); null when it has neither. */
    val corner: String? get() = if (power != null && toughness != null) "$power/$toughness" else primary?.count?.toString()

    /** The counters shown as bubbles on its art (owner, 2026-11-11): every kind but the one its corner shows. */
    val bubbles: List<ShownCounter> get() = counters.filter { primary == null || it.kind != primary.kind }

    /** Up (1) when the computed P/T is above the base in one value and below it in none, down (-1) the other way
     * round, 0 unchanged, null when it went up in one and down in the other (a switch). */
    val trend: Int?
        get() {
            val p = (power ?: return 0) - (basePower ?: return 0)
            val t = (toughness ?: return 0) - (baseToughness ?: return 0)
            return when {
                p == 0 && t == 0 -> 0
                p >= 0 && t >= 0 -> 1
                p <= 0 && t <= 0 -> -1
                else -> null
            }
        }

    /** The short marks the card shows besides its P/T: its states first (the chip is cut short, TBL-113, TBL-133), then
     * changed types and colours, keywords gained and lost (its counters are bubbles, [bubbles]). */
    val marks: List<String> get() = states + listOfNotNull(typeLine, colors) + gained + lost.map { "no $it" }
}

/**
 * [count] counters of one kind on a card as the table shows them (owner, 2026-11-11: counters as visuals): [kind] the engine's
 * kind (null when it can't be read), [name] as players read it ("+1/+1", "loyalty", "Flying counter", "charge").
 */
data class ShownCounter(val kind: CounterKind?, val name: String, val count: Int) {
    /** The words Inspect lists it with ("+1/+1 counter ×2", "loyalty counter"). */
    val words: String get() = (if (name.endsWith(" counter")) name else "$name counter").let { if (count == 1) it else "$it ×$count" }
}

/** One continuous effect on a card as Inspect lists it ([ComputedBoard.effects]): what it does ("+3/+3 until end of
 * turn") and, when known, the name of what it comes from ([source]). */
data class EffectChange(val text: String, val source: String? = null)

/**
 * The engine's view of a game's board (I1): [cards] by ATW card id for the Battlefield cards the engine gives values,
 * from the game's own engine state and view ([of]). When the device couldn't work the board out ([failed], the reason in
 * [unsupported]) nothing is computed and every card keeps its plain display. [explain] says where a card's values come from (Inspect).
 */
class ComputedBoard private constructor(
    val cards: Map<String, ComputedCard>,
    val unsupported: List<String>,
    /** Abilities the engine leaves to the players (unparsed, I-D21) of the cards [viewer] may see (I-D27), in all. */
    val manualAbilities: Int,
    /** The cards with manual abilities [viewer] may see (I-D27), by name, with how many each has. */
    val manualCards: List<Pair<String, Int>>,
    private val state: GameState?,
    private val view: View?,
    private val ids: CardIds?,
    /** ZON-094: the states of cards in exile the rules keep ("Plotted", "Foretold", "Warped"), by ATW card id; each is public
     * even for a card exiled face down. */
    val exileStates: Map<String, List<String>> = emptyMap(),
) {
    /**
     * Where [cardId]'s values come from, one line each, for Inspect (spec §7.1): its power and toughness as
     * "2/2 printed, +1/+1 Glorious Anthem, +3/+3 Giant Growth until end of turn", then the other changes ("Flying —
     * Sky Banner"). Empty when nothing is computed for the card. Names only what [viewer] may see (I-D27).
     */
    fun explain(cardId: String, viewer: String): List<String> {
        val state = state ?: return emptyList()
        val view = view ?: return emptyList()
        cards[cardId] ?: return emptyList()
        val id = objectOf(cardId) ?: return emptyList()
        val explanation = Inspect.explain(state, id) ?: return emptyList()
        fun name(source: ObjectId?): String? {
            if (source == null) return null
            val obj = state.obj(source) ?: (source as? ObjectId.Card)?.let { c ->
                state.objects.values.lastOrNull { (it.id as? ObjectId.Card)?.card == c.card } }
                ?: return Inspect.lastKnown(state, source)?.characteristics?.name?.takeIf { LastKnown.public(state, source) }
            if (obj.faceDown || obj.zone !in PUBLIC) return "a card"
            return view[obj.id]?.name ?: obj.definition?.faces?.getOrNull(obj.face)?.name
        }
        fun source(step: ExplainedEffect): String = when (val s = step.source) {
            is EffectSource.StaticAbility -> if (s.obj == id) "its own ability" else name(s.obj)
                ?: Faults.placeholder("ComputedBoard", "static ability without its source", "an ability", mapOf("object" to id.toString(), "source" to s.obj.toString()))
            is EffectSource.EffectObject -> {
                val effect = state.effects[s.effect]
                val by = name(effect?.source) ?: if (effect?.source == null) "an effect"
                    else Faults.placeholder("ComputedBoard", "effect without its source's name", "an effect",
                        mapOf("object" to id.toString(), "effect" to s.effect.toString()))
                listOfNotNull(by, effect?.let { DurationText(state, view, viewer).of(it, id) }).joinToString(" ")
            }
            is EffectSource.Counters -> "counters"
        }
        val lines = mutableListOf<String>()
        val base = explanation.base
        val ptSteps = explanation.steps.filter { it.layer in PT_LAYERS }
        val final = explanation.characteristics
        if (final.power != null && final.toughness != null) {
            val parts = mutableListOf("${pt(base.power, base.toughness)} ${if (state.obj(id)?.faceDown == true) "face down" else "printed"}")
            for (step in ptSteps) parts += when (step.layer) {
                Layer.PowerToughnessDefining -> "${pt(step.after.power, step.after.toughness)} ${source(step)}"
                Layer.PowerToughnessSet -> "set to ${pt(step.after.power, step.after.toughness)} ${source(step)}"
                Layer.PowerToughnessSwitch -> "switched ${source(step)}"
                else -> "${signed((step.after.power ?: 0) - (step.before.power ?: 0))}/" +
                    "${signed((step.after.toughness ?: 0) - (step.before.toughness ?: 0))} ${source(step)}"
            }
            lines += "${final.power}/${final.toughness}: " + parts.joinToString(", ")
        }
        for (step in explanation.steps.filter { it.layer !in PT_LAYERS }) {
            val change = when (step.layer) {
                Layer.Type -> typeLine(step.after)
                Layer.Color -> colors(step.after)
                Layer.Ability -> {
                    val (gained, lost) = keywordChange(step.before, step.after)
                    (gained + lost.map { "no $it" }).ifEmpty {
                        listOf(if (step.after.abilities.size > step.before.abilities.size) "an ability" else "loses abilities")
                    }.joinToString(", ")
                }
                Layer.Control -> "controlled by ${step.controllerAfter?.value?.let { if (it == viewer) "you" else "the opponent" } ?: "nobody"}"
                else -> continue
            }
            lines += "$change — ${source(step)}"
        }
        return lines
    }

    /**
     * TBL-104 (owner, 2026-11-06): what continuous effects changed on each of [cardIds] (Battlefield cards the board
     * computes), one [EffectChange] per effect in the order applied ("+3/+3, trample until end of turn", "Flying",
     * "Becomes blue"), each with its source's name as [viewer] may see it; counters and a card's own characteristic-
     * defining ability aren't changes here. [names] names the players by account. Cards with no changes are left out;
     * one run of the layers for all of them.
     */
    fun effects(cardIds: List<String>, viewer: String, names: Map<String, String> = emptyMap()): Map<String, List<EffectChange>> {
        val state = state ?: return emptyMap()
        val view = view ?: return emptyMap()
        val objects = cardIds.filter { it in cards }.mapNotNull { card -> objectOf(card)?.let { card to it } }
        if (objects.isEmpty()) return emptyMap()
        val explanations = Inspect.explain(state, objects.map { it.second })
        val durations = DurationText(state, view, viewer, names)
        return objects.mapNotNull { (card, id) ->
            val explanation = explanations[id] ?: return@mapNotNull null
            val changes = explanation.effects.mapNotNull { effect ->
                val source = effect.source
                if (source is EffectSource.Counters) return@mapNotNull null
                if (source is EffectSource.StaticAbility && source.characteristicDefining && source.obj == id) return@mapNotNull null
                val parts = effect.steps.flatMap { step -> stepParts(step, viewer, names) }.distinct()
                if (parts.isEmpty()) return@mapNotNull null
                val made = (source as? EffectSource.EffectObject)?.let { state.effects[it.effect] }
                val lasts = effect.duration?.let { durations.of(it, made, id) }
                val text = (parts.joinToString(", ") + (lasts?.let { " $it" } ?: ""))
                    .replaceFirstChar { it.uppercase() }
                val from = when {
                    source is EffectSource.StaticAbility && source.obj == id -> "Its own ability"
                    else -> visibleName(state, view, effect.madeBy)
                }
                EffectChange(text, from)
            }
            changes.takeIf { it.isNotEmpty() }?.let { card to it }
        }.toMap()
    }

    /**
     * TBL-133: [cardIds]' states as Inspect lists them ([CardStates.lines]), in [viewer]'s words; [names] names the players
     * by account. Only what the state holds; nothing is worked out.
     */
    fun states(cardIds: List<String>, viewer: String, names: Map<String, String> = emptyMap()): Map<String, List<EffectChange>> {
        val state = state ?: return emptyMap()
        return cardIds.filter { it in cards }.mapNotNull { card ->
            val o = objectOf(card)?.let { state.obj(it) } ?: return@mapNotNull null
            CardStates.lines(state, o, viewer, names).takeIf { it.isNotEmpty() }?.let { card to it }
        }.toMap()
    }

    /**
     * TBL-134: what continuous effects tell each of [cardIds] (on the Battlefield, or a spell on the Stack) it can't or must do,
     * from the board's own view ([Inspect.restrictions]): "Can't block until end of turn", with its source as [viewer] may see
     * it. A Ring-bearer's rule from the Ring is its own line ([states]).
     */
    fun restrictions(cardIds: List<String>, viewer: String, names: Map<String, String> = emptyMap()): Map<String, List<EffectChange>> {
        val state = state ?: return emptyMap()
        val view = view ?: return emptyMap()
        val durations = DurationText(state, view, viewer, names)
        return cardIds.mapNotNull { card ->
            val id = ids?.engine(card)?.let { engine -> state.objects.keys.firstOrNull {
                (it as? ObjectId.Card)?.card == engine && state.objects[it]?.zone.let { z -> z == Zone.Battlefield || z == Zone.Stack } } } ?: return@mapNotNull null
            val changes = Inspect.restrictions(state, view, id).mapNotNull { restriction ->
                val from = restriction.madeBy?.let { state.obj(it) }
                if (from?.zone == Zone.Command && from.definition?.faces?.firstOrNull()?.name == CardStates.RING) return@mapNotNull null
                val made = restriction.effect?.let { state.effects[it] }
                val lasts = restriction.duration?.let { durations.of(it, made, id) }
                EffectChange(restriction.text + (lasts?.let { " $it" } ?: ""), visibleName(state, view, restriction.madeBy))
            }
            changes.takeIf { it.isNotEmpty() }?.let { card to it }
        }.toMap()
    }

    /** What one layer step changed, in a few words each ("+3/+3", "trample", "loses flying", "becomes blue"). */
    private fun stepParts(step: ExplainedEffect, viewer: String, names: Map<String, String>): List<String> = when (step.layer) {
        Layer.PowerToughnessDefining, Layer.PowerToughnessSet -> listOf("base ${pt(step.after.power, step.after.toughness)}")
        Layer.PowerToughnessModify -> listOf("${signed((step.after.power ?: 0) - (step.before.power ?: 0))}/" +
            signed((step.after.toughness ?: 0) - (step.before.toughness ?: 0)))
        Layer.PowerToughnessSwitch -> listOf("power and toughness switched")
        Layer.Type -> listOf("becomes ${typeLine(step.after)}")
        Layer.Color -> listOf("becomes ${colors(step.after)}")
        Layer.Ability -> {
            val (gained, lost) = keywordChange(step.before, step.after)
            (gained.map { it.lowercase() } + lost.map { "loses ${it.lowercase()}" }).ifEmpty {
                listOf(when {
                    step.after.abilities.isEmpty() -> "loses all abilities"
                    step.after.abilities.size > step.before.abilities.size -> "gains an ability"
                    else -> "loses an ability"
                })
            }
        }
        Layer.Control -> listOf("controlled by " + (step.controllerAfter?.value?.let { if (it == viewer) "you" else names[it] ?: "the opponent" }
            ?: "nobody"))
        Layer.Text -> listOf("text changed")
        else -> emptyList()
    }

    /** The name of [source] (an object or its card's latest object) as [viewer] may see it: "a card" when hidden. */
    private fun visibleName(state: GameState, view: View, source: ObjectId?): String? {
        if (source == null) return null
        val obj = state.obj(source) ?: (source as? ObjectId.Card)?.let { c ->
            state.objects.values.lastOrNull { (it.id as? ObjectId.Card)?.card == c.card } } ?: return null
        if (obj.faceDown || obj.zone !in PUBLIC) return "a card"
        return view[obj.id]?.name ?: obj.definition?.faces?.getOrNull(obj.face)?.name
    }

    private fun objectOf(cardId: String): ObjectId? {
        val state = state ?: return null
        val engine = ids?.engine(cardId) ?: return null
        return state.objects.keys.firstOrNull { (it as? ObjectId.Card)?.card == engine && state.objects[it]?.zone == Zone.Battlefield }
    }

    companion object {
        private val PUBLIC = setOf(Zone.Battlefield, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command)
        private val PT_LAYERS = setOf<Layer>(Layer.PowerToughnessDefining, Layer.PowerToughnessSet, Layer.PowerToughnessModify,
            Layer.PowerToughnessSwitch)

        /** Nothing computed (Automatum off, or the board not built yet). */
        val NONE = ComputedBoard(emptyMap(), emptyList(), 0, emptyList(), null, null, null)

        /** Nothing computed because [reason] (the device couldn't work the board out): every card stays manual. */
        fun failed(reason: String) = ComputedBoard(emptyMap(), listOf(reason), 0, emptyList(), null, null, null)

        /**
         * [of] for a game on engine state (I-D56; engine-responsiveness R3, R4a): [board] is the board shown of [state]
         * ([EngineProjection]) with its card data [submitted], [state] the game's own state (full information; kept for
         * [explain], so never changed afterwards) and [view] its view, which the caller already has (the engine thread's
         * one view of the state), so nothing is copied or worked out again.
         */
        fun of(board: BoardState, submitted: List<SubmittedDefinition>, definitions: Map<String, CardDefinition>, viewer: String,
            state: GameState, view: View): ComputedBoard {
            val (manualAbilities, manualCards) = manual(board, submitted, definitions, viewer)
            return computed(board, manualAbilities, manualCards, state, view)
        }

        private fun computed(board: BoardState, manualAbilities: Int, manualCards: List<Pair<String, Int>>, state: GameState,
            view: View): ComputedBoard {
            val ids = CardIds()
            val battlefield = board.containers.filter { it.zone == PlayZone.BATTLEFIELD }.flatMap { it.cards }.toSet()
            val cards = LinkedHashMap<String, ComputedCard>()
            for (card in board.cards) {
                if (card.id !in battlefield) continue
                val id = ObjectId.Card(ids.engine(card.id), card.zoneChanges)
                val o = view[id] ?: continue
                val chars = o.characteristics ?: continue
                val base = o.base ?: chars
                val (gained, lost) = keywordChange(base, chars)
                val eot = state.effects.values.any { e ->
                    e.duration == Duration.EndOfTurn && e.locked?.contains(id) == true && e.mechanics.any { it.layer in PT_LAYERS }
                }
                cards[card.id] = ComputedCard(
                    cardId = card.id, power = chars.power, toughness = chars.toughness,
                    basePower = base.power, baseToughness = base.toughness, untilEndOfTurn = eot,
                    gained = gained, lost = lost,
                    typeLine = typeLine(chars).takeIf { it != typeLine(base) },
                    colors = colors(chars).takeIf { chars.colors != base.colors },
                    counters = shownCounters(card.typedCounters),
                    primary = primaryCounter(chars, shownCounters(card.typedCounters)),
                    keywords = chars.keywords.map(Keyword::text),
                    states = o.obj?.let { CardStates.words(state, it) }.orEmpty(),
                )
            }
            val exile = board.containers.filter { it.zone == PlayZone.EXILE }.flatMap { it.cards }.toSet()
            val exileStates = board.cards.filter { it.id in exile }.mapNotNull { card ->
                val o = state.obj(ObjectId.Card(ids.engine(card.id), card.zoneChanges)) ?: return@mapNotNull null
                if (o.marks.isEmpty()) null else CardStates.words(state, o).takeIf { it.isNotEmpty() }?.let { card.id to it }
            }.toMap()
            return ComputedBoard(cards, emptyList(), manualAbilities, manualCards, state, view, ids, exileStates)
        }

        private fun manual(board: BoardState, submitted: List<SubmittedDefinition>, definitions: Map<String, CardDefinition>,
            viewer: String): Pair<Int, List<Pair<String, Int>>> {
            val byDigest = submitted.associateBy { it.digest }
            var total = 0
            val seen = mutableSetOf<String>()
            val shown = LinkedHashMap<String, Int>()
            for (card in board.cards) {
                // I-D27: only cards the viewer may see count, so the number tells nothing of hidden ones
                if (!CardVisibility.isVisibleFaceUp(board, card, viewer)) continue
                val definition = definitions[card.definition] ?: byDigest[card.definition]?.definition?.let(PrintedCards::definition) ?: continue
                val count = definition.faces.sumOf { face -> face.abilities.count { it is Ability.Unparsed } }
                if (count == 0) continue
                if (seen.add(card.definition)) total += count
                shown[definition.faces.firstOrNull()?.name ?: Faults.placeholder("ComputedBoard", "card definition without a face", "A card",
                    mapOf("card" to card.id))] = count
            }
            return total to shown.entries.map { it.key to it.value }.sortedBy { it.first }
        }

        private fun keywordChange(before: Characteristics, after: Characteristics): Pair<List<String>, List<String>> {
            val had = before.keywords.map(Keyword::text).toSet()
            val has = after.keywords.map(Keyword::text).toSet()
            return (has - had).toList() to (had - has).toList()
        }

        private fun typeLine(c: Characteristics): String {
            val front = (c.supertypes.map { it.toString() } + c.types.map(CardType::toString)).joinToString(" ")
            val subtypes = if (c.allCreatureTypes) "every creature type" else c.subtypes.joinToString(" ") { it.name }
            return if (subtypes.isEmpty()) front else "$front — $subtypes"
        }

        private fun colors(c: Characteristics): String =
            c.colors.joinToString(" and ") { COLOR_NAMES.getValue(it.symbol) }.ifEmpty { "colorless" }

        private val COLOR_NAMES = mapOf('W' to "white", 'U' to "blue", 'B' to "black", 'R' to "red", 'G' to "green")

        private fun pt(power: Int?, toughness: Int?) = "${power ?: 0}/${toughness ?: 0}"
        private fun signed(n: Int) = if (n < 0) "−${-n}" else "+$n"

        /** A typed counter kind as players read it: "+1/+1", "Flying counter", "charge". */
        fun counterName(kind: String): String = when (val k = runCatching { EngineCodec.counterKind(kind) }.getOrNull()) {
            is CounterKind.Keyword -> "${k.keyword.text} counter"
            is CounterKind.Named -> k.name
            null -> kind
            else -> EngineCodec.counterName(k)
        }

        /** A card's counters, each kind once, as the table shows them: bubbles on the battlefield and in exile (owner,
         * 2026-11-11; ZON-091), lines in Inspect (TBL-104). */
        fun shownCounters(counters: List<org.asyncttwiz.protocol.TypedCounter>): List<ShownCounter> = counters.filter { it.count != 0 }
            .map { ShownCounter(runCatching { EngineCodec.counterKind(it.kind) }.getOrNull(), counterName(it.kind), it.count) }

        /** TBL-021 (owner, 2026-11-11): the counter a permanent without P/T shows in its corner, the one its rules make primary:
         * a planeswalker's loyalty (CR 306.5c), a battle's defense (CR 310.4c), a Saga's lore (CR 714.2b); with its count, 0
         * when it has none. Null for a permanent with P/T (its corner is the P/T, the counter a bubble) or none of these. */
        fun primaryCounter(chars: Characteristics, counters: List<ShownCounter>): ShownCounter? {
            if (chars.power != null && chars.toughness != null) return null
            val kind = when {
                chars.isType(CardType.Planeswalker) -> CounterKind.Loyalty
                chars.isType(CardType.Battle) -> CounterKind.Defense
                chars.subtypes.any { it.name == "Saga" } -> CounterKind.Lore
                else -> return null
            }
            return counters.firstOrNull { it.kind == kind } ?: ShownCounter(kind, counterName(EngineCodec.counterName(kind)), 0)
        }
    }
}
