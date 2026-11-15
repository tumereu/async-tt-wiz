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
}
