package org.asyncttwiz.protocol

import kotlin.test.*

/** Owner's state/event-only undo contract, including negative and unchanged witnesses. */
class UndoOwnerRuleTest {
    private val f = UndoFixture
    private val a = f.a
    private val b = f.b
    private var request = 1000
    private fun id(n: Int) = f.id(n)
    private fun root(): PlayState = gameplayFixture().let { state ->
        state.copy(board = state.board.copy(
            cards = state.board.cards.mapIndexed { index, card -> card.copy(containerId = id(22), covered = false,
                placement = PlayPlacement(BoardPosition(index * 2, 0), id(500 + index))) },
            containers = state.board.containers.map { container -> container.copy(cards =
                if (container.id == id(22)) state.board.cards.map { it.id } else emptyList()) } +
                PlayContainer(id(26), PlayZone.GRAVEYARD, b) + PlayContainer(id(27), PlayZone.BATTLEFIELD, b)))
    }
    private fun state(tree: GameplayTree) = assertNotNull(tree.stateAt(tree.currentNodeId))
    private fun play(tree: GameplayTree, actor: String, action: PlayAction, continuation: String? = null): GameplayTree {
        val command = f.command(GameAction.Play(action), request++).copy(
            continuation = continuation?.let { Continuation(it, true) })
        val change = HistoryContinuation.change(tree, actor, command)
        return assertNotNull(change.tree, "$action: ${change.reason}")
    }
    private fun control(tree: GameplayTree, actor: String = a, redo: Boolean = false) =
        HistoryContinuation.change(tree, actor, f.command(if (redo) GameAction.Redo else GameAction.Undo, request++))
    private fun undo(tree: GameplayTree, actor: String = a): GameplayTree =
        control(tree, actor).let { assertNotNull(it.tree, it.reason) }
    private fun move(tree: GameplayTree, actor: String, cardId: String, destination: MoveDestination): GameplayTree {
        val card = state(tree).board.cards.single { it.id == cardId }
        return play(tree, actor, PlayAction.MoveCard(cardId, MoveSource(card.containerId, card.placement), destination))
    }
    private fun grid(x: Int) = MoveDestination(id(22), MoveInsertion.EMPTY, BoardPosition(x, 0))
    private fun handSourceRoot() = root().let { r -> r.copy(board = r.board.copy(
        cards = r.board.cards.map { if (it.id == id(11)) it.copy(containerId = id(21), placement = null, covered = true) else it },
        containers = r.board.containers.map { it.copy(cards = when (it.id) {
            id(22) -> it.cards - id(11); id(21) -> listOf(id(11)); else -> it.cards
        }) })) }
    private fun duplicates(tree: GameplayTree) = state(tree).board.cards.filter { it.containerId == id(22) }
        .groupBy { it.placement!!.position }.values.any { cards -> cards.map { it.placement!!.pileId }.distinct().size > 1 }
    private fun effectTree(): Pair<GameplayTree, String> {
        val tree = GameplayTree(root())
        val next = play(tree, b, PlayAction.Stack(StackAction.AddEffect(id(10), GameplayRules.stackDigest(state(tree)))))
        return next to state(next).board.stackEffects.single().id
    }

    @Test fun undoCannotRestoreCardIntoLaterEmptyCellMove() {
        var tree = move(GameplayTree(handSourceRoot()), a, id(10), grid(1))
        tree = move(tree, b, id(11), grid(0))
        assertEquals("replayBlocked", control(tree).reason)
        assertFalse(duplicates(tree))
    }

    @Test fun redoCannotMoveCardIntoLaterOccupiedCell() {
        val moved = move(GameplayTree(handSourceRoot()), a, id(10), grid(1))
        var tree = undo(moved)
        tree = move(tree, b, id(11), grid(1))
        val redone = control(tree, redo = true)
        assertEquals("replayBlocked", redone.reason)
        assertNull(redone.tree)
    }

    @Test fun tokenAutoPlacementReadsVacancy() {
        var tree = move(GameplayTree(root()), b, id(10), grid(1))
        tree = play(tree, a, PlayAction.CreateToken(TokenCatalog.blank))
        assertEquals("replayBlocked", control(tree, b).reason)
    }

    @Test fun undoCannotRemoveDefinitionStillUsedByLaterToken() {
        var tree = play(GameplayTree(root()), a, PlayAction.CreateToken(TokenCatalog.blank))
        tree = play(tree, b, PlayAction.CreateToken(TokenCatalog.blank))
        assertEquals("replayBlocked", control(tree).reason)
    }

