package arcanum.engine.cube

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Chunk 55 in play: mana creatures and Treasure/Food makers, with a leveler. */
class Chunk55LeftoverManaCreaturesScenariosTest {
    private val level = CounterKind.Level

    /** Gravebloom Augur: three tap abilities exiling a card from a graveyard: a land for mana of any colour, an instant or
     * sorcery to drain 2, a creature to gain 2. */
    // CR 605.1a: with a target it isn't a mana ability: it uses the Stack
    @Test
    fun gravebloomAugurLandForMana() = scenario {
        alice.battlefield("Gravebloom Augur")
        bob.graveyard("Forest")
        alice.activates("Gravebloom Augur", target = "Forest")
        expect { stack.size shouldBe 1 }
        allPass()
        alice.answers(Answer.ColorChoice(Color.Red))
        expect {
            bob.exile has "Forest"
            assertTrue(alice.pool.single().color == Color.Red)
        }
    }

    @Test
    fun gravebloomAugurDrains() = scenario {
        alice.battlefield("Gravebloom Augur", "Swamp")
        bob.graveyard("Storm Spark")
        alice.activates("Gravebloom Augur", ability = 1, target = "Storm Spark")
        allPass()
        expect {
            bob.exile has "Storm Spark"
            bob.life shouldBe 18
        }
    }

    @Test
    fun gravebloomAugurGainsLife() = scenario {
        alice.battlefield("Gravebloom Augur", "Forest")
        bob.graveyard("Elvish Seer")
        alice.activates("Gravebloom Augur", ability = 2, target = "Elvish Seer")
        allPass()
        expect {
            bob.exile has "Elvish Seer"
            alice.life shouldBe 22
        }
    }

    /** Gilt-Feather Gander: flying; a Food as it enters; {1}{G}, {T}: a Food; {T}, sacrifice a Food: one mana of any
     * colour. */
    @Test
    fun giltFeatherGanderEntersWithFood() = scenario {
        alice.battlefield("Forest")
        alice.hand("Gilt-Feather Gander")
        alice.casts("Gilt-Feather Gander")
        allPass()
        allPass()
        expect {
            "Gilt-Feather Gander" hasKeyword Keyword.Flying
            card("Food Token").types shouldBe listOf(arcanum.api.CardType.Artifact)
        }
    }

    @Test
    fun giltFeatherGanderMakesFoodAndMana() = scenario {
        alice.battlefield("Gilt-Feather Gander", "Forest", "Forest")
        alice.activates("Gilt-Feather Gander")
        allPass()
        expect { alice.battlefield.names.count { it == "Food Token" } shouldBe 1 }
        // Untapped again for the mana ability (CR 605.3a: no Stack)
        change(arcanum.engine.change.Changes.tap(state, card("Gilt-Feather Gander"), tapped = false))
        alice.activates("Gilt-Feather Gander", ability = 1, color = Color.Blue)
        if (pending is Decision.ChooseColor) alice.answers(Answer.ColorChoice(Color.Blue))
        expect {
            stack.isEmpty()
            alice.battlefield.names.count { it == "Food Token" } shouldBe 0
            assertTrue(alice.pool.single().color == Color.Blue)
        }
    }

    // The Food: {2}, {T}, sacrifice it: gain 3 life
    @Test
    fun giltFeatherGanderFood() = scenario {
        alice.battlefield("Forest")
        alice.battlefield("Plains", "Plains")
        alice.hand("Gilt-Feather Gander")
        alice.casts("Gilt-Feather Gander")
        allPass()
        allPass()
        alice.activates("Food Token")
        allPass()
        expect {
            alice.life shouldBe 23
            alice.battlefield lacks "Food Token"
        }
    }

