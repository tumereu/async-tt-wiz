package org.asyncttwiz.server.activity

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.ActivityWire
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FcmServiceAccountTest {
    @Test fun `RS256 assertion is exchanged and token cached until shortly before expiry`() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val key = "-----BEGIN PRIVATE KEY-----\n${Base64.getEncoder().encodeToString(pair.private.encoded)}\n-----END PRIVATE KEY-----"
        val json = buildJsonObject { put("project_id", "fixture-project"); put("client_email", "service@example.invalid")
            put("private_key_id", "fixture-key"); put("private_key", key) }.toString()
        val clock = ActivityClock(); val requests = AtomicInteger(); val assertions = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/token") { exchange ->
            val fields = exchange.requestBody.use { it.readAllBytes().decodeToString() }.split('&').associate {
                val parts = it.split('=', limit = 2); parts[0] to URLDecoder.decode(parts[1], Charsets.UTF_8)
            }
            assertions += fields.getValue("assertion")
            val body = """{"access_token":"token-${requests.incrementAndGet()}","expires_in":3600,"token_type":"Bearer"}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong()); exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val endpoint = URI("http://127.0.0.1:${server.address.port}/token")
            val account = FcmServiceAccount(json, clock, endpoint)
            assertEquals("fixture-project", account.project)
            assertEquals("token-1", account.accessToken()); assertEquals("token-1", account.accessToken())
            assertEquals(1, requests.get())
            val jwt = assertions.single().split('.')
            assertTrue(Signature.getInstance("SHA256withRSA").run {
                initVerify(pair.public); update("${jwt[0]}.${jwt[1]}".toByteArray()); verify(Base64.getUrlDecoder().decode(jwt[2]))
            })
            val header = ActivityWire.json.parseToJsonElement(Base64.getUrlDecoder().decode(jwt[0]).decodeToString()).jsonObject
            assertEquals("RS256", header.getValue("alg").jsonPrimitive.content)
            val claims = ActivityWire.json.parseToJsonElement(Base64.getUrlDecoder().decode(jwt[1]).decodeToString()).jsonObject
            assertEquals("service@example.invalid", claims.getValue("iss").jsonPrimitive.content)
            assertEquals(endpoint.toString(), claims.getValue("aud").jsonPrimitive.content)
            assertEquals("https://www.googleapis.com/auth/firebase.messaging", claims.getValue("scope").jsonPrimitive.content)
            assertEquals(clock.time.epochSecond + 3600, claims.getValue("exp").jsonPrimitive.long)
            clock.time = clock.time.plusSeconds(3539); assertEquals("token-1", account.accessToken())
            clock.time = clock.time.plusSeconds(1); assertEquals("token-2", account.accessToken()); assertEquals(2, requests.get())
        } finally { server.stop(0) }
    }
}
