package arcanum.engine.cube

import arcanum.engine.decision.Answer
import arcanum.api.Keyword
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** Cube chunk 12, harder card flow, played as a player would: X announced, searches answered, triggers resolved. */
class Chunk12HarderCardFlowScenariosTest {
    /** Coda of Ruin: a creature card with mana value X or less from the graveyard onto the battlefield. */
    @Test
    fun codaOfRuinFromTheGraveyard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand("Coda of Ruin")
        alice.graveyard("Elvish Hierophant", "Bull of the Pyre")
        alice.library("Island", "Swamp")

        alice.casts("Coda of Ruin", x = 3)
        allPass()
        // "and/or": the searcher chooses the zones (CR 701.23a)
        answer(Answer.Option("library and graveyard"))
        alice.chooses("Elvish Hierophant")

        expect {
            alice.battlefield has "Elvish Hierophant"
            alice.graveyard has "Bull of the Pyre"
            "Elvish Hierophant".power shouldBe 2
        }
    }

    /** Coda of Ruin: from the library, then shuffle. */
    @Test
    fun codaOfRuinFromTheLibrary() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand("Coda of Ruin")
        alice.library("Island", "Elvish Seer", "Canopy Visionary")

        alice.casts("Coda of Ruin", x = 1)
        allPass()
        // "and/or": the searcher chooses the zones (CR 701.23a)
        answer(Answer.Option("library and graveyard"))
        alice.chooses("Elvish Seer")

        expect {
            alice.battlefield has "Elvish Seer"
            alice.library.size shouldBe 2
            log mentions "Searched"
        }
    }

    /** Coda of Ruin with X 10: creatures you control also get +10/+10 and haste until end of turn. */
    @Test
    fun codaOfRuinWithXTen() = scenario {
        alice.battlefield(*Array(12) { "Forest" })
        alice.battlefield("Elvish Seer")
        alice.hand("Coda of Ruin")
        alice.graveyard("Elvish Hierophant")

        alice.casts("Coda of Ruin", x = 10)
        allPass()
        // "and/or": the searcher chooses the zones (CR 701.23a)
        answer(Answer.Option("library and graveyard"))
        alice.chooses("Elvish Hierophant")

        expect {
            "Elvish Hierophant".power shouldBe 12
            "Elvish Seer".power shouldBe 12
            "Elvish Hierophant" hasKeyword Keyword.Haste
        }
    }

    /** Verdant Moon's Apex: a green creature card with mana value X or less onto the battlefield; the Apex is shuffled
     * into its owner's library. */
    @Test
    fun verdantMoonsApex() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand("Verdant Moon's Apex")
        alice.library("Myriad-Faced Shade", "Canopy Visionary", "Island")

        alice.casts("Verdant Moon's Apex", x = 3)
        allPass()
        alice.chooses("Canopy Visionary")

        expect {
            alice.battlefield has "Canopy Visionary"
            // Its enters trigger
            stack.size shouldBe 1
            alice.graveyard.isEmpty()
            alice.library.size shouldBe 3
            alice.library has "Verdant Moon's Apex"
        }
    }

    /** Eldritch Realization: draw X, discard X; a 1/1 white flying Spirit for each card type among the discarded. */
    @Test
    fun eldritchRealization() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand("Eldritch Realization", "Denial")
        alice.library("Forest", "Storm Spark", "Elvish Seer", "Swamp")

        alice.casts("Eldritch Realization", x = 3)
        allPass()
        // A land, an instant and a creature
        alice.chooses("Forest", "Storm Spark", "Elvish Seer")

        expect {
            alice.hand.names shouldBe listOf("Denial")
            alice.battlefield.names.count { it == "Spirit Token" } shouldBe 3
            card("Spirit Token") hasKeyword Keyword.Flying
        }
    }

    /** Eldritch Realization: two lands discarded are one card type: one Spirit. */
    @Test
    fun eldritchRealizationOneType() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Eldritch Realization")
        alice.library("Forest", "Swamp", "Storm Spark")

        alice.casts("Eldritch Realization", x = 2)
        allPass()

        expect {
            alice.graveyard.names.toSet() shouldBe setOf("Eldritch Realization", "Forest", "Swamp")
            alice.battlefield.names.count { it == "Spirit Token" } shouldBe 1
        }
    }

    /** Recursive Result: return any number of your nonland, nontoken permanents; draw a card for each returned. */
    @Test
    fun recursiveResult() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Canopy Visionary", "Lotus Frond", "Elvish Seer")
        alice.hand("Recursive Result")
        alice.library("Forest", "Swamp", "Plains")

        alice.casts("Recursive Result", targets = listOf("Canopy Visionary", "Lotus Frond"))
        allPass()

        expect {
            alice.hand.names.toSet() shouldBe setOf("Canopy Visionary", "Lotus Frond", "Forest", "Swamp")
            alice.battlefield has "Elvish Seer"
            alice.library.names shouldBe listOf("Plains")
        }
    }

    /** Hint of the Wild: this turn, each creature spell you cast draws a card. */
    @Test
    fun hintOfTheWild() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand("Hint of the Wild", "Elvish Seer", "Thicketwood Elves")
        alice.library("Island", "Swamp")

        alice.casts("Hint of the Wild")
        allPass()
        alice.casts("Elvish Seer")
        expect { stack.names shouldBe listOf("Elvish Seer", "ability of Hint of the Wild") }
        allPass()
        expect { alice.hand.names shouldBe listOf("Thicketwood Elves", "Island") }
        allPass()
        alice.casts("Thicketwood Elves")
        allPass()
        allPass()

        expect {
            alice.battlefield has "Elvish Seer"
            alice.battlefield has "Thicketwood Elves"
            alice.hand.names shouldBe listOf("Island", "Swamp")
        }
    }
}
