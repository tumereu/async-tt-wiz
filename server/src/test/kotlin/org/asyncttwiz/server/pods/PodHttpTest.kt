package org.asyncttwiz.server.pods

import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceSettings
import org.asyncttwiz.server.SpringService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PodHttpTest {
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()

    private inner class Fixture(val db: OwnedPostgres) : AutoCloseable {
        private fun port() = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val settings = ServiceSettings(db.settings, URI("https://synthetic.invalid"), InetAddress.getByName("127.0.0.1"), port(), InetAddress.getByName("127.0.0.1"), port())
        val service = SpringService(settings)
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        val a: SessionCreated
        val b: SessionCreated
        val c: SessionCreated
        val cube: SavedCube
        init {
            service.start()
            val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
            while (client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(), HttpResponse.BodyHandlers.ofString()).statusCode() != 200) {
                check(System.nanoTime() < deadline); Thread.sleep(20)
            }
            a = account("pod-a"); b = account("pod-b"); c = account("pod-c")
            val cards = (0 until 90).map { n ->
                val card = CardDefinition("Private fixture $n", listOf(DefinitionFace(id(), "Private fixture $n", manaCost = "{W}",
                    sourceData = if (n % 2 == 0) "{\"colors\":[\"U\"]}" else null)))
                SubmittedDefinition(DeckJson.digest(card), card)
            }
            send(a, "POST", "/v1/definitions", SubmitDefinitions(id(), cards))
            cube = json.decodeFromString(send(a, "POST", "/v1/cubes", CreateCube(id(), "Snapshot cube", cards.map { SavedCubeEntry(it.digest, 1) }), 201))
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
        fun get(who: SessionCreated, pod: String): PodDetail {
            val response = request(who, "GET", "/v1/pods/$pod")
            assertEquals(200, response.statusCode(), response.body())
            return json.decodeFromString(response.body())
        }
        fun list(who: SessionCreated): PodList = json.decodeFromString(request(who, "GET", "/v1/pods").body())
        fun activity(who: SessionCreated, after: String? = null): ActivityPage {
            val response = request(who, "GET", "/activity" + (after?.let { "?after=$it" } ?: ""))
            assertEquals(200, response.statusCode(), response.body())
            return ActivityWire.json.decodeFromString(response.body())
        }
        fun target(who: SessionCreated, pod: String, kind: ActivityKind): ActivityTarget {
            val response = request(who, "GET", "/activity/target?kind=${kind.name}&game=$pod&identity=${id()}")
            assertEquals(200, response.statusCode(), response.body())
            return ActivityWire.json.decodeFromString(response.body())
        }
        fun create(mode: DraftMode = DraftMode.BOOSTER): PodDetail = json.decodeFromString(send(a, "POST", "/v1/pods", CreatePod(id(), "Fixture pod", cube.cube.id, mode), 201))
        fun join(pod: String, who: SessionCreated = b) {
            send(a, "POST", "/v1/pods/$pod/invitations", InviteToPod(id(), who.account.accountId))
            send(who, "POST", "/v1/pods/$pod/accept", PodRequest(id()))
        }
        fun configure(pod: String, settings: DraftSettings) = send(a, "PUT", "/v1/pods/$pod", UpdatePod(id(), settings = settings))
        fun start(pod: String): PodDetail = json.decodeFromString(send(a, "POST", "/v1/pods/$pod/start", PodRequest(id())))
        fun selection(who: SessionCreated, pod: String): PickPod {
            val detail = get(who, pod); val head = detail.draft!!.head!!
            val keep = head.cards.take(detail.settings.keep).map { it.id }
            val burn = if (head.cards.size <= detail.settings.keep + detail.settings.burn) emptyList()
                else head.cards.drop(detail.settings.keep).take(detail.settings.burn).map { it.id }
            return PickPod(id(), detail.draftAttemptId!!, head.id, keep, burn)
        }
        fun pick(who: SessionCreated, pod: String): PodDetail = json.decodeFromString(send(who, "POST", "/v1/pods/$pod/pick", selection(who, pod)))
        fun finish(pod: String, players: List<SessionCreated>) {
            var actions = 0
            while (get(a, pod).phase == PodPhase.DRAFTING) {
                var progress = false
                for (player in players) if (get(player, pod).draft!!.head != null) {
                    pick(player, pod); progress = true; check(++actions < 1000)
                }
                assertTrue(progress)
            }
        }
        fun count(kind: ActivityKind, pod: String, who: SessionCreated? = null): Int = db.connection().use { connection ->
            connection.prepareStatement("select count(*) from account_activity where game_id = ? and kind = ?" + if (who != null) " and account_id = ?" else "").use { statement ->
                statement.setObject(1, UUID.fromString(pod)); statement.setString(2, kind.name)
                who?.let { statement.setObject(3, UUID.fromString(it.account.accountId)) }
                statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
            }
        }
        override fun close() = service.close()
    }

    private fun fixture(test: (Fixture) -> Unit) = OwnedPostgres().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        Fixture(db).use(test)
    }

    @Test fun `Glimpse HTTP completion private builds locked submission and retained retries`() = fixture { f ->
        val create = CreatePod(id(), "Glimpse pod", f.cube.cube.id, DraftMode.GLIMPSE)
        val created = f.send(f.a, "POST", "/v1/pods", create, 201)
        val pod = json.decodeFromString<PodDetail>(created).id
        assertEquals(created, f.send(f.a, "POST", "/v1/pods", create, 201))
        f.send(f.a, "POST", "/v1/pods", create.copy(name = "Conflicting"), 409)
        f.join(pod)
        f.configure(pod, DraftSettings.glimpse().copy(packsPerPlayer = 2, cardsPerPack = 6, packsOpenAtOnce = 2, sideboardLimit = 0))
        val start = PodRequest(id())
        val started = f.send(f.a, "POST", "/v1/pods/$pod/start", start)
        assertEquals(started, f.send(f.a, "POST", "/v1/pods/$pod/start", start))
        assertEquals(2, f.get(f.a, pod).members.first { it.account == f.a.account }.packsWaiting)
        assertEquals(PodAttention.YOUR_PICK, f.list(f.a).pods.single().attention)
        // Definitions and copy identities held by A must never reach B (even though A is the creator).
        assertPrivacy(f, pod)
        val pick = f.selection(f.a, pod)
        val picked = f.send(f.a, "POST", "/v1/pods/$pod/pick", pick)
        assertEquals(picked, f.send(f.a, "POST", "/v1/pods/$pod/pick", pick))
        assertEquals(1, f.get(f.a, pod).draft!!.pool.size)
        assertEquals(2, f.count(ActivityKind.POD_YOUR_PICK, pod)) // B already had two packs; no extra hint.
        assertPrivacy(f, pod)
        val copy = f.get(f.a, pod).draft!!.pool.single().id
        val attempt = f.get(f.a, pod).draftAttemptId!!
        f.send(f.b, "PUT", "/v1/pods/$pod/build/move", MovePodCopy(id(), attempt, copy, PodDeckCategory.SIDE), 400)
        val move = MovePodCopy(id(), attempt, copy, PodDeckCategory.SIDE)
        val moved = f.send(f.a, "PUT", "/v1/pods/$pod/build/move", move)
        assertEquals(moved, f.send(f.a, "PUT", "/v1/pods/$pod/build/move", move))
        f.send(f.a, "POST", "/v1/pods/$pod/submit", PodDraftRequest(id(), attempt), 409)
        f.send(f.a, "PUT", "/v1/pods/$pod/build/basics", SetPodBasics(id(), attempt, PodDeckBasics(plains = -1)), 400)
        f.finish(pod, listOf(f.a, f.b))
        val complete = f.get(f.a, pod)
        assertEquals(PodPhase.DRAFTED, complete.phase)
        assertEquals(4, complete.draft!!.pool.size)
        assertEquals(PodDeckCategory.SIDE, complete.build!!.copies.first { it.copyId == copy }.category)
        assertTrue(complete.build!!.copies.filter { it.copyId != copy }.all { it.category == PodDeckCategory.MAIN })
        assertEquals(8, f.get(f.a, pod).draft!!.pool.size + f.get(f.b, pod).draft!!.pool.size) // Burns never become pool cards.
        assertEquals(2, f.count(ActivityKind.POD_DRAFT_COMPLETE, pod))
        assertEquals(PodAttention.BUILD, f.list(f.a).pods.single().attention)
        assertEquals(attempt, f.activity(f.a).entries.single { it.kind == ActivityKind.POD_DRAFT_COMPLETE }.identity)
        f.send(f.a, "POST", "/v1/pods/$pod/submit", PodDraftRequest(id(), attempt), 400) // main too small
        f.send(f.a, "PUT", "/v1/pods/$pod/build/basics", SetPodBasics(id(), attempt, PodDeckBasics(plains = 40)))
        assertEquals("sideboard_limit", json.decodeFromString<PodError>(f.send(f.a, "POST", "/v1/pods/$pod/submit", PodDraftRequest(id(), attempt), 400)).code)
        f.send(f.a, "PUT", "/v1/pods/$pod/build/move", move.copy(requestId = id(), category = PodDeckCategory.TRASH))
        val submit = PodDraftRequest(id(), attempt)
        val submitted = f.send(f.a, "POST", "/v1/pods/$pod/submit", submit)
        assertEquals(submitted, f.send(f.a, "POST", "/v1/pods/$pod/submit", submit))
        val locked = f.get(f.a, pod)
        assertTrue(locked.submitted); assertEquals(3, locked.deck!!.copies.size)
        assertFalse(f.get(f.b, pod).submitted)
        assertPrivacy(f, pod)
        f.send(f.a, "PUT", "/v1/pods/$pod/build/move", move.copy(requestId = id()), 409)
        f.send(f.a, "PUT", "/v1/pods/$pod/build/basics", SetPodBasics(id(), attempt, PodDeckBasics()), 409)
        assertEquals(PodAttention.NONE, f.list(f.a).pods.single().attention)
    }

    private fun assertPrivacy(f: Fixture, pod: String) {
        checkPrivacy(f.get(f.a, pod), f.get(f.b, pod))
        checkPrivacy(f.get(f.b, pod), f.get(f.a, pod)) // The creator has no extra visibility either.
    }

    private fun checkPrivacy(a: PodDetail, b: PodDetail) {
        val aCards = a.draft!!.head?.cards.orEmpty() + a.draft!!.pool
        val bCards = b.draft!!.head?.cards.orEmpty() + b.draft!!.pool
        val payload = json.encodeToString(b)
        for (copy in aCards.filter { aCard -> bCards.none { it.id == aCard.id } }) assertFalse(payload.contains(copy.id), copy.id)
        for (definition in a.definitions.filter { aDefinition -> b.definitions.none { it.digest == aDefinition.digest } }) {
            assertFalse(payload.contains(definition.digest), definition.digest)
            assertFalse(payload.contains(json.encodeToString(definition.definition.name)), definition.definition.name)
            assertFalse(payload.contains(definition.definition.faces.first().faceId))
        }
        assertEquals(bCards.map { it.definition }.toSet(), b.definitions.map { it.digest }.toSet())
        assertFalse(payload.contains("sourceSnapshot")); assertFalse(payload.contains("queues")); assertFalse(payload.contains("seating"))
    }

    @Test fun `three player Booster passing queue notifications multi pack rounds and concurrent retries`() = fixture { f ->
        val pod = f.create().id
        f.join(pod); f.join(pod, f.c)
        f.configure(pod, DraftSettings(packsPerPlayer = 3, cardsPerPack = 4, packsOpenAtOnce = 2, colorBalance = false))
        val started = f.start(pod)
        assertTrue(started.members.all { it.packsWaiting == 2 })
        assertEquals(3, f.count(ActivityKind.POD_YOUR_PICK, pod))
        val oldViews = listOf(f.a, f.b, f.c).associateWith { f.get(it, pod).draft!! }
        val request = f.selection(f.a, pod)
        val retries = List(2) { CompletableFuture.supplyAsync { f.send(f.a, "POST", "/v1/pods/$pod/pick", request) } }
        assertEquals(retries[0].join(), retries[1].join())
        assertEquals(1, f.get(f.a, pod).draft!!.pool.size)
        val recipient = listOf(f.b, f.c).single { f.get(it, pod).draft!!.packsBehind == 2 }
        val third = listOf(f.b, f.c).single { it != recipient }
        assertEquals(1, f.get(third, pod).draft!!.packsBehind)
        assertEquals(3, f.count(ActivityKind.POD_YOUR_PICK, pod))
        // Deplete A's queue, then recipient's original packs pass to the third seat,
        // and third's original pack arrives at empty A: exactly one new A hint.
        f.pick(f.a, pod)
        assertNull(f.get(f.a, pod).draft!!.head)
        val before = f.count(ActivityKind.POD_YOUR_PICK, pod, f.a)
        val thirdPack = oldViews.getValue(third).head!!
        val thirdPick = f.pick(third, pod)
        assertEquals(thirdPack.id, f.get(f.a, pod).draft!!.head!!.id)
        assertEquals(3, f.get(f.a, pod).draft!!.head!!.cards.size)
        assertEquals(before + 1, f.count(ActivityKind.POD_YOUR_PICK, pod, f.a))
        f.pick(third, pod)
        assertEquals(before + 1, f.count(ActivityKind.POD_YOUR_PICK, pod, f.a))
        assertEquals(2, f.get(f.a, pod).members.first { it.account == f.a.account }.packsWaiting)
        assertFalse(thirdPick.draft!!.pool.isEmpty())
        val concurrent = listOf(f.a, recipient).map { player ->
            val selection = f.selection(player, pod)
            val poolSize = f.get(player, pod).draft!!.pool.size
            Triple(player, poolSize, CompletableFuture.supplyAsync { f.send(player, "POST", "/v1/pods/$pod/pick", selection) })
        }
        concurrent.forEach { (player, previousSize, response) ->
            response.join(); assertTrue(f.get(player, pod).draft!!.pool.size >= previousSize + 1)
        }
        f.finish(pod, listOf(f.a, f.b, f.c))
        val allCopies = listOf(f.a, f.b, f.c).flatMap { f.get(it, pod).draft!!.pool }
        assertEquals(36, allCopies.size); assertEquals(36, allCopies.map { it.id }.toSet().size)
        assertEquals(3, f.count(ActivityKind.POD_DRAFT_COMPLETE, pod))
    }

    @Test fun `setup authorization supply snapshots invitation lifecycle and limits`() = fixture { f ->
        val pod = f.create().id
        assertEquals(401, f.request(null, "GET", "/v1/pods").statusCode())
        assertEquals(404, f.request(f.b, "GET", "/v1/pods/$pod").statusCode())
        f.send(f.a, "POST", "/v1/pods/$pod/start", PodRequest(id()), 409)
        f.send(f.b, "POST", "/v1/pods/$pod/start", PodRequest(id()), 403)
        f.send(f.b, "PUT", "/v1/pods/$pod", UpdatePod(id(), name = "Other"), 403)
        f.send(f.a, "PUT", "/v1/pods/$pod", UpdatePod(id(), settings = DraftSettings(sideboardLimit = -1)), 400)
        f.send(f.a, "POST", "/v1/pods/$pod/invitations", InviteToPod(id(), f.b.account.accountId))
        assertEquals(pod, f.list(f.b).invitations.single().podId)
        assertTrue(f.activity(f.b).entries.any { it.kind == ActivityKind.POD_INVITATION })
        f.send(f.b, "POST", "/v1/pods/$pod/decline", PodRequest(id()))
        assertTrue(f.list(f.b).invitations.isEmpty())
        f.join(pod)
        f.configure(pod, DraftSettings(packsPerPlayer = 4))
        assertEquals("insufficient_supply", json.decodeFromString<PodError>(f.send(f.a, "POST", "/v1/pods/$pod/start", PodRequest(id()), 409)).code)
        f.send(f.a, "POST", "/v1/pods/$pod/leave", PodRequest(id()), 403)
        val leave = PodRequest(id())
        val left = f.send(f.b, "POST", "/v1/pods/$pod/leave", leave)
        assertEquals(left, f.send(f.b, "POST", "/v1/pods/$pod/leave", leave))
        assertEquals(1, f.get(f.a, pod).members.size)
        f.join(pod)
        f.send(f.a, "POST", "/v1/pods/$pod/invitations", InviteToPod(id(), f.c.account.accountId))
        f.configure(pod, DraftSettings(packsPerPlayer = 1, cardsPerPack = 3))
        // Cube rename, contents replacement and deletion do not alter the chosen pod snapshot.
        f.send(f.a, "PUT", "/v1/cubes/${f.cube.cube.id}", UpdateCube(id(), name = "Changed", entries = f.cube.entries.take(1)))
        f.send(f.a, "DELETE", "/v1/cubes/${f.cube.cube.id}", DeleteCube(id()))
        assertEquals(PodSource("Snapshot cube", 90), f.get(f.a, pod).source)
        f.start(pod)
        assertTrue(f.list(f.c).invitations.isEmpty())
        f.send(f.c, "POST", "/v1/pods/$pod/accept", PodRequest(id()), 409)
        f.send(f.a, "POST", "/v1/pods/$pod/invitations", InviteToPod(id(), f.c.account.accountId), 409)
        f.send(f.a, "PUT", "/v1/pods/$pod", UpdatePod(id(), name = "Too late"), 409)
        assertEquals(404, f.request(f.c, "GET", "/v1/pods/$pod").statusCode())
        // Snapshot stores explicit front colors, not only mana-cost inference.
        f.db.connection().use { connection ->
            connection.prepareStatement("select source_snapshot::text from pod where id = ?").use { statement ->
                statement.setObject(1, UUID.fromString(pod))
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    val source = json.decodeFromString<List<DraftCubeEntry>>(rows.getString(1))
                    assertTrue(source.any { it.colors == setOf(DraftColor.U) }); assertTrue(source.any { it.colors == setOf(DraftColor.W) })
                }
            }
        }
    }

    @Test fun `leave cancel destroy clear attempts pools and builds and notify affected members`() = fixture { f ->
        val pod = f.create().id
        f.join(pod)
        f.configure(pod, DraftSettings(packsPerPlayer = 1, cardsPerPack = 5))
        f.start(pod)
        val pick = f.selection(f.a, pod)
        f.send(f.a, "POST", "/v1/pods/$pod/pick", pick)
        f.send(f.a, "PUT", "/v1/pods/$pod/build/basics", SetPodBasics(id(), pick.draftAttemptId, PodDeckBasics(forest = 40)))
        f.send(f.b, "POST", "/v1/pods/$pod/cancel", PodDraftRequest(id(), pick.draftAttemptId), 403)
        val cancel = PodDraftRequest(id(), pick.draftAttemptId)
        val cancelled = f.send(f.a, "POST", "/v1/pods/$pod/cancel", cancel)
        assertEquals(PodPhase.SETUP, json.decodeFromString<PodDetail>(cancelled).phase)
        assertEquals("draft_attempt_expired", json.decodeFromString<PodError>(f.send(f.a, "POST", "/v1/pods/$pod/cancel", cancel, 409)).code)
        val reset = f.get(f.a, pod)
        assertEquals(PodPhase.SETUP, reset.phase); assertNull(reset.draft); assertNull(reset.build); assertNull(reset.draftAttemptId)
        assertEquals(1, f.count(ActivityKind.POD_DRAFT_CANCELLED, pod, f.b))
        assertTrue(f.activity(f.b).entries.any { it.kind == ActivityKind.POD_DRAFT_CANCELLED })
        assertFalse(f.activity(f.b).entries.any { it.kind == ActivityKind.POD_YOUR_PICK })
        f.send(f.a, "POST", "/v1/pods/$pod/pick", pick, 409) // Even the previously successful retry is expired.
        f.start(pod)
        val next = f.get(f.a, pod)
        assertNotEquals(pick.draftAttemptId, next.draftAttemptId)
        assertTrue(next.build!!.copies.isEmpty()); assertEquals(0L, next.build!!.basics.total)
        f.send(f.a, "PUT", "/v1/pods/$pod/build/basics", SetPodBasics(id(), pick.draftAttemptId, PodDeckBasics()), 409)
        f.send(f.a, "POST", "/v1/pods/$pod/pick", pick.copy(requestId = id()), 409)
        f.send(f.b, "POST", "/v1/pods/$pod/leave", PodRequest(id()))
        assertEquals(PodPhase.SETUP, f.get(f.a, pod).phase); assertEquals(1, f.get(f.a, pod).members.size)
        assertEquals(1, f.count(ActivityKind.POD_DRAFT_CANCELLED, pod, f.a))
        f.join(pod)
        f.start(pod)
        f.send(f.b, "DELETE", "/v1/pods/$pod", PodRequest(id()), 403)
        val destroy = PodRequest(id())
        val destroyed = f.send(f.a, "DELETE", "/v1/pods/$pod", destroy)
        assertEquals(destroyed, f.send(f.a, "DELETE", "/v1/pods/$pod", destroy))
        assertTrue(f.list(f.a).pods.isEmpty()); assertTrue(f.list(f.b).pods.isEmpty())
        assertEquals(1, f.count(ActivityKind.POD_DESTROYED, pod, f.b))
        assertTrue(f.activity(f.b).entries.any { it.kind == ActivityKind.POD_DESTROYED })
        assertEquals(404, f.request(f.a, "GET", "/v1/pods/$pod").statusCode())
        // Setup destruction also works.
        val setup = f.create().id
        f.send(f.a, "DELETE", "/v1/pods/$setup", PodRequest(id()))
    }

    @Test fun `after draft leaving preserves three member pod then destroys final two member pod`() = fixture { f ->
        val pod = f.create().id
        f.join(pod); f.join(pod, f.c)
        f.configure(pod, DraftSettings(packsPerPlayer = 1, cardsPerPack = 1)) // Automatic awards can complete at Start.
        val started = f.start(pod)
        assertEquals(PodPhase.DRAFTED, started.phase)
        f.send(f.a, "POST", "/v1/pods/$pod/cancel", PodDraftRequest(id(), started.draftAttemptId!!), 409)
        f.send(f.c, "POST", "/v1/pods/$pod/leave", PodRequest(id()))
        assertEquals(2, f.get(f.a, pod).members.size)
        f.send(f.b, "POST", "/v1/pods/$pod/leave", PodRequest(id()))
        assertEquals(404, f.request(f.a, "GET", "/v1/pods/$pod").statusCode())
        assertEquals(1, f.count(ActivityKind.POD_DESTROYED, pod, f.a))
        val drafted = f.create().id
        f.join(drafted); f.configure(drafted, DraftSettings(packsPerPlayer = 1, cardsPerPack = 1)); f.start(drafted)
        f.send(f.a, "DELETE", "/v1/pods/$drafted", PodRequest(id()))
        assertEquals(1, f.count(ActivityKind.POD_DESTROYED, drafted, f.b))
    }

    @Test fun `delayed cancel and retained draft responses cannot expose or cancel a replacement attempt`() = fixture { f ->
        val pod = f.create().id
        f.join(pod)
        f.configure(pod, DraftSettings(packsPerPlayer = 1, cardsPerPack = 5))
        val start = PodRequest(id())
        val started = f.send(f.a, "POST", "/v1/pods/$pod/start", start)
        val old = json.decodeFromString<PodDetail>(started)
        val attempt = old.draftAttemptId!!
        assertFalse(old.definitions.isEmpty())
        val pick = f.selection(f.a, pod)
        f.send(f.a, "POST", "/v1/pods/$pod/pick", pick)
        val basics = SetPodBasics(id(), attempt, PodDeckBasics(plains = 40))
        f.send(f.a, "PUT", "/v1/pods/$pod/build/basics", basics)
        // Cancel requires an explicit attempt; old attempt-free clients cannot cancel anything.
        f.send(f.a, "POST", "/v1/pods/$pod/cancel", PodRequest(id()), 400)
        f.send(f.a, "POST", "/v1/pods/$pod/cancel", PodDraftRequest(id(), attempt))
        fun expired(method: String, path: String, body: String) {
            val response = f.request(f.a, method, "/v1/pods/$pod/$path", body)
            assertEquals(409, response.statusCode(), response.body())
            assertEquals("draft_attempt_expired", json.decodeFromString<PodError>(response.body()).code)
            assertFalse(response.body().contains("definitions"))
            assertFalse(response.body().contains(old.draft!!.head!!.id))
        }
        expired("POST", "start", json.encodeToString(start))
        val replacement = f.start(pod)
        assertNotEquals(old.draftAttemptId, replacement.draftAttemptId)
        expired("POST", "start", json.encodeToString(start))
        expired("POST", "pick", json.encodeToString(pick))
        expired("PUT", "build/basics", json.encodeToString(basics))
        // This cancel was never previously delivered, so idempotency cannot protect the new draft.
        expired("POST", "cancel", json.encodeToString(PodDraftRequest(id(), attempt)))
        assertEquals(replacement, f.get(f.a, pod))
        f.send(f.a, "DELETE", "/v1/pods/$pod", PodRequest(id()))
        expired("POST", "start", json.encodeToString(start))
    }

    @Test fun `incremental activity never revives cancelled attempt pick hints after restart`() = fixture { f ->
        val pod = f.create().id
        f.join(pod)
        f.configure(pod, DraftSettings(packsPerPlayer = 1, cardsPerPack = 3))
        val first = f.start(pod)
        assertEquals(first.draftAttemptId, f.activity(f.b, "0").entries.single { it.kind == ActivityKind.POD_YOUR_PICK }.identity)
        f.send(f.a, "POST", "/v1/pods/$pod/cancel", PodDraftRequest(id(), first.draftAttemptId!!))
        val second = f.start(pod)
        // Reading the event log rather than the reset summaries used to reveal both attempts.
        for (page in listOf(f.activity(f.b, "0"), f.activity(f.b))) {
            val picks = page.entries.filter { it.kind == ActivityKind.POD_YOUR_PICK }
            assertEquals(1, picks.size)
            assertEquals(second.draftAttemptId, picks.single().identity)
            assertTrue(page.entries.any { it.kind == ActivityKind.POD_DRAFT_CANCELLED })
        }
        f.finish(pod, listOf(f.a, f.b))
        val completed = f.activity(f.b, "0").entries.filter { it.kind == ActivityKind.POD_DRAFT_COMPLETE }
        assertEquals(1, completed.size)
        assertEquals(second.draftAttemptId, completed.single().identity)
    }

    @Test fun `pod activity navigation authorizes members and only invitation targets for invitees`() = fixture { f ->
        val pod = f.create().id
        val kinds = ActivityKind.entries.filter { it.name.startsWith("POD_") }
        for (kind in kinds) {
            assertEquals(ActivityTarget(pod, ActivityDestination.POD), f.target(f.a, pod, kind))
            assertEquals(ActivityDestination.UNAVAILABLE, f.target(f.b, pod, kind).destination)
        }
        f.send(f.a, "POST", "/v1/pods/$pod/invitations", InviteToPod(id(), f.b.account.accountId))
        assertEquals(ActivityDestination.POD, f.target(f.b, pod, ActivityKind.POD_INVITATION).destination)
        for (kind in kinds.filter { it != ActivityKind.POD_INVITATION })
            assertEquals(ActivityDestination.UNAVAILABLE, f.target(f.b, pod, kind).destination)
        f.send(f.b, "POST", "/v1/pods/$pod/accept", PodRequest(id()))
        for (kind in kinds) assertEquals(ActivityDestination.POD, f.target(f.b, pod, kind).destination)
        f.send(f.b, "POST", "/v1/pods/$pod/leave", PodRequest(id()))
        for (kind in kinds) assertEquals(ActivityDestination.UNAVAILABLE, f.target(f.b, pod, kind).destination)
        f.send(f.a, "DELETE", "/v1/pods/$pod", PodRequest(id()))
        for (kind in kinds) assertEquals(ActivityDestination.UNAVAILABLE, f.target(f.a, pod, kind).destination)
    }

    @Test fun `pod hint cleanup supports resource indexes and clears every members queued hints`() = fixture { f ->
        for (who in listOf(f.a, f.b)) f.send(who, "PUT", "/activity/installations", RegisterInstallation(id(), id(), "fixture-token-${id()}"))
        val pod = f.create().id
        f.join(pod)
        f.configure(pod, DraftSettings(packsPerPlayer = 1, cardsPerPack = 3))
        val draft = f.start(pod)
        f.db.connection().use { connection ->
            // Small fixtures naturally prefer a sequential scan; prove the resource predicates
            // can both be served by the new indexes without needing an account/generation key.
            connection.createStatement().use { it.execute("set enable_seqscan = off") }
            for (table in listOf("activity_summary", "activity_push")) {
                connection.prepareStatement("explain delete from $table where game_id = ? and kind in ('POD_YOUR_PICK', 'POD_DRAFT_COMPLETE', 'POD_INVITATION')").use { statement ->
                    statement.setObject(1, UUID.fromString(pod))
                    val plan = statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString(1)) }.joinToString("\n") }
                    assertTrue(plan.contains("${table}_game_kind"), plan)
                    assertTrue(plan.contains("Index Cond:") && plan.contains("game_id =") && plan.contains("kind = ANY"), plan)
                }
                connection.prepareStatement("select count(*) from $table where game_id = ? and kind = 'POD_YOUR_PICK'").use { statement ->
                    statement.setObject(1, UUID.fromString(pod))
                    statement.executeQuery().use { rows -> assertTrue(rows.next()); assertEquals(2, rows.getInt(1)) }
                }
            }
        }
        f.send(f.a, "POST", "/v1/pods/$pod/cancel", PodDraftRequest(id(), draft.draftAttemptId!!))
        f.db.connection().use { connection ->
            for (table in listOf("activity_summary", "activity_push")) {
                connection.prepareStatement("select count(*) from $table where game_id = ? and kind in ('POD_YOUR_PICK', 'POD_DRAFT_COMPLETE', 'POD_INVITATION')").use { statement ->
                    statement.setObject(1, UUID.fromString(pod))
                    statement.executeQuery().use { rows -> assertTrue(rows.next()); assertEquals(0, rows.getInt(1)) }
                }
            }
        }
    }

    @Test fun `accepted members plus pending invitations cap at eight and setup can replace its source`() = fixture { f ->
        val pod = f.create().id
        f.join(pod)
        f.send(f.a, "POST", "/v1/pods/$pod/invitations", InviteToPod(id(), f.c.account.accountId))
        val targets = List(6) { index ->
            val account = id()
            f.db.connection().use { connection ->
                connection.prepareStatement("insert into account (id, username, username_key, email_delivery, email_key, password_phc) values (?, ?, ?, ?, ?, 'unused-fixture-password')").use { statement ->
                    statement.setObject(1, UUID.fromString(account))
                    for (column in 2..5) statement.setString(column, "capacity-$index")
                    statement.executeUpdate()
                }
            }
            account
        }
        targets.take(5).forEach { f.send(f.a, "POST", "/v1/pods/$pod/invitations", InviteToPod(id(), it)) }
        assertEquals(6, f.get(f.b, pod).pendingInvitations.size)
        f.send(f.a, "POST", "/v1/pods/$pod/invitations", InviteToPod(id(), targets.last()), 409)
        f.send(f.c, "POST", "/v1/pods/$pod/accept", PodRequest(id()))
        assertEquals(3, f.get(f.a, pod).members.size)
        f.send(f.a, "POST", "/v1/pods/$pod/invitations", InviteToPod(id(), targets.last()), 409)
        f.send(f.c, "POST", "/v1/pods/$pod/leave", PodRequest(id()))
        f.send(f.a, "POST", "/v1/pods/$pod/invitations", InviteToPod(id(), targets.last()))
        val replacement = json.decodeFromString<SavedCube>(f.send(f.a, "POST", "/v1/cubes", CreateCube(id(), "New source", f.cube.entries.take(2).map { it.copy(quantity = 4) }), 201))
        f.send(f.a, "PUT", "/v1/pods/$pod", UpdatePod(id(), name = "Renamed", cubeId = replacement.cube.id))
        assertEquals(PodSource("New source", 8), f.get(f.b, pod).source)
        assertEquals("Renamed", f.get(f.b, pod).name)
        f.send(f.b, "PUT", "/v1/pods/$pod", UpdatePod(id(), cubeId = f.cube.cube.id), 403)
        f.send(f.a, "PUT", "/v1/pods/$pod", UpdatePod(id(), settings = DraftSettings(packsOpenAtOnce = 0)), 400)
    }
}
