package org.asyncttwiz.e2e

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import org.asyncttwiz.android.AccountApi
import java.io.File

/**
 * The HTTP client of the tests' own requests to the server (their setup over the API and what they check there): the
 * apps' client ([AccountApi.client]) that sends a request again when its connection ended before any answer came.
 *
 * Why (measured 2026-11-27 on this machine): WSL's networking here routes TCP to 127.0.0.1 through Windows
 * (`ip route get 127.0.0.1` says `via 169.254.73.249 dev loopback0`). After a client drops a connection at once (a reset:
 * Chrome as a test closes its browser, a phone's socket closed with unread data), a new connection from the same local
 * port to the server is broken for about a minute: it connects, but its request never reaches the server (no access-log
 * line, no Tomcat log) and the answer is an immediate end of stream ("Unexpected end of file from server") or a reset.
 * Linux hands such a just-freed port to this JVM's next connections now and then, so a test's setup failed with "Pod
 * request failed: network". Plain sockets in Python show the same on 127.0.0.1 and not on the machine's LAN address, so it
 * is the environment, not the app or the server. The server never saw the request, so sending the same one again (same
 * request id) is safe; the next attempt takes another local port. Each retry is written to `e2e/build/e2e/retries.log`.
 * Only a new connection can be such a one, so the JVM keeps its connections alive between requests (http.keepAlive left on).
 * The phones' own requests (the app under test) go without this: a player's action that fails so fails its test.
 */
object E2eHttp {
    private const val ATTEMPTS = 5

    fun client(): HttpClient = AccountApi.client().config {
        install(HttpRequestRetry) {
            retryIf(ATTEMPTS - 1) { _, _ -> false } // an answer, refusal or error, is the server's: never again
            retryOnExceptionIf(ATTEMPTS - 1) { request, cause ->
                closedBeforeAnswer(cause).also { again ->
                    if (again) note("${request.method.value} ${request.url.buildString().substringAfter("127.0.0.1").substringBefore("?")}: ${cause.javaClass.simpleName}: ${cause.message}")
                }
            }
            constantDelay(50)
        }
    }

    /** The connection ended (end of stream or reset) before an answer; not a refused connection or a timeout. */
    private fun closedBeforeAnswer(cause: Throwable): Boolean =
        generateSequence(cause) { it.cause }.take(4).any { it is java.net.SocketException && it !is java.net.ConnectException }

    private fun note(line: String) {
        val text = "${java.time.Instant.now()} sent again after the connection closed before an answer: $line"
        System.err.println("E2E $text")
        runCatching { File(E2eStack.output, "retries.log").appendText("$text\n") }
    }
}
