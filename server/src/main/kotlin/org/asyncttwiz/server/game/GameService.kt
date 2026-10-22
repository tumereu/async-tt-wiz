package org.asyncttwiz.server.game

import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.GameInbox.GAME_INBOX
import org.asyncttwiz.server.generated.tables.GameOutbox.GAME_OUTBOX
import org.asyncttwiz.server.generated.tables.GameRevision.GAME_REVISION
import org.asyncttwiz.server.generated.tables.GameStream.GAME_STREAM
import org.asyncttwiz.server.generated.tables.Invitation.INVITATION
import org.asyncttwiz.server.generated.tables.RequestIdentity.REQUEST_IDENTITY
import org.asyncttwiz.server.generated.tables.records.GameRecord
import org.asyncttwiz.server.lobby.LobbyService
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

class GameProtocolFailure(val code: String, val retryable: Boolean = false) : RuntimeException(code)

/** One durable request/execution domain per persistent game, extended by preparation/gameplay. */
class GameService(private val runtime: ServiceRuntime) {
    private val json = GameWire.json
    private val lobby = LobbyService(runtime)
    private fun uuid(value: String): UUID {
        if (!DeckValidation.uuid(value)) throw GameProtocolFailure("invalidInput")
        return UUID.fromString(value)
    }
    private fun actor(principal: AccountService.Principal) = uuid(principal.account.accountId)
    private fun <T> transaction(work: (DSLContext) -> T): T = runtime.transaction { connection ->
        // Expected protocol refusals are values at the ownership boundary, not database failures.
        // A savepoint rolls back any tentative identity without releasing the writer epoch lock.
        val savepoint = connection.setSavepoint()
        try { Result.success(work(DSL.using(connection, SQLDialect.POSTGRES))) }
        catch (failure: GameProtocolFailure) { connection.rollback(savepoint); Result.failure<T>(failure) }
    }.getOrThrow()
    private fun authorize(game: GameRecord?, actor: UUID): GameRecord {
        if (game == null || actor !in listOf(game.inviterId, game.recipientId) || game.lifecycle !in listOf("LOBBY", "ACTIVE"))
            throw GameProtocolFailure("accessDenied")
        return game
    }
    private fun locked(sql: DSLContext, game: UUID, actor: UUID): GameRecord = authorize(
        sql.selectFrom(GAME).where(GAME.ID.eq(game)).forUpdate().fetchOne(), actor)

    fun open(principal: AccountService.Principal, hello: GameHello, connectionId: UUID, gameId: String,
        streamId: String, resume: List<String>? = null): GameStreamReady {
        val game = uuid(gameId); val stream = uuid(streamId); val account = actor(principal)
        val installation = uuid(hello.installationId); val process = uuid(hello.processId)
        if (resume != null) validateIds(resume)
        return transaction { sql ->
            locked(sql, game, account)
            val row = sql.selectFrom(GAME_STREAM).where(GAME_STREAM.GAME_ID.eq(game))
                .and(GAME_STREAM.ACCOUNT_ID.eq(account)).and(GAME_STREAM.INSTALLATION_ID.eq(installation)).forUpdate().fetchOne()
            if (resume != null && (row == null || row.processId != process || row.streamId != stream))
                throw GameProtocolFailure("streamFenced")
            val generation = Math.addExact(row?.generation ?: 0L, 1L)
            sql.insertInto(GAME_STREAM).set(GAME_STREAM.GAME_ID, game).set(GAME_STREAM.ACCOUNT_ID, account)
                .set(GAME_STREAM.INSTALLATION_ID, installation).set(GAME_STREAM.PROCESS_ID, process)
                .set(GAME_STREAM.STREAM_ID, stream).set(GAME_STREAM.CONNECTION_ID, connectionId).set(GAME_STREAM.GENERATION, generation)
                .onConflict(GAME_STREAM.GAME_ID, GAME_STREAM.ACCOUNT_ID, GAME_STREAM.INSTALLATION_ID).doUpdate()
                .set(GAME_STREAM.PROCESS_ID, process).set(GAME_STREAM.STREAM_ID, stream)
                .set(GAME_STREAM.CONNECTION_ID, connectionId).set(GAME_STREAM.GENERATION, generation).execute()
            // The game lock and dispatcher cutover fence the old connection before any negative status.
            GameStreamReady(gameId, streamId, generation.toString(), (resume ?: emptyList()).map {
                status(sql, game, account, it, "notAdmitted") })
        }
    }

