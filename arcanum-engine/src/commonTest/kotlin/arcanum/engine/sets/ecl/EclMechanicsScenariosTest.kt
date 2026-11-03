package arcanum.engine.sets.ecl

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Lorwyn Eclipsed's mechanics in play: blight (as a cost, an optional or alternative additional cost, a payment on resolution and an
 * effect), behold (and exile, or several at once), vivid counts and a creature type chosen with the costs. */
class EclMechanicsScenariosTest {
    private val m1m1 = CounterKind.PowerToughness(-1, -1)
    private val barbHurler = Cards.invent("Barb-Hurler", "{2}{R}", "Creature — Goblin Warrior",
        "{1}{R}, {T}, Blight 1: This creature deals 2 damage to each opponent. (To blight 1, put a -1/-1 counter on a creature you control.)", "3/3")
    private val emberJab = Cards.invent("Ember Jab", "{R}", "Sorcery", """
        As an additional cost to cast this spell, you may blight 1. (You may put a -1/-1 counter on a creature you control.)
        Ember Jab deals 2 damage to target creature. It deals 4 damage to that creature instead if this spell's additional cost was paid.
    """)
    private val feralUndoing = Cards.invent("Feral Undoing", "{U}{U}", "Instant", """
        As an additional cost to cast this spell, blight 2 or pay {1}. (To blight 2, put two -1/-1 counters on a creature you control.)
        Counter target spell.
    """)
    private val granny = Cards.invent("Stalecrust Granny", "{2}{R}{R}", "Creature — Goblin Warrior",
        "When this creature enters, you may blight 2. If you do, create two 1/1 black and red Goblin creature tokens. (To blight 2, put two -1/-1 counters on a creature you control.)",
        "4/3")
    private val velsyn = Cards.invent("High Exalted Velsyn", "{2}{B}{G}", "Legendary Creature — Elf Noble", """
        Whenever High Exalted Velsyn or another Elf you control enters, each opponent blights 1. (They each put a -1/-1 counter on a creature they control.)
        Tap three untapped Elves you control: Proliferate. Activate only as a sorcery. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
    """, "4/4")
    private val pyre = Cards.invent("Spirit Pyre", "{3}{R}{R}", "Sorcery", """
        As an additional cost to cast this spell, blight X. X can't be greater than the greatest toughness among creatures you control. (Put X -1/-1 counters on a creature you control.)
        Spirit Pyre deals X damage to each opponent and each creature they control.
    """)
    private val exemplar = Cards.invent("Exemplar of the Hamlet", "{3}{W}", "Creature — Kithkin Knight", """
        Flash
        As an additional cost to cast this spell, behold a Kithkin and exile it. (Exile a Kithkin you control or a Kithkin card from your hand.)
        Other Kithkin you control get +1/+1.
        When this creature leaves the battlefield, return the exiled card to its owner's hand.
    """, "4/5")
    private val hopeful = Cards.invent("Hearthvale Hopeful", "{W}", "Creature — Kithkin Citizen", """
        As an additional cost to cast this spell, behold a Kithkin or pay {2}. (To behold a Kithkin, choose a Kithkin you control or reveal a Kithkin card from your hand.)
        Whenever another creature you control enters, this creature gets +1/+1 until end of turn.
    """, "2/1")
    private val homecoming = Cards.invent("Starlit Homecoming", "{X}{G}", "Sorcery", """
        As an additional cost to cast this spell, you may choose a creature type and behold two creatures of that type.
        Search your library for a creature card with mana value X or less, reveal it, put it into your hand, then shuffle. If this spell's additional cost was paid and the revealed card is the chosen type, put that card onto the battlefield instead of putting it into your hand.
    """)
    private val hamletwarden = Cards.invent("Hamletwarden", "{6}{W}", "Creature — Elemental", """
        Vivid — When this creature enters, create X 1/1 green and white Kithkin creature tokens, where X is the number of colors among permanents you control.
        Tap three untapped creatures you control: This creature gets +3/+0 and gains flying until end of turn.
    """, "3/3")
    private val basher = Cards.invent("Tanglevine Basher", "{6}{G}", "Creature — Giant Berserker", """
        Vivid — This spell costs {1} less to cast for each color among permanents you control.
        Reach, trample
    """, "6/5")
    private val bloomKeeper = Cards.invent("Blossom Keeper", "{1}{G}", "Creature — Elf Druid",
        "Vivid — {T}: For each color among permanents you control, add one mana of that color.", "1/1")
    private val cluckblaze = Cards.invent("Cluckblaze", "{3}{R}", "Creature — Elemental", """
        Double strike
        Vivid — Cluckblaze's power is equal to the number of colors among permanents you control.
    """, "*/4")
    private val kithkinFolk = Cards.invent("Hamlet Smith", "{W}", "Creature — Kithkin", "", "1/1")
    private val elf = Cards.invent("Grove Elf", "{G}", "Creature — Elf", "", "1/1")
    private val elfCard = Cards.invent("Grove Elder", "{2}{G}", "Creature — Elf", "", "3/3")
    private val bear = Cards.invent("Plaza Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val redBear = Cards.invent("Ember Bear", "{1}{R}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Plaza Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val shock = Cards.invent("Spark Jolt", "{R}", "Instant", "Spark Jolt deals 2 damage to any target.")
    private val elfMatron = Cards.invent("Grove Matron", "{1}{G}", "Creature — Elf", "", "3/3")
    private val unmake = Cards.invent("Unmake Ward", "{W}{W}", "Instant", "Exile target creature.")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 701.68a: the creature with the ability may be the one that gets the counter
    @Test
    fun blightAsAnActivationCostCanBlightItself() = scenario {
        alice.battlefield(barbHurler, "Mountain", "Mountain")
        alice.activates(barbHurler, choosing = listOf(barbHurler))
        resolveAll()
        expect { barbHurler.counters(m1m1) shouldBe 1 }
        assertEquals(18, bob.life)
    }

    // CR 701.68b: without a creature to blight, the cost can't be paid
    @Test
    fun blightCantBePaidWithoutACreature() = scenario {
        alice.battlefield("Mountain", "Island", "Island")
        alice.hand(feralUndoing)
        bob.battlefield("Mountain")
        bob.hand(shock)
        turn(active = bob)
        bob.casts(shock, target = "alice")
        bob.passes()
        // the blight option isn't payable, so {1} is the cost: counter with three lands
        alice.casts(feralUndoing, target = shock, option = "Pay")
        resolveAll()
        expect { bob.graveyard has shock }
        assertEquals(20, alice.life)
    }

    // CR 601.2b, 601.2f: the optional additional cost, read by "if this spell's additional cost was paid"
    @Test
    fun optionalBlightMakesTheSpellStronger() = scenario {
        alice.battlefield("Mountain", bear)
        alice.hand(emberJab)
        bob.battlefield(ogre)
        alice.casts(emberJab, target = ogre, kicked = 1, choosing = listOf(bear))
        resolveAll()
        expect {
            bear.counters(m1m1) shouldBe 1
            ogre.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun withoutTheOptionalBlightTheSpellDealsLess() = scenario {
        alice.battlefield("Mountain", bear)
        alice.hand(emberJab)
        bob.battlefield(ogre)
        alice.casts(emberJab, target = ogre, kicked = 0)
        resolveAll()
        expect {
            bear.counters(m1m1) shouldBe 0
            ogre.zone shouldBe Zone.Battlefield
        }
    }

    // CR 118.12, 701.68c: paid on resolution; the tokens come only if it was
    @Test
    fun youMayBlightIfYouDo() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(granny)
        alice.casts(granny)
        resolveAll()
        assertTrue(pending is Decision.May || pending is Decision.ChooseObjects, "offered to blight: $pending")
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses(granny)
        resolveAll()
        expect {
            granny.counters(m1m1) shouldBe 2
            alice.battlefield has "Goblin Token"
        }
    }

    // CR 701.68a: each opponent chooses one of their own creatures
    @Test
    fun eachOpponentBlights() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Forest")
        alice.hand(velsyn)
        bob.battlefield(ogre, redBear)
        alice.casts(velsyn)
        resolveAll()
        expect { (pending as? Decision.ChooseObjects)?.player shouldBe bob.id }
        bob.chooses(ogre)
        resolveAll()
        expect {
            ogre.counters(m1m1) shouldBe 1
            redBear.counters(m1m1) shouldBe 0
        }
    }

    // CR 107.3a: X can't be greater than the greatest toughness among your creatures
    @Test
    fun blightXIsLimitedByToughness() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", ogre)
        alice.hand(pyre)
        bob.battlefield(bear)
        assertFailsWith<IllegalArgumentException> { alice.casts(pyre, x = 4) }
    }

