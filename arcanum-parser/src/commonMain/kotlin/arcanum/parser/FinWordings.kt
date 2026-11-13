package arcanum.parser

import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Value

/** fin: nouns, values and conditions of Final Fantasy wordings, tried first by [Filters], [Values] and [Conditions]. */
internal object FinPhrases {
    // fin chunk 03: "creature that isn't a Kraken, Leviathan, Merfolk, Octopus, or Serpent"
    private val THAT_ISNT = Regex("""(\w+) that isn't an? (.+)""")
    // fin chunk 03: "the total mana value of other permanents you control"
    private val TOTAL_MANA_VALUE_OF = Regex("""the total mana value of (.+)""")

    // fin chunk 05
    private val MANA_SPENT = Regex("""(?:at least (\w+)|(\w+) or more) mana was spent to cast (?:it|that spell)""")
    private val IT_HAS_COUNTERS = Regex("""it has (\w+) or more (\S+) counters on it""")
    private val PLAYER_DEALT_COMBAT_DAMAGE = Regex("""a player was dealt (\d+) or more combat damage this turn""")
    private val DIED_UNDER_CONTROL = Regex("""an? (\w+) died under (an opponent's|your) control this turn""")
    private val CARDS_ARE_IN_GRAVEYARD = Regex("""(\w+) or more cards are in your graveyard""")
    private val AND_SUBTYPE_YOU_CONTROL = Regex("""([A-Za-z]+ creatures) and ([A-Z][a-z]+?)(?:es|s) you control""")
    private val ABILITIES_OR_SPELL = Regex("""activated ability, triggered ability, or (.+ spell)""")
    // inr chunk 07: Overcharged Amalgam "spell, activated ability, or triggered ability"
    private val SPELL_OR_ABILITIES = Regex("""(.*spell), activated ability, or triggered ability""")
    private val SUBTYPE_LIST_YOU_CONTROL = Regex("""([A-Z][a-z]+), ([A-Z][a-z]+), and(?:/or)? ([A-Z][a-z]+) you control""")
    /** A subtype written singular or plural ("Scouts", "Scout"); null when neither is a subtype. */
    private fun singular(word: String): String? = listOf(word, word.removeSuffix("s"), word.removeSuffix("es")).firstOrNull { it in RulesWords.subtypes }
    private val BASIC_LAND_OR_TOWN = Regex("""basic land cards? (?:or|and/or) Town cards?""")
    private val DIDNT_ENTER_THIS_TURN = Regex("""(.+?) didn't enter the battlefield this turn""")
    fun noun(phrase: String): ObjectFilter? {
        THAT_ISNT.matchEntire(phrase)?.let { m ->
            val kind = Filters.noun(m.groupValues[1]) ?: return null
            val not = Filters.noun(m.groupValues[2]) ?: return null
            return Filters.all(kind, ObjectFilter.Not(not))
        }
        // fin chunk 08: "Artifact creatures and Heroes you control" (either kind, yours)
        AND_SUBTYPE_YOU_CONTROL.matchEntire(phrase)?.let { m ->
            val words = m.groupValues[1].replaceFirstChar { it.lowercase() }
            val first = Filters.noun(words) ?: Filters.noun(words.removeSuffix("s")) ?: return null
            if (m.groupValues[2] !in RulesWords.subtypes) return null
            return Filters.all(ObjectFilter.Or(listOf(first, ObjectFilter.Sub(arcanum.api.Subtype(m.groupValues[2])))),
                ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.You))
        }
        // fin chunk 09: "activated ability, triggered ability, or noncreature spell" (abilities on the Stack, CR 113.1c)
        ABILITIES_OR_SPELL.matchEntire(phrase)?.let { m ->
            fun ability(kind: arcanum.api.AbilityKind) = Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = kind))
            val spell = Filters.noun(m.groupValues[1]) ?: return null
            return ObjectFilter.Or(listOf(ability(arcanum.api.AbilityKind.Activated), ability(arcanum.api.AbilityKind.Triggered), spell))
        }
        SPELL_OR_ABILITIES.matchEntire(phrase)?.let { m ->
            fun ability(kind: arcanum.api.AbilityKind) = Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = kind))
            val spell = Filters.noun(m.groupValues[1]) ?: return null
            return ObjectFilter.Or(listOf(spell, ability(arcanum.api.AbilityKind.Activated), ability(arcanum.api.AbilityKind.Triggered)))
        }
        // fin chunk 12: "attacking equipped creature" (CR 301.5c)
        if (phrase == "attacking equipped creature")
            return Filters.all(ObjectFilter.HasStatus(arcanum.api.Status.Attacking), ObjectFilter.Type(arcanum.api.CardType.Creature),
                ObjectFilter.Attached(to = null, attachments = ObjectFilter.Sub(arcanum.api.Subtype("Equipment"))))
        // fin chunk 12: "Scouts, Pirates, and/or Rogues you control", "Scout, Pirate, and Rogue you control"
        SUBTYPE_LIST_YOU_CONTROL.matchEntire(phrase)?.let { m ->
            val names = listOf(m.groupValues[1], m.groupValues[2], m.groupValues[3]).map { singular(it) ?: return null }
            return Filters.all(ObjectFilter.Or(names.map { ObjectFilter.Sub(arcanum.api.Subtype(it)) }), ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.You))
        }
        // fin chunk 13: "basic land card or Town card", "basic land cards and/or Town cards"
        BASIC_LAND_OR_TOWN.matchEntire(phrase)?.let {
            return ObjectFilter.Or(listOf(Filters.all(ObjectFilter.Super(arcanum.api.Supertype.Basic), ObjectFilter.Type(arcanum.api.CardType.Land)),
                ObjectFilter.Sub(arcanum.api.Subtype("Town"))))
        }
        // fin-noun-end
        return null
    }

    fun value(text: String): Value? {
        TOTAL_MANA_VALUE_OF.matchEntire(text)?.let { m ->
            return Value.Of(NumericProperty.ManaValue, Objects.Each(Filters.noun(m.groupValues[1]) ?: return null), Combine.Total)
        }
        // fin chunk 08: "the amount of life you gained this turn"
        if (text == "the amount of life you gained this turn")
            return Value.EventsThisTurn(arcanum.api.EventPattern.GainLife(arcanum.api.PlayerFilter.You), arcanum.api.Measure.Amount)
        // fin-value-end
        return null
    }

    fun condition(text: String, self: Set<String>): Condition? {
        // fin chunk 05: "at least four mana was spent to cast it", "four or more mana was spent to cast that spell" (the spell cast, CR 601.2h)
        MANA_SPENT.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1] + m.groupValues[2]) ?: return null
            return Condition.Compare(Value.ManaSpent(of = Objects.EventObject(arcanum.api.EventRole.Object)), arcanum.api.Comparison.AtLeast, Value.Fixed(n))
        }
        // fin chunk 05: "it has three or more +1/+1 counters on it" (this object)
        IT_HAS_COUNTERS.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(Counters.kind(m.groupValues[2]) ?: return null, atLeast = n))
        }
        // fin chunk 05: "a player was dealt 6 or more combat damage this turn" (one player's damage added up)
        PLAYER_DEALT_COMBAT_DAMAGE.matchEntire(text)?.let { m ->
            val damage = arcanum.api.EventPattern.Damage(ObjectFilter.Any, arcanum.api.RecipientFilter(players = arcanum.api.PlayerFilter.Is(arcanum.api.Players.Tested)),
                combat = true)
            return Condition.PlayerExists(arcanum.api.PlayerFilter.Where(Condition.HappenedThisTurn(damage, atLeast = m.groupValues[1].toInt(),
                measure = arcanum.api.Measure.Amount)), atLeast = 1)
        }
        // fin chunk 05: "your life total is less than or equal to half your starting life total" (CR 103.4)
        if (text == "your life total is less than or equal to half your starting life total")
            return Condition.Compare(Value.LifeOf(arcanum.api.Players.You), arcanum.api.Comparison.AtMost,
                Value.Half(Value.StartingLife(arcanum.api.Players.You), roundUp = false))
        // fin chunk 05: "a creature died under an opponent's control this turn"
        DIED_UNDER_CONTROL.matchEntire(text)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            val whose = if (m.groupValues[2] == "an opponent's") arcanum.api.PlayerFilter.Opponent else arcanum.api.PlayerFilter.You
            return Condition.HappenedThisTurn(arcanum.api.EventPattern.ZoneChange(Filters.all(noun, ObjectFilter.ControlledBy(whose)), from = arcanum.api.Zone.Battlefield,
                to = arcanum.api.Zone.Graveyard), atLeast = 1, measure = arcanum.api.Measure.Events)
        }
        // fin chunk 05: "eight or more cards are in your graveyard"
        CARDS_ARE_IN_GRAVEYARD.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return Condition.Exists(Filters.all(ObjectFilter.InZone(arcanum.api.Zone.Graveyard), ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You)), atLeast = n)
        }
        // fin chunk 13: "this creature didn't enter the battlefield this turn"
        DIDNT_ENTER_THIS_TURN.matchEntire(text)?.takeIf { it.groupValues[1] in self || it.groupValues[1].replaceFirstChar { c -> c.lowercase() } in self }?.let {
            return Condition.Not(Condition.HappenedThisTurn(arcanum.api.EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = arcanum.api.Zone.Battlefield),
                atLeast = 1, measure = arcanum.api.Measure.Events))
        }
        // fin-condition-end
        return null
    }
}

