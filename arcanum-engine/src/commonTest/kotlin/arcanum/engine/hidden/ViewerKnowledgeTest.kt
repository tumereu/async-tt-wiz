package arcanum.engine.hidden

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.RandomKind
import arcanum.engine.input.Input
import arcanum.engine.review.filledIds
import arcanum.engine.review.hostFillsAsked
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.state.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The viewer-knowledge rule (D60, spec §7.0 "Hidden information"): the state is one viewer's view. Cards are filled in
 * as their identity matters or becomes visible to the viewer, and forgotten as they become hidden from the viewer. Each
 * scenario is played on both players' views where the two differ, and every host answer goes through JSON first (D58).
 */
class ViewerKnowledgeTest {
    /** Plays [block] on alice's view, then on bob's; every host answer is checked through JSON. */
    private fun onEachView(block: Scenario.(viewer: ScenarioPlayer) -> Unit) {
        listOf(0, 1).forEach { i ->
            scenario {
                jsonAtHostAnswers = true
                val viewer = players[i]
                viewer(viewer)
                block(viewer)
            }
        }
    }

    private fun Scenario.known(id: ObjectId) = !state.objOrThrow(id).unknown

    private fun Scenario.randomsAsked(): List<Decision.Random> =
        applied.filterIsInstance<Change.PendingSet>().mapNotNull { it.after?.decision as? Decision.Random }

    /** Shrine of Oblivion mills two of bob's cards: everyone sees them, so both views fill exactly those two, publicly;
     * bob's last library card is never asked for. */
    // CR 701.17a, 400.2, D60
    @Test
    fun milledCardsAreFilledInEveryView() = onEachView { _ ->
        Cards.invent("Knowledge Bear", "{1}{G}", "Creature — Bear", "", "2/2")
        alice.battlefield("Shrine of Oblivion", "Knowledge Bear")
        bob.library("Forest", "Storm Spark", "Island")

        alice.activates("Shrine of Oblivion", target = "bob")
        allPass()

        expect {
            bob.graveyard has "Forest"
            bob.graveyard has "Storm Spark"
        }
        val milled = hostFillsAsked().single()
        assertTrue(milled.public)
        assertEquals(2, milled.objects.size)
        assertFalse(known(card("Island", owner = bob)))
        assertTrue(hostAnswersThroughJson >= 1)
    }

    /** alice's draw in her draw step is filled for her alone in her view (she sees it); bob's view isn't asked. */
    // CR 504.1, 121.1, 402.3, D60
    @Test
    fun aDrawIsFilledOnlyInTheDrawersView() = onEachView { viewer ->
        turn(number = 2, step = TurnStep.Upkeep)
        alice.library("Swamp", "Island")
        alice.passes()
        bob.passes()

        val drawn = card("Swamp")
        assertEquals(Zone.Hand, state.objOrThrow(drawn).zone)
        assertEquals(viewer == alice, known(drawn))
        assertEquals(if (viewer == alice) 1 else 0, filledIds().size)
        hostFillsAsked().forEach { assertEquals(alice.id, it.player); assertFalse(it.public) }
        assertFalse(known(card("Island")))
    }

    /** Tidal Spellcharge returns bob's creature to his hand: in alice's view it becomes unknown and she may no longer
     * see it; in bob's view it stays known. */
    // CR 402.3, D60
    @Test
    fun aCardPutIntoAnotherPlayersHandIsForgotten() = onEachView { viewer ->
        alice.battlefield("Island", "Tidal Spellcharge")
        bob.battlefield("Elvish Seer")

        alice.activates("Tidal Spellcharge", target = "Elvish Seer")
        allPass()

        val seer = state.player(bob.id).hand.single()
        assertEquals(viewer == bob, known(seer))
        assertTrue(seer in Engine.visible(state, bob.id))
        assertFalse(seer in Engine.visible(state, alice.id))
        assertTrue(filledIds().isEmpty())
    }

    /** Sunbaked Mesa: alice searches her library for a Mountain and shuffles. The search is filled for her alone (not
     * public), and bob isn't offered answers naming her cards while it waits; the Mountain is public on the
     * battlefield; after the shuffle no view knows the library: the host gave new card ids, so not even they tell the
     * order. */
    // CR 701.23a, 701.24a, D38, D60
    @Test
    fun aSearchAndShuffleLeaveNoViewKnowingTheLibrary() = onEachView { _ ->
        alice.battlefield("Sunbaked Mesa")
        alice.library("Storm Spark", "Mountain", "Plains")
        val before = state.player(alice.id).library.map { (it as ObjectId.Card).card }.toSet()

        alice.activates("Sunbaked Mesa")
        allPass()
        val d = decision<Decision.ChooseObjects>()
        assertEquals(alice.id, d.player)
        assertTrue(Engine.options(state, bob.id).none { it is Input.Answer && (it.answer as Answer.Objects).chosen.isNotEmpty() })
        alice.chooses("Mountain")

        val search = hostFillsAsked().single { it.reason == "search" }
        assertEquals(alice.id, search.player)
        assertFalse(search.public)
        expect { alice.battlefield has "Mountain" }
        val library = state.player(alice.id).library
        assertEquals(2, library.size)
        assertTrue(library.none { known(it) })
        assertTrue(library.none { (it as ObjectId.Card).card in before }, "new card ids")
        assertTrue((randomsAsked().single().what as RandomKind.Objects).newIds)
    }

