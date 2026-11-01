package arcanum.engine.casting

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Activating abilities (CR 602), loyalty abilities (CR 606) and mana abilities (CR 605). */
class ActivationTest {
    // CR 602.2: an activated ability goes on the Stack; its activator keeps priority (CR 117.3c)
    @Test
    fun abilityGoesOnTheStack() = scenario {
        alice.battlefield("Velis, Mender of the Lost Way")
        alice.activates("Velis, Mender of the Lost Way", target = "bob")
        val item = state.stackItems.values.single()
        assertEquals(StackItemKind.Activated, item.kind)
        assertEquals(alice.id, item.controller)
        assertEquals(listOf(TargetRef.Player(bob.id)), item.targets.values.single())
        assertEquals(alice.id, state.priority.holder)
        expect { "Velis, Mender of the Lost Way".tapped shouldBe true }
        assertTrue(applied.any { it is Change.Happened && it.event is GameEvent.Activated })
        assertTrue(applied.any { it is Change.StackItemAdded && Meaning.Activated in it.meanings })
    }

    // CR 608.2, 117.3b: the ability leaves the Stack as it resolves
    @Test
    fun abilityResolves() = scenario {
        alice.battlefield("Velis, Mender of the Lost Way")
        alice.activates("Velis, Mender of the Lost Way", target = "bob")
        allPass()
        expect { stack.isEmpty() }
        assertTrue(state.stackItems.isEmpty())
    }

    // CR 302.6, 602.5a: a creature's {T} ability needs it controlled since the turn began
    @Test
    fun summoningSickCreatureCantTap() = scenario {
        alice.battlefield("Velis, Mender of the Lost Way", sick = true)
        assertFailsWith<IllegalArgumentException> { alice.activates("Velis, Mender of the Lost Way", target = "bob") }
    }

    // CR 702.10b, 302.6: haste lifts it
    @Test
    fun hasteLetsItTap() = scenario {
        Cards.invent("Hasty Druid", "{G}", "Creature — Elf Druid", "Haste\n{T}: Add {G}.", "1/1")
        alice.battlefield("Hasty Druid", sick = true)
        alice.activates("Hasty Druid")
        assertEquals(listOf(Color.Green), alice.pool.map { it.color })
    }

    // CR 602.5d, 702.6a: equip only as a sorcery
    @Test
    fun equipOnlyAsASorcery() = scenario {
        alice.battlefield("Eden Cloak", "Elvish Seer", "Forest", "Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        assertFailsWith<IllegalArgumentException> { alice.activates("Eden Cloak", target = "Elvish Seer") }
        allPass()
        alice.activates("Eden Cloak", target = "Elvish Seer")
        expect { stack.size shouldBe 1 }
    }

    // CR 702.6a: equip targets a creature you control
    @Test
    fun equipTargetsOwnCreatures() = scenario {
        alice.battlefield("Eden Cloak", "Elvish Seer", "Forest")
        bob.battlefield("Thicketwood Elves")
        alice.activates("Eden Cloak")
        assertEquals(listOf(TargetRef.Object(card("Elvish Seer"))), assertIs<Decision.Targets>(pending).options)
    }

    // CR 606.3: a loyalty ability: put on its loyalty cost, once each turn
    @Test
    fun loyaltyAbilityOncePerTurn() = scenario {
        alice.battlefield("Renji Kagehara", counters = listOf(CounterKind.Loyalty to 3))
        alice.library("Forest", "Island")
        alice.activates("Renji Kagehara", ability = 0)
        expect { "Renji Kagehara".counters(CounterKind.Loyalty) shouldBe 4 }
        allPass()
        assertFailsWith<IllegalArgumentException> { alice.activates("Renji Kagehara", ability = 1) }
    }

    // CR 606.3: loyalty abilities as a sorcery: not with something on the Stack
    @Test
    fun loyaltyAbilityNotWithTheStackFull() = scenario {
        alice.battlefield("Renji Kagehara", "Mountain", counters = listOf(CounterKind.Loyalty to 3))
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        assertFailsWith<IllegalArgumentException> { alice.activates("Renji Kagehara", ability = 0) }
    }

    // CR 606.3: not on another player's turn
    @Test
    fun loyaltyAbilityNotOnTheOpponentsTurn() = scenario {
        bob.battlefield("Renji Kagehara", counters = listOf(CounterKind.Loyalty to 3))
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.activates("Renji Kagehara", ability = 0) }
    }

