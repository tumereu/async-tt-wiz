package arcanum.engine.sets.snc

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Streets of New Capenna chunk 11 in play: lands and creatures that enter tapped, shield counters (damage and destruction
 * each remove one instead), a trample granted only while a shield counter is there, a turn-bound power bonus, the Angel counter
 * replacement with three players, and a chosen permanent whose activated abilities are shut off and copied. */
class SncChunk11ReplacementScenariosTest {
    private val shield = CounterKind.Shield
    private val plaza = Cards.invent("Snc11 Verdant Plaza", null, "Land", """
        This land enters tapped.
        {T}: Add {G} or {W}.
        {2}{G}{W}, {T}, Sacrifice this land: Draw a card.
    """)
    private val janitor = Cards.invent("Snc11 Crooked Janitor", "{1}{B}", "Creature — Ogre Rogue", "This creature enters tapped.", "3/2")
    private val shieldmate = Cards.invent("Snc11 Dapper Shieldbearer", "{3}{W}", "Creature — Human Soldier", """
        This creature enters with a shield counter on it. (If it would be dealt damage or destroyed, remove a shield counter from it instead.)
        During your turn, this creature gets +2/+0.
    """, "2/2")
    private val rhox = Cards.invent("Snc11 Rhox Thumper", "{5}{G}", "Creature — Rhino Soldier", """
        This creature enters with a shield counter on it. (If it would be dealt damage or destroyed, remove a shield counter from it instead.)
        This creature has trample as long as it has a shield counter on it.
    """, "6/3")
    private val giada = Cards.invent("Snc11 Gianna, Spring of Hope", "{1}{W}", "Legendary Creature — Angel", """
        Flying, vigilance
        Each other Angel you control enters with an additional +1/+1 counter on it for each Angel you already control.
        {T}: Add {W}. Spend this mana only to cast an Angel spell.
    """, "2/2")
    private val angel = Cards.invent("Snc11 Lesser Angel", "{W}", "Creature — Angel", "Flying", "1/1")
    private val fence = Cards.invent("Snc11 Crafty Pawnbroker", "{W}{U}", "Creature — Human Citizen", """
        As this creature enters, you may choose a nonland permanent.
        Activated abilities of the chosen permanent can't be activated.
        This creature has all activated abilities of the chosen permanent except for loyalty abilities. You may spend mana as though it were mana of any color to activate those abilities.
    """, "2/3")
    private val engine = Cards.invent("Snc11 Card Engine", "{2}", "Artifact", "{1}: Draw a card.")
    private val angelOfBob = Cards.invent("Snc11 Rival Angel", "{W}", "Creature — Angel", "Flying", "1/1")
    private val angelTwo = Cards.invent("Snc11 Newer Angel", "{W}", "Creature — Angel", "Flying", "1/1")
    private val doom = Cards.invent("Snc11 Quiet Doom", "{B}", "Instant", "Destroy target creature.")
    private val jolt = Cards.invent("Snc11 Alley Jolt", "{R}", "Instant", "Snc11 Alley Jolt deals 3 damage to any target.")

    // CR 614.1c, 305.1: it enters tapped
    @Test
    fun landEntersTapped() = scenario {
        alice.hand(plaza)
        alice.plays(plaza)
        expect { card(plaza).tapped shouldBe true }
    }

    // CR 602.2, 701.21 (sacrifice as a cost), 121.1
    @Test
    fun landSacrificesItselfToDraw() = scenario {
        alice.battlefield(plaza, "Forest", "Plains", "Island", "Island")
        alice.library("Island", "Swamp")
        alice.activates(plaza, ability = 1)
        resolveAll()
        expect {
            alice.graveyard has plaza
            alice.hand has "Island"
        }
    }

    // CR 614.1c
    @Test
    fun creatureEntersTapped() = scenario {
        alice.hand(janitor)
        alice.battlefield("Swamp", "Swamp")
        alice.casts(janitor)
        resolveAll()
        expect { card(janitor).tapped shouldBe true }
    }

    // CR 122.1, 614.1c: cast, it enters with a shield counter; +2/+0 on its controller's turn only
    @Test
    fun entersWithShieldAndPumpedOnYourTurn() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(shieldmate)
        alice.casts(shieldmate)
        resolveAll()
        expect {
            card(shieldmate).counters(shield) shouldBe 1
            card(shieldmate).power shouldBe 4
        }
    }

    // CR 122.1 (shield counter): the first damage is replaced by removing the counter; the next hit kills
    @Test
    fun shieldPreventsOneDamageEventThenTheNextKills() = scenario {
        alice.battlefield("Mountain", "Mountain", shieldmate, counters = listOf(shield to 1))
        alice.hand(jolt, jolt)
        alice.casts(jolt, target = shieldmate)
        resolveAll()
        expect {
            alice.battlefield has shieldmate
            card(shieldmate).counters(shield) shouldBe 0
        }
        alice.casts(jolt, target = shieldmate)
        resolveAll()
        expect { alice.graveyard has shieldmate }
    }

    // CR 122.1c: destruction by an effect removes a shield counter instead; the second one destroys
    @Test
    fun shieldReplacesDestructionOnce() = scenario {
        alice.battlefield("Swamp", "Swamp", shieldmate, counters = listOf(shield to 1))
        alice.hand(doom, doom)
        alice.casts(doom, target = shieldmate)
        resolveAll()
        expect {
            alice.battlefield has shieldmate
            card(shieldmate).counters(shield) shouldBe 0
        }
        alice.casts(doom, target = shieldmate)
        resolveAll()
        expect { alice.graveyard has shieldmate }
    }

    // CR 613.1f, 122.1: trample only while a shield counter is on it
    @Test
    fun trampleLastsWhileShielded() = scenario {
        alice.battlefield("Mountain", rhox, counters = listOf(shield to 1))
        alice.hand(jolt)
        expect { rhox hasKeyword Keyword.Trample.kind }
        alice.casts(jolt, target = rhox)
        resolveAll()
        expect { rhox lacksKeyword Keyword.Trample.kind }
    }

    // CR 614.1c, 614.12: with three players, only Angels of the controller count; the new Angel is not counted itself
    @Test
    fun otherAngelsEnterWithACounterPerAngelAlreadyControlled() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", giada, angel)
        bob.battlefield(angelOfBob)
        alice.hand(angelTwo)
        alice.casts(angelTwo)
        resolveAll()
        expect {
            card(angelTwo).counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            card(giada).counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 113.6 (chosen as it enters), 602.5 (can't be activated), 613.1f: the copy works, the original does not
    @Test
    fun chosenPermanentIsShutOffAndCopied() = scenario {
        alice.battlefield("Plains", "Island", "Island", "Island")
        alice.hand(fence)
        alice.library("Island", "Swamp")
        bob.battlefield(engine, "Island")
        alice.casts(fence)
        resolveAll()
        alice.chooses(engine)
        resolveAll()
        assertFailsWith<Throwable> { bob.activates(engine, paying = listOf("Island")) }
        alice.activates(fence)
        resolveAll()
        expect { alice.hand has "Island" }
    }
}
