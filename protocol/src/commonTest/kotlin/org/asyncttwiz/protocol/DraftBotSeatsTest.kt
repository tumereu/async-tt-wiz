package org.asyncttwiz.protocol

import kotlin.test.*

/** Bot seats' moves in every mode they draft in (owner 2026-11-21); invented cards. */
class DraftBotSeatsTest {
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private val bot = PodBots.id(1)
    private val human = "alice"

    private fun card(name: String, cost: String, type: String, text: String = "", power: String = "", toughness: String = "") =
        CardDefinition(name, listOf(DefinitionFace("f-$name", name, typeLine = type, manaCost = cost, rulesText = text,
            power = power, toughness = toughness)))
    // Their scores for a bot with no picks yet: 63, 52, 37, 31 and 0
    private val definitions = mapOf(
        "bomb" to card("Skyrend Tyrant", "{3}{R}{R}", "Creature — Dragon", "Flying\nWhen this creature enters, it deals 4 damage to any target.", "5", "5"),
        "bolt" to card("Quick Cinder", "{R}", "Instant", "Quick Cinder deals 3 damage to any target."),
        "bear" to card("Dawn Cub", "{1}{W}", "Creature — Bear", power = "2", toughness = "2"),
        "ox" to card("Plodding Ox", "{2}{G}", "Creature — Ox", power = "2", toughness = "2"),
        "land" to card("Mountain", "", "Basic Land — Mountain"))
    private val rank = listOf("bomb", "bolt", "bear", "ox", "land")
    private val cards = { copy: DraftCopy ->
        DraftBotCard(copy.id, definitions[copy.definition], if (copy.definition == "bomb") SetCardRarity.RARE else SetCardRarity.COMMON)
    }
    private fun copy(definition: String, n: Int = 1) = DraftCopy("$definition-$n", definition)
    private fun cube(each: Int) = rank.map { DraftCubeEntry(it, each) }
    private fun definition(state: LimitedDraftState, id: String) = state.copies.first { it.id == id }.definition
    private val logs = mutableListOf<String>()
    private val log: (String) -> Unit = { logs += it }

    // DRF-297: bot seats are "Bot n"
    @Test fun botIdentitiesAndModes() {
        assertEquals(listOf("bot:1", "bot:2"), PodBots.ids(2))
        assertEquals(AccountIdentity("bot:3", "Bot 3"), PodBots.identity("bot:3"))
        assertNull(PodBots.identity("0b0e1f2a-0000-4000-8000-000000000000"))
        // DRF-296 (owner, 2026-11-23): up to the engine's limit less the creator, who may draft alone with bots; none in Sealed and Arena
        assertEquals(7, DraftMode.BOOSTER.maxBots)
        assertEquals(7, DraftMode.ROTISSERIE.maxBots)
        assertEquals(3, DraftMode.GRID.maxBots)
        assertEquals(3, DraftMode.WINSTON.maxBots)
        assertEquals(0, DraftMode.SEALED.maxBots)
        assertEquals(0, DraftMode.ARENA.maxBots)
        assertEquals(emptyList(), DraftSettings.booster().copy(bots = 7).validate(8))
        assertEquals(listOf(DraftSettingsIssue.BOTS), DraftSettings.booster().copy(bots = 8).validate(9).filter { it == DraftSettingsIssue.BOTS })
        assertTrue(DraftSettingsIssue.BOTS in DraftSettings.arena().copy(bots = 1).validate(3))
        assertTrue(DraftSettingsIssue.BOTS in DraftSettings.sealed().copy(bots = 1).validate(3))
        // Seats count the bots: two humans and two bots fill a Grid, a third human is one too many
        assertEquals(4, DraftSettings.grid().copy(bots = 2).seats(2))
        assertTrue(DraftSettingsIssue.PLAYERS in DraftSettings.grid().copy(bots = 2).let { it.validate(it.seats(3)) })
    }

    // DRF-298, DRF-299: in Booster the bot keeps its best card and burns the strongest one left; near the end it only keeps
    @Test fun boosterBotKeepsTheBestAndBurnsTheStrongestLeft() {
        val settings = DraftSettings(packsPerPlayer = 1, cardsPerPack = 4, keep = 1, burn = 1, bots = 1)
        val packs = (1..2).map { n -> listOf(copy("land", n), copy("ox", n), copy("bolt", n), copy("bomb", n)) }
        var state = DraftBotSeats.run(DraftEngine.startWithPacks(listOf(human, bot), packs, settings, seed), cards, log)
        val first = state.log.single { it.player == bot }
        assertEquals(listOf("bomb"), first.kept.map { it.substringBefore('-') })
        assertEquals(listOf("bolt"), first.burned.map { it.substringBefore('-') })
        assertTrue(logs.single().startsWith("bot:1 (seat ") && "keeps Skyrend Tyrant; burns Quick Cinder" in logs.single(), logs.single())
        // Alice picks her own pack; its last two cards reach the bot, which keeps the Ox and leaves the rest to go
        val mine = DraftEngine.privateView(state, human).head!!
        val (keep, burn) = mine.cards.sortedBy { rank.indexOf(it.definition) }.map { it.id }
        state = DraftBotSeats.run(DraftEngine.pick(state, DraftPick(human, mine.id, listOf(keep), listOf(burn))).state!!, cards, log)
        assertEquals(listOf("bomb", "ox"), state.pools.getValue(bot).map { it.substringBefore('-') })
        assertTrue(DraftBotSeats.booster(state, bot) { error("no card asked") } == null)
    }

