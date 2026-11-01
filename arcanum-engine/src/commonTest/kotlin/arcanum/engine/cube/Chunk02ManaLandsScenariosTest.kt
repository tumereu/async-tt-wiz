package arcanum.engine.cube

import arcanum.api.Color
import arcanum.api.Subtype
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Chunk 2 in play: fetch lands, dual lands, shock lands and lands with a second ability (CR 305, 605). */
class Chunk02ManaLandsScenariosTest {
    // ---- Fetch lands: "{T}, Pay 1 life, Sacrifice this land: Search your library for an A or B card, put it onto the
    // battlefield, then shuffle."

    /** Activates the fetch land [fetch], finds [found] (one of its types) and leaves [notFound] in the library. */
    private fun fetches(fetch: String, found: String, notFound: String) = scenario {
        alice.battlefield(fetch)
        alice.library(notFound, found)

        alice.activates(fetch)
        expect {
            // CR 602.2, 118.3: the costs are paid as it's activated
            alice.life shouldBe 19
            alice.graveyard has fetch
            stack.names shouldBe listOf("ability of $fetch")
        }
        allPass()
        val search = decision<Decision.ChooseObjects>()
        // CR 701.19a: only cards of the named land types can be found
        assertEquals(listOf(card(found)), search.options)
        alice.chooses(found)

        expect {
            alice.battlefield has found
            card(found).tapped shouldBe false
            alice.library has notFound
            alice.library.size shouldBe 1
            stack.isEmpty()
        }
    }

    /** Pay 1 life and sacrifice it: find a Mountain or Plains card (the dual land Highland Mesa counts as both). */
    @Test
    fun sunbakedMesa() = fetches("Sunbaked Mesa", "Highland Mesa", "Island")

    @Test
    fun sunbakedMesaFindsABasicMountain() = fetches("Sunbaked Mesa", "Mountain", "Forest")

    /** Find a Swamp or Mountain card. */
    @Test
    fun cinderSoakedBog() = fetches("Cinder-Soaked Bog", "Swamp", "Forest")

    /** Find a Plains or Island card. */
    @Test
    fun tidewashedShore() = fetches("Tidewashed Shore", "Glacial Plain", "Swamp")

    /** Find a Plains or Swamp card. */
    @Test
    fun gloomlitFen() = fetches("Gloomlit Fen", "Plains", "Island")

    /** Find a Forest or Island card. */
    @Test
    fun mistveilCanopy() = fetches("Mistveil Canopy", "Island", "Mountain")

    /** Find an Island or Swamp card. */
    @Test
    fun brackishEstuary() = fetches("Brackish Estuary", "Sunken Grotto", "Plains")

    /** Find an Island or Mountain card. */
    @Test
    fun steamingCaldera() = fetches("Steaming Caldera", "Mountain", "Swamp")

    /** Find a Swamp or Forest card. */
    @Test
    fun mossyOssuary() = fetches("Mossy Ossuary", "Forest", "Plains")

    /** Find a Forest or Plains card. */
    @Test
    fun gustingMoorland() = fetches("Gusting Moorland", "Golden Grassland", "Island")

    /** Find a Mountain or Forest card. */
    @Test
    fun timberedRidge() = fetches("Timbered Ridge", "Frostpine Slopes", "Plains")

    /** Find any basic land card; a nonbasic land with basic types isn't one. */
    @Test
    fun refractingOverlook() = fetches("Refracting Overlook", "Island", "Glacial Plain")

    // ---- Dual lands: "Land — A B" with "({T}: Add {a} or {b}.)"

