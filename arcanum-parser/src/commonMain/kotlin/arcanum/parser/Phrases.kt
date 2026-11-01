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
        // chunk 31: "instant or sorcery card in your graveyard", "card from a graveyard"
        IN_YOUR_GRAVEYARD.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
        }
        FROM_A_GRAVEYARD.matchEntire(phrase)?.let { m -> return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard)) }
        // chunk 32: "creature card with power 2 or less", "… with toughness 2 or less"
        WITH_POWER_OR_TOUGHNESS.matchEntire(phrase)?.let { m ->
            val n = Value.Fixed(m.groupValues[3].toInt())
            val test = if (m.groupValues[2] == "power") ObjectFilter.Power(Comparison.AtMost, n) else ObjectFilter.Toughness(Comparison.AtMost, n)
            return all(noun(m.groupValues[1]) ?: return null, test)
        }
        WITH_MANA_VALUE.matchEntire(phrase)?.let { m ->
            val value = if (m.groupValues[2] == "X") Value.Announced() else Value.Fixed(m.groupValues[2].toInt())
            val comparison = if (m.groupValues[3].isEmpty()) arcanum.api.Comparison.Equal else arcanum.api.Comparison.AtMost
            return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.ManaValue(comparison, value))
        }
        WITH_POWER_OR_TOUGHNESS_AT_MOST.matchEntire(phrase)?.let { m ->
            val n = Value.Fixed(m.groupValues[2].toInt())
            return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.Or(listOf(
                ObjectFilter.Power(arcanum.api.Comparison.AtMost, n), ObjectFilter.Toughness(arcanum.api.Comparison.AtMost, n))))
        }
        CONTROLLED.matchEntire(phrase)?.let { m ->
            val who = when (m.groupValues[2]) {
                "you control" -> PlayerFilter.You
                "an opponent controls" -> PlayerFilter.Opponent
                "you don't control" -> PlayerFilter.Not(PlayerFilter.You) // chunk 33
                else -> return null
            }
            return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.ControlledBy(who))
        }
        WITH_BASIC_LAND_TYPE.matchEntire(phrase)?.let { m ->
            return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.Or(BASIC_LAND_TYPES.map { ObjectFilter.Sub(Subtype(it)) }))
        }
        NAMED.matchEntire(phrase)?.let { m ->
            val zone = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { if (it == " in each graveyard") ObjectFilter.InZone(Zone.Graveyard) else return null }
            return listOfNotNull(ObjectFilter.Named(m.groupValues[1]), zone).let { it.singleOrNull() ?: all(*it.toTypedArray()) }
        }
        // chunk 32: "instant or sorcery spell": the alternatives share the noun "spell".
        if (phrase.contains(" or ") && phrase.endsWith(" spell"))
            return all(noun(phrase.removeSuffix(" spell")) ?: return null, ObjectFilter.Category(arcanum.api.ObjectCategory.Spell))
        // "artifact, enchantment, or nonbasic land": alternatives. "nonartifact, nonland card": all of them.
        if (phrase.contains(" or ")) {
            val list = phrase.split(Regex(""",? or |, """)).filter { it.isNotEmpty() }
            return ObjectFilter.Or(list.map { noun(it.removeArticle()) ?: return null })
        }
        if (phrase.contains(", ")) {
            val modifiers = phrase.split(", ")
            return all(*modifiers.map { noun(it) ?: return null }.toTypedArray())
        }
        val trimmed = phrase.removeSuffix(" cards").let { if (it.contains(' ')) it.removeSuffix(" card") else it }
        val words = trimmed.split(' ').map { singular(it) }
        val parts = words.map { word(it) ?: return null }
        return parts.singleOrNull() ?: all(*parts.toTypedArray())
    }

    /** "creature you control" → every such object; "card named [this card] in each graveyard". */
    fun objects(phrase: String, self: Set<String> = emptySet()): Objects? = noun(phrase)?.let { Objects.Each(it) }

    /** The name of a target slot: its noun ("nonbasic land" → "land"), "permanent" for a list of permanent types. */
    fun slotName(phrase: String): String {
        val core = (CONTROLLED.matchEntire(phrase)?.groupValues?.get(1) ?: phrase).substringBefore(" with ")
            .substringBefore(" in your graveyard").substringBefore(" from a graveyard") // chunk 31
        // chunk 31: "instant or sorcery card" is a card, "instant or sorcery spell" a spell
        if ((core.contains(" or ") || core.contains(", ")) && core.split(' ').last() in setOf("card", "spell")) return core.split(' ').last()
        return if (core.contains(" or ") || core.contains(", ")) "permanent" else core.split(' ').last()
    }

    private fun and(a: ObjectFilter, b: ObjectFilter) = all(a, b)

    /** All of [filters], nested "and"s flattened into one (one canonical form). */
    fun all(vararg filters: ObjectFilter): ObjectFilter =
        ObjectFilter.And(filters.flatMap { if (it is ObjectFilter.And) it.all else listOf(it) })

    private fun singular(word: String): String = when {
        word.endsWith("s") && word.dropLast(1).lowercase() in SINGULAR_NOUNS -> word.dropLast(1)
        word.first().isUpperCase() && word !in RulesWords.subtypes ->
            IRREGULAR_PLURALS[word] ?: word.removeSuffix("s").takeIf { word.endsWith("s") && it in RulesWords.subtypes } ?: word
        else -> word
    }

    private fun word(word: String): ObjectFilter? {
        if (word.startsWith("non")) return word(word.removePrefix("non"))?.let { ObjectFilter.Not(it) }
        when (word) {
            "spell" -> return ObjectFilter.Category(arcanum.api.ObjectCategory.Spell)
            "token" -> return ObjectFilter.Category(arcanum.api.ObjectCategory.Token)
            "permanent" -> return ObjectFilter.Category(arcanum.api.ObjectCategory.Permanent)
            "card" -> return ObjectFilter.Category(arcanum.api.ObjectCategory.Card)
            "attacking" -> return ObjectFilter.HasStatus(arcanum.api.Status.Attacking) // chunk 32
        }
        COLORS[word]?.let { return ObjectFilter.OfColor(it) }
        CARD_TYPES[word]?.let { return ObjectFilter.Type(it) }
        SUPERTYPES[word]?.let { return ObjectFilter.Super(it) }
        // Only subtypes the rules list (CR 205.3); "X", plurals left over and other words stay unparsed.
        if (word in RulesWords.subtypes) return ObjectFilter.Sub(Subtype(word))
        return null
    }

    fun players(phrase: String): Players? = when (phrase) {
        "Each opponent", "each opponent" -> Players.Each(PlayerFilter.Opponent)
        "You", "you" -> Players.You
        "Each player", "each player" -> Players.Each(PlayerFilter.Any)
        else -> null
    }

    private val NAMED = Regex("""card named (.+?)( in each graveyard)?""")
    private val WITH_MANA_VALUE = Regex("""(.+) with mana value (\d+|X)( or less)?""")
    private val WITH_POWER_OR_TOUGHNESS_AT_MOST = Regex("""(.+) with power or toughness (\d+) or less""")
    private val ON_THE_BATTLEFIELD = Regex("""(.+) on the battlefield""")
    private val CONTROLLED = Regex("""(.+) (you control|an opponent controls|you don't control)""")
    private val WITH_BASIC_LAND_TYPE = Regex("""(.+) with a basic land type""")
    private val COLORS = mapOf("white" to Color.White, "blue" to Color.Blue, "black" to Color.Black, "red" to Color.Red, "green" to Color.Green)
    val BASIC_LAND_TYPES = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")
    /** Plural creature types as printed ("Elves you control"); regular plurals are only read where the text needs them. */
    private val IRREGULAR_PLURALS = mapOf("Elves" to "Elf", "Dwarves" to "Dwarf", "Wolves" to "Wolf", "Goblins" to "Goblin",
        "Zombies" to "Zombie", "Humans" to "Human", "Spirits" to "Spirit", "Merfolk" to "Merfolk", "Soldiers" to "Soldier")
    // chunk 31
    private val IN_YOUR_GRAVEYARD = Regex("""(.+) in your graveyard""")
    private val FROM_A_GRAVEYARD = Regex("""(.+) from a graveyard""")
    // chunk 32
    private val WITH_POWER_OR_TOUGHNESS = Regex("""(.+) with (power|toughness) (\d+) or less""")
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
        // chunk 31: "dies" means "is put into a graveyard from the battlefield" (CR 700.4)
        DIES.matchEntire(text)?.let { m ->
            return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, from = Zone.Battlefield, to = Zone.Graveyard)
        }
        // chunk 33: leaves-the-battlefield (CR 603.6c)
        LEAVES_THE_BATTLEFIELD.matchEntire(text)?.let { m ->
            return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, from = Zone.Battlefield)
        }
        // chunk 32: "this creature enters from your hand"
        ENTERS_FROM_YOUR_HAND.matchEntire(text)?.let { m ->
            return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, from = Zone.Hand, to = Zone.Battlefield)
        }
        return null
    }

    /**
     * chunk 31: one or more trigger events of one ability ("this creature enters or dies"); [oneOrMore] for "one or
     * more artifacts you control enter" (one trigger for a batch).
     */
    fun events(text: String, self: Set<String>): Pair<List<EventPattern>, Boolean>? {
        ENTERS_OR_DIES.matchEntire(text)?.let { m ->
            val subject = subject(m.groupValues[1], self) ?: return null
            return listOf(EventPattern.ZoneChange(subject, to = Zone.Battlefield),
                EventPattern.ZoneChange(subject, from = Zone.Battlefield, to = Zone.Graveyard)) to false
        }
        ONE_OR_MORE_ENTER.matchEntire(text)?.let { m ->
            return listOf(EventPattern.ZoneChange(Filters.noun(m.groupValues[1]) ?: return null, to = Zone.Battlefield)) to true
        }
        return event(text, self)?.let { listOf(it) to false }
    }

    /** "this land", "this land or another land you control". */
    private fun subject(text: String, self: Set<String>): ObjectFilter? {
        if (text in self || text.replaceFirstChar { it.lowercase() } in self) return ObjectFilter.Is(Objects.Self)
        SELF_OR_ANOTHER.matchEntire(text)?.let { m ->
            if (m.groupValues[1] !in self) return null
            val other = Filters.noun(m.groupValues[2]) ?: return null
            return ObjectFilter.Or(listOf(ObjectFilter.Is(Objects.Self),
                Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), other)))
        }
        // chunk 30: "another creature or artifact you control"
        ANOTHER.matchEntire(text)?.let { m ->
            return Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), Filters.noun(m.groupValues[1]) ?: return null)
        }
        return null
    }

    private val ENTERS = Regex("""(.+) enters""")
    private val BEGINNING_OF_YOUR = Regex("""the beginning of your (upkeep|draw step|end step)""")
    private val STEPS = mapOf("upkeep" to arcanum.api.TurnStep.Upkeep, "draw step" to arcanum.api.TurnStep.Draw, "end step" to arcanum.api.TurnStep.End)
    private val PUT_INTO_GRAVEYARD_FROM_BATTLEFIELD = Regex("""(.+) is put into a graveyard from the battlefield""")
    private val SELF_OR_ANOTHER = Regex("""(.+) or another (.+)""")
    // chunk 30
    private val ANOTHER = Regex("""another (.+)""")
    // chunk 31
    private val DIES = Regex("""(.+) dies""")
    private val ENTERS_OR_DIES = Regex("""(.+) enters or dies""")
    private val ONE_OR_MORE_ENTER = Regex("""one or more (.+) enter""")
    // chunk 32
    private val ENTERS_FROM_YOUR_HAND = Regex("""(.+) enters from your hand""")
    // chunk 33
    private val LEAVES_THE_BATTLEFIELD = Regex("""(.+) leaves the battlefield""")
}

