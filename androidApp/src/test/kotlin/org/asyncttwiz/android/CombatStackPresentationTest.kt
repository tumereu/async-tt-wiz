package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CombatStackPresentationTest {

    // STK-003
    @Test fun stackAndCombatDrawingsIgnoreTappedSourcesWithoutChangingRealCards() {
        val f = TableStatesFixture
        val original = f.combat()
        val actual = CombatStackRules.stack(original.board).cards.first { id -> original.board.cards.any { it.id == id } }
        val frozen = original.board.cards.first().copy(tapped = true, token = true)
        val state = original.copy(board = original.board.copy(cards = original.board.cards.map {
            if (it.id == actual) it.copy(tapped = true) else it
        }, stackEffects = original.board.stackEffects + StackEffect(f.id("frozen"), frozen.id, frozen),
            containers = original.board.containers.map { if (it.zone == PlayZone.STACK)
                it.copy(cards = it.cards + f.id("frozen")) else it }))
        val drawings = CombatStackPresentation.stack(state.board, 28f, 40f).visible
        assertEquals(1, drawings.size)
        assertTrue(drawings.all { !it.card.tapped && it.bounds.width == 28f && it.bounds.height == 40f })
        val scene = TablePresentation.scene(f.game, tableBoard(state), f.a, 412f, 892f, false)
        assertTrue(scene.items.filter { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD }.all { !it.tapped })
        assertFalse(scene.items.single { it.cardId == f.id("attacker2") }.tapped)
        assertTrue(state.board.cards.single { it.id == f.source(f.a, "1/2").id }.tapped)
        assertTrue(state.board.cards.single { it.id == actual }.tapped) // Presentation does not mutate the source.
    }
    private fun populated(): PlayState {
        val boards = CombatBoards(combatId(8))
        val state = combatState()
        val declared = boards.declare(state, listOf(20, 21).map {
            CombatStackController.bind(state.board, combatId(it))
        }, false)
        return boards.place(declared, listOf(combatId(22), combatId(23)),
            CombatDestination(declared.board.combat!!.id, CombatRole.BLOCKER, declared.board.combat!!.pairs.first().id))
    }

    // CMB-008
    @Test fun packedPairWidthsUseUprightCardsAndOnlyInteriorDividers() {
        val state = populated().let { it.copy(board = it.board.copy(cards = it.board.cards.map { card ->
            if (card.id == combatId(22)) card.copy(tapped = true) else card
        })) }
        val geometry = CombatStackPresentation.combat(state.board, 48f, 68f)
        val card = geometry.cards.single { it.card.id == combatId(22) }
        assertEquals(48f, card.bounds.width)
        assertEquals(68f, card.bounds.height)
        assertFalse(card.card.tapped)
        assertTrue(state.board.cards.single { it.id == combatId(22) }.tapped)
        assertEquals(48f, geometry.cards.single { it.card.id == combatId(20) }.bounds.width)
        assertEquals(1, geometry.dividers.size)
        val widths = listOf(geometry.dividers.single(), geometry.width - geometry.dividers.single())
        assertTrue(widths[0] > widths[1])
        assertEquals(48f + 2 * CombatStackPresentation.slotMargin(48f), widths[1], .001f)
        val blockers = geometry.cards.filter { CombatStackRules.location(state.board.combat!!, it.viewId)!!.role == CombatRole.BLOCKER }
        val attackers = geometry.cards.filter { CombatStackRules.location(state.board.combat!!, it.viewId)!!.role == CombatRole.ATTACKER }
        assertTrue(blockers.maxOf { it.bounds.y + it.bounds.height } < attackers.minOf { it.bounds.y })
        // CMB-028: only the area's two end gaps (both rows) take drops outside the pairs; none at a divider
        assertEquals(4, geometry.destinations.count { it.destination.pairId == null })
        assertEquals(widths.sum(), geometry.width, .001f) // No reserved insertion slots.
        assertTrue(geometry.dividers.all { it > 0 && it < geometry.width })
        assertTrue(geometry.destinations.none { it.destination.pairId == null && !it.extendLeft && !it.extendRight })
        for (role in CombatRole.entries) {
            val regions = geometry.destinations.filter { it.destination.role == role }
            val y = regions.first().bounds.center.y
            fun target(x: Float) = regions.asReversed().firstOrNull { it.contains(TablePoint(x, y)) }?.destination
            assertEquals(IdentityGap(beforeId = state.board.combat!!.pairs.first().id), target(-1e20f)?.pairGap)
            assertEquals(IdentityGap(afterId = state.board.combat!!.pairs.last().id), target(1e20f)?.pairGap)
            assertNull(regions.firstOrNull { it.contains(TablePoint(-1e20f, -1f)) })
            assertEquals(state.board.combat!!.pairs.first().id, target(geometry.dividers.single() - 11f)?.pairId)
            assertEquals(state.board.combat!!.pairs.last().id, target(geometry.dividers.single() + 14f)?.pairId)
        }
    }

    @Test fun closedVersusEmptyAreaAndAccessibleDropChoices() {
        val state = combatState()
        assertTrue(CombatStackPresentation.combat(state.board, 48f, 68f).destinations.isEmpty())
        val opened = CombatBoards(combatId(8)).apply(state, PlayAction.Combat(CombatAction.Open))
        val scene = CombatStackPresentation.combat(opened.board, 48f, 68f)
        // One empty lane: at least one card slot wide, and wide enough for its full name.
        assertEquals(maxOf(48f + 2 * CombatStackPresentation.slotMargin(48f), CombatStackPresentation.laneWidth(scene.lanes.single())),
            scene.width, .001f)
        assertEquals(2, scene.destinations.size)
        assertTrue(scene.cards.isEmpty())
        assertTrue(scene.dividers.isEmpty())
        assertTrue(opened.board.combat!!.pairs.isEmpty())
        assertTrue(scene.destinations.all { it.extendLeft && it.extendRight && it.contains(TablePoint(-1e20f, it.bounds.center.y)) &&
            it.contains(TablePoint(1e20f, it.bounds.center.y)) })
        val populated = populated()
        val choices = CombatStackPresentation.combat(populated.board, 48f, 68f).destinations
        // CMB-028: no drop between two cards (no reordering or swapping)
        assertTrue(choices.none { it.destination.insertion == CombatInsertion.GAP })
        assertTrue(choices.all { it.destination.areaId == populated.board.combat!!.id && it.label.isNotBlank() })
    }

    // CMB-028, CMB-034: a drop on a pair's card is that pair, never a place between its cards
    @Test fun groupFacesAppendToTheirRow() {
        val state = populated()
        val geometry = CombatStackPresentation.combat(state.board, 48f, 68f)
        val pair = state.board.combat!!.pairs.first()
        fun target(point: TablePoint) = geometry.destinations.asReversed().first { it.contains(point) }.destination
        for (role in CombatRole.entries) {
            val occurrence = pair.side(role).first()
            val drawing = geometry.cards.single { it.viewId == occurrence.id }
            val destination = target(drawing.bounds.center)
            assertEquals(pair.id, destination.pairId)
            assertEquals(role, destination.role)
            assertEquals(CombatInsertion.APPEND, destination.insertion)
        }
    }

    // CMB-008
    @Test fun eachRowIsCenteredInItsSlotWithUnchangedCardGapsAndDividerPadding() {
        val state = populated()
        for (width in listOf(28f, 48f, 56f)) {
            val geometry = CombatStackPresentation.combat(state.board, width, width * 40 / 28)
            val pair = state.board.combat!!.pairs.first()
            val blockers = geometry.cards.filter { card -> pair.blockers.any { it.id == card.viewId } }.map { it.bounds }
            val attacker = geometry.cards.single { it.viewId == pair.attackers.single().id }.bounds
            // The slot ends at the divider (a lane wider than its pairs, for its chip with the target's value, CMB-013, keeps
            // them in its middle, so the slot needn't start at the lane's edge)
            val slotCenter = geometry.dividers.single() - CombatStackPresentation.slotWidth(pair.blockers.size, pair.attackers.size, width) / 2
            assertEquals(slotCenter, attacker.center.x, .001f)
            assertEquals(slotCenter, (blockers.first().x + blockers.last().right) / 2, .001f)
            assertEquals(width * .04f, blockers.last().x - blockers.first().right, .001f)
            assertEquals(CombatStackPresentation.slotMargin(width), geometry.dividers.single() - blockers.last().right, .001f)
            val next = geometry.cards.single { it.viewId == state.board.combat!!.pairs.last().attackers.single().id }.bounds
            assertEquals(CombatStackPresentation.slotMargin(width), next.x - geometry.dividers.single(), .001f)
        }
    }

    // CMB-008
    @Test fun slotsRecenterOnAdditionAndRemovalInBothOrientationsWithoutResettingCamera() {
        val f = TableStatesFixture
        val initial = f.combat()
        val area = initial.board.combat!!
        for (landscape in listOf(false, true)) for (pairs in listOf(area.pairs, area.pairs.take(1), emptyList())) {
            val state = initial.copy(board = initial.board.copy(combat = area.copy(pairs = pairs)))
            // Lanes fit their target's chip, so the geometry uses the scene's player names.
            // CMB-042: the area's cards are the battlefield's (28 dp at the default card size)
            val card = 28f
            val geometry = CombatStackPresentation.combat(state.board, card, card * 40f / 28f, state.definitions,
                listOf(f.game.inviter, f.game.recipient).associate { it.accountId to it.username }, f.a)
            val home = TablePresentation.scene(f.game, tableBoard(state), f.a, if (landscape) 892f else 412f,
                if (landscape) 412f else 892f, landscape)
            val rows = home.combatDrops.filter { it.destination.insertion == CombatInsertion.APPEND && it.destination.pairId != null }
            val center = if (rows.isEmpty()) home.combatDrops.first().landing.x else
                (rows.minOf { it.bounds.x } + rows.maxOf { it.bounds.right }) / 2
            assertEquals(home.world!!.viewport.center.x, center, .001f)
            if (rows.isNotEmpty()) assertEquals(geometry.width, rows.maxOf { it.bounds.right } - rows.minOf { it.bounds.x }, .001f)
            val camera = TableCamera(13f, 5f, 1.4f)
            val moved = TablePresentation.scene(f.game, tableBoard(state), f.a, if (landscape) 892f else 412f,
                if (landscape) 412f else 892f, landscape, TableLayoutState(camera = camera))
            assertEquals(camera, moved.camera)
            val movedRows = moved.combatDrops.filter { it.destination.insertion == CombatInsertion.APPEND && it.destination.pairId != null }
            val movedCenter = if (movedRows.isEmpty()) moved.combatDrops.first().landing.x else
                (movedRows.minOf { it.bounds.x } + movedRows.maxOf { it.bounds.right }) / 2
            assertEquals(center - camera.x * camera.zoom, movedCenter, .001f)
        }
    }

    // STK-008
    @Test fun stackTopThreeDrawingAllOrderedContentsAndCountAtCommonScale() {
        // 27 Stack entries pointing at the same card (STK-001), newest on top
        val state = combatState().let { s ->
            val entries = (0 until 27).map { StackEffect(combatId(500 + it), combatId(20)) }
            s.copy(board = s.board.copy(stackEffects = entries.reversed() + s.board.stackEffects,
                containers = s.board.containers.map { if (it.zone == PlayZone.STACK) it.copy(cards = entries.reversed().map { e -> e.id } + it.cards) else it }))
        }
        val scene = CombatStackPresentation.stack(state.board, 48f, 68f)
        assertEquals(27, scene.count)
        assertEquals(1, scene.visible.size)
        assertEquals(27, scene.orderedIds.distinct().size)
        assertEquals(scene.orderedIds.take(1), scene.visible.map { it.viewId })
        assertTrue(scene.visible.all { it.bounds.width == 48f && it.bounds.height == 68f && it.projection })
        assertTrue(scene.visible.zipWithNext().all { (a, b) -> a.bounds.x > b.bounds.x && a.bounds.y > b.bounds.y })
    }


}
