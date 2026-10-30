package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Account HTTP v1. Secrets are never used as URLs, log strings or business replay payloads. */
@Serializable
data class RegisterAccount(val username: String, val email: String, val password: String) {
    override fun toString() = "RegisterAccount[redacted]"
}

@Serializable
data class CreateSession(val email: String, val password: String) {
    override fun toString() = "CreateSession[redacted]"
}

@Serializable
data class RequestPasswordReset(val email: String) {
    override fun toString() = "RequestPasswordReset[redacted]"
}

@Serializable
data class CheckPasswordResetLink(val token: String) {
    override fun toString() = "CheckPasswordResetLink[redacted]"
}

@Serializable
data class ResetPassword(val token: String, val password: String) {
    override fun toString() = "ResetPassword[redacted]"
}

/** The only identity projection exposed to clients/other accounts; deliberately no email. */
@Serializable
data class AccountIdentity(val accountId: String, val username: String, val avatar: Avatar? = null)

@Serializable
data class Avatar(val scryfallId: String, val face: Int = 0)

/** Separate updates avoid accidentally clearing an avatar when changing a name. */
@Serializable
data class ChangeUsername(val username: String)

@Serializable
data class ChangeAvatar(val avatar: Avatar? = null)

@Serializable
data class SessionCreated(val token: String, val account: AccountIdentity) {
    override fun toString() = "SessionCreated[redacted]"
}

/** Default for each new game's "Show deck name & art" toggle; the server applies it when a deck is selected. */
@Serializable
enum class DeckVisibility { VISIBLE, HIDDEN, AFTER_FIRST_GAME }

/** Server-side account preferences (`GET`/`PUT /account/settings`). Defaults are omitted on the wire. */
@Serializable
data class AccountSettings(val deckVisibility: DeckVisibility = DeckVisibility.AFTER_FIRST_GAME)

@Serializable
data class AccountError(val code: String, val field: String? = null)

object AccountJson {
    val format = Json { explicitNulls = false; ignoreUnknownKeys = false }
}
