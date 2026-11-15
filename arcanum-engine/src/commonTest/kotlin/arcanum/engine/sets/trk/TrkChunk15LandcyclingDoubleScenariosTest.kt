package arcanum.engine.sets.trk

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Star Trek chunk 15 in play: basic landcycling, a creature that can't be blocked by small creatures, an uncounterable life doubler, total-power attack triggers, a land whose two abilities share the tap, three players. */
class TrkChunk15LandcyclingDoubleScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private val crew = Cards.invent("Trk15 Reptile Vanguard", "{4}{G}{G}", "Creature — Gorn Warrior", """
        When this creature enters, put two +1/+1 counters on target creature.
        This creature can't be blocked by creatures with power 2 or less.
        Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
    """.trimIndent(), "4/4")
    private val drones = Cards.invent("Trk15 Unbound Drones", "{3}{W}{W}", "Artifact Creature — Borg Rebel", """
        When this creature enters, creatures you control get +1/+1 until end of turn.
        Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
    """.trimIndent(), "4/5")
    private val longLife = Cards.invent("Trk15 Long Life and Fortune", "{5}{W}{U}", "Sorcery", """
        This spell can't be countered.
        Double your life total. Draw cards equal to the number of cards in your hand. You have no maximum hand size until your next turn.
    """.trimIndent())
    private val negate = Cards.invent("Trk15 Negate Order", "{1}{U}", "Instant", "Counter target spell.")
    private val kaleth = Cards.invent("Trk15 Kaleth", "{R/W}{R/W}", "Legendary Creature — Klingon Warrior Officer", """
        Whenever you attack while creatures you control have total power 8 or greater, double Trk15 Kaleth's power until end of turn.
        Tap two untapped artifacts and/or creatures you control: Put a +1/+1 counter on Trk15 Kaleth. Activate only as a sorcery.
    """.trimIndent(), "1/1")
    private val warden = Cards.invent("Trk15 Ilya Warden", "{2}{R}{R}", "Legendary Creature — Human Soldier",
        "Whenever you attack while creatures you control have total power 8 or greater, target creature can't block this turn.", "4/3")
    private val brute = Cards.invent("Trk15 Brute", "{4}{G}", "Creature — Ogre", "", "5/5")
    private val runt = Cards.invent("Trk15 Runt", "{G}", "Creature — Ogre", "", "2/2")
    private val cub = Cards.invent("Trk15 Cub", "{G}", "Creature — Cat", "", "2/2")
    private val ogre = Cards.invent("Trk15 Ogre", "{2}{G}", "Creature — Ogre", "", "3/3")
    private val land = Cards.invent("Trk15 Uncharted Frontier", null, "Land", """
        {T}: Add {C}.
        {1}, {T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
    """.trimIndent())
    private val processor = Cards.invent("Trk15 Linear Cogitator", "{2}", "Artifact", """
        {T}: Add {C}. Spend this mana only to cast an artifact spell or activate an ability of an artifact source.
        {2}, {T}: Draw a card, then discard a card.
    """.trimIndent())
    private val gadget = Cards.invent("Trk15 Gadget", "{1}", "Artifact", "")
    private val drifter = Cards.invent("Trk15 Drifter", "{1}", "Creature — Human", "", "1/1")

    private fun Scenario.toCombat() {
        turn(step = TurnStep.PrecombatMain)
        advanceTo(TurnStep.DeclareAttackers)
    }

    // CR 702.29, 205.3i: discarding it from hand pays {2}; the basic land is revealed and put into hand
    @Test
    fun basicLandcyclingFindsOnlyABasicLand() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest")
        alice.hand(crew)
        alice.library("Trk15 Uncharted Frontier", "Island", "Plains")
        alice.activates(crew)
        resolveAll()
        alice.chooses("Island")
        resolveAll()
        expect {
            alice.hand has "Island"
            alice.graveyard has crew
            alice.hand lacks "Plains"
        }
    }

    // CR 509.1b: power 2 can't block it, power 3 can; every defending player's creatures obey it
    @Test
    fun crewCantBeBlockedByPowerTwoOrLess() = scenario(players = 3) {
        alice.battlefield(crew)
        bob.battlefield(cub, ogre)
        toCombat()
        alice.attacks(crew, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(cub to crew) }
        bob.blocks(ogre to crew)
    }

    // CR 611.2c: only creatures you control when the trigger resolves are pumped; opponents' aren't
    @Test
    fun dronesPumpOnlyYourCreatures() = scenario(players = 3) {
        alice.battlefield(runt, "Plains", "Plains", "Plains", "Plains", "Plains")
        bob.battlefield(cub)
        carol.battlefield(ogre)
        alice.hand(drones)
        alice.casts(drones)
        resolveAll()
        expect {
            runt.power shouldBe 3
            drones.power shouldBe 5
            cub.power shouldBe 2
            ogre.power shouldBe 3
        }
    }

    // CR 101.5 (can't be countered): the counterspell resolves but the spell still does; 701.10d (life doubled), 402.2
    @Test
    fun longLifeResistsACounterspellAndDoublesLifeAndDraws() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Island")
        alice.hand(longLife, gadget, gadget, gadget)
        alice.library("Island", "Island", "Island", "Island", "Island", "Island")
        bob.battlefield("Island", "Island")
        bob.hand(negate)
        alice.casts(longLife)
        alice.passes()
        bob.casts(negate, target = longLife)
        resolveAll()
        expect {
            alice.life shouldBe 40
            bob.life shouldBe 20
            carol.life shouldBe 20
            alice.hand.size shouldBe 6
            alice.graveyard has longLife
        }
    }

    // CR 402.2: no maximum hand size until your next turn: eight cards stay in hand at cleanup
    @Test
    fun longLifeKeepsAnOversizedHandThroughCleanup() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Island")
        alice.hand(longLife, gadget, gadget, gadget, gadget)
        alice.library("Island", "Island", "Island", "Island", "Island", "Island")
        alice.casts(longLife)
        resolveAll()
        advanceTo(TurnStep.End)
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { alice.hand.size shouldBe 8 }
    }

    // CR 514.1: control for the test above: without the effect the hand is cut to seven at cleanup
    @Test
    fun withoutItTheHandIsCutToSeven() = scenario {
        alice.hand(gadget, gadget, gadget, gadget, gadget, gadget, gadget, gadget)
        advanceTo(TurnStep.End)
        assertFailsWith<Throwable> { advanceTo(TurnStep.Upkeep, turn = 2) }
        decision<Decision.ChooseObjects>()
    }

    // CR 700.5 total power 9 (1 + 5 + 2): the attack trigger doubles its power (701.10b): 1 -> 2
    @Test
    fun kalethDoublesWhenTotalPowerIsEightOrMore() = scenario(players = 3) {
        alice.battlefield(kaleth, brute, runt)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(kaleth, defender = "bob")
        resolveAll()
        expect { kaleth.power shouldBe 2 }
    }

    @Test
    fun kalethDoesNothingBelowTotalPowerEight() = scenario(players = 3) {
        alice.battlefield(kaleth, runt)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(kaleth, defender = "bob")
        resolveAll()
        expect { kaleth.power shouldBe 1 }
    }

    // CR 701.10b: X is the power as it resolves: with a counter already on it (power 2) it becomes 4
    @Test
    fun kalethDoublesTheCurrentPower() = scenario(players = 3) {
        alice.battlefield(kaleth, brute, counters = listOf(p1p1 to 1))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(kaleth, defender = "bob")
        resolveAll()
        expect { kaleth.power shouldBe 4 }
    }

    // CR 700.5, 509.1a: the chosen opponent creature can't block this turn, another one can
    @Test
    fun wardenStopsOneBlocker() = scenario(players = 3) {
        alice.battlefield(warden, brute)
        bob.battlefield(cub, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(warden, defender = "bob")
        answer(arcanum.engine.decision.Answer.Targets(listOf(target("Trk15 Cub"))))
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(cub to warden) }
        bob.blocks(ogre to warden)
    }

    // CR 602.2, 701.26: both land abilities need {T}: once tapped for mana, the sacrifice ability can't be paid; untapped, it fetches a land
    @Test
    fun tappedLandCantAlsoBeSacrificed() = scenario(players = 3) {
        alice.battlefield(land, "Island")
        alice.library("Forest", "Plains")
        alice.activates(land)
        expect { alice.pool.size shouldBe 1 }
        assertFailsWith<Throwable> { alice.activates(land, ability = 1) }
    }

    @Test
    fun untappedLandSacrificesForABasicLand() = scenario(players = 3) {
        alice.battlefield(land, "Island")
        alice.library("Forest", "Plains")
        alice.activates(land, ability = 1)
        resolveAll()
        alice.chooses("Forest")
        resolveAll()
        expect {
            alice.graveyard has land
            alice.battlefield has "Forest"
        }
    }

    // CR 106.6: the colorless mana is spent only on artifacts and artifact abilities: it can't pay for a creature spell
    @Test
    fun processorManaPaysForAnArtifactButNotACreature() = scenario(players = 3) {
        alice.battlefield(processor)
        alice.hand(gadget, drifter)
        alice.activates(processor)
        perform(Input.Cast(alice.id, card("Trk15 Drifter")))
        val pay = decision<Decision.ManaPayment>()
        assertTrue(pay.options.none { it is ManaUse.Pool })
        perform(Input.Cancel(alice.id))
        alice.casts(gadget)
        resolveAll()
        expect { alice.battlefield has gadget }
    }

    // CR 602.2: its looting ability costs {2} and {T}: the same tap that makes mana is used up
    @Test
    fun processorTapsToLoot() = scenario(players = 3) {
        alice.battlefield(processor, "Island", "Island")
        alice.hand(gadget)
        alice.library("Island")
        alice.activates(processor, ability = 1)
        resolveAll()
        alice.chooses("Trk15 Gadget")
        resolveAll()
        expect {
            processor.tapped shouldBe true
            alice.graveyard has gadget
        }
    }
}