    @Test
    fun blightXDealsThatMuch() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", ogre)
        alice.hand(pyre)
        bob.battlefield(bear)
        alice.casts(pyre, x = 2, choosing = listOf(ogre))
        resolveAll()
        expect {
            ogre.counters(m1m1) shouldBe 2
            bear.zone shouldBe Zone.Graveyard
        }
        assertEquals(18, bob.life)
    }

    // CR 701.4a, 607.2a: the Kithkin card exiled from the hand comes back when the champion leaves
    @Test
    fun beholdAndExileReturnsTheCard() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(exemplar, kithkinFolk, unmake)
        alice.casts(exemplar, option = "Exile from Hand", choosing = listOf(kithkinFolk), paying = listOf("Plains", "Plains", "Plains", "Plains"))
        expect { alice.exile has kithkinFolk }
        resolveAll()
        expect { alice.battlefield has exemplar }
        // it leaves: the exiled card returns to the hand
        alice.casts(unmake, target = exemplar)
        resolveAll()
        expect { alice.hand has kithkinFolk }
    }

    // CR 701.4a: a Kithkin card revealed from the hand pays instead of {2}
    @Test
    fun beholdOrPay() = scenario {
        alice.battlefield("Plains")
        alice.hand(hopeful, kithkinFolk)
        alice.casts(hopeful, option = "Behold", choosing = listOf(kithkinFolk))
        resolveAll()
        expect {
            alice.battlefield has hopeful
            alice.hand has kithkinFolk
        }
    }

    // CR 601.2f, 701.4a: a creature type chosen with the costs; the found card of that type goes onto the battlefield
    @Test
    fun aCreatureTypeChosenWithTheCosts() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", elf)
        alice.hand(homecoming, elfCard)
        alice.library(bear, elfMatron)
        val outcome = alice.casts(homecoming, x = 3, kicked = 1)
        assertTrue(pending is Decision.ChooseSubtype, "a creature type is asked: $pending ($outcome)")
        alice.answers(Answer.SubtypeChoice(arcanum.api.Subtype("Elf")))
        if (pending is Decision.ChooseObjects) alice.chooses(elf, elfCard)
        settlePayment()
        resolveAll()
        expect { (pending is Decision.ChooseObjects) shouldBe true }
        alice.chooses(elfMatron)
        resolveAll()
        expect { alice.battlefield has elfMatron }
    }

    // CR 105.4: one for each colour among permanents you control
    @Test
    fun vividCountsColors() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", bear, redBear)
        alice.hand(hamletwarden)
        alice.casts(hamletwarden)
        resolveAll()
        // white (Hamletwarden), green and red
        expect { alice.battlefield.names.count { it == "Kithkin Token" } shouldBe 3 }
    }

    // CR 601.2f: {1} less for each colour
    @Test
    fun vividCostReduction() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", bear, redBear)
        alice.hand(basher)
        alice.casts(basher)
        resolveAll()
        expect { alice.battlefield has basher }
    }

    // CR 106.1: one mana of each colour; CR 604.3: the power of a characteristic-defining ability
    @Test
    fun vividManaAndPower() = scenario {
        alice.battlefield(bloomKeeper, redBear, cluckblaze)
        alice.activates(bloomKeeper)
        expect {
            alice.pool.map { it.color }.toSet() shouldBe setOf(arcanum.api.Color.Green, arcanum.api.Color.Red)
            cluckblaze.power shouldBe 2
        }
    }

    private fun Scenario.settlePayment() {
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
    }
}