    // DRF-298: a Custom booster's bot picks and burns as the pack's pass says
    @Test fun customBoosterBotFollowsThePasses() {
        val settings = DraftSettings.customBooster().copy(packsPerPlayer = 1, cardsPerPack = 5, passes = listOf(DraftPass(2, 1), DraftPass(1, 0)), bots = 1)
        val packs = (1..2).map { n -> listOf(copy("land", n), copy("ox", n), copy("bear", n), copy("bolt", n), copy("bomb", n)) }
        val state = DraftBotSeats.run(DraftEngine.startWithPacks(listOf(human, bot), packs, settings, seed), cards, log)
        val pick = state.log.single { it.player == bot }
        assertEquals(listOf("bomb", "bolt"), pick.kept.map { it.substringBefore('-') })
        assertEquals(listOf("bear"), pick.burned.map { it.substringBefore('-') })
    }

    // DRF-298, DRF-301: a whole Booster draft of one human and three bots runs to its end, the bots moving whenever a pack
    // reaches them; their picks stay with the draft
    @Test fun aBoosterDraftWithThreeBotsRunsToItsEnd() {
        val settings = DraftSettings(packsPerPlayer = 2, cardsPerPack = 4, bots = 3)
        val players = listOf(human) + PodBots.ids(3)
        var state = DraftBotSeats.run(DraftEngine.start(players, rank.take(4).map { DraftCubeEntry(it, 8) }, settings, seed), cards, log)
        var picks = 0
        while (!state.finished) {
            check(picks++ < 20) { "the draft didn't end" }
            // Only the human is ever waited for
            assertTrue(PodBots.ids(3).all { state.queues.getValue(it).isEmpty() })
            val head = DraftEngine.privateView(state, human).head!!
            state = DraftBotSeats.run(DraftEngine.pick(state, DraftPick(human, head.id, listOf(head.cards.first().id))).state!!, cards, log)
        }
        // Each pack's last card goes on by itself: three picks of each of the eight packs, six of them Alice's
        assertEquals(6, picks)
        assertTrue(players.all { state.pools.getValue(it).size == 8 })
        assertEquals(18, logs.size)
    }

    // DRF-298, DRF-300: in Grid the bot takes the line worth most together: the Tyrant with a Cinder before two Cinders or the Tyrant alone
    @Test fun gridBotTakesTheLineWorthMostTogether() {
        val settings = DraftSettings.grid().copy(grids = 1, bots = 1)
        val layout = listOf(copy("bomb"), copy("land", 1), copy("land", 2), copy("bolt", 1), copy("bolt", 2), copy("land", 3),
            copy("land", 4), copy("land", 5), copy("land", 6))
        var state = DraftBotSeats.run(GridEngine.startWithCards(listOf(human, bot), layout, settings, seed), cards, log)
        if (state.log.none { it.player == bot }) {
            // Alice starts: she takes the bottom row of Mountains, then the bot moves
            val grid = state.queues.getValue(human).single()
            state = DraftBotSeats.run(GridEngine.pick(state, GridPick(human, grid, GridLine(GridLineType.ROW, 2))).state!!, cards, log)
        }
        assertEquals(GridLine(GridLineType.COLUMN, 0), state.log.single { it.player == bot }.line)
        assertTrue("takes column 1" in logs.single(), logs.single())
    }

    // DRF-298: in Rochester the bot takes the best card of the pack where it is its pick
    @Test fun rochesterBotTakesTheBestCardOfItsPack() {
        val settings = DraftSettings.rochester().copy(packsPerPlayer = 1, cardsPerPack = 5, bots = 1)
        val state = DraftBotSeats.run(LimitedEngine.startFromCube(listOf(human, bot), cube(2), settings, seed), cards, log)
        val take = state.log.first { it.player == bot }
        val pack = state.packs.first { it.id == take.packId }
        val best = pack.cards.minBy { rank.indexOf(definition(state, it)) }
        assertEquals(definition(state, best), definition(state, take.cards.single()))
        // It moved wherever it could and now waits for Alice
        assertTrue(state.queues.getValue(bot).isEmpty())
        assertTrue(state.queues.getValue(human).isNotEmpty())
    }

    // DRF-298: in Rotisserie the bot takes the best free card of the pool on its turn
    @Test fun rotisserieBotTakesTheBestFreeCard() {
        val settings = DraftSettings.rotisserie().copy(cardsPerPlayer = 5, poolPerPlayer = 5, bots = 1)
        var state = DraftBotSeats.run(LimitedEngine.startFromCube(listOf(human, bot), cube(2), settings, seed), cards, log)
        if (state.log.isEmpty()) {
            val land = state.board.first { definition(state, it) == "land" }
            state = DraftBotSeats.run(LimitedEngine.act(state, LimitedMove(human, LimitedAction.TAKE_CARD, 0, card = land)).state!!, cards, log)
        }
        assertEquals("bomb", definition(state, state.log.first { it.player == bot }.cards.single()))
        // Snake order: after the first pick the bot may pick twice in a row; the second goes to the other Tyrant or a Cinder
        assertTrue(state.queues.getValue(bot).isEmpty())
    }

