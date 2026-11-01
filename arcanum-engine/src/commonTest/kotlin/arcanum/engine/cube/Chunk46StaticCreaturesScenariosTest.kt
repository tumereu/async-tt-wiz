package arcanum.engine.cube

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Chunk 46 in play: creatures with static abilities: lords, "enters with counters", cost changes, casting limits. */
class Chunk46StaticCreaturesScenariosTest {
    private val plusOne = CounterKind.PowerToughness(1, 1)

    /** Bough-Crowned Oracle: other Elves get +1/+1; casting an Elf spell, you may pay {G} to draw a card. */
    @Test
    fun boughCrownedOracleAnthem() = scenario {
        // CR 613.4c
        alice.battlefield("Bough-Crowned Oracle", "Elvish Seer")
        bob.battlefield("Thicketwood Elves")
        expect {
            "Elvish Seer".power shouldBe 2
            "Bough-Crowned Oracle".power shouldBe 1
            "Thicketwood Elves".power shouldBe 1
        }
    }

    @Test
    fun boughCrownedOracleDrawsOnElfSpells() = scenario {
        alice.battlefield("Bough-Crowned Oracle", "Forest", "Forest")
        alice.hand("Thicketwood Elves")
        alice.library("Island")
        alice.casts("Thicketwood Elves", paying = listOf("Forest"))
        expect { stack.names shouldBe listOf("Thicketwood Elves", "ability of Bough-Crowned Oracle") }
        allPass()
        assertTrue(decision<Decision.May>().cost != null)
        alice.answers(Answer.YesNo(true))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect {
            alice.hand.names shouldBe listOf("Island")
            card("Forest", 1).tapped shouldBe true
        }
    }

    /** Lord of the Aetherforge: as big as your artifact count; other artifact creatures get +1/+1. */
    @Test
    fun lordOfTheAetherforge() = scenario {
        // CR 604.3, 613.4a, 613.4c
        alice.battlefield("Lord of the Aetherforge", "Tinkered Mite", "Sun Band")
        expect {
            "Lord of the Aetherforge".power shouldBe 3
            "Lord of the Aetherforge".toughness shouldBe 3
            "Tinkered Mite".power shouldBe 2
        }
    }

    /** Wyrm's Fury Conduit: casting a noncreature spell, surveil 1; with delirium +2/+2, flying, attacks each combat. */
    @Test
    fun wyrmsFuryConduitSurveils() = scenario {
        alice.battlefield("Wyrm's Fury Conduit", "Mountain")
        alice.hand("Storm Spark")
        alice.library("Forest", "Island")
        alice.casts("Storm Spark", target = "bob")
        allPass()
        alice.chooses("Forest")
        expect {
            alice.graveyard has "Forest"
            alice.library.names shouldBe listOf("Island")
            stack.names shouldBe listOf("Storm Spark")
        }
    }

    // Delirium: land, instant, creature and artifact cards in the graveyard
    @Test
    fun wyrmsFuryConduitDelirium() = scenario {
        alice.battlefield("Wyrm's Fury Conduit")
        alice.graveyard("Forest", "Storm Spark", "Elvish Seer", "Sun Band")
        expect {
            "Wyrm's Fury Conduit".power shouldBe 3
            "Wyrm's Fury Conduit".toughness shouldBe 3
            "Wyrm's Fury Conduit" hasKeyword Keyword.Flying
            "Wyrm's Fury Conduit" hasMechanic { it is Mechanic.MustAttack }
        }
    }

    @Test
    fun wyrmsFuryConduitWithoutDelirium() = scenario {
        alice.battlefield("Wyrm's Fury Conduit")
        alice.graveyard("Forest", "Storm Spark", "Elvish Seer")
        expect {
            "Wyrm's Fury Conduit".power shouldBe 1
            "Wyrm's Fury Conduit" lacksKeyword Keyword.Flying
        }
    }

    /** Elenwyn: your other creatures enter with additional +1/+1 counters equal to her toughness. */
    // CR 614.1c
    @Test
    fun elenwynSpinnerOfDawn() = scenario {
        alice.battlefield("Elenwyn, Spinner of Dawn", "Forest")
        alice.hand("Elvish Seer")
        alice.casts("Elvish Seer")
        allPass()
        expect {
            "Elvish Seer".counters(plusOne) shouldBe 1
            "Elvish Seer".power shouldBe 2
        }
    }

