package org.asyncttwiz.server.pods

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.encodeToJsonElement
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceSettings
import org.asyncttwiz.server.SpringService
import org.asyncttwiz.server.EphemeralPorts
import org.asyncttwiz.server.startBound
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PodHttpTest {
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()

    private inner class Fixture(val db: TestPostgres) : AutoCloseable {
        private val config = EphemeralPorts.settings(db.settings)
        val service = SpringService(config)
        val settings: ServiceSettings
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        val a: SessionCreated
        val b: SessionCreated
        val c: SessionCreated
        val cube: SavedCube
        init {
            settings = service.startBound(config)
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
            // Invented card data (explicit colours apart from their cost), stored as a cube from before holds it
            val stored = db.connection().use { org.asyncttwiz.server.StoredDecks.cube(it, a.account.accountId, "Snapshot cube", cards.map { it.definition }) }
            cube = json.decodeFromString(request(a, "GET", "/v1/cubes/$stored").also { assertEquals(200, it.statusCode(), it.body()) }.body())
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
        fun gridSelection(who: SessionCreated, pod: String): SelectPodGrid {
            val detail = get(who, pod); val grid = detail.grid!!
            val board = grid.publicView.grids.single { it.id == grid.mine.headGridId }
            val row = (0..2).first { row -> (0..2).any { board.positions[row * 3 + it] != null } }
            return SelectPodGrid(id(), detail.draftAttemptId!!, board.id, GridLineType.ROW, row)
        }
        fun select(who: SessionCreated, pod: String, input: SelectPodGrid = gridSelection(who, pod)): PodDetail =
            json.decodeFromString(send(who, "POST", "/v1/pods/$pod/grid/select", input))
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

    private fun fixture(test: (Fixture) -> Unit) = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        Fixture(db).use(test)
    }

    // DRF-023
    @Test fun `creation atomically accepts all settings and rejects invalid ranges`() = fixture { f ->
        val settings = DraftSettings.glimpse().copy(packsPerPlayer = 4, cardsPerPack = 12, keep = 2, burn = 3,
            discardRemainingAt = 1, packsOpenAtOnce = 2, colorBalance = false, sideboardLimit = 7)
        val input = CreatePod(id(), "Configured at creation", f.cube.cube.id, settings = settings)
        val response = f.send(f.a, "POST", "/v1/pods", input, 201)
        val created = json.decodeFromString<PodDetail>(response)
        assertEquals(settings, created.settings)
        assertEquals(settings, f.get(f.a, created.id).settings)
        assertEquals(response, f.send(f.a, "POST", "/v1/pods", input, 201))
        for (invalid in listOf(settings.copy(keep = 13), settings.copy(sideboardLimit = -1),
            DraftSettings.grid().copy(grids = 2, gridsOpenAtOnce = 3))) {
            f.send(f.a, "POST", "/v1/pods", input.copy(requestId = id(), settings = invalid), 400)
        }
        assertEquals(1, f.list(f.a).pods.size)
    }

    // DRF-051
    @Test fun `Librarian settings snapshot privacy spending ownership and build synchronization over HTTP`() = fixture { f ->
        // Card data from the server's catalog (owner 2026-11-10): the server supplies the Librarian, whatever a device sends
        val definition = checkNotNull(org.asyncttwiz.server.TestPrintings.catalog.librarian)
        for (invalid in listOf(DraftSettings(librarians = -1), DraftSettings(librarians = 11), DraftSettings.grid().copy(librarians = 1)))
            f.send(f.a, "POST", "/v1/pods", CreatePod(id(), "Bad", f.cube.cube.id, settings = invalid), 400)
        val supplied = json.decodeFromString<PodDetail>(f.send(f.a, "POST", "/v1/pods",
            CreatePod(id(), "Supplied", f.cube.cube.id, settings = DraftSettings(librarians = 1)), 201))
        assertEquals(definition, supplied.settings.librarianDefinition)
        val impostor = definition.copy(definition = definition.definition.copy(name = "Not a Librarian"))
        val replaced = json.decodeFromString<PodDetail>(f.send(f.a, "POST", "/v1/pods", CreatePod(id(), "Wrong", f.cube.cube.id,
            settings = DraftSettings(librarians = 1, librarianDefinition = impostor)), 201))
        assertEquals(definition, replaced.settings.librarianDefinition)
        val none = json.decodeFromString<PodDetail>(f.send(f.a, "POST", "/v1/pods", CreatePod(id(), "None", f.cube.cube.id,
            settings = DraftSettings(librarianDefinition = definition)), 201))
        assertNull(none.settings.librarianDefinition, "no Librarians, no card data")
        for (pod in listOf(supplied, replaced, none)) f.send(f.a, "DELETE", "/v1/pods/${pod.id}", PodRequest(id()))
        for (mode in listOf(DraftMode.BOOSTER, DraftMode.GLIMPSE)) {
            val settings = DraftSettings(mode = mode, packsPerPlayer = 1, cardsPerPack = 8, burn = 2, librarians = 2)
            val created = json.decodeFromString<PodDetail>(f.send(f.a, "POST", "/v1/pods",
                CreatePod(id(), "Librarians", f.cube.cube.id, settings = settings), 201))
            val pod = created.id
            f.join(pod); f.start(pod)
            val a = f.get(f.a, pod); val b = f.get(f.b, pod)
            val owned = a.draft!!.pool.map { it.id }
            assertEquals(2, owned.size)
            assertTrue(a.draft!!.pool.all { it.librarian })
            assertTrue(a.build!!.copies.all { it.category == PodDeckCategory.SIDE })
            assertEquals(definition, b.definitions.single { it.digest == definition.digest })
            assertTrue(b.draft!!.pool.none { it.id in owned })
            val pack = a.draft!!.head!!
            assertTrue(pack.cards.none { it.librarian })
            val cards = pack.cards.map { it.id }
            // Spent Librarians join the pack: Booster passes both on; Glimpse keeps one back and burns the other.
            val input = if (mode == DraftMode.BOOSTER) PickPod(id(), a.draftAttemptId!!, pack.id, cards.take(3), cards.drop(3).take(2), owned)
                else PickPod(id(), a.draftAttemptId!!, pack.id, listOf(owned[0]) + cards.take(2), listOf(owned[1], cards[2]), owned)
            // Saved marks may mark a spent Librarian, but not one that is not spent.
            val marks = SetPodDraftMarks(a.draftAttemptId!!, pack.id, input.keep, input.burn, owned)
            f.send(f.a, "PUT", "/v1/pods/$pod/draft/marks", marks)
            assertEquals(DraftMarks(pack.id, input.keep, input.burn, owned), f.get(f.a, pod).draft!!.marks)
            assertEquals("invalid_marks", json.decodeFromString<PodError>(f.send(f.a, "PUT", "/v1/pods/$pod/draft/marks",
                marks.copy(keeps = listOf(owned[0]), burns = emptyList(), librarians = emptyList()), 400)).code)
            for (bad in listOf(input.copy(librarians = owned + owned.first()),
                input.copy(librarians = b.draft!!.pool.map { it.id }), input.copy(keep = input.keep.take(1)),
                input.copy(burn = input.burn + cards.last())))
                f.send(f.a, "POST", "/v1/pods/$pod/pick", bad.copy(requestId = id()), 409)
            val response = f.send(f.a, "POST", "/v1/pods/$pod/pick", input)
            assertEquals(response, f.send(f.a, "POST", "/v1/pods/$pod/pick", input))
            val picked = f.get(f.a, pod)
            assertNull(picked.draft!!.marks)
            assertEquals(input.keep.toSet(), picked.build!!.copies.map { it.copyId }.toSet())
            assertEquals(input.keep.toSet(), picked.draft!!.pool.map { it.id }.toSet())
            val passed = f.get(f.b, pod).draft!!.queuedPacks.single { it.id == pack.id }
            if (mode == DraftMode.BOOSTER) {
                assertTrue(picked.build!!.copies.all { it.category == PodDeckCategory.MAIN })
                assertEquals(cards.drop(5) + owned, passed.cards.map { it.id })
            } else {
                // The kept-back Librarian keeps its build place; the burned one is gone.
                assertEquals(PodDeckCategory.SIDE, picked.build!!.copies.single { it.copyId == owned[0] }.category)
                assertEquals(cards.drop(3), passed.cards.map { it.id })
            }
        }
        // A Librarian already in the source is ordinary supply, but becomes spendable once picked.
        val nativeCube = json.decodeFromString<SavedCube>(f.send(f.a, "POST", "/v1/cubes",
            CreateCube(id(), "Native Librarians", listOf(org.asyncttwiz.server.TestPrintings.entry("Cogwork Librarian", 16))), 201))
        val nativePod = json.decodeFromString<PodDetail>(f.send(f.a, "POST", "/v1/pods",
            CreatePod(id(), "Native", nativeCube.cube.id, settings = DraftSettings(packsPerPlayer = 1, cardsPerPack = 8)), 201))
        f.join(nativePod.id); f.start(nativePod.id)
        assertTrue(f.get(f.a, nativePod.id).draft!!.pool.isEmpty())
        val pickedNative = f.pick(f.a, nativePod.id).draft!!.pool.single()
        assertTrue(pickedNative.librarian)
        f.pick(f.b, nativePod.id)
        val next = f.get(f.a, nativePod.id)
        val head = next.draft!!.head!!
        val exchange = PickPod(id(), next.draftAttemptId!!, head.id, head.cards.take(2).map { it.id }, librarians = listOf(pickedNative.id))
        f.send(f.a, "POST", "/v1/pods/${nativePod.id}/pick", exchange)
        assertFalse(f.get(f.a, nativePod.id).draft!!.pool.any { it.id == pickedNative.id })
    }

    // DRF-044, DRF-093, DRF-120, DRF-136, DRF-139, DRF-140
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
        val mine = f.get(f.a, pod)
        val queued = mine.draft!!.queuedPacks.single()
        assertTrue(queued.cards.all { card -> mine.definitions.any { it.digest == card.definition } })
        assertEquals(PodAttention.YOUR_PICK, f.list(f.a).pods.single().attention)
        // Definitions and copy identities held by A must never reach B (even though A is the creator).
        assertPrivacy(f, pod)
        val pick = f.selection(f.a, pod)
        val picked = f.send(f.a, "POST", "/v1/pods/$pod/pick", pick)
        assertEquals(queued, json.decodeFromString<PodDetail>(picked).draft!!.head)
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
        f.send(f.a, "PUT", "/v1/pods/$pod/build/move", move.copy(requestId = id(), category = PodDeckCategory.MAYBE))
        val submit = PodDraftRequest(id(), attempt)
        val submitted = f.send(f.a, "POST", "/v1/pods/$pod/submit", submit)
        assertEquals(submitted, f.send(f.a, "POST", "/v1/pods/$pod/submit", submit))
        val locked = f.get(f.a, pod)
        assertTrue(locked.submitted); assertEquals(4, locked.deck!!.copies.size) // Maybe included (LOB-141)
        assertFalse(f.get(f.b, pod).submitted)
        assertPrivacy(f, pod)
        f.send(f.a, "PUT", "/v1/pods/$pod/build/move", move.copy(requestId = id()), 409)
        f.send(f.a, "PUT", "/v1/pods/$pod/build/basics", SetPodBasics(id(), attempt, PodDeckBasics()), 409)
        assertEquals(PodAttention.NONE, f.list(f.a).pods.single().attention)
    }

    // DRF-002
    @Test fun `two pods share one cube and a finished draft leaves it unchanged on every session`() = fixture { f ->
        // Each pod needs 48 of the cube's 90 copies, so two running pods only fit if neither consumes the cube.
        val settings = DraftSettings.glimpse().copy(packsPerPlayer = 2, cardsPerPack = 12, sideboardLimit = 0)
        val pods = List(2) { f.create(DraftMode.GLIMPSE).id }
        for (pod in pods) { f.join(pod); f.configure(pod, settings); f.start(pod) }
        assertTrue(pods.all { f.get(f.a, it).phase == PodPhase.DRAFTING })
        f.finish(pods.first(), listOf(f.a, f.b))
        assertEquals(PodPhase.DRAFTED, f.get(f.a, pods.first()).phase)
        assertEquals(PodPhase.DRAFTING, f.get(f.a, pods.last()).phase)
        val other = json.decodeFromString<SessionCreated>(f.send(null, "POST", "/sessions", CreateSession("pod-a@example.invalid", "password😀"), 201))
        assertNotEquals(f.a.token, other.token)
        for (session in listOf(f.a, other)) {
            val read = f.request(session, "GET", "/v1/cubes/${f.cube.cube.id}")
            assertEquals(200, read.statusCode(), read.body())
            assertEquals(f.cube, json.decodeFromString<SavedCube>(read.body()))
            assertEquals(listOf(f.cube.cube), json.decodeFromString<CubeList>(f.request(session, "GET", "/v1/cubes").body()).cubes)
        }
        assertEquals(90L, f.cube.cube.totalCopies)
        // A third pod can still start from the same cube.
        val third = f.create(DraftMode.GLIMPSE).id
        f.join(third); f.configure(third, settings)
        assertEquals(PodPhase.DRAFTING, f.start(third).phase)
    }

    // DRF-013
    @Test fun `pod list puts pods needing you first longest waiting first then the rest newest first`() = fixture { f ->
        val settings = DraftSettings.glimpse().copy(packsPerPlayer = 1, cardsPerPack = 3, sideboardLimit = 0)
        fun pod(): String = f.create(DraftMode.GLIMPSE).id.also { f.join(it); f.configure(it, settings) }
        val build = pod(); f.start(build); f.finish(build, listOf(f.a, f.b))
        val pick = pod(); f.start(pick)
        val waiting = pod(); f.start(waiting); f.pick(f.a, waiting) // A has picked; only B holds a pack.
        val setup = pod()
        val listed = f.list(f.a).pods
        assertEquals(listOf(pick, build, setup, waiting).toSet(), listed.map { it.id }.toSet())
        // Your pick and your build are one group, the longest waiting (the older build) first.
        assertEquals(listOf(PodAttention.BUILD, PodAttention.YOUR_PICK), listed.take(2).map { it.attention })
        assertEquals(listOf(build, pick), listed.take(2).map { it.id })
        assertEquals(1, listed[1].packsWaiting)
        // Pods not needing A follow, newest change first.
        assertEquals(listOf(setup, waiting), listed.drop(2).map { it.id })
        assertTrue(listed.drop(2).all { it.attention == PodAttention.NONE })
    }

    // LOB-076, DRF-136
    @Test fun `waiting list holds pods waiting for your pick or build until you act`() = fixture { f ->
        fun waiting(who: SessionCreated): List<ActivityHint> {
            val response = f.request(who, "GET", "/activity/waiting")
            assertEquals(200, response.statusCode(), response.body())
            return ActivityWire.json.decodeFromString<WaitingGames>(response.body()).games
        }
        val pod = f.create(DraftMode.GLIMPSE).id; f.join(pod)
        f.configure(pod, DraftSettings.glimpse().copy(packsPerPlayer = 1, cardsPerPack = 3, sideboardLimit = 0))
        assertTrue(waiting(f.a).isEmpty(), "Setup waits for nobody's pick")
        val attempt = f.start(pod).draftAttemptId
        for (who in listOf(f.a, f.b)) {
            val hint = waiting(who).single()
            assertEquals(ActivityKind.POD_YOUR_PICK, hint.kind); assertEquals(pod, hint.gameId)
            assertEquals(attempt, hint.identity); assertEquals("Fixture pod", hint.resourceName)
            assertEquals(f.activity(who, "0").entries.single { it.kind == ActivityKind.POD_YOUR_PICK }.activityId, hint.activityId)
        }
        f.pick(f.a, pod)
        assertTrue(waiting(f.a).none { it.kind == ActivityKind.POD_YOUR_PICK }, "Picked; B's pack has not reached A yet")
        f.finish(pod, listOf(f.a, f.b))
        for (who in listOf(f.a, f.b)) assertEquals(ActivityKind.POD_DRAFT_COMPLETE, waiting(who).single().kind)
        f.send(f.a, "PUT", "/v1/pods/$pod/build/basics", SetPodBasics(id(), attempt!!, PodDeckBasics(plains = 40)))
        f.send(f.a, "POST", "/v1/pods/$pod/submit", PodDraftRequest(id(), attempt))
        assertTrue(waiting(f.a).isEmpty(), "Submitted: the build no longer waits")
        assertEquals(1, waiting(f.b).size)
        assertTrue(waiting(f.c).isEmpty(), "Never another account's pods")
    }

    // DRF-105
    @Test fun `deck name endpoint is owner scoped validated idempotent and attempt fenced`() = fixture { f ->
        val pod = f.create().id
        f.join(pod)
        val started = f.start(pod)
        val attempt = started.draftAttemptId!!
        assertEquals("Fixture pod deck", started.deckName)
        val input = SetPodDeckName(id(), attempt, "Secret blue plan")
        val saved = f.send(f.a, "PUT", "/v1/pods/$pod/build/name", input)
        assertEquals(saved, f.send(f.a, "PUT", "/v1/pods/$pod/build/name", input))
        assertEquals(input.name, f.get(f.a, pod).deckName)
        assertEquals("Fixture pod deck", f.get(f.b, pod).deckName)
        assertFalse(f.request(f.b, "GET", "/v1/pods/$pod").body().contains(input.name))
        assertFalse(f.request(f.b, "GET", "/v1/pods").body().contains(input.name))
        f.send(f.b, "PUT", "/v1/pods/$pod/build/name", input, 409)
        f.send(f.c, "PUT", "/v1/pods/$pod/build/name", input.copy(requestId = id()), 404)
        for (name in listOf("", " ", "x".repeat(121), "bad\nname"))
            f.send(f.a, "PUT", "/v1/pods/$pod/build/name", input.copy(requestId = id(), name = name), 400)
        f.send(f.a, "PUT", "/v1/pods/$pod/build/name", input.copy(requestId = id(), draftAttemptId = id()), 409)
        assertEquals(input.name, f.get(f.a, pod).deckName)
    }

    // DRF-166: the route, its scope and its fence; the rules of what may be chosen are in DraftDeckTest
    @Test fun `deck cover endpoint is owner scoped validated and attempt fenced`() = fixture { f ->
        val pod = f.create().id
        f.join(pod)
        val attempt = f.start(pod).draftAttemptId!!
        val input = SetPodDeckCover(id(), attempt, "sha256:not-in-the-pool")
        f.send(f.a, "PUT", "/v1/pods/$pod/build/cover", input, 400)
        f.send(f.c, "PUT", "/v1/pods/$pod/build/cover", input.copy(requestId = id()), 404)
        f.send(f.a, "PUT", "/v1/pods/$pod/build/cover", input.copy(requestId = id(), draftAttemptId = id()), 409)
        assertEquals(null, f.get(f.a, pod).deckCover)
    }

    private fun assertPrivacy(f: Fixture, pod: String) {
        checkPrivacy(f.get(f.a, pod), f.get(f.b, pod))
        checkPrivacy(f.get(f.b, pod), f.get(f.a, pod)) // The creator has no extra visibility either.
    }

    private fun checkPrivacy(a: PodDetail, b: PodDetail) {
        val aCards = a.draft!!.head?.cards.orEmpty() + a.draft!!.queuedPacks.flatMap { it.cards } + a.draft!!.pool
        val bCards = b.draft!!.head?.cards.orEmpty() + b.draft!!.queuedPacks.flatMap { it.cards } + b.draft!!.pool
        val payload = json.encodeToString(b)
        // Match a complete JSON string: set copy :1 must not be confused with :10 through :14.
        for (copy in aCards.filter { aCard -> bCards.none { it.id == aCard.id } }) assertFalse(payload.contains(json.encodeToString(copy.id)), copy.id)
        for (definition in a.definitions.filter { aDefinition -> b.definitions.none { it.digest == aDefinition.digest } }) {
            assertFalse(payload.contains(definition.digest), definition.digest)
            assertFalse(payload.contains(json.encodeToString(definition.definition.name)), definition.definition.name)
            assertFalse(payload.contains(definition.definition.faces.first().faceId))
        }
        assertEquals(bCards.map { it.definition }.toSet(), b.definitions.map { it.digest }.toSet())
        assertFalse(payload.contains("sourceSnapshot")); assertFalse(payload.contains("queues")); assertFalse(payload.contains("seating"))
    }

    // DRF-136, DRF-140
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

    // DRF-047
    @Test fun `next round opens early once only the opponents final pick remains and notifies the opener`() = fixture { f ->
        val pod = f.create().id
        f.join(pod)
        f.configure(pod, DraftSettings(packsPerPlayer = 2, cardsPerPack = 3, colorBalance = false))
        f.start(pod)
        fun hints(who: SessionCreated) = f.count(ActivityKind.POD_YOUR_PICK, pod, who)
        assertEquals(1, hints(f.a)); assertEquals(1, hints(f.b))
        f.pick(f.a, pod) // A's pack 3 -> 2 queues behind B's own pack
        val aPack = f.get(f.b, pod).draft!!.queuedPacks.single().id
        f.pick(f.b, pod) // B's pack 3 -> 2 arrives at A's empty queue
        assertEquals(2, hints(f.a)); assertEquals(1, hints(f.b))
        val bPack = f.get(f.a, pod).draft!!.head!!.id
        val early = f.pick(f.b, pod) // A's pack 2 -> 1 is awarded to A; only A's final pick of B's pack remains
        assertEquals(0, early.draft!!.packsBehind)
        assertEquals(3, early.draft!!.head!!.cards.size)
        assertNotEquals(aPack, early.draft!!.head!!.id); assertNotEquals(bPack, early.draft!!.head!!.id)
        assertEquals(2, hints(f.b))
        assertEquals(PodPhase.DRAFTING, early.phase)
        val waitingA = f.get(f.a, pod)
        assertEquals(bPack, waitingA.draft!!.head!!.id); assertEquals(2, waitingA.draft!!.head!!.cards.size)
        assertEquals(2, waitingA.draft!!.pool.size)
        assertPrivacy(f, pod)
        val roundTwo = early.draft!!.head!!.id
        f.pick(f.b, pod) // B's round-two pack is held for A until A's remaining round-one pack is picked
        val heldA = f.get(f.a, pod)
        assertEquals(bPack, heldA.draft!!.head!!.id); assertTrue(heldA.draft!!.queuedPacks.isEmpty())
        assertEquals(0, heldA.draft!!.packsBehind)
        // A's one queued pick, then A opens round two: A's own pack and the held one are picks in a row too.
        assertEquals(3, heldA.members.first { it.account == f.a.account }.packsWaiting)
        assertEquals(3, f.list(f.a).pods.single { it.id == pod }.packsWaiting)
        assertEquals(0, heldA.members.first { it.account == f.b.account }.packsWaiting)
        assertEquals(2, hints(f.a))
        f.pick(f.a, pod) // A opens round two: A's own fresh pack first, then the held, picked one; the release notifies A
        val opened = f.get(f.a, pod).draft!!
        assertEquals(3, opened.head!!.cards.size); assertEquals(1, opened.head!!.pick); assertNull(opened.head!!.passedBy)
        assertEquals(roundTwo, opened.queuedPacks.single().id)
        assertEquals(f.b.account.accountId, opened.queuedPacks.single().passedBy)
        assertEquals(1, opened.packsBehind)
        assertEquals(3, hints(f.a))
        f.finish(pod, listOf(f.a, f.b))
        assertEquals(6, f.get(f.a, pod).draft!!.pool.size); assertEquals(6, f.get(f.b, pod).draft!!.pool.size)
        assertEquals(2, f.count(ActivityKind.POD_DRAFT_COMPLETE, pod))
    }

    private fun autoStart(f: Fixture, pod: String, enabled: Boolean, status: Int = 200, who: SessionCreated = f.a) =
        f.send(who, "POST", "/v1/pods/$pod/auto-start", SetPodAutoStart(id(), enabled), status)
    private fun invite(f: Fixture, pod: String, who: SessionCreated) =
        f.send(f.a, "POST", "/v1/pods/$pod/invitations", InviteToPod(id(), who.account.accountId))
    private fun respond(f: Fixture, pod: String, who: SessionCreated, accept: Boolean) =
        f.send(who, "POST", "/v1/pods/$pod/${if (accept) "accept" else "decline"}", PodRequest(id()))

    // DRF-069
    @Test fun `draft marks are private to the caller head pack and cleared by the pick and cancellation`() = fixture { f ->
        val pod = f.create().id
        f.join(pod)
        f.configure(pod, DraftSettings.glimpse().copy(packsPerPlayer = 2, cardsPerPack = 6, packsOpenAtOnce = 2))
        f.start(pod)
        fun rows() = f.db.connection().use { connection ->
            connection.prepareStatement("select count(*) from pod_draft_marks where pod_id = ?").use { statement ->
                statement.setObject(1, UUID.fromString(pod)); statement.executeQuery().use { it.next(); it.getInt(1) }
            }
        }
        fun code(body: String) = json.decodeFromString<PodError>(body).code
        val mine = f.get(f.a, pod)
        val attempt = mine.draftAttemptId!!
        val head = mine.draft!!.head!!
        assertEquals(1, head.pick); assertNull(head.passedBy); assertNull(mine.draft!!.marks)
        val marks = SetPodDraftMarks(attempt, head.id, listOf(head.cards[0].id), listOf(head.cards[1].id, head.cards[2].id))
        f.send(f.a, "PUT", "/v1/pods/$pod/draft/marks", marks)
        assertEquals(DraftMarks(head.id, marks.keeps, marks.burns), f.get(f.a, pod).draft!!.marks)
        // Latest wins.
        val changed = marks.copy(keeps = listOf(head.cards[3].id))
        f.send(f.a, "PUT", "/v1/pods/$pod/draft/marks", changed)
        assertEquals(DraftMarks(head.id, changed.keeps, changed.burns), f.get(f.a, pod).draft!!.marks)
        // Private: B sees none of A's marks and cannot write marks for a pack that is not B's head.
        assertNull(f.get(f.b, pod).draft!!.marks)
        assertEquals("not_head_of_queue", code(f.send(f.b, "PUT", "/v1/pods/$pod/draft/marks", marks, 409)))
        assertEquals("not_head_of_queue", code(f.send(f.a, "PUT", "/v1/pods/$pod/draft/marks",
            marks.copy(packId = mine.draft!!.queuedPacks.single().id), 409)))
        assertEquals("invalid_marks", code(f.send(f.a, "PUT", "/v1/pods/$pod/draft/marks",
            marks.copy(keeps = listOf(mine.draft!!.queuedPacks.single().cards[0].id)), 400)))
        assertEquals("invalid_marks", code(f.send(f.a, "PUT", "/v1/pods/$pod/draft/marks",
            marks.copy(burns = marks.keeps), 400)))
        assertEquals("draft_attempt_expired", code(f.send(f.a, "PUT", "/v1/pods/$pod/draft/marks",
            marks.copy(draftAttemptId = id()), 409)))
        assertEquals(1, rows())
        // The pick clears them; a late write for the picked pack is refused.
        f.pick(f.a, pod)
        assertEquals(0, rows())
        val next = f.get(f.a, pod).draft!!
        assertNull(next.marks)
        assertEquals("not_head_of_queue", code(f.send(f.a, "PUT", "/v1/pods/$pod/draft/marks", changed, 409)))
        // Picked packs arrive with orientation data.
        val passed = f.get(f.b, pod).draft!!.queuedPacks.last()
        assertEquals(2, passed.pick); assertEquals(f.a.account.accountId, passed.passedBy)
        val other = f.get(f.b, pod).draft!!.head!!
        f.send(f.b, "PUT", "/v1/pods/$pod/draft/marks", SetPodDraftMarks(attempt, other.id, listOf(other.cards[0].id)))
        assertEquals(DraftMarks(other.id, listOf(other.cards[0].id)), f.get(f.b, pod).draft!!.marks)
        assertNull(f.get(f.a, pod).draft!!.marks)
        f.send(f.a, "POST", "/v1/pods/$pod/cancel", PodDraftRequest(id(), attempt))
        assertEquals(0, rows())
        assertEquals("draft_attempt_expired", code(f.send(f.b, "PUT", "/v1/pods/$pod/draft/marks",
            SetPodDraftMarks(attempt, other.id), 409)))
        f.send(f.c, "PUT", "/v1/pods/$pod/draft/marks", SetPodDraftMarks(attempt, other.id), 404)
    }

    // DRF-165: the draft history is the caller's own picks with the packs as they came, named, a wheel's cards taken since,
    // and only for members of a Booster or Glimpse draft
    @Test fun `draft history lists the caller's picks with their packs and what a wheel lost`() = fixture { f ->
        val pod = f.create().id
        f.join(pod)
        f.configure(pod, DraftSettings.glimpse().copy(packsPerPlayer = 1, cardsPerPack = 7, burn = 1))
        f.start(pod)
        fun history(who: SessionCreated, status: Int = 200): String {
            val response = f.request(who, "GET", "/v1/pods/$pod/draft/history")
            assertEquals(status, response.statusCode(), response.body())
            return response.body()
        }
        assertTrue(json.decodeFromString<PodDraftHistory>(history(f.a)).picks.isEmpty())
        val first = f.get(f.a, pod).draft!!.head!!
        f.pick(f.a, pod); f.pick(f.b, pod); f.pick(f.a, pod); f.pick(f.b, pod)
        // A's own pack comes back: A picks it again
        val back = f.get(f.a, pod).draft!!.head!!
        assertEquals(first.id, back.id)
        f.pick(f.a, pod)
        val mine = json.decodeFromString<PodDraftHistory>(history(f.a))
        assertEquals(listOf(1, 2, 3), mine.picks.map { it.pick })
        assertEquals(first.cards.map { it.id }.toSet(), mine.picks[0].cards.map { it.id }.toSet())
        assertEquals(listOf(first.cards[0].id), mine.picks[0].kept)
        assertEquals(listOf(first.cards[1].id), mine.picks[0].burned)
        assertEquals(back.cards.map { it.id }.toSet(), mine.picks[2].cards.map { it.id }.toSet())
        // B kept one and burned one of A's pack: both are gone since A passed it
        assertEquals(2, mine.picks[2].takenSince.size)
        assertEquals((mine.picks.flatMap { it.cards + it.takenSince }).map { it.definition }.toSet(), mine.definitions.map { it.digest }.toSet())
        // B's history is B's own two picks and the last card of A's pack, given automatically
        assertEquals(listOf(false, false, true), json.decodeFromString<PodDraftHistory>(history(f.b)).picks.map { it.automatic })
        history(f.c, 404)
        // A pod without a Booster draft has no history
        val setup = f.create(DraftMode.GRID).id
        assertEquals("wrong_draft_mode", json.decodeFromString<PodError>(f.request(f.a, "GET", "/v1/pods/$setup/draft/history").body()).code)
    }

    // DRF-118
    @Test fun `deck notes are private per attempt, editable after submitting and cleared by cancellation`() = fixture { f ->
        val pod = f.create().id
        f.join(pod)
        f.configure(pod, DraftSettings(packsPerPlayer = 1, cardsPerPack = 1))
        f.start(pod)
        fun code(body: String) = json.decodeFromString<PodError>(body).code
        fun rows() = f.db.connection().use { connection ->
            connection.prepareStatement("select count(*) from pod_deck_notes where pod_id = ?").use { statement ->
                statement.setObject(1, UUID.fromString(pod)); statement.executeQuery().use { it.next(); it.getInt(1) }
            }
        }
        val attempt = f.get(f.a, pod).draftAttemptId!!
        assertNull(f.get(f.a, pod).notes)
        val notes = PodDeckNotes("Splash red?\nCut the 6-drop", listOf(1, 2, 0, 0, 0, 0, 3, 99))
        f.send(f.a, "PUT", "/v1/pods/$pod/build/notes", SetPodDeckNotes(attempt, notes))
        assertEquals(notes, f.get(f.a, pod).notes)
        val changed = notes.copy(text = "Latest", counters = List(8) { 0 })
        f.send(f.a, "PUT", "/v1/pods/$pod/build/notes", SetPodDeckNotes(attempt, changed))
        assertEquals(changed, f.get(f.a, pod).notes)
        // Private: B's view never carries A's notes, and B's own are separate.
        assertNull(f.get(f.b, pod).notes)
        f.send(f.b, "PUT", "/v1/pods/$pod/build/notes", SetPodDeckNotes(attempt, PodDeckNotes("B")))
        assertEquals("B", f.get(f.b, pod).notes!!.text); assertEquals(changed, f.get(f.a, pod).notes)
        f.send(f.c, "PUT", "/v1/pods/$pod/build/notes", SetPodDeckNotes(attempt, notes), 404)
        for (invalid in listOf(notes.copy(text = "x".repeat(PodDeckNotes.MAX_TEXT + 1)), notes.copy(text = "bell\u0007"),
            notes.copy(counters = List(7) { 0 }), notes.copy(counters = List(7) { 0 } + 100), notes.copy(counters = List(7) { 0 } + -1)))
            assertEquals("invalid_notes", code(f.send(f.a, "PUT", "/v1/pods/$pod/build/notes", SetPodDeckNotes(attempt, invalid), 400)))
        assertEquals("draft_attempt_expired", code(f.send(f.a, "PUT", "/v1/pods/$pod/build/notes", SetPodDeckNotes(id(), notes), 409)))
        // Kept after the draft and still editable after submitting.
        f.finish(pod, listOf(f.a, f.b))
        assertEquals(changed, f.get(f.a, pod).notes)
        f.send(f.a, "PUT", "/v1/pods/$pod/build/basics", SetPodBasics(id(), attempt, PodDeckBasics(plains = 40)))
        f.send(f.a, "POST", "/v1/pods/$pod/submit", PodDraftRequest(id(), attempt))
        f.send(f.a, "PUT", "/v1/pods/$pod/build/notes", SetPodDeckNotes(attempt, notes))
        assertEquals(notes, f.get(f.a, pod).notes)
        assertEquals(2, rows())
        // A new attempt starts empty; the cancelled attempt's notes are gone.
        val other = f.create().id
        f.join(other); f.configure(other, DraftSettings(packsPerPlayer = 1, cardsPerPack = 4)); f.start(other)
        val second = f.get(f.a, other).draftAttemptId!!
        f.send(f.a, "PUT", "/v1/pods/$other/build/notes", SetPodDeckNotes(second, notes))
        f.send(f.a, "POST", "/v1/pods/$other/cancel", PodDraftRequest(id(), second))
        assertNull(f.get(f.a, other).notes)
        assertEquals("draft_attempt_expired", code(f.send(f.a, "PUT", "/v1/pods/$other/build/notes", SetPodDeckNotes(second, notes), 409)))
        assertEquals(2, rows())
    }

    // DRF-036
    @Test fun `auto start arms only with pending invitations and can be cancelled`() = fixture { f ->
        val pod = f.create().id
        assertEquals("no_pending_invitations", json.decodeFromString<PodError>(autoStart(f, pod, true, 409)).code)
        invite(f, pod, f.b)
        autoStart(f, pod, true, 403, f.b)
        val armed = json.decodeFromString<PodDetail>(autoStart(f, pod, true))
        assertTrue(armed.autoStart); assertNull(armed.autoStartOff)
        assertEquals(PodPhase.SETUP, armed.phase)
        val cancelled = json.decodeFromString<PodDetail>(autoStart(f, pod, false))
        assertFalse(cancelled.autoStart)
        respond(f, pod, f.b, true)
        assertEquals(PodPhase.SETUP, f.get(f.a, pod).phase) // cancelled: accepting does not start
        assertEquals(0, f.count(ActivityKind.POD_AUTO_START_OFF, pod))
    }

    // DRF-037
    @Test fun `armed pod starts when the last invitation is accepted and settings edits keep it armed`() = fixture { f ->
        val pod = f.create().id
        invite(f, pod, f.b); invite(f, pod, f.c)
        autoStart(f, pod, true)
        f.configure(pod, DraftSettings(packsPerPlayer = 2, cardsPerPack = 15, colorBalance = false))
        assertTrue(f.get(f.a, pod).autoStart)
        respond(f, pod, f.b, true)
        val waiting = f.get(f.b, pod)
        assertEquals(PodPhase.SETUP, waiting.phase); assertTrue(waiting.autoStart) // members see it armed
        assertNull(waiting.autoStartOff)
        assertEquals(0, f.count(ActivityKind.POD_YOUR_PICK, pod))
        val started = json.decodeFromString<PodDetail>(respond(f, pod, f.c, true))
        assertEquals(PodPhase.DRAFTING, started.phase)
        assertNotNull(started.draft!!.head)
        assertFalse(started.autoStart)
        assertEquals(3, started.members.size); assertTrue(started.pendingInvitations.isEmpty())
        for (who in listOf(f.a, f.b, f.c)) assertEquals(1, f.count(ActivityKind.POD_YOUR_PICK, pod, who))
        assertEquals(0, f.count(ActivityKind.POD_AUTO_START_OFF, pod))
    }

    // DRF-037
    @Test fun `a decline switches auto start off and tells the creator`() = fixture { f ->
        val pod = f.create().id
        invite(f, pod, f.b); invite(f, pod, f.c)
        autoStart(f, pod, true)
        respond(f, pod, f.b, false)
        val creator = f.get(f.a, pod)
        assertEquals(PodPhase.SETUP, creator.phase); assertFalse(creator.autoStart)
        assertEquals(PodAutoStartOff("declined", f.b.account), creator.autoStartOff)
        assertEquals(1, f.count(ActivityKind.POD_AUTO_START_OFF, pod, f.a))
        assertTrue(f.activity(f.a).entries.any { it.kind == ActivityKind.POD_AUTO_START_OFF && it.authorId == f.b.account.accountId })
        respond(f, pod, f.c, true)
        val joined = f.get(f.c, pod)
        assertEquals(PodPhase.SETUP, joined.phase)
        assertNull(joined.autoStartOff) // creator-only notice
        // Arming again clears the notice; with nobody pending it cannot be armed.
        invite(f, pod, f.b)
        assertNull(json.decodeFromString<PodDetail>(autoStart(f, pod, true)).autoStartOff)
        assertTrue(f.activity(f.a).entries.none { it.kind == ActivityKind.POD_AUTO_START_OFF })
        respond(f, pod, f.b, true)
        assertEquals(PodPhase.SETUP, f.get(f.a, pod).phase) // 3 x 45 cards exceed the 90-card cube
    }

    // DRF-037
    @Test fun `an armed pod that cannot start at the last acceptance stays in setup and tells the creator why`() = fixture { f ->
        val pod = f.create().id
        invite(f, pod, f.b); invite(f, pod, f.c)
        autoStart(f, pod, true)
        respond(f, pod, f.b, true)
        val last = json.decodeFromString<PodDetail>(respond(f, pod, f.c, true))
        assertEquals(PodPhase.SETUP, last.phase); assertFalse(last.autoStart)
        assertNull(last.autoStartOff)
        val creator = f.get(f.a, pod)
        assertEquals(PodAutoStartOff("insufficient_supply"), creator.autoStartOff)
        assertEquals(3, creator.members.size)
        assertEquals(1, f.count(ActivityKind.POD_AUTO_START_OFF, pod, f.a))
        assertEquals(0, f.count(ActivityKind.POD_YOUR_PICK, pod))
        // An ordinary start after fixing the settings clears the notice.
        f.configure(pod, DraftSettings(packsPerPlayer = 2, colorBalance = false))
        val started = f.start(pod)
        assertEquals(PodPhase.DRAFTING, started.phase); assertNull(started.autoStartOff)
    }

    // DRF-038
    @Test fun `start now with pending invitations lapses them`() = fixture { f ->
        val pod = f.create().id
        f.join(pod); invite(f, pod, f.c)
        autoStart(f, pod, true)
        val started = f.start(pod)
        assertEquals(PodPhase.DRAFTING, started.phase); assertFalse(started.autoStart)
        assertTrue(started.pendingInvitations.isEmpty()); assertEquals(2, started.members.size)
        assertTrue(f.list(f.c).invitations.isEmpty())
        f.send(f.c, "POST", "/v1/pods/$pod/accept", PodRequest(id()), 409)
        assertEquals(0, f.count(ActivityKind.POD_AUTO_START_OFF, pod))
    }

    // DRF-050, DRF-141: Prepare cube moves cards between the pod's Main and Maybe; the saved cube never changes, the
    // supply check and the draft use the prepared Main, and only the creator in setup can prepare.
    @Test fun `prepare cube moves cards between main and maybe for this pod only`() = fixture { f ->
        val names = org.asyncttwiz.server.TestPrintings.names("E2E").take(34)
        val saved = json.decodeFromString<SavedCube>(f.send(f.a, "POST", "/v1/cubes", CreateCube(id(), "Prepared cube",
            names.take(30).map { org.asyncttwiz.server.TestPrintings.entry(it, 1) } +
                names.drop(30).map { org.asyncttwiz.server.TestPrintings.entry(it, 1, SavedDeckSection.MAYBEBOARD) }), 201))
        val main = saved.entries.filter { it.section == SavedDeckSection.MAINBOARD }
        val maybe = saved.entries.filter { it.section == SavedDeckSection.MAYBEBOARD }
        val pod = json.decodeFromString<PodDetail>(f.send(f.a, "POST", "/v1/pods", CreatePod(id(), "Prepared pod", saved.cube.id,
            settings = DraftSettings(packsPerPlayer = 1, cardsPerPack = 16)), 201)).id
        assertEquals(PodSource("Prepared cube", 30, maybeCopies = 4), f.get(f.a, pod).source)
        fun cube(): PodCube = json.decodeFromString(f.request(f.a, "GET", "/v1/pods/$pod/cube").body())
        assertEquals(main.toSet(), cube().entries.filter { it.section == SavedDeckSection.MAINBOARD }.toSet())
        assertEquals(maybe.toSet(), cube().entries.filter { it.section == SavedDeckSection.MAYBEBOARD }.toSet())
        f.join(pod)
        // 2 players × 16 cards: 30 in Main is too few until Maybe cards move in.
        assertFalse(f.get(f.a, pod).supply.sufficient)
        f.send(f.b, "PUT", "/v1/pods/$pod/cube/move", MovePodCubeCards(id(), maybe[0].definition, SavedDeckSection.MAYBEBOARD), 403)
        assertEquals(403, f.request(f.b, "GET", "/v1/pods/$pod/cube").statusCode())
        assertEquals(404, f.request(f.c, "GET", "/v1/pods/$pod/cube").statusCode())
        val move = MovePodCubeCards(id(), maybe[0].definition, SavedDeckSection.MAYBEBOARD)
        val moved = f.send(f.a, "PUT", "/v1/pods/$pod/cube/move", move)
        assertEquals(moved, f.send(f.a, "PUT", "/v1/pods/$pod/cube/move", move))
        f.send(f.a, "PUT", "/v1/pods/$pod/cube/move", MovePodCubeCards(id(), maybe[1].definition, SavedDeckSection.MAYBEBOARD))
        assertEquals("not_enough_copies", json.decodeFromString<PodError>(f.send(f.a, "PUT", "/v1/pods/$pod/cube/move",
            MovePodCubeCards(id(), maybe[1].definition, SavedDeckSection.MAYBEBOARD), 409)).code)
        f.send(f.a, "PUT", "/v1/pods/$pod/cube/move", MovePodCubeCards(id(), main[0].definition, SavedDeckSection.MAINBOARD, 2), 409)
        f.send(f.a, "PUT", "/v1/pods/$pod/cube/move", MovePodCubeCards(id(), main[0].definition, SavedDeckSection.SIDEBOARD), 400)
        assertEquals(PodSource("Prepared cube", 32, maybeCopies = 2), f.get(f.a, pod).source)
        assertTrue(f.get(f.a, pod).supply.sufficient)
        // Moving one back and forth again: the card returns to the same section entry.
        f.send(f.a, "PUT", "/v1/pods/$pod/cube/move", MovePodCubeCards(id(), main[0].definition, SavedDeckSection.MAINBOARD))
        assertEquals(1, cube().entries.single { it.definition == main[0].definition }.quantity)
        assertEquals(SavedDeckSection.MAYBEBOARD, cube().entries.single { it.definition == main[0].definition }.section)
        f.send(f.a, "PUT", "/v1/pods/$pod/cube/move", MovePodCubeCards(id(), main[0].definition, SavedDeckSection.MAYBEBOARD))
        // Definitions are read for the prepared cube's cards only.
        val digests = cube().entries.map { it.definition }
        val definitions = json.decodeFromString<SubmittedDefinitions>(f.send(f.a, "POST", "/v1/pods/$pod/cube/definitions",
            ReadCubeDefinitions(digests))).definitions
        assertEquals(digests.toSet(), definitions.map { it.digest }.toSet())
        f.send(f.a, "POST", "/v1/pods/$pod/cube/definitions", ReadCubeDefinitions(listOf(f.cube.entries.last().definition)), 404)
        // The saved cube is unchanged.
        val unchanged = json.decodeFromString<SavedCube>(f.request(f.a, "GET", "/v1/cubes/${saved.cube.id}").body())
        assertEquals((main + maybe).toSet(), unchanged.entries.toSet())
        val started = f.start(pod)
        assertEquals(PodPhase.DRAFTING, started.phase)
        val drafted = (f.get(f.a, pod).draft!!.head!!.cards + f.get(f.b, pod).draft!!.head!!.cards).map { it.definition }.toSet()
        assertTrue(maybe[0].definition in drafted && maybe[1].definition in drafted, "moved Maybe cards are drafted")
        assertFalse(maybe[2].definition in drafted || maybe[3].definition in drafted, "Maybe cards are not drafted")
        f.send(f.a, "PUT", "/v1/pods/$pod/cube/move", MovePodCubeCards(id(), maybe[2].definition, SavedDeckSection.MAYBEBOARD), 409)
        // Choosing the cube again starts from the saved cube's own sections.
        val other = f.create().id
        f.send(f.a, "PUT", "/v1/pods/$other/cube/move", MovePodCubeCards(id(), f.cube.entries[0].definition, SavedDeckSection.MAINBOARD))
        assertEquals(PodSource("Snapshot cube", 89, maybeCopies = 1), f.get(f.a, other).source)
        f.send(f.a, "PUT", "/v1/pods/$other", UpdatePod(id(), cubeId = f.cube.cube.id))
        assertEquals(PodSource("Snapshot cube", 90), f.get(f.a, other).source)
    }

    // DRF-029, DRF-035, DRF-050, DRF-140
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
        // DRF-137: the notice names who invited and the pod
        val invited = f.activity(f.b).entries.single { it.kind == ActivityKind.POD_INVITATION }
        assertEquals(f.a.account.username, invited.opponentName); assertEquals(f.get(f.a, pod).name, invited.resourceName)
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
        f.send(f.a, "PUT", "/v1/cubes/${f.cube.cube.id}", UpdateCube(id(), name = "Changed", entries = f.cube.entries.take(1).map { CardListEntry(it.section, it.definition, it.quantity) }))
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

    // DRF-039, DRF-040, DRF-041
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

    // DRF-041
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

    // DRF-123
    @Test fun `challenge HTTP creates an ordinary invitation with the server's basic lands`() = fixture { f ->
        val pod = f.create().id; f.join(pod)
        f.configure(pod, DraftSettings(packsPerPlayer = 1, cardsPerPack = 1)); f.start(pod)
        for (player in listOf(f.a, f.b)) {
            val attempt = f.get(player, pod).draftAttemptId!!
            f.send(player, "PUT", "/v1/pods/$pod/build/basics", SetPodBasics(id(), attempt, PodDeckBasics(forest = 39)))
            f.send(player, "POST", "/v1/pods/$pod/submit", PodDraftRequest(id(), attempt))
        }
        val input = ChallengeFromPod(id(), f.b.account.accountId, "HTTP challenge")
        assertEquals(401, f.request(null, "POST", "/v1/pods/$pod/challenge", json.encodeToString(input)).statusCode())
        val result = f.send(f.a, "POST", "/v1/pods/$pod/challenge", input, 201)
        assertEquals(result, f.send(f.a, "POST", "/v1/pods/$pod/challenge", input, 201))
        val summary = json.decodeFromString<GameSummary>(result)
        assertEquals(pod, summary.podId); assertEquals(InvitationState.PENDING, summary.invitationState)
        assertEquals(listOf(summary), json.decodeFromString<GameList>(f.request(f.b, "GET", "/v1/invitations").body()).games)
        f.send(f.a, "DELETE", "/v1/pods/$pod", PodRequest(id()))
        assertTrue(json.decodeFromString<GameList>(f.request(f.b, "GET", "/v1/invitations").body()).games.isEmpty())
        assertFalse(f.activity(f.b).entries.any { it.kind == ActivityKind.INVITATION && it.gameId == summary.id })
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

    // DRF-030
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
        val replacement = json.decodeFromString<SavedCube>(f.send(f.a, "POST", "/v1/cubes", CreateCube(id(), "New source", org.asyncttwiz.server.TestPrintings.names("E2E").take(2).map { org.asyncttwiz.server.TestPrintings.entry(it, 4) }), 201))
        f.send(f.a, "PUT", "/v1/pods/$pod", UpdatePod(id(), name = "Renamed", cubeId = replacement.cube.id))
        assertEquals(PodSource("New source", 8), f.get(f.b, pod).source)
        assertEquals("Renamed", f.get(f.b, pod).name)
        f.send(f.b, "PUT", "/v1/pods/$pod", UpdatePod(id(), cubeId = f.cube.cube.id), 403)
        f.send(f.a, "PUT", "/v1/pods/$pod", UpdatePod(id(), settings = DraftSettings(packsOpenAtOnce = 0)), 400)
    }

    private fun finishGrid(f: Fixture, pod: String, players: List<SessionCreated>) {
        var actions = 0
        while (f.get(f.a, pod).phase == PodPhase.DRAFTING) {
            val before = players.associateWith { f.get(it, pod) }
            for ((player, detail) in before) {
                assertNull(detail.draft)
                // Each member sees the grids of the rounds they have opened; their pickable grids are all among them.
                val public = detail.grid!!.publicView
                assertEquals(before.values.first().grid!!.publicView.seating, public.seating)
                assertEquals(player.account.accountId, detail.grid!!.mine.player)
                val queue = detail.grid!!.mine.queue
                assertEquals(queue.firstOrNull(), detail.grid!!.mine.headGridId)
                assertEquals(public.grids.filter { it.currentPlayer == player.account.accountId }.map { it.id }.toSet(), queue.toSet())
                assertEquals(detail.grid!!.mine.pool.map { it.id }.toSet(), detail.build!!.copies.map { it.copyId }.toSet())
                assertEquals(detail.grid!!.mine.pool.size, detail.build!!.copies.size)
                val waiting = queue.size
                // The count adds next-round grids the member would open themselves; positive exactly with a queue.
                val count = detail.members.single { it.account == player.account }.packsWaiting
                assertTrue(count >= waiting)
                assertEquals(waiting > 0, count > 0)
                assertEquals(count, f.list(player).pods.single { it.id == pod }.packsWaiting)
                assertEquals(waiting > 0, detail.members.single { it.account == player.account }.picking)
                val hints = f.activity(player).entries.filter { it.gameId == pod && it.kind == ActivityKind.POD_YOUR_PICK }
                assertEquals(waiting > 0, hints.isNotEmpty())
                if (waiting > 0) assertEquals(detail.draftAttemptId, hints.single().identity)
                assertEquals(if (waiting > 0) PodAttention.YOUR_PICK else PodAttention.NONE,
                    f.list(player).pods.single { it.id == pod }.attention)
                val visible = (public.grids + public.finishedGrids).flatMap { it.slots.mapNotNull { slot -> slot.copy } } +
                    public.log.flatMap { it.kept + it.discarded } + detail.grid!!.mine.pool
                assertTrue((public.grids + public.finishedGrids).all { it.slots.size == 9 })
                assertEquals(visible.map { it.definition }.toSet(), detail.definitions.map { it.digest }.toSet())
                val payload = json.encodeToString(detail)
                assertFalse(payload.contains("sourceSnapshot")); assertFalse(payload.contains("\"supply\":["))
                assertFalse(payload.contains("turns")); assertFalse(payload.contains("submittedDeck"))
            }
            val player = players.first { before.getValue(it).grid!!.mine.headGridId != null }
            val counts = players.associateWith { f.count(ActivityKind.POD_YOUR_PICK, pod, it) }
            f.select(player, pod)
            for (other in players) {
                // A hint when grids reach an empty queue; the picker's just-picked grid does not count.
                val had = before.getValue(other).grid!!.mine.queue
                val has = f.get(other, pod).grid!!.mine.queue
                assertEquals(counts.getValue(other) + if (has.isNotEmpty() && had.none { it in has }) 1 else 0,
                    f.count(ActivityKind.POD_YOUR_PICK, pod, other))
            }
            check(++actions < 100)
        }
        val finished = players.map { f.get(it, pod) }
        assertTrue(finished.all { it.grid!!.publicView.finished && it.grid!!.publicView.grids.isEmpty() })
        val copies = finished.flatMap { it.grid!!.mine.pool }
        assertEquals(copies.size, copies.map { it.id }.distinct().size)
        assertEquals(players.size, f.count(ActivityKind.POD_DRAFT_COMPLETE, pod))
        for (player in players) {
            val detail = f.get(player, pod)
            assertEquals(PodAttention.BUILD, f.list(player).pods.single { it.id == pod }.attention)
            val page = f.activity(player, "0")
            assertFalse(page.entries.any { it.kind == ActivityKind.POD_YOUR_PICK && it.gameId == pod })
            assertEquals(detail.draftAttemptId, page.entries.single { it.kind == ActivityKind.POD_DRAFT_COMPLETE && it.gameId == pod }.identity)
        }
    }

    // DRF-136
    @Test fun `two player Grid HTTP two picks parallel boards private builds notifications and submit`() = fixture { f ->
        val pod = f.create(DraftMode.GRID).id; f.join(pod)
        assertEquals(DraftMode.GRID, f.get(f.a, pod).settings.mode)
        f.configure(pod, DraftSettings.grid().copy(grids = 5, gridsOpenAtOnce = 3, twoPicks = true))
        val started = f.start(pod)
        assertEquals(3, started.grid!!.publicView.grids.size)
        assertEquals(2, f.count(ActivityKind.POD_YOUR_PICK, pod)) // Not one hint per board.
        assertEquals(404, f.request(f.c, "GET", "/v1/pods/$pod").statusCode())
        val player = listOf(f.a, f.b).single { it.account.accountId == started.grid!!.publicView.grids.first().currentPlayer }
        val selection = f.gridSelection(player, pod)
        val attempts = List(2) { CompletableFuture.supplyAsync { f.send(player, "POST", "/v1/pods/$pod/grid/select", selection) } }
        assertEquals(attempts[0].join(), attempts[1].join())
        val after = f.get(player, pod)
        assertEquals(3, after.grid!!.mine.pool.size)
        assertEquals(9, after.grid!!.publicView.grids.first().positions.filterNotNull().size) // First-pick refill.
        val copy = after.grid!!.mine.pool.first().id
        val other = listOf(f.a, f.b).single { it != player }
        f.send(other, "PUT", "/v1/pods/$pod/build/move", MovePodCopy(id(), selection.draftAttemptId, copy, PodDeckCategory.MAYBE), 400)
        f.send(player, "PUT", "/v1/pods/$pod/build/move", MovePodCopy(id(), selection.draftAttemptId, copy, PodDeckCategory.SIDE))
        finishGrid(f, pod, listOf(f.a, f.b))
        val complete = f.get(player, pod)
        assertEquals(PodDeckCategory.SIDE, complete.build!!.copies.single { it.copyId == copy }.category)
        assertEquals(60, listOf(f.a, f.b).sumOf { f.get(it, pod).grid!!.mine.pool.size })
        f.send(player, "PUT", "/v1/pods/$pod/build/basics", SetPodBasics(id(), selection.draftAttemptId, PodDeckBasics(plains = 40)))
        f.send(player, "POST", "/v1/pods/$pod/submit", PodDraftRequest(id(), selection.draftAttemptId))
        assertTrue(f.get(player, pod).submitted); assertNull(f.get(other, pod).deck)
    }

    // DRF-080
    @Test fun `Grid HTTP opens a member's next round early with attention and a hint while the other still picks`() = fixture { f ->
        val pod = f.create(DraftMode.GRID).id; f.join(pod)
        f.configure(pod, DraftSettings.grid().copy(grids = 6, gridsOpenAtOnce = 3))
        val seating = f.start(pod).grid!!.publicView.seating
        val (first, second) = seating.map { id -> listOf(f.a, f.b).single { it.account.accountId == id } }
        f.select(first, pod); f.select(first, pod) // Grids 1 and 3, where the first seat picks first.
        f.select(second, pod) // Grid 2.
        val hints = f.count(ActivityKind.POD_YOUR_PICK, pod, first)
        f.select(first, pod) // The first seat's last pick of round 1; only the second seat's picks of grids 1 and 3 remain.
        val early = f.get(first, pod).grid!!
        assertEquals(listOf("grid-4"), early.mine.queue)
        assertEquals(2, early.mine.round)
        assertEquals(listOf(5), early.publicView.grids.filter { it.round == 2 }.map { it.number })
        assertEquals(PodAttention.YOUR_PICK, f.list(first).pods.single { it.id == pod }.attention)
        assertEquals(hints + 1, f.count(ActivityKind.POD_YOUR_PICK, pod, first))
        assertTrue(f.activity(first).entries.any { it.gameId == pod && it.kind == ActivityKind.POD_YOUR_PICK })
        val behind = f.get(second, pod)
        assertEquals(1, behind.grid!!.mine.round)
        assertEquals(listOf("grid-0", "grid-2"), behind.grid!!.mine.queue)
        assertTrue(behind.grid!!.publicView.grids.none { it.round == 2 })
        assertEquals(1, behind.members.single { it.account == first.account }.packsWaiting)
        f.select(first, pod) // Grid 5 waits unseen for the second seat.
        assertEquals(PodAttention.NONE, f.list(first).pods.single { it.id == pod }.attention)
        assertEquals(listOf("grid-0", "grid-2"), f.get(second, pod).grid!!.mine.queue)
        finishGrid(f, pod, listOf(f.a, f.b))
    }

    // DRF-079
    @Test fun `three player Grid HTTP refills follows Draftmancer's turn order and completes parallel rounds`() = fixture { f ->
        val pod = f.create(DraftMode.GRID).id; f.join(pod); f.join(pod, f.c)
        f.configure(pod, DraftSettings.grid().copy(grids = 5, gridsOpenAtOnce = 2))
        val started = f.start(pod)
        assertEquals(DraftSupply(60, 90), started.supply)
        assertEquals(2, f.count(ActivityKind.POD_YOUR_PICK, pod))
        finishGrid(f, pod, listOf(f.a, f.b, f.c))
        val view = f.get(f.a, pod).grid!!.publicView
        assertEquals(15, view.log.size)
        val orders = listOf(listOf(0, 1, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(0, 2, 1), listOf(2, 1, 0))
        for (number in 0..4) {
            val log = view.log.filter { it.gridId == "grid-$number" }
            assertEquals(orders[number].map { view.seating[it] }, log.map { it.player })
        }
        assertEquals(45, listOf(f.a, f.b, f.c).sumOf { f.get(it, pod).grid!!.mine.pool.size })
    }

    @Test fun `Grid HTTP rejections supply mode locking cancellation retries and expired attempts`() = fixture { f ->
        val pod = f.create(DraftMode.GRID).id; f.join(pod)
        f.send(f.a, "POST", "/v1/pods/$pod/start", PodRequest(id()), 409) // 18 grids exceed the cube.
        f.configure(pod, DraftSettings.grid().copy(grids = 3, gridsOpenAtOnce = 3))
        val started = f.start(pod)
        val players = listOf(f.a, f.b)
        val first = players.single { it.account.accountId == started.grid!!.publicView.grids.first().currentPlayer }
        val other = players.single { it != first }
        val input = f.gridSelection(first, pod)
        fun reject(who: SessionCreated, request: SelectPodGrid, code: String) {
            assertEquals(code, json.decodeFromString<PodError>(f.send(who, "POST", "/v1/pods/$pod/grid/select", request, 409)).code)
        }
        reject(other, input.copy(requestId = id()), "not_your_turn")
        reject(first, input.copy(requestId = id(), gridId = "grid-2"), "not_head_grid")
        reject(first, input.copy(requestId = id(), index = 3), "invalid_line")
        reject(first, input.copy(requestId = id(), index = -1), "invalid_line")
        reject(first, input.copy(requestId = id(), gridId = "missing"), "not_your_turn")
        f.send(f.c, "POST", "/v1/pods/$pod/grid/select", input.copy(requestId = id()), 404)
        f.send(first, "POST", "/v1/pods/$pod/pick", PickPod(id(), input.draftAttemptId, "pack-0", emptyList()), 409)
        val picked = f.send(first, "POST", "/v1/pods/$pod/grid/select", input)
        assertEquals(picked, f.send(first, "POST", "/v1/pods/$pod/grid/select", input))
        reject(first, input.copy(index = 1), "request_conflict")
        // The other member's own fresh grid comes before the grid just passed to them.
        reject(other, input.copy(requestId = id()), "not_head_grid")
        f.select(other, pod)
        reject(other, input.copy(requestId = id()), "empty_line")
        assertEquals(3, f.get(other, pod).grid!!.mine.pool.size)
        val cancel = PodDraftRequest(id(), input.draftAttemptId)
        f.send(f.a, "POST", "/v1/pods/$pod/cancel", cancel)
        val reset = f.get(f.a, pod)
        assertNull(reset.grid); assertNull(reset.build)
        reject(first, input, "draft_attempt_expired")
        assertFalse(f.activity(first, "0").entries.any { it.kind == ActivityKind.POD_YOUR_PICK })
        val next = f.start(pod)
        reject(first, input.copy(requestId = id()), "draft_attempt_expired")
        assertNotEquals(input.draftAttemptId, next.draftAttemptId)
        assertTrue(next.grid!!.mine.pool.isEmpty())
        f.send(f.b, "POST", "/v1/pods/$pod/leave", PodRequest(id()))
        assertNull(f.get(f.a, pod).grid)
        // A booster pod cannot accept grid selections.
        val booster = f.create().id; f.join(booster)
        f.configure(booster, DraftSettings(packsPerPlayer = 1, cardsPerPack = 3))
        val draft = f.start(booster)
        assertEquals("wrong_draft_mode", json.decodeFromString<PodError>(f.send(f.a, "POST", "/v1/pods/$booster/grid/select",
            input.copy(requestId = id(), draftAttemptId = draft.draftAttemptId!!), 409)).code)
    }

    // DRF-169: a pod's set is named by its code alone
    @Test fun `a pod's source is one cube or one set code`() = fixture { f ->
        f.send(f.a, "POST", "/v1/pods", CreatePod(id(), "No source"), 400)
        f.send(f.a, "POST", "/v1/pods", CreatePod(id(), "Two sources", f.cube.cube.id, setCode = "TST"), 400)
        val oversized = " ".repeat(65537)
        assertEquals(413, f.request(f.a, "POST", "/v1/pods", oversized).statusCode())
        val pod = f.create().id
        assertEquals(413, f.request(f.a, "PUT", "/v1/pods/$pod", oversized).statusCode())
        assertEquals(413, f.request(f.a, "POST", "/v1/pods/$pod/start", oversized).statusCode())
        f.send(f.a, "PUT", "/v1/pods/$pod", UpdatePod(id(), cubeId = f.cube.cube.id, setCode = "TST"), 400)
        assertEquals(PodSourceKind.CUBE, f.get(f.a, pod).source.kind)
        f.send(f.a, "PUT", "/v1/pods/$pod", UpdatePod(id(), setCode = "TST"))
        assertEquals(PodSourceKind.SET, f.get(f.a, pod).source.kind)
        f.send(f.a, "PUT", "/v1/pods/$pod", UpdatePod(id(), cubeId = f.cube.cube.id))
        assertEquals(PodSource("Snapshot cube", 90), f.get(f.a, pod).source)
    }

    // DRF-090: recipe packs from the catalog's set, and challenges from a set pod
    @Test fun `set boosters use the recipe size and allow challenges`() = fixture { f ->
        run {
            val request = CreatePod(id(), "Set pod", setCode = "TST")
            val created = f.send(f.a, "POST", "/v1/pods", request, 201)
            assertEquals(created, f.send(f.a, "POST", "/v1/pods", request, 201))
            val pod = json.decodeFromString<PodDetail>(created).id
            assertEquals(PodSource(kind = PodSourceKind.SET, setCode = "TST", setName = "Test Set",
                boosterKind = SetBoosterKind.RECIPE_BASED, boosterName = "play"), f.get(f.a, pod).source)
            f.join(pod)
            f.configure(pod, DraftSettings(packsPerPlayer = 1, cardsPerPack = 7))
            val started = f.start(pod)
            assertTrue(started.supply.sufficient)
            val packSize = 4
            assertEquals(packSize, started.draft!!.head!!.cards.size)
            assertNull(started.grid)
            f.send(f.a, "PUT", "/v1/pods/$pod", UpdatePod(id(), setCode = "TWL"), 409)
            val all = started.draft!!.head!!.cards
            assertTrue(all.all { card -> started.definitions.any { it.digest == card.definition } })
            f.finish(pod, listOf(f.a, f.b))
            assertEquals(2 * packSize, listOf(f.a, f.b).sumOf { f.get(it, pod).draft!!.pool.size })
            assertPrivacy(f, pod)
            for (player in listOf(f.a, f.b)) {
                val attempt = f.get(player, pod).draftAttemptId!!
                f.send(player, "PUT", "/v1/pods/$pod/build/basics", SetPodBasics(id(), attempt, PodDeckBasics(forest = 40)))
                f.send(player, "POST", "/v1/pods/$pod/submit", PodDraftRequest(id(), attempt))
            }
            val result = f.send(f.a, "POST", "/v1/pods/$pod/challenge", ChallengeFromPod(id(), f.b.account.accountId, "Set challenge"), 201)
            val game = json.decodeFromString<GameSummary>(result)
            assertEquals(pod, game.podId)
            assertEquals(InvitationState.PENDING, game.invitationState)
        }
    }

    // DRF-119
    @Test fun `oversized submitted deck is rejected without locking and can be corrected`() = fixture { f ->
        val pod = f.create().id
        f.join(pod)
        f.configure(pod, DraftSettings(packsPerPlayer = 1, cardsPerPack = 1))
        val started = f.start(pod)
        f.finish(pod, listOf(f.a, f.b))
        val attempt = started.draftAttemptId!!
        // The unlimited Side starts with 17 of each basic, and those count toward the deck size too.
        val fixedCount = f.get(f.a, pod).build!!.let { it.copies.size + it.sideBasics.total.toInt() }
        assertEquals(85, fixedCount - f.get(f.a, pod).build!!.copies.size)
        f.send(f.a, "PUT", "/v1/pods/$pod/build/basics", SetPodBasics(id(), attempt, PodDeckBasics(plains = 1001 - fixedCount)))
        val response = f.send(f.a, "POST", "/v1/pods/$pod/submit", PodDraftRequest(id(), attempt), 400)
        assertEquals("deck_too_large", json.decodeFromString<PodError>(response).code)
        assertFalse(f.get(f.a, pod).submitted)
        f.send(f.a, "PUT", "/v1/pods/$pod/build/basics", SetPodBasics(id(), attempt, PodDeckBasics(plains = 1000 - fixedCount)))
        f.send(f.a, "POST", "/v1/pods/$pod/submit", PodDraftRequest(id(), attempt))
        assertTrue(f.get(f.a, pod).submitted)
    }

    // DRF-090
    @Test fun `set Grid generates cards with unlimited supply and completes`() = fixture { f ->
        val pod = f.create(DraftMode.GRID).id
        f.join(pod)
        val replacement = UpdatePod(id(), setCode = "TWL",
            settings = DraftSettings.grid().copy(grids = 2, twoPicks = true, gridsOpenAtOnce = 2, freshBoosters = false))
        val updated = f.send(f.a, "PUT", "/v1/pods/$pod", replacement)
        assertEquals(updated, f.send(f.a, "PUT", "/v1/pods/$pod", replacement))
        val started = f.start(pod)
        assertTrue(started.supply.sufficient)
        assertEquals(9, started.grid!!.publicView.grids.first().positions.count { it != null })
        finishGrid(f, pod, listOf(f.a, f.b))
        assertEquals(24, listOf(f.a, f.b).sumOf { f.get(it, pod).grid!!.mine.pool.size })
        for (player in listOf(f.a, f.b)) {
            val attempt = f.get(player, pod).draftAttemptId!!
            f.send(player, "PUT", "/v1/pods/$pod/build/basics", SetPodBasics(id(), attempt, PodDeckBasics(forest = 40)))
            f.send(player, "POST", "/v1/pods/$pod/submit", PodDraftRequest(id(), attempt))
        }
        val game = json.decodeFromString<GameSummary>(f.send(f.a, "POST", "/v1/pods/$pod/challenge",
            ChallengeFromPod(id(), f.b.account.accountId, "Grid challenge"), 201))
        assertEquals(pod, game.podId)
    }

    // DRF-091
    @Test fun `set Grid with fresh boosters gives each grid random cards from its own booster`() = fixture { f ->
        val pod = f.create(DraftMode.GRID).id
        f.join(pod)
        assertTrue(f.get(f.a, pod).settings.freshBoosters) // on by default
        val settings = DraftSettings.grid().copy(grids = 2, twoPicks = true, gridsOpenAtOnce = 2)
        f.send(f.a, "PUT", "/v1/pods/$pod", UpdatePod(id(), setCode = "TST", settings = settings.copy(freshBoosters = false)))
        assertFalse(f.get(f.b, pod).settings.freshBoosters)
        f.send(f.a, "PUT", "/v1/pods/$pod", UpdatePod(id(), settings = settings))
        assertTrue(f.get(f.b, pod).settings.freshBoosters)
        val started = f.start(pod)
        val grids = started.grid!!.publicView.grids
        assertEquals(2, grids.size)
        // The test set's booster holds 4 cards: each grid takes all of its own booster and is topped up from spare boosters.
        grids.forEachIndexed { index, grid ->
            val boosters = grid.positions.map { it!!.id.substringBefore(":copy:") }
            assertEquals(4, boosters.count { it == "set-pack-$index" })
            assertTrue(boosters.filter { it != "set-pack-$index" }.all { it.removePrefix("set-pack-").toInt() >= 2 })
        }
        finishGrid(f, pod, listOf(f.a, f.b))
        assertEquals(24, listOf(f.a, f.b).sumOf { f.get(it, pod).grid!!.mine.pool.size })
    }

    // DRF-169, DRF-090 (docs/specs/web-client.md section 3.7): a set pod from its code, its packs built from the server's
    // card catalog (the test cards' catalog, tests/fixtures/card-catalog)
    @Test fun `a set chosen by its code gets packs the server builds from its card catalog`() = fixture { f ->
        val created = json.decodeFromString<PodDetail>(f.send(f.a, "POST", "/v1/pods", CreatePod(id(), "Catalog set", setCode = "TST"), 201))
        assertEquals(PodSource(kind = PodSourceKind.SET, setCode = "TST", setName = "Test Set", boosterKind = SetBoosterKind.RECIPE_BASED,
            boosterName = "play"), created.source)
        f.join(created.id)
        val started = f.start(created.id)
        val pack = started.draft!!.head!!.cards
        assertEquals(4, pack.size, "the recipe's 2 commons, 1 uncommon and 1 rare")
        val names = started.definitions.associate { it.digest to it.definition.name }
        assertEquals(setOf("Test Bear", "Test Caller", "Test Bolt", "Test Knight"), pack.map { names.getValue(it.definition) }.toSet())
        assertTrue(started.definitions.all { it.definition.provider == "mtgjson" && it.definition.sourceVersion == "2026-10-31/5.3.0+20261031" })
        // Switching an unstarted pod to another catalog set
        val other = f.create().id
        f.send(f.a, "PUT", "/v1/pods/$other", UpdatePod(id(), setCode = "TWL"))
        assertEquals("Thornwall Crossing", f.get(f.a, other).source.setName)
        f.send(f.a, "PUT", "/v1/pods/$other", UpdatePod(id(), setCode = "TWL", cubeId = f.cube.cube.id), 400)
    }

    // DRF-162, SYS-082 (section 3.8): the server refuses a set whose packs can hold a card Automatum can't play, by the catalog's
    // marks, and a set its catalog doesn't have to draft
    @Test fun `a set the catalog's marks say can't be drafted is refused`() = fixture { f ->
        fun refused(input: CreatePod, status: Int, code: String) =
            assertEquals(code, json.decodeFromString<PodError>(f.send(f.a, "POST", "/v1/pods", input, status)).code)
        refused(CreatePod(id(), "Broken", setCode = "BAD"), 409, "automatum_unsupported")
        refused(CreatePod(id(), "Unlisted", setCode = "OFF"), 409, "automatum_unsupported")
        refused(CreatePod(id(), "Promos", setCode = "DCK"), 404, "set_not_found")
        refused(CreatePod(id(), "Unknown", setCode = "NOPE"), 404, "set_not_found")
        val pod = f.create().id
        assertEquals("automatum_unsupported", json.decodeFromString<PodError>(
            f.send(f.a, "PUT", "/v1/pods/$pod", UpdatePod(id(), setCode = "BAD"), 409)).code)
        assertEquals(PodSourceKind.CUBE, f.get(f.a, pod).source.kind)
        // A set the catalog can draft keeps its support from the marks: nothing Automatum can't play
        f.send(f.a, "PUT", "/v1/pods/$pod", UpdatePod(id(), setCode = "TST"))
        assertEquals(PodSourceKind.SET, f.get(f.a, pod).source.kind)
        // A set whose only unsupported cards are on a bonus sheet from another set can be drafted (CatalogPackSheets)
        f.send(f.a, "PUT", "/v1/pods/$pod", UpdatePod(id(), setCode = "BON"))
        assertEquals("Bonus Sheet Set", f.get(f.a, pod).source.setName)
    }

    private fun Fixture.chat(who: SessionCreated, pod: String, query: String = ""): PodChatPage {
        val response = request(who, "GET", "/v1/pods/$pod/chat$query")
        assertEquals(200, response.statusCode(), response.body())
        return json.decodeFromString(response.body())
    }
    private fun Fixture.say(who: SessionCreated, pod: String, text: String, requestId: String = id(), status: Int = 200): String =
        send(who, "POST", "/v1/pods/$pod/chat", SendPodChat(requestId, text), status)

    // DRF-170, DRF-171: members write and read the pod's chat in order; the server keeps each member's read cursor (only
    // forward), counts the other members' messages after it, and a retried send is stored once
    @Test fun `pod chat keeps messages in order with private read cursors and once-only sends`() = fixture { f ->
        val pod = f.create().id
        f.join(pod)
        assertEquals(PodChatPage(pod, emptyList(), emptyList(), PodChatRead(pod)), f.chat(f.a, pod))
        val request = id()
        val first = json.decodeFromString<PodChatMessage>(f.say(f.a, pod, "  Hello\n😀  ", request))
        assertEquals("  Hello\n😀  ", first.text)
        assertEquals("1", first.ordinal)
        assertEquals(first, json.decodeFromString<PodChatMessage>(f.say(f.a, pod, "  Hello\n😀  ", request)), "a retry is the same message")
        assertEquals("request_conflict", json.decodeFromString<PodError>(f.say(f.a, pod, "Other", request, 409)).code)
        f.say(f.b, pod, "Hi Alice")
        f.say(f.a, pod, "Ready?")
        val bob = f.chat(f.b, pod)
        assertEquals(listOf("  Hello\n😀  ", "Hi Alice", "Ready?"), bob.messages.map { it.text })
        assertEquals(listOf("1", "2", "3"), bob.messages.map { it.ordinal })
        assertEquals(setOf(f.a.account.accountId, f.b.account.accountId), bob.authors.map { it.accountId }.toSet())
        // His own message counts as read through it; Alice's later one is unread
        assertEquals(PodChatRead(pod, "2", "3", 1), bob.read)
        assertEquals(PodChatRead(pod, "3", "3", 0), f.chat(f.a, pod).read)
        assertEquals(listOf("Ready?"), f.chat(f.b, pod, "?after=2").messages.map { it.text })
        assertEquals(listOf("  Hello\n😀  "), f.chat(f.b, pod, "?before=2").messages.map { it.text })
        assertEquals(1, f.list(f.b).pods.single().chatUnread)
        val read = json.decodeFromString<PodChatRead>(f.send(f.b, "PUT", "/v1/pods/$pod/chat/read", ReadChat(bob.messages.last().id)))
        assertEquals(PodChatRead(pod, "3", "3", 0), read)
        // The cursor never moves back
        assertEquals(read, json.decodeFromString<PodChatRead>(f.send(f.b, "PUT", "/v1/pods/$pod/chat/read", ReadChat(first.id))))
        assertEquals(read, json.decodeFromString<PodChatRead>(f.request(f.b, "GET", "/v1/pods/$pod/chat/read").body()))
        assertEquals(0, f.list(f.b).pods.single().chatUnread)
        f.send(f.b, "PUT", "/v1/pods/$pod/chat/read", ReadChat(id()), 404)
        // A game chat's limits: no blank text, nothing above its size
        f.say(f.a, pod, " \n ", status = 400)
        f.say(f.a, pod, "x".repeat(DeckValidation.FIELD_BYTES + 1), status = 400)
        f.say(f.a, pod, "x".repeat(DeckValidation.FIELD_BYTES))
    }

    // DRF-171: only members read, write or move a cursor; an invitee doesn't, a member who leaves loses the chat (their
    // messages stay with their name), and the chat goes with the pod
    @Test fun `pod chat is for members only and a member who leaves loses it`() = fixture { f ->
        val pod = f.create().id
        f.send(f.a, "POST", "/v1/pods/$pod/invitations", InviteToPod(id(), f.b.account.accountId))
        val hello = json.decodeFromString<PodChatMessage>(f.say(f.a, pod, "Welcome"))
        fun refused(who: SessionCreated) {
            assertEquals(404, f.request(who, "GET", "/v1/pods/$pod/chat").statusCode())
            assertEquals(404, f.request(who, "GET", "/v1/pods/$pod/chat/read").statusCode())
            f.say(who, pod, "Let me in", status = 404)
            f.send(who, "PUT", "/v1/pods/$pod/chat/read", ReadChat(hello.id), 404)
        }
        refused(f.b); refused(f.c)
        assertEquals(401, f.request(null, "GET", "/v1/pods/$pod/chat").statusCode())
        f.send(f.b, "POST", "/v1/pods/$pod/accept", PodRequest(id()))
        assertEquals(listOf("Welcome"), f.chat(f.b, pod).messages.map { it.text })
        f.say(f.b, pod, "Thanks")
        f.send(f.b, "POST", "/v1/pods/$pod/leave", PodRequest(id()))
        refused(f.b)
        val left = f.chat(f.a, pod)
        assertEquals(listOf("Welcome", "Thanks"), left.messages.map { it.text })
        assertTrue(left.authors.any { it.accountId == f.b.account.accountId })
        f.send(f.a, "DELETE", "/v1/pods/$pod", PodRequest(id()))
        assertEquals(404, f.request(f.a, "GET", "/v1/pods/$pod/chat").statusCode())
        assertEquals(0, f.db.connection().use { connection ->
            connection.createStatement().executeQuery("select count(*) from pod_chat").use { rows -> rows.next(); rows.getInt(1) }
        })
    }

    // DRF-172: a message notifies every other member ("New message", Draft activity), never its author; once read, the
    // notice is no longer in the feed
    @Test fun `a pod chat message notifies the other members until they read it`() = fixture { f ->
        val pod = f.create().id
        f.join(pod); f.join(pod, f.c)
        val message = json.decodeFromString<PodChatMessage>(f.say(f.a, pod, "Draft tonight?"))
        assertEquals(0, f.count(ActivityKind.POD_MESSAGE, pod, f.a))
        for (who in listOf(f.b, f.c)) {
            assertEquals(1, f.count(ActivityKind.POD_MESSAGE, pod, who))
            val notice = f.activity(who, "0").entries.single { it.kind == ActivityKind.POD_MESSAGE }
            assertEquals(pod, notice.gameId); assertEquals(message.id, notice.identity); assertEquals(f.a.account.accountId, notice.authorId)
            assertEquals(ActivityTarget(pod, ActivityDestination.POD), f.target(who, pod, ActivityKind.POD_MESSAGE))
        }
        f.send(f.b, "PUT", "/v1/pods/$pod/chat/read", ReadChat(message.id))
        assertTrue(f.activity(f.b, "0").entries.none { it.kind == ActivityKind.POD_MESSAGE })
        assertEquals(1, f.activity(f.c, "0").entries.count { it.kind == ActivityKind.POD_MESSAGE })
    }
}
