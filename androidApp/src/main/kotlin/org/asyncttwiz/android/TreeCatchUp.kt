package org.asyncttwiz.android

import org.asyncttwiz.android.GameSession.Companion.newerOrEqual
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.GameplayTreeStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

/** GameSession's retained-tree synchronization: restoring a play from its local cache, applying
 * accepted deltas on the serial tree worker, detecting heads that are ahead of the received deltas,
 * and catching up (tree transfer, or baseline without a cache) with the deltas that arrive meanwhile. */
internal class TreeCatchUp(private val session: Session, private val connection: GameConnection,
    private val treeCache: ((String, String, String) -> GameplayTreeStore?)?, private val treeWorker: ExecutorService,
    private val uiThread: Thread, private val now: () -> Long, private val reconnectTrace: ReconnectRecorder,
    private val chats: SessionChat) {
    /** What catch-up reads from and hands back to the owning GameSession. */
    interface Session : SessionScope {
        val token: String?
        fun credential(): String?
        fun stream(gameId: String): SessionStream?
        /** The game's replica, created when missing. */
        fun replica(gameId: String): GameplayReplica
        /** The game's replica if there is one. */
        fun retained(gameId: String): GameplayReplica?
        fun summary(gameId: String): GameSummary?
        /** This game's queued originals, in order. */
        fun pending(gameId: String): List<GameCommand>
        fun sent(requestId: String): Boolean
        fun install(game: GameSummary, revision: String, preparation: LobbyPreparation?, play: PlayState?)
        fun result(result: GameResult)
        fun clearError()
        fun flush()
        fun recoveryReady()
        fun connectionUnavailable(cause: String)
        fun send(message: GameClientMessage): Boolean
    }

    // Only treeWorker touches these coordinators and their mutable materializations.
    private val downloads = mutableMapOf<String, Pair<GameplayTreeStore, TreeDownload>>()
    private fun download(cache: GameplayTreeStore): TreeDownload {
        downloads[cache.gameId]?.takeIf { it.first === cache }?.let { return it.second }
        return TreeDownload(cache, uiThread).also { downloads[cache.gameId] = cache to it }
    }
    private fun evictDownload(gameId: String) {
        caches.remove(gameId)?.invalidate()
        if (!treeWorker.isShutdown) treeWorker.execute { downloads.remove(gameId) }
    }
    private fun presentation(value: PublishedDownloadedTree) = value.generation.copy(tree = value.generation.tree.materializedCopy())
    private val caches = ConcurrentHashMap<String, GameplayTreeStore>()
    private val observedPlays = ConcurrentHashMap<String, String>()
    private val restoring = mutableSetOf<String>()
    private data class Recovery(val ids: List<String>, val notAdmitted: Set<String>, var through: String, val playId: String?,
        val tail: MutableList<GameRevision> = mutableListOf())
    private val recoveries = mutableMapOf<String, Recovery>()
    // The server refused to transfer this play: it is no longer current. Its remaining historical
    // deliveries (e.g. a resume from an old cursor) must not restart catch-up. One entry per game.
    private val supersededPlays = mutableMapOf<String, String>()
    // Main-thread receipt cursor, distinct from the worker's published cursor. Heartbeats may
    // observe a committed head while its contiguous deltas are still waiting for disk work.
    private val treeThrough = mutableMapOf<String, String>()
    private val treeHead = mutableMapOf<String, String>()
    private data class HeadGap(val since: Long, var through: String)
    private val headGaps = mutableMapOf<String, HeadGap>()

    fun cache(gameId: String): GameplayTreeStore? = caches[gameId]
    fun observedPlay(gameId: String): String? = observedPlays[gameId]
    fun recovering(gameId: String) = gameId in recoveries
    /** No head is ahead of the received deltas, and every received delta has been published. */
    fun caughtUp(gameId: String) = gameId !in headGaps &&
        treeThrough[gameId]?.let { through -> session.retained(gameId)?.revision?.let { newerOrEqual(it, through) }
            ?: (session.summary(gameId)?.lifecycle != GameLifecycle.ACTIVE) } != false
    /** The play's newest known head is at or behind [revision]. */
    fun reachesHead(gameId: String, revision: String) = treeHead[gameId]?.let { newerOrEqual(revision, it) } == true
    fun watch(current: GameSummary) {
        observePlay(current)
        if (current.lifecycle == GameLifecycle.ACTIVE && current.playId != null &&
            session.retained(current.id)?.accepted == null && restoring.add(current.id)) loadCached(current)
    }
    /** A head for the superseded play makes that play current again. */
    fun headArrived(summary: GameSummary) {
        if (summary.lifecycle == GameLifecycle.ACTIVE && summary.playId == supersededPlays[summary.id])
            supersededPlays.remove(summary.id)
    }
    /** Tracks a head's catch-up position. Returns false for a head without a play (lobby or ended),
     * which the session installs itself. */
    fun head(message: GameHead): Boolean {
        observePlay(message.summary)
        recoveries[message.gameId]?.let { if (newerOrEqual(message.revision, it.through)) it.through = message.revision }
        if (message.play == null) return false
        if (treeHead[message.gameId]?.let { newerOrEqual(it, message.revision) } != true)
            treeHead[message.gameId] = message.revision
        val replica = session.replica(message.gameId)
        if (replica.revision == message.revision) {
            session.stream(message.gameId)?.head = true
        } else if (treeCache == null && !message.retainedTreeRequired && replica.accepted == null && replica.root(message)) {
            session.install(message.summary, message.revision, null, message.play)
            session.stream(message.gameId)?.head = true
        } else if (treeThrough[message.gameId]?.let { newerOrEqual(it, message.revision) } == true) {
            // Publication will mark this stream ready. Queued work is not a revision gap.
        } else if (replica.revision?.let { newerOrEqual(it, message.revision) } != true) {
            if (replica.accepted?.id == message.summary.playId && message.gameId !in recoveries) {
                // Heads read committed state before the publication worker necessarily
                // sends its deltas. Keep using the materialized replica during that race.
                headGaps.getOrPut(message.gameId) { HeadGap(now(), message.revision) }.let {
                    if (newerOrEqual(message.revision, it.through)) it.through = message.revision
                }
            } else recover(message.gameId, message.revision, "headAhead:local=${replica.revision}:queued=${treeThrough[message.gameId]}:head=${message.revision}")
        }
        return true
    }
    /** An accepted revision of the current active play: apply it, or queue it into catch-up. */
    fun revision(message: GameRevision) {
        recoveries[message.gameId]?.let {
            it.tail.add(message)
            if (newerOrEqual(message.resultRevision, it.through)) it.through = message.resultRevision
        }
        val replica = session.replica(message.gameId)
        if (treeCache != null && message.gameId !in recoveries) {
            applyTree(message)
        } else if (treeCache != null && message.gameId in recoveries) {
            // Recovery owns one isolated generation and its interleaved tail.
        } else if (replica.accepted == null && message.play != null && message.play!!.currentNodeId == message.play!!.root.id) {
            replica.root(GameHead(message.gameId, message.resultRevision, message.summary, play = message.play))
            session.install(message.summary, message.resultRevision, null, message.play)
        } else if (replica.apply(message)) session.install(message.summary, message.resultRevision, null, replica.accepted)
        else {
            if (message.gameId !in recoveries) { recover(message.gameId, message.resultRevision, "replica:${replica.applyFailure}"); recoveries[message.gameId]?.tail?.add(message) }
        }
    }
    fun checkHeadGaps() {
        // Bounded by watched games, never by their action histories. Receiving contiguous deltas
        // is sufficient; a slow disk/publication worker must not cause another tree download.
        for ((gameId, gap) in headGaps.toMap()) {
            val through = treeThrough[gameId] ?: session.retained(gameId)?.revision
            if (through?.let { newerOrEqual(it, gap.through) } == true) headGaps.remove(gameId)
            else if (now() - gap.since >= 2_000) {
                recover(gameId, gap.through, "headDeltaGraceExpired:local=$through:head=${gap.through}")
            }
        }
    }
    private fun recover(gameId: String, through: String, reason: String = "explicitHistoryDownload") {
        if (gameId in recoveries) return
        val stream = session.stream(gameId) ?: return
        headGaps.remove(gameId)
        reconnectTrace.start(gameId, "headRequiresCatchUp")
        reconnectTrace.step(gameId, reason.take(160))
        reconnectTrace.step(gameId, "catchUpStart")
        DebugJank.catchUp(gameId, true)
        treeThrough.remove(gameId)
        stream.head = false
        val ids = session.pending(gameId).map { it.requestId }
        val recovery = Recovery(ids, ids.filter { !session.sent(it) }.toSet(), through, observedPlays[gameId])
        recoveries[gameId] = recovery
        val generation = session.epoch; val current = session.token
        fun rejected(code: String) = session.post {
            if (!session.closed && generation == session.epoch && current == session.credential() && session.sameAccount() && recoveries[gameId] === recovery)
                recoveryRejected(gameId, recovery, code)
        }
        if (treeCache != null && connection.treeTransfer(gameId, recovery.ids, ::rejected) { transfer -> session.post {
            if (!session.closed && generation == session.epoch && current == session.credential() && session.sameAccount() && recoveries[gameId] === recovery) {
                if (transfer == null) { recoveries.remove(gameId); session.connectionUnavailable("treeManifestFailed") }
                else { reconnectTrace.step(gameId, "treeManifestReceived"); stageTree(gameId, recovery, transfer, generation, current) }
            }
        } }) return
        if (!connection.baseline(gameId, recovery.ids, ::rejected) { baseline -> session.post {
            if (!session.closed && generation == session.epoch && current == session.credential() && session.sameAccount() && recoveries[gameId] === recovery) {
                recoveries.remove(gameId)
                val installed = runCatching {
                    requireNotNull(baseline)
                    require(baseline.snapshot.head.gameId == gameId)
                    require(baseline.statuses.map { it.requestId }.toSet() == recovery.ids.toSet())
                    val replica = session.replica(gameId)
                    val local = session.pending(gameId).filter { it.requestId !in recovery.ids && !session.sent(it.requestId) }
                    val correlated = baseline.copy(statuses = baseline.statuses.map {
                        if (it.status == "unknown" && it.requestId in recovery.notAdmitted) it.copy(status = "notAdmitted") else it } +
                        local.map { GameRequestStatus(it.requestId, "notAdmitted") })
                    if (!replica.baseline(correlated, recovery.tail, recovery.through)) return@runCatching false
                    val latest = recovery.tail.maxByOrNull { GameWire.ordinal(it.resultRevision) }
                        ?.takeIf { it.resultRevision == replica.revision }
                    val head = baseline.snapshot.head
                    session.install(latest?.summary ?: head.summary, replica.revision!!, null, replica.accepted)
                    chats.installChatHead(head)
                    recovery.tail.forEach { update -> update.chatMessage?.let {
                        chats.installChat(gameId, update.resultRevision, listOf(it), snapshot = false)
                    } }
                    for (status in baseline.statuses) if (status.status == "final") session.result(status.result!!)
                    true
                }.getOrDefault(false)
                if (installed) { stream.head = true; session.clearError(); session.flush(); session.recoveryReady(); session.statusChanged() } else session.connectionUnavailable("baselineFailed")
            }
        } }) { recoveries.remove(gameId); session.connectionUnavailable("baselineNotStarted") }
    }

    /** The server has no transferable current play (ended, back in the lobby, replaced or no longer
     * accessible). That is game state, not connectivity: retire only this catch-up, never the shared
     * connection, and re-read the authoritative head from the newest known revision. */
    private fun recoveryRejected(gameId: String, recovery: Recovery, code: String) {
        recoveries.remove(gameId)
        recovery.playId?.let { supersededPlays[gameId] = it }
        treeThrough.remove(gameId); headGaps.remove(gameId)
        reconnectTrace.step(gameId, "catchUpRejected:$code")
        DebugJank.catchUp(gameId, false)
        val cursor = listOfNotNull(recovery.through, session.summary(gameId)?.revision).maxBy(GameWire::ordinal)
        session.send(SubscribeGame(gameId, cursor))
        session.statusChanged()
    }

    /** O(1): a delivery for a play that newer knowledge (a later summary of another lifecycle/play,
     * or a refused transfer) already retired. Catching it up could only fail against the server. */
    fun superseded(message: GameRevision): Boolean {
        val play = message.summary.playId?.takeIf { message.summary.lifecycle == GameLifecycle.ACTIVE } ?: return false
        if (supersededPlays[message.gameId] == play) return true
        val known = session.summary(message.gameId) ?: return false
        return GameWire.ordinal(known.revision) > GameWire.ordinal(message.resultRevision) &&
            (known.lifecycle != GameLifecycle.ACTIVE || known.playId != play)
    }

    fun observePlay(game: GameSummary) {
        val play = game.playId.takeIf { game.lifecycle == GameLifecycle.ACTIVE }
        if (play == null) observedPlays.remove(game.id) else observedPlays.put(game.id, play)
        recoveries[game.id]?.takeIf { it.playId != play }?.let {
            recoveries.remove(game.id)
            treeThrough.remove(game.id); treeHead.remove(game.id); headGaps.remove(game.id)
            DebugJank.catchUp(game.id, false)
        }
        caches[game.id]?.takeIf { it.playId != play }?.let { evictDownload(game.id) }
    }

    private fun loadCached(game: GameSummary) {
        val playId = game.playId ?: return; val owner = session.account ?: return
        val createCache = treeCache ?: return
        val generation = session.epoch
        fun valid() = !session.closed && generation == session.epoch && session.account == owner && session.sameAccount() && observedPlays[game.id] == playId
        treeWorker.execute {
            val published = runCatching {
                if (!valid()) return@runCatching null
                val cache = caches[game.id]?.takeIf { it.isCurrent() } ?: (createCache(owner, game.id, playId) ?: return@runCatching null).also {
                    if (!valid()) it.invalidate()
                    caches[game.id] = it
                }
                cache.guard(::valid)
                download(cache).accepted()?.takeIf {
                    it.generation.tree.stateAt(it.generation.tree.currentNodeId) != null }
            }.getOrNull()
            val prepared = published?.let(::presentation)
            session.post {
                restoring.remove(game.id)
                if (!valid() || published == null || session.retained(game.id)?.revision?.let {
                        GameWire.ordinal(it) >= published.version.revision } == true) return@post
                val at = published.version.revision.toString()
                val endpoint = prepared!!.tree.stateAt(prepared.tree.currentNodeId)
                val head = GameHead(game.id, at, game.copy(revision = at), play = endpoint)
                val replica = session.replica(game.id)
                if (replica.published(head, prepared, at, emptyList(), emptyList(), published.history)) {
                    session.install(head.summary, at, null, replica.accepted); session.statusChanged()
                }
            }
        }
    }

    private fun delta(update: GameRevision): TreeRecordDelta {
        // Shared metadata/communication owners can advance the game revision without changing the
        // tree. They still advance the SAME accepted cursor; never erase history or force a second
        // cache/journal. An unmatched full board without tree records fails closed to baseline.
        require(update.treeDelta != null || update.play == null)
        return TreeRecordDelta(update.gameId, requireNotNull(update.summary.playId),
            GameWire.ordinal(update.baseRevision), GameWire.ordinal(update.resultRevision), update.treeDelta?.upserts.orEmpty(),
            update.treeDelta?.removals.orEmpty().map { it.kind to it.id })
    }

    private fun stageTree(gameId: String, recovery: Recovery, transfer: GameplayTreeTransfer, generation: Long, current: String?) {
        val playId = transfer.manifest.playId
        val owner = session.account ?: return session.connectionUnavailable("recoveryAccountMissing")
        val createCache = treeCache ?: return
        val tail = recovery.tail.toList().sortedBy { GameWire.ordinal(it.resultRevision) }
        val through = recovery.through
        fun valid() = !session.closed && generation == session.epoch && current == session.credential() && session.sameAccount() && observedPlays[gameId] == playId
        fun deliver(published: PublishedDownloadedTree?, appliedTail: List<GameRevision>) {
            if (!valid() || recoveries[gameId] !== recovery) return
            if (published != null && GameWire.ordinal(recovery.through) > published.version.revision) {
                val late = recovery.tail.filter { GameWire.ordinal(it.resultRevision) > published.version.revision }
                    .distinctBy { it.resultRevision }.sortedBy { GameWire.ordinal(it.resultRevision) }
                if (late.firstOrNull()?.baseRevision == published.version.revision.toString() &&
                    late.last().resultRevision == recovery.through) {
                    // Staging can take seconds. Arrivals during it are a delta tail, not a reason
                    // to discard a complete baseline and start another expensive download.
                    treeWorker.execute {
                        val caughtUp = runCatching {
                            var next = published
                            for (update in late) {
                                if (!valid()) return@runCatching null
                                next = (download(requireNotNull(caches[gameId])).apply(delta(update)) as? TreePublication.Published)?.value
                                    ?: return@runCatching null
                            }
                            next
                        }.getOrNull()
                        val prepared = caughtUp?.let { it.copy(generation = presentation(it)) }
                        session.post { deliver(prepared, appliedTail + late) }
                    }
                    return
                }
            }
            recoveries.remove(gameId)
            if (published == null || GameWire.ordinal(recovery.through) > published.version.revision) {
                recover(gameId, recovery.through, "stagedTreeBehindOrFailed"); return
            }
            val replica = session.replica(gameId)
            if (!replica.published(requireNotNull(transfer.manifest.head), published.generation,
                published.version.revision.toString(), transfer.manifest.statuses, appliedTail, published.history)) { session.connectionUnavailable("treePublicationFailed"); return }
            val summary = appliedTail.lastOrNull { it.resultRevision == replica.revision }?.summary ?: transfer.manifest.head!!.summary
            session.install(summary, replica.revision!!, null, replica.accepted)
            val head = requireNotNull(transfer.manifest.head)
            chats.installChatHead(head)
            appliedTail.forEach { update -> update.chatMessage?.let { chats.installChat(gameId, update.resultRevision, listOf(it), snapshot = false) } }
            for (status in transfer.manifest.statuses) if (status.status == "final") session.result(requireNotNull(status.result))
            session.stream(gameId)?.head = true; session.clearError(); session.flush(); session.recoveryReady(); session.statusChanged()
        }
        treeWorker.execute {
            if (!valid()) return@execute
            val published = runCatching {
                require(transfer.manifest.statuses.map { it.requestId }.toSet() == recovery.ids.toSet())
                transfer.manifest.statuses.forEach { status ->
                    require(status.status in setOf("final", "pending", "unknown"))
                    if (status.status == "final") require(status.result?.requestId == status.requestId &&
                        status.result?.scope == GameScope(gameId, playId = playId))
                }
                val cache = caches[gameId]?.takeIf { it.playId == playId && it.isCurrent() }
                    ?: (createCache(owner, gameId, playId) ?: return@runCatching null).also {
                    if (!valid()) it.invalidate()
                    caches[gameId] = it
                }
                cache.guard(::valid)
                if (!valid()) return@runCatching null
                val download = download(cache)
                val staged = download.stage(transfer.plan) { index -> if (valid()) transfer.openChunk(index) else null }
                    ?: return@runCatching null
                if (!valid()) return@runCatching null
                val result = download.publish(staged, tail.filter { GameWire.ordinal(it.resultRevision) > transfer.plan.revision }
                    .map(::delta), GameWire.ordinal(through))
                (result as? TreePublication.Published)?.value?.takeIf { value ->
                    value.generation.tree.stateAt(value.generation.tree.currentNodeId) != null }
            }.getOrNull()
            val prepared = published?.let { it.copy(generation = presentation(it)) }
            session.post { deliver(prepared, tail) }
        }
    }

    private fun applyTree(update: GameRevision) {
        val queued = treeThrough[update.gameId] ?: session.retained(update.gameId)?.revision
        if (queued != null && newerOrEqual(queued, update.resultRevision)) return
        if (queued == null || queued != update.baseRevision) {
            recover(update.gameId, update.resultRevision, "deltaGap:queued=$queued:base=${update.baseRevision}:result=${update.resultRevision}")
            recoveries[update.gameId]?.tail?.add(update)
            return
        }
        treeThrough[update.gameId] = update.resultRevision
        val generation = session.epoch; val current = session.token; val playId = update.summary.playId
        fun valid() = !session.closed && generation == session.epoch && current == session.credential() && session.sameAccount() && observedPlays[update.gameId] == playId
        treeWorker.execute {
            if (!valid()) return@execute
            var reason = "cacheMissing"
            val published = runCatching {
                val result = caches[update.gameId]?.let { cache ->
                    // The retained owner survives reconnect. Renew its operation fence only on
                    // this serial worker, after this socket operation passed its own epoch check.
                    cache.guard(::valid)
                    download(cache).let {
                    it.apply(delta(update)).also { _ -> reason = it.failure ?: "endpointMissing" }
                } }
                val value = result as? TreePublication.Published
                value?.value?.takeIf { it.generation.tree.stateAt(it.generation.tree.currentNodeId) != null }
            }.onFailure { failure ->
                val check = failure.stackTrace.firstOrNull { it.className.startsWith("org.asyncttwiz") }
                reason = "${failure.javaClass.simpleName}:${check?.fileName}:${check?.lineNumber}"
            }.getOrNull()
            val prepared = published?.let(::presentation)
            session.post {
                if (!valid() || update.gameId in recoveries) return@post
                if (published == null) { recover(update.gameId, update.resultRevision, "treeApply:$reason"); return@post }
                val replica = session.replica(update.gameId)
                if (session.retained(update.gameId)?.revision?.let { GameWire.ordinal(it) >= published.version.revision } == true) return@post
                val endpoint = prepared!!.tree.stateAt(prepared.tree.currentNodeId)
                val head = GameHead(update.gameId, update.resultRevision, update.summary, play = endpoint)
                if (replica.published(head, prepared, published.version.revision.toString(), emptyList(), listOf(update), published.history, animate = true)) {
                    if (treeHead[update.gameId]?.let { newerOrEqual(replica.revision!!, it) } != false)
                        session.stream(update.gameId)?.head = true
                    session.install(update.summary, replica.revision!!, null, replica.accepted); session.statusChanged()
                    session.flush(); session.recoveryReady()
                }
            }
        }
    }
    /** The game's personal context is gone (unavailable, cancelled or deleted). */
    fun remove(gameId: String) {
        observedPlays.remove(gameId)
        evictDownload(gameId)
        treeThrough.remove(gameId); treeHead.remove(gameId)
        recoveries.remove(gameId); supersededPlays.remove(gameId)
        headGaps.remove(gameId)
    }
    /** Catch-up and receipt cursors belong to the old socket epoch. */
    fun disconnected() { recoveries.clear(); treeThrough.clear(); treeHead.clear(); headGaps.clear() }
    fun clear() {
        caches.values.forEach { it.invalidate() }; caches.clear(); observedPlays.clear(); restoring.clear()
        if (!treeWorker.isShutdown) treeWorker.execute { downloads.clear() }
        supersededPlays.clear()
    }
}
