package arcanum.parser

import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.Measure
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Value

/** tla: conditions and values of Avatar: The Last Airbender wordings, tried first by [Conditions] and [Values]. */
internal object TlaPhrases {
    // tla chunk 02: "there's a Lesson card in your graveyard", "there is a Lesson card in your graveyard"
    private val THERES_A = Regex("""there(?:'s| is) an? (.+? in your graveyard)""")
    // tla chunk 03: "you sacrificed a permanent this turn"
    private val YOU_SACRIFICED_A = Regex("""you sacrificed an? (\w+) this turn""")
    // tla chunk 03: "A or B" of two conditions ("you control a land creature or a land entered the battlefield under your control this turn")
    private val CAST_IT_AND = Regex("""you cast (?:it|them) and (.+)""")
    private val EITHER = Regex("""(you control .+?) or (an? .+ this turn)""")

    fun condition(text: String, self: Set<String>): Condition? {
        THERES_A.matchEntire(text)?.let { m -> return Condition.Exists(Filters.noun(m.groupValues[1]) ?: return null, atLeast = 1) }
        YOU_SACRIFICED_A.matchEntire(text)?.let { m ->
            val kind = if (m.groupValues[1] == "permanent") null else Filters.noun(m.groupValues[1]) ?: return null
            val yours = ObjectFilter.ControlledBy(PlayerFilter.You)
            return Condition.HappenedThisTurn(EventPattern.Sacrifice(if (kind == null) yours else Filters.all(kind, yours)), atLeast = 1, measure = Measure.Objects)
        }
        // tla chunk 03: "you cast them and there are three or more Dragon and/or Lesson cards in your graveyard" (CR 601.2)
        CAST_IT_AND.matchEntire(text)?.let { m ->
            return Condition.And(listOf(Condition.CastFrom(null, Objects.Self), Conditions.condition(m.groupValues[1], self) ?: return null))
        }
        EITHER.matchEntire(text)?.let { m ->
            val first = Conditions.condition(m.groupValues[1], self) ?: return null
            val second = Conditions.condition(m.groupValues[2], self) ?: return null
            return Condition.Or(listOf(first, second))
        }
        return null
    }

    // tla chunk 03: "the number of permanents you've sacrificed this turn"
    private val SACRIFICED_THIS_TURN = Regex("""the number of (\w+?)s you've sacrificed this turn""")
    // tla chunk 05: "the number of +1/+1 counters on lands you control"
    private val COUNTERS_ON = Regex("""the number of (\S+) counters on (.+)""")
    // tla chunk 05: "2 plus the number of Lesson cards in your graveyard"
    private val PLUS = Regex("""(\d+) plus (.+)""")

    fun value(text: String): Value? {
        SACRIFICED_THIS_TURN.matchEntire(text)?.let { m ->
            val kind = if (m.groupValues[1] == "permanent") null else Filters.noun(m.groupValues[1]) ?: return null
            val yours = ObjectFilter.ControlledBy(PlayerFilter.You)
            return Value.EventsThisTurn(EventPattern.Sacrifice(if (kind == null) yours else Filters.all(kind, yours)), Measure.Objects)
        }
        COUNTERS_ON.matchEntire(text)?.let { m ->
            val kind = Counters.kind(m.groupValues[1]) ?: return null
            return Value.Of(NumericProperty.Counters(kind), Objects.Each(Filters.noun(m.groupValues[2]) ?: return null), Combine.Total)
        }
        PLUS.matchEntire(text)?.let { m -> return Value.Sum(listOf(Value.Fixed(m.groupValues[1].toInt()), Values.value(m.groupValues[2]) ?: return null)) }
        return null
    }
}

