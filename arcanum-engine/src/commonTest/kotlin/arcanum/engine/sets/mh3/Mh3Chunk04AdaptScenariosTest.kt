package arcanum.engine.sets.mh3

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import arcanum.engine.view.AbilityOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The MH3 adapt cards of chunk 04, played: the invented names carry the oracle text of Cursed Wombat, Emperor of Bones, Hydra Trainer,
 * Expanding Ooze, Temperamental Oozewagg and Etherium Pteramander (costs cut where they don't change play).
 */
class Mh3Chunk04AdaptScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private val wombat = Cards.invent("Mossgrave Wombat", "{B}{G}", "Creature — Nightmare Wombat", """
        {2}{B}{G}: Adapt 2. (If this creature has no +1/+1 counters on it, put two +1/+1 counters on it.)
        Permanents you control have "Whenever one or more +1/+1 counters are put on this permanent, put an additional +1/+1 counter on it. This ability triggers only once each turn."
    """, "2/3")
    private val emperor = Cards.invent("Marrowcrown Emperor", "{1}{B}", "Creature — Skeleton Noble", """
        At the beginning of combat on your turn, exile up to one target card from a graveyard.
        {1}{B}: Adapt 2.
        Whenever one or more +1/+1 counters are put on this creature, put a creature card exiled with this creature onto the battlefield under your control with a finality counter on it. It gains haste. Sacrifice it at the beginning of the next end step.
    """, "2/2")
    private val trainer = Cards.invent("Ashpen Hydra Handler", "{1}{G}", "Creature — Human Warrior", """
        You may exert this creature as it attacks. When you do, target creature gets +X/+X until end of turn, where X is the number of counters on permanents you control. (An exerted creature won't untap during your next untap step.)
        {2}{G}: Adapt 2. (If this creature has no +1/+1 counters on it, put two +1/+1 counters on it.)
    """, "1/1")
    private val ooze = Cards.invent("Brambleback Ooze", "{1}{B}{G}", "Creature — Ooze", """
        {B}{G}: Adapt 1. (If this creature has no +1/+1 counters on it, put a +1/+1 counter on it.)
        Whenever this creature attacks, put a +1/+1 counter on target modified creature you control. (Equipment, Auras you control, and counters are modifications.)
    """, "3/3")
    private val wagg = Cards.invent("Gravelwagg Ooze", "{3}{G}", "Creature — Ooze Brushwagg", """
        {2}{G}: Adapt 2. (If this creature has no +1/+1 counters on it, put two +1/+1 counters on it.)
        Modified creatures you control have trample. (Equipment, Auras you control, and counters are modifications.)
    """, "4/4")
    private val drake = Cards.invent("Copperwing Pteramander", "{B}", "Artifact Creature — Salamander Drake", """
        Flying
        This creature can block only creatures with flying.
        {6}{B}: Adapt 4. This ability costs {1} less to activate for each other artifact you control. (If this creature has no +1/+1 counters on it, put four +1/+1 counters on it.)
    """, "1/1")
    private val bear = Cards.invent("Glassrib Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val flier = Cards.invent("Glassrib Drake", "{1}{U}", "Creature — Drake", "Flying", "2/2")
    private val trinket = Cards.invent("Glassrib Trinket", "{1}", "Artifact", "")
    private val boost = Cards.invent("Glassrib Boon", "{G}", "Instant", "Put a +1/+1 counter on target creature.")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 701.46a, 122.6, 603.2: adapt puts two counters (one event); the Wombat's granted trigger adds one more; that extra counter's own
    // event doesn't trigger again the same turn
    @Test
    fun wombatAddsOneCounterOnceEachTurn() = scenario {
        alice.battlefield(wombat, "Swamp", "Swamp", "Forest", "Forest")
        alice.activates(wombat)
        resolveAll()
        expect { wombat.counters(p1p1) shouldBe 3 }
    }

    // CR 113.10, 603.2: the grant covers every permanent you control, once each turn each; the opponent's creature isn't covered
    @Test
    fun wombatGrantsTheTriggerToOtherPermanentsOfYours() = scenario {
        alice.battlefield(wombat, bear, "Forest", "Forest")
        bob.battlefield(flier)
        alice.hand(boost, boost)
        alice.casts(boost, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 2 }
        alice.casts(boost, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 3 }
        expect { flier.counters(p1p1) shouldBe 0 }
    }

    // CR 603.2: with the Wombat gone nothing adds the extra counter
    @Test
    fun noExtraCounterForTheOpponentsCreature() = scenario {
        alice.battlefield(wombat, "Forest")
        bob.battlefield(bear)
        alice.hand(boost)
        alice.casts(boost, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    // CR 607.2a, 122.6, 122.1h, 702.10, 603.7, 400.7: exile a card in combat; adapting puts the creature card onto the battlefield with a
    // finality counter and haste; it is sacrificed at the next end step and, with the finality counter, exiled instead of dying. Any
    // player's graveyard works (3 players).
    @Test
    fun emperorReturnsAnExiledCreatureForOneTurn() = scenario(players = 3) {
        alice.battlefield(emperor, "Swamp", "Swamp")
        carol.graveyard(bear)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(bear))))
        resolveAll()
        expect { carol.exile has bear }
        alice.activates(emperor)
        resolveAll()
        expect {
            alice.battlefield has bear
            bear.counters(CounterKind.Finality) shouldBe 1
            bear hasKeyword Keyword.Haste.kind
            emperor.counters(p1p1) shouldBe 2
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield lacks bear
            carol.graveyard lacks bear
            carol.exile has bear
        }
    }

    // CR 608.2b: nothing was exiled with it (a non-creature card, or none): adapting gives nothing back
    @Test
    fun emperorWithNothingExiledReturnsNothing() = scenario {
        alice.battlefield(emperor, "Swamp", "Swamp")
        alice.activates(emperor)
        resolveAll()
        expect {
            emperor.counters(p1p1) shouldBe 2
            alice.battlefield.names.count { it == "Glassrib Bear" } shouldBe 0
        }
    }

    // CR 701.43d, 603.12, 122.1: exerting as it attacks; X counts counters of every kind on permanents its controller controls
    @Test
    fun trainerGivesPlusXForEveryCounterYouControl() = scenario {
        alice.battlefield(trainer)
        alice.battlefield(bear, counters = listOf(p1p1 to 2, CounterKind.Named("shield") to 1))
        bob.battlefield(flier, counters = listOf(p1p1 to 4))
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(trainer), TargetRef.Player(bob.id), paying = listOf(AbilityOrigin.Printed(0, 0))))))
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(trainer))))
        resolveAll()
        expect { trainer.power shouldBe 4 }
    }

    // CR 508.1g: attacking without exerting gives nothing
    @Test
    fun trainerWithoutExertingGivesNothing() = scenario {
        alice.battlefield(trainer)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(trainer)
        resolveAll()
        expect { trainer.power shouldBe 1 }
    }

    // CR 700.9: only a modified creature you control can be the target; the Ooze with no counters isn't one, a creature with a counter is
    @Test
    fun oozeTargetsOnlyModifiedCreatures() = scenario {
        alice.battlefield(ooze)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.battlefield(flier)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ooze)
        val d = pending
        if (d is Decision.Targets) {
            assertEquals(1, d.options.size)
            answer(Answer.Targets(d.options))
        }
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 2
            flier.counters(p1p1) shouldBe 0
        }
    }

    // CR 700.9, 122.6: once the Ooze itself has a counter (adapted) it is a modified creature and can target itself
    @Test
    fun oozeIsModifiedOnceItAdapts() = scenario {
        alice.battlefield(ooze, "Swamp", "Forest")
        alice.activates(ooze)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ooze)
        resolveAll()
        expect { ooze.counters(p1p1) shouldBe 2 }
    }

    // CR 700.9, 613.1f: modified creatures you control have trample; a plain one and the opponent's don't
    @Test
    fun waggGivesTrampleToModifiedCreaturesYouControl() = scenario {
        alice.battlefield(wagg)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.battlefield(flier)
        bob.battlefield(Cards.invent("Glassrib Ox", "{2}{G}", "Creature — Ox", "", "2/4"), counters = listOf(p1p1 to 1))
        expect {
            bear hasKeyword Keyword.Trample.kind
            flier lacksKeyword Keyword.Trample.kind
            wagg lacksKeyword Keyword.Trample.kind
            "Glassrib Ox" lacksKeyword Keyword.Trample.kind
        }
    }

    // CR 509.1b ("can block only creatures with flying"): a ground attacker can't be blocked by it, a flier can
    @Test
    fun pteramanderBlocksOnlyFliers() = scenario {
        alice.battlefield(bear)
        bob.battlefield(drake)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks("Copperwing Pteramander" to "Glassrib Bear") }
    }

    @Test
    fun pteramanderBlocksAFlier() = scenario {
        alice.battlefield(flier)
        bob.battlefield(drake)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(flier)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks("Copperwing Pteramander" to "Glassrib Drake")
    }

    // CR 601.2f, 118.7: the ability costs {1} less for each other artifact you control: {6}{B} with two others is {4}{B}
    @Test
    fun pteramanderAdaptCostsLessForEachOtherArtifact() = scenario {
        alice.battlefield(drake, trinket, trinket, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.activates(drake)
        resolveAll()
        expect { drake.counters(p1p1) shouldBe 4 }
    }

    // CR 601.2f: with no other artifact the full {6}{B} is due: five lands don't pay it
    @Test
    fun pteramanderAdaptCostsFullWithoutOtherArtifacts() = scenario {
        alice.battlefield(drake, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.activates(drake)
        assertTrue((decision<Decision.ManaPayment>()).suggestion.isEmpty())
    }
}
