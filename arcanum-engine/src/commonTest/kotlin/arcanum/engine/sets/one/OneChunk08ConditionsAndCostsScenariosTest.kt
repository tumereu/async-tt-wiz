package arcanum.engine.sets.one

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Phyrexia: All Will Be One chunk 08 in play: entering that doesn't trigger opponents' abilities and triggers yours twice (CR 603.2, 603.2d),
 * "if that creature has toxic … instead" (CR 608.2c), lands entering tapped unless you control few lands (CR 614.1c), damage to each player
 * unless they control basic lands, an "unless you remove counters" cost (CR 118.12a), abilities of land cards in any graveyard (CR 613.1f),
 * a color and type added until end of turn (layers 4 and 5) and an activation condition looking back over the turn.
 */
class OneChunk08ConditionsAndCostsScenariosTest {
    private val oil = CounterKind.Named("oil")

    private val matron = Cards.invent("Prime Matron of Engines", "{4}{W}", "Legendary Creature — Phyrexian Praetor", """
        Vigilance
        If a permanent entering causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.
        Permanents entering don't cause abilities of permanents your opponents control to trigger.
    """, "4/7")
    private val slash = Cards.invent("Venomgilt Slash", "{R}", "Instant",
        "Venomgilt Slash deals 2 damage to target creature. If that creature has toxic, Venomgilt Slash deals 4 damage to that creature instead.")
    private val ashcleave = Cards.invent("Ashcleave Ridge", null, "Land", """
        This land enters tapped unless you control two or fewer other lands.
        {T}: Add {B} or {R}.
    """)
    private val chastiser = Cards.invent("Kiln Chastiser", "{2}{R}", "Creature — Phyrexian Warrior", """
        Menace
        At the beginning of each player's upkeep, this creature deals 2 damage to that player unless they control two or more basic lands.
    """, "3/3")
    private val sprinter = Cards.invent("Lavaborn Sprinter", "{2}{R}", "Creature — Phyrexian Warrior", """
        Haste
        When this creature enters, put two oil counters on target artifact or creature you control.
        At the beginning of your end step, return this creature to its owner's hand unless you remove two oil counters from it.
    """, "3/2")
    private val vault = Cards.invent("Gilded Haven Vault", "{3}", "Artifact",
        "As long as this artifact is on the battlefield, it has all activated abilities of all land cards in all graveyards.")
    private val tinker = Cards.invent("Vedalken Grand Tinker", "{1}{U}{U}", "Legendary Artifact Creature — Phyrexian Vedalken", """
        Other blue creatures you control have "Whenever this creature becomes tapped, draw a card, then discard a card."
        Other artifact creatures you control get +1/+1.
        {U/P}: Until end of turn, target creature you control becomes a blue artifact in addition to its other colors and types. Activate only as a sorcery. ({U/P} can be paid with either {U} or 2 life.)
    """, "2/4")
    private val vat = Cards.invent("Roiling Oil Vat", "{R}", "Artifact", """
        At the beginning of your upkeep, put an oil counter on another target nontoken artifact or creature you control.
        {2}, {T}: Create a 1/1 red Phyrexian Goblin creature token. Activate only if an oil counter was removed from a permanent you controlled this turn or a permanent with an oil counter on it was put into a graveyard this turn.
    """)

    private val gleamingEntrant = Cards.invent("Gleaming Entrant", "{W}", "Creature — Human", "When this creature enters, you gain 2 life.", "1/1")
    private val rivalEntrant = Cards.invent("Rival Entrant", "{W}", "Creature — Human", "When this creature enters, you gain 2 life.", "1/1")
    private val gleaner = Cards.invent("Watchful Gleaner", "{1}{W}", "Creature — Human", "Whenever another creature enters, you gain 1 life.", "1/2")
    private val onlooker = Cards.invent("Envious Onlooker", "{1}{W}", "Creature — Human", "Whenever another creature enters, you gain 1 life.", "1/2")
    private val spitter = Cards.invent("Ichor Spitter Drone", "{2}{B}", "Creature — Phyrexian Drone", "Toxic 1", "2/4")
    private val brute = Cards.invent("Unmarked Brute", "{2}{G}", "Creature — Beast", "", "2/4")
    private val wastes = Cards.invent("Cinderscar Wastes", null, "Land", "{T}: Add {C}.")
    private val quarry = Cards.invent("Glimmer Quarry", null, "Land", """
        {T}: Add {C}.
        {1}, {T}: You gain 2 life.
    """)
    private val brawler = Cards.invent("Thicket Brawler", "{1}{G}", "Creature — Beast", "", "2/2")
    private val drudge = Cards.invent("Oilback Drudge", "{1}", "Artifact Creature — Phyrexian", "", "1/1")
    private val siphon = Cards.invent("Oil Siphon", "{1}", "Artifact", "{T}, Remove an oil counter from this artifact: You gain 1 life.")
    private val flick = Cards.invent("Searing Flick", "{R}", "Instant", "Searing Flick deals 1 damage to target creature.")

    // CR 603.2: an opponent's creature entering triggers neither its own nor another opponent's abilities; CR 603.2d: yours trigger twice
    @Test
    fun matronMutesOpponentsAndDoublesYoursForAnOpponentsCreature() = scenario(players = 3) {
        alice.battlefield(matron, gleaner)
        bob.battlefield("Plains")
        bob.hand(rivalEntrant)
        carol.battlefield(onlooker)
        turn(number = 2, active = bob)
        bob.casts(rivalEntrant)
        resolveAll()
        expect {
            rivalEntrant.zone shouldBe Zone.Battlefield
            bob.life shouldBe 20
            carol.life shouldBe 20
            alice.life shouldBe 22
        }
    }

