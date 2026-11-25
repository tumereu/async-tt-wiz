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
 * A set's card ratings for the draft bot (DRF-312, owner 2026-11-25): [table] holds each rated card's GIH WR in Premier
 * Draft (17Lands' public data, the card catalog's set ratings) in tenths of a percent, by its card's name. A card's rating
 * becomes its strength ([points]): its distance from the set's mean in the set's standard deviations, times
 * [DraftBot.RATED_SPREAD], from [DraftBot.HEURISTIC_MEAN]. A card without a rating keeps the heuristic, rescaled the same
 * way from the heuristic's own mean and spread over the rated sets' cards ([rescaled]), so the two compare fairly. A card is
 * strong enough to splash ([DraftBot.SPLASH]) from the set's top tenth ([strong]).
 */
class DraftBotRatings(val set: String, val table: Map<String, Int>) {
    init { require(table.isNotEmpty()) { "No ratings for $set" } }
    private val mean = table.values.average()
    private val deviation = kotlin.math.sqrt(table.values.sumOf { (it - mean) * (it - mean) } / table.size).takeIf { it > 0.0 } ?: 1.0
    /** The lowest rating of the set's top tenth (its 90th percentile). */
    val strong: Int = table.values.sorted().let { sorted -> sorted[((sorted.size - 1) * 9 + 9) / 10] }

    /** [definition]'s rating, by its card's name, or null for a card the set's ratings don't have. */
    fun rating(definition: CardDefinition): Int? = table[definition.name]

    /** [rating] on the bot's scale. */
    fun points(rating: Int): Int = kotlin.math.round(DraftBot.HEURISTIC_MEAN + (rating - mean) / deviation * DraftBot.RATED_SPREAD).toInt()

    /** A heuristic score (without colour) of a card without a rating, on the same scale as [points]. */
    fun rescaled(heuristic: Int): Int =
        kotlin.math.round(DraftBot.HEURISTIC_MEAN + (heuristic - DraftBot.HEURISTIC_MEAN) / DraftBot.HEURISTIC_SPREAD * DraftBot.RATED_SPREAD).toInt()

    override fun toString() = "$set ratings of ${table.size} cards"
}

/**
 * The bot's view of one offer: every card's [scores] in the offer's order, the cards it would take ([picks], best first),
 * the colours it leans to from its picks so far ([colors], at most two), how much those count now ([commitment], 0–100 %)
 * and the colour it splashes ([splash], DRF-313; null for none).
 */
data class DraftBotChoice(val scores: List<DraftBotScore>, val picks: List<String>, val colors: List<DraftColor>, val commitment: Int,
    val splash: DraftColor? = null) {
    fun score(card: String): DraftBotScore? = scores.firstOrNull { it.card == card }
}

