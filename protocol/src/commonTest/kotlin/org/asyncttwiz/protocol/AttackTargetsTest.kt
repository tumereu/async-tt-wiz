package org.asyncttwiz.protocol

import kotlin.test.*

class AttackTargetsTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private var request = 1000
    private fun state(): PlayState {
        val definitions = listOf("Creature", "Legendary Planeswalker — Test", "Battle — Siege", "Enchantment — Saga").mapIndexed { n, type ->
            val definition = CardDefinition("Card $n", listOf(DefinitionFace(id(40 + n), "Card $n", typeLine = type,
                loyalty = if (n == 1) "4" else "", defense = if (n == 2) "7" else "")))
            SubmittedDefinition(DeckJson.digest(definition), definition)
        }
        val cards = definitions.mapIndexed { n, definition -> PlayCard(id(20 + n), definition.digest,
            if (n == 0) id(7) else id(8), if (n == 0) id(10) else id(11), id(40 + n), covered = false,
            placement = PlayPlacement(BoardPosition(n, 0), id(60 + n))) }
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5), BoardState(
            listOf(PlayPlayer(id(7)), PlayPlayer(id(8))), cards,
            listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, id(7), listOf(id(20))),
                PlayContainer(id(11), PlayZone.BATTLEFIELD, id(8), cards.drop(1).map { it.id }),
                PlayContainer(id(12), PlayZone.STACK), PlayContainer(id(13), PlayZone.GRAVEYARD, id(8))),
            TurnState(id(7))), definitions, emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    }
    private fun change(state: PlayState, action: PlayAction): GameplayChange {
        val command = GameCommand(id(request++), GameScope(state.gameId, playId = state.id), GameAction.Play(action), id(9), "1")
        assertTrue(GameWire.valid(command))
        return GameplayRules.change(state, id(7), command)
    }
    private fun exact(state: PlayState, action: PlayAction): GameplayChange {
        val result = change(state, action)
        val next = assertNotNull(result.state, result.reason)
        val event = assertNotNull(result.mutation)
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(GameWire.json.encodeToString(event))
        assertEquals(18, event.transition.rulesVersion)
        assertEquals(next, GameplayHistory.apply(state, decoded))
        assertEquals(state, GameplayHistory.unapply(next, decoded))
        assertEquals(next, GameplayHistory.replay(state, decoded).state)
        return result
    }
    private fun declared(): PlayState {
        val opened = exact(state(), PlayAction.Combat(CombatAction.Open)).state!!
        return exact(opened, PlayAction.Combat(CombatAction.Declare(opened.board.combat!!.id, id(20)))).state!!
    }
    private fun retarget(s: PlayState, card: String?) = PlayAction.Combat(CombatAction.SetAttackTarget(
        s.board.combat!!.id, s.board.combat.pairs.single().id, card))

    @Test fun setClearValidateAndReplayTargets() {
        val original = declared()
        assertNull(original.board.combat!!.pairs.single().attackTarget)
        for (target in listOf(id(21), id(22))) {
            val changed = exact(original, retarget(original, target))
            assertEquals(target, changed.state!!.board.combat!!.pairs.single().attackTarget)
            assertEquals(listOf(CombatAttackTargetChange(original.board.combat.pairs.single().id, null, target)),
                changed.mutation!!.transition.combatStack!!.attackTargets)
            assertEquals(original.board, exact(changed.state, retarget(changed.state, null)).state!!.board)
        }
        for (target in listOf(id(20), id(23), id(999))) assertEquals("inapplicable", change(original, retarget(original, target)).reason)
        val own = original.copy(board = original.board.copy(containers = original.board.containers.map {
            if (it.id == id(11)) it.copy(accountId = id(7)) else it }))
        assertEquals("inapplicable", change(own, retarget(own, id(21))).reason)
    }

    @Test fun targetDepartureFallsBackInTheSameMoveAndExactInverseRestoresIt() {
        val original = declared()
        val targeted = exact(original, retarget(original, id(21))).state!!
        val target = targeted.board.cards.single { it.id == id(21) }
        val moved = exact(targeted, PlayAction.MoveCard(target.id, MoveSource(target.containerId, target.placement),
            MoveDestination(id(13), MoveInsertion.UNORDERED)))
        assertNull(moved.state!!.board.combat!!.pairs.single().attackTarget)
        assertEquals(targeted.board.combat!!.pairs.single().attackers, moved.state.board.combat.pairs.single().attackers)
        assertTrue(moved.mutation!!.transition.concerns!!.fields.any { it.field == ConcernField.AttackTargetIncident(id(21)) })
        assertNull(GameplayHistory.apply(original, moved.mutation))
    }

    @Test fun targetConcernIgnoresUnrelatedFieldsButProtectsTargetEligibilityAndPriorTarget() {
        val original = declared()
        val result = exact(original, retarget(original, id(21)))
        val event = result.mutation!!
        val unrelated = original.copy(board = original.board.copy(cards = original.board.cards.map { it.copy(note = "unrelated", corner = "99") }))
        assertNotNull(GameplayHistory.replay(unrelated, event).state)
        val unavailable = original.copy(board = original.board.copy(cards = original.board.cards.map {
            if (it.id == id(21)) it.copy(containerId = id(13), placement = null) else it }))
        assertNull(GameplayHistory.replay(unavailable, event).state)
        val otherTarget = exact(original, retarget(original, id(22))).state!!
        assertNull(GameplayHistory.replay(otherTarget, event).state)
    }

    @Test fun oldPairsKeepTheirWireShapeAndOldEventsReplayWithoutNewDefaults() {
        val pair = CombatPair(id(50), listOf(CombatOccurrence(id(51), id(20))))
        val bytes = GameWire.json.encodeToString(pair)
        assertFalse("attackTarget" in bytes)
        assertNull(GameWire.json.decodeFromString<CombatPair>(bytes).attackTarget)
        val state = declared().let { it.copy(board = it.board.copy(cards = it.board.cards.map { card ->
            if (card.id == id(21)) card.copy(corner = "custom") else card })) }
        val accepted = exact(state, PlayAction.ResetCorner(id(21)))
        val oldNext = accepted.state!!.copy(board = accepted.state.board.copy(cards = accepted.state.board.cards.map {
            if (it.id == id(21)) it.copy(corner = "") else it }))
        val raw = accepted.mutation!!.let { it.copy(transition = GameplayTransition(rulesVersion = 16)) }
        val delta = GameplayDeltas.capture(state, oldNext, raw.id)
        val recorded = GameplayHistory.recontextualize(state, raw.copy(transition = raw.transition.copy(delta = delta)))
        val legacy = recorded.copy(transition = recorded.transition.copy(delta = delta.copy(seal = GameplayDeltas.seal(recorded))))
        assertEquals(oldNext.board, GameplayHistory.replay(state, legacy).state!!.board)
    }

    @Test fun selectiveUndoAllowsIndependentPairTargetsButNotChangedDepartureCleanup() {
        val first = declared()
        val initial = exact(first, PlayAction.Combat(CombatAction.Declare(first.board.combat!!.id, id(22)))).state!!
        val area = initial.board.combat!!
        var tree = GameplayTree(initial.copy(root = PlayRoot(initial.currentNodeId, initial.currentBranchId)))
        fun command(action: GameAction) = GameCommand(id(request++), GameScope(initial.gameId, playId = initial.id), action, id(9), "1")
        fun append(actor: String, action: PlayAction) {
            val change = tree.play(actor, command(GameAction.Play(action)))
            tree = assertNotNull(change.tree, change.reason)
        }
        append(id(7), PlayAction.Combat(CombatAction.SetAttackTarget(area.id, area.pairs.first().id, id(21))))
        val oneTarget = tree
        append(id(8), PlayAction.Combat(CombatAction.SetAttackTarget(area.id, area.pairs.last().id, id(21))))
        val undone = SelectiveUndo.change(tree, id(7), command(GameAction.Undo))
        val nextTree = assertNotNull(undone.tree, undone.reason)
        val pairs = nextTree.stateAt(nextTree.currentNodeId)!!.board.combat!!.pairs
        assertNull(pairs.first().attackTarget)
        assertEquals(id(21), pairs.last().attackTarget)
        tree = oneTarget
        val target = tree.stateAt(tree.currentNodeId)!!.board.cards.single { it.id == id(21) }
        append(id(8), PlayAction.MoveCard(target.id, MoveSource(target.containerId, target.placement),
            MoveDestination(id(13), MoveInsertion.UNORDERED)))
        assertEquals("replayBlocked", SelectiveUndo.change(tree, id(7), command(GameAction.Undo)).reason)
    }

    @Test fun printedCornerDefaultsAndSingleNumberAdjustmentAndLeaveReset() {
        val initial = state()
        assertEquals(listOf("", "4", "7", "1"), initial.definitions.map { it.definition.faces.single().defaultCorner() })
        assertEquals("2/3", DefinitionFace(id(80), "Creature", power = "2", toughness = "3").defaultCorner())
        assertEquals("", DefinitionFace(id(80), "Not a saga", typeLine = "Enchantment", rulesText = "Saga").defaultCorner())
        for ((card, value) in listOf(id(21) to "4", id(22) to "7", id(23) to "1")) {
            val reset = exact(initial, PlayAction.ResetCorner(card)).state!!
            assertEquals(value, reset.board.cards.single { it.id == card }.corner)
            val adjusted = exact(reset, PlayAction.AdjustCorner(card, CornerPart.WHOLE, 1)).state!!
            val source = adjusted.board.cards.single { it.id == card }
            assertEquals((value.toInt() + 1).toString(), source.corner)
            val moved = exact(adjusted, PlayAction.MoveCard(card, MoveSource(source.containerId, source.placement),
                MoveDestination(id(13), MoveInsertion.UNORDERED))).state!!
            assertEquals(value, moved.board.cards.single { it.id == card }.corner)
        }
    }
}
