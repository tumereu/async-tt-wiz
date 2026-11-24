package arcanum.parser

import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Zone

/** Regular expressions of Kamigawa: Neon Dynasty (neo) wordings read in [Sentences]. */
internal object Neo {
    // neo chunk 10: Kotose, the Silent Spider
    val EXILE_SEARCH_SAME_NAME_PLAY_ONE = Regex("""[Ee]xile target (.+?) from an opponent's graveyard\. Search that player's graveyard, hand, and library for any number of cards with the same name as that card and exile them\. Then that player shuffles\. For as long as you control (.+?), you may play one of the exiled cards, and you may spend mana as though it were mana of any color to cast it\.""")
    // neo chunk 10: Discover the Impossible
    val LOOK_EXILE_FACE_DOWN_CAST_IF_ELSE_HAND = Regex("""Look at the top (\w+) cards of your library\. Exile one of them face down and put the rest on the bottom of your library in a random order\. You may cast the exiled card without paying its mana cost if it's an? (.+? spell with mana value \d+ or less)\. If you don't, put that card into your hand\.""")
    // neo fixes: Storyweave ("The next time one or more enchantment creatures you control enter this turn, each enters with two additional
    // +1/+1 counters on it.")
    val NEXT_TIME_ONE_OR_MORE_ENTER_EACH_WITH_COUNTERS = Regex("""The next time one or more (.+?) enter(?: the battlefield)? this turn, each enters with (\w+) additional (\S+) counters? on it\.""")
    // neo fixes: The Dragon-Kami Reborn, after "Look at the top three cards of your library."
    val EXILE_ONE_FACE_DOWN_WITH_COUNTER_REST_BOTTOM = Regex("""Exile one of them face down with an? (\S+) counter on it, then put the rest on the bottom of your library in any order\.""")
    // neo chunk 10: "Up to X target creatures phase out."
    val UP_TO_N_TARGET_PHASE_OUT = Regex("""Up to (\w+) target (.+) phase out\.""")
    // neo chunk 10: "For each of them, put your choice of a reach counter, a vigilance counter, or a trample counter on it."
    val FOR_EACH_OF_THEM_CHOICE_OF_COUNTERS = Regex("""For each of them, put your choice of (an? \S+ counter(?:, an? \S+ counter)*,? or an? \S+ counter) on it\.""")
    // neo chunk 10: "Tatsunari and target Frog you control can't be blocked this turn except by creatures with flying or reach."
    val SELF_AND_TARGET_CANT_BE_BLOCKED_EXCEPT_BY = Regex("""(.+?) and target (.+?) can't be blocked this turn except by (creatures with .+)\.""")
    // neo chunk 10: "artifact and enchantment cards" (after "all")
    val TYPE_AND_TYPE_CARDS = Regex("""^([a-z]+) and ([a-z]+) cards$""")
    // neo chunk 10: "Each nontoken creature you control gets +1/+1 for each other creature you control with the same name as that creature."
    val EACH_GETS_FOR_EACH_OTHER_SAME_NAME = Regex("""Each (.+?) gets \+(\d+)/\+(\d+) for each other (.+?) with the same name as that creature\.""")
    // neo chunk 10: "modified creature you controlled as you cast this spell" (after "for each")
    val YOU_CONTROLLED_AS_YOU_CAST = Regex("""(.+?) you controlled as you cast this spell""")
    // neo chunk 10: "The first activated ability of an artifact you activate each turn costs {2} less to activate."
    val FIRST_ABILITY_OF_EACH_TURN_COSTS_LESS = Regex("""The first activated ability of an? (.+?) you activate each turn costs ((?:\{[^}]+\})+) less to activate\.""")
    // neo chunk 04: "Equipped creature has base power and toughness 5/5, has menace, and is a black Demon in addition to its other colors and types."
    val IS_COLOR_SUBTYPES_IN_ADDITION_PREDICATE = Regex("""is an? (white|blue|black|red|green) ((?:[A-Z]\w+ )*[A-Z]\w+) in addition to its other colors and types""")
    // neo chunk 03: "If you cast a spell this way, pay life equal to its mana value rather than paying its mana cost." (after a permission to play cards)
    val PAY_LIFE_RATHER_THAN_MANA_COST = Regex("""If you cast a spell this way, pay life equal to its mana value rather than pay(?:ing)? its mana cost\.""")
    // neo chunk 02: "You may discard up to two cards. If you do, draw that many cards." (the number discarded, CR 608.2c)
    val MAY_DISCARD_UP_TO_IF_YOU_DO_DRAW_THAT_MANY = Regex("""You may discard up to (\w+) cards\. If you do, draw that many cards\.""")
    // neo chunk 02: "your opponents can't cast spells with the same mana value as that spell until your next turn."
    val OPPONENTS_CANT_CAST_SAME_MANA_VALUE_UNTIL_NEXT_TURN = Regex("""[Yy]our opponents can't cast (.+?) with the same mana value as that spell until your next turn\.""")
    // neo chunk 02: "Each player gains control of all permanents they own." (CR 613.1b)
    val EACH_PLAYER_GAINS_CONTROL_OF_ALL_THEY_OWN = Regex("""Each player gains control of all (.+?) they own\.""")
    // neo chunk 02: "defending player chooses a nonland card in your graveyard. Return that card to your hand. This creature gets +X/+0 until end of
    // turn, where X is the mana value of that card."
    val DEFENDING_CHOOSES_IN_YOUR_GRAVEYARD_RETURN_GETS_X = Regex("""Defending player chooses an? (.+?) in your graveyard\. Return that card to your hand\. (.+?) gets \+X/\+0 until end of turn(?:, where X is the mana value of that card)?\.""")
    // neo chunk 05: "Create a Treasure token and a 1/1 colorless Pilot creature token with "…"" (two token kinds, the last with a quoted ability)
    // neo chunk 05: Mechtitan Core
    val EXILE_SELF_AND_OTHERS_YOU_CONTROL = Regex("""Exile (.+?) and (\w+) other (.+?) you control""")
    val CREATE_NAMED_LEGENDARY_ALL_COLORS = Regex("""Create ([A-Z][\w' -]*?), a legendary (\d+)/(\d+) ([A-Z][A-Za-z ]+?) creature token(?: with (.+?))? that's all colors\.""")
    val CREATE_WHEN_THAT_TOKEN_LEAVES = Regex("""(Create [^.]+? token[^.]*?\.) When that token leaves the battlefield, (.+)""")
    val RETURN_ALL_EXILED_WITH_EXCEPT_TAPPED_UNDER_OWNERS = Regex("""Return all cards exiled with (.+?) except (.+?) to the battlefield tapped under their owners' control\.""")
    val UNTIL_EOT_BECOMES_COPY_OF_TARGET_EXCEPT = Regex("""[Uu]ntil end of turn, (.+?) becomes a copy of target (.+?), except (.+)\.""")
    val CREATE_TOKEN_AND_TOKEN = Regex("""Create (an? [^"]+? tokens?) and (an? .+ tokens? with ".+"\.?)""")
    // neo chunk 06: "Enchanted land is a 3/3 red Spirit creature with haste. It's still a land." (for as long as it's attached)
    val ATTACHED_IS_CREATURE = Regex("""((?:Enchanted|Equipped) (?:creature|land|permanent|artifact)) is (an? [^.]+? creature(?: with [^.]+)?)\.(?: It's still an? [a-z]+\.)?""")
    // neo chunk 06: "If enchanted land would be destroyed, instead sacrifice this Aura and that land gains indestructible until end of turn."
    val AND_THAT_NOUN = Regex(""" and that (?:creature|land|permanent|artifact) """)
    // neo chunk 07: "As long as a card exiled with Eater of Virtue has flying, equipped creature has flying. The same is true for first strike, …"
    val AS_LONG_AS_EXILED_WITH_HAS_SAME_FOR = Regex("""As long as an? (.+?) exiled with (.+?) has ([a-z ]+), (.+?) has \3\. The same is true for (.+)\.""")
    // neo chunk 07: "Return any number of target nonland permanents with total mana value 6 or less to their owners' hands."
    val TARGETS_WITH_TOTAL_AT_MOST = Regex("""(.+?\btarget [^.]+?) with total (mana value|power) (\d+) or less( [^.]+)?\.""")
    // neo chunk 07: "If it has mana value less than the sacrificed permanent's mana value, scry 2."
    val IT_HAS_NUMBER_THAN = Regex("""it has (mana value|power|toughness) (less|greater) than (.+)""")
    // neo chunk 07: "the sacrificed permanent's mana value"
    val SACRIFICED_NUMBER = Regex("""the sacrificed (?:creature|permanent|artifact)'s (mana value|power|toughness)""")
    // neo chunk 07: "When you exile a nonland card this way, Hidetsugu deals damage equal to the exiled card's mana value to any target."
    val WHEN_YOU_EXILE_THIS_WAY = Regex("""When you exile an? (.+?) this way, (.+)""")
    // neo chunk 07: "Equipped creature gets +X/+0 until end of turn, where X is …" (the X cut off before)
    val ATTACHED_GETS_UNTIL_EOT = Regex("""((?:Enchanted|Equipped) creature) gets ([+-](?:\d+|X))/([+-](?:\d+|X)) until end of turn\.""")
    val IF_WOULD_BE_DESTROYED_INSTEAD = Regex("""If ((?:enchanted|equipped) (?:creature|land|permanent|artifact)) would be destroyed, instead (.+)\.""")
    // neo chunk 08
    val COSTS_LESS_IF_AND_LESS_IF = Regex("""This spell costs (\{\d+\}) less to cast if (.+?) and (\{\d+\}) less to cast if (.+)\.""")
    val ITS_A_SUBTYPES_WITH_BASE_PT = Regex("""It's an? ((?:[A-Z]\w+ )*[A-Z]\w+) with base power and toughness (\d+)/(\d+)(?: and (.+))?\.""")
    val AS_LONG_AS_ENTERED_THIS_TURN_LOYALTY_INSTANT = Regex("""As long as (.+?) entered this turn, you may activate (?:her|his|its|their) loyalty abilities any time you could cast an instant\.""")
    val AS_LONG_AS_HAS_QUOTED = Regex("""As long as (.+?), (it|.+?) has "(.+)"\.?""")
    val SACRIFICES_IF_THEY_CANT_REPEAT_FOR = Regex("""(Target opponent|Target player|Each opponent|Each player) sacrifices (an? \w+) of their choice\. If they can't, (.+?)\. Then repeat this process for (an? \w+(?:, an? \w+)*,? and an? \w+)\.""")
    val CAST_UP_TO_TOTAL_MV_FROM_ZONES_FREE = Regex("""You may cast up to (\w+) (.+?) spells with total mana value (\d+) or less from your (graveyard|hand)(?: and/or (graveyard|hand))? without paying their mana costs\.""")
    val SPELLS_COST_FOR_EACH_TARGET = Regex("""Spells (you|your opponents) cast cost (\{\d+\}) (less|more) to cast for each target\.""")
    val WITH_MANA_VALUE_AT_MOST_THAT = Regex("""(.+?) with mana value less than or equal to that (?:[A-Z]\w+|creature|artifact|enchantment|permanent|card)(?: and (with .+))?""")
    // neo chunk 09: "Whenever a Vehicle you control attacks" (the subtype a later "that Vehicle" names)
    val WHENEVER_A_SUBTYPE = Regex("""^(?:(?:When|Whenever) )?an? ([A-Z][a-z]+) """)
    // neo chunk 09: "If this spell's additional cost was paid, Voltage Surge deals 4 damage instead."
    val IF_DEALS_AMOUNT_INSTEAD = Regex("""If (.+?), (.+?) deals (.+?) damage instead\.""")
    // neo chunk 09: "X plus 2", "X minus one"
    val PLUS_OR_MINUS = Regex("""(.+) (plus|minus) (\w+)""")
    // neo chunk 09: "X minus one 2/2 white Samurai creature tokens with vigilance" (a count that isn't a plain number)
    val COUNTED_TOKENS = Regex("""(X(?: (?:plus|minus) \w+)?) (.+ tokens(?: with .+)?)""")
    // neo chunk 09: "Seismic Wave deals 2 damage to any target and 1 damage to each nonartifact creature target opponent controls."
    val DEALS_N_TO_AND_M_TO = Regex("""(.+?) deals (\w+) damage to (.+?) and (\w+) damage to (.+)\.""")
    // neo chunk 09: "It deals damage equal to … to target player and that much damage to up to one target creature."
    val DEALS_EQUAL_TO_AND_THAT_MUCH_TO = Regex("""(.+? deals damage equal to .+? to .+?) and that much damage to (.+)\.""")
    // neo chunk 09: "distribute four +1/+1 counters among any number of creatures and/or Vehicles target player controls"
    val DISTRIBUTE_AMONG_TARGET_PLAYER_CONTROLS = Regex("""[Dd]istribute (\w+) (\S+) counters among any number of (.+?) target (player|opponent) controls\.""")
    // neo chunk 09: "for each kind of counter on target permanent, put another counter of that kind on it or remove one from it."
    val EACH_KIND_PUT_OR_REMOVE = Regex("""[Ff]or each kind of counter on target (.+?), put another counter of that kind on it or remove one from it\.""")
    // neo chunk 09: "permanent that shares a card type with the chosen card"
    val SHARES_CARD_TYPE_WITH_CHOSEN = Regex("""(.+?) that shares a card type with the chosen card""")
    val NEXT_SPELL_COSTS_LESS = Regex("""[Tt]he next (?:(.+?) )?spell you cast this turn costs ((?:\{[^}]+\})+) less to cast\.""")
}

