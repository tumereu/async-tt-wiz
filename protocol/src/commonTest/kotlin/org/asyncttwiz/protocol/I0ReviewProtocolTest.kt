package org.asyncttwiz.protocol

import kotlin.test.*

/**
 * Adversarial review of phase I0, rules v36 (integration spec §4.5): MtgFacts, undo/redo and the server's form checks.
 * Tests marked `@Ignore("i0review #N …")` fail today; N is the finding's number in the review report.
 */
class I0ReviewProtocolTest {
    private val a = gameplayId(7)
    private val b = gameplayId(8)
    private val hand = gameplayId(21)
    private val graveyard = gameplayId(23)
    private var request = 700

    private fun base(): PlayState = gameplayFixture().withBattlefieldCards(gameplayId(10), gameplayId(11))
    private fun card(state: PlayState, n: Int) = state.board.cards.single { it.id == gameplayId(n) }
    private fun command(action: GameAction) = GameCommand(gameplayId(request++), GameScope(gameplayId(1), playId = gameplayId(2)),
        action, gameplayId(80), "1")
    private fun play(tree: GameplayTree, actor: String, action: PlayAction) =
        assertNotNull(tree.play(actor, command(GameAction.Play(action))).tree, "$action")
    private fun move(state: PlayState, n: Int, destination: MoveDestination): PlayAction.MoveCard {
        val card = card(state, n)
        return PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement), destination)
    }
    private fun head(tree: GameplayTree) = tree.stateAt(tree.currentNodeId)!!

    // #7 §4.5 / D34 ("later stamps are always higher, so stamp order is event order"): Alice undoes her move, Bob stamps
    // another card, Alice redoes. Her event goes back before Bob's in history; I-D26 (SYS-058): the redo gives it a fresh
    // stamp, so it stays distinct from Bob's and follows the real order (CR 613.7).
    @Test fun redoAfterTheOtherPlayersEventKeepsTimestampsDistinctAndInOrder() {
        val start = base()
        val moved = play(GameplayTree(start), a, move(start, 12, MoveDestination(hand, MoveInsertion.UNORDERED)))
        val aliceStamp = card(head(moved), 12).timestamp
        val undone = assertNotNull(SelectiveUndo.change(moved, a, command(GameAction.Undo)).tree)
        val covered = play(undone, b, PlayAction.SetCovered(gameplayId(11), true))
        val bobStamp = card(head(covered), 11).timestamp
        val redone = SelectiveUndo.change(covered, a, command(GameAction.Redo))
        assertNotNull(redone.tree, "redo is allowed: ${redone.reason}")
        val after = head(redone.tree!!)
        assertEquals(aliceStamp, bobStamp, "both were stamped from the same clock")
        assertNotEquals(card(after, 12).timestamp, card(after, 11).timestamp,
            "two objects share timestamp ${card(after, 11).timestamp} (Alice's redone move, Bob's later face-down: $bobStamp)")
        assertTrue(card(after, 12).timestamp > card(after, 11).timestamp, "the redone move is the newer object")
        // The redone event undoes exactly again.
        val again = assertNotNull(SelectiveUndo.change(redone.tree!!, a, command(GameAction.Undo)).tree)
        assertEquals(card(head(covered), 12), card(head(again), 12))
    }

    // #8 P8: one Restamp to the clock's end; every later stamp of the game overflows into negative timestamps, which every
    // later engine event about that card then fails MtgRules.valid on (timestamps must be ≥ 0)
    // SYS-057
    @Test fun aRestampCantPushTheClockPastItsEnd() {
        var state = base()
        val restamp = PlayAction.Mtg(MtgAction.Restamp(gameplayId(10), MtgStamp(0, 0), MtgStamp(0, Long.MAX_VALUE)))
        val accepted = gameplayChange(state, restamp, 701)
        if (accepted.outcome != "accepted") return // refused at the door: fine
        state = accepted.state!!
        state = gameplayChange(state, move(state, 12, MoveDestination(hand, MoveInsertion.UNORDERED)), 702).state!!
        assertTrue(card(state, 12).timestamp >= 0, "the next stamp overflowed to ${card(state, 12).timestamp}")
    }

    // #2 Automatum.MAX_DATA_BYTES says engine data may be 256 KiB, but Automatum.data also requires DeckValidation.text,
    // which caps any text at 64 KiB (DeckValidation.FIELD_BYTES): records, ledgers, Stack items and tags between 64 and
    // 256 KiB are refused as invalidInput
    @Test fun engineDataUpToTheDocumentedLimitIsAccepted() {
        val data = "\"" + "a".repeat(100 * 1024) + "\""
        assertTrue(data.encodeToByteArray().size <= Automatum.MAX_DATA_BYTES)
        assertTrue(Automatum.data(data), "100 KiB of engine JSON is refused")
        assertTrue(MtgTag.valid(MtgTag(listOf("destroyed"), data = data)))
    }

    // Owner decision A → I-D19 (SYS-060): a shared flow's steps are sent by whoever answers (D11, §2): Bob answered Alice's
    // trigger. Undo takes back only the actor's own events, so switching off reverts the flow on the server instead, every
    // step whoever made it.
    @Test fun aSharedFlowAnsweredByBothPlayersCanBeTakenBack() {
        val start = base()
        val open = EngineRecord(Automatum.CONTINUATION_ID, EngineRecordKind.Continuation, data = """{"step":1}""")
        val answered = open.copy(data = """{"step":2}""")
        val step1 = play(GameplayTree(start), a, PlayAction.Mtg(MtgAction.SetRecord(open.id, null, open)))
        val step2 = play(step1, b, PlayAction.Mtg(MtgAction.SetRecord(open.id, open, answered)))
        // Alice switches off: the server reverts the flow to where it began
        val reverted = HistoryContinuation.change(step2, a, command(GameAction.RevertFlow))
        val back = reverted.tree
        assertTrue(back != null && head(back).board.engineRecords.isEmpty(), "Alice can't take the flow back: ${reverted.reason}")
        assertEquals(GameplayTree(start).currentNodeId, back.currentNodeId)
    }

    // Owner decision B: SYS-026 counts the undo window in events (50 later events, 51 back to the actor's own). One engine call is one
    // batch of up to 100 events (a turn change on a busy board is ~50): after one such step by Bob, Alice can't undo her
    // own last action although nothing in Bob's batch touches it (the undo rule would allow it).
    @Ignore("i0review owner decision B → I-D20: the undo window keeps counting events; raised later if playtesting needs it")
    @Test fun oneBigAutomatedStepDoesntPutTheOtherPlayersLastActionOutOfUndoReach() {
        val start = base()
        val edited = play(GameplayTree(start), a, PlayAction.SetLife(a, "17"))
        val untaps = List(52) { PlayAction.SetTapped(gameplayId(10 + it % 2), it / 2 % 2 == 0) }
        val batch = command(GameAction.PlayBatch(untaps))
        val stepped = assertNotNull(PlayBatches.change(edited, b, batch).tree, "Bob's automated step")
        val undo = SelectiveUndo.change(stepped, a, command(GameAction.Undo))
        assertNotNull(undo.tree, "Alice's undo of her life edit: ${undo.reason}")
    }

    // #11 SYS-031, SYS-056: in a game started before rules v36 every permanent has timestamp 0; Automatum switched on
    // there orders them (CR 613.7, layers) by card id. The switch should stamp what it takes over.
    // SYS-059
    @Test fun switchingAutomatumOnInAnOlderGameGivesItsPermanentsTimestamps() {
        val state = base()
        assertTrue(state.board.cards.all { it.timestamp == 0L })
        val on = gameplayChange(state, PlayAction.Mtg(MtgAction.SetAutomatum(true)), 703).state!!
        val field = on.board.cards.filter { it.containerId == gameplayId(22) }
        assertEquals(field.size, field.map { it.timestamp }.distinct().size, "permanents share timestamps: ${field.map { it.timestamp }}")
    }
}
