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
    // ltr chunk 05
    val YOUR_OPPONENTS_CANT_GAIN_LIFE = Regex("""Your opponents can't gain life\.""")
    val IF_THE_SACRIFICED_WAS = Regex("""If the sacrificed (creature|permanent) was (.+?), (.+)""")
    val MAY_SACRIFICE_IT_WHEN_YOU_DO = Regex("""(Whenever .+?), you may sacrifice it\. When you do, (.+)""")
    val YOU_CREATE_OR_SACRIFICE_A_TOKEN = Regex("""you create or sacrifice a token""")
    val CREATE_TAPPED_AND_ATTACKING_SACRIFICE_AT_END_OF_COMBAT = Regex("""Create (.+? tokens?(?: with [^.]+?)?)(?: named (.+?))? that's tapped and attacking\. Sacrifice that token at end of combat\.""")
    val EXILE_TARGET_THEN_CREATE_X = Regex("""Exile (target .+?), then create X (.+?), where X is the exiled card's (power|toughness)\.""")
    val AS_LONG_AS_YOUR_TURN_AND_IS_ARTIFACT_CREATURE = Regex("""As long as it's your turn and (.+?), (.+?) is an artifact creature\.""")
    val HAND_OVER_N_EXILE_DIFFERENCE = Regex("""If its controller has more than (\w+) cards in hand, they exile cards from their hand equal to the difference\.""")
    val COSTS_LESS_IF_TARGETS_DEALT_DAMAGE = Regex("""This spell costs ((?:\{[^}]+\})+) less to cast if it targets an? (\w+) that was dealt damage this turn\.""")
    // ltr chunk 06
    val IF_THAT_CREATURE_IS_ITS_CONTROLLER = Regex("""If that creature is (legendary|an? .+?), (its controller .+)""")
    val IF_THE_SACRIFICED_WAS_INSTEAD = Regex("""If the sacrificed (creature|permanent) was (.+?), (.+) instead\.""")
    val RING_TEMPTS_THEN = Regex("""The Ring tempts you, then (.+)""")
    val EACH_PLAYER_MILLS_EQUAL_TO = Regex("""[Ee]ach player mills cards equal to (.+)\.""")
    val EACH_OPPONENT_LOSES_FOR_EACH_IN_THAT_PLAYERS = Regex("""Each opponent loses (\w+) life for each (.+?) in that player's graveyard\.""")
    val DEFENDING_SACRIFICES_LEAST_POWER = Regex("""[Dd]efending player sacrifices an? (\w+) with the least power among (\w+) they control\.""")
    val EXILE_ALL_CAST_FROM_AMONG_ANY_MANA = Regex("""Exile all (.+?) from target player's graveyard\. You may cast spells from among those cards for as long as they remain exiled, and mana of any type can be spent to cast them\.""")
    val UP_TO_N_IN_DECK = Regex("""A deck can have up to (\w+) cards named (.+)\.""")
    val EACH_OPPONENT_SACRIFICES_FOOD_FOR_EACH = Regex("""Each opponent sacrifices an? (\w+) of their choice\. Create an? (.+?) token for each (\w+) sacrificed this way\.""")
    val YOU_AND_TARGET_OPPONENT_EACH_CREATE = Regex("""You and target opponent each create (an? .+? token)\.""")
    val CREATURES_DEAL_COMBAT_DAMAGE_TO_YOU_SACRIFICE = Regex("""Whenever one or more creatures deal combat damage to you, each opponent sacrifices a creature of their choice that dealt combat damage to you this turn\. (.+)""")
    val GAINS_UNTIL_EOT_TAP_HIM = Regex("""(.+?) gains (.+?) until end of turn\. Tap (?:him|her)\.""")
    val GUESSING_GAME = Regex("""Look at the top two cards of your library, put them back in any order, then choose land or nonland\. An opponent guesses whether the top card of your library is the chosen kind\. Reveal that card\. If they guessed right, remove (.+?) from combat\. Otherwise, you draw a card and (.+?) can't be blocked this turn\.""")
    val WHEN_ENTERS_AND_WHENEVER = Regex("""When (.+?) enters and whenever (.+?), (.+)""")
    val COPY_TOKEN_EXCEPT_EXILE_UNLESS_BEARER = Regex("""Exile (target .+?)\. Create a tapped and attacking token that's a copy of that card, except it's an? (\d+)/(\d+) (\w+) ([A-Z]\w+) with (\w+)\. At the beginning of the next end step, exile that token unless (.+?) is your Ring-bearer\.""")
    // ltr chunks 07-09
    val TARGET_DEALS_DAMAGE_TO_ANOTHER_THAT_PLAYER = Regex("""Target creature an opponent controls deals damage equal to its power to another target creature that player controls\.""")
    val DAMAGE_CANT_BE_PREVENTED_THIS_TURN = Regex("""Damage can't be prevented this turn\.""")
    val X_DAMAGE_AND_ONE_TO_EACH_OTHER_SAME_CONTROLLER = Regex("""(.+?) deals (\w+) damage to target creature and (\w+) damage to each other creature with the same controller\.""")
    val IT_DEALS_DAMAGE_TO_EACH_BLOCKING_IT = Regex("""[Ii]t deals (\w+) damage to each creature blocking it\.""")
    val CONTROL_CREATURE_WITH_GREATEST_POWER = Regex("""you control a creature with the greatest power among creatures on the battlefield""")
    val COPY_ANY_NUMBER_OF_TARGET_SPELLS = Regex("""Copy any number of target (.+?) spells\. You may choose new targets for the copies\.""")
    val AMASS_WHEN_YOU_DO = Regex("""Amass (\w+) (\w+)\. When you do, (.+)""")
    val UNTIL_EOT_GAIN_CONTROL_OF_TARGET = Regex("""[Uu]ntil end of turn, gain control of (target .+?)\. (.+)""")
    val DEALS_DAMAGE_TO_EACH_OF_UP_TO = Regex("""(.+?) deals (\w+) damage to each of up to (\w+) target (.+)\.""")
    val IF_ITS_A_IT_ALSO_GAINS = Regex("""If it's (legendary|an? .+?), it also gains (.+) until end of turn\.""")
    val IS_DEALT_EXCESS_NONCOMBAT_DAMAGE = Regex("""an? (.+?) is dealt excess noncombat damage""")
    val THAT_EXCESS_DAMAGE = "that excess damage"
    val AS_LONG_AS_EQUIPPED_IS_IT_HAS = Regex("""As long as equipped creature is (legendary|an? .+?), it has (.+)\.""")
    val TARGET_CANT_BLOCK_UNTIL_EOT = Regex("""(Up to one target .+?|Target .+?) can't block until end of turn\.""")
    val RETURN_IT_TO_HAND_IF_YOU_DO_EQUAL_TO_TOUGHNESS = Regex("""Return it to its owner's hand\. If you do, create a number of (.+? tokens) equal to its toughness\.""")
    val ATTACKED_THIS_TURN = Regex("""(.+?) attacked this turn""")
    val DAMAGE_THEN_DESTROY_EQUIPMENT_ATTACHED = Regex("""(?:It|(.+?)) deals (\w+) damage to (target .+?)\. Destroy all Equipment attached to that creature\.""")
    val EXILE_TOP_PLAY_WHEN_YOU_PLAY = Regex("""Exile the top card of your library\. You may play that card this turn\. When you play a card this way, (.+)""")
    val CANT_BLOCK_UNLESS_YOU_CONTROL = Regex("""(.+?) can't block unless you control (.+)\.""")
    val LOSES_UNTIL_EOT_IF_WOULD_DIE_EXILE = Regex("""That creature loses (\w+) until end of turn\. If that creature would die this turn, exile it instead\.""")
    val FELL_THE_FOREST = Regex("""Sacrifice any number of lands\. Reveal the top X cards of your library, where X is the number of lands sacrificed this way\. Choose any number of (.+?) cards revealed this way\. Put all nonland cards chosen this way onto the battlefield, then put all land cards chosen this way onto the battlefield tapped, then put the rest on the bottom of your library in a random order\.""")
    val SACRIFICE_A_LAND_SEARCH_INSTEAD = Regex("""Sacrifice a land\. Search your library for up to (\w+) (.+?) cards, put them onto the battlefield tapped, then shuffle\. If (.+?), instead search your library for up to (\w+) \2 cards, put them onto the battlefield tapped, then shuffle\.""")
    val SPEND_ONLY_AND_CANT_BE_COUNTERED = Regex("""(.+?) Spend this mana only to cast an? (.+?) spell, and that spell can't be countered\.""")
    val CHOOSE_ONE_AND_GETS = Regex("""(Whenever .+?), choose one and (.+?) gets ([+-]\d+)/([+-]\d+) until end of turn\.""")
    val SCRY_THEN_MAY_REVEAL_IF_REVEALED_DRAW = Regex("""Scry (\w+), then you may reveal the top card of your library\. If an? (.+?) card is revealed this way, (.+)""")
    val COUNTER_IF_POWER_THEN_PUMP_FIGHT = Regex("""Put a \+1/\+1 counter on (target .+?) if its power is (\d+) or greater\. Then that creature gets ([+-]\d+)/([+-]\d+) until end of turn and fights (target .+)\.""")
    val UNTIL_EOT_GAINS_QUOTED_THEN_FIGHTS = Regex("""Until end of turn, (target .+?) gains "(.+?)" Then that creature fights (up to one other target .+)\.""")
    val ADD_TWICE_THAT_MUCH = Regex("""[Aa]dd twice that much (\{[WUBRGC]\})\.""")
    val YOU_CAST_A_SPELL_THAT_TARGETS = Regex("""you cast a spell that targets (.+)""")
    val GETS_PER_CARD_LOOKED_AT_SCRYING = Regex("""(.+?) gets \+1/\+1 until end of turn for each card looked at while scrying this way\.""")
    val THAT_CREATURE_LOSES_UNTIL_EOT = Regex("""That creature loses (\w+) until end of turn\.""")
    val MUST_BE_BLOCKED_THIS_TURN = Regex("""(.+?) must be blocked this turn if able\.""")
    val CANT_BE_BLOCKED_BY_MORE_THAN_ONE_THIS_TURN = Regex("""(.+?) can't be blocked by more than one creature each combat this turn\.""")
    val DEALS_DAMAGE_EQUAL_TO_ITS_POWER_TO = Regex("""(.+?) deals damage equal to its power to (up to one target .+|target .+)\.""")
    val CREATE_NAMED_TOKEN_WITH_QUOTED = Regex("""Create ([A-Z][\w']+), an? (legendary )?(\d+)/(\d+) (\w+) ([A-Z]\w+) creature token with (.+?), and "(.+)"\.?""")
    val CREATE_N_PREDEFINED_TOKENS = Regex("""[Cc]reate (\w+) (Treasure|Food|Clue|Blood) tokens\.""")
    // ltr chunk 10
    val LOOK_TOP_X_MANA_VALUE_REVEAL_NOT_SHARING = Regex("""Look at the top X cards of your library(?:, where X is that creature's mana value)?\. You may reveal an? (.+?) card that doesn't share a creature type with a creature you control from among those cards and put it into your hand\. Put the rest on the bottom of your library in a random order\.""")
    val TOKENS_PLUS_ADDITIONAL = Regex("""If one or more tokens would be created under your control, those tokens plus an additional (.+?) token are created instead\.""")
    val NOTE_A_CREATURE_TYPE_NEXT_CAST = Regex("""Note a creature type that hasn't been noted for this Saga\. When you next cast a creature spell of that type this turn, that creature enters with an additional (\S+) counter on it\.""")
    val PERMANENT_YOU_CONTROLLED_LEFT = Regex("""a permanent you controlled left the battlefield this turn""")
    val RING_TEMPTS_REVEAL_UNTIL_X_LANDS = Regex("""The Ring tempts you\. Then reveal cards from the top of your library until you reveal X (\w+) cards(?:, where X is (.+?))?\. Put those \1 cards onto the battlefield tapped and the rest on the bottom of your library in a random order\.""")
    // ltr chunk 11
    val MAY_REVEAL_TOP_IF_REVEALED = Regex("""You may reveal the top card of your library\. If an? (.+?) card is revealed this way, (.+)""")
    val GAIN_PROTECTION_FROM_EACH_OF_COLORS = Regex("""(.+?) gains? protection from each of that creature's colors until end of turn\.""")
    val SCRY_REVEAL_ATTACKING_UNTIL_NEXT_TURN = Regex("""Scry (\w+), then you may reveal the top card of your library\. If an? (.+?) card is revealed this way, put it onto the battlefield tapped and attacking\. Until your next turn, it gains (\w+) if you control an? (\w+) and (\w+) if you control an? (\w+)\.""")
    val EACH_DEAL_DAMAGE_EQUAL_TO_THEIR_POWER = Regex("""(Target .+?) and (up to one other target .+?) each deal damage equal to their power to (target .+)\.""")
    val CHOOSE_COUNTER_FROM_AMONG = Regex("""[Pp]ut your choice of a counter from among (.+) on (.+)\.""")
    val PUT_ONE_OF_EACH_OF_THOSE_KINDS = Regex("""[Pp]ut one of each of those kinds of counters on (.+)\.""")
    val YOU_PUT_COUNTERS_ON = Regex("""you put one or more counters on (.+)""")
    val PUT_COUNTER_AND_COUNTER_ON_THAT_AND_SELF = Regex("""Put an? (\S+) counter and an? (\S+) counter on that creature and an? \1 counter and an? \2 counter on (.+)\.""")
    val CHOOSE_OPPONENT_NEXT_END_STEP = Regex("""Choose an opponent\. At the beginning of that player's next end step, (.+?) if they didn't attack you that turn\. Otherwise, (.+)""")
    val COSTS_LESS_FOR_EACH_SACRIFICED_THIS_TURN = Regex("""This spell costs (\{\d+\}) less to cast for each (\w+) sacrificed this turn\.""")
    val COUNTER_ON_EACH_OF_UP_TO_X = Regex("""[Pp]ut an? (\S+) counter on each of up to X target (.+)\.""")
    // ltr chunk 12
    val EACH_PLAYER_SACRIFICES_IF_YOU_DID_RETURN = Regex("""Each player sacrifices an? (\w+) of their choice\. If you sacrificed an? \1 this way, you may return another (.+?) from your graveyard to the battlefield\.""")
    val TARGET_PLAYER_MILLS_THEN_AMASS_X = Regex("""Target player mills (\w+) cards\. Then amass (\w+) X(?:, where X is the number of (.+?) cards in that player's graveyard)?\.""")
    val DEALS_X_EXCESS_TO_CONTROLLER = Regex("""(.+?) deals X damage to (target .+?)(?:, where X is (.+?))?\. Excess damage is dealt to that creature's controller instead\.""")
    val GAINS_PROTECTION_FROM_CHOSEN_CARD_TYPE = Regex("""(.+?) gains protection from the card type of your choice until end of turn\.""")
    val BECOMES_TARGET_OF_A_SPELL_PHASES_OUT = Regex("""(.+?) becomes the target of a spell, it phases out\.""")
    val PHASES_IN = Regex("""(.+?) phases in""")
    val SHARES_A_COLOR_WITH = Regex("""(.+?) that shares a color with an? (.+)""")
    val ATTACK_WITH_SELF_AND_ANOTHER = Regex("""you attack with (.+?) and another (.+)""")
    val N_OR_MORE_CREATURES_DIED_UNDER_YOUR_CONTROL = Regex("""(\w+) or more creatures died under your control this turn""")
    val RESOLVES_FOR_THE_NTH_TIME = Regex("""When this ability resolves for the (\w+) time this turn, (.+)""")
    // ltr chunk 13
    val ATTACH_UP_TO_ONE_THEN_AMASS_ATTACHED = Regex("""Attach (up to one target Equipment) to it\. Then amass (\w+) X(?:, where X is the number of Equipment attached to (.+?))?\.""")
    val IF_THAT_CREATURE_WAS_PUT_COUNTERS_INSTEAD = Regex("""If that creature was an? (.+?), put (\w+) (\S+) counters on (.+?) instead\.""")
    val OPPONENT_SEPARATES_PILES = Regex("""Choose an opponent\. They look at the top (\w+) cards of your library and separate them into a face-down pile and a face-up pile\. Put one pile into your hand and the other into your graveyard\.(?: (.+))?""")
    val IF_THAT_CREATURE_HAS_POWER_IT_GAINS = Regex("""If that creature has power (\d+) or greater, it gains (.+) until end of turn\.""")
    val COPY_THAT_DIED_EXCEPT_FOOD = Regex("""Whenever (another creature) dealt damage this turn by an? (\w+) you controlled dies, create a token that's a copy of that creature, except it's a (\w+) artifact with "(.+?)," and it loses all other card types\.""")
    val TARGET_OPPONENT_REVEALS_UNTIL_UNDER_YOUR_CONTROL = Regex("""Target opponent reveals cards from the top of their library until they reveal an? (.+?) card\. Put that card onto the battlefield tapped under your control and the rest into their graveyard\.""")
    val LORE_COUNTERS_AMONG_SAGAS = Regex("""there are (\w+) or more lore counters among Sagas you control""")
    val FINAL_CHAPTER_OF_A_SAGA_RESOLVES = Regex("""the final chapter ability of an? (.+?) resolves""")
    val REVEAL_UNTIL_PUT_ONTO_BATTLEFIELD_REST_BOTTOM = Regex("""Reveal cards from the top of your library until you reveal an? (.+?) card\. Put that card onto the battlefield and the rest on the bottom of your library in a random order\.""")
    val PUT_SELF_ON_BOTTOM = Regex("""[Pp]ut (.+?) on the bottom of its owner's library\.""")
    val LANDS_ABILITIES_CANT_BE_ACTIVATED_UNLESS_MANA = Regex("""Activated abilities of (.+?) can't be activated unless they're mana abilities\.""")
    val HAS_ALL_ACTIVATED_ABILITIES_EXCEPT_MANA = Regex("""(.+?) has all activated abilities of (.+?) except mana abilities\.""")
    val MANA_OF_ANY_TYPE_TO_ACTIVATE_SELFS = Regex("""Mana of any type can be spent to activate (.+?)'s abilities\.""")
    val OPPONENTS_MILL_WHEN_MILLED_EXILE_COPY_CAST = Regex("""Each opponent mills (\w+) cards\. When one or more cards are milled this way, exile (target .+?) with equal or lesser mana value than that spell from an opponent's graveyard\. Copy the exiled card\. You may cast the copy without paying its mana cost\.""")
    val TARGETS_EACH_GAIN = Regex("""((?:Up to )?(?:two|three|Two|Three|Any number of) target .+?) (?:each )?(gain|get) (.+)""") // fdn chunk 05: "Any number of"
    // ltr chunk 14
    val YOU_GAIN_PROTECTION_FROM_EVERYTHING = Regex("""[Yy]ou gain protection from everything until your next turn\.""")
    val YOU_LOSE_LIFE_FOR_EACH = Regex("""[Yy]ou lose (\d+) life for each (.+)\.""")
    val CREATE_IF_THAT_CREATURE_INSTEAD_ATTACKING = Regex("""(Create .+?)\. If that creature is (\w+), instead create (\w+) of those tokens that are tapped and attacking\.""")
    val BLOCKS_OR_BECOMES_BLOCKED_THAT_CREATURE_LOSES = Regex("""Whenever (.+?) blocks or becomes blocked by an? (.+?), that creature loses all abilities until end of turn\.""")
    val MAY_CAST_FROM_HAND_WITH_MANA_VALUE_AT_MOST = Regex("""[Yy]ou may cast an? (.+?) spell from your hand with mana value less than or equal to (.+?) without paying its mana cost\.""")
    val OWNER_SHUFFLES_IT = Regex("""[Ii]ts owner shuffles it into their library\.""")
    val OPPONENT_MAY_HAVE_YOU_DRAW_ELSE_MILL = Regex("""(.+?\.) Then target opponent may have you draw a card\. If that player doesn't, you mill X cards, where X is the number of (\S+) counters on (.+?), and that player loses life equal to the total mana value of those cards\.""")
    val WOULD_WHILE_YOU_HAVE = Regex("""If you would (.+?) while you have (.+?), (.+)\.""")
    val UNTAP_EQUIPPED_CREATURE = Regex("""[Uu]ntap equipped creature\.""")
    val HAS_AS_LONG_AS_BLOCKING_OR_BLOCKED_BY = Regex("""Equipped creature has (.+?) as long as it's blocking or blocked by an? (.+)\.""")
    val NONBASIC_LANDWALK = Regex("""[Nn]onbasic landwalk""")
    val ADD_MANA_OF_ANY_COLOR_AMONG = Regex("""[Aa]dd one mana of any color among (.+)\.""")
    val SACRIFICE_SELF_AND_A = Regex("""Sacrifice (.+?) and (an? .+)""")
    val YOU_HAVE_N_OR_LESS_LIFE = Regex("""you have (\d+) or less life""")
    val CHOOSE_UP_TO_THEN_DESTROY_THE_REST = Regex("""[Cc]hoose up to (\w+) (.+?), then destroy the rest\.""")
}

