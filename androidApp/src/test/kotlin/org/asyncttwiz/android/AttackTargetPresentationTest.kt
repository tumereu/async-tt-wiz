package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

internal fun attackTargetState(): PlayState {
    val base = combatState()
    val definitions = listOf("Legendary Planeswalker — Test", "Battle — Siege", "Creature").mapIndexed { n, type ->
        val definition = CardDefinition("Target $n", listOf(DefinitionFace(combatId(90 + n), "Target $n", typeLine = type)))
        SubmittedDefinition(DeckJson.digest(definition), definition)
    }
    val targets = definitions.mapIndexed { n, definition -> PlayCard(combatId(80 + n), definition.digest, combatId(8),
        combatId(70), combatId(90 + n), covered = false, placement = PlayPlacement(BoardPosition(n, 0), combatId(100 + n))) }
    val initial = base.copy(definitions = base.definitions + definitions, board = base.board.copy(cards = base.board.cards + targets,
        containers = base.board.containers + PlayContainer(combatId(70), PlayZone.BATTLEFIELD, combatId(8), targets.map { it.id })))
    val commands = CombatCommands()
    val declared = CombatStackController.declare(initial, combatId(7), (20..22).map { CombatStackController.bind(initial.board, combatId(it)) },
        false, commands::command).prediction
    val area = declared.board.combat!!
    return CombatStackController.place(declared, combatId(7), listOf(combatId(23)),
        CombatDestination(area.id, CombatRole.BLOCKER, area.pairs.first().id), commands::command).prediction
}

class AttackTargetPresentationTest {
    @Test fun groupingKeepsOneRowPerRoleAndReservedLabelsAndBoldLaneBoundaries() {
        val original = attackTargetState()
        val area = original.board.combat!!
        val state = original.copy(board = original.board.copy(combat = area.copy(pairs = area.pairs.mapIndexed { n, pair ->
            pair.copy(attackTarget = if (n == 1) null else combatId(80))
        })))
        val scene = CombatStackPresentation.combat(state.board, 48f, 68f, state.definitions, mapOf(combatId(8) to "Bob"))
        assertEquals(listOf("Target 0", "→ Bob"), scene.lanes.map { it.label })
        assertEquals(1, scene.laneDividers.size)
        val displayed = scene.cards.filter { CombatStackRules.location(state.board.combat!!, it.viewId)!!.role == CombatRole.ATTACKER }
        assertEquals(listOf(combatId(20), combatId(22), combatId(21)), displayed.map { it.card.id })
        assertEquals(1, displayed.map { it.bounds.y }.distinct().size)
        assertTrue(scene.cards.all { it.bounds.y >= scene.labelHeight })
        assertTrue(scene.lanes.all { it.bounds.bottom <= scene.cards.minOf { it.bounds.y } })
        assertEquals(area.pairs.map { it.id }, state.board.combat!!.pairs.map { it.id })
        // Visual neighbors can differ from recorded order: all insertion gaps must still be usable.
        for (drop in scene.destinations.filter { it.destination.pairId == null }) {
            val result = CombatStackController.place(state, combatId(7), listOf(combatId(24)), drop.destination, CombatCommands()::command)
            assertEquals(1, result.commands.size)
        }
    }

    @Test fun emptyCombatHasDefaultPlayerLabelAndChoicesOnlyIncludeDefendingWalkersAndBattles() {
        val original = attackTargetState()
        val empty = original.copy(board = original.board.copy(combat = original.board.combat!!.copy(pairs = emptyList())))
        val labels = CombatStackPresentation.combat(empty.board, 48f, 68f, empty.definitions, mapOf(combatId(8) to "Bob")).lanes
        assertEquals(listOf("→ Bob"), labels.map { it.label })
        assertEquals(listOf(null, combatId(80), combatId(81)), attackTargetChoices(original, mapOf(combatId(8) to "Bob"), combatId(7)).map { it.first })
    }

    @Test fun attackTargetChoicesExcludeFaceDownWalkersAndBattlesEvenWhenKnown() {
        val original = attackTargetState()
        for (known in listOf(false, true)) {
            val hidden = original.copy(board = original.board.copy(cards = original.board.cards.map { card ->
                if (card.id in listOf(combatId(80), combatId(81)))
                    card.copy(covered = true, explicitlyCovered = true, knownToAll = known) else card
            }))
            for (viewer in listOf(combatId(7), combatId(8))) {
                assertEquals(listOf(null to "→ Bob"), attackTargetChoices(hidden, mapOf(combatId(8) to "Bob"), viewer))
            }
        }
        // Legacy covered flags in public zones are not explicit face-down state.
        val legacy = original.copy(board = original.board.copy(cards = original.board.cards.map { it.copy(covered = true) }))
        assertEquals(listOf(null, combatId(80), combatId(81)), attackTargetChoices(legacy, emptyMap(), combatId(7)).map { it.first })
    }

