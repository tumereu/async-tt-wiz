package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TableToolkitGeometryTest {
    private fun state(f: BasicTableFixture): PlayState {
        val source = f.root.board.cards.first().copy(containerId = f.container(PlayZone.BATTLEFIELD),
            placement = PlayPlacement(BoardPosition(0, 0), f.id(300)), tapped = true, covered = false)
        val effects = (400..426).map { StackEffect(f.id(it), source.id) }
        val occurrence = CombatOccurrence(f.id(301), source.id)
        return f.root.copy(board = f.root.board.copy(cards = listOf(source), containers = f.root.board.containers.map {
            it.copy(cards = when (it.id) {
                source.containerId -> listOf(source.id)
                f.container(PlayZone.STACK) -> effects.map { effect -> effect.id }
                else -> emptyList()
            })
        }, combat = CombatArea(f.id(302), listOf(CombatPair(f.id(303), listOf(occurrence)))), stackEffects = effects))
    }

    @Test fun actualCanvasSceneSharesDrawHitSemanticsAndCommonScaleForCombatAndMixedStack() {
        val f = BasicTableFixture(); val play = state(f)
        for (landscape in listOf(false, true)) {
            val scene = TablePresentation.scene(f.game, play, f.actor, if (landscape) 1200f else 480f,
                if (landscape) 700f else 1000f, landscape)
            val real = scene.items.single { it.cardId == f.id(40) && !it.projection }
            val combat = scene.items.single { it.cardId == f.id(301) }
            assertEquals(40f, real.bounds.width); assertEquals(28f, real.bounds.height)
            assertEquals(real.bounds.width, combat.bounds.width); assertEquals(real.bounds.height, combat.bounds.height)
            assertEquals(TablePresentation.GRID, scene.footprint)
            assertTrue(combat.projection); assertEquals(real.sourceId, combat.sourceId)
            assertEquals(combat, scene.hit(combat.visible.first().center))
            assertEquals(setOf(combat.cardId), scene.rectangle(combat.visible.first()))
            assertTrue(scene.combatDrops.any { it.destination.role == CombatRole.BLOCKER && it.destination.pairId == null })
            assertTrue(scene.combatDrops.any { it.destination.role == CombatRole.ATTACKER && it.destination.pairId == f.id(303) })
            val stack = scene.items.single { it.id == f.container(PlayZone.STACK) }
            assertEquals("27", stack.value); assertEquals(27, stack.cards.size)
            assertEquals(3, scene.items.count { it.id.startsWith("stackDrawing/") })
            val top = scene.items.last { it.id.startsWith("stackDrawing/") }
            assertEquals(stack.id, scene.hit(top.visible.first().center)!!.id)
            assertEquals(real.sourceId, top.sourceId)
            assertTrue(scene.combatDividers.isNotEmpty())
        }
    }

    @Test fun geometryDropCaptureKeepsExactAreaPairAndRoleWhenIncomingSceneReflows() {
        val f = BasicTableFixture(); val play = state(f)
        val scene = TablePresentation.scene(f.game, play, f.actor, 480f, 1000f, false)
        val source = scene.items.single { it.cardId == f.id(40) && !it.projection }
        val region = scene.combatDrops.first { it.destination.role == CombatRole.BLOCKER && it.destination.pairId == null }
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator({ events += it })
        gestures.update(scene, TableSelection(setOf(source.cardId!!)), true)
        gestures.down(source.visible.first().center, 0)
        gestures.move(region.bounds.center, 20)
        gestures.update(scene.copy(combatDrops = scene.combatDrops.map { it.copy(destination = it.destination.copy(areaId = f.id(999))) }),
            TableSelection(), true)
        gestures.up(region.bounds.center, 30)
        assertEquals(region.destination, (events.filterIsInstance<TableIntent.Move>().single().destination as TableDestination.Combat).destination)
    }

    @Test fun frozenEffectHasNoEditableSourceAndDoesNotExpandOccurrenceSelection() {
        val f = BasicTableFixture(); val live = state(f)
        val source = live.board.cards.single().copy(token = true, corner = "5/2")
        val effect = live.board.stackEffects.first().copy(frozen = source)
        val frozen = live.copy(board = live.board.copy(cards = emptyList(), combat = null,
            stackEffects = listOf(effect), containers = live.board.containers.map { it.copy(cards = if (it.zone == PlayZone.STACK) listOf(effect.id) else emptyList()) }))
        val scene = TablePresentation.scene(f.game, frozen, f.actor, 480f, 1000f, false)
        val drawing = scene.items.single { it.id.startsWith("stackDrawing/") }
        assertTrue(drawing.frozen); assertNull(drawing.sourceId)
        assertFalse(effect.id in scene.sources)
        val selected = TableSelection(setOf(effect.id))
        assertEquals(setOf(effect.id), selected.ids)
        assertTrue(toolkitQuickContext(frozen, selected, ExplicitStackTarget(effect.id, StackTargetKind.EFFECT)).selectedSourceIds.isEmpty())
        assertEquals(1, scene.items.single { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }.cards.size)
    }
}
