package arcanum.parser

import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.EventPattern
import arcanum.api.Measure
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Value
import arcanum.api.Zone

/** Regular expressions of Marvel Super Heroes (msh) wordings read in [Sentences]. */
internal object Msh {
    // msh chunk 01: teamwork (CR 702.194) and power-up (CR 702.193)
    val BECOMES_TAPPED_TO_PAY_TEAMWORK = Regex("""(.+) becomes tapped to pay a teamwork cost""")
    val PUT_COUNTER_AND_COUNTER_ON = Regex("""Put an? (\S+) counter and an? (.+?) counter on (.+)\.""")
    val AS_LONG_AS_YOU_AND_HAVE = Regex("""As long as (.+), you and (.+) have (hexproof)\.""")
    val YOU_CAST_SPELL_THAT_TARGETS = Regex("""you cast an? (.*?)spell that targets (?:an? |one or more )?(.+)""")
    val PLAYER_CASTS_SPELL_THAT_TARGETS = Regex("""a player casts a spell that targets (.+)""")
    // bro chunk 06: also "During your turn, your opponents can't cast spells or activate abilities of artifacts, creatures, or enchantments."
    val OPPONENTS_CANT_CAST_DURING_YOUR_TURN = Regex("""(During your turn, y|Y)our opponents can't cast spells(?: or activate abilities of (.+?))?( during your turn)?\.""")
    val AS_LONG_AS_MAY_CAST_AS_FLASH = Regex("""As long as (.+), you may cast spells as though they had flash\.""")
    val TEAMWORK_INSTEAD = Regex("""If this spell was cast using teamwork, instead (.+)\.""")
    val TEAMWORK_IT_DEALS_INSTEAD = Regex("""If this spell was cast using teamwork, it deals (\d+) damage to that creature instead\.""")
    val TEAMWORK_ALSO_DEALS_TO_CONTROLLER = Regex("""If this spell was cast using teamwork, it also deals (\d+) damage to that creature's controller\.""")
    val TEAMWORK_ALSO_GAINS = Regex("""If this spell was cast using teamwork, that creature also gains (.+) until end of turn\.""")
    val COUNTER_UNLESS_PAYS_INSTEAD_IF_TEAMWORK = Regex("""Counter target spell unless its controller pays (\{[^}]+\})\. Counter that spell unless its controller pays (\{[^}]+\}) instead if this spell was cast using teamwork\.""")
    val CHOOSE_TARGET_TEAMWORK_INSTEAD_RETURN = Regex("""Choose target (.+?)\. If this spell was cast using teamwork, instead choose target (.+?)\. Return the chosen card to the battlefield\.""")
    val REVEAL_PUT_FROM_AMONG_TEAMWORK = Regex("""Reveal the top (\w+) cards of your library\. You may put an? (.+?) from among them onto the battlefield\. If this spell was cast using teamwork, put any number of (.+?) from among them onto the battlefield instead\. Put the rest into your graveyard\.""")

    // msh chunk 02: connive (CR 701.50)
    val CONNIVES = Regex("""(.+?) connives\.""")
    val MAY_HAVE_IT_CONNIVE = Regex("""[Yy]ou may have (it|that creature) connive\.""")
    // lci chunk 03: "If a creature you control would explore, instead you scry 1, then that creature explores."
    val WOULD_CONNIVE_INSTEAD_DRAW = Regex("""If (.+) would (connive|explore), instead you (.+?), then that creature \2s\.""")

    // msh chunk 03: plan counters
    val NTH_COUNTER_PUT_ON = Regex("""the (\w+) (\S+) counter is put on (.+)""")
    val SACRIFICE_IT_WHEN_YOU_DO = Regex("""[Ss]acrifice it\. When you do, (.+)""")
    val SACRIFICE_IT_AND = Regex("""[Ss]acrifice it(?:,| and) (.+)""")

