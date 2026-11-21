package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/** Arena: each player drafts alone from offers of their own; nothing passes between players. */
class ArenaEngineTest {
    private val seed = RandomV1.encodeSeed(ByteArray(32) { (it * 11).toByte() })
    private val otherSeed = RandomV1.encodeSeed(ByteArray(32) { (it * 5 + 1).toByte() })

    /** A cube of [commons] commons, [uncommons] uncommons, [rares] rares and [mythics] mythic rares, one copy each. */
    private fun cube(commons: Int = 40, uncommons: Int = 20, rares: Int = 10, mythics: Int = 5): List<ArenaCard> {
        val rarities = List(commons) { SetCardRarity.COMMON } + List(uncommons) { SetCardRarity.UNCOMMON } +
            List(rares) { SetCardRarity.RARE } + List(mythics) { SetCardRarity.MYTHIC }
        val entries = rarities.indices.map { DraftCubeEntry("card-${it.toString().padStart(3, '0')}", 1) }
        return ArenaEngine.cubeCards(entries) { definition -> rarities[definition.removePrefix("card-").toInt()] }
    }
    private val rarityOf = { cards: List<ArenaCard> -> cards.flatMap { card -> card.printings.map { it to card.rarity } }.toMap() }

    /** Without initial rares (DRF-281), so "Rare every" and "Uncommon every" alone decide each pick's rarity here. */
    private fun settings(picks: Int = 12, cards: Int = 5, take: Int = 1) =
        DraftSettings.arena().copy(packsPerPlayer = picks, cardsPerPack = cards, keep = take, initialRares = 0)
    private fun start(cards: List<ArenaCard>, settings: DraftSettings = settings(), players: Int = 2, seed: String = this.seed,
        cube: Boolean = true, pickSets: List<String> = emptyList()) =
        ArenaEngine.start(List(players) { "p$it" }, cards, settings, seed, cube, pickSets)

    private fun offer(state: DraftState, player: String): List<DraftCopy> {
        val head = DraftEngine.privateView(state, player).head ?: return emptyList()
        return head.cards
    }
    private fun pickFirst(state: DraftState, player: String): DraftState {
        val head = DraftEngine.privateView(state, player).head!!
        return assertNotNull(DraftEngine.pick(state, DraftPick(player, head.id, head.cards.take(state.settings.keep).map { it.id })).state)
    }

    // DRF-281: the defaults and limits
    @Test fun defaultsAndLimits() {
        val base = DraftSettings.defaults(DraftMode.ARENA)
        assertEquals(listOf(45, 5, 1, 1, 10, 4, 8), listOf(base.packsPerPlayer, base.cardsPerPack, base.keep, base.initialRares, base.rareEvery,
            base.uncommonEvery, base.mythicOneIn))
        assertFalse(base.ignoreRarity); assertFalse(base.reuseCards)
        assertTrue(base.validate(2).isEmpty()); assertTrue(base.validate(8).isEmpty())
        assertTrue(base.copy(rareEvery = null, uncommonEvery = null, mythicOneIn = null).validate(2).isEmpty())
        assertContains(base.copy(keep = 5).validate(2), DraftSettingsIssue.KEEP)
        assertContains(base.copy(cardsPerPack = 1, keep = 1).validate(2), DraftSettingsIssue.CARDS_PER_PACK)
        assertContains(base.copy(cardsPerPack = 16).validate(2), DraftSettingsIssue.CARDS_PER_PACK)
        assertContains(base.copy(packsPerPlayer = 121).validate(2), DraftSettingsIssue.PACKS_PER_PLAYER)
        // Owner 2026-11-20: up to 120 picks, and rarity intervals up to 120
        assertTrue(base.copy(packsPerPlayer = 120, rareEvery = 120, uncommonEvery = 120).validate(2).isEmpty())
        assertContains(base.copy(uncommonEvery = 121).validate(2), DraftSettingsIssue.RARITY_SCHEDULE)
        assertContains(base.copy(rareEvery = 0).validate(2), DraftSettingsIssue.RARITY_SCHEDULE)
        assertContains(base.copy(mythicOneIn = 101).validate(2), DraftSettingsIssue.MYTHIC_CHANCE)
        // Owner 2026-11-21: initial rares 0–120
        assertTrue(base.copy(initialRares = 0).validate(2).isEmpty()); assertTrue(base.copy(initialRares = 120).validate(2).isEmpty())
        assertContains(base.copy(initialRares = 121).validate(2), DraftSettingsIssue.RARITY_SCHEDULE)
        assertContains(base.copy(initialRares = -1).validate(2), DraftSettingsIssue.RARITY_SCHEDULE)
        assertContains(base.copy(librarians = 1).validate(2), DraftSettingsIssue.LIBRARIANS)
        assertEquals(2..8, DraftMode.ARENA.players)
        assertTrue(DraftMode.ARENA.draftState); assertFalse(DraftMode.ARENA.packPassing); assertFalse(DraftMode.ARENA.limited)
        // DRF-284: a cube needs one player's picks before the last offer plus that offer, or one offer with Reuse cards
        assertEquals(44L * 1 + 5, ArenaEngine.needed(base))
        assertEquals(5L, ArenaEngine.needed(base.copy(reuseCards = true)))
        assertEquals(49L, base.supply(8, listOf(DraftCubeEntry("x", 49))).needed)
    }

