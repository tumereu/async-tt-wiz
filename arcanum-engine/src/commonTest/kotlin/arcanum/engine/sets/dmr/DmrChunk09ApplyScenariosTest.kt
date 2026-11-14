package arcanum.engine.sets.dmr

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Dominaria Remastered chunk 09 in play: "until end of turn" effects. X announced for +X/+0 (CR 107.3a), losing all abilities and base
 * 0/1 (CR 613.1f, 613.4b), a sacrifice cost that only offers your own creatures (CR 701.21a), an effect that can't be blocked (CR 509.1b).
 */
class DmrChunk09ApplyScenariosTest {
    private val howl = Cards.invent("Howl of Embers", "{X}{B}", "Instant", "Target creature gets +X/+0 until end of turn.")
    private val hex = Cards.invent("Mooncalf Hex", "{1}{U}", "Instant",
        "Until end of turn, target creature loses all abilities and has base power and toughness 0/1.")
    private val ghoul = Cards.invent("Grimsky Ghoul", "{2}{B}", "Creature — Phyrexian Zombie",
        "Sacrifice a creature: This creature gets +2/+2 until end of turn.", "2/2")
    private val scout = Cards.invent("Tunnel Scout", "{1}{R}", "Creature — Goblin Scout",
        "When this creature enters, target creature with power 2 or less can't be blocked this turn.", "2/1")
    private val lemure = Cards.invent("Gossamer Lemure", "{4}{B}", "Creature — Spirit",
        "{0}: This creature gets -1/-0 and gains flying until end of turn.", "4/3")
    private val drake = Cards.invent("Stormwing Drake", "{2}{U}", "Creature — Drake", "Flying, vigilance", "3/3")
    private val bear = Cards.invent("Thicket Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")

    // CR 107.3a: X = 3 makes +3/+0
    @Test
    fun howlGivesPlusXPower() = scenario {
        alice.battlefield(bear, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(howl)
        alice.casts(howl, target = bear, x = 3)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear.toughness shouldBe 2
        }
    }

    // CR 613.1f, 613.4b: flying and vigilance are lost, base becomes 0/1
    @Test
    fun hexRemovesAbilitiesAndSetsBaseSize() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(hex)
        bob.battlefield(drake)
        alice.casts(hex, target = drake)
        resolveAll()
        expect {
            drake.power shouldBe 0
            drake.toughness shouldBe 1
            drake lacksKeyword Keyword.Flying
            drake lacksKeyword Keyword.Vigilance
        }
    }

    // CR 701.21a: only a creature you control can be sacrificed; with two own creatures the choice is asked
    @Test
    fun ghoulSacrificesOnlyYourOwnCreature() = scenario {
        alice.battlefield(ghoul, bear)
        bob.battlefield(ogre)
        alice.activates(ghoul)
        assertEquals(setOf(card(ghoul), card(bear)), decision<Decision.ChooseObjects>().options.toSet())
        alice.chooses(bear)
        resolveAll()
        expect {
            alice.graveyard has bear
            bob.battlefield has ogre
            ghoul.power shouldBe 4
            ghoul.toughness shouldBe 4
        }
    }

    // CR 509.1b: the unblockable 2-power creature gets through the blocker; the 3-power one is not a legal target
    @Test
    fun scoutMakesAnotherCreatureUnblockable() = scenario {
        alice.hand(scout)
        alice.battlefield(bear, "Mountain", "Mountain")
        bob.battlefield(ogre)
        alice.casts(scout)
        resolveAll()
        answer(Answer.Targets(listOf(target(bear))))
        allPass()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        alice.passes()
        advanceTo(TurnStep.DeclareBlockers)
        expect { bob.life shouldBe 20 }
        kotlin.test.assertFailsWith<Throwable> { bob.blocks(ogre to bear) }
    }

    // CR 613.4c: -1/-0 and flying; a {0} activation is free
    @Test
    fun lemureLosesPowerAndFlies() = scenario {
        alice.battlefield(lemure)
        alice.activates(lemure)
        resolveAll()
        expect {
            lemure.power shouldBe 3
            lemure hasKeyword Keyword.Flying
        }
    }
}