    @Test fun laneLabelsAndArtFollowViewerVisibilityWithoutExposingHiddenFaces() {
        val initial = TableFixture.play(board = 1, library = 0)
        val definition = TableFixture.definition.copy(faces = listOf(TableFixture.definition.faces.single().copy(
            typeLine = "Planeswalker", imageUri = "https://cards.scryfall.io/normal/front/a/b/test.jpg")))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val targetId = "b/BATTLEFIELD/0"
        val original = initial.copy(definitions = listOf(submitted), board = initial.board.copy(
            cards = initial.board.cards.map { it.copy(definition = submitted.digest) },
            combat = CombatArea("combat", listOf(CombatPair("pair",
                attackers = listOf(CombatOccurrence("attacker", "a/BATTLEFIELD/0")), attackTarget = targetId)))))
        for (zone in listOf(PlayZone.BATTLEFIELD, PlayZone.HAND, PlayZone.LIBRARY)) {
            for (faceDown in listOf(false, true)) for (known in listOf(false, true)) {
                val destination = TableFixture.container("b", zone)
                val state = original.copy(board = original.board.copy(
                    cards = original.board.cards.map { if (it.id == targetId) it.copy(containerId = destination,
                        covered = faceDown, explicitlyCovered = faceDown, knownToAll = known) else it },
                    containers = original.board.containers.map { container -> container.copy(
                        cards = (container.cards - targetId) + if (container.id == destination) listOf(targetId) else emptyList()) }))
                for (viewer in listOf("a", "b")) for (landscape in listOf(false, true)) {
                    val visible = !faceDown && (known || zone == PlayZone.BATTLEFIELD || zone == PlayZone.HAND && viewer == "b")
                    val scene = TablePresentation.scene(TableFixture.game, state, viewer,
                        if (landscape) 900f else 412f, if (landscape) 412f else 820f, landscape)
                    val lane = scene.combatLabels.single()
                    assertEquals(if (visible) "Secret face" else "→ Face-down card", lane.label)
                    assertEquals(lane.label, lane.lines.joinToString(""))
                    if (visible) assertNotNull(lane.artwork) else assertNull(lane.artwork)
                }
            }
        }
    }

    @Test fun multiAttackerRetargetAndCrossLaneDragAreSingleAtomicBatchesKeepingBlockers() {
        val original = attackTargetState()
        val pairs = original.board.combat!!.pairs
        val selected = pairs.take(2).flatMap { it.attackers }.map { it.id }
        val commands = CombatCommands()
        val plan = CombatStackController.attackTarget(original, combatId(7), selected, combatId(80), commands::command)
        val batch = assertIs<GameAction.PlayBatch>(plan.commands.single().action)
        assertEquals(2, batch.actions.size)
        assertTrue(plan.prediction.board.combat!!.pairs.take(2).all { it.attackTarget == combatId(80) })
        assertEquals(pairs.map { it.blockers }, plan.prediction.board.combat!!.pairs.map { it.blockers })
        val destination = CombatDestination(original.board.combat!!.id, CombatRole.ATTACKER, pairs.last().id)
        val drag = CombatStackController.place(plan.prediction, combatId(7), selected, destination, commands::command)
        assertEquals(2, assertIs<GameAction.PlayBatch>(drag.commands.single().action).actions.size)
        assertEquals(original.board.combat, drag.prediction.board.combat)
        assertEquals(original.board.cards, drag.prediction.board.cards)
    }

    @Test fun crossLaneBlockerRowDropMovesAttackerIdentityAndLeavesOriginalBlockersInPlace() {
        val original = attackTargetState()
        val commands = CombatCommands()
        val pairs = original.board.combat!!.pairs
        val attacker = pairs.first().attackers.single()
        val targeted = CombatStackController.attackTarget(original, combatId(7), listOf(attacker.id), combatId(80), commands::command).prediction
        val destination = CombatDestination(targeted.board.combat!!.id, CombatRole.BLOCKER, pairs.last().id)
        assertTrue(CombatStackController.retargetDrop(targeted.board, listOf(attacker.id), destination).isEmpty())
        val plan = CombatStackController.place(targeted, combatId(7), listOf(attacker.id), destination, commands::command)
        val batch = assertIs<GameAction.PlayBatch>(plan.commands.single().action)
        assertIs<CombatAction.Place>(assertIs<PlayAction.Combat>(batch.actions.single()).action)
        val result = plan.prediction.board.combat!!
        val origin = result.pairs.single { it.id == pairs.first().id }
        assertTrue(origin.attackers.isEmpty())
        assertEquals(pairs.first().blockers, origin.blockers)
        assertEquals(combatId(80), origin.attackTarget)
        assertEquals(pairs.last().copy(blockers = pairs.last().blockers + attacker), result.pairs.last())
        assertEquals(targeted.board.cards, plan.prediction.board.cards)
        val change = GameplayRules.change(targeted, combatId(7), plan.commands.single().playMembers().single())
        assertNull(change.reason)
        assertEquals(change, GameplayHistory.replay(targeted, change.mutation!!))
        assertEquals(plan.prediction, change.state)
    }

    @Test fun defaultSingleCornersUseTheSameDragAsPowerToughness() {
        for (face in listOf(DefinitionFace("walker", "Walker", typeLine = "Planeswalker", loyalty = "4"),
            DefinitionFace("battle", "Battle", typeLine = "Battle", defense = "7"),
            DefinitionFace("saga", "Saga", typeLine = "Enchantment — Saga"))) {
            val value = face.defaultCorner()
            assertEquals((value.toInt() + 1).toString(), cornerDragValue(value, 1))
            assertEquals((value.toInt() - 1).toString(), cornerDragValue(value, -1))
        }
    }
}
