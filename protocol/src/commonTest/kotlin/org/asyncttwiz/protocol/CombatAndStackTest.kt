package org.asyncttwiz.protocol

import kotlin.test.*

class CombatAndStackTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun state(): PlayState {
        val cards = (20..25).map { n -> PlayCard(id(n), "definition", id(7), id(10), id(11),
            covered = false, corner = "1/2", note = "last live", token = n == 25,
            placement = PlayPlacement(BoardPosition(n, n), id(n + 100))) }
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))), cards,
                listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, id(7), cards.map { it.id }),
                    PlayContainer(id(12), PlayZone.STACK), PlayContainer(id(13), PlayZone.GRAVEYARD, id(7)),
                    PlayContainer(id(14), PlayZone.EXILE, id(7))), TurnState(id(7))),
            emptyList(), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    }
    private fun command(action: PlayAction, n: Int) = GameCommand(id(n), GameScope(id(1), playId = id(2)),
        GameAction.Play(action), id(9), "1", invocationId = id(500))
    private fun change(state: PlayState, action: PlayAction, n: Int): GameplayChange {
        val command = command(action, n)
        assertTrue(GameWire.valid(command))
        assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
        return GameplayRules.change(state, id(8), command)
    }
    private fun apply(state: PlayState, action: PlayAction, n: Int): PlayState {
        val result = change(state, action, n)
        assertNull(result.reason)
        if (result.mutation != null) assertEquals(result, GameplayHistory.replay(state, result.mutation))
        return result.state!!
    }
    private fun open(state: PlayState = state()) = apply(state, PlayAction.Combat(CombatAction.Open), 1000)
    private fun declare(state: PlayState, n: Int) = apply(state,
        PlayAction.Combat(CombatAction.Declare(state.board.combat!!.id, id(n))), n + 1000)
    private fun place(state: PlayState, source: Int, pairId: String?, role: CombatRole, n: Int,
        pairGap: IdentityGap = IdentityGap(), insertion: CombatInsertion = CombatInsertion.APPEND,
        cardGap: IdentityGap = IdentityGap()): PlayState {
        val area = state.board.combat!!
        val old = CombatStackRules.occurrences(area).find { it.sourceId == id(source) }
        return apply(state, PlayAction.Combat(CombatAction.Place(id(source), CombatDestination(area.id, role,
            pairId, pairGap, insertion, cardGap), old?.let { CombatStackRules.location(area, it.id) })), n)
    }
    private fun effect(state: PlayState, source: Int, request: Int) = apply(state,
        PlayAction.Stack(StackAction.AddEffect(id(source), CombatStackRules.stackDigest(state))), request)

    @Test fun copyingAndControllerChangesAreProjectionOnlyExactDeltas() {
        val initial = effect(state(), 20, 8000)
        val original = initial.board.stackEffects.single()
        val changed = change(initial, PlayAction.Stack(StackAction.SetController(original.id, id(8))), 8001)
        val controlled = changed.state!!
        assertEquals(initial.board.cards, controlled.board.cards)
        assertEquals(id(8), CombatStackRules.controller(controlled.board, original.id))
        assertEquals(initial.board, GameplayHistory.reverse(controlled, changed.mutation!!))
        val copied = change(controlled, PlayAction.Stack(StackAction.CopyEffect(original.id, CombatStackRules.stackDigest(controlled))), 8002)
        val next = copied.state!!
        val copy = next.board.stackEffects.last()
        assertTrue(copy.isCopy)
        assertEquals(id(8), copy.controller)
        assertEquals(original.sourceId, copy.sourceId)
        assertEquals(copy.id, CombatStackRules.stack(next.board).cards.first())
        assertEquals(initial.board.cards, next.board.cards)
        assertEquals(controlled.board, GameplayHistory.reverse(next, copied.mutation!!))
        assertEquals(copied, GameplayHistory.replay(controlled, copied.mutation))
        assertEquals("missingReference", change(next, PlayAction.Stack(StackAction.SetController(copy.id, id(999))), 8003).reason)
        assertEquals("missingReference", change(next, PlayAction.Stack(StackAction.SetController(id(20), id(8))), 8004).reason)
        assertEquals("missingReference", change(next, PlayAction.Stack(StackAction.CopyEffect(id(20), CombatStackRules.stackDigest(next))), 8005).reason)
        assertEquals("stackChanged", change(next, PlayAction.Stack(StackAction.CopyEffect(original.id, CombatStackRules.stackDigest(controlled))), 8006).reason)
    }

    @Test fun realStackCardsAndFrozenEffectsCanBeCopiedWithoutMovingOrRevivingSources() {
        val base = state()
        val real = apply(base, PlayAction.MoveCard(id(20), MoveSource(id(10), base.board.cards.first().placement),
            MoveDestination(id(12), MoveInsertion.TOP), CombatStackRules.stackDigest(base)), 8100)
        val copied = apply(real, PlayAction.Stack(StackAction.CopyEffect(id(20), CombatStackRules.stackDigest(real))), 8101)
        assertEquals(real.board.cards, copied.board.cards)
        assertTrue(copied.board.stackEffects.single().isCopy)
        val projected = effect(state(), 25, 8102)
        val frozen = apply(projected, PlayAction.RemoveToken(id(25)), 8103)
        val entry = frozen.board.stackEffects.single()
        val second = apply(frozen, PlayAction.Stack(StackAction.CopyEffect(entry.id, CombatStackRules.stackDigest(frozen))), 8104)
        assertEquals(entry.frozen, second.board.stackEffects.last().frozen)
        assertEquals(frozen.board.cards, second.board.cards)
    }

    @Test fun pairedSlotsRoleChangeAndSourceUniqueness() {
        val original = open()
        var state = declare(original, 20)
        val areaId = state.board.combat!!.id
        val pairId = state.board.combat.pairs.single().id
        val occurrence = state.board.combat.pairs.single().attackers.single()
        state = place(state, 21, pairId, CombatRole.BLOCKER, 1101)
        state = place(state, 22, pairId, CombatRole.ATTACKER, 1102)
        state = place(state, 20, pairId, CombatRole.BLOCKER, 1103)
        assertEquals(listOf(id(22)), state.board.combat!!.pairs.single().attackers.map { it.sourceId })
        assertEquals(listOf(id(21), id(20)), state.board.combat.pairs.single().blockers.map { it.sourceId })
        assertEquals(occurrence.id, state.board.combat.pairs.single().blockers.last().id)
        assertEquals(original.board.cards, state.board.cards)
        assertEquals(3, CombatStackRules.occurrences(state.board.combat).map { it.sourceId }.distinct().size)
        assertEquals(areaId, state.board.combat.id)
        val repeated = declare(state, 22)
        assertEquals(state, repeated)
    }

    @Test fun blockerConversionAppendsAndRetainsSurvivingGroup() {
        var state = declare(open(), 20)
        val first = state.board.combat!!.pairs.single().id
        state = place(state, 21, first, CombatRole.BLOCKER, 1110)
        val blocker = state.board.combat!!.pairs.single().blockers.single()
        state = declare(state, 22)
        val prior = state.board.combat!!.pairs.map { it.id }
        state = declare(state, 21)
        assertEquals(prior, state.board.combat!!.pairs.dropLast(1).map { it.id })
        assertEquals(blocker, state.board.combat.pairs.last().attackers.single())
        assertEquals(listOf(id(20)), state.board.combat.pairs.first().attackers.map { it.sourceId })
        assertTrue(state.board.combat.pairs.first().blockers.isEmpty())
    }

    @Test fun pairInsertionWithinSideInsertionAndEmptyPairCleanup() {
        var state = declare(declare(open(), 20), 21)
        val first = state.board.combat!!.pairs.first().id
        val second = state.board.combat.pairs.last().id
        state = place(state, 22, first, CombatRole.BLOCKER, 1120)
        val survivor = state.board.combat!!.pairs.first().blockers.single()
        val oldAttacker = state.board.combat.pairs.first().attackers.single()
        state = place(state, 20, null, CombatRole.BLOCKER, 1121, IdentityGap(first, second))
        assertEquals(listOf(first, CommandIds.uuidV5(id(1121), "combat-pair/0"), second), state.board.combat!!.pairs.map { it.id })
        assertEquals(listOf(survivor), state.board.combat.pairs.first().blockers)
        assertTrue(state.board.combat.pairs.first().attackers.isEmpty())
        assertEquals(oldAttacker, state.board.combat.pairs[1].blockers.single())
        state = place(state, 23, first, CombatRole.BLOCKER, 1122, insertion = CombatInsertion.GAP,
            cardGap = IdentityGap(null, survivor.id))
        assertEquals(listOf(id(23), id(22)), state.board.combat!!.pairs.first().blockers.map { it.sourceId })
        for (occurrence in state.board.combat.pairs.first().blockers.toList()) state = apply(state,
            PlayAction.Combat(CombatAction.Remove(state.board.combat!!.id, occurrence.id)), 1200 + occurrence.sourceId.takeLast(2).toInt())
        assertEquals(2, state.board.combat!!.pairs.size)
        assertTrue(state.board.combat.pairs.none { it.id == first })
    }

    @Test fun occurrenceRemovalAndClosureCleanLinksButNeverSourcesOrSiblingEffects() {
        var state = effect(effect(declare(open(), 20), 20, 1130), 20, 1131)
        val combat = CombatStackRules.occurrences(state.board.combat).single()
        val effects = state.board.stackEffects
        val cross = ProjectionLink(id(300), combat.id, effects[0].id)
        val sibling = ProjectionLink(id(301), effects[0].id, effects[1].id)
        state = state.copy(board = state.board.copy(projectionLinks = listOf(cross, sibling)))
        val closed = change(state, PlayAction.Combat(CombatAction.Close(state.board.combat!!.id)), 1132)
        assertEquals(listOf(cross), closed.mutation!!.transition.combatStack!!.removedLinks)
        assertEquals(listOf(sibling), closed.state!!.board.projectionLinks)
        assertEquals(state.board.cards, closed.state.board.cards)
        assertEquals(effects, closed.state.board.stackEffects)
        assertEquals(closed, GameplayHistory.replay(state, closed.mutation))
        val removed = change(closed.state, PlayAction.Stack(StackAction.RemoveEffect(effects[0].id)), 1133)
        assertEquals(listOf(effects[1]), removed.state!!.board.stackEffects)
        assertTrue(removed.state.board.projectionLinks.isEmpty())
        assertEquals(state.board.cards, removed.state.board.cards)
        assertEquals(listOf(sibling), removed.mutation!!.transition.combatStack!!.removedLinks)
    }

    @Test fun capturedCloseNeverClosesReplacementAndAbsentAreaIsNoOp() {
        val first = open()
        val old = first.board.combat!!.id
        val closed = apply(first, PlayAction.Combat(CombatAction.Close(old)), 1140)
        val replacement = apply(closed, PlayAction.Combat(CombatAction.Open), 1141)
        assertEquals("inapplicable", change(replacement, PlayAction.Combat(CombatAction.Close(old)), 1142).reason)
        assertEquals("noOp", change(replacement, PlayAction.Combat(CombatAction.Close(null)), 1143).outcome)
        assertEquals("noOp", change(closed, PlayAction.Combat(CombatAction.Close(old)), 1144).outcome)
    }

    @Test fun stackAdmissionGuardIsSemanticAndLaterPredictionUsesPredecessor() {
        val original = state()
        val digest = CombatStackRules.stackDigest(original)
        val first = effect(original, 20, 1150)
        assertEquals("stackChanged", change(first, PlayAction.Stack(StackAction.AddEffect(id(21), digest)), 1151).reason)
        val second = effect(first, 20, 1152)
        assertEquals(2, second.board.stackEffects.size)
        assertEquals(second.board.stackEffects.map { it.id }.reversed(), CombatStackRules.stack(second.board).cards)
        val edited = second.copy(board = second.board.copy(cards = second.board.cards.map {
            it.copy(corner = "99/99", covered = true, tapped = true, faceId = id(999), owner = id(8),
                note = "changed", counters = mapOf(CounterColor.RED to "3"), placement = null)
        }))
        assertEquals(CombatStackRules.stackDigest(second), CombatStackRules.stackDigest(edited))
        val added = change(edited, PlayAction.Stack(StackAction.AddEffect(id(21), CombatStackRules.stackDigest(second))), 1153)
        assertEquals("accepted", added.outcome)
        assertNotEquals(CombatStackRules.stackDigest(second), CombatStackRules.stackDigest(id(99), second.board))
        assertEquals(second.board.cards, original.board.cards)
    }

    @Test fun distinctEffectsNoCapacityAndReadThroughSource() {
        var state = state()
        repeat(27) { state = effect(state, 20, 2000 + it) }
        assertEquals(27, CombatStackRules.identities(state.board).distinct().size)
        val view = state.board.stackEffects[2].id
        val source = CombatStackRules.liveSource(state.board, view)!!
        state = apply(state, PlayAction.SetTapped(source.id, true), 1170)
        assertTrue(state.board.stackEffects.all { CombatStackRules.inspection(state.board, it.id)!!.tapped })
        assertEquals(28, CombatStackRules.relatedViews(state.board, view).size)
        assertTrue(state.board.stackEffects.all { it.frozen == null })
        assertEquals(6, state.board.cards.size)
    }

    @Test fun legacyReorderEventRoundTripsReplaysAndUnappliesExactly() {
        val initial = effect(effect(state(), 20, 8170), 21, 8171)
        val order = CombatStackRules.stack(initial.board).cards
        // The retained reducer constructs the same event as the retired command did.
        val legacy = change(initial, PlayAction.Stack(StackAction.ReorderEffect(order[1],
            IdentityGap(order[0], null), IdentityGap(null, order[0]))), 8172)
        val retained = GameWire.json.decodeFromString<GameplayMutation>(
            GameWire.json.encodeToString(GameplayMutation.serializer(), legacy.mutation!!))
        assertEquals(legacy.mutation, retained)
        val replayed = GameplayHistory.replay(initial, retained)
        assertEquals(legacy, replayed)
        assertEquals(order.reversed(), CombatStackRules.stack(replayed.state!!.board).cards)
        assertEquals(initial, GameplayHistory.unapply(replayed.state, retained))
        assertEquals(initial.board, GameplayHistory.reverse(replayed.state, retained))
        assertEquals(replayed.state, GameplayHistory.apply(initial, retained))
    }

    @Test fun reorderAndRemovalInvalidateStaleAdditionWithoutComparingOnlyTop() {
        val state = effect(effect(effect(state(), 20, 1180), 20, 1181), 20, 1182)
        val ids = CombatStackRules.stack(state.board).cards
        val reordered = apply(state, PlayAction.Stack(StackAction.ReorderEffect(ids[2], IdentityGap(ids[1], null),
            IdentityGap(ids[0], ids[1]))), 1183)
        assertEquals(listOf(ids[0], ids[2], ids[1]), CombatStackRules.stack(reordered.board).cards)
        assertEquals("stackChanged", change(reordered,
            PlayAction.Stack(StackAction.AddEffect(id(21), CombatStackRules.stackDigest(state))), 1184).reason)
        val removed = apply(state, PlayAction.Stack(StackAction.RemoveEffect(ids[1])), 1185)
        assertEquals("stackChanged", change(removed,
            PlayAction.Stack(StackAction.AddEffect(id(21), CombatStackRules.stackDigest(state))), 1186).reason)
    }

    /** Real movement/token command integration belongs to I13/I19/I21b. Exercise the production
     * shared record hook with the exact candidate board those reducers must submit, not a fake UI move.
     */
    private fun recordCandidate(state: PlayState, candidate: BoardState, request: Int): GameplayChange =
        GameplayRules.record(state, id(8), command(PlayAction.SetTapped(id(20), true), request), candidate, GameplayTransition())

    @Test fun sourceDepartureHookRetainsLiveEffectsAndBlockerOnlyPair() {
        var state = declare(open(), 20)
        state = place(state, 21, state.board.combat!!.pairs.single().id, CombatRole.BLOCKER, 1190)
        state = effect(state, 20, 1191)
        val source = state.board.cards.single { it.id == id(20) }
        val moved = state.board.copy(cards = state.board.cards.map {
            if (it.id == source.id) it.copy(containerId = id(13), corner = "", counters = emptyMap(), placement = null) else it
        }, containers = state.board.containers.map { when (it.id) {
            id(10) -> it.copy(cards = it.cards - source.id)
            id(13) -> it.copy(cards = it.cards + source.id)
            else -> it
        } })
        val result = recordCandidate(state, moved, 1192)
        assertEquals("accepted", result.outcome)
        assertTrue(result.state!!.board.combat!!.pairs.single().attackers.isEmpty())
        assertEquals(id(21), result.state.board.combat.pairs.single().blockers.single().sourceId)
        assertNull(result.state.board.stackEffects.single().frozen)
        assertEquals(id(13), CombatStackRules.inspection(result.state.board, result.state.board.stackEffects.single().id)!!.containerId)
        assertEquals(CombatStackRules.stackDigest(state), CombatStackRules.stackDigest(result.state))
        val fromGraveyard = effect(result.state, 20, 1193)
        assertEquals(2, fromGraveyard.board.stackEffects.size)
        val exile = fromGraveyard.copy(board = fromGraveyard.board.copy(cards = fromGraveyard.board.cards.map {
            if (it.id == id(20)) it.copy(containerId = id(14)) else it
        }))
        assertEquals(3, effect(exile, 20, 1194).board.stackEffects.size)
    }

    @Test fun disappearedTokenFreezesExactLastLiveStateBeforeResetsAndNeverResurrects() {
        var state = effect(effect(declare(open(), 25), 25, 1210), 25, 1211)
        val token = state.board.cards.single { it.id == id(25) }.copy(corner = "5/2", covered = true,
            tapped = true, note = "edited\nnote", counters = mapOf(CounterColor.PURPLE to "2"), owner = id(8))
        state = state.copy(board = state.board.copy(cards = state.board.cards.map { if (it.id == token.id) token else it }))
        val effects = state.board.stackEffects
        val combat = CombatStackRules.occurrences(state.board.combat).single()
        val cross = ProjectionLink(id(310), combat.id, effects[0].id)
        val sibling = ProjectionLink(id(311), effects[0].id, effects[1].id)
        state = state.copy(board = state.board.copy(projectionLinks = listOf(cross, sibling)))
        val candidate = state.board.copy(cards = state.board.cards.filterNot { it.id == token.id },
            containers = state.board.containers.map { it.copy(cards = it.cards - token.id) })
        val result = recordCandidate(state, candidate, 1212)
        assertEquals("accepted", result.outcome)
        val board = result.state!!.board
        assertNotNull(board.combat)
        assertTrue(board.combat.pairs.isEmpty())
        assertEquals(listOf(sibling), board.projectionLinks)
        assertEquals(listOf(token, token), board.stackEffects.map { it.frozen })
        assertNull(CombatStackRules.liveSource(board, effects[0].id))
        assertEquals(token, CombatStackRules.inspection(board, effects[0].id))
        assertEquals(CombatStackRules.stackDigest(state), CombatStackRules.stackDigest(result.state))
        assertEquals(listOf(cross), result.mutation!!.transition.combatStack!!.removedLinks)
        assertEquals(2, result.mutation.transition.combatStack.effects.size)
        val order = CombatStackRules.stack(board).cards
        val reordered = apply(result.state, PlayAction.Stack(StackAction.ReorderEffect(order[1],
            IdentityGap(order[0], null), IdentityGap(null, order[0]))), 1215)
        assertEquals(order.reversed(), CombatStackRules.stack(reordered.board).cards)
        assertEquals(listOf(token, token), reordered.board.stackEffects.map { it.frozen })
        val removed = apply(result.state, PlayAction.Stack(StackAction.RemoveEffect(effects[0].id)), 1213)
        assertEquals(token, removed.board.stackEffects.single().frozen)
        assertTrue(removed.board.cards.none { it.id == token.id })
        assertEquals("missingReference", change(removed, PlayAction.SetTapped(token.id, false), 1214).reason)
    }

    @Test fun exactRelevantTuplesPermitUnrelatedFieldsButBlockChangedPlacementAndCleanup() {
        val initial = declare(declare(open(), 20), 21)
        val area = initial.board.combat!!
        val moved = CombatStackRules.occurrences(area).first { it.sourceId == id(20) }
        val action = PlayAction.Combat(CombatAction.Place(id(20), CombatDestination(area.id, CombatRole.BLOCKER,
            area.pairs.last().id), CombatStackRules.location(area, moved.id)))
        val result = change(initial, action, 1220)
        val relevant = result.mutation!!.transition.combatStack!!
        assertEquals(listOf(moved.id), relevant.occurrences.map { it.id })
        assertEquals(listOf(area.pairs.first().id), relevant.pairs.map { it.id })
        val changedField = initial.copy(board = initial.board.copy(cards = initial.board.cards.map { it.copy(note = "unrelated") }))
        assertNull(GameplayHistory.replay(changedField, result.mutation).reason)
        val addedBlocker = place(initial, 22, area.pairs.first().id, CombatRole.BLOCKER, 1221)
        assertEquals("replayBlocked", GameplayHistory.replay(addedBlocker, result.mutation).reason)
        assertEquals("invalidStructure", recordCandidate(initial,
            initial.board.copy(cards = initial.board.cards.filterNot { it.id == id(20) }), 1222).reason)
    }
}
