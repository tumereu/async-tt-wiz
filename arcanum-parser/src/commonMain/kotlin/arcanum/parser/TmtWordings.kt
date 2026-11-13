package arcanum.parser

import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Value
import arcanum.api.Zone

/** Regular expressions of Teenage Mutant Ninja Turtles (tmt) wordings read in [Sentences]. */
internal object Tmt {
    // tmt chunk 01: names that are plural ("Don & Raph attack", "The Neutrinos get +1/+0"): the verb read as singular
    val PLURAL_VERB = Regex("""\b(attack|deal|enter|get|gain|block|have)\b""")
    // tmt chunk 01: keywords whose cost isn't mana ("Equip—Sacrifice another nonland permanent. Activate only once each turn.")
    val EQUIP_COST = Regex("""Equip—(.+?)\.( Activate only once each turn\.)?""")
    val KICKER_COST = Regex("""Kicker—(.+?)\.?""")

    // tmt chunk 02: sneak (CR 702.190)
    val SNEAK_PAID = Regex("""(?:this spell's|his|her|its|their|(.+)'s) sneak cost was paid( this turn)?""")
    val SNEAK_THEY_ENTER_TAPPED_AND_ATTACKING = Regex("""If (.+) was paid, (?:they enter|it enters) tapped and attacking\.""")
    val SNEAK_INSTEAD_EXILE = Regex("""(.+?)\. Exile that creature until (.+?) leaves the battlefield\. If (.+?), instead exile the chosen creature\.""")
    val CHOOSE_TARGET = Regex("""[Cc]hoose target (.+)""")
    val IF_INSTEAD_RETURN_TO_BATTLEFIELD = Regex("""If (.+?), instead return that card to the battlefield\.""")

    // tmt chunk 03: the next end step, "you may pay … If you don't, …"
    val NEXT_END_STEP_MAY_PAY = Regex("""(.+?)\. At the beginning of the next end step, you may pay (\{[^.]+\})\. If you don't, (.+)""")
    val YOU_CREATE = Regex("""You create (.+)""")
    val MILL_DRAW_THEN_DISCARD = Regex("""[Mm]ill (\w+) cards?, draw (\w+) cards?, then discard (\w+) cards?\.""")

