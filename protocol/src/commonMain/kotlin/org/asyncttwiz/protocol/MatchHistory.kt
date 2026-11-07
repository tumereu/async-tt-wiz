package org.asyncttwiz.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Game history (spec game-history): the server's record of one match (one played game inside a game's lobby, from its
 * start to its result), written when the result is final, or as Unfinished ([MatchEnding.Unfinished]) when the game was
 * deleted while the match was in play. Records are never deleted. A viewer gets only the deck fields they may see: the
 * opponent's deck once it was shown to them in any game ([MatchPlayer.deck] null until then); a pod's members get the
 * pod's matches without any deck.
 */
@Serializable data class MatchRecord(
    val id: String,
    val gameId: String,
    val gameName: String,
    val podId: String? = null,
    val podName: String? = null,
    /** When the match started and ended (ISO-8601 instants). */
    val startedAt: String,
    val endedAt: String,
    /** The engine's turn number at the end (0: it ended in the opening); for an Unfinished match, the turn reached. */
    val turns: Int,
    /** The two players, the game's inviter first. */
    val players: List<MatchPlayer>,
    val ending: MatchEnding,
) {
    fun player(accountId: String): MatchPlayer? = players.firstOrNull { it.account.accountId == accountId }
    fun opponent(accountId: String): MatchPlayer? = players.firstOrNull { it.account.accountId != accountId }
    val finished: Boolean get() = ending !is MatchEnding.Unfinished
}

/** One player of a [MatchRecord]: their [result] (null when Unfinished), mulligans, final life, whether they started
 * ([onThePlay]), their deck as the viewer may see it, and why they lost when the rules decided ([lossCause]). */
@Serializable data class MatchPlayer(
    val account: AccountIdentity,
    val result: SuggestedResult? = null,
    val mulligans: Int? = null,
    val finalLife: Int? = null,
    val onThePlay: Boolean = false,
    val deck: MatchDeck? = null,
    val lossCause: LossCause? = null,
)

/** A deck as a record holds it: its name, cover and colours (WUBRG letters of its Main cards, "" for colourless), no card
 * list. [sourceId]: the saved deck's id, or the pod's id for a pod deck. [openable]: the viewer's own deck, still saved
 * (Open deck). */
@Serializable data class MatchDeck(val name: String, val cover: DeckCover? = null, val colors: String = "",
    val sourceId: String, val openable: Boolean = false)

/** How a match ended. */
@Serializable sealed interface MatchEnding {
    /** By the rules: each loser's [MatchPlayer.lossCause] says why. */
    @Serializable @SerialName("rules") data object ByRules : MatchEnding
    /** [by] conceded (CR 104.3a). */
    @Serializable @SerialName("conceded") data class Conceded(val by: String) : MatchEnding
    /** A suggestion accepted (TRN-047), with its optional reason (TRN-046). */
    @Serializable @SerialName("agreed") data class Agreed(val reason: String = "") : MatchEnding
    /** A draw by the rules: everyone lost at once (CR 104.4a) or a loop ([loop], CR 104.4b). */
    @Serializable @SerialName("draw") data class Draw(val loop: Boolean = false) : MatchEnding
    /** The game was deleted while the match was in play: no result. */
    @Serializable @SerialName("unfinished") data object Unfinished : MatchEnding
}

/** Why a player lost by the rules. */
@Serializable sealed interface LossCause {
    /** Life 0 or less (CR 704.5a). */
    @Serializable @SerialName("life") data object ZeroLife : LossCause
    /** Drawing from an empty library (CR 704.5b). */
    @Serializable @SerialName("library") data object EmptyLibrary : LossCause
    /** 10 or more poison counters (CR 704.5c). */
    @Serializable @SerialName("poison") data object Poison : LossCause
    /** A card's effect (CR 104.3e), naming the card. */
    @Serializable @SerialName("card") data class CardEffect(val card: String? = null) : LossCause
}

/** GET /v1/history (your records) and GET /v1/games/{id}/history (this game's): newest first. */
@Serializable data class MatchHistory(val records: List<MatchRecord>)

/** GET /v1/pods/{id}/matches: every finished match of the pod and every one in play, without decks (members only). */
@Serializable data class PodMatches(val records: List<MatchRecord>, val playing: List<PodMatchInPlay> = emptyList())

/** A pod's match in play: its game, players (inviter first) and the turn reached. */
@Serializable data class PodMatchInPlay(val gameId: String, val gameName: String, val players: List<AccountIdentity>, val turn: Int)

/** The colours of a deck's Main cards as WUBRG letters: each card's mana cost symbols (hybrid and Phyrexian too) and colour
 * indicator, of its front face and that face's printed parts. */
object DeckColors {
    private const val ORDER = "WUBRG"
    fun of(cards: Iterable<CardDefinition>): String {
        val found = mutableSetOf<Char>()
        for (card in cards) {
            val face = card.faces.firstOrNull() ?: continue
            val texts = listOf(face.manaCost, face.colorIndicator.orEmpty()) + face.parts.flatMap { listOf(it.manaCost, it.colorIndicator.orEmpty()) }
            for (text in texts) {
                // Mana symbols: the letters inside braces ({W}, {W/U}, {G/P}, {2/R}); a colour indicator: plain letters
                val letters = if ('{' in text) Regex("\\{([^}]*)\\}").findAll(text).flatMap { it.groupValues[1].asSequence() } else text.asSequence()
                letters.filter { it in ORDER }.forEach { found += it }
            }
        }
        return ORDER.filter { it in found }
    }
}
