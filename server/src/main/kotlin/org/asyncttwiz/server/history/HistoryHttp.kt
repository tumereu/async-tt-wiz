package org.asyncttwiz.server.history

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountRateLimits
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.OwnershipFailure
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/**
 * The game history's reads (spec game-history §3): GET /v1/history (the account's records, the Stats screen), GET
 * /v1/games/{id}/history (a game's records, its lobby's History tab) and GET /v1/pods/{id}/matches (a pod's matches,
 * its Games tab; members only), and the kept drafts (LOB-279): GET /v1/history/drafts (the account's, newest first) and GET
 * /v1/history/drafts/{id} (one with its draft history).
 */
class HistoryHttp(private val runtime: ServiceRuntime) {
    private val accounts = AccountService(runtime)
    private val limits = AccountRateLimits()
    private val json = DeckJson.format
    private fun log(message: String) = println("${java.time.Instant.now()} History: $message")

    fun handle(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val path = request.requestURI
        val parts = path.split('/')
        val game = parts.size == 5 && parts.take(3) == listOf("", "v1", "games") && parts[4] == "history"
        val pod = parts.size == 5 && parts.take(3) == listOf("", "v1", "pods") && parts[4] == "matches"
        val drafts = path == "/v1/history/drafts"
        val draft = parts.size == 5 && parts.take(4) == listOf("", "v1", "history", "drafts")
        if (path != "/v1/history" && !game && !pod && !drafts && !draft) return false
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("X-Content-Type-Options", "nosniff")
        fun reply(status: Int, body: String) {
            response.status = status; response.contentType = "application/json;charset=UTF-8"; response.writer.write(body)
        }
        fun error(status: Int, code: String) = reply(status, json.encodeToString(LobbyError(code)))
        try {
            if (!runtime.admitting()) { error(503, "unavailable"); return true }
            val token = request.getHeaders("Authorization").toList().singleOrNull()?.takeIf { it.startsWith("Bearer ", true) }?.substring(7)
            val principal = token?.let(accounts::authenticate)
            if (principal == null) { error(401, "authentication_required"); return true }
            if (!limits.allow("history-http", principal.account.accountId, 600, 60, shown = "account ${principal.account.accountId}")) {
                response.setHeader("Retry-After", "60"); error(429, "try_later"); return true
            }
            if (request.method != "GET") { response.setHeader("Allow", "GET"); error(405, "method_not_allowed"); return true }
            if ((game || pod) && !DeckValidation.uuid(parts[3])) { error(404, "not_found"); return true }
            if (draft && !DeckValidation.uuid(parts[4])) { error(404, "not_found"); return true }
            val viewer = UUID.fromString(principal.account.accountId)
            val body = runtime.transaction { connection ->
                val sql = DSL.using(connection, SQLDialect.POSTGRES)
                when {
                    game -> json.encodeToString(MatchHistory(MatchRecords.forGame(sql, viewer, UUID.fromString(parts[3]))))
                    pod -> MatchRecords.forPod(sql, viewer, UUID.fromString(parts[3]))?.let { json.encodeToString(it) }
                    drafts -> DraftRecords.list(sql, viewer).let { list ->
                        log("drafts of $viewer: ${list.drafts.size} kept (${list.drafts.joinToString { "${it.id} '${it.podName}'" }})")
                        json.encodeToString(list)
                    }
                    draft -> DraftRecords.read(sql, viewer, UUID.fromString(parts[4])).let { record ->
                        log("draft ${parts[4]} of $viewer: " + (record?.let { "'${it.summary.podName}', ${it.history.picks.size} picks" } ?: "none such"))
                        record?.let { json.encodeToString(it) }
                    }
                    else -> json.encodeToString(MatchHistory(MatchRecords.forAccount(sql, viewer)))
                }
            }
            if (body == null) error(404, "not_found") else reply(200, body)
        } catch (_: OwnershipFailure) { error(503, "unavailable")
        } catch (_: RuntimeException) { error(500, "internal_error") }
        return true
    }
}
