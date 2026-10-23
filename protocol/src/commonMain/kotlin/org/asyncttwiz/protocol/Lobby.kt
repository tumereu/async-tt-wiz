package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Username-only projections. Account emails and credentials never enter shared lobby metadata. */
@Serializable data class UserSearch(val users: List<AccountIdentity>)
@Serializable data class ContactList(val contacts: List<AccountIdentity>, val next: String? = null)
@Serializable data class AddContact(val requestId: String, val accountId: String)
@Serializable data class CreateInvitation(val requestId: String, val recipient: String, val gameName: String)
@Serializable enum class InvitationState { PENDING, ACCEPTED, DECLINED }
@Serializable enum class GameLifecycle { LOBBY, ACTIVE, CANCELLED, DELETED }
@Serializable data class GameSummary(val id: String, val gameName: String, val createdAt: String,
    val inviter: AccountIdentity, val recipient: AccountIdentity, val preparationRound: String,
    val invitationId: String, val invitationState: InvitationState, val lifecycle: GameLifecycle = GameLifecycle.LOBBY,
    val revision: String = "0", val playId: String? = null)
@Serializable data class GameList(val games: List<GameSummary>, val next: String? = null)
@Serializable data class LobbyError(val code: String)

object LobbyValidation {
    fun name(value: String) = value.isNotBlank() && DeckValidation.text(value)
}
