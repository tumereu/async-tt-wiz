package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CostPart
import arcanum.api.Mechanic
import arcanum.engine.Engine
import arcanum.engine.decision.AnnounceStage
import arcanum.engine.decision.Announced
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.view.AbilityInstance

/**
 * One way to cast or play a hand card (integration spec I-D50), a "way" chip above the raised card: the regular cast
 * (or a land's play) first, then the other ways the engine offers (another face or an Adventure, flashback, evoke,
 * other alternative costs, both halves fused). One way is chosen at a time.
 */
data class CastWayChip(
    /** The engine's [Input.Cast] or [Input.PlayLand]. */
    val input: Input,
    /** What the chip says: "Cast", "Play", a face's name, "Flashback", "Evoke", "Reel // Rend (fused)". */
    val name: String,
    /** The mana it costs now, in printed symbols ("{2}{R}"), without extras (X at its least); null when it costs none. */
    val cost: String?,
    /** The printed text of its alternative cost ("Evoke {R/G}{R/G}"), for the chip's details; null for the others. */
    val text: String?,
    /** Whether what the player has now pays it (with the extras chosen so far when it is the chosen way). */
    val affordable: Boolean,
)

/**
 * An optional additional cost of the spell (I-D50), an "extra" chip ("+ Kicker {4}"): kicker, multikicker, buyback,
 * offspring, gift, teamwork and the others the rules call optional additional costs (CR 601.2b, 702.33). Extras add to any
 * way; each is on or off, a [repeatable] one (multikicker) counts how often it is paid.
 */
data class CastExtraChip(
    /** "Kicker", "Buyback", "Offspring", "Gift", or "Optional cost". */
    val name: String,
    /** Its mana in printed symbols ("{4}"); null when it costs no mana (a gift, tapping creatures). */
    val cost: String?,
    /** Its printed text ("Kicker {4}"), for the chip's details. */
    val text: String?,
    val repeatable: Boolean,
    /** Whether what the player has now pays the chosen way with this extra on (once more, for a repeatable one). */
    val affordable: Boolean,
)

/**
 * The cost chips of a raised hand card (I-D50) for one choice ([way], [times]): the ways, the chosen way's extras, and
 * what Autotap would tap for it ([sources], ATW card ids; mana in the pool is spent first and taps nothing).
 */
data class HandCastOptions(
    val cardId: String,
    val ways: List<CastWayChip>,
    /** The chosen way, an index of [ways]. */
    val way: Int,
    val extras: List<CastExtraChip>,
    /** How often each of [extras] is paid (0: off), by index. */
    val times: List<Int>,
    val sources: Set<String>,
    /** Whether the choice can be paid now (the card is cast with it on its next tap). */
    val affordable: Boolean,
    /** The mana the choice costs in all, its extras included (X at its least); null when it costs none. */
    val total: String? = null,
) {
    val chosen: CastWayChip get() = ways[way]
}

/**
 * One complete way to cast or play a hand card (ZON-080, owner 2026-11-07), a chip above the raised card: a way of
 * [HandCastOptions.ways] with every optional extra decided ("Cast {1}{G}", "Cast with offspring {3}{G}"). A tap casts it at
 * once.
 */
data class CastChip(
    /** The way, an index of the card's ways ([HandCastOptions.ways]). */
    val way: Int,
    /** How often each optional extra of the way is paid (0: not), in the engine's order. */
    val times: List<Int>,
    /** The engine's [Input.Cast], [Input.PlayLand] or [Input.Activate]. */
    val input: Input,
    /** What the chip says: the way's name, with the extras paid ("Cast with kicker and offspring"). */
    val name: String,
    /** The mana it costs in all, extras included (X at its least); null when it costs none. */
    val cost: String?,
    /** The printed text of its alternative cost and extras, for the chip's details; null when none. */
    val text: String?,
    /** Whether what the player has now pays it. */
    val affordable: Boolean,
    /** The sources Autotap would tap for it (ATW card ids). */
    val sources: Set<String>,
    /** Whether the cast asks anything before its payment (modes, X, targets, another choice). */
    val asks: Boolean,
)

/**
 * The chips of a raised hand card (ZON-080, owner 2026-11-07): one per complete way ([CastChip]) when it has at most
 * [HandCasts.MAX_CHIPS]; with more, [chips] is empty and the hand shows a single "Cast…" chip with the [cheapest] cost
 * that opens the cast panel.
 */
