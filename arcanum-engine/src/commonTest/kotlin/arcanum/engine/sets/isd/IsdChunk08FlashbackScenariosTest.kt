package arcanum.engine.sets.isd

import arcanum.api.Keyword
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** ISD chunk 08: flashback cards (CR 702.34a: cast from the graveyard for the flashback cost, then exiled, CR 118.9). Invented names carry
 * the oracle text of the real cards. */
class IsdChunk08FlashbackScenariosTest {
    private val tail = "(You may cast this card from your graveyard for its flashback cost. Then exile it.)"

    private val moan = Cards.invent("Moan of the Restless", "{2}{B}{B}", "Sorcery", "Create two 2/2 black Zombie creature tokens.\nFlashback {5}{B}{B} $tail")
    private val pastInEmbers = Cards.invent("Past in Embers", "{3}{R}", "Sorcery", """
        Each instant and sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost.
        Flashback {4}{R} $tail
    """)
    private val rally = Cards.invent("Rally the Crofters", "{2}{W}", "Instant", "Creatures you control get +2/+0 until end of turn.\nFlashback {2}{R} $tail")
    private val tremor = Cards.invent("Rolling Tremor", "{2}{R}", "Sorcery", "Rolling Tremor deals 2 damage to each creature without flying.\nFlashback {4}{R}{R} $tail")
    private val lineage = Cards.invent("Sever the Lineage", "{3}{B}", "Sorcery", """
        Exile target creature and all other creatures with the same name as that creature.
        Flashback {5}{B}{B} $tail
    """)
    private val exit = Cards.invent("Silent Exit", "{U}", "Sorcery", "Return target creature to its owner's hand.\nFlashback {4}{U} $tail")
    private val spiders = Cards.invent("Weave of Spiders", "{4}{G}", "Sorcery", """
        Create a 1/2 green Spider creature token with reach for each creature card in your graveyard.
        Flashback {6}{B} $tail
    """)
    private val anew = Cards.invent("Think Anew", "{1}{U}", "Instant", "Draw a card.\nFlashback {2}{U} $tail")
    private val stores = Cards.invent("Travel Stores", "{1}{G}", "Sorcery", "Put a +1/+1 counter on each of up to two target creatures.\nFlashback {1}{W} $tail")
    private val ritual = Cards.invent("Unburial Ritual", "{4}{B}", "Sorcery", "Return target creature card from your graveyard to the battlefield.\nFlashback {3}{W} $tail")

    private val murmur = Cards.invent("Murmur Draw", "{1}{U}", "Instant", "Draw a card.")
    private val quietReturn = Cards.invent("Quiet Return", "{U}", "Sorcery", "Return target creature to its owner's hand.")

    private val hound = Cards.invent("Barrowfield Hound", "{1}{G}", "Creature — Dog", "", "2/2")
    private val houndB = Cards.invent("Barrowfield Gull", "{1}{U}", "Creature — Bird", "Flying", "1/3")
    private val tomb = Cards.invent("Barrowfield Tomb", "{2}", "Creature — Dog", "", "3/3")

