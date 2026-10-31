package org.asyncttwiz.android

/** What GameSession's parts read from the session to fence asynchronous work: a callback started
 * under one connection epoch or account must not land after a reconnect, account switch or close. */
internal interface SessionScope {
    val closed: Boolean
    val epoch: Long
    val account: String?
    fun sameAccount(): Boolean
    fun post(action: () -> Unit)
    fun statusChanged()
}
