package arcanum.engine.hidden

import arcanum.engine.Host
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import arcanum.engine.state.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which library places a player knows ([arcanum.engine.state.PlayerState.libraryPlaces]) and the host's new random order
 * of the rest ([Host.unknownOrder], [Host.reorderUnknown]): a reorder never moves a card whose place someone knows.
 * And what an outcome shows a player in passing ([Host.revealed]).
 */
class LibraryPlacesTest {
    // CR 401.2: a library's order is unknown; CR 701.22a: a scried card put on the bottom has a known place there
    @Test
    fun aScriedCardKeepsItsKnownPlace() = scenario {
        alice.battlefield("Island")
        alice.hand("Glance Ahead")
        alice.library("Forest", "Storm Spark", "Swamp", "Plains", "Mountain")
        val start = state.player(alice.id).library.toList()
        assertEquals(listOf(start), Host.unknownOrder(state, alice.id))

        alice.casts("Glance Ahead")
        allPass()
        alice.chooses("Forest")

        val library = state.player(alice.id).library
        val forest = library.last()
        assertEquals(mapOf(forest to 0), state.player(alice.id).libraryPlaces)
        assertEquals(listOf(library.dropLast(1)), Host.unknownOrder(state, alice.id))
        // A host's new order of the unknown part: the scried card stays at the bottom
        val reversed = library.dropLast(1).reversed()
        val reorder = Host.reorderUnknown(state, alice.id, listOf(reversed))!!
        assertEquals(reversed + forest, reorder.after)
        change(reorder)
        assertEquals(forest, state.player(alice.id).library.last())
        // Only a new order of the same cards is taken
        assertFailsWith<IllegalArgumentException> { Host.reorderUnknown(state, alice.id, listOf(listOf(forest))) }
    }

    // CR 401.4: a card put on top of its owner's library by a known action has a known place
    @Test
    fun aCardPutOnTopHasAKnownPlace() = scenario {
        alice.library("Forest", "Swamp")
        alice.graveyard("Storm Spark")
        // Through the engine (a resolution or rule): the place becomes known
        val recorder = arcanum.engine.change.Recorder(state.deepCopy())
        recorder.record(arcanum.engine.change.Changes.move(recorder.state, listOf(card("Storm Spark")), arcanum.api.Zone.Library))
        val top = recorder.state.player(alice.id).library.first()
        assertEquals(mapOf(top to 0), recorder.state.player(alice.id).libraryPlaces)
        assertTrue(recorder.changes.any { it is Change.LibraryPlaces })
    }

    // CR 701.24a: a shuffle randomizes the whole library: no place stays known
    @Test
    fun aShuffleForgetsEveryPlace() = scenario {
        alice.library("Forest", "Swamp", "Plains")
        val recorder = arcanum.engine.change.Recorder(state.deepCopy())
        val library = recorder.state.player(alice.id).library
        recorder.record(Change.Shown(alice.id, listOf(library.first())))
        assertEquals(mapOf(library.first() to 0), recorder.state.player(alice.id).libraryPlaces)
        val before = library.map { recorder.state.objOrThrow(it) }
        recorder.record(Change.Shuffle(alice.id, before, before.reversed().map { it.copy(id = it.id.next()) }))
        assertTrue(recorder.state.player(alice.id).libraryPlaces.isEmpty())
        assertEquals(1, Host.unknownOrder(recorder.state, alice.id).size)
    }

    // CR 702.75a (hideaway): the rest go to the bottom in a random order: one group, known as a group only
    @Test
    fun cardsPutInARandomOrderAreOneGroup() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Seer of Morrows")
        alice.library("Forest", "Storm Spark", "Swamp", "Plains", "Mountain", "Island")

        alice.casts("Seer of Morrows")
        allPass()
        allPass()
        answer(Answer.Objects(listOf(card("Storm Spark"))))

        val library = state.player(alice.id).library
        val places = state.player(alice.id).libraryPlaces
        val group = library.takeLast(3)
        val top = library.dropLast(3)
        assertEquals(group.toSet(), places.keys)
        assertEquals(1, places.values.toSet().size)
        assertTrue(places.values.first() > 0)
        // The unknown part on top (two cards), then the group: each reordered only among its own places
        val parts = Host.unknownOrder(state, alice.id)
        assertEquals(listOf(top, group), parts)
        val reorder = Host.reorderUnknown(state, alice.id, parts.map { it.reversed() })!!
        assertEquals(top.reversed() + group.reversed(), reorder.after)
    }

    // CR 401.5: a card a player may look at now isn't in an unknown part
    @Test
    fun nothingToReorderInALibraryOfOne() = scenario {
        alice.library("Forest")
        assertTrue(Host.unknownOrder(state, alice.id).isEmpty())
        assertNull(Host.reorderUnknown(state, alice.id, emptyList()))
    }

    // CR 402.3: a player looking at another player's hand sees its cards, though they stay hidden afterwards
    @Test
    fun aLookAtAHandIsRevealedInPassing() = scenario {
        alice.battlefield("Island")
        alice.hand("Cerebral Scan")
        alice.library("Forest")
        bob.hand("Storm Spark", "Denial")
        bob.library("Swamp")
        val before = state.deepCopy()
        alice.casts("Cerebral Scan", target = "bob")
        allPass()
        val changes = applied.toList()
        val seen = Host.revealed(before, changes, alice.id)
        // Bob's hand in passing, and Alice's draw: her own library's card (cards are compared by card, not object)
        assertEquals(setOf(card("Storm Spark"), card("Denial"), card("Forest")).map { (it as ObjectId.Card).card }.toSet(), seen)
        // Bob sees only Alice's spell, which was public once cast
        assertEquals(setOf((card("Cerebral Scan") as ObjectId.Card).card), Host.revealed(before, changes, bob.id))
        assertTrue(changes.any { it is Change.Shown && it.to == alice.id })
    }
}