/** Regular expressions of Avatar: The Last Airbender (tla) wordings read in [Sentences]. */
internal object Tla {
    // tla chunk 01: airbend (CR 701.65), earthbend (CR 701.66), waterbend (CR 701.67), firebending (CR 702.189)
    val TARGET_PHRASE = Regex("""(up to one |up to two |any number of |one or two )?(other |another )?target (.+)""")
    val AIRBEND = Regex("""[Aa]irbend (.+)\.""")
    val CHOOSE_UP_TO_ONE_THEN_AIRBEND_ALL_OTHER = Regex("""Choose up to one target (\w+), then airbend all other (\w+)\.""")
    val EARTHBEND = Regex("""(?:[Yy]ou )?[Ee]arthbend (\d+|X)\.""")
    val EARTHBEND_TWICE = Regex("""[Ee]arthbend (\d+), then earthbend (\d+)\.""")
    val WATERBEND_COST = Regex("""[Ww]aterbend ((?:\{[^}]+\})+)""")
    val MAY_WATERBEND_ADDITIONAL = Regex("""As an additional cost to cast this spell, you may waterbend ((?:\{[^}]+\})+)\.""")
    val FIREBENDING_X = Regex("""Firebending X, where X is (.+?)\.""")
    val YOU_BEND = Regex("""you (waterbend|earthbend|firebend|airbend)(?:, (waterbend|earthbend|firebend|airbend))*(?:,? or (waterbend|earthbend|firebend|airbend))?""")
    val UNLESS_YOU_WATERBEND = Regex("""(.+) unless you waterbend ((?:\{[^}]+\})+)\.""")
    val LOSE_UNSPENT_RED_MANA = Regex("""Until end of turn, you don't lose unspent (\w+) mana as steps and phases end\.""")

    // tla chunk 02: white and blue wordings
    val KICKED_SEARCH_TWO = Regex("""Search your library for (a basic land card)\. If this spell was kicked, instead search your library for a basic land card and (an? .+? card)\. Reveal those cards, put them into your hand, then shuffle\.""")
    val GAIN_ALL_BASIC_LAND_TYPES = Regex("""(.+) gain all basic land types until end of turn\.""")
    val EXILE_UNTIL_LEAVES = Regex("""[Ee]xile (.+?target .+?) until (.+) leaves the battlefield\.""")
    val EACH_PLAYER_KEEPS_TOTAL_POWER = Regex("""Each player chooses any number of creatures they control with total power (\d+) or less, then sacrifices all other creatures they control\.""")
    val BECOMES_TYPE_AND_GAINS = Regex("""Until end of turn, target (.+?) becomes an? ([A-Z]\w+) in addition to its other types and gains (.+)\.""")
    val UNTAP_ONE_OR_TWO_THEY_GET = Regex("""Untap (one or two|up to two) target (.+?)\. They each get ([+-]\d+)/([+-]\d+) until end of turn\.""")
    val CREATE_FOR_EACH = Regex("""[Cc]reate (an? .+? tokens?(?: with .+?)?) for each (.+)\.""")
    val STARTING_WITH_YOU_EACH_CHOOSES_EXILE = Regex("""Starting with you, each player chooses up to one (.+?) from among (.+?)\. Exile those permanents\.""")
    val FIRST_SPELL_DURING_YOUR_TURNS_COSTS_LESS = Regex("""The first (.+?) you cast during each of your turns costs ((?:\{[^}]+\})+) less to cast\.""")
    val DEALS_EXCESS_GAIN = Regex("""(.+) deals (\d+) damage to target (.+?)\. You gain life equal to the excess damage dealt this way\.""")
    val CHOOSE_TAP_MAY_SACRIFICE_STUN = Regex("""Choose target (.+?)\. Tap it, then you may sacrifice (an? .+?)\. If you do, put a stun counter on the chosen creature\.""")
    val LOOK_PUT_ONE_EACH_IF = Regex("""Look at the top (\w+) cards of your library\. Put one of those cards into your hand and the rest on the bottom of your library in any order\. Put each of those cards into your hand instead if (.+)\.""")
    val RETURN_IF_CONTROLLED_DRAW = Regex("""Return target (.+?) to its owner's hand\. If you controlled that permanent, draw a card\.""")
    val TAP_UP_TO_X_DISTRIBUTE_STUN = Regex("""Tap up to X target (.+?), then distribute (\w+) stun counters among any number of (.+)\.""")
    val COPY_EXCEPT_NOT_LEGENDARY_PT_TYPE = Regex("""Create a token that's a copy of target (.+?), except it's not legendary and it's an? (\d+)/(\d+) ([A-Z]\w+) in addition to its other types\.""")
    val AS_LONG_AS_THERES = Regex("""(.+) gets ([+-]\d+)/([+-]\d+) as long as (there(?:'s| is| are) .+)\.""")
    val HAS_BASE_PT_UNTIL_EOT = Regex("""(.+) has base power and toughness (\d+)/(\d+) until end of turn\.""")
    val ENCHANTED_LOSES_ALL_IS_NAMED = Regex("""Enchanted creature loses all abilities and is an? ([A-Z]\w+) with base power and toughness (\d+)/(\d+) and "(.+)" named (.+)\.""")
    val BECOMES_ARTIFACT_CREATURE = Regex("""(.+) becomes an artifact creature\.""")
    val CANT_BLOCK_OR_BE_BLOCKED_BY = Regex("""(.+) can't block or be blocked by (.+)\.""")
    val OWNER_PUTS_SECOND_OR_BOTTOM = Regex("""The owner of target (.+?) puts it into their library second from the top or on the bottom\.""")
    val YOU_CONTROL_NEXT_COMBAT = Regex("""You control target opponent during their next combat phase\. If this spell's additional cost was paid, you control that player during their next turn instead\.""")
    val CAST_AS_THOUGH_FLASH_IF = Regex("""If (.+), you may cast this spell as though it had flash\.""")
    val DRAW_IF_PAID_INSTEAD_REVIVAL = Regex("""Draw two cards\. If this spell's additional cost was paid, instead shuffle your graveyard into your library, draw seven cards, and you have no maximum hand size for the rest of the game\.""")
    val LOOT_WHEN_DISCARD_NONLAND = Regex("""[Dd]raw a card, then discard a card\. When you discard a nonland card this way, (.+)""")
    val TAP_DOESNT_UNTAP_AS_LONG_AS = Regex("""[Tt]ap up to one target (.+?)\. It doesn't untap during its controller's untap step for as long as you control (.+)\.""")
    val X_COUNTERS_DRAW_HALF_X = Regex("""[Pp]ut X \+1/\+1 counters on (\w+)\. Then draw half X cards, rounded (down|up)\.""")
    val PUT_COUNTER_ON_SELF_AND_DRAW = Regex("""[Pp]ut a \+1/\+1 counter on (.+) and draw a card\.""")
    val QUEST_COUNTER_THEN_IF = Regex("""[Pp]ut a quest counter on (.+?)\. Then if it has (\w+) or more quest counters on it, (.+)""")
    val OWNER_SHUFFLES_ENCHANTED = Regex("""Enchanted creature's owner shuffles it into their library\.""")
    val MAY_CAST_FROM_HAND_FREE = Regex("""You may cast an? (.+?) spell from your hand without paying its mana cost\.""")

