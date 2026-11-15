package arcanum.engine.sets.trk

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Star Trek chunk 16 in play: creatures that can't block, graveyard-counted and attack-conditional cost reductions, a tapper that untaps on noncreature spells, three players. */
class TrkChunk16TokensCostsCountersScenariosTest {
    private val veteran = Cards.invent("Trk16 Veteran Of Old Tales", "{B}", "Creature — Klingon Advisor", """
        This creature can't block.
        You may cast this card from your graveyard. If you do, it enters with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
    """.trimIndent(), "2/1")
    private val fuzzballs = Cards.invent("Trk16 Multiplying Fuzzballs", "{2}{W}", "Creature — Tribble", """
        This creature can't block.
        At the beginning of your upkeep, create a token that's a copy of this creature.
    """.trimIndent(), "0/1")
    private val starfish = Cards.invent("Trk16 Gorging Starfish", "{5}{R}", "Creature — Fish", """
        This spell costs {1} less to cast for each noncreature, nonland card in your graveyard.
        Flying, haste
    """.trimIndent(), "3/3")
    private val klaxon = Cards.invent("Trk16 Battle Stations Klaxon", "{2}{R}", "Instant", """
        This spell costs {2} less to cast if a creature is attacking you.
        Creatures you control get +2/+0 until end of turn.
    """.trimIndent())
    private val analyst = Cards.invent("Trk16 Obsidian Order Analyst", "{2}{R}", "Creature — Romulan Scientist", """
        {T}: This creature deals 1 damage to each opponent.
        Whenever you cast a noncreature spell, untap this creature.
    """.trimIndent(), "3/2")
    private val trinket = Cards.invent("Trk16 Trinket", "{R}", "Artifact", "")
    private val zap = Cards.invent("Trk16 Zap", "{R}", "Instant", "Trk16 Zap deals 3 damage to target creature.")
    private val bolt = Cards.invent("Trk16 Spark", "{R}", "Instant", "Trk16 Spark deals 1 damage to any target.")
    private val drifter = Cards.invent("Trk16 Drifter", "{1}", "Creature — Human", "", "1/1")
    private val ogre = Cards.invent("Trk16 Ogre", "{2}{G}", "Creature — Ogre", "", "3/3")
    private val cub = Cards.invent("Trk16 Cub", "{G}", "Creature — Cat", "", "2/2")
    private val bear = Cards.invent("Trk16 Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    private fun Scenario.toCombat() {
        turn(step = TurnStep.PrecombatMain)
        advanceTo(TurnStep.DeclareAttackers)
    }

    // CR 509.1b: the veteran can't be declared as a blocker, another creature of the same player can
    @Test
    fun veteranCantBlockButOthersCan() = scenario(players = 3) {
        alice.battlefield(ogre)
        bob.battlefield(veteran, cub)
        toCombat()
        alice.attacks(ogre, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(veteran to ogre) }
        bob.blocks(cub to ogre)
    }

    // CR 601.3e / 112.6 (cast from the graveyard), 122.1 (finality counter from the replacement)
    @Test
    fun veteranCastFromGraveyardEntersWithAFinalityCounter() = scenario(players = 3) {
        alice.battlefield("Swamp")
        alice.graveyard(veteran)
        alice.casts(veteran)
        resolveAll()
        expect { veteran.counters(CounterKind.Finality) shouldBe 1 }
    }

    // CR 603.2, 707.2, 509.1b: each upkeep adds a copy (also unable to block); other players' upkeeps add none
    @Test
    fun fuzzballsCopyThemselvesAndNeitherBlocks() = scenario(players = 3) {
        alice.battlefield(fuzzballs)
        bob.battlefield(cub)
        alice.library(bear, bear, bear, bear)
        bob.library(bear, bear, bear, bear)
        carol.library(bear, bear, bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 4)
        resolveAll()
        expect { card("Trk16 Multiplying Fuzzballs", 1).power shouldBe 0 }
        advanceTo(TurnStep.DeclareAttackers, turn = 5)
        bob.attacks(cub, defender = "alice")
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { alice.blocks(fuzzballs to cub) }
    }

    // CR 601.2f: two noncreature, nonland cards (instant, artifact) reduce {5}{R} by {2}; the creature and land cards don't count
    @Test
    fun starfishCostsOneLessPerNoncreatureNonlandCardInGraveyard() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard(zap, trinket, drifter, "Forest")
        alice.hand(starfish)
        alice.casts(starfish)
        resolveAll()
        expect {
            starfish.zone shouldBe arcanum.api.Zone.Battlefield
            card("Mountain", 0).tapped shouldBe true
            card("Mountain", 3).tapped shouldBe true
            card("Mountain", 4).tapped shouldBe false
            card("Mountain", 5).tapped shouldBe false
        }
    }

    // CR 601.2f, 506.2 (attacking you): only the defending player pays {2} less; the pump covers only the caster's creatures (611.2c)
    @Test
    fun klaxonIsCheaperOnlyForThePlayerBeingAttacked() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", drifter)
        carol.battlefield("Mountain", "Mountain", "Mountain", cub)
        bob.battlefield(ogre)
        alice.hand(klaxon)
        turn(active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(ogre, defender = "carol")
        bob.passes()
        carol.passes()
        alice.casts(klaxon)
        resolveAll()
        expect {
            card("Mountain", 0).tapped shouldBe true
            card("Mountain", 1).tapped shouldBe true
            card("Mountain", 2).tapped shouldBe true
            drifter.power shouldBe 3
            cub.power shouldBe 2
            ogre.power shouldBe 3
        }
    }

    @Test
    fun klaxonForTheAttackedPlayerCostsOnlyOneMana() = scenario(players = 3) {
        alice.battlefield(drifter)
        carol.battlefield("Mountain", "Mountain", "Mountain", cub)
        bob.battlefield(ogre)
        carol.hand(klaxon)
        turn(active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(ogre, defender = "carol")
        bob.passes()
        carol.casts(klaxon)
        resolveAll()
        expect {
            card("Mountain", 0).tapped shouldBe true
            card("Mountain", 1).tapped shouldBe false
            card("Mountain", 2).tapped shouldBe false
            cub.power shouldBe 4
            drifter.power shouldBe 1
            ogre.power shouldBe 3
        }
    }

    // CR 602.2, 302.6: {T} pings every opponent; a noncreature spell untaps it for a second ping; a creature spell doesn't
    @Test
    fun analystPingsEachOpponentAndUntapsOnNoncreatureSpells() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", analyst)
        alice.hand(bolt, drifter)
        alice.activates(analyst)
        resolveAll()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 19
            alice.life shouldBe 20
            analyst.tapped shouldBe true
        }
        alice.casts(drifter)
        resolveAll()
        expect { analyst.tapped shouldBe true }
        alice.casts(bolt, target = "bob")
        resolveAll()
        expect {
            analyst.tapped shouldBe false
            bob.life shouldBe 18
        }
        alice.activates(analyst)
        resolveAll()
        expect {
            bob.life shouldBe 17
            carol.life shouldBe 18
        }
    }
}
