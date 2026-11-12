package arcanum.parser

/** Regular expressions of Murders at Karlov Manor (MKM) wordings read in [Sentences] and [Events]. */
internal object Mkm {
    // mkm chunk 01: "When this creature enters or is turned face up" (one ability, two events; CR 603.2, 702.168d: turning it face up
    // isn't entering)
    val ENTERS_OR_TURNED_FACE_UP = Regex("""(.+) enters or is turned face up""")
    // mkm chunk 01: "As this creature is turned face up, …", "As this creature enters or is turned face up, …" (CR 614.1e)
    val AS_ENTERS_OR_TURNED_FACE_UP = Regex("""As (.+?) (enters or )?is turned face up, (.+)""")
    // mkm chunk 01: "Disguise {5}{R}. This cost is reduced by {1} for each instant and sorcery card in your graveyard." (CR 118.7)
    val DISGUISE_REDUCED_FOR_EACH = Regex("""Disguise ((?:\{[^}]+\})+)\. This cost is reduced by ((?:\{[^}]+\})+) for each (.+?)\.""")
    // mkm chunk 01: "target permanent you own but don't control"
    val OWNED_NOT_CONTROLLED = Regex("""(.+) you own but don't control""")
    // mkm chunk 01: "Draw a card for each one they gained control of this way." after giving control of targets
    val DRAW_FOR_EACH_THEY_GAINED_CONTROL = Regex("""Draw a card for each one they gained control of this way\.""")
}
