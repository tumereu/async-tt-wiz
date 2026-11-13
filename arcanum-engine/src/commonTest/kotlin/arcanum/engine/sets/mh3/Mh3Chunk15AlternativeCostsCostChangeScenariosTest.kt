package arcanum.engine.sets.mh3

import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.rtr.ownAlternative
import kotlin.test.Test
import kotlin.test.assertFails

/**
 * The MH3 cards of chunk 15, played: sacrificing a nontoken creature of the card's color instead of paying its cost,
 * and the five colored cost reducers (invented names carry the oracle text of the Flares and the Medallions).
 */
class Mh3Chunk15AlternativeCostsCostChangeScenariosTest {
    private val denial = Cards.invent("Flare of Dismissal", "{1}{U}{U}", "Instant", """
        You may sacrifice a nontoken blue creature rather than pay this spell's mana cost.
        Counter target spell.
    """)
    private val duplication = Cards.invent("Flare of Repetition", "{1}{R}{R}", "Instant", """
        You may sacrifice a nontoken red creature rather than pay this spell's mana cost.
        Copy target instant or sorcery spell. You may choose new targets for the copy.
    """)
    private val malice = Cards.invent("Flare of Decay", "{2}{B}{B}", "Instant", """
        You may sacrifice a nontoken black creature rather than pay this spell's mana cost.
        Each opponent sacrifices a creature or planeswalker with the greatest mana value among creatures and planeswalkers they control.
    """)
    private val spark = Cards.invent("Ashen Spark", "{R}", "Instant", "This spell deals 2 damage to target player.")
    private val tidal = Cards.invent("Tideborn Imp", "{1}{U}", "Creature — Imp", "", "1/1")
    private val ember = Cards.invent("Emberhide Imp", "{1}{R}", "Creature — Imp", "", "1/1")
    private val shade = Cards.invent("Gravecloak Imp", "{1}{B}", "Creature — Imp", "", "1/1")
    private val goat = Cards.invent("Gravecloak Goat", "{B}", "Creature — Goat", "", "1/1")
    private val brute = Cards.invent("Gravecloak Brute", "{2}{B}", "Creature — Ogre", "", "3/3")
    private val twin = Cards.invent("Gravecloak Twin", "{1}{B}", "Creature — Ogre", "", "2/2")
    private val twin2 = Cards.invent("Gravecloak Twin Brother", "{1}{B}", "Creature — Ogre", "", "2/2")
    private val ward = Cards.invent("Reaping Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    private val verdant = Cards.invent("Verdant Medallion", "{2}", "Artifact", "Green spells you cast cost {1} less to cast.")
    private val onyx = Cards.invent("Onyx Medallion", "{2}", "Artifact", "Black spells you cast cost {1} less to cast.")
    private val ivory = Cards.invent("Ivory Medallion", "{2}", "Artifact", "White spells you cast cost {1} less to cast.")
    private val garnet = Cards.invent("Garnet Medallion", "{2}", "Artifact", "Red spells you cast cost {1} less to cast.")
    private val azure = Cards.invent("Azure Medallion", "{2}", "Artifact", "Blue spells you cast cost {1} less to cast.")
    private val greenSpell = Cards.invent("Medallion Sprout", "{1}{G}", "Creature — Plant", "", "1/1")
    private val blackSpell = Cards.invent("Medallion Wisp", "{1}{B}", "Creature — Spirit", "", "1/1")
    private val whiteSpell = Cards.invent("Medallion Cleric", "{1}{W}", "Creature — Cleric", "", "1/1")
    private val redSpell = Cards.invent("Medallion Spark", "{1}{R}", "Creature — Elemental", "", "1/1")
    private val blueSpell = Cards.invent("Medallion Drake", "{1}{U}", "Creature — Drake", "", "1/1")

    // CR 118.9, 118.3, 701.6a: the sacrifice pays for the spell, so it is cast with no mana, and it counters the target
    @Test
    fun flareOfDenialSacrificesABlueCreatureToCounter() = scenario {
        alice.battlefield(tidal, ward)
        alice.hand(denial)
        bob.battlefield("Mountain")
        bob.hand(spark)
        turn(number = 2, active = bob)
        bob.casts(spark, target = "alice")
        bob.passes()
        alice.casts(denial, way = ownAlternative(denial), target = spark, choosing = listOf(tidal))
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.graveyard has tidal
            alice.graveyard has denial
            bob.graveyard has spark
            alice.battlefield has ward
        }
    }

