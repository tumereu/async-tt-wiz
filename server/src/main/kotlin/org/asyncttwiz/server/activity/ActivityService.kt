package org.asyncttwiz.server.activity

import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.generated.tables.Account.ACCOUNT
import org.asyncttwiz.server.generated.tables.AccountSession.ACCOUNT_SESSION as SESSION
import org.asyncttwiz.server.generated.tables.ActivityCursor.ACTIVITY_CURSOR as CURSOR
import org.asyncttwiz.server.generated.tables.AccountActivity.ACCOUNT_ACTIVITY as EVENT
import org.asyncttwiz.server.generated.tables.ActivitySummary.ACTIVITY_SUMMARY as SUMMARY
import org.asyncttwiz.server.generated.tables.ActivityInstallation.ACTIVITY_INSTALLATION as INSTALL
import org.asyncttwiz.server.generated.tables.ActivityPush.ACTIVITY_PUSH as PUSH
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.Invitation.INVITATION
import org.asyncttwiz.server.generated.tables.GameplayGroup.GAMEPLAY_GROUP
import org.asyncttwiz.server.generated.tables.GameplayMutation.GAMEPLAY_MUTATION
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

class ActivityFailure(val status: Int, val code: String) : RuntimeException(code)

/** Account-local cursor, never a game command or shared observation. */
class ActivityService(private val runtime: ServiceRuntime, private val clock: Clock = Clock.systemUTC()) {
    private fun now() = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
    private fun <T> transaction(work: (DSLContext) -> T): T = runtime.transaction { connection ->
        val savepoint = connection.setSavepoint()
        try { Result.success(work(DSL.using(connection, SQLDialect.POSTGRES))) }
        catch (failure: ActivityFailure) { connection.rollback(savepoint); Result.failure<T>(failure) }
    }.getOrThrow()
    private fun account(principal: AccountService.Principal) = UUID.fromString(principal.account.accountId)

    fun register(principal: AccountService.Principal, input: RegisterInstallation): ActivityRegistration {
        if (!ActivityWire.valid(input)) throw ActivityFailure(400, "invalid_request")
        return transaction { sql ->
            val id = UUID.fromString(input.installationId)
            val old = sql.selectFrom(INSTALL).where(INSTALL.INSTALLATION_ID.eq(id)).forUpdate().fetchOne()
            val binding = UUID.fromString(input.binding)
            val same = old?.sessionId == principal.sessionId && old.binding == binding && old.accountId == account(principal)
            val generation = if (same) old!!.generation!! else UUID.randomUUID()
            val invalid = old?.invalidTokenHash
            val token = input.pushToken?.takeUnless { tokenHash(it) == invalid }
            // Remove queued old-account hints before transferring this installation's ownership.
            if (!same && old != null) sql.deleteFrom(INSTALL).where(INSTALL.INSTALLATION_ID.eq(id)).execute()
            sql.insertInto(INSTALL).set(INSTALL.INSTALLATION_ID, id).set(INSTALL.ACCOUNT_ID, account(principal))
                .set(INSTALL.SESSION_ID, principal.sessionId).set(INSTALL.BINDING, binding).set(INSTALL.GENERATION, generation)
                .set(INSTALL.PUSH_TOKEN, token).set(INSTALL.INVALID_TOKEN_HASH, invalid)
                .onConflict(INSTALL.INSTALLATION_ID).doUpdate().set(INSTALL.PUSH_TOKEN, token).execute()
            ActivityRegistration(generation.toString())
        }
    }

    fun unregister(principal: AccountService.Principal, installation: String) {
        if (!DeckValidation.uuid(installation)) throw ActivityFailure(400, "invalid_request")
        transaction { sql -> sql.deleteFrom(INSTALL).where(INSTALL.INSTALLATION_ID.eq(UUID.fromString(installation)))
            .and(INSTALL.ACCOUNT_ID.eq(account(principal))).and(INSTALL.SESSION_ID.eq(principal.sessionId)).execute() }
    }

