package org.asyncttwiz.protocol

import kotlin.test.*
import kotlin.time.TimeSource

class GameplayDeltaTest {
    private val a = gameplayId(7)
    private val b = gameplayId(8)
    private var request = 50000
    private val seen = mutableSetOf<String>()
    private fun command(state: PlayState, action: GameAction) = GameCommand(gameplayId(request++),
        GameScope(state.gameId, playId = state.id), action, gameplayId(80), "1")

    private fun check(state: PlayState, action: PlayAction,
        references: EventReferenceResolver = EventReferenceResolver.UNAVAILABLE): PlayState {
        val change = GameplayRules.change(state, a, command(state, GameAction.Play(action)), references)
        val event = assertNotNull(change.mutation, "$action: ${change.reason}/${change.outcome}")
        val next = assertNotNull(change.state)
        seen += action::class.simpleName!!
        assertEquals(2, event.schemaVersion)
        assertEquals(next, GameplayHistory.apply(state, event), "$action forward")
        assertEquals(state, GameplayHistory.unapply(next, event), "$action inverse")
        assertNull(GameplayHistory.apply(next, event), "wrong parent must fail")
        assertNull(GameplayHistory.unapply(state, event), "wrong head must fail")
        // Both accounts receive the retained tree wire format; face hiding is currently a
        // CardVisibility presentation policy, not a different PlayState/event wire schema.
        for (viewer in listOf(a, b)) {
            val wireState = GameWire.json.decodeFromString(PlayState.serializer(), GameWire.json.encodeToString(PlayState.serializer(), state))
            val wireEvent = GameWire.json.decodeFromString(GameplayMutation.serializer(), GameWire.json.encodeToString(GameplayMutation.serializer(), event))
            assertEquals(event, wireEvent, "$viewer event codec")
            val applied = assertNotNull(GameplayHistory.apply(wireState, wireEvent), "$viewer $action")
            assertEquals(next, applied)
            assertEquals(wireState, GameplayHistory.unapply(applied, wireEvent))
        }
        return next
    }