    /**
     * The dual land [land] has both basic land types (CR 305.6) and taps for either colour: two of them pay a spell
     * costing one of each.
     */
    private fun dual(land: String, a: Subtype, b: Subtype, ca: Color, cb: Color) = scenario {
        alice.battlefield(land, land)
        alice.hand(pipSpell("{${ca.symbol}}{${cb.symbol}}"))
        expect { land.subtypes shouldBe listOf(a, b) }

        alice.casts(pipSpell("{${ca.symbol}}{${cb.symbol}}"))

        expect {
            card(land, 0).tapped shouldBe true
            card(land, 1).tapped shouldBe true
            stack.names shouldBe listOf(pipSpell("{${ca.symbol}}{${cb.symbol}}"))
        }
        assertTrue(alice.pool.isEmpty())
    }

    @Test
    fun ashenWastes() = dual("Ashen Wastes", Subtype("Swamp"), Subtype("Mountain"), Color.Black, Color.Red)

    @Test
    fun blackwaterMire() = dual("Blackwater Mire", Subtype("Swamp"), Subtype("Forest"), Color.Black, Color.Green)

    @Test
    fun highlandMesa() = dual("Highland Mesa", Subtype("Mountain"), Subtype("Plains"), Color.Red, Color.White)

    @Test
    fun goldenGrassland() = dual("Golden Grassland", Subtype("Forest"), Subtype("Plains"), Color.Green, Color.White)

    @Test
    fun thornbrushFlats() = dual("Thornbrush Flats", Subtype("Plains"), Subtype("Swamp"), Color.White, Color.Black)

    @Test
    fun frostpineSlopes() = dual("Frostpine Slopes", Subtype("Mountain"), Subtype("Forest"), Color.Red, Color.Green)

    @Test
    fun palmfringeAtoll() = dual("Palmfringe Atoll", Subtype("Forest"), Subtype("Island"), Color.Green, Color.Blue)

    @Test
    fun glacialPlain() = dual("Glacial Plain", Subtype("Plains"), Subtype("Island"), Color.White, Color.Blue)

    @Test
    fun sunkenGrotto() = dual("Sunken Grotto", Subtype("Island"), Subtype("Swamp"), Color.Blue, Color.Black)

    @Test
    fun smolderingIsle() = dual("Smoldering Isle", Subtype("Island"), Subtype("Mountain"), Color.Blue, Color.Red)

    // ---- Shock lands: a dual land that asks for 2 life as it enters

    /**
     * Played paying 2 life, the shock land [land] enters untapped and pays a spell right away; a second one played
     * without paying enters tapped (CR 614.12).
     */
    private fun shock(land: String, color: Color) = scenario {
        alice.hand(land, pipSpell("{${color.symbol}}"))

        // CR 305.1, 614.12: the replacement asks as the land is played
        alice.plays(land)
        val ask = decision<Decision.May>()
        assertTrue(ask.cost != null)
        alice.answers(Answer.YesNo(true))
        expect {
            alice.life shouldBe 18
            land.tapped shouldBe false
        }

        alice.casts(pipSpell("{${color.symbol}}"))
        expect {
            land.tapped shouldBe true
            stack.names shouldBe listOf(pipSpell("{${color.symbol}}"))
        }
    }

    private fun shockDeclined(land: String) = scenario {
        alice.hand(land)
        alice.plays(land)
        alice.answers(Answer.YesNo(false))
        expect {
            alice.life shouldBe 20
            land.tapped shouldBe true
            alice.battlefield has land
        }
    }

    @Test
    fun goreVault() = shock("Gore Vault", Color.Red)

    @Test
    fun goreVaultUnpaid() = shockDeclined("Gore Vault")

    @Test
    fun spawningBasin() = shock("Spawning Basin", Color.Blue)

    @Test
    fun hereticsChapel() = shock("Heretic's Chapel", Color.Black)

    @Test
    fun consecratedSpring() = shock("Consecrated Spring", Color.White)

    @Test
    fun ivyChokedCrypt() = shock("Ivy-Choked Crypt", Color.Green)

    @Test
    fun holyForge() = shock("Holy Forge", Color.White)

    @Test
    fun geyserFissure() = shock("Geyser Fissure", Color.Blue)

    @Test
    fun trampledGlade() = shock("Trampled Glade", Color.Red)