/** Conditions ("if you control seven or more lands with different names"). */
internal object Conditions {
    fun condition(text: String, self: Set<String> = emptySet()): Condition? {
        CONTROL_A.matchEntire(text)?.let { m -> return Condition.Exists(Filters.noun(m.groupValues[1] + " you control") ?: return null) }
        // chunk 30: "you control at least three other snow permanents"
        CONTROL_AT_LEAST.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val filter = Filters.noun(m.groupValues[3] + " you control") ?: return null
            return Condition.Exists(if (m.groupValues[2].isEmpty()) filter else Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter), atLeast = n)
        }
        // chunk 30: "this card is in your graveyard"
        SELF_IN_YOUR_GRAVEYARD.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.InZone(Zone.Graveyard))
        }
        // chunk 32: "{R}{R} was spent to cast it" (at least that much mana of that colour)
        MANA_WAS_SPENT.matchEntire(text)?.let { m ->
            val symbols = arcanum.api.ManaCost.parse(m.groupValues[1])?.symbols ?: return null
            val colors = symbols.map { (it as? arcanum.api.ManaSymbol.Colored)?.color ?: return null }.distinct()
            return Condition.Compare(Value.ManaSpent(color = colors.singleOrNull() ?: return null), Comparison.AtLeast, Value.Fixed(symbols.size))
        }
        // chunk 32: "you've discarded a card this turn"
        if (text == "you've discarded a card this turn") return Condition.HappenedThisTurn(EventPattern.Discard(players = PlayerFilter.You))
        // chunk 33: "it was kicked", "this creature was kicked" (CR 702.33d)
        WAS_KICKED.matchEntire(text)?.takeIf { it.groupValues[1] == "it" || it.groupValues[1] in self }?.let {
            return Condition.Paid(arcanum.api.Keyword.Kicker.Kind)
        }
        // chunk 32: "it's attacking" (about this permanent)
        if (text == "it's attacking") return Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(arcanum.api.Status.Attacking))
        CARD_TYPES_IN_GRAVEYARD.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return Condition.Compare(Value.CountDistinct(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You)))), SharedQuality.CardType), Comparison.AtLeast, Value.Fixed(n))
        }
        if (text == "a permanent left the battlefield under your control this turn")
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(ObjectFilter.And(listOf(
                ObjectFilter.Category(arcanum.api.ObjectCategory.Permanent), ObjectFilter.ControlledBy(PlayerFilter.You))), from = Zone.Battlefield))
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

    private val CONTROL_A = Regex("""you control an? (\w+)""")
    // chunk 30
    private val CONTROL_AT_LEAST = Regex("""you control at least (\w+) (other )?(.+)""")
    private val SELF_IN_YOUR_GRAVEYARD = Regex("""(.+) is in your graveyard""")
    // chunk 32
    private val MANA_WAS_SPENT = Regex("""((?:\{[WUBRG]\})+) was spent to cast it""")
    // chunk 33
    private val WAS_KICKED = Regex("""(.+) was kicked""")
    private val CARD_TYPES_IN_GRAVEYARD = Regex("""there are (\w+) or more card types among cards in your graveyard""")
    private val SELF_IS_TAPPED = Regex("""(.+) is tapped""")
    private val CARDS_IN_YOUR_GRAVEYARD = Regex("""there are (\w+) or more cards in your graveyard""")
    private val CONTROL_N_OR_MORE = Regex("""you control (\w+) or more (\w+)""")
    private val CONTROL_N_WITH_DIFFERENT_NAMES = Regex("""you control (\w+) or more (.+) with different names""")
}

