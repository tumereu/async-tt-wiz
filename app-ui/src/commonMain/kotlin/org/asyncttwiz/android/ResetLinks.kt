package org.asyncttwiz.android

import org.asyncttwiz.appui.parseUri
import org.asyncttwiz.appui.isCanonicalUrlBase64

/** Only the configured HTTPS email origin and exact path/query, never a redirect or arbitrary API host. */
object ResetLinks {
    fun canonicalToken(token: String): Boolean = token.matches(Regex("[A-Za-z0-9_-]{43}")) &&
        runCatching { isCanonicalUrlBase64(token) }.getOrDefault(false)

    fun token(link: String?, origin: String): String? = runCatching {
        if (link == null || link.length > 2048 || origin.isEmpty()) return null
        val base = parseUri(origin)
        val uri = parseUri(link)
        fun port(value: org.asyncttwiz.appui.UriParts) = if (value.port == -1) 443 else value.port
        if (base.scheme != "https" || base.host == null || base.rawUserInfo != null ||
            !base.rawPath.isNullOrEmpty() || base.rawQuery != null || base.rawFragment != null ||
            uri.scheme != "https" || !base.host.equals(uri.host, ignoreCase = true) || port(base) != port(uri) ||
            uri.rawUserInfo != null || uri.rawFragment != null || uri.rawPath != "/reset-password") return null
        val token = uri.rawQuery?.takeIf { it.startsWith("token=") }?.substring(6) ?: return null
        token.takeIf(::canonicalToken)
    }.getOrNull()
}
