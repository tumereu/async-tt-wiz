package org.asyncttwiz.server.database

import org.junit.jupiter.api.Assertions.assertTrue
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.sql.Connection
import java.sql.SQLException
import java.net.ServerSocket
import java.net.InetAddress
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Never attaches to Compose/developer databases and never enables container reuse. */
class OwnedPostgres : AutoCloseable {
    // Reserve on the actual eventual bind address, including non-restart tests.
    // Docker's random wildcard publisher does not consult this host's loopback
    // socket allocation. WSL can reset that path even after PostgreSQL is ready.
    // Explicit publication also keeps restart mappings stable and host-only.
    private val portReservation = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val container = PostgreSQLContainer(DockerImageName.parse(System.getProperty("asyncttwiz.postgresImage")).asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("foundation_integration")
        .withUsername("foundation_test")
        .withPassword(UUID.randomUUID().toString())
        .withLabel("org.asyncttwiz.owner", "server-integration")
        .withReuse(false)
        .apply { setPortBindings(listOf("127.0.0.1:${portReservation.localPort}:5432")) }

    fun start(): OwnedPostgres {
        portReservation.close()
        container.start()
        println("OWNED integration PostgreSQL container=${container.containerId} image=${container.dockerImageName}")
        // PostgreSQLContainer 2.0.5 waits for two server log messages, not a
        // successful JDBC round trip through the host's newly published port.
        // A transient WSL/Docker port-path reset must fail readiness, not the
        // whole scenario, until the bounded startup deadline has expired.
        awaitStartupConnection {
            DriverManager.getConnection(container.jdbcUrl, Properties().apply {
                setProperty("user", container.username)
                setProperty("password", container.password)
                setProperty("connectTimeout", "1")
                setProperty("socketTimeout", "1")
            })
        }.use { connection ->
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

    companion object {
        internal fun awaitStartupConnection(timeoutMillis: Long = 10_000, connect: () -> Connection): Connection {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (true) {
                try {
                    return connect()
                } catch (failure: SQLException) {
                    // Only connection establishment is retried. Authentication,
                    // SQL/schema errors and all ordinary runtime operations fail
                    // normally, so outages during a test cannot be hidden.
                    if (failure.sqlState?.startsWith("08") != true || System.nanoTime() >= deadline) throw failure
                    println("WAIT integration PostgreSQL JDBC startup: transient connection failure")
                    Thread.sleep(50)
                }
            }
        }
    }

    override fun close() {
        portReservation.close()
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
