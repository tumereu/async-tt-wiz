package arcanum.parser

/** Regular expressions of Modern Horizons 3 (mh3) wordings read in [Sentences]. */
internal object Mh3 {
    // mh3 chunk 01
    /** The rest of a list after a comma ("5, or 6, create …"): the list's words, then what follows. */
    val LIST_CONTINUES = Regex("""((?:\w+, )*(?:or|and) \w+), (.+)""")
    /** "When you cast this spell and when this creature dies, …": two trigger conditions of one effect (CR 603.1). */
    val AND_WHEN = Regex("""((?:When|Whenever) .+?) and when (.+? (?:dies|leaves the battlefield|enters)), (.+)""")
    /** "If this creature was kicked, it enters with two +1/+1 counters and a trample counter on it and with haste." */
    val IF_IT_ENTERS_WITH_COUNTERS_AND_WITH = Regex("""If (.+), it enters with (.+?) on it(?: and with (.+?))?\.""")
    val COUNTER_IN_LIST = Regex("""(a|an|one|two|three|four|five) (\S+) counters?""")
    /** "Exile up to one target …. If you do, …", "Exile target …. You may cast that card …": then a sentence about the exiled card. */
    val EXILE_THEN = Regex("""(Exile (?:up to one )?target [^.]+\.) ((?:If you do, |You may cast that card ).+)""")
    /** "As this creature enters, it becomes your choice of a 3/3 creature with flying, a 2/5 creature with vigilance, or …" (CR 614.12) */
    val AS_ENTERS_BECOMES_YOUR_CHOICE = Regex("""As (.+) enters, it becomes your choice of (.+)\.""")
    /** The items of "A, B, or C". */
    val OR_LIST = Regex(""",? or |, """)
    val CHOICE_PT_CREATURE_WITH = Regex("""an? (\d+)/(\d+) creature with (.+)""")
    /** "You may cast that card for as long as it remains exiled, and mana of any type can be spent to cast it." (blb), "…, and you may spend
     * colorless mana as though it were mana of any color to cast that spell." (CR 609.4b) */
    val MAY_CAST_REMAINS_EXILED_SPEND = Regex("""You may cast that card for as long as it remains exiled, and (mana of any type can be spent to cast it|you may spend colorless mana as though it were mana of any color to cast that spell)\.""")
}
