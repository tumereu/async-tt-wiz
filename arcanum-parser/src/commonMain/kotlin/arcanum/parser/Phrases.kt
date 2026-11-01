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
        // chunk 14: "instant or sorcery spell": the head noun covers both alternatives.
        OR_SPELL.matchEntire(phrase)?.let { m -> return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.Category(arcanum.api.ObjectCategory.Spell)) }
        // chunk 14: "permanent card" (CR 110.4a): an artifact, battle, creature, enchantment, land, or planeswalker card.
        if (phrase == "permanent card" || phrase == "permanent cards") return ObjectFilter.Or(PERMANENT_TYPES.map { ObjectFilter.Type(it) })
        ON_THE_BATTLEFIELD.matchEntire(phrase)?.let { m -> return noun(m.groupValues[1]) }
        // chunk 21: "creatures you control with +1/+1 counters on them"
        WITH_COUNTERS.matchEntire(phrase)?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.HasCounter(kind))
        }
        // chunk 20: "artifact and/or enchantment" is either of them
        if (phrase.contains(" and/or ")) return noun(phrase.replace(" and/or ", " or "))
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
            val who = when (m.groupValues[2]) { "you control" -> PlayerFilter.You; "an opponent controls" -> PlayerFilter.Opponent; else -> return null }
            return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.ControlledBy(who))
        }
        WITH_BASIC_LAND_TYPE.matchEntire(phrase)?.let { m ->
            return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.Or(BASIC_LAND_TYPES.map { ObjectFilter.Sub(Subtype(it)) }))
        }
        NAMED.matchEntire(phrase)?.let { m ->
            val zone = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { if (it == " in each graveyard") ObjectFilter.InZone(Zone.Graveyard) else return null }
            return listOfNotNull(ObjectFilter.Named(m.groupValues[1]), zone).let { it.singleOrNull() ?: all(*it.toTypedArray()) }
        }
        // chunk 26: "blue or black creature": colours as alternatives, the noun for all of them
        COLOR_ALTERNATIVES.matchEntire(phrase)?.let { m ->
            val colors = m.groupValues[1].split(Regex(""",? or |, """)).map { ObjectFilter.OfColor(COLORS[it] ?: return null) }
            return all(ObjectFilter.Or(colors), noun(m.groupValues[2]) ?: return null)
        }
        // chunk 20: "1/1 creature": power and toughness as printed in the phrase
        WITH_POWER_TOUGHNESS.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[3]) ?: return null,
                ObjectFilter.Power(arcanum.api.Comparison.Equal, Value.Fixed(m.groupValues[1].toInt())),
                ObjectFilter.Toughness(arcanum.api.Comparison.Equal, Value.Fixed(m.groupValues[2].toInt())))
        }
        // chunk 36: "nonland permanent cards" (CR 110.4a): the kinds of card that can be permanents
        PERMANENT_CARDS.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.Or(PERMANENT_TYPES.map { ObjectFilter.Type(it) }))
        }
        // chunk 36: "creatures with flying or reach": keyword abilities as alternatives
        WITH_KEYWORDS.matchEntire(phrase)?.let { m ->
            val keywords = m.groupValues[2].split(Regex(""",? or |, """)).map { OracleParser.keyword(it) }
            if (keywords.all { it != null }) {
                val has = keywords.map { ObjectFilter.HasKeyword(it!!.kind) }
                return all(noun(m.groupValues[1]) ?: return null, has.singleOrNull() ?: ObjectFilter.Or(has))
            }
        }
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
        // chunk 21: "permanent an opponent controls with mana value 2 or less": drop the "with" part first
        val core = phrase.substringBefore(" with ").let { CONTROLLED.matchEntire(it)?.groupValues?.get(1) ?: it }
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
    // chunk 26
    private val COLOR_ALTERNATIVES = Regex("""((?:white|blue|black|red|green)(?:, (?:white|blue|black|red|green))*,? or (?:white|blue|black|red|green)) (.+)""")
    private val WITH_MANA_VALUE = Regex("""(.+) with mana value (\d+|X)( or less)?""")
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
    // chunk 14
    private val OR_SPELL = Regex("""(\w+ or \w+) spells?""")
    private val PERMANENT_TYPES = listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land, CardType.Planeswalker)
    // chunk 20
    private val WITH_POWER_TOUGHNESS = Regex("""(\d+)/(\d+) (.+)""")
    // chunk 21
    private val WITH_COUNTERS = Regex("""(.+) with (?:an? )?(\S+) counters? on (?:it|them)""")
    // chunk 36
    private val PERMANENT_CARDS = Regex("""(.+) permanent cards?""")
    private val WITH_KEYWORDS = Regex("""(.+?) with ([a-z ]+(?:,? or [a-z ]+)+|[a-z]+)""")
}

