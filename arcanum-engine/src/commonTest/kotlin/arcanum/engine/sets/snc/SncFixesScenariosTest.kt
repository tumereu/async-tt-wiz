package arcanum.engine.sets.snc

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test

/** Streets of New Capenna fixes in play: shield counters beside regeneration, damage marked and state-based actions. */
class SncFixesScenariosTest {
    private val shield = CounterKind.Shield
    private val brute = Cards.invent("SncFix Dockside Brute", "{2}{G}", "Creature — Ogre", "", "3/3")
    private val troll = Cards.invent("SncFix Sewer Troll", "{2}{G}", "Creature — Troll", "{G}: Regenerate this creature.", "3/3")
    private val jolt = Cards.invent("SncFix Alley Jolt", "{R}", "Instant", "SncFix Alley Jolt deals 2 damage to target creature.")
    private val ward = Cards.invent("SncFix Bodyguard's Oath", "{W}", "Instant", "Put a shield counter on target creature.")
    private val doom = Cards.invent("SncFix Quiet Doom", "{B}", "Instant", "Destroy target creature.")
    private val wither = Cards.invent("SncFix Withering Whisper", "{B}", "Instant", "Target creature gets -1/-1 until end of turn.")
    private val bear = Cards.invent("SncFix Alley Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val cub = Cards.invent("SncFix Alley Cub", "{G}", "Creature — Bear", "", "1/1")
    private val goad = Cards.invent("SncFix Taunting Heckle", "{R}", "Instant", "Target creature attacks during its controller's next combat phase if able.")
    private val detour = Cards.invent("SncFix Endless Roundabout", "{G}{W}{U}", "Instant",
        "The owner of target spell, nonland permanent, or card in a graveyard puts it on their choice of the top or bottom of their library.")

    /** A 3/3 with 2 damage marked, then given a shield counter. */
    private fun Scenario.damagedThenShielded() {
        alice.battlefield(brute, "Mountain", "Plains", "Swamp")
        alice.hand(jolt, ward, doom, wither)
        alice.casts(jolt, target = brute)
        resolveAll()
        alice.casts(ward, target = brute)
        resolveAll()
    }

    // CR 122.1c: the shield counter's replacement is only "instead remove a shield counter from it": unlike regeneration (CR 701.19a)
    // the damage marked stays
    @Test
    fun shieldCounterReplacingDestructionLeavesTheDamageMarked() = scenario {
        damagedThenShielded()
        alice.casts(doom, target = brute)
        resolveAll()
        expect {
            alice.battlefield has brute
            card(brute).counters(shield) shouldBe 0
            brute.view.obj?.damage shouldBe 2
        }
    }

    // CR 122.1c, 701.8b, 704.5g: destruction for lethal damage is a state-based action, not the result of an effect: the shield counter
    // doesn't replace it
    @Test
    fun shieldCounterDoesNotReplaceDestructionByStateBasedActions() = scenario {
        damagedThenShielded()
        alice.casts(wither, target = brute)
        resolveAll()
        expect { alice.graveyard has brute }
    }

    /** A Troll with a shield counter and a regeneration shield, then destroyed: its controller chooses which replacement applies. */
    private fun Scenario.shieldedTrollRegeneratingIsDestroyed(): Decision.ChooseReplacement {
        alice.battlefield(troll, "Forest", "Swamp", counters = listOf(shield to 1))
        alice.hand(doom)
        alice.activates(troll)
        resolveAll()
        alice.casts(doom, target = troll)
        resolveAll()
        val choice = decision<Decision.ChooseReplacement>()
        expect { choice.player shouldBe alice.id }
        return choice
    }

    // CR 616.1e, 122.1c, 701.19a: a shield counter and a regeneration shield both replace the destruction; the affected permanent's
    // controller chooses: regenerating keeps the shield counter
    @Test
    fun regenerationChosenFirstKeepsTheShieldCounter() = scenario {
        val choice = shieldedTrollRegeneratingIsDestroyed()
        answer(Answer.Replacement(choice.options.first { it.text != "Remove a shield counter instead" }.index))
        resolveAll()
        expect {
            alice.battlefield has troll
            card(troll).counters(shield) shouldBe 1
            troll.tapped shouldBe true
        }
    }

    // CR 616.1e, 122.1c: choosing the shield counter uses it up and leaves the regeneration shield for later
    @Test
    fun shieldCounterChosenFirstKeepsTheRegenerationShield() = scenario {
        val choice = shieldedTrollRegeneratingIsDestroyed()
        answer(Answer.Replacement(choice.options.first { it.text == "Remove a shield counter instead" }.index))
        resolveAll()
        expect {
            alice.battlefield has troll
            card(troll).counters(shield) shouldBe 0
            troll.tapped shouldBe false
        }
    }

    // CR 122.1c, 510.2, 615.13: combat damage from two blockers is one damage event; the shield counters' one prevention effect prevents
    // all of it and removes a single shield counter
    @Test
    fun damageFromTwoBlockersAtOnceRemovesOneShieldCounter() = scenario(players = 3) {
        alice.battlefield(brute, counters = listOf(shield to 2))
        bob.battlefield(bear, cub)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(brute, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bear to brute, cub to brute)
        var guard = 20
        while (state.turn.step != TurnStep.EndOfCombat && guard-- > 0) {
            if (pending is Decision.Divide) divideAsSuggested() else passPriority()
        }
        expect {
            alice.battlefield has brute
            card(brute).counters(shield) shouldBe 1
            brute.view.obj?.damage shouldBe 0
        }
    }

    // CR 608.2b, 401.4, 400.3: a spell on the stack is a legal target; as Detour resolves the spell's owner chooses top or bottom and the
    // spell goes there (it never resolves)
    @Test
    fun detourPutsATargetSpellOnItsOwnersLibrary() = scenario(players = 3) {
        turn(number = 2, active = bob)
        bob.hand(bear)
        bob.battlefield("Forest", "Forest")
        bob.library("Plains")
        alice.hand(detour)
        alice.battlefield("Forest", "Plains", "Island")
        bob.casts(bear)
        bob.passes()
        carol.passes()
        alice.casts(detour, target = bear)
        resolveAll()
        val choice = decision<Decision.ChooseOption>()
        expect { choice.player shouldBe bob.id }
        answer(Answer.Option("bottom"))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Library
            bob.library.size shouldBe 2
            state.stack.isEmpty() shouldBe true
        }
    }

    // CR 508.1d, 611.2a: made during its controller's own declare attackers step (after attackers are declared), the requirement is for
    // that player's next combat phase, not the one under way
    @Test
    fun mustAttackMadeDuringItsControllersDeclareAttackersStepLastsToTheirNextCombat() = scenario(players = 3) {
        turn(number = 2, active = bob)
        bob.battlefield(bear)
        alice.battlefield("Mountain")
        alice.hand(goad)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        carol.library("Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks()
        bob.passes()
        carol.passes()
        alice.casts(goad, target = bear)
        resolveAll()
        advanceTo(TurnStep.EndOfCombat)
        expect { bear hasMechanic { it is arcanum.api.Mechanic.MustAttack } }
        advanceTo(TurnStep.DeclareAttackers, turn = 5)
        kotlin.test.assertFailsWith<IllegalArgumentException> { bob.attacks() }
        bob.attacks(bear, defender = "carol")
        advanceTo(TurnStep.EndOfCombat)
        expect { bear lacksMechanic { it is arcanum.api.Mechanic.MustAttack } }
    }
}
