package org.asyncttwiz.protocol

/**
 * One card a draft bot looks at: its draft copy [id], its card data [definition] (null when unknown) and its printing's
 * [rarity] when card records know it. A card Automatum can't play ([supported] false) or without card data scores
 * [DraftBot.NEUTRAL].
 */
data class DraftBotCard(val id: String, val definition: CardDefinition?, val rarity: SetCardRarity? = null,
    val supported: Boolean = true)

/** One named part of a card's score, e.g. "Removal" +12; [DraftBotScore.total] adds them up. */
data class DraftBotPart(val label: String, val points: Int)

/** A card's score as its named [parts] (the first is always "Base" or why the card is neutral). */
data class DraftBotScore(val card: String, val parts: List<DraftBotPart>) {
    val total: Int get() = parts.sumOf { it.points }
}

/**
 * The bot's view of one offer: every card's [scores] in the offer's order, the cards it would take ([picks], best first),
 * the colours it leans to from its picks so far ([colors], at most two) and how much those count now ([commitment], 0–100 %).
 */
data class DraftBotChoice(val scores: List<DraftBotScore>, val picks: List<String>, val colors: List<DraftColor>, val commitment: Int) {
    fun score(card: String): DraftBotScore? = scores.firstOrNull { it.card == card }
}

/**
 * The draft bot's pick heuristic (backlog, owner 2026-11-20: a simple heuristic, no outside pick data): pure and
 * deterministic, for the server's bot seats and the clients' debug view alike. A card's score is a base plus named parts
 * read from what the card data says: rarity, a creature's power and toughness against its mana value, its keywords, a
 * removal or card-advantage hint from its rules text, a high cost, lands; then colour, which grows with the bot's picks:
 * nothing for its first [OPEN_PICKS] picks, then more each pick until [FULL_PICK], favouring its two main colours.
 * The rules text is read by plain words, not parsed: cheap enough for every card of an offer on a phone.
 */
object DraftBot {
    const val BASE = 40
    /** What a card the bot can't judge scores: the [BASE] alone. */
    const val NEUTRAL = BASE
    /** Picks before colour counts at all (the bot stays open). */
    const val OPEN_PICKS = 3
    /** The pick from which colour counts fully. */
    const val FULL_PICK = 13
    /** Points at full commitment for a card all in the bot's colours, and against for each colour outside them. */
    const val IN_COLOURS = 10
    const val OFF_COLOUR = 16

    /**
     * Scores [offer] and takes the best [keep] cards. [picks]: the bot's cards so far (their colours); [pickNumber]: the
     * 1-based number of this pick in the bot's draft. Ties go to the card whose name comes first, then the lower id.
     */
    fun choose(offer: List<DraftBotCard>, picks: List<DraftBotCard>, pickNumber: Int, keep: Int = 1): DraftBotChoice {
        val colors = mainColors(picks)
        val commitment = commitment(pickNumber)
        val scores = offer.map { score(it, colors, commitment) }
        val names = offer.associate { it.id to (it.definition?.name ?: "") }
        val order = compareByDescending<DraftBotScore> { it.total }.thenBy { names[it.card] }.thenBy { it.card }
        return DraftBotChoice(scores, scores.sortedWith(order).take(keep.coerceAtLeast(0)).map { it.card }, colors, commitment)
    }

    /** How much colour counts at [pickNumber], in percent: 0 for the first [OPEN_PICKS] picks, 100 from [FULL_PICK]. */
    fun commitment(pickNumber: Int): Int =
        ((pickNumber - OPEN_PICKS) * 100 / (FULL_PICK - OPEN_PICKS)).coerceIn(0, 100)

    /** The (at most two) colours most of [picks] are, in WUBRG order on a tie; a colour none of them is never counts. */
    fun mainColors(picks: List<DraftBotCard>): List<DraftColor> {
        val counts = IntArray(DraftColor.entries.size)
        picks.forEach { card -> card.definition?.takeIf { card.supported }?.let(::colors)?.forEach { counts[it.ordinal]++ } }
        return DraftColor.entries.filter { counts[it.ordinal] > 0 }.sortedByDescending { counts[it.ordinal] }.take(2)
    }

