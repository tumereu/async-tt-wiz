package arcanum.parser

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.SharedQuality
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone

/** Number words ("a", "two", "seven") and generic mana amounts ("{1}"). */
internal object Numbers {
    fun count(word: String): Int? = WORDS[word.lowercase()] ?: word.toIntOrNull()
    fun mana(symbol: String): Int? = Regex("""\{(\d+)\}""").matchEntire(symbol)?.groupValues?.get(1)?.toInt()

    private val WORDS = mapOf("a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10)
}

/** Noun phrases naming objects and players ("nonbasic land", "a Mountain or Plains card", "creature you control"). */
internal object Filters {
    /**
     * "land", "nonbasic land", "Mountain or Plains", "basic land", "artifact, enchantment, or nonbasic land an opponent
     * controls", "land card with a basic land type", "legendary creature you control".
     */
    fun noun(phrase: String): ObjectFilter? {
        ON_THE_BATTLEFIELD.matchEntire(phrase)?.let { m -> return noun(m.groupValues[1]) }
        WITH_MANA_VALUE.matchEntire(phrase)?.let { m ->
            return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.ManaValue(arcanum.api.Comparison.Equal, Value.Fixed(m.groupValues[2].toInt())))
        }
        WITH_POWER_OR_TOUGHNESS_AT_MOST.matchEntire(phrase)?.let { m ->
            val n = Value.Fixed(m.groupValues[2].toInt())
            return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.Or(listOf(
                ObjectFilter.Power(arcanum.api.Comparison.AtMost, n), ObjectFilter.Toughness(arcanum.api.Comparison.AtMost, n))))
        }
        CONTROLLED.matchEntire(phrase)?.let { m ->
            val who = when (m.groupValues[2]) { "you control" -> PlayerFilter.You; "an opponent controls" -> PlayerFilter.Opponent; else -> return null }
            return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.ControlledBy(who))
        }
        WITH_BASIC_LAND_TYPE.matchEntire(phrase)?.let { m ->
            return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.Or(BASIC_LAND_TYPES.map { ObjectFilter.Sub(Subtype(it)) }))
        }
        NAMED.matchEntire(phrase)?.let { m ->
            val zone = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { if (it == " in each graveyard") ObjectFilter.InZone(Zone.Graveyard) else return null }
            return listOfNotNull(ObjectFilter.Named(m.groupValues[1]), zone).let { it.singleOrNull() ?: ObjectFilter.And(it) }
        }
        // "artifact, enchantment, or nonbasic land": alternatives. "nonartifact, nonland card": all of them.
        if (phrase.contains(" or ")) {
            val list = phrase.split(Regex(""",? or |, """)).filter { it.isNotEmpty() }
            return ObjectFilter.Or(list.map { noun(it.removeArticle()) ?: return null })
        }
        if (phrase.contains(", ")) {
            val modifiers = phrase.split(", ")
            return ObjectFilter.And(modifiers.flatMap { part -> (noun(part) ?: return null).let { if (it is ObjectFilter.And) it.all else listOf(it) } })
        }
        val trimmed = phrase.removeSuffix(" cards").let { if (it.contains(' ')) it.removeSuffix(" card") else it }
        val words = trimmed.split(' ').map { singular(it) }
        val parts = words.map { word(it) ?: return null }
        return parts.singleOrNull() ?: ObjectFilter.And(parts)
    }

    /** "creature you control" → every such object; "card named [this card] in each graveyard". */
    fun objects(phrase: String, self: Set<String> = emptySet()): Objects? = noun(phrase)?.let { Objects.Each(it) }

    /** The name of a target slot: its noun ("nonbasic land" → "land"), "permanent" for a list of permanent types. */
    fun slotName(phrase: String): String {
        val core = (CONTROLLED.matchEntire(phrase)?.groupValues?.get(1) ?: phrase).substringBefore(" with ")
        return if (core.contains(" or ") || core.contains(", ")) "permanent" else core.split(' ').last()
    }

    private fun and(a: ObjectFilter, b: ObjectFilter) =
        if (a is ObjectFilter.And) ObjectFilter.And(a.all + b) else ObjectFilter.And(listOf(a, b))

    private fun singular(word: String): String = when {
        word.endsWith("s") && word.dropLast(1).lowercase() in SINGULAR_NOUNS -> word.dropLast(1)
        word.first().isUpperCase() -> IRREGULAR_PLURALS[word] ?: word
        else -> word
    }

    private fun word(word: String): ObjectFilter? {
        if (word.startsWith("non")) return word(word.removePrefix("non"))?.let { ObjectFilter.Not(it) }
        when (word) {
            "spell" -> return ObjectFilter.Category(arcanum.api.ObjectCategory.Spell)
            "token" -> return ObjectFilter.Category(arcanum.api.ObjectCategory.Token)
            "permanent" -> return ObjectFilter.Category(arcanum.api.ObjectCategory.Permanent)
            "card" -> return ObjectFilter.Category(arcanum.api.ObjectCategory.Card)
        }
        COLORS[word]?.let { return ObjectFilter.OfColor(it) }
        CARD_TYPES[word]?.let { return ObjectFilter.Type(it) }
        SUPERTYPES[word]?.let { return ObjectFilter.Super(it) }
        if (word.first().isUpperCase()) return ObjectFilter.Sub(Subtype(word))
        return null
    }

    fun players(phrase: String): Players? = when (phrase) {
        "Each opponent", "each opponent" -> Players.Each(PlayerFilter.Opponent)
        "You", "you" -> Players.You
        else -> null
    }

    private val NAMED = Regex("""card named (.+?)( in each graveyard)?""")
    private val WITH_MANA_VALUE = Regex("""(.+) with mana value (\d+)""")
    private val WITH_POWER_OR_TOUGHNESS_AT_MOST = Regex("""(.+) with power or toughness (\d+) or less""")
    private val ON_THE_BATTLEFIELD = Regex("""(.+) on the battlefield""")
    private val CONTROLLED = Regex("""(.+) (you control|an opponent controls)""")
    private val WITH_BASIC_LAND_TYPE = Regex("""(.+) with a basic land type""")
    private val COLORS = mapOf("white" to Color.White, "blue" to Color.Blue, "black" to Color.Black, "red" to Color.Red, "green" to Color.Green)
    val BASIC_LAND_TYPES = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")
    /** Plural creature types as printed ("Elves you control"); regular plurals are only read where the text needs them. */
    private val IRREGULAR_PLURALS = mapOf("Elves" to "Elf", "Dwarves" to "Dwarf", "Wolves" to "Wolf", "Goblins" to "Goblin",
        "Zombies" to "Zombie", "Humans" to "Human", "Spirits" to "Spirit", "Merfolk" to "Merfolk", "Soldiers" to "Soldier")
    private val SINGULAR_NOUNS = setOf("land", "creature", "artifact", "enchantment", "planeswalker", "instant", "sorcery", "card",
        "permanent", "spell", "token")
    val CARD_TYPES = mapOf("artifact" to CardType.Artifact, "creature" to CardType.Creature, "enchantment" to CardType.Enchantment,
        "instant" to CardType.Instant, "land" to CardType.Land, "planeswalker" to CardType.Planeswalker, "sorcery" to CardType.Sorcery,
        "battle" to CardType.Battle)
    private val SUPERTYPES = mapOf("basic" to Supertype.Basic, "legendary" to Supertype.Legendary, "snow" to Supertype.Snow)
}