data class HandCastChips(
    val cardId: String,
    /** Every complete way, in the ways' order, each way's extras off first; empty when there are more than [HandCasts.MAX_CHIPS]. */
    val chips: List<CastChip>,
    /** How many complete ways the card has. */
    val count: Int,
    /** The cheapest cost among the card's ways (the "Cast…" chip's): the cheapest one the player can pay now, else the
     * cheapest; null when it costs none. */
    val cheapest: String?,
    /** Whether any way can be paid now. */
    val affordable: Boolean,
    /** The sources Autotap would tap for the regular way (ZON-079, outlined while the card is raised). */
    val sources: Set<String>,
) {
    /** More complete ways than chips: a single "Cast…" chip. */
    val more: Boolean get() = count > HandCasts.MAX_CHIPS
    /** Whether the card has any choice: several complete ways, or one that asks something before its payment. A card
     * with none is cast on its raised card's tap. */
    val choice: Boolean get() = count > 1 || chips.singleOrNull()?.asks == true
}

/**
 * Works out a hand card's cost chips (I-D50) by trying each choice through the engine as far as its payment: the cast is
 * begun ([CastFlow]) with the extras answered as chosen, every other question before the payment (modes, X, targets) is
 * answered with the engine's first listed answer (X at its least), and the payment's suggestion says whether it is paid
 * and which sources Autotap would tap. Blocking (one replay per question); the app runs it on the Automatum worker.
 */
object HandCasts {
    /**
     * The chips of [cardId] for [viewer]'s choice [way] and [times], given its [offered] plays ([HandPlays.of]), of a cast
     * run on [source] ([CastFlow.Source.engine]); [definitions]: the game's card definitions by ATW definition digest (the
     * chips' names and texts). Null when the engine offers no way to cast or play it now (or [way] is out of range).
     */
    fun options(source: CastFlow.Source, definitions: Map<String, CardDefinition>, viewer: String,
        cardId: String, offered: List<Input>, way: Int = 0, times: List<Int> = emptyList(),
        seeds: CastFlow.Seeds = CastFlow.Seeds { error("No randomness before a cast") }): HandCastOptions? {
        fun start(input: Input, chosen: List<Int>): CastFlow = CastFlow.start(source, viewer, input, seeds, extras = chosen)
        val plays = ways(source, viewer, cardId, offered)
        if (plays.isEmpty() || way !in plays.indices) return null
        // A way the engine won't begin now (a cost it can't pay, not now) is still a chip, greyed (ZON-080)
        val first = try { start(plays[way], times) } catch (_: IllegalArgumentException) { null }
        val extras = first?.let(::extrasOf).orEmpty()
        val chosen = extras.indices.map { i -> times.getOrElse(i) { 0 }.coerceAtLeast(0).let { if (extras[i].second.repeatable) it else minOf(it, 1) } }
        val current = probe { if (chosen == times && first != null) first else start(plays[way], chosen) }
        val cardDefinition = definitionOf(source, definitions, cardId, plays)
        val printed = printedCosts(source, plays)
        val ways = plays.mapIndexed { i, input ->
            val probe = if (i == way) current else probe { start(input, emptyList()) }
            val plain = if (i == way && chosen.any { it > 0 }) probe { start(input, emptyList()) } else probe
            CastWayChip(input, wayName(cardDefinition, input), plain.cost ?: printed[i].takeIf { !plain.affordable }, wayText(cardDefinition, input),
                probe.affordable)
        }
        val extraChips = extras.mapIndexed { i, (instance, mechanic) ->
            val more = chosen.toMutableList().also { it[i] = if (mechanic.repeatable) it[i] + 1 else 1 }
            val affordable = if (more == chosen) current.affordable else probe { start(plays[way], more) }.affordable
            CastExtraChip(mechanic.keyword?.text ?: "Optional cost", manaOf(mechanic),
                CastFlow.abilityText(cardDefinition, instance.origin)?.takeIf { it.isNotBlank() }
                    ?: CastFlow.text(instance.ability), mechanic.repeatable, affordable)
        }
        return HandCastOptions(cardId, ways, way, extraChips, chosen, current.sources, current.affordable, current.cost)
    }

