package org.asyncttwiz.protocol

import kotlin.test.*

/** The draft bot's pick heuristic (backlog: draft bots); invented cards. */
class DraftBotTest {
    private fun card(name: String, cost: String, type: String, text: String = "", power: String = "", toughness: String = "") =
        CardDefinition(name, listOf(DefinitionFace("f-$name", name, typeLine = type, manaCost = cost, rulesText = text,
            power = power, toughness = toughness)))
    private fun offer(id: String, definition: CardDefinition?, rarity: SetCardRarity? = SetCardRarity.COMMON, supported: Boolean = true) =
        DraftBotCard(id, definition, rarity, supported)

    private val vanilla = card("Plodding Ox", "{2}{G}", "Creature — Ox", power = "2", toughness = "2")
    private val bomb = card("Skyrend Tyrant", "{3}{R}{R}", "Creature — Dragon",
        "Flying\nWhen this creature enters, it deals 4 damage to any target.", "5", "5")
    private val bolt = card("Quick Cinder", "{R}", "Instant", "Quick Cinder deals 3 damage to any target.")
    private val whiteBear = card("Dawn Cub", "{1}{W}", "Creature — Bear", power = "2", toughness = "2")
    private val blueBear = card("Tide Cub", "{1}{U}", "Creature — Bear", power = "2", toughness = "2")
    private val blackBear = card("Grave Cub", "{1}{B}", "Creature — Bear", power = "2", toughness = "2")
    private val mountain = card("Mountain", "", "Basic Land — Mountain")

    @Test fun aBombRareBeatsAVanillaCommon() {
        val choice = DraftBot.choose(listOf(offer("a", vanilla), offer("b", bomb, SetCardRarity.RARE)), emptyList(), 1)
        assertEquals(listOf("b"), choice.picks)
        val parts = choice.score("b")!!.parts.map { it.label }
        assertEquals(listOf("Base", "Rare", "Stats 5/5 for 5", "Keywords: flying", "Removal"), parts)
        assertTrue(choice.score("b")!!.total > choice.score("a")!!.total)
    }

    @Test fun eachScoreBreaksDownIntoNamedParts() {
        val score = DraftBot.score(offer("x", bolt), emptyList(), 0)
        assertEquals(listOf(DraftBotPart("Base", 40), DraftBotPart("Removal", 12)), score.parts)
        assertEquals(52, score.total)
        val ox = DraftBot.score(offer("o", vanilla), emptyList(), 0)
        // 2 + 2 against 2 × 3 + 1: three below the curve, 3 points each
        assertEquals(listOf(DraftBotPart("Base", 40), DraftBotPart("Stats 2/2 for 3", -9)), ox.parts)
        assertEquals(listOf(DraftBotPart("Base", 40), DraftBotPart("Basic land", -40)), DraftBot.score(offer("m", mountain), emptyList(), 0).parts)
    }

    @Test fun unknownAndUnsupportedCardsScoreNeutral() {
        val choice = DraftBot.choose(listOf(offer("u", null), offer("n", bomb, SetCardRarity.MYTHIC, supported = false)),
            listOf(offer("p", whiteBear)), 20)
        assertEquals(listOf(DraftBotPart("Unknown card", DraftBot.NEUTRAL)), choice.score("u")!!.parts)
        assertEquals(listOf(DraftBotPart("Not supported", DraftBot.NEUTRAL)), choice.score("n")!!.parts)
    }

    @Test fun colourCommitmentGrowsWithThePicks() {
        assertEquals(0, DraftBot.commitment(1))
        assertEquals(0, DraftBot.commitment(DraftBot.OPEN_PICKS))
        assertTrue(DraftBot.commitment(DraftBot.OPEN_PICKS + 1) in 1..99)
        assertEquals(100, DraftBot.commitment(DraftBot.FULL_PICK))
        assertEquals(100, DraftBot.commitment(45))
        val picks = List(5) { offer("w$it", whiteBear) } + List(3) { offer("u$it", blueBear) } + offer("b0", blackBear)
        assertEquals(listOf(DraftColor.W, DraftColor.U), DraftBot.mainColors(picks))
        val bears = listOf(offer("black", blackBear), offer("blue", blueBear))
        // Early the bot stays open: equal cards tie, and the name decides (Grave Cub before Tide Cub)
        assertEquals(listOf("black"), DraftBot.choose(bears, picks, 2).picks)
        // Later it takes the card of its colours, and the other is marked off colour
        val later = DraftBot.choose(bears, picks, 14)
        assertEquals(listOf("blue"), later.picks)
        assertEquals(DraftBotPart("In colours WU", DraftBot.IN_COLOURS), later.score("blue")!!.parts.last())
        assertEquals(DraftBotPart("Off colour B", -DraftBot.OFF_COLOUR), later.score("black")!!.parts.last())
    }

