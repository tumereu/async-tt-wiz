package org.asyncttwiz.android

import org.asyncttwiz.appui.runBlocking
import org.asyncttwiz.protocol.*
import org.asyncttwiz.appui.ExecutorService
import kotlin.concurrent.Volatile

/** LobbyController's list refresh: contacts, games, invitations and the selected game's head read over
 * HTTP and merged with summaries accepted meanwhile, plus the foreground/background switch and quiet
 * background failures. Main thread; the reads run on [worker]. */
class LobbyRefresh(private val scope: LobbyScope, private val api: Lobbies, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit, private val session: GameSession?,
    clock: () -> Long) {
    private var state: LobbyState
        get() = scope.state
        set(value) { scope.state = value }
    private val closed get() = scope.closed
    private val epoch get() = scope.epoch
    private val latest get() = scope.latest
    @Volatile private var refreshVersion = 0L
    /** Switch-review #11: the tokens the last list reads answered (main thread): the next read asks only for what changed
     * since them. Null: the whole lists are read (first, after a failure, on return to the foreground). */
    private var gamesSince: String? = null
    private var invitationsSince: String? = null
    @Volatile private var lifecycleVersion = 0L
    @Volatile var inForeground = true
        private set
    private var refreshError: String? = null
    private val quietFailures = QuietRefreshFailures(clock)
    /** Fences a list read in flight, so its result is dropped. */
    fun fence() { refreshVersion++ }
    fun clear() { refreshError = null; quietFailures.succeeded(); gamesSince = null; invitationsSince = null }
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
        // Back from the background: the whole lists (names and avatars change without a game changing)
        gamesSince = null; invitationsSince = null
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
        // Switch-review #11: once the lists are loaded, only what changed since the last read (summaries: no play or
        // engine state); with a game open nothing else is read (no contacts, no head: the game's own stream has it)
        val changes = state.loaded && gamesSince != null && invitationsSince != null
        val sinceGames = gamesSince; val sinceInvitations = invitationsSince
        val needsHead = selected != null && (!changes || state.detail?.id != selected)
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
                val removedGames = mutableListOf<String>(); val removedInvites = mutableListOf<String>()
                var gamesToken: String? = null; var invitesToken: String? = null
                suspend fun pages(read: suspend (String?) -> String?): Boolean {
                    var after: String? = null; val seen = mutableSetOf<String>()
                    do {
                        if (!current()) return false
                        after = read(after).also { check(it == null || seen.add(it)) }
                    } while (after != null)
                    return current()
                }
                suspend fun list(invitations: Boolean, since: String?, cursor: String?): GameList =
                    (if (since != null) api.gamesSince(token, invitations, since, cursor) else null)
                        ?: api.games(token, invitations, cursor)
                var delta = changes
                if (!changes && !pages { cursor -> serviceRetry(current = ::current, delays = delays) { api.contacts(token, cursor) }.let { contacts.addAll(it.contacts); it.next } }) return@runBlocking null
                if (!pages { cursor -> serviceRetry(current = ::current, delays = delays) { list(false, sinceGames.takeIf { changes }, cursor) }.let {
                        if (cursor == null) { gamesToken = it.token; if (changes && it.token == null) delta = false }
                        games.addAll(it.games); removedGames.addAll(it.removed); it.next } }) return@runBlocking null
                if (!pages { cursor -> serviceRetry(current = ::current, delays = delays) { list(true, sinceInvitations.takeIf { changes }, cursor) }.let {
                        if (cursor == null) invitesToken = it.token
                        invites.addAll(it.games); removedInvites.addAll(it.removed); it.next } }) return@runBlocking null
                // A server that can't answer changes answered whole lists: then they are read as such (contacts too)
                if (changes && !delta) {
                    if (!pages { cursor -> serviceRetry(current = ::current, delays = delays) { api.contacts(token, cursor) }.let { contacts.addAll(it.contacts); it.next } }) return@runBlocking null
                }
                val detail = selected?.takeIf { needsHead }?.let {
                    try { serviceRetry(current = ::current, delays = delays) { api.head(token, it) } } catch (failure: LobbyFailure) {
                        if (failure.code == "not_found") null else throw failure
                    }
                }
                ListRead(LobbyState(contacts = contacts, games = games, invitations = invites, detail = detail), delta,
                    removedGames, removedInvites, gamesToken, invitesToken)
            } }
            post { if (!closed && generation == epoch && version == refreshVersion) {
                val read = result.getOrNull()
                val failure = result.exceptionOrNull()
                // A changes read is applied to the lists shown, so what follows sees whole lists either way
                val data = read?.let { r -> if (!r.delta) r.state else r.state.copy(contacts = state.contacts,
                    games = applyChanges(state.games, r.state.games, r.removedGames),
                    invitations = applyChanges(state.invitations, r.state.invitations, r.removedInvites),
                    detail = r.state.detail ?: state.detail?.takeIf { it.id == selected }?.let { shown ->
                        (r.state.games + r.state.invitations).firstOrNull { it.id == selected } ?: shown }) }
                if (read != null) { gamesSince = read.gamesToken; invitationsSince = read.invitesToken }
                else if (failure != null) { gamesSince = null; invitationsSince = null }
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
                    for (id in atStart.keys - present) {
                        AppLog.info("lobby", "Game ${id.take(8)} missing from the list read: unavailable")
                        session?.unavailable(id)
                    }
                    state = state.copy(contacts = data.contacts, games = state.games.filter { old -> games.any { it.id == old.id } },
                        invitations = state.invitations.filter { old -> games.any { it.id == old.id } }, busy = false,
                        backgroundLoading = false, loaded = true, error = state.error.takeUnless { it == refreshError })
                    refreshError = null
                    // Only the server's answer to the head this read asked for says the open game is gone. A read that didn't
                    // ask for it (the game was open with its detail when it began) has no detail when the player left and
                    // opened the game again meanwhile (e2e GameEndDialogTest, TRN-065: the background sync's read landing
                    // after the conceder went back to the game showed the Games list, not the dialog over the lobby)
                    if (selected != null && needsHead && state.selected == selected && data.detail == null && latest[selected] == atStart[selected]) {
                        AppLog.info("lobby", "Game ${selected.take(8)} not found by its head read")
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
                    refreshError = if (silent || failure == null) null else lobbyError(failure)
                    state = state.copy(busy = false, backgroundLoading = false,
                        error = refreshError ?: state.error.takeUnless { it == previousError })
                }
                // A user may choose another row while a list refresh is in flight, or the same game again (its detail
                // reset, its head not asked for by this read): the next read asks for it.
                if (state.selected != selected || (state.selected != null && state.detail == null && !needsHead)) {
                    if (state.selected == selected) AppLog.info("lobby", "Game ${selected?.take(8)} opened again during a list read: its head read next")
                    refresh()
                }
            } }
        }
    }
    /** One list read: whole lists, or ([delta]) the games changed since the tokens and the ids that left each list. */
    private class ListRead(val state: LobbyState, val delta: Boolean, val removedGames: List<String>, val removedInvites: List<String>,
        val gamesToken: String?, val invitesToken: String?)

    /** [rows] with [changed] put in (replacing a row of the same game, else added) and [removed] taken out. */
    private fun applyChanges(rows: List<GameSummary>, changed: List<GameSummary>, removed: List<String>): List<GameSummary> {
        val byId = changed.associateBy { it.id }
        val gone = removed.toSet() - byId.keys
        val kept = rows.filter { it.id !in gone }.map { old -> byId[old.id]?.takeIf { GameSession.newerOrEqual(it.revision, old.revision) } ?: old }
        return kept + changed.filter { new -> kept.none { it.id == new.id } }
    }

    private fun leaveUnavailable() = scope.leaveUnavailable()
    private fun connectionState() = scope.connectionState()
}

fun updatedGameRows(rows: List<GameSummary>, game: GameSummary, included: Boolean): List<GameSummary> =
    if (!included) rows.filterNot { it.id == game.id }
    else if (rows.any { it.id == game.id }) rows.map { if (it.id == game.id) game else it }
    else rows + game
fun stableGameRows(old: List<GameSummary>, incoming: List<GameSummary>): List<GameSummary> {
    val byId = incoming.associateBy { it.id }
    val retained = old.mapNotNull { byId[it.id] }
    val ids = old.map { it.id }.toSet()
    return retained + incoming.filterNot { it.id in ids }
}
