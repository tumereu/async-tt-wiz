package arcanum.engine.sets.tla

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.tla.TlaExtras.bear
import arcanum.engine.sets.tla.TlaExtras.brute
import arcanum.engine.sets.tla.TlaExtras.flier
import arcanum.engine.sets.tla.TlaExtras.notes
import arcanum.engine.sets.tla.TlaExtras.ogre
import arcanum.engine.sets.tla.TlaExtras.p1p1
import arcanum.engine.sets.tla.TlaExtras.trinket
import arcanum.engine.sets.tla.TlaExtras.zap
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunks 15 and 16 cards in play: the black-and-red sacrifice and Clue cards, damage boosts and the green
 * power-4 cards. Thunderbolt Jab (3 damage), Weasel Gecko (1 damage as it enters, mountaincycling), Silent Bow Archers (a loot as it
 * enters) and Fang-Tusk Elk-Panther (reach, forestcycling) repeat tested templates and covered keywords.
 */
class TlaRedGreenScenariosTest {
    private val sac = Cards.invent("Offering Stone", "{1}", "Artifact", "{T}, Sacrifice this artifact: You gain 1 life.")
    private val looter = Cards.invent("Card Sage", "{U}", "Instant", "Draw two cards.")
    private val ox = Cards.invent("Ox Moose", "{2}{G}", "Creature — Ox Elk Beast", "", "1/1")

    // ---- chunk 15

