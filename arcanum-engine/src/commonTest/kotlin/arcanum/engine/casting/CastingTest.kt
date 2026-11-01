package arcanum.engine.casting

import arcanum.api.CardType
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Casting spells (CR 601) and playing lands (CR 305). */
class CastingTest {
    // CR 601.2a, 601.2i, 608.3: a creature spell goes on the Stack, then resolves onto the battlefield
    @Test
    fun creatureSpellResolvesOntoTheBattlefield() = scenario {
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        alice.casts("Elvish Seer")
        expect {
            stack.names shouldBe listOf("Elvish Seer")
            "Forest".tapped shouldBe true
            log mentions "moved from Hand to Stack"
        }
        allPass()
        expect {
            alice.battlefield has "Elvish Seer"
            stack.isEmpty()
        }
    }

    // CR 601.2i: the spell becomes cast with all its choices: the Stack item carries the cast meaning
    @Test
    fun becomingCastIsTagged() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        val added = applied.filterIsInstance<Change.StackItemAdded>().single()
        assertEquals(listOf(Meaning.Cast), added.meanings)
        assertEquals(StackItemKind.Spell, added.item.kind)
        assertEquals(listOf(TargetRef.Player(bob.id)), added.item.targets["any"])
        assertEquals(Zone.Hand, state.objOrThrow(card("Storm Spark")).facts.castFrom)
    }

    // CR 117.3c: the caster receives priority after casting
    @Test
    fun casterKeepsPriority() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        alice.passes()
        bob.passes()
        // A new step: alice has priority again
        alice.casts("Storm Spark", target = "bob")
        assertEquals(alice.id, state.priority.holder)
        assertEquals(0, state.priority.passes)
    }

    // CR 117.3b, 608.1: after resolving, the active player receives priority
    @Test
    fun activePlayerGetsPriorityAfterResolution() = scenario {
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "alice")
        bob.passes()
        alice.passes()
        expect { bob.graveyard has "Storm Spark" }
        assertEquals(alice.id, state.priority.holder)
        assertEquals(TurnStep.PrecombatMain, state.turn.step)
    }

    // CR 405.5, 117.4: the top of the Stack resolves first; the next waits for another round of passes
    @Test
    fun lastInFirstOut() = scenario {
        alice.battlefield("Forest", "Mountain")
        alice.hand("Elvish Seer", "Storm Spark")
        alice.casts("Elvish Seer")
        alice.casts("Storm Spark", target = "bob")
        allPass()
        expect {
            alice.graveyard has "Storm Spark"
            stack.names shouldBe listOf("Elvish Seer")
        }
        allPass()
        expect { alice.battlefield has "Elvish Seer" }
    }

    // CR 307.1: a sorcery only in your main phase with an empty Stack
    @Test
    fun sorceryOnlyAtSorcerySpeed() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Cinder Bolt", "Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        assertFailsWith<IllegalArgumentException> { alice.casts("Cinder Bolt", target = "bob") }
    }

    // CR 307.1: not on an opponent's turn
    @Test
    fun noSorceryOnTheOpponentsTurn() = scenario {
        bob.battlefield("Mountain")
        bob.hand("Cinder Bolt")
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.casts("Cinder Bolt", target = "alice") }
    }

    // CR 307.1: not outside a main phase
    @Test
    fun noSorceryInCombat() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Cinder Bolt")
        advanceTo(TurnStep.BeginningOfCombat)
        assertFailsWith<IllegalArgumentException> { alice.casts("Cinder Bolt", target = "bob") }
    }

    // CR 117.1a: an instant any time its caster has priority
    @Test
    fun instantOnTheOpponentsTurn() = scenario {
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        advanceTo(TurnStep.BeginningOfCombat)
        alice.passes()
        bob.casts("Storm Spark", target = "alice")
        allPass()
        expect { bob.graveyard has "Storm Spark" }
    }

    // CR 117.1: no casting without priority
    @Test
    fun noCastingWithoutPriority() = scenario {
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        assertFailsWith<IllegalArgumentException> { bob.casts("Storm Spark", target = "alice") }
    }

    // CR 702.8a: flash: a creature at instant speed
    @Test
    fun flashCreatureOnTheOpponentsTurn() = scenario {
        bob.battlefield("Forest", "Forest", "Forest")
        bob.hand("Persistence")
        alice.passes()
        bob.casts("Persistence")
        expect { stack.names shouldBe listOf("Persistence") }
    }

    // CR 307.1: a creature without flash waits for its own main phase
    @Test
    fun creatureWithoutFlashNotOnTheOpponentsTurn() = scenario {
        bob.battlefield("Forest")
        bob.hand("Elvish Seer")
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.casts("Elvish Seer") }
    }

    // CR 601.2c: the targets decision lists every legal target, players included
    @Test
    fun targetsAreOffered() = scenario {
        alice.battlefield("Mountain")
        bob.battlefield("Elvish Seer")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark")
        val d = assertIs<Decision.Targets>(pending)
        assertEquals(setOf(TargetRef.Player(alice.id), TargetRef.Player(bob.id), TargetRef.Object(card("Elvish Seer"))), d.options.toSet())
        assertEquals(1, d.min)
        assertEquals(1, d.max)
        assertEquals("any", d.slot)
    }

    // CR 601.2c, 115.1: a target that doesn't match is refused
    @Test
    fun illegalTargetRefused() = scenario {
        alice.battlefield("Mountain", "Forest")
        alice.hand("Grindstone Rasp")
        alice.battlefield("Lotus Frond")
        bob.battlefield("Thicketwood Elves")
        alice.casts("Grindstone Rasp", modes = listOf(0))
        assertIs<Decision.Targets>(pending)
        assertFailsWith<IllegalArgumentException> { answer(Answer.Targets(listOf(target("Lotus Frond")))) }
    }

    // CR 702.11b: hexproof: not a legal target for an opponent's spell
    @Test
    fun hexproofIsntOffered() = scenario {
        arcanum.engine.cards.Cards.invent("Veiled Stalker", "{1}{G}", "Creature — Beast", "Hexproof", "2/2")
        alice.battlefield("Mountain")
        bob.battlefield("Veiled Stalker")
        alice.battlefield("Elvish Seer")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark")
        val d = assertIs<Decision.Targets>(pending)
        assertTrue(TargetRef.Object(card("Veiled Stalker")) !in d.options)
        assertTrue(TargetRef.Object(card("Elvish Seer")) in d.options)
    }

    // CR 702.11b: hexproof doesn't stop its controller's own spells
    @Test
    fun hexproofAllowsItsController() = scenario {
        arcanum.engine.cards.Cards.invent("Veiled Stalker", "{1}{G}", "Creature — Beast", "Hexproof", "2/2")
        alice.battlefield("Mountain", "Veiled Stalker")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark")
        assertTrue(TargetRef.Object(card("Veiled Stalker")) in assertIs<Decision.Targets>(pending).options)
    }

    // CR 702.18a: shroud: nobody's spells
    @Test
    fun shroudIsntOfferedToAnyone() = scenario {
        arcanum.engine.cards.Cards.invent("Shrouded Hulk", "{3}{G}", "Creature — Beast", "Shroud", "4/4")
        alice.battlefield("Mountain", "Shrouded Hulk")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark")
        assertTrue(TargetRef.Object(card("Shrouded Hulk")) !in assertIs<Decision.Targets>(pending).options)
    }

    // CR 702.16b: protection from a colour: not a target of spells of that colour
    @Test
    fun protectionFromBlueStopsBlueSpells() = scenario {
        alice.battlefield("Island", "Island")
        bob.battlefield("Glade Sprite Ranger", "Thicketwood Elves")
        alice.hand("Click Back")
        alice.casts("Click Back")
        assertTrue(TargetRef.Object(card("Glade Sprite Ranger")) !in assertIs<Decision.Targets>(pending).options)
    }

    // CR 115.5: a spell can't target itself
    @Test
    fun aSpellDoesntTargetItself() = scenario {
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        bob.battlefield("Island", "Island")
        bob.hand("Denial")
        alice.casts("Elvish Seer")
        alice.passes()
        bob.casts("Denial")
        assertEquals(listOf(TargetRef.Object(card("Elvish Seer"))), assertIs<Decision.Targets>(pending).options)
    }

    // CR 601.2c, 601.2e: with no legal target, a spell that needs one can't be cast: refused, nothing changes
    @Test
    fun noLegalTargetNoSpell() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Denial")
        assertFailsWith<IllegalArgumentException> { alice.casts("Denial") }
        expect { alice.hand has "Denial" }
    }

    // CR 601.2c, 700.2: a mode without legal targets can't be chosen
    @Test
    fun modeWithoutTargetsRefused() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Grindstone Rasp")
        alice.casts("Grindstone Rasp")
        assertFailsWith<IllegalArgumentException> { answer(Answer.Modes(listOf(1))) }
        assertIs<Decision.Modes>(pending)
    }

    // CR 601.2c: counter target spell: the spells on the Stack are the options
    @Test
    fun counterspellTargetsASpell() = scenario {
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        bob.battlefield("Island", "Island")
        bob.hand("Denial")
        alice.casts("Elvish Seer")
        alice.passes()
        bob.casts("Denial", target = "Elvish Seer")
        val item = state.stackItems[card("Denial")]!!
        assertEquals(listOf(TargetRef.Object(card("Elvish Seer"))), item.targets["spell"])
    }

    // CR 601.2 (D58): cancelling rewinds the whole cast: the card is back, nothing was paid
    @Test
    fun cancelRewindsTheCast() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        perform(Input.Cast(alice.id, card("Storm Spark")))
        assertIs<Decision.Targets>(pending)
        answer(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        assertIs<Decision.ManaPayment>(pending)
        perform(Input.Cancel)
        assertNull(pending)
        expect {
            alice.hand has "Storm Spark"
            stack.isEmpty()
            "Mountain".tapped shouldBe false
        }
        assertEquals(alice.id, state.priority.holder)
    }

    // D58: while a cast is open, nothing else is accepted
    @Test
    fun anOpenCastBlocksOtherInputs() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        perform(Input.Cast(alice.id, card("Storm Spark")))
        assertFailsWith<IllegalArgumentException> { perform(Input.PassPriority(alice.id)) }
    }

    // CR 700.2, 601.2b: modes first, then the chosen mode's targets
    @Test
    fun modalSpellChoosesAMode() = scenario {
        alice.battlefield("Mountain", "Mountain")
        bob.battlefield("Elvish Seer")
        alice.hand("Grindstone Rasp")
        alice.casts("Grindstone Rasp")
        val d = assertIs<Decision.Modes>(pending)
        assertEquals(1, d.min)
        assertEquals(1, d.max)
        answer(Answer.Modes(listOf(0)))
        val t = assertIs<Decision.Targets>(pending)
        assertEquals("creature", t.slot)
        assertEquals(listOf(TargetRef.Object(card("Elvish Seer"))), t.options)
    }

    // CR 700.2: the number of modes is enforced
    @Test
    fun tooManyModesRefused() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Grindstone Rasp")
        alice.casts("Grindstone Rasp")
        assertFailsWith<IllegalArgumentException> { answer(Answer.Modes(listOf(0, 1))) }
    }

    // CR 700.2d: "you may choose the same mode more than once"
    @Test
    fun repeatableModes() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Blazing Confluence")
        alice.casts("Blazing Confluence", modes = listOf(1, 1, 1))
        val item = state.stackItems[card("Blazing Confluence")]!!
        assertEquals(listOf(1, 1, 1), item.modes)
    }

    // CR 700.2: a mode can't be chosen twice unless the spell says so
    @Test
    fun repeatedModeRefused() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain")
        alice.hand("Spellforge Command")
        alice.casts("Spellforge Command")
        assertFailsWith<IllegalArgumentException> { answer(Answer.Modes(listOf(1, 1))) }
    }

    // CR 700.2, 601.2c: two chosen modes with a slot of the same name keep their targets apart
    @Test
    fun twoModesWithTheSameSlotName() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain")
        alice.hand("Spellforge Command")
        alice.casts("Spellforge Command", modes = listOf(1, 2), targets = listOf("bob", "alice"))
        val item = state.stackItems[card("Spellforge Command")]!!
        assertEquals(listOf(TargetRef.Player(bob.id)), item.targets["player"])
        assertEquals(listOf(TargetRef.Player(alice.id)), item.targets["player@1"])
    }

    // CR 107.3, 601.2b, 601.2f: X is announced and becomes part of the cost
    @Test
    fun xIsAnnouncedAndPaid() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Doomfire")
        alice.casts("Doomfire", x = 3, target = "bob")
        val item = state.stackItems[card("Doomfire")]!!
        assertEquals(3, item.announced["X"])
        assertEquals(3, state.objOrThrow(card("Doomfire")).facts.x)
        expect { listOf(0, 1, 2, 3).all { card("Mountain", it).tapped } shouldBe true }
    }

    // CR 107.3: {X}{X} costs twice X
    @Test
    fun doubleXCostsTwice() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand("Vermin Outbreak")
        perform(Input.Cast(alice.id, card("Vermin Outbreak")))
        answer(Answer.Number(2))
        // No artifacts or enchantments: up to X targets, none chosen
        val d = assertIs<Decision.ManaPayment>(pending)
        assertEquals("{4}{G}", d.cost.toString())
        answer(Answer.ManaPayment(d.suggestion))
        expect { (0..5).count { card("Forest", it).tapped } shouldBe 5 }
    }

    // CR 601.2c: "up to X targets": the number of targets depends on X
    @Test
    fun upToXTargets() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        bob.battlefield("Lotus Frond", "Verdant Bauble", "Onyx Bauble")
        alice.hand("Vermin Outbreak")
        alice.casts("Vermin Outbreak", x = 1)
        val d = assertIs<Decision.Targets>(pending)
        assertEquals(0, d.min)
        assertEquals(1, d.max)
        assertFailsWith<IllegalArgumentException> { answer(Answer.Targets(listOf(target("Lotus Frond"), target("Verdant Bauble")))) }
    }

    // CR 601.2e, 115.3: the same object can't be chosen twice for one slot
    @Test
    fun sameTargetTwiceRefused() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        bob.battlefield("Lotus Frond")
        alice.hand("Vermin Outbreak")
        alice.casts("Vermin Outbreak", x = 2)
        assertFailsWith<IllegalArgumentException> { answer(Answer.Targets(listOf(target("Lotus Frond"), target("Lotus Frond")))) }
    }

    // CR 305.1, 305.2: one land a turn, in a main phase
    @Test
    fun oneLandPerTurn() = scenario {
        alice.hand("Forest", "Island")
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
        assertFailsWith<IllegalArgumentException> { alice.plays("Island") }
        assertEquals(1, state.turn.landsPlayed[alice.id])
        // CR 116.2a, 117.3c: a special action; the player keeps priority
        assertEquals(alice.id, state.priority.holder)
    }

    // CR 305.2: extra land plays from an effect
    @Test
    fun extraLandPlays() = scenario {
        effect(alice, arcanum.api.Mechanic.ExtraLandPlays(1), affects = arcanum.api.Affected.Players(arcanum.api.Players.You),
            duration = arcanum.api.Duration.EndOfTurn)
        alice.hand("Forest", "Island", "Swamp")
        alice.plays("Forest")
        alice.plays("Island")
        assertFailsWith<IllegalArgumentException> { alice.plays("Swamp") }
    }

    // CR 305.3: not on another player's turn
    @Test
    fun noLandOnTheOpponentsTurn() = scenario {
        bob.hand("Forest")
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.plays("Forest") }
    }

    // CR 305.1: not while the Stack has something on it
    @Test
    fun noLandWithASpellOnTheStack() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Forest", "Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        assertFailsWith<IllegalArgumentException> { alice.plays("Forest") }
    }

    // CR 305.1: the land count starts again next turn
    @Test
    fun landPlaysResetEachTurn() = scenario {
        alice.hand("Forest")
        bob.hand("Island")
        bob.library("Swamp")
        alice.plays("Forest")
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.plays("Island")
        expect { bob.battlefield has "Island" }
    }

    // CR 305.9: a land isn't cast
    @Test
    fun landsArentCast() = scenario {
        alice.hand("Forest")
        assertFailsWith<IllegalArgumentException> { alice.casts("Forest") }
    }

    // CR 712.11, 305.1: a modal double-faced card's land face is played as a land
    @Test
    fun playingTheBackFaceLand() = scenario {
        alice.hand("Summit Ascetic // Hermit Peak")
        alice.plays("Summit Ascetic // Hermit Peak", face = 1)
        expect {
            card("Hermit Peak").types shouldBe listOf(CardType.Land)
            alice.battlefield has "Summit Ascetic // Hermit Peak"
        }
    }

    // CR 305.9, 712.11: its front face isn't a land
    @Test
    fun frontFaceIsntALand() = scenario {
        alice.hand("Summit Ascetic // Hermit Peak")
        assertFailsWith<IllegalArgumentException> { alice.plays("Summit Ascetic // Hermit Peak") }
    }

    // CR 712.11: the front face is cast as a spell
    @Test
    fun castingTheFrontFace() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Summit Ascetic // Hermit Peak")
        alice.casts("Summit Ascetic // Hermit Peak")
        expect { stack.size shouldBe 1 }
    }

    // CR 709.3: a split card is cast as one of its halves, with that half's cost
    @Test
    fun castingASplitHalf() = scenario {
        alice.battlefield("Island", "Island", "Mountain", "Mountain", "Mountain")
        alice.hand("Expanse // Eruption")
        alice.casts("Expanse // Eruption", way = arcanum.engine.input.CastWay(face = 1), x = 1, targets = listOf("bob", "alice"))
        val item = state.stackItems[card("Expanse // Eruption")]!!
        assertEquals(1, item.announced["X"])
        assertEquals(1, state.objOrThrow(card("Expanse // Eruption")).face)
        expect { (0..2).all { card("Mountain", it).tapped } shouldBe true }
    }

    // CR 715.3: an adventurer card's Adventure is cast as that face
    @Test
    fun castingTheAdventure() = scenario {
        alice.battlefield("Island", "Island")
        bob.battlefield("Lotus Frond")
        alice.hand("Brash Pilferer")
        alice.casts("Brash Pilferer", way = arcanum.engine.input.CastWay(face = 1), target = "Lotus Frond")
        expect { card("Brash Pilferer").types shouldBe listOf(CardType.Instant) }
        assertEquals(1, state.objOrThrow(card("Brash Pilferer")).face)
    }

    // CR 601.2d: a divided amount with a single target all goes to it
    @Test
    fun dividedAmountToASingleTarget() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Wildfire Spread")
        alice.casts("Wildfire Spread", x = 1, target = "bob")
        allPass()
        expect { bob.life shouldBe 19 }
    }

    // CR 601.3: "can't cast noncreature spells" is checked on the card as the spell it would be: a noncreature card
    // can't be cast, a creature card can
    @Test
    fun cantCastNoncreatureSpells() = scenario {
        alice.battlefield("Ranger-Marshal of Dawn")
        bob.battlefield("Mountain", "Forest")
        bob.hand("Storm Spark", "Elvish Seer")
        turn(number = 2, active = bob)
        alice.activatesWithoutCosts("Ranger-Marshal of Dawn")
        resolve()
        assertFailsWith<IllegalArgumentException> { bob.casts("Storm Spark", target = "alice") }
        bob.casts("Elvish Seer", paying = listOf("Forest"))
        expect { stack.names shouldBe listOf("Elvish Seer") }
    }
}