    /**
     * The chips of [cardId] (ZON-080, owner 2026-11-07): one per complete way, each way of the card ([options]) with each
     * combination of its optional extras on or off (a repeatable one paid at most once here; more is chosen in the cast
     * panel), in the ways' order, extras off first. With more than [MAX_CHIPS] only the ways' own costs are worked out (the
     * "Cast…" chip's [HandCastChips.cheapest]). Null when the engine offers no way to cast or play it now. Blocking (one
     * replay per question of each complete way); the app runs it on the Automatum worker.
     */
    fun chips(source: CastFlow.Source, definitions: Map<String, CardDefinition>, viewer: String, cardId: String,
        offered: List<Input>, seeds: CastFlow.Seeds = CastFlow.Seeds { error("No randomness before a cast") }): HandCastChips? {
        val plays = ways(source, viewer, cardId, offered)
        if (plays.isEmpty()) return null
        fun start(input: Input, chosen: List<Int>): CastFlow = CastFlow.start(source, viewer, input, seeds, extras = chosen)
        val cardDefinition = definitionOf(source, definitions, cardId, plays)
        val printed = printedCosts(source, plays)
        // Each way begun plainly: its own cost and payment, and the extras it has
        val begun = plays.map { input -> try { start(input, emptyList()) } catch (_: IllegalArgumentException) { null } }
        val plain = plays.indices.map { i -> probe { begun[i] ?: throw IllegalArgumentException("not now") } }
        val extras = begun.map { flow -> flow?.let(::extrasOf).orEmpty() }
        val combos = extras.map { list -> combinations(list.size) }
        val count = combos.sumOf { it.size }
        val costs = plays.indices.map { i -> plain[i].cost ?: printed[i] }
        if (count > MAX_CHIPS) {
            val payable = plays.indices.filter { plain[it].affordable }
            val cheapest = (payable.ifEmpty { plays.indices.toList() }).minByOrNull { manaValue(costs[it]) }?.let { costs[it] }
            return HandCastChips(cardId, emptyList(), count, cheapest, payable.isNotEmpty(), plain[0].sources)
        }
        val chips = plays.indices.flatMap { i ->
            combos[i].map { times ->
                val probe = if (times.none { it > 0 }) plain[i] else probe { start(plays[i], times) }
                val on = extras[i].filterIndexed { e, _ -> times[e] > 0 }
                val name = wayName(cardDefinition, plays[i]).let { way ->
                    if (on.isEmpty()) way else "$way with " + on.joinToString(" and ") { (_, m) -> (m.keyword?.text ?: "optional cost").lowercase() }
                }
                val texts = listOfNotNull(wayText(cardDefinition, plays[i])) + on.mapNotNull { (instance, _) ->
                    CastFlow.abilityText(cardDefinition, instance.origin)?.takeIf { it.isNotBlank() } ?: CastFlow.text(instance.ability)
                }
                CastChip(i, times, plays[i], name, probe.cost ?: printed[i].takeIf { times.none { it > 0 } }, texts.joinToString("\n").ifEmpty { null },
                    probe.affordable, probe.sources, probe.asks)
            }
        }
        val payable = chips.filter { it.affordable }
        val cheapest = payable.ifEmpty { chips }.minByOrNull { manaValue(it.cost) }?.cost
        return HandCastChips(cardId, chips, count, cheapest, payable.isNotEmpty(), chips.first().sources)
    }

    /** Every combination of [size] extras on (1) or off (0): none first, then the first alone, the second alone, both, … */
    private fun combinations(size: Int): List<List<Int>> =
        (0 until (1 shl minOf(size, 4))).map { bits -> List(size) { e -> if (e < 4 && bits and (1 shl e) != 0) 1 else 0 } }

    /** The mana value of printed mana [cost] (X as 0), to find the cheapest; 0 for none. */
    private fun manaValue(cost: String?): Int = cost?.let(arcanum.api.ManaCost::parse)?.symbols.orEmpty().sumOf { symbol ->
        when (symbol) {
            is arcanum.api.ManaSymbol.Generic -> symbol.amount
            is arcanum.api.ManaSymbol.GenericHybrid -> symbol.amount
            is arcanum.api.ManaSymbol.Variable -> 0
            else -> 1
        }
    }

