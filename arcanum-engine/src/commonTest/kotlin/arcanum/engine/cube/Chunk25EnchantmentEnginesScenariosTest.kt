package arcanum.engine.cube

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The cube's chunk 25 (enchantment engines) played in the engine. */
class Chunk25EnchantmentEnginesScenariosTest {
    /** Sacrifice a creature: 1 damage to any target. */
    @Test
    fun goblinVolleyPit() = scenario {
        alice.battlefield("Mountain", "Mountain", "Elvish Seer")
        alice.hand("Goblin Volley Pit")

        alice.casts("Goblin Volley Pit")
        allPass()
        alice.activates("Goblin Volley Pit", target = "bob", choosing = listOf("Elvish Seer"))
        expect { alice.graveyard has "Elvish Seer" }
        allPass()

        expect {
            bob.life shouldBe 19
            alice.battlefield has "Goblin Volley Pit"
        }
    }

    /** {1}, sacrifice an artifact or creature: 1 damage to any target. */
    @Test
    fun scrapheapOrdnance() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Tinkered Mite")
        bob.battlefield("Umbral Elves")
        alice.hand("Scrapheap Ordnance")

        alice.casts("Scrapheap Ordnance")
        allPass()
        alice.activates("Scrapheap Ordnance", target = "Umbral Elves", choosing = listOf("Tinkered Mite"))
        allPass()

