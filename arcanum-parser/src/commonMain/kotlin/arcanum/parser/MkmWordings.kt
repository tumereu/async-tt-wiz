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
    // mkm chunk 03: "Search your library for a basic land card. If evidence was collected, instead search your library for a creature or land card.
    // Reveal that card, put it into your hand, then shuffle."
    val SEARCH_IF_INSTEAD_SEARCH = Regex("""Search your library for (.+?)\. If (.+?), instead search your library for (.+?)\. Reveal (?:that card|it), (.+)\.""")
    // mkm chunk 03: "If evidence was collected, it has base power and toughness 1/1 until end of turn instead." after a "becomes"
    val IF_HAS_BASE_PT_INSTEAD = Regex("""If (.+?), (?:it|that creature) has base power and toughness (\d+)/(\d+) until end of turn instead\.""")
    // mkm chunk 03: "As an additional cost to cast this spell, you may collect evidence 6. This spell costs {2} less to cast if evidence was collected."
    val ADDITIONAL_COST_THEN_THIS_SPELL = Regex("""(As an additional cost to cast this spell, [^.]+\.) (This spell .+\.)""")
    // mkm chunk 03: "Collect evidence 4." as an effect ("you may collect evidence 4", CR 701.59a)
    val COLLECT_EVIDENCE = Regex("""[Cc]ollect evidence (\d+)\.""")
    // mkm chunk 03: "… for as long as this creature remains tapped." (CR 611.2b)
    val FOR_AS_LONG_AS_REMAINS_TAPPED = Regex("""(.+) for as long as (.+?) remains tapped\.""")
    // mkm chunk 03: "You may choose not to untap this creature during your untap step." (CR 502.3)
    val MAY_CHOOSE_NOT_TO_UNTAP = Regex("""You may choose not to untap (.+?) during your untap step\.""")
    // mkm chunk 03: "If this creature is a Detective, it becomes a legendary creature named Mileva, the Stalwart, it has base power and toughness
    // 5/5, and it gains "Other creatures you control have indestructible.""
    val IF_IS_BECOMES_LEGENDARY_NAMED = Regex("""(?:If (.+?) is an? ([A-Z]\w+), )?(.+?) becomes a legendary creature named (.+?), it has base power and toughness (\d+)/(\d+), and it gains "(.+)"\.""")
    // mkm chunk 03: "you may cast an instant or sorcery spell from among cards exiled with it" (CR 607.2a)
    val MAY_CAST_FROM_AMONG_EXILED_WITH = Regex("""[Yy]ou may cast an? (.+?) spell from among cards exiled with (.+?)\.""")
    // mkm chunk 03: "If that spell would be put into a graveyard, put it on the bottom of its owner's library instead." after casting it
    val IF_THAT_SPELL_WOULD_GRAVEYARD_BOTTOM = Regex("""If that spell would be put into a graveyard, put it on the bottom of its owner's library instead\.""")
    // mkm chunk 03: "you may exile it and collect evidence 4. If you do, return this card to the battlefield tapped." (a dies trigger)
    val MAY_EXILE_IT_AND_IF_YOU_DO = Regex("""You may exile it and (.+?)\. If you do, (.+)""")
    // mkm chunk 03: "Destroy all creatures. If evidence was collected, exile a card from an opponent's graveyard. Then search its owner's graveyard,
    // hand, and library for any number of cards with that name and exile them. That player shuffles, then draws a card for each card exiled from
    // their hand this way."
    val IF_EXILE_FROM_OPPONENTS_GRAVEYARD_SEARCH_NAME = Regex("""(.+?\.) If (.+?), exile a card from an opponent's graveyard\. Then search its owner's graveyard, hand, and library for any number of cards with that name and exile them\. That player shuffles, then draws a card for each card exiled from their hand this way\.""")
    // mkm chunk 04: "Whenever one or more creatures you control and/or creature cards in your graveyard are put into exile" (once for the batch, CR 603.2c)
    val PUT_INTO_EXILE_FROM_BATTLEFIELD_AND_OR_GRAVEYARD = Regex("""one or more (.+?) you control and/or (.+?) cards in your graveyard are put into exile""")
    // mkm chunk 04: "You may choose a creature card from among them. Until end of turn, target token you control becomes a copy of it, except it has flying."
    val MAY_CHOOSE_FROM_AMONG_THEM_TARGET_BECOMES_COPY = Regex("""You may choose an? (.+?) from among them\. Until end of turn, target (.+?) becomes a copy of it, except it has (\w+)\.""")
    // mkm chunk 04: "exile a card from a graveyard" (chosen as it resolves, from any player's graveyard; CR 701.13a)
    val EXILE_A_CARD_FROM_A_GRAVEYARD = Regex("""[Ee]xile a card from a graveyard\.""")
    // mkm chunk 05: "Suspect it." (an object named otherwise than as a target; CR 701.60a)
    val SUSPECT_OBJECTS = Regex("""[Ss]uspect (?!up to |target |other |another |one of )(.+?)\.""")
    // mkm chunk 05: "All suspected creatures are no longer suspected.", "this creature is no longer suspected." (CR 701.60a)
    val NO_LONGER = Regex("""(.+?)(?: is| are|'s) no longer (\w+)\.""") // mkm chunk 07: "it's no longer suspected"
    // mkm chunk 05: "If it's suspected, exile it." about the object the sentences before named
    val IF_IT_IS_MARKED = Regex("""If (it|that creature)(?:'s| is)(n't| not)? (\w+), (.+)""")
    // mkm chunk 05: "you may suspect one of the other creatures. If you do, this creature is no longer suspected." after a batch trigger
    val MAY_SUSPECT_ONE_OF_THE_OTHER = Regex("""[Yy]ou may suspect one of the other (.+?)\. If you do, (.+)""")
    // mkm chunk 06: "three or more sources you controlled dealt damage this turn" (each source once, CR 120.1)
    val N_SOURCES_DEALT_DAMAGE_THIS_TURN = Regex("""(\w+) or more sources you controlled dealt damage this turn""")
    // mkm chunk 06: "three or more creatures attacked this turn" (any player's, each creature once, CR 508.1)
    val N_ATTACKED_THIS_TURN = Regex("""(\w+) or more (.+?) attacked this turn""")
    // mkm chunk 06: "three or more creature cards were put into graveyards from anywhere this turn"
    // mkm chunk 08: "a creature card was put into your graveyard from anywhere this turn"
    val N_PUT_INTO_GRAVEYARDS_THIS_TURN = Regex("""(?:(\w+) or more|an?) (.+?) (?:were|was) put into (graveyards|your graveyard) from anywhere this turn""")
    // mkm chunk 06: "This Case is a 4/4 Gorgon creature with deathtouch and lifelink in addition to its other types."
    val IS_PT_CREATURE_WITH_IN_ADDITION = Regex("""(.+?) is an? (\d+)/(\d+) ((?:[A-Z]\w+ )*)creature(?: with ([a-z ,]+?))? in addition to its other types\.""")
    // mkm chunk 06: "You may look at the top card of your library any time, and you may play lands and cast … from the top of your library." (two
    // permissions of the player in one sentence)
    val YOU_MAY_AND_YOU_MAY = Regex("""(You may .+?), and you may (.+\.)""")
    // mkm chunk 06: "Creature cards in your graveyard gain "You may cast this card from your graveyard" until end of turn."
    val CARDS_IN_YOUR_GRAVEYARD_GAIN_QUOTED = Regex("""(.+?) cards? in your graveyard gains? "(.+)" until end of turn\.""")
    // mkm chunk 06: "create a 2/1 black Skeleton creature token and suspect it"
    val CREATE_TOKEN_AND_IT = Regex("""([Cc]reate an? [^.]+? token) and ([a-z][^.]* it)\.""")
    // mkm chunk 07: static predicates ("Enchanted creature has base power and toughness 1/1, has deathtouch, and is an Assassin in addition to its
    // other types.", "… and can't block Detectives.", "… and can't become suspected.")
    val BASE_PT_PREDICATE = Regex("""has base power and toughness (\d+)/(\d+)""")
    val CANT_BLOCK_PREDICATE = Regex("""can't block (.+)""")
    val CANT_BECOME_PREDICATE = Regex("""can't become (\w+)""")
    val IS_SUBTYPES_IN_ADDITION_PREDICATE = Regex("""is an? ((?:[A-Z]\w+ )*[A-Z]\w+) in addition to its other types""")
    // mkm chunk 07: "Cloak a card from your hand." (CR 701.58a)
    val CLOAK_FROM_HAND = Regex("""[Cc]loak an? (.+?) from your hand\.""")
    // mkm chunk 07: "Look at the top five cards of your library, cloak two of them, and put the rest on the bottom of your library in a random order."
    val LOOK_CLOAK_REST_BOTTOM = Regex("""Look at the top (\w+) cards of your library, cloak (\w+) of them, and put the rest on the bottom of your library in (any|a random) order\.""")
    // mkm chunk 07: "cloak the top card of your library, then attach this Equipment to it."
    val THEN_ATTACH_TO_IT = Regex("""(.+?), then attach (.+?) to it\.""")
    // mkm chunk 07: "Turn target face-down creature face up." (CR 708.8)
    val TURN_TARGET_FACE_UP = Regex("""Turn target (.+?) face up\.""")
    // mkm chunk 08: "Destroy up to one target artifact, up to one target creature, up to one target enchantment, and up to one target planeswalker."
    val UP_TO_ONE_TARGET_EACH = Regex("""(Destroy|Exile) ((?:up to one target [a-z]+, )+and up to one target [a-z]+)\.""")
    val UP_TO_ONE_TARGET = Regex("""(?:and )?up to one target ([a-z]+)""")
    // mkm chunk 08: "collect evidence X, where X is the total mana value of the permanents this spell targets" (CR 601.2c: targets come first)
    val COLLECT_EVIDENCE_TARGETS_MANA_VALUE = Regex("""[Cc]ollect evidence X, where X is the total mana value of the (.+?) this spell targets""")
    // mkm chunk 08: "If the card's mana value is 1 or less, create …" (the card the instructions last named)
    val IF_ITS_MANA_VALUE = Regex("""If (?:the card|that card|it)'s mana value is (\d+) or (less|greater), (.+)""")
    // mkm chunk 08: "You may have it become no longer suspected." (CR 701.60a)
    val MAY_HAVE_BECOME_NO_LONGER = Regex("""You may have (.+?) become no longer (\w+)\.""")
    // mkm chunk 08: "Search your library for a creature or land card and reveal it. Put it onto the battlefield tapped if it's a land card.
    // Otherwise, put it into your hand. Then shuffle."
    val SEARCH_REVEAL_PUT_IF_TYPE_OTHERWISE_HAND = Regex("""Search your library for an? (.+?) and reveal it\. Put it onto the battlefield( tapped)? if it's an? (.+?)\. Otherwise, put it into your hand\. Then shuffle\.""")
    // mkm chunk 08: "sacrifice any number of other creatures, then exile the top X cards of your library, where X is their total power. You may cast
    // any number of instant and/or sorcery spells from among the exiled cards without paying their mana costs."
    val SACRIFICE_ANY_EXILE_TOTAL_POWER_CAST = Regex("""[Ss]acrifice any number of (.+?), then exile the top X cards of your library, where X is their total power\. You may cast any number of (.+?) spells from among the exiled cards without paying their mana costs\.""")
    // mkm chunk 08: "That spell gains deathtouch and lifelink." in a cast trigger
    val THAT_SPELL_GAINS = Regex("""That spell gains ([a-z ,]+)\.""")
    // mkm chunk 08: "You may turn a creature you control face up." (chosen as it resolves, CR 708.8)
    val MAY_TURN_A_FACE_UP = Regex("""You may turn an? (.+?) face up\.""")
    // mkm chunk 07: "Turn this creature face up. If you can't, exile it, then you may cast the exiled card without paying its mana cost."
    val TURN_FACE_UP_IF_YOU_CANT = Regex("""Turn (.+?) face up\. If you can't, (.+)""")
    // mkm chunk 07: "you may cast the exiled card without paying its mana cost" (cast as this resolves, CR 608.2g)
    val MAY_CAST_IT_FREE = Regex("""[Yy]ou may cast (the exiled card|that card|it) without paying its mana cost\.""")
    // mkm chunk 07: "Exile any number of face-up creatures you control with disguise in a face-down pile, shuffle that pile, then cloak them."
    val EXILE_FACE_DOWN_PILE_CLOAK = Regex("""Exile any number of (.+?) in a face-down pile, shuffle that pile, then cloak them\.""")
    // mkm chunk 07: "you mill two cards" (CR 701.17a)
    val YOU_MILL = Regex("""[Yy]ou mill (.+)""")
    // mkm chunk 07: "Then sacrifice it if it has five or more bloodstain counters on it. When you do, return target creature card …" (CR 603.12)
    val SACRIFICE_IF_WHEN_YOU_DO = Regex("""(?:Then )?[Ss]acrifice (.+?) if (.+?)\. When you do, (.+)""")
    // mkm chunk 07: "Enchanted creature gets +2/+2 as long as it's a Detective you control. Otherwise, it has base power and toughness 1/1 and can't
    // block Detectives." (two statics, one condition and its opposite)
    val AS_LONG_AS_ITS_OTHERWISE = Regex("""(.+?) ((?:gets?|has|have|can't) .+?) as long as it's an? (.+?)\. Otherwise, it ((?:gets?|has|have|can't) .+)\.""")
}