    /**
     * The open cast [flow] taken on to its payment (STK-024, owner 2026-11-07: the cast panel shows and lets the player
     * choose what pays while earlier questions are open): every question before it answered as the chips are worked out
     * (the engine's first listed answer, X at its least, the first targets). [flow] itself when its payment is open; null
     * when it reaches no payment (a free spell, or a question without a listed answer). Blocking (one replay per question).
     */
    fun ahead(flow: CastFlow): CastFlow? {
        var at = flow
        repeat(MAX_QUESTIONS) {
            val open = at.pending ?: return null
            if (open is Decision.ManaPayment) return at
            val answer = defaultAnswer(at, open) ?: return null
            at = try { at.answer(answer) } catch (_: IllegalArgumentException) { return null }
        }
        return null
    }

    /** The card's definition: the game's card data, else the engine's own object (its definition is always there). */
    private fun definitionOf(source: CastFlow.Source, definitions: Map<String, CardDefinition>, cardId: String, plays: List<Input>): CardDefinition? =
        source.board.cards.singleOrNull { it.id == cardId }?.let { definitions[it.definition] }
            ?: plays.firstNotNullOfOrNull { input ->
                val obj = when (input) { is Input.Cast -> input.card; is Input.Activate -> input.source; is Input.PlayLand -> input.card; else -> null }
                obj?.let { source.run { error("No randomness while naming ways") }.working.obj(it)?.definition }
            }

    /**
     * Every way to cast [cardId] the card has (ZON-080, owner playtest 2026-11-05 evening: the list comes from the card, the
     * engine only greys a way it can't begin or pay now), worked out as the engine lists them (CR 601.3: the castable
     * faces, each plainly and with each of its alternative costs, then both halves fused): each face that isn't a land,
     * plainly, the front face with each of its alternative costs (bestow, evoke, dash, blitz, overload, disguise,
     * prototype, mutate, …), another face with each of its own, and a split card's halves fused, always. A land's play
     * ("Play", greyed while it can't be played now). Then the card's special actions from the hand (foretell, plot) and
     * its abilities that work in the hand (cycling and its kinds, channel, ninjutsu, …), each a way of its own. In the
     * engine's order, the plain cast of the front face (or a land's play) first.
     */
    private fun ways(source: CastFlow.Source, viewer: String, cardId: String, offered: List<Input>): List<Input> {
        val call = source.run { error("No randomness while listing ways") }
        val state = call.working
        val card = state.players.firstOrNull { it.id.value == viewer }?.hand.orEmpty()
            .firstOrNull { (it as? arcanum.engine.state.ObjectId.Card)?.let { id -> call.ids.atw(id.card) } == cardId } ?: return offered
        val definition = state.obj(card)?.definition ?: return offered
        val player = arcanum.engine.state.PlayerId(viewer)
        val abilities = Engine.view(state)[card]?.characteristics?.abilities.orEmpty()
        // The special actions the card offers from the hand: its own (plot) and foretell (a rule of the engine's for a
        // card with foretell, CR 702.143b); then its activated abilities that work in the hand (CR 113.6j: cycling, channel)
        val front = definition.faces.firstOrNull()
        val instantOrSorcery = front?.types.orEmpty().any { it == arcanum.api.CardType.Instant || it == arcanum.api.CardType.Sorcery }
        val actions = abilities.filter { (it.ability as? Ability.SpecialAction)?.functionsIn.orEmpty().contains(arcanum.api.Zone.Hand) }
            .map { it.origin } +
            listOfNotNull(FORETELL.takeIf { abilities.any { it.ability is arcanum.api.Keyword.Foretell } }) +
            abilities.filter { it.ability is Ability.Activated &&
                arcanum.api.Zone.Hand in arcanum.engine.view.Functioning.zones(it.ability, instantOrSorcery) }.map { it.origin }
        val activations = actions.distinct().map { Input.Activate(player, card, it) }
        // A land: its play (the engine's, or greyed when it can't be played now), then the rest
        if (offered.any { it is Input.PlayLand } || arcanum.api.CardType.Land in front?.types.orEmpty())
            return offered.ifEmpty { listOf(Input.PlayLand(player, card)) } + activations.filter { it !in offered }
        val faces = when (definition.layout) {
            arcanum.api.Layout.Split, arcanum.api.Layout.Adventure, arcanum.api.Layout.ModalDoubleFaced, arcanum.api.Layout.Omen ->
                definition.faces.indices.toList()
            else -> listOf(0)
        }.filter { face -> arcanum.api.CardType.Land !in definition.faces.getOrNull(face)?.types.orEmpty() }
        // The front face's alternative costs as the card in the hand has them (granted ones too); another face's, its own
        val alternatives = abilities
            .filter { instance -> (instance.ability as? Ability.Static)?.mechanics.orEmpty().any { it is Mechanic.AlternativeCost } }
            .map { it.origin }
        fun alternativesOf(face: Int): List<arcanum.engine.view.AbilityOrigin?> =
            listOf(null) + if (face == faces.firstOrNull()) alternatives else printedAlternatives(definition, face)
        // Fused only for a split card (no refused probe, "it has no fuse", for every other card's menu)
        val all = faces.flatMap { face -> alternativesOf(face).map { Input.Cast(player, card, arcanum.engine.input.CastWay(face, it)) } } +
            listOfNotNull(Input.Cast(player, card, arcanum.engine.input.CastWay(fused = true)).takeIf { definition.layout == arcanum.api.Layout.Split })
        return all + offered.filter { it !in all } + activations.filter { it !in offered }
    }

