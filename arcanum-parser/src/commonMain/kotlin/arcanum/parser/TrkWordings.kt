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
    // trk chunk 08: "You can't sacrifice Treasure tokens."
    val YOU_CANT_SACRIFICE = Regex("""You can't sacrifice (.+?)\.""")
    // trk chunk 08: "Choose target creature. Whenever it deals combat damage to a player this turn, you draw that many cards."
    val CHOOSE_TARGET_WHENEVER_COMBAT_DAMAGE_THIS_TURN = Regex("""Choose target (.+?)\. Whenever (?:it|that creature) deals combat damage to a player this turn, (.+)""")
    // trk chunk 08: "<a cast permission>. If you cast a spell this way, that artifact creature enters with a finality counter on it."
    val PERMISSION_CAST_THIS_WAY_ENTERS_WITH = Regex("""(.+?\.) If you cast a spell this way, that (?:[a-z]+ )*?(?:creature|permanent|spell) enters with (a|an|one|two) (\S+) counters? on it\.""")
    // trk chunk 08: "If another creature you control with a +1/+1 counter on it would be destroyed, instead remove all +1/+1 counters from it and all
    // damage already dealt to it is healed."
    val WOULD_BE_DESTROYED_REMOVE_COUNTERS_HEAL = Regex("""If (.+?) would be destroyed, instead remove all (\S+) counters from it and all damage already dealt to it is healed\.""")
    // trk chunk 09: "you control five or more differently named lands" (CR 201.2)
    val CONTROL_N_DIFFERENTLY_NAMED = Regex("""you control (\w+) or more differently named (.+)""")
    // trk chunk 09: "Seven of Nine's base power and toughness become 7/9." (layer 7b, no end: CR 611.2a)
    val BASE_PT_BECOME = Regex("""(.+?)'s base power and toughness become (\d+)/(\d+)\.""")
    // trk chunk 09: "Until end of turn, it gets +1/+1 and becomes a Doctor." (its other creature types replaced, CR 205.1b)
    val UNTIL_EOT_IT_GETS_AND_BECOMES = Regex("""Until end of turn, it gets ([+-]\d+)/([+-]\d+) and becomes an? ([A-Z]\w+)\.""")
    // trk chunk 09: "If a creature you control would enter while you control an attacking creature, instead it enters tapped and attacking."
    val WOULD_ENTER_WHILE_ENTERS_ATTACKING = Regex("""If (an? .+?) would enter while (.+?), instead it enters tapped and attacking\.""")
    // trk chunk 09: "All artifacts and creatures phase out." (CR 702.26)
    val ALL_PHASE_OUT = Regex("""All (.+) phase out\.""")
    // trk chunk 09: "Permanents can't phase in." (CR 702.26c, 101.2)
    val PERMANENTS_CANT_PHASE_IN = Regex("""Permanents can't phase in\.""")
}
