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

    @Test fun aSearchShowsWhatWaitsForTheShuffleUnderItsHeaderForBothPlayers() {
        val root = f.root
        val library = f.container(PlayZone.LIBRARY)
        val ids = root.board.containers.single { it.id == library }.cards
        val plain = ContentsDisplay(f.id(600), f.actor, library, ContentsSelection.ALL, ids.size, false, ids.drop(1), panelVersion = 3)
        assertTrue(searchQueueLines(plain).isEmpty())
        assertNull(searchQueueText(plain))
        val waiting = plain.copy(queuedTop = listOf(listOf(ids.first()), listOf(f.id(601), f.id(602))))
        assertEquals("After shuffle: 3 on top", searchQueueText(waiting))
        val both = waiting.copy(queuedBottom = listOf(listOf(f.id(603))))
        assertEquals(listOf("After shuffle:", "3 on top", "1 on bottom"), searchQueueLines(both))
        assertEquals("After shuffle: 3 on top · 1 on bottom", searchQueueText(both))
        assertEquals("After shuffle: 1 on bottom", searchQueueText(plain.copy(queuedBottom = both.queuedBottom)))
        for (viewer in listOf(f.actor, f.other)) {
            fun panel(display: ContentsDisplay) = cardPanelGeometry(root.copy(board = root.board.copy(contentsDisplays = listOf(display))),
                viewer, TableLayoutState(), TableBounds(0f, 0f, 412f, 700f)).single()
            val before = panel(plain); val after = panel(both)
            assertTrue(before.queueLines.isEmpty())
            assertEquals(searchQueueLines(both), after.queueLines)
            // The card grid starts below the count lines.
            assertTrue(after.viewport.bounds.y >= before.viewport.bounds.y + 3 * 11f)
            assertEquals(before.bounds, after.bounds)
        }
    }

    @Test fun emptyLocalPilesHaveNoPanelGeometry() {
        for (zone in listOf(PlayZone.GRAVEYARD, PlayZone.EXILE)) {
            assertNull(localPanelDisplay(f.root, f.actor, TableLayoutState(localPileId = f.container(zone))))
        }
    }

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

    @Test fun oneAndTwoPanelsFitBattlefieldWithoutCoveringOrShrinkingHands() {
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
            assertEquals(scene.world!!.viewport.y, left.bounds.y)
            assertEquals(scene.world!!.viewport.bottom, left.bounds.bottom)
            val hands = scene.items.filter { it.kind == TableItemKind.HAND }
            assertTrue(hands.all { hand -> scene.cardPanels.all { it.bounds.intersection(hand.bounds) == null } })
            val closed = TablePresentation.scene(f.game, play.copy(board = play.board.copy(contentsDisplays = emptyList())),
                f.actor, width, height, landscape)
            assertEquals(closed.items.filter { it.kind == TableItemKind.HAND }.map { it.bounds }, hands.map { it.bounds })
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
        for (zone in listOf(PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.LIBRARY)) {
            val container = f.container(zone)
            val ids = cards.map { it.id }
            val variant = play.copy(board = play.board.copy(cards = cards.map { it.copy(containerId = container) },
                containers = play.board.containers.map { it.copy(cards = if (it.id == container) ids else emptyList()) },
                contentsDisplays = if (zone == PlayZone.LIBRARY) listOf(ContentsDisplay("search", f.actor, container,
                    ContentsSelection.ALL, ids.size, false, ids)) else emptyList()))
            val panel = cardPanelGeometry(variant, f.actor, TableLayoutState(localPileId = container.takeIf { zone != PlayZone.LIBRARY }),
                TableBounds(0f, 0f, 412f, 700f)).single()
            assertEquals(listOf("Artifact (1)", "Creature (3)", "Land (1)"), panel.typeHeaders.map { it.title })
            assertEquals(listOf("Rock", "Beta", "Zulu", "Alpha"), panel.rows.map { it.entry.name })
            assertEquals(listOf(1, 2, 1, 1), panel.rows.map { it.copies })
            assertTrue(panel.typeHeaders.all { it.bounds.width == panel.bounds.width - 4 && it.bounds.height == 20f })
            assertEquals(panel.rows.first().bounds.x, panel.rows.last().bounds.x) // Singleton sections use the leftmost cell.
            assertEquals(panel.rows.first().bounds.x, panel.rows[1].bounds.x)
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

    @Test fun partialLookAndRevealRowsReorderAtTheLeftEdge() {
        val play = manyCards().let { initial -> initial.copy(board = initial.board.copy(
            contentsDisplays = initial.board.contentsDisplays.map { it.copy(cardIds = it.cardIds.take(4), requestedCount = 4) })) }
        for (reveal in listOf(false, true)) {
            val state = play.copy(board = play.board.copy(contentsDisplays = play.board.contentsDisplays.map { it.copy(reveal = reveal) }))
            val scene = TablePresentation.scene(f.game, state, f.actor, 412f, 892f, false)
            val panel = scene.cardPanels.single()
            val last = panel.rows.last()
            assertEquals(panel.rows.first().bounds.x, last.bounds.x)
            val scope = TableScope(panel.rows.first().entry.cardId, listOf(panel.rows.first().entry.cardId))
            assertEquals(last.entry.cardId, scene.panelReorder(last.bounds.center.copy(x = last.bounds.x), scope)!!.beforeId)
            assertNull(scene.panelReorder(last.bounds.center.copy(x = last.bounds.right + 8f), scope)!!.beforeId)
        }
    }

    @Test fun unifiedMenuGreysRevealedAndSameZoneCellsWithoutRemovingThem() {
        val play = manyCards()
        val display = play.board.contentsDisplays.single()
        val id = display.cardIds.first()
        assertEquals(18, panelCardActions.size)
        assertEquals(listOf("Inspect", "Other side", "Ping", "Hand", "Reveal\nhand", "Battlefield", "Tapped", "Stack", "Graveyard",
            "Exile", "Library top", "Reveal\ntop", "Library bottom", "Shuffle into library", "Temp 1", "Temp 2", "Temp 3", "Temp 4"),
            panelCardActions.map { it.label })
        // A Library look panel: only shuffling into its own Library is greyed.
        assertEquals(listOf("Shuffle into library"), panelCardActions.filterNot {
            panelCardActionEnabled(it, display, listOf(id), null, play.board) }.map { it.name })
        for (revealed in listOf(display.copy(reveal = true), display.copy(revealedIds = setOf(id)))) {
            assertEquals(listOf("Reveal+Hand", "Reveal+Top", "Shuffle into library"), panelCardActions.filterNot {
                panelCardActionEnabled(it, revealed, listOf(id), null, play.board) }.map { it.name })
        }
        // Some selected cards still unrevealed: the reveal variants stay available.
        assertTrue(panelCardActions.filter { it.reveal }.all { panelCardActionEnabled(it, display.copy(revealedIds = setOf(id)),
            display.cardIds.take(2), null, play.board) })
        for (zone in listOf(PlayZone.GRAVEYARD, PlayZone.EXILE)) {
            val disabled = panelCardActions.filterNot { panelCardActionEnabled(it, display, listOf(id), zone) }
            assertEquals(3, disabled.size)
            assertTrue(disabled.all { it.reveal || it.zone == zone })
        }
    }

    @Test fun panelTargetsAreTheTappedCardOrTheSelectedCardsOfThatPanelOnly() {
        val display = manyCards().board.contentsDisplays.single()
        val (a, b, c) = display.cardIds.take(3)
        assertEquals(listOf(a), panelCardTargets(display, a, emptySet()))
        assertEquals(listOf(a), panelCardTargets(display, a, setOf(b, c, "elsewhere")))
        assertEquals(listOf(a, c), panelCardTargets(display, c, setOf(c, a, "elsewhere")))
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
            val ui = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
            ui.openPublicPile(f.container(PlayZone.GRAVEYARD))
            val count = f.commands().size
            ui.localPanelAction(f.id(40), PlayZone.BATTLEFIELD, tapped = true)
            assertEquals(f.container(PlayZone.BATTLEFIELD), f.card(40).containerId)
            assertTrue(f.card(40).tapped)
            assertTrue(f.commands().drop(count).flatMap { it.playActions() }.none {
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
