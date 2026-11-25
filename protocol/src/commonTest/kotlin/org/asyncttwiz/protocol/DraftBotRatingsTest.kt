package org.asyncttwiz.protocol

import kotlin.test.*

/** The draft bot's card ratings and splashes (owner, 2026-11-25); invented cards and ratings. */
class DraftBotRatingsTest {
    private fun card(name: String, cost: String, type: String, text: String = "", power: String = "", toughness: String = "") =
        CardDefinition(name, listOf(DefinitionFace("f-$name", name, typeLine = type, manaCost = cost, rulesText = text,
            power = power, toughness = toughness)))
    private fun offer(id: String, definition: CardDefinition, rarity: SetCardRarity = SetCardRarity.COMMON) = DraftBotCard(id, definition, rarity)

    private val dawnCub = card("Dawn Cub", "{1}{W}", "Creature — Bear", power = "2", toughness = "2")
    private val tideCub = card("Tide Cub", "{1}{U}", "Creature — Bear", power = "2", toughness = "2")
    private val graveCub = card("Grave Cub", "{1}{B}", "Creature — Bear", power = "2", toughness = "2")
    private val nightTerror = card("Night Terror", "{3}{B}", "Creature — Horror", power = "3", toughness = "3")
    private val doubleDread = card("Double Dread", "{2}{B}{B}", "Creature — Horror", power = "3", toughness = "3")
    private val emberLord = card("Ember Lord", "{3}{R}", "Creature — Elemental", power = "3", toughness = "3")
    private val skyTyrant = card("Sky Tyrant", "{3}{R}{R}", "Creature — Dragon", "Flying", "4", "4")
    private val ashGiant = card("Ash Giant", "{4}{R}", "Creature — Giant", "When this creature enters, it deals 4 damage to any target.", "5", "5")
    private val plainOx = card("Plain Ox", "{2}{G}", "Creature — Ox", power = "2", toughness = "2")

    /** Twenty fillers rated 50.0 % to 59.5 %, and the named cards. */
    private val ratings = DraftBotRatings("TST", (0 until 20).associate { "Filler $it" to 500 + 5 * it } + mapOf(
        "Dawn Cub" to 548, "Tide Cub" to 548, "Grave Cub" to 548, "Night Terror" to 640, "Double Dread" to 640,
        "Ember Lord" to 640, "Sky Tyrant" to 660, "Plain Ox" to 600))

    /** A WU bot: five white and four blue picks. */
    private val wu = List(5) { offer("w$it", dawnCub) } + List(4) { offer("u$it", tideCub) }

    // DRF-312: a rated card's strength is its rating, put on the heuristic's scale: the set's mean near the heuristic's,
    // a strong card well above; the rating beats the heuristic's view of the card
    @Test fun aRatedCardIsJudgedByItsRating() {
        val ox = DraftBot.score(offer("ox", plainOx), emptyList(), 0, ratings)
        assertEquals(1, ox.parts.size)
        assertEquals("Rating (GIH WR 60.0%)", ox.parts.single().label)
        val cub = DraftBot.score(offer("cub", dawnCub), emptyList(), 0, ratings).total
        assertTrue(cub in 38..48, "a card near the set's mean scores near the heuristic's mean: $cub")
        assertTrue(DraftBot.score(offer("t", skyTyrant), emptyList(), 0, ratings).total > 70)
        // Without ratings (a cube), the heuristic: the 2/2 for 3 scores below the 2/2 for 2; with them the ox's rating wins
        assertTrue(DraftBot.score(offer("ox", plainOx), emptyList(), 0).total < DraftBot.score(offer("cub", dawnCub), emptyList(), 0).total)
        assertEquals(listOf("ox"), DraftBot.choose(listOf(offer("cub", dawnCub), offer("ox", plainOx)), emptyList(), 1, ratings = ratings).picks)
    }

    // DRF-312: a card without a rating keeps the heuristic, rescaled to the ratings' scale, and says it has none
    @Test fun aCardWithoutARatingKeepsTheHeuristic() {
        val bolt = card("Quick Cinder", "{R}", "Instant", "Quick Cinder deals 3 damage to any target.")
        val unrated = DraftBot.score(offer("b", bolt), emptyList(), 0, ratings)
        // 52 is 0.89 heuristic deviations above the heuristic's mean: as many of the ratings' deviations above theirs
        assertEquals(listOf(DraftBotPart("No rating: base", 40), DraftBotPart("Removal", 12), DraftBotPart("Rescaled to the ratings", 4)),
            unrated.parts)
        assertEquals(56, unrated.total)
        assertEquals(DraftBot.HEURISTIC_MEAN.toInt(), ratings.rescaled(DraftBot.HEURISTIC_MEAN.toInt()))
        // An ordinary card (the heuristic's mean) and one rated at its set's mean score alike
        assertTrue(kotlin.math.abs(ratings.rescaled(43) - ratings.points(563)) <= 1)
    }

