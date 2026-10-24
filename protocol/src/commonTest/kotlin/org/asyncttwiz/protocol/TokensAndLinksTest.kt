package org.asyncttwiz.protocol

import kotlin.test.*

class TokensAndLinksTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val definition = CardDefinition("Printed card", listOf(DefinitionFace(id(11), "Front", listOf(id(12)), corner = "1/2"),
        DefinitionFace(id(12), "Back", listOf(id(11)), corner = "3/4")))
    private val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
    private fun cardEndpoint(n: Int) = LinkEndpoint(LinkEndpointKind.CARD, id(n))
    private fun playerEndpoint(n: Int) = LinkEndpoint(LinkEndpointKind.PLAYER, id(n))
    private fun state(): PlayState = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))),
            listOf(PlayCard(id(9), submitted.digest, id(8), id(10), id(12), covered = true, tapped = true,
                corner = "5/2", counters = mapOf(CounterColor.BLUE to "0"), note = "current note",
                placement = PlayPlacement(BoardPosition(0, 0), id(13)), explicitlyCovered = true)),
            listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, id(7), listOf(id(9))),
                PlayContainer(id(14), PlayZone.LIBRARY, id(7)), PlayContainer(id(15), PlayZone.GRAVEYARD, id(8)),
                PlayContainer(id(16), PlayZone.EXILE, id(7)), PlayContainer(id(17), PlayZone.HAND, id(8))), TurnState(id(7))),
        listOf(submitted), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    private fun command(action: PlayAction, n: Int = 20) = GameCommand(id(n), GameScope(id(1), playId = id(2)),
        GameAction.Play(action), id(18), "1")
    private fun apply(state: PlayState, action: PlayAction, n: Int = 20, actor: String = id(7)): GameplayChange =
        GameplayRules.change(state, actor, command(action, n))
    private fun assertReplayAndInverse(original: PlayState, change: GameplayChange) {
        assertEquals("accepted", change.outcome)
        assertEquals(change, GameplayHistory.replay(original, change.mutation!!))
        assertEquals(original.board, GameplayHistory.inverseTokenLinks(change.state!!.board, change.mutation.transition))
        val encoded = GameWire.json.encodeToString(GameplayMutation.serializer(), change.mutation)
        assertEquals(change.mutation, GameWire.json.decodeFromString(GameplayMutation.serializer(), encoded))
    }

    @Test fun catalogAndBlankRetainDefinitionsAndUseRequestIdentity() {
        val original = state()
        val catalog = definition.copy(token = true, name = "Soldier")
        for (choice in listOf(TokenCatalog.blank, SubmittedDefinition(DeckJson.digest(catalog), catalog))) {
            val action = PlayAction.CreateToken(choice)
            val cmd = command(action)
            assertTrue(GameWire.valid(cmd))
            assertEquals(cmd, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(cmd)))
            val result = apply(original, action)
            assertReplayAndInverse(original, result)
            val token = result.state!!.board.cards.single { it.token }
            assertEquals(CommandIds.uuidV5(id(20), "token/0"), token.id)
            assertEquals(CommandIds.uuidV5(id(20), "pile/0"), token.placement!!.pileId)
            assertEquals(BoardPosition(1, 0), token.placement.position)
            assertEquals(choice.digest, token.definition)
            assertEquals(choice.definition.faces.first().faceId, token.faceId)
            assertEquals(choice.definition.faces.first().corner, token.corner)
            assertEquals(id(7), token.owner)
            assertFalse(token.covered); assertFalse(token.tapped)
            assertTrue(token.counters.isEmpty()); assertEquals("", token.note)
            assertEquals(choice, result.state.definitions.single { it.digest == token.definition })
            assertEquals("inapplicable", apply(result.state, action).reason) // ingress owns retained request dedup
        }
        assertEquals("invalidInput", apply(original, PlayAction.CreateToken(submitted)).reason)
        assertEquals("invalidInput", apply(original, PlayAction.CreateToken(TokenCatalog.blank.copy(digest = submitted.digest))).reason)
        assertEquals("accessDenied", apply(original, PlayAction.CreateToken(TokenCatalog.blank), actor = id(99)).reason)
    }

    @Test fun cloneReadsLiveFieldsNotPredictionAndDoesNotReadSourceLocationOrOwner() {
        val original = state()
        val linked = apply(original, PlayAction.Link(cardEndpoint(9), playerEndpoint(8)), 21).state!!
        val clone = apply(linked, PlayAction.CloneToken(id(9)))
        assertReplayAndInverse(linked, clone)
        val source = linked.board.cards.single()
        val token = clone.state!!.board.cards.single { it.token }
        assertEquals(source.copy(id = token.id, owner = id(7), token = true, placement = token.placement), token)
        assertEquals(linked.board.links, clone.state.board.links) // never copies incident links
        assertEquals(source, clone.state.board.cards.single { !it.token })
        assertEquals(submitted, clone.mutation!!.transition.cloneRead!!.definition)
        val edited = linked.copy(board = linked.board.copy(cards = listOf(source.copy(corner = "7/8", note = "new note"))))
        val liveClone = apply(edited, PlayAction.CloneToken(id(9)))
        assertEquals("7/8", liveClone.state!!.board.cards.single { it.token }.corner)
        assertEquals("new note", liveClone.state.board.cards.single { it.token }.note)
        assertEquals("replayBlocked", GameplayHistory.replay(edited, clone.mutation).reason)
        val moved = linked.copy(board = linked.board.copy(cards = listOf(source.copy(containerId = id(17), placement = null, owner = id(7))),
            containers = linked.board.containers.map { when (it.id) {
                id(10) -> it.copy(cards = emptyList()); id(17) -> it.copy(cards = listOf(source.id)); else -> it
            } }))
        // Source movement alone does not lock Clone. Its automatic output cell may legitimately change.
        val replay = GameplayHistory.replay(moved, clone.mutation)
        assertEquals("accepted", replay.outcome) // Retained replay uses the recorded cell, not a newly chosen default.
        assertEquals("accepted", apply(moved, PlayAction.CloneToken(id(9))).outcome)
        val occupiedCell = moved.copy(board = moved.board.copy(cards = moved.board.cards +
            source.copy(id = id(30), placement = source.placement), containers = moved.board.containers.map {
                if (it.id == id(10)) it.copy(cards = listOf(id(30))) else it
            }))
        assertEquals(clone.mutation.transition.copy(delta = null),
            apply(occupiedCell, PlayAction.CloneToken(id(9))).mutation!!.transition.copy(delta = null))
        assertEquals("accepted", GameplayHistory.replay(occupiedCell, clone.mutation).outcome)
        assertEquals("missingReference", apply(original, PlayAction.CloneToken(id(99))).reason)
    }

    @Test fun cloneTokenAlsoCreatesIndependentTokenAndRetainsDefaults() {
        val initial = apply(state(), PlayAction.CreateToken(TokenCatalog.blank)).state!!
        val source = initial.board.cards.single { it.token }
        val changed = initial.copy(board = initial.board.copy(cards = initial.board.cards.map {
            if (it.id == source.id) it.copy(corner = "custom", note = "blank note", counters = mapOf(CounterColor.RED to "-1")) else it
        }))
        val clone = apply(changed, PlayAction.CloneToken(source.id), 22)
        assertReplayAndInverse(changed, clone)
        assertEquals(2, clone.state!!.board.cards.count { it.token })
        val output = clone.state.board.cards.single { it.id != source.id && it.token }
        assertEquals(source.definition, output.definition)
        assertEquals("custom", output.corner)
        assertEquals("", clone.state.definitions.single { it.digest == output.definition }.definition.faces.first().corner)
    }

    @Test fun uniqueUndirectedLinkNoOpAndExactRemovalInverse() {
        val original = state()
        val created = apply(original, PlayAction.Link(cardEndpoint(9), playerEndpoint(8)))
        assertReplayAndInverse(original, created)
        val link = created.state!!.board.links.single()
        assertEquals(CommandIds.uuidV5(id(20), "link/0"), link.id)
        val again = apply(created.state, PlayAction.Link(playerEndpoint(8), cardEndpoint(9)), 21)
        assertEquals("noOp", again.outcome); assertNull(again.mutation)
        assertEquals(created.state, again.state)
        val removed = apply(created.state, PlayAction.RemoveLink(link.id), 22)
        assertReplayAndInverse(created.state, removed)
        assertEquals(original.board.cards, removed.state!!.board.cards)
        assertEquals("noOp", apply(removed.state, PlayAction.RemoveLink(link.id), 23).outcome)
        assertEquals("inapplicable", apply(original, PlayAction.Link(cardEndpoint(9), cardEndpoint(9))).reason)
        assertEquals("missingReference", apply(original, PlayAction.Link(cardEndpoint(9), cardEndpoint(99))).reason)
        assertEquals("missingReference", apply(original, PlayAction.Link(cardEndpoint(9), playerEndpoint(9))).reason)
        val edited = original.copy(board = original.board.copy(cards = original.board.cards.map { it.copy(tapped = false, note = "irrelevant") }))
        assertEquals(created.mutation!!.transition, GameplayHistory.replay(edited, created.mutation).mutation!!.transition)
    }

    @Test fun removeTokenCleansIncidentLinksAndNeverDeletesRealCard() {
        assertEquals("inapplicable", apply(state(), PlayAction.RemoveToken(id(9))).reason)
        var initial = apply(state(), PlayAction.CloneToken(id(9))).state!!
        val token = initial.board.cards.single { it.token }
        initial = apply(initial, PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, token.id), cardEndpoint(9)), 21).state!!
        initial = apply(initial, PlayAction.Link(cardEndpoint(9), playerEndpoint(8)), 22).state!!
        val removed = apply(initial, PlayAction.RemoveToken(token.id), 23)
        assertReplayAndInverse(initial, removed)
        assertEquals(1, removed.mutation!!.transition.links.size)
        assertEquals(token, removed.mutation.transition.tokens.single().from!!.card)
        assertEquals(1, removed.state!!.board.links.size)
        assertEquals(initial.board.cards.single { !it.token }, removed.state.board.cards.single())
        assertTrue(removed.state.definitions.any { it.digest == token.definition })
        val changedToken = initial.copy(board = initial.board.copy(cards = initial.board.cards.map {
            if (it.token) it.copy(note = "changed before remove") else it
        }))
        assertEquals("replayBlocked", GameplayHistory.replay(changedToken, removed.mutation).reason)
        val endpointGone = removed.state.copy(board = removed.state.board.copy(cards = emptyList()))
        assertNull(GameplayHistory.inverseTokenLinks(endpointGone.board, removed.mutation.transition))
    }

    @Test fun disappearanceForEveryConfiguredDestinationDoesNotOccupyIt() {
        val initial = apply(state(), PlayAction.CloneToken(id(9))).state!!
        val token = initial.board.cards.single { it.token }
        for ((container, insertion) in listOf(14 to MoveInsertion.TOP, 14 to MoveInsertion.BOTTOM,
            14 to MoveInsertion.GAP, 15 to MoveInsertion.UNORDERED, 16 to MoveInsertion.UNORDERED)) {
            val move = PlayAction.MoveCard(token.id, MoveSource(token.containerId, token.placement), MoveDestination(id(container), insertion))
            val gone = apply(initial, move, 24)
            assertReplayAndInverse(initial, gone)
            assertFalse(gone.state!!.board.cards.any { it.id == token.id })
            assertEquals(initial.board.containers.single { it.id == id(container) }, gone.state.board.containers.single { it.id == id(container) })
            assertEquals(TokenMoveRead(id(container), initial.board.containers.single { it.id == id(container) }.zone), gone.mutation!!.transition.tokenMoveRead)
            // Full movement validates the captured placement before token disappearance.
            assertEquals("movementConflict", apply(initial, move.copy(source = move.source.copy(placement = null)), 25).reason)
        }
        val bad = PlayAction.MoveCard(token.id, MoveSource(token.containerId, token.placement), MoveDestination(id(14), MoveInsertion.GAP, afterId = id(99)))
        assertEquals("inapplicable", apply(initial, bad).reason)
    }

    @Test fun capturedLifecycleInverseKeepsUnrelatedEditsAndCreationGrouping() {
        val initial = state()
        val created = apply(initial, PlayAction.CreateToken(TokenCatalog.blank))
        val afterLife = apply(created.state!!, PlayAction.SetLife(id(8), "11"), 21)
        assertEquals("creation", afterLife.state!!.currentGroup!!.category)
        val another = apply(afterLife.state, PlayAction.CloneToken(id(9)), 22)
        assertEquals("3", another.state!!.currentGroup!!.count)
        val restored = GameplayHistory.inverseTokenLinks(afterLife.state.board, created.mutation!!.transition)!!
        assertEquals("11", restored.players.single { it.accountId == id(8) }.life)
        assertEquals(initial.board.cards, restored.cards)
        val otherAuthor = apply(created.state, PlayAction.CloneToken(id(9)), 23, id(8))
        assertEquals("missingReference", otherAuthor.reason) // no actor battlefield in this bounded fixture
        val changedOutput = created.state.board.copy(cards = created.state.board.cards.map { if (it.token) it.copy(note = "edited") else it })
        assertNull(GameplayHistory.inverseTokenLinks(changedOutput, created.mutation.transition))
    }
}