    /** [card]'s score for a bot leaning to [colors] at [commitment] %. */
    fun score(card: DraftBotCard, colors: List<DraftColor>, commitment: Int): DraftBotScore {
        val definition = card.definition
        if (definition == null || definition.faces.isEmpty()) return DraftBotScore(card.id, listOf(DraftBotPart("Unknown card", NEUTRAL)))
        if (!card.supported) return DraftBotScore(card.id, listOf(DraftBotPart("Not supported", NEUTRAL)))
        val parts = mutableListOf(DraftBotPart("Base", BASE))
        fun add(label: String, points: Int) { if (points != 0) parts += DraftBotPart(label, points) }
        rarityPoints(card.rarity)?.let { (label, points) -> add(label, points) }
        val front = definition.faces.first()
        val typeLine = front.typeLine
        val manaValue = PodDeckManaCost.front(definition).value.toInt()
        val text = rulesText(definition)
        when {
            "Basic" in typeLine && "Land" in typeLine -> add("Basic land", -BASE)
            "Land" in typeLine && "Creature" !in typeLine -> add("Land", -10)
        }
        if ("Planeswalker" in typeLine) add("Planeswalker", 10)
        creature(definition)?.let { (power, toughness) ->
            val diff = power + toughness - (2 * manaValue + 1)
            add("Stats $power/$toughness for $manaValue", (3 * diff).coerceIn(-9, 9))
        }
        keywords(definition).takeIf { it.isNotEmpty() }?.let { found ->
            add("Keywords: ${found.joinToString { it.first }}", found.sumOf { it.second }.coerceAtMost(8))
        }
        when {
            sweeper.containsMatchIn(text) -> add("Sweeper", 8)
            removal.containsMatchIn(text) -> add("Removal", 12)
            bounce.containsMatchIn(text) -> add("Bounce", 4)
        }
        if (counter.containsMatchIn(text)) add("Counterspell", 3)
        when {
            drawMany.containsMatchIn(text) -> add("Draws cards", 6)
            drawOne.containsMatchIn(text) -> add("Draws a card", 3)
        }
        if (tokens.containsMatchIn(text)) add("Makes tokens", 3)
        if (regrowth.containsMatchIn(text)) add("Returns a card", 3)
        if ("Land" !in typeLine) when {
            manaValue >= 7 -> add("Costs $manaValue", -6)
            manaValue == 6 -> add("Costs 6", -3)
        }
        colourPart(definition, colors, commitment)?.let { (label, points) -> add(label, points) }
        return DraftBotScore(card.id, parts)
    }

    private fun rarityPoints(rarity: SetCardRarity?): Pair<String, Int>? = when (rarity) {
        SetCardRarity.UNCOMMON -> "Uncommon" to 4
        SetCardRarity.RARE -> "Rare" to 10
        SetCardRarity.MYTHIC -> "Mythic" to 14
        else -> null
    }

    /**
     * The colour part: a card all in [colors] gets up to [IN_COLOURS]; each colour it needs beyond them and the slots still
     * open (two colours in all) costs up to [OFF_COLOUR], at most two; colourless cards get none. A hybrid symbol needs
     * one of its colours.
     */
    private fun colourPart(definition: CardDefinition, colors: List<DraftColor>, commitment: Int): Pair<String, Int>? {
        if (commitment == 0 || colors.isEmpty()) return null
        val needs = needs(definition)
        if (needs.isEmpty()) return null
        val unmet = needs.filter { alternatives -> alternatives.none { it in colors } }
        val off = unmet.map { it.first() }.distinct()
        val open = 2 - colors.size
        val shown = colors.joinToString("") { it.name }
        return when {
            off.isEmpty() -> "In colours $shown" to IN_COLOURS * commitment / 100
            off.size <= open -> null
            else -> "Off colour ${off.joinToString("") { it.name }}" to
                -OFF_COLOUR * (off.size - open).coerceAtMost(2) * commitment / 100
        }
    }

    /** The colours [definition] is, from its faces' mana costs and colour indicators. */
    fun colors(definition: CardDefinition): Set<DraftColor> = needs(definition).flatten().toSet()