    // DRF-312, DRF-299: burns use the ratings too
    @Test fun burnsUseTheRatings() {
        val rest = listOf(offer("cub", dawnCub), offer("ox", plainOx))
        assertEquals(listOf("cub"), DraftBot.burns(rest, 1))
        assertEquals(listOf("ox"), DraftBot.burns(rest, 1, ratings))
    }

    // DRF-313: a strong card one coloured pip off the bot's two colours costs a quarter of the off-colour penalty
    @Test fun aStrongCardOnePipOffIsASplash() {
        val choice = DraftBot.choose(listOf(offer("terror", nightTerror), offer("grave", graveCub), offer("dread", doubleDread)), wu, 20,
            ratings = ratings)
        assertEquals(DraftBotPart("Splash B", -DraftBot.SPLASH), choice.score("terror")!!.parts.last())
        // Not strong enough: the full penalty
        assertEquals(DraftBotPart("Off colour B", -DraftBot.OFF_COLOUR), choice.score("grave")!!.parts.last())
        // Two pips off: the full penalty, however strong
        assertEquals(DraftBotPart("Off colour B", -DraftBot.OFF_COLOUR), choice.score("dread")!!.parts.last())
        // The penalty grows with the commitment, as the full one does
        val early = DraftBot.choose(listOf(offer("terror", nightTerror)), wu, 8, ratings = ratings)
        assertEquals(DraftBotPart("Splash B", -DraftBot.SPLASH * early.commitment / 100), early.score("terror")!!.parts.last())
        assertNull(choice.splash, "no splash among the picks yet")
    }

    // DRF-313: one splash colour at most: once its picks splash red, a strong black card is off colour
    @Test fun theBotSplashesOneColourAtMost() {
        val picks = wu + offer("lord", emberLord)
        assertEquals(DraftColor.R, DraftBot.splashColor(picks, listOf(DraftColor.W, DraftColor.U), ratings))
        val choice = DraftBot.choose(listOf(offer("terror", nightTerror), offer("lord2", emberLord)), picks, 20, ratings = ratings)
        assertEquals(DraftColor.R, choice.splash)
        assertEquals(DraftBotPart("Off colour B", -DraftBot.OFF_COLOUR), choice.score("terror")!!.parts.last())
        assertEquals(DraftBotPart("Splash R", -DraftBot.SPLASH), choice.score("lord2")!!.parts.last())
    }

    // DRF-313: without a rating (a cube), strength by the heuristic decides the splash
    @Test fun withoutRatingsTheHeuristicDecidesTheSplash() {
        val wuCube = List(5) { offer("w$it", dawnCub) } + List(4) { offer("u$it", tideCub) }
        val giant = DraftBot.score(offer("giant", ashGiant, SetCardRarity.RARE), emptyList(), 0).total
        assertTrue(giant >= DraftBot.HEURISTIC_STRONG, "$giant")
        val choice = DraftBot.choose(listOf(offer("giant", ashGiant, SetCardRarity.RARE), offer("lord", emberLord)), wuCube, 20)
        assertEquals(DraftBotPart("Splash R", -DraftBot.SPLASH), choice.score("giant")!!.parts.last())
        assertEquals(DraftBotPart("Off colour R", -DraftBot.OFF_COLOUR), choice.score("lord")!!.parts.last())
    }

    // DRF-313: colour adds to strength, so a bomb off colour beats an ordinary card of the bot's colours at the first pick of
    // packs 2 and 3 (picks 15 and 29 of 14-card packs), but not an ordinary card off colour beats an ordinary one in colour
    @Test fun aLargeGapBeatsColour() {
        for (pick in listOf(15, 29)) {
            val choice = DraftBot.choose(listOf(offer("cub", dawnCub), offer("tyrant", skyTyrant, SetCardRarity.MYTHIC)), wu, pick,
                ratings = ratings)
            assertEquals(listOf("tyrant"), choice.picks, "pick $pick: ${choice.scores}")
            assertEquals(DraftBotPart("Off colour R", -DraftBot.OFF_COLOUR), choice.score("tyrant")!!.parts.last())
            assertEquals(listOf("cub"), DraftBot.choose(listOf(offer("cub", dawnCub), offer("grave", graveCub)), wu, pick, ratings = ratings).picks)
        }
    }

    // DRF-312: a set's top tenth by rating is strong; the ratings say how many cards they hold
    @Test fun theTopTenthIsStrong() {
        assertEquals(640, ratings.strong)
        assertTrue(DraftBot.strong(offer("t", nightTerror), ratings))
        assertFalse(DraftBot.strong(offer("ox", plainOx), ratings))
        assertEquals("TST ratings of 28 cards", ratings.toString())
    }
}
