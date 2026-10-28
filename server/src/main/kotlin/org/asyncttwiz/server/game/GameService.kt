package org.asyncttwiz.server.game

import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
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

/** One durable request/execution domain per persistent game, extended by preparation/gameplay. */
class GameService(private val runtime: ServiceRuntime) {
    private val json = GameWire.json
    private val lobby = LobbyService(runtime)
    private val gameplayStore = GameplayStore()
    internal fun evictExpiredBaselines() = gameplayStore.evictExpiredExports()
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
    private fun authorize(game: GameRecord?, actor: UUID, recovery: Boolean = false): GameRecord {
        if (game == null || actor !in listOf(game.inviterId, game.recipientId) ||
            (!recovery && game.lifecycle !in listOf("LOBBY", "ACTIVE")) ||
            // A held pod challenge does not exist for its recipient until they submit their deck.
            (game.podInvitationHeld == true && actor == game.recipientId))
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
                return@transaction inbox.result?.let { json.decodeFromString<GameResult>(it) }
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
        return inbox.result?.let { GameRequestStatus(id, "final", json.decodeFromString(it)) } ?: GameRequestStatus(id, "pending")
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
        locked(sql, uuid(gameId), actor(principal))
        readHead(sql, principal, gameId)
    }
    /** Stack reordering is retired: a same-Stack card move is the legacy reorder route, so new ones are refused here. */
    private fun reordersStack(tree: GameplayTree, command: GameCommand): Boolean {
        val batch = command.action as? GameAction.PlayBatch
        if (batch != null) return batch.actions.any {
            reordersStack(tree, command.copy(action = GameAction.Play(it))) ||
                (it as? PlayAction.Stack)?.action is StackAction.ReorderEffect
        }
        val move = (command.action as? GameAction.Play)?.action as? PlayAction.MoveCard ?: return false
        if (move.source.containerId != move.destination.containerId) return false
        return tree.stateAt(tree.currentNodeId)?.board?.containers
            ?.any { it.id == move.source.containerId && it.zone == PlayZone.STACK } == true
    }
    /** A socket may learn removal and recover originals, never reopen the removed replica. */
    fun subscriptionHead(principal: AccountService.Principal, gameId: String): GameHead = transaction { sql ->
        val game = locked(sql, uuid(gameId), actor(principal), recovery = true)
        if (game.lifecycle !in listOf("LOBBY", "ACTIVE")) {
            val summary = lobby.summary(sql, game.id!!)
            GameHead(gameId, summary.revision, summary)
        } else readHead(sql, principal, gameId)
    }
    private fun readHead(sql: DSLContext, principal: AccountService.Principal, gameId: String): GameHead {
        val summary = lobby.summary(sql, uuid(gameId))
        val play = PlayStore.read(sql, uuid(gameId))
        return GameHead(gameId, summary.revision, summary, preparation =
            if (play == null && canReadPreparation(summary, principal.account.accountId)) PreparationStore.read(sql, summary) else null,
            play = play, retainedTreeRequired = play != null, chatSync = ChatStore.sync(sql, uuid(gameId)), boundedEndpoint = true,
            ownPodDeckName = PreparationStore.ownPodDeckName(sql, uuid(gameId), actor(principal)))
    }
    /** Snapshot export and original-outcome lookup are independent of the incremental delivery window. */
    fun baseline(principal: AccountService.Principal, gameId: String, ids: List<String>): GameplayManifest {
        validateIds(ids)
        return transaction { sql ->
            val account = actor(principal); val game = uuid(gameId)
            locked(sql, game, account)
            val summary = lobby.summary(sql, game)
            val head = GameHead(gameId, summary.revision, summary, play = PlayStore.read(sql, game), retainedTreeRequired = true,
                chatSync = ChatStore.sync(sql, game), boundedEndpoint = true,
                ownPodDeckName = PreparationStore.ownPodDeckName(sql, game, account))
            gameplayStore.create(sql, account, head, ids.map { status(sql, game, account, it, "unknown") })
        }
    }
    fun baselineManifest(principal: AccountService.Principal, id: String): GameplayManifest = transaction { sql ->
        val manifest = gameplayStore.manifest(actor(principal), uuid(id).toString())
        val game = locked(sql, uuid(manifest.gameId), actor(principal))
        if (game.currentPlay.toString() != manifest.playId) throw GameProtocolFailure("baselineExpired")
        manifest
    }
    fun baselineChunk(principal: AccountService.Principal, id: String, index: Int): ByteArray {
        baselineManifest(principal, id) // Recheck access/lifecycle on every read; never reopen removed play.
        return gameplayStore.chunk(actor(principal), id, index)
    }
    fun revisions(principal: AccountService.Principal, gameId: String, after: String): GameRevisions {
        val base = GameWire.ordinal(after)
        return transaction { sql ->
            val game = locked(sql, uuid(gameId), actor(principal))
            if (base > game.revision!!) throw GameProtocolFailure("invalidInput")
            val rows = sql.select(GAME_REVISION.PAYLOAD).from(GAME_REVISION).where(GAME_REVISION.GAME_ID.eq(game.id))
                .and(GAME_REVISION.REVISION.gt(base)).orderBy(GAME_REVISION.REVISION).limit(101).fetch(GAME_REVISION.PAYLOAD)
            val revisions = rows.take(100).map {
                visibleRevision(json.decodeFromString<GameServerMessage>(it!!) as GameRevision, principal.account.accountId) }
            GameRevisions(revisions, if (rows.size > 100) revisions.last().resultRevision else null)
        }
    }

