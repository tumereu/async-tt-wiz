package arcanum.engine.sets.rvr

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.option
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** Ravnica Remastered chunk 01 in play: addendum (cast during your own main phase or not), an Aura that leaves mana abilities usable,
 * an Aura that removes abilities and sets base power and toughness, hellbent on an Aura, an Equipment whose bonuses follow its
 * creature's tapped state, damage redirected to the equipped creature, and a copy trigger that ignores mana abilities. */
class RvrChunk01AddendumAurasEquipmentScenariosTest {
    private val wardensFervor = Cards.invent("Warden's Fervor", "{W}", "Instant", """
        Target creature gets +2/+2 until end of turn.
        Addendum — If you cast this spell during your main phase, that creature gains flying until end of turn.
    """)
    private val swiftVerdict = Cards.invent("Swift Verdict", "{1}{W}", "Instant", """
        Swift Verdict deals 3 damage to target tapped creature.
        Addendum — If you cast this spell during your main phase, it deals 5 damage instead.
    """)
    private val unyieldingPhalanx = Cards.invent("Unyielding Phalanx", "{2}{W}", "Instant", """
        Creatures you control gain indestructible until end of turn.
        Addendum — If you cast this spell during your main phase, put a +1/+1 counter on each of those creatures and they gain vigilance until end of turn.
    """)
    private val shackles = Cards.invent("Shackles of Devotion", "{3}{W}", "Enchantment — Aura", """
        Enchant permanent
        When this Aura enters, you gain 4 life.
        Enchanted permanent can't attack or block, and its activated abilities can't be activated unless they're mana abilities.
    """)
    private val magesUnmaking = Cards.invent("Mage's Unmaking", "{1}{U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature loses all abilities and has base power and toughness 1/1.
    """)
    private val hungerForHavoc = Cards.invent("Hunger for Havoc", "{R}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+0.
        Hellbent — Enchanted creature gets an additional +2/+0 as long as you have no cards in hand.
    """)
    private val twinHouses = Cards.invent("Blade of the Twin Houses", "{4}", "Artifact — Equipment", """
        As long as equipped creature is tapped, tapped creatures you control get +2/+0.
        As long as equipped creature is untapped, untapped creatures you control get +0/+2.
        {3}: You may tap or untap equipped creature.
        Equip {3}
    """)
    private val outcastsAegis = Cards.invent("Outcast's Aegis", "{5}", "Artifact — Equipment", """
        All damage that would be dealt to you is dealt to equipped creature instead.
        Equip {3}
    """)
    private val cuffs = Cards.invent("Phantasmist's Cuffs", "{2}", "Artifact — Equipment", """
        Whenever an ability of equipped creature is activated, if it isn't a mana ability, copy that ability. You may choose new targets for the copy.
        Equip {3}
    """)