    /** A Winston table of one human and the bot, the bot to move at [pile] of [piles] with [stack] face down. */
    private fun winston(piles: List<List<String>>, stack: List<String>, pile: Int = 0, mode: DraftMode = DraftMode.WINSTON): LimitedDraftState {
        val settings = (if (mode == DraftMode.WINSTON) DraftSettings.winston() else DraftSettings.winchester())
            .copy(packsPerPlayer = 1, cardsPerPack = 5, piles = piles.size, bots = 1)
        val base = LimitedEngine.startFromCube(listOf(human, bot), cube(2), settings, seed)
        fun ids(names: List<String>) = names.map { name -> name.substringBefore('-').let { d ->
            base.copies.filter { it.definition == d }[name.substringAfter('-', "1").toInt() - 1].id } }
        val dealt = piles.map(::ids)
        return base.copy(seating = listOf(bot, human), piles = dealt, stack = ids(stack), turn = 0, pile = pile, tables = emptyList(),
            queues = mapOf(bot to listOf("turn"), human to emptyList()), seen = mapOf(bot to dealt.flatten()), log = emptyList())
    }

    private fun move(state: LimitedDraftState): LimitedMove {
        val move = DraftBotSeats.limited(state, bot) { id -> cards(state.copies.first { it.id == id }) }!!.move
        assertNull(LimitedEngine.act(state, move).reason, "the engine takes $move")
        return move.also { logs += it.toString() }
    }

    // DRF-298, DRF-300: Winston: a pile before the last is taken when worth about one good card, the last when worth a blind card,
    // and one that can't be passed always; piles of several cards count sublinearly
    @Test fun winstonBotTakesOrPassesByThePilesWorth() {
        assertEquals(LimitedAction.PASS_PILE, move(winston(listOf(listOf("ox"), listOf("bolt"), listOf("bear")), listOf("land"))).action)
        assertEquals(LimitedAction.TAKE_PILE, move(winston(listOf(listOf("bomb"), listOf("ox"), listOf("bear")), listOf("land"))).action)
        // Bear and Ox together (37 + 31/2) are under one good card; Cinder and Ox (52 + 31/2) aren't
        assertEquals(LimitedAction.PASS_PILE, move(winston(listOf(listOf("bear", "ox"), listOf("bolt")), listOf("land"))).action)
        assertEquals(LimitedAction.TAKE_PILE, move(winston(listOf(listOf("bolt", "ox"), listOf("bear")), listOf("land"))).action)
        // The last pile with cards: a Cinder beats a blind card, an Ox doesn't
        assertEquals(LimitedAction.TAKE_PILE, move(winston(listOf(emptyList(), emptyList(), listOf("bolt")), listOf("land"), pile = 2)).action)
        assertEquals(LimitedAction.PASS_PILE, move(winston(listOf(emptyList(), emptyList(), listOf("ox")), listOf("land"), pile = 2)).action)
        // An empty stack and no later pile: it must take even a Mountain
        val forced = move(winston(listOf(emptyList(), emptyList(), listOf("land")), emptyList(), pile = 2))
        assertEquals(LimitedMove(bot, LimitedAction.TAKE_PILE, 0, pile = 2), forced)
    }

    // DRF-298, DRF-300: Winchester: the bot takes the pile worth most together, two Cinders before the Tyrant alone
    @Test fun winchesterBotTakesThePileWorthMost() {
        assertEquals(1, move(winston(listOf(listOf("ox"), listOf("bomb")), listOf("land"), mode = DraftMode.WINCHESTER)).pile)
        assertEquals(1, move(winston(listOf(listOf("bomb"), listOf("bolt-1", "bolt-2")), listOf("land"), mode = DraftMode.WINCHESTER)).pile)
    }

    // DRF-298: a Winston draft with a bot runs: every time it is the bot's turn it moves at once
    @Test fun winstonDraftWithABotNeverWaitsForIt() {
        val settings = DraftSettings.winston().copy(packsPerPlayer = 1, cardsPerPack = 5, bots = 1)
        var state = DraftBotSeats.run(LimitedEngine.startFromCube(listOf(human, bot), cube(2), settings, seed), cards, log)
        var moves = 0
        while (!state.finished) {
            check(moves++ < 100) { "the draft didn't end" }
            assertTrue(state.queues.getValue(bot).isEmpty())
            val table = LimitedEngine.view(state, human).tables.first()
            val take = LimitedEngine.act(state, LimitedMove(human, LimitedAction.TAKE_PILE, table.turn, pile = table.pile))
            state = DraftBotSeats.run(take.state!!, cards, log)
        }
        assertEquals(10, state.pools.values.sumOf { it.size })
        assertTrue(state.pools.getValue(bot).isNotEmpty())
    }
}
