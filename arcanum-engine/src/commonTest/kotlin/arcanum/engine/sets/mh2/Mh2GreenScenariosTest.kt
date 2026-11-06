package arcanum.engine.sets.mh2

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Supertype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Modern Horizons 2 cards of parser chunks 09–10 in play: the green cards. Trivial ones not played here: Squirrelstorm and Prowling Pack's
 * storm (as Spreading Revolt), Pry Open (destroy and a Treasure), Lowly Hierarch (exalted and a mana ability), Jade Retaliator (bushido
 * only), Gem-Eyed Viper (a dies Treasure), Grove Strider (Food, landcycling), Rift Seeder (a mana ability, suspend), Groundmorph (a land
 * search, rebound as Godless Scavenging), Ageless Witness (an enters return, eternalize as Ageless Wyrm), Squirrel Monarch (a lord).
 */
class Mh2GreenScenariosTest {
    private val s = Mh2Set
    private val p = Mh2Plain
    private val p1p1 = Mh2Plain.p1p1
    private val forests = arrayOf("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")

    // ---- Chunk 09

    /** Choosing nonland: revealed until a nonland card, it goes to the hand, the rest to the bottom. */
    @Test
    fun bountifulReapingNonland() = scenario {
        alice.battlefield("Forest")
        alice.hand(s.bountifulReaping)
        alice.library("Island", "Island", p.ogre, p.bear)
        alice.casts(s.bountifulReaping)
        playOut(options = listOf("nonland"))
        expect {
            alice.hand has p.ogre
            alice.library.names.first() shouldBe p.bear
        }
    }

    /** A token copy isn't legendary; it enters with a counter for each other Ooze. */
    // CR 205.4
    @Test
    fun avieTokenIsntLegendary() = scenario {
        alice.battlefield(*forests.take(5).toTypedArray())
        alice.hand(p.zap, s.aviePrimordialOoze)
        alice.battlefield("Mountain")
        alice.casts(p.zap, target = "bob")
        playOut()
        alice.casts(s.aviePrimordialOoze)
        playOut()
        expect {
            alice.battlefield.names.count { it == s.aviePrimordialOoze } shouldBe 2
        }
    }

    /** Reinforce from the hand puts two counters on a creature. */
    // CR 702.77a
    @Test
    fun flaghideKrushokReinforces() = scenario {
        alice.battlefield("Forest", "Forest", p.bear)
        alice.hand(s.flaghideKrushok)
        alice.activates(s.flaghideKrushok, target = p.bear)
        playOut()
        expect { p.bear.counters(p1p1) shouldBe 2 }
    }

