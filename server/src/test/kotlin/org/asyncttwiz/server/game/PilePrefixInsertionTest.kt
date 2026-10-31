package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PilePrefixInsertionTest {
    @Test fun `real PostgreSQL FIFO keeps individual prefix success source and anchor conflicts partial outcomes and exact accepted records`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val library = f.root.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == f.a.account.accountId }
                val field = f.root.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == f.a.account.accountId }
                val hand = f.root.board.containers.single { it.zone == PlayZone.HAND && it.accountId == f.a.account.accountId }
                val x = library.cards[0]; val a = library.cards[1]; val b = library.cards[2]; val y = library.cards[3]
                val allCommands = mutableListOf<GameCommand>()
                val allResults = mutableListOf<GameResult>()
                fun current() = f.games.head(f.a, f.game.id).play!!
                fun members() = current().board.containers.single { it.id == field.id }.cards
                fun command(action: PlayAction, invocation: String? = null) = f.owner.command(action).copy(invocationId = invocation)
                fun batch(commands: List<GameCommand>): List<GameResult> {
                    // Real ingress, all admitted before any FIFO execution/outcome lookup.
                    val receipts = commands.map { f.owner.admit(it) as GameReceipt }
                    assertEquals(List(commands.size - 1) { 1L }, receipts.map { it.ingressOrdinal.toLong() }.zipWithNext { l, r -> r - l })
                    assertEquals(receipts.first(), f.owner.admit(commands.first()))
                    repeat(commands.size) { assertTrue(f.games.executeOne()) }; assertFalse(f.games.executeOne())
                    val results = commands.map(f.owner::result)
                    assertEquals(receipts.map { it.ingressOrdinal }, results.map { it.ingressOrdinal })
                    allCommands += commands; allResults += results
                    commands.zip(results).forEach { (input, result) ->
                        assertEquals(result, f.owner.admit(input)) // Original outcome, not another application.
                        assertEquals(input.requestId, result.requestId)
                        if (result.outcome != "accepted") { assertNull(result.mutation); assertNull(result.commitRevision) }
                    }
                    return results
                }
                fun execute(action: PlayAction) = batch(listOf(command(action))).single()
                fun reset(): PlayPlacement {
                    for (id in library.cards) {
                        val card = current().board.cards.single { it.id == id }
                        if (card.containerId != library.id) assertEquals("accepted", execute(PlayAction.MoveCard(id,
                            MoveSource(card.containerId, card.placement), MoveDestination(library.id, MoveInsertion.BOTTOM))).outcome)
                    }
                    assertEquals("accepted", execute(PlayAction.MoveCard(x, MoveSource(library.id),
                        MoveDestination(field.id, MoveInsertion.EMPTY, BoardPosition(2, 1)))).outcome)
                    return current().board.cards.single { it.id == x }.placement!!
                }
                fun prefix(p: PlayPlacement, card: String, after: String? = null, pending: List<String> = emptyList()) =
                    PlayAction.MoveCard(card, MoveSource(library.id), MoveDestination(field.id, MoveInsertion.PILE_GAP,
                        p.position, p.pileId, afterId = after, beforeId = x, targetCardId = x, pendingIds = pending))
                fun leave(p: PlayPlacement, card: String) = PlayAction.MoveCard(card, MoveSource(field.id, p),
                    MoveDestination(hand.id, MoveInsertion.UNORDERED))

                var p = reset()
                val before = current()
                val invocation = gameplayId()
                val pair = listOf(command(prefix(p, a, pending = listOf(b)), invocation), command(prefix(p, b, after = a), invocation))
                val success = batch(pair)
                assertEquals(listOf("accepted", "accepted"), success.map { it.outcome })
                assertEquals(listOf(a, b, x), members())
                assertEquals(1, success.map { it.mutation!!.invocationId }.distinct().size)
                assertEquals(CardLocation(field.id, p, beforeId = x), success[0].mutation!!.transition.movement!!.to)
                assertEquals(CardLocation(field.id, p, afterId = a, beforeId = x), success[1].mutation!!.transition.movement!!.to)
                var predicted = before
                pair.zip(success).forEach { (input, result) ->
                    val change = GameplayRules.change(predicted, f.a.account.accountId, input)
                    assertEquals(result.mutation, change.mutation); predicted = change.state!!
                }
                assertEquals(current(), predicted)
                val noOp = prefix(p, a, pending = listOf(b)).copy(source = MoveSource(field.id, p))
                assertEquals("noOp", execute(noOp).outcome)

                p = reset()
                val anchorInvocation = gameplayId()
                val anchorConflict = batch(listOf(command(prefix(p, a, pending = listOf(b)), anchorInvocation),
                    command(leave(p, a)), command(prefix(p, b, after = a), anchorInvocation),
                    command(PlayAction.AdjustLife(f.a.account.accountId, "1"))))
                assertEquals(listOf("accepted", "accepted", "rejected", "accepted"), anchorConflict.map { it.outcome })
                assertEquals("movementConflict", anchorConflict[2].reason)
                assertEquals(listOf(x), members()); assertEquals(hand.id, current().board.cards.single { it.id == a }.containerId)
                assertEquals(library.id, current().board.cards.single { it.id == b }.containerId)

                p = reset()
                val sourceInvocation = gameplayId()
                val sourceConflict = batch(listOf(command(PlayAction.MoveCard(b, MoveSource(library.id), MoveDestination(hand.id, MoveInsertion.UNORDERED))),
                    command(prefix(p, a, pending = listOf(b)), sourceInvocation), command(prefix(p, b, after = a), sourceInvocation)))
                assertEquals(listOf("accepted", "accepted", "rejected"), sourceConflict.map { it.outcome })
                assertEquals("movementConflict", sourceConflict[2].reason); assertEquals(listOf(a, x), members())

                p = reset()
                val failedFirst = batch(listOf(command(PlayAction.MoveCard(a, MoveSource(library.id), MoveDestination(hand.id, MoveInsertion.UNORDERED))),
                    command(prefix(p, a, pending = listOf(b))), command(prefix(p, b, after = a)),
                    command(PlayAction.SetTapped(x, true))))
                assertEquals(listOf("accepted", "rejected", "rejected", "accepted"), failedFirst.map { it.outcome })
                assertEquals(listOf(x), members()) // No substituted source/top and no stop-the-queue barrier.

                p = reset()
                val capacity = batch(listOf(command(prefix(p, a, pending = listOf(b))), command(prefix(p, y, after = a)), command(prefix(p, b, after = a))))
                assertEquals(listOf("accepted", "accepted", "rejected"), capacity.map { it.outcome })
                assertEquals("movementConflict", capacity[2].reason); assertEquals(listOf(a, y, x), members())

                p = reset()
                val target = batch(listOf(command(prefix(p, a, pending = listOf(b))), command(leave(p, x)), command(prefix(p, b, after = a))))
                assertEquals(listOf("accepted", "accepted", "rejected"), target.map { it.outcome })
                assertEquals("movementConflict", target[2].reason); assertEquals(listOf(a), members())

                val history = f.games.history(f.b, f.game.id, f.root.id, "0")
                val mutations = allResults.mapNotNull { it.mutation }
                assertEquals(mutations, history.mutations)
                var replay = f.root
                for (mutation in history.mutations) {
                    val next = GameplayHistory.replay(replay, mutation)
                    assertEquals("accepted", next.outcome)
                    assertEquals(replay.board, GameplayHistory.reverse(next.state!!, mutation))
                    replay = next.state!!
                }
                assertEquals(current(), replay)
                val revisions = f.games.revisions(f.b, f.game.id, f.game.revision)
                assertEquals(mutations, revisions.revisions.mapNotNull { it.mutation })
                db.connection().use { connection ->
                    connection.createStatement().executeQuery("SELECT payload FROM gameplay_mutation ORDER BY effective_ordinal").use { rows ->
                        val retained = mutableListOf<GameplayMutation>()
                        while (rows.next()) retained += GameWire.json.decodeFromString<GameplayMutation>(rows.getString(1))
                        assertEquals(mutations, retained)
                    }
                    connection.createStatement().executeQuery("SELECT result FROM game_inbox WHERE result IS NOT NULL ORDER BY ingress_ordinal").use { rows ->
                        val retained = mutableListOf<GameResult>()
                        while (rows.next()) retained += GameWire.json.decodeFromString<GameResult>(rows.getString(1))
                        assertEquals(allResults, retained.filter { it.requestId in allCommands.map { c -> c.requestId } })
                    }
                }
                println("PASS owned PostgreSQL FIFO: A then B => [A,B,X]; individual original results and exact accepted movement/codec/replay/inverse/revision/SQL records; no-op; prefix/source/first-source/capacity/addressed-target conflicts retain partial outcomes and later independent edits")
            }
        }
    }
}