    // DRF-281: the first pick (initial rares, default 1) and every 10th pick rare, uncommon every 4th, rare winning, common
    // otherwise; each offer is of its pick's rarity
    @Test fun theRarityScheduleDecidesEachOffer() {
        val base = DraftSettings.arena()
        assertEquals(SetCardRarity.RARE, ArenaEngine.pickRarity(base, 1))
        assertEquals(SetCardRarity.COMMON, ArenaEngine.pickRarity(base, 2))
        assertEquals(SetCardRarity.UNCOMMON, ArenaEngine.pickRarity(base, 4))
        assertEquals(SetCardRarity.RARE, ArenaEngine.pickRarity(base, 10))
        assertEquals(SetCardRarity.RARE, ArenaEngine.pickRarity(base, 20)) // both apply: rare wins
        assertEquals(mapOf(SetCardRarity.RARE to 5, SetCardRarity.UNCOMMON to 9, SetCardRarity.COMMON to 31), ArenaEngine.schedule(base))
        assertEquals(SetCardRarity.COMMON, ArenaEngine.pickRarity(base.copy(rareEvery = null, uncommonEvery = null), 20))
        assertEquals(SetCardRarity.RARE, ArenaEngine.pickRarity(base.copy(rareEvery = null, uncommonEvery = null), 1))
        val cards = cube()
        val rarity = rarityOf(cards)
        var state = start(cards, settings(picks = 10).copy(mythicOneIn = null))
        for (pick in 1..10) {
            val shown = offer(state, "p0")
            assertEquals(5, shown.size)
            assertEquals(setOf(ArenaEngine.pickRarity(state.settings, pick)), shown.map { rarity.getValue(it.definition) }.toSet(), "pick $pick")
            assertEquals(pick, DraftEngine.privateView(state, "p0").round)
            state = pickFirst(state, "p0")
        }
        assertTrue(state.queues.getValue("p0").isEmpty())
    }

    // DRF-281 (owner 2026-11-21): "Initial rares": the first N picks offer rares, then "Rare every" decides as before (counting
    // from the first pick), an initial rare winning over an uncommon pick; 0 gives none; their offers are of rares
    @Test fun initialRaresComeFirst() {
        val three = DraftSettings.arena().copy(initialRares = 3, uncommonEvery = 2)
        assertEquals(List(3) { SetCardRarity.RARE } + listOf(SetCardRarity.UNCOMMON, SetCardRarity.COMMON),
            (1..5).map { ArenaEngine.pickRarity(three, it) })
        assertEquals(SetCardRarity.RARE, ArenaEngine.pickRarity(three, 10))
        assertEquals(SetCardRarity.COMMON, ArenaEngine.pickRarity(three.copy(initialRares = 0, uncommonEvery = null), 1))
        // More initial rares than picks: every pick is rare
        assertEquals(mapOf(SetCardRarity.RARE to 4), ArenaEngine.schedule(three.copy(packsPerPlayer = 4, initialRares = 120)))
        val cards = cube()
        val rarity = rarityOf(cards)
        var state = start(cards, settings(picks = 3).copy(initialRares = 2, rareEvery = null, uncommonEvery = null, mythicOneIn = null))
        for (expected in listOf(SetCardRarity.RARE, SetCardRarity.RARE, SetCardRarity.COMMON)) {
            assertEquals(setOf(expected), offer(state, "p0").map { rarity.getValue(it.definition) }.toSet())
            state = pickFirst(state, "p0")
        }
    }