        expect {
            alice.graveyard has "Tinkered Mite"
            bob.graveyard has "Umbral Elves"
            card("Mountain", 2).tapped shouldBe true
        }
    }

    /** {R}, discard a land card: 2 damage to any target. */
    @Test
    fun magmaWhirlpool() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Magma Whirlpool", "Forest")

        alice.casts("Magma Whirlpool")
        allPass()
        alice.activates("Magma Whirlpool", target = "bob", choosing = listOf("Forest"))
        allPass()

        expect {
            bob.life shouldBe 18
            alice.graveyard has "Forest"
            alice.hand.isEmpty()
        }
    }

    /** Discarding a nonland card doesn't pay "discard a land card". */
    @Test
    fun magmaWhirlpoolNeedsALandCard() = scenario {
        alice.battlefield("Mountain", "Magma Whirlpool")
        alice.hand("Storm Spark")

        assertFailsWith<IllegalArgumentException> {
            alice.activates("Magma Whirlpool", target = "bob", choosing = listOf("Storm Spark"))
        }
    }

    /** Discard two cards: a 2/2 black Zombie token. */
    @Test
    fun ghoulOutbreak() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Ghoul Outbreak", "Forest", "Storm Spark")

        alice.casts("Ghoul Outbreak")
        allPass()
        alice.activates("Ghoul Outbreak", choosing = listOf("Forest", "Storm Spark"))
        allPass()

        expect {
            alice.graveyard.names.toSet() shouldBe setOf("Forest", "Storm Spark")
            alice.battlefield has "Zombie Token"
            card("Zombie Token").power shouldBe 2
            card("Zombie Token").toughness shouldBe 2
            card("Zombie Token").colors shouldBe listOf(Color.Black)
            card("Zombie Token").types shouldBe listOf(CardType.Creature)
        }
    }

    /** {G}, discard a creature card: search for a creature card, reveal it, put it into hand, shuffle. */
    @Test
    fun lawOfTheWilds() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand("Law of the Wilds", "Umbral Elves")
        alice.library("Forest", "Elvish Seer", "Island")

        alice.casts("Law of the Wilds")
        allPass()
        alice.activates("Law of the Wilds", choosing = listOf("Umbral Elves"))
        allPass()
        alice.chooses("Elvish Seer")

        expect {
            alice.graveyard has "Umbral Elves"
            alice.hand.names shouldBe listOf("Elvish Seer")
            alice.library.size shouldBe 2
        }
    }

    /** Sacrifice a land: one mana of any type that land could produce (a mana ability, CR 605.1a). */
    @Test
    fun wastedBounty() = scenario {
        alice.battlefield("Swamp", "Forest", "Island")
        alice.hand("Wasted Bounty")

        alice.casts("Wasted Bounty", paying = listOf("Swamp", "Forest"))
        allPass()
        alice.activates("Wasted Bounty", choosing = listOf("Island"))

        expect {
            alice.graveyard has "Island"
            stack.isEmpty()
        }
        assertEquals(listOf(Color.Blue), alice.pool.map { it.color })
    }

    /** Tap an untapped creature you control: untap target basic land. */
    @Test
    fun soilbinding() = scenario {
        alice.battlefield("Forest", "Forest", "Elvish Seer")
        alice.hand("Soilbinding")

        alice.casts("Soilbinding")
        allPass()
        alice.activates("Soilbinding", target = "Forest", choosing = listOf("Elvish Seer"))
        allPass()

        expect {
            "Elvish Seer".tapped shouldBe true
            "Forest".tapped shouldBe false
            card("Forest", 1).tapped shouldBe true
        }
    }

    /** One additional land each turn (CR 305.2). */
    @Test
    fun frontierWandering() = scenario {
        alice.battlefield("Forest")
        alice.hand("Frontier Wandering", "Island", "Swamp", "Mountain")

        alice.casts("Frontier Wandering")
        allPass()
        alice.plays("Island")
        alice.plays("Swamp")

        expect { alice.battlefield.names.toSet() shouldBe setOf("Forest", "Frontier Wandering", "Island", "Swamp") }
        // CR 305.2: two land plays this turn, both used
        assertTrue(Engine.options(state, alice.id).none { it is Input.PlayLand })
        assertFailsWith<IllegalArgumentException> { alice.plays("Mountain") }
    }

    /** Any number of lands; every land after the first this turn deals 1 damage to you. */
    @Test
    fun rootrush() = scenario {
        alice.battlefield("Forest")
        alice.hand("Rootrush", "Island", "Swamp", "Mountain")

        alice.casts("Rootrush")
        allPass()
        alice.plays("Island")
        expect { alice.life shouldBe 20 }
        alice.plays("Swamp")
        allPass()
        alice.plays("Mountain")
        allPass()

        expect {
            alice.battlefield has "Mountain"
            alice.life shouldBe 18
        }
    }

    /** Any player may cast creature spells with mana value 3 or less free and as though they had flash. */
    @Test
    fun verdantHollowmere() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand("Verdant Hollowmere")
        bob.hand("Elvish Seerling", "Thunderhoof Colossus")
        bob.library("Island")

        alice.casts("Verdant Hollowmere")
        allPass()
        // Bob, on Alice's turn, with an empty Stack: as though it had flash, without paying its mana cost
        alice.passes()
        val free = Engine.options(state, bob.id).filterIsInstance<Input.Cast>().filter { it.card == card("Elvish Seerling") }
        assertTrue(free.isNotEmpty(), "Bob may cast Elvish Seerling now")
        perform(free.first { it.way.permission != null || it.way.alternative != null })
        expect { stack.names shouldBe listOf("Elvish Seerling") }
        // CR 601.2g: no mana to pay; the Colossus (mana value 8) has no such permission
        assertTrue(Engine.options(state, bob.id).filterIsInstance<Input.Cast>().none { it.card == card("Thunderhoof Colossus") })
        allPass()
        allPass()

        expect {
            bob.battlefield has "Elvish Seerling"
            bob.hand.names shouldBe listOf("Thunderhoof Colossus", "Island")
        }
    }

    /** Creatures don't untap during untap steps; whenever a creature enters, untap all creatures. */
    @Test
    fun sentryKlaxon() = scenario {
        alice.battlefield("Island", "Island", "Island", "Forest", "Forest")
        alice.battlefield("Elvish Seer", tapped = true)
        bob.battlefield("Umbral Elves", tapped = true)
        alice.hand("Sentry Klaxon", "Tinkered Mite")
        alice.library("Island", "Island", "Island")
        bob.library("Swamp", "Swamp", "Swamp")

        alice.casts("Sentry Klaxon")
        allPass()
        // CR 502.3: Bob's untap step leaves his creature tapped, but not his lands
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { "Umbral Elves".tapped shouldBe true }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        expect {
            "Elvish Seer".tapped shouldBe true
            "Island".tapped shouldBe false
        }
        alice.casts("Tinkered Mite")
        allPass()
        expect { stack.names shouldBe listOf("ability of Sentry Klaxon") }
        allPass()

        expect {
            "Elvish Seer".tapped shouldBe false
            "Umbral Elves".tapped shouldBe false
        }
    }

    /** Hand revealed; draws reveal the top card: a creature goes to the graveyard, else it's drawn; creatures put into
     * your graveyard from the battlefield return to your hand. */
    @Test
    fun endlessReturn() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Mountain", "Elvish Seer")
        alice.hand("Endless Return", "Storm Spark", "Glance Ahead")
        alice.library("Umbral Elves", "Island", "Swamp")

        alice.casts("Endless Return")
        allPass()
        // CR 614.6: the draw is replaced: the creature card on top goes to the graveyard
        alice.castsWithoutCosts("Glance Ahead")
        resolve()
        alice.chooses()
        expect {
            alice.graveyard has "Umbral Elves"
            alice.hand lacks "Umbral Elves"
            alice.library.names shouldBe listOf("Island", "Swamp")
        }
        // A creature put into the graveyard from the battlefield comes back to the hand
        alice.casts("Storm Spark", target = "Elvish Seer")
        allPass()
        allPass()

        expect {
            alice.hand has "Elvish Seer"
            alice.graveyard lacks "Elvish Seer"
        }
    }

    /** A noncreature card on top is drawn as usual. */
    @Test
    fun endlessReturnDrawsANoncreatureCard() = scenario {
        alice.battlefield("Endless Return")
        alice.hand("Glance Ahead")
        alice.library("Island", "Swamp")

        alice.castsWithoutCosts("Glance Ahead")
        resolve()
        alice.chooses()

        expect { alice.hand.names shouldBe listOf("Island") }
    }

    /** Flash; the enchanted creature becomes a Vehicle artifact with crew 5 and loses its other card types. */
    @Test
    fun rapidRetrofit() = scenario {
        alice.battlefield("Plains")
        bob.battlefield("Umbral Elves")
        alice.hand("Rapid Retrofit")
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain, priority = alice)

        alice.casts("Rapid Retrofit", target = "Umbral Elves")
        allPass()

        expect {
            "Rapid Retrofit".zone shouldBe Zone.Battlefield
            // CR 205.1b: the new types replace the old ones
            "Umbral Elves".types shouldBe listOf(CardType.Artifact)
            "Umbral Elves".subtypes shouldBe listOf(Subtype("Vehicle"))
            "Umbral Elves" hasKeyword Keyword.Crew.Kind
        }
    }
}
