package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Trigger
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.view.AbilityOrigin
import org.asyncttwiz.protocol.MtgStackItem
import org.asyncttwiz.protocol.MtgStackKind

/**
 * STK-046: what an ability on the Stack says, for drawing it as an ability rather than as its source card: the text of the
 * triggered or activated ability the engine put on the Stack ("When this creature enters, create …"), what kind of
 * ability it is ("Cycling trigger") and the short form of what it does for the Stack line ("each opponent mills 4 cards").
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

    /**
     * STK-046, STK-050 (owner playtest 2026-11-07): what kind of ability [item] is, as players name it: "Cycling trigger"
     * for a "when you cycle" trigger (CR 702.29c), a keyword's own trigger or ability by its keyword ("Offspring trigger",
     * "Cycling ability", "Equip ability"), else "Triggered ability" or "Activated ability"; null for a spell or a copy of
     * one.
     */
    fun kind(item: StackItem): String? = when (item.kind) {
        StackItemKind.Triggered -> if (cycleTrigger(item.ability)) "Cycling trigger"
            else keyword(item)?.let { "${it.text} trigger" } ?: "Triggered ability"
        StackItemKind.Activated -> keyword(item)?.let { "${it.text} ability" } ?: "Activated ability"
        else -> null
    }

    /** [kind] of a structured Stack item, or null for a spell or an item without the engine's data. */
    fun kind(item: MtgStackItem): String? = engine(item)?.let(::kind)

    /** Whether [ability] triggers when its controller cycles a card (CR 702.29c: "when you cycle this card"). */
    private fun cycleTrigger(ability: Ability?): Boolean =
        ((ability as? Ability.Triggered)?.trigger as? Trigger.On)?.events?.any { it is EventPattern.Cycle } == true

    /** HIS-091 (owner playtest 2026-11-07): whether [item] is a cycling ability (cycling or typecycling, CR 702.29a). */
    fun cycling(item: StackItem): Boolean = item.kind == StackItemKind.Activated && keyword(item)?.kind == Keyword.Cycling.Kind

    /**
     * The Stack line's short form of what [item] does (owner playtest 2026-11-07): its chosen modes' texts, else a spell's
     * own text (null for a permanent spell without one) or the ability's text ([of]), each shortened by [short]; X is the
     * announced X or, for a trigger on paying one (a cycling {X}), that X. [source]: its source's name, dropped where the
     * text begins with it.
     */
    fun effect(item: StackItem, rulesText: String? = null, source: String? = null): String? {
        val x = item.announced["X"] ?: item.event?.x
        val options = modeOptions(item.ability)
        val chosen = item.modes.mapNotNull { options?.getOrNull(it)?.text?.trim()?.takeIf { t -> t.isNotEmpty() } }
        if (chosen.isNotEmpty()) return chosen.joinToString("; ") { short(it, source, x) }
        val text = text(item, rulesText) ?: return null
        return short(text, source, x, activated = item.kind == StackItemKind.Activated,
            triggered = item.kind == StackItemKind.Triggered).takeIf { it.isNotEmpty() }
    }

    /** Whether [effect]'s text for [item] has an X in it (filled in when known); false without a text. */
    fun mentionsX(item: StackItem, rulesText: String? = null): Boolean {
        val options = modeOptions(item.ability)
        val texts = item.modes.mapNotNull { options?.getOrNull(it)?.text }.ifEmpty { listOfNotNull(text(item, rulesText)) }
        return texts.any { X_WORD.containsMatchIn(it) }
    }

    /** The whole text [effect] shortens: an ability's ([of]) or a spell's own; null for a permanent spell without one. */
    private fun text(item: StackItem, rulesText: String?): String? = when (item.kind) {
        StackItemKind.Activated, StackItemKind.Triggered -> of(item, rulesText)
        else -> (item.ability as? Ability.Spell)?.text
    }

    private val NUMBERS = listOf("two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty")
    private val NUMBER_WORDS = Regex("\\b(${NUMBERS.joinToString("|")})\\b")
    private val REMINDER = Regex("\\s*\\([^)]*\\)")
    private val REMINDER_TEXT = Regex("\\(([^)]*)\\)")
    private val TRIGGER_WORDS = Regex("^(When|Whenever|At)\\b[^,]*,\\s*", RegexOption.IGNORE_CASE)
    private val X_WORD = Regex("\\bX\\b")

    /**
     * A short form of an effect's [text] (owner playtest 2026-11-07: "each opponent mills 14 cards"): on one line, without
     * reminder text (a bare keyword line reads as its reminder: "Cycling {R} ({R}, Discard this card: Draw a card.)" →
     * "draw a card"), without an [activated] ability's cost or a [triggered] ability's trigger condition, without the
     * [source]'s own name in front, numbers two to twenty as digits, X as [x] when known, a lower-case first word unless it
     * begins a name, and no closing full stop.
     */
    fun short(text: String, source: String? = null, x: Int? = null, activated: Boolean = false, triggered: Boolean = false): String {
        var t = text.lines().map { it.trim().removePrefix("•").trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        val bare = REMINDER.replace(t, "").trim()
        val reminder = REMINDER_TEXT.find(t)?.groupValues?.get(1)?.trim()
        val fromReminder = reminder != null && bare.none { it == '.' || it == ':' }
        t = if (fromReminder) reminder!! else bare
        if (activated || fromReminder) t.indexOf(':').takeIf { it >= 0 }?.let { t = t.substring(it + 1).trim() }
        if (triggered) t = TRIGGER_WORDS.replace(t, "")
        if (source != null && t.startsWith("$source ")) t = t.removePrefix("$source ")
        t = NUMBER_WORDS.replace(t) { (NUMBERS.indexOf(it.value) + 2).toString() }
        if (x != null) t = X_WORD.replace(t, x.toString())
        t = t.trim().removeSuffix(".").trim()
        val words = t.split(' ')
        if (words[0].firstOrNull()?.isUpperCase() == true && words.getOrNull(1)?.firstOrNull()?.isUpperCase() != true)
            t = t.replaceFirstChar { it.lowercase() }
        return t
    }

    private fun modeOptions(ability: Ability?) = when (ability) {
        is Ability.Activated -> ability.instructions.modes
        is Ability.Triggered -> ability.instructions.modes
        is Ability.Spell -> ability.instructions.modes
        else -> null
    }?.options

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
        val engine = decoded(item) ?: return emptyList()
        val options = modeOptions(engine.ability) ?: return emptyList()
        return engine.modes.map { index -> options.getOrNull(index)?.text?.trim()?.takeIf { it.isNotEmpty() } ?: "Mode ${index + 1}" }
    }

    private fun engine(item: MtgStackItem): StackItem? {
        if (item.kind != MtgStackKind.Activated && item.kind != MtgStackKind.Triggered) return null
        return decoded(item)
    }

    /** The engine's Stack item of [item] (a spell's too), or null without its data. */
    fun decoded(item: MtgStackItem): StackItem? {
        if (item.data.isEmpty()) return null
        return runCatching { EngineCodec.stackItem(item) }.getOrNull()
    }
}