    /** Canopy Whisperer: level up {1}{G}; level 1-4: 1/2 with "{T}: Add {G}{G}"; level 5+: 1/4 and Elves you control
     * have that ability. */
    // CR 711.2, 711.6
    @Test
    fun canopyWhispererLevelsUp() = scenario {
        alice.battlefield("Canopy Whisperer", "Forest", "Forest")
        expect {
            "Canopy Whisperer".toughness shouldBe 1
            "Canopy Whisperer".view.characteristics!!.abilities.count { it.ability is arcanum.api.Ability.Activated } shouldBe 1
        }
        alice.activates("Canopy Whisperer")
        allPass()
        expect {
            "Canopy Whisperer".counters(level) shouldBe 1
            "Canopy Whisperer".power shouldBe 1
            "Canopy Whisperer".toughness shouldBe 2
        }
        alice.activates("Canopy Whisperer", ability = 1)
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun canopyWhispererLevelFive() = scenario {
        alice.battlefield("Canopy Whisperer", counters = listOf(level to 5))
        alice.battlefield("Elvish Seer")
        expect { "Canopy Whisperer".toughness shouldBe 4 }
        // Elvish Seer's own {T}: Add {G}, then the granted {T}: Add {G}{G}
        alice.activates("Elvish Seer", ability = 1)
        expect { alice.pool.size shouldBe 2 }
    }

    // CR 711.2: level up only as a sorcery
    @Test
    fun canopyWhispererLevelUpAtSorcerySpeed() = scenario {
        alice.battlefield("Canopy Whisperer", "Forest", "Forest")
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "bob", paying = listOf("Mountain"))
        assertFailsWith<IllegalArgumentException> { alice.activates("Canopy Whisperer") }
    }

    /** Remorseless Corsair: another creature of yours dying makes a Treasure. */
    @Test
    fun remorselessCorsair() = scenario {
        alice.battlefield("Remorseless Corsair", "Corpse Gnawer", "Elvish Seer")
        alice.activates("Corpse Gnawer", choosing = listOf("Elvish Seer"))
        allPass()
        expect { card("Treasure Token").types shouldBe listOf(arcanum.api.CardType.Artifact) }
        // The Treasure: {T}, sacrifice it: one mana of any colour
        alice.activates("Treasure Token", color = Color.White)
        if (pending is Decision.ChooseColor) alice.answers(Answer.ColorChoice(Color.White))
        expect {
            assertTrue(alice.pool.single().color == Color.White)
            alice.battlefield lacks "Treasure Token"
        }
    }

    /** Shuffling Wight: when it dies, choose one: target creature an opponent controls gets -1/-1, or a Treasure. */
    // CR 700.2
    @Test
    fun shufflingWightShrinks() = scenario {
        alice.battlefield("Shuffling Wight", "Corpse Gnawer")
        bob.battlefield("Elvish Seer")
        alice.activates("Corpse Gnawer", choosing = listOf("Shuffling Wight"))
        alice.answers(Answer.Modes(listOf(0)))
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Elvish Seer"))))
        allPass()
        expect { bob.graveyard has "Elvish Seer" }
    }

    @Test
    fun shufflingWightTreasure() = scenario {
        alice.battlefield("Shuffling Wight", "Corpse Gnawer")
        alice.activates("Corpse Gnawer", choosing = listOf("Shuffling Wight"))
        alice.answers(Answer.Modes(listOf(1)))
        allPass()
        expect { alice.battlefield has "Treasure Token" }
    }

    /** Burrow Soulbroker: pay 1 life and sacrifice another creature: a Treasure. */
    @Test
    fun burrowSoulbroker() = scenario {
        alice.battlefield("Burrow Soulbroker", "Elvish Seer")
        alice.activates("Burrow Soulbroker", choosing = listOf("Elvish Seer"))
        allPass()
        expect {
            alice.life shouldBe 19
            alice.graveyard has "Elvish Seer"
            alice.battlefield has "Treasure Token"
        }
    }

    /** Tamsin: entering, a 0/0 Construct that grows with your artifacts; tap an artifact: {U}; {5}: shuffle, exile the
     * top card and play it free this turn. */
    @Test
    fun tamsinMakesAConstruct() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Sun Band")
        alice.hand("Tamsin, Grand Mechanist")
        alice.casts("Tamsin, Grand Mechanist")
        allPass()
        allPass()
        expect { card("Construct Token").power shouldBe 2 }
    }

    @Test
    fun tamsinTapsArtifactsForBlue() = scenario {
        alice.battlefield("Tamsin, Grand Mechanist", "Sun Band")
        alice.activates("Tamsin, Grand Mechanist", choosing = listOf("Sun Band"))
        expect {
            "Sun Band".tapped shouldBe true
            assertTrue(alice.pool.single().color == Color.Blue)
        }
    }

    @Test
    fun tamsinPlaysTheTopCardFree() = scenario {
        alice.battlefield("Tamsin, Grand Mechanist", "Island", "Island", "Island", "Island", "Island")
        alice.library("Storm Spark")
        alice.activates("Tamsin, Grand Mechanist", ability = 1)
        allPass()
        expect { alice.exile has "Storm Spark" }
        alice.casts("Storm Spark", target = "bob")
        allPass()
        expect { bob.life shouldBe 17 }
    }
}
