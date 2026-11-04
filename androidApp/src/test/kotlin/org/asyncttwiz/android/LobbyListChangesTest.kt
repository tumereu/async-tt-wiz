package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

/**
 * Switch-review #11 (owner, 2026-11-04): after the first read the games list is read as "changed since" the token the
 * last read answered: no contacts or head, only the changed summaries and the ids that left each list, applied to the
 * rows shown; back from the background the whole lists are read again.
 */
class LobbyListChangesTest {
    private class Worker : AbstractExecutorService() {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.add(command) }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
    }
    private fun id() = UUID.randomUUID().toString()
    private val owner = AccountIdentity(id(), "Inviter")
    private val recipient = AccountIdentity(id(), "Recipient")
    private fun game(name: String, revision: String = "1") = GameSummary(id(), name, "2026-10-22T00:00:00Z", owner, recipient, id(), id(),
        InvitationState.ACCEPTED, GameLifecycle.LOBBY, revision)

    private inner class Api : Lobbies {
        val reads = mutableListOf<String>()
        var games = listOf(game("First"), game("Second"), game("Third"))
        var changed = emptyList<GameSummary>()
        var removed = emptyList<String>()
        override suspend fun search(token: String, query: String) = UserSearch(emptyList())
        override suspend fun contacts(token: String, after: String?): ContactList { reads += "contacts"; return ContactList(listOf(recipient)) }
        override suspend fun games(token: String, invitations: Boolean, after: String?): GameList {
            reads += if (invitations) "invitations" else "games"
            return GameList(if (invitations) emptyList() else games, token = "10")
        }
        override suspend fun gamesSince(token: String, invitations: Boolean, since: String, after: String?): GameList {
            reads += (if (invitations) "invitations" else "games") + " since $since"
            return if (invitations) GameList(emptyList(), token = "11") else GameList(changed, token = "11", removed = removed)
        }
        override suspend fun head(token: String, id: String): GameSummary { reads += "head"; return games.first { it.id == id } }
        override suspend fun add(token: String, input: AddContact) = recipient
        override suspend fun create(token: String, input: CreateInvitation) = error("unused")
        override fun close() = Unit
    }

    @Test fun afterTheFirstReadOnlyChangesAreReadAndApplied() {
        val worker = Worker(); val api = Api()
        val controller = LobbyController(api, { "token" }, worker, { it() })
        try {
            controller.refresh(); worker.drain()
            assertEquals(listOf("contacts", "games", "invitations"), api.reads)
            assertEquals(listOf("First", "Second", "Third"), controller.state.games.map { it.gameName })
            // The second game changed, the third left the list, a fourth is new
            val second = api.games[1].copy(gameName = "Second, changed", revision = "2")
            val fourth = game("Fourth")
            api.changed = listOf(second, fourth); api.removed = listOf(api.games[2].id)
            api.reads.clear()
            controller.refresh(); worker.drain()
            assertEquals(listOf("games since 10", "invitations since 10"), api.reads, "no contacts, no whole lists")
            assertEquals(listOf("First", "Second, changed", "Fourth"), controller.state.games.map { it.gameName })
            // The next read is since the token the changes answered
            api.changed = emptyList(); api.removed = emptyList(); api.reads.clear()
            controller.refresh(); worker.drain()
            assertEquals(listOf("games since 11", "invitations since 11"), api.reads)
            assertEquals(listOf("First", "Second, changed", "Fourth"), controller.state.games.map { it.gameName })
            // Back from the background: the whole lists again
            controller.background(); controller.foreground(); worker.drain()
            assertTrue("contacts" in api.reads && "games" in api.reads, api.reads.toString())
        } finally { controller.close(); worker.drain() }
    }

    @Test fun anOpenGameIsNotReadAgainByTheChanges() {
        val worker = Worker(); val api = Api()
        val controller = LobbyController(api, { "token" }, worker, { it() })
        try {
            controller.refresh(); worker.drain()
            controller.open(api.games[0].id); worker.drain()
            assertNotNull(controller.state.detail)
            api.reads.clear()
            controller.refresh(); worker.drain()
            assertEquals(listOf("games since 11", "invitations since 11"), api.reads, "the open game's head isn't read again")
            assertEquals(api.games[0].id, controller.state.detail?.id)
        } finally { controller.close(); worker.drain() }
    }
}
