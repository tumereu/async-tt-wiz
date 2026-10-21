package org.asyncttwiz.server.database

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Host infrastructure qualification, not an Android or account/game E2E test. */
class ComposeIsolationTest {
    private val root = Path.of("").toAbsolutePath().let { if (Files.exists(it.resolve("e2e/environment.py"))) it else it.parent }

    private fun command(directory: Path, vararg arguments: String, witness: (() -> Unit)? = null): String {
        val output = Files.createTempFile(Path.of("/tmp/opencode"), "compose-isolation-output-", ".txt")
        val process = ProcessBuilder(arguments.toList()).directory(root.toFile())
            .redirectErrorStream(true).redirectOutput(output.toFile()).start()
        try {
            val until = System.nanoTime() + TimeUnit.MINUTES.toNanos(15)
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                witness?.invoke()
                check(System.nanoTime() < until) { "Owned infrastructure command exceeded bounded deadline" }
            }
            witness?.invoke()
            val text = Files.readString(output)
            println("COMMAND ${arguments.toList()} instance=${directory.fileName} exit=${process.exitValue()}\n$text")
            assertEquals(0, process.exitValue(), "Owned infrastructure qualification failed; retained output above")
            return text
        } finally {
            if (process.isAlive) process.destroyForcibly().waitFor()
            // This temporary transcript contains synthetic infrastructure only;
            // Gradle's test XML/standard streams retain it for the run evidence.
            Files.deleteIfExists(output)
        }
    }

    private fun cli(directory: Path, vararg operation: String) = command(directory,
        "python3", "scripts/e2e-environment.py", "--directory", directory.toString(), *operation)

    @Test fun `Compose repeated reset and cleanup cannot touch separately owned Testcontainers state`() {
        val directory = Path.of("/tmp/opencode/atw-e2e-${UUID.randomUUID().toString().replace("-", "")}")
        val project = directory.fileName.toString()
        val client = DockerClientFactory.instance().client()
        fun composeIds() = client.listContainersCmd().withShowAll(true)
            .withLabelFilter(mapOf("com.docker.compose.project" to project)).exec().map { it.id }.toSet()
        fun volumes() = client.listVolumesCmd().exec().volumes.orEmpty().map { it.name }.toSet()
        fun networks() = client.listNetworksCmd().exec().map { it.id }.toSet()
        try {
            cli(directory, "provision")
            val initialCompose = composeIds()
            assertEquals(5, initialCompose.size)
            var checks = 0
            val db = OwnedPostgres()
            try {
                db.start()
                FoundationMigrations.migrate(db.settings)
                assertEquals(initialCompose, composeIds(), "Testcontainers startup replaced Compose resources")
                val baselineContainers = client.listContainersCmd().withShowAll(true).exec().map { it.id }.toSet() - initialCompose
                val baselineVolumes = volumes() - setOf("$project-database", "$project-adapters")
                val baselineNetworks = networks() - client.listNetworksCmd().withNameFilter("$project-scenario").exec().map { it.id }.toSet()
                db.connection().use { connection ->
                    connection.createStatement().use { sql ->
                        sql.queryTimeout = 5
                        sql.execute("CREATE TABLE independent_sentinel(value text NOT NULL); INSERT INTO independent_sentinel VALUES ('separate-owner-retained')")
                        connection.autoCommit = false
                        sql.execute("INSERT INTO independent_sentinel VALUES ('held-transaction')")
                        fun witness() {
                            sql.executeQuery("SELECT string_agg(value,',' ORDER BY value) FROM independent_sentinel").use { rows ->
                                assertTrue(rows.next())
                                assertEquals("held-transaction,separate-owner-retained", rows.getString(1))
                            }
                            checks++
                        }
                        command(directory, "python3", "scripts/check-e2e-reset.py", "--directory", directory.toString(), witness = ::witness)
                        val finalCompose = composeIds()
                        assertEquals(5, finalCompose.size)
                        assertTrue((initialCompose intersect finalCompose).isEmpty())
                        assertEquals(baselineContainers, client.listContainersCmd().withShowAll(true).exec().map { it.id }.toSet() - finalCompose)
                        assertEquals(baselineVolumes, volumes() - setOf("$project-database", "$project-adapters"))
                        assertEquals(baselineNetworks, networks() - client.listNetworksCmd().withNameFilter("$project-scenario").exec().map { it.id }.toSet())
                        println("PASS separately owned Testcontainers/JDBC transaction and sentinel survived $checks continuous probes across Compose reset/failures; unrelated container/volume/network inventory unchanged")
                        connection.commit()
                        // Compose cleanup must not touch the held integration DB.
                        cli(directory, "cleanup")
                        assertTrue(composeIds().isEmpty())
                        witness()
                        println("PASS Compose cleanup preserves separate Testcontainers container=${db.container.containerId}, volume, connection and committed sentinel")
                    }
                }
            } finally {
                db.close()
            }
            assertTrue(checks > 10)
            // The existing Compose environment was cleaned first, so qualify the
            // reverse cleanup order with a second small independently owned pair.
            val second = Path.of("/tmp/opencode/atw-e2e-${UUID.randomUUID().toString().replace("-", "")}")
            try {
                cli(second, "provision")
                val secondProject = second.fileName.toString()
                fun secondIds() = client.listContainersCmd().withShowAll(true)
                    .withLabelFilter(mapOf("com.docker.compose.project" to secondProject)).exec().map { it.id }.toSet()
                val ids = secondIds()
                OwnedPostgres().use { separate ->
                    separate.start()
                    FoundationMigrations.migrate(separate.settings)
                }
                assertEquals(ids, secondIds())
                cli(second, "wait")
                println("PASS separate Testcontainers startup/migration/cleanup preserves live Compose IDs/readiness and owned stores; no shared reset owner")
            } finally {
                if (Files.exists(second.resolve("instance.json")) && !Files.exists(second.resolve("cleaned.json"))) cli(second, "cleanup")
            }
        } finally {
            if (Files.exists(directory.resolve("instance.json")) && !Files.exists(directory.resolve("cleaned.json"))) {
                cli(directory, "capture")
                cli(directory, "cleanup")
            }
        }
    }
}
