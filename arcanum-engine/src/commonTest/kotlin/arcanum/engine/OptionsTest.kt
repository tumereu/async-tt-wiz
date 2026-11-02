package arcanum.engine

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** [Engine.options]: what a player may do now (CR 116, 117.1). */
class OptionsTest {
    private fun Scenario.options(player: arcanum.engine.scenario.ScenarioPlayer) = Engine.options(state, player.id)

    private fun Scenario.casts(player: arcanum.engine.scenario.ScenarioPlayer) =
        options(player).filterIsInstance<Input.Cast>().map { nameOf(state.objOrThrow(it.card)) }

    /** Every listed option is accepted by [Engine.perform] as the game stands (it may open a decision). */
    private fun Scenario.allAccepted(player: arcanum.engine.scenario.ScenarioPlayer) {
        options(player).forEach { input ->
            runCatching { Engine.perform(state, input) }.onFailure { throw AssertionError("$input was listed but refused: ${it.message}", it) }
        }
    }

    // CR 117.1, 305.1, 601.3: with priority in a main phase: pass, play a land, cast what can be paid, activate abilities
    @Test
    fun mainPhaseOptions() = scenario {
        alice.battlefield("Forest", "Lotus Frond")
        alice.hand("Island", "Elvish Seer", "Old Enmity", "Elvish Hierophant")
        val options = options(alice)
        assertTrue(Input.PassPriority(alice.id) in options)
        assertTrue(Input.PlayLand(alice.id, card("Island")) in options)
        assertTrue("Elvish Seer" in casts(alice))
        // {1}{G}{G} can't be paid with a Forest and the Lotus
        assertTrue("Elvish Hierophant" !in casts(alice))
        assertTrue(options.any { it is Input.Activate && it.source == card("Lotus Frond") })
        assertTrue(options.any { it is Input.Activate && it.source == card("Forest") })
        allAccepted(alice)
    }

    // CR 117.1: a player without priority has nothing to do
    @Test
    fun noPriorityNoOptions() = scenario {
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        assertTrue(options(bob).isEmpty())
    }

    // CR 307.1, 117.1a: on another player's turn: instants, not sorceries or lands
    @Test
    fun opponentsTurnOptions() = scenario {
        bob.battlefield("Mountain", "Mountain")
        bob.hand("Storm Spark", "Cinder Bolt", "Mountain")
        alice.passes()
        assertEquals(listOf("Storm Spark"), casts(bob))
        assertTrue(options(bob).none { it is Input.PlayLand })
        allAccepted(bob)
    }

