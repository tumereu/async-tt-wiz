package org.asyncttwiz.storage

import arcanum.api.Ability
import arcanum.api.CardDefinition

/** What the device knows about one game card's rules (docs/specs/arcanum-atw-integration.md I-D2). */
sealed interface ParsedCard {
    /** The card parsed: [definition] keeps every ability the parser couldn't read as [Ability.Unparsed] (played by
     * hand); [manualAbilities] counts them over all faces. */
    data class Read(val definition: CardDefinition, val manualAbilities: Int) : ParsedCard {
        val fullyParsed: Boolean get() = manualAbilities == 0
    }

    /** The card's printed values (type line, mana cost, P/T, faces) couldn't be read, so the whole card stays
     * manual; [problem] says why. */
    data class Unreadable(val problem: String) : ParsedCard
}
