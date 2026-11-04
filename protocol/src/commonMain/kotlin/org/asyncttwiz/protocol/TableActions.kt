package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Fixed positions. Main phases have one position, not an invented additional step. [FIRST_STRIKE_DAMAGE] is the
 * first of two combat damage steps (CR 510.4) Automatum adds when a creature with first or double strike is in combat
 * (I-D31); playing by hand never passes through it ([MANUAL]). */
@Serializable enum class TurnPosition(val phase: String, val step: String, val label: String, val glyph: String) {
    UNTAP("BEGINNING", "UNTAP", "Untap", "↶"),
    UPKEEP("BEGINNING", "UPKEEP", "Upkeep", "⌛"),
    DRAW("BEGINNING", "DRAW", "Draw", "↓"),
    PRECOMBAT_MAIN("PRECOMBAT_MAIN", "MAIN", "Precombat main", "①"),
    BEGIN_COMBAT("COMBAT", "BEGIN_COMBAT", "Beginning of combat", "⚑"),
    DECLARE_ATTACKERS("COMBAT", "DECLARE_ATTACKERS", "Declare attackers", "⚔"),
    DECLARE_BLOCKERS("COMBAT", "DECLARE_BLOCKERS", "Declare blockers", "⛨"),
    FIRST_STRIKE_DAMAGE("COMBAT", "FIRST_STRIKE_DAMAGE", "First-strike damage", "⚡"),
    COMBAT_DAMAGE("COMBAT", "COMBAT_DAMAGE", "Combat damage", "♨"),
    END_COMBAT("COMBAT", "END_COMBAT", "End of combat", "⚐"),
    POSTCOMBAT_MAIN("POSTCOMBAT_MAIN", "MAIN", "Postcombat main", "②"),
    END_STEP("ENDING", "END_STEP", "End step", "☾"),
    CLEANUP("ENDING", "CLEANUP", "Cleanup", "◇");

    companion object {
        fun of(turn: TurnState) = entries.singleOrNull { it.phase == turn.phase && it.step == turn.step }

        /** The twelve positions a turn passes when played by hand (TRN-001): every one but [FIRST_STRIKE_DAMAGE]. */
        val MANUAL: List<TurnPosition> = entries.filter { it != FIRST_STRIKE_DAMAGE }
    }
}

object TableActions {
    fun numeric(value: String) = DeckValidation.text(value) && value.matches(Regex("[+-]?[0-9]+"))

    /** Shared scalar arithmetic; exact signed decimal strings, bounded only by the existing text limit. */
    fun addNumber(from: String, delta: String): String? {
        if (!numeric(from) || !numeric(delta)) return null
        return GameplayNumbers.add(from, delta).takeIf(DeckValidation::text)
    }
}

internal object GameplayNumbers {
    fun integer(value: String) = value.matches(Regex("[+-]?[0-9]+"))
    fun add(left: String, right: String): String {
        fun magnitude(s: String) = s.removePrefix("-").removePrefix("+").trimStart('0').ifEmpty { "0" }
        val a = magnitude(left); val b = magnitude(right)
        val negativeA = left.startsWith('-') && a != "0"; val negativeB = right.startsWith('-') && b != "0"
        val result: String; val negative: Boolean
        if (negativeA == negativeB) {
            var carry = 0; val digits = StringBuilder()
            for (i in 0 until maxOf(a.length, b.length)) {
                val sum = (a.getOrNull(a.lastIndex - i)?.digitToInt() ?: 0) + (b.getOrNull(b.lastIndex - i)?.digitToInt() ?: 0) + carry
                digits.append(sum % 10); carry = sum / 10
            }
            if (carry != 0) digits.append(carry)
            result = digits.reverse().toString(); negative = negativeA
        } else {
            val comparison = if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b)
            val high = if (comparison >= 0) a else b; val low = if (comparison >= 0) b else a
            var borrow = 0; val digits = StringBuilder()
            for (i in high.indices) {
                var value = high[high.lastIndex - i].digitToInt() - (low.getOrNull(low.lastIndex - i)?.digitToInt() ?: 0) - borrow
                borrow = if (value < 0) 1 else 0
                if (value < 0) value += 10
                digits.append(value)
            }
            result = digits.reverse().toString().trimStart('0').ifEmpty { "0" }
            negative = if (comparison >= 0) negativeA else negativeB
        }
        return if (negative && result != "0") "-$result" else result
    }
}
