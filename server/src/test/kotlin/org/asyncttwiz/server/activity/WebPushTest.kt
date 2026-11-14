package org.asyncttwiz.server.activity

import com.sun.net.httpserver.HttpServer
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECPrivateKeySpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.asyncttwiz.protocol.ActivityWire
import org.asyncttwiz.protocol.WebPushNotice
import org.asyncttwiz.protocol.WebPushSubscription
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** WEB-052: the server's Web Push: its VAPID key, the messages' encryption (RFC 8291) and token (RFC 8292), and sending. */
class WebPushTest {
    private fun b64(text: String) = WebPushCrypto.unbase64(text)

    /** The browser's side of RFC 8291: [body] decrypted with the browser's key pair and secret. */
    private fun decrypt(body: ByteArray, browser: KeyPair, auth: ByteArray): ByteArray {
        val salt = body.copyOfRange(0, 16)
        assertEquals(4096, ByteBuffer.wrap(body, 16, 4).int)
        val length = body[20].toInt()
        val serverKey = body.copyOfRange(21, 21 + length)
        val shared = KeyAgreement.getInstance("ECDH").run { init(browser.private); doPhase(WebPushCrypto.publicKey(serverKey), true); generateSecret() }
        val browserKey = WebPushCrypto.uncompressed(browser.public as ECPublicKey)
        val ikm = WebPushCrypto.hmac(WebPushCrypto.hmac(auth, shared), "WebPush: info".toByteArray() + byteArrayOf(0) + browserKey + serverKey, byteArrayOf(1))
        val prk = WebPushCrypto.hmac(salt, ikm)
        val key = WebPushCrypto.hmac(prk, "Content-Encoding: aes128gcm".toByteArray(), byteArrayOf(0, 1)).copyOf(16)
        val nonce = WebPushCrypto.hmac(prk, "Content-Encoding: nonce".toByteArray(), byteArrayOf(0, 1)).copyOf(12)
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); doFinal(body.copyOfRange(21 + length, body.size))
        }
        assertEquals(2.toByte(), plain.last(), "the last record's delimiter")
        return plain.copyOf(plain.size - 1)
    }

    private fun keyPair(publicKey: String, privateKey: String): KeyPair = KeyPair(WebPushCrypto.publicKey(b64(publicKey)),
        KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(1, b64(privateKey)), WebPushCrypto.P256)))

    private fun pem(keys: KeyPair): String {
        fun block(name: String, bytes: ByteArray) = "-----BEGIN $name-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(bytes) + "\n-----END $name-----\n"
        return block("PRIVATE KEY", keys.private.encoded) + block("PUBLIC KEY", keys.public.encoded)
    }

    // RFC 8291 Appendix A: the example message, encrypted with the example's keys and salt
    @Test fun `a message is encrypted as RFC 8291's example`() {
        val server = keyPair("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
            "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw")
        val body = WebPushCrypto.encrypt("When I grow up, I want to be a watermelon".toByteArray(),
            b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"),
            b64("BTBZMqHH6r4Tts7J_aSIgg"), server, b64("DGv6ra1nlYgDCS1FRnbzlw"))
        assertEquals("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_" +
            "yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN", WebPushCrypto.base64(body))
        val browser = keyPair("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
            "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94")
        assertEquals("When I grow up, I want to be a watermelon", decrypt(body, browser, b64("BTBZMqHH6r4Tts7J_aSIgg")).decodeToString())
    }

    @Test fun `the key file holds both halves of one P-256 key, and its public key is the browsers' 65-byte point`() {
        val keys = WebPushCrypto.newKeyPair()
        val parsed = WebPushKeys.parse(pem(keys))
        assertEquals(65, b64(parsed.publicKey).size)
        assertEquals(WebPushCrypto.base64(WebPushCrypto.uncompressed(keys.public as ECPublicKey)), parsed.publicKey)
        assertThrows(IllegalArgumentException::class.java) {
            WebPushKeys.parse(pem(keys).substringBefore("-----BEGIN PUBLIC KEY") + pem(WebPushCrypto.newKeyPair()).substringAfter("-----END PRIVATE KEY-----\n"))
        }
        assertThrows(IllegalArgumentException::class.java) { WebPushKeys.parse("no key here") }
        assertFalse(parsed.toString().contains(parsed.publicKey))
    }

    @Test fun `only the browsers' push services are written to`() {
        for (endpoint in listOf("https://fcm.googleapis.com/fcm/send/abc", "https://updates.push.services.mozilla.com/wpush/v2/abc",
                "https://web.push.apple.com/abc", "https://wns2-db5p.notify.windows.com/w/?token=abc", "https://android.googleapis.com/gcm/send/x"))
            assertTrue(WebPushSender.pushService(URI(endpoint)), endpoint)
        for (endpoint in listOf("http://fcm.googleapis.com/fcm/send/abc", "https://evilfcm.googleapis.com.example.com/x",
                "https://127.0.0.1/x", "https://localhost/x", "https://fcm.googleapis.com:8443/x", "https://user@fcm.googleapis.com/x",
                "https://example.com/push.apple.com"))
            assertFalse(WebPushSender.pushService(URI(endpoint)), endpoint)
    }

    // WEB-052: a notice reaches the push service encrypted for its browser alone, with the server's VAPID token; the push
    // service's answers are told apart (delivered, the subscription gone, try later)
    @Test fun `a notice is posted encrypted with the server's token, and the push service's answers are told apart`() {
        val keys = WebPushKeys.parse(pem(WebPushCrypto.newKeyPair()))
        val browser = WebPushCrypto.newKeyPair()
        val auth = ByteArray(16) { it.toByte() }
        val received = mutableListOf<Triple<Map<String, String>, ByteArray, String>>()
        var answer = 201 to mapOf<String, String>()
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/push/") { exchange ->
            received += Triple(exchange.requestHeaders.mapValues { it.value.single() }.mapKeys { it.key.lowercase() },
                exchange.requestBody.readAllBytes(), exchange.requestMethod)
            answer.second.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            exchange.sendResponseHeaders(answer.first, -1); exchange.close()
        }
        http.start()
        try {
            val clock = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC)
            val sender = WebPushSender(keys, "https://atw.example.invalid", allowed = { it.host == "127.0.0.1" }, clock = clock)
            val subscription = WebPushSubscription("http://127.0.0.1:${http.address.port}/push/abc",
                WebPushCrypto.base64(WebPushCrypto.uncompressed(browser.public as ECPublicKey)), WebPushCrypto.base64(auth))
            val notice = WebPushNotice("Private table · vs Inviter", "Your move", "game:g", "/games/g", 1L)
            assertEquals(PushResult(PushStatus.DELIVERED), sender.send(subscription, notice))
            val (headers, body, method) = received.single()
            assertEquals("POST", method)
            assertEquals("aes128gcm", headers["content-encoding"]); assertEquals("86400", headers["ttl"])
            assertEquals(notice, ActivityWire.json.decodeFromString(WebPushNotice.serializer(), decrypt(body, browser, auth).decodeToString()))
            // RFC 8292: "vapid t=<JWT for the push service's origin, signed with the server's key>, k=<its public key>"
            val authorization = Regex("vapid t=([^,]+), k=(.+)").matchEntire(headers.getValue("authorization"))!!
            assertEquals(keys.publicKey, authorization.groupValues[2])
            val (header, claims, signature) = authorization.groupValues[1].split('.')
            assertTrue(Signature.getInstance("SHA256withECDSAinP1363Format").run {
                initVerify(keys.public); update("$header.$claims".toByteArray()); verify(b64(signature))
            })
            val decoded = ActivityWire.json.parseToJsonElement(b64(claims).decodeToString()).jsonObject
            assertEquals("http://127.0.0.1:${http.address.port}", decoded.getValue("aud").jsonPrimitive.content)
            assertEquals("https://atw.example.invalid", decoded.getValue("sub").jsonPrimitive.content)
            assertEquals(clock.instant().epochSecond + 12 * 3600, decoded.getValue("exp").jsonPrimitive.long)
            answer = 410 to emptyMap(); assertEquals(PushResult(PushStatus.INVALID_REGISTRATION), sender.send(subscription, notice))
            answer = 404 to emptyMap(); assertEquals(PushResult(PushStatus.INVALID_REGISTRATION), sender.send(subscription, notice))
            answer = 429 to mapOf("Retry-After" to "30"); assertEquals(PushResult(PushStatus.TRANSIENT, 30), sender.send(subscription, notice))
            answer = 400 to emptyMap(); assertEquals(PushResult(PushStatus.FAILED), sender.send(subscription, notice))
            // Never to an address that isn't a push service
            val sent = received.size
            assertEquals(PushResult(PushStatus.INVALID_REGISTRATION), WebPushSender(keys, "https://atw.example.invalid").send(subscription, notice))
            assertEquals(sent, received.size)
        } finally { http.stop(0) }
    }
}
