package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.*

class PodApiTest {
    private val id = UUID.randomUUID().toString()
    private val request = UUID.randomUUID().toString()
    private val token = "A".repeat(43)
    private val account = AccountIdentity(id, "Alice")
    private val detail = PodDetail(id, "Pod", PodPhase.SETUP, account, DraftSettings(), PodSource("Cube", 540),
        DraftSupply(45, 540), listOf(PodMember(account)), emptyList(), updatedAt = "2026-10-26T00:00:00Z")

    @Test fun allRoutesSendAuthenticatedOriginalPayloadAndCorrectVerb() = runBlocking {
        data class Expected(val path: String, val method: HttpMethod, val body: String? = null, val ack: Boolean = false)
        val expected = ArrayDeque<Expected>()
        val api = PodApi("https://pods.example", HttpClient(MockEngine { call ->
            val next = expected.removeFirst()
            assertEquals(next.path, call.url.encodedPath); assertEquals(next.method, call.method)
            assertEquals("Bearer $token", call.headers["Authorization"])
            if (next.body != null) assertEquals(next.body, (call.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
            respond(if (next.ack) DeckJson.format.encodeToString(PodAcknowledged(id))
                else if (next.path == "/v1/pods" && next.method == HttpMethod.Get) DeckJson.format.encodeToString(PodList(emptyList(), emptyList()))
                else DeckJson.format.encodeToString(detail), headers = headersOf("Content-Type", "application/json"))
        }))
        fun expect(action: String, method: HttpMethod, body: String? = null, ack: Boolean = false) {
            expected.add(Expected("/v1/pods$action", method, body, ack))
        }
        api.use {
            expect("", HttpMethod.Get); api.list(token)
            expect("/$id", HttpMethod.Get); assertEquals(detail, api.detail(token, id))
            val create = CreatePod(request, "Pod", id)
            expect("", HttpMethod.Post, DeckJson.format.encodeToString(create)); api.create(token, create)
            val update = UpdatePod(request, settings = DraftSettings.glimpse())
            expect("/$id", HttpMethod.Put, DeckJson.format.encodeToString(update)); api.update(token, id, update)
            val invite = InviteToPod(request, id)
            expect("/$id/invitations", HttpMethod.Post, DeckJson.format.encodeToString(invite)); api.invite(token, id, invite)
            val input = PodRequest(request)
            expect("/$id/accept", HttpMethod.Post, DeckJson.format.encodeToString(input)); api.accept(token, id, input)
            expect("/$id/decline", HttpMethod.Post, DeckJson.format.encodeToString(input), true); api.decline(token, id, input)
            expect("/$id/start", HttpMethod.Post, DeckJson.format.encodeToString(input)); api.start(token, id, input)
            val pick = PickPod(request, id, "pack", listOf("keep"), listOf("burn"))
            expect("/$id/pick", HttpMethod.Post, DeckJson.format.encodeToString(pick)); api.pick(token, id, pick)
            val grid = SelectPodGrid(request, id, "grid-0", GridLineType.COLUMN, 2)
            expect("/$id/grid/select", HttpMethod.Post, DeckJson.format.encodeToString(grid)); api.gridSelect(token, id, grid)
            val move = MovePodCopy(request, id, "copy", PodDeckCategory.SIDE)
            expect("/$id/build/move", HttpMethod.Put, DeckJson.format.encodeToString(move)); api.move(token, id, move)
            val basics = SetPodBasics(request, id, PodDeckBasics(plains = 17))
            expect("/$id/build/basics", HttpMethod.Put, DeckJson.format.encodeToString(basics)); api.basics(token, id, basics)
            val submit = PodDraftRequest(request, id)
            expect("/$id/submit", HttpMethod.Post, DeckJson.format.encodeToString(submit)); api.submit(token, id, submit)
            expect("/$id/cancel", HttpMethod.Post, DeckJson.format.encodeToString(submit)); api.cancel(token, id, submit)
            expect("/$id/leave", HttpMethod.Post, DeckJson.format.encodeToString(input), true); api.leave(token, id, input)
            expect("/$id", HttpMethod.Delete, DeckJson.format.encodeToString(input), true); api.destroy(token, id, input)
        }
        assertTrue(expected.isEmpty())
    }

    @Test fun invalidIdNeverMakesNetworkCallAndUnauthorizedUsesExistingAuthRecovery() = runBlocking {
        var calls = 0; var rejected: String? = null
        PodApi("https://pods.example", HttpClient(MockEngine {
            calls++; respond(DeckJson.format.encodeToString(PodError("authentication_required")), HttpStatusCode.Unauthorized)
        }), { rejected = it }).use { api ->
            assertEquals("invalid_request", assertFailsWith<PodFailure> { api.detail(token, "../game") }.code)
            assertEquals(0, calls)
            assertEquals("authentication_required", assertFailsWith<PodFailure> { api.list(token) }.code)
            assertEquals(token, rejected)
        }
    }
}
