package arcanum.engine.cube

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Chunk 13 of the cube in play: hand disruption and removal, cast the way a player casts them. */
class Chunk13HandDisruptionAndRemovalScenariosTest {
    private fun Scenario.alternative(name: String): CastWay = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
    }.origin)

    /** Whispered Therapy: name a nonland card; the target player discards every card with that name. */
    @Test
    fun whisperedTherapyDiscardsEveryCopyOfTheNamedCard() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Whispered Therapy")
        bob.hand("Storm Spark", "Storm Spark", "Denial", "Forest")

        alice.casts("Whispered Therapy", target = "bob")
        allPass()
        // CR 201.3: the name is chosen on resolution
        assertIs<Decision.ChooseCardName>(pending)
        answer(Answer.CardName("Storm Spark"))

        expect {
            bob.graveyard.names shouldBe listOf("Storm Spark", "Storm Spark")
            bob.hand.names shouldBe listOf("Denial", "Forest")
            alice.graveyard has "Whispered Therapy"
        }
    }

    /** Whispered Therapy's flashback sacrifices a creature instead of mana. */
    @Test
    fun whisperedTherapyFlashbackSacrificingACreature() = scenario {
        // CR 702.34a
        alice.battlefield("Shapeless Exile")
        alice.graveyard("Whispered Therapy")
        bob.hand("Denial", "Forest")

        alice.casts("Whispered Therapy", target = "bob", choosing = listOf("Shapeless Exile"))
        expect {
            alice.graveyard has "Shapeless Exile"
            stack.names shouldBe listOf("Whispered Therapy")
        }
        allPass()
        answer(Answer.CardName("Denial"))

        expect {
            bob.graveyard has "Denial"
            bob.hand.names shouldBe listOf("Forest")
            alice.exile has "Whispered Therapy"
        }
    }

    /** Petty Coercion: the caster picks a noncreature, nonland card from the opponent's hand. */
    @Test
    fun pettyCoercionTakesANoncreatureNonlandCard() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Petty Coercion")
        bob.hiddenHand("Elvish Seer", "Forest", "Storm Spark", "Denial")

        alice.casts("Petty Coercion", target = "bob")
        allPass()
        val choice = decision<Decision.ChooseObjects>()
        assertEquals(alice.id, choice.player)
        assertEquals(setOf(card("Storm Spark"), card("Denial")), choice.options.toSet())
        alice.chooses("Storm Spark")

        expect {
            bob.graveyard.names shouldBe listOf("Storm Spark")
            bob.hand.size shouldBe 3
        }
    }

    /** Mind Pilfer: take a nonland card, then lose 2 life. */
    @Test
    fun mindPilferTakesACardAndCostsLife() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Mind Pilfer")
        bob.hiddenHand("Elvish Seer", "Storm Spark", "Forest")

        alice.casts("Mind Pilfer", target = "bob")
        allPass()
        assertEquals(setOf(card("Elvish Seer"), card("Storm Spark")), decision<Decision.ChooseObjects>().options.toSet())
        alice.chooses("Elvish Seer")

        expect {
            bob.graveyard has "Elvish Seer"
            bob.hand.names shouldBe listOf("Storm Spark", "Forest")
            alice.life shouldBe 18
        }
    }

    /** Inquest of the Deep: only a nonland card with mana value 3 or less can be taken. */
    @Test
    fun inquestOfTheDeepTakesACheapCard() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Inquest of the Deep")
        bob.hiddenHand("Elvish Hierophant", "Elvish Seer", "Deadly Plotting", "Forest")

        alice.casts("Inquest of the Deep", target = "bob")
        allPass()
        assertEquals(setOf(card("Elvish Hierophant"), card("Elvish Seer")), decision<Decision.ChooseObjects>().options.toSet())
        alice.chooses("Elvish Hierophant")

        expect {
            bob.graveyard has "Elvish Hierophant"
            bob.hand.size shouldBe 3
        }
    }

    /** Grim Refrain for {B}: a nonland card with mana value 2 or less. */
    @Test
    fun grimRefrainWithoutCleaveIsLimited() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Grim Refrain")
        bob.hand("Elvish Hierophant", "Storm Spark", "Elvish Seer")

        alice.casts("Grim Refrain", target = "bob")
        allPass()
        assertEquals(setOf(card("Storm Spark"), card("Elvish Seer")), decision<Decision.ChooseObjects>().options.toSet())
        alice.chooses("Storm Spark")

        expect { bob.graveyard has "Storm Spark" }
    }

    /** Grim Refrain cleaved: the bracketed limit is gone, any nonland card. */
    @Test
    fun grimRefrainCleavedTakesAnyNonlandCard() = scenario {
        // CR 702.148a
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand("Grim Refrain")
        bob.hand("Elvish Hierophant", "Storm Spark", "Forest")

        alice.casts("Grim Refrain", way = alternative("Grim Refrain"), target = "bob")
        expect { (0..2).all { card("Swamp", it).tapped } shouldBe true }
        allPass()
        assertEquals(setOf(card("Elvish Hierophant"), card("Storm Spark")), decision<Decision.ChooseObjects>().options.toSet())
        alice.chooses("Elvish Hierophant")

        expect { bob.graveyard has "Elvish Hierophant" }
    }

    /** Visions of Iron and Bone: exile an artifact or creature card from the hand and one from the graveyard. */
    @Test
    fun visionsOfIronAndBoneExilesFromHandAndGraveyard() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Visions of Iron and Bone")
        bob.hand("Elvish Seer", "Lotus Frond", "Storm Spark")
        bob.graveyard("Tinkered Mite", "Thicketwood Elves", "Denial")

        alice.casts("Visions of Iron and Bone", target = "bob")
        allPass()
        assertEquals(setOf(card("Elvish Seer"), card("Lotus Frond")), decision<Decision.ChooseObjects>().options.toSet())
        alice.chooses("Elvish Seer")
        assertEquals(setOf(card("Tinkered Mite"), card("Thicketwood Elves")), decision<Decision.ChooseObjects>().options.toSet())
        alice.chooses("Tinkered Mite")

        expect {
            bob.exile.names shouldBe listOf("Elvish Seer", "Tinkered Mite")
            bob.hand.names shouldBe listOf("Lotus Frond", "Storm Spark")
            bob.graveyard.names shouldBe listOf("Thicketwood Elves", "Denial")
        }
    }

    /** Communal Cruelty with all three modes: two escalate discards; discard, -2/-2 and a drain. */
    @Test
    fun communalCrueltyEscalatedToAllModes() = scenario {
        // CR 702.120a
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Communal Cruelty", "Forest", "Island")
        bob.battlefield("Thicketwood Elves")
        bob.hand("Storm Spark", "Cinder Bolt", "Elvish Seer")

        alice.casts("Communal Cruelty", modes = listOf(0, 1, 2), targets = listOf("bob", "Thicketwood Elves", "bob"),
            choosing = listOf("Forest", "Island"))
        expect {
            alice.graveyard.names shouldBe listOf("Forest", "Island")
            alice.hand.size shouldBe 0
        }
        allPass()
        alice.chooses("Storm Spark")

        expect {
            bob.graveyard has "Storm Spark"
            bob.graveyard has "Thicketwood Elves"
            bob.hand.names shouldBe listOf("Cinder Bolt", "Elvish Seer")
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    /** Crow's Larceny: the target player discards a card of their choice; retrace casts it again from the graveyard. */
    @Test
    fun crowsLarcenyThenRetrace() = scenario {
        // CR 702.81a
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Crow's Larceny", "Forest")
        bob.hand("Storm Spark", "Denial", "Forest")

        alice.casts("Crow's Larceny", target = "bob")
        allPass()
        assertEquals(bob.id, decision<Decision.ChooseObjects>().player)
        bob.chooses("Denial")
        expect { alice.graveyard has "Crow's Larceny" }

        alice.casts("Crow's Larceny", target = "bob", choosing = listOf("Forest"))
        allPass()
        bob.chooses("Storm Spark")

        expect {
            bob.graveyard.names shouldBe listOf("Denial", "Storm Spark")
            bob.hand.names shouldBe listOf("Forest")
            alice.graveyard.names shouldBe listOf("Forest", "Crow's Larceny")
        }
    }

    /** Sever Limb: {1}{B/P}{B/P} paid with one Swamp and 4 life; the creature gets -5/-5 and dies. */
    @Test
    fun severLimbPaidWithLife() = scenario {
        // CR 107.4f
        alice.battlefield("Swamp")
        alice.hand("Sever Limb")
        bob.battlefield("Wall of Portents")

        perform(Input.Cast(alice.id, card("Sever Limb")))
        answer(Answer.Targets(listOf(target("Wall of Portents"))))
        val payment = decision<Decision.ManaPayment>()
        answer(Answer.ManaPayment(listOf(
            payment.options.filterIsInstance<ManaUse.Ability>().first { it.source == card("Swamp") },
            ManaUse.Life(1), ManaUse.Life(2),
        )))
        expect {
            alice.life shouldBe 16
            stack.names shouldBe listOf("Sever Limb")
        }
        allPass()

        expect {
            bob.graveyard has "Wall of Portents"
            alice.graveyard has "Sever Limb"
        }
    }

    /** Banish Below: the creature goes second from the top of its owner's library; its controller gains 3 life. */
    @Test
    fun banishBelowTucksTheCreature() = scenario {
        alice.battlefield("Plains")
        alice.hand("Banish Below")
        bob.battlefield("Elvish Seer")
        bob.library("Forest", "Island")

        alice.casts("Banish Below", target = "Elvish Seer")
        allPass()

        expect {
            bob.library.names shouldBe listOf("Forest", "Elvish Seer", "Island")
            bob.life shouldBe 23
        }
    }

    /** Flash of Spite for free by sacrificing a black creature: each opponent sacrifices their most expensive creature
     * or planeswalker. */
    @Test
    fun flashOfSpiteAlternativeCostAndGreatestManaValue() = scenario {
        // CR 118.9, 101.4
        alice.battlefield("Shapeless Exile")
        alice.hand("Flash of Spite")
        bob.battlefield("Elvish Seer", "Elvish Hierophant")

        alice.casts("Flash of Spite", way = alternative("Flash of Spite"), choosing = listOf("Shapeless Exile"))
        expect { alice.graveyard has "Shapeless Exile" }
        allPass()

        expect {
            bob.graveyard.names shouldBe listOf("Elvish Hierophant")
            bob.battlefield has "Elvish Seer"
        }
    }

    /** Flash of Spite: a tie for the greatest mana value lets the opponent choose. */
    @Test
    fun flashOfSpiteTieIsTheOpponentsChoice() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand("Flash of Spite")
        bob.battlefield("Elvish Seer", "Thicketwood Elves")

        alice.casts("Flash of Spite")
        allPass()
        val choice = decision<Decision.ChooseObjects>()
        assertEquals(bob.id, choice.player)
        bob.chooses("Thicketwood Elves")

        expect {
            bob.graveyard.names shouldBe listOf("Thicketwood Elves")
            bob.battlefield has "Elvish Seer"
        }
    }

    /** Deadly Plotting convoked: destroy the creature; each creature that convoked connives. */
    @Test
    fun deadlyPlottingConvokedCreaturesConnive() = scenario {
        // CR 702.51a, 701.50a
        alice.battlefield("Swamp", "Onyx Bauble", "Corpse Gnawer", "Tinkered Mite")
        alice.library("Storm Spark", "Forest")
        bob.battlefield("Elvish Hierophant")
        alice.hand("Deadly Plotting")

        alice.casts("Deadly Plotting", target = "Elvish Hierophant", paying = listOf("Swamp", "Onyx Bauble", "Corpse Gnawer", "Tinkered Mite"))
        allPass()
        // Each conniving creature's controller draws, then discards (one connive at a time)
        while (pending is Decision.ChooseObjects) {
            val options = decision<Decision.ChooseObjects>().options
            alice.chooses(if (card("Storm Spark") in options) "Storm Spark" else "Forest")
        }

        expect {
            bob.graveyard has "Elvish Hierophant"
            alice.graveyard has "Storm Spark"
            alice.graveyard has "Forest"
            // The creature that discarded the nonland card got a +1/+1 counter
            (card("Corpse Gnawer").counters(CounterKind.PowerToughness(1, 1)) + card("Tinkered Mite").counters(CounterKind.PowerToughness(1, 1))) shouldBe 1
        }
    }

    /** Procession of Pale Light: exiling white cards from hand makes it {2} cheaper each; X limits the mana value. */
    @Test
    fun processionOfPaleLightExilingWhiteCards() = scenario {
        // CR 601.2f
        alice.battlefield("Plains")
        alice.hand("Procession of Pale Light", "Wall of Portents")
        bob.battlefield("Elvish Seer", "Elvish Hierophant")

        perform(Input.Cast(alice.id, card("Procession of Pale Light")))
        answer(Answer.Number(2))
        // Mana value 2 or less: the Hierophant (3) can't be targeted
        assertTrue(target("Elvish Hierophant") !in decision<Decision.Targets>().options)
        answer(Answer.Targets(listOf(target("Elvish Seer"))))
        alice.chooses("Wall of Portents")
        assertEquals("{W}", decision<Decision.ManaPayment>().cost.toString())
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()

        expect {
            alice.exile has "Wall of Portents"
            bob.exile has "Elvish Seer"
            bob.battlefield has "Elvish Hierophant"
        }
    }

    /** Vermin Outbreak with X=1: destroy an artifact, create two Pests that each gain 1 life when they die. */
    @Test
    fun verminOutbreakDestroysAndMakesPests() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Mountain")
        alice.hand("Vermin Outbreak", "Storm Spark")
        bob.battlefield("Lotus Frond", "Frontier Wandering")

        alice.casts("Vermin Outbreak", x = 1, target = "Lotus Frond", paying = listOf("Forest", "Forest#1", "Forest#2"))
        allPass()

        expect {
            bob.graveyard has "Lotus Frond"
            bob.battlefield has "Frontier Wandering"
            alice.battlefield.names.count { it == "Pest Token" } shouldBe 2
            "Pest Token".power shouldBe 1
        }

        // The Pest's own dies trigger
        alice.casts("Storm Spark", target = "Pest Token")
        allPass()
        expect { stack.names shouldBe listOf("ability of Pest Token") }
        allPass()
        expect {
            alice.life shouldBe 21
            alice.battlefield.names.count { it == "Pest Token" } shouldBe 1
        }
    }
}
