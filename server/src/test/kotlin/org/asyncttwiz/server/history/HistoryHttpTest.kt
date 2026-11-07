package org.asyncttwiz.server.history

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import org.asyncttwiz.protocol.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory

/** Game history §3: the HTTP reads of the records: GET /v1/history, /v1/games/{id}/history and /v1/pods/{id}/matches. */
class HistoryHttpTest {
    // LOB-129, LOB-130, DRF-121 (game history §2, §3): each read answers its viewer's records; a pod's matches only to its members; sign-in needed
    @Test fun `history endpoints answer the viewer's records`() = HistoryWorld.run { w ->
        val (a, aliceToken) = w.session("HttpAlice"); val (b, bobToken) = w.session("HttpBob"); val (_, carolToken) = w.session("HttpCarol")
        val pod = UUID.randomUUID()
        w.execute("INSERT INTO pod (id, name, creator_id, settings, source_name, source_snapshot) VALUES (?, 'Http pod', ?, '{}'::jsonb, 'Cube', '[]'::jsonb)",
            pod, UUID.fromString(a.account.accountId))
        for (member in listOf(a, b)) w.execute("INSERT INTO pod_member (pod_id, account_id) VALUES (?, ?)", pod, UUID.fromString(member.account.accountId))
        val m = w.Match(a, b, "Http Spark")
        w.execute("UPDATE game SET pod_id = ?, pod_name = 'Http pod' WHERE id = ?", pod, m.id)
        m.start(); m.concede()
        val http = HistoryHttp(w.runtime)
        val server = TomcatServletWebServerFactory(0).apply { setAddress(InetAddress.getLoopbackAddress()) }.getWebServer({ context ->
            context.addServlet("routes", object : HttpServlet() {
                override fun service(request: HttpServletRequest, response: HttpServletResponse) {
                    if (!http.handle(request, response)) response.status = 404
                }
            }).addMapping("/")
        })
        server.start()
        try {
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
            fun get(path: String, token: String?): HttpResponse<String> = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}$path"))
                .apply { token?.let { header("Authorization", "Bearer $it") } }.timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString())
            val mine = get("/v1/history", aliceToken)
            assertEquals(200, mine.statusCode(), mine.body())
            val record = DeckJson.format.decodeFromString<MatchHistory>(mine.body()).records.single()
            assertEquals(MatchEnding.Conceded(b.account.accountId), record.ending)
            assertEquals("Http pod", record.podName)
            val game = DeckJson.format.decodeFromString<MatchHistory>(get("/v1/games/${m.game.id}/history", bobToken).body())
            assertEquals(listOf(record.id), game.records.map { it.id })
            assertTrue(DeckJson.format.decodeFromString<MatchHistory>(get("/v1/games/${m.game.id}/history", carolToken).body()).records.isEmpty())
            val matches = get("/v1/pods/$pod/matches", bobToken)
            assertEquals(200, matches.statusCode())
            assertTrue(DeckJson.format.decodeFromString<PodMatches>(matches.body()).records.single().players.all { it.deck == null })
            assertEquals(404, get("/v1/pods/$pod/matches", carolToken).statusCode(), "members only")
            assertEquals(404, get("/v1/pods/not-a-pod/matches", bobToken).statusCode())
            assertEquals(401, get("/v1/history", null).statusCode())
            assertEquals(405, client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/v1/history"))
                .header("Authorization", "Bearer $aliceToken").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode())
        } finally { server.stop() }
    }
}
