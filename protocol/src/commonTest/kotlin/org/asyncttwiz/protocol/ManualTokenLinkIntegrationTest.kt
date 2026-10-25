package org.asyncttwiz.protocol

import kotlin.test.*

class ManualTokenLinkIntegrationTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val definition = CardDefinition("Token", listOf(DefinitionFace(id(11), "Front", listOf(id(12)), corner = "1/2"),
        DefinitionFace(id(12), "Back", listOf(id(11)), corner = "3/4")), token = true)
    private val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
    private fun endpoint(cardId: String) = LinkEndpoint(LinkEndpointKind.CARD, cardId)
    private val playerEndpoint = LinkEndpoint(LinkEndpointKind.PLAYER, id(8))
    private fun initial() = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))),
            listOf(PlayCard(id(9), submitted.digest, id(8), id(10), id(11), token = false, note = "unrelated",
                placement = PlayPlacement(BoardPosition(0, 0), id(13)))),
            listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, id(7), listOf(id(9)))), TurnState(id(7))),
        listOf(submitted), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    private fun change(state: PlayState, action: PlayAction, n: Int) = GameplayRules.change(state, id(7),
        GameCommand(id(n), GameScope(id(1), playId = id(2)), GameAction.Play(action), id(18), "1"))

    @Test fun mixedAcceptedJourneyReplaysOriginalIdentitiesAndPreservesIndependentFields() {
        val original = initial()
        var current = original
        val mutations = mutableListOf<GameplayMutation>()
        fun accept(action: PlayAction): GameplayMutation {
            val result = change(current, action, 30 + mutations.size)
            assertEquals("accepted", result.outcome)
            val mutation = result.mutation!!
            assertEquals(result, GameplayHistory.replay(current, mutation))
            assertEquals(mutation, GameWire.json.decodeFromString(GameplayMutation.serializer(),
                GameWire.json.encodeToString(GameplayMutation.serializer(), mutation)))
            mutations += mutation
            current = result.state!!
            return mutation
        }

        val creation = accept(PlayAction.CreateToken(submitted))
        val tokenId = CommandIds.uuidV5(creation.requestId, "token/0")
        assertEquals(tokenId, creation.transition.tokens.single().to!!.card.id)
        accept(PlayAction.SetCorner(tokenId, " 7/8 "))
        val counter = accept(PlayAction.AdjustCounter(CounterTarget.CARD, tokenId, CounterColor.BLUE, "1"))
        assertEquals(listOf(GameplayFieldChange(tokenId, GameplayField.COUNTER, null, "1", CounterColor.BLUE)),
            counter.transition.fields)
        accept(PlayAction.SetNote(tokenId, " current token note "))
        accept(PlayAction.SetFace(tokenId, id(12)))
        val beforeClone = current
        val source = current.board.cards.single { it.id == tokenId }
        val clone = accept(PlayAction.CloneToken(tokenId))
        val cloneId = CommandIds.uuidV5(clone.requestId, "token/0")
        val cloned = current.board.cards.single { it.id == cloneId }
        assertEquals(source.copy(id = cloneId, placement = cloned.placement), cloned)
        assertEquals(submitted, clone.transition.cloneRead!!.definition)
        val changedSource = beforeClone.copy(board = beforeClone.board.copy(cards = beforeClone.board.cards.map {
            if (it.id == tokenId) it.copy(note = "different relevant input") else it
        }))
        assertEquals("replayBlocked", GameplayHistory.replay(changedSource, clone).reason)

        val cardLink = accept(PlayAction.Link(endpoint(tokenId), endpoint(id(9)))).transition.links.single().to!!
        val playerLink = accept(PlayAction.Link(endpoint(tokenId), playerEndpoint)).transition.links.single().to!!
        val unrelatedLink = accept(PlayAction.Link(endpoint(cloneId), playerEndpoint)).transition.links.single().to!!
        val beforeReset = current
        val reset = accept(PlayAction.ResetCorner(cloneId))
        assertEquals(listOf(GameplayRead(cloneId, GameplayField.FACE, id(12))), reset.transition.reads)
        assertEquals("3/4", current.board.cards.single { it.id == cloneId }.corner)
        assertEquals(beforeReset.board.links, current.board.links)
        val noDefaults = GameplayHistory.replay(beforeReset.copy(definitions = emptyList()), reset)
        assertEquals("replayBlocked", noDefaults.reason)
        val clear = accept(PlayAction.SetCounter(CounterTarget.CARD, cloneId, CounterColor.BLUE, " "))
        assertEquals(listOf(GameplayFieldChange(cloneId, GameplayField.COUNTER, "1", null, CounterColor.BLUE)),
            clear.transition.fields)
        assertEquals("noOp", change(current, PlayAction.Link(playerEndpoint, endpoint(cloneId)), 90).outcome)

        val beforeRemove = current
        val removed = accept(PlayAction.RemoveToken(tokenId))
        assertEquals(source, removed.transition.tokens.single().from!!.card)
        assertEquals(setOf(cardLink, playerLink), removed.transition.links.map { it.from }.toSet())
        assertEquals(listOf(unrelatedLink), current.board.links)
        assertFalse(current.board.cards.any { it.id == tokenId })
        assertEquals(beforeRemove.board, GameplayHistory.inverseTokenLinks(current.board, removed.transition))
        accept(PlayAction.RemoveLink(unrelatedLink.id))
        assertTrue(current.board.links.isEmpty())
        assertEquals(original.board.cards.single(), current.board.cards.single { it.id == id(9) })
        assertEquals(setOf(id(9), cloneId), current.board.cards.map { it.id }.toSet())

        var replayed = original.copy(board = original.board.copy(
            cards = original.board.cards.map { it.copy(note = "independent remote note") },
            players = original.board.players.map { it.copy(life = "17") }))
        for (mutation in mutations) {
            val result = GameplayHistory.replay(replayed, mutation)
            assertEquals("accepted", result.outcome)
            assertEquals(mutation, result.mutation) // includes original request, node, branch and endpoint IDs
            replayed = result.state!!
        }
        assertEquals(current.copy(board = current.board.copy(
            cards = current.board.cards.map { if (it.id == id(9)) it.copy(note = "independent remote note") else it },
            players = current.board.players.map { it.copy(life = "17") })), replayed)
    }

    @Test fun creationCustomizationAndInspectionKeepBothGroupingRules() {
        val created = change(initial(), PlayAction.CreateToken(submitted), 60).state!!
        val token = created.board.cards.single { it.token }
        val edited = change(created, PlayAction.SetCorner(token.id, "custom"), 61).state!!
        assertEquals(created.currentGroup!!.id, edited.currentGroup!!.id)
        assertEquals("creation", edited.currentGroup.category)
        val inspected = change(edited, PlayAction.InspectFace(token.id, id(12), id(10)), 62)
        assertEquals(edited.board, inspected.state!!.board)
        assertEquals(listOf(GameplayDisclosure(token.id, id(12), id(10), listOf(id(7)))),
            inspected.mutation!!.transition.disclosures)
        assertEquals(inspected, GameplayHistory.replay(edited, inspected.mutation))
        assertNotEquals(edited.currentGroup.id, inspected.state.currentGroup!!.id)
        assertEquals("view", inspected.state.currentGroup.category)
        val next = change(inspected.state, PlayAction.SetNote(token.id, "after inspection"), 63).state!!
        assertNotEquals(inspected.state.currentGroup.id, next.currentGroup!!.id)
        assertEquals("edit", next.currentGroup.category)
    }

    @Test fun familySpecificReplayAndInverseNeverIgnoreOtherTransitionEffects() {
        val original = initial()
        val manual = change(original, PlayAction.SetCounter(CounterTarget.CARD, id(9), CounterColor.RED, "0"), 70).mutation!!
        val token = change(original, PlayAction.CreateToken(TokenCatalog.blank), 71).mutation!!
        val link = change(original, PlayAction.Link(endpoint(id(9)), playerEndpoint), 72)
        val inspected = change(original, PlayAction.InspectFace(id(9), id(12), id(10)), 73).mutation!!
        for (transition in listOf(manual.transition.copy(tokens = token.transition.tokens),
            manual.transition.copy(links = link.mutation!!.transition.links),
            manual.transition.copy(cloneRead = CloneRead(id(9), submitted, id(11), true, false, "", emptyMap(), "")),
            manual.transition.copy(tokenMoveRead = TokenMoveRead(id(10), PlayZone.BATTLEFIELD)))) {
            assertEquals("replayBlocked", GameplayHistory.replay(original, manual.copy(transition = transition)).reason)
        }
        val transition = link.mutation!!.transition
        assertNull(GameplayHistory.inverseTokenLinks(link.state!!.board, transition.copy(reads = inspected.transition.reads)))
        assertNull(GameplayHistory.inverseTokenLinks(link.state.board, transition.copy(disclosures = inspected.transition.disclosures)))
    }
}
