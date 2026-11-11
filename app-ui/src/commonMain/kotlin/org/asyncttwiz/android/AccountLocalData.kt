package org.asyncttwiz.android

enum class ShellDestination(val title: String) { MENU("Menu"), GAMES("Games"), PODS("Pods"), DECKS("Manage decks"), CONTACTS("Contacts"), SETTINGS("Settings"), STATS("Stats") }

/** Blocking local operations. Called only on the auth worker, never from a Composable. */
interface AccountLocalData {
    fun resumeLogout(store: SessionStore) = Unit
    fun activate(accountId: String): ShellDestination = ShellDestination.MENU
    fun destination(accountId: String, destination: ShellDestination) = Unit
    fun logout(store: SessionStore) { store.clear() }
}