/** Trigger events ("this land or another land you control enters", "the beginning of your upkeep"). */
internal object Events {
    /** One or more events of one trigger ("this artifact enters or leaves the battlefield", "attacks or blocks"). */
    // chunk 21
    fun events(text: String, self: Set<String>): List<EventPattern>? {
        ENTERS_OR_LEAVES.matchEntire(text)?.let { m ->
            val subject = subject(m.groupValues[1], self) ?: return null
            val second = if (m.groupValues[2] == "leaves the battlefield") EventPattern.ZoneChange(subject, from = Zone.Battlefield)
                else EventPattern.ZoneChange(subject, from = Zone.Battlefield, to = Zone.Graveyard)
            return listOf(EventPattern.ZoneChange(subject, to = Zone.Battlefield), second)
        }
        ATTACKS_OR_BLOCKS.matchEntire(text)?.let { m ->
            val subject = subject(m.groupValues[1], self) ?: return null
            return listOf(EventPattern.Attack(subject), EventPattern.Block(subject))
        }
        // chunk 36: "Korvold enters or attacks"
        ENTERS_OR_ATTACKS.matchEntire(text)?.let { m ->
            val subject = subject(m.groupValues[1], self) ?: return null
            return listOf(EventPattern.ZoneChange(subject, to = Zone.Battlefield), EventPattern.Attack(subject))
        }
        // chunk 35: "this creature dies or another artifact you control is put into a graveyard from the battlefield"
        DIES_OR_PUT_INTO_GRAVEYARD.matchEntire(text)?.let { m ->
            return listOf(m.groupValues[1], m.groupValues[2]).map {
                EventPattern.ZoneChange(subject(it, self) ?: return null, from = Zone.Battlefield, to = Zone.Graveyard)
            }
        }
        return event(text, self)?.let(::listOf)
    }

