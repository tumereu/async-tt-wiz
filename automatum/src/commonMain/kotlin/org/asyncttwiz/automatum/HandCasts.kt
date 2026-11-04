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
import arcanum.engine.state.LoggedChange
import arcanum.engine.view.AbilityInstance
import org.asyncttwiz.protocol.PlayState

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
 * Works out a hand card's cost chips (I-D50) by trying each choice through the engine as far as its payment: the cast is
 * begun ([CastFlow]) with the extras answered as chosen, every other question before the payment (modes, X, targets) is
 * answered with the engine's first listed answer (X at its least), and the payment's suggestion says whether it is paid
 * and which sources Autotap would tap. Blocking (one replay per question); the app runs it on the Automatum worker.
 */
object HandCasts {
    /**
     * The chips of [cardId] for [viewer]'s choice [way] and [times], given its [plays] ([HandPlays.of]); null when the
     * engine offers no way to cast or play it now (or [way] is out of range).
     */
    fun options(play: PlayState, definitions: Map<String, CardDefinition>, viewer: String, log: List<LoggedChange>,
        cardId: String, offered: List<Input>, way: Int = 0, times: List<Int> = emptyList(),
        seeds: CastFlow.Seeds = CastFlow.Seeds { error("No randomness before a cast") }): HandCastOptions? {
        fun start(input: Input, chosen: List<Int>): CastFlow = CastFlow.start(play, definitions, viewer, log, input, seeds, extras = chosen)
        val plays = ways(play, definitions, viewer, log, cardId, offered) { input ->
            try { start(input, emptyList()); true } catch (_: IllegalArgumentException) { false } catch (_: Unsupported) { false }
        }
        if (plays.isEmpty() || way !in plays.indices) return null
        val first = try { start(plays[way], times) } catch (_: IllegalArgumentException) { return null } catch (_: Unsupported) { return null }
        val extras = extrasOf(first)
        val chosen = extras.indices.map { i -> times.getOrElse(i) { 0 }.coerceAtLeast(0).let { if (extras[i].second.repeatable) it else minOf(it, 1) } }
        val current = probe { if (chosen == times) first else start(plays[way], chosen) }
        val cardDefinition = play.board.cards.singleOrNull { it.id == cardId }?.let { definitions[it.definition] }
        val ways = plays.mapIndexed { i, input ->
            val probe = if (i == way) current else probe { start(input, emptyList()) }
            val plain = if (i == way && chosen.any { it > 0 }) probe { start(input, emptyList()) } else probe
            CastWayChip(input, wayName(cardDefinition, input), plain.cost, (input as? Input.Cast)?.way?.alternative
                ?.let { CastFlow.abilityText(cardDefinition, it) }, probe.affordable)
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
     * Every way to cast [cardId] the engine would begin now ([begins]): the ways it [offered] (those it can pay), and the
     * ways it can't pay now, worked out as the engine lists them (CR 601.3: the castable faces, each plainly and with each
     * of its alternative costs, then both halves fused), so a way the player can't afford still shows, greyed. In the
     * engine's order, the plain cast of the front face first; a land's ways are only those offered.
     */
    private fun ways(play: PlayState, definitions: Map<String, CardDefinition>, viewer: String, log: List<LoggedChange>,
        cardId: String, offered: List<Input>, begins: (Input) -> Boolean): List<Input> {
        if (offered.any { it is Input.PlayLand }) return offered
        val call = try { EngineCall(play, definitions, null, log) { error("No randomness while listing ways") } }
            catch (_: Unsupported) { return offered }
        val state = call.working
        val card = state.players.firstOrNull { it.id.value == viewer }?.hand.orEmpty()
            .firstOrNull { (it as? arcanum.engine.state.ObjectId.Card)?.let { id -> call.ids.atw(id.card) } == cardId } ?: return offered
        val definition = state.obj(card)?.definition ?: return offered
        val faces = when (definition.layout) {
            arcanum.api.Layout.Split, arcanum.api.Layout.Adventure, arcanum.api.Layout.ModalDoubleFaced, arcanum.api.Layout.Omen ->
                definition.faces.indices.toList()
            else -> listOf(0)
        }
        val alternatives = listOf(null) + Engine.view(state)[card]?.characteristics?.abilities.orEmpty()
            .filter { instance -> (instance.ability as? Ability.Static)?.mechanics.orEmpty().any { it is Mechanic.AlternativeCost } }
            .map { it.origin }
        val player = arcanum.engine.state.PlayerId(viewer)
        // Fused only for a split card (no refused probe, "it has no fuse", for every other card's menu)
        val listed = faces.flatMap { face -> alternatives.map { Input.Cast(player, card, arcanum.engine.input.CastWay(face, it)) } } +
            listOfNotNull(Input.Cast(player, card, arcanum.engine.input.CastWay(fused = true)).takeIf { definition.layout == arcanum.api.Layout.Split })
        val all = listed.filter { it in offered || begins(it) }
        return all + offered.filter { it !in all }
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
    internal fun answerExtras(call: EngineCall, extras: List<Int>) {
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
        else -> "Cast"
    }

    /** [text] without its mana symbols and the dash or reminder that follows a keyword ("Evoke {R/G}{R/G}" → "Evoke",
     * "Flashback—Sacrifice a Mountain." → "Flashback"). */
    internal fun withoutSymbols(text: String): String =
        text.substringBefore('(').substringBefore('—').replace(SYMBOL, "").trim().trimEnd('.', ',', ' ', '-')

    private fun manaOf(mechanic: Mechanic.AdditionalCost): String? =
        mechanic.cost.parts.filterIsInstance<CostPart.Mana>().joinToString("") { it.cost.toString() }.ifEmpty { null }

    /** What trying a choice as far as its payment found. */
    private class Probe(val cost: String?, val sources: Set<String>, val affordable: Boolean)

    /** Tries the choice [start] begins as far as its payment (see [HandCasts]). A choice the engine refuses to begin is
     * unaffordable; one whose questions can't be answered with a listed answer counts as payable (the cast says). */
    private fun probe(start: () -> CastFlow): Probe {
        var flow = try { start() } catch (_: IllegalArgumentException) { return Probe(null, emptySet(), false) }
            catch (_: Unsupported) { return Probe(null, emptySet(), false) }
        repeat(MAX_QUESTIONS) {
            val open = flow.pending ?: return Probe(null, emptySet(), true)
            if (open is Decision.ManaPayment) {
                val pays = flow.pays(open.suggestion)
                val cost = open.cost.toString().ifEmpty { null }
                return Probe(cost, if (pays) sources(flow, open.suggestion) else emptySet(), pays)
            }
            val answer = when (open) {
                is Decision.Announce -> Answer.Number(open.min)
                is Decision.Targets -> Answer.Targets(open.options.take(open.min)).takeIf { open.options.size >= open.min }
                is Decision.Modes -> open.options.filter { it.available }.take(maxOf(open.min, 1)).map { it.index }
                    .takeIf { it.size >= open.min }?.let(Answer::Modes)
                else -> flow.listedAnswers().firstOrNull()
            } ?: return Probe(null, emptySet(), true)
            flow = try { flow.answer(answer) } catch (_: IllegalArgumentException) { return Probe(null, emptySet(), true) }
        }
        return Probe(null, emptySet(), true)
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
    private val SYMBOL = Regex("""\{[^{}]+\}""")
}
