package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Actual isolated PostgreSQL/FIFO execution, not a link-specific store or alternate processor. */
class ProjectionLinkRuntimeTest {
    @Test fun `original occurrence requests pipeline through FIFO relational transitions cleanup retry and immutable replay`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                var prediction = f.root
                val inputs = mutableListOf<GameCommand>()
                val expected = mutableListOf<GameplayChange>()
                val invocation = gameplayId()
                fun plan(action: PlayAction): GameplayChange {
                    val command = f.owner.command(action).copy(invocationId = invocation)
                    val change = GameplayRules.change(prediction, f.a.account.accountId, command)
                    inputs += command; expected += change
                    change.state?.let { prediction = it }
                    return change
                }
                fun link(a: String, b: String) = PlayAction.LinkProjections(ProjectionEndpoint(a), ProjectionEndpoint(b))
                plan(PlayAction.CreateToken(TokenCatalog.blank))
                val token = prediction.board.cards.single { it.token }
                plan(PlayAction.Combat(CombatAction.Open))
                plan(PlayAction.Combat(CombatAction.Declare(prediction.board.combat!!.id, token.id)))
                repeat(3) { plan(PlayAction.Stack(StackAction.AddEffect(token.id, GameplayRules.stackDigest(prediction)))) }
                val (a, b, c) = prediction.board.stackEffects
                val combat = CombatStackRules.occurrences(prediction.board.combat).single()
                plan(link(combat.id, a.id)); plan(link(a.id, b.id)); plan(link(b.id, c.id))
                val originalLinks = prediction.board.projectionLinks.toList()
                plan(PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, token.id),
                    LinkEndpoint(LinkEndpointKind.PLAYER, f.b.account.accountId)))
                val repeated = plan(link(b.id, a.id)); assertEquals("noOp", repeated.outcome)
                plan(PlayAction.MoveCard(token.id, MoveSource(token.containerId, token.placement),
                    MoveDestination(token.containerId, MoveInsertion.EMPTY, BoardPosition(90, 3))))
                assertEquals(originalLinks, prediction.board.projectionLinks)
                val area = prediction.board.combat!!
                plan(PlayAction.Combat(CombatAction.Place(token.id, CombatDestination(area.id, CombatRole.BLOCKER,
                    area.pairs.single().id), CombatStackRules.location(area, combat.id))))
                assertEquals(originalLinks, prediction.board.projectionLinks)
                val beforeDisappear = prediction
                val removal = plan(PlayAction.RemoveToken(token.id))
                val cleanup = removal.mutation!!.transition.combatStack!!
                assertEquals(originalLinks.filter { combat.id in listOf(it.first, it.second) }, cleanup.removedLinks)
                assertEquals(List(3) { beforeDisappear.board.cards.single { it.id == token.id } }, prediction.board.stackEffects.map { it.frozen })
                assertTrue(prediction.board.links.isEmpty())
                assertEquals(2, prediction.board.projectionLinks.size)
                val stale = plan(link(combat.id, b.id)); assertEquals("missingReference", stale.reason)
                val resolved = plan(PlayAction.Stack(StackAction.RemoveEffect(a.id)))
                assertEquals(originalLinks.filter { it.first != combat.id && it.second != combat.id &&
                    a.id in listOf(it.first, it.second) }, resolved.mutation!!.transition.combatStack!!.removedLinks)
                val remaining = prediction.board.projectionLinks.single()
                plan(PlayAction.RemoveProjectionLink(remaining.id))
                assertEquals("noOp", plan(PlayAction.RemoveProjectionLink(remaining.id)).outcome)
                assertEquals("missingReference", plan(link(token.id, b.id)).reason) // no source alias
                plan(link(b.id, c.id)) // frozen occurrences may create new own links
                val frozenLink = prediction.board.projectionLinks.single()
                plan(PlayAction.Combat(CombatAction.Close(area.id))) // does not resolve Stack or remove surviving link
                assertEquals(listOf(frozenLink), prediction.board.projectionLinks)
                val expectedHead = prediction
                val before = f.games.head(f.a, f.game.id)

                // Admit every captured request before executing or looking up any result.
                val receipts = inputs.map { f.owner.admit(it) as GameReceipt }
                assertEquals(List(inputs.size - 1) { 1L }, receipts.map { it.ingressOrdinal.toLong() }.zipWithNext { x, y -> y - x })
                assertEquals(receipts.first(), f.owner.admit(inputs.first()))
                repeat(inputs.size) { assertTrue(f.games.executeOne()) }; assertFalse(f.games.executeOne())
                val results = inputs.map(f.owner::result)
                assertEquals(expected.map { it.outcome }, results.map { it.outcome })
                assertEquals(expected.map { it.reason }, results.map { it.reason })
                assertEquals(expected.map { it.mutation }, results.map { it.mutation })
                val head = f.games.head(f.a, f.game.id)
                assertEquals(expectedHead, head.play)
                assertEquals(before.revision.toLong() + expected.count { it.mutation != null }, head.revision.toLong())
                val history = f.games.history(f.b, f.game.id, f.root.id, "0")
                assertEquals(results.mapNotNull { it.mutation }, history.mutations)
                var replay = f.root
                history.mutations.forEach { mutation -> replay = GameplayHistory.replay(replay, mutation).state!! }
                assertEquals(head.play, replay)
                assertEquals(3, beforeDisappear.board.projectionLinks.size) // retained prefix remains immutable
                assertNotNull(beforeDisappear.board.cards.singleOrNull { it.id == token.id })
                results.forEachIndexed { index, result ->
                    assertEquals(result, f.owner.admit(inputs[index])) // accepted, rejected and no-op original retries
                    if (result.outcome != "accepted") { assertNull(result.mutation); assertNull(result.commitRevision) }
                    db.connection().use { connection ->
                        connection.prepareStatement("""SELECT i.result, o.result, o.revision, m.payload
                            FROM game_inbox i JOIN game_outbox o ON o.request_id = i.request_id
                            LEFT JOIN gameplay_mutation m ON m.request_id = i.request_id
                            WHERE i.request_id = ?::uuid""").use { statement ->
                            statement.setString(1, result.requestId)
                            statement.executeQuery().use { rows ->
                                assertTrue(rows.next())
                                assertEquals(result, GameWire.json.decodeFromString<GameResult>(rows.getString(1)))
                                assertEquals(result, GameWire.json.decodeFromString<GameServerMessage>(rows.getString(2)))
                                val revision = rows.getString(3)?.let { GameWire.json.decodeFromString<GameServerMessage>(it) as GameRevision }
                                assertEquals(result.commitRevision, revision?.resultRevision)
                                assertEquals(result.mutation, revision?.mutation)
                                assertEquals(result.mutation, rows.getString(4)?.let { GameWire.json.decodeFromString<GameplayMutation>(it) })
                                assertFalse(rows.next())
                            }
                        }
                    }
                }
                val firstLinkInput = inputs.first { (it.action as GameAction.Play).action is PlayAction.LinkProjections }
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) {
                    f.owner.admit(firstLinkInput.copy(action = GameAction.Play(link(b.id, c.id))))
                }.code)
                assertEquals(head, f.games.head(f.a, f.game.id)); assertFalse(f.games.executeOne())
                println("PASS actual PostgreSQL original/FIFO relational transitions: same-source distinct occurrences, deterministic exact endpoints, undirected no-op, reposition survives, same-node incident cleanup, frozen sibling links retained/recreated, explicit removal, immutable replay, original retries and no source alias")
            }
        }
    }
}