    /** The graveyard is shuffled into the library, and combat damage this turn is prevented. */
    // CR 615.1
    @Test
    fun hallowedReprieveFog() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(s.hallowedReprieve)
        alice.graveyard(p.ogre)
        bob.battlefield(p.giant)
        turn(active = bob, step = TurnStep.DeclareAttackers, priority = null)
        bob.attacks(p.giant)
        playOut()
        bob.passes()
        alice.casts(s.hallowedReprieve, target = "alice")
        playOut()
        advanceTo(TurnStep.CombatDamage)
        playOut()
        expect {
            alice.life shouldBe 20
            alice.library has p.ogre
        }
    }

    /** Tokens come with as many Squirrels. */
    // CR 614.1a
    @Test
    fun chitterjawAddsSquirrels() = scenario {
        alice.battlefield("Island", s.chitterjawSquirrelCaptain)
        alice.hand(s.firmTestimony)
        alice.casts(s.firmTestimony)
        playOut()
        expect { alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 2 }
    }

    /** Sacrificing a token at upkeep adds an acorn counter, and Squirrels get +1/+1 for each. */
    @Test
    fun acornspitterAcorns() = scenario {
        alice.battlefield(s.acornspitter, p.squirrel)
        alice.hand(s.firmTestimony)
        aliceUpkeep()
        alice.castsWithoutCosts(s.firmTestimony)
        playOut("Crab Token")
        expect { p.squirrel.power shouldBe 2 }
    }

    @Test
    fun acornspitterMakesSquirrels() = scenario {
        alice.battlefield("Forest", s.acornspitter)
        alice.activates(s.acornspitter)
        playOut()
        expect { alice.battlefield has "Squirrel Token" }
    }

    /** {1} less for each +1/+1 counter on creatures you control. */
    @Test
    fun deepwoodDwellerCheaperDraw() = scenario {
        alice.battlefield("Forest", "Forest", s.deepwoodDweller, p.bear, counters = listOf(p1p1 to 2))
        alice.library(p.ogre)
        alice.activates(s.deepwoodDweller, ability = 0)
        playOut()
        expect { alice.hand has p.ogre }
    }

    @Test
    fun duskshellCreeperTrample() = scenario {
        alice.battlefield("Forest", "Forest", p.bear)
        alice.hand(s.duskshellCreeper)
        alice.casts(s.duskshellCreeper)
        playOut(p.bear)
        expect { p.bear hasKeyword Keyword.Trample }
    }

    /** Evoked, the target player's graveyard goes to the bottom of their library. */
    @Test
    fun doggednessEvoked() = scenario {
        alice.hand(s.doggedness, p.elf)
        bob.graveyard(p.ogre)
        alice.casts(s.doggedness, way = alternativeOf(s.doggedness, Keyword.Evoke.Kind), choosing = listOf(p.elf))
        playOut("bob")
        expect { bob.library has p.ogre }
    }

    /** Having cast a creature spell and a noncreature spell, three tokens at the end step. */
    @Test
    fun fairyTributeThreeTokens() = scenario {
        alice.battlefield("Forest", "Forest", "Mountain", s.fairyTribute)
        alice.hand(p.elf, p.zap)
        alice.casts(p.elf)
        playOut()
        alice.casts(p.zap, target = "bob")
        playOut()
        advanceTo(TurnStep.End)
        playOut()
        expect {
            alice.battlefield has "Clue Token"
            alice.battlefield has "Food Token"
            alice.battlefield has "Treasure Token"
        }
    }

    /** Entwined: 5 damage to a flyer and +3/+3. */
    @Test
    fun thrivingBlowEntwined() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", p.bear)
        alice.hand(s.thrivingBlow)
        bob.battlefield(p.hawk)
        alice.casts(s.thrivingBlow, modes = listOf(0, 1), targets = listOf(p.hawk, p.bear), kicked = 1)
        playOut()
        expect {
            bob.graveyard has p.hawk
            p.bear.power shouldBe 5
        }
    }

    @Test
    fun bedrockBreakerEvoked() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(s.bedrockBreaker)
        bob.battlefield(p.charm)
        alice.casts(s.bedrockBreaker, way = alternativeOf(s.bedrockBreaker, Keyword.Evoke.Kind))
        playOut(p.charm)
        expect {
            bob.graveyard has p.charm
            alice.graveyard has s.bedrockBreaker
        }
    }

    /** Morbid: a creature died this turn, so it investigates. */
    @Test
    fun funnelwebHermitMorbid() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Swamp", "Swamp")
        alice.hand(p.doom, s.funnelwebHermit)
        bob.battlefield(p.bear)
        alice.casts(p.doom, target = p.bear)
        playOut()
        alice.casts(s.funnelwebHermit)
        playOut()
        expect { alice.battlefield has "Clue Token" }
    }

    /** Lands and spells from the graveyard; cards put into the graveyard are exiled instead. */
    @Test
    fun gaeasWishPlaysFromTheGraveyard() = scenario {
        alice.battlefield("Mountain")
        alice.hand(s.gaeasWish)
        alice.graveyard(p.zap, "Forest")
        alice.castsWithoutCosts(s.gaeasWish)
        resolve()
        expect { alice.exile has s.gaeasWish }
        alice.plays("Forest")
        alice.casts(p.zap, target = "bob")
        playOut()
        expect {
            alice.battlefield has "Forest"
            alice.exile has p.zap
        }
    }

    @Test
    fun shimmerBairnEatsAToken() = scenario {
        alice.battlefield("Island", s.shimmerBairn)
        alice.hand(s.firmTestimony)
        alice.casts(s.firmTestimony)
        playOut()
        alice.activates(s.shimmerBairn, choosing = listOf("Clue Token"))
        playOut()
        expect { s.shimmerBairn.power shouldBe 3 }
    }

    /** Converge with two colours: four counters. */
    @Test
    fun gleamingCreeperConverge() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Island")
        alice.hand(s.gleamingCreeper)
        alice.casts(s.gleamingCreeper, paying = listOf("Forest", "Forest", "Forest", "Forest", "Island"))
        playOut()
        expect { s.gleamingCreeper.counters(p1p1) shouldBe 4 }
    }

    @Test
    fun droveBalothMakesABeast() = scenario {
        alice.battlefield("Forest", s.droveBaloth)
        alice.hand(p.growth)
        alice.casts(p.growth, target = s.droveBaloth)
        playOut()
        expect { alice.battlefield has "Beast Token" }
    }

    // ---- Chunk 10

    /** {T}: X mana of one colour, X the number of enchantments. */
    @Test
    fun sanctumSpinnerMana() = scenario {
        alice.battlefield(s.sanctumSpinner, p.charm)
        alice.hand(p.bear)
        alice.casts(p.bear, paying = listOf(s.sanctumSpinner + ":G"))
        playOut()
        expect { alice.battlefield has p.bear }
    }

    // CR 702.100a
    @Test
    fun scurryingOakEvolves() = scenario {
        alice.battlefield("Forest", "Forest", s.scurryingOak)
        alice.hand(p.bear)
        alice.casts(p.bear)
        playOut()
        expect {
            s.scurryingOak.counters(p1p1) shouldBe 1
            alice.battlefield has "Squirrel Token"
        }
    }

    /** Proliferate, then a fight. */
    @Test
    fun scentOfDreadFights() = scenario {
        alice.battlefield("Forest", "Forest", p.giant)
        alice.hand(s.scentOfDread)
        bob.battlefield(p.ogre)
        alice.casts(s.scentOfDread, targets = listOf(p.giant, p.ogre))
        playOut()
        expect { bob.graveyard has p.ogre }
    }

    @Test
    fun squirrelRefugeReturns() = scenario {
        alice.battlefield("Forest", "Swamp", s.squirrelRefuge, p.bear)
        alice.hand(p.doom)
        alice.casts(p.doom, target = p.bear)
        playOut()
        expect { alice.hand has s.squirrelRefuge }
    }

    @Test
    fun sylvanHymnScries() = scenario {
        alice.battlefield("Forest", "Forest", s.sylvanHymn)
        alice.hand(p.bear)
        alice.library(p.ogre)
        alice.casts(p.bear)
        playOut()
        expect { p.bear.power shouldBe 3 }
    }

    /** {3} less for each other spell cast this turn; hexproof the turn it entered; excess damage to a planeswalker's controller. */
    // CR 702.19c
    @Test
    fun thravosTramplesOverPlaneswalkers() = scenario {
        alice.battlefield(s.thravosTempestsHowl)
        bob.battlefield(p.walker, counters = listOf(CounterKind.Loyalty to 3))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.thravosTempestsHowl, defender = p.walker)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == null) bob.blocks()
        advanceTo(TurnStep.CombatDamage)
        playOut()
        expect {
            bob.graveyard has p.walker
            bob.life shouldBe 16
        }
    }

    @Test
    fun thravosHexproofTheTurnItEntered() = scenario {
        alice.battlefield(*forests, *forests, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(s.thravosTempestsHowl)
        alice.casts(s.thravosTempestsHowl)
        playOut()
        expect { s.thravosTempestsHowl hasKeyword Keyword.Hexproof }
    }

    /** Landfall: a Food or a Treasure. */
    @Test
    fun untiringProvisionerLandfall() = scenario {
        alice.battlefield(s.untiringProvisioner)
        alice.hand("Forest")
        alice.plays("Forest")
        playOut(options = listOf("Treasure"))
        expect { alice.battlefield has "Treasure Token" }
    }

    /** Enrage: dealt damage, it proliferates. */
    @Test
    fun urbanKnifetoothEnrage() = scenario {
        alice.battlefield("Mountain", s.urbanKnifetooth, p.bear, counters = listOf(p1p1 to 1))
        alice.hand(p.ping)
        alice.casts(p.ping, target = s.urbanKnifetooth)
        playOut(p.bear, s.urbanKnifetooth)
        expect { p.bear.counters(p1p1) shouldBe 2 }
    }

    /** Two modes: Squirrels for a player, and life. */
    @Test
    fun verdantDecreeTwoModes() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(s.verdantDecree)
        alice.casts(s.verdantDecree, modes = listOf(0, 3), targets = listOf("alice", "alice"))
        playOut()
        expect {
            alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 2
            alice.life shouldBe 23
        }
    }

    /** Reinforce X from the hand: X counters. */
    // CR 702.77a, 107.3a
    @Test
    fun wrenhollowHydraReinforceX() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", p.bear)
        alice.hand(s.wrenhollowHydra)
        alice.activates(s.wrenhollowHydra, x = 3, target = p.bear)
        playOut()
        expect { p.bear.counters(p1p1) shouldBe 3 }
    }

    @Test
    fun wrenhollowHydraEntersWithX() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(s.wrenhollowHydra)
        alice.casts(s.wrenhollowHydra, x = 3)
        playOut()
        expect { s.wrenhollowHydra.counters(p1p1) shouldBe 3 }
    }
}
