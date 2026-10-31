package org.asyncttwiz.server.database

import org.junit.jupiter.api.Assertions.assertEquals
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger

/**
 * A brand-new database for one test, created inside this test JVM's single [OwnedPostgres]
 * container. With [migrated] it is cloned from a template migrated once per JVM, so it holds
 * exactly what a fresh container plus [FoundationMigrations.migrate] would; without it the
 * database is empty, as a fresh container's was. It is dropped again on [close].
 *
 * Tests that stop, pause or restart the container, change cluster-wide state (roles) or
 * test migrations and the container itself keep their own [OwnedPostgres].
 */
class FreshDatabase(private val migrated: Boolean = true) : TestPostgres {
    private val name = "test_${counter.incrementAndGet()}"
    private var created = false

    fun start(): FreshDatabase {
        SharedContainer.create(name, if (migrated) SharedContainer.migratedTemplate() else "template1")
        created = true
        return this
    }

    override val settings: DatabaseSettings get() = SharedContainer.settings(name)

    override fun connection(): Connection = settings.let { DriverManager.getConnection(it.url(), it.user(), it.password()) }

    override fun close() {
        if (!created) return
        created = false
        SharedContainer.drop(name)
    }

    private companion object { val counter = AtomicInteger() }

    /** Lazily started on first use, stopped and verified removed when the test JVM exits; never reused across runs. */
    private object SharedContainer {
        private const val TEMPLATE = "foundation_migrated_template"
        private val postgres: OwnedPostgres = OwnedPostgres().start(shared = true).also { owned ->
            Runtime.getRuntime().addShutdownHook(Thread { owned.close() })
        }
        // Docker's published loopback port runs every round trip through the userland proxy and,
        // on this WSL host, the mirrored Windows loopback: about 2 ms per query instead of 0.2 ms,
        // and the path that sometimes resets new connections. The container's bridge address is
        // host-only too, so prefer it. It is chosen once, before any test, never per query.
        private val address: String = run {
            val container = postgres.container
            val bridge = container.dockerClient.inspectContainerCmd(container.containerId).exec()
                .networkSettings.networks.orEmpty().values.mapNotNull { it.ipAddress?.takeIf(String::isNotEmpty) }.firstOrNull()
            val reachable = bridge != null && try {
                OwnedPostgres.awaitStartupConnection(timeoutMillis = 3_000) {
                    DriverManager.getConnection("jdbc:postgresql://$bridge:5432/${container.databaseName}?connectTimeout=1&socketTimeout=1",
                        container.username, container.password)
                }.use { connection -> connection.createStatement().use { it.execute("SELECT 1") } }
                true
            } catch (unreachable: java.sql.SQLException) { false }
            if (reachable) "$bridge:5432" else "${container.host}:${container.getMappedPort(5432)}"
                .also { println("Shared test PostgreSQL: bridge address unreachable, using the published port") }
        }
        private var templateReady = false

        fun settings(database: String): DatabaseSettings {
            val query = postgres.container.jdbcUrl.substringAfter('?', "")
            return DatabaseSettings("jdbc:postgresql://$address/$database" + if (query.isEmpty()) "" else "?$query",
                postgres.container.username, postgres.container.password)
        }

        @Synchronized fun migratedTemplate(): String {
            if (!templateReady) {
                admin { it.execute("CREATE DATABASE $TEMPLATE") }
                FoundationMigrations.migrate(settings(TEMPLATE))
                // No connection may reach the template, so every clone starts from the migrated state.
                admin { it.execute("ALTER DATABASE $TEMPLATE WITH IS_TEMPLATE true ALLOW_CONNECTIONS false") }
                templateReady = true
            }
            return TEMPLATE
        }

        fun create(database: String, template: String) = admin { it.execute("CREATE DATABASE $database TEMPLATE $template") }

        fun drop(database: String) = admin { statement ->
            statement.execute("DROP DATABASE $database WITH (FORCE)")
            statement.executeQuery("SELECT count(*) FROM pg_database WHERE datname = '$database'").use { rows ->
                rows.next(); assertEquals(0, rows.getInt(1), "Fresh test database was not dropped")
            }
        }

        private fun <T> admin(work: (java.sql.Statement) -> T): T =
            settings(postgres.container.databaseName).let { DriverManager.getConnection(it.url(), it.user(), it.password()) }
                .use { connection -> connection.createStatement().use(work) }
    }
}