    /** What each coloured mana symbol of [definition]'s costs (and each colour indicator) needs: one colour of each set. */
    private fun needs(definition: CardDefinition): List<Set<DraftColor>> {
        val costs = PodDeckManaCost.splitHalfCosts(definition).ifEmpty {
            definition.faces.flatMap { face -> listOf(face.manaCost) + face.parts.map { it.manaCost } }
        }
        val needs = costs.flatMap { cost ->
            symbol.findAll(cost).mapNotNull { match ->
                val alternatives = match.groupValues[1].uppercase().split('/')
                // {2/W}: payable without the colour
                if (alternatives.any { it.toIntOrNull() != null }) null
                else alternatives.mapNotNull { part -> DraftColor.entries.firstOrNull { it.name == part } }.toSet().takeIf { it.isNotEmpty() }
            }.toList()
        }
        val indicators = definition.faces.flatMap { face -> face.colorIndicator.orEmpty().mapNotNull { letter ->
            DraftColor.entries.firstOrNull { it.name == letter.toString() }?.let { setOf(it) } } }
        return needs + indicators
    }

    /** The first creature's power and toughness among [definition]'s front face and its parts; null for a non-creature or
     * one whose power or toughness isn't a plain number. */
    private fun creature(definition: CardDefinition): Pair<Int, Int>? {
        val front = definition.faces.first()
        val candidates = listOf(Triple(front.typeLine, front.power, front.toughness)) +
            front.parts.map { Triple(it.typeLine, it.power, it.toughness) }
        val (_, power, toughness) = candidates.firstOrNull { "Creature" in it.first } ?: return null
        return (power.trim().toIntOrNull() ?: return null) to (toughness.trim().toIntOrNull() ?: return null)
    }

    private val keywordPoints = listOf("flying" to 4, "double strike" to 4, "deathtouch" to 3, "first strike" to 2, "lifelink" to 2,
        "menace" to 2, "hexproof" to 2, "trample" to 1, "vigilance" to 1, "haste" to 1, "reach" to 1, "ward" to 1, "flash" to 1)

    /** The keywords of [definition]'s front face's keyword lines (a line of keywords only, separated by commas). */
    private fun keywords(definition: CardDefinition): List<Pair<String, Int>> {
        val front = definition.faces.first()
        val lines = (listOf(front.rulesText) + front.parts.map { it.rulesText }).flatMap { it.lines() }
        val found = linkedMapOf<String, Int>()
        for (line in lines) {
            val words = reminder.replace(line, "").split(',', ';').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (words.isEmpty()) continue
            val matched = words.map { word -> keywordPoints.firstOrNull { (keyword, _) -> word == keyword || word.startsWith("$keyword ") } }
            if (matched.any { it == null }) continue
            matched.filterNotNull().forEach { (keyword, points) -> found.getOrPut(keyword) { points } }
        }
        return found.map { (keyword, points) -> keyword to points }
    }

    /** Every face's and part's rules text in lower case, without reminder text. */
    private fun rulesText(definition: CardDefinition): String = definition.faces
        .flatMap { face -> listOf(face.rulesText) + face.parts.map { it.rulesText } }
        .joinToString("\n") { reminder.replace(it, "") }.lowercase()

    private val symbol = Regex("\\{([^{}]+)\\}")
    private val reminder = Regex("\\([^()]*\\)")
    private val removal = Regex("(destroy|exile) (up to one )?(another )?target (creature|permanent|nonland|artifact or creature|" +
        "creature or planeswalker)|deals (\\d+|x) damage to (any target|target creature|target attacking|target blocking|" +
        "target creature or planeswalker)|target creature (an opponent controls )?gets -\\d+/-\\d+|target creature gets -x/-x|" +
        "\\bfights? (target|up to one target|another target)")
    private val sweeper = Regex("destroy all creatures|exile all creatures|deals (\\d+|x) damage to each creature|all creatures get -")
    private val bounce = Regex("return (up to one )?target (creature|nonland permanent) to its owner's hand")
    private val counter = Regex("counter target (spell|creature spell|noncreature spell)")
    private val drawMany = Regex("draw (two|three|four|x) cards")
    private val drawOne = Regex("draw a card")
    private val tokens = Regex("create (a|an|one|two|three|x|\\d+) .*tokens?")
    private val regrowth = Regex("return target [a-z ]*card from your graveyard to your hand")
}
