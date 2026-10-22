package org.asyncttwiz.server.accounts

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/** Explicit file secrets only. No implicit unencrypted remote SMTP fallback. */
class ResetMailSettings(val host: String, val port: Int, val tls: String, val username: String?,
                        val password: String?, val from: String, val resetOrigin: URI, val key: ByteArray) {
    init {
        require(host.matches(Regex("[A-Za-z0-9.:-]{1,253}")) && port in 1..65535)
        require(tls in setOf("starttls", "tls", "local-plaintext"))
        require(tls != "local-plaintext" || (username == null && password == null))
        require((username == null) == (password == null))
        require(username == null || (username.length <= 256 && username.none { it.isISOControl() }))
        require(password == null || password.isNotEmpty())
        AccountFields.email(from)
        require(from.all { it.code in 33..126 })
        require(resetOrigin.scheme == "https" && resetOrigin.host != null && resetOrigin.rawUserInfo == null &&
            resetOrigin.rawQuery == null && resetOrigin.rawFragment == null && resetOrigin.path in listOf("", "/") &&
            resetOrigin.port != 0 && resetOrigin.port <= 65535)
        require(key.size == 32)
    }
    fun link(token: String) = resetOrigin.toString().trimEnd('/') + "/reset-password?token=" + token
    override fun toString() = "ResetMailSettings[redacted]"

    companion object {
        @JvmStatic fun fromEnvironment(env: Map<String, String>): ResetMailSettings {
            fun required(name: String) = env[name]?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Missing reset mail configuration")
            fun secret(name: String): String {
                val path = Path.of(required(name))
                require(Files.isRegularFile(path) && Files.isReadable(path) && Files.size(path) <= 65536)
                return Files.readString(path).replaceFirst(Regex("\\r?\\n$"), "")
            }
            val user = env["SMTP_USERNAME_FILE"]?.let { secret("SMTP_USERNAME_FILE") }
            val password = env["SMTP_PASSWORD_FILE"]?.let { secret("SMTP_PASSWORD_FILE") }
            val encoded = secret("RESET_MAIL_KEY_FILE")
            require(encoded.matches(Regex("[A-Za-z0-9+/]{43}=")))
            return ResetMailSettings(required("SMTP_HOST"), required("SMTP_PORT").toInt(),
                required("SMTP_TLS"), user, password, required("SMTP_FROM"), URI(required("RESET_BASE_URL")),
                Base64.getDecoder().decode(encoded))
        }
    }
}
