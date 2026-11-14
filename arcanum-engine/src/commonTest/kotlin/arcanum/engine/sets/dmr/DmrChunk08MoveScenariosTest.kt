package arcanum.engine.sets.dmr

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.aliceUpkeep
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Dominaria Remastered chunk 08 in play: a creature bounced by an enters ability among three players (CR 400.3), a creature that must be
 * paid for with a discard or exiled (CR 118.12a), vanishing with an enters-or-leaves return (CR 702.63a, 603.6c), all other permanents
 * exiled and returned when the dragon leaves (CR 607.2a, 610.3), a card shuffled into its library instead of dying (CR 614.1a, 701.24),
 * and a card returned from the graveyard by paying in the upkeep (CR 603.4, 118.12).
 */
class DmrChunk08MoveScenariosTest {
    private val jelly = Cards.invent("Tide Jelly", "{2}{U}", "Creature — Jellyfish", "When this creature enters, return target creature to its owner's hand.", "2/2")
    private val snatcher = Cards.invent("Husk Reaver", "{2}{B}{B}", "Creature — Phyrexian Minion", """
        When this creature enters, exile it unless you discard a creature card.
        When this creature dies, exile it and return target creature card from your graveyard to the battlefield.
    """, "2/2")
    private val elder = Cards.invent("Thornbark Elder", "{5}{G}", "Creature — Treefolk", """
        Vanishing 3 (This creature enters with three time counters on it. At the beginning of your upkeep, remove a time counter from it. When the last is removed, sacrifice it.)
        When this creature enters or leaves the battlefield, return another target creature card from your graveyard to your hand.
    """, "3/6")
    private val relic = Cards.invent("Prismatic Relic", "{7}", "Legendary Artifact", """
        {W}{U}{B}{R}{G}: Exile target permanent.
        If Prismatic Relic would be put into a graveyard from anywhere, reveal Prismatic Relic and shuffle it into its owner's library instead.
    """)
    private val wyrm = Cards.invent("Ember Wyrm", "{3}{R}{R}{R}", "Creature — Nightmare Dragon", """
        Flying, trample
        When this creature enters, exile all other permanents you control.
        When this creature leaves the battlefield, return the exiled cards to the battlefield under their owners' control.
    """, "7/7")
    private val zombie = Cards.invent("Cinder Revenant", "{1}{B}{R}", "Creature — Zombie", """
        At the beginning of your upkeep, if this card is in your graveyard, you may pay {1}{B}{B}. If you do, return it to your hand.
        {1}{R}{R}, Sacrifice this creature: It deals 2 damage to any target.
    """, "2/1")
    private val shatter = Cards.invent("Scrap Bolt", "{R}", "Instant", "Destroy target artifact.")
    private val verdict = Cards.invent("Final Verdict", "{B}", "Instant", "Destroy target creature.")
    private val altar = Cards.invent("Charnel Altar", "{1}", "Artifact", "Sacrifice a creature: You gain 1 life.")
    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val wolf = Cards.invent("Alley Wolf", "{1}{G}", "Creature — Wolf", "", "3/3")

