package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.asyncttwiz.protocol.AccountJson
import org.asyncttwiz.protocol.CreateSession
import org.asyncttwiz.protocol.RegisterAccount
import org.junit.Test
import kotlin.test.*

class AccountApiTest {
    @Test fun exactPasswordBytesAndBodyOnlyAuthentication() = runBlocking {
        val secret = " \tCafe\u0301 🧙\n "
        val requests = mutableListOf<String>()
        val token = "A".repeat(43)
        val engine = MockEngine { request ->
            requests += request.url.encodedPath
            assertEquals("", request.url.encodedQuery)
            assertNull(request.headers["Cookie"])
            val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()
            when (request.url.encodedPath) {
                "/accounts" -> {
                    val fields = AccountJson.format.decodeFromString<RegisterAccount>(body!!)
                    assertEquals(secret, fields.password)
                    assertEquals(" Name ", fields.username)
                    assertNull(request.headers["Authorization"])
                    respond("""{"accountId":"id","username":"Name"}""", HttpStatusCode.Created)
                }
                "/sessions" -> {
                    assertEquals(secret, AccountJson.format.decodeFromString<CreateSession>(body!!).password)
                    assertNull(request.headers["Authorization"])
                    respond("""{"token":"$token","account":{"accountId":"id","username":"Name"}}""", HttpStatusCode.Created)
                }
                else -> {
                    assertEquals("Bearer $token", request.headers["Authorization"])
                    respond("""{"accountId":"id","username":"Name"}""")
                }
            }
        }
        AccountApi("https://accounts.example", HttpClient(engine) { followRedirects = false }).use { api ->
            api.register(" Name ", " Email@example.com ", secret)
            assertEquals(token, api.login("Email@example.com", secret).token)
            assertEquals("Name", api.identity(token).username)
        }
        assertEquals(listOf("/accounts", "/sessions", "/account"), requests)
    }

    @Test fun malformedAndOutOfBoundsSecretsNeverReachTransport() = runBlocking {
        var count = 0
        val client = HttpClient(MockEngine { count++; respond("{}") })
        AccountApi("https://accounts.example", client).use { api ->
            for (secret in listOf("short", "x".repeat(257), "goodpass\uD800", "\uDC00goodpass")) {
                val failure = assertFailsWith<AccountFailure> { api.login("x@example.com", secret) }
                assertEquals("password", failure.field)
            }
        }
        assertEquals(0, count)
        AccountApi.validatePassword("😀".repeat(256)) // 256 scalars, 1024 bytes, not 512 characters.
        AccountApi.validatePassword(" ".repeat(8)) // No hidden composition/trim rule.
    }

    @Test fun responseErrorsAreBoundedGenericAndRedirectsAreNotFollowed() = runBlocking {
        for ((status, text, expected) in listOf(
            Triple(401, """{"code":"invalid_credentials"}""", "invalid_credentials"),
            Triple(429, """{"code":"try_later"}""", "try_later"),
            Triple(500, "<private stack/password/email>", "unavailable"),
            Triple(307, "", "unavailable"),
            Triple(200, "x".repeat(16_385), "invalid_response"),
            Triple(200, "{}", "invalid_response"),
        )) {
            var calls = 0
            val client = HttpClient(MockEngine { calls++; respond(text, HttpStatusCode.fromValue(status),
                headersOf("Location", "https://other.example/steal")) }) { followRedirects = false }
            AccountApi("https://accounts.example", client).use { api ->
                assertEquals(expected, assertFailsWith<AccountFailure> { api.login("x@example.com", "goodpassword") }.code)
            }
            assertEquals(1, calls)
        }
        AccountApi("https://accounts.example", HttpClient(MockEngine { respond("", HttpStatusCode.Unauthorized) })).use { api ->
            assertEquals("authentication_required", assertFailsWith<AccountFailure> { api.identity("A".repeat(43)) }.code)
        }
        assertFalse(AuthController.errorMessage(AccountFailure("<private>" )).contains("<private>"))
    }

    @Test fun endpointAndTokenValidationRefusesCredentialUrls() {
        for (origin in listOf("https://user:pass@example.com", "https://example.com?token=x",
            "https://example.com/path", "http://untrusted.example", "file:///private", "https://example.com#token")) {
            val client = HttpClient(MockEngine { respond("{}") })
            try { assertFailsWith<IllegalArgumentException> { AccountApi(origin, client) } } finally { client.close() }
        }
        assertFalse(AccountApi.validToken("token\r\nHeader: x"))
    }
}
