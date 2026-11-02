package arcanum.engine.sets.rtr

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/** Return to Ravnica's mechanics in play: detain, populate, overload, scavenge, unleash and regeneration, with the invented
 * cards of the parser's chunks 01–05. */
class RtrMechanicsScenariosTest {
    private val stay = Cards.invent("Stay of Motion", "{1}{U}", "Sorcery", """
        Detain target creature an opponent controls. (Until your next turn, that creature can't attack or block and its activated abilities can't be activated.)
        Draw a card.
    """)
    private val wings = Cards.invent("Wings Over the Market", "{3}{W}", "Instant",
        "Create a 1/1 white Bird creature token with flying, then populate. (Create a token that's a copy of a creature token you control.)")
    private val bulwark = Cards.invent("Deeproot Bulwark", "{2}{W}", "Instant",
        "Populate. Creatures you control gain indestructible until end of turn. (To populate, create a token that's a copy of a creature token you control.)")
    private val breach = Cards.invent("Vortex Breach", "{1}{U}", "Instant", """
        Return target nonland permanent you don't control to its owner's hand.
        Overload {6}{U} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val barrage = Cards.invent("Foundry Barrage", "{1}{R}", "Sorcery", """
        Foundry Barrage deals 4 damage to target creature you don't control.
        Overload {3}{R}{R}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val lurcher = Cards.invent("Gutter Lurcher", "{2}{B}", "Creature — Zombie", """
        Swampwalk (This creature can't be blocked as long as defending player controls a Swamp.)
        Scavenge {2}{B} ({2}{B}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
    """, "3/1")
    private val ghoul = Cards.invent("Carouse Ghoul", "{2}{B}", "Creature — Zombie",
        "Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)", "3/3")
    private val juggler = Cards.invent("Bone Juggler", "{1}{B}", "Creature — Skeleton Warrior", """
        Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
        {1}{B}: Regenerate this creature.
    """, "1/1")

    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val pinger = Cards.invent("Spark Tinker", "{1}{R}", "Creature — Human", "{T}: Spark Tinker deals 1 damage to any target.", "1/1")
    private val relic = Cards.invent("Brass Relic", "{2}", "Artifact")
    private val murder = Cards.invent("Quick Execution", "{1}{B}{B}", "Instant", "Destroy target creature.")

    private fun Scenario.overload(name: String): CastWay = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
    }.origin)

    private fun Scenario.yes() = answer(Answer.YesNo(true))

    // ---- Detain (CR 701.35a)

    /** Until the detaining player's next turn the creature can't attack or block and its activated abilities can't be
     * activated; then it can again. */
    // CR 701.35a
    @Test
    fun detainedUntilYourNextTurn() = scenario {
        alice.hand(stay)
        alice.library("Island", "Island", "Island")
        bob.battlefield(pinger)
        bob.library("Swamp", "Swamp")
        alice.castsWithoutCosts(stay, target = pinger)
        resolve()
        expect {
            alice.hand.names shouldBe listOf("Island")
            pinger hasMechanic { it is Mechanic.CantAttack }
            pinger hasMechanic { it is Mechanic.CantBlock }
            pinger hasMechanic { it == Mechanic.CantBeActivated(manaAbilities = true) }
        }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        assertTrue(Engine.options(state, bob.id).none { it is Input.Activate && it.source == card(pinger) }, "a detained creature's abilities can't be activated")
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { pinger lacksMechanic { it is Mechanic.CantAttack } }
    }

    // ---- Populate (CR 701.36a, b)

    /** The token just created is copied. */
    // CR 701.36a
    @Test
    fun populateCopiesACreatureToken() = scenario {
        alice.hand(wings)
        alice.castsWithoutCosts(wings)
        resolve()
        expect {
            alice.battlefield.names shouldBe listOf("Bird Token", "Bird Token")
        }
    }

    /** Without a creature token nothing is created; the rest of the spell still happens. */
    // CR 701.36b
    @Test
    fun populateWithoutTokensDoesNothing() = scenario {
        alice.hand(bulwark)
        alice.battlefield(bear)
        alice.castsWithoutCosts(bulwark)
        resolve()
        expect {
            alice.battlefield.names shouldBe listOf(bear)
            bear hasMechanic { it == Mechanic.Indestructible }
        }
    }

    // ---- Overload (CR 702.96)

    /** For the overload cost the spell has no target and returns each nonland permanent you don't control. */
    // CR 702.96a, b
    @Test
    fun overloadAffectsEach() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", bear)
        alice.hand(breach)
        bob.battlefield(pinger, relic, "Swamp")
        alice.casts(breach, way = overload(breach))
        allPass()
        expect {
            bob.hand.names shouldBe listOf(pinger, relic)
            bob.battlefield.names shouldBe listOf("Swamp")
            alice.battlefield has bear
        }
    }

    /** Cast for its mana cost it has its target only. */
    // CR 702.96a
    @Test
    fun withoutOverloadOneTarget() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(barrage)
        bob.battlefield(pinger, bear)
        alice.casts(barrage, target = bear)
        allPass()
        expect {
            bob.graveyard.names shouldBe listOf(bear)
            bob.battlefield has pinger
        }
    }

    // ---- Scavenge (CR 702.97a)

    /** From the graveyard, exiling the card: counters equal to its power. */
    // CR 702.97a
    @Test
    fun scavengePutsCountersEqualToPower() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.graveyard(lurcher)
        alice.activates(lurcher, target = bear)
        allPass()
        expect {
            bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 3
            alice.exile.names shouldBe listOf(lurcher)
        }
    }

    /** Only as a sorcery: not in another player's turn. */
    // CR 702.97a
    @Test
    fun scavengeOnlyAsASorcery() = scenario {
        turn(2, active = bob)
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.graveyard(lurcher)
        assertTrue(Engine.options(state, alice.id).none { it is Input.Activate && it.source == card(lurcher) })
    }

    // ---- Unleash (CR 702.98a)

    /** Entering with the counter, it can't block; entering without it, it can. */
    // CR 702.98a
    @Test
    fun unleashedCantBlock() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(ghoul, ghoul)
        alice.casts(ghoul)
        allPass()
        yes()
        alice.casts(ghoul)
        allPass()
        answer(Answer.YesNo(false))
        expect {
            card(ghoul, 0).counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            card(ghoul, 0).power shouldBe 4
            card(ghoul, 1).counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
        assertTrue(view()[card(ghoul, 0)]!!.has { it is Mechanic.CantBlock })
        assertTrue(!view()[card(ghoul, 1)]!!.has { it is Mechanic.CantBlock })
    }

    // ---- Regenerate (CR 701.19a)

    /** The shield replaces the destruction: tapped, damage removed, still on the battlefield. */
    // CR 701.19a
    @Test
    fun regenerationShield() = scenario {
        alice.battlefield("Swamp", "Swamp", juggler)
        bob.battlefield("Swamp", "Swamp", "Swamp")
        bob.hand(murder)
        alice.activates(juggler, ability = 0)
        allPass()
        bob.castsWithoutCosts(murder, target = juggler)
        resolve()
        expect {
            alice.battlefield has juggler
            card(juggler).tapped shouldBe true
        }
    }
}
