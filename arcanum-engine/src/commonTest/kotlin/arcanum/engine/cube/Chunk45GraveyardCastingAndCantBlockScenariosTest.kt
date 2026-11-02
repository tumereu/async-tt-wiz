package arcanum.engine.cube

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.query.Evaluator
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.Bindings
import arcanum.engine.state.NamedValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Chunk 45 in play: creatures that come back from the graveyard or are cast from it, from exile and from the top of
 * the library, and creatures that can't block. */
class Chunk45GraveyardCastingAndCantBlockScenariosTest {
    /** Pays the open mana payment with convoke/delve objects by name. */
    private fun Scenario.payWithObjects(vararg names: String) {
        val d = assertIs<Decision.ManaPayment>(pending)
        answer(Answer.ManaPayment(names.map { name ->
            d.options.filterIsInstance<ManaUse.Special>().first { it.objects == listOf(card(name)) }
        }))
    }

    /** Bob attacks alice with [attacker] in his turn; the game waits for alice's blockers. */
    private fun Scenario.bobAttacksWith(attacker: String) {
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(attacker)
        allPass()
        assertEquals(TurnStep.DeclareBlockers, state.turn.step)
    }

    /** Corpse Gnawer: can't block; sacrifice a creature to put a +1/+1 counter on it. */
    @Test
    fun corpseGnawer() = scenario {
        alice.battlefield("Swamp")
        alice.battlefield("Elvish Seer")
        alice.hand("Corpse Gnawer")
        alice.casts("Corpse Gnawer")
        allPass()
        alice.activates("Corpse Gnawer", choosing = listOf("Elvish Seer"))
        expect { alice.graveyard has "Elvish Seer" }
        allPass()
        expect {
            "Corpse Gnawer".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            "Corpse Gnawer".power shouldBe 2
        }
    }

    // CR 509.1a: a creature that can't block isn't declared as a blocker
    @Test
    fun corpseGnawerCantBlock() = scenario {
        bob.battlefield("Elvish Seer")
        alice.battlefield("Corpse Gnawer")
        bobAttacksWith("Elvish Seer")
        assertFailsWith<IllegalArgumentException> { alice.blocks("Corpse Gnawer" to "Elvish Seer") }
    }