    // DRF-282: each card of a rare pick's offer is a mythic rare with chance 1 in N, drawn from the seed: the same seed
    // gives the same offers
    @Test fun mythicUpgradesFollowTheSeed() {
        val cards = cube(rares = 30, mythics = 30)
        val rarity = rarityOf(cards)
        val all = start(cards, settings(picks = 1).copy(rareEvery = 1, mythicOneIn = 1))
        assertTrue(offer(all, "p0").all { rarity.getValue(it.definition) == SetCardRarity.MYTHIC })
        val none = start(cards, settings(picks = 1).copy(rareEvery = 1, mythicOneIn = null))
        assertTrue(offer(none, "p0").all { rarity.getValue(it.definition) == SetCardRarity.RARE })
        // Over many players' rare offers about one card in eight is a mythic rare
        val many = start(cards, settings(picks = 1, cards = 10).copy(rareEvery = 1, mythicOneIn = 8, reuseCards = true), players = 8)
        val drawn = many.seating.flatMap { offer(many, it) }.map { rarity.getValue(it.definition) }
        val mythics = drawn.count { it == SetCardRarity.MYTHIC }
        assertTrue(mythics in 3..22, "$mythics mythic rares of ${drawn.size}")
        // A fixed seed: the same offers again, another seed others
        val again = start(cards, many.settings, players = 8)
        assertEquals(many.seating.map { offer(many, it) }, again.seating.map { offer(again, it) })
        val other = start(cards, many.settings, players = 8, seed = otherSeed)
        assertNotEquals(many.seating.map { p -> offer(many, p).map { it.definition } }, other.seating.map { p -> offer(other, p).map { it.definition } })
        // Not a rare pick: no upgrade, whatever the chance
        val common = start(cards, settings(picks = 1).copy(rareEvery = null, uncommonEvery = null, mythicOneIn = 1))
        assertTrue(offer(common, "p0").all { rarity.getValue(it.definition) == SetCardRarity.COMMON })
    }

    // DRF-286: a set's offers never hold two copies of one card (its printings are one card); a later offer may bring it again;
    // plain basic lands and other sets' cards are left out
    @Test fun setOffersNeverRepeatACardWithinAnOffer() {
        val printings = (0 until 8).flatMap { card ->
            List(1 + card % 3) { variant -> SetSourceCard("def-$card-$variant", "uuid-$card-$variant", SetCardRarity.COMMON, card = "Card $card") }
        } + SetSourceCard("def-plains", "uuid-plains", SetCardRarity.COMMON, card = "Plains") +
            SetSourceCard("def-guest", "uuid-guest", SetCardRarity.COMMON, card = "Guest")
        val set = SetSourceSnapshot("TST", "Test set", printings)
        val cards = ArenaEngine.setCards(listOf(set), own = { _, uuid -> uuid != "uuid-guest" })
        assertEquals(8, cards.size)
        assertEquals(listOf("def-2-0", "def-2-1", "def-2-2"), cards.first { it.id == "TST:Card 2" }.printings)
        val settings = settings(picks = 30, cards = 6).copy(rareEvery = null, uncommonEvery = null)
        var state = start(cards, settings, cube = false, pickSets = List(30) { "TST" })
        val seen = mutableSetOf<String>()
        repeat(30) {
            val shown = offer(state, "p0")
            val names = shown.map { copy -> cards.first { copy.definition in it.printings }.id }
            assertEquals(6, names.distinct().size)
            seen += names
            state = pickFirst(state, "p0")
        }
        assertEquals(8, seen.size) // every card comes again in later offers
        assertTrue(state.copies.none { it.definition in setOf("def-plains", "def-guest") })
        // Unknown counts as common; special and bonus as their tier, rare without one (DRF-291)
        assertEquals(SetCardRarity.RARE, ArenaEngine.rarity(SetCardRarity.SPECIAL))
        assertEquals(SetCardRarity.UNCOMMON, ArenaEngine.rarity(SetCardRarity.BONUS, SetCardRarity.UNCOMMON))
        assertEquals(SetCardRarity.COMMON, ArenaEngine.rarity(SetCardRarity.UNKNOWN))
    }