    fun history(principal: AccountService.Principal, gameId: String, playId: String, after: String): GameplayHistoryPage = transaction { sql ->
        uuid(playId)
        val game = locked(sql, uuid(gameId), actor(principal))
        val cursor = GameWire.ordinal(after)
        if (cursor > game.revision!!) throw GameProtocolFailure("invalidInput")
        val state = PlayStore.read(sql, game.id!!) ?: throw GameProtocolFailure("lifecycleEnded")
        if (state.id != playId) throw GameProtocolFailure("inapplicable")
        gameplayStore.history(sql, state, game.revision!!, cursor)
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

    internal fun visibleRevision(revision: GameRevision, accountId: String): GameRevision =
        if (revision.summary.lifecycle !in listOf(GameLifecycle.LOBBY, GameLifecycle.ACTIVE)) revision.copy(preparation = null, play = null, mutation = null, group = null, chatMessage = null, treeDelta = null)
        else if (canReadPreparation(revision.summary, accountId)) revision else revision.copy(preparation = null)

    /** An old unpublished board must not restore access after a later lifecycle commit. */
    fun publicationState(principal: AccountService.Principal, revision: GameRevision): GameServerMessage = transaction { sql ->
        val game = locked(sql, uuid(revision.gameId), actor(principal), recovery = true)
        if (game.lifecycle !in listOf("LOBBY", "ACTIVE")) {
            val summary = lobby.summary(sql, game.id!!)
            GameHead(revision.gameId, summary.revision, summary)
        } else visibleRevision(revision, principal.account.accountId)
    }

    /** Candidate games are independent; every selected game executes its lowest pending ordinal. */
    fun executeOne(): Boolean = executeOne(null)

    internal fun executeOne(listener: org.jooq.ExecuteListener?): Boolean {
        var committedTree: Pair<Long, GameplayTree>? = null
        val executed = transaction { context ->
        val sql = if (listener == null) context else DSL.using(context.configuration().derive(listener))
        val game = sql.selectFrom(GAME).where(DSL.exists(sql.selectOne().from(GAME_INBOX)
            .where(GAME_INBOX.GAME_ID.eq(GAME.ID)).and(GAME_INBOX.RESULT.isNull)))
            .orderBy(GAME.ID).limit(1).forUpdate().skipLocked().fetchOne() ?: return@transaction false
        val inbox = sql.selectFrom(GAME_INBOX).where(GAME_INBOX.GAME_ID.eq(game.id)).and(GAME_INBOX.RESULT.isNull)
            .orderBy(GAME_INBOX.INGRESS_ORDINAL).limit(1).fetchSingle()
        val command = GameWire.decodeClient(inbox.command!!) as GameCommand
        val requestSavepoint = context.connectionResult { it.setSavepoint() }
        val processingStarted = System.nanoTime()
        var historyLoaded = processingStarted
        var reduced = processingStarted
        var saved = processingStarted
        try {
        val before = game.revision!!
        val isGameplay = command.action.isPlay || command.action == GameAction.Undo || command.action == GameAction.Redo
        var treeDelta: GameplayTreeDelta? = null
        var createdMutation: GameplayMutation? = null
        var batchMutations: List<GameplayMutation> = emptyList()
        var insertedChat: ChatMessage? = null
        val gameplay = if (((command.action as? GameAction.Play)?.action as? PlayAction.Stack)?.action is StackAction.ReorderEffect)
            GameplayTreeChange(reason = "stackReorderingRemoved")
        else if (isGameplay) {
            val tree = if (game.lifecycle == "ACTIVE") gameplayStore.readCurrent(sql, game) else null
            if (tree == null) GameplayTreeChange(reason = "lifecycleEnded")
            else if (reordersStack(tree, command)) GameplayTreeChange(reason = "stackReorderingRemoved")
            else {
                historyLoaded = System.nanoTime()
                HistoryContinuation.change(tree, inbox.accountId.toString(), command).also { change ->
                    reduced = System.nanoTime()
                    change.tree?.let { next ->
                        treeDelta = gameplayStore.save(sql, tree, next, Math.addExact(before, 1), command.requestId)
                        committedTree = Math.addExact(before, 1) to next
                        if (command.action is GameAction.Play) createdMutation = next.nodes[change.targetId]
                        if (command.action is GameAction.PlayBatch) batchMutations = treeDelta!!.upserts
                            .filter { it.kind == "node" }.map { next.nodes.getValue(it.id) }
                    }
                    saved = System.nanoTime()
                }
            }
        } else null
        val rejection = if (game.lifecycle !in listOf("LOBBY", "ACTIVE")) "lifecycleEnded" else if (isGameplay) gameplay!!.reason else when (command.action) {
            is GameAction.AcceptResult -> PlayStore.accept(sql, lobby.summary(sql, game.id!!), inbox.accountId.toString(), command)
            GameAction.DeleteGame -> applyDeletion(sql, game, command)
            is GameAction.SendChat -> ChatStore.apply(sql, game, inbox.accountId!!, command).also { insertedChat = it.message }.reason
            GameAction.CancelSetup -> applyCancellation(sql, game, inbox.accountId!!, command)
            is GameAction.SelectDeck -> applySelection(sql, game, inbox.accountId!!, command)
            is GameAction.TransferPreparedCard -> applyTransfer(sql, game, inbox.accountId!!, command)
            is GameAction.SetPodGameBasics -> applyPodBasics(sql, game, inbox.accountId!!, command)
            is GameAction.SetStarter, GameAction.Ready, GameAction.WithdrawReady -> applyReadiness(sql, game, inbox.accountId!!, command)
            else -> applyInvitation(sql, game, inbox.accountId!!, command)
        }
        val revision = if (rejection == null && (gameplay == null || gameplay.tree != null)) Math.addExact(before, 1) else null
        val mutation = createdMutation
        var update: GameRevision? = null
        var encodedUpdate: String? = null
        if (revision != null) {
            // Reused summaries must have the same precision as PostgreSQL's timestamp(6).
            val updatedAt = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
            sql.update(GAME).set(GAME.REVISION, revision)
                .set(GAME.UPDATED_AT, updatedAt)
                .where(GAME.ID.eq(game.id)).execute()
            val next = gameplay?.tree
            val reducedPlay = next?.stateAt(next.currentNodeId)
            val summary = if (reducedPlay != null) {
                game.revision = revision; game.updatedAt = updatedAt
                lobby.summary(sql, game, reducedPlay.board.effectiveAttentionHolder)
            } else lobby.summary(sql, game.id!!)
            val play = reducedPlay ?: if (summary.lifecycle == GameLifecycle.ACTIVE) PlayStore.read(sql, game.id!!) else null
            update = GameRevision(game.id.toString(), before.toString(), revision.toString(), command.requestId,
                inbox.accountId.toString(), command.scope, summary,
                if (summary.lifecycle == GameLifecycle.LOBBY) PreparationStore.read(sql, summary) else null,
                if (gameplay == null && command.action !is GameAction.SendChat) play else null, mutation,
                if (mutation != null) play?.currentGroup else null,
                insertedChat, treeDelta)
            encodedUpdate = GameWire.encode(update)
            sql.insertInto(GAME_REVISION).set(GAME_REVISION.GAME_ID, game.id).set(GAME_REVISION.REVISION, revision)
                .set(GAME_REVISION.PAYLOAD, encodedUpdate).execute()
            // The shared reducer/store already chose the accepted group, including extensions.
            // Record within THIS execution transaction, never from a delivery timer or a retry.
            (listOfNotNull(mutation) + batchMutations).forEach { mutation ->
                val group = requireNotNull(next?.stateAt(mutation.id)?.currentGroup ?: play?.currentGroup)
                // Passing joins the preceding display group even when the other player made it.
                // Activity still belongs to the player who performed this individual event.
                check(group.author == mutation.author || mutation.action == PlayAction.PassAttention)
                val opponent = if (mutation.author == game.inviterId.toString()) game.recipientId!! else game.inviterId!!
                org.asyncttwiz.server.activity.ActivityService.record(sql, opponent, ActivityKind.GAME,
                    game.id!!, uuid(group.id), uuid(mutation.author), yourMove = summary.attentionHolder == opponent.toString())
            }
            insertedChat?.let { message ->
                val opponent = if (inbox.accountId == game.inviterId) game.recipientId!! else game.inviterId!!
                org.asyncttwiz.server.activity.ActivityService.record(sql, opponent, ActivityKind.GAME,
                    game.id!!, uuid(message.id), inbox.accountId, yourMove = summary.attentionHolder == opponent.toString(), newMessage = true)
            }
            if (isGameplay && mutation == null && batchMutations.isEmpty()) {
                val opponent = if (inbox.accountId == game.inviterId) game.recipientId!! else game.inviterId!!
                org.asyncttwiz.server.activity.ActivityService.record(sql, opponent, ActivityKind.GAME,
                    game.id!!, uuid(command.requestId), inbox.accountId, yourMove = summary.attentionHolder == opponent.toString())
            }
        }
        val created = when {
            revision == null -> emptyList()
            command.action is GameAction.PlayBatch -> batchMutations.flatMap { member ->
                listOf(member.id) + listOfNotNull(member.transition.movement?.to?.placement?.pileId?.takeIf {
                    (member.action as? PlayAction.MoveCard)?.destination?.insertion == MoveInsertion.EMPTY })
            }
            command.action is GameAction.AcceptResult -> listOf(update!!.summary.preparationRound)
            mutation != null -> listOf(mutation.id) + listOfNotNull(
                mutation.transition.movement?.to?.placement?.pileId?.takeIf {
                    (command.action as? GameAction.Play)?.action.let { action -> action is PlayAction.MoveCard && action.destination.insertion == MoveInsertion.EMPTY } })
            command.action is GameAction.SendChat -> listOf(command.requestId)
            update!!.play?.startRequestId == command.requestId -> listOf(update.play!!.id, update.play!!.root.id)
            command.action == GameAction.Reinvite -> listOf(update!!.summary.invitationId)
            command.action is GameAction.SelectDeck -> update!!.preparation!!.players.single {
                it.accountId == inbox.accountId.toString() }.deck!!.let { listOf(it.id) }
            else -> emptyList()
        }
        val result = GameResult(command.requestId, command.scope, gameplay?.outcome ?: if (rejection == null) "accepted" else "rejected",
            rejection, inbox.ingressOrdinal.toString(), revision?.toString(), created, mutation, gameplay?.targetId)
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
            committedTree = null
            val result = GameResult(command.requestId, command.scope, "rejected",
                (failure as? GameProtocolFailure)?.code ?: "internalError", inbox.ingressOrdinal.toString())
            context.update(GAME_INBOX).set(GAME_INBOX.RESULT, json.encodeToString(result))
                .where(GAME_INBOX.REQUEST_ID.eq(inbox.requestId)).execute()
            context.insertInto(GAME_OUTBOX).set(GAME_OUTBOX.GAME_ID, game.id).set(GAME_OUTBOX.ACCOUNT_ID, inbox.accountId)
                .set(GAME_OUTBOX.REQUEST_ID, inbox.requestId).set(GAME_OUTBOX.RESULT, GameWire.encode(result)).execute()
            org.slf4j.LoggerFactory.getLogger(GameService::class.java).error(
                "Gameplay request rejected game={} request={} failureType={}", game.id, command.requestId, failure.javaClass.simpleName)
            true
        } finally {
            org.slf4j.LoggerFactory.getLogger(GameService::class.java).info(
                "Gameplay command game={} action={} processingMs={} historyLoadMs={} reduceMs={} saveMs={} bookkeepingMs={}", command.scope.gameId,
                command.action.latencyType(), (System.nanoTime() - processingStarted) / 1_000_000.0,
                (historyLoaded - processingStarted) / 1_000_000.0, (reduced - historyLoaded) / 1_000_000.0,
                (saved - reduced) / 1_000_000.0, (System.nanoTime() - saved) / 1_000_000.0)
        }
    }
        committedTree?.let { (revision, tree) -> gameplayStore.committed(revision, tree) }
        return executed
    }

    private fun applyDeletion(sql: DSLContext, game: GameRecord, command: GameCommand): String? {
        if (game.lifecycle != "ACTIVE") return "lifecycleEnded"
        if (command.scope.playId != game.currentPlay?.toString()) return "inapplicable"
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
        val definitions = PreparationStore.definitions(sql, source?.entries?.map { it.definition }?.toSet() ?: emptySet())
        val change = PreparationRules.select(summary, PreparationStore.read(sql, summary), account.toString(),
            command.requestId, command.scope, action, source, definitions)
        change.state?.let { PreparationStore.saveSelection(sql, account, it) }
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
                if (account != game.recipientId || game.podInvitationHeld == true) return "accessDenied"
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
                org.asyncttwiz.server.activity.ActivityService.record(sql, game.recipientId!!,
                    ActivityKind.INVITATION, game.id!!, next, account)
            }
            else -> error("Preparation uses the shared preparation processor")
        }
        return null
    }

    private fun applyReadiness(sql: DSLContext, game: GameRecord, account: UUID, command: GameCommand): String? {
        val summary = lobby.summary(sql, game.id!!)
        val change = StartRules.change(summary, PreparationStore.read(sql, summary), account.toString(), command.scope, command.action)
        if (change.state?.let { preparation -> StartRules.canStart(summary, preparation) &&
            preparation.players.any { !DeckValidation.quantities(it.deck!!.entries.map { entry -> entry.quantity }) } } == true)
            return "invalidDeck"
        change.state?.let { preparation ->
            PreparationStore.saveReadiness(sql, account, preparation)
            if (StartRules.canStart(summary, preparation)) PlayStore.start(sql, summary, preparation, command.requestId)
        }
        return change.reason
    }

    /** Recovery validates the newly implemented durable worker inputs, not forgotten client queues. */
    fun validatePending() = transaction { sql ->
        for (game in sql.selectFrom(GAME).where(GAME.LIFECYCLE.eq("ACTIVE")).orderBy(GAME.ID).forUpdate().fetch()) {
            PlayStore.read(sql, game.id!!)?.let { gameplayStore.recover(sql, it) }
        }
        sql.select(GAME_INBOX.COMMAND).from(GAME_INBOX).where(GAME_INBOX.RESULT.isNull).fetchLazy().use { cursor ->
            for (row in cursor) check(GameWire.valid(GameWire.decodeClient(row.value1()!!) as GameCommand))
        }
        sql.selectCount().from(GAME_OUTBOX).where(GAME_OUTBOX.PUBLISHED.eq(false)).fetchSingle()
        Unit
    }

    /** Explicit offline maintenance; no normal command or recovery invokes exhaustive replay. */
    fun checkpointGameplay() = transaction { sql ->
        for (game in sql.selectFrom(GAME).where(GAME.LIFECYCLE.eq("ACTIVE")).orderBy(GAME.ID).forUpdate().fetch())
            PlayStore.read(sql, game.id!!)?.let { gameplayStore.rebuild(sql, it, game.revision!!) }
    }
    data class Publication(val id: Long, val gameId: String, val accountId: String, val revision: String?, val result: String)
    fun publications(): List<Publication> = transaction { sql ->
        sql.selectFrom(GAME_OUTBOX).where(GAME_OUTBOX.PUBLISHED.eq(false)).orderBy(GAME_OUTBOX.ID).limit(100).fetch().map {
            Publication(it.id!!, it.gameId.toString(), it.accountId.toString(), it.revision, it.result!!)
        }
    }
    fun published(id: Long) = transaction { sql -> sql.update(GAME_OUTBOX).set(GAME_OUTBOX.PUBLISHED, true).where(GAME_OUTBOX.ID.eq(id)).execute(); Unit }
}