    /** Shapeless Exile: every creature type; can't block and can't be blocked. */
    @Test
    fun shapelessExile() = scenario {
        // CR 702.73a
        alice.battlefield("Shapeless Exile")
        bob.battlefield("Elvish Seer")
        expect {
            assertTrue("Shapeless Exile".view.characteristics!!.hasSubtype(Subtype("Elf")))
            assertTrue("Shapeless Exile".view.characteristics!!.hasSubtype(Subtype("Zombie")))
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Shapeless Exile")
        allPass()
        // Nothing can block it, so bob declares no blockers without being asked (P5)
        assertTrue(bob.id in state.combat!!.blockersDeclaredBy)
        assertFailsWith<IllegalArgumentException> { bob.blocks("Elvish Seer" to "Shapeless Exile") }
        allPass()
        expect { bob.life shouldBe 19 }
    }

    /** Gorehaunt: hasty while an opponent is at 10 life or less; landfall brings it back from the graveyard. */
    @Test
    fun gorehauntHasteAtTenLife() = scenario {
        alice.battlefield("Gorehaunt", sick = true)
        bob.life = 10
        expect { "Gorehaunt" hasKeyword Keyword.Haste }
    }

    @Test
    fun gorehauntNoHasteAboveTen() = scenario {
        alice.battlefield("Gorehaunt", sick = true)
        bob.life = 11
        expect { "Gorehaunt" lacksKeyword Keyword.Haste }
    }

    @Test
    fun gorehauntLandfallReturnsIt() = scenario {
        alice.graveyard("Gorehaunt")
        alice.hand("Swamp")
        alice.plays("Swamp")
        expect { stack.names shouldBe listOf("ability of Gorehaunt") }
        allPass()
        decision<Decision.May>()
        alice.answers(Answer.YesNo(true))
        expect {
            alice.battlefield has "Gorehaunt"
            alice.graveyard.isEmpty()
        }
    }

    /** Bloodsworn Duelist: comes back from the graveyard for {1}{B} once you've attacked this turn. */
    @Test
    fun bloodswornDuelistRaid() = scenario {
        alice.battlefield("Swamp", "Swamp", "Elvish Seer")
        alice.graveyard("Bloodsworn Duelist")
        // Not before attacking
        assertFailsWith<IllegalArgumentException> { alice.activates("Bloodsworn Duelist") }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Elvish Seer")
        alice.activates("Bloodsworn Duelist")
        allPass()
        expect {
            alice.battlefield has "Bloodsworn Duelist"
            "Swamp".tapped shouldBe true
            card("Swamp", 1).tapped shouldBe true
        }
    }

    /** Duskflame Phoenix: at the beginning of combat on your turn, if two creatures died this turn, it comes back. */
    // CR 603.4
    @Test
    fun duskflamePhoenixReturns() = scenario {
        alice.battlefield("Corpse Gnawer", "Elvish Seer", "Thicketwood Elves")
        alice.graveyard("Duskflame Phoenix")
        alice.activates("Corpse Gnawer", choosing = listOf("Elvish Seer"))
        allPass()
        alice.activates("Corpse Gnawer", choosing = listOf("Thicketwood Elves"))
        allPass()
        advanceTo(TurnStep.BeginningOfCombat)
        expect { stack.names shouldBe listOf("ability of Duskflame Phoenix") }
        allPass()
        expect {
            alice.battlefield has "Duskflame Phoenix"
            "Duskflame Phoenix" hasKeyword Keyword.Haste
        }
    }

    // CR 603.4: one creature died: no trigger
    @Test
    fun duskflamePhoenixStaysWithOneDeath() = scenario {
        alice.battlefield("Corpse Gnawer", "Elvish Seer")
        alice.graveyard("Duskflame Phoenix")
        alice.activates("Corpse Gnawer", choosing = listOf("Elvish Seer"))
        allPass()
        advanceTo(TurnStep.BeginningOfCombat)
        expect {
            stack.isEmpty()
            alice.graveyard has "Duskflame Phoenix"
        }
    }

    /** Smoldering Spite: from the graveyard, with a Mountain, gives your creatures haste. */
    // CR 113.6b
    @Test
    fun smolderingSpiteFromTheGraveyard() = scenario {
        alice.battlefield("Mountain")
        alice.battlefield("Elvish Seer", sick = true)
        alice.graveyard("Smoldering Spite")
        expect { "Elvish Seer" hasKeyword Keyword.Haste }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Elvish Seer")
        expect { "Elvish Seer".tapped shouldBe true }
    }

    @Test
    fun smolderingSpiteNeedsAMountain() = scenario {
        alice.battlefield("Elvish Seer", sick = true)
        alice.graveyard("Smoldering Spite")
        expect { "Elvish Seer" lacksKeyword Keyword.Haste }
    }

    /** Tombscuttler: castable from the graveyard while you control a Zombie. */
    // CR 601.3a
    @Test
    fun tombscuttlerFromTheGraveyard() = scenario {
        alice.battlefield("Swamp", "Corpse Gnawer")
        alice.graveyard("Tombscuttler")
        alice.casts("Tombscuttler")
        expect { stack.names shouldBe listOf("Tombscuttler") }
        allPass()
        expect { alice.battlefield has "Tombscuttler" }
    }

    /** Grommak: paid only by convoke and delve, castable from the graveyard; trample. */
    // CR 702.51a, 702.66a
    @Test
    fun grommakFromTheGraveyard() = scenario {
        alice.battlefield("Elvish Seer", "Thicketwood Elves", "Forest")
        alice.graveyard("Grommak, Risen Charnelhost", "Storm Spark", "Denial", "Mountain", "Swamp", "Plains")
        perform(Input.Cast(alice.id, card("Grommak, Risen Charnelhost")))
        val d = assertIs<Decision.ManaPayment>(pending)
        assertTrue(d.options.none { it is ManaUse.Ability || it is ManaUse.Pool })
        payWithObjects("Elvish Seer", "Thicketwood Elves", "Storm Spark", "Denial", "Mountain", "Swamp", "Plains")
        allPass()
        expect {
            alice.battlefield has "Grommak, Risen Charnelhost"
            "Grommak, Risen Charnelhost" hasKeyword Keyword.Trample
            "Forest".tapped shouldBe false
            alice.exile.size shouldBe 5
        }
    }

    /** Inspector's Firebird: a 2/2 flier with haste. */
    @Test
    fun inspectorsFirebirdCast() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Inspector's Firebird")
        alice.casts("Inspector's Firebird")
        allPass()
        expect {
            alice.battlefield has "Inspector's Firebird"
            "Inspector's Firebird" hasKeyword Keyword.Flying
            "Inspector's Firebird" hasKeyword Keyword.Haste
        }
    }

