package arcanum.engine.sets.dsk

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dsk.Dsk.bear
import arcanum.engine.sets.dsk.Dsk.giant
import arcanum.engine.sets.dsk.Dsk.ogre
import arcanum.engine.sets.dsk.Dsk.p1p1
import arcanum.engine.sets.dsk.Dsk.shock
import kotlin.test.Test

/** Duskmourn's Rooms in play (parser chunks 01–02): each Room's doors, beyond casting and unlocking played in [DskRoomsScenariosTest]. */
class DskRoomCardsScenariosTest {
    private val gildedPlayhouse = Cards.inventFaces("Split",
        face("Gilded Playhouse", "{3}{W}", "Enchantment — Room", "Creature spells you cast have convoke. (Your creatures can help cast those spells. Each creature you tap while casting a creature spell pays for {1} or one mana of that creature's color.)", null, null, null),
        face("Costume Loft", "{2}{W}", "Enchantment — Room", "Untap each creature you control during each other player's untap step.", null, null, null),
    )
    private val toymakersStall = Cards.inventFaces("Split",
        face("Toymaker's Stall", "{1}{W}", "Enchantment — Room", "Whenever one or more non-Toy creatures you control attack a player, create a 1/1 white Toy artifact creature token.", null, null, null),
        face("Bisque Parlor", "{4}{W}{W}", "Enchantment — Room", "Creatures you control have base power and toughness each equal to the number of creatures you control.", null, null, null),
    )
    private val grandVestibule = Cards.inventFaces("Split",
        face("Grand Vestibule", "{1}{W}", "Enchantment — Room", "When you unlock this door, create a 1/1 white Glimmer enchantment creature token.", null, null, null),
        face("Gleaming Atrium", "{2}{W}", "Enchantment — Room", "When you unlock this door, put a +1/+1 counter on each of up to two target creatures.", null, null, null),
    )
    private val operatingTheater = Cards.inventFaces("Split",
        face("Operating Theater", "{1}{W}", "Enchantment — Room", "When you unlock this door, return target creature card with mana value 3 or less from your graveyard to the battlefield.", null, null, null),
        face("Recovery Ward", "{3}{W}", "Enchantment — Room", "Whenever you attack, put a +1/+1 counter on target attacking creature.", null, null, null),
    )
    private val sunkenBasin = Cards.inventFaces("Split",
        face("Sunken Basin", "{U}", "Enchantment — Room", "When you unlock this door, return up to one target creature to its owner's hand.", null, null, null),
        face("Changing Room", "{4}{U}", "Enchantment — Room", "Whenever one or more creatures you control deal combat damage to a player, draw a card.", null, null, null),
    )
    private val rattlingLift = Cards.inventFaces("Split",
        face("Rattling Lift", "{3}{U}", "Enchantment — Room", "When you unlock this door, search your library for a Room card that doesn't have the same name as a Room you control, reveal it, put it into your hand, then shuffle.", null, null, null),
        face("Hopeful Staircase", "{2}{U}", "Enchantment — Room", "At the beginning of your upkeep, surveil 1. You win the game if there are eight or more different names among unlocked doors of Rooms you control.", null, null, null),
    )
    private val butchersFreezer = Cards.inventFaces("Split",
        face("Butcher's Freezer", "{2}{U}", "Enchantment — Room", "When you unlock this door, tap up to one target creature and put two stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)", null, null, null),
        face("Flooded Kitchen", "{3}{U}{U}", "Enchantment — Room", "When you unlock this door, draw three cards, then discard a card.", null, null, null),
    )
    private val lookingGlassHall = Cards.inventFaces("Split",
        face("Looking-Glass Hall", "{2}{U}", "Enchantment — Room", "When you unlock this door, create a token that's a copy of target creature you control, except it's a Reflection in addition to its other creature types.", null, null, null),
        face("Splintered Wing", "{5}{U}{U}", "Enchantment — Room", "If a triggered ability of a permanent you control triggers, that ability triggers an additional time.", null, null, null),
    )
    private val drownedPassage = Cards.inventFaces("Split",
        face("Drowned Passage", "{U}", "Enchantment — Room", "When you unlock this door, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)", null, null, null),
        face("Murky Tank", "{3}{U}", "Enchantment — Room", "When you unlock this door, manifest dread, then put a +1/+1 counter on that creature.", null, null, null),
    )
    private val offLimitsStudy = Cards.inventFaces("Split",
        face("Off-Limits Study", "{2}{W}{W}", "Enchantment — Room", "When you unlock this door, destroy all creatures with power 3 or greater.", null, null, null),
        face("Seminar Chamber", "{5}{U}{U}", "Enchantment — Room", "Other permanents you control have hexproof.", null, null, null),
    )
    private val desecratedVault = Cards.inventFaces("Split",
        face("Desecrated Vault", "{3}{B}", "Enchantment — Room", "Whenever one or more cards leave your graveyard, create a 2/2 black Horror enchantment creature token. This ability triggers only once each turn.", null, null, null),
        face("Embalming Lab", "{B}", "Enchantment — Room", "When you unlock this door, return target creature card from your graveyard to your hand.", null, null, null),
    )
    private val dustyGarret = Cards.inventFaces("Split",
        face("Dusty Garret", "{2}{B}", "Enchantment — Room", "When you unlock this door, you draw two cards and you lose 2 life.", null, null, null),
        face("Lonely Balcony", "{3}{B}", "Enchantment — Room", "Whenever a creature you control attacks alone, it gets +1/+0 and gains deathtouch until end of turn.", null, null, null),
    )
    private val mourningChapel = Cards.inventFaces("Split",
        face("Mourning Chapel", "{2}{B}", "Enchantment — Room", "Whenever a creature you control dies, each opponent loses 1 life and you gain 1 life.", null, null, null),
        face("Restless Hall", "{6}{B}{B}", "Enchantment — Room", "When you unlock this door, return all creature cards from your graveyard to the battlefield.", null, null, null),
    )
    private val profaneAnnex = Cards.inventFaces("Split",
        face("Profane Annex", "{2}{B}", "Enchantment — Room", "At the beginning of your end step, draw a card. If you control a Demon, each opponent loses 2 life and you gain 2 life. Otherwise, you lose 2 life.", null, null, null),
        face("Summoning Chamber", "{3}{B}{B}", "Enchantment — Room", "When you unlock this door, create a 6/6 black Demon creature token with flying.", null, null, null),
    )
    private val scorchedLobby = Cards.inventFaces("Split",
        face("Scorched Lobby", "{3}{R}", "Enchantment — Room", "At the beginning of your upkeep, exile the top card of your library. You may play it this turn.", null, null, null),
        face("Twisted Gallery", "{4}{R}{R}", "Enchantment — Room", "Once each turn, you may pay {0} rather than pay the mana cost for a spell you cast from exile.", null, null, null),
    )
    private val shatteredConservatory = Cards.inventFaces("Split",
        face("Shattered Conservatory", "{2}{R}", "Enchantment — Room", "When you unlock this door, this Room deals 4 damage to target creature an opponent controls.", null, null, null),
        face("Splintered Courtyard", "{4}{R}", "Enchantment — Room", "At the beginning of your end step, this Room deals 1 damage to each opponent.", null, null, null),
    )
    private val artistsLoft = Cards.inventFaces("Split",
        face("Artist's Loft", "{2}{R}", "Enchantment — Room", "When you unlock this door, exile the top two cards of your library. You may play them until the end of your next turn.", null, null, null),
        face("Vandalized Hall", "{1}{R}", "Enchantment — Room", "Whenever you attack, attacking creatures you control get +1/+0 until end of turn.", null, null, null),
    )
    private val admissionKiosk = Cards.inventFaces("Split",
        face("Admission Kiosk", "{2}{R}", "Enchantment — Room", "When you unlock this door, manifest dread.", null, null, null),
        face("Hatred Tunnel", "{4}{R}{R}", "Enchantment — Room", "Whenever you attack, target attacking creature gains double strike until end of turn.", null, null, null),
    )
    private val overgrownSolarium = Cards.inventFaces("Split",
        face("Overgrown Solarium", "{2}{G}", "Enchantment — Room", "Lands you control have \"{T}: Add one mana of any color.\"", null, null, null),
        face("Creaking Pergola", "{3}{G}", "Enchantment — Room", "When you unlock this door, mill four cards, then return up to two permanent cards from among them to your hand.", null, null, null),
    )
    private val mildewedGymnasium = Cards.inventFaces("Split",
        face("Mildewed Gymnasium", "{2}{G}", "Enchantment — Room", "When you unlock this door, search your library for a basic land card, put it onto the battlefield tapped, then shuffle.", null, null, null),
        face("Iron Room", "{5}{G}", "Enchantment — Room", "When you unlock this door, manifest dread, then put three +1/+1 counters on that creature.", null, null, null),
    )
    private val wardrobeNook = Cards.inventFaces("Split",
        face("Wardrobe Nook", "{2}{G}", "Enchantment — Room", "You may play lands from your graveyard.", null, null, null),
        face("Forsaken Basement", "{3}{G}{G}", "Enchantment — Room", "When you unlock this door, you may cast spells from your graveyard this turn, and if a card would be put into your graveyard from anywhere this turn, exile it instead.", null, null, null),
    )
    private val blazingHearth = Cards.inventFaces("Split",
        face("Blazing Hearth", "{1}{R}", "Enchantment — Room", "When you unlock this door, this Room deals damage equal to the number of cards in your hand to target creature an opponent controls.", null, null, null),
        face("Vapor Bath", "{3}{U}{U}", "Enchantment — Room", """
            You have no maximum hand size.
            At the beginning of your end step, draw a card.
        """, null, null, null),
    )
    private val hazyLounge = Cards.inventFaces("Split",
        face("Hazy Lounge", "{2}{R}", "Enchantment — Room", "At the beginning of your first main phase, add {R}{R}. Spend this mana only to cast Room spells and unlock doors.", null, null, null),
        face("Foggy Salon", "{3}{U}", "Enchantment — Room", "When you unlock this door, create an X/X blue Spirit creature token with flying, where X is the number of unlocked doors among Rooms you control.", null, null, null),
    )

