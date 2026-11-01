package arcanum.engine.cube

import arcanum.api.Color
import arcanum.api.Subtype
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Chunk 3 in play: cycling tri-lands and utility lands (CR 305, 605, 702.29, 702.34). */
class Chunk03UtilityLandsScenariosTest {
    // ---- Tri-lands: three basic types, enters tapped, cycling {3}

    /** The tri-land [land] enters tapped when played (CR 614.1c), has its three types and taps for each of them. */
    private fun triland(land: String, types: List<Subtype>, colors: List<Color>) = scenario {
        alice.battlefield(land, land, land)
        alice.hand(land, pipSpell(colors.joinToString("") { "{${it.symbol}}" }))

        perform(Input.PlayLand(alice.id, card(land, 3)))
        expect {
            card(land, 3).tapped shouldBe true
            card(land, 3).subtypes shouldBe types
        }

        alice.casts(pipSpell(colors.joinToString("") { "{${it.symbol}}" }))
        expect {
            card(land, 0).tapped shouldBe true
            card(land, 1).tapped shouldBe true
            card(land, 2).tapped shouldBe true
        }
    }

    /** Cycling {3} (CR 702.29a): pay {3} and discard it from hand to draw a card. */
    private fun cycles(land: String) = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(land)
        alice.library("Island")

        alice.activates(land)
        expect {
            alice.graveyard has land
            stack.size shouldBe 1
        }
        allPass()

