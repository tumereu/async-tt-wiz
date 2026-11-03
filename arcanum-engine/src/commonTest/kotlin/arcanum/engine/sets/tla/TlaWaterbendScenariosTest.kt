package arcanum.engine.sets.tla

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.tla.TlaExtras.bear
import arcanum.engine.sets.tla.TlaExtras.brute
import arcanum.engine.sets.tla.TlaExtras.notes
import arcanum.engine.sets.tla.TlaExtras.ogre
import arcanum.engine.sets.tla.TlaExtras.p1p1
import arcanum.engine.sets.tla.TlaExtras.quest
import arcanum.engine.sets.tla.TlaExtras.trinket
import arcanum.engine.sets.tla.TlaExtras.zap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Avatar: The Last Airbender chunks 04 and 05 cards in play: waterbend costs (CR 701.67) of activated abilities, as additional costs and
 * ward costs, paid by tapping artifacts and creatures, and what the cards do when they're paid. Spout Jumper and Tideshaping Lesson are
 * played in [TlaMechanicsScenariosTest]; Colossal Carp's "can't be blocked this turn" repeats a tested template.
 */
class TlaWaterbendScenariosTest {
    private val squire = Cards.invent("Tide Squire", "{U}", "Creature — Human", "", "1/1")
    private val islands = arrayOf("Island", "Island", "Island", "Island", "Island", "Island")

    /** Waterbend {5} paid by tapping: a creature with power 3 or less from the top four to hand. */
    @Test
    fun tideclanRallier() = scenario {
        alice.battlefield(TlaSet.tideclanRallier, bear, ogre, trinket, "Island", "Island")
        alice.library(brute, bear, notes, notes)
        alice.activates(TlaSet.tideclanRallier, paying = listOf(bear, ogre, trinket, "Island", "Island"))
        resolveAll()
        choose(bear)
        expect { alice.hand has bear }
    }

    /** Waterbend {5} as an additional cost: the spell's own {U}{U} by mana, the {5} by tapping five objects. */
    // CR 701.67b
    @Test
    fun kindlyBrookSpiritCast() = scenario {
        alice.battlefield(bear, ogre, brute, trinket, squire, "Island", "Island")
        alice.hand(TlaSet.kindlyBrookSpirit)
        alice.library(notes, notes)
        alice.casts(TlaSet.kindlyBrookSpirit, paying = listOf("Island", "Island", bear, ogre, brute, trinket, squire))
        resolveAll()
        if (pending != null) choose()
        expect { TlaSet.kindlyBrookSpirit.zone shouldBe Zone.Battlefield }
    }

