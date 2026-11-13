package arcanum.engine.sets.one

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.fin.targets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Phyrexia: All Will Be One chunk 15 in play: damage counted from the board (CR 120.3), a sweep that also stops blocking for the
 * opponents of 3 players (CR 509.1a), oil-counter counts (CR 122.1), a once-each-turn trigger for several dying creatures with each
 * opponent losing life (CR 603.4, 119.3), looting (CR 121.1), looking at the top cards (CR 701.20), the "Then if" check after life
 * is gained (CR 608.2c), cost reductions of an activated ability (CR 601.2f) and sorcery-speed sacrifice artifacts (CR 602.5d).
 */
class OneChunk15DamageDrawLifeLookScenariosTest {
    private val oil = CounterKind.Named("oil")
    private val charge = Cards.invent("Charge of the Mice", "{2}{W}", "Instant", """
        Choose one —
        • Charge of the Mice deals damage equal to the number of creatures you control to target creature or planeswalker.
        • Create two 1/1 colorless Phyrexian Mite artifact creature tokens with toxic 1 and "This token can't block." (Players dealt combat damage by them also get a poison counter.)
    """)
    private val burst = Cards.invent("Hazardous Burst", "{3}{R}", "Sorcery",
        "Hazardous Burst deals 1 damage to each creature your opponents control. Creatures your opponents control can't block this turn.")
    private val anointress = Cards.invent("Urabrask's Anointress", "{3}{R}", "Artifact Creature — Phyrexian Wizard",
        "When this creature enters, it deals X damage to any target, where X is the number of permanents you control with oil counters on them.", "4/2")
    private val skullburst = Cards.invent("Basilica Skullburst", "{1}", "Artifact", """
        {1}, Sacrifice this artifact: Draw a card.
        {2}{W}, Sacrifice this artifact: Target creature you control gets +2/+2 and gains flying until end of turn. Draw a card. Activate only as a sorcery.
    """)
    private val glissa = Cards.invent("Glissa Sunbringer", "{1}{B}{G}", "Legendary Creature — Phyrexian Zombie Elf", """
        First strike, deathtouch
        Whenever Glissa Sunbringer deals combat damage to a player, choose one —
        • You draw a card and lose 1 life.
        • Destroy target enchantment.
        • Remove up to three counters from target permanent.
    """, "3/3")
    private val ledger = Cards.invent("Tamiyo's Ledger", "{2}{U}", "Artifact — Book",
        "{5}{U}, {T}: Draw a card. This ability costs {1} less to activate for each other artifact you control.")
    private val theoretician = Cards.invent("Transplant Theoretician", "{3}{U}", "Artifact Creature — Phyrexian Artificer", """
        Whenever this creature or another artifact you control enters, you may draw a card. If you do, discard a card.
        {2}: Put target card from your graveyard on the bottom of your library.
    """, "2/4")
    private val troll = Cards.invent("Oil-Glutton Troll", "{3}{G}{G}", "Creature — Phyrexian Troll Warrior",
        "When this creature enters, you gain 3 life. Then if you control a permanent with an oil counter on it, draw a card.", "3/4")
    private val bearer = Cards.invent("Testimony Bearer", "{3}{B}", "Creature — Phyrexian Warrior",
        "When this creature dies, look at the top three cards of your library. Put one of them into your hand and the rest into your graveyard.", "4/1")
    private val vraan = Cards.invent("Vraan, Executioner Than", "{1}{B}", "Legendary Creature — Phyrexian Vampire",
        "Whenever one or more other creatures you control die, each opponent loses 2 life and you gain 2 life. This ability triggers only once each turn.", "2/2")
    private val sweep = Cards.invent("Pyre Sweep", "{1}{R}", "Sorcery", "Pyre Sweep deals 1 damage to each creature.")
    private val spark = Cards.invent("Single Spark", "{R}", "Instant", "Single Spark deals 1 damage to target creature.")
    private val bear = Cards.invent("Stonefang Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val rat = Cards.invent("Scrap Rat", "{B}", "Creature — Rat", "", "1/1")
    private val ratTwo = Cards.invent("Scrap Rat Two", "{B}", "Creature — Rat", "", "1/1")
    private val ox = Cards.invent("Scrap Ox", "{1}{B}", "Creature — Ox", "", "2/2")
    private val ogre = Cards.invent("Cragmaw Ogre", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val trinket = Cards.invent("Brass Trinket", "{1}", "Artifact", "")
    private val trinketTwo = Cards.invent("Brass Trinket Two", "{1}", "Artifact", "")
    private val aura = Cards.invent("Wispy Veil", "{1}{W}", "Enchantment", "")

    // CR 120.3, 700.2: mode 1 deals damage equal to the number of creatures its controller has (three here)
    @Test
    fun chargeDealsDamageEqualToMyCreatures() = scenario {
        alice.battlefield(bear, rat, ratTwo, "Plains", "Plains", "Plains")
        bob.battlefield(ogre)
        alice.hand(charge)
        alice.casts(charge, target = ogre, modes = listOf(0))
        resolveAll()
        expect { ogre.view.obj?.damage shouldBe 3 }
    }

    // CR 700.2, 111.1, 702.164a: mode 2 creates two Mite tokens with toxic 1 that can't block (CR 509.1b)
    @Test
    fun chargeCreatesTwoMitesThatCantBlock() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(charge)
        alice.casts(charge, modes = listOf(1))
        resolveAll()
        expect {
            assertEquals(2, alice.battlefield.names.count { it == "Phyrexian Mite Token" })
            card("Phyrexian Mite Token") hasKeyword Keyword.Toxic.Kind
        }
    }

    // CR 120.3, 509.1a, 3 players: each opponent's creatures take 1 damage and none of them can block; my own creature takes none
    @Test
    fun burstDamagesBothOpponentsCreaturesAndStopsBlocking() = scenario(players = 3) {
        alice.battlefield(bear, "Mountain", "Mountain", "Mountain", "Mountain")
        bob.battlefield(ogre)
        carol.battlefield(rat)
        alice.hand(burst)
        alice.casts(burst)
        resolveAll()
        expect {
            ogre.view.obj?.damage shouldBe 1
            rat.zone shouldBe Zone.Graveyard
            bear.view.obj?.damage shouldBe 0
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks("Cragmaw Ogre" to "Stonefang Bear") }
    }

    // CR 122.1: two permanents with oil counters (the Trinket and the Bear) make 2 damage to any target, here the player bob
    @Test
    fun anointressCountsPermanentsWithOilCounters() = scenario {
        alice.battlefield(trinket, counters = listOf(oil to 1))
        alice.battlefield(bear, counters = listOf(oil to 3))
        alice.battlefield(trinketTwo, "Mountain", "Mountain", "Mountain", "Mountain")
        bob.battlefield(ogre)
        alice.hand(anointress)
        alice.casts(anointress)
        resolveAll()
        targets("bob")
        expect { bob.life shouldBe 18 }
    }

    // CR 602.5d, 602.2: the second ability works in the main phase: pump, flying and a card drawn after
    @Test
    fun skullburstPumpsAtSorcerySpeed() = scenario {
        alice.battlefield(skullburst, bear, "Plains", "Plains", "Plains")
        alice.library("Forest", "Forest")
        alice.activates(skullburst, ability = 1, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Flying.kind
            skullburst.zone shouldBe Zone.Graveyard
            alice.hand.size shouldBe 1
        }
    }

    // CR 602.5d: in combat the sorcery-speed ability can't be activated, the plain draw ability can (CR 602.2)
    @Test
    fun skullburstPumpCantBeActivatedInCombat() = scenario {
        alice.battlefield(skullburst, bear, "Plains", "Plains", "Plains")
        alice.library("Forest", "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        assertFailsWith<IllegalArgumentException> { alice.activates(skullburst, ability = 1, target = bear) }
        alice.activates(skullburst, ability = 0)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 700.2, 121.1, 119.3: Glissa's first mode draws a card and loses 1 life
    @Test
    fun glissaDrawsAndLosesLife() = scenario {
        alice.battlefield(glissa)
        alice.library("Forest", "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(glissa)
        advanceTo(TurnStep.CombatDamage)
        modes(0)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.life shouldBe 19
            bob.life shouldBe 17
        }
    }

    // CR 122.8: the third mode removes up to three counters from any permanent, here two of the four on the Bear
    @Test
    fun glissaRemovesUpToThreeCounters() = scenario {
        alice.battlefield(glissa)
        bob.battlefield(bear, counters = listOf(CounterKind.PowerToughness(1, 1) to 4))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(glissa)
        advanceTo(TurnStep.CombatDamage)
        modes(2)
        targets("Stonefang Bear")
        if (pending is arcanum.engine.decision.Decision.ChooseNumber) answer(arcanum.engine.decision.Answer.Number(2))
        resolveAll()
        expect { bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2 }
    }

    // CR 601.2f: with two other artifacts the ability costs {1}{1} less: {3}{U} is paid with four lands
    @Test
    fun ledgerCostsLessForOtherArtifacts() = scenario {
        alice.battlefield(ledger, trinket, trinketTwo, "Island", "Island", "Island", "Island")
        alice.library("Forest", "Forest")
        alice.activates(ledger)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 601.2f: the Ledger itself isn't "other": with one other artifact the ability still costs five
    @Test
    fun ledgerDoesNotCountItself() = scenario {
        alice.battlefield(ledger, trinket, "Island", "Island", "Island", "Island")
        alice.library("Forest", "Forest")
        alice.activates(ledger)
        resolveAll()
        expect { alice.hand.size shouldBe 0 }
    }

    // CR 603.2, 121.1, 701.9: another artifact entering lets me loot: draw, then discard
    @Test
    fun theoreticianLoots() = scenario {
        alice.battlefield(theoretician, "Island")
        alice.hand(trinket, "Plains")
        alice.library("Swamp", "Forest")
        alice.casts(trinket)
        resolveAll()
        may(true)
        alice.chooses("Plains")
        resolveAll()
        expect {
            alice.library.size shouldBe 1
            alice.hand has "Swamp"
            alice.graveyard has "Plains"
        }
    }

    // CR 603.2: an artifact an opponent controls doesn't trigger it
    @Test
    fun theoreticianIgnoresOpponentsArtifacts() = scenario {
        alice.battlefield(theoretician)
        bob.battlefield("Island")
        bob.hand(trinket)
        alice.library("Swamp", "Forest")
        turn(number = 2, active = bob)
        bob.casts(trinket)
        resolveAll()
        expect { alice.library.size shouldBe 2 }
    }

    // CR 608.2c, 122.1: with an oil permanent in play the "Then if" is true: 3 life and a card
    @Test
    fun trollDrawsWithAnOilPermanent() = scenario {
        alice.battlefield(trinket, "Forest", "Forest", "Forest", "Forest", "Forest", counters = listOf(oil to 1))
        alice.hand(troll)
        alice.library("Swamp", "Forest")
        alice.casts(troll)
        resolveAll()
        expect {
            alice.life shouldBe 23
            alice.hand.size shouldBe 1
        }
    }

    // CR 608.2c: no permanent with an oil counter: only the life
    @Test
    fun trollDoesNotDrawWithoutOil() = scenario {
        alice.battlefield(trinket, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(troll)
        alice.library("Swamp", "Forest")
        alice.casts(troll)
        resolveAll()
        expect {
            alice.life shouldBe 23
            alice.hand.size shouldBe 0
        }
    }

    // CR 603.10a, 701.20: the dying creature looks at the top three: one into hand, the other two into the graveyard
    @Test
    fun bearerLooksAtTopThree() = scenario {
        alice.battlefield(bearer)
        alice.library("Swamp", "Forest", "Island", "Plains")
        bob.battlefield("Mountain")
        bob.hand(spark)
        turn(number = 2, active = bob)
        bob.casts(spark, target = bearer)
        resolveAll()
        alice.chooses("Island")
        resolveAll()
        expect {
            alice.hand has "Island"
            alice.graveyard has "Swamp"
            alice.graveyard has "Forest"
            alice.library.size shouldBe 1
        }
    }

    // CR 603.2c, 603.4, 119.3, 3 players: two creatures dying together trigger it once: each opponent loses 2, I gain 2;
    // a creature dying later the same turn doesn't trigger it again
    @Test
    fun vraanTriggersOnceEachTurnAgainstEachOpponent() = scenario(players = 3) {
        alice.battlefield(vraan, rat, ratTwo, ox, "Mountain", "Mountain", "Mountain")
        alice.hand(sweep, spark)
        alice.casts(sweep)
        resolveAll()
        expect {
            rat.zone shouldBe Zone.Graveyard
            ratTwo.zone shouldBe Zone.Graveyard
            bob.life shouldBe 18
            carol.life shouldBe 18
            alice.life shouldBe 22
        }
        alice.casts(spark, target = ox)
        resolveAll()
        expect {
            ox.zone shouldBe Zone.Graveyard
            bob.life shouldBe 18
            carol.life shouldBe 18
            alice.life shouldBe 22
        }
    }
}