    fun admit(principal: AccountService.Principal, hello: GameHello, connectionId: UUID, input: GameCommand): GameServerMessage {
        if (!GameWire.valid(input)) throw GameProtocolFailure("invalidInput")
        val game = uuid(input.scope.gameId); val account = actor(principal); val request = uuid(input.requestId)
        return transaction { sql ->
            val row = locked(sql, game, account)
            val stream = sql.selectFrom(GAME_STREAM).where(GAME_STREAM.GAME_ID.eq(game))
                .and(GAME_STREAM.ACCOUNT_ID.eq(account)).and(GAME_STREAM.INSTALLATION_ID.eq(uuid(hello.installationId)))
                .forUpdate().fetchOne()
            if (stream == null || stream.connectionId != connectionId || stream.processId.toString() != hello.processId ||
                stream.streamId.toString() != input.streamId || stream.generation != GameWire.ordinal(input.generation))
                throw GameProtocolFailure("streamFenced")
            val digest = GameWire.digest(input)
            val inserted = sql.insertInto(REQUEST_IDENTITY).set(REQUEST_IDENTITY.ID, request)
                .set(REQUEST_IDENTITY.ACCOUNT_ID, account).set(REQUEST_IDENTITY.KIND, "gameCommand")
                .set(REQUEST_IDENTITY.SCOPE, game.toString()).set(REQUEST_IDENTITY.INTENT_DIGEST, digest)
                .onConflictDoNothing().execute() == 1
            if (!inserted) {
                val original = sql.selectFrom(REQUEST_IDENTITY).where(REQUEST_IDENTITY.ID.eq(request)).fetchSingle()
                if (original.accountId != account || original.kind != "gameCommand" || original.scope != game.toString() || original.intentDigest != digest)
                    throw GameProtocolFailure("requestIdentityMismatch")
                val inbox = sql.selectFrom(GAME_INBOX).where(GAME_INBOX.REQUEST_ID.eq(request)).fetchSingle()
                return@transaction inbox.result?.let { json.decodeFromString<GameResult>(it) }
                    ?: GameReceipt(input.requestId, input.scope, inbox.ingressOrdinal.toString())
            }
            if (sql.fetchCount(GAME_INBOX, GAME_INBOX.GAME_ID.eq(game).and(GAME_INBOX.RESULT.isNull)) >= 1024)
                throw GameProtocolFailure("busy", true) // rolls the new identity back too
            val ordinal = row.nextIngress!!
            sql.update(GAME).set(GAME.NEXT_INGRESS, Math.addExact(ordinal, 1)).where(GAME.ID.eq(game)).execute()
            sql.insertInto(GAME_INBOX).set(GAME_INBOX.REQUEST_ID, request).set(GAME_INBOX.GAME_ID, game)
                .set(GAME_INBOX.ACCOUNT_ID, account).set(GAME_INBOX.INGRESS_ORDINAL, ordinal)
                .set(GAME_INBOX.STREAM_ID, uuid(input.streamId)).set(GAME_INBOX.GENERATION, GameWire.ordinal(input.generation))
                .set(GAME_INBOX.COMMAND, json.encodeToString<GameClientMessage>(input)).execute()
            GameReceipt(input.requestId, input.scope, ordinal.toString())
        }
    }

