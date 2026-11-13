package arcanum.parser

import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Value

/** Wordings first met in Innistrad Remastered (INR). */
internal object Inr {
    // inr chunk 04: Distended Mindbender
    val YOU_CHOOSE_FROM_IT_A_AND_B = Regex("""You choose from it (an? (?:[a-z]+ )?card)( with .+?)? and (an? (?:[a-z]+ )?card)( with .+?)?\.""")
    val LOOK_TOP_X_PUT_N_REST_BOTTOM_RANDOM = Regex("""Look at the top X cards of your library\. Put (\w+) of them into your hand and the rest on the bottom of your library in a random order\.""")
    // inr chunk 01: Conduit of Storms: mana added later this turn (a delayed trigger, CR 603.7b)
    val ADD_AT_NEXT_MAIN_PHASE_THIS_TURN = Regex("""[Aa]dd ((?:\{[WUBRGC]\})+) at the beginning of your next main phase this turn\.""")
    // inr chunk 01: Garruk Relentless: "That creature deals damage equal to its power to him." (the earlier target fights back one way)
    val THAT_CREATURE_DEALS_POWER_TO_SELF = Regex("""That creature deals damage equal to its power to (him|her|it)\.""")
    // inr chunk 01: Garruk, the Veil-Cursed: "Sacrifice a creature. If you do, …" (CR 701.21a)
    val SACRIFICE_A_IF_YOU_DO = Regex("""(Sacrifice an? [^.]+?)\. If you do, (.+)""")
    val NAMES_THE_SACRIFICED = Regex("""\bthat (?:creature|permanent|artifact|land|card)\b|\bthe sacrificed\b""")
    // inr chunk 01: Soulcipher Board: the other card stays on top (CR 401.4)
    val LOOK_TOP_N_PUT_ONE_INTO_GRAVEYARD = Regex("""Look at the top (\w+) cards of your library\. Put one of them into your graveyard\.""")
    // inr chunk 01: Ravager of the Fells: the creature target belongs to the player or the planeswalker's controller (CR 115.1)
    val DEALS_TO_OPPONENT_OR_PW_AND_UP_TO_ONE_CREATURE_THAT_PLAYER_CONTROLS = Regex(
        """(.+?) deals (\d+) damage to target ((?:opponent|player) or planeswalker) and (\d+) damage to up to one target creature that player or that planeswalker's controller controls\.""")
    // inr chunk 07: Geistlight Snare: a second conditional reduction on the same line (CR 601.2f)
    val COSTS_LESS_IF_IT_ALSO_COSTS_LESS_IF = Regex("""(This spell costs (\{\d+\}) less to cast if [^.]+\.) It also costs (\{\d+\}) less to cast if (.+)\.""")
    // inr chunk 07: Emrakul, the Promised End: "for each card type among cards in your graveyard" (CR 205.2a)
    val CARD_TYPE_AMONG = Regex("""card type among (.+)""")
    // inr chunk 07: Summary Dismissal: "Exile all other spells and counter all abilities." (abilities on the Stack, CR 113.1c)
    val AND_COUNTER_ALL_ABILITIES = Regex("""(.+) and counter all abilities\.""")
    // inr chunk 07: Triskaidekaphobia: "Each player with exactly 13 life loses the game" (CR 104.3e)
    val EACH_PLAYER_WITH_LIFE_LOSES = Regex("""Each player with (?:exactly (\d+)|(\d+) or (less|more)) life loses the game\.""")
    // inr chunk 07: Necroduality, Soul Separator: a copy of the object an earlier sentence or the trigger named (CR 707.2, 707.9b)
    val CREATE_COPY_OF_THAT = Regex("""[Cc]reate a token that's a copy of (?:that card|that creature|it)(?:, except (.+))?\.""")
    // inr chunk 07: Soul Separator: "Create a black Zombie creature token with power equal to that card's power and toughness equal to that card's
    // toughness." (values fixed as the token is created, CR 111.4)
    val CREATE_TOKEN_PT_EQUAL_TO_THAT_CARDS = Regex("""[Cc]reate an? (\w+(?: and \w+)? [A-Z][A-Za-z ]+? creature) token with power equal to that (?:card|creature)'s power and toughness equal to that (?:card|creature)'s toughness\.""")
    // inr chunk 07: Cultivator Colossus: "you may put a land card … If you do, draw a card and repeat this process." (CR 608.2c)
    val MAY_PUT_IF_YOU_DO_REPEAT = Regex("""(You may put [^.]+? from your hand onto the battlefield(?: tapped)?)\. If you do, (.+?) and repeat this process\.""")
    // inr chunk 07: Gather the Townsfolk: "create five of those tokens instead" (the same token, another number)
    val CREATE_N_OF_THOSE_TOKENS = Regex("""create (\w+) of those tokens""")
    // inr chunk 07: Altered Ego "except it enters with X additional +1/+1 counters on it" (CR 614.1c)
    val EXCEPT_ENTERS_WITH_ADDITIONAL_COUNTERS = Regex("""it enters with (X|\w+) additional (\S+) counters? on it""")
    // inr chunk 07: Odric, Lunarch Marshal (CR 608.2h)
    val GAIN_IF_ONE_HAS_SAME_FOR = Regex("""(Creatures you control) gain ([a-z ]+) until end of turn if (an? .+?) has \2\. The same is true for (.+)\.""")
    // inr chunk 07: Tamiyo, Field Researcher (CR 603.7c)
    val CHOOSE_TARGETS_UNTIL_NEXT_TURN_WHENEVER_DEALS = Regex("""Choose up to (one|two|three) target (creatures?)\. Until your next turn, whenever (?:either of those creatures|any of those creatures|that creature) deals (combat damage(?: to a player)?), (.+)""")
    // inr chunk 07: Emrakul, the Promised End (CR 723.1, 500.7)
    val CONTROL_NEXT_TURN_THEN_EXTRA_TURN = Regex("""[Yy]ou gain control of target (opponent|player) during that player's next turn\. After that turn, that player takes an extra turn\.""")
    // inr chunk 06: Briseis: "Your opponents can't cast spells with mana value 3 or less." (CR 101.2)
    val OPPONENTS_CANT_CAST_SPELLS_WITH = Regex("""Your opponents can't cast (spells with .+)\.""")
    // inr chunk 06: Grisly Eelfish: "Creatures your opponents control attack this turn if able." (CR 508.1d)
    val CREATURES_ATTACK_THIS_TURN_IF_ABLE = Regex("""(Creatures .+?) attack this turn if able\.""")
    // inr chunk 06: Aberrant Scholar: "Mill a card. If an instant or sorcery card was milled this way, transform this creature." (CR 701.17a)
    val MILL_IF_MILLED_TRANSFORM = Regex("""Mill (an?|\w+) cards?\. If an? (.+?) card was milled this way, transform (?:this creature|it)\.""")
    // inr chunk 08: Geistcatcher's Rig, Angel's Tomb: "you may have it deal 4 damage to …", "you may have this artifact become a 3/3 …" (CR 608.2d)
    val YOU_MAY_HAVE_SELF = Regex("""[Yy]ou may have (it|this [a-z]+|[A-Z][^ ]*(?: [^ ]+)*?) (deal|become) (.+)\.""")
    // inr chunk 08: Vexing Devil: "any opponent may have it deal 4 damage to them." (each in turn order until one does, CR 101.4)
    val ANY_OPPONENT_MAY_HAVE_DEAL_TO_THEM = Regex("""[Aa]ny opponent may have (it|this [a-z]+) deal (\d+) damage to them\.""")
    // inr chunk 08: Moonlight Hunt: "Each creature you control that's a Wolf or a Werewolf deals damage equal to its power to that creature."
    val EACH_DEALS_POWER_TO_THAT = Regex("""Each (.+?) deals damage equal to its power to (that creature|target .+)\.""")
    // inr chunk 08: Tree of Perdition (CR 701.12g)
    val EXCHANGE_LIFE_WITH_SELF_PT = Regex("""Exchange (target opponent's|target player's|your) life total with (.+)'s (power|toughness)\.""")
    // inr chunk 08: Chandra, Dressed to Kill: "If it's red, you may cast it this turn." (the card exiled just before)
    val IF_ITS_COLOR = Regex("""If it's (white|blue|black|red|green), (.+)""")
    // inr chunk 08: Chandra, Dressed to Kill: "You may cast red spells from among them this turn." (CR 601.2a)
    // mh3 chunk 09: any kind of spell ("instant and sorcery spells"), also "without paying their mana costs"
    val MAY_CAST_COLOR_SPELLS_FROM_AMONG_THEM = Regex("""You may cast (.+?) spells from among (?:them|those cards) this turn( without paying their mana costs)?\.""")
    // inr chunk 08: Fiend Hunter: "you may exile another target creature." (CR 115.1)
    val EXILE_ANOTHER_TARGET = Regex("""Exile another target (.+)\.""")
    // inr chunk 08: Compelling Deterrence: "Return target … to its owner's hand. Then that player discards a card if you control a Zombie."
    // inr chunk 09: Killing Wave
    val FOR_EACH_CONTROLLER_SACRIFICES_UNLESS_PAYS_LIFE = Regex("""For each (.+?), its controller sacrifices it unless they pay (X|\d+) life\.""")
    // inr chunk 09: Liliana of the Veil
    val SEPARATE_PERMANENTS_INTO_PILES_SACRIFICE_CHOSEN =
        Regex("""Separate all permanents target (player|opponent) controls into two piles\. That player sacrifices all permanents in the pile of their choice\.""")
    // inr chunk 09: Grimgrin, Corpse-Born
    val ENTERS_TAPPED_AND_DOESNT_UNTAP = Regex("""(.+?) enters tapped and doesn't untap during your untap step\.""")
    // inr chunk 09: Eldritch Evolution "Search your library for …. Put that card onto the battlefield, then shuffle. Exile ~."
    val SEARCH_THEN_PUT_THAT_CARD = Regex("""Search your library for (an? [^.]+?)\. Put that card (onto the battlefield|into your hand), then shuffle\.(?: (.+))?""")
    // inr chunk 09: Sigarda, Host of Herons
    val OPPONENTS_CANT_CAUSE_YOU_TO_SACRIFICE = Regex("""Spells and abilities your opponents control can't cause you to sacrifice permanents\.""")
    // inr chunk 09: Mirrorwing Dragon
    val PLAYER_CASTS_SPELL_THAT_TARGETS_ONLY = Regex("""a player casts an? (.+?) spell that targets only (.+)""")
    val THAT_PLAYER_COPIES_FOR_EACH_COULD_TARGET =
        Regex("""That player copies that spell for each (other )?(.+?) they control that the spell could target\. Each copy targets a different one of those \w+\.""")
    val RETURN_THEN_THAT_PLAYER_DISCARDS_IF = Regex("""Return target (.+?) to its owner's hand\. Then that player discards a card(?: if (.+))?\.""")
}

