package arcanum.parser

/** Wordings first met in Phyrexia: All Will Be One (ONE). */
internal object One {
    // one chunk 01: Carnivorous Canopy, Reject Imperfection, Serum Snare (and tmt's "If its mana value was 4 or less"): the earlier target's
    // mana value as it last existed (CR 608.2h)
    val IF_MANA_VALUE_WAS = Regex(
        """If (?:(?:its|that (?:permanent|spell|creature|card)'s) mana value was|(?:it|that (?:permanent|spell|creature|card)) had mana value) (\d+) or (less|greater), (.+)""")
    // one chunk 01: Expand the Sphere
    val IF_PUT_FEWER_REPEAT_DIFFERENCE = Regex("""If you put fewer than (\w+) (?:\w+ )?(?:lands|cards) onto the battlefield this way, (.+) a number of times equal to the difference\.""")
    // one chunk 01: Ichormoon Gauntlet
    val CHOOSE_COUNTER_PUT_ADDITIONAL = Regex("""Choose a counter on target (\w+)\. Put an additional counter of that kind on that (\w+)\.""")
    // one chunk 01: Tekuthal, Inquiry Dominus
    val WOULD_DO_TWICE_INSTEAD = Regex("""If you would ([a-z ]+), ([a-z ]+) twice instead\.""")
    // one chunk 02: Skrelv, Defector Mite
    val CHOOSE_COLOR_GAINS_HEXPROOF_FROM_UNBLOCKABLE_BY = Regex(
        """Choose a color\. (Another target|Target) (.+?) gains (?:(.+?) and )?hexproof from that color until end of turn\. (?:It|That creature) can't be blocked by (\w+) of that color this turn\.""")
}
