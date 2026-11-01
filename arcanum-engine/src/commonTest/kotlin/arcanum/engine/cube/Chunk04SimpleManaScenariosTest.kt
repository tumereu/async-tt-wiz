package arcanum.engine.cube

import arcanum.api.Color
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Chunk 4 in play: rituals, mana rocks and sacrifice outlets that make mana (CR 106, 605). */
class Chunk04SimpleManaScenariosTest {
    // ---- Rituals

    /** "Add {B}{B}{B}." for {B}: the mana pays a spell costing {B}{B}{B} (CR 106.4: it stays until the step ends). */
    @Test
    fun grimOffering() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Grim Offering", pipSpell("{B}{B}{B}"))

        alice.casts("Grim Offering")
        allPass()
        assertEquals(List(3) { Color.Black }, alice.pool.map { it.color })
        alice.casts(pipSpell("{B}{B}{B}"))

        expect {
            alice.graveyard has "Grim Offering"
            stack.names shouldBe listOf(pipSpell("{B}{B}{B}"))
        }
        assertTrue(alice.pool.isEmpty())
    }

    /** "Add {R}{R}{R}{R}{R}." for {2}{R}. */
    @Test
    fun roilingChant() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Roiling Chant")

        alice.casts("Roiling Chant")
        allPass()

        assertEquals(List(5) { Color.Red }, alice.pool.map { it.color })
        expect { alice.graveyard has "Roiling Chant" }
    }

    // ---- Mana rocks

    /** The {0} artifact [bauble] is cast for nothing and taps for [color] the turn it enters (it's no creature). */
    private fun bauble(bauble: String, color: Color) = scenario {
        alice.hand(bauble, pipSpell("{${color.symbol}}"))

        alice.casts(bauble)
        allPass()
        alice.casts(pipSpell("{${color.symbol}}"))

        expect {
            bauble.tapped shouldBe true
            stack.names shouldBe listOf(pipSpell("{${color.symbol}}"))
        }
    }

    /** "{T}: Add {G}." */
    @Test
    fun verdantBauble() = bauble("Verdant Bauble", Color.Green)

    /** "{T}: Add {B}." */
    @Test
    fun onyxBauble() = bauble("Onyx Bauble", Color.Black)

    /** "{T}: Add {W}." */
    @Test
    fun nacreBauble() = bauble("Nacre Bauble", Color.White)

    /** "{T}: Add {R}." */
    @Test
    fun garnetBauble() = bauble("Garnet Bauble", Color.Red)

    /** "{T}: Add {U}." */
    @Test
    fun azureBauble() = bauble("Azure Bauble", Color.Blue)

    /** "{T}: Add {C}{C}." for {1}: cast with a Plains, then it pays a spell costing {2}. */
    @Test
    fun sunBand() = scenario {
        alice.battlefield("Plains")
        alice.hand("Sun Band", pipSpell("{2}"))

        alice.casts("Sun Band")
        allPass()
        alice.casts(pipSpell("{2}"))

        expect {
            "Sun Band".tapped shouldBe true
            stack.names shouldBe listOf(pipSpell("{2}"))
        }
    }

    /** "{T}, Sacrifice this artifact: Add one mana of any color." */
    @Test
    fun lotusFrond() = scenario {
        alice.hand("Lotus Frond", pipSpell("{U}"))

        alice.casts("Lotus Frond")
        allPass()
        alice.casts(pipSpell("{U}"), paying = listOf("Lotus Frond:U"))

        expect {
            alice.graveyard has "Lotus Frond"
            stack.names shouldBe listOf(pipSpell("{U}"))
        }
    }

    /** "{T}, Sacrifice this artifact: Add three mana of any one color." */
    @Test
    fun midnightLotus() = scenario {
        alice.battlefield("Midnight Lotus")
        alice.hand(pipSpell("{G}{G}{G}"))

        alice.casts(pipSpell("{G}{G}{G}"))

        expect {
            alice.graveyard has "Midnight Lotus"
            stack.names shouldBe listOf(pipSpell("{G}{G}{G}"))
        }
        assertTrue(alice.pool.isEmpty())
    }

    /** Metalcraft — "{T}: Add one mana of any color. Activate only if you control three or more artifacts." */
    @Test
    fun opalineTrinket() = scenario {
        alice.battlefield("Opaline Trinket", "Sun Band", "Tinkered Mite")

        alice.activates("Opaline Trinket", color = Color.Red)

        assertEquals(listOf<Color?>(Color.Red), alice.pool.map { it.color })
    }

    @Test
    fun opalineTrinketWithoutMetalcraft() = scenario {
        // CR 602.5b: the activation restriction
        alice.battlefield("Opaline Trinket", "Sun Band")
        alice.hand(pipSpell("{R}"))
        assertFailsWith<IllegalArgumentException> { alice.activates("Opaline Trinket", color = Color.Red) }
    }

    // ---- Sacrifice outlets

    /** "Sacrifice a creature: Add {C}{C}." */
    @Test
    fun renderersAltar() = scenario {
        alice.battlefield("Renderer's Altar", "Festering Gremlin")
        alice.hand(pipSpell("{2}"))

        alice.activates("Renderer's Altar", choosing = listOf("Festering Gremlin"))
        expect {
            alice.graveyard has "Festering Gremlin"
            stack.names shouldBe listOf("ability of Festering Gremlin")
        }
        // CR 702.79a: persist returns it with a -1/-1 counter; CR 106.4: the mana stays while the step lasts
        allPass()
        alice.casts(pipSpell("{2}"))

        expect {
            "Festering Gremlin".counters(arcanum.api.CounterKind.PowerToughness(-1, -1)) shouldBe 1
            stack.names shouldBe listOf(pipSpell("{2}"))
            "Renderer's Altar".tapped shouldBe false
        }
        assertTrue(alice.pool.isEmpty())
    }

    /** "Sacrifice an artifact: Add {C}{C}." It can sacrifice itself. */
    @Test
    fun scrapperClanForge() = scenario {
        alice.battlefield("Scrapper-Clan Forge", "Verdant Bauble")

        alice.activates("Scrapper-Clan Forge", choosing = listOf("Verdant Bauble"))
        alice.activates("Scrapper-Clan Forge", choosing = listOf("Scrapper-Clan Forge"))

        assertEquals(List<Color?>(4) { null }, alice.pool.map { it.color })
        expect {
            alice.graveyard has "Verdant Bauble"
            alice.graveyard has "Scrapper-Clan Forge"
        }
    }

    /** "Sacrifice a creature: Add one mana of any color." */
    @Test
    fun compleatAltar() = scenario {
        alice.battlefield("Compleat Altar", "Tinkered Mite")
        alice.hand(pipSpell("{W}"))

        alice.activates("Compleat Altar", choosing = listOf("Tinkered Mite"), color = Color.White)
        alice.casts(pipSpell("{W}"))

        expect {
            alice.graveyard has "Tinkered Mite"
            stack.names shouldBe listOf(pipSpell("{W}"))
        }
    }

    /** "{T}, Tap an untapped creature you control: Add one mana of any color." A creature that came this turn may be
     * tapped: it isn't its own {T} (CR 302.6). */
    @Test
    fun leafbeatDrum() = scenario {
        alice.battlefield("Leafbeat Drum")
        alice.battlefield("Tinkered Mite", sick = true)

        alice.activates("Leafbeat Drum", choosing = listOf("Tinkered Mite"), color = Color.Green)

        assertEquals(listOf<Color?>(Color.Green), alice.pool.map { it.color })
        expect {
            "Leafbeat Drum".tapped shouldBe true
            "Tinkered Mite".tapped shouldBe true
        }
    }

    /** "{1}, {T}, Sacrifice this artifact: Add one mana of any color. When this artifact is put into a graveyard from
     * the battlefield, draw a card." */
    @Test
    fun prismaticMote() = scenario {
        alice.battlefield("Prismatic Mote", "Plains")
        alice.library("Island")

        alice.activates("Prismatic Mote", color = Color.Black)
        assertEquals(listOf<Color?>(Color.Black), alice.pool.map { it.color })
        expect {
            alice.graveyard has "Prismatic Mote"
            stack.names shouldBe listOf("ability of Prismatic Mote")
        }
        allPass()

        expect { alice.hand has "Island" }
    }

    /** "{S}" (paid with mana from a snow source, CR 107.4h); "When this artifact enters, draw a card."; "{1}, {T}: Add
     * one mana of any color." */
    @Test
    fun rimewatchersOrrery() = scenario {
        alice.battlefield("Rimefrost Druid", "Forest")
        alice.hand("Rimewatcher's Orrery")
        alice.library("Island")

        alice.casts("Rimewatcher's Orrery")
        expect { "Rimefrost Druid".tapped shouldBe true }
        allPass()
        expect { stack.names shouldBe listOf("ability of Rimewatcher's Orrery") }
        allPass()
        expect { alice.hand has "Island" }

        alice.activates("Rimewatcher's Orrery", color = Color.Blue)
        assertEquals(listOf<Color?>(Color.Blue), alice.pool.map { it.color })
        expect { "Forest".tapped shouldBe true }
    }

    @Test
    fun rimewatchersOrreryNeedsSnowMana() = scenario {
        // CR 107.4h: a Forest isn't a snow source
        alice.battlefield("Forest")
        alice.hand("Rimewatcher's Orrery")
        perform(arcanum.engine.input.Input.Cast(alice.id, card("Rimewatcher's Orrery")))
        assertTrue(decision<arcanum.engine.decision.Decision.ManaPayment>().suggestion.isEmpty())
    }
}
