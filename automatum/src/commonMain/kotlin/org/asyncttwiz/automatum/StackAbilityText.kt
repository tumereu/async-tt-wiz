package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.engine.state.StackItem
import org.asyncttwiz.protocol.MtgStackItem
import org.asyncttwiz.protocol.MtgStackKind

/**
 * STK-046: what an ability on the Stack says, for drawing it as an ability rather than as its source card: the text of the
 * triggered or activated ability the engine put on the Stack ("When this creature enters, create …").
 */
object StackAbilityText {
    /** The ability text of [item], or null for a spell, a copy of a spell, or an item without the engine's data. */
    fun of(item: MtgStackItem): String? = engine(item)?.let(::of)

    /** The ability text of the engine's [item] (a keyword's name and amount for a keyword ability). */
    fun of(item: StackItem): String? = item.ability?.let { AtwTokens.text(it) ?: CastFlow.text(it) }?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * The modes chosen for [item] (CR 700.2), a spell's (STK-050's lines under it, owner 2026-11-06) or an ability's, in
     * order, each as its printed text ("Mode 2" when it has none); empty without modes or without the engine's data.
     */
    fun modes(item: MtgStackItem): List<String> {
        if (item.modes.isEmpty() || item.data.isEmpty()) return emptyList()
        val engine = runCatching { EngineCodec.stackItem(item) }.getOrNull() ?: return emptyList()
        val options = when (val ability = engine.ability) {
            is Ability.Activated -> ability.instructions.modes
            is Ability.Triggered -> ability.instructions.modes
            is Ability.Spell -> ability.instructions.modes
            else -> null
        }?.options ?: return emptyList()
        return engine.modes.map { index -> options.getOrNull(index)?.text?.trim()?.takeIf { it.isNotEmpty() } ?: "Mode ${index + 1}" }
    }

    private fun engine(item: MtgStackItem): StackItem? {
        if (item.kind != MtgStackKind.Activated && item.kind != MtgStackKind.Triggered) return null
        if (item.data.isEmpty()) return null
        return runCatching { EngineCodec.stackItem(item) }.getOrNull()
    }
}