    private val squire = Cards.invent("Fervent Squire", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val recruit = Cards.invent("Phalanx Recruit", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val defendant = Cards.invent("Verdict Defendant", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val druid = Cards.invent("Shackled Druid", "{1}{G}", "Creature — Elf Druid", """
        {T}: Add {G}.
        {1}: This creature gets +1/+1 until end of turn.
    """, "1/1")
    private val drake = Cards.invent("Unmade Drake", "{2}{U}", "Creature — Drake", "Flying", "3/3")
    private val rider = Cards.invent("Havoc Rider", "{1}{R}", "Creature — Human Warrior", "", "2/2")
    private val sentry = Cards.invent("Twin Houses Sentry", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val bearer = Cards.invent("Aegis Bearer", "{3}{W}", "Creature — Giant", "", "4/4")
    private val shock = Cards.invent("Gate Steward's Jolt", "{R}", "Instant", "Gate Steward's Jolt deals 2 damage to any target.")

    // CR 207.2c (addendum), 505.1: cast in your own precombat main phase, the creature also gains flying
    @Test
    fun wardensFervorInYourMainPhaseGivesFlying() = scenario {
        alice.battlefield("Plains", squire)
        alice.hand(wardensFervor)
        alice.casts(wardensFervor, target = squire)
        resolveAll()
        expect {
            squire.power shouldBe 4
            squire hasKeyword Keyword.Flying.kind
        }
    }

    // CR 505.1: the beginning of combat step isn't a main phase, so only the +2/+2
    @Test
    fun wardensFervorInCombatGivesNoFlying() = scenario {
        alice.battlefield("Plains", squire)
        alice.hand(wardensFervor)
        turn(step = TurnStep.BeginningOfCombat)
        alice.casts(wardensFervor, target = squire)
        resolveAll()
        expect {
            squire.power shouldBe 4
            squire lacksKeyword Keyword.Flying.kind
        }
    }

    // CR 505.1: "your main phase": another player's main phase isn't yours (three players, cast in Bob's turn)
    @Test
    fun wardensFervorInAnOpponentsMainPhaseGivesNoFlying() = scenario(players = 3) {
        alice.battlefield("Plains", squire)
        alice.hand(wardensFervor)
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain, priority = alice)
        alice.casts(wardensFervor, target = squire)
        resolveAll()
        expect {
            squire.power shouldBe 4
            squire lacksKeyword Keyword.Flying.kind
        }
    }

    // CR 207.2c: 5 damage instead of 3 in your main phase
    @Test
    fun swiftVerdictDealsFiveInYourMainPhase() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(swiftVerdict)
        bob.battlefield(defendant, tapped = true)
        alice.casts(swiftVerdict, target = defendant)
        resolveAll()
        expect { bob.graveyard has defendant }
    }

    // CR 505.1: in an opponent's turn it deals the usual 3
    @Test
    fun swiftVerdictDealsThreeOutsideYourMainPhase() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(swiftVerdict)
        bob.battlefield(defendant, tapped = true)
        turn(number = 2, active = bob, step = TurnStep.PostcombatMain, priority = alice)
        alice.casts(swiftVerdict, target = defendant)
        resolveAll()
        expect {
            bob.battlefield has defendant
            defendant.view.obj?.damage shouldBe 3
        }
    }

    // CR 608.2c: "each of those creatures" are the creatures given indestructible; the opponent's creature gets nothing
    @Test
    fun unyieldingPhalanxInYourMainPhaseAddsCountersAndVigilance() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", recruit, squire)
        alice.hand(unyieldingPhalanx)
        bob.battlefield(defendant)
        alice.casts(unyieldingPhalanx)
        resolveAll()
        expect {
            recruit hasKeyword Keyword.Indestructible.kind
            recruit hasKeyword Keyword.Vigilance.kind
            recruit.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            squire.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            squire hasKeyword Keyword.Vigilance.kind
            defendant.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            defendant lacksKeyword Keyword.Indestructible.kind
        }
    }

    // CR 505.1: in combat only indestructible
    @Test
    fun unyieldingPhalanxInCombatGivesOnlyIndestructible() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", recruit)
        alice.hand(unyieldingPhalanx)
        turn(step = TurnStep.BeginningOfCombat)
        alice.casts(unyieldingPhalanx)
        resolveAll()
        expect {
            recruit hasKeyword Keyword.Indestructible.kind
            recruit lacksKeyword Keyword.Vigilance.kind
            recruit.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 605.1a: its mana ability can still be activated, its other activated ability can't
    @Test
    fun shacklesLeaveManaAbilitiesUsable() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(shackles)
        bob.battlefield(druid, "Forest")
        alice.casts(shackles, target = druid)
        resolveAll()
        assertEquals(24, alice.life)
        alice.passes()
        assertFails { bob.activates(druid, ability = 1, paying = listOf("Forest")) }
        bob.activates(druid)
        expect { bob.pool.size shouldBe 1 }
    }

