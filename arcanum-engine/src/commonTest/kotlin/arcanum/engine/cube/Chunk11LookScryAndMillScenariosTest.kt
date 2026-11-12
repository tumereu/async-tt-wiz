package arcanum.engine.cube

import arcanum.api.Color
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cube chunk 11, looking, scrying and milling, played as a player would: cast, resolved, the looked-at cards placed. */
class Chunk11LookScryAndMillScenariosTest {
    /** Mull It Over: surveil 1, then draw a card. */
    @Test
    fun mullItOver() = scenario {
        alice.battlefield("Island")
        alice.hand("Mull It Over")
        alice.library("Forest", "Storm Spark")

        alice.casts("Mull It Over")
        allPass()
        // CR 701.25a: the Forest goes to the graveyard
        alice.chooses("Forest")

        expect {
            alice.graveyard has "Forest"
            alice.hand.names shouldBe listOf("Storm Spark")
        }
    }

    /** Glance Ahead: scry 1, then draw a card. */
    @Test
    fun glanceAhead() = scenario {
        alice.battlefield("Island")
        alice.hand("Glance Ahead")
        alice.library("Forest", "Storm Spark", "Swamp")

        alice.casts("Glance Ahead")
        allPass()
        // CR 701.22a: the Forest goes to the bottom
        alice.chooses("Forest")

        expect {
            alice.hand.names shouldBe listOf("Storm Spark")
            alice.library.names shouldBe listOf("Swamp", "Forest")
        }
    }

    /** Foretell the Way: scry 2, then draw a card. */
    @Test
    fun foretellTheWay() = scenario {
        alice.battlefield("Island")
        alice.hand("Foretell the Way")
        alice.library("Forest", "Storm Spark", "Swamp")

        alice.casts("Foretell the Way")
        allPass()
        alice.chooses("Forest")

        expect {
            alice.hand.names shouldBe listOf("Storm Spark")
            alice.library.names shouldBe listOf("Swamp", "Forest")
        }
    }

    /** Ichor Dreams: draw a card, then scry 2. */
    @Test
    fun ichorDreams() = scenario {
        alice.battlefield("Island")
        alice.hand("Ichor Dreams")
        alice.library("Storm Spark", "Forest", "Swamp", "Plains")

        alice.casts("Ichor Dreams")
        allPass()
        // CR 701.22a: both stay on top, in alice's order: the Swamp first
        alice.answers(Answer.Objects(emptyList()))
        alice.chooses("Swamp", "Forest")

        expect {
            alice.hand.names shouldBe listOf("Storm Spark")
            alice.library.names shouldBe listOf("Swamp", "Forest", "Plains")
        }
    }

    /** Muse Awhile: look at the top three, put them back in any order (no shuffle), then draw. */
    @Test
    fun museAwhile() = scenario {
        alice.battlefield("Island")
        alice.hand("Muse Awhile")
        alice.library("Forest", "Island", "Storm Spark", "Mountain")

        alice.casts("Muse Awhile")
        allPass()
        answer(Answer.Objects(listOf(card("Storm Spark"), card("Island", 1), card("Forest"))))
        answer(Answer.YesNo(false))

        expect {
            alice.hand.names shouldBe listOf("Storm Spark")
            alice.library.names shouldBe listOf("Island", "Forest", "Mountain")
        }
    }

    /** Mind Squall: draw three, then put two cards from your hand on top of your library in any order. */
    @Test
    fun mindSquall() = scenario {
        alice.battlefield("Island")
        alice.hand("Mind Squall", "Forest")
        alice.library("Storm Spark", "Swamp", "Plains", "Mountain")

        alice.casts("Mind Squall")
        allPass()
        alice.chooses("Forest", "Swamp")
        // CR 401.4: alice puts them in her order, first on top
        alice.chooses("Swamp", "Forest")

        expect {
            alice.hand.names.toSet() shouldBe setOf("Storm Spark", "Plains")
            alice.library.names shouldBe listOf("Swamp", "Forest", "Mountain")
        }
    }

    /** Cerebral Scan: {U/P} paid with 2 life; look at target player's hand; draw a card. */
    @Test
    fun cerebralScan() = scenario {
        alice.hand("Cerebral Scan")
        alice.library("Forest")
        bob.hand("Storm Spark", "Denial")

        alice.casts("Cerebral Scan", target = "bob")
        expect { alice.life shouldBe 18 }
        allPass()

        expect { alice.hand.names shouldBe listOf("Forest") }
    }

    /** Thought Siphon: target player mills two; draw a card. */
    @Test
    fun thoughtSiphon() = scenario {
        alice.battlefield("Island")
        alice.hand("Thought Siphon")
        alice.library("Forest")
        bob.library("Storm Spark", "Denial", "Swamp")

        alice.casts("Thought Siphon", target = "bob")
        allPass()

        expect {
            bob.graveyard.names shouldBe listOf("Storm Spark", "Denial")
            bob.library.names shouldBe listOf("Swamp")
            alice.hand.names shouldBe listOf("Forest")
        }
    }