    /** The alternative costs printed on face [face] of [definition] (CR 118.9), as the engine names them: a static ability
     * by its place, a keyword's (evoke, dash, …) by its expansion's. */
    private fun printedAlternatives(definition: CardDefinition, face: Int): List<arcanum.engine.view.AbilityOrigin> =
        definition.faces.getOrNull(face)?.abilities.orEmpty().flatMapIndexed { i, ability ->
            val printed = arcanum.engine.view.AbilityOrigin.Printed(face, i)
            fun alternative(a: Ability?) = (a as? Ability.Static)?.mechanics.orEmpty().any { it is Mechanic.AlternativeCost }
            when {
                alternative(ability) -> listOf(printed)
                ability is arcanum.api.Keyword -> arcanum.api.KeywordExpansions.expand(ability).orEmpty()
                    .mapIndexedNotNull { j, a -> arcanum.engine.view.AbilityOrigin.Expansion(printed, j).takeIf { alternative(a) } }
                else -> emptyList()
            }
        }

    /** The engine's foretell special action (CR 702.143b), a rule rather than an ability of the card. */
    private val FORETELL = arcanum.engine.view.AbilityOrigin.Rule("foretell")

    /** The printed mana of each of [plays] (its alternative cost's, its face's, its special action's), shown on a way the
     * engine won't begin now (so no payment says it). */
    private fun printedCosts(source: CastFlow.Source, plays: List<Input>): List<String?> {
        val state = source.run { error("No randomness while listing ways") }.working
        return plays.map { input ->
            when (input) {
                is Input.Cast -> {
                    val o = state.obj(input.card)
                    val alternative = input.way.alternative
                    if (alternative != null) Engine.view(state)[input.card]?.characteristics?.abilities.orEmpty()
                        .firstOrNull { it.origin == alternative }?.let { (it.ability as? Ability.Static)?.mechanics.orEmpty()
                            .filterIsInstance<Mechanic.AlternativeCost>().firstOrNull()?.cost }?.let(::manaOf)
                    else o?.definition?.faces?.getOrNull(input.way.face)?.manaCost?.toString()?.ifEmpty { null }
                }
                is Input.Activate -> if (input.ability == FORETELL) "{2}" else Engine.view(state)[input.source]?.characteristics?.abilities.orEmpty()
                    .firstOrNull { it.origin == input.ability }?.let { (it.ability as? Ability.SpecialAction)?.cost ?: (it.ability as? Ability.Activated)?.cost }
                    ?.let(::manaOf)
                else -> null
            }
        }
    }

    private fun manaOf(cost: arcanum.api.Cost): String? = cost.parts.filterIsInstance<CostPart.Mana>().joinToString("") { it.cost.toString() }.ifEmpty { null }

    /** The printed text of a way, for its chip's details: its alternative cost's or special action's; null for a face. */
    private fun wayText(definition: CardDefinition?, input: Input): String? = when (input) {
        is Input.Cast -> input.way.alternative?.let { CastFlow.abilityText(definition, it) }
        is Input.Activate -> CastFlow.abilityText(definition, input.ability)
            ?: if (input.ability == FORETELL) "Foretell (During your turn, you may pay {2} and exile this card from your hand face down.)" else null
        else -> null
    }

