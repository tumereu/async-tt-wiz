package arcanum.engine.casting

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.CostChange
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Value
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Castability (owner, report KX8GYS, castability spec): a card is left out only with proof; every mana ability counts and pays
 * (CR 605.3a), by Autotap and by hand, in a cast and during resolution; mana choices interact with the cast's other costs (CR
 * 601.2g before 601.2h); the current state as it stands (no looking for "tap this first").
 */
class CastabilityTest {
    private val spark = Cards.invent("Cast Spark", "{R}", "Sorcery", "Draw a card.")
    private val colorless = Cards.invent("Cast Two Pence", "{2}", "Sorcery", "Draw a card.")
    private val blueOne = Cards.invent("Cast Blue Note", "{U}", "Sorcery", "Draw a card.")
    private val whiteBlue = Cards.invent("Cast Dual Note", "{W}{U}", "Sorcery", "Draw a card.")
    private val blackFour = Cards.invent("Cast Four Black", "{B}{B}{B}{B}", "Sorcery", "Draw a card.")
    private val five = Cards.invent("Cast Five Pence", "{5}", "Sorcery", "Draw a card.")
    private val bear = Cards.invent("Cast Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Cast Ox", "{2}{G}", "Creature — Ox", "", "3/3")

    private val painLand = Cards.invent("Cast Pain Land", null, "Land", "{T}: Add {C}.\n{T}: Add {R}. This land deals 1 damage to you.")
    private val tomb = Cards.invent("Cast Tomb", null, "Land", "{T}: Add {C}{C}. This land deals 2 damage to you.")
    private val counterLand = Cards.invent("Cast Charge Land", null, "Land", "{T}, Remove a charge counter from this land: Add one mana of any color.")
    private val altar = Cards.invent("Cast Altar", "{2}", "Artifact", "Sacrifice a creature: Add {R}.")
    private val signet = Cards.invent("Cast Signet", "{2}", "Artifact", "{1}, {T}: Add {W}{U}.")
    private val prism = Cards.invent("Cast Prism", "{3}", "Artifact", "{T}: Add one mana of any color.")
    private val swampBoost = Cards.invent("Cast Swamp Boost", "{2}", "Enchantment", "Whenever you tap a Swamp for mana, add an additional {B}.")
    private val elf = Cards.invent("Cast Elf", "{G}", "Creature — Elf", "{T}: Add {G}.", "1/1")
    private val lifeStone = Cards.invent("Cast Life Stone", "{2}", "Artifact", "Pay 1 life: Add {C}.")
    private val guide = Cards.invent("Cast Guide", "{1}{R}", "Creature — Ape", "Exile this card from your hand: Add {R}.", "2/2")
    private val ritual = Cards.invent("Cast Ritual", "{B}", "Instant", "Add {B}{B}{B}.")

