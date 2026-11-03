package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CardPanelLayoutTest {
    private val f = BasicTableFixture()



    @Test fun emptyLocalPilesHaveNoPanelGeometry() {
        for (zone in listOf(PlayZone.GRAVEYARD, PlayZone.EXILE)) {
            assertNull(localPanelDisplay(f.root, f.actor, TableLayoutState(localPileId = f.container(zone))))
        }
    }

    // ZON-049
    @Test fun maximumColumnsAndLeftAlignedPartialRowsAreIndependentOfCardCount() {
        assertEquals(3, panelColumnCount(3))
        assertEquals(2, panelColumnCount(2))
        for (columns in 2..3) for (count in 1..12) {
            val positions = (0 until count).map { panelColumn(it, count, columns) }
            positions.chunked(columns).forEach { assertEquals((0 until it.size).toList(), it) }
        }
    }

    private fun manyCards(): PlayState {
        val library = f.container(PlayZone.LIBRARY)
        val grave = f.container(PlayZone.GRAVEYARD, f.other)
        val cards = (1000..1119).map { f.root.board.cards.first().copy(id = f.id(it), containerId = library) }
        val dead = f.root.board.cards.last().copy(containerId = grave, owner = f.other)
        val display = ContentsDisplay(f.id(600), f.actor, library, ContentsSelection.TOP, cards.size, false, cards.map { it.id })
        return f.root.copy(board = f.root.board.copy(cards = cards + dead,
            containers = f.root.board.containers.map { it.copy(cards = when (it.id) {
                library -> cards.map { c -> c.id }; grave -> listOf(dead.id); else -> emptyList()
            }) }, contentsDisplays = listOf(display)))
    }


    // ZON-037, ZON-051, SYS-051
    @Test fun panelTypesFollowTheSharedOrderNamesAlphabeticCopiesGroupedAndHiddenCardsStayPrivate() {
        val specs = listOf("Zulu" to "Creature", "Alpha" to "Land", "Beta" to "Creature", "Beta" to "Creature", "Rock" to "Artifact")
        val definitions = specs.mapIndexed { n, (name, type) -> SubmittedDefinition("d$n",
            CardDefinition(name, listOf(DefinitionFace(f.id(700 + n), name, typeLine = type)))) }
        val grave = f.container(PlayZone.GRAVEYARD)
        val cards = definitions.mapIndexed { n, d -> f.root.board.cards.first().copy(id = f.id(800 + n), definition = d.digest,
            faceId = d.definition.faces.single().faceId, containerId = grave) }
        val play = f.root.copy(definitions = definitions, board = f.root.board.copy(cards = cards,
            containers = f.root.board.containers.map { it.copy(cards = if (it.id == grave) cards.map { c -> c.id } else emptyList()) }))
        for (zone in listOf(PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.LIBRARY)) {
            val container = f.container(zone)
            val ids = cards.map { it.id }
            val variant = play.copy(board = play.board.copy(cards = cards.map { it.copy(containerId = container) },
                containers = play.board.containers.map { it.copy(cards = if (it.id == container) ids else emptyList()) },
                contentsDisplays = if (zone == PlayZone.LIBRARY) listOf(ContentsDisplay("search", f.actor, container,
                    ContentsSelection.ALL, ids.size, false, ids)) else emptyList()))
            val panel = cardPanelGeometry(variant, f.actor, TableLayoutState(localPileId = container.takeIf { zone != PlayZone.LIBRARY }),
                TableBounds(0f, 0f, 412f, 700f)).single()
            assertEquals(listOf("Creature (3)", "Artifact (1)", "Land (1)"), panel.typeHeaders.map { it.title })
            assertEquals(listOf("Beta", "Zulu", "Rock", "Alpha"), panel.rows.map { it.entry.name })
            assertEquals(listOf(2, 1, 1, 1), panel.rows.map { it.copies })
            assertTrue(panel.typeHeaders.all { it.bounds.width == panel.bounds.width - 4 && it.bounds.height == 20f })
            assertEquals(panel.rows.first().bounds.x, panel.rows.last().bounds.x) // Singleton sections use the leftmost cell.
            assertEquals(panel.rows.first().bounds.x, panel.rows[2].bounds.x) // Artifact's single card.
            if (zone != PlayZone.LIBRARY) {
                // Rules v27 arrival order (newest first) never leaks into the inspect panel's sorting.
                for (order in listOf(ids.reversed(), ids.drop(2) + ids.take(2))) {
                    val reordered = variant.copy(board = variant.board.copy(containers = variant.board.containers.map {
                        if (it.id == container) it.copy(cards = order) else it }))
                    val other = cardPanelGeometry(reordered, f.actor, TableLayoutState(localPileId = container), TableBounds(0f, 0f, 412f, 700f)).single()
                    assertEquals(panel.typeHeaders, other.typeHeaders)
                    assertEquals(panel.rows, other.rows)
                }
            }
        }
        val backs = listOf(ContentsEntry("a", "Card back", "Cards", "", faceVisible = false),
            ContentsEntry("b", "Card back", "Cards", "", faceVisible = false))
        assertEquals(2, panelGroups(backs).size)
    }




    // ZON-036
    @Test fun localPileTogglesReplacesSurvivesMenusAndUpdatesWithoutCommands() {
        var play = manyCards()
        val ui = TableToolkitController(f.actor, { play }, { true }, { _, _ -> error("Local view must not send") })
        val grave = f.container(PlayZone.GRAVEYARD, f.other)
        val exile = f.container(PlayZone.EXILE)
        ui.openPublicPile(grave)
        assertNull(ui.panel)
        ui.tableMenu(); ui.dismiss()
        assertEquals(grave, ui.layoutState.value.localPileId)
        assertEquals(1, ui.publicContents()!!.cardIds.size)
        play = play.copy(board = play.board.copy(containers = play.board.containers.map { if (it.id == grave) it.copy(cards = emptyList()) else it }))
        assertTrue(ui.publicContents()!!.cardIds.isEmpty())
        ui.openPublicPile(exile)
        assertEquals(exile, ui.layoutState.value.localPileId)
        ui.openPublicPile(exile)
        assertNull(ui.layoutState.value.localPileId)
        ui.openPublicPile(grave); ui.closeLocalPile()
        assertNull(ui.layoutState.value.localPileId)
    }


}
