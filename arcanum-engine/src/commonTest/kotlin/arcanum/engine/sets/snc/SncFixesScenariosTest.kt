package arcanum.engine.sets.snc

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dft.targets
import kotlin.test.Test

/** Streets of New Capenna fixes in play: shield counters beside regeneration, damage marked and state-based actions. */
class SncFixesScenariosTest {
    private val shield = CounterKind.Shield
    private val brute = Cards.invent("SncFix Dockside Brute", "{2}{G}", "Creature — Ogre", "", "3/3")
    private val troll = Cards.invent("SncFix Sewer Troll", "{2}{G}", "Creature — Troll", "{G}: Regenerate this creature.", "3/3")
    private val jolt = Cards.invent("SncFix Alley Jolt", "{R}", "Instant", "SncFix Alley Jolt deals 2 damage to target creature.")
    private val ward = Cards.invent("SncFix Bodyguard's Oath", "{W}", "Instant", "Put a shield counter on target creature.")
    private val doom = Cards.invent("SncFix Quiet Doom", "{B}", "Instant", "Destroy target creature.")
    private val wither = Cards.invent("SncFix Withering Whisper", "{B}", "Instant", "Target creature gets -1/-1 until end of turn.")
    private val bear = Cards.invent("SncFix Alley Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val cub = Cards.invent("SncFix Alley Cub", "{G}", "Creature — Bear", "", "1/1")
    private val adversary = Cards.invent("SncFix Ob Vexilis, the Contrarian", "{1}{B}{R}", "Legendary Planeswalker — Nixilis", """
        Casualty X. The copy isn't legendary and has starting loyalty X. (As you cast this spell, you may sacrifice a creature with power X. When you do, copy this spell. The copy becomes a token.)
        [+1]: You gain 2 life.
    """, loyalty = "3")
    private val goad = Cards.invent("SncFix Taunting Heckle", "{R}", "Instant", "Target creature attacks during its controller's next combat phase if able.")
    private val detour = Cards.invent("SncFix Endless Roundabout", "{G}{W}{U}", "Instant",
        "The owner of target spell, nonland permanent, or card in a graveyard puts it on their choice of the top or bottom of their library.")

    /** A 3/3 with 2 damage marked, then given a shield counter. */
    private fun Scenario.damagedThenShielded() {
        alice.battlefield(brute, "Mountain", "Plains", "Swamp")
        alice.hand(jolt, ward, doom, wither)
        alice.casts(jolt, target = brute)
        resolveAll()
        alice.casts(ward, target = brute)
        resolveAll()
    }

    // CR 122.1c: the shield counter's replacement is only "instead remove a shield counter from it": unlike regeneration (CR 701.19a)
    // the damage marked stays
    @Test
    fun shieldCounterReplacingDestructionLeavesTheDamageMarked() = scenario {
        damagedThenShielded()
        alice.casts(doom, target = brute)
        resolveAll()
        expect {
            alice.battlefield has brute
            card(brute).counters(shield) shouldBe 0
            brute.view.obj?.damage shouldBe 2
        }
    }

    // CR 122.1c, 701.8b, 704.5g: destruction for lethal damage is a state-based action, not the result of an effect: the shield counter
    // doesn't replace it
    @Test
    fun shieldCounterDoesNotReplaceDestructionByStateBasedActions() = scenario {
        damagedThenShielded()
        alice.casts(wither, target = brute)
        resolveAll()
        expect { alice.graveyard has brute }
    }

    /** A Troll with a shield counter and a regeneration shield, then destroyed: its controller chooses which replacement applies. */
    private fun Scenario.shieldedTrollRegeneratingIsDestroyed(): Decision.ChooseReplacement {
        alice.battlefield(troll, "Forest", "Swamp", counters = listOf(shield to 1))
        alice.hand(doom)
        alice.activates(troll)
        resolveAll()
        alice.casts(doom, target = troll)
        resolveAll()
        val choice = decision<Decision.ChooseReplacement>()
        expect { choice.player shouldBe alice.id }
        return choice
    }

    // CR 616.1e, 122.1c, 701.19a: a shield counter and a regeneration shield both replace the destruction; the affected permanent's
    // controller chooses: regenerating keeps the shield counter
    @Test
    fun regenerationChosenFirstKeepsTheShieldCounter() = scenario {
        val choice = shieldedTrollRegeneratingIsDestroyed()
        answer(Answer.Replacement(choice.options.first { it.text != "Remove a shield counter instead" }.index))
        resolveAll()
        expect {
            alice.battlefield has troll
            card(troll).counters(shield) shouldBe 1
            troll.tapped shouldBe true
        }
    }

    // CR 616.1e, 122.1c: choosing the shield counter uses it up and leaves the regeneration shield for later
    @Test
    fun shieldCounterChosenFirstKeepsTheRegenerationShield() = scenario {
        val choice = shieldedTrollRegeneratingIsDestroyed()
        answer(Answer.Replacement(choice.options.first { it.text == "Remove a shield counter instead" }.index))
        resolveAll()
        expect {
            alice.battlefield has troll
            card(troll).counters(shield) shouldBe 0
            troll.tapped shouldBe false
        }
    }

    // CR 122.1c, 510.2, 615.13: combat damage from two blockers is one damage event; the shield counters' one prevention effect prevents
    // all of it and removes a single shield counter
    @Test
    fun damageFromTwoBlockersAtOnceRemovesOneShieldCounter() = scenario(players = 3) {
        alice.battlefield(brute, counters = listOf(shield to 2))
        bob.battlefield(bear, cub)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(brute, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bear to brute, cub to brute)
        var guard = 20
        while (state.turn.step != TurnStep.EndOfCombat && guard-- > 0) {
            if (pending is Decision.Divide) divideAsSuggested() else passPriority()
        }
        expect {
            alice.battlefield has brute
            card(brute).counters(shield) shouldBe 1
            brute.view.obj?.damage shouldBe 0
        }
    }

    // CR 608.2b, 401.4, 400.3: a spell on the stack is a legal target; as Detour resolves the spell's owner chooses top or bottom and the
    // spell goes there (it never resolves)
    @Test
    fun detourPutsATargetSpellOnItsOwnersLibrary() = scenario(players = 3) {
        turn(number = 2, active = bob)
        bob.hand(bear)
        bob.battlefield("Forest", "Forest")
        bob.library("Plains")
        alice.hand(detour)
        alice.battlefield("Forest", "Plains", "Island")
        bob.casts(bear)
        bob.passes()
        carol.passes()
        alice.casts(detour, target = bear)
        resolveAll()
        val choice = decision<Decision.ChooseOption>()
        expect { choice.player shouldBe bob.id }
        answer(Answer.Option("bottom"))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Library
            bob.library.size shouldBe 2
            state.stack.isEmpty() shouldBe true
        }
    }