    // ---- Chunks 01–02: Rooms (casting a door and unlocking: DskRoomsScenariosTest)

    // CR 502.3: untapping during another player's untap step
    @Test
    fun costumeLoftUntapsDuringTheOpponentsUntapStep() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.battlefield(bear, tapped = true)
        alice.hand(gildedPlayhouse)
        bob.library(ogre)
        alice.castsDoor(gildedPlayhouse, 1)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { bear.tapped shouldBe false }
    }

    // CR 702.51a: creature spells you cast have convoke
    @Test
    fun gildedPlayhouseGivesCreatureSpellsConvoke() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Forest", Dsk.human)
        alice.hand(gildedPlayhouse, bear)
        alice.castsDoor(gildedPlayhouse, 0, paying = listOf("Plains", "Plains#1", "Plains#2", "Plains#3"))
        resolveAll()
        alice.casts(bear, paying = listOf("Forest", Dsk.human))
        resolveAll()
        expect {
            alice.battlefield has bear
            Dsk.human.tapped shouldBe true
        }
    }

    // CR 508.1: "whenever one or more … attack a player"
    @Test
    fun toymakersStallMakesAToyOnAttack() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(toymakersStall)
        alice.castsDoor(toymakersStall, 0)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect { alice.battlefield has "Toy Token" }
    }

    // CR 613.4b: base power and toughness set by a count
    @Test
    fun bisqueParlorSetsBaseSizeByTheCreatureCount() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", bear, giant, Dsk.human)
        alice.hand(toymakersStall)
        alice.castsDoor(toymakersStall, 1)
        resolveAll()
        expect {
            giant.power shouldBe 3
            Dsk.human.toughness shouldBe 3
        }
    }

    // CR 709.5h: "when you unlock this door" for each door
    @Test
    fun grandVestibuleMakesAGlimmerThenGrowsTwo() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(grandVestibule)
        alice.castsDoor(grandVestibule, 0, paying = listOf("Plains", "Plains#1"))
        resolveAll()
        expect {
            alice.battlefield has "Glimmer Token"
            "Glimmer Token".types.toSet() shouldBe setOf(CardType.Enchantment, CardType.Creature)
        }
        alice.takesSpecialAction(grandVestibule, "unlock-right", paying = listOf("Plains#2", "Plains#3", "Plains#4"))
        targets(bear, "Glimmer Token")
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            "Glimmer Token".counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun operatingTheaterReturnsACheapCreature() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(operatingTheater)
        alice.graveyard(ogre, giant)
        alice.castsDoor(operatingTheater, 0)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { alice.battlefield has ogre }
    }

    @Test
    fun recoveryWardGrowsAnAttacker() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(operatingTheater)
        alice.castsDoor(operatingTheater, 1)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun sunkenBasinBouncesACreature() = scenario {
        alice.battlefield("Island")
        alice.hand(sunkenBasin)
        bob.battlefield(ogre)
        alice.castsDoor(sunkenBasin, 0)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.hand has ogre }
    }

    @Test
    fun changingRoomDrawsOnCombatDamage() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", bear)
        alice.hand(sunkenBasin)
        alice.library(ogre)
        alice.castsDoor(sunkenBasin, 1)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.EndOfCombat)
        expect { alice.hand has ogre }
    }

    // CR 201.2: a Room with an unlocked door has that door's name
    @Test
    fun rattlingLiftFindsAnotherRoom() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(rattlingLift)
        alice.library(bear, sunkenBasin)
        alice.castsDoor(rattlingLift, 0)
        resolveAll()
        alice.chooses(sunkenBasin)
        resolveAll()
        expect { alice.hand has sunkenBasin }
    }

    @Test
    fun butchersFreezerStunsTwice() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(butchersFreezer)
        bob.battlefield(ogre)
        alice.castsDoor(butchersFreezer, 0)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 2
        }
    }

    @Test
    fun floodedKitchenDrawsThreeDiscardsOne() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(butchersFreezer, giant)
        alice.library(bear, ogre, Dsk.human)
        alice.castsDoor(butchersFreezer, 1)
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        expect { alice.hand.names shouldBe listOf(bear, ogre, Dsk.human) }
    }

    // CR 707.9a: a copy with an exception adding a creature type
    @Test
    fun lookingGlassHallCopiesAsAReflection() = scenario {
        alice.battlefield("Island", "Island", "Island", bear)
        alice.hand(lookingGlassHall)
        alice.castsDoor(lookingGlassHall, 0)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == bear } shouldBe 2
            card(bear, 1).subtypes.map { it.name }.contains("Reflection") shouldBe true
        }
    }

    // CR 603.2d: a triggered ability of a permanent you control triggers an additional time
    @Test
    fun splinteredWingDoublesTriggers() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(lookingGlassHall, drownedPassage)
        alice.library(bear, ogre, giant, Dsk.human)
        alice.castsDoor(lookingGlassHall, 1)
        resolveAll()
        alice.castsDoor(drownedPassage, 0)
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.graveyard has ogre
        }
    }

    // CR 701.62a: manifest dread, then a counter on that creature
    @Test
    fun murkyTankManifestsWithACounter() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(drownedPassage)
        alice.library(ogre, "Island")
        alice.castsDoor(drownedPassage, 1)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            card(ogre).power shouldBe 3
            alice.graveyard has "Island"
        }
    }

    @Test
    fun drownedPassageSurveilsTwo() = scenario {
        alice.battlefield("Island")
        alice.hand(drownedPassage)
        alice.library(ogre, bear, giant)
        alice.castsDoor(drownedPassage, 0)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { alice.graveyard has ogre }
    }

    @Test
    fun offLimitsStudyDestroysTheBig() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(offLimitsStudy)
        bob.battlefield(ogre, giant)
        alice.castsDoor(offLimitsStudy, 0)
        resolveAll()
        expect {
            bob.graveyard has ogre
            bob.graveyard has giant
            alice.battlefield has bear
        }
    }

    @Test
    fun seminarChamberGivesOtherPermanentsHexproof() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", bear)
        alice.hand(offLimitsStudy)
        alice.castsDoor(offLimitsStudy, 1)
        resolveAll()
        expect { bear hasKeyword Keyword.Hexproof }
    }

    // CR 603.2h: "triggers only once each turn"; Embalming Lab's return makes a card leave the graveyard
    @Test
    fun desecratedVaultMakesAHorrorWhenCardsLeave() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(desecratedVault)
        alice.graveyard(bear)
        alice.castsDoor(desecratedVault, 0, paying = listOf("Swamp", "Swamp#1", "Swamp#2", "Swamp#3"))
        resolveAll()
        alice.takesSpecialAction(desecratedVault, "unlock-right", paying = listOf("Swamp#4"))
        targetsIfAsked(bear)
        resolveAll()
        expect {
            alice.hand has bear
            alice.battlefield has "Horror Token"
        }
    }

    @Test
    fun dustyGarretDrawsTwoForTwoLife() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(dustyGarret)
        alice.library(bear, ogre)
        alice.castsDoor(dustyGarret, 0)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(bear, ogre)
            alice.life shouldBe 18
        }
    }

    // CR 506.5: attacks alone
    @Test
    fun lonelyBalconyArmsALoneAttacker() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(dustyGarret)
        alice.castsDoor(dustyGarret, 1)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Deathtouch
        }
    }

    @Test
    fun mourningChapelDrainsOnADeath() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain", bear)
        alice.hand(mourningChapel, shock)
        alice.castsDoor(mourningChapel, 0, paying = listOf("Swamp", "Swamp#1", "Swamp#2"))
        resolveAll()
        alice.casts(shock, target = bear)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    @Test
    fun restlessHallReturnsAllCreatures() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(mourningChapel)
        alice.graveyard(bear, ogre, Dsk.charm)
        alice.castsDoor(mourningChapel, 1)
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.battlefield has ogre
            alice.graveyard has Dsk.charm
        }
    }

    @Test
    fun profaneAnnexDrawsForLifeWithoutADemon() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(profaneAnnex)
        alice.library(bear)
        alice.castsDoor(profaneAnnex, 0)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.hand has bear
            alice.life shouldBe 18
        }
    }

    @Test
    fun profaneAnnexDrainsWithADemon() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", Dsk.demon)
        alice.hand(profaneAnnex)
        alice.library(bear)
        alice.castsDoor(profaneAnnex, 0)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    @Test
    fun summoningChamberMakesADemon() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(profaneAnnex)
        alice.castsDoor(profaneAnnex, 1)
        resolveAll()
        expect {
            "Demon Token".power shouldBe 6
            "Demon Token" hasKeyword Keyword.Flying
        }
    }

    @Test
    fun scorchedLobbyExilesAPlayableCardEachUpkeep() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(scorchedLobby)
        alice.library("Forest", bear)
        bob.library(ogre)
        alice.castsDoor(scorchedLobby, 0)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        advanceTo(TurnStep.PrecombatMain)
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    @Test
    fun shatteredConservatoryBurnsACreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(shatteredConservatory)
        bob.battlefield(ogre)
        alice.castsDoor(shatteredConservatory, 0)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun splinteredCourtyardPingsAtTheEndStep() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(shatteredConservatory)
        alice.castsDoor(shatteredConservatory, 1)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun artistsLoftExilesTwoPlayable() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(artistsLoft)
        alice.library("Forest", bear)
        alice.castsDoor(artistsLoft, 0)
        resolveAll()
        alice.plays("Forest")
        expect {
            alice.battlefield has "Forest"
            alice.exile has bear
        }
    }

    @Test
    fun vandalizedHallPumpsAttackers() = scenario {
        alice.battlefield("Mountain", "Mountain", bear)
        alice.hand(artistsLoft)
        alice.castsDoor(artistsLoft, 1)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun admissionKioskManifestsDread() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(admissionKiosk)
        alice.library(ogre, "Island")
        alice.castsDoor(admissionKiosk, 0)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { card(ogre).power shouldBe 2 }
    }

    @Test
    fun hatredTunnelGivesDoubleStrike() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", bear)
        alice.hand(admissionKiosk)
        alice.castsDoor(admissionKiosk, 1)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        targetsIfAsked(bear)
        resolveAll()
        expect { bear hasKeyword Keyword.DoubleStrike }
    }

    // CR 613.1f: lands gain a mana ability
    @Test
    fun overgrownSolariumLetsLandsMakeAnyColor() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Plains")
        alice.hand(overgrownSolarium)
        alice.castsDoor(overgrownSolarium, 0, paying = listOf("Forest", "Forest#1", "Forest#2"))
        resolveAll()
        alice.activates("Plains", ability = 1, color = Color.Blue)
        expect { alice.pool.size shouldBe 1 }
    }

    @Test
    fun creakingPergolaReturnsTwoMilledPermanents() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(overgrownSolarium)
        alice.library(bear, Dsk.ponder, ogre, "Island", giant)
        alice.castsDoor(overgrownSolarium, 1)
        resolveAll()
        alice.chooses(bear, ogre)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(bear, ogre)
            alice.graveyard has Dsk.ponder
            alice.library.names shouldBe listOf(giant)
        }
    }

    @Test
    fun mildewedGymnasiumFetchesALand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(mildewedGymnasium)
        alice.library(bear, "Plains")
        alice.castsDoor(mildewedGymnasium, 0)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Plains")
        resolveAll()
        expect {
            alice.battlefield has "Plains"
            "Plains".tapped shouldBe true
        }
    }

    @Test
    fun ironRoomManifestsWithThreeCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(mildewedGymnasium)
        alice.library(ogre, "Island")
        alice.castsDoor(mildewedGymnasium, 1)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { card(ogre).power shouldBe 5 }
    }

    // CR 305.1, 601.3: playing lands from the graveyard
    @Test
    fun wardrobeNookPlaysLandsFromTheGraveyard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(wardrobeNook)
        alice.graveyard("Plains")
        alice.castsDoor(wardrobeNook, 0)
        resolveAll()
        alice.plays("Plains")
        expect { alice.battlefield has "Plains" }
    }

    // CR 614.1a: exiled instead of put into the graveyard
    @Test
    fun forsakenBasementCastsFromTheGraveyard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Mountain")
        alice.hand(wardrobeNook)
        alice.graveyard(shock)
        alice.castsDoor(wardrobeNook, 1, paying = listOf("Forest", "Forest#1", "Forest#2", "Forest#3", "Forest#4"))
        resolveAll()
        alice.casts(shock, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.exile has shock
        }
    }

    @Test
    fun blazingHearthBurnsByTheHandSize() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(blazingHearth, bear, ogre, giant)
        bob.battlefield(ogre)
        alice.castsDoor(blazingHearth, 0)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    // CR 402.2: no maximum hand size
    @Test
    fun vaporBathDrawsAtTheEndStep() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(blazingHearth)
        alice.library(bear)
        alice.castsDoor(blazingHearth, 1)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun foggySalonMakesASpiritByTheUnlockedDoors() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(hazyLounge)
        alice.castsDoor(hazyLounge, 1)
        resolveAll()
        expect {
            "Spirit Token".power shouldBe 1
            "Spirit Token" hasKeyword Keyword.Flying
        }
    }

    // CR 106.6: restricted mana unlocks a door
    @Test
    fun hazyLoungeAddsRoomMana() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(hazyLounge)
        alice.library(bear)
        bob.library(ogre)
        alice.castsDoor(hazyLounge, 0)
        resolveAll()
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        expect { alice.pool.size shouldBe 2 }
    }
}
