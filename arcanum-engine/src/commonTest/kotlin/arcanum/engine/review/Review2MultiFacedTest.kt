package arcanum.engine.review

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 2: modal double-faced, transforming and Adventure cards in each zone (CR 712.8, 715.4): outside the
 * battlefield and the Stack only the front face counts; a transformed permanent's mana value is its front face's.
 */
class Review2MultiFacedTest {
    /** Sets [name] up with its back face up (setup only). */
    private fun Scenario.backFaceUp(name: String) {
        val id = card(name)
        state.objects[id] = state.objOrThrow(id).copy(face = 1)
    }

    /** A modal double-faced card with a land back face is a creature card in the graveyard: Knight of the Relic Vault
     * counts only the Forest. */
    // CR 712.8a
    @Test
    fun anMdfcInTheGraveyardIsItsFrontFace() = scenario {
        alice.battlefield("Knight of the Relic Vault")
        alice.graveyard("Forest", "Summit Ascetic // Hermit Peak")
        expect { "Knight of the Relic Vault".power shouldBe 3 }
    }

    /** "Search your library for a land card" doesn't find a modal double-faced card whose back face is a land. */
    // CR 712.8a, 701.23a
    @Test
    fun aSearchForALandDoesntFindTheBackFace() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand("Field Exchange")
        alice.library("Summit Ascetic // Hermit Peak", "Island")
        alice.casts("Field Exchange", choosing = listOf("Forest"))
        allPass()
        val d = pending
        if (d is Decision.ChooseObjects) assertEquals(listOf(card("Island")), d.options)
        else expect { alice.battlefield has "Island" }
    }

    /** Played as its back face, Hermit Peak is a land with mana value 0 on the battlefield. */
    // CR 712.8f, 202.3b
    @Test
    fun aBackFaceLandHasManaValueZero() = scenario {
        alice.hand("Summit Ascetic // Hermit Peak")
        alice.plays("Hermit Peak", face = 1)
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        expect {
            card("Summit Ascetic // Hermit Peak").view.characteristics!!.manaValue() shouldBe 0
            card("Summit Ascetic // Hermit Peak").view.name shouldBe "Hermit Peak"
        }
    }

    /** In a hand, an Adventurer card is only its creature: "choose a noncreature, nonland card" can't take it. */
    // CR 715.4
    @Test
    fun anAdventurerInHandIsOnlyACreature() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Petty Coercion")
        bob.hand("Brash Pilferer // Petty Pinch", "Storm Spark", "Denial")
        alice.casts("Petty Coercion", target = "bob")
        allPass()
        val d = decision<Decision.ChooseObjects>()
        assertTrue(card("Brash Pilferer // Petty Pinch") !in d.options, "${d.options}")
    }

    /** An Adventure that's countered goes to the graveyard, not to exile. */
    // CR 715.4
    @Test
    fun aCounteredAdventureGoesToTheGraveyard() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Brash Pilferer // Petty Pinch")
        bob.battlefield("Elvish Seer", "Island", "Island")
        bob.hand("Denial")
        alice.casts("Brash Pilferer // Petty Pinch", way = CastWay(face = 1), target = "Elvish Seer")
        alice.passes()
        bob.casts("Denial", target = "Brash Pilferer // Petty Pinch")
        allPass()
        expect { alice.graveyard has "Brash Pilferer // Petty Pinch" }
    }

    /** A transformed permanent's mana value is its front face's: Risen Leviathan has mana value 2, so Rescind with X 2
     * can target it. */
    // CR 712.8e
    @Test
    fun aTransformedPermanentHasItsFrontFacesManaValue() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Rescind")
        bob.battlefield("Shape Beneath the Floe // Risen Leviathan")
        backFaceUp("Shape Beneath the Floe // Risen Leviathan")
        alice.library("Island")
        alice.casts("Rescind", x = 2, target = "Shape Beneath the Floe // Risen Leviathan")
        allPass()
        expect { bob.hand has "Shape Beneath the Floe // Risen Leviathan" }
    }

    /** Cast as its back face, Brennor is a mana value 5 spell on the Stack: "counter target spell with mana value 2"
     * can't target it. */
    // CR 712.8f
    @Test
    fun aBackFaceSpellHasItsOwnManaValue() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Skalda, God of Sagas // Brennor, Horn of Plenty")
        bob.battlefield("Island")
        bob.hand("Spell Noose")
        alice.casts("Skalda, God of Sagas // Brennor, Horn of Plenty", way = CastWay(face = 1))
        alice.passes()
        val noose = Engine.options(state, bob.id).filterIsInstance<Input.Cast>().firstOrNull { it.card == card("Spell Noose") }
        assertTrue(noose == null, "Spell Noose has no legal target")
        allPass()
        expect { card("Skalda, God of Sagas // Brennor, Horn of Plenty").view.name shouldBe "Brennor, Horn of Plenty" }
        assertEquals(Zone.Battlefield, state.objOrThrow(card("Skalda, God of Sagas // Brennor, Horn of Plenty")).zone)
    }
}
