package arcanum.parser

import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Designation
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Value
import arcanum.api.Zone

/** Regular expressions of The Hobbit (hob) wordings read in [Sentences]. */
internal object Hob {
    // hob chunk 01: storied (CR 702.195)
    val AS_LONG_AS_ENDURING = Regex("""As long as you have an enduring story, (.+)""")
    val DOESNT_UNTAP_UNLESS = Regex("""(.+?) doesn't untap during your untap step unless (.+)\.""")
    val PAY_ZERO_RATHER_THAN_EQUIP = Regex("""[Yy]ou may pay (\{\d+\}) rather than pay the equip cost of the first equip ability you activate each turn\.""")
    val MAY_DISCARD_HAND_DRAW_X = Regex("""You may discard your hand\. Draw X cards, where X is the number of cards discarded this way\.""")
    val IF_ENDURING_DEALS_X = Regex("""If you have an enduring story, (.+?) deals X damage to each opponent\.""")

    // hob chunk 02: recruit (CR 701.70a)
    val RECRUIT = Regex("""(?:You )?[Rr]ecruit\.""")
    val IF_SPELLS_MANA_VALUE_RECRUIT = Regex("""If that spell's mana value was (\d+) or (less|greater), recruit\.""")

    // hob chunk 03: amass (CR 701.47a)
    val AMASS = Regex("""[Aa]mass (\w+) (\d+|X)(, then attach (.+?) to the amassed Army)?\.""")
    val CONTROLLER_AMASSES_X_POWER = Regex("""Its controller amasses (\w+) X, where X is that creature's power\.""")
    val AMASS_X_POWER = Regex("""[Aa]mass (\w+) X, where X is (.+?)'s power\.""")
    val AMASS_INSTEAD_IF_GRAVEYARD = Regex("""[Aa]mass (\w+) (\d+)\. If this spell was cast from a graveyard, amass (\w+) (\d+) instead\.""")
    val IF_YOU_CONTROLLED_THAT_CREATURE = Regex("""If you controlled that creature, (.+)""")
    val PUT_COUNTERS_ON_ONE_OR_MORE = Regex("""Whenever you put one or more counters on (.+ you control), (.+?) deals (\d+) damage to target opponent\.""")
    val PER_OPPONENT_EXILE_UNTIL_LEAVES = Regex("""For each opponent, exile up to one target (.+?) that player controls until (.+?) leaves the battlefield\.""")
    val IT_GETS_FOR_EACH = Regex("""It gets \+(\d+)/\+(\d+) until end of turn for each (.+)\.""")
    val TARGET_OPPONENT_SACRIFICES = Regex("""Target opponent sacrifices an? (.+?) of their choice\.""")
    val IF_THAT_CREATURE_IS_COUNTER_ON_IT = Regex("""If that creature is an? (.+?), put an? (\S+) counter on it\.""")
    val CREATURES_TARGET_PLAYER_CONTROLS_GET = Regex("""(Creatures) target player controls get ([+-]\d+)/([+-]\d+) until end of turn\.""")
    val CREATE_X_TAPPED = Regex("""Create X tapped (\w+) tokens\.""")
    val A_PLAYER_LOSES_LIFE = Regex("""a player loses life""")
    val SACRIFICE_WHEN_YOU_DO_EXCESS = Regex("""You may sacrifice another creature\. When you do, (.+?) deals damage equal to that creature's power to another target creature\. If excess damage was dealt this way, amass (\w+) X, where X is that excess damage\.""")

    // hob chunk 04: Sagas and their exiled cards
    val SEARCH_EXILE_THEM = Regex("""Search your library for up to (\w+) (.+?) cards, exile them, then shuffle\.""")
    val PUT_A_CARD_EXILED_WITH_INTO_HAND = Regex("""Put a card exiled with (.+?) into its owner's hand\.""")
    val WHENEVER_YOU_ATTACK_THIS_TURN = Regex("""Whenever you attack this turn, (.+)""")
    val FOR_AS_LONG_AS_REMAINS = Regex("""(.+?) for as long as (.+?) remains on the battlefield\.""")
    val PREVENT_ALL_DAMAGE_DEALT_BY = Regex("""Prevent all damage that would be dealt by (up to one )?target (.+)""")
    val EXILE_UP_TO_ONE_RETURN_NEXT_END = Regex("""Exile up to one target (.+?)\. If you do, return it to the battlefield under its owner's control at the beginning of the next end step\.""")
    val CREATE_THEN_IF_SACRIFICE_SELF = Regex("""Create (.+?)\. Then if you control (\w+) or more (.+?), sacrifice (.+?)\. If you do, create (.+)\.""")
    val SAGA_GAINS_QUOTED = Regex("""(.+?) gains "(.+)"\.""")

