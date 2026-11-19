package org.asyncttwiz.automatum

import org.asyncttwiz.protocol.Faults

/** Players' names in the texts players read (LOB-090, owner rule 2026-11-11: a generic stand-in shown is a fault). */
object PlayerNames {
    /** [names]' name for [accountId]; without one [standIn] ("the opponent"), reported as a placeholder from [area]. */
    fun of(names: Map<String, String>, accountId: String, standIn: String, area: String): String =
        names[accountId] ?: Faults.placeholder(area, "player without a name", standIn,
            mapOf("player" to accountId, "named" to names.keys.joinToString()))
}
