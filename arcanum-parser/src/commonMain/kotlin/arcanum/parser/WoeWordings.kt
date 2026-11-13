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
    // woe chunk 04: "If this spell was bargained, destroy that creature instead.", "…, it deals twice X damage to that permanent instead.",
    // "…, instead it deals 3 damage to that permanent and you scry 1." (after damage to a target)
    val IF_INSTEAD_OF_DAMAGE = Regex("""If (.+?), (?:instead (.+)|(.+) instead)\.""")
    val DEALS_TO_THAT = Regex("""it deals (\d+|twice X) damage to that (?:creature|permanent)(?: and (.+))?""")
    val DESTROY_THAT = Regex("""destroy that (?:creature|permanent)""")
    // woe chunk 04: "If this spell was bargained, you may put one of those cards with mana value 4 or less onto the battlefield instead of putting
    // it into your hand." (after returning target cards to their owner's hand)
    val MAY_PUT_ONE_OF_THOSE_ONTO_BATTLEFIELD_INSTEAD = Regex("""If (.+?), you may put one of those cards with mana value (\d+) or less onto the battlefield instead of putting it into your hand\.""")
    // woe chunk 04: "Put two of them into your hand and the rest on the bottom of your library in a random order." (after looking at the top cards)
    val PUT_N_OF_THEM_INTO_HAND_REST_BOTTOM = Regex("""Put (\w+) of them into your hand and the rest on the bottom of your library in (any|a random) order\.""")
    // woe chunk 04: "put up to two of them back on top of your library in any order and the rest into your graveyard" (CR 401.4)
    val PUT_UP_TO_BACK_ON_TOP_REST_GRAVEYARD = Regex("""[Pp]ut up to (\w+) of them back on top of your library in any order and the rest into your graveyard\.""")
    // woe chunk 04: "Put the revealed cards onto the battlefield." / "… into your hand." (the cards revealed from among those looked at)
    val PUT_THE_REVEALED_CARDS = Regex("""[Pp]ut the revealed cards (onto the battlefield|into your hand)\.""")
    // woe chunk 04: "target land you control becomes a 3/3 Elemental creature with haste that's still a land" (for good, CR 611.2a; CR 205.1b)
    val BECOMES_THATS_STILL = Regex("""(.+?) becomes (an? .+?) that's still an? (\w+)\.""")
    // woe chunk 04: "If a permanent dealt damage by Torch the Tower would die this turn, exile it instead." (a later paragraph about the
    // spell's target, CR 614.1a)
    val IF_DEALT_DAMAGE_BY_WOULD_DIE_EXILE = Regex("""If a (?:creature|permanent) dealt damage by (.+?) would die this turn, exile it instead\.""")
    // woe chunk 04: "Search your library for a card, exile it face down, then shuffle. If this spell was bargained, you may cast the exiled card
    // without paying its mana cost if that spell's mana value is 4 or less. Put the exiled card into your hand if it wasn't cast this way."
    val SEARCH_EXILE_FACE_DOWN_MAY_CAST_FREE_OR_HAND = Regex("""Search your library for (an? .+?), exile it face down, then shuffle\. If (.+?), you may cast the exiled card without paying its mana cost if that spell's mana value is (\d+) or less\. Put the exiled card into your hand if it wasn't cast this way\.""")
    // woe chunk 05: "Whenever you tap an untapped creature an opponent controls" (the trigger event; only an untapped permanent can be tapped)
    val YOU_TAP_AN_UNTAPPED = Regex("""you tap an untapped (.+)""")
    // woe chunk 05: "Whenever …, you may pay {1}. When you do, choose one —" (a mana cost paid on resolution, CR 118.12; the reflexive trigger's modes, CR 603.12)
    val MAY_PAY_WHEN_YOU_DO_CHOOSE = Regex("""(Whenever .+?), you may pay (\{[^}]+\})\. When you do, (.+)""")
    // woe chunk 05: 'Until end of turn, this land becomes a 2/1 blue and red Elemental creature with "…"' (It's still a land. is a separate sentence)
    val BECOMES_WITH_QUOTED = Regex("""(Until end of turn, )?(.+?) becomes (an? [^"]+?) with "(.+)"\.""")
    // woe chunk 05: "If the token is an Aura, untap Yenna, then scry 2." (after "Create a token that's a copy of it, …")
    val IF_THE_TOKEN_IS = Regex("""If the token is an? (\w+), (.+)\.""")
}
