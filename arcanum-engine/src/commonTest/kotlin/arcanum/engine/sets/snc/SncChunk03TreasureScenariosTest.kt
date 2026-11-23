package arcanum.engine.sets.snc

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.sets.fin.targets
import arcanum.engine.sets.fin.yes
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Streets of New Capenna chunk 03 in play: Treasure tokens counted, made for others, granted to lands and made tapped. */
class SncChunk03TreasureScenariosTest {
    private val tycoon = Cards.invent("Snc03 Back Alley Hoarder", "{R}{G}", "Creature — Cat Rogue", """
        At the beginning of your upkeep, this creature deals 2 damage to you for each Treasure you control.
        {T}: Create a Treasure token.
    """, "2/2")
    private val mint = Cards.invent("Snc03 Mint Run", "{R}", "Sorcery", "Create three Treasure tokens.")
    private val wyrm = Cards.invent("Snc03 Brazen Cinder Wyrm", "{3}{B}{R}{G}", "Legendary Creature — Demon Dragon", """
        Flying
        At the beginning of your end step, you may sacrifice another creature. When you do, this creature deals damage equal to that creature's power to any target and you create three Treasure tokens.
    """, "6/6")
    private val ogre = Cards.invent("Snc03 Sewer Ogre", "{3}{R}", "Creature — Ogre", "", "4/3")
    private val rat = Cards.invent("Snc03 Gutter Rat", "{B}", "Creature — Rat", "", "1/1")
    private val stash = Cards.invent("Snc03 Smugglers' Cellar Stock", "{5}{G}", "Artifact", "Lands you control have \"{T}: Create a Treasure token.\"")
    private val lot = Cards.invent("Snc03 Quiet Lot", null, "Land", "")
    private val bobLot = Cards.invent("Snc03 Back Lot", null, "Land", "")
    private val silence = Cards.invent("Snc03 Hush Money Offer", "{4}{W}", "Sorcery",
        "Exile target nonland permanent. Its controller creates a Treasure token.")
    private val scarab = Cards.invent("Snc03 Gilded Dung Beetle", "{2}", "Artifact Creature — Insect",
        "{2}, Exile this card from your graveyard: Create a Treasure token.", "2/1")
    private val firebrand = Cards.invent("Snc03 Ashmane Firebrand", "{1}{B/R}{R}{R/G}", "Legendary Creature — Lizard Warrior", """
        Haste
        Whenever a creature you control with haste attacks, create a tapped Treasure token.
    """, "3/3")
    private val fixer = Cards.invent("Snc03 Knuckle Fixer", "{2}{R}", "Creature — Human Warrior", """
        Menace
        Whenever one or more creatures you control deal combat damage to a player, create a Treasure token.
        Sacrifice a Treasure: Exile the top card of your library. You may play that card this turn.
    """, "2/3")

    private fun treasures(player: ScenarioPlayer): Int = player.battlefield.names.count { it == "Treasure Token" }

    /** Casts the Mint Run (setup comes first: the caller gives Alice a Mountain and the card in hand). */
    private fun Scenario.makeTreasures() {
        alice.casts(mint)
        resolveAll()
        check(treasures(alice) == 3)
    }

    // CR 603.2, 120.3, 111.10a: two damage to its controller only, for each Treasure; three players
    @Test
    fun hoarderHurtsOnlyItsControllerTwicePerTreasure() = scenario(players = 3) {
        alice.battlefield(tycoon, "Mountain")
        alice.hand(mint)
        bob.library("Island", "Island", "Island")
        carol.library("Island", "Island", "Island")
        alice.library("Island", "Island", "Island")
        makeTreasures()
        advanceTo(TurnStep.Upkeep, turn = 4)
        resolveAll()
        expect {
            alice.life shouldBe 14
            bob.life shouldBe 20
            carol.life shouldBe 20
        }
    }

    // CR 111.10a, 602.2: the tap ability makes a Treasure
    @Test
    fun hoarderTapsForATreasure() = scenario {
        alice.battlefield(tycoon)
        alice.activates(tycoon)
        resolveAll()
        expect { treasures(alice) shouldBe 1 }
    }

    // CR 603.12, 608.2h, 111.10a: the sacrificed creature's power is the damage; three Treasures follow; three players
    @Test
    fun wyrmSacrificesACreatureForDamageAndTreasures() = scenario(players = 3) {
        alice.battlefield(wyrm, ogre)
        advanceTo(TurnStep.End)
        resolveAll()
        yes()
        if (pending is arcanum.engine.decision.Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        if (pending != null) targets("carol")
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            carol.life shouldBe 16
            bob.life shouldBe 20
        }
        check(treasures(alice) == 3)
    }

    // CR 603.12: nothing sacrificed, no reflexive trigger, no Treasures
    @Test
    fun wyrmDecliningMakesNothing() = scenario {
        alice.battlefield(wyrm, ogre)
        advanceTo(TurnStep.End)
        resolveAll()
        yes(false)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            bob.life shouldBe 20
        }
        check(treasures(alice) == 0)
    }

    // CR 113.6, 613.1f (granted ability), 602.5, 111.10a: a land makes a Treasure by tapping, only for its controller
    @Test
    fun stashLetsLandsMakeTreasures() = scenario {
        alice.battlefield(stash, lot)
        bob.battlefield(bobLot)
        alice.activates(lot)
        resolveAll()
        expect {
            treasures(alice) shouldBe 1
            lot.tapped shouldBe true
        }
        assertFailsWith<IllegalArgumentException> { alice.activates(lot) }
    }

    // CR 111.2 (the creating player owns the token), 608.2h: the permanent's controller, not the caster, gets it; three players
    @Test
    fun silenceGivesTheTreasureToTheExiledPermanentsController() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        carol.battlefield(ogre)
        alice.hand(silence)
        alice.casts(silence, target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Exile }
        check(treasures(carol) == 1 && treasures(alice) == 0 && treasures(bob) == 0)
    }

    // CR 113.6m (the cost exiles it from the graveyard), 111.10a
    @Test
    fun scarabExilesItselfFromTheGraveyardForATreasure() = scenario {
        alice.battlefield("Island", "Island")
        alice.graveyard(scarab)
        alice.activates(scarab)
        resolveAll()
        expect { scarab.zone shouldBe Zone.Exile }
        check(treasures(alice) == 1)
    }

    // CR 508.1, 111.10a, 111.5: each attacking creature with haste makes a tapped Treasure; one without haste makes none
    @Test
    fun firebrandMakesATappedTreasureWhenAHasteCreatureAttacks() = scenario {
        alice.battlefield(firebrand, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(firebrand, ogre)
        resolveAll()
        check(treasures(alice) == 1)
        expect { "Treasure Token".tapped shouldBe true }
    }

    // CR 701.21a (sacrifice as a cost), 608.2, 400.7: the exiled card may be played this turn
    @Test
    fun fixerSacrificesATreasureToExileAndPlayTheTopCard() = scenario {
        alice.battlefield(fixer, "Mountain")
        alice.hand(mint)
        alice.library("Island", "Island")
        makeTreasures()
        alice.activates(fixer, choosing = listOf("Treasure Token"))
        resolveAll()
        check(treasures(alice) == 2)
        expect { alice.library.names.size shouldBe 1 }
    }
}