    fun event(text: String, self: Set<String>): EventPattern? {
        // chunk 20
        DIES.matchEntire(text)?.let { m ->
            return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, from = Zone.Battlefield, to = Zone.Graveyard)
        }
        BECOMES_UNATTACHED.matchEntire(text)?.let { m ->
            val from = Filters.noun(m.groupValues[2]) ?: return null
            return EventPattern.Unattach(subject(m.groupValues[1], self) ?: return null, from = from)
        }
        // chunk 21
        YOU_DISCARD.matchEntire(text)?.let { m ->
            val cards = if (m.groupValues[1] == "card") ObjectFilter.Any else Filters.noun(m.groupValues[1]) ?: return null
            return EventPattern.Discard(cards, players = PlayerFilter.You)
        }
        BEGINNING_OF_YOUR.matchEntire(text)?.let { m ->
            val step = STEPS[m.groupValues[1]] ?: return null
            return EventPattern.StepBegins(step, PlayerFilter.You)
        }
        ENTERS.matchEntire(text)?.let { m -> return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, to = Zone.Battlefield) }
        PUT_INTO_GRAVEYARD_FROM_BATTLEFIELD.matchEntire(text)?.let { m ->
            return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, from = Zone.Battlefield, to = Zone.Graveyard)
        }
        // chunk 12
        // "you cast a creature spell": the event's object is always a spell, so the filter names only its kind.
        YOU_CAST.matchEntire(text)?.let { m ->
            val kind = m.groupValues[1].takeIf { it.isNotEmpty() }?.let { Filters.noun(it) ?: return null } ?: ObjectFilter.Any
            return EventPattern.Cast(spell = kind, by = PlayerFilter.You)
        }
        // chunk 25: "a creature is put into your graveyard from the battlefield": a card goes to its owner's graveyard
        PUT_INTO_OWNERS_GRAVEYARD.matchEntire(text)?.let { m ->
            val owner = if (m.groupValues[2] == "your") PlayerFilter.You else PlayerFilter.Opponent
            return EventPattern.ZoneChange(Filters.all(subject(m.groupValues[1], self) ?: return null, ObjectFilter.OwnedBy(owner)),
                from = Zone.Battlefield, to = Zone.Graveyard)
        }
        // chunk 26: "one or more creature cards leave your graveyard" (looks back in time, CR 603.10a)
        LEAVE_YOUR_GRAVEYARD.matchEntire(text)?.let { m ->
            return EventPattern.ZoneChange(Filters.all(subject(m.groupValues[1], self) ?: return null, ObjectFilter.OwnedBy(PlayerFilter.You)),
                from = Zone.Graveyard)
        }
        // chunk 26: "a creature you control deals combat damage to a player"
        DEALS_COMBAT_DAMAGE_TO_A_PLAYER.matchEntire(text)?.let { m ->
            return EventPattern.Damage(subject(m.groupValues[1], self) ?: return null, arcanum.api.RecipientFilter(players = PlayerFilter.Any), combat = true)
        }
        // chunk 25: "you play a land" (CR 305.1)
        if (text == "you play a land") return EventPattern.PlayLand(players = PlayerFilter.You)
        // chunk 14: "a player casts an instant or sorcery spell", "a player taps an Island for mana", "an opponent gains life"
        PLAYER_CASTS.matchEntire(text)?.let { m ->
            val kind = m.groupValues[1].takeIf { it.isNotEmpty() }?.let { Filters.noun(it) ?: return null } ?: ObjectFilter.Any
            return EventPattern.Cast(kind)
        }
        PLAYER_TAPS_FOR_MANA.matchEntire(text)?.let { m -> return EventPattern.Tap(Filters.noun(m.groupValues[1].removeArticle()) ?: return null, forMana = true) }
        // chunk 36: "this creature attacks"
        ATTACKS.matchEntire(text)?.let { m -> return EventPattern.Attack(subject(m.groupValues[1], self) ?: return null) }
        // chunk 35: "you sacrifice a permanent": only a permanent's controller can sacrifice it (CR 701.21a), and what is
        // sacrificed is always a permanent, so the filter names only its kind and its controller.
        YOU_SACRIFICE.matchEntire(text)?.let { m ->
            val kind = if (m.groupValues[1] == "permanent") null else Filters.noun(m.groupValues[1]) ?: return null
            val yours = ObjectFilter.ControlledBy(PlayerFilter.You)
            return EventPattern.Sacrifice(if (kind == null) yours else Filters.all(kind, yours))
        }
        if (text == "an opponent gains life") return EventPattern.GainLife(PlayerFilter.Opponent)
        return null
    }

    /** "this land", "this land or another land you control", "equipped creature", "a nontoken creature". */
    private fun subject(text: String, self: Set<String>): ObjectFilter? {
        if (text in self || text.replaceFirstChar { it.lowercase() } in self) return ObjectFilter.Is(Objects.Self)
        // chunk 20
        if (text == "equipped creature" || text == "enchanted creature") return ObjectFilter.Is(Objects.AttachedTo())
        SELF_OR_ANOTHER.matchEntire(text)?.let { m ->
            if (m.groupValues[1] !in self) return null
            val other = Filters.noun(m.groupValues[2]) ?: return null
            return ObjectFilter.Or(listOf(ObjectFilter.Is(Objects.Self),
                Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), other)))
        }
        // chunk 25: "a creature", "a land you control", "one or more creature cards"
        ARTICLE_NOUN.matchEntire(text)?.let { m -> return Filters.noun(m.groupValues[1]) }
        // chunk 35: "another artifact you control"
        ANOTHER.matchEntire(text)?.let { m -> return Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), Filters.noun(m.groupValues[1]) ?: return null) }
        return null
    }

    private val ENTERS = Regex("""(.+) enters""")
    private val BEGINNING_OF_YOUR = Regex("""the beginning of your (upkeep|draw step|end step)""")
    private val STEPS = mapOf("upkeep" to arcanum.api.TurnStep.Upkeep, "draw step" to arcanum.api.TurnStep.Draw, "end step" to arcanum.api.TurnStep.End)
    private val PUT_INTO_GRAVEYARD_FROM_BATTLEFIELD = Regex("""(.+) is put into a graveyard from the battlefield""")
    private val SELF_OR_ANOTHER = Regex("""(.+) or another (.+)""")
    private val DIES = Regex("""(.+) dies""")
    // chunk 12
    private val YOU_CAST = Regex("""you cast an? (?:(.+) )?spell""")
    // chunk 25
    private val PUT_INTO_OWNERS_GRAVEYARD = Regex("""(.+) is put into (your|an opponent's) graveyard from the battlefield""")
    private val ARTICLE_NOUN = Regex("""(?:an?|one or more) (.+)""")
    // chunk 26
    private val LEAVE_YOUR_GRAVEYARD = Regex("""(.+) leaves? your graveyard""")
    private val DEALS_COMBAT_DAMAGE_TO_A_PLAYER = Regex("""(.+) deals combat damage to a player""")
    // chunk 14
    private val PLAYER_CASTS = Regex("""a player casts an? (?:(.+) )?spell""")
    private val PLAYER_TAPS_FOR_MANA = Regex("""a player taps (.+) for mana""")
    // chunk 20
    private val BECOMES_UNATTACHED = Regex("""(.+) becomes unattached from an? (.+)""")
    // chunk 21
    private val ENTERS_OR_LEAVES = Regex("""(.+) enters or (leaves the battlefield|is put into a graveyard from the battlefield)""")
    private val ATTACKS_OR_BLOCKS = Regex("""(.+) attacks or blocks""")
    private val YOU_DISCARD = Regex("""you discard an? (.+)""")
    // chunk 35
    private val DIES_OR_PUT_INTO_GRAVEYARD = Regex("""(.+) dies or (.+) is put into a graveyard from the battlefield""")
    private val YOU_SACRIFICE = Regex("""you sacrifice an? (.+)""")
    private val ANOTHER = Regex("""another (.+)""")
    // chunk 36
    private val ENTERS_OR_ATTACKS = Regex("""(.+) enters or attacks""")
    private val ATTACKS = Regex("""(.+) attacks""")
}

