package arcanum.engine.review

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Players
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Review 1: continuous effects, layers, timestamps and dependencies (CR 611, 613). */
class Review1LayersTest {
    private fun set(p: Int, t: Int) = Mechanic.SetPowerToughness(Value.Fixed(p), Value.Fixed(t))
    private fun modify(p: Int, t: Int) = Mechanic.ModifyPowerToughness(Value.Fixed(p), Value.Fixed(t))

    /** Setting base P/T (7b) keeps counters (7c): X=3 with a +1/+1 counter is 4/4. */
    // CR 613.4b, 613.4c
    @Test
    fun basePowerAndToughnessKeepsCounters() = scenario {
        alice.battlefield("Glass Polymorph", "Mountain", "Mountain", "Mountain")
        alice.battlefield("Thicketwood Elves", counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.activates("Glass Polymorph", x = 3)
        allPass()
        expect {
            "Thicketwood Elves".power shouldBe 4
            "Thicketwood Elves".toughness shouldBe 4
            "Glass Polymorph".power shouldBe 3
        }
    }

    /** A resolved "creatures you control have base P/T X/X" locks its set: a creature arriving later isn't affected. */
    // CR 611.2c
    @Test
    fun aLaterCreatureIsntInTheLockedSet() = scenario {
        alice.battlefield("Glass Polymorph", "Mountain", "Mountain", "Forest")
        alice.hand("Elvish Seer")
        alice.activates("Glass Polymorph", x = 3, paying = listOf("Mountain", "Mountain#1", "Forest"))
        allPass()
        move("Elvish Seer", Zone.Battlefield)
        expect { "Elvish Seer".power shouldBe 1 }
    }

    /** "Each Elf has base P/T 5/5" (7b) under an Elf anthem (7c): the other Elves are 6/6, the anthem's own source 5/5. */
    // CR 613.4b, 613.4c
    @Test
    fun setThenAnthem() = scenario {
        alice.battlefield("Saurian Herder", "Elvish Hierophant", "Elvish Seer")
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates("Saurian Herder")
        allPass()
        expect {
            "Elvish Seer".power shouldBe 6
            "Elvish Hierophant".power shouldBe 5
            "Saurian Herder".power shouldBe 6
            assertTrue(Subtype("Dinosaur") in "Elvish Seer".subtypes)
        }
    }

    /** A characteristic-defining P/T (7a) is overridden by a set effect (7b). */
    // CR 613.4a, 613.4b
    @Test
    fun setOverridesACharacteristicDefiningAbility() = scenario {
        alice.battlefield("Lord of the Aetherforge", "Sun Band", "Verdant Bauble")
        effect(bob, set(1, 1), on = listOf("Lord of the Aetherforge"))
        expect { "Lord of the Aetherforge".power shouldBe 1 }
    }

    /** Losing all abilities removes a characteristic-defining P/T: a star/star creature is then its printed 0/0. */
    // CR 613.4a, 604.3, 613.1f
    @Test
    fun losingAllAbilitiesRemovesTheCharacteristicDefiningAbility() = scenario {
        alice.battlefield("Lord of the Aetherforge", "Sun Band", "Verdant Bauble")
        effect(bob, Mechanic.LoseAbilities(), on = listOf("Lord of the Aetherforge"))
        expect { "Lord of the Aetherforge".power shouldBe 0 }
    }

    /** A changeling is an Elf for "Other Elf creatures you control get +1/+1". */
    // CR 702.73a, 613.4c
    @Test
    fun changelingGetsTheElfAnthem() = scenario {
        alice.battlefield("Elvish Hierophant", "Shapeless Exile")
        expect { "Shapeless Exile".power shouldBe 2 }
    }

    /** An Elf anthem whose source stops being a creature keeps working (it's a static ability of a permanent), and the
     * source loses its creature types. */
    // CR 613.1d, 205.3d, 301.7
    @Test
    fun anthemOfAPermanentThatStoppedBeingACreature() = scenario {
        alice.battlefield("Elvish Hierophant", "Elvish Seer", "Plains")
        alice.hand("Rapid Retrofit")
        alice.casts("Rapid Retrofit", target = "Elvish Hierophant")
        allPass()
        expect {
            assertFalse(CardType.Creature in "Elvish Hierophant".types)
            assertTrue(CardType.Artifact in "Elvish Hierophant".types)
            assertTrue(Subtype("Vehicle") in "Elvish Hierophant".subtypes)
            assertFalse(Subtype("Elf") in "Elvish Hierophant".subtypes)
            "Elvish Hierophant".power shouldBe null
            "Elvish Seer".power shouldBe 2
        }
    }

    /** Switching (7d) comes after counters (7c): a 1/3 with a +1/+1 counter switched is 4/2. */
    // CR 613.4c, 613.4d
    @Test
    fun switchAfterCounters() = scenario {
        alice.battlefield("Brakka, Wild Whelp", counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        effect(alice, set(1, 3), on = listOf("Brakka, Wild Whelp"))
        effect(alice, Mechanic.SwitchPowerToughness, on = listOf("Brakka, Wild Whelp"))
        expect {
            "Brakka, Wild Whelp".power shouldBe 4
            "Brakka, Wild Whelp".toughness shouldBe 2
        }
    }

    /** Gaining control doesn't end an "until end of turn" pump on the creature (the effect locked its object). */
    // CR 611.2c, 613.1b
    @Test
    fun pumpStaysThroughAControlChange() = scenario {
        alice.battlefield("Thicketwood Elves")
        effect(alice, modify(2, 2), on = listOf("Thicketwood Elves"))
        effect(bob, Mechanic.ControlledBy(Players.You), on = listOf("Thicketwood Elves"))
        expect {
            "Thicketwood Elves".controller shouldBe bob.id
            "Thicketwood Elves".power shouldBe 3
        }
    }

    /** A copy effect copies the copiable values only: not the original's pump, but the copy keeps its own counters. */
    // CR 707.2, 613.2
    @Test
    fun copyTakesPrintedValuesAndKeepsItsOwnCounters() = scenario {
        alice.battlefield("Orla, Gilded Artificer", counters = listOf(CounterKind.Loyalty to 5))
        alice.battlefield("Sun Band", counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.battlefield("Gearwing Glider")
        effect(alice, modify(3, 3), on = listOf("Gearwing Glider"))
        alice.activates("Orla, Gilded Artificer", targets = listOf("Sun Band", "Gearwing Glider"))
        allPass()
        expect {
            "Sun Band".power shouldBe 1
            "Sun Band".toughness shouldBe 3
            "Sun Band" hasKeyword Keyword.Flying
            assertTrue(CardType.Artifact in "Sun Band".types)
        }
    }

    /** Devotion counts coloured and hybrid symbols of permanents you control: Pyrrhus (RR) and a {B/R}{B/R} creature is
     * 4. */
    // CR 700.5
    @Test
    fun devotionCountsHybridSymbols() = scenario {
        alice.battlefield("Pyrrhus, Tempered in Ash", "Bloodthirsty Redcap")
        expect { "Pyrrhus, Tempered in Ash".power shouldBe 4 }
    }

    /** A creature that lost all abilities in an earlier timestamp then gains flying still has flying (layer 6 in
     * timestamp order). */
    // CR 613.7
    @Test
    fun gainAfterLoseByTimestamp() = scenario {
        alice.battlefield("Thicketwood Elves")
        effect(bob, Mechanic.LoseAbilities(), on = listOf("Thicketwood Elves"))
        effect(alice, Mechanic.GainAbilities(listOf(Keyword.Flying)), on = listOf("Thicketwood Elves"))
        expect { "Thicketwood Elves" hasKeyword Keyword.Flying }
    }

    /** Losing all abilities later removes a flying gained earlier. */
    // CR 613.7
    @Test
    fun loseAfterGainByTimestamp() = scenario {
        alice.battlefield("Thicketwood Elves")
        effect(alice, Mechanic.GainAbilities(listOf(Keyword.Flying)), on = listOf("Thicketwood Elves"))
        effect(bob, Mechanic.LoseAbilities(), on = listOf("Thicketwood Elves"))
        expect { "Thicketwood Elves" lacksKeyword Keyword.Flying }
    }

    /** Lord of the Aetherforge counts artifacts its controller controls, so after a control change it counts the new
     * controller's. */
    // CR 604.3, 613.1b
    @Test
    fun characteristicDefiningAbilityFollowsTheController() = scenario {
        alice.battlefield("Lord of the Aetherforge", "Sun Band", "Verdant Bauble")
        bob.battlefield("Onyx Bauble")
        effect(bob, Mechanic.ControlledBy(Players.You), on = listOf("Lord of the Aetherforge"))
        expect { "Lord of the Aetherforge".power shouldBe 2 }
    }

    /** "+1/+1 for each land card in your graveyard" (a 7c effect with a value) follows the graveyard as it changes. */
    // CR 611.3a, 613.4c
    @Test
    fun staticValueIsWorkedOutContinuously() = scenario {
        alice.battlefield("Knight of the Relic Vault")
        alice.graveyard("Forest", "Island")
        expect { "Knight of the Relic Vault".power shouldBe 4 }
    }

    /** Level counters set P/T through the level abilities: Canopy Whisperer at level 5 is 1/4. */
    // CR 711.2, 613.4b
    @Test
    fun levelerSetsPowerAndToughness() = scenario {
        alice.battlefield("Canopy Whisperer", counters = listOf(CounterKind.Level to 5))
        expect {
            "Canopy Whisperer".power shouldBe 1
            "Canopy Whisperer".toughness shouldBe 4
        }
    }

    /** Glass Polymorph gives "all creature types": the creatures become Elves too, so an Elf anthem pumps them. */
    // CR 613.1d, 613.8a
    @Test
    fun allCreatureTypesFeedsAnAnthem() = scenario {
        alice.battlefield("Glass Polymorph", "Bough-Crowned Oracle", "Wall of Portents", "Plains", "Plains")
        alice.activates("Glass Polymorph", x = 2)
        allPass()
        expect {
            "Wall of Portents".power shouldBe 3
            "Wall of Portents".toughness shouldBe 3
        }
    }

    /** A spell's "until end of turn" pump ends in the cleanup step. */
    // CR 514.2
    @Test
    fun untilEndOfTurnEndsInCleanup() = scenario {
        alice.battlefield("Scorching Mudlizard", "Mountain")
        alice.activates("Scorching Mudlizard")
        allPass()
        expect { "Scorching Mudlizard".power shouldBe 3 }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { "Scorching Mudlizard".power shouldBe 1 }
    }
}