    /** With full information nothing is forgotten: the searched library is shuffled in an order the host gives, and
     * every card stays known (tests and a host's own state). */
    // CR 701.24a, D60
    @Test
    fun withFullInformationAShuffleKeepsTheCardsKnown() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Fiendish Mentor")
        alice.library("Forest", "Storm Spark", "Island")
        alice.casts("Fiendish Mentor")
        allPass()
        alice.chooses("Storm Spark")
        val library = state.player(alice.id).library
        assertEquals(2, library.size)
        assertTrue(library.none { state.objOrThrow(it).unknown })
        assertFalse((randomsAsked().single().what as RandomKind.Objects).newIds)
        assertTrue(hostFillsAsked().isEmpty())
    }

    /** Seer of Harvestide lets alice look at her top card any time: in her view it is filled before she gets priority,
     * and the land on top is offered; in bob's view it isn't, and only alice may see it. */
    // CR 401.5, 305.1, D60
    @Test
    fun aCardItsControllerMayLookAtIsFilledOnlyInTheirView() = onEachView { viewer ->
        alice.battlefield("Seer of Harvestide")
        alice.library("Forest", "Island")
        // Checked with the state-based actions, before alice gets priority
        settle()

        val top = card("Forest")
        assertEquals(viewer == alice, known(top))
        assertTrue(top in Engine.visible(state, alice.id))
        assertFalse(top in Engine.visible(state, bob.id))
        val plays = Engine.options(state, alice.id).filterIsInstance<Input.PlayLand>().map { it.card }
        assertEquals(viewer == alice, top in plays)
        assertFalse(known(card("Island")), "only the top card")
    }

    /** Cascade in alice's view: each card is asked for as it is turned over (publicly), and the ones put on the bottom
     * in a random order go in under new ids from the host, unknown again. The card below the hit is never asked for. */
    // CR 702.85a, 701.20d, D60
    @Test
    fun cascadePutsTheExiledCardsBackUnknown() = scenario {
        jsonAtHostAnswers = true
        viewer(alice)
        alice.battlefield("Forest", "Island", "Island")
        alice.hand("Splintered Envoy")
        alice.library("Forest", "Island", "Tinkered Mite", "Swamp")

        alice.casts("Splintered Envoy")
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))

        assertTrue(hostFillsAsked().all { it.public && it.objects.size == 1 }, "one card at a time")
        assertEquals(3, filledIds().size)
        assertTrue(card("Swamp") !in filledIds())
        val library = state.player(alice.id).library
        assertEquals(4, library.size)
        assertTrue(library.none { known(it) }, "the bottom cards are unknown again")
        assertTrue((randomsAsked().single().what as RandomKind.Objects).newIds)
    }

    /** bob discards to his hand size in the cleanup step, in alice's view: his hand is unknown to her, so she isn't
     * offered answers naming his cards; he is. The discarded card is filled publicly. */
    // CR 514.1, 400.2, D60
    @Test
    fun anotherPlayersDiscardChoiceIsntOfferedOverCardsTheyCantSee() = scenario {
        viewer(alice)
        turn(number = 2, active = bob, step = TurnStep.End)
        bob.hand("Forest", "Forest", "Forest", "Forest", "Island", "Island", "Island", "Storm Spark")
        allPass()
        val d = decision<Decision.ChooseObjects>()
        assertEquals(bob.id, d.player)
        assertTrue(Engine.options(state, alice.id).none { it is Input.Answer && (it.answer as Answer.Objects).chosen.isNotEmpty() })
        assertTrue(Engine.options(state, bob.id).any { it is Input.Answer })
        bob.answers(Answer.Objects(listOf(card("Storm Spark"))))
        assertTrue(known(card("Storm Spark", zone = Zone.Graveyard)))
        assertTrue(hostFillsAsked().single().public)
    }

    /** bob's face-down creature: a nameless 2/2 in both views; known to bob, who controls it (CR 708.5), unknown to
     * alice. When it dies it is revealed (CR 708.9): alice's view asks for it, bob's knows it already. */
    // CR 708.2a, 708.5, 708.9, D60
    @Test
    fun aFaceDownCreatureIsATwoTwoInBothViewsAndRevealedAsItDies() = onEachView { viewer ->
        alice.battlefield("Mountain", "Cinder Spellcharge")
        bob.battlefield("Barkwood Pathfinders", faceDown = true)
        val morph = card("Barkwood Pathfinders")
        assertEquals(viewer == bob, known(morph))
        expect {
            "Barkwood Pathfinders".power shouldBe 2
            "Barkwood Pathfinders".toughness shouldBe 2
        }
        assertEquals("", view()[morph]?.characteristics?.name.orEmpty())

        alice.activates("Cinder Spellcharge", target = "Barkwood Pathfinders")
        allPass()

        expect { bob.graveyard has "Barkwood Pathfinders" }
        assertTrue(known(card("Barkwood Pathfinders", zone = Zone.Graveyard)))
        assertEquals(if (viewer == alice) listOf(morph) else emptyList(), filledIds())
    }

    /** An invented artifact "{T}: Turn target creature face up/down." (no cube card turns creatures face up or down
     * by an effect, and the parser has no wording for it yet): parsed from a +1/+1 ability, its step replaced. */
    private fun faceTurner(name: String, effect: (arcanum.api.Objects) -> arcanum.api.Effect): String {
        val base = Cards.invent("$name Base", "{1}", "Artifact", "{T}: Target creature gets +1/+1 until end of turn.")
        val d = Cards.definition(base)
        val face = d.faces.single()
        val ability = face.abilities.single() as arcanum.api.Ability.Activated
        val slot = ability.instructions.targets.single().name
        val turning = ability.copy(instructions = ability.instructions.copy(
            steps = listOf(arcanum.api.Step.Do(effect(arcanum.api.Objects.Target(slot))))))
        return Cards.define(name, d.copy(faces = listOf(face.copy(name = name, abilities = listOf(turning)))))
    }

    /** bob's face-down creature turned face up: everyone sees it (CR 708.8), so alice's view asks for it publicly;
     * bob's view knows it already. */
    // CR 708.8, D60
    @Test
    fun aCardTurnedFaceUpIsFilledInEveryView() = onEachView { viewer ->
        val lifter = faceTurner("Mask Lifter") { arcanum.api.Effect.TurnFaceUp(it) }
        alice.battlefield(lifter)
        bob.battlefield("Barkwood Pathfinders", faceDown = true)
        val morph = card("Barkwood Pathfinders")

        alice.activates(lifter, target = "Barkwood Pathfinders")
        allPass()

        assertTrue(known(morph))
        assertFalse(state.objOrThrow(morph).faceDown)
        assertEquals(if (viewer == alice) listOf(morph) else emptyList(), filledIds())
        hostFillsAsked().forEach { assertTrue(it.public) }
        expect { "Barkwood Pathfinders".power shouldBe 1 }
    }

    /** A creature turned face down is forgotten in the view of a player who can't look at it (CR 708.5), and stays
     * known to its controller; it is a 2/2 in both. */
    // CR 708.2a, 708.5, D60
    @Test
    fun aCardTurnedFaceDownIsForgottenByWhoCantLook() = onEachView { viewer ->
        val veil = faceTurner("Mask Veil") { arcanum.api.Effect.TurnFaceDown(it) }
        alice.battlefield(veil)
        bob.battlefield("Elvish Seer")
        val seer = card("Elvish Seer")

        alice.activates(veil, target = "Elvish Seer")
        allPass()

        assertTrue(state.objOrThrow(seer).faceDown)
        assertEquals(viewer == bob, known(seer))
        assertEquals(viewer == alice, applied.any { it is Change.Forgotten })
        assertEquals(2, view()[seer]?.characteristics?.power)
    }

    /** A card put into a library known stays known until the library is shuffled: Gossamer Coterie's pick goes to the
     * bottom of bob's library, and alice, who looked at it, still knows it there. */
    // CR 701.20c, D60
    @Test
    fun aCardPutIntoALibraryKnownStaysKnownUntilShuffled() = scenario {
        viewer(alice)
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Gossamer Coterie")
        bob.hand("Storm Spark", "Forest", "Denial")
        bob.library("Island", "Swamp")
        alice.casts("Gossamer Coterie")
        allPass()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses("Storm Spark")
        allPass()
        val bottom = state.player(bob.id).library.last()
        assertEquals("Storm Spark", state.objOrThrow(bottom).definition?.faces?.first()?.name)
        assertTrue(state.player(bob.id).library.dropLast(1).none { known(it) })
    }
}
