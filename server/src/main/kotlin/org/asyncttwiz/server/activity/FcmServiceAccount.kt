package org.asyncttwiz.server.activity

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.ActivityWire

/** Explicit opt-in only. Credentials and minted tokens never enter wire data or logs. */
class FcmServiceAccount(json: String, private val clock: Clock = Clock.systemUTC(),
    private val endpoint: URI = URI("https://oauth2.googleapis.com/token"),
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NEVER).build()) {
    private val configuration = ActivityWire.json.parseToJsonElement(json).jsonObject
    val project = configuration.getValue("project_id").jsonPrimitive.content
    private val email = configuration.getValue("client_email").jsonPrimitive.content
    private val keyId = configuration["private_key_id"]?.jsonPrimitive?.content
    private val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(
        configuration.getValue("private_key").jsonPrimitive.content
            .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace(Regex("\\s"), ""))))
    private var cached: String? = null
    private var expires = Instant.EPOCH

    @Synchronized fun accessToken(): String {
        val now = clock.instant()
        cached?.takeIf { now < expires.minusSeconds(60) }?.let { return it }
        val header = buildJsonObject { put("alg", "RS256"); put("typ", "JWT"); keyId?.let { put("kid", it) } }
        val claims = buildJsonObject {
            put("iss", email); put("scope", "https://www.googleapis.com/auth/firebase.messaging")
            put("aud", endpoint.toString()); put("iat", now.epochSecond); put("exp", now.plusSeconds(3600).epochSecond)
        }
        val unsigned = "${encode(header.toString().toByteArray())}.${encode(claims.toString().toByteArray())}"
        val signature = Signature.getInstance("SHA256withRSA").run { initSign(key); update(unsigned.toByteArray()); sign() }
        val assertion = "$unsigned.${encode(signature)}"
        val body = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${URLEncoder.encode(assertion, Charsets.UTF_8)}"
        val response = client.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofInputStream())
        val bytes = response.body().use { it.readNBytes(16385) }
        check(response.statusCode() in 200..299 && bytes.size <= 16384) { "FCM token exchange failed" }
        val token = ActivityWire.json.parseToJsonElement(bytes.decodeToString()).jsonObject
        val value = token.getValue("access_token").jsonPrimitive.content
        require(value.isNotEmpty() && value.all { it.code in 33..126 }) { "Invalid FCM access token" }
        val seconds = token.getValue("expires_in").jsonPrimitive.long.coerceIn(1, 3600)
        cached = value; expires = now.plusSeconds(seconds)
        return value
    }

    companion object {
        private fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        @JvmStatic fun fromEnvironment(): ActivityPushAdapter? {
            val file = System.getenv("FCM_SERVICE_ACCOUNT_FILE")?.takeIf { it.isNotBlank() } ?: return null
            val path = Path.of(file)
            require(Files.size(path) <= 65536) { "FCM service account file too large" }
            val account = FcmServiceAccount(Files.readString(path))
            return FcmActivityAdapter(account.project, account::accessToken)
        }
    }
}
