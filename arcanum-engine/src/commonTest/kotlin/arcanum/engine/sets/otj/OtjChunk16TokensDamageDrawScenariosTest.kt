package arcanum.engine.sets.otj

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

/** OTJ chunk 16: token batches, extra triggers, "another spell this turn", conditional flash, a granted copy ability, hexproof on your turn, tapped triggers, looking at the top cards. */
class OtjChunk16TokensDamageDrawScenariosTest {
    private val duchess = Cards.invent("Duchess Valmar Greywater", "{2}{W}{B}", "Legendary Creature — Vampire Noble", """
        Whenever one or more tokens you control enter, create a 1/1 black Vampire Rogue creature token with lifelink. This ability triggers only once each turn.
        {1}{B}, Sacrifice another creature or artifact: Draw a card.
    """, "3/4")
    private val chorus = Cards.invent("Rabbit Chorus", "{2}{W}", "Sorcery", "Create three 1/1 white Rabbit creature tokens.")
    private val loneRabbit = Cards.invent("Lone Rabbit", "{W}", "Sorcery", "Create a 1/1 white Rabbit creature token.")
    private val conjurer = Cards.invent("Stormscale Conjurer", "{2}{R}", "Creature — Human Warlock", "Whenever this creature attacks, create a 3/1 red Dinosaur creature token if you control a creature with power 4 or greater.", "3/3")
    private val marta = Cards.invent("Marta Joins Forces", "{1}{R}{G}{W}", "Legendary Enchantment", """
        When Marta Joins Forces enters, it deals 5 damage to target creature or planeswalker an opponent controls.
        If a triggered ability of a legendary creature you control triggers, that ability triggers an additional time.
    """)
    private val paleLegend = Cards.invent("Pale Legend", "{1}{W}", "Legendary Creature — Human Noble", "When this creature enters, you gain 1 life.", "1/1")
    private val duneColt = Cards.invent("Dune Colt", "{1}{W}", "Creature — Horse Mount", "When this creature enters, you gain 1 life.", "2/2")
    private val cadence = Cards.invent("Quick Cadence", "{U}{R}", "Instant", "Quick Cadence deals 2 damage to any target. If you've cast another spell this turn, draw a card.")
    private val quiet = Cards.invent("Quiet Cantrip", "{U}", "Instant", "You gain 1 life.")
    private val loud = Cards.invent("Loud Cantrip", "{R}", "Instant", "You gain 1 life.")
    private val calm = Cards.invent("Calm Cantrip", "{U}", "Instant", "You gain 1 life.")
    private val bovine = Cards.invent("Bovine Remedy", "{1}{W}", "Instant", "Destroy target artifact or creature. Its controller creates a 2/2 white Ox creature token.")
    private val pilferer = Cards.invent("Vault Pilferer", "{2}{B}", "Creature — Human Rogue", "When this creature enters, target player draws a card and loses 1 life.", "3/1")
    private val sandwurm = Cards.invent("Titanic Sandwurm", "{2}{G}{G}", "Creature — Wurm", """
        Titanic Sandwurm has flash as long as you control a Desert.
        Trample
        {1}{G}, Exile this card from your graveyard: Search your library for a Desert card, put it onto the battlefield tapped, then shuffle.
    """, "6/5")
    private val mesa = Cards.invent("Dusty Mesa", null, "Land — Desert", "{T}: Add {G}.")
    private val gerda = Cards.invent("Gerda, Mirror of Dunes", "{R}{G}{W}", "Legendary Creature — Human Shaman", """
        Haste
        Nontoken creatures you control have "{T}: Create a token that's a copy of target token you control that entered this turn."
    """, "3/3")
    private val mirabel = Cards.invent("Mirabel, Herd Tender", "{G}{W}", "Legendary Creature — Human Druid", """
        During your turn, Mounts and Vehicles you control have hexproof.
        Whenever a Mount or Vehicle you control attacks, put a +1/+1 counter on it.
    """, "3/2")
    private val zap = Cards.invent("Bolt Zap", "{R}", "Instant", "Bolt Zap deals 1 damage to target creature.")
    private val wynn = Cards.invent("Wynn Duke, Hill Hero", "{1}{G}{W}", "Legendary Creature — Human Ranger", """
        Vigilance
        Whenever Wynn Duke becomes tapped, you gain 1 life and draw a card.
    """, "4/2")
    private val tapper = Cards.invent("Rune Tap", "{U}", "Instant", "Tap target creature.")
    private val malcolm = Cards.invent("Malcolm, the Ears", "{U}{R}", "Legendary Creature — Siren Pirate", """
        Flying, haste
        Whenever you cast your second spell each turn, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
    """, "2/2")
    private val pathfinder = Cards.invent("Frontier Pathfinder", "{1}{W}", "Creature — Human Scout", "When this creature enters, look at the top five cards of your library. You may reveal a Mount creature card or a Plains card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.", "2/1")
    private val ox = Cards.invent("Steady Ox", "{G}", "Creature — Ox", "", "1/1")
    private val bear = Cards.invent("Reedmarsh Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val giant = Cards.invent("Hill Elder", "{3}{G}", "Creature — Giant", "", "4/4")

    // CR 603.2c, 603.2h: three tokens entering at once trigger it once; its own token entering doesn't trigger it again this turn
    @Test
    fun aBatchOfTokensMakesOneVampire() = scenario {
        alice.battlefield(duchess, "Plains", "Plains", "Plains")
        alice.hand(chorus)
        alice.casts(chorus)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Rabbit Token" } shouldBe 3
            alice.battlefield.names.count { it == "Vampire Rogue Token" } shouldBe 1
            card("Vampire Rogue Token") hasKeyword Keyword.Lifelink.kind
        }
    }

