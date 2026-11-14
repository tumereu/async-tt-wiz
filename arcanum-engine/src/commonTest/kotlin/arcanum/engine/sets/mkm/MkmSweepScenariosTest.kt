package arcanum.engine.sets.mkm

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.alternativeOf
import arcanum.engine.sets.tdm.Plain
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MKM sweep: siblings of the engine and parser fixes the set's chunks made, played on other paths. */
class MkmSweepScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private val afterword = Cards.invent("Raider's Afterword", "{3}{R}", "Instant", """
        This spell costs {2} less to cast if you attacked this turn.
        Raider's Afterword deals 3 damage to any target.
    """)
    private val mourner = Cards.invent("Rampart Mourner", "{1}{W}", "Creature — Human Cleric", "Whenever a blocking creature dies, you gain 1 life.", "1/3")
    private val hoarder = Cards.invent("Ossuary Hoarder", "{1}{B}", "Creature — Rat", """
        {2}, Exile a card from your graveyard: Put a +1/+1 counter on this creature.
        When this creature leaves the battlefield, put each card exiled with it into its owner's hand.
    """, "1/1")
    private val clerk = Cards.invent("Docket Clerk", "{1}{U}", "Creature — Human Detective", """
        Whenever another creature you control enters, you may collect evidence 1. If you do, draw a card.
        When this creature leaves the battlefield, put each card exiled with it into its owner's hand.
    """, "1/2")
    private val drill = Cards.invent("Recall Drill", "{1}{W}", "Instant", """
        Exile target creature you control, then return it to the battlefield under its owner's control. Put a +1/+1 counter on it.
    """)
    private val barter = Cards.invent("Grim Barter", "{2}{B}", "Creature — Human Warlock", """
        When this creature enters, you may sacrifice a creature. If you do, create a 2/2 black Zombie creature token. Put a +1/+1 counter on it.
    """, "2/2")
    private val witness = Cards.invent("Burrow Witness", "{1}{G}", "Creature — Mole Scout", """
        At the beginning of your end step, if a face-down creature entered the battlefield under your control this turn, put a +1/+1 counter on this creature.
    """, "1/1")
    private val visitor = Cards.invent("Furtive Visitor", "{2}{R}", "Creature — Human Rogue", "Disguise {1}{R}", "3/2")
    private val keeper = Cards.invent("Lure Keeper", "{3}{U}", "Creature — Merfolk Wizard", """
        When this creature enters, gain control of target creature an opponent controls for as long as you control this creature.
    """, "2/2")

    private fun Scenario.pay() {
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    private fun Scenario.libraries() {
        alice.library("Swamp", "Swamp", "Swamp")
        bob.library("Swamp", "Swamp")
        carol.library("Swamp", "Swamp")
    }

    // CR 603.10a, 608.2h (sibling of chunk 14's sacrificed artifact): "if you attacked this turn" in a spell's cost reduction is about
    // creatures as they attacked; the only attacker died in combat and still counts, its controller read as it last existed
    @Test
    fun attackedThisTurnCountsAnAttackerThatDied() = scenario(players = 3) {
        alice.battlefield(Plain.bear, "Mountain", "Mountain")
        alice.hand(afterword)
        bob.battlefield(Plain.ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(Plain.bear, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(Plain.ogre to Plain.bear)
        advanceTo(TurnStep.PostcombatMain)
        expect { alice.graveyard has Plain.bear }
        alice.casts(afterword, target = "bob")
        // {3}{R} less {2} is payable with the two Mountains
        if (pending is Decision.ManaPayment) assertTrue(decision<Decision.ManaPayment>().suggestion.isNotEmpty())
        pay()
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    // CR 603.10a, 506.4: a blocking creature that dies is looked back on as it was, blocking, though dying removed it from combat
    @Test
    fun aBlockingCreatureThatDiesTriggers() = scenario(players = 3) {
        alice.battlefield(mourner, Plain.ogre)
        bob.battlefield(Plain.bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(Plain.ogre, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(Plain.bear to Plain.ogre)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            bob.graveyard has Plain.bear
            alice.life shouldBe 21
        }
    }

    // CR 607.2a, 406.6 (sibling of chunk 03's collect evidence cost): the cards an activated ability's graveyard exile cost exiles are "exiled
    // with" its source, every activation's
    @Test
    fun graveyardExileCostCardsAreExiledWithTheSource() = scenario(players = 3) {
        alice.battlefield(hoarder, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(Plain.study, Plain.heal)
        alice.hand(Plain.doom)
        alice.activates(hoarder, choosing = listOf(Plain.study))
        pay()
        if (pending is Decision.ChooseObjects) alice.chooses(Plain.study)
        resolveAll()
        alice.activates(hoarder, choosing = listOf(Plain.heal))
        pay()
        if (pending is Decision.ChooseObjects) alice.chooses(Plain.heal)
        resolveAll()
        expect { hoarder.counters(p1p1) shouldBe 2 }
        alice.casts(Plain.doom, target = hoarder)
        pay()
        resolveAll()
        expect {
            alice.hand has Plain.study
            alice.hand has Plain.heal
        }
    }

    // CR 607.2a, 701.59a (sibling of chunk 03 on the resolution path): a triggered ability's optional collect evidence exiles cards with its
    // source each time; the second time adds to the first
    @Test
    fun triggeredCollectEvidenceKeepsEveryCardExiledWithIt() = scenario(players = 3) {
        alice.battlefield(clerk, "Forest", "Forest", "Forest", "Swamp")
        alice.graveyard(Plain.study, Plain.heal)
        alice.hand(Plain.elf, Plain.bear, Plain.doom)
        libraries()
        alice.casts(Plain.elf)
        pay()
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses(Plain.study)
        resolveAll()
        alice.casts(Plain.bear)
        pay()
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses(Plain.heal)
        resolveAll()
        expect {
            alice.exile has Plain.study
            alice.exile has Plain.heal
        }
        alice.casts(Plain.doom, target = clerk)
        pay()
        resolveAll()
        expect {
            alice.hand has Plain.study
            alice.hand has Plain.heal
        }
    }

    // CR 400.7, 610.3 (sibling of chunk 05's "it" after a return): "it" after exiling and returning is the returned permanent
    @Test
    fun flickeredCreatureGetsTheCounter() = scenario(players = 3) {
        alice.battlefield(Plain.bear, "Plains", "Plains")
        alice.hand(drill)
        alice.casts(drill, target = Plain.bear)
        pay()
        resolveAll()
        expect {
            Plain.bear.zone shouldBe Zone.Battlefield
            Plain.bear.counters(p1p1) shouldBe 1
        }
    }

    // CR 608.2c, 111.1 (sibling of chunk 16's pay-then-token): "it" after "If you do, create a token" is the token
    @Test
    fun sacrificeThenTokenCounterGoesOnTheToken() = scenario(players = 3) {
        alice.battlefield(Plain.elf, "Swamp", "Swamp", "Swamp")
        alice.hand(barter)
        alice.casts(barter)
        pay()
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses(Plain.elf)
        resolveAll()
        expect {
            alice.graveyard has Plain.elf
            "Zombie Token".counters(p1p1) shouldBe 1
            barter.counters(p1p1) shouldBe 0
        }
    }

    // CR 603.4, 708.2a, 400.7 (sibling of the fix chunk's history as of the event): a creature that entered face down, was turned face up
    // and then died still entered face down this turn
    @Test
    fun aFaceDownArrivalThatDiedStillCounts() = scenario(players = 3) {
        alice.battlefield(witness, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Swamp")
        alice.hand(visitor, Plain.doom)
        libraries()
        alice.casts(visitor, way = alternativeOf(visitor, Keyword.Disguise.Kind))
        pay()
        resolveAll()
        perform(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().single { it.source == card(visitor) })
        pay()
        assertFalse(state.objOrThrow(card(visitor)).faceDown)
        alice.casts(Plain.doom, target = visitor)
        pay()
        resolveAll()
        expect { alice.graveyard has visitor }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { witness.counters(p1p1) shouldBe 1 }
    }

    // CR 611.2b, 400.7 (sibling of the fix chunk's "remains tapped"): "for as long as you control this creature" ends once it has left
    // the battlefield, and control goes back to the opponent it was taken from (3 players)
    @Test
    fun controlForAsLongAsYouControlItEndsWhenItDies() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island", "Swamp")
        alice.hand(keeper, Plain.doom)
        carol.battlefield(Plain.giant)
        alice.casts(keeper)
        pay()
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(Plain.giant))))
        resolveAll()
        expect { alice.battlefield has Plain.giant }
        alice.casts(Plain.doom, target = keeper)
        pay()
        resolveAll()
        expect { carol.battlefield has Plain.giant }
    }
}