    /**
     * STK-023 (owner playtest 2026-11-05 evening): the most the announcement open in [flow] ([Decision.Announce], an X)
     * may be so that the cast can still be paid with what the player has now, each value tried as far as the payment as
     * the chips are ([probe]: the later questions answered with the engine's first listed answers); one less than its
     * least when even that can't be paid; at most [cap] (and the decision's own most). Null when no announcement is open.
     * Blocking (a few replays per value tried); the app runs it on the Automatum worker.
     */
    fun mostAnnounced(flow: CastFlow, cap: Int = MAX_ANNOUNCED): Int? {
        val open = flow.pending as? Decision.Announce ?: return null
        val top = minOf(open.max ?: cap, cap).coerceAtLeast(open.min)
        fun pays(value: Int) = probe { flow.answer(Answer.Number(value)) }.affordable
        if (!pays(open.min)) return open.min - 1
        // Doubling steps up to the first value that can't be paid, then halving between it and the last that can
        var good = open.min
        var bad: Int? = null
        var step = 1
        while (good < top) {
            val next = minOf(good + step, top)
            if (pays(next)) { good = next; step *= 2 } else { bad = next; break }
        }
        var high = bad ?: return good
        while (high - good > 1) {
            val middle = (good + high) / 2
            if (pays(middle)) good = middle else high = middle
        }
        return good
    }

    /** The spell's optional additional costs as it is cast in [flow] (CR 601.2b), in the order the engine asks them. */
    internal fun extrasOf(flow: CastFlow): List<Pair<AbilityInstance, Mechanic.AdditionalCost>> {
        if (flow.begin !is Input.Cast) return emptyList()
        // The spell on the Stack (a new object, CR 400.7): the cast's own, or the top of the Stack once nothing is asked
        val spell = ((flow.call.pending?.continuation as? Continuation.Announcing)?.what as? Announced.Spell)?.card
            ?: flow.state.stack.lastOrNull() ?: return emptyList()
        return optionalCosts(Engine.view(flow.state)[spell]?.characteristics?.abilities.orEmpty())
    }

    private fun optionalCosts(abilities: List<AbilityInstance>): List<Pair<AbilityInstance, Mechanic.AdditionalCost>> =
        abilities.flatMap { instance ->
            (instance.ability as? Ability.Static)?.mechanics.orEmpty().filterIsInstance<Mechanic.AdditionalCost>()
                .filter { it.optional }.map { instance to it }
        }

    /**
     * Answers the optional additional costs [call]'s cast asks now with [extras] (times paid, by index; missing: not
     * paid), as the cost chips chose them (I-D50): while the open question is the spell's next optional cost (CR 601.2b:
     * the engine asks them in order, after its modes, before splicing). Anything else is left open.
     */
    internal fun answerExtras(call: EngineRun, extras: List<Int>) {
        repeat(MAX_EXTRAS) {
            val pending = call.pending ?: return
            val announcing = pending.continuation as? Continuation.Announcing ?: return
            val spell = announcing.what as? Announced.Spell ?: return
            if (announcing.stage != AnnounceStage.Modes) return
            val decision = pending.decision
            if (decision !is Decision.May && decision !is Decision.ChooseNumber) return
            val asked = announcing.choices.costs.values.keys.count { it.startsWith(OPTIONAL_KEY) }
            val optional = optionalCosts(Engine.view(call.working)[spell.card]?.characteristics?.abilities.orEmpty())
            // Every optional cost answered: a "splice onto it?" question follows (not an extra)
            if (asked >= optional.size) return
            val times = extras.getOrElse(asked) { 0 }.coerceAtLeast(0)
            call.perform(Input.Answer(decision.id, when (decision) {
                is Decision.ChooseNumber -> Answer.Number(times)
                else -> Answer.YesNo(times > 0)
            }))
        }
    }

