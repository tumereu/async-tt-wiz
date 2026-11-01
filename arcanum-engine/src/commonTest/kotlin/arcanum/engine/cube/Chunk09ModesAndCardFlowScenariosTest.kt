package arcanum.engine.cube

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/** Cube chunk 9, modal spells and card flow, played as a player would: modes chosen, costs paid, resolved. */
class Chunk09ModesAndCardFlowScenariosTest {
    /** A vanilla 3/3. */
    private val brute = Cards.invent("Bramblehide Brute", "{2}{G}", "Creature — Beast", "", "3/3")

    /** Grindstone Rasp, first mode: 3 damage to target creature. */
    @Test
    fun grindstoneRaspDamage() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Grindstone Rasp")
        bob.battlefield(brute)

        alice.casts("Grindstone Rasp", modes = listOf(0), target = brute)
        allPass()

        expect { bob.graveyard has brute }
    }

    /** Grindstone Rasp, second mode: destroy target artifact. */
    @Test
    fun grindstoneRaspArtifact() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Grindstone Rasp")
        bob.battlefield("Lotus Frond", brute)

        alice.casts("Grindstone Rasp", modes = listOf(1), target = "Lotus Frond")
        allPass()

        expect {
            bob.graveyard has "Lotus Frond"
            bob.battlefield has brute
        }
    }

    /** Stormforge Charm, first mode: counter a noncreature spell unless its controller pays {2}. */
    @Test
    fun stormforgeCharmCounters() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Island", "Mountain")
        bob.hand("Stormforge Charm")

        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        bob.casts("Stormforge Charm", modes = listOf(0), target = "Storm Spark")
        allPass()
        // CR 118.12a: alice could pay with her two untapped Mountains, but declines
        answer(Answer.YesNo(false))

        expect {
            alice.graveyard has "Storm Spark"
            bob.life shouldBe 20
        }
    }

    /** Stormforge Charm, second mode: 2 damage to target creature. */
    @Test
    fun stormforgeCharmDamage() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand("Stormforge Charm")
        bob.battlefield("Elvish Hierophant")

        alice.casts("Stormforge Charm", modes = listOf(1), target = "Elvish Hierophant")
        allPass()

        expect { bob.graveyard has "Elvish Hierophant" }
    }

    /** Stormforge Charm, third mode: draw two cards, then discard two. */
    @Test
    fun stormforgeCharmLoots() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand("Stormforge Charm", "Forest")
        alice.library("Island", "Swamp")

        alice.casts("Stormforge Charm", modes = listOf(2))
        allPass()
        alice.chooses("Forest", "Swamp")

        expect {
            alice.hand.names shouldBe listOf("Island")
            alice.graveyard.names.toSet() shouldBe setOf("Stormforge Charm", "Forest", "Swamp")
        }
    }

    /** Warlord's Decree: two modes: return a creature card from your graveyard, and 2 damage to any target. */
    @Test
    fun warlordsDecreeRegrowthAndDamage() = scenario {
        alice.battlefield("Swamp", "Mountain", "Mountain")
        alice.hand("Warlord's Decree")
        alice.graveyard("Elvish Seer")

        alice.casts("Warlord's Decree", modes = listOf(0, 3), targets = listOf("Elvish Seer", "bob"))
        allPass()

        expect {
            alice.hand has "Elvish Seer"
            bob.life shouldBe 18
        }
    }

    /** Warlord's Decree: two other modes: target player discards a card, and destroy target artifact. */
    @Test
    fun warlordsDecreeDiscardAndArtifact() = scenario {
        alice.battlefield("Swamp", "Mountain", "Mountain")
        alice.hand("Warlord's Decree")
        bob.hand("Storm Spark", "Forest")
        bob.battlefield("Lotus Frond")

        alice.casts("Warlord's Decree", modes = listOf(1, 2), targets = listOf("bob", "Lotus Frond"))
        allPass()
        bob.chooses("Forest")

        expect {
            bob.graveyard.names.toSet() shouldBe setOf("Forest", "Lotus Frond")
            bob.hand.names shouldBe listOf("Storm Spark")
        }
    }

    /** Spellforge Command: 2 damage to any target, and target player creates a Treasure. */
    @Test
    fun spellforgeCommandDamageAndTreasure() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain")
        alice.hand("Spellforge Command")

        alice.casts("Spellforge Command", modes = listOf(0, 2), targets = listOf("bob", "alice"))
        allPass()

        expect {
            bob.life shouldBe 18
            alice.battlefield has "Treasure Token"
        }
    }

    /** Spellforge Command: target player draws two then discards two, and destroy target artifact. */
    @Test
    fun spellforgeCommandLootAndArtifact() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain")
        alice.hand("Spellforge Command")
        bob.library("Forest", "Island")
        bob.battlefield("Lotus Frond")

        alice.casts("Spellforge Command", modes = listOf(1, 3), targets = listOf("bob", "Lotus Frond"))
        allPass()

        // bob's only two cards are discarded without asking
        expect {
            bob.hand.isEmpty()
            bob.graveyard.names.toSet() shouldBe setOf("Forest", "Island", "Lotus Frond")
        }
    }

    /** Blazing Confluence: one of each mode. */
    @Test
    fun blazingConfluenceEachMode() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Elvish Seer")
        alice.hand("Blazing Confluence")
        bob.battlefield("Elvish Hierophant", "Lotus Frond")

        alice.casts("Blazing Confluence", modes = listOf(0, 1, 2), target = "Lotus Frond",
            paying = listOf("Mountain", "Mountain", "Mountain", "Mountain"))
        allPass()

        expect {
            alice.graveyard has "Elvish Seer"
            bob.battlefield has "Elvish Hierophant"
            bob.graveyard has "Lotus Frond"
            bob.life shouldBe 18
        }
    }

    /** Blazing Confluence: the same mode three times (CR 700.2d). */
    @Test
    fun blazingConfluenceSameModeThrice() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Blazing Confluence")
        bob.battlefield("Elvish Hierophant")

        alice.casts("Blazing Confluence", modes = listOf(0, 0, 0))
        allPass()

        expect {
            bob.graveyard has "Elvish Hierophant"
            bob.life shouldBe 20
        }
    }

    /** Thorough Study: target player draws two; flashback for {1}{U} and 3 life. */
    @Test
    fun thoroughStudyAndItsFlashback() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand("Thorough Study")
        alice.library("Forest", "Swamp", "Plains", "Mountain")

        alice.casts("Thorough Study", target = "alice")
        allPass()
        alice.casts("Thorough Study", target = "alice")
        expect { alice.life shouldBe 17 }
        allPass()

        expect {
            alice.hand.size shouldBe 4
            alice.exile has "Thorough Study"
            (0..5).all { card("Island", it).tapped } shouldBe true
        }
    }

    /** Faithless Rummage: draw two then discard two; flashback {2}{R}. */
    @Test
    fun faithlessRummageAndItsFlashback() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Faithless Rummage", "Forest")
        alice.library("Island", "Swamp", "Plains", "Storm Spark")

        alice.casts("Faithless Rummage")
        allPass()
        alice.chooses("Forest", "Swamp")
        expect { alice.hand.names shouldBe listOf("Island") }

        alice.casts("Faithless Rummage")
        allPass()
        alice.chooses("Island", "Plains")

        expect {
            alice.hand.names shouldBe listOf("Storm Spark")
            alice.exile has "Faithless Rummage"
        }
    }

    /** Hoard Voyage: delve away the graveyard to pay; draw three. */
    @Test
    fun hoardVoyage() = scenario {
        alice.battlefield("Island")
        alice.graveyard("Storm Spark", "Denial", "Forest", "Mountain", "Swamp", "Plains", "Cinder Bolt")
        alice.hand("Hoard Voyage")
        alice.library("Island", "Island", "Island", "Swamp")

        alice.casts("Hoard Voyage", paying = listOf("Island", "Storm Spark", "Denial", "Forest", "Mountain", "Swamp", "Plains", "Cinder Bolt"))
        allPass()

        expect {
            alice.exile.size shouldBe 7
            alice.hand.names shouldBe listOf("Island", "Island", "Island")
            alice.graveyard.names shouldBe listOf("Hoard Voyage")
        }
    }

    /** Delve Through Ages: look at the top seven, two into the hand, the rest on the bottom. */
    @Test
    fun delveThroughAges() = scenario {
        alice.battlefield("Island", "Island")
        alice.graveyard("Storm Spark", "Denial", "Forest", "Mountain", "Swamp", "Plains")
        alice.hand("Delve Through Ages")
        alice.library("Forest", "Island", "Swamp", "Mountain", "Plains", "Denial", "Storm Spark", "Cinder Bolt")

        alice.casts("Delve Through Ages", paying = listOf("Island", "Island", "Storm Spark", "Denial", "Forest", "Mountain", "Swamp", "Plains"))
        allPass()
        alice.answers(Answer.Objects(listOf(card("Denial", 1), card("Storm Spark", 1))))
        // CR 401.4: the rest go to the bottom in the order alice chooses, first on top
        answer(Answer.Objects(listOf(card("Plains", 1), card("Mountain", 1), card("Swamp", 1), card("Island", 2), card("Forest", 1))))

        expect {
            alice.hand.names.toSet() shouldBe setOf("Denial", "Storm Spark")
            alice.library.names shouldBe listOf("Cinder Bolt", "Plains", "Mountain", "Swamp", "Island", "Forest")
            alice.exile.size shouldBe 6
        }
    }

    /** Expressive Variation: one card into the hand, one to the bottom, one exiled and playable this turn. */
    @Test
    fun expressiveVariation() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand("Expressive Variation")
        alice.library("Storm Spark", "Swamp", "Forest", "Plains")

        alice.casts("Expressive Variation")
        allPass()
        alice.chooses("Storm Spark")
        alice.chooses("Swamp")

        expect {
            alice.hand.names shouldBe listOf("Storm Spark")
            alice.library.names shouldBe listOf("Plains", "Swamp")
            alice.exile has "Forest"
        }
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    /** Echo of Ages: each player shuffles hand and graveyard into the library and draws seven; flashback {2}{U}. */
    @Test
    fun echoOfAgesAndItsFlashback() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand("Echo of Ages", "Storm Spark")
        alice.graveyard("Forest")
        alice.library(*Array(12) { "Swamp" })
        bob.library(*Array(14) { "Mountain" })
        bob.graveyard("Plains")

        alice.casts("Echo of Ages")
        allPass()
        expect {
            alice.hand.size shouldBe 7
            bob.hand.size shouldBe 7
            alice.graveyard.names shouldBe listOf("Echo of Ages")
            bob.library.size shouldBe 8
        }

        // CR 702.34a: from the graveyard for {2}{U}, then exiled
        alice.casts("Echo of Ages")
        allPass()
        expect {
            alice.hand.size shouldBe 7
            alice.exile has "Echo of Ages"
            bob.library.size shouldBe 8
        }
    }

    /** Time Whorl: exiles itself; each player wheels into seven; you untap up to six lands. */
    @Test
    fun timeWhorl() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand("Time Whorl")
        alice.library(*Array(7) { "Swamp" })
        bob.library(*Array(7) { "Mountain" })

        alice.casts("Time Whorl")
        allPass()
        answer(Answer.Objects((0..5).map { card("Island", it) }))

        expect {
            alice.exile has "Time Whorl"
            alice.hand.size shouldBe 7
            bob.hand.size shouldBe 7
            (0..5).none { card("Island", it).tapped } shouldBe true
        }
    }

    /** Fleeting Recall: flicker your creature (its enters trigger again); rebound casts it again at your next upkeep. */
    @Test
    fun fleetingRecallWithRebound() = scenario {
        alice.battlefield("Plains", "Canopy Visionary")
        alice.hand("Fleeting Recall")
        alice.library("Forest", "Island", "Swamp")
        bob.library("Mountain")

        alice.casts("Fleeting Recall", target = "Canopy Visionary")
        allPass()
        expect {
            alice.battlefield has "Canopy Visionary"
            stack.names shouldBe listOf("ability of Canopy Visionary")
            // CR 702.88a: cast from the hand, it's exiled as it resolves
            alice.exile has "Fleeting Recall"
        }
        allPass()
        expect { alice.hand.names shouldBe listOf("Forest") }

        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { stack.names shouldBe listOf("ability of Fleeting Recall") }
        allPass()
        answer(Answer.YesNo(true))
        expect { stack.names shouldBe listOf("Fleeting Recall") }
        allPass()
        allPass()

        expect {
            alice.hand.names shouldBe listOf("Forest", "Island")
            // Cast from exile this time: it goes to the graveyard
            alice.graveyard has "Fleeting Recall"
        }
    }

    /** Lingering Shades: two 1/1 white Spirit fliers; flashback {1}{B} makes two more. */
    @Test
    fun lingeringShadesAndItsFlashback() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Swamp", "Swamp")
        alice.hand("Lingering Shades")

        alice.casts("Lingering Shades", paying = listOf("Plains", "Plains", "Plains"))
        allPass()
        alice.casts("Lingering Shades")
        allPass()

        expect {
            alice.battlefield.names.count { it == "Spirit Token" } shouldBe 4
            card("Spirit Token").power shouldBe 1
            card("Spirit Token") hasKeyword arcanum.api.Keyword.Flying
            alice.exile has "Lingering Shades"
        }
        assertEquals(listOf(arcanum.api.Color.White), view()[card("Spirit Token", 3)]!!.characteristics!!.colors)
    }
}
