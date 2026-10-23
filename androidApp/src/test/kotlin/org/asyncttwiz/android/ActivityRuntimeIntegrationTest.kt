package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ActivityRuntimeIntegrationTest {
    private class Store : ActivityWindowStore {
        var at: String? = "0"
        val marks = mutableMapOf<String, ActivityWatermark>()
        var waiting = emptyMap<String, PendingActivity>()
        var writable = true
        override fun cursor() = at
        override fun watermark(key: String) = marks[key]
        override fun pending() = waiting
        override fun save(cursor: String, marks: Map<String, ActivityWatermark>) = saveWindow(cursor, marks, waiting)
        override fun saveWindow(cursor: String, marks: Map<String, ActivityWatermark>, pending: Map<String, PendingActivity>): Boolean {
            if (!writable) return false
            at = cursor; this.marks.putAll(marks); waiting = pending.toMap(); return true
        }
    }
    private fun id() = UUID.randomUUID().toString()
    private val account = AccountIdentity(id(), "Player")
    private val opponent = AccountIdentity(id(), "Opponent")
    private val owner = ActivityOwner(account.accountId, id())
    private val game = GameSummary(id(), "Table", "2026-10-23T00:00:00Z", opponent, account,
        id(), id(), InvitationState.ACCEPTED, lifecycle = GameLifecycle.ACTIVE, playId = id())
    private fun hint(n: Int, identity: String = "group", gameId: String = game.id, kind: ActivityKind = ActivityKind.GAME) =
        ActivityHint(n.toString(), kind, gameId, identity, opponent.accountId)
    private fun page(vararg hints: ActivityHint, reset: Boolean = false) =
        ActivityPage(hints.toList(), hints.lastOrNull()?.activityId ?: "0", "0", reset, false)

    @Test fun pollingWindowsSurviveRestartAndPushOverlapAndExtendSilentlyUnderOneGameId() {
        val store = Store(); val notices = mutableListOf<ActivityNotice>(); var time = 0L
        fun engine() = ActivityDelivery(store, { time }) { notice ->
            assertEquals(notice.hint.activityId.toLong(), store.marks[notice.key]?.activity)
            assertFalse(notice.key in store.waiting)
            notices += notice
        }.also { it.bind(owner) }
        val first = engine()
        assertTrue(first.apply(owner, page(hint(1))) { true })
        assertEquals("1", store.at); assertTrue(notices.isEmpty()); assertEquals(2_000L, first.nextDue(owner))
        time = 1_000; first.apply(owner, page(hint(2))) { true }
        assertEquals(3_000L, first.nextDue(owner))
        val restarted = engine()
        time = 2_999; restarted.apply(owner, page(hint(2))) { true } // duplicate push-fetch cannot restart quiet window
        assertTrue(notices.isEmpty()); assertEquals(3_000L, restarted.nextDue(owner))
        time = 3_000; restarted.drain(owner) { true }
        assertEquals(1, notices.size); assertFalse(notices.last().silent)
        time = 4_000; restarted.apply(owner, page(hint(3))) { true }
        time = 6_000; restarted.drain(owner) { true }; assertTrue(notices.last().silent)
        restarted.apply(owner, page(hint(4, "next"))) { true }
        time = 8_000; restarted.drain(owner) { true }
        assertFalse(notices.last().silent); assertEquals(1, notices.map { it.key }.distinct().size)
        restarted.apply(owner, page(hint(4, "next"))) { true }
        assertEquals(3, notices.size)
    }

    @Test fun continuousExtensionsHaveExactTenSecondCapAndIndependentGamesInvitationsAndInstallations() {
        val store = Store(); val notices = mutableListOf<ActivityNotice>(); var time = 0L
        val delivery = ActivityDelivery(store, { time }, notices::add).also { it.bind(owner) }
        delivery.apply(owner, page(hint(1))) { true }
        for (second in 1..9) {
            time = second * 1_000L
            delivery.apply(owner, page(hint(second + 1))) { true }
            assertTrue(notices.isEmpty())
            assertEquals(minOf(time + 2_000, 10_000), delivery.nextDue(owner))
        }
        time = 9_999; delivery.drain(owner) { true }; assertTrue(notices.isEmpty())
        time = 10_000; delivery.drain(owner) { true }; assertEquals(1, notices.size)
        delivery.display(game.id, true)
        delivery.apply(owner, page(hint(11, "review"), hint(12, "attempt", kind = ActivityKind.INVITATION),
            hint(13, "other", "other-game"))) { true }
        assertEquals(ActivityKind.INVITATION, notices.last().hint.kind)
        assertEquals(11L, store.marks["game:${game.id}"]?.activity)
        time = 12_000; delivery.drain(owner) { true }; assertEquals("other-game", notices.last().hint.gameId)
        // Another installation has its own windows and foreground choice, not shared read progress.
        val secondStore = Store(); val secondNotices = mutableListOf<ActivityNotice>()
        val second = ActivityDelivery(secondStore, { time }, secondNotices::add).also { it.bind(owner) }
        second.apply(owner, page(hint(11, "review"))) { true }; time += 2_000; second.drain(owner) { true }
        assertEquals(1, secondNotices.size)
        delivery.display(game.id, false)
        delivery.apply(owner, page(hint(14, "background"))) { true }; time += 2_000; delivery.drain(owner) { true }
        assertEquals("background", notices.last().hint.identity)
    }

    @Test fun pendingSuppressionResetDeniedControlsAndAccountFencesPrecedeStorageAndDisplay() {
        val store = Store(); val notices = mutableListOf<ActivityNotice>(); var time = 0L
        val delivery = ActivityDelivery(store, { time }, notices::add).also { it.bind(owner) }
        delivery.apply(owner, page(hint(1))) { true }
        delivery.display(game.id, true); delivery.drain(owner) { true }
        assertTrue(store.waiting.isEmpty()); assertEquals(1L, store.marks["game:${game.id}"]?.activity)
        delivery.display(null, false); delivery.apply(owner, page(hint(2))) { true }
        time = 2_000; delivery.drain(owner) { false }; assertTrue(notices.isEmpty())
        delivery.apply(owner, page(hint(3))) { true }
        delivery.apply(owner, page(hint(3), reset = true)) { true }
        time += 10_000; delivery.drain(owner) { true }; assertTrue(notices.isEmpty())
        delivery.apply(owner, page(hint(4))) { true }; val pending = store.waiting
        delivery.bind(owner.copy(binding = id()))
        assertFalse(delivery.drain(owner) { true }); assertEquals(pending, store.waiting)
        assertFalse(delivery.apply(owner, page(hint(5))) { true }); assertEquals("4", store.at)
        delivery.bind(owner); store.writable = false; time += 2_000
        assertFalse(delivery.drain(owner) { true }); assertTrue(notices.isEmpty())
    }

    @Test fun actualWorkerRefreshDriverUsesAuthorizedHttpCursorAndFencesLatePages() = runBlocking {
        val store = Store(); val notices = mutableListOf<ActivityNotice>(); var time = 0L
        val delivery = ActivityDelivery(store, { time }, notices::add).also { it.bind(owner) }
        var requests = 0; var fence = false
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals("Bearer ${"A".repeat(43)}", request.headers["Authorization"])
            val body = when (request.url.encodedPath) {
                "/activity/installations" -> ActivityWire.json.encodeToString(ActivityRegistration(id()))
                "/activity" -> {
                    assertEquals(store.at, request.url.parameters["after"])
                    if (fence) delivery.bind(null)
                    ActivityWire.json.encodeToString(page(hint(requests)))
                }
                else -> error("Unexpected worker route")
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        ActivityApi("https://example.test", client).use { api ->
            val refresh = ActivityRefresh({ delivery.current(owner) }, { delivery.cursor(owner) }, { delivery.apply(owner, it) { true } })
            val register: suspend () -> Unit = { api.register("A".repeat(43), RegisterInstallation(id(), owner.binding)); Unit }
            assertFalse(refresh.run(register) { api.page("A".repeat(43), it) })
            assertEquals(2, requests); assertTrue(notices.isEmpty()); assertEquals("2", store.at)
            fence = true
            assertFalse(refresh.run(register) { api.page("A".repeat(43), it) })
            assertEquals("2", store.at); assertTrue(notices.isEmpty())
        }
    }

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
    private inner class Api : Lobbies {
        val commands = mutableListOf<String>()
        var head = game
        override suspend fun contacts(token: String, after: String?) = ContactList(emptyList())
        override suspend fun games(token: String, invitations: Boolean, after: String?) = GameList(if (invitations) emptyList() else listOf(head))
        override suspend fun head(token: String, id: String) = head
        override suspend fun search(token: String, query: String) = UserSearch(emptyList())
        override suspend fun add(token: String, input: AddContact): AccountIdentity { commands += "add"; return account }
        override suspend fun create(token: String, input: CreateInvitation): GameSummary { commands += "create"; return head }
        override fun close() = Unit
    }

    @Test fun normalLobbyColdWarmRouteUsesExactAuthorizedBoundaryAndNoReplacementCommands() {
        val api = Api(); val worker = Worker(); var requested: ActivityRoute? = null; var current = true
        val group = id(); val boundary = id()
        var target = ActivityTarget(game.id, ActivityDestination.GROUP, playId = game.playId, groupId = group, boundaryNodeId = boundary)
        val lobby = LobbyController(api, { "A".repeat(43) }, worker, { it() }, accountId = { account.accountId },
            activityTarget = { _, route -> requested = route; target })
        val route = ActivityRoute(account.accountId, owner.binding, ActivityKind.GAME, game.id, group)
        try {
            lobby.openActivity(route) { current }; assertNull(lobby.state.selected); worker.drain()
            assertEquals(route, requested); assertEquals(game.id, lobby.state.selected)
            assertEquals(boundary, lobby.state.activityTarget?.boundaryNodeId)
            assertNull(lobby.state.error)
            assertNull(lobby.state.play) // Target authorization alone is not a fabricated historical board.
            assertEquals(game.id, ActivityDisplay.game(true, lobby.state.selected, lobby.state.detail))
            assertNull(ActivityDisplay.game(false, lobby.state.selected, lobby.state.detail))
            target = ActivityTarget(game.id, ActivityDestination.UNAVAILABLE)
            lobby.openActivity(route) { current }; worker.drain()
            assertNull(lobby.state.selected); assertTrue(lobby.state.error!!.contains("no longer available"))
            target = ActivityTarget(game.id, ActivityDestination.INVITATION, invitationId = game.invitationId)
            api.head = game.copy(lifecycle = GameLifecycle.LOBBY, playId = null, invitationState = InvitationState.PENDING)
            lobby.openActivity(route.copy(kind = ActivityKind.INVITATION, identity = game.invitationId)) { current }; worker.drain()
            assertEquals(game.invitationId, lobby.state.activityTarget?.invitationId)
            // A replacement invitation returned by the authorized head is NOT the captured attempt.
            api.head = api.head.copy(invitationId = id())
            lobby.openActivity(route.copy(kind = ActivityKind.INVITATION, identity = game.invitationId)) { current }; worker.drain()
            assertEquals(ActivityDestination.CURRENT, lobby.state.activityTarget?.destination)
            lobby.openActivity(route) { current }; current = false; worker.drain()
            assertTrue(api.commands.isEmpty())
        } finally { lobby.close(); worker.drain() }
    }

    @Test fun intentParserBindsAllOpaqueFieldsAndRejectsMalformedTargets() {
        val identity = id()
        val route = ActivityRoute.parse(account.accountId, owner.binding, "GAME", game.id, identity)!!
        assertEquals(identity, route.identity); assertEquals(owner.binding, route.binding)
        assertNull(ActivityRoute.parse(account.accountId, owner.binding, "GAME", game.id, "covered-card-name"))
        assertNull(ActivityRoute.parse(account.accountId, owner.binding, "CHAT", game.id, identity))
    }
    @Test fun typedActivityTargetAndDelayedRouteCannotRestoreCancelledOrDeletedChatAndDialogs() {
        for (terminal in listOf(GameLifecycle.CANCELLED, GameLifecycle.DELETED)) {
            val data = GameplayFixture(); val wire = CancellationFixture.Wire(); val worker = Worker()
            val posts = ArrayDeque<() -> Unit>()
            val live = if (terminal == GameLifecycle.DELETED) data.game else data.game.copy(
                lifecycle = GameLifecycle.LOBBY, playId = null, invitationState = InvitationState.PENDING)
            val api = object : Lobbies {
                override suspend fun contacts(token: String, after: String?) = ContactList(emptyList())
                override suspend fun games(token: String, invitations: Boolean, after: String?) = GameList(if (invitations) emptyList() else listOf(live))
                override suspend fun head(token: String, id: String) = live
                override suspend fun search(token: String, query: String) = UserSearch(emptyList())
                override suspend fun add(token: String, input: AddContact) = live.recipient
                override suspend fun create(token: String, input: CreateInvitation) = live
                override fun close() = Unit
            }
            val target = if (terminal == GameLifecycle.DELETED) ActivityTarget(live.id, ActivityDestination.GROUP,
                playId = live.playId, groupId = id(), boundaryNodeId = data.root.id)
                else ActivityTarget(live.id, ActivityDestination.INVITATION, invitationId = live.invitationId)
            val lobby = LobbyController(api, { "token" }, worker, { posts.add(it) }, gameConnection = wire,
                accountId = { data.actor }, activityTarget = { _, _ -> target })
            fun drain() { while (worker.queue.isNotEmpty() || posts.isNotEmpty()) {
                worker.drain(); while (posts.isNotEmpty()) posts.removeFirst()()
            } }
            val route = ActivityRoute(data.actor, id(), if (terminal == GameLifecycle.DELETED) ActivityKind.GAME else ActivityKind.INVITATION,
                live.id, target.groupId ?: target.invitationId!!)
            try {
                lobby.open(live.id); drain(); wire.listener.opened(); drain()
                wire.listener.message(GameWelcome()); drain()
                val stream = wire.frames.filterIsInstance<OpenGameStream>().single()
                wire.listener.message(GameStreamReady(live.id, stream.streamId, "1")); drain()
                wire.listener.message(GameHead(live.id, live.revision, live,
                    preparation = if (live.playId == null) PreparationRules.empty(live) else null,
                    play = if (live.playId != null) data.initial else null)); drain()
                lobby.openActivity(route) { true }; drain()
                assertEquals(target, lobby.state.activityTarget)
                if (terminal == GameLifecycle.DELETED) { lobby.openChat(); lobby.requestGameDeletion(live) }
                else lobby.requestCancelSetup(live)
                lobby.openActivity(route) { true }; worker.drain() // Completed old HTTP callback remains queued.
                wire.listener.message(GameHead(live.id, "9", live.copy(lifecycle = terminal, revision = "9")))
                posts.removeLast()(); drain()
                assertNull(lobby.state.selected); assertNull(lobby.state.activityTarget)
                assertNull(lobby.state.deletion); assertNull(lobby.setupCancellation.confirmation)
                assertFalse(lobby.state.chatOpen); assertEquals(ChatPresentation(), lobby.state.chat)
                assertNull(lobby.state.play); assertNull(lobby.state.acceptedPlay)
                assertTrue(wire.frames.filterIsInstance<GameCommand>().isEmpty())
            } finally { lobby.close(); drain() }
        }
    }
}
