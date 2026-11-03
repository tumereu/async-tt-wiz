package arcanum.engine.sets.msh

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Marvel Super Heroes cards in play: their new wordings, from "can't become untapped" to sneak. */
class MshCardsScenariosTest {
    private val arachnidWoman = Cards.invent("Arachnid Woman, Secret Agent", "{3}{W/U}", "Legendary Creature — Spider Human Spy Hero", """
        Flash
        When Arachnid Woman enters, tap target creature an opponent controls. That creature can't become untapped for as long as you control Arachnid Woman.
    """, "1/4")
    private val gang = Cards.invent("The Ruinous Demolition Gang", "{X}{B}{R}", "Legendary Creature — Human Villain", """
        The Ruinous Demolition Gang enters with X +1/+1 counters on it.
        When The Ruinous Demolition Gang enters, choose up to X —
        • Discard a card, then draw a card.
        • Target opponent loses 2 life.
        • Destroy target token.
        • Each player sacrifices a creature of their choice.
    """, "2/2")
    private val schism = Cards.invent("The Great Hero Schism", "{3}{R}{W}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Gain control of up to two target creatures with total mana value 6 or less for as long as this Saga remains on the battlefield.
        II — Creatures you control get +1/+1 and gain vigilance until end of turn.
        III — Target creature you control fights up to one other target creature.
    """)
    private val badger = Cards.invent("Badger, Fierce Fighter", "{2}{R}{G}", "Legendary Creature — Mutant Berserker Hero", """
        Haste
        When Badger enters, he fights up to one other target creature.
        If damage would be dealt to Badger, instead that damage is dealt, but all other damage already dealt to him is healed.
    """, "3/5")
    private val vipers = Cards.invent("The Viper Brotherhood", "{1}{B}{G}", "Legendary Creature — Human Snake Villain", """
        Deathtouch
        Ward—Get five poison counters. (A player with ten or more poison counters loses the game.)
        Whenever another creature you control with deathtouch dies, each opponent sacrifices a nontoken creature of their choice.
    """, "3/4")
    private val broker = Cards.invent("Influential Broker", "{2}{G}", "Creature — Human Villain",
        "{T}: For each kind of counter on target permanent or player, give that permanent or player another counter of that kind. Activate only as a sorcery.", "3/3")
    private val marquee = Cards.invent("Marvelous Marquee, Screen Hero", "{3}{R}{R}", "Legendary Creature — Human Performer Hero", """
        Flying
        Each power-up ability of permanents you control can be activated an additional time.
        Power-up — {5}{R}{R}: Put two +1/+1 counters on Marvelous Marquee. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)
    """, "4/4")
    private val scrapper = Cards.invent("Plucky Scrapper", "{1}{W}", "Creature — Human Warrior Hero", """
        Lifelink
        Power-up — {4}{W}: Put two +1/+1 counters on this creature. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)
    """, "2/1")
    private val masterLin = Cards.invent("Master Lin, Kung Fu Prodigy", "{1}{G}", "Legendary Creature — Human Warrior Hero", """
        You may activate abilities of creatures you control as though those creatures had haste.
        {T}: Add two mana of any one color. Spend this mana only to activate abilities of creature sources.
    """, "2/2")
    private val nerith = Cards.invent("Nerith the Tide-King", "{1}{U}{U}", "Legendary Creature — Mutant Merfolk Villain", """
        Flying
        Nerith's power is equal to the number of Merfolk you control.
        Whenever you cast a noncreature spell with one or more blue mana symbols in its mana cost, create that many 1/1 blue Merfolk creature tokens.
    """, "*/4")
    private val tideLore = Cards.invent("Tide Lore", "{1}{U}{U}", "Sorcery", "Draw a card.")
    private val jester = Cards.invent("Jester, God of Mischief", "{1}{U}", "Legendary Creature — God Sorcerer Villain",
        "Whenever a player or permanent becomes the target of an ability you control, draw a card. This ability triggers only once each turn.", "2/1")
    private val needler = Cards.invent("Brass Needler", "{1}", "Artifact", "{T}: Target player loses 1 life.")
    private val tempest = Cards.invent("Tempest, Windrider", "{1}{G}{W}{W}", "Legendary Creature — Mutant Hero", """
        Flying
        Creatures with flying can't attack you or block creatures you control.
        Whenever you cast a spell that targets one or more creatures, those creatures gain flying until end of turn.
    """, "4/4")
    private val brawn = Cards.invent("Quick Brawn", "{G}", "Instant", "Target creature gets +2/+2 until end of turn.")
    private val agents = Cards.invent("Agents of the Bureau", "{2}{W}", "Creature — Human Spy Hero",
        "Whenever a creature you control attacks alone, that creature gets +1/+1 until end of turn.", "2/4")
    private val kiri = Cards.invent("Kiri Wynn, Street Samurai", "{1}{W}", "Legendary Creature — Human Samurai Hero",
        "Whenever you cast a spell that targets a creature you control, put a +1/+1 counter on Kiri Wynn. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)",
        "2/2")
    private val bayne = Cards.inventFaces("ModalDoubleFaced",
        arcanum.engine.cards.face("Doctor Brennan Bayne", "{U}", "Legendary Creature — Human Scientist Hero", """
            {X}{X}, {T}: Draw X cards. Activate only as a sorcery.
            {2}{R}{R}{G}{G}: Transform Doctor Brennan Bayne. Activate only as a sorcery.
        """.trimIndent(), "1/1", null, null),
        arcanum.engine.cards.face("The Unstoppable Brute", "{2}{R}{R}{G}{G}", "Legendary Creature — Gamma Berserker Hero", """
            Reach, trample
            Enrage — Whenever The Unstoppable Brute is dealt damage, put a +1/+1 counter on him. If he's attacking, untap him and there is an additional combat phase after this phase.
        """.trimIndent(), "8/8", null, null))
    private val worldWar = Cards.invent("World War Brute", "{3}{G}{G}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — The next red or green creature spell you cast this turn can be cast without paying its mana cost.
        II — Put three +1/+1 counters on target creature you control.
        III — Choose target creature you control. Until end of turn, double its power and toughness and it gains trample.
    """)
    private val katarina = Cards.invent("Katarina, Daughter of the Fist", "{2}{B}{B}", "Legendary Creature — Human Ninja Villain", """
        Sneak {1}{B}{B} (You may cast this spell for {1}{B}{B} if you also return an unblocked attacker you control to hand during the declare blockers step. She enters tapped and attacking.)
        When Katarina enters, destroy target creature an opponent controls with power 3 or less.
    """, "3/3")
    private val shock = Cards.invent("Quick Zap", "{R}", "Instant", "Quick Zap deals 2 damage to any target.")
    private val bear = Cards.invent("Plaza Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Plaza Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val giant = Cards.invent("Plaza Giant", "{3}{R}", "Creature — Giant", "", "4/4")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    /** The tapped creature stays tapped in its controller's untap step while the Arachnid Woman's controller keeps her. */
    // CR 502.3
    @Test
    fun cantBecomeUntappedWhileYouControlHer() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(arachnidWoman)
        alice.library(bear, bear)
        bob.library(bear, bear)
        bob.battlefield(ogre)
        alice.casts(arachnidWoman, target = ogre)
        resolveAll()
        expect { ogre.tapped shouldBe true }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { ogre.tapped shouldBe true }
        move(arachnidWoman, Zone.Graveyard)
        advanceTo(TurnStep.Upkeep, turn = 4)
        expect { ogre.tapped shouldBe false }
    }

    /** "Choose up to X": with X = 1, one mode at most. */
    // CR 700.2, 107.3m
    @Test
    fun choosesUpToXModes() = scenario {
        alice.battlefield("Swamp", "Mountain", "Swamp")
        alice.hand(gang)
        alice.casts(gang, x = 1)
        resolveAll()
        expect { gang.counters(p1p1) shouldBe 1 }
        assertEquals(1, decision<Decision.Modes>().max)
        assertFailsWith<IllegalArgumentException> { alice.answers(Answer.Modes(listOf(1, 3))) }
        alice.answers(Answer.Modes(listOf(1)))
        resolveAll()
        assertEquals(18, bob.life)
    }

    /** Up to two targets with total mana value 6 or less: an Ogre and a Giant (7) are refused, an Ogre and a Bear taken. */
    // CR 601.2c, 714.2b
    @Test
    fun targetsWithTotalManaValueAtMostSix() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Plains", "Plains")
        alice.hand(schism)
        bob.battlefield(ogre, giant, bear)
        alice.casts(schism)
        resolveAll()
        assertFailsWith<IllegalArgumentException> { alice.answers(Answer.Targets(listOf(target(ogre), target(giant)))) }
        alice.answers(Answer.Targets(listOf(target(ogre), target(bear))))
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            bear.controller shouldBe alice.id
            giant.controller shouldBe bob.id
        }
    }

    /** New damage heals the damage already marked: two 2-damage hits leave 2 marked, not 4. */
    // CR 701.69a, 614.1a
    @Test
    fun newDamageHealsOldDamage() = scenario {
        alice.battlefield(badger)
        bob.battlefield("Mountain", "Mountain")
        bob.hand(shock, shock)
        turn(active = bob)
        bob.casts(shock, target = badger)
        resolveAll()
        expect { badger.view.obj!!.damage shouldBe 2 }
        bob.casts(shock, target = badger)
        resolveAll()
        expect {
            badger.view.obj!!.damage shouldBe 2
            badger.zone shouldBe Zone.Battlefield
        }
    }

    /** Ward paid by getting five poison counters: the spell still resolves. */
    // CR 702.21a, 122.1f
    @Test
    fun wardPaidWithPoisonCounters() = scenario {
        alice.battlefield(vipers)
        bob.battlefield("Mountain")
        bob.hand(shock)
        turn(active = bob)
        bob.casts(shock, target = vipers)
        allPass()
        bob.answers(Answer.YesNo(true))
        resolveAll()
        assertEquals(5, bob.counters(CounterKind.Poison))
        expect { vipers.view.obj!!.damage shouldBe 2 }
    }

    /** Ward unpaid: the spell is countered. */
    // CR 702.21a
    @Test
    fun wardUnpaidCountersTheSpell() = scenario {
        alice.battlefield(vipers)
        bob.battlefield("Mountain")
        bob.hand(shock)
        turn(active = bob)
        bob.casts(shock, target = vipers)
        allPass()
        bob.answers(Answer.YesNo(false))
        resolveAll()
        assertEquals(0, bob.counters(CounterKind.Poison))
        expect {
            vipers.view.obj!!.damage shouldBe 0
            bob.graveyard has shock
        }
    }

    /** Another counter of each kind already there: on a creature, and on a player. */
    // CR 122.1
    @Test
    fun anotherCounterOfEachKind() = scenario {
        alice.battlefield(broker)
        bob.battlefield(ogre)
        bob.counters(CounterKind.Poison, 2)
        alice.activates(broker, target = "bob")
        resolveAll()
        assertEquals(3, bob.counters(CounterKind.Poison))
    }

    /** A creature with +1/+1 and stun counters gets one more of each. */
    // CR 122.1
    @Test
    fun anotherCounterOfEachKindOnACreature() = scenario {
        alice.battlefield(broker)
        bob.battlefield(ogre)
        change(arcanum.engine.change.Changes.counters(state, arcanum.engine.state.TargetRef.Object(card(ogre)), p1p1, 2))
        change(arcanum.engine.change.Changes.counters(state, arcanum.engine.state.TargetRef.Object(card(ogre)), CounterKind.Stun, 1))
        alice.activates(broker, target = ogre)
        resolveAll()
        expect {
            ogre.counters(p1p1) shouldBe 3
            ogre.counters(CounterKind.Stun) shouldBe 2
        }
    }

    /** Power-up abilities may be activated twice while the Marquee is around. */
    // CR 702.193a
    @Test
    fun powerUpAnAdditionalTime() = scenario {
        alice.battlefield(marquee, scrapper)
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates(scrapper)
        resolveAll()
        alice.activates(scrapper)
        resolveAll()
        expect { scrapper.counters(p1p1) shouldBe 4 }
    }

    /** As though it had haste: the creature's {T} ability works the turn it enters. */
    // CR 302.6
    @Test
    fun abilitiesAsThoughHaste() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(masterLin)
        alice.casts(masterLin)
        resolveAll()
        alice.activates(masterLin, color = arcanum.api.Color.Green)
        assertEquals(2, alice.pool.size)
    }

    /** A noncreature spell with two blue mana symbols: two Merfolk, and Nerith's power counts them. */
    // CR 202.1
    @Test
    fun tokensForEachBlueManaSymbol() = scenario {
        alice.battlefield(nerith, "Island", "Island", "Island")
        alice.hand(tideLore)
        alice.library(bear)
        alice.casts(tideLore)
        resolveAll()
        expect { nerith.power shouldBe 3 }
    }

    /** A player becoming the target of an ability you control draws a card, once each turn. */
    // CR 603.2, 115.1
    @Test
    fun drawsWhenAnAbilityTargets() = scenario {
        alice.battlefield(jester, needler)
        alice.library(bear, bear)
        alice.activates(needler, target = "bob")
        resolveAll()
        assertEquals(19, bob.life)
        expect { alice.hand has bear }
    }

    /** The creature a spell you cast targets gains flying. */
    // CR 603.2
    @Test
    fun targetedCreaturesGainFlying() = scenario {
        alice.battlefield(tempest, bear, "Forest")
        alice.hand(brawn)
        alice.casts(brawn, target = bear)
        allPass()
        expect { bear hasKeyword Keyword.Flying }
    }

    /** A creature attacking alone gets +1/+1; two attackers get nothing. */
    // CR 506.5
    @Test
    fun attackingAloneGetsABonus() = scenario {
        alice.battlefield(agents, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    /** Two attackers: no one attacks alone. */
    // CR 506.5
    @Test
    fun attackingTogetherGetsNothing() = scenario {
        alice.battlefield(agents, bear, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear, ogre)
        resolveAll()
        expect {
            bear.power shouldBe 2
            ogre.power shouldBe 3
        }
        assertTrue(state.stack.isEmpty())
    }

    /** A spell targeting your own creature: a +1/+1 counter and scry 1. */
    // CR 603.2
    @Test
    fun spellTargetingYourCreatureGrowsHer() = scenario {
        alice.battlefield(kiri, bear, "Forest")
        alice.hand(brawn)
        alice.library(ogre)
        alice.casts(brawn, target = bear)
        allPass()
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(emptyList()))
        resolveAll()
        expect {
            kiri.counters(p1p1) shouldBe 1
            bear.power shouldBe 4
        }
    }

    /** The modal double-faced card's front transforms into its back face. */
    // CR 712.3, 701.27a
    @Test
    fun modalDoubleFacedTransforms() = scenario {
        alice.battlefield(bayne, "Mountain", "Mountain", "Forest", "Forest", "Island", "Island")
        alice.activates(bayne, ability = 1)
        resolveAll()
        expect {
            card(bayne).power shouldBe 8
            card(bayne) hasKeyword Keyword.Trample
        }
    }

    /** Chapter I: the next red or green creature spell this turn is free. */
    // CR 714.2b, 118.9
    @Test
    fun nextGreenCreatureSpellIsFree() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(worldWar, giant)
        alice.casts(worldWar)
        resolveAll()
        val free = arcanum.engine.casting.Legality.alternativeCosts(arcanum.engine.casting.Snapshot(state), card(giant), alice.id).single().first.origin
        alice.casts(giant, way = CastWay(alternative = free))
        resolveAll()
        expect { giant.zone shouldBe Zone.Battlefield }
    }

    /** Sneak in the declare blockers step: the unblocked attacker returns to hand, Katarina enters tapped and attacking. */
    // CR 702.190a, 702.190b
    @Test
    fun sneakSwapsAnUnblockedAttacker() = scenario {
        alice.battlefield(bear, "Swamp", "Swamp", "Swamp")
        alice.hand(katarina)
        bob.battlefield(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        allPass()
        bob.blocks()
        val sneak = view()[card(katarina)]!!.characteristics!!.abilities.first { inst ->
            (inst.ability as? arcanum.api.Ability.Static)?.mechanics?.any { it is arcanum.api.Mechanic.AlternativeCost } == true
        }.origin
        alice.casts(katarina, way = CastWay(alternative = sneak), choosing = listOf(bear))
        expect { alice.hand has bear }
        resolveAll()
        expect {
            katarina.zone shouldBe Zone.Battlefield
            katarina.tapped shouldBe true
            ogre.zone shouldBe Zone.Graveyard
        }
        assertTrue(state.combat!!.attackers.any { it.id == card(katarina) })
    }

    /** Sneak isn't available outside the declare blockers step. */
    // CR 702.190a
    @Test
    fun sneakOnlyInTheDeclareBlockersStep() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(katarina)
        val sneak = view()[card(katarina)]!!.characteristics!!.abilities.first { inst ->
            (inst.ability as? arcanum.api.Ability.Static)?.mechanics?.any { it is arcanum.api.Mechanic.AlternativeCost } == true
        }.origin
        assertFailsWith<IllegalArgumentException> { alice.casts(katarina, way = CastWay(alternative = sneak)) }
    }
}
