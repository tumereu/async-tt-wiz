package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v36, I0 review decisions (integration spec I-D12–I-D26): turn changes naming the player, ability-only Stack
 * entries, automated batch limits, the switch's refusals and stamps, the per-player skip setting, reverting a flow,
 * no branches during a flow, clearing Automatum data, and batches planned on another board. */
class AutomatumFlowTest {
    private val a = gameplayId(7)
    private val b = gameplayId(8)
    private val hand = gameplayId(21)
    private val field = gameplayId(22)
    private var request = 900

    private fun base(): PlayState = gameplayFixture().withBattlefieldCards(gameplayId(10), gameplayId(11))
    /** [base] with Automatum on: automated (tagged) events apply only then (SYS-057). */
    private fun on(): PlayState = base().let { it.copy(board = it.board.copy(automatum = true)) }
    private fun card(state: PlayState, n: Int) = state.board.cards.single { it.id == gameplayId(n) }
    private fun command(action: GameAction) = GameCommand(gameplayId(request++), GameScope(gameplayId(1), playId = gameplayId(2)),
        action, gameplayId(80), "1")
    private fun change(state: PlayState, action: PlayAction, actor: String = a, tag: MtgTag? = null) =
        GameplayRules.change(state, actor, command(GameAction.Play(action, tag)))
    private fun accept(state: PlayState, action: PlayAction, actor: String = a): PlayState {
        val change = change(state, action, actor)
        assertEquals("accepted", change.outcome, "$action: ${change.reason}")
        assertEquals(state.board, GameplayHistory.reverse(change.state!!, change.mutation!!), "reverts exactly")
        return change.state
    }
    private fun play(tree: GameplayTree, actor: String, action: PlayAction) =
        assertNotNull(HistoryContinuation.change(tree, actor, command(GameAction.Play(action))).tree, "$action")
    private fun head(tree: GameplayTree) = tree.stateAt(tree.currentNodeId)!!
    private fun mtg(action: MtgAction) = PlayAction.Mtg(action)
    private val open = EngineRecord(Automatum.CONTINUATION_ID, EngineRecordKind.Continuation, data = """{"step":1}""")
    private val effect = EngineRecord("effect-1", EngineRecordKind.Effect, 3, """{"until":"end"}""")

    // TRN-052 (I-D12, CR 500.7): the turn change names whose turn begins; the same player takes an extra turn
    @Test fun beginTurnNamesWhoseTurnItIs() {
        val state = base()
        val extra = accept(state, PlayAction.BeginTurn(a))
        assertEquals(TurnState(a, "2"), extra.board.turn)
        assertEquals(a, extra.board.effectiveAttentionHolder)
        val next = accept(extra, PlayAction.BeginTurn(b))
        assertEquals(TurnState(b, "3"), next.board.turn)
        assertEquals("missingReference", change(state, PlayAction.BeginTurn(gameplayId(99))).reason)
        assertFalse(GameplayRules.valid(PlayAction.BeginTurn("not-an-id")))
    }

    // STK-022 (I-D13): an ability-only entry shows its own card data (name and the ability's text) as a card already gone
    @Test fun abilityOnlyStackEntryShowsItsText() {
        val state = base()
        val face = DefinitionFace(gameplayId(60), "Ghostly Pyre", typeLine = "Ability", rulesText = "At the beginning of the next end step, draw a card.")
        val definition = CardDefinition("Ghostly Pyre", listOf(face), token = true).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val added = accept(state, PlayAction.Stack(StackAction.AddAbility(definition, b, GameplayRules.stackDigest(state))))
        val entry = added.board.stackEffects.single()
        assertEquals(listOf(entry.id), CombatStackRules.stack(added.board).cards)
        val shown = assertNotNull(CombatStackRules.inspection(added.board, entry.id))
        assertEquals(definition.digest, shown.definition)
        assertNull(CombatStackRules.liveSource(added.board, entry.id), "nothing on the table to move or point at")
        assertEquals(b, CombatStackRules.controller(added.board, entry.id))
        assertTrue(added.definitions.any { it == definition })
        // It leaves like any entry; the Stack guard refuses an entry planned on another Stack
        val removed = accept(added, PlayAction.Stack(StackAction.RemoveEffect(entry.id)))
        assertTrue(removed.board.stackEffects.isEmpty())
        assertEquals("stackChanged", change(added, PlayAction.Stack(StackAction.AddAbility(definition, b, GameplayRules.stackDigest(state)))).reason)
    }

