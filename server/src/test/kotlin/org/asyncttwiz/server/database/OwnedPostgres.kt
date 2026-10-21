package org.asyncttwiz.server.database

import org.junit.jupiter.api.Assertions.assertTrue
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.net.ServerSocket
import java.util.UUID

/** Never attaches to Compose/developer databases and never enables container reuse. */
class OwnedPostgres(retainPortAcrossRestart: Boolean = false) : AutoCloseable {
    // Docker can republish a random host port on stop/start while Testcontainers caches
    // the original mapping. Only restart tests reserve an OS-allocated, explicit port.
    // No hard-coded/shared host port; release the reservation immediately before create.
    private val portReservation = if (retainPortAcrossRestart) ServerSocket(0) else null
    val container = PostgreSQLContainer(DockerImageName.parse(System.getProperty("asyncttwiz.postgresImage")).asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("foundation_integration")
        .withUsername("foundation_test")
        .withPassword(UUID.randomUUID().toString())
        .withLabel("org.asyncttwiz.owner", "server-integration")
        .withReuse(false)
        .apply { portReservation?.let { setPortBindings(listOf("${it.localPort}:5432")) } }

    fun start(): OwnedPostgres {
        portReservation?.close()
        container.start()
        println("OWNED integration PostgreSQL container=${container.containerId} image=${container.dockerImageName}")
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SHOW server_version").use { rows ->
                    assertTrue(rows.next())
                    assertTrue(rows.getString(1).startsWith("17.11"))
                    println("PostgreSQL server_version=${rows.getString(1)}")
                }
            }
        }
        return this
    }

    val settings: DatabaseSettings get() = DatabaseSettings(container.jdbcUrl, container.username, container.password)

    fun connection() = DriverManager.getConnection(container.jdbcUrl, container.username, container.password)

    override fun close() {
        portReservation?.close()
        val id = container.containerId ?: return
        val client = container.dockerClient
        val volumes = client.inspectContainerCmd(id).exec().mounts.orEmpty().mapNotNull { it.name }
        container.stop()
        assertTrue(client.listContainersCmd().withShowAll(true).withIdFilter(listOf(id)).exec().isEmpty())
        val remainingVolumes = client.listVolumesCmd().exec().volumes.orEmpty().map { it.name }
        assertTrue(volumes.none { it in remainingVolumes }, "Owned anonymous database volume leaked")
        println("CLEANUP removed owned integration container=$id volumes=$volumes")
    }
}
