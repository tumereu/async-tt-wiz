package arcanum.engine.sets.ecl

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ecl.Ecl.bear
import arcanum.engine.sets.ecl.Ecl.elemental
import arcanum.engine.sets.ecl.Ecl.elf
import arcanum.engine.sets.ecl.Ecl.goblin
import arcanum.engine.sets.ecl.Ecl.jolt
import arcanum.engine.sets.ecl.Ecl.m1m1
import arcanum.engine.sets.ecl.Ecl.merfolk
import arcanum.engine.sets.ecl.Ecl.ogre
import arcanum.engine.sets.ecl.Ecl.redBear
import arcanum.engine.sets.ecl.Ecl.totem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Lorwyn Eclipsed's blight and behold cards in play (chunks 1–2): blight as a cost of graveyard and Aura abilities, optional blight
 * that adds a mode or gain, blight on resolution with "if you do" / "if you don't", and behold-or-pay creatures with their triggers. */
class EclBlightAndBeholdScenariosTest {
    private val wingshadeBoon = Cards.invent("Wingshade Boon", "{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+0 and has flying.
        {1}{W}, Blight 2: Return this card from your graveyard to your hand. Activate only as a sorcery. (To blight 2, put two -1/-1 counters on a creature you control.)
    """)
    private val costlyReprisal = Cards.invent("Costly Reprisal", "{2}{W}", "Instant", """
        As an additional cost to cast this spell, you may blight 2. (You may put two -1/-1 counters on a creature you control.)
        Choose one. If this spell's additional cost was paid, choose both instead.
        • Destroy target artifact or enchantment.
        • Destroy target creature with mana value 3 or greater.
    """)
    private val driftIntoStillness = Cards.invent("Drift into Stillness", "{1}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature can't attack or block.
        {1}{W}, Blight 1, Sacrifice this Aura: Exile enchanted creature. (To blight 1, put a -1/-1 counter on a creature you control.)
    """)
    private val sickenedThornbark = Cards.invent("Sickened Thornbark", "{4}{B}", "Creature — Treefolk Warlock", "Whenever this creature enters or attacks, you may blight 2. If you do, you draw a card and lose 1 life. (To blight 2, put two -1/-1 counters on a creature you control.)", "3/7")
    private val hobgoblinRevelry = Cards.invent("Hobgoblin Revelry", "{2}{B}", "Kindred Enchantment — Goblin", """
        When this enchantment enters, you may blight 1. If you do, create two 1/1 black and red Goblin creature tokens. (To blight 1, put a -1/-1 counter on a creature you control.)
        Whenever a Goblin creature you control dies, each opponent loses 1 life and you gain 1 life.
    """)
    private val mirecrawlersGrip = Cards.invent("Mirecrawler's Grip", "{1}{B}", "Sorcery", """
        As an additional cost to cast this spell, blight 1 or pay {3}. (To blight 1, put a -1/-1 counter on a creature you control.)
        Exile target creature.
    """)
    private val paragonOfTheUncanny = Cards.invent("Paragon of the Uncanny", "{3}{B}", "Creature — Goblin Berserker", """
        As an additional cost to cast this spell, behold a Goblin and exile it. (Exile a Goblin you control or a Goblin card from your hand.)
        Pay 1 life, Blight 2: Target opponent blights 2. Activate only as a sorcery.
        When this creature leaves the battlefield, return the exiled card to its owner's hand.
    """, "5/5")
    private val draughtOfDuskshine = Cards.invent("Draught of Duskshine", "{4}{B}", "Instant", "Return target creature card from your graveyard to the battlefield. Then if it isn't your main phase, blight 2. (Put two -1/-1 counters on a creature you control.)")
    private val slumberSnatcher = Cards.invent("Slumber Snatcher", "{3}{B}", "Creature — Faerie Rogue", """
        Flying
        When this creature enters, you may blight 1. If you do, each opponent discards a card. (To blight 1, put a -1/-1 counter on a creature you control.)
    """, "3/2")
    private val bellyripperCrew = Cards.invent("Bellyripper Crew", "{3}{B}", "Creature — Goblin Berserker", "At the beginning of your first main phase, you may blight 2. If you don't, you lose 3 life. (To blight 2, put two -1/-1 counters on a creature you control.)", "6/6")
    private val repayingJinx = Cards.invent("Repaying Jinx", "{B}", "Instant", """
        As an additional cost to cast this spell, you may blight 1. (You may put a -1/-1 counter on a creature you control.)
        Destroy target creature with mana value 2 or less. If this spell's additional cost was paid, you gain 2 life.
    """)
    private val searingInquisitiveness = Cards.invent("Searing Inquisitiveness", "{2}{R}", "Sorcery", """
        As an additional cost to cast this spell, you may blight 1. (You may put a -1/-1 counter on a creature you control.)
        Exile the top two cards of your library. If this spell's additional cost was paid, exile the top three cards instead. Until the end of your next turn, you may play those cards.
    """)
    private val sinewGobbler = Cards.invent("Sinew Gobbler", "{1}{R}", "Creature — Goblin Scout", "{T}, Blight 1: Discard a card. If you do, draw a card. (To blight 1, put a -1/-1 counter on a creature you control.)", "1/3")
    private val grimebackForager = Cards.invent("Grimeback Forager", "{1}{R}", "Creature — Goblin Warrior", """
        At the beginning of your first main phase, you may blight 1. If you do, create a Treasure token. (To blight 1, put a -1/-1 counter on a creature you control. A Treasure token is an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """, "3/2")
    private val burrowFlamekeeper = Cards.invent("Burrow Flamekeeper", "{1}{R}", "Creature — Goblin Warrior", "At the beginning of combat on your turn, you may blight 1. When you do, target creature gains haste until end of turn. (To blight 1, put a -1/-1 counter on a creature you control.)", "2/2")
    private val havocRetcher = Cards.invent("Havoc Retcher", "{2}{B/R}", "Creature — Goblin Warlock", "When this creature enters, you may pay {2}. If you don't, blight 2. (To blight 2, put two -1/-1 counters on a creature you control.)", "5/4")
    private val gloomImp = Cards.invent("Gloom Imp", "{2}{B/R}", "Creature — Ouphe", """
        Whenever this creature attacks, blight 1. (Put a -1/-1 counter on a creature you control.)
        Whenever a creature you control with one or more counters on it dies, exile that many cards from the top of your library. Until your next end step, you may play those cards.
    """, "3/4")
    private val exemplarsOfTheReef = Cards.invent("Exemplars of the Reef", "{3}{U}", "Creature — Merfolk Soldier", """
        As an additional cost to cast this spell, behold a Merfolk and exile it. (Exile a Merfolk you control or a Merfolk card from your hand.)
        Whenever this creature enters or becomes tapped, tap up to one target creature and put a stun counter on it.
        When this creature leaves the battlefield, return the exiled card to its owner's hand.
    """, "4/6")
    private val brightfinTutor = Cards.invent("Brightfin Tutor", "{1}{U}", "Creature — Merfolk Wizard", """
        As an additional cost to cast this spell, behold a Merfolk or pay {2}. (To behold a Merfolk, choose a Merfolk you control or reveal a Merfolk card from your hand.)
        When this creature enters, create a 1/1 white and blue Merfolk creature token.
    """, "2/1")
    private val mudknotHexflinger = Cards.invent("Mudknot Hexflinger", "{B}", "Creature — Goblin Warlock", """
        As an additional cost to cast this spell, behold a Goblin or pay {2}. (To behold a Goblin, choose a Goblin you control or reveal a Goblin card from your hand.)
        This creature can't block.
        When this creature dies, destroy target creature an opponent controls with power 2 or less.
    """, "2/1")
    private val exemplarOfTheTrail = Cards.invent("Exemplar of the Trail", "{3}{R}", "Creature — Elemental Sorcerer", """
        As an additional cost to cast this spell, behold an Elemental and exile it. (Exile an Elemental you control or an Elemental card from your hand.)
        Whenever another Elemental you control enters, it deals damage equal to its power to each opponent.
        When this creature leaves the battlefield, return the exiled card to its owner's hand.
    """, "7/3")
    private val stokeTheHiddenBlaze = Cards.invent("Stoke the Hidden Blaze", "{3}{R}", "Kindred Sorcery — Elemental", """
        Create a token that's a copy of target creature you control, except it has haste and "At the beginning of the end step, sacrifice this token."
        Flashback—{1}{R}, Behold three Elementals. (You may cast this card from your graveyard for its flashback cost. Then exile it. To behold an Elemental, choose an Elemental you control or reveal an Elemental card from your hand.)
    """)
    private val spiritglowSearcher = Cards.invent("Spiritglow Searcher", "{R}", "Creature — Elemental Sorcerer", """
        As an additional cost to cast this spell, behold an Elemental or pay {2}. (To behold an Elemental, choose an Elemental you control or reveal an Elemental card from your hand.)
        {R}: Target creature you control gains trample until end of turn. If this is the third time this ability has resolved this turn, add {R}{R}{R}{R}.
    """, "2/1")
    private val exemplarsOfTheFlawless = Cards.invent("Exemplars of the Flawless", "{3}{G}", "Creature — Elf Warrior", """
        As an additional cost to cast this spell, behold an Elf and exile it. (Exile an Elf you control or an Elf card from your hand.)
        Whenever you cast a creature spell, draw a card.
        When this creature leaves the battlefield, return the exiled card to its owner's hand.
    """, "6/6")
    private val lysValenEnvoy = Cards.invent("Lys Valen Envoy", "{1}{G}", "Creature — Elf Advisor", """
        As an additional cost to cast this spell, behold an Elf or pay {2}. (To behold an Elf, choose an Elf you control or reveal an Elf card from your hand.)
        {T}: Add {G}{G}. Activate only if there is an Elf card in your graveyard.
    """, "2/3")