    // CR 118.9: a creature that isn't blue can't be sacrificed for it
    @Test
    fun flareOfDenialNeedsABlueCreature() = scenario {
        alice.battlefield(ward)
        alice.hand(denial)
        bob.battlefield("Mountain")
        bob.hand(spark)
        turn(number = 2, active = bob)
        bob.casts(spark, target = "alice")
        bob.passes()
        assertFails { alice.casts(denial, way = ownAlternative(denial), target = spark, choosing = listOf(ward)) }
    }

    // CR 118.9, 707.10, 707.10c: the red creature pays; the copy deals damage again to the target chosen for it
    @Test
    fun flareOfDuplicationSacrificesARedCreatureToCopy() = scenario {
        alice.battlefield(ember, "Mountain")
        alice.hand(duplication, spark)
        alice.casts(spark, target = "bob")
        alice.casts(duplication, way = ownAlternative(duplication), target = spark, choosing = listOf(ember))
        resolveAll()
        if (pending != null) {
            // "You may choose new targets": keep the old one
            answer(Answer.YesNo(false))
            resolveAll()
        }
        expect {
            bob.life shouldBe 16
            alice.graveyard has ember
        }
    }

    // CR 118.9, 701.21a, 608.2d, 101.4: with three players each opponent sacrifices from their own greatest mana value
    @Test
    fun flareOfMaliceMakesEachOpponentSacrificeTheirGreatest() = scenario(players = 3) {
        alice.battlefield(shade)
        alice.hand(malice)
        bob.battlefield(goat, brute)
        carol.battlefield(twin, twin2)
        alice.casts(malice, way = ownAlternative(malice), choosing = listOf(shade))
        resolveAll()
        val d = decision<Decision.ChooseObjects>()
        answer(Answer.Objects(listOf(d.options.first())))
        resolveAll()
        expect {
            alice.graveyard has shade
            bob.graveyard has brute
            bob.battlefield has goat
            carol.battlefield.names.size shouldBe 1
            carol.graveyard.names.size shouldBe 1
        }
    }

    /** Two mana's spell of the medallion's color is cast with one land; one of another color can't be paid with one land. */
    private fun reduces(medallion: String, onColor: String, land: String, offColor: String, offLand: String) {
        scenario {
            alice.battlefield(medallion, land)
            alice.hand(onColor)
            alice.casts(onColor)
            resolveAll()
            expect { alice.battlefield has onColor }
        }
        scenario {
            alice.battlefield(medallion, offLand)
            alice.hand(offColor)
            alice.casts(offColor)
            assert(decision<Decision.ManaPayment>().suggestion.isEmpty())
        }
    }

    // CR 601.2f, 118.7: {1} less for green spells you cast (and the same for the other four colors below)
    @Test fun emeraldMedallionReducesGreenSpells() = reduces(verdant, greenSpell, "Forest", blackSpell, "Swamp")
    @Test fun jetMedallionReducesBlackSpells() = reduces(onyx, blackSpell, "Swamp", whiteSpell, "Plains")
    @Test fun pearlMedallionReducesWhiteSpells() = reduces(ivory, whiteSpell, "Plains", redSpell, "Mountain")
    @Test fun rubyMedallionReducesRedSpells() = reduces(garnet, redSpell, "Mountain", blueSpell, "Island")
    @Test fun sapphireMedallionReducesBlueSpells() = reduces(azure, blueSpell, "Island", greenSpell, "Forest")

    // CR 601.2f: it is only for spells you cast
    @Test
    fun medallionDoesNotHelpTheOpponent() = scenario {
        alice.battlefield(verdant)
        bob.battlefield("Forest")
        bob.hand(greenSpell)
        turn(number = 2, active = bob)
        bob.casts(greenSpell)
        assert(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }
}
