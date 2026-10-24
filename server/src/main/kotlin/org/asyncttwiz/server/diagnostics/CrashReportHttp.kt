package org.asyncttwiz.server.diagnostics

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.SerializationException
import org.asyncttwiz.protocol.AccountJson
import org.asyncttwiz.protocol.CrashReport
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.OwnershipFailure

/** No read route. Reports are operational data, not a shared game mutation. */
class CrashReportHttp(private val runtime: ServiceRuntime, private val clock: Clock = Clock.systemUTC()) {
    private val accounts = AccountService(runtime)

    fun handle(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        if (request.requestURI != "/debug/crash-reports") return false
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("X-Content-Type-Options", "nosniff")
        try {
            if (!runtime.ready()) { response.status = 503; return true }
            val token = request.getHeaders("Authorization").toList().singleOrNull()
                ?.takeIf { it.startsWith("Bearer ", true) }?.substring(7)
            val principal = token?.let(accounts::authenticate)
            if (principal == null) { response.status = 401; return true }
            if (request.method != "POST") {
                response.setHeader("Allow", "POST"); response.status = 405; return true
            }
            val media = request.contentType.orEmpty().split(';').map(String::trim)
            if (!media.first().equals("application/json", true) || media.drop(1).any {
                    !it.equals("charset=UTF-8", true) && !it.equals("charset=\"UTF-8\"", true)
                }) { response.status = 415; return true }
            val bytes = request.inputStream.readNBytes(1_048_577)
            if (bytes.size > 1_048_576) { response.status = 413; return true }
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
            val input = AccountJson.format.decodeFromString<CrashReport>(text)
            val reported = try { Instant.parse(input.reportedAt) } catch (_: Exception) {
                response.status = 400; return true
            }
            if (input.appVersion.length > 512 || input.device.length > 512) { response.status = 400; return true }
            val accepted = store(UUID.fromString(principal.account.accountId), reported, input)
            response.status = if (accepted) 204 else 429
        } catch (_: SerializationException) { response.status = 400
        } catch (_: java.nio.charset.CharacterCodingException) { response.status = 400
        } catch (_: java.io.IOException) { response.status = 400
        } catch (_: OwnershipFailure) { response.status = 503
        } catch (_: RuntimeException) { response.status = 500 }
        return true
    }

    private fun store(account: UUID, reported: Instant, input: CrashReport): Boolean = runtime.transaction { connection ->
        val now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        // Serialize the small diagnostic queue: quota and global retention remain exact under concurrency.
        connection.createStatement().use { it.execute("LOCK TABLE debug_crash_report IN EXCLUSIVE MODE") }
        connection.prepareStatement("DELETE FROM debug_crash_daily_count WHERE day < ?").use {
            it.setObject(1, now.toLocalDate()); it.executeUpdate()
        }
        val accepted = connection.prepareStatement("""
            INSERT INTO debug_crash_daily_count(account_id, day, reports) VALUES (?, ?, 1)
            ON CONFLICT (account_id, day) DO UPDATE SET reports = debug_crash_daily_count.reports + 1
            WHERE debug_crash_daily_count.reports < 20 RETURNING reports
        """.trimIndent()).use {
            it.setObject(1, account); it.setObject(2, now.toLocalDate())
            it.executeQuery().use { rows -> rows.next() }
        }
        if (accepted) connection.prepareStatement("""
            INSERT INTO debug_crash_report(id, account_id, received_at, reported_at, app_version, device, body)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()).use {
            it.setObject(1, UUID.randomUUID()); it.setObject(2, account); it.setObject(3, now)
            it.setObject(4, OffsetDateTime.ofInstant(reported, ZoneOffset.UTC))
            it.setString(5, input.appVersion); it.setString(6, input.device)
            val bytes = input.body.toByteArray(Charsets.UTF_8)
            val body = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.IGNORE)
                .decode(ByteBuffer.wrap(bytes, 0, minOf(bytes.size, 65_536))).toString().replace("\u0000", "")
            it.setString(7, body); it.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM debug_crash_report WHERE received_at < ?").use {
            it.setObject(1, now.minusDays(30)); it.executeUpdate()
        }
        connection.createStatement().use { it.executeUpdate("""
            DELETE FROM debug_crash_report WHERE id IN (
                SELECT id FROM debug_crash_report ORDER BY received_at DESC, id DESC OFFSET 500
            )
        """.trimIndent()) }
        accepted
    }
}
