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
}
