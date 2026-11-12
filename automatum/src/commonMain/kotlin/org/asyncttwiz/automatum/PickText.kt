package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TextSpan
import arcanum.engine.Engine
import arcanum.engine.Inspect
import arcanum.engine.decision.Announced
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.CostPartAt
import arcanum.engine.decision.Decision
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.TargetRef
import arcanum.engine.view.AbilityInstance
import arcanum.engine.view.AbilityOrigin

/**
 * STK-047, STK-030 (owner, 2026-11-11, "which pick is which"): the text of the spell or ability whose targets or costs are
 * being chosen, read so the player sees which phrase each pick is for: the phrase chosen now ([Part.Now]), each phrase
 * already chosen as what was chosen ([Part.Chosen]), and for a choice with no phrase of its own (a keyword's target, "Equip",
 * "support 2") the names after it in brackets ([Part.After]). Phrases are the parser's spans ([arcanum.api.TargetSlot.span],
 * [Cost.spans]). [roles] says what each card picked so far is for, for its chip on the table ("Target", "Sacrificed", "Equip").
 */
data class PickText(val parts: List<Part>, val roles: List<Pair<TargetRef, String>>) {
    sealed interface Part {
        /** Text as printed. */
        data class Plain(val text: String) : Part
        /** The phrase of the choice open now. */
        data class Now(val text: String) : Part
        /** A phrase whose choice is made: what was chosen in its place; [none] the words for nothing chosen ("no creature"). */
        data class Chosen(val chosen: List<TargetRef>, val none: String) : Part
        /** What was chosen for a choice without a phrase, named in brackets after the words before it. */
        data class After(val chosen: List<TargetRef>) : Part
    }

    /** The text with each choice's names as [names] gives them (the bracketed ones in brackets); the open phrase as printed. */
    fun text(names: (List<TargetRef>, String) -> String): String = parts.joinToString("") { part ->
        when (part) {
            is Part.Plain -> part.text
            is Part.Now -> part.text
            is Part.Chosen -> names(part.chosen, part.none)
            is Part.After -> " (" + names(part.chosen, "") + ")"
        }
    }

    /** The phrase being chosen now, when the text has one. */
    val now: String? get() = parts.filterIsInstance<Part.Now>().firstOrNull()?.text

