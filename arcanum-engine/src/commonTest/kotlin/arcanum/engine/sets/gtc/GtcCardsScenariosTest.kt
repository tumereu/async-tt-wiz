package arcanum.engine.sets.gtc

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.change.Changes
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Gatecrash cards whose rules needed new engine support: counters triggering per counter, entering as a Mutant,
 * blocking relations, "dealt damage this turn", hexproof ignored, the greatest number discarded, targets from a single
 * graveyard, counters removed from among creatures, and "except it has this ability". */
class GtcCardsScenariosTest {
    private val depthsight = Cards.invent("Depthsight Mage", "{2}{G}{U}", "Creature — Human Wizard", """
        Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        Whenever a +1/+1 counter is put on this creature, you may draw a card.
    """, "1/1")
    private val graft = Cards.invent("Triple Graft", "{2}{G}", "Sorcery", "Put three +1/+1 counters on target creature.")
    private val biomancer = Cards.invent("Grand Biomancer", "{2}{G}{U}", "Creature — Elf Wizard",
        "Each other creature you control enters with a number of additional +1/+1 counters on it equal to this creature's power and as a Mutant in addition to its other types.", "2/4")
    private val saprolings = Cards.invent("Spore Call", "{G}", "Sorcery", "Create a 1/1 green Saproling creature token.")
    private val charity = Cards.invent("Charity Beast", "{2}{W}{B}", "Creature — Beast", "Creatures blocking or blocked by this creature have lifelink.", "6/6")
    private val convoy = Cards.invent("Armored Convoy", "{3}", "Artifact Creature — Construct",
        "Prevent all combat damage that would be dealt to this creature by creatures blocking it.", "2/1")
    private val swing = Cards.invent("Headsman's Swing", "{W}{B}", "Instant", "Target creature that dealt damage this turn gets -5/-5 until end of turn.")
    private val searchlight = Cards.invent("Glaring Searchlight", "{1}", "Artifact", """
        Creatures your opponents control with hexproof can be the targets of spells and abilities you control as though they didn't have hexproof.
        {3}, Sacrifice this artifact: Creatures you control gain hexproof until end of turn and can't be blocked this turn.
    """)
    private val frenzy = Cards.invent("Whispered Frenzy", "{2}{U}{B}", "Sorcery", """
        Each player discards their hand, then draws cards equal to the greatest number of cards a player discarded this way.
        Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
    """)
    private val recollection = Cards.invent("Serene Recollection", "{G}", "Sorcery",
        "Shuffle Serene Recollection and up to three target cards from a single graveyard into their owners' libraries.")
    private val vat = Cards.invent("Slimecraft Vat", "{3}{G}", "Enchantment",
        "{1}{G}, Remove one or more +1/+1 counters from among creatures you control: Create an X/X green Ooze creature token, where X is the number of +1/+1 counters removed this way.")
    private val stage = Cards.invent("Actor's Stage", null, "Land", """
        {T}: Add {C}.
        {2}, {T}: This land becomes a copy of target land, except it has this ability.
    """)

    private val aurelis = Cards.invent("Aurelis, the Battle Angel", "{2}{R}{R}{W}{W}", "Legendary Creature — Angel", """
        Flying, vigilance, haste
        Whenever Aurelis attacks for the first time each turn, untap all creatures you control. After this phase, there is an additional combat phase.
    """, "3/4")

    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Wide Ox", "{2}{G}", "Creature — Ox", "", "1/4")
    private val lurker = Cards.invent("Veiled Lurker", "{1}{G}", "Creature — Elf", "Hexproof", "2/2")
    private val pinger = Cards.invent("Spark Tinker", "{1}{R}", "Creature — Human", "{T}: Spark Tinker deals 1 damage to any target.", "1/1")
    private val murder = Cards.invent("Quick Execution", "{1}{B}{B}", "Instant", "Destroy target creature.")
    private val counter = CounterKind.PowerToughness(1, 1)

    /** Resolves the Stack, answering "you may" with [yes] and ordering simultaneous triggers as they come. */
    private fun Scenario.resolveAll(yes: Boolean = true) {
        var guard = 40
        while (guard-- > 0) {
            when {
                pending is Decision.May -> answer(Answer.YesNo(yes))
                pending is Decision.OrderTriggers -> answer(Answer.Order(decision<Decision.OrderTriggers>().triggers.map { it.index }))
                pending != null -> return
                state.stack.isNotEmpty() -> resolve()
                else -> return
            }
        }
    }

    private fun Scenario.counters(name: String, n: Int) = change(Changes.counters(state, TargetRef.Object(card(name)), counter, n))

    /** Three counters at once: three triggers, three cards. */
    // CR 603.2c
    @Test
    fun aCounterPutTriggersPerCounter() = scenario {
        alice.battlefield(depthsight)
        alice.library("Island", "Island", "Island", "Island")
        alice.hand(graft)
        alice.castsWithoutCosts(graft, target = depthsight)
        resolve()
        resolveAll()
        expect {
            depthsight.counters(counter) shouldBe 3
            alice.hand.names.size shouldBe 3
        }
    }

    /** Another creature enters with counters equal to its power and as a Mutant; a token too. */
    // CR 614.1c, 614.12, 111.1
    @Test
    fun biomancerCardsAndTokens() = scenario {
        alice.battlefield(biomancer)
        alice.hand(bear, saprolings)
        alice.castsWithoutCosts(bear)
        resolve()
        alice.castsWithoutCosts(saprolings)
        resolve()
        expect {
            bear.counters(counter) shouldBe 2
            bear.power shouldBe 4
            assertTrue(Subtype("Mutant") in bear.subtypes)
            assertTrue(Subtype("Bear") in bear.subtypes)
            "Saproling Token".counters(counter) shouldBe 2
            assertTrue(Subtype("Mutant") in "Saproling Token".subtypes)
            biomancer.counters(counter) shouldBe 0
        }
    }