    // CR 606.6: a minus ability needs that many loyalty counters
    @Test
    fun minusAbilityNeedsLoyalty() = scenario {
        alice.battlefield("Renji Kagehara", counters = listOf(CounterKind.Loyalty to 1))
        assertFailsWith<IllegalArgumentException> { alice.activates("Renji Kagehara", ability = 1) }
    }

    // CR 606.4: a minus ability removes loyalty counters
    @Test
    fun minusAbilityRemovesLoyalty() = scenario {
        alice.battlefield("Renji Kagehara", counters = listOf(CounterKind.Loyalty to 3))
        alice.activates("Renji Kagehara", ability = 1)
        expect { "Renji Kagehara".counters(CounterKind.Loyalty) shouldBe 1 }
    }

    // CR 602.5b: "Activate only once each turn"
    @Test
    fun onlyOnceEachTurn() = scenario {
        alice.battlefield("Scorching Mudlizard", "Mountain", "Mountain")
        alice.activates("Scorching Mudlizard")
        allPass()
        assertFailsWith<IllegalArgumentException> { alice.activates("Scorching Mudlizard") }
    }

    // CR 602.5b: the limit counts again next turn
    @Test
    fun onceEachTurnAgainNextTurn() = scenario {
        alice.battlefield("Scorching Mudlizard", "Mountain")
        alice.library("Island")
        bob.battlefield("Mountain")
        alice.activates("Scorching Mudlizard")
        allPass()
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates("Scorching Mudlizard")
        expect { stack.size shouldBe 1 }
    }

    // CR 602.5b: "Activate only if you control three or more artifacts" (metalcraft)
    @Test
    fun activationCondition() = scenario {
        alice.battlefield("Opaline Trinket", "Verdant Bauble")
        assertFailsWith<IllegalArgumentException> { alice.activates("Opaline Trinket", color = Color.Red) }
    }

    @Test
    fun activationConditionMet() = scenario {
        // CR 602.5b
        alice.battlefield("Opaline Trinket", "Verdant Bauble", "Onyx Bauble")
        alice.activates("Opaline Trinket", color = Color.Red)
        assertEquals(listOf(Color.Red), alice.pool.map { it.color })
    }

    // CR 602.2: "Any player may activate this ability"
    @Test
    fun anyPlayerMayActivate() = scenario {
        alice.battlefield("Gloomwing Skulker")
        bob.hand("Forest")
        alice.passes()
        bob.activates("Gloomwing Skulker", choosing = listOf("Forest"))
        val item = state.stackItems.values.single()
        assertEquals(bob.id, item.controller)
        expect { bob.graveyard has "Forest" }
    }

