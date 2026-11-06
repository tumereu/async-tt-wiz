package arcanum.parser

import arcanum.api.Comparison
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.ObjectFilter
import arcanum.api.Value
import arcanum.api.Zone

/** Regular expressions of The Lord of the Rings: Tales of Middle-earth (ltr) wordings read in [Sentences]. */
internal object Ltr {
    // ltr chunk 01
    val PUT_A_CARD_FROM_HAND_ON_BOTTOM = Regex("""[Pp]ut a card from your hand on the bottom of your library\.""")
    val IS_A_SUBTYPE_AND = Regex("""(Enchanted creature) is an? ([A-Z]\w+) and (.+)\.""")
    val IF_THAT_CREATURE_IS_INSTEAD = Regex("""If (that creature|it) is (equipped|legendary|an? [A-Z]\w+(?: or [A-Z]\w+)?), (.+) instead\.""")
    val EQUIP_ABILITIES_COST_LESS = Regex("""Equip abilities you activate cost ((?:\{[^}]+\})+) less to activate\.""")
    val UNTIL_EOT_PREFIX = Regex("""Until end of turn, (.+)\.""")
    val ASSIGNS_DAMAGE_BY_TOUGHNESS = Regex("""(.+) assigns combat damage equal to its toughness rather than its power""")
    val WHENEVER_IF_NO_MANA_SPENT = Regex("""(Whenever .+?), if no mana was spent to cast it, (.+)""")
    val IF_SELF_IS_A_IT_BECOMES = Regex("""If (.+?) is an? ([A-Z]\w+), it becomes an? ([A-Z]\w+(?: [A-Z]\w+)*) with (.+?)\.?""")
    val BASE_PT_AND = Regex("""base power and toughness (\d+)/(\d+)(?: and (.+))?""")
    val LOSES_THE_GAME_IF_RING_OTHERWISE = Regex("""(That player) loses the game if the Ring has tempted you (\w+) or more times this game\. Otherwise, (.+)""")
    val ENTERING_OR_LEAVING_TRIGGERS_ADDITIONAL = Regex("""If (.+) entering or leaving the battlefield causes a triggered ability of (.+) to trigger, that ability triggers an additional time\.""")
    val REMOVE_COUNTER_IF_YOU_DO = Regex("""[Rr]emove an? (\S+) counter from (.+?)\. If you do, (.+)""")
    val THEN_IF_NO_COUNTERS = Regex("""Then if (.+?) has no (\S+) counters on it, (.+)""")
    val N_OR_MORE_ATTACK_A_PLAYER = Regex("""(two|three) or more (.+) attack a player""")
    val DURING_YOUR_TURN_ACTIVATE_AT_INSTANT_SPEED = Regex("""During your turn, you may activate (\w+) abilities any time you could cast an instant\.""")
    // ltr chunk 02
    val ATTACH_TARGET_TO_SELF = Regex("""[Aa]ttach (target .+?) to (.+)\.""")
    val FOR_AS_LONG_AS_YOU_CONTROL = Regex("""(.+) for as long as you control (.+)\.""")
    val CHOOSE_TARGET = Regex("""[Cc]hoose (up to one target .+)\.""")
    val EXILE_THEN_RETURN_UNDER_YOUR_CONTROL = Regex("""[Ee]xile (up to one target .+?|target .+?), then return it to the battlefield under your control\.""")
    val TRAILING_IF_POWER = Regex("""([A-Z].+?) if (that creature|it)'s power is (\d+) or (less|greater)\.""")
    val OTHER_THAN_SELF = Regex("""(.*?)\btarget (.+?) other than (.+?)(\.|,.*)""")
    val THEY_GAIN_UNTIL_EOT = Regex("""They gain (.+?) until end of turn\.""")
    val YOU_CREATE_A_TOKEN = Regex("""you create a token""")
    // ltr chunk 03
    val MOVE_COUNTER_OF_EACH_KIND_NOT_ON = Regex("""Move a counter of each kind not on (.+?) from (.+?) onto (.+)\.""")
    val MOVE_ONE_OR_MORE_COUNTERS_IF_YOU_DO = Regex("""Move one or more counters from (.+?) onto (.+?)\. If you do, (.+)""")
    val UNTAP_REPLACED_BY_REMOVING_COUNTER = Regex("""If (.+?) would untap during your untap step, remove an? (\S+) counter from it instead\. If you do, untap it\.""")
    val IF_MAY_EXILE_THIS_FROM_GRAVEYARD = Regex("""(Whenever .+?), if (.+?), you may exile this card from your graveyard\. If you do, (.+)""")
    val SHUFFLE_TARGET_CARDS_INTO_LIBRARY = Regex("""Shuffle (any number of target .+?) from your graveyard into your library\.(?: (.+))?""")
    val CAST_SPELLS_THIS_TURN_AS_THOUGH_FLASH = Regex("""You may cast spells this turn as though they had flash\.""")
    val UNTAP_N_OTHER_TARGET = Regex("""Untap (two|three) other target (.+)\.""")
    val IF_IT_WAS_A_SPELL = Regex("""If it was an? (.+?) spell, (.+)""")
    val TARGET_OPPONENT_GAINS_CONTROL_IF_THEY_DO = Regex("""Target opponent gains control of (target .+?)\. If they do, (.+)""")
    val REMOVE_FROM_COMBAT = Regex("""[Rr]emove (.+?) from combat\.""")
    // ltr chunk 04
    val DRAW_DURING_OPPONENTS_TURN = Regex("""you draw a card during an opponent's turn""")
    val UNTAP_AND_PUT = Regex("""(Untap .+?) and (put .+)\.""")
    val RETURN_SPELL_OR_NONLAND_PERMANENT = Regex("""Return target spell or nonland permanent an opponent controls to its owner's hand\.""")
    val MAY_CAST_EQUAL_OR_LESSER_FREE = Regex("""You may cast an? (.+?) spell with equal or lesser mana value from your hand without paying its mana cost\.""")
    val MAY_TAP_ANY_NUMBER_DRAW_FOR_EACH = Regex("""You may tap any number of untapped (.+?)\. Draw a card for each (\w+) tapped this way\.""")
    val IF_IT_HAD_N_OR_MORE_COUNTERS = Regex("""If it had (\w+) or more (\S+) counters on it, (.+)""")
    val COPY_IT_NOT_LEGENDARY_NEW_TARGETS = Regex("""Copy it, except the copy isn't legendary\. You may choose new targets for the copy\.""")
    val TAP_UP_TO_X_TARGET = Regex("""Tap up to X target (.+)\.""")
    val GAIN_CONTROL_OF_UP_TO_ONE_UNTIL_EOT = Regex("""Gain control of (up to one target .+?) until end of turn\.""")
    val DRAW_FOR_EACH_TARGET_OPPONENT_CONTROLS = Regex("""Draw a card for each (.+?) target opponent controls\.""")
    val TARGETS_EACH_GAIN = Regex("""((?:Up to )?(?:two|three|Two|Three) target .+?) (?:each )?(gain|get) (.+)""")
}

