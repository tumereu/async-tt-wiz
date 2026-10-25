package org.asyncttwiz.server.game

import java.util.UUID
import kotlin.system.measureNanoTime
import kotlinx.serialization.encodeToString
import org.asyncttwiz.fixtures.ScaleGames as F
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.jooq.ExecuteContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultExecuteListener
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IndexedEndpointScaleTest {
    private val json = GameWire.json
    private fun uuid(value: String) = UUID.fromString(value)
    private class Work : DefaultExecuteListener() {
        var statements = 0; var rows = 0
        override fun executeEnd(ctx: ExecuteContext) { statements++ }
        override fun fetchEnd(ctx: ExecuteContext) { rows += ctx.result()?.size ?: 0 }
        fun reset() { statements = 0; rows = 0 }
    }

    /** Bulk fixture import only. Every mutation/board/index was produced by the shared reducers. */
    private fun seed(sql: org.jooq.DSLContext, tree: GameplayTree, size: Int) {
        val root = tree.initial
        for ((account, name) in listOf(F.a to "ScaleA", F.b to "ScaleB")) sql.execute("""INSERT INTO account
            (id,username,username_key,email_delivery,email_key,password_phc) VALUES (?::uuid,?,?,?,?,?)""",
            account, name, name.lowercase(), "$name@example.invalid", "${name.lowercase()}@example.invalid", "fixture")
        sql.execute("""INSERT INTO game(id,game_name,inviter_id,recipient_id,preparation_round,current_invitation,revision,next_ingress)
            VALUES (?::uuid,'Scale game',?::uuid,?::uuid,?::uuid,?::uuid,?,?)""", root.gameId, F.a, F.b, root.preparationRound, F.id(91), size + 1, size + 100)
        sql.execute("INSERT INTO invitation(id,game_id,state) VALUES (?::uuid,?::uuid,'ACCEPTED')", F.id(91), root.gameId)
        val nodes = tree.nodes.values.toList()
        val requests = listOf(root.startRequestId) + nodes.map { it.requestId } + (800001..800010).map(F::id)
        val identity = sql.connectionResult { c -> c.prepareStatement("INSERT INTO request_identity(id,account_id,kind,scope,intent_digest) VALUES (?,?,'gameCommand',?,?)").use { statement ->
            for (request in requests) { statement.setObject(1, uuid(request)); statement.setObject(2, uuid(F.a)); statement.setString(3, root.gameId)
                statement.setString(4, "0".repeat(64)); statement.addBatch() }
            statement.executeBatch()
        } }
        assertEquals(requests.size, identity.size)
        sql.connection { c -> c.prepareStatement("INSERT INTO game_inbox(request_id,game_id,account_id,ingress_ordinal,stream_id,generation,command,result) VALUES (?,?,?,?,?,1,?,'{}')").use { statement ->
            requests.forEachIndexed { index, request ->
                statement.setObject(1, uuid(request)); statement.setObject(2, uuid(root.gameId)); statement.setObject(3, uuid(F.a))
                statement.setLong(4, (index + 1).toLong()); statement.setObject(5, uuid(F.id(90)))
                statement.setString(6, json.encodeToString<GameClientMessage>(F.command(tree, GameAction.Undo, 800009).copy(requestId = request)))
                statement.addBatch()
            }; statement.executeBatch()
        } }
        sql.execute("""INSERT INTO game_play(id,game_id,preparation_round,start_request_id,root_id,payload,initial_payload)
            VALUES (?::uuid,?::uuid,?::uuid,?::uuid,?::uuid,?,?)""", root.id, root.gameId, root.preparationRound, root.startRequestId, root.root.id,
            json.encodeToString(tree.stateAt(tree.currentNodeId)), json.encodeToString(root))
        sql.execute("UPDATE game SET lifecycle='ACTIVE',current_play=?::uuid WHERE id=?::uuid", root.id, root.gameId)
        val groups = nodes.map { requireNotNull(it.bookkeeping!!.toGroup) }.associateBy { it.id }
        sql.connection { c -> c.prepareStatement("INSERT INTO gameplay_group(id,game_id,play_id,author_id,first_node_id,last_node_id,payload) VALUES (?,?,?,?,?,?,?::jsonb)").use { statement ->
            for (group in groups.values) {
                listOf(group.id, root.gameId, root.id, group.author, group.firstNodeId, group.lastNodeId).forEachIndexed { index, value -> statement.setObject(index + 1, uuid(value)) }
                statement.setString(7, json.encodeToString(group)); statement.addBatch()
            }; statement.executeBatch()
        } }
        sql.connection { c -> c.prepareStatement("INSERT INTO gameplay_mutation(id,game_id,play_id,parent_id,branch_id,author_id,request_id,group_id,effective_ordinal,payload) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb)").use { statement ->
            nodes.forEachIndexed { index, node ->
                listOf(node.id, root.gameId, root.id, node.parentId, node.branchId, node.author, node.requestId, node.bookkeeping!!.toGroup!!.id)
                    .forEachIndexed { i, value -> statement.setObject(i + 1, uuid(value)) }
                statement.setLong(9, (index + 1).toLong()); statement.setString(10, json.encodeToString(node)); statement.addBatch()
            }; statement.executeBatch()
        } }
        sql.connection { c -> c.prepareStatement("INSERT INTO gameplay_tree_control(play_id,kind,id,payload) VALUES (?,?,?,?::jsonb)").use { statement ->
            TreeSync.records(TreeSync.generation(tree)).filter { it.kind !in setOf("root", "node", "definition") }.forEach {
                statement.setObject(1, uuid(root.id)); statement.setString(2, it.kind); statement.setString(3, it.id); statement.setString(4, it.payload.toString()); statement.addBatch()
            }; statement.executeBatch()
        } }
        GameplayEndpoint(sql, root.id).initialize(tree, size.toLong())
        // Chat uses the SAME accepted action identities; fixture data is outside timed operations.
        sql.connection { c -> c.prepareStatement("INSERT INTO game_chat(id,game_id,play_id,author_id,text,revision) VALUES (?,?,?,?,'scale chat',?)").use { statement ->
            nodes.forEachIndexed { index, node ->
                listOf(node.requestId, root.gameId, root.id, node.author).forEachIndexed { i, value -> statement.setObject(i + 1, uuid(value)) }
                statement.setLong(5, (index + 1).toLong()); statement.addBatch()
            }; statement.executeBatch()
        } }
    }

    @Test fun coldWarmAnnotationsChatRecoveryAndPagesStayBoundedAtTenThousandActions() {
        val results = linkedMapOf<Int, Map<String, Double>>()
        val endpointBytes = linkedMapOf<Int, Int>()
        for (size in listOf(1000, 10000)) OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val fixture = F.build(size)
            db.connection().use { connection ->
                connection.autoCommit = false
                val work = Work()
                val sql = DSL.using(connection, SQLDialect.POSTGRES)
                seed(sql, fixture, size); connection.commit()
                sql.execute("ANALYZE gameplay_mutation"); sql.execute("ANALYZE game_chat"); connection.commit()
                val observed = DSL.using(sql.configuration().derive(work))
                val store = GameplayStore()
                val initial = PlayStore.read(sql, uuid(F.root().gameId))!!
                endpointBytes[size] = json.encodeToString(initial).length
                assertTrue(initial.annotations.commentsById.isEmpty() && initial.annotations.pingsById.isEmpty())
                val anchor = EventReference(initial.id, initial.currentNodeId)
                val command = F.command(fixture, GameAction.Play(PlayAction.AdjustLife(F.a, "1")), 800001)
                fun read(s: GameplayStore = store): GameplayTree {
                    val state = PlayStore.read(observed, uuid(initial.gameId))!!
                    val revision = observed.fetchOne("SELECT checkpoint_revision FROM game_play WHERE id=?::uuid", initial.id)!!.get(0, Long::class.java)
                    return s.read(observed, state, revision)
                }
                fun change(tree: GameplayTree, action: GameAction, actor: String = F.a, request: Int = 800001, maxIndexed: Int = 8): GameplayTree {
                    val next = F.change(tree, command.copy(requestId = F.id(request), action = action), actor)
                    val revision = observed.fetchOne("SELECT checkpoint_revision FROM game_play WHERE id=?::uuid", initial.id)!!.get(0, Long::class.java) + 1
                    store.save(observed, tree, next, revision)
                    assertEquals(0, tree.work.fullHistoryScans); assertEquals(0, tree.work.rootMaterializations)
                    assertTrue(tree.work.indexedNodes <= maxIndexed)
                    return next
                }
                val operations = linkedMapOf<String, () -> Unit>(
                    "cold-command" to { change(read(GameplayStore()), command.action); Unit },
                    "warm-command" to { val tree = read(); store.committed(size.toLong(), tree); change(read(), command.action); Unit },
                    "comment" to {
                        val next = change(read(), GameAction.Play(PlayAction.EventComment(anchor, "indexed comment")))
                        assertEquals("indexed comment", next.stateAt(next.currentNodeId)!!.annotations.commentsById[next.currentNodeId]!!.text)
                        assertTrue(PlayStore.read(observed, uuid(initial.gameId))!!.annotations.commentsById.isEmpty())
                    },
                    "ping" to { change(read(), GameAction.Play(PlayAction.Ping(anchor, F.pingTarget))); Unit },
                    "unping" to {
                        change(read(), GameAction.Play(PlayAction.Ping(anchor, F.pingTarget)))
                        change(read(), GameAction.Play(PlayAction.Unping(anchor, F.pingTarget)), request = 800002); Unit
                    },
                    "undo" to { change(read(), GameAction.Undo, F.b); Unit },
                    "redo" to {
                        change(read(), GameAction.Undo, F.b)
                        change(read(), GameAction.Redo, F.b, 800002); Unit
                    },
                    "recovery" to { store.recover(observed, initial) },
                    "chat-head" to { assertEquals(size.toString(), ChatStore.sync(observed, uuid(initial.gameId)).revision); PlayStore.read(observed, uuid(initial.gameId)); Unit },
                    "chat-page" to { val page = ChatStore.page(observed, uuid(initial.gameId)); assertEquals(100, page.messages.size); assertNotNull(page.older) },
                    "chat-catchup" to { val page = ChatStore.page(observed, uuid(initial.gameId), after = (size - 3).toLong()); assertEquals(3, page.messages.size); assertNull(page.next) },
                    "chat-insert" to {
                        val game = observed.fetchOne("SELECT * FROM game WHERE id=?::uuid", initial.gameId)!!.into(org.asyncttwiz.server.generated.tables.Game.GAME)
                        val result = ChatStore.apply(observed, game, uuid(F.a), command.copy(requestId = F.id(800010), action = GameAction.SendChat("inserted directly")))
                        assertNull(result.reason); assertEquals("inserted directly", result.message!!.text)
                    },
                    "history-page" to { val page = store.history(observed, initial, size.toLong(), 0); assertEquals(100, page.mutations.size); assertNotNull(page.next) },
                )
                val samples = operations.keys.associateWith { mutableListOf<Double>() }
                val maxima = operations.keys.associateWith { intArrayOf(0, 0) }
                repeat(19) { round -> for ((name, operation) in operations) {
                    work.reset()
                    val elapsed = measureNanoTime(operation) / 1_000_000.0
                    val max = maxima.getValue(name); max[0] = maxOf(max[0], work.statements); max[1] = maxOf(max[1], work.rows)
                    // Primary gate: the same bounded SQL read/write set, independent of timing/JIT.
                    assertTrue(work.statements <= 160, "$name: ${work.statements} statements")
                    assertTrue(work.rows <= 220, "$name: ${work.rows} fetched rows")
                    if (name == "recovery") assertTrue(work.rows <= 10 && work.statements <= 10)
                    if (round >= 6) samples.getValue(name) += elapsed
                    connection.rollback()
                } }
                results[size] = samples.mapValues { it.value.sorted()[it.value.size / 2] }
                println("server scale $size mediansMs=${results[size]} maxStatements/fetchedRows=${maxima.mapValues { it.value.toList() }} endpointBytes=${endpointBytes[size]}")

                // A selective edit can leave later transition bytes identical while changing
                // their complete ancestry identities. A subsequent cold read must see all of
                // those sparse index writes, not only the wire's changed mutation payloads.
                val suffix = (800001..800004).map { request -> change(read(),
                    GameAction.Play(PlayAction.AdjustLife(F.b, "1")), F.b, request).currentNodeId }
                val removed = change(read(), GameAction.Undo, F.a, 800009, maxIndexed = 64)
                val cold = read(GameplayStore())
                for (node in suffix) assertEquals(removed.materializationId(node), cold.materializationId(node))
                assertEquals(removed.stateAt(removed.currentNodeId)!!.board, cold.stateAt(cold.currentNodeId)!!.board)
                connection.rollback()

                // Make almost all rows detached, then verify the retained-window query uses the
                // partial index instead of walking thousands of detached ordinal entries.
                sql.execute("UPDATE gameplay_mutation SET retained=false WHERE effective_ordinal <= ?", size - 50)
                sql.execute("ANALYZE gameplay_mutation")
                val plan = sql.fetch("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM gameplay_mutation WHERE play_id=?::uuid AND retained AND effective_ordinal > 0 ORDER BY effective_ordinal LIMIT 101", initial.id)
                    .joinToString { it.get(0).toString() }
                assertTrue(plan.contains("gameplay_mutation_retained_page"), plan)
                assertEquals(50, store.history(sql, initial, size.toLong(), 0).mutations.size)
                connection.rollback()
            }
        }
        assertTrue(endpointBytes.getValue(10000) < endpointBytes.getValue(1000) * 2)
        for (name in results.getValue(1000).keys) {
            val small = results.getValue(1000).getValue(name); val large = results.getValue(10000).getValue(name)
            println("server scale $name: 1k=$small ms 10k=$large ms ratio=${large / small}")
            assertTrue(large < small * 3, "$name ratio=${large / small}")
            assertTrue(large < 1000, "$name absolute budget: $large ms")
        }
    }
}
