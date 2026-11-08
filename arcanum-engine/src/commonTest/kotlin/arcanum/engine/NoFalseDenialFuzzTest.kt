package arcanum.engine

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.TargetRef
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Deny only with proof (owner, castability §1.1, report KX8GYS): on generated boards of every mana kind, costs and reductions, each
 * card [Engine.options] leaves out is tried without bounds through the engine itself (every target and object set, every mode, X
 * up to a small number, every set of mana sources by hand): no way may exist. The other direction, that a listed cast is accepted,
 * is [OptionsFuzzTest]'s, which accepts "seems castable" (a payment only a player's own picks may find).
 */
class NoFalseDenialFuzzTest {
    private val sources = listOf(
        Cards.invent("Deny Pain Land", null, "Land", "{T}: Add {C}.\n{T}: Add {R}. This land deals 1 damage to you."),
        Cards.invent("Deny Tomb", null, "Land", "{T}: Add {C}{C}. This land deals 2 damage to you."),
        Cards.invent("Deny Altar", "{2}", "Artifact", "Sacrifice a creature: Add {R}."),
        Cards.invent("Deny Signet", "{2}", "Artifact", "{1}, {T}: Add {W}{U}."),
        Cards.invent("Deny Prism", "{3}", "Artifact", "{T}: Add one mana of any color."),
        Cards.invent("Deny Swamp Boost", "{2}", "Enchantment", "Whenever you tap a Swamp for mana, add an additional {B}."),
        Cards.invent("Deny Elf", "{G}", "Creature — Elf", "{T}: Add {G}.", "1/1"),
        Cards.invent("Deny Life Stone", "{2}", "Artifact", "Pay 1 life: Add {C}."),
        filterLand(),
    )
    private val creatures = listOf(
        Cards.invent("Deny Bear", "{1}{G}", "Creature — Bear", "", "2/2"),
        Cards.invent("Deny Ox", "{2}{G}", "Creature — Ox Beast", "", "3/3"),
        Cards.invent("Deny Bird", "{1}{W}", "Creature — Bird", "Flying", "1/1"),
    )
    private val spells = listOf(
        Cards.invent("Deny Banishing", "{4}{W}", "Sorcery", "This spell costs {2} less to cast if it targets a tapped creature.\nExile target creature."),
        Cards.invent("Deny Wing Clip", "{3}{W}", "Enchantment — Aura", "Enchant creature\nThis spell costs {2} less to cast if it targets a creature with flying.\nEnchanted creature gets -1/-0."),
        Cards.invent("Deny Burst", "{X}{1}{R}", "Sorcery", "This spell costs {1} less to cast if it targets a tapped creature.\nThis spell deals X damage to target creature."),
        Cards.invent("Deny Land Eater", "{G}", "Sorcery", "As an additional cost to cast this spell, sacrifice a land.\nDraw a card."),
        Cards.invent("Deny Tap Pact", "{G}", "Sorcery", "As an additional cost to cast this spell, tap an untapped creature you control.\nDraw a card."),
        Cards.invent("Deny Blood Pact", "{R}", "Sorcery", "As an additional cost to cast this spell, pay 2 life.\nDraw a card."),
        Cards.invent("Deny Offering", "{R}", "Sorcery", "As an additional cost to cast this spell, sacrifice a creature.\nDraw a card."),
        Cards.invent("Deny Phyrexian", "{1}{R/P}", "Sorcery", "Draw a card."),
        Cards.invent("Deny Double Phyrexian", "{W/P}{B/P}", "Sorcery", "Draw a card."),
        Cards.invent("Deny Twobrid", "{2/W}{2/W}", "Sorcery", "Draw a card."),
        Cards.invent("Deny Black Four", "{B}{B}{B}{B}", "Sorcery", "Draw a card."),
        Cards.invent("Deny Dual", "{W}{U}", "Sorcery", "Draw a card."),
        Cards.invent("Deny Two White", "{W}{W}", "Sorcery", "Draw a card."),
        falling("Deny Many Targets", CostChange.Less(ManaCost.parse("{1}")!!, Value.Count(Objects.Target("creatures")))),
    )