    // msh chunk 04: Heroes and Villains
    val ATTACKED_WITH_THIS_TURN = Regex("""you attacked with an? (.+) this turn""")
    val ENTERED_UNDER_YOUR_CONTROL_THIS_TURN = Regex("""(an?|another) (.+) entered the battlefield under your control this turn""") // mom: another
    val HAS_COUNTER_ON = Regex("""(.+) has an? (\S+) counter on (?:it|him|her|them)""")
    val OPPONENT_HAS_CAST_THIS_TURN = Regex("""an opponent has cast a spell this turn""")
    val POWER_AT_MOST = Regex("""(?:its|his|her|(.+)'s) power is (\d+) or (less|greater)""")
    val IS_ATTACKING = Regex("""(?:it's|he's|she's|(.+) is) attacking""")
    val IS_EQUIPPED = Regex("""(?:it's|he's|she's|(.+) is) equipped""")
    val YOUVE_PUT_COUNTERS_ON_THIS_TURN = Regex("""you've put one or more (\S+) counters on (.+) this turn""")
    val FEWER_THAN_CARDS_IN_HAND = Regex("""you have fewer than (\w+) cards in hand""")
    val CANT_BE_BLOCKED_IF = Regex("""(.+) can't be blocked if (.+)\.""")
    val AS_LONG_AS_GETS_AND_ALL_TYPES = Regex("""As long as (.+), (.+?) gets \+(\d+)/\+(\d+) and is all creature types\.""")
    val CANT_ATTACK_YOU_OR_BLOCK = Regex("""(.+) can't attack you(?: or block (.+))?\.""") // rvr chunk 04: also "Creatures can't attack you."
    val PREVENT_ALL_DAMAGE_TO_SELF = Regex("""Prevent all damage that would be dealt to (.+)\.""")
    val DAMAGE_DEALT_OTHER_HEALED = Regex("""If damage would be dealt to (.+?), instead that damage is dealt, but all other damage already dealt to (?:it|him|her) is healed\.""")
    val OPENING_HAND_BEGIN = Regex("""If (.+) is in your opening hand, you may begin the game with (?:it|him|her) on the battlefield\.""")
    val GETS_FOR_EACH_ATTACHED = Regex("""(.+?) gets \+(\d+)/\+(\d+) for each (.+?) attached to (?:it|him|her)\.""")
    val MAXIMUM_HAND_SIZE_IS = Regex("""Your maximum hand size is (\w+)\.""")

    // msh chunk 05: counters, targets and copies
    val YOU_PUT_ONE_OR_MORE_ON_ONE_OR_MORE = Regex("""you put one or more (\S+) counters on one or more (.+)""")
    val YOU_PUT_A_COUNTER_ON = Regex("""you put an? (\S+) counter on (.+)""")
    val IS_RETURNED_TO_HAND = Regex("""(.+) is returned to its owner's hand""")
    val PLAYER_OR_PERMANENT_BECOMES_TARGET_OF_ABILITY = Regex("""a player or permanent becomes the target of an ability you control""")
    val ONE_OR_MORE_BECOME_TAPPED = Regex("""one or more (.+) become tapped""")
    val BECOMES_TAPPED = Regex("""(.+) becomes tapped""")
    val YOU_CAST_FROM_YOUR_HAND = Regex("""you cast an? (\w+) spell from your hand""")
    val PLAYER_DRAWS_NTH = Regex("""a player draws their (\w+) card each turn""")
    val ONE_OR_MORE_DEAL_DAMAGE_TO_A_PLAYER = Regex("""one or more (.+) deal damage to a player""")
    val EACH_YOUVE_PUT_COUNTERS_ON_HAS = Regex("""Each (.+) that you've put one or more (\S+) counters on this turn has ([a-z ,]+)\.""")
    val COUNTERS_PLUS_ONE = Regex("""If you would put one or more counters on (.+), put that many plus one of each of those kinds of counters on that permanent instead\.""")
    val COPY_TARGET_ABILITY = Regex("""Copy target activated or triggered ability you control from an? (\w+) source\. You may choose new targets for the copy\.""")
    val CAST_WITH_MANA_SYMBOLS_CREATE_THAT_MANY = Regex("""Whenever you cast an? (\w+) spell with one or more (white|blue|black|red|green) mana symbols in its mana cost, create that many (.+) tokens\.""")
    val FOR_EACH_CREATE_COPY_NOT_LEGENDARY = Regex("""For each (.+), create a token that's a copy of (?:that (creature|permanent)|it)(?:, except (.+))?\.""") // mh3 chunk 09: "it", other exceptions // inr chunk 09: permanent, no exception
    val TARGET_BECOMES_COPY_OF_SECOND = Regex("""Target (.+) becomes a copy of a second target (.+) until end of turn, except it isn't legendary\.""")
    val MAY_PUT_FROM_HAND_IF_EQUIPMENT_ATTACH = Regex("""[Yy]ou may put an? (.+) from your hand onto the battlefield\. If it's an? (\w+), attach it to (.+)\.""")
    val UNTIL_EOT_TARGET_BECOMES_ARTIFACT_CREATURE = Regex("""Until end of turn, target (.+) becomes an artifact creature with base power and toughness (\d+)/(\d+) and gains (.+)\.""")
    val ATTACH_IT_TO = Regex("""Attach it to (.+)\.""")
    val MAY_HAVE_DEAL_THAT_MUCH = Regex("""[Yy]ou may have (.+) deal that much damage to any target\.""")
    val CREATES_NAMED_TOKEN = Regex("""(Target opponent creates|target opponent creates|Target player creates|target player creates|Create|create|You create) ([A-Z].+?, a legendary .+ creature token.*)\.""")
    val NAMED_LEGENDARY_TOKEN = Regex("""(.+?), a legendary (\d+)/(\d+) (\w+(?: and \w+)?) ([A-Z][A-Za-z ]+?) creature token(?: with (.+))?""")
    val COUNTED_TOKEN_WITH = Regex("""(a|an|one|two|three|four|five) ((?:\d+/\d+|X/X) .+? creature) tokens? with (.+)""")

