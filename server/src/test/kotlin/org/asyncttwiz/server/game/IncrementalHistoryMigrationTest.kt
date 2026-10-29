package org.asyncttwiz.server.game

import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.fixtures.ScaleGames as F
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IncrementalHistoryMigrationTest {
    @Test fun destructiveMigrationDeletesAllDependentRowsButKeepsIncrementalAndEmptyGames() {
        OwnedPostgres().start().use { db ->
            val flyway = FoundationMigrations.flyway(db.settings)
            val versions = flyway.info().all().map { it.version.toString() }
            val migration = versions.indexOf("21")
            assertTrue(migration > 0)
            Flyway.configure().configuration(flyway.configuration).target(versions[migration - 1]).load().migrate()
            db.connection().use { connection ->
                connection.autoCommit = false
                val sql = DSL.using(connection, SQLDialect.POSTGRES)
                fun u(n: Int) = UUID.fromString(F.id(n))
                for ((index, account) in listOf(F.a, F.b).withIndex()) sql.execute(
                    "INSERT INTO account(id,username,username_key,email_delivery,email_key,password_phc) VALUES (?::uuid,?,?,?,?,?)",
                    account, "Player$index", "player$index", "$index@example.invalid", "$index@example.invalid", "test-only")
                val definition = F.root().definitions.single()
                sql.execute("INSERT INTO definition VALUES (?,1,?)", definition.digest, GameWire.json.encodeToString(definition.definition))
                sql.execute("INSERT INTO account_session(id,account_id,token_hash,auth_epoch) VALUES (?,?::uuid,decode(repeat('00',32),'hex'),0)", u(800), F.a)
                sql.execute("INSERT INTO activity_installation(installation_id,account_id,session_id,binding,generation) VALUES (?,?::uuid,?,?,?)", u(801), F.a, u(800), u(802), u(803))
                for (n in 0..6) {
                    val game = u(100 + n); val play = u(200 + n); val invitation = u(300 + n)
                    val round = u(400 + n); val start = u(500 + n); val request = u(700 + n)
                    for (id in listOf(start, request)) {
                        sql.execute("INSERT INTO request_identity VALUES (?,?::uuid,'game',?,repeat('0',64))", id, F.a, game.toString())
                    }
                    sql.execute("INSERT INTO game(id,game_name,inviter_id,recipient_id,preparation_round,current_invitation) VALUES (?,'Migration test',?::uuid,?::uuid,?,?)", game, F.a, F.b, round, invitation)
                    sql.execute("INSERT INTO invitation VALUES (?,?,'ACCEPTED')", invitation, game)
                    for ((ordinal, id) in listOf(start, request).withIndex()) sql.execute(
                        "INSERT INTO game_inbox(request_id,game_id,account_id,ingress_ordinal,stream_id,generation,command) VALUES (?,?,?::uuid,?,?,1,'{}')", id, game, F.a, ordinal + 1, u(900))
                    val root = F.root().copy(gameId = game.toString(), id = play.toString(), preparationRound = round.toString(),
                        startRequestId = start.toString(), root = PlayRoot(F.id(600 + n), F.id(6)), currentNodeId = F.id(600 + n))
                    val command = GameCommand(request.toString(), GameScope(root.gameId, playId = root.id),
                        GameAction.Play(PlayAction.AdjustLife(F.a, "1")), F.id(900), "1")
                    val accepted = GameplayRules.change(root, F.a, command)
                    var node = accepted.mutation!!
                    when (n) {
                        0, 3, 6 -> node = node.copy(schemaVersion = 1, bookkeeping = null, transition = node.transition.copy(delta = null))
                        1 -> node = node.copy(transition = node.transition.copy(delta = null))
                        4 -> node = node.copy(transition = node.transition.copy(delta = node.transition.delta!!.copy(
                            entities = listOf(GameplayEntityDelta("annotations", values = emptyList())))))
                        5 -> node = node.copy(parentId = F.id(999))
                    }
                    sql.execute("INSERT INTO game_play(id,game_id,preparation_round,start_request_id,root_id,payload,initial_payload) VALUES (?,?,?,?,?,?,?)",
                        play, game, round, start, UUID.fromString(root.root.id), GameWire.json.encodeToString(root), GameWire.json.encodeToString(root))
                    sql.execute("UPDATE game SET current_play = ? WHERE id = ?", play, game)
                    val group = accepted.state!!.currentGroup!!
                    sql.execute("INSERT INTO gameplay_group VALUES (?,?,?,?::uuid,?,?,?::jsonb)", UUID.fromString(group.id), game, play, F.a,
                        UUID.fromString(group.firstNodeId), UUID.fromString(group.lastNodeId), GameWire.json.encodeToString(group))
                    sql.execute("INSERT INTO gameplay_mutation(id,game_id,play_id,parent_id,branch_id,author_id,request_id,group_id,effective_ordinal,payload,retained) VALUES (?,?,?,?,?,?::uuid,?,?,1,?::jsonb,?)",
                        UUID.fromString(node.id), game, play, UUID.fromString(node.parentId), UUID.fromString(node.branchId), F.a, request,
                        UUID.fromString(group.id), GameWire.json.encodeToString(node), n !in listOf(3, 6))
                    if (n == 6) sql.execute("INSERT INTO gameplay_tree_control VALUES (?,'redo',?,?::jsonb)", play, F.a,
                        """{"entries":[{"mutation":${GameWire.json.encodeToString(node)},"successorId":null,"otherChildIds":[]}]}""")
                    // Populate every non-cascading game/play dependency to exercise deletion order.
                    sql.execute("INSERT INTO game_play_mutation VALUES (?,?,?,'{}')", u(1000 + n), play, request)
                    sql.execute("INSERT INTO game_chat(id,game_id,play_id,author_id,text,revision) VALUES (?,?,?,?::uuid,'test',1)", request, game, play, F.a)
                    sql.execute("INSERT INTO player_preparation(game_id,account_id) VALUES (?,?::uuid)", game, F.a)
                    sql.execute("INSERT INTO game_definition VALUES (?,?)", game, definition.digest)
                    sql.execute("INSERT INTO game_revision VALUES (?,1,'{}')", game)
                    sql.execute("INSERT INTO game_outbox(game_id,account_id,request_id,result) VALUES (?,?::uuid,?,'{}')", game, F.a, request)
                    sql.execute("INSERT INTO game_stream VALUES (?,?::uuid,?,?,?,?,1)", game, F.a, u(1100 + n), u(1200 + n), u(1300 + n), u(1400 + n))
                    sql.execute("INSERT INTO account_activity(account_id,activity_id,kind,game_id,identity) VALUES (?::uuid,?,'GAME',?,?)", F.a, n + 1, game, request)
                    sql.execute("INSERT INTO activity_summary(account_id,game_id,kind,activity_id,identity) VALUES (?::uuid,?,'GAME',?,?)", F.a, game, n + 1, request)
                    sql.execute("INSERT INTO activity_push(generation,game_id,kind,activity_id,identity,first_at,due_at,expires_at) VALUES (?,?,'GAME',?,?,now(),now(),now())", u(803), game, n + 1, request)
                }
                connection.commit()
            }
            Flyway.configure().configuration(flyway.configuration).target("21").load().migrate()
            assertEquals(versions.take(migration + 1), flyway.info().applied().map { it.version.toString() })
            val tables = listOf("game_play", "gameplay_mutation", "gameplay_group", "game_inbox", "game_outbox", "game_revision", "game_stream",
                "game_chat", "player_preparation", "game_definition", "invitation", "account_activity", "activity_summary", "activity_push")
            db.connection().use { connection ->
                val sql = DSL.using(connection, SQLDialect.POSTGRES)
                assertEquals(setOf(F.id(102), F.id(103)), sql.fetch("SELECT id::text FROM game").map { it.get(0, String::class.java) }.toSet())
                assertEquals(2, sql.fetchCount(DSL.table("account")))
                for (table in tables) {
                    assertEquals(0, sql.fetchOne("SELECT count(*) FROM $table WHERE game_id NOT IN (?::uuid,?::uuid)", F.id(102), F.id(103))!!.get(0, Int::class.java), table)
                }
                assertEquals(2, sql.fetchCount(DSL.table("game_play_mutation")))
            }
            // V41 (rules v29 library layers) then deletes every game that started a play, with all its rows.
            flyway.migrate()
            assertEquals(versions, flyway.info().applied().map { it.version.toString() })
            db.connection().use { connection ->
                val sql = DSL.using(connection, SQLDialect.POSTGRES)
                assertEquals(0, sql.fetchCount(DSL.table("game")))
                assertEquals(2, sql.fetchCount(DSL.table("account")))
                for (table in tables + listOf("game_play_mutation", "gameplay_tree_control")) assertEquals(0, sql.fetchCount(DSL.table(table)), table)
            }
        }
    }
}
