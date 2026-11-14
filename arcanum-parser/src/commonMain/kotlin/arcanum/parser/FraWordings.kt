package arcanum.parser

import arcanum.api.AbilityKind
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.EventPattern
import arcanum.api.KeywordActionKind
import arcanum.api.Measure
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.RecipientFilter
import arcanum.api.SharedQuality
import arcanum.api.Value
import arcanum.api.Zone

/** Regular expressions of the Reality Fracture (fra) wordings read in [Sentences]. */
internal object Fra {
    // fra chunk 01: "Empower Jace 2." / "empower Jace X" (CR 701.71a)
    val EMPOWER_JACE = Regex("""[Ee]mpower Jace (\d+|X)\.""")
    // fra chunk 01: "This creature enters prepared." (CR 722.3a)
    val ENTERS_PREPARED = Regex("""(.+?) enters prepared\.""")
    // fra chunk 01: "[objects] becomes prepared." / "It becomes unprepared." (CR 722.3a, b)
    val BECOMES_PREPARED = Regex("""(.+?) (?:becomes?) (prepared|unprepared)\.""")
    // fra chunk 01: "Create a 2/2 colorless Wizard Soldier creature token named Cadet." (CR 111.4)
    val CREATE_NAMED_TOKENS = Regex("""Create (a|an|one|two|three|four|five|X) ((?:\d+/\d+) \w+(?: and \w+)? [A-Z][A-Za-z ]+? creature) tokens? named ([A-Z][A-Za-z' ]+?)(?: with (".+"|[a-z ,]+?))?(, then .+)?\.""")

    val THEN_COUNTER_ON_EACH_OTHER = Regex(""", then put an? (\S+) counter on each other (.+)""")

    // whole lines
    val ENTERS_IF_AT_LEAST_OTHER = Regex("""Whenever an? (.+?) you control enters, if you control at least (\w+) other (.+?), (.+)""")
    val ACTIVATE_LOYALTY_IF_REMOVED = Regex("""Whenever you activate a loyalty ability, if you removed (\w+) or more loyalty counters to activate it, (.+)""")
    val ENTERS_GETS_X_THAT_CREATURES_POWER = Regex("""Whenever (another creature you control enters), (.+?) gets \+X/\+0 until end of turn, where X is that creature's power\.""")
    val CLASH = Regex("""Choose target (.+?)\. Its owner may put it on top of their library\. If they do, (.+?) deals (\d+) damage to them\. If they didn't put the card on top of their library, they put it on the bottom\.""")

    // sentences changing the steps before them
    val IF_PUT_INTO_GRAVEYARD_THIS_WAY = Regex("""If you put (.+?) into your graveyard this way, put that card into your hand\.""")
    val IF_INSTEAD = Regex("""If (.+?), (put .+? on that creature) instead\.""")
    val IF_THAT_PERMANENT_WAS = Regex("""If that permanent was an? (.+?), (.+)\.""")
    val IF_MANA_VALUE_WAS_RETURN = Regex("""If that permanent's mana value was (\d+) or (less|greater), return it to the battlefield tapped under your control\.""")
    val IF_CAST_FROM_GRAVEYARD_COUNTERS_EVERY = Regex("""If this spell was cast from a graveyard, put a \+1/\+1 counter on each of them for every (\w+) cards in your graveyard\.""")
    val IF_YOU_SACRIFICED_THIS_WAY = Regex("""If you sacrificed an? (.+?) this way, (.+)\.""")
    val FOR_EACH_OPPONENT_WHO_DIDNT_DISCARD = Regex("""For each opponent who didn't discard (\w+) (.+?) this way, you draw a card\.""")

    /** "a card with mana value less than or equal to the amount of life you gained this turn" */
    fun mvAtMostLifeGained(phrase: String): ObjectFilter? =
        if (phrase == "a card with mana value less than or equal to the amount of life you gained this turn")
            ObjectFilter.ManaValue(Comparison.AtMost, Value.EventsThisTurn(EventPattern.GainLife(PlayerFilter.You), Measure.Amount)) else null

