package arcanum.engine.sets.bro

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test

/**
 * The Brothers' War chunk 12 in play: counters on Soldiers (a chosen other one, each other one), damage prevention for attacking
 * Soldiers only, and a counter trigger that works only during your own turn.
 */
class BroChunk12PutCountersScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val hound = Cards.invent("Rally Hound", "{3}{W}{W}", "Creature — Dog Soldier", """
        Flash
        When this creature enters, put a +1/+1 counter on each other Soldier you control.
        Prevent all damage that would be dealt to other attacking Soldiers you control.
    """, "3/3")
    private val cavalry = Cards.invent("Skyrider Cavalry", "{4}{W}", "Creature — Human Soldier", """
        Flying
        When this creature enters, put a +1/+1 counter on another target Soldier you control.
    """, "3/4")
    private val vigil = Cards.invent("Crypt Vigil", "{1}{B}", "Enchantment",
        "Whenever one or more artifact and/or creature cards leave your graveyard during your turn, put a +1/+1 counter on target creature you control.")
    private val recall = Cards.invent("Plain Recall", "{B}", "Instant", "Return target creature card from your graveyard to your hand.")
    private val pikeman = Cards.invent("Plain Pikeman", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val lancer = Cards.invent("Plain Lancer", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val bear = Cards.invent("Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val brute = Cards.invent("Plain Brute", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val corpse = Cards.invent("Plain Corpse", "{1}{B}", "Creature — Zombie", "", "1/1")

    // CR 122.1, 700.2: a chosen other Soldier, not a non-Soldier and not the entering creature itself
    @Test
    fun cavalryCountersAnotherSoldierOnly() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", pikeman, bear)
        alice.hand(cavalry)
        alice.casts(cavalry)
        playOut(pikeman)
        expect {
            pikeman.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 0
            cavalry.counters(p1p1) shouldBe 0
        }
    }

    // CR 122.1, 615.1, 506.4: every other Soldier grows; only attacking Soldiers are protected, the Soldier that blocks is not
    @Test
    fun houndCountersEachOtherSoldierAndShieldsAttackingSoldiers() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", pikeman, lancer, bear)
        bob.battlefield(brute)
        alice.hand(hound)
        alice.casts(hound)
        playOut()
        expect {
            pikeman.counters(p1p1) shouldBe 1
            lancer.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 0
            hound.counters(p1p1) shouldBe 0
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(pikeman, bear)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(brute to pikeman)
        advanceTo(TurnStep.CombatDamage)
        playOut()
        expect {
            pikeman.zone shouldBe Zone.Battlefield
            card(pikeman).view.obj!!.damage shouldBe 0
            brute.zone shouldBe Zone.Battlefield
        }
    }

    // CR 615.1: a non-Soldier attacker is not protected
    @Test
    fun houndDoesNotShieldANonSoldierAttacker() = scenario {
        alice.battlefield(hound, bear)
        bob.battlefield(brute)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(brute to bear)
        advanceTo(TurnStep.CombatDamage)
        playOut()
        expect { bear.zone shouldBe Zone.Graveyard }
    }

    // CR 603.4 (a trigger checks its condition), 500.1: the counter comes on your turn, not on the opponent's
    @Test
    fun vigilCountersOnlyDuringYourTurn() = scenario {
        alice.battlefield(vigil, "Swamp", "Swamp", bear)
        alice.hand(recall)
        alice.graveyard(corpse)
        alice.casts(recall, target = corpse)
        playOut(bear)
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun vigilDoesNothingOnTheOpponentsTurn() = scenario {
        turn(number = 2, active = bob, priority = alice)
        alice.battlefield(vigil, "Swamp", "Swamp", bear)
        alice.hand(recall)
        alice.graveyard(corpse)
        alice.casts(recall, target = corpse)
        playOut(bear)
        expect {
            corpse.zone shouldBe Zone.Hand
            bear.counters(p1p1) shouldBe 0
        }
    }
}
