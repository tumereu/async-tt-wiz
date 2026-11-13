package arcanum.parser

import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.PlayerFilter
import arcanum.api.RecipientFilter
import arcanum.api.Zone

/** Wordings first met in March of the Machine (MOM). */
internal object Mom {
    // mom chunk 06: Doomskar Warrior "look at that many cards from the top of your library. You may reveal a creature or land card from among them…"
    val LOOK_THAT_MANY_MAY_REVEAL_PUT_INTO_HAND_REST_BOTTOM = Regex("""[Ll]ook at that many cards from the top of your library\. You may reveal an? (.+?) card from among them and put it into your hand\. Put the rest on the bottom of your library in a random order\.""")
    // mom chunk 06: Streetwise Negotiator "This creature assigns combat damage equal to its toughness rather than its power."
    val SELF_ASSIGNS_BY_TOUGHNESS = Regex("""(.+) assigns combat damage equal to its toughness rather than its power\.""")
    // mom chunk 01: Blightreaper Thallid "this creature transforms into Blightsower Thallid or dies"
    val TRANSFORMS_INTO_OR_DIES = Regex("""(.+) transforms into (.+) or dies""")
    // mom chunk 03: Elesh Norn "that source's controller loses 2 life" (a damage trigger's source)
    val THAT_SOURCES_CONTROLLER_LOSES = Regex("""[Tt]hat source's controller loses (\d+) life\.""")
    // mom chunk 03: Corruption of Towashi "a permanent you control transforms or a permanent you control enters transformed"
    val TRANSFORMS_OR_ENTERS_TRANSFORMED = Regex("""(.+) transforms or (.+) enters transformed""")
    // mom chunk 01: Zilortha, Apex of Ikoria
    val MAY_ASSIGN_AS_THOUGH_UNBLOCKED = Regex("""(?:For each (.+?), you may have that creature|You may have (.+?)) assign its combat damage as though it weren't blocked\.""")
    // mom chunk 01: Ayara, Furnace Queen
    val RETURN_UP_TO_ONE_HASTE_EXILE_AT_END = Regex("""Return up to one target (.+?) from your graveyard to the battlefield\. It gains haste\. Exile it at the beginning of the next end step\.""")
    // mom chunk 01: Teferi Akosa of Zhalfir
    val SHUFFLE_TARGET_INTO_LIBRARY = Regex("""[Ss]huffle target (.+) into its owner's library\.""")
    // mom chunk 01: Etali, Primal Conqueror
    val EACH_PLAYER_EXILES_UNTIL_CAST_ANY = Regex("""Each player exiles cards from the top of their library until they exile an? ((\w+) card)\. You may cast any number of spells from among the \2 cards exiled this way without paying their mana costs\.""")
    // mom chunk 01: Invasion of Gobakhan
    // mom chunk 02: Awakened Skyclave
    val AS_LONG_AS_ON_BATTLEFIELD_ITS_A = Regex("""As long as (.+?) is on the battlefield, it's an? (\w+) in addition to its other types\.""")
    // mom chunk 02: Invasion of Tarkir
    val REVEAL_ANY_NUMBER_WHEN_YOU_DO_DEALS_X_PLUS = Regex("""Reveal any number of (.+?) cards from your hand\. When you do, (.+?) deals X plus (\d+) damage to (any (?:other )?target), where X is the number of cards revealed this way\.""")
    // mom chunk 02: Rona, Tolarian Obliterator
    val EXILES_AT_RANDOM_LAND_OR_CAST = Regex("""(.+?) exiles a card from their hand at random\. If it's an? (.+?) card, you may put it onto the battlefield under your control\. Otherwise, you may cast it without paying its mana cost\.""")
    // mom chunk 02: Furnace-Blessed Conqueror
    val ATTACKING_COPY_COUNTERS_SACRIFICE_AT_END = Regex("""[Cc]reate a tapped and attacking token that's a copy of (.+?)\. Put a \+1/\+1 counter on that token for each \+1/\+1 counter on (.+?)\. Sacrifice that token at the beginning of the next end step\.""")
    // mom chunk 02: The Great Work
    val UNTIL_EOT_CAST_FROM_ANY_GRAVEYARD = Regex("""Until end of turn, you may cast (.+?) spells from any graveyard\.""")
    // mom chunk 04: Sunder the Gateway "transform an Incubator token you control" (the object of "Transform", not a target)
    val TRANSFORM_A = Regex("""an? (?!target )(.+)""")
    // mom chunk 04: Sunfall "Incubate X, where X is the number of creatures exiled this way." (fra chunk 03: Empower Jace)
    val ACTION_X_EXILED_THIS_WAY = Regex("""(Empower Jace|Incubate) X, where X is the number of creatures exiled this way\.""")
    val LOOK_AT_HAND_EXILE_OWNER_MAY_PLAY = Regex("""Look at target opponent's hand\. You may exile an? (.+? card) from it\. For as long as that card remains exiled, its owner may play it\. A spell cast this way costs ((?:\{[^}]+\})+) more to cast\.""")
    // mom chunk 05: Knight-Errant of Eos (its "where X is" is read first, as the ability's X)
    val LOOK_REVEAL_UP_TO_MV_X_HAND_SHUFFLE = Regex("""Look at the top (\w+) cards of your library\. You may reveal up to (\w+) (.+?) with mana value X or less from among them\. Put the revealed cards into your hand, then shuffle\.""")
    // mom chunk 05: Hoarding Broodlord
    // mom chunk 07: Halo-Charged Skaab "you may put an instant, sorcery, or battle card from your graveyard on top of your library", Unseal the
    // Necropolis "you return up to two creature cards from your graveyard to your hand" (no target: chosen as it resolves)
    val CHOOSE_FROM_YOUR_GRAVEYARD_MOVE = Regex("""(?:You (may )?)?(?:[Rr]eturn|[Pp]ut) (an?|up to \w+) (.+?) from your graveyard (to your hand|on top of your library|to the battlefield(?: tapped)?)\.""")
    // mom chunk 07: Nezumi Freewheeler's back "put target permanent card with mana value 2 or less from a graveyard onto the battlefield under your control"
    val PUT_TARGET_FROM_A_GRAVEYARD_UNDER_YOUR_CONTROL = Regex("""[Pp]ut target (.+?) from a graveyard onto the battlefield( tapped)? under your control\.""")
    // mom chunk 07: Realmbreaker "Target opponent mills three cards. Put a land card from their graveyard onto the battlefield tapped under your control."
    val PUT_A_FROM_THEIR_GRAVEYARD_UNDER_YOUR_CONTROL = Regex("""[Pp]ut an? (.+?) from their graveyard onto the battlefield( tapped)? under your control\.""")
    // mom chunk 07: Wrenn and Realmbreaker's emblem "You may play lands and cast permanent spells from your graveyard."
    val MAY_PLAY_LANDS_AND_CAST_FROM_GRAVEYARD = Regex("""You may play lands and cast (.+?) spells from your graveyard\.""")
    val IT_GAINS_QUOTED = Regex("""It gains "(.+)"\.?""")
    // mom chunk 07: Breach the Multiverse
    val FOR_EACH_PLAYER_CHOOSE_IN_THEIR_GRAVEYARD = Regex("""For each (player|opponent), choose an? (.+?) in that player's graveyard\.""")
    val PUT_THOSE_CARDS_ONTO_BATTLEFIELD_UNDER_YOUR_CONTROL = Regex("""Put those cards onto the battlefield( tapped)? under your control\.""")
    // mom chunk 07: Breach the Multiverse "each creature you control becomes a Phyrexian in addition to its other types" (no duration)
    val EACH_BECOMES_SUBTYPE_IN_ADDITION = Regex("""[Ee]ach (.+?) becomes an? ([A-Z][a-z]+) in addition to its other types\.""")
    // mom chunk 09: Holy Frazzle-Cannon "put a +1/+1 counter on that creature and each other creature you control that shares a creature type with it"
    val COUNTER_ON_THAT_AND_EACH_OTHER_SHARING = Regex("""[Pp]ut an? (\S+) counter on that creature and each other (.+?) that shares an? (creature type|color|card type) with it\.""")
    // mom chunk 09: Sword of Once and Future "you may cast an instant or sorcery spell with mana value 2 or less from your graveyard without paying its mana cost"
    val MAY_CAST_FROM_YOUR_GRAVEYARD_MV_FREE = Regex("""You may cast an? (.+?) spell with mana value (\d+) or less from your graveyard without paying its mana cost\.""")
    // mom chunk 09: Invasion of Regatha "it deals 4 damage to another target battle or opponent and 1 damage to up to one target creature"
    val DEALS_TO_TARGET_OR_PLAYER_AND_UP_TO_ONE = Regex("""(.+?) deals (\d+) damage to (another )?target (\w+) or (opponent|player) and (\d+) damage to up to one target (.+)\.""")
    // mom chunk 09: Kogla and Yidaro "Shuffle this card into your library from your graveyard" (after discarding it as a cost)
    val SHUFFLE_THIS_CARD_FROM_YOUR_GRAVEYARD = Regex("""[Ss]huffle this card into your library from your graveyard\.""")
    val SEARCH_EXILE_FACE_DOWN_MAY_PLAY = Regex("""[Ss]earch your library for an? (.+?), exile it face down, then shuffle\. For as long as that card remains exiled, you may play it\.""")
    // mom chunk 10: Rampaging Geoderm "it's attacking a battle", Portent Tracker "an opponent protects it" (conditions about it)
    val ITS_ATTACKING_A = Regex("""it's attacking an? (.+)""")
    val PROTECTS_IT = Regex("""(an opponent|you) protects? it""")
    // mom chunk 10: Furnace Reins 'Until end of turn, it gains haste and "Whenever this creature deals combat damage …"'
    val UNTIL_EOT_IT_GAINS_AND_QUOTED = Regex("""Until end of turn, (it|that creature) gains ([a-z ,]+?) and "(.+)"\.?""")
    // mom chunk 10: Rankle and Torbran "If a source would deal damage to a player or battle this turn, it deals that much damage plus 2 instead."
    val EXILE_TOP_MAY_CAST_ONE_UNTIL_NEXT_TURN = Regex("""Exile the top (\w+) cards of your library\. Until the end of your next turn, you may cast an? (.+?) spell from among (?:those exiled cards|them|the exiled cards)\.""")
    val CHOOSE_EXILED_WITH_MAY_CAST_FREE_THIS_TURN = Regex("""Choose target (.+?) exiled with (.+?)\. You may cast that card this turn without paying its mana cost\.(?: If that spell would be put into a graveyard, (exile it|put it on the bottom of its owner's library) instead\.)?""")
    val DEALS_TO_EACH_OF_UP_TO_TARGETS = Regex("""(.+?) deals (X|\w+) damage to each of up to (\w+) targets\.""")
    val PUT_ANY_NUMBER_ON_BOTTOM_DRAW_THAT_MANY = Regex("""Put any number of cards from your hand on the bottom of your library, then draw that many cards( plus one)?\.""")
    // mom chunk 12: Surge of Salvation "Prevent all damage that black and/or red sources would deal to creatures you control this turn."
    val PREVENT_ALL_DAMAGE_SOURCES_WOULD_DEAL = Regex("""Prevent all (combat )?damage that (.+?) sources would deal to (.+?) this turn\.""")
    // mom chunk 12: Glistening Deluge "Creatures that are green and/or white get an additional -2/-2 until end of turn." (adds to the earlier change)
    val GET_AN_ADDITIONAL = Regex("""(.+? gets?) an additional ([+-]\d+/[+-]\d+ until end of turn\.)""")
    // mom chunk 12: Borborygmos and Fblthp "draw a card, then you may discard any number of land cards. When you discard one or more cards this way, …
    // deals twice that much damage to target creature."
    val DRAW_MAY_DISCARD_ANY_NUMBER_WHEN_TWICE_DAMAGE = Regex("""Draw a card, then you may discard any number of (.+?) cards\. When you discard one or more cards this way, (.+?) deals twice that much damage to target (.+)\.""")
    // mom chunk 12: Djeru and Hazoret, Nahiri's Warcrafting: look at the top N (or the excess damage) cards, exile one, the rest on the bottom, play it
    val LOOK_MAY_EXILE_ONE_REST_BOTTOM_PLAY = Regex("""(?:(.+?) deals (\d+) damage to target (.+?)\. )?Look at the top (\w+) cards of your library(, where X is the excess damage dealt this way)?\. You may exile (?:one of those cards|an? (.+?) from among them)\. Put the rest on the bottom of your library in a random order\. (You may play the exiled card this turn|Until end of turn, you may cast the exiled card without paying its mana cost)\.""")
    val SOURCE_DAMAGE_PLUS_THIS_TURN = Regex("""If a source would deal damage to (a player or battle|a player|an opponent) this turn, it deals that much damage plus (\d+) instead\.""")
}