    // statics
    val AS_ENTERS_MAY_BEHOLD = Regex("""As (.+?) enters, you may behold an? (.+?)\. If you don't, (.+?) enters tapped\.""")
    val ANY_NUMBER_IN_DECK = Regex("""A deck can have any number of cards named (.+)\.""")
    val AS_LONG_AS_ATTACK_DESPITE_DEFENDER = Regex("""As long as (.+?), (.+?) can attack as though it didn't have defender\.""")
    val CAN_ATTACK_DESPITE_DEFENDER = Regex("""(.+?) can attack as though they didn't have defender\.""")
    val ASSIGNS_BY_TOUGHNESS = Regex("""Each (.+?) assigns combat damage equal to its toughness rather than its power\.""")
    val POWER_NEGATIVE_ASSIGNS = Regex("""If (.+?)'s power is negative, (?:he|she|it) assigns combat damage as though (?:his|her|its) power were positive\.""")
    val NO_GRAVEYARD_FOR_ZERO_LOYALTY = Regex("""(.+?) aren't put into their owners' graveyards for having 0 loyalty\.""")
    val ENTERING_DOESNT_TRIGGER = Regex("""(.+?) entering(?: the battlefield)? don't cause abilities(?: of (.+?))? to trigger\.""")
    val SPELLS_HAVE_KEYWORD = Regex("""(.+? spells you control) have ([a-z ]+)\.""")
    val SPELLS_OPPONENTS_CAST_COST_MORE = Regex("""(.+? spells) your opponents cast cost (\{\d+\}) more to cast\.""")
    val COSTS_X_LESS_WHERE = Regex("""(.+?) costs \{X\} less to cast, where X is (.+)\.""")
    val COSTS_LESS_IF = Regex("""(.+?) costs ((?:\{[^}]+\})+) less to cast (if .+?)\.""")
    val GETS_FOR_EVERY = Regex("""(.+?) gets \+(\d+)/\+(\d+) for every (\w+) (cards in your graveyard)\.""")
    val COUNTERS_PLUS = Regex("""If one or more (\S+) counters would be put on (an? .+?), that many plus (\w+) (\S+) counters are put on (?:it|that permanent|that creature) instead\.""") // mom: "that permanent"
    val TOKENS_CREATED_INSTEAD = Regex("""If one or more (.+?) tokens would be created under your control, that many (.+? creature) tokens(?: with (.+))? are created instead\.""")
    val ENCHANTED_IS_CREATURE_WITH_BASE = Regex("""Enchanted permanent is an? ([A-Z][a-z]+(?: [A-Z][a-z]+)*) creature with base power and toughness (\d+)/(\d+) in addition to its other types\.""")
    val EQUIPPED_GETS_AND_HAS_QUOTED = Regex("""Equipped creature gets \+(\d+)/\+(\d+) and has (?:([a-z ,]+?) and )?"(.+)"""")
    val HAVE_NO_MORE_THAN_ATTACK = Regex("""(.+?) you control have "No more than (\w+) creatures? can attack this planeswalker each combat\."""")
    // one chunk 01: Ichormoon Gauntlet, two quoted loyalty abilities ("[0]: Proliferate" and "[−12]: Take an extra turn after this one.")
    val HAVE_QUOTED_LOYALTY = Regex("""(Planeswalkers) you control have "(\[[^\]]+\]: [^"]+)"(?: and "(\[[^\]]+\]: [^"]+)")?""")
    val AS_LONG_AS_MAY_CAST_EXILED = Regex("""As long as (.+?), you may cast the exiled card, and mana of any type can be spent to cast that spell\.""")
    val SPELLS_WITH_MV_AT_MOST = Regex("""spells with mana value less than or equal to (.+)""")
    val MAY_CAST_FROM_HAND_WITHOUT_PAYING = Regex("""You may cast (spells(?: with .+?)?) from your hand without paying their mana costs\.""")
    val HAS_KEYWORD_UNTIL_DEALT_COMBAT_DAMAGE = Regex("""(.+?) has ([a-z ,]+) as long as (?:it hasn't|they haven't|he hasn't|she hasn't) dealt combat damage yet\.""")
    val POWER_EQUAL_TO = Regex("""(.+?)'s power is equal to (.+?)(?:( and its toughness is equal to that number plus) (\w+))?\.""")

    // effects
    val HE_SHE = Regex("""(?:He|She) ((?:gains|gets|has) .+)""")
    // otj chunk 06: "a tapped …", several quoted abilities ("… with "A" and "B".")
    val CREATE_NAMED_NONCREATURE_TOKEN = Regex("""Create an? (tapped )?(colorless|\w+(?: and \w+)?) ([a-z]+(?: [a-z]+)?) token named ([A-Z][A-Za-z' ]+) with "(.+)"\.""")
    val THEY_CREATE = Regex("""They create (.+? tokens?)\.""")
    val COUNTER_ON_EACH_OF_TARGETS = Regex("""Put an? (\S+) counter on each of (\w+) or (\w+) target (.+)\.""")
    val PUT_COUNTER_ON_IT_IF = Regex("""Put an? (\S+) counter on it if it's an? (.+)\.""")
    val RETURN_WITH_ADDITIONAL_COUNTER = Regex("""(Return .+? to the battlefield) with an additional (\S+) counter on it\.""")
    val OWNER_PUTS_TOP_OR_BOTTOM = Regex("""The owner of (up to one )?(other )?target (.+?) puts it on their choice of the top or bottom of their library\.""")
    val DISCARD_REFLEXIVE_TAP_STUN = Regex("""Draw (\w+) cards, then discard (\w+) cards\. When you discard one or more nonland cards this way, tap up to that many target creatures and put a stun counter on each of them\.""")
    val MAY_EXILE_SPELL_AND_NAMED = Regex("""You may exile this spell and (\w+) cards named (.+?) from your graveyard\. If you do, (.+)""")
    val EACH_PLAYER_MAY_DISCARD_HAND_DRAW = Regex("""Each player may discard their hand and draw (\w+) cards\.""")
    val FOR_EACH_CREATURE_COPY_EXCEPT = Regex("""For each creature target player controls, create a token that's a copy of that creature, except it has (\w+) and "(.+)"\.""")
    val REVEAL_UNTIL = Regex("""Reveal cards from the top of your library until you reveal an? (.+?) card\.""")
    // otj chunk 11: "each of two other target creatures"; mom chunk 12: "another target creature, planeswalker, or battle"
    val TARGET_DEALS_DAMAGE_EQUAL_TO_ITS = Regex("""Target (.+?) deals damage equal to its (power|loyalty) to (that permanent|(?:another )?target .+?|each of (\w+) other target (.+?))\.""")
    val TARGET_LOSES_ALL_ABILITIES = Regex("""Target (.+?) loses all abilities until end of turn\.""")
    val SEARCH_UP_TO_THAT_MANY = Regex("""(You may )?[Ss]earch your library for up to that many (.+?), put them onto the battlefield( tapped)?, then shuffle\.""")
    val GAINS_YOUR_CHOICE_OF = Regex("""(It|.+?) gains your choice of (.+?) until end of turn\.""")
    val THAT_CREATURE_DEALS_EACH_OPPONENT = Regex("""That creature deals (\d+) damage to each opponent\.""")
    val FIGHTS_UP_TO_ONE = Regex("""(It|.+?) fights up to one target (.+)\.""")
    val COUNTER_ON_EACH_TARGET_PLAYER_CONTROLS = Regex("""Put an? (\S+) counter on each (.+?) target player controls\.""")
    val OWNER_SHUFFLES_SELF = Regex("""(.+?)'s owner shuffles (?:him|her|it) into their library\.""")
    val DEALS_DAMAGE_TO_EACH_EXCEPT = Regex("""(.+?) deals (\d+) damage to each (.+?) except for (.+)\.""")
    val IF_YOU_DONT = Regex("""If you don't, (.+)\.""")
    val COUNTER_FOR_EACH_DISCARDED_THIS_TURN = Regex("""Put an? (\S+) counter on that creature for each card you've discarded this turn\.""")
    val IF_THAT_LAND_IS_ADD = Regex("""If that land is an? (.+?), (add .+)\.""")
    val DEALS_DAMAGE_TO_THAT_CREATURES_CONTROLLER = Regex("""(.+?) deals (\d+) damage to that (creature|permanent)'s controller\.""") // rvr chunk 04: permanent
    val DEALS_DAMAGE_TO_UP_TO_ONE_AND_PLAYER = Regex("""(.+?) deals (\d+) damage to up to one target (.+?) and (\d+) damage to target player\.""")
    val SEARCH_UP_TO_X_DIFFERENT_NAMES = Regex("""Search your library for up to X (.+?) with different names, reveal them, put them into your hand, then shuffle\.""")
    val UNTAP_UP_TO = Regex("""Untap up to (\w+) target (.+)\.""")
    val UNTAP_ALL = Regex("""Untap all (.+)\.""")
    val UNTIL_NEXT_TURN_WHENEVER_ATTACK_OPPONENT = Regex("""Until your next turn, whenever one or more creatures attack one of your opponents, those creatures get \+(\d+)/\+(\d+) and gain (\w+) until end of turn\.""")
    val DRAW_FOR_EACH_COLOR_AMONG = Regex("""Draw a card for each (color|different mana value) among (.+)\.""")
    val PROLIFERATE_TIMES = Regex("""[Pp]roliferate (?:(\w+) times|(twice))\.""") // one chunk 01: Ezuri, "proliferate twice"
    val REMOVE_UP_TO_COUNTERS = Regex("""Remove up to (\w+) counters from (another )?target (.+)\.""")
    val EXILE_UP_TO_ONE_THEN_RETURN = Regex("""Exile up to one (other )?target (.+?), then return that card to the battlefield under its owner's control\.""")
    val PER_OPPONENT = Regex("""(destroy|tap|exile|return|put \w+ \S+ counters? on) (up to one )?target (.+?) that player controls( to its owner's hand)?\.""")
    val BEHOLD = Regex("""[Bb]ehold an? (.+)""")
    val EXILE_FROM_HAND_TARGET_GAINS_UNTIL_CAST = Regex("""(.+?, Exile this card from your hand): Target (.+?) gains "(.+)" until this card is cast from exile\. You may cast this card for as long as it remains exiled\.""")
    val DIES_IF_NOT_TOKEN_COPY = Regex("""When (.+?) dies, if it isn't a token, create a token that's a copy of it\.""")
    val EQUIP_COSTS_LESS_PER_COUNTER = Regex("""Equip (\{[^}]+\}(?:\{[^}]+\})*)\. This ability costs (\{\d+\}) less to activate for each (\S+) counter on the creature it targets\.""")
    val EXILE_EACH_TYPE_COPY_CAST = Regex("""When (.+?) enters, if you cast (?:him|her|it), exile up to one target (.+?) card of each card type from your graveyard\. Copy those cards\. You may cast any number of spells with total mana value (\d+) or less from among the copies without paying their mana costs\.""")
    val PUT_ITS_COUNTERS_ON = Regex("""Put its counters on (up to one )?target (.+)\.""")
    val PUT_N_COUNTERS_ON_EACH = Regex("""Put (X|two|three|four) (\S+) counters on each (.+)\.""")
    // otj chunk 10: "it becomes a copy of up to one other target creature until end of turn"
    // otj chunk 11: ", except he has hexproof"
    val BECOMES_COPY_OF_TARGET_UNTIL_EOT = Regex("""(.+?) becomes a copy of (up to one )?(other |another )?target (.+?) until end of turn(?:, except (?:it|he|she|they) (has .+))?\.""")
    val PUT_COUNTERS_ON_IT = Regex("""Put (a|an|X|two|three) (\S+) counters? on (?:it|that creature)\.""")
    val PUT_COUNTER_ON_EACH_OF_THOSE = Regex("""Put an? (\S+) counter on each of those creatures( you don't control| you control)?\.""")
    val IT_FIGHTS_TARGET = Regex("""It fights target (.+)\.""")
    val LOYALTY_AT_INSTANT_SPEED = Regex("""Until end of turn, you may activate loyalty abilities of (.+?) on any player's turn any time you could cast an instant\.""")
    val IF_SACRIFICE_DEALS_GAIN = Regex("""If (.+?), sacrifice (.+?), it deals (\d+) damage to each opponent, and you gain (\d+) life\.""")
    val ADD_ONE_MANA_OF_THAT_COLOR_FOR_EACH_DIFFERENT = Regex("""Add one mana of that color for each different (power|toughness) among (.+)\.""")
    val PUT_N_COUNTERS = Regex("""put (\w+) (\S+) counters? on""")
}

/** Reality Fracture (fra) phrases: nouns, events, conditions and values tried first by [Filters], [Events], [Conditions]
 * and [Values]; each returns null when it can't read the phrase. */
internal object FraPhrases {
    private val COLORS = mapOf("white" to Color.White, "blue" to Color.Blue, "black" to Color.Black, "red" to Color.Red, "green" to Color.Green)
    private fun color(word: String) = COLORS[word]?.let { ObjectFilter.OfColor(it) }

    // fra chunk 02: "creature or planeswalker that's green or blue"
    private val THATS_COLOR_OR_COLOR = Regex("""(.+?) that's (white|blue|black|red|green) or (white|blue|black|red|green)""")
    // fra chunk 03: "creature that entered this turn", "creature that attacked this turn"
    private val THAT_ENTERED_THIS_TURN = Regex("""(.+?) that entered this turn""")
    private val THAT_ATTACKED_THIS_TURN = Regex("""(.+?) that attacked this turn""")

    private val WITH_MV_AT_MOST_VALUE = Regex("""(.+?) with mana value less than or equal to (the .+)""")

    fun noun(phrase: String): ObjectFilter? {
        // fra chunk 05: "prepared spell" (CR 722.3d)
        if (phrase == "prepared spell" || phrase == "prepared") return ObjectFilter.PrepareSpell
        // fra chunk 10: "spells with mana value less than or equal to the number of creatures you control"
        WITH_MV_AT_MOST_VALUE.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: Filters.noun(m.groupValues[1].removeSuffix("s")) ?: return null
            return Filters.all(base, ObjectFilter.ManaValue(Comparison.AtMost, Values.value(m.groupValues[2]) ?: return null))
        }
        THATS_COLOR_OR_COLOR.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(base, ObjectFilter.Or(listOf(color(m.groupValues[2])!!, color(m.groupValues[3])!!)))
        }
        THAT_ENTERED_THIS_TURN.matchEntire(phrase)?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Battlefield)))
        }
        THAT_ATTACKED_THIS_TURN.matchEntire(phrase)?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.ThisTurn(EventPattern.Attack(ObjectFilter.Any)))
        }
        return null
    }

    fun events(text: String, self: Set<String>): List<EventPattern>? {
        // fra chunk 04: "you scry or surveil" (one ability, two events)
        if (text == "you scry or surveil") return listOf(ActionWordings.youDid(KeywordActionKind.Scry), ActionWordings.youDid(KeywordActionKind.Surveil))
        return null
    }

    private val ACTIVATES_LOYALTY = Regex("""(you|an opponent) activates? a loyalty ability""")
    private val YOU_DISCARD_SELF = Regex("""you discard (.+)""")
    private val ATTACKS_A_PLAYER_ALONE = Regex("""(a creature you control) attacks a player alone""")

    private val OPPONENT_CASTS_SPELL_WITH = Regex("""an opponent casts an? (spell with .+)""")
    private val BEGINNING_OF_EACH_OPPONENTS_DRAW = Regex("""the beginning of each opponent's draw step""")

    fun event(text: String, self: Set<String>): EventPattern? {
        // fra chunk 01: "you cast this spell" (CR 603.6: it triggers from the Stack)
        if (text == "you cast this spell") return EventPattern.Cast(ObjectFilter.Is(Objects.Self), PlayerFilter.You)
        // fra chunk 05: "an opponent casts a spell with mana value 2 or less"
        OPPONENT_CASTS_SPELL_WITH.matchEntire(text)?.let { m -> return EventPattern.Cast(Filters.noun(m.groupValues[1]) ?: return null, PlayerFilter.Opponent) }
        // fra chunk 08: "the beginning of each opponent's draw step"
        if (BEGINNING_OF_EACH_OPPONENTS_DRAW.matches(text)) return EventPattern.StepBegins(arcanum.api.TurnStep.Draw, PlayerFilter.Opponent)
        // fra chunk 05: "you activate a loyalty ability", "an opponent activates a loyalty ability" (CR 606)
        ACTIVATES_LOYALTY.matchEntire(text)?.let { m ->
            return EventPattern.Activate(ObjectFilter.Any, by = if (m.groupValues[1] == "you") PlayerFilter.You else PlayerFilter.Opponent, kind = AbilityKind.Loyalty)
        }
        // fra chunk 05: "you discard this card"
        YOU_DISCARD_SELF.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return EventPattern.Discard(ObjectFilter.Is(Objects.Self), PlayerFilter.You)
        }
        // fra chunk 05: "an opponent is dealt noncombat damage" (CR 120)
        if (text == "an opponent is dealt noncombat damage")
            return EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.Opponent), combat = false, per = arcanum.api.DamageOccurrence.Recipient)
        // fra chunk 05: "a creature you control attacks a player alone" (CR 506.5)
        ATTACKS_A_PLAYER_ALONE.matchEntire(text)?.let { m ->
            return EventPattern.Attack(Filters.noun(m.groupValues[1].removePrefix("a ")) ?: return null, alone = true, target = RecipientFilter(players = PlayerFilter.Any))
        }
        // fra chunk 05: "you cast an Equipment spell or a spell that targets a creature you control"
        if (text == "you cast an Equipment spell or a spell that targets a creature you control") {
            val creatureYouControl = Filters.noun("creature you control") ?: return null
            return EventPattern.Cast(ObjectFilter.Or(listOf(ObjectFilter.Sub(arcanum.api.Subtype("Equipment")), ObjectFilter.Targets(objects = Objects.Each(creatureYouControl)))), PlayerFilter.You)
        }
        // fra chunk 05: "you cast a spell that targets an opponent or a creature an opponent controls" (CR 115.1)
        if (text == "you cast a spell that targets an opponent or a creature an opponent controls") {
            val theirs = Filters.noun("creature an opponent controls") ?: return null
            return EventPattern.Cast(ObjectFilter.Targets(objects = Objects.Each(theirs), players = arcanum.api.Players.Each(PlayerFilter.Opponent)), PlayerFilter.You)
        }
        return null
    }

    private val OPPONENT_DEALT_NONCOMBAT = Regex("""an opponent (?:was|has been) dealt noncombat damage (this|last) turn""")
    private val DRAWN_N_THIS_TURN = Regex("""you've drawn (\w+) or more cards this turn|you've drawn more than (\w+) cards? this turn""")
    private val LOYALTY_AMONG = Regex("""there are (\S+) or more loyalty counters among (.+)""")
    private val NUMBER_WORDS = mapOf("twenty-five" to 25)

    fun condition(text: String, self: Set<String>): Condition? {
        // fra chunk 04: "you've scried or surveilled this turn"
        if (text == "you've scried or surveilled this turn") return Condition.Or(listOf(
            Condition.HappenedThisTurn(ActionWordings.youDid(KeywordActionKind.Scry), atLeast = 1, measure = Measure.Events),
            Condition.HappenedThisTurn(ActionWordings.youDid(KeywordActionKind.Surveil), atLeast = 1, measure = Measure.Events)))
        // fra chunk 07: "an opponent was dealt noncombat damage this turn", "… last turn"
        OPPONENT_DEALT_NONCOMBAT.matchEntire(text)?.let { m ->
            val damage = EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.Opponent), combat = false)
            return if (m.groupValues[1] == "this") Condition.HappenedThisTurn(damage, atLeast = 1, measure = Measure.Events)
                else Condition.HappenedLastTurn(damage, atLeast = 1, measure = Measure.Events)
        }
        // fra chunk 08: "you've drawn three or more cards this turn"
        DRAWN_N_THIS_TURN.matchEntire(text)?.let { m ->
            // mkm chunk 10: "you've drawn more than one card this turn" (two or more)
            val atLeast = if (m.groupValues[1].isNotEmpty()) Numbers.count(m.groupValues[1]) ?: return null else (Numbers.count(m.groupValues[2]) ?: return null) + 1
            return Condition.HappenedThisTurn(EventPattern.Draw(PlayerFilter.You), atLeast = atLeast, measure = Measure.Events)
        }
        // fra chunk 08: "you didn't cast a spell this turn"
        // one chunk 07: "you haven't cast a spell this turn"
        if (text == "you didn't cast a spell this turn" || text == "you haven't cast a spell this turn")
            return Condition.Not(Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You), atLeast = 1, measure = Measure.Events))
        // fra chunk 08: "you've cast a noncreature spell this turn"
        if (text == "you've cast a noncreature spell this turn")
            return Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.Not(ObjectFilter.Type(CardType.Creature)), PlayerFilter.You), atLeast = 1, measure = Measure.Events)
        // fra chunk 05: "you've activated a loyalty ability this turn"
        if (text == "you've activated a loyalty ability this turn")
            return Condition.HappenedThisTurn(EventPattern.Activate(ObjectFilter.Any, PlayerFilter.You, kind = AbilityKind.Loyalty), atLeast = 1, measure = Measure.Events)
        // fra chunk 09: "there are twenty-five or more loyalty counters among Jaces you control"
        LOYALTY_AMONG.matchEntire(text)?.let { m ->
            val n = NUMBER_WORDS[m.groupValues[1]] ?: Numbers.count(m.groupValues[1]) ?: return null
            val among = Filters.noun(m.groupValues[2]) ?: return null
            return Condition.Compare(Value.Of(NumericProperty.Counters(CounterKind.Loyalty), Objects.Each(among), Combine.Total), Comparison.AtLeast, Value.Fixed(n))
        }
        return null
    }

    fun value(text: String): Value? {
        // fra chunk 03: "the amount of life you gained this turn"
        if (text == "the amount of life you gained this turn") return Value.EventsThisTurn(EventPattern.GainLife(PlayerFilter.You), Measure.Amount)
        // fra chunk 10: "the greatest toughness among creatures you control"
        if (text.startsWith("the greatest toughness among ")) {
            return Value.Of(NumericProperty.Toughness, Filters.objects(text.removePrefix("the greatest toughness among ")) ?: return null, Combine.Greatest)
        }
        // fra chunk 10: "the number of planeswalker types among planeswalkers you control" (CR 205.3j)
        if (text.startsWith("the number of planeswalker types among ")) {
            return Value.CountDistinct(Filters.objects(text.removePrefix("the number of planeswalker types among ")) ?: return null, SharedQuality.PlaneswalkerType)
        }
        // fra chunk 10: "the number of card types among cards in all graveyards"
        if (text == "the number of card types among cards in all graveyards") return Value.CountDistinct(Objects.Each(ObjectFilter.InZone(Zone.Graveyard)), SharedQuality.CardType)
        // fra chunk 10: "the number of basic land types among lands you control" (domain, CR 305.6)
        if (text.startsWith("the number of basic land types among ")) {
            return Value.CountDistinct(Filters.objects(text.removePrefix("the number of basic land types among ")) ?: return null, SharedQuality.BasicLandType)
        }
        return null
    }
}
