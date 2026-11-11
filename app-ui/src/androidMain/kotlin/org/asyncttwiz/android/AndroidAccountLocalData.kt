package org.asyncttwiz.android

import android.content.Context
import android.util.AtomicFile
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.storage.AndroidClientStorage
import org.asyncttwiz.storage.ClientStorage
import org.asyncttwiz.storage.StoredPayload
import java.io.File

class AndroidAccountLocalData(context: Context) : AppLocalData {
    private val database = AndroidClientStorage.databaseFile(context)
    private val pending = AtomicFile(File(context.noBackupFilesDir, "account/logout.pending"))
    private val handle = AccountStorageHandle { AndroidClientStorage.open(database) }
    private fun <T> storage(block: (ClientStorage) -> T) = handle.storage(block)
    override fun cardDefinitions(submitted: Collection<SubmittedDefinition>) = handle.cardDefinitions(submitted)
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
        check(pending.baseFile.parentFile.let { it != null && (it.isDirectory || it.mkdirs()) })
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
