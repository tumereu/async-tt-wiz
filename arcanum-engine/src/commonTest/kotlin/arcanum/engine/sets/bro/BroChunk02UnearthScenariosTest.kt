package arcanum.engine.sets.bro

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Brothers' War chunk 02 in play: Unearth with an enters-from-your-graveyard trigger, a trigger that works on the Stack and on
 * the battlefield whose destroyed permanent's controller makes a tapped Powerstone, and "choose three" modes (three players).
 */
class BroChunk02UnearthScenariosTest {
    private val powerstoneReminder = "(It's an artifact with \"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\")"
    private val unearthReminder = "({3}{R}{R}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)"

    private val rubbleColossus = Cards.invent("Rubble Colossus", "{8}", "Artifact Creature — Construct", """
        Trample
        When you cast this spell and whenever this creature attacks, destroy up to one target nonland permanent. Its controller creates a tapped Powerstone token. $powerstoneReminder
        Unearth {8}
    """, "8/8")
    private val emberWyrmEngine = Cards.invent("Ember Wyrm Engine", "{3}", "Artifact Creature — Phyrexian Dragon", """
        Double strike
        When this creature enters from your graveyard, you may discard your hand. If you do, draw three cards.
        Unearth {3}{R}{R} $unearthReminder
    """, "2/2")
    private val orrinLostToRust = Cards.invent("Orrin, Lost to Rust", "{9}", "Legendary Artifact Creature — Phyrexian Artificer", """
        Whenever Orrin enters or attacks, choose three —
        • Target opponent discards two cards.
        • Orrin deals 3 damage to any target.
        • Destroy target artifact or planeswalker.
        • Creatures you control gain menace and trample until end of turn.
        • Creatures you don't control get -1/-1 until end of turn.
        • Create two tapped Powerstone tokens. $powerstoneReminder
    """, "9/9")
    private val targetTrinket = Cards.invent("Rusted Trinket", "{1}", "Artifact")
    private val mudBear = Cards.invent("Mud Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val rockOgre = Cards.invent("Rock Ogre", "{3}{R}", "Creature — Ogre", "", "3/3")
    private val sandScout = Cards.invent("Sand Scout", "{1}{W}", "Creature — Human Scout", "", "2/2")

    private fun Scenario.powerstones(player: ScenarioPlayer) = player.battlefield.names.count { it == "Powerstone Token" }
    private val islands = Array(8) { "Island" }

    // CR 603.3a, 113.6k: the cast trigger works from the Stack; CR 608.2h: the destroyed permanent's controller (the third player)
    // makes the tapped Powerstone, not the caster
    @Test
    fun rubbleColossusCastTriggerDestroysAndItsControllerGetsAPowerstone() = scenario(players = 3) {
        alice.battlefield(*islands)
        carol.battlefield(targetTrinket)
        alice.hand(rubbleColossus)
        alice.casts(rubbleColossus)
        playOut(targetTrinket)
        expect {
            carol.graveyard has targetTrinket
            powerstones(carol) shouldBe 1
            "Powerstone Token".tapped shouldBe true
            powerstones(alice) shouldBe 0
            alice.battlefield has rubbleColossus
        }
    }

    // CR 508.1m, 603.3: the same ability when the creature attacks, with no target chosen (up to one): nothing is destroyed or made
    @Test
    fun rubbleColossusAttackTriggerWithoutATargetDoesNothing() = scenario(players = 3) {
        alice.battlefield(rubbleColossus)
        carol.battlefield(targetTrinket)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rubbleColossus)
        playOut()
        expect {
            carol.battlefield has targetTrinket
            powerstones(carol) shouldBe 0
        }
    }