    // hob chunk 05: adventures
    val MILL_THEN_PUT_FROM_AMONG = Regex("""Mill (\w+) cards, then put (an|all|up to (?:one|two|three)) (.+?) cards? from among them into your hand\.""")
    // (the dsk chunk 12 wording "untapped creatures you control" with "They each …" after it is read by its own rule)
    val TAP_ONE_OR_TWO = Regex("""Tap one or two target (creatures)\.""")
    val EXCHANGE_CONTROL_SHARING = Regex("""Exchange control of two target (.+?) that share a card type\.""")
    val EXILE_TWO_RETURN = Regex("""Exile two target (.+?), then return them to the battlefield under their owner's control\.""")
    val LOOK_EXILE_FACE_DOWN_PLAY_IF = Regex("""Look at the top (\w+) cards of your library and exile them face down\. For as long as they remain exiled, you may play them if you control an? (.+)\.""")
    val CREATE_X_TOKENS = Regex("""Create X (.+) tokens\.""")
    val YOU_MAY_PLAY_ADDITIONAL_LAND = Regex("""You may play an additional land this turn\.""")

    // hob chunk 06: Equipment
    val CREATE_THEN_ATTACH_SELF = Regex("""Create (.+?) token, then attach (.+?) to it\.""")
    val CREATE_NAMED_EQUIPMENT_WHEN_YOU_DO = Regex("""(Create a colorless Equipment artifact token named .+?)\. When you do, attach it to target (.+)\.""")
    val EACH_EQUIPPED_ATTACKING_GAINS = Regex("""Each equipped attacking creature gains (.+) until end of turn\.""")
    val ATTACH_TARGET_EQUIPMENT_TO_UP_TO_ONE = Regex("""[Aa]ttach target Equipment you control to up to one target (.+)\.""")
    val ATTACH_ANY_NUMBER_REFLEXIVE = Regex("""[Aa]ttach any number of target Equipment you control to target (.+?)\. When one or more Equipment become attached to that creature this way, that creature deals damage equal to its power to up to one target (.+)\.""")
    val EQUIP_ABILITIES_TARGETING_SELF_COST_LESS = Regex("""Equip abilities you activate that target (.+?) cost ((?:\{[^}]+\})+) less to activate\.""")
    val TRIGGERED_ABILITY_OF_EQUIPPED = Regex("""If a triggered ability of equipped creature triggers, that ability triggers an additional time\.""")
    val HONE_PER_CREATURE_THEN_ATTACH = Regex("""Put a hone counter on (.+?) for each (.+?) target opponent controls\. Attach (.+?) to up to one target (.+)\.""")
    val DEALS_1_IF_DRAGON_DESTROY = Regex("""(?:It|(.+?)) deals (\d+) damage to any target\. If an? (.+?) is dealt damage this way, destroy it\.""")
    val IF_DWARF_MAY_ATTACH = Regex("""If it's an? (.+?), you may attach an? (.+?) you control to it\.""")
    val CHOOSE_TYPE_TREASURE_PER = Regex("""Choose a creature type\. Create a (\w+) token for each creature you control of that type\.""")
    val INSTANT_SORCERY_COST_X_LESS_EQUIPPED = Regex("""(.+? spells you cast) cost \{X\} less to cast, where X is equipped creature's power\.""")
    val ATTACH_SELF_TO_TARGET_DWARF = Regex("""[Aa]ttach (?:it|this Equipment) to target (.+)\.""")

