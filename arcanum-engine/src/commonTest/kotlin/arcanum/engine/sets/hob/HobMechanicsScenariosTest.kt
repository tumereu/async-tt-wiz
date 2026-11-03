package arcanum.engine.sets.hob

import arcanum.api.CounterKind
import arcanum.api.Designation
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Hobbit's mechanics in play: storied and the enduring story, recruit, amass Goblins, hone counters. */
class HobMechanicsScenariosTest {
    private val ondo = Cards.invent("Ondo, Keeper of Ballads", "{2}{W}", "Legendary Creature — Dwarf Bard", """
        Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
        As long as you have an enduring story, Ondo gets +1/+0 and has vigilance.
    """, "3/3")
    private val bombrin = Cards.invent("Bombrin, Gentle Sleeper", "{2}{R}", "Legendary Creature — Dwarf Bard", """
        Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
        Bombrin doesn't untap during your untap step unless you have an enduring story.
    """, "5/3")
    private val trinket = Cards.invent("Brass Trinket", "{1}", "Artifact")
    private val tutor = Cards.invent("Steady Tutor", "{2}{W/U}", "Creature — Human Citizen", """
        Vigilance
        When this creature enters, recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)
    """, "2/2")
    private val lackeys = Cards.invent("Cave-town Lackeys", "{1}{R}", "Creature — Goblin Soldier", """
        Haste
        When this creature enters, amass Goblins 1. (Put a +1/+1 counter on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)
    """, "1/1")
    private val boneArmy = Cards.invent("Bone Army", "{2}{B}", "Creature — Zombie Army", "", "0/0")
    private val grukk = Cards.invent("Grukk, Deepdelve's Ruin", "{2}{B}", "Legendary Creature — Goblin Soldier",
        "When Grukk enters, destroy up to one other target creature. Its controller amasses Goblins X, where X is that creature's power. If you controlled that creature, draw a card. (To amass Goblins X, that player puts X +1/+1 counters on an Army they control. It's also a Goblin. If they don't control an Army, they create a 0/0 black Goblin Army creature token first.)",
        "1/3")
    private val vastGoblin = Cards.invent("The Vast Goblin", "{1}{B/R}{B/R}", "Legendary Creature — Goblin Noble", """
        Whenever you put one or more counters on a Goblin, Orc, or Army you control, The Vast Goblin deals 2 damage to target opponent.
        Whenever another Goblin, Orc, or Army you control dies, exile the top card of your library. You may play it until the end of your next turn.
    """, "3/2")
    private val dwerin = Cards.invent("Dwerin, Armsmaster", "{1}{R/W}", "Legendary Creature — Dwarf Warrior", """
        First strike
        Whenever Dwerin enters or attacks, put a hone counter on each Equipment you control. (Each hone counter on an Equipment grants +1/+0 to equipped creature.)
    """, "2/1")
    private val ladle = Cards.invent("Much-Used Ladle", "{1}", "Artifact — Equipment", """
        When this Equipment enters, you gain 2 life.
        Equipped creature gets +1/+1.
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val krali = Cards.invent("Krali the Handy", "{1}{W}", "Legendary Creature — Dwarf Scout", """
        Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
        As long as you have an enduring story, you may pay {0} rather than pay the equip cost of the first equip ability you activate each turn.
        Whenever another Dwarf or Equipment you control enters, draw a card. This ability triggers only once each turn.
    """, "1/2")
    private val pike = Cards.invent("Tattered Short Pike", "{1}{R}", "Artifact — Equipment", """
        When this Equipment enters, you may discard a card. If you do, draw two cards.
        Equipped creature gets +2/+0.
        Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val basher = Cards.invent("Dwarven Basher", "{R}", "Creature — Dwarf Warrior",
        "Equip abilities you activate that target this creature cost {2} less to activate.", "2/1")
    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val shock = Cards.invent("Quick Shock", "{R}", "Instant", "Quick Shock deals 2 damage to any target.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private fun Scenario.hasStory(player: arcanum.engine.scenario.ScenarioPlayer) = Designation.EnduringStory in state.player(player.id).designations

    /** Three artifacts and legendaries give an enduring story at once, kept after they leave; its abilities then apply. */
    // CR 702.195a, 702.195b
    @Test
    fun storiedGivesAnEnduringStoryForTheRestOfTheGame() = scenario {
        alice.battlefield(ondo, trinket, "Plains")
        alice.hand(trinket)
        expect { ondo.power shouldBe 3 }
        assertTrue(!hasStory(alice))
        alice.casts(trinket)
        resolveAll()
        assertTrue(hasStory(alice))
        assertTrue(!hasStory(bob))
        expect {
            ondo.power shouldBe 4
            ondo hasKeyword Keyword.Vigilance
        }
        move(trinket, Zone.Graveyard)
        expect { ondo.power shouldBe 4 }
        assertTrue(hasStory(alice))
    }

    /** Without an enduring story it stays tapped in its controller's untap step. */
    // CR 502.3, 702.195b
    @Test
    fun doesntUntapWithoutAStory() = scenario {
        alice.battlefield(bombrin, tapped = true)
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { bombrin.tapped shouldBe true }
    }

    /** With an enduring story it untaps as usual. */
    // CR 502.3
    @Test
    fun untapsWithAStory() = scenario {
        alice.battlefield(bombrin, tapped = true)
        alice.battlefield(trinket, trinket)
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { bombrin.tapped shouldBe false }
    }

    /** Recruit: draw, then discard; a nonland card discarded makes a 1/1 white Human Soldier. */
    // CR 701.70a
    @Test
    fun recruitDiscardingANonlandCardMakesASoldier() = scenario {
        alice.battlefield("Plains", "Plains", "Island")
        alice.hand(tutor)
        alice.library(bear)
        alice.casts(tutor)
        resolveAll()
        expect { alice.graveyard has bear }
        val soldiers = state.battlefield.filter { state.obj(it)?.token == true }
        assertEquals(1, soldiers.size)
        assertEquals(listOf("Human", "Soldier"), view()[soldiers.single()]!!.characteristics!!.subtypes.map { it.name })
        assertEquals(1, view()[soldiers.single()]!!.characteristics!!.power)
    }

    /** Discarding a land makes no token. */
    // CR 701.70a
    @Test
    fun recruitDiscardingALandMakesNothing() = scenario {
        alice.battlefield("Plains", "Plains", "Island")
        alice.hand(tutor)
        alice.library("Forest")
        alice.casts(tutor)
        resolveAll()
        assertTrue(state.battlefield.none { state.obj(it)?.token == true })
    }

    /** Amass with no Army makes a 0/0 black Goblin Army and puts the counters on it; amassing again grows the same one. */
    // CR 701.47a
    @Test
    fun amassMakesAnArmyAndGrowsIt() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(lackeys, lackeys)
        alice.casts(lackeys)
        resolveAll()
        val army = state.battlefield.single { state.obj(it)?.token == true }
        assertEquals(listOf("Goblin", "Army"), view()[army]!!.characteristics!!.subtypes.map { it.name })
        assertEquals(1, state.obj(army)!!.counters(p1p1))
        alice.casts(lackeys)
        resolveAll()
        assertEquals(1, state.battlefield.count { state.obj(it)?.token == true })
        assertEquals(2, state.obj(army)!!.counters(p1p1))
    }

    /** An Army that isn't a Goblin becomes one too. */
    // CR 701.47a
    @Test
    fun amassMakesTheArmyAGoblin() = scenario {
        alice.battlefield(boneArmy, counters = listOf(p1p1 to 1))
        alice.battlefield("Mountain", "Mountain")
        alice.hand(lackeys)
        alice.casts(lackeys)
        resolveAll()
        expect {
            boneArmy.counters(p1p1) shouldBe 2
            assertTrue(Subtype("Goblin") in boneArmy.subtypes && Subtype("Zombie") in boneArmy.subtypes)
        }
    }

    /** The destroyed creature's controller amasses, X its power as it last existed. */
    // CR 701.47a, 608.2h
    @Test
    fun theDestroyedCreaturesControllerAmasses() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(grukk)
        bob.battlefield(ogre)
        alice.casts(grukk)
        resolveAll()
        if (pending != null) alice.answers(Answer.Targets(listOf(target(ogre))))
        resolveAll()
        expect { bob.graveyard has ogre }
        val army = state.battlefield.single { state.obj(it)?.token == true }
        assertEquals(bob.id, view()[army]!!.controller)
        assertEquals(3, state.obj(army)!!.counters(p1p1))
    }