    private fun Scenario.count(name: String) = state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == name }

    // CR 702.34a, 111.1
    @Test
    fun moanFlashbackMakesTwoZombiesAndIsExiled() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(moan)
        alice.casts(moan)
        resolveAll()
        expect {
            moan.zone shouldBe Zone.Exile
            count("Zombie Token") shouldBe 2
        }
    }

    // CR 702.34a, 611.2c: a sorcery in the graveyard is cast for its mana cost, an instant too; a creature card isn't castable; the
    // spell is exiled after resolving (CR 118.9)
    @Test
    fun pastInEmbersLetsInstantsAndSorceriesBeCastForTheirManaCost() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(pastInEmbers)
        alice.battlefield(houndB)
        alice.graveyard(murmur, quietReturn, hound)
        alice.library("Plains", "Plains")
        alice.casts(pastInEmbers)
        resolveAll()
        alice.casts(murmur)
        resolveAll()
        alice.casts(quietReturn, target = "Barrowfield Gull")
        resolveAll()
        assertFailsWith<IllegalArgumentException> { alice.casts(hound) }
        expect {
            murmur.zone shouldBe Zone.Exile
            quietReturn.zone shouldBe Zone.Exile
            alice.hand.size shouldBe 2
        }
    }

    // CR 702.34a, 611.2a: only the caster's creatures get +2/+0, also with three players
    @Test
    fun rallyFlashbackPumpsOnlyYourCreatures() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", hound)
        bob.battlefield(tomb)
        carol.battlefield(houndB)
        alice.graveyard(rally)
        alice.casts(rally)
        resolveAll()
        expect {
            hound.power shouldBe 4
            tomb.power shouldBe 3
            houndB.power shouldBe 1
            rally.zone shouldBe Zone.Exile
        }
    }

    // CR 120.3, 704.5g: the creature with flying survives, the others die, whoever controls them
    @Test
    fun tremorFlashbackDamagesCreaturesWithoutFlying() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", hound)
        bob.battlefield(houndB)
        carol.battlefield(tomb)
        alice.graveyard(tremor)
        alice.casts(tremor)
        resolveAll()
        expect {
            hound.zone shouldBe Zone.Graveyard
            houndB.zone shouldBe Zone.Battlefield
            tomb.zone shouldBe Zone.Battlefield
            tremor.zone shouldBe Zone.Exile
        }
    }

    // CR 201.2, 608.2b: every creature with the target's name goes, whoever controls it (three players)
    @Test
    fun lineageExilesEveryCreatureWithTheSameName() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", hound, houndB)
        bob.battlefield(hound)
        carol.battlefield(hound, tomb)
        alice.hand(lineage)
        alice.casts(lineage, target = "Barrowfield Hound")
        resolveAll()
        expect {
            alice.battlefield has houndB
            carol.battlefield has tomb
            count("Barrowfield Hound") shouldBe 0
            lineage.zone shouldBe Zone.Graveyard
        }
    }

    // CR 702.34a, 400.3: the creature returns to its owner's hand
    @Test
    fun exitFlashbackReturnsTheCreatureToItsOwnersHand() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        bob.battlefield(tomb)
        alice.graveyard(exit)
        alice.casts(exit, target = "Barrowfield Tomb")
        resolveAll()
        expect {
            bob.hand has tomb
            exit.zone shouldBe Zone.Exile
        }
    }

    // CR 111.1, 702.34a: one Spider per creature card in your graveyard as it resolves (the flashback spell isn't a creature card)
    @Test
    fun spidersFlashbackCountsCreatureCards() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(spiders, hound, tomb, houndB)
        alice.casts(spiders)
        resolveAll()
        expect {
            count("Spider Token") shouldBe 3
            "Spider Token" hasKeyword Keyword.Reach.kind
            spiders.zone shouldBe Zone.Exile
        }
    }

    // CR 121.1, 702.34a
    @Test
    fun anewFlashbackDrawsACardAndIsExiled() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.graveyard(anew)
        alice.library("Plains")
        alice.casts(anew)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            anew.zone shouldBe Zone.Exile
        }
    }

    // CR 115.2, 122.1: each of two targets gets a +1/+1 counter; the flashback cost has another colour
    @Test
    fun storesFlashbackPutsCountersOnTwoCreatures() = scenario {
        alice.battlefield("Plains", "Plains", hound, houndB)
        alice.graveyard(stores)
        alice.casts(stores, targets = listOf("Barrowfield Hound", "Barrowfield Gull"))
        resolveAll()
        expect {
            hound.power shouldBe 3
            houndB.power shouldBe 2
            stores.zone shouldBe Zone.Exile
        }
    }

    // CR 702.34a, 110.1: the creature card comes back to the battlefield under your control
    @Test
    fun ritualFlashbackReturnsACreatureCard() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.graveyard(ritual, tomb)
        alice.casts(ritual, target = "Barrowfield Tomb")
        resolveAll()
        expect {
            alice.battlefield has tomb
            ritual.zone shouldBe Zone.Exile
        }
    }
}