    // hob chunk 07: drawing and landfall
    val OPPONENT_DRAWS_NTH = Regex("""an opponent draws their (\w+) card each turn""")
    val TWO_TARGET_PLAYERS_EACH_DRAW = Regex("""(\w+) target players each draw (a|\w+) cards?\.""")
    val GRAVEYARDS_WITH_SEVEN = Regex("""graveyard with (\w+) or more cards in it""")
    val GETS_FOR_EACH_GRAVEYARD = Regex("""(.+?) gets \+(\d+)/\+(\d+) for each graveyard with (\w+) or more cards in it\.""")
    val DRAW_FOR_EACH_GRAVEYARD = Regex("""Draw a card for each graveyard with (\w+) or more cards in it\.""")
    val MAY_HAVE_BASE_PT_BECOME = Regex("""[Yy]ou may have (.+?)'s base power and toughness become (\d+)/(\d+) until end of turn\.""")
    val MAY_DRAW_X_IF_YOU_DO_DISCARD = Regex("""You may draw X cards, where X is the amount of mana spent to cast that spell\. If you do, discard (\w+) cards\.""")
    val DISCARD_LAND_THIS_WAY_PUT_ONTO = Regex("""If you discard a land card this way, put it from your graveyard onto the battlefield tapped\.""")
    val BECOMES_CREATURE_GAINS_QUOTED = Regex("""(.+?) becomes an? ([A-Z][a-z]+) creature in addition to its other types and gains "(.+)"\.""")

    // hob chunk 08: wolves and ferocious
    val CANT_ATTACK_UNLESS_CONTROL = Regex("""(.+?) can't attack unless you control (\w+) or more (.+)\.""")
    val UNTIL_EOT_GETS_AND_GAIN = Regex("""[Uu]ntil end of turn, (.+?) gets \+(\d+)/\+(\d+) and (.+?) gain (.+)\.""")
    val IF_EXILE_INSTEAD_WHEN_YOU_DO = Regex("""If (.+?) would die, exile it instead\. When you do, (.+)""")

    // hob chunk 09: white and blue
    val EAGLES_ARE_COMING = Regex("""Choose target (.+?) you own\. If this spell was kicked, instead choose any number of target (.+?) you own\. Return each chosen creature to your hand\. At the beginning of the next upkeep, create (.+?) for each creature returned to your hand this way\.""")
    val SEARCH_PUT_ONE_BATTLEFIELD_OTHER_HAND = Regex("""Search your library for up to (\w+) (.+?) cards, reveal them, put one onto the battlefield tapped and the other into your hand, then shuffle\.""")
    val EXILE_ALL_ATTACKING_TARGET_PLAYER = Regex("""Exile all attacking creatures target player controls\. That player may search their library for that many (.+?) cards, put those cards onto the battlefield tapped, then shuffle\.""")
    val BECOMES_TYPE_AND_GAINS = Regex("""Until end of turn, target (.+?) becomes an? (artifact|enchantment) in addition to its other types and gains (.+)\.""")
    val PLAYERS_CANT_CAST_THIS_TURN = Regex("""[Pp]layers can't cast spells this turn\.""")
    val RETURN_TARGET_SPELL_TO_HAND = Regex("""Return target spell to its owner's hand\.""")
    val OWNER_SHUFFLES_SELF_DRAWS = Regex("""(.+?)'s owner shuffles (?:him|her|it|them) into their library and draws (\w+) cards\.""")
    val EXILE_UP_TO_TWO_RETURN_NEXT_END = Regex("""Exile up to (\w+) other target (.+?)\. Return those cards to the battlefield under their owner's control at the beginning of the next end step\.""")
    val TAP_ENCHANTED_REMOVE_COUNTERS = Regex("""[Tt]ap enchanted creature and remove all counters from it\.""")
    val RIDDLES = Regex("""Look at the top (\w+) cards of your library and separate them into a face-down pile and a face-up pile\. An opponent chooses one of the piles\. Put that pile into your hand and the other into your graveyard\.""")
    val COUNTER_EXILE_PERMANENT_CAST_FREE = Regex("""Counter target spell\. If a permanent spell is countered this way, exile it instead of putting it into its owner's graveyard\. You may cast that card without paying its mana cost for as long as it remains exiled\.""")
    val CAST_FROM_ANYWHERE_OTHER_COST_LESS = Regex("""Spells you cast from anywhere other than your hand cost ((?:\{[^}]+\})+) less to cast\.""")
    val MAY_CAST_FROM_GRAVEYARD_EXILE_INSTEAD = Regex("""You may cast an? (.+?) spell from your graveyard\. If an? (.+?) spell cast this way would be put into your graveyard, exile it instead\.""")
    val OWNER_PUTS_TOP_OR_BOTTOM = Regex("""Target (.+?)'s owner puts it on their choice of the top or bottom of their library\.""")
    val ACTIVATE_ABILITY_OF = Regex("""you activate an ability of an? (.+)""")

