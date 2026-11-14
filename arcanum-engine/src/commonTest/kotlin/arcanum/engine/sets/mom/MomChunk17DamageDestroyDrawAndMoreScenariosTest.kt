package arcanum.engine.sets.mom

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.resolveAll
import arcanum.engine.sets.rtr.may
import arcanum.engine.sets.rtr.payIfAsked
import arcanum.engine.sets.rtr.targetsIfAsked
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * MOM chunk 17 played: the invented names carry the oracle text of Mirrodin Avenged, Tribute to the World Tree, Protocol Knight,
 * Surrak and Goreclaw, Errant and Giada, Marshal of Zhalfir and Terror of Towashi.
 */
class MomChunk17DamageDestroyDrawAndMoreScenariosTest {
    private val avenger = Cards.invent("Ashen Avenger", "{B}", "Instant", """
        Destroy target creature that was dealt damage this turn.
        Draw a card.
    """)
    private val spark = Cards.invent("Hexed Spark", "{R}", "Instant", "Hexed Spark deals 1 damage to any target.")
    private val tribute = Cards.invent("Grove Tribute", "{G}{G}{G}", "Enchantment",
        "Whenever a creature you control enters, draw a card if its power is 3 or greater. Otherwise, put two +1/+1 counters on it.")
    private val squire = Cards.invent("Protocol Squire", "{3}{U}", "Creature — Human Knight",
        "When this creature enters, tap target creature an opponent controls. Put a stun counter on that creature if you control another Knight. (If a permanent with a stun counter would become untapped, remove one from it instead.)", "3/4")
    private val boulder = Cards.invent("Boulder and Maw", "{4}{G}{G}", "Legendary Creature — Human Bear", """
        Trample
        Other creatures you control have trample.
        Whenever another nontoken creature you control enters, put a +1/+1 counter on it. It gains haste until end of turn.
    """, "6/5")
    private val seer = Cards.invent("Seer and Guide", "{1}{W}{U}", "Legendary Creature — Human Angel", """
        Flash
        Flying
        You may look at the top card of your library any time.
        You may cast spells with flash or flying from the top of your library.
    """, "2/3")
    private val marshal = Cards.invent("Marshal of Dunmere", "{W}{U}", "Creature — Human Knight", """
        Other Knights you control get +1/+1.
        {W}{U}, {T}: Tap another target creature.
    """, "2/2")
    private val terror = Cards.invent("Terror of Dunmere", "{2}{B}{B}", "Creature — Phyrexian Ogre", """
        Deathtouch
        Whenever this creature attacks, you may pay {3}{B}. When you do, return target creature card from your graveyard to the battlefield. It's a Phyrexian in addition to its other types.
    """, "4/3")
    private val maker = Cards.invent("Soldier Maker", "{W}", "Sorcery", "Create a 1/1 white Soldier creature token.")
    private val brute = Cards.invent("Stoneback Brute", "{2}{G}", "Creature — Beast", "", "4/4")
    private val wall = Cards.invent("Mossy Wall", "{1}{G}", "Creature — Plant", "", "0/4")
    private val bear = Cards.invent("Mudback Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val mite = Cards.invent("Tiny Mite", "{G}", "Creature — Insect", "", "1/1")
    private val knight = Cards.invent("Dunmere Knight", "{1}{W}", "Creature — Human Knight", "", "3/3")
    private val rivalKnight = Cards.invent("Rival Knight", "{1}{W}", "Creature — Human Knight", "", "3/3")
    private val quickScout = Cards.invent("Quickfoot Scout", "{G}", "Creature — Scout", "Flash", "1/1")
    private val skyScout = Cards.invent("Skyfoot Scout", "{W}", "Creature — Bird", "Flying", "1/1")
    private val plainScout = Cards.invent("Plainfoot Scout", "{W}", "Creature — Scout", "", "1/1")

    // CR 120.6: dealt damage by any source this turn, CR 701.8
    @Test
    fun mirrodinAvengedDestroysADamagedCreatureAndDraws() = scenario {
        alice.battlefield("Swamp", "Mountain")
        alice.hand(spark, avenger)
        alice.library("Plains")
        bob.battlefield(brute, wall)
        alice.casts(spark, target = brute)
        resolveAll()
        alice.casts(avenger, target = brute)
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Graveyard
            wall.zone shouldBe Zone.Battlefield
            alice.hand has "Plains"
        }
    }

