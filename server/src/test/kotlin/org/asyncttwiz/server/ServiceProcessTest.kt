package org.asyncttwiz.server

import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real installed Spring/JVM processes, actual HTTP/signals, Testcontainers-owned databases only. */
class ServiceProcessTest {
    @TempDir lateinit var temporary: Path
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()

    private fun port(address: String) = ServerSocket(0, 50, java.net.InetAddress.getByName(address)).use { it.localPort }

    private fun environment(db: OwnedPostgres): Map<String, String> {
        val secret = temporary.resolve("password-${UUID.randomUUID()}")
        Files.writeString(secret, db.container.password + "\r\n")
        return mapOf("DB_URL" to db.container.jdbcUrl, "DB_USER" to db.container.username,
            "DB_PASSWORD_FILE" to secret.toString(), "PUBLIC_BASE_URL" to "https://synthetic.invalid/base",
            "SERVER_ADDRESS" to "127.0.0.1", "SERVER_PORT" to port("127.0.0.1").toString(),
            "HEALTH_ADDRESS" to "127.0.0.1", "HEALTH_PORT" to port("127.0.0.1").toString(),
            // These are deliberately NOT an alternate configuration channel.
            "SPRING_APPLICATION_JSON" to """{"logging":{"level":{"root":"TRACE"}},"server":{"port":1}}""",
            "SPRING_CONFIG_LOCATION" to "file:/not-an-application-configuration")
    }

    private fun launch(inputs: Map<String, String>, command: String, log: Path): Process {
        val builder = ProcessBuilder(System.getProperty("asyncttwiz.migrationExecutable"), command)
            .redirectErrorStream(true).redirectOutput(log.toFile())
        val inherited = builder.environment().filterKeys { it in setOf("PATH", "JAVA_HOME", "JAVA_TOOL_OPTIONS", "HOME") }
        builder.environment().clear()
        builder.environment().putAll(inherited + inputs)
        return builder.start()
    }

    private fun redactedOutput(log: Path, inputs: Map<String, String>) {
        val output = Files.readString(log)
        val secretPath = inputs["DB_PASSWORD_FILE"]?.let(Path::of)
        val secrets = listOfNotNull(inputs["DB_URL"], inputs["DB_USER"], inputs["DB_PASSWORD_FILE"],
            secretPath?.takeIf { Files.isRegularFile(it) }?.let {
                try { Files.readString(it).trimEnd('\r', '\n').takeIf(String::isNotEmpty) }
                catch (_: Exception) { null }
            })
        secrets.forEach { assertFalse(output.contains(it), "Service/migration log exposed configuration input") }
        assertFalse(output.contains("Exception"), "Raw exception/stack trace in service output")
        println("RETAINED process output (pid/configuration/secret independent):\n$output")
    }