    /** The creature blocking it has lifelink. */
    // CR 509.1a, 702.15b
    @Test
    fun blockingCreatureHasLifelink() = scenario {
        alice.battlefield(charity)
        bob.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(charity)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bear to charity)
        expect {
            bear hasKeyword Keyword.Lifelink
            charity lacksKeyword Keyword.Lifelink
        }
        advanceTo(TurnStep.CombatDamage)
        expect { bob.life shouldBe 22 }
    }

    /** Combat damage from the creature blocking it is prevented. */
    // CR 615, 510.1
    @Test
    fun convoyTakesNoDamageFromBlockers() = scenario {
        alice.battlefield(convoy)
        bob.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(convoy)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bear to convoy)
        advanceTo(TurnStep.CombatDamage)
        expect { alice.battlefield has convoy }
    }

    /** Only a creature that dealt damage this turn can be the target. */
    // CR 115.1, 601.2c
    @Test
    fun swingTargetsACreatureThatDealtDamage() = scenario {
        alice.battlefield("Plains", "Swamp", pinger)
        alice.hand(swing)
        bob.battlefield(bear)
        // no creature dealt damage yet: nothing to cast it on
        assertTrue(arcanum.engine.Engine.options(state, alice.id).none { it is arcanum.engine.input.Input.Cast && it.card == card(swing) })
        alice.activates(pinger, target = "bob")
        allPass()
        alice.casts(swing, target = pinger)
        allPass()
        expect { alice.graveyard.names shouldBe listOf(swing, pinger) }
    }

    /** An opponent's hexproof creature can be the target of your spells. */
    // CR 702.11b
    @Test
    fun searchlightIgnoresHexproof() = scenario {
        alice.battlefield(searchlight, "Swamp", "Swamp", "Swamp")
        alice.hand(murder)
        bob.battlefield(lurker)
        alice.casts(murder, target = lurker)
        allPass()
        expect { bob.graveyard.names shouldBe listOf(lurker) }
    }

    /** Without it, hexproof stops the target. */
    // CR 702.11b
    @Test
    fun hexproofWithoutSearchlight() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(murder)
        bob.battlefield(lurker)
        assertFailsWith<IllegalArgumentException> { alice.casts(murder, target = lurker) }
    }

    /** Each player draws as many cards as the most any player discarded. */
    // CR 101.4, 608.2h
    @Test
    fun frenzyDrawsTheGreatestNumber() = scenario {
        alice.hand(frenzy, "Island")
        alice.library("Swamp", "Swamp", "Swamp", "Swamp")
        bob.hand("Forest", "Forest", "Forest")
        bob.library("Plains", "Plains", "Plains", "Plains")
        alice.castsWithoutCosts(frenzy)
        resolve()
        expect {
            alice.hand.names shouldBe listOf("Swamp", "Swamp", "Swamp")
            bob.hand.names shouldBe listOf("Plains", "Plains", "Plains")
            bob.graveyard.names shouldBe listOf("Forest", "Forest", "Forest")
        }
    }

    /** Cards from two graveyards can't be its targets together. */
    // CR 601.2c
    @Test
    fun recollectionFromASingleGraveyard() = scenario {
        alice.battlefield("Forest")
        alice.hand(recollection)
        alice.graveyard(bear)
        bob.graveyard(ox)
        assertFailsWith<IllegalArgumentException> { alice.casts(recollection, targets = listOf(bear, ox)) }
    }

    /** Counters removed from among creatures you control: the Ooze is as big as the number removed. */
    // CR 601.2f, 107.3
    @Test
    fun vatRemovesCountersFromAmongCreatures() = scenario {
        alice.battlefield(vat, bear, ox, "Forest", "Forest")
        counters(bear, 1)
        counters(ox, 2)
        alice.activates(vat, x = 3, choosing = listOf(bear, ox))
        allPass()
        expect {
            bear.counters(counter) shouldBe 0
            ox.counters(counter) shouldBe 0
            "Ooze Token".power shouldBe 3
            "Ooze Token".toughness shouldBe 3
        }
    }

    /** Attacking the first time: everything untaps and a second combat follows this one (the second attack doesn't
     * trigger it again). */
    // CR 500.8, 506.1
    @Test
    fun aurelisAddsACombat() = scenario {
        alice.battlefield(aurelis, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(aurelis, bear)
        resolveAll()
        expect { bear.tapped shouldBe false }
        advanceTo(TurnStep.EndOfCombat)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(aurelis, bear)
        expect { stack.isEmpty() }
        advanceTo(TurnStep.PostcombatMain)
        expect { bob.life shouldBe 10 }
        advanceTo(TurnStep.End)
        expect { bob.life shouldBe 10 }
    }

    /** It becomes a copy of the land and keeps this ability, so it can copy again. */
    // CR 707.9b
    @Test
    fun stageKeepsThisAbility() = scenario {
        alice.battlefield(stage, "Plains", "Plains")
        bob.battlefield("Island")
        alice.activates(stage, ability = 1, target = "Island", paying = listOf("Plains", "Plains"))
        allPass()
        val c = view()[card(stage)]!!.characteristics!!
        assertEquals("Island", c.name)
        assertEquals(1, c.abilities.count { a -> (a.ability as? Ability.Activated)?.instructions?.steps?.any {
            ((it as? arcanum.api.Step.Do)?.effect as? arcanum.api.Effect.ApplyMechanics)?.mechanics?.any { m -> m is arcanum.api.Mechanic.CopyOf } == true
        } == true })
    }
}
