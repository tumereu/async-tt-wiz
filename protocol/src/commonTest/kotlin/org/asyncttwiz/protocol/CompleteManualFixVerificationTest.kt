package org.asyncttwiz.protocol

import kotlin.test.*

/** Fresh, bounded checks of dbae70f; no ordinary I13 movement or full Undo claim. */
class CompleteManualFixVerificationTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val definition = CardDefinition("Verifier", listOf(
        DefinitionFace(id(11), "Front", listOf(id(12)), corner = "1/2"),
        DefinitionFace(id(12), "Back", listOf(id(11)), corner = "3/4")))
    private val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
    private fun initial() = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))),
            listOf(PlayCard(id(9), submitted.digest, id(8), id(10), id(11), corner = "1/2",
                placement = PlayPlacement(BoardPosition(0, 0), id(15)))),
            listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, id(7), listOf(id(9))),
                PlayContainer(id(13), PlayZone.LIBRARY, id(7)), PlayContainer(id(14), PlayZone.STACK)), TurnState(id(7))),
        listOf(submitted), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    private fun change(state: PlayState, action: PlayAction, request: Int, actor: String = id(7)) =
        GameplayRules.change(state, actor, GameCommand(id(request), GameScope(state.gameId, playId = state.id),
            GameAction.Play(action), id(20), "1"))
    private fun accept(state: PlayState, action: PlayAction, request: Int, actor: String = id(7)) =
        change(state, action, request, actor).also {
            assertEquals("accepted", it.outcome, "$action")
            assertEquals(it, GameplayHistory.replay(state, it.mutation!!), "$action")
            assertEquals(state.currentNodeId, it.mutation.parentId)
        }
    private fun inspect() = PlayAction.InspectFace(id(9), id(12), id(10))

    @Test fun tapTrackingInspectionAndFollowingEditRetainEveryEffectivePrefix() {
        val root = initial()
        val tap = accept(root, PlayAction.SetTapped(id(9), true), 100)
        val tracking = accept(tap.state!!, PlayAction.EndTurn, 101)
        val view = accept(tracking.state!!, inspect(), 102)
        val edit = accept(view.state!!, PlayAction.SetNote(id(9), "after V"), 103)
        val prefixes = listOf(tap, tracking, view, edit)
        val expected = listOf("tap", "tap", "view", "edit")
        var folded: GameplayGroup? = null
        for ((index, result) in prefixes.withIndex()) {
            val mutation = result.mutation!!
            folded = GameplayHistory.append(folded, mutation)
            assertEquals(result.state!!.currentGroup, folded)
            assertEquals(expected[index], folded.category)
            assertEquals(mutation.id, folded.lastNodeId)
            assertEquals(if (index == 3) "1" else (index + 1).toString(), folded.count)
            assertEquals(if (index == 3) edit.mutation!!.id else tap.mutation!!.id, folded.firstNodeId)
        }
        assertEquals(tap.state.currentGroup!!.id, view.state.currentGroup!!.id)
        assertEquals(view.mutation!!.id, view.state.currentGroup.representative!!.nodeId)
        assertEquals("visibility", view.state.currentGroup.representative.semantic.icon.wire)
        assertEquals(tracking.state.board, view.state.board)
        assertEquals(listOf(GameplayDisclosure(id(9), id(12), id(10), listOf(id(7)))), view.mutation.transition.disclosures)
        assertNotEquals(view.state.currentGroup.id, edit.state!!.currentGroup!!.id)
        // Earlier immutable prefixes never borrow the later disclosure's representative.
        assertEquals(tap.mutation!!.id, tracking.state.currentGroup!!.representative!!.nodeId)
        assertEquals(root.currentNodeId, tap.mutation.parentId)
    }

    @Test fun backwardTrackingDoesNotHideCompletedCreationEditOrViewContext() {
        for (anchor in listOf<PlayAction>(PlayAction.CloneToken(id(9)), PlayAction.SetNote(id(9), "E"), inspect())) {
            val first = accept(initial(), anchor, 110).state!!
            val customized = if (anchor is PlayAction.CloneToken)
                accept(first, PlayAction.SetCorner(id(9), "custom"), 111).state!! else first
            val tracked = accept(customized, PlayAction.NextStep, 112).state!!
            assertEquals(first.currentGroup!!.id, tracked.currentGroup!!.id)
            assertEquals(first.currentGroup.category, tracked.currentGroup.category)
            val view = accept(tracked, inspect(), 113)
            assertNotEquals(tracked.currentGroup.id, view.state!!.currentGroup!!.id, "$anchor; B; V")
            assertEquals("1", view.state.currentGroup.count)
            assertEquals(view.mutation!!.id, view.state.currentGroup.firstNodeId)
            assertEquals("view", view.state.currentGroup.category)
        }
    }

    @Test fun actualForwardClosureAndRandomContextsAcceptInspectionButAuthorsStillSplit() {
        val opened = accept(initial(), PlayAction.Combat(CombatAction.Open), 120).state!!
        val closure = accept(opened, PlayAction.Combat(CombatAction.Close(opened.board.combat!!.id)), 121).state!!
        val random = accept(initial(), PlayAction.FlipCoin(CoinFace.HEADS), 122).state!!
        val tap = accept(initial(), PlayAction.SetTapped(id(9), true), 123).state!!
        for (pending in listOf(closure, random, tap)) {
            val tracked = accept(pending, PlayAction.NextStep, 124).state!!
            val viewed = accept(tracked, inspect(), 125).state!!
            assertEquals(pending.currentGroup!!.id, viewed.currentGroup!!.id)
            assertEquals(pending.currentGroup.firstNodeId, viewed.currentGroup.firstNodeId)
            assertEquals("3", viewed.currentGroup.count)
            assertEquals("view", viewed.currentGroup.category)
            val other = accept(tracked, inspect(), 126, id(8))
            assertNotEquals(pending.currentGroup.id, other.state!!.currentGroup!!.id)
            assertEquals(id(8), other.state.currentGroup.author)
            assertEquals(listOf(id(8)), other.mutation!!.transition.disclosures.single().viewers)
        }
    }

    @Test fun noOpOrRejectedRequestsCannotSupplyAnInspectionAnchor() {
        val pending = accept(initial(), PlayAction.SetTapped(id(9), true), 130).state!!
        val noOp = change(pending, PlayAction.SetNote(id(9), ""), 131)
        assertEquals("noOp", noOp.outcome)
        assertEquals(pending, noOp.state)
        assertNull(noOp.mutation)
        for (bad in listOf(inspect().copy(containerId = id(13)), inspect().copy(faceId = id(99)))) {
            val rejected = change(pending, bad, 132)
            assertEquals("rejected", rejected.outcome)
            assertNull(rejected.state)
            assertNull(rejected.mutation)
        }
        assertEquals(pending.currentGroup!!.id, accept(pending, inspect(), 133).state!!.currentGroup!!.id)
    }

    @Test fun realForeignFamilyTuplesAreRefusedWithoutLockingUnrelatedCombatState() {
        val root = initial()
        val manual = accept(root, PlayAction.SetCorner(id(9), "manual"), 140).mutation!!
        val clone = accept(root, PlayAction.CloneToken(id(9)), 141)
        val combat = accept(root, PlayAction.Combat(CombatAction.Open), 142)
        val effect = accept(root, PlayAction.Stack(StackAction.AddEffect(id(9), CombatStackRules.stackDigest(root))), 143)
        val contents = accept(root, PlayAction.ShowContents(id(13), ContentsSelection.ALL), 144)
        val random = accept(root, PlayAction.RollDie(6, 2), 145)
        val shuffle = accept(root, PlayAction.ShuffleLibrary(id(13), RandomV1.encodeSeed(ByteArray(32))), 146)
        val foreign = listOf(combat.mutation!!.transition, effect.mutation!!.transition,
            contents.mutation!!.transition, random.mutation!!.transition, shuffle.mutation!!.transition)
        for (extra in foreign) {
            val rejected = GameplayHistory.replay(root, manual.copy(transition = extra.copy(fields = manual.transition.fields)))
            assertEquals("replayBlocked", rejected.reason)
            assertNull(rejected.state)
            assertNull(rejected.mutation)
            assertNull(GameplayHistory.inverseTokenLinks(clone.state!!.board,
                extra.copy(tokens = clone.mutation!!.transition.tokens)))
        }
        val independent = GameplayHistory.replay(effect.state!!, manual)
        assertEquals(manual.transition, independent.mutation!!.transition)
        assertEquals(effect.state.board.stackEffects, independent.state!!.board.stackEffects)
        assertEquals(CombatStackRules.stackDigest(effect.state), CombatStackRules.stackDigest(independent.state))
        assertEquals(root.board, GameplayHistory.inverseTokenLinks(clone.state!!.board, clone.mutation!!.transition))
    }

    @Test fun tokenLibraryDeparturePreservesSharedPairSiblingAndExactFrozenTuple() {
        var state = accept(initial(), PlayAction.CloneToken(id(9)), 150).state!!
        val tokenId = state.board.cards.single { it.token }.id
        state = accept(state, PlayAction.Combat(CombatAction.Open), 151).state!!
        state = accept(state, PlayAction.Combat(CombatAction.Declare(state.board.combat!!.id, tokenId)), 152).state!!
        val area = state.board.combat!!
        state = accept(state, PlayAction.Combat(CombatAction.Place(id(9),
            CombatDestination(area.id, CombatRole.BLOCKER, pairId = area.pairs.single().id))), 153).state!!
        repeat(2) { state = accept(state, PlayAction.Stack(StackAction.AddEffect(tokenId, CombatStackRules.stackDigest(state))), 154 + it).state!! }
        state = accept(state, PlayAction.SetTapped(tokenId, true), 156).state!!
        state = accept(state, PlayAction.SetCovered(tokenId, false), 157).state!!
        state = accept(state, PlayAction.SetCounter(CounterTarget.CARD, tokenId, CounterColor.BLUE, "9"), 158).state!!
        state = accept(state, PlayAction.SetNote(tokenId, "last live"), 159).state!!
        val token = state.board.cards.single { it.id == tokenId }
        val effects = state.board.stackEffects
        val departing = CombatStackRules.occurrences(state.board.combat).single { it.sourceId == tokenId }
        val survivor = CombatStackRules.occurrences(state.board.combat).single { it.sourceId == id(9) }
        // Explicit accepted-state fixture: projection-link authoring is not yet an implemented route.
        val incident = ProjectionLink(id(30), departing.id, effects.first().id)
        val retained = ProjectionLink(id(31), survivor.id, effects.last().id)
        state = state.copy(board = state.board.copy(projectionLinks = listOf(incident, retained)))
        val action = PlayAction.MoveCard(tokenId, MoveSource(token.containerId, token.placement),
            MoveDestination(id(13), MoveInsertion.GAP))
        val removed = accept(state, action, 160)
        val board = removed.state!!.board
        val transition = removed.mutation!!.transition
        assertEquals(TokenMoveRead(id(13), PlayZone.LIBRARY), transition.tokenMoveRead)
        assertEquals(state.board.containers.single { it.id == id(13) }, board.containers.single { it.id == id(13) })
        assertTrue(board.cards.none { it.id == tokenId })
        assertEquals(listOf(survivor), CombatStackRules.occurrences(board.combat))
        assertEquals(area.pairs.single().id, board.combat!!.pairs.single().id)
        assertEquals(listOf(retained), board.projectionLinks)
        assertEquals(effects.map { it.copy(frozen = token) }, board.stackEffects)
        assertEquals(CombatStackRules.stackDigest(state), CombatStackRules.stackDigest(removed.state))
        val cleanup = transition.combatStack!!
        assertTrue(cleanup.pairs.isEmpty(), "Shared pair must survive")
        assertEquals(listOf(incident), cleanup.removedLinks)
        assertEquals(listOf(departing.id), cleanup.occurrences.map { it.id })
        assertNull(GameplayHistory.inverseTokenLinks(board, transition), "Never return a partial compound inverse")
        for (altered in listOf(cleanup.copy(removedLinks = emptyList()), cleanup.copy(effects = emptyList()))) {
            assertEquals("replayBlocked", GameplayHistory.replay(state,
                removed.mutation.copy(transition = transition.copy(combatStack = altered))).reason)
        }
        val changedSource = state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id == tokenId) it.copy(note = "different last live") else it
        }))
        assertEquals("replayBlocked", GameplayHistory.replay(changedSource, removed.mutation).reason)
        val frozenEdit = change(removed.state, PlayAction.SetNote(tokenId, "cannot edit frozen"), 161)
        assertEquals("rejected", frozenEdit.outcome)
        assertNull(frozenEdit.state)
        assertNull(frozenEdit.mutation)
        val afterEffect = accept(removed.state, PlayAction.Stack(StackAction.RemoveEffect(effects.first().id)), 162).state!!.board
        assertEquals(listOf(effects.last().copy(frozen = token)), afterEffect.stackEffects)
        assertEquals(listOf(retained), afterEffect.projectionLinks)
        assertEquals(token, CombatStackRules.inspection(afterEffect, effects.last().id))
        assertNull(CombatStackRules.liveSource(afterEffect, effects.last().id))
    }
}
