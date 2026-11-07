package arcanum.engine.setup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Why a player lost the game (CR 104.3), kept from the moment they lose as
 * [arcanum.engine.state.PlayerState.lossReason] (null while they haven't lost) and carried by the
 * [arcanum.engine.change.Change.Lost] that made them lose, so undoing the loss takes the reason back with it. When a
 * player loses for several reasons at once, the first state-based action listed in CR 704.5 names it.
 */
@Serializable
sealed interface LossReason {
    /** They had 0 or less life (CR 104.3b, 704.5a). */
    @Serializable @SerialName("life") data object ZeroLife : LossReason

    /** They attempted to draw a card from a library with no cards in it (CR 104.3c, 704.5b). */
    @Serializable @SerialName("empty-library") data object EmptyLibrary : LossReason

    /** They had ten or more poison counters (CR 104.3d, 704.5c). */
    @Serializable @SerialName("poison") data object Poison : LossReason

    /** An effect said they lose the game, or that another player wins it (CR 104.3e, 104.2b); [card] is the name its
     * source had then (null when it had none). */
    @Serializable @SerialName("effect") data class Effect(val card: String?) : LossReason

    /** They conceded ([arcanum.engine.input.Input.Concede], CR 104.3a). */
    @Serializable @SerialName("conceded") data object Conceded : LossReason

    /** They left as the players agreed ([arcanum.engine.input.Input.AgreedResult]: the others conceding to the winner,
     * CR 104.3a, or an intentional draw, CR 104.4i). */
    @Serializable @SerialName("agreed") data object Agreed : LossReason

    /** The game was in a loop of mandatory actions and is a draw: the remaining players left it together (CR 104.4b,
     * [arcanum.engine.input.Input.MandatoryLoop]). */
    @Serializable @SerialName("loop") data object Loop : LossReason
}
