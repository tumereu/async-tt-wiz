package arcanum.engine.sets.rvr

import arcanum.api.Keyword
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.fin.targetStackItem
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ravnica Remastered chunk 12 in play: a cost increase for spells that target one creature, a charm's three modes, a
 * counterspell that returns the spell to hand, one that counters abilities, a split card copying a spell and dealing X, a
 * Guildmage copying a cheap instant, and an uncounterable dragon that draws and pings. The cards whose wording other sets
 * already play (attack costs, can't be blocked, can only attack alone, uncounterable spells, cost reducers) are parser tests only. */
class RvrChunk12CountersCopiesCostsScenariosTest {
    private val sphinx = Cards.invent("Rvr12 Prahv Sphinx", "{W}{W}{U}{U}", "Creature — Sphinx", """
        Flying, vigilance
        Spells your opponents cast that target this creature cost {2} more to cast.
    """, "4/3")
    private val charm = Cards.invent("Rvr12 Izzet Charm", "{U}{R}", "Instant", """
        Choose one —
        • Counter target noncreature spell unless its controller pays {2}.
        • Rvr12 Izzet Charm deals 2 damage to target creature.
        • Draw two cards, then discard two cards.
    """)
    private val remand = Cards.invent("Rvr12 Remand", "{1}{U}", "Instant", """
        Counter target spell. If that spell is countered this way, put it into its owner's hand instead of into that player's graveyard.
        Draw a card.
    """)
    private val voidslime = Cards.invent("Rvr12 Voidslime", "{G}{U}{U}", "Instant",
        "Counter target spell, activated ability, or triggered ability. (Mana abilities can't be targeted.)")
    private val split = Cards.inventFaces("Split",
        face("Rvr12 Expansion", "{U/R}{U/R}", "Instant", "Copy target instant or sorcery spell with mana value 4 or less. You may choose new targets for the copy.", null, null, null),
        face("Rvr12 Explosion", "{X}{U}{U}{R}{R}", "Instant", "Rvr12 Explosion deals X damage to any target. Target player draws X cards.", null, null, null))
    private val guildmage = Cards.invent("Rvr12 Guildmage", "{U/R}{U/R}", "Creature — Human Wizard", """
        ({U/R} can be paid with either {U} or {R}.)
        {2}{U}: Copy target instant spell you control with mana value 2 or less. You may choose new targets for the copy.
        {2}{R}: Copy target sorcery spell you control with mana value 2 or less. You may choose new targets for the copy.
    """, "2/2")
    private val niv = Cards.invent("Rvr12 Niv-Mizzet", "{U}{U}{U}{R}{R}{R}", "Legendary Creature — Dragon Wizard", """
        This spell can't be countered.
        Flying
        Whenever you draw a card, Rvr12 Niv-Mizzet deals 1 damage to any target.
        Whenever a player casts an instant or sorcery spell, you draw a card.
    """, "5/5")
    private val pinger = Cards.invent("Rvr12 Pinger", "{1}", "Creature — Construct", "{T}: Rvr12 Pinger deals 1 damage to any target.", "1/1")

    private val spark = Cards.invent("Rvr12 Spark", "{R}", "Instant", "Rvr12 Spark deals 2 damage to any target.")
    private val twinge = Cards.invent("Rvr12 Twinge", "{R}", "Sorcery", "Rvr12 Twinge deals 1 damage to any target.")
    private val bear = Cards.invent("Rvr12 Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Rvr12 Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")

    /** CR 601.2f: the extra {2} applies only to spells that target the Sphinx, not to other spells of an opponent. */
    @Test
    fun spellsTargetingTheSphinxCostTwoMore() = scenario {
        alice.battlefield(sphinx)
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(spark)
        turn(active = bob)
        bob.casts(spark, target = sphinx)
        resolveAll()
        expect {
            (0..2).count { card("Mountain", it).tapped } shouldBe 3
            alice.life shouldBe 20
        }
    }

    @Test
    fun otherSpellsOfOpponentsCostNormally() = scenario {
        alice.battlefield(sphinx)
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(spark)
        turn(active = bob)
        bob.casts(spark, target = "alice")
        resolveAll()
        expect {
            (0..2).count { card("Mountain", it).tapped } shouldBe 1
            alice.life shouldBe 18
        }
    }

    /** CR 700.2, 118.12: mode one counters a noncreature spell unless its controller pays {2}. */
    @Test
    fun charmCountersAnUnpaidNoncreatureSpell() = scenario {
        bob.battlefield("Mountain")
        bob.hand(spark)
        alice.battlefield("Island", "Mountain")
        alice.hand(charm)
        turn(active = bob)
        bob.casts(spark, target = "alice")
        bob.passes()
        alice.casts(charm, modes = listOf(0), target = spark)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.graveyard has spark
            alice.life shouldBe 20
        }
    }

    @Test
    fun charmLetsAPayingControllerKeepTheSpell() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(spark)
        alice.battlefield("Island", "Mountain")
        alice.hand(charm)
        turn(active = bob)
        bob.casts(spark, target = "alice", paying = listOf("Mountain"))
        bob.passes()
        alice.casts(charm, modes = listOf(0), target = spark)
        resolveAll()
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect { alice.life shouldBe 18 }
    }

    /** CR 700.2: the same card, mode two, kills a creature with toughness 2. */
    @Test
    fun charmDealsTwoToACreature() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand(charm)
        bob.battlefield(bear)
        alice.casts(charm, modes = listOf(1), target = bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun charmLoots() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand(charm, bear, ogre)
        alice.library(spark, twinge)
        alice.casts(charm, modes = listOf(2))
        resolveAll()
        alice.chooses(bear, ogre)
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.graveyard has ogre
            alice.hand.names shouldBe listOf(spark, twinge)
        }
    }

    /** CR 701.6a, 614.1: the countered spell goes to its owner's hand, and Remand's controller draws. */
    @Test
    fun remandReturnsTheSpellToItsOwnersHand() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        alice.battlefield("Island", "Island")
        alice.hand(remand)
        alice.library(bear)
        turn(active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(remand, target = ogre)
        resolveAll()
        expect {
            bob.hand has ogre
            bob.graveyard lacks ogre
            alice.hand has bear
        }
    }

    /** CR 701.6a, 112.1: an activated ability on the Stack is a legal target and doesn't resolve. */
    @Test
    fun voidslimeCountersAnActivatedAbility() = scenario {
        alice.battlefield("Forest", "Island", "Island")
        alice.hand(voidslime)
        bob.battlefield(pinger)
        turn(active = bob)
        bob.activates(pinger, target = "alice")
        bob.passes()
        alice.casts(voidslime)
        targetStackItem()
        resolveAll()
        expect { alice.life shouldBe 20 }
    }

    @Test
    fun voidslimeCountersASpell() = scenario {
        alice.battlefield("Forest", "Island", "Island")
        alice.hand(voidslime)
        bob.battlefield("Mountain")
        bob.hand(spark)
        turn(active = bob)
        bob.casts(spark, target = "alice")
        bob.passes()
        alice.casts(voidslime)
        targetStackItem()
        resolveAll()
        expect {
            alice.life shouldBe 20
            bob.graveyard has spark
        }
    }

    /** CR 709.3, 707.10c: the Expansion half copies a cheap instant; the copy deals its damage too. */
    @Test
    fun expansionCopiesAnInstant() = scenario(players = 3) {
        alice.battlefield("Island", "Mountain", "Mountain")
        alice.hand(split, spark)
        alice.casts(spark, target = "bob", paying = listOf("Mountain#0"))
        alice.casts(split, way = CastWay(face = 0), target = spark)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(false))
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    /** CR 107.3a, 601.2b: the Explosion half announces X; it damages and its target player draws that many. */
    @Test
    fun explosionDealsXAndDrawsX() = scenario {
        alice.battlefield("Island", "Island", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(split)
        bob.library("Island", "Island", "Island", "Island")
        alice.casts(split, way = CastWay(face = 1), x = 2, targets = listOf("bob", "bob"))
        resolveAll()
        expect {
            bob.life shouldBe 18
            bob.hand.size shouldBe 2
        }
    }

    /** CR 707.10: the Guildmage copies an instant of mana value 2 or less, not a sorcery's activation. */
    @Test
    fun guildmageCopiesACheapInstant() = scenario {
        alice.battlefield(guildmage, "Mountain", "Mountain", "Mountain", "Island", "Mountain")
        alice.hand(spark)
        alice.casts(spark, target = "bob", paying = listOf("Mountain#0"))
        alice.activates(guildmage, ability = 0, target = spark)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(false))
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    /** CR 101.2: Niv-Mizzet resolves through a counterspell, because its own text says it can't be countered. */
    @Test
    fun nivMizzetResolvesThroughACounterspell() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain", "Mountain", "Mountain")
        alice.hand(niv)
        bob.battlefield("Island", "Island")
        bob.hand(remand)
        bob.library("Island", "Island")
        alice.casts(niv)
        alice.passes()
        bob.casts(remand, target = niv)
        resolveAll()
        expect { alice.battlefield has niv }
    }

    /** CR 603.2: an opponent's instant draws Niv-Mizzet's controller a card, and that draw pings an any-target (CR 121.2). */
    @Test
    fun nivMizzetDrawsOnAnyInstantAndPingsOnEachDraw() = scenario {
        alice.battlefield(niv)
        alice.library("Island", "Island", "Island")
        bob.battlefield("Mountain")
        bob.hand(spark)
        turn(active = bob)
        bob.casts(spark, target = "bob")
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(arcanum.engine.state.TargetRef.Player(bob.id))))
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            bob.life shouldBe 17
        }
    }
}