    /** Manashift: two mana in any combination of colours, then draw; the mana casts a spell. */
    @Test
    fun manashift() = scenario {
        alice.battlefield("Mountain", "Forest")
        alice.hand("Manashift")
        alice.library("Storm Spark")

        alice.casts("Manashift")
        allPass()
        answer(Answer.ColorChoice(Color.Red))
        answer(Answer.ColorChoice(Color.Blue))
        assertEquals(listOf(Color.Red, Color.Blue), alice.pool.map { it.color })

        alice.casts("Storm Spark", target = "bob")
        allPass()
        expect {
            bob.life shouldBe 17
            alice.graveyard has "Storm Spark"
        }
        assertEquals(listOf(Color.Blue), alice.pool.map { it.color })
    }

    /** Solid Proof: a 0/3 blue Crab and a Clue; the Clue draws a card for {2} and itself. */
    @Test
    fun solidProofAndItsClue() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Solid Proof")
        alice.library("Forest")

        alice.casts("Solid Proof", paying = listOf("Island"))
        allPass()
        expect {
            card("Crab Token").power shouldBe 0
            card("Crab Token").toughness shouldBe 3
            card("Crab Token").colors shouldBe listOf(Color.Blue)
            alice.battlefield has "Clue Token"
        }

        alice.activates("Clue Token")
        expect { alice.battlefield lacks "Clue Token" }
        allPass()

        expect {
            alice.hand.names shouldBe listOf("Forest")
            (0..2).all { card("Island", it).tapped } shouldBe true
        }
    }

    /** Shadowsting: target creature gets -1/-1 until end of turn. */
    @Test
    fun shadowsting() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Shadowsting")
        bob.battlefield("Elvish Hierophant", "Elvish Seer")

        alice.casts("Shadowsting", target = "Elvish Hierophant")
        allPass()
        expect {
            "Elvish Hierophant".power shouldBe 1
            "Elvish Hierophant".toughness shouldBe 1
            "Elvish Seer".power shouldBe 2
        }

        // CR 514.2: it ends in the cleanup step
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { "Elvish Hierophant".power shouldBe 2 }
    }

    /** Shadowsting kills a 1/1. */
    @Test
    fun shadowstingKills() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Shadowsting")
        bob.battlefield("Elvish Seer")

        alice.casts("Shadowsting", target = "Elvish Seer")
        allPass()

        expect {
            bob.graveyard has "Elvish Seer"
            alice.graveyard has "Shadowsting"
        }
    }

    /** Shadowsting's dredge 3: instead of the draw step's draw, mill three and return it to hand. */
    @Test
    fun shadowstingDredgedInTheDrawStep() = scenario {
        turn(3, active = alice, step = TurnStep.Upkeep)
        alice.graveyard("Shadowsting")
        alice.library("Forest", "Island", "Swamp", "Mountain")

        advanceTo(TurnStep.Draw)
        // CR 702.52a, 614.11: dredge replaces the draw, if alice wants
        answer(Answer.YesNo(true))

        expect {
            alice.hand.names shouldBe listOf("Shadowsting")
            alice.graveyard.names shouldBe listOf("Forest", "Island", "Swamp")
            alice.library.names shouldBe listOf("Mountain")
        }
    }

    /** Rebirth from the Mire: return up to three target land cards from your graveyard to your hand. */
    @Test
    fun rebirthFromTheMire() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand("Rebirth from the Mire")
        alice.graveyard("Island", "Swamp", "Plains", "Mountain", "Storm Spark")

        alice.casts("Rebirth from the Mire", targets = listOf("Island", "Swamp", "Plains"))
        allPass()

        expect {
            alice.hand.names.toSet() shouldBe setOf("Island", "Swamp", "Plains")
            alice.graveyard.names.toSet() shouldBe setOf("Mountain", "Storm Spark", "Rebirth from the Mire")
        }
    }

    /** Rebirth from the Mire's dredge 3 replaces a spell's draw. */
    @Test
    fun rebirthFromTheMireDredged() = scenario {
        alice.battlefield("Island")
        alice.graveyard("Rebirth from the Mire")
        alice.hand("Thought Siphon")
        alice.library("Forest", "Island", "Swamp", "Mountain")
        bob.library("Storm Spark", "Denial")

        alice.casts("Thought Siphon", target = "bob")
        allPass()
        answer(Answer.YesNo(true))

        expect {
            alice.hand.names shouldBe listOf("Rebirth from the Mire")
            alice.graveyard.names.toSet() shouldBe setOf("Forest", "Island", "Swamp", "Thought Siphon")
            alice.library.names shouldBe listOf("Mountain")
        }
    }
}
