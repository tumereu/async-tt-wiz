package arcanum.api

/**
 * Explanations of the keywords ([KeywordKind]), keyword actions ([KeywordAction], and scry, surveil, mill
 * and fight, which are effects of their own), states ([states]), token kinds ([tokens]) and other rules words ([terms]) the
 * libraries know, for players who hold one to see what it means, and the ones a card's text leaves unexplained
 * ([reminders]).
 *
 * Every text here is written as MTG reminder text (owner 2026-11-21): the official reminder-text template's wording where
 * the keyword or keyword action has one, made generic (a number becomes N, a cost "its flashback cost"), and a short
 * sentence in the same register where it has none (a state, a counter, a word like transform); never the Comprehensive
 * Rules' text. A token kind says what the token is and quotes its abilities ([tokens]). They simplify (corner cases are
 * left out); the rules and the engine decide. A test checks that every [KeywordKind.all] and every [KeywordAction] type
 * has an explanation, and the parser's tests that every keyword it reads has one.
 */
object RulesGlossary {
    /** One explanation: [name] as cards print it ("Flying", "Collect evidence"), [text] what it means, as reminder text;
     * [action] for a keyword action (something a card tells you to do), else a keyword ability. [id] is the
     * [KeywordKind.id] or the keyword action's serial name ("forage", "scry"). */
    data class Entry(val id: String, val name: String, val text: String, val action: Boolean = false)

    /** Every keyword's explanation, by [KeywordKind.id]. */
    val keywords: Map<String, Entry> by lazy { KEYWORDS.associate { (id, name, text) -> id to Entry(id, name, text) } }

    /** Every keyword action's explanation, by its serial name ("forage", "collect-evidence", "scry"). */
    val actions: Map<String, Entry> by lazy { ACTIONS.associate { (id, name, text) -> id to Entry(id, name, text, action = true) } }

    /** The explanation of keyword [kind], null when it has none (a test makes sure every kind has one). */
    fun of(kind: KeywordKind): Entry? = keywords[kind.id]

    /** The explanation of the keyword action with serial name [id], null when it has none. */
    fun action(id: String): Entry? = actions[id]

    /**
     * What the states the rules keep mean, by id: an object's states (a permanent's marks, by their serial name:
     * "ring-bearer", "monstrous", "goaded", ...) and a player's or the game's ("monarch", "speed", "the-ring", "day", ...).
     * Written and kept like the keywords' explanations. A test checks that every [ObjectMark] and [Designation] has one, but
     * [ObjectMark.Named] and the face-down marks ([ObjectMark.Manifested], [ObjectMark.Cloaked]).
     */
    val states: Map<String, Entry> by lazy { (CARD_STATES + PLAYER_STATES).associate { (id, name, text) -> id to Entry(id, name, text) } }

    /** The explanation of the state with id [id] ([states]), null when it has none. */
    fun state(id: String): Entry? = states[id]

    /**
     * What a Ring-bearer gets at each of the Ring's levels (CR 701.54c), in plain words: a host shows as many as the Ring has
     * tempted the player, the first for one temptation.
     */
    val ringLevels: List<String> = listOf(
        "legendary, can't be blocked by creatures with greater power",
        "when it attacks, you draw a card, then discard a card",
        "a creature that blocks it is sacrificed at end of combat",
        "when it deals combat damage to a player, each opponent loses 3 life",
    )

