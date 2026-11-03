package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Keyword
import arcanum.api.Layer
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.view.Characteristics
import arcanum.engine.view.EffectSource
import arcanum.engine.view.ExplainedEffect
import arcanum.engine.view.View
import org.asyncttwiz.protocol.CardVisibility
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone

/**
 * What a card on the Battlefield shows while Automatum is on (integration spec §3 I1): the engine's view of it next to
 * its values after layer 1 (printed, copied or face down, [arcanum.engine.view.ObjectView.base]).
 *
 * - [power]/[toughness]: after every continuous effect; null when the engine gives it none (not a creature, CR 208.3).
 * - [untilEndOfTurn]: an until-end-of-turn effect changes its power or toughness.
 * - [gained]/[lost]: keyword abilities it has that its base values don't, and the other way round (layer 6).
 * - [typeLine]/[colors]: its type line and colours when an effect changed them (layers 4, 5); null when unchanged.
 * - [counters]: its typed counters (§4.2) as "+1/+1 ×2", "charge ×1", "Flying counter ×1".
 * - [keywords]: every keyword ability it has.
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
    val counters: List<String> = emptyList(),
    /** Every keyword ability it has now ("Flying", "First strike"), as the engine works them out (layer 6). */
    val keywords: List<String> = emptyList(),
) {
    /** The computed P/T as the corner shows it ("3/3"); null when the engine gives the card no power and toughness. */
    val corner: String? get() = if (power != null && toughness != null) "$power/$toughness" else null

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

    /** The short marks the card shows besides its P/T: changed types and colours, keywords gained and lost, counters. */
    val marks: List<String> get() = listOfNotNull(typeLine, colors) + gained + lost.map { "no $it" } + counters
}

/**
 * The engine's view of an ATW board while Automatum is on (I1): built afresh from [PlayState] (I-D7) with the game's
 * card definitions; [cards] by ATW card id for the Battlefield cards the engine gives values. When the board holds
 * something the engine can't take over ([unsupported], [EngineState.unsupported]) nothing is computed and every card
 * keeps its manual display. [explain] says where a card's values come from (Inspect).
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
        val explanation = Engine.explain(state, id) ?: return emptyList()
        fun name(source: ObjectId?): String? {
            if (source == null) return null
            val obj = state.obj(source) ?: (source as? ObjectId.Card)?.let { c ->
                state.objects.values.lastOrNull { (it.id as? ObjectId.Card)?.card == c.card } }
                ?: return null
            if (obj.faceDown || obj.zone !in PUBLIC) return "a card"
            return view[obj.id]?.name ?: obj.definition?.faces?.getOrNull(obj.face)?.name
        }
        fun source(step: ExplainedEffect): String = when (val s = step.source) {
            is EffectSource.StaticAbility -> if (s.obj == id) "its own ability" else name(s.obj) ?: "an ability"
            is EffectSource.EffectObject -> {
                val effect = state.effects[s.effect]
                val by = name(effect?.source) ?: "an effect"
                listOfNotNull(by, effect?.duration?.let(::duration)).joinToString(" ")
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

    private fun objectOf(cardId: String): ObjectId? {
        val state = state ?: return null
        val engine = ids?.engine(cardId) ?: return null
        return state.objects.keys.firstOrNull { (it as? ObjectId.Card)?.card == engine && state.objects[it]?.zone == Zone.Battlefield }
    }

    companion object {
        private val PUBLIC = setOf(Zone.Battlefield, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command)
        private val PT_LAYERS = setOf(Layer.PowerToughnessDefining, Layer.PowerToughnessSet, Layer.PowerToughnessModify,
            Layer.PowerToughnessSwitch)

        /** Nothing computed (Automatum off, or the board not built yet). */
        val NONE = ComputedBoard(emptyMap(), emptyList(), 0, emptyList(), null, null, null)

        /** Nothing computed because [reason] (the device couldn't work the board out): every card stays manual. */
        fun failed(reason: String) = ComputedBoard(emptyMap(), listOf(reason), 0, emptyList(), null, null, null)

        /**
         * The engine's view of [play] with the game's card [definitions] (by ATW definition digest; a card missing
         * from it plays with its printed values, I-D21), as [viewer] is shown it. Blocking work: one engine view.
         */
        fun of(play: PlayState, definitions: Map<String, CardDefinition>, viewer: String): ComputedBoard {
            val (manualAbilities, manualCards) = manual(play, definitions, viewer)
            val unsupported = EngineState.unsupported(play, definitions, viewer)
            if (unsupported.isNotEmpty()) return ComputedBoard(emptyMap(), unsupported, manualAbilities, manualCards, null, null, null)
            // Full information (I-D27): only Battlefield cards are shown, each as anyone may see it (a face-down
            // permanent is a nameless 2/2, CR 708.2a).
            val state = try {
                EngineState.from(play, definitions)
            } catch (problem: Unsupported) {
                return ComputedBoard(emptyMap(), problem.reasons, manualAbilities, manualCards, null, null, null)
            }
            val view = Engine.view(state)
            val ids = CardIds.of(play.board)
            val battlefield = play.board.containers.filter { it.zone == PlayZone.BATTLEFIELD }.flatMap { it.cards }.toSet()
            val cards = LinkedHashMap<String, ComputedCard>()
            for (card in play.board.cards) {
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
                    counters = card.typedCounters.filter { it.count != 0 }.map { "${counterName(it.kind)} ×${it.count}" },
                    keywords = chars.keywords.map(Keyword::text),
                )
            }
            return ComputedBoard(cards, emptyList(), manualAbilities, manualCards, state, view, ids)
        }

        private fun manual(play: PlayState, definitions: Map<String, CardDefinition>, viewer: String): Pair<Int, List<Pair<String, Int>>> {
            val byDigest = play.definitions.associateBy { it.digest }
            var total = 0
            val seen = mutableSetOf<String>()
            val shown = LinkedHashMap<String, Int>()
            for (card in play.board.cards) {
                // I-D27: only cards the viewer may see count, so the number tells nothing of hidden ones
                if (!CardVisibility.isVisibleFaceUp(play.board, card, viewer)) continue
                val definition = definitions[card.definition] ?: byDigest[card.definition]?.definition?.let(PrintedCards::definition) ?: continue
                val count = definition.faces.sumOf { face -> face.abilities.count { it is Ability.Unparsed } }
                if (count == 0) continue
                if (seen.add(card.definition)) total += count
                shown[definition.faces.firstOrNull()?.name ?: "A card"] = count
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

        private fun duration(d: Duration): String? = when (d) {
            Duration.EndOfTurn -> "until end of turn"
            Duration.EndOfCombat -> "until end of combat"
            Duration.UntilYourNextTurn -> "until its controller's next turn"
            Duration.UntilEndOfYourNextTurn -> "until the end of its controller's next turn"
            is Duration.UntilStep, is Duration.ThroughNextStep -> "until a later step"
            is Duration.AsLongAs -> "for as long as its condition holds"
            is Duration.UntilLeaves -> "until its source leaves the battlefield"
            is Duration.UntilCast -> "until the exiled card is cast"
            is Duration.UntilEndOfNextTurnOf -> "until the end of that player's next turn"
            Duration.Indefinitely -> null
        }

        /** A typed counter kind as players read it: "+1/+1", "Flying counter", "charge". */
        fun counterName(kind: String): String = when (val k = runCatching { EngineCodec.counterKind(kind) }.getOrNull()) {
            is CounterKind.Keyword -> "${k.keyword.text} counter"
            is CounterKind.Named -> k.name
            null -> kind
            else -> EngineCodec.counterName(k)
        }
    }
}
