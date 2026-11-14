package arcanum.engine.sets.mh3

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.CardRef
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.dft.hits
import arcanum.engine.sets.dft.payYes
import arcanum.engine.sets.ltr.alternativeOf
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test

/**
 * MH3 sweep: siblings of the bugs MH3 set work found, played on other paths (a bounced or sacrificed object beside a dying one, costs
 * beside effects, copies and face-down cards beside transformed ones). Invented names carry real oracle wordings.
 */
class Mh3SweepScenariosTest {
    private val runt = Cards.invent("Sweepreef Runt", "{1}{U}", "Creature — Merfolk", "", "2/2")
    private val vestment = Cards.invent("Sweepchapel Vestment", "{1}", "Artifact — Equipment", """
        Equipped creature gets +1/+0 and is a Cleric in addition to its other types.
        Equip {1}
    """)
    private val bolt = Cards.invent("Sweepcinder Bolt", "{R}", "Instant", "Sweepcinder Bolt deals 2 damage to any target.")

    // ---- Last known information of an object that left the battlefield (sibling of "If that creature was a Cleric" in a dies trigger)

    private val sentry = Cards.invent("Recallwing Sentry", "{2}{W}", "Creature — Bird Soldier", """
        When this creature enters, return up to one other target creature you control to its owner's hand. If it was a Cleric, put a +1/+1 counter on this creature.
    """, "2/2")

