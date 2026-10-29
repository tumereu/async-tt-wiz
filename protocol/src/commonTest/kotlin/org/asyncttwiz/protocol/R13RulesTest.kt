package org.asyncttwiz.protocol

import kotlin.test.*

class R13RulesTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private val seed = RandomV1.encodeSeed(ByteArray(32))
    private var request = 100
    private fun root(): PlayState {
        val definition = CardDefinition("Front", listOf(DefinitionFace(id(40), "Front", corner = "2/3"),
            DefinitionFace(id(41), "Back", corner = "4/5")))
        val zones = PlayZone.entries.mapIndexed { i, zone -> PlayContainer(id(10 + i), zone,
            a.takeUnless { zone == PlayZone.STACK }, when (zone) {
                PlayZone.LIBRARY -> (21..29).map(::id)
                PlayZone.HAND -> listOf(id(30))
                PlayZone.BATTLEFIELD -> listOf(id(20))
                else -> emptyList()
            }) }
        val cards = (20..30).map { n ->
            val container = zones.single { id(n) in it.cards }
            PlayCard(id(n), "definition", a, container.id, id(40), covered = container.zone != PlayZone.BATTLEFIELD,
                corner = "2/3", placement = if (n == 20) PlayPlacement(BoardPosition(0, 0), id(50)) else null)
        }
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), cards, zones, TurnState(a)),
            listOf(SubmittedDefinition("definition", definition)), listOf(InitialShuffle(a, seed, cards.map { it.id })),
            InitialStarter(StarterChoice(a), a))
    }
    private fun container(s: PlayState, zone: PlayZone) = s.board.containers.single { it.zone == zone }
    private fun command(s: PlayState, action: GameAction) = GameCommand(id(request++), GameScope(s.gameId, playId = s.id), action, id(9), "1")
    private fun change(s: PlayState, action: PlayAction) = GameplayRules.change(s, a, command(s, GameAction.Play(action)))
    private fun exact(s: PlayState, action: PlayAction): GameplayChange {
        val changed = change(s, action)
        val event = assertNotNull(changed.mutation, changed.reason)
        val next = assertNotNull(changed.state)
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(GameWire.json.encodeToString(event))
        assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, event.transition.rulesVersion)
        assertEquals(next, GameplayHistory.apply(s, decoded))
        assertEquals(s, GameplayHistory.unapply(next, decoded))
        assertEquals(next, GameplayHistory.replay(s, decoded).state)
        return changed
    }
    private fun move(s: PlayState, zone: PlayZone, cardId: String = id(20)): PlayAction.MoveCard {
        val card = s.board.cards.single { it.id == cardId }
        val target = container(s, zone)
        return PlayAction.MoveCard(cardId, MoveSource(card.containerId, card.placement), MoveDestination(target.id,
            when (zone) { PlayZone.LIBRARY, PlayZone.STACK -> MoveInsertion.TOP
                PlayZone.BATTLEFIELD -> MoveInsertion.EMPTY
                else -> MoveInsertion.UNORDERED }, position = if (zone == PlayZone.BATTLEFIELD) BoardPosition(3, 4) else null),
            expectedStackDigest = if (zone == PlayZone.STACK) GameplayRules.stackDigest(s) else null)
    }

    @Test fun battlefieldLeaveResetsEveryModificationAndIncidentLinkInTheMove() {
        for (zone in PlayZone.entries.filter { it != PlayZone.BATTLEFIELD }) {
            val initial = root().let { s -> s.copy(board = s.board.copy(cards = s.board.cards.map { card ->
                if (card.id != id(20)) card else card.copy(owner = b, faceId = id(41), corner = "9/9", tapped = true,
                    counters = CounterColor.entries.associateWith { "3" }, note = "target", covered = true, explicitlyCovered = true)
            }, links = listOf(PlayLink(id(60), LinkEndpoint(LinkEndpointKind.CARD, id(20)), LinkEndpoint(LinkEndpointKind.PLAYER, b))))) }
            val action = move(initial, zone)
            assertEquals(listOf(action), GameplayRules.moveActions(initial, action))
            val result = exact(initial, action)
            val card = result.state!!.board.cards.single { it.id == id(20) }
            assertEquals("", card.note)
            assertEquals("2/3", card.corner)
            assertTrue(card.counters.isEmpty())
            assertEquals(id(40), card.faceId)
            assertEquals(a, card.owner)
            assertFalse(card.tapped)
            assertEquals(zone == PlayZone.EXILE, card.isFaceDown)
            assertTrue(result.state.board.links.isEmpty())
            assertEquals(1, result.mutation!!.transition.links.size)
            assertEquals(6, result.mutation.transition.fields.count { it.field == GameplayField.COUNTER })
        }
    }

    @Test fun noteClearsOnStackExitButBattlefieldPlacementPreservesIt() {
        var s = root()
        s = exact(s, PlayAction.SetNote(id(20), "battlefield")).state!!
        s = exact(s, move(s, PlayZone.BATTLEFIELD)).state!!
        assertEquals("battlefield", s.board.cards.single { it.id == id(20) }.note)
        s = exact(s, move(s, PlayZone.STACK)).state!!
        assertEquals("", s.board.cards.single { it.id == id(20) }.note)
        s = exact(s, PlayAction.SetNote(id(20), "X=3")).state!!
        s = exact(s, move(s, PlayZone.GRAVEYARD)).state!!
        assertEquals("", s.board.cards.single { it.id == id(20) }.note)
    }

    @Test fun manualEditsAreZoneRestrictedAndStackNotesRemainAllowed() {
        for (zone in PlayZone.entries.filter { it != PlayZone.BATTLEFIELD }) {
            val s = exact(root(), move(root(), zone)).state!!
            for (edit in listOf(PlayAction.SetCorner(id(20), "4/4"), PlayAction.AdjustCorner(id(20), CornerPart.BOTH, 1),
                PlayAction.ResetCorner(id(20)), PlayAction.SetCounter(CounterTarget.CARD, id(20), CounterColor.RED, "2"),
                PlayAction.AdjustCounter(CounterTarget.CARD, id(20), CounterColor.BLUE, "1"),
                PlayAction.ClearCardCounter(id(20), CounterColor.GRAY), PlayAction.SetOwner(id(20), b)))
                assertEquals("inapplicable", change(s, edit).reason, "$zone $edit")
            if (zone == PlayZone.STACK) exact(s, PlayAction.SetNote(id(20), "target"))
            else assertEquals("inapplicable", change(s, PlayAction.SetNote(id(20), "target")).reason)
        }
    }

    @Test fun projectionsHaveIndependentNotesAndRemovalRetainsAnExactInverse() {
        var s = exact(root(), PlayAction.SetNote(id(20), "source")).state!!
        s = exact(s, PlayAction.Stack(StackAction.AddEffect(id(20), GameplayRules.stackDigest(s)))).state!!
        s = exact(s, PlayAction.Combat(CombatAction.Open)).state!!
        s = exact(s, PlayAction.Combat(CombatAction.Declare(s.board.combat!!.id, id(20)))).state!!
        val effect = s.board.stackEffects.single().id
        val occurrence = CombatStackRules.occurrences(s.board.combat).single().id
        for (id in listOf(effect, occurrence)) {
            s = exact(s, PlayAction.SetProjectionNote(id, "own $id")).state!!
            assertEquals("own $id", CombatStackRules.projectionNote(s.board, id))
            assertEquals("source", s.board.cards.single { it.id == this.id(20) }.note)
        }
        val before = s
        s = exact(s, PlayAction.Stack(StackAction.RemoveEffect(effect))).state!!
        assertNull(CombatStackRules.projectionNote(s.board, effect))
        s = exact(s, PlayAction.Combat(CombatAction.Remove(s.board.combat!!.id, occurrence))).state!!
        assertNull(CombatStackRules.projectionNote(s.board, occurrence))
        assertEquals("own $effect", before.board.stackEffects.single().note)
        assertEquals("missingReference", change(s, PlayAction.SetProjectionNote(effect, "stale")).reason)
    }

    @Test fun projectionNoteDoesNotDependOnSourceNoteAndSupportsSelectiveUndoRedo() {
        val initial = root()
        var tree = assertNotNull(GameplayTree(initial).play(a, command(initial, GameAction.Play(
            PlayAction.Stack(StackAction.AddEffect(id(20), GameplayRules.stackDigest(initial)))))).tree)
        val s = assertNotNull(tree.stateAt(tree.currentNodeId))
        val effect = s.board.stackEffects.single().id
        tree = assertNotNull(tree.play(a, command(s, GameAction.Play(PlayAction.SetNote(id(20), "source")))).tree)
        tree = assertNotNull(tree.play(b, command(s, GameAction.Play(PlayAction.SetProjectionNote(effect, "projection")))).tree)
        val undone = assertNotNull(SelectiveUndo.change(tree, a, command(s, GameAction.Undo)).tree)
        val state = assertNotNull(undone.stateAt(undone.currentNodeId))
        assertEquals("", state.board.cards.single { it.id == id(20) }.note)
        assertEquals("projection", CombatStackRules.projectionNote(state.board, effect))
        val redone = assertNotNull(SelectiveUndo.change(undone, a, command(s, GameAction.Redo)).tree)
        assertEquals(tree.stateAt(tree.currentNodeId), redone.stateAt(redone.currentNodeId))
    }

    @Test fun opponentProjectionNoteDoesNotBlockUndoingTheEffectsControllerChange() {
        val initial = root()
        var tree = assertNotNull(GameplayTree(initial).play(a, command(initial, GameAction.Play(
            PlayAction.Stack(StackAction.AddEffect(id(20), GameplayRules.stackDigest(initial)))))).tree)
        val s = assertNotNull(tree.stateAt(tree.currentNodeId))
        val effect = s.board.stackEffects.single().id
        tree = assertNotNull(tree.play(a, command(s, GameAction.Play(
            PlayAction.Stack(StackAction.SetController(effect, b))))).tree)
        tree = assertNotNull(tree.play(b, command(s, GameAction.Play(PlayAction.SetProjectionNote(effect, "B's target")))).tree)
        val note = tree.nodes.getValue(tree.currentNodeId)
        val undo = SelectiveUndo.change(tree, a, command(s, GameAction.Undo))
        val undone = assertNotNull(undo.tree, undo.reason)
        val state = assertNotNull(undone.stateAt(undone.currentNodeId))
        assertEquals(a, CombatStackRules.controller(state.board, effect))
        assertEquals("B's target", CombatStackRules.projectionNote(state.board, effect))
        assertEquals(note.action, undone.nodes.getValue(note.id).action)
        assertEquals(note.transition, undone.nodes.getValue(note.id).transition)
        val redo = SelectiveUndo.change(undone, a, command(s, GameAction.Redo))
        val redone = assertNotNull(redo.tree, redo.reason)
        assertEquals(tree.stateAt(tree.currentNodeId), redone.stateAt(redone.currentNodeId))
    }

    @Test fun projectionNoteConcernsNeitherCombatPairNorRoleWhenReplayingAfterPlacementUndo() {
        val initial = root()
        var tree = assertNotNull(GameplayTree(initial).play(a, command(initial,
            GameAction.Play(PlayAction.Combat(CombatAction.Open)))).tree)
        var s = assertNotNull(tree.stateAt(tree.currentNodeId))
        tree = assertNotNull(tree.play(a, command(s,
            GameAction.Play(PlayAction.Combat(CombatAction.Declare(s.board.combat!!.id, id(20)))))).tree)
        s = assertNotNull(tree.stateAt(tree.currentNodeId))
        val area = s.board.combat!!
        val occurrence = area.pairs.single().attackers.single().id
        tree = assertNotNull(tree.play(a, command(s, GameAction.Play(PlayAction.Combat(
            CombatAction.Place(id(20), CombatDestination(area.id, CombatRole.BLOCKER, area.pairs.single().id),
                CombatStackRules.location(area, occurrence)))))).tree)
        tree = assertNotNull(tree.play(b, command(s, GameAction.Play(PlayAction.SetProjectionNote(occurrence, "combat target")))).tree)
        val note = tree.nodes.getValue(tree.currentNodeId)
        val undo = SelectiveUndo.change(tree, a, command(s, GameAction.Undo))
        val undone = assertNotNull(undo.tree, undo.reason)
        val state = assertNotNull(undone.stateAt(undone.currentNodeId))
        assertEquals(area.pairs.single().id, CombatStackRules.location(state.board.combat!!, occurrence)?.pairId)
        assertEquals(CombatRole.ATTACKER, CombatStackRules.location(state.board.combat, occurrence)?.role)
        assertEquals("combat target", CombatStackRules.projectionNote(state.board, occurrence))
        assertEquals(note.transition, undone.nodes.getValue(note.id).transition)
        val redo = SelectiveUndo.change(undone, a, command(s, GameAction.Redo))
        val redone = assertNotNull(redo.tree, redo.reason)
        assertEquals(tree.stateAt(tree.currentNodeId), redone.stateAt(redone.currentNodeId))
    }

    @Test fun mulliganIsOneDeterministicRecordedEventWithAtomicUndoRedoAndStaleHandRejection() {
        val s = root()
        val library = container(s, PlayZone.LIBRARY)
        val hand = container(s, PlayZone.HAND)
        val action = PlayAction.Mulligan(library.id, hand.id, hand.cards, 7, seed)
        val result = exact(s, action)
        val order = RandomV1.shuffle(hand.cards + library.cards, seed)
        assertEquals(order.take(7), container(result.state!!, PlayZone.HAND).cards)
        assertEquals(order.drop(7), container(result.state, PlayZone.LIBRARY).cards)
        // The same seed gives the same cards (new layer IDs come from the request).
        assertEquals(result.state.board.copy(containers = result.state.board.containers.map { it.copy(layers = emptyList()) }),
            change(s, action).state!!.board.let { b -> b.copy(containers = b.containers.map { it.copy(layers = emptyList()) }) })
        assertEquals("shuffle", result.state.currentGroup!!.representative!!.semantic.icon)
        assertEquals("movementConflict", change(s, action.copy(displayedHand = emptyList())).reason)
        assertEquals("invalidInput", change(s, action.copy(displayedHand = hand.cards + hand.cards)).reason)
        assertEquals("invalidInput", change(s, action.copy(drawCount = -1)).reason)
        val tree = assertNotNull(GameplayTree(s).play(a, command(s, GameAction.Play(action))).tree)
        assertEquals(1, tree.nodes.size)
        val undone = assertNotNull(SelectiveUndo.change(tree, a, command(s, GameAction.Undo)).tree)
        assertEquals(s.board, undone.stateAt(undone.currentNodeId)!!.board)
        val redone = assertNotNull(SelectiveUndo.change(undone, a, command(s, GameAction.Redo)).tree)
        assertEquals(tree.nodes, redone.nodes)
        assertEquals(tree.stateAt(tree.currentNodeId), redone.stateAt(redone.currentNodeId))
    }

    @Test fun untapsOnlyJoinUntapsAndBackwardPingActionsWhileShuffleJoinsTheImmediatelyPriorLibraryEntry() {
        var s = root()
        s = exact(s, PlayAction.SetTapped(id(20), true)).state!!
        val tapped = s.currentGroup!!.id
        s = exact(s, PlayAction.SetTapped(id(20), false)).state!!
        val untapped = s.currentGroup!!.id
        assertNotEquals(tapped, untapped)
        assertEquals("untap", s.currentGroup.category)
        s = exact(s, PlayAction.PassAttention).state!!
        assertEquals(untapped, s.currentGroup!!.id)
        s = exact(s, PlayAction.SetTapped(id(20), true)).state!!
        assertNotEquals(untapped, s.currentGroup!!.id)
        s = exact(s, PlayAction.SetTapped(id(20), false)).state!!
        val untap = s.currentGroup!!.id
        s = exact(s, PlayAction.SetNote(id(20), "separate")).state!!
        assertNotEquals(untap, s.currentGroup!!.id)
        s = exact(s, move(s, PlayZone.LIBRARY)).state!!
        val moved = s.currentGroup!!.id
        s = exact(s, PlayAction.ShuffleLibrary(container(s, PlayZone.LIBRARY).id, seed)).state!!
        assertEquals(moved, s.currentGroup!!.id)
        var other = exact(root(), move(root(), PlayZone.LIBRARY)).state!!
        val oldGroup = other.currentGroup!!.id
        other = exact(other, PlayAction.PassAttention).state!!
        other = exact(other, PlayAction.ShuffleLibrary(container(other, PlayZone.LIBRARY).id, seed)).state!!
        assertNotEquals(oldGroup, other.currentGroup!!.id)
        val untapSemantic = HistoryGrouping.classify(PlayAction.SetTapped(id(20), false), transition = GameplayTransition(rulesVersion = 13))
        val group = HistoryGrouping.append(null, id(80), id(81), a, untapSemantic)
        assertEquals(group.id, HistoryGrouping.append(group, id(82), id(83), a, untapSemantic).id)
        assertNotEquals(group.id, HistoryGrouping.append(group, id(82), id(83), b, untapSemantic).id)
    }

    @Test fun legacySealedMovesAndOffBattlefieldNotesReplayWithoutNewDefaultsOrGrouping() {
        val s = root()
        val card = s.board.cards.single { it.id == id(30) }
        val command = command(s, GameAction.Play(PlayAction.SetNote(card.id, "legacy hand note")))
        val next = s.copy(board = s.board.copy(cards = s.board.cards.map { if (it.id == card.id) it.copy(note = "legacy hand note") else it }))
        val raw = GameplayMutation(CommandIds.uuidV5(command.requestId, "board-node"), s.currentNodeId, s.currentBranchId,
            command.requestId, a, null, (command.action as GameAction.Play).action,
            GameplayTransition(fields = listOf(GameplayFieldChange(card.id, GameplayField.NOTE, "", "legacy hand note"))), schemaVersion = 2)
        val delta = GameplayDeltas.capture(s, next, raw.id)
        val recorded = GameplayHistory.recontextualize(s, raw.copy(transition = raw.transition.copy(delta = delta)))
        val legacy = recorded.copy(transition = recorded.transition.copy(delta = delta.copy(seal = GameplayDeltas.seal(recorded))))
        val bytes = GameWire.json.encodeToString(legacy)
        assertFalse("rulesVersion" in bytes)
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(bytes)
        val applied = assertNotNull(GameplayHistory.apply(s, decoded))
        assertEquals("legacy hand note", applied.board.cards.single { it.id == card.id }.note)
        assertEquals(s, GameplayHistory.unapply(applied, decoded))
        assertEquals(bytes, GameWire.json.encodeToString(decoded))
        val oldUntap = HistoryGrouping.classify(PlayAction.SetTapped(id(20), false), transition = GameplayTransition())
        assertEquals(HistoryCategory.T, oldUntap.category)
        assertEquals(1, oldUntap.rulesVersion)
    }

    @Test fun legacyMoveKeepsItsRecordedModificationsAndUntapsKeepTheirOldGroup() {
        val s = root().let { state -> state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id == id(20)) it.copy(note = "old note", counters = mapOf(CounterColor.BLUE to "2"), corner = "9/9") else it })) }
        val action = move(s, PlayZone.GRAVEYARD)
        val input = command(s, GameAction.Play(action))
        val next = s.copy(board = s.board.copy(cards = s.board.cards.map { if (it.id == id(20))
            it.copy(containerId = action.destination.containerId, placement = null) else it },
            containers = s.board.containers.map { when (it.id) {
                action.source.containerId -> it.copy(cards = it.cards - id(20))
                action.destination.containerId -> it.copy(cards = listOf(id(20)))
                else -> it
            } }))
        val transition = GameplayTransition(movement = GameplayMoveChange(id(20), GameplayRules.location(s.board, id(20), true),
            GameplayRules.location(next.board, id(20), true), coverRulesVersion = 6))
        val raw = GameplayMutation(CommandIds.uuidV5(input.requestId, "board-node"), s.currentNodeId, s.currentBranchId,
            input.requestId, a, null, action, transition, schemaVersion = 2,
            grouping = HistoryGrouping.classify(action, s.board.containers, transition))
        val delta = GameplayDeltas.capture(s, next, raw.id)
        val recorded = GameplayHistory.recontextualize(s, raw.copy(transition = transition.copy(delta = delta)))
        val sealed = recorded.copy(transition = recorded.transition.copy(delta = delta.copy(seal = GameplayDeltas.seal(recorded))))
        val bytes = GameWire.json.encodeToString(sealed)
        assertFalse("rulesVersion" in bytes)
        val event = GameWire.json.decodeFromString<GameplayMutation>(bytes)
        val applied = assertNotNull(GameplayHistory.replay(s, event).state)
        assertEquals(next.board, applied.board)
        assertEquals(s, GameplayHistory.unapply(applied, event))
        assertEquals(bytes, GameWire.json.encodeToString(event))
        val tap = HistoryGrouping.classify(PlayAction.SetTapped(id(20), true), transition = GameplayTransition())
        val untap = HistoryGrouping.classify(PlayAction.SetTapped(id(20), false), transition = GameplayTransition())
        val group = HistoryGrouping.append(null, id(80), id(81), a, tap)
        assertEquals(group.id, HistoryGrouping.append(group, id(82), id(83), a, untap).id)
    }
}
