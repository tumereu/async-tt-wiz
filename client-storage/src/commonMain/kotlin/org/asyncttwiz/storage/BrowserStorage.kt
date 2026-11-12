package org.asyncttwiz.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

// The browser's personal storage (docs/specs/web-client.md §1 item 4, §11 step 3): the session, the accounts' local data
// and the card definitions cache, each one of the browser's asynchronous stores (IndexedDB; app-ui's wasm side adapts it),
// mirrored in memory so the app's synchronous storage interfaces (SessionStore, AppLocalData, CardDefinitionCache) read it
// at once. Writes go to memory at once and to the store in order, one batch at a time.

/** One of the browser's stores of text by key (an IndexedDB object store). Asynchronous. */
interface AsyncTextStore {
    /** Every key with its text. */
    suspend fun entries(): Map<String, String>

    /** Puts each key with text and deletes each key with null, all or none. */
    suspend fun write(changes: Map<String, String?>)
}

/** [AsyncTextStore] in memory: tests, and a browser without IndexedDB (private windows of some browsers), where nothing
 * outlives the tab. */
class MemoryTextStore(initial: Map<String, String> = emptyMap()) : AsyncTextStore {
    val values = LinkedHashMap(initial)
    /** Tests: each write's changes, in order. */
    val writes = mutableListOf<Map<String, String?>>()
    /** Tests: the next write fails with it. */
    var failNext: Exception? = null

    override suspend fun entries(): Map<String, String> = LinkedHashMap(values)

    override suspend fun write(changes: Map<String, String?>) {
        failNext?.let { failNext = null; throw it }
        writes += changes
        changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
    }
}

/**
 * An [AsyncTextStore] read whole once ([open]) and kept in memory: reads are immediate, writes change memory at once and
 * reach the store in the order made ([flush] waits for them). A write the store refuses is logged and stays in memory for
 * this tab only. Single-threaded, as the browser is.
 */
class MirroredStore private constructor(private val backing: AsyncTextStore, initial: Map<String, String>,
    scope: CoroutineScope, private val log: (String, Throwable) -> Unit) {
    private val values = HashMap(initial)
    private val pending = Channel<Map<String, String?>>(Channel.UNLIMITED)
    private var queued = 0L
    private val written = MutableStateFlow(0L)

    init {
        scope.launch {
            for (changes in pending) {
                try { backing.write(changes) } catch (failure: Exception) {
                    log("A browser storage write of ${changes.size} keys failed; kept for this tab only", failure)
                }
                written.value += 1
            }
        }
    }

    operator fun get(key: String): String? = values[key]

    val keys: Set<String> get() = values.keys.toSet()

    fun put(key: String, value: String) = write(mapOf(key to value))

    fun remove(key: String) = write(mapOf(key to null))

    /** Puts each key with text and deletes each key with null, in memory now and in the store as one write. */
    fun write(changes: Map<String, String?>) {
        val effective = changes.filter { (key, value) -> values[key] != value || (value == null && key in values) }
        if (effective.isEmpty()) return
        effective.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        queued++
        pending.trySend(effective)
    }

    /** Waits until every write made so far has reached the store (or failed). */
    suspend fun flush() {
        val target = queued
        written.first { it >= target }
    }

    companion object {
        /** [backing] read whole; its writes run in [scope]. */
        suspend fun open(backing: AsyncTextStore, scope: CoroutineScope,
            log: (String, Throwable) -> Unit = { _, _ -> }): MirroredStore = MirroredStore(backing, backing.entries(), scope, log)
    }
}

/** A remembered account's session (test builds' Switch account, LOB-009), as the browser keeps it. */
class StoredRememberedSession(val token: String, val accountId: String, val username: String, val email: String) {
    override fun toString() = "StoredRememberedSession[redacted]"
    override fun equals(other: Any?) = other is StoredRememberedSession && other.token == token &&
        other.accountId == accountId && other.username == username && other.email == email
    override fun hashCode() = token.hashCode()
}