    // tla chunk 03: black, red and green wordings
    val SACRIFICE_SELF_IF_YOU_DO = Regex("""Sacrifice (.+?)\. If you do, (.+)""")
    val YOU_CAST_FROM_EXILE = Regex("""you cast a spell from exile""")
    val CAST_FROM_EXILE_AND_ENTERS_FROM_EXILE = Regex("""you cast a spell from exile and whenever (.+) enters from exile""")
    val CANT_CAST_OTHER_THAN_HANDS = Regex("""Until your next turn, your opponents can't cast spells from anywhere other than their hands\.""")
    val BECOMES_PUT_COUNTERS_ON_IT = Regex("""(.+?)\. Put (\w+) \+1/\+1 counters on it\.""")
    val ONE_OR_MORE_ATTACK = Regex("""one or more (.+) attack""")
    val ATTACK_A_PLAYER_WITH = Regex("""you attack a player with one or more (.+)""")
    val OPPONENT_SEARCHES = Regex("""an opponent searches their library""")
    val MAY_CAST_FROM_AMONG_EXILED_WITH = Regex("""you may cast an? (.+?) spell from among cards you own exiled with (.+)\.""")
    val DIES_IF_HAD_COUNTERS = Regex("""Whenever (.+) dies, if it had counters on it, put its counters on (.+)\.""")
    val LOSES_ALL_ABILITIES_DESTROY_THOSE = Regex("""Each (.+?) loses all abilities until end of turn\. Destroy those (\w+)\.""")
    val CHOOSE_WHEN_DIES_THIS_TURN = Regex("""Choose target (.+?)\. When that (\w+) dies this turn, (.+)""")
    val LOSE_KEYWORDS_UNTIL_EOT = Regex("""(.+?) lose ([a-z]+(?: and [a-z]+)?) until end of turn\.""")
    val CREATE_NAMED_TAPPED_ATTACKING_SACRIFICE = Regex("""[Cc]reate (an? .+? token)(?: with (.+?))? named (.+?) that's tapped and attacking\. Sacrifice that token at the beginning of the next end step\.""")
    val EXILE_X_COPY_EACH_SACRIFICE = Regex("""Exile X target (.+?) from graveyards\. For each (.+?) exiled this way, create a token that's a copy of it\. At the beginning of your next end step, sacrifice those tokens\.""")
    val PAY_CHOOSE_EXILED_WITH = Regex("""Choose an? (.+?) exiled with (.+)\.""")
    val HAS_ABILITIES_OF_LAST_CHOSEN = Regex("""(.+) has all activated and triggered abilities of the last chosen card\.""")
    val TWO_GROUPS_HAVE = Regex("""(.+? you control) and (.+? you control) have (.+)\.""")
    val ENTERS_AND_AT_UPKEEP = Regex("""When (.+) enters and at the beginning of your upkeep, (.+)""")
    val IF_X_IS_N_OR_MORE_THAT_CREATURE_GAINS = Regex("""If X is (\w+) or more, that creature gains (.+) until end of turn\.""")
    val DEALS_DAMAGE_THAT_PLAYER_DISCARDS = Regex("""(.+) deals (\d+) damage to target player\. That player discards (\w+) cards?\.""")
    val AS_LONG_AS_NAMED_IS_ARTIFACT_CREATURE = Regex("""As long as you control (\w+) or more permanents named (.+?), (.+) is an artifact creature\.""")
    val AS_LONG_AS_COUNTERS_ITS_ARTIFACT_CREATURE = Regex("""As long as (.+) has (\w+) or more (\S+) counters on it, it's an artifact creature\.""")
    val YOU_SACRIFICE_ANOTHER = Regex("""you sacrifice another (\w+)""")
    val YOU_SACRIFICE_DURING_YOUR_TURN = Regex("""you sacrifice an? (\w+) during your turn""")
    val EXILE_FROM_GRAVEYARD_IF_CREATURE_CLUE = Regex("""[Ee]xile up to one target (card from a graveyard)\. If a (\w+) card is exiled this way, (.+)""")
    val CHOOSE_NAME_SEARCH_EXILE = Regex("""Choose a card name\. Search target opponent's graveyard, hand, and library for up to (\w+) cards with that name and exile them\. Then that player shuffles\.""")
    val MAY_PAY_X_WHEN_YOU_DO_TOTAL = Regex("""you may pay \{X\}\. When you do, put any number of target (.+?) with total mana value X or less from that player's graveyard onto the battlefield under your control\.""")
    val PAID_WHENEVER_DIES_THIS_TURN = Regex("""(.+?)\. If this spell's additional cost was paid, whenever (.+?) this turn, (.+)""")
    val EXILE_IF_DEALT_DAMAGE = Regex("""Exile target (.+?)\. If it was dealt damage this turn, (.+)""")
    val COSTS_LESS_IF_TARGETS_WITH = Regex("""This spell costs ((?:\{[^}]+\})+) less to cast if it targets an? (\w+) with (\w+)\.""")
    val KICKED_INSTEAD_ONTO_BATTLEFIELD = Regex("""(.+?)\. If this spell was kicked, instead put that card onto the battlefield( tapped)?\.""")
    val DESTROY_UNLESS_DAMAGE = Regex("""destroy target (.+?) unless its controller has (.+?) deal damage to them equal to (?:his|her|its|their) power\.""")
    val FOR_EACH_CREATE = Regex("""for each (.+?), create (.+)\.""")
    val DAMAGE_PLUS_COUNTERS = Regex("""If a source you control would deal damage to an opponent or a permanent an opponent controls, it deals that much damage plus an amount of damage equal to the number of (\S+) counters on (.+) instead\.""")
    val ATTACKING_CAUSES_TRIGGER = Regex("""Whenever a (.+?) attacking causes a triggered ability of that creature to trigger, (.+)""")
    val MAY_COPY_THAT_ABILITY = Regex("""you may copy that ability\. You may choose new targets for the copy\.""")
    val KICKED_DEALS_INSTEAD = Regex("""If this spell was kicked, it deals (\d+) damage to that (\w+) instead\.""")
    val WHEN_NEXT_CAST_COPY = Regex("""When you next cast an? (.+?) this turn, copy it and you may choose new targets for the copy\.""")
    val CANT_BLOCK_KICKED_GAIN_CONTROL = Regex("""Target (.+?) can't block this turn\. If this spell was kicked, gain control of that creature until end of turn, untap it, and it gains (\w+) until end of turn\.""")
    val FIGHTS_EXCESS_ADD = Regex("""Target (.+?) fights target (.+?)\. If the creature the opponent controls is dealt excess damage this way, add that much (\{[WUBRGC]\})\.""")
    val COPY_SELF_NOT_LEGENDARY = Regex("""create a token that's a copy of (.+), except it's not legendary\.""")
    val YOU_CAST_THEM_AND = Regex("""you cast (?:it|them) and (.+)""")
    val EXILE_UNTIL_NONLAND_CAST_IF = Regex("""Exile cards from the top of your library until you exile a (\w+) card\. You may cast that card without paying its mana cost if the spell's mana value is less than (.+?)\. If you don't cast that card this way, put it into your hand\.""")
    val EACH_GAINS_UNTIL_EOT = Regex("""Each (.+?) gains (.+) until end of turn\.""")
    val AS_LONG_AS_COUNTER_ARE_TYPE = Regex("""As long as (.+?) has an? (\S+) counter on (?:him|her|it|them), (.+) are ([A-Z]\w+)s\.""")
    val EACH_DEAL_DAMAGE_POWER = Regex("""Up to two target (.+?) each deal damage equal to their power to target (.+)\.""")
    val ENCHANTED_GETS_FOR_EACH_AND_IS = Regex("""Enchanted creature gets \+1/\+1 for each (.+?) and is an? ([A-Z]\w+) in addition to its other types\.""")
    val MILL_POWER_RETURN_SELF_AND_ONE = Regex("""mill cards equal to its power\. Return this card to its owner's hand and up to one (.+?) milled this way to the battlefield under your control\.""")
    val CHOOSE_UP_TO_X_WHERE = Regex("""When (.+) enters, choose up to X, where X is (.+?)(?: —)?""")
    val GETS_FOR_EACH_OF_ITS_TYPES = Regex("""Each (.+?) gets \+1/\+1 for each of its creature types, to a maximum of (\d+)\.""")
    val SEARCH_UP_TO_THAT_MANY = Regex("""search your library for up to that many (.+?) cards, put them onto the battlefield tapped, then shuffle\.""")
    val PUT_COUNTERS_ON_A_CREATURE = Regex("""you put one or more (\S+) counters on an? (\w+)""")
    val MAY_GAIN_THAT_MUCH_ONCE = Regex("""you may gain that much life\.""")
    val EARTHBEND_WHEN_YOU_DO = Regex("""Earthbend (\d+)\. When you do, (.+)""")
    val QUEST_WHEN_YOU_DO_IF = Regex("""[Pp]ut a quest counter on (.+?)\. When you do, if it has (\w+) or more quest counters on it, (.+)""")
    val GETS_FOR_EACH_COLOR_AMONG = Regex("""(.+) gets \+1/\+0 for each color among (.+)\.""")
    val SEARCH_DIFFERENT_NAMES_OPPONENT_CHOOSES = Regex("""Search your library for up to (\w+) (.+?) cards with different names and reveal them\. An opponent chooses (\w+) of those cards\. Put the chosen cards into your graveyard and the rest onto the battlefield tapped, then shuffle\.""")
    val EACH_A_AND_B_HAS_QUOTED = Regex("""Each (\w+) and (\w+) you control has "(.+)"""")
    val SEARCH_UP_TO_X_PUT_THOSE = Regex("""search your library for up to X (.+?) cards\. Put those cards onto the battlefield tapped, then shuffle\.""")
    val EARTHBEND_THAT_LAND_BECOMES = Regex("""Earthbend (\d+|X)\. That land becomes an? ([A-Z]\w+) in addition to its other types\.""")
    val MILL_MAY_PUT_IF_YOU_DONT = Regex("""mill (\w+) cards\. You may put an? (.+?) from among them into your hand\. If you don't, (.+)""")
    val MILL_PUT_LAND_GAIN_IF = Regex("""Mill a card\. You may put an? (.+?) milled this way into your hand\. You gain (\d+) life if an? (.+?) is milled this way\.""")
    val BASE_POWER_AND_GAINS = Regex("""Until end of turn, (.+?) has base power (\d+) and gains (.+)\.""")
    val MAY_SEARCH_IF_SEARCHED_BOTTOM = Regex("""you may search your library for (an? .+?) and put it onto the battlefield tapped\. If you search your library this way, put (.+?) on the bottom of its owner's library, then shuffle\.""")
    val LOOK_PUT_ONTO_BATTLEFIELD_REST_RANDOM = Regex("""look at the top (\w+) cards of your library\. You may put an? (.+?) from among them onto the battlefield\. Put the rest on the bottom of your library in a random order\.""")
    val TRANSFORM_AT_NEXT_UPKEEP = Regex("""transform (.+) at the beginning of the next upkeep\.""")
    val PLURAL_NAME_VERB = Regex("""\b(attack|enter)\b""")
    val DRAW_THEN_IF_ALL_FOUR = Regex("""draw a card\. Then if you've done all four this turn, transform (.+)\.""")
    val SPELLS_YOU_CAST_COST_LESS = Regex("""Spells you cast cost ((?:\{[^}]+\})+) less to cast\.""")
    val MAY_TRANSFORM_IF_YOU_DO = Regex("""you may transform (.+?)\. If you do, (.+)""")
    val OPPONENT_EXILES_THEN_GRAVEYARD = Regex("""target opponent exiles an? (.+?) they control, then they exile an? (.+?) from their graveyard\.""")
    val CAST_EXILED_WITH_FLASH_ANY_MANA = Regex("""During your turn, you may cast cards exiled with (.+?) and you may cast them as though they had flash\. Mana of any type can be spent to cast those spells\.""")
    val UNTAP_LANDS_EXTRA_COMBAT_ONLY = Regex("""untap all (.+?)\. After this phase, there is an additional combat phase\. Only (.+?) can attack during that combat phase\.""")
    val HAMA = Regex("""target opponent mills (\w+) cards\. Exile up to one (.+?) from that player's graveyard\. For as long as you control (.+?), you may cast the exiled card during your turn by waterbending \{X\} rather than paying its mana cost, where X is its mana value\.""")
    val EACH_HAS_FLASHBACK_ITS_MANA_COST = Regex("""During your turn, each (.+?) has flashback\. The flashback cost is equal to that card's mana cost\.""")
    val MAY_GIVE_CONTROL_WHEN_YOU_DO = Regex("""you may have target opponent gain control of target (.+?)\. When you do, (.+)""")
    val CREATE_TOKEN_COUNTER_FOR_EACH = Regex("""[Cc]reate (an? .+? token)\. Put a \+1/\+1 counter on that token for each (.+)\.""")
    val CREATURES_BASE_PT_X = Regex("""(.+) have base power and toughness X/X until end of turn\. X can't be 0\.""")
    val LOSE_UNSPENT_BECOMES = Regex("""If you would lose unspent mana, that mana becomes (\w+) instead\.""")
    val HAS_AS_LONG_AS_UNSPENT = Regex("""(.+) has (.+?) as long as you have (\w+) or more unspent mana\.""")
    val MAY_EXILE_IT_WHEN_YOU_DO = Regex("""you may exile it\. When you do, (.+)""")
    val DISCARD_UP_TO_DRAW_THAT_MANY = Regex("""discard up to (\w+) cards, then draw that many cards\.""")
    val AND_AT_LEAST_ONE_OTHER_ATTACK = Regex("""(.+) and at least one other creature attack""")
    val IF_THAT_SPELL_IS_ADDITIONAL_COUNTER = Regex("""If that spell is an? (\w+), put an additional \+1/\+1 counter on that land\.""")
    val CHOOSE_UNCHOSEN_AND_LOSE = Regex("""At the beginning of your first main phase, choose one that hasn't been chosen and you lose (\d+) life —""")
    val EXILE_RETURN_UNDER_OPPONENT = Regex("""Exile (.+?), then return (?:him|her|it|them) to the battlefield under an opponent's control\.""")
    val SCRY_OR_SURVEIL_LOOK_CAST = Regex("""Whenever you scry or surveil, look at the top card of your library\. You may cast that card without paying its mana cost\. Do this only once each turn\.""")
    val RETURN_GRANTER_TO_HAND = Regex("""Return (.+) to its owner's hand\.""")
    val ADD_X_ANY_ONE_COLOR_TYPE_IN_COMMON = Regex("""\{T\}: Add X mana of any one color, where X is the greatest number of (.+?) that have a creature type in common\.""")
    val TARGET_PLAYER_SCRIES = Regex("""Target player (scries|surveils) (\w+)\.""")
    val WHENEVER_YOU_BEND = Regex("""Whenever (you waterbend, earthbend, firebend, or airbend), (.+)""")
    val PUT_KEYWORD_COUNTER = Regex("""[Pp]ut an? ([a-z]+ [a-z]+) counter on (.+)\.""")
    val TARGET_FIGHTS_TARGET = Regex("""((?:up to one )?target .+?) fights (target .+)\.""")
    val RETURN_MANA_VALUE_AT_MOST_POWER = Regex("""return target (.+?) with mana value less than or equal to this creature's power from your graveyard to the battlefield\.""")
    val TWO_TARGETS_SHARE_TYPE = Regex("""Two target (.+?) that share a creature type can't be blocked this turn\.""")
}
