package arcanum.parser

/** Regular expressions of Star Trek (trk) wordings read in [Sentences]. */
internal object Trk {
    // trk chunk 02: "Gain control of target nonland permanent an opponent controls. That opponent draws three cards." (the target's controller)
    val GAIN_CONTROL_THAT_OPPONENT_DRAWS = Regex("""Gain control of target (.+? an opponent controls)\. That opponent draws (\w+) cards?\.""")
    // trk chunk 02: "Choose X target creatures your opponents control. For each of them, create a token that's a copy of that creature, except it
    // has changeling. Those tokens gain haste. Exile those tokens at the beginning of the next end step."
    val CHOOSE_X_COPY_EACH_GAIN_EXILE = Regex("""Choose X target (.+?)\. For each of them, create a token that's a copy of that (\w+)(?:, except (it has .+?))?\. Those tokens gain (\w+)\. Exile those tokens at the beginning of the next end step\.""")
    // trk chunk 02: "For as long as Lwaxana Troi remains tapped, if a triggered ability of that creature triggers, that ability triggers an
    // additional time."
    val FOR_AS_LONG_AS_TAPPED_TRIGGERS_ADDITIONAL = Regex("""For as long as (.+?) remains tapped, if a triggered ability of that creature triggers, that ability triggers an additional time\.""")
    // trk chunk 03: "Put all permanent cards exiled this way onto the battlefield." after an exile
    val PUT_ALL_EXILED_THIS_WAY_ONTO_BATTLEFIELD = Regex("""Put all (.+? cards?) exiled this way onto the battlefield\.""")
    // trk chunk 04: "Reveal the top X cards of your library, where X is …. Put all permanent cards revealed this way onto the battlefield tapped and the
    // rest on the bottom of your library in a random order."
    val REVEAL_PUT_ALL_TAPPED_REST_BOTTOM = Regex("""Reveal the top X cards of your library\. Put all (.+? cards?) revealed this way onto the battlefield( tapped)? and the rest on the bottom of your library in a random order\.""")
    // trk chunk 04: "Each opponent exiles the top X cards of their library, where X is …. For each opponent, choose a land card that player owns exiled
    // this way. You may play the chosen cards for as long as they remain exiled."
    val EACH_OPPONENT_EXILES_CHOOSE_ONE_EACH_MAY_PLAY = Regex("""[Ee]ach opponent exiles the top X cards of their library\. For each opponent, choose an? (.+? card) that player owns exiled this way\. You may play the chosen cards for as long as they remain exiled\.""")
    // trk chunk 04: "This creature can't be the target of abilities from artifact sources your opponents control."
    val CANT_BE_TARGET_OF_ABILITIES_FROM = Regex("""(.+?) can't be the target of abilities from (.+?) sources your opponents control\.""")
    // trk chunk 06: "assimilate target creature card from a graveyard", "assimilate a creature card exiled with it" (CR 701.72b)
    val ASSIMILATE = Regex("""[Aa]ssimilate (.+? cards?(?: from .+?| exiled with .+?)?)\.""")
    // trk chunk 06: "target opponent exiles cards from the top of their library until they exile a nonland card."
    val TARGET_PLAYER_EXILES_UNTIL = Regex("""[Tt]arget (opponent|player) exiles cards from the top of their library until they exile (an? .+)\.""")
    // trk chunk 06: "target opponent may have you draw a card." (then "If they don't, …")
    val TARGET_OPPONENT_MAY_HAVE_YOU = Regex("""[Tt]arget opponent may have you (.+)\.""")
    // trk chunk 06: "If her power is 4 or greater, instead draw a card." (the sentence before happens only if the condition is false)
    val IF_INSTEAD = Regex("""If (.+?), instead (.+)\.""")
    // trk chunk 07: "draw a card for every five creature types among non-Borg creatures you control."
    val DRAW_FOR_EVERY = Regex("""[Dd]raw a card for every (\w+) (.+)\.""")
    // trk chunk 07: "If you draw three or more cards this way, put two +1/+1 counters on each creature you control." (after a draw)
    val IF_DREW_N_THIS_WAY = Regex("""If you draw (\w+) or more cards this way, (.+)\.""")
}
