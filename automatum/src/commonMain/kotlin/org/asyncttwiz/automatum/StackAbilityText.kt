package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.Keyword
import arcanum.engine.state.StackItem
import arcanum.engine.view.AbilityOrigin
import org.asyncttwiz.protocol.MtgStackItem
import org.asyncttwiz.protocol.MtgStackKind

/**
 * STK-046: what an ability on the Stack says, for drawing it as an ability rather than as its source card: the text of the
 * triggered or activated ability the engine put on the Stack ("When this creature enters, create …").
 */
object StackAbilityText {
    /** The ability text of [item], or null for a spell, a copy of a spell, or an item without the engine's data;
     * [rulesText]: its source's printed rules text, for a keyword's trigger ([of]). */
    fun of(item: MtgStackItem, rulesText: String? = null): String? = engine(item)?.let { of(it, rulesText) }

    /**
     * The ability text of the engine's [item] (a keyword's name and amount for a keyword ability). A keyword's own
     * trigger has no printed text of its own (offspring's "when this permanent enters, if its offspring cost was paid,
     * create a token that's a copy of it", CR 702.175a; owner playtest 2026-11-06): it reads as its keyword's printed
     * line in its source's [rulesText] ("Offspring {2} (You may pay an additional {2} …)"), else as the keyword
     * ("Offspring {2}").
     */
    fun of(item: StackItem, rulesText: String? = null): String? {
        item.ability?.let { AtwTokens.text(it) ?: CastFlow.text(it) }?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val keyword = keyword(item) ?: return null
        return keywordLine(keyword.text, rulesText) ?: AtwTokens.text(keyword) ?: keyword.text
    }

    /** The keyword whose expansion [item]'s ability is ([AbilityOrigin.Expansion]), as its source was last seen. */
    private fun keyword(item: StackItem): Keyword? {
        val origin = item.origin as? AbilityOrigin.Expansion ?: return null
        val seen = item.snapshots.firstOrNull { it.id == item.source }?.characteristics ?: return null
        return seen.abilities.firstOrNull { it.origin == origin.keyword }?.ability as? Keyword
    }

    /**
     * The line of [rulesText] a keyword's [text] stands for ("Offspring" → "Offspring {2} (You may pay …)"): the
     * printed line that begins with it; null when [text] is more than a keyword's name or no line begins with it.
     */
    fun keywordLine(text: String, rulesText: String?): String? {
        if (rulesText == null || text.isBlank() || text.any { it == '.' || it == ',' }) return null
        return rulesText.lines().map { it.trim() }.firstOrNull { line ->
            line.startsWith(text, ignoreCase = true) && line.getOrNull(text.length)?.isLetter() != true
        }
    }

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
