package arcanum.engine.review

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Review 2: layers in depth (CR 613): copy effects with counters and later P/T effects, CDAs that are copied, face-down
 * permanents under other effects and turning face up, control changes and summoning sickness, split and modal cards in
 * each zone.
 */
class Review2LayersTest {
    private fun set(p: Int, t: Int) = Mechanic.SetPowerToughness(Value.Fixed(p), Value.Fixed(t))
    private fun modify(p: Int, t: Int) = Mechanic.ModifyPowerToughness(Value.Fixed(p), Value.Fixed(t))
    private val plusOne = CounterKind.PowerToughness(1, 1)

    /** A set P/T effect (7b) from before a copy effect (layer 1) still applies on top of the copied values: layers come
     * before timestamps. */
    // CR 613.1a, 613.4b, 613.7
    @Test
    fun anEarlierSetEffectAppliesOverALaterCopy() = scenario {
        alice.battlefield("Elvish Seer", "Brakka, Wild Whelp")
        effect(alice, set(0, 3), on = listOf("Elvish Seer"))
        effect(alice, Mechanic.CopyOf(Objects.Bound("original")), on = listOf("Elvish Seer"), names = mapOf("original" to "Brakka, Wild Whelp"))
        expect {
            card("Elvish Seer").view.name shouldBe "Brakka, Wild Whelp"
            card("Elvish Seer").view.power shouldBe 0
            card("Elvish Seer").view.toughness shouldBe 3
        }
    }

    /** A copy keeps its own counters and the modifications on it: copy of a 2/2 with a +1/+1 counter and +1/+0 is 4/3. */
    // CR 613.4c, 707.2, 122.1a
    @Test
    fun aCopyKeepsCountersAndModifications() = scenario {
        alice.battlefield("Elvish Seer", counters = listOf(plusOne to 1))
        alice.battlefield("Brakka, Wild Whelp")
        effect(alice, modify(1, 0), on = listOf("Elvish Seer"))
        effect(alice, Mechanic.CopyOf(Objects.Bound("original")), on = listOf("Elvish Seer"), names = mapOf("original" to "Brakka, Wild Whelp"))
        expect {
            card("Elvish Seer").view.power shouldBe 4
            card("Elvish Seer").view.toughness shouldBe 3
        }
    }

    /** A copied characteristic-defining ability counts for the copy's controller: bob's copy of alice's Lord of the
     * Aetherforge counts bob's artifacts. */
    // CR 707.2, 604.3, 613.4a
    @Test
    fun aCopiedCdaCountsForTheCopysController() = scenario {
        alice.battlefield("Lord of the Aetherforge", "Sun Band", "Verdant Bauble", "Onyx Bauble")
        bob.battlefield("Elvish Seer", "Sun Band")
        effect(bob, Mechanic.CopyOf(Objects.Bound("original")), on = listOf("Elvish Seer"), names = mapOf("original" to "Lord of the Aetherforge"))
        expect {
            "Lord of the Aetherforge".power shouldBe 4
            // bob controls the copy (an artifact creature) and a Sun Band
            card("Elvish Seer").view.power shouldBe 2
        }
    }

    /** Copying a face-down creature gives a face-up nameless 2/2 (its copiable values are the face-down ones). */
    // CR 707.2 (the Clone example), 708.2a
    @Test
    fun copyingAFaceDownCreatureGivesANamelessTwoTwo() = scenario {
        alice.battlefield("Elvish Hierophant", faceDown = true)
        alice.battlefield("Elvish Seer")
        effect(alice, Mechanic.CopyOf(Objects.Bound("original")), on = listOf("Elvish Seer"), names = mapOf("original" to "Elvish Hierophant"))
        expect {
            card("Elvish Seer").view.name shouldBe null
            card("Elvish Seer").view.power shouldBe 2
            card("Elvish Seer").view.characteristics!!.abilities shouldBe emptyList()
        }
    }