/** mom: nouns and conditions of March of the Machine wordings, tried by [Filters] and [Conditions]. */
internal object MomPhrases {
    // mom chunk 01: Invasion of Ravnica "spell that's exactly two colors", "permanent … that isn't exactly two colors" (CR 105.2)
    private val THATS_EXACTLY_COLORS = Regex("""(.+?) that(?:'s| is| (isn't)) exactly (\w+) colors?""")

    // mom chunk 03: The Argent Etchings "other permanents except for artifacts, lands, and Phyrexians"
    private val EXCEPT_FOR = Regex("""(.+?) except for (.+)""")

    // mom chunk 05: Cut Short "planeswalker that was activated this turn" (one of its loyalty abilities was, CR 606.3)
    private val WAS_ACTIVATED_THIS_TURN = Regex("""(.+?) that was activated this turn""")

    // mom chunk 05: "each creature that convoked it", "creatures that convoked this creature" (CR 702.51c)
    private val THAT_CONVOKED = Regex("""(?:each )?creatures? that convoked (?:it|this creature|this spell)""")

    // mom chunk 12: Rampaging Raptor "planeswalker that player controls or battle that player protects" (a battle's protector, CR 310.8)
    private val CONTROLS_OR_PROTECTS = Regex("""(.+?) (that player|an opponent|you) controls? or (.+?) \2 protects?""")
    private val PROTECTS = Regex("""(.+?) (that player|an opponent|you) protects?""")