    /** Gleaming Copycat: choose a creature type as it enters; it has the type, and others of it enter with a counter. */
    @Test
    fun gleamingCopycat() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand("Gleaming Copycat", "Elvish Seer")
        alice.casts("Gleaming Copycat")
        allPass()
        decision<Decision.ChooseSubtype>()
        alice.answers(Answer.SubtypeChoice(Subtype("Elf")))
        expect {
            alice.battlefield has "Gleaming Copycat"
            assertTrue(Subtype("Elf") in "Gleaming Copycat".subtypes)
        }
        alice.casts("Elvish Seer")
        allPass()
        expect { "Elvish Seer".counters(plusOne) shouldBe 1 }
    }

    /** Torvanna: another nontoken creature of yours entering bolsters 1. */
    // CR 701.39a
    @Test
    fun torvannaBolsters() = scenario {
        alice.battlefield("Torvanna, Elder-Tree Spirit", "Forest")
        alice.hand("Elvish Seer")
        alice.casts("Elvish Seer")
        allPass()
        expect { stack.names shouldBe listOf("ability of Torvanna, Elder-Tree Spirit") }
        allPass()
        expect {
            "Elvish Seer".counters(plusOne) shouldBe 1
            "Torvanna, Elder-Tree Spirit".counters(plusOne) shouldBe 0
        }
    }

    /** Radiant Acolyte: at the beginning of combat on your turn, a +1/+1 counter on target creature you control. */
    @Test
    fun radiantAcolyte() = scenario {
        alice.battlefield("Radiant Acolyte", "Elvish Seer")
        advanceTo(TurnStep.BeginningOfCombat)
        alice.answers(Answer.Targets(listOf(target("Elvish Seer"))))
        allPass()
        expect { "Elvish Seer".counters(plusOne) shouldBe 1 }
    }

    /** Magnetite Colossus: nonartifact spells cost {1} more. */
    // CR 601.2f
    @Test
    fun magnetiteColossus() = scenario {
        alice.battlefield("Magnetite Colossus", "Mountain", "Mountain", "Mountain")
        alice.hand("Storm Spark", "Sun Band")
        alice.casts("Storm Spark", target = "bob")
        expect {
            "Mountain".tapped shouldBe true
            card("Mountain", 1).tapped shouldBe true
            card("Mountain", 2).tapped shouldBe false
        }
        allPass()
        // An artifact costs as printed
        alice.casts("Sun Band")
        expect { card("Mountain", 2).tapped shouldBe true }
    }

    /** Aetherbound Lawkeeper: a player who cast a nonartifact spell this turn can't cast another. */
    @Test
    fun aetherboundLawkeeper() = scenario {
        alice.battlefield("Aetherbound Lawkeeper", "Mountain", "Mountain", "Plains")
        alice.hand("Storm Spark", "Storm Spark", "Sun Band")
        alice.casts("Storm Spark", target = "bob")
        allPass()
        assertFailsWith<IllegalArgumentException> { alice.casts("Storm Spark", target = "bob") }
        alice.casts("Sun Band")
        expect { stack.names shouldBe listOf("Sun Band") }
    }

    /** Brassel: combat damage to your attacking artifact creatures is prevented; artifact creatures entering draw a
     * card once each turn. */
    // CR 615.1
    @Test
    fun brasselPreventsDamageToAttackingArtifacts() = scenario {
        alice.battlefield("Brassel, Gearwork Sage", "Tinkered Mite")
        bob.battlefield("Elvish Seer")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Tinkered Mite")
        allPass()
        bob.blocks("Elvish Seer" to "Tinkered Mite")
        advanceTo(TurnStep.EndOfCombat)
        expect {
            alice.battlefield has "Tinkered Mite"
            bob.graveyard has "Elvish Seer"
        }
    }

    // CR 603.2h: triggers only once each turn
    @Test
    fun brasselDrawsOnceEachTurn() = scenario {
        alice.battlefield("Brassel, Gearwork Sage")
        alice.hand("Tinkered Mite", "Gearwing Glider")
        alice.library("Island", "Swamp")
        alice.casts("Tinkered Mite")
        allPass()
        allPass()
        expect { alice.hand.names shouldBe listOf("Gearwing Glider", "Island") }
        alice.casts("Gearwing Glider")
        allPass()
        expect {
            stack.isEmpty()
            alice.hand.names shouldBe listOf("Island")
        }
    }

    /** Wish of Aethervale: sacrifice it: the player it dealt combat damage this turn can't cast noncreature spells
     * until your next turn. */
    @Test
    fun wishOfAethervale() = scenario {
        alice.battlefield("Wish of Aethervale")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        expect { "Wish of Aethervale" hasKeyword Keyword.Flying }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Wish of Aethervale")
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 19 }
        alice.activates("Wish of Aethervale", target = "bob")
        allPass()
        expect { alice.graveyard has "Wish of Aethervale" }
        alice.passes()
        assertEquals(bob.id, state.priority.holder)
        assertFailsWith<IllegalArgumentException> { bob.casts("Storm Spark", target = "alice") }
    }

    // Only a player it dealt combat damage this turn can be targeted
    @Test
    fun wishOfAethervaleNeedsCombatDamage() = scenario {
        alice.battlefield("Wish of Aethervale")
        // CR 601.2c, 602.2b: no legal target, so it can't be activated
        assertFailsWith<IllegalArgumentException> { alice.activates("Wish of Aethervale", target = "bob") }
        expect { alice.battlefield has "Wish of Aethervale" }
    }
}