    // msh chunk 03: harness and ∞ (CR 701.64, 702.186)
    val INFINITY = Regex("""∞ — (.+)""")
    val HARNESS = Regex("""Harness (.+)\.""")
    val EXILE_THEN_RETURN = Regex("""[Ee]xile (up to one )(other )?target (.+?), then return (?:that card|it) to the battlefield( tapped)? under its owner's control\.""")
    val AND_COUNTER_ON_SELF = Regex("""(.+?),? and (?:put )?an? (\S+) counter on (this \w+)\.""")

    // msh chunk 06: Equipment and Auras
    val EQUIP_WORTHY = Regex("""Equip worthy (\{[^}]+\})""")
    val ENCHANTED_GETS_HAS_AND_IS = Regex("""Enchanted creature gets \+(\d+)/\+(\d+), has ([a-z ,]+), and is a (legendary )?([A-Z]\w+) in addition to its other types\.""")
    val ENCHANTED_LOSES_ALL_CANT_UNTAP = Regex("""Enchanted creature loses all abilities and can't become untapped\.""")
    val DOUBLE_DAMAGE_EQUIPPED_WOULD_DEAL = Regex("""Double all damage equipped creature would deal\.""")
    val ATTACH_ANY_NUMBER_TO_IT = Regex("""[Aa]ttach any number of target (.+) to it\.""")
    val SACRIFICE_ATTACHED_TO_SELF = Regex("""Sacrifice an? (.+?) attached to (.+)""")
    val EXILE_UNTIL_LEAVES_ENCHANTED_BECOMES_COPY = Regex("""Exile up to one target (.+?) other than enchanted creature until (.+?) leaves the battlefield\. Enchanted creature becomes a copy of that creature until (.+?) leaves the battlefield\.""")

    // msh chunk 07: spells
    val TARGET_PLAYER_CREATES = Regex("""Target player creates (.+)\.""")
    val RETURN_ONE_OR_TWO = Regex("""Return one or two target (.+) to their owners' hands\.""")
    val SPELLS_WITH_MANA_VALUE_COST_X_LESS_POWER = Regex("""(.+ spells you cast) with mana value (\d+) or greater cost \{X\} less to cast, where X is (.+)'s power\.""")
    val NONCOMBAT_DAMAGE_PLUS_POWER = Regex("""If a source you control would deal noncombat damage to an opponent or a permanent an opponent controls, instead it deals that much damage plus X, where X is (.+)'s power\.""")
    val EXILE_HAND_DRAW_PLAY = Regex("""Exile all (?:the )?cards from your hand, then draw that many cards\. Until the end of your next turn, you may play cards exiled this way\.""")
    val WHEN_YOU_NEXT_CAST_COPY = Regex("""When you next cast an? (.+?) spell with mana value less than or equal to (.+)'s power this turn, copy that spell\. You may choose new targets for the copy\.""")
    val COST_OR_COST = Regex("""((?:Sacrifice|Discard|Exile|Pay|sacrifice|discard|exile|pay) .+?) or ((?:sacrifice|discard|exile|pay) .+)""")
    val GET_COUNTERS = Regex("""Get (\w+) (\S+) counters""")

    // msh chunk 08: green
    val IF_THAT_CREATURE_IS_COUNTERS_INSTEAD = Regex("""If that creature is (?:an? )?(.+?), put (\w+) \+1/\+1 counters on it instead\.""")
    val UNTIL_EOT_SELF_BECOMES_SUBTYPES = Regex("""Until end of turn, (.+?) becomes an? ([A-Z]\w+(?: [A-Z]\w+)*) with base power and toughness (\d+)/(\d+) and gains (.+)\.""")
    val ACTIVATE_AS_THOUGH_HASTE = Regex("""You may activate abilities of (.+) as though those creatures had haste\.""")
    val REMOVE_ANY_NUMBER_OF_COUNTERS = Regex("""Remove any number of (\S+) counters from (.+)""")
    val CHOOSE_TARGET_RETURN_TO_HAND = Regex("""Choose target (.+)\. Return it to your hand\.""")
    val MAY_PUT_FROM_HAND_IF_YOU_DONT = Regex("""You may put an? (.+) from your hand onto the battlefield\. If you don't, (.+)""")
    val OWNER_PUTS_SECOND_OR_BOTTOM = Regex("""The owner of target (.+?) puts it into their library second from the top or on the bottom\. (Then .+)""")

