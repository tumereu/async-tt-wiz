package arcanum.engine.view

import arcanum.api.CardType
import arcanum.api.CardTypeRef
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Status
import arcanum.api.SubtypeRef
import arcanum.api.Value
import arcanum.engine.state.EffectId
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import kotlinx.serialization.Serializable

/**
 * Whether it is day or night (CR 731, [arcanum.engine.Engine.dayOrNight]).
 */
@Serializable
sealed interface DayOrNight {
    @Serializable @kotlinx.serialization.SerialName("day") data object Day : DayOrNight
    @Serializable @kotlinx.serialization.SerialName("night") data object Night : DayOrNight
}

/**
 * A rule a continuous effect puts on an object beyond its characteristics (CR 113.12: "can't block" from an Aura, an effect
 * with its own text), as [arcanum.engine.Engine.restrictions] lists it: [text] in plain words without a full stop ("Can't
 * block", "Can't be blocked by creatures with flying"), [mechanic] what it is, [madeBy] the object that made it (null: the
 * rules themselves, a suspected creature's "can't block"), and for an effect object its [effect] and [duration]; a static
 * ability has neither: it applies while it functions (CR 611.3a).
 */
@Serializable
data class AppliedRestriction(
    val text: String,
    val mechanic: Mechanic,
    val madeBy: ObjectId?,
    val effect: EffectId?,
    val duration: Duration?,
    val timestamp: Long,
)

/** [arcanum.engine.Engine.restrictions]: read from a [View]'s [ObjectView.mechanics], with the words for each kind. */
internal object Restrictions {
    fun of(state: GameState, view: View, id: ObjectId): List<AppliedRestriction> {
        val o = view[id] ?: return emptyList()
        return o.mechanics.mapNotNull { applied ->
            // A keyword's meaning (the keyword is what a host lists) and the object's own printed text aren't effects on it
            val origin = applied.origin
            if (origin is AbilityOrigin.Expansion) return@mapNotNull null
            if (applied.effect == null && applied.source == id && origin is AbilityOrigin.Printed) return@mapNotNull null
            val text = text(applied.mechanic) ?: return@mapNotNull null
            val effectId = applied.effect ?: (origin as? AbilityOrigin.FromEffect)?.effect
            val effect = effectId?.let { state.effects[it] }
            val madeBy = when {
                effect != null -> effect.source
                origin is AbilityOrigin.FromAbility -> origin.source
                origin == null -> null // the rules themselves (CR 701.60c)
                else -> applied.source
            }
            AppliedRestriction(text, applied.mechanic, madeBy, effectId, effect?.duration, applied.timestamp)
        }.distinctBy { Triple(it.text, it.madeBy, it.effect) }
    }

    /** [mechanic] in plain words; null for what isn't a restriction, a requirement or a change of how it fights or untaps. */
    fun text(mechanic: Mechanic): String? = when (mechanic) {
        // CR 508.1c, 509.1b: restrictions on attacking and blocking
        is Mechanic.CantAttack -> when {
            mechanic.fromDefender -> null
            mechanic.target == null -> "Can't attack"
            else -> "Can't attack some players or permanents"
        }
        is Mechanic.CantBlock -> if (mechanic.attackers == ObjectFilter.Any) "Can't block" else "Can't block ${creatures(mechanic.attackers)}"
        Mechanic.CantBeBlocked -> "Can't be blocked"
        is Mechanic.CanBeBlockedOnlyBy -> "Can be blocked only by ${creatures(mechanic.blockers)}"
        is Mechanic.CantBeBlockedBy -> "Can't be blocked by ${creatures(mechanic.blockers)}"
        is Mechanic.MinimumBlockers -> "Can't be blocked except by ${mechanic.count} or more creatures"
        is Mechanic.MaximumBlockers -> "Can't be blocked by more than ${mechanic.count} ${if (mechanic.count == 1) "creature" else "creatures"}"
        is Mechanic.CanBlockOnly -> "Can block only ${creatures(mechanic.attackers)}"
        Mechanic.CantBeAttacked -> "Can't be attacked"
        Mechanic.CantAttackAlone -> "Can't attack alone"
        Mechanic.CanOnlyAttackAlone -> "Can attack only alone"
        Mechanic.CantBlockAlone -> "Can't block alone"
        Mechanic.CanAttackDespiteDefender -> "Can attack as though it didn't have defender"
        Mechanic.AttacksWithoutTapping -> "Attacking doesn't tap it"
        is Mechanic.CanBlockAdditional -> "Can block ${mechanic.count} more ${if (mechanic.count == 1) "creature" else "creatures"}"
        Mechanic.CanBlockAnyNumber -> "Can block any number of creatures"
        // CR 508.1d, 509.1c: requirements
        is Mechanic.MustAttack -> if (mechanic.target == null) "Attacks each combat if able" else "Attacks a certain player or permanent each combat if able"
        is Mechanic.MustBlock -> "Blocks each combat if able"
        Mechanic.MustBeBlocked -> "Must be blocked if able"
        // CR 510.1: combat damage
        Mechanic.AssignsDamageByToughness -> "Deals combat damage equal to its toughness"
        Mechanic.AssignsDamageAsThoughUnblocked -> "May deal its combat damage as though it weren't blocked"
        Mechanic.AssignsNoCombatDamage -> "Deals no combat damage"
        // CR 502.3: untapping
        Mechanic.DoesntUntap -> "Doesn't untap"
        Mechanic.CantBecomeUntapped -> "Can't become untapped"
        is Mechanic.UntapReplacedByRemovingCounter -> "Untaps only by removing ${counter(mechanic.kind)}"
        Mechanic.UntapsDuringOtherUntapSteps -> "Untaps during each other player's untap step too"
        Mechanic.DamageNotRemovedInCleanup -> "Damage stays on it"
        // CR 101.2: other things that can't happen to it
        Mechanic.CantBeCountered -> "Can't be countered"
        Mechanic.CantBeCopied -> "Can't be copied"
        is Mechanic.CantBeActivated -> if (mechanic.manaAbilities) "Its abilities can't be activated" else "Its abilities can't be activated, but for mana"
        Mechanic.CantBeRegenerated -> "Can't be regenerated"
        Mechanic.CantBeSacrificed -> "Can't be sacrificed"
        Mechanic.CantBeTurnedFaceUp -> "Can't be turned face up"
        is Mechanic.CantBeTargeted -> "Can't be targeted by some spells or abilities"
        else -> null
    }