    fun validateIds(ids: List<String>) {
        if (ids.size > 256 || ids.distinct().size != ids.size || !ids.all(DeckValidation::uuid)) throw GameProtocolFailure("invalidInput")
    }
    private fun status(sql: DSLContext, game: UUID, account: UUID, id: String, missing: String): GameRequestStatus {
        val identity = sql.selectFrom(REQUEST_IDENTITY).where(REQUEST_IDENTITY.ID.eq(uuid(id))).fetchOne()
        if (identity == null) return GameRequestStatus(id, missing)
        if (identity.accountId != account || identity.kind != "gameCommand" || identity.scope != game.toString())
            throw GameProtocolFailure("requestIdentityMismatch")
        val inbox = sql.selectFrom(GAME_INBOX).where(GAME_INBOX.REQUEST_ID.eq(uuid(id))).fetchSingle()
        return inbox.result?.let { GameRequestStatus(id, "final", json.decodeFromString(it)) } ?: GameRequestStatus(id, "pending")
    }
    fun results(principal: AccountService.Principal, gameId: String, ids: List<String>): GameResults {
        validateIds(ids)
        return transaction { sql ->
            locked(sql, uuid(gameId), actor(principal))
            // HTTP is not a stream cutover: absence here cannot prove non-admission by a live socket.
            GameResults(ids.map { status(sql, uuid(gameId), actor(principal), it, "unknown") })
        }
    }
    fun head(principal: AccountService.Principal, gameId: String): GameHead = transaction { sql ->
        locked(sql, uuid(gameId), actor(principal))
        val summary = lobby.summary(sql, uuid(gameId))
        GameHead(gameId, summary.revision, summary)
    }
    fun revisions(principal: AccountService.Principal, gameId: String, after: String): GameRevisions {
        val base = GameWire.ordinal(after)
        return transaction { sql ->
            val game = locked(sql, uuid(gameId), actor(principal))
            if (base > game.revision!!) throw GameProtocolFailure("invalidInput")
            val rows = sql.select(GAME_REVISION.PAYLOAD).from(GAME_REVISION).where(GAME_REVISION.GAME_ID.eq(game.id))
                .and(GAME_REVISION.REVISION.gt(base)).orderBy(GAME_REVISION.REVISION).limit(101).fetch(GAME_REVISION.PAYLOAD)
            val revisions = rows.take(100).map { json.decodeFromString<GameServerMessage>(it!!) as GameRevision }
            GameRevisions(revisions, if (rows.size > 100) revisions.last().resultRevision else null)
        }
    }

    /** Candidate games are independent; every selected game executes its lowest pending ordinal. */
    fun executeOne(): Boolean = transaction { sql ->
        val game = sql.selectFrom(GAME).where(DSL.exists(sql.selectOne().from(GAME_INBOX)
            .where(GAME_INBOX.GAME_ID.eq(GAME.ID)).and(GAME_INBOX.RESULT.isNull)))
            .orderBy(GAME.ID).limit(1).forUpdate().skipLocked().fetchOne() ?: return@transaction false
        val inbox = sql.selectFrom(GAME_INBOX).where(GAME_INBOX.GAME_ID.eq(game.id)).and(GAME_INBOX.RESULT.isNull)
            .orderBy(GAME_INBOX.INGRESS_ORDINAL).limit(1).fetchSingle()
        val command = GameWire.decodeClient(inbox.command!!) as GameCommand
        val before = game.revision!!
        val rejection = applyInvitation(sql, game, inbox.accountId!!, command)
        val revision = if (rejection == null) Math.addExact(before, 1) else null
        var update: GameRevision? = null
        if (revision != null) {
            sql.update(GAME).set(GAME.REVISION, revision).where(GAME.ID.eq(game.id)).execute()
            update = GameRevision(game.id.toString(), before.toString(), revision.toString(), command.requestId,
                inbox.accountId.toString(), command.scope, lobby.summary(sql, game.id!!))
            sql.insertInto(GAME_REVISION).set(GAME_REVISION.GAME_ID, game.id).set(GAME_REVISION.REVISION, revision)
                .set(GAME_REVISION.PAYLOAD, GameWire.encode(update)).execute()
        }
        val created = if (revision != null && command.action == GameAction.Reinvite) listOf(update!!.summary.invitationId) else emptyList()
        val result = GameResult(command.requestId, command.scope, if (rejection == null) "accepted" else "rejected",
            rejection, inbox.ingressOrdinal.toString(), revision?.toString(), created)
        val encoded = json.encodeToString(result)
        sql.update(GAME_INBOX).set(GAME_INBOX.RESULT, encoded).where(GAME_INBOX.REQUEST_ID.eq(inbox.requestId)).execute()
        sql.insertInto(GAME_OUTBOX).set(GAME_OUTBOX.GAME_ID, game.id).set(GAME_OUTBOX.ACCOUNT_ID, inbox.accountId)
            .set(GAME_OUTBOX.REQUEST_ID, inbox.requestId).set(GAME_OUTBOX.RESULT, GameWire.encode(result))
            .set(GAME_OUTBOX.REVISION, update?.let(GameWire::encode)).execute()
        true
    }