/** Nouns, values and conditions of The Lord of the Rings (ltr). */
internal object LtrPhrases {
    private val PUT_THERE_FROM_BATTLEFIELD_THIS_TURN = Regex("""(.+?) that (?:was|were) put there from the battlefield this turn""")
    private val BLOCKED_OR_WAS_BLOCKED_BY = Regex("""(.+?) that blocked or was blocked by an? (.+?) this turn""")
    private val WITH_MANA_VALUE_N_OR_M = Regex("""(.+?) with mana value (\d+) or (\d+)""")
    private val IN_YOUR_GRAVEYARD = Regex("""(.+? cards?) in your graveyard""")
    private val WITH_LESSER_POWER = Regex("""(.+?) with lesser power""")

    fun noun(phrase: String): ObjectFilter? {
        // ltr chunk 02: "permanent card in your graveyard that was put there from the battlefield this turn" (the new object, CR 400.7)
        PUT_THERE_FROM_BATTLEFIELD_THIS_TURN.matchEntire(phrase)?.let { m ->
            val base = IN_YOUR_GRAVEYARD.matchEntire(m.groupValues[1])?.let { g -> Filters.noun(g.groupValues[1])?.let {
                Filters.all(it, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You)) } } ?: Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(base, ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, from = Zone.Battlefield, to = Zone.Graveyard), role = EventRole.After))
        }
        // ltr chunk 02: "creature that blocked or was blocked by a legendary creature this turn" (CR 509.1)
        BLOCKED_OR_WAS_BLOCKED_BY.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: return null
            val other = Filters.noun(m.groupValues[2]) ?: return null
            return Filters.all(base, ObjectFilter.Or(listOf(
                ObjectFilter.ThisTurn(EventPattern.Block(ObjectFilter.Any, other), role = EventRole.Object),
                ObjectFilter.ThisTurn(EventPattern.Block(other, ObjectFilter.Any), role = EventRole.OtherCombatant))))
        }
        // ltr chunk 02: "Equipment you control with mana value 2 or 3"
        WITH_MANA_VALUE_N_OR_M.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(base, ObjectFilter.Or(listOf(ObjectFilter.ManaValue(Comparison.Equal, Value.Fixed(m.groupValues[2].toInt())),
                ObjectFilter.ManaValue(Comparison.Equal, Value.Fixed(m.groupValues[3].toInt())))))
        }
        // ltr chunk 04: "creature with lesser power" (than this creature)
        WITH_LESSER_POWER.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(base, ObjectFilter.Power(Comparison.Less, Value.Of(arcanum.api.NumericProperty.Power, arcanum.api.Objects.Self, arcanum.api.Combine.Single)))
        }
        return null
    }

    /** ltr: a value "where X is …" defines. */
    fun value(text: String): Value? = when (text) {
        // ltr chunk 04: the Army chosen as the amass instruction was followed (CR 701.47c), named by [Sentences]
        "the amassed Army's power" -> Value.Of(arcanum.api.NumericProperty.Power, arcanum.api.Objects.Bound(AMASSED_ARMY), arcanum.api.Combine.Single)
        // ltr chunk 04: the scry event's number of cards (CR 701.22a)
        "the number of cards looked at while scrying this way" -> Value.EventAmount
        else -> null
    }

    /** The name the amassed Army is bound to (CR 701.47c). */
    const val AMASSED_ARMY: String = "army"
}