        expect {
            alice.hand has "Island"
            alice.hand.size shouldBe 1
        }
    }

    @Test
    fun hallowedMireTriad() = triland("Hallowed Mire Triad", listOf(Subtype("Plains"), Subtype("Swamp"), Subtype("Forest")), listOf(Color.White, Color.Black, Color.Green))

    @Test
    fun hallowedMireTriadCycling() = cycles("Hallowed Mire Triad")

    @Test
    fun revelersArbor() = triland("Revelers' Arbor", listOf(Subtype("Mountain"), Subtype("Forest"), Subtype("Plains")), listOf(Color.Red, Color.Green, Color.White))

    @Test
    fun revelersArborCycling() = cycles("Revelers' Arbor")

    @Test
    fun stormrootTriad() = triland("Stormroot Triad", listOf(Subtype("Forest"), Subtype("Island"), Subtype("Mountain")), listOf(Color.Green, Color.Blue, Color.Red))

    @Test
    fun stormrootTriadCycling() = cycles("Stormroot Triad")

    @Test
    fun spymastersSpire() = triland("Spymaster's Spire", listOf(Subtype("Plains"), Subtype("Island"), Subtype("Swamp")), listOf(Color.White, Color.Blue, Color.Black))

    @Test
    fun spymastersSpireCycling() = cycles("Spymaster's Spire")

    @Test
    fun skyforgeTriad() = triland("Skyforge Triad", listOf(Subtype("Island"), Subtype("Mountain"), Subtype("Plains")), listOf(Color.Blue, Color.Red, Color.White))

    @Test
    fun skyforgeTriadCycling() = cycles("Skyforge Triad")

    @Test
    fun ashlightTriad() = triland("Ashlight Triad", listOf(Subtype("Mountain"), Subtype("Plains"), Subtype("Swamp")), listOf(Color.Red, Color.White, Color.Black))

    @Test
    fun ashlightTriadCycling() = cycles("Ashlight Triad")

    @Test
    fun gardenersLoft() = triland("Gardener's Loft", listOf(Subtype("Forest"), Subtype("Plains"), Subtype("Island")), listOf(Color.Green, Color.White, Color.Blue))

    @Test
    fun gardenersLoftCycling() = cycles("Gardener's Loft")

    @Test
    fun velvetDen() = triland("Velvet Den", listOf(Subtype("Island"), Subtype("Swamp"), Subtype("Mountain")), listOf(Color.Blue, Color.Black, Color.Red))

    @Test
    fun velvetDenCycling() = cycles("Velvet Den")

    @Test
    fun bogshadeTriad() = triland("Bogshade Triad", listOf(Subtype("Swamp"), Subtype("Forest"), Subtype("Island")), listOf(Color.Black, Color.Green, Color.Blue))

    @Test
    fun bogshadeTriadCycling() = cycles("Bogshade Triad")

    @Test
    fun scalelordsArena() = triland("Scalelord's Arena", listOf(Subtype("Swamp"), Subtype("Mountain"), Subtype("Forest")), listOf(Color.Black, Color.Red, Color.Green))

    @Test
    fun scalelordsArenaCycling() = cycles("Scalelord's Arena")

    // ---- Utility lands

    /** "{T}: Add {G} for each creature you control." Two creatures: {G}{G} for a spell costing that. */
    @Test
    fun mothersBower() = scenario {
        alice.battlefield("Mother's Bower", "Tinkered Mite", "Gearwing Glider")
        bob.battlefield("Elvish Seer")
        alice.hand(pipSpell("{G}{G}"))

        alice.casts(pipSpell("{G}{G}"))

        expect {
            "Mother's Bower".tapped shouldBe true
            stack.names shouldBe listOf(pipSpell("{G}{G}"))
        }
        assertTrue(alice.pool.isEmpty())
    }

    /** "{T}: Add {U} for each artifact you control." */
    @Test
    fun arcanistCollegium() = scenario {
        alice.battlefield("Arcanist Collegium", "Sun Band", "Tinkered Mite", "Verdant Bauble")
        bob.battlefield("Azure Bauble")

        alice.activates("Arcanist Collegium")

        assertEquals(listOf<Color?>(Color.Blue, Color.Blue, Color.Blue), alice.pool.map { it.color })
    }

    /** "{T}: Add {C}{C}{C}. Spend this mana only to cast artifact spells." It pays an artifact creature, not a green
     * creature (CR 106.6). */
    @Test
    fun tinkersAtelier() = scenario {
        alice.battlefield("Tinker's Atelier")
        alice.hand(pipSpell("{3}"))

        alice.casts(pipSpell("{3}"))
        allPass()

        expect { alice.battlefield has pipSpell("{3}") }
    }

    @Test
    fun tinkersAtelierManaCantCastOtherSpells() = scenario {
        // CR 106.6
        alice.battlefield("Tinker's Atelier", "Forest")
        alice.hand("Canopy Visionary")
        alice.activates("Tinker's Atelier")

        perform(Input.Cast(alice.id, card("Canopy Visionary")))

        val pay = decision<Decision.ManaPayment>()
        assertTrue(pay.suggestion.isEmpty())
        assertTrue(pay.options.none { it is arcanum.engine.decision.ManaUse.Pool })
    }

    /** "{T}: Draw two cards, then discard three cards." Not a mana ability: it uses the Stack. */
    @Test
    fun soukOfTheSandKings() = scenario {
        alice.battlefield("Souk of the Sand Kings")
        alice.hand("Storm Spark", "Forest")
        alice.library("Island", "Mountain", "Swamp")

        alice.activates("Souk of the Sand Kings")
        expect { stack.names shouldBe listOf("ability of Souk of the Sand Kings") }
        allPass()
        val discard = decision<Decision.ChooseObjects>()
        assertEquals(3, discard.min)
        alice.chooses("Forest", "Island", "Mountain")

        expect {
            alice.hand.names shouldBe listOf("Storm Spark")
            alice.graveyard.size shouldBe 3
            alice.library.names shouldBe listOf("Swamp")
        }
    }

    /**
     * "This land enters tapped. {T}: Add {C}. Whenever this land or another land you control enters, if you control
     * seven or more lands with different names, create a 2/2 black Zombie creature token."
     */
    @Test
    fun barrowOfTheRestless() = scenario {
        alice.battlefield("Plains", "Island", "Swamp", "Mountain", "Forest", "Ashen Wastes")
        alice.hand("Barrow of the Restless")

        alice.plays("Barrow of the Restless")
        expect {
            "Barrow of the Restless".tapped shouldBe true
            // CR 603.4: seven lands with different names
            stack.names shouldBe listOf("ability of Barrow of the Restless")
        }
        allPass()

        expect {
            alice.battlefield has "Zombie Token"
            "Zombie Token".power shouldBe 2
            "Zombie Token".colors shouldBe listOf(Color.Black)
        }
    }

    @Test
    fun barrowOfTheRestlessCountsDifferentNames() = scenario {
        // CR 603.4: six lands, two of them Forests: only six names
        alice.battlefield("Forest", "Island", "Swamp", "Mountain", "Forest", "Barrow of the Restless")
        alice.hand("Plains")

        alice.plays("Plains")

        expect { stack.isEmpty() }
    }

    @Test
    fun barrowOfTheRestlessAnotherLandEnters() = scenario {
        alice.battlefield("Barrow of the Restless", "Island", "Swamp", "Mountain", "Forest", "Ashen Wastes")
        alice.hand("Plains")

        alice.plays("Plains")
        allPass()

        expect { alice.battlefield has "Zombie Token" }
        alice.activates("Barrow of the Restless")
        assertEquals(listOf<Color?>(null), alice.pool.map { it.color })
    }

    /**
     * "{T}: Add {G}." and Channel — "{1}{G}, Discard this card: Destroy target artifact, enchantment, or nonbasic land
     * an opponent controls. That player may search their library for a land card with a basic land type, put it onto
     * the battlefield, then shuffle. This ability costs {1} less to activate for each legendary creature you control."
     */
    @Test
    fun okuraiWhoAbides() = scenario {
        alice.battlefield("Forest", "Tavros, Thicket Emissary")
        alice.hand("Okurai, Who Abides")
        bob.battlefield("Gore Vault")
        bob.library("Blackwater Mire", "Okurai, Who Abides")

        // CR 601.2f: one legendary creature: {G} instead of {1}{G}
        alice.activates("Okurai, Who Abides", ability = 1, target = "Gore Vault")
        expect {
            alice.graveyard has "Okurai, Who Abides"
            "Forest".tapped shouldBe true
            "Tavros, Thicket Emissary".tapped shouldBe false
        }
        allPass()
        bob.answers(Answer.YesNo(true))
        assertEquals(listOf(card("Blackwater Mire")), decision<Decision.ChooseObjects>().options)
        bob.chooses("Blackwater Mire")

        expect {
            bob.graveyard has "Gore Vault"
            bob.battlefield has "Blackwater Mire"
            bob.library.size shouldBe 1
        }
    }

    @Test
    fun okuraiWhoAbidesForMana() = scenario {
        alice.battlefield("Okurai, Who Abides")
        alice.activates("Okurai, Who Abides")
        assertEquals(listOf<Color?>(Color.Green), alice.pool.map { it.color })
    }
}
