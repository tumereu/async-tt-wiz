package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** What LobbyController's parts share with it: the one shown [LobbyState], the fences that drop
 * asynchronous work after Logout or close, the newest accepted game summaries and the navigation
 * steps more than one part takes. */
internal interface LobbyScope {
    var state: LobbyState
    val closed: Boolean
    val epoch: Long
    /** The newest accepted summary per game. */
    val latest: Map<String, GameSummary>
    fun sessionAvailable(id: String): Boolean
    fun open(id: String)
    fun refresh(background: Boolean = false)
    fun leaveUnavailable()
    fun connectionState()
}