    // CR 601.3a, 702.34a: flashback from the graveyard is offered
    @Test
    fun flashbackIsOffered() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard("Cinder Bolt")
        val cast = options(alice).filterIsInstance<Input.Cast>().single()
        assertEquals(card("Cinder Bolt"), cast.card)
        assertTrue(cast.way.permission != null)
        allAccepted(alice)
    }

    // CR 118.9: an alternative cost whose condition holds is offered as its own way to cast
    @Test
    fun alternativeCostIsOffered() = scenario {
        alice.battlefield("Swamp")
        bob.battlefield("Thicketwood Elves")
        alice.hand("Wink Out")
        val ways = options(alice).filterIsInstance<Input.Cast>().filter { it.card == card("Wink Out") }
        assertEquals(1, ways.size)
        assertTrue(ways.single().way.alternative != null)
        allAccepted(alice)
    }

    // CR 606.3: a loyalty ability isn't offered a second time in a turn
    @Test
    fun loyaltyAbilitiesOncePerTurn() = scenario {
        alice.battlefield("Renji Kagehara", counters = listOf(CounterKind.Loyalty to 3))
        alice.library("Forest", "Forest")
        assertEquals(2, options(alice).count { it is Input.Activate })
        alice.activates("Renji Kagehara", ability = 0)
        allPass()
        assertEquals(0, options(alice).count { it is Input.Activate })
    }

    // D58: with a decision open, its answers and (for a cast) Cancel by the caster
    @Test
    fun openDecisionOptions() = scenario {
        alice.battlefield("Mountain")
        bob.battlefield("Elvish Seer")
        alice.hand("Storm Spark")
        perform(Input.Cast(alice.id, card("Storm Spark")))
        val options = options(alice)
        assertTrue(Input.Cancel(alice.id) in options)
        assertEquals(3, options.count { it is Input.Answer })
        // P7: anyone may answer; only the caster cancels their cast
        assertEquals(options - Input.Cancel(alice.id), options(bob))
        allAccepted(alice)
        allAccepted(bob)
    }

    // §7.8: the mana payment's listed answer is the suggestion
    @Test
    fun paymentOptionIsTheSuggestion() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        perform(Input.Cast(alice.id, card("Storm Spark")))
        answer(Answer.Targets(listOf(target("bob"))))
        val answer = options(alice).filterIsInstance<Input.Answer>().single()
        perform(answer)
        expect { stack.names shouldBe listOf("Storm Spark") }
    }

    // CR 509.1: the defending player's options are block declarations
    @Test
    fun blockOptions() = scenario {
        alice.battlefield("Faerie Augur", "Thicketwood Elves")
        bob.battlefield("Elvish Seer")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Faerie Augur", "Thicketwood Elves")
        allPass()
        val options = options(bob)
        assertTrue(options.all { it is Input.DeclareBlockers })
        // The Seer can block the Elves but not the flier
        assertEquals(2, options.size)
        assertTrue(options(alice).isEmpty())
        allAccepted(bob)
    }

    // D56: a broad board: everything listed is accepted
    @Test
    fun everythingListedIsAccepted() = scenario {
        alice.battlefield("Forest", "Mountain", "Island", "Swamp", "Plains", "Elvish Seer", "Lotus Frond", "Eden Cloak", "Velis, Mender of the Lost Way")
        alice.hand("Storm Spark", "Grindstone Rasp", "Doomfire", "Flash Surge", "Bone Splinters", "Thicketwood Elves", "Forest", "Hallowed Mire Triad")
        alice.graveyard("Cinder Bolt", "Magma Needle")
        bob.battlefield("Thicketwood Elves", "Lotus Frond")
        allAccepted(alice)
        assertTrue(options(alice).size > 15)
    }

    // CR 601.2f, 601.3: a permission for cards in the hand ("without paying their mana costs") is offered beside the
    // usual cast, so it can be cast with no mana at all
    @Test
    fun handPermissionIsOffered() = scenario {
        alice.battlefield("Verdant Hollowmere")
        alice.hand("Elvish Seerling")
        val casts = options(alice).filterIsInstance<Input.Cast>().filter { it.card == card("Elvish Seerling") }
        assertEquals(1, casts.size)
        assertTrue(casts.single().way.permission != null)
        allAccepted(alice)
    }

    // CR 601.2h, 118.3: an additional cost that can't be paid ("sacrifice a creature or discard a card", with neither)
    @Test
    fun spellWithAnUnpayableCostIsntListed() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Bone Splinters")
        bob.battlefield("Thicketwood Elves")
        assertTrue("Bone Splinters" !in casts(alice))
        assertFailsWith<IllegalArgumentException> { perform(Input.Cast(alice.id, card("Bone Splinters"))) }
        allAccepted(alice)
    }

    // CR 601.2c, 700.2: a modal spell none of whose modes has a legal target isn't listed
    @Test
    fun modalSpellWithNoPossibleModeIsntListed() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Grindstone Rasp")
        assertTrue("Grindstone Rasp" !in casts(alice))
        assertFailsWith<IllegalArgumentException> { perform(Input.Cast(alice.id, card("Grindstone Rasp"))) }
    }

    // CR 702.122a: crew without creatures to tap isn't offered
    @Test
    fun crewWithoutCreaturesIsntListed() = scenario {
        alice.battlefield("Runner's Skiff")
        assertTrue(options(alice).none { it is Input.Activate && it.source == card("Runner's Skiff") })
        allAccepted(alice)
    }

    // CR 702.122a: crewing lists a group of creatures whose total power is enough
    @Test
    fun crewListsCreaturesWithEnoughPower() = scenario {
        alice.battlefield("Runner's Skiff", "Elvish Seer")
        perform(options(alice).filterIsInstance<Input.Activate>().first { it.source == card("Runner's Skiff") })
        val answers = options(alice).filterIsInstance<Input.Answer>()
        assertTrue(answers.isNotEmpty(), "No answer listed for ${state.pending?.decision}")
        allAccepted(alice)
    }

    // CR 601.2, P7: anyone may answer a cast's decisions, but only the caster cancels it
    @Test
    fun onlyTheCasterCancels() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        perform(Input.Cast(alice.id, card("Storm Spark")))
        assertFailsWith<IllegalArgumentException> { perform(Input.Cancel(bob.id)) }
        assertTrue(options(bob).none { it is Input.Cancel })
        perform(Input.Cancel(alice.id))
        expect { alice.hand has "Storm Spark" }
    }

    // ---- Every decision a listed cast or activation reaches has an answer listed (no dead end)

    private fun Scenario.castOf(player: arcanum.engine.scenario.ScenarioPlayer, name: String) =
        options(player).filterIsInstance<Input.Cast>().single { it.card == card(name) }

    private fun Scenario.numbersListed(player: arcanum.engine.scenario.ScenarioPlayer) =
        options(player).filterIsInstance<Input.Answer>().map { (it.answer as Answer.Number).value }

    // CR 107.3, 601.2b–c: X is offered at the value a target needs, here larger than the least few
    @Test
    fun xIsOfferedAtTheValueATargetNeeds() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand("Rescind")
        bob.battlefield("Bull of the Pyre")
        perform(castOf(alice, "Rescind"))
        assertEquals(listOf(5), numbersListed(alice))
        allAccepted(alice)
    }

    // CR 107.3, 601.2g–h: X is offered only as far as mana is found for the cost with it: {X}{U} with three Islands
    @Test
    fun xIsOfferedOnlyAsFarAsManaIsFound() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Rescind")
        bob.battlefield("Elvish Hierophant", "Wall of Portents")
        perform(castOf(alice, "Rescind"))
        assertEquals(listOf(2), numbersListed(alice))
        allAccepted(alice)
    }

    // CR 601.2c, 601.2g–h: a spell no X works for (the only target needs a larger X than mana pays) isn't offered
    @Test
    fun spellNoXWorksForIsntListed() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Rescind")
        bob.battlefield("Elvish Hierophant")
        assertTrue("Rescind" !in casts(alice))
        allAccepted(alice)
    }

    // CR 118.3, 602.2b: removing a counter the source doesn't have can't be paid, so the ability isn't offered
    @Test
    fun counterCostWithoutTheCounterIsntListed() = scenario {
        alice.battlefield("Wishfang Amulet")
        alice.battlefield("Swamp", "Swamp")
        assertTrue(options(alice).none { it is Input.Activate && it.source == card("Wishfang Amulet") })
        assertFailsWith<IllegalArgumentException> { alice.activates("Wishfang Amulet") }
        allAccepted(alice)
    }

    // CR 118.3, 602.2b: with the counter, it is
    @Test
    fun counterCostWithTheCounterIsListed() = scenario {
        alice.battlefield("Wishfang Amulet", counters = listOf(CounterKind.Named("wish") to 1))
        alice.battlefield("Swamp", "Swamp")
        assertTrue(options(alice).any { it is Input.Activate && it.source == card("Wishfang Amulet") })
        allAccepted(alice)
    }

    // CR 702.66a, 601.2g–h: cards to exile count when looking for a payment, and the suggested one exiles them
    @Test
    fun delveCountsWhenFindingAPayment() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Delve Through Ages")
        alice.graveyard("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.library("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        perform(castOf(alice, "Delve Through Ages"))
        val payment = pending as Decision.ManaPayment
        assertEquals(6, payment.suggestion.count { it is ManaUse.Special })
        assertTrue(options(alice).any { it is Input.Answer })
        allAccepted(alice)
    }

    // CR 702.66a, 118.3: without cards to exile the cost isn't found, so the spell isn't offered
    @Test
    fun delveWithoutCardsIsntListed() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Delve Through Ages")
        assertTrue("Delve Through Ages" !in casts(alice))
    }

    // CR 702.51a, 702.66a: "You can't spend mana to cast this spell": convoke and delve alone pay it, and the suggested
    // payment uses them
    @Test
    fun otherWaysAlonePayWhenManaCantBeSpent() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.battlefield("Elvish Seer", "Elvish Seer")
        alice.hand("Grommak, Risen Charnelhost")
        alice.graveyard("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        perform(castOf(alice, "Grommak, Risen Charnelhost"))
        val payment = pending as Decision.ManaPayment
        assertEquals(7, payment.suggestion.size)
        assertTrue(payment.suggestion.all { it is ManaUse.Special })
        allAccepted(alice)
    }

    // CR 601.2c: a later target that depends on an earlier one is looked ahead: Goblin Solderer's second target is an
    // artifact card in the graveyard of the first's controller
    @Test
    fun dependentTargetsAreLookedAhead() = scenario {
        alice.battlefield("Goblin Solderer", "Tinkered Mite")
        assertTrue(options(alice).none { it is Input.Activate && it.source == card("Goblin Solderer") })
        alice.graveyard("Gearwing Glider")
        assertTrue(options(alice).any { it is Input.Activate && it.source == card("Goblin Solderer") })
        allAccepted(alice)
    }
}
