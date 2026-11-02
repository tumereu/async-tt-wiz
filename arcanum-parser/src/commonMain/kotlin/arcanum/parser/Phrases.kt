package arcanum.parser

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Measure
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

/** Number words ("a", "two", "seven") and ordinals. */
internal object Numbers {
    fun count(word: String): Int? = WORDS[word.lowercase()] ?: word.toIntOrNull()
    /** chunks 47, 50, 52: "first", "second", "third" ("your second card", "the third time"). */
    fun ordinal(word: String): Int? = ORDINALS[word.lowercase()]

    private val WORDS = mapOf("a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10)
    private val ORDINALS = mapOf("first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5)
}

/** Noun phrases naming objects and players ("nonbasic land", "a Mountain or Plains card", "creature you control"). */
internal object Filters {
    /**
     * "land", "nonbasic land", "Mountain or Plains", "basic land", "artifact, enchantment, or nonbasic land an opponent
     * controls", "land card with a basic land type", "legendary creature you control".
     */
    fun noun(phrase: String): ObjectFilter? {
        // chunk 40: "another creature", "other Elves you control": any but this object
        OTHER.matchEntire(phrase)?.let { m -> return all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), noun(m.groupValues[1]) ?: return null) }
        // chunks 31, 42: "creature card in your graveyard", "instant or sorcery card in your graveyard"
        IN_YOUR_GRAVEYARD.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
        }
        // chunk 51: "artifact card with mana value equal to 1 plus the sacrificed artifact's mana value" (its last known
        // information, CR 608.2h)
        MANA_VALUE_PLUS_SACRIFICED.matchEntire(phrase)?.let { m ->
            val sacrificed = Value.Of(arcanum.api.NumericProperty.ManaValue, Objects.Bound(SACRIFICED), Combine.Single)
            return all(noun(m.groupValues[1]) ?: return null,
                ObjectFilter.ManaValue(Comparison.Equal, Value.Sum(listOf(Value.Fixed(m.groupValues[2].toInt()), sacrificed))))
        }
        // chunk 14: "instant or sorcery spell": the head noun covers both alternatives.
        OR_SPELL.matchEntire(phrase)?.let { m -> return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.Category(arcanum.api.ObjectCategory.Spell)) }
        // chunk 14: "permanent card" (CR 110.4a): an artifact, battle, creature, enchantment, land, or planeswalker card.
        if (phrase == "permanent card" || phrase == "permanent cards") return ObjectFilter.Or(PERMANENT_TYPES.map { ObjectFilter.Type(it) })
        // chunk 42: "nonland permanent card"
        QUALIFIED_PERMANENT_CARD.matchEntire(phrase)?.let { m -> return all(noun(m.groupValues[1]) ?: return null, noun("permanent card")!!) }
        ON_THE_BATTLEFIELD.matchEntire(phrase)?.let { m -> return noun(m.groupValues[1]) }
        // chunk 21: "creatures you control with +1/+1 counters on them"
        WITH_COUNTERS.matchEntire(phrase)?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.HasCounter(kind, atLeast = 1))
        }
        // chunk 47: "creatures with decayed": a keyword ability (CR 702)
        WITH_KEYWORD.matchEntire(phrase)?.let { m ->
            OracleParser.keyword(m.groupValues[2])?.let { keyword -> return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.HasKeyword(keyword.kind)) }
        }
        // chunk 20: "artifact and/or enchantment" is either of them
        if (phrase.contains(" and/or ")) return noun(phrase.replace(" and/or ", " or "))
        // chunk 31: "card from a graveyard"
        FROM_A_GRAVEYARD.matchEntire(phrase)?.let { m -> return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.InZone(Zone.Graveyard)) }
        // chunk 32: "creature card with power 2 or less", "… with toughness 2 or less"
        WITH_POWER_OR_TOUGHNESS.matchEntire(phrase)?.let { m ->
            val n = Value.Fixed(m.groupValues[3].toInt())
            // blb chunk 01: "… or greater"
            val comparison = if (m.groupValues[4] == "greater") Comparison.AtLeast else Comparison.AtMost
            val test = if (m.groupValues[2] == "power") ObjectFilter.Power(comparison, n) else ObjectFilter.Toughness(comparison, n)
            return all(noun(m.groupValues[1]) ?: return null, test)
        }
        WITH_MANA_VALUE.matchEntire(phrase)?.let { m ->
            val value = if (m.groupValues[2] == "X") Value.Announced(name = "X") else Value.Fixed(m.groupValues[2].toInt())
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
        // chunk 32: "instant or sorcery spell": the alternatives share the noun "spell".
        if (phrase.contains(" or ") && phrase.endsWith(" spell"))
            return all(noun(phrase.removeSuffix(" spell")) ?: return null, ObjectFilter.Category(arcanum.api.ObjectCategory.Spell))
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
        // chunk 56: "non-Horror"
        if (word.startsWith("non")) return word(word.removePrefix("non").removePrefix("-"))?.let { ObjectFilter.Not(it) }
        when (word) {
            "spell" -> return ObjectFilter.Category(arcanum.api.ObjectCategory.Spell)
            "token" -> return ObjectFilter.Category(arcanum.api.ObjectCategory.Token)
            "permanent" -> return ObjectFilter.Category(arcanum.api.ObjectCategory.Permanent)
            "card" -> return ObjectFilter.Category(arcanum.api.ObjectCategory.Card)
            "attacking" -> return ObjectFilter.HasStatus(arcanum.api.Status.Attacking) // chunk 32
            // chunk 40: "spell or ability" (an ability on the Stack, CR 113.1)
            "ability" -> return ObjectFilter.Category(arcanum.api.ObjectCategory.StackAbility)
            // chunk 41: "a tapped land"
            "tapped" -> return ObjectFilter.HasStatus(arcanum.api.Status.Tapped)
            "untapped" -> return ObjectFilter.Not(ObjectFilter.HasStatus(arcanum.api.Status.Tapped))
        }
        COLORS[word]?.let { return ObjectFilter.OfColor(it) }
        // chunk 50: "historic": artifacts, legendaries and Sagas (CR 700.6)
        if (word == "historic") return ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.Super(Supertype.Legendary),
            ObjectFilter.Sub(Subtype("Saga"))))
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
    private val WITH_POWER_OR_TOUGHNESS = Regex("""(.+) with (power|toughness) (\d+) or (less|greater)""")
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
    // chunk 40
    private val OTHER = Regex("""(?:an)?other (.+)""")
    // chunk 42
    private val QUALIFIED_PERMANENT_CARD = Regex("""(.+) permanent cards?""")
    // chunk 36
    private val WITH_KEYWORDS = Regex("""(.+?) with ([a-z ]+(?:,? or [a-z ]+)+|[a-z]+)""")
    // chunk 47
    private val WITH_KEYWORD = Regex("""(.+) with (\w+)""")
    // chunk 51
    private val MANA_VALUE_PLUS_SACRIFICED = Regex("""(.+) with mana value equal to (\d+) plus the sacrificed \w+'s mana value""")
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
        // chunk 31: "this creature enters or dies"; "one or more artifacts you control enter" (one trigger for a batch)
        ENTERS_OR_DIES.matchEntire(text)?.let { m ->
            val subject = subject(m.groupValues[1], self) ?: return null
            return listOf(EventPattern.ZoneChange(subject, to = Zone.Battlefield), EventPattern.ZoneChange(subject, from = Zone.Battlefield, to = Zone.Graveyard))
        }
        ONE_OR_MORE_ENTER.matchEntire(text)?.let { m -> return listOf(EventPattern.ZoneChange(Filters.noun(m.groupValues[1]) ?: return null, to = Zone.Battlefield)) }
        ATTACKS_OR_BLOCKS.matchEntire(text)?.let { m ->
            val subject = subject(m.groupValues[1], self) ?: return null
            return listOf(EventPattern.Attack(subject), EventPattern.Block(subject, attacker = ObjectFilter.Any))
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
        // chunks 45, 46: "at the beginning of combat on your turn"
        if (text == "the beginning of combat on your turn") return EventPattern.StepBegins(arcanum.api.TurnStep.BeginningOfCombat, PlayerFilter.You)
        // chunk 47: "a player sacrifices a permanent"
        PLAYER_SACRIFICES.matchEntire(text)?.let { m -> return EventPattern.Sacrifice(Filters.noun(m.groupValues[1]) ?: return null) }
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
        if (text == "you play a land") return EventPattern.PlayLand(players = PlayerFilter.You, lands = ObjectFilter.Any)
        // chunk 14: "a player casts an instant or sorcery spell", "a player taps an Island for mana", "an opponent gains life"
        PLAYER_CASTS.matchEntire(text)?.let { m ->
            val kind = m.groupValues[1].takeIf { it.isNotEmpty() }?.let { Filters.noun(it) ?: return null } ?: ObjectFilter.Any
            return EventPattern.Cast(kind, by = PlayerFilter.Any)
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
        // blb chunk 01
        if (text == "you gain life") return EventPattern.GainLife(PlayerFilter.You)
        // blb chunk 01: "a creature you control becomes the target of a spell or ability an opponent controls" (CR 115.10)
        BECOMES_TARGET.matchEntire(text)?.let { m ->
            val by = when (m.groupValues[2]) {
                "you control" -> PlayerFilter.You
                "an opponent controls" -> PlayerFilter.Opponent
                else -> return null
            }
            return EventPattern.BecomesTarget(subject(m.groupValues[1], self) ?: return null, by = ObjectFilter.ControlledBy(by))
        }
        // chunk 33: leaves-the-battlefield (CR 603.6c)
        LEAVES_THE_BATTLEFIELD.matchEntire(text)?.let { m ->
            return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, from = Zone.Battlefield)
        }
        // chunk 32: "this creature enters from your hand"
        ENTERS_FROM_YOUR_HAND.matchEntire(text)?.let { m ->
            return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, from = Zone.Hand, to = Zone.Battlefield)
        }
        // chunk 42: "you sacrifice a land": only its controller sacrifices a permanent (CR 701.21a)
        YOU_SACRIFICE.matchEntire(text)?.let { m ->
            return EventPattern.Sacrifice(Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.ControlledBy(PlayerFilter.You)))
        }
        // chunk 50: "a land card is put into your graveyard from anywhere": a card goes to its owner's graveyard; tokens
        // aren't cards (CR 108.2b)
        PUT_INTO_YOUR_GRAVEYARD_FROM_ANYWHERE.matchEntire(text)?.let { m ->
            val cards = listOfNotNull(subject(m.groupValues[1], self) ?: return null,
                ObjectFilter.Category(arcanum.api.ObjectCategory.Card).takeIf { m.groupValues[1].contains("card") })
            return EventPattern.ZoneChange(Filters.all(*cards.toTypedArray(), ObjectFilter.OwnedBy(PlayerFilter.You)), to = Zone.Graveyard)
        }
        // chunk 50: "you cast a spell from anywhere other than your hand" (CR 601.2a: cast from where it was)
        YOU_CAST_NOT_FROM_HAND.matchEntire(text)?.let { m ->
            val notFromHand = ObjectFilter.Not(ObjectFilter.WasCast(Zone.Hand))
            val spell = m.groupValues[1].takeIf { it.isNotEmpty() }?.let { Filters.all(Filters.noun(it) ?: return null, notFromHand) } ?: notFromHand
            return EventPattern.Cast(spell = spell, by = PlayerFilter.You)
        }
        // chunk 36: "the beginning of each end step": every player's
        BEGINNING_OF_EACH.matchEntire(text)?.let { m -> return EventPattern.StepBegins(STEPS[m.groupValues[1]] ?: return null, whose = PlayerFilter.Any) }
        // chunk 52: "a creature is exiled from the battlefield"
        EXILED_FROM_THE_BATTLEFIELD.matchEntire(text)?.let { m ->
            return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, from = Zone.Battlefield, to = Zone.Exile)
        }
        // chunk 56: "this creature transforms into [its back face's name]": it transforms and then has that name (CR 701.27e)
        TRANSFORMS_INTO.matchEntire(text)?.let { m ->
            if (m.groupValues[2] !in self) return null
            return EventPattern.Transform(Filters.all(subject(m.groupValues[1], self) ?: return null, ObjectFilter.Named(m.groupValues[2])))
        }
        return null
    }

    /** "this land", "this land or another land you control", "equipped creature", "a nontoken creature". */
    private fun subject(text: String, self: Set<String>): ObjectFilter? {
        if (text in self || text.replaceFirstChar { it.lowercase() } in self) return ObjectFilter.Is(Objects.Self)
        // chunk 20
        if (text == "equipped creature" || text == "enchanted creature") return ObjectFilter.Is(Objects.AttachedTo(of = Objects.Self))
        SELF_OR_ANOTHER.matchEntire(text)?.let { m ->
            if (m.groupValues[1] !in self) return null
            val other = Filters.noun(m.groupValues[2]) ?: return null
            return ObjectFilter.Or(listOf(ObjectFilter.Is(Objects.Self),
                Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), other)))
        }
        // chunk 40: "another nontoken creature you control"
        if (text.startsWith("another ")) return Filters.noun(text)
        // chunk 25: "a creature", "a land you control", "one or more creature cards"
        ARTICLE_NOUN.matchEntire(text)?.let { m -> return Filters.noun(m.groupValues[1]) }
        // chunk 30: "another creature or artifact you control"
        ANOTHER.matchEntire(text)?.let { m ->
            return Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), Filters.noun(m.groupValues[1]) ?: return null)
        }
        return null
    }

    private val ENTERS = Regex("""(.+) enters?""")
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
    // chunk 30
    private val ANOTHER = Regex("""another (.+)""")
    // chunk 31
    private val ENTERS_OR_DIES = Regex("""(.+) enters or dies""")
    private val ONE_OR_MORE_ENTER = Regex("""one or more (.+) enter""")
    // chunk 32
    private val ENTERS_FROM_YOUR_HAND = Regex("""(.+) enters from your hand""")
    // chunk 33
    private val LEAVES_THE_BATTLEFIELD = Regex("""(.+) leaves the battlefield""")
    // chunk 42
    private val YOU_SACRIFICE = Regex("""you sacrifice an? (.+)""")
    // chunk 35
    private val DIES_OR_PUT_INTO_GRAVEYARD = Regex("""(.+) dies or (.+) is put into a graveyard from the battlefield""")
    // chunk 36
    private val ENTERS_OR_ATTACKS = Regex("""(.+) enters or attacks""")
    private val ATTACKS = Regex("""(.+) attacks""")
    // chunk 46
    // chunk 47
    private val PLAYER_SACRIFICES = Regex("""a player sacrifices an? (.+)""")
    // chunk 50
    private val YOU_CAST_NOT_FROM_HAND = Regex("""you cast an? (?:(.+) )?spell from anywhere other than your hand""")
    private val BEGINNING_OF_EACH = Regex("""the beginning of each (upkeep|end step)""")
    private val PUT_INTO_YOUR_GRAVEYARD_FROM_ANYWHERE = Regex("""(.+) (?:is|are) put into your graveyard from anywhere""")
    // chunk 52
    private val EXILED_FROM_THE_BATTLEFIELD = Regex("""(.+) is exiled from the battlefield""")
    // chunk 56
    private val TRANSFORMS_INTO = Regex("""(.+) transforms into (.+)""")
    // blb chunk 01
    private val BECOMES_TARGET = Regex("""(.+) becomes the target of a spell or ability (you control|an opponent controls)""")
}

