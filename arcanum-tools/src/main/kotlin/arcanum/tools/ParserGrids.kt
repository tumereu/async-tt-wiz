package arcanum.tools

import arcanum.parser.OracleCard

/** One grid cell: a line of invented text on an invented card. [grid] is the row ("G1"), [group] its part ("G1 verb×object: Exile"). */
internal class GridCell(val grid: String, val group: String, val typeLine: String, val text: String) {
    val card get() = OracleCard(name = PROBE_NAME, manaCost = null, typeLine = typeLine, text = text, power = null, toughness = null,
        loyalty = null, defense = null, colorIndicator = null)
}

private const val PROBE_NAME = "Probe Card"
private const val SORCERY = "Sorcery"
private const val CREATURE = "Creature — Elf"
private const val ENCHANTMENT = "Enchantment"

private fun String.cap() = replaceFirstChar { it.uppercase() }
private fun String.low() = replaceFirstChar { it.lowercase() }

/**
 * Every combination of parts the rules model can express, as lines built from our own templates: the fair test of how
 * much the parser's parts combine. Not every cell is printed on a card and some are unusual English; a grammar reads
 * them and a list of sentences doesn't. G1 to G11; see the parser architecture review §4.3.
 */
internal fun parserGrids(): List<GridCell> = buildList {
    fun cell(group: String, type: String, text: String) = add(GridCell(group.substringBefore(' '), group, type, text))

    // G1 verb × object
    val singular = listOf("target creature", "target artifact", "target enchantment", "target land", "target nonland permanent",
        "target creature an opponent controls", "target creature you control", "target creature with flying",
        "target creature with power 3 or less", "target tapped creature", "target artifact or enchantment",
        "up to one target creature", "another target creature")
    val plural = listOf("up to two target creatures", "two target creatures", "any number of target creatures", "all creatures",
        "all artifacts", "all creatures with flying", "all tapped creatures", "all creatures you control")
    val each = listOf("each creature", "each creature an opponent controls", "each other creature", "each nonland permanent",
        "each artifact you control")
    val verbs = listOf("Destroy" to { o: String -> "Destroy $o." }, "Exile" to { o: String -> "Exile $o." }, "Tap" to { o: String -> "Tap $o." },
        "Untap" to { o: String -> "Untap $o." }, "Gain control" to { o: String -> "Gain control of $o." },
        "+1 counter" to { o: String -> "Put a +1/+1 counter on $o." }, "Goad" to { o: String -> "Goad $o." },
        "Detain" to { o: String -> "Detain $o." }, "Damage" to { o: String -> "$PROBE_NAME deals 2 damage to $o." })
    for ((verb, line) in verbs) for (o in singular + plural + each) cell("G1 verb×object: $verb", SORCERY, line(o))
    for (o in singular + each) cell("G1 verb×object: Return", SORCERY, "Return $o to its owner's hand.")
    for (o in plural) cell("G1 verb×object: Return", SORCERY, "Return $o to their owners' hands.")

    // G1b subject + predicate
    for (o in singular + each) {
        cell("G1b subject+pump", SORCERY, "${o.cap()} gets +2/+2 until end of turn.")
        cell("G1b subject+keyword", SORCERY, "${o.cap()} gains flying until end of turn.")
        cell("G1b subject+cant", SORCERY, "${o.cap()} can't block this turn.")
    }
    for (o in plural) {
        cell("G1b subject+pump", SORCERY, "${o.cap()} get +2/+2 until end of turn.")
        cell("G1b subject+keyword", SORCERY, "${o.cap()} gain flying until end of turn.")
        cell("G1b subject+cant", SORCERY, "${o.cap()} can't block this turn.")
    }

    // G2 player × action
    val actions = listOf("draw a card" to "draws a card", "draw two cards" to "draws two cards", "discard a card" to "discards a card",
        "discard two cards" to "discards two cards", "lose 2 life" to "loses 2 life", "gain 2 life" to "gains 2 life",
        "mill two cards" to "mills two cards", "scry 2" to "scries 2", "surveil 2" to "surveils 2",
        "sacrifice a creature" to "sacrifices a creature", "create a Treasure token" to "creates a Treasure token",
        "create a 1/1 green Elf creature token" to "creates a 1/1 green Elf creature token", "investigate" to "investigates",
        "shuffle their library" to "shuffles their library",
        "look at the top card of their library" to "looks at the top card of their library",
        "take an extra turn after this one" to "takes an extra turn after this one")
    for ((you, them) in actions) {
        cell("G2 player×action: imperative", SORCERY, "${you.cap()}.")
        cell("G2 player×action: You", SORCERY, "You $you.")
        for (player in listOf("Target player", "Target opponent", "Each player", "Each opponent")) cell("G2 player×action: $player", SORCERY, "$player $them.")
    }

    // G3 subject × event
    val subjects = listOf("this creature", "another creature you control", "a creature you control", "a creature",
        "a creature an opponent controls", "an artifact you control", "a nontoken creature you control", "a Goblin you control")
    val events = listOf("enters" to "enter", "dies" to "die", "attacks" to "attack", "blocks" to "block",
        "becomes tapped" to "become tapped", "becomes untapped" to "become untapped",
        "leaves the battlefield" to "leave the battlefield", "deals combat damage to a player" to "deal combat damage to a player",
        "deals damage" to "deal damage", "is dealt damage" to "are dealt damage",
        "becomes the target of a spell" to "become the target of a spell", "becomes blocked" to "become blocked",
        "blocks or becomes blocked" to "block or become blocked", "attacks or blocks" to "attack or block")
    for (s in subjects) for ((event, _) in events) cell("G3 subject×event: $event", CREATURE, "Whenever $s $event, draw a card.")
    for ((event, many) in events) cell("G3 subject×event: $event", CREATURE, "Whenever one or more creatures you control $many, draw a card.")

    // G4 player events
    val playerEvents = listOf("draw a card" to "draws a card", "discard a card" to "discards a card", "gain life" to "gains life",
        "lose life" to "loses life", "scry" to "scries", "surveil" to "surveils", "cycle a card" to "cycles a card",
        "cycle or discard a card" to "cycles or discards a card", "sacrifice a creature" to "sacrifices a creature",
        "sacrifice a permanent" to "sacrifices a permanent", "sacrifice an artifact" to "sacrifices an artifact",
        "cast a spell" to "casts a spell", "cast a creature spell" to "casts a creature spell",
        "cast a noncreature spell" to "casts a noncreature spell",
        "cast an instant or sorcery spell" to "casts an instant or sorcery spell", "play a land" to "plays a land",
        "attack" to "attacks", "activate an ability" to "activates an ability", "create a token" to "creates a token",
        "mill a card" to "mills a card", "shuffle your library" to null, "tap an untapped creature" to null)
    for ((you, them) in playerEvents) {
        cell("G4 player event: you", CREATURE, "Whenever you $you, draw a card.")
        if (them != null) {
            cell("G4 player event: an opponent", CREATURE, "Whenever an opponent $them, draw a card.")
            cell("G4 player event: a player", CREATURE, "Whenever a player $them, draw a card.")
        }
    }

    // G5 step triggers
    val whose = listOf("your", "each", "each opponent's", "each player's", "enchanted player's", "the")
    val steps = listOf("upkeep", "draw step", "precombat main phase", "first main phase", "postcombat main phase", "combat", "end step", "end of combat")
    for (w in whose) for (step in steps) {
        val text = when {
            step == "combat" && w == "your" -> "At the beginning of combat on your turn, draw a card."
            step == "combat" && w != "the" ->
                "At the beginning of combat on each ${if (w == "each" || w == "each player's") "turn" else "opponent's turn"}, draw a card."
            w == "the" -> "At the beginning of the next $step, draw a card."
            else -> "At the beginning of $w $step, draw a card."
        }
        cell("G5 step triggers", CREATURE, text)
    }

    // G6 durations
    val durations = listOf("until end of turn", "until your next turn", "until end of combat", "until the end of your next turn",
        "until your next upkeep", "for as long as you control this creature", "until this creature leaves the battlefield", "this turn")
    for (d in durations) {
        cell("G6 duration: pump", CREATURE, "{T}: Target creature gets +2/+2 $d.")
        cell("G6 duration: keyword", CREATURE, "{T}: Target creature gains flying $d.")
        cell("G6 duration: cant block", CREATURE, "{T}: Target creature can't block $d.")
        cell("G6 duration: control", CREATURE, "{T}: Gain control of target creature $d.")
        cell("G6 duration: self pump", CREATURE, "{T}: This creature gets +2/+2 $d.")
        cell("G6 duration: group", CREATURE, "{T}: Creatures you control get +1/+1 $d.")
    }

    // G7 frames around known effects
    val effects = listOf("Draw a card.", "Destroy target creature.", "Exile target creature.", "You gain 2 life.", "Create a Treasure token.",
        "Target creature gets +2/+2 until end of turn.", "Put a +1/+1 counter on target creature.", "Tap target creature.",
        "Return target creature to its owner's hand.", "Each opponent loses 2 life.", "Scry 2.", "Surveil 2.", "Discard a card.",
        "Mill two cards.", "Target player draws two cards.")
    for (e in effects) {
        val low = e.low()
        cell("G7 base effect", SORCERY, e)
        cell("G7 you may", SORCERY, "You may $low")
        cell("G7 if condition", SORCERY, "If you control an artifact, $low")
        cell("G7 trigger wrap", CREATURE, "Whenever this creature attacks, $low")
        cell("G7 activated wrap", CREATURE, "{2}, {T}: $e")
        cell("G7 may in trigger", CREATURE, "When this creature enters, you may $low")
        cell("G7 intervening if", CREATURE, "When this creature enters, if you control an artifact, $low")
        cell("G7 may pay if you do", CREATURE, "When this creature enters, you may pay {2}. If you do, $low")
        cell("G7 sacrifice if you do", CREATURE, "When this creature enters, you may sacrifice an artifact. If you do, $low")
        cell("G7 reflexive", CREATURE, "When this creature enters, you may pay {2}. When you do, $low")
        cell("G7 delayed", SORCERY, "At the beginning of the next end step, $low")
        cell("G7 otherwise", SORCERY, "If you control an artifact, draw a card. Otherwise, $low")
        cell("G7 unless pays", SORCERY, "${e.dropLast(1)} unless target opponent pays {2}.")
        cell("G7 saga chapter", "Enchantment — Saga", "I — $e")
        cell("G7 modal mode", SORCERY, "Choose one —\n• $e\n• Draw a card.")
        cell("G7 ability word", CREATURE, "Landfall — Whenever a land you control enters, $low")
        cell("G7 granted quoted", ENCHANTMENT, "Enchant creature\nEnchanted creature has \"{T}: $e\"")
        cell("G7 token quoted", SORCERY, "Create a 1/1 green Elf creature token with \"{T}: $e\"")
        for (f in effects.take(6)) {
            if (e == f) continue
            cell("G7 sequence A. B.", SORCERY, "$e $f")
            cell("G7 A, then B", SORCERY, "${e.dropLast(1)}, then ${f.low()}")
            cell("G7 A and B", SORCERY, "${e.dropLast(1)} and ${f.low()}")
        }
    }

    // G8 conditions in four frames
    val conditions = listOf("you control an artifact", "you control a Goblin", "you control three or more creatures",
        "you have 10 or more life", "your life total is 5 or less", "an opponent has 10 or less life",
        "there are seven or more cards in your graveyard", "you have no cards in hand", "you have seven or more cards in hand",
        "this creature is tapped", "this creature has a +1/+1 counter on it", "it's your turn", "it's not your turn",
        "you cast a spell this turn", "a creature died this turn", "you gained life this turn", "you attacked this turn",
        "an opponent lost life this turn", "you control no creatures", "you control another creature",
        "you control a creature with power 4 or greater", "defending player controls an Island")
    for (c in conditions) {
        cell("G8 condition: static as long as", CREATURE, "As long as $c, this creature gets +1/+1.")
        cell("G8 condition: intervening if", CREATURE, "When this creature enters, if $c, draw a card.")
        cell("G8 condition: effect if", SORCERY, "If $c, draw a card.")
        cell("G8 condition: activate only if", CREATURE, "{T}: Draw a card. Activate only if $c.")
    }

    // G9 static subject × predicate
    val staticSubjects = listOf("This creature" to true, "Creatures you control" to false, "Other creatures you control" to false,
        "Goblins you control" to false, "Other Goblins you control" to false, "Each creature you control" to true,
        "Enchanted creature" to true, "All creatures" to false, "Creatures your opponents control" to false,
        "Artifact creatures you control" to false, "Nontoken creatures you control" to false,
        "Creatures you control with flying" to false, "Green creatures you control" to false,
        "Other Goblin creatures you control" to false, "Tapped creatures you control" to false)
    val predicates = listOf("gets +1/+1" to "get +1/+1", "has flying" to "have flying",
        "has first strike and vigilance" to "have first strike and vigilance", "can't block" to "can't block",
        "can't attack" to "can't attack", "can't be blocked" to "can't be blocked",
        "attacks each combat if able" to "attack each combat if able", "gets +1/+1 and has flying" to "get +1/+1 and have flying",
        "has \"{T}: Add {G}.\"" to "have \"{T}: Add {G}.\"", "has islandwalk" to "have islandwalk",
        "can block an additional creature each combat" to "can block an additional creature each combat",
        "doesn't untap during your untap step" to "don't untap during your untap step")
    for ((s, one) in staticSubjects) for ((single, many) in predicates) {
        val aura = "Enchanted" in s
        cell("G9 static subject×predicate", if (aura) "Enchantment — Aura" else CREATURE,
            (if (aura) "Enchant creature\n" else "") + "$s ${if (one) single else many}.")
    }

    // G10 costs
    val costs = listOf("{T}", "{2}", "Sacrifice this creature", "Sacrifice a creature", "Sacrifice an artifact", "Sacrifice a land",
        "Discard a card", "Discard two cards", "Pay 2 life", "Exile a card from your graveyard",
        "Exile a creature card from your graveyard", "Remove a +1/+1 counter from this creature", "Tap an untapped creature you control",
        "Tap two untapped creatures you control", "Return a land you control to its owner's hand", "Exile this creature", "Mill a card",
        "Reveal a card from your hand", "Pay {E}", "Put a -1/-1 counter on this creature", "Discard your hand")
    for (a in costs) {
        cell("G10 cost single", CREATURE, "$a: Draw a card.")
        for (b in costs) if (a != b && (a == "{T}" || a == "{2}")) cell("G10 cost pair", CREATURE, "$a, $b: Draw a card.")
    }

    // G11 keyword actions
    for (k in listOf("Investigate.", "Proliferate.", "Populate.", "Amass Zombies 2.", "Connive.", "Explore.", "Learn.",
        "Venture into the dungeon.", "Incubate 2.", "Discover 3.", "Manifest dread.", "Cloak the top card of your library.",
        "Goad target creature.", "Support 2.", "Bolster 2.", "Adapt 2.", "Fateseal 2.", "Clash with an opponent.", "Exert this creature.",
        "Time travel.", "Seek a land card.", "You become the monarch.", "You take the initiative.", "The Ring tempts you.",
        "Become the monarch.", "Transform this creature.", "Regenerate this creature.", "Regenerate target creature.", "Tap this creature.",
        "Sacrifice this creature.", "Vote.", "Abandon this scheme.", "Open an Attraction.", "Roll to visit your Attractions.",
        "You get {E}{E}.", "You get an experience counter.", "You get a poison counter.", "Target opponent gets a poison counter.")) {
        cell("G11 keyword actions", SORCERY, k)
    }
}
