package org.asyncttwiz.storage

import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Resolves immediately before each new connection. OkHttp connects to these vetted addresses,
 * not a second unchecked DNS lookup; TLS still verifies the original hostname. */
class ArtworkUrlPolicy(val syntheticOrigin: String? = null,
    private val lookup: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() }) {
    init {
        syntheticOrigin?.let {
            val uri = URI(it)
            require(uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535 &&
                uri.rawUserInfo == null && uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null)
        }
    }
    fun validate(url: String): URI = URI(url).also { uri ->
        require(url.length <= 4096 && uri.host != null && uri.rawUserInfo == null && uri.rawFragment == null)
        if (syntheticOrigin != null) require("${uri.scheme}://${uri.rawAuthority}" == syntheticOrigin)
        else require(uri.scheme == "https" && (uri.port == -1 || uri.port == 443))
    }
    fun addresses(host: String): List<InetAddress> {
        if (syntheticOrigin != null) {
            require(host == "127.0.0.1")
            return listOf(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        }
        return lookup(host).also { addresses ->
            require(addresses.isNotEmpty() && addresses.all(::publicAddress)) {
                "$host resolved to non-public addresses: ${addresses.joinToString { it.hostAddress.orEmpty() }}"
            }
        }
    }
    companion object {
        private val NAT64_PREFIX = listOf(0, 0x64, 0xff, 0x9b, 0, 0, 0, 0, 0, 0, 0, 0)
        fun publicAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress) return false
            val b = address.address.map { it.toInt() and 255 }
            if (b.size == 4) return !(b[0] in listOf(0, 10, 127) || b[0] >= 224 ||
                (b[0] == 100 && b[1] in 64..127) || (b[0] == 169 && b[1] == 254) ||
                (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && (b[1] == 168 || (b[1] == 0 && b[2] in listOf(0, 2)))) ||
                (b[0] == 198 && (b[1] in 18..19 || (b[1] == 51 && b[2] == 100))) || (b[0] == 203 && b[1] == 0 && b[2] == 113))
            // IPv6-only mobile networks (464XLAT) answer IPv4-only hosts such as the image CDN with
            // NAT64 addresses (64:ff9b::/96): public exactly when the embedded IPv4 address is.
            if (b.size == 16 && b.subList(0, 12) == NAT64_PREFIX)
                return publicAddress(InetAddress.getByAddress(address.address.copyOfRange(12, 16)))
            // Only ordinary global unicast; exclude translation/tunneling and special-use blocks.
            return b.size == 16 && b[0] in 0x20..0x3f && !(b[0] == 0x20 &&
                ((b[1] == 1 && (b[2] < 2 || (b[2] == 0x0d && b[3] == 0xb8))) || b[1] == 2))
        }
    }
}

data class ArtworkResponse(val status: Int, val bytes: ByteArray = byteArrayOf(), val type: String = "",
    val etag: String = "", val modified: String = "", val retryAt: Long = 0)

fun interface ArtworkTransport {
    fun get(url: String, maximum: Long, etag: String, modified: String): ArtworkResponse
}

/** One installation-wide spacing for provider API (metadata) requests, including their redirects:
 * Scryfall asks for 50-100 ms between api.scryfall.com calls. No bursts. A provider Retry-After
 * cools down every artwork request, throttled or not. */
class ArtworkThrottle(private val now: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep, private val interval: Long = 100) {
    private var next = 0L
    private var blocked = 0L
    @Synchronized fun acquire() {
        cooling()
        val wait = next - now()
        if (wait > 0) sleep(wait)
        next = now() + interval
    }
    @Synchronized fun cooling() = check(now() >= blocked) { "Artwork provider cooling down" }
    @Synchronized fun retryAfter(until: Long) { blocked = maxOf(blocked, until) }
}

enum class ArtworkPriority { VISIBLE, PREFETCH }

/** Bounded concurrent artwork downloads. Waiting visible requests are always admitted before
 * waiting prefetch; a prefetch ticket is promoted when a visible request joins its download. */
class ArtworkGate(val permits: Int = 6) {
    class Ticket internal constructor(visible: Boolean) {
        @Volatile var visible = visible
            internal set
    }
    init { require(permits in 1..16) }
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val waiting = ArrayList<Ticket>()
    private var active = 0

