package org.asyncttwiz.protocol

import kotlin.test.*

/** Independent checks of the saved I17/I19 integration seams, not full B5 qualification. */
class ManualTokenLinkVerificationTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val definition = CardDefinition("Two faces", listOf(
        DefinitionFace(id(11), "Front", listOf(id(12)), corner = "2/3"),
        DefinitionFace(id(12), "Back", listOf(id(11)), corner = "4/5")))
    private val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
    private fun initial() = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))),
            listOf(PlayCard(id(9), submitted.digest, id(8), id(10), id(11), corner = "custom", note = "keep",
                placement = PlayPlacement(BoardPosition(0, 0), id(13)))),
            listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, id(7), listOf(id(9))),
                PlayContainer(id(14), PlayZone.EXILE, id(8))), TurnState(id(7))),
        listOf(submitted), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    private fun change(state: PlayState, action: PlayAction, request: Int) = GameplayRules.change(state, id(7),
        GameCommand(id(request), GameScope(id(1), playId = id(2)), GameAction.Play(action), id(19), "1"))
    private fun accept(state: PlayState, action: PlayAction, request: Int) = change(state, action, request).also {
        assertEquals("accepted", it.outcome)
        assertEquals(it, GameplayHistory.replay(state, it.mutation!!))
        assertEquals(CommandIds.uuidV5(id(request), "board-node"), it.mutation.id)
        assertEquals(state.root.branchId, it.mutation.branchId)
        assertEquals(id(request), it.mutation.requestId)
    }
    private fun card(id: String) = LinkEndpoint(LinkEndpointKind.CARD, id)
    private val player = LinkEndpoint(LinkEndpointKind.PLAYER, id(8))

    @Test fun nullableTuplesAndResetReplayPreserveIndependentCurrentFields() {
        val original = initial()
        for (target in CounterTarget.entries) {
            val targetId = if (target == CounterTarget.CARD) id(9) else id(8)
            val added = accept(original, PlayAction.AdjustCounter(target, targetId, CounterColor.RED, "1"), 30)
            assertEquals(listOf(GameplayFieldChange(targetId,
                if (target == CounterTarget.CARD) GameplayField.COUNTER else GameplayField.PLAYER_COUNTER,
                null, "1", CounterColor.RED)), added.mutation!!.transition.fields)
            val independent = original.copy(board = original.board.copy(
                cards = original.board.cards.map { it.copy(note = "remote") },
                players = original.board.players.map { it.copy(life = "17") }))
            val replay = GameplayHistory.replay(independent, added.mutation)
            assertEquals(added.mutation, replay.mutation)
            assertEquals("remote", replay.state!!.board.cards.single().note)
            assertTrue(replay.state.board.players.all { it.life == "17" })
            val zero = accept(original, PlayAction.SetCounter(target, targetId, CounterColor.RED, "0"), 31)
            assertEquals("replayBlocked", GameplayHistory.replay(zero.state!!, added.mutation).reason)
            val removed = accept(added.state!!, PlayAction.SetCounter(target, targetId, CounterColor.RED, " "), 32)
            assertEquals("1", removed.mutation!!.transition.fields.single().from)
            assertNull(removed.mutation.transition.fields.single().to)
            assertEquals(original.board, removed.state!!.board)
        }
        val flipped = accept(original, PlayAction.SetFace(id(9), id(12)), 33).state!!
        val reset = accept(flipped, PlayAction.ResetCorner(id(9)), 34)
        assertEquals(flipped.board.cards.single().copy(corner = "4/5"), reset.state!!.board.cards.single())
        assertEquals(listOf(GameplayRead(id(9), GameplayField.FACE, id(12))), reset.mutation!!.transition.reads)
        val retainedReset = GameplayHistory.replay(flipped.copy(definitions = emptyList()), reset.mutation)
        assertEquals(reset.state.board, retainedReset.state!!.board)
        assertEquals(reset.mutation, retainedReset.mutation)
        assertEquals("replayBlocked", GameplayHistory.replay(original, reset.mutation).reason)
    }

    @Test fun currentSourceCloneAndCreationCustomizationKeepOriginalIdentity() {
        val created = accept(initial(), PlayAction.CreateToken(TokenCatalog.blank), 40)
        val token = created.state!!.board.cards.single { it.token }
        val edited = accept(created.state, PlayAction.SetNote(token.id, " current source "), 41).state!!
        val clone = accept(edited, PlayAction.CloneToken(token.id), 42)
        val output = clone.state!!.board.cards.single { it.token && it.id != token.id }
        assertEquals(CommandIds.uuidV5(id(42), "token/0"), output.id)
        assertEquals(CommandIds.uuidV5(id(42), "pile/0"), output.placement!!.pileId)
        assertEquals("current source", output.note)
        assertEquals(TokenCatalog.blank, clone.mutation!!.transition.cloneRead!!.definition)
        val customized = accept(clone.state, PlayAction.SetCounter(CounterTarget.CARD, output.id, CounterColor.BLUE, "0"), 43)
        assertEquals(created.state.currentGroup!!.id, customized.state!!.currentGroup!!.id)
        assertEquals("creation", customized.state.currentGroup.category)
        assertEquals("4", customized.state.currentGroup.count)
        val changed = edited.copy(board = edited.board.copy(cards = edited.board.cards.map {
            if (it.id == token.id) it.copy(note = "later source") else it
        }))
        assertEquals("later source", change(changed, PlayAction.CloneToken(token.id), 44).state!!.board.cards.single {
            it.id == CommandIds.uuidV5(id(44), "token/0")
        }.note)
        assertEquals("replayBlocked", GameplayHistory.replay(changed, clone.mutation).reason)
        val ownerOnly = edited.copy(board = edited.board.copy(cards = edited.board.cards.map {
            if (it.id == token.id) it.copy(owner = id(8)) else it
        }))
        assertEquals(clone.mutation, GameplayHistory.replay(ownerOnly, clone.mutation).mutation)
        assertEquals(edited.board, GameplayHistory.inverseTokenLinks(clone.state.board, clone.mutation.transition))
        assertEquals("missingReference", change(initial(), PlayAction.CloneToken(token.id), 45).reason)
    }

    @Test fun inspectionRetainsFaceRecipientAndRegionAndSeparatesCreationAndEdits() {
        val created = accept(initial(), PlayAction.CreateToken(TokenCatalog.blank), 50).state!!
        val inspected = accept(created, PlayAction.InspectFace(id(9), id(12), id(10)), 51)
        assertEquals(created.board, inspected.state!!.board)
        assertEquals(listOf(GameplayDisclosure(id(9), id(12), id(10), listOf(id(7)))),
            inspected.mutation!!.transition.disclosures)
        assertNotEquals(created.currentGroup!!.id, inspected.state.currentGroup!!.id)
        val again = accept(inspected.state, PlayAction.InspectFace(id(9), id(12), id(10)), 52).state!!
        assertNotEquals(inspected.state.currentGroup.id, again.currentGroup!!.id)
        val edited = accept(again, PlayAction.SetNote(id(9), "after viewing"), 53).state!!
        assertNotEquals(again.currentGroup.id, edited.currentGroup!!.id)
        val moved = created.copy(board = created.board.copy(cards = created.board.cards.map {
            if (it.id == id(9)) it.copy(containerId = id(14), placement = null) else it
        }, containers = created.board.containers.map {
            if (it.id == id(10)) it.copy(cards = it.cards - id(9)) else it.copy(cards = listOf(id(9)))
        }))
        assertEquals("replayBlocked", GameplayHistory.replay(moved, inspected.mutation).reason)
    }

    @Test fun cleanupAndLinkInversePreserveUnrelatedEditsAndLegitimateMoveRead() {
        val cloned = accept(initial(), PlayAction.CloneToken(id(9)), 60).state!!
        val token = cloned.board.cards.single { it.token }
        val linked = accept(cloned, PlayAction.Link(card(token.id), player), 61).state!!
        val other = accept(linked, PlayAction.Link(card(id(9)), player), 62).state!!
        assertEquals("noOp", change(other, PlayAction.Link(player, card(token.id)), 63).outcome)
        for (action in listOf(PlayAction.RemoveToken(token.id), PlayAction.MoveCard(token.id,
            MoveSource(token.containerId, token.placement), MoveDestination(id(14), MoveInsertion.UNORDERED)))) {
            val removed = accept(other, action, 64)
            assertEquals(listOf(token), removed.mutation!!.transition.tokens.map { it.from!!.card })
            assertEquals(1, removed.mutation.transition.links.size)
            assertEquals(other.board.links.filterNot { it.first == card(token.id) || it.second == card(token.id) }, removed.state!!.board.links)
            assertEquals(other.board.containers.single { it.id == id(14) }, removed.state.board.containers.single { it.id == id(14) })
            val independent = removed.state.board.copy(cards = removed.state.board.cards.map { it.copy(note = "remote") },
                players = removed.state.board.players.map { it.copy(life = "13") })
            assertEquals(other.board.copy(cards = other.board.cards.map { if (it.token) it else it.copy(note = "remote") },
                players = other.board.players.map { it.copy(life = "13") }),
                GameplayHistory.inverseTokenLinks(independent, removed.mutation.transition))
            if (action is PlayAction.MoveCard) assertEquals(TokenMoveRead(id(14), PlayZone.EXILE), removed.mutation.transition.tokenMoveRead)
        }
        val link = other.board.links.single { it.first != card(token.id) && it.second != card(token.id) }
        val unlinked = accept(other, PlayAction.RemoveLink(link.id), 65)
        assertEquals(other.board, GameplayHistory.inverseTokenLinks(unlinked.state!!.board, unlinked.mutation!!.transition))
    }

    @Test fun familyGuardsRejectEveryUnsupportedEffectWithoutApplyingPartialWork() {
        val original = initial()
        val manual = accept(original, PlayAction.SetNote(id(9), "edited"), 70).mutation!!
        val clone = accept(original, PlayAction.CloneToken(id(9)), 71).mutation!!
        val linked = accept(original, PlayAction.Link(card(id(9)), player), 72)
        val inspection = accept(original, PlayAction.InspectFace(id(9), id(12), id(10)), 73).mutation!!
        for (transition in listOf(manual.transition.copy(tokens = clone.transition.tokens),
            manual.transition.copy(links = linked.mutation!!.transition.links),
            manual.transition.copy(cloneRead = clone.transition.cloneRead),
            manual.transition.copy(tokenMoveRead = TokenMoveRead(id(14), PlayZone.EXILE)),
            manual.transition.copy(movement = GameplayMoveChange(id(9), CardLocation(id(10)), CardLocation(id(14)))))) {
            val rejected = GameplayHistory.replay(original, manual.copy(transition = transition))
            assertEquals("replayBlocked", rejected.reason)
            assertNull(rejected.state)
        }
        for (transition in listOf(linked.mutation!!.transition.copy(reads = inspection.transition.reads),
            linked.mutation.transition.copy(disclosures = inspection.transition.disclosures),
            linked.mutation.transition.copy(fields = manual.transition.fields))) {
            assertNull(GameplayHistory.inverseTokenLinks(linked.state!!.board, transition))
        }
    }

    @Test fun explicitInspectionAnchorsPendingTapRunUnderCurrentGroupingContract() {
        // history-grouping.md rules 2/3/5; grouping-matrix.md T -> V is '+', not '|'.
        val tapped = accept(initial(), PlayAction.SetTapped(id(9), true), 80).state!!
        val inspected = accept(tapped, PlayAction.InspectFace(id(9), id(12), id(10)), 81).state!!
        assertEquals(tapped.currentGroup!!.id, inspected.currentGroup!!.id,
            "An explicit inspection must anchor the pending tap-only segment, not start an extra playback step")
        assertEquals("view", inspected.currentGroup.category)
        assertEquals("2", inspected.currentGroup.count)
        val edited = accept(inspected, PlayAction.SetNote(id(9), "next edit"), 82).state!!
        assertNotEquals(inspected.currentGroup.id, edited.currentGroup!!.id)
    }
}
