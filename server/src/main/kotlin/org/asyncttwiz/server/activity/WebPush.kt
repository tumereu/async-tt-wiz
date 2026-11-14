package org.asyncttwiz.server.activity

import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.time.Duration
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.asyncttwiz.protocol.ActivityHint
import org.asyncttwiz.protocol.ActivityKind
import org.asyncttwiz.protocol.ActivityTexts
import org.asyncttwiz.protocol.ActivityWire
import org.asyncttwiz.protocol.WebPushNotice
import org.asyncttwiz.protocol.WebPushSubscription
import org.slf4j.LoggerFactory

// WEB-052: Web Push (RFC 8030) to the browsers that turned their notifications on, with the JDK's own cryptography: the
// server's VAPID key pair identifies it to the push services (RFC 8292, an ES256 JWT), and each message is encrypted for its
// browser alone (RFC 8291, aes128gcm), so the push service carries the notification's words without reading them.

/** Sends one [WebPushNotice] to a browser's [WebPushSubscription]. */
fun interface WebPushAdapter { fun send(subscription: WebPushSubscription, notice: WebPushNotice): PushResult }

/** The server's VAPID key pair (P-256): [publicKey] as browsers subscribe with it (base64url of the uncompressed point). */
class WebPushKeys(val privateKey: ECPrivateKey, val public: ECPublicKey) {
    val publicKey: String = WebPushCrypto.base64(WebPushCrypto.uncompressed(public))

    init {
        // The two halves belong together: what one signs, the other verifies
        val probe = "atw-web-push".toByteArray()
        val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(privateKey); update(probe) }.sign()
        require(Signature.getInstance("SHA256withECDSA").apply { initVerify(public); update(probe) }.verify(signature)) {
            "The Web Push key file's public key isn't its private key's"
        }
        require(public.params.curve == WebPushCrypto.P256.curve) { "The Web Push key isn't a P-256 key" }
    }

    override fun toString() = "WebPushKeys[redacted]"

    companion object {
        /** The key pair from a PEM file's PKCS#8 "PRIVATE KEY" and X.509 "PUBLIC KEY" blocks (scripts/dev-server.sh makes one
         * with openssl). */
        fun parse(pem: String): WebPushKeys {
            fun block(name: String): ByteArray {
                val match = requireNotNull(Regex("-----BEGIN $name-----([A-Za-z0-9+/=\\s]+)-----END $name-----").find(pem)) {
                    "The Web Push key file has no $name"
                }
                return Base64.getMimeDecoder().decode(match.groupValues[1])
            }
            val factory = KeyFactory.getInstance("EC")
            return WebPushKeys(factory.generatePrivate(PKCS8EncodedKeySpec(block("PRIVATE KEY"))) as ECPrivateKey,
                factory.generatePublic(X509EncodedKeySpec(block("PUBLIC KEY"))) as ECPublicKey)
        }

        /** The key pair from the file WEB_PUSH_KEY_FILE names; null without one (the server then sends no browser
         * notifications, and Settings says so). */
        @JvmStatic fun fromEnvironment(env: Map<String, String> = System.getenv()): WebPushKeys? {
            val file = env["WEB_PUSH_KEY_FILE"]?.takeIf { it.isNotBlank() } ?: return null
            val path = Path.of(file)
            require(Files.isRegularFile(path) && Files.isReadable(path) && Files.size(path) <= 65536) { "Unreadable Web Push key file" }
            return parse(Files.readString(path))
        }
    }
}

/** RFC 8291's message encryption and RFC 8292's VAPID token, on the JDK's P-256, ECDH, HMAC and AES-GCM. */
object WebPushCrypto {
    val P256: ECParameterSpec = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
        .getParameterSpec(ECParameterSpec::class.java)
    private val random = SecureRandom()
    /** Each message's record size: one record holds the whole of a notification. */
    private const val RECORD_SIZE = 4096

    fun base64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun unbase64(text: String): ByteArray = Base64.getUrlDecoder().decode(text.trimEnd('='))

    /** [key] as the 65-byte uncompressed point (0x04, x, y). */
    fun uncompressed(key: ECPublicKey): ByteArray {
        fun coordinate(value: BigInteger) = value.toByteArray().let { bytes ->
            if (bytes.size >= 32) bytes.copyOfRange(bytes.size - 32, bytes.size) else ByteArray(32 - bytes.size) + bytes
        }
        return byteArrayOf(4) + coordinate(key.w.affineX) + coordinate(key.w.affineY)
    }