    // CR 400.3, 603.3d: the target is chosen among creatures of three players; a creature goes to its owner's hand
    @Test
    fun jellyBouncesTheChosenCreatureOfAnotherPlayer() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(jelly)
        bob.battlefield(bear)
        carol.battlefield(wolf)
        alice.casts(jelly)
        resolveAll()
        targets(wolf)
        finish()
        expect {
            carol.hand has wolf
            bob.battlefield has bear
            alice.battlefield has jelly
        }
    }

    // CR 118.12a: a creature card discarded, the Reaver stays
    @Test
    fun reaverStaysWhenACreatureCardIsDiscarded() = scenario {
        alice.hand(snatcher, bear)
        alice.castsWithoutCosts(snatcher)
        resolve()
        resolve()
        yes()
        if (pending != null) alice.chooses(bear)
        expect {
            alice.battlefield has snatcher
            alice.graveyard has bear
        }
    }

    // CR 118.12a: nothing discarded, the Reaver is exiled
    @Test
    fun reaverIsExiledWithoutTheDiscard() = scenario {
        alice.hand(snatcher, bear)
        alice.castsWithoutCosts(snatcher)
        resolve()
        resolve()
        answer(Answer.YesNo(false))
        resolveAll()
        expect {
            alice.exile has snatcher
            alice.hand has bear
        }
    }

    // CR 603.10a, 700.4: when it dies it exiles itself and returns a creature card from the graveyard to the battlefield
    @Test
    fun reaverDyingExilesItselfAndReturnsACreature() = scenario {
        alice.battlefield(snatcher, altar)
        alice.graveyard(wolf)
        alice.activates(altar, choosing = listOf(snatcher))
        resolveAll()
        if (pending is Decision.Targets) targets(wolf)
        finish()
        expect {
            alice.exile has snatcher
            alice.battlefield has wolf
        }
    }

    // CR 702.63a, 603.6c: three time counters; the enters trigger returns a creature card, and leaving returns another
    @Test
    fun elderVanishesAndReturnsCreatureCardsOnEnteringAndLeaving() = scenario {
        alice.battlefield(altar, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(elder)
        alice.graveyard(bear, wolf)
        alice.casts(elder)
        resolveAll()
        if (pending is Decision.Targets) targets(bear)
        resolveAll()
        expect {
            elder.counters(CounterKind.Time) shouldBe 3
            alice.hand has bear
        }
        alice.activates(altar, choosing = listOf(elder))
        resolveAll()
        if (pending is Decision.Targets) targets(wolf)
        finish()
        expect {
            alice.hand has wolf
            alice.graveyard has elder
        }
    }

    // CR 614.1a, 701.24: destroyed, the Relic is revealed and shuffled into its owner's library, not put in a graveyard
    @Test
    fun relicIsShuffledIntoTheLibraryInsteadOfDying() = scenario {
        alice.battlefield(relic)
        alice.library("Forest")
        bob.battlefield("Mountain")
        bob.hand(shatter)
        alice.passes()
        bob.casts(shatter, target = relic)
        resolveAll()
        expect {
            alice.library has relic
            alice.graveyard lacks relic
            alice.battlefield lacks relic
        }
    }

    // CR 602.2: five colours of mana exile any permanent, an opponent's among three players
    @Test
    fun relicExilesTargetPermanent() = scenario(players = 3) {
        alice.battlefield(relic, "Plains", "Island", "Swamp", "Mountain", "Forest")
        carol.battlefield(wolf)
        alice.activates(relic, target = wolf)
        resolveAll()
        expect {
            carol.exile has wolf
            alice.battlefield has relic
        }
    }

    // CR 607.2a, 610.3, 108.3: the exiled permanents return to their owners when the Wyrm leaves; the Wyrm itself and others' permanents stay
    @Test
    fun wyrmExilesYourOtherPermanentsAndReturnsThemWhenItLeaves() = scenario(players = 3) {
        alice.battlefield(bear, altar)
        bob.battlefield(wolf)
        alice.hand(wyrm)
        bob.battlefield("Swamp")
        bob.hand(verdict)
        alice.castsWithoutCosts(wyrm)
        resolveAll()
        expect {
            alice.exile has bear
            alice.exile has altar
            alice.battlefield has wyrm
            bob.battlefield has wolf
        }
        alice.passes()
        bob.casts(verdict, target = wyrm)
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.battlefield has altar
            alice.graveyard has wyrm
        }
    }

    // CR 603.4, 118.12: in the graveyard at the upkeep, the owner may pay and return the card to hand
    @Test
    fun zombieReturnsFromTheGraveyardWhenPaidInTheUpkeep() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.graveyard(zombie)
        aliceUpkeep()
        resolveAll()
        yes()
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        finish()
        expect {
            alice.hand has zombie
            alice.graveyard lacks zombie
        }
    }

    // CR 118.12: not paid, it stays in the graveyard
    @Test
    fun zombieStaysWhenNotPaid() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.graveyard(zombie)
        aliceUpkeep()
        resolveAll()
        answer(Answer.YesNo(false))
        finish()
        expect { alice.graveyard has zombie }
    }

    // CR 602.2, 701.21a: sacrificed as a cost; it deals 2 damage to any target
    @Test
    fun zombieSacrificesItselfToDealDamage() = scenario {
        alice.battlefield(zombie, "Mountain", "Mountain", "Mountain")
        bob.battlefield(bear)
        alice.activates(zombie, target = bear)
        resolveAll()
        expect {
            alice.graveyard has zombie
            bear.zone shouldBe Zone.Graveyard
        }
    }

    /** Resolves what is on the Stack; a "you may" it reaches is answered [yes]. */
    private fun Scenario.finish(yes: Boolean = true) {
        var guard = 30
        while (guard-- > 0) {
            if (pending is Decision.May) may(yes)
            if (pending != null || state.stack.isEmpty()) break
            allPass()
        }
    }

    private fun Scenario.yes() = answer(Answer.YesNo(true))
}
