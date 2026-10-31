package org.asyncttwiz.server

import org.asyncttwiz.server.database.DatabaseSettings
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.web.server.PortInUseException
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI

class SpringStartupTest {
    private val database = DatabaseSettings("jdbc:postgresql://127.0.0.1:1/fixture", "fixture", "fixture")

    @Test fun `explicit contexts start without database connection on startup thread`() {
        SpringService(EphemeralPorts.settings(database)).use { service ->
            service.start()
            assertTrue(service.publicPort() > 0); assertTrue(service.healthPort() > 0)
            assertNotEquals(service.publicPort(), service.healthPort())
        }
        println("PASS explicit Spring contexts start/close independently of database readiness")
    }

    @Test fun `configured fixed ports are bound exactly as configured`() {
        val address = InetAddress.getByName("127.0.0.1")
        repeat(5) {
            // Choosing a fixed port is inherently racy: another process may take it before the bind, so only
            // a bind failure is retried with freshly chosen ports. Production always configures fixed ports.
            // This WSL host needs reservations on the eventual bind address, not wildcard -> loopback.
            val port = ServerSocket(0, 50, address).use { it.localPort }
            val health = ServerSocket(0, 50, address).use { it.localPort }
            try {
                java.nio.channels.ServerSocketChannel.open().use { socket ->
                    socket.bind(java.net.InetSocketAddress(address, health))
                    println("PASS minimal JDK NIO fixed bind actual=${socket.localAddress}")
                }
                SpringService(ServiceSettings(database, URI("https://synthetic.invalid"), address, port, address, health)).use { service ->
                    service.start()
                    assertEquals(port, service.publicPort())
                    assertEquals(health, service.healthPort())
                }
                println("PASS configured public/health ports bound as configured")
                return
            } catch (failure: Exception) {
                if (generateSequence<Throwable>(failure) { it.cause }.none { it is BindException || it is PortInUseException }) throw failure
                println("DIAGNOSTIC chosen port taken before bind; retrying with new ports")
            }
        }
        fail<Unit>("Could not bind freshly chosen fixed ports")
    }
}
