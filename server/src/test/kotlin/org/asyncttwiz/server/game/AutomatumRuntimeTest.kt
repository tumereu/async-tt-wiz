package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Rules v36 (Arcanum integration spec §4, phase I0; P8; I-D52): the server validates and stores the engine's events like
 * any other action: a tagged engine batch is accepted whole and kept, a stale or malformed one is refused and changes
 * nothing; outside Automatum batches it takes only what isn't a change made by hand. */
class AutomatumRuntimeTest {
    // SYS-055, SYS-057
    @Test fun `engine batches are stored with their tags and refused whole when stale or malformed`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime, deckSize = 10)
                val owner = f.a.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                fun batch(actions: List<PlayAction>, tags: List<MtgTag?> = emptyList(), stream: GameplayFixture.Stream = f.owner) =
                    stream.command(PlayAction.NextStep).copy(action = GameAction.PlayBatch(actions, tags))
                val library = state().board.zoneContainer(owner, PlayZone.LIBRARY)!!
                val graveyard = state().board.zoneContainer(owner, PlayZone.GRAVEYARD)!!
                val card = library.cards.first()
                val effect = EngineRecord("effect-1", EngineRecordKind.Effect, 2, """{"mechanics":[]}""")
                // Automatum's form: the first member names the board it was planned on (pivot review #4)
                val tag = MtgTag(listOf("milled", "put-into-graveyard"), MtgCause(owner, name = "Thought Sieve"), board = MtgFacts.digest(state().board))

                // An automated batch: a mill and an effect record, each member with its own tag.
                val accepted = f.owner.execute(batch(listOf(PlayAction.MoveCard(card, MoveSource(library.id, edge = MoveSourceEdge.TOP),
                        MoveDestination(graveyard.id, MoveInsertion.UNORDERED)),
                    PlayAction.Mtg(MtgAction.SetRecord(effect.id, null, effect))), listOf(tag)))
                assertEquals("accepted", accepted.outcome, accepted.reason)
                val board = state().board
                assertEquals(listOf(effect), board.engineRecords)
                assertEquals(1, board.cards.single { it.id == card }.zoneChanges)
                val history = f.games.history(f.b, f.game.id, f.root.id, "0").mutations
                assertEquals(listOf(tag, null), history.takeLast(2).map { it.tag })

                // Stale: the record changed since the engine read it. The whole batch is refused.
                fun refused(reason: String, input: GameCommand) {
                    val before = state()
                    val result = f.recipient.execute(input)
                    assertEquals("rejected", result.outcome)
                    assertEquals(reason, result.reason)
                    assertEquals(before, state())
                }
                refused("inapplicable", batch(listOf(PlayAction.SetLife(owner, "19"),
                    PlayAction.Mtg(MtgAction.SetRecord(effect.id, null, effect.copy(data = "{}")))), listOf(TEST_TAG), stream = f.recipient))
                refused("missingReference", batch(listOf(PlayAction.Mtg(MtgAction.SetCounter(MtgRef.Card(card, 0), "+1/+1",
                    null, TypedCounter("+1/+1", 1, 3)))), listOf(TEST_TAG), stream = f.recipient))
                // Malformed engine data never reaches the game.
                val before = state()
                val malformed = batch(listOf(PlayAction.Mtg(MtgAction.SetRecord("x", null,
                    EngineRecord("x", EngineRecordKind.Effect, data = "{not json")))), listOf(TEST_TAG), stream = f.recipient)
                assertEquals("invalidInput", assertThrows(GameProtocolFailure::class.java) { f.recipient.admit(malformed) }.message)
                assertEquals(before, state())

                // The stored game replays exactly from its history.
                var replay = f.root
                f.games.history(f.b, f.game.id, f.root.id, "0").mutations.forEach { replay = GameplayHistory.replay(replay, it).state!! }
                assertEquals(state(), replay)
            }
        }
    }

    // SYS-061, SYS-057
    @Test fun `big automated batches are taken whole and one planned on another board is refused`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val owner = f.a.account.accountId
                val other = f.b.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                fun batch(actions: List<PlayAction>, tags: List<MtgTag?>, stream: GameplayFixture.Stream = f.owner) =
                    stream.command(PlayAction.NextStep).copy(action = GameAction.PlayBatch(actions, tags))
                val start = state()
                val open = EngineRecord(Automatum.CONTINUATION_ID, EngineRecordKind.Continuation, data = """{"step":1}""")
                // A flow opened by the owner's batch of 150 members (more than a player's own batch may hold) ...
                val planned = MtgTag(listOf("life-changed"), board = MtgFacts.digest(start.board))
                val opening = batch(List(149) { PlayAction.AdjustLife(other, if (it % 2 == 0) "-1" else "+1") } +
                    PlayAction.Mtg(MtgAction.SetRecord(open.id, null, open)), listOf(planned))
                assertEquals("accepted", f.owner.execute(opening).let { it.outcome + (it.reason ?: "") })
                // ... and answered by the other player
                assertEquals("accepted", f.recipient.execute(f.recipient.command(PlayAction.Mtg(MtgAction.SetRecord(open.id, open,
                    open.copy(data = """{"step":2}"""))))).outcome)
                // A batch planned on the board before the flow is refused whole
                val stale = f.recipient.execute(batch(listOf(PlayAction.AdjustLife(owner, "-3")),
                    listOf(planned.copy(meanings = listOf("damaged"))), f.recipient))
                assertEquals("inapplicable", stale.reason)
            }
        }
    }

    // SYS-069, SYS-073, TRN-046, TRN-062 (I-D52): outside Automatum batches the server refuses every change made by hand,
    // and still takes the player's own settings, result suggestions, their opening and conceding (CR 104.3a)
    @Test fun `changes made by hand are refused and what is no change by hand goes on`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime, deckSize = 10)
                val owner = f.a.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                fun manual(stream: GameplayFixture.Stream, action: PlayAction) =
                    stream.execute(stream.command(action).copy(action = GameAction.Play(action)))
                fun manualBatch(stream: GameplayFixture.Stream, actions: List<PlayAction>) =
                    stream.execute(stream.command(PlayAction.NextStep).copy(action = GameAction.PlayBatch(actions)))
                val start = state()
                val hand = start.board.zoneContainer(owner, PlayZone.HAND)!!
                val library = start.board.zoneContainer(owner, PlayZone.LIBRARY)!!
                // Every play begins in the opening, the opening hands drawn (TRN-062)
                assertEquals(GameRules.STANDARD.openingHand, hand.cards.size)
                for (action in listOf(PlayAction.SetTapped(hand.cards.first(), true), PlayAction.SetLife(owner, "17"),
                    PlayAction.NextStep, PlayAction.RollDie(6, 2), PlayAction.SetStatus("Thinking"))) {
                    val refused = manual(f.owner, action)
                    assertEquals(RuleRefusals.AUTOMATUM_ONLY, refused.reason, "$action")
                }
                assertEquals(start, state())
                // The player's own settings
                assertEquals("accepted", manual(f.owner, PlayAction.Mtg(MtgAction.SetFullControl(true))).outcome)
                assertEquals("accepted", manual(f.recipient, PlayAction.Mtg(MtgAction.SetSkipWhenNoChoice(false))).outcome)
                // Alice's opening: two London mulligans (the first free), then Keep with one card on the bottom and Ready
                for (taken in 0..1) {
                    val mulligan = manualBatch(f.owner, listOf(PlayAction.Mulligan(library.id, hand.id,
                        state().board.zoneContainer(owner, PlayZone.HAND)!!.cards, 7, RandomV1.encodeSeed(ByteArray(32))),
                        PlayAction.Mtg(MtgAction.SetMulligans(taken, taken + 1))))
                    assertEquals("accepted", mulligan.outcome, mulligan.reason)
                }
                val kept = state().board.zoneContainer(owner, PlayZone.HAND)!!
                val keep = manualBatch(f.owner, listOf(PlayAction.MoveCard(kept.cards.first(), MoveSource(kept.id),
                    MoveDestination(library.id, MoveInsertion.BOTTOM)), PlayAction.SetStatus("")))
                assertEquals("accepted", keep.outcome, keep.reason)
                assertFalse(PlayerStatus.opening(state().board, owner))
                // Ready, the opening is over for Alice: no more mulligans by hand
                assertEquals(RuleRefusals.AUTOMATUM_ONLY, manualBatch(f.owner, listOf(PlayAction.Mulligan(library.id, hand.id,
                    state().board.zoneContainer(owner, PlayZone.HAND)!!.cards, 7, RandomV1.encodeSeed(ByteArray(32))))).reason)
                // A draw suggested and waiting; Alice concedes all the same (CR 104.3a): she loses at once, the play ends
                val draw = manual(f.recipient, PlayAction.SuggestResult(SuggestedResult.DRAW))
                assertEquals("accepted", draw.outcome)
                val played = state()
                val concession = f.owner.command(PlayAction.NextStep).copy(action = GameAction.Concede)
                val conceded = f.owner.execute(concession)
                assertEquals("accepted", conceded.outcome, conceded.reason)
                assertEquals(GameLifecycle.LOBBY, f.games.head(f.a, f.game.id).summary.lifecycle)
                // The play ended with the concession
                db.connection().use { c -> c.createStatement().executeQuery(
                    "SELECT ended_request_id FROM game_play p WHERE p.id = '${played.id}'").use {
                    assertTrue(it.next()); assertEquals(concession.requestId, it.getString(1))
                } }
                // Conceding again: the play is over
                assertEquals("lifecycleEnded", f.owner.execute(f.owner.command(PlayAction.NextStep).copy(action = GameAction.Concede)).reason)
            }
        }
    }
}
