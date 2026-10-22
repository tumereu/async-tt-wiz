package org.asyncttwiz.server.accounts

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Self-contained fallback browser form. No GET mutates credentials, no auth cookies or third parties. */
class ResetFormHttp(private val resets: PasswordResetService, private val settings: ResetMailSettings,
                    private val limits: AccountRateLimits) {
    private val random = SecureRandom()
    private fun escaped(value: String) = value.replace("&", "&amp;").replace("\"", "&quot;")
        .replace("<", "&lt;").replace(">", "&gt;")
    private fun signature(token: String, nonce: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(settings.key, "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            mac.doFinal(("reset-form-v1\n$token\n$nonce").toByteArray(Charsets.UTF_8)))
    }
    private fun fields(value: String): Map<String, String> {
        if (value.isEmpty()) return emptyMap()
        val pairs = value.split('&').map {
            val parts = it.split('=', limit = 2)
            require(parts.size == 2)
            fun decode(text: String): String {
                // Reject malformed %-encoded UTF8 rather than silently replacing password bytes.
                val latin = URLDecoder.decode(text, Charsets.ISO_8859_1)
                return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(latin.toByteArray(Charsets.ISO_8859_1))).toString()
            }
            decode(parts[0]) to decode(parts[1])
        }
        require(pairs.map { it.first }.distinct().size == pairs.size)
        return pairs.toMap()
    }
    private fun page(response: HttpServletResponse, status: Int, content: String) {
        response.status = status
        response.contentType = "text/html;charset=UTF-8"
        response.setHeader("Referrer-Policy", "no-referrer")
        response.setHeader("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'")
        response.writer.write("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>Async Tabletop Wizards — password reset</title><style>body{font:1rem sans-serif;max-width:28rem;margin:3rem auto;padding:1rem}input,button{box-sizing:border-box;width:100%;min-height:3rem;margin:.5rem 0}input{font:inherit}</style>" +
            "<h1>Async Tabletop Wizards</h1>$content</html>")
    }
    private fun form(response: HttpServletResponse, token: String, message: String = "", status: Int = 200) {
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        response.addHeader("Set-Cookie", "reset_csrf=$nonce; Path=/reset-password; Secure; HttpOnly; SameSite=Strict; Max-Age=1800")
        val request = token.isEmpty()
        val input = if (request) "<label>Email<input name=\"email\" type=\"email\" autocomplete=\"email\" required></label>" else
            "<label>New password<input name=\"password\" type=\"password\" autocomplete=\"new-password\" required></label>" +
            "<p>Use 8–256 characters. Spaces and Unicode are preserved.</p>"
        page(response, status, "<h2>${if (request) "Request a reset link" else "Set a new password"}</h2><p>$message</p>" +
            "<form method=\"post\" action=\"${if (request) "/reset-password/request" else "/reset-password"}\">" +
            "<input type=\"hidden\" name=\"token\" value=\"${escaped(token)}\">" +
            "<input type=\"hidden\" name=\"csrf\" value=\"${signature(token, nonce)}\">$input" +
            "<button>${if (request) "Send reset link" else "Set password"}</button></form>")
    }
    private fun invalid(response: HttpServletResponse) = page(response, 400,
        "<h2>This reset link is invalid or expired</h2><a href=\"/reset-password\">Request another link</a>")

    fun handle(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        if (request.requestURI !in setOf("/reset-password", "/reset-password/request")) return false
        // Also applies to rejection pages, never reflect arbitrary incoming query/input.
        response.setHeader("Referrer-Policy", "no-referrer")
        try {
            if (request.method == "GET" && request.requestURI == "/reset-password") {
                val query = fields(request.queryString.orEmpty())
                val token = query["token"]
                if (query.isEmpty()) form(response, "")
                else if (query.keys == setOf("token") && token != null && resets.usable(token)) form(response, token)
                else invalid(response)
            } else if (request.method == "POST") {
                if (!request.contentType.orEmpty().equals("application/x-www-form-urlencoded", true)) {
                    page(response, 415, "<p>A form submission is required.</p>"); return true
                }
                val bytes = request.inputStream.readNBytes(8193)
                if (bytes.size > 8192) { page(response, 413, "<p>Form too large.</p>"); return true }
                val raw = Charsets.US_ASCII.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
                val input = fields(raw)
                val token = input["token"].orEmpty()
                val cookie = request.cookies.orEmpty().filter { it.name == "reset_csrf" }.singleOrNull()?.value.orEmpty()
                val csrf = input["csrf"].orEmpty()
                val origin = request.getHeader("Origin")
                if (cookie.length != 43 || csrf.length != 43 || !MessageDigest.isEqual(csrf.toByteArray(), signature(token, cookie).toByteArray()) ||
                    (origin != null && origin != settings.resetOrigin.toString().trimEnd('/'))) {
                    page(response, 403, "<p>Submission expired. Open the link again, or <a href=\"/reset-password\">request another link</a>.</p>")
                    return true
                }
                if (request.requestURI == "/reset-password/request") {
                    require(input.keys == setOf("token", "csrf", "email") && token.isEmpty())
                    val email = AccountFields.email(input["email"]!!)
                    if (!limits.allow("reset-ip", request.remoteAddr, 20, 3600) || !limits.allow("reset-email", email.key, 3, 3600))
                        form(response, "", "Please try again later.", 429)
                    else { resets.request(input["email"]!!); page(response, 202, "<p>If this address can receive a reset link, an email will be sent. You can now return to the app.</p>") }
                } else {
                    require(input.keys == setOf("token", "csrf", "password"))
                    if (!limits.allow("reset-confirm-ip", request.remoteAddr, 20, 60)) form(response, token, "Please try again later.", 429)
                    else try {
                        if (resets.confirm(token, input["password"]!!)) page(response, 200, "<h2>Password changed</h2><p>Return to the app and sign in with your new password.</p>")
                        else invalid(response)
                    } catch (_: InvalidAccountField) { form(response, token, "Use 8–256 characters (at most 1024 UTF-8 bytes).", 400) }
                }
            } else { response.setHeader("Allow", if (request.requestURI == "/reset-password") "GET, POST" else "POST"); page(response, 405, "<p>Method not allowed.</p>") }
        } catch (_: IllegalArgumentException) { invalid(response) }
        catch (_: java.nio.charset.CharacterCodingException) { invalid(response) }
        catch (_: InvalidAccountField) { form(response, "", "Enter a valid ASCII email address.", 400) }
        return true
    }
}
