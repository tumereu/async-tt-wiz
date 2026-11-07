package org.asyncttwiz.server.game

import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.StoredData
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.activity.OpponentPresences
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.generated.tables.Game.GAME
import org.asyncttwiz.server.generated.tables.GameInbox.GAME_INBOX
import org.asyncttwiz.server.generated.tables.GameOutbox.GAME_OUTBOX
import org.asyncttwiz.server.generated.tables.GameRevision.GAME_REVISION
import org.asyncttwiz.server.generated.tables.GameStream.GAME_STREAM
import org.asyncttwiz.server.generated.tables.Invitation.INVITATION
import org.asyncttwiz.server.generated.tables.RequestIdentity.REQUEST_IDENTITY
import org.asyncttwiz.server.generated.tables.records.GameRecord
import org.asyncttwiz.server.lobby.LobbyService
import org.asyncttwiz.server.pods.PodService
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

class GameProtocolFailure(val code: String, val retryable: Boolean = false) : RuntimeException(code)

/** One durable request/execution domain per persistent game: preparation, and the play on engine state. */
class GameService @JvmOverloads constructor(private val runtime: ServiceRuntime,
    private val clock: java.time.Clock = java.time.Clock.systemUTC()) {
    private val json = GameWire.json
    private val lobby = LobbyService(runtime)
    /** I-D54: every play's state is the engine's (I-D56 step D2). */
    internal val engineGames = EngineGames()
    private fun uuid(value: String): UUID {
        if (!DeckValidation.uuid(value)) throw GameProtocolFailure("invalidInput")
        return UUID.fromString(value)
    }
    private fun actor(principal: AccountService.Principal) = uuid(principal.account.accountId)
    private fun <T> transaction(work: (DSLContext) -> T): T = runtime.transaction { connection ->
        // Expected protocol refusals are values at the ownership boundary, not database failures.
        // Rolling back drops any tentative identity; the commit that follows then has nothing to do.
        try { Result.success(work(DSL.using(connection, SQLDialect.POSTGRES))) }
        catch (failure: GameProtocolFailure) { connection.rollback(); Result.failure<T>(failure) }
    }.getOrThrow()
    private fun authorize(game: GameRecord?, actor: UUID, recovery: Boolean = false): GameRecord {
        if (game == null || actor !in listOf(game.inviterId, game.recipientId) ||
            (!recovery && game.lifecycle !in listOf("LOBBY", "ACTIVE")))
            throw GameProtocolFailure("accessDenied")
        return game
    }
    private fun locked(sql: DSLContext, game: UUID, actor: UUID, recovery: Boolean = false): GameRecord = authorize(
        sql.selectFrom(GAME).where(GAME.ID.eq(game)).forUpdate().fetchOne(), actor, recovery)

    fun open(principal: AccountService.Principal, hello: GameHello, connectionId: UUID, gameId: String,
        streamId: String, resume: List<String>? = null): GameStreamReady {
        val game = uuid(gameId); val stream = uuid(streamId); val account = actor(principal)
        val installation = uuid(hello.installationId); val process = uuid(hello.processId)
        if (resume != null) validateIds(resume)
        return transaction { sql ->
            val current = locked(sql, game, account, recovery = resume != null)
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
                status(sql, game, account, it, "notAdmitted") },
                ended = if (current.lifecycle !in listOf("LOBBY", "ACTIVE")) lobby.summary(sql, game) else null)
        }
    }

    fun admit(principal: AccountService.Principal, hello: GameHello, connectionId: UUID, input: GameCommand): GameServerMessage {
        if (!GameWire.valid(input)) throw GameProtocolFailure("invalidInput")
        val game = uuid(input.scope.gameId); val account = actor(principal); val request = uuid(input.requestId)
        return transaction { sql ->
            val row = locked(sql, game, account, recovery = true)
            val stream = sql.selectFrom(GAME_STREAM).where(GAME_STREAM.GAME_ID.eq(game))
                .and(GAME_STREAM.ACCOUNT_ID.eq(account)).and(GAME_STREAM.INSTALLATION_ID.eq(uuid(hello.installationId)))
                .forUpdate().fetchOne()
            if (stream == null || stream.connectionId != connectionId || stream.processId.toString() != hello.processId ||
                stream.streamId.toString() != input.streamId || stream.generation != GameWire.ordinal(input.generation))
                throw GameProtocolFailure("streamFenced")
            val digest = GameWire.digest(input)
            // Only an already owned original may be replayed against a tombstone. Never insert
            // a new identity or disclose ordinary preparation/history after cancellation.
            if (row.lifecycle !in listOf("LOBBY", "ACTIVE") && !sql.fetchExists(sql.selectOne().from(REQUEST_IDENTITY)
                    .where(REQUEST_IDENTITY.ID.eq(request)))) throw GameProtocolFailure("accessDenied")
            val inserted = sql.insertInto(REQUEST_IDENTITY).set(REQUEST_IDENTITY.ID, request)
                .set(REQUEST_IDENTITY.ACCOUNT_ID, account).set(REQUEST_IDENTITY.KIND, "gameCommand")
                .set(REQUEST_IDENTITY.SCOPE, game.toString()).set(REQUEST_IDENTITY.INTENT_DIGEST, digest)
                .onConflictDoNothing().execute() == 1
            if (!inserted) {
                val original = sql.selectFrom(REQUEST_IDENTITY).where(REQUEST_IDENTITY.ID.eq(request)).fetchSingle()
                if (original.accountId != account || original.kind != "gameCommand" || original.scope != game.toString() || original.intentDigest != digest)
                    throw GameProtocolFailure("requestIdentityMismatch")
                val inbox = sql.selectFrom(GAME_INBOX).where(GAME_INBOX.REQUEST_ID.eq(request)).fetchSingle()
                return@transaction inbox.result?.let { GameWire.stored(GameResult.serializer(), it) }
                    ?: GameReceipt(input.requestId, input.scope, inbox.ingressOrdinal.toString())
            }
            // Original outcomes remain private and recoverable after removal. New intent does not.
            if (row.lifecycle !in listOf("LOBBY", "ACTIVE")) throw GameProtocolFailure("accessDenied")
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
        return inbox.result?.let { GameRequestStatus(id, "final", GameWire.stored(GameResult.serializer(), it)) } ?: GameRequestStatus(id, "pending")
    }
    fun results(principal: AccountService.Principal, gameId: String, ids: List<String>): GameResults {
        validateIds(ids)
        return transaction { sql ->
            locked(sql, uuid(gameId), actor(principal), recovery = true)
            // HTTP is not a stream cutover: absence here cannot prove non-admission by a live socket.
            GameResults(ids.map { status(sql, uuid(gameId), actor(principal), it, "unknown") })
        }
    }
    fun head(principal: AccountService.Principal, gameId: String): GameHead = transaction { sql ->
        readHead(sql, principal, gameId, locked(sql, uuid(gameId), actor(principal)))
    }
    /** HIS-111, HIS-099: a page of the active play's main line ending at its event [through], for a player of the game
     * ([EngineGames.historyPage]); refused ("inapplicable") when the game has no active engine play or [through] isn't
     * on its main line. */
    fun history(principal: AccountService.Principal, gameId: String, through: String): EngineHistoryPage = transaction { sql ->
        val game = locked(sql, uuid(gameId), actor(principal))
        val play = game.currentPlay?.takeIf { game.lifecycle == "ACTIVE" } ?: throw GameProtocolFailure("inapplicable")
        try { engineGames.historyPage(sql, play, through) } catch (_: org.asyncttwiz.protocol.game.Refusal) { null }
            ?: throw GameProtocolFailure("inapplicable")
    }
    /** A socket may learn removal and recover originals, never reopen the removed replica. [engine] (switch-review #11):
     * the open game's head, with its play and engine state; otherwise an active play's head is its summary only. */
    fun subscriptionHead(principal: AccountService.Principal, gameId: String, presence: Boolean = false,
        engine: Boolean = true): GameHead = transaction { sql ->
        val game = locked(sql, uuid(gameId), actor(principal), recovery = true)
        if (game.lifecycle !in listOf("LOBBY", "ACTIVE")) {
            val summary = lobby.summary(sql, game.id!!)
            GameHead(gameId, summary.revision, summary)
        } else (if (engine || game.lifecycle != "ACTIVE") readHead(sql, principal, gameId, game) else summaryHead(sql, principal, gameId, game)).let { head ->
            if (!presence) head else head.copy(opponentPresence = OpponentPresences.read(sql, game.id!!,
                if (game.inviterId == actor(principal)) game.recipientId!! else game.inviterId!!,
                java.time.OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC)))
        }
    }
    /** An active play's head without its play or engine state (switch-review #11: a game in the list): its summary, whose
     * move it is per viewer from the engine seats, and the chat cursor. No play row or engine state is read. */
    private fun summaryHead(sql: DSLContext, principal: AccountService.Principal, gameId: String, game: GameRecord): GameHead {
        val summary = lobby.summary(sql, game, null, actor(principal))
        return GameHead(gameId, summary.revision, summary, chatSync = ChatStore.sync(sql, uuid(gameId)),
            ownPodDeckName = PreparationStore.ownPodDeckName(sql, uuid(gameId), actor(principal)))
    }

    /** [game] is the row locked by this transaction: the play is read (and checked) once, for the summary too. */
    private fun readHead(sql: DSLContext, principal: AccountService.Principal, gameId: String, game: GameRecord): GameHead {
        val reads = headReads(sql, game.id!!, actor(principal))
        val play = reads.play
        // I-D54: a play whose state is the engine's says whose move it is from the engine, per viewer (I-D56 §7)
        val engine = game.currentPlay?.takeIf { game.lifecycle == "ACTIVE" && engineGames.mayExist(sql) }
            ?.let { engineGames.snapshot(sql, it, actor(principal)) }
        val summary = lobby.summary(sql, game, if (game.lifecycle != "ACTIVE") null
            else if (engine != null) engine.second else play?.board?.effectiveAttentionHolder, engine?.let { actor(principal) })
        return GameHead(gameId, summary.revision, summary, preparation =
            if (play == null && canReadPreparation(summary, principal.account.accountId)) PreparationStore.read(sql, summary) else null,
            play = play, chatSync = reads.chatSync, ownPodDeckName = reads.ownPodDeckName, engine = engine?.first)
    }

    /** What a head reads of [game]'s other tables besides the engine's (R6). */
    internal class HeadReads(val play: PlayState?, val chatSync: ChatSync, val ownPodDeckName: String?)

    /** R6: [PlayStore.read], [ChatStore.sync] and [PreparationStore.ownPodDeckName] for [viewer], in one statement. Run
     * after the game's lock: a statement waiting for the lock would read the other tables as they were before it. */
    internal fun headReads(sql: DSLContext, game: UUID, viewer: UUID): HeadReads {
        val payload = PlayStore.payloadField(game)
        val (chatId, chatRevision) = ChatStore.syncFields(game)
        val deckName = PreparationStore.ownPodDeckNameField(game, viewer)
        val row = sql.select(payload, chatId, chatRevision, deckName).fetchSingle()
        return HeadReads(PlayStore.decode(game, row[payload]), ChatStore.sync(row[chatId], row[chatRevision]), row[deckName])
    }
    fun revisions(principal: AccountService.Principal, gameId: String, after: String): GameRevisions {
        val base = GameWire.ordinal(after)
        return transaction { sql ->
            val game = locked(sql, uuid(gameId), actor(principal))
            if (base > game.revision!!) throw GameProtocolFailure("invalidInput")
            val rows = sql.select(GAME_REVISION.PAYLOAD).from(GAME_REVISION).where(GAME_REVISION.GAME_ID.eq(game.id))
                .and(GAME_REVISION.REVISION.gt(base)).orderBy(GAME_REVISION.REVISION).limit(101).fetch(GAME_REVISION.PAYLOAD)
            val revisions = rows.take(100).map {
                visibleRevision(GameWire.stored(GameServerMessage.serializer(), it!!) as GameRevision, principal.account.accountId) }
            GameRevisions(revisions, if (rows.size > 100) revisions.last().resultRevision else null)
        }
    }

    fun chat(principal: AccountService.Principal, gameId: String, before: String? = null, after: String? = null): ChatPage = transaction { sql ->
        val game = locked(sql, uuid(gameId), actor(principal))
        if (before != null && after != null) throw GameProtocolFailure("invalidInput")
        ChatStore.page(sql, game.id!!, before?.let(GameWire::ordinal), after?.let(GameWire::ordinal))
            .copy(readState = ChatStore.readState(sql, game.id!!, actor(principal)))
    }

    fun chatRead(principal: AccountService.Principal, gameId: String, input: ReadChat? = null): ChatReadState = transaction { sql ->
        val account = actor(principal)
        val game = locked(sql, uuid(gameId), account)
        if (input == null) ChatStore.readState(sql, game.id!!, account)
        else ChatStore.read(sql, game.id!!, account, uuid(input.messageId))
    }

    private fun canReadPreparation(summary: GameSummary, accountId: String) = accountId == summary.inviter.accountId ||
        (accountId == summary.recipient.accountId && summary.invitationState == InvitationState.ACCEPTED)

    internal fun visibleRevision(stored: GameRevision, accountId: String): GameRevision {
        // TRN-058 (I-D57): whose move an engine play is: the viewer when the game waits for them, else the other player
        // when it waits for them; the per-player table stays here
        val moves = stored.engine?.moves
        // ... and so does the last-activity time: nothing about the line past the viewer's next stop (§7)
        // (a revision that changed nothing of the engine's carries only these: it is sent without an engine part)
        val revision = if (moves == null) stored else stored.copy(engine = stored.engine?.copy(moves = null, activity = null)?.takeIf { it != EngineUpdate() },
            summary = stored.summary.copy(attentionHolder = if (moves[accountId] == true) accountId else moves.entries.firstOrNull { it.value }?.key,
                updatedAt = stored.engine?.activity?.get(accountId) ?: stored.summary.updatedAt))
        return if (revision.summary.lifecycle !in listOf(GameLifecycle.LOBBY, GameLifecycle.ACTIVE)) revision.copy(preparation = null, play = null, chatMessage = null)
        else if (canReadPreparation(revision.summary, accountId)) revision else revision.copy(preparation = null)
    }

    /** An old unpublished board must not restore access after a later lifecycle commit. [engine] false (switch-review
     * #11: a game in the list, not the open one): an active play's revision without its engine part or play, so the
     * list gets its summary, chat and preparation only. */
    fun publicationState(principal: AccountService.Principal, revision: GameRevision, engine: Boolean = true): GameServerMessage = transaction { sql ->
        val game = locked(sql, uuid(revision.gameId), actor(principal), recovery = true)
        if (game.lifecycle !in listOf("LOBBY", "ACTIVE")) {
            val summary = lobby.summary(sql, game.id!!)
            GameHead(revision.gameId, summary.revision, summary)
        } else visibleRevision(revision, principal.account.accountId).let { visible ->
            if (engine || visible.summary.lifecycle != GameLifecycle.ACTIVE) visible
            else visible.copy(play = null, engineEvent = null, engine = null)
        }
    }

    /** Candidate games are independent; every selected game executes its lowest pending ordinal. */
    fun executeOne(): Boolean = executeOne(null)

    internal fun executeOne(listener: org.jooq.ExecuteListener?): Boolean {
        var committedEngine: EngineGames.Outcome? = null
        val executed = transaction { context ->
        val sql = if (listener == null) context else DSL.using(context.configuration().derive(listener))
        val game = sql.selectFrom(GAME).where(DSL.exists(sql.selectOne().from(GAME_INBOX)
            .where(GAME_INBOX.GAME_ID.eq(GAME.ID)).and(GAME_INBOX.RESULT.isNull)))
            .orderBy(GAME.ID).limit(1).forUpdate().skipLocked().fetchOne() ?: return@transaction false
        val inbox = sql.selectFrom(GAME_INBOX).where(GAME_INBOX.GAME_ID.eq(game.id)).and(GAME_INBOX.RESULT.isNull)
            .orderBy(GAME_INBOX.INGRESS_ORDINAL).limit(1).fetchSingle()
        // A pending request the current code can't read is refused like any other failing request (SYS-052).
        val command = StoredData.orSkip("game request", inbox.requestId) { GameWire.decodeClient(inbox.command!!) as GameCommand }
            ?: return@transaction reject(context, game.id!!, inbox, GameResult(inbox.requestId.toString(), GameScope(game.id.toString()),
                "rejected", "internalError", inbox.ingressOrdinal.toString()))
        val requestSavepoint = context.connectionResult { it.setSavepoint() }
        val processingStarted = System.nanoTime()
        try {
        val before = game.revision!!
        var insertedChat: ChatMessage? = null
        var engineAccepted: EngineGames.Outcome? = null
        val rejection = if (game.lifecycle !in listOf("LOBBY", "ACTIVE")) "lifecycleEnded" else when (command.action) {
            GameAction.DeleteGame -> applyDeletion(sql, game, command)
            is GameAction.Submit, is GameAction.EngineSeen, is GameAction.EngineRewind, is GameAction.EngineRequestRewind,
            is GameAction.EngineAnswerRewind, GameAction.EngineCancelRewind, is GameAction.EngineSettings ->
                applyEngine(sql, game, inbox.accountId!!, command).let { (reason, accepted) -> engineAccepted = accepted; reason }
            is GameAction.SendChat -> ChatStore.apply(sql, game, inbox.accountId!!, command).also { insertedChat = it.message }.reason
            GameAction.CancelSetup -> applyCancellation(sql, game, inbox.accountId!!, command)
            is GameAction.SelectDeck -> applySelection(sql, game, inbox.accountId!!, command)
            is GameAction.TransferPreparedCard -> applyTransfer(sql, game, inbox.accountId!!, command)
            is GameAction.SetPodGameBasics -> applyPodBasics(sql, game, inbox.accountId!!, command)
            is GameAction.SetStarter, is GameAction.SetAlternateStarter, is GameAction.ShowDeck, GameAction.Ready, GameAction.WithdrawReady ->
                applyReadiness(sql, game, inbox.accountId!!, command)
            else -> applyInvitation(sql, game, inbox.accountId!!, command)
        }
        val revision = if (rejection == null) Math.addExact(before, 1) else null
        var update: GameRevision? = null
        var encodedUpdate: String? = null
        if (revision != null) {
            // Reused summaries must have the same precision as PostgreSQL's timestamp(6).
            val updatedAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
            // Game history §4.4: a change by a player is activity (the cleanup's clock); a match whose result became final
            // ended now, and that is the last activity until the next one
            val activityAt = java.time.OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC)
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
            sql.update(GAME).set(GAME.REVISION, revision)
                .set(GAME.UPDATED_AT, updatedAt)
                .let { if (activity(command.action)) it.set(GAME.ACTIVITY_AT, activityAt) else it }
                .let { if (engineAccepted?.ended == true) it.set(GAME.LAST_MATCH_END_AT, activityAt) else it }
                .where(GAME.ID.eq(game.id)).execute()
            val engine = engineAccepted
            var seated: EngineUpdate? = null
            val summary = if (engine != null && !engine.ended) {
                game.revision = revision; game.updatedAt = updatedAt
                lobby.summary(sql, game, engine.attention)
            } else lobby.summary(sql, game.id!!).let { summary ->
                // switchreview #2: every revision of an active engine play (chat, the start) says whose move it is per
                // viewer from the engine seats, as an engine revision does ([visibleRevision]); never from the play's
                // starting board
                seated = summary.playId?.takeIf { engine == null && summary.lifecycle == GameLifecycle.ACTIVE }
                    ?.let { engineGames.moves(sql, UUID.fromString(it)) }
                seated?.let { summary.copy(attentionHolder = it.moves.orEmpty().entries.firstOrNull { (_, move) -> move }?.key) } ?: summary
            }
            val play = if (summary.lifecycle == GameLifecycle.ACTIVE) PlayStore.read(sql, game.id!!) else null
            update = GameRevision(game.id.toString(), before.toString(), revision.toString(), command.requestId,
                inbox.accountId.toString(), command.scope, summary,
                if (summary.lifecycle == GameLifecycle.LOBBY) PreparationStore.read(sql, summary) else null,
                if (command.action !is GameAction.SendChat && engine == null) play else null,
                insertedChat, engineEvent = engine?.events?.firstOrNull(), engine = engine?.update ?: seated)
            encodedUpdate = GameWire.encode(update)
            sql.insertInto(GAME_REVISION).set(GAME_REVISION.GAME_ID, game.id).set(GAME_REVISION.REVISION, revision)
                .set(GAME_REVISION.PAYLOAD, encodedUpdate).execute()
            val opponent = if (inbox.accountId == game.inviterId) game.recipientId!! else game.inviterId!!
            // Whether a notice tells the other player it is their move: on engine state only when the game waits for them
            // (I-D57: never while this player still plays on assumptions about them)
            val opponentMoves = (if (seated != null) summary.playId?.let { engineGames.waits(sql, UUID.fromString(it)) } else null)
                ?.get(opponent.toString()) ?: (summary.attentionHolder == opponent.toString())
            insertedChat?.let { message ->
                org.asyncttwiz.server.activity.ActivityService.record(sql, opponent, ActivityKind.GAME,
                    game.id!!, uuid(message.id), inbox.accountId, yourMove = opponentMoves, newMessage = true)
            }
            // The other player's earlier Ready is otherwise never answered when this Ready starts the game.
            if (play != null && play.startRequestId == command.requestId) {
                org.asyncttwiz.server.activity.ActivityService.resetReminders(sql, game.id!!)
                org.asyncttwiz.server.activity.ActivityService.record(sql, opponent, ActivityKind.GAME,
                    game.id!!, uuid(play.id), inbox.accountId, yourMove = opponentMoves)
            }
            // switchreview #3 (LOB-075, LOB-076), I-D57: a player the game waited for until now is no longer listed or
            // reminded: when their own move ended it (a confirmation, playback past their stop, an undo request, an action)
            // their stretch is answered (a wait of the other player's recorded below arms their reminder again, from its own
            // time); when the other player went on instead (assuming after all, cancelling a request) their notice is
            // withdrawn: nothing waits for them
            if (engine != null && !engine.ended) for (player in engine.endedWaits) {
                val other = if (player == game.inviterId.toString()) game.recipientId!! else game.inviterId!!
                if (player == inbox.accountId.toString()) org.asyncttwiz.server.activity.ActivityService.moveEnded(sql,
                    UUID.fromString(player), game.id!!, other, engine.waits[other.toString()] == true, updatedAt)
                else org.asyncttwiz.server.activity.ActivityService.waitWithdrawn(sql, UUID.fromString(player), game.id!!)
            }
            // I-D56 §7, I-D57: a player hears of an engine play only when the game newly waits for them ("your move", timed
            // from their next stop's event), never while the other player plays on assumptions, never of the line beyond
            // their next stop; LOB-111: the game's end once its result is final
            if (engine != null) {
                val waited = engine.waits[opponent.toString()] == true
                if (engine.ended) org.asyncttwiz.server.activity.ActivityService.record(sql, opponent, ActivityKind.GAME,
                    game.id!!, uuid(command.requestId), inbox.accountId)
                // I-D56 §8 (LOB-116): the assumer hears that the other player answered differently, once per branch
                else if (engine.branched) org.asyncttwiz.server.activity.ActivityService.record(sql, opponent, ActivityKind.GAME,
                    game.id!!, uuid(command.requestId), inbox.accountId, yourMove = waited, move = waited, branched = true)
                else if (opponent.toString() in engine.newWaits) org.asyncttwiz.server.activity.ActivityService.record(sql, opponent,
                    ActivityKind.GAME, game.id!!, uuid(command.requestId), inbox.accountId,
                    time = engine.stopTimes[opponent.toString()] ?: java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC), yourMove = true, move = true,
                    opponentReady = opponent.toString() in engine.ready)
            }
        }
        val created = when {
            revision == null -> emptyList()
            engineAccepted?.ended == true -> listOf(update!!.summary.preparationRound)
            command.action is GameAction.SendChat -> listOf(command.requestId)
            update!!.play?.startRequestId == command.requestId -> listOf(update.play!!.id, update.play!!.root.id)
            command.action == GameAction.Reinvite -> listOf(update.summary.invitationId)
            command.action is GameAction.SelectDeck -> update.preparation!!.players.single {
                it.accountId == inbox.accountId.toString() }.deck!!.let { listOf(it.id) }
            else -> emptyList()
        }
        if (revision != null) committedEngine = engineAccepted
        val result = GameResult(command.requestId, command.scope, if (rejection == null) "accepted" else "rejected",
            rejection, inbox.ingressOrdinal.toString(), revision?.toString(), created)
        val encoded = json.encodeToString(result)
        sql.update(GAME_INBOX).set(GAME_INBOX.RESULT, encoded).where(GAME_INBOX.REQUEST_ID.eq(inbox.requestId)).execute()
        sql.insertInto(GAME_OUTBOX).set(GAME_OUTBOX.GAME_ID, game.id).set(GAME_OUTBOX.ACCOUNT_ID, inbox.accountId)
            .set(GAME_OUTBOX.REQUEST_ID, inbox.requestId).set(GAME_OUTBOX.RESULT, GameWire.encode(result))
            .set(GAME_OUTBOX.REVISION, encodedUpdate).execute()
        true
        } catch (failure: org.asyncttwiz.server.database.OwnershipFailure) {
            throw failure
        } catch (failure: org.jooq.exception.DataAccessException) {
            // Storage failures remain retryable at the fenced transaction/recovery boundary.
            throw failure
        } catch (failure: Throwable) {
            // A poison request must not terminate a scheduled worker or retain tentative writes.
            context.connection { it.rollback(requestSavepoint) }
            committedEngine = null
            reject(context, game.id!!, inbox, GameResult(command.requestId, command.scope, "rejected",
                (failure as? GameProtocolFailure)?.code ?: "internalError", inbox.ingressOrdinal.toString()))
            org.slf4j.LoggerFactory.getLogger(GameService::class.java).error(
                "Gameplay request rejected game={} request={} failureType={}", game.id, command.requestId, failure.javaClass.simpleName)
            true
        } finally {
            org.slf4j.LoggerFactory.getLogger(GameService::class.java).info(
                "Gameplay command game={} action={} processingMs={}", command.scope.gameId,
                command.action.latencyType(), (System.nanoTime() - processingStarted) / 1_000_000.0)
        }
    }
        committedEngine?.let(engineGames::committed)
        return executed
    }

    /** The request's final outcome without any game change: original result plus the sender's outbox notice. */
    private fun reject(sql: DSLContext, game: UUID, inbox: org.asyncttwiz.server.generated.tables.records.GameInboxRecord,
        result: GameResult): Boolean {
        sql.update(GAME_INBOX).set(GAME_INBOX.RESULT, json.encodeToString(result))
            .where(GAME_INBOX.REQUEST_ID.eq(inbox.requestId)).execute()
        sql.insertInto(GAME_OUTBOX).set(GAME_OUTBOX.GAME_ID, game).set(GAME_OUTBOX.ACCOUNT_ID, inbox.accountId)
            .set(GAME_OUTBOX.REQUEST_ID, inbox.requestId).set(GAME_OUTBOX.RESULT, GameWire.encode(result)).execute()
        return true
    }

    /**
     * I-D54 phase 3, I-D56 step A: a play whose state is the engine's ([GameAction.Submit], local-head reports, rewinds and
     * undo requests, private settings): the reason it is refused, or what it did. A result that became final ends the
     * play as an accepted result does (I-D56 §11: concession at once, else confirmed once both players saw it).
     */
    private fun applyEngine(sql: DSLContext, game: GameRecord, account: UUID, command: GameCommand): Pair<String?, EngineGames.Outcome?> {
        if (game.lifecycle != "ACTIVE") return "lifecycleEnded" to null
        val play = game.currentPlay ?: return "lifecycleEnded" to null
        val now = java.time.OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC)
        val live = { player: UUID -> OpponentPresences.read(sql, game.id!!, player, now).state == PresenceState.ONLINE }
        val outcome = try {
            when (val action = command.action) {
                is GameAction.Submit -> engineGames.submit(sql, play, account, action.submission, live)
                is GameAction.EngineSeen -> engineGames.seen(sql, play, account, action.eventId, live)
                is GameAction.EngineRewind -> engineGames.rewind(sql, play, account, action.target, live)
                is GameAction.EngineRequestRewind -> engineGames.requestRewind(sql, play, account, action.target, action.message, live)
                is GameAction.EngineAnswerRewind -> engineGames.answerRewind(sql, play, account, action.accept, live)
                GameAction.EngineCancelRewind -> engineGames.cancelRewind(sql, play, account, live)
                is GameAction.EngineSettings -> engineGames.settings(sql, play, account, action.settings, live)
                else -> error("Not an engine action")
            } ?: return "notEngineState" to null
        } catch (refusal: org.asyncttwiz.protocol.game.Refusal) {
            return (if (refusal.reason in ENGINE_REASONS) refusal.reason else "refusedByEngine") to null
        }
        if (outcome.ended) {
            val summary = lobby.summary(sql, game.id!!)
            // Game history §1.1: the finished match's record, from the decks it was played with (before the lobby's return)
            org.asyncttwiz.server.history.MatchRecords.finished(sql, summary, play, outcome.facts!!, now)
            PlayStore.endEngine(sql, summary, command.requestId, outcome.result) { lobby.summary(sql, it) }
        }
        return null to outcome
    }

    /** Whether [action] is a change by a player that counts as the game's activity (game history §4.4): anything but a
     * device reporting what its player has seen, or a player's private play settings. */
    private fun activity(action: GameAction): Boolean = action !is GameAction.EngineSeen && action !is GameAction.EngineSettings

    private fun applyDeletion(sql: DSLContext, game: GameRecord, command: GameCommand): String? {
        if (game.lifecycle != "ACTIVE") return "lifecycleEnded"
        if (command.scope.playId != game.currentPlay?.toString()) return "inapplicable"
        // Game history §1.3: the match in play leaves an Unfinished record
        org.asyncttwiz.server.history.MatchRecords.unfinished(sql, lobby.summary(sql, game.id!!),
            java.time.OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC))
        sql.update(GAME).set(GAME.LIFECYCLE, "DELETED").where(GAME.ID.eq(game.id)).execute()
        return null
    }

    private fun applyCancellation(sql: DSLContext, game: GameRecord, account: UUID, command: GameCommand): String? {
        val rejection = CancellationRules.rejection(lobby.summary(sql, game.id!!), account.toString(), command.scope)
        if (rejection != null) return rejection
        // Retain identity, attempts and original outcomes; lifecycle excludes every current listing/read.
        sql.update(GAME).set(GAME.LIFECYCLE, "CANCELLED").where(GAME.ID.eq(game.id)).execute()
        return null
    }

    private fun applySelection(sql: DSLContext, game: GameRecord, account: UUID, command: GameCommand): String? {
        val action = command.action as GameAction.SelectDeck
        val summary = lobby.summary(sql, game.id!!)
        // Refuse scope/access before looking at any account-private saved source.
        if (game.lifecycle != "LOBBY") return "lifecycleEnded"
        if (command.scope.preparationId != game.preparationRound.toString()) return "inapplicable"
        if (!canReadPreparation(summary, account.toString())) return "accessDenied"
        if (summary.podId != null) return "podDeckFixed"
        val source = DeckService(runtime).selectedSource(sql, account, action.deckId)
        // I-D51/I-D52: a deck Automatum can't play fully (as the owner's device worked it out) can't start a game.
        if (source?.deck?.automatum?.supported == false) return "automatumUnsupported"
        val definitions = PreparationStore.definitions(sql, source?.entries?.map { it.definition }?.toSet() ?: emptySet())
        val current = PreparationStore.read(sql, summary)
        val change = PreparationRules.select(summary, current, account.toString(),
            command.requestId, command.scope, action, source, definitions)
        change.state?.let { state ->
            PreparationStore.saveSelection(sql, account, state)
            // The account default decides the deck's visibility whenever a different deck is chosen; re-selecting
            // the same deck keeps this game's own Show deck choice.
            val previous = current.players.single { it.accountId == account.toString() }.deck?.sourceId
            if (previous != action.deckId) DeckVisibilityStore.apply(sql, summary, account, action.deckId)
        }
        return change.reason
    }

    private fun applyPodBasics(sql: DSLContext, game: GameRecord, account: UUID, command: GameCommand): String? {
        val summary = lobby.summary(sql, game.id!!)
        val change = PodGamePreparation.setBasics(summary, PreparationStore.read(sql, summary), account.toString(),
            command.scope, command.action as GameAction.SetPodGameBasics)
        change.state?.let { PreparationStore.saveSelection(sql, account, it) }
        return change.reason
    }

    private fun applyTransfer(sql: DSLContext, game: GameRecord, account: UUID, command: GameCommand): String? {
        val summary = lobby.summary(sql, game.id!!)
        val change = PreparationRules.transfer(summary, PreparationStore.read(sql, summary), account.toString(),
            command.scope, command.action as GameAction.TransferPreparedCard)
        change.state?.let { PreparationStore.saveSelection(sql, account, it) }
        return change.reason
    }

    private fun applyInvitation(sql: DSLContext, game: GameRecord, account: UUID, command: GameCommand): String? {
        if (game.lifecycle != "LOBBY") return "lifecycleEnded"
        if (command.scope.preparationId != game.preparationRound.toString() || command.scope.invitationId != game.currentInvitation.toString())
            return "inapplicable"
        val invitation = sql.selectFrom(INVITATION).where(INVITATION.ID.eq(game.currentInvitation)).fetchSingle()
        when (command.action) {
            GameAction.AcceptInvitation, GameAction.DeclineInvitation -> {
                if (account != game.recipientId) return "accessDenied"
                // A held pod challenge is visible and declinable, but accepting waits for the recipient's deck.
                if (command.action == GameAction.AcceptInvitation && game.podInvitationHeld == true) return "inapplicable"
                if (invitation.state != "PENDING") return "inapplicable"
                if (command.action == GameAction.AcceptInvitation)
                    PodService(runtime).acceptChallenge(sql, lobby.summary(sql, game.id!!), command.requestId)
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
                // A still-held challenge notifies when the recipient's deck releases it.
                if (game.podInvitationHeld != true) org.asyncttwiz.server.activity.ActivityService.record(sql, game.recipientId!!,
                    ActivityKind.INVITATION, game.id!!, next, account)
            }
            else -> error("Preparation uses the shared preparation processor")
        }
        return null
    }

    private fun applyReadiness(sql: DSLContext, game: GameRecord, account: UUID, command: GameCommand): String? {
        val summary = lobby.summary(sql, game.id!!)
        val changed = StartRules.change(summary, PreparationStore.read(sql, summary), account.toString(), command.scope, command.action)
        // CR 309.2: a Ready brings the player's dungeon cards outside the game; a deck that ventures or takes the initiative needs them
        val outside = command.inlineDefinitions
        if (changed.state != null && command.action == GameAction.Ready) {
            if (!org.asyncttwiz.protocol.game.OutsideCards.valid(outside)) return "invalidOutsideCards"
            if (!EngineDecks.outsideProvided(changed.state!!, account.toString(), outside)) return "automatumUnsupported"
        }
        val change = if (command.action == GameAction.Ready) changed.copy(state = changed.state?.let {
            StartRules.withOutside(it, account.toString(), outside) }) else changed
        if (change.state?.let { preparation -> StartRules.canStart(summary, preparation) &&
            preparation.players.any { !DeckValidation.quantities(it.deck!!.entries.map { entry -> entry.quantity }) } } == true)
            return "invalidDeck"
        // I-D51/I-D52 (pivot review #2): a player is Ready, and a game starts, only with decks Automatum can play fully, as
        // stored for the deck each player prepared (also in a lobby kept from before the block); a pod game's decks come
        // from the pod's source
        if (change.state?.let { preparation -> (command.action == GameAction.Ready || StartRules.canStart(summary, preparation)) &&
            unsupported(sql, summary, preparation, account.toString().takeUnless { StartRules.canStart(summary, preparation) }) } == true)
            return "automatumUnsupported"
        // I-D54: the play's state is the engine's, its decks parsed on the server. Switchreview #9 (I-D51/I-D52): the
        // server's own parse decides too, whatever support was stored for the decks (none, an older deck revision, a
        // pod's source): a card it can't read never starts a game as a dead card
        val seats = change.state?.takeIf { StartRules.canStart(summary, it) }?.let { engineSeats?.invoke(game.id!!) ?: EngineDecks.seats(it) }
        if (seats?.any { seat -> (seat.deck + seat.sideboard).any { it.definition == null } } == true) return "automatumUnsupported"
        change.state?.let { preparation ->
            PreparationStore.saveReadiness(sql, account, preparation)
            // The prepared deck with its outside cards, and their definitions
            if (command.action == GameAction.Ready) PreparationStore.saveSelection(sql, account, preparation)
            if (command.action is GameAction.ShowDeck) DeckVisibilityStore.chosen(sql, game.id!!, account)
            if (seats != null) {
                val play = PlayStore.start(sql, summary, preparation, command.requestId)
                // Its first event starts the game. Not kept warm here: the transaction hasn't committed yet
                run {
                    val now = java.time.OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC)
                    engineGames.start(sql, UUID.fromString(play.id), seats, arcanum.engine.state.PlayerId(play.starter.accountId), account) { player ->
                        OpponentPresences.read(sql, game.id!!, player, now).state == PresenceState.ONLINE }
                }
            }
        }
        return change.reason
    }

    /** Whether a deck of [preparation] ([only]'s alone when given) is one Automatum can't play fully, by the stored support
     * results (I-D52). */
    private fun unsupported(sql: DSLContext, summary: GameSummary, preparation: LobbyPreparation, only: String? = null): Boolean {
        summary.podId?.let { return PodService(runtime).unsupported(sql, UUID.fromString(it)) }
        return preparation.players.filter { only == null || it.accountId == only }.any { player -> player.deck?.let { deck ->
            DeckService(runtime).storedSupport(sql, UUID.fromString(player.accountId), deck.sourceId, deck.sourceRevision)?.supported == false
        } == true }
    }

    /** Recovery validates the newly implemented durable worker inputs, not forgotten client queues. */
    fun validatePending() = transaction { sql ->
        // A game or pending request the current code can't decode, or a game that fails its integrity check, is logged and
        // left to fail on its own; the service still starts (SYS-052). Only explicit maintenance repairs a damaged game.
        for (game in sql.selectFrom(GAME).where(GAME.LIFECYCLE.eq("ACTIVE")).orderBy(GAME.ID).forUpdate().fetch())
            StoredData.orSkip("game", game.id) { PlayStore.read(sql, game.id!!) }
        sql.select(GAME_INBOX.REQUEST_ID, GAME_INBOX.COMMAND).from(GAME_INBOX).where(GAME_INBOX.RESULT.isNull).fetchLazy().use { cursor ->
            for (row in cursor) StoredData.orSkip("game request", row.value1()) { GameWire.decodeClient(row.value2()!!) as GameCommand }
                ?.let { check(GameWire.valid(it)) }
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
