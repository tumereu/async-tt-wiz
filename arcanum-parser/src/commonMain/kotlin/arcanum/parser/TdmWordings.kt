package arcanum.parser

import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.Measure
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone

/** Regular expressions of Tarkir: Dragonstorm (tdm) wordings read in [Sentences] and [OracleParser]. */
internal object Tdm {
    // tdm chunk 01: endure (CR 701.63)
    val ENDURES = Regex("""(.+?) endures? (\w+)\.""")
    val LOSE_LIFE_AND_ENDURES = Regex("""[Yy]ou lose (\d+) life and (.+?) endures (\w+)\.""")
    // tdm chunk 01: "Mobilize X, where X is …" (CR 702.181a, a number the text defines)
    val MOBILIZE_X_WHERE = Regex("""Mobilize X, where X is (.+?)\.""")
    // tdm chunk 02: siege-like clans: "As this enchantment enters, choose Abzan or Mardu." and "• Abzan — [ability]" (CR 614.12, 607.2a)
    val AS_ENTERS_CHOOSE_A_OR_B = Regex("""As (.+?) enters, choose ([A-Z][a-z]+) or ([A-Z][a-z]+)\.""")
    val CHOSEN_OPTION_ABILITY = Regex("""• ([A-Z][a-z]+) — (.+)""")
    // tdm chunk 03: counters of several kinds ("Put a flying counter, a deathtouch counter, and a lifelink counter on target creature.",
    // "Put two +1/+1 counters and a reach counter on target creature.")
    val PUT_COUNTER_LIST_ON = Regex("""Put ((?:a|an|one|two|three|four|five|X) \S+ counters?(?:,? (?:and )?(?:a|an|one|two|three|four|five|X) \S+ counters?)+) on (.+)\.""")
    val COUNTER_ITEM = Regex("""(a|an|one|two|three|four|five|X) (\S+) counters?""")
    // tdm chunk 03: "Put a decayed counter on each of X target creatures."
    val PUT_COUNTER_ON_EACH_OF_X_TARGETS = Regex("""Put an? (\S+) counter on each of X target (.+)\.""")
    // tdm chunk 03: "Those creatures gain flying until your next turn." after counters on each of them
    val THOSE_GAIN_UNTIL = Regex("""Those (?:creatures|permanents) gain (.+?) until (your next turn|end of turn)\.""")
    // tdm chunk 04: "If a Dragon was beheld" (CR 701.4a: the optional additional cost was paid)
    val WAS_BEHELD = Regex("""an? (\w+) was beheld""")
    // tdm chunk 04: "You may cast this spell as though it had flash if you behold a Dragon as an additional cost to cast it."
    val FLASH_IF_BEHOLD = Regex("""You may cast this spell as though it had flash if you behold an? (\w+) as an additional cost to cast it\.""")
    // tdm chunk 04: "Counter target spell unless its controller pays {2}. If a Dragon was beheld, counter that spell unless its controller pays {4}
    // instead."
    val COUNTER_UNLESS_PAYS_INSTEAD_IF = Regex("""Counter target (.+?) unless its controller pays (\{[^}]+\})\. If (.+?), counter that spell unless its controller pays (\{[^}]+\}) instead\.""")
    // tdm chunk 05: "This spell costs {2} more to cast if it targets a Dragon." (CR 601.2f)
    val COSTS_MORE_IF_TARGETS = Regex("""This spell costs (\{[^}]+\}) more to cast if it targets an? (.+)\.""")
    // tdm chunk 05: "Target creature gets +1/-1 until end of turn. When it dies under your control this turn, draw two cards."
    val WHEN_IT_DIES_UNDER_YOUR_CONTROL_THIS_TURN = Regex("""When it dies under your control this turn, (.+)""")
    // tdm chunk 05: "target creature an opponent controls gets -2/-2 until end of turn and target creature you control gets +2/+2 until end of turn"
    val TWO_TARGETS_GET = Regex("""[Tt]arget (.+?) gets ([+-]\d+)/([+-]\d+) until end of turn and target (.+?) gets ([+-]\d+)/([+-]\d+) until end of turn\.""")
    // tdm chunk 05: "You may cast this card from your graveyard. If you do, it enters with a finality counter on it."
    val MAY_CAST_FROM_GRAVEYARD_ENTERS_WITH = Regex("""You may cast (.+?) from your graveyard\. If you do, (?:it|he|she|they) enters? with an? (\S+) counter on (?:it|him|her|them)\.""")
    // tdm chunk 05: "Target opponent exiles a creature they control and their graveyard."
    val OPPONENT_EXILES_CREATURE_AND_GRAVEYARD = Regex("""Target opponent exiles an? (.+?) they control and their graveyard\.""")
    // tdm chunk 05: "Creatures target opponent controls get -1/-1 until end of turn."
    val CREATURES_TARGET_OPPONENT_CONTROLS_GET = Regex("""(\w+) target opponent controls get ([+-]\d+)/([+-]\d+) until end of turn\.""")
    // tdm chunk 05: "{T}, Sacrifice a creature you control with mana value X other than Sidisi: Return target creature card with mana value X plus 1 …"
    val WITH_MANA_VALUE_X_PLUS = Regex("""(.+) with mana value X plus (\d+)(.*)""")
    val WITH_MANA_VALUE_X_OTHER_THAN = Regex("""(.+) with mana value X other than (.+)""")
    // tdm chunk 06: "exile up to two target cards from a single graveyard" (CR 115: one owner)
    val FROM_A_SINGLE_GRAVEYARD = Regex("""[Ee]xile up to (\w+) target cards from a single graveyard\.""")
    // tdm chunk 06: "Activated abilities of artifacts, creatures, and planeswalkers can't be activated."
    val ACTIVATED_ABILITIES_OF_LIST_CANT = Regex("""Activated abilities of (.+) can't be activated\.""")
    // tdm chunk 06: "During your turn, this creature has base power and toughness 5/2."
    val DURING_YOUR_TURN_BASE_PT = Regex("""During your turn, (.+?) has base power and toughness (\d+)/(\d+)\.""")
    // tdm chunk 06: "up to one target player mills cards equal to this creature's power"
    val UP_TO_ONE_TARGET_PLAYER_MILLS_EQUAL = Regex("""[Uu]p to one target player mills cards equal to (.+)\.""")
    // tdm chunk 06: "return up to two other target nonland permanents to their owners' hands"
    val RETURN_UP_TO_OTHER_TARGETS_TO_HANDS = Regex("""[Rr]eturn up to (\w+) other target (.+?) to their owners' hands\.""")
    // tdm chunk 07: "Exile the top two cards of your library. Until the end of your next turn, you may play those cards."
    val EXILE_TOP_PLAY_UNTIL_NEXT_TURN = Regex("""Exile the top (\w+) cards of your library\. Until the end of your next turn, you may play those cards\.""")
    // tdm chunk 07: "Up to two target creatures each get +2/+1 until end of turn."
    val UP_TO_TARGETS_EACH_GET = Regex("""Up to (\w+) target (.+?) each get ([+-]\d+)/([+-]\d+) until end of turn\.""")
    // tdm chunk 07: "exile a card at random from your graveyard. You may play that card this turn."
    val EXILE_RANDOM_FROM_GRAVEYARD_PLAY = Regex("""[Ee]xile a card at random from your graveyard\. You may play that card this turn\.""")
    // tdm chunk 07: "Until end of turn, Sarkhan becomes a Dragon in addition to his other types and gains flying."
    val UNTIL_EOT_BECOMES_SUBTYPE_AND_GAINS = Regex("""Until end of turn, (.+?) becomes an? ([A-Z]\w+) in addition to (?:its|his|her|their) other types and gains (.+)\.""")
    // tdm chunk 07: "exile cards from the top of your library until you exile a nonland card. You may cast it without paying its mana cost if
    // that spell's mana value is 8 or less. If you don't, put that card into your hand."
    val EXILE_UNTIL_CAST_FREE_IF_OR_HAND = Regex("""[Ee]xile cards from the top of your library until you exile an? (.+?) card\. You may cast it without paying its mana cost if that spell's mana value is (\d+) or less\. If you don't, put that card into your hand\.""")
    // tdm chunk 07: "At the beginning of each combat, if …, this enchantment becomes a 3/3 Monk creature with haste in addition to its other types until
    // end of turn."
    val BECOMES_PT_CREATURE_WITH_IN_ADDITION = Regex("""(.+?) becomes an? (\d+)/(\d+) ([A-Z]\w*(?: [A-Z]\w*)*) creature(?: with (.+?))? in addition to its other types until end of turn\.""")
    // tdm chunk 08: "Creatures with power less than this creature's power can't block it."
    val POWER_LESS_THAN_CANT_BLOCK = Regex("""Creatures with power less than (.+?)'s power can't block (?:it|him|her)\.""")
    // tdm chunk 08: "Put a +1/+1 counter on target creature you control, then it deals damage equal to its power to target creature an opponent
    // controls."
    val COUNTER_THEN_DEALS_DAMAGE_EQUAL_POWER = Regex("""Put an? (\S+) counter on target (.+?)(?:, then it|\. Then that creature) deals damage equal to its power to target (.+)\.""")
    // tdm chunk 08: "you may mill cards equal to the number of lands you control"
    val MAY_MILL_EQUAL = Regex("""[Yy]ou may mill cards equal to (.+)\.""")
    // tdm chunk 08: "draw a card if you control a creature with a counter on it. If you don't draw a card this way, put a +1/+1 counter on this
    // creature."
    val DRAW_IF_ELSE_COUNTER = Regex("""[Dd]raw a card if (.+?)\. If you don't draw a card this way, put an? (\S+) counter on (.+)\.""")
    // tdm chunk 08: "look at the top card of your library. If it's a land card, you may reveal it and put it into your hand. If you don't put
    // the card into your hand, you may put it into your graveyard."
    // otj chunk 10: "If you don't put it into your hand, you may put it on the bottom of your library." (CR 401.4: bottom)
    val LOOK_TOP_LAND_HAND_ELSE_GRAVEYARD = Regex("""[Ll]ook at the top card of your library\. If it's an? (.+?) card, you may reveal it and put it into your hand\. If you don't put (?:the card|it) into your hand, you may put it (into your graveyard|on the bottom of your library)\.""")
    // tdm chunk 08: "Return up to two target permanent cards from your graveyard to your hand. Target player shuffles up to four target cards
    // from their graveyard into their library. Exile Rite of Renewal."
    val RETURN_AND_PLAYER_SHUFFLES = Regex("""Return up to (\w+) target (.+?) from your graveyard to your hand\. Target player shuffles up to (\w+) target cards from their graveyard into their library\. Exile (.+)\.""")
    // tdm chunk 08: "Whenever another nontoken creature you control enters, it endures X, where X is the number of counters on this creature."
    // (the X is read by [Sentences.instructions])
    // tdm chunk 09: "if it's your main phase, there is an additional combat phase after this phase followed by an additional main phase. When you
    // next attack this turn, untap each creature you control."
    val ADDITIONAL_COMBAT_THEN_WHEN_NEXT_ATTACK_UNTAP = Regex("""[Tt]here is an additional combat phase after this phase followed by an additional main phase\. When you next attack this turn, untap each creature you control\.""")
    // tdm chunk 09: "distribute three +1/+1 counters among one, two, or three target creatures you control"
    val DISTRIBUTE_COUNTERS_AMONG = Regex("""[Dd]istribute (\w+) (\S+) counters among (one, two, or three|any number of) target (.+?)\.""")
    // tdm chunk 09: "Destroy all creatures and enchantments. Draw a card for each permanent destroyed this way."
    val DESTROY_ALL_DRAW_FOR_EACH = Regex("""Destroy all (.+?)\. Draw a card for each permanent destroyed this way\.""")
    // tdm chunk 09: "it deals 3 damage to each creature and each planeswalker"
    val DEALS_DAMAGE_TO_EACH_AND_EACH = Regex("""(.+?) deals (X|\d+) damage to each (\w+) and each (\w+)\.""")
    // tdm chunk 09: "Double the power and toughness of target creature you control until end of turn. Then it fights up to one target creature an
    // opponent controls."
    val DOUBLE_PT_THEN_FIGHTS = Regex("""Double the power and toughness of target (.+?) until end of turn\. Then it fights up to one target (.+)\.""")
    val DOUBLE_PT_OF_EACH = Regex("""Double the power and toughness of each (.+?) until end of turn\.""")
    // tdm chunk 09: "draw a card and put two +1/+1 counters on Eshki Dragonclaw"
    val DRAW_AND_PUT_COUNTERS = Regex("""[Dd]raw a card and put (\w+) (\S+) counters on (.+)\.""")
    // tdm chunk 09: "copy the next spell you cast this turn when you cast it. You may choose new targets for the copy."
    val COPY_NEXT_SPELL_THIS_TURN = Regex("""[Cc]opy the next spell you cast this turn when you cast it\. You may choose new targets for the copy\.""")
    // tdm chunk 09: "Draw a card, then you may discard a card. When you discard a nonland card this way, Glacial Dragonhunt deals 3 damage to
    // target creature."
    // one chunk 09: "Draw a card. Then you may discard a nonland card. When you discard a card this way, …"
    val DRAW_MAY_DISCARD_WHEN_NONLAND_DAMAGE = Regex("""Draw a card(?:, then|\. Then) you may discard an? (?:(\w+) )?card\. When you discard an? (?:(\w+) )?card this way, (.+?) deals (\d+) damage to target (.+)\.""")
    // tdm chunk 09: "look at the top three cards of your library. You may put one of those cards back on top of your library. Put the rest into your
    // graveyard."
    val LOOK_MAY_ONE_BACK_REST_GRAVEYARD = Regex("""[Ll]ook at the top (\w+) cards of your library\. You may put one of those cards back on top of your library\. Put the rest into your graveyard\.""")
    // tdm chunk 09: "Look at the top four cards of your library. Put two of them into your hand and the rest into your graveyard."
    val LOOK_PUT_N_HAND_REST_GRAVEYARD = Regex("""[Ll]ook at the top (\w+) cards of your library\. Put (\w+) of them into your hand and the rest into your graveyard\.""")
    // tdm chunk 10: "This creature has hexproof as long as it hasn't dealt damage yet."
    val HAS_KEYWORD_AS_LONG_AS_NO_DAMAGE_YET = Regex("""(.+?) has (\w+) as long as it hasn't dealt damage yet\.""")
    // tdm chunk 10: "exile the top X cards of their library, where X is the amount of damage dealt. You may cast any number of spells with mana value
    // X or less from among them without paying their mana costs."
    val EXILE_TOP_X_OF_THEIR_CAST_ANY = Regex("""[Ee]xile the top X cards of their library, where X is the amount of damage dealt\. You may cast any number of spells with mana value X or less from among them without paying their mana costs\.""")
    // tdm chunk 10: "Return target creature card from your graveyard to your hand. Lie in Wait deals damage equal to that card's power to target
    // creature."
    val RETURN_THEN_DEALS_ITS_POWER = Regex("""Return target (.+?) from your graveyard to your hand\. (.+?) deals damage equal to that card's power to target (.+)\.""")
    // tdm chunk 10: "search your library for a black card, a green card, and a blue card. Put those cards into your graveyard, then shuffle."
    val SEARCH_COLORS_INTO_GRAVEYARD = Regex("""[Ss]earch your library for an? (\w+) card, an? (\w+) card, and an? (\w+) card\. Put those cards into your graveyard, then shuffle\.""")
    // tdm chunk 10: "When this creature enters, exile up to one other target creature you control until this creature leaves the battlefield."
    // + "Whenever this creature attacks, for each opponent, create a tapped token that's a copy of the exiled card attacking that opponent. At the
    // beginning of your next end step, sacrifice those tokens."
    val COPY_OF_EXILED_ATTACKING_EACH_OPPONENT = Regex("""[Ff]or each opponent, create a tapped token that's a copy of the exiled card attacking that opponent\. At the beginning of your next end step, sacrifice those tokens\.""")
    // tdm chunk 10: "put up to one target noncreature, nonland card from your graveyard on top of your library"
    val PUT_UP_TO_ONE_FROM_GRAVEYARD_ON_TOP = Regex("""[Pp]ut up to one target (.+?) from your graveyard on top of your library\.""")
    // tdm chunk 10: "you may discard your hand. If you do, draw cards equal to the number of spells you've cast this turn."
    val MAY_DISCARD_HAND_DRAW_EQUAL = Regex("""[Yy]ou may discard your hand\. If you do, draw cards equal to (.+)\.""")
    // tdm chunk 10: "If a creature you control that entered this turn would deal damage, it deals twice that much damage instead."
    val WOULD_DEAL_DAMAGE_TWICE = Regex("""If an? (.+?) would deal damage, it deals twice that much damage instead\.""")
    // tdm chunk 10: "Return target permanent card from your graveyard to the battlefield with a hexproof counter and an indestructible counter on it."
    val RETURN_WITH_TWO_COUNTERS = Regex("""Return target (.+?) from your graveyard to the battlefield with an? (\S+) counter and an? (\S+) counter on it\.""")
    // tdm chunk 10: "remove all counters from up to one target creature"
    val REMOVE_ALL_COUNTERS_FROM_UP_TO_ONE = Regex("""[Rr]emove all counters from up to one target (.+)\.""")
    // tdm chunk 11: "exile target nonland card with mana value 3 or less from your graveyard. Copy it, then you may cast the copy without paying its
    // mana cost."
    val EXILE_COPY_CAST_FREE = Regex("""[Ee]xile target (.+?) from your graveyard\. Copy it, then you may cast the copy without paying its mana cost\.""")
    // tdm chunk 11: "target instant or sorcery card in your graveyard gains harmonize until end of turn. Its harmonize cost is equal to its mana cost."
    val GAINS_HARMONIZE_EQUAL_MANA_COST = Regex("""[Tt]arget (.+?) gains harmonize until end of turn\. Its harmonize cost is equal to its mana cost\.""")
    // tdm chunk 11: "it deals 2 damage to any target and you gain 2 life. If a player is dealt damage this way, they discard a card."
    val DAMAGE_ANY_GAIN_IF_PLAYER_DISCARDS = Regex("""(.+?) deals (\d+) damage to any target and you gain (\d+) life\. If a player is dealt damage this way, they discard a card\.""")
    // tdm chunk 11: "Whenever one or more counters are put on a creature you control, if it's the first time counters have been put on that creature
    // this turn, put a +1/+1 counter on that creature."
    val FIRST_TIME_COUNTERS_THIS_TURN = Regex("""it's the first time (?:(\S+) )?counters have been put on that (?:creature|permanent) this turn""")
    // tdm chunk 11: "During your turn, spells you cast cost {1} less to cast for each creature you control with power 4 or greater."
    // mom chunk 01: also without "During your turn" ("Spells you cast cost {1} less to cast for each card your opponents have drawn this turn.")
    val DURING_YOUR_TURN_SPELLS_COST_LESS_FOR_EACH = Regex("""(During your turn, )?((?:.+? )?[Ss]pells you cast) cost (\{[^}]+\}) less to cast for each (.+)\.""")
    // tdm chunk 11: "Spells you cast have delve."
    val SPELLS_YOU_CAST_HAVE = Regex("""Spells you cast(?: from (exile|your hand))? have (\w+)\.""")
    // tdm chunk 11: "Whenever you cast a spell, you lose life equal to its mana value."
    val LOSE_LIFE_EQUAL_ITS_MV = Regex("""[Yy]ou lose life equal to its mana value\.""")
    // tdm chunk 11: "it deals X damage divided as you choose among any number of target creatures and/or planeswalkers your opponents control, where X
    // is the number of lands you control"
    // mh2 chunk 07: also a number ("it deals 4 damage divided as you choose among any number of target creatures and/or planeswalkers")
    val DIVIDED_AMONG_ANY_NUMBER = Regex("""(.+?) deals (X|\d+) damage divided as you choose among any number of target (.+)\.""")
    // tdm chunk 11: "You may cast sorcery spells and Dragon spells as though they had flash."
    val CAST_SPELLS_AS_THOUGH_FLASH = Regex("""You may cast (.+?) as though they had flash\.""")
    // tdm chunk 11: "create a 1/1 red Goblin creature token. It gains lifelink and haste until end of turn."
    val CREATE_IT_GAINS_UNTIL_EOT = Regex("""It gains (.+?) until end of turn\.""")
    // tdm chunk 11: "During your end step, Warrior tokens you control have "This token can't be sacrificed.""
    val DURING_YOUR_END_STEP_HAVE = Regex("""During your end step, (.+?) have "(.+)"""")
    // tdm chunk 12: "Equipped creature gets +2/+2 and has hexproof from monocolored."
    // tdm chunk 12: "Equip {4}. This ability costs {1} less to activate for each color of the creature it targets."
    val EQUIP_LESS_FOR_EACH_COLOR = Regex("""Equip (\{[^}]+\})\. This ability costs (\{[^}]+\}) less to activate for each color of the creature it targets\.""")
    // tdm chunk 12: "Each Dragon you control enters with an additional +1/+1 counter on it."
    val EACH_ENTERS_WITH_ADDITIONAL = Regex("""Each (.+?) enters with an additional (\S+) counter on it\.""")
    // tdm chunk 12: "you may search your library for a basic land card, reveal it, then shuffle and put that card on top. If you control a Dragon,
    // put that card onto the battlefield tapped instead."
    val SEARCH_TOP_OR_BATTLEFIELD_IF = Regex("""[Yy]ou may search your library for an? (.+?) card, reveal it, then shuffle and put that card on top\. If (.+?), put that card onto the battlefield tapped instead\.""")
    // tdm chunk 12: "Create three 1/1 red Warrior creature tokens. They gain menace and haste until end of turn."
    val THEY_GAIN_UNTIL_EOT = Regex("""They gain (.+?) until end of turn\.""")
    // tdm chunk 13: "Create a 4/4 Dragon creature token named Reliquary Dragon that's all colors. It has flying, lifelink, and "…""
    val CREATE_NAMED_ALL_COLORS = Regex("""Create an? (\d+)/(\d+) ([A-Z]\w*) creature token named (.+?) that's all colors\. It has (.+?), and "(.+)"""")
    // tdm chunk 13: Ugin's "−11": "Search your library for any number of colorless nonland cards, exile them, then shuffle. Until end of turn, you may cast
    // those cards without paying their mana costs."
    val SEARCH_ANY_EXILE_CAST_FREE = Regex("""Search your library for any number of (.+?) cards, exile them, then shuffle\. Until end of turn, you may cast those cards without paying their mana costs\.""")
    // tdm chunk 13: "Look at the top seven cards of your library. Put up to two noncreature, nonland permanent cards with mana value 3 or less from among
    // them onto the battlefield. Put the rest on the bottom of your library in a random order."
    val LOOK_PUT_UP_TO_ONTO_BATTLEFIELD_REST_BOTTOM = Regex("""Look at the top (\w+) cards of your library\. Put up to (\w+) (.+?) from among them onto the battlefield\. Put the rest on the bottom of your library in a random order\.""")
    // tdm chunk 13: "When you cast this spell, if you've cast another spell this turn, copy this spell."
    val COPY_THIS_SPELL = Regex("""[Cc]opy this spell\.""")
    // tdm chunk 13: "Activate only during your turn and only if a card left your graveyard this turn."
    val ACTIVATE_ONLY_DURING_YOUR_TURN_AND_IF = Regex("""(.+?)\. Activate only during your turn and only if (.+)\.""")
    // tdm chunk 13: "Put a +1/+1 counter on target nonlegendary creature you control. Each other creature you control becomes a copy of that creature
    // until end of turn."
    val EACH_OTHER_BECOMES_COPY_UNTIL_EOT = Regex("""Each other (.+?) becomes a copy of that creature until end of turn\.""")
    // tdm chunk 13: "At the beginning of your upkeep, mill three cards. Then if your library has no cards in it, exile this enchantment and put five
    // cards from your graveyard on top of your library in any order."
    val THEN_IF_LIBRARY_EMPTY_EXILE_AND_PUT = Regex("""Then if your library has no cards in it, exile (.+?) and put (\w+) cards from your graveyard on top of your library in any order\.""")
    // tdm chunk 13: Taigam's flurry: "copy it, then exile the spell you cast with four time counters on it. If it doesn't have suspend, it gains suspend."
    val COPY_IT_THEN_EXILE_WITH_TIME_COUNTERS_SUSPEND = Regex("""[Cc]opy it, then exile the spell you cast with (\w+) time counters on it\. If it doesn't have suspend, it gains suspend\.""")
    // tdm chunk 14: "for each color, put a +1/+1 counter on a Dragon you control of that color. If you put +1/+1 counters on five Dragons this way, you
    // win the game."
    val FOR_EACH_COLOR_COUNTER_WIN = Regex("""[Ff]or each color, put an? (\S+) counter on an? (\w+) you control of that color\. If you put (\S+) counters on five \w+ this way, you win the game\.""")
    // tdm chunk 14: Betor's thresholds: "draw a card. Then if creatures you control have total toughness 20 or greater, untap each creature you control.
    // Then if creatures you control have total toughness 40 or greater, each opponent loses half their life, rounded up."
    val THEN_IF_STEP = Regex("""Then if (.+?), (.+)""")
    // tdm chunk 14: New Way Forward
    val NEXT_TIME_SOURCE_OF_CHOICE_PREVENT_REFLECT = Regex("""The next time a source of your choice would deal damage to you this turn, prevent that damage\. When damage is prevented this way, (.+?) deals that much damage to that source's controller and you draw that many cards\.""")
    // tdm chunk 14: "Whenever this creature or another creature you control dies, if it had counters on it, put its counters on up to one target creature you
    // control."
    val PUT_ITS_COUNTERS_ON_UP_TO_ONE = Regex("""[Pp]ut its counters on up to one target (.+)\.""")
    // tdm chunk 14: "Magmatic Hellkite": "destroy target nonbasic land an opponent controls. Its controller searches their library for a basic land card,
    // puts it onto the battlefield tapped with a stun counter on it, then shuffles."
    // tdm chunk 11: 'During your end step, Warrior tokens you control have "This token can't be sacrificed."'
    val CANT_BE_SACRIFICED = Regex("""(.+?) can't be sacrificed\.""")
    // tdm chunk 12: "hexproof from monocolored" (CR 702.11d)
    val HEXPROOF_FROM = Regex("""[Hh]exproof from (.+)""")
    // tdm chunk 02: "If a creature attacking causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time."
    // one chunk 07: "dying" too
    val ATTACKING_CAUSES_TRIGGER_ADDITIONAL = Regex("""If an? (.+?) (attacking|dying) causes a triggered ability of an? (.+?) to trigger, that ability triggers an additional time\.""")
    // tdm chunk 10: "for each opponent, create a tapped token that's a copy of the exiled card attacking that opponent. At the beginning of your next end
    // step, sacrifice those tokens."
    // tdm chunk 14: "Sidisi": "Sacrifice a creature you control with mana value X other than Sidisi"
    val SACRIFICE_WITH_MANA_VALUE_X_OTHER_THAN = Regex("""Sacrifice an? (.+?) with mana value X other than (.+)""")
    // tdm chunk 13: "you cast a colorless spell"
    val YOU_CAST_COLORLESS = Regex("""you cast an? (colorless .+)""")
    // tdm chunk 11: "a counter is put on a creature you control", "one or more counters are put on a creature you control"
    val COUNTER_IS_PUT_ON = Regex("""(an? counter is|one or more counters are) put on (.+)""")
    // tdm chunk 14: "each opponent loses half their life, rounded up"
    val EACH_OPPONENT_LOSES_HALF = Regex("""[Ee]ach opponent loses half their life, rounded (up|down)\.""")
    // tdm chunk 06: "You may attach this Equipment to it." after a token is created
    val MAY_ATTACH_SELF_TO_IT = Regex("""You may attach (.+) to it\.""")
    // tdm chunk 09: "Put a +1/+1 counter on each of those creatures."
    val COUNTER_ON_EACH_OF_THOSE = Regex("""Put an? (\S+) counter on each of those creatures\.""")
    // tdm chunk 09: "If it was a token, draw a card."; lci chunk 03: "If it was tapped, create a Map token."
    val IF_IT_WAS_A_TOKEN = Regex("""If it was (a token|tapped), (.+)\.""")
    val DESTROY_ITS_CONTROLLER_SEARCHES = Regex("""[Dd]estroy target (.+?)\. Its controller searches their library for an? (.+?) card, puts it onto the battlefield tapped with an? (\S+) counter on it, then shuffles\.""")
}

/** tdm: nouns, conditions and values of Tarkir: Dragonstorm wordings, tried first by [Filters], [Conditions] and [Values]. */
internal object TdmPhrases {
    private val BASIC_TYPES_LIST = Regex("""basic ((?:[A-Z]\w+, )+)or ([A-Z]\w+)(?: cards?)?""")
    private val NAMED_OR_NAMED = Regex("""cards? named (.+?) or (.+)""")
    private val SPELLS_CAST_THIS_TURN = Regex("""the number of (other )?(\w+ )?spells (you've|they've) cast this turn""")
    private val OR_A_SPELL_YOU_CONTROL = Regex("""(.+? you control) or an? (\w+) spell you control""")
    // tdm chunk 13: "permanent that's one or more colors" (not colorless, CR 105.2c)
    private val ONE_OR_MORE_COLORS = Regex("""(.+?) that's one or more colors""")
    // tdm chunk 04: "you've cast both a creature spell and a noncreature spell this turn"
    private val CAST_BOTH_THIS_TURN = Regex("""you've cast both an? (.+?) spell and an? (.+?) spell this turn""")
    // tdm chunk 14: "creatures you control have total toughness 10 or greater"
    private val TOTAL_TOUGHNESS_OR_GREATER = Regex("""(.+?) have total (power|toughness) (\d+) or greater""")
    // tdm chunk 05: "there are three or more different kinds of counters among creatures you control"
    private val DIFFERENT_KINDS_OF_COUNTERS = Regex("""there are (\w+) or more different kinds of counters among (.+)""")

    fun noun(phrase: String): ObjectFilter? {
        // tdm chunk 02: "basic Plains, Swamp, or Forest card": basic, and any of the land types (CR 205.4a)
        BASIC_TYPES_LIST.matchEntire(phrase)?.let { m ->
            val types = m.groupValues[1].removeSuffix(", ").split(", ") + m.groupValues[2]
            return Filters.all(ObjectFilter.Super(arcanum.api.Supertype.Basic), ObjectFilter.Or(types.map { ObjectFilter.Sub(arcanum.api.SubtypeRef.Literal(arcanum.api.Subtype(it))) }))
        }
        // tdm chunk 04: "card named Stormbrood Orb or Rockhewn Dragon" (either name, CR 201.2)
        NAMED_OR_NAMED.matchEntire(phrase)?.let { m -> return ObjectFilter.Or(listOf(ObjectFilter.Named(m.groupValues[1]), ObjectFilter.Named(m.groupValues[2]))) }
        // tdm chunk 13: "creature you control or a creature spell you control" (a permanent or a spell, CR 110.1, 112.1)
        OR_A_SPELL_YOU_CONTROL.matchEntire(phrase)?.let { m ->
            val first = Filters.noun(m.groupValues[1]) ?: return null
            val spell = Filters.noun(m.groupValues[2] + " spell") ?: return null
            return ObjectFilter.Or(listOf(first, Filters.all(spell, ObjectFilter.ControlledBy(PlayerFilter.You))))
        }
        // tdm chunk 14: "creature card with mana value X plus 1" (X announced, CR 107.3a)
        Tdm.WITH_MANA_VALUE_X_PLUS.matchEntire(phrase)?.takeIf { it.groupValues[3].isEmpty() }?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.ManaValue(Comparison.Equal,
                Value.Sum(listOf(Value.Announced(name = "X"), Value.Fixed(m.groupValues[2].toInt())))))
        }
        // tdm chunk 13: "colorless spell", "colorless nonland cards" (CR 105.2c)
        if (phrase.startsWith("colorless ")) return Filters.all(ObjectFilter.Colorless, Filters.noun(phrase.removePrefix("colorless ")) ?: return null)
        // tdm chunk 11: "spells you cast" (spells a player cast are spells they control, CR 112.2)
        if (phrase == "spells you cast") return Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Spell), ObjectFilter.ControlledBy(PlayerFilter.You))
        ONE_OR_MORE_COLORS.matchEntire(phrase)?.let { m -> return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.Not(ObjectFilter.Colorless)) }
        // tdm chunk 11: "sorcery spells and Dragon spells": either
        if (phrase.contains(" spells and ") && phrase.endsWith(" spells")) {
            val parts = phrase.split(" and ").map { Filters.noun(it.removeSuffix("s").let { s -> if (s.endsWith(" spell")) s else "$s spell" }) ?: return null }
            return ObjectFilter.Or(parts)
        }
        return null
    }

    fun condition(text: String): Condition? {
        Tdm.WAS_BEHELD.matchEntire(text)?.let { return Condition.Paid(null, times = 1, of = Objects.Self) }
        CAST_BOTH_THIS_TURN.matchEntire(text)?.let { m ->
            val first = Filters.noun(m.groupValues[1] + " spell") ?: return null
            val second = Filters.noun(m.groupValues[2] + " spell") ?: return null
            return Condition.And(listOf(first, second).map { Condition.HappenedThisTurn(EventPattern.Cast(it, by = PlayerFilter.You), atLeast = 1, measure = Measure.Events) })
        }
        TOTAL_TOUGHNESS_OR_GREATER.matchEntire(text)?.let { m ->
            val property = if (m.groupValues[2] == "power") NumericProperty.Power else NumericProperty.Toughness
            return Condition.Compare(Value.Of(property, Filters.objects(m.groupValues[1]) ?: return null, Combine.Total), Comparison.AtLeast,
                Value.Fixed(m.groupValues[3].toInt()))
        }
        DIFFERENT_KINDS_OF_COUNTERS.matchEntire(text)?.let { m ->
            return Condition.Compare(Value.CountDistinct(Filters.objects(m.groupValues[2]) ?: return null, arcanum.api.SharedQuality.CounterKind), Comparison.AtLeast,
                Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
        }
        // tdm chunk 11: "it's the first time counters have been put on that creature this turn" (the counters of the event are the first: one such
        // event this turn)
        // mom chunk 11: "it's the first time +1/+1 counters have been put on that permanent this turn" (only counters of that kind count)
        Tdm.FIRST_TIME_COUNTERS_THIS_TURN.matchEntire(text)?.let { m ->
            val kind = if (m.groupValues[1].isEmpty()) null else Counters.kind(m.groupValues[1]) ?: return null
            return Condition.Compare(Value.EventsThisTurn(EventPattern.CountersPut(
                ObjectFilter.Is(Objects.EventObject(arcanum.api.EventRole.Object)), kind), Measure.Events), Comparison.Equal, Value.Fixed(1))
        }
        // tdm chunk 09: "it's your main phase" (CR 505.1)
        if (text == "it's your main phase") return Condition.And(listOf(Condition.YourTurn,
            Condition.Or(listOf(Condition.StepIs(TurnStep.PrecombatMain), Condition.StepIs(TurnStep.PostcombatMain)))))
        return null
    }

    fun value(text: String): Value? {
        // tdm chunk 08: "the number of counters on this creature" (every kind)
        if (text == "the number of counters on this creature") return Value.Of(NumericProperty.Counters(null), Objects.Self, Combine.Single)
        // tdm chunk 10: "the number of spells you've cast this turn" (CR 601.2i)
        // otj chunk 11: "the number of other spells you've cast this turn" (not this spell), "… noncreature spells they've cast this turn" (they: the
        // event's player)
        SPELLS_CAST_THIS_TURN.matchEntire(text)?.let { m ->
            val kind = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { Filters.noun(it.trim()) ?: return null }
            val spells = listOfNotNull(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)).takeIf { m.groupValues[1].isNotEmpty() }, kind)
            val by = if (m.groupValues[3] == "you've") PlayerFilter.You else PlayerFilter.Is(arcanum.api.Players.EventPlayer)
            return Value.EventsThisTurn(EventPattern.Cast(spells.singleOrNull() ?: if (spells.isEmpty()) ObjectFilter.Any else Filters.all(*spells.toTypedArray()), by = by),
                Measure.Events)
        }
        return null
    }
}
