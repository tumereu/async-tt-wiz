package org.asyncttwiz.android

import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import java.util.concurrent.ExecutorService

/** LobbyController's list refresh: contacts, games, invitations and the selected game's head read over
 * HTTP and merged with summaries accepted meanwhile, plus the foreground/background switch and quiet
 * background failures. Main thread; the reads run on [worker]. */
internal class LobbyRefresh(private val scope: LobbyScope, private val api: Lobbies, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit, private val session: GameSession?,
    clock: () -> Long) {
    private var state: LobbyState
        get() = scope.state
        set(value) { scope.state = value }
    private val closed get() = scope.closed
    private val epoch get() = scope.epoch
    private val latest get() = scope.latest
    @Volatile private var refreshVersion = 0L
    @Volatile private var lifecycleVersion = 0L
    @Volatile var inForeground = true
        private set
    private var refreshError: String? = null
    private val quietFailures = QuietRefreshFailures(clock)
    /** Fences a list read in flight, so its result is dropped. */
    fun fence() { refreshVersion++ }
    fun clear() { refreshError = null; quietFailures.succeeded() }
    fun background() {
        inForeground = false
        session?.background()
        lifecycleVersion++
        state = state.copy(backgroundLoading = state.busy, syncedGames = emptySet())
    }
    fun foreground() {
        if (closed) return
        inForeground = true
        // Like GameSession.foreground's action-grace reset, an old interrupted read must not
        // present a reconnect error or prevent the silent resume attempt from starting.
        refreshVersion++
        state = state.copy(busy = false, backgroundLoading = false,
            error = state.error.takeUnless { it == refreshError })
        refreshError = null
        session?.foreground()
        // Refresh account lists silently, retaining the table and existing stream generations.
        refresh(background = true)
    }
    fun refresh(background: Boolean = false) {
        if (closed || !inForeground || state.busy || state.mutating) return
        val token = credential() ?: return
        val generation = epoch; val selected = state.selected
        val version = ++refreshVersion; val lifecycle = lifecycleVersion
        fun current() = generation == epoch && version == refreshVersion && inForeground
        val atStart = latest.toMap()
        // Nothing shown yet: ride out a short outage on the loading indicator instead of failing fast.
        val firstLoad = !state.loaded
        val delays = if (firstLoad) firstLoadRetryDelays else quickRetryDelays
        state = state.copy(busy = true, backgroundLoading = background,
            error = if (background || state.draft != null) state.error else null)
        worker.execute {
            if (!current()) return@execute
            val result = runCatching { runBlocking {
                val contacts = mutableListOf<AccountIdentity>(); val games = mutableListOf<GameSummary>(); val invites = mutableListOf<GameSummary>()
                suspend fun pages(read: suspend (String?) -> String?): Boolean {
                    var after: String? = null; val seen = mutableSetOf<String>()
                    do {
                        if (!current()) return false
                        after = read(after); check(after == null || seen.add(after!!))
                    } while (after != null)
                    return current()
                }
                if (!pages { cursor -> serviceRetry(current = ::current, delays = delays) { api.contacts(token, cursor) }.let { contacts.addAll(it.contacts); it.next } }) return@runBlocking null
                if (!pages { cursor -> serviceRetry(current = ::current, delays = delays) { api.games(token, false, cursor) }.let { games.addAll(it.games); it.next } }) return@runBlocking null
                if (!pages { cursor -> serviceRetry(current = ::current, delays = delays) { api.games(token, true, cursor) }.let { invites.addAll(it.games); it.next } }) return@runBlocking null
                val detail = selected?.let {
                    try { serviceRetry(current = ::current, delays = delays) { api.head(token, it) } } catch (failure: LobbyFailure) {
                        if (failure.code == "not_found") null else throw failure
                    }
                }
                LobbyState(contacts = contacts, games = games, invitations = invites, detail = detail)
            } }
            post { if (!closed && generation == epoch && version == refreshVersion) {
                val data = result.getOrNull()
                val failure = result.exceptionOrNull()
                if (data != null) {
                    quietFailures.succeeded()
                    state = state.copy(unreachable = false)
                    // Omission removes an old row. Only a revision learned since this read began
                    // can override omission; retained old lists must not resurrect cancelled games.
                    val newer = latest.values.filter { atStart[it.id] != it }
                    val games = (data.games + data.invitations + listOfNotNull(data.detail) + newer)
                        .groupBy { it.id }.values.map { versions -> versions.maxBy { GameWire.ordinal(it.revision) } }
                        .map { game -> latest[game.id]?.takeIf { GameSession.newerOrEqual(it.revision, game.revision) } ?: game }
                    val present = games.map { it.id }.toSet()
                    for (id in atStart.keys - present) session?.unavailable(id)
                    state = state.copy(contacts = data.contacts, games = state.games.filter { old -> games.any { it.id == old.id } },
                        invitations = state.invitations.filter { old -> games.any { it.id == old.id } }, busy = false,
                        backgroundLoading = false, loaded = true, error = state.error.takeUnless { it == refreshError })
                    refreshError = null
                    if (selected != null && state.selected == selected && data.detail == null && latest[selected] == atStart[selected]) {
                        session?.unavailable(selected); leaveUnavailable()
                    }
                    if (session == null) state = state.copy(games = stableGameRows(state.games, data.games), invitations = stableGameRows(state.invitations, data.invitations),
                        detail = if (state.selected == selected) data.detail else state.detail)
                    else {
                        session.watch(games); connectionState()
                    }
                } else if (background && !firstLoad && failure != null && transientServiceFailure(failure)) {
                    // A background poll of shown lists: keep them and let the next poll retry.
                    state = state.copy(busy = false, backgroundLoading = false, unreachable = quietFailures.failed())
                } else {
                    val silent = failure == null || lobbyLifecycleInterruption(failure,
                        interrupted = lifecycle != lifecycleVersion || !inForeground)
                    val previousError = refreshError
                    refreshError = if (silent) null else lobbyError(failure!!)
                    state = state.copy(busy = false, backgroundLoading = false,
                        error = refreshError ?: state.error.takeUnless { it == previousError })
                }
                // A user may choose another row while a list refresh is in flight.
                if (state.selected != selected) refresh()
            } }
        }
    }
    private fun leaveUnavailable() = scope.leaveUnavailable()
    private fun connectionState() = scope.connectionState()
}

internal fun updatedGameRows(rows: List<GameSummary>, game: GameSummary, included: Boolean): List<GameSummary> =
    if (!included) rows.filterNot { it.id == game.id }
    else if (rows.any { it.id == game.id }) rows.map { if (it.id == game.id) game else it }
    else rows + game
internal fun stableGameRows(old: List<GameSummary>, incoming: List<GameSummary>): List<GameSummary> {
    val byId = incoming.associateBy { it.id }
    val retained = old.mapNotNull { byId[it.id] }
    val ids = old.map { it.id }.toSet()
    return retained + incoming.filterNot { it.id in ids }
}
