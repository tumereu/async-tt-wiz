package arcanum.parser

/** Wordings first met in Wilds of Eldraine (WOE). */
internal object Woe {
    // woe chunk 01: Role tokens: "create a Monster Role token attached to target creature you control" (CR 111.10j–r, 303.4i)
    val CREATE_ATTACHED_TO = Regex("""[Cc]reate (an? .+? token) attached to (.+)\.""")
    // woe chunk 01: "For each creature your opponents control, create a Cursed Role token attached to that creature."
    // woe chunk 02: "… attached to that Rat."
    val FOR_EACH_CREATE_ATTACHED = Regex("""For each (.+?), create (an? .+? token) attached to (?:that [A-Za-z]+|it)\.""")
}