    @Test fun aSecondColourStaysOpenWhileTheBotHasOne() {
        val picks = List(6) { offer("w$it", whiteBear) }
        val choice = DraftBot.choose(listOf(offer("blue", blueBear)), picks, 14)
        assertEquals(listOf("Base", "Stats 2/2 for 2"), choice.score("blue")!!.parts.map { it.label })
        val gold = card("Dusk Pact", "{U}{B}", "Sorcery", "Draw two cards.")
        assertEquals(DraftBotPart("Off colour UB", -DraftBot.OFF_COLOUR), DraftBot.choose(listOf(offer("g", gold)), picks, 14).score("g")!!.parts.last())
        // A hybrid symbol needs one of its colours
        val hybrid = card("Twin Ward", "{1}{W/B}", "Instant", "Destroy target creature.")
        assertEquals(DraftBotPart("In colours W", DraftBot.IN_COLOURS), DraftBot.choose(listOf(offer("h", hybrid)), picks, 14).score("h")!!.parts.last())
    }

    @Test fun keepTakesTheBestCardsInOrder() {
        val cards = listOf(offer("ox", vanilla), offer("bolt", bolt), offer("bomb", bomb, SetCardRarity.MYTHIC), offer("land", mountain))
        assertEquals(listOf("bomb", "bolt"), DraftBot.choose(cards, emptyList(), 1, keep = 2).picks)
        assertEquals(listOf("bomb", "bolt", "ox", "land"), DraftBot.choose(cards, emptyList(), 1, keep = 9).picks)
    }

    @Test fun theSameInputsGiveTheSameChoice() {
        val cards = listOf(offer("1", whiteBear), offer("2", blueBear), offer("3", blackBear), offer("4", bolt), offer("5", vanilla))
        val picks = listOf(offer("p1", bolt), offer("p2", blueBear))
        val first = DraftBot.choose(cards, picks, 9)
        assertEquals(first, DraftBot.choose(cards, picks, 9))
        // The offer's order changes neither the pick nor any score
        val reversed = DraftBot.choose(cards.reversed(), picks, 9)
        assertEquals(first.picks, reversed.picks)
        assertEquals(first.scores.sortedBy { it.card }, reversed.scores.sortedBy { it.card })
        // Two copies of one card tie: the lower id wins
        assertEquals(listOf("a"), DraftBot.choose(listOf(offer("b", bolt), offer("a", bolt)), emptyList(), 1).picks)
    }

    @Test fun rulesTextSignals() {
        fun labels(definition: CardDefinition) = DraftBot.score(offer("x", definition), emptyList(), 0).parts.map { it.label }.drop(1)
        assertEquals(listOf("Sweeper"), labels(card("Ashfall", "{2}{B}{B}", "Sorcery", "Destroy all creatures.")))
        assertEquals(listOf("Bounce"), labels(card("Undertow", "{1}{U}", "Instant", "Return target creature to its owner's hand.")))
        assertEquals(listOf("Counterspell"), labels(card("Hush", "{1}{U}{U}", "Instant", "Counter target spell.")))
        assertEquals(listOf("Draws cards"), labels(card("Ponder Deep", "{2}{U}", "Sorcery", "Draw two cards.")))
        assertEquals(listOf("Makes tokens"), labels(card("Call Kin", "{1}{W}", "Sorcery", "Create two 1/1 white Soldier creature tokens.")))
        assertEquals(listOf("Returns a card"), labels(card("Dig Up", "{B}", "Sorcery", "Return target creature card from your graveyard to your hand.")))
        assertEquals(listOf("Planeswalker"), labels(card("Mira, Seer", "{2}{U}", "Legendary Planeswalker — Mira")))
        assertEquals(listOf("Land"), labels(card("Shrine Path", "", "Land", "{T}: Add {C}.")))
        assertEquals(listOf("Costs 7"), labels(card("Titan Idol", "{7}", "Artifact")))
        // Keyword lines only, reminder text left out; "flying" in other text is no keyword
        assertEquals(listOf("Stats 1/1 for 1", "Keywords: flying, lifelink"),
            labels(card("Moth", "{W}", "Creature — Insect", "Flying, lifelink (Damage dealt by this creature also causes you to gain that much life.)",
                "1", "1")))
        assertEquals(listOf("Stats 2/2 for 2"), labels(card("Netter", "{1}{G}", "Creature — Spider",
            "Creatures with flying can't attack you.", "2", "2")))
    }
}
