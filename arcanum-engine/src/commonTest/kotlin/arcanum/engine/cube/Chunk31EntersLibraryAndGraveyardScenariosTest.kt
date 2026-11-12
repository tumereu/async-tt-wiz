package arcanum.engine.cube

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/** The cube's chunk 31 (enters: library and graveyard) played in the engine. */
class Chunk31EntersLibraryAndGraveyardScenariosTest {
    /** Casts [name], resolves it and its enters trigger (all players pass twice). */
    private fun Scenario.castAndResolve(name: String) {
        alice.casts(name)
        allPass()
        expect { stack.names shouldBe listOf("ability of $name") }
        allPass()
    }

    /** Enters: mill three; {3}{G}, sacrifice it: return all land cards from your graveyard to the battlefield tapped. */
    @Test
    fun wreckageAnalyst() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand("Wreckage Analyst")
        alice.library("Island", "Storm Spark", "Swamp", "Plains")

        castAndResolve("Wreckage Analyst")
        expect {
            alice.graveyard.names.toSet() shouldBe setOf("Island", "Storm Spark", "Swamp")
            alice.library.names shouldBe listOf("Plains")
        }
        alice.activates("Wreckage Analyst")
        allPass()

        expect {
            alice.graveyard.names.toSet() shouldBe setOf("Storm Spark", "Wreckage Analyst")
            "Island".zone shouldBe Zone.Battlefield
            "Island".tapped shouldBe true
            "Swamp".tapped shouldBe true
        }
    }

    /** Enters or dies: mill three. */
    @Test
    fun stitchworkPorter() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Stitchwork Porter", "Storm Spark")
        alice.library("Island", "Island", "Island", "Swamp", "Swamp", "Swamp", "Plains")

        castAndResolve("Stitchwork Porter")
        expect { alice.library.size shouldBe 4 }
        alice.castsWithoutCosts("Storm Spark", target = "Stitchwork Porter")
        resolve()
        expect { stack.names shouldBe listOf("ability of Stitchwork Porter") }
        resolve()

        expect {
            alice.library.names shouldBe listOf("Plains")
            alice.graveyard.size shouldBe 8
        }
    }

    /** Enters: mill three; dies: you may exile it, and when you do, return a creature card from your graveyard to hand. */
    @Test
    fun undyingValet() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Undying Valet", "Storm Spark")
        alice.library("Elvish Seer", "Umbral Elves", "Island", "Swamp")

        castAndResolve("Undying Valet")
        expect { alice.graveyard has "Elvish Seer" }
        alice.castsWithoutCosts("Storm Spark", target = "Undying Valet")
        resolve()
        resolve()
        answer(Answer.YesNo(true))
        // CR 603.12: the reflexive trigger chooses its target as it goes on the Stack
        answer(Answer.Targets(listOf(target("Elvish Seer"))))
        resolve()

        expect {
            alice.exile has "Undying Valet"
            alice.hand.names shouldBe listOf("Elvish Seer")
            alice.graveyard has "Umbral Elves"
        }
    }

    /** Affinity; enters: mill four; {T}: you may cast an artifact card from your graveyard this turn. */
    @Test
    fun ondraLurkerOfTheMere() = scenario {
        alice.battlefield("Island", "Tinkered Mite", "Tinkered Mite")
        alice.hand("Ondra, Lurker of the Mere")
        alice.library("Cookie Brawler", "Island", "Island", "Island", "Swamp")

        // CR 702.41a: {2}{U} costs {U} with two artifacts
        castAndResolve("Ondra, Lurker of the Mere")
        expect {
            alice.graveyard.size shouldBe 4
            alice.library.names shouldBe listOf("Swamp")
        }
    }

    /** {T}: the chosen artifact card may be cast from the graveyard this turn. */
    @Test
    fun ondraLetsAnArtifactBeCast() = scenario {
        alice.battlefield("Island", "Ondra, Lurker of the Mere")
        alice.graveyard("Cookie Brawler")

        alice.activates("Ondra, Lurker of the Mere", target = "Cookie Brawler")
        allPass()
        alice.casts("Cookie Brawler")
        allPass()

        expect {
            alice.battlefield has "Cookie Brawler"
            "Ondra, Lurker of the Mere".tapped shouldBe true
        }
    }

    /** Enters: reveal the top card: a land goes onto the battlefield, anything else into the hand. */
    @Test
    fun twiningAugur() = scenario {
        alice.battlefield("Forest", "Island")
        alice.hand("Twining Augur")
        alice.library("Swamp", "Storm Spark")

        castAndResolve("Twining Augur")

        expect {
            alice.battlefield has "Swamp"
            alice.library.names shouldBe listOf("Storm Spark")
        }
    }

    /** A nonland card goes into the hand. */
    @Test
    fun twiningAugurNonland() = scenario {
        alice.battlefield("Forest", "Island")
        alice.hand("Twining Augur")
        alice.library("Storm Spark", "Swamp")

        castAndResolve("Twining Augur")

        expect { alice.hand.names shouldBe listOf("Storm Spark") }
    }

    /** Enters: look at four, you may take an artifact card; artifacts entering grow it once each turn. */
    @Test
    fun cleverTinkersmith() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand("Clever Tinkersmith")
        alice.library("Island", "Tinkered Mite", "Swamp", "Forest", "Plains")

        castAndResolve("Clever Tinkersmith")
        alice.chooses("Tinkered Mite")
        expect {
            alice.hand.names shouldBe listOf("Tinkered Mite")
            alice.library.names.first() shouldBe "Plains"
            alice.library.size shouldBe 4
        }
        alice.casts("Tinkered Mite")
        allPass()
        expect { stack.names shouldBe listOf("ability of Clever Tinkersmith") }
        allPass()

        expect { "Clever Tinkersmith".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    /** CR 603.2i: "This ability triggers only once each turn". */
    @Test
    fun cleverTinkersmithTriggersOnceEachTurn() = scenario {
        alice.battlefield("Clever Tinkersmith")
        alice.hand("Tinkered Mite", "Tinkered Mite")

        alice.casts("Tinkered Mite")
        allPass()
        allPass()
        alice.castsWithoutCosts("Tinkered Mite")
        resolve()

        expect {
            alice.battlefield.names.count { it == "Tinkered Mite" } shouldBe 2
            stack.isEmpty()
            "Clever Tinkersmith".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    /** Enters: reveal four, you may put a land into your hand, the rest into the graveyard. */
    @Test
    fun satyrPathseeker() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand("Satyr Pathseeker")
        alice.library("Storm Spark", "Island", "Elvish Seer", "Swamp", "Plains")

        castAndResolve("Satyr Pathseeker")
        alice.chooses("Island")

        expect {
            alice.hand.names shouldBe listOf("Island")
            alice.graveyard.names.toSet() shouldBe setOf("Storm Spark", "Elvish Seer", "Swamp")
            alice.library.names shouldBe listOf("Plains")
        }
    }

    /** Enters: look at the top X (devotion to blue), keep up to one on top, the rest on the bottom. */
    @Test
    fun oracleOfTheDeepTide() = scenario {
        alice.battlefield("Island", "Island", "Faerie Augur")
        alice.hand("Oracle of the Deep Tide")
        alice.library("Forest", "Swamp", "Plains", "Mountain", "Island")

        // CR 700.5: devotion to blue: {U}{U} and Faerie Augur's {U}
        castAndResolve("Oracle of the Deep Tide")
        alice.chooses("Plains")

        expect {
            alice.library.names.first() shouldBe "Plains"
            alice.library.names[1] shouldBe "Mountain"
            alice.library.size shouldBe 5
        }
    }

    /** X at least the library's size: you win the game. */
    @Test
    fun oracleOfTheDeepTideWins() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Oracle of the Deep Tide")
        alice.library("Forest")

        castAndResolve("Oracle of the Deep Tide")
        alice.chooses("Forest")

        assertTrue(state.player(bob.id).lost)
    }

    /** Hideaway 4, enters tapped; leaving the battlefield puts the hidden card into its owner's hand. */
    @Test
    fun seerOfMorrows() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Seer of Morrows", "Storm Spark")
        alice.library("Forest", "Storm Spark", "Swamp", "Plains", "Mountain")

        castAndResolve("Seer of Morrows")
        answer(Answer.Objects(listOf(card("Storm Spark", 1))))
        expect {
            "Seer of Morrows".tapped shouldBe true
            alice.exile.size shouldBe 1
            alice.library.names.first() shouldBe "Mountain"
            alice.library.size shouldBe 4
        }
        alice.castsWithoutCosts("Storm Spark", target = "Seer of Morrows")
        resolve()
        resolve()

        expect {
            alice.exile.isEmpty()
            alice.hand.names shouldBe listOf("Storm Spark")
        }
    }

    /** Enters: you may return target card from your graveyard to your hand. */
    @Test
    fun undyingChronicler() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.graveyard("Storm Spark")
        alice.hand("Undying Chronicler")

        castAndResolve("Undying Chronicler")
        answer(Answer.YesNo(true))

        expect { alice.hand.names shouldBe listOf("Storm Spark") }
    }

    /** Flash; enters: an instant or sorcery in your graveyard gains flashback for its mana cost until end of turn. */
    @Test
    fun echoflashAdept() = scenario {
        alice.battlefield("Island", "Island", "Mountain")
        alice.graveyard("Storm Spark")
        alice.hand("Echoflash Adept")

        castAndResolve("Echoflash Adept")
        alice.casts("Storm Spark", target = "bob")
        allPass()

        expect {
            bob.life shouldBe 17
            alice.exile has "Storm Spark"
            "Mountain".tapped shouldBe true
        }
    }

    /** Reach; enters: you may put a land card from your hand onto the battlefield tapped. */
    @Test
    fun canopyBrowser() = scenario {
        alice.battlefield("Forest")
        alice.hand("Canopy Browser", "Island", "Storm Spark")

        castAndResolve("Canopy Browser")
        alice.chooses("Island")

        expect {
            "Island".zone shouldBe Zone.Battlefield
            "Island".tapped shouldBe true
            "Canopy Browser" hasKeyword Keyword.Reach
        }
    }

    /** Flash, reach; enters: up to one target player puts their graveyard on the bottom of their library. */
    @Test
    fun persistence() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand("Persistence")
        bob.graveyard("Island", "Storm Spark")
        bob.library("Swamp")

        alice.casts("Persistence")
        allPass()
        answer(Answer.Targets(listOf(target("bob"))))
        allPass()

        expect {
            bob.graveyard.isEmpty()
            bob.library.names.first() shouldBe "Swamp"
            bob.library.size shouldBe 3
        }
    }

    /** Evoke by exiling a green card from your hand: it's sacrificed as it enters, its trigger still happens. */
    @Test
    fun persistenceEvoked() = scenario {
        alice.hand("Persistence", "Elvish Seer")
        bob.graveyard("Island")

        alice.casts("Persistence", way = CastWay(alternative = view()[card("Persistence")]!!.characteristics!!.abilities.first {
            (it.ability as? arcanum.api.Ability.Static)?.mechanics?.any { m -> m is arcanum.api.Mechanic.AlternativeCost } == true
        }.origin), choosing = listOf("Elvish Seer"))
        expect { alice.exile has "Elvish Seer" }
        allPass()
        // CR 702.74a: two triggers: the sacrifice and the enters trigger
        answer(Answer.Order(listOf(0, 1)))
        answer(Answer.Targets(listOf(target("bob"))))
        allPass()
        allPass()

        expect {
            alice.graveyard has "Persistence"
            bob.graveyard.isEmpty()
        }
    }

    /** Enters: you may search for an artifact card and put it into your graveyard; {R}, {T}, sacrifice an artifact:
     * return an artifact card with mana value 3 or less from your graveyard to the battlefield. */
    @Test
    fun goblinSalvager() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Tinkered Mite")
        alice.hand("Goblin Salvager")
        alice.library("Island", "Cookie Brawler", "Swamp")

        castAndResolve("Goblin Salvager")
        answer(Answer.YesNo(true))
        alice.chooses("Cookie Brawler")
        expect { alice.graveyard has "Cookie Brawler" }
    }

    /** The activated ability: sacrifice an artifact, return one from the graveyard. */
    @Test
    fun goblinSalvagerReturnsAnArtifact() = scenario {
        alice.battlefield("Mountain", "Goblin Salvager", "Tinkered Mite")
        alice.graveyard("Cookie Brawler")

        alice.activates("Goblin Salvager", target = "Cookie Brawler", choosing = listOf("Tinkered Mite"))
        allPass()

        expect {
            alice.battlefield has "Cookie Brawler"
            alice.graveyard has "Tinkered Mite"
            "Goblin Salvager".tapped shouldBe true
        }
    }
}
