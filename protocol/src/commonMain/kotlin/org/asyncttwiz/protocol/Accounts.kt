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
data class ResetPassword(val token: String, val password: String) {
    override fun toString() = "ResetPassword[redacted]"
}

/** The only identity projection exposed to clients/other accounts; deliberately no email. */
@Serializable
data class AccountIdentity(val accountId: String, val username: String)

@Serializable
data class SessionCreated(val token: String, val account: AccountIdentity) {
    override fun toString() = "SessionCreated[redacted]"
}

@Serializable
data class AccountError(val code: String, val field: String? = null)

object AccountJson {
    val format = Json { explicitNulls = false; ignoreUnknownKeys = false }
}