    @Test fun everyPlayActionHasAnExactRecordedForwardAndInverseIncludingCompoundCleanup() {
        val root = gameplayFixture()
        val card = root.board.cards.first().id
        for (action in listOf<PlayAction>(PlayAction.SetTapped(card, true), PlayAction.SetLife(a, "17"),
            PlayAction.AdjustLife(a, "-2"), PlayAction.SetPlayerCounter(a, CounterColor.BLUE, "3"),
            PlayAction.AdjustPlayerCounter(a, CounterColor.RED, "1"), PlayAction.NextStep, PlayAction.EndTurn,
            PlayAction.ChooseStep(TurnPosition.DECLARE_BLOCKERS), PlayAction.PassAttention,
            PlayAction.RollDie(20, 17), PlayAction.FlipCoin(CoinFace.TAILS),
            PlayAction.SetCorner(card, "4/4"), PlayAction.ResetCorner(card),
            PlayAction.SetCounter(CounterTarget.CARD, card, CounterColor.RED, "4"),
            PlayAction.AdjustCounter(CounterTarget.CARD, card, CounterColor.RED, "-1"),
            PlayAction.ClearCardCounter(card, CounterColor.RED), PlayAction.SetNote(card, "new note"),
            PlayAction.SetOwner(card, b), PlayAction.ShuffleLibrary(gameplayId(20), RandomV1.encodeSeed(ByteArray(32))),
            PlayAction.SuggestResult(SuggestedResult.DRAW))) check(root, action)

        val numeric = check(root, PlayAction.SetCorner(card, "3/5"))
        check(numeric, PlayAction.AdjustCorner(card, CornerPart.LEFT, 1))
        val shown = check(root, PlayAction.ShowContents(gameplayId(20), ContentsSelection.ALL, reveal = true))
        check(shown, PlayAction.DismissContents(shown.board.contentsDisplays.single().id))
        var field = check(root, PlayAction.MoveCard(card, MoveSource(gameplayId(20)),
            MoveDestination(gameplayId(22), MoveInsertion.EMPTY, BoardPosition(0, 0))))
        val covered = check(field, PlayAction.SetCovered(card, true))
        check(covered, PlayAction.InspectFace(card, gameplayId(30), gameplayId(22)))
        val alternateFace = gameplayId(31)
        val definition = root.definitions.single().definition.let { it.copy(faces = listOf(
            it.faces.single().copy(otherFaceIds = listOf(alternateFace)), DefinitionFace(alternateFace, "Back", otherFaceIds = listOf(gameplayId(30))))) }
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val twoFaces = field.copy(definitions = listOf(submitted), board = field.board.copy(cards = field.board.cards.map { it.copy(definition = submitted.digest) }))
        check(twoFaces, PlayAction.InspectFace(card, alternateFace, gameplayId(22)))
        check(twoFaces, PlayAction.SetFace(card, alternateFace))
        val token = check(field, PlayAction.CreateToken(TokenCatalog.blank))
        check(token, PlayAction.RemoveToken(token.board.cards.single { it.token }.id))
        check(field, PlayAction.CloneToken(card))
        val linked = check(field, PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, card), LinkEndpoint(LinkEndpointKind.PLAYER, b)))
        check(linked, PlayAction.RemoveLink(linked.board.links.single().id))

        field = check(field, PlayAction.Combat(CombatAction.Open))
        field = check(field, PlayAction.Combat(CombatAction.Declare(field.board.combat!!.id, card)))
        val occurrence = CombatStackRules.occurrences(field.board.combat).single()
        check(field, PlayAction.Combat(CombatAction.Place(card, CombatDestination(field.board.combat!!.id,
            CombatRole.BLOCKER, field.board.combat!!.pairs.single().id), CombatStackRules.location(field.board.combat!!, occurrence.id))))
        check(field, PlayAction.Combat(CombatAction.Remove(field.board.combat!!.id, occurrence.id)))
        check(field, PlayAction.Combat(CombatAction.Close(field.board.combat!!.id)))
        var stack = check(field, PlayAction.Stack(StackAction.AddEffect(card, GameplayRules.stackDigest(field))))
        stack = check(stack, PlayAction.Stack(StackAction.AddEffect(card, GameplayRules.stackDigest(stack))))
        val order = CombatStackRules.stack(stack.board).cards
        check(stack, PlayAction.Stack(StackAction.ReorderEffect(order[1], IdentityGap(order[0], null), IdentityGap(null, order[0]))))
        check(stack, PlayAction.Stack(StackAction.RemoveEffect(order[0])))
        val textStack = check(twoFaces, PlayAction.Stack(StackAction.AddEffect(card, GameplayRules.stackDigest(twoFaces))))
        val textEntry = textStack.board.stackEffects.single().id
        val marked = check(textStack, PlayAction.Stack(StackAction.MarkText(textEntry, 0)))
        check(marked, PlayAction.Stack(StackAction.MarkText(textEntry, null)))
        val projections = check(stack, PlayAction.LinkProjections(ProjectionEndpoint(occurrence.id), ProjectionEndpoint(order[0])))
        check(projections, PlayAction.RemoveProjectionLink(projections.board.projectionLinks.single().id))
        check(projections, PlayAction.MoveCard(card, MoveSource(gameplayId(22), projections.board.cards.first().placement),
            MoveDestination(gameplayId(23), MoveInsertion.UNORDERED)))
        val projectedToken = token.board.cards.single { it.token }
        val tokenStack = check(token, PlayAction.Stack(StackAction.AddEffect(projectedToken.id, GameplayRules.stackDigest(token))))
        check(tokenStack, PlayAction.MoveCard(projectedToken.id, MoveSource(projectedToken.containerId, projectedToken.placement),
            MoveDestination(gameplayId(20), MoveInsertion.TOP)))
        val reference = EventReference(root.id, root.root.id)
        val resolver = EventReferenceResolver { _, _, _ -> RetainedAnnotationContext.from(root, "root") }
        check(root, PlayAction.EventComment(reference, "comment"), resolver)
        val ping = check(root, PlayAction.Ping(reference, PingTarget(PingTargetKind.CARD, card)), resolver)
        check(ping, PlayAction.Unping(reference, PingTarget(PingTargetKind.CARD, card)), resolver)
        assertEquals(setOf("SuggestResult", "SetTapped", "SetLife", "AdjustLife", "SetPlayerCounter", "AdjustPlayerCounter",
            "NextStep", "PassAttention", "EndTurn", "ChooseStep", "RollDie", "FlipCoin", "MoveCard", "ShuffleLibrary",
            "ShowContents", "DismissContents", "EventComment", "Ping", "Unping", "SetCorner", "AdjustCorner", "ResetCorner",
            "SetCounter", "AdjustCounter", "SetNote", "SetOwner", "SetFace", "SetCovered", "InspectFace", "ClearCardCounter",
            "CreateToken", "CloneToken", "RemoveToken", "Link", "RemoveLink", "LinkProjections", "RemoveProjectionLink", "Combat", "Stack"), seen)
    }

    private fun longGame(): GameplayTree {
        val base = gameplayFixture()
        val cards = listOf(a, b).flatMapIndexed { player, actor -> List(60) { index -> base.board.cards.first().copy(
            id = gameplayId(1000 + player * 60 + index), owner = actor, containerId = gameplayId(20 + player * 10), corner = "3/5") } }
        val containers = base.board.containers.map { if (it.zone == PlayZone.LIBRARY) it.copy(cards = cards.take(60).map { c -> c.id }) else it } +
            listOf(PlayContainer(gameplayId(30), PlayZone.LIBRARY, b, cards.drop(60).map { it.id }), PlayContainer(gameplayId(31), PlayZone.HAND, b))
        var tree = GameplayTree(base.copy(board = base.board.copy(cards = cards, containers = containers)))
        repeat(360) { index ->
            val state = tree.stateAt(tree.currentNodeId)!!
            val actor = if (index % 2 == 0) a else b
            val card = cards[index % cards.size]
            val action = when (index % 9) {
                0 -> PlayAction.AdjustLife(actor, "-1")
                1 -> PlayAction.SetNote(card.id, "note $index")
                2 -> PlayAction.AdjustPlayerCounter(actor, CounterColor.BLUE, "1")
                3 -> PlayAction.NextStep
                4 -> PlayAction.RollDie(20, index % 20 + 1)
                5 -> PlayAction.SetCounter(CounterTarget.CARD, card.id, CounterColor.GREEN, index.toString())
                6 -> PlayAction.ShuffleLibrary(gameplayId(if (actor == a) 20 else 30), RandomV1.encodeSeed(ByteArray(32) { index.toByte() }))
                7 -> PlayAction.FlipCoin(CoinFace.HEADS)
                else -> PlayAction.AdjustLife(actor, "2")
            }
            tree = assertNotNull(tree.play(actor, command(state, GameAction.Play(action))).tree)
        }
        return tree
    }

    @Test fun longGameUnappliesToTheExactRootAndRemovalEqualsReverseTraversal() {
        val tree = longGame()
        var state = tree.stateAt(tree.currentNodeId)!!
        for (event in tree.path(tree.currentNodeId)!!.asReversed()) state = assertNotNull(GameplayHistory.unapply(state, event), event.action.toString())
        assertEquals(tree.initial, state)
        val head = tree.nodes.getValue(tree.currentNodeId)
        val before = assertNotNull(GameplayHistory.unapply(tree.stateAt(head.id)!!, head))
        val removed = tree.copy(nodes = tree.nodes - head.id, currentNodeId = head.parentId)
        assertEquals(before, removed.stateAt(head.parentId))
        val undone = assertNotNull(SelectiveUndo.change(tree, head.author, command(before, GameAction.Undo)).tree)
        assertEquals(before, undone.stateAt(undone.currentNodeId))
    }

    private fun medianMillis(block: () -> Unit): Double {
        repeat(5) { block() }
        return List(9) { val start = TimeSource.Monotonic.markNow(); block(); start.elapsedNow().inWholeNanoseconds / 1_000_000.0 }.sorted()[4]
    }

    @Test fun retainedMetadataDoesNotInvalidateRecordedEffectsButEffectCorruptionDoes() {
        val root = gameplayFixture()
        val action = PlayAction.SetTapped(root.board.cards.first().id, true)
        val event = assertNotNull(GameplayRules.change(root, a, command(root, GameAction.Play(action))).mutation)
        val retained = event.copy(branchId = gameplayId(90000), grouping = null)
        val cold = GameplayTree(root, mapOf(retained.id to retained), retained.id)
        val state = assertNotNull(cold.stateAt(retained.id))
        assertTrue(state.board.cards.first().tapped)
        assertEquals(retained.branchId, state.currentBranchId)
        assertEquals(root, cold.stateAt(root.root.id), "inverse traversal restores the original context")
        val wire = TreeSync.decode(TreeSync.records(TreeSync.generation(cold)), root.gameId, root.id).tree
        assertEquals(state, wire.stateAt(retained.id))
        val replayed = assertNotNull(GameplayHistory.replay(root, retained).mutation)
        assertEquals(root, GameplayHistory.unapply(state, replayed))
        assertNull(GameplayHistory.apply(root, event.copy(transition = event.transition.copy(fields = emptyList()))))
        assertNull(GameplayHistory.apply(root, event.copy(action = PlayAction.SetTapped(action.cardId, false))))
    }

    @Test fun warmLongGameUndoAndCursorBenchmarksAndThe50EventBoundary() {
        val tree = longGame()
        val head = tree.nodes.getValue(tree.currentNodeId)
        val before = assertNotNull(tree.stateAt(head.parentId))
        assertTrue(medianMillis { assertNotNull(GameplayHistory.apply(before, head)) } < 20.0)
        val undo = command(tree.initial, GameAction.Undo)
        assertTrue(medianMillis { assertNotNull(HistoryContinuation.change(tree, head.author, undo).tree) } < 20.0)
        var selective = assertNotNull(tree.play(a, command(tree.initial, GameAction.Play(PlayAction.SetNote(gameplayId(1000), "target")))).tree)
        repeat(50) { selective = assertNotNull(selective.play(b, command(tree.initial, GameAction.Play(PlayAction.AdjustLife(b, "1")))).tree) }
        assertTrue(medianMillis { assertNotNull(HistoryContinuation.change(selective, a, undo).tree) } < 20.0)
        val fiftyOne = assertNotNull(selective.play(b, command(tree.initial, GameAction.Play(PlayAction.AdjustLife(b, "1")))).tree)
        assertEquals("tooFarBack", HistoryContinuation.change(fiftyOne, a, undo).reason)
        assertTrue(medianMillis {
            assertNotNull(tree.prepareReview(head.parentId)); assertNotNull(tree.prepareReview(head.id))
        } < 5.0)
    }
}
