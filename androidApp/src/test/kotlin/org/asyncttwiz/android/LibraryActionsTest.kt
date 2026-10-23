package org.asyncttwiz.android

import kotlin.test.*
import org.junit.Test
import org.asyncttwiz.protocol.*

class LibraryActionsTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun state(): PlayState = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))),
            listOf(PlayCard(id(20), "z", id(7), id(10), id(40)), PlayCard(id(21), "a", id(7), id(10), id(41)),
                PlayCard(id(22), "a", id(7), id(11), id(41))),
            listOf(PlayContainer(id(10), PlayZone.LIBRARY, id(7), listOf(id(21), id(20))),
                PlayContainer(id(11), PlayZone.HAND, id(7), listOf(id(22))),
                PlayContainer(id(12), PlayZone.GRAVEYARD, id(8)), PlayContainer(id(13), PlayZone.EXILE, id(7)),
                PlayContainer(id(14), PlayZone.BATTLEFIELD, id(7)), PlayContainer(id(15), PlayZone.STACK)), TurnState(id(7))),
        listOf(SubmittedDefinition("z", CardDefinition("Zulu", listOf(DefinitionFace(id(40), "Zulu", typeLine = "Creature")))),
            SubmittedDefinition("a", CardDefinition("Alpha", listOf(DefinitionFace(id(41), "Alpha", typeLine = "Land"))))),
        emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    private fun display(selection: ContentsSelection = ContentsSelection.ALL, reveal: Boolean = false) =
        ContentsDisplay(id(30), id(8), id(10), selection, 3, reveal, listOf(id(20), id(21), id(22)))

    @Test fun fullContentsAreCategoryAlphabeticalIndependentOfLibraryPositionsAndSearchIsPersonal() {
        val current = state()
        val first = contentsEntries(current, display(), id(8))
        val reversed = contentsEntries(current, display().copy(cardIds = display().cardIds.reversed()), id(8))
        assertEquals(first, reversed)
        assertEquals(listOf("Creature", "Land", "Land"), first.map { it.category })
        assertFalse(first.any { it.bottommost })
        assertEquals(listOf(id(21), id(22)), contentsEntries(current, display(), id(8), "alph").map { it.cardId })
        assertEquals(listOf(id(21), id(20)), current.board.containers.first().cards)
    }

    @Test fun coveredViewCannotLeakNamesTypesSearchOrPositionThroughPresentation() {
        val current = state()
        val entries = contentsEntries(current, display(), id(7), "Zulu")
        assertEquals(3, entries.size)
        assertEquals(setOf("Card back"), entries.map { it.name }.toSet())
        assertEquals(setOf("Cards"), entries.map { it.category }.toSet())
        assertFalse(entries.any { it.bottommost })
        assertEquals(3, contentsEntries(current, display(reveal = true), id(7)).size)
        assertTrue(contentsEntries(current, display(reveal = true), id(7)).any { it.name == "Zulu" })
    }

    @Test fun sliceRetainsAcceptedOrderBottomMarkerAndUpdatedLocationWithoutRefill() {
        val current = state()
        val entries = contentsEntries(current, display(ContentsSelection.BOTTOM, reveal = true), id(7))
        assertEquals(listOf(20, 21, 22).map(::id), entries.map { it.cardId })
        assertTrue(entries.last().bottommost)
        assertEquals("hand", entries.last().location)
        assertEquals(listOf(false, false, true), entries.map { it.bottommost })
    }

    @Test fun destinationInventoryHasLibraryOrderAndExplicitBattlefieldStackWithoutSideboard() {
        val choices = libraryDestinations(state().board).map { it.second }
        assertEquals(listOf(MoveInsertion.TOP, MoveInsertion.BOTTOM, MoveInsertion.UNORDERED,
            MoveInsertion.UNORDERED, MoveInsertion.UNORDERED, MoveInsertion.EMPTY, MoveInsertion.TOP), choices.map { it.insertion })
        assertEquals(setOf(id(10), id(11), id(12), id(13), id(14), id(15)), choices.map { it.containerId }.toSet())
        assertEquals(BoardPosition(0, 0), choices.single { it.containerId == id(14) }.position)
    }
}
