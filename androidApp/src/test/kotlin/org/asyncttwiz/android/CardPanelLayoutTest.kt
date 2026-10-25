package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CardPanelLayoutTest {
    private val f = BasicTableFixture()

    @Test fun eyeBadgesBelongOnlyToTheAuthorOfLookAndSearchAndSplitMixedVisibilityCopies() {
        val root = f.root
        val library = f.container(PlayZone.LIBRARY)
        val ids = root.board.containers.single { it.id == library }.cards
        for (search in listOf(false, true)) for (reveal in listOf(false, true)) for (viewer in listOf(f.actor, f.other)) {
            val display = ContentsDisplay(f.id(600), f.actor, library,
                if (search) ContentsSelection.ALL else ContentsSelection.TOP, ids.size, reveal, ids,
                revealedIds = setOf(ids.last()), panelVersion = 3)
            val play = root.copy(board = root.board.copy(contentsDisplays = listOf(display)))
            val scene = TablePresentation.scene(f.game, play, viewer, 412f, 892f, false)
            val badges = scene.items.filter { it.panelRevealed }
            if (!reveal && viewer == f.actor) {
                assertEquals(listOf(ids.last()), badges.map { it.cardId })
                assertTrue(badges.all { it.viewport.startsWith("cardPanel/") && !it.covered })
                if (search) assertEquals(2, scene.cardPanels.single().rows.size)
            } else assertTrue(badges.isEmpty())
        }
    }

    @Test fun emptyLocalPilesHaveNoPanelGeometry() {
        for (zone in listOf(PlayZone.GRAVEYARD, PlayZone.EXILE)) {
            assertNull(localPanelDisplay(f.root, f.actor, TableLayoutState(localPileId = f.container(zone))))
        }
    }

    @Test fun fewestColumnsThatFitAreChosenAndOverflowIsCapped() {
        assertEquals(1, panelColumnCount(listOf(4), 236f, 3))
        assertEquals(2, panelColumnCount(listOf(5), 236f, 3))
        assertEquals(3, panelColumnCount(listOf(9), 236f, 3))
        assertEquals(3, panelColumnCount(listOf(1000), 236f, 3))
        assertEquals(2, panelColumnCount(listOf(9), 236f, 2))
        assertEquals(2, panelColumnCount(listOf(2, 2), 172f, 2, typeHeaders = true))
        assertEquals(1, panelColumnCount(listOf(0), 0f, 3))
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

    @Test fun oneAndTwoPanelsFitPortraitAndLandscapeAndReserveBothPortraitHands() {
        val play = manyCards()
        for (landscape in listOf(false, true)) for (two in listOf(false, true)) {
            val width = if (landscape) 892f else 412f
            val height = if (landscape) 412f else 892f
            val ui = TableLayoutState(localPileId = if (two) f.container(PlayZone.GRAVEYARD, f.other) else null)
            val scene = TablePresentation.scene(f.game, play, f.actor, width, height, landscape, ui)
            assertEquals(if (two) 2 else 1, scene.cardPanels.size)
            val left = scene.cardPanels.first()
            assertEquals(if (two) 2 else 3, left.columns)
            assertTrue(left.viewport.maxScrollY > 0)
            assertEquals(if (landscape) TablePresentation.sidebarWidth(width) else 0f, left.bounds.x)
            for (panel in scene.cardPanels) {
                assertTrue(panel.bounds.x >= 0 && panel.bounds.y >= 0)
                assertTrue(panel.bounds.right <= width && panel.bounds.bottom <= height)
                assertTrue(panel.rows.all { it.bounds.width == 28f && it.bounds.height == 40f })
            }
            if (two) {
                val right = scene.cardPanels.last()
                assertTrue(left.bounds.right < right.bounds.x)
                assertEquals(PlayZone.GRAVEYARD, right.localZone)
                assertEquals(left.bounds.y, right.bounds.y)
                assertEquals(left.bounds.bottom, right.bounds.bottom)
            }
            if (landscape) {
                assertEquals(scene.world!!.viewport.y, left.bounds.y)
                assertEquals(scene.world!!.viewport.bottom, left.bounds.bottom)
            } else {
                val hands = scene.items.filter { it.kind == TableItemKind.HAND }
                assertEquals(hands.minOf { it.bounds.y }, left.bounds.y)
                assertEquals(hands.maxOf { it.bounds.bottom }, left.bounds.bottom)
                assertTrue(hands.all { it.bounds.x > scene.cardPanels.last().bounds.right })
                assertTrue(scene.viewports.filter { it.hand }.all { it.bounds.x > scene.cardPanels.last().bounds.right })
            }
            val rows = left.rows
            assertTrue(rows[0].bounds.x < rows[1].bounds.x)
            assertEquals(rows[0].bounds.y, rows[1].bounds.y)
            assertTrue(rows[left.columns].bounds.y > rows[0].bounds.y)
            assertEquals(play.board.contentsDisplays.single().cardIds.first(), rows.first().entry.cardId)
            val reorder = scene.panelReorder(rows[1].bounds.center.copy(x = rows[1].bounds.x), TableScope(rows[0].entry.cardId, listOf(rows[0].entry.cardId)))
            assertEquals(rows[1].entry.cardId, reorder!!.beforeId)
        }
    }

    @Test fun graveyardTypesAndNamesAreAlphabeticCopiesGroupedAndHiddenCardsStayPrivate() {
        val specs = listOf("Zulu" to "Creature", "Alpha" to "Land", "Beta" to "Creature", "Beta" to "Creature", "Rock" to "Artifact")
        val definitions = specs.mapIndexed { n, (name, type) -> SubmittedDefinition("d$n",
            CardDefinition(name, listOf(DefinitionFace(f.id(700 + n), name, typeLine = type)))) }
        val grave = f.container(PlayZone.GRAVEYARD)
        val cards = definitions.mapIndexed { n, d -> f.root.board.cards.first().copy(id = f.id(800 + n), definition = d.digest,
            faceId = d.definition.faces.single().faceId, containerId = grave) }
        val play = f.root.copy(definitions = definitions, board = f.root.board.copy(cards = cards,
            containers = f.root.board.containers.map { it.copy(cards = if (it.id == grave) cards.map { c -> c.id } else emptyList()) }))
        val panel = cardPanelGeometry(play, f.actor, TableLayoutState(localPileId = grave), TableBounds(0f, 0f, 412f, 700f)).single()
        assertEquals(listOf("Artifact", "Creature", "Land"), panel.typeHeaders.map { it.title })
        assertEquals(listOf("Rock", "Beta", "Zulu", "Alpha"), panel.rows.map { it.entry.name })
        assertEquals(listOf(1, 2, 1, 1), panel.rows.map { it.copies })
        val backs = listOf(ContentsEntry("a", "Card back", "Cards", "", faceVisible = false),
            ContentsEntry("b", "Card back", "Cards", "", faceVisible = false))
        assertEquals(2, panelGroups(backs).size)
    }

    @Test fun fixedMenuGreysRevealedAndSameZoneCellsWithoutRemovingThem() {
        val display = manyCards().board.contentsDisplays.single()
        val id = display.cardIds.first()
        assertEquals(9, panelCardActions.size)
        assertEquals(listOf("Hand", "Reveal+Hand", "Battlefield", "Battlefield tapped", "Graveyard", "Exile", "Top", "Reveal+Top", "Bottom"), panelCardActions.map { it.label })
        assertTrue(panelCardActions.all { panelCardActionEnabled(it, display, id, null) })
        for (revealed in listOf(display.copy(reveal = true), display.copy(revealedIds = setOf(id)))) {
            assertEquals(listOf("Reveal+Hand", "Reveal+Top"), panelCardActions.filterNot { panelCardActionEnabled(it, revealed, id, null) }.map { it.label })
        }
        for (zone in listOf(PlayZone.GRAVEYARD, PlayZone.EXILE)) {
            val disabled = panelCardActions.filterNot { panelCardActionEnabled(it, display, id, zone) }
            assertEquals(3, disabled.size)
            assertTrue(disabled.all { it.reveal || it.zone == zone })
        }
    }

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

    @Test fun localMovesUseNormalOwnerDestinationsAndHistoryNotPanelActions() {
        f.connect()
        try {
            assertTrue(f.move(40, TableDestination.Container(f.container(PlayZone.GRAVEYARD), PlayZone.GRAVEYARD)))
            val ui = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay)
            ui.openPublicPile(f.container(PlayZone.GRAVEYARD))
            val count = f.commands().size
            ui.localPanelAction(f.id(40), PlayZone.BATTLEFIELD, tapped = true)
            assertEquals(f.container(PlayZone.BATTLEFIELD), f.card(40).containerId)
            assertTrue(f.card(40).tapped)
            assertTrue(f.commands().drop(count).map { (it.action as GameAction.Play).action }.none {
                it is PlayAction.ShowContents || it is PlayAction.RevealContentsCard || it is PlayAction.DismissContents })
            assertEquals(f.container(PlayZone.GRAVEYARD), ui.layoutState.value.localPileId)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun opponentsOpenPanelAlsoBlocksClientShowWithoutSending() {
        val initial = manyCards()
        val play = initial.copy(board = initial.board.copy(contentsDisplays = initial.board.contentsDisplays.map { it.copy(author = f.other) }))
        val library = LibraryController(play.id, f.container(PlayZone.LIBRARY), f.container(PlayZone.HAND),
            { play }, { true }, { error("An open opponent panel blocks all new panels") })
        assertEquals(LibrarySubmission(0, 1), library.show(ContentsSelection.TOP))
        assertEquals(LibrarySubmission(0, 1), library.show(ContentsSelection.TOP, reveal = true))
        assertEquals(LibrarySubmission(0, 1), library.show(ContentsSelection.ALL))
    }
}
