package arcanum.engine.resolution

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.engine.CardSupport
import arcanum.engine.action.Events
import arcanum.engine.action.PlannedEvent
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.change.DamageDealt
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.state.GameState
import arcanum.engine.state.TargetRef
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How resolution runs its steps (CR 608.2c–e): committed steps, choices, loops, conditions, by-hand parts (D29, D31, D58). */
class StepsTest {
    private fun spell(name: String, vararg steps: Step, targets: List<TargetSlot> = emptyList()): String = Cards.define(name,
        CardDefinition(Layout.Normal, listOf(FaceDefinition(name, ManaCost.parse("{1}"), types = listOf(CardType.Sorcery),
            abilities = listOf(Ability.Spell(Instructions(targets = targets, steps = steps.toList())))))))

    private val creature = ObjectFilter.Type(CardType.Creature)
    private fun slot(name: String, filter: ObjectFilter = creature) = TargetSlot(name, TargetKind.Objects(filter), Value.Fixed(1), Value.Fixed(1))
    private val playerSlot = TargetSlot("player", TargetKind.Players(PlayerFilter.Any), Value.Fixed(1), Value.Fixed(1))
    private fun Effect.step() = Step.Do(this)

    // D31, D58: the draw is committed before the discard is asked, so the player sees what they drew
    @Test
    fun earlierStepsAreDoneBeforeADecision() = scenario {
        alice.hand("Faithless Rummage", "Storm Spark")
        alice.library("Forest", "Island")

        alice.castsWithoutCosts("Faithless Rummage")
        resolve()

        expect { alice.hand.names shouldBe listOf("Storm Spark", "Forest", "Island") }
        decision<Decision.ChooseObjects>()
        alice.chooses("Storm Spark", "Forest")
        expect {
            alice.hand.names shouldBe listOf("Island")
            alice.graveyard.names shouldBe listOf("Storm Spark", "Forest", "Faithless Rummage")
        }
    }

    // D58: the open decision and its continuation are plain data the host stores as JSON
    @Test
    fun theContinuationSurvivesJson() = scenario {
        alice.hand("Faithless Rummage", "Storm Spark")
        alice.library("Forest", "Island")

        alice.castsWithoutCosts("Faithless Rummage")
        resolve()

        val json = Json.encodeToString(GameState.serializer(), state)
        assertEquals(state, Json.decodeFromString(GameState.serializer(), json))
        assertTrue(state.pending!!.continuation is Continuation.Resolving)
    }

    // CR 608.2c: "simultaneously": one zone change event for both
    @Test
    fun simultaneously() = scenario {
        /** "Destroy target creature and target artifact simultaneously." */
        val card = spell("Test Twin Strike", Step.Simultaneously(listOf(
            Effect.Destroy(Objects.Target("creature")).step(), Effect.Destroy(Objects.Target("artifact")).step())),
            targets = listOf(slot("creature"), slot("artifact", ObjectFilter.Type(CardType.Artifact))))
        alice.hand(card)
        bob.battlefield("Umbral Elves", "Mana Reliquary")

        alice.castsWithoutCosts(card, targets = listOf("Umbral Elves", "Mana Reliquary"))
        resolve()

        expect { bob.graveyard.names.toSet() shouldBe setOf("Umbral Elves", "Mana Reliquary") }
        assertEquals(1, applied.filterIsInstance<Change.ZoneChange>().count { z -> z.moves.any { Meaning.Destroyed in it.meanings } })
    }

    // CR 603.7c: "that creature" is fixed when the delayed trigger is made ([Step.Bind])
    @Test
    fun bindForADelayedTrigger() = scenario {
        /** "At the beginning of the next end step, destroy target creature." */
        val card = spell("Test Slow Doom", Step.Bind(Objects.Target("creature"), "doomed"),
            Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                Instructions(steps = listOf(Effect.Destroy(Objects.Bound("doomed")).step()))).step(),
            targets = listOf(slot("creature")))
        alice.hand(card)
        bob.battlefield("Umbral Elves")

        alice.castsWithoutCosts(card, target = "Umbral Elves")
        resolve()
        expect { bob.battlefield has "Umbral Elves" }
        beginStep(TurnStep.End)
        resolve()

