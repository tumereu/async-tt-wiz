package org.asyncttwiz.server.diagnostics

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.AccountError
import org.asyncttwiz.protocol.AccountJson
import org.asyncttwiz.protocol.ProblemReport
import org.asyncttwiz.protocol.ProblemReportReceipt
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountRateLimits
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.OwnershipFailure

/** Public: works before login (update gate). A valid session only attaches the account. No read route. */
class ProblemReportHttp @JvmOverloads constructor(private val runtime: ServiceRuntime, private val clock: Clock = Clock.systemUTC(),
    private val retained: Int = System.getenv("PROBLEM_REPORT_LIMIT")?.toIntOrNull()?.takeIf { it > 0 } ?: 200,
    private val limits: AccountRateLimits = AccountRateLimits()) {
    private val accounts = AccountService(runtime)
    private val random = SecureRandom()

    fun handle(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        if (request.requestURI != "/app/problem-reports") return false
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("X-Content-Type-Options", "nosniff")
        try {
            if (request.method != "POST") {
                response.setHeader("Allow", "POST"); response.status = 405; return true
            }
            val media = request.contentType.orEmpty().split(';').map(String::trim)
            if (!media.first().equals("application/json", true) || media.drop(1).any {
                    !it.equals("charset=UTF-8", true) && !it.equals("charset=\"UTF-8\"", true)
                }) { error(response, 415, "json_required"); return true }
            if (request.contentLengthLong > ProblemReport.MAX_BYTES) { error(response, 413, "request_too_large"); return true }
            val bytes = request.inputStream.readNBytes(ProblemReport.MAX_BYTES + 1)
            if (bytes.size > ProblemReport.MAX_BYTES) { error(response, 413, "request_too_large"); return true }
            if (!limits.allow("problem-report-ip", request.remoteAddr, 20, 600)) {
                response.setHeader("Retry-After", "600"); error(response, 429, "try_later"); return true
            }
            if (!runtime.ready()) { error(response, 503, "unavailable"); return true }
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
            val input = AccountJson.format.decodeFromString<ProblemReport>(text)
            val reported = runCatching { Instant.parse(input.reportedAt) }.getOrNull()
            if (reported == null || input.appVersion.length > 100 || input.versionCode < 0 || input.buildType.length > 40 ||
                input.device.length > 200 || input.android.length > 100 || input.description.length > ProblemReport.MAX_DESCRIPTION ||
                input.problem.length > ProblemReport.MAX_PROBLEM) { error(response, 400, "invalid_request"); return true }
            val account = request.getHeaders("Authorization").toList().singleOrNull()
                ?.takeIf { it.startsWith("Bearer ", true) }?.substring(7)?.let(accounts::authenticate)
                ?.let { UUID.fromString(it.account.accountId) }
            val code = store(account, reported, input)
            response.status = 201
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write(AccountJson.format.encodeToString(ProblemReportReceipt(code)))
        } catch (_: SerializationException) { error(response, 400, "invalid_request")
        } catch (_: IllegalArgumentException) { error(response, 400, "invalid_request")
        } catch (_: java.nio.charset.CharacterCodingException) { error(response, 400, "invalid_request")
        } catch (_: java.io.IOException) { response.status = 400
        } catch (_: OwnershipFailure) { error(response, 503, "unavailable")
        } catch (_: RuntimeException) { response.status = 500 }
        return true
    }

    private fun error(response: HttpServletResponse, status: Int, code: String) {
        response.status = status
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write(AccountJson.format.encodeToString(AccountError(code)))
    }

    private fun code(): String = String(CharArray(6) { ALPHABET[random.nextInt(ALPHABET.length)] })

    private fun store(account: UUID?, reported: Instant, input: ProblemReport): String = runtime.transaction { connection ->
        // Serialize inserts so retention stays exact under concurrency.
        connection.createStatement().use { it.execute("LOCK TABLE problem_report IN EXCLUSIVE MODE") }
        fun clean(value: String) = value.replace("\u0000", "")
        var code: String
        do {
            code = code()
            val inserted = connection.prepareStatement("""
                INSERT INTO problem_report(code, account_id, received_at, reported_at, app_version, version_code,
                    build_type, device, android, description, problem, log, logcat)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (code) DO NOTHING
            """.trimIndent()).use {
                it.setString(1, code); it.setObject(2, account)
                it.setObject(3, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                it.setObject(4, OffsetDateTime.ofInstant(reported, ZoneOffset.UTC))
                it.setString(5, clean(input.appVersion)); it.setInt(6, input.versionCode); it.setString(7, clean(input.buildType))
                it.setString(8, clean(input.device)); it.setString(9, clean(input.android))
                it.setString(10, clean(input.description)); it.setString(11, clean(input.problem))
                it.setString(12, clean(input.log)); it.setString(13, clean(input.logcat))
                it.executeUpdate()
            }
        } while (inserted == 0)
        connection.prepareStatement("""
            DELETE FROM problem_report WHERE id IN (
                SELECT id FROM problem_report ORDER BY id DESC OFFSET ?
            )
        """.trimIndent()).use { it.setInt(1, retained); it.executeUpdate() }
        code
    }

    private companion object {
        // No look-alikes (0/O, 1/I) so a player can read the code out.
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
