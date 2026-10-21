package org.asyncttwiz.server

import org.asyncttwiz.server.database.DatabaseSettings
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI

class SpringStartupTest {
    @Test fun `explicit contexts start without database connection on startup thread`() {
        val address = InetAddress.getByName("127.0.0.1")
        // This WSL host needs reservations on the eventual bind address, not wildcard -> loopback.
        val port = ServerSocket(0, 50, address).use { it.localPort }
        val health = ServerSocket(0, 50, address).use { it.localPort }
        java.nio.channels.ServerSocketChannel.open().use { socket ->
            socket.bind(java.net.InetSocketAddress(address, health))
            println("PASS minimal JDK NIO fixed bind actual=${socket.localAddress}")
        }
        val settings = ServiceSettings(DatabaseSettings("jdbc:postgresql://127.0.0.1:1/fixture", "fixture", "fixture"),
            URI("https://synthetic.invalid"), address, port, address, health)
        SpringService(settings).use { it.start() }
        println("PASS explicit Spring contexts start/close independently of database readiness")
    }
}
