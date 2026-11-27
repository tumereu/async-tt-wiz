package org.asyncttwiz.protocol

import kotlinx.serialization.SerialName
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
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val resultNotice: ResultNotice? = null,
    /** The game's deck settings (LOB-247), set by its creator ([inviter]). Omitted when the defaults. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val deckSettings: DeckSettings = DeckSettings(),
    /** DRF-039 (owner, 2026-11-23): a pod game that carried on past its pod's cancelled draft: it keeps [podId], [podName]
     * and its pod deck, but no longer belongs to the pod (not in its Games tab, no Pod chat, its deck its own). Omitted when false. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val podOrphaned: Boolean = false)

/**
 * TRN-065 (owner 2026-11-09): how the last match ended, after a concession or an accepted suggestion, kept from its end until
 * the other player has tapped OK on its result dialog (a result the rules gave ended on both OKs, so it needs none): the
 * [winner] (null: a draw), how it ended ([ending]), why each loser lost by the rules ([causes], by account), and who still has
 * to tap OK ([unseen]; the player whose own action ended it never does).
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
 *
 * The same Game settings card (and so this value) also holds [undoPastPasses] (owner 2026-11-19, N15): "Undo past the other
 * player's passes needs a request". Off (the default), the other player's passes never count for a free undo (HIS-202); on,
 * going back past one needs their approval like their real input (HIS-099).
 *
 * It also holds the lobby-only switches "Reshuffle libraries on undo" ([reshuffleOnUndo]) and "Reshuffle libraries on branch"
 * ([reshuffleOnBranch]) (owner 2026-11-26, playtest F; both on by default): off, that kind of new line of play keeps the
 * libraries' unknown order instead of reshuffling it (HIS-042; the game's start always shuffles). While branches don't
 * reshuffle, [library] lets nothing through ([libraryAllowed], LOB-152: a card of the other player's library seen on a
 * discarded line would still lie where it was seen).
 */
@Serializable data class AssumeReveals(val library: Boolean = true, val hands: Boolean = false, val undoPastPasses: Boolean = false,
    val reshuffleOnUndo: Boolean = true, val reshuffleOnBranch: Boolean = true) {
    /** HIS-042: whether a new line of play reshuffles the libraries' unknown parts: a branch's ([branch]) or an undo's. */
    fun reshuffles(branch: Boolean): Boolean = if (branch) reshuffleOnBranch else reshuffleOnUndo

    /** LOB-152: whether cards of the other player's library may be shown on a tentative line: [library], while branches
     * reshuffle the libraries (the line that shows them may be discarded, the cards staying where they were seen). */
    val libraryAllowed: Boolean get() = library && reshuffleOnBranch

    /** Whether [other] has the same reshuffle switches (they change only in the lobby, owner 2026-11-26). */
    fun sameReshuffles(other: AssumeReveals): Boolean =
        reshuffleOnUndo == other.reshuffleOnUndo && reshuffleOnBranch == other.reshuffleOnBranch

    companion object {
        /** Nothing relaxed: the information rule as such (its checks' default, and a device not yet told). */
        val NONE = AssumeReveals(library = false, hands = false)
    }
}
/** LOB-247 (owner 2026-11-19): which of the game creator's saved decks they lend to the other player. */
@Serializable sealed interface DeckLending {
    /** Nothing lent (the default). */
    @Serializable @SerialName("off") data object Off : DeckLending
    /** Every ordinary saved deck of the creator's (draft decks are never lent). */
    @Serializable @SerialName("all") data object All : DeckLending
    /** The decks in the chosen categories ([ids], one or several), their subcategories included (LOB-245). */
    @Serializable @SerialName("categories") data class Categories(val ids: List<String>) : DeckLending
}

/**
 * LOB-247..LOB-251 (owner 2026-11-19): a game's deck settings, set only by its creator (the inviter) in the lobby; a change
 * clears both players' Ready. [lend]: the creator's decks the other player may borrow. [onlyLent]: while lending, every
 * player plays a lent deck (a battle box: nothing from outside, the creator's other decks included). [sameDeck]: both
 * players may use the same deck. [random]: while lending, the server deals each player a lent deck as the game starts,
 * instead of each picking one. [redeal]: with [random], every rematch deals new decks.
 */
@Serializable data class DeckSettings(val lend: DeckLending = DeckLending.Off, val onlyLent: Boolean = false,
    val sameDeck: Boolean = true, val random: Boolean = false, val redeal: Boolean = false) {
    /** Whether the creator lends any deck. */
    val lending: Boolean get() = lend != DeckLending.Off
    /** Players may use only lent decks (the settings that need lending count only while lending). */
    val onlyLentDecks: Boolean get() = lending && (onlyLent || random)
    /** The server deals the decks as the game starts. */
    val dealt: Boolean get() = lending && random
    /** Each rematch deals new decks. */
    val newDealOnRematch: Boolean get() = dealt && redeal
}

/** GET /v1/games/{id}/lent-decks (LOB-248): the decks the game's creator ([lender]) lends, with the lent categories they
 * lie in (a category whose parent isn't lent lies at the top level here). Only the game's players read them. */
@Serializable data class LentDecks(val lender: AccountIdentity, val decks: List<DeckSummary>, val categories: List<DeckCategory> = emptyList())

/** A page of the games list. Read with a [token] from an earlier read ("changed since", switch-review #11): only the games
 * changed since then, with [removed] the ids of changed games that left this list (ended, cancelled, declined, no longer
 * visible). [token] is what to read changed games since next time (the first page's; null on an older read). */
@Serializable data class GameList(val games: List<GameSummary>, val next: String? = null, val token: String? = null,
    val removed: List<String> = emptyList())
@Serializable data class LobbyError(val code: String)

object LobbyValidation {
    fun name(value: String) = value.isNotBlank() && DeckValidation.text(value)
}