    /** The P-256 public key whose uncompressed point is [raw]. */
    fun publicKey(raw: ByteArray): ECPublicKey {
        require(raw.size == 65 && raw[0] == 4.toByte()) { "Not an uncompressed P-256 point" }
        val point = ECPoint(BigInteger(1, raw.copyOfRange(1, 33)), BigInteger(1, raw.copyOfRange(33, 65)))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, P256)) as ECPublicKey
    }

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    fun hmac(key: ByteArray, vararg data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); data.forEach(::update); doFinal()
    }

    /**
     * RFC 8291: [payload] encrypted for the browser whose public key is [browserKey] (its subscription's p256dh) and whose
     * secret is [auth], as one aes128gcm record: the salt, the record size, this message's own public key ([ephemeral]'s)
     * and the ciphertext.
     */
    fun encrypt(payload: ByteArray, browserKey: ByteArray, auth: ByteArray, ephemeral: KeyPair = newKeyPair(),
        salt: ByteArray = ByteArray(16).also(random::nextBytes)): ByteArray {
        require(payload.size <= RECORD_SIZE - 17 - 86) { "A Web Push message holds at most ${RECORD_SIZE - 103} bytes" }
        val serverKey = uncompressed(ephemeral.public as ECPublicKey)
        val shared = KeyAgreement.getInstance("ECDH").run { init(ephemeral.private); doPhase(publicKey(browserKey), true); generateSecret() }
        val keyInfo = "WebPush: info".toByteArray() + byteArrayOf(0) + browserKey + serverKey
        val ikm = hmac(hmac(auth, shared), keyInfo, byteArrayOf(1))
        val prk = hmac(salt, ikm)
        val contentKey = hmac(prk, "Content-Encoding: aes128gcm".toByteArray(), byteArrayOf(0, 1)).copyOf(16)
        val nonce = hmac(prk, "Content-Encoding: nonce".toByteArray(), byteArrayOf(0, 1)).copyOf(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(contentKey, "AES"), GCMParameterSpec(128, nonce))
        }
        // The last (only) record's padding delimiter is 2
        val encrypted = cipher.doFinal(payload + byteArrayOf(2))
        return ByteBuffer.allocate(16 + 4 + 1 + serverKey.size + encrypted.size).put(salt).putInt(RECORD_SIZE)
            .put(serverKey.size.toByte()).put(serverKey).put(encrypted).array()
    }

    /** RFC 8292: the ES256 JWT that tells the push service at [audience] (its origin) who sends, signed with [keys]. */
    fun vapidToken(keys: WebPushKeys, audience: String, subject: String, expiresAt: Long): String {
        val header = base64("""{"typ":"JWT","alg":"ES256"}""".toByteArray())
        val claims = base64(buildJsonObject { put("aud", audience); put("exp", expiresAt); put("sub", subject) }.toString().toByteArray())
        val signature = Signature.getInstance("SHA256withECDSAinP1363Format").run {
            initSign(keys.privateKey); update("$header.$claims".toByteArray()); sign()
        }
        return "$header.$claims.${base64(signature)}"
    }
}

/**
 * WEB-052: sends notifications to browsers' push services, as [subject] (the server's public address, RFC 8292's contact)
 * with [keys]. Only to a known push service's https address ([allowed]: [pushService]), never following a redirect.
 */
