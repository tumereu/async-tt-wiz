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
    // snc chunk 05: "This creature deals 1 damage to target creature that's blocking it." (granted to the attacker: "it" is this creature)
    val DEALS_DAMAGE_TO_TARGET_BLOCKING_IT = Regex("""(.+?) deals (\w+) damage to target (.+?) that's blocking it\.""")
    // snc chunk 05: 'Enchanted creature can't attack or block and has "{7}: ..."'
    val ATTACHED_CANT_AND_HAS_QUOTED = Regex("""(?:Enchanted|Equipped) creature (can't .+?) and has "(.+)"""")
    // snc chunk 05: "two or more Equipment are attached to it"
    val ATTACHMENTS_ATTACHED_TO_IT = Regex("""(\w+) or more (.+?) are attached to it""")
    // snc chunk 05: "All creatures attack enchanted creature's controller each combat if able."
    val ALL_ATTACK_ATTACHED_CONTROLLER = Regex("""All (creatures) attack (?:enchanted|equipped) creature's controller each combat if able\.""")
    // snc chunk 05: "Equipped permanent isn't a planeswalker and is a creature in addition to its other types."
    val ATTACHED_ISNT_AND_IS_ADDITION = Regex("""(?:Enchanted|Equipped) permanent isn't an? (\w+) and is an? (\w+) in addition to its other types\.""")
    // snc chunk 06: "any opponent may tap an untapped creature they control."
    val ANY_OPPONENT_MAY_TAP = Regex("""[Aa]ny opponent may (tap an untapped .+?) they control\.""")
    // snc chunk 06: "If you would create one or more tokens, you may instead create that many 2/2 green Cat creature tokens with haste or that
    // many 3/1 green Dog creature tokens with vigilance."
    val CREATE_TOKENS_MAY_INSTEAD_ONE_OF = Regex("""If you would create one or more tokens, you may instead create that many (.+? tokens(?: with [^,]+?)?) or that many (.+? tokens(?: with .+?)?)\.""")
    // snc chunk 07: "If your life total is less than your starting life total, this spell costs {X} less to cast, where X is the difference."
    val IF_LESS_THAN_COSTS_X_LESS_DIFFERENCE = Regex("""If (.+?) is less than (.+?), (.+?) costs \{X\} less to cast, where X is the difference\.""")
    // snc chunk 07: "[Each opponent] sacrifices a creature of their choice, discards a card, and loses 4 life." (the subject's verb phrases)
    val VERB_LIST_OF_ONE_SUBJECT = Regex("""(.+?), ((?:loses|gains|draws|discards|mills|sacrifices|exiles|gets) [^,]+), and ((?:loses|gains|draws|discards|mills|sacrifices|exiles|gets) [^,]+)\.""")
    // snc chunk 07: "Exile up to one target creature card from a graveyard. If a card is put into exile this way, look at the top three cards ..."
    val EXILE_IF_A_CARD_EXILED_THIS_WAY = Regex("""((?:[Ee]xile) [^.]+)\. If a card is put into exile this way, (.+)""")
    // snc chunk 07: "You may cast spells from the top of your library by removing a counter from a creature you control in addition to paying their other costs."
    val CAST_FROM_TOP_BY_ADDITIONAL = Regex("""You may cast spells from the top of your library by (removing|sacrificing|discarding|exiling|tapping|paying) (.+?) in addition to paying their other costs\.""")
    // snc chunk 07: "Remove a counter from a creature you control" (a cost)
    val REMOVE_A_COUNTER_FROM_A = Regex("""Remove a counter from an? (.+)""")
    // snc chunk 07: "Spend this mana only to cast spells with exactly three colors."
    val CAST_SPELLS_EXACTLY_COLORS = Regex("""cast spells with exactly (\w+) colors""")
    // snc chunk 08: "For each color, return up to one target creature card of that color from your graveyard to your hand."
    val FOR_EACH_COLOR_RETURN_UP_TO_ONE = Regex("""For each color, return up to one target (.+?) of that color from your graveyard to your (hand)\.""")
    // snc chunk 08: "Each opponent chooses a permanent they control that shares a card type with the sacrificed permanent and sacrifices it."
    val EACH_CHOOSES_SHARING_AND_SACRIFICES = Regex("""Each (opponent|player) chooses an? (.+?) they control that shares a (card type|color|creature type) with (the sacrificed \w+|it) and sacrifices it\.""")
    // snc chunk 08: "For each of those permanents, its controller creates a 3/3 white Angel creature token with flying."
    val FOR_EACH_OF_THOSE_ITS_CONTROLLER_CREATES = Regex("""For each of those (\w+), its controller creates (.+)\.""")
    // snc chunk 08: "Count the colors of the sacrificed creature, then search your library for a creature card that's exactly that many colors plus one."
    val COUNT_COLORS_SEARCH_EXACTLY_PLUS = Regex("""Count the colors of (the sacrificed \w+), then search your library for an? (.+?) that's exactly that many colors plus (\w+)\.""")
    // snc chunk 08: "if its mana value is equal to 1 plus the number of soul counters on this enchantment" (in a cast trigger)
    val ITS_MANA_VALUE_IS_EQUAL_TO = Regex("""its mana value is equal to (?:(\d+) plus )?(.+)""")
    val NUMBER_OF_COUNTERS_ON = Regex("""the number of (\S+) counters on (.+)""")
    // snc chunk 08: Exotic Pets
    val CREATE_THEN_FOR_EACH_KIND_AMONG = Regex("""(Create .+? tokens .+?)\s+Then for each kind of counter among (.+?), put a counter of that kind on either of those tokens\.""")
    // snc chunk 08: Urabrask, "the next time they would draw a card this turn, instead they exile the top card of their library. They may play it
    // this turn."
    val NEXT_DRAW_INSTEAD_EXILE_TOP_MAY_PLAY = Regex("""The next time they would draw a card this turn, instead they exile the top card of their library\. They may play (?:it|that card) this turn\.""")
    // snc chunk 09: "Creatures you control also get +1/+0 and have trample as long as you control six or more creatures."
    val ALSO_GET_AS_LONG_AS = Regex("""(.+? you control) also (get .+ as long as .+\.)""")
    // snc fixes: "Casualty X. The copy isn't legendary and has starting loyalty X."
    val CASUALTY_X = Regex("""Casualty X\.(?: The copy (isn't legendary)?(?: and )?(has starting loyalty X)?\.)?""")
    // snc chunk 09: "Put a +1/+1 counter on each creature you control and a loyalty counter on each planeswalker you control."
    val PUT_COUNTERS_ON_EACH_AND_EACH = Regex("""[Pp]ut (an? \S+ counter on each .+?) and (an? \S+ counter on each .+)\.""")
    // snc chunk 09: "When it deals excess damage this way, destroy target artifact or enchantment an opponent controls with mana value less than or
    // equal to that amount of excess damage."
    val WHEN_IT_DEALS_EXCESS_DESTROY = Regex("""When it deals excess damage this way, destroy target (.+?) with mana value less than or equal to that amount of excess damage\.""")
    // snc chunk 09: "Once during each of your turns, you may cast an instant or sorcery spell from your graveyard by sacrificing a creature in addition
    // to paying its other costs. If a spell cast this way would be put into your graveyard, exile it instead."
    val ONCE_CAST_FROM_GRAVEYARD_BY_ADDITIONAL = Regex("""Once (during each of your turns|each turn), you may cast (an? .+?) from your graveyard by (removing|sacrificing|discarding|exiling|tapping|paying) (.+?) in addition to paying its other costs\.( If a spell cast this way would be put into your graveyard, exile it instead\.)?""")
    // snc chunk 09: "Exile an instant or sorcery card at random from your graveyard. Then copy each card exiled with this enchantment. You may cast any
    // number of the copies without paying their mana costs."
    val EXILE_RANDOM_COPY_EACH_EXILED_WITH_CAST = Regex("""Exile an? (.+?) card at random from your graveyard\. Then copy each card exiled with (.+?)\. You may cast any number of the copies without paying their mana costs\.""")
}
