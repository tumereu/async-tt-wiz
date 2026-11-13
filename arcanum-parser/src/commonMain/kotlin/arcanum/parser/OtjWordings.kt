package arcanum.parser

/** Wordings first met in Outlaws of Thunder Junction (OTJ). */
internal object Otj {
    // otj chunk 01: "It becomes plotted." (a card in exile, CR 702.170c)
    val BECOMES_PLOTTED = Regex("""(.+?) becomes plotted\.""")
    // otj chunk 01: Make Your Own Luck: the card exiled from among the looked-at ones, then the rest into the hand
    val LOOK_TOP_MAY_EXILE_ONE_IF_YOU_DO_REST_INTO_HAND = Regex(
        """Look at the top (\w+) cards of your library\. You may exile (an? .+? card) from among them\. If you do, (.+?\.) Put the rest into your hand\.""")
    // otj chunk 01: Outlaw Stitcher
    val CREATE_THEN_COUNTERS_ON_TOKEN_FOR_EACH = Regex("""Create (an? .+? token), then put (a|an|one|two|three) (\S+) counters? on that token for each (.+)\.""")
    // otj chunk 01: Pitiless Carnage: an effect, not a cost; "that many" are those sacrificed (CR 701.21a)
    val SACRIFICE_ANY_NUMBER_DRAW_THAT_MANY = Regex("""Sacrifice any number of (.+?), then draw that many cards\.""")
    // otj chunk 01: Sheriff of Safe Passage
    val ENTERS_WITH_COUNTER_PLUS_ADDITIONAL_FOR_EACH = Regex(
        """(.+?) enters with (a|an|one|two) (\S+) counters? on it plus an additional (\S+) counter on it for each (.+)\.""")
    // otj chunk 02: Final Showdown
    val GROUP_LOSES_ALL_ABILITIES_UNTIL_EOT = Regex("""(All|Each) (.+?) loses? all abilities until end of turn\.""")
    // otj chunk 02: Getaway Glamer
    val DESTROY_TARGET_IF_NO_OTHER_GREATER_POWER = Regex("""Destroy target (.+?) if no other (.+?) has greater power\.""")
    // otj chunk 02: Great Train Heist
    val ADDITIONAL_COMBAT_AFTER_THIS_PHASE = Regex("""[Tt]here is an additional combat phase after this phase\.""")
    val WHENEVER_DEALS_COMBAT_DAMAGE_TO_THAT_PLAYER_THIS_TURN = Regex("""Whenever (.+?) deals combat damage to that player this turn, (.+)""")
    // otj chunk 02: Lively Dirge
    val RETURN_UP_TO_TOTAL_MANA_VALUE = Regex("""Return up to (\w+) (.+?) cards with total mana value (\d+) or less from your graveyard to the battlefield\.""")
    // otj chunk 02: One Last Job
    val RETURN_TARGET_ATTACHED_TO_A = Regex("""Return target (.+?) card from your graveyard to the battlefield attached to an? (.+?)\.""")
    // otj chunk 02: Rush of Dread
    val TARGET_PLAYER_HALF = Regex(
        """Target (opponent|player) (sacrifices half the (.+?) they control of their choice|discards half the cards in their hand|loses half their life), rounded (up|down)\.""")
    // chunk 03
    val GAIN_LIFE_FOR_EACH_OF_THOSE = Regex("""You gain (\w+) life for each of those creatures\.""")
    val MAY_RETURN_ANY_NUMBER_TO_OWNERS_HAND = Regex("""(?:Then )?[Yy]ou may return any number of (.+?) to their owners?'s? hands?\.""")
    val AT_END_OF_COMBAT_EXILE_IT_AND_UP_TO_ONE_RETURN = Regex(
        """[Aa]t end of combat, exile it and up to one (.+?), then return those cards to the battlefield under their owners?'s? control\.""")
    val CREATE_TAPPED_ATTACKING_COPY_OF_IT = Regex("""[Cc]reate a tapped and attacking token that's a copy of (?:it|that creature)\.""")
    // otj chunk 04: "exile up to one target black card from your graveyard and copy it. You may cast the copy. If you do, you lose 2 life."
    val AND_COPY_IT = Regex("""(.+?) and copy (it|them)\.""")
    val MAY_CAST_THE_COPY_IF_YOU_DO = Regex("""You may cast the copy\. If you do, (.+)""")
    // otj chunk 04: "Then you may exile a card from a graveyard. If a creature card was exiled this way, you may have Lazav become a copy of that
    // card until end of turn."
    val MAY_EXILE_A_FROM_GRAVEYARD = Regex("""You may exile an? (.+? from (?:a|your) graveyard)\.""")
    val IF_A_WAS_EXILED_THIS_WAY = Regex("""If an? (.+?) was exiled this way, (.+)""")
    val MAY_HAVE_BECOME_COPY_OF_THAT_CARD_UNTIL_EOT = Regex("""You may have (.+?) become a copy of that card until end of turn\.""")
    // otj chunk 05: Tinybones, the Pickpocket
    val MAY_CAST_TARGET_FROM_GRAVEYARD_ANY_TYPE = Regex("""[Yy]ou may cast target (.+?) from (that player's|your|a) graveyard, and mana of any type can be spent to cast that spell\.""")
    // otj chunk 05: Kellan, the Kid: "If you don't, you may put a land card from your hand onto the battlefield." after a free cast
    val IF_YOU_DONT = Regex("""If you don't, (.+)""")
    // otj chunk 05: Prairie Dog: "if you would put one or more +1/+1 counters on a creature you control, put that many plus one +1/+1 counters
    // on it instead" (after "Until end of turn, ")
    val YOU_WOULD_PUT_COUNTERS_PLUS = Regex("""If you would put one or more (\S+) counters on (.+?), put that many plus (\w+) (\S+) counters on it instead\.""")
    // otj chunk 05: Doc Aurlock: "Plotting cards from your hand costs {2} less." (plot's special action, CR 702.170a)
    val PLOTTING_FROM_HAND_COSTS_LESS = Regex("""Plotting cards from your hand costs ((?:\{[^}]+\})+) less\.""")
    val CHOOSE_A_AND = Regex("""[Cc]hoose an? (.+?) and (.+)\.""")
}

