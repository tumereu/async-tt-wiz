package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.Subtype
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.ownAlternative
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Chunk 11b played: the invented names carry the oracle text of Demolition Field, Hoarding Dragon, Feldon's Cane, Finale of Revelation,
 * Infernal Vessel, Banner of Kinship, Blasphemous Edict and Tribute to Hunger.
 */
class FdnChunk11bSearchShuffleScenariosTest {
    private val fellowship = CounterKind.Named("fellowship")
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val field = Cards.invent("Quarry Wasteland", null, "Land", """
        {T}: Add {C}.
        {2}, {T}, Sacrifice this land: Destroy target nonbasic land an opponent controls. That land's controller may search their library for a basic land card, put it onto the battlefield, then shuffle. You may search your library for a basic land card, put it onto the battlefield, then shuffle.
    """)
    private val bobsLand = Cards.invent("Quarry Hollow", null, "Land", "{T}: Add {C}.")
    private val dragon = Cards.invent("Cache Wyrm", "{3}{R}{R}", "Creature — Dragon", """
        Flying
        When this creature enters, you may search your library for an artifact card, exile it, then shuffle.
        When this creature dies, you may put the exiled card into its owner's hand.
    """, "4/4")
    private val cane = Cards.invent("Ashen Reset", "{1}", "Artifact", "{T}, Exile this artifact: Shuffle your graveyard into your library.")
    private val finale = Cards.invent("Tide of Revelation", "{X}{U}{U}", "Sorcery", """
        Draw X cards. If X is 10 or more, instead shuffle your graveyard into your library, draw X cards, untap up to five lands, and you have no maximum hand size for the rest of the game.
        Exile Tide of Revelation.
    """)
    private val vessel = Cards.invent("Cinder Vessel", "{2}{B}", "Creature — Human Cleric", "When this creature dies, if it wasn't a Demon, return it to the battlefield under its owner's control with two +1/+1 counters on it. It's a Demon in addition to its other types.", "2/1")
    private val fiend = Cards.invent("Cinder Fiend", "{2}{B}", "Creature — Demon Cleric", "When this creature dies, if it wasn't a Demon, return it to the battlefield under its owner's control with two +1/+1 counters on it. It's a Demon in addition to its other types.", "2/1")
    private val banner = Cards.invent("Standard of Kin", "{5}", "Artifact", """
        As this artifact enters, choose a creature type. This artifact enters with a fellowship counter on it for each creature you control of the chosen type.
        Creatures you control of the chosen type get +1/+1 for each fellowship counter on this artifact.
    """)
    private val edict = Cards.invent("Grim Ultimatum", "{3}{B}{B}", "Sorcery", """
        You may pay {B} rather than pay this spell's mana cost if there are thirteen or more creatures on the battlefield.
        Each player sacrifices thirteen creatures of their choice.
    """)
    private val toll = Cards.invent("Famine Toll", "{2}{B}", "Instant", "Target opponent sacrifices a creature of their choice. You gain life equal to that creature's toughness.")

    private val cull = Cards.invent("Hollowvein Cull", "{B}", "Instant", "Destroy target creature.")
    private val relic = Cards.invent("Hollowvein Relic", "{2}", "Artifact", "")
    private val bear = Cards.invent("Hollowvein Bear", "{2}{G}", "Creature — Bear", "", "2/2")
    private val bruiser = Cards.invent("Hollowvein Bruiser", "{2}{G}", "Creature — Bear", "", "3/3")
    private val ogre = Cards.invent("Hollowvein Ogre", "{3}{B}", "Creature — Ogre", "", "4/4")
    private val imp = Cards.invent("Hollowvein Imp", "{1}{B}", "Creature — Imp", "", "1/1")
    private val drone = Cards.invent("Hollowvein Drone", "{1}", "Creature — Insect", "", "1/1")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 701.23a, 701.24: both players fetch a basic land onto the battlefield; the destroyed land's controller searches first (CR 608.2c order)
    @Test
    fun fieldDestroysTheLandAndBothPlayersFetchABasic() = scenario {
        alice.battlefield(field, "Swamp", "Swamp")
        bob.battlefield(bobsLand)
        alice.library("Forest", "Island")
        bob.library("Mountain", "Plains")
        alice.activates(field, ability = 1, target = bobsLand)
        resolveAll()
        bob.answers(Answer.YesNo(true))
        bob.chooses("Mountain")
        resolveAll()
        alice.answers(Answer.YesNo(true))
        alice.chooses("Forest")
        resolveAll()
        expect {
            bobsLand.zone shouldBe Zone.Graveyard
            field.zone shouldBe Zone.Graveyard
            bob.battlefield has "Mountain"
            alice.battlefield has "Forest"
        }
    }