/** Token descriptions ("a 2/2 black Zombie creature token", CR 111.4). */
internal object Tokens {
    /** "a Treasure token", "two 1/1 white Spirit creature tokens with flying": how many and what (CR 111.10, 111.4). */
    fun spec(text: String): Pair<Int, TokenSpec>? {
        PREDEFINED.matchEntire(text)?.let { m ->
            if (m.groupValues[2] !in arcanum.api.PredefinedTokens.names) return null
            return (Numbers.count(m.groupValues[1]) ?: return null) to TokenSpec.Predefined(m.groupValues[2])
        }
        val m = COUNTED.matchEntire(text) ?: return null
        val count = Numbers.count(m.groupValues[1]) ?: return null
        val token = defined("a " + m.groupValues[2] + " token") ?: return null
        val keywords = m.groupValues[3].takeIf { it.isNotEmpty() }?.split(Regex(""",? and |, """))?.map { OracleParser.keyword(it) ?: return null }.orEmpty()
        return count to token.copy(face = token.face.copy(abilities = keywords))
    }

    fun defined(text: String): TokenSpec.Defined? {
        val m = TOKEN.matchEntire(text) ?: return null
        val colors = if (m.groupValues[2] == "colorless") emptyList() else m.groupValues[2].split(" and ").map { COLORS[it] ?: return null } // chunk 32
        val types = mutableListOf<CardType>()
        val subtypes = mutableListOf<Subtype>()
        for (word in m.groupValues[3].split(' ')) {
            Filters.CARD_TYPES[word]?.let { types += it } ?: subtypes.add(Subtype(word))
        }
        // chunk 33: "an X/X … token": X is worked out when it is created (the ability's "where X is …")
        val x = m.groupValues[1] == "X/X"
        return TokenSpec.Defined(FaceDefinition(
            name = subtypes.joinToString(" ") { it.name } + " Token",
            colorIndicator = colors,
            types = types, subtypes = subtypes,
            power = if (x) PrintedNumber.X else PrintedNumber.Fixed(m.groupValues[1].substringBefore('/').toInt()),
            toughness = if (x) PrintedNumber.X else PrintedNumber.Fixed(m.groupValues[1].substringAfter('/').toInt()),
        ), power = if (x) Value.Announced() else null, toughness = if (x) Value.Announced() else null)
    }