/** Phrases first met in Outlaws of Thunder Junction (OTJ). */
internal object OtjPhrases {
    fun condition(text: String): arcanum.api.Condition? {
        // otj chunk 05: "you haven't cast a spell from your hand this turn" (CR 601.2a: where it was cast from)
        if (text == "you haven't cast a spell from your hand this turn") return arcanum.api.Condition.Not(arcanum.api.Condition.HappenedThisTurn(
            arcanum.api.EventPattern.Cast(arcanum.api.ObjectFilter.Any, arcanum.api.PlayerFilter.You, from = arcanum.api.Zone.Hand), atLeast = 1, measure = arcanum.api.Measure.Events))
        // otj chunk 05: "you've committed a crime this turn" (CR 700.13)
        if (text == "you've committed a crime this turn") return arcanum.api.Condition.HappenedThisTurn(
            arcanum.api.EventPattern.CommitCrime(arcanum.api.PlayerFilter.You), atLeast = 1, measure = arcanum.api.Measure.Events)
        // otj chunk 02: "it's your combat phase" (any step of your combat phase, CR 506.1)
        if (text == "it's your combat phase") return arcanum.api.Condition.And(listOf(arcanum.api.Condition.YourTurn,
            arcanum.api.Condition.Or(listOf(arcanum.api.TurnStep.BeginningOfCombat, arcanum.api.TurnStep.DeclareAttackers, arcanum.api.TurnStep.DeclareBlockers,
                arcanum.api.TurnStep.CombatDamage, arcanum.api.TurnStep.EndOfCombat).map { arcanum.api.Condition.StepIs(it) })))
        return null
    }
}
