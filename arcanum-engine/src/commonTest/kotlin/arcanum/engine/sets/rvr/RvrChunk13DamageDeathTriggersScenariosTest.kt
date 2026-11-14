package arcanum.engine.sets.rvr

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test

/** Ravnica Remastered chunk 13 in play: death triggers that deal damage (a chosen target, the dying creature's own power,
 * each opponent of three players) and a drain enchantment with an upkeep trigger and a sacrifice ability. The token,
 * destroy and plain damage cards are mechanically the same as tested ones. */
class RvrChunk13DamageDeathTriggersScenariosTest {
    private val fiend = Cards.invent("Rvr13 Lamplight Fiend", "{B/R}", "Creature — Devil", "When this creature dies, it deals 1 damage to any target.", "1/1")
    private val vengeance = Cards.invent("Rvr13 Lurking Vengeance", "{5}{R}{R}", "Creature — Avatar", """
        Haste
        Whenever another creature you control dies, it deals damage equal to its power to target player or planeswalker.
    """, "5/5")
    private val vampire = Cards.invent("Rvr13 Spiteful Vampire", "{3}{B}", "Creature — Vampire",
        "Whenever another creature you control dies, this creature deals 1 damage to each opponent and you gain 1 life.", "2/3")
    private val legacy = Cards.invent("Rvr13 Ill-Earned Legacy", "{3}{B}", "Enchantment", """
        At the beginning of your upkeep, this enchantment deals 1 damage to each opponent and you gain 1 life.
        {5}{B}, Sacrifice this enchantment: It deals 4 damage to target opponent and you gain 4 life.
    """)
    private val ogre = Cards.invent("Rvr13 Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val smite = Cards.invent("Rvr13 Smite", "{R}", "Instant", "Destroy target creature.")

    // CR 700.4, 603.10a, 115.1: a dies trigger chooses any target
    @Test
    fun fiendPingsAChosenTargetAsItDies() = scenario {
        alice.battlefield("Mountain", fiend)
        alice.hand(smite)
        alice.casts(smite, target = fiend)
        resolveAll()
        answer(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    // CR 603.10a, 608.2h, 120.3: the dying creature (3 power) deals the damage, not the 5/5 that triggered
    @Test
    fun vengeanceDealsTheDyingCreaturesPower() = scenario {
        alice.battlefield("Mountain", vengeance, ogre)
        alice.hand(smite)
        alice.casts(smite, target = ogre)
        resolveAll()
        answer(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    // CR 102.2 (each opponent), 120.3, 119.1: both opponents of three players lose 1 life
    @Test
    fun vampireDrainsEachOpponent() = scenario(players = 3) {
        alice.battlefield("Mountain", vampire, ogre)
        alice.hand(smite)
        alice.casts(smite, target = ogre)
        resolveAll()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    // CR 603.2 (upkeep trigger), 102.2, 120.3
    @Test
    fun legacyDrainsEachOpponentAtUpkeep() = scenario(players = 3) {
        alice.battlefield(legacy)
        alice.library(ogre, ogre, ogre)
        bob.library(ogre, ogre, ogre)
        carol.library(ogre, ogre, ogre)
        advanceTo(TurnStep.Upkeep, turn = 4)
        resolveAll()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    // CR 602.2 (sacrifice as a cost), 608.2h (last known information of the sacrificed enchantment as damage source)
    @Test
    fun legacySacrificeHitsOneChosenOpponent() = scenario(players = 3) {
        alice.battlefield(legacy, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.activates(legacy, ability = 0, target = "carol")
        resolveAll()
        expect {
            carol.life shouldBe 16
            bob.life shouldBe 20
            alice.life shouldBe 24
        }
    }
}