    // DRF-305 (owner, 2026-11-21): a card's printing in an offer goes by how often the set's booster recipe puts each printing
    // in a pack: Card 0's normal printing fills 5 slots of a 10-card sheet (0.5 a pack), its retro one 1 slot of a 5-card sheet
    // (0.2), its poster none (never offered); without a recipe each printing alike
    @Test fun aSetCardsPrintingGoesByItsBoosterFrequency() {
        val printings = List(10) { SetSourceCard("def-$it-a", "u$it-a", SetCardRarity.COMMON, card = "Card $it") } +
            List(5) { SetSourceCard("def-$it-b", "u$it-b", SetCardRarity.COMMON, card = "Card $it") } +
            SetSourceCard("def-0-c", "u0-c", SetCardRarity.COMMON, card = "Card 0")
        val recipe = SetBoosterRecipe(listOf(SetBoosterComposition(mapOf("common" to 5, "retro" to 1), 3)),
            mapOf("common" to SetBoosterSheet(List(10) { "u$it-a" to 1L }.toMap()), "retro" to SetBoosterSheet(List(5) { "u$it-b" to 1L }.toMap())))
        val weighted = ArenaEngine.setCards(listOf(SetSourceSnapshot("TST", "Test", printings, recipe)))
        val card0 = weighted.single { it.id == "TST:Card 0" }
        assertEquals(listOf("def-0-a", "def-0-b", "def-0-c"), card0.printings)
        assertEquals(listOf(500_000_000L, 200_000_000L, 0L), card0.weights)
        assertEquals(emptyList(), weighted.single { it.id == "TST:Card 7" }.weights, "one printing: nothing to choose")
        fun drawn(cards: List<ArenaCard>): Map<String, Int> {
            val settings = settings(picks = ArenaEngine.MAX_PICKS, cards = 10).copy(rareEvery = null, uncommonEvery = null)
            var state = start(cards, settings, cube = false, pickSets = List(ArenaEngine.MAX_PICKS) { "TST" })
            repeat(ArenaEngine.MAX_PICKS) { for (player in listOf("p0", "p1")) state = pickFirst(state, player) }
            return state.copies.map { it.definition }.filter { it.startsWith("def-0-") }.groupingBy { it }.eachCount()
        }
        val byFrequency = drawn(weighted)
        assertEquals(2 * ArenaEngine.MAX_PICKS, byFrequency.values.sum())
        assertNull(byFrequency["def-0-c"], "the poster printing is in no pack")
        // 0.2 of 0.7: about 69 of 240 retro
        assertTrue(byFrequency.getValue("def-0-b") in 45..95, "$byFrequency")
        val alike = drawn(ArenaEngine.setCards(listOf(SetSourceSnapshot("TST", "Test", printings))))
        assertEquals(setOf("def-0-a", "def-0-b", "def-0-c"), alike.keys)
        assertTrue(alike.values.all { it in 55..105 }, "about 80 each: $alike")
    }

