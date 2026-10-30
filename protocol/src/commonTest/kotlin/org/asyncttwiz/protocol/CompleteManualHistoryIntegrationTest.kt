package org.asyncttwiz.protocol

import kotlin.test.*

/** Actual merged commands, not category substitutes or the unfinished I13 board-move seam. */
class CompleteManualHistoryIntegrationTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val definition = TokenCatalog.blank
    private val face = definition.definition.faces.single().faceId
    private fun initial() = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))),
            listOf(PlayCard(id(9), definition.digest, id(7), id(10), face, covered = false, corner = "2",
                placement = PlayPlacement(BoardPosition(0, 0), id(19)))),
            listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, id(7), listOf(id(9))),
                PlayContainer(id(11), PlayZone.LIBRARY, id(7)), PlayContainer(id(12), PlayZone.HAND, id(7)),
                PlayContainer(id(13), PlayZone.EXILE, id(7)), PlayContainer(id(14), PlayZone.STACK)), TurnState(id(7))),
        listOf(definition), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    private fun accept(state: PlayState, action: PlayAction, request: Int): GameplayChange {
        val command = GameCommand(id(request), GameScope(state.gameId, playId = state.id), GameAction.Play(action), id(20), "1")
        assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
        return GameplayRules.change(state, id(7), command).also {
            assertEquals("accepted", it.outcome, "$action")
            assertEquals(it, GameplayHistory.replay(state, it.mutation!!), "$action")
            assertEquals(CommandIds.uuidV5(id(request), "board-node"), it.mutation.id)
            assertEquals(state.currentNodeId, it.mutation.parentId)
            assertEquals(state.root.branchId, it.mutation.branchId)
            assertEquals(HistoryGrouping.classify(action, state.board.containers, it.mutation.transition), it.mutation.grouping)
        }
    }

    @Test fun actualPrefixesRetainMarkersAndLiteralRepresentativesAcrossFamilies() {
        var state = initial()
        var group: GameplayGroup? = null
        val mutations = mutableListOf<GameplayMutation>()
        fun step(action: PlayAction, request: Int, joins: Boolean, category: HistoryCategory, representative: Int) {
            val previous = state.currentGroup
            val result = accept(state, action, request)
            state = result.state!!
            group = state.currentGroup!!
            assertEquals(joins, previous?.id == group!!.id, "$action")
            assertEquals(category.key, group!!.category)
            assertEquals(CommandIds.uuidV5(id(representative), "board-node"), group!!.representative!!.nodeId)
            mutations += result.mutation!!
            var folded: GameplayGroup? = null
            for (mutation in mutations) folded = GameplayHistory.append(folded, mutation)
            assertEquals(group, folded)
        }
        step(PlayAction.SetTapped(id(9), true), 100, false, HistoryCategory.T, 100)
        val tap = group!!
        step(PlayAction.InspectFace(id(9), face, id(10)), 101, true, HistoryCategory.V, 101)
        assertEquals(tap.firstNodeId, group!!.firstNodeId)
        assertEquals("2", group!!.count)
        step(PlayAction.SetNote(id(9), "after inspection"), 102, false, HistoryCategory.E, 102)
        step(PlayAction.InspectFace(id(9), face, id(10)), 103, false, HistoryCategory.V, 103)
        step(PlayAction.InspectFace(id(9), face, id(10)), 104, false, HistoryCategory.V, 104)
        step(PlayAction.CreateToken(definition), 105, false, HistoryCategory.C, 105)
        step(PlayAction.SetCorner(id(9), "3"), 106, true, HistoryCategory.C, 105)
        step(PlayAction.InspectFace(id(9), face, id(10)), 107, false, HistoryCategory.V, 107)
        step(PlayAction.RollDie(6, 4), 108, false, HistoryCategory.R, 108)
        step(PlayAction.EndTurn, 109, true, HistoryCategory.R, 108)
        step(PlayAction.Combat(CombatAction.Open), 110, true, HistoryCategory.K, 110)
        val area = state.board.combat!!.id
        step(PlayAction.Combat(CombatAction.Declare(area, id(9))), 111, true, HistoryCategory.K, 111)
        step(PlayAction.Combat(CombatAction.Close(area)), 112, false, HistoryCategory.F, 112)
        step(PlayAction.NextStep, 113, true, HistoryCategory.F, 112)
        step(PlayAction.Stack(StackAction.AddEffect(id(9), CombatStackRules.stackDigest(state))), 114, true, HistoryCategory.I, 114)
        val effect = state.board.stackEffects.single().id
        step(PlayAction.Stack(StackAction.AddEffect(id(9), CombatStackRules.stackDigest(state))), 115, false, HistoryCategory.I, 115)
        val top = CombatStackRules.stack(state.board).cards.first()
        step(PlayAction.Stack(StackAction.ReorderEffect(effect, IdentityGap(top), IdentityGap(null, top))), 116, false, HistoryCategory.L, 116)
        step(PlayAction.Stack(StackAction.ReorderEffect(effect, IdentityGap(null, top), IdentityGap(top))), 117, true, HistoryCategory.L, 116)
        step(PlayAction.Stack(StackAction.RemoveEffect(effect)), 118, false, HistoryCategory.I, 118)
        step(PlayAction.ShowContents(id(11), ContentsSelection.ALL), 119, false, HistoryCategory.V, 119)
        val display = state.board.contentsDisplays.single().id
        // Rules v29: the Search close (with its shuffle) is its own history step.
        step(PlayAction.DismissContents(display), 120, false, HistoryCategory.F, 120)
        step(PlayAction.ShuffleLibrary(id(11), RandomV1.encodeSeed(ByteArray(32))), 121, false, HistoryCategory.H, 121)
        assertEquals("layers", HistoryGrouping.classify(PlayAction.Stack(StackAction.AddEffect(id(9), CombatStackRules.stackDigest(state)))).icon.wire)
    }

    @Test fun actualTokenDisappearanceCleansBothLinkFamiliesAndFreezesLastLiveManualEdits() {
        for (move in listOf(false, true)) {
            var state = accept(initial(), PlayAction.CloneToken(id(9)), 200).state!!
            val tokenId = state.board.cards.single { it.token && it.id != id(9) }.id
            state = accept(state, PlayAction.Combat(CombatAction.Open), 201).state!!
            state = accept(state, PlayAction.Combat(CombatAction.Declare(state.board.combat!!.id, tokenId)), 202).state!!
            repeat(2) { state = accept(state, PlayAction.Stack(StackAction.AddEffect(tokenId, CombatStackRules.stackDigest(state))), 203 + it).state!! }
            state = accept(state, PlayAction.SetNote(tokenId, "last live, not Clone-time"), 205).state!!
            state = accept(state, PlayAction.SetCorner(tokenId, "9/8"), 206).state!!
            state = accept(state, PlayAction.SetCounter(CounterTarget.CARD, tokenId, CounterColor.PURPLE, "0"), 207).state!!
            state = accept(state, PlayAction.SetCovered(tokenId, true), 208).state!!
            state = accept(state, PlayAction.SetOwner(tokenId, id(8)), 209).state!!
            val player = LinkEndpoint(LinkEndpointKind.PLAYER, id(8))
            state = accept(state, PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, tokenId), player), 210).state!!
            state = accept(state, PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, id(9)), player), 211).state!!
            val token = state.board.cards.single { it.id == tokenId }
            val effects = state.board.stackEffects
            val occurrence = CombatStackRules.occurrences(state.board.combat).single()
            val incident = ProjectionLink(id(30), occurrence.id, effects[0].id)
            val sibling = ProjectionLink(id(31), effects[0].id, effects[1].id)
            // I21 has no projection-link creation action yet; this is an explicit accepted-state fixture.
            state = state.copy(board = state.board.copy(projectionLinks = listOf(incident, sibling)))
            val action = if (move) PlayAction.MoveCard(tokenId, MoveSource(token.containerId, token.placement),
                MoveDestination(id(13), MoveInsertion.UNORDERED)) else PlayAction.RemoveToken(tokenId)
            val result = accept(state, action, 212)
            val board = result.state!!.board
            val transition = result.mutation!!.transition
            assertEquals(listOf(token), transition.tokens.map { it.from!!.card })
            assertTrue(board.cards.none { it.id == tokenId })
            assertEquals(listOf(sibling), board.projectionLinks)
            assertTrue(board.combat!!.pairs.isEmpty())
            assertEquals(1, board.links.size)
            assertEquals(listOf(token, token), board.stackEffects.map { it.frozen })
            assertEquals(effects.map { it.id }, board.stackEffects.map { it.id })
            assertEquals(state.board.containers.single { it.id == id(13) }, board.containers.single { it.id == id(13) })
            assertEquals(CombatStackRules.stackDigest(state), CombatStackRules.stackDigest(result.state))
            assertEquals(listOf(incident), transition.combatStack!!.removedLinks)
            assertEquals(2, transition.combatStack.effects.size)
            assertNull(CombatStackRules.liveSource(board, effects[0].id))
            assertEquals(token, CombatStackRules.inspection(board, effects[0].id))
            assertNull(GameplayHistory.inverseTokenLinks(board, transition), "No partial inverse of compound combat cleanup")
            val changed = state.copy(board = state.board.copy(cards = state.board.cards.map {
                if (it.id == tokenId) it.copy(note = "different snapshot") else it
            }))
            assertEquals("replayBlocked", GameplayHistory.replay(changed, result.mutation).reason)
        }
    }

    @Test fun manualReplayAndLifecycleInverseRejectForeignMergedEffects() {
        val state = initial()
        val manual = accept(state, PlayAction.SetNote(id(9), "manual"), 300).mutation!!
        val token = accept(state, PlayAction.CloneToken(id(9)), 301)
        val foreign = listOf(
            GameplayTransition(libraryOrder = LibraryOrderChange(id(11), emptyList(), emptyList())),
            GameplayTransition(contents = ContentsDisplayChange(to = ContentsDisplay(id(30), id(7), id(11), ContentsSelection.ALL, 0, false, emptyList()))),
            GameplayTransition(random = GameplayRandomResult(RandomTool.DIE, "1", "6")),
            GameplayTransition(annotation = AnnotationTransition(state.id, state.root.branchId, id(30),
                ReferencedEventContext(EventReference(state.id, state.currentNodeId), "retained"), comment = "foreign")),
            GameplayTransition(combatStack = CombatStackTransition(area = CombatAreaChange(null, id(30)))))
        for (extra in foreign) {
            assertEquals("replayBlocked", GameplayHistory.replay(state, manual.copy(transition = extra.copy(fields = manual.transition.fields))).reason)
            assertNull(GameplayHistory.inverseTokenLinks(token.state!!.board, extra.copy(tokens = token.mutation!!.transition.tokens)))
        }
    }
}