    companion object {
        /** The announcement a pick of [continuation] belongs to: a cast's or activation's, or a trigger's as it is put on the
         * Stack (CR 601.2, 602.2, 603.3d): for a trigger whose targets are asked before it is begun, the waiting trigger of
         * [decision]'s source and player, with nothing chosen yet. */
        fun announcing(continuation: Continuation?, decision: Decision? = null): Continuation.Announcing? = when (continuation) {
            is Continuation.Announcing -> continuation
            is Continuation.PlacingTriggers -> continuation.current ?: decision?.let { d ->
                continuation.waiting.firstOrNull { it.source == d.source && it.controller == d.player }
                    ?: continuation.waiting.singleOrNull { it.controller == d.player }
            }?.let { Continuation.Announcing(it.controller, Announced.Trigger(it), arcanum.engine.decision.AnnounceStage.Targets) }
            else -> null
        }

        /**
         * The text for [decision], open in [announcing] on [state]: [costs] are the cost choices made so far in it (their
         * printed parts and the objects chosen), [picked] what is picked so far in [decision] when it takes several, [rulesText]
         * the source's printed rules text (for a keyword's line). Null when there is no text to show it in.
         */
        fun of(state: GameState, announcing: Continuation.Announcing, decision: Decision?, costs: List<Pair<CostPartAt, List<ObjectId>>>,
            picked: List<TargetRef> = emptyList(), rulesText: String? = null): PickText? {
            val what = What.of(state, announcing.what, rulesText) ?: return null
            val openSlot = (decision as? Decision.Targets)?.slot?.substringBefore(':')
            val openCost = (decision as? Decision.ChooseObjects)?.cost
            val chosenTargets = announcing.choices.targets
            val chosenCosts = costs.toMap()
            val segments = mutableListOf<Segment>()
            // An additional or alternative cost the spell states, before its own text, while it is chosen or was
            (openCost?.let { listOf(it) }.orEmpty() + costs.map { it.first }).mapNotNull { it.ability }.distinct().forEach { origin ->
                what.costOf(origin)?.let { (text, cost) -> segments += Segment(text, emptyList(), cost.spans.mapIndexed { i, s -> s to CostPartAt(origin, i) }) }
            }
            val own = what.text
            if (own != null) {
                val slots = what.instructions?.let { slotSpans(it, announcing.choices.modes, own) }.orEmpty()
                segments += Segment(own, slots.mapNotNull { (key, span) -> span?.let { key to it } },
                    what.cost?.spans.orEmpty().mapIndexed { i, s -> s to CostPartAt(null, i) }, slots.filter { it.second == null }.map { it.first })
            }
            if (segments.isEmpty()) return null
            val parts = mutableListOf<Part>()
            segments.forEachIndexed { index, segment ->
                if (index > 0) parts += Part.Plain("\n")
                parts += segment.parts(openSlot, openCost, chosenTargets, chosenCosts, what.keywordName)
            }
            val roles = mutableListOf<Pair<TargetRef, String>>()
            fun slotRole() = what.keywordName ?: "Target"
            chosenTargets.forEach { (key, refs) -> if (key.substringBefore(':') != openSlot) refs.forEach { roles += it to slotRole() } }
            costs.forEach { (at, ids) -> what.part(at)?.let { part -> ids.forEach { roles += TargetRef.Object(it) to costRole(part) } } }
            val openRole = when {
                openSlot != null -> slotRole()
                openCost != null -> what.part(openCost)?.let(::costRole)
                else -> null
            }
            if (openRole != null) picked.forEach { roles += it to openRole }
            return PickText(merged(parts), roles.distinctBy { it.first })
        }

        /** What a card picked for [part] is for, on its chip: "Sacrificed", "Tapped", "Returned", … */
        fun costRole(part: CostPart): String = when (part) {
            is CostPart.Sacrifice -> "Sacrificed"
            is CostPart.TapObjects -> "Tapped"
            is CostPart.ReturnToHand -> "Returned"
            is CostPart.Exile -> "Exiled"
            is CostPart.Discard -> "Discarded"
            is CostPart.Reveal -> "Revealed"
            is CostPart.RemoveCounters -> "Counter removed"
            is CostPart.PutCounters -> "Counter put"
            is CostPart.Action -> when (part.action) {
                is arcanum.api.KeywordAction.Behold -> "Beheld"
                is arcanum.api.KeywordAction.Blight -> "Blighted"
                else -> "Chosen"
            }
            else -> "Chosen"
        }


        /** Each target slot of [instructions] (its own, then each chosen mode's) by key, with its phrase's span in [text]. */
        private fun slotSpans(instructions: Instructions, modes: List<Int>, text: String): List<Pair<String, TextSpan?>> {
            val own = instructions.targets.map { it.name to it.span }
            val options = instructions.modes?.options.orEmpty()
            val fromModes = modes.withIndex().flatMap { (position, index) ->
                val mode = options.getOrNull(index) ?: return@flatMap emptyList()
                val at = mode.text?.takeIf { it.isNotEmpty() }?.let { text.indexOf(it) }?.takeIf { it >= 0 }
                mode.instructions.targets.map { slot ->
                    StackAbilityText.slotKey(instructions, modes, position, slot.name) to
                        if (at == null) null else slot.span?.let { TextSpan(it.start + at, it.end + at) }
                }
            }
            return own + fromModes
        }

        /** Neighbouring plain parts joined. */
        private fun merged(parts: List<Part>): List<Part> = parts.fold(mutableListOf()) { acc, part ->
            val last = acc.lastOrNull()
            if (part is Part.Plain && last is Part.Plain) acc[acc.size - 1] = Part.Plain(last.text + part.text) else acc += part
            acc
        }

        /** Words for nothing chosen for a phrase: "no creature" for "up to one target creature". */
        fun none(phrase: String): String {
            var rest = phrase.trim()
            while (true) {
                val m = LEADING.find(rest)?.takeIf { it.range.first == 0 } ?: break
                rest = rest.substring(m.range.last + 1).trimStart()
            }
            return "no " + rest.ifEmpty { "target" }
        }

        private val LEADING = Regex("^(?:up to (?:one|two|three|four|five|six|seven|eight|nine|ten|x|\\d+)|any number of|one or more|one or two|one, two, or three|another|other|targets?|an?|the|x|one|two|three|four|five|\\d+)\\b",
            RegexOption.IGNORE_CASE)
    }