    private fun filterLand(): String {
        val base = Cards.definition(Cards.invent("Deny Filter Land Base", null, "Land", "{T}: Add {C}.")).faces.single()
        val own = base.abilities.single() as Ability.Activated
        val ability = own.copy(
            cost = own.cost.copy(parts = listOf(CostPart.Mana(ManaCost.parse("{W/U}")!!)) + own.cost.parts),
            instructions = Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(2),
                listOf(ColorRef.Literal(Color.White), ColorRef.Literal(Color.Blue)), combination = true)), Players.You)))),
        )
        return Cards.define("Deny Filter Land", CardDefinition(arcanum.api.Layout.Normal, listOf(base.copy(name = "Deny Filter Land", abilities = listOf(ability)))))
    }

    /** "{5}, destroy any number of target creatures", with [change]. */
    private fun falling(name: String, change: CostChange): String {
        val base = Cards.definition(Cards.invent("$name Base", "{4}", "Sorcery", "Draw a card.")).faces.single()
        val slot = arcanum.api.TargetSlot("creatures", arcanum.api.TargetKind.Objects(arcanum.api.ObjectFilter.Type(arcanum.api.CardType.Creature)), Value.Fixed(0), null)
        val spell = Ability.Spell(Instructions(targets = listOf(slot), steps = listOf(Step.Do(Effect.Destroy(Objects.Target("creatures"))))))
        val static = Ability.Static(listOf(Mechanic.CostChange(arcanum.api.ObjectFilter.Is(Objects.Self), change)), arcanum.api.Affected.Objects(Objects.Self))
        return Cards.define(name, CardDefinition(arcanum.api.Layout.Normal, listOf(base.copy(name = name, abilities = listOf(static, spell)))))
    }

    /** How many answers a card's search without bounds may try on one board. Boards are small enough not to reach it; one that does is counted. */
    private class Tries(var left: Int = 20_000)

    @Test
    fun aCardLeftOutHasNoWayToBeCast() {
        var denied = 0
        var capped = 0
        repeat(BOARDS) { i ->
            val seed = SEED + i
            scenario(seed = seed) {
                val rnd = Random(seed)
                alice.life = listOf(1, 2, 3, 20).random(rnd)
                val basics = listOf("Plains", "Island", "Swamp", "Mountain", "Forest").shuffled(rnd).take(rnd.nextInt(4))
                if (basics.isNotEmpty()) alice.battlefield(*basics.toTypedArray())
                sources.shuffled(rnd).take(rnd.nextInt(5)).forEach { alice.battlefield(it) }
                creatures.shuffled(rnd).take(rnd.nextInt(3)).forEach { alice.battlefield(it) }
                bob.battlefield(creatures.random(rnd), tapped = rnd.nextBoolean())
                bob.battlefield(creatures.random(rnd), tapped = rnd.nextBoolean())
                val hand = spells.shuffled(rnd).take(4)
                alice.hand(*hand.toTypedArray())
                val listed = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().map { it.card }
                for (card in state.player(alice.id).hand) {
                    if (card in listed) continue
                    val input = Input.Cast(alice.id, card)
                    if (Engine.refusal(state, input) != null) continue
                    denied++
                    val tries = Tries()
                    val way = wayFrom(state, input, tries)
                    if (way != null) fail("Board $seed: ${nameOf(state.objOrThrow(card))} was left out, but it can be cast: $way\nbattlefield ${state.battlefield.map { nameOf(state.objOrThrow(it)) }}, life ${alice.life}")
                    if (tries.left <= 0) capped++
                }
            }
        }
        assertTrue(denied > BOARDS / 2, "the boards deny enough to test: $denied")
        assertTrue(capped < denied / 4, "the search without bounds finished on most boards: $capped of $denied")
    }

    /** The answers that finish [input] from [state], or null when there are none. */
    private fun wayFrom(state: GameState, input: Input, tries: Tries): List<Answer>? {
        if (tries.left-- <= 0) return null
        val outcome = try { Engine.perform(state, input) } catch (_: IllegalArgumentException) { return null } catch (_: IllegalStateException) { return null }
        val next = state.deepCopy()
        outcome.changes.forEach { next.apply(it) }
        val pending = outcome.pending ?: return emptyList()
        if (pending.continuation !is Continuation.Announcing) return emptyList()
        val d = pending.decision
        for (answer in everyAnswer(next, d)) {
            val rest = wayFrom(next, Input.Answer(d.id, answer), tries)
            if (rest != null) return listOf(answer) + rest
            if (tries.left <= 0) return null
        }
        return null
    }

    private fun everyAnswer(state: GameState, d: Decision): Sequence<Answer> = when (d) {
        is Decision.Targets -> subsets(d.options, d.min, d.max).map { Answer.Targets(it) }
        is Decision.ChooseObjects -> subsets(d.options, d.min, d.max).map { Answer.Objects(it) }
        is Decision.Modes -> subsets(d.options.filter { it.available }.map { it.index }, d.min, d.max).map { Answer.Modes(it) }
        is Decision.May -> sequenceOf(Answer.YesNo(true), Answer.YesNo(false))
        is Decision.Announce -> (d.min..d.min + 6).asSequence().map { Answer.Number(it) }
        is Decision.ChooseOption -> d.options.asSequence().map { Answer.Option(it) }
        is Decision.ChooseColor -> d.among.asSequence().map { Answer.ColorChoice(it) }
        is Decision.ManaPayment -> everyPayment(d).map { Answer.ManaPayment(it) }
        else -> emptySequence()
    }

    /** Every set of the payment's options: from each source at most one way, the pool's mana and life on their own. */
    private fun everyPayment(d: Decision.ManaPayment): Sequence<List<ManaUse>> = sequence {
        val groups = d.options.groupBy { use -> if (use is ManaUse.Ability) use.source.toString() + use.answers.filterIsInstance<Answer.Objects>() else use.toString() }.values.toList()
        suspend fun SequenceScope<List<ManaUse>>.go(i: Int, chosen: List<ManaUse>) {
            if (i == groups.size) { yield(chosen); return }
            go(i + 1, chosen)
            groups[i].forEach { go(i + 1, chosen + it) }
        }
        go(0, emptyList())
    }

    private fun <T> subsets(options: List<T>, min: Int, max: Int?): Sequence<List<T>> = sequence {
        val top = minOf(max ?: options.size, options.size)
        for (mask in 0 until (1 shl options.size)) {
            val size = mask.countOneBits()
            if (size in min..top) yield(options.filterIndexed { i, _ -> mask and (1 shl i) != 0 })
        }
    }

    private companion object {
        const val BOARDS = 200
        const val SEED = 91_000L
    }
}
