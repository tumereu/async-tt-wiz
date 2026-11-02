package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Rules v36 (Arcanum integration spec §4, phase I0; P8): the server validates and stores the engine's events like any
 * other action: a tagged engine batch is accepted whole and kept, a stale or malformed one is refused and changes
 * nothing. */
class AutomatumRuntimeTest {
    // SYS-055, SYS-057
    @Test fun `engine batches are stored with their tags and refused whole when stale or malformed`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val owner = f.a.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                fun batch(actions: List<PlayAction>, tags: List<MtgTag?> = emptyList(), stream: GameplayFixture.Stream = f.owner) =
                    stream.command(PlayAction.NextStep).copy(action = GameAction.PlayBatch(actions, tags))
                val library = state().board.zoneContainer(owner, PlayZone.LIBRARY)!!
                val graveyard = state().board.zoneContainer(owner, PlayZone.GRAVEYARD)!!
                val card = library.cards.first()
                val effect = EngineRecord("effect-1", EngineRecordKind.Effect, 2, """{"mechanics":[]}""")
                val tag = MtgTag(listOf("milled", "put-into-graveyard"), MtgCause(owner, name = "Thought Sieve"))

                // An automated batch: the switch, a mill and an effect record, each member with its own tag.
                val accepted = f.owner.execute(batch(listOf(PlayAction.Mtg(MtgAction.SetAutomatum(true)),
                    PlayAction.MoveCard(card, MoveSource(library.id, edge = MoveSourceEdge.TOP),
                        MoveDestination(graveyard.id, MoveInsertion.UNORDERED)),
                    PlayAction.Mtg(MtgAction.SetRecord(effect.id, null, effect))), listOf(null, tag)))
                assertEquals("accepted", accepted.outcome, accepted.reason)
                val board = state().board
                assertTrue(board.automatum)
                assertEquals(listOf(effect), board.engineRecords)
                assertEquals(1, board.cards.single { it.id == card }.zoneChanges)
                val history = f.games.history(f.b, f.game.id, f.root.id, "0").mutations
                assertEquals(listOf(null, tag, null), history.takeLast(3).map { it.tag })

                // Stale: the record changed since the engine read it. The whole batch is refused.
                fun refused(reason: String, input: GameCommand) {
                    val before = state()
                    val result = f.recipient.execute(input)
                    assertEquals("rejected", result.outcome)
                    assertEquals(reason, result.reason)
                    assertEquals(before, state())
                }
                refused("inapplicable", batch(listOf(PlayAction.SetLife(owner, "19"),
                    PlayAction.Mtg(MtgAction.SetRecord(effect.id, null, effect.copy(data = "{}")))), stream = f.recipient))
                refused("missingReference", batch(listOf(PlayAction.Mtg(MtgAction.SetCounter(MtgRef.Card(card, 0), "+1/+1",
                    null, TypedCounter("+1/+1", 1, 3)))), stream = f.recipient))
                // Malformed engine data never reaches the game.
                val before = state()
                val malformed = batch(listOf(PlayAction.Mtg(MtgAction.SetRecord("x", null,
                    EngineRecord("x", EngineRecordKind.Effect, data = "{not json")))), stream = f.recipient)
                assertEquals("invalidInput", assertThrows(GameProtocolFailure::class.java) { f.recipient.admit(malformed) }.message)
                assertEquals(before, state())

                // Either player may switch Automatum, and the stored game replays exactly from its history.
                assertEquals("accepted", f.recipient.execute(f.recipient.command(PlayAction.Mtg(MtgAction.SetAutomatum(false)))).outcome)
                assertFalse(state().board.automatum)
                var replay = f.root
                f.games.history(f.b, f.game.id, f.root.id, "0").mutations.forEach { replay = GameplayHistory.replay(replay, it).state!! }
                assertEquals(state(), replay)
            }
        }
    }

    // SYS-060, SYS-061, SYS-057
    @Test fun `an open flow is reverted on the server whoever made its steps, and big automated batches are taken whole`() {
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
                // Switching off reverts it all, both players' steps, in one server process
                val reverted = f.recipient.execute(f.recipient.command(PlayAction.NextStep).copy(action = GameAction.RevertFlow))
                assertEquals("accepted", reverted.outcome, reverted.reason)
                assertEquals(start.board, state().board)
                assertEquals(start.currentNodeId, state().currentNodeId)
                assertEquals("noFlow", f.owner.execute(f.owner.command(PlayAction.NextStep).copy(action = GameAction.RevertFlow)).reason)
            }
        }
    }
}
