package arcanum.parser

/** Wordings first met in Foundations (FDN). */
internal object Fdn {
    // fdn chunk 01: a state trigger's condition (CR 603.8)
    val WHEN_THERE_ARE = Regex("""When (there are .+?), (.+)""")
    // fdn chunk 01: "If you do" after a mandatory exile (CR 118.12)
    val EXILE_IT_IF_YOU_DO = Regex("""Exile it\. If you do, ([^.]+\.)""")
    // fdn chunk 02: the counter and the count on the same object, named by the ability
    val COUNTER_ON_IT_THEN_IF_IT_HAS = Regex("""[Pp]ut an? (\S+) counter on it\. Then if it has (\w+) or more (\S+) counters on it, (.+)""")
    // fdn chunk 02: an Aura's own card, after the creature it enchanted died
    val RETURN_THIS_CARD_TO_HAND = Regex("""[Rr]eturn this card to its owner's hand\.""")
    // chunk 03: "Whenever equipped creature becomes untapped"
    val BECOMES_UNTAPPED = Regex("""(.+) becomes untapped""")
    // chunk 03: "Put a bait counter on Fishing Pole." in a granted ability
    val PUT_COUNTER_ON_NAME = Regex("""Put an? (\S+) counter on (.+)\.""")
}