    private fun counter(kind: CounterKind) = when (kind) {
        is CounterKind.PowerToughness -> "a ${signed(kind.power)}/${signed(kind.toughness)} counter"
        is CounterKind.Named -> "a ${kind.name} counter"
        else -> "a counter"
    }

    private fun signed(n: Int) = if (n < 0) "$n" else "+$n"

    /** The creatures [filter] means, in a few plain words; "some creatures" for a filter too rare to have words here. */
    private fun creatures(filter: ObjectFilter): String = words(filter)?.phrase() ?: "some creatures"

    /** A filter on creatures in parts: the words before "creatures" and after them. */
    private class Words(val before: List<String> = emptyList(), val noun: String = "creatures", val after: List<String> = emptyList()) {
        fun phrase() = (before + noun).joinToString(" ") + if (after.isEmpty()) "" else " " + after.joinToString(" and ")
        operator fun plus(other: Words) = Words(before + other.before, if (other.noun != "creatures") other.noun else noun, after + other.after)
    }

    private fun words(filter: ObjectFilter): Words? = when (filter) {
        ObjectFilter.Any -> Words()
        is ObjectFilter.And -> filter.all.map { words(it) ?: return null }.fold(Words()) { all, part -> all + part }
        is ObjectFilter.Or -> filter.any.map { words(it) ?: return null }.let { parts ->
            val keyword = parts.map { it.after.singleOrNull()?.removePrefix("with ") }
            if (keyword.all { it != null }) Words(after = listOf("with " + keyword.joinToString(" or "))) else null
        }
        is ObjectFilter.Not -> when (val inner = filter.filter) {
            is ObjectFilter.HasKeyword -> Words(after = listOf("without ${inner.keyword.id.replace('-', ' ')}"))
            else -> null
        }
        is ObjectFilter.Type -> when (val ref = filter.cardType) {
            is CardTypeRef.Literal -> when (ref.cardType) {
                CardType.Creature -> Words()
                CardType.Artifact -> Words(before = listOf("artifact"))
                CardType.Enchantment -> Words(before = listOf("enchantment"))
                CardType.Land -> Words(before = listOf("land"))
                else -> null
            }
            else -> null
        }
        is ObjectFilter.Sub -> (filter.subtype as? SubtypeRef.Literal)?.let { Words(noun = it.subtype.name + "s") }
        is ObjectFilter.OfColor -> (filter.color as? ColorRef.Literal)?.let { Words(before = listOf(colorName(it.color))) }
        is ObjectFilter.HasKeyword -> Words(after = listOf("with ${filter.keyword.id.replace('-', ' ')}"))
        is ObjectFilter.HasStatus -> (filter.status == Status.Tapped).takeIf { it }?.let { Words(before = listOf("tapped")) }
        is ObjectFilter.Power -> comparison("power", filter.comparison, filter.value)
        is ObjectFilter.Toughness -> comparison("toughness", filter.comparison, filter.value)
        else -> null
    }

    /** "with power 2 or less"; compared with the restricted creature's own power: "with greater power". */
    private fun comparison(what: String, comparison: Comparison, value: Value): Words? {
        val fixed = (value as? Value.Fixed)?.value
        val text = when {
            fixed != null -> when (comparison) {
                Comparison.Equal -> "$what $fixed"
                Comparison.AtMost -> "$what $fixed or less"
                Comparison.AtLeast -> "$what $fixed or greater"
                Comparison.Greater -> "$what greater than $fixed"
                Comparison.Less -> "$what less than $fixed"
                Comparison.NotEqual -> return null
            }
            value is Value.Of -> when (comparison) {
                Comparison.Greater -> "greater $what"
                Comparison.AtLeast -> "equal or greater $what"
                Comparison.Less -> "less $what"
                Comparison.AtMost -> "equal or less $what"
                else -> return null
            }
            else -> return null
        }
        return Words(after = listOf("with $text"))
    }

    private fun colorName(color: Color) = when (color.symbol) {
        'W' -> "white"
        'U' -> "blue"
        'B' -> "black"
        'R' -> "red"
        else -> "green"
    }
}
