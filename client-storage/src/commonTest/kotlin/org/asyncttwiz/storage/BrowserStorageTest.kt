package org.asyncttwiz.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

// The browser's personal storage over an in-memory store (web client step 3a; IndexedDB itself is app-ui's thin wasm side).
class BrowserStorageTest {
    @Test
    fun mirroredStoreReadsAtOnceAndWritesInOrder() = runTest {
        val backing = MemoryTextStore(mapOf("kept" to "1"))
        val store = MirroredStore.open(backing, backgroundScope)
        assertEquals("1", store["kept"])
        store.put("a", "x")
        store.put("b", "y")
        store.remove("kept")
        // Memory first: the reads see the writes before the store does
        assertEquals("x", store["a"])
        assertNull(store["kept"])
        store.flush()
        assertEquals(mapOf("a" to "x", "b" to "y"), backing.values)
        assertEquals(listOf(mapOf("a" to "x"), mapOf("b" to "y"), mapOf<String, String?>("kept" to null)), backing.writes)
    }

    @Test
    fun mirroredStoreSkipsWritesThatChangeNothing() = runTest {
        val backing = MemoryTextStore(mapOf("a" to "x"))
        val store = MirroredStore.open(backing, backgroundScope)
        store.put("a", "x")
        store.remove("missing")
        store.flush()
        assertTrue(backing.writes.isEmpty())
    }

    @Test
    fun aRefusedWriteIsLoggedAndKeptForTheTab() = runTest {
        val backing = MemoryTextStore()
        val logged = mutableListOf<String>()
        val store = MirroredStore.open(backing, backgroundScope) { message, _ -> logged += message }
        backing.failNext = IllegalStateException("QuotaExceededError")
        store.put("a", "x")
        store.put("b", "y")
        store.flush()
        assertEquals("x", store["a"])
        assertEquals(mapOf("b" to "y"), backing.values)
        assertEquals(1, logged.size)
    }

    @Test
    fun sessionsKeepTheTokenAndRememberedAccounts() = runTest {
        val backing = MemoryTextStore()
        val store = MirroredStore.open(backing, backgroundScope)
        val sessions = BrowserSessions(store)
        assertNull(sessions.token())
        sessions.saveToken("token-a")
        val remembered = listOf(StoredRememberedSession("token-b", "account-b", "bea", "bea@example.invalid"))
        sessions.remember(remembered)
        store.flush()
        // A new page reads what the last one kept
        val reopened = BrowserSessions(MirroredStore.open(backing, backgroundScope))
        assertEquals("token-a", reopened.token())
        assertEquals(remembered, reopened.remembered())
        reopened.clearToken()
        reopened.remember(emptyList())
        assertNull(reopened.token())
        assertEquals(emptyList(), reopened.remembered())
        assertFalse(remembered.toString().contains("token-b"))
    }

    @Test
    fun unreadableRememberedSessionsSayItIsPermanent() = runTest {
        val sessions = BrowserSessions(MirroredStore.open(MemoryTextStore(mapOf("remembered" to "{not json")), backgroundScope))
        assertFailsWith<UnreadableStoredSession> { sessions.remembered() }
        val wrongVersion = BrowserSessions(MirroredStore.open(MemoryTextStore(mapOf("remembered" to """{"version":2,"sessions":[]}""")), backgroundScope))
        assertFailsWith<UnreadableStoredSession> { wrongVersion.remembered() }
    }

    @Test
    fun anotherAccountClearsTheFormerOnesPersonalData() = runTest {
        val account = BrowserAccountData(MirroredStore.open(MemoryTextStore(), backgroundScope))
        assertNull(account.activate("alice", "shell-destination"))
        account.saveForAccount("alice", "shell-destination", "\"GAMES\"")
        // Only the installation's account saves
        account.saveForAccount("bob", "shell-destination", "\"PODS\"")
        assertEquals("\"GAMES\"", account.activate("alice", "shell-destination"))
        assertNull(account.activate("bob", "shell-destination"))
        assertNull(account.activate("alice", "shell-destination"))
    }

    // LOB-007, docs/specs/web-client.md §9.5: a logout clears the account's data from the browser, even when the page
    // closes midway
    @Test
    fun aLogoutBegunIsFinishedOnTheNextStart() = runTest {
        val backing = MemoryTextStore()
        val store = MirroredStore.open(backing, backgroundScope)
        val account = BrowserAccountData(store)
        account.activate("alice", "shell-destination")
        account.saveForAccount("alice", "shell-destination", "\"GAMES\"")
        // The page closes after the logout is marked, before its session is cleared
        store.put("logout-pending", "1")
        store.flush()
        val next = BrowserAccountData(MirroredStore.open(backing, backgroundScope))
        assertTrue(next.logoutPending())
        var sessionCleared = false
        next.finishLogout { sessionCleared = true }
        assertTrue(sessionCleared)
        assertFalse(next.logoutPending())
        assertNull(next.activate("alice", "shell-destination"))
    }

    @Test
    fun aLogoutClearsSessionAndPersonalData() = runTest {
        val account = BrowserAccountData(MirroredStore.open(MemoryTextStore(), backgroundScope))
        account.activate("alice", "shell-destination")
        account.saveForAccount("alice", "shell-destination", "\"STATS\"")
        var cleared = 0
        account.logout { cleared++ }
        assertEquals(1, cleared)
        assertFalse(account.logoutPending())
        assertNull(account.activate("alice", "shell-destination"))
    }

    @Test
    fun cardDefinitionsAreCachedByParserVersion() = runTest {
        val backing = MemoryTextStore()
        val store = MirroredStore.open(backing, backgroundScope)
        val cache = BrowserCardDefinitionCache(store)
        cache.cacheCardDefinitions(7, listOf(CachedCardDefinition("d1", "{\"x\":1}", 2, null),
            CachedCardDefinition("d2", null, 0, "No type line")))
        cache.cacheCardDefinitions(6, listOf(CachedCardDefinition("d1", "{}", 0, null)))
        store.flush()
        val reopened = BrowserCardDefinitionCache(MirroredStore.open(backing, backgroundScope))
        assertEquals(listOf(CachedCardDefinition("d1", "{\"x\":1}", 2, null), CachedCardDefinition("d2", null, 0, "No type line")),
            reopened.cachedCardDefinitions(7, listOf("d1", "d2", "d3")))
        reopened.evictCardDefinitionsExcept(7)
        assertEquals(emptyList(), reopened.cachedCardDefinitions(6, listOf("d1")))
        assertEquals(2, reopened.cachedCardDefinitions(7, listOf("d1", "d2")).size)
    }
}