    // msh chunk 01: power-up
    val LABEL_ADDITIONAL_TIME = Regex("""Each power-up ability of (.+) can be activated an additional time\.""")
    val POWER_UP_ABILITIES_COST_LESS = Regex("""Power-up abilities of (.+) cost (\{\d+\}) less to activate\.""")
    val IF_ATTACKING_UNTAP_EXTRA_COMBAT = Regex("""If it's attacking, untap it and there is an additional combat phase after this phase\.""")

    // msh chunk 09: more wordings
    val EXILE_RETURN_AT_NEXT_END_STEP = Regex("""[Ee]xile (another )?target (.+?)\. Return (?:that card|it) to the battlefield under (its owner's|your) control at the beginning of the next end step\.""")
    // woe chunk 07: also "… spells you cast that have an Adventure"
    val SPELLS_YOU_CAST_COST_LESS = Regex("""(.+ spells you cast(?: that [^.]+?)?) cost (\{\d+\}) less to cast\.""")
    // woe chunk 08: also "Spells you cast this turn that are black and/or red cost {X} less to cast, where X is …"; mh3 chunk 09: "Until your
    // next turn, … spells you cast cost …"
    val SPELLS_YOU_CAST_THIS_TURN_COST_LESS = Regex("""(Until your next turn, )?((?:.+ )?[Ss]pells you cast)( this turn)?(?: that are ([a-z]+(?: and/or [a-z]+)*))? cost (\{\d+\}|\{X\}) less to cast(?:, where X is (.+?))?\.""")
    val TAKE_EXTRA_TURN = Regex("""Take an extra turn after this one\.""")
    val DURING_THAT_TURN_CANT_ACTIVATE = Regex("""During that turn, power-up abilities can't be activated\.""")
    val PUT_X_COUNTERS_ON = Regex("""Put X (\S+) counters on (.+)\.""")
    val ADD_MANA_CANT_BE_SPENT = Regex("""Add (.+)\. This mana can't be spent to cast (?:an? (.+) spell|(.+) spells)\.""")
    val CREATE_THEN = Regex("""(Create .+? tokens?(?: with [a-z ,]+)?), then (creatures you control get .+)\.""")
    val DRAW_FOR_EACH_DISCARDED = Regex("""Draw a card for each card you've discarded this turn\.""")
    val IS_A_IN_ADDITION = Regex("""That creature is an? ([A-Z]\w+) in addition to its other types\.""")
    val THE_NUMBER_ATTACHED_TO = Regex("""the number of (.+?) attached to (?:it|him|her|(.+))""")
    val FIGHTS_UP_TO_ONE_OTHER = Regex("""(.+?) fights up to one other target (.+)\.""")
    val THAT_PLAYER_DRAWS = Regex("""That player draws (a|\w+) cards?\.""")
    val RETURN_TAPPED_AND_ATTACKING_WITH_COUNTER = Regex("""[Rr]eturn target (.+?) to the battlefield tapped and attacking with an? (\S+) counter on it\.""")
    val REVEALS_NUMBER_YOU_CHOOSE_DISCARDS = Regex("""Target player reveals a number of cards from their hand equal to one plus (.+?)\. You choose one of them\. That player discards that card\.""")
    val ATTACH_UP_TO_ONE_TO_TARGET = Regex("""[Aa]ttach up to one target (.+?) to (?:target (.+)|that creature)\.""")
    val DEALS_DIVIDED_ONE_OR_TWO = Regex("""(.+?) deals (\d+) damage divided as you choose among one or two targets\.""")
    val GAIN_CONTROL_VILLAIN_INSTEAD = Regex("""Gain control of target (.+?) until end of turn\. If you control an? (.+?) with greater mana value than that creature, gain control of that creature until the end of your next turn instead\. Untap that creature\. It gains (.+) until end of turn\.""")
    val GAINS_QUOTED_UNTIL_EOT = Regex("""(.+?) gains "(.+)" until end of turn\.""")
    val DEALS_DAMAGE_EQUAL_TO_ANY_OTHER = Regex("""(.+?) deals damage equal to (.+?) to any other target\.?""")
    val COPY_IT_X_TIMES = Regex("""Copy it X times\. You may choose new targets for the copies\.""")
    val CANT_BE_BLOCKED_EXCEPT_BY = Regex("""[Tt]arget (.+?) can't be blocked this turn except by (.+)\.""")
    val ATTACHED_GAINS = Regex("""(?:[Ee]nchanted|[Ee]quipped) creature gains (.+) until end of turn\.""")
    val EXILE_FROM_GRAVEYARD_PLAY_UNTIL_NEXT_TURN = Regex("""[Ee]xile target (.+?) from your graveyard\. Until the end of your next turn, you may play that card\.""")
    val DEALS_DAMAGE_EQUAL_SPELL_MANA_VALUE = Regex("""(.+?) deals damage equal to that spell's mana value to any target\.""")
    val CREATE_TOKEN_OR_TOKEN = Regex("""[Cc]reate an? (\w+) token or an? (\w+) token\.""")
    val DOUBLE_TARGET_PT = Regex("""Double target (.+?)'s power and toughness until end of turn\.""")
    val CHOOSE_TARGET_DOUBLE_PT_GAINS = Regex("""Choose target (.+?)\. Until end of turn, double its power and toughness and it gains (.+)\.""")
    val LOSES_ALL_ABILITIES_AND_GAINS = Regex("""It loses all abilities and gains (.+)\.""")
    val CHOOSE_UP_TO_THAT_MANY_TARGETS_COUNTER = Regex("""Choose up to that many target (.+?)\. Put an? (\S+) counter on each of them\.""")
    val HAS_GREATER_POWER_OR_TOUGHNESS = Regex("""(?:it|that creature) has greater power or toughness than (.+)|its power is greater than (.+?)'s power or its toughness is greater than \2's toughness""") // mkm chunk 02
    val FIRST_TIME_TAPPED_THIS_TURN = Regex("""it's the first time that creature has become tapped this turn""")
    val REMOVE_A_COUNTER_IF_YOU_DO = Regex("""Remove a counter from (.+?)\. If you do, (.+)""")
    val ONE_MORE_OF_EACH_KIND = Regex("""For each kind of counter on target (.+?), give that (.+?) another counter of that kind\.""")
    val MAY_PUT_FROM_AMONG_MILLED = Regex("""(You may put|Put) (an?|up to \w+) (.+?) from among (?:the milled cards|those cards|them) (into your hand|onto the battlefield)\.""")
    val TARGET_PLAYER_GAINS_SEARCHES = Regex("""Target player (?:gains (\d+) life, then )?searches their library for an? (.+?), puts it onto the battlefield tapped, then shuffles\.""")
    val PUT_COUNTER_ON_UP_TO_ONE = Regex("""Put an? (\S+) counter on up to one target (.+)\.""")
    val NEXT_SPELL_WITHOUT_PAYING = Regex("""The next (.+?) spell you cast this turn can be cast without paying its mana cost\.""")
    val BECOMES_COPY_EXCEPT_PT_IN_ADDITION = Regex("""[Uu]ntil your next turn, (.+?) becomes a copy of up to one target (.+?), except its name is (.+?), it's a legendary (\d+)/(\d+) ([A-Z][A-Za-z ]+?) creature in addition to its other types, and it has (.+)\.""")
    val BECOMES_COPY_EXCEPT_REPLACED = Regex("""[Uu]ntil your next turn, (.+?) becomes a copy of up to one target (.+?), except its name is (.+?) and it's a legendary ([A-Z][A-Za-z ]+?) creature\.""")
    val PUT_COUNTER_ON_IT_FOR_EACH_THEN = Regex("""Put an? (\S+) counter on it for each (\S+) counter on (.+?), then put an? (\S+) counter on (.+)\.""")
    val MAY_DRAW_FOR_EACH_IF_YOU_DO = Regex("""You may draw a card for each (.+?)\. If you do, (.+)""")
    val CREATE_THAT_MANY = Regex("""Create that many (.+) tokens\.""")
    val DEALS_TO_EACH_AND_EACH_OPPONENT = Regex("""(.+?) deals (\d+) damage to each (.+?) and each opponent\.""")
    val CREATE_TOKEN_FOR_EACH = Regex("""Create an? (\w+) token for each (.+)\.""")
    val CHOOSE_OPPONENT_AND_CREATURE_EXILE_UNTIL = Regex("""Choose target opponent and up to one target (.+?) they control\. They reveal their hand\. You may exile a (.+?) from their hand or the chosen creature until (.+?) leaves? the battlefield\.""")
    val MAY_EXILE_TOP_IF_HERO_PLAY = Regex("""You may exile the top card of your library\. If that card is an? (.+?), (.+?) gets \+(\d+)/\+(\d+) until end of turn\. You may play that card this turn\.""")
    val ASSIGN_DAMAGE_BY_TOUGHNESS = Regex("""[Uu]ntil end of turn, (.+) assign combat damage equal to their toughness rather than their power\.""")
    val BASE_PT_BECOME_THEY_GAIN = Regex("""[Uu]ntil end of turn, (.+?)'s base power and toughness become (\d+)/(\d+) and they gain (.+)\.""")
    val MAY_CHOOSE_NEW_TARGETS_FOR_THAT_SPELL = Regex("""You may choose new targets for that spell\.""")
    val CANT_BECOME_UNTAPPED_AS_LONG_AS_CONTROL = Regex("""That creature can't become untapped for as long as you control (.+)\.""")
    val THOSE_CREATURES_GAIN = Regex("""[Tt]hose creatures gain (.+) until end of turn\.""")
    val GAIN_CONTROL_TOTAL_MANA_VALUE = Regex("""Gain control of up to (\w+) target (.+?) with total mana value (\d+) or less for as long as (.+?) remains on the battlefield\.""")
    val CHOOSE_ODD_OR_EVEN = Regex("""Choose odd or even\.""")
    val DESTROY_EACH_OF_CHOSEN_QUALITY = Regex("""Destroy each (.+?) with mana value of the chosen quality\.""")
    val SEARCH_LIBRARY_AND_GRAVEYARD_X_COUNTERS = Regex("""Search your library and/or graveyard for an? (.+?) with mana value X or less and put it onto the battlefield with X additional (\S+) counters on it\. If X is (\d+) or greater, it gains (.+) until end of turn\. If you search your library this way, shuffle\.""")
    val RETURN_SELF_WITH_COUNTER_MAY_ATTACH = Regex("""Return this card from your graveyard to the battlefield with an? (\S+) counter on it\. Then you may attach an? (.+?) to it\.""")
    val EXILE_ALL_EACH_PLAYER_PUTS_RETURN = Regex("""Exile all (.+?)\. Each player may put any number of (.+?) from their hand onto the battlefield\. Then put all cards exiled this way into their owners' hands\. Exile (.+)\.""")
    val LOOK_CAST_GREATEST_POWER_REST_BOTTOM = Regex("""[Ll]ook at the top (\w+) cards of your library\. You may cast a spell from among them with mana value less than or equal to the greatest power among (.+?) without paying its mana cost\. Put the rest on the bottom of your library in a random order\.""")
    val IF_NOT_CREATURE_BECOMES = Regex("""If (.+?) isn't a creature, it becomes an? (\d+)/(\d+) ([A-Z][A-Za-z ]+?) artifact creature with (\w+) and "(.+)" until end of turn\.""")
    val ADAPTOID = Regex("""Choose another target creature\. If that creature has (\w+) and (.+?) doesn't, put an? \1 counter on \2\. Do the same for (.+)\.""")
    val FEWER_THAN_DRAW_DIFFERENCE = Regex("""At the beginning of your end step, if you have fewer than (\w+) cards in hand, draw cards equal to the difference\.""")
    val PAY_CREATE_COPY_IF_NOT_CREATURE = Regex("""[Yy]ou may pay (\{[^}]+\})\. If you do, create a token that's a copy of it\. If the token isn't a creature, it becomes an? (\d+)/(\d+) ([A-Z][A-Za-z ]+?) creature in addition to its other types\.""")
    val LOOK_REVEAL_AND_PUT_REST_ANY_ORDER = Regex("""Look at the top (\w+) cards of your library\. You may reveal an? (.+?) from among them and put it into your hand\. Put the rest on the bottom of your library in any order\.""")
    val COUNTERS_THEN_LOOK_PUT_TRANSFORM = Regex("""Put (\w+) (\S+) counters on (.+?), then look at the top (\w+) cards of your library\. You may put an? (.+?) from among them onto the battlefield\. If it's a double-faced card, you may transform it\. Put the rest on the bottom of your library in a random order\.""")
    val DRAW_THEN_GAINS_QUOTED = Regex("""Draw a card\. Until end of turn, (.+?) gains "(.+)"""")
    val BASE_POWER_EQUAL_TO = Regex("""(.+?)'s base power is equal to (.+)\.""")
    val BOAST_COPY_CAST = Regex("""Copy those exiled cards\. You may cast up to (\w+) of the copies without paying their mana costs\.""")
    val EXILE_BLACK_CARDS_SYMBOLS = Regex("""Exile any number of (\w+) cards from your graveyard with (\w+) or more (\w+) mana symbols among their mana costs""")
    val EXILES_UNTIL_NONLAND_MAY_COUNTER_OR_CAST = Regex("""[Tt]hat player exiles cards from the top of their library until they exile an? (.+?)\. You may put an? (\S+) counter on (.+?)\. If you don't, you may cast the exiled (.+?) until end of turn and mana of any type can be spent to cast that spell\.""")
    val OPPONENT_EXILES_TOP_MAY_CAST_UP_TO = Regex("""[Tt]arget opponent exiles the top (\w+) cards of their library\. You may cast up to (\w+) spells from among the exiled cards without paying their mana costs\.""")
    val COSTS_LESS_IF_TARGETS = Regex("""This ability costs (\{\d+\}) less to activate if it targets an? (.+?)\.""")
    val PAY_UP_TO_TIMES_CHOOSE_THAT_MANY = Regex("""[Ww]henever (.+?) becomes tapped, you may pay (\{[^}]+\}) up to (\w+) times\. When you do, choose up to that many —""")
    val ENTERS_CHOOSE_UP_TO_X = Regex("""When (.+?) enters, choose up to X —""")
    val CHOOSE_UP_TO_RETURN_THOSE = Regex("""Choose up to (\w+)\. Return those cards from your graveyard to your hand\.""")
    val CONTROL_OPPONENT_NEXT_TURN = Regex("""[Yy]ou control target opponent during their next turn\.""")
}

/** Conditions, events and values of Marvel Super Heroes (msh) wordings. */
internal object MshPhrases {
    /** "this spell was cast using teamwork": its optional cost was paid (CR 702.194b). */
    val teamwork: Condition = Condition.Paid(arcanum.api.Keyword.Teamwork.Kind, times = 1, of = Objects.Self)