    // CR 603.2d: your own creature's enters trigger and your watcher both trigger twice; CR 603.2: the opponent's watcher doesn't trigger
    @Test
    fun matronDoublesYourOwnEnterTriggers() = scenario(players = 3) {
        alice.battlefield(matron, gleaner, "Plains")
        alice.hand(gleamingEntrant)
        carol.battlefield(onlooker)
        alice.casts(gleamingEntrant)
        resolveAll()
        expect {
            alice.life shouldBe 26
            carol.life shouldBe 20
        }
    }

    // CR 608.2c: 4 damage to a creature with toxic
    @Test
    fun slashDealsFourToAToxicCreature() = scenario {
        alice.battlefield("Mountain")
        alice.hand(slash)
        bob.battlefield(spitter)
        alice.casts(slash, target = spitter)
        resolveAll()
        expect { spitter.zone shouldBe Zone.Graveyard }
    }

    // CR 608.2c: only 2 to a creature without toxic
    @Test
    fun slashDealsTwoToACreatureWithoutToxic() = scenario {
        alice.battlefield("Mountain")
        alice.hand(slash)
        bob.battlefield(brute)
        alice.casts(slash, target = brute)
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Battlefield
            brute.view.obj?.damage shouldBe 2
        }
    }

    // CR 614.1c: with three other lands it enters tapped
    @Test
    fun ridgeEntersTappedWithThreeOtherLands() = scenario {
        alice.battlefield("Swamp", "Swamp", "Mountain")
        alice.hand(ashcleave)
        alice.plays(ashcleave)
        expect { ashcleave.tapped shouldBe true }
    }

    // CR 614.1c: with two other lands it enters untapped
    @Test
    fun ridgeEntersUntappedWithTwoOtherLands() = scenario {
        alice.battlefield("Swamp", "Mountain")
        alice.hand(ashcleave)
        alice.plays(ashcleave)
        expect { ashcleave.tapped shouldBe false }
    }

    // CR 503.1a: each player's upkeep; that player (CR 608.2c, checked as it resolves) is dealt damage unless they control two basic lands; a
    // nonbasic land doesn't count (CR 205.4c)
    @Test
    fun chastiserHurtsOnlyPlayersWithFewBasicLands() = scenario(players = 3) {
        alice.battlefield(chastiser)
        bob.battlefield("Mountain", wastes)
        carol.battlefield("Forest", "Forest")
        bob.library("Island", "Island")
        turn(number = 2, active = bob, step = TurnStep.Upkeep)
        beginStep(TurnStep.Upkeep)
        resolveAll()
        expect { bob.life shouldBe 18 }
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect {
            carol.life shouldBe 20
            alice.life shouldBe 20
        }
    }

    // CR 118.12a: removing the two oil counters keeps it on the battlefield
    @Test
    fun sprinterStaysWhenItsCountersAreRemoved() = scenario {
        alice.battlefield(sprinter, counters = listOf(oil to 2))
        advanceTo(TurnStep.End)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        resolveAll()
        expect {
            sprinter.zone shouldBe Zone.Battlefield
            sprinter.counters(oil) shouldBe 0
        }
    }

    // CR 118.12a: without oil counters it can't pay, so it returns to its owner's hand
    @Test
    fun sprinterReturnsWithoutCounters() = scenario {
        alice.battlefield(sprinter)
        advanceTo(TurnStep.End)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        resolveAll()
        expect { sprinter.zone shouldBe Zone.Hand }
    }

    // CR 613.1f: the activated abilities of a land card in an opponent's graveyard
    @Test
    fun vaultUsesALandCardInAnOpponentsGraveyard() = scenario {
        alice.battlefield(vault, "Plains")
        bob.graveyard(quarry)
        alice.activates(vault, ability = 1)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    // CR 613.1d, e: an artifact and blue in addition to green, so it gets the other artifact creatures' +1/+1 (layer 7c) until end of turn
    @Test
    fun tinkerMakesACreatureABlueArtifact() = scenario {
        alice.battlefield(tinker, brawler, "Island")
        alice.activates(tinker, target = brawler)
        resolveAll()
        expect {
            assertTrue(Color.Blue in brawler.colors && Color.Green in brawler.colors)
            assertTrue(CardType.Artifact in brawler.types && CardType.Creature in brawler.types)
            brawler.power shouldBe 3
        }
    }

    // CR 602.5b: not before; a permanent with an oil counter put into a graveyard this turn (as it last existed, CR 603.10a) allows it
    @Test
    fun vatNeedsAnOilPermanentPutIntoAGraveyard() = scenario {
        alice.battlefield(vat, "Mountain", "Mountain", "Mountain")
        alice.battlefield(drudge, counters = listOf(oil to 1))
        alice.hand(flick)
        assertFailsWith<IllegalArgumentException> { alice.activates(vat, ability = 0) }
        alice.casts(flick, target = drudge)
        resolveAll()
        alice.activates(vat, ability = 0)
        resolveAll()
        expect { alice.battlefield has "Phyrexian Goblin Token" }
    }

    // CR 602.5b: an oil counter removed from a permanent you controlled this turn allows it
    @Test
    fun vatAfterAnOilCounterWasRemoved() = scenario {
        alice.battlefield(vat, "Mountain", "Mountain")
        alice.battlefield(siphon, counters = listOf(oil to 1))
        alice.activates(siphon)
        resolveAll()
        alice.activates(vat, ability = 0)
        resolveAll()
        expect { alice.battlefield has "Phyrexian Goblin Token" }
    }
}