    // CR 303.4, 613.4c: the Aura's bonus and flying
    @Test
    fun wingshadeBoonEnchants() = scenario {
        alice.battlefield("Plains", bear)
        alice.hand(wingshadeBoon)
        alice.casts(wingshadeBoon, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Flying
        }
    }

    // CR 602.1, 701.68a: blight 2 as part of a graveyard ability's cost
    @Test
    fun wingshadeBoonReturnsFromTheGraveyard() = scenario {
        alice.battlefield("Plains", "Plains", ogre)
        alice.graveyard(wingshadeBoon)
        alice.activates(wingshadeBoon, choosing = listOf(ogre))
        resolveAll()
        expect {
            alice.hand has wingshadeBoon
            ogre.counters(m1m1) shouldBe 2
        }
    }

    // CR 700.2d: with the additional cost paid, both modes
    @Test
    fun costlyReprisalChoosesBothWhenBlighted() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", Ecl.giant)
        alice.hand(costlyReprisal)
        bob.battlefield(totem, ogre)
        alice.casts(costlyReprisal, targets = listOf(totem, ogre), modes = listOf(0, 1), kicked = 1, choosing = listOf(Ecl.giant))
        resolveAll()
        expect {
            bob.graveyard has totem
            bob.graveyard has ogre
            Ecl.giant.counters(m1m1) shouldBe 2
        }
    }

    @Test
    fun costlyReprisalOneModeWithoutBlight() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(costlyReprisal)
        bob.battlefield(totem, ogre)
        alice.casts(costlyReprisal, targets = listOf(ogre), modes = listOf(1), kicked = 0)
        resolveAll()
        expect {
            bob.battlefield has totem
            bob.graveyard has ogre
        }
    }

    // CR 508.1c, 509.1b: it can't attack or block; sacrificing the Aura and blighting exiles the creature
    @Test
    fun driftIntoStillness() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(driftIntoStillness)
        bob.battlefield(ogre)
        alice.casts(driftIntoStillness, target = ogre)
        resolveAll()
        expect {
            ogre hasMechanic { it is Mechanic.CantAttack }
            ogre hasMechanic { it is Mechanic.CantBlock }
        }
        alice.activates(driftIntoStillness, choosing = listOf(bear))
        resolveAll()
        expect {
            bob.exile has ogre
            alice.graveyard has driftIntoStillness
            bear.counters(m1m1) shouldBe 1
        }
    }

    // CR 603.5, 608.2c: "you may blight 2. If you do, you draw a card and lose 1 life"
    @Test
    fun sickenedThornbarkBlightsToDraw() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(sickenedThornbark)
        alice.library(bear)
        alice.casts(sickenedThornbark)
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses(sickenedThornbark)
        resolveAll()
        expect {
            sickenedThornbark.counters(m1m1) shouldBe 2
            alice.hand has bear
        }
        assertEquals(19, alice.life)
    }

    // the tokens come only with the blight; a Goblin of yours dying drains
    @Test
    fun hobgoblinRevelry() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain", bear)
        alice.hand(hobgoblinRevelry, jolt)
        alice.casts(hobgoblinRevelry, paying = listOf("Swamp", "Swamp", "Swamp"))
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Goblin Token" } shouldBe 2 }
        alice.casts(jolt, target = "Goblin Token")
        resolveAll()
        assertEquals(19, bob.life)
        assertEquals(21, alice.life)
    }

    // CR 601.2b: "blight 1 or pay {3}": the blight is chosen
    @Test
    fun mirecrawlersGripBlights() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.hand(mirecrawlersGrip)
        bob.battlefield(ogre)
        alice.casts(mirecrawlersGrip, target = ogre, option = "Blight", choosing = listOf(bear))
        resolveAll()
        expect {
            bob.exile has ogre
            bear.counters(m1m1) shouldBe 1
        }
    }

    // CR 701.4a, 701.68a: beheld and exiled from the hand; the opponent blights one of their own creatures
    @Test
    fun paragonOfTheUncannyMakesAnOpponentBlight() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(paragonOfTheUncanny, goblin)
        bob.battlefield(ogre)
        alice.casts(paragonOfTheUncanny, option = "Exile from Hand", choosing = listOf(goblin))
        resolveAll()
        expect { alice.exile has goblin }
        alice.activates(paragonOfTheUncanny, target = "bob", choosing = listOf(paragonOfTheUncanny))
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses(ogre)
        resolveAll()
        expect {
            paragonOfTheUncanny.counters(m1m1) shouldBe 2
            ogre.counters(m1m1) shouldBe 2
        }
        assertEquals(19, alice.life)
    }

    // CR 505.1: outside your main phase, the returned creature comes with a blight
    @Test
    fun draughtOfDuskshineOutsideYourMainPhase() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(draughtOfDuskshine)
        alice.graveyard(ogre)
        turn(active = bob)
        bob.passes()
        alice.casts(draughtOfDuskshine, target = ogre)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            alice.battlefield has ogre
            ogre.counters(m1m1) shouldBe 2
        }
    }

    @Test
    fun draughtOfDuskshineInYourMainPhase() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(draughtOfDuskshine)
        alice.graveyard(ogre)
        alice.casts(draughtOfDuskshine, target = ogre)
        resolveAll()
        expect {
            alice.battlefield has ogre
            ogre.counters(m1m1) shouldBe 0
        }
    }

    // CR 701.9a: each opponent discards a card
    @Test
    fun slumberSnatcherMakesOpponentsDiscard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(slumberSnatcher)
        bob.hand(bear)
        alice.casts(slumberSnatcher)
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses(slumberSnatcher)
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses(bear)
        resolveAll()
        expect {
            bob.graveyard has bear
            slumberSnatcher.counters(m1m1) shouldBe 1
        }
    }

    // CR 505.1a: "your first main phase"; without the blight, 3 life
    @Test
    fun bellyripperCrewCostsLifeWithoutBlight() = scenario {
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.battlefield(bellyripperCrew)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        say(false)
        resolveAll()
        assertEquals(17, alice.life)
    }

    @Test
    fun bellyripperCrewBlights() = scenario {
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.battlefield(bellyripperCrew)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses(bellyripperCrew)
        resolveAll()
        assertEquals(20, alice.life)
        expect { bellyripperCrew.counters(m1m1) shouldBe 2 }
    }

    // CR 601.2f: the optional cost paid adds the life gain
    @Test
    fun repayingJinxGainsWhenBlighted() = scenario {
        alice.battlefield("Swamp", ogre)
        alice.hand(repayingJinx)
        bob.battlefield(redBear)
        alice.casts(repayingJinx, target = redBear, kicked = 1, choosing = listOf(ogre))
        resolveAll()
        expect { bob.graveyard has redBear }
        assertEquals(22, alice.life)
    }

    // a creature of mana value 3 isn't a legal target
    @Test
    fun repayingJinxOnlyCheapCreatures() = scenario {
        alice.battlefield("Swamp")
        alice.hand(repayingJinx)
        bob.battlefield(ogre)
        assertFailsWith<IllegalArgumentException> { alice.casts(repayingJinx, target = ogre, kicked = 0) }
    }

    // CR 601.2f: three cards instead of two, playable until the end of the next turn
    @Test
    fun searingInquisitivenessExilesThreeWhenBlighted() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", ogre)
        alice.hand(searingInquisitiveness)
        alice.library(bear, elf, "Forest", redBear)
        alice.casts(searingInquisitiveness, kicked = 1, choosing = listOf(ogre))
        resolveAll()
        expect {
            alice.exile has bear
            alice.exile has elf
            alice.exile has "Forest"
            alice.library has redBear
        }
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    // CR 608.2c: "Discard a card. If you do, draw a card."
    @Test
    fun sinewGobblerRummages() = scenario {
        alice.battlefield(sinewGobbler)
        alice.hand(ogre)
        alice.library(bear)
        alice.activates(sinewGobbler, choosing = listOf(sinewGobbler))
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.hand has bear
            sinewGobbler.counters(m1m1) shouldBe 1
        }
    }

    // CR 111.10a: a Treasure for the blight
    @Test
    fun grimebackForagerMakesATreasure() = scenario {
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.battlefield(grimebackForager)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses(grimebackForager)
        resolveAll()
        expect { alice.battlefield has "Treasure Token" }
    }

    // CR 603.12: a reflexive trigger: haste for a creature cast this turn
    @Test
    fun burrowFlamekeeperGivesHaste() = scenario {
        alice.battlefield(burrowFlamekeeper, ogre)
        alice.battlefield(bear, sick = true)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target(bear))))
        resolveAll()
        expect {
            bear hasKeyword Keyword.Haste
            ogre.counters(m1m1) shouldBe 1
        }
    }

    // CR 118.12: "you may pay {2}. If you don't, blight 2."
    @Test
    fun havocRetcherBlightsUnlessPaid() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(havocRetcher)
        alice.casts(havocRetcher)
        resolveAll()
        say(false)
        if (pending is Decision.ChooseObjects) alice.chooses(havocRetcher)
        resolveAll()
        expect { havocRetcher.counters(m1m1) shouldBe 2 }
    }

    @Test
    fun havocRetcherPaid() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(havocRetcher)
        alice.casts(havocRetcher, paying = listOf("Swamp", "Swamp", "Swamp"))
        resolveAll()
        say(true)
        paySuggested()
        resolveAll()
        expect { havocRetcher.counters(m1m1) shouldBe 0 }
    }

    // CR 603.10a: the creature's counters as it last existed: that many cards exiled, playable
    @Test
    fun gloomImpExilesForACreatureWithCounters() = scenario {
        alice.library("Forest", bear)
        bob.library(bear, bear)
        alice.battlefield("Mountain", gloomImp, redBear)
        alice.hand(jolt)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(gloomImp)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(redBear)
        resolveAll()
        expect { redBear.counters(m1m1) shouldBe 1 }
        alice.casts(jolt, target = redBear)
        resolveAll()
        expect {
            alice.graveyard has redBear
            alice.exile has "Forest"
            alice.library has bear
        }
        advanceTo(TurnStep.PostcombatMain)
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    // CR 701.4a, 701.26: beheld from the hand and exiled; the enters trigger taps and stuns
    @Test
    fun exemplarsOfTheReefStuns() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(exemplarsOfTheReef, merfolk)
        bob.battlefield(ogre)
        alice.casts(exemplarsOfTheReef, option = "Exile from Hand", choosing = listOf(merfolk))
        resolveAll()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target(ogre))))
        resolveAll()
        expect {
            alice.exile has merfolk
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 1
        }
    }

    // behold (reveal from the hand) instead of {2}
    @Test
    fun brightfinTutorBeholds() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(brightfinTutor, merfolk)
        alice.casts(brightfinTutor, option = "Behold", choosing = listOf(merfolk))
        resolveAll()
        expect {
            alice.hand has merfolk
            alice.battlefield has "Merfolk Token"
        }
    }

    // CR 509.1a: it can't block; dying, it destroys a small creature of the opponent
    @Test
    fun mudknotHexflingerDies() = scenario {
        alice.battlefield("Mountain", mudknotHexflinger)
        alice.hand(jolt)
        bob.battlefield(redBear, ogre)
        expect { mudknotHexflinger hasMechanic { it is Mechanic.CantBlock } }
        alice.casts(jolt, target = mudknotHexflinger)
        resolveAll()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target(redBear))))
        resolveAll()
        expect {
            bob.graveyard has redBear
            bob.battlefield has ogre
        }
    }

    // another Elemental entering deals its power to each opponent
    @Test
    fun exemplarOfTheTrailDamagesWithElementals() = scenario {
        alice.battlefield("Mountain", "Mountain", exemplarOfTheTrail)
        alice.hand(elemental)
        alice.casts(elemental)
        resolveAll()
        assertEquals(18, bob.life)
    }

    // CR 707.9b, 603.7: a hasty copy that is sacrificed at the end step
    @Test
    fun stokeTheHiddenBlazeCopiesForATurn() = scenario {
        alice.library(bear)
        bob.library(bear)
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", ogre)
        alice.hand(stokeTheHiddenBlaze)
        alice.casts(stokeTheHiddenBlaze, target = ogre)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Plaza Ogre" } shouldBe 2
            card("Plaza Ogre", 1) hasKeyword Keyword.Haste
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Plaza Ogre" } shouldBe 1 }
    }

    // CR 702.34a: flashback paid with {1}{R} and three beheld Elementals
    @Test
    fun stokeTheHiddenBlazeFlashback() = scenario {
        alice.battlefield("Mountain", "Mountain", elemental, Ecl.bigElemental, exemplarOfTheTrail)
        alice.graveyard(stokeTheHiddenBlaze)
        alice.casts(stokeTheHiddenBlaze, target = Ecl.bigElemental, choosing = listOf(elemental, Ecl.bigElemental, exemplarOfTheTrail))
        resolveAll()
        expect {
            alice.exile has stokeTheHiddenBlaze
            alice.battlefield.names.count { it == "Ashen Colossus" } shouldBe 2
        }
        // the copy is another Elemental entering: the Exemplar of the Trail deals its power
        assertEquals(15, bob.life)
    }

    // CR 707.10b: the third resolution this turn adds {R}{R}{R}{R}
    @Test
    fun spiritglowSearcherThirdResolution() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", spiritglowSearcher)
        repeat(2) {
            alice.activates(spiritglowSearcher, target = spiritglowSearcher)
            resolveAll()
        }
        expect { alice.pool.size shouldBe 0 }
        alice.activates(spiritglowSearcher, target = spiritglowSearcher)
        resolveAll()
        expect {
            spiritglowSearcher hasKeyword Keyword.Trample
            alice.pool.size shouldBe 4
        }
    }

    // a creature spell cast draws a card
    @Test
    fun exemplarsOfTheFlawlessDraws() = scenario {
        alice.battlefield("Forest", exemplarsOfTheFlawless)
        alice.hand(elf)
        alice.library(bear)
        alice.casts(elf)
        resolveAll()
        expect { alice.hand has bear }
    }

    // CR 602.5b: only with an Elf card in the graveyard
    @Test
    fun lysValenEnvoyNeedsAnElfInTheGraveyard() = scenario {
        alice.battlefield(lysValenEnvoy)
        assertFailsWith<IllegalArgumentException> { alice.activates(lysValenEnvoy) }
    }

    @Test
    fun lysValenEnvoyAddsTwoGreen() = scenario {
        alice.battlefield(lysValenEnvoy)
        alice.graveyard(elf)
        alice.activates(lysValenEnvoy)
        expect { alice.pool.size shouldBe 2 }
    }
}