    private fun applyInvitation(sql: DSLContext, game: GameRecord, account: UUID, command: GameCommand): String? {
        if (game.lifecycle != "LOBBY") return "lifecycleEnded"
        if (command.scope.preparationId != game.preparationRound.toString() || command.scope.invitationId != game.currentInvitation.toString())
            return "inapplicable"
        val invitation = sql.selectFrom(INVITATION).where(INVITATION.ID.eq(game.currentInvitation)).fetchSingle()
        when (command.action) {
            GameAction.AcceptInvitation, GameAction.DeclineInvitation -> {
                if (account != game.recipientId) return "accessDenied"
                if (invitation.state != "PENDING") return "inapplicable"
                sql.update(INVITATION).set(INVITATION.STATE,
                    if (command.action == GameAction.AcceptInvitation) "ACCEPTED" else "DECLINED")
                    .where(INVITATION.ID.eq(invitation.id)).execute()
            }
            GameAction.Reinvite -> {
                if (account != game.inviterId) return "accessDenied"
                if (invitation.state != "DECLINED") return "inapplicable"
                val next = UUID.randomUUID()
                sql.insertInto(INVITATION).set(INVITATION.ID, next).set(INVITATION.GAME_ID, game.id).execute()
                sql.update(GAME).set(GAME.CURRENT_INVITATION, next).where(GAME.ID.eq(game.id)).execute()
            }
        }
        return null
    }

    /** Recovery validates the newly implemented durable worker inputs, not forgotten client queues. */
    fun validatePending() = transaction { sql ->
        sql.select(GAME_INBOX.COMMAND).from(GAME_INBOX).where(GAME_INBOX.RESULT.isNull).fetchLazy().use { cursor ->
            for (row in cursor) check(GameWire.valid(GameWire.decodeClient(row.value1()!!) as GameCommand))
        }
        sql.selectCount().from(GAME_OUTBOX).where(GAME_OUTBOX.PUBLISHED.eq(false)).fetchSingle()
        Unit
    }
    data class Publication(val id: Long, val gameId: String, val accountId: String, val revision: String?, val result: String)
    fun publications(): List<Publication> = transaction { sql ->
        sql.selectFrom(GAME_OUTBOX).where(GAME_OUTBOX.PUBLISHED.eq(false)).orderBy(GAME_OUTBOX.ID).limit(100).fetch().map {
            Publication(it.id!!, it.gameId.toString(), it.accountId.toString(), it.revision, it.result!!)
        }
    }
    fun published(id: Long) = transaction { sql -> sql.update(GAME_OUTBOX).set(GAME_OUTBOX.PUBLISHED, true).where(GAME_OUTBOX.ID.eq(id)).execute(); Unit }
}
