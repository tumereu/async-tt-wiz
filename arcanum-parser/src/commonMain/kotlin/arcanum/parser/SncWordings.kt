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
    // snc chunk 04: "that creature attacks during its controller's next combat phase if able." (a requirement through that declare attackers step)
    val ATTACKS_DURING_NEXT_COMBAT = Regex("""(That creature|It|Target .+?) attacks during (?:its|their) controller's next combat phase if able\.""")
    // snc chunk 04: "Exile them from your graveyard." (the cards of a discard or put-into-graveyard batch)
    val EXILE_THEM_FROM_GRAVEYARD = Regex("""[Ee]xile them from (?:your|their) graveyard\.""")
    // snc chunk 04: "If a creature is put into exile this way, return each other card exiled with this Vehicle to the battlefield under its owner's control."
    val IF_EXILED_THIS_WAY_RETURN_OTHERS = Regex("""If an? (.+?) is put into exile this way, return each other card exiled with (.+?) to the battlefield under its owner's control\.""")
    // snc chunk 04: "enchanted creature is attacking alone" (CR 506.5)
    val ATTACHED_IS_ATTACKING = Regex("""(equipped|enchanted) creature is attacking( alone)?""")
    // snc chunk 04: "cards exiled with it" (in "the number of …")
    val CARDS_EXILED_WITH = Regex("""cards exiled with (.+)""")
}
