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
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        // gtc chunk 09
        "twenty" to 20,
        // hob chunk 10
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15)
    /** isd chunk 01: every counted number word above, for a regex alternation ("thirteen tapped 2/2 … tokens"). */
    val WORD_PATTERN: String = WORDS.keys.sortedByDescending { it.length }.joinToString("|")
    private val ORDINALS = mapOf("first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
        // msh
        "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9, "tenth" to 10)
}

/** Noun phrases naming objects and players ("nonbasic land", "a Mountain or Plains card", "creature you control"). */
private val HALF_ROUNDED = Regex("""half (.+), rounded (up|down)""")

internal object Filters {
    /**
     * "land", "nonbasic land", "Mountain or Plains", "basic land", "artifact, enchantment, or nonbasic land an opponent
     * controls", "land card with a basic land type", "legendary creature you control".
     */
    fun noun(phrase: String): ObjectFilter? {
        // CR 701.54e: "your Ring-bearer"
        if (phrase == "your Ring-bearer" || phrase == "Your Ring-bearer") return MechanicWordings.yourRingBearer
        LtrPhrases.noun(phrase)?.let { return it } // ltr
        Mh2Phrases.noun(phrase)?.let { return it } // mh2
        DftPhrases.noun(phrase)?.let { return it } // dft
        FinPhrases.noun(phrase)?.let { return it } // fin
        TdmPhrases.noun(phrase)?.let { return it } // tdm
        EoePhrases.noun(phrase)?.let { return it } // eoe
        SpmPhrases.noun(phrase)?.let { return it } // spm
        EclPhrases.noun(phrase)?.let { return it } // ecl
        SosPhrases.noun(phrase)?.let { return it } // sos
        TmtPhrases.noun(phrase)?.let { return it } // tmt
        MshPhrases.noun(phrase)?.let { return it } // msh
        FraPhrases.noun(phrase)?.let { return it } // fra
        InrPhrases.noun(phrase)?.let { return it } // inr
        IsdPhrases.noun(phrase)?.let { return it } // isd
        // fdn chunk 08: "Kithkin spells and Soldier spells you cast": a spell of either kind (CR 601.2f)
        Fdn.X_SPELLS_AND_Y_SPELLS.matchEntire(phrase)?.let { m ->
            return ObjectFilter.Or(listOf(noun("${m.groupValues[1]} spells${m.groupValues[3]}") ?: return null, noun("${m.groupValues[2]} spells${m.groupValues[3]}") ?: return null))
        }
        // fdn chunk 11c: "red instant or sorcery spell": the colour describes both kinds (CR 202.2)
        Fdn.COLOR_TYPE_OR_TYPE.matchEntire(phrase)?.let { m ->
            return ObjectFilter.Or(listOf(noun("${m.groupValues[1]} ${m.groupValues[2]}${m.groupValues[4]}") ?: return null,
                noun("${m.groupValues[1]} ${m.groupValues[3]}${m.groupValues[4]}") ?: return null))
        }
        gtcNoun(phrase)?.let { return it }
        // fdn chunk 10a: "creatures you control of the chosen color" (the color chosen as this permanent entered, CR 105.1)
        OF_CHOSEN_COLOR.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.OfColor(arcanum.api.ColorRef.Remembered("color")))
        }
        // blb chunk 12: "creatures you control of the chosen type" (the creature type chosen as this permanent entered)
        OF_CHOSEN_TYPE.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.Sub(arcanum.api.SubtypeRef.Remembered("type")))
        }
        // blb chunk 12: "other creature you control that's a token or a Rabbit"
        THATS_A_OR_A.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.Or(listOf(noun(m.groupValues[2]) ?: return null, noun(m.groupValues[3]) ?: return null)))
        }
        // blb chunk 11: "creature you control with toughness greater than its power"
        TOUGHNESS_GREATER_THAN_POWER.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.Toughness(Comparison.Greater,
                Value.Of(arcanum.api.NumericProperty.Power, Objects.Tested, Combine.Single)))
        }
        // blb chunk 09: "creature card you own in exile and in your graveyard"
        OWN_IN_EXILE_AND_GRAVEYARD.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.OwnedBy(PlayerFilter.You),
                ObjectFilter.Or(listOf(ObjectFilter.InZone(Zone.Exile), ObjectFilter.InZone(Zone.Graveyard))))
        }
        // dsk chunk 01: "a Room card that doesn't have the same name as a Room you control" (CR 709.4a: either name)
        NOT_SAME_NAME_AS.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.Not(ObjectFilter.SameNameAs(Objects.Each(noun(m.groupValues[2].removeArticle()) ?: return null))))
        }
        // mkm chunk 08: "creatures that don't have a name" (a face-down permanent has none, CR 708.2a)
        WITHOUT_A_NAME.matchEntire(phrase)?.let { m -> return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.Not(ObjectFilter.HasName)) }
        // dsk chunk 08: "an instant or sorcery spell that targets only a single creature you control" (CR 115.7)
        TARGETS_ONLY_A_SINGLE.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.SingleTarget(noun(m.groupValues[2]) ?: return null))
        }
        // dsk chunk 07: "Insects and Spiders": either of two plural subtypes
        PLURAL_SUBTYPES_AND.matchEntire(phrase)?.let { m ->
            val a = noun(m.groupValues[1]); val b = noun(m.groupValues[2])
            if (a != null && b != null) return ObjectFilter.Or(listOf(a, b))
        }
        // dsk chunk 07: "any card" (CR 701.23a: a search for any card)
        if (phrase == "any card") return ObjectFilter.Category(arcanum.api.ObjectCategory.Card)
        // dsk chunk 01: "creature spells you cast": spells a player cast are spells they control (CR 112.2)
        SPELLS_YOU_CAST.matchEntire(phrase)?.let { m -> return all(noun(m.groupValues[1] + " spell") ?: return null, ObjectFilter.ControlledBy(PlayerFilter.You)) }
        // blb gaps 2: "instant and sorcery spells you cast": each spell of either type
        SPELLS_OF_TYPES_YOU_CAST.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1] + " or " + m.groupValues[2] + " spell") ?: return null, ObjectFilter.ControlledBy(PlayerFilter.You))
        }
        // blb gaps 2: "creature spells with {X} in their mana costs" (CR 107.3)
        WITH_X_IN_MANA_COST.matchEntire(phrase)?.let { m -> return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.XInManaCost) }
        // dsk chunk 01: "creatures with power 3 or greater"
        WITH_POWER_OR_GREATER.matchEntire(phrase)?.let { m ->
            val n = Value.Fixed(m.groupValues[3].toInt())
            val test = if (m.groupValues[2] == "power") ObjectFilter.Power(Comparison.AtLeast, n) else ObjectFilter.Toughness(Comparison.AtLeast, n)
            return all(noun(m.groupValues[1]) ?: return null, test)
        }
        // mkm chunk 07: "creature you control other than enchanted creature": any but the one this Aura or Equipment is attached to
        OTHER_THAN_ATTACHED.matchEntire(phrase)?.let { m ->
            return all(ObjectFilter.Not(ObjectFilter.Is(Objects.AttachedTo(of = Objects.Self))), noun(m.groupValues[1]) ?: return null)
        }
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

        // blb chunk 08: "creature you control without flying", "… that entered the battlefield this turn"
        WITHOUT_KEYWORD.matchEntire(phrase)?.let { m ->
            OracleParser.keyword(m.groupValues[2].replaceFirstChar { it.uppercase() })?.let { k ->
                return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.Not(ObjectFilter.HasKeyword(k.kind)))
            }
        }
        ENTERED_THIS_TURN.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Battlefield)))
        }
        // rtr chunk 05: "creature that dealt damage this turn" (the source of a damage event)
        DEALT_DAMAGE_THIS_TURN_AS_SOURCE.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, arcanum.api.RecipientFilter()),
                role = arcanum.api.EventRole.Source))
        }
        // blb chunk 06: "creature an opponent controls that was dealt damage this turn"
        DEALT_DAMAGE_THIS_TURN.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, arcanum.api.RecipientFilter()),
                // fin chunk 02: the creature dealt the damage, not one that dealt it
                role = arcanum.api.EventRole.Object))
        }
        // blb chunk 05: "equipped creatures you control" (plural: every creature with an Equipment; "equipped creature" is the
        // one this Equipment is attached to, CR 301.5)
        EQUIPPED_PLURAL.matchEntire(phrase)?.let { m ->
            val attachment = ObjectFilter.Sub(Subtype(if (m.groupValues[1] == "equipped") "Equipment" else "Aura"))
            return all(ObjectFilter.Attached(attachments = attachment), noun(m.groupValues[2]) ?: return null)
        }
        // blb chunk 05: "permanents you control with counters on them" (any kind)
        WITH_ANY_COUNTERS.matchEntire(phrase)?.let { m -> return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.HasCounter(null, atLeast = 1)) }
        // tla chunk 03: "creature with no counters on it"
        WITH_NO_COUNTERS.matchEntire(phrase)?.let { m -> return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.Not(ObjectFilter.HasCounter(null, atLeast = 1))) }
        // chunk 21: "creatures you control with +1/+1 counters on them"
        WITH_COUNTERS.matchEntire(phrase)?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.HasCounter(kind, atLeast = 1))
        }
        // chunk 47: "creatures with decayed": a keyword ability (CR 702)
        // fdn chunk 22: not "an instant card or a card with flash": the alternative after " or a" has its own noun, the " or " split owns it
        WITH_KEYWORD.matchEntire(phrase)?.takeUnless { OR_AN_ALTERNATIVE.containsMatchIn(it.groupValues[1]) }?.let { m ->
            OracleParser.keyword(m.groupValues[2])?.let { keyword -> return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.HasKeyword(keyword.kind)) }
            // mkm chunk 07: "creatures you control with disguise": a keyword that has a cost, named without it (CR 702.168a)
            KEYWORDS_NAMED_WITHOUT_COST[m.groupValues[2]]?.let { kind -> return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.HasKeyword(kind)) }
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
            // blb gaps 2: "… or greater"
            val comparison = when (m.groupValues[3]) { "" -> arcanum.api.Comparison.Equal; " or greater" -> arcanum.api.Comparison.AtLeast; else -> arcanum.api.Comparison.AtMost }
            return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.ManaValue(comparison, value))
        }
        WITH_POWER_OR_TOUGHNESS_AT_MOST.matchEntire(phrase)?.let { m ->
            val n = Value.Fixed(m.groupValues[2].toInt())
            // blb chunk 10: "… or greater"
            val c = if (m.groupValues[3] == "greater") arcanum.api.Comparison.AtLeast else arcanum.api.Comparison.AtMost
            return and(noun(m.groupValues[1]) ?: return null, ObjectFilter.Or(listOf(ObjectFilter.Power(c, n), ObjectFilter.Toughness(c, n))))
        }
        // blb chunk 10: "attacking, blocking, or tapped creature": statuses as alternatives, the noun for all of them
        STATUS_ALTERNATIVES.matchEntire(phrase)?.let { m ->
            val statuses = m.groupValues[1].split(LazyRegexes.OR_LIST).map { word(it) ?: return null }
            return all(ObjectFilter.Or(statuses), noun(m.groupValues[2]) ?: return null)
        }
        // mkm chunk 01: "permanent you own but don't control"
        Mkm.OWNED_NOT_CONTROLLED.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.OwnedBy(PlayerFilter.You), ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))
        }
        // dsk chunk 09: "creatures and/or enchantments you own"
        OWNED.matchEntire(phrase)?.let { m -> noun(m.groupValues[1].replace(" and/or ", " or "))?.let { return and(it, ObjectFilter.OwnedBy(PlayerFilter.You)) } }
        CONTROLLED.matchEntire(phrase)?.let { m ->
            val who = when (m.groupValues[2]) {
                "you control" -> PlayerFilter.You
                "an opponent controls" -> PlayerFilter.Opponent
                "you don't control" -> PlayerFilter.Not(PlayerFilter.You) // chunk 33
                "your opponents control" -> PlayerFilter.Opponent // dsk chunk 06
                "that player controls" -> PlayerFilter.Is(Players.EventPlayer) // dsk chunk 07: in a trigger, the event's player
                // dsk chunk 09: in an attack trigger of this creature (CR 506.2)
                "defending player controls" -> PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))
                // isd chunk 03: the player a Curse enchants (CR 303.4e)
                "enchanted player controls" -> PlayerFilter.Is(Players.AttachedTo(Objects.Self))
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
            val colors = m.groupValues[1].split(LazyRegexes.OR_LIST).map { ObjectFilter.OfColor(COLORS[it] ?: return null) }
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
        WITH_KEYWORDS.matchEntire(phrase)?.takeUnless { OR_AN_ALTERNATIVE.containsMatchIn(it.groupValues[1]) }?.let { m ->
            val keywords = m.groupValues[2].split(LazyRegexes.OR_LIST).map { OracleParser.keyword(it) }
            if (keywords.all { it != null }) {
                val has = keywords.map { ObjectFilter.HasKeyword(it!!.kind) }
                return all(noun(m.groupValues[1]) ?: return null, has.singleOrNull() ?: ObjectFilter.Or(has))
            }
        }
        // inr chunk 10: "attacking Wolf or Werewolf": a leading status or colour describes every creature-type alternative
        if (phrase.contains(" or ")) {
            val list = phrase.split(LazyRegexes.OR_LIST).filter { it.isNotEmpty() }.map { it.removeArticle() }
            val first = list.first().split(' ')
            val modifiers = first.dropLast(1)
            if (list.size > 1 && modifiers.isNotEmpty() && modifiers.all { it[0].isLowerCase() && it !in CARD_TYPES } &&
                (listOf(first.last()) + list.drop(1)).all { it in RulesWords.subtypes && it[0].isUpperCase() })
                return all(ObjectFilter.Or((listOf(first.last()) + list.drop(1)).map { ObjectFilter.Sub(Subtype(it)) }),
                    *modifiers.map { word(it) ?: return null }.toTypedArray())
        }
        // inr chunk 07: "Angel or Human creature card": the creature types are the alternatives, the rest describes both (CR 205.3m)
        if (phrase.contains(" or ")) {
            val list = phrase.split(LazyRegexes.OR_LIST).filter { it.isNotEmpty() }
            val last = list.last().split(' ')
            if (list.size > 1 && last.size > 1 && singular(last[1]) in CARD_TYPES && (list.dropLast(1).map { it.removeArticle() } + last.first()).all { it in RulesWords.subtypes && it[0].isUpperCase() })
                return all(ObjectFilter.Or((list.dropLast(1).map { it.removeArticle() } + last.first()).map { ObjectFilter.Sub(Subtype(it)) }),
                    noun(last.drop(1).joinToString(" ")) ?: return null)
        }
        // "artifact, enchantment, or nonbasic land": alternatives. "nonartifact, nonland card": all of them.
        if (phrase.contains(" or ")) {
            val list = phrase.split(LazyRegexes.OR_LIST).filter { it.isNotEmpty() }
            return ObjectFilter.Or(list.map { noun(it.removeArticle()) ?: return null })
        }
        // blb chunk 08: "Rabbits, Bats, Birds, and Mice": a list of creature types is any of them (CR 205.3m)
        if (phrase.contains(", and ")) {
            val items = phrase.split(LazyRegexes.AND_LIST).map { singular(it) }
            if (items.all { it in RulesWords.subtypes }) return ObjectFilter.Or(items.map { ObjectFilter.Sub(Subtype(it)) })
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

    /** gtc: the Gatecrash and Dragon's Maze noun phrases. */
    private fun gtcNoun(phrase: String): ObjectFilter? {
        // gtc chunk 03: "artifacts and creatures your opponents control": each object of either type
        GTC_TYPES_AND.matchEntire(phrase)?.let { m ->
            val a = CARD_TYPES[m.groupValues[1]]; val b = CARD_TYPES[m.groupValues[2]]
            // inr chunk 09: "creatures and nonbasic lands your opponents control" (the second type with a supertype negated)
            val bFilter = b?.let { ObjectFilter.Type(it) } ?: m.groupValues[2].takeIf { it.startsWith("non") && ' ' in it }?.let { noun(it) }
            if (a != null && bFilter != null) {
                val either = ObjectFilter.Or(listOf(ObjectFilter.Type(a), bFilter))
                return if (m.groupValues[3].isEmpty()) either else all(either, noun("permanents" + m.groupValues[3])?.let { p ->
                    (p as? ObjectFilter.And)?.all?.firstOrNull { it is ObjectFilter.ControlledBy } } ?: return null)
            }
        }
        // dgm: "instant and sorcery" (cards of either type, "instant and sorcery spells")
        GTC_TYPE_AND_TYPE.matchEntire(phrase)?.let { m ->
            val a = CARD_TYPES[m.groupValues[1]]; val b = CARD_TYPES[m.groupValues[2]]
            if (a != null && b != null) return ObjectFilter.Or(listOf(ObjectFilter.Type(a), ObjectFilter.Type(b)))
        }
        // gtc chunk 08: "creature cards in your opponents' graveyards", "cards in your opponents' graveyards"
        GTC_IN_OPPONENTS_GRAVEYARDS.matchEntire(phrase)?.let { m ->
            val kind = if (m.groupValues[1].isEmpty()) ObjectFilter.Category(arcanum.api.ObjectCategory.Card) else noun(m.groupValues[1] + " card") ?: return null
            return all(kind, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent))
        }
        // gtc chunk 10: "creatures with no +1/+1 counters on them"
        GTC_WITH_NO_COUNTERS.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.Not(ObjectFilter.HasCounter(Counters.kind(m.groupValues[2]) ?: return null, atLeast = 1)))
        }
        // gtc chunk 08: "creature with power X or less" (X announced, CR 107.3a)
        GTC_WITH_POWER_X.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.Power(Comparison.AtMost, Value.Announced(name = "X")))
        }
        // gtc chunk 11: "creature that dealt damage this turn": the source of damage this turn
        GTC_DEALT_DAMAGE_THIS_TURN.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null,
                ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, arcanum.api.RecipientFilter()), role = arcanum.api.EventRole.Source))
        }
        // gtc chunk 04: "creature with power less than or equal to the number of +1/+1 counters removed this way"
        GTC_WITH_POWER_AT_MOST_VALUE.matchEntire(phrase)?.let { m ->
            return all(noun(m.groupValues[1]) ?: return null, ObjectFilter.Power(Comparison.AtMost, Values.value(m.groupValues[2]) ?: return null))
        }
        // dgm: "creatures named Biovisionary" (CR 201.2)
        GTC_NAMED.matchEntire(phrase)?.let { m ->
            val named = all(noun(m.groupValues[1]) ?: return null, ObjectFilter.Named(m.groupValues[2]))
            return if (m.groupValues[3].isEmpty()) named else all(named, ObjectFilter.ControlledBy(PlayerFilter.You))
        }
        return null
    }

    // gtc
    private val GTC_TYPE_AND_TYPE = Regex("""([a-z]+) and ([a-z]+)""")
    private val GTC_TYPES_AND = Regex("""([a-z]+)s and ((?:non[a-z]+ )?[a-z]+)s(| you control| your opponents control| an opponent controls)""")
    private val GTC_IN_OPPONENTS_GRAVEYARDS = Regex("""(?:(.+) )?cards? in your opponents' graveyards""")
    private val GTC_WITH_NO_COUNTERS = Regex("""(.+) with no (\S+) counters on (?:them|it)""")
    private val GTC_WITH_POWER_X = Regex("""(.+) with power X or less""")
    private val GTC_WITH_POWER_AT_MOST_VALUE = Regex("""(.+) with power less than or equal to (.+)""")
    private val GTC_DEALT_DAMAGE_THIS_TURN = Regex("""(.+) that dealt damage this turn""")
    private val GTC_NAMED = Regex("""((?:other )?(?:creatures?|permanents?)(?: you control)?) named (.+?)( you control)?""")

    /** "creature you control" → every such object; "card named [this card] in each graveyard". */
    fun objects(phrase: String, self: Set<String> = emptySet()): Objects? = noun(phrase)?.let { Objects.Each(it) }

    /** The name of a target slot: its noun ("nonbasic land" → "land"), "permanent" for a list of permanent types. */
    fun slotName(phrase: String): String {
        // chunk 21: "permanent an opponent controls with mana value 2 or less": drop the "with" part first
        // blb chunk 06: "… that was dealt damage this turn" is not part of the name; blb chunk 10: nor statuses as alternatives
        val core = (STATUS_ALTERNATIVES.matchEntire(phrase)?.groupValues?.get(2) ?: phrase).substringBefore(" that was ").substringBefore(" that targets ").substringBefore(" that crewed ") /* tmt chunks 09, 14 */.substringBefore(" that dealt ").substringBefore(" that attacked ").substringBefore(" that entered ").substringBefore(" without ").substringBefore(" with ")
            .let { Mkm.OWNED_NOT_CONTROLLED.matchEntire(it)?.groupValues?.get(1) ?: it } // mkm chunk 01
            .let { CONTROLLED.matchEntire(it)?.groupValues?.get(1) ?: it }
            .substringBefore(" in your graveyard").substringBefore(" from a graveyard").substringBefore(" in a graveyard") // chunk 31, mkm chunk 09
        // chunk 31: "instant or sorcery card" is a card, "instant or sorcery spell" a spell
        if ((core.contains(" or ") || core.contains(", ")) && core.split(' ').last() in setOf("card", "spell")) return core.split(' ').last()
        // inr chunk 07: "spell, activated ability, or triggered ability" is named for the spell, as "…, or noncreature spell" is
        if (core.contains(", ") && core.endsWith(" triggered ability") && core.split(", ").first().endsWith("spell")) return "spell"
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
            "blocking" -> return ObjectFilter.HasStatus(arcanum.api.Status.Blocking) // blb chunk 10
            // dsk chunk 06: "a face-down permanent" (CR 708)
            "face-down" -> return ObjectFilter.HasStatus(arcanum.api.Status.FaceDown)
            // mkm chunk 07: "face-up creatures you control with disguise" (CR 708)
            "face-up" -> return ObjectFilter.Not(ObjectFilter.HasStatus(arcanum.api.Status.FaceDown))
            "untapped" -> return ObjectFilter.Not(ObjectFilter.HasStatus(arcanum.api.Status.Tapped))
            // rtr chunk 05: "monocolored creature", "multicolored spell" (CR 105.2)
            "monocolored" -> return ObjectFilter.Monocolored
            "multicolored" -> return ObjectFilter.Multicolored
            // gtc chunk 06: "blocked creature" (CR 509.1h)
            "blocked" -> return ObjectFilter.HasStatus(arcanum.api.Status.Blocked)
            // mkm chunk 04: "suspected creatures" (CR 701.60a)
            "suspected" -> return ObjectFilter.HasStatus(arcanum.api.Status.Marked(arcanum.api.ObjectMark.Suspected))
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
        // mkm chunk 10: "each of your opponents"
        "Each opponent", "each opponent", "each of your opponents" -> Players.Each(PlayerFilter.Opponent)
        "You", "you" -> Players.You
        "Each player", "each player" -> Players.Each(PlayerFilter.Any)
        else -> null
    }

    private val NAMED = Regex("""card named (.+?)( in each graveyard)?""")
    // dsk chunk 08
    private val TARGETS_ONLY_A_SINGLE = Regex("""(.+ spell) that targets only a single (.+)""")
    // dsk chunk 07
    private val PLURAL_SUBTYPES_AND = Regex("""([A-Z][a-z]+s) and ([A-Z][a-z]+s)""")
    // dsk chunk 01
    private val WITHOUT_A_NAME = Regex("""(.+) that (?:don't|doesn't) have a name""")
    private val NOT_SAME_NAME_AS = Regex("""(.+) that doesn't have the same name as (.+)""")
    // (also "Kithkin or Soldier spells you cast": "you cast" belongs to every alternative, CR 601.2f)
    private val SPELLS_YOU_CAST = Regex("""([\w-]+(?:(?:,| or|, or) [\w-]+)*) spells you cast""")
    private val WITH_POWER_OR_GREATER = Regex("""(.+) with (power|toughness) (\d+) or greater""")
    // chunk 26
    private val COLOR_ALTERNATIVES = Regex("""((?:white|blue|black|red|green)(?:, (?:white|blue|black|red|green))*,? or (?:white|blue|black|red|green)) (.+)""")
    private val WITH_MANA_VALUE = Regex("""(.+) with mana value (\d+|X)( or less| or greater)?""")
    // blb gaps 2
    private val SPELLS_OF_TYPES_YOU_CAST = Regex("""(\w+) and (\w+) spells you cast""")
    private val WITH_X_IN_MANA_COST = Regex("""(.+) with \{X\} in (?:its|their) mana costs?""")
    private val WITH_POWER_OR_TOUGHNESS_AT_MOST = Regex("""(.+) with power or toughness (\d+) or (less|greater)""")
    // blb chunk 10
    private val STATUS_ALTERNATIVES = Regex("""((?:attacking|blocking|tapped|untapped)(?:(?:, |,? or )(?:attacking|blocking|tapped|untapped))+) (.+)""")
    private val ON_THE_BATTLEFIELD = Regex("""(.+) on the battlefield""")
    private val CONTROLLED = Regex("""(.+) (you control|an opponent controls|you don't control|your opponents control|that player controls|defending player controls|enchanted player controls)""")
    // dsk chunk 09
    private val OWNED = Regex("""(.+) you own""")
    private val WITH_BASIC_LAND_TYPE = Regex("""(.+) with a basic land type""")
    private val COLORS = mapOf("white" to Color.White, "blue" to Color.Blue, "black" to Color.Black, "red" to Color.Red, "green" to Color.Green)
    val BASIC_LAND_TYPES = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")
    /** Plural creature types as printed ("Elves you control"); regular plurals are only read where the text needs them. */
    private val IRREGULAR_PLURALS = mapOf("Elves" to "Elf", "Dwarves" to "Dwarf", "Wolves" to "Wolf", "Goblins" to "Goblin",
        "Zombies" to "Zombie", "Humans" to "Human", "Spirits" to "Spirit", "Merfolk" to "Merfolk", "Soldiers" to "Soldier",
        // blb chunk 04
        "Mice" to "Mouse",
        // msh
        "Heroes" to "Hero",
        // tla chunk 02
        "Allies" to "Ally",
        // inr chunk 01
        "Werewolves" to "Werewolf")
    // chunk 31
    private val IN_YOUR_GRAVEYARD = Regex("""(.+) in your graveyard""")
    private val FROM_A_GRAVEYARD = Regex("""(.+) (?:from|in) a graveyard""") // mkm chunk 09: "nonland card in a graveyard"
    // chunk 32
    private val WITH_POWER_OR_TOUGHNESS = Regex("""(.+) with (power|toughness) (\d+) or (less|greater)""")
    private val SINGULAR_NOUNS = setOf("land", "creature", "artifact", "enchantment", "planeswalker", "instant", "sorcery", "card",
        "permanent", "spell", "token")
    val CARD_TYPES = mapOf<String, CardType>("artifact" to CardType.Artifact, "creature" to CardType.Creature, "enchantment" to CardType.Enchantment,
        "instant" to CardType.Instant, "land" to CardType.Land, "planeswalker" to CardType.Planeswalker, "sorcery" to CardType.Sorcery,
        "battle" to CardType.Battle)
    private val SUPERTYPES = mapOf<String, Supertype>("basic" to Supertype.Basic, "legendary" to Supertype.Legendary, "snow" to Supertype.Snow)
    // chunk 14
    private val OR_SPELL = Regex("""(\w+ or \w+) spells?""")
    private val PERMANENT_TYPES = listOf<CardType>(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land, CardType.Planeswalker)
    // chunk 20
    private val WITH_POWER_TOUGHNESS = Regex("""(\d+)/(\d+) (.+)""")
    // chunk 21
    private val WITH_NO_COUNTERS = Regex("""(.+) with no counters on (?:it|them)""") // tla chunk 03
    private val WITH_COUNTERS = Regex("""(.+) with (?:an? )?(\S+) counters? on (?:it|them)""")
    // blb chunk 12
    private val OF_CHOSEN_TYPE = Regex("""(.+) of the chosen type""")
    private val OF_CHOSEN_COLOR = Regex("""(.+) of the chosen color""")
    private val THATS_A_OR_A = Regex("""(.+) that's an? (\w+) or an? (\w+)""")
    // blb chunk 11
    private val TOUGHNESS_GREATER_THAN_POWER = Regex("""(.+) with toughness greater than its power""")
    // blb chunk 09
    private val OWN_IN_EXILE_AND_GRAVEYARD = Regex("""(.+) you own in exile and in your graveyard""")
    // blb chunk 08
    private val WITHOUT_KEYWORD = Regex("""(.+) without ([a-z ]+)""")
    private val ENTERED_THIS_TURN = Regex("""(.+) that entered the battlefield this turn""")
    // rtr chunk 05
    private val DEALT_DAMAGE_THIS_TURN_AS_SOURCE = Regex("""(.+) that dealt damage this turn""")
    // blb chunk 06
    private val DEALT_DAMAGE_THIS_TURN = Regex("""(.+) that was dealt damage this turn""")
    // blb chunk 05
    private val WITH_ANY_COUNTERS = Regex("""(.+) with counters on (?:it|them)""")
    private val EQUIPPED_PLURAL = Regex("""(equipped|enchanted) (creatures.*)""")
    // chunk 40
    private val OTHER = Regex("""(?:an)?other (.+)""")
    /** Keywords with a cost, as a filter names them ("with disguise"). */
    private val KEYWORDS_NAMED_WITHOUT_COST: Map<String, arcanum.api.KeywordKind> = mapOf("disguise" to arcanum.api.Keyword.Disguise.Kind)
    private val OTHER_THAN_ATTACHED = Regex("""(.+) other than (?:enchanted|equipped) creature""")
    // chunk 42
    private val QUALIFIED_PERMANENT_CARD = Regex("""(.+) permanent cards?""")
    // chunk 36
    private val OR_AN_ALTERNATIVE = Regex(" or an? ")
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
        MechanicWordings.events(text, self)?.let { return it }
        EoePhrases.events(text, self)?.let { return it } // eoe
        EclPhrases.events(text, self)?.let { return it } // ecl
        FraPhrases.events(text, self)?.let { return it } // fra
        // blb chunk 03: "this artifact enters and when you sacrifice it" (only its controller sacrifices it, CR 701.21a)
        ENTERS_AND_WHEN_YOU_SACRIFICE_IT.matchEntire(text)?.let { m ->
            val subject = subject(m.groupValues[1], self) ?: return null
            return listOf(EventPattern.ZoneChange(subject, to = Zone.Battlefield), EventPattern.Sacrifice(subject))
        }
        // blb chunk 07: "you gain or lose life" (one ability, two events)
        if (text == "you gain or lose life") return listOf(EventPattern.GainLife(PlayerFilter.You), EventPattern.LoseLife(PlayerFilter.You))
        // blb chunk 04: "this enchantment enters and whenever you expend 4"
        ENTERS_AND_WHENEVER.matchEntire(text)?.let { m ->
            val subject = subject(m.groupValues[1], self) ?: return null
            return listOf(EventPattern.ZoneChange(subject, to = Zone.Battlefield), event(m.groupValues[2], self) ?: return null)
        }
        // dsk chunk 01: "an enchantment you control enters and whenever you fully unlock a Room": one ability, two events
        if (text.contains(" and whenever ")) return text.split(" and whenever ").flatMap { events(it, self) ?: return null }
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
        // mkm chunk 01: "this creature enters or is turned face up" (CR 702.168d: turning it face up isn't entering)
        Mkm.ENTERS_OR_TURNED_FACE_UP.matchEntire(text)?.let { m ->
            val subject = subject(m.groupValues[1], self) ?: return null
            return listOf(EventPattern.ZoneChange(subject, to = Zone.Battlefield), EventPattern.TurnedFaceUp(subject))
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
        ActionWordings.event(text) { subject(it, self) }?.let { return it }
        MechanicWordings.event(text)?.let { return it }
        MechanicWordings.subjectEvent(text) { subject(it, self) }?.let { return it }
        EclPhrases.event(text, self)?.let { return it } // ecl
        FraPhrases.event(text, self)?.let { return it } // fra
        // gtc chunk 01: battalion, "this creature and at least two other creatures attack": declared as attackers together
        // (CR 508.1a)
        AND_AT_LEAST_OTHERS_ATTACK.matchEntire(text)?.let { m ->
            return EventPattern.Attack(subject(m.groupValues[1], self) ?: return null, withOthers = Numbers.count(m.groupValues[2]) ?: return null)
        }
        gtcEvent(text, self)?.let { return it }
        // blb chunk 11: "one or more other creatures you control leave the battlefield without dying" (CR 700.4: dying is
        // going to a graveyard from the battlefield)
        LEAVES_WITHOUT_DYING.matchEntire(text)?.let { m ->
            return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, from = Zone.Battlefield, notTo = Zone.Graveyard)
        }
        // blb chunk 11: "this creature deals damage to an opponent"
        DEALS_DAMAGE_TO_AN_OPPONENT.matchEntire(text)?.let { m ->
            return EventPattern.Damage(subject(m.groupValues[1], self) ?: return null, arcanum.api.RecipientFilter(players = PlayerFilter.Opponent))
        }
        // dsk chunk 12: "this creature becomes the target of a spell or ability an opponent controls"
        BECOMES_TARGET_OF_OPPONENTS.matchEntire(text)?.let { m ->
            return EventPattern.BecomesTarget(subject(m.groupValues[1], self) ?: return null, ObjectFilter.ControlledBy(PlayerFilter.Opponent))
        }
        // dsk chunk 12: "one or more land cards are put into your graveyard from your library" (a batch)
        PUT_INTO_YOUR_GRAVEYARD_FROM_LIBRARY.matchEntire(text)?.let { m ->
            val what = m.groupValues[1].ifEmpty { m.groupValues[3] }
            val owner = if (m.groupValues[2].ifEmpty { m.groupValues[4] } == "your") PlayerFilter.You else PlayerFilter.Opponent
            return EventPattern.ZoneChange(Filters.all(subject(what, self) ?: return null, ObjectFilter.OwnedBy(owner)),
                from = Zone.Library, to = Zone.Graveyard)
        }
        // dsk chunk 11: "a creature you control becomes blocked" (once per combat each, CR 509.3c)
        BECOMES_BLOCKED.matchEntire(text)?.let { m -> return EventPattern.BecomesBlocked(subject(m.groupValues[1], self) ?: return null, ObjectFilter.Any) }
        // dsk chunk 11: "one or more creatures die" (a batch), "an opponent draws a card"
        DIE_PLURAL.matchEntire(text)?.let { m -> return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, from = Zone.Battlefield, to = Zone.Graveyard) }
        if (text == "an opponent draws a card") return EventPattern.Draw(PlayerFilter.Opponent)
        // dsk chunk 10: "enchanted creature is dealt damage", "enchanted player is dealt damage", "this creature is dealt damage"
        IS_DEALT_DAMAGE.matchEntire(text)?.let { m ->
            if (m.groupValues[1] == "enchanted player")
                return EventPattern.Damage(ObjectFilter.Any, arcanum.api.RecipientFilter(players = PlayerFilter.Is(Players.AttachedTo(Objects.Self))))
            return EventPattern.Damage(ObjectFilter.Any, arcanum.api.RecipientFilter(objects = subject(m.groupValues[1], self) ?: return null))
        }
        // dsk chunk 08: "you cast an instant or sorcery spell that targets only a single creature you control", "you gain life",
        // "you lose life"
        YOU_CAST_SPELL_THAT.matchEntire(text)?.let { m -> return EventPattern.Cast(Filters.noun(m.groupValues[1]) ?: return null, by = PlayerFilter.You) }
        // blb gaps 2: "you cast a creature spell with mana value 4 or greater" (the event's object is a spell: the filter names its kind)
        YOU_CAST_SPELL_WITH.matchEntire(text)?.let { m -> return EventPattern.Cast(Filters.noun(m.groupValues[1] + " " + m.groupValues[2]) ?: return null, by = PlayerFilter.You) }
        if (text == "you gain life") return EventPattern.GainLife(PlayerFilter.You)
        if (text == "you lose life") return EventPattern.LoseLife(PlayerFilter.You)
        // dsk chunk 07: "a source you control deals noncombat damage to an opponent"
        SOURCE_DEALS_DAMAGE_TO.matchEntire(text)?.let { m ->
            val source = if (m.groupValues[1] == "a source you control") ObjectFilter.ControlledBy(PlayerFilter.You) else subject(m.groupValues[1], self) ?: return null
            val combat = when (m.groupValues[2]) { "noncombat " -> false; "combat " -> true; else -> null }
            val to = when (m.groupValues[3]) {
                "an opponent" -> arcanum.api.RecipientFilter(players = PlayerFilter.Opponent)
                "a player" -> arcanum.api.RecipientFilter(players = PlayerFilter.Any)
                "you" -> arcanum.api.RecipientFilter(players = PlayerFilter.You)
                else -> return null
            }
            return EventPattern.Damage(source, to, combat = combat)
        }
        // dsk chunk 06: "this creature or another permanent you control is turned face up", "you turn a permanent face up"
        // (CR 708.8: its controller turns it), "you sacrifice this enchantment"
        IS_TURNED_FACE_UP.matchEntire(text)?.let { m -> return EventPattern.TurnedFaceUp(subject(m.groupValues[1], self) ?: return null) }
        YOU_TURN_FACE_UP.matchEntire(text)?.let { m ->
            return EventPattern.TurnedFaceUp(Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.ControlledBy(PlayerFilter.You)))
        }
        YOU_SACRIFICE_SELF.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let { return EventPattern.Sacrifice(ObjectFilter.Is(Objects.Self)) }
        // dsk chunk 05: "you manifest dread" (CR 701.62b); "the beginning of each opponent's upkeep"
        if (text == "you manifest dread") return EventPattern.ManifestDread(PlayerFilter.You)
        if (text == "the beginning of each opponent's upkeep") return EventPattern.StepBegins(arcanum.api.TurnStep.Upkeep, PlayerFilter.Opponent)
        // dsk chunk 01: unlocking a door of a Room (CR 709.5h, i); only its controller unlocks it
        if (text == "you unlock this door") return EventPattern.Unlock(ObjectFilter.Is(Objects.Self), thisDoor = true)
        YOU_FULLY_UNLOCK.matchEntire(text)?.let { m ->
            return EventPattern.Unlock(Filters.noun(m.groupValues[1] + " you control") ?: return null, fully = true)
        }
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
        // mkm chunk 10: "an opponent sacrifices an artifact" (only a permanent's controller sacrifices it, CR 701.21a)
        PLAYER_SACRIFICES.matchEntire(text)?.let { m ->
            val kind = Filters.noun(m.groupValues[2]) ?: return null
            return EventPattern.Sacrifice(if (m.groupValues[1] == "a player") kind else Filters.all(kind, ObjectFilter.ControlledBy(PlayerFilter.Opponent)))
        }
        ENTERS.matchEntire(text)?.let { m -> return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, to = Zone.Battlefield) }
        PUT_INTO_GRAVEYARD_FROM_BATTLEFIELD.matchEntire(text)?.let { m ->
            return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, from = Zone.Battlefield, to = Zone.Graveyard)
        }
        // chunk 12
        // "you cast a creature spell": the event's object is always a spell, so the filter names only its kind.
        // fdn chunk 11c: "you cast a spell of the chosen color" (the color an as-enters choice remembered)
        YOU_CAST_OF_CHOSEN.matchEntire(text)?.let { m ->
            return EventPattern.Cast(spell = Filters.noun(m.groupValues[1]) ?: return null, by = PlayerFilter.You)
        }
        // inr chunk 09: "you cast another Vampire spell" (not this card itself, CR 109.1)
        YOU_CAST_ANOTHER.matchEntire(text)?.let { m ->
            return EventPattern.Cast(spell = Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), Filters.noun(m.groupValues[1]) ?: return null), by = PlayerFilter.You)
        }
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
        // isd chunk 03: "a creature deals combat damage to enchanted player" (a Curse's player, CR 303.4e)
        DEALS_COMBAT_DAMAGE_TO_A_PLAYER.matchEntire(text)?.let { m ->
            val to = if (m.groupValues[2] == "enchanted player") PlayerFilter.Is(Players.AttachedTo(Objects.Self)) else PlayerFilter.Any
            return EventPattern.Damage(subject(m.groupValues[1], self) ?: return null, arcanum.api.RecipientFilter(players = to), combat = true)
        }
        // chunk 25: "you play a land" (CR 305.1)
        if (text == "you play a land") return EventPattern.PlayLand(players = PlayerFilter.You, lands = ObjectFilter.Any)
        // chunk 14: "a player casts an instant or sorcery spell", "a player taps an Island for mana", "an opponent gains life"
        PLAYER_CASTS.matchEntire(text)?.let { m ->
            val kind = m.groupValues[1].takeIf { it.isNotEmpty() }?.let { Filters.noun(it) ?: return null } ?: ObjectFilter.Any
            return EventPattern.Cast(kind, by = PlayerFilter.Any)
        }
        PLAYER_TAPS_FOR_MANA.matchEntire(text)?.let { m -> return EventPattern.Tap(Filters.noun(m.groupValues[1].removeArticle()) ?: return null, forMana = true) }
        // dsk chunk 02: "a creature you control attacks alone" (CR 506.5)
        ATTACKS_ALONE.matchEntire(text)?.let { m -> return EventPattern.Attack(subject(m.groupValues[1], self) ?: return null, alone = true) }
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
        // blb chunk 05: "this Class becomes level 2" (CR 716.2a)
        BECOMES_LEVEL.matchEntire(text)?.let { m -> return EventPattern.BecomesLevel(subject(m.groupValues[1], self) ?: return null, m.groupValues[2].toInt()) }
        // blb chunk 05: "one or more creatures you control die"
        ONE_OR_MORE_DIE.matchEntire(text)?.let { m ->
            return EventPattern.ZoneChange(Filters.noun(m.groupValues[1]) ?: return null, from = Zone.Battlefield, to = Zone.Graveyard)
        }
        // blb chunk 04: "you expend 4" (CR 700.14)
        YOU_EXPEND.matchEntire(text)?.let { m -> return EventPattern.Expend(m.groupValues[1].toInt(), PlayerFilter.You) }
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
        // blb chunk 05: "the beginning of each opponent's upkeep"
        // fdn chunk 11c: "the beginning of each player's draw step" (every turn's step, CR 503.1)
        BEGINNING_OF_EACH_PLAYERS.matchEntire(text)?.let { m -> return EventPattern.StepBegins(STEPS[m.groupValues[1]] ?: return null, whose = PlayerFilter.Any) }
        BEGINNING_OF_EACH_OPPONENTS.matchEntire(text)?.let { m -> return EventPattern.StepBegins(STEPS[m.groupValues[1]] ?: return null, whose = PlayerFilter.Opponent) }
        // chunk 52: "a creature is exiled from the battlefield"
        EXILED_FROM_THE_BATTLEFIELD.matchEntire(text)?.let { m ->
            return EventPattern.ZoneChange(subject(m.groupValues[1], self) ?: return null, from = Zone.Battlefield, to = Zone.Exile)
        }
        // chunk 56: "this creature transforms into [its back face's name]": it transforms and then has that name (CR 701.27e)
        TRANSFORMS_INTO.matchEntire(text)?.let { m ->
            if (m.groupValues[2] !in self) return null
            return EventPattern.Transform(Filters.all(subject(m.groupValues[1], self) ?: return null, ObjectFilter.Named(m.groupValues[2])))
        }
        // inr chunk 05: "equipped creature transforms" (CR 701.27a)
        TRANSFORMS.matchEntire(text)?.let { m -> return EventPattern.Transform(subject(m.groupValues[1], self) ?: return null) }
        return null
    }

    /** gtc: the Gatecrash and Dragon's Maze trigger events. */
    private fun gtcEvent(text: String, self: Set<String>): EventPattern? {
        // gtc chunk 06: "this creature blocks" (once per combat, CR 509.3a); "a creature blocks"
        GTC_BLOCKS.matchEntire(text)?.let { m -> return EventPattern.Block(subject(m.groupValues[1], self) ?: return null, attacker = ObjectFilter.Any) }
        // gtc chunk 04: "a +1/+1 counter is put on this creature" (one event per counter put, CR 122.6); "this creature evolves"
        // (CR 702.100b: +1/+1 counters put on it by its evolve ability resolving)
        GTC_COUNTER_PUT_ON.matchEntire(text)?.let { m ->
            return EventPattern.CountersPut(subject(m.groupValues[2], self) ?: return null, Counters.kind(m.groupValues[1]) ?: return null)
        }
        GTC_EVOLVES.matchEntire(text)?.let { m ->
            return EventPattern.CountersPut(subject(m.groupValues[1], self) ?: return null, arcanum.api.CounterKind.PowerToughness(1, 1),
                causedBy = ObjectFilter.AbilityOf(keyword = arcanum.api.Keyword.Evolve))
        }
        // gtc chunk 03: "you tap a Swamp for mana", "you tap this creature for mana" (only a permanent's controller taps it for mana)
        GTC_YOU_TAP_FOR_MANA.matchEntire(text)?.let { m ->
            val what = if (m.groupValues[1] in self || m.groupValues[1].replaceFirstChar { it.lowercase() } in self) ObjectFilter.Is(Objects.Self)
                else Filters.all(Filters.noun(m.groupValues[1].removeArticle()) ?: return null, ObjectFilter.ControlledBy(PlayerFilter.You))
            return EventPattern.Tap(what, forMana = true)
        }
        // gtc chunk 08: "enchanted land becomes tapped"; gtc chunk 10: "enchanted land is tapped for mana"
        GTC_BECOMES_TAPPED.matchEntire(text)?.let { m -> return EventPattern.Tap(subject(m.groupValues[1], self) ?: return null) }
        // fdn chunk 03: "equipped creature becomes untapped" (CR 701.26)
        Fdn.BECOMES_UNTAPPED.matchEntire(text)?.let { m -> return EventPattern.Untap(subject(m.groupValues[1], self) ?: return null) }
        GTC_IS_TAPPED_FOR_MANA.matchEntire(text)?.let { m -> return EventPattern.Tap(subject(m.groupValues[1], self) ?: return null, forMana = true) }
        // gtc chunk 07: "this creature deals combat damage" (to any player or permanent)
        GTC_DEALS_COMBAT_DAMAGE.matchEntire(text)?.let { m ->
            return EventPattern.Damage(subject(m.groupValues[1], self) ?: return null, arcanum.api.RecipientFilter(), combat = true)
        }
        // dgm: "a creature deals combat damage to you"
        GTC_DEALS_COMBAT_DAMAGE_TO_YOU.matchEntire(text)?.let { m ->
            return EventPattern.Damage(subject(m.groupValues[1], self) ?: return null, arcanum.api.RecipientFilter(players = PlayerFilter.You), combat = true)
        }
        // gtc chunk 15: "an ability of equipped creature is activated, if it isn't a mana ability" (mana abilities aren't on the
        // Stack, CR 605.3b, so they can't be copied)
        Gtc.ABILITY_OF_IS_ACTIVATED_NOT_MANA.matchEntire(text)?.let { m ->
            return EventPattern.Activate(subject(m.groupValues[1], self) ?: return null, by = PlayerFilter.Any, manaAbilities = false)
        }
        // dgm: "this creature attacks a player and isn't blocked" (CR 509.1h); "a player casts a spell from their hand"; "you cast an
        // instant or sorcery spell from your library" (CR 601.2a: from where it was)
        Gtc.ATTACKS_A_PLAYER_AND_ISNT_BLOCKED.matchEntire(text)?.let { m ->
            return EventPattern.AttacksUnblocked(subject(m.groupValues[1], self) ?: return null, target = arcanum.api.RecipientFilter(players = PlayerFilter.Any))
        }
        if (Gtc.PLAYER_CASTS_FROM_HAND.matches(text)) return EventPattern.Cast(ObjectFilter.Any, by = PlayerFilter.Any, from = Zone.Hand)
        Gtc.OPPONENT_CASTS.matchEntire(text)?.let { m ->
            val kind = m.groupValues[1].takeIf { it.isNotEmpty() }?.let { Filters.noun(it.trim()) ?: return null } ?: ObjectFilter.Any
            return EventPattern.Cast(kind, by = PlayerFilter.Opponent)
        }
        Gtc.YOU_CAST_FROM_YOUR_LIBRARY.matchEntire(text)?.let { m ->
            return EventPattern.Cast(Filters.noun(m.groupValues[1] + " spell") ?: return null, by = PlayerFilter.You, from = Zone.Library)
        }
        // gtc chunk 06: "the beginning of the end step", "the beginning of each combat": every player's (CR 513.1, 507.1)
        when (text) {
            "the beginning of the end step" -> return EventPattern.StepBegins(arcanum.api.TurnStep.End, PlayerFilter.Any)
            "the beginning of each combat" -> return EventPattern.StepBegins(arcanum.api.TurnStep.BeginningOfCombat, PlayerFilter.Any)
            // gtc chunk 11: the upkeep of the player who controls the enchanted creature
            "the beginning of the upkeep of enchanted creature's controller" ->
                return EventPattern.StepBegins(arcanum.api.TurnStep.Upkeep, PlayerFilter.Is(Players.ControllerOf(Objects.AttachedTo(Objects.Self))))
            // isd chunk 03: a Curse's enchanted player's upkeep (CR 303.4e: an Aura that enchants a player)
            "the beginning of enchanted player's upkeep" ->
                return EventPattern.StepBegins(arcanum.api.TurnStep.Upkeep, PlayerFilter.Is(Players.AttachedTo(Objects.Self)))
        }
        // gtc chunk 11: "a creature card is put into an opponent's graveyard from anywhere": a card goes to its owner's graveyard
        GTC_PUT_INTO_OPPONENTS_GRAVEYARD.matchEntire(text)?.let { m ->
            val cards = if (m.groupValues[1] == "a card") ObjectFilter.Category(arcanum.api.ObjectCategory.Card)
                else Filters.all(subject(m.groupValues[1], self) ?: return null, ObjectFilter.Category(arcanum.api.ObjectCategory.Card))
            return EventPattern.ZoneChange(Filters.all(cards, ObjectFilter.OwnedBy(PlayerFilter.Opponent)), to = Zone.Graveyard)
        }
        // dgm: "an instant or sorcery spell you control deals damage"
        GTC_SPELL_DEALS_DAMAGE.matchEntire(text)?.let { m ->
            return EventPattern.Damage(Filters.noun(m.groupValues[1]) ?: return null, arcanum.api.RecipientFilter())
        }
        return null
    }

    // gtc
    private val GTC_BLOCKS = Regex("""(.+) blocks""")
    private val GTC_COUNTER_PUT_ON = Regex("""an? (\S+) counter is put on (.+)""")
    private val GTC_EVOLVES = Regex("""(.+) evolves""")
    private val GTC_YOU_TAP_FOR_MANA = Regex("""you tap (.+) for mana""")
    private val GTC_BECOMES_TAPPED = Regex("""(.+) becomes tapped""")
    private val GTC_IS_TAPPED_FOR_MANA = Regex("""(.+) is tapped for mana""")
    private val GTC_DEALS_COMBAT_DAMAGE = Regex("""(.+) deals combat damage""")
    private val GTC_DEALS_COMBAT_DAMAGE_TO_YOU = Regex("""(.+) deals combat damage to you""")
    private val GTC_PUT_INTO_OPPONENTS_GRAVEYARD = Regex("""(.+) is put into an opponent's graveyard from anywhere""")
    private val GTC_SPELL_DEALS_DAMAGE = Regex("""an? (.+ spell you control) deals damage""")

    /** "this land", "this land or another land you control", "equipped creature", "a nontoken creature". */
    /** ecl: [subject] for the set's phrases ([EclPhrases]). */
    fun subjectOf(text: String, self: Set<String>): ObjectFilter? = subject(text, self)

    private fun subject(text: String, self: Set<String>): ObjectFilter? {
        if (text in self || text.replaceFirstChar { it.lowercase() } in self) return ObjectFilter.Is(Objects.Self)
        // chunk 20
        if (text == "equipped creature" || text == "enchanted creature") return ObjectFilter.Is(Objects.AttachedTo(of = Objects.Self))
        // CR 701.54e
        if (text == "your Ring-bearer" || text == "Your Ring-bearer") return MechanicWordings.yourRingBearer
        // gtc chunk 08
        if (text == "enchanted land") return ObjectFilter.Is(Objects.AttachedTo(of = Objects.Self))
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
    // gtc chunk 01
    private val AND_AT_LEAST_OTHERS_ATTACK = Regex("""(.+?) and at least (\w+) other creatures attack""")
    // blb chunk 11
    private val LEAVES_WITHOUT_DYING = Regex("""(.+) leaves? the battlefield without dying""")
    private val DEALS_DAMAGE_TO_AN_OPPONENT = Regex("""(.+) deals damage to an opponent""")
    // dsk chunk 01
    private val YOU_FULLY_UNLOCK = Regex("""you fully unlock an? (\w+)""")
    // dsk chunk 11
    private val DIE_PLURAL = Regex("""(one or more .+) die""")
    private val BECOMES_BLOCKED = Regex("""(.+) becomes blocked""")
    // dsk chunk 12
    private val BECOMES_TARGET_OF_OPPONENTS = Regex("""(.+) becomes the target of a spell or ability an opponent controls""")
    // isd chunk 05: also "a creature card is put into an opponent's graveyard from their library" (each card, its owner's graveyard and library)
    private val PUT_INTO_YOUR_GRAVEYARD_FROM_LIBRARY = Regex("""(one or more .+) are put into (your) graveyard from your library|(an? .+) is put into (your|an opponent's) graveyard from (?:your|their) library""")
    // dsk chunk 10
    private val IS_DEALT_DAMAGE = Regex("""(.+) is dealt damage""")
    // dsk chunk 08
    private val YOU_CAST_SPELL_THAT = Regex("""you cast an? (.+ spell that .+)""")
    // blb gaps 2
    private val YOU_CAST_SPELL_WITH = Regex("""you cast an? (.+) spell (with .+)""")
    // dsk chunk 07
    private val SOURCE_DEALS_DAMAGE_TO = Regex("""(.+?) deals (noncombat |combat )?damage to (an opponent|a player|you)""")
    // dsk chunk 06
    private val IS_TURNED_FACE_UP = Regex("""(.+) is turned face up""")
    private val YOU_TURN_FACE_UP = Regex("""you turn an? (.+) face up""")
    private val YOU_SACRIFICE_SELF = Regex("""you sacrifice (.+)""")
    // dsk chunk 02
    private val ATTACKS_ALONE = Regex("""(.+) attacks alone""")
    // blb chunk 04: "your second main phase"
    private val BEGINNING_OF_YOUR = Regex("""the beginning of your (upkeep|draw step|end step|first main phase|second main phase)""")
    private val STEPS = mapOf<String, arcanum.api.TurnStep>("upkeep" to arcanum.api.TurnStep.Upkeep, "draw step" to arcanum.api.TurnStep.Draw, "end step" to arcanum.api.TurnStep.End,
        // dsk chunk 02: the first main phase is the precombat main phase (CR 505.1a)
        "first main phase" to arcanum.api.TurnStep.PrecombatMain, "second main phase" to arcanum.api.TurnStep.PostcombatMain)
    private val PUT_INTO_GRAVEYARD_FROM_BATTLEFIELD = Regex("""(.+) is put into a graveyard from the battlefield""")
    private val SELF_OR_ANOTHER = Regex("""(.+) or another (.+)""")
    private val DIES = Regex("""(.+) dies""")
    // chunk 12
    private val YOU_CAST_OF_CHOSEN = Regex("""you cast an? (spell of the chosen \w+)""")
    private val YOU_CAST = Regex("""you cast an? (?:(.+) )?spell""")
    private val YOU_CAST_ANOTHER = Regex("""you cast another (.+) spell""")
    // chunk 25
    private val PUT_INTO_OWNERS_GRAVEYARD = Regex("""(.+) is put into (your|an opponent's) graveyard from the battlefield""")
    private val ARTICLE_NOUN = Regex("""(?:an?|one or more) (.+)""")
    // chunk 26
    private val LEAVE_YOUR_GRAVEYARD = Regex("""(.+) leaves? your graveyard""")
    private val DEALS_COMBAT_DAMAGE_TO_A_PLAYER = Regex("""(.+) deals combat damage to (a player|enchanted player)""")
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
    private val PLAYER_SACRIFICES = Regex("""(a player|an opponent) sacrifices an? (.+)""")
    // chunk 50
    private val YOU_CAST_NOT_FROM_HAND = Regex("""you cast an? (?:(.+) )?spell from anywhere other than your hand""")
    private val BEGINNING_OF_EACH = Regex("""the beginning of each (upkeep|end step)""")
    private val PUT_INTO_YOUR_GRAVEYARD_FROM_ANYWHERE = Regex("""(.+) (?:is|are) put into your graveyard from anywhere""")
    // chunk 52
    private val EXILED_FROM_THE_BATTLEFIELD = Regex("""(.+) is exiled from the battlefield""")
    // chunk 56
    private val TRANSFORMS_INTO = Regex("""(.+) transforms into (.+)""")
    private val TRANSFORMS = Regex("""(.+) transforms""")
    // blb chunk 01
    private val BECOMES_TARGET = Regex("""(.+) becomes the target of a spell or ability (you control|an opponent controls)""")
    // blb chunk 05
    private val BEGINNING_OF_EACH_OPPONENTS = Regex("""the beginning of each opponent's (upkeep|end step)""")
    private val BEGINNING_OF_EACH_PLAYERS = Regex("""the beginning of each player's (upkeep|draw step|end step)""")
    private val BECOMES_LEVEL = Regex("""(.+) becomes level (\d+)""")
    private val ONE_OR_MORE_DIE = Regex("""one or more (.+) die""")
    // blb chunk 04
    private val YOU_EXPEND = Regex("""you expend (\d+)""")
    private val ENTERS_AND_WHENEVER = Regex("""(.+) enters and whenever (.+)""")
    // blb chunk 03
    private val ENTERS_AND_WHEN_YOU_SACRIFICE_IT = Regex("""(.+) enters and when you sacrifice it""")
}

