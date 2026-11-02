package arcanum.engine.review

import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Review 3: more of the same roots: "another" against an object's own new self (CR 400.7, 603.10a), what counts as a
 * cast (CR 707.10), delayed triggers that return under the owner's control (CR 603.7e), restricted mana.
 */
class Review3MoreTest {
    /** Netherbound Turncoat dying alone: "another creature" is never itself, also as the card it became (CR 400.7): no
     * trigger. */
    // CR 603.10a, 400.7
    @Ignore("review3 #2: a card's graveyard ability triggers on its own death (\"another\" compares new and old ids)")
    @Test
    fun turncoatDyingAloneDoesntTrigger() = scenario {
        alice.battlefield("Netherbound Turncoat", "Swamp")
        bob.hand("Storm Spark")
        bob.castsWithoutCosts("Storm Spark", target = "Netherbound Turncoat")
        resolve()
        expect {
            alice.graveyard has "Netherbound Turncoat"
            stack.isEmpty()
        }
    }

    /** Harborside Knifer dies with Tinkered Mite: looking back, it sees both deaths (itself and another creature). */
    // CR 603.10a
    @Test
    fun kniferSeesItsOwnAndAnothersDeath() = scenario {
        alice.battlefield("Harborside Knifer", "Tinkered Mite")
        bob.hand("Blazing Confluence")
        bob.castsWithoutCosts("Blazing Confluence", modes = listOf(0, 0, 0))
        resolve()
        resolveStack()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    /** Brisa exiles bob's creature; at the next end step it returns under its owner's (bob's) control, so Brisa gets no
     * counter. */
    // CR 603.7e, 610.3
    @Test
    fun brisaReturnsTheCardToItsOwner() = scenario {
        alice.battlefield("Brisa, Bounding Shepherd")
        bob.battlefield("Elvish Seer")
        alice.library("Island"); bob.library("Swamp", "Swamp")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Brisa, Bounding Shepherd")
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Elvish Seer"))))
        allPass()
        expect { bob.exile has "Elvish Seer" }
        advanceAnswering(TurnStep.End)
        passUntilEmpty()
        expect {
            bob.battlefield has "Elvish Seer"
            "Brisa, Bounding Shepherd".counters(arcanum.api.CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    /** Hint of the Wild: "whenever you cast a creature spell this turn" triggers for each creature spell. */
    // CR 603.7b
    @Test
    fun hintOfTheWildTriggersEachTime() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand("Hint of the Wild", "Elvish Seer", "Thicketwood Elves")
        alice.library("Island", "Island", "Island")
        alice.casts("Hint of the Wild")
        allPass()
        alice.casts("Elvish Seer")
        passUntilEmpty()
        alice.casts("Thicketwood Elves")
        passUntilEmpty()
        expect { alice.hand.size shouldBe 2 }
    }

    /** Invoice Shredder: a copy isn't a spell cast, so Storm Spark and its copy are one spell; the second cast spell is
     * Flash of Mirroring itself. One connive. */
    // CR 707.10, 603.2
    @Test
    fun aCopyIsntASecondSpell() = scenario {
        alice.battlefield("Invoice Shredder", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Storm Spark", "Flash of Mirroring", "Island")
        alice.library("Swamp", "Swamp", "Swamp")
        alice.casts("Storm Spark", target = "bob")
        alice.casts("Flash of Mirroring", target = "Storm Spark")
        var connives = 0
        repeat(12) {
            when (val d = pending) {
                is Decision.May -> no()
                is Decision.ChooseObjects -> { connives++; answer(Answer.Objects(d.options.take(d.min.coerceAtLeast(1)))) }
                null -> if (state.stack.isNotEmpty()) allPass()
                else -> {}
            }
        }
        assertTrue(connives == 1, "connived $connives times")
        expect { bob.life shouldBe 14 }
    }

    /** "Draw a card at the beginning of the next end step", written by hand (the parser has no such sentence yet). */
    private val tardyInsight = arcanum.engine.cards.Cards.define("Review3 Tardy Insight", arcanum.api.CardDefinition(
        arcanum.api.Layout.Normal, listOf(arcanum.api.FaceDefinition(
            name = "Review3 Tardy Insight", manaCost = arcanum.api.ManaCost.parse("{U}"), types = listOf(arcanum.api.CardType.Instant),
            abilities = listOf(arcanum.api.Ability.Spell(arcanum.api.Instructions(steps = listOf(arcanum.api.Step.Do(
                arcanum.api.Effect.CreateDelayedTrigger(
                    arcanum.api.Trigger.On(events = listOf(arcanum.api.EventPattern.StepBegins(TurnStep.End, arcanum.api.PlayerFilter.Any))),
                    arcanum.api.Instructions(steps = listOf(arcanum.api.Step.Do(arcanum.api.Effect.Draw(arcanum.api.Players.You, arcanum.api.Value.Fixed(1))))),
                ),
            ))))),
        )),
    ))

    /** A delayed "at the beginning of the next end step" made during an end step waits for the next turn's end step. */
    // CR 513.2, 603.7
    @Test
    fun aNextEndStepTriggerMadeInTheEndStepWaitsAWholeTurn() = scenario {
        alice.hand(tardyInsight)
        alice.library("Island", "Island"); bob.library("Swamp", "Swamp", "Swamp")
        advanceTo(TurnStep.End)
        alice.castsWithoutCosts(tardyInsight)
        resolve()
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { alice.hand.size shouldBe 0 }
        advanceTo(TurnStep.End, turn = 2)
        passUntilEmpty()
        expect { alice.hand.size shouldBe 1 }
    }

    /** A token copy of a creature under a copy effect copies the copied values (CR 707.2): Orla makes Tinkered Mite a copy
     * of Gearwing Glider; Myriad-Faced Shade's token of the Mite is a Gearwing Glider, and stays one after the turn. */
    // CR 707.2, 707.9b, 613.2
    @Test
    fun aTokenCopyOfACopiedCreatureCopiesTheCopy() = scenario {
        alice.battlefield("Orla, Gilded Artificer", counters = listOf(arcanum.api.CounterKind.Loyalty to 4))
        alice.battlefield("Tinkered Mite", "Gearwing Glider", "Elvish Seer", "Island", "Island", "Island", "Island")
        alice.hand("Myriad-Faced Shade")
        alice.library("Swamp", "Swamp"); bob.library("Swamp", "Swamp")
        alice.activates("Orla, Gilded Artificer", ability = 0, targets = listOf("Tinkered Mite", "Gearwing Glider"))
        allPass()
        expect { card("Tinkered Mite").view.name shouldBe "Gearwing Glider" }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Tinkered Mite", "Elvish Seer")
        advanceTo(TurnStep.DeclareBlockers)
        alice.activates("Myriad-Faced Shade", choosing = listOf("Elvish Seer"))
        allPass()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(arcanum.engine.state.TargetRef.Object(card("Tinkered Mite")))))
        passUntilEmpty(mays = true)
        val token = state.battlefield.map { state.objOrThrow(it) }.single { it.token }
        expect { arcanum.engine.scenario.CardRef(token.id, "token").view.name shouldBe "Gearwing Glider" }
        advanceAnswering(TurnStep.Upkeep, turn = 2)
        expect {
            arcanum.engine.scenario.CardRef(token.id, "token").view.name shouldBe "Gearwing Glider"
            card("Tinkered Mite").view.name shouldBe "Tinkered Mite"
        }
    }

    /** Tinker's Atelier's mana is only for artifact spells: it can't pay Refitting Workshop's {2} ability. */
    // CR 106.6
    @Test
    fun restrictedManaDoesntPayAnAbility() = scenario {
        alice.battlefield("Tinker's Atelier", "Refitting Workshop")
        val workshop = activateOptions(alice, "Refitting Workshop")
        assertTrue(workshop.isEmpty(), "Workshop's abilities offered with only restricted mana: $workshop")
    }
}