/** neo: nouns and conditions of Kamigawa: Neon Dynasty wordings, tried by [Filters] and [Conditions]. */
internal object NeoPhrases {
    private val CARD_OTHER_THAN_A = Regex("""(cards?) other than an? (.+ card)""")
    // neo chunk 02: "creature dealt damage this turn by a source you controlled" (the creature was dealt it, CR 120.1; the source's controller then)
    private val DEALT_DAMAGE_THIS_TURN_BY = Regex("""(.+?) dealt damage this turn by an? (.+?) you controlled""")
    // neo chunk 02: "creature cards in defending player's graveyard" (in an attack trigger, CR 506.2)
    private val IN_DEFENDING_PLAYERS_GRAVEYARD = Regex("""(.+?) in defending player's graveyard""")
    // neo chunk 02: "it has dealt 10 or more damage to that player this turn" (this object's damage to the trigger's player, added up)
    private val IT_HAS_DEALT_DAMAGE_TO_THAT_PLAYER = Regex("""it has dealt (\w+) or more damage to that player this turn""")

    private val SUBTYPE_AND_SUBTYPE = Regex("""([A-Z][\w-]*) and ([A-Z][\w-]*) ((?:creatures|permanents)\b.*)""")
    private val SELF_IS_MODIFIED = Regex("""(.+?) is modified""")
    private val SELF_IS_ATTACHED_TO_A_CREATURE = Regex("""(.+?) is attached to a creature""")
    // neo chunk 06: "creature with the greatest power among creatures on the battlefield" (ties count, CR 208.1)
    private val WITH_THE_GREATEST_AMONG = Regex("""(.+?) with the greatest (power|toughness|mana value) among (.+)""")
    private val CONTROL_A_AND_A = Regex("""you control an? ([a-z]+) and an? ([a-z]+)""")
    private val WITHOUT_A_COUNTER = Regex("""(.+?) that (?:doesn't|don't) have an? (\S+) counters? on (?:it|them)""")