    /** Exact attempt/group lookup under the ordinary game lock. No replacement action or observation. */
    fun target(principal: AccountService.Principal, kind: ActivityKind, gameId: String, identity: String): ActivityTarget {
        if (!DeckValidation.uuid(gameId) || !DeckValidation.uuid(identity)) throw ActivityFailure(400, "invalid_request")
        return transaction { sql ->
            val owner = account(principal); val game = UUID.fromString(gameId); val id = UUID.fromString(identity)
            val row = sql.selectFrom(GAME).where(GAME.ID.eq(game)).forUpdate().fetchOne()
            val unavailable = ActivityTarget(gameId, ActivityDestination.UNAVAILABLE)
            if (row == null || owner !in listOf(row.inviterId, row.recipientId) || row.lifecycle !in listOf("LOBBY", "ACTIVE"))
                return@transaction unavailable
            val invitation = sql.selectFrom(INVITATION).where(INVITATION.ID.eq(row.currentInvitation)).fetchSingle()
            if (owner == row.recipientId && invitation.state !in listOf("PENDING", "ACCEPTED")) return@transaction unavailable
            if (kind == ActivityKind.INVITATION && row.recipientId == owner && row.currentInvitation == id &&
                row.lifecycle == "LOBBY" && invitation.state == "PENDING")
                return@transaction ActivityTarget(gameId, ActivityDestination.INVITATION, invitationId = identity)
            if (kind == ActivityKind.GAME && row.lifecycle == "ACTIVE" && (owner == row.inviterId || invitation.state == "ACCEPTED")) {
                val group = sql.selectFrom(GAMEPLAY_GROUP).where(GAMEPLAY_GROUP.ID.eq(id))
                    .and(GAMEPLAY_GROUP.GAME_ID.eq(game)).and(GAMEPLAY_GROUP.PLAY_ID.eq(row.currentPlay)).fetchOne()
                if (group != null) {
                    val first = sql.selectFrom(GAMEPLAY_MUTATION).where(GAMEPLAY_MUTATION.ID.eq(group.firstNodeId))
                        .and(GAMEPLAY_MUTATION.PLAY_ID.eq(row.currentPlay)).fetchSingle()
                    return@transaction ActivityTarget(gameId, ActivityDestination.GROUP, playId = row.currentPlay.toString(),
                        groupId = identity, boundaryNodeId = first.parentId.toString())
                }
            }
            ActivityTarget(gameId, ActivityDestination.CURRENT, playId = row.currentPlay?.toString())
        }
    }

    /** Bounded maintenance also runs without FCM or an account reopening the app. */
    fun pruneExpired() = transaction { sql ->
        val owners = sql.selectDistinct(EVENT.ACCOUNT_ID).from(EVENT).where(EVENT.CREATED_AT.lt(now().minusDays(30)))
            .orderBy(EVENT.ACCOUNT_ID).limit(100).fetch(EVENT.ACCOUNT_ID)
        for (owner in owners) {
            val state = sql.selectFrom(CURSOR).where(CURSOR.ACCOUNT_ID.eq(owner)).forUpdate().fetchSingle()
            prune(sql, owner!!, state.retentionFloor!!)
        }
    }

    private fun prune(sql: DSLContext, owner: UUID, previous: Long): Long {
        val expired = sql.select(DSL.max(EVENT.ACTIVITY_ID)).from(EVENT).where(EVENT.ACCOUNT_ID.eq(owner))
            .and(EVENT.CREATED_AT.lt(now().minusDays(30))).fetchOne(0, Long::class.java)
        val floor = maxOf(previous, expired ?: 0)
        if (floor != previous) {
            sql.deleteFrom(EVENT).where(EVENT.ACCOUNT_ID.eq(owner)).and(EVENT.ACTIVITY_ID.le(floor)).execute()
            sql.update(CURSOR).set(CURSOR.RETENTION_FLOOR, floor).where(CURSOR.ACCOUNT_ID.eq(owner)).execute()
        }
        return floor
    }