        expect { bob.graveyard has "Umbral Elves" }
    }

    // D61: a part the definition can't express isn't carried out (CardSupport.manualParts names it) and asks nothing
    @Test
    fun manualStep() = scenario {
        /** "Draw a card. [Something only a player can do.]" */
        val card = spell("Test Oddity", Effect.Draw(Players.You, Value.Fixed(1)).step(), Step.Manual("Name a planet"))
        alice.hand(card)
        alice.library("Forest")

        alice.castsWithoutCosts(card)
        resolve()
        assertEquals(null, pending)

        expect {
            alice.hand has "Forest"
            alice.graveyard has card
        }
    }

    // CR 101.4: "starting with" the target opponent, each player draws in turn order
    @Test
    fun forEachPlayerStartingWith() = scenario(players = 3) {
        /** "Starting with target opponent, each player draws a card." */
        val card = spell("Test Round Table", Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player",
            listOf(Effect.Draw(Players.Bound("player"), Value.Fixed(1)).step()), startingWith = Players.Target("opponent")),
            targets = listOf(TargetSlot("opponent", TargetKind.Players(PlayerFilter.Opponent), Value.Fixed(1), Value.Fixed(1))))
        alice.hand(card)
        alice.library("Forest")
        bob.library("Island")
        carol.library("Swamp")

        alice.castsWithoutCosts(card, target = "carol")
        resolve()

        val drawers = applied.filterIsInstance<Change.ZoneChange>().flatMap { it.moves }.filter { Meaning.Drew in it.meanings }.map { it.before.owner.value }
        assertEquals(listOf("carol", "alice", "bob"), drawers)
    }

    // CR 608.2d: choosing a number; the bound number is read later
    @Test
    fun chooseANumber() = scenario {
        /** "Choose a number from 0 to 3. Target player mills that many cards." */
        val card = spell("Test Measure", Step.Choose(Choice.Number(Value.Fixed(0), Value.Fixed(3)), "n", Players.You),
            Effect.Mill(Players.Target("player"), Value.Bound("n")).step(), targets = listOf(playerSlot))
        alice.hand(card)
        bob.library("Forest", "Island", "Swamp", "Mountain")

        alice.castsWithoutCosts(card, target = "bob")
        resolve()
        answer(Answer.Number(2))

        expect { bob.graveyard.size shouldBe 2 }
    }

    // CR 608.2d: choosing a player
    @Test
    fun chooseAPlayer() = scenario(players = 3) {
        /** "Choose an opponent. That player loses 3 life." */
        val card = spell("Test Grudge", Step.Choose(Choice.Player(PlayerFilter.Opponent), "victim", Players.You),
            Effect.LoseLife(Players.Bound("victim"), Value.Fixed(3)).step())
        alice.hand(card)

        alice.castsWithoutCosts(card)
        resolve()
        assertEquals(listOf(bob.id, carol.id), decision<Decision.ChoosePlayer>().options)
        answer(Answer.Player(carol.id))

        expect { carol.life shouldBe 17 }
    }

    // CR 608.2d: choosing one of named options, read with Condition.Chose
    @Test
    fun chooseAnOption() = scenario {
        /** "Choose odd or even. If you chose odd, you gain 3 life. Otherwise, draw a card." */
        val card = spell("Test Parity", Step.Choose(Choice.Option(listOf("odd", "even")), "parity", Players.You),
            Step.If(Condition.Chose("parity", "odd"), listOf(Effect.GainLife(Players.You, Value.Fixed(3)).step()),
                listOf(Effect.Draw(Players.You, Value.Fixed(1)).step())))
        alice.hand(card)
        alice.library("Forest")

        alice.castsWithoutCosts(card)
        resolve()
        answer(Answer.Option("even"))

        expect {
            alice.life shouldBe 20
            alice.hand has "Forest"
        }
    }

    // CR 105.1: choosing a colour; "of the chosen colour"
    @Test
    fun chooseAColor() = scenario {
        /** "Choose a color. Destroy each creature of the chosen color." */
        val card = spell("Test Purge", Step.Choose(Choice.Color(), "color", Players.You),
            Effect.Destroy(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.OfColor(ColorRef.Chosen("color")))))).step())
        alice.hand(card)
        bob.battlefield("Umbral Elves", "Venomous Owlet")

        alice.castsWithoutCosts(card)
        resolve()
        answer(Answer.ColorChoice(Color.Green))

        expect {
            bob.graveyard has "Umbral Elves"
            bob.battlefield has "Venomous Owlet"
        }
    }

    // CR 201.3: naming a card; "all cards with that name"
    @Test
    fun nameACard() = scenario {
        alice.hand("Whispered Therapy")
        bob.hand("Storm Spark", "Storm Spark", "Denial")

        alice.castsWithoutCosts("Whispered Therapy", target = "bob")
        resolve()
        answer(Answer.CardName("Storm Spark"))

        expect {
            bob.hand.names shouldBe listOf("Denial")
            bob.graveyard.size shouldBe 2
        }
    }

    // CR 608.2c: "repeat this process"
    @Test
    fun repeat() = scenario {
        /** "Draw a card. Repeat this process two more times." */
        val card = spell("Test Thrice", Step.Repeat(Value.Fixed(3), listOf(Effect.Draw(Players.You, Value.Fixed(1)).step())))
        alice.hand(card)
        alice.library("Forest", "Island", "Swamp", "Mountain")

        alice.castsWithoutCosts(card)
        resolve()

        expect { alice.hand.size shouldBe 3 }
    }

    // CR 710.2: a flip card flips and stays flipped
    @Test
    fun flip() = scenario {
        val flipCard = Cards.define("Test Apprentice", CardDefinition(Layout.Flip, listOf(
            FaceDefinition("Test Apprentice", ManaCost.parse("{U}"), types = listOf(CardType.Creature), power = PrintedNumber.Fixed(1),
                toughness = PrintedNumber.Fixed(1)),
            FaceDefinition("Test Archmage", types = listOf(CardType.Creature), power = PrintedNumber.Fixed(4), toughness = PrintedNumber.Fixed(4)))))
        /** "Flip target creature." */
        val card = spell("Test Ascend", Effect.Flip(Objects.Target("creature")).step(), targets = listOf(slot("creature")))
        alice.battlefield(flipCard)
        alice.hand(card)

        alice.castsWithoutCosts(card, target = flipCard)
        resolve()

        expect {
            card(flipCard).view.name shouldBe "Test Archmage"
            card(flipCard).power shouldBe 4
        }
    }

    // CR 614.10: the player's next turn is skipped (sos: no longer done by hand)
    @Test
    fun skipNextTurnIsRemembered() = scenario {
        /** "Target player skips their next turn." */
        val card = spell("Test Time Lock", Effect.SkipNextTurn(Players.Target("player")).step(), targets = listOf(playerSlot))
        alice.hand(card)

        alice.castsWithoutCosts(card, target = "bob")
        resolve()

        assertEquals(listOf(bob.id), state.turn.skippedTurns)
    }

    // CR 702.33d: "if this spell was kicked"
    @Test
    fun kicked() = scenario {
        alice.hand("Flash Surge")

        alice.castsWithoutCosts("Flash Surge", target = "bob", kicked = 1)
        resolve()

        expect { bob.life shouldBe 16 }
    }

    // CR 608.2c: the otherwise branch
    @Test
    fun notKicked() = scenario {
        alice.hand("Flash Surge")

        alice.castsWithoutCosts("Flash Surge", target = "bob")
        resolve()

        expect { bob.life shouldBe 18 }
    }

    // CR 608.2b: an illegal target is left out; the spell still affects the legal one
    @Test
    fun partOfTheTargetsAreIllegal() = scenario {
        alice.hand("Wildfire Spread")
        bob.battlefield("Elvish Hierophant", "Umbral Elves")

        alice.castsWithoutCosts("Wildfire Spread", targets = listOf("Elvish Hierophant", "Umbral Elves"), x = 4, divided = listOf(2, 2))
        move("Elvish Hierophant", to = arcanum.api.Zone.Hand)
        resolve()

        expect {
            bob.graveyard has "Umbral Elves"
            bob.hand has "Elvish Hierophant"
        }
    }

    // CR 700.2: two modes, each with its own target
    @Test
    fun twoModes() = scenario {
        alice.hand("Spellforge Command")
        bob.battlefield("Mana Reliquary")

        alice.castsWithoutCosts("Spellforge Command", targets = listOf("bob", "Mana Reliquary"), modes = listOf(0, 3))
        resolve()

        expect {
            bob.life shouldBe 18
            bob.graveyard has "Mana Reliquary"
        }
    }

    // CR 608.3, 303.4f: an Aura spell resolves attached to what it targeted
    @Test
    fun auraEntersAttached() = scenario {
        alice.hand("Rapid Retrofit")
        alice.battlefield("Elvish Seer")

        alice.castsWithoutCosts("Rapid Retrofit", target = "Elvish Seer")
        resolve()

        expect { alice.battlefield has "Rapid Retrofit" }
        assertEquals(TargetRef.Object(card("Elvish Seer")), state.objOrThrow(card("Rapid Retrofit")).attachedTo)
    }

    // CR 616.1e, D29: an event outside resolution (combat damage) waits for the affected player's order and is redone
    @Test
    fun anEventWaitsForAReplacementOrder() = scenario {
        /** "If a source would deal damage to an opponent, it deals double that damage instead." */
        val doubler = Cards.define("Test Furnace Heart", CardDefinition(Layout.Normal, listOf(FaceDefinition("Test Furnace Heart",
            ManaCost.parse("{2}"), types = listOf(CardType.Enchantment), abilities = listOf(Ability.Static(listOf(Mechanic.Replacement(
                EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.Opponent)),
                modify = listOf(Modification.MultiplyAmount(2)))), Affected.Objects(Objects.Self)))))))
        /** "If a source would deal damage to an opponent, it deals that much damage plus 1 instead." */
        val plusOne = Cards.define("Test Ember Crown", CardDefinition(Layout.Normal, listOf(FaceDefinition("Test Ember Crown",
            ManaCost.parse("{2}"), types = listOf(CardType.Enchantment), abilities = listOf(Ability.Static(listOf(Mechanic.Replacement(
                EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.Opponent)),
                modify = listOf(Modification.AddAmount(Value.Fixed(1))))), Affected.Objects(Objects.Self)))))))
        alice.battlefield(doubler, plusOne, "Elvish Hierophant")

        drive { r ->
            Events.run(r, PlannedEvent.Damage(listOf(DamageDealt(card("Elvish Hierophant"), TargetRef.Player(bob.id), 2, combat = true)), alice.id))
        }
        assertEquals(bob.id, decision<Decision.ChooseReplacement>().player)
        assertTrue(state.pending!!.continuation is Continuation.Event)
        expect { bob.life shouldBe 20 }
        // Plus one first, then doubled: 6
        answer(Answer.Replacement(1))

        expect { bob.life shouldBe 14 }
    }

    @Suppress("unused")
    private val counters = CounterKind.PowerToughness(1, 1)
}
