package org.asyncttwiz.android

/** SYS-051: the shared card type order. Every grouping of cards by type uses it; a card joins the first
 * group its front type line names. [label] is the plural group title, [type] the singular type word. */
internal enum class CardTypeGroup(val label: String, val type: String?) {
    CREATURES("Creatures", "Creature"), PLANESWALKERS("Planeswalkers", "Planeswalker"), BATTLES("Battles", "Battle"),
    INSTANTS("Instants", "Instant"), SORCERIES("Sorceries", "Sorcery"), ARTIFACTS("Artifacts", "Artifact"),
    ENCHANTMENTS("Enchantments", "Enchantment"), LANDS("Lands", "Land"), OTHER("Other", null);

    /** Singular header word, e.g. "Creature" or "Other". */
    val singular: String get() = type ?: label
}

/** Card types only: subtypes after a dash and later faces never decide the group. */
internal fun cardTypeGroup(typeLine: String): CardTypeGroup {
    val types = typeLine.substringBefore("//").substringBefore('—').substringBefore(" - ")
        .split(' ', '\t').filter { it.isNotBlank() }.map { it.lowercase() }.toSet()
    return CardTypeGroup.entries.firstOrNull { group -> group.type?.lowercase()?.let { it in types } == true }
        ?: CardTypeGroup.OTHER
}