    fun page(principal: AccountService.Principal, after: String?): ActivityPage = transaction { sql ->
        val owner = account(principal)
        sql.insertInto(CURSOR).set(CURSOR.ACCOUNT_ID, owner).onConflictDoNothing().execute()
        val state = sql.selectFrom(CURSOR).where(CURSOR.ACCOUNT_ID.eq(owner)).forUpdate().fetchSingle()
        // Actual contiguous prefix, not a fictitious minimum based on surviving rows.
        val floor = prune(sql, owner, state.retentionFloor!!)
        val high = state.highWater!!
        val resetParts = after?.takeIf { it.startsWith("r:") }?.split(':')
        val normal = if (after == null || resetParts != null) null else ActivityWire.ordinal(after)
            ?: throw ActivityFailure(400, "invalid_cursor")
        if (normal != null && normal > high) throw ActivityFailure(400, "invalid_cursor")
        var snapshot = high
        var lower = 0L
        if (resetParts != null) {
            if (resetParts.size != 3) throw ActivityFailure(400, "invalid_cursor")
            snapshot = ActivityWire.ordinal(resetParts[1]) ?: throw ActivityFailure(400, "invalid_cursor")
            lower = ActivityWire.ordinal(resetParts[2]) ?: throw ActivityFailure(400, "invalid_cursor")
            if (snapshot > high || lower > snapshot) throw ActivityFailure(400, "invalid_cursor")
            if (snapshot < floor) { snapshot = high; lower = 0 }
        }
        val reset = after == null || resetParts != null || normal!! < floor
        val source = if (reset) SUMMARY else EVENT
        val activity = if (reset) SUMMARY.ACTIVITY_ID else EVENT.ACTIVITY_ID
        val kind = if (reset) SUMMARY.KIND else EVENT.KIND
        val identity = if (reset) SUMMARY.IDENTITY else EVENT.IDENTITY
        val author = if (reset) SUMMARY.AUTHOR_ID else EVENT.AUTHOR_ID
        val game = if (reset) SUMMARY.GAME_ID else EVENT.GAME_ID
        val ownerField = if (reset) SUMMARY.ACCOUNT_ID else EVENT.ACCOUNT_ID
        val rows = sql.select(activity, kind, game, identity, author).from(source)
            .join(GAME).on(GAME.ID.eq(game)).join(INVITATION).on(INVITATION.ID.eq(GAME.CURRENT_INVITATION))
            .where(ownerField.eq(owner)).and(activity.gt(if (reset) lower else normal!!))
            .and(activity.le(if (reset) snapshot else high)).and(visible(owner, kind, identity))
            .orderBy(activity).limit(101).fetch()
        val entries = rows.take(100).map { ActivityHint(it[activity].toString(), ActivityKind.valueOf(it[kind]!!),
            it[game].toString(), it[identity].toString(), it[author]?.toString()) }
        val more = rows.size > 100
        val next = if (more) {
            if (reset) "r:$snapshot:${entries.last().activityId}" else entries.last().activityId
        } else (if (reset) snapshot else high).toString()
        ActivityPage(entries, next, floor.toString(), reset, more)
    }

