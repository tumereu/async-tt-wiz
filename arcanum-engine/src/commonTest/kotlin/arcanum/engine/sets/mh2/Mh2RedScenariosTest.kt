package arcanum.engine.sets.mh2

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Modern Horizons 2 cards of parser chunks 07–08 in play: the red cards. Trivial ones not played here: Arcwired Slasher (modular as Arcwired
 * Template, riot), Greater Behemoth (suspend as Rekindled Conviction), Hit the Jackpot (a Treasure, flashback), Insurrectionist (an
 * enters return and madness, as Pit Mongrel), Skophos Ravager (during your turn +2/+0, madness), Cinder Strider (affinity, a sacrifice
 * ping), Flaring Rootwalla (a once-each-turn pump, madness {0}), Viashino Whipclaw (haste for creatures).
 */
class Mh2RedScenariosTest {
    private val s = Mh2Set
    private val p = Mh2Plain
    private val p1p1 = Mh2Plain.p1p1
    private val mountains = arrayOf("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
    private val fourTypes = arrayOf("Island", Mh2Plain.bear, Mh2Plain.zap, Mh2Plain.notes)

    // ---- Chunk 07

    /** The second spell of the turn puts a counter on it, the first doesn't. */
    @Test
    fun arcwiredTrackerAfterTheFirstSpell() = scenario {
        alice.battlefield("Mountain", "Mountain", s.arcwiredTracker, counters = listOf(p1p1 to 2))
        alice.hand(p.zap, p.zap)
        alice.casts(p.zap, target = "bob")
        playOut()
        alice.casts(p.zap, target = "bob")
        playOut()
        expect { s.arcwiredTracker.counters(p1p1) shouldBe 3 }
    }

    @Test
    fun arcwiredWhelpPumps() = scenario {
        alice.battlefield("Mountain", s.arcwiredWhelp, counters = listOf(p1p1 to 2))
        alice.activates(s.arcwiredWhelp)
        playOut()
        expect { s.arcwiredWhelp.power shouldBe 3 }
    }

    @Test
    fun battleSchemePumpsAtCombat() = scenario {
        alice.battlefield(s.battleScheme, p.bear)
        turn(step = TurnStep.BeginningOfCombat)
        beginStep(TurnStep.BeginningOfCombat)
        playOut(p.bear)
        expect { p.bear.power shouldBe 4 }
    }

    /** With delirium the spell has cascade. */
    // CR 702.85a
    @Test
    fun bloodbraidRaiderCascades() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard(*fourTypes)
        alice.library(p.ogre, p.elf)
        alice.hand(s.bloodbraidRaider)
        alice.casts(s.bloodbraidRaider)
        playOut()
        expect { alice.battlefield has p.elf }
    }

    /** Sacrificing an artifact, one mode exiles the top card to play until the end of the next turn. */
    @Test
    fun breyasPupilModes() = scenario {
        alice.battlefield(s.breyasPupil, p.trinket, p.bear)
        alice.library(p.ogre)
        alice.activates(s.breyasPupil, modes = listOf(1), target = p.bear, choosing = listOf(p.trinket))
        playOut()
        expect { p.bear.power shouldBe 4 }
    }

