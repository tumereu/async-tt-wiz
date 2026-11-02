package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v36 (Arcanum integration spec §4, phase I0): the engine's shared state, its events and the facts ATW keeps. */
class AutomatumTest {
    private val a = gameplayId(7)
    private val b = gameplayId(8)
    private val library = gameplayId(20)
    private val hand = gameplayId(21)
    private val field = gameplayId(22)
    private val graveyard = gameplayId(23)
    private val otherField = gameplayId(26)
    private var request = 300

    /** Cards 10 and 11 on the Battlefield, 12–14 in the Library; a Stack entry for card 10; both players' Battlefields. */
    private fun root(): PlayState = base().let { state ->
        accept(state, PlayAction.Stack(StackAction.AddEffect(gameplayId(10), GameplayRules.stackDigest(state)))).state!!
    }

    /** [root] before its Stack entry: a game's starting point for history trees. */
    private fun base(): PlayState = gameplayFixture().withBattlefieldCards(gameplayId(10), gameplayId(11)).let { state ->
        state.copy(board = state.board.copy(containers = state.board.containers + PlayContainer(otherField, PlayZone.BATTLEFIELD, b)))
    }

    private fun entry(state: PlayState) = state.board.stackEffects.single().id
    private fun card(state: PlayState, n: Int) = state.board.cards.single { it.id == gameplayId(n) }
    private fun ref(state: PlayState, n: Int) = MtgRef.Card(gameplayId(n), card(state, n).zoneChanges)
    private fun mtg(action: MtgAction) = PlayAction.Mtg(action)
    private fun command(action: GameAction) = GameCommand(gameplayId(request++), GameScope(gameplayId(1), playId = gameplayId(2)),
        action, gameplayId(80), "1")
    private fun change(state: PlayState, action: PlayAction, tag: MtgTag? = null, actor: String = a) =
        GameplayRules.change(state, actor, command(GameAction.Play(action, tag)))

    /** Accepted, replays exactly from its sealed delta, reverts exactly, and survives the wire. */
    private fun accept(state: PlayState, action: PlayAction, tag: MtgTag? = null): GameplayChange {
        val change = change(state, action, tag)
        assertEquals("accepted", change.outcome, "$action: ${change.reason}")
        val mutation = change.mutation!!
        assertEquals(change, GameplayHistory.replay(state, mutation))
        assertEquals(state.board, GameplayHistory.reverse(change.state!!, mutation))
        assertEquals(mutation, GameWire.json.decodeFromString(GameplayMutation.serializer(),
            GameWire.json.encodeToString(GameplayMutation.serializer(), mutation)))
        return change
    }