/** Trigger events ("this land or another land you control enters", "the beginning of your upkeep"). */
internal object Events {
    fun event(text: String, self: Set<String>): EventPattern? {
        BEGINNING_OF_YOUR.matchEntire(text)?.let { m ->
            val step = STEPS[m.groupValues[1]] ?: return null
            return EventPattern.StepBegins(step, PlayerFilter.You)
        }
        ENTERS.matchEntire(text)?.let { m -> return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, to = Zone.Battlefield) }
        PUT_INTO_GRAVEYARD_FROM_BATTLEFIELD.matchEntire(text)?.let { m ->
            return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, from = Zone.Battlefield, to = Zone.Graveyard)
        }
        return null
    }

    /** "this land", "this land or another land you control". */
    private fun subject(text: String, self: Set<String>): ObjectFilter? {
        if (text in self || text.replaceFirstChar { it.lowercase() } in self) return ObjectFilter.Is(Objects.Self)
        SELF_OR_ANOTHER.matchEntire(text)?.let { m ->
            if (m.groupValues[1] !in self) return null
            val other = Filters.noun(m.groupValues[2]) ?: return null
            return ObjectFilter.Or(listOf(ObjectFilter.Is(Objects.Self),
                ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), other))))
        }
        return null
    }

    private val ENTERS = Regex("""(.+) enters""")
    private val BEGINNING_OF_YOUR = Regex("""the beginning of your (upkeep|draw step|end step)""")
    private val STEPS = mapOf("upkeep" to arcanum.api.TurnStep.Upkeep, "draw step" to arcanum.api.TurnStep.Draw, "end step" to arcanum.api.TurnStep.End)
    private val PUT_INTO_GRAVEYARD_FROM_BATTLEFIELD = Regex("""(.+) is put into a graveyard from the battlefield""")
    private val SELF_OR_ANOTHER = Regex("""(.+) or another (.+)""")
}