    /** Bestowed from the graveyard for {R} and collecting evidence 6: enchanted creature gets +2/+2, flying, haste. */
    // CR 702.103a, 701.59a
    @Test
    fun inspectorsFirebirdBestowedFromTheGraveyard() = scenario {
        alice.battlefield("Mountain", "Elvish Seer")
        alice.graveyard("Inspector's Firebird", "Duskflame Phoenix", "Smoldering Spite")
        val bestow = view()[card("Inspector's Firebird")]!!.characteristics!!.abilities.first { inst ->
            (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
        }.origin
        alice.casts("Inspector's Firebird", way = arcanum.engine.input.CastWay(alternative = bestow), target = "Elvish Seer",
            choosing = listOf("Duskflame Phoenix", "Smoldering Spite"))
        allPass()
        expect {
            "Elvish Seer".power shouldBe 3
            "Elvish Seer" hasKeyword Keyword.Flying
            alice.exile.size shouldBe 2
        }
    }

    /** Velmora: lifelink; once during each of your turns, cast a permanent spell with mana value 2 or less from your
     * graveyard. */
    @Test
    fun velmoraCastsFromTheGraveyardOncePerTurn() = scenario {
        alice.battlefield("Velmora of the Sleep-Den", "Forest", "Forest")
        alice.graveyard("Elvish Seer", "Thicketwood Elves")
        expect { "Velmora of the Sleep-Den" hasKeyword Keyword.Lifelink }
        alice.casts("Elvish Seer")
        allPass()
        expect { alice.battlefield has "Elvish Seer" }
        assertFailsWith<IllegalArgumentException> { alice.casts("Thicketwood Elves") }
    }

    // CR 702.15b
    @Test
    fun velmoraLifelink() = scenario {
        alice.battlefield("Velmora of the Sleep-Den")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Velmora of the Sleep-Den")
        advanceTo(TurnStep.EndOfCombat)
        expect {
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    // CR 702.139a, 116.2g: the revealed companion goes to the hand from outside the game for {3}
    @Test
    fun velmoraAsCompanion() = scenario {
        alice.battlefield("Plains", "Swamp", "Forest")
        val id = put(alice, listOf("Velmora of the Sleep-Den"), Zone.Outside).single()
        state.objects[id] = state.objOrThrow(id).copy(memory = Bindings.EMPTY.with(Evaluator.REVEALED_COMPANION, NamedValue.Number(1)))
        val action = view()[id]!!.characteristics!!.abilities.first { it.ability is Ability.SpecialAction }
        perform(Input.Activate(alice.id, id, action.origin))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect {
            alice.hand has "Velmora of the Sleep-Den"
            stack.isEmpty()
        }
    }

    /** Colossal Rumblebeast: suspend 10 for {R}; while suspended, sacrifice a permanent to remove a time counter. */
    // CR 702.62a
    @Test
    fun colossalRumblebeastSuspended() = scenario {
        alice.battlefield("Mountain", "Swamp")
        alice.hand("Colossal Rumblebeast")
        val suspend = view()[card("Colossal Rumblebeast")]!!.characteristics!!.abilities.first { it.ability is Ability.SpecialAction }
        perform(Input.Activate(alice.id, card("Colossal Rumblebeast"), suspend.origin))
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect {
            alice.exile has "Colossal Rumblebeast"
            "Colossal Rumblebeast".counters(CounterKind.Time) shouldBe 10
            "Mountain".tapped shouldBe true
        }
        alice.activates("Colossal Rumblebeast", choosing = listOf("Swamp"))
        allPass()
        expect {
            "Colossal Rumblebeast".counters(CounterKind.Time) shouldBe 9
            alice.graveyard has "Swamp"
        }
    }

    // CR 702.62a: not suspended (in hand): the ability can't be activated
    @Test
    fun colossalRumblebeastOnlyWhileSuspended() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Colossal Rumblebeast")
        assertFailsWith<IllegalArgumentException> { alice.activates("Colossal Rumblebeast", choosing = listOf("Swamp")) }
    }

    // CR 702.62a: the last time counter removed, it's cast without paying and has haste
    @Test
    fun colossalRumblebeastArrives() = scenario {
        alice.battlefield("Swamp")
        put(alice, listOf("Colossal Rumblebeast"), Zone.Exile, counters = listOf(CounterKind.Time to 1))
        alice.activates("Colossal Rumblebeast", choosing = listOf("Swamp"))
        allPass()
        // The last counter removed: the cast trigger
        expect { stack.names shouldBe listOf("ability of Colossal Rumblebeast") }
        allPass()
        alice.answers(Answer.YesNo(true))
        expect { stack.names shouldBe listOf("Colossal Rumblebeast") }
        allPass()
        expect {
            alice.battlefield has "Colossal Rumblebeast"
            "Colossal Rumblebeast" hasKeyword Keyword.Haste
        }
    }

    /** Seer of Harvestide: look at the top card any time, play lands from the top, and with coven cast creatures from
     * the top. */
    @Test
    fun seerOfHarvestidePlaysLandsFromTheTop() = scenario {
        alice.battlefield("Seer of Harvestide")
        alice.library("Forest", "Island")
        expect {
            assertTrue(view.players[alice.id]!!.mechanics.any { it.mechanic is Mechanic.MayLookAt })
        }
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
        // CR 305.2: one land a turn
        assertFailsWith<IllegalArgumentException> { alice.plays("Island") }
    }

    // Coven: powers 1, 2 and 3
    @Test
    fun seerOfHarvestideCovenCastsCreaturesFromTheTop() = scenario {
        alice.battlefield("Seer of Harvestide", "Elvish Seer", "Duskflame Phoenix", "Forest")
        alice.library("Thicketwood Elves")
        alice.casts("Thicketwood Elves")
        allPass()
        expect { alice.battlefield has "Thicketwood Elves" }
    }

    @Test
    fun seerOfHarvestideWithoutCoven() = scenario {
        alice.battlefield("Seer of Harvestide", "Elvish Seer", "Forest")
        alice.library("Thicketwood Elves")
        assertFailsWith<IllegalArgumentException> { alice.casts("Thicketwood Elves") }
    }
}
