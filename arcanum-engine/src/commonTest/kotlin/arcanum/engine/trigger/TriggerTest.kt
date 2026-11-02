package arcanum.engine.trigger

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.change.Changes
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Triggered abilities (CR 603): when they trigger, look-back, APNAP placement, intervening "if", limits. */
class TriggerTest {
    /** "Destroy all creatures." */
    private val wrath = Cards.define("Test Wrath", CardDefinition(Layout.Normal, listOf(FaceDefinition("Test Wrath", ManaCost.parse("{2}{W}{W}"),
        types = listOf(CardType.Sorcery), abilities = listOf(Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.Destroy(Objects.Each(ObjectFilter.Type(CardType.Creature))))))))))))

    // CR 603.10a: "dies" triggers look back: the Knifer sees itself and the other creature die in one event
    @Test
    fun diesTriggersLookBackInABoardWipe() = scenario {
        alice.hand(wrath)
        alice.battlefield("Harborside Knifer", "Elvish Seer")
        bob.battlefield("Umbral Elves")

        alice.castsWithoutCosts(wrath)
        resolve()

        expect {
            stack.names shouldBe listOf("ability of Harborside Knifer", "ability of Harborside Knifer")
        }
        resolveStack()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    // CR 603.2, 603.6c: a leaves-the-battlefield trigger of another permanent; it goes on the Stack with a target
    @Test
    fun aTriggerWithATargetAsksForIt() = scenario {
        alice.battlefield("Crimson Mourner")
        bob.battlefield("Umbral Elves")
        alice.hand("Storm Spark")

        alice.castsWithoutCosts("Storm Spark", target = "Umbral Elves")
        resolve()
        val targets = decision<Decision.Targets>()
        assertEquals(alice.id, targets.player)
        answer(Answer.Targets(listOf(target("bob"))))
        resolve()

        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    // CR 603.3b: APNAP: the active player's triggers go on the Stack first, so the other player's resolve first
    @Test
    fun apnapOrder() = scenario {
        alice.battlefield("Remorseless Corsair", "Elvish Seer")
        bob.battlefield("Fertile Passing")
        bob.library("Forest")
        alice.library("Island")
        alice.hand("Storm Spark")

        alice.castsWithoutCosts("Storm Spark", target = "Elvish Seer")
        resolve()

        expect { stack.names shouldBe listOf("ability of Remorseless Corsair", "ability of Fertile Passing") }
        resolve()
        // CR 603.5: "may" is decided on resolution; it's the dead creature's controller who may draw
        assertEquals(alice.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        resolve()
        expect {
            alice.hand has "Island"
            alice.battlefield has "Treasure Token"
        }
    }

    // CR 603.3b: a player with two triggers at once orders them
    @Test
    fun aPlayerOrdersTheirOwnTriggers() = scenario {
        alice.battlefield("Remorseless Corsair", "Harborside Knifer", "Elvish Seer")
        alice.hand("Storm Spark")

        alice.castsWithoutCosts("Storm Spark", target = "Elvish Seer")
        resolve()
        val order = decision<Decision.OrderTriggers>()
        assertEquals(2, order.triggers.size)
        answer(Answer.Order(listOf(1, 0)))

        expect { stack.names shouldBe listOf("ability of Harborside Knifer", "ability of Remorseless Corsair") }
    }

    // CR 603.4: an intervening "if" is checked when it would trigger: persist doesn't return a creature that had a -1/-1 counter
    @Test
    fun persistReturnsOnce() = scenario {
        alice.battlefield("Festering Gremlin")
        alice.hand("Storm Spark", "Storm Spark")

        alice.castsWithoutCosts("Storm Spark", target = "Festering Gremlin")
        resolve()
        resolve()
        expect {
            alice.battlefield has "Festering Gremlin"
            "Festering Gremlin".counters(CounterKind.PowerToughness(-1, -1)) shouldBe 1
            "Festering Gremlin".power shouldBe 1
        }

        alice.castsWithoutCosts("Storm Spark", 1.let { "Festering Gremlin" })
        resolve()
        expect {
            alice.graveyard has "Festering Gremlin"
            stack.isEmpty()
        }
    }

    // CR 603.10a: an Equipment's "whenever equipped creature dies" looks back to what it was attached to
    @Test
    fun equippedCreatureDies() = scenario {
        alice.battlefield("Bonevise", "Elvish Hierophant")
        attach("Bonevise", "Elvish Hierophant")
        alice.library("Forest", "Island")
        alice.hand("Storm Spark")

        alice.castsWithoutCosts("Storm Spark", target = "Elvish Hierophant")
        resolve()
        resolve()

        expect { alice.hand.names shouldBe listOf("Forest", "Island") }
    }

    // CR 702.108a: prowess triggers on a noncreature spell
    @Test
    fun prowess() = scenario {
        alice.battlefield("Summit Ascetic")
        alice.hand("Storm Spark")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        expect { stack.names shouldBe listOf("Storm Spark", "ability of Summit Ascetic") }
        resolve()

        expect { "Summit Ascetic".power shouldBe 3 }
    }

    // CR 702.40a: storm copies for each spell cast before it this turn
    @Test
    fun stormCopies() = scenario {
        alice.hand("Storm Spark", "Glance Ahead", "Chitterstorm")
        alice.library("Forest", "Island")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        alice.castsWithoutCosts("Glance Ahead")
        alice.castsWithoutCosts("Chitterstorm")
        resolve()

        expect { stack.names shouldBe listOf("Storm Spark", "Glance Ahead", "Chitterstorm", "copy of Chitterstorm", "copy of Chitterstorm") }
        resolve()
        resolve()
        resolve()
        expect { alice.battlefield.names shouldBe listOf("Squirrel Token", "Squirrel Token", "Squirrel Token") }
    }

    // CR 702.85a: cascade exiles until a cheaper nonland card, which may be cast without paying; the rest go to the bottom
    // CR 601.2c: cast like any spell, it needs a legal target (Review1BlockTriggersTest.cascadeIntoASpellWithoutTargets)
    @Test
    fun cascade() = scenario {
        alice.hand("Splintered Envoy")
        alice.library("Forest", "Sudden Rot", "Storm Spark", "Island")
        bob.battlefield("Thicketwood Elves")

        alice.castsWithoutCosts("Splintered Envoy")
        resolve()
        // Sudden Rot (mana value 2) is the first nonland card with lower mana value: cast it?
        answer(Answer.YesNo(true))
        // Its only legal target is chosen without asking (P5)
        expect { stack.names shouldBe listOf("Splintered Envoy", "Sudden Rot") }
        assertEquals(listOf(target("Thicketwood Elves")), state.stackItems.getValue(card("Sudden Rot")).targets.values.single())
        expect {
            alice.library.names shouldBe listOf("Storm Spark", "Island", "Forest")
        }
    }

    // CR 603.2c, 603.2h: "one or more" triggers once for a batch; "only once each turn"
    @Test
    fun oneOrMoreAndOncePerTurn() = scenario {
        alice.battlefield("Elvish Drillmaster")
        alice.hand("Elvish Seer", "Umbral Elves")

        alice.castsWithoutCosts("Elvish Seer")
        resolve()
        expect { stack.size shouldBe 1 }
        resolve()
        alice.castsWithoutCosts("Umbral Elves")
        resolve()

        expect {
            stack.isEmpty()
            alice.battlefield.names.count { it == "Elf Warrior Token" } shouldBe 1
        }
    }

    // CR 603.2: "your second spell each turn" counts this turn's cast events
    @Test
    fun secondSpellEachTurn() = scenario {
        alice.battlefield("Invoice Shredder")
        alice.hand("Glance Ahead", "Mull It Over")
        alice.library("Forest", "Island", "Swamp", "Mountain")

        alice.castsWithoutCosts("Glance Ahead")
        expect { stack.size shouldBe 1 }
        alice.castsWithoutCosts("Mull It Over")
        expect { stack.names shouldBe listOf("Glance Ahead", "Mull It Over", "ability of Invoice Shredder") }
    }

    // CR 603.2, 500: "at the beginning of your upkeep"
    @Test
    fun upkeepTrigger() = scenario {
        alice.battlefield("Mana Reliquary")
        random(Answer.CoinFlip(false))

        beginStep(TurnStep.Upkeep)
        resolve()

        expect { alice.life shouldBe 17 }
    }

    // CR 603.2: only your own upkeep
    @Test
    fun upkeepTriggerOnlyOnItsControllersTurn() = scenario {
        alice.battlefield("Mana Reliquary")

        beginStep(TurnStep.Upkeep, active = bob)

        expect { stack.isEmpty() }
    }

    // CR 714.2b, 714.4: lore counters reach a chapter; after the last chapter the Saga is sacrificed
    @Test
    fun sagaChapters() = scenario {
        alice.battlefield("Haruka's Age of Honor", counters = listOf(CounterKind.Lore to 2))
        alice.battlefield("Elvish Seer")

        // CR 714.3b: a lore counter is put on after the draw step (a turn-based action)
        drive { r ->
            r.record(Changes.counters(r.state, TargetRef.Object(card("Haruka's Age of Honor")), CounterKind.Lore, 1))
            arcanum.engine.Progress.Done
        }
        expect { stack.names shouldBe listOf("ability of Haruka's Age of Honor") }
        resolve()

        expect {
            card("Haruka's Age of Honor").view.name shouldBe "Likeness of Haruka"
            card("Haruka's Age of Honor").zone shouldBe Zone.Battlefield
        }
    }

    // CR 603.12: "When you do" — the reflexive trigger goes on the Stack after the dies trigger resolved
    @Test
    fun reflexiveTriggerAfterPaying() = scenario {
        alice.battlefield("Undying Valet")
        alice.graveyard("Elvish Hierophant")
        alice.hand("Storm Spark")

        alice.castsWithoutCosts("Storm Spark", target = "Undying Valet")
        resolve()
        resolve()
        answer(Answer.YesNo(true))
        expect {
            alice.exile has "Undying Valet"
            stack.size shouldBe 1
        }
        resolve()
        expect { alice.hand has "Elvish Hierophant" }
    }

    // CR 702.21a: ward counters the spell unless its controller pays
    @Test
    fun wardCountersUnlessPaid() = scenario {
        bob.battlefield("Scrapquilt Automaton")
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Storm Spark")

        alice.castsWithoutCosts("Storm Spark", target = "Scrapquilt Automaton")
        expect { stack.names shouldBe listOf("Storm Spark", "ability of Scrapquilt Automaton") }
        resolve()
        assertEquals(alice.id, decision<Decision.May>().player)
        answer(Answer.YesNo(false))

        expect {
            alice.graveyard has "Storm Spark"
            stack.isEmpty()
            bob.battlefield has "Scrapquilt Automaton"
        }
    }

    // CR 701.28, 603.2: "when this transforms into …" after the last ice counter goes
    @Test
    fun transformsAfterTheLastCounter() = scenario {
        alice.battlefield("Shape Beneath the Floe", counters = listOf(CounterKind.Named("ice") to 1))
        bob.battlefield("Umbral Elves")
        alice.hand("Glance Ahead")
        alice.library("Forest", "Island")

        alice.castsWithoutCosts("Glance Ahead")
        resolve()
        expect { stack.names shouldBe listOf("Glance Ahead", "ability of Risen Leviathan") }
        resolve()
        expect { bob.hand has "Umbral Elves" }
    }

    // CR 603.8: a state trigger triggers once while its condition holds, and again after it has left the Stack
    @Test
    fun stateTrigger() = scenario {
        /** "When you control no other creatures, sacrifice this creature." */
        val lonely = Cards.define("Test Lonely Sentinel", CardDefinition(Layout.Normal, listOf(FaceDefinition("Test Lonely Sentinel",
            ManaCost.parse("{1}"), types = listOf(CardType.Creature), power = PrintedNumber.Fixed(3), toughness = PrintedNumber.Fixed(3),
            abilities = listOf(Ability.Triggered(Trigger.State(Condition.Not(Condition.Exists(ObjectFilter.And(listOf(
                ObjectFilter.Type(CardType.Creature), ObjectFilter.ControlledBy(PlayerFilter.You), ObjectFilter.Not(ObjectFilter.Is(Objects.Self)))), 1))),
                Instructions(steps = listOf(Step.Do(Effect.Sacrifice(Objects.Self))))))))))
        alice.battlefield(lonely, "Elvish Seer")
        alice.hand("Storm Spark")

        alice.castsWithoutCosts("Storm Spark", target = "Elvish Seer")
        resolve()
        expect { stack.names shouldBe listOf("ability of Test Lonely Sentinel") }
        resolve()
        expect { alice.graveyard has lonely }
    }

    // CR 603.6a: an enters trigger of a token, created by an effect
    @Test
    fun enteringTokensTriggerLandfallLikeAbilities() = scenario {
        alice.battlefield("Elvish Drillmaster")
        /** "Create a 1/1 green Elf creature token." */
        val elf = Cards.define("Test Kinship", CardDefinition(Layout.Normal, listOf(FaceDefinition("Test Kinship", ManaCost.parse("{G}"),
            types = listOf(CardType.Sorcery), abilities = listOf(Ability.Spell(Instructions(steps = listOf(Step.Do(Effect.CreateToken(
                arcanum.api.TokenSpec.Defined(FaceDefinition("Elf Token", colorIndicator = listOf(arcanum.api.Color.Green),
                    types = listOf(CardType.Creature), subtypes = listOf(arcanum.api.Subtype("Elf")),
                    power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1))), Value.Fixed(1), Players.You))))))))))
        alice.hand(elf)

        alice.castsWithoutCosts(elf)
        resolve()

        expect { stack.names shouldBe listOf("ability of Elvish Drillmaster") }
    }

    // CR 610.3, 610.3c: "until … leaves the battlefield": the exiled card returns under its owner's control by a one-shot
    // effect right after it leaves, without the Stack
    @Test
    fun exiledUntilItLeaves() = scenario {
        alice.hand("Pocket Rift")
        bob.battlefield("Umbral Elves")
        bob.hand("Old Enmity")

        alice.castsWithoutCosts("Pocket Rift")
        resolve()
        resolve()
        expect { bob.exile has "Umbral Elves" }

        bob.castsWithoutCosts("Old Enmity", target = "Pocket Rift")
        resolve()
        expect {
            bob.battlefield has "Umbral Elves"
            stack.isEmpty()
        }
    }

    @Suppress("unused")
    private val unused = listOf(Zone.Battlefield, Meaning.Died)

    // CR 113.6m: "return this card from your graveyard" works only in the graveyard: on the battlefield it doesn't trigger
    @Test
    fun returnFromGraveyardTriggerOnlyInTheGraveyard() = scenario {
        alice.battlefield("Gorehaunt")
        alice.hand("Swamp")
        alice.plays("Swamp")
        expect { stack.isEmpty() }
    }

    // CR 113.6m: in the graveyard it triggers
    @Test
    fun returnFromGraveyardTriggerInTheGraveyard() = scenario {
        alice.graveyard("Gorehaunt")
        alice.hand("Swamp")
        alice.plays("Swamp")
        expect { stack.names shouldBe listOf("ability of Gorehaunt") }
    }

    // CR 700.2, 603.3c: "choose one that hasn't been chosen this turn": a mode chosen is unavailable for the rest of the
    // turn, and available again the next turn
    @Test
    fun modeNotChosenThisTurn() = scenario {
        alice.battlefield("Memorial to Perseverance")
        alice.hand("Crow's Larceny", "Crow's Larceny", "Forest", "Island")

        alice.castsWithoutCosts("Crow's Larceny", target = "alice")
        resolve()
        alice.chooses("Forest")
        answer(arcanum.engine.decision.Answer.Modes(listOf(2)))
        resolve()
        expect { bob.life shouldBe 17 }

        beginStep(TurnStep.Upkeep, active = bob)
        alice.castsWithoutCosts("Crow's Larceny", target = "alice")
        resolve()
        assertEquals(listOf(true, true, true), decision<arcanum.engine.decision.Decision.Modes>().options.map { it.available })
    }

    @Suppress("unused")
    private fun check(b: Boolean) = assertTrue(b)

    // CR 603.6a, 603.4: "enters from your hand, if it's attacking" works on the battlefield, and a creature put onto the
    // battlefield attacking (ninjutsu, CR 508.4) is attacking as it enters
    @Test
    fun entersFromTheHandAttacking() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Elvish Seer", "Umbral Elves")
        alice.hand("Myriad-Faced Shade")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Elvish Seer", "Umbral Elves")
        advanceTo(TurnStep.DeclareBlockers)
        alice.activates("Myriad-Faced Shade", choosing = listOf("Elvish Seer"))
        allPass()
        expect { stack.names shouldBe listOf("ability of Myriad-Faced Shade") }
    }
}
