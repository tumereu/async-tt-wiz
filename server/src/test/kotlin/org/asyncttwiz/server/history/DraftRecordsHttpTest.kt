package org.asyncttwiz.server.history

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.EphemeralPorts
import org.asyncttwiz.server.ServiceSettings
import org.asyncttwiz.server.SpringService
import org.asyncttwiz.server.startBound
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** LOB-279 (owner, 2026-11-26): the drafts with card ratings the server keeps for each player's Stats, over HTTP. */
class DraftRecordsHttpTest {
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()

    private class Fixture(val db: TestPostgres) : AutoCloseable {
        private val json = DeckJson.format
        private val config = EphemeralPorts.settings(db.settings)
        val service = SpringService(config)
        val settings: ServiceSettings
        val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        val a: SessionCreated
        val b: SessionCreated
        init {
            settings = service.startBound(config)
            val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
            while (client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode() != 200) {
                check(System.nanoTime() < deadline); Thread.sleep(20)
            }
            a = account("drafts-a"); b = account("drafts-b")
        }
        private fun account(name: String): SessionCreated {
            send(null, "POST", "/accounts", RegisterAccount(name, "$name@example.invalid", "password😀"), 201)
            return json.decodeFromString(send(null, "POST", "/sessions", CreateSession("$name@example.invalid", "password😀"), 201))
        }
        fun request(who: SessionCreated?, method: String, path: String, body: String? = null): HttpResponse<String> {
            val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path"))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
            who?.let { builder.header("Authorization", "Bearer ${it.token}") }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        }
        inline fun <reified T> send(who: SessionCreated?, method: String, path: String, input: T, status: Int = 200): String {
            val response = request(who, method, path, json.encodeToString(input))
            assertEquals(status, response.statusCode(), "$method $path ${response.body()}")
            return response.body()
        }
        inline fun <reified T> get(who: SessionCreated, path: String, status: Int = 200): T? {
            val response = request(who, "GET", path)
            assertEquals(status, response.statusCode(), "GET $path ${response.body()}")
            return if (status == 200) json.decodeFromString<T>(response.body()) else null
        }
        fun pod(who: SessionCreated, pod: String): PodDetail = get(who, "/v1/pods/$pod")!!
        fun drafts(who: SessionCreated): List<DraftRecordSummary> = get<DraftRecordList>(who, "/v1/history/drafts")!!.drafts
        /** A pod of [setCode] (or the test cards' cube) Alice drafts alone with two bots, started. */
        fun soloPod(name: String, setCode: String?, settings: DraftSettings = DraftSettings(packsPerPlayer = 2, cardsPerPack = 5,
            keep = 1, burn = 1, bots = 2)): String {
            val cube = if (setCode != null) null else json.decodeFromString<SavedCube>(send(a, "POST", "/v1/cubes",
                CreateCube(UUID.randomUUID().toString(), "Draft records cube",
                    org.asyncttwiz.server.TestPrintings.supported().take(60).map { org.asyncttwiz.server.TestPrintings.entry(it, 1) }), 201)).cube.id
            val pod = json.decodeFromString<PodDetail>(send(a, "POST", "/v1/pods",
                CreatePod(UUID.randomUUID().toString(), name, cube, settings.mode, settings = settings, setCode = setCode), 201)).id
            send(a, "POST", "/v1/pods/$pod/start", PodRequest(UUID.randomUUID().toString()))
            return pod
        }
        /** Alice's first pick of [pod]; false when she has none. */
        fun pick(pod: String): Boolean {
            val detail = pod(a, pod)
            val head = detail.draft?.head ?: return false
            val keep = detail.settings.keepAt(head.pick - 1); val burn = detail.settings.burnAt(head.pick - 1)
            send(a, "POST", "/v1/pods/$pod/pick", PickPod(UUID.randomUUID().toString(), detail.draftAttemptId!!, head.id,
                head.cards.take(keep).map { it.id },
                if (detail.settings.keepsOnly(head.cards.size, keep.toLong(), head.pick - 1)) emptyList()
                else head.cards.drop(keep).take(burn).map { it.id }))
            return true
        }
        fun draftToTheEnd(pod: String) {
            var picks = 0
            while (pod(a, pod).phase == PodPhase.DRAFTING) { check(picks++ < 100) { "the draft didn't end" }; check(pick(pod)) }
        }
        fun <T> sql(block: (org.jooq.DSLContext) -> T): T = db.connection().use { block(DSL.using(it, SQLDialect.POSTGRES)) }
        override fun close() = service.close()
    }