    // CR 701.23a, 406.1, 607.2a (linked): the Wyrm exiles an artifact; when it dies, that card goes to its owner's hand
    @Test
    fun dragonExilesAnArtifactAndReturnsItWhenItDies() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Swamp")
        alice.hand(dragon, cull)
        alice.library(relic, "Island")
        alice.casts(dragon)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        alice.chooses(relic)
        resolveAll()
        expect { relic.zone shouldBe Zone.Exile }
        alice.casts(cull, target = dragon)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            dragon.zone shouldBe Zone.Graveyard
            alice.hand has relic
        }
    }

    // CR 603.5: "you may": declining the search leaves the library alone
    @Test
    fun dragonMaySkipTheSearch() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(dragon)
        alice.library(relic, "Island")
        alice.casts(dragon)
        resolveAll()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect { alice.library has relic }
    }

    // CR 701.24a: the graveyard cards are put into the library, which is shuffled; the Cane exiles itself as a cost (CR 118.3)
    @Test
    fun caneShufflesTheGraveyardIntoTheLibrary() = scenario {
        alice.battlefield(cane)
        alice.graveyard(bear, ogre)
        alice.library("Island")
        alice.activates(cane)
        resolveAll()
        expect {
            alice.graveyard lacks bear
            alice.graveyard lacks ogre
            alice.library has bear
            alice.library has ogre
            alice.library.size shouldBe 3
            cane.zone shouldBe Zone.Exile
        }
    }

    // CR 107.3, 608.2e: with X below 10 it only draws X cards and the graveyard stays
    @Test
    fun finaleWithASmallXOnlyDraws() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(finale)
        alice.graveyard(bear)
        alice.library("Forest", "Forest", "Forest")
        alice.casts(finale, x = 2)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.graveyard has bear
            alice.library.size shouldBe 1
            finale.zone shouldBe Zone.Exile
        }
    }

    // CR 608.2e, 701.24a, 107.3, 701.26a: with X of 10 the graveyard is shuffled in first, then X cards are drawn and up to five lands untap
    @Test
    fun finaleWithTenShufflesTheGraveyardDrawsAndUntaps() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(finale)
        alice.graveyard(bear)
        alice.library("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.casts(finale, x = 10)
        resolveAll()
        alice.chooses("Island", "Island", "Island", "Island", "Island")
        resolveAll()
        expect {
            alice.hand.size shouldBe 10
            alice.graveyard lacks bear
            alice.library.size shouldBe 1
            finale.zone shouldBe Zone.Exile
            (0 until 12).count { !card("Island", it).tapped } shouldBe 5
        }
    }

    // CR 603.10a, 400.7, 122.1, 205.3: a non-Demon returns as a new object with two +1/+1 counters and is a Demon
    @Test
    fun vesselReturnsAsADemonWithCounters() = scenario {
        alice.battlefield(vessel, "Swamp")
        alice.hand(cull)
        alice.casts(cull, target = vessel)
        resolveAll()
        expect {
            vessel.zone shouldBe Zone.Battlefield
            vessel.counters(p1p1) shouldBe 2
            vessel.subtypes.contains(Subtype("Demon")) shouldBe true
            vessel.subtypes.contains(Subtype("Cleric")) shouldBe true
        }
    }

    // CR 603.4 (intervening if): a Demon's death trigger does nothing, and it stays dead
    @Test
    fun aDemonVesselStaysDead() = scenario {
        alice.battlefield(fiend, "Swamp")
        alice.hand(cull)
        alice.casts(cull, target = fiend)
        resolveAll()
        expect { fiend.zone shouldBe Zone.Graveyard }
    }

    // CR 614.12, 607.2d, 614.1c, 613.4c: two Bears chosen = two fellowship counters = +2/+2 for each Bear, not for the Ogre
    @Test
    fun bannerCountsTheChosenTypeAsItEnters() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", bear, bruiser, ogre)
        alice.hand(banner)
        alice.casts(banner)
        resolveAll()
        answer(Answer.SubtypeChoice(Subtype("Bear")))
        resolveAll()
        expect {
            banner.counters(fellowship) shouldBe 2
            bear.power shouldBe 4
            bruiser.power shouldBe 5
            ogre.power shouldBe 4
        }
    }

    // CR 601.2f, 118.9: the alternative cost needs thirteen creatures; with twelve only the full cost can be paid
    @Test
    fun edictCostsOnlyBWithThirteenCreatures() = scenario {
        alice.battlefield("Swamp", *Array(7) { drone })
        bob.battlefield(*Array(6) { drone })
        alice.hand(edict)
        alice.casts(edict, way = ownAlternative("Grim Ultimatum"))
        resolveAll()
        expect {
            alice.graveyard has edict
            alice.battlefield lacks drone
            bob.battlefield lacks drone
        }
    }

    @Test
    fun edictCantBeCastForBWithTwelveCreatures() = scenario {
        alice.battlefield("Swamp", *Array(6) { drone })
        bob.battlefield(*Array(6) { drone })
        alice.hand(edict)
        assertFailsWith<Throwable> { alice.casts(edict, way = ownAlternative("Grim Ultimatum")) }
        alice.casts(edict)
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    // CR 701.21 (the opponent chooses), 119.3: the life gained is the toughness of the creature that was sacrificed
    @Test
    fun tollMakesTheOpponentSacrificeAndGainsItsToughness() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        bob.battlefield(ogre, imp)
        alice.hand(toll)
        alice.casts(toll, target = "bob")
        resolveAll()
        bob.chooses(imp)
        resolveAll()
        expect {
            imp.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Battlefield
            alice.life shouldBe 21
        }
    }
}