    /** It reveals until a nonland card and deals damage equal to its mana value. */
    // CR 603.12
    @Test
    fun measuredBlastReveals() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(s.measuredBlast)
        alice.library("Forest", p.giant)
        alice.casts(s.measuredBlast)
        playOut("bob")
        expect { bob.life shouldBe 15 }
    }

    /** The third spell each turn puts a counter on it and it deals damage equal to its power. */
    @Test
    fun captainRillaVaneOnTheThirdSpell() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", s.captainRillaVane)
        alice.hand(p.zap, p.zap, p.zap)
        repeat(3) {
            alice.casts(p.zap, target = "bob")
            playOut("bob")
        }
        expect {
            s.captainRillaVane.counters(p1p1) shouldBe 1
            bob.life shouldBe 10
        }
    }

    /** It takes a spell with a single target, copies it, and the targets are chosen again at random, never Alice or hers. */
    // CR 115.7
    @Test
    fun cooksKissStealsASpell() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", p.bear)
        alice.hand(s.cooksKiss)
        bob.battlefield("Mountain", p.ogre)
        bob.hand(p.zap)
        turn(active = bob)
        bob.casts(p.zap, target = p.bear)
        bob.passes()
        alice.casts(s.cooksKiss, target = p.zap)
        playOut()
        expect {
            alice.battlefield has p.bear
            alice.life shouldBe 20
        }
    }

    @Test
    fun dragonsWrathConduitSurveils() = scenario {
        alice.battlefield("Mountain", s.dragonsWrathConduit)
        alice.hand(p.zap)
        alice.library(p.ogre)
        alice.casts(p.zap, target = "bob")
        playOut()
        expect { s.dragonsWrathConduit.power shouldBe 1 }
    }

    /** Rebound: exiled, it is cast again at the next upkeep. */
    // CR 702.88a
    @Test
    fun godlessScavengingRebounds() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(s.godlessScavenging, p.ogre)
        alice.library(p.bear)
        alice.casts(s.godlessScavenging)
        playOut(p.ogre)
        expect {
            alice.exile has s.godlessScavenging
            alice.hand has p.bear
        }
    }

    @Test
    fun swiftFeralHalves() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(s.swiftFeral)
        bob.battlefield(p.bear, p.hawk)
        alice.casts(s.swiftFeral, way = CastWay(face = 1))
        playOut()
        expect {
            bob.graveyard has p.bear
            bob.battlefield has p.hawk
        }
    }

    @Test
    fun fireRushHitsPlaneswalkers() = scenario {
        alice.battlefield(s.fireRush)
        bob.battlefield(p.walker)
        turn(step = TurnStep.End)
        beginStep(TurnStep.End)
        playOut()
        expect { bob.graveyard has p.walker }
    }

    /** Multikicked twice it enters with two counters and deals damage equal to its power. */
    // CR 702.33g
    @Test
    fun flametongueCubMultikicked() = scenario {
        alice.battlefield(*mountains)
        alice.hand(s.flametongueCub)
        bob.battlefield(p.giant)
        alice.casts(s.flametongueCub, kicked = 2)
        playOut(p.giant)
        expect {
            s.flametongueCub.counters(p1p1) shouldBe 2
            card(p.giant).view.obj?.damage shouldBe 4
        }
    }

    /** Evoked, its 4 damage is divided among two creatures. */
    // CR 601.2d
    @Test
    fun cholerEvokedDivides() = scenario {
        alice.hand(s.choler, p.ogre)
        bob.battlefield(p.bear, p.elf)
        alice.casts(s.choler, way = alternativeOf(s.choler, Keyword.Evoke.Kind), choosing = listOf(p.ogre))
        playOut(p.bear, p.elf)
        expect {
            bob.graveyard has p.bear
            bob.graveyard has p.elf
        }
    }

    /** The exiled card can be played only during the next turn. */
    @Test
    fun galvanicHandoffNextTurn() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(s.galvanicHandoff)
        alice.library(p.zap)
        alice.casts(s.galvanicHandoff)
        playOut()
        expect { alice.exile has p.zap }
        assertTrue(arcanum.engine.Engine.options(state, alice.id).filterIsInstance<arcanum.engine.input.Input.Cast>().none { it.card == card(p.zap) }, "not this turn")
    }

    // ---- Chunk 08

    /** Its permanents are shuffled away and as many cards revealed; the permanent cards among them come onto the battlefield. */
    // CR 701.24a
    @Test
    fun glanceAtTomorrowResets() = scenario {
        alice.battlefield(p.bear, p.charm)
        alice.hand(s.glanceAtTomorrow)
        alice.castsWithoutCosts(s.glanceAtTomorrow)
        resolve()
        playOut()
        expect { alice.battlefield.size shouldBe 2 }
    }

    /** Coins won make Goblins tapped and attacking. */
    // CR 705
    @Test
    fun goblinSnarerunnerFlips() = scenario {
        alice.battlefield(s.goblinSnarerunner)
        random(Answer.CoinFlip(true), Answer.CoinFlip(false), Answer.CoinFlip(true))
        unblocked(s.goblinSnarerunner)
        expect { alice.battlefield.names.count { it == "Goblin Token" } shouldBe 2 }
    }

    @Test
    fun goredFanaticDelirium() = scenario {
        alice.battlefield(s.goredFanatic)
        alice.graveyard(*fourTypes)
        bob.battlefield(p.elf)
        unblocked(s.goredFanatic)
        expect { bob.graveyard has p.elf }
    }

    /** A Wizard's triggered ability triggers twice. */
    // CR 603.2d
    @Test
    fun resonantProdigyDoublesWizards() = scenario {
        alice.battlefield("Island", s.resonantProdigy, s.etheriumWeaver)
        alice.hand(p.notes)
        alice.library(p.ogre)
        alice.casts(p.notes)
        playOut()
        expect { s.resonantProdigy.power shouldBe 2 }
    }

    @Test
    fun prismscorchConverge() = scenario {
        alice.battlefield("Mountain", "Island")
        alice.hand(s.prismscorch)
        alice.casts(s.prismscorch, target = "bob", paying = listOf("Mountain", "Island"))
        playOut()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun thunderLanceSacrificed() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", s.thunderLance)
        alice.activates(s.thunderLance, target = "bob")
        playOut()
        expect { bob.life shouldBe 17 }
    }

    /** During its controller's turn it can be cast by sacrificing a Mountain. */
    // CR 118.9
    @Test
    fun tunnelCaveInSacrificingAMountain() = scenario {
        alice.battlefield("Mountain")
        alice.hand(s.tunnelCaveIn)
        bob.battlefield(p.giant)
        alice.casts(s.tunnelCaveIn, way = alternativeOf(s.tunnelCaveIn, null), target = p.giant, choosing = listOf("Mountain"))
        playOut()
        expect {
            bob.graveyard has p.giant
            alice.graveyard has "Mountain"
        }
    }

    /** Discarding a creature card at combat deals damage equal to its card types. */
    @Test
    fun velusPeakManticoreDiscards() = scenario {
        alice.battlefield(s.velusPeakManticore)
        alice.hand(p.golem)
        turn(step = TurnStep.BeginningOfCombat)
        beginStep(TurnStep.BeginningOfCombat)
        playOut(p.golem, "bob")
        expect { bob.life shouldBe 18 }
    }

    /** It costs {1} less for each land opponents control that could produce {C}. */
    @Test
    fun basaltCharmawCheaper() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(s.basaltCharmaw)
        bob.battlefield(p.wastes)
        alice.casts(s.basaltCharmaw)
        playOut(p.wastes)
        expect { bob.graveyard has p.wastes }
    }

    /** Combat damage to a player: a Treasure, and the top card of their library may be cast this turn. */
    @Test
    fun rakavanPilfers() = scenario {
        alice.battlefield(s.rakavanQuickPilferer)
        bob.library(p.zap)
        hits(s.rakavanQuickPilferer)
        expect {
            alice.battlefield has "Treasure Token"
            bob.exile has p.zap
        }
    }

    // CR 702.40a
    @Test
    fun spreadingRevoltStorm() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(p.zap, s.spreadingRevolt)
        bob.battlefield(p.ogre, p.bear)
        alice.casts(p.zap, target = "bob")
        playOut()
        alice.casts(s.spreadingRevolt, target = p.ogre)
        playOut(p.bear, p.ogre)
        expect {
            card(p.ogre).controller shouldBe alice.id
            card(p.bear).controller shouldBe alice.id
        }
    }

    /** Winning a coin flip makes two Treasures. */
    @Test
    fun taproomScoundrelWins() = scenario {
        alice.battlefield("Mountain", s.taproomScoundrel, p.trinket)
        random(Answer.CoinFlip(true))
        alice.activates(s.taproomScoundrel, choosing = listOf(p.trinket))
        playOut()
        expect { alice.battlefield.names.count { it == "Treasure Token" } shouldBe 2 }
    }

    @Test
    fun profaneFeverDelirium() = scenario {
        alice.battlefield("Mountain")
        alice.graveyard(*fourTypes)
        alice.hand(s.profaneFever)
        bob.battlefield(p.giant)
        alice.casts(s.profaneFever, target = p.giant)
        playOut()
        expect { bob.graveyard has p.giant }
    }
}