    fun condition(text: String, self: Set<String>): Condition? {
        fun isSelf(t: String) = t in self || t.replaceFirstChar { it.lowercase() } in self
        if (text == "this spell was cast using teamwork") return teamwork
        // msh chunk 04: "you attacked with a Hero this turn" (CR 508.1)
        Msh.ATTACKED_WITH_THIS_TURN.matchEntire(text)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return Condition.HappenedThisTurn(EventPattern.Attack(Filters.all(filter, ObjectFilter.ControlledBy(PlayerFilter.You))), atLeast = 1, measure = Measure.Events)
        }
        // msh chunk 04: "a Hero entered the battlefield under your control this turn"
        // mom chunk 10: "another Knight entered the battlefield under your control this turn" (not this object)
        Msh.ENTERED_UNDER_YOUR_CONTROL_THIS_TURN.matchEntire(text)?.let { m ->
            val noun = Filters.noun(m.groupValues[2]) ?: return null
            val filter = if (m.groupValues[1] == "another") Filters.all(ObjectFilter.Not(ObjectFilter.Is(arcanum.api.Objects.Self)), noun) else noun
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(Filters.all(filter, ObjectFilter.ControlledBy(PlayerFilter.You)), to = Zone.Battlefield),
                atLeast = 1, measure = Measure.Events)
        }
        // msh chunk 04: "Captain America has a shield counter on him"
        Msh.HAS_COUNTER_ON.matchEntire(text)?.takeIf { isSelf(it.groupValues[1]) }?.let { m ->
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(Counters.kind(m.groupValues[2]) ?: return null, atLeast = 1))
        }
        // msh chunk 04: "an opponent has cast a spell this turn"
        if (Msh.OPPONENT_HAS_CAST_THIS_TURN.matches(text))
            return Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Opponent), atLeast = 1, measure = Measure.Events)
        // msh chunk 04: "her power is 1 or less", "Kang's power is 4 or greater"
        Msh.POWER_AT_MOST.matchEntire(text)?.takeIf { it.groupValues[1].isEmpty() || isSelf(it.groupValues[1]) }?.let { m ->
            val comparison = if (m.groupValues[3] == "less") Comparison.AtMost else Comparison.AtLeast
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.Power(comparison, Value.Fixed(m.groupValues[2].toInt())))
        }
        // msh chunk 04: "he's attacking"
        Msh.IS_ATTACKING.matchEntire(text)?.takeIf { it.groupValues[1].isEmpty() || isSelf(it.groupValues[1]) }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(arcanum.api.Status.Attacking))
        }
        // msh chunk 04: "he's equipped" (CR 301.5)
        Msh.IS_EQUIPPED.matchEntire(text)?.takeIf { it.groupValues[1].isEmpty() || isSelf(it.groupValues[1]) }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.Attached(attachments = ObjectFilter.Sub(arcanum.api.Subtype("Equipment"))))
        }
        // msh chunk 04: "you've put one or more +1/+1 counters on Beast this turn" (by your spells and abilities)
        Msh.YOUVE_PUT_COUNTERS_ON_THIS_TURN.matchEntire(text)?.takeIf { isSelf(it.groupValues[2]) }?.let { m ->
            return Condition.HappenedThisTurn(EventPattern.CountersPut(ObjectFilter.Is(Objects.Self), Counters.kind(m.groupValues[1]) ?: return null,
                causedBy = ObjectFilter.ControlledBy(PlayerFilter.You)), atLeast = 1, measure = Measure.Events)
        }
        // msh: "you have fewer than ten cards in hand"
        Msh.FEWER_THAN_CARDS_IN_HAND.matchEntire(text)?.let { m ->
            return Condition.Compare(Value.HandSize(Players.You), Comparison.Less, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
        }
        return null
    }

    /** Worthy: legendary, not a Villain, and red and/or white (CR 700.16). */
    val worthy: ObjectFilter = ObjectFilter.And(listOf(ObjectFilter.Super(arcanum.api.Supertype.Legendary),
        ObjectFilter.Not(ObjectFilter.Sub(arcanum.api.Subtype("Villain"))),
        ObjectFilter.Or(listOf(ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(Color.Red)), ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(Color.White))))))

    private val ATTACHED_YOU_CONTROL = Regex("""(equipped|enchanted) (creature|permanent) you control""")

    /** msh nouns, tried first in [Filters.noun]. */
    fun noun(phrase: String): ObjectFilter? {
        // msh chunk 06: "creature you control that's attacking alone" (CR 506.5)
        ATTACKING_ALONE.matchEntire(phrase)?.let { m -> return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.AttackingAlone) }
        // msh chunk 02: "equipped creature you control" (a creature with an Equipment attached, CR 301.5); woe chunk 01: "enchanted creature you
        // control", "enchanted permanent you control" (one with an Aura attached, CR 303.4; read as written before the eoe rewrite of "enchanted
        // permanent" to "enchanted creature")
        ATTACHED_YOU_CONTROL.matchEntire(phrase)?.let { m ->
            val noun = if (m.groupValues[2] == "creature") ObjectFilter.Type(arcanum.api.CardType.Creature) else ObjectFilter.Category(arcanum.api.ObjectCategory.Permanent)
            return Filters.all(noun, ObjectFilter.ControlledBy(PlayerFilter.You),
                ObjectFilter.Attached(attachments = ObjectFilter.Sub(arcanum.api.Subtype(if (m.groupValues[1] == "equipped") "Equipment" else "Aura"))))
        }
        // msh chunk 09: "nontoken artifact or creature": the adjective goes with both types
        NON_A_OR_B.matchEntire(phrase)?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null,
                ObjectFilter.Or(listOf(Filters.noun(m.groupValues[2]) ?: return null, Filters.noun(m.groupValues[3]) ?: return null)))
        }
        // msh chunk 08: "permanent card in your graveyard that was put there from anywhere this turn"
        PUT_THERE_THIS_TURN.matchEntire(phrase)?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null,
                ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Graveyard), role = arcanum.api.EventRole.After))
        }
        return null
    }
    private val ATTACKING_ALONE = Regex("""(.+) that's attacking alone""")
    private val NON_A_OR_B = Regex("""(non\w+) (artifact|creature|enchantment|land|planeswalker) or (artifact|creature|enchantment|land|planeswalker)""")
    private val PUT_THERE_THIS_TURN = Regex("""(.+) that was put there from anywhere this turn""")

    /** The number of [color] mana symbols in [of]'s mana cost (CR 107.4). */
    fun manaSymbols(color: Color, of: Objects): Value = Value.Of(NumericProperty.ManaSymbols(color), of, Combine.Single)

    /** "a +1/+1 counter" etc. */
    fun counter(word: String): CounterKind? = Counters.kind(word)

    /** "Heroes", "Villains" and other regular plurals of creature types. */
    fun creatureType(plural: String): arcanum.api.Subtype? = listOf(plural.removeSuffix("es"), plural.removeSuffix("s"), plural)
        .firstOrNull { it in RulesWords.subtypes }?.let { arcanum.api.Subtype(it) }
}