    // tmt chunk 04: other wordings
    val GREATER_POWER = Regex("""(.+) can't be blocked by creatures with greater power\.""")
    val OTHER_THAN_SELF = Regex("""(.+) other than (.+)""")
    val MAY_ATTACH_TO_IT = Regex("""[Yy]ou may attach (.+) to it\.""")
    val CANT_ATTACK_UNLESS_CONTROL = Regex("""(.+) can't attack unless you control (.+)\.""")
    val SPELL_THAT_TARGETS = Regex("""spell that targets (?:an? )?(.+)""")
    val MILLS_HALF_LIBRARY = Regex("""(Target opponent|Target player|Each opponent) mills half their library, rounded (up|down)\.""")
    val DRAW_EQUAL_TO_DIFFERENCE = Regex("""[Dd]raw cards equal to the difference\.""")
    val FEWER_THAN_IN_HAND = Regex("""you have fewer than (\w+) cards in hand""")
    val BECOMES_ARTIFACT_CREATURE_BASE = Regex("""Until end of turn, target (.+) becomes an artifact creature with base power and toughness (\d+)/(\d+)\.""")
    val BECOMES_COLOR_OF_CHOICE_HEXPROOF = Regex("""Until end of turn, (.+) becomes the color of your choice and gains hexproof from that color\.""")
    val ENCHANTED_IS_SUBTYPE_BASE_PT = Regex("""Enchanted creature is an? ([A-Z]\w+) with base power and toughness (\d+)/(\d+)\. It can't attack and loses all abilities\.""")
    val UNTIL_EOT_GAINS_AND_QUOTED = Regex("""Until end of turn, (another )?target (.+?) gains ([a-z ,]+?) and "(.+)"\.?""") // fdn: another
    val RETURN_TARGET_AND_SAME_NAME = Regex("""Return target (.+?) and all other cards with the same name as that card from your graveyard to the battlefield( tapped)?\.""")
    val NUMBER_OF_COUNTERS_ON = Regex("""the number of counters on (.+)""")
    val PUT_FROM_THAT_PLAYERS_GRAVEYARD = Regex("""[Pp]ut up to one target (.+?) from that player's graveyard onto the battlefield under your control\. It enters tapped and attacking that player\.""")
    val DESTROY_IF_TYPE_DESTROYED = Regex("""Destroy target (.+?)\. If an? (\w+) was destroyed this way, (.+)""")
    val GAIN_LIFE_EQUAL_TO_ITS = Regex("""[Yy]ou gain life equal to its (power|toughness)\.""")
    val KICKED_INSTEAD = Regex("""If this spell was kicked, that creature gets ([+-]\d+)/([+-]\d+) until end of turn instead\.""")
    val GAIN_CONTROL_OF_ALL_UNTIL_EOT = Regex("""Gain control of all (.+?) until end of turn\. Untap them\. They gain (\w+) until end of turn\.""")
    val NEXT_UPKEEP_DISCARD_RANDOM = Regex("""At the beginning of your next upkeep, discard (\w+) cards? at random\.""")
    val MAY_PUT_FROM_HAND_ON_BOTTOM_DRAW = Regex("""You may put a card from your hand on the bottom of your library\. If you do, draw a card\.""")
    val SPEND_ONLY_ARTIFACT_OR_ABILITY = Regex("""\{T\}: Add (\{[WUBRGC]\})\. Spend this mana only to cast an? (\w+) spell or to activate an ability\.""")
    val UNTIL_EOT_MAY_PLAY_EXILED_WITH = Regex("""until end of turn, you may play a card exiled with (.+)\.""")
    val VERB_ATTACHED = Regex("""(Exile) (?:enchanted|equipped) creature\.""")
    val RETURN_IT_TAPPED_UNDER_OWNER = Regex("""Return it to the battlefield tapped under its owner's control\.""")
    val PUT_TARGET_FROM_GRAVEYARD_ON_TOP = Regex("""Put target (.+?) from your graveyard on top of your library\.""")
    val UP_TO_ONE_TARGET_GETS = Regex("""[Uu]p to one target (.+?) gets ([+-](?:X|\d+))/([+-](?:X|\d+)) until end of turn\.""")
    val PUT_COUNTERS_ON_UP_TO_ONE = Regex("""[Pp]ut (\w+) (\S+) counters? on up to one (other )?target (.+)\.""")
    val EXILE_THEN_COPY_IT = Regex("""Exile target (.+?)\. Create a token that's a copy of it, except it isn't legendary and is an? ([A-Z]\w+) in addition to its other types\.""")
    val DEALS_DAMAGE_EQUAL_TO_TARGET = Regex("""(.+?) deals damage equal to (.+) to target (.+)\.""")
    val IF_IT_ISNT_CREATURE_BECOMES = Regex("""If it isn't a creature, it becomes an? (\d+)/(\d+) ([A-Z][A-Za-z ]+?) creature in addition to its other types\.""")
    val WITH_A_COUNTER_ON_IT = Regex("""(.+) with a counter on (?:it|them)""")
    val LIST_OR_CREATURE_WITH = Regex("""(.+), or (creature with .+)""")
    val MAY_PUT_AND_OR_FROM_HAND = Regex("""[Yy]ou may put an? (\w+) card and/or an? (\w+) card from your hand onto the battlefield\.""")
    val WHENEVER_THIS_TURN = Regex("""Whenever (.+?) this turn, (.+\. .+)""")
    val IT_GAINS_UNTIL_EOT = Regex("""It gains ([a-z ,]+) until end of turn\.""")
    val ACT_ON_IT_NEXT_END_STEP = Regex("""(Destroy|Sacrifice) it at the beginning of the next end step\.""")
    val EXILE_TARGET_YOU_OWN = Regex("""Exile target (.+?) you own\.""")
    val ADD_THAT_MUCH = Regex("""[Aa]dd that much (\{[WUBRGC]\})\.""")
    val ENTERS_OR_DEALS_COMBAT_DAMAGE = Regex("""(.+) enters or deals combat damage to a player""")
    val RETURN_ONE_OR_TWO_FROM_GRAVEYARD = Regex("""Return one or two target (.+?) cards(?: each( with .+?))? from your graveyard to (?:the |your )(battlefield|hand)\.""")
    val DEALS_TO_ANY_AND_TO_YOU = Regex("""(.+?) deals (\d+) damage to any target and (\d+) damage to you\.""")
    val MAY_SEARCH_IF_YOU_DONT_PUT = Regex("""[Yy]ou may search your library for an? (.+?) card, reveal it, put it into your hand, then shuffle\. If you don't put a card into your hand this way, (.+)""")
    val MAY_REMOVE_COUNTER_WHEN_YOU_DO = Regex("""[Yy]ou may remove a counter from (.+?)\. When you do, (.+)""")
    val LOOK_PUT_TOTAL_MANA_VALUE = Regex("""Look at the top (\w+) cards of your library\. Put up to (\w+) (.+?) with total mana value (\d+) or less from among them onto the battlefield and the rest on the bottom of your library in a random order\.""")
    val ACTIVATED_ABILITIES_COST_LESS = Regex("""Activated abilities of (.+?) cost (\{\d+\}) less to activate\.""")
    val SEARCH_UP_TO_PUT_THEM_TAPPED = Regex("""Search your library for (?:up to )?(\w+) (.+?) cards, put them onto the battlefield( tapped)?, then shuffle\.""") // fdn chunk 05: "for two" (fewer may be found, CR 701.23b), untapped
    val ATTACH_WHEN_YOU_DO_FIGHTS = Regex("""[Aa]ttach it to target (.+?)\. When you do, equipped creature fights up to one target (.+)\.""")
    // bro chunk 01: "Each player who controls an artifact or enchantment creates ..."
    val EACH_PLAYER_CREATES = Regex("""[Ee]ach player(?: who controls (.+?))? creates (.+)\.""")
    val OPPONENTS_ARE_ATTACKED = Regex("""one or more of your opponents are attacked""")
    val SACRIFICE_UNLESS_DISCARD = Regex("""[Ss]acrifice an? (.+?) unless you discard a card\.""")
    val RETURN_IF_ARTIFACT_CREATURE_OTHERWISE = Regex("""Choose target (.+?)\. Return it to the battlefield if it's an artifact creature card\. Otherwise, return it to the battlefield and it's an? (\d+)/(\d+) ([A-Z]\w+) artifact creature with ([a-z ,]+)\.""")
    val THEN_IF_CONTROL_LOSES_HALF = Regex("""Then if you control (\w+) or more (.+?), that player loses half their life, rounded (up|down)\.""")
    val EXILE_TWO_THEN_RETURN_THEM = Regex("""[Ee]xile up to one target (.+?) and up to one target (.+?)\. Then return them to the battlefield under their owners' control\.""")
    val NEXT_SPELL_HAS = Regex("""the next (.+?) spell you cast this turn has (.+?)\.""")
    val EXILE_THEN_RETURN = Regex("""[Ee]xile (up to one )?target (.+?), then return it to the battlefield under (its owner's|your) control( tapped and attacking)\.""")
    val EXILE_UNTIL_NONLAND = Regex("""[Ee]ach opponent exiles cards from the top of their library until they exile a (\w+) card\.""")
    val MAY_CAST_EXILED_WITH_FREE = Regex("""[Yy]ou may cast a card exiled with (.+) without paying its mana cost\.""")
    val MAY_PLAY_LANDS_AND_CAST_FROM_TOP = Regex("""You may play lands and cast (.+?) spells from the top of your library\. If you cast a creature spell this way, that creature enters with an additional \+1/\+1 counter on it\.""")
    val YOU_PUT_A_COUNTER_ON = Regex("""you put a counter on (.+)""")
    val FROM_OUTSIDE_THE_GAME = Regex("""[Yy]ou may put a card you own from outside the game into your hand\.""")
    val FIRST_COMBAT_PHASE = Regex("""it's the first combat phase of the turn""")
    val UNTAP_ONE_OR_TWO_ADDITIONAL_COMBAT = Regex("""[Uu]ntap one or two target (.+?)\. After this phase, there is an additional combat phase\.""")
    val REVEAL_UNTIL_TAPPED_ATTACKING = Regex("""[Rr]eveal cards from the top of your library until you reveal an? (.+?) card\. Put that card onto the battlefield tapped and attacking and the rest on the bottom of your library in a random order\.""")
    val IF_ITS_MANA_VALUE_WAS = Regex("""If its mana value was (\d+) or (less|greater), (.+)""")
    val MANA_SPENT_LESS_THAN_MANA_VALUE = Regex("""the amount of mana spent to cast it was less than its mana value""")
    val TOKEN_COPY_GAINS_HASTE_SACRIFICE = Regex("""Create a token that's a copy of (.+?)\. That token gains haste\. Sacrifice it at the beginning of the next end step\.""")
    val TARGET_PLAYER_GAINS_AND_DRAWS = Regex("""Target player gains (\d+) life and draws (\w+) cards?\.""")
    val EACH_OF_YOUR_OPPONENTS = Regex("""Each of your opponents (.+)""")
    val TOKEN_FOR_EACH_COUNTER_ON_IT = Regex("""[Cc]reate an? (\w+) token for each (\+1/\+1) counter on it\.""")
    val CREATURE_THAT_CREWED_IT = Regex("""(.+) that crewed it this turn""")
    val THEN_IF_DOUBLE_COUNTERS = Regex("""Then if that creature is (?:an? )?(.+?), double the number of (\+1/\+1) counters on it\.""")
    val RETURN_EXILED_WITH_CREATURE_AND_OTHERS = Regex("""Return a (.+?) exiled with (.+?) to the battlefield under your control\. Return each other card exiled with (.+?) to its owner's hand\.""")
    val CARD_TYPES_AMONG_SPELLS_CAST = Regex("""[Dd]raw a card for each card type among spells you've cast this turn\.""")
    val EXCHANGE_CONTROL_DIFFERENT_PLAYERS = Regex("""[Yy]ou may exchange control of two other target (.+?) controlled by different players\.""")
    val SEARCH_AND_OR_OUTSIDE = Regex("""Search your library and/or outside the game for exactly (\w+) (.+?) you own with different names, then reveal those cards\. An opponent chooses (\w+) of them\. Put the chosen cards into your hand and shuffle the rest into your library\.""")
    val SHUFFLE_HAND_AND_GRAVEYARD_DRAW = Regex("""Each player may shuffle their hand and graveyard into their library, then each player who does draws (\w+) cards\.""")
    val PAY_LIFE_EQUAL_TO_POWER_COUNTERS = Regex("""[Yy]ou may pay life equal to its power\. If you do, put that many \+1/\+1 counters on it\.""")
    val CARDS_HAVE_SNEAK = Regex("""Creature cards in your graveyard have sneak (\{.+\})\.""")
    val CAST_USING_SNEAK = Regex("""You may cast creature spells from your graveyard using their sneak abilities\.""")
    val DURING_YOUR_TURN_CAST_FROM_GRAVEYARD_FINALITY = Regex("""During your turn, you may cast (.+?) spells with power or toughness (\d+) or less from your graveyard\. If you cast a spell this way, that creature enters with a finality counter on it\.""")
    val CLONING_COPY_EXCEPT = Regex("""Create a token that's a copy of (it|a card exiled with this Saga), except it isn't legendary and is an? ([A-Z]\w+) in addition to its other types\.""")
}