    private fun move(state: PlayState, n: Int, destination: MoveDestination): PlayAction.MoveCard {
        val card = card(state, n)
        return PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement), destination)
    }

    private val effect = EngineRecord("effect-1", EngineRecordKind.Effect, 7, """{"mechanics":["anthem"]}""")
    private val trigger = EngineRecord("delayed-1", EngineRecordKind.DelayedTrigger, 8, """{"at":"end"}""")
    private val continuation = EngineRecord(Automatum.CONTINUATION_ID, EngineRecordKind.Continuation, data = """{"decision":"targets"}""")

    // SYS-055, SYS-027
    @Test fun everyEngineEventAppliesAndRevertsExactly() {
        var state = root()
        val entryId = entry(state)
        val item = MtgStackItem(MtgRef.StackItem("ability-1"), MtgStackKind.Activated, a, ability = "printed/0",
            source = ref(state, 10), targets = mapOf("target" to listOf(MtgRef.Player(b), ref(state, 11))), modes = listOf(1),
            values = mapOf("X" to 3), timestamp = 5, data = """{"snapshots":[]}""")
        val counter = TypedCounter("+1/+1", 2, 4)
        val actions = listOf(
            MtgAction.SetPriority(null, MtgPriority(a)),
            MtgAction.SetPriority(MtgPriority(a), MtgPriority(b, 1)),
            MtgAction.SetManaPool(a, emptyList(), listOf(PoolMana("R"), PoolMana("C", """{"source":"x"}"""))),
            MtgAction.SetCounter(ref(state, 10), "+1/+1", null, counter),
            MtgAction.SetCounter(ref(state, 10), "+1/+1", counter, counter.copy(count = 3)),
            MtgAction.SetCounter(MtgRef.Player(b), "poison", null, TypedCounter("poison", 1, 6)),
            MtgAction.Attach(ref(state, 11), null, ref(state, 10)),
            MtgAction.SetLedger(ref(state, 10), "", """{"kicked":1}"""),
            MtgAction.SetLedger(MtgRef.Player(a), "", """{"spells":2}"""),
            MtgAction.SetStackItem(entryId, null, item),
            // SYS-059: with no manual entry left on the Stack
            MtgAction.SetAutomatum(true),
            MtgAction.SetStackItem(entryId, item, item.copy(modes = listOf(0))),
            MtgAction.SetRecord(effect.id, null, effect),
            MtgAction.SetRecord(trigger.id, null, trigger),
            MtgAction.SetRecord(Automatum.CONTINUATION_ID, null, continuation),
            MtgAction.SetRecord(effect.id, effect, effect.copy(data = "{}")),
            MtgAction.SetRecord(trigger.id, trigger, null),
            MtgAction.Restamp(gameplayId(12), MtgStamp(0, 0), MtgStamp(1, 9)),
            MtgAction.SetAutomatum(false),
        )
        for (action in actions) {
            assertTrue(GameplayRules.valid(mtg(action)), "$action")
            state = accept(state, mtg(action)).state!!
        }
        val board = state.board
        assertFalse(board.automatum)
        // SYS-070 (I-D42, combat review #11): turning Automatum on keeps priority, its holder and passes in a row
        assertEquals(MtgPriority(b, 1), board.priority)
        assertEquals(listOf(PoolMana("R"), PoolMana("C", """{"source":"x"}""")), board.players.single { it.accountId == a }.manaPool)
        assertEquals(listOf(counter.copy(count = 3)), card(state, 10).typedCounters)
        assertEquals(listOf(TypedCounter("poison", 1, 6)), board.players.single { it.accountId == b }.typedCounters)
        assertEquals(MtgRef.Card(gameplayId(10), 0), card(state, 11).attachedTo)
        assertEquals("""{"kicked":1}""", card(state, 10).ledger)
        assertEquals("""{"spells":2}""", board.players.single { it.accountId == a }.ledger)
        assertEquals(item.copy(modes = listOf(0)), board.stackEffects.single().item)
        // SYS-060: switching off closed the flow; the effect stays
        assertEquals(listOf(effect.id), board.engineRecords.map { it.id })
        assertEquals(MtgStamp(1, 9), card(state, 12).let { MtgStamp(it.zoneChanges, it.timestamp) })
    }

    // SYS-057
    @Test fun engineEventsCheckTheValuesTheyExpect() {
        val state = root()
        val stale = mtg(MtgAction.SetRecord(effect.id, effect, null))
        assertEquals("inapplicable", change(state, stale).reason)
        assertEquals("inapplicable", change(state, mtg(MtgAction.SetPriority(MtgPriority(a), null))).reason)
        assertEquals("inapplicable", change(state, mtg(MtgAction.SetManaPool(a, listOf(PoolMana("G")), emptyList()))).reason)
        assertEquals("inapplicable", change(state, mtg(MtgAction.SetLedger(ref(state, 10), "{}", ""))).reason)
        // A card named by an older object (another zone-change count) is not that object any more (D21).
        val older = MtgRef.Card(gameplayId(10), 1)
        assertEquals("missingReference", change(state, mtg(MtgAction.SetCounter(older, "+1/+1", null, TypedCounter("+1/+1", 1, 1)))).reason)
        assertEquals("missingReference", change(state, mtg(MtgAction.SetStackItem(gameplayId(99), null, null))).reason)
        assertEquals("missingReference", change(state, mtg(MtgAction.SetPriority(null, MtgPriority(gameplayId(99))))).reason)
        // Equal values change nothing; turning the switch to where it is changes nothing.
        assertEquals("noOp", change(state, mtg(MtgAction.SetRecord(effect.id, null, null))).outcome)
        assertEquals("noOp", change(state, mtg(MtgAction.SetAutomatum(false))).outcome)
    }

    // SYS-057
    @Test fun malformedEngineDataIsRefusedAtTheWire() {
        fun valid(action: MtgAction) = GameplayRules.valid(mtg(action))
        assertFalse(valid(MtgAction.SetRecord("x", null, EngineRecord("y", EngineRecordKind.Effect, data = "{}"))))
        assertFalse(valid(MtgAction.SetRecord("x", null, EngineRecord("x", EngineRecordKind.Effect, data = "{not json"))))
        assertFalse(valid(MtgAction.SetRecord("x", null, EngineRecord("x", EngineRecordKind.Continuation, data = "{}"))))
        assertFalse(valid(MtgAction.SetRecord(Automatum.CONTINUATION_ID, null, EngineRecord(Automatum.CONTINUATION_ID, EngineRecordKind.Effect, data = "{}"))))
        assertFalse(valid(MtgAction.SetRecord("x", null, EngineRecord("x", EngineRecordKind.Effect, data = "\"" + "a".repeat(Automatum.MAX_DATA_BYTES) + "\""))))
        assertFalse(valid(MtgAction.SetManaPool(a, emptyList(), listOf(PoolMana("X")))))
        assertFalse(valid(MtgAction.SetCounter(MtgRef.Player(a), "poison", null, TypedCounter("energy", 1, 1))))
        assertFalse(valid(MtgAction.SetCounter(MtgRef.Player(a), "poison", null, TypedCounter("poison", 0, 1))))
        assertFalse(valid(MtgAction.SetCounter(MtgRef.StackItem("s"), "poison", null, null)))
        assertFalse(valid(MtgAction.SetLedger(MtgRef.Card("not-a-uuid", 0), "", "{}")))
        assertFalse(valid(MtgAction.SetPriority(null, MtgPriority(a, -1))))
        val tagged = command(GameAction.Play(mtg(MtgAction.SetAutomatum(true)), MtgTag(listOf("Died"))))
        assertFalse(GameWire.valid(tagged))
        val batch = command(GameAction.PlayBatch(listOf(mtg(MtgAction.SetAutomatum(true))), listOf(MtgTag(listOf("cast")), null)))
        assertFalse(GameWire.valid(batch))
        assertTrue(GameWire.valid(batch.copy(action = GameAction.PlayBatch(listOf(mtg(MtgAction.SetAutomatum(true))),
            listOf(MtgTag(listOf("cast"), MtgCause(a, ref(root(), 10), "Storm Spark")))))))
    }

    // SYS-056; D21, CR 400.7: a zone change makes a new object with a new timestamp and fresh facts; a control change doesn't.
    @Test fun zoneChangesCountUpAndStampNewObjects() {
        var state = root()
        state = accept(state, mtg(MtgAction.SetCounter(ref(state, 10), "+1/+1", null, TypedCounter("+1/+1", 1, 3)))).state!!
        state = accept(state, mtg(MtgAction.SetLedger(ref(state, 10), "", "{}"))).state!!
        state = accept(state, mtg(MtgAction.Attach(ref(state, 10), null, MtgRef.Player(b)))).state!!
        val attachedStamp = card(state, 10).timestamp
        assertEquals(4, attachedStamp) // CR 613.7e: attaching stamps, one above the highest stamp present (3)
        // To the other player's Battlefield: a control change, the same object.
        state = accept(state, move(state, 10, MoveDestination(otherField, MoveInsertion.EMPTY, BoardPosition(0, 0)))).state!!
        assertEquals(0, card(state, 10).zoneChanges)
        assertEquals(attachedStamp, card(state, 10).timestamp)
        // To the graveyard: a new object.
        state = accept(state, move(state, 10, MoveDestination(graveyard, MoveInsertion.UNORDERED))).state!!
        val dead = card(state, 10)
        assertEquals(1, dead.zoneChanges)
        assertEquals(attachedStamp + 1, dead.timestamp)
        assertEquals(emptyList(), dead.typedCounters)
        assertEquals("", dead.ledger)
        assertNull(dead.attachedTo)
    }

    // SYS-056; CR 701.20d, D38: a shuffle makes every card of the library a new object, without new timestamps.
    @Test fun shufflesMakeNewObjects() {
        val state = root()
        val shuffled = accept(state, PlayAction.ShuffleLibrary(library, RandomV1.encodeSeed(ByteArray(32) { 1 }))).state!!
        for (n in 12..14) assertEquals(MtgStamp(1, 0), card(shuffled, n).let { MtgStamp(it.zoneChanges, it.timestamp) })
        assertEquals(0, card(shuffled, 10).zoneChanges)
    }

    // SYS-056; CR 613.7f: turning face down or up gives a new timestamp; a step change empties mana pools (CR 106.4).
    @Test fun faceChangesStampAndStepsEmptyManaPools() {
        var state = root()
        state = accept(state, PlayAction.SetCovered(gameplayId(11), true)).state!!
        assertEquals(1, card(state, 11).timestamp)
        state = accept(state, mtg(MtgAction.SetManaPool(b, emptyList(), listOf(PoolMana("G"))))).state!!
        state = accept(state, PlayAction.NextStep).state!!
        assertEquals(emptyList(), state.board.players.single { it.accountId == b }.manaPool)
    }

    // SYS-056; D33, D42: a manual move is tagged with what the move alone means; an engine event carries the engine's tag.
    @Test fun movesAreTaggedAndEngineTagsAreRecordedAndSealed() {
        // Automatum on: its own (tagged) events apply only then (SYS-057)
        var state = root().let { it.copy(board = it.board.copy(automatum = true)) }
        val died = accept(state, move(state, 11, MoveDestination(graveyard, MoveInsertion.UNORDERED)))
        assertEquals(MtgTag(listOf("left-battlefield", "died", "put-into-graveyard")), died.mutation!!.tag)
        val drawn = accept(state, move(state, 12, MoveDestination(hand, MoveInsertion.UNORDERED)))
        assertNull(drawn.mutation!!.tag)
        assertNull(accept(state, PlayAction.SetTapped(gameplayId(10), true)).mutation!!.tag)
        val tag = MtgTag(listOf("sacrificed", "died"), MtgCause(a, ref(state, 10), "Storm Spark", MtgRef.StackItem("ability-1")),
            """{"seen":{"name":"Grizzly Cub"}}""")
        val engine = accept(state, move(state, 11, MoveDestination(graveyard, MoveInsertion.UNORDERED)), tag)
        assertEquals(tag, engine.mutation!!.tag)
        // The tag is part of the sealed event: a changed tag no longer matches its seal.
        val forged = engine.mutation.copy(tag = tag.copy(meanings = listOf("destroyed")))
        assertNotEquals(engine.mutation.transition.delta!!.seal, GameplayDeltas.seal(forged))
        // Batch members carry their own tags.
        state = base()
        val batch = command(GameAction.PlayBatch(listOf(mtg(MtgAction.SetAutomatum(true)), PlayAction.SetTapped(gameplayId(10), true)),
            listOf(null, MtgTag(listOf("tapped-for-mana")))))
        val tree = GameplayTree(state)
        val played = assertNotNull(PlayBatches.change(tree, a, batch).tree)
        val members = generateSequence(played.nodes[played.currentNodeId]) { played.nodes[it.parentId] }.toList().asReversed()
        assertEquals(listOf(null, MtgTag(listOf("tapped-for-mana"))), members.map { it.tag })
    }

    // SYS-058; undo rule: engine records are per record, and the timestamp clock is no event's concern (D34).
    @Test fun undoIsBlockedOnlyByLaterEventsOnTheSameRecord() {
        fun play(tree: GameplayTree, actor: String, action: PlayAction) =
            assertNotNull(tree.play(actor, command(GameAction.Play(action))).tree, "$action")
        fun undo(tree: GameplayTree) = SelectiveUndo.change(tree, a, command(GameAction.Undo))
        val other = EngineRecord("effect-2", EngineRecordKind.Effect, 3, "{}")
        val created = play(GameplayTree(base()), a, mtg(MtgAction.SetRecord(effect.id, null, effect)))
        // Another record changed later: Alice's record can be undone, Bob's stays.
        val independent = play(created, b, mtg(MtgAction.SetRecord(other.id, null, other)))
        val undone = assertNotNull(undo(independent).tree)
        assertEquals(listOf(other), undone.stateAt(undone.currentNodeId)!!.board.engineRecords)
        // The same record changed later: undo is refused.
        val dependent = play(created, b, mtg(MtgAction.SetRecord(effect.id, effect, effect.copy(data = "[]"))))
        assertEquals("replayBlocked", undo(dependent).reason)
        // Two moves stamp in turn; undoing the first keeps the second's stamp and the order of stamps.
        val start = base()
        val first = play(GameplayTree(start), a, move(start, 10, MoveDestination(graveyard, MoveInsertion.UNORDERED)))
        val firstState = first.stateAt(first.currentNodeId)!!
        val second = play(first, b, move(firstState, 11, MoveDestination(gameplayId(24), MoveInsertion.UNORDERED)))
        val stamp = card(second.stateAt(second.currentNodeId)!!, 11).timestamp
        assertTrue(stamp > card(firstState, 10).timestamp)
        val back = assertNotNull(undo(second).tree, undo(second).reason)
        val after = back.stateAt(back.currentNodeId)!!
        assertEquals(stamp, card(after, 11).timestamp)
        assertEquals(MtgStamp(0, 0), card(after, 10).let { MtgStamp(it.zoneChanges, it.timestamp) })
        // The next stamp is still above every stamp present.
        val next = play(back, a, move(after, 12, MoveDestination(hand, MoveInsertion.UNORDERED)))
        assertEquals(stamp + 1, card(next.stateAt(next.currentNodeId)!!, 12).timestamp)
        // Mana pools: a later change of the same pool blocks undo.
        val mana = play(GameplayTree(base()), a, mtg(MtgAction.SetManaPool(b, emptyList(), listOf(PoolMana("U")))))
        val spent = play(mana, b, mtg(MtgAction.SetManaPool(b, listOf(PoolMana("U")), emptyList())))
        assertEquals("replayBlocked", undo(spent).reason)
    }

    // SYS-031: events recorded under older rules keep their behaviour; old boards gain no new fields.
    @Test fun olderRulesAndOldBoardsAreUnchanged() {
        val state = root()
        val action = move(state, 12, MoveDestination(hand, MoveInsertion.UNORDERED))
        val v35 = GameplayRules.move(state, a, command(GameAction.Play(action)), action, rulesVersion = 35)
        assertEquals(35, v35.mutation!!.transition.rulesVersion)
        assertEquals(MtgStamp(0, 0), card(v35.state!!, 12).let { MtgStamp(it.zoneChanges, it.timestamp) })
        val encoded = GameWire.json.encodeToString(BoardState.serializer(), gameplayFixture().board)
        for (key in listOf("automatum", "priority", "engineRecords", "zoneChanges", "timestamp", "typedCounters", "attachedTo",
            "ledger", "manaPool", "\"item\"")) assertFalse(key in encoded, key)
        val untagged = GameWire.json.encodeToString(GameAction.serializer(), GameAction.Play(PlayAction.NextStep))
        assertFalse("tag" in untagged)
    }

    // HIS-092 (I-D22, I-D35): history events are natural branching points, one per point where a player can act. Every
    // member of an automated batch after its first that is tagged folded joins the visible event before it; the batch's
    // first event and untagged events never fold.
    @Test fun foldedBookkeepingJoinsTheBatchsVisibleEvent() {
        val state = root().let { it.copy(board = it.board.copy(automatum = true)) }
        val batch = command(GameAction.PlayBatch(listOf(PlayAction.SetTapped(gameplayId(11), true),
            mtg(MtgAction.SetRecord("effect-1", null, effect)), mtg(MtgAction.SetPriority(null, MtgPriority(b))),
            mtg(MtgAction.SetRecord("emblem:1", null, EngineRecord("emblem:1", EngineRecordKind.Emblem, 9, "{}", a, "Flying"))),
            PlayAction.SetTapped(gameplayId(10), true)),
            listOf(null, MtgTag(folded = true), MtgTag(folded = true), MtgTag(folded = true), null)))
        var next = state
        val recorded = (0 until 5).map { i ->
            val change = GameplayRules.change(next, a, PlayBatches.member(batch, i, (batch.action as GameAction.PlayBatch).actions[i]))
            assertEquals("accepted", change.outcome, "$i: ${change.reason}")
            next = change.state!!
            change.mutation!!
        }
        assertEquals(listOf(false, true, true, true, false), recorded.map { it.grouping?.folded == true })
        assertEquals(recorded[0].bookkeeping!!.toGroup!!.id, recorded[2].bookkeeping!!.toGroup!!.id)
        assertEquals(recorded[0].bookkeeping!!.toGroup!!.representative, recorded[2].bookkeeping!!.toGroup!!.representative,
            "never the group's representative")
        // A folded tag on the batch's first member, or on a single play, is shown as usual
        val first = command(GameAction.PlayBatch(listOf(mtg(MtgAction.SetPriority(null, MtgPriority(b)))), listOf(MtgTag(folded = true))))
        assertNotEquals(true, GameplayRules.change(state, a, PlayBatches.member(first, 0,
            (first.action as GameAction.PlayBatch).actions[0])).mutation!!.grouping?.folded)
        assertNotEquals(true, change(state, mtg(MtgAction.SetPriority(null, MtgPriority(b))), MtgTag(folded = true)).mutation!!.grouping?.folded)
        assertTrue(MtgTag.valid(MtgTag(folded = true)))
    }

    // SYS-070 (I-D42): turning Automatum on drops the combat record, waiting triggers and early answers recorded before it;
    // effects, delayed triggers, emblems, the turn record and priority (combat review #11) stay; undoing the switch
    // restores them
    @Test fun switchingOnDropsTurnScopedEngineData() {
        val combat = EngineRecord("combat", EngineRecordKind.Combat, data = """{"attackers":[]}""")
        val waiting = EngineRecord("triggered", EngineRecordKind.Triggered, data = "[]")
        val early = EngineRecord("pre-answer:1", EngineRecordKind.PreAnswer, data = "{}", player = b)
        val turn = EngineRecord("turn", EngineRecordKind.Turn, data = "{}")
        val emblem = EngineRecord("emblem:1", EngineRecordKind.Emblem, 9, "{}", a, "Flying")
        val kept = listOf(effect, trigger, emblem, turn)
        val off = base().let { it.copy(board = it.board.copy(priority = MtgPriority(b, 1),
            engineRecords = (kept + listOf(combat, waiting, early)).sortedBy { it.id })) }
        val on = accept(off, mtg(MtgAction.SetAutomatum(true))).state!!
        assertTrue(on.board.automatum)
        assertEquals(kept.sortedBy { it.id }, on.board.engineRecords)
        assertEquals(MtgPriority(b, 1), on.board.priority)
        // Off and on again with nothing turn-scoped left: only the switch changes
        val again = accept(accept(on, mtg(MtgAction.SetAutomatum(false))).state!!, mtg(MtgAction.SetAutomatum(true))).state!!
        assertEquals(on.board, again.board)
        // Undo of the switch brings them back
        val switched = assertNotNull(GameplayTree(off).play(a, command(GameAction.Play(mtg(MtgAction.SetAutomatum(true))))).tree)
        val undone = assertNotNull(SelectiveUndo.change(switched, a, command(GameAction.Undo)).tree)
        assertEquals(off.board, undone.stateAt(undone.currentNodeId)!!.board)
    }

    // SYS-066 (I-D25): an emblem record says whose it is and its text; other records say neither
    @Test fun emblemRecordsCarryTheirPlayerAndText() {
        val state = root()
        val emblem = EngineRecord("emblem:1", EngineRecordKind.Emblem, 9, "{}", a, "Creatures you control have flying.")
        val with = accept(state, mtg(MtgAction.SetRecord("emblem:1", null, emblem))).state!!
        assertEquals(emblem, with.board.engineRecords.single())
        for (bad in listOf(emblem.copy(player = null), emblem.copy(player = "nobody"), effect.copy(player = a), effect.copy(text = "x")))
            assertFalse(MtgRules.valid(MtgAction.SetRecord(bad.id, null, bad)), "$bad")
    }
}
