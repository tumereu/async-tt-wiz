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
    // mkm chunk 02: "To solve — You control three or more artifacts." (a Case, CR 719.3a)
    val TO_SOLVE = Regex("""To solve — (.+)\.""")
    // mkm chunk 02: "you may have Lazav become a copy of a creature card exiled with it until end of turn" (CR 607.2a, 707.2)
    val MAY_HAVE_BECOME_COPY_OF_EXILED_WITH = Regex("""[Yy]ou may have (.+?) become a copy of an? (.+?) exiled with (.+?) until end of turn\.""")
    // mkm chunk 02: "Each player who controls the most creatures investigates." (CR 701.16a)
    val EACH_PLAYER_WHO_INVESTIGATES = Regex("""Each (player|opponent) who (.+?) investigates\.""")
    // mkm chunk 02: "investigate for each opponent who lost life this turn", "investigate once for each opponent who has more cards in hand than you"
    val INVESTIGATE_FOR_EACH = Regex("""[Ii]nvestigate (?:once )?for each (.+)\.""")
    // mkm chunk 02: "Choose any number of target players." (targets only, for the sentences after it)
    val CHOOSE_ANY_NUMBER_OF_TARGET = Regex("""Choose any number of target (players|opponents)\.""")
    // mkm chunk 02: "Investigate X times, where X is the total number of creatures those players control."
    val INVESTIGATE_X_TIMES_THOSE_PLAYERS_CONTROL = Regex("""Investigate X times, where X is the (?:total )?number of (.+?) those players control\.""")
    // mkm chunk 02: "This spell costs {W}{U} more to cast for each target beyond the first." (whatever the targets are, CR 601.2f)
    val MORE_FOR_EACH_TARGET_BEYOND_THE_FIRST = Regex("""This spell costs ((?:\{[^}]+\})+) more to cast for each target beyond the first\.""")
    // mkm chunk 02: "If any of them are suspected, they're no longer suspected." after an effect on a group (CR 701.60a)
    val IF_ANY_OF_THEM_NO_LONGER = Regex("""If any of them are (\w+), they're no longer \1\.""")
    // mkm chunk 02: "If excess damage was dealt to that creature this way, investigate." (CR 120.4a)
    val IF_EXCESS_DAMAGE_THIS_WAY = Regex("""If excess damage was dealt(?: to that (?:creature|permanent))? this way, (.+)""")
    // mkm chunk 02: "this creature is dealt 3 or more damage" (all the damage dealt to it at once, CR 120.4, 603.2c)
    val IS_DEALT_N_OR_MORE_DAMAGE = Regex("""(.+?) is dealt (\d+) or more damage""")
    // mkm chunk 02: "You may cast a spell from each opponent's graveyard without paying its mana cost." (one card of each, CR 601.2)
    val MAY_CAST_FROM_EACH_OPPONENTS_GRAVEYARD_FREE = Regex("""You may cast an? (spell|.+? spell) from each opponent's graveyard without paying its mana cost\.""")
    // mkm chunk 02: "opponent who lost life this turn", "player who controls the most creatures"
    val PLAYER_WHO = Regex("""(player|opponent) who (.+)""")
}
