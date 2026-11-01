package arcanum.engine.review

import arcanum.api.Ability
import arcanum.api.Color
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.StateDiff
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** Review 1: casting spells (CR 601), targets on resolution (CR 608.2b), costs (CR 118, 601.2f). */
class Review1CastingTest {
    private fun Scenario.alternative(name: String): CastWay = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
    }.origin)

    /** Pays the open mana payment with the engine's suggestion. */
    private fun Scenario.paySuggested() {
        val d = decision<Decision.ManaPayment>()
        answer(Answer.ManaPayment(d.suggestion))
    }

    /** The state as it was, for "nothing changed" checks: the timestamp counter aside (D34). */
    private fun Scenario.assertSameAs(before: arcanum.engine.state.GameState) {
        val now = state.deepCopy().also { it.timestampCounter = before.timestampCounter }
        if (now != before) fail("The game isn't as before:\n" + StateDiff.between(before, now))
    }

    /** A spell all of whose targets are gone doesn't resolve and goes to the graveyard. */
    // CR 608.2b
    @Test
    fun spellWithItsOnlyTargetGoneDoesntResolve() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Thicketwood Elves")
        alice.casts("Storm Spark", target = "Thicketwood Elves")
        move("Thicketwood Elves", Zone.Hand)
        allPass()
        expect {
            alice.graveyard has "Storm Spark"
            bob.hand has "Thicketwood Elves"
        }
        assertTrue(applied.any { it is Change.StackItemRemoved && Meaning.Fizzled in it.meanings })
    }

    /** A modal spell with one target gone still does the parts whose targets are legal. */
    // CR 608.2b, 700.2
    @Test
    fun partOfAModalSpellStillHappensWhenAnotherTargetIsGone() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain")
        alice.hand("Spellforge Command")
        bob.battlefield("Sun Band")
        alice.casts("Spellforge Command", modes = listOf(0, 3), targets = listOf("bob", "Sun Band"))
        move("Sun Band", Zone.Hand)
        allPass()
        expect {
            bob.life shouldBe 18
            alice.graveyard has "Spellforge Command"
        }
    }

    /** Protection gained in response makes the target illegal: the spell doesn't resolve. */
    // CR 608.2b, 702.16b
    @Ignore("review1 #2: a granted protection from the chosen color loses the effect's chosen color")
    @Test
    fun protectionInResponseFizzlesTheSpell() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Bestower of Sigils", "Thicketwood Elves")
        alice.casts("Storm Spark", target = "Thicketwood Elves")
        alice.passes()
        bob.activates("Bestower of Sigils", target = "Thicketwood Elves")
        // The colour (or colorless) is chosen as it resolves
        allPass()
        var guard = 5
        while (pending != null && guard-- > 0) when (val d = pending) {
            is Decision.ChooseColor -> answer(Answer.ColorChoice(Color.Red))
            is Decision.ChooseOption -> answer(Answer.Option(d.options.first { it != "colorless" }))
            else -> fail("Unexpected $d")
        }
        allPass()
        expect {
            bob.battlefield has "Thicketwood Elves"
            alice.graveyard has "Storm Spark"
        }
    }

    /** On the Stack, X counts in the mana value: Doomfire with X=1 has mana value 2. */
    // CR 202.3e, 601.2b
    @Test
    fun xCountsInTheManaValueOnTheStack() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Doomfire")
        bob.battlefield("Island")
        bob.hand("Spell Noose")
        alice.casts("Doomfire", target = "bob", x = 1)
        alice.passes()
        bob.casts("Spell Noose", target = "Doomfire")
        allPass()
        expect {
            alice.graveyard has "Doomfire"
            bob.life shouldBe 20
        }
    }

    /** Two cost increases add up: a noncreature, nonartifact spell under both costs {2} more. */
    // CR 601.2f
    @Test
    fun costIncreasesAddUp() = scenario {
        alice.hand("Storm Spark")
        bob.battlefield("Seraphine, Keeper of the Gate", "Magnetite Colossus")
        alice.casts("Storm Spark", target = "bob")
        assertEquals(ManaCost.parse("{2}{R}"), decision<Decision.ManaPayment>().cost)
    }

    /** Cost increases apply to an alternative cost too: Wink Out for 4 life still costs {1} under a tax. */
    // CR 601.2f, 118.9d
    @Test
    fun increaseAppliesToAnAlternativeCost() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Wink Out")
        bob.battlefield("Seraphine, Keeper of the Gate", "Thicketwood Elves")
        perform(Input.Cast(alice.id, card("Wink Out"), alternative("Wink Out")))
        answer(Answer.Targets(listOf(target("Thicketwood Elves"))))
        assertEquals(ManaCost.parse("{1}"), decision<Decision.ManaPayment>().cost)
        paySuggested()
        expect { alice.life shouldBe 16 }
    }

    /** Increases are added before reductions are taken off: {X}{W} with X=0 and a {1} tax, reduced by {2}, costs
     * {W} (not {1}{W}). */
    // CR 601.2f
    @Test
    fun increasesBeforeReductions() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand("Procession of Pale Light", "Plowshare Decree")
        bob.battlefield("Seraphine, Keeper of the Gate", "Tinkered Mite")
        perform(Input.Cast(alice.id, card("Procession of Pale Light")))
        var guard = 10
        while (pending !is Decision.ManaPayment && pending != null && guard-- > 0) when (val d = pending) {
            is Decision.Announce -> answer(Answer.Number(0))
            is Decision.Targets -> answer(Answer.Targets(listOf(target("Tinkered Mite"))))
            is Decision.ChooseObjects -> answer(Answer.Objects(listOf(card("Plowshare Decree"))))
            is Decision.May -> answer(Answer.YesNo(true))
            is Decision.ChooseNumber -> answer(Answer.Number(1))
            else -> fail("Unexpected $d")
        }
        assertEquals(ManaCost.parse("{W}"), decision<Decision.ManaPayment>().cost)
    }

    /** Affinity reduces only generic mana: Cogitating Sentry with three artifacts costs {3}{U}. */
    // CR 702.41a, 118.7a
    @Test
    fun affinityReducesGenericMana() = scenario {
        alice.battlefield("Sun Band", "Verdant Bauble", "Onyx Bauble")
        alice.hand("Cogitating Sentry")
        alice.casts("Cogitating Sentry")
        assertEquals(ManaCost.parse("{3}{U}"), decision<Decision.ManaPayment>().cost)
    }

    /** Flashback with kicker: the alternative cost plus the additional cost (CR 601.2f). */
    // CR 702.34a, 702.33a, 601.2f
    @Test
    fun flashbackPlusKicker() = scenario {
        alice.graveyard("Blast from Before")
        perform(Input.Cast(alice.id, card("Blast from Before")))
        var guard = 10
        while (pending !is Decision.ManaPayment && pending != null && guard-- > 0) when (val d = pending) {
            is Decision.May -> answer(Answer.YesNo("kicker" in d.text.lowercase()))
            is Decision.ChooseNumber -> answer(Answer.Number(0))
            is Decision.Targets -> answer(Answer.Targets(listOf(target("bob"))))
            else -> fail("Unexpected $d")
        }
        assertEquals(ManaCost.parse("{5}{R}{R}"), decision<Decision.ManaPayment>().cost)
    }

    /** A flashback spell with buyback paid is still exiled as it resolves: flashback's replacement wins. */
    // CR 702.34a, 702.27a
    @Test
    fun flashbackWithBuybackIsExiled() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard("Blast from Before")
        perform(Input.Cast(alice.id, card("Blast from Before")))
        var guard = 10
        while (pending !is Decision.ManaPayment && pending != null && guard-- > 0) when (val d = pending) {
            is Decision.May -> answer(Answer.YesNo("buyback" in d.text.lowercase()))
            is Decision.ChooseNumber -> answer(Answer.Number(0))
            is Decision.Targets -> answer(Answer.Targets(listOf(target("bob"))))
            else -> fail("Unexpected $d")
        }
        paySuggested()
        allPass()
        expect {
            bob.life shouldBe 18
            alice.exile has "Blast from Before"
            alice.hand lacks "Blast from Before"
        }
    }

    /** Cancelling a cast from the graveyard leaves no trace, even of a trigger the card's leaving caused. */
    // CR 601.2 (illegal or cancelled cast reversed, 723.1), D58
    @Test
    fun cancelFromGraveyardLeavesNoTrace() = scenario {
        alice.battlefield("Stitchwork Porter", "Sinister Rootwork")
        alice.graveyard("Tombscuttler")
        val before = state.deepCopy()
        perform(Input.Cast(alice.id, card("Tombscuttler")))
        assertTrue(pending is Decision.ManaPayment)
        perform(Input.Cancel)
        assertSameAs(before)
    }

    /** Cancelling a modal spell after its modes, targets and an additional cost's choice leaves no trace. */
    // CR 601.2, D58
    @Test
    fun cancelAfterModesTargetsAndCostChoicesLeavesNoTrace() = scenario {
        alice.hand("Hollow Victory", "Island")
        bob.battlefield("Thicketwood Elves")
        val before = state.deepCopy()
        perform(Input.Cast(alice.id, card("Hollow Victory")))
        var guard = 10
        while (pending !is Decision.ManaPayment && pending != null && guard-- > 0) when (val d = pending) {
            is Decision.Targets -> answer(Answer.Targets(listOf(target("Thicketwood Elves"))))
            is Decision.ChooseOption -> answer(Answer.Option(d.options.first { "Discard" in it }))
            is Decision.ChooseObjects -> answer(Answer.Objects(listOf(card("Island"))))
            else -> fail("Unexpected $d")
        }
        perform(Input.Cancel)
        assertSameAs(before)
    }

    /** A spell that can't be countered is still a legal target; the counterspell resolves and does nothing to it. */
    // CR 101.2, 701.6a
    @Test
    fun uncounterableSpellStaysOnTheStack() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.hand("Sudden Rot")
        bob.battlefield("Island", "Island", "Elvish Seer")
        bob.hand("Denial")
        alice.casts("Sudden Rot", target = "Elvish Seer")
        alice.passes()
        bob.casts("Denial", target = "Sudden Rot")
        allPass()
        expect {
            bob.graveyard has "Denial"
            stack.size shouldBe 1
        }
        allPass()
        expect { bob.graveyard has "Elvish Seer" }
    }

    /** "Counter unless its controller pays": the controller who can pay is asked, pays, and the spell resolves. */
    // CR 118.12, 701.6a
    @Test
    fun counterUnlessPaysAsksAndPaid() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Island", "Island")
        bob.hand("Mana Seep")
        alice.casts("Storm Spark", target = "bob", paying = listOf("Mountain"))
        alice.passes()
        bob.casts("Mana Seep", target = "Storm Spark")
        allPass()
        assertEquals(alice.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        val d = decision<Decision.ManaPayment>()
        assertEquals(alice.id, d.player)
        answer(Answer.ManaPayment(d.suggestion))
        allPass()
        expect { bob.life shouldBe 17 }
    }

    /** "Counter target spell" can't target an activated ability on the Stack. */
    // CR 115.1, 113.2
    @Test
    fun counterspellCantTargetAnAbility() = scenario {
        alice.battlefield("Mountain", "Mountain", "Cinder Spellcharge")
        alice.hand("Storm Spark")
        bob.battlefield("Island", "Island")
        bob.hand("Denial")
        alice.casts("Storm Spark", target = "bob", paying = listOf("Mountain"))
        alice.activates("Cinder Spellcharge", target = "bob", paying = listOf("Mountain#1"))
        alice.passes()
        perform(Input.Cast(bob.id, card("Denial")))
        val d = decision<Decision.Targets>()
        assertEquals(listOf<TargetRef>(TargetRef.Object(card("Storm Spark"))), d.options)
    }

    /** The target of an X spell is chosen after X: Rescind with X=2 can't target a mana value 1 permanent. */
    // CR 601.2b, 601.2c
    @Test
    fun targetsAreCheckedAgainstTheAnnouncedX() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Rescind")
        bob.battlefield("Elvish Seer", "Canopy Visionary")
        perform(Input.Cast(alice.id, card("Rescind")))
        answer(Answer.Number(3))
        val d = decision<Decision.Targets>()
        assertTrue(TargetRef.Object(card("Canopy Visionary")) in d.options)
        assertFalse(TargetRef.Object(card("Elvish Seer")) in d.options)
    }

    /** Phyrexian mana paid with life: Sever Limb for {1} and 4 life. */
    // CR 107.4f, 119.4
    @Test
    fun phyrexianManaWithLife() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Sever Limb")
        bob.battlefield("Thicketwood Elves")
        alice.casts("Sever Limb", target = "Thicketwood Elves")
        allPass()
        expect {
            alice.life shouldBe 16
            bob.graveyard has "Thicketwood Elves"
        }
    }

    /** Ward: targeting a warded creature triggers ward; not paying counters the spell. */
    // CR 702.21a
    @Test
    fun wardCountersUnlessPaid() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Scrapquilt Automaton")
        alice.casts("Storm Spark", target = "Scrapquilt Automaton")
        // The ward trigger goes on the Stack above the spell
        expect { stack.size shouldBe 2 }
        allPass()
        // alice has no mana left: nothing to ask, the spell is countered
        allPass()
        expect {
            bob.battlefield has "Scrapquilt Automaton"
            alice.graveyard has "Storm Spark"
        }
    }

    /** Divided damage: Wildfire Spread for X=3 split 2 and 1 among two targets. */
    // CR 601.2d
    @Test
    fun dividedDamage() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Wildfire Spread")
        bob.battlefield("Thicketwood Elves")
        alice.casts("Wildfire Spread", targets = listOf("bob", "Thicketwood Elves"), x = 3)
        val d = decision<Decision.Divide>()
        answer(Answer.Division(listOf(arcanum.engine.decision.Share(target("bob"), 2), arcanum.engine.decision.Share(target("Thicketwood Elves"), 1))))
        if (pending is Decision.ManaPayment) paySuggested()
        allPass()
        expect {
            bob.life shouldBe 18
            bob.graveyard has "Thicketwood Elves"
        }
        assertEquals(3, d.amount)
    }

    /** Storm copies one for each spell cast before it this turn. */
    // CR 702.40a
    @Test
    fun stormCopiesForEachEarlierSpell() = scenario {
        alice.battlefield("Mountain", "Mountain", "Forest", "Forest")
        alice.hand("Storm Spark", "Magma Needle", "Chitterstorm")
        alice.casts("Storm Spark", target = "bob", paying = listOf("Mountain"))
        allPass()
        alice.casts("Magma Needle", target = "bob", paying = listOf("Mountain#1"))
        allPass()
        alice.casts("Chitterstorm")
        repeat(4) { allPass() }
        expect { alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 3 }
    }

    /** A spell that resolves with an illegal target and a legal one affects only the legal one. */
    // CR 608.2b
    @Test
    fun illegalTargetIsntAffectedByTheRest() = scenario {
        alice.battlefield("Swamp", "Mountain", "Mountain")
        alice.hand("Warlord's Decree")
        bob.battlefield("Sun Band")
        bob.hand("Island")
        alice.casts("Warlord's Decree", modes = listOf(1, 2), targets = listOf("bob", "Sun Band"))
        move("Sun Band", Zone.Hand)
        allPass()
        var guard = 3
        while (pending != null && guard-- > 0) answer(Answer.Objects(listOf(card("Island"))))
        expect {
            bob.graveyard has "Island"
            bob.hand has "Sun Band"
        }
    }
}
