package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Username-only projections. Account emails and credentials never enter shared lobby metadata. */
@Serializable data class UserSearch(val users: List<AccountIdentity>)
@Serializable data class ContactList(val contacts: List<AccountIdentity>, val next: String? = null)
@Serializable data class AddContact(val requestId: String, val accountId: String)
/** Every game is played with Automatum (I-D52). */
@Serializable data class CreateInvitation(val requestId: String, val recipient: String, val gameName: String)
@Serializable enum class InvitationState { PENDING, ACCEPTED, DECLINED }
@Serializable enum class GameLifecycle { LOBBY, ACTIVE, CANCELLED, DELETED }
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class GameSummary(val id: String, val gameName: String, val createdAt: String,
    val inviter: AccountIdentity, val recipient: AccountIdentity, val preparationRound: String,
    val invitationId: String, val invitationState: InvitationState, val lifecycle: GameLifecycle = GameLifecycle.LOBBY,
    val revision: String = "0", val playId: String? = null,
    val attentionHolder: String? = null, val updatedAt: String = createdAt,
    val podId: String? = null, val podName: String? = null,
    /** Pod challenge waiting for the opponent's deck: both players see it, the recipient can decline but not yet accept. Omitted when false. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val invitationHeld: Boolean = false,
    /** The game's assume reveal settings (LOB-135), set by its host ([inviter]). Omitted when the defaults. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val assumeReveals: AssumeReveals = AssumeReveals(),
    /** TRN-065: the last match's result while a player still has to tap OK on it. Omitted when nobody has to. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val resultNotice: ResultNotice? = null)

/**
 * TRN-065 (owner 2026-11-07): how the last match ended, kept from its end until both players have tapped OK on its result
 * dialog: the [winner] (null: a draw), how it ended ([ending]), why each loser lost by the rules ([causes], by account),
 * and who still has to tap OK ([unseen]; the player whose own action ended it never does).
 */
@Serializable data class ResultNotice(val winner: String? = null, val ending: MatchEnding, val causes: Map<String, LossCause> = emptyMap(),
    val unseen: List<String>)

/**
 * A game's settings that relax the information rule (TRN-067, LOB-135; owner 2026-11-07), set by the game's host before
 * and during the game, outside undo: with [library], cards of the other player's library (a reveal from its top, a
 * search's result shown) don't hold back an assumption or the player's own actions on a tentative line (every line
 * reshuffles the unknown parts of the libraries, HIS-042); with [hands], cards of the other player's hand (reveals,
 * random discards) don't either. Face-down cards and other random results are held back regardless. The defaults are
 * the game's: library on, hands off.
 */
@Serializable data class AssumeReveals(val library: Boolean = true, val hands: Boolean = false) {
    companion object {
        /** Nothing relaxed: the information rule as such (its checks' default, and a device not yet told). */
        val NONE = AssumeReveals(library = false, hands = false)
    }
}
/** A page of the games list. Read with a [token] from an earlier read ("changed since", switch-review #11): only the games
 * changed since then, with [removed] the ids of changed games that left this list (ended, cancelled, declined, no longer
 * visible). [token] is what to read changed games since next time (the first page's; null on an older read). */
@Serializable data class GameList(val games: List<GameSummary>, val next: String? = null, val token: String? = null,
    val removed: List<String> = emptyList())
@Serializable data class LobbyError(val code: String)

object LobbyValidation {
    fun name(value: String) = value.isNotBlank() && DeckValidation.text(value)
}