/** Phrases of Teenage Mutant Ninja Turtles (tmt) wordings: conditions and nouns. */
internal object TmtPhrases {
    /** tmt chunk 02: "his sneak cost was paid", "if this spell's sneak cost was paid" (CR 702.190); "this turn": it entered this
     * turn too. */
    fun condition(text: String, self: Set<String>): Condition? {
        Tmt.SNEAK_PAID.matchEntire(text)?.let { m ->
            if (m.groupValues[1].isNotEmpty() && m.groupValues[1] !in self && m.groupValues[1].replaceFirstChar { it.lowercase() } !in self) return null
            val paid = Condition.Paid(Keyword.Sneak.Kind, times = 1, of = Objects.Self)
            if (m.groupValues[2].isEmpty()) return paid
            return Condition.And(listOf(paid, Condition.ObjectMatches(Objects.Self, ObjectFilter.ThisTurn(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)))))
        }
        // tmt chunk 04: "you have fewer than four cards in hand"
        Tmt.FEWER_THAN_IN_HAND.matchEntire(text)?.let { m ->
            return Condition.Compare(Value.HandSize(Players.You), Comparison.Less, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
        }
        // tmt chunk 07: "it's the first combat phase of the turn": the beginning of combat step has begun once this turn (CR 506.1)
        if (Tmt.FIRST_COMBAT_PHASE.matches(text)) return Condition.Compare(Value.EventsThisTurn(EventPattern.StepBegins(arcanum.api.TurnStep.BeginningOfCombat,
            PlayerFilter.Any), arcanum.api.Measure.Events), Comparison.AtMost, Value.Fixed(1))
        // tmt chunk 06: "the amount of mana spent to cast it was less than its mana value" (CR 601.2h, 202.3)
        if (Tmt.MANA_SPENT_LESS_THAN_MANA_VALUE.matches(text)) {
            val spell = Objects.EventObject(arcanum.api.EventRole.Object)
            return Condition.Compare(Value.ManaSpent(of = spell), Comparison.Less, Value.Of(NumericProperty.ManaValue, spell, Combine.Single))
        }
        return null
    }