    /** A face-down permanent that becomes a copy stays a face-down 2/2. */
    // CR 708.10
    @Test
    fun aFaceDownPermanentThatBecomesACopyStaysTwoTwo() = scenario {
        alice.battlefield("Elvish Seer", faceDown = true)
        alice.battlefield("Brakka, Wild Whelp")
        effect(alice, Mechanic.CopyOf(Objects.Bound("original")), on = listOf("Elvish Seer"), names = mapOf("original" to "Brakka, Wild Whelp"))
        expect {
            card("Elvish Seer").view.name shouldBe null
            card("Elvish Seer").view.power shouldBe 2
        }
    }

    /** An anthem for other creatures applies to a face-down creature; a type-specific (Elf) anthem doesn't. */
    // CR 708.2a, 613.4c
    @Test
    fun faceDownCreaturesGetGeneralButNotTypedAnthems() = scenario {
        alice.battlefield("Gloomcrest Herald", "Elvish Hierophant")
        alice.battlefield("Elvish Seer", faceDown = true)
        expect {
            card("Elvish Seer").view.power shouldBe 3
            card("Elvish Seer").view.characteristics!!.colors shouldBe listOf(Color.Black)
        }
    }

    /** A face-down 2/2 with 1 damage turned face up as a 1/1 dies to state-based actions: damage stays. */
    // CR 708.8, 704.5g
    @Test
    fun turningFaceUpKeepsDamage() = scenario {
        alice.battlefield("Forest")
        alice.battlefield("Barkwood Pathfinders", faceDown = true)
        alice.hand("Magma Needle")
        alice.battlefield("Mountain")
        alice.casts("Magma Needle", target = "Barkwood Pathfinders")
        allPass()
        expect { "Barkwood Pathfinders".power shouldBe 2 }
        val turnUp = Engine.options(state, alice.id).filterIsInstance<Input.Activate>().single { it.source == card("Barkwood Pathfinders") }
        perform(turnUp)
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect { alice.graveyard has "Barkwood Pathfinders" }
    }

    /** Turning a face-down permanent face up keeps the effects already on it (+2/+2 until end of turn). */
    // CR 708.8
    @Test
    fun turningFaceUpKeepsEffects() = scenario {
        alice.battlefield("Forest")
        alice.battlefield("Barkwood Pathfinders", faceDown = true)
        effect(alice, modify(2, 2), on = listOf("Barkwood Pathfinders"))
        val turnUp = Engine.options(state, alice.id).filterIsInstance<Input.Activate>().single { it.source == card("Barkwood Pathfinders") }
        perform(turnUp)
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect {
            "Barkwood Pathfinders".power shouldBe 3
            "Barkwood Pathfinders".view.name shouldBe "Barkwood Pathfinders"
        }
    }

    /** Turning face up is a special action: it doesn't use the Stack and can be done any time the player has priority,
     * also with a spell on the Stack. */
    // CR 702.37e, 116.2b
    @Test
    fun turningFaceUpWithASpellOnTheStack() = scenario {
        alice.battlefield("Forest", "Mountain")
        alice.battlefield("Barkwood Pathfinders", faceDown = true)
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        turn(number = 2, active = bob, priority = bob)
        bob.casts("Storm Spark", target = "Barkwood Pathfinders")
        bob.passes()
        val turnUp = Engine.options(state, alice.id).filterIsInstance<Input.Activate>().singleOrNull { it.source == card("Barkwood Pathfinders") }
        assertTrue(turnUp != null, "turning face up is offered with a spell on the Stack")
        perform(turnUp)
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect { stack.size shouldBe 1 }
    }

