package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AccountsTest {
    @kotlin.test.Test fun avatarIsOptionalForOldPayloadsAndRoundTrips() {
        val legacy = AccountJson.format.decodeFromString<AccountIdentity>("""{"accountId":"id","username":"name"}""")
        assertEquals(null, legacy.avatar)
        val updated = legacy.copy(avatar = Avatar("00000000-0000-0000-0000-000000000001", 1))
        assertEquals(updated, AccountJson.format.decodeFromString<AccountIdentity>(AccountJson.format.encodeToString(updated)))
    }
    // LOB-080
    @Test fun accountSettingsDefaultToShowAfterFirstGameAndRoundTrip() {
        assertEquals(AccountSettings(DeckVisibility.AFTER_FIRST_GAME), AccountJson.format.decodeFromString<AccountSettings>("{}"))
        assertEquals("{}", AccountJson.format.encodeToString(AccountSettings()))
        for (option in DeckVisibility.entries) assertEquals(AccountSettings(option),
            AccountJson.format.decodeFromString<AccountSettings>(AccountJson.format.encodeToString(AccountSettings(option))))
        assertEquals("""{"deckVisibility":"HIDDEN"}""", AccountJson.format.encodeToString(AccountSettings(DeckVisibility.HIDDEN)))
    }
    @Test fun credentialsRoundTripWithoutNormalizationOrDiagnosticDisclosure() {
        val input = RegisterAccount("Straße", "Local@Example.invalid", " \tPäSS e\u0301😀\n")
        val encoded = AccountJson.format.encodeToString(input)
        assertEquals(input, AccountJson.format.decodeFromString<RegisterAccount>(encoded))
        assertEquals("RegisterAccount[redacted]", input.toString())
        assertFalse(AccountJson.format.encodeToString(AccountIdentity("id", "name")).contains("email"))
        assertEquals("SessionCreated[redacted]", SessionCreated("secret", AccountIdentity("id", "name")).toString())
    }
}
