package org.asyncttwiz.server.accounts

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AccountFieldsTest {
    @Test fun `Unicode 15_1 names use NFC then full default folding not password rules`() {
        assertEquals(AccountFields.Name("Café", "café"), AccountFields.username("\u2003Cafe\u0301\u00a0"))
        assertEquals("strasse", AccountFields.username("Straße").key)
        assertEquals("σσσ", AccountFields.username("Σσς").key)
        assertEquals("i\u0307", AccountFields.username("İ").key)
        assertEquals("Ꭰ", AccountFields.username("ꭰ").key) // Default fold for Cherokee is uppercase.
        for (value in listOf("\u2003", "A\nB", "a\u202eb", "a\u2066b", "a\u0000b", "a\ud800", "😀".repeat(65)))
            assertThrows(InvalidAccountField::class.java) { AccountFields.username(value) }
        assertEquals(128, AccountFields.username("😀".repeat(64)).display.length)
    }

    @Test fun `email has its own IDNA ASCII local equivalence without provider aliases`() {
        assertEquals("local+tag@xn--bcher-kva.example", AccountFields.email(" Local+Tag@BÜCHER.example ").key)
        assertEquals("User@BÜCHER.example", AccountFields.email(" User@BÜCHER.example ").delivery)
        assertNotEquals(AccountFields.email("a.b@example.invalid").key, AccountFields.email("ab@example.invalid").key)
        assertNotEquals(AccountFields.email("a+tag@example.invalid").key, AccountFields.email("a@example.invalid").key)
        assertEquals("u@xn--fa-hia.example", AccountFields.email("u@faß.example").key)
        for (value in listOf("é@example.invalid", "a..b@example.invalid", "a@", "@example.invalid", "a@x@x",
            "a@exa mple.invalid", "a@x\nb.invalid", "a\udc00@x.invalid", "a".repeat(65) + "@example.invalid",
            "a@" + "x".repeat(64) + ".invalid", "a@" + "x.".repeat(126) + "invalid"))
            assertThrows(InvalidAccountField::class.java) { AccountFields.email(value) }
    }

    @Test fun `password preserves scalar whitespace case Unicode newline and maximum UTF8`() {
        val exact = " \tPäSS e\u0301😀\n"
        assertSame(exact, AccountFields.password(exact))
        assertEquals("😀".repeat(256), AccountFields.password("😀".repeat(256)))
        assertEquals("\u0000".repeat(8), AccountFields.password("\u0000".repeat(8)))
        for (value in listOf("1234567", "😀".repeat(257), "abcdefgh\ud800", "abcdefgh\udc00"))
            assertThrows(InvalidAccountField::class.java) { AccountFields.password(value) }
    }

    @Test fun `versioned Argon2id salts exact bytes and bounded hashing benchmark on this host`() {
        val passwords = PasswordHashing()
        val password = " \tPäSS e\u0301😀\n"
        val begin = System.nanoTime()
        val first = passwords.hash(password)
        val second = passwords.hash(password)
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin)
        assertNotEquals(first, second)
        assertTrue(first.startsWith("\$argon2id\$v=19\$m=65536,t=3,p=1\$"))
        assertEquals(16, java.util.Base64.getDecoder().decode(first.split('$')[4]).size)
        assertEquals(32, java.util.Base64.getDecoder().decode(first.split('$')[5]).size)
        assertTrue(passwords.verify(password, first))
        assertFalse(passwords.needsRehash(first))
        for (different in listOf(password.trim(), password.lowercase(), java.text.Normalizer.normalize(password, java.text.Normalizer.Form.NFC), password + " "))
            assertFalse(passwords.verify(different, first))
        assertFalse(passwords.verify(password, null))
        val legacy = org.springframework.security.crypto.argon2.Argon2PasswordEncoder(16, 32, 1, 16384, 2).encode(password)!!
        assertTrue(passwords.needsRehash(legacy))
        assertTrue(passwords.verify(password, legacy))
        Executors.newFixedThreadPool(2).use { pool ->
            val entered = CountDownLatch(2)
            val release = CountDownLatch(1)
            val jobs = (1..2).map { pool.submit { passwords.bounded { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)) } } }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertThrows(HashingBusy::class.java) { passwords.hash(password) }
            } finally { release.countDown() }
            jobs.forEach { it.get(5, TimeUnit.SECONDS) }
        }
        println("BENCHMARK host-only Argon2id v19 65536KiB/t3/p1: two fresh hashes ms=$elapsed mean-ms=${elapsed / 2.0}; JVM=${System.getProperty("java.version")} arch=${System.getProperty("os.arch")} processors=${Runtime.getRuntime().availableProcessors()}; not physical Android/UI performance")
    }

    @Test fun `rate windows are independent bounded generic and recover without permanent lockout`() {
        var time = 0L
        val limits = AccountRateLimits { time }
        repeat(5) { assertTrue(limits.allow("email", "key", 5, 60)) }
        assertFalse(limits.allow("email", "key", 5, 60))
        assertTrue(limits.allow("email", "other", 5, 60))
        assertTrue(limits.allow("ip", "key", 10, 60))
        time = 60
        assertTrue(limits.allow("email", "key", 5, 60))
        val capacity = AccountRateLimits { time }
        repeat(10_000) { assertTrue(capacity.allow("email", "flood-$it", 5, 60)) }
        assertFalse(capacity.allow("email", "over-capacity", 5, 60))
        time = 120
        assertTrue(capacity.allow("email", "recovered", 5, 60))
    }
}