    // DRF-286 (owner, 2026-11-20): snow-covered basics and Wastes are ordinary cards of a set whose boosters hold them (a sheet of
    // its recipe names the printing; without a recipe its approximate packs draw from every card); the five plain basics never are
    @Test fun snowCoveredBasicsAndWastesAreOfferedWhenTheSetsBoostersHoldThem() {
        val printings = List(4) { SetSourceCard("def-$it", "uuid-$it", SetCardRarity.COMMON, card = "Card $it") } +
            SetSourceCard("def-forest", "uuid-forest", SetCardRarity.COMMON, card = "Forest") +
            SetSourceCard("def-snow-forest", "uuid-snow-forest", SetCardRarity.COMMON, card = "Snow-Covered Forest") +
            SetSourceCard("def-wastes", "uuid-wastes", SetCardRarity.COMMON, card = "Wastes")
        fun recipe(vararg land: String) = SetBoosterRecipe(listOf(SetBoosterComposition(mapOf("common" to 1, "land" to 1), 1)),
            mapOf("common" to SetBoosterSheet(List(4) { "uuid-$it" to 1L }.toMap()),
                "land" to SetBoosterSheet((land.toList() + "uuid-forest").associateWith { 1L })))
        fun names(recipe: SetBoosterRecipe?) = ArenaEngine.setCards(listOf(SetSourceSnapshot("TST", "Test", printings, recipe))).map { it.id }.toSet()
        val plain = setOf("TST:Card 0", "TST:Card 1", "TST:Card 2", "TST:Card 3")
        assertEquals(plain + "TST:Snow-Covered Forest", names(recipe("uuid-snow-forest")), "the land sheet names the snow land, not Wastes")
        assertEquals(plain + "TST:Wastes", names(recipe("uuid-wastes")))
        assertEquals(plain, names(recipe()), "boosters without them")
        assertEquals(plain + "TST:Snow-Covered Forest" + "TST:Wastes", names(null), "approximate packs draw every card of the set")
        val cards = ArenaEngine.setCards(listOf(SetSourceSnapshot("TST", "Test", printings, recipe("uuid-snow-forest", "uuid-wastes"))))
        assertEquals(ArenaCard("TST:Snow-Covered Forest", listOf("def-snow-forest"), set = "TST"), cards.single { it.id == "TST:Snow-Covered Forest" })
        assertEquals(ArenaCounts(common = 6), ArenaEngine.counts(cards))
    }

    // DRF-291 (owner, 2026-11-20): a set's special and bonus cards form their tier's bonus bag; a card drawn for the tier comes
    // from it with chance 20% × min(1, bag / normal cards): 10 normal mythics and 1 bonus mythic give it about 2%, each
    // normal mythic about 9.8%
    @Test fun bonusCardsComeFromTheirTiersBagAtTheirChance() {
        fun set(bonus: Boolean) = SetSourceSnapshot("TST", "Test", List(10) { SetSourceCard("m$it", "um$it", SetCardRarity.MYTHIC, card = "Mythic $it") } +
            List(10) { SetSourceCard("r$it", "ur$it", SetCardRarity.RARE, card = "Rare $it") } +
            listOfNotNull(SetSourceCard("bonus", "ubonus", SetCardRarity.SPECIAL, card = "Bonus").takeIf { bonus }) +
            // A card with a normal printing too is a normal card: its special printing is not offered
            SetSourceCard("r-special", "ur-special", SetCardRarity.SPECIAL, card = "Rare 0"))
        val tiers = mapOf("Bonus" to SetCardRarity.MYTHIC)
        val cards = ArenaEngine.setCards(listOf(set(bonus = true))) { tiers[it] }
        assertEquals(ArenaCard("TST:Bonus", listOf("bonus"), rarity = SetCardRarity.MYTHIC, set = "TST", bonus = true), cards.single { it.bonus })
        assertEquals(listOf("r0"), cards.single { it.id == "TST:Rare 0" }.printings)
        assertEquals(SetCardRarity.RARE, ArenaEngine.setCards(listOf(set(bonus = true))).single { it.bonus }.rarity) // no other printing: rare
        assertEquals(ArenaCounts(rare = 10, mythic = 11), ArenaEngine.counts(cards))
        val settings = settings(picks = 120, cards = 2).copy(rareEvery = 1, mythicOneIn = 1)
        fun drawn(cards: List<ArenaCard>): Map<String, Int> {
            var state = start(cards, settings, players = 8, cube = false, pickSets = List(120) { "TST" })
            val counts = mutableMapOf<String, Int>()
            while (!state.finished) {
                val player = state.seating.first { state.queues.getValue(it).isNotEmpty() }
                offer(state, player).forEach { counts[it.definition] = (counts[it.definition] ?: 0) + 1 }
                state = pickFirst(state, player)
            }
            return counts
        }
        val counts = drawn(cards)
        val total = counts.values.sum()
        assertEquals(1920, total)
        val bonus = counts["bonus"] ?: 0
        assertTrue(bonus in 15..65, "the bonus mythic came $bonus times of $total (about 2%)")
        for (n in 0 until 10) assertTrue(counts.getValue("m$n") in 120..260, "mythic $n came ${counts["m$n"]} times of $total (about 9.8%)")
        // An empty bag: never a bonus card, every card a normal mythic
        val plain = drawn(ArenaEngine.setCards(listOf(set(bonus = false))) { tiers[it] })
        assertEquals(setOf("m0", "m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8", "m9"), plain.keys)
        // A tier with only its bag: the bag gives its cards, with no fill-up
        val bagOnly = SetSourceSnapshot("TST", "Test", List(3) { SetSourceCard("b$it", "ub$it", SetCardRarity.BONUS, card = "Bonus $it") } +
            List(5) { SetSourceCard("c$it", "uc$it", SetCardRarity.COMMON, card = "Common $it") })
        val onlyBag = start(ArenaEngine.setCards(listOf(bagOnly)) { SetCardRarity.MYTHIC }, settings(picks = 1, cards = 2).copy(rareEvery = 1, mythicOneIn = 1),
            cube = false, pickSets = listOf("TST"))
        assertTrue(offer(onlyBag, "p0").all { it.definition.startsWith("b") })
        assertTrue(onlyBag.arena!!.fills.isEmpty())
    }