    // SYS-061 (I-D14): an automated batch (members with tags) may have up to 1000 members; one a player makes, 100
    @Test fun automatedBatchesMayBeBigger() {
        val tap = PlayAction.SetTapped(gameplayId(10), true)
        val tag = MtgTag(listOf("tapped"))
        fun valid(size: Int, tagged: Boolean) = GameWire.valid(command(GameAction.PlayBatch(List(size) { tap },
            if (tagged) listOf(tag) else emptyList())))
        assertTrue(valid(100, false)); assertFalse(valid(101, false))
        assertTrue(valid(1000, true)); assertFalse(valid(1001, true))
        // One automated batch of 300 members is one Undo
        val tree = GameplayTree(on())
        val untaps = List(300) { PlayAction.SetTapped(gameplayId(10 + it % 2), it / 2 % 2 == 0) }
        val batch = command(GameAction.PlayBatch(untaps, listOf(tag)))
        val after = assertNotNull(HistoryContinuation.change(tree, b, batch).tree)
        val undone = assertNotNull(HistoryContinuation.change(after, b, command(GameAction.Undo)).tree, "one Undo")
        assertEquals(head(tree).board, head(undone).board)
        val redone = assertNotNull(HistoryContinuation.change(undone, b, command(GameAction.Redo)).tree, "one Redo")
        assertEquals(head(after).board, head(redone).board)
    }

