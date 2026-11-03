package arcanum.engine.sets.eoe

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.eoe.EoeExtras.bear
import arcanum.engine.sets.eoe.EoeExtras.charge
import arcanum.engine.sets.eoe.EoeExtras.giant
import arcanum.engine.sets.eoe.EoeExtras.ogre
import arcanum.engine.sets.eoe.EoeExtras.shuttle
import arcanum.engine.sets.eoe.EoeExtras.wall
import arcanum.engine.sets.eoe.EoeExtras.warper
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Edge of Eternities mechanics in play: warp (CR 702.185: the alternative cost from the hand, the permanent exiled at the next end step,
 * the card cast from exile on a later turn, warped cards in exile), station (CR 702.184, 721: charge counters by power or toughness, the
 * striations' abilities and P/T box), Lander tokens (CR 111.10u) and void.
 */
class EoeMechanicsScenariosTest {
    private fun arcanum.engine.scenario.Scenario.libraries() {
        alice.library(EoeExtras.notes, EoeExtras.notes, EoeExtras.notes)
        bob.library(EoeExtras.notes, EoeExtras.notes, EoeExtras.notes)
    }

    // ---- warp

    /** Cast for its warp cost: exiled at the beginning of the next end step, then cast from exile on a later turn for its mana cost. */
    // CR 702.185a
    @Test
    fun warpExilesAtTheNextEndStepAndCastsLater() = scenario {
        libraries()
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(warper)
        alice.casts(warper, way = warpWay(warper))
        resolveAll()
        expect {
            warper.zone shouldBe Zone.Battlefield
            card("Forest", 1).tapped shouldBe false
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { warper.zone shouldBe Zone.Exile }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts(warper)
        resolveAll()
        expect {
            warper.zone shouldBe Zone.Battlefield
            card("Forest", 3).tapped shouldBe true
        }
        advanceTo(TurnStep.End, turn = 3)
        resolveAll()
        expect { warper.zone shouldBe Zone.Battlefield }
    }

    /** The warped card can't be cast in the turn it was exiled (only after that turn has ended). */
    // CR 702.185a
    @Test
    fun warpedCardWaitsForALaterTurn() = scenario {
        libraries()
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(warper)
        alice.casts(warper, way = warpWay(warper))
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { warper.zone shouldBe Zone.Exile }
        assertTrue(Engine.options(state, alice.id).none { it is Input.Cast && it.card == card(warper) })
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertTrue(Engine.options(state, bob.id).none { it is Input.Cast && it.card == card(warper) })
    }

    /** Warp is only from the hand: the warped card in exile is cast for its mana cost, not its warp cost. */
    // CR 702.185a
    @Test
    fun warpOnlyFromTheHand() = scenario {
        libraries()
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(warper)
        alice.casts(warper, way = warpWay(warper))
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        assertFailsWith<Throwable> { alice.casts(warper, way = warpWay(warper)) }
    }

    /** Cast for its mana cost, a warp card stays. */
    // CR 702.185a
    @Test
    fun castNormallyItStays() = scenario {
        libraries()
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(warper)
        alice.casts(warper)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        expect { warper.zone shouldBe Zone.Battlefield }
    }

    /** A warped permanent that left before the end step isn't exiled from where it went. */
    // CR 702.185a, 400.7
    @Test
    fun warpedPermanentThatDiedStaysInTheGraveyard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(warper)
        bob.battlefield("Swamp")
        bob.hand(EoeExtras.doom)
        alice.casts(warper, way = warpWay(warper))
        resolveAll()
        alice.passes()
        bob.casts(EoeExtras.doom, target = warper)
        resolveAll()
        expect { warper.zone shouldBe Zone.Graveyard }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { warper.zone shouldBe Zone.Graveyard }
    }

    /** "A spell was warped this turn": void holds after a spell is cast for its warp cost, not after one cast normally. */
    // CR 702.185c
    @Test
    fun voidAfterAWarpedSpell() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Mountain")
        alice.hand(warper, EoeSet.ionLance)
        alice.casts(warper, way = warpWay(warper))
        resolveAll()
        alice.casts(EoeSet.ionLance, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    /** "A nonland permanent left the battlefield this turn" holds after a creature dies; without either, void doesn't. */
    // CR 207.2c
    @Test
    fun voidAfterAPermanentLeft() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(EoeSet.ionLance, EoeSet.ionLance, EoeSet.ionLance)
        bob.battlefield(bear)
        alice.casts(EoeSet.ionLance, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
        alice.casts(EoeSet.ionLance, target = bear)
        resolveAll()
        expect { bear.zone shouldBe Zone.Graveyard }
        alice.casts(EoeSet.ionLance, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 15 }
    }

    // ---- station

    /** Station: tap another creature, charge counters equal to its power; at 3+ it is an artifact creature 2/2 with flying. */
    // CR 702.184a, 721.2b
    @Test
    fun stationBecomesACreature() = scenario {
        alice.battlefield(shuttle, bear, ogre)
        expect {
            shuttle.types shouldBe listOf(CardType.Artifact)
            shuttle.power shouldBe null
        }
        alice.activates(shuttle, choosing = listOf(bear))
        resolveAll()
        expect {
            shuttle.counters(charge) shouldBe 2
            bear.tapped shouldBe true
            shuttle.types shouldBe listOf(CardType.Artifact)
        }
        alice.activates(shuttle, choosing = listOf(ogre))
        resolveAll()
        expect {
            shuttle.counters(charge) shouldBe 5
            shuttle.types shouldBe listOf(CardType.Artifact, CardType.Creature)
            shuttle.power shouldBe 2
            shuttle hasKeyword Keyword.Flying
        }
    }

    /** Station only as a sorcery, and only by tapping another untapped creature. */
    // CR 702.184a
    @Test
    fun stationOnlyAsASorceryWithAnUntappedCreature() = scenario {
        alice.battlefield(shuttle)
        alice.battlefield(bear, tapped = true)
        assertFailsWith<Throwable> { alice.activates(shuttle, choosing = listOf(bear)) }
    }

    /** Station only as a sorcery: not in combat. */
    // CR 702.184a
    @Test
    fun stationOnlyAsASorcery() = scenario {
        alice.battlefield(shuttle, ogre)
        advanceTo(TurnStep.BeginningOfCombat)
        assertFailsWith<Throwable> { alice.activates(shuttle, choosing = listOf(ogre)) }
    }

    /** A creature with toughness greater than its power stations with its toughness (Tapestry Warden). */
    // CR 702.184c
    @Test
    fun stationWithToughness() = scenario {
        alice.battlefield(shuttle, wall, EoeSet.loomworkSentinel)
        alice.activates(shuttle, choosing = listOf(wall))
        resolveAll()
        expect { shuttle.counters(charge) shouldBe 4 }
    }

    /** A Spacecraft that is a creature only from its counters can attack once it has enough; it stops being one when they're gone. */
    // CR 721.2b, 721.4
    @Test
    fun stationedSpacecraftAttacks() = scenario {
        libraries()
        alice.battlefield(shuttle, giant)
        alice.activates(shuttle, choosing = listOf(giant))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(shuttle)
        advanceTo(TurnStep.End, turn = 3)
        expect { bob.life shouldBe 18 }
    }

    // ---- Lander tokens

    /** A Lander: {2}, {T}, sacrifice it: search for a basic land card, put it onto the battlefield tapped, then shuffle. */
    // CR 111.10u
    @Test
    fun landerFetchesABasicLand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(EoeSet.orlosWanderlust)
        alice.library("Island", "Plains", "Swamp")
        alice.casts(EoeSet.orlosWanderlust)
        resolveAll()
        expect {
            alice.life shouldBe 22
            alice.battlefield has "Lander Token"
        }
        alice.activates("Lander Token")
        resolveAll()
        choose("Plains")
        expect {
            alice.battlefield has "Plains"
            card("Plains").tapped shouldBe true
            alice.battlefield lacks "Lander Token"
        }
    }
}
