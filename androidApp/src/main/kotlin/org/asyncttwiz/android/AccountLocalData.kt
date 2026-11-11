package org.asyncttwiz.android

import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.storage.CardDefinitions
import org.asyncttwiz.storage.ClientStorage
import org.asyncttwiz.storage.ParsedCard
import org.asyncttwiz.storage.StorageOpenResult
import org.asyncttwiz.storage.StoredPayload

enum class ShellDestination(val title: String) { MENU("Menu"), GAMES("Games"), PODS("Pods"), DECKS("Manage decks"), CONTACTS("Contacts"), SETTINGS("Settings"), STATS("Stats") }

/** Blocking local operations. Called only on the auth worker, never from a Composable. */
interface AccountLocalData {
    fun resumeLogout(store: SessionStore) = Unit
    fun activate(accountId: String): ShellDestination = ShellDestination.MENU
    fun destination(accountId: String, destination: ShellDestination) = Unit
    fun logout(store: SessionStore) { store.clear() }
}

/** The auth lifecycle and session factory share one existing driver. All calls are blocking worker
 * calls. Injectable opening permits real SQLite/session lifecycle tests without Android UI stubs. */
class AccountStorageHandle(private val open: () -> StorageOpenResult) : AutoCloseable {
    private var handle: ClientStorage? = null
    private var closed = false
    @Synchronized fun <T> storage(block: (ClientStorage) -> T): T {
        check(!closed)
        val storage = handle ?: open().let { result ->
            check(result is StorageOpenResult.Opened) { "Private storage unavailable" }
            result.storage.also { handle = it }
        }
        return block(storage)
    }

    @Synchronized fun destination(accountId: String, destination: ShellDestination) {
        if (closed) return
        storage {
            if (it.personal("installation", "account-owner") == StoredPayload(1, accountId))
                it.savePersonal("account:$accountId", "shell-destination", StoredPayload(1, "\"${destination.name}\""))
        }
    }

    private var cardDefinitions: CardDefinitions? = null

    /** The game's card definitions for Automatum (I-D2), parsed or read from the device cache. Blocking. */
    @Synchronized fun cardDefinitions(submitted: Collection<SubmittedDefinition>): Map<String, ParsedCard> =
        if (closed) emptyMap() else storage { s -> (cardDefinitions ?: CardDefinitions(s).also { cardDefinitions = it }).forDefinitions(submitted) }

    @Synchronized override fun close() { closed = true; handle?.close(); handle = null; cardDefinitions = null }
}