    // CR 508.1d, 611.2a: made during its controller's own declare attackers step (after attackers are declared), the requirement is for
    // that player's next combat phase, not the one under way
    @Test
    fun mustAttackMadeDuringItsControllersDeclareAttackersStepLastsToTheirNextCombat() = scenario(players = 3) {
        turn(number = 2, active = bob)
        bob.battlefield(bear)
        alice.battlefield("Mountain")
        alice.hand(goad)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        carol.library("Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks()
        bob.passes()
        carol.passes()
        alice.casts(goad, target = bear)
        resolveAll()
        advanceTo(TurnStep.EndOfCombat)
        expect { bear hasMechanic { it is arcanum.api.Mechanic.MustAttack } }
        advanceTo(TurnStep.DeclareAttackers, turn = 5)
        kotlin.test.assertFailsWith<IllegalArgumentException> { bob.attacks() }
        bob.attacks(bear, defender = "carol")
        advanceTo(TurnStep.EndOfCombat)
        expect { bear lacksMechanic { it is arcanum.api.Mechanic.MustAttack } }
    }

    // CR 702.153a, 107.3, 707.9b, 707.10c, 306.5b: sacrificing a 2/2 for casualty X makes X 2; the copy becomes a token that isn't legendary
    // (no legend rule, CR 704.5j) and enters with two loyalty counters, the original with its printed three
    @Test
    fun casualtyXCopyIsANonlegendaryTokenWithLoyaltyX() = scenario {
        alice.battlefield(bear, "Swamp", "Mountain", "Mountain")
        alice.hand(adversary)
        alice.casts(adversary, kicked = 1, choosing = listOf(bear))
        resolveAll()
        val walkers = state.battlefield.filter { state.obj(it)?.definition?.faces?.first()?.name == adversary }
        expect {
            alice.graveyard has bear
            walkers.size shouldBe 2
            walkers.map { state.obj(it)!!.counters(CounterKind.Loyalty) }.sorted() shouldBe listOf(2, 3)
            walkers.count { state.obj(it)!!.token } shouldBe 1
            walkers.count { id -> arcanum.engine.Engine.view(state)[id]?.characteristics?.supertypes.orEmpty().contains(arcanum.api.Supertype.Legendary) } shouldBe 1
        }
    }

    private val patron = Cards.invent("SncFix Gang Patron", "{4}{W}", "Legendary Creature — Human Hero",
        "Whenever you put one or more +1/+1 counters on one or more other Heroes you control, you gain 1 life.", "2/5")
    private val watcher = Cards.invent("SncFix Scale Watcher", "{1}{G}", "Creature — Human Druid",
        "Whenever one or more counters are put on a creature you control, you gain 1 life.", "1/1")
    private val hero = Cards.invent("SncFix Street Hero", "{1}{W}", "Creature — Human Hero", "", "2/2")
    private val rally = Cards.invent("SncFix Rally the Family", "{1}{G}", "Sorcery", "Put a +1/+1 counter on each creature you control.")
    private val supremacy = Cards.invent("SncFix Dealers Supremacy", "{G}{W}{U}", "Enchantment",
        "At the beginning of your end step, put a +1/+1 counter on each creature you control and a loyalty counter on each planeswalker you control.")

    // CR 122.6, 603.2c, 608.2c: one instruction puts its counters on every creature at once, one event: "on one or more other Heroes"
    // triggers once for it
    @Test
    fun countersOnSeveralHeroesAtOnceTriggerOneOrMoreOnce() = scenario {
        alice.battlefield(patron, hero, hero, "Forest", "Forest")
        alice.hand(rally)
        alice.casts(rally)
        resolveAll()
        expect {
            alice.life shouldBe 21
            card(hero).counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 122.6, 603.2c: the counters of "put a +1/+1 counter on each creature … and a loyalty counter on each planeswalker" are one
    // event, so the Heroes' trigger is once
    @Test
    fun brokersCountersAreOneEventForOneOrMoreHeroes() = scenario {
        alice.battlefield(supremacy, patron, hero, hero)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    // CR 603.2c: one event with counters put on three creatures holds three occurrences of "counters are put on a creature you
    // control": once for each creature
    @Test
    fun countersOnSeveralCreaturesAtOnceTriggerOncePerCreature() = scenario(players = 3) {
        alice.battlefield(watcher, hero, hero, "Forest", "Forest")
        bob.battlefield(hero)
        alice.hand(rally)
        alice.casts(rally)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    private val fountain = Cards.invent("SncFix Aureole Wellspring", "{2}{W}", "Artifact", """
        {W}, {T}, Untap a tapped creature you control: Create a 1/1 green and white Citizen creature token.
        {W}{W}, {T}, Untap two tapped creatures you control: Draw a card.
    """)
    private val magpie = Cards.invent("SncFix Lavinia, the Pilferer", "{G}{W}{U}", "Legendary Creature — Human Soldier",
        "When SncFix Lavinia, the Pilferer enters, exile any number of other target creatures controlled by different players until SncFix Lavinia, the Pilferer leaves the battlefield. When an exiled card enters under your control this way, put two +1/+1 counters on it.", "2/3")

    // CR 118.3, 701.26a: untapping two tapped creatures you control is part of the cost; they are untapped as it is paid
    @Test
    fun untappingTappedCreaturesPaysTheCost() = scenario {
        alice.battlefield(fountain, "Plains", "Plains")
        alice.battlefield(bear, cub, tapped = true)
        alice.library("Island")
        alice.activates(fountain, ability = 1, choosing = listOf(bear, cub))
        resolveAll()
        expect {
            bear.tapped shouldBe false
            cub.tapped shouldBe false
            alice.hand has "Island"
        }
    }

    // CR 118.3: an untapped creature can't be untapped to pay the cost, so with one tapped creature it can't be paid
    @Test
    fun untapCostNeedsTappedCreatures() = scenario {
        alice.battlefield(fountain, "Plains", "Plains", bear)
        alice.battlefield(cub, tapped = true)
        kotlin.test.assertFailsWith<Throwable> { alice.activates(fountain, ability = 1, choosing = listOf(bear, cub)) }
    }

    // CR 601.2c: the targets must be controlled by different players (any number of players); CR 610.3: they return when it leaves; CR 603.12:
    // only the card returning under its controller's control gets the counters
    @Test
    fun magpieExilesOnePerPlayerAndCountersWhatReturnsToYou() = scenario(players = 3) {
        alice.battlefield("Forest", "Plains", "Island", "Swamp", hero)
        alice.hand(magpie, doom)
        bob.battlefield(bear)
        carol.battlefield(cub)
        alice.casts(magpie)
        resolveAll()
        targets(hero, bear, cub)
        resolveAll()
        expect {
            alice.exile has hero
            bob.exile has bear
            carol.exile has cub
        }
        alice.casts(doom, target = magpie)
        resolveAll()
        expect {
            alice.battlefield has hero
            bob.battlefield has bear
            carol.battlefield has cub
            card(hero).counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            card(bear).counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 601.2c: two creatures of the same player can't both be targets
    @Test
    fun magpieRefusesTwoTargetsOfOnePlayer() = scenario(players = 3) {
        alice.battlefield("Forest", "Plains", "Island")
        alice.hand(magpie)
        bob.battlefield(bear, cub)
        alice.casts(magpie)
        resolveAll()
        kotlin.test.assertFailsWith<Throwable> { targets(bear, cub) }
    }
}
