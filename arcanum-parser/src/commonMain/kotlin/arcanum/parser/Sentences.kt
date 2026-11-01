package arcanum.parser

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone

/**
 * Rules-text lines that aren't keyword lines: activated abilities ("[cost]: [effect]") and the static wordings learned
 * so far. Each wording is one rule; a line no rule reads stays [Ability.Unparsed]. [self] holds the names the card
 * calls itself ("this land", its own name).
 */
internal class Sentences(private val self: Set<String>) {
    fun line(line: String): Ability? = static(line) ?: activated(line)

    /** "As this land enters, you may pay 2 life. If you don't, it enters tapped." (CR 614.12, 118.12a) */
    private fun static(line: String): Ability? {
        AS_ENTERS_PAY_LIFE.matchEntire(line)?.let { m ->
            if (m.groupValues[1] !in self) return null
            return Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(Step.Unless(Players.You, Cost(listOf(CostPart.PayLife(Value.Fixed(m.groupValues[2].toInt())))),
                    listOf(Step.Modify(Modification.EntersTapped)))))))))
        }
        return null
    }

    /** "[cost]: [effect]" (CR 602.1). */
    private fun activated(line: String): Ability? {
        val colon = line.indexOf(": ").takeIf { it > 0 } ?: return null
        val cost = cost(line.substring(0, colon)) ?: return null
        val instructions = instructions(line.substring(colon + 2)) ?: return null
        return Ability.Activated(cost, instructions)
    }

    /** Cost parts separated by ", " (CR 602.1a). */
    fun cost(text: String): Cost? {
        val parts = text.split(", ").map { costPart(it) ?: return null }
        return Cost(parts.flatten())
    }

    private fun costPart(text: String): List<CostPart>? {
        if (text == "{T}") return listOf(CostPart.TapSelf)
        ManaCost.parse(text)?.let { return listOf(CostPart.Mana(it)) }
        PAY_LIFE.matchEntire(text)?.let { return listOf(CostPart.PayLife(Value.Fixed(it.groupValues[1].toInt()))) }
        SACRIFICE.matchEntire(text)?.let { m ->
            val what = m.groupValues[1]
            return listOf(CostPart.Sacrifice(if (what in self) CostObjects.Self else CostObjects.Choose(Filters.noun(what.removeArticle()) ?: return null)))
        }
        return null
    }

    /** The effect sentences of a spell or ability, in order. */
    fun instructions(text: String): Instructions? {
        val context = Context()
        val steps = sentences(text).map { effect(it, context) ?: return null }.flatten()
        return Instructions(targets = context.targets, steps = steps)
    }

    private class Context {
        val targets = mutableListOf<TargetSlot>()
        fun target(noun: String, filter: ObjectFilter): Objects {
            targets += TargetSlot(noun, TargetKind.Objects(filter))
            return Objects.Target(noun)
        }
    }

    private fun effect(sentence: String, context: Context): List<Step>? {
        ADD_MANA.matchEntire(sentence)?.let { m -> return listOf(Step.Do(Effect.AddMana(ManaProduction(mana(m.groupValues[1]) ?: return null)))) }
        PLAYERS_GAIN_LIFE.matchEntire(sentence)?.let { m ->
            val players = Filters.players(m.groupValues[1]) ?: return null
            return listOf(Step.Do(Effect.GainLife(players, Value.Fixed(m.groupValues[2].toInt()))))
        }
        DESTROY_TARGET.matchEntire(sentence)?.let { m ->
            val noun = m.groupValues[1]
            val filter = Filters.noun(noun) ?: return null
            return listOf(Step.Do(Effect.Destroy(context.target(Filters.head(noun), filter))))
        }
        SEARCH_PUT_SHUFFLE.matchEntire(sentence)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle().removeSuffix(" card")) ?: return null
            return listOf(
                Step.Do(Effect.Search(Players.You, filter, bind = "found")),
                Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield))),
                Step.Do(Effect.Shuffle(Players.You)))
        }
        return null
    }

    /** "{C}", "{B}{B}", "{R} or {G}" (CR 106.1). */
    private fun mana(text: String): ManaKind? {
        ManaCost.parse(text)?.let { return ManaKind.Fixed(it) }
        val colors = text.split(" or ").map { symbol -> ManaCost.parse(symbol)?.symbols?.singleOrNull()
            ?.let { (it as? arcanum.api.ManaSymbol.Colored)?.color } ?: return null }
        return ManaKind.Choose(Value.Fixed(1), colors.map { ColorRef.Literal(it) })
    }

    private fun sentences(text: String): List<String> = SENTENCE_END.split(text.trim()).map { it.trim() }.filter { it.isNotEmpty() }
        .map { if (it.endsWith(".")) it else "$it." }

    private companion object {
        val SENTENCE_END = Regex("""(?<=\.)\s+""")
        val AS_ENTERS_PAY_LIFE = Regex("""As (.+) enters, you may pay (\d+) life\. If you don't, it enters tapped\.""")
        val PAY_LIFE = Regex("""Pay (\d+) life""")
        val SACRIFICE = Regex("""Sacrifice (.+)""")
        val ADD_MANA = Regex("""Add (.+)\.""")
        val PLAYERS_GAIN_LIFE = Regex("""(.+) gains? (\d+) life\.""")
        val DESTROY_TARGET = Regex("""Destroy target (.+)\.""")
        val SEARCH_PUT_SHUFFLE = Regex("""Search your library for (.+), put it onto the battlefield, then shuffle\.""")
    }
}

private fun String.removeArticle() = removePrefix("a ").removePrefix("an ")

/** Noun phrases naming objects and players ("nonbasic land", "a Mountain or Plains card", "each opponent"). */
internal object Filters {
    /** "land", "nonbasic land", "Mountain or Plains", "basic land", "creature". */
    fun noun(phrase: String): ObjectFilter? {
        val alternatives = phrase.split(" or ")
        if (alternatives.size > 1) return ObjectFilter.Or(alternatives.map { noun(it.removeArticle()) ?: return null })
        val words = phrase.split(' ')
        val parts = words.map { word(it) ?: return null }
        return parts.singleOrNull() ?: ObjectFilter.And(parts)
    }

    /** The noun a target slot is named after: the last word ("nonbasic land" → "land"). */
    fun head(phrase: String): String = phrase.split(' ').last()

    private fun word(word: String): ObjectFilter? {
        if (word.startsWith("non")) return word(word.removePrefix("non"))?.let { ObjectFilter.Not(it) }
        CARD_TYPES[word]?.let { return ObjectFilter.Type(it) }
        SUPERTYPES[word]?.let { return ObjectFilter.Super(it) }
        if (word.first().isUpperCase()) return ObjectFilter.Sub(Subtype(word))
        return null
    }

    fun players(phrase: String): Players? = when (phrase) {
        "Each opponent" -> Players.Each(PlayerFilter.Opponent)
        "You", "you" -> Players.You
        else -> null
    }

    private val CARD_TYPES = mapOf("artifact" to CardType.Artifact, "creature" to CardType.Creature, "enchantment" to CardType.Enchantment,
        "instant" to CardType.Instant, "land" to CardType.Land, "planeswalker" to CardType.Planeswalker, "sorcery" to CardType.Sorcery,
        "battle" to CardType.Battle)
    private val SUPERTYPES = mapOf("basic" to Supertype.Basic, "legendary" to Supertype.Legendary, "snow" to Supertype.Snow)
}
