package org.asyncttwiz.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

class BattlefieldOrientationTest {
    @Test fun onlyBattlefieldCardsAcceptEitherOrientationEvenWhenAssignmentWouldBeANoop() {
        for (zone in PlayZone.entries) {
            val root = inZone(zone)
            val card = root.board.cards.first()
            for (tapped in listOf(true, false)) {
                val change = gameplayChange(root, PlayAction.SetTapped(card.id, tapped))
                if (zone == PlayZone.BATTLEFIELD) assertNull(change.reason)
                else {
                    assertEquals("inapplicable", change.reason, "$zone / $tapped")
                    assertNull(change.mutation)
                }
            }
        }
    }

    @Test fun movesOutsideBattlefieldUntapAndRecordedInverseRestoresTheTap() {
        val root = inZone(PlayZone.BATTLEFIELD, tapped = true)
        val card = root.board.cards.first()
        for (target in root.board.containers) {
            val destination = when (target.zone) {
                PlayZone.BATTLEFIELD -> MoveDestination(target.id, MoveInsertion.EMPTY, BoardPosition(10, 0))
                PlayZone.LIBRARY, PlayZone.STACK -> MoveDestination(target.id, MoveInsertion.TOP)
                else -> MoveDestination(target.id, MoveInsertion.UNORDERED)
            }
            val action = PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement), destination,
                if (target.zone == PlayZone.STACK) GameplayRules.stackDigest(root) else null)
            val change = gameplayChange(root, action)
            val next = assertNotNull(change.state, change.reason)
            assertEquals(target.zone == PlayZone.BATTLEFIELD, next.board.cards.first().tapped)
            val event = assertNotNull(change.mutation)
            assertEquals(7, event.transition.movement!!.coverRulesVersion)
            assertEquals(next, GameplayHistory.apply(root, event))
            assertEquals(root, GameplayHistory.unapply(next, event))
        }
    }

    /** The battlefield feels infinite: drags may place cards at negative columns, which the rules accept and replay. */
    @Test fun battlefieldMovesAcceptNegativeColumnsAndReplayExactly() {
        val root = inZone(PlayZone.BATTLEFIELD)
        val card = root.board.cards.first()
        val field = root.board.containers.single { it.zone == PlayZone.BATTLEFIELD }
        val change = gameplayChange(root, PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
            MoveDestination(field.id, MoveInsertion.EMPTY, BoardPosition(-4, 1))))
        val next = assertNotNull(change.state, change.reason)
        assertEquals(BoardPosition(-4, 1), next.board.cards.first().placement!!.position)
        val event = assertNotNull(change.mutation)
        assertEquals(next, GameplayHistory.apply(root, event))
        assertEquals(root, GameplayHistory.unapply(next, event))
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(DeckJson.canonical(event))
        assertEquals(next, GameplayHistory.apply(root, decoded))
    }

    @Test fun legacyTappedCardsAreNormalizedByOtherNonBattlefieldMovesToo() {
        val root = inZone(PlayZone.HAND, tapped = true)
        val card = root.board.cards.first()
        val target = root.board.containers.single { it.zone == PlayZone.GRAVEYARD }
        val change = gameplayChange(root, PlayAction.MoveCard(card.id, MoveSource(card.containerId),
            MoveDestination(target.id, MoveInsertion.UNORDERED)))
        assertFalse(assertNotNull(change.state).board.cards.first().tapped)
    }

    @Test fun retainedOldTapEligibilityAndMoveDeltaReplayUnchanged() {
        val root = inZone(PlayZone.HAND)
        val card = root.board.cards.first()
        val action = PlayAction.SetTapped(card.id, true)
        val command = GameCommand(gameplayId(100), GameScope(root.gameId, playId = root.id),
            GameAction.Play(action), gameplayId(80), "1")
        val oldTap = GameplayRules.record(root, gameplayId(7), command,
            root.board.copy(cards = root.board.cards.map { if (it.id == card.id) it.copy(tapped = true) else it }),
            GameplayTransition(fields = listOf(GameplayFieldChange(card.id, GameplayField.TAPPED, "false", "true"))))
        val tap = retained(root, oldTap)
        val onHand = assertNotNull(GameplayHistory.apply(root, tap))
        assertTrue(onHand.board.cards.first().tapped)
        assertEquals(root, GameplayHistory.unapply(onHand, tap))
        assertEquals(1, tap.transition.concerns!!.rulesVersion)

        val target = onHand.board.containers.single { it.zone == PlayZone.GRAVEYARD }
        val fresh = gameplayChange(onHand, PlayAction.MoveCard(card.id, MoveSource(card.containerId),
            MoveDestination(target.id, MoveInsertion.UNORDERED)), 101)
        val oldAfter = fresh.state!!.copy(board = fresh.state.board.copy(cards = fresh.state.board.cards.map {
            if (it.id == card.id) it.copy(tapped = true) else it }))
        val oldMove = retained(onHand, fresh.copy(state = oldAfter, mutation = fresh.mutation!!.copy(transition =
            fresh.mutation.transition.copy(fields = fresh.mutation.transition.fields.filterNot { it.field == GameplayField.TAPPED },
                movement = fresh.mutation.transition.movement!!.copy(coverRulesVersion = 5)))))
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(DeckJson.canonical(oldMove))
        assertEquals(oldAfter, GameplayHistory.apply(onHand, decoded))
        assertEquals(onHand, GameplayHistory.unapply(oldAfter, decoded))
    }

    @Test fun newTapConcernAllowsBattlefieldRelocationButNotRebasingIntoHand() {
        val root = inZone(PlayZone.BATTLEFIELD)
        val card = root.board.cards.first()
        val tap = gameplayChange(root, PlayAction.SetTapped(card.id, true)).mutation!!
        assertEquals(3, tap.transition.concerns!!.rulesVersion)
        val relocated = root.copy(board = root.board.copy(cards = root.board.cards.map {
            if (it.id == card.id) it.copy(placement = it.placement!!.copy(position = BoardPosition(20, 0))) else it }))
        assertNotNull(GameplayHistory.replay(relocated, tap).state)
        assertEquals("replayBlocked", GameplayHistory.replay(inZone(PlayZone.HAND), tap).reason)
    }

    private fun retained(from: PlayState, change: GameplayChange): GameplayMutation {
        val event = change.mutation!!
        val next = change.state!!
        val delta = GameplayDeltas.capture(from, next, event.id)
        val concerns = GameplayConcerns.capture(from, next, event, delta).let { concerns -> concerns.copy(rulesVersion = 1,
            fields = concerns.fields.map { if (it.field is ConcernField.TapEligible)
                it.copy(before = JsonPrimitive(true), after = JsonPrimitive(true)) else it }) }
        val recorded = event.copy(schemaVersion = 2, transition = event.transition.copy(delta = delta, concerns = concerns),
            bookkeeping = GameplayBookkeeping(from.currentGroup, from.currentBranchId, next.currentGroup))
        return recorded.copy(transition = recorded.transition.copy(delta = delta.copy(seal = GameplayDeltas.seal(recorded))))
    }

    private fun inZone(zone: PlayZone, tapped: Boolean = false): PlayState = gameplayFixture().let { root ->
        val card = root.board.cards.first()
        val target = root.board.containers.single { it.zone == zone }
        root.copy(board = root.board.copy(cards = root.board.cards.map { if (it.id == card.id)
            it.copy(containerId = target.id, tapped = tapped, covered = zone in listOf(PlayZone.HAND, PlayZone.LIBRARY),
                placement = if (zone == PlayZone.BATTLEFIELD) PlayPlacement(BoardPosition(0, 0), gameplayId(200)) else null) else it },
            containers = root.board.containers.map { it.copy(cards = it.cards - card.id + if (it.id == target.id) listOf(card.id) else emptyList()) }))
    }
}
