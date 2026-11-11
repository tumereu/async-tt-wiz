package org.asyncttwiz.android

import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import java.util.concurrent.ExecutorService

/** LobbyController's notification hand-off: opens the game a notification (activity) points to, or
 * explains that it is gone, and keeps its target current as the game changes. Main thread; the read
 * runs on [worker]. */
internal class LobbyActivityRoutes(private val scope: LobbyScope, private val api: Lobbies, private val credential: () -> String?,
    private val accountId: () -> String?, private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    private val session: GameSession?, private val openGame: () -> Unit,
    private val activityTarget: (suspend (String, ActivityRoute) -> ActivityTarget)?) {
    private var state: LobbyState
        get() = scope.state
        set(value) { scope.state = value }
    private val closed get() = scope.closed
    private val epoch get() = scope.epoch
    private val latest get() = scope.latest
    private var routeVersion = 0L
    /** Drops a notification open still in flight. */
    fun fence() { routeVersion++ }
    fun activityUnavailable(message: String = "This activity is no longer available. Showing your current Games.") {
        leaveUnavailable()
        state = state.copy(error = message)
        openGame(); refresh(background = true)
    }
    fun openActivity(route: ActivityRoute, current: () -> Boolean) {
        if (route.kind.isPodActivity()) return
        val read = activityTarget ?: return activityUnavailable()
        val token = credential() ?: return activityUnavailable()
        if (closed || state.mutating) return
        if (route.accountId != accountId() || !current()) return activityUnavailable()
        val generation = epoch; val version = ++routeVersion
        worker.execute {
            if (generation != epoch) return@execute
            val result = runCatching { runBlocking {
                val target = read(token, route)
                require(target.gameId == route.gameId)
                target to if (target.destination == ActivityDestination.UNAVAILABLE) null else api.head(token, route.gameId)
            } }
            post {
                if (closed || generation != epoch || version != routeVersion || credential() != token || !current()) return@post
                val resolved = result.getOrNull()
                if (resolved?.second == null) {
                    activityUnavailable(if (result.isFailure) "Could not open this notification. Refresh Games and try again."
                        else "This activity is no longer available. Showing your current Games.")
                } else {
                    if (state.mutating) return@post
                    val detail = session?.summary(resolved.second!!) ?: latest[route.gameId]?.takeIf {
                        GameSession.newerOrEqual(it.revision, resolved.second!!.revision) } ?: resolved.second!!
                    if (!sessionAvailable(route.gameId) || detail.lifecycle !in listOf(GameLifecycle.LOBBY, GameLifecycle.ACTIVE)) {
                        activityUnavailable(); return@post
                    }
                    open(route.gameId)
                    state = state.copy(detail = detail, activityTarget = resolved.first,
                        error = if (resolved.first.destination == ActivityDestination.CURRENT)
                            "This notification is no longer current. Showing the current game." else null)
                    reconcileActivityTarget(detail)
                }
            }
        }
    }
    fun reconcileActivityTarget(game: GameSummary) {
        val target = state.activityTarget ?: return
        val valid = when (target.destination) {
            ActivityDestination.INVITATION -> game.lifecycle == GameLifecycle.LOBBY && game.invitationState == InvitationState.PENDING && game.invitationId == target.invitationId
            ActivityDestination.GROUP -> game.lifecycle == GameLifecycle.ACTIVE && game.playId == target.playId
            else -> true
        }
        if (!valid) state = state.copy(activityTarget = ActivityTarget(game.id, ActivityDestination.CURRENT, playId = game.playId),
            error = "This notification is no longer current. Showing the current game.")
        else if (target.destination == ActivityDestination.GROUP) state = state.copy(error = null)
    }
    private fun sessionAvailable(id: String) = scope.sessionAvailable(id)
    private fun open(id: String) = scope.open(id)
    private fun refresh(background: Boolean) = scope.refresh(background)
    private fun leaveUnavailable() = scope.leaveUnavailable()
}