    /** Enters: 1 life and a Clue; attacking after two sacrifices puts two counters on an attacker. */
    @Test
    fun relentlessHunt() = scenario {
        alice.battlefield("Swamp", "Swamp", bear, sac, TlaExtras.trinket)
        alice.hand(TlaSet.relentlessHunt)
        alice.casts(TlaSet.relentlessHunt)
        resolveAll()
        expect {
            alice.life shouldBe 19
            alice.battlefield has "Clue Token"
        }
        alice.activates(sac)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        targets(bear)
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    /** End step after a sacrifice: a token copy of the airship. */
    @Test
    fun cinderbirdFleetAirship() = scenario {
        alice.battlefield(TlaSet.cinderbirdFleetAirship, sac)
        alice.activates(sac)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Cinderbird Fleet Airship" } shouldBe 2 }
    }

    @Test
    fun corsairHawkers() = scenario {
        alice.battlefield(TlaSet.corsairHawkers, sac)
        alice.activates(sac)
        resolveAll()
        expect { TlaSet.corsairHawkers.counters(p1p1) shouldBe 1 }
    }

    /** A creature card exiled from a graveyard makes a Clue; the second draw drains 1. */
    @Test
    fun crowHawk() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Island")
        alice.hand(TlaSet.crowHawk, looter)
        alice.library(notes, notes)
        bob.graveyard(bear)
        alice.casts(TlaSet.crowHawk, paying = listOf("Swamp", "Swamp", "Swamp"))
        resolveAll()
        targets(bear)
        expect {
            bear.zone shouldBe Zone.Exile
            alice.battlefield has "Clue Token"
        }
        alice.casts(looter)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    /** A damaged creature exiled: a Clue too. */
    @Test
    fun betrayedForCoin() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(TlaSet.betrayedForCoin, zap)
        bob.battlefield(brute)
        alice.casts(zap, target = brute)
        resolveAll()
        alice.casts(TlaSet.betrayedForCoin, target = brute)
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Exile
            alice.battlefield has "Clue Token"
        }
    }

    /** Targeting a flier, it costs {1}{B}; the flier gets -5/-3. */
    @Test
    fun bogsnareTrapOnAFlier() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(TlaSet.bogsnareTrap)
        bob.battlefield(flier)
        alice.casts(TlaSet.bogsnareTrap, target = flier)
        resolveAll()
        expect { flier.zone shouldBe Zone.Graveyard }
    }

    /** Second draw: pay {B} to return it from the graveyard with a finality counter. */
    @Test
    fun houndbatReturns() = scenario {
        alice.battlefield("Swamp", "Island")
        alice.graveyard(TlaSet.houndbat)
        alice.hand(looter)
        alice.library(notes, notes)
        alice.casts(looter)
        resolveAll()
        yes()
        pay()
        expect {
            TlaSet.houndbat.zone shouldBe Zone.Battlefield
            TlaSet.houndbat.counters(CounterKind.Finality) shouldBe 1
        }
    }

    @Test
    fun banishedHeirsResolveKicked() = scenario {
        alice.battlefield(*Array(5) { "Swamp" })
        alice.hand(TlaSet.banishedHeirsResolve)
        alice.graveyard(brute)
        alice.casts(TlaSet.banishedHeirsResolve, target = brute, kicked = 1)
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Battlefield
            brute.tapped shouldBe true
        }
    }

    @Test
    fun hogUQuill() = scenario {
        alice.battlefield(TlaSet.hogUQuill, "Island")
        alice.hand(notes)
        alice.library(notes)
        alice.casts(notes)
        resolveAll()
        expect { TlaSet.hogUQuill.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun oldKingsBashBothModes() = scenario {
        alice.battlefield(*Array(8) { "Mountain" })
        alice.hand(TlaSet.oldKingsBash, TlaSet.oldKingsBash)
        bob.battlefield(brute, TlaSet.emberLegionPalace)
        alice.casts(TlaSet.oldKingsBash, modes = listOf(0), target = brute)
        resolveAll()
        alice.casts(TlaSet.oldKingsBash, modes = listOf(1), target = TlaSet.emberLegionPalace)
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Graveyard
            TlaSet.emberLegionPalace.zone shouldBe Zone.Graveyard
        }
    }

    /** The permanent's controller takes 4 damage rather than lose it. */
    // CR 118.12
    @Test
    fun blastmindAssassin() = scenario {
        alice.battlefield(TlaSet.blastmindAssassin)
        bob.battlefield(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.blastmindAssassin)
        resolveAll()
        targets(ogre)
        // the opponent chooses the damage
        answer(Answer.Option("damage"))
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            bob.life shouldBe 16
        }
    }

    @Test
    fun craftyGambit() = scenario {
        alice.battlefield(bear, "Mountain", "Mountain")
        alice.hand(TlaSet.craftyGambit)
        alice.casts(TlaSet.craftyGambit, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            alice.battlefield has "Clue Token"
        }
    }

    @Test
    fun defectorsPupil() = scenario {
        alice.battlefield(TlaSet.defectorsPupil, bear)
        alice.activates(TlaSet.defectorsPupil, target = bear)
        resolveAll()
        expect { bear hasMechanic { it == arcanum.api.Mechanic.CantBeBlocked } }
    }

    /** Two fire counters: a 2-damage spell deals 4 to the opponent. */
    // CR 614.1a
    @Test
    fun destinedBlaze() = scenario {
        alice.battlefield(*Array(6) { "Mountain" })
        alice.hand(TlaSet.destinedBlaze, zap)
        alice.casts(TlaSet.destinedBlaze, x = 2, paying = List(5) { "Mountain" })
        resolveAll()
        expect { TlaSet.destinedBlaze.counters(CounterKind.Named("fire")) shouldBe 2 }
        alice.casts(zap, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun emberLegionRaider() = scenario {
        alice.battlefield(bear, *Array(4) { "Mountain" })
        alice.hand(TlaSet.emberLegionRaider)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.PostcombatMain)
        alice.casts(TlaSet.emberLegionRaider)
        resolveAll()
        expect { alice.battlefield has "Clue Token" }
    }

    /** Kicked: their creature is yours until end of turn, untapped and hasty. */
    // CR 702.33d
    @Test
    fun rebelsMindBendingKicked() = scenario {
        alice.battlefield(*Array(4) { "Mountain" })
        alice.hand(TlaSet.rebelsMindBending)
        bob.battlefield(ogre, tapped = true)
        alice.casts(TlaSet.rebelsMindBending, target = ogre, kicked = 1)
        resolveAll()
        expect {
            alice.battlefield has ogre
            ogre.tapped shouldBe false
            ogre hasKeyword Keyword.Haste
            alice.battlefield has "Clue Token"
        }
    }

    /** Exhaust: a double strike counter. */
    @Test
    fun ilsaJadedEdge() = scenario {
        alice.battlefield(TlaSet.ilsaJadedEdge, "Mountain", "Mountain", "Mountain")
        alice.activates(TlaSet.ilsaJadedEdge)
        resolveAll()
        expect { TlaSet.ilsaJadedEdge hasKeyword Keyword.DoubleStrike }
    }

    // ---- chunk 16

    /** It can attack only with another creature of power 4 or greater. */
    @Test
    fun pantherPangolinNeedsABigFriend() = scenario {
        alice.battlefield(TlaSet.pantherPangolin, brute)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.pantherPangolin)
        advanceTo(TurnStep.CombatDamage)
        expect { bob.life shouldBe 16 }
    }

    /** Flashed in, it attaches and gives double strike. */
    @Test
    fun pairedSabers() = scenario {
        alice.battlefield(bear, "Mountain", "Mountain", "Mountain")
        alice.hand(TlaSet.pairedSabers)
        alice.casts(TlaSet.pairedSabers)
        resolveAll()
        targets(bear)
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.DoubleStrike
        }
    }

    @Test
    fun junaArtfulTumbler() = scenario {
        alice.battlefield(TlaSet.junaArtfulTumbler, "Mountain")
        bob.battlefield(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.junaArtfulTumbler)
        resolveAll()
        yes()
        pay()
        targets(ogre)
        expect { ogre hasMechanic { it is arcanum.api.Mechanic.CantBlock } }
    }

    /** Three fire counters make it a creature. */
    @Test
    fun siegeBalloon() = scenario {
        alice.battlefield(TlaSet.siegeBalloon, "Mountain", "Mountain", "Mountain")
        repeat(3) { alice.activates(TlaSet.siegeBalloon); resolveAll() }
        expect { TlaSet.siegeBalloon.types.contains(arcanum.api.CardType.Creature) shouldBe true }
    }

    /** +1/+1 for each creature card in the graveyard; dying, it mills its power and returns a creature and itself. */
    // CR 603.10a
    @Test
    fun heraldsDestiny() = scenario {
        alice.battlefield(bear, "Forest", "Forest", "Forest", "Forest", "Mountain", "Mountain")
        alice.graveyard(ogre)
        alice.hand(TlaSet.heraldsDestiny, TlaSet.thunderboltJab)
        alice.library(notes, brute, notes)
        alice.casts(TlaSet.heraldsDestiny, target = bear, paying = List(4) { "Forest" })
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear.subtypes.contains(Subtype("Avatar")) shouldBe true
        }
        alice.casts(TlaSet.thunderboltJab, target = bear)
        resolveAll()
        choose(brute)
        expect {
            alice.hand has TlaSet.heraldsDestiny
            alice.battlefield has brute
        }
    }

    /** Non-Humans get +1/+1 for each creature type. */
    @Test
    fun dutifulMenagerist() = scenario {
        alice.battlefield(TlaSet.dutifulMenagerist, ox)
        expect {
            ox.power shouldBe 4
            TlaSet.dutifulMenagerist.power shouldBe 4
        }
    }

    /** A 4/4 Bear token; when it attacks, a basic land enters tapped. */
    @Test
    fun theStoneSovereign() = scenario {
        alice.battlefield(TlaSet.theStoneSovereign, brute)
        alice.library("Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(brute)
        resolveAll()
        choose("Forest")
        expect { "Forest".tapped shouldBe true }
    }

    @Test
    fun theStoneSovereignBear() = scenario {
        alice.battlefield(*Array(4) { "Forest" })
        alice.hand(TlaSet.theStoneSovereign)
        alice.casts(TlaSet.theStoneSovereign)
        resolveAll()
        expect { "Bear Token".power shouldBe 4 }
    }

    /** Counters on each creature; a power-4 creature can't be blocked by more than one. */
    @Test
    fun gumbo() = scenario {
        alice.battlefield(ogre, *Array(6) { "Forest" })
        alice.hand(TlaSet.gumboTheOldKingsPal)
        alice.casts(TlaSet.gumboTheOldKingsPal)
        resolveAll()
        expect {
            ogre.counters(p1p1) shouldBe 1
            ogre hasMechanic { it is arcanum.api.Mechanic.MaximumBlockers }
        }
    }

    /** No land milled: a counter. */
    @Test
    fun emuPony() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(TlaSet.emuPony)
        alice.library(notes, notes, notes)
        alice.casts(TlaSet.emuPony)
        resolveAll()
        choose()
        expect { TlaSet.emuPony.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun emuPonyTakesALand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(TlaSet.emuPony)
        alice.library(notes, "Island", notes)
        alice.casts(TlaSet.emuPony)
        resolveAll()
        choose("Island")
        expect {
            alice.hand has "Island"
            TlaSet.emuPony.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun columnVault() = scenario {
        alice.battlefield("Forest")
        alice.battlefield(bear, tapped = true)
        alice.hand(TlaSet.columnVault)
        alice.casts(TlaSet.columnVault, target = bear)
        resolveAll()
        expect {
            bear.tapped shouldBe false
            bear hasKeyword Keyword.Reach
            bear.power shouldBe 4
        }
    }

    @Test
    fun rowdySpectatorsWithABigCreature() = scenario {
        alice.battlefield(TlaSet.rowdySpectators, brute)
        alice.activates(TlaSet.rowdySpectators)
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun stonyRetort() = scenario {
        alice.battlefield(brute, "Forest", "Forest")
        alice.hand(TlaSet.stonyRetort)
        bob.battlefield(ogre)
        alice.casts(TlaSet.stonyRetort, targets = listOf(brute, ogre))
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            brute.view.obj?.damage shouldBe 0
        }
    }

    @Test
    fun tortoiseGoose() = scenario {
        alice.battlefield(TlaSet.tortoiseGoose, "Forest", "Forest", "Forest")
        alice.activates(TlaSet.tortoiseGoose)
        resolveAll()
        expect {
            TlaSet.tortoiseGoose.power shouldBe 4
            TlaSet.tortoiseGoose hasKeyword Keyword.Trample
        }
    }

    /** Sacrificing its Food: a basic land tapped, and the Vendor goes to the bottom of the library. */
    @Test
    fun haplessTurnipVendor() = scenario {
        alice.battlefield(*Array(4) { "Forest" })
        alice.hand(TlaSet.haplessTurnipVendor)
        alice.library("Island")
        alice.casts(TlaSet.haplessTurnipVendor, paying = listOf("Forest", "Forest"))
        resolveAll()
        alice.activates("Food Token")
        resolveAll()
        yes()
        choose("Island")
        expect {
            "Island".tapped shouldBe true
            alice.library has TlaSet.haplessTurnipVendor
            alice.life shouldBe 23
        }
    }
}
