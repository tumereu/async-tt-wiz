package arcanum.engine.sets.fin

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.FinExtras.bear
import arcanum.engine.sets.fin.FinExtras.charm
import arcanum.engine.sets.fin.FinExtras.notes
import arcanum.engine.sets.fin.FinExtras.ogre
import arcanum.engine.sets.fin.FinExtras.p1p1
import arcanum.engine.sets.fin.FinExtras.stun
import arcanum.engine.sets.fin.FinExtras.zap
import kotlin.test.Test

/**
 * Final Fantasy chunk 06 in play: noncreature-spell triggers and "if at least four (eight) mana was spent to cast it" (CR 601.2h, intervening
 * ifs CR 603.4), the 0/1 Wizard tokens that ping on noncreature spells. Mystic Elder's, Queen of Brahm's and Ring of Incantation's Wizard
 * tokens are Ambushed by Mages' token (tested); Pella's Treasure and Brineclaw Raider's counter repeat Cinder Bomb's four-mana trigger.
 */
class FinWizardsScenariosTest {
    private val bigSpell = Cards.invent("Crystal Grand Notes", "{3}{U}", "Sorcery", "Draw a card.")
    private val hugeSpell = Cards.invent("Crystal Vast Notes", "{7}{U}", "Sorcery", "Draw a card.")
    private fun lands(land: String, n: Int) = Array(n) { land }

    @Test
    fun matronDrawsOnScryAndSurveil() = scenario {
        libraries()
        alice.battlefield(FinSet.matronArchonElder, FinSet.gerrandFallenKnight, "Mountain")
        alice.hand(zap)
        alice.casts(zap, target = "bob")
        resolveAll()
        choose()
        expect { alice.hand.size shouldBe 1 }
    }

    @Test
    fun ambushedByMagesEdictsAndMakesAPinger() = scenario {
        alice.battlefield(*lands("Swamp", 3), "Mountain")
        alice.hand(FinSet.ambushedByMages, zap)
        bob.battlefield(ogre)
        alice.casts(FinSet.ambushedByMages, target = "bob")
        resolveAll()
        alice.casts(zap, target = "bob")
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            bob.life shouldBe 17
        }
    }

    @Test
    fun shanaraGrowsByManaSpentAndDraws() = scenario {
        libraries()
        alice.battlefield(FinSet.shanaraTacticianMagician, *lands("Island", 4))
        alice.hand(bigSpell)
        alice.casts(bigSpell)
        resolveAll()
        expect {
            FinSet.shanaraTacticianMagician.power shouldBe 4
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun grimWaltzerBurnsOnNoncreatureSpells() = scenario {
        alice.battlefield(FinSet.grimWaltzerNo5, "Mountain")
        alice.hand(zap)
        alice.casts(zap, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun telmarMakesHeroesDrawsAndBlowsUpOnEightMana() = scenario {
        libraries()
        alice.battlefield(FinSet.telmarGreatSage, *lands("Island", 8))
        alice.hand(hugeSpell)
        alice.casts(hugeSpell)
        resolveAll()
        expect {
            alice.battlefield has "Hero Token"
            alice.hand.size shouldBe 3
            FinSet.telmarGreatSage.zone shouldBe Zone.Graveyard
            bob.life shouldBe 12
        }
    }

    @Test
    fun theGrandStagecraftAnimatesOnBigSpells() = scenario {
        libraries()
        alice.battlefield(FinSet.theGrandStagecraft, *lands("Island", 4))
        alice.hand(bigSpell)
        alice.casts(bigSpell)
        resolveAll()
        expect { (arcanum.api.CardType.Creature in FinSet.theGrandStagecraft.types) shouldBe true }
    }

    @Test
    fun mysticElderMakesAPinger() = scenario {
        alice.battlefield(*lands("Mountain", 3))
        alice.hand(FinSet.mysticElder)
        alice.casts(FinSet.mysticElder)
        resolveAll()
        expect { alice.battlefield has "Wizard Token" }
    }

    // CR 106.1: mana in any combination of two colours
    @Test
    fun viraAddsManaByPowerAndGrows() = scenario {
        alice.battlefield(FinSet.viraOrnitier, counters = listOf(p1p1 to 2))
        alice.hand(zap)
        alice.activates(FinSet.viraOrnitier)
        while (pending is arcanum.engine.decision.Decision.ChooseColor) answer(arcanum.engine.decision.Answer.ColorChoice(arcanum.api.Color.Red))
        alice.casts(zap, target = "bob")
        resolveAll()
        expect {
            FinSet.viraOrnitier.counters(p1p1) shouldBe 3
            bob.life shouldBe 17
        }
    }

    @Test
    fun queenOfBrahmMakesAWizardWhenAttacking() = scenario {
        libraries()
        alice.battlefield(FinSet.queenOfBrahm)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.queenOfBrahm)
        resolveAll()
        expect { alice.battlefield has "Wizard Token" }
    }

    @Test
    fun ulrosStunsOnFourAndGrowsOnEight() = scenario {
        libraries()
        alice.battlefield(FinSet.ulrosInkyNuisance, *lands("Island", 8))
        alice.hand(hugeSpell)
        bob.battlefield(ogre)
        alice.casts(hugeSpell)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            ogre.counters(stun) shouldBe 1
            FinSet.ulrosInkyNuisance.counters(p1p1) shouldBe 8
        }
    }

    @Test
    fun xandraGrowsWithTheGraveyard() = scenario {
        alice.battlefield(FinSet.xandraDarkMage)
        alice.graveyard(zap, notes, charm, bear)
        expect { FinSet.xandraDarkMage.power shouldBe 6 }
    }

    @Test
    fun ringOfIncantationDrawsAndLeadsWizards() = scenario {
        libraries()
        alice.battlefield(*lands("Swamp", 4), FinSet.mysticElder)
        alice.hand(FinSet.ringOfIncantation)
        alice.casts(FinSet.ringOfIncantation)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.life shouldBe 18
            FinSet.mysticElder.power shouldBe 2
            FinSet.mysticElder hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun cinderBombGrowsAndBlowsUp() = scenario {
        libraries()
        alice.battlefield(FinSet.cinderBomb, *lands("Island", 4))
        alice.hand(bigSpell)
        bob.battlefield(bear)
        alice.casts(bigSpell)
        resolveAll()
        alice.activates(FinSet.cinderBomb, target = bear)
        resolveAll()
        expect { bear.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun pellaMakesTreasureOnBigSpells() = scenario {
        libraries()
        alice.battlefield(FinSet.pellaShutterbug, *lands("Island", 4))
        alice.hand(bigSpell)
        alice.casts(bigSpell)
        resolveAll()
        expect { alice.battlefield has "Treasure Token" }
    }

    @Test
    fun brineclawRaiderCantBeBlocked() = scenario {
        libraries()
        alice.battlefield(FinSet.brineclawRaider, *lands("Island", 4))
        alice.hand(bigSpell)
        alice.casts(bigSpell)
        resolveAll()
        expect {
            FinSet.brineclawRaider.counters(p1p1) shouldBe 1
            FinSet.brineclawRaider hasMechanic { it is arcanum.api.Mechanic.CantBeBlocked }
        }
    }
}
