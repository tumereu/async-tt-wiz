package org.asyncttwiz.automatum

import arcanum.engine.state.StackItem
import org.asyncttwiz.protocol.MtgStackItem
import org.asyncttwiz.protocol.MtgStackKind

/**
 * STK-046: what an ability on the Stack says, for drawing it as an ability rather than as its source card: the text of the
 * triggered or activated ability the engine put on the Stack ("When this creature enters, create …").
 */
object StackAbilityText {
    /** The ability text of [item], or null for a spell, a copy of a spell, or an item without the engine's data. */
    fun of(item: MtgStackItem): String? {
        if (item.kind != MtgStackKind.Activated && item.kind != MtgStackKind.Triggered) return null
        if (item.data.isEmpty()) return null
        val engine = runCatching { EngineCodec.stackItem(item) }.getOrNull() ?: return null
        return of(engine)
    }

    /** The ability text of the engine's [item] (a keyword's name and amount for a keyword ability). */
    fun of(item: StackItem): String? = item.ability?.let { AtwTokens.text(it) ?: CastFlow.text(it) }?.trim()?.takeIf { it.isNotEmpty() }
}
