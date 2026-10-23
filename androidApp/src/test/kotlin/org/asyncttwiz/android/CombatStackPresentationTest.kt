package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CombatStackPresentationTest {
    private fun populated(): PlayState {
        val commands = CombatCommands()
        val state = combatState()
        val declared = CombatStackController.declare(state, combatId(8), listOf(20, 21).map {
            CombatStackController.bind(state.board, combatId(it))
        }, false, commands::command).prediction
        return CombatStackController.place(declared, combatId(8), listOf(combatId(22), combatId(23)),
            CombatDestination(declared.board.combat!!.id, CombatRole.BLOCKER, declared.board.combat!!.pairs.first().id), commands::command).prediction
    }

    @Test fun compactPairWidthsCommonScaleAndRotatedEnvelopeNeverShrink() {
        val state = populated().let { it.copy(board = it.board.copy(cards = it.board.cards.map { card ->
            if (card.id == combatId(22)) card.copy(tapped = true) else card
        })) }
        val geometry = CombatStackPresentation.combat(state.board, 48f, 68f)
        val card = geometry.cards.single { it.card.id == combatId(22) }
        assertEquals(68f, card.bounds.width)
        assertEquals(48f, card.bounds.height)
        assertEquals(48f, geometry.cards.single { it.card.id == combatId(20) }.bounds.width)
        val widths = geometry.dividers.chunked(2).map { it[1] - it[0] }
        assertTrue(widths[0] > widths[1])
        assertEquals(48f + 2 * 48f * .04f, widths[1], .001f)
        val blockers = geometry.cards.filter { CombatStackRules.location(state.board.combat!!, it.viewId)!!.role == CombatRole.BLOCKER }
        val attackers = geometry.cards.filter { CombatStackRules.location(state.board.combat!!, it.viewId)!!.role == CombatRole.ATTACKER }
        assertTrue(blockers.maxOf { it.bounds.y + it.bounds.height } < attackers.minOf { it.bounds.y })
        assertEquals(6, geometry.destinations.count { it.destination.pairId == null }) // 3 gaps, both sides.
        assertTrue(geometry.destinations.filter { it.destination.pairId == null }.all { it.bounds.width == 48f })
    }

    @Test fun closedVersusEmptyAreaAndAccessibleInsertionChoices() {
        val state = combatState()
        assertTrue(CombatStackPresentation.combat(state.board, 48f, 68f).destinations.isEmpty())
        val commands = CombatCommands()
        val opened = CombatStackController.single(state, combatId(8), PlayAction.Combat(CombatAction.Open), commands::command).prediction
        val scene = CombatStackPresentation.combat(opened.board, 48f, 68f)
        assertEquals(48f, scene.width)
        assertEquals(2, scene.destinations.size)
        assertTrue(scene.cards.isEmpty())
        assertTrue(scene.dividers.isEmpty())
        assertTrue(opened.board.combat!!.pairs.isEmpty())
        val populated = populated()
        val choices = CombatStackPresentation.combat(populated.board, 48f, 68f).destinations
        assertTrue(choices.any { it.destination.insertion == CombatInsertion.GAP && it.destination.cardGap.beforeId != null })
        assertTrue(choices.all { it.destination.areaId == populated.board.combat!!.id && it.label.isNotBlank() })
    }

    @Test fun stackTopThreeDrawingAllOrderedContentsAndCountAtCommonScale() {
        val commands = CombatCommands()
        var state = combatState()
        repeat(27) { state = CombatStackController.addEffects(state, combatId(8),
            listOf(CombatStackController.bind(state.board, combatId(20))), commands::command).prediction }
        val scene = CombatStackPresentation.stack(state.board, 48f, 68f)
        assertEquals(27, scene.count)
        assertEquals(3, scene.visible.size)
        assertEquals(27, scene.orderedIds.distinct().size)
        assertEquals(scene.orderedIds.take(3), scene.visible.map { it.viewId })
        assertTrue(scene.visible.all { it.bounds.width == 48f && it.bounds.height == 68f && it.projection })
        assertTrue(scene.visible.zipWithNext().all { (a, b) -> a.bounds.x < b.bounds.x && a.bounds.y < b.bounds.y })
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
