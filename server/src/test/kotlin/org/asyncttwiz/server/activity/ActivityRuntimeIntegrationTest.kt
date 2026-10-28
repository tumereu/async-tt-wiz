package org.asyncttwiz.server.activity

import java.time.Instant
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.asyncttwiz.server.game.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ActivityRuntimeIntegrationTest {
    private fun id() = UUID.randomUUID().toString()
    private fun number(db: OwnedPostgres, query: String): Long = db.connection().use { c ->
        c.createStatement().executeQuery(query).use { it.next(); it.getLong(1) }
    }
    private fun sql(db: OwnedPostgres, statement: String) { db.connection().use { it.createStatement().execute(statement) } }

    @Test fun `real FIFO commits opponent group extensions next groups and no op original retry distinctions atomically`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime); val activity = ActivityService(runtime)
                val second = org.asyncttwiz.server.accounts.AccountService(runtime).let { accounts ->
                    accounts.authenticate(accounts.login("PlayB@example.invalid", "synthetic password")!!.token)!!
                }
                val r1 = activity.register(f.b, RegisterInstallation(id(), id(), "runtime-one"))
                val r2 = activity.register(second, RegisterInstallation(id(), id(), "runtime-two"))
                activity.register(f.b, RegisterInstallation(id(), id()))
                val before = activity.page(f.b, null).nextCursor
                val beforeA = activity.page(f.a, null).nextCursor // after the game-start notice
                val life = f.owner.command(PlayAction.AdjustLife(f.a.account.accountId, "1"))
                val result = f.owner.execute(life)
                val accepted = f.games.head(f.b, f.game.id)
                val group = accepted.play!!.currentGroup!!
                val first = activity.page(f.b, before).entries.single()
                assertEquals(group.id, first.identity); assertEquals(group.author, first.authorId)
                assertTrue(activity.page(f.a, beforeA).entries.isEmpty())
                assertEquals(result, f.owner.admit(life))
                assertFalse(f.games.executeOne())
                assertEquals(listOf(first), activity.page(f.b, before).entries)
                val noop = f.owner.command(PlayAction.SetLife(f.a.account.accountId, "21"))
                assertEquals("noOp", f.owner.execute(noop).outcome)
                assertEquals(accepted, f.games.head(f.a, f.game.id))
                assertEquals(listOf(first), activity.page(f.b, before).entries)
                val repeated = f.owner.command(PlayAction.AdjustLife(f.a.account.accountId, "1"))
                assertNotEquals(life.requestId, repeated.requestId)
                f.owner.execute(repeated)
                val extension = activity.page(f.b, first.activityId).entries.single()
                assertEquals(first.identity, extension.identity)
                assertNotEquals(first.activityId, extension.activityId)
                // Accepted revisions/outbox and notices are immediate when the actor is absent.
                assertEquals(1, f.games.revisions(f.b, f.game.id, accepted.revision).revisions.size)
                assertTrue(f.games.publications().any { it.gameId == f.game.id && it.revision != null })
                assertEquals(2L, number(db, "SELECT count(*) FROM activity_push WHERE kind='GAME'"))
                assertEquals(2L, number(db, "SELECT count(*) FROM activity_push WHERE kind='GAME' AND due_at>=first_at"))
                val sent = mutableListOf<ActivityPushHint>(); val clock = ActivityClock(Instant.now().plusSeconds(3))
                ActivityDelivery(runtime, ActivityPushAdapter { _, hint -> sent += hint; PushResult(PushStatus.DELIVERED) }, clock).use { it.deliverDue() }
                assertEquals(setOf(r1.generation, r2.generation), sent.map { it.generation }.toSet())
                assertEquals(setOf(extension.activityId), sent.map { it.activityId }.toSet())
                assertEquals(extension.identity, activity.page(second, first.activityId).entries.single().identity)
                val target = activity.target(f.b, ActivityKind.GAME, f.game.id, group.id)
                assertEquals(ActivityDestination.GROUP, target.destination)
                assertEquals(f.root.id, target.playId); assertEquals(f.root.root.id, target.boundaryNodeId)
                f.recipient.execute(f.recipient.command(PlayAction.AdjustLife(f.b.account.accountId, "1")))
                f.owner.execute(f.owner.command(PlayAction.AdjustLife(f.a.account.accountId, "1")))
                val next = activity.page(f.b, extension.activityId).entries.single()
                assertNotEquals(group.id, next.identity)
                assertEquals(2L, number(db, "SELECT count(*) FROM activity_push WHERE kind='GAME' AND game_id='${f.game.id}'"))
                val chat = f.owner.command(PlayAction.AdjustLife(f.a.account.accountId, "1")).copy(action = GameAction.SendChat("private chat not in push"))
                f.owner.execute(chat)
                val chatPage = activity.page(f.b, next.activityId)
                assertEquals(chat.requestId, chatPage.entries.single().identity)
                assertTrue(chatPage.readyNotices!!.single().notice!!.newMessage)
                assertTrue(activity.page(f.a, "0").entries.none { it.identity == chat.requestId })
                f.owner.admit(chat)
                assertFalse(f.games.executeOne())
                assertEquals(chatPage, activity.page(f.b, next.activityId))
                println("PASS real accepted FIFO groups/extensions, original/noOp/new-request distinctions, immediate revisions/outbox, every registered push installation and independent polling, exact authorized first-group boundary")
            }
        }
    }

    @Test fun `post hint SQL failure rolls back board group result revision and hints then restart recovers original once`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val runtime = ServiceRuntime(db.settings)
            runtime.start(); gameplayAwait(runtime::ready)
            val f = GameplayFixture(runtime); val activity = ActivityService(runtime)
            activity.register(f.b, RegisterInstallation(id(), id(), "runtime-restart"))
            val cursor = activity.page(f.b, null).nextCursor
            val head = f.games.head(f.a, f.game.id)
            val input = f.owner.command(PlayAction.AdjustLife(f.a.account.accountId, "1"))
            f.owner.admit(input)
            sql(db, """CREATE FUNCTION runtime_fail_outbox() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                BEGIN RAISE EXCEPTION 'run-owned failure after activity insert'; END ${'$'}${'$'};
                CREATE TRIGGER runtime_fail_outbox BEFORE INSERT ON game_outbox FOR EACH ROW EXECUTE FUNCTION runtime_fail_outbox();""")
            try {
                assertThrows(Exception::class.java) { f.games.executeOne() }
                assertEquals(0L, number(db, "SELECT count(*) FROM account_activity WHERE kind='GAME' AND identity<>'${head.play!!.id}'"))
                assertEquals(0L, number(db, "SELECT count(*) FROM gameplay_mutation"))
                assertEquals(0L, number(db, "SELECT count(*) FROM gameplay_group"))
                assertEquals(0L, number(db, "SELECT count(*) FROM game_inbox WHERE request_id='${input.requestId}' AND result IS NOT NULL"))
            } finally { runtime.close() }
            sql(db, "DROP TRIGGER runtime_fail_outbox ON game_outbox; DROP FUNCTION runtime_fail_outbox()")
            ServiceRuntime(db.settings).use { restarted ->
                restarted.start(); gameplayAwait(restarted::ready)
                val games = GameService(restarted); val recovered = ActivityService(restarted)
                assertEquals(head, games.head(f.a, f.game.id))
                assertTrue(games.executeOne()); assertFalse(games.executeOne())
                val hint = recovered.page(f.b, cursor).entries.single()
                assertEquals(games.head(f.b, f.game.id).play!!.currentGroup!!.id, hint.identity)
                assertEquals(1L, number(db, "SELECT count(*) FROM gameplay_mutation"))
                assertEquals(1L, number(db, "SELECT count(*) FROM activity_push WHERE kind='GAME'"))
                val result = games.results(f.a, f.game.id, listOf(input.requestId)).statuses.single().result!!
                assertEquals("accepted", result.outcome)
                assertEquals(result, games.admit(f.a, f.owner.hello, f.owner.connection, input))
                assertEquals(listOf(hint), recovered.page(f.b, cursor).entries)
                println("PASS real SQL rollback after hints leaves no board/group/revision/final-result fragment; restart executes admitted original once and retains one poll/push identity")
            }
        }
    }

    @Test fun `authorized exact invitation target falls back truthfully after attempt changes deletion and other account`() {
        ActivityFixture().use { f ->
            val service = ActivityService(f.runtime); val game = f.create()
            val target = service.target(f.b, ActivityKind.INVITATION, game.id, game.invitationId)
            assertEquals(ActivityDestination.INVITATION, target.destination); assertEquals(game.invitationId, target.invitationId)
            f.accept(game)
            assertEquals(ActivityDestination.CURRENT, service.target(f.b, ActivityKind.INVITATION, game.id, game.invitationId).destination)
            assertEquals(ActivityDestination.CURRENT, service.target(f.a, ActivityKind.GAME, game.id, id()).destination)
            f.accounts.register("Unrelated", "other@example.invalid", "synthetic password")
            val other = f.accounts.authenticate(f.accounts.login("other@example.invalid", "synthetic password")!!.token)!!
            assertEquals(ActivityDestination.UNAVAILABLE, service.target(other, ActivityKind.INVITATION, game.id, game.invitationId).destination)
            f.sql("UPDATE game SET lifecycle='DELETED' WHERE id='${game.id}'")
            assertEquals(ActivityDestination.UNAVAILABLE, service.target(f.b, ActivityKind.INVITATION, game.id, game.invitationId).destination)
            assertThrows(ActivityFailure::class.java) { service.target(f.b, ActivityKind.GAME, "invalid", id()) }
            println("PASS exact pending invitation identity, accepted/stale current fallback, denied/deleted opaque unavailable, validation; lookup emits no game actions")
        }
    }
}