    private fun fixture(test: (Fixture) -> Unit) = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        Fixture(db).use(test)
    }

    // LOB-279, LOB-182, DRF-315 (owner, 2026-11-26): Alice's draft of a set with card ratings is kept once her own draft is done,
    // with exactly what the pod's draft history gave her; it outlives the pod's deletion by hand and by the cleanup, and only she
    // reads it. A cube's draft and a set's without ratings aren't kept
    @Test fun `a rated draft is kept for the drafter once her own draft is done and outlives its pod`() = fixture { f ->
        val pod = f.soloPod("Kept draft", "TST")
        assertTrue(f.pod(f.a, pod).source.botRatings)
        assertTrue(f.pick(pod))
        assertEquals(emptyList<DraftRecordSummary>(), f.drafts(f.a), "nothing kept while her draft runs")
        f.draftToTheEnd(pod)
        val history = f.get<PodDraftHistory>(f.a, "/v1/pods/$pod/draft/history")!!
        val kept = f.drafts(f.a).single()
        assertEquals(pod, kept.podId)
        assertEquals("Kept draft", kept.podName)
        assertEquals("TST", kept.source.setCode)
        assertTrue(kept.source.botRatings)
        assertEquals(DraftMode.BOOSTER, kept.settings.mode)
        assertEquals((listOf(f.a.account.accountId) + PodBots.ids(2)).toSet(), kept.players.map { it.accountId }.toSet())
        assertEquals(PodBots.identity(1), kept.players.single { it.accountId == PodBots.id(1) })
        assertEquals(history.seating, kept.players.map { it.accountId }, "the players in seat order")
        assertTrue(OffsetDateTime.parse(kept.finishedAt).isAfter(OffsetDateTime.now().minusMinutes(5)))
        val record = f.get<DraftRecord>(f.a, "/v1/history/drafts/${kept.id}")!!
        assertEquals(kept, record.summary)
        assertEquals(history, record.history, "what the pod's draft history showed her, definitions included")
        assertTrue(record.history.picks.isNotEmpty())
        // Only hers
        assertEquals(emptyList<DraftRecordSummary>(), f.drafts(f.b))
        f.get<DraftRecord>(f.b, "/v1/history/drafts/${kept.id}", 404)
        f.get<DraftRecord>(f.a, "/v1/history/drafts/${UUID.randomUUID()}", 404)
        f.get<DraftRecord>(f.a, "/v1/history/drafts/not-a-draft", 404)
        assertEquals(401, f.request(null, "GET", "/v1/history/drafts").statusCode())
        // Deleted by hand: still kept and readable
        f.send(f.a, "DELETE", "/v1/pods/$pod", PodRequest(id()))
        f.get<PodDetail>(f.a, "/v1/pods/$pod", 404)
        assertEquals(listOf(kept), f.drafts(f.a))
        assertEquals(record, f.get<DraftRecord>(f.a, "/v1/history/drafts/${kept.id}"))
        // Deleted by the cleanup (LOB-133): still kept
        val other = f.soloPod("Cleaned up", "TST")
        f.draftToTheEnd(other)
        f.sql { GameRemoval.deletePods(it, listOf(UUID.fromString(other))) }
        assertEquals(listOf("Cleaned up", "Kept draft"), f.drafts(f.a).map { it.podName }, "newest first")
        // No card ratings, nothing kept
        val unrated = f.soloPod("Unrated", "TWL")
        assertFalse(f.pod(f.a, unrated).source.botRatings)
        f.draftToTheEnd(unrated)
        val cube = f.soloPod("Cube", null)
        f.draftToTheEnd(cube)
        assertEquals(listOf("Cleaned up", "Kept draft"), f.drafts(f.a).map { it.podName })
    }

    // LOB-279: a player's 21st kept draft drops their oldest; another player's drafts are their own count
    @Test fun `a 21st kept draft drops the oldest`() = fixture { f ->
        val start = OffsetDateTime.now().minusDays(30)
        val alice = UUID.fromString(f.a.account.accountId); val bob = UUID.fromString(f.b.account.accountId)
        fun keep(account: UUID, n: Int) = f.sql { sql ->
            DraftRecords.write(sql, account, UUID.randomUUID(), UUID.randomUUID(), "Draft $n", PodSource(kind = PodSourceKind.SET,
                setCode = "TST", botRatings = true), DraftSettings(), listOf(account.toString()), emptyList(), emptyList(), start.plusHours(n.toLong()))
        }
        keep(bob, 0)
        for (n in 1..20) keep(alice, n)
        assertEquals((20 downTo 1).map { "Draft $it" }, f.drafts(f.a).map { it.podName })
        keep(alice, 21)
        assertEquals((21 downTo 2).map { "Draft $it" }, f.drafts(f.a).map { it.podName })
        assertEquals(listOf("Draft 0"), f.drafts(f.b).map { it.podName })
        // A draft attempt is kept once
        val pod = UUID.randomUUID(); val attempt = UUID.randomUUID()
        repeat(2) { f.sql { sql -> DraftRecords.write(sql, bob, pod, attempt, "Once", PodSource(), DraftSettings(), emptyList(), emptyList(),
            emptyList(), OffsetDateTime.now()) } }
        assertEquals(listOf("Once", "Draft 0"), f.drafts(f.b).map { it.podName })
    }
}