/** Conditions ("if you control seven or more lands with different names"). */
internal object Conditions {
    fun condition(text: String, self: Set<String> = emptySet()): Condition? {
        CONTROL_A.matchEntire(text)?.let { m -> return Condition.Exists(Filters.noun(m.groupValues[1] + " you control") ?: return null) }
        CARD_TYPES_IN_GRAVEYARD.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return Condition.Compare(Value.CountDistinct(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You)))), SharedQuality.CardType), Comparison.AtLeast, Value.Fixed(n))
        }
        if (text == "a permanent left the battlefield under your control this turn")
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(ObjectFilter.And(listOf(
                ObjectFilter.Category(arcanum.api.ObjectCategory.Permanent), ObjectFilter.ControlledBy(PlayerFilter.You))), from = Zone.Battlefield))
        // chunk 21
        SELF_IS_UNTAPPED.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.Not(ObjectFilter.HasStatus(arcanum.api.Status.Tapped)))
        }
        // chunk 20: "the sacrificed creature's toughness was 4 or greater" (its last known information, CR 608.2h)
        SACRIFICED_AT_LEAST.matchEntire(text)?.let { m ->
            val n = Value.Fixed(m.groupValues[3].toInt())
            val filter = if (m.groupValues[2] == "power") ObjectFilter.Power(Comparison.AtLeast, n) else ObjectFilter.Toughness(Comparison.AtLeast, n)
            return Condition.ObjectMatches(Objects.Bound(SACRIFICED), filter)
        }
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
        // chunk 26: "this card is in your graveyard"
        IS_IN_YOUR_GRAVEYARD.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.InZone(Zone.Graveyard))
        }
        // chunk 26: "Kaito entered this turn"
        ENTERED_THIS_TURN.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.ThisTurn(EventPattern.ZoneChange(to = Zone.Battlefield)))
        }
        // chunk 26: "there are cards exiled with this enchantment": its card memory (CR 607.2a); false when there are none
        EXILED_WITH_SELF.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.Remembered(EXILED_WITH), ObjectFilter.InZone(Zone.Exile))
        }
        // chunk 25: "it wasn't the first land you played this turn": this turn's land plays include it (CR 305.2a)
        if (text == "it wasn't the first land you played this turn")
            return Condition.HappenedThisTurn(EventPattern.PlayLand(players = PlayerFilter.You), atLeast = 2)
        return null
    }

    private val CONTROL_A = Regex("""you control an? (\w+)""")
    private val CARD_TYPES_IN_GRAVEYARD = Regex("""there are (\w+) or more card types among cards in your graveyard""")
    private val SELF_IS_TAPPED = Regex("""(.+) is tapped""")
    private val CARDS_IN_YOUR_GRAVEYARD = Regex("""there are (\w+) or more cards in your graveyard""")
    private val CONTROL_N_OR_MORE = Regex("""you control (\w+) or more (\w+)""")
    private val CONTROL_N_WITH_DIFFERENT_NAMES = Regex("""you control (\w+) or more (.+) with different names""")
    // chunk 26
    private val IS_IN_YOUR_GRAVEYARD = Regex("""(.+) is in your graveyard""")
    private val ENTERED_THIS_TURN = Regex("""(.+) entered this turn""")
    private val EXILED_WITH_SELF = Regex("""there are cards exiled with (.+)""")
    // chunk 20
    private val SACRIFICED_AT_LEAST = Regex("""the sacrificed (\w+)'s (power|toughness) was (\d+) or greater""")
    // chunk 21
    private val SELF_IS_UNTAPPED = Regex("""(.+) is untapped""")
}

