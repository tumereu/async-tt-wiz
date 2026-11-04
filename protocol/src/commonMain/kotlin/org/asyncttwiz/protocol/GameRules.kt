package org.asyncttwiz.protocol

/**
 * The rules a game is played by (I-D49), for now the same for every game ([STANDARD]); later per-game settings.
 * With Automatum on the opening follows them: each player draws [openingHand] cards and may take London mulligans
 * (CR 103.5): the hand goes back, the library is shuffled and [openingHand] cards are drawn again; on keeping, one card
 * per mulligan beyond the first [freeMulligans] goes on the bottom of the library.
 */
data class GameRules(val openingHand: Int = 7, val freeMulligans: Int = 1) {
    /** The cards owed to the bottom of the library on keeping after [mulligans] mulligans. */
    fun owed(mulligans: Int): Int = (mulligans - freeMulligans).coerceAtLeast(0)

    /** Whether another mulligan may be taken after [mulligans]: only while the hand kept would still hold a card
     * (CR 103.5: until the opening hand would be zero cards). */
    fun canMulligan(mulligans: Int): Boolean = openingHand - owed(mulligans) > 0 && mulligans < MAX_MULLIGANS

    companion object {
        /** Opening hand 7, the first mulligan free, London mulligans. */
        val STANDARD = GameRules()
        /** At most this many mulligans a player takes. */
        const val MAX_MULLIGANS = 64
    }
}
