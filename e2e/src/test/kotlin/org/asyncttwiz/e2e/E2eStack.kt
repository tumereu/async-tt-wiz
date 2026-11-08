package org.asyncttwiz.e2e

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The full stack the e2e tests play on, started once per test JVM and stopped when it exits: a PostgreSQL container
 * (the server's pinned image, through the Docker CLI) and the installed server (`server/build/install`, as deployed:
 * `migrate`, then `serve` on free ports). The apps reach it at [origin] over plain loopback HTTP, as the debug build
 * allows. The tests share one server; [reset] brings its database back to the base dataset before each test.
 *
 * Robolectric loads test classes once per sandbox; the running stack is found again through system properties, so a
 * second sandbox never starts a second one.
 */
object E2eStack {
    private const val ORIGIN = "atw.e2e.origin"
    private const val SECRET = "atw.e2e.secretFile"
    private const val CONTAINER = "atw.e2e.container"
    private const val BASE = "atw.e2e.base"

    /** The server's public origin, e.g. `http://127.0.0.1:41234`. */
    val origin: String get() = System.getProperty(ORIGIN) ?: start()

    /** The file the server reads each new game's random secret from (TEST_GAME_SECRET_FILE). */
    val secretFile: File get() { origin; return File(System.getProperty(SECRET)) }

    /** Where failure screenshots and server logs go. */
    val output: File = File(System.getProperty("atw.e2e.output") ?: "build/e2e").apply { mkdirs() }