    // A filter land: the parser has no wording for its three-way mana yet (a parser wording of its own), built directly
    private val filterLand: String = run {
        val base = Cards.definition(Cards.invent("Cast Filter Land Base", null, "Land", "{T}: Add {C}.")).faces.single()
        val own = base.abilities.single() as Ability.Activated
        val ability = own.copy(
            cost = own.cost.copy(parts = listOf(arcanum.api.CostPart.Mana(ManaCost.parse("{W/U}")!!)) + own.cost.parts),
            instructions = Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(2),
                listOf(ColorRef.Literal(Color.White), ColorRef.Literal(Color.Blue)), combination = true)), Players.You)))),
            text = "{W/U}, {T}: Add {W}{W}, {W}{U}, or {U}{U}.",
        )
        Cards.define("Cast Filter Land", CardDefinition(arcanum.api.Layout.Normal, listOf(base.copy(name = "Cast Filter Land", abilities = listOf(ability)))))
    }

    private fun Scenario.casts(player: ScenarioPlayer) =
        Engine.options(state, player.id).filterIsInstance<Input.Cast>().map { nameOf(state.objOrThrow(it.card)) }

    /** Casts [card] and pays with Autotap's suggestion. */
    private fun Scenario.castByAutotap(card: String) {
        perform(Input.Cast(alice.id, card(card)))
        val payment = decision<Decision.ManaPayment>()
        assertTrue(payment.suggestion.isNotEmpty(), "Autotap finds a way: $payment")
        answer(Answer.ManaPayment(payment.suggestion))
        assertTrue(state.pending == null, "the cast finished")
    }

    /** Casts [card] and pays by hand with the sources named, in the order given. */
    private fun Scenario.castByHand(card: String, vararg sources: String) {
        perform(Input.Cast(alice.id, card(card)))
        payByHand(*sources)
    }

    private fun Scenario.payByHand(vararg sources: String, colors: Map<String, Color> = emptyMap()) {
        val payment = decision<Decision.ManaPayment>()
        val uses = sources.map { name ->
            val all = payment.options.filterIsInstance<ManaUse.Ability>().filter { it.source == card(name) }
            assertTrue(all.isNotEmpty(), "$name is offered: ${payment.options}")
            colors[name]?.let { c -> all.first { it.color == c } } ?: all.first()
        }
        answer(Answer.ManaPayment(uses))
    }

    // CR 605.1a, 605.3a: every mana ability counts for whether a card is castable and pays, by Autotap
    @Test fun aPainLandsColouredAbilityCounts() = scenario {
        alice.battlefield(painLand)
        alice.hand(spark)
        assertTrue(spark in casts(alice))
        castByAutotap(spark)
        assertEquals(19, alice.life)
    }

    @Test fun anAncientTombStyleLandCounts() = scenario {
        alice.battlefield(tomb)
        alice.hand(colorless)
        assertTrue(colorless in casts(alice))
        castByAutotap(colorless)
        assertEquals(18, alice.life)
    }

    @Test fun aLandRemovingACounterCounts() = scenario {
        alice.battlefield(counterLand, counters = listOf(CounterKind.Named("charge") to 1))
        alice.hand(spark)
        assertTrue(spark in casts(alice))
        castByAutotap(spark)
        assertEquals(0, state.objOrThrow(card(counterLand)).counters(CounterKind.Named("charge")))
    }

    @Test fun aLandWithoutItsCounterDoesNotCount() = scenario {
        alice.battlefield(counterLand)
        alice.hand(spark)
        assertFalse(spark in casts(alice))
    }

    @Test fun aSacrificeAltarCounts() = scenario {
        alice.battlefield(altar)
        alice.battlefield(bear)
        alice.hand(spark)
        assertTrue(spark in casts(alice))
        castByAutotap(spark)
        assertTrue(state.player(alice.id).graveyard.any { nameOf(state.objOrThrow(it)) == bear })
    }

    @Test fun aSacrificeAltarWithNothingToSacrificeDoesNotCount() = scenario {
        alice.battlefield(altar)
        alice.hand(spark)
        assertFalse(spark in casts(alice))
    }

    // A chain counts by what it nets: a Plains into a Signet
    @Test fun aSignetPaidByAnotherLandCounts() = scenario {
        alice.battlefield("Plains")
        alice.battlefield(signet)
        alice.hand(whiteBlue)
        assertTrue(whiteBlue in casts(alice))
        castByAutotap(whiteBlue)
        assertTrue(state.objOrThrow(card(signet)).tapped)
        assertTrue(state.objOrThrow(card("Plains")).tapped)
    }

    // CR 605.3a: a Signet alone has nothing to pay its {1} with
    @Test fun aSignetAloneDoesNotCount() = scenario {
        alice.battlefield(signet)
        alice.hand(blueOne)
        assertFalse(blueOne in casts(alice))
    }

    // CR 605.3a: picked by hand in either order, the Plains runs first
    @Test fun aSignetChainIsPaidByHandWhicheverWayItIsPicked() = scenario {
        alice.battlefield("Plains")
        alice.battlefield(signet)
        alice.hand(whiteBlue)
        castByHand(whiteBlue, signet, "Plains")
        assertTrue(state.pending == null, "the cast finished")
        assertEquals(emptyList(), state.player(alice.id).manaPool)
    }

    // A Signet fed by a Signet: mana made by a chain's link pays the next one
    @Test fun aChainOfTwoSignetsCounts() = scenario {
        alice.battlefield("Plains")
        alice.battlefield(signet)
        alice.battlefield(signet)
        alice.hand(whiteBlue)
        val other = Cards.invent("Cast Three Mixed", "{W}{U}{U}", "Sorcery", "Draw a card.")
        alice.hand(other)
        assertTrue(other in casts(alice))
        castByAutotap(other)
    }

    // CR 106.1: {W/U}, {T}: Add {W}{W}, {W}{U}, or {U}{U}: an Island fed through it
    @Test fun aFilterLandCounts() = scenario {
        alice.battlefield("Island")
        alice.battlefield(filterLand)
        val twoWhite = Cards.invent("Cast Two White", "{W}{W}", "Sorcery", "Draw a card.")
        alice.hand(twoWhite)
        assertTrue(twoWhite in casts(alice))
        castByAutotap(twoWhite)
        assertEquals(emptyList(), state.player(alice.id).manaPool)
    }

    @Test fun aFilterLandFedTheWrongColourDoesNotCount() = scenario {
        alice.battlefield("Forest")
        alice.battlefield(filterLand)
        val twoWhite = Cards.invent("Cast Two White", "{W}{W}", "Sorcery", "Draw a card.")
        alice.hand(twoWhite)
        assertFalse(twoWhite in casts(alice))
    }

    @Test fun aColourChoiceCounts() = scenario {
        alice.battlefield(prism)
        alice.hand(blueOne)
        assertTrue(blueOne in casts(alice))
        perform(Input.Cast(alice.id, card(blueOne)))
        payByHand(prism, colors = mapOf(prism to Color.Blue))
        assertTrue(state.pending == null, "the cast finished")
    }

    // CR 605.1b: triggered mana abilities: a Swamp makes {B}{B}
    @Test fun triggeredManaCounts() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.battlefield(swampBoost)
        alice.hand(blackFour)
        assertTrue(blackFour in casts(alice))
        castByAutotap(blackFour)
        assertEquals(emptyList(), state.player(alice.id).manaPool)
    }

    // CR 113.6: a mana ability of a card in the hand
    @Test fun aManaAbilityInTheHandCounts() = scenario {
        alice.hand(guide, spark)
        assertTrue(spark in casts(alice))
        castByAutotap(spark)
        assertTrue(state.exile.any { nameOf(state.objOrThrow(it)) == guide })
    }

    // CR 118.3, 119.4 limit only paid life; CR 605.3a, 605.3b, 704.3, 704.5a: damage from a mana ability is an effect: the cast finishes,
    // then the player loses
    @Test fun lethalDamageFromAManaAbilityCountsAndTheCastFinishes() = scenario {
        alice.life = 1
        alice.battlefield(painLand)
        alice.hand(spark)
        assertTrue(spark in casts(alice))
        castByAutotap(spark)
        assertEquals(0, state.player(alice.id).life)
        assertTrue(state.stack.isNotEmpty(), "the cast is on the Stack")
    }

    // CR 119.4: paid life you don't have doesn't count
    @Test fun paidLifeYouDoNotHaveDoesNotCount() = scenario {
        alice.life = 0
        alice.battlefield(lifeStone)
        alice.hand(colorless)
        assertFalse(colorless in casts(alice))
    }

    @Test fun paidLifeYouHaveCounts() = scenario {
        alice.life = 2
        alice.battlefield(lifeStone)
        alice.hand(colorless)
        assertTrue(colorless in casts(alice))
        castByAutotap(colorless)
        assertEquals(0, alice.life)
    }

    // CR 605.3a: rituals aren't mana abilities: they can't be used during a cast, and the card glows once they resolve
    @Test fun aRitualDoesNotCount() = scenario {
        alice.battlefield("Swamp")
        alice.hand(ritual, blackFour)
        assertFalse(blackFour in casts(alice))
        assertTrue(ritual in casts(alice))
    }

    // Castability §1.2: floated mana counts and is spent first; the check doesn't look for "tap this first"
    @Test fun floatedManaCountsAndIsSpentFirst() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(spark)
        perform(Input.Activate(alice.id, card("Mountain", 0), Engine.view(state)[card("Mountain", 0)]!!.characteristics!!.abilities.first().origin))
        assertEquals(1, state.player(alice.id).manaPool.size)
        perform(Input.Cast(alice.id, card(spark)))
        val payment = decision<Decision.ManaPayment>()
        assertEquals(listOf<ManaUse>(ManaUse.Pool(0)), payment.suggestion)
        answer(Answer.ManaPayment(payment.suggestion))
        assertFalse(state.objOrThrow(card("Mountain", 1)).tapped, "the other land stays")
    }

    // Autotap's order (owner, §4): sources with no side effects first, damage only when needed
    @Test fun autotapPrefersDamageFreeSources() = scenario {
        alice.battlefield(painLand)
        alice.battlefield("Mountain")
        alice.hand(spark)
        castByAutotap(spark)
        assertFalse(state.objOrThrow(card(painLand)).tapped)
        assertEquals(20, alice.life)
    }

    @Test fun autotapUsesThePainLandWhenNeeded() = scenario {
        alice.battlefield(painLand)
        alice.battlefield("Forest")
        val two = Cards.invent("Cast Red Two", "{1}{R}", "Sorcery", "Draw a card.")
        alice.hand(two)
        castByAutotap(two)
        assertEquals(19, alice.life)
    }

    // Owner, §4: a lethal source last: only when it is the only way
    @Test fun autotapAvoidsALethalSourceWhileAnotherWayExists() = scenario {
        alice.life = 1
        alice.battlefield(painLand)
        alice.battlefield(bear)
        alice.battlefield(altar)
        alice.hand(spark)
        castByAutotap(spark)
        assertEquals(1, alice.life)
        assertFalse(state.objOrThrow(card(painLand)).tapped)
    }

    // ---- The cast's other costs (CR 601.2g before 601.2h)

    private val sacLand = Cards.invent("Cast Land Eater", "{G}", "Sorcery", "As an additional cost to cast this spell, sacrifice a land.\nDraw a card.")
    private val tapCreature = Cards.invent("Cast Tap Pact", "{G}", "Sorcery", "As an additional cost to cast this spell, tap an untapped creature you control.\nDraw a card.")
    private val payTwo = Cards.invent("Cast Blood Pact", "{R}", "Sorcery", "As an additional cost to cast this spell, pay 2 life.\nDraw a card.")
    private val sacCreature = Cards.invent("Cast Offering", "{R}", "Sorcery", "As an additional cost to cast this spell, sacrifice a creature.\nDraw a card.")

    // CR 601.2g before 601.2h: one Forest pays "{G}, sacrifice a land"
    @Test fun aLandTappedForManaIsThenSacrificedAsTheCost() = scenario {
        alice.battlefield("Forest")
        alice.hand(sacLand)
        assertTrue(sacLand in casts(alice))
        perform(Input.Cast(alice.id, card(sacLand)))
        // Its one option is taken
        val payment = decision<Decision.ManaPayment>()
        assertTrue(payment.suggestion.isNotEmpty())
        answer(Answer.ManaPayment(payment.suggestion))
        assertTrue(state.player(alice.id).graveyard.any { nameOf(state.objOrThrow(it)) == "Forest" })
    }

    // CR 601.2h: a creature needed for "tap an untapped creature" can't also tap for mana
    @Test fun aCreatureTappedForTheCostCannotAlsoTapForMana() = scenario {
        alice.battlefield(elf)
        alice.hand(tapCreature)
        assertFalse(tapCreature in casts(alice), "the Elf is the only creature and the only mana")
    }

    @Test fun anotherCreatureCanBeTappedForTheCostAndAutotapAvoidsIt() = scenario {
        alice.battlefield(elf)
        alice.battlefield(bear)
        alice.hand(tapCreature)
        assertTrue(tapCreature in casts(alice))
        perform(Input.Cast(alice.id, card(tapCreature)))
        answer(Answer.Objects(listOf(card(bear))))
        val payment = decision<Decision.ManaPayment>()
        assertEquals(listOf(card(elf)), payment.suggestion.map { (it as ManaUse.Ability).source })
        assertTrue(payment.options.none { (it as? ManaUse.Ability)?.source == card(bear) })
        answer(Answer.ManaPayment(payment.suggestion))
    }

    // CR 601.2g, 601.2h: a pain land's damage comes before a paid-life cost: at 2 life it can't be combined with "pay 2 life"
    @Test fun aPainLandCannotBeCombinedWithPaidLifeItLeavesTooLittleFor() = scenario {
        alice.life = 2
        alice.battlefield(painLand)
        alice.hand(payTwo)
        assertFalse(payTwo in casts(alice))
    }

    @Test fun aPainLandCanBeCombinedWithPaidLifeWithEnough() = scenario {
        alice.life = 3
        alice.battlefield(painLand)
        alice.hand(payTwo)
        assertTrue(payTwo in casts(alice))
        castByAutotap(payTwo)
        assertEquals(0, alice.life)
    }

    // CR 601.2g, 601.2h: a creature sacrificed for mana can't also be sacrificed for the cost
    @Test fun aCreatureSacrificedForManaCannotAlsoBeSacrificedForTheCost() = scenario {
        alice.battlefield(altar)
        alice.battlefield(bear)
        alice.hand(sacCreature)
        assertFalse(sacCreature in casts(alice))
    }

    @Test fun twoCreaturesPayTheAltarAndTheCost() = scenario {
        alice.battlefield(altar)
        alice.battlefield(bear)
        alice.battlefield(ox)
        alice.hand(sacCreature)
        assertTrue(sacCreature in casts(alice))
        perform(Input.Cast(alice.id, card(sacCreature)))
        answer(Answer.Objects(listOf(card(bear))))
        val payment = decision<Decision.ManaPayment>()
        answer(Answer.ManaPayment(payment.suggestion))
        assertEquals(2, state.player(alice.id).graveyard.size)
    }

    // ---- During resolution (CR 605.3a, 608.2g): "counter unless you pay"

    private val tax = Cards.invent("Cast Mana Tax", "{1}{U}", "Instant", "Counter target spell unless its controller pays {1}.")

    @Test fun aPaymentDuringResolutionTakesEveryManaKind() = scenario {
        alice.battlefield(painLand)
        alice.battlefield("Island", "Island")
        alice.battlefield(tomb)
        alice.hand(spark)
        bob.battlefield("Island", "Island")
        bob.hand(tax)
        perform(Input.Cast(alice.id, card(spark)))
        answer(Answer.ManaPayment(listOf(decision<Decision.ManaPayment>().options.filterIsInstance<ManaUse.Ability>().first { it.source == card(painLand) && it.color == Color.Red })))
        passPriority()
        perform(Input.Cast(bob.id, card(tax)))
        answer(Answer.Targets(listOf(TargetRef.Object(state.stack.first()))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        // "Unless pays": yes, and the Ancient Tomb style land pays with damage
        val may = decision<Decision.May>()
        assertTrue(may.payable)
        answer(Answer.YesNo(true))
        val payment = decision<Decision.ManaPayment>()
        val tombUse = payment.options.filterIsInstance<ManaUse.Ability>().first { it.source == card(tomb) }
        answer(Answer.ManaPayment(listOf(tombUse)))
        assertEquals(17, alice.life)
        assertEquals(1, state.player(alice.id).manaPool.size, "mana left over goes to the pool")
    }

    // ---- Selections (§3): candidates legal given what is picked; a cost falling with more targets

    private val fallsPerTarget: String = spellWith("Cast Many Targets", "{5}",
        CostChange.Less(ManaCost.parse("{1}")!!, Value.Count(Objects.Target("creatures"))))

    /** "Destroy any number of target creatures" that costs [change]. */
    private fun spellWith(name: String, cost: String, change: CostChange): String {
        val base = Cards.definition(Cards.invent("$name Base", cost, "Sorcery", "Draw a card.")).faces.single()
        val slot = arcanum.api.TargetSlot("creatures", arcanum.api.TargetKind.Objects(arcanum.api.ObjectFilter.Type(arcanum.api.CardType.Creature)), Value.Fixed(0), null)
        val fixed = Ability.Spell(Instructions(targets = listOf(slot), steps = listOf(Step.Do(Effect.Destroy(Objects.Target("creatures"))))), text = "Destroy any number of target creatures.")
        val cheaper = Ability.Static(listOf(Mechanic.CostChange(arcanum.api.ObjectFilter.Is(Objects.Self), change)), arcanum.api.Affected.Objects(Objects.Self), text = "cost")
        return Cards.define(name, CardDefinition(arcanum.api.Layout.Normal, listOf(base.copy(name = name, abilities = listOf(cheaper, fixed)))))
    }

    private fun Scenario.targetNames(refs: List<TargetRef>) = refs.map { nameOf(state.objOrThrow((it as TargetRef.Object).id)).toString() }.sorted()

    private fun Scenario.selectable(vararg picked: String) =
        Engine.selection(state, picked.map { TargetRef.Object(card(it)) })

    // CR 601.2f: {5}, {1} less per target, 3 lands: one target {4} doesn't pay, two {3} do
    @Test fun aCostFallingWithMoreTargetsIsCastableAndOnlyAPairFinishes() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(fallsPerTarget)
        bob.battlefield(bear)
        bob.battlefield(ox)
        assertTrue(fallsPerTarget in casts(alice))
        perform(Input.Cast(alice.id, card(fallsPerTarget)))
        val first = selectable()
        assertEquals(listOf(bear, ox).sorted(), targetNames(first.candidates), "each works as part of a pair")
        assertFalse(first.done)
        val one = selectable(bear)
        assertFalse(one.done, "one target costs {4}")
        assertTrue(ox in targetNames(one.candidates))
        assertTrue(selectable(bear, ox).done, "two targets cost {3}")
        answer(Answer.Targets(listOf(target(bear), target(ox))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        assertTrue(state.pending == null)
    }

    // Owner's example: {B}{B} for each creature type on the selected cards, 6 Swamps (a value over the targets, each read)
    private val threeTypes = Cards.invent("Cast Triple Beast", "{2}", "Creature — Elf Bear Ox", "", "1/1")
    private val fourTypes = Cards.invent("Cast Quad Beast", "{2}", "Creature — Elf Bear Ox Human", "", "1/1")
    private val twoA = Cards.invent("Cast Two A", "{2}", "Creature — Elf Bear", "", "1/1")
    private val twoB = Cards.invent("Cast Two B", "{2}", "Creature — Ox Human", "", "1/1")
    private val twoSame = Cards.invent("Cast Two Same", "{2}", "Creature — Elf Bear", "", "1/1")
    private val oneA = Cards.invent("Cast One A", "{2}", "Creature — Goat", "", "1/1")
    private val oneB = Cards.invent("Cast One B", "{2}", "Creature — Rat", "", "1/1")

    /** Reading one: every type of every selected card counts (a type on two cards twice). */
    private val perCard: String = spellWith("Cast Type Toll Per Card", "{0}",
        CostChange.More(ManaCost.parse("{B}{B}")!!, Value.Of(NumericProperty.CreatureTypes, Objects.Target("creatures"), Combine.Total)))

    /** Reading two: each creature type among the selected cards counts once. */
    private val distinctTypes: String = spellWith("Cast Type Toll Distinct", "{0}",
        CostChange.More(ManaCost.parse("{B}{B}")!!, Value.CountDistinct(Objects.Target("creatures"), SharedQuality.CreatureType)))

    @Test fun theOwnersExampleNarrowsTheCandidatesAsTheyArePicked() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(perCard)
        bob.battlefield(fourTypes)
        bob.battlefield(twoA)
        bob.battlefield(twoB)
        bob.battlefield(oneA)
        bob.battlefield(oneB)
        assertTrue(perCard in casts(alice))
        perform(Input.Cast(alice.id, card(perCard)))
        val start = selectable()
        assertFalse(fourTypes in targetNames(start.candidates), "4 types cost {B}{B}x4: 8 mana")
        assertTrue(twoA in targetNames(start.candidates))
        val withTwo = selectable(twoA)
        assertTrue(withTwo.done)
        assertFalse(twoB in targetNames(withTwo.candidates), "two more types: 8 mana")
        assertTrue(oneA in targetNames(withTwo.candidates))
        assertTrue(selectable(twoA, oneA).done)
        // Unselecting opens them again
        assertTrue(twoB in targetNames(selectable().candidates))
    }

    // The other reading: two creatures sharing their types count their types once, per-card twice
    @Test fun twoCreaturesSharingTheirTypesDependOnHowTheCardCountsThem() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(perCard, distinctTypes)
        bob.battlefield(twoA)
        bob.battlefield(twoSame)
        perform(Input.Cast(alice.id, card(perCard)))
        assertFalse(twoSame in targetNames(selectable(twoA).candidates), "counted per card: 8 mana")
        assertFalse(selectable(twoA, twoSame).done)
        perform(Input.Cancel(alice.id))
        perform(Input.Cast(alice.id, card(distinctTypes)))
        assertTrue(twoSame in targetNames(selectable(twoA).candidates), "the same two types: 4 mana")
        assertTrue(selectable(twoA, twoSame).done)
    }

    // ---- What a card shows (§2)

    private val banish = Cards.invent("Cast Banishing", "{4}{W}", "Sorcery",
        "This spell costs {2} less to cast if it targets a tapped creature.\nExile target creature.")
    private val plain = Cards.invent("Cast Plain Banish", "{2}{W}", "Sorcery", "Exile target creature.")

    @Test fun aSpellWithSeveralCostsSaysTheSmallestAndThatThereAreMore() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(banish)
        bob.battlefield(bear)
        bob.battlefield(ox, tapped = true)
        perform(Input.Cast(alice.id, card(banish)))
        val costs = Engine.castCosts(state)
        assertEquals("{2}{W}", costs.least.toString(), "the tapped one's, though the untapped is listed first")
        assertTrue(costs.several)
        assertTrue(costs.castable)
        assertEquals(3, costs.suggestion.size, "Autotap's sources for the minimum only")
    }

    @Test fun aSpellWithOneCostSaysJustThat() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(plain)
        bob.battlefield(bear)
        perform(Input.Cast(alice.id, card(plain)))
        val costs = Engine.castCosts(state)
        assertEquals("{2}{W}", costs.least.toString())
        assertFalse(costs.several)
    }

    // ---- Partial override (§4)

    @Test fun apickedSourceStaysAndAutotapFillsTheRest() = scenario {
        alice.battlefield("Plains", "Plains", "Mountain")
        val two = Cards.invent("Cast Any Two", "{2}", "Sorcery", "Draw a card.")
        alice.hand(two)
        perform(Input.Cast(alice.id, card(two)))
        val payment = decision<Decision.ManaPayment>()
        val mountain = payment.options.first { (it as? ManaUse.Ability)?.source == card("Mountain") }
        val filled = Engine.fillPayment(state, listOf(mountain), emptyList())
        assertEquals(2, filled.size)
        assertTrue(mountain in filled)
    }

    @Test fun aSourceTakenOutIsNotUsedAgain() = scenario {
        alice.battlefield("Plains", "Plains", "Mountain")
        val two = Cards.invent("Cast Any Two", "{2}", "Sorcery", "Draw a card.")
        alice.hand(two)
        perform(Input.Cast(alice.id, card(two)))
        val payment = decision<Decision.ManaPayment>()
        val plains = payment.options.filterIsInstance<ManaUse.Ability>().first { it.source == card("Plains", 0) }
        val filled = Engine.fillPayment(state, emptyList(), listOf(plains))
        assertEquals(2, filled.size)
        assertFalse(plains in filled)
        val mountain = payment.options.first { (it as? ManaUse.Ability)?.source == card("Mountain") }
        assertTrue(Engine.fillPayment(state, emptyList(), listOf(plains, mountain)).isEmpty(), "the rest can't be filled")
    }

    // A way exists but only by hand: an announced amount isn't searched, and doesn't deny
    @Test fun aSourceTheSearchCannotReadNeverDenies() = scenario {
        val xStone = Cards.invent("Cast X Stone", "{2}", "Artifact", "{X}, {T}: Add X mana of any one color.")
        alice.battlefield("Plains")
        alice.battlefield(xStone)
        alice.hand(blueOne)
        assertTrue(blueOne in casts(alice), "the Plains through the stone makes a blue")
        perform(Input.Cast(alice.id, card(blueOne)))
        val payment = decision<Decision.ManaPayment>()
        assertTrue(payment.suggestion.isEmpty(), "Autotap doesn't find it")
        val stone = payment.options.filterIsInstance<ManaUse.Ability>().first { it.source == card(xStone) && it.color == Color.Blue && it.answers.first() == Answer.Number(1) }
        val plains = payment.options.filterIsInstance<ManaUse.Ability>().first { it.source == card("Plains") }
        answer(Answer.ManaPayment(listOf(stone, plains)))
        assertNotNull(state.stack.firstOrNull(), "paid by hand")
    }
}
