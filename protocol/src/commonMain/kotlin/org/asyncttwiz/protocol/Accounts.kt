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

/** Server-side account preferences (`GET`/`PUT /account/settings`). Defaults are omitted on the wire. [playDefaults]: the
 * response mode, Assume resolves and Autotap mana a new game on engine state starts with (LOB-113, I-D56 §7).
 * [moveSound]: the sound the move passing to you makes at an open table (TRN-075, LOB-146). */
@Serializable
data class AccountSettings(val deckVisibility: DeckVisibility = DeckVisibility.AFTER_FIRST_GAME,
    val playDefaults: org.asyncttwiz.protocol.game.PlaySettings = org.asyncttwiz.protocol.game.PlaySettings.NEW_GAMES,
    val moveSound: MoveSound = MoveSound.DEFAULT)

/**
 * TRN-075, LOB-146 (owner, 2026-11-11): the "Your move" sound: Off or one of the app's own short soft sounds, [Plip] by
 * default. On the wire and in the database by its [id].
 */
@Serializable(with = MoveSound.WireSerializer::class)
sealed class MoveSound(val id: String, val label: String) {
    data object Off : MoveSound("off", "Off")
    data object Plip : MoveSound("plip", "Plip")
    data object Drop : MoveSound("drop", "Drop")
    data object Chime : MoveSound("chime", "Chime")
    data object Tock : MoveSound("tock", "Tock")

    companion object {
        /** Every choice, in the order Settings lists them. */
        val all: List<MoveSound> get() = listOf(Off, Plip, Drop, Chime, Tock)
        val DEFAULT: MoveSound get() = Plip
        fun of(id: String): MoveSound? = all.firstOrNull { it.id == id }
    }

    /** By [id]; an unknown id is refused. */
    object WireSerializer : kotlinx.serialization.KSerializer<MoveSound> {
        override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("MoveSound",
            kotlinx.serialization.descriptors.PrimitiveKind.STRING)
        override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: MoveSound) = encoder.encodeString(value.id)
        override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): MoveSound = decoder.decodeString().let { id ->
            of(id) ?: throw kotlinx.serialization.SerializationException("Unknown move sound $id")
        }
    }
}

@Serializable
data class AccountError(val code: String, val field: String? = null)

object AccountJson {
    val format = Json { explicitNulls = false; ignoreUnknownKeys = false }
}