class WebPushSender @JvmOverloads constructor(private val keys: WebPushKeys, private val subject: String,
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NEVER).build(),
    private val allowed: (URI) -> Boolean = ::pushService, private val clock: Clock = Clock.systemUTC()) : WebPushAdapter {
    /** The public key browsers subscribe with. */
    val publicKey: String get() = keys.publicKey

    override fun send(subscription: WebPushSubscription, notice: WebPushNotice): PushResult = try {
        val endpoint = URI(subscription.endpoint)
        if (!allowed(endpoint)) {
            log.warn("Web Push to {} refused: not a push service", endpoint.host)
            PushResult(PushStatus.INVALID_REGISTRATION)
        } else {
            val audience = "${endpoint.scheme}://${endpoint.rawAuthority}"
            val token = WebPushCrypto.vapidToken(keys, audience, subject, clock.instant().epochSecond + 12 * 3600)
            val body = WebPushCrypto.encrypt(ActivityWire.json.encodeToString(WebPushNotice.serializer(), notice).toByteArray(),
                WebPushCrypto.unbase64(subscription.p256dh), WebPushCrypto.unbase64(subscription.auth))
            val response = client.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
                .header("Authorization", "vapid t=$token, k=${keys.publicKey}").header("TTL", "86400").header("Urgency", "high")
                .header("Content-Encoding", "aes128gcm").header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.discarding())
            val status = when (response.statusCode()) {
                in 200..299 -> PushStatus.DELIVERED
                404, 410 -> PushStatus.INVALID_REGISTRATION // The browser unsubscribed or the subscription expired
                429, in 500..599 -> PushStatus.TRANSIENT
                else -> PushStatus.FAILED
            }
            if (status != PushStatus.DELIVERED) log.info("Web Push to {} answered {}: {}", endpoint.host, response.statusCode(), status)
            PushResult(status, FcmActivityAdapter.retryAfter(response.headers().firstValue("Retry-After").orElse(null)))
        }
    } catch (failure: Exception) {
        log.info("Web Push failed: {}", failure.javaClass.simpleName)
        PushResult(PushStatus.TRANSIENT)
    }

    override fun toString() = "WebPushSender[redacted]"

    companion object {
        private val log = LoggerFactory.getLogger(WebPushSender::class.java)

        /** The browsers' push services: Chrome's, Edge's and Opera's (Google's and Microsoft's), Firefox's and Safari's. */
        private val SERVICES = listOf("fcm.googleapis.com", "android.googleapis.com", "push.services.mozilla.com",
            "push.apple.com", "notify.windows.com")

        /** Whether [endpoint] is a push service's https address (no other host gets the server's requests). */
        @JvmStatic fun pushService(endpoint: URI): Boolean {
            val host = endpoint.host?.lowercase() ?: return false
            return endpoint.scheme == "https" && endpoint.rawUserInfo == null && endpoint.port in listOf(-1, 443) &&
                SERVICES.any { host == it || host.endsWith(".$it") }
        }

        /** The sender for the key pair WEB_PUSH_KEY_FILE names, with the server's [publicBaseUrl] as its contact; null without
         * a key file. */
        @JvmStatic fun fromEnvironment(publicBaseUrl: URI): WebPushSender? =
            WebPushKeys.fromEnvironment()?.let { WebPushSender(it, publicBaseUrl.toString().trimEnd('/')) }
    }
}

/** WEB-051, WEB-052: what a browser is sent for a notice, and which of its switches lets it through. */
object WebPushNotices {
    /** [hint]'s notification in the Android app's words (LOB-072), replacing the earlier one of its game, pod or invitation
     * (its tag), and the app address its click opens: the game, the pod (a held pod challenge: the pod's Games tab). */
    fun of(hint: ActivityHint): WebPushNotice {
        val (title, body) = ActivityTexts.notification(hint)
        val kind = hint.kind
        val tag = when (kind) {
            ActivityKind.GAME, ActivityKind.GAME_REMOVAL -> "game:${hint.gameId}"
            ActivityKind.INVITATION -> "invite:${hint.identity}"
            ActivityKind.POD_INVITATION -> "pod-invite:${hint.gameId}"
            else -> "pod:${hint.gameId}"
        }
        val path = when {
            kind == ActivityKind.INVITATION && hint.held && hint.podId != null -> "/pods/${hint.podId}/games"
            kind == ActivityKind.POD_DESTROYED -> "/pods"
            ActivityTexts.pod(kind) -> "/pods/${hint.gameId}"
            else -> "/games/${hint.gameId}"
        }
        val time = hint.notice?.lastEventAt?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
        return WebPushNotice(title.take(300), body?.take(600), tag, path, time)
    }

    /** Whether [subscription]'s switches let [kind] through: Invitations, Draft activity or Game activity (LOB-072). */
    fun wanted(subscription: WebPushSubscription, kind: ActivityKind): Boolean = when {
        kind == ActivityKind.INVITATION || kind == ActivityKind.POD_INVITATION -> subscription.invitations
        ActivityTexts.pod(kind) -> subscription.drafts
        else -> subscription.games
    }
}