/** Conditions first met in INR. */
internal object InrPhrases {
    // inr chunk 01: the Innistrad werewolf transform conditions (CR 603.4)
    private val SPELLS_CAST_LAST_TURN = Regex("""(no spells were cast|a player cast (\w+) or more spells) last turn""")
    // inr chunk 01: "each player has 10 or less life"
    private val EACH_PLAYER_HAS_LIFE = Regex("""each player has (\d+) or (less|more) life""")

    // inr chunk 01: "Garruk has two or fewer loyalty counters on him" (this object)
    private val HAS_N_OR_FEWER_COUNTERS_ON = Regex("""(.+) has (\w+) or fewer (\S+) counters on (?:it|him|her)""")

    // inr chunk 08: Boarded Window "if you were dealt 4 or more damage this turn" (all of it, from any sources, CR 120.1)
    private val YOU_WERE_DEALT_DAMAGE_THIS_TURN = Regex("""you were dealt (?:(\d+) or more )?damage this turn""")
    // inr chunk 08: Boarded Window "Creatures attacking you": attacking you, not a planeswalker or battle (CR 506.3, 508.1b)
    private val ATTACKING_YOU = Regex("""(.+?) attacking you""")

    // inr chunk 09: Falkenrath Gorger "Vampire creature card you own that isn't on the battlefield" (in any other zone, CR 400.1)
    private val YOU_OWN_NOT_ON_BATTLEFIELD = Regex("""(.+?) you own that isn't on the battlefield""")
    // inr chunk 09: Edgar Markov's eminence "if Edgar is in the command zone or on the battlefield"
    private val IS_IN_COMMAND_ZONE_OR_ON_BATTLEFIELD = Regex("""(.+) is in the command zone or on the battlefield""")

