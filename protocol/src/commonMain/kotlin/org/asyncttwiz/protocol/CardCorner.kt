package org.asyncttwiz.protocol

/** Printed/default corner shared by deck cards, tokens and explicit/destination resets.
 * Retained events replay their recorded values, never recompute this default. */
fun DefinitionFace.defaultCorner(): String = when {
    typeLine.contains("Planeswalker", ignoreCase = true) && loyalty.isNotBlank() -> loyalty
    typeLine.contains("Battle", ignoreCase = true) && defense.isNotBlank() -> defense
    typeLine.substringAfter('—', "").split(Regex("\\s+")).any { it.equals("Saga", ignoreCase = true) } -> "1"
    corner.isNotEmpty() -> corner
    power.isNotEmpty() || toughness.isNotEmpty() -> "$power/$toughness"
    loyalty.isNotEmpty() -> loyalty
    else -> defense
}
