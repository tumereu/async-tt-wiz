package arcanum.engine.cube

import arcanum.api.Ability
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Cube chunk 10, draw, discard and tutors, played as a player would: cast with their costs, resolved, chosen. */
class Chunk10DrawDiscardAndTutorsScenariosTest {
    private fun Scenario.alternative(name: String): CastWay = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
    }.origin)

    /** Forebear's Insight: target player draws three cards. */
    @Test
    fun forebearsInsight() = scenario {
        alice.battlefield("Island")
        alice.hand("Forebear's Insight")
        alice.library("Forest", "Swamp", "Plains", "Mountain")

        alice.casts("Forebear's Insight", target = "alice")
        allPass()

        expect {
            alice.hand.names shouldBe listOf("Forest", "Swamp", "Plains")
            alice.library.names shouldBe listOf("Mountain")
        }
    }

    /** Midnight Murmur: you draw two cards and lose 2 life. */
    @Test
    fun midnightMurmur() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Midnight Murmur")
        alice.library("Forest", "Island", "Plains")

        alice.casts("Midnight Murmur")
        allPass()

        expect {
            alice.hand.names shouldBe listOf("Forest", "Island")
            alice.life shouldBe 18
        }
    }

    /** Hasty Rummage: draw two, discard two, untap up to three lands. */
    @Test
    fun hastyRummage() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Hasty Rummage", "Forest")
        alice.library("Storm Spark", "Swamp")

        alice.casts("Hasty Rummage")
        allPass()
        alice.chooses("Forest", "Swamp")
        answer(Answer.Objects((0..2).map { card("Island", it) }))

        expect {
            alice.hand.names shouldBe listOf("Storm Spark")
            alice.graveyard.names.toSet() shouldBe setOf("Hasty Rummage", "Forest", "Swamp")
            (0..2).none { card("Island", it).tapped } shouldBe true
        }
    }

    /** Plot the Voyage: draw two, then discard a card when you haven't attacked this turn. */
    @Test
    fun plotTheVoyageWithoutAttacking() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Plot the Voyage")
        alice.library("Forest", "Storm Spark")

        alice.casts("Plot the Voyage")
        allPass()
        alice.chooses("Forest")

        expect {
            alice.hand.names shouldBe listOf("Storm Spark")
            alice.graveyard has "Forest"
        }
    }

    /** Plot the Voyage after attacking: no discard. */
    @Test
    fun plotTheVoyageAfterAttacking() = scenario {
        alice.battlefield("Island", "Island", "Elvish Seer")
        alice.hand("Plot the Voyage")
        alice.library("Forest", "Storm Spark")

        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Elvish Seer")
        advanceTo(TurnStep.PostcombatMain)
        alice.casts("Plot the Voyage")
        allPass()

        expect {
            bob.life shouldBe 19
            alice.hand.names shouldBe listOf("Forest", "Storm Spark")
            alice.graveyard.names shouldBe listOf("Plot the Voyage")
        }
    }

    /** Searing Question: each player draws three, then discards three at random. */
    @Test
    fun searingQuestion() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Searing Question", "Forest")
        alice.library("Island", "Swamp", "Plains")
        bob.hand("Storm Spark")
        bob.library("Mountain", "Mountain", "Mountain")

        alice.casts("Searing Question")
        allPass()

        expect {
            alice.hand.size shouldBe 1
            bob.hand.size shouldBe 1
            alice.graveyard.size shouldBe 4
            bob.graveyard.size shouldBe 3
            alice.library.isEmpty()
        }
    }

    /** Spinning Fate: each player discards their hand, then draws seven. */
    @Test
    fun spinningFate() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Spinning Fate", "Forest", "Storm Spark")
        alice.library(*Array(7) { "Island" })
        bob.hand("Denial")
        bob.library(*Array(8) { "Swamp" })

        alice.casts("Spinning Fate")
        allPass()

        expect {
            alice.graveyard.names.toSet() shouldBe setOf("Spinning Fate", "Forest", "Storm Spark")
            bob.graveyard.names shouldBe listOf("Denial")
            alice.hand.names shouldBe List(7) { "Island" }
            bob.hand.names shouldBe List(7) { "Swamp" }
            bob.library.size shouldBe 1
        }
    }

    /** Hourspinner: each player shuffles hand and graveyard into the library and draws seven; it ends in the graveyard. */
    @Test
    fun hourspinner() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Hourspinner", "Storm Spark")
        alice.graveyard("Forest")
        alice.library(*Array(6) { "Island" })
        bob.hand("Denial")
        bob.library(*Array(7) { "Swamp" })

        alice.casts("Hourspinner")
        allPass()

        expect {
            alice.hand.size shouldBe 7
            alice.library.size shouldBe 1
            bob.hand.size shouldBe 7
            bob.library.size shouldBe 1
            alice.graveyard.names shouldBe listOf("Hourspinner")
        }
    }

    /** Joyful Release: discard two cards as it's cast; draw three. */
    @Test
    fun joyfulRelease() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Joyful Release", "Forest", "Island", "Denial")
        alice.library("Swamp", "Plains", "Storm Spark")

        alice.casts("Joyful Release", choosing = listOf("Forest", "Island"))
        expect { alice.graveyard.names.toSet() shouldBe setOf("Forest", "Island") }
        allPass()

        expect { alice.hand.names.toSet() shouldBe setOf("Denial", "Swamp", "Plains", "Storm Spark") }
    }

    /** Molten Epiphany: discard a land card as it's cast; draw two. */
    @Test
    fun moltenEpiphany() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Molten Epiphany", "Forest", "Denial")
        alice.library("Swamp", "Plains")

        alice.casts("Molten Epiphany", choosing = listOf("Forest"))
        allPass()

        expect {
            alice.graveyard has "Forest"
            alice.hand.names.toSet() shouldBe setOf("Denial", "Swamp", "Plains")
        }
    }

    // CR 601.2h: without a land card to discard, it can't be cast
    @Test
    fun moltenEpiphanyNeedsALand() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Molten Epiphany", "Denial")

        assertFailsWith<IllegalArgumentException> { alice.casts("Molten Epiphany", choosing = listOf("Denial")) }
        expect { alice.hand.size shouldBe 2 }
    }

    /** Insist on Truth: sacrifice an artifact (or discard) as it's cast; draw two. */
    @Test
    fun insistOnTruth() = scenario {
        alice.battlefield("Mountain", "Mountain", "Lotus Frond")
        alice.hand("Insist on Truth", "Forest")
        alice.library("Swamp", "Plains")

        alice.casts("Insist on Truth", option = "Sacrifice", choosing = listOf("Lotus Frond"), paying = listOf("Mountain", "Mountain"))
        allPass()

        expect {
            alice.graveyard has "Lotus Frond"
            alice.hand.names.toSet() shouldBe setOf("Forest", "Swamp", "Plains")
        }
    }

    /** Tidal Rush: return two Islands instead of paying; draw two. */
    @Test
    fun tidalRushForIslands() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Tidal Rush")
        alice.library("Swamp", "Plains")

        alice.casts("Tidal Rush", way = alternative("Tidal Rush"), choosing = listOf("Island", "Island"))
        expect { alice.battlefield.isEmpty() }
        allPass()

        expect { alice.hand.names.toSet() shouldBe setOf("Island", "Swamp", "Plains") }
    }

    /** Fiendish Mentor: search for any card, put it into your hand, shuffle. */
    @Test
    fun fiendishMentor() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Fiendish Mentor")
        alice.library("Forest", "Storm Spark", "Island")

        alice.casts("Fiendish Mentor")
        allPass()
        alice.chooses("Storm Spark")

        expect {
            alice.hand.names shouldBe listOf("Storm Spark")
            alice.library.size shouldBe 2
            log mentions "Searched"
        }
    }

    /** Sinister Purpose: sacrifice a creature as it's cast; search for any card. */
    @Test
    fun sinisterPurpose() = scenario {
        alice.battlefield("Swamp", "Swamp", "Elvish Seer")
        alice.hand("Sinister Purpose")
        alice.library("Forest", "Storm Spark")

        alice.casts("Sinister Purpose", choosing = listOf("Elvish Seer"))
        expect { alice.graveyard has "Elvish Seer" }
        allPass()
        alice.chooses("Storm Spark")

        expect { alice.hand.names shouldBe listOf("Storm Spark") }
    }

    /** Reckless Wager: search for a card into your hand, then discard a card at random. */
    @Test
    fun recklessWager() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Reckless Wager", "Forest")
        alice.library("Storm Spark", "Island")

        alice.casts("Reckless Wager")
        allPass()
        alice.chooses("Storm Spark")

        expect {
            alice.hand.size shouldBe 1
            alice.graveyard.size shouldBe 2
            alice.library.size shouldBe 1
        }
    }

    /** Bloodsucker's Lesson: search, shuffle, put the card on top; lose 2 life. */
    @Test
    fun bloodsuckersLesson() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Bloodsucker's Lesson")
        alice.library("Forest", "Island", "Storm Spark")

        alice.casts("Bloodsucker's Lesson")
        allPass()
        alice.chooses("Storm Spark")

        expect {
            alice.library.names.first() shouldBe "Storm Spark"
            alice.library.size shouldBe 3
            alice.life shouldBe 18
        }
    }

    /** Illumined Mentor: an artifact or enchantment card on top of the library. */
    @Test
    fun illuminedMentor() = scenario {
        alice.battlefield("Plains")
        alice.hand("Illumined Mentor")
        alice.library("Forest", "Lotus Frond", "Storm Spark")

        alice.casts("Illumined Mentor")
        allPass()
        // CR 701.23a: only the artifact matches
        assertEquals(listOf(card("Lotus Frond")), decision<Decision.ChooseObjects>().options)
        alice.chooses("Lotus Frond")

        expect { alice.library.names.first() shouldBe "Lotus Frond" }
    }

    /** Arcane Mentor: an instant or sorcery card on top of the library. */
    @Test
    fun arcaneMentor() = scenario {
        alice.battlefield("Island")
        alice.hand("Arcane Mentor")
        alice.library("Forest", "Cinder Bolt", "Storm Spark", "Lotus Frond")

        alice.casts("Arcane Mentor")
        allPass()
        assertEquals(setOf(card("Cinder Bolt"), card("Storm Spark")), decision<Decision.ChooseObjects>().options.toSet())
        alice.chooses("Cinder Bolt")

        expect {
            alice.library.names.first() shouldBe "Cinder Bolt"
            alice.library.size shouldBe 4
        }
    }

    /** Field Exchange: sacrifice a land as it's cast; search for a land onto the battlefield. */
    @Test
    fun fieldExchange() = scenario {
        alice.battlefield("Forest", "Swamp")
        alice.hand("Field Exchange")
        alice.library("Storm Spark", "Island")

        alice.casts("Field Exchange", choosing = listOf("Swamp"))
        expect { alice.graveyard has "Swamp" }
        allPass()
        alice.chooses("Island")

        expect {
            alice.battlefield has "Island"
            "Island".tapped shouldBe false
            alice.library.size shouldBe 1
        }
    }

    /** Tinkerer's Swap: sacrifice an artifact as it's cast; search for an artifact onto the battlefield. */
    @Test
    fun tinkerersSwap() = scenario {
        alice.battlefield("Island", "Island", "Island", "Lotus Frond")
        alice.hand("Tinkerer's Swap")
        alice.library("Storm Spark", "Onyx Bauble", "Verdant Bauble")

        alice.casts("Tinkerer's Swap", choosing = listOf("Lotus Frond"), paying = listOf("Island", "Island", "Island"))
        allPass()
        alice.chooses("Onyx Bauble")

        expect {
            alice.graveyard has "Lotus Frond"
            alice.battlefield has "Onyx Bauble"
            alice.library.size shouldBe 2
        }
    }
}