    fun ticket(priority: ArtworkPriority) = Ticket(priority == ArtworkPriority.VISIBLE)
    fun promote(ticket: Ticket) = lock.withLock {
        if (!ticket.visible) { ticket.visible = true; changed.signalAll() }
    }
    /** Interruptible while waiting: a cancelled caller never occupies a download slot. */
    fun <T> run(ticket: Ticket, block: () -> T): T {
        lock.lockInterruptibly()
        try {
            waiting += ticket
            try { while (active >= permits || next() !== ticket) changed.await() }
            finally { waiting.remove(ticket); changed.signalAll() }
            active++
        } finally { lock.unlock() }
        try { return block() } finally { lock.withLock { active--; changed.signalAll() } }
    }
    private fun next() = waiting.firstOrNull { it.visible } ?: waiting.firstOrNull()
}

/** Scryfall's image CDN (cards.scryfall.io) is not rate limited; only its API is. Image downloads
 * are bounded by [ArtworkGate] concurrency instead. */
fun scryfallApiRequest(uri: URI): Boolean = uri.host.equals("api.scryfall.com", ignoreCase = true)

/** Dedicated client has NO service auth, cookie storage, proxy credentials or automatic redirects.
 * No network Coil module is installed: all artwork bytes pass through this boundary. */
class HttpArtworkTransport(private val policy: ArtworkUrlPolicy = ArtworkUrlPolicy(),
    private val userAgent: String = "AsyncTabletopWizards/0.1.0 (artwork client)",
    private val throttle: ArtworkThrottle = ArtworkThrottle(),
    private val now: () -> Long = System::currentTimeMillis,
    private val throttled: (URI) -> Boolean = ::scryfallApiRequest) : ArtworkTransport, AutoCloseable {
    private val client = OkHttpClient.Builder().dns(object : Dns {
        override fun lookup(hostname: String) = policy.addresses(hostname)
    }).proxy(Proxy.NO_PROXY)
        .cookieJar(CookieJar.NO_COOKIES).followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).build()

    override fun get(url: String, maximum: Long, etag: String, modified: String): ArtworkResponse {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        var uri = policy.validate(url)
        repeat(4) { redirects ->
            policy.validate(uri.toString())
            policy.addresses(uri.host)
            if (throttled(uri)) throttle.acquire() else throttle.cooling()
            check(System.nanoTime() < deadline)
            val request = Request.Builder().url(uri.toString()).header("User-Agent", userAgent)
                .header("Accept", "application/json, image/png, image/jpeg, image/webp")
                .header("Accept-Encoding", "identity").apply {
                    // Validators are public data but need not cross an origin redirect either.
                    if (uri == URI(url)) {
                        if (etag.isNotEmpty()) header("If-None-Match", etag)
                        if (modified.isNotEmpty()) header("If-Modified-Since", modified)
                    }
                }.build()
            val call = client.newCall(request)
            call.timeout().timeout((deadline - System.nanoTime()).coerceAtLeast(1), TimeUnit.NANOSECONDS)
            call.execute().use { response ->
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    check(redirects < 3)
                    uri = policy.validate(uri.resolve(requireNotNull(response.header("Location"))).toString())
                } else {
                    val retry = if (response.code == 429) retryAfter(response.header("Retry-After"), now()) else 0
                    if (retry > 0) throttle.retryAfter(retry)
                    val bytes = if (response.code == 200) {
                        check(response.header("Content-Encoding").let { it == null || it == "identity" })
                        val body = requireNotNull(response.body)
                        check(body.contentLength() <= maximum)
                        body.byteStream().use { LimitedInput(it, maximum).readBytes() }.also {
                            check(body.contentLength() < 0 || it.size.toLong() == body.contentLength()) { "Incomplete artwork download" }
                        }
                    } else byteArrayOf()
                    return ArtworkResponse(response.code, bytes, response.header("Content-Type").orEmpty().substringBefore(';'),
                        response.header("ETag").orEmpty().take(1024), response.header("Last-Modified").orEmpty().take(1024), retry)
                }
            }
        }
        error("Artwork redirect limit")
    }
    /** Drops pooled connections, which can be dead after the app's network was cut off. */
    fun evictConnections() = client.connectionPool.evictAll()
    override fun close() { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    companion object {
        fun retryAfter(value: String?, now: Long): Long = runCatching {
            val seconds = value?.toLongOrNull()
            if (seconds != null && seconds >= 0) Math.addExact(now, Math.multiplyExact(seconds, 1000))
            else ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli().coerceAtLeast(now)
        }.getOrDefault(now + 60_000)
    }
}