    companion object {
        internal fun tokenHash(token: String) = java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
        internal fun visible(owner: UUID, kind: org.jooq.Field<String>, identity: org.jooq.Field<UUID>) =
            GAME.LIFECYCLE.`in`("LOBBY", "ACTIVE").and(
                kind.eq("INVITATION").and(GAME.RECIPIENT_ID.eq(owner)).and(INVITATION.STATE.eq("PENDING"))
                    .and(GAME.CURRENT_INVITATION.eq(identity)).or(
                        kind.eq("GAME").and(GAME.INVITER_ID.eq(owner).or(GAME.RECIPIENT_ID.eq(owner)
                            .and(INVITATION.STATE.eq("ACCEPTED"))))))

        /** Call inside the accepting transaction. Gameplay integration supplies the real group ID.
         * Only an opponent's activity is recorded; publication never waits for a delivery window. */
        fun record(sql: DSLContext, owner: UUID, kind: ActivityKind, game: UUID, identity: UUID,
            author: UUID? = null, time: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)) {
            if (author == owner) return
            val id = sql.insertInto(CURSOR).set(CURSOR.ACCOUNT_ID, owner).set(CURSOR.HIGH_WATER, 1L)
                .onConflict(CURSOR.ACCOUNT_ID).doUpdate().set(CURSOR.HIGH_WATER, CURSOR.HIGH_WATER.plus(1L))
                .returning(CURSOR.HIGH_WATER).fetchSingle().highWater!!
            sql.insertInto(EVENT).set(EVENT.ACCOUNT_ID, owner).set(EVENT.ACTIVITY_ID, id).set(EVENT.KIND, kind.name)
                .set(EVENT.GAME_ID, game).set(EVENT.IDENTITY, identity).set(EVENT.AUTHOR_ID, author).set(EVENT.CREATED_AT, time).execute()
            sql.insertInto(SUMMARY).set(SUMMARY.ACCOUNT_ID, owner).set(SUMMARY.GAME_ID, game).set(SUMMARY.KIND, kind.name)
                .set(SUMMARY.ACTIVITY_ID, id).set(SUMMARY.IDENTITY, identity).set(SUMMARY.AUTHOR_ID, author)
                .onConflict(SUMMARY.ACCOUNT_ID, SUMMARY.GAME_ID, SUMMARY.KIND).doUpdate()
                .set(SUMMARY.ACTIVITY_ID, id).set(SUMMARY.IDENTITY, identity).set(SUMMARY.AUTHOR_ID, author).execute()
            val installs = sql.select(INSTALL.GENERATION).from(INSTALL).join(SESSION).on(SESSION.ID.eq(INSTALL.SESSION_ID))
                .join(ACCOUNT).on(ACCOUNT.ID.eq(INSTALL.ACCOUNT_ID)).where(INSTALL.ACCOUNT_ID.eq(owner))
                .and(INSTALL.PUSH_TOKEN.isNotNull).and(SESSION.REVOKED_AT.isNull).and(SESSION.AUTH_EPOCH.eq(ACCOUNT.AUTH_EPOCH))
                .fetch(INSTALL.GENERATION)
            for (generation in installs) {
                val pending = sql.selectFrom(PUSH).where(PUSH.GENERATION.eq(generation)).and(PUSH.GAME_ID.eq(game))
                    .and(PUSH.KIND.eq(kind.name)).forUpdate().fetchOne()
                val first = if (pending?.identity == identity) pending.firstAt!! else time
                val due = if (kind == ActivityKind.INVITATION) time else minOf(time.plusSeconds(2), first.plusSeconds(10))
                val retrying = pending?.identity == identity && pending.attempts!! > 0
                val nextDue = if (retrying) maxOf(due, pending!!.dueAt!!) else due
                sql.insertInto(PUSH).set(PUSH.GENERATION, generation).set(PUSH.GAME_ID, game).set(PUSH.KIND, kind.name)
                    .set(PUSH.ACTIVITY_ID, id).set(PUSH.IDENTITY, identity).set(PUSH.FIRST_AT, first).set(PUSH.DUE_AT, due)
                    .set(PUSH.EXPIRES_AT, time.plusHours(24)).onConflict(PUSH.GENERATION, PUSH.GAME_ID, PUSH.KIND).doUpdate()
                    .set(PUSH.ACTIVITY_ID, id).set(PUSH.IDENTITY, identity).set(PUSH.FIRST_AT, first).set(PUSH.DUE_AT, nextDue)
                    .set(PUSH.EXPIRES_AT, time.plusHours(24)).set(PUSH.ATTEMPTS, if (retrying) pending!!.attempts else 0).execute()
            }
        }
    }
}