    // mom chunk 12: Glistening Deluge "creatures that are green and/or white" (CR 105.2)
    private val THAT_ARE_COLORS = Regex("""(.+?) that (?:are|is) ((?:white|blue|black|red|green)(?:(?:,| and/or| or|, or) (?:white|blue|black|red|green))*)""")

    private fun protector(who: String): PlayerFilter? = when (who) {
        "that player" -> PlayerFilter.Is(arcanum.api.Players.EventPlayer)
        "an opponent" -> PlayerFilter.Opponent
        "you" -> PlayerFilter.You
        else -> null
    }

    /** mom chunk 05: objects that aren't a filter: the creatures tapped to pay for this object's convoke (CR 702.51c). */
    fun objects(phrase: String): arcanum.api.Objects? {
        if (THAT_CONVOKED.matches(phrase)) return arcanum.api.Objects.PaidWith(arcanum.api.ManaPaymentWay.TapCreatures, arcanum.api.Objects.Self)
        return null
    }

    fun noun(phrase: String): ObjectFilter? {
        CONTROLS_OR_PROTECTS.matchEntire(phrase)?.let { m ->
            val controlled = Filters.noun("${m.groupValues[1]} ${m.groupValues[2]} controls") ?: return null
            val protected = noun("${m.groupValues[3]} ${m.groupValues[2]} protects") ?: return null
            return ObjectFilter.Or(listOf(controlled, protected))
        }
        PROTECTS.matchEntire(phrase)?.let { m ->
            val who = protector(m.groupValues[2]) ?: return null
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null,
                ObjectFilter.Where(Condition.PlayerMatches(arcanum.api.Players.ProtectorOf(arcanum.api.Objects.Tested), who)))
        }
        THAT_ARE_COLORS.matchEntire(phrase)?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, Filters.noun(m.groupValues[2]) ?: return null)
        }
        WAS_ACTIVATED_THIS_TURN.matchEntire(phrase)?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.ThisTurn(
                EventPattern.Activate(ObjectFilter.Any, PlayerFilter.Any, kind = arcanum.api.AbilityKind.Loyalty), role = arcanum.api.EventRole.Source))
        }
        EXCEPT_FOR.matchEntire(phrase)?.let { m ->
            // "artifacts, lands, and Phyrexians": an object of any of them is excepted
            val excepted = m.groupValues[2].split(Regex(""",? and |, """)).map { Filters.noun(it) ?: return null }
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.Not(excepted.singleOrNull() ?: ObjectFilter.Or(excepted)))
        }
        THATS_EXACTLY_COLORS.matchEntire(phrase)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            val count = ObjectFilter.ColorCount(Numbers.count(m.groupValues[3]) ?: return null)
            return Filters.all(noun, if (m.groupValues[2].isEmpty()) count else ObjectFilter.Not(count))
        }
        return null
    }

    // mom chunk 01: Ashen Reaper "a permanent was put into a graveyard from the battlefield this turn" (any player's, CR 700.4)
    private val PUT_INTO_GRAVEYARD_FROM_BATTLEFIELD_THIS_TURN = Regex("""an? (.+?) was put into a graveyard from the battlefield this turn""")

    private val OPPONENT_HAS_CARDS_IN_GRAVEYARD = Regex("""an opponent has (\w+) or more cards in their graveyard""")
    // mom chunk 11: Sandstalker Moloch "an opponent cast a blue and/or black spell this turn"
    private val PLAYER_CAST_SPELL_THIS_TURN = Regex("""(an opponent|a player|you) cast an? (.+? )?spell this turn""")
    // mom chunk 11: Fairgrounds Trumpeter "a +1/+1 counter was put on a permanent under your control this turn"
    private val COUNTER_WAS_PUT_THIS_TURN = Regex("""an? (\S+) counter was put on an? (.+?) this turn""")
    // mom chunk 10: War Historian "it attacked a battle this turn"
    private val SELF_ATTACKED_A_THIS_TURN = Regex("""(.+?) attacked an? (battle|planeswalker) this turn""")

    /** mom chunk 01: Heliod, the Warped Eclipse "card your opponents have drawn this turn" (one draw is one card, CR 121.2) */
    fun value(text: String): arcanum.api.Value? {
        // mom chunk 01: Teferi Akosa of Zhalfir "the number of creatures tapped this way" (the creatures the ability tapped)
        if (text == "the number of creatures tapped this way") return arcanum.api.Value.Count(arcanum.api.Objects.Bound("tapped"))
        if (text == "card your opponents have drawn this turn" || text == "the number of cards your opponents have drawn this turn")
            return arcanum.api.Value.EventsThisTurn(EventPattern.Draw(PlayerFilter.Opponent), Measure.Events)
        return null
    }

    fun condition(text: String, self: Set<String>): Condition? {
        // mom chunk 10: War Historian "it attacked a battle this turn" (in a static ability, "it" is this object; what it attacked, CR 506.3)
        SELF_ATTACKED_A_THIS_TURN.matchEntire(text)?.takeIf { it.groupValues[1] == "it" || it.groupValues[1] in self }?.let { m ->
            val defender = Filters.noun(m.groupValues[2]) ?: return null
            val selfIs = ObjectFilter.Is(arcanum.api.Objects.Self)
            return Condition.ObjectMatches(arcanum.api.Objects.Self, ObjectFilter.ThisTurn(EventPattern.Attack(selfIs, target = RecipientFilter(objects = defender))))
        }
        // mom chunk 01: Marchesa "you haven't been dealt combat damage since your last turn" (CR 510.2)
        if (text == "you haven't been dealt combat damage since your last turn")
            return Condition.Not(Condition.HappenedSinceYourLastTurn(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.You), combat = true),
                atLeast = 1, measure = Measure.Events))
        // mom chunk 07: Tenured Oilcaster, Sheoldred "an opponent has eight or more cards in their graveyard"
        OPPONENT_HAS_CARDS_IN_GRAVEYARD.matchEntire(text)?.let { m ->
            val graveyard = ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(arcanum.api.Players.Tested))))
            return Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent,
                PlayerFilter.Where(Condition.Exists(graveyard, atLeast = Numbers.count(m.groupValues[1]) ?: return null)))), atLeast = 1)
        }
        // mom chunk 11: a spell of that kind cast by such a player this turn (CR 601.2i)
        PLAYER_CAST_SPELL_THIS_TURN.matchEntire(text)?.let { m ->
            val spell = Filters.noun(m.groupValues[2] + "spell") ?: return null
            val by = when (m.groupValues[1]) { "an opponent" -> PlayerFilter.Opponent; "you" -> PlayerFilter.You; else -> PlayerFilter.Any }
            return Condition.HappenedThisTurn(EventPattern.Cast(spell, by), atLeast = 1, measure = Measure.Events)
        }
        // mom chunk 11: one counter of that kind put on such a permanent this turn (CR 122.6)
        COUNTER_WAS_PUT_THIS_TURN.matchEntire(text)?.let { m ->
            val objects = Filters.noun(m.groupValues[2].replace(" under your control", " you control")) ?: return null
            return Condition.HappenedThisTurn(EventPattern.CountersPut(objects, Counters.kind(m.groupValues[1]) ?: return null), atLeast = 1, measure = Measure.Events)
        }
        PUT_INTO_GRAVEYARD_FROM_BATTLEFIELD_THIS_TURN.matchEntire(text)?.let { m ->
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(Filters.noun(m.groupValues[1]) ?: return null, from = Zone.Battlefield, to = Zone.Graveyard),
                atLeast = 1, measure = Measure.Events)
        }
        return null
    }
}