    /**
     * The explanations [text] names, in the order it names them: each keyword or keyword action whose printed name is
     * in it as whole words, case aside ("flying" → Flying; "+3/+3, trample until end of turn" → Trample; "Forage" →
     * Forage; "Crew 3" → Crew; "Protection from red" → Protection). Longer names win over the shorter names inside them
     * ("Double strike" is not "Double", "Manifest dread" not "Manifest"); each entry is listed once. A keyword's variants
     * name it too: "Megamorph" morph, "Multikicker" kicker, "Swampcycling" cycling, "Forestwalk" landwalk. An object's states
     * are named the same way ("Goaded by Bob" → Goaded, "Ring-bearer: legendary" → Ring-bearer); a player's aren't, their
     * words ("day", "speed") being common.
     */
    fun named(text: String): List<Entry> {
        val lower = text.lowercase()
        val taken = BooleanArray(lower.length)
        val found = mutableListOf<Pair<Int, Entry>>()
        for ((name, entry) in names) {
            var from = 0
            while (true) {
                val at = lower.indexOf(name, from)
                if (at < 0) break
                val end = at + name.length
                val whole = (at == 0 || !lower[at - 1].isLetterOrDigit()) && (end == lower.length || !lower[end].isLetterOrDigit())
                if (whole && (at until end).none { taken[it] }) {
                    for (i in at until end) taken[i] = true
                    if (found.none { it.second == entry }) found += at to entry
                }
                from = at + 1
            }
        }
        // Typecycling and landwalk: "Swampcycling", "Basic landcycling", "Islandwalk"
        for (match in Regex("[a-z]+(cycling|walk)\\b").findAll(lower)) {
            if (match.range.any { taken[it] }) continue
            val entry = keywords.getValue(if (match.groupValues[1] == "cycling") "cycling" else "landwalk")
            if (found.none { it.second == entry }) found += match.range.first to entry
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    /** The first explanation [text] names ([named]), null when it names none. */
    fun first(text: String): Entry? = named(text).firstOrNull()

    /**
     * What the token kinds cards make or name mean, by the name cards print ("Treasure", "Monster Role"): every predefined
     * token ([PredefinedTokens.names]) and "Role" itself. Each says what the token is in the manner of reminder text, its
     * card type and then its abilities in quotes as [PredefinedTokens] gives them ("It's an artifact token with \"{2},
     * Sacrifice this token: Draw a card.\""); tests check that every predefined token has one and that it quotes them.
     */
    val tokens: Map<String, Entry> by lazy { TOKENS.associate { (id, name, text) -> name to Entry(id, name, text) } }

    /** The explanation of the token kind cards print as [name] ("Food", "Young Hero Role", "Role"), null when it has none. */
    fun token(name: String): Entry? = tokens[name]

    /**
     * Other rules words cards print that the library models as neither a [KeywordKind] nor a [KeywordAction], by id:
     * ability labels ([AbilityLabel]: "Exhaust", "Max speed", "Power-up", ...) and actions and words of their own
     * ("Transform", "Suspect", "Face a dilemma", "Descend"). Written and kept like the keywords' explanations.
     */
    val terms: Map<String, Entry> by lazy { TERMS.associate { (id, name, text) -> id to Entry(id, name, text) } }

    /**
     * The explanation of what cards print as [name], a keyword ability, keyword action, other rules word, token kind or an
     * object's state, as a card list names it ("Ward", "Hexproof from", "Swampcycling", "Scry", "Transform", "Food",
     * "Role token", "Solved"); null when the glossary has none.
     */
    fun lookup(name: String): Entry? {
        val bare = name.trim().removeSuffix(" tokens").removeSuffix(" token")
        tokens[bare]?.let { return it }
        val lower = bare.lowercase().trimEnd('!')
        for (words in listOf(lower, lower.removeSuffix(" from"))) vocabulary.firstOrNull { !it.exactCase && it.words == words }?.let { return it.entry }
        // A keyword's variants: "Basic landcycling", "Islandwalk"
        return vocabularyIn(lower).singleOrNull()?.takeIf { (_, end, _) -> end == lower.length }?.third
    }

    /**
     * The rules reminders of a card whose rules text is [text] (its faces' texts one per line; [names] the card's own names,
     * which never count as rules words): each keyword ability, keyword action, other rules word ([terms]), token kind
     * ([tokens]) and object state the text names, in the order it first names them, but those the card explains itself.
     * A card explains a word with reminder text: a parenthesis later on the same line as the word ("Ward {2} (Whenever
     * …)", "Create a Food token. (It's an artifact …)"); one explained place explains the word for the whole card.
     * Words inside reminder text aren't counted, nor ability and flavor words leading a line ("Landfall — …"); verbs count
     * in their forms too ("explores", "foraging", "transformed").
     */
    fun reminders(text: String, names: Collection<String> = emptyList()): List<Entry> =
        rulesWords(text, names).filterValues { !it }.keys.toList()

    /** Every rules word [reminders] finds in [text] (with the card's own [names]), in the order the text first names them,
     * each with whether the card explains it itself. */
    fun rulesWords(text: String, names: Collection<String> = emptyList()): Map<Entry, Boolean> {
        val own = names.flatMap { listOf(it, it.substringBefore(',')) }.map { it.trim() }.filter { it.length > 1 }.distinct()
            .sortedByDescending { it.length }
        val explained = LinkedHashMap<Entry, Boolean>()
        for (printed in text.lines()) {
            val line = withoutLeadWords(printed)
            val asides = ASIDE.findAll(line).map { it.range }.toList()
            // The card's names, where they stand: a word within one is no rules word ("Flash Ward"), a rules word holding one
            // is ("Empower Jace" on a Jace)
            val named = mutableListOf<IntRange>()
            for (name in own) {
                var from = 0
                while (true) {
                    val at = line.indexOf(name, from)
                    if (at < 0) break
                    from = at + name.length
                    if (named.none { at in it || at + name.length - 1 in it }) named += at until at + name.length
                }
            }
            for ((at, end, entry) in vocabularyIn(line)) {
                if (asides.any { at in it } || named.any { at in it && end - 1 in it }) continue
                explained[entry] = explained[entry] == true || line.indexOf('(', end) >= 0
            }
        }
        return explained
    }

    /** Reminder text: a parenthesis, to the line's end when it isn't closed. */
    private val ASIDE = Regex("""\([^)]*\)?""")

    /** [line] with the ability or flavor words that lead it blanked ("Landfall — ", "I, II — Wings of Light — "): each part
     * before a spaced dash, but one that is itself a rules word, with or without a number ("Exhaust", "Max speed", "Descend
     * 4"), or holds a cost ("Prototype {1}{B}{B}"). */
    private fun withoutLeadWords(line: String): String {
        val parts = line.split(" — ")
        if (parts.size < 2) return line
        return parts.mapIndexed { index, part ->
            val word = part.trim().removePrefix("• ").replace(Regex("""\s+(\d+|X|N)$"""), "").lowercase()
            if (index == parts.size - 1 || '{' in part || vocabulary.any { it.words == word }) part else " ".repeat(part.length)
        }.joinToString(" — ")
    }

    /** One way a rules word is printed in [reminders]: [words] (lower case, unless [exactCase]) and the [endings] its forms
     * add. */
    private class Printed(val words: String, val entry: Entry, val exactCase: Boolean = false, val endings: List<String> = emptyList())

    /** Every word [reminders] looks for, longest first. */
    private val vocabulary: List<Printed> by lazy {
        val verbs = listOf("s", "es", "d", "ed", "ing")
        fun verb(entry: Entry): List<Printed> {
            val words = entry.name.lowercase()
            return listOf(Printed(words, entry, endings = verbs)) + when {
                words.endsWith("e") -> listOf(Printed(words.dropLast(1), entry, endings = listOf("ing"))) // "foraging"
                words.endsWith("y") -> listOf(Printed(words.dropLast(1), entry, endings = listOf("ies", "ied"))) // "scries"
                else -> emptyList()
            }
        }
        val states = CARD_STATES.map { (id, _, _) -> states.getValue(id) } + REMINDED_PLAYER_STATES.map { states.getValue(it) }
        (keywords.values.map { Printed(it.name.lowercase().trimEnd('!'), it) } +
            VARIANTS.map { (name, id) -> Printed(name, keywords.getValue(id)) } +
            actions.values.flatMap(::verb) + terms.values.filter { it.id !in PRINTED_AS }.flatMap(::verb) +
            PRINTED_AS.map { (id, words) -> Printed(words, terms.getValue(id)) } +
            states.map { Printed(it.name.lowercase(), it) } +
            tokens.values.map { Printed(it.name, it, exactCase = true, endings = listOf("s")) })
            .sortedWith(compareByDescending<Printed> { it.words.length }.thenBy { it.entry.id })
    }

    /** The rules words [line] names, at their places (start, end), none overlapping, longest first. A word's edges are
     * whole: no letter, digit or hyphen beside it ("double-faced" isn't double). */
    private fun vocabularyIn(line: String): List<Triple<Int, Int, Entry>> {
        val lower = line.lowercase()
        val taken = BooleanArray(line.length)
        val found = mutableListOf<Triple<Int, Int, Entry>>()
        fun inWord(text: String, at: Int) = at in text.indices && (text[at].isLetterOrDigit() || text[at] == '-')
        fun take(at: Int, end: Int, entry: Entry): Boolean {
            if ((at until end).any { taken[it] }) return false
            for (i in at until end) taken[i] = true
            found += Triple(at, end, entry)
            return true
        }
        for (printed in vocabulary) {
            val text = if (printed.exactCase) line else lower
            var from = 0
            while (true) {
                val at = text.indexOf(printed.words, from)
                if (at < 0) break
                from = at + 1
                if (inWord(text, at - 1)) continue
                val end = at + printed.words.length
                val ending = if (!inWord(text, end)) "" else printed.endings.firstOrNull { text.startsWith(it, end) && !inWord(text, end + it.length) } ?: continue
                take(at, end + ending.length, printed.entry)
            }
        }
        // Typecycling and landwalk: "Swampcycling", "Basic landcycling", "Islandwalk"
        for (match in Regex("[a-z]+(cycling|walk)\\b").findAll(lower))
            take(match.range.first, match.range.last + 1, keywords.getValue(if (match.groupValues[1] == "cycling") "cycling" else "landwalk"))
        return found.sortedBy { it.first }
    }

    /** The player states cards name in words of their own, whose reminders a card's text gets ("you become the monarch"). */
    private val REMINDED_PLAYER_STATES = listOf("monarch", "initiative", "citys-blessing")

    /** Rules words printed otherwise than their name ([terms] id → printed words). */
    private val PRINTED_AS = mapOf("infinity" to "∞")

    /** Every name an entry goes by, lower case (variants too), longest first. */
    private val names: List<Pair<String, Entry>> by lazy {
        ((keywords.values + actions.values + CARD_STATES.map { (id, _, _) -> states.getValue(id) }).map { it.name.lowercase().trimEnd('!') to it } +
            VARIANTS.map { (name, id) -> name to keywords.getValue(id) })
            .sortedWith(compareByDescending<Pair<String, Entry>> { it.first.length }.thenBy { it.second.id })
    }

    /** Keyword variants printed under names of their own, and the keyword they are (CR 702.33c, 702.37b). */
    private val VARIANTS = listOf("multikicker" to "kicker", "megamorph" to "morph")

    // An object's states (CR 701, 702, 709, 720, 723): (id, name as a player reads it, explanation)
    private val CARD_STATES: List<Triple<String, String, String>> = listOf(
        Triple("ring-bearer", "Ring-bearer", "The creature its controller chose as their Ring-bearer when the Ring tempted them. It stays the Ring-bearer until they choose another creature or another player gains control of it, and it gets more with each of the Ring's levels."),
        Triple("goaded", "Goaded", "This creature attacks each combat if able and attacks a player other than the player who goaded it if able. It stays goaded until that player's next turn."),
        Triple("monstrous", "Monstrous", "This creature has become monstrous. Its monstrosity ability won't put counters on it again, and its abilities that care about being monstrous apply."),
        Triple("renowned", "Renowned", "This creature has become renowned. Its renown ability won't put counters on it again while it stays on the battlefield."),
        Triple("suspected", "Suspected", "A suspected creature has menace and can't block."),
        Triple("saddled", "Saddled", "This Mount became saddled this turn. It stays saddled until end of turn or until it leaves the battlefield, and its abilities that care about being saddled apply."),
        Triple("solved", "Solved", "This Case's goal was met at the beginning of its controller's end step, so it became solved. Its Solved abilities apply from now on."),
        Triple("prepared", "Prepared", "Its controller may cast the copy of its prepare spell once. Casting that copy makes this permanent unprepared."),
        Triple("harnessed", "Harnessed", "This permanent stays harnessed until it leaves the battlefield. Its abilities marked with the infinity symbol apply while it's harnessed."),
        Triple("unlocked", "Unlocked", "This door of the Room is unlocked, so its abilities apply. A locked door has no abilities until it's unlocked."),
        Triple("phased-out", "Phased out", "This permanent is treated as though it doesn't exist. It phases in during its controller's next untap step, before they untap."),
        Triple("plotted", "Plotted", "This card is plotted in exile. Its owner may cast it as a sorcery on a later turn without paying its mana cost."),
        Triple("foretold", "Foretold", "This card was foretold: it's exiled face down, its owner may look at it, and they may cast it on a later turn for its foretell cost."),
        Triple("warped", "Warped", "This card was cast for its warp cost and exiled at the beginning of the end step. Its owner may cast it from exile on a later turn."),
    )

    // A player's and the game's states: (id, name as a player reads it, explanation)
    private val PLAYER_STATES: List<Triple<String, String, String>> = listOf(
        Triple("monarch", "Monarch", "At the beginning of this player's end step, they draw a card. Whenever a creature deals combat damage to the monarch, its controller becomes the monarch."),
        Triple("initiative", "Initiative", "Whenever this player takes the initiative and at the beginning of their upkeep, they venture into Undercity. Whenever a creature deals combat damage to them, its controller takes the initiative."),
        Triple("citys-blessing", "City's blessing", "This player controlled ten or more permanents while they had ascend, so they have the city's blessing for the rest of the game. Their abilities that check for it apply."),
        Triple("enduring-story", "Enduring story", "This player controlled three or more artifacts, Sagas, and/or legendary permanents while they had storied, so they have an enduring story for the rest of the game."),
        Triple("speed", "Speed", "This player's speed starts at 1. It increases once on each of their turns when an opponent loses life. Max speed is 4, and max speed abilities apply only then."),
        Triple("the-ring", "The Ring", "Each time the Ring tempts this player, they choose a Ring-bearer and the Ring gains its next level, up to four. Their Ring-bearer gets the abilities of each level."),
        Triple("day", "Day", "It's day. If a player casts no spells during their own turn, it becomes night next turn, and daybound permanents transform."),
        Triple("night", "Night", "It's night. If a player casts two or more spells during their own turn, it becomes day next turn, and nightbound permanents transform."),
        Triple("poison", "Poison", "A player with ten or more poison counters loses the game."),
        Triple("energy", "Energy", "This player's energy counters. Each {E} in a cost is paid by removing one of them, and they stay until spent."),
        Triple("rad", "Rad", "At the beginning of this player's first main phase, they mill cards equal to their rad counters. For each nonland card milled this way, they lose 1 life and remove a rad counter."),
        Triple("experience", "Experience", "This player's experience counters. They stay for the rest of the game, and cards that care about experience counters count them."),
        Triple("dungeons-completed", "Dungeons completed", "The number of dungeons this player has completed this game. Some cards count them."),
        Triple("extra-turn", "Extra turn", "This player takes that many extra turns after this one."),
        Triple("skipped-turn", "Skips a turn", "This player skips that many of their next turns."),
    )

    // Keywords (CR 702), in [KeywordKind.all]'s order: (id, printed name, explanation)
    private val KEYWORDS: List<Triple<String, String, String>> = listOf(
        Triple("deathtouch", "Deathtouch", "Any amount of damage this deals to a creature is enough to destroy it."),
        Triple("defender", "Defender", "This creature can't attack."),
        Triple("double-strike", "Double strike", "This creature deals both first-strike and regular combat damage."),
        Triple("first-strike", "First strike", "This creature deals combat damage before creatures without first strike."),
        Triple("flash", "Flash", "You may cast this spell any time you could cast an instant."),
        Triple("flying", "Flying", "This creature can't be blocked except by creatures with flying or reach."),
        Triple("haste", "Haste", "This creature can attack and {T} as soon as it comes under your control."),
        Triple("hexproof", "Hexproof", "This permanent can't be the target of spells or abilities your opponents control."),
        Triple("indestructible", "Indestructible", "Damage and effects that say \"destroy\" don't destroy it."),
        Triple("intimidate", "Intimidate", "This creature can't be blocked except by artifact creatures and/or creatures that share a color with it."),
        Triple("lifelink", "Lifelink", "Damage dealt by this creature also causes you to gain that much life."),
        Triple("reach", "Reach", "This creature can block creatures with flying."),
        Triple("shroud", "Shroud", "This permanent can't be the target of spells or abilities."),
        Triple("trample", "Trample", "This creature can deal excess combat damage to the player, planeswalker, or battle it's attacking."),
        Triple("trample-over-planeswalkers", "Trample over planeswalkers", "This creature can deal excess combat damage to the player or battle it's attacking, or to the controller of the planeswalker it's attacking."),
        Triple("vigilance", "Vigilance", "Attacking doesn't cause this creature to tap."),
        Triple("banding", "Banding", "Any creatures with banding, and up to one without, can attack in a band. Bands are blocked as a group. If a creature with banding you control is blocking or blocked by a creature, you divide that creature's combat damage, not its controller."),
        Triple("flanking", "Flanking", "Whenever a creature without flanking blocks this creature, the blocking creature gets -1/-1 until end of turn."),
        Triple("phasing", "Phasing", "This phases in or out before you untap during each of your untap steps. While it's phased out, it's treated as though it doesn't exist."),
        Triple("shadow", "Shadow", "This creature can block or be blocked by only creatures with shadow."),
        Triple("horsemanship", "Horsemanship", "This creature can't be blocked except by creatures with horsemanship."),
        Triple("fear", "Fear", "This creature can't be blocked except by artifact creatures and/or black creatures."),
        Triple("provoke", "Provoke", "Whenever this creature attacks, you may have target creature defending player controls untap and block it if able."),
        Triple("storm", "Storm", "When you cast this spell, copy it for each spell cast before it this turn. You may choose new targets for the copies."),
        Triple("sunburst", "Sunburst", "This enters with a +1/+1 counter on it for each color of mana spent to cast it. If it isn't a creature, it gets that many charge counters instead."),
        Triple("epic", "Epic", "For the rest of the game, you can't cast spells. At the beginning of each of your upkeeps, copy this spell except for its epic ability. You may choose new targets for the copy."),
        Triple("convoke", "Convoke", "Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color."),
        Triple("haunt", "Haunt", "When this creature dies, or when this spell card is put into a graveyard after resolving, exile it haunting target creature. When the haunted creature dies, its haunt effect happens again."),
        Triple("split-second", "Split second", "As long as this spell is on the stack, players can't cast spells or activate abilities that aren't mana abilities."),
        Triple("delve", "Delve", "Each card you exile from your graveyard while casting this spell pays for {1}."),
        Triple("gravestorm", "Gravestorm", "When you cast this spell, copy it for each permanent put into a graveyard this turn. You may choose new targets for the copies."),
        Triple("changeling", "Changeling", "This card is every creature type."),
        Triple("conspire", "Conspire", "As you cast this spell, you may tap two untapped creatures you control that share a color with it. When you do, copy it and you may choose a new target for the copy."),
        Triple("persist", "Persist", "When this creature dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it."),
        Triple("wither", "Wither", "This deals damage to creatures in the form of -1/-1 counters."),
        Triple("retrace", "Retrace", "You may cast this card from your graveyard by discarding a land card in addition to paying its other costs."),
        Triple("exalted", "Exalted", "Whenever a creature you control attacks alone, that creature gets +1/+1 until end of turn for each instance of exalted."),
        Triple("cascade", "Cascade", "When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order."),
        Triple("rebound", "Rebound", "If you cast this spell from your hand, exile it as it resolves. At the beginning of your next upkeep, you may cast this card from exile without paying its mana cost."),
        Triple("umbra-armor", "Umbra armor", "If enchanted creature would be destroyed, instead remove all damage from it and destroy this Aura."),
        Triple("infect", "Infect", "This creature deals damage to creatures in the form of -1/-1 counters and to players in the form of poison counters."),
        Triple("battle-cry", "Battle cry", "Whenever this creature attacks, each other attacking creature gets +1/+0 until end of turn."),
        Triple("living-weapon", "Living weapon", "When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this to it."),
        Triple("undying", "Undying", "When this creature dies, if it had no +1/+1 counters on it, return it to the battlefield under its owner's control with a +1/+1 counter on it."),
        Triple("soulbond", "Soulbond", "You may pair this creature with another unpaired creature when either enters. They remain paired for as long as you control both of them, and both get the bonus its text describes."),
        Triple("unleash", "Unleash", "You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it."),
        Triple("cipher", "Cipher", "Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost."),
        Triple("evolve", "Evolve", "Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature."),
        Triple("extort", "Extort", "Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life."),
        Triple("fuse", "Fuse", "You may cast one or both halves of this card from your hand. Cast together, they are one spell and you pay both costs."),
        Triple("dethrone", "Dethrone", "Whenever this creature attacks the player with the most life or tied for most life, put a +1/+1 counter on it."),
        Triple("hidden-agenda", "Hidden agenda", "Start the game with this conspiracy face down in the command zone and secretly choose a card name. You may turn this conspiracy face up any time and reveal that name."),
        Triple("double-agenda", "Double agenda", "Start the game with this conspiracy face down in the command zone and secretly choose two different card names. You may turn this conspiracy face up any time and reveal those names."),
        Triple("prowess", "Prowess", "Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn."),
        Triple("exploit", "Exploit", "When this creature enters, you may sacrifice a creature. Its exploit ability says what that gets you."),
        Triple("menace", "Menace", "This creature can't be blocked except by two or more creatures."),
        Triple("devoid", "Devoid", "This card has no color."),
        Triple("ingest", "Ingest", "Whenever this creature deals combat damage to a player, that player exiles the top card of their library."),
        Triple("myriad", "Myriad", "Whenever this creature attacks, for each opponent other than defending player, you may create a token copy of it that's tapped and attacking that player or a planeswalker they control. Exile the tokens at end of combat."),
        Triple("skulk", "Skulk", "This creature can't be blocked by creatures with greater power."),
        Triple("melee", "Melee", "Whenever this creature attacks, it gets +1/+1 until end of turn for each opponent you attacked this combat."),
        Triple("partner", "Partner", "You can have two commanders if both have partner."),
        Triple("undaunted", "Undaunted", "This spell costs {1} less to cast for each opponent you have."),
        Triple("improvise", "Improvise", "Your artifacts can help cast this spell. Each artifact you tap after you're done activating mana abilities pays for {1}."),
        Triple("aftermath", "Aftermath", "Cast this spell only from your graveyard. Then exile it. The card's other half can't be cast from your graveyard."),
        Triple("ascend", "Ascend", "If you control ten or more permanents, you get the city's blessing for the rest of the game."),
        Triple("assist", "Assist", "Another player can pay any amount of the generic mana in this spell's cost."),
        Triple("jump-start", "Jump-start", "You may cast this card from your graveyard by discarding a card in addition to paying its other costs. Then exile this card."),
        Triple("mentor", "Mentor", "Whenever this creature attacks, put a +1/+1 counter on target attacking creature with lesser power."),
        Triple("riot", "Riot", "This creature enters with your choice of a +1/+1 counter or haste."),
        Triple("demonstrate", "Demonstrate", "When you cast this spell, you may copy it. If you do, choose an opponent to also copy it. Players may choose new targets for their copies."),
        Triple("daybound", "Daybound", "If a player casts no spells during their own turn, it becomes night next turn. As it becomes night, this permanent transforms to its nightbound face."),
        Triple("nightbound", "Nightbound", "If a player casts at least two spells during their own turn, it becomes day next turn. As it becomes day, this permanent transforms to its daybound face."),
        Triple("decayed", "Decayed", "This creature can't block. When it attacks, sacrifice it at end of combat."),
        Triple("training", "Training", "Whenever this creature attacks with another creature with greater power, put a +1/+1 counter on this creature."),
        Triple("compleated", "Compleated", "Each Phyrexian mana symbol in this planeswalker's cost can be paid with 2 life. For each one paid with life, it enters with two fewer loyalty counters."),
        Triple("enlist", "Enlist", "As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn."),
        Triple("read-ahead", "Read ahead", "Choose a chapter and start with that many lore counters. Add one after your draw step. Skipped chapters don't trigger."),
        Triple("ravenous", "Ravenous", "This creature enters with X +1/+1 counters on it. If X is 5 or more, draw a card when it enters."),
        Triple("living-metal", "Living metal", "As long as it's your turn, this Vehicle is also a creature."),
        Triple("for-mirrodin", "For Mirrodin!", "When this Equipment enters, create a 2/2 red Rebel creature token, then attach this to it."),
        Triple("bargain", "Bargain", "You may sacrifice an artifact, enchantment, or token as you cast this spell. If you do, it does more, as its text says."),
        Triple("spree", "Spree", "Choose one or more additional costs. Each mode you choose adds the cost beside it to this spell's cost."),
        Triple("start-your-engines", "Start your engines!", "If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4."),
        Triple("job-select", "Job select", "When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it."),
        Triple("tiered", "Tiered", "Choose one additional cost. The mode you choose adds the cost beside it to this spell's cost."),
        Triple("station", "Station", "Tap another untapped creature you control: Put a number of charge counters on this permanent equal to the tapped creature's power. Station only as a sorcery. With enough counters, it gains what its text lists."),
        Triple("increment", "Increment", "Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature."),
        Triple("paradigm", "Paradigm", "Then exile this spell. After you first resolve a spell with this name, at the beginning of each of your first main phases, you may cast a copy of it from exile without paying its mana cost."),
        Triple("storied", "Storied", "If you control three or more artifacts, Sagas, and/or legendary permanents, you have an enduring story for the rest of the game."),
        Triple("rampage", "Rampage", "Whenever this creature becomes blocked, it gets +N/+N until end of turn for each creature blocking it beyond the first."),
        Triple("fading", "Fading", "This enters with N fade counters on it. At the beginning of your upkeep, remove a fade counter from it. If you can't, sacrifice it."),
        Triple("amplify", "Amplify", "As this creature enters, put N +1/+1 counters on it for each card you reveal in your hand that shares a creature type with it."),
        Triple("modular", "Modular", "This creature enters with N +1/+1 counters on it. When it dies, you may put its +1/+1 counters on target artifact creature."),
        Triple("bushido", "Bushido", "Whenever this creature blocks or becomes blocked, it gets +N/+N until end of turn."),
        Triple("soulshift", "Soulshift", "When this creature dies, you may return target Spirit card with mana value N or less from your graveyard to your hand."),
        Triple("dredge", "Dredge", "If you would draw a card, you may mill N cards instead. If you do, return this card from your graveyard to your hand. You can't if your library has fewer than N cards."),
        Triple("bloodthirst", "Bloodthirst", "If an opponent was dealt damage this turn, this creature enters with N +1/+1 counters on it."),
        Triple("graft", "Graft", "This creature enters with N +1/+1 counters on it. Whenever another creature enters, you may move a +1/+1 counter from this creature onto it."),
        Triple("ripple", "Ripple", "When you cast this spell, you may reveal the top N cards of your library. You may cast spells with the same name as this spell from among them without paying their mana costs. Put the rest on the bottom."),
        Triple("vanishing", "Vanishing", "This permanent enters with N time counters on it. At the beginning of your upkeep, remove a time counter from it. When the last is removed, sacrifice it."),
        Triple("absorb", "Absorb", "If a source would deal damage to this creature, prevent N of that damage."),
        Triple("frenzy", "Frenzy", "Whenever this creature attacks and isn't blocked, it gets +N/+0 until end of turn."),
        Triple("poisonous", "Poisonous", "Whenever this creature deals combat damage to a player, that player gets N poison counters."),
        Triple("hideaway", "Hideaway", "When this permanent enters, look at the top N cards of your library, exile one face down, then put the rest on the bottom in a random order. Another of its abilities lets you play that card later."),
        Triple("devour", "Devour", "As this creature enters, you may sacrifice any number of creatures. This creature enters with N times that many +1/+1 counters on it."),
        Triple("annihilator", "Annihilator", "Whenever this creature attacks, defending player sacrifices N permanents."),
        Triple("tribute", "Tribute", "As this creature enters, an opponent of your choice may put N +1/+1 counters on it. If they don't, its \"if tribute wasn't paid\" ability happens."),
        Triple("renown", "Renown", "When this creature deals combat damage to a player, if it isn't renowned, put N +1/+1 counters on it and it becomes renowned."),
        Triple("crew", "Crew", "Tap any number of untapped creatures you control with total power N or more: This Vehicle becomes an artifact creature until end of turn."),
        Triple("fabricate", "Fabricate", "When this creature enters, put N +1/+1 counters on it or create N 1/1 colorless Servo artifact creature tokens."),
        Triple("afflict", "Afflict", "Whenever this creature becomes blocked, defending player loses N life."),
        Triple("afterlife", "Afterlife", "When this creature dies, create N 1/1 white and black Spirit creature tokens with flying."),
        Triple("casualty", "Casualty", "As you cast this spell, you may sacrifice a creature with power N or greater. When you do, copy this spell."),
        Triple("toxic", "Toxic", "Players dealt combat damage by this creature also get N poison counters."),
        Triple("backup", "Backup", "When this creature enters, put N +1/+1 counters on target creature. If that's another creature, it gains this creature's other abilities until end of turn."),
        Triple("saddle", "Saddle", "Tap any number of other untapped creatures you control with total power N or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery."),
        Triple("mobilize", "Mobilize", "Whenever this creature attacks, create N tapped and attacking 1/1 red Warrior creature tokens. Sacrifice them at the beginning of the next end step."),
        Triple("firebending", "Firebending", "Whenever this creature attacks, add N {R}. This mana lasts until end of combat."),
        Triple("teamwork", "Teamwork", "As you cast this spell, you may tap untapped creatures you control with total power N or more. If you do, it gets its teamwork bonus."),
        Triple("ward", "Ward", "Whenever this permanent becomes the target of a spell or ability an opponent controls, counter it unless that player pays its ward cost."),
        Triple("cumulative-upkeep", "Cumulative upkeep", "At the beginning of your upkeep, put an age counter on this permanent, then sacrifice it unless you pay its upkeep cost for each age counter on it."),
        Triple("buyback", "Buyback", "You may pay an additional buyback cost as you cast this spell. If you do, put this card into your hand as it resolves."),
        Triple("cycling", "Cycling", "Pay its cycling cost, Discard this card: Draw a card."),
        Triple("echo", "Echo", "At the beginning of your upkeep, if this came under your control since the beginning of your last upkeep, sacrifice it unless you pay its echo cost."),
        Triple("flashback", "Flashback", "You may cast this card from your graveyard for its flashback cost. Then exile it."),
        Triple("madness", "Madness", "If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard."),
        Triple("morph", "Morph", "You may cast this card face down as a 2/2 creature for {3}. Turn it face up any time for its morph cost. A megamorph card also gets a +1/+1 counter as it turns face up."),
        Triple("entwine", "Entwine", "Choose all of this spell's modes if you also pay its entwine cost."),
        Triple("ninjutsu", "Ninjutsu", "Pay its ninjutsu cost, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand tapped and attacking."),
        Triple("transmute", "Transmute", "Pay its transmute cost, Discard this card: Search your library for a card with the same mana value as this card, reveal it, put it into your hand, then shuffle. Transmute only as a sorcery."),
        Triple("fortify", "Fortify", "Pay its fortify cost: Attach to target land you control. Fortify only as a sorcery."),
        Triple("replicate", "Replicate", "When you cast this spell, copy it for each time you paid its replicate cost. You may choose new targets for the copies."),
        Triple("recover", "Recover", "When a creature is put into your graveyard from the battlefield, you may pay its recover cost. If you do, return this card from your graveyard to your hand. Otherwise, exile this card."),
        Triple("evoke", "Evoke", "You may cast this spell for its evoke cost. If you do, it's sacrificed when it enters."),
        Triple("prowl", "Prowl", "You may cast this for its prowl cost if you dealt combat damage to a player this turn with a creature that shares a creature type with it."),
        Triple("unearth", "Unearth", "Pay its unearth cost: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery."),
        Triple("level-up", "Level up", "Pay its level up cost: Put a level counter on this. Level up only as a sorcery. Enough level counters give it the abilities its text lists."),
        Triple("miracle", "Miracle", "You may cast this card for its miracle cost when you draw it if it's the first card you drew this turn."),
        Triple("overload", "Overload", "You may cast this spell for its overload cost. If you do, change \"target\" in its text to \"each.\""),
        Triple("scavenge", "Scavenge", "Pay its scavenge cost, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery."),
        Triple("bestow", "Bestow", "If you cast this card for its bestow cost, it's an Aura spell with enchant creature. It becomes a creature again if it's not attached to a creature."),
        Triple("outlast", "Outlast", "Pay its outlast cost, {T}: Put a +1/+1 counter on this creature. Outlast only as a sorcery."),
        Triple("dash", "Dash", "You may cast this spell for its dash cost. If you do, it gains haste, and it's returned from the battlefield to its owner's hand at the beginning of the next end step."),
        Triple("surge", "Surge", "You may cast this spell for its surge cost if you or a teammate has cast another spell this turn."),
        Triple("emerge", "Emerge", "You may cast this spell by sacrificing a creature and paying the emerge cost reduced by that creature's mana value."),
        Triple("escalate", "Escalate", "Pay its escalate cost for each mode chosen beyond the first."),
        Triple("embalm", "Embalm", "Pay its embalm cost, Exile this card from your graveyard: Create a token that's a copy of it, except it's a white Zombie with no mana cost. Embalm only as a sorcery."),
        Triple("eternalize", "Eternalize", "Pay its eternalize cost, Exile this card from your graveyard: Create a token that's a copy of it, except it's a 4/4 black Zombie with no mana cost. Eternalize only as a sorcery."),
        Triple("spectacle", "Spectacle", "You may cast this spell for its spectacle cost rather than its mana cost if an opponent lost life this turn."),
        Triple("escape", "Escape", "You may cast this card from your graveyard for its escape cost, which includes exiling other cards from your graveyard."),
        Triple("mutate", "Mutate", "If you cast this spell for its mutate cost, put it over or under target non-Human creature you own. They mutate into the creature on top plus all abilities from under it."),
        Triple("encore", "Encore", "Pay its encore cost, Exile this card from your graveyard: For each opponent, create a token copy that attacks that opponent this turn if able. They gain haste. Sacrifice them at the next end step. Encore only as a sorcery."),
        Triple("foretell", "Foretell", "During your turn, you may pay {2} and exile this card from your hand face down. Cast it on a later turn for its foretell cost."),
        Triple("disturb", "Disturb", "You may cast this card from your graveyard transformed for its disturb cost."),
        Triple("craft", "Craft", "Pay its craft cost, Exile this and the listed materials from among permanents you control and cards in your graveyard: Return this card transformed under its owner's control. Craft only as a sorcery."),
        Triple("cleave", "Cleave", "You may cast this spell for its cleave cost. If you do, remove the words in square brackets."),
        Triple("reconfigure", "Reconfigure", "Pay its reconfigure cost: Attach to target creature you control; or unattach from a creature. Reconfigure only as a sorcery. While attached, this isn't a creature."),
        Triple("blitz", "Blitz", "If you cast this spell for its blitz cost, it gains haste and \"When this creature dies, draw a card.\" Sacrifice it at the beginning of the next end step."),
        Triple("squad", "Squad", "As an additional cost to cast this spell, you may pay its squad cost any number of times. When this creature enters, create that many tokens that are copies of it."),
        Triple("disguise", "Disguise", "You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost."),
        Triple("plot", "Plot", "You may pay its plot cost and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery."),
        Triple("freerunning", "Freerunning", "You may cast this spell for its freerunning cost if you dealt combat damage to a player this turn with an Assassin or commander."),
        Triple("offspring", "Offspring", "You may pay an additional offspring cost as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it."),
        Triple("harmonize", "Harmonize", "You may cast this card from your graveyard for its harmonize cost. You may tap a creature you control to reduce that cost by {1} for each point of its power. Then exile this spell."),
        Triple("warp", "Warp", "You may cast this card from your hand for its warp cost. Exile this permanent at the beginning of the next end step, then you may cast it from exile on a later turn."),
        Triple("mayhem", "Mayhem", "You may cast this card from your graveyard for its mayhem cost if you discarded it this turn. Mayhem without a cost lets you play it from your graveyard that turn instead, even a land."),
        Triple("web-slinging", "Web-slinging", "You may cast this spell for its web-slinging cost if you also return a tapped creature you control to its owner's hand."),
        Triple("sneak", "Sneak", "During your declare blockers step, you may cast this spell for its sneak cost if you also return an unblocked attacker you control to its owner's hand. A creature cast this way enters tapped and attacking."),
        Triple("beam-me-up", "Beam me up", "You may cast this card from your graveyard for its beam me up cost if you also return a creature you control to its owner's hand. Then exile this card."),
        Triple("suspend", "Suspend", "Rather than cast this card from your hand, pay its suspend cost and exile it with N time counters. At the beginning of your upkeep, remove one. When the last is removed, you may cast it without paying its mana cost. A creature has haste."),
        Triple("reinforce", "Reinforce", "Pay its reinforce cost, Discard this card: Put N +1/+1 counters on target creature."),
        Triple("awaken", "Awaken", "If you cast this spell for its awaken cost, also put N +1/+1 counters on target land you control and it becomes a 0/0 Elemental creature with haste. It's still a land."),
        Triple("impending", "Impending", "If you cast this spell for its impending cost, it enters with N time counters and isn't a creature until the last is removed. At the beginning of your end step, remove a time counter from it."),
        Triple("equip", "Equip", "Pay its equip cost: Attach to target creature you control. Equip only as a sorcery."),
        Triple("kicker", "Kicker", "You may pay an additional kicker cost as you cast this spell. With multikicker, you may pay it any number of times."),
        Triple("enchant", "Enchant", "This Aura targets an object of the named kind as you cast it, and it can be attached only to an object of that kind."),
        Triple("protection", "Protection", "This can't be blocked, targeted, dealt damage, enchanted, or equipped by anything with the named quality."),
        Triple("landwalk", "Landwalk", "This creature can't be blocked as long as defending player controls a land of the type it names, such as a Forest for forestwalk."),
        Triple("affinity", "Affinity", "This spell costs {1} less to cast for each permanent of the named kind you control."),
        Triple("splice", "Splice", "As you cast a spell of the named kind, you may reveal this card from your hand and pay its splice cost. If you do, add this card's effects to that spell."),
        Triple("partner-with", "Partner with", "When this creature enters, target player may put its named partner into their hand from their library, then shuffle. As commanders, the two can lead one deck together."),
        Triple("commander-ninjutsu", "Commander ninjutsu", "Pay its commander ninjutsu cost, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand or the command zone tapped and attacking."),
        Triple("companion", "Companion", "If your deck meets its condition, you may choose this card as your companion and keep it outside the game. Once each game, you may pay {3} any time you could cast a sorcery to put it into your hand."),
        Triple("prototype", "Prototype", "You may cast this spell with different mana cost, color, and size. It keeps its abilities and types."),
        Triple("gift", "Gift", "You may promise an opponent a gift as you cast this spell. If you do, they get the gift and the spell does more, as its text says."),
        Triple("class-level", "Level", "Gain the next level as a sorcery to add its ability. Pay a level's cost to reach it; this Class has the abilities of every level it has reached."),
    )

    // Keyword actions (CR 701): the [KeywordAction] types by serial name, then scry, surveil, mill and fight
    private val ACTIONS: List<Triple<String, String, String>> = listOf(
        Triple("investigate", "Investigate", "Create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\""),
        Triple("proliferate", "Proliferate", "Choose any number of permanents and/or players, then give each another counter of each kind already there."),
        Triple("populate", "Populate", "Create a token that's a copy of a creature token you control."),
        Triple("explore", "Explore", "Reveal the top card of your library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on this creature, then put the card back or put it into your graveyard."),
        Triple("amass", "Amass", "Put N +1/+1 counters on an Army you control. If you don't control one, create a 0/0 black Army creature token of the named creature type first."),
        Triple("incubate", "Incubate", "Create an Incubator token with N +1/+1 counters on it and \"{2}: Transform this token.\" It transforms into a 0/0 Phyrexian artifact creature."),
        Triple("connive", "Connive", "Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature."),
        Triple("discover", "Discover", "Exile cards from the top of your library until you exile a nonland card with mana value N or less. Cast it without paying its mana cost or put it into your hand. Put the rest on the bottom in a random order."),
        Triple("venture", "Venture into the dungeon", "Enter the first room or advance to the next room. If you aren't in a dungeon, choose one and enter its first room. Then do what the room says."),
        Triple("venture-into", "Venture into", "If you aren't in a dungeon, enter the first room of the named dungeon. Otherwise, advance to the next room of the dungeon you're in. Then do what the room says."),
        Triple("manifest", "Manifest", "Put the top card of your library onto the battlefield face down as a 2/2 creature. Turn it face up any time for its mana cost if it's a creature card."),
        Triple("manifest-dread", "Manifest dread", "Look at the top two cards of your library. Put one onto the battlefield face down as a 2/2 creature and the other into your graveyard. Turn it face up any time for its mana cost if it's a creature card."),
        Triple("monstrosity", "Monstrosity", "If this creature isn't monstrous, put N +1/+1 counters on it and it becomes monstrous."),
        Triple("adapt", "Adapt", "If this creature has no +1/+1 counters on it, put N +1/+1 counters on it."),
        Triple("goad", "Goad", "Until your next turn, that creature attacks each combat if able and attacks a player other than you if able."),
        Triple("learn", "Learn", "You may reveal a Lesson card you own from outside the game and put it into your hand, or discard a card to draw a card."),
        Triple("double", "Double", "Double a number: put as many more counters, or give as much more life, power, toughness, or mana as there already is."),
        Triple("bolster", "Bolster", "Choose a creature with the least toughness among creatures you control and put N +1/+1 counters on it."),
        Triple("support", "Support", "Put a +1/+1 counter on each of up to N other target creatures."),
        Triple("detain", "Detain", "Until your next turn, that permanent can't attack or block and its activated abilities can't be activated."),
        Triple("endure", "Endure", "Put N +1/+1 counters on this creature or create an N/N white Spirit creature token."),
        Triple("forage", "Forage", "Exile three cards from your graveyard or sacrifice a Food."),
        Triple("foretell", "Foretell", "Exile this card from your hand face down. You may cast it on a later turn for its foretell cost."),
        Triple("cloak", "Cloak", "Put that card onto the battlefield face down as a 2/2 creature with ward {2}. Turn it face up any time for its mana cost if it's a creature card."),
        Triple("time-travel", "Time travel", "For each suspended card you own and each permanent you control with a time counter on it, you may add or remove a time counter."),
        Triple("ring-tempts", "The Ring tempts you", "Choose a creature you control as your Ring-bearer. Each time the Ring tempts you, you must choose one if you can, and your Ring-bearer gains the Ring's next ability."),
        Triple("exert", "Exert", "An exerted creature won't untap during your next untap step."),
        Triple("behold", "Behold", "Choose a permanent of the named kind you control or reveal a card of that kind from your hand."),
        Triple("collect-evidence", "Collect evidence", "Exile cards with total mana value N or greater from your graveyard."),
        Triple("earthbend", "Earthbend", "Target land you control becomes a 0/0 creature with haste that's still a land. Put N +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped."),
        Triple("regenerate", "Regenerate", "The next time it would be destroyed this turn, instead tap it, remove all damage from it, and remove it from combat."),
        Triple("empower-jace", "Empower Jace", "If you don't control a Jace planeswalker token, create a blue Jace planeswalker token. Then put N loyalty counters on a Jace token you control."),
        Triple("recruit", "Recruit", "Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token."),
        Triple("airbend", "Airbend", "Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost."),
        Triple("meld", "Meld", "Exile the two cards of a meld pair, then put them onto the battlefield as one permanent showing their combined back face."),
        Triple("blight", "Blight", "Put N -1/-1 counters on a creature you control."),
        Triple("scry", "Scry", "Look at the top N cards of your library, then put any number of them on the bottom and the rest on top in any order."),
        Triple("surveil", "Surveil", "Look at the top N cards of your library, then put any number of them into your graveyard and the rest on top in any order."),
        Triple("mill", "Mill", "Put the top N cards of your library into your graveyard."),
        Triple("fight", "Fight", "Each of two creatures deals damage equal to its power to the other."),
    )

    // Token kinds (CR 111.10): (id, name as cards print it, explanation); Role tokens by kind, then Role itself. Written as a
    // card's reminder text says what a token is (owner 2026-11-20): its card type, then its abilities as the token has them
    // (PredefinedTokens' texts) in quotes
    private val TOKENS: List<Triple<String, String, String>> = listOf(
        Triple("treasure", "Treasure", "It's an artifact token with \"{T}, Sacrifice this token: Add one mana of any color.\""),
        Triple("food", "Food", "It's an artifact token with \"{2}, {T}, Sacrifice this token: You gain 3 life.\""),
        Triple("gold", "Gold", "It's an artifact token with \"Sacrifice this token: Add one mana of any color.\""),
        Triple("walker", "Walker", "It's a 2/2 black Zombie creature token named Walker."),
        Triple("shard", "Shard", "It's an enchantment token with \"{2}, Sacrifice this token: Scry 1, then draw a card.\""),
        Triple("clue", "Clue", "It's an artifact token with \"{2}, Sacrifice this token: Draw a card.\""),
        Triple("blood", "Blood", "It's an artifact token with \"{1}, {T}, Discard a card, Sacrifice this token: Draw a card.\""),
        Triple("heartwood", "Heartwood", "It's a red and green artifact token with \"{T}: Add {R} or {G}.\""),
        Triple("mutagen", "Mutagen", "It's an artifact token with \"{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.\""),
        Triple("lander", "Lander", "It's an artifact token with \"{2}, {T}, Sacrifice this token: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\""),
        Triple("incubator", "Incubator", "It's an artifact token with \"{2}: Transform this token.\" It transforms into a 0/0 Phyrexian artifact creature, keeping its counters."),
        Triple("map", "Map", "It's an artifact token with \"{1}, {T}, Sacrifice this token: Target creature you control explores. Activate only as a sorcery.\""),
        Triple("powerstone", "Powerstone", "It's an artifact token with \"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\""),
        Triple("cursed-role", "Cursed Role", "It's an Aura token with enchant creature and \"Enchanted creature has base power and toughness 1/1.\" If you put another Role on that creature, the older one goes to the graveyard."),
        Triple("monster-role", "Monster Role", "It's an Aura token with enchant creature and \"Enchanted creature gets +1/+1 and has trample.\" If you put another Role on that creature, the older one goes to the graveyard."),
        Triple("royal-role", "Royal Role", "It's an Aura token with enchant creature and \"Enchanted creature gets +1/+1 and has ward {1}.\" If you put another Role on that creature, the older one goes to the graveyard."),
        Triple("sorcerer-role", "Sorcerer Role", "It's an Aura token with enchant creature and \"Enchanted creature gets +1/+1 and has 'Whenever this creature attacks, scry 1.'\" If you put another Role on that creature, the older one goes to the graveyard."),
        Triple("virtuous-role", "Virtuous Role", "It's an Aura token with enchant creature and \"Enchanted creature gets +1/+1 for each enchantment you control.\" If you put another Role on that creature, the older one goes to the graveyard."),
        Triple("wicked-role", "Wicked Role", "It's an Aura token with enchant creature, \"Enchanted creature gets +1/+1.\" and \"When this token is put into a graveyard from the battlefield, each opponent loses 1 life.\" If you put another Role on that creature, the older one goes to the graveyard."),
        Triple("young-hero-role", "Young Hero Role", "It's an Aura token with enchant creature and \"Enchanted creature has 'Whenever this creature attacks, if its toughness is 3 or less, put a +1/+1 counter on it.'\" If you put another Role on that creature, the older one goes to the graveyard."),
        Triple("role", "Role", "It's an Aura token with enchant creature; each kind of Role (Monster, Royal …) gives the enchanted creature its own bonus. If you put another Role on a creature, your older Role there goes to the graveyard."),
    )

    // Other rules words (CR 701, 702): the [AbilityLabel]s by serial name (solved is a state), then actions and words of their own
    private val TERMS: List<Triple<String, String, String>> = listOf(
        Triple("boast", "Boast", "Activate only if this creature attacked this turn and only once each turn."),
        Triple("exhaust", "Exhaust", "Activate each exhaust ability only once. A permanent that leaves the battlefield and returns is a new object."),
        Triple("max-speed", "Max speed", "This has the ability after max speed only as long as your speed is 4, the highest it goes."),
        Triple("power-up", "Power-up", "Activate each power-up ability only once. If this permanent entered this turn, its power-up cost is reduced by its mana cost."),
        Triple("infinity", "Infinity", "This permanent has its abilities marked with the infinity symbol only while it's harnessed."),
        Triple("forecast", "Forecast", "Activate a forecast ability only by revealing this card from your hand, only during your upkeep, and only once each turn. The card stays in your hand."),
        Triple("harness", "Harness", "Harness this permanent: it becomes harnessed until it leaves the battlefield, turning on its abilities marked with the infinity symbol."),
        Triple("transform", "Transform", "Turn this double-faced permanent over to its other face. It's still the same permanent, with the same counters, damage, and attachments."),
        Triple("suspect", "Suspect", "Suspect a creature: it becomes suspected. A suspected creature has menace and can't block until it stops being suspected."),
        Triple("face-a-dilemma", "Face a dilemma", "You face a dilemma each time you choose one or more modes for a spell or ability."),
        Triple("assimilate", "Assimilate", "Put that card onto the battlefield under your control with a +1/+1 counter on it. It's a Borg artifact creature and loses its other creature types."),
        Triple("waterbend", "Waterbend", "While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}."),
        Triple("triple", "Triple", "Make a number three times as much: a creature's power and toughness, counters, damage, or the like."),
        Triple("descend", "Descend", "You descend whenever a permanent card is put into your graveyard from anywhere. A descend N ability applies while there are N or more permanent cards in your graveyard."),
    )
}