    @Test fun copyReadsEffectiveController() {
        val (base, effect) = effectTree()
        var tree = play(base, a, PlayAction.SetOwner(id(10), b))
        tree = play(tree, b, PlayAction.Stack(StackAction.CopyEffect(effect, GameplayRules.stackDigest(state(tree)))))
        assertEquals("replayBlocked", control(tree).reason)
    }

    @Test fun copyCannotRetainFrozenSourceAfterUndoRestoresLiveToken() {
        var tree = play(GameplayTree(root()), b, PlayAction.CreateToken(TokenCatalog.blank))
        val token = state(tree).board.cards.single { it.token }.id
        tree = play(tree, b, PlayAction.Stack(StackAction.AddEffect(token, GameplayRules.stackDigest(state(tree)))))
        val effect = state(tree).board.stackEffects.single().id
        tree = play(tree, a, PlayAction.RemoveToken(token))
        tree = play(tree, b, PlayAction.Stack(StackAction.CopyEffect(effect, GameplayRules.stackDigest(state(tree)))))
        assertEquals("replayBlocked", control(tree).reason)
    }

    @Test fun restoredLinkCannotDuplicateLaterLinkPair() {
        val first = LinkEndpoint(LinkEndpointKind.CARD, id(10))
        val second = LinkEndpoint(LinkEndpointKind.CARD, id(11))
        var tree = play(GameplayTree(root()), b, PlayAction.Link(first, second))
        val link = state(tree).board.links.single().id
        tree = play(tree, a, PlayAction.RemoveLink(link))
        tree = play(tree, b, PlayAction.Link(first, second))
        assertEquals("replayBlocked", control(tree).reason)
    }

