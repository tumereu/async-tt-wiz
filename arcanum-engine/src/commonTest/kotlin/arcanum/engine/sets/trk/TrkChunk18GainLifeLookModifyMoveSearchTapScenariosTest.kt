package arcanum.engine.sets.trk

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Star Trek chunk 18 in play, three players: life gained counted for counters, a hand look that exiles a card its owner may play for more, exiled
 * cards played later, a tap trigger, a graveyard ability that copies itself, a sacrifice after attacking, searches, a Saga that buffs creatures
 * entering, and an attack trigger on total power. */
class TrkChunk18GainLifeLookModifyMoveSearchTapScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val infirmary = Cards.invent("Trk18 Infirmary Of Growth", "{5}", "Artifact", """
        Whenever a creature you control enters, you gain 1 life.
        At the beginning of combat on your turn, put X +1/+1 counters on target creature you control, where X is one plus the amount of life you gained this turn.
    """.trimIndent())
    private val nero = Cards.invent("Trk18 Nero", "{1}{W/B}{W/B}", "Legendary Artifact Creature — Medusan Scientist", """
        Lifelink
        When Trk18 Nero enters, look at target opponent's hand. You may exile a nonland card from it. For as long as that card remains exiled, its owner may play it. A spell cast this way costs {2} more to cast.
    """.trimIndent(), "2/2")
    private val impulse = Cards.invent("Trk18 Rash Impulse", "{1}{R}", "Sorcery",
        "Exile the top two cards of your library. Until the end of your next turn, you may play those cards.")
    private val vexing = Cards.invent("Trk18 Vexing Automaton", "{1}{W}", "Artifact Creature — Borg",
        "When this creature enters, tap target creature an opponent controls.", "2/2")
    private val tasha = Cards.invent("Trk18 Tasha Yarrow", "{1}{R}", "Legendary Creature — Human Officer", """
        Haste
        When Trk18 Tasha Yarrow dies, exile the top card of your library. Until the end of your next turn, you may play that card.
        {2}{R}, Exile this card from your graveyard: Create a token that's a copy of this card. Sacrifice it at the beginning of the next end step. Activate only as a sorcery.
    """.trimIndent(), "2/1")
    private val targ = Cards.invent("Trk18 Targ Handler", "{2}{R}", "Creature — Klingon Warrior", """
        Menace (This creature can't be blocked except by two or more creatures.)
        At the beginning of your end step, if this creature attacked this turn, sacrifice a nonland permanent.
    """.trimIndent(), "5/3")
    private val genesis = Cards.invent("Trk18 Genesis Engine", "{2}{G}", "Legendary Artifact", """
        When Trk18 Genesis Engine enters, search your library for a basic land card or Planet card, put it onto the battlefield tapped, then shuffle.
        {3}{G}{G}, {T}, Sacrifice Trk18 Genesis Engine: Sacrifice any number of lands you control, then search your library for up to that many land cards, put them onto the battlefield tapped, then shuffle. Activate only as a sorcery.
    """.trimIndent())
    private val planet = Cards.invent("Trk18 Far Planet", null, "Land — Planet", "{T}: Add {C}.")
    private val naked = Cards.invent("Trk18 The Naked Hour", "{1}{R}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Search your library for a basic Mountain card, put it onto the battlefield tapped, then shuffle.
        II — Whenever a creature you control enters this turn, put a +1/+1 counter on it and it gains haste until end of turn.
        III — Sacrifice a land.
    """.trimIndent())
    private val kirk = Cards.invent("Trk18 Kirkland", "{1}{R}{W}", "Legendary Creature — Human Officer", """
        Whenever you attack while creatures you control have total power 8 or greater, exile the top card of your library. You may play that card this turn.
        {2}, Sacrifice Trk18 Kirkland: Destroy target artifact or enchantment.
    """.trimIndent(), "3/3")
    private val drifter = Cards.invent("Trk18 Drifter", "{1}", "Creature — Human", "", "1/1")
    private val bear = Cards.invent("Trk18 Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Trk18 Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")

    // CR 603.2, 119.1, 608.2h: the creature entering gains 1 life, so combat puts 1 + 1 counters; another player's creature entering gives nothing
    @Test
    fun infirmaryCountsTheLifeGainedThisTurn() = scenario(players = 3) {
        alice.battlefield(infirmary, "Forest")
        alice.hand(drifter)
        for (p in listOf(alice, bob, carol)) p.library("Forest", "Forest", "Forest")
        alice.casts(drifter)
        resolveAll()
        expect { alice.life shouldBe 21 }
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { drifter.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun infirmaryPutsOneCounterWhenNoLifeWasGained() = scenario(players = 3) {
        alice.battlefield(infirmary, drifter)
        for (p in listOf(alice, bob, carol)) p.library("Forest", "Forest", "Forest")
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { drifter.counters(p1p1) shouldBe 1 }
    }

    // CR 400.7, 601.2f: of the two opponents only the one targeted loses a card, its owner alone may play it later, paying {2} more
    @Test
    fun neroExilesACardOfTheTargetedOpponentWhichItsOwnerPlaysForTwoMore() = scenario(players = 3) {
        alice.hand(nero)
        alice.battlefield("Plains", "Plains", "Plains")
        bob.hand(ogre, "Forest")
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        carol.hand(bear)
        for (p in listOf(alice, bob, carol)) p.library("Island", "Island", "Island", "Island", "Island", "Island")
        alice.casts(nero)
        resolveAll()
        answer(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            bob.exile has ogre
            bob.hand has "Forest"
            carol.hand has bear
        }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts(ogre)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            bob.battlefield.names.count { it == "Mountain" } shouldBe 5
            "Mountain".tapped shouldBe true
        }
    }

    // CR 406.3, 611.2b: both cards are exiled and may be played on your next turn (the fourth turn with three players)
    @Test
    fun impulseLetsYouPlayTheExiledLandOnYourNextTurn() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(impulse)
        alice.library("Forest", ogre, "Island")
        for (p in listOf(bob, carol)) p.library("Island", "Island", "Island", "Island")
        alice.casts(impulse)
        resolveAll()
        expect {
            alice.exile has "Forest"
            alice.exile has ogre
        }
        advanceTo(TurnStep.PrecombatMain, turn = 4)
        alice.plays("Forest")
        expect { card("Forest").zone shouldBe Zone.Battlefield }
    }

    // CR 701.26b: the automaton taps the chosen opponent's creature; the other opponent's creature stays untapped
    @Test
    fun vexingAutomatonTapsOnlyTheChosenCreature() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains")
        alice.hand(vexing)
        bob.battlefield(bear)
        carol.battlefield(ogre)
        alice.casts(vexing)
        resolveAll()
        answer(Answer.Targets(listOf(target(ogre))))
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            bear.tapped shouldBe false
        }
    }

    // CR 113.6m, 707.2, 603.7: from the graveyard, for {2}{R}, a hasty token copy that is sacrificed at the end step
    @Test
    fun tashaCopiesItselfFromTheGraveyardForATurn() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.graveyard(tasha)
        turn(step = TurnStep.PrecombatMain)
        alice.activates(tasha)
        resolveAll()
        expect {
            alice.exile has tasha
            alice.battlefield.names.count { it == tasha } shouldBe 1
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == tasha } shouldBe 0
        }
    }

    // CR 602.5d: only as a sorcery, so not on another player's turn
    @Test
    fun tashaCannotCopyItselfOnAnotherPlayersTurn() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.graveyard(tasha)
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain, priority = alice)
        assertFailsWith<Throwable> { alice.activates(tasha) }
    }

    // CR 603.4: having attacked this turn, the end step costs a nonland permanent of the controller's choice
    @Test
    fun targHandlerSacrificesAfterAttacking() = scenario(players = 3) {
        alice.battlefield(targ, drifter, "Forest")
        turn(step = TurnStep.PrecombatMain)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(targ, defender = "bob")
        advanceTo(TurnStep.End)
        resolveAll()
        alice.chooses(drifter)
        resolveAll()
        expect {
            alice.graveyard has drifter
            alice.battlefield has "Forest"
            targ.zone shouldBe Zone.Battlefield
        }
    }

    @Test
    fun targHandlerSacrificesNothingWithoutAttacking() = scenario(players = 3) {
        alice.battlefield(targ, drifter, "Forest")
        turn(step = TurnStep.PrecombatMain)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield has drifter
            targ.zone shouldBe Zone.Battlefield
        }
    }

    // CR 701.23, 205.3i: entering, it fetches a Planet card tapped
    @Test
    fun genesisEngineFetchesAPlanetWhenItEnters() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(genesis)
        alice.library("Island", planet, "Island")
        alice.casts(genesis)
        resolveAll()
        alice.chooses(planet)
        resolveAll()
        expect {
            planet.zone shouldBe Zone.Battlefield
            planet.tapped shouldBe true
        }
    }

    // CR 701.21, 701.23: two lands sacrificed, up to two land cards fetched tapped
    @Test
    fun genesisEngineTradesSacrificedLandsForTheSameNumber() = scenario(players = 3) {
        alice.battlefield(genesis, "Forest", "Forest", "Forest", "Forest", "Forest", "Mountain", "Mountain")
        alice.library("Plains", "Plains", "Plains", "Island")
        turn(step = TurnStep.PrecombatMain)
        alice.activates(genesis, paying = listOf("Forest", "Forest#1", "Forest#2", "Forest#3", "Forest#4"))
        resolveAll()
        alice.chooses("Mountain", "Mountain")
        resolveAll()
        alice.chooses("Plains", "Plains")
        resolveAll()
        expect {
            genesis.zone shouldBe Zone.Graveyard
            alice.battlefield.names.count { it == "Plains" } shouldBe 2
            alice.battlefield.names.count { it == "Mountain" } shouldBe 0
            "Plains".tapped shouldBe true
        }
    }

    // CR 714.2, 603.7: chapter I fetches a Mountain tapped; on the next turn chapter II marks creatures entering that turn with a counter and haste
    @Test
    fun theNakedHourFetchesThenBuffsCreaturesThatEnter() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(naked, ogre)
        for (p in listOf(alice, bob, carol)) p.library("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.casts(naked)
        resolveAll()
        alice.chooses("Mountain")
        resolveAll()
        expect { alice.battlefield.names.count { it == "Mountain" } shouldBe 4 }
        advanceTo(TurnStep.PrecombatMain, turn = 4)
        resolveAll()
        alice.casts(ogre)
        resolveAll()
        expect {
            ogre.counters(p1p1) shouldBe 1
            ogre hasKeyword Keyword.Haste.kind
        }
    }

    // CR 603.4: with total power 8 the attack exiles the top card, playable this turn
    @Test
    fun kirklandExilesTheTopCardWhenTheTotalPowerIsEight() = scenario(players = 3) {
        alice.battlefield(kirk, ogre, bear)
        alice.library("Forest", "Island")
        turn(step = TurnStep.PrecombatMain)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(kirk, defender = "bob")
        resolveAll()
        expect { alice.exile has "Forest" }
    }

    @Test
    fun kirklandDoesNothingBelowTotalPowerEight() = scenario(players = 3) {
        alice.battlefield(kirk, ogre)
        alice.library("Forest", "Island")
        turn(step = TurnStep.PrecombatMain)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(kirk, defender = "bob")
        resolveAll()
        expect { alice.exile.names.size shouldBe 0 }
    }
}