/** The name a sacrifice cost gives the sacrificed object when the effect reads it ("the sacrificed creature's power"). */
// chunk 20
internal const val SACRIFICED = "sacrificed"

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
        // chunk 26: 'with "This token can't be blocked."' (CR 111.4)
        val quoted = QUOTED.matchEntire(m.groupValues[3])?.let { q ->
            listOf(Sentences(setOf("this token", "this creature")).line(q.groupValues[1]) ?: return null)
        }
        val keywords = quoted ?: m.groupValues[3].takeIf { it.isNotEmpty() }?.split(Regex(""",? and |, """))?.map { OracleParser.keyword(it) ?: return null }.orEmpty()
        return count to token.copy(face = token.face.copy(abilities = keywords))
    }

    fun defined(text: String): TokenSpec.Defined? {
        val m = TOKEN.matchEntire(text) ?: return null
        // chunk 26: "colorless" has no colour indicator colours (CR 105.2c)
        val colors = if (m.groupValues[2] == "colorless") emptyList() else m.groupValues[2].split(" and ").map { COLORS[it] ?: return null }
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

    private val PREDEFINED = Regex("""(a|an|two|three) ([A-Z][a-z]+) tokens?""")
    private val COUNTED = Regex("""(a|an|one|two|three|four|five) (\d+/\d+ .+? creature) tokens?(?: with (.+))?""")
    // chunk 26
    private val QUOTED = Regex(""""(.+)"""")
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

/** chunk 26: card memory key for cards "exiled with" this object (CR 607.2a). */
internal const val EXILED_WITH = "exiled"
