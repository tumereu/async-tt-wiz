@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.asyncttwiz.protocol.AccountIdentity
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.storage.AsyncTextStore
import org.asyncttwiz.storage.BrowserAccountData
import org.asyncttwiz.storage.BrowserCardDefinitionCache
import org.asyncttwiz.storage.BrowserSessions
import org.asyncttwiz.storage.CardDefinitions
import org.asyncttwiz.storage.MemoryTextStore
import org.asyncttwiz.storage.MirroredStore
import org.asyncttwiz.storage.ParsedCard
import org.asyncttwiz.storage.StoredRememberedSession
import org.asyncttwiz.storage.UnreadableStoredSession

/**
 * The app's storage in the browser (docs/specs/web-client.md §1 items 4 and 5, §11 step 3): the session, the accounts'
 * local data and the card definitions cache in IndexedDB, read whole into memory when the page starts ([open]) so the
 * app's synchronous storage interfaces read them at once; the card records and pictures ([catalogController],
 * [artworkService]). The web shell opens it before it installs the app's services. A browser without IndexedDB (some
 * private windows) keeps everything for the tab only.
 */
class BrowserStorage private constructor(
    /** The IndexedDB database, null without one. */
    internal val database: JsAny?,
    private val sessions: BrowserSessions,
    private val account: BrowserAccountData,
    private val definitions: BrowserCardDefinitionCache,
) {
    /** The signed-in session's store: [SessionStore] over IndexedDB. */
    fun sessionStore(): SessionStore = BrowserSessionStore(sessions)

    /** The accounts' data in this browser. */
    fun accountLocalData(): AppLocalData = BrowserAccountLocalData(account, definitions)

    companion object {
        const val DATABASE = "atw"
        private const val VERSION = 1
        internal const val SESSION = "session"
        internal const val ACCOUNT = "account"
        internal const val DEFINITIONS = "definitions"
        internal const val CATALOG = "catalog"

        /** Opens the browser's storage; its writes run in [scope] (the page's). */
        suspend fun open(scope: CoroutineScope): BrowserStorage {
            val database = try { idbOpen(DATABASE, VERSION, listOf(SESSION, ACCOUNT, DEFINITIONS, CATALOG).joinToString(",")).await<JsAny>() }
                catch (failure: Throwable) {
                    AppLog.warn("storage", "IndexedDB unavailable; this tab keeps its data in memory only", failure)
                    null
                }
            suspend fun mirrored(name: String): MirroredStore {
                val store: AsyncTextStore = database?.let { IndexedDbTextStore(it, name) } ?: MemoryTextStore()
                return MirroredStore.open(store, scope) { message, failure -> AppLog.warn("storage", "$name: $message", failure) }
            }
            val storage = BrowserStorage(database, BrowserSessions(mirrored(SESSION)), BrowserAccountData(mirrored(ACCOUNT)),
                BrowserCardDefinitionCache(mirrored(DEFINITIONS)))
            AppLog.info("storage", "Browser storage open (${if (database == null) "memory only" else "IndexedDB"})")
            return storage
        }
    }
}

/** One IndexedDB object store of text values. */
internal class IndexedDbTextStore(private val database: JsAny, private val store: String) : AsyncTextStore {
    override suspend fun entries(): Map<String, String> =
        Json.parseToJsonElement(idbEntriesJson(database, store).await<JsString>().toString()).jsonObject
            .mapValues { it.value.jsonPrimitive.content }

    override suspend fun write(changes: Map<String, String?>) {
        val json = JsonObject(changes.mapValues { (_, value) -> value?.let(::JsonPrimitive) ?: JsonNull })
        idbWriteJson(database, store, json.toString()).await<JsAny?>()
    }
}

/** The session token in the browser (§1 item 5): kept in IndexedDB as Android keeps it, not encrypted (the browser has no
 * key store of its own); remembered sessions for test builds' Switch account. */
internal class BrowserSessionStore(private val sessions: BrowserSessions) : SessionStore {
    override fun load(): StoredSession {
        val token = sessions.token() ?: return StoredSession.Empty
        return if (AccountApi.validToken(token)) StoredSession.Present(token) else {
            AppLog.warn("storage", "The kept session token isn't one; it can't be used")
            StoredSession.Unrecoverable
        }
    }

    override fun save(token: String) {
        require(AccountApi.validToken(token))
        sessions.saveToken(token)
    }

    override fun clear() = sessions.clearToken()

    override fun resetUnrecoverable() {
        val brokenRemembered = try { remembered(); false } catch (failure: UnreadableStoredSession) { true }
        sessions.clearToken()
        if (brokenRemembered) sessions.clearRemembered()
    }

    override fun remembered(): List<RememberedSession> = sessions.remembered().map {
        if (!AccountApi.validToken(it.token)) throw UnreadableStoredSession("A remembered session token isn't one")
        RememberedSession(it.token, RememberedAccount(AccountIdentity(it.accountId, it.username), it.email))
    }

    override fun remember(sessions: List<RememberedSession>) {
        sessions.forEach { require(AccountApi.validToken(it.token)) }
        this.sessions.remember(sessions.map {
            StoredRememberedSession(it.token, it.account.identity.accountId, it.account.identity.username, it.account.email)
        })
    }

    override fun permanentFailure(failure: Throwable): Boolean =
        generateSequence(failure) { it.cause?.takeUnless { cause -> cause === it } }.take(16).any { it is UnreadableStoredSession }
}

/** The accounts' data in the browser, as AndroidAccountLocalData on Android: the installation's account, its shell
 * destination, logout clearing (§9.5), and the card definitions Automatum reads, parsed in the browser and cached. */
internal class BrowserAccountLocalData(private val account: BrowserAccountData,
    private val cache: BrowserCardDefinitionCache) : AppLocalData {
    private var closed = false
    private var definitions: CardDefinitions? = null

    override fun activate(accountId: String): ShellDestination {
        val saved = account.activate(accountId, SHELL_DESTINATION)
        return ShellDestination.entries.firstOrNull { saved == "\"${it.name}\"" } ?: ShellDestination.MENU
    }

    override fun destination(accountId: String, destination: ShellDestination) {
        if (!closed) account.saveForAccount(accountId, SHELL_DESTINATION, "\"${destination.name}\"")
    }

    override fun resumeLogout(store: SessionStore) {
        // A tab closed during local cleanup cannot turn a deliberate Logout into reauthentication.
        if (account.logoutPending()) {
            AppLog.info("storage", "Finishing a logout begun before the page closed")
            account.finishLogout { store.clear() }
        }
    }

    override fun logout(store: SessionStore) = account.logout { store.clear() }

    override fun cardDefinitions(submitted: Collection<SubmittedDefinition>): Map<String, ParsedCard> =
        if (closed) emptyMap() else (definitions ?: CardDefinitions(cache).also { definitions = it }).forDefinitions(submitted)

    override fun close() { closed = true; definitions = null }

    private companion object { const val SHELL_DESTINATION = "shell-destination" }
}
