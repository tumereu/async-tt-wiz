package org.asyncttwiz.android

import kotlin.test.*
import org.junit.Test
import org.asyncttwiz.protocol.*

class LibraryActionsTest {
    @Test fun publicContentsUseTypeColumnsAndLocalSearchWithoutCoveredMetadata() {
        val play = TableStatesFixture.initial
        val container = play.board.containers.first { it.zone == PlayZone.GRAVEYARD }
        val pile = PileContents(container.id, "Graveyard", container.cards)
        val entries = contentsEntries(play, pile, TableStatesFixture.a)
        assertEquals(listOf("Creature", "Land"), entries.map { it.category })
        assertEquals(entries, contentsEntries(play, pile.copy(cardIds = pile.cardIds.reversed()), TableStatesFixture.a))
        assertEquals(listOf("Meadow Sentinel"), contentsEntries(play, pile, TableStatesFixture.a, "sent").map { it.name })
        val hiddenId = entries.first().cardId
        val covered = play.copy(board = play.board.copy(cards = play.board.cards.map {
            if (it.id == hiddenId) it.copy(covered = true, explicitlyCovered = true) else it
        }))
        val hidden = contentsEntries(covered, pile, TableStatesFixture.b, "Forest").single { it.cardId == hiddenId }
        assertEquals("Card back", hidden.name); assertEquals("Cards", hidden.category); assertFalse(hidden.faceVisible)
        assertFalse(hidden.bottommost)
        // No source mutation or logged reference display is created by filtering.
        assertEquals(container.cards, play.board.containers.single { it.id == container.id }.cards)
        assertTrue(play.board.contentsDisplays.isEmpty())
    }

    @Test fun stackContentsRetainOrderedDistinctLiveAndFrozenEffectsAndSearch() {
        val play = TableStatesFixture.combat()
        val order = CombatStackRules.stack(play.board).cards
        val effect = play.board.stackEffects.single()
        val card = CombatStackRules.inspection(play.board, effect.id)!!
        val frozen = play.copy(board = play.board.copy(cards = play.board.cards.filterNot { it.id == card.id },
            stackEffects = listOf(effect.copy(frozen = card), effect.copy(id = "second-effect", frozen = card))))
        val pile = PileContents("stack", "Stack", order + "second-effect", ordered = true)
        val entries = contentsEntries(frozen, pile, TableStatesFixture.a)
        assertEquals(pile.cardIds, entries.map { it.cardId })
        assertTrue(entries.first().location.startsWith("1 · "))
        assertTrue(entries.last().location.endsWith("Retained effect"))
        assertEquals(listOf(effect.id, "second-effect"), contentsEntries(frozen, pile, TableStatesFixture.a, "River").map { it.cardId })
    }

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

    @Test fun destinationsUseUsernamesAndNeverAccountIdentifiers() {
        val names = mapOf(id(7) to "alex", id(8) to "riley")
        val labels = libraryDestinations(state().board, names).map { it.first }
        assertTrue(labels.any { it == "alex library top" })
        assertTrue(labels.any { it == "riley graveyard" })
        for (label in labels + libraryDestinations(state().board).map { it.first }) {
            assertFalse(label.contains(id(7)))
            assertFalse(label.contains(id(8)))
        }
    }
}
