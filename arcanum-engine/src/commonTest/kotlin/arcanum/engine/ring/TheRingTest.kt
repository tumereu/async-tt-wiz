package arcanum.engine.ring

import arcanum.api.ObjectMark
import arcanum.api.Supertype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Ring tempts you (CR 701.54): the emblem, the Ring-bearer, the temptation count and "whenever the Ring tempts you". */
class TheRingTest {
    private val lurker = Cards.invent("Ring Barrow Lurker", "{1}{B}", "Creature — Wraith", "When this creature enters, the Ring tempts you.", "2/1")
    private val wanderer = Cards.invent("Ring Grey Wanderer", "{2}{U}", "Legendary Creature — Avatar Wizard",
        "Whenever the Ring tempts you, if you chose a creature other than Ring Grey Wanderer as your Ring-bearer, draw a card.", "2/3")
    private val beckon = Cards.invent("Ring Dark Beckoning", "{B}", "Sorcery", "The Ring tempts you.")
    private val bear = Cards.invent("Ring Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val giant = Cards.invent("Ring Hill Giant", "{3}{R}", "Creature — Giant", "", "3/3")
    private val ringward = Cards.invent("Ring Bold Ringward", "{1}{R}", "Creature — Human Soldier", "As long as Ring Bold Ringward is your Ring-bearer, it gets +2/+0.", "1/1")

    private fun Scenario.ringBearer(): String? = state.battlefield.firstOrNull { ObjectMark.RingBearer in state.objOrThrow(it).marks }
        ?.let { state.objOrThrow(it).definition?.faces?.first()?.name }

    /** [player] gains control of [name] by an effect made by hand (CR 613.1b). */
    private fun Scenario.gainControl(player: arcanum.engine.scenario.ScenarioPlayer, name: String) {
        val id = card(name)
        change(arcanum.engine.change.Changes.effect(state) { ts ->
            arcanum.engine.state.EffectObject(arcanum.engine.state.EffectId("ring-control:$ts"), listOf(arcanum.api.Mechanic.ControlledBy(arcanum.api.Players.You)),
                affects = null, locked = listOf(id), source = null, controller = player.id, timestamp = ts, createdTurn = state.turn.number,
                duration = arcanum.api.Duration.Indefinitely)
        })
    }

    private fun Scenario.emblems(): Int = state.command.count { state.obj(it)?.definition?.faces?.first()?.name == TheRing.EMBLEM }

    // CR 701.54a, c: the first temptation gives the emblem and makes the chosen creature the Ring-bearer, legendary
    @Test
    fun firstTemptationGivesTheEmblemAndABearer() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.hand(lurker)
        alice.casts(lurker)
        allPass()
        allPass()
        assertTrue(pending is Decision.ChooseObjects)
        alice.chooses(bear)
        assertEquals(1, emblems())
        assertEquals(bear, ringBearer())
        assertEquals(1, TheRing.temptations(state, alice.id))
        assertTrue(Supertype.Legendary in view()[card(bear)]!!.characteristics!!.supertypes)
        assertFalse(Supertype.Legendary in view()[card(lurker)]!!.characteristics!!.supertypes)
    }

    // CR 701.54a, c: tempted again, no second emblem; the new choice replaces the old Ring-bearer
    @Test
    fun anotherBearerReplacesTheFirst() = scenario {
        alice.battlefield("Swamp", "Swamp", bear, giant)
        alice.hand(beckon, beckon)
        alice.casts(beckon)
        allPass()
        alice.chooses(bear)
        alice.casts(beckon)
        allPass()
        alice.chooses(giant)
        assertEquals(1, emblems())
        assertEquals(giant, ringBearer())
        assertEquals(2, TheRing.temptations(state, alice.id))
        assertFalse(ObjectMark.RingBearer in state.objOrThrow(card(bear)).marks)
    }