/** fin: regular expressions of Final Fantasy wordings (the set's parser tests, sets/fin/FinChunk*Test.kt). */
internal object Fin {
    // fin chunk 02: a tiered mode "Cure — {0} — [effect]" (CR 702.183a); "3/2." completing a paragraph
    val TIERED_MODE = Regex("""([^—]+) — ((?:\{[^}]+\})+) — (.+)""")
    val PT_MODE = Regex("""(\d+/\d+)\.""")
    // fin chunk 01: "Equipped creature gets +1/+1, has "…," and is a Cleric in addition to its other types."
    val ATTACHED_CLAUSES = Regex("""(Equipped creature|Enchanted creature) ((?:gets|has|is) .+?)\.?""")
    val HAS_QUOTE = Regex("""has "(.+)"""")
    val IS_A_IN_ADDITION = Regex("""is an? ([A-Z][a-z]+(?: [A-Z][a-z]+)*) in addition to its other types""")
    val CLAUSE_SEPARATOR = Regex(""",? and (?=(?:is|has|gets) )|, (?:and )?""")
    // fin chunk 01: "Whenever you cast a noncreature spell and whenever you draw your third card each turn, put …"
    val WHENEVER_AND_WHENEVER = Regex("""Whenever (.+?) and whenever (.+?), (.+)""")
    val IF_THAT_CARD_ENTERS_TAPPED_ATTACKING = Regex("""If that card is an? (.+?), it enters tapped and attacking\.""")
    // fin chunk 02
    val TARGETS_OWNER_SHUFFLES = Regex("""Target (.+?)'s owner shuffles it into their library\.""")
    val REMOVE_COUNTER_FROM_TARGET = Regex("""Remove (a|an|one|two|three) (\S+) counters? from target (.+)\.""")
    val TRIPLE_TARGET_PT = Regex("""Triple target (.+?)'s power and toughness until end of turn\.""")
    val UP_TO_FROM_SINGLE_GRAVEYARD = Regex("""(Exile) up to (\w+) target (.+?) from a single graveyard\.""")
    val GAINS_QUOTE_AND_BASE_PT = Regex("""Until end of turn, target (.+?) gains "(.+?)" and has base power and toughness (\d+)/(\d+)\.""")
    val COUNTER_ON_ONE_OF_THEM = Regex("""Put an? (\S+) counter on one of them\.""")
    // fin chunk 03
    val COPY_IT_TIMES = Regex("""Copy it (twice|three times)\.""")
    val SACRIFICES_AND_LOSES = Regex("""(Each opponent|Each player|Target player|Target opponent) sacrifices (.+?) and loses (\w+) life\.""")
    val SELF_GAINS_QUOTE = Regex("""(.+?) gains "(.+)"\.?""")
    val UNTIL_EOT_GAINS_AND_GETS = Regex("""Until end of turn, (.+?) gains (.+?) and gets (.+)\.""")
    val UNTIL_EOT_WHENEVER = Regex("""Until end of turn, whenever (.+)""")
    val COUNTER_ON_EACH_OF_THEM = Regex("""Put an? (\S+) counter on each of them\.""")
    val WHEN_YOU_NEXT_CAST = Regex("""When you next cast an? (.+?) this turn, (.+)""")
    val THAT_CREATURE_ENTERS_WITH_ADDITIONAL = Regex("""That creature enters with an additional (\S+) counter on it(?: for each (.+?))?\.""")
    val DEALS_DAMAGE_EQUAL_TOTAL = Regex("""(.+?) deals damage equal to (the total mana value of .+?) to (each opponent|any target|target .+)\.""")
    // fin chunk 04
    val TAP_ALL = Regex("""Tap all (.+)\.""")
    val EXILE_SELF_THEN_RETURN = Regex("""[Ee]xile (.+?), then (?:return it to|put it onto) the battlefield( transformed)?( under its owner's control)?(?: with an? (\S+) counter on it)?\.""")
    val RETURN_SELF_TRANSFORMED = Regex("""Return (this card) from your graveyard to the battlefield transformed\.""")
    val RETURN_ANY_NUMBER_TOTAL = Regex("""Return any number of target (.+?) with total mana value (\d+) or less from your graveyard to the battlefield\.""")
    val PUT_UP_TO_ONE_MILLED = Regex("""Put up to one (.+?) milled this way into your hand\.""")
    val DURING_YOUR_TURN_SELF_AND_OTHERS_HAVE = Regex("""During your turn, (.+?) and other (.+?) have (.+)\.""")
    val PUT_IT_ON_LIBRARY = Regex("""Put it on the (top|bottom) of its owner's library\.""")
    val UNTIL_NEXT_TURN_IT_GAINS = Regex("""Until your next turn, it gains (.+)\.""")
    val IF_THAT_CREATURE_IS = Regex("""If that creature is (\w+), (.+)\.""")
    val ADD_MANA_LIST = Regex("""Add ((?:\{[WUBRGC]\})+(?:, (?:and )?(?:\{[WUBRGC]\})+)+)\.""")
    val THOSE_CREATURES_GAIN = Regex("""Those creatures gain (.+) until end of turn\.""")
    val COPY_ENCHANTMENT_HASTE_LORE = Regex("""Create a token that's a copy of target (.+?)\. It gains haste\. If it's a Saga, put up to three lore counters on it\. Sacrifice it at the beginning of your next end step\.""")
    // fin chunk 05
    val SELF_DEALS_DAMAGE = Regex("""(.+?) deals damage""")
    val COUNTERS_EQUAL_TO_THAT_CREATURES_POWER = Regex("""Put a number of (\S+) counters on (.+?) equal to that creature's power\.""")
    val MAY_SACRIFICE_ANY_NUMBER_DRAW = Regex("""You may sacrifice any number of (.+?)\. If you do, draw that many cards\.""")
    val IF_YOU_PUT_A_CARD_INTO_HAND = Regex("""If you put a card into your hand this way, (.+)\.""")
    val IF_IT_HAS_COUNTERS_TRANSFORM_IT = Regex("""If it has (\w+) or more (\S+) counters on it, transform it\.""")
    val RETURN_IT_TO_BATTLEFIELD = Regex("""Return it to the battlefield( tapped)?\.""")
    val TRANSFORM_THEN_ATTACH = Regex("""Transform (.+?), then attach it to an? (.+)\.""")
    val CREATURES_OTHER_THAN_SELF_AND_CHOSEN_GET = Regex("""Until end of turn, creatures other than (.+?) and the chosen creature get ([+-]\d+)/([+-]\d+)\.""")
    val WOULD_DEAL_DAMAGE_DOUBLE = Regex("""If an? (.+?) would deal damage to a permanent or player, it deals double that damage instead\.""")
    val AS_TRANSFORMS_CHOOSE_OPPONENT = Regex("""As (.+?) transforms into (.+), choose an opponent\.""")
    val AS_TRANSFORMS_INTO = Regex("""As (.+?) transforms into (.+)""")
    val WHEN_CHOSEN_LEAVES = Regex("""When the chosen creature leaves the battlefield, (.+)""")
    // fin chunk 06
    val COUNTER_ON_SELF_AND_IT = Regex("""Put an? (\S+) counter on (.+?) and it (.+)\.""")
    val SACRIFICE_SELF_AND_DEALS_THAT_MUCH = Regex("""Sacrifice (.+?) and it deals that much damage to each opponent\.""")
    val ADD_X_ANY_COMBINATION = Regex("""Add (X|\w+) mana in any combination of \{([WUBRG])\} and/or \{([WUBRG])\}\.""")
    // fin chunk 07
    val MAY_RETURN_TO_HAND = Regex("""You may return (another .+?) to its owner's hand\.""")
    val MAY_ATTACH_ANY_NUMBER = Regex("""You may attach any number of (.+?) to target (.+)\.""")
    val SEARCH_LIBRARY_AND_OR_GRAVEYARD = Regex("""Search your library and/or graveyard for an? (.+?), reveal it, and put it into your hand\. If you search your library this way, shuffle\.""")
    val OPPONENT_GAINS_CONTROL_IF_THEY_DO = Regex("""Target opponent gains control of another target (.+?)\. If they do, (.+)\.""")
    val GAINS_PUT_COUNTERS_ATTACH_EQUIPMENT = Regex("""Target (.+?) gains (.+?) until end of turn\. Put (.+?)'s counters on that creature and attach an Equipment that was attached to (.+?) to that creature\.""")
    val EQUIPPED_TRIGGERS_ADDITIONAL = Regex("""As long as (.+?) is equipped, if a triggered ability of (.+?) or an Equipment attached to it triggers, that ability triggers an additional time\.""")
    val NUMBER_OF_COUNTERS_ON = Regex("""the number of (\S+) counters on (.+)""")
    // fin chunk 08
    val GET_FOR_EACH_AND_EACH = Regex("""(.+?) get \+(\d+)/\+(\d+) for each (.+?) and each (.+?)\.""")
    val EACH_OPPONENT_MILLS = Regex("""Each opponent mills (X|\w+) cards?\.""")
    // fin chunk 09
    val IF_CAST_FROM_GRAVEYARD_COUNTERS_ON_TOKEN = Regex("""(?:Then )?[Ii]f this spell was cast from a graveyard, put (\w+) (\S+) counters? on that token\.""")
    val CHOOSE_SPELL_SURVEIL_COUNTER_UNLESS = Regex("""Choose target spell\. Surveil (\w+), then counter the chosen spell unless its controller pays (\{\d+\}) for each card in your graveyard\.""")
    val MAY_CAST_TARGET_FROM_GRAVEYARD_ANY_TYPE = Regex("""You may cast target (.+?) from a graveyard, and mana of any type can be spent to cast that spell\. If that spell would be put into a graveyard, exile it instead\.""")
    val ADDITIONAL_COST_OR = Regex("""As an additional cost to cast this spell, (sacrifice an? .+?) or (pay \{.+\})\.""")
    val ATTACKED_THIS_TURN_MAY_PLAY_TOP = Regex("""As long as (.+?) attacked this turn, you may play the top card of your library\.""")
    val WOULD_MILL_PLUS = Regex("""If an opponent would mill one or more cards, they mill that many cards plus (\w+) instead\.""")
    val COPY_ABILITY_X_TIMES = Regex("""(\{X\}\{X\}, \{T\}): Copy target activated or triggered ability you control X times\. You may choose new targets for the copies\. This ability can't be copied and X can't be 0\.""")
    // fin chunk 11
    val RETURN_TARGET_WITH_ADDITIONAL_COUNTERS = Regex("""Return target (.+?) from your graveyard to the battlefield with (\w+) additional (\S+) counters? on it\.""")
    val EACH_OPPONENT_DISCARDS_LOSES_EXILES = Regex("""Each opponent discards a card, loses (\d+) life, and exiles the top (\w+) cards of their library\.""")
    val EACH_PLAYER_SACRIFICES_HALF = Regex("""Each player sacrifices half the (.+?) they control of their choice, rounded (down|up)\.""")
    val EXILE_CARD_COPY_EXCEPT_DEMON = Regex("""Exile up to one target (.+?) from a graveyard\. If you exiled a card this way, create a token that's a copy of that card, except it's a (\d+)/(\d+) (black|white|blue|red|green) ([A-Z][a-z]+)\.""")
    val CREATE_INSTEAD_IF_CAST_FROM_GRAVEYARD = Regex("""(Create .+? tokens?)\. If this spell was cast from a graveyard, instead create X of those tokens\.""")
    val COUNTER_THEN_BECOMES_ARTIFACT_CREATURE = Regex("""Put a \+1/\+1 counter on (.+?)\. It becomes an? ([A-Z][a-z]+) artifact creature in addition to its other types until end of turn\.""")
    val EXILE_TOP_SACRIFICE_PLAY_ANY_TYPE = Regex("""Exile the top card of that player's library\. Then you may (sacrifice .+?)\. If you do, you may play the exiled card this turn, and mana of any type can be spent to cast it\.""")
    val PLAYER_GAINS_CONTROL_DRAW_TREASURE_LOSE = Regex("""That player gains control of (.+?)\. If they do, you draw that many cards, create that many tapped Treasure tokens, then lose that much life\.""")
    val HAS_A_COUNTER_ATTACK_DESPITE_DEFENDER = Regex("""As long as (.+?) has a counter on it, it can attack as though it didn't have defender\.""")
    val WOULD_DIE_INSTEAD_EXILE_GAIN = Regex("""If an? (.+?) would die, instead exile it and you gain (\d+) life\.""")
    val PUT_TARGET_EXILED_WITH_ONTO_BATTLEFIELD = Regex("""(.+?): Put target (.+?) exiled with (.+?) onto the battlefield tapped under your control with (\w+) additional \+1/\+1 counters on it\.""")
    val PLAYER_SACRIFICES = Regex("""a player sacrifices (another creature|a creature)""")
    // fin chunk 12
    val COPY_OF_IT_EQUIP_LESS_SACRIFICE = Regex("""Create a token that's a copy of it, except it has "This Equipment's equip abilities cost (\{\d+\}) less to activate\." Sacrifice that token at the beginning of the next (upkeep|end step)\.""")
    val LOOK_PUT_EQUIPMENT_ATTACH = Regex("""Look at the top (\w+) cards of your library\. You may put any number of Equipment cards from among them onto the battlefield\. Put the rest on the bottom of your library in a random order\. When you put one or more Equipment onto the battlefield this way, you may attach one of them to an? (.+)\.""")
    val DAMAGE_THEN_DESTROY_EQUIPMENT_ATTACHED = Regex("""(.+?) deals (\d+) damage to target creature\. Destroy up to one Equipment attached to that creature\.""")
    val SHUFFLE_MILL_PUT_CREATURES_HASTE_RETURN = Regex("""Shuffle your library, then mill (\w+) cards\. Put each creature card milled this way onto the battlefield\. They gain haste\. At the beginning of the next end step, return those creatures to their owner's hand\.""")
    val ADD_ONLY_EQUIPMENT = Regex("""Add ((?:\{[WUBRGC]\})+)\. Spend this mana only to cast an Equipment spell or activate an equip ability\.""")
    val DEALS_DAMAGE_EQUAL_TO_DEFENDING = Regex("""(.+?) deals damage equal to (the number of .+?) to defending player\.""")
    val DAMAGE_TARGET_AND_X_TO_CONTROLLER = Regex("""(.+?) deals (\d+) damage to target creature and (X|\d+) damage to (?:that creature's|its) controller\.""") // inr chunk 08: N, its
    val ATTACH_UP_TO_ONE_TARGET_TO_TARGET = Regex("""Attach up to one target (.+?) to target (.+)\.""")
    // bro chunk 03: "Pay life equal to this creature's power"
    val PAY_LIFE_EQUAL_TO_POWER = Regex("""Pay life equal to ([A-Z].*?|this [a-z]+)'s power""")
    val WHENEVER_COPY_OF_IT_EQUIP_LESS = Regex("""Whenever (.+?), create a token that's a copy of it, except it has "This Equipment's equip abilities cost (\{\d+\}) less to activate\." Sacrifice that token at the beginning of the next (upkeep|end step)\.""")
    // fin chunk 13
    val DOUBLE_SELF_POWER_EOT = Regex("""(Whenever .+?), double (.+?)'s power until end of turn\.""")
    val FIRST_SPELL_EACH_TURN_ENTERS_WITH = Regex("""Whenever you cast your first (.+?) spell each turn, (that creature enters with .+)""")
    val EACH_OTHER_DEALS_DAMAGE_EQUAL_POWER = Regex("""When (.+?) enters, each other (.+?) deals damage equal to its power to target (.+)\.""")
    val DAMAGE_NOT_REMOVED = Regex("""Damage isn't removed from (.+?) during cleanup steps\.""")
    val PREVENT_COMBAT_DAMAGE_TO_SELF = Regex("""Prevent all combat damage that would be dealt to (.+)\.""")
    val COUNTERS_DOUBLED = Regex("""If one or more (\S+) counters would be put on an? (.+?), twice that many (\S+) counters are put on that creature instead\.""")
    val TOKENS_PLUS_ONE = Regex("""If one or more tokens would be created under your control, those tokens plus an? (.+? token) are created instead\.""")
    val PLAY_LANDS_AND_CAST_SUBTYPE_FROM_TOP = Regex("""You may play lands and cast ([A-Z][a-z]+) spells from the top of your library\.""")
    val ENTERING_CAUSES_TRIGGER_ADDITIONAL = Regex("""If an? (.+?) entering the battlefield causes a triggered ability of an? (.+?) to trigger, that ability triggers an additional time\.""")
    val WHEN_YOU_SEARCH_THIS_WAY = Regex("""When you search your library this way, (.+)""")
    val SEARCH_UP_TO_DIFFERENT_NAMES = Regex("""Search your library for up to (\w+) (.+?) with different names, put them onto the battlefield tapped, then shuffle\.""")
    val SELF_BECOMES_CREATURE = Regex("""(.+?) becomes an? (\d+)/(\d+) ([A-Z][a-z]+) creature in addition to its other types\.""")
    val UNTIL_EOT_GETS_AND_GAINS_QUOTE = Regex("""Until end of turn, target (.+?) gets \+(\d+)/\+(\d+) and gains "(.+)"\.?""")
    val COUNTERS_EQUAL_ITS_POWER_ON_UP_TO_ONE = Regex("""Put a number of (\S+) counters equal to its power on up to one target (.+)\.""")
    val UNTIL_EOT_SELF_GETS_FOR_EACH = Regex("""Until end of turn, (.+?) gets \+1/\+1 for each (.+)\.""")
    val MILL_MAY_PUT_IF_TOWN_GAIN = Regex("""Mill (\w+) cards\. You may put an? (.+?) from among them into your hand\. If you put an? (.+?) into your hand this way, you gain (\d+) life\.""")
    val EXILE_IT_AND = Regex("""Exile it and (create .+)\.""")
    val CREATE_N_TAPPED_PREDEFINED = Regex("""Create (\w+) tapped (Treasure|Food|Clue) tokens?\.""")
    // fin chunk 14
    val VEHICLE_CREWED_BY_ATTACKS_EXTRA_COMBAT = Regex("""Whenever a Vehicle crewed by (.+?) this turn attacks, if it's the first combat phase of the turn, you may pay ((?:\{[^}]+\})+)\. If you do, after this phase, there is an additional combat phase\.""")
    val BIRDS_ATTACK_LOOK_THAT_MANY = Regex("""Whenever one or more (.+?) attack, look at that many cards from the top of your library\. You may put one of them into your hand\. Then put any number of land cards from among them onto the battlefield tapped and the rest into your graveyard\.""")
    val REMOVE_LORE_FROM_ANY_NUMBER = Regex("""Whenever (.+?) attacks, you may remove a lore counter from each of any number of Sagas you control\. Put a \+1/\+1 counter on (.+?) for each lore counter removed this way\.""")
    val END_STEP_ARTIFACTS_RETURN_THEN_LOSE = Regex("""At the beginning of your end step, if you control (\w+) or more artifacts, return target creature card from your graveyard to your hand\. Then if you control (\w+) or more artifacts, each opponent loses life equal to that card's power\.""")
    val ENTERS_LOOK_PUT_LAND = Regex("""When (.+?) enters, look at the top (\w+) cards of your library\. You may put a land card from among them onto the battlefield tapped\. Put the rest on the bottom of your library in a random order\.""")
    val EXILE_CARD_IF_CREATURE_FOOD = Regex("""(.+?): Exile target card from a graveyard\. If a creature card was exiled this way, create a Food token\.""")
    val COUNTERS_EQUAL_POWER_BECOMES_TYPE = Regex("""At the beginning of combat on your turn, put a number of \+1/\+1 counters equal to (.+?)'s power on up to one other target creature\. That creature becomes an? ([A-Z][a-z]+) in addition to its other types\.""")
    val DIES_DURING_YOUR_TURN_DRAW_POWER = Regex("""Whenever an? ([A-Z][a-z]+) you control dies during your turn, you draw cards equal to its power\.""")
    val DAMAGE_THEN_DOUBLE_DAMAGE_TO_PLAYER = Regex("""Whenever (.+?) deals combat damage to a player, until your next turn, if a source would deal damage to that player or a permanent that player controls, it deals double that damage instead\.""")
    val CAST_FROM_GRAVEYARD_PAYING_LIFE_FINALITY = Regex("""You may cast (.+?) spells from your graveyard by paying (\d+) life in addition to paying their other costs\. If you cast a spell this way, that \w+ enters with a finality counter on it\.""")
    val FOR_EACH_OPPONENT_TAP_STUN_GAIN = Regex("""When (.+?) enters, for each opponent, tap up to one target nonland permanent that opponent controls\. Put X stun counters on each of those permanents and you gain X life, where X is the number of (.+?)\.""")
    val EXILE_RANDOM_COPY_REPEAT = Regex("""Whenever (.+?) enters or attacks, exile a permanent card from your graveyard at random, then create a tapped token that's a copy of that card\. If the exiled card is a land card, repeat this process\.""")
    val IF_TOWNS_CREATE_ALL_COLORS = Regex("""At the beginning of combat on your turn, if you control (\w+) or more Towns, create a (\d+)/(\d+) ([A-Z][a-z]+) creature token that's all colors\.""")
    val DURING_YOUR_TURN_SELF_AND_HAVE = Regex("""During your turn, (.+?) and (.+? you control) have (.+)\.""")
    val END_STEP_RETURN_UP_TO_ONE_FINALITY = Regex("""At the beginning of your end step, return up to one target (.+?) from your graveyard to the battlefield with a finality counter on it\.""")
    // fin chunk 15
    val COUNTER_ON_LAND_LOSES_TYPES = Regex("""Whenever (.+?) attacks, put an? (\S+) counter on target land\. For as long as that land has an? \S+ counter on it, it loses all land types and abilities and has "\{T\}: Add \{C\}\."""")
    val TAP_LAND_FOR_ADD_ADDITIONAL = Regex("""Whenever you tap a land for (\{[WUBRGC]\}), add an additional (\{[WUBRGC]\})\.""")
    val ACTIVATE_ONLY_IF_LEGEND_DEALT_DAMAGE = Regex("""(.+?): Draw (\w+) cards\. Activate only if an opponent was dealt combat damage by a legendary creature this turn\.""")
    val SHUFFLE_GRAVEYARD_GAIN_PER_CARD = Regex("""(.+?): Shuffle all (.+?) from your graveyard into your library\. You gain life equal to the number of cards shuffled into your library this way\.""")
    val BASE_POWER_BECOMES = Regex("""(.+?): Until end of turn, (.+?)'s base power becomes equal to (the number of .+)\.""")
    val CANT_BE_BLOCKED_EXCEPT_BY_N = Regex("""(.+?) can't be blocked except by (\w+) or more creatures\.""")
    val MAY_ATTACH_IF_YOU_DO_FIRST_STRIKE_LURE = Regex("""When (.+?) enters, you may attach it to target creature you control\. If you do, that creature gains first strike until end of turn and must be blocked this turn if able\.""")
    val PLAY_HIDEAWAY_IF_LEGENDS = Regex("""(.+?): You may play the exiled card without paying its mana cost if you control (\w+) or more legendary creatures\.""")
    val ENTERS_TAPPED_UNLESS_EARLY_TURN = Regex("""(.+?) enters tapped unless it's your first, second, or third turn of the game\.""")
    /** A meld trigger: "At the beginning of <step>, if you both own and control X and a creature named Y, [you may pay {cost}. If you do, ]exile them, then meld them into Z." */
    // bro chunk 04: a land or artifact partner, and another condition before it ("if there are four or more land cards in your graveyard and …")
    val MELD_AT_STEP = Regex("""At (the beginning of .+?), if (?:(.+?) and )?you both own and control (.+?) and an? (creature|land|artifact) named (.+?), (?:you may pay ((?:\{[^}]+\})+)\. If you do, )?exile them, then meld them into (.+)\.""")
    /** A meld activated ability: "<cost>: If you both own and control X and a creature named Y, exile them, then meld them into Z." */
    val MELD_ACTIVATED = Regex("""(.+?): If you both own and control (.+?) and an? (creature|land|artifact) named (.+?), exile them, then meld them into (.+?)\.( Activate only as a sorcery\.)?""")
    val DIES_DESTROY_AND_RETURN = Regex("""When (.+?) dies, destroy target permanent and return target (.+?) from your graveyard to the battlefield\.""")
    val COUNTER_ON_IT_FOR_EACH = Regex("""Put an? (\S+) counter on it for each (.+)\.""")
}