/** Conditions ("if you control seven or more lands with different names"). */
internal object Conditions {
    fun condition(text: String, self: Set<String> = emptySet()): Condition? {
        SELF_IS_TAPPED.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(arcanum.api.Status.Tapped))
        }
        CARDS_IN_YOUR_GRAVEYARD.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))), atLeast = n)
        }
        CONTROL_N_OR_MORE.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val filter = Filters.noun(m.groupValues[2] + " you control") ?: return null
            return Condition.Exists(filter, atLeast = n)
        }
        CONTROL_N_WITH_DIFFERENT_NAMES.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val filter = Filters.noun(m.groupValues[2] + " you control") ?: return null
            return Condition.Compare(Value.CountDistinct(Objects.Each(filter), SharedQuality.Name), Comparison.AtLeast, Value.Fixed(n))
        }
        return null
    }

    private val SELF_IS_TAPPED = Regex("""(.+) is tapped""")
    private val CARDS_IN_YOUR_GRAVEYARD = Regex("""there are (\w+) or more cards in your graveyard""")
    private val CONTROL_N_OR_MORE = Regex("""you control (\w+) or more (\w+)""")
    private val CONTROL_N_WITH_DIFFERENT_NAMES = Regex("""you control (\w+) or more (.+) with different names""")
}

/** Token descriptions ("a 2/2 black Zombie creature token", CR 111.4). */
internal object Tokens {
    fun defined(text: String): TokenSpec? {
        val m = TOKEN.matchEntire(text) ?: return null
        val colors = m.groupValues[2].split(" and ").map { COLORS[it] ?: return null }
        val types = mutableListOf<CardType>()
        val subtypes = mutableListOf<Subtype>()
        for (word in m.groupValues[3].split(' ')) {
            Filters.CARD_TYPES[word]?.let { types += it } ?: subtypes.add(Subtype(word))
        }
        return TokenSpec.Defined(FaceDefinition(
            name = subtypes.joinToString(" ") { it.name } + " Token",
            colorIndicator = colors,
            types = types, subtypes = subtypes,
            power = PrintedNumber.Fixed(m.groupValues[1].substringBefore('/').toInt()),
            toughness = PrintedNumber.Fixed(m.groupValues[1].substringAfter('/').toInt()),
        ))
    }

    private val TOKEN = Regex("""an? (\d+/\d+) (\w+(?: and \w+)?) ([A-Z][A-Za-z ]+? creature) token""")
    private val COLORS = mapOf("white" to Color.White, "blue" to Color.Blue, "black" to Color.Black, "red" to Color.Red, "green" to Color.Green)
}

/** Counter kinds as printed ("-1/-1", "+1/+1", "time", "charge"; CR 122). */
internal object Counters {
    fun kind(text: String): arcanum.api.CounterKind? {
        PT.matchEntire(text)?.let { m -> return arcanum.api.CounterKind.PowerToughness(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        return when (text) {
            "time" -> arcanum.api.CounterKind.Time
            "loyalty" -> arcanum.api.CounterKind.Loyalty
            "level" -> arcanum.api.CounterKind.Level
            "stun" -> arcanum.api.CounterKind.Stun
            "shield" -> arcanum.api.CounterKind.Shield
            else -> if (text.all { it.isLetter() }) arcanum.api.CounterKind.Named(text) else null
        }
    }

    private val PT = Regex("""([+-]\d+)/([+-]\d+)""")
}
