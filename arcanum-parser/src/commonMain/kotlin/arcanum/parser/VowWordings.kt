package arcanum.parser

/** Regular expressions of Innistrad: Crimson Vow (vow) wordings read in [Sentences]. */
internal object Vow {
    // vow chunk 01: "Then if that creature has toughness 6 or greater, …" (the creature named before, as the ability resolves)
    val IT_HAS_STAT = Regex("""(?:that creature|it) has (power|toughness) (\d+) or (greater|less)""")
    // vow chunk 01: "When you play a card exiled with this creature" (a trigger)
    val YOU_PLAY_A_CARD_EXILED_WITH = Regex("""you play a card exiled with (.+)""")
    // vow chunk 01: Hauken's Insight
    val ONCE_PLAY_FROM_AMONG_EXILED_WITH_FREE = Regex("""Once during each of your turns, you may play a land or cast a spell from among the cards exiled with (.+?) without paying its mana cost\.""")
    // vow chunk 02: Volatile Arsonist
    val DEALS_TO_EACH_OF_UP_TO_ONE_CREATURE_PLAYER_PLANESWALKER = Regex("""(.+?) deals (\d+) damage to each of up to one target creature, up to one target player, and/or up to one target planeswalker\.""")
    // vow chunk 03: "this creature enters or becomes the target of an Aura spell" (a trigger of two events)
    val ENTERS_OR = Regex("""(.+?) enters or (.+)""")
    // vow chunk 03: "as long as it's attacking alone" after "This creature …" or "Enchanted creature …" (the subject)
    val ITS_AT_START = Regex("""it(?:'s| is) (.+)""")
    val SUBJECT_AT_START = Regex("""^(This creature|Enchanted creature|Equipped creature) """)
    // vow chunk 04: cleave (CR 702.148a): words in square brackets, with the space before them
    val CLEAVE_BRACKET = Regex("""( ?)\[([^\]]+)\]""")
    // vow chunk 04: "During that turn, damage can't be prevented." after taking an extra turn
    val DURING_THAT_TURN = Regex("""During that turn, (.+)\.""")
    // vow chunk 04: "At the beginning of that turn's end step, you lose the game." after taking an extra turn
    val AT_THAT_TURNS_STEP = Regex("""At the beginning of that turn's (upkeep|draw step|end step), (.+)""")
    // vow chunk 04: "X is the number of creatures you control." after the sentences that use X
    val X_IS = Regex("""X is (.+)\.""")
    // vow chunk 04: "Your maximum hand size is reduced by three for the rest of the game."
    val MAXIMUM_HAND_SIZE_CHANGED_REST_OF_GAME = Regex("""Your maximum hand size is (increased|reduced) by (\w+) for the rest of the game\.""")
    // vow chunk 05: an Aura's "When enchanted creature dies, return this card to the battlefield transformed under your control attached to target opponent."
    val RETURN_THIS_CARD_TO_BATTLEFIELD = Regex("""[Rr]eturn this card to the battlefield( transformed)?(?: under (your|its owner's) control)?(?: attached to target (player|opponent))?\.""")
    // vow chunk 06: Grolnok, the Omnivore
    val MAY_PLAY_OWNED_IN_EXILE_WITH_COUNTERS = Regex("""You may play lands and cast spells from among cards you own in exile with (\S+) counters on them\.""")
    // vow chunk 06: Old Rutstein: "If a land card is milled this way, create a Treasure token." after a mill
    val IF_CARD_MILLED_THIS_WAY = Regex("""If an? (.+? card) (?:is|was) milled this way, (.+)""")
    // vow chunk 06: Bride's Gown: "an Equipment named Groom's Finery is attached to a creature you control" (a condition)
    val NAMED_IS_ATTACHED_TO = Regex("""an? (\S+) named (.+?) is attached to an? (.+)""")
    // vow chunk 06: Bride's Gown: "Equipped creature gets +2/+0. It gets an additional +0/+2 and has first strike as long as …"
    val ATTACHED_STATIC_THEN_IT = Regex("""(Equipped creature|Enchanted creature) ([^.]+)\. It ([^.]+\.)""")
    // vow chunk 06: Mindleech Ghoul
    val EACH_OPPONENT_EXILES_FROM_HAND = Regex("""[Ee]ach opponent exiles a card from their hand\.""")
    // vow chunk 06: Screaming Swarm
    val PUT_SELF_FROM_GRAVEYARD_FROM_TOP = Regex("""[Pp]ut this card from your graveyard into your library (\w+) from the top\.""")
    // vow chunk 07: Savior of Ollenbock: "this creature trains" (a trigger, CR 702.149c)
    val TRAINS = Regex("""(.+?) trains""")
    // vow chunk 07: Magma Pummeler
    val PREVENT_REMOVE_THAT_MANY_COUNTERS = Regex("""If damage would be dealt to (.+?) while it has an? (\S+) counter on it, prevent that damage and remove that many (\S+) counters from it\. When one or more counters are removed from (.+?) this way, (it|.+?) deals that much damage to any target\.""")
    // vow chunk 07: Skulking Killer: "… if that opponent controls no other creatures" (the target's controller)
    val IF_THAT_PLAYER_CONTROLS_NO_OTHER = Regex("""(.+) if that (?:opponent|player) controls no other (.+?)\.""")
    // vow chunk 07: Arm the Cathars: "Until end of turn, A gets +3/+3, B gets +2/+2, and C gets +1/+1."
    val UNTIL_EOT_LIST_OF_GETS = Regex("""Until end of turn, ((?:[^,]+ gets [+-]\d+/[+-]\d+, )+and [^,]+ gets [+-]\d+/[+-]\d+)\.""")
    // vow chunk 08: Into the Night: "It becomes night." (CR 732.1)
    val IT_BECOMES_DAY_OR_NIGHT = Regex("""It becomes (day|night)\.""")
    // vow chunk 08: By Invitation Only: "Choose a number between 0 and 13." ("that many" after it is the number)
    val CHOOSE_A_NUMBER_BETWEEN = Regex("""Choose a number between (\d+) and (\d+)\.""")
    val EACH_SACRIFICES_THAT_MANY = Regex("""Each (player|opponent) sacrifices that many (.+?) of their choice\.""")
    // vow chunk 08: Consuming Tide: "Each player chooses a nonland permanent they control." then "Return all nonland permanents not chosen this way to
    // their owners' hands."
    val EACH_CHOOSES_ONE_THEY_CONTROL = Regex("""Each (player|opponent) chooses an? (.+?) they control\.""")
    val RETURN_ALL_NOT_CHOSEN_TO_HANDS = Regex("""Return all (.+?) not chosen this way to their owners' hands\.""")
    val OPPONENTS_WITH_MORE_CARDS_IN_HAND = Regex("""the number of opponents? who (?:has|have) more cards in (?:their|his or her) hand than you""")
    // vow chunk 08: Investigator's Journal: "the greatest number of creatures a player controls"
    val GREATEST_NUMBER_A_PLAYER_CONTROLS = Regex("""the greatest number of (.+?) a player controls""")
    // vow chunk 08: Odric, Blood-Cursed: "the number of abilities from among flying, first strike, …, and vigilance found among creatures you control"
    val ABILITIES_FROM_AMONG_FOUND_AMONG = Regex("""the number of abilities from among (.+?) found among (.+)""")
    // vow chunk 08: Kaya, Geist Hunter; Lantern of the Lost
    val EXILE_ALL_CARDS_FROM_ALL_GRAVEYARDS = Regex("""[Ee]xile all cards from all graveyards\.""")
    // vow chunk 08: Cemetery Desecrator: "exile another card from a graveyard" (chosen as it resolves, not targeted)
    val EXILE_A_CARD_FROM_A_GRAVEYARD = Regex("""[Ee]xile (an?|another) (.+?) from a graveyard\.""")
    // vow chunk 08: Sigarda's Summons
    val HAVE_BASE_PT_HAVE_AND_ARE_IN_ADDITION = Regex("""(.+?) have base power and toughness (\d+)/(\d+), have ([a-z ,]+), and are (\w+?)s in addition to their other (?:creature )?types\.""")
    // vow chunk 08: Cemetery Prowler
    val CARD_TYPE_THEY_SHARE_WITH_EXILED_WITH = Regex("""card type (?:they share|it shares) with cards exiled with (.+)""")
    // vow chunk 08: Dollhouse of Horrors: "Create a token that's a copy of the exiled card, except …" after a cost that exiles one card
    val CREATE_COPY_OF_THE_EXILED_CARD = Regex("""Create a token that's a copy of the exiled card(?:, except (.+))?\.""")
    // vow chunk 08: "it's a 0/0 Construct artifact in addition to its other types" (a copy exception: power and toughness, subtypes and card types)
    val COPY_EXCEPT_PT_SUBTYPE_TYPE_IN_ADDITION = Regex("""it's an? (\d+)/(\d+) ([A-Z]\w+(?: [A-Z]\w+)*) ([a-z]+(?: [a-z]+)*) in addition to its other types""")
    // vow chunk 08: 'it has "This token gets +1/+1 for each Construct you control."' (a copy exception: a quoted ability)
    val COPY_EXCEPT_HAS_QUOTED = Regex("""(?:it )?has "(.+)"""")
    // vow chunk 08: Cemetery Desecrator: "exile another card from a graveyard. When you do, choose one —" (after the trigger's comma)
    val EFFECT_WHEN_YOU_DO_CHOOSE = Regex("""([^.]+\.) When you do, (choose .+)""")
    // vow chunk 08: Cemetery Desecrator: "Remove X counters from target permanent, where X is …" (any kinds, the remover's choice)
    val REMOVE_N_COUNTERS_FROM_TARGET = Regex("""Remove (X|\w+) counters from target (.+)\.""")
    // vow chunk 08: Creepy Puppeteer
    val ATTACKS_IF_EXACTLY_ONE_OTHER_BASE_PT = Regex("""Whenever (.+?) attacks, if you attacked with exactly one other creature this combat, you may have that creature's base power and toughness become (\d+)/(\d+) until end of turn\.""")
}

/** Numbers first met in Innistrad: Crimson Vow (vow), read through [Values.value]. */
internal object VowPhrases {
    fun value(text: String): arcanum.api.Value? {
        // vow chunk 08: "the number of opponents who have more cards in their hand than you" ("for each opponent who has …", CR 402.1)
        if (Vow.OPPONENTS_WITH_MORE_CARDS_IN_HAND.matches(text)) {
            return arcanum.api.Value.CountPlayers(arcanum.api.Players.Each(arcanum.api.PlayerFilter.And(listOf(arcanum.api.PlayerFilter.Opponent,
                arcanum.api.PlayerFilter.Where(arcanum.api.Condition.Compare(arcanum.api.Value.HandSize(arcanum.api.Players.Tested), arcanum.api.Comparison.Greater,
                    arcanum.api.Value.HandSize(arcanum.api.Players.You)))))))
        }
        // vow chunk 08: "the greatest number of creatures a player controls" (each player's count, the greatest of them)
        Vow.GREATEST_NUMBER_A_PLAYER_CONTROLS.matchEntire(text)?.let { m ->
            val noun = Filters.noun(m.groupValues[1].let { if (it.endsWith("s")) it.dropLast(1) else it }) ?: return null
            return arcanum.api.Value.GreatestFor(arcanum.api.Players.Each(arcanum.api.PlayerFilter.Any), arcanum.api.Value.Count(arcanum.api.Objects.Each(
                Filters.all(noun, arcanum.api.ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.Is(arcanum.api.Players.Tested))))))
        }
        // vow chunk 08: "the number of abilities from among flying, first strike, … found among creatures you control": each listed keyword
        // counts once if any of those creatures has it (Odric, Blood-Cursed's reminder: "Count abilities, not creatures.")
        Vow.ABILITIES_FROM_AMONG_FOUND_AMONG.matchEntire(text)?.let { m ->
            val among = Filters.noun(m.groupValues[2].let { if (it.startsWith("creatures ")) "creature " + it.removePrefix("creatures ") else it }) ?: return null
            val kinds = m.groupValues[1].split(LazyRegexes.AND_LIST).map { (OracleParser.keyword(it.trim()) ?: return null).kind }
            return arcanum.api.Value.Sum(kinds.map { kind ->
                arcanum.api.Value.If(arcanum.api.Condition.Exists(Filters.all(among, arcanum.api.ObjectFilter.HasKeyword(kind)), atLeast = 1),
                    arcanum.api.Value.Fixed(1), arcanum.api.Value.Fixed(0))
            })
        }
        return null
    }
}
