package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ArtworkTest {
    @TempDir lateinit var temporary: Path
    private val fixtures get() = File(System.getProperty("catalog.fixtures"))
    private val id = "2487c5bd-8d7f-5925-af7a-a2f6e5eb6b7a"
    private val origin = "http://127.0.0.1:12345"
    private val policy get() = ArtworkUrlPolicy(origin)
    private var clock = 1_000_000L
    private fun cache(name: String = "art", budget: Long = 256L * 1024 * 1024) =
        ArtworkDiskCache(temporary.resolve(name).toFile(), budget) { clock }
    private fun repository(transport: ArtworkTransport, disk: ArtworkDiskCache = cache()) =
        HttpArtworkRepository(disk, transport, policy, "$origin/cards/", { clock })
    private fun png() = File(fixtures, "prism/v1/art/$id.png").readBytes()

    @Test fun normalAndArtCropResolveExactFacesAndHaveIndependentPersistentCacheKeys() {
        val card = """{"object":"card","id":"$id",
            "image_uris":{"normal":"$origin/front","art_crop":"$origin/front-art"},
            "card_faces":[{}, {"image_uris":{"normal":"$origin/back","art_crop":"$origin/back-art"}}]}""".toByteArray()
        val urls = mutableListOf<String>()
        val disk = cache()
        val art = repository(ArtworkTransport { url, maximum, _, _ ->
            urls += url
            if (url == "$origin/cards/$id") ArtworkResponse(200, card, "application/json") else {
                assertEquals(HttpArtworkRepository.MAX_IMAGE, maximum)
                ArtworkResponse(200, png() + url.toByteArray(), "image/png")
            }
        }, disk)
        val references = listOf(ArtworkReference(id, 0, "v1"), ArtworkReference(id, 0, "v1", "art_crop"),
            ArtworkReference(id, 1, "v1"), ArtworkReference(id, 1, "v1", "art_crop"))
        val expectedUrls = listOf("$origin/front", "$origin/front-art", "$origin/back", "$origin/back-art")
        assertEquals(4, references.map { it.key }.distinct().size)
        references.zip(expectedUrls).forEach { (reference, url) ->
            assertArrayEquals(png() + url.toByteArray(), art.load(reference))
        }
        assertEquals(listOf("$origin/cards/$id") + expectedUrls, urls)
        val offline = repository(ArtworkTransport { _, _, _, _ -> error("offline") }, disk)
        references.zip(expectedUrls).forEach { (reference, url) ->
            assertArrayEquals(png() + url.toByteArray(), offline.load(reference))
        }
    }

    @Test fun sizesRemainSeparateEvenWhenProviderReturnsTheSameImageUrl() {
        val card = """{"object":"card","id":"$id","image_uris":{"normal":"$origin/image","art_crop":"$origin/image"}}""".toByteArray()
        var imageCalls = 0
        val art = repository(ArtworkTransport { url, _, _, _ ->
            if (url == "$origin/cards/$id") ArtworkResponse(200, card, "application/json") else {
                imageCalls++
                ArtworkResponse(200, png(), "image/png")
            }
        })
        val normal = ArtworkReference(id, 0, "v1")
        val crop = normal.copy(size = "art_crop")
        repeat(2) { assertNotNull(art.load(normal)); assertNotNull(art.load(crop)) }
        assertEquals(2, imageCalls)
    }

    @Test fun missingOrDisallowedArtCropNeverSubstitutesNormalOrBypassesPolicy() {
        for (imageUris in listOf("""{"normal":"$origin/full"}""",
            """{"normal":"$origin/full","art_crop":"http://169.254.169.254/art"}""")) {
            var calls = 0
            val art = repository(ArtworkTransport { _, _, _, _ ->
                calls++
                ArtworkResponse(200, """{"object":"card","id":"$id","image_uris":$imageUris}""".toByteArray(), "application/json")
            }, cache("policy-${imageUris.length}"))
            assertNull(art.load(ArtworkReference(id, 0, "v1", "art_crop")))
            assertEquals(1, calls)
        }
    }

    @Test fun realCatalogBothFamiliesVersionsAndExactFacesWithoutEagerDownload() {
        for (family in listOf("prism", "quilt")) for (edition in 1..2) {
            val base = File(fixtures, "$family/v$edition")
            val records = CatalogRepository(temporary.resolve("$family$edition").toFile()) {
                JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}")
            }
            val metadata = File(base, "Meta.json").inputStream().use { MtgJsonCatalog().metadata(it) }
            records.stage { index -> File(base, "AllPrintings.json").inputStream().use { MtgJsonCatalog().import(it, index, metadata) } }
            var requests = 0
            val transport = ArtworkTransport { url, _, _, _ ->
                requests++
                val path = java.net.URI(url).path
                if (path.startsWith("/cards/")) {
                    val bytes = File(base, "scryfall/cards/${path.substringAfterLast('/')}.json").readText()
                        .replace("http://synthetic-source:8080", origin).toByteArray()
                    ArtworkResponse(200, bytes, "application/json")
                } else ArtworkResponse(200, File(fixtures, path.removePrefix("/")).readBytes(), "image/png")
            }
            val disk = cache("art-$family$edition")
            val art = repository(transport, disk)
            assertEquals(0, requests)
            val faceRecords = JsonParser.parseString(File(base, "AllPrintings.json").readText()).asJsonObject
                .getAsJsonObject("data").entrySet().flatMap { it.value.asJsonObject.getAsJsonArray("cards").toList() }
                .filter { it.asJsonObject.has("side") }.map { records.printing(it.asJsonObject.get("uuid").asString)!! }
            assertEquals(2, faceRecords.size)
            for (record in faceRecords) {
                val reference = ArtworkReference.from(record, records.metadata()!!.checksum + records.metadata()!!.date)!!
                val metadata = JsonParser.parseString(File(base, "scryfall/cards/${record.scryfallId}.json").readText()).asJsonObject
                val path = java.net.URI(metadata.getAsJsonArray("card_faces")[reference.face].asJsonObject
                    .getAsJsonObject("image_uris").get("normal").asString).path
                assertArrayEquals(File(fixtures, path.removePrefix("/")).readBytes(), art.load(reference))
                val previous = requests
                assertArrayEquals(art.load(reference), repository(ArtworkTransport { _, _, _, _ -> error("offline") }, disk).load(reference))
                assertEquals(previous, requests)
            }
            assertEquals(3, requests, "one metadata + two exact face images")
            println("PASS $family v$edition actual indexed catalog -> exact two faces, lazy resolution, restart/offline cache reuse")
        }
    }

    @Test fun missingMappingWrongFaceWrongPrintingBrokenAndExecutableBytesNeverSubstitute() {
        var calls = 0
        val card = """{"object":"card","id":"$id","image_uris":{"normal":"$origin/front"}}""".toByteArray()
        var response = ArtworkResponse(200, card, "application/json")
        val art = repository(ArtworkTransport { _, _, _, _ -> calls++; response })
        assertNull(art.load(ArtworkReference(null, 0, "v1"))); assertEquals(0, calls)
        assertNull(art.load(ArtworkReference(id, 1, "v1"))); assertEquals(1, calls)
        response = ArtworkResponse(200, card.toString(Charsets.UTF_8).replace(id, "00000000-0000-0000-0000-000000000000").toByteArray(), "application/json")
        assertNull(art.load(ArtworkReference(id, 0, "wrong")))
        for (type in listOf("image/svg+xml", "text/html", "image/png")) {
            response = ArtworkResponse(200, "<svg onload='script'/>".toByteArray(), type)
            assertNull(art.load(ArtworkReference(null, 0, type, customUrl = "$origin/custom-$type")))
        }
        response = ArtworkResponse(200, ByteArray(HttpArtworkRepository.MAX_IMAGE.toInt() + 1), "image/png")
        assertNull(art.load(ArtworkReference(null, 0, "oversize", customUrl = "$origin/huge")))
    }

    @Test fun negativeAndTransientWindowsConditionalRequestsAndRetryAfter() {
        var calls = 0
        var response = ArtworkResponse(404)
        val art = repository(ArtworkTransport { _, _, _, _ -> calls++; response })
        val reference = ArtworkReference(null, 0, "v1", customUrl = "$origin/missing")
        assertNull(art.load(reference)); clock += 999
        assertNull(art.load(reference)); assertEquals(1, calls)
        clock++; response = ArtworkResponse(503)
        assertNull(art.load(reference)); assertEquals(2, calls)
        clock += 1_999; art.load(reference); assertEquals(2, calls)
        clock++; art.load(reference); assertEquals(3, calls)
        clock += 3_999; art.load(reference); assertEquals(3, calls)
        clock++; art.load(reference); assertEquals(4, calls)
        clock += 7_999; art.load(reference); assertEquals(4, calls)
        clock++; response = ArtworkResponse(429, retryAt = clock + 7_200_000)
        art.load(reference); assertEquals(5, calls)
        clock += 7_199_999; art.load(reference); assertEquals(5, calls)
        clock++; response = ArtworkResponse(200, png(), "image/png", "\"v1\"", "Wed, 21 Oct 2026 10:00:00 GMT")
        assertArrayEquals(png(), art.load(reference)); assertEquals(6, calls)
        clock += HttpArtworkRepository.DAY
        val conditional = repository(ArtworkTransport { _, _, etag, modified ->
            assertEquals("\"v1\"", etag); assertEquals("Wed, 21 Oct 2026 10:00:00 GMT", modified)
            ArtworkResponse(304)
        })
        assertArrayEquals(png(), conditional.load(reference))
        clock += HttpArtworkRepository.DAY
        assertArrayEquals(png(), repository(ArtworkTransport { _, _, _, _ -> error("offline") }).load(reference))
    }

    @Test fun perKeyConcurrentCoalescing() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val calls = AtomicInteger()
        val art = repository(ArtworkTransport { _, _, _, _ ->
            calls.incrementAndGet(); entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
            ArtworkResponse(200, png(), "image/png")
        })
        val pool = Executors.newFixedThreadPool(8)
        try {
            val reference = ArtworkReference(null, 0, "v1", customUrl = "$origin/image")
            val results = (1..8).map { pool.submit<ByteArray?> { art.load(reference) } }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); release.countDown()
            results.forEach { assertArrayEquals(png(), it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, calls.get())
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test fun cancelledOwnerRecordsNoFailureAndAJoinedCallerTakesTheDownloadOver() {
        val entered = CountDownLatch(1); val calls = AtomicInteger()
        val art = repository(ArtworkTransport { _, _, _, _ ->
            if (calls.incrementAndGet() == 1) { entered.countDown(); Thread.sleep(10_000) }
            ArtworkResponse(200, png(), "image/png")
        })
        val reference = ArtworkReference(null, 0, "v1", customUrl = "$origin/image")
        val owner = Thread { art.load(reference, ArtworkPriority.PREFETCH) }.apply { start() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val pool = Executors.newSingleThreadExecutor()
        try {
            val joined = pool.submit<ByteArray?> { art.load(reference) }
            Thread.sleep(100) // let the visible caller join the owner's attempt
            owner.interrupt(); owner.join(5_000)
            assertArrayEquals(png(), joined.get(5, TimeUnit.SECONDS))
            assertEquals(2, calls.get())
            // No failure window was written: a cancellation is not an outage.
            assertArrayEquals(png(), art.load(reference)); assertEquals(2, calls.get())
        } finally { pool.shutdownNow() }
    }

    // LOB-098
    @Test fun gateBoundsDownloadsAndAdmitsVisibleBeforeQueuedPrefetch() {
        val gate = ArtworkGate(1)
        val running = AtomicInteger(); val peak = AtomicInteger()
        val release = CountDownLatch(1); val order = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val pool = Executors.newFixedThreadPool(6)
        try {
            fun start(name: String, priority: ArtworkPriority, ticket: ArtworkGate.Ticket = gate.ticket(priority)) =
                pool.submit { gate.run(ticket) {
                    order += name; peak.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                    check(release.await(5, TimeUnit.SECONDS)); running.decrementAndGet()
                } }
            val blockers = listOf(start("p1", ArtworkPriority.PREFETCH))
            while (order.isEmpty()) Thread.sleep(5)
            val queued = gate.ticket(ArtworkPriority.PREFETCH)
            val waiting = listOf(start("p3", ArtworkPriority.PREFETCH, queued), start("p4", ArtworkPriority.PREFETCH))
            Thread.sleep(50)
            val visible = start("v", ArtworkPriority.VISIBLE)
            Thread.sleep(50)
            gate.promote(queued)
            Thread.sleep(50)
            release.countDown()
            (blockers + waiting + visible).forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, peak.get())
            assertEquals(listOf("p1", "p3", "v", "p4"), order.toList(), "visible (and promoted) first, then FIFO")
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test fun onlyProviderApiRequestsAreThrottled() {
        assertTrue(scryfallApiRequest(java.net.URI("https://api.scryfall.com/cards/$id")))
        assertFalse(scryfallApiRequest(java.net.URI("https://cards.scryfall.io/large/front/2/2/$id.jpg")))
    }

    @Test fun decoderFailuresHavePersistedExponentialWindowsAndSuccessReset() {
        val transport = ArtworkTransport { _, _, _, _ -> ArtworkResponse(200, png(), "image/png") }
        val reference = ArtworkReference(null, 0, "decode", customUrl = "$origin/image")
        var art = repository(transport)
        for (delay in listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L)) {
            assertNotNull(art.load(reference)); art.rejectDecoded(reference)
            art = repository(transport) // real persisted failure state, not a same-process counter
            clock += delay - 1; assertNull(art.load(reference))
            clock++; assertNotNull(art.load(reference))
        }
        art.decoded(reference)
        art.rejectDecoded(reference)
        clock += 1_000
        assertNotNull(art.load(reference), "successful bitmap decode resets failure escalation")
    }

    @Test fun rateLimitAndHttpDate() {
        val requests = mutableListOf<Long>()
        val throttle = ArtworkThrottle({ clock }, { clock += it })
        repeat(20) { throttle.acquire(); requests += clock }
        assertTrue(requests.zipWithNext().all { (a,b) -> b - a >= 100 })
        throttle.retryAfter(clock + 10_000)
        assertThrows(IllegalStateException::class.java) { throttle.acquire() }
        clock += 10_000; throttle.acquire()
        assertEquals(clock + 3000, HttpArtworkTransport.retryAfter("3", clock))
        val date = "Wed, 21 Oct 2026 10:00:00 GMT"
        assertEquals(1792576800000L, HttpArtworkTransport.retryAfter(date, 0))
    }

    @Test fun everyRequestAndSwallowedFailureIsReported() {
        val card = """{"object":"card","id":"$id","image_uris":{"normal":"$origin/front"}}""".toByteArray()
        val attempts = mutableListOf<ArtworkAttempt>()
        val failures = mutableListOf<Throwable>()
        val log = object : ArtworkLog {
            override fun attempt(attempt: ArtworkAttempt) { attempts += attempt }
            override fun failed(reference: ArtworkReference, failure: Throwable) { failures += failure }
        }
        var imageOnline = false
        val disk = cache()
        val art = HttpArtworkRepository(disk, ArtworkTransport { url, _, _, _ ->
            when {
                url == "$origin/cards/$id" -> ArtworkResponse(200, card, "application/json")
                imageOnline -> ArtworkResponse(200, png(), "image/png")
                else -> throw java.net.UnknownHostException("cards.test")
            }
        }, policy, "$origin/cards/", { clock }, log = log)
        val reference = ArtworkReference(id, 0, "v1")
        assertNull(art.load(reference))
        assertEquals(listOf(false, true), attempts.map { it.image })
        assertNull(attempts[0].failure)
        assertEquals("UnknownHostException: cards.test", attempts[1].failure)
        assertEquals(1, attempts[1].failures)
        assertEquals(ArtworkRetrySchedule.backoff(1), attempts[1].retryInMillis)
        clock += 60_000; imageOnline = true
        assertNotNull(art.load(reference))
        assertNull(attempts.last().failure)
        assertEquals("$origin/front", attempts.last().url)
        // Unusable card data never reaches the network log as a request failure, but is still reported.
        val broken = HttpArtworkRepository(cache("broken"), ArtworkTransport { _, _, _, _ ->
            ArtworkResponse(200, """{"object":"card","id":"other"}""".toByteArray(), "application/json")
        }, policy, "$origin/cards/", { clock }, log = log)
        assertNull(broken.load(reference))
        assertEquals(1, failures.size)
    }

    @Test fun urlDnsAndProductionIsolationPolicy() {
        val addresses = listOf("0.0.0.0", "10.1.2.3", "100.64.1.2", "127.0.0.1", "169.254.169.254",
            "172.16.0.1", "192.168.1.1", "192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1",
            "224.0.0.1", "255.255.255.255", "::1", "::", "fc00::1", "fe80::1", "2001:db8::1", "::ffff:127.0.0.1")
        for (address in addresses) assertFalse(ArtworkUrlPolicy.publicAddress(InetAddress.getByName(address)), address)
        for (address in listOf("1.1.1.1", "8.8.8.8", "2606:4700:4700::1111", "192.2.1.1", "192.0.32.1", "198.51.1.1"))
            assertTrue(ArtworkUrlPolicy.publicAddress(InetAddress.getByName(address)), address)
        // IPv6-only mobile networks reach IPv4-only hosts (Scryfall's image CDN) through NAT64.
        assertTrue(ArtworkUrlPolicy.publicAddress(InetAddress.getByName("64:ff9b::3982:2101")))
        for (address in listOf("64:ff9b::a01:203", "64:ff9b::7f00:1", "64:ff9b::c0a8:101"))
            assertFalse(ArtworkUrlPolicy.publicAddress(InetAddress.getByName(address)), address)
        val nat64 = ArtworkUrlPolicy(lookup = { listOf(InetAddress.getByName("64:ff9b::3982:2101"), InetAddress.getByName("57.130.33.1")) })
        assertEquals(2, nat64.addresses("cards.test").size)
        val production = ArtworkUrlPolicy(lookup = { listOf(InetAddress.getByName("127.0.0.1")) })
        for (url in listOf("file:///etc/passwd", "data:image/svg+xml,x", "http://public.test/art", "https://user:secret@public.test/art",
            "https://public.test:8080/art", "https://public.test/art#fragment", "https://public.test@127.0.0.1/art"))
            assertThrows(Exception::class.java) { production.validate(url) }
        assertThrows(Exception::class.java) { production.addresses("public.test") }
        val mixed = ArtworkUrlPolicy(lookup = { listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("10.1.1.1")) })
        val refused = assertThrows(Exception::class.java) { mixed.addresses("mixed.test") }
        assertTrue(refused.message!!.contains("mixed.test") && refused.message!!.contains("10.1.1.1"), refused.message)
        assertThrows(Exception::class.java) { ArtworkUrlPolicy("http://192.168.1.1:1234") }
        assertThrows(Exception::class.java) { policy.validate("http://127.0.0.1:12346/art") }
        assertThrows(Exception::class.java) { policy.validate("https://scryfall.com/art") }
    }

    @Test fun actualNetworkRedirectsByteLimitsAndCredentialIsolation() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val base = "http://127.0.0.1:${server.address.port}"
        val paths = mutableListOf<String>()
        server.createContext("/") { exchange ->
            synchronized(paths) { paths += exchange.requestURI.path }
            assertNull(exchange.requestHeaders.getFirst("Authorization")); assertNull(exchange.requestHeaders.getFirst("Cookie"))
            assertTrue(exchange.requestHeaders.getFirst("User-Agent").contains("AsyncTabletopWizards"))
            val path = exchange.requestURI.path
            val target = when (path) {
                "/bad" -> "http://169.254.169.254/latest/meta-data/"
                "/port" -> "http://127.0.0.1:1/art"
                "/credentials" -> "$base".replace("://", "://user:pass@") + "/image"
                "/loop" -> "$base/loop"
                "/r1" -> "$base/r2"
                "/r2" -> "$base/r3"
                "/r3" -> "$base/image"
                else -> null
            }
            if (target != null) {
                exchange.responseHeaders.add("Location", target); exchange.responseHeaders.add("Set-Cookie", "secret=neverforward")
                exchange.sendResponseHeaders(302, -1)
            } else {
                val bytes = png(); exchange.responseHeaders.add("Content-Type", "image/png")
                exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
        try { HttpArtworkTransport(ArtworkUrlPolicy(base)).use { network ->
            assertArrayEquals(png(), network.get("$base/r1", 10 * 1024 * 1024, "", "").bytes)
            assertEquals(listOf("/r1", "/r2", "/r3", "/image"), paths)
            for (path in listOf("bad", "port", "credentials", "loop"))
                assertThrows(Exception::class.java) { network.get("$base/$path", 1024, "", "") }
            assertEquals(4, paths.count { it == "/loop" })
            assertThrows(Exception::class.java) { network.get("$base/image", 8, "", "") }
        } } finally { server.stop(0) }
    }

    @Test fun diskBudgetLruCorruptEntriesAndSeparateEssentialFiles() {
        val essential = temporary.resolve("accepted.db").toFile().apply { writeText("accepted definition") }
        val disk = cache(budget = 450)
        fun put(key: String) { disk.write(key, ArtworkEntry(200, clock + 1000, 0, "image/png", "", "", ByteArray(100))) }
        put("a"); clock++; put("b"); clock++; put("c"); clock++
        assertNotNull(disk.read("a")); clock++; put("d")
        assertNull(disk.read("b")); assertNotNull(disk.read("a")); assertNotNull(disk.read("d"))
        assertTrue(temporary.resolve("art").toFile().listFiles()!!.sumOf { it.length() } <= 450)
        val file = temporary.resolve("art/${ArtworkDiskCache.hash("a")}.art").toFile()
        file.writeText("corrupt disposable bytes"); assertNull(disk.read("a"))
        val residue = temporary.resolve("art/${ArtworkDiskCache.hash("partial")}.art.part").toFile().apply { writeText("interrupted write") }
        val other = temporary.resolve("art/unrelated.txt").toFile().apply { writeText("not a cache entry") }
        cache(budget = 100).trim()
        assertFalse(residue.exists()); assertTrue(other.exists())
        assertTrue(temporary.resolve("art").toFile().listFiles()!!.filter { it.extension == "art" }.sumOf { it.length() } <= 100)
        assertEquals("accepted definition", essential.readText())
    }
}