    // CR 603.2h: a second batch the same turn does nothing; only the controller's tokens count (CR 603.2c)
    @Test
    fun theVampireComesOnlyOncePerTurnAndForOwnTokensOnly() = scenario {
        alice.battlefield(duchess, "Plains", "Plains", "Plains")
        alice.hand(chorus, loneRabbit)
        alice.casts(chorus)
        resolveAll()
        alice.casts(loneRabbit)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Vampire Rogue Token" } shouldBe 1 }
    }

    @Test
    fun anOpponentsTokenDoesNotTriggerIt() = scenario {
        alice.battlefield(duchess)
        bob.battlefield("Plains")
        bob.hand(loneRabbit)
        turn(number = 1, active = bob)
        bob.casts(loneRabbit)
        resolveAll()
        expect {
            bob.battlefield has "Rabbit Token"
            alice.battlefield lacks "Vampire Rogue Token"
        }
    }

    // CR 118.12 (sacrifice as a cost), 121.1
    @Test
    fun sacrificeAnotherCreatureToDraw() = scenario {
        alice.battlefield(duchess, ox, "Swamp", "Swamp")
        alice.library("Island")
        alice.activates(duchess, choosing = listOf(ox))
        resolveAll()
        expect {
            alice.graveyard has ox
            alice.hand has "Island"
        }
    }