    /** X is announced for the waterbend cost: two targets tapped, then three stun counters among tapped opposing creatures. */
    // CR 107.3, 122.1d
    @Test
    fun breakingSurge() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(TlaSet.breakingSurge)
        bob.battlefield(bear, ogre)
        alice.casts(TlaSet.breakingSurge, x = 2, targets = listOf(bear, ogre))
        resolveAll()
        // the tapped creatures to share the counters among, then the shares
        choose(bear, ogre)
        answer(Answer.Division(listOf(Share(target(bear), 2), Share(target(ogre), 1))))
        resolveAll()
        resolveAll()
        expect {
            bear.tapped shouldBe true
            (bear.counters(CounterKind.Stun) + ogre.counters(CounterKind.Stun)) shouldBe 3
        }
    }

    @Test
    fun suppleTideshaper() = scenario {
        alice.battlefield(TlaSet.suppleTideshaper, "Island", "Island", "Island")
        alice.activates(TlaSet.suppleTideshaper)
        resolveAll()
        expect {
            TlaSet.suppleTideshaper.power shouldBe 5
            TlaSet.suppleTideshaper.toughness shouldBe 2
        }
    }

    @Test
    fun colossalCarpUnblockable() = scenario {
        alice.battlefield(TlaSet.colossalCarp, "Island", "Island", "Island")
        alice.activates(TlaSet.colossalCarp)
        resolveAll()
        expect { TlaSet.colossalCarp hasMechanic { it == Mechanic.CantBeBlocked } }
    }

    /** It bounces as it enters; exhaust makes it an artifact creature with three counters, for good. */
    // CR 702.177a
    @Test
    fun siegeDivingBell() = scenario {
        alice.battlefield("Island", "Island", "Island", bear, ogre, brute)
        alice.hand(TlaSet.siegeDivingBell)
        bob.battlefield(TlaExtras.flier)
        alice.casts(TlaSet.siegeDivingBell)
        resolveAll()
        targets(TlaExtras.flier)
        alice.activates(TlaSet.siegeDivingBell, paying = listOf(bear, ogre, brute))
        resolveAll()
        expect {
            bob.hand has TlaExtras.flier
            TlaSet.siegeDivingBell.power shouldBe 3
            TlaSet.siegeDivingBell.types.contains(CardType.Creature) shouldBe true
        }
    }

    /** End step: tapped, a counter; waterbend {6} draws. */
    @Test
    fun nerysShapingProdigy() = scenario {
        alice.battlefield(TlaSet.nerysShapingProdigy, *islands)
        alice.library(notes)
        alice.activates(TlaSet.nerysShapingProdigy, paying = listOf(TlaSet.nerysShapingProdigy, "Island", "Island", "Island", "Island", "Island"))
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            TlaSet.nerysShapingProdigy.counters(p1p1) shouldBe 1
        }
    }

    /** Herald Osric makes a Spirit for each spell; Spirits can block only Spirits. */
    // CR 509.1a
    @Test
    fun heraldOsricSpirits() = scenario {
        turn(step = TurnStep.Upkeep)
        alice.battlefield(TlaSet.theTaleOfOsric, counters = listOf(CounterKind.Lore to 2))
        alice.battlefield("Island")
        alice.hand(notes)
        alice.library(notes, notes, notes, notes)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        alice.casts(notes)
        resolveAll()
        expect { alice.battlefield has "Spirit Token" }
    }

    /** Chapters I and II scry 2 and draw. */
    @Test
    fun taleOfOsricChapterOne() = scenario {
        alice.battlefield(*islands)
        alice.hand(TlaSet.theTaleOfOsric)
        alice.library(notes, notes, notes)
        alice.casts(TlaSet.theTaleOfOsric)
        resolveAll()
        // scry 2: none to the bottom, both kept on top
        choose()
        choose(notes, notes)
        expect { alice.hand.size shouldBe 1 }
    }

    @Test
    fun northernIcePatrolTaps() = scenario {
        alice.battlefield(TlaSet.northernIcePatrol, bear, ogre, trinket)
        bob.battlefield(brute)
        alice.activates(TlaSet.northernIcePatrol, ability = 1, target = brute, paying = listOf(bear, ogre, trinket))
        resolveAll()
        expect { brute.tapped shouldBe true }
    }

    /** Unpaid: Alice makes Bob's decisions during his next combat phase only. */
    // CR 723.2
    @Test
    fun secretOfVeinshapingControlsTheNextCombat() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(TlaSet.secretOfVeinshaping)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.casts(TlaSet.secretOfVeinshaping, target = "bob", kicked = 0)
        resolveAll()
        assertEquals(listOf(arcanum.engine.state.PlayerControl(bob.id, alice.id, combatOnly = true)), state.turn.controlNext)
        advanceTo(arcanum.api.TurnStep.PrecombatMain, turn = 2)
        assertEquals(bob.id, arcanum.engine.Engine.decider(state, bob.id))
        advanceTo(arcanum.api.TurnStep.BeginningOfCombat, turn = 2)
        assertEquals(alice.id, arcanum.engine.Engine.decider(state, bob.id))
        expect { TlaSet.secretOfVeinshaping.zone shouldBe Zone.Exile }
    }

    /** Paid: the graveyard shuffled in, seven cards, no maximum hand size; the spell exiles itself. */
    // CR 601.2b, 402.2
    @Test
    fun holySpringRevivalPaid() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(TlaSet.holySpringRevival)
        alice.graveyard(notes, notes, notes, notes, notes)
        alice.library(notes, notes, notes)
        alice.casts(TlaSet.holySpringRevival, kicked = 1)
        resolveAll()
        expect {
            alice.hand.size shouldBe 7
            alice.graveyard.size shouldBe 0
            TlaSet.holySpringRevival.zone shouldBe Zone.Exile
        }
    }

    /** Ward—waterbend {4}: the opponent pays it by tapping their creatures, so the spell isn't countered. */
    // CR 702.21a, 701.67a
    @Test
    fun leviathanWardPaidByTapping() = scenario {
        alice.battlefield(TlaSet.theLeviathanOfReedIsle)
        bob.battlefield(bear, ogre, brute, squire, "Mountain")
        bob.hand(zap)
        turn(active = bob)
        bob.casts(zap, target = TlaSet.theLeviathanOfReedIsle)
        resolveAll()
        yes()
        resolveAll()
        expect {
            bear.tapped shouldBe true
            TlaSet.theLeviathanOfReedIsle.view.obj?.damage shouldBe 2
        }
    }

    /** Opponent's second draw each turn: you draw two. */
    @Test
    fun leviathanDraws() = scenario {
        alice.battlefield(TlaSet.theLeviathanOfReedIsle, "Island")
        alice.library(notes, notes)
        bob.library(notes, notes, notes)
        bob.battlefield("Island", "Island")
        bob.hand(notes, notes)
        turn(active = bob)
        bob.casts(notes)
        resolveAll()
        expect { alice.hand.size shouldBe 0 }
        bob.casts(notes)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    /** Combat damage puts quest counters; the fourth draws. */
    @Test
    fun tideshaperAscension() = scenario {
        alice.battlefield(TlaSet.tideshaperAscension, counters = listOf(quest to 3))
        alice.battlefield(bear)
        alice.library(notes)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    /** Waterbend {5}: the enchanted creature's owner shuffles it into their library. */
    @Test
    fun drowningGrip() = scenario {
        alice.battlefield("Island", bear, ogre, brute, trinket, squire)
        alice.hand(TlaSet.drowningGrip)
        bob.battlefield(TlaExtras.flier)
        alice.casts(TlaSet.drowningGrip, target = TlaExtras.flier)
        resolveAll()
        alice.activates(TlaSet.drowningGrip, paying = listOf(bear, ogre, brute, trinket, squire))
        resolveAll()
        expect { bob.library has TlaExtras.flier }
    }

    /** Waterbend {5}, {T}: a noncreature spell from hand without paying. */
    @Test
    fun selvaCastsFree() = scenario {
        alice.battlefield(TlaSet.selvaTheMoonSpirit, bear, ogre, brute, trinket, squire)
        alice.hand(TlaSet.breakingSurge, notes)
        alice.library(notes)
        alice.activates(TlaSet.selvaTheMoonSpirit, paying = listOf(bear, ogre, brute, trinket, squire))
        resolveAll()
        choose(notes)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    /** X target creature cards exiled; a token copy of each, sacrificed at your next end step. */
    @Test
    fun mistyBogVisions() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.hand(TlaSet.mistyBogVisions)
        bob.graveyard(brute)
        alice.casts(TlaSet.mistyBogVisions, x = 1, target = brute, paying = listOf("Swamp", "Swamp", "Swamp", bear))
        resolveAll()
        expect {
            bob.graveyard lacks brute
            alice.battlefield has brute
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield lacks brute }
    }

    /** Paid: all creatures -2/-2, and each creature dying this turn gains you 1 life. */
    @Test
    fun ruinousTideshapingPaid() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(TlaSet.ruinousTideshaping)
        bob.battlefield(bear, squire)
        alice.casts(TlaSet.ruinousTideshaping, kicked = 1)
        resolveAll()
        expect {
            bob.battlefield.size shouldBe 0
            alice.life shouldBe 22
        }
    }

    /** Waterbend {5}: a counter, only during your turn. */
    @Test
    fun mistyBogVineshaper() = scenario {
        alice.battlefield(TlaSet.mistyBogVineshaper, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(TlaSet.mistyBogVineshaper)
        resolveAll()
        expect { TlaSet.mistyBogVineshaper.counters(p1p1) shouldBe 1 }
    }

    /** The opponent mills three; one exiled noncreature card is yours to cast by waterbend {X} (its mana value) during your turn. */
    // CR 118.9, 701.67a
    @Test
    fun griselCastsTheExiledCard() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", bear)
        alice.hand(TlaSet.griselTheVeinshaper)
        alice.library(notes)
        bob.library(TlaExtras.lesson, brute, zap)
        alice.casts(TlaSet.griselTheVeinshaper)
        resolveAll()
        choose(TlaExtras.lesson)
        expect { TlaExtras.lesson.zone shouldBe Zone.Exile }
        alice.casts(TlaExtras.lesson, paying = listOf(bear))
        resolveAll()
        expect {
            bear.tapped shouldBe true
            alice.life shouldBe 21
        }
    }

    /** Waterbend {X}: creatures become X/X; X can't be 0. */
    // CR 107.3
    @Test
    fun nerysTideclansHope() = scenario {
        alice.battlefield(TlaSet.nerysTideclansHope, bear, "Island", "Island", "Island")
        alice.activates(TlaSet.nerysTideclansHope, x = 3)
        resolveAll()
        expect { bear.power shouldBe 3 }
        assertFailsWith<IllegalArgumentException> { alice.activates(TlaSet.nerysTideclansHope, x = 0) }
    }

    /** The Master of the Four: spells cost {W}{U}{B}{R}{G} less (generic too); at the upkeep it transforms back with its rewards. */
    // CR 601.2f, 701.27
    @Test
    fun masterOfTheFour() = scenario {
        alice.battlefield(TlaSet.heraldKiro)
        backFaceUp(TlaSet.heraldKiro)
        alice.hand(brute)
        alice.library(notes, notes, notes, notes, notes, notes)
        bob.library(notes, notes)
        expect { "Kiro, Master of the Four".power shouldBe 6 }
        // {3}{G}: {G} and three generic all reduced
        alice.casts(brute)
        resolveAll()
        expect { brute.zone shouldBe Zone.Battlefield }
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        yes()
        expect {
            TlaSet.heraldKiro.counters(p1p1) shouldBe 4
            alice.life shouldBe 24
            bob.life shouldBe 16
            alice.hand.size shouldBe 4
        }
    }
}
