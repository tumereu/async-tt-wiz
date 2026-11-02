package arcanum.engine.review

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.input.Permission
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 2: storm, cascade and copies of spells (CR 702.40, 702.85, 707.10); flashback, unearth, rebound and escape
 * with their replacements (CR 702.34, 702.84, 702.88, 702.138); the legend rule with copies and control changes;
 * extra turns (CR 500.7).
 */
class Review2SpellsTest {
    /** Storm counts every spell cast before it this turn, the opponent's too. */
    // CR 702.40a
    @Test
    fun stormCountsTheOpponentsSpells() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand("Chitterstorm")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "alice")
        allPass()
        alice.casts("Chitterstorm")
        // The storm trigger, its copy, the original
        repeat(3) { allPass() }
        expect { alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 2 }
    }

    /** Copies aren't cast: a copy made by Flash of Mirroring doesn't add to storm. Storm Spark and Flash of Mirroring
     * were cast before Chitterstorm: two copies, three Squirrels. */
    // CR 702.40a, 707.10
    @Test
    fun copiesDontCountForStorm() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Forest", "Forest")
        alice.hand("Storm Spark", "Flash of Mirroring", "Chitterstorm")
        alice.casts("Storm Spark", target = "bob")
        alice.casts("Flash of Mirroring", target = "Storm Spark", paying = listOf("Mountain#1", "Mountain#2", "Mountain#3"))
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        allPass()
        allPass()
        expect { bob.life shouldBe 14 }
        alice.casts("Chitterstorm")
        repeat(4) { allPass() }
        expect { alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 3 }
    }

    /** Cascade passes over a split card whose combined mana value (6) isn't less than the cascading spell's (3). */
    // CR 702.85a, 709.4
    @Test
    fun cascadeSkipsASplitCardByItsCombinedManaValue() = scenario {
        alice.battlefield("Forest", "Island", "Island")
        alice.hand("Splintered Envoy")
        alice.library("Expanse // Eruption", "Tinkered Mite", "Island")
        alice.casts("Splintered Envoy")
        allPass()
        alice.answers(Answer.YesNo(true))
        expect { stack.names shouldBe listOf("Splintered Envoy", "Tinkered Mite") }
    }

    /** A copy of a spell keeps X: Expanse copies a Doomfire with X 3 (mana value 4 on the Stack) and the copy deals 3. */
    // CR 707.10, 202.3e
    @Test
    fun aCopyKeepsX() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Island", "Mountain")
        alice.hand("Doomfire", "Expanse // Eruption")
        alice.casts("Doomfire", x = 3, target = "bob", paying = listOf("Mountain", "Mountain#1", "Mountain#2", "Mountain#3"))
        alice.casts("Expanse // Eruption", way = CastWay(face = 0), target = "Doomfire")
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        allPass()
        allPass()
        expect { bob.life shouldBe 14 }
    }

    /** A copy of a kicked spell is kicked: Flash Surge kicked deals 4, so does its copy. */
    // CR 707.10
    @Test
    fun aCopyOfAKickedSpellIsKicked() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Flash Surge", "Flash of Mirroring")
        alice.casts("Flash Surge", target = "bob", kicked = 1)
        alice.casts("Flash of Mirroring", target = "Flash Surge")
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        allPass()
        allPass()
        expect { bob.life shouldBe 12 }
    }

    /** Mounting Retribution cast with flashback copies the spell twice, and is exiled. */
    // CR 702.34a, 707.10
    @Test
    fun mountingRetributionFromTheGraveyardCopiesTwice() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Storm Spark")
        alice.graveyard("Mounting Retribution")
        alice.casts("Storm Spark", target = "bob")
        val flashback = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().single { it.card == card("Mounting Retribution") }
        perform(flashback)
        alice.answers(Answer.Targets(listOf(target("Storm Spark"))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        while (pending is Decision.May) alice.answers(Answer.YesNo(false))
        repeat(3) { allPass() }
        expect {
            bob.life shouldBe 11
            alice.exile has "Mounting Retribution"
        }
    }

    /** Twincast Adept copies the opponent's spell: the copy is the Adept's controller's, who may choose a new target. */
    // CR 707.10, 707.10c
    @Test
    fun aCopyOfTheOpponentsSpellIsTheCopiers() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand("Twincast Adept")
        alice.casts("Storm Spark", target = "bob")
        alice.passes()
        bob.casts("Twincast Adept")
        allPass()
        if (pending is Decision.Targets) bob.answers(Answer.Targets(listOf(target("Storm Spark"))))
        allPass()
        if (pending is Decision.May) bob.answers(Answer.YesNo(true))
        if (pending is Decision.Targets) bob.answers(Answer.Targets(listOf(target("alice"))))
        repeat(2) { allPass() }
        expect {
            alice.life shouldBe 17
            bob.life shouldBe 17
        }
    }

    /** A copy of a spell that's countered ceases to exist: nothing goes to a graveyard. */
    // CR 707.10a, 704.5e
    @Test
    fun aCounteredCopyCeasesToExist() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Storm Spark", "Flash of Mirroring")
        bob.battlefield("Island", "Island")
        bob.hand("Denial")
        alice.casts("Storm Spark", target = "bob")
        alice.casts("Flash of Mirroring", target = "Storm Spark")
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        alice.passes()
        val copy = state.stack.last()
        perform(Input.Cast(bob.id, card("Denial")))
        bob.answers(Answer.Targets(listOf(arcanum.engine.state.TargetRef.Object(copy))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        expect {
            alice.graveyard.names.count { it == "Storm Spark" } shouldBe 0
            alice.graveyard has "Flash of Mirroring"
            bob.graveyard has "Denial"
        }
        assertTrue(copy !in state.stackItems && state.obj(copy) == null)
    }

    /** An unearthed creature returned to hand is exiled instead. */
    // CR 702.84a
    @Test
    fun anUnearthedCreatureBouncedIsExiled() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard("Junkyard Cur")
        bob.battlefield("Island", "Island")
        bob.hand("Click Back")
        alice.activates("Junkyard Cur")
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        allPass()
        expect { alice.battlefield has "Junkyard Cur" }
        alice.passes()
        bob.casts("Click Back", target = "Junkyard Cur")
        allPass()
        expect { alice.exile has "Junkyard Cur" }
    }

    /** An unearthed creature has haste and is exiled at the beginning of the next end step. */
    // CR 702.84a
    @Test
    fun anUnearthedCreatureHasHasteAndIsExiledAtEndStep() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard("Junkyard Cur")
        alice.activates("Junkyard Cur")
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        allPass()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Junkyard Cur")
        advanceTo(TurnStep.End)
        resolveStack()
        expect {
            bob.life shouldBe 18
            alice.exile has "Junkyard Cur"
        }
    }

    /** Rebound: cast from hand, the spell is exiled as it resolves and cast again at the next upkeep. */
    // CR 702.88a
    @Test
    fun reboundCastsAgainNextUpkeep() = scenario {
        alice.battlefield("Plains", "Elvish Seer")
        alice.hand("Fleeting Recall")
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.casts("Fleeting Recall", target = "Elvish Seer")
        allPass()
        expect { alice.exile has "Fleeting Recall" }
        advanceTo(TurnStep.Upkeep, turn = 3)
        allPass()
        alice.answers(Answer.YesNo(true))
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Elvish Seer"))))
        allPass()
        expect { alice.graveyard has "Fleeting Recall" }
    }

    /** A rebound spell that's countered goes to the graveyard: rebound applies only as it resolves. */
    // CR 702.88a
    @Test
    fun aCounteredReboundSpellGoesToTheGraveyard() = scenario {
        alice.battlefield("Plains", "Elvish Seer")
        alice.hand("Fleeting Recall")
        bob.battlefield("Island", "Island")
        bob.hand("Denial")
        alice.casts("Fleeting Recall", target = "Elvish Seer")
        alice.passes()
        bob.casts("Denial", target = "Fleeting Recall")
        allPass()
        expect { alice.graveyard has "Fleeting Recall" }
    }

    /** Escape: cast from the graveyard exiling eight other cards; it escapes with a +1/+1 counter. */
    // CR 702.138a, 702.138c
    @Test
    fun bullOfThePyreEscapes() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard("Bull of the Pyre", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.library("Island", "Island", "Island")
        val escape = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().single { it.card == card("Bull of the Pyre") }
        perform(escape)
        while (pending is Decision.ChooseObjects) {
            val d = decision<Decision.ChooseObjects>()
            answer(Answer.Objects(d.options.take(d.min)))
        }
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        allPass()
        expect {
            "Bull of the Pyre".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            alice.exile.size shouldBe 8
            alice.hand.size shouldBe 3
        }
    }

    /** Necromancer's Will: a card cast from the graveyard and resolved is exiled instead of going back. */
    // CR 614.1a, 601.2a
    @Test
    fun necromancersWillExilesWhatWouldGoToTheGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand("Necromancer's Will")
        alice.graveyard("Storm Spark")
        alice.casts("Necromancer's Will", paying = listOf("Swamp", "Swamp#1", "Swamp#2"))
        allPass()
        expect { alice.exile has "Necromancer's Will" }
        val fromGraveyard = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().single { it.card == card("Storm Spark") }
        perform(fromGraveyard)
        alice.answers(Answer.Targets(listOf(target("bob"))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        expect {
            bob.life shouldBe 17
            alice.exile has "Storm Spark"
        }
    }

    /** Gaining control of a legendary permanent with the same name as one you control: the legend rule. */
    // CR 704.5j
    @Test
    fun stealingALegendTriggersTheLegendRule() = scenario {
        alice.battlefield("Island", "Island", "Island", "Opaline Trinket")
        alice.hand("Pilfering Skydiver")
        bob.battlefield("Opaline Trinket")
        alice.casts("Pilfering Skydiver", kicked = 1, x = 1)
        allPass()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(arcanum.engine.state.TargetRef.Object(card("Opaline Trinket", owner = bob)))))
        allPass()
        assertTrue(pending is Decision.LegendRule, "legend rule: $pending")
    }

    /** Time Stride: alice takes the next turn; then bob. */
    // CR 500.7
    @Test
    fun anExtraTurn() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Time Stride")
        players.forEach { it.library("Island", "Island", "Island") }
        alice.casts("Time Stride")
        allPass()
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(alice.id, state.turn.active)
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals(bob.id, state.turn.active)
    }

    /** Two extra turns: both are alice's, then bob's turn. */
    // CR 500.7
    @Test
    fun twoExtraTurns() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand("Time Stride", "Time Stride")
        players.forEach { it.library("Island", "Island", "Island", "Island") }
        alice.casts("Time Stride")
        allPass()
        alice.casts("Time Stride")
        allPass()
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals(alice.id, state.turn.active)
        advanceTo(TurnStep.Upkeep, turn = 4)
        assertEquals(bob.id, state.turn.active)
    }

    /** With three players, the extra turn comes right after alice's, before bob's and carol's. */
    // CR 500.7
    @Test
    fun anExtraTurnWithThreePlayers() = scenario(players = 3) {
        alice.battlefield("Island", "Island")
        alice.hand("Time Stride")
        players.forEach { it.library("Island", "Island", "Island") }
        alice.casts("Time Stride")
        allPass()
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(alice.id, state.turn.active)
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals(bob.id, state.turn.active)
        advanceTo(TurnStep.Upkeep, turn = 4)
        assertEquals(carol.id, state.turn.active)
    }

    /** "Until your next turn" ends as alice's extra turn begins. */
    // CR 500.7, 611.2a
    @Test
    fun untilYourNextTurnEndsAtTheExtraTurn() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Time Stride")
        bob.battlefield("Brakka, Wild Whelp")
        players.forEach { it.library("Island", "Island", "Island") }
        effect(alice, arcanum.api.Mechanic.ModifyPowerToughness(arcanum.api.Value.Fixed(-2), arcanum.api.Value.Fixed(0)),
            on = listOf("Brakka, Wild Whelp"), duration = arcanum.api.Duration.UntilYourNextTurn)
        alice.casts("Time Stride")
        allPass()
        expect { "Brakka, Wild Whelp".power shouldBe 0 }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { "Brakka, Wild Whelp".power shouldBe 2 }
    }

    /** A flashback spell countered by Send Back ("put it into its owner's hand instead") is exiled: flashback's
     * replacement applies to any way it leaves the Stack. */
    // CR 702.34a, 616.1
    @Test
    fun flashbackBeatsSendBack() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard("Cinder Bolt")
        bob.battlefield("Island", "Island")
        bob.hand("Send Back")
        val flashback = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().single { it.card == card("Cinder Bolt") }
        perform(flashback)
        alice.answers(Answer.Targets(listOf(target("bob"))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        alice.passes()
        bob.casts("Send Back", target = "Cinder Bolt")
        allPass()
        if (pending is Decision.ChooseReplacement) answer(Answer.Replacement(decision<Decision.ChooseReplacement>().options.first().index))
        expect {
            alice.exile has "Cinder Bolt"
            bob.life shouldBe 20
        }
    }

    /** An escaped spell that's countered goes to the graveyard (escape has no exile clause), and can escape again. */
    // CR 702.138a
    @Test
    fun aCounteredEscapeGoesToTheGraveyard() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard("Bull of the Pyre", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        bob.battlefield("Island", "Island")
        bob.hand("Denial")
        val escape = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().single { it.card == card("Bull of the Pyre") }
        perform(escape)
        while (pending is Decision.ChooseObjects) {
            val d = decision<Decision.ChooseObjects>()
            answer(Answer.Objects(d.options.take(d.min)))
        }
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        alice.passes()
        bob.casts("Denial", target = "Bull of the Pyre")
        allPass()
        expect { alice.graveyard has "Bull of the Pyre" }
    }

    /** An unearthed creature that would die under Necromancer's Will's "exile instead" is exiled either way; with no
     * Will, a sacrificed unearthed creature is exiled too (unearth's own replacement). */
    // CR 702.84a, 614.1a
    @Test
    fun anUnearthedCreatureSacrificedIsExiled() = scenario {
        alice.battlefield("Mountain", "Mountain", "Renderer's Altar")
        alice.graveyard("Junkyard Cur")
        alice.activates("Junkyard Cur")
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        allPass()
        alice.activates("Renderer's Altar", choosing = listOf("Junkyard Cur"))
        expect { alice.exile has "Junkyard Cur" }
    }

    /** Cascade into a storm spell: it is cast, so its storm triggers and counts the cascading spell: one copy. */
    // CR 702.85a, 702.40a, 601.2i
    @Test
    fun cascadeIntoStorm() = scenario {
        alice.battlefield("Forest", "Island", "Island")
        alice.hand("Splintered Envoy")
        alice.library("Island", "Chitterstorm", "Forest")
        alice.casts("Splintered Envoy")
        allPass()
        alice.answers(Answer.YesNo(true))
        var guard = 10
        while (guard-- > 0 && state.stack.isNotEmpty()) {
            if (pending is Decision.May) alice.answers(Answer.YesNo(false)) else allPass()
        }
        expect { alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 2 }
    }

    /** An extra turn of a player who left the game doesn't begin: after alice concedes, bob's turn comes next. */
    // CR 800.4k, 500.7
    @Test
    fun anExtraTurnOfAPlayerWhoLeftDoesntBegin() = scenario(players = 3) {
        alice.battlefield("Island", "Island")
        alice.hand("Time Stride")
        players.forEach { it.library("Island", "Island", "Island") }
        alice.casts("Time Stride")
        allPass()
        perform(Input.Concede(alice.id))
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(bob.id, state.turn.active)
    }
}