    // DRF-284 (owner, 2026-11-20): without Reuse cards a card a player picked is never offered to that player again; another
    // player's picks never change what a player is offered, so the order players draft in can't matter
    @Test fun reuseIsPerPlayerAndOtherPlayersPicksNeverChangeAnOffer() {
        val cards = cube(commons = 12, uncommons = 0, rares = 0, mythics = 0)
        val settings = settings(picks = 8, cards = 4, take = 1).copy(rareEvery = null, uncommonEvery = null)
        var state = start(cards, settings)
        val picked = mutableListOf<String>()
        repeat(8) {
            val shown = offer(state, "p0")
            assertTrue(shown.none { it.definition in picked }, "offered again: ${shown.map { it.definition }} after $picked")
            picked += shown.first().definition
            state = pickFirst(state, "p0")
        }
        assertEquals(8, picked.distinct().size)
        // p1 is offered the cards p0 took, and the same offers whether p0 picked first or not at all
        val untouched = start(cards, settings)
        var alone = untouched; var after = state
        repeat(8) {
            assertEquals(offer(alone, "p1"), offer(after, "p1"))
            alone = pickFirst(alone, "p1"); after = pickFirst(after, "p1")
        }
        assertTrue(after.finished)
        // Unpicked cards stay in the player's own pool: with 12 cards and 8 picks of 1 every offer is full
        assertTrue(after.packs.all { pack -> after.log.first { it.packId == pack.id }.let { it.kept.size + it.discarded.size } == 4 })
        // With Reuse cards a picked card may be offered to the player again
        var reuse = start(cards, settings.copy(reuseCards = true))
        val mine = mutableListOf<String>(); var again = false
        repeat(8) {
            val shown = offer(reuse, "p0")
            if (shown.any { it.definition in mine }) again = true
            mine += shown.first().definition
            reuse = pickFirst(reuse, "p0")
        }
        assertTrue(again)
    }

    // DRF-283: with Ignore rarity a cube's offers are drawn from the whole cube; without it, from the pick's rarity
    @Test fun ignoreRarityDrawsFromTheWholeCube() {
        val cards = cube(commons = 20, uncommons = 20, rares = 20, mythics = 20)
        val rarity = rarityOf(cards)
        val settings = settings(picks = 20, cards = 10).copy(ignoreRarity = true, reuseCards = true)
        var state = start(cards, settings)
        val seen = mutableSetOf<SetCardRarity>()
        repeat(20) { seen += offer(state, "p0").map { rarity.getValue(it.definition) }; state = pickFirst(state, "p0") }
        assertEquals(setOf(SetCardRarity.COMMON, SetCardRarity.UNCOMMON, SetCardRarity.RARE, SetCardRarity.MYTHIC), seen)
        // A set ignores it: the option is a cube's
        val set = SetSourceSnapshot("TST", "Test", List(20) { SetSourceCard("d$it", "u$it", if (it < 10) SetCardRarity.COMMON else SetCardRarity.RARE, card = "C$it") })
        val setCards = ArenaEngine.setCards(listOf(set))
        val fromSet = start(setCards, settings(picks = 1).copy(ignoreRarity = true, rareEvery = null, uncommonEvery = null), cube = false, pickSets = listOf("TST"))
        assertTrue(offer(fromSet, "p0").all { copy -> setCards.first { copy.definition in it.printings }.rarity == SetCardRarity.COMMON })
    }

