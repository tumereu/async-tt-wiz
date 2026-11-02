package arcanum.engine.review

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Review 2: combat evasion and damage (CR 509, 510, 702.2, 702.7, 702.19, 702.110, 702.28, 702.14). */
class Review2CombatTest {
    private val venomBrute = Cards.invent("Review Venom Brute", "{3}{B}{G}", "Creature — Beast", "Deathtouch\nTrample", "4/4")

    /** Deathtouch and trample: 1 damage is lethal to the blocker, the rest tramples over. */
    // CR 702.19c, 702.2c, 510.1c
    @Test
    fun deathtouchTrampleAssignsOneToTheBlocker() = scenario {
        alice.battlefield(venomBrute)
        bob.battlefield("Brakka, Wild Whelp")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(venomBrute)
        allPass()
        bob.blocks("Brakka, Wild Whelp" to venomBrute)
        allPass()
        if (pending is Decision.Divide) divideAsSuggested()
        advanceTo(TurnStep.EndOfCombat)
        expect {
            bob.graveyard has "Brakka, Wild Whelp"
            bob.life shouldBe 17
        }
    }

    /** First strike: Seraphine kills a 1/1 blocker in the first-strike step, which then deals no damage. */
    // CR 510.4, 702.7b
    @Test
    fun firstStrikeKillsBeforeTheRegularStep() = scenario {
        alice.battlefield("Seraphine, Keeper of the Gate")
        bob.battlefield("Elvish Seer")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Seraphine, Keeper of the Gate")
        allPass()
        bob.blocks("Elvish Seer" to "Seraphine, Keeper of the Gate")
        advanceTo(TurnStep.EndOfCombat)
        expect {
            bob.graveyard has "Elvish Seer"
            alice.battlefield has "Seraphine, Keeper of the Gate"
        }
    }

    /** Golem Grafter's token is a Golem with first strike from the Grafter (a granted ability). */
    // CR 613.1f, 702.7
    @Test
    fun grantedFirstStrike() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand("Golem Grafter")
        alice.casts("Golem Grafter")
        repeat(2) { allPass() }
        expect { "Phyrexian Golem Token" hasKeyword arcanum.api.Keyword.FirstStrike }
    }

    /** Menace: one blocker isn't enough. */
    // CR 702.110b, 509.1b
    @Test
    fun menaceNeedsTwoBlockers() = scenario {
        alice.battlefield("Unyielding Dead")
        bob.battlefield("Elvish Seer", "Thicketwood Elves")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Unyielding Dead")
        allPass()
        assertFailsWith<IllegalArgumentException> {
            Engine.perform(state, Input.DeclareBlockers(bob.id, listOf(Block(card("Elvish Seer"), card("Unyielding Dead")))))
        }
        bob.blocks("Elvish Seer" to "Unyielding Dead", "Thicketwood Elves" to "Unyielding Dead")
        expect { stack.size shouldBe 0 }
    }

    /** Brash Pilferer can block only creatures with flying. */
    // CR 509.1b
    @Test
    fun blocksOnlyFlyers() = scenario {
        alice.battlefield("Brakka, Wild Whelp")
        bob.battlefield("Brash Pilferer")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Brakka, Wild Whelp")
        allPass()
        assertTrue(Engine.options(state, bob.id).filterIsInstance<Input.DeclareBlockers>().none { it.blocks.isNotEmpty() })
    }

    /** Forestwalk: Nutcracker can't be blocked while the defending player controls a Forest. */
    // CR 702.14c
    @Test
    fun forestwalk() = scenario {
        alice.battlefield("Nutcracker, Squirrel Marshal")
        bob.battlefield("Elvish Seer", "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Nutcracker, Squirrel Marshal")
        allPass()
        assertFailsWith<IllegalArgumentException> {
            Engine.perform(state, Input.DeclareBlockers(bob.id, listOf(Block(card("Elvish Seer"), card("Nutcracker, Squirrel Marshal")))))
        }
    }

    /** Shadow: a creature without shadow can't block Netherbound Turncoat. */
    // CR 702.28b
    @Test
    fun shadowCantBeBlockedByOthers() = scenario {
        alice.battlefield("Netherbound Turncoat")
        bob.battlefield("Elvish Seer")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Netherbound Turncoat")
        allPass()
        assertFailsWith<IllegalArgumentException> {
            Engine.perform(state, Input.DeclareBlockers(bob.id, listOf(Block(card("Elvish Seer"), card("Netherbound Turncoat")))))
        }
    }

    /** Shadow: a creature with shadow can't block one without. */
    // CR 702.28b
    @Test
    fun shadowCantBlockOthers() = scenario {
        alice.battlefield("Brakka, Wild Whelp")
        bob.battlefield("Netherbound Turncoat")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Brakka, Wild Whelp")
        allPass()
        assertFailsWith<IllegalArgumentException> {
            Engine.perform(state, Input.DeclareBlockers(bob.id, listOf(Block(card("Netherbound Turncoat"), card("Brakka, Wild Whelp")))))
        }
    }

    /** Frost-Scale Serpent has deathtouch only while its controller controls at least three other snow permanents:
     * without them, it hasn't. */
    // CR 611.3a, 604.1
    @Test
    fun conditionalDeathtouchOff() = scenario {
        alice.battlefield("Frost-Scale Serpent")
        bob.battlefield("Brakka, Wild Whelp")
        expect { "Frost-Scale Serpent" lacksKeyword arcanum.api.Keyword.Deathtouch }
    }
}