    // CR 508.1, 603.4 (checked on resolution of the "if"): the Dinosaur only with a creature of power 4 or more
    @Test
    fun dinosaurComesWithABigCreature() = scenario {
        alice.battlefield(conjurer, giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(conjurer)
        resolveAll()
        expect { alice.battlefield has "Dinosaur Token" }
    }

    @Test
    fun noDinosaurWithoutABigCreature() = scenario {
        alice.battlefield(conjurer, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(conjurer)
        resolveAll()
        expect { alice.battlefield lacks "Dinosaur Token" }
    }

    // CR 603.2d: a legendary creature's enters trigger happens twice; another creature's once
    @Test
    fun legendaryCreatureTriggersTwice() = scenario {
        alice.battlefield(marta, "Plains", "Plains")
        alice.hand(paleLegend)
        alice.casts(paleLegend)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    @Test
    fun otherCreatureTriggersOnce() = scenario {
        alice.battlefield(marta, "Plains", "Plains")
        alice.hand(duneColt)
        alice.casts(duneColt)
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    // CR 120.3: 5 damage kills a creature of an opponent (here the third player's)
    @Test
    fun martaDamagesAnOpponentsCreature() = scenario(players = 3) {
        alice.battlefield("Mountain", "Forest", "Plains", "Plains")
        carol.battlefield(giant)
        alice.hand(marta)
        alice.casts(marta)
        resolveAll()
        if (pending is Decision.Targets) alice.answers(arcanum.engine.decision.Answer.Targets(listOf(target(giant))))
        resolveAll()
        expect { carol.graveyard has giant }
    }

    // CR 601.2i, 117.1: "another spell this turn" counts only a different spell cast this turn
    @Test
    fun cadenceAloneDrawsNothing() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.library("Forest", "Forest")
        alice.hand(cadence)
        alice.casts(cadence, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.hand.size shouldBe 0
        }
    }

    @Test
    fun cadenceAfterAnotherSpellDraws() = scenario {
        alice.battlefield("Island", "Island", "Mountain")
        alice.library("Forest", "Forest")
        alice.hand(quiet, cadence)
        alice.casts(quiet)
        resolveAll()
        alice.casts(cadence, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.hand has "Forest"
        }
    }

    // CR 701.16a, 603.2: only the second spell of the turn investigates
    @Test
    fun malcolmInvestigatesOnTheSecondSpellOnly() = scenario {
        alice.battlefield(malcolm, "Island", "Mountain", "Island")
        alice.hand(quiet, loud, calm)
        alice.casts(quiet)
        resolveAll()
        expect { alice.battlefield lacks "Clue Token" }
        alice.casts(loud)
        resolveAll()
        expect { alice.battlefield has "Clue Token" }
        alice.casts(calm)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Clue Token" } shouldBe 1 }
    }

    // CR 111.2, 701.8a: the destroyed creature's controller (not the caster) gets the Ox
    @Test
    fun theControllerGetsTheOx() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains")
        carol.battlefield(bear)
        alice.hand(bovine)
        alice.casts(bovine, target = bear)
        resolveAll()
        expect {
            carol.graveyard has bear
            carol.battlefield has "Ox Token"
            alice.battlefield lacks "Ox Token"
            card("Ox Token").power shouldBe 2
            card("Ox Token").toughness shouldBe 2
        }
    }

    // CR 121.1, 119.3: a chosen player draws and loses life
    @Test
    fun targetPlayerDrawsAndLoses() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        bob.library("Island")
        alice.hand(pilferer)
        alice.casts(pilferer)
        resolveAll()
        if (pending is Decision.Targets) alice.answers(arcanum.engine.decision.Answer.Targets(listOf(target("bob"))))
        resolveAll()
        expect {
            bob.hand has "Island"
            bob.life shouldBe 19
        }
    }

    // CR 601.3b, 604.2: flash only while a Desert is controlled
    @Test
    fun sandwurmHasFlashWithADesert() = scenario {
        turn(number = 1, active = bob, step = TurnStep.Upkeep)
        alice.battlefield("Forest", "Forest", "Forest", "Forest", mesa)
        alice.hand(sandwurm)
        bob.passes()
        alice.casts(sandwurm)
        resolveAll()
        expect { alice.battlefield has sandwurm }
    }

    @Test
    fun sandwurmHasNoFlashWithoutADesert() = scenario {
        turn(number = 1, active = bob, step = TurnStep.Upkeep)
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(sandwurm)
        bob.passes()
        assertFailsWith<IllegalArgumentException> { alice.casts(sandwurm) }
    }

    // CR 602.2, 701.23, 614.1c: from the graveyard, fetch a Desert tapped
    @Test
    fun sandwurmFetchesADesertFromTheGraveyard() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.graveyard(sandwurm)
        alice.library("Island", mesa)
        alice.activates(sandwurm)
        resolveAll()
        alice.chooses(mesa)
        resolveAll()
        expect {
            alice.battlefield has mesa
            mesa.tapped shouldBe true
            alice.graveyard lacks sandwurm
        }
    }