    /** A face-down permanent returned to its owner's hand is a face-up card there. */
    // CR 708.9, 400.7
    @Test
    fun aBouncedFaceDownCardIsFaceUpInHand() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Click Back")
        bob.battlefield("Barkwood Pathfinders", faceDown = true)
        alice.casts("Click Back", target = "Barkwood Pathfinders")
        allPass()
        val inHand = state.objOrThrow(card("Barkwood Pathfinders"))
        assertEquals(Zone.Hand, inHand.zone)
        assertFalse(inHand.faceDown)
    }

    /** A face-down creature has no name, so two face-down legends don't fall under the legend rule. */
    // CR 708.2a, 704.5j
    @Test
    fun faceDownLegendsDontTriggerTheLegendRule() = scenario {
        alice.battlefield("Velo, Master of Ceremonies", faceDown = true)
        alice.battlefield("Velo, Master of Ceremonies", faceDown = true)
        settle()
        expect { alice.battlefield.size shouldBe 2 }
        assertNull(pending)
    }

    /** A creature whose control changed this turn is summoning sick for its new controller: its {T} mana ability
     * isn't offered and can't be activated. */
    // CR 302.6, 605.1a
    @Test
    fun aStolenCreatureIsSummoningSick() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand("Pilfering Skydiver")
        bob.battlefield("Gearwing of Eden")
        alice.casts("Pilfering Skydiver", kicked = 1, x = 2)
        allPass()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Gearwing of Eden"))))
        allPass()
        expect { "Gearwing of Eden".controller shouldBe alice.id }
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().none { it.source == card("Gearwing of Eden") })
        assertTrue(view()[card("Gearwing of Eden")]!!.summoningSick)
    }

    /** A stolen noncreature artifact's {T} mana ability can be used at once: summoning sickness is for creatures. */
    // CR 302.6
    @Test
    fun aStolenNoncreatureArtifactCanTapAtOnce() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Pilfering Skydiver")
        bob.battlefield("Sun Band")
        alice.casts("Pilfering Skydiver", kicked = 1, x = 1)
        allPass()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Sun Band"))))
        allPass()
        expect { "Sun Band".controller shouldBe alice.id }
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().any { it.source == card("Sun Band") })
    }

    /** Control taken until end of turn comes back at the cleanup step; on its owner's next turn the creature isn't
     * summoning sick: they have controlled it continuously since that turn began. */
    // CR 506.4, 302.6
    @Test
    fun controlReturningAtEndOfTurnLeavesItSickForTheNextTurnOnlyIfNew() = scenario {
        alice.battlefield("Brakka, Wild Whelp")
        bob.battlefield("Elvish Seer")
        effect(alice, Mechanic.ControlledBy(Players.You), on = listOf("Elvish Seer"))
        expect { "Elvish Seer".controller shouldBe alice.id }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { "Elvish Seer".controller shouldBe bob.id }
        // bob has controlled it continuously since his turn 2 began: not sick
        assertFalse(view()[card("Elvish Seer")]!!.summoningSick)
    }

    /** On the Stack a split spell has only the half cast (Expanse, mana value 2), so "counter target spell with mana
     * value 2" can target it; in the graveyard it has both halves (mana value 6). */
    // CR 709.3, 709.4
    @Test
    fun aSplitSpellOnTheStackHasOnlyTheCastHalf() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand("Expanse // Eruption")
        bob.battlefield("Mountain", "Island")
        bob.hand("Storm Spark", "Spell Noose")
        turn(number = 2, active = bob)
        bob.casts("Storm Spark", target = "alice")
        bob.passes()
        alice.casts("Expanse // Eruption", way = CastWay(face = 0), target = "Storm Spark")
        alice.passes()
        bob.casts("Spell Noose", target = "Expanse // Eruption")
        allPass()
        expect {
            alice.graveyard has "Expanse // Eruption"
            card("Expanse // Eruption").view.characteristics!!.manaValue() shouldBe 6
        }
    }

    /** Losing all abilities removes an anthem only from the creature that loses them; a creature losing abilities before
     * the anthem's own timestamp is still pumped by others. "Hush of the Meek" makes every creature 1/1 without
     * abilities: Hierophant's Elf anthem is gone. */
    // CR 613.8, 613.1f, 613.4b
    @Test
    fun hushOfTheMeekStopsTheAnthem() = scenario {
        alice.battlefield("Elvish Hierophant", "Elvish Seer", "Lord of the Aetherforge")
        alice.battlefield("Hush of the Meek")
        expect {
            "Elvish Seer".power shouldBe 1
            "Elvish Hierophant".power shouldBe 1
            "Lord of the Aetherforge".power shouldBe 1
        }
    }

    /** Hush of the Meek's 1/1 (7b) doesn't stop counters (7c). */
    // CR 613.4b, 613.4c
    @Test
    fun hushOfTheMeekKeepsCounters() = scenario {
        alice.battlefield("Elvish Seer", counters = listOf(plusOne to 2))
        alice.battlefield("Hush of the Meek")
        expect { "Elvish Seer".power shouldBe 3 }
    }

    /** A Saga gets its lore counter as the precombat main phase begins, not during the draw step. */
    // CR 714.3c
    @Test
    fun loreCounterAtTheBeginningOfThePrecombatMainPhase() = scenario {
        turn(number = 3, step = TurnStep.Upkeep)
        alice.battlefield("Haruka's Age of Honor", counters = listOf(CounterKind.Lore to 1))
        alice.library("Island", "Island")
        advanceTo(TurnStep.Draw)
        expect { "Haruka's Age of Honor".counters(CounterKind.Lore) shouldBe 1 }
        advanceTo(TurnStep.PrecombatMain)
        expect { "Haruka's Age of Honor".counters(CounterKind.Lore) shouldBe 2 }
    }

    /** The Saga's last chapter exiles it and returns it transformed: a 0/0 + 1/1 per artifact or enchantment, itself
     * included (an enchantment creature), so 1/1; it isn't sacrificed. */
    // CR 714.2b, 714.4, 712.14
    @Test
    fun sagaThirdChapterTransforms() = scenario {
        alice.battlefield("Elvish Seer")
        alice.battlefield("Haruka's Age of Honor", counters = listOf(CounterKind.Lore to 2))
        turn(number = 3, step = TurnStep.Draw)
        alice.library("Island")
        advanceTo(TurnStep.PrecombatMain)
        resolveStack()
        allPass()
        expect {
            "Likeness of Haruka".power shouldBe 1
            "Likeness of Haruka".types shouldBe listOf(CardType.Enchantment, CardType.Creature)
        }
    }

    /** Level up is sorcery speed; at level 1 the leveler is a 1/2 with "{T}: Add {G}{G}". */
    // CR 711.2a, 702.87a
    @Test
    fun levelUpGivesTheLevelAbilities() = scenario {
        alice.battlefield("Canopy Whisperer", "Forest", "Forest")
        alice.activates("Canopy Whisperer")
        allPass()
        expect {
            "Canopy Whisperer".power shouldBe 1
            "Canopy Whisperer".toughness shouldBe 2
            "Canopy Whisperer".counters(CounterKind.Level) shouldBe 1
        }
        val abilities = view()[card("Canopy Whisperer")]!!.characteristics!!.abilities.filter { it.ability is Ability.Activated }
        assertEquals(2, abilities.size, "level up and {T}: Add {G}{G}")
    }

    /** Level up only as a sorcery: not offered on the opponent's turn. */
    // CR 702.87a
    @Test
    fun levelUpOnlyAsASorcery() = scenario {
        alice.battlefield("Canopy Whisperer", "Forest", "Forest")
        turn(number = 2, active = bob, priority = alice)
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().none { it.source == card("Canopy Whisperer") })
    }

    /** A phased-out permanent is treated as though it doesn't exist: its anthem doesn't apply, it can't be targeted,
     * and a phased-out legend doesn't share the legend rule. */
    // CR 702.26b
    @Test
    fun aPhasedOutPermanentDoesntExist() = scenario {
        alice.battlefield("Elvish Hierophant", "Elvish Seer", "Velo, Master of Ceremonies", "Velo, Master of Ceremonies")
        listOf(card("Elvish Hierophant"), card("Velo, Master of Ceremonies", 1)).forEach { id ->
            state._objects[id] = state.objOrThrow(id).copy(phasedOut = true)
        }
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        settle()
        assertNull(pending, "no legend rule with a phased-out Velo")
        expect { "Elvish Seer".power shouldBe 1 }
    }
}