/**
 * msh: [line] with the gendered pronouns of a legendary creature ("he", "she", "him", "her", "his") read as "it"/"its", so
 * the general rules read it ("put a +1/+1 counter on her" = "on it"). [self]'s names are kept as written ("She-Hulk").
 */
internal fun ungendered(line: String, self: Set<String>): String {
    val names = self.filter { it.isNotEmpty() && line.contains(it) }.sortedByDescending { it.length }
    var masked = line
    names.forEachIndexed { i, name -> masked = masked.replace(name, "\u0001$i\u0001") }
    masked = masked
        .replace(LazyRegexes.HE_OR_SHE_IS, "it's").replace(LazyRegexes.HE_OR_SHE_IS_CAPITAL, "It's")
        .replace(LazyRegexes.HE_OR_SHE, "it").replace(LazyRegexes.HE_OR_SHE_CAPITAL, "It")
        .replace(LazyRegexes.HIS, "its").replace(LazyRegexes.HIS_CAPITAL, "Its")
        .replace(LazyRegexes.HIMSELF_OR_HERSELF, "itself")
        .replace(LazyRegexes.HIM, "it")
        .replace(LazyRegexes.HER_BEFORE_NOUN, "its ").replace(LazyRegexes.HER_BEFORE_NOUN_CAPITAL, "Its ")
        .replace(LazyRegexes.HER, "it")
    names.forEachIndexed { i, name -> masked = masked.replace("\u0001$i\u0001", name) }
    return masked
}