/** What the browser's session store keeps can't be read (a broken or foreign value): never recoverable by retrying. */
class UnreadableStoredSession(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The signed-in session in the browser (docs/specs/web-client.md §1 item 5): the session token as Android keeps it, in its
 * own store, and the remembered sessions. No passwords. Tokens never enter logs or diagnostic strings.
 */
class BrowserSessions(private val store: MirroredStore) {
    /** The current session's token, null without one. */
    fun token(): String? = store[CURRENT]

    fun saveToken(token: String) = store.put(CURRENT, token)

    fun clearToken() = store.remove(CURRENT)

    /** The remembered sessions; throws [UnreadableStoredSession] when what is kept can't be read. */
    fun remembered(): List<StoredRememberedSession> {
        val text = store[REMEMBERED] ?: return emptyList()
        return try {
            val root = Json.parseToJsonElement(text).jsonObject
            require(root["version"]?.let { (it as JsonPrimitive).long } == 1L)
            root.getValue("sessions").jsonArray.map {
                val session = it.jsonObject
                fun field(key: String) = session.getValue(key).asText()
                StoredRememberedSession(field("token"), field("accountId"), field("username"), field("email"))
            }.also { require(it.size <= 1000) }
        } catch (failure: Exception) {
            throw UnreadableStoredSession("Remembered sessions unreadable", failure)
        }
    }

    fun remember(sessions: List<StoredRememberedSession>) {
        if (sessions.isEmpty()) { store.remove(REMEMBERED); return }
        require(sessions.size <= 1000)
        store.put(REMEMBERED, buildJsonObject {
            put("version", 1)
            put("sessions", buildJsonArray {
                sessions.forEach { session -> add(buildJsonObject {
                    put("token", session.token); put("accountId", session.accountId)
                    put("username", session.username); put("email", session.email)
                }) }
            })
        }.toString())
    }

    fun clearRemembered() = store.remove(REMEMBERED)

    private companion object {
        const val CURRENT = "current"
        const val REMEMBERED = "remembered"
    }
}

/**
 * The accounts' local data in the browser, as Android's ClientStorage keeps it for AndroidAccountLocalData: one installation
 * owner; switching accounts clears the former one's personal data (a logout too, §9.5); a logout begun survives the tab
 * closing midway ([logoutPending]), so it is finished on the next start. Personal values are JSON texts by scope and key.
 */
class BrowserAccountData(private val store: MirroredStore) {
    /** Makes [accountId] the installation's account (clearing another's personal data) and returns its saved
     * [shellKey] value. */
    fun activate(accountId: String, shellKey: String): String? {
        require(accountId.isNotEmpty())
        if (store[OWNER] != accountId) {
            clearPersonal()
            store.put(OWNER, accountId)
        }
        return store[personalKey("account:$accountId", shellKey)]
    }

    /** Saves [value] under [key] of [accountId] while it is the installation's account; otherwise nothing. */
    fun saveForAccount(accountId: String, key: String, value: String) {
        if (store[OWNER] == accountId) store.put(personalKey("account:$accountId", key), value)
    }

    fun logoutPending(): Boolean = store[LOGOUT_PENDING] != null

    /** A logout: marks it begun, runs [clearSession], clears the personal data, then unmarks it. */
    fun logout(clearSession: () -> Unit) {
        store.put(LOGOUT_PENDING, "1")
        finishLogout(clearSession)
    }

    /** Finishes a logout [logoutPending] says was begun. */
    fun finishLogout(clearSession: () -> Unit) {
        clearSession()
        clearPersonal()
        store.remove(LOGOUT_PENDING)
    }

    /** Every account's personal data, the installation owner too (Android's clearPrivateData). */
    fun clearPersonal() = store.write(store.keys.filter { it.startsWith(PERSONAL) || it == OWNER }.associateWith { null })

    private fun personalKey(scope: String, key: String) = "$PERSONAL$scope|$key"

    private companion object {
        const val PERSONAL = "personal:"
        const val OWNER = "personal:installation|account-owner"
        const val LOGOUT_PENDING = "logout-pending"
    }
}

/** The card definitions cache ([CardDefinitions]) in the browser: one value per parser version and definition digest. */
class BrowserCardDefinitionCache(private val store: MirroredStore) : CardDefinitionCache {
    override fun cachedCardDefinitions(parserVersion: Long, digests: Collection<String>): List<CachedCardDefinition> =
        digests.distinct().mapNotNull { digest ->
            store[key(parserVersion, digest)]?.let { text ->
                runCatching {
                    val value = Json.parseToJsonElement(text).jsonObject
                    CachedCardDefinition(digest, value["definition"]?.takeUnless { it is JsonNull }?.asText(),
                        (value.getValue("manual") as JsonPrimitive).long, value["problem"]?.takeUnless { it is JsonNull }?.asText())
                }.getOrNull()
            }
        }

    override fun cacheCardDefinitions(parserVersion: Long, entries: List<CachedCardDefinition>) = store.write(entries.associate {
        key(parserVersion, it.digest) to buildJsonObject {
            put("definition", it.definitionJson); put("manual", it.manualAbilities); put("problem", it.problem)
        }.toString()
    })

    override fun evictCardDefinitionsExcept(parserVersion: Long) {
        val kept = "$parserVersion|"
        store.write(store.keys.filterNot { it.startsWith(kept) }.associateWith { null })
    }

    private fun key(parserVersion: Long, digest: String) = "$parserVersion|$digest"
}