    // CR 602.2: otherwise only its controller
    @Test
    fun onlyTheControllerActivates() = scenario {
        alice.battlefield("Velis, Mender of the Lost Way")
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.activates("Velis, Mender of the Lost Way", target = "alice") }
    }

    // CR 702.29a, 113.6m: cycling works from the hand, discarding the card as a cost
    @Test
    fun cyclingFromTheHand() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Spymaster's Spire")
        alice.activates("Spymaster's Spire")
        expect {
            alice.graveyard has "Spymaster's Spire"
            stack.size shouldBe 1
            log mentions "Discarded"
        }
    }

    // CR 113.6: a cycling ability doesn't work from the battlefield
    @Test
    fun noCyclingFromTheBattlefield() = scenario {
        alice.battlefield("Spymaster's Spire", "Island", "Island", "Island")
        val abilities = view()[card("Spymaster's Spire")]!!.characteristics!!.abilities
        val cycling = abilities.first { inst ->
            (inst.ability as? arcanum.api.Ability.Activated)?.cost?.parts.orEmpty().any { it is arcanum.api.CostPart.Discard }
        }
        assertFailsWith<IllegalArgumentException> { perform(arcanum.engine.input.Input.Activate(alice.id, card("Spymaster's Spire"), cycling.origin)) }
    }

    // CR 602.2, 118: {T}, pay 1 life, sacrifice it: costs paid together; its last known information stays with the ability
    @Test
    fun fetchLandCosts() = scenario {
        alice.battlefield("Sunbaked Mesa")
        alice.activates("Sunbaked Mesa")
        expect {
            alice.life shouldBe 19
            alice.graveyard has "Sunbaked Mesa"
            stack.size shouldBe 1
        }
        val item = state.stackItems.values.single()
        assertTrue(item.snapshots.any { it.characteristics?.name == "Sunbaked Mesa" })
    }

    // D58: cancelling an activation before paying leaves everything as it was
    @Test
    fun cancelAnActivation() = scenario {
        alice.battlefield("Quarry Gouge")
        bob.battlefield("Forest")
        alice.activates("Quarry Gouge", ability = 1)
        assertIs<Decision.Targets>(pending)
        perform(arcanum.engine.input.Input.Cancel)
        expect {
            alice.battlefield has "Quarry Gouge"
            "Quarry Gouge".tapped shouldBe false
            stack.isEmpty()
        }
    }

    // CR 107.3, 602.2b: an {X} activation cost is announced
    @Test
    fun xInAnActivationCost() = scenario {
        alice.battlefield("Glass Polymorph", "Plains", "Plains", "Plains")
        alice.activates("Glass Polymorph", x = 3)
        expect { (0..2).all { card("Plains", it).tapped } shouldBe true }
        assertEquals(3, state.stackItems.values.single().announced["X"])
    }

    // CR 601.2f, 602.2b: "costs {1} less to activate for each legendary creature you control"
    @Test
    fun activationCostReduction() = scenario {
        alice.battlefield("Velis, Mender of the Lost Way", "Forest")
        bob.battlefield("Lotus Frond")
        alice.hand("Okurai, Who Abides")
        alice.activates("Okurai, Who Abides", ability = 1, target = "Lotus Frond")
        expect {
            alice.graveyard has "Okurai, Who Abides"
            "Forest".tapped shouldBe true
            stack.size shouldBe 1
        }
    }

    // CR 702.84a: unearth from the graveyard, as a sorcery
    @Test
    fun unearthFromTheGraveyard() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard("Junkyard Cur")
        alice.activates("Junkyard Cur")
        expect { stack.size shouldBe 1 }
    }

    // CR 702.49a: ninjutsu: from the hand, returning an unblocked attacker
    @Test
    fun ninjutsu() = scenario {
        alice.battlefield("Elvish Seer", "Island", "Island")
        alice.hand("Ninja of the Silent Hours")
        advanceTo(TurnStep.BeginningOfCombat)
        allPass()
        alice.attacks("Elvish Seer")
        allPass()
        assertEquals(TurnStep.DeclareBlockers, state.turn.step)
        alice.activates("Ninja of the Silent Hours")
        expect {
            alice.hand has "Elvish Seer"
            stack.size shouldBe 1
        }
    }

    // CR 702.49a: ninjutsu needs an unblocked attacker
    @Test
    fun ninjutsuNeedsAnUnblockedAttacker() = scenario {
        alice.battlefield("Elvish Seer", "Island", "Island")
        alice.hand("Ninja of the Silent Hours")
        assertFailsWith<IllegalArgumentException> { alice.activates("Ninja of the Silent Hours") }
    }

    // CR 605.3b: a mana ability doesn't use the Stack; its mana is in the pool at once
    @Test
    fun manaAbilityResolvesAtOnce() = scenario {
        alice.battlefield("Lotus Frond")
        alice.activates("Lotus Frond", color = Color.Blue)
        expect {
            stack.isEmpty()
            alice.graveyard has "Lotus Frond"
        }
        assertEquals(listOf(Color.Blue), alice.pool.map { it.color })
        assertTrue(applied.any { it is Change.Happened && (it.event as? GameEvent.Activated)?.manaAbility == true })
    }

    // CR 106.1: "three mana of any one color"
    @Test
    fun threeManaOfOneColour() = scenario {
        alice.battlefield("Midnight Lotus")
        alice.activates("Midnight Lotus", color = Color.Black)
        assertEquals(List(3) { Color.Black }, alice.pool.map { it.color })
    }

    // CR 605.3a: a mana ability with a cost choice: which creature to sacrifice
    @Test
    fun manaAbilitySacrificingACreature() = scenario {
        alice.battlefield("Renderer's Altar", "Elvish Seer", "Thicketwood Elves")
        alice.activates("Renderer's Altar", choosing = listOf("Thicketwood Elves"))
        assertEquals(listOf<Color?>(null, null), alice.pool.map { it.color })
        expect { alice.graveyard has "Thicketwood Elves" }
    }

    // CR 605.3a: tapping three untapped Elves (summoning sick ones too: it isn't their own {T})
    @Test
    fun tapThreeElves() = scenario {
        alice.battlefield("Lineage Druid", "Elvish Seer", "Thicketwood Elves")
        alice.battlefield("Frosthorn Elves", sick = true)
        alice.activates("Lineage Druid", choosing = listOf("Elvish Seer", "Thicketwood Elves", "Frosthorn Elves"))
        assertEquals(List(3) { Color.Green }, alice.pool.map { it.color })
        expect { "Frosthorn Elves".tapped shouldBe true }
    }

    // CR 605.3b: what a mana ability does beyond adding mana happens at once, without the Stack
    @Test
    fun manaAbilityWithMoreToDo() = scenario {
        alice.battlefield("Umbral Elves")
        alice.activates("Umbral Elves")
        assertEquals(listOf(Color.Black), alice.pool.map { it.color })
        assertEquals(null, pending)
        expect {
            alice.life shouldBe 19
            stack.isEmpty()
        }
    }

    // D61: a part of a mana ability that needs a decision is left to the players, with a manual step
    @Test
    fun manaAbilityWithAChoiceLeftToPlayers() = scenario {
        // "{T}: Add {G}. You may gain 1 life."
        val seer = Cards.definition("Elvish Seer")
        val face = seer.faces.single()
        val mana = face.abilities.single() as arcanum.api.Ability.Activated
        val may = arcanum.api.Step.May(listOf(arcanum.api.Step.Do(arcanum.api.Effect.GainLife(arcanum.api.Players.You, arcanum.api.Value.Fixed(1)))), arcanum.api.Players.You)
        Cards.define("Hopeful Druid", seer.copy(faces = listOf(face.copy(name = "Hopeful Druid",
            abilities = listOf(mana.copy(instructions = mana.instructions.copy(steps = mana.instructions.steps + may)))))))
        alice.battlefield("Hopeful Druid")
        alice.activates("Hopeful Druid")
        assertEquals(listOf(Color.Green), alice.pool.map { it.color })
        assertIs<Decision.Manual>(pending)
        answer(Answer.Done)
        assertEquals(null, pending)
        expect { alice.life shouldBe 20 }
    }

    // CR 605.3a: "Add X mana in any combination of colors": a colour is chosen for each mana
    @Test
    fun manaInAnyCombinationOfColours() = scenario {
        alice.battlefield("Sorcerer's Flares", "Plains", "Plains", "Plains")
        alice.activates("Sorcerer's Flares", x = 3)
        answer(Answer.ColorChoice(Color.Red))
        answer(Answer.ColorChoice(Color.Green))
        answer(Answer.ColorChoice(Color.Green))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        assertEquals(listOf<Color?>(Color.Red, Color.Green, Color.Green), alice.pool.map { it.color })
    }

    // CR 605.3a: "three mana of any one color" stays one colour for all of it
    @Test
    fun manaOfAnyOneColourIsAskedOnce() = scenario {
        alice.battlefield("Midnight Lotus")
        alice.activates("Midnight Lotus")
        answer(Answer.ColorChoice(Color.White))
        assertEquals(null, pending)
        assertEquals(List(3) { Color.White }, alice.pool.map { it.color })
    }
}