    // SYS-057 (#8): no stamp beyond 2^53 − 1 is valid, so no event can overflow the clock
    @Test fun stampsStayWithinTheClock() {
        assertFalse(MtgRules.valid(MtgAction.Restamp(gameplayId(10), MtgStamp(0, 0), MtgStamp(0, Automatum.MAX_TIMESTAMP + 1))))
        assertTrue(MtgRules.valid(MtgAction.Restamp(gameplayId(10), MtgStamp(0, 0), MtgStamp(0, Automatum.MAX_TIMESTAMP))))
        val full = accept(base(), mtg(MtgAction.Restamp(gameplayId(10), MtgStamp(0, 0), MtgStamp(0, Automatum.MAX_TIMESTAMP))))
        val card = card(full, 12)
        val moved = change(full, PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement), MoveDestination(hand, MoveInsertion.UNORDERED)))
        assertEquals("invalidStructure", moved.reason, "the clock is full: refused, never wrapped")
    }

    // SYS-063 (I-D18): "Skip when I have no choice" is on by default, kept per player, and only its player changes it
    @Test fun skipWhenNoChoiceIsEachPlayersOwn() {
        val state = base()
        assertTrue(state.board.players.all { it.skipWhenNoChoice })
        val off = accept(state, mtg(MtgAction.SetSkipWhenNoChoice(false)), b)
        assertEquals(listOf(true, false), off.board.players.map { it.skipWhenNoChoice })
        assertEquals("""{"accountId":"$b","life":"20","counters":{},"skipWhenNoChoice":false}""",
            GameWire.json.encodeToString(PlayPlayer.serializer(), off.board.players[1]))
        assertFalse(GameWire.json.encodeToString(PlayPlayer.serializer(), state.board.players[1]).contains("skip"), "on is left out")
        assertEquals("noOp", change(off, mtg(MtgAction.SetSkipWhenNoChoice(false)), b).outcome)
    }

    // SYS-071 (I-D41): "Full control" is off by default, kept per player, and only its player changes it
    @Test fun fullControlIsEachPlayersOwn() {
        val state = base()
        assertTrue(state.board.players.none { it.fullControl })
        val on = accept(state, mtg(MtgAction.SetFullControl(true)), b)
        assertEquals(listOf(false, true), on.board.players.map { it.fullControl })
        assertEquals("""{"accountId":"$b","life":"20","counters":{},"fullControl":true}""",
            GameWire.json.encodeToString(PlayPlayer.serializer(), on.board.players[1]))
        assertFalse(GameWire.json.encodeToString(PlayPlayer.serializer(), state.board.players[1]).contains("full"), "off is left out")
        assertEquals("noOp", change(on, mtg(MtgAction.SetFullControl(true)), b).outcome)
    }

    // TRN-062 (I-D49, CR 103.5): the mulligans a player took in their opening are kept per player, only its player changes
    // them, from the count it names; none is left out on the wire
    @Test fun mulligansAreEachPlayersOwn() {
        val state = base()
        assertTrue(state.board.players.all { it.mulligans == 0 })
        val one = accept(state, mtg(MtgAction.SetMulligans(0, 1)), b)
        assertEquals(listOf(0, 1), one.board.players.map { it.mulligans })
        assertEquals("""{"accountId":"$b","life":"20","counters":{},"mulligans":1}""",
            GameWire.json.encodeToString(PlayPlayer.serializer(), one.board.players[1]))
        assertFalse(GameWire.json.encodeToString(PlayPlayer.serializer(), state.board.players[1]).contains("mulligans"))
        assertEquals("inapplicable", change(one, mtg(MtgAction.SetMulligans(0, 1)), b).reason)
        assertEquals("noOp", change(one, mtg(MtgAction.SetMulligans(1, 1)), b).outcome)
        assertFalse(MtgRules.valid(MtgAction.SetMulligans(0, GameRules.MAX_MULLIGANS + 1)))
        // CR 103.5: London mulligans, the first free; no further mulligan once the hand kept would have no cards
        val rules = GameRules.STANDARD
        assertEquals(listOf(0, 0, 1, 2, 7), listOf(0, 1, 2, 3, 8).map(rules::owed))
        assertTrue(rules.canMulligan(7))
        assertFalse(rules.canMulligan(8))
    }

    // SYS-062 (I-D23, I-D41 decision A): no branch starts from a point inside an open flow; a branch from before the flow
    // leaves the live line's open flow behind with that line (a late reaction, pausereview #7)
    @Test fun noBranchWhileAFlowIsOpen() {
        var tree = GameplayTree(base())
        tree = play(tree, a, PlayAction.SetTapped(gameplayId(11), true))
        val earlier = tree.currentNodeId
        tree = play(tree, a, mtg(MtgAction.SetRecord(open.id, null, open)))
        val inside = tree.currentNodeId
        tree = play(tree, b, PlayAction.SetLife(b, "17"))
        fun branch(at: String) = HistoryContinuation.change(tree, a, command(GameAction.Play(PlayAction.SetLife(a, "5")))
            .copy(continuation = Continuation(at, true)))
        assertEquals(RuleRefusals.FLOW_OPEN, branch(inside).reason)
        val left = assertNotNull(branch(earlier).tree, "a branch from before the flow")
        assertFalse(Automatum.flowOpen(head(left).board), "the open flow stays on the line left")
        // Once the flow is closed, branching from before it works as always
        tree = play(tree, b, mtg(MtgAction.SetRecord(open.id, open, null)))
        assertNotNull(HistoryContinuation.change(tree, a, command(GameAction.Play(PlayAction.SetLife(a, "5")))
            .copy(continuation = Continuation(earlier, true))).tree)
    }

    // SYS-057 (#4): an automated batch names the board it was planned on; on another board its first member is refused
    @Test fun aBatchPlannedOnAnotherBoardIsRefused() {
        val state = on()
        val planned = MtgTag(listOf("life-changed"), board = MtgFacts.digest(state.board))
        assertEquals("accepted", change(state, PlayAction.AdjustLife(b, "-3"), tag = planned).outcome)
        val edited = accept(state, PlayAction.SetLife(b, "15"))
        assertEquals("inapplicable", change(edited, PlayAction.AdjustLife(b, "-3"), tag = planned).reason)
        // What is only shown doesn't count: a note, a status, the move holder
        val shown = accept(accept(accept(state, PlayAction.SetNote(gameplayId(10), "watch")), PlayAction.SetStatus("Wait")), PlayAction.PassAttention)
        assertEquals("accepted", change(shown, PlayAction.AdjustLife(b, "-3"), tag = planned).outcome)
    }

    // SYS-057, SYS-071 (finalreview-combat #7): the players' pause settings aren't part of the board a batch was planned
    // on: Full control or Skip when I have no choice changed meanwhile (a device syncing it as the game opens) never
    // refuses the other player's batch in flight
    @Test fun aPauseSettingChangedMeanwhileDoesntRefuseABatch() {
        val state = on()
        val planned = MtgTag(listOf("life-changed"), board = MtgFacts.digest(state.board))
        val changed = accept(accept(state, PlayAction.Mtg(MtgAction.SetFullControl(true))), PlayAction.Mtg(MtgAction.SetSkipWhenNoChoice(false)))
        assertEquals("accepted", change(changed, PlayAction.AdjustLife(b, "-3"), tag = planned).outcome)
    }
}
