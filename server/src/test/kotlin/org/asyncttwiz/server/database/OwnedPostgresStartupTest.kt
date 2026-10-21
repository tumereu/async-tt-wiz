package org.asyncttwiz.server.database

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.SQLException

/** Readiness regressions for the actual Docker-published JDBC path, not Compose. */
class OwnedPostgresStartupTest {
    @Test fun `fresh Testcontainers startup exposes a usable JDBC connection`() {
        repeat(30) { iteration ->
            OwnedPostgres().use { database ->
                try {
                    database.start()
                } catch (failure: SQLException) {
                    val info = database.container.dockerClient.inspectContainerCmd(database.container.containerId).exec()
                    println("DIAGNOSTIC mapped-port=${database.container.getMappedPort(5432)} host=${database.container.host} bindings=${info.networkSettings.ports}")
                    println("DIAGNOSTIC database logs: ${database.container.logs}")
                    for (network in info.networkSettings.networks.orEmpty().values) {
                        try {
                            java.sql.DriverManager.getConnection("jdbc:postgresql://${network.ipAddress}:5432/foundation_integration?connectTimeout=1&socketTimeout=1", database.container.username, database.container.password).use {
                                println("DIAGNOSTIC direct bridge connection succeeds while published host connection fails")
                            }
                        } catch (direct: SQLException) {
                            println("DIAGNOSTIC direct bridge failure state=${direct.sqlState} cause=${direct.cause?.javaClass?.simpleName}")
                        }
                    }
                    throw failure
                }
                val bindings = database.container.dockerClient.inspectContainerCmd(database.container.containerId)
                    .exec().networkSettings.ports.bindings.values.flatMap { it.toList() }
                assertTrue(bindings.isNotEmpty())
                assertTrue(bindings.all { it.hostIp == "127.0.0.1" })
                database.connection().use { connection ->
                    connection.createStatement().use { sql ->
                        sql.executeQuery("SELECT 1").use { rows ->
                            assertTrue(rows.next() && rows.getInt(1) == 1)
                        }
                    }
                }
                println("PASS immediate JDBC round trip after actual startup iteration=$iteration")
            }
        }
    }

    @Test fun `startup alone retries connection failures before returning the real database connection`() {
        OwnedPostgres().use { database ->
            database.start()
            var attempts = 0
            OwnedPostgres.awaitStartupConnection {
                attempts++
                if (attempts <= 2) throw SQLException("synthetic startup reset", "08001")
                database.connection()
            }.use { connection ->
                connection.createStatement().use { sql ->
                    sql.executeQuery("SELECT current_database()").use { rows ->
                        assertTrue(rows.next())
                        assertEquals("foundation_integration", rows.getString(1))
                    }
                }
            }
            assertEquals(3, attempts)
        }
    }

    @Test fun `permanent connection failure is bounded and authentication or SQL failures are never retried`() {
        val unavailable = SQLException("synthetic unavailable", "08001")
        val started = System.nanoTime()
        var attempts = 0
        assertSame(unavailable, assertThrows(SQLException::class.java) {
            OwnedPostgres.awaitStartupConnection(timeoutMillis = 120) { attempts++; throw unavailable }
        })
        assertTrue(attempts >= 2)
        assertTrue(System.nanoTime() - started < java.util.concurrent.TimeUnit.SECONDS.toNanos(2))
        for (state in listOf("28P01", "42601", null)) {
            attempts = 0
            val invalid = SQLException("synthetic invalid configuration or SQL", state)
            assertSame(invalid, assertThrows(SQLException::class.java) {
                OwnedPostgres.awaitStartupConnection { attempts++; throw invalid }
            })
            assertEquals(1, attempts)
        }
    }
}