    // CR 701.54d: the Ring tempts a player even when they control no creature to choose
    @Test
    fun temptedWithNoCreature() = scenario {
        alice.battlefield("Swamp")
        alice.hand(beckon)
        alice.casts(beckon)
        allPass()
        assertEquals(1, emblems())
        assertEquals(null, ringBearer())
        assertEquals(1, TheRing.temptations(state, alice.id))
    }

    // CR 701.54d: "whenever the Ring tempts you, if you chose a creature other than [this]": the creature chosen is the event's object
    @Test
    fun wheneverTheRingTemptsYouSeesTheChoice() = scenario {
        alice.battlefield("Swamp", "Swamp", wanderer, bear)
        alice.hand(beckon, beckon)
        alice.library("Island", "Island")
        alice.casts(beckon)
        allPass()
        alice.chooses(wanderer)
        // No trigger: the Ring-bearer chosen is this creature
        assertTrue(state.stack.isEmpty())
        expect { alice.hand.size shouldBe 1 }
        alice.casts(beckon)
        allPass()
        alice.chooses(bear)
        allPass()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 701.54c: the Ring-bearer can't be blocked by creatures with greater power
    @Test
    fun cantBeBlockedByGreaterPower() = scenario {
        alice.battlefield("Swamp", bear)
        alice.hand(beckon)
        bob.battlefield(giant)
        alice.casts(beckon)
        allPass()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        allPass()
        kotlin.test.assertFailsWith<IllegalArgumentException> { bob.blocks(giant to bear) }
    }

    // CR 701.54c: tempted twice, the Ring-bearer attacking loots
    @Test
    fun twoTemptationsLootOnAttack() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.hand(beckon, beckon)
        alice.library("Island", "Island")
        alice.casts(beckon)
        allPass()
        alice.casts(beckon)
        allPass()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        allPass()
        // Draw, then discard the card drawn
        if (pending is Decision.ChooseObjects) alice.chooses("Island")
        expect {
            alice.hand.size shouldBe 0
            alice.graveyard has "Island"
        }
    }

    // CR 701.54c: tempted three times, a creature blocking the Ring-bearer is sacrificed at end of combat
    @Test
    fun threeTemptationsSacrificeTheBlocker() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", giant)
        alice.hand(beckon, beckon, beckon)
        alice.library("Island", "Island", "Island")
        bob.battlefield(bear)
        repeat(3) { alice.casts(beckon); allPass() }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(giant)
        allPass()
        if (pending is Decision.ChooseObjects) alice.chooses("Island")
        allPass()
        bob.blocks(bear to giant)
        advanceTo(TurnStep.EndOfCombat)
        allPass()
        expect { bob.graveyard has bear }
    }

    // CR 701.54c: tempted four times, combat damage to a player makes each opponent lose 3 life
    @Test
    fun fourTemptationsDrainOnDamage() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(beckon, beckon, beckon, beckon)
        alice.library("Island", "Island", "Island")
        repeat(4) { alice.casts(beckon); allPass() }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        allPass()
        if (pending is Decision.ChooseObjects) alice.chooses("Island")
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 15 }
    }

    // CR 701.54e: "as long as [this] is your Ring-bearer"; CR 701.54a: another player gaining control ends it
    @Test
    fun bearerConditionAndControlChange() = scenario {
        alice.battlefield("Swamp", ringward)
        alice.hand(beckon)
        alice.casts(beckon)
        allPass()
        expect { ringward.power shouldBe 3 }
        gainControl(bob, ringward)
        settle()
        assertEquals(null, ringBearer())
        expect { ringward.power shouldBe 1 }
    }

    // CR 701.54b: leaving the battlefield ends it; the new object isn't a Ring-bearer
    @Test
    fun leavingEndsIt() = scenario {
        alice.battlefield("Swamp", bear)
        alice.hand(beckon)
        alice.casts(beckon)
        allPass()
        move(bear, Zone.Hand)
        assertEquals(null, ringBearer())
        assertFalse(ObjectMark.RingBearer in state.objOrThrow(card(bear)).marks)
    }
}
