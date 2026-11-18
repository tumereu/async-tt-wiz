@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

/**
 * The app's storage in the browser ([BrowserStorage]) on the browser's own IndexedDB: what one page keeps is what the next
 * page (a reload) opens with.
 */
class BrowserStorageTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest fun stop() = scope.cancel()

    private val token = "A".repeat(20) + "b_-" + "9".repeat(20)

    /** What the page's database holds: the session's and the accounts' stores. */
    private class Held(val session: Map<String, String>, val account: Map<String, String>)

    /** Opens the storage as a page does, runs [use], and closes its database as a closed page does once [kept] reads true of
     * what the database holds (the page's writes run in the background). */
    private suspend fun page(kept: (Held) -> Boolean = { true }, use: suspend (BrowserStorage, JsAny) -> Unit) {
        val storage = BrowserStorage.open(scope)
        val database = storage.database ?: fail("IndexedDB in headless Chrome")
        use(storage, database)
        suspend fun held() = Held(IndexedDbTextStore(database, BrowserStorage.SESSION).entries(),
            IndexedDbTextStore(database, BrowserStorage.ACCOUNT).entries())
        var tries = 0
        while (!kept(held())) {
            check(++tries < 300) { "the page's writes never reached IndexedDB: ${held().session.keys}, ${held().account.keys}" }
            withContext(Dispatchers.Default) { delay(10) }
        }
        closeDatabase(database)
    }

    // WEB-009: sign-in is kept in the browser, across reloads, until you log out; the account's last place in the app with it
    @Test fun theSessionIsKeptAcrossReloadsUntilLogout() = runTest {
        deleteDatabase(BrowserStorage.DATABASE).await<JsAny?>()
        page { storage, _ ->
            assertEquals(StoredSession.Empty, storage.sessionStore().load(), "a first visit is signed out")
        }
        page(kept = { token in it.session.values && "\"PODS\"" in it.account.values }) { storage, _ ->
            storage.sessionStore().save(token)
            storage.accountLocalData().activate("account-1")
            storage.accountLocalData().destination("account-1", ShellDestination.PODS)
        }
        page(kept = { token !in it.session.values && it.account.isEmpty() }) { storage, _ ->
            val loaded = storage.sessionStore().load()
            assertIs<StoredSession.Present>(loaded, "signed in after the reload")
            assertEquals(token, loaded.token)
            assertEquals(ShellDestination.PODS, storage.accountLocalData().activate("account-1"))
            storage.accountLocalData().logout(storage.sessionStore())
        }
        page { storage, _ ->
            assertEquals(StoredSession.Empty, storage.sessionStore().load(), "signed out after the logout and a reload")
        }
    }

    // WEB-009: a kept value that isn't a session token is never used as one: the page asks for sign-in again
    @Test fun aBrokenKeptTokenIsNotUsed() = runTest {
        deleteDatabase(BrowserStorage.DATABASE).await<JsAny?>()
        page(kept = { it.session.isNotEmpty() }) { _, database ->
            // BrowserSessions' key of the current session
            IndexedDbTextStore(database, BrowserStorage.SESSION).write(mapOf("current" to "not a token"))
        }
        page(kept = { it.session.isEmpty() }) { storage, _ ->
            assertEquals(StoredSession.Unrecoverable, storage.sessionStore().load())
            storage.sessionStore().resetUnrecoverable()
            assertEquals(StoredSession.Empty, storage.sessionStore().load())
        }
    }
}
