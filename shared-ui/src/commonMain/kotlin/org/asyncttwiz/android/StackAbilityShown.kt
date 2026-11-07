package org.asyncttwiz.android

// Web spike: split from StackAbilities.kt (androidApp) with the table drawing
/**
 * STK-046 (owner 2026-11-04, MTG Arena as the reference): a triggered or activated ability on the Stack is drawn as an
 * ability, never as its source card: a smaller token with the source's art, a trigger or activation marker, the source's
 * name and the ability's text. [source] is the source's name, [text] the ability's text ("" when unknown).
 */
data class StackAbilityShown(val triggered: Boolean, val source: String, val text: String) {
    /** How the ability is named in lists and texts: "Ember Herald's ability". */
    val name: String get() = "$source's ability"
    /** The source's name and the ability's text: "Ember Herald — When this creature enters, …". */
    val line: String get() = if (text.isEmpty()) source else "$source — $text"
    /** The marker's words: "Triggered ability" or "Activated ability". */
    val kind: String get() = if (triggered) "Triggered ability" else "Activated ability"
    /** The kind chip's letter (STK-046): "T" for a triggered ability, "A" for an activated one. */
    val letter: String get() = if (triggered) "T" else "A"
}