    /** One printed text with its phrases: target slots' by key, cost parts' by where they are printed; [unplaced] slots
     * without a phrase. */
    private class Segment(val text: String, val slots: List<Pair<String, TextSpan>>, val costs: List<Pair<TextSpan, CostPartAt>>,
        val unplaced: List<String> = emptyList()) {
        fun parts(openSlot: String?, openCost: CostPartAt?, targets: Map<String, List<TargetRef>>, costs: Map<CostPartAt, List<ObjectId>>,
            keyword: String?): List<Part> {
            class Mark(val span: TextSpan, val part: Part)
            val marks = mutableListOf<Mark>()
            for ((span, at) in this.costs) {
                if (span.end > text.length) continue
                val phrase = text.substring(span.start, span.end)
                when {
                    at == openCost -> marks += Mark(span, Part.Now(phrase))
                    at in costs -> marks += Mark(span, Part.Chosen(costs[at].orEmpty().map { TargetRef.Object(it) }, none(phrase)))
                }
            }
            for ((span, key) in slots.groupBy({ it.second }, { it.first })) {
                if (span.end > text.length) continue
                val phrase = text.substring(span.start, span.end)
                when {
                    openSlot in key -> marks += Mark(span, Part.Now(phrase))
                    key.any { it in targets } -> marks += Mark(span, Part.Chosen(key.flatMap { targets[it].orEmpty() }.distinct(), none(phrase)))
                }
            }
            // A slot without a phrase (a keyword's target): its names follow the keyword's name and amount ("Equip", "Support 2"),
            // or the text; the keyword's name is the phrase while it is chosen
            val lead = keyword?.let { k -> KEYWORD_LEAD(k).find(text)?.takeIf { it.range.first == 0 }?.let { TextSpan(0, it.range.last + 1) } }
            val afterAt = lead?.end ?: text.trimEnd().removeSuffix(".").length
            val after = unplaced.filter { it in targets }.flatMap { targets[it].orEmpty() }.distinct()
            if (openSlot != null && openSlot in unplaced && lead != null) marks += Mark(lead, Part.Now(text.substring(lead.start, lead.end)))
            val out = mutableListOf<Part>()
            var at = 0
            var afterDone = after.isEmpty()
            for (mark in marks.sortedBy { it.span.start }) {
                if (mark.span.start < at) continue
                if (!afterDone && afterAt <= mark.span.start) {
                    out += Part.Plain(text.substring(at, afterAt)); out += Part.After(after); at = afterAt; afterDone = true
                }
                if (mark.span.start > at) out += Part.Plain(text.substring(at, mark.span.start))
                out += mark.part
                at = mark.span.end
                if (!afterDone && afterAt == at) { out += Part.After(after); afterDone = true }
            }
            if (!afterDone && afterAt >= at) {
                out += Part.Plain(text.substring(at, afterAt)); out += Part.After(after); at = afterAt
            }
            if (at < text.length) out += Part.Plain(text.substring(at))
            return out
        }

        private companion object {
            fun KEYWORD_LEAD(keyword: String) = Regex("${Regex.escape(keyword)}(?: (?:\\d+|X))?", RegexOption.IGNORE_CASE)
        }
    }