    /** tmt chunk 04: "spell that targets an artifact or creature you control", "Dinosaurs you control other than this creature". */
    fun noun(phrase: String): ObjectFilter? {
        // tmt chunk 02: "Dinosaurs you control other than this creature"
        Tmt.OTHER_THAN_SELF.matchEntire(phrase)?.takeIf { it.groupValues[2].startsWith("this ") }?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.Not(ObjectFilter.Is(Objects.Self)))
        }
        // tmt chunk 01: "creature you control with a counter on it" (of any kind)
        Tmt.WITH_A_COUNTER_ON_IT.matchEntire(phrase)?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.HasCounter(null, atLeast = 1))
        }
        // tmt chunk 01: "artifact, enchantment, or creature with flying": the quality is the creature's
        Tmt.LIST_OR_CREATURE_WITH.matchEntire(phrase)?.let { m ->
            val first = m.groupValues[1].split(", ").map { Filters.noun(it) ?: return null }
            return ObjectFilter.Or(first + (Filters.noun(m.groupValues[2]) ?: return null))
        }
        Tmt.SPELL_THAT_TARGETS.matchEntire(phrase)?.let { m ->
            val targeted = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Spell), ObjectFilter.Targets(objects = Objects.Each(targeted)))
        }
        Tmt.CREATURE_THAT_CREWED_IT.matchEntire(phrase)?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.CrewedThisTurn(Objects.Self))
        }
        return null
    }

    /** tmt chunk 04: "the number of counters on Savanti Romero" (of every kind). */
    fun value(text: String, self: Set<String>): Value? {
        Tmt.NUMBER_OF_COUNTERS_ON.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Value.Of(NumericProperty.Counters(null), Objects.Self, Combine.Single)
        }
        return null
    }

    val PLAYER: PlayerFilter = PlayerFilter.Any
}