    /** Putting counters on a Goblin Army you control: 2 damage to the opponent, once for the batch. */
    // CR 603.2c
    @Test
    fun countersOnAnArmyDealDamage() = scenario {
        alice.battlefield(vastGoblin, "Mountain", "Mountain")
        alice.hand(lackeys)
        alice.casts(lackeys)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    /** Each hone counter on an Equipment gives the equipped creature +1/+0. */
    // CR 122.1j
    @Test
    fun honeCountersGivePower() = scenario {
        alice.battlefield(ladle, bear, "Mountain", "Plains")
        attach(ladle, bear)
        alice.hand(dwerin)
        expect { bear.power shouldBe 3 }
        alice.casts(dwerin)
        resolveAll()
        expect {
            ladle.counters(CounterKind.Hone) shouldBe 1
            bear.power shouldBe 4
            bear.toughness shouldBe 3
        }
    }

    /** With an enduring story the first equip ability each turn may be paid with {0}; the next costs its own cost. */
    // CR 118.9, 602.2b
    @Test
    fun theFirstEquipEachTurnIsFree() = scenario {
        alice.battlefield(krali, trinket, trinket, ladle, ladle, bear, "Plains")
        settle()
        assertTrue(hasStory(alice))
        alice.activates(ladle, target = bear)
        resolveAll()
        expect { card("Plains").tapped shouldBe false }
        alice.activates(ladle, target = krali)
        resolveAll()
        expect { card("Plains").tapped shouldBe true }
    }

    /** Equip abilities targeting this creature cost {2} less. */
    // CR 601.2f, 602.2b
    @Test
    fun equipCheaperOntoTheBasher() = scenario {
        alice.battlefield(pike, basher, "Mountain")
        alice.activates(pike, target = basher)
        resolveAll()
        expect { basher.power shouldBe 4 }
    }
}