    // CR 613.1f, 613.4b, 613.4c: abilities lost in layer 6, base 1/1 in layer 7b, its +1/+1 counter still applies in 7c
    @Test
    fun magesUnmakingMakesAOneOneWithoutAbilities() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(magesUnmaking)
        bob.battlefield(drake, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.casts(magesUnmaking, target = drake)
        resolveAll()
        expect {
            drake.power shouldBe 2
            drake.toughness shouldBe 2
            drake lacksKeyword Keyword.Flying.kind
        }
    }

    // CR 207.2c (hellbent): no cards in hand, +4/+0 in all
    @Test
    fun hungerForHavocWithAnEmptyHand() = scenario {
        alice.battlefield("Mountain", rider)
        alice.hand(hungerForHavoc)
        alice.casts(hungerForHavoc, target = rider)
        resolveAll()
        expect { rider.power shouldBe 6 }
    }

    // CR 611.3a: with a card in hand only the first +2/+0
    @Test
    fun hungerForHavocWithACardInHand() = scenario {
        alice.battlefield("Mountain", rider)
        alice.hand(hungerForHavoc, "Island")
        alice.casts(hungerForHavoc, target = rider)
        resolveAll()
        expect { rider.power shouldBe 4 }
    }

    // CR 611.3a: the bonus follows the equipped creature's tapped state; tapping it with the ability swaps the bonus
    @Test
    fun bladeOfTheTwinHousesFollowsTheEquippedCreature() = scenario {
        alice.battlefield(twinHouses, sentry, squire, "Plains", "Plains", "Plains")
        alice.battlefield(recruit, tapped = true)
        attach(twinHouses, sentry)
        expect {
            sentry.toughness shouldBe 4
            squire.toughness shouldBe 4
            recruit.toughness shouldBe 2
            recruit.power shouldBe 2
        }
        alice.activates(twinHouses)
        resolveAll()
        may(true)
        option("tap")
        resolveAll()
        expect {
            sentry.tapped shouldBe true
            sentry.power shouldBe 4
            recruit.power shouldBe 4
            squire.power shouldBe 2
            squire.toughness shouldBe 2
        }
    }

    // CR 301.5c: unattached, "equipped creature" is no creature, so neither condition holds
    @Test
    fun bladeOfTheTwinHousesUnattachedDoesNothing() = scenario {
        alice.battlefield(twinHouses, squire)
        alice.battlefield(recruit, tapped = true)
        expect {
            squire.toughness shouldBe 2
            recruit.power shouldBe 2
        }
    }

    // CR 614.1a: damage to you is dealt to the equipped creature instead
    @Test
    fun outcastsAegisRedirectsToTheEquippedCreature() = scenario {
        alice.battlefield(outcastsAegis, bearer)
        attach(outcastsAegis, bearer)
        bob.battlefield("Mountain")
        bob.hand(shock)
        turn(number = 2, active = bob)
        bob.casts(shock, target = "alice")
        resolveAll()
        assertEquals(20, alice.life)
        expect { bearer.view.obj!!.damage shouldBe 2 }
    }

    // CR 614.1a, 301.5c: with no equipped creature there's nothing to redirect to, so you're dealt the damage
    @Test
    fun outcastsAegisUnattachedLeavesTheDamage() = scenario {
        alice.battlefield(outcastsAegis, bearer)
        bob.battlefield("Mountain")
        bob.hand(shock)
        turn(number = 2, active = bob)
        bob.casts(shock, target = "alice")
        resolveAll()
        assertEquals(18, alice.life)
        expect { bearer.view.obj!!.damage shouldBe 0 }
    }

    // CR 605.1a, 603.4: a mana ability of the equipped creature isn't copied (audit: "if it isn't a mana ability"); its other
    // ability is
    @Test
    fun phantasmistsCuffsIgnoreManaAbilities() = scenario {
        alice.battlefield(cuffs, druid)
        attach(cuffs, druid)
        alice.activates(druid)
        expect {
            alice.pool.size shouldBe 1
            stack.isEmpty()
        }
        alice.activates(druid, ability = 1)
        expect { stack.size shouldBe 2 }
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { druid.power shouldBe 3 }
    }
}