    // CR 120.6: an undamaged creature is no legal target
    @Test
    fun mirrodinAvengedNeedsADamagedTarget() = scenario {
        alice.battlefield("Swamp", "Mountain")
        alice.hand(spark, avenger)
        bob.battlefield(brute, wall)
        alice.casts(spark, target = brute)
        resolveAll()
        assertFailsWith<IllegalArgumentException> { alice.casts(avenger, target = wall) }
    }

    // CR 603.2, 608.2h: the power of the creature that entered decides, not the enchantment's
    @Test
    fun tributeDrawsForBigCreaturesAndGrowsSmallOnes() = scenario {
        alice.battlefield(tribute, "Forest", "Forest", "Forest", "Forest")
        alice.hand(brute, mite)
        alice.library("Plains")
        alice.casts(brute)
        resolveAll()
        expect {
            alice.hand has "Plains"
            brute.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
        alice.casts(mite)
        resolveAll()
        expect { mite.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2 }
    }

    // CR 122.1c: the stun counter goes on the tapped creature, not on the Knight that entered
    @Test
    fun protocolSquireStunsWithAnotherKnight() = scenario {
        alice.battlefield(knight, "Island", "Island", "Island", "Island")
        alice.hand(squire)
        bob.battlefield(brute)
        alice.casts(squire)
        resolveAll()
        targetsIfAsked(brute)
        resolveAll()
        expect {
            brute.tapped shouldBe true
            brute.counters(CounterKind.Stun) shouldBe 1
            squire.counters(CounterKind.Stun) shouldBe 0
        }
    }

    // CR 608.2c: no other Knight, so only the tap
    @Test
    fun protocolSquireWithoutAnotherKnightOnlyTaps() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(squire)
        bob.battlefield(brute)
        alice.casts(squire)
        resolveAll()
        targetsIfAsked(brute)
        resolveAll()
        expect {
            brute.tapped shouldBe true
            brute.counters(CounterKind.Stun) shouldBe 0
        }
    }

    // CR 613.1f (trample granted to others), 603.6a (counter and haste for the entering nontoken creature only)
    @Test
    fun boulderAndMawGrowsNontokenCreaturesOnly() = scenario {
        alice.battlefield(boulder, "Forest", "Forest", "Plains")
        alice.hand(bear, maker)
        bob.battlefield(wall)
        alice.casts(bear)
        resolveAll()
        alice.casts(maker)
        resolveAll()
        expect {
            bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            bear hasKeyword Keyword.Haste.kind
            bear hasKeyword Keyword.Trample.kind
            "Soldier Token".counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            "Soldier Token" hasKeyword Keyword.Trample.kind
            wall lacksKeyword Keyword.Trample.kind
        }
    }

    // CR 401.5 (look at the top card), 601.3 (cast from the library): only spells with flash or flying
    @Test
    fun seerAndGuideCastsFlashAndFlyingSpellsFromTheTop() = scenario {
        alice.battlefield(seer, "Forest", "Plains", "Plains")
        alice.library(quickScout, skyScout)
        expect { assertTrue(view.players[alice.id]!!.mechanics.any { it.mechanic is Mechanic.MayLookAt }) }
        alice.casts(quickScout)
        resolveAll()
        expect { alice.battlefield has quickScout }
        alice.casts(skyScout)
        resolveAll()
        expect { alice.battlefield has skyScout }
    }

    @Test
    fun seerAndGuideCannotCastAPlainSpellFromTheTop() = scenario {
        alice.battlefield(seer, "Plains", "Plains")
        alice.library(plainScout)
        assertFailsWith<IllegalArgumentException> { alice.casts(plainScout) }
    }

    // CR 613.4c: the lord gives +1/+1 to other Knights you control only; its tap ability needs another target
    @Test
    fun marshalPumpsOtherKnightsAndTapsAnotherCreature() = scenario {
        alice.battlefield(marshal, knight, bear, "Plains", "Island")
        bob.battlefield(rivalKnight)
        expect {
            knight.power shouldBe 4
            bear.power shouldBe 2
            marshal.power shouldBe 2
            rivalKnight.power shouldBe 3
        }
        alice.activates(marshal, target = rivalKnight)
        resolveAll()
        expect { rivalKnight.tapped shouldBe true }
    }

    // CR 603.12 (reflexive trigger), 205.1b (the returned creature gains a subtype)
    @Test
    fun terrorOfDunmereReturnsAPhyrexianCreature() = scenario {
        alice.battlefield(terror, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(terror)
        resolveAll()
        may(true)
        payIfAsked()
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            alice.battlefield has bear
            assertTrue(bear.view.characteristics!!.hasSubtype(Subtype("Phyrexian")))
            assertTrue(bear.view.characteristics!!.hasSubtype(Subtype("Bear")))
        }
    }
}
