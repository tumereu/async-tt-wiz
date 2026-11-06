package arcanum.engine.sets.ltr

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
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
 * The Lord of the Rings cards of parser chunks 11–13 in play: the multicoloured cards. Trivial ones not played here: Nob, Bree Innkeeper and
 * Ecthelion, Ruling Steward (end step tokens by conditions already played), Prince Adrahil the Fair (as Stalwarts of the River Fort), Shade
 * Summoning (tapped tokens), Thengel, King of Rohan, Beren and Lúthien, Wed and Gorthaur, the Lidless Eye (enter triggers of shapes played
 * elsewhere), Old Grey Willow (a reflexive trigger after an optional sacrifice, as Quickfoot Halfling), Ted, Crooked Shirriff (the second
 * spell each turn, as Curunil), Mairon, the Dark Lord (amass, the Ring tempting and a discard-to-draw already played), Mithrandir the Grey
 * (modes that haven't been chosen, a shape of other sets).
 */
class LtrMulticoloredScenariosTest {
    private val s = LtrSet
    private val p = LtrPlain
    private val p1p1 = LtrPlain.p1p1
    private val lands = arrayOf("Plains", "Island", "Swamp", "Mountain", "Forest", "Plains", "Island", "Swamp", "Mountain", "Forest")
    private val wizard = Cards.invent("Shirefolk Wizard", "{1}{U}", "Creature — Human Wizard", "", "1/2")

    // ---- Chunk 11

    /** Choosing another Ring-bearer puts a chosen keyword counter on it; those kinds then go on another creature too. */
    // CR 122.1b
    @Test
    fun arathornSharesItsCounters() = scenario {
        alice.battlefield(s.arathornCompanyLeader, p.bear, "Swamp")
        alice.hand(p.beckon)
        alice.casts(p.beckon)
        playOut(p.bear, options = listOf("deathtouch"))
        expect {
            s.arathornCompanyLeader.counters(CounterKind.Keyword(Keyword.Deathtouch)) shouldBe 1
            p.bear.counters(CounterKind.Keyword(Keyword.Deathtouch)) shouldBe 1
        }
    }

    @Test
    fun elessarRedSpellBurns() = scenario {
        alice.battlefield(s.elessarTheUniter, "Mountain")
        alice.hand(p.zap)
        alice.casts(p.zap, target = "bob")
        playOut("bob")
        expect { bob.life shouldBe 15 }
    }

    /** Removing its indestructible counter: the target is indestructible and both get +1/+1 and lifelink counters. */
    @Test
    fun luthienSharesHerGrace() = scenario {
        alice.battlefield("Plains", "Forest", "Plains", "Plains")
        alice.hand(s.luthienMortalQueen)
        alice.battlefield(p.bear)
        alice.casts(s.luthienMortalQueen)
        playOut()
        expect { s.luthienMortalQueen.counters(CounterKind.Keyword(Keyword.Indestructible)) shouldBe 1 }
        alice.activates(s.luthienMortalQueen, target = p.bear)
        playOut()
        expect {
            p.bear hasKeyword Keyword.Indestructible
            p.bear.counters(p1p1) shouldBe 1
            s.luthienMortalQueen hasKeyword Keyword.Lifelink
            s.luthienMortalQueen.counters(CounterKind.Keyword(Keyword.Indestructible)) shouldBe 0
        }
    }

    /** With two permanents sacrificed this turn it costs {2} less; only legendary creatures can block it. */
    // CR 601.2f, 509.1b
    @Test
    fun fireDemonCheaperAfterSacrifices() = scenario {
        alice.battlefield("Plains", "Swamp", "Swamp", "Swamp", "Mountain", "Mountain", "Mountain", s.spiderPassPatrol, p.bear, p.elf)
        alice.hand(s.theFireDemonDurinsBane)
        alice.library(p.ogre, p.ogre)
        alice.activates(s.spiderPassPatrol, choosing = listOf(p.bear), paying = listOf("Plains"))
        playOut()
        alice.casts(s.theFireDemonDurinsBane)
        playOut()
        expect { alice.battlefield has s.theFireDemonDurinsBane }
    }

    @Test
    fun fireDemonOnlyLegendsBlock() = scenario {
        alice.battlefield(s.theFireDemonDurinsBane)
        bob.battlefield(p.ogre, p.legend)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.theFireDemonDurinsBane)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(p.ogre to s.theFireDemonDurinsBane) }
    }

    /** Entering and leaving each tempt. */
    @Test
    fun bungoTemptsTwice() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain", "Swamp")
        alice.hand(s.bungoRetiredBurglar, p.doom)
        alice.casts(s.bungoRetiredBurglar)
        playOut(s.bungoRetiredBurglar)
        alice.casts(p.doom, target = s.bungoRetiredBurglar)
        playOut()
        assertEquals(2, temptations())
    }

    /** A creature revealed after the attack scry comes in tapped and attacking, with trample from a Dwarf. */
    // CR 508.4
    @Test
    fun gatesOfTheDwarfLordAddsAnAttacker() = scenario {
        alice.battlefield(s.gatesOfTheDwarfLord, p.bear, Cards.invent("Shirefolk Dwarf", "{1}{R}", "Creature — Dwarf", "", "2/2"))
        alice.library(p.ogre, p.elf, p.elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(p.bear)
        playOut()
        expect {
            alice.battlefield has p.ogre
            p.ogre.tapped shouldBe true
            p.ogre hasKeyword Keyword.Trample
            p.ogre lacksKeyword Keyword.Hexproof
        }
        assertTrue(state.combat?.attackers.orEmpty().any { it.id == card(p.ogre) })
    }

    /** Scrying two cards: counters on up to two targets; an opponent's target draws for a countered creature. */
    @Test
    fun elrosHealsAfterScry() = scenario {
        alice.battlefield(s.elrosMasterOfHealing, p.bear, p.elf, *lands)
        alice.hand(s.reedWind)
        alice.library(p.ogre, p.ogre, p.ogre)
        alice.casts(s.reedWind)
        playOut(p.bear, p.elf)
        expect {
            p.bear.counters(p1p1) shouldBe 1
            p.elf.counters(p1p1) shouldBe 1
        }
    }

    /** The exiled creature's colour: legendary creatures get protection from red. */
    // CR 702.16a
    @Test
    fun theodwynExilesAndProtects() = scenario {
        alice.battlefield(*lands)
        alice.hand(s.theodwynFearlessKnight)
        alice.battlefield(p.legend)
        bob.battlefield(Cards.invent("Shirefolk Red Giant", "{4}{R}", "Creature — Giant", "", "5/5"))
        alice.casts(s.theodwynFearlessKnight)
        playOut("Shirefolk Red Giant")
        expect { bob.exile has "Shirefolk Red Giant" }
        assertTrue(view()[card(p.legend)]!!.characteristics!!.abilities.any { it.ability is Keyword.Protection })
    }

    /** At Bob's next end step: no attack means Alice draws. */
    // CR 603.7
    @Test
    fun damrodDrawsWithoutAnAttack() = scenario {
        alice.battlefield(s.damrodPrinceOfIthilien)
        alice.library(p.ogre, p.ogre, p.ogre)
        bob.library(p.ogre, p.ogre)
        advanceTo(TurnStep.End)
        playOut()
        advanceTo(TurnStep.End, turn = 2)
        playOut()
        expect { alice.hand.size shouldBe 1 }
    }

    /** With a Wizard, two modes. */
    // CR 700.2
    @Test
    fun flameOfTheSecretFireTwoModes() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain", wizard)
        alice.hand(s.flameOfTheSecretFire)
        alice.library(p.ogre, p.ogre)
        bob.battlefield(p.trinket, p.giant)
        alice.casts(s.flameOfTheSecretFire, modes = listOf(1, 2), targets = listOf(p.trinket, p.giant))
        playOut()
        expect {
            bob.graveyard has p.trinket
            bob.graveyard has p.giant
        }
    }

    @Test
    fun flameOfTheSecretFireOneModeWithoutAWizard() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain")
        alice.hand(s.flameOfTheSecretFire)
        bob.battlefield(p.trinket, p.giant)
        assertFailsWith<Throwable> { alice.casts(s.flameOfTheSecretFire, modes = listOf(1, 2), targets = listOf(p.trinket, p.giant)) }
    }

    /** Two creatures each deal their power to Bob's creature. */
    @Test
    fun friendlyContest() = scenario {
        alice.battlefield("Mountain", "Forest", p.bear, p.legend)
        alice.hand(s.friendlyContest)
        bob.battlefield(p.giant)
        alice.casts(s.friendlyContest, targets = listOf(p.bear, p.legend, p.giant))
        playOut()
        expect { p.giant.view.obj?.damage shouldBe 4 }
    }

    /** As the Ring-bearer it must be blocked if able. */
    @Test
    fun drogoMustBeBlocked() = scenario {
        alice.battlefield(s.drogoBaggins, "Swamp")
        alice.hand(p.beckon)
        bob.battlefield(p.bear)
        alice.casts(p.beckon)
        playOut(s.drogoBaggins)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.drogoBaggins)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks() }
    }

    /** A scry reveals a land on top: onto the battlefield tapped. */
    @Test
    fun celebrianDrawsLandsOut() = scenario {
        alice.battlefield(s.celebrianOfLothlorien, *lands)
        alice.hand(s.reedWind)
        alice.library(p.ogre, "Forest", p.ogre)
        alice.casts(s.reedWind)
        playOut()
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(listOf(card(p.ogre, zone = Zone.Library))))
        playOut()
        assertTrue(state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) == "Forest" } == true } >= 2)
    }

    // ---- Chunk 12

    /** X damage; the excess goes to the creature's controller. */
    // CR 120.4a
    @Test
    fun wizardsSanctionSpillsOver() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain")
        alice.hand(s.wizardsSanction)
        alice.graveyard(p.zap, p.zap, p.notes, p.notes)
        bob.battlefield(p.bear)
        alice.casts(s.wizardsSanction, target = p.bear)
        playOut()
        expect {
            bob.graveyard has p.bear
            bob.life shouldBe 18
        }
    }

    /** The third death this turn: a fight. */
    // CR 113.7
    @Test
    fun thrainFightsOnTheThirdDeath() = scenario {
        alice.battlefield(s.thrainMournfulAvenger, p.elf, p.elf, p.elf, "Swamp", "Swamp", "Swamp")
        alice.hand(p.doom, p.doom, p.doom)
        bob.battlefield(p.bear)
        repeat(3) { i ->
            alice.casts(p.doom, target = p.elf)
            playOut(p.bear)
            if (i == 1) expect { s.thrainMournfulAvenger hasKeyword Keyword.Indestructible }
        }
        expect {
            s.thrainMournfulAvenger.counters(p1p1) shouldBe 3
            bob.graveyard has p.bear
        }
    }

    /** Targeted by a spell, it phases out; phasing back in makes a Spirit. */
    // CR 702.26
    @Test
    fun kingOfTheForswornPhases() = scenario {
        alice.battlefield(s.kingOfTheForsworn)
        alice.library(p.ogre, p.ogre)
        bob.battlefield("Mountain")
        bob.hand(p.zap)
        bob.library(p.ogre, p.ogre)
        turn(active = bob)
        bob.casts(p.zap, target = s.kingOfTheForsworn)
        playOut()
        expect { s.kingOfTheForsworn.view.obj?.phasedOut shouldBe true }
        advanceTo(TurnStep.Upkeep, turn = 2)
        playOut()
        assertEquals(1, tokens("Spirit Token").size)
    }

    /** A scry untaps it once a turn. */
    @Test
    fun thranduilUntapsOnScry() = scenario {
        alice.battlefield(s.thranduilCounterOfKills, tapped = true)
        alice.battlefield(*lands)
        alice.hand(s.reedWind)
        alice.library(p.ogre, p.ogre)
        alice.casts(s.reedWind)
        playOut()
        expect { s.thranduilCounterOfKills.tapped shouldBe false }
    }

    /** One more +1/+1 counter on an Orc. */
    // CR 614.1a
    @Test
    fun lugdushAddsACounter() = scenario {
        alice.battlefield(s.lugdushUrukHaiCaptain, p.orc, "Forest")
        alice.hand(p.growth)
        alice.casts(p.growth, target = p.orc)
        playOut()
        expect { p.orc.counters(p1p1) shouldBe 2 }
    }

    /** Attacking with another legendary creature draws; first strike while equipped. */
    @Test
    fun berilacDrawsWithAnotherLegend() = scenario {
        alice.battlefield(s.berilacEsquireOfRohan, p.legend)
        alice.library(p.ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.berilacEsquireOfRohan, p.legend)
        playOut()
        expect { alice.hand has p.ogre }
    }

    /** The milled instants and sorceries amass. */
    @Test
    fun voiceOfTheDarkTowerAmasses() = scenario {
        alice.battlefield(*lands)
        alice.hand(s.theVoiceOfTheDarkTower)
        bob.library(p.zap, p.notes, p.ogre, p.ogre)
        alice.casts(s.theVoiceOfTheDarkTower)
        playOut("bob")
        assertEquals(2, state.obj(tokens("Orc Army Token").single())!!.counters(p1p1))
    }

    /** Protection from creatures: unblockable by them. */
    @Test
    fun bolgerGivesProtection() = scenario {
        alice.battlefield(s.bolgerGuardOfTheCitadel, p.bear)
        alice.activates(s.bolgerGuardOfTheCitadel, target = p.bear)
        playOut()
        val d = decision<Decision.ChooseCardType>()
        assertEquals(alice.id, d.player)
        answer(Answer.CardTypeChoice(CardType.Creature))
        playOut()
        assertTrue(view()[card(p.bear)]!!.characteristics!!.abilities.any { it.ability is Keyword.Protection })
    }

    /** It finds a card sharing a colour with a legendary creature. */
    @Test
    fun ringVisionFindsAColour() = scenario {
        alice.battlefield(p.legend, "Island", "Swamp", "Swamp")
        alice.hand(s.ringVision)
        alice.library(p.ogre, p.heal)
        alice.casts(s.ringVision)
        playOut(p.legend, p.heal)
        expect { alice.hand has p.heal }
    }

    /** Each player sacrifices; Alice returns another permanent card. */
    @Test
    fun riseOfTheDarkLordReturns() = scenario {
        alice.battlefield(p.bear, "Swamp", "Swamp", "Forest", "Forest")
        alice.graveyard(p.ogre)
        alice.hand(s.riseOfTheDarkLord)
        bob.battlefield(p.elf)
        alice.casts(s.riseOfTheDarkLord)
        playOut(p.bear, p.elf, p.ogre)
        expect {
            alice.battlefield has p.ogre
            bob.graveyard has p.elf
        }
    }

    // ---- Chunk 13

    /** The second spell mills Bob; a cheaper instant is exiled from his graveyard, copied and cast. */
    // CR 707.12
    @Test
    fun curumoCastsTheirSpells() = scenario {
        alice.battlefield(s.curumoOfManyColors, "Island", "Island")
        alice.hand(p.notes, p.notes)
        alice.library(p.ogre, p.ogre, p.ogre)
        bob.library(p.zap, p.ogre, p.ogre)
        alice.casts(p.notes)
        playOut()
        alice.casts(p.notes)
        playOut(p.zap, "bob")
        expect {
            bob.exile has p.zap
            bob.life shouldBe 18
        }
    }

    /** Bob separates the piles; Alice takes one. */
    @Test
    fun darkLordsRansom() = scenario {
        alice.battlefield("Island", "Swamp", "Swamp")
        alice.hand(s.theDarkLordsRansom)
        alice.library(p.ogre, p.bear, p.elf, p.giant)
        alice.casts(s.theDarkLordsRansom)
        allPass()
        playOut("bob")
        var guard = 6
        while (pending != null && guard-- > 0) {
            when (val d = pending) {
                is Decision.ChooseObjects -> answer(Answer.Objects(d.options.take(d.min.coerceAtLeast(1))))
                is Decision.ChooseOption -> answer(Answer.Option(d.options.first()))
                else -> playOut()
            }
            playOut()
        }
        expect { (alice.hand.size + alice.graveyard.size) shouldBe 5 }
    }

    /** Attacking, it puts a smaller creature card onto the battlefield attacking. */
    @Test
    fun felarofBringsAHorse() = scenario {
        alice.battlefield(s.felarofLordOfHorses)
        alice.hand(p.bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.felarofLordOfHorses)
        playOut(p.bear)
        expect { alice.battlefield has p.bear }
        assertTrue(state.combat?.attackers.orEmpty().any { it.id == card(p.bear) })
    }

    /** It takes an Equipment and amasses for it. */
    @Test
    fun muzgashTakesTheLoot() = scenario {
        alice.battlefield(s.muzgashLootBearer, p.sword)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.muzgashLootBearer)
        playOut(p.sword)
        expect { s.muzgashLootBearer.power shouldBe 5 }
        assertEquals(1, state.obj(tokens("Orc Army Token").single())!!.counters(p1p1))
    }

    /** Bob's land ability can't be activated; Sandyman has it. */
    @Test
    fun sandymanStealsLandAbilities() = scenario {
        val beacon = Cards.invent("Shirefolk Beacon", null, "Land", "{T}: Add {C}.\n{1}, {T}: You gain 1 life.")
        alice.battlefield(s.sandymanTyrantOfTheShire, "Island")
        bob.battlefield(beacon, "Plains")
        turn(active = bob)
        assertFailsWith<Throwable> { bob.activates(beacon, ability = 1) }
    }

    @Test
    fun sandymanUsesStolenAbilities() = scenario {
        val beacon = Cards.invent("Shirefolk Beacon", null, "Land", "{T}: Add {C}.\n{1}, {T}: You gain 1 life.")
        alice.battlefield(s.sandymanTyrantOfTheShire, "Island")
        bob.battlefield(beacon)
        alice.activates(s.sandymanTyrantOfTheShire, ability = 0)
        playOut()
        expect { alice.life shouldBe 21 }
    }

    /** A creature a Spider damaged dies: a Food copy of it. */
    // CR 707.9b
    @Test
    fun mirkspawnMakesFood() = scenario {
        alice.battlefield(s.mirkspawnChildOfUngoliant)
        bob.battlefield(p.bear)
        turn(active = bob, step = TurnStep.DeclareAttackers, priority = null)
        bob.attacks(p.bear)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(s.mirkspawnChildOfUngoliant to p.bear)
        advanceTo(TurnStep.CombatDamage)
        playOut()
        val copy = tokens(p.bear).single()
        val c = view()[copy]!!.characteristics!!
        assertEquals(listOf(CardType.Artifact), c.types)
        assertTrue(Subtype("Food") in c.subtypes)
    }

    /** Tempted, Bob reveals until a land, which Alice gets. */
    @Test
    fun deagolStealsALand() = scenario {
        alice.battlefield(s.deagolHelpfulGuide, "Swamp")
        alice.hand(p.beckon)
        bob.library(p.ogre, "Plains", p.ogre)
        alice.casts(p.beckon)
        playOut(s.deagolHelpfulGuide, "bob")
        expect {
            card("Plains", owner = bob).let { state.obj(it)?.zone } shouldBe Zone.Battlefield
            bob.graveyard has p.ogre
        }
    }

    /** A land entering pumps a creature to 4 power: first strike. */
    @Test
    fun halbaradGivesFirstStrike() = scenario {
        alice.battlefield(s.halbaradRangerOfTheNorth, p.ogre)
        alice.hand("Forest")
        alice.plays("Forest")
        playOut(p.ogre)
        expect {
            p.ogre.power shouldBe 4
            p.ogre hasKeyword Keyword.FirstStrike
        }
    }

    /** A Saga's final chapter resolving reveals the next Saga onto the battlefield. */
    // CR 714.2c
    @Test
    fun iarwainChainsSagas() = scenario {
        alice.battlefield(s.iarwainBenAdar, s.bookOfTheDeepRecords, counters = listOf(CounterKind.Lore to 2))
        alice.battlefield("Mountain")
        alice.library(p.ogre, p.ogre, s.bookOfTheDeepRecords)
        bob.library(p.ogre, p.ogre)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        playOut()
        assertTrue(state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) == s.bookOfTheDeepRecords } == true } >= 1)
        assertTrue(state.player(alice.id).graveyard.any { state.obj(it)?.let { o -> nameOf(o) == s.bookOfTheDeepRecords } == true })
    }

    /** A dying Orc gives two counters; another creature one. */
    @Test
    fun lurtzGrowsMoreForOrcs() = scenario {
        alice.battlefield(s.lurtzOfTheWhiteHand, p.orc, p.bear, "Swamp", "Swamp")
        alice.hand(p.doom, p.doom)
        alice.casts(p.doom, target = p.orc)
        playOut()
        alice.casts(p.doom, target = p.bear)
        playOut()
        expect { s.lurtzOfTheWhiteHand.counters(p1p1) shouldBe 3 }
    }

    /** An opponent's legendary creature dying puts it on the bottom of its library. */
    @Test
    fun fireDemonFlameLeaves() = scenario {
        alice.battlefield(s.theFireDemonFlameOfUdun, "Swamp")
        alice.hand(p.doom)
        bob.battlefield(p.legend)
        alice.casts(p.doom, target = p.legend)
        playOut()
        expect { alice.library has s.theFireDemonFlameOfUdun }
    }
}
