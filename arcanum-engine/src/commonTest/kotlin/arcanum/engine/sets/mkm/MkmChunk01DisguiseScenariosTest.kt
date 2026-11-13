package arcanum.engine.sets.mkm

import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Players
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.action.Knowledge
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.alternativeOf
import arcanum.engine.sets.tdm.Plain
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MKM chunk 01: disguise (CR 702.168), "as" and "when" a permanent is turned face up (CR 614.1e, 708.8). */
class MkmChunk01DisguiseScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val avenger = Cards.invent("Shrouded Halo Avenger", "{2}{W}{W}", "Creature — Angel", """
        Flying, lifelink, ward {2}
        Disguise {X}{3}{W}
        When this creature is turned face up, exile up to X other target creatures from the battlefield and/or creature cards from graveyards.
        When this creature leaves the battlefield, return the exiled cards to their owners' hands.
    """, "4/2")
    private val courier = Cards.invent("Inkcloud Courier", "{1}{U}", "Creature — Octopus Fish", """
        Disguise {5}{U} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        As this creature is turned face up, put four +1/+1 counters on it.
    """, "2/1")
    private val kestrel = Cards.invent("Prized Kestrel", "{1}{U}{U}", "Artifact Creature — Bird", """
        Flying
        Whenever this creature attacks, gain control of target permanent you own but don't control.
        Disguise {1}{U}
        When this creature is turned face up, target opponent gains control of any number of target permanents you control. Draw a card for each one they gained control of this way.
    """, "1/4")
    private val sentry = Cards.invent("Rabble-Taming Sentry", "{3}{G}{W}", "Creature — Centaur Soldier", """
        As this creature enters or is turned face up, put X +1/+1 counters on it, where X is the number of other creatures you control.
        Disguise {3}{G/W}{G/W} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
    """, "4/4")
    private val cipherist = Cards.invent("Runaway Cipherist", "{1}{R}", "Creature — Goblin Rogue", """
        Prowess, haste
        Disguise {5}{R}. This cost is reduced by {1} for each instant and sorcery card in your graveyard. (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        When this creature is turned face up, discard your hand, then draw three cards.
    """, "2/1")
    private val tinkerer = Cards.invent("Contraption Tinkerer", "{2}{U}{R}", "Creature — Goblin Artificer", """
        When this creature enters or is turned face up, create a 1/1 colorless Thopter artifact creature token with flying.
        Disguise {U/R}{U/R} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
    """, "3/2")
    private val washtub = Cards.invent("Shambling Washtub", "{5}", "Artifact Creature — Golem", """
        {2}: Until end of turn, you may look at face-down creatures you don't control any time.
        Disguise {5} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
    """, "4/5")
    private val showman = Cards.invent("Flashpowder Showman", "{1}{R}", "Creature — Lizard Assassin", """
        Disguise {R} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        Whenever this creature or another creature you control is turned face up, that creature deals damage equal to its power to each opponent.
    """, "3/2")
    private val portwarden = Cards.invent("Steadfast Portwarden", "{1}{W}", "Creature — Elephant Cleric", """
        Disguise {1}{W} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        When this creature is turned face up, exile another target nonland permanent. If you controlled it, return it to the battlefield tapped. Otherwise, its controller creates a 2/2 white and blue Detective creature token.
    """, "3/2")
    private val mugger = Cards.invent("Backstreet Mugger", "{2}{B}", "Creature — Vampire Rogue", """
        This creature enters tapped.
        Disguise {4}{B}{B} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        When this creature is turned face up, target opponent loses 3 life and you gain 3 life.
    """, "3/3")

    private fun Scenario.pay() {
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    /** [player] turns [name], face down on the battlefield, face up for its disguise cost (CR 702.168d, 116.2b), with [x] as its X. */
    private fun Scenario.turnUp(name: String, player: ScenarioPlayer = alice, x: Int? = null) {
        val id = card(name)
        perform(Engine.options(state, player.id).filterIsInstance<Input.Activate>().single { it.source == id })
        if (pending is Decision.Announce) answer(Answer.Number(x!!))
        pay()
    }

    // CR 614.1e: the counters are put on as it turns face up, not on the Stack, so the Showman's trigger (CR 603.10a: "that creature")
    // sees a 6/5 Courier; it deals damage to each opponent (CR 102.2)
    @Test
    fun courierTurnsUpWithCountersAndTheShowmanHitsEachOpponent() = scenario(players = 3) {
        alice.battlefield(showman, "Island", "Island", "Island", "Island", "Island", "Island")
        alice.battlefield(courier, faceDown = true)
        turnUp(courier)
        expect {
            courier.counters(p1p1) shouldBe 4
            courier.power shouldBe 6
        }
        assertEquals(1, state.stack.size)
        resolveAll()
        expect {
            bob.life shouldBe 14
            carol.life shouldBe 14
            alice.life shouldBe 20
        }
    }

    // CR 603.2, 702.168d: "this creature or another creature you control": the Showman's own turning face up too
    @Test
    fun showmanTurnedUpDealsItsOwnPower() = scenario(players = 3) {
        alice.battlefield("Mountain")
        alice.battlefield(showman, faceDown = true)
        turnUp(showman)
        resolveAll()
        expect {
            bob.life shouldBe 17
            carol.life shouldBe 17
        }
    }

    // CR 614.1c, 614.12: as it enters, it gets X counters, the number of other creatures its controller has
    @Test
    fun sentryEntersWithACounterForEachOtherCreature() = scenario {
        alice.battlefield(Plain.bear, Plain.elf, "Forest", "Forest", "Forest", "Plains", "Plains")
        bob.battlefield(Plain.ogre)
        alice.hand(sentry)
        alice.casts(sentry)
        allPass()
        expect {
            sentry.counters(p1p1) shouldBe 2
            sentry.power shouldBe 6
        }
    }

    // CR 614.1e: and as it is turned face up (it is already on the battlefield: it doesn't count itself)
    @Test
    fun sentryTurnedUpGetsACounterForEachOtherCreature() = scenario {
        alice.battlefield(Plain.bear, "Forest", "Forest", "Forest", "Plains", "Plains")
        alice.battlefield(sentry, faceDown = true)
        turnUp(sentry)
        expect {
            sentry.counters(p1p1) shouldBe 1
            sentry.toughness shouldBe 5
        }
    }

    // CR 603.2, 702.168d: one ability, triggering when it enters and when it is turned face up
    @Test
    fun tinkererMakesAThopterWhenItEntersAndWhenTurnedUp() = scenario {
        alice.battlefield("Island", "Mountain", "Island", "Mountain")
        alice.battlefield(tinkerer, faceDown = true)
        turnUp(tinkerer)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Thopter Token" } shouldBe 1 }
    }

    // CR 702.168e, 107.3: up to X targets, X being what its disguise cost was paid with
    @Test
    fun avengerExilesUpToXTargets() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.battlefield(avenger, faceDown = true)
        bob.battlefield(Plain.ogre)
        bob.graveyard(Plain.giant)
        turnUp(avenger, x = 1)
        resolveAll()
        assertEquals(1, decision<Decision.Targets>().max)
        answer(Answer.Targets(listOf(TargetRef.Object(card(Plain.giant)))))
        resolveAll()
        expect {
            bob.exile has Plain.giant
            bob.battlefield has Plain.ogre
        }
    }

    // CR 118.7: the disguise cost {5}{R} is reduced by {1} for each instant and sorcery card in its controller's graveyard
    @Test
    fun cipheristTurnsUpForLessWithSpellsInTheGraveyard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.battlefield(cipherist, faceDown = true)
        alice.graveyard(Plain.shock, Plain.study)
        alice.hand(Plain.bear)
        alice.library("Island", "Plains", "Swamp")
        turnUp(cipherist)
        assertFalse(state.objOrThrow(card(cipherist)).faceDown)
        resolveAll()
        expect {
            alice.graveyard has Plain.bear
            alice.hand.size shouldBe 3
        }
    }

    // CR 118.7, 116.2b: one instant only takes {1} off; four lands can't pay {4}{R}, so it can't be turned face up
    @Test
    fun cipheristCantTurnUpWithoutEnoughReduction() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.battlefield(cipherist, faceDown = true)
        alice.graveyard(Plain.shock)
        val id = card(cipherist)
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().none { it.source == id })
    }

    // CR 108.3, 110.2: "a permanent you own but don't control"; the control change lasts (CR 613.1b)
    @Test
    fun kestrelTakesBackWhatItsControllerOwns() = scenario {
        alice.battlefield(kestrel, Plain.idol)
        effect(bob, Mechanic.ControlledBy(Players.You), on = listOf(Plain.idol), duration = Duration.Indefinitely)
        bob.battlefield(Plain.charm)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(kestrel)
        resolveAll()
        expect { Plain.idol.controller shouldBe alice.id }
    }

    // CR 115.1, 601.2c: any number of target permanents go to the target opponent; a card for each one they now control (CR 608.2c)
    @Test
    fun kestrelTurnedUpGivesPermanentsAwayAndDraws() = scenario(players = 3) {
        alice.battlefield("Island", "Island", Plain.idol, Plain.charm)
        alice.battlefield(kestrel, faceDown = true)
        alice.library("Plains", "Swamp", "Forest")
        turnUp(kestrel)
        resolveAll()
        answer(Answer.Targets(listOf(TargetRef.Player(carol.id))))
        answer(Answer.Targets(listOf(target(Plain.idol), target(Plain.charm))))
        resolveAll()
        expect {
            Plain.idol.controller shouldBe carol.id
            Plain.charm.controller shouldBe carol.id
            alice.hand.size shouldBe 2
        }
    }

    // CR 708.5, 611.2a: for the rest of the turn its controller may look at face-down creatures they don't control
    @Test
    fun washtubLetsItsControllerLookAtFaceDownCreatures() = scenario {
        alice.battlefield(washtub, "Plains", "Plains")
        bob.battlefield(Plain.bear, faceDown = true)
        val bear = card(Plain.bear)
        assertFalse(Knowledge.lookable(state, view()).contains(bear to alice.id))
        alice.activates(washtub)
        resolveAll()
        assertTrue(Knowledge.lookable(state, view()).contains(bear to alice.id))
    }

    // CR 400.7, 110.2a: its controller's own permanent comes back tapped, a new object under their control
    @Test
    fun portwardenFlickersItsControllersPermanentTapped() = scenario {
        alice.battlefield("Plains", "Plains", Plain.idol)
        alice.battlefield(portwarden, faceDown = true)
        turnUp(portwarden)
        resolveAll()
        expect {
            alice.battlefield has Plain.idol
            Plain.idol.tapped shouldBe true
        }
    }

    // CR 608.2h: "you controlled it" is the permanent as it last existed: a permanent of another owner it controlled comes back under its
    // control (CR 110.2a)
    @Test
    fun portwardenReturnsABorrowedPermanentToItsController() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.battlefield(portwarden, faceDown = true)
        bob.battlefield(Plain.idol)
        effect(alice, Mechanic.ControlledBy(Players.You), on = listOf(Plain.idol), duration = Duration.Indefinitely)
        turnUp(portwarden)
        resolveAll()
        expect {
            Plain.idol.controller shouldBe alice.id
            Plain.idol.tapped shouldBe true
            alice.battlefield.names.contains("Detective Token") shouldBe false
        }
    }

    // CR 608.2h: "its controller" is the exiled permanent's controller as it last existed, who creates the Detective (3 players)
    @Test
    fun portwardenGivesTheExiledPermanentsControllerADetective() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains")
        alice.battlefield(portwarden, faceDown = true)
        carol.battlefield(Plain.idol)
        turnUp(portwarden)
        resolveAll()
        expect {
            carol.exile has Plain.idol
            carol.battlefield has "Detective Token"
            bob.battlefield lacks "Detective Token"
            alice.battlefield lacks "Detective Token"
        }
    }

    // CR 708.2a, 702.168a: cast face down it has no abilities as it enters, so it doesn't enter tapped; turning it face up isn't entering
    // (CR 702.168d)
    @Test
    fun muggerCastFaceDownEntersUntapped() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(mugger)
        alice.casts(mugger, way = alternativeOf(mugger, Keyword.Disguise.Kind))
        pay()
        allPass()
        assertTrue(state.objOrThrow(card(mugger)).faceDown)
        expect { mugger.tapped shouldBe false }
        turnUp(mugger)
        resolveAll()
        expect {
            mugger.tapped shouldBe false
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    // CR 614.1c: cast face up, it enters tapped
    @Test
    fun muggerCastFaceUpEntersTapped() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(mugger)
        alice.casts(mugger)
        allPass()
        expect { mugger.tapped shouldBe true }
    }
}
