package arcanum.parser

/** Regular expressions of Streets of New Capenna (snc) wordings read in [Sentences]. */
internal object Snc {
    // snc chunk 01: "Look at the top card of your library. You may play a land from the top of your library or cast a spell with mana value
    // less than or equal to the damage dealt from the top of your library without paying its mana cost. If you don't, put that card into your hand."
    val LOOK_PLAY_OR_CAST_UP_TO_DAMAGE_ELSE_HAND = Regex("""[Ll]ook at the top card of your library\. You may play a land from the top of your library or cast a spell with mana value less than or equal to the damage dealt from the top of your library without paying its mana cost\. If you don't, put that card into your hand\.""")
    // snc chunk 02: "Choose target creature or planeswalker. If it was dealt noncombat damage this turn, <effects>. Otherwise, <effects>."
    val CHOOSE_TARGET_IF_DEALT_NONCOMBAT_OTHERWISE = Regex("""Choose target (.+?)\. If it was dealt noncombat damage this turn, (.+?)\. Otherwise, (.+?)\.""")
    // snc chunk 03: "this creature deals 2 damage to you for each Treasure you control."
    val DEALS_DAMAGE_TO_FOR_EACH = Regex("""(.+?) deals (\d+) damage to (you|each opponent|each player) for each (.+)\.""")
}