    @Synchronized private fun start(): String {
        System.getProperty(ORIGIN)?.let { return it }
        val server = requireNotNull(System.getProperty("atw.e2e.server")) { "Run the e2e tests with ./scripts/gradle.sh :e2e:test" }
        val image = requireNotNull(System.getProperty("atw.e2e.postgresImage"))
        val dir = Files.createTempDirectory(output.toPath(), "stack-").toFile()
        val password = UUID.randomUUID().toString()
        val name = "atw-e2e-${ProcessHandle.current().pid()}"
        // Containers of e2e runs that died without their shutdown hook; never another run still going (parallel worktrees)
        docker("ps", "-a", "--format", "{{.Names}}", "--filter", "label=org.asyncttwiz.owner=e2e").lines().filter(String::isNotBlank)
            .filter { stale -> stale.removePrefix("atw-e2e-").toLongOrNull()?.let { ProcessHandle.of(it).isPresent } != true }
            .forEach { runCatching { docker("rm", "-f", it) } }
        docker("run", "-d", "--rm", "--name", name, "--label", "org.asyncttwiz.owner=e2e", "-e", "POSTGRES_USER=atw_e2e",
            "-e", "POSTGRES_PASSWORD=$password", "-e", "POSTGRES_DB=atw_e2e", "--tmpfs", "/var/lib/postgresql/data", image)
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { docker("rm", "-f", name) } })
        // The bridge address: no userland proxy (see the server tests' OwnedPostgres)
        val host = docker("inspect", "-f", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", name).trim()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        while (runCatching { docker("exec", name, "pg_isready", "-h", "127.0.0.1", "-U", "atw_e2e", "-d", "atw_e2e") }.isFailure) {
            check(System.nanoTime() < deadline) { "PostgreSQL did not start" }
            Thread.sleep(200)
        }
        val passwordFile = File(dir, "db-password").apply { writeText(password) }
        val resetKey = File(dir, "reset-mail-key").apply { writeText(java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 })) }
        val secret = File(dir, "game-secret")
        val environment = mapOf("DB_URL" to "jdbc:postgresql://$host:5432/atw_e2e", "DB_USER" to "atw_e2e",
            "DB_PASSWORD_FILE" to passwordFile.path, "PUBLIC_BASE_URL" to "http://127.0.0.1:8080",
            "SERVER_ADDRESS" to "127.0.0.1", "SERVER_PORT" to "0", "HEALTH_ADDRESS" to "127.0.0.1", "HEALTH_PORT" to "0",
            "SMTP_HOST" to "127.0.0.1", "SMTP_PORT" to "9", "SMTP_TLS" to "local-plaintext", "SMTP_FROM" to "e2e@atw.invalid",
            "RESET_BASE_URL" to "https://reset.atw.invalid", "RESET_MAIL_KEY_FILE" to resetKey.path,
            "TEST_GAME_SECRET_FILE" to secret.path, "TEST_LOBBY_REQUESTS_A_MINUTE" to "100000",
            "JAVA_OPTS" to "-Djava.net.preferIPv4Stack=true -Xmx768m")
        fun launch(command: String, log: File): Process = ProcessBuilder(server, command).redirectErrorStream(true)
            .redirectOutput(log).also { it.environment().putAll(environment) }.start()
        val migrate = launch("migrate", File(dir, "migrate.log"))
        check(migrate.waitFor(120, TimeUnit.SECONDS) && migrate.exitValue() == 0) { "Migration failed: ${File(dir, "migrate.log").readText()}" }
        val log = File(dir, "server.log")
        val process = launch("serve", log)
        Runtime.getRuntime().addShutdownHook(Thread { process.destroy(); process.waitFor(10, TimeUnit.SECONDS); process.destroyForcibly() })
        val bound = Regex("(?m)^Listener bound: (health|public) port=([0-9]+)\\r?\\n")
        var ports = emptyMap<String, Int>()
        while (ports.size < 2) {
            check(process.isAlive) { "The server stopped: ${log.readText().takeLast(4000)}" }
            check(System.nanoTime() < deadline + TimeUnit.SECONDS.toNanos(60)) { "The server did not start: ${log.readText().takeLast(4000)}" }
            ports = bound.findAll(log.readText()).associate { it.groupValues[1] to it.groupValues[2].toInt() }
            if (ports.size < 2) Thread.sleep(100)
        }
        val http = HttpClient.newHttpClient()
        while (runCatching { http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${ports["health"]}/health/ready"))
                .timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.discarding()).statusCode() }.getOrNull() != 200) {
            check(process.isAlive) { "The server stopped: ${log.readText().takeLast(4000)}" }
            Thread.sleep(100)
        }
        println("E2E stack: server log ${log.path}")
        System.setProperty(SECRET, secret.path)
        System.setProperty(CONTAINER, name)
        return "http://127.0.0.1:${ports["public"]}".also { System.setProperty(ORIGIN, it) }
    }

    /**
     * The database as it is now becomes the base dataset [reset] brings back (made once per run: the accounts, their
     * sessions and settings). Its sequences aren't part of it: ids keep growing across resets, never reused.
     */
    @Synchronized fun keepBase() {
        val dump = exec(null, "pg_dump", "-U", "atw_e2e", "-d", "atw_e2e", "--data-only", "--disable-triggers",
            "--exclude-table=flyway_schema_history").lines().filterNot { it.startsWith("SELECT pg_catalog.setval(") }
        val file = File(secretFile.parentFile, "base.sql")
        file.writeText(dump.joinToString("\n"))
        System.setProperty(BASE, file.path)
    }

    /**
     * Brings the database back to the base dataset ([keepBase]), in one transaction: every table emptied (games, pods,
     * invitations, contacts, decks, notifications, chat, problem reports, sessions, settings, ...) and the base's rows
     * put back. Again on failure (a deadlock with a write of the server's, say), at most three times.
     */
    @Synchronized fun reset() {
        val base = File(requireNotNull(System.getProperty(BASE)) { "no base dataset kept" })
        val script = buildString {
            appendLine("BEGIN;")
            appendLine("DO \$\$ BEGIN EXECUTE (SELECT 'TRUNCATE ' || string_agg(format('%I.%I', schemaname, tablename), ', ') " +
                "FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'); END \$\$;")
            appendLine(base.readText())
            appendLine("COMMIT;")
        }
        repeat(3) { attempt ->
            try { exec(script, "psql", "-q", "-U", "atw_e2e", "-d", "atw_e2e", "-v", "ON_ERROR_STOP=1"); return }
            catch (failure: IllegalStateException) { if (attempt == 2) throw failure; Thread.sleep(200) }
        }
    }

    /** The rows [sql] (a read) gives now, each one line of its values separated by `|`. */
    fun query(sql: String): List<String> =
        exec(null, "psql", "-q", "-t", "-A", "-U", "atw_e2e", "-d", "atw_e2e", "-v", "ON_ERROR_STOP=1", "-c", sql).lines().filter { it.isNotBlank() }

    /** [command] in the database's container ([input] its standard input); its standard output. */
    private fun exec(input: String?, vararg command: String): String {
        val name = requireNotNull(System.getProperty(CONTAINER)) { "the stack isn't running" }
        val process = ProcessBuilder(listOf("docker", "exec", "-i", name) + command).start()
        val errors = StringBuilder()
        val reader = Thread { errors.append(process.errorStream.bufferedReader().readText()) }.apply { start() }
        process.outputStream.use { stream -> input?.let { stream.write(it.toByteArray()) } }
        val output = process.inputStream.bufferedReader().readText()
        reader.join(TimeUnit.SECONDS.toMillis(120))
        check(process.waitFor(120, TimeUnit.SECONDS) && process.exitValue() == 0) { "${command.first()} failed: $errors" }
        return output
    }

    private fun docker(vararg args: String): String {
        val process = ProcessBuilder(listOf("docker") + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(120, TimeUnit.SECONDS) && process.exitValue() == 0) { "docker ${args.first()} failed: $output" }
        return output
    }
}