/** Nouns, values and conditions of The Lord of the Rings (ltr). */
internal object LtrPhrases {
    private val PUT_THERE_FROM_BATTLEFIELD_THIS_TURN = Regex("""(.+?) that (?:was|were) put there from the battlefield this turn""")
    private val BLOCKED_OR_WAS_BLOCKED_BY = Regex("""(.+?) that blocked or was blocked by an? (.+?) this turn""")
    private val WITH_MANA_VALUE_N_OR_M = Regex("""(.+?) with mana value (\d+) or (\d+)""")
    private val IN_YOUR_GRAVEYARD = Regex("""(.+? cards?) in your graveyard""")
    private val WITH_LESSER_POWER = Regex("""(.+?) with (lesser|greater) power""")
    private val DEALT_DAMAGE_THIS_TURN = Regex("""(.+?) that was dealt damage this turn""")
    private val FROM_AN_OPPONENTS_GRAVEYARD = Regex("""(.+? cards?) from an opponent's graveyard""")
    private val AND_OR = Regex("""(\w+?)s? and/or (\w+?)s?""")
    private val POWER_AT_MOST_AMASSED = Regex("""(.+?) with power less than or equal to the amassed Army's power""")
    private val WITH_POWER_X = Regex("""(.+?) with power X""")

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
        // ltr chunk 12: "card that shares a color with a legendary creature you control" (CR 105.4)
        Ltr.SHARES_A_COLOR_WITH.matchEntire(phrase)?.let { m ->
            val other = Filters.noun(m.groupValues[2]) ?: return null
            val shares = ObjectFilter.Shares(arcanum.api.SharedQuality.Color, arcanum.api.Objects.Each(other))
            return if (m.groupValues[1] == "card") shares else Filters.all(Filters.noun(m.groupValues[1]) ?: return null, shares)
        }
        // ltr chunk 05: "creature that was dealt damage this turn" (CR 120.3)
        DEALT_DAMAGE_THIS_TURN.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(base, ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, arcanum.api.RecipientFilter()), role = EventRole.Object))
        }
        // ltr chunk 05: "creature card from an opponent's graveyard"
        FROM_AN_OPPONENTS_GRAVEYARD.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(base, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.Opponent))
        }
        // ltr chunk 05: "Goblins and/or Orcs" (either subtype)
        AND_OR.matchEntire(phrase)?.let { m ->
            // also card types: "instant and/or sorcery", "artifact and/or land"
            val parts = listOf(m.groupValues[1], m.groupValues[2]).map { t ->
                if (t in RulesWords.subtypes) ObjectFilter.Sub(arcanum.api.SubtypeRef.Literal(arcanum.api.Subtype(t))) else Filters.noun(t) ?: return@let }
            return ObjectFilter.Or(parts)
        }
        // ltr chunk 07: "… with power less than or equal to the amassed Army's power" (CR 701.47c)
        POWER_AT_MOST_AMASSED.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(base, ObjectFilter.Power(Comparison.AtMost, Value.Of(arcanum.api.NumericProperty.Power, arcanum.api.Objects.Bound(AMASSED_ARMY), arcanum.api.Combine.Single)))
        }
        // ltr chunk 14: "creature with power X" (X announced as the ability is activated, CR 107.3a)
        WITH_POWER_X.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(base, ObjectFilter.Power(Comparison.Equal, Value.Announced(name = "X")))
        }
        // ltr chunk 04: "creature with lesser power" (than this creature)
        WITH_LESSER_POWER.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(base, ObjectFilter.Power(if (m.groupValues[2] == "lesser") Comparison.Less else Comparison.Greater,
                Value.Of(arcanum.api.NumericProperty.Power, arcanum.api.Objects.Self, arcanum.api.Combine.Single)))
        }
        return null
    }

    /** ltr: a value "where X is …" defines. */
    fun value(text: String, event: EventPattern? = null): Value? = when (text) {
        // ltr chunk 04: the Army chosen as the amass instruction was followed (CR 701.47c), named by [Sentences]
        "the amassed Army's power" -> Value.Of(arcanum.api.NumericProperty.Power, arcanum.api.Objects.Bound(AMASSED_ARMY), arcanum.api.Combine.Single)
        // ltr chunk 04: the scry event's number of cards (CR 701.22a)
        "the number of cards looked at while scrying this way" -> Value.EventAmount
        // ltr chunk 07: the excess damage of the damage event (CR 120.4a)
        "that excess damage" -> Value.EventAmount.takeIf { event is EventPattern.Damage && event.excess }
        else -> null
    }

    /** ltr: a condition. */
    fun condition(text: String): arcanum.api.Condition? {
        // ltr chunk 14: "you have 5 or less life"
        Ltr.YOU_HAVE_N_OR_LESS_LIFE.matchEntire(text)?.let { m ->
            return arcanum.api.Condition.Compare(Value.LifeOf(arcanum.api.Players.You), Comparison.AtMost, Value.Fixed(m.groupValues[1].toInt()))
        }
        // ltr chunk 07: "you control a creature with the greatest power among creatures on the battlefield" (ties count, CR 208.1)
        if (Ltr.CONTROL_CREATURE_WITH_GREATEST_POWER.matches(text)) {
            val creature = ObjectFilter.Type(arcanum.api.CardType.Creature)
            return arcanum.api.Condition.Exists(Filters.all(creature, ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.You), ObjectFilter.Power(Comparison.AtLeast,
                Value.Of(arcanum.api.NumericProperty.Power, arcanum.api.Objects.Each(creature), arcanum.api.Combine.Greatest))), atLeast = 1)
        }
        // ltr chunk 13: "there are four or more lore counters among Sagas you control"
        Ltr.LORE_COUNTERS_AMONG_SAGAS.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return@let
            return arcanum.api.Condition.Compare(Value.Of(arcanum.api.NumericProperty.Counters(arcanum.api.CounterKind.Lore), arcanum.api.Objects.Each(Filters.all(
                ObjectFilter.Sub(arcanum.api.SubtypeRef.Literal(arcanum.api.Subtype("Saga"))), ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.You))),
                arcanum.api.Combine.Total), Comparison.AtLeast, Value.Fixed(n))
        }
        // ltr chunk 12: "two or more creatures died under your control this turn"
        Ltr.N_OR_MORE_CREATURES_DIED_UNDER_YOUR_CONTROL.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return@let
            return arcanum.api.Condition.HappenedThisTurn(EventPattern.ZoneChange(Filters.all(ObjectFilter.Type(arcanum.api.CardType.Creature),
                ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.You)), from = Zone.Battlefield, to = Zone.Graveyard), atLeast = n, measure = arcanum.api.Measure.Events)
        }
        // ltr chunk 10: "a permanent you controlled left the battlefield this turn" (as it last existed, CR 603.10a)
        if (Ltr.PERMANENT_YOU_CONTROLLED_LEFT.matches(text))
            return arcanum.api.Condition.HappenedThisTurn(EventPattern.ZoneChange(Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Permanent),
                ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.You)), from = Zone.Battlefield), atLeast = 1, measure = arcanum.api.Measure.Events)
        // ltr chunk 08: "this creature attacked this turn"
        Ltr.ATTACKED_THIS_TURN.matchEntire(text)?.takeIf { it.groupValues[1] == "this creature" }?.let {
            return arcanum.api.Condition.ObjectMatches(arcanum.api.Objects.Self, ObjectFilter.ThisTurn(EventPattern.Attack(ObjectFilter.Is(arcanum.api.Objects.Self))))
        }
        return null
    }

    /** The name the amassed Army is bound to (CR 701.47c). */
    const val AMASSED_ARMY: String = "army"
}