    // DRF-285: a rarity with too few cards fills the offer from the nearest rarity below, then above, and the state keeps it for the log
    @Test fun aShortRarityFillsFromBelowThenAbove() {
        val cards = cube(commons = 10, uncommons = 2, rares = 1, mythics = 0)
        val rarity = rarityOf(cards)
        val rare = start(cards, settings(picks = 1).copy(rareEvery = 1, mythicOneIn = null, reuseCards = true))
        assertEquals(mapOf(SetCardRarity.RARE to 1, SetCardRarity.UNCOMMON to 2, SetCardRarity.COMMON to 2),
            offer(rare, "p0").groupingBy { rarity.getValue(it.definition) }.eachCount())
        assertEquals(listOf(ArenaFill("p0", 1, SetCardRarity.RARE, SetCardRarity.UNCOMMON, 2), ArenaFill("p0", 1, SetCardRarity.RARE, SetCardRarity.COMMON, 2)),
            rare.arena!!.fills.filter { it.player == "p0" })
        // No commons below: a common pick fills from above
        val above = start(cube(commons = 2, uncommons = 2, rares = 5, mythics = 0), settings(picks = 1).copy(rareEvery = null, uncommonEvery = null, reuseCards = true))
        assertEquals(5, offer(above, "p0").size)
        assertEquals(listOf(SetCardRarity.UNCOMMON to 2, SetCardRarity.RARE to 1), above.arena!!.fills.filter { it.player == "p0" }.map { it.used to it.count })
        assertEquals(ArenaCounts(10, 2, 1, 0), ArenaEngine.counts(cards))
    }

    // DRF-280, DRF-290: offers are refused with other counts, burns or another player's offer; your badge counts your picks left; the draft ends when every player has made
    // every pick, and the history shows each pick's offer with what was taken
    @Test fun wholeDraftsAndTheirHistory() {
        val cards = cube()
        var state = start(cards, settings(picks = 6, cards = 5, take = 2).copy(reuseCards = true), players = 3)
        val head = DraftEngine.privateView(state, "p0").head!!
        assertEquals(DraftPickRejection.WRONG_COUNTS, DraftEngine.pick(state, DraftPick("p0", head.id, head.cards.take(1).map { it.id })).reason)
        assertEquals(DraftPickRejection.WRONG_COUNTS, DraftEngine.pick(state, DraftPick("p0", head.id, head.cards.take(2).map { it.id },
            head.cards.drop(2).take(1).map { it.id })).reason)
        assertEquals(DraftPickRejection.NOT_YOUR_PACK, DraftEngine.pick(state, DraftPick("p1", head.id, head.cards.take(2).map { it.id })).reason)
        assertEquals(6, DraftEngine.consecutivePicks(state, "p0"))
        while (!state.finished) {
            val player = state.seating.first { state.queues.getValue(it).isNotEmpty() }
            val before = DraftEngine.consecutivePicks(state, player)
            state = pickFirst(state, player)
            assertEquals(before - 1, DraftEngine.consecutivePicks(state, player))
        }
        assertTrue(state.seating.all { state.pools.getValue(it).size == 12 })
        val history = DraftEngine.history(state, "p1")
        assertEquals((1..6).toList(), history.map { it.round })
        assertTrue(history.all { it.cards.size == 5 && it.kept.size == 2 && !it.returning && it.takenSince.isEmpty() })
        assertTrue(history.flatMap { it.cards }.map { it.id }.all { id -> state.packs.first { it.initialPlayer == "p1" && id.startsWith(it.id) }.initialPlayer == "p1" })
        assertEquals(state, Json.decodeFromString<DraftState>(Json.encodeToString(state)))
    }
}
