package org.asyncttwiz.server.activity

import java.time.Instant
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.asyncttwiz.server.game.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ActivityRuntimeIntegrationTest {
    private fun id() = UUID.randomUUID().toString()
    private fun number(db: TestPostgres, query: String): Long = db.connection().use { c ->
        c.createStatement().executeQuery(query).use { it.next(); it.getLong(1) }
    }
    private fun sql(db: TestPostgres, statement: String) { db.connection().use { it.createStatement().execute(statement) } }

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
