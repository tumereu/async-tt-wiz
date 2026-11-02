package arcanum.engine.review

import arcanum.api.Affected
import arcanum.api.Duration
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Review 3: "can't" beats "can" (CR 101.2) where a permission meets a restriction; who controls an ability on the
 * Stack and a triggered ability (CR 113.8, 603.3a) when its source's controller changes.
 */
class Review3CantAndControlTest {
    /** Verdant Hollowmere lets alice cast Elvish Seer free with flash; Aetherbound Lawkeeper says she can't cast another
     * nonartifact spell this turn: the restriction wins. */
    // CR 101.2
    @Test
    fun lawkeeperBeatsHollowmere() = scenario {
        alice.battlefield("Aetherbound Lawkeeper", "Verdant Hollowmere", "Mountain")
        alice.hand("Storm Spark", "Elvish Seer")
        val free = castOptions(alice, "Elvish Seer").first { it.way.permission != null }
        alice.casts("Storm Spark", target = "bob")
        allPass()
        assertTrue(castOptions(alice, "Elvish Seer").isEmpty())
        assertTrue(refuses(free))
    }

    /** Silencing Automaton names Gloomwing Skulker: "any player may activate" doesn't beat "can't be activated". */
    // CR 101.2
    @Test
    fun silencingAutomatonBeatsAnyPlayerMayActivate() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Silencing Automaton", "Island")
        bob.battlefield("Gloomwing Skulker")
        bob.hand("Swamp")
        alice.casts("Silencing Automaton")
        allPass()
        answer(Answer.CardName("Gloomwing Skulker"))
        assertTrue(activateOptions(alice, "Gloomwing Skulker").isEmpty())
        alice.passes()
        assertTrue(activateOptions(bob, "Gloomwing Skulker").isEmpty())
    }

    /** Mirrorward Mage bounces bob's Elvish Seer: bob can't cast spells with that name until alice's next turn, also not
     * free through Verdant Hollowmere's permission. */
    // CR 101.2
    @Test
    fun mirrorwardBeatsHollowmere() = scenario {
        alice.battlefield("Plains", "Island", "Island", "Verdant Hollowmere")
        alice.hand("Mirrorward Mage")
        bob.battlefield("Elvish Seer", "Forest")
        alice.casts("Mirrorward Mage")
        allPass()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Elvish Seer"))))
        allPass()
        expect { bob.hand has "Elvish Seer" }
        alice.passes()
        assertTrue(castOptions(bob, "Elvish Seer").isEmpty(), "${castOptions(bob, "Elvish Seer")}")
    }

    /** Ranger-Marshal of Dawn sacrificed: bob can't cast noncreature spells this turn, also by flashback. */
    // CR 101.2, 702.34a
    @Test
    fun rangerMarshalBeatsFlashback() = scenario {
        alice.battlefield("Ranger-Marshal of Dawn")
        bob.battlefield("Mountain", "Mountain")
        bob.graveyard("Magma Needle")
        bob.hand("Storm Spark")
        alice.activates("Ranger-Marshal of Dawn")
        allPass()
        alice.passes()
        assertTrue(castOptions(bob, "Magma Needle").isEmpty())
        assertTrue(castOptions(bob, "Storm Spark").isEmpty())
    }

    /** Crafty Pawnbroker chooses bob's Goblin Volley Pit: the Pit's own ability can't be activated, the Pawnbroker's copy
     * of it can. */
    // CR 101.2, 113.2
    @Test
    fun pawnbrokerActivatesWhatItStops() = scenario {
        alice.battlefield("Plains", "Island", "Tinkered Mite")
        alice.hand("Crafty Pawnbroker")
        bob.battlefield("Goblin Volley Pit", "Elvish Seer")
        alice.casts("Crafty Pawnbroker")
        allPass()
        when (pending) {
            is Decision.May -> { yes(); if (pending is Decision.ChooseObjects) alice.chooses("Goblin Volley Pit") }
            is Decision.ChooseObjects -> alice.chooses("Goblin Volley Pit")
            else -> {}
        }
        assertTrue(activateOptions(alice, "Crafty Pawnbroker").isNotEmpty(), "the Pawnbroker has the Pit's ability")
        alice.passes()
        assertTrue(activateOptions(bob, "Goblin Volley Pit").isEmpty(), "the Pit's ability can't be activated")
    }

    /** An activated ability on the Stack is controlled by the player who activated it (CR 113.8, 602.2): Demon-Cult
     * Forge changes controller while its ability waits; "each opponent" and "you" are still alice's. */
    // CR 113.8, 110.2
    @Test
    fun anAbilityKeepsItsControllerWhenItsSourceChangesHands() = scenario {
        alice.battlefield("Demon-Cult Forge", "Tinkered Mite")
        alice.activates("Demon-Cult Forge", choosing = listOf("Tinkered Mite"))
        val forge = card("Demon-Cult Forge")
        change(Change.EffectAdded(EffectObject(
            id = EffectId("review3-steal"), mechanics = listOf(Mechanic.ControlledBy(Players.You)),
            affects = Affected.Objects(Objects.Self), locked = listOf(forge), source = null, controller = bob.id,
            timestamp = state.timestampCounter + 1, createdTurn = 1, duration = Duration.EndOfTurn,
        )))
        expect { "Demon-Cult Forge".controller shouldBe bob.id }
        // Demon-Cult Forge's own trigger (an artifact left) resolves first, then the ability
        passUntilEmpty()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    /** A triggered ability is controlled by its source's controller when it triggered (CR 603.3a): bob controls
     * alice's Crimson Mourner; the trigger is bob's, who targets alice and gains the life. */
    // CR 603.3a
    @Test
    fun aStolenSourcesTriggerIsTheNewControllers() = scenario {
        alice.battlefield("Crimson Mourner")
        bob.battlefield("Elvish Seer")
        bob.hand("Storm Spark")
        effect(bob, Mechanic.ControlledBy(Players.You), on = listOf("Crimson Mourner"))
        bob.castsWithoutCosts("Storm Spark", target = "Elvish Seer")
        resolve()
        val targets = decision<Decision.Targets>()
        assertTrue(targets.player == bob.id, "bob chooses: ${targets.player}")
        bob.answers(Answer.Targets(listOf(target("alice"))))
        resolve()
        expect {
            alice.life shouldBe 19
            bob.life shouldBe 21
        }
    }

    /** "Can't be countered" beats "counter target spell unless its controller pays": Saurian Herder's green spell
     * stays whatever bob decides, and the Stack shows no counter. */
    // CR 101.2, 701.6b
    @Test
    fun cantBeCounteredBeatsCounterUnless() = scenario {
        alice.battlefield("Saurian Herder")
        alice.hand("Elvish Seer")
        bob.hand("Mana Seep")
        alice.castsWithoutCosts("Elvish Seer")
        bob.castsWithoutCosts("Mana Seep", target = "Elvish Seer")
        resolve()
        if (pending is Decision.May) no()
        resolve()
        expect { alice.battlefield has "Elvish Seer" }
        assertTrue(pending == null)
    }
}