    /** What is announced, as far as its text goes: its own [text] with its [instructions] and [cost], the keyword it stands for
     * ([keywordName]), and the abilities of its source (for the costs its other abilities state). */
    private class What(val text: String?, val instructions: Instructions?, val cost: Cost?, val keywordName: String?,
        val abilities: List<AbilityInstance>, val rulesText: String?) {
        /** The text and cost of the cost [origin]'s ability states: a static ability's sentence, or a keyword's line. */
        fun costOf(origin: AbilityOrigin): Pair<String, Cost>? {
            val inst = abilities.firstOrNull { it.origin == origin } ?: return null
            val cost = when (val a = inst.ability) {
                is Ability.Static -> a.mechanics.firstNotNullOfOrNull { m -> (m as? Mechanic.AdditionalCost)?.cost ?: (m as? Mechanic.AlternativeCost)?.cost }
                is arcanum.api.HasCost -> a.cost
                else -> null
            } ?: return null
            val text = (inst.ability as? Ability.Static)?.text ?: keywordText(origin) ?: return null
            return text to cost
        }

        /** The printed cost part [at] names. */
        fun part(at: CostPartAt): CostPart? = (if (at.ability == null) cost else at.ability?.let { costOf(it)?.second })?.parts?.getOrNull(at.part)

        /** The line of the keyword [origin] stands for (itself, or the keyword whose expansion it is), without reminder text. */
        private fun keywordText(origin: AbilityOrigin): String? {
            val keywordOrigin = (origin as? AbilityOrigin.Expansion)?.keyword ?: origin
            val keyword = abilities.firstOrNull { it.origin == keywordOrigin }?.ability as? Keyword ?: return null
            return line(keyword, rulesText)
        }

        companion object {
            fun of(state: GameState, what: Announced, rulesText: String?): What? {
                val view = Engine.view(state)
                fun abilitiesOf(id: ObjectId?): List<AbilityInstance> = id?.let { view[it]?.characteristics?.abilities ?: Inspect.lastKnown(state, it)?.characteristics?.abilities }.orEmpty()
                return when (what) {
                    is Announced.Spell -> {
                        val abilities = abilitiesOf(what.card)
                        val spell = abilities.firstNotNullOfOrNull { it.ability as? Ability.Spell }
                        What(spell?.text?.takeIf { it.isNotBlank() }, spell?.instructions, null, null, abilities, rulesText)
                    }
                    is Announced.Ability -> ability(what.ability, what.origin, abilitiesOf(what.source), rulesText)
                    is Announced.Trigger -> ability(what.trigger.ability, what.trigger.origin,
                        abilitiesOf(what.trigger.source).ifEmpty { what.trigger.sourceSeen?.characteristics?.abilities.orEmpty() }, rulesText)
                }
            }

            private fun ability(ability: Ability, origin: AbilityOrigin?, abilities: List<AbilityInstance>, rulesText: String?): What {
                val keyword = (origin as? AbilityOrigin.Expansion)?.let { o -> abilities.firstOrNull { it.origin == o.keyword }?.ability as? Keyword }
                val own = when (ability) {
                    is Ability.Activated -> ability.text
                    is Ability.Triggered -> ability.text
                    is Ability.SpecialAction -> ability.text
                    else -> null
                }?.takeIf { it.isNotBlank() }
                val text = own ?: keyword?.let { line(it, rulesText) }
                val instructions = when (ability) {
                    is Ability.Activated -> ability.instructions
                    is Ability.Triggered -> ability.instructions
                    is Ability.SpecialAction -> ability.instructions
                    else -> null
                }
                val cost = (ability as? Ability.Activated)?.cost ?: (ability as? Ability.SpecialAction)?.cost
                return What(text, instructions, cost, keyword?.text?.takeIf { own == null }, abilities, rulesText)
            }

            /** [keyword]'s printed line in [rulesText] without its reminder text; its name without one. */
            fun line(keyword: Keyword, rulesText: String?): String =
                (StackAbilityText.keywordLine(keyword.text, rulesText) ?: keyword.text).let { REMINDER.replace(it, "").trim() }

            private val REMINDER = Regex("\\s*\\([^)]*\\)")
        }
    }
}