    private val PREDEFINED = Regex("""(a|an|two|three) ([A-Z][a-z]+) tokens?""")
    private val COUNTED = Regex("""(a|an|one|two|three|four|five) ((?:\d+/\d+|X/X) .+? creature) tokens?(?: with (.+))?""")
    private val TOKEN = Regex("""an? (\d+/\d+|X/X) (\w+(?: and \w+)?) ([A-Z][A-Za-z ]+? creature) token""")
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

/** chunk 30: numbers the text defines ("where X is the number of creatures you control", CR 107.3c). */
internal object Values {
    /** [exiled]: what "the exiled card" refers to on this card (linked abilities, CR 607.2a). */
    fun value(text: String, exiled: Objects? = null): Value? {
        DIED_UNDER_YOUR_CONTROL.matchEntire(text)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return Value.EventsThisTurn(EventPattern.ZoneChange(Filters.all(filter, ObjectFilter.ControlledBy(PlayerFilter.You)),
                from = Zone.Battlefield, to = Zone.Graveyard), arcanum.api.Measure.Objects)
        }
        CARDS_IN_YOUR_ZONE.matchEntire(text)?.let { m ->
            val zone = when (m.groupValues[1]) { "library" -> Zone.Library; "hand" -> Zone.Hand; else -> Zone.Graveyard }
            return Value.Count(Objects.Each(Filters.all(ObjectFilter.InZone(zone), ObjectFilter.OwnedBy(PlayerFilter.You))))
        }
        DEVOTION.matchEntire(text)?.let { m -> return Value.Devotion(listOf(COLORS[m.groupValues[1]] ?: return null)) }
        // One exiled card, but card memory may hold several: read as their total (DefinitionChecks).
        if (text == "the mana value of the exiled card")
            return Value.Of(arcanum.api.NumericProperty.ManaValue, exiled ?: return null, Combine.Total)
        NUMBER_OF.matchEntire(text)?.let { m -> return Value.Count(Filters.objects(m.groupValues[1]) ?: return null) }
        return null
    }

    private val DIED_UNDER_YOUR_CONTROL = Regex("""the number of (.+) that died under your control this turn""")
    private val CARDS_IN_YOUR_ZONE = Regex("""the number of cards in your (library|hand|graveyard)""")
    private val DEVOTION = Regex("""your devotion to (white|blue|black|red|green)""")
    private val NUMBER_OF = Regex("""the number of (.+)""")
    private val COLORS = mapOf("white" to Color.White, "blue" to Color.Blue, "black" to Color.Black, "red" to Color.Red, "green" to Color.Green)
}