    @Test
    fun cloisterGrove() = shock("Cloister Grove", Color.Green)

    @Test
    fun drownedBarrow() = shock("Drowned Barrow", Color.Black)

    @Test
    fun drownedBarrowUnpaid() = shockDeclined("Drowned Barrow")

    // ---- Other mana lands

    /** "{T}: Add {C}. {T}: Add {R} or {G}. Each opponent gains 1 life." */
    @Test
    fun hearthOfTheEmberwillows() = scenario {
        alice.battlefield("Hearth of the Emberwillows")
        alice.hand(pipSpell("{R}"))

        alice.activates("Hearth of the Emberwillows", ability = 1, color = Color.Red)
        alice.casts(pipSpell("{R}"))

        expect {
            bob.life shouldBe 21
            alice.life shouldBe 20
            stack.names shouldBe listOf(pipSpell("{R}"))
        }
    }

    @Test
    fun hearthOfTheEmberwillowsColourless() = scenario {
        alice.battlefield("Hearth of the Emberwillows")
        alice.hand(pipSpell("{1}"))

        alice.casts(pipSpell("{1}"))

        expect {
            bob.life shouldBe 20
            "Hearth of the Emberwillows".tapped shouldBe true
            stack.names shouldBe listOf(pipSpell("{1}"))
        }
    }

    /** "{T}: Add {C}. {T}, Sacrifice a creature: Add {B}{B}." */
    @Test
    fun oblationSpire() = scenario {
        alice.battlefield("Oblation Spire", "Elvish Seer")
        alice.hand(pipSpell("{B}{B}"))

        alice.activates("Oblation Spire", ability = 1, choosing = listOf("Elvish Seer"))
        expect {
            alice.graveyard has "Elvish Seer"
            "Oblation Spire".tapped shouldBe true
        }
        assertEquals(listOf<Color?>(Color.Black, Color.Black), alice.pool.map { it.color })

        alice.casts(pipSpell("{B}{B}"))
        expect { stack.names shouldBe listOf(pipSpell("{B}{B}")) }
        assertTrue(alice.pool.isEmpty())
    }

    @Test
    fun oblationSpireColourless() = scenario {
        alice.battlefield("Oblation Spire")
        alice.activates("Oblation Spire")
        assertEquals(listOf<Color?>(null), alice.pool.map { it.color })
    }

    /** "{T}: Add {C}. {T}, Sacrifice this land: Destroy target land." */
    @Test
    fun quarryGouge() = scenario {
        alice.battlefield("Quarry Gouge")
        bob.battlefield("Forest")

        alice.activates("Quarry Gouge", ability = 1, target = "Forest")
        allPass()

        expect {
            alice.graveyard has "Quarry Gouge"
            bob.graveyard has "Forest"
        }
    }

    @Test
    fun quarryGougeForMana() = scenario {
        alice.battlefield("Quarry Gouge")
        alice.activates("Quarry Gouge")
        assertEquals(listOf<Color?>(null), alice.pool.map { it.color })
    }

    /** "{T}, Sacrifice this land: Destroy target nonbasic land." A basic land can't be targeted. */
    @Test
    fun barrenExpanse() = scenario {
        alice.battlefield("Barren Expanse")
        bob.battlefield("Forest", "Gore Vault")

        alice.activates("Barren Expanse", ability = 1)
        // CR 115.1: a basic land isn't a legal target
        assertEquals(listOf(target("Barren Expanse"), target("Gore Vault")), decision<Decision.Targets>().options)
        alice.answers(Answer.Targets(listOf(target("Gore Vault"))))
        allPass()

        expect {
            alice.graveyard has "Barren Expanse"
            bob.graveyard has "Gore Vault"
            bob.battlefield has "Forest"
        }
    }

    @Test
    fun barrenExpanseForMana() = scenario {
        alice.battlefield("Barren Expanse")
        alice.activates("Barren Expanse")
        assertEquals(listOf<Color?>(null), alice.pool.map { it.color })
    }
}
