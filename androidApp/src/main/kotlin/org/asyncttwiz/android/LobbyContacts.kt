package org.asyncttwiz.android

import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import java.util.UUID
import java.util.concurrent.ExecutorService

enum class ContactsTab { CONTACTS, SEARCH }

/** LobbyController's contacts: the contacts screen's tab and filter, username search, and adding and
 * removing contacts over HTTP. Main thread; requests run on [worker]. */
internal class LobbyContacts(private val scope: LobbyScope, private val api: Lobbies, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    /** Fences a list refresh in flight, so it cannot undo this change. */
    private val fenceRefresh: () -> Unit,
    /** Starts naming a new game with a just-added contact. */
    private val begin: (AccountIdentity) -> Unit) {
    private var state: LobbyState
        get() = scope.state
        set(value) { scope.state = value }
    private val closed get() = scope.closed
    private val epoch get() = scope.epoch
    private var searchVersion = 0L
    private val pendingAdds = mutableMapOf<String, AddContact>()
    fun clear() { searchVersion++; pendingAdds.clear() }
    fun query(value: String) { searchVersion++; state = state.copy(query = value, results = emptyList(), searching = false, searched = false, searchError = null) }
    fun search() {
        if (closed) return
        val token = credential() ?: return
        if (state.query.isBlank()) { state = state.copy(searchError = "Enter a username."); return }
        val query = state.query; val generation = epoch; val version = ++searchVersion
        state = state.copy(searching = true, searchError = null)
        worker.execute {
            if (generation != epoch) return@execute
            val result = runCatching { runBlocking { serviceRetry(current = { generation == epoch && version == searchVersion }) { api.search(token, query) } } }
            post { if (!closed && generation == epoch && version == searchVersion) state = state.copy(searching = false,
                searched = true, results = result.getOrNull()?.users ?: state.results, searchError = result.exceptionOrNull()?.let(::lobbyError)) }
        }
    }
    fun contacts(chooseOpponent: Boolean) {
        state = state.copy(choosingOpponent = chooseOpponent, contactsTab = ContactsTab.CONTACTS, contactFilter = "")
    }
    fun contactsTab(tab: ContactsTab) { state = state.copy(contactsTab = tab) }
    fun filterContacts(value: String) { state = state.copy(contactFilter = value) }
    fun requestRemoveContact(contact: AccountIdentity) { if (!state.mutating) state = state.copy(removingContact = contact) }
    fun dismissRemoveContact() { state = state.copy(removingContact = null) }
    fun removeContact() {
        val contact = state.removingContact ?: return
        val token = credential() ?: return
        if (closed || state.mutating) return
        val generation = epoch
        // Fence a list read started before this removal so it cannot resurrect the contact.
        fenceRefresh()
        state = state.copy(mutating = true, busy = false, removingContact = null, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            val result = runCatching { runBlocking { serviceRetry(current = { generation == epoch }) { api.remove(token, contact.accountId) } } }
            post { if (!closed && generation == epoch) {
                state = state.copy(mutating = false, contacts = if (result.isSuccess) state.contacts.filterNot { it.accountId == contact.accountId } else state.contacts,
                    error = result.exceptionOrNull()?.let(::lobbyError))
            } }
        }
    }
    fun add(contact: AccountIdentity, inviteAfter: Boolean = false) {
        if (closed || state.mutating || state.contacts.any { it.accountId == contact.accountId }) return
        val token = credential() ?: return
        val input = pendingAdds.getOrPut(contact.accountId) { AddContact(UUID.randomUUID().toString(), contact.accountId) }
        val generation = epoch
        fenceRefresh()
        state = state.copy(mutating = true, busy = false, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            val result = runCatching { runBlocking { serviceRetry(current = { generation == epoch }) { api.add(token, input) } } }
            post { if (!closed && generation == epoch) {
                val saved = result.getOrNull()
                if (saved != null) pendingAdds.remove(contact.accountId)
                state = state.copy(mutating = false, contacts = if (saved == null) state.contacts else
                    (state.contacts.filterNot { it.accountId == saved.accountId } + saved), error = result.exceptionOrNull()?.let(::lobbyError))
                if (saved != null && inviteAfter) begin(saved)
            } }
        }
    }
}
