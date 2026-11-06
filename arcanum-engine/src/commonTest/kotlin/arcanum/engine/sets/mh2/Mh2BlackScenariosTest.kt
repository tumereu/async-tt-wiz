package arcanum.engine.sets.mh2

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Modern Horizons 2 cards of parser chunks 05–06 in play: the black cards. Trivial ones not played here: Scullery Imp (keywords and
 * madness, as Pit Mongrel), Sinister Sea Star (a surveil ability), Curse It All (overload as Wake and Stir, a destroy), Weary of Worlds
 * (an Aura's -4/-4 and landcycling).
 */
class Mh2BlackScenariosTest {
    private val s = Mh2Set
    private val p = Mh2Plain
    private val p1p1 = Mh2Plain.p1p1
    private val swamps = arrayOf("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")

    // ---- Chunk 05

    /** Unearth returns it with haste; its enters trigger shrinks the opponents' creatures. */
    // CR 702.84a
    @Test
    fun archdemonOfWoeUnearthed() = scenario {
        alice.battlefield(*swamps.take(5).toTypedArray())
        alice.graveyard(s.archdemonOfWoe)
        bob.battlefield(p.bear)
        alice.activates(s.archdemonOfWoe)
        playOut()
        expect {
            bob.graveyard has p.bear
            s.archdemonOfWoe hasKeyword Keyword.Haste
        }
    }

    /** Entering, the opponent sacrifices, discards and loses 3; Alice draws and gains 3. */
    @Test
    fun archonOfMaliceEnters() = scenario {
        alice.battlefield(*swamps)
        alice.hand(s.archonOfMalice)
        alice.library(p.ogre)
        bob.battlefield(p.bear)
        bob.hand(p.ogre)
        alice.casts(s.archonOfMalice)
        playOut("bob", p.bear, p.ogre)
        expect {
            bob.graveyard has p.bear
            bob.life shouldBe 17
            alice.life shouldBe 23
            alice.hand has p.ogre
        }
    }

    /** The additional cost may be a discard instead of a sacrifice. */
    // CR 601.2b
    @Test
    fun boneSliversWithADiscard() = scenario {
        alice.battlefield("Swamp")
        alice.hand(s.boneSlivers, p.ogre)
        bob.battlefield(p.giant)
        alice.casts(s.boneSlivers, target = p.giant, option = "Discard", choosing = listOf(p.ogre))
        playOut(p.ogre)
        expect {
            bob.graveyard has p.giant
            alice.graveyard has p.ogre
        }
    }

    /** A land that could produce {C} is destroyed. */
    // CR 106.7
    @Test
    fun shatterTheFrostOnAColorlessLand() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(s.shatterTheFrost)
        bob.battlefield(p.wastes, "Forest")
        alice.casts(s.shatterTheFrost, target = p.wastes)
        playOut()
        expect { bob.graveyard has p.wastes }
    }

    @Test
    fun shatterTheFrostCantTargetAForest() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(s.shatterTheFrost)
        bob.battlefield("Forest")
        kotlin.test.assertFailsWith<IllegalArgumentException> { alice.casts(s.shatterTheFrost) }
    }

    @Test
    fun cabalNoviceThreshold() = scenario {
        alice.battlefield(s.cabalNovice)
        alice.graveyard(p.ogre, p.ogre, p.ogre, p.ogre, p.ogre, p.ogre, p.ogre)
        expect { s.cabalNovice.toughness shouldBe 3 }
    }

    /** It returns from the graveyard to the hand; entering, it draws and loses 1. */
    @Test
    fun rattlingSoothsayerReturns() = scenario {
        alice.battlefield(*swamps.take(4).toTypedArray())
        alice.graveyard(s.rattlingSoothsayer)
        alice.activates(s.rattlingSoothsayer)
        playOut()
        expect { alice.hand has s.rattlingSoothsayer }
    }

    /** Cards going to an opponent's graveyard are exiled with a void counter; sacrificed, it plays one of them for free. */
    // CR 614.1a, 601.2a
    @Test
    fun dauthiNullwalkerVoids() = scenario {
        alice.battlefield(s.dauthiNullwalker)
        bob.battlefield("Swamp")
        bob.hand(p.doom)
        bob.battlefield(p.giant)
        alice.passes()
        bob.casts(p.doom, target = p.giant)
        playOut()
        expect {
            bob.exile has p.giant
            p.giant.counters(CounterKind.Named("void")) shouldBe 1
        }
    }

    /** Looking at four: one to the hand, the rest to the graveyard, life equal to the greatest power among them. */
    // CR 608.2h
    @Test
    fun refinedPalateGains() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(s.refinedPalate)
        alice.library(p.bear, p.giant, p.ogre, p.zap)
        alice.casts(s.refinedPalate)
        playOut(p.bear)
        expect {
            alice.hand has p.bear
            alice.life shouldBe 25
        }
    }

    @Test
    fun resoundingReturnTakesAllCopies() = scenario {
        alice.battlefield("Swamp")
        alice.hand(s.resoundingReturn)
        alice.graveyard(p.ogre, p.ogre, p.bear)
        alice.casts(s.resoundingReturn, target = p.ogre)
        playOut()
        expect {
            alice.hand.names.count { it == p.ogre } shouldBe 2
            alice.graveyard has p.bear
        }
    }

    @Test
    fun banquetOfReasonPings() = scenario {
        alice.battlefield(s.banquetOfReason, s.skyshiftProtist)
        alice.hand(p.ogre)
        alice.activates(s.skyshiftProtist, choosing = listOf(p.ogre))
        playOut("bob")
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    /** Life equal to the counters it had. */
    @Test
    fun stripTheEssenceCountsCounters() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(s.stripTheEssence)
        bob.battlefield(p.ogre, counters = listOf(p1p1 to 3))
        alice.casts(s.stripTheEssence, target = p.ogre)
        playOut()
        expect {
            bob.exile has p.ogre
            alice.life shouldBe 23
        }
    }

    @Test
    fun giltEdgeProwlerAfterADiscard() = scenario {
        alice.battlefield("Swamp", s.giltEdgeProwler, s.skyshiftProtist)
        alice.hand(p.ogre)
        alice.library(p.bear)
        alice.activates(s.skyshiftProtist, choosing = listOf(p.ogre))
        playOut()
        alice.activates(s.giltEdgeProwler)
        playOut()
        expect { alice.hand has p.bear }
    }

    /** Evoked, it is sacrificed, but a nonland card is taken from the revealed hand. */
    @Test
    fun lamentEvoked() = scenario {
        alice.hand(s.lament, p.knight)
        bob.hand(p.ogre, "Forest")
        alice.casts(s.lament, way = alternativeOf(s.lament, Keyword.Evoke.Kind), choosing = listOf(p.knight))
        playOut("bob", p.ogre)
        expect {
            bob.graveyard has p.ogre
            alice.graveyard has s.lament
        }
    }

    /** Discarded, madness casts it for its madness cost. */
    // CR 702.35a
    @Test
    fun pitMongrelMadness() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", s.skyshiftProtist)
        alice.hand(s.pitMongrel)
        alice.activates(s.skyshiftProtist, choosing = listOf(s.pitMongrel))
        playOut()
        expect { alice.battlefield has s.pitMongrel }
    }

    /** Sacrificing another creature, it explores. */
    // CR 701.44a
    @Test
    fun legionOutriderExplores() = scenario {
        alice.battlefield("Swamp", s.legionOutrider, p.bear)
        alice.library(p.ogre)
        alice.activates(s.legionOutrider, choosing = listOf(p.bear))
        playOut(may = false)
        expect { s.legionOutrider.counters(p1p1) shouldBe 1 }
    }

    /** Exploiting a creature destroys a creature with mana value 3 or less. */
    // CR 702.110b
    @Test
    fun repugnantArchivistExploits() = scenario {
        alice.battlefield(*swamps.take(5).toTypedArray(), p.bear)
        alice.hand(s.repugnantArchivist)
        bob.battlefield(p.ogre)
        alice.casts(s.repugnantArchivist)
        playOut(p.bear, p.ogre)
        expect {
            alice.graveyard has p.bear
            bob.graveyard has p.ogre
        }
    }

    /** A nontoken creature of Alice's dying makes a Zombie; an opponent's creature dying exiles it. */
    @Test
    fun magusOfTheSpanZombiesThenExile() = scenario {
        alice.battlefield("Swamp", "Swamp", s.magusOfTheSpan, p.bear)
        alice.hand(p.doom, p.doom)
        bob.battlefield(p.ogre)
        alice.casts(p.doom, target = p.bear)
        playOut()
        expect { alice.battlefield has "Zombie Token" }
        alice.casts(p.doom, target = p.ogre)
        playOut()
        expect { alice.exile has s.magusOfTheSpan }
    }

    // ---- Chunk 06

    /** Its power counts creature cards in all graveyards; each upkeep, that player discards. */
    @Test
    fun mortgoyfCountsAndDiscards() = scenario {
        alice.battlefield(s.mortgoyf)
        alice.graveyard(p.bear)
        bob.graveyard(p.ogre)
        alice.hand(p.zap)
        expect { s.mortgoyf.power shouldBe 2 }
        aliceUpkeep()
        playOut(p.zap)
        expect { alice.graveyard has p.zap }
    }

    @Test
    fun gravecallersFamiliarHellbent() = scenario {
        alice.battlefield(s.gravecallersFamiliar)
        expect { s.gravecallersFamiliar hasKeyword Keyword.Lifelink }
    }

    @Test
    fun huddledShamblerSquirrels() = scenario {
        alice.battlefield(s.huddledShambler, counters = listOf(p1p1 to 2))
        alice.hand(p.doom)
        alice.battlefield("Swamp")
        alice.casts(p.doom, target = s.huddledShambler)
        playOut()
        expect { alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 3 }
    }

    // CR 122.6
    @Test
    fun endureBeyondReturnsWithACounter() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(s.endureBeyond)
        alice.graveyard(p.ogre)
        alice.casts(s.endureBeyond, target = p.ogre)
        playOut()
        expect {
            alice.battlefield has p.ogre
            p.ogre.power shouldBe 2
        }
    }

    @Test
    fun unholyTutorFinds() = scenario {
        alice.hand(s.unholyTutor)
        alice.library(p.bear, p.giant)
        alice.castsWithoutCosts(s.unholyTutor)
        resolve()
        playOut(p.giant)
        expect { alice.hand has p.giant }
    }

    /** Converge with two colours: each opponent loses 2 and Alice gains 2. */
    @Test
    fun prismaticGourmandConverge() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(s.prismaticGourmand)
        alice.casts(s.prismaticGourmand, paying = listOf("Swamp", "Swamp", "Swamp", "Swamp", "Mountain"))
        playOut()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    // CR 702.61a
    @Test
    fun abruptDecreeEdict() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(s.abruptDecree)
        bob.battlefield(p.ogre)
        alice.casts(s.abruptDecree, target = "bob")
        playOut(p.ogre)
        expect { bob.graveyard has p.ogre }
    }

    /** Escaping with a flying counter. */
    // CR 702.138c
    @Test
    fun tizerusCourserEscapes() = scenario {
        alice.battlefield(*swamps.take(5).toTypedArray())
        alice.graveyard(s.tizerusCourser, p.ogre, p.ogre, p.ogre, p.ogre, p.ogre)
        alice.casts(s.tizerusCourser, choosing = listOf(p.ogre, p.ogre, p.ogre, p.ogre, p.ogre))
        playOut(options = listOf("a flying"))
        expect { s.tizerusCourser hasKeyword Keyword.Flying }
    }

    /** Kicked, the opponent discards two at random; their discards grow it. */
    @Test
    fun vourachKicked() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(s.vourachDreadChanter)
        bob.hand(p.ogre, p.bear)
        alice.casts(s.vourachDreadChanter, kicked = 1)
        playOut("bob")
        expect {
            bob.hand.size shouldBe 0
            s.vourachDreadChanter.counters(p1p1) shouldBe 2
        }
    }

    @Test
    fun vourachsHymnalTwoDiscards() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(s.vourachsHymnal)
        bob.hand(p.ogre, p.bear, p.zap)
        alice.casts(s.vourachsHymnal, target = "bob")
        playOut(p.ogre)
        expect {
            bob.graveyard has p.ogre
            bob.hand.size shouldBe 1
        }
    }

    /** Hellbent: -13/-13 instead. */
    @Test
    fun woefulPlungeHellbent() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(s.woefulPlunge)
        bob.battlefield(p.giant)
        alice.casts(s.woefulPlunge, target = p.giant)
        playOut()
        expect { bob.graveyard has p.giant }
    }

    // CR 700.5
    @Test
    fun netherworldRecluseDevotion() = scenario {
        alice.battlefield(*swamps.take(6).toTypedArray(), p.knight)
        alice.hand(s.netherworldRecluse)
        alice.casts(s.netherworldRecluse)
        playOut()
        expect { alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 3 }
    }

    @Test
    fun namelessGraveEntombs() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(s.namelessGrave)
        alice.library(p.bear, p.giant)
        alice.casts(s.namelessGrave)
        playOut(p.giant)
        expect { alice.graveyard has p.giant }
    }

    @Test
    fun verminGluttonDrains() = scenario {
        alice.battlefield(s.verminGlutton, p.bear)
        alice.activates(s.verminGlutton, choosing = listOf(p.bear))
        playOut()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    @Test
    fun foulInterrerEntombs() = scenario {
        alice.battlefield(*swamps.take(4).toTypedArray())
        alice.hand(s.foulInterrer)
        alice.library(p.bear, p.giant)
        alice.casts(s.foulInterrer)
        playOut(p.giant)
        expect { alice.graveyard has p.giant }
    }

    /** Exiling two cards from the graveyard, a reflexive trigger returns a creature card. */
    // CR 603.12
    @Test
    fun fledglingNecromancerReanimates() = scenario {
        alice.battlefield(*swamps.take(5).toTypedArray())
        alice.hand(s.fledglingNecromancer)
        alice.graveyard(p.giant, p.zap, p.notes)
        alice.casts(s.fledglingNecromancer)
        playOut(p.zap, p.notes, p.giant)
        expect { alice.battlefield has p.giant }
    }
}
