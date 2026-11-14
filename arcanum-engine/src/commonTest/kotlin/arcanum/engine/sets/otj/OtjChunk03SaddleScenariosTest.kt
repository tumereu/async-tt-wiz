package arcanum.engine.sets.otj

import arcanum.api.CounterKind
import arcanum.api.ObjectMark
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.chooseNone
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dft.choose
import arcanum.engine.sets.dft.libraries
import arcanum.engine.sets.ltr.tokens
import arcanum.engine.state.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OTJ chunk 03: Mounts in play: "creature(s) that saddled it this turn" (tapped to pay its saddle cost, CR 702.171c), "this creature is
 * saddled" checked as the ability resolves (CR 702.171b), a repeated copy-and-attack, an end-of-combat blink and "that much" after
 * "Otherwise".
 */
class OtjChunk03SaddleScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val hound = Cards.invent("Trail Hound", "{2}{G}", "Creature — Dog", "", "3/3")
    private val mule = Cards.invent("Idle Mule", "{1}{W}", "Creature — Ox", "", "2/2")
    private val maud = Cards.invent("Famed Rancher Maud", "{1}{W}", "Legendary Creature — Human", "", "2/2")
    private val coyote = Cards.invent("Corral Coyote", "{1}{G}", "Creature — Coyote", "", "2/2")
    private val giant = Cards.invent("Prairie Giant", "{2}{G}", "Creature — Giant", "", "3/3")
    private val bolt = Cards.invent("Dust Devil Bolt", "{1}{R}", "Instant", "Target player loses 2 life.")

    private val newt = Cards.invent("Scrollkeeper Newt", "{1}{U}", "Creature — Salamander Mount", """
        Whenever this creature deals combat damage to a player, target instant or sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost. That card gains flashback {0} until end of turn instead if this creature is saddled.
        Saddle 3
    """.trimIndent(), "2/2")
    private val duskcat = Cards.invent("Leaping Duskcat", "{5}{W}", "Creature — Cat Beast Mount", """
        Whenever this creature attacks while saddled, put a +1/+1 counter on each other creature you control. You gain 1 life for each of those creatures.
        Saddle 2
    """.trimIndent(), "4/7")
    private val ruin = Cards.invent("Ruin, Thundering Stallion", "{4}{R}{R}", "Legendary Creature — Horse Mount", """
        Haste
        Whenever Ruin attacks while saddled, choose a nonlegendary creature that saddled it this turn and create a tapped and attacking token that's a copy of it. Sacrifice that token at the beginning of the next end step. Repeat this process once.
        Saddle 1
    """.trimIndent(), "4/6")
    private val bronco = Cards.invent("Venomhide Bronco", "{1}{B}", "Creature — Snake Horse Mount", """
        Whenever this creature attacks, reveal the top card of your library and put it into your hand. You lose life equal to that card's mana value if this creature isn't saddled. Otherwise, each opponent loses that much life.
        Saddle 3
    """.trimIndent(), "2/2")
    private val providence = Cards.invent("Providence, Faithful Mare", "{2}{W}", "Legendary Creature — Beast Mount", """
        When Providence enters, scry 2.
        Whenever Providence attacks while saddled, at end of combat, exile it and up to one creature that saddled it this turn, then return those cards to the battlefield under their owner's control.
        Saddle 1
    """.trimIndent(), "2/4")
    private val beaver = Cards.invent("Riverdam Beaver", "{3}{G}", "Creature — Beaver Mount", """
        Vigilance
        Whenever this creature attacks while saddled, put a +1/+1 counter on target creature that saddled it this turn.
        Saddle 3
    """.trimIndent(), "4/4")
    private val possum = Cards.invent("Wandering Possum", "{2}{G}", "Creature — Possum Mount", """
        Whenever this creature attacks while saddled, it gets +1/+2 until end of turn. Then you may return any number of creatures that saddled it this turn to their owner's hand.
        Saddle 1
    """.trimIndent(), "3/3")
    private val bogmaw = Cards.invent("The Bogmaw, Ravening Mount", "{3}{B}{G}", "Legendary Creature — Frog Horror Mount", """
        Trample, haste
        Whenever The Bogmaw deals combat damage to a player, you may sacrifice a creature that saddled it this turn. If you do, draw X cards, then put up to X land cards from your hand onto the battlefield tapped, where X is the sacrificed creature's power.
        Saddle 1
    """.trimIndent(), "6/5")

    /** Alice saddles [mount] with [riders] in her main phase and attacks with it; the attack triggers are put on the Stack. */
    private fun Scenario.saddleAndAttack(mount: String, vararg riders: String) {
        alice.activates(mount, choosing = riders.toList())
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(mount)
    }

    /** Nobody blocks; play goes on to [step]. */
    private fun Scenario.unblockedTo(step: TurnStep) {
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == null) bob.blocks()
        advanceTo(step)
    }

    // CR 702.171c: only the creature tapped to saddle it is a legal target; the other creature isn't
    @Test
    fun beaverPutsTheCounterOnTheCreatureThatSaddledIt() = scenario {
        alice.battlefield(beaver, hound, mule)
        saddleAndAttack(beaver, hound)
        resolveAll()
        expect {
            hound.counters(p1p1) shouldBe 1
            mule.counters(p1p1) shouldBe 0
        }
    }

    // CR 702.171c, 107.1: any number of the creatures that saddled it, here one of two, return to hand after the pump
    @Test
    fun possumReturnsTheChosenSaddlersToHand() = scenario {
        alice.battlefield(possum, hound, mule)
        saddleAndAttack(possum, hound, mule)
        resolveAll()
        choose(hound)
        expect {
            alice.hand has hound
            mule.zone shouldBe Zone.Battlefield
            possum.power shouldBe 4
            possum.toughness shouldBe 5
        }
    }

    // CR 702.171c, 707.2, 508.4, 603.7: two attacking copies of the nonlegendary saddler (the legendary one can't be chosen), both
    // sacrificed at the next end step
    @Test
    fun ruinCopiesTheNonlegendarySaddlerTwiceAndSacrificesTheCopies() = scenario {
        alice.battlefield(ruin, hound, maud)
        saddleAndAttack(ruin, hound, maud)
        resolveAll()
        choose(hound)
        choose(hound)
        expect {
            tokens(hound).size shouldBe 2
            tokens(hound).all { state.obj(it)?.tapped == true } shouldBe true
            tokens(hound).all { t -> state.combat?.attackers?.any { it.id == t } == true } shouldBe true
            tokens(maud).size shouldBe 0
        }
        unblockedTo(TurnStep.End)
        resolveAll()
        expect {
            bob.life shouldBe 20 - 4 - 3 - 3
            tokens(hound).size shouldBe 0
            hound.zone shouldBe Zone.Battlefield
        }
    }

    // CR 702.171b, 608.2h: not saddled: Alice loses the revealed card's mana value
    @Test
    fun broncoUnsaddledCostsAliceLife() = scenario(players = 3) {
        alice.battlefield(bronco)
        alice.library(giant, "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bronco, defender = "bob")
        resolveAll()
        expect {
            alice.hand has giant
            alice.life shouldBe 17
            bob.life shouldBe 20
            carol.life shouldBe 20
        }
    }

    // CR 702.171b: saddled: each opponent loses that much life (the same mana value), Alice none
    @Test
    fun broncoSaddledDrainsEachOpponent() = scenario(players = 3) {
        alice.battlefield(bronco, hound)
        alice.library(giant, "Forest")
        alice.activates(bronco, choosing = listOf(hound))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bronco, defender = "bob")
        resolveAll()
        expect {
            alice.hand has giant
            alice.life shouldBe 20
            bob.life shouldBe 17
            carol.life shouldBe 17
        }
    }

    // CR 603.7, 400.7: at end of combat the Mount and the creature that saddled it leave and return as new, untapped, unsaddled objects;
    // the returning Mount's enters trigger scries
    @Test
    fun providenceBlinksItselfAndItsSaddlerAtEndOfCombat() = scenario {
        libraries()
        alice.battlefield(providence, hound, mule)
        saddleAndAttack(providence, hound)
        resolveAll()
        unblockedTo(TurnStep.EndOfCombat)
        resolveAll()
        choose(hound)
        chooseNone()
        resolveAll()
        expect {
            bob.life shouldBe 18
            providence.zone shouldBe Zone.Battlefield
            providence.tapped shouldBe false
            providence.view.obj?.marks?.contains(ObjectMark.Saddled) shouldBe false
            hound.zone shouldBe Zone.Battlefield
            hound.tapped shouldBe false
        }
    }

    // CR 702.171c, 608.2h: the sacrificed saddler's power is X: three cards drawn, then up to three lands put onto the battlefield tapped
    @Test
    fun bogmawSacrificesItsSaddlerToDrawAndPutLands() = scenario {
        alice.battlefield(bogmaw, hound)
        alice.library("Forest", "Forest", "Island", "Plains")
        bob.library("Swamp")
        saddleAndAttack(bogmaw, hound)
        resolveAll()
        unblockedTo(TurnStep.CombatDamage)
        resolveAll()
        // the lone creature that saddled it is sacrificed without a choice
        may(true)
        resolveAll()
        val lands = decision<Decision.ChooseObjects>()
        assertEquals(3, lands.max)
        // the cards drawn while the ability resolves are new objects (CR 400.7): picked by their card
        val forests = cards("Forest").map { (it.id as ObjectId.Card).card }.toSet()
        answer(Answer.Objects(lands.options.filter { (it as? ObjectId.Card)?.card in forests }))
        resolveAll()
        expect {
            bob.life shouldBe 14
            alice.graveyard has hound
            alice.hand has "Island"
            alice.hand lacks "Forest"
            alice.library.names shouldBe listOf("Plains")
            alice.battlefield.names.count { it == "Forest" } shouldBe 2
        }
    }

    // CR 702.34a, 702.171b: saddled, the instant gains flashback {0}: cast from the graveyard with no mana, then exiled
    @Test
    fun newtSaddledGivesFlashbackForNothing() = scenario {
        alice.battlefield(newt, hound)
        alice.graveyard(bolt)
        saddleAndAttack(newt, hound)
        unblockedTo(TurnStep.CombatDamage)
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        alice.casts(bolt, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 16
            alice.exile has bolt
        }
    }

    // CR 608.2c: each other creature Alice controls gets a counter and she gains 1 life for each of them; Bob's creature counts for neither
    @Test
    fun duskcatCountersEachOtherCreatureAndGainsLifeForThem() = scenario {
        alice.battlefield(duskcat, hound, mule)
        bob.battlefield(coyote)
        saddleAndAttack(duskcat, hound)
        resolveAll()
        expect {
            hound.counters(p1p1) shouldBe 1
            mule.counters(p1p1) shouldBe 1
            duskcat.counters(p1p1) shouldBe 0
            coyote.counters(p1p1) shouldBe 0
            alice.life shouldBe 22
        }
    }
}
