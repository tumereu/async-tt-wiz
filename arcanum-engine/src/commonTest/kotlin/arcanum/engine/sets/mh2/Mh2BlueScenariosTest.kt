package arcanum.engine.sets.mh2

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Modern Horizons 2 cards of parser chunks 03–04 in play: the blue cards. Trivial ones not played here: Inner Voyage (draw three, basic
 * landcycling as Groundskeeper Colos), Package Myr (a sacrifice-to-draw ability), Mind Overseer (affinity as Glinting Glare, an enters
 * draw), Ironfin Leviathan (an artifact enters trigger untapping it, as Scrap Serpent's token trigger).
 */
class Mh2BlueScenariosTest {
    private val s = Mh2Set
    private val p = Mh2Plain
    private val p1p1 = Mh2Plain.p1p1

    /** Four card types in the graveyard: a land, a creature, an instant and a sorcery. */
    private val fourTypes = arrayOf("Island", Mh2Plain.bear, Mh2Plain.zap, Mh2Plain.notes)

    // ---- Chunk 03

    // CR 613.4d
    @Test
    fun skyshiftProtistSwitches() = scenario {
        alice.battlefield(s.skyshiftProtist)
        alice.hand(p.ogre)
        alice.activates(s.skyshiftProtist, choosing = listOf(p.ogre))
        playOut()
        expect {
            s.skyshiftProtist.power shouldBe 4
            s.skyshiftProtist.toughness shouldBe 2
        }
    }

    /** Sacrificing a token (a Treasure for mana) gives it flying. */
    @Test
    fun ladenSkyriggerFliesOnASacrifice() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(s.ladenSkyrigger)
        alice.casts(s.ladenSkyrigger)
        playOut()
        alice.activates("Treasure Token", color = arcanum.api.Color.Blue)
        playOut()
        expect { s.ladenSkyrigger hasKeyword Keyword.Flying }
    }

    /** Creatures lose all abilities; at the end step it is sacrificed. */
    // CR 613.1f
    @Test
    fun strippedBareRemovesAbilities() = scenario {
        alice.battlefield("Island", "Island")
        alice.library(p.ogre)
        alice.hand(s.strippedBare)
        bob.battlefield(p.hawk)
        alice.casts(s.strippedBare)
        playOut()
        expect {
            p.hawk lacksKeyword Keyword.Flying
            alice.hand has p.ogre
        }
        advanceTo(TurnStep.End)
        playOut()
        expect { alice.graveyard has s.strippedBare }
    }

    @Test
    fun etheriumWeaverOnABigSpell() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", s.etheriumWeaver)
        alice.hand(p.giant)
        alice.casts(p.giant)
        playOut()
        expect { alice.battlefield has "Thopter Token" }
    }

    // CR 604.3
    @Test
    fun filigreeStewardCountsArtifacts() = scenario {
        alice.battlefield(s.filigreeSteward, p.trinket)
        expect { s.filigreeSteward.power shouldBe 2 }
    }

    @Test
    fun tidehoundInvestigates() = scenario {
        alice.battlefield("Island", "Island", "Island", s.tidehound)
        alice.activates(s.tidehound)
        playOut()
        expect { alice.battlefield has "Clue Token" }
    }

    /** With four card types in the graveyard, delirium gives +1/+0. */
    @Test
    fun fetidSentinelDelirium() = scenario {
        alice.battlefield(s.fetidSentinel)
        alice.graveyard(*fourTypes)
        expect { s.fetidSentinel.power shouldBe 2 }
    }

    /** Cycled, each opponent mills four. */
    // CR 702.29c
    @Test
    fun shatteredReasonCycled() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(s.shatteredReason)
        alice.library(p.ogre)
        bob.library(p.ogre, p.ogre, p.ogre, p.ogre, p.bear)
        alice.activates(s.shatteredReason)
        playOut()
        expect {
            bob.graveyard.size shouldBe 4
            bob.library.names.first() shouldBe p.bear
        }
    }

    /** Channel with X 2: two target creatures gain flying. */
    // CR 107.3a
    @Test
    fun lanternlitDrifterChannel() = scenario {
        alice.battlefield("Island", "Island", "Island", p.bear, p.ogre)
        alice.hand(s.lanternlitDrifter)
        alice.activates(s.lanternlitDrifter, ability = 1, x = 2, targets = listOf(p.bear, p.ogre))
        playOut(p.bear, p.ogre)
        expect {
            p.bear hasKeyword Keyword.Flying
            p.ogre hasKeyword Keyword.Flying
            alice.graveyard has s.lanternlitDrifter
        }
    }

    @Test
    fun firmTestimonyCrabAndClue() = scenario {
        alice.battlefield("Island")
        alice.hand(s.firmTestimony)
        alice.casts(s.firmTestimony)
        playOut()
        expect {
            alice.battlefield has "Crab Token"
            alice.battlefield has "Clue Token"
        }
    }

    /** It puts a creature card from the opponent's library onto the battlefield under its caster's control. */
    // CR 701.23a
    @Test
    fun foreseenTreacheryStealsFromTheLibrary() = scenario {
        alice.hand(s.foreseenTreachery)
        bob.library(p.giant, p.bear)
        alice.castsWithoutCosts(s.foreseenTreachery, target = "bob")
        resolve()
        playOut(p.giant)
        expect {
            alice.battlefield has p.giant
            card(p.giant).controller shouldBe alice.id
        }
    }

    /** A token entering taps a permanent, which doesn't untap next time. */
    @Test
    fun scrapSerpentLocksDown() = scenario {
        alice.battlefield("Island", s.scrapSerpent)
        alice.hand(s.firmTestimony)
        bob.battlefield(p.ogre)
        alice.casts(s.firmTestimony)
        playOut(p.ogre)
        expect {
            p.ogre.tapped shouldBe true
            p.ogre hasMechanic { it == arcanum.api.Mechanic.DoesntUntap }
        }
    }

    /** Replicated once: two counters, each unless its controller pays {2}. */
    // CR 702.56a
    @Test
    fun lapseOfAttentionReplicated() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(s.lapseOfAttention)
        bob.battlefield("Island")
        bob.hand(p.notes)
        turn(active = bob)
        bob.casts(p.notes)
        bob.passes()
        alice.casts(s.lapseOfAttention, target = p.notes, kicked = 1)
        playOut(p.notes, may = false)
        expect { bob.graveyard has p.notes }
    }

    // CR 205.2a
    @Test
    fun wakingVisionsCountsTypes() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.graveyard(*fourTypes)
        alice.library(p.ogre, p.ogre, p.ogre, p.ogre, p.ogre)
        alice.hand(s.wakingVisions)
        alice.casts(s.wakingVisions)
        playOut()
        expect { alice.hand.size shouldBe 4 }
    }

    /** Delve exiles an instant and a sorcery: it enters with two counters, and an instant leaving the graveyard adds another. */
    // CR 702.66a
    @Test
    fun murkwaterTyrantDelve() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.graveyard(p.zap, p.notes)
        alice.hand(s.murkwaterTyrant)
        alice.casts(s.murkwaterTyrant, choosing = listOf(p.zap, p.notes))
        playOut()
        expect { s.murkwaterTyrant.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun arcaneErasureMillsOnDiscard() = scenario {
        alice.battlefield(s.arcaneErasure, s.skyshiftProtist)
        alice.hand(p.ogre)
        bob.library(p.bear, p.bear, p.ogre)
        alice.activates(s.skyshiftProtist, choosing = listOf(p.ogre))
        playOut()
        expect { bob.graveyard.size shouldBe 2 }
    }

    /** Becoming the target of a spell, it is sacrificed. */
    // CR 115.10
    @Test
    fun illusoryDreadjawSacrificedWhenTargeted() = scenario {
        alice.battlefield(s.illusoryDreadjaw)
        bob.battlefield("Mountain")
        bob.hand(p.ping)
        turn(active = bob)
        bob.casts(p.ping, target = s.illusoryDreadjaw)
        playOut()
        expect { alice.graveyard has s.illusoryDreadjaw }
    }

    // ---- Chunk 04

    /** Delirium's ability can be activated only with four card types in the graveyard. */
    @Test
    fun frenziedSeerDeliriumDraw() = scenario {
        alice.battlefield("Island", "Island", "Island", s.frenziedSeer)
        alice.graveyard(*fourTypes)
        alice.library(p.ogre)
        alice.activates(s.frenziedSeer, ability = 1)
        playOut()
        expect { alice.hand has p.ogre }
    }

    @Test
    fun readjustDrawsAfterADiscard() = scenario {
        alice.battlefield("Island", "Island", s.skyshiftProtist)
        alice.hand(s.readjust, p.ogre)
        alice.library(p.bear)
        bob.battlefield(p.giant)
        alice.activates(s.skyshiftProtist, choosing = listOf(p.ogre))
        playOut()
        alice.casts(s.readjust, target = p.giant)
        playOut()
        expect {
            bob.hand has p.giant
            alice.hand has p.bear
        }
    }

    /** Overloaded, each noncreature artifact you control becomes a 0/0 creature with four counters. */
    // CR 702.96b
    @Test
    fun wakeAndStirOverloaded() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", p.trinket, p.zeroTrinket)
        alice.hand(s.wakeAndStir)
        alice.casts(s.wakeAndStir, way = alternativeOf(s.wakeAndStir, Keyword.Overload.Kind))
        playOut()
        expect {
            p.trinket.power shouldBe 4
            p.zeroTrinket.toughness shouldBe 4
        }
    }

    @Test
    fun wakeAndStirOnOneArtifact() = scenario {
        alice.battlefield("Island", "Island", p.trinket)
        alice.hand(s.wakeAndStir)
        alice.casts(s.wakeAndStir, target = p.trinket)
        playOut()
        expect { p.trinket.power shouldBe 4 }
    }

    /** Tapping a land; islandwalk past a defender with an Island. */
    @Test
    fun rishadanDeckhandTapsALand() = scenario {
        alice.battlefield("Island", s.rishadanDeckhand)
        bob.battlefield("Plains")
        alice.activates(s.rishadanDeckhand, target = "Plains")
        playOut()
        expect { "Plains".tapped shouldBe true }
    }

    /** The two halves: one returns an instant from the graveyard, the other taps two creatures. */
    // CR 709.3
    @Test
    fun spokenFinishedHalves() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.graveyard(p.zap)
        alice.hand(s.spokenFinished)
        alice.casts(s.spokenFinished, way = CastWay(face = 0), target = p.zap)
        playOut()
        expect { alice.hand has p.zap }
    }

    @Test
    fun spokenFinishedTaps() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(s.spokenFinished)
        bob.battlefield(p.ogre, p.bear)
        alice.casts(s.spokenFinished, way = CastWay(face = 1), targets = listOf(p.ogre, p.bear))
        playOut()
        expect {
            p.ogre.tapped shouldBe true
            p.bear.tapped shouldBe true
        }
    }

    @Test
    fun scuttlesurgeCrabsWithDelirium() = scenario {
        alice.battlefield("Island", "Island", s.scuttlesurge)
        alice.graveyard(*fourTypes)
        alice.hand(p.ogre)
        alice.activates(s.scuttlesurge, choosing = listOf(p.ogre))
        playOut()
        expect { "Crab Token".power shouldBe 1 }
    }

    /** Third from the top of its owner's library. */
    // CR 401.4
    @Test
    fun fracturedPrideTucks() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", s.fracturedPride)
        bob.battlefield(p.giant)
        attach(s.fracturedPride, p.giant)
        bob.library(p.bear, p.bear, p.bear)
        expect { p.giant.power shouldBe 2 }
        alice.activates(s.fracturedPride)
        playOut()
        expect { bob.library.names[2] shouldBe p.giant }
    }

    /** With a token, it taps the creature and scries; the creature stays tapped. */
    @Test
    fun suchSparkleWithAToken() = scenario {
        alice.battlefield("Island", "Island", "Island", "Clue Token".let { p.trinket })
        alice.hand(s.suchSparkle)
        alice.library(p.ogre)
        bob.battlefield(p.giant)
        alice.casts(s.suchSparkle, target = p.giant)
        playOut()
        expect { p.giant hasMechanic { it == arcanum.api.Mechanic.DoesntUntap } }
    }

    /** It makes a Squirrel and a Crab; dying, it copies a token. */
    // CR 707.2
    @Test
    fun sampleHoarderCopiesAToken() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(s.sampleHoarder)
        bob.battlefield("Swamp")
        bob.hand(p.doom)
        alice.casts(s.sampleHoarder)
        playOut()
        alice.passes()
        bob.casts(p.doom, target = s.sampleHoarder)
        playOut("Crab Token")
        expect { alice.battlefield.names.count { it == "Crab Token" } shouldBe 2 }
    }

    @Test
    fun walkBetweenBouncesTwo() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(s.walkBetween)
        bob.battlefield(p.ogre, p.bear)
        alice.casts(s.walkBetween, targets = listOf(p.ogre, p.bear))
        playOut()
        expect {
            bob.hand has p.ogre
            bob.hand has p.bear
        }
    }

    /** A creature spell put on the bottom of its owner's library. */
    @Test
    fun finesseBottomsASpell() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(s.finesse)
        bob.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        bob.hand(p.giant)
        bob.library(p.bear)
        turn(active = bob)
        bob.casts(p.giant)
        bob.passes()
        alice.casts(s.finesse)
        playOut(p.giant, options = listOf("bottom"))
        expect { bob.library.names.last() shouldBe p.giant }
    }

    /** Exiled with two time counters, a creature without suspend gains it. */
    // CR 702.62b
    @Test
    fun holdInAbeyanceSuspendsACreature() = scenario {
        alice.battlefield("Island")
        alice.hand(s.holdInAbeyance)
        bob.battlefield(p.giant)
        alice.casts(s.holdInAbeyance, target = p.giant)
        playOut()
        expect {
            bob.exile has p.giant
            p.giant.counters(CounterKind.Time) shouldBe 2
            p.giant hasKeyword Keyword.Suspend.Kind
        }
    }

    /** With two other Merfolk it is indestructible; other Merfolk have ward {1}; it draws on attacking. */
    // CR 702.21a
    @Test
    fun ysvelinLord() = scenario {
        alice.battlefield(s.ysvelinOfTideAndSky, p.merfolk, p.merfolk)
        alice.library(p.ogre)
        expect {
            s.ysvelinOfTideAndSky hasKeyword Keyword.Indestructible
            p.merfolk hasKeyword Keyword.Ward.Kind
        }
        unblocked(s.ysvelinOfTideAndSky)
        expect { alice.hand has p.ogre }
    }

    /** Converge: three colours spent make three Thopters. */
    @Test
    fun clearTheHeavensConverge() = scenario {
        alice.battlefield("Island", "Island", "Plains", "Swamp")
        alice.hand(s.clearTheHeavens)
        alice.casts(s.clearTheHeavens, x = 2, paying = listOf("Island", "Island", "Plains", "Swamp"))
        playOut()
        expect { alice.battlefield.names.count { it == "Thopter Token" } shouldBe 3 }
    }

    /** Kicked, a land becomes an Island for as long as it stays; it gets +1/+1 with an opponent's Island. */
    // CR 305.7
    @Test
    fun currentSculptorKicked() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(s.currentSculptor)
        bob.battlefield("Mountain")
        alice.casts(s.currentSculptor, kicked = 1)
        playOut("Mountain")
        expect {
            assertTrue(card("Mountain").view.characteristics!!.subtypes == listOf(Subtype("Island")), "an Island")
            s.currentSculptor.power shouldBe 2
        }
    }

    @Test
    fun vedalkenInterloperMetalcraft() = scenario {
        alice.battlefield(s.vedalkenInterloper, p.trinket, p.zeroTrinket, p.golem)
        expect { s.vedalkenInterloper.power shouldBe 2 }
    }
}