/** Conditions ("if you control seven or more lands with different names"). */
internal object Conditions {
    /** mkm: "N or more sources you controlled dealt damage this turn", "N or more creatures attacked this turn", "N or more creature cards were put
     * into graveyards from anywhere this turn". */
    private fun mkmCondition(text: String): Condition? {
        Mkm.N_SOURCES_DEALT_DAMAGE_THIS_TURN.matchEntire(text)?.let { m ->
            return Condition.HappenedThisTurn(EventPattern.Damage(ObjectFilter.ControlledBy(PlayerFilter.You), arcanum.api.RecipientFilter()),
                atLeast = Numbers.count(m.groupValues[1]) ?: return null, measure = Measure.Sources)
        }
        Mkm.N_ATTACKED_THIS_TURN.matchEntire(text)?.let { m ->
            return Condition.HappenedThisTurn(EventPattern.Attack(Filters.noun(m.groupValues[2]) ?: return null),
                atLeast = Numbers.count(m.groupValues[1]) ?: return null, measure = Measure.Objects)
        }
        Mkm.N_PUT_INTO_GRAVEYARDS_THIS_TURN.matchEntire(text)?.let { m ->
            // "creature cards": a token put into a graveyard isn't a card (CR 108.2, 111.7)
            val noun = Filters.noun(m.groupValues[2]) ?: return null
            val cards = if (m.groupValues[2].endsWith("cards") || m.groupValues[2].endsWith("card")) Filters.all(noun, ObjectFilter.Category(
                arcanum.api.ObjectCategory.Card)) else noun
            // "your graveyard": a card goes to its owner's graveyard (CR 404.2)
            val moved = if (m.groupValues[3] == "your graveyard") Filters.all(cards, ObjectFilter.OwnedBy(PlayerFilter.You)) else cards
            val atLeast = if (m.groupValues[1].isEmpty()) 1 else Numbers.count(m.groupValues[1]) ?: return null
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(moved, to = Zone.Graveyard), atLeast = atLeast, measure = Measure.Objects)
        }
        return null
    }

    fun condition(text: String, self: Set<String> = emptySet()): Condition? {
        MechanicWordings.condition(text, self)?.let { return it }
        LtrPhrases.condition(text)?.let { return it } // ltr
        Mh2Phrases.condition(text, self)?.let { return it } // mh2
        DftPhrases.condition(text)?.let { return it } // dft
        TdmPhrases.condition(text)?.let { return it } // tdm
        EoePhrases.condition(text, self)?.let { return it } // eoe
        FinPhrases.condition(text, self)?.let { return it } // fin
        SpmPhrases.condition(text, self)?.let { return it } // spm
        TlaPhrases.condition(text, self)?.let { return it } // tla
        EclPhrases.condition(text, self)?.let { return it } // ecl
        SosPhrases.condition(text, self)?.let { return it } // sos
        TmtPhrases.condition(text, self)?.let { return it } // tmt
        MshPhrases.condition(text, self)?.let { return it } // msh
        HobPhrases.condition(text)?.let { return it } // hob
        FraPhrases.condition(text, self)?.let { return it } // fra
        InrPhrases.condition(text, self)?.let { return it } // inr
        mkmCondition(text)?.let { return it } // mkm
        // fra chunk 06: "this creature isn't prepared" (CR 722.3a); mkm chunk 05: any mark, "this creature is suspected", "it's not suspected"
        // (about this object, CR 701.60a)
        MARKED.matchEntire(text)?.takeIf { it.groupValues[1] == "it" || it.groupValues[1] in self || it.groupValues[1].replaceFirstChar { c -> c.lowercase() } in self }?.let { m ->
            val marked = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(arcanum.api.Status.Marked(ActionWordings.mark(m.groupValues[3]) ?: return@let)))
            return if (m.groupValues[2].isEmpty()) marked else Condition.Not(marked)
        }
        IsdPhrases.condition(text, self)?.let { return it } // isd
        // dgm: "this creature isn't a token" (CR 111.1)
        GTC_ISNT_A_TOKEN.matchEntire(text)?.takeIf { it.groupValues[1] in self || it.groupValues[1].replaceFirstChar { c -> c.lowercase() } in self }?.let {
            return Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.Category(arcanum.api.ObjectCategory.Token)))
        }
        // blb chunk 12: "an opponent controls more lands than you", "an opponent has more life than you", "… more cards in hand
        // than you": some opponent compared with you (CR 102.3)
        OPPONENT_CONTROLS_MORE.matchEntire(text)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            fun count(of: PlayerFilter) = Value.Count(Objects.Each(Filters.all(noun, ObjectFilter.ControlledBy(of))))
            return Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(Condition.Compare(
                count(PlayerFilter.Is(Players.Tested)), Comparison.Greater, count(PlayerFilter.You))))), atLeast = 1)
        }
        if (text == "an opponent has more life than you")
            return Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Life(Comparison.Greater, Value.LifeOf(Players.You)))), atLeast = 1)
        if (text == "an opponent has more cards in hand than you")
            return Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(Condition.Compare(
                Value.HandSize(Players.Tested), Comparison.Greater, Value.HandSize(Players.You))))), atLeast = 1)
        // blb chunk 12: "creatures you control have total power 10 or greater"
        TOTAL_POWER.matchEntire(text)?.let { m ->
            return Condition.Compare(Value.Of(arcanum.api.NumericProperty.Power, Filters.objects(m.groupValues[1]) ?: return null, Combine.Total),
                Comparison.AtLeast, Value.Fixed(m.groupValues[2].toInt()))
        }
        // blb chunk 12: "Kitsa's power is 3 or greater"
        SELF_POWER_AT_LEAST.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let { m ->
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.Power(Comparison.AtLeast, Value.Fixed(m.groupValues[2].toInt())))
        }
        // blb chunk 11: "another creature entered the battlefield under your control this turn" (looks back over the turn)
        if (text == "another creature entered the battlefield under your control this turn")
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                ObjectFilter.Type(arcanum.api.CardType.Creature), ObjectFilter.ControlledBy(PlayerFilter.You))), to = Zone.Battlefield), atLeast = 1, measure = Measure.Events)
        // blb chunk 09: "there are four or more card types among cards exiled with this creature" (CR 607.2a)
        CARD_TYPES_EXILED_WITH.matchEntire(text)?.takeIf { it.groupValues[2] in self }?.let { m ->
            return Condition.Compare(Value.CountDistinct(Objects.Remembered(EXILED_WITH), SharedQuality.CardType), Comparison.AtLeast,
                Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
        }
        // blb chunk 09: "it's not your turn"
        if (text == "it's not your turn") return Condition.Not(Condition.YourTurn)
        // dsk chunk 14: "a player has 13 or less life"
        A_PLAYER_HAS_LIFE_OR_LESS.matchEntire(text)?.let { m ->
            return Condition.PlayerExists(PlayerFilter.Life(arcanum.api.Comparison.AtMost, Value.Fixed(m.groupValues[1].toInt())), atLeast = 1)
        }
        // dsk chunk 13: "Kaiden has one or more loyalty counters on him" (this object)
        // fdn chunk 06: "this creature has three or more +1/+1 counters on it"
        HAS_N_OR_MORE_COUNTERS_ON.matchEntire(text)?.takeIf { it.groupValues[1] in self || it.groupValues[1].replaceFirstChar { c -> c.lowercase() } in self }?.let { m ->
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(Counters.kind(m.groupValues[3]) ?: return null, atLeast = Numbers.count(m.groupValues[2]) ?: return null))
        }
        // dsk chunk 13: "you sacrificed one or more permanents this turn" (a sacrificed permanent is its controller's, CR 701.21a)
        if (text == "you sacrificed one or more permanents this turn")
            return Condition.HappenedThisTurn(EventPattern.Sacrifice(ObjectFilter.ControlledBy(PlayerFilter.You)), atLeast = 1, measure = arcanum.api.Measure.Objects)
        // dsk chunk 13: "a land entered the battlefield under your control this turn"
        ENTERED_UNDER_YOUR_CONTROL_THIS_TURN.matchEntire(text)?.let { m ->
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(Filters.all(Filters.noun(m.groupValues[1]) ?: return null,
                ObjectFilter.ControlledBy(PlayerFilter.You)), to = Zone.Battlefield), atLeast = 1, measure = arcanum.api.Measure.Events)
        }
        // dsk chunk 13: "you control a prime number of lands"
        YOU_CONTROL_PRIME_NUMBER_OF.matchEntire(text)?.let { m ->
            return Condition.Prime(Value.Count(Filters.objects(m.groupValues[1] + " you control") ?: return null))
        }
        // dsk chunk 11: "there are seven or more time counters on it" (this object), "the discarded card wasn't a land card"
        COUNTERS_ON_IT_AT_LEAST.matchEntire(text)?.takeIf { it.groupValues[3] == "it" || it.groupValues[3] in self }?.let { m -> // fdn chunk 01: "on this artifact"
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(Counters.kind(m.groupValues[2]) ?: return null, atLeast = Numbers.count(m.groupValues[1]) ?: return null))
        }
        THE_DISCARDED_CARD_WAS.matchEntire(text)?.let { m ->
            val matches = Condition.ObjectMatches(Objects.Bound("discarded"), Filters.noun(m.groupValues[2]) ?: return null)
            return if (m.groupValues[1] == "was") matches else Condition.Not(matches)
        }
        // dsk chunk 04: "there are three or more cards exiled with this creature": its card memory still in exile (CR 607.2a)
        EXILED_WITH_AT_LEAST.matchEntire(text)?.takeIf { it.groupValues[2] in self }?.let { m ->
            return Condition.Compare(Value.Count(Objects.Where(Objects.Remembered(EXILED_WITH), ObjectFilter.InZone(Zone.Exile))), Comparison.AtLeast,
                Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
        }
        // dsk chunk 03: "there are two or more unlocked doors among Rooms you control" (CR 709.5c)
        UNLOCKED_DOORS_AT_LEAST.matchEntire(text)?.let { m ->
            return Condition.Compare(Values.value("the number of unlocked doors among " + m.groupValues[2]) ?: return null, Comparison.AtLeast,
                Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
        }
        // dsk chunk 01: a Room on the battlefield has only its unlocked doors' names (CR 709.5), so these are the names among
        // the Rooms
        NAMES_AMONG_UNLOCKED_DOORS.matchEntire(text)?.let { m ->
            return Condition.Compare(Value.CountDistinct(Objects.Each(Filters.noun("Room you control")!!), SharedQuality.Name),
                Comparison.AtLeast, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
        }
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
        // mkm chunk 10: "Tolsimir attacked this combat" (declared as an attacker, even if removed from combat since, CR 506.4)
        Mkm.ATTACKED_THIS_COMBAT.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.AttackedThisCombat)
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
        // blb chunk 09: "this spell was cast from a graveyard"
        if (text == "this spell was cast from a graveyard") return Condition.CastFrom(Zone.Graveyard, Objects.Self)

        // blb chunk 08: "you don't control a Snail"
        DONT_CONTROL_A.matchEntire(text)?.let { m ->
            return Condition.Not(Condition.Exists(Filters.noun(m.groupValues[1] + " you control") ?: return null, atLeast = 1))
        }
        // blb chunk 08: "a Bird, Frog, Otter, or Rat entered the battlefield under your control this turn"
        ENTERED_UNDER_YOUR_CONTROL_THIS_TURN.matchEntire(text)?.let { m ->
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(Filters.all(Filters.noun(m.groupValues[1]) ?: return null,
                ObjectFilter.ControlledBy(PlayerFilter.You)), to = Zone.Battlefield), atLeast = 1, measure = Measure.Events)
        }
        // blb chunk 07: "you gained or lost life this turn", "you gained and lost life this turn", "you've lost life this turn",
        // "an opponent lost life this turn" (CR 119.9, 119.10)
        LIFE_THIS_TURN.matchEntire(text)?.let { m ->
            val who = if (m.groupValues[1] == "an opponent") PlayerFilter.Opponent else PlayerFilter.You
            fun happened(word: String) = Condition.HappenedThisTurn(if (word == "gained") EventPattern.GainLife(who) else EventPattern.LoseLife(who),
                atLeast = 1, measure = Measure.Events)
            val first = happened(m.groupValues[2])
            val second = m.groupValues[4].takeIf { it.isNotEmpty() }?.let { happened(it) } ?: return first
            return if (m.groupValues[3] == "or") Condition.Or(listOf(first, second)) else Condition.And(listOf(first, second))
        }
        // blb chunk 05: "that player has one or fewer cards in hand" (the event's player)
        THAT_PLAYER_HAS_CARDS.matchEntire(text)?.let { m ->
            // inr chunk 05: "that player has no cards in hand"
            val most = if (m.groupValues[2].isNotEmpty()) 0 else Numbers.count(m.groupValues[1]) ?: return null
            return Condition.Compare(Value.HandSize(Players.EventPlayer), Comparison.AtMost, Value.Fixed(most))
        }
        // blb chunk 05: "you control a creature with power 4 or greater"
        BLB_CONTROL_A_PHRASE.matchEntire(text)?.let { m -> Filters.noun(m.groupValues[1] + " you control")?.let { return Condition.Exists(it, atLeast = 1) } }
        // blb chunk 03: "A or if B"
        OR_IF.matchEntire(text)?.let { m ->
            val first = condition(m.groupValues[1], self) ?: return null
            val second = condition(m.groupValues[2], self) ?: return null
            return Condition.Or(listOf(first, second))
        }
        // blb chunk 03: "three or more cards left your graveyard this turn": each card counts (CR 400.7)
        N_LEFT_YOUR_GRAVEYARD_THIS_TURN.matchEntire(text)?.let { m ->
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(Filters.all(Filters.noun(m.groupValues[2]) ?: return null,
                ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard), atLeast = Numbers.count(m.groupValues[1]) ?: return null, measure = Measure.Objects)
        }
        // blb chunk 03: "you've sacrificed a Food this turn"
        SACRIFICED_THIS_TURN.matchEntire(text)?.let { m ->
            return Condition.HappenedThisTurn(EventPattern.Sacrifice(Filters.all(Filters.noun(m.groupValues[1]) ?: return null,
                ObjectFilter.ControlledBy(PlayerFilter.You))), atLeast = 1, measure = Measure.Events)
        }
        // mkm chunk 03: "evidence was collected": the spell's linked optional additional cost was paid (CR 701.59c, 607.2)
        if (text == "evidence was collected") return Condition.Paid(null, times = 1, of = Objects.Self)
        // blb chunk 02: "the gift was promised" (CR 702.174k)
        if (text == "the gift was promised") return Condition.Paid(arcanum.api.Keyword.Gift.Kind, times = 1, of = Objects.Self)
        if (text == "the gift wasn't promised") return Condition.Not(Condition.Paid(arcanum.api.Keyword.Gift.Kind, times = 1, of = Objects.Self))
        // dsk chunk 10: "a creature died this turn" (CR 700.4), "it was cast", "defending player controls no Glimmer creatures"
        A_DIED_THIS_TURN.matchEntire(text)?.let { m ->
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(Filters.noun(m.groupValues[1]) ?: return null, from = Zone.Battlefield, to = Zone.Graveyard),
                atLeast = 1, measure = Measure.Events)
        }
        if (text == "it was cast") return Condition.CastFrom(null, Objects.Self)
        DEFENDING_PLAYER_CONTROLS_NO.matchEntire(text)?.let { m ->
            return Condition.Not(Condition.Exists(Filters.noun(m.groupValues[1] + " defending player controls") ?: return null, atLeast = 1))
        }
        // dsk chunk 09: "you cast it", "you cast it from your hand" (CR 601.2a), "you have no cards in hand"
        CAST_IT.matchEntire(text)?.let { m ->
            val zone = when (m.groupValues[1]) { "" -> null; " from your hand" -> Zone.Hand; else -> Zone.Graveyard }
            return Condition.CastFrom(zone, Objects.Self)
        }
        if (text == "you have no cards in hand")
            return Condition.Not(Condition.Exists(Filters.all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 1))
        // dsk chunk 08: "you have at least 7 life more than your starting life total" (CR 103.4)
        LIFE_MORE_THAN_STARTING.matchEntire(text)?.let { m ->
            return Condition.Compare(Value.LifeOf(Players.You), Comparison.AtLeast, Value.Sum(listOf(Value.StartingLife(Players.You), Value.Fixed((m.groupValues[1] + m.groupValues[2]).toInt()))))
        }
        // fdn chunk 05: "your life total is greater than your starting life total" (CR 103.4)
        if (text == "your life total is greater than your starting life total")
            return Condition.Compare(Value.LifeOf(Players.You), Comparison.Greater, Value.StartingLife(Players.You))
        // fdn chunk 05: "any player controls a black permanent": the permanent of anyone (CR 110.1)
        Fdn.ANY_PLAYER_CONTROLS.matchEntire(text)?.let { m ->
            return Condition.Exists(Filters.noun(m.groupValues[1]) ?: return null, atLeast = 1)
        }
        // dsk chunk 06: "a permanent entered the battlefield face down under your control this turn", "you turned a permanent
        // face up this turn"
        if (text == "a permanent entered the battlefield face down under your control this turn")
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Permanent),
                ObjectFilter.HasStatus(arcanum.api.Status.FaceDown), ObjectFilter.ControlledBy(PlayerFilter.You)), to = Zone.Battlefield), atLeast = 1, measure = Measure.Events)
        if (text == "you turned a permanent face up this turn")
            return Condition.HappenedThisTurn(EventPattern.TurnedFaceUp(Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Permanent),
                ObjectFilter.ControlledBy(PlayerFilter.You))), atLeast = 1, measure = Measure.Events)
        // dsk chunk 04: "you control a Glimmer creature", "you don't control a Glimmer creature"
        CONTROL_A_PHRASE.matchEntire(text)?.let { m ->
            val filter = Filters.noun(m.groupValues[3] + " you control") ?: return null
            // dsk chunk 09: "you control another creature with power 2 or less"
            val exists = Condition.Exists(if (m.groupValues[2] == "another") Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), filter) else filter, atLeast = 1)
            return if (m.groupValues[1].isEmpty()) exists else Condition.Not(exists)
        }
        // chunk 52: "this is the second time this ability has resolved this turn" (counting this resolution)
        NTH_RESOLUTION.matchEntire(text)?.let { m ->
            return Condition.Compare(Value.AbilityUsesThisTurn(resolved = true), Comparison.Equal, Value.Fixed(Numbers.ordinal(m.groupValues[1]) ?: return null))
        }
        // dsk chunk 06: "[condition] or [condition]", when each part is one
        if (text.contains(" or ")) {
            val parts = text.split(" or ").map { condition(it, self) }
            if (parts.size > 1 && parts.all { it != null }) return Condition.Or(parts.filterNotNull())
        }
        return null
    }

    private val CONTROL_A = Regex("""you control an? (\w+)""")
    // dsk chunk 01
    // dsk chunk 14
    private val A_PLAYER_HAS_LIFE_OR_LESS = Regex("""a player has (\d+) or less life""")
    // dsk chunk 13
    private val HAS_N_OR_MORE_COUNTERS_ON = Regex("""(.+) has (\w+) or more (\S+) counters on (?:it|him|her)""")
    private val ENTERED_UNDER_YOUR_CONTROL_THIS_TURN = Regex("""an? (.+) entered the battlefield under your control this turn""")
    private val YOU_CONTROL_PRIME_NUMBER_OF = Regex("""you control a prime number of (.+)""")
    // dsk chunk 11
    private val COUNTERS_ON_IT_AT_LEAST = Regex("""there are (\w+) or more (\S+) counters on (.+)""")
    private val THE_DISCARDED_CARD_WAS = Regex("""the discarded card (was|wasn't) an? (.+)""")
    // dsk chunk 10
    private val A_DIED_THIS_TURN = Regex("""an? ([\w-]+(?: [\w-]+)?) died this turn""")
    private val DEFENDING_PLAYER_CONTROLS_NO = Regex("""defending player controls no (.+)""")
    // dsk chunk 09
    private val CAST_IT = Regex("""you cast it( from your hand| from your graveyard)?""")
    // dsk chunk 08
    private val LIFE_MORE_THAN_STARTING = Regex("""(?:you have at least (\d+) life more than|your life total is at least (\d+) greater than) your starting life total""")
    // dsk chunk 04
    private val CONTROL_A_PHRASE = Regex("""you (don't )?control (an?|another) (.+)""")
    private val EXILED_WITH_AT_LEAST = Regex("""there are (\w+) or more cards exiled with (.+)""")
    private val UNLOCKED_DOORS_AT_LEAST = Regex("""there are (\w+) or more unlocked doors among (.+)""")
    private val NAMES_AMONG_UNLOCKED_DOORS = Regex("""there are (\w+) or more different names among unlocked doors of Rooms you control""")
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
    // dsk chunk 03: "if it's the second time" after "if this is the first time this ability has resolved this turn"
    private val NTH_RESOLUTION = Regex("""(?:this is|it's) the (\w+) time(?: this ability has resolved this turn)?""")
    // blb chunk 12
    // dgm
    private val GTC_ISNT_A_TOKEN = Regex("""(.+) isn't a token""")
    // mkm chunk 05: "this creature is suspected", "it's not suspected", "this creature isn't prepared" (a mark, [ActionWordings.mark])
    private val MARKED = Regex("""(.+?)(?: is|'s)(n't| not)? (\w+)""")
    private val OPPONENT_CONTROLS_MORE = Regex("""an opponent controls more (.+) than you""")
    private val TOTAL_POWER = Regex("""(.+) have total power (\d+) or greater""")
    private val SELF_POWER_AT_LEAST = Regex("""(.+)'s power is (\d+) or greater""")
    // blb chunk 09
    private val CARD_TYPES_EXILED_WITH = Regex("""there are (\w+) or more card types among cards exiled with (.+)""")
    // blb chunk 08
    private val DONT_CONTROL_A = Regex("""you don't control an? (.+)""")
    // blb chunk 07
    private val LIFE_THIS_TURN = Regex("""(you|you've|an opponent) (gained|lost)(?: (or|and) (gained|lost))? life this turn""")
    // blb chunk 05
    private val THAT_PLAYER_HAS_CARDS = Regex("""that player has (?:(\w+) or fewer|(no)) cards in hand""")
    private val BLB_CONTROL_A_PHRASE = Regex("""you control an? (.+)""")
    // blb chunk 03
    private val OR_IF = Regex("""(.+?) or if (.+)""")
    private val N_LEFT_YOUR_GRAVEYARD_THIS_TURN = Regex("""(\w+) or more (.+) left your graveyard this turn""")
    private val SACRIFICED_THIS_TURN = Regex("""you've sacrificed an? (.+) this turn""")
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
        val keywords = quoted ?: m.groupValues[3].takeIf { it.isNotEmpty() }?.split(LazyRegexes.AND_LIST)?.map { OracleParser.keyword(it) ?: return null }.orEmpty()
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
    private val COUNTED = Regex("""(${Numbers.WORD_PATTERN}) ((?:\d+/\d+|X/X) .+? creature) tokens?(?: with (.+))?""")
    // chunk 26
    private val QUOTED = Regex(""""(.+)"""")
    private val TOKEN = Regex("""an? (\d+/\d+|X/X) (\w+(?: and \w+)?) ([A-Z][A-Za-z ]+? creature) token""")
    private val COLORS = mapOf("white" to Color.White, "blue" to Color.Blue, "black" to Color.Black, "red" to Color.Red, "green" to Color.Green)

    /** dsk chunk 13: a colour word ("red"). */
    fun color(word: String): Color? = COLORS[word]
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
            // dsk chunk 07: counters with rules meaning are their own kinds (CR 122.1)
            "finality" -> arcanum.api.CounterKind.Finality
            "rad" -> arcanum.api.CounterKind.Rad
            "hone" -> arcanum.api.CounterKind.Hone
            "lore" -> arcanum.api.CounterKind.Lore
            "defense" -> arcanum.api.CounterKind.Defense
            "poison" -> arcanum.api.CounterKind.Poison
            "energy" -> arcanum.api.CounterKind.Energy
            // dsk chunk 04: "a flying counter" is a keyword counter (CR 122.1b)
            else -> OracleParser.keyword(text.replaceFirstChar { it.uppercase() })?.let { arcanum.api.CounterKind.Keyword(it.kind) } ?: if (text.all { it.isLetter() }) arcanum.api.CounterKind.Named(text) else null
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
        Mh2Phrases.value(text, exiled)?.let { return it } // mh2
        DftPhrases.value(text)?.let { return it } // dft
        TdmPhrases.value(text)?.let { return it } // tdm
        EoePhrases.value(text)?.let { return it } // eoe
        FinPhrases.value(text)?.let { return it } // fin
        TlaPhrases.value(text)?.let { return it } // tla
        EclPhrases.value(text)?.let { return it } // ecl
        SosPhrases.value(text)?.let { return it } // sos
        HobPhrases.value(text)?.let { return it } // hob
        FraPhrases.value(text)?.let { return it } // fra
        // mkm chunk 09: "the number of different color pairs among permanents you control that are exactly two colors" (CR 105.2)
        Mkm.NUMBER_OF_COLOR_PAIRS_AMONG.matchEntire(text)?.let { m ->
            return Value.CountDistinct(Filters.objects(m.groupValues[1]) ?: return null, arcanum.api.SharedQuality.ColorPair)
        }
        // mkm chunk 09: "the number of artifacts that were put into graveyards from the battlefield this turn" (each object once; tokens too, CR 111.7)
        Mkm.NUMBER_PUT_INTO_GRAVEYARDS_FROM_THIS_TURN.matchEntire(text)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val graveyard = if (m.groupValues[2] == "your graveyard") Filters.all(filter, ObjectFilter.OwnedBy(PlayerFilter.You)) else filter
            return Value.EventsThisTurn(EventPattern.ZoneChange(graveyard, from = Zone.Battlefield.takeIf { m.groupValues[3] == "the battlefield" }, to = Zone.Graveyard),
                arcanum.api.Measure.Objects)
        }
        IsdPhrases.value(text)?.let { return it } // isd
        // inr chunk 08: "twice the number of Blood tokens you control"
        if (text.startsWith("twice ")) value(text.removePrefix("twice "), exiled)?.let { return Value.Times(it, 2) }
        // isd chunk 06: "half the number of Zombies you control, rounded down" (CR 107.1a)
        HALF_ROUNDED.matchEntire(text)?.let { m -> value(m.groupValues[1], exiled)?.let { return Value.Half(it, roundUp = m.groupValues[2] == "up") } }
        // gtc chunk 04: "the number of +1/+1 counters removed this way": the number chosen as the cost was paid (announced, CR 601.2f)
        if (LazyRegexes.COUNTERS_REMOVED_THIS_WAY.matches(text)) return Value.Announced(name = "X")
        // dsk chunk 12: "the power of the creature you chose or the card you revealed" (the beheld creature, CR 701.4a)
        if (text == "the power of the creature you chose or the card you revealed") return Value.Of(arcanum.api.NumericProperty.Power, Objects.Bound("beheld"), Combine.Single)
        // dsk chunk 12: "the greatest power among creatures you control"
        if (text.startsWith("the greatest power among ")) {
            return Value.Of(arcanum.api.NumericProperty.Power, Filters.objects(text.removePrefix("the greatest power among ")) ?: return null, Combine.Greatest)
        }
        // dsk chunk 07: "the number of card types among cards in your graveyard"
        if (text == "the number of card types among cards in your graveyard")
            return Value.CountDistinct(Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))), SharedQuality.CardType)
        // dsk chunk 04: "where X is its power" (this object's)
        if (text == "its power") return Value.Of(arcanum.api.NumericProperty.Power, Objects.Self, Combine.Single)
        // dsk chunk 03: "the number of counters on this creature" (every kind; as it last existed once it has left, CR 608.2h)
        if (text == "the number of counters on this creature") return Value.Of(arcanum.api.NumericProperty.Counters(null), Objects.Self, Combine.Single)
        // dsk chunk 02: "the number of unlocked doors among Rooms you control" (CR 709.5c)
        UNLOCKED_DOORS_AMONG.matchEntire(text)?.let { m ->
            return Value.Of(arcanum.api.NumericProperty.UnlockedDoors, Filters.objects(m.groupValues[1]) ?: return null, Combine.Total)
        }
        // blb gaps 2: "the number of creatures that were exiled under your opponents' control this turn" (from the battlefield)
        EXILED_UNDER_CONTROL.matchEntire(text)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            val who = if (m.groupValues[2] == "your opponents'") PlayerFilter.Opponent else PlayerFilter.You
            return Value.EventsThisTurn(EventPattern.ZoneChange(Filters.all(filter, ObjectFilter.ControlledBy(who)),
                from = Zone.Battlefield, to = Zone.Exile), arcanum.api.Measure.Objects)
        }
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
        // rtr chunk 06: "the power of the creature that died" (as it last existed, CR 603.10a)
        if (text == "the power of the creature that died") return Value.Of(arcanum.api.NumericProperty.Power, Objects.EventObject(arcanum.api.EventRole.Before), Combine.Single)
        // rtr chunk 06: "the sacrificed creature's toughness" (as it last existed, CR 608.2h)
        SACRIFICED_PROPERTY.matchEntire(text)?.let { m ->
            // inr chunk 09: also "mana value" (Eldritch Evolution)
            val property = when (m.groupValues[1]) { "power" -> arcanum.api.NumericProperty.Power; "toughness" -> arcanum.api.NumericProperty.Toughness
                else -> arcanum.api.NumericProperty.ManaValue }
            return Value.Of(property, Objects.Bound(SACRIFICED), Combine.Single)
        }
        // rtr chunk 05: "the total life lost by your opponents this turn"
        if (text == "the total life lost by your opponents this turn")
            return Value.EventsThisTurn(EventPattern.LoseLife(PlayerFilter.Opponent), arcanum.api.Measure.Amount)
        // One exiled card, but card memory may hold several: read as their total (DefinitionChecks).
        if (text == "the mana value of the exiled card")
            return Value.Of(arcanum.api.NumericProperty.ManaValue, exiled ?: return null, Combine.Total)
        NUMBER_OF.matchEntire(text)?.let { m -> return Value.Count(Filters.objects(m.groupValues[1]) ?: return null) }
        // blb chunk 09: "the greatest mana value among cards in your graveyard"
        GREATEST_AMONG.matchEntire(text)?.let { m ->
            val property = if (m.groupValues[1] == "power") arcanum.api.NumericProperty.Power else arcanum.api.NumericProperty.ManaValue
            val among = if (m.groupValues[2] == "cards in your graveyard") Objects.Each(Filters.all(ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You))) else Filters.objects(m.groupValues[2]) ?: return null
            return Value.Of(property, among, Combine.Greatest)
        }

        return null
    }

    // blb gaps 2
    private val EXILED_UNDER_CONTROL = Regex("""the number of (.+) that were exiled under (your|your opponents') control this turn""")
    private val DIED_UNDER_YOUR_CONTROL = Regex("""the number of (.+) that died under your control this turn""")
    // rtr chunk 06
    private val SACRIFICED_PROPERTY = Regex("""the sacrificed creature's (power|toughness|mana value)""")
    // dsk chunk 02
    private val UNLOCKED_DOORS_AMONG = Regex("""the number of unlocked doors among (.+)""")
    private val CARDS_IN_YOUR_ZONE = Regex("""the number of cards in your (library|hand|graveyard)""")
    private val DEVOTION = Regex("""your devotion to (white|blue|black|red|green)""")
    private val NUMBER_OF = Regex("""the number of (.+)""")
    // blb chunk 09
    private val GREATEST_AMONG = Regex("""the greatest (mana value|power) among (.+)""")

    private val COLORS = mapOf("white" to Color.White, "blue" to Color.Blue, "black" to Color.Black, "red" to Color.Red, "green" to Color.Green)
}
