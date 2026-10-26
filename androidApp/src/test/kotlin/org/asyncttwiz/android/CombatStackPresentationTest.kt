package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CombatStackPresentationTest {
    @Test fun projectionNoteChipsUseOccurrenceTextNotTheSourceNoteAndPingTargetsTheOccurrence() {
        val f = TableStatesFixture
        val original = f.combat()
        val state = original.copy(board = original.board.copy(cards = original.board.cards.map { it.copy(note = "source") },
            stackEffects = original.board.stackEffects.map { it.copy(note = "effect") },
            combat = original.board.combat!!.let { area -> area.copy(pairs = area.pairs.map { pair -> pair.copy(
                attackers = pair.attackers.map { it.copy(note = "attacker") }, blockers = pair.blockers.map { it.copy(note = "blocker") }) }) }))
        val scene = TablePresentation.scene(f.game, state, f.a, 412f, 892f, false)
        for (item in scene.items.filter { it.projection && !it.covered }) {
            assertEquals(CombatStackRules.projectionNote(state.board, item.cardId!!), item.note)
            assertEquals(PingTarget(PingTargetKind.OCCURRENCE, item.cardId), tablePingTarget(state, item))
            assertNotEquals("source", item.note)
        }
        assertTrue(scene.items.any { it.projection && it.note.isNotEmpty() })
    }
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
        val scene = TablePresentation.scene(f.game, state, f.a, 412f, 892f, false)
        assertTrue(scene.items.filter { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD }.all { !it.tapped })
        assertFalse(scene.items.single { it.cardId == f.id("attacker2") }.tapped)
        assertTrue(state.board.cards.single { it.id == f.source(f.a, "1/2").id }.tapped)
        assertTrue(state.board.cards.single { it.id == actual }.tapped) // Presentation does not mutate the source.
    }
    private fun populated(): PlayState {
        val commands = CombatCommands()
        val state = combatState()
        val declared = CombatStackController.declare(state, combatId(8), listOf(20, 21).map {
            CombatStackController.bind(state.board, combatId(it))
        }, false, commands::command).prediction
        return CombatStackController.place(declared, combatId(8), listOf(combatId(22), combatId(23)),
            CombatDestination(declared.board.combat!!.id, CombatRole.BLOCKER, declared.board.combat!!.pairs.first().id), commands::command).prediction
    }

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
        assertEquals(6, geometry.destinations.count { it.destination.pairId == null }) // 3 gaps, both sides.
        assertEquals(widths.sum(), geometry.width, .001f) // No reserved insertion slots.
        assertTrue(geometry.dividers.all { it > 0 && it < geometry.width })
        val between = geometry.destinations.filter { it.destination.pairId == null && !it.extendLeft && !it.extendRight }
        assertTrue(between.all { it.bounds.width == 24f && it.bounds.center.x == geometry.dividers.single() })
        for (role in CombatRole.entries) {
            val regions = geometry.destinations.filter { it.destination.role == role }
            val y = regions.first().bounds.center.y
            fun target(x: Float) = regions.asReversed().firstOrNull { it.contains(TablePoint(x, y)) }?.destination
            assertEquals(IdentityGap(beforeId = state.board.combat!!.pairs.first().id), target(-1e20f)?.pairGap)
            assertEquals(IdentityGap(afterId = state.board.combat!!.pairs.last().id), target(1e20f)?.pairGap)
            assertNull(regions.firstOrNull { it.contains(TablePoint(-1e20f, -1f)) })
            assertNull(target(geometry.dividers.single() - 11f)?.pairId)
            assertEquals(state.board.combat!!.pairs.last().id, target(geometry.dividers.single() + 14f)?.pairId)
        }
    }

    @Test fun closedVersusEmptyAreaAndAccessibleInsertionChoices() {
        val state = combatState()
        assertTrue(CombatStackPresentation.combat(state.board, 48f, 68f).destinations.isEmpty())
        val commands = CombatCommands()
        val opened = CombatStackController.single(state, combatId(8), PlayAction.Combat(CombatAction.Open), commands::command).prediction
        val scene = CombatStackPresentation.combat(opened.board, 48f, 68f)
        assertEquals(48f + 2 * CombatStackPresentation.slotMargin(48f), scene.width)
        assertEquals(2, scene.destinations.size)
        assertTrue(scene.cards.isEmpty())
        assertTrue(scene.dividers.isEmpty())
        assertTrue(opened.board.combat!!.pairs.isEmpty())
        assertTrue(scene.destinations.all { it.extendLeft && it.extendRight && it.contains(TablePoint(-1e20f, it.bounds.center.y)) &&
            it.contains(TablePoint(1e20f, it.bounds.center.y)) })
        val populated = populated()
        val choices = CombatStackPresentation.combat(populated.board, 48f, 68f).destinations
        assertTrue(choices.any { it.destination.insertion == CombatInsertion.GAP && it.destination.cardGap.beforeId != null })
        assertTrue(choices.all { it.destination.areaId == populated.board.combat!!.id && it.label.isNotBlank() })
    }

    @Test fun groupFacesAppendToTheirRowAndInternalCardGapsRetainIdentityTargets() {
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
        val gap = geometry.destinations.single { it.destination.cardGap.beforeId == pair.blockers.last().id }
        assertEquals(gap.destination, target(gap.bounds.center))
        assertEquals(IdentityGap(pair.blockers.first().id, pair.blockers.last().id), gap.destination.cardGap)
    }

    @Test fun eachRowIsCenteredInItsSlotWithUnchangedCardGapsAndDividerPadding() {
        val state = populated()
        for (width in listOf(28f, 48f, 56f)) {
            val geometry = CombatStackPresentation.combat(state.board, width, width * 40 / 28)
            val pair = state.board.combat!!.pairs.first()
            val blockers = geometry.cards.filter { card -> pair.blockers.any { it.id == card.viewId } }.map { it.bounds }
            val attacker = geometry.cards.single { it.viewId == pair.attackers.single().id }.bounds
            val slotCenter = geometry.dividers.single() / 2
            assertEquals(slotCenter, attacker.center.x, .001f)
            assertEquals(slotCenter, (blockers.first().x + blockers.last().right) / 2, .001f)
            assertEquals(width * .04f, blockers.last().x - blockers.first().right, .001f)
            assertEquals(CombatStackPresentation.slotMargin(width), geometry.dividers.single() - blockers.last().right, .001f)
            val next = geometry.cards.single { it.viewId == state.board.combat!!.pairs.last().attackers.single().id }.bounds
            assertEquals(CombatStackPresentation.slotMargin(width), next.x - geometry.dividers.single(), .001f)
        }
    }

    @Test fun slotsRecenterOnAdditionAndRemovalInBothOrientationsWithoutResettingCamera() {
        val f = TableStatesFixture
        val initial = f.combat()
        val area = initial.board.combat!!
        for (landscape in listOf(false, true)) for (pairs in listOf(area.pairs, area.pairs.take(1), emptyList())) {
            val state = initial.copy(board = initial.board.copy(combat = area.copy(pairs = pairs)))
            val geometry = CombatStackPresentation.combat(state.board, 28f, 40f)
            val home = TablePresentation.scene(f.game, state, f.a, if (landscape) 892f else 412f,
                if (landscape) 412f else 892f, landscape)
            val rows = home.combatDrops.filter { it.destination.insertion == CombatInsertion.APPEND && it.destination.pairId != null }
            val center = if (rows.isEmpty()) home.combatDrops.first().landing.x else
                (rows.minOf { it.bounds.x } + rows.maxOf { it.bounds.right }) / 2
            assertEquals(home.world!!.viewport.center.x, center, .001f)
            if (rows.isNotEmpty()) assertEquals(geometry.width, rows.maxOf { it.bounds.right } - rows.minOf { it.bounds.x }, .001f)
            val camera = TableCamera(13f, 5f, 1.4f)
            val moved = TablePresentation.scene(f.game, state, f.a, if (landscape) 892f else 412f,
                if (landscape) 412f else 892f, landscape, TableLayoutState(camera = camera))
            assertEquals(camera, moved.camera)
            val movedRows = moved.combatDrops.filter { it.destination.insertion == CombatInsertion.APPEND && it.destination.pairId != null }
            val movedCenter = if (movedRows.isEmpty()) moved.combatDrops.first().landing.x else
                (movedRows.minOf { it.bounds.x } + movedRows.maxOf { it.bounds.right }) / 2
            assertEquals(center - camera.x * camera.zoom, movedCenter, .001f)
        }
    }

    @Test fun stackTopThreeDrawingAllOrderedContentsAndCountAtCommonScale() {
        val commands = CombatCommands()
        var state = combatState()
        repeat(27) { state = CombatStackController.addEffects(state, combatId(8),
            listOf(CombatStackController.bind(state.board, combatId(20))), commands::command).prediction }
        val scene = CombatStackPresentation.stack(state.board, 48f, 68f)
        assertEquals(27, scene.count)
        assertEquals(1, scene.visible.size)
        assertEquals(27, scene.orderedIds.distinct().size)
        assertEquals(scene.orderedIds.take(1), scene.visible.map { it.viewId })
        assertTrue(scene.visible.all { it.bounds.width == 48f && it.bounds.height == 68f && it.projection })
        assertTrue(scene.visible.zipWithNext().all { (a, b) -> a.bounds.x > b.bounds.x && a.bounds.y > b.bounds.y })
    }

    @Test fun contextualDestinationsAreExplicitTypedMetadataNotRulesInference() {
        var state = combatState()
        val source = state.board.cards.first()
        state = state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id == source.id) it.copy(containerId = combatId(12)) else it
        }, containers = state.board.containers.map { when (it.zone) {
            PlayZone.STACK -> it.copy(cards = listOf(source.id))
            else -> it.copy(cards = it.cards - source.id)
        } }))
        val permanent = StackContexts.context(state, source.id)
        assertEquals(PlayZone.BATTLEFIELD, permanent.destination)
        val choices = listOf(StackMoveChoice("Graveyard", PlayZone.GRAVEYARD, MoveDestination(combatId(13), MoveInsertion.UNORDERED)),
            StackMoveChoice("Battlefield", PlayZone.BATTLEFIELD, MoveDestination(combatId(10), MoveInsertion.EMPTY,
                BoardPosition(4, 2))))
        assertEquals(listOf(choices[1]), StackContexts.destinations(permanent, choices, true))
        assertEquals(choices, StackContexts.destinations(permanent, choices, false))
        val spell = state.copy(definitions = state.definitions.map { it.copy(definition = it.definition.copy(faces = it.definition.faces.map { face ->
            face.copy(typeLine = "Instant", rulesText = "a creature text is not classification")
        })) })
        assertEquals(listOf(choices[0]), StackContexts.destinations(StackContexts.context(spell, source.id), choices, true))
        val unknown = state.copy(definitions = emptyList())
        assertTrue(StackContexts.destinations(StackContexts.context(unknown, source.id), choices, true).isEmpty())
        assertEquals(choices, StackContexts.destinations(StackContexts.context(unknown, source.id), choices, false))
        val ambiguous = spell.copy(definitions = spell.definitions.map { it.copy(definition = it.definition.copy(
            faces = it.definition.faces.map { face -> face.copy(typeLine = "Instant Creature") })) })
        assertEquals(StackContextKind.UNCLASSIFIED, StackContexts.context(ambiguous, source.id).kind)
        val otherFace = state.copy(definitions = state.definitions.map { it.copy(definition = it.definition.copy(
            faces = it.definition.faces + DefinitionFace(combatId(699), "Other face", typeLine = "Instant"))) })
        assertEquals(StackContextKind.PERMANENT, StackContexts.context(otherFace, source.id).kind)
        assertFailsWith<IllegalArgumentException> { StackContexts.context(state, combatId(999)) }
    }

    @Test fun liveEffectMovesBindSourceButFrozenEffectOnlyRemovesAndInspects() {
        val state = combatState()
        val commands = CombatCommands()
        val live = CombatStackController.addEffects(state, combatId(8),
            listOf(CombatStackController.bind(state.board, combatId(25))), commands::command).prediction
        val effect = live.board.stackEffects.single()
        val context = StackContexts.context(live, effect.id)
        val choice = StackMoveChoice("Graveyard", PlayZone.GRAVEYARD, MoveDestination(combatId(13), MoveInsertion.UNORDERED))
        assertEquals(combatId(25), context.target.sourceId)
        assertTrue(StackContexts.destinations(context, listOf(choice), true).isEmpty())
        assertEquals(listOf(choice), StackContexts.destinations(context, listOf(choice), false))
        val token = live.board.cards.single { it.id == effect.sourceId }.copy(corner = "5/2")
        val frozen = live.copy(board = live.board.copy(cards = live.board.cards.filterNot { it.id == token.id },
            stackEffects = listOf(effect.copy(frozen = token))))
        val frozenContext = StackContexts.context(frozen, effect.id)
        assertNull(frozenContext.target.sourceId)
        assertTrue(StackContexts.destinations(frozenContext, listOf(choice), false).isEmpty())
        val scene = CombatStackPresentation.stack(frozen.board, 48f, 68f)
        assertTrue(scene.visible.single().frozen)
        val label = CombatStackPresentation.label(frozen, scene.visible.single())
        assertTrue(label.contains("Covered card") && label.contains("read-only"))
        assertFalse(label.contains("Sample"))
        assertEquals("5/2", CombatStackRules.inspection(frozen.board, effect.id)!!.corner)
    }
}
