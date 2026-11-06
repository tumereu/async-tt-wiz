package arcanum.engine.sets.ltr

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Supertype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The Lord of the Rings cards of parser chunks 03–04 in play: the blue cards. Trivial ones not played here: Party Vanishing, Soothing of
 * the Wretch and Horses of the Loudwater (draws, returns and the Ring tempting, as Ride for Wrath), Captain of the Corsairs (a loot
 * ability), Mislead the Herald, Wizard's Trickery and Treason of the Tower (a spell's effect and amass, as the Hobbit's amass scenarios),
 * Harbor Navigator of the West, Elven Rope Knots and Reed-Wind (scry), Isolation at the Tower (a library position, as Lost to Old Songs),
 * River Kingfisher of the South (a dies draw), Knights of the Swan Coast (as Stalwarts of the River Fort), Golden Wood Revealed
 * (islandcycling, as Eagles of the Far Peaks' plainscycling).
 */
class LtrBlueScenariosTest {
    private val s = LtrSet
    private val p = LtrPlain
    private val p1p1 = LtrPlain.p1p1
    private val islands = arrayOf("Island", "Island", "Island", "Island", "Island", "Island")

    // ---- Chunk 03

    /** Two legendary creatures make it cost {2}{U}: three lands cast it. */
    // CR 601.2f
    @Test
    fun evenstarsGiftCostsLessWithTwoLegends() = scenario {
        alice.battlefield("Island", "Island", "Island", p.legend, p.bigLegend)
        alice.hand(s.evenstarsGift)
        alice.library(p.ogre, p.ogre, p.ogre)
        alice.casts(s.evenstarsGift)
        playOut()
        expect { alice.hand.size shouldBe 2 }
    }

    /** Chapter III shuffles the chosen graveyard cards into the library and adds {U}{U}. */
    // CR 714.2b, 701.24
    @Test
    fun washingSongShufflesBack() = scenario {
        alice.battlefield(s.theWashingSong, counters = listOf(CounterKind.Lore to 2))
        alice.graveyard(p.ogre, p.bear)
        alice.library(p.elf, p.elf)
        bob.library(p.elf, p.elf)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        playOut(p.ogre, p.bear)
        expect {
            alice.library has p.ogre
            alice.library has p.bear
            alice.pool.size shouldBe 2
        }
    }

    /** The enchanted creature is tapped; in its untap step a counter comes off and it untaps; without counters it stays tapped. */
    // CR 614.1a, 502.3
    @Test
    fun bewitchingHerbcraftTakesACounterToUntap() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(s.bewitchingHerbcraft)
        alice.library(p.ogre, p.ogre)
        bob.battlefield(p.bear, counters = listOf(p1p1 to 1))
        bob.library(p.ogre, p.ogre, p.ogre)
        alice.casts(s.bewitchingHerbcraft, target = p.bear)
        playOut()
        expect { p.bear.tapped shouldBe true }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            p.bear.tapped shouldBe false
            p.bear.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun bewitchingHerbcraftWithoutCountersStaysTapped() = scenario {
        alice.battlefield(s.bewitchingHerbcraft)
        bob.battlefield(p.bear, tapped = true)
        attach(s.bewitchingHerbcraft, p.bear)
        alice.library(p.ogre, p.ogre)
        bob.library(p.ogre, p.ogre, p.ogre)
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { p.bear.tapped shouldBe true }
    }

    /** Blocked, it gives Bob its Horse: removed from combat, three Treasures. */
    // CR 700.2, 506.4
    @Test
    fun hobFerrowSwindlesAHorse() = scenario {
        val pony = Cards.invent("Shirefolk Pony", "{W}", "Creature — Horse", "", "1/1")
        alice.battlefield(s.hobFerrowInnSwindler, pony)
        bob.battlefield(p.ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.hobFerrowInnSwindler)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(p.ogre to s.hobFerrowInnSwindler)
        modes(1)
        playOut("bob", pony)
        expect { pony.controller shouldBe bob.id }
        assertEquals(3, tokens("Treasure Token").size)
        advanceTo(TurnStep.CombatDamage)
        expect { s.hobFerrowInnSwindler.zone shouldBe Zone.Battlefield }
    }

    /** After it resolves, a sorcery can be cast on the opponent's turn. */
    // CR 702.8a
    @Test
    fun carriedOnTheBreezeGivesFlash() = scenario {
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain, priority = alice)
        alice.battlefield("Island", "Island", "Island")
        alice.hand(s.carriedOnTheBreeze, p.notes)
        alice.library(p.ogre, p.ogre)
        alice.casts(s.carriedOnTheBreeze)
        playOut()
        bob.passes()
        alice.casts(p.notes)
        playOut()
        expect { alice.hand.size shouldBe 2 }
    }

    /** From the graveyard, a scry with an Island lets Alice exile it to draw. */
    // CR 113.6, 701.22
    @Test
    fun councilsMusingWorksFromTheGraveyard() = scenario {
        alice.battlefield("Island", s.harborNavigatorOfTheWest)
        alice.graveyard(s.councilsMusing)
        alice.hand(p.zap)
        alice.library(p.ogre, p.bear)
        alice.battlefield("Mountain")
        alice.hand(s.reedWind)
        alice.castsWithoutCosts(s.reedWind)
        playOut()
        expect {
            alice.exile has s.councilsMusing
            alice.hand.size shouldBe 2
        }
    }

    /** Base power and toughness 5/5 until end of turn; the Ring tempts. */
    // CR 613.4b
    @Test
    fun terribleAsTheTempest() = scenario {
        alice.battlefield("Island", "Island", "Island", p.elf)
        alice.hand(s.terribleAsTheTempest)
        alice.casts(s.terribleAsTheTempest, target = p.elf)
        playOut(p.elf)
        expect { p.elf.power shouldBe 5 }
        assertEquals(1, temptations())
    }

    /** The second resolution this turn tempts; the first doesn't. */
    // CR 113.7
    @Test
    fun elrohirTemptsOnTheSecondResolution() = scenario {
        alice.battlefield(s.elrohirLordOfTheValley, "Plains", "Plains")
        alice.hand(p.human, p.human)
        alice.library(p.ogre, p.ogre, p.ogre)
        alice.casts(p.human)
        playOut()
        assertEquals(0, temptations())
        alice.casts(p.human)
        playOut(p.human)
        assertEquals(1, temptations())
    }

    /** Sorceries at instant speed; choosing another Ring-bearer draws. */
    // CR 701.54d
    @Test
    fun tharkunDrawsForAnotherBearer() = scenario {
        alice.battlefield(s.tharkunFriendOfTheHills, p.bear, "Swamp")
        alice.hand(p.beckon)
        alice.library(p.ogre)
        alice.casts(p.beckon)
        playOut(p.bear)
        expect { alice.hand has p.ogre }
    }

    /** Countering a legendary creature spell tempts; a nonlegendary one doesn't. */
    // CR 701.54a
    @Test
    fun gloriousGustCountersALegend() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(s.gloriousGust)
        bob.battlefield("Plains", "Plains")
        bob.hand(p.legend)
        turn(active = bob)
        bob.casts(p.legend)
        bob.passes()
        alice.casts(s.gloriousGust, target = p.legend)
        playOut()
        expect { bob.graveyard has p.legend }
        assertEquals(1, temptations())
    }

    /** It takes one counter of each kind it lacks; then moves a counter away and draws. */
    // CR 122.5
    @Test
    fun lilywenMovesCounters() = scenario {
        alice.battlefield(s.lilywenRiverDaughter, "Island")
        alice.battlefield(p.bear, counters = listOf(p1p1 to 2, CounterKind.Keyword(Keyword.Flying) to 1))
        alice.library(p.ogre)
        alice.activates(s.lilywenRiverDaughter, ability = 0, target = p.bear)
        playOut()
        expect {
            s.lilywenRiverDaughter.counters(p1p1) shouldBe 1
            s.lilywenRiverDaughter.counters(CounterKind.Keyword(Keyword.Flying)) shouldBe 1
            p.bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun lilywenGivesCountersAndDraws() = scenario {
        alice.battlefield(s.lilywenRiverDaughter, counters = listOf(p1p1 to 2))
        alice.battlefield("Island", p.bear)
        alice.library(p.ogre)
        alice.activates(s.lilywenRiverDaughter, ability = 1, target = p.bear)
        allPass()
        answer(Answer.Number(1))
        playOut()
        expect {
            p.bear.counters(p1p1) shouldBe 1
            alice.hand has p.ogre
        }
    }

    /** Two other legendary creatures untap. */
    @Test
    fun berethielUntapsTwoLegends() = scenario {
        alice.battlefield(s.berethielOfTheHealingHall)
        alice.battlefield(p.legend, p.bigLegend, tapped = true)
        alice.activates(s.berethielOfTheHealingHall, ability = 1, targets = listOf(p.legend, p.bigLegend))
        playOut()
        expect {
            p.legend.tapped shouldBe false
            p.bigLegend.tapped shouldBe false
        }
    }

    // ---- Chunk 04

    /** Each scry adds a verse counter; exiling it draws that many. */
    // CR 608.2h
    @Test
    fun farIsleCallingDrawsForVerses() = scenario {
        alice.battlefield(s.farIsleCalling, *islands, *islands)
        alice.hand(s.reedWind)
        alice.library(p.ogre, p.ogre, p.bear, p.bear)
        alice.casts(s.reedWind)
        playOut()
        expect { s.farIsleCalling.counters(CounterKind.Named("verse")) shouldBe 1 }
        alice.activates(s.farIsleCalling)
        playOut()
        expect {
            alice.exile has s.farIsleCalling
            alice.hand.size shouldBe 1
        }
    }

    /** Seven verse counters also give an extra turn. */
    // CR 500.7
    @Test
    fun farIsleCallingTakesAnExtraTurn() = scenario {
        alice.battlefield(s.farIsleCalling, counters = listOf(CounterKind.Named("verse") to 7))
        alice.battlefield(*islands)
        alice.library(p.ogre, p.ogre, p.ogre, p.ogre, p.ogre, p.ogre, p.ogre, p.ogre, p.ogre)
        bob.library(p.ogre, p.ogre)
        alice.activates(s.farIsleCalling)
        playOut()
        expect { alice.hand.size shouldBe 7 }
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(alice.id, state.turn.active)
    }

    /** Combat damage blinks a creature Alice owns, back under her control. */
    @Test
    fun thorondilBlinks() = scenario {
        alice.battlefield(s.thorondilSwiftSavior, p.bear)
        hits(s.thorondilSwiftSavior, picks = listOf(p.bear))
        expect {
            p.bear.zone shouldBe Zone.Battlefield
            bob.life shouldBe 17
        }
    }

    /** A scry makes it +1/+0 and unblockable this turn, once a turn. */
    @Test
    fun silverlodeWatcherSlipsPast() = scenario {
        alice.battlefield(s.silverlodeWatcher, "Island", "Island", "Island", "Island", "Island")
        alice.hand(s.reedWind)
        alice.library(p.ogre, p.ogre)
        bob.battlefield(p.ogre)
        alice.casts(s.reedWind)
        playOut()
        expect { s.silverlodeWatcher.power shouldBe 3 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.silverlodeWatcher)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(p.ogre to s.silverlodeWatcher) }
    }

    /** Its mana pays for an instant but not for a creature spell. */
    // CR 106.6
    @Test
    fun harborTownSurvivorManaOnlyForInstants() = scenario {
        alice.battlefield(s.harborTownSurvivor)
        alice.hand(p.zap, p.goblin)
        assertFailsWith<Throwable> { alice.casts(p.goblin, paying = listOf(s.harborTownSurvivor + ":R")) }
    }

    @Test
    fun harborTownSurvivorPaysForAnInstant() = scenario {
        alice.battlefield(s.harborTownSurvivor)
        alice.hand(p.zap)
        alice.casts(p.zap, target = "bob", paying = listOf(s.harborTownSurvivor + ":R"))
        playOut()
        expect { bob.life shouldBe 18 }
    }

    /** A creature spell goes back; Alice casts a cheaper sorcery for free. */
    // CR 601.2, 608.2h
    @Test
    fun driveBackTheFoeAndCastFree() = scenario {
        alice.battlefield(*islands)
        alice.hand(s.driveBackTheFoe, p.notes)
        alice.library(p.ogre)
        bob.battlefield(p.ogre)
        alice.casts(s.driveBackTheFoe, target = p.ogre)
        playOut(p.notes)
        expect {
            bob.hand has p.ogre
            alice.graveyard has p.notes
            alice.hand has p.ogre
        }
    }

    /** It takes a creature with lesser power while Alice controls it, and the Ring tempts. */
    // CR 611.2b
    @Test
    fun rangersTakeASmallerCreature() = scenario {
        alice.battlefield(*islands)
        alice.hand(s.rangersOfTheHiddenVale, p.doom)
        alice.battlefield("Swamp")
        bob.battlefield(p.bear)
        alice.casts(s.rangersOfTheHiddenVale)
        playOut(p.bear, s.rangersOfTheHiddenVale)
        expect { p.bear.controller shouldBe alice.id }
        alice.casts(p.doom, target = s.rangersOfTheHiddenVale)
        playOut()
        expect { p.bear.controller shouldBe bob.id }
    }

    /** The second spell of the turn amasses Orcs 2. */
    // CR 603.2
    @Test
    fun curunilAmassesOnTheSecondSpell() = scenario {
        alice.battlefield(s.curunilTheWhite, "Island", "Island")
        alice.hand(p.notes, p.notes)
        alice.library(p.ogre, p.ogre)
        alice.casts(p.notes)
        playOut()
        assertTrue(tokens("Orc Army Token").isEmpty())
        alice.casts(p.notes)
        playOut()
        assertEquals(1, tokens("Orc Army Token").size)
    }

    /** I steals an artifact while the Saga stays; II taps and stuns; III draws per tapped creature. */
    // CR 611.2b, 122.1d
    @Test
    fun scrollOfTheLastKing() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(s.scrollOfTheLastKing)
        bob.battlefield(p.trinket)
        alice.casts(s.scrollOfTheLastKing)
        playOut(p.trinket)
        expect { p.trinket.controller shouldBe alice.id }
        move(s.scrollOfTheLastKing, Zone.Graveyard)
        expect { p.trinket.controller shouldBe bob.id }
    }

    @Test
    fun scrollOfTheLastKingStunsAndDraws() = scenario {
        alice.battlefield(s.scrollOfTheLastKing, counters = listOf(CounterKind.Lore to 1))
        alice.library(p.ogre, p.ogre, p.ogre, p.ogre)
        bob.battlefield(p.bear, p.ogre)
        bob.library(p.ogre, p.ogre)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        playOut(p.bear, p.ogre)
        expect {
            p.bear.tapped shouldBe true
            p.bear.counters(CounterKind.Stun) shouldBe 1
        }
        advanceTo(TurnStep.PrecombatMain, turn = 5)
        playOut("bob")
        expect { alice.hand.size shouldBe 4 }
    }

    /** The second spell is copied, the copy of a legendary creature spell isn't legendary. */
    // CR 707.9b, 707.10
    @Test
    fun stormOfTheWhiteWizardCopiesNonlegendary() = scenario {
        alice.battlefield(s.stormOfTheWhiteWizard, "Plains", "Plains", "Plains")
        alice.hand(p.human, p.legend)
        alice.casts(p.human)
        playOut()
        alice.casts(p.legend)
        playOut()
        val copies = tokens(p.legend)
        assertEquals(1, copies.size)
        assertTrue(Supertype.Legendary !in view()[copies.single()]!!.characteristics!!.supertypes)
        expect { alice.battlefield has p.legend }
    }

    /** The amassed Army's power, three, is how many cards the player mills. */
    // CR 701.47c
    @Test
    fun hemmedInByOrcsMillsByTheArmy() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(s.hemmedInByOrcs)
        bob.library(p.ogre, p.ogre, p.ogre, p.ogre, p.ogre)
        alice.casts(s.hemmedInByOrcs, target = "bob")
        playOut()
        expect { bob.graveyard.size shouldBe 3 }
    }

    /** It enters tapped with nine stun counters; a draw on Bob's turn makes a Tentacle; a Tentacle dying untaps it and stuns a permanent. */
    // CR 122.1d
    @Test
    fun theLurkerInTheLake() = scenario {
        alice.battlefield(*islands)
        alice.hand(s.theLurkerInTheLake)
        alice.casts(s.theLurkerInTheLake)
        playOut()
        expect {
            s.theLurkerInTheLake.tapped shouldBe true
            s.theLurkerInTheLake.counters(CounterKind.Stun) shouldBe 9
        }
    }

    @Test
    fun theLurkerMakesAndLosesTentacles() = scenario {
        val study = Cards.invent("Shirefolk Quick Study", "{U}", "Instant", "Draw a card.")
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain, priority = alice)
        alice.battlefield(s.theLurkerInTheLake, tapped = true)
        alice.battlefield("Island", "Swamp")
        alice.hand(study, p.doom)
        alice.library(p.ogre)
        bob.battlefield(p.trinket)
        alice.casts(study)
        playOut()
        expect { alice.hand has p.ogre }
        assertEquals(1, tokens("Tentacle Token").size)
        bob.passes()
        alice.casts(p.doom, target = "Tentacle Token")
        playOut(p.trinket, s.theLurkerInTheLake)
        expect {
            s.theLurkerInTheLake.tapped shouldBe false
            p.trinket.counters(CounterKind.Stun) shouldBe 1
        }
    }

    /** Scrying one card lets it tap up to one target nonland permanent. */
    @Test
    fun elvishSailwrightTapsAsItScries() = scenario {
        alice.battlefield(s.elvishSailwright, "Island", "Island", "Island", "Island", "Island")
        alice.hand(s.reedWind)
        alice.library(p.ogre, p.ogre)
        bob.battlefield(p.ogre, p.bear)
        alice.casts(s.reedWind)
        playOut(p.ogre, p.bear)
        expect {
            p.ogre.tapped shouldBe true
            p.bear.tapped shouldBe true
        }
    }

    /** Its power is the hand size; attacking, tapping two Humans draws two. */
    @Test
    fun whiteCityGarrisonTapsHumansToDraw() = scenario {
        alice.battlefield(s.whiteCityGarrison, p.human)
        alice.battlefield(Cards.invent("Shirefolk Archer", "{1}{W}", "Creature — Human Archer", "", "1/1"))
        alice.hand(p.zap)
        alice.library(p.ogre, p.ogre)
        expect { s.whiteCityGarrison.power shouldBe 1 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.whiteCityGarrison)
        playOut(p.human, "Shirefolk Archer")
        expect {
            alice.hand.size shouldBe 3
            s.whiteCityGarrison.power shouldBe 3
        }
    }
}
