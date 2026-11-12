package arcanum.parser

/** Wordings first met in Wilds of Eldraine (WOE). */
internal object Woe {
    // woe chunk 01: Role tokens: "create a Monster Role token attached to target creature you control" (CR 111.10j–r, 303.4i)
    val CREATE_ATTACHED_TO = Regex("""[Cc]reate (an? .+? token) attached to (.+)\.""")
    // woe chunk 01: "For each creature your opponents control, create a Cursed Role token attached to that creature."
    // woe chunk 02: "… attached to that Rat."
    val FOR_EACH_CREATE_ATTACHED = Regex("""For each (.+?), create (an? .+? token) attached to (?:that [A-Za-z]+|it)\.""")
    // woe chunk 03: "You may sacrifice an artifact, enchantment, or token. If you don't, tap this creature." (CR 118.12: the optional payment's bound result)
    val MAY_PAY_IF_YOU_DONT = Regex("""[Yy]ou may (.+?)\. If you don't, (.+)\.""")
    // woe chunk 03: "create half X Food tokens, rounded up" (X as the permanent was cast, CR 107.3m; CR 107.1a rounding)
    val CREATE_HALF_X = Regex("""[Cc]reate half X (.+? token)s, rounded (up|down)\.""")
    // woe chunk 03: "up to one target creature gets -X/-X until end of turn" (X from the ability's "where X is", CR 107.3a)
    val TARGET_GETS_MINUS_X_UNTIL_EOT = Regex("""((?:[Uu]p to one )?[Tt]arget .+?) gets -X/-X until end of turn\.""")
}