    private fun migrate(inputs: Map<String, String>) {
        val log = temporary.resolve("migrate-${UUID.randomUUID()}.log")
        val process = launch(inputs, "migrate", log)
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Explicit migration did not finish")
            assertEquals(0, process.exitValue())
            assertTrue(Files.readString(log).contains("Migration successful"))
            redactedOutput(log, inputs)
        } finally { if (process.isAlive) process.destroyForcibly().waitFor() }
    }

    private inner class Service(val inputs: Map<String, String>) : AutoCloseable {
        val log = temporary.resolve("service-${UUID.randomUUID()}.log")
        val process = launch(inputs, "serve", log)
        private var closed = false

        fun request(path: String, health: Boolean = true, method: String = "GET"): HttpResponse<String> {
            val address = inputs.getValue(if (health) "HEALTH_ADDRESS" else "SERVER_ADDRESS")
            val port = inputs.getValue(if (health) "HEALTH_PORT" else "SERVER_PORT")
            return http.send(HttpRequest.newBuilder(URI("http://$address:$port$path"))
                .timeout(Duration.ofSeconds(15)).method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString())
        }

        fun awaitStatus(path: String, expected: Int, seconds: Long = 30) {
            var last = "no response"
            await("HTTP $path status=$expected", seconds) {
                assertTrue(process.isAlive, "Service exited before requested state")
                try {
                    val response = request(path)
                    val actual = "${response.statusCode()}: ${response.body()}"
                    if (actual != last) { println("DIAGNOSTIC actual HTTP $path $actual"); last = actual }
                    response.statusCode() == expected
                } catch (failure: java.io.IOException) {
                    val actual = failure.javaClass.simpleName
                    if (actual != last) { println("DIAGNOSTIC actual HTTP $path $actual"); last = actual }
                    false
                }
            }
            println("PASS actual service pid=${process.pid()} $path status=$expected")
        }

        fun signal(signal: String) {
            assertEquals(0, ProcessBuilder("kill", "-$signal", process.pid().toString()).start().waitFor())
        }

        override fun close() {
            if (closed) return
            closed = true
            // CONT is harmless for a running process and ensures failure cleanup after STOP.
            if (process.isAlive) signal("CONT")
            val start = System.nanoTime()
            try {
                if (process.isAlive) {
                    signal("TERM")
                    assertTrue(process.waitFor(30, TimeUnit.SECONDS), "SIGTERM exceeded 30 seconds")
                    assertEquals(143, process.exitValue(), "Expected JVM SIGTERM exit")
                }
                val elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis()
                listOf("SERVER", "HEALTH").forEach { prefix ->
                    // WSL's host loopback relay can still accept TCP after the owned JVM exits.
                    // Rebinding the exact local address/port proves its real listener is released.
                    ServerSocket().use { socket ->
                        socket.reuseAddress = true
                        socket.bind(InetSocketAddress(inputs.getValue("${prefix}_ADDRESS"),
                            inputs.getValue("${prefix}_PORT").toInt()))
                    }
                }
                redactedOutput(log, inputs)
                println("CLEANUP service pid=${process.pid()} exited=${process.exitValue()} SIGTERM-ms=$elapsed listeners closed")
            } finally {
                if (process.isAlive) process.destroyForcibly().waitFor()
            }
        }
    }

    private fun await(description: String, seconds: Long = 30, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        do {
            if (predicate()) return
            Thread.sleep(100)
        } while (System.nanoTime() < deadline)
        fail<Unit>("Timed out: $description")
    }

    private fun sql(db: OwnedPostgres, statement: String) = db.connection().use {
        it.createStatement().use { query -> query.execute(statement) }
    }

    private fun number(db: OwnedPostgres, statement: String): Long = db.connection().use {
        it.createStatement().use { query -> query.executeQuery(statement).use { rows -> assertTrue(rows.next()); rows.getLong(1) } }
    }

    private fun epoch(db: OwnedPostgres) = number(db, "SELECT epoch FROM service_writer")

    private fun assertNoServiceConnections(db: OwnedPostgres) {
        await("closed service database sessions") {
            number(db, "SELECT count(*) FROM pg_stat_activity WHERE application_name = 'async-tt-wiz-ownership'") == 0L
        }
        assertEquals(0L, number(db, "SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND classid=1096045655::oid"))
        println("CLEANUP no ownership sessions/advisory locks remain")
    }

    @Test fun `real service stays live without schema then explicit migration enables isolated health and retained restart`() {
        OwnedPostgres().start().use { db ->
            val inputs = environment(db)
            var priorEpoch = 0L
            Service(inputs).use { service ->
                service.awaitStatus("/health/live", 200)
                service.awaitStatus("/health/ready", 503)
                assertEquals("OK\n", service.request("/health/live").body())
                assertEquals("Unavailable\n", service.request("/health/ready").body())
                assertEquals(0L, number(db, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'"))
                println("PASS empty schema unchanged by real service startup/recovery")
                migrate(inputs)
                service.awaitStatus("/health/ready", 200)
                priorEpoch = epoch(db)
                assertTrue(priorEpoch > 0)
                for (path in listOf("/health/live", "/health/ready", "/actuator/env", "/error", "/unknown?password=do-not-echo")) {
                    val response = service.request(path, health = false)
                    assertEquals(404, response.statusCode())
                    assertEquals("Not found\n", response.body())
                    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow())
                }
                assertEquals(404, service.request("/actuator/env").statusCode())
                assertEquals(404, service.request("/health/live", method = "POST").statusCode())
                assertEquals("", service.request("/health/live", method = "HEAD").body())
                assertThrows(java.io.IOException::class.java) {
                    Socket().use { it.connect(InetSocketAddress("127.0.0.2", inputs.getValue("HEALTH_PORT").toInt()), 500) }
                }
                println("PASS health/public port and address isolation; detail-free unknown paths/methods")
            }
            assertNoServiceConnections(db)
            assertEquals(priorEpoch, epoch(db), "Shutdown must not reset retained state")
            migrate(inputs) // Retained explicit migration is still idempotent.
            Service(environment(db)).use { service ->
                service.awaitStatus("/health/ready", 200)
                assertEquals(priorEpoch + 1, epoch(db))
                println("PASS actual service restart advances retained epoch=$priorEpoch to ${epoch(db)}")
            }
            assertNoServiceConnections(db)
        }
    }

    @Test fun `checksum future and unversioned schemas are never ready or automatically repaired`() {
        OwnedPostgres().start().use { db ->
            val inputs = environment(db)
            sql(db, "CREATE TABLE test_owned_unversioned (retained text); INSERT INTO test_owned_unversioned VALUES ('keep')")
            Service(inputs).use { service ->
                service.awaitStatus("/health/live", 200)
                service.awaitStatus("/health/ready", 503)
                assertEquals(1L, number(db, "SELECT count(*) FROM test_owned_unversioned WHERE retained='keep'"))
                assertEquals(0L, number(db, "SELECT count(*) FROM information_schema.tables WHERE table_name='flyway_schema_history'"))
            }
            sql(db, "DROP TABLE test_owned_unversioned") // Test-owned database only.
            migrate(inputs)
            val checksum = number(db, "SELECT checksum FROM flyway_schema_history WHERE version='1'")
            for (corruption in listOf("checksum", "future")) {
                if (corruption == "checksum") sql(db, "UPDATE flyway_schema_history SET checksum=123 WHERE version='1'")
                else sql(db, "INSERT INTO flyway_schema_history VALUES (2,'999','future','SQL','V999__future.sql',42,current_user,now(),0,true)")
                Service(environment(db)).use { service ->
                    service.awaitStatus("/health/live", 200)
                    service.awaitStatus("/health/ready", 503)
                    // Observe several recovery cycles; no epoch or migration repair occurs.
                    Thread.sleep(1200)
                    assertEquals(503, service.request("/health/ready").statusCode())
                    assertEquals(0L, epoch(db))
                    assertEquals(if (corruption == "future") 2L else 1L, number(db, "SELECT count(*) FROM flyway_schema_history"))
                    println("PASS real process rejects $corruption schema unchanged")
                }
                if (corruption == "checksum") sql(db, "UPDATE flyway_schema_history SET checksum=$checksum WHERE version='1'")
                else sql(db, "DELETE FROM flyway_schema_history WHERE version='999'")
            }
            assertNoServiceConnections(db)
        }
    }

    @Test fun `two actual processes recover database outage and lock takeover without dual ready writers`() {
        OwnedPostgres().start().use { db ->
            val firstInputs = environment(db)
            migrate(firstInputs)
            Service(firstInputs).use { first ->
                first.awaitStatus("/health/ready", 200)
                Service(environment(db)).use { second ->
                    second.awaitStatus("/health/live", 200)
                    second.awaitStatus("/health/ready", 503)
                    val initial = epoch(db)
                    // STOP only the old JVM, terminate its dedicated session, let the live peer take over.
                    first.signal("STOP")
                    try {
                        val lockPid = number(db, "SELECT pid FROM pg_locks WHERE locktype='advisory' AND granted")
                        sql(db, "SELECT pg_terminate_backend($lockPid)")
                        second.awaitStatus("/health/ready", 200)
                        assertEquals(initial + 1, epoch(db))
                    } finally { first.signal("CONT") }
                    first.awaitStatus("/health/ready", 503)
                    assertEquals(200, second.request("/health/ready").statusCode())
                    println("PASS terminated dedicated lock session; replacement ready only after new persisted epoch")

                    val beforeRestart = epoch(db)
                    db.container.dockerClient.stopContainerCmd(db.container.containerId).withTimeout(1).exec()
                    for (service in listOf(first, second)) {
                        service.awaitStatus("/health/ready", 503)
                        val start = System.nanoTime()
                        assertEquals(200, service.request("/health/live").statusCode())
                        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 1500)
                    }
                    db.container.dockerClient.startContainerCmd(db.container.containerId).exec()
                    await("retained DB returns") { try { db.connection().use { it.isValid(1) } } catch (_: Exception) { false } }
                    await("one recovered writer") {
                        listOf(first, second).count { it.request("/health/ready").statusCode() == 200 } == 1
                    }
                    assertTrue(epoch(db) > beforeRestart)
                    println("PASS real DB stop/start; exactly one writer recovers with advanced retained epoch")
                }
                first.awaitStatus("/health/ready", 200)
            }
            assertNoServiceConnections(db)
        }
    }

    @Test fun `invalid credentials remain live and detail free and SIGTERM is bounded during database blackhole`() {
        OwnedPostgres().start().use { db ->
            val inputs = environment(db)
            migrate(inputs)
            val wrongSecret = temporary.resolve("wrong-secret")
            Files.writeString(wrongSecret, "synthetic-credential-do-not-log")
            Service(inputs + ("DB_PASSWORD_FILE" to wrongSecret.toString())).use { service ->
                service.awaitStatus("/health/live", 200)
                service.awaitStatus("/health/ready", 503)
                assertEquals("Unavailable\n", service.request("/health/ready").body())
            }
            Service(environment(db)).use { service ->
                service.awaitStatus("/health/ready", 200)
                db.container.dockerClient.pauseContainerCmd(db.container.containerId).exec()
                try {
                    // Liveness is independent of the blocked dedicated-connection probe/recovery loop.
                    val start = System.nanoTime()
                    assertEquals(200, service.request("/health/live").statusCode())
                    assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 1500)
                    service.awaitStatus("/health/ready", 503)
                    service.close() // Actual TERM while PostgreSQL is still paused.
                } finally { db.container.dockerClient.unpauseContainerCmd(db.container.containerId).exec() }
            }
            assertNoServiceConnections(db)
        }
    }

    @Test fun `lock loss requires schema validation and retained singleton reload before readiness returns`() {
        OwnedPostgres().start().use { db ->
            val inputs = environment(db)
            migrate(inputs)
            Service(inputs).use { service ->
                service.awaitStatus("/health/ready", 200)
                val original = epoch(db)
                val checksum = number(db, "SELECT checksum FROM flyway_schema_history WHERE version='1'")
                sql(db, "UPDATE flyway_schema_history SET checksum=123 WHERE version='1'")
                val pid = number(db, "SELECT pid FROM pg_locks WHERE locktype='advisory' AND granted")
                sql(db, "SELECT pg_terminate_backend($pid)")
                service.awaitStatus("/health/ready", 503)
                assertEquals(200, service.request("/health/live").statusCode())
                Thread.sleep(1200)
                assertEquals(503, service.request("/health/ready").statusCode())
                assertEquals(original, epoch(db))
                sql(db, "UPDATE flyway_schema_history SET checksum=$checksum WHERE version='1'")
                service.awaitStatus("/health/ready", 200)
                assertEquals(original + 1, epoch(db))
                println("PASS actual process revalidates changed schema after lock loss, then advances/reloads epoch only after compatibility restored")
            }
            assertNoServiceConnections(db)
        }
    }

    @Test fun `occupied public port fails safely and closes already started health listener`() {
        OwnedPostgres().start().use { db ->
            val inputs = environment(db)
            ServerSocket().use { occupied ->
                occupied.bind(InetSocketAddress(inputs.getValue("SERVER_ADDRESS"), inputs.getValue("SERVER_PORT").toInt()))
                val log = temporary.resolve("occupied-public-port.log")
                val process = launch(inputs, "serve", log)
                try {
                    assertTrue(process.waitFor(20, TimeUnit.SECONDS))
                    assertEquals(1, process.exitValue())
                    redactedOutput(log, inputs)
                    ServerSocket().use { health ->
                        health.reuseAddress = true
                        health.bind(InetSocketAddress(inputs.getValue("HEALTH_ADDRESS"), inputs.getValue("HEALTH_PORT").toInt()))
                    }
                    assertNoServiceConnections(db)
                    println("PASS public bind failure closes partially started health context and exits safely")
                } finally { if (process.isAlive) process.destroyForcibly().waitFor() }
            }
        }
    }

    @Test fun `required malformed and unreadable file configuration fails promptly without disclosing sentinels`() {
        val secret = temporary.resolve("sentinel-file")
        Files.writeString(secret, "synthetic-do-not-log-credential")
        val invalidUtf8 = temporary.resolve("invalid-utf8")
        Files.write(invalidUtf8, byteArrayOf(0xc3.toByte(), 0x28))
        val empty = temporary.resolve("empty")
        Files.writeString(empty, "\n")
        val unreadable = temporary.resolve("unreadable")
        Files.writeString(unreadable, "private")
        Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------"))
        assertFalse(Files.isReadable(unreadable), "Unreadable file check must actually be unavailable to this user")
        val valid = mapOf("DB_URL" to "jdbc:postgresql://127.0.0.1:1/private_db", "DB_USER" to "private_username",
            "DB_PASSWORD_FILE" to secret.toString(), "PUBLIC_BASE_URL" to "https://synthetic.invalid")
        val cases = listOf(emptyMap(), valid - "DB_URL", valid - "DB_USER", valid - "DB_PASSWORD_FILE",
            valid - "PUBLIC_BASE_URL", valid + ("PUBLIC_BASE_URL" to "https://secret:password@host/"),
            valid + ("PUBLIC_BASE_URL" to "not-a-url"), valid + ("SERVER_PORT" to "-1"),
            valid + ("HEALTH_PORT" to "65536"), valid + ("HEALTH_PORT" to "8080"),
            valid + ("HEALTH_ADDRESS" to ""), valid + ("SERVER_ADDRESS" to "not a valid address"),
            valid + ("DB_URL" to "jdbc:postgresql://localhost:invalid/private_db?password=synthetic-do-not-log-credential"),
            valid + ("DB_PASSWORD_FILE" to temporary.resolve("absent").toString()),
            valid + ("DB_PASSWORD_FILE" to temporary.toString()),
            valid + ("DB_PASSWORD_FILE" to unreadable.toString()),
            valid + ("DB_PASSWORD_FILE" to invalidUtf8.toString()), valid + ("DB_PASSWORD_FILE" to empty.toString()))
        try {
            for ((index, inputs) in cases.withIndex()) {
                val log = temporary.resolve("bad-config-$index.log")
                val process = launch(inputs, "serve", log)
                try {
                    assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Invalid config failed to exit")
                    assertEquals(1, process.exitValue())
                    assertTrue(Files.readString(log).contains("Service failed"))
                    redactedOutput(log, inputs)
                    println("PASS installed service malformed configuration case=$index exit=1")
                } finally { if (process.isAlive) process.destroyForcibly().waitFor() }
            }
        } finally { Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("rw-------")) }
    }
}