/** Conditions ("if you control seven or more lands with different names"). */
internal object Conditions {
    fun condition(text: String, self: Set<String> = emptySet()): Condition? {
        CONTROL_A.matchEntire(text)?.let { m -> return Condition.Exists(Filters.noun(m.groupValues[1] + " you control") ?: return null, atLeast = 1) }
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
            return Condition.Compare(Value.ManaSpent(color = colors.singleOrNull() ?: return null, of = Objects.Self), Comparison.AtLeast, Value.Fixed(symbols.size))
        }
        // chunk 32: "you've discarded a card this turn"
        if (text == "you've discarded a card this turn") return Condition.HappenedThisTurn(EventPattern.Discard(players = PlayerFilter.You, cards = ObjectFilter.Any), atLeast = 1, measure = Measure.Events)
        // chunk 33: "it was kicked", "this creature was kicked" (CR 702.33d)
        WAS_KICKED.matchEntire(text)?.takeIf { it.groupValues[1] == "it" || it.groupValues[1] in self }?.let {
            return Condition.Paid(arcanum.api.Keyword.Kicker.Kind, times = 1, of = Objects.Self)
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
                ObjectFilter.Category(arcanum.api.ObjectCategory.Permanent), ObjectFilter.ControlledBy(PlayerFilter.You))), from = Zone.Battlefield), atLeast = 1, measure = Measure.Events)
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
            // chunk 45: "with different powers" (coven)
            val quality = if (m.groupValues[3] == "powers") SharedQuality.Number(arcanum.api.NumericProperty.Power) else SharedQuality.Name
            return Condition.Compare(Value.CountDistinct(Objects.Each(filter), quality), Comparison.AtLeast, Value.Fixed(n))
        }
        // chunk 56: "you control five or more Blood tokens"
        CONTROL_N_OR_MORE_PHRASE.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return Condition.Exists(Filters.noun(m.groupValues[2] + " you control") ?: return null, atLeast = n)
        }
        // chunk 26: "this card is in your graveyard"
        IS_IN_YOUR_GRAVEYARD.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.InZone(Zone.Graveyard))
        }
        // chunk 26: "Kaito entered this turn"
        ENTERED_THIS_TURN.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.ThisTurn(EventPattern.ZoneChange(to = Zone.Battlefield, objects = ObjectFilter.Any)))
        }
        // chunk 26: "there are cards exiled with this enchantment": its card memory (CR 607.2a); false when there are none
        EXILED_WITH_SELF.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.Remembered(EXILED_WITH), ObjectFilter.InZone(Zone.Exile))
        }
        // chunk 45: "you attacked this turn" (raid)
        if (text == "you attacked this turn") return Condition.HappenedThisTurn(EventPattern.Attack(ObjectFilter.ControlledBy(PlayerFilter.You)), atLeast = 1, measure = Measure.Events)
        // chunk 45: "this card is suspended": in exile, with suspend and a time counter (CR 702.62b)
        IS_SUSPENDED.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.Self, Filters.all(ObjectFilter.InZone(Zone.Exile),
                ObjectFilter.HasKeyword(arcanum.api.Keyword.Suspend.Kind), ObjectFilter.HasCounter(arcanum.api.CounterKind.Time, atLeast = 1)))
        }
        // chunk 45: "two or more creatures died this turn": counts the creatures (CR 700.4)
        DIED_THIS_TURN.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(Filters.noun(m.groupValues[2]) ?: return null, from = Zone.Battlefield, to = Zone.Graveyard),
                atLeast = n, measure = arcanum.api.Measure.Objects)
        }
        // chunk 45: "an opponent has 10 or less life"
        OPPONENT_LIFE_AT_MOST.matchEntire(text)?.let { m ->
            return Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Life(Comparison.AtMost, Value.Fixed(m.groupValues[1].toInt())))), atLeast = 1)
        }
        // chunk 47: "you control no creatures with decayed"
        CONTROL_NONE.matchEntire(text)?.let { m ->
            return Condition.Not(Condition.Exists(Filters.noun(m.groupValues[1] + " you control") ?: return null, atLeast = 1))
        }
        // chunk 25: "it wasn't the first land you played this turn": this turn's land plays include it (CR 305.2a)
        if (text == "it wasn't the first land you played this turn")
            return Condition.HappenedThisTurn(EventPattern.PlayLand(players = PlayerFilter.You, lands = ObjectFilter.Any), atLeast = 2, measure = Measure.Events)
        // chunk 41: "this creature is attacking"
        SELF_IS_ATTACKING.matchEntire(text)?.takeIf { it.groupValues[1] in self || it.groupValues[1].replaceFirstChar { c -> c.lowercase() } in self }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(arcanum.api.Status.Attacking))
        }
        // chunk 42: "there are three or more land cards in your graveyard"
        THERE_ARE_N_OR_MORE.matchEntire(text)?.let { m ->
            return Condition.Exists(Filters.noun(m.groupValues[2]) ?: return null, atLeast = Numbers.count(m.groupValues[1]) ?: return null)
        }
        // chunk 36: "a card left your graveyard this turn"
        LEFT_YOUR_GRAVEYARD_THIS_TURN.matchEntire(text)?.let { m ->
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(Filters.all(Filters.noun(m.groupValues[1]) ?: return null,
                ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard), atLeast = 1, measure = Measure.Events)
        }
        // chunk 52: "this is the second time this ability has resolved this turn" (counting this resolution)
        NTH_RESOLUTION.matchEntire(text)?.let { m ->
            return Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, Value.Fixed(Numbers.ordinal(m.groupValues[1]) ?: return null))
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
    private val CONTROL_N_WITH_DIFFERENT_NAMES = Regex("""you control (\w+) or more (.+) with different (names|powers)""")
    // chunk 26
    private val IS_IN_YOUR_GRAVEYARD = Regex("""(.+) is in your graveyard""")
    private val ENTERED_THIS_TURN = Regex("""(.+) entered this turn""")
    private val EXILED_WITH_SELF = Regex("""there are cards exiled with (.+)""")
    // chunk 20
    private val SACRIFICED_AT_LEAST = Regex("""the sacrificed (\w+)'s (power|toughness) was (\d+) or greater""")
    // chunk 21
    private val SELF_IS_UNTAPPED = Regex("""(.+) is untapped""")
    // chunk 41
    private val SELF_IS_ATTACKING = Regex("""(.+) is attacking""")
    // chunk 42
    private val THERE_ARE_N_OR_MORE = Regex("""there are (\w+) or more (.+)""")
    // chunk 45
    private val IS_SUSPENDED = Regex("""(.+) is suspended""")
    private val DIED_THIS_TURN = Regex("""(\w+) or more (.+) died this turn""")
    private val OPPONENT_LIFE_AT_MOST = Regex("""an opponent has (\d+) or less life""")
    // chunk 47
    private val CONTROL_NONE = Regex("""you control no (.+)""")
    // chunk 52
    // chunk 36
    private val LEFT_YOUR_GRAVEYARD_THIS_TURN = Regex("""an? (.+) left your graveyard this turn""")
    private val NTH_RESOLUTION = Regex("""this is the (\w+) time this ability has resolved this turn""")
    // chunk 56
    private val CONTROL_N_OR_MORE_PHRASE = Regex("""you control (\w+) or more (.+)""")
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
        // chunk 33: "an X/X … token": X is worked out when it is created (the ability's "where X is …")
        val x = m.groupValues[1] == "X/X"
        return TokenSpec.Defined(FaceDefinition(
            name = subtypes.joinToString(" ") { it.name } + " Token",
            colorIndicator = colors,
            types = types, subtypes = subtypes,
            power = if (x) PrintedNumber.X else PrintedNumber.Fixed(m.groupValues[1].substringBefore('/').toInt()),
            toughness = if (x) PrintedNumber.X else PrintedNumber.Fixed(m.groupValues[1].substringAfter('/').toInt()),
        ), power = if (x) Value.Announced(name = "X") else null, toughness = if (x) Value.Announced(name = "X") else null)
    }

    private val PREDEFINED = Regex("""(a|an|two|three) ([A-Z][a-z]+) tokens?""")
    private val COUNTED = Regex("""(a|an|one|two|three|four|five) ((?:\d+/\d+|X/X) .+? creature) tokens?(?: with (.+))?""")
    // chunk 26
    private val QUOTED = Regex(""""(.+)"""")
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

/** chunk 26: card memory key for cards "exiled with" this object (CR 607.2a). */
internal const val EXILED_WITH = "exiled"
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
        DEVOTION.matchEntire(text)?.let { m -> return Value.Devotion(listOf(COLORS[m.groupValues[1]] ?: return null), players = Players.You) }
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
