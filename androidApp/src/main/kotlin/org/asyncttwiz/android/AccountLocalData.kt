package org.asyncttwiz.android

import android.content.Context
import android.util.AtomicFile
import org.asyncttwiz.storage.AndroidClientStorage
import org.asyncttwiz.storage.ClientStorage
import org.asyncttwiz.storage.StorageOpenResult
import org.asyncttwiz.storage.StoredPayload
import org.asyncttwiz.storage.GameplayTreeCache
import org.asyncttwiz.storage.GameplayTreeStore
import java.io.File

enum class ShellDestination(val title: String) { MENU("Menu"), GAMES("Games"), DECKS("Manage decks"), CONTACTS("Contacts"), SETTINGS("Settings") }

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

    /** Called by GameSession's serial tree worker. Activation/Logout and the cache share this
     * existing database/driver and its transaction lock; binding never activates an account. */
    @Synchronized fun treeCache(accountId: String, gameId: String, playId: String): GameplayTreeStore? =
        if (closed) null else storage { GameplayTreeCache.bind(it, accountId, gameId, playId) }

    @Synchronized fun destination(accountId: String, destination: ShellDestination) {
        if (closed) return
        storage {
            if (it.personal("installation", "account-owner") == StoredPayload(1, accountId))
                it.savePersonal("account:$accountId", "shell-destination", StoredPayload(1, "\"${destination.name}\""))
        }
    }

    @Synchronized override fun close() { closed = true; handle?.close(); handle = null }
}

class AndroidAccountLocalData(context: Context) : AccountLocalData, AutoCloseable {
    private val database = AndroidClientStorage.databaseFile(context)
    private val pending = AtomicFile(File(context.noBackupFilesDir, "account/logout.pending"))
    private val handle = AccountStorageHandle { AndroidClientStorage.open(database) }
    private fun <T> storage(block: (ClientStorage) -> T) = handle.storage(block)
    fun treeCache(accountId: String, gameId: String, playId: String) = handle.treeCache(accountId, gameId, playId)
    override fun close() = handle.close()

    override fun activate(accountId: String): ShellDestination = storage {
        it.activateAccount(accountId)
        val saved = it.personal("account:$accountId", "shell-destination")
        ShellDestination.entries.firstOrNull { entry -> saved == StoredPayload(1, "\"${entry.name}\"") }
            ?: ShellDestination.MENU
    }

    override fun destination(accountId: String, destination: ShellDestination) = handle.destination(accountId, destination)

    override fun resumeLogout(store: SessionStore) {
        // A crash during local cleanup cannot turn a deliberate Logout into reauthentication.
        if (pending.baseFile.exists() || File(pending.baseFile.path + ".bak").exists()) finishLogout(store)
    }

    override fun logout(store: SessionStore) {
        check(pending.baseFile.parentFile!!.let { it.isDirectory || it.mkdirs() })
        val output = pending.startWrite()
        try { output.write(1); pending.finishWrite(output) }
        catch (failure: Exception) { pending.failWrite(output); throw failure }
        finishLogout(store)
    }

    private fun finishLogout(store: SessionStore) {
        store.clear()
        storage { it.clearPrivateData() }
        pending.delete()
        check(!pending.baseFile.exists())
    }
}