    @Test fun tokenRemovalReadsAbsentIncidentLinks() {
        var tree = play(GameplayTree(root()), b, PlayAction.CreateToken(TokenCatalog.blank))
        val token = state(tree).board.cards.single { it.token }.id
        tree = play(tree, b, PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, token), LinkEndpoint(LinkEndpointKind.CARD, id(10))))
        val link = state(tree).board.links.single().id
        tree = play(tree, a, PlayAction.RemoveLink(link))
        tree = play(tree, b, PlayAction.RemoveToken(token))
        assertEquals("replayBlocked", control(tree).reason)
    }

    @Test fun zoneExitReadsAbsentCombatOccurrences() {
        var tree = play(GameplayTree(root()), b, PlayAction.Combat(CombatAction.Open))
        val area = state(tree).board.combat!!.id
        tree = play(tree, b, PlayAction.Combat(CombatAction.Declare(area, id(10))))
        tree = play(tree, a, PlayAction.Combat(CombatAction.Close(area)))
        tree = move(tree, b, id(10), MoveDestination(id(23), MoveInsertion.UNORDERED))
        assertEquals("replayBlocked", control(tree).reason)
    }

    @Test fun restoredProjectionLinkCannotDuplicateLaterPair() {
        val (base, first) = effectTree()
        var tree = play(base, b, PlayAction.Stack(StackAction.AddEffect(id(11), GameplayRules.stackDigest(state(base)))))
        val second = state(tree).board.stackEffects.single { it.id != first }.id
        val action = PlayAction.LinkProjections(ProjectionEndpoint(first), ProjectionEndpoint(second))
        tree = play(tree, b, action)
        val link = state(tree).board.projectionLinks.single().id
        tree = play(tree, a, PlayAction.RemoveProjectionLink(link))
        tree = play(tree, b, action)
        assertEquals("replayBlocked", control(tree).reason)
    }

    @Test fun setControllerCannotBecomeNoopAfterUndo() {
        val (base, effect) = effectTree()
        var tree = play(base, a, PlayAction.SetOwner(id(10), b))
        tree = play(tree, b, PlayAction.Stack(StackAction.SetController(effect, a)))
        assertEquals("replayBlocked", control(tree).reason)
        val fresh = GameplayRules.change(state(base), b,
            f.command(GameAction.Play(PlayAction.Stack(StackAction.SetController(effect, a))), request++))
        assertEquals("noOp", fresh.outcome)
    }

    @Test fun unchangedAttentionAssignmentIsValidatedOnUndoAndRedo() {
        var tree = play(GameplayTree(root()), a, PlayAction.PassAttention)
        tree = play(tree, b, PlayAction.ChooseStep(TurnPosition.DECLARE_BLOCKERS))
        assertEquals("replayBlocked", control(tree).reason)
        val passed = play(GameplayTree(root()), a, PlayAction.PassAttention)
        val removed = undo(passed)
        val step = play(removed, b, PlayAction.ChooseStep(TurnPosition.DECLARE_BLOCKERS))
        assertEquals("replayBlocked", control(step, redo = true).reason)
    }

    @Test fun tapCannotReplayOnStackWhereLiveTapIsForbidden() {
        val initial = root().let { r -> r.copy(board = r.board.copy(
            cards = r.board.cards.map { if (it.id == id(10)) it.copy(containerId = id(25), placement = null) else it },
            containers = r.board.containers.map { it.copy(cards = when (it.id) {
                id(22) -> it.cards - id(10); id(25) -> listOf(id(10)); else -> it.cards
            }) })) }
        var tree = move(GameplayTree(initial), a, id(10), grid(1))
        tree = play(tree, b, PlayAction.SetTapped(id(10), true))
        assertEquals("replayBlocked", control(tree).reason)
        assertEquals("inapplicable", GameplayRules.change(initial, b,
            f.command(GameAction.Play(PlayAction.SetTapped(id(10), true)), request++)).reason)
    }

    @Test fun panelOpenReadsOnePanelLimit() {
        var tree = play(GameplayTree(gameplayFixture()), a, PlayAction.ShowContents(id(20), ContentsSelection.TOP))
        val panel = state(tree).board.contentsDisplays.single()
        tree = play(tree, a, PlayAction.DismissContents(panel.id))
        tree = play(tree, b, PlayAction.ShowContents(id(20), ContentsSelection.TOP))
        assertEquals("replayBlocked", control(tree).reason)
    }

    @Test fun moveCannotReplayWithoutRequiredPanelCleanupOrAuthorCheck() {
        val initial = gameplayFixture().let { r -> r.copy(board = r.board.copy(
            cards = r.board.cards.take(1), containers = r.board.containers.map {
                if (it.id == id(20)) it.copy(cards = listOf(id(10))) else it })) }
        var tree = play(GameplayTree(initial), a, PlayAction.ShowContents(id(20), ContentsSelection.ALL))
        val panel = state(tree).board.contentsDisplays.single()
        tree = play(tree, a, PlayAction.DismissContents(panel.id))
        tree = move(tree, b, id(10), MoveDestination(id(21), MoveInsertion.UNORDERED))
        assertEquals("replayBlocked", control(tree).reason)
    }

    @Test fun battlefieldLeaveOwnerResetConcernsThePreviousOwnerEvenWithAnExplicitDestination() {
        var tree = play(GameplayTree(root()), a, PlayAction.SetOwner(id(10), b))
        tree = move(tree, b, id(10), MoveDestination(id(26), MoveInsertion.UNORDERED))
        assertEquals("replayBlocked", control(tree).reason)
        val card = state(tree).board.cards.single { it.id == id(10) }
        assertEquals(a, card.owner)
        assertEquals(id(26), card.containerId)
    }

    @Test fun tokenToGraveyardDeletesRegardlessOfOwner() {
        var tree = play(GameplayTree(root()), b, PlayAction.CreateToken(TokenCatalog.blank))
        val token = state(tree).board.cards.single { it.token }.id
        tree = play(tree, a, PlayAction.SetOwner(token, a))
        tree = move(tree, b, token, MoveDestination(id(26), MoveInsertion.UNORDERED))
        val removed = undo(tree)
        assertTrue(state(removed).board.cards.none { it.id == token })
        assertNotNull(control(removed, redo = true).tree)
    }

    @Test fun unknownDrawConcernsEntireShuffleSetMembership() {
        var tree = move(GameplayTree(gameplayFixture()), a, id(13), MoveDestination(id(23), MoveInsertion.UNORDERED))
        tree = play(tree, b, PlayAction.MoveCard(id(10), MoveSource(id(20), edge = MoveSourceEdge.TOP),
            MoveDestination(id(21), MoveInsertion.UNORDERED)))
        assertEquals("replayBlocked", control(tree).reason)
    }

    @Test fun projectionPingProtectsProjectionRatherThanSourceCardOrAncestry() {
        var tree = play(GameplayTree(root()), b, PlayAction.Combat(CombatAction.Open))
         tree = play(tree, a, PlayAction.Combat(CombatAction.Declare(state(tree).board.combat!!.id, id(10))))
         val occurrence = CombatStackRules.occurrences(state(tree).board.combat).single().id
        // Keep the ping's group stable so this tests target scope, not annotation regrouping.
        tree = play(tree, b, PlayAction.AdjustLife(b, "1"))
        val reference = EventReference(tree.initial.id, tree.currentNodeId, tree.contextVersion(tree.currentNodeId))
        val pinged = play(tree, b, PlayAction.Ping(reference, PingTarget(PingTargetKind.OCCURRENCE, occurrence)))
        assertEquals("replayBlocked", control(pinged).reason)
        val cardPing = play(tree, b, PlayAction.Ping(reference, PingTarget(PingTargetKind.CARD, id(10))))
        assertNotNull(control(cardPing).tree)
    }

    @Test fun invocationMembersShareOneBoundedBudget() {
        var tree = GameplayTree(root())
        repeat(52) {
            val command = f.command(GameAction.Play(PlayAction.AdjustLife(a, "1")), request++).copy(invocationId = id(999))
            tree = assertNotNull(HistoryContinuation.change(tree, a, command).tree)
        }
        tree.work.reset()
        val refused = control(tree)
        assertEquals("tooFarBack", refused.reason)
        assertNull(refused.tree)
        assertTrue(tree.work.ancestrySteps <= 2)
        assertEquals(0, tree.work.unappliedNodes)
        assertTrue(tree.redoByAccount.isEmpty())
    }

    @Test fun knownOrderDrawDoesNotConcernUntakenNextCard() {
        val initial = gameplayFixture().let { r -> r.copy(board = r.board.copy(containers = r.board.containers.map {
            if (it.id == id(20)) it.copy(knownTop = listOf(id(10), id(11))) else it })) }
        var tree = move(GameplayTree(initial), a, id(11), MoveDestination(id(23), MoveInsertion.UNORDERED))
        tree = play(tree, b, PlayAction.MoveCard(id(10), MoveSource(id(20), edge = MoveSourceEdge.TOP),
            MoveDestination(id(21), MoveInsertion.UNORDERED)))
        val undone = undo(tree)
        assertEquals(listOf(id(11)), state(undone).board.containers.single { it.id == id(20) }.knownTop)
        assertNotNull(control(undone, redo = true).tree)
    }

    @Test fun bottomSliceGuardIsReplayedBeyondAdjacentNeighbor() {
        var tree = move(GameplayTree(gameplayFixture()), a, id(14), MoveDestination(id(23), MoveInsertion.UNORDERED))
        tree = play(tree, b, PlayAction.MoveCard(id(11), MoveSource(id(20), bottomSlice = listOf(id(11), id(12), id(13))),
            MoveDestination(id(21), MoveInsertion.UNORDERED)))
        assertEquals("replayBlocked", control(tree).reason)
    }

    @Test fun shuffleValidatesUnchangedKnownEdges() {
        for (top in listOf(true, false)) {
            val initial = gameplayFixture().let { r -> r.copy(board = r.board.copy(cards = r.board.cards.take(1),
                containers = r.board.containers.map { if (it.id == id(20)) it.copy(cards = listOf(id(10)),
                    knownTop = if (top) listOf(id(10)) else emptyList(),
                    knownBottom = if (top) emptyList() else listOf(id(10))) else it })) }
            val shuffle = PlayAction.ShuffleLibrary(id(20), RandomV1.encodeSeed(ByteArray(32)))
            var tree = play(GameplayTree(initial), a, shuffle)
            tree = play(tree, b, shuffle)
            assertEquals("replayBlocked", control(tree).reason)
        }
    }

    @Test fun shuffleCannotRetainRestoredLegacyKnownToAll() {
        val initial = gameplayFixture().let { r -> r.copy(board = r.board.copy(cards = r.board.cards.map {
            if (it.id == id(10)) it.copy(knownToAll = true) else it })) }
        var tree = play(GameplayTree(initial), a, PlayAction.ShowContents(id(20), ContentsSelection.TOP))
        tree = play(tree, b, PlayAction.ShuffleLibrary(id(20), RandomV1.encodeSeed(ByteArray(32))))
        assertEquals("replayBlocked", control(tree).reason)
    }

    @Test fun everyLibrarySourceEdgeConcernsUnchangedKnowledgeCleanup() {
        val ids = listOf(id(10), id(11))
        val seed = (0..20).map { n -> RandomV1.encodeSeed(ByteArray(32) { n.toByte() }) }
            .first { RandomV1.shuffle(ids, it) == ids }
        for (knownTop in listOf(true, false)) for (edge in MoveSourceEdge.entries) for (within in listOf(false, true)) {
            val initial = gameplayFixture().let { r -> r.copy(board = r.board.copy(cards = r.board.cards.take(2),
                containers = r.board.containers.map { if (it.id == id(20)) it.copy(cards = ids,
                    knownTop = if (knownTop) listOf(ids.first()) else emptyList(),
                    knownBottom = if (knownTop) emptyList() else listOf(ids.last())) else it })) }
            var tree = play(GameplayTree(initial), a, PlayAction.ShuffleLibrary(id(20), seed))
            assertEquals(ids, state(tree).board.containers.single { it.id == id(20) }.cards)
            val card = if (edge == MoveSourceEdge.BOTTOM) ids.last() else ids.first()
            val destination = if (within) MoveDestination(id(20), MoveInsertion.GAP,
                afterId = if (card == ids.first()) ids.last() else null,
                beforeId = if (card == ids.last()) ids.first() else null)
                else MoveDestination(id(21), MoveInsertion.UNORDERED)
            tree = play(tree, b, PlayAction.MoveCard(card, MoveSource(id(20), edge = edge), destination))
            val fields = tree.nodes.getValue(tree.currentNodeId).transition.concerns!!.fields
            for (key in listOf("knownTop", "knownBottom")) {
                val tuple = fields.single { it.field == ConcernField.Entity("container", id(20), listOf(key)) }
                assertEquals(tuple.before, tuple.after, "$edge within=$within $key is an unchanged cleanup")
            }
            tree.work.reset()
            val refused = control(tree)
            assertEquals("replayBlocked", refused.reason, "$edge within=$within knownTop=$knownTop")
            assertNull(refused.tree)
            assertEquals(0, tree.work.rootMaterializations)
            assertEquals(0, tree.work.fullHistoryScans)
        }
    }

    @Test fun undoRefusesToStrandRegroupedPingsCommentsAndOrdinals() {
        for (comment in listOf(false, true)) {
            var tree = play(GameplayTree(root()), b, PlayAction.SetNote(id(10), "B"))
            tree = play(tree, a, PlayAction.SetNote(id(11), "A"))
            val reference = EventReference(tree.initial.id, tree.initial.root.id, tree.contextVersion(tree.initial.root.id))
            val action = if (comment) PlayAction.EventComment(reference, "retained comment")
                else PlayAction.Ping(reference, PingTarget(PingTargetKind.CARD, id(10)))
            tree = play(tree, b, action)
            val before = state(tree)
            val group = before.currentGroup!!.id
            if (comment) assertEquals(group, before.annotations.comments.single().recording.groupId)
            else {
                assertEquals("1", EventAnnotations.activePings(before.annotations, group).single().to!!.ordinal)
                assertEquals(1L, before.annotations.lastOrdinals[group])
            }
            tree.work.reset()
            val refused = control(tree)
            assertEquals("replayBlocked", refused.reason)
            assertNull(refused.tree)
            assertEquals(before, state(tree))
            assertEquals(0, tree.work.rootMaterializations)
            assertEquals(0, tree.work.fullHistoryScans)
        }
    }

    @Test fun redoRefusesToStrandRegroupedPingOrdinal() {
        var tree = play(GameplayTree(root()), b, PlayAction.SetNote(id(10), "B"))
        tree = play(tree, a, PlayAction.SetNote(id(11), "A"))
        tree = undo(tree)
        val reference = EventReference(tree.initial.id, tree.initial.root.id, tree.contextVersion(tree.initial.root.id))
        tree = play(tree, b, PlayAction.Ping(reference, PingTarget(PingTargetKind.CARD, id(10))))
        val before = state(tree)
        val refused = control(tree, redo = true)
        assertEquals("replayBlocked", refused.reason)
        assertNull(refused.tree)
        assertEquals("1", EventAnnotations.activePings(state(tree).annotations, before.currentGroup!!.id).single().to!!.ordinal)
        assertEquals(1L, state(tree).annotations.lastOrdinals[before.currentGroup.id])
    }

    @Test fun annotationsWithStableGroupSurviveIndependentUndoAndRedo() {
        var tree = play(GameplayTree(root()), a, PlayAction.SetTapped(id(11), true))
        tree = play(tree, b, PlayAction.SetNote(id(10), "B"))
        val reference = EventReference(tree.initial.id, tree.initial.root.id, tree.contextVersion(tree.initial.root.id))
        tree = play(tree, b, PlayAction.Ping(reference, PingTarget(PingTargetKind.CARD, id(10))))
        tree = play(tree, b, PlayAction.EventComment(reference, "retained"))
        val before = state(tree)
        val undone = undo(tree)
        val redone = assertNotNull(control(undone, redo = true).tree)
        for (candidate in listOf(undone, redone)) {
            val after = state(candidate)
            assertEquals(before.currentGroup!!.id, after.currentGroup!!.id)
            assertEquals(before.annotations, after.annotations)
            assertEquals("1", EventAnnotations.activePings(after.annotations, after.currentGroup.id).single().to!!.ordinal)
            assertEquals(after.currentGroup.id, after.annotations.comments.single().recording.groupId)
        }
    }

    @Test fun independentForkIsBlockedEvenWithTinySuffix() {
        var tree = play(GameplayTree(root()), a, PlayAction.SetTapped(id(10), true))
        val anchor = tree.currentNodeId
        tree = play(tree, b, PlayAction.AdjustLife(b, "1"))
        tree = play(tree, b, PlayAction.AdjustLife(b, "2"), anchor)
        assertEquals("forkBoundary", control(tree).reason)
    }

    @Test fun redoIndependentEventFailsWhenOpponentUndoesItsParent() {
        var tree = play(GameplayTree(root()), b, PlayAction.SetTapped(id(10), true))
        tree = play(tree, a, PlayAction.SetTapped(id(11), true))
        tree = undo(tree)
        tree = undo(tree, b)
        assertEquals("missingAnchor", control(tree, redo = true).reason)
    }

    @Test fun retainedReseedInputTupleIsNeverRewritten() {
        var tree = play(GameplayTree(gameplayFixture()), b, PlayAction.AdjustLife(b, "1"))
        val anchor = tree.initial.root.id
        tree = play(tree, a, PlayAction.AdjustLife(a, "1"), anchor)
        val own = tree.currentNodeId
        tree = play(tree, b, PlayAction.SetPlayerCounter(b, CounterColor.RED, "other branch"), anchor)
        tree = play(tree, b, PlayAction.SetPlayerCounter(b, CounterColor.BLUE, "retained"), own)
        val suffix = tree.currentNodeId
        val original = tree.nodes.getValue(suffix).transition.delta!!.libraryReseeds
        assertEquals("replayBlocked", control(tree).reason)
        assertEquals(original, tree.nodes.getValue(suffix).transition.delta!!.libraryReseeds)
    }

    @Test fun cardPingDoesNotLockWholeReferencedAncestry() {
        var tree = play(GameplayTree(root()), a, PlayAction.SetTapped(id(10), true))
        tree = play(tree, b, PlayAction.AdjustLife(b, "1"))
        val reference = EventReference(tree.initial.id, tree.currentNodeId, tree.contextVersion(tree.currentNodeId))
        tree = play(tree, b, PlayAction.Ping(reference, PingTarget(PingTargetKind.CARD, id(11))))
        assertNotNull(control(tree).tree)
    }

    @Test fun singleEventSuffixDoesNotTraverseLongHistoryForAnnotationReference() {
        var tree = GameplayTree(root())
        repeat(120) { tree = play(tree, b, PlayAction.AdjustLife(b, "1")) }
        tree = play(tree, a, PlayAction.PassAttention)
        tree = play(tree, b, PlayAction.Ping(EventReference(tree.initial.id, tree.initial.root.id,
            tree.contextVersion(tree.initial.root.id)), PingTarget(PingTargetKind.CARD, id(11))))
        tree.work.reset()
        val removed = undo(tree)
        assertTrue(removed.work.unappliedNodes <= 3, "actual work ${removed.work.unappliedNodes}")
        assertEquals(0, removed.work.rootMaterializations)
        assertEquals(0, removed.work.fullHistoryScans)
    }

    @Test fun exposedFaceAndRecordedRandomAreReversible() {
        val exposed = play(GameplayTree(root()), a, PlayAction.InspectFace(id(10), id(30), id(22)))
        assertNotNull(control(exposed).tree)
        for (action in listOf(PlayAction.RollDie(6, 4), PlayAction.FlipCoin(CoinFace.HEADS))) {
            val random = play(GameplayTree(root()), a, action)
            val undone = undo(random)
            val redone = control(undone, redo = true)
            assertEquals(random.nodes, assertNotNull(redone.tree, redone.reason).nodes)
        }
    }
}