    // CR 608.2h: the effect moved it to a hidden zone: "If it was a Cleric" uses its last known information (a Cleric through Equipment)
    @Test
    fun bouncedThenIfItWasAClericUsesLastKnownInformation() = scenario {
        alice.battlefield(runt, vestment, "Plains", "Plains", "Plains")
        attach(vestment, to = runt)
        alice.hand(sentry)
        alice.casts(sentry)
        resolveAll()
        targets(runt)
        resolveAll()
        expect {
            alice.hand has runt
            card(sentry).counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    private val torturer = Cards.invent("Ashveil Torturer", "{2}{B}", "Creature — Vampire", """
        Sacrifice a creature: This creature gains flying until end of turn. If the sacrificed creature was a Cleric, put a +1/+1 counter on this creature.
    """, "3/1")

    // CR 608.2h, 602.2b: "the sacrificed creature" is as it last existed on the battlefield: a Cleric through Equipment
    @Test
    fun sacrificedCreatureWasAClericUsesLastKnownInformation() = scenario {
        alice.battlefield(torturer, runt, vestment)
        attach(vestment, to = runt)
        alice.activates(torturer, choosing = listOf(runt))
        resolveAll()
        expect {
            alice.graveyard has runt
            card(torturer).counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    private val mourner = Cards.invent("Chapelgrave Mourner", "{1}{W}", "Creature — Human", """
        Whenever another creature you control dies, if it was a Cleric, you gain 2 life.
    """, "1/3")

    // CR 603.4, 603.10a: the intervening "if it was a Cleric" looks at the creature as it last existed on the battlefield
    @Test
    fun diesInterveningIfItWasAClericUsesLastKnownInformation() = scenario {
        alice.battlefield(mourner, runt, vestment, "Mountain")
        attach(vestment, to = runt)
        alice.hand(bolt)
        alice.casts(bolt, target = runt)
        resolveAll()
        expect {
            alice.graveyard has runt
            alice.life shouldBe 22
        }
    }

    private val fling = Cards.invent("Hurlstone Fling", "{1}{R}", "Instant", """
        As an additional cost to cast this spell, sacrifice a creature.
        Hurlstone Fling deals damage equal to the sacrificed creature's power to any target.
    """)

    // CR 608.2h, 601.2h: the sacrificed creature's power as it last existed: 3 with the Equipment, not the card's 2
    @Test
    fun spellSacrificedCreaturesPowerUsesLastKnownInformation() = scenario {
        alice.battlefield(runt, vestment, "Mountain", "Mountain")
        attach(vestment, to = runt)
        alice.hand(fling)
        alice.casts(fling, target = "bob", choosing = listOf(runt))
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    private val acolyte = Cards.invent("Bonealtar Acolyte", "{B}", "Creature — Human Cleric", """
        {1}, Sacrifice another creature: You gain life equal to the sacrificed creature's toughness.
    """, "1/1")

    // CR 608.2h, 602.2b: the sacrificed creature's toughness counts its +1/+1 counter (3), as it last existed
    @Test
    fun activatedSacrificedCreaturesToughnessUsesLastKnownInformation() = scenario {
        alice.battlefield(acolyte, "Swamp")
        alice.battlefield(runt, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.activates(acolyte, choosing = listOf(runt))
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    private val brute = Cards.invent("Skarnhold Brute", "{3}{R}", "Creature — Ogre", """
        When this creature enters, you may sacrifice another creature. When you do, this creature deals damage equal to that creature's power to another target creature. If excess damage was dealt this way, amass Goblins X, where X is that excess damage. (Put X +1/+1 counters on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)
    """, "3/3")
    private val wall = Cards.invent("Sweepstone Wall", "{2}{W}", "Creature — Wall", "Defender", "0/5")

    // CR 603.12, 608.2h: the reflexive trigger's "that creature" is the sacrificed one as it last existed (power 3 with the Equipment)
    @Test
    fun reflexiveSacrificedCreaturesPowerUsesLastKnownInformation() = scenario {
        bob.battlefield(wall)
        alice.battlefield(runt, vestment, "Mountain", "Mountain", "Mountain", "Mountain")
        attach(vestment, to = runt)
        alice.hand(brute)
        alice.casts(brute)
        resolveAll()
        may(true)
        if (pending is arcanum.engine.decision.Decision.ChooseObjects) alice.chooses(runt)
        resolveAll()
        if (pending != null) targets(wall)
        resolveAll()
        expect { wall.view.obj?.damage shouldBe 3 }
    }

    private val disciple = Cards.invent("Freyabloom Disciple", "{3}{G}", "Creature — Elf Druid", """
        When this creature enters, you may sacrifice another creature. If you do, you gain X life and draw X cards, where X is that creature's power.
    """, "3/3")

    // CR 608.2h: "that creature's power" is the sacrificed creature's as it last existed: 3 with the Equipment
    @Test
    fun ifYouDoSacrificedCreaturesPowerUsesLastKnownInformation() = scenario {
        alice.battlefield(runt, vestment, "Forest", "Forest", "Forest", "Forest")
        attach(vestment, to = runt)
        alice.library("Island", "Island", "Island", "Island")
        alice.hand(disciple)
        alice.casts(disciple)
        resolveAll()
        may(true)
        if (pending is arcanum.engine.decision.Decision.ChooseObjects) alice.chooses(runt)
        resolveAll()
        expect {
            alice.life shouldBe 23
            alice.hand.size shouldBe 3
        }
    }

    // ---- Counters of several kinds removed as a cost (sibling of "Remove a counter from target permanent" choosing the kind)

    private val sentinel = Cards.invent("Twincharge Sentinel", "{3}", "Artifact Creature — Construct", """
        Remove two counters from this creature: Draw a card.
    """, "2/2")

    // CR 118.3, 602.2b: two counters of different kinds are two counters: the cost can be paid
    @Test
    fun removeTwoCountersCostTakesCountersOfDifferentKinds() = scenario {
        alice.battlefield(sentinel, counters = listOf(CounterKind.PowerToughness(1, 1) to 1, CounterKind.Named("oil") to 1))
        alice.library("Island")
        alice.activates(sentinel)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            card(sentinel).counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            card(sentinel).counters(CounterKind.Named("oil")) shouldBe 0
        }
    }

    private val engine = Cards.invent("Tallyhoard Engine", "{2}", "Artifact", """
        {1}, Remove two counters from among creatures you control: Draw a card.
    """)

    // CR 118.3, 602.2b: "from among": both counters may come from one creature that has two kinds
    @Test
    fun removeCountersFromAmongTakesCountersOfDifferentKindsFromOneCreature() = scenario {
        alice.battlefield(engine, "Island")
        alice.battlefield(runt, counters = listOf(CounterKind.PowerToughness(1, 1) to 1, CounterKind.Named("oil") to 1))
        alice.library("Island")
        alice.activates(engine, choosing = listOf(runt))
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            card(runt).counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            card(runt).counters(CounterKind.Named("oil")) shouldBe 0
        }
    }

    private val golem = Cards.invent("Pickcounter Golem", "{3}", "Artifact Creature — Golem", """
        Remove a counter from this creature: Draw a card.
    """, "2/2")

    // CR 118.3, 602.2b, 601.2h: with two kinds of counters on it, the payer chooses which one the cost removes (the oil counter)
    @Test
    fun removeACounterCostLetsThePayerChooseTheKind() = scenario {
        alice.battlefield(golem, counters = listOf(CounterKind.PowerToughness(1, 1) to 1, CounterKind.Named("oil") to 1))
        alice.library("Island")
        alice.activates(golem, option = "oil")
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            card(golem).counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            card(golem).counters(CounterKind.Named("oil")) shouldBe 0
        }
    }

    private val lurker = Cards.invent("Siltgrip Lurker", "{2}{G}", "Creature — Crocodile", """
        Whenever this creature deals combat damage to a player, you may remove a counter from it. When you do, destroy target artifact or enchantment that player controls.
    """, "2/2")
    private val totem = Cards.invent("Sweepbrass Totem", "{2}", "Artifact", "")

    // CR 118.3, 608.2d: a cost paid as the ability resolves: with two kinds of counters, its controller chooses which (the oil counter)
    @Test
    fun removeACounterPaidOnResolutionLetsThePayerChooseTheKind() = scenario {
        alice.battlefield(lurker, counters = listOf(CounterKind.PowerToughness(1, 1) to 1, CounterKind.Named("oil") to 1))
        bob.battlefield(totem)
        hits(lurker)
        may(true)
        answer(Answer.Option("oil"))
        resolveAll()
        if (pending != null) targets(totem)
        resolveAll()
        expect {
            bob.graveyard has totem
            card(lurker).counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            card(lurker).counters(CounterKind.Named("oil")) shouldBe 0
        }
    }

    // ---- "Enters with" replacements of the permanent as it will exist (sibling of entering transformed using the back face's)

    private val hydra = Cards.invent("Countergrown Hydra", "{2}{G}", "Creature — Hydra", "This creature enters with two +1/+1 counters on it.", "0/0")
    private val ooze = Cards.invent("Mirrorpool Ooze", "{3}{U}", "Creature — Ooze", "You may have this creature enter as a copy of any creature on the battlefield.", "0/0")
    private val echo = Cards.invent("Mirrorpool Echo", "{2}{U}", "Sorcery", "Create a token that's a copy of target creature you control.")
    private val ritual = Cards.invent("Shroudcall Ritual", "{1}{G}", "Sorcery", "Manifest the top card of your library.")

    // CR 614.12, 707.2: entering as a copy of the Hydra, it has the Hydra's "enters with" replacement and gets the two counters
    @Test
    fun enteringAsACopyGetsTheCopiedEntersWithCounters() = scenario {
        alice.battlefield(hydra, counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(ooze)
        alice.casts(ooze)
        playOut(hydra)
        val copy = CardRef(card(ooze), "copy")
        expect {
            copy.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            copy.power shouldBe 2
        }
    }

    // CR 614.12, 111.10, 707.2: a token copy of the Hydra enters with the Hydra's two counters
    @Test
    fun tokenCopyGetsTheCopiedEntersWithCounters() = scenario {
        alice.battlefield(hydra, counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        alice.battlefield("Island", "Island", "Island")
        alice.hand(echo)
        alice.casts(echo, target = hydra)
        resolveAll()
        val token = CardRef(tokens(hydra).single(), "token")
        expect {
            token.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            token.power shouldBe 2
        }
    }

    // CR 701.40a, 708.2a, 614.12: manifested face down, it has no abilities: no "enters with" counters; a 2/2
    @Test
    fun manifestedCardDoesntGetItsEntersWithCounters() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.library(hydra)
        alice.hand(ritual)
        alice.casts(ritual)
        resolveAll()
        val manifested = CardRef(card(hydra), "manifested")
        expect {
            manifested.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            manifested.power shouldBe 2
        }
    }

    // ---- Life that can't change (sibling of damage and payments under "can't lose life")

    private val stillness = Cards.invent("Stillpoint Vow", "{W}", "Instant", "Until end of turn, your life total can't change.")
    private val halving = Cards.invent("Halving Verdict", "{2}{B}", "Sorcery", "Your life total becomes half your starting life total, rounded down.")
    private val swap = Cards.invent("Lifeswap Hex", "{2}{B}", "Sorcery",
        "You may exchange life totals with target opponent. If you lost life this way, draw that many cards.")

    // CR 119.5, 119.8: setting the life total lower is losing life: a player whose life total can't change stays at 20
    @Test
    fun lifeTotalBecomesDoesntLowerALifeTotalThatCantChange() = scenario {
        alice.battlefield("Plains", "Swamp", "Swamp", "Swamp")
        alice.hand(stillness, halving)
        alice.casts(stillness)
        resolveAll()
        alice.casts(halving)
        resolveAll()
        expect { alice.life shouldBe 20 }
    }

    // CR 119.8, 701.12a: an exchange that would lower the life total of a player who can't lose life doesn't happen, for either player
    @Test
    fun lifeExchangeDoesntHappenWhenALifeTotalCantChange() = scenario(players = 3) {
        alice.battlefield("Mountain", "Plains", "Swamp", "Swamp", "Swamp")
        alice.hand(bolt, stillness, swap)
        alice.library("Island", "Island", "Island")
        alice.casts(bolt, target = "bob")
        resolveAll()
        alice.casts(stillness)
        resolveAll()
        alice.casts(swap, target = "bob")
        resolveAll()
        if (pending != null) may(true)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 20
            alice.hand.size shouldBe 0
        }
    }

    // ---- Life payments while life can't change (sibling of "can't lose life" on exchanges and costs of activated abilities)

    private val thrall = Cards.invent("Sweepmite Thrall", "{1}{B/P}", "Artifact Creature — Phyrexian Mite", "", "1/1")
    private val fen = Cards.invent("Sweepmire Fen", null, "Land", "{T}, Pay 1 life: Add {B}.")
    private val drainer = Cards.invent("Sweepvein Drainer", "{B}", "Sorcery", "Each opponent loses 2 life.")
    private val brawler = Cards.invent("Sweepgore Brawler", "{1}{R}", "Creature — Human Berserker", "This creature can't attack unless you pay 2 life.", "3/3")

    // CR 119.8, 107.4f: a player who can't lose life can't pay life for a Phyrexian symbol: with one land after the Vow, no way to cast it
    @Test
    fun phyrexianSymbolCantBePaidWithLifeWhenLifeCantChange() = scenario {
        alice.battlefield("Plains", "Island")
        alice.hand(stillness, thrall)
        alice.casts(stillness)
        resolveAll()
        kotlin.test.assertTrue(arcanum.engine.Engine.options(state, alice.id).none { it is arcanum.engine.input.Input.Cast && it.card == card(thrall) })
        runCatching { alice.casts(thrall) }
        kotlin.test.assertTrue((pending as? arcanum.engine.decision.Decision.ManaPayment)?.options.orEmpty().none { it is arcanum.engine.decision.ManaUse.Life })
        expect { alice.life shouldBe 20 }
    }

    // CR 119.8, 605.1a: a mana ability with a life cost can't be activated by a player who can't lose life: nothing pays the {B}
    @Test
    fun manaAbilityLifeCostCantBePaidWhenLifeCantChange() = scenario {
        alice.battlefield("Plains", fen)
        alice.hand(stillness, drainer)
        alice.casts(stillness)
        resolveAll()
        kotlin.test.assertTrue(arcanum.engine.Engine.options(state, alice.id).none { it is arcanum.engine.input.Input.Cast && it.card == card(drainer) })
        runCatching { alice.casts(drainer) }
        kotlin.test.assertTrue((pending as? arcanum.engine.decision.Decision.ManaPayment)?.options.orEmpty().none { it is arcanum.engine.decision.ManaUse.Ability })
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 20
        }
    }

    // CR 119.8, 508.1h: a cost to attack that pays life can't be paid by a player who can't lose life: the creature can't attack
    @Test
    fun attackLifeCostCantBePaidWhenLifeCantChange() = scenario {
        alice.battlefield(brawler, "Plains")
        alice.hand(stillness)
        alice.casts(stillness)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        runCatching { alice.attacks(brawler) }
        resolveAll()
        kotlin.test.assertTrue(state.combat?.attackers.orEmpty().none { it.id == card(brawler) }, "attacking: ${state.combat?.attackers}")
        expect { alice.life shouldBe 20 }
    }

    // CR 119.8, 118.3: "for each {B}, you may pay 2 life" offers nothing while life can't change: no black source, no activation
    @Test
    fun payLifeRatherThanBlackManaNotWhenLifeCantChange() = scenario {
        alice.battlefield(vrakkoth, idol, "Plains")
        alice.hand(stillness)
        alice.casts(stillness)
        resolveAll()
        kotlin.test.assertTrue(arcanum.engine.Engine.options(state, alice.id).none { it is arcanum.engine.input.Input.Activate && it.source == card(idol) })
        expect { bob.life shouldBe 20 }
    }

    // ---- Paying 2 life rather than {B} (sibling of spells' costs: an activated ability's cost)

    private val vrakkoth = Cards.invent("Vrakkoth, Blight Scion", "{3}{B}{B}", "Legendary Creature — Phyrexian Horror", """
        For each {B} in a cost, you may pay 2 life rather than pay that mana.
    """, "4/4")
    private val idol = Cards.invent("Blackglass Idol", "{2}", "Artifact", "{B}, {T}: Target player loses 1 life.")

    // CR 602.2b, 601.2h, 119.4: the {B} of an activation cost may be paid with 2 life (no black source)
    @Test
    fun payLifeRatherThanBlackManaInAnActivationCost() = scenario {
        alice.battlefield(vrakkoth, idol)
        alice.activates(idol, target = "bob")
        resolveAll()
        expect {
            alice.life shouldBe 18
            bob.life shouldBe 19
        }
    }

    // ---- Target legality of a permanent spell on resolution (sibling of an Aura spell whose target became illegal)

    private val boon = Cards.invent("Sweepbloom Boon", "{2}{W}", "Enchantment Creature — Spirit", """
        Bestow {3}{W}
        Enchanted creature gets +2/+2.
    """, "2/2")
    private val veil = Cards.invent("Sweepveil Ward", "{U}", "Instant", "Target creature you control gains hexproof until end of turn.")

    // CR 702.103e, 608.3b: a bestowed Aura spell whose target became illegal (hexproof) resolves as an enchantment creature, unattached
    @Test
    fun bestowedSpellWithAnIllegalTargetResolvesAsACreature() = scenario {
        bob.battlefield(wall, "Island")
        bob.hand(veil)
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(boon)
        alice.casts(boon, way = alternativeOf(boon, Keyword.Bestow.Kind), target = wall)
        alice.passes()
        bob.casts(veil, target = wall)
        resolveAll()
        expect {
            alice.battlefield has boon
            boon.view.obj?.attachedTo shouldBe null
            boon.power shouldBe 2
            wall.toughness shouldBe 5
        }
    }

    // ---- Intervening "if" checked again on resolution (sibling of the reflexive "When you do, if")

    private val omen = Cards.invent("Sweepdusk Omen", "{U}", "Instant", "At the beginning of the next end step, if you control an artifact, draw a card.")
    private val shatter = Cards.invent("Sweepshard Breaker", "{R}", "Instant", "Destroy target artifact.")

    // CR 603.4: the delayed trigger's intervening "if" is checked again as it resolves: the artifact is gone, no card is drawn
    @Test
    fun delayedTriggerInterveningIfIsCheckedOnResolution() = scenario {
        alice.battlefield(totem, "Island")
        alice.library("Island", "Island")
        bob.battlefield("Mountain")
        bob.hand(shatter)
        alice.hand(omen)
        alice.casts(omen)
        resolveAll()
        advanceTo(TurnStep.End)
        expect { stack.size shouldBe 1 }
        alice.passes()
        bob.casts(shatter, target = totem)
        resolveAll()
        expect {
            alice.graveyard has totem
            alice.hand.size shouldBe 0
        }
    }

    // ---- Keywords granted to spells by a static ability (sibling of convoke granted to the next spell)

    private val conclave = Cards.invent("Sweepchoir Conclave", "{2}{W}", "Enchantment", "Creature spells you cast have convoke.")
    private val golem2 = Cards.invent("Sweepclay Golem", "{2}", "Artifact Creature — Golem", "", "2/2")

    // CR 702.51a, 601.2h: cast from the hand with convoke granted by a static ability, two tapped creatures pay its {2}
    @Test
    fun staticallyGrantedConvokePaysFromTheHand() = scenario {
        alice.battlefield(conclave, runt, wall)
        alice.hand(golem2)
        alice.casts(golem2)
        resolveAll()
        expect {
            alice.battlefield has golem2
            card(runt).tapped shouldBe true
            card(wall).tapped shouldBe true
        }
    }

    // ---- Costs paid on resolution (sibling of mana restrictions and life for {B} in costs paid while casting)

    private val denialB = Cards.invent("Sweeptithe Denial", "{U}", "Instant", "Counter target spell unless its controller pays {B}.")
    private val denial1 = Cards.invent("Sweeptoll Denial", "{U}", "Instant", "Counter target spell unless its controller pays {1}.")
    private val cairn = Cards.invent("Sweepgrove Cairn", null, "Land", "{T}: Add {C}. Spend this mana only to cast creature spells.")

    // CR 118.3, 119.4: "for each {B} in a cost" covers a cost paid as a spell resolves: 2 life pays the {B}, no black source
    @Test
    fun payLifeRatherThanBlackManaForACostPaidOnResolution() = scenario {
        alice.battlefield(vrakkoth, "Island", "Island")
        alice.hand(totem)
        bob.battlefield("Island")
        bob.hand(denialB)
        alice.casts(totem)
        alice.passes()
        bob.casts(denialB, target = totem)
        resolveAll()
        payYes()
        expect {
            alice.battlefield has totem
            alice.life shouldBe 18
        }
    }

    // CR 106.6: mana that may be spent only to cast creature spells can't pay a cost paid as a spell resolves: the artifact is countered
    @Test
    fun restrictedManaCantPayACostOnResolution() = scenario {
        alice.battlefield(cairn, "Island", "Island")
        alice.hand(totem)
        bob.battlefield("Island")
        bob.hand(denial1)
        alice.casts(totem)
        alice.passes()
        bob.casts(denial1, target = totem)
        resolveAll()
        if (pending is arcanum.engine.decision.Decision.May) payYes()
        expect {
            alice.graveyard has totem
            card(cairn).tapped shouldBe false
        }
    }

    // ---- Cost reductions with a condition (sibling of "This spell costs {N} less to cast if")

    private val scholar = Cards.invent("Sweepwatch Scholar", "{1}{U}", "Creature — Human Wizard", """
        {2}, {T}: Draw a card. This ability costs {1} less to activate if you control an artifact.
    """, "1/1")

    // CR 602.2b, 601.2f: with an artifact, the activation costs {1}: one Island pays it
    @Test
    fun activatedAbilityCostsLessIfAConditionHolds() = scenario {
        alice.battlefield(scholar, totem, "Island")
        alice.library("Island")
        alice.activates(scholar)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // ---- "It" after a target names the target, not the source (sibling of "Sacrifice it." and "It can't be blocked" read as this creature)

    private val herald = Cards.invent("Sweepbanner Herald", "{2}{G}", "Creature — Elf Warrior", """
        When this creature enters, put a +1/+1 counter on target creature you control. It gains trample until end of turn.
    """, "2/2")
    private val necro = Cards.invent("Sweepgrave Caller", "{3}{B}", "Creature — Zombie Wizard", """
        When this creature enters, return target creature card from your graveyard to the battlefield. Exile it at the beginning of the next end step.
    """, "2/2")
    private val trickster = Cards.invent("Sweepshade Trickster", "{1}{U}", "Creature — Faerie Rogue", """
        {1}: Target creature gets +1/+0 until end of turn. It can't be blocked this turn.
    """, "1/1")

    // CR 608.2c, 611.2a: "it" is the target that got the counter: the Runt gains trample, the Herald doesn't
    @Test
    fun itAfterATargetedCounterIsTheTarget() = scenario {
        alice.battlefield(runt, "Forest", "Forest", "Forest")
        alice.hand(herald)
        alice.casts(herald)
        resolveAll()
        targets(runt)
        resolveAll()
        expect {
            card(runt).counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            runt hasKeyword Keyword.Trample
            herald lacksKeyword Keyword.Trample
        }
    }

    // CR 603.7a, 608.2c: the delayed trigger exiles the returned creature, not the creature whose ability returned it
    @Test
    fun itOfADelayedExileIsTheReturnedCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(runt)
        alice.hand(necro)
        alice.casts(necro)
        resolveAll()
        if (pending != null) targets(runt)
        resolveAll()
        expect { alice.battlefield has runt }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.exile has runt
            alice.battlefield has necro
        }
    }

    // CR 608.2c, 611.2a: "It can't be blocked this turn" is the target creature, not the Trickster
    @Test
    fun itCantBeBlockedIsTheTarget() = scenario {
        alice.battlefield(trickster, runt, "Island")
        alice.activates(trickster, target = runt)
        resolveAll()
        expect {
            runt.power shouldBe 3
            runt hasMechanic { it == arcanum.api.Mechanic.CantBeBlocked }
            trickster lacksMechanic { it == arcanum.api.Mechanic.CantBeBlocked }
        }
    }

    // ---- "<a kind> other than this creature" keeps "you control" (sibling of the trigger condition that dropped it)

    private val servitor = Cards.invent("Sweepcog Servitor", "{2}", "Artifact Creature — Construct", """
        {T}: Draw a card. Activate only if you control an artifact other than this creature.
    """, "1/1")
    private val adept = Cards.invent("Sweeprune Adept", "{1}{U}", "Creature — Human Wizard", """
        This creature gets +2/+0 as long as you control a Wizard other than this creature.
    """, "1/2")
    private val sage = Cards.invent("Sweeptome Sage", "{U}", "Creature — Human Wizard", "", "1/1")

    // CR 602.5b: the only other artifact is the opponent's: the ability can't be activated; with one of your own, it can
    @Test
    fun activateOnlyIfYouControlAnotherArtifactCountsOnlyYours() = scenario {
        alice.battlefield(servitor)
        bob.battlefield(totem)
        alice.library("Island")
        kotlin.test.assertTrue(arcanum.engine.Engine.options(state, alice.id).none { it is arcanum.engine.input.Input.Activate && it.source == card(servitor) })
        alice.battlefield(totem)
        alice.activates(servitor)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 613.4c, 611.3a: an opponent's Wizard doesn't count, nor the Adept itself; a second Wizard of your own does
    @Test
    fun asLongAsYouControlAnotherWizardCountsOnlyYours() = scenario {
        alice.battlefield(adept)
        bob.battlefield(sage)
        expect { adept.power shouldBe 1 }
        alice.battlefield(sage)
        expect { adept.power shouldBe 3 }
    }
}