    // hob chunk 10: black and red
    val SACRIFICES_OF_THEIR_CHOICE = Regex("""(.+? sacrifices? an? .+?) of their choice\.""")
    val EXILE_UP_TO_ONE_FROM_OPPONENTS_GRAVEYARD = Regex("""[Ee]xile up to one target card from an opponent's graveyard\.""")
    val CHOOSE_ODD_OR_EVEN = Regex("""As (.+?) enters, choose (\w+) or (\w+)\.""")
    val OPPONENT_CASTS_CHOSEN_QUALITY = Regex("""an opponent casts a spell with mana value of the chosen quality""")
    val PLAY_EXILED_TOP_X_PAY_LIFE = Regex("""Exile the top X cards of target opponent's library\. You may play those cards this turn\. If you cast a spell this way, pay life equal to its mana value rather than pay its mana cost\.""")
    val THAT_PLAYER_MILLS_THAT_MANY = Regex("""That player mills that many cards\.""")
    val YOU_DRAW_AND_LOSE = Regex("""You draw (a|\w+) cards? and lose (\d+) life\.""")
    val MAY_SACRIFICE_COUNTERS_EQUAL_POWER = Regex("""You may sacrifice (another .+?)\. If you do, put a number of \+1/\+1 counters on (.+?) equal to the sacrificed creature's power\.""")
    val SUPPER = Regex("""Put onto the battlefield under your control all (.+?) in your opponents' graveyards that were put there from the battlefield this turn\. They are (\w+) artifacts with "(.+)"""")
    val GETS_AND_DEALS = Regex("""(.+?) gets \+(\d+)/\+(\d+) until end of turn and deals (\d+) damage to each opponent\.""")
    val DEALS_DIVIDED_AMONG = Regex("""(?:He|She|It|(.+?)) deals (\d+) damage divided as you choose among one, two, or three targets\.""")
    val DEALS_DAMAGE_EQUAL_NUMBER_ANY = Regex("""(?:He|She|It|(.+?)) deals damage equal to (the number of .+?) to any target\.""")
    val REVEAL_TOP_RANDOM_ONTO = Regex("""Reveal the top (\w+) cards of your library\. Put a random (.+?) card from among them onto the battlefield\. Put the rest on the bottom of your library in a random order\.""")
    val QUEST_COUNTER_SACRIFICE_SEARCH = Regex("""Put an? (\w+) counter on (.+?)\. If it has (\w+) or more \w+ counters on it, sacrifice it\. If you do, search your hand and/or library for an? (.+?) card and put it onto the battlefield\. If you search your library this way, shuffle\.""")
    val CREATE_NAMED_ARTIFACT_CREATURE_TOKEN = Regex("""[Cc]reate an? (\d+)/(\d+) colorless ([A-Z][a-z]+) artifact creature token with ([a-z]+) named ([A-Z][A-Za-z ]+)\.""")
    val CREATE_X_TAPPED_TREASURES_WHERE = Regex("""[Cc]reate X tapped (\w+) tokens, where X is (.+)\.""")
    val IF_MANA_FROM_SPENT = Regex("""if mana from an? (.+?) was spent to cast it""")
    val TOKENS_TWICE_THAT_MANY = Regex("""If one or more tokens would be created under your control, twice that many of those tokens are created instead\.""")
    val DRAW_EXCEPT_FIRST_DRAW_TWO = Regex("""If you would draw a card except the first one you draw in each of your draw steps, draw two cards instead\.""")
    val DIES_IF_CREATURE_RETURN_ARTIFACT = Regex("""When (.+?) die, if they were a creature, return them to the battlefield\. They're an artifact\.""")
    val MAY_CAST_AS_FLASH_IF_CONTROL = Regex("""You may cast this spell as though it had flash if you control an? (.+)\.""")
    val HAS_ACTIVATED_OF_CARDS_IN_GRAVEYARD = Regex("""(.+?) has all activated abilities of all (.+?) cards in your graveyard\.""")

    // hob chunk 11: green and multicoloured
    val TRAMPLE_COUNTER_BECOMES_THEN_DRAW = Regex("""[Pp]ut an? (\S+) counter on up to one target (.+?)\. It becomes an? ([A-Z][a-z]+) in addition to its other types\. Then if you control (\w+) or more (.+?), draw (\w+) cards\.""")
    val PUT_X_COUNTERS_WHERE_SPELLS_MV = Regex("""[Pp]ut X (\S+) counters on target (.+?), where X is that spell's mana value\.""")
    val CHOOSE_UP_TO_ONE_BASE_PT_EQUAL = Regex("""[Cc]hoose up to one (other target .+?)\. Its base power and toughness become equal to (.+?)'s power and toughness until end of turn\.""")
    val NOT_TOKEN_CREATE_COPIES_NOT_LEGENDARY = Regex("""When (.+?) enters?, if they're not a token, create (\w+) tokens that are copies of them, except the tokens aren't legendary\.""")
    val REVEAL_UNTIL_MV_LANDS = Regex("""Reveal cards from the top of your library until you reveal an? (.+?) card\. If its mana value is less than or equal to the number of (.+?), put it onto the battlefield\. Otherwise, put it into your hand\. Put the rest on the bottom of your library in a random order\.""")
    val FIRST_SPELL_COSTS_LESS_AND_FLASH = Regex("""The first (.+?) spell you cast each turn costs ((?:\{[^}]+\})+) less to cast and can be cast as though it had flash\.""")
    val LOOK_TOP_PUT_ANY_NUMBER_ONTO_THEN_SHUFFLE = Regex("""Look at the top (\w+) cards of your library, put any number of (.+?) cards from among them onto the battlefield tapped, then shuffle\.""")
    val ADD_X_ANY_ONE_COLOR_POWER = Regex("""Add X mana of any one color, where X is (.+?)'s power\. Spend this mana only to cast (.+?) spells and activate abilities of (.+?) sources\.""")
    val RETURN_SELF_ATTACHED_TO_TARGET = Regex("""Return (.+?) from your graveyard to the battlefield attached to target (.+)\.""")
    val RETURN_UP_TO_ONE_IF_YOU_DO_COUNTER = Regex("""[Rr]eturn up to one (other target .+?) to its owner's hand\. If you do, put an? (\S+) counter on (.+?)\.""")
    val DISCARD_LEGENDARY_SAME_NAME = Regex("""Discard a legendary card with the same name as a legendary permanent you control""")
    val BEHOLD_IF_YOU_DO_UNTAP = Regex("""You may behold an? (.+?)\. If you do, untap that land\.""")
}

/** The Hobbit (hob) noun, value and condition phrases, tried by [Filters.noun], [Values.value] and [Conditions.condition]. */
internal object HobPhrases {
    /** "you have an enduring story" (CR 702.195b) */
    val enduringStory: Condition = Condition.PlayerMatches(Players.You, PlayerFilter.Has(Designation.EnduringStory))

