package arcanum.api

/**
 * Plain-words explanations of the keywords ([KeywordKind]), keyword actions ([KeywordAction], and scry, surveil, mill
 * and fight, which are effects of their own), states ([states]), token kinds ([tokens]) and other rules words ([terms]) the
 * libraries know, for players who hold one to see what it means, and the ones a card's text leaves unexplained
 * ([reminders]).
 *
 * Every text here is written for this library in its own words: a short summary of what the keyword does in play, never
 * the Comprehensive Rules' text or a card's reminder text. They simplify (corner cases are left out); the rules and the
 * engine decide. A test checks that every [KeywordKind.all] and every [KeywordAction] type has an explanation, and the
 * parser's tests that every keyword it reads has one.
 */
object RulesGlossary {
    /** One explanation: [name] as cards print it ("Flying", "Collect evidence"), [text] what it means in plain words;
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
     * token ([PredefinedTokens.names]) and "Role" itself. Written and kept like the keywords' explanations; a test checks
     * that every predefined token has one.
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
        Triple("ring-bearer", "Ring-bearer", "The creature its controller chose when the Ring tempted them. It stays the Ring-bearer until they choose another or someone else gains control of it, and it gets more with each of the Ring's levels."),
        Triple("goaded", "Goaded", "It must attack each combat if it can, and must go after someone other than the player who goaded it if it can. It lasts until that player's next turn."),
        Triple("monstrous", "Monstrous", "It has used its monstrosity: it can't do so again, and its abilities that care about being monstrous work."),
        Triple("renowned", "Renowned", "It has dealt combat damage to a player and got its renown counters; it can't become renowned again."),
        Triple("suspected", "Suspected", "It has menace and can't block for as long as it is suspected."),
        Triple("saddled", "Saddled", "A Mount that creatures tapped to saddle. It stays saddled until the end of the turn, and its abilities that care about it work."),
        Triple("solved", "Solved", "This Case's goal was met at an end step: its \"solved\" abilities work from now on."),
        Triple("prepared", "Prepared", "Its controller may cast the copy of its prepare spell, once; casting it unprepares this permanent."),
        Triple("harnessed", "Harnessed", "It stays harnessed until it leaves the battlefield, and its abilities with the infinity symbol work."),
        Triple("unlocked", "Unlocked", "This half of the Room is unlocked: its abilities work. A locked half does nothing until its door is unlocked."),
        Triple("phased-out", "Phased out", "It acts as though it doesn't exist. It phases back in during its controller's next untap step."),
        Triple("plotted", "Plotted", "Its owner may cast it from exile without paying its mana cost, in a main phase of a later turn while the Stack is empty."),
        Triple("foretold", "Foretold", "Exiled face down; its owner may look at it and cast it for its foretell cost on a later turn."),
        Triple("warped", "Warped", "Exiled at the end step of the turn it was cast for its warp cost; its owner may cast it on a later turn."),
    )

    // A player's and the game's states: (id, name as a player reads it, explanation)
    private val PLAYER_STATES: List<Triple<String, String, String>> = listOf(
        Triple("monarch", "Monarch", "This player draws an extra card at each of their end steps. A creature that deals combat damage to the monarch makes its controller the monarch."),
        Triple("initiative", "Initiative", "This player ventures into the Undercity when they take it and at each of their upkeeps. A creature that deals combat damage to them takes it for its controller."),
        Triple("citys-blessing", "City's blessing", "This player controlled ten or more permanents with ascend. They keep the blessing for the rest of the game, and their ascend abilities work."),
        Triple("enduring-story", "Enduring story", "Storied gave this player a story that lasts for the rest of the game."),
        Triple("speed", "Speed", "A player's speed runs from 1 to 4. It goes up by one the first time an opponent loses life on each of their turns. Some abilities work only at the highest speed."),
        Triple("the-ring", "The Ring", "Each time the Ring tempts a player they choose a Ring-bearer and the Ring gains a level, up to 4, giving their Ring-bearer more."),
        Triple("day", "Day", "It stays day while each turn's active player casts a spell. A turn with no spell cast makes it night; daybound permanents then transform."),
        Triple("night", "Night", "It stays night until a player casts two or more spells during their own turn, which makes it day; nightbound permanents then transform."),
        Triple("poison", "Poison", "A player with ten or more poison counters loses the game."),
        Triple("energy", "Energy", "Counters a player keeps and pays with for abilities that cost {E}."),
        Triple("rad", "Rad", "At the start of their first main phase the player mills that many cards, loses 1 life and removes one rad counter for each nonland card milled."),
        Triple("experience", "Experience", "Counters a player keeps for the cards that count them."),
        Triple("dungeons-completed", "Dungeons completed", "How many dungeons this player has finished this game. Some cards care."),
        Triple("extra-turn", "Extra turn", "This player takes that many extra turns after the current one."),
        Triple("skipped-turn", "Skips a turn", "This player skips that many of their next turns."),
    )

    // Keywords (CR 702), in [KeywordKind.all]'s order: (id, printed name, explanation)
    private val KEYWORDS: List<Triple<String, String, String>> = listOf(
        Triple("deathtouch", "Deathtouch", "Even 1 damage from it destroys a creature."),
        Triple("defender", "Defender", "It never attacks, but it can block."),
        Triple("double-strike", "Double strike", "Deals its combat damage twice: once in an early damage step with first strikers, then again in the normal one."),
        Triple("first-strike", "First strike", "It hits first: its combat damage comes in an earlier step than that of creatures lacking first strike."),
        Triple("flash", "Flash", "It can be cast at instant speed, for example during another player's turn or in answer to a spell."),
        Triple("flying", "Flying", "Only creatures with flying or reach can block it."),
        Triple("haste", "Haste", "It can attack and use its {T} abilities right away, without waiting for your next turn."),
        Triple("hexproof", "Hexproof", "Your opponents' spells and abilities can't target it. Yours still can."),
        Triple("indestructible", "Indestructible", "Nothing destroys it: lethal damage and \"destroy\" effects leave it in play (sacrifice, exile or 0 toughness still remove it)."),
        Triple("intimidate", "Intimidate", "Only artifact creatures and creatures that share one of its colors can block it."),
        Triple("lifelink", "Lifelink", "Its controller gains life equal to any damage it deals."),
        Triple("reach", "Reach", "It can stop fliers: it may block creatures with flying."),
        Triple("shroud", "Shroud", "No spell or ability can target it, not even its controller's."),
        Triple("trample", "Trample", "When it's blocked, the damage its blockers don't need to be dealt lethal damage can go on to the player or permanent it attacks."),
        Triple("trample-over-planeswalkers", "Trample over planeswalkers", "Like trample, and when it attacks a planeswalker, damage beyond what that planeswalker needs can go on to its controller."),
        Triple("vigilance", "Vigilance", "It stays untapped when it attacks, so it is ready to block or use {T} abilities."),
        Triple("banding", "Banding", "It can attack together with others as a band that is blocked as one group; in its fights you, not the other player, decide how the other side's damage is split."),
        Triple("flanking", "Flanking", "Creatures without flanking that block it get -1/-1 for the turn."),
        Triple("phasing", "Phasing", "Every other turn of yours it phases out before you untap: until it phases back in, it's treated as though it weren't there."),
        Triple("shadow", "Shadow", "It fights only in the shadows: it blocks and is blocked only by creatures with shadow."),
        Triple("horsemanship", "Horsemanship", "Only creatures with horsemanship can block it."),
        Triple("fear", "Fear", "Only artifact creatures and black creatures can block it."),
        Triple("provoke", "Provoke", "When it attacks, you may make a creature of the defending player untap and block it if it can."),
        Triple("storm", "Storm", "When you cast it, you get a copy of it for every other spell cast before it this turn."),
        Triple("sunburst", "Sunburst", "It enters with one counter for each color of mana spent on it: +1/+1 counters on a creature, charge counters otherwise."),
        Triple("epic", "Epic", "After this spell you can't cast spells for the rest of the game, but at the start of each of your upkeeps you get a copy of it."),
        Triple("convoke", "Convoke", "Tapping your creatures helps pay for it: each tapped creature covers {1} or one mana of a color it has."),
        Triple("haunt", "Haunt", "When it leaves (or the spell resolves), it's exiled haunting a creature; when that creature dies, the haunt effect happens again."),
        Triple("split-second", "Split second", "While this spell is on the Stack, nobody can cast spells or activate abilities other than mana abilities."),
        Triple("delve", "Delve", "Exiling cards from your graveyard as you cast it helps pay: {1} less for each card."),
        Triple("gravestorm", "Gravestorm", "When you cast it, you get a copy of it for every permanent that went to a graveyard this turn."),
        Triple("changeling", "Changeling", "It has every creature type, wherever it is."),
        Triple("conspire", "Conspire", "While casting it you may tap two untapped creatures of yours that share a color with it to get a copy of it."),
        Triple("persist", "Persist", "When it dies without a -1/-1 counter on it, it comes back to the battlefield with one."),
        Triple("wither", "Wither", "Its damage to creatures is dealt as -1/-1 counters, which stay."),
        Triple("retrace", "Retrace", "You can also cast it from your graveyard by discarding a land card on top of its cost."),
        Triple("exalted", "Exalted", "Whenever one of your creatures attacks alone, it gets +1/+1 until end of turn for each exalted you have."),
        Triple("cascade", "Cascade", "When you cast it, reveal cards from your library until a nonland card that costs less; you may cast that card for free. The rest go to the bottom."),
        Triple("rebound", "Rebound", "Cast from your hand, it's exiled as it resolves, and at your next upkeep you may cast it again from exile for free."),
        Triple("umbra-armor", "Umbra armor", "If the creature it enchants would be destroyed, this Aura is destroyed instead and the creature's damage is removed."),
        Triple("infect", "Infect", "Its damage to creatures is dealt as -1/-1 counters and to players as poison counters (ten poison counters and a player loses)."),
        Triple("battle-cry", "Battle cry", "When it attacks, all the other attackers get +1/+0 for the turn."),
        Triple("living-weapon", "Living weapon", "It arrives wielded: a 0/0 black Phyrexian Germ creature token is made and this Equipment is attached to it."),
        Triple("undying", "Undying", "When it dies without a +1/+1 counter on it, it comes back to the battlefield with one."),
        Triple("soulbond", "Soulbond", "When it or another creature of yours enters, the two may pair up; while both stay, both get the bonus it describes."),
        Triple("unleash", "Unleash", "It may enter with a +1/+1 counter, but while it has one it can't block."),
        Triple("cipher", "Cipher", "As it resolves you may encode it on a creature of yours; whenever that creature deals combat damage to a player, you may cast a copy of it for free."),
        Triple("evolve", "Evolve", "Whenever a creature with greater power or greater toughness enters under your control, this gets a +1/+1 counter."),
        Triple("extort", "Extort", "Each spell you cast lets you pay {W/B} more to drain each opponent for 1 life, which you gain."),
        Triple("fuse", "Fuse", "From your hand you may cast both halves of this split card together as one spell, paying both costs."),
        Triple("dethrone", "Dethrone", "Attacking the player with the highest life total (or tied for it) gives it a +1/+1 counter."),
        Triple("hidden-agenda", "Hidden agenda", "Starts the game face down with a card name chosen in secret; turn it up any time to show the name and get its bonus."),
        Triple("double-agenda", "Double agenda", "Like hidden agenda, but two card names are chosen in secret."),
        Triple("prowess", "Prowess", "Each noncreature spell you cast pumps it by +1/+1 for the rest of the turn."),
        Triple("exploit", "Exploit", "When it enters, you may sacrifice a creature (it may be this one); its exploit ability says what that gets you."),
        Triple("menace", "Menace", "It can't be blocked by just one creature: blocking it takes two or more."),
        Triple("devoid", "Devoid", "It is colorless, whatever colored mana its cost shows."),
        Triple("ingest", "Ingest", "Each time its combat damage hits a player, that player's top library card is exiled."),
        Triple("myriad", "Myriad", "Whenever it attacks, it may bring tapped, attacking token copies of itself against each other opponent; they're exiled at end of combat."),
        Triple("skulk", "Skulk", "Only creatures with power no greater than its own can block it."),
        Triple("melee", "Melee", "When it attacks it gets +1/+1 for the turn per opponent you are attacking this combat."),
        Triple("partner", "Partner", "Two commanders with partner can lead one deck together."),
        Triple("undaunted", "Undaunted", "Each opponent you have makes it {1} cheaper to cast."),
        Triple("improvise", "Improvise", "Tapping your artifacts helps pay for it: {1} less for each artifact tapped."),
        Triple("aftermath", "Aftermath", "This half of the card can be cast only from your graveyard, and the card is exiled afterwards; the other half only from elsewhere."),
        Triple("ascend", "Ascend", "Control ten permanents at once and you keep the city's blessing for the rest of the game."),
        Triple("assist", "Assist", "Another player may help pay the generic mana in its cost."),
        Triple("jump-start", "Jump-start", "Castable again from your graveyard if you also discard a card; afterwards it is exiled."),
        Triple("mentor", "Mentor", "When it attacks, a weaker attacking creature (less power) gets a +1/+1 counter."),
        Triple("riot", "Riot", "As it enters, choose: it gets a +1/+1 counter, or it has haste."),
        Triple("demonstrate", "Demonstrate", "When you cast it, you may copy it; if you do, an opponent you choose copies it too."),
        Triple("daybound", "Daybound", "The day side of a card that turns over as day and night change: once a turn passes with no spells cast by its active player, it becomes night."),
        Triple("nightbound", "Nightbound", "The night side of a card that turns over as day and night change: once a player casts two spells in their turn, it becomes day."),
        Triple("decayed", "Decayed", "It never blocks, and once it attacks it is sacrificed when combat ends."),
        Triple("training", "Training", "Attacking alongside a stronger creature (more power) gives it a +1/+1 counter."),
        Triple("compleated", "Compleated", "Phyrexian mana in its cost may be paid with life; for each paid that way it enters with two fewer loyalty counters."),
        Triple("enlist", "Enlist", "As it attacks, you may tap one of your creatures that isn't attacking (and could attack) to add that creature's power to it until end of turn."),
        Triple("read-ahead", "Read ahead", "As this Saga enters, you pick the chapter it starts on; the chapters before it don't happen."),
        Triple("ravenous", "Ravenous", "X +1/+1 counters come with it, and an X of 5 or more also draws you a card as it enters."),
        Triple("living-metal", "Living metal", "During your turn it's also an artifact creature."),
        Triple("for-mirrodin", "For Mirrodin!", "It arrives wielded: a 2/2 red Rebel creature token is made and this Equipment is attached to it."),
        Triple("bargain", "Bargain", "As you cast it you may also sacrifice an artifact, enchantment or token; bargained, it does more, as its text says."),
        Triple("spree", "Spree", "Choose one or more of its modes; each mode you choose adds its own cost."),
        Triple("start-your-engines", "Start your engines!", "You get a speed (starting at 1) that goes up by one, at most once a turn and to at most 4, when an opponent loses life on your turn; some cards get better at top speed."),
        Triple("job-select", "Job select", "It arrives wielded: a 1/1 colorless Hero creature token is made and this Equipment is attached to it."),
        Triple("tiered", "Tiered", "Choose one of its modes; each mode adds its own cost."),
        Triple("station", "Station", "Tap another creature of yours to put charge counters on this equal to that creature's power (only when you could cast a sorcery); with enough counters it gains what its text lists."),
        Triple("increment", "Increment", "Whenever you cast a spell, if more mana was spent on it than this creature's power or toughness, it gets a +1/+1 counter."),
        Triple("paradigm", "Paradigm", "The first time it resolves for you, it's exiled, and from then on at the start of each of your first main phases you may cast a copy of it for free."),
        Triple("storied", "Storied", "You're storied while you control three or more artifacts, Sagas or legendary permanents; other cards check for it."),
        Triple("rampage", "Rampage", "For each blocker past the first, it gets +N/+N for the turn."),
        Triple("fading", "Fading", "It enters with N fade counters; at each of your upkeeps remove one, and when you can't, sacrifice it."),
        Triple("amplify", "Amplify", "As it enters, reveal cards in your hand that share a creature type with it; it gets N +1/+1 counters for each one."),
        Triple("modular", "Modular", "It starts with N +1/+1 counters; when it dies, those counters can move to an artifact creature."),
        Triple("bushido", "Bushido", "In any fight where it blocks or is blocked, it gets +N/+N for the turn."),
        Triple("soulshift", "Soulshift", "Its death can bring a Spirit card (mana value N or less) back from your graveyard to your hand."),
        Triple("dredge", "Dredge", "Instead of drawing a card, you may mill N cards to return this card from your graveyard to your hand (only with N cards left in your library)."),
        Triple("bloodthirst", "Bloodthirst", "It enters with N +1/+1 counters when an opponent has been dealt damage this turn."),
        Triple("graft", "Graft", "It enters with N +1/+1 counters; whenever another creature enters, you may move one of them onto it."),
        Triple("ripple", "Ripple", "When you cast it, you may reveal the top N cards of your library and cast any with the same name for free; the rest go to the bottom."),
        Triple("vanishing", "Vanishing", "It enters with N time counters; at each of your upkeeps remove one, and when the last is gone, sacrifice it."),
        Triple("absorb", "Absorb", "Damage dealt to it is reduced by N each time."),
        Triple("frenzy", "Frenzy", "Left unblocked when attacking, it gets +N/+0 for the turn."),
        Triple("poisonous", "Poisonous", "Whenever it deals combat damage to a player, a trigger then gives that player N poison counters (ten and they lose)."),
        Triple("hideaway", "Hideaway", "When it enters, look at the top N cards of your library and exile one face down; another ability of it lets you play that card later for free."),
        Triple("devour", "Devour", "As it enters, you may sacrifice any number of creatures; it gets N +1/+1 counters for each one."),
        Triple("annihilator", "Annihilator", "Each attack forces the defending player to sacrifice N permanents."),
        Triple("tribute", "Tribute", "As it enters, an opponent chooses: put N +1/+1 counters on it, or let its \"if tribute wasn't paid\" effect happen."),
        Triple("renown", "Renown", "The first time it deals combat damage to a player, it gets N +1/+1 counters and becomes renowned."),
        Triple("crew", "Crew", "Tap your creatures with combined power of N or more to turn this Vehicle into an artifact creature for the turn."),
        Triple("fabricate", "Fabricate", "As it arrives you pick: N +1/+1 counters on it, or N 1/1 colorless Servo artifact creature tokens."),
        Triple("afflict", "Afflict", "Blocking it costs the defending player N life."),
        Triple("afterlife", "Afterlife", "Its death leaves behind N 1/1 white and black flying Spirit tokens."),
        Triple("casualty", "Casualty", "Sacrificing a creature with power N or more while casting it gets you a copy of the spell."),
        Triple("toxic", "Toxic", "Its combat damage to a player also gives that player N poison counters, at once (ten and they lose)."),
        Triple("backup", "Backup", "On entering it hands out N +1/+1 counters to a creature; given to another creature, that one also borrows its other abilities for the turn."),
        Triple("saddle", "Saddle", "Tap other creatures of yours with combined power N or more (only when you could cast a sorcery) and this Mount is saddled for the turn."),
        Triple("mobilize", "Mobilize", "Its attacks bring N 1/1 red Warrior tokens into the fight, tapped and attacking; they are sacrificed at the next end step."),
        Triple("firebending", "Firebending", "Whenever it attacks, you add N {R}, which stays until end of combat."),
        Triple("teamwork", "Teamwork", "As you cast it you may also tap your untapped creatures with total power N or more, to get its teamwork bonus."),
        Triple("ward", "Ward", "Opponents must pay the ward cost each time they target it, or their spell or ability is countered."),
        Triple("cumulative-upkeep", "Cumulative upkeep", "At each of your upkeeps it gets an age counter, then you pay the cost once per age counter or sacrifice it."),
        Triple("buyback", "Buyback", "You may pay the buyback cost as well when you cast it; if you do, it goes back to your hand instead of the graveyard."),
        Triple("cycling", "Cycling", "From your hand, pay its cycling cost and discard it to draw a fresh card."),
        Triple("echo", "Echo", "At your first upkeep after it comes under your control, pay its echo cost again or sacrifice it."),
        Triple("flashback", "Flashback", "A second life: it can be cast again from your graveyard by paying its flashback cost, and is exiled afterwards."),
        Triple("madness", "Madness", "When you discard it, you may cast it for its madness cost instead of putting it into your graveyard."),
        Triple("morph", "Morph", "You can cast it face down as a nameless 2/2 creature for {3}, and turn it face up any time by paying its morph cost."),
        Triple("entwine", "Entwine", "You may choose all of its modes by paying the entwine cost as well."),
        Triple("ninjutsu", "Ninjutsu", "Pay its ninjutsu cost and return one of your unblocked attackers to your hand: put this card onto the battlefield tapped and attacking instead."),
        Triple("transmute", "Transmute", "Pay its transmute cost and discard it (only when you could cast a sorcery): search your library for a card with the same mana value and put it into your hand."),
        Triple("fortify", "Fortify", "Pay its fortify cost: attach this Fortification to a land you control (only when you could cast a sorcery)."),
        Triple("replicate", "Replicate", "As you cast it, pay its replicate cost any number of times to get that many copies."),
        Triple("recover", "Recover", "When a creature goes to your graveyard from the battlefield while this card is there, pay its recover cost to return it to your hand, or else exile it."),
        Triple("evoke", "Evoke", "Cast cheaply for its evoke cost, it is sacrificed right after it enters (what happens as it enters still happens)."),
        Triple("prowl", "Prowl", "You may cast it for its prowl cost if a creature of yours sharing one of its creature types dealt combat damage to a player this turn."),
        Triple("unearth", "Unearth", "Pay its unearth cost (only when you could cast a sorcery): it returns from your graveyard with haste, and is exiled at the next end step or if it would leave."),
        Triple("level-up", "Level up", "Pay its level up cost (only when you could cast a sorcery) to give it a level counter; enough levels unlock the abilities its text lists."),
        Triple("miracle", "Miracle", "If it's the first card you draw in a turn, you may reveal it and cast it then for its miracle cost."),
        Triple("overload", "Overload", "You may cast it for its overload cost instead; then \"target\" in its text becomes \"each\"."),
        Triple("scavenge", "Scavenge", "Pay its scavenge cost and exile it from your graveyard (only when you could cast a sorcery): put +1/+1 counters equal to its power on a creature."),
        Triple("bestow", "Bestow", "You may cast it for its bestow cost as an Aura that enchants a creature; if that creature leaves, it stays as a creature of its own."),
        Triple("outlast", "Outlast", "Pay its outlast cost and tap it (only when you could cast a sorcery): it gets a +1/+1 counter."),
        Triple("dash", "Dash", "You may cast it for its dash cost instead; it gains haste and returns to your hand at the next end step."),
        Triple("surge", "Surge", "You may cast it for its surge cost if you or a teammate cast another spell this turn."),
        Triple("emerge", "Emerge", "You may cast it by sacrificing a creature and paying its emerge cost, reduced by the sacrificed creature's mana value."),
        Triple("escalate", "Escalate", "Choose any number of its modes, paying the escalate cost once for each mode beyond the first."),
        Triple("embalm", "Embalm", "Pay its embalm cost and exile it from your graveyard (only when you could cast a sorcery): you get a white Zombie token copy of it with no mana cost."),
        Triple("eternalize", "Eternalize", "Pay its eternalize cost and exile it from your graveyard (only when you could cast a sorcery): you get a 4/4 black Zombie token copy of it with no mana cost."),
        Triple("spectacle", "Spectacle", "You may cast it for its spectacle cost if an opponent lost life this turn."),
        Triple("escape", "Escape", "You can cast it from your graveyard for its escape cost, which includes exiling other cards from your graveyard."),
        Triple("mutate", "Mutate", "Cast for its mutate cost, it merges with a non-Human creature you own, on top or under it: one creature with every ability of both."),
        Triple("encore", "Encore", "Pay its encore cost and exile it from your graveyard (only when you could cast a sorcery): for each opponent you get a token copy with haste that attacks that opponent this turn and is sacrificed at the end step."),
        Triple("foretell", "Foretell", "During your turn pay {2} to exile it from your hand face down; on a later turn you may cast it for its foretell cost."),
        Triple("disturb", "Disturb", "You can cast it from your graveyard transformed, its back face up, for its disturb cost."),
        Triple("craft", "Craft", "Pay its craft cost and exile it with the listed materials from your permanents and graveyard (only when you could cast a sorcery): it returns transformed."),
        Triple("cleave", "Cleave", "You may cast it for its cleave cost instead; then the words in square brackets in its text don't count."),
        Triple("reconfigure", "Reconfigure", "Pay its reconfigure cost (only when you could cast a sorcery) to attach it to a creature of yours or unattach it; while attached it isn't a creature."),
        Triple("blitz", "Blitz", "You may cast it for its blitz cost instead; it gains haste and \"when it dies, draw a card\", and is sacrificed at the next end step."),
        Triple("squad", "Squad", "As you cast it you may pay its squad cost any number of times; when it enters you get that many token copies of it."),
        Triple("disguise", "Disguise", "You can cast it face down as a nameless 2/2 creature with ward {2} for {3}, and turn it face up any time by paying its disguise cost."),
        Triple("plot", "Plot", "Pay its plot cost and exile it from your hand (only when you could cast a sorcery); on a later turn you may cast it from exile for free, as a sorcery."),
        Triple("freerunning", "Freerunning", "You may cast it for its freerunning cost if an Assassin or a commander of yours dealt combat damage to a player this turn."),
        Triple("offspring", "Offspring", "As you cast it you may pay its offspring cost as well; when it enters you then get a 1/1 token copy of it."),
        Triple("harmonize", "Harmonize", "You can cast it from your graveyard for its harmonize cost, tapping one of your creatures to pay generic mana up to its power; then it's exiled."),
        Triple("warp", "Warp", "You may cast it from your hand for its warp cost; it's exiled at the next end step, and on a later turn you may cast it from exile."),
        Triple("mayhem", "Mayhem", "If you discarded it this turn, you can cast it (play it, for a land) from your graveyard, for its mayhem cost when it has one."),
        Triple("web-slinging", "Web-slinging", "You may cast it for its web-slinging cost by also returning one of your tapped creatures to your hand."),
        Triple("sneak", "Sneak", "During your declare blockers step you may cast it for its sneak cost by also returning one of your unblocked attackers to your hand; a creature cast this way enters tapped and attacking."),
        Triple("beam-me-up", "Beam me up", "You can cast it from your graveyard for its beam me up cost by also returning one of your creatures to your hand; then it's exiled."),
        Triple("suspend", "Suspend", "Pay its suspend cost and exile it from your hand with N time counters; at each of your upkeeps remove one, and when the last is gone you cast it for free (a creature gets haste)."),
        Triple("reinforce", "Reinforce", "Pay its reinforce cost and discard it: put N +1/+1 counters on a creature, at instant speed."),
        Triple("awaken", "Awaken", "You may cast it for its awaken cost; then one of your lands also becomes a 0/0 Elemental creature with haste and N +1/+1 counters."),
        Triple("impending", "Impending", "You may cast it for its impending cost; it enters with N time counters and isn't a creature while it has any; one comes off at each of your end steps."),
        Triple("equip", "Equip", "Pay its equip cost (only when you could cast a sorcery) to attach this Equipment to a creature you control."),
        Triple("kicker", "Kicker", "You may pay the kicker cost as well when you cast it; kicked, it does more, as its text says."),
        Triple("enchant", "Enchant", "This Aura is attached to the kind of object it names, and only that kind; it targets one as you cast it."),
        Triple("protection", "Protection", "Protection from a quality: it can't be blocked, targeted, enchanted or equipped by anything with that quality, and damage from such sources is prevented."),
        Triple("landwalk", "Landwalk", "It can't be blocked while the defending player controls a land of the type it names (forestwalk, islandwalk, ...)."),
        Triple("affinity", "Affinity", "It costs {1} less to cast for each permanent of the named kind you control."),
        Triple("splice", "Splice", "As you cast a spell of the named kind, you may reveal this card from your hand and pay its splice cost to add its text to that spell."),
        Triple("partner-with", "Partner with", "When it enters, a player may search their library for the named partner card and put it into their hand; as commanders, the two can lead one deck."),
        Triple("commander-ninjutsu", "Commander ninjutsu", "Ninjutsu that also works from the command zone."),
        Triple("companion", "Companion", "If your deck meets its condition, you may keep it outside the game; once a game, pay {3} (when you could cast a sorcery) to put it into your hand."),
        Triple("prototype", "Prototype", "You may cast it for its prototype cost as a smaller version, with the prototype's power and toughness (and color)."),
        Triple("gift", "Gift", "As you cast it you may promise an opponent the gift; if you do, they get it and the spell does more, as its text says."),
        Triple("class-level", "Level", "This Class gains the abilities of each level it has reached; pay a level's cost (only when you could cast a sorcery) to reach the next one."),
    )

    // Keyword actions (CR 701): the [KeywordAction] types by serial name, then scry, surveil, mill and fight
    private val ACTIONS: List<Triple<String, String, String>> = listOf(
        Triple("investigate", "Investigate", "You get a Clue token, an artifact you can later sacrifice for {2} to draw a card."),
        Triple("proliferate", "Proliferate", "Pick any players and permanents that already have counters: each of them gets one more counter of every kind it has."),
        Triple("populate", "Populate", "You get another token that copies one of your creature tokens."),
        Triple("explore", "Explore", "Reveal your top card: a land goes to your hand; anything else gives this creature a +1/+1 counter, and you may put that card into your graveyard."),
        Triple("amass", "Amass", "Your Army grows by N +1/+1 counters; with no Army you first get a 0/0 Army creature token."),
        Triple("incubate", "Incubate", "You get an Incubator token with N +1/+1 counters on it; for {2} it transforms into a 0/0 Phyrexian artifact creature that keeps them."),
        Triple("connive", "Connive", "You draw one card and then discard one; if what you discarded wasn't a land, the creature that connived gets a +1/+1 counter."),
        Triple("discover", "Discover", "Turn over cards from the top of your library until one isn't a land and has mana value N or less: cast it for free or take it into your hand; the others go to the bottom."),
        Triple("venture", "Venture into the dungeon", "Move your marker to the next room of your dungeon (starting a new dungeon if you have none) and do what that room says."),
        Triple("venture-into", "Venture into", "Venture into the dungeon, starting a new dungeon of the kind named if you have none."),
        Triple("manifest", "Manifest", "Your library's top card enters face down as a nameless 2/2 creature; a creature card can later be turned face up by paying its mana cost."),
        Triple("manifest-dread", "Manifest dread", "Of your library's top two cards, one enters face down as a 2/2 creature (manifested) and the other goes to your graveyard."),
        Triple("monstrosity", "Monstrosity", "Only once: a creature that isn't monstrous yet gets N +1/+1 counters and is monstrous from then on."),
        Triple("adapt", "Adapt", "The creature gets N +1/+1 counters, but only when it has none yet."),
        Triple("goad", "Goad", "Until your next turn the goaded creature must attack every combat, and must go after someone other than you when it can."),
        Triple("learn", "Learn", "Either take a Lesson card of yours from outside the game into your hand, or discard a card to draw one; you may also do neither."),
        Triple("double", "Double", "Make the number twice as big: as many more counters, life, power or mana as there already is."),
        Triple("bolster", "Bolster", "Put N +1/+1 counters on the creature you control with the least toughness (you pick among ties)."),
        Triple("support", "Support", "Up to N other creatures each get a +1/+1 counter."),
        Triple("detain", "Detain", "Until your next turn the detained permanent is held back: no attacking, no blocking, and none of its activated abilities."),
        Triple("endure", "Endure", "Choose: the creature gets N +1/+1 counters, or its controller gets a white Spirit creature token that is N/N instead."),
        Triple("forage", "Forage", "Pay by giving up a Food (sacrifice it) or by exiling three cards of your graveyard."),
        Triple("foretell", "Foretell", "Exile the card from your hand face down; on a later turn you may cast it for its foretell cost."),
        Triple("cloak", "Cloak", "The card enters face down as a nameless 2/2 creature with ward {2}; a creature card can later be turned face up by paying its mana cost."),
        Triple("time-travel", "Time travel", "Each of your suspended cards and permanents with time counters may get one time counter more or one fewer, as you like."),
        Triple("ring-tempts", "The Ring tempts you", "Choose a creature as your Ring-bearer; each time the Ring tempts you, your Ring-bearer gains the next of the Ring's abilities."),
        Triple("exert", "Exert", "The creature stays tapped through your next untap step instead of untapping."),
        Triple("behold", "Behold", "Show the named kind of card from your hand, or choose a permanent of that kind you control."),
        Triple("collect-evidence", "Collect evidence", "Pay by exiling cards from your graveyard whose mana values together reach at least N."),
        Triple("earthbend", "Earthbend", "One of your lands also becomes a 0/0 creature with haste and gets N +1/+1 counters; when that land dies or is exiled, it comes back tapped."),
        Triple("regenerate", "Regenerate", "A one-time shield this turn: instead of being destroyed, it gets tapped, loses its damage and leaves combat."),
        Triple("empower-jace", "Empower Jace", "Get a blue Jace planeswalker token if you have none, then put N loyalty counters on one of your Jace tokens."),
        Triple("recruit", "Recruit", "Draw a card, then discard a card; if you discarded a nonland card, create a 1/1 white Human Soldier creature token."),
        Triple("airbend", "Airbend", "It goes into exile; from there its owner may later cast it for just {2}."),
        Triple("meld", "Meld", "Two cards of a meld pair become one permanent: their shared back face, both cards under it."),
        Triple("blight", "Blight", "One of your own creatures takes N -1/-1 counters."),
        Triple("scry", "Scry", "See your top N cards and keep any of them on top, in the order you like; the others go to the bottom of your library."),
        Triple("surveil", "Surveil", "See your top N cards; any of them may go to your graveyard, and the others go back on top in the order you like."),
        Triple("mill", "Mill", "The top N cards of your library go straight to your graveyard."),
        Triple("fight", "Fight", "Two creatures fight: each one hits the other for damage equal to its own power."),
    )

    // Token kinds (CR 111.10): (id, name as cards print it, explanation); Role tokens by kind, then Role itself
    private val TOKENS: List<Triple<String, String, String>> = listOf(
        Triple("treasure", "Treasure", "An artifact token you can tap and sacrifice to add one mana of any color."),
        Triple("food", "Food", "An artifact token you can pay {2}, tap and sacrifice to gain 3 life."),
        Triple("gold", "Gold", "An artifact token you can sacrifice, without tapping it, to add one mana of any color."),
        Triple("walker", "Walker", "A plain 2/2 black Zombie creature token named Walker."),
        Triple("shard", "Shard", "An enchantment token you can pay {2} and sacrifice to scry 1 and then draw a card."),
        Triple("clue", "Clue", "An artifact token you can pay {2} and sacrifice to draw a card."),
        Triple("blood", "Blood", "An artifact token you can pay {1}, tap, discard a card and sacrifice to draw a card, swapping a card in hand for a new one."),
        Triple("heartwood", "Heartwood", "A red and green artifact token you can tap to add {R} or {G}."),
        Triple("mutagen", "Mutagen", "An artifact token you can pay {1}, tap and sacrifice, when you could cast a sorcery, to put a +1/+1 counter on a creature."),
        Triple("lander", "Lander", "An artifact token you can pay {2}, tap and sacrifice to put a basic land card from your library onto the battlefield tapped."),
        Triple("incubator", "Incubator", "An artifact token that, for {2}, turns over into a 0/0 Phyrexian artifact creature, keeping its +1/+1 counters."),
        Triple("map", "Map", "An artifact token you can pay {1}, tap and sacrifice, when you could cast a sorcery, to have one of your creatures explore."),
        Triple("powerstone", "Powerstone", "An artifact token that taps for {C}; that mana pays for abilities and artifact spells, but not for other spells."),
        Triple("cursed-role", "Cursed Role", "An Aura token making the creature it enchants a 1/1 at its base; a newer Role from the same player replaces it."),
        Triple("monster-role", "Monster Role", "An Aura token giving the creature it enchants +1/+1 and trample; a newer Role from the same player replaces it."),
        Triple("royal-role", "Royal Role", "An Aura token giving the creature it enchants +1/+1 and ward {1}; a newer Role from the same player replaces it."),
        Triple("sorcerer-role", "Sorcerer Role", "An Aura token giving the creature it enchants +1/+1 and a scry 1 each time it attacks; a newer Role from the same player replaces it."),
        Triple("virtuous-role", "Virtuous Role", "An Aura token giving the creature it enchants +1/+1 for each enchantment you control; a newer Role from the same player replaces it."),
        Triple("wicked-role", "Wicked Role", "An Aura token giving the creature it enchants +1/+1, and each opponent loses 1 life when it goes to the graveyard from play; a newer Role from the same player replaces it."),
        Triple("young-hero-role", "Young Hero Role", "An Aura token; each time the creature it enchants attacks with toughness 3 or less, it gets a +1/+1 counter; a newer Role from the same player replaces it."),
        Triple("role", "Role", "An Aura token that enchants a creature; when a player gives a creature a second Role, their older one there goes to the graveyard."),
    )

    // Other rules words (CR 701, 702): the [AbilityLabel]s by serial name (solved is a state), then actions and words of their own
    private val TERMS: List<Triple<String, String, String>> = listOf(
        Triple("boast", "Boast", "A boast ability can be used only in a turn this creature attacked, and only once each turn."),
        Triple("exhaust", "Exhaust", "Each exhaust ability can be used only once; a card that leaves the battlefield and comes back counts as new."),
        Triple("max-speed", "Max speed", "What follows max speed works only while your speed is 4, the highest it goes."),
        Triple("power-up", "Power-up", "A power-up ability can be used only once, and costs less by the permanent's mana cost in the turn it entered."),
        Triple("infinity", "Infinity", "An ability after the infinity sign works only while its permanent is harnessed."),
        Triple("forecast", "Forecast", "Use it by showing this card from your hand, only during your upkeep and only once each turn; the card stays in your hand."),
        Triple("harness", "Harness", "Harnessing a permanent switches on its infinity-sign abilities for as long as it stays on the battlefield."),
        Triple("transform", "Transform", "Turn a double-faced permanent over to its other face; it stays the same permanent, keeping its counters, damage and attachments."),
        Triple("suspect", "Suspect", "Mark a creature as suspected: while it is, it has menace and can't block."),
        Triple("face-a-dilemma", "Face a dilemma", "Each time you pick modes for a spell or ability, one mode or more, you face a dilemma."),
        Triple("assimilate", "Assimilate", "The card comes onto the battlefield on your side with a +1/+1 counter, as a Borg artifact creature without its other creature types."),
        Triple("waterbend", "Waterbend", "Tapping your artifacts and creatures helps pay a waterbend cost: each one pays {1} of it."),
        Triple("triple", "Triple", "Make the number three times as big: a creature's power and toughness, counters, damage or the like."),
        Triple("descend", "Descend", "You descend whenever a permanent card goes to your graveyard from anywhere; a \"Descend N\" ability works with N or more permanent cards there."),
    )
}