    fun noun(phrase: String): ObjectFilter? {
        YOU_OWN_NOT_ON_BATTLEFIELD.matchEntire(phrase)?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.OwnedBy(PlayerFilter.You),
                ObjectFilter.Not(ObjectFilter.InZone(arcanum.api.Zone.Battlefield)))
        }
        ATTACKING_YOU.matchEntire(phrase)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(noun, ObjectFilter.HasStatus(arcanum.api.Status.Attacking),
                ObjectFilter.Where(Condition.PlayerMatches(Players.DefendingPlayerOf(Objects.Tested), PlayerFilter.You)),
                ObjectFilter.Where(Condition.Compare(Value.Count(Objects.AttackedBy(Objects.Tested)), Comparison.Equal, Value.Fixed(0))))
        }
        return null
    }

    fun condition(text: String, self: Set<String>): Condition? {
        IS_IN_COMMAND_ZONE_OR_ON_BATTLEFIELD.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.Or(listOf(ObjectFilter.InZone(arcanum.api.Zone.Command),
                ObjectFilter.InZone(arcanum.api.Zone.Battlefield))))
        }
        YOU_WERE_DEALT_DAMAGE_THIS_TURN.matchEntire(text)?.let { m ->
            val n = m.groupValues[1].takeIf { it.isNotEmpty() }?.toInt() ?: 1
            return Condition.HappenedThisTurn(EventPattern.Damage(ObjectFilter.Any, arcanum.api.RecipientFilter(players = PlayerFilter.You)), atLeast = n,
                measure = Measure.Amount)
        }
        // inr chunk 07: Laboratory Maniac "while your library has no cards in it"
        if (text == "your library has no cards in it")
            return Condition.Not(Condition.Exists(Filters.all(ObjectFilter.InZone(arcanum.api.Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 1))
        // inr chunk 07: Lupine Prototype "a player has no cards in hand" (any player, CR 402)
        if (text == "a player has no cards in hand")
            return Condition.PlayerExists(PlayerFilter.Where(Condition.Compare(Value.HandSize(Players.Tested), Comparison.Equal, Value.Fixed(0))), atLeast = 1)
        HAS_N_OR_FEWER_COUNTERS_ON.matchEntire(text)?.takeIf { it.groupValues[1] in self || it.groupValues[1].replaceFirstChar { c -> c.lowercase() } in self }?.let { m ->
            val n = Numbers.count(m.groupValues[2]) ?: return null
            return Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(Counters.kind(m.groupValues[3]) ?: return null, atLeast = n + 1)))
        }
        SPELLS_CAST_LAST_TURN.matchEntire(text)?.let { m ->
            if (m.groupValues[2].isEmpty())
                return Condition.Not(Condition.HappenedLastTurn(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Any), atLeast = 1, measure = Measure.Events))
            // one player cast them all, not the total of all players
            val count = Numbers.count(m.groupValues[2]) ?: return null
            return Condition.PlayerExists(PlayerFilter.Where(Condition.HappenedLastTurn(
                EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Is(Players.Tested)), atLeast = count, measure = Measure.Events)), atLeast = 1)
        }
        EACH_PLAYER_HAS_LIFE.matchEntire(text)?.let { m ->
            // no player has more (or less) than that
            val outside = if (m.groupValues[2] == "less") Comparison.Greater else Comparison.Less
            return Condition.Not(Condition.PlayerExists(PlayerFilter.Life(outside, Value.Fixed(m.groupValues[1].toInt())), atLeast = 1))
        }
        return null
    }
}