    fun condition(text: String): Condition? {
        // hob chunk 01
        if (text == "you have an enduring story") return enduringStory
        // hob chunk 12: "mana from a Treasure was spent to cast it" (the spell of a cast trigger, CR 106.3)
        MANA_FROM_SPENT.matchEntire(text)?.let { m ->
            return Condition.Compare(Value.ManaSpent(of = Objects.EventObject(arcanum.api.EventRole.Object), source = Filters.noun(m.groupValues[1]) ?: return null),
                arcanum.api.Comparison.Greater, Value.Fixed(0))
        }
        return null
    }
    private val MANA_FROM_SPENT = Regex("""mana from an? (.+?) was spent to cast it""")

    /** "each graveyard with seven or more cards in it": the players whose graveyards hold that many cards. */
    fun graveyardsWith(count: Int): Players = Players.Each(PlayerFilter.Where(Condition.Exists(
        ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Tested)))), atLeast = count)))

    fun value(text: String): Value? {
        // hob chunk 06: "the number of Treasures you control", "the number of artifacts your opponents control" (generic counting
        // already reads most; this is for the total power of creatures)
        // hob chunk 09: "the total power of creatures you control with flying"
        if (text.startsWith("the total power of ")) {
            val noun = text.removePrefix("the total power of ")
            return Value.Of(NumericProperty.Power, Objects.Each(Filters.noun(noun) ?: return null), Combine.Total)
        }
        return null
    }
}
