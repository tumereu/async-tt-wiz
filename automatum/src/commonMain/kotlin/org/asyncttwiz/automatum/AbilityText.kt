package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.ColorRef
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.isManaAbility
import org.asyncttwiz.protocol.Faults

/**
 * TBL-124 (owner, 2026-11-12: a chip names what it does): the words players read for an ability that has no printed text
 * of its own, worked out from what it is. A mana ability says the mana it makes in symbols, as a card would: "{T}: Add {G}.",
 * "{T}: Add {R} or {G}.", "{T}: Add one mana of any color."; anything else says its cost, which is all that can be read
 * of it, and is reported as a stand-in ([Faults.placeholder], owner rule 2026-11-11).
 */
object AbilityText {
    /** [ability]'s printed text, its keyword's, or the words worked out from it ([of]); [area] names the caller in a report. */
    fun shown(ability: Ability, area: String): String = CastFlow.text(ability) ?: of(ability, area)

    /** The words worked out for [ability], which has no text of its own. */
    fun of(ability: Ability, area: String): String {
        val cost = (ability as? Ability.Activated)?.cost?.let(::cost)?.takeIf { it.isNotEmpty() }
        if (ability.isManaAbility()) mana(ability)?.let { return listOfNotNull(cost, it).joinToString(": ") + "." }
        val kind = when {
            ability is Ability.SpecialAction -> "special action"
            ability.isManaAbility() -> "mana ability"
            ability is Ability.Triggered -> "triggered ability"
            ability is Ability.Activated -> "activated ability"
            else -> "ability"
        }
        return Faults.placeholder(area, "$kind without text", cost?.let { "$it: …" } ?: kind.replaceFirstChar { it.uppercase() },
            mapOf("ability" to ability.toString().take(300)))
    }

    /** A cost as printed: "{2}, {T}", "{T}, Sacrifice", "Pay 2 life". */
    fun cost(cost: Cost): String = cost.parts.joinToString(", ") { part ->
        when (part) {
            CostPart.TapSelf -> "{T}"
            CostPart.UntapSelf -> "{Q}"
            is CostPart.PayLife -> "Pay ${CombatFlow.costLabel(Cost(listOf(part)))}"
            else -> CombatFlow.costLabel(Cost(listOf(part)))
        }
    }

    /** "Add …" for the mana [ability] makes, when it makes it plainly (one AddMana it always does); null otherwise. */
    private fun mana(ability: Ability): String? {
        val steps = (ability as? Ability.Activated)?.instructions?.steps ?: (ability as? Ability.Triggered)?.instructions?.steps ?: return null
        val adds = steps.mapNotNull { ((it as? Step.Do)?.effect as? Effect.AddMana)?.mana }
        if (adds.size != 1 || steps.size != 1) return null
        return production(adds.single())?.let { "Add $it" }
    }

    /** The mana [production] makes, in printed words: "{G}", "{R} or {G}", "one mana of any color". Null: not plain. */
    fun production(production: ManaProduction): String? = when (val kind = production.kind) {
        is ManaKind.Fixed -> kind.mana.toString().takeIf { kind.times == null && kind.mana.symbols.isNotEmpty() }
        is ManaKind.Choose -> {
            val amount = (kind.amount as? Value.Fixed)?.value
            val colors = kind.colors?.map { (it as? ColorRef.Literal)?.color ?: return null }
            when {
                amount == null || amount < 1 -> null
                colors == null && amount == 1 -> "one mana of any color"
                colors == null -> "${words(amount)} mana " + if (kind.combination) "in any combination of colors" else "of any one color"
                kind.combination -> "${words(amount)} mana in any combination of " + colors.joinToString(" and/or ") { "{${it.symbol}}" }
                else -> colors.joinToString(if (colors.size > 2) ", " else " or ") { "{${it.symbol}}".repeat(amount) }
                    .let { if (colors.size > 2) it.replaceAfterLast(", ", "or " + it.substringAfterLast(", ")) else it }
            }
        }
        else -> null
    }

    private fun words(n: Int): String = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
        .getOrElse(n) { n.toString() }
}