/**
 * The draft bot (DRF-292, DRF-312): pure and deterministic, for the server's bot seats and the clients' debug view alike.
 * A card's strength is its card rating where the pod has ratings ([DraftBotRatings]: a pod of one set that has them, owner
 * 2026-11-25) and the card has one; otherwise the heuristic: a base plus named parts read from what the card data says:
 * rarity, a creature's power and toughness against its mana value, its keywords, a removal or card-advantage hint from its
 * rules text, a high cost, lands. Then colour, which grows with the bot's picks: nothing for its first [OPEN_PICKS] picks,
 * then more each pick until [FULL_PICK], favouring its two main colours; a strong card one pip off its colours costs only
 * [SPLASH] (DRF-313). The colour part adds to the strength, so a much stronger card can beat colour. The rules text is read
 * by plain words, not parsed: cheap enough for every card of an offer on a phone.
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
    /** DRF-313: what a strong card one coloured pip off the bot's colours costs at full commitment, in place of [OFF_COLOUR]. */
    const val SPLASH = 4
    /** The heuristic's mean and standard deviation over the rated cards of the sets with ratings, without colour
     * (measured on the catalog of 2026-11-25: 7248 cards of 27 sets, mean 43.2, deviation 9.9): the scale ratings are put
     * on ([DraftBotRatings.points]). */
    const val HEURISTIC_MEAN = 43.2
    const val HEURISTIC_SPREAD = 9.9
    /** DRF-312, DRF-313: the points one standard deviation of a set's ratings is worth: a card about two deviations above its
     * set's mean (its top 3 %, a bomb) outweighs the colour swing between an ordinary card of the bot's colours and one
     * colour off ([IN_COLOURS] + [OFF_COLOUR]). */
    const val RATED_SPREAD = 14.0
    /** DRF-313: the heuristic strength from which a card without a rating is strong enough to splash: the top tenth of
     * the heuristic over those cards (its 90th percentile). */
    const val HEURISTIC_STRONG = 54

    /**
     * Scores [offer] and takes the best [keep] cards. [picks]: the bot's cards so far (their colours); [pickNumber]: the
     * 1-based number of this pick in the bot's draft; [ratings]: the pod's card ratings, null for none (DRF-312). Ties go to
     * the card whose name comes first, then the lower id.
     */
    fun choose(offer: List<DraftBotCard>, picks: List<DraftBotCard>, pickNumber: Int, keep: Int = 1,
        ratings: DraftBotRatings? = null): DraftBotChoice {
        val colors = mainColors(picks)
        val commitment = commitment(pickNumber)
        val splash = splashColor(picks, colors, ratings)
        val scores = offer.map { score(it, colors, commitment, ratings, splash) }
        val names = offer.associate { it.id to (it.definition?.name ?: "") }
        val order = compareByDescending<DraftBotScore> { it.total }.thenBy { names[it.card] }.thenBy { it.card }
        return DraftBotChoice(scores, scores.sortedWith(order).take(keep.coerceAtLeast(0)).map { it.card }, colors, commitment, splash)
    }

    /**
     * What several cards taken together are worth (owner 2026-11-21: a Grid line, a Winston or Winchester pile, and any
     * pick of more than one card), less than the sum of their scores: the scores sorted best first, each counted half as
     * much as the one before it (s1 + s2/2 + s3/4 + …), a score below 0 counting as 0 (a card one doesn't want costs
     * nothing). So one 99 beats two 50s (99 against 75), and two 80s beat one 99 (120 against 99).
     */
    fun value(scores: List<Int>): Double {
        var weight = 1.0
        var total = 0.0
        for (score in scores.sortedDescending()) { total += score.coerceAtLeast(0) * weight; weight /= 2 }
        return total
    }

    /**
     * The [count] cards of [rest] (an offer without the bot's keeps) a bot burns: the strongest by their score without
     * colour (commitment 0), what any player the pack goes to next would value most, so they can't take them. Ties go
     * as in [choose]: the name first, then the lower id.
     */
    fun burns(rest: List<DraftBotCard>, count: Int, ratings: DraftBotRatings? = null): List<String> {
        val names = rest.associate { it.id to (it.definition?.name ?: "") }
        return rest.map { score(it, emptyList(), 0, ratings) }
            .sortedWith(compareByDescending<DraftBotScore> { it.total }.thenBy { names[it.card] }.thenBy { it.card })
            .take(count.coerceAtLeast(0)).map { it.card }
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

    /**
     * DRF-313: the one colour the bot splashes, or null while it has none (or fewer than two main colours): among its
     * [picks] strong enough to splash ([strong]) with exactly one coloured pip off its [colors], the colour most of them
     * need (WUBRG order on a tie).
     */
    fun splashColor(picks: List<DraftBotCard>, colors: List<DraftColor>, ratings: DraftBotRatings? = null): DraftColor? {
        if (colors.size < 2) return null
        val counts = IntArray(DraftColor.entries.size)
        picks.forEach { card ->
            val definition = card.definition?.takeIf { card.supported && it.faces.isNotEmpty() } ?: return@forEach
            val off = needs(definition).filter { alternatives -> alternatives.none { it in colors } }
            if (off.size == 1 && strong(card, ratings)) counts[off.single().first().ordinal]++
        }
        return DraftColor.entries.filter { counts[it.ordinal] > 0 }.maxByOrNull { counts[it.ordinal] }
    }

    /** DRF-313: whether [card] is strong enough to splash: a rated card in its set's top tenth by rating, any other by the
     * heuristic ([HEURISTIC_STRONG]). */
    fun strong(card: DraftBotCard, ratings: DraftBotRatings?): Boolean {
        val definition = card.definition?.takeIf { card.supported && it.faces.isNotEmpty() } ?: return false
        ratings?.rating(definition)?.let { return it >= ratings.strong }
        return heuristic(card, definition).sumOf { it.points } >= HEURISTIC_STRONG
    }

    /** [card]'s score for a bot leaning to [colors] at [commitment] %, with the pod's [ratings] (null: none, DRF-312) and
     * the bot's [splash] colour (DRF-313). */
    fun score(card: DraftBotCard, colors: List<DraftColor>, commitment: Int, ratings: DraftBotRatings? = null,
        splash: DraftColor? = null): DraftBotScore {
        val definition = card.definition
        if (definition == null || definition.faces.isEmpty()) return DraftBotScore(card.id, listOf(DraftBotPart("Unknown card", NEUTRAL)))
        if (!card.supported) return DraftBotScore(card.id, listOf(DraftBotPart("Not supported", NEUTRAL)))
        val rating = ratings?.rating(definition)
        val parts = if (rating != null) mutableListOf(DraftBotPart("Rating (GIH WR ${rating / 10}.${rating % 10}%)", ratings.points(rating)))
            else heuristic(card, definition).toMutableList().also { list ->
                if (ratings == null) return@also
                list[0] = DraftBotPart("No rating: base", BASE)
                val total = list.sumOf { it.points }
                ratings.rescaled(total).minus(total).takeIf { it != 0 }?.let { list += DraftBotPart("Rescaled to the ratings", it) }
            }
        colourPart(definition, colors, commitment, splash, strong(card, ratings))?.let { (label, points) -> if (points != 0) parts += DraftBotPart(label, points) }
        return DraftBotScore(card.id, parts)
    }

    /** The heuristic's parts of [card] (its [definition] known), without colour: "Base" first. */
    private fun heuristic(card: DraftBotCard, definition: CardDefinition): List<DraftBotPart> {
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
        return parts
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
     * one of its colours. DRF-313: a [strong] card with exactly one coloured pip off the bot's two colours costs only
     * [SPLASH], unless the bot already splashes another colour ([splash]).
     */
    private fun colourPart(definition: CardDefinition, colors: List<DraftColor>, commitment: Int, splash: DraftColor?,
        strong: Boolean): Pair<String, Int>? {
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
            unmet.size == 1 && strong && (splash == null || splash == off.single()) -> "Splash ${off.single().name}" to -SPLASH * commitment / 100
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
