package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.net.SocketException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * A request that fails without an answer is "network" to the screens, and the failure keeps what actually happened as its
 * cause, so a test or a log that shows the failure shows that too (two e2e setups failed with a bare "Pod request failed:
 * network", their real exception lost; it was the connection closed before any answer).
 */
class NetworkFailureCauseTest {
    private val token = "A".repeat(43)
    private val closed = SocketException("Unexpected end of file from server")
    private fun client() = HttpClient(MockEngine { throw closed })

    @Test fun eachServiceKeepsTheCauseOfItsNetworkFailure() = runBlocking {
        val pod = assertFailsWith<PodFailure> { PodApi("https://api.example", client()).use { it.list(token) } }
        assertEquals("network", pod.code); assertClosed(pod.cause)
        val lobby = assertFailsWith<LobbyFailure> { LobbyApi("https://api.example", client()).use { it.contacts(token) } }
        assertEquals("network", lobby.code); assertClosed(lobby.cause)
        val deck = assertFailsWith<DeckFailure> { DeckApi("https://api.example", client()).use { it.list(token, null) } }
        assertEquals("network", deck.code); assertClosed(deck.cause)
        val account = assertFailsWith<AccountFailure> {
            AccountApi("https://api.example", client()).use { it.login("alice@example.com", "a password 1") }
        }
        assertEquals("network", account.code); assertClosed(account.cause)
    }

    // The coroutine machinery may hand over a copy of the exception: the same kind and message
    private fun assertClosed(cause: Throwable?) = assertEquals(closed.message, assertIs<SocketException>(cause).message)
}