    /** The name of [input] on its chip: "Play" (or "Play <face>") for a land, "Cast" for the plain cast, else the way
     * ([CastFlow.wayLabel]) with its mana symbols left to the chip's cost. */
    fun wayName(definition: CardDefinition?, input: Input): String = when (input) {
        is Input.PlayLand -> if (input.face == 0) "Play" else definition?.faces?.getOrNull(input.face)?.name?.let { "Play $it" } ?: "Play back face"
        is Input.Cast -> {
            val way = input.way
            val parts = mutableListOf<String>()
            if (way.fused) CastFlow.fusedName(definition)?.let { parts += it }
            else if (way.face != 0) definition?.faces?.getOrNull(way.face)?.name?.let { parts += it }
            way.alternative?.let { origin -> parts += CastFlow.abilityText(definition, origin)?.let(::withoutSymbols)?.ifBlank { null } ?: "Alternative cost" }
            if (way.permission != null) parts += "With permission"
            parts.joinToString(" · ").ifEmpty { "Cast" }
        }
        // A special action or an ability from the hand: its keyword ("Plot", "Foretell", "Cycling", "Swampcycling") or the
        // word before its dash ("Channel"); a plain "cost: effect" ability is "Activate"
        is Input.Activate -> if (input.ability == FORETELL) "Foretell"
            else CastFlow.abilityText(definition, input.ability)?.let { text ->
                if (':' in text.substringBefore('(') && '—' !in text.substringBefore(':')) "Activate" else withoutSymbols(text).ifBlank { null }
            } ?: "Activate"
        else -> "Cast"
    }

    /** [text] without its mana symbols and the dash or reminder that follows a keyword ("Evoke {R/G}{R/G}" → "Evoke",
     * "Flashback—Sacrifice a Mountain." → "Flashback"). */
    internal fun withoutSymbols(text: String): String =
        text.substringBefore('(').substringBefore('—').replace(SYMBOL, "").trim().trimEnd('.', ',', ' ', '-')

    private fun manaOf(mechanic: Mechanic.AdditionalCost): String? = manaOf(mechanic.cost)

    /** What trying a choice as far as its payment found: its [cost], the [sources] Autotap would tap when it is paid,
     * and whether it [asks] anything before the payment. */
    private class Probe(val cost: String?, val sources: Set<String>, val affordable: Boolean, val asks: Boolean = false)

    /** Tries the choice [start] begins as far as its payment (see [HandCasts]). A choice the engine refuses to begin is
     * unaffordable; one whose questions can't be answered with a listed answer counts as payable (the cast says). */
    private fun probe(start: () -> CastFlow): Probe {
        var flow = try { start() } catch (_: IllegalArgumentException) { return Probe(null, emptySet(), false) }
        var asks = false
        repeat(MAX_QUESTIONS) {
            val open = flow.pending ?: return Probe(null, emptySet(), true, asks)
            if (open is Decision.ManaPayment) {
                val pays = flow.pays(open.suggestion)
                val cost = open.cost.toString().ifEmpty { null }
                return Probe(cost, if (pays) sources(flow, open.suggestion) else emptySet(), pays, asks)
            }
            asks = true
            val answer = defaultAnswer(flow, open) ?: return Probe(null, emptySet(), true, asks)
            flow = try { flow.answer(answer) } catch (_: IllegalArgumentException) { return Probe(null, emptySet(), true, asks) }
        }
        return Probe(null, emptySet(), true, asks)
    }

    /** The answer a question before the payment is given while trying a choice: X at its least, the first targets and
     * modes, else the engine's first listed answer; null when there is none. */
    private fun defaultAnswer(flow: CastFlow, open: Decision): Answer? = when (open) {
        is Decision.Announce -> Answer.Number(open.min)
        is Decision.Targets -> Answer.Targets(open.options.take(open.min)).takeIf { open.options.size >= open.min }
        is Decision.Modes -> open.options.filter { it.available }.take(maxOf(open.min, 1)).map { it.index }
            .takeIf { it.size >= open.min }?.let(Answer::Modes)
        else -> flow.listedAnswers().firstOrNull()
    }

    private fun sources(flow: CastFlow, uses: List<ManaUse>): Set<String> = uses.flatMap { use ->
        when (use) {
            is ManaUse.Ability -> listOfNotNull(flow.atwCard(use.source))
            is ManaUse.Special -> use.objects.mapNotNull(flow::atwCard)
            else -> emptyList()
        }
    }.toSet()

    private const val OPTIONAL_KEY = "opt:"
    private const val MAX_EXTRAS = 16
    private const val MAX_QUESTIONS = 12
    /** The most chips above a raised hand card; a card with more complete ways shows one "Cast…" chip ([HandCastChips.more]). */
    const val MAX_CHIPS = 4
    /** The most an X is ever offered ([mostAnnounced]). */
    const val MAX_ANNOUNCED = 99
    private val SYMBOL = Regex("""\{[^{}]+\}""")
}