    // CR 603.3, 608.2h: attacking, it destroys the target and the attack goes on
    @Test
    fun rubbleColossusAttackTriggerDestroysAndGivesAPowerstone() = scenario(players = 3) {
        alice.battlefield(rubbleColossus)
        carol.battlefield(targetTrinket)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rubbleColossus)
        playOut(targetTrinket)
        expect {
            carol.graveyard has targetTrinket
            powerstones(carol) shouldBe 1
            powerstones(alice) shouldBe 0
        }
    }

    // CR 702.84a, 603.6a: unearthed, it enters from its owner's graveyard: discard the hand, draw three
    @Test
    fun emberWyrmEngineUnearthedDiscardsTheHandAndDraws() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard(emberWyrmEngine)
        alice.hand("Island", "Swamp")
        alice.library("Plains", "Plains", "Plains", "Plains")
        alice.activates(emberWyrmEngine)
        playOut()
        expect {
            alice.graveyard has "Island"
            alice.graveyard has "Swamp"
            alice.hand.names.count { it == "Plains" } shouldBe 3
            alice.hand lacks "Island"
        }
    }

    // CR 608.2c, 118.12: declining the "may" keeps the hand, and "if you do" draws nothing
    @Test
    fun emberWyrmEngineUnearthedDecliningDrawsNothing() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard(emberWyrmEngine)
        alice.hand("Island", "Swamp")
        alice.library("Plains", "Plains", "Plains", "Plains")
        alice.activates(emberWyrmEngine)
        playOut(may = false)
        expect {
            alice.hand has "Island"
            alice.hand has "Swamp"
            alice.hand lacks "Plains"
        }
    }

    // CR 603.6a: cast from the hand it enters from the stack, not from the graveyard: no trigger
    @Test
    fun emberWyrmEngineCastFromTheHandDoesNotTrigger() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(emberWyrmEngine, "Swamp")
        alice.library("Plains", "Plains", "Plains", "Plains")
        alice.casts(emberWyrmEngine)
        playOut()
        expect {
            alice.battlefield has emberWyrmEngine
            alice.hand has "Swamp"
            alice.hand lacks "Plains"
        }
    }

    // CR 700.2, 603.3c: three modes of six; with three players the opponent target and the any target can both be the third player
    @Test
    fun orrinChoosesThreeModes() = scenario(players = 3) {
        alice.battlefield(orrinLostToRust)
        carol.hand("Island", "Swamp", "Plains")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(orrinLostToRust)
        resolveModes(listOf(0, 1, 5), "carol", "Island", "Swamp")
        expect {
            carol.hand.names.size shouldBe 1
            carol.life shouldBe 17
            bob.life shouldBe 20
            powerstones(alice) shouldBe 2
        }
    }

    // CR 700.2d: the same mode can't be chosen twice, and exactly three must be chosen
    @Test
    fun orrinCannotChooseTheSameModeTwiceOrFewerThanThree() = scenario {
        alice.battlefield(orrinLostToRust)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(orrinLostToRust)
        resolveAllStack()
        val d = decision<Decision.Modes>()
        assertTrue(d.min == 3 && d.max == 3)
        kotlin.test.assertFailsWith<IllegalArgumentException> { answer(Answer.Modes(listOf(0, 0, 1))) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { answer(Answer.Modes(listOf(0, 1))) }
    }

    // CR 109.5, 611.2: "creatures you don't control" is every opponent's creatures, with three players both lose 1/1; yours gain the keywords
    @Test
    fun orrinShrinksEveryOpponentsCreaturesAndGivesMenaceAndTrample() = scenario(players = 3) {
        alice.battlefield(orrinLostToRust, sandScout)
        bob.battlefield(mudBear)
        carol.battlefield(rockOgre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(orrinLostToRust)
        resolveModes(listOf(3, 4, 5), "carol")
        expect {
            card(mudBear).power shouldBe 1
            card(rockOgre).power shouldBe 2
            card(sandScout).power shouldBe 2
            sandScout hasKeyword Keyword.Menace
            sandScout hasKeyword Keyword.Trample
        }
    }

    private fun Scenario.resolveAllStack() {
        var guard = 20
        while (pending == null && state.stack.isNotEmpty() && guard-- > 0) allPass()
    }

    private fun Scenario.resolveModes(modes: List<Int>, vararg picks: String) {
        resolveAllStack()
        decision<Decision.Modes>()
        answer(Answer.Modes(modes))
        playOut(*picks)
    }
}
