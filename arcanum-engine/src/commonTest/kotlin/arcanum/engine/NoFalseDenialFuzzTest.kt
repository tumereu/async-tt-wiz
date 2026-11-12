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
import arcanum.api.isManaAbility
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
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
        Cards.invent("Deny Shield", "{0}", "Enchantment", "Prevent all damage that would be dealt to you."),
    )
    private val xGiant = Cards.invent("Deny X Giant", "{X}{4}", "Creature — Giant", "", "4/4")
    private val sizeReducer: String = run {
        val face = Cards.definition(Cards.invent("Deny Drover Base", "{0}", "Enchantment", "Creature spells you cast cost {2} less to cast.")).faces.single()
        val static = face.abilities.single() as Ability.Static
        val m = static.mechanics.single() as Mechanic.CostChange
        val big = arcanum.api.ObjectFilter.ManaValue(arcanum.api.Comparison.AtLeast, Value.Fixed(6))
        Cards.define("Deny Drover", CardDefinition(arcanum.api.Layout.Normal, listOf(face.copy(name = "Deny Drover",
            abilities = listOf(static.copy(mechanics = listOf(m.copy(what = arcanum.api.ObjectFilter.And(listOf(m.what, big))))))))))
    }
    private val fertile = Cards.invent("Deny Fertile", "{0}", "Enchantment — Aura", "Enchant land\nWhenever enchanted land is tapped for mana, its controller adds an additional one mana of any color.")
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
        Cards.invent("Deny Blue Green", "{U}{G}", "Sorcery", "Draw a card."),
        Cards.invent("Deny Red One Tap", "{1}", "Sorcery", "As an additional cost to cast this spell, tap an untapped creature you control.\nDraw a card."),
        Cards.invent("Deny Green Tap", "{G}", "Sorcery", "As an additional cost to cast this spell, tap an untapped creature you control.\nDraw a card."),
    )
    /** These read a creature's being tapped: floating first (tapping it) may make them castable where they weren't, which isn't a false denial
     * (castability §1.2). */
    private val readTapped = setOf("Deny Banishing", "Deny Burst")

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

    private fun Scenario.board(seed: Long) {
        val rnd = Random(seed)
        alice.life = listOf(1, 2, 3, 20).random(rnd)
        // Every seventh board: reducers that start to apply as X grows (CR 202.3e, 601.2f)
        if (seed % 7 == 0L) {
            repeat(1 + rnd.nextInt(2)) { alice.battlefield(sizeReducer) }
            alice.battlefield(*List(1 + rnd.nextInt(4)) { "Forest" }.toTypedArray())
            alice.hand(xGiant)
            return
        }
        // Every third board: the cast's other costs against the mana (CR 601.2g before 601.2h): an altar or a mana creature, the creatures a cost may tap
        if (seed % 3 == 0L) {
            alice.battlefield(sources.first { "Altar" in it })
            if (rnd.nextBoolean()) alice.battlefield(sources.first { "Elf" in it })
            creatures.shuffled(rnd).take(1 + rnd.nextInt(2)).forEach { alice.battlefield(it) }
            bob.battlefield(creatures.random(rnd), tapped = rnd.nextBoolean())
            alice.hand("Deny Red One Tap", "Deny Green Tap", "Deny Offering", "Deny Blood Pact")
            return
        }
        val basics = listOf("Plains", "Island", "Swamp", "Mountain", "Forest").shuffled(rnd).take(rnd.nextInt(4))
        if (basics.isNotEmpty()) alice.battlefield(*basics.toTypedArray())
        if (basics.isNotEmpty() && rnd.nextInt(4) == 0) { alice.battlefield(fertile); attach(fertile, basics.first()) }
        sources.shuffled(rnd).take(rnd.nextInt(5)).forEach { alice.battlefield(it) }
        creatures.shuffled(rnd).take(rnd.nextInt(3)).forEach { alice.battlefield(it) }
        bob.battlefield(creatures.random(rnd), tapped = rnd.nextBoolean())
        bob.battlefield(creatures.random(rnd), tapped = rnd.nextBoolean())
        alice.hand(*spells.shuffled(rnd).take(4).toTypedArray())
    }

    @Test
    fun aCardLeftOutHasNoWayToBeCast() {
        var denied = 0
        var capped = 0
        repeat(BOARDS) { i ->
            val seed = SEED + i
            scenario(seed = seed) {
                board(seed)
                val listed = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().map { it.card }
                for (card in state.player(alice.id).hand) {
                    if (card in listed) continue
                    val input = Input.Cast(alice.id, card)
                    if (CastScreen.refusal(state, input) != null) continue
                    denied++
                    val tries = Tries()
                    val way = wayFrom(state, input, tries)
                    if (way != null) fail("Board $seed: ${nameOf(state.objOrThrow(card))} was left out, but it can be cast: $way\nbattlefield ${state.battlefield.map { nameOf(state.objOrThrow(it)) }}, life ${alice.life}")
                    // A second way, not through the payment's own options: floating the mana first with the abilities on their own (CR 605.3a)
                    if (i % FLOAT_EVERY == 0 && nameOf(state.objOrThrow(card)).toString() !in readTapped) floated(state, Tries(FLOATS)).firstOrNull { wayFrom(it, input, tries) != null }?.let {
                        fail("Board $seed: ${nameOf(state.objOrThrow(card))} was left out, but floating first ${it.player(alice.id).manaPool.map { u -> u.color }} casts it\nbattlefield ${state.battlefield.map { b -> nameOf(state.objOrThrow(b)) }}, life ${alice.life}")
                    }
                    if (tries.left <= 0) capped++
                }
            }
        }
        assertTrue(denied > BOARDS / 2, "the boards deny enough to test: $denied")
        assertTrue(capped < denied / 4, "the search without bounds finished on most boards: $capped of $denied")
    }

    // Castability §3: on every board and every first choice of targets or objects in a cast: a selection that is done can be finished, and a
    // candidate left out has no finishing selection
    @Test
    fun aSelectionsDoneAndItsLeftOutCandidatesHoldOnEveryBoard() {
        var looked = 0
        repeat(BOARDS) { i ->
            val seed = SEED + i
            scenario(seed = seed) {
                board(seed)
                for (input in Engine.options(state, alice.id).filterIsInstance<Input.Cast>()) {
                    val open = try { Engine.perform(state, input) } catch (_: IllegalArgumentException) { continue }
                    val at = state.deepCopy().also { s -> open.changes.forEach { s.apply(it) } }
                    val d = open.pending?.decision ?: continue
                    val options = when (d) {
                        is Decision.Targets -> d.options
                        is Decision.ChooseObjects -> d.options.map { TargetRef.Object(it) }
                        else -> continue
                    }
                    val min = if (d is Decision.Targets) d.min else (d as Decision.ChooseObjects).min
                    val max = if (d is Decision.Targets) d.max else (d as Decision.ChooseObjects).max
                    fun answer(picked: List<TargetRef>): Answer = if (d is Decision.Targets) Answer.Targets(picked) else Answer.Objects(picked.map { (it as TargetRef.Object).id })
                    looked++
                    val all = subsets(options, min, max).toList()
                    for (picked in all) {
                        val done = CastScreen.selection(at, picked).done
                        if (done) assertTrue(CastScreen.finishableWith(at, answer(picked)), "Board $seed: $d: done with $picked, but it can't be finished")
                        if (done) {
                            // Autotap's suggestion at that payment, when it has one, is accepted: it doesn't use what the rest of the cost uses (CR 601.2g before 601.2h)
                            val next = try { Engine.perform(at, Input.Answer(d.id, answer(picked))) } catch (_: IllegalArgumentException) { continue }
                            val payment = next.pending?.decision as? Decision.ManaPayment ?: continue
                            if (payment.suggestion.isEmpty()) continue
                            val after = at.deepCopy().also { c -> next.changes.forEach { c.apply(it) } }
                            try { Engine.perform(after, Input.Answer(payment.id, Answer.ManaPayment(payment.suggestion))) }
                            catch (e: IllegalArgumentException) { fail("Board $seed: Autotap's ${payment.suggestion} is refused after $picked: ${e.message}") }
                        }
                    }
                    val left = options - CastScreen.selection(at, emptyList()).candidates.toSet()
                    for (out in left) for (picked in all.filter { out in it }) {
                        val way = wayFrom(at, Input.Answer(d.id, answer(picked)), Tries())
                        if (way != null) fail("Board $seed: $d: $out was left out, but $picked finishes it: $way")
                    }
                }
            }
        }
        assertTrue(looked > 20, "enough choices to test: $looked")
    }

    /** The states reached by activating mana abilities on their own from [state], [state] itself first, with each colour they ask. */
    private fun floated(state: GameState, tries: Tries): Sequence<GameState> = sequence {
        yield(state)
        if (tries.left <= 0) return@sequence
        val view = Engine.view(state)
        val abilities = Engine.options(state, PlayerId("alice")).filterIsInstance<Input.Activate>().filter { a ->
            view[a.source]?.characteristics?.abilities?.firstOrNull { it.origin == a.ability }?.ability?.isManaAbility() == true
        }
        for (a in abilities) for (after in finished(state, a, tries)) yieldAll(floated(after, tries))
    }

    /** The states after [input] with every way to answer what it asks. */
    private fun finished(state: GameState, input: Input, tries: Tries): Sequence<GameState> = sequence {
        if (tries.left-- <= 0) return@sequence
        val outcome = try { Engine.perform(state, input) } catch (_: IllegalArgumentException) { return@sequence } catch (_: IllegalStateException) { return@sequence }
        val next = state.deepCopy()
        outcome.changes.forEach { next.apply(it) }
        val pending = outcome.pending
        if (pending == null) { yield(next); return@sequence }
        for (answer in everyAnswer(pending.decision, tries)) yieldAll(finished(next, Input.Answer(pending.decision.id, answer), tries))
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
        for (answer in everyAnswer(d, tries)) {
            val rest = wayFrom(next, Input.Answer(d.id, answer), tries)
            if (rest != null) return listOf(answer) + rest
            if (tries.left <= 0) return null
        }
        return null
    }

    private fun everyAnswer(d: Decision, tries: Tries): Sequence<Answer> = when (d) {
        is Decision.Targets -> subsets(d.options, d.min, d.max).map { Answer.Targets(it) }
        is Decision.ChooseObjects -> subsets(d.options, d.min, d.max).map { Answer.Objects(it) }
        is Decision.Modes -> subsets(d.options.filter { it.available }.map { it.index }, d.min, d.max).map { Answer.Modes(it) }
        is Decision.May -> sequenceOf(Answer.YesNo(true), Answer.YesNo(false))
        is Decision.Announce -> (d.min..d.min + 6).asSequence().map { Answer.Number(it) }
        is Decision.ChooseOption -> d.options.asSequence().map { Answer.Option(it) }
        is Decision.ChooseColor -> d.among.asSequence().map { Answer.ColorChoice(it) }
        is Decision.ChooseNumber -> (d.min..(d.max ?: (d.min + 6))).asSequence().map { Answer.Number(it) }
        is Decision.ChoosePlayer -> d.options.asSequence().map { Answer.Player(it) }
        is Decision.ManaPayment -> everyPayment(d).map { Answer.ManaPayment(it) }
        // Not explored: this board can't tell, and it is counted as cut short
        else -> { tries.left = 0; emptySequence() }
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
        const val FLOATS = 150
        /** Floating first is the slow part: every so many boards. */
        const val FLOAT_EVERY = 5
        const val SEED = 91_000L
    }
}