    fun noun(phrase: String): ObjectFilter? {
        // neo chunk 10: "card other than a basic land card" (any card that isn't one)
        CARD_OTHER_THAN_A.matchEntire(phrase)?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.Not(Filters.noun(m.groupValues[2]) ?: return null))
        }
        // neo chunk 03: "Ninja and Rogue creatures you control": creatures of either subtype, as "Ninja or Rogue creatures" (CR 205.3)
        SUBTYPE_AND_SUBTYPE.matchEntire(phrase)?.takeIf { it.groupValues[1] in RulesWords.subtypes && it.groupValues[2] in RulesWords.subtypes }
            ?.let { m -> return Filters.noun("${m.groupValues[1]} or ${m.groupValues[2]} ${m.groupValues[3]}") }
        // neo chunk 03: "creature you control that doesn't have a +1/+1 counter on it" (CR 122.1)
        WITHOUT_A_COUNTER.matchEntire(phrase)?.let { m ->
            val kind = Counters.kind(m.groupValues[2]) ?: return null
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.Not(ObjectFilter.HasCounter(kind, atLeast = 1)))
        }
        // (only "among ... on the battlefield": "among creatures they control" depends on the sentence's player, and a sentence rule that cuts
        // "they control" off reads the rest itself)
        WITH_THE_GREATEST_AMONG.matchEntire(phrase)?.takeIf { it.groupValues[3].endsWith(" on the battlefield") }?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            val among = Filters.objects(m.groupValues[3]) ?: return null
            fun greatest(p: arcanum.api.NumericProperty) = arcanum.api.Value.Of(p, among, arcanum.api.Combine.Greatest)
            val at = arcanum.api.Comparison.AtLeast
            return Filters.all(noun, when (m.groupValues[2]) {
                "power" -> ObjectFilter.Power(at, greatest(arcanum.api.NumericProperty.Power))
                "toughness" -> ObjectFilter.Toughness(at, greatest(arcanum.api.NumericProperty.Toughness))
                else -> ObjectFilter.ManaValue(at, greatest(arcanum.api.NumericProperty.ManaValue))
            })
        }
        DEALT_DAMAGE_THIS_TURN_BY.matchEntire(phrase)?.let { m ->
            val dealtTo = Filters.noun(m.groupValues[1]) ?: return null
            val by = if (m.groupValues[2] == "source") ObjectFilter.ControlledBy(PlayerFilter.You) else Filters.noun("${m.groupValues[2]} you control") ?: return null
            return Filters.all(dealtTo, ObjectFilter.ThisTurn(EventPattern.Damage(by, RecipientFilter()), role = EventRole.Object))
        }
        IN_DEFENDING_PLAYERS_GRAVEYARD.matchEntire(phrase)?.let { m ->
            val cards = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(cards, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))))
        }
        return null
    }

    fun condition(text: String, self: Set<String>): Condition? {
        // neo chunk 08: "you control an artifact and an enchantment" (one of each, both at once; an artifact enchantment is both, CR 205.2b)
        CONTROL_A_AND_A.matchEntire(text)?.let { m ->
            val first = Filters.noun(m.groupValues[1] + " you control") ?: return@let
            val second = Filters.noun(m.groupValues[2] + " you control") ?: return@let
            return Condition.And(listOf(Condition.Exists(first, atLeast = 1), Condition.Exists(second, atLeast = 1)))
        }
        // neo chunk 04: "The Reality Chip is attached to a creature" (an Equipment with reconfigure, CR 702.151b; false while unattached)
        SELF_IS_ATTACHED_TO_A_CREATURE.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.AttachedTo(of = Objects.Self), ObjectFilter.Type(arcanum.api.CardType.Creature))
        }
        // neo chunk 08: "this creature is modified" (it has an Equipment or an Aura its controller controls attached, or a counter, CR 700.9)
        SELF_IS_MODIFIED.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.Modified)
        }
        IT_HAS_DEALT_DAMAGE_TO_THAT_PLAYER.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: m.groupValues[1].toIntOrNull() ?: return null
            return Condition.HappenedThisTurn(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Is(Players.EventPlayer))),
                atLeast = n, measure = Measure.Amount)
        }
        return null
    }
}