    // CR 613.1f, 111.10, 603.2: a nontoken creature taps to copy a token that entered this turn; tokens don't get the ability
    @Test
    fun nontokenCreatureCopiesAFreshToken() = scenario {
        alice.battlefield(gerda, "Plains")
        alice.hand(loneRabbit)
        alice.casts(loneRabbit)
        resolveAll()
        alice.activates(gerda, target = "Rabbit Token")
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Rabbit Token" } shouldBe 2
            gerda.tapped shouldBe true
        }
        assertFails { alice.activates("Rabbit Token") }
    }

    // CR 611.3a, 702.11b: hexproof during your turn only
    @Test
    fun mountIsHexproofOnItsControllersTurn() = scenario {
        alice.battlefield(mirabel, duneColt)
        bob.battlefield("Mountain")
        bob.hand(zap)
        turn(number = 1, active = alice, priority = bob)
        assertFailsWith<IllegalArgumentException> { bob.casts(zap, target = duneColt) }
    }

    @Test
    fun mountIsNotHexproofOnTheOpponentsTurn() = scenario {
        alice.battlefield(mirabel, duneColt)
        bob.battlefield("Mountain")
        bob.hand(zap)
        turn(number = 1, active = bob)
        bob.casts(zap, target = duneColt)
        resolveAll()
        expect { duneColt.view.obj?.damage shouldBe 1 }
    }

    // CR 508.1, 122.1: only the attacking Mount gets the counter
    @Test
    fun attackingMountGetsACounter() = scenario {
        alice.battlefield(mirabel, duneColt, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(duneColt, bear)
        resolveAll()
        expect {
            duneColt.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 701.26b, 502.3, 702.20b: tapping triggers it; attacking with vigilance doesn't tap
    @Test
    fun becomingTappedGainsLifeAndDraws() = scenario {
        alice.battlefield(wynn, "Island")
        alice.library("Forest")
        alice.hand(tapper)
        alice.casts(tapper, target = wynn)
        resolveAll()
        expect {
            alice.life shouldBe 21
            alice.hand has "Forest"
        }
    }

    @Test
    fun vigilanceAttackDoesNotTrigger() = scenario {
        alice.battlefield(wynn)
        alice.library("Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wynn)
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.hand.size shouldBe 0
        }
    }

    // CR 701.20, 401.4: only a Mount creature or a Plains is offered; the rest goes to the bottom
    @Test
    fun pathfinderOffersOnlyMountsAndPlains() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(pathfinder)
        alice.library("Forest", "Plains", ox, duneColt, "Island", "Swamp")
        alice.casts(pathfinder)
        resolveAll()
        val offered = (pending as Decision.ChooseObjects).options
        assertEquals(2, offered.size)
        alice.chooses(duneColt)
        resolveAll()
        expect {
            alice.hand has duneColt
            alice.library.size shouldBe 5
            alice.library.names.first() shouldBe "Swamp"
            alice.library has ox
        }
    }

    @Test
    fun pathfinderTakesNothingWhenDeclined() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(pathfinder)
        alice.library("Forest", "Plains", ox, duneColt, "Island", "Swamp")
        alice.casts(pathfinder)
        resolveAll()
        alice.chooses()
        resolveAll()
        expect {
            alice.hand.size shouldBe 0
            alice.library.size shouldBe 6
            alice.library.names.first() shouldBe "Swamp"
        }
    }
}
