package arcanum.engine.casting

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Cost
import arcanum.api.Effect
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.MarkClear
import arcanum.api.MarkRules
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.foretell.Foretell
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.ObjectId
import arcanum.engine.view.AbilityOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Special actions (CR 116): any instructions run through the step runner as the action is taken, without the Stack; those the rules give an
 * object are offered from one list; a permanent turned face up, by a special action or an effect, loses what the mark table says it loses.
 * Invented cards.
 */
class GeneralSpecialActionsTest {
    private val bear = Cards.invent("Hushed Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val manifester = Cards.invent("Veil Press", "{0}", "Artifact", "{0}: Manifest the top card of your library.")
    private val cloaker = Cards.invent("Shroud Press", "{0}", "Artifact", "{0}: Cloak the top card of your library.")
    /** "Turn target creature face up." */
    private val revealer = aimed("Dawn Word", Effect.TurnFaceUp(Objects.Target("creature")))
    /** "Turn target creature face down." */
    private val hider = aimed("Dusk Word", Effect.TurnFaceDown(Objects.Target("creature")))

    /** "Suspect target creature." */
    private val suspecter = aimed("Noon Word", Effect.Mark(Objects.Target("creature"), ObjectMark.Suspected))

    private fun aimed(name: String, effect: Effect) = Cards.define(name, CardDefinition(Layout.Normal, listOf(FaceDefinition(name, types = listOf(CardType.Instant),
        abilities = listOf(Ability.Spell(Instructions(steps = listOf(Step.Do(effect)), targets = listOf(TargetSlot("creature",
            TargetKind.Objects(ObjectFilter.Type(CardType.Creature)), min = Value.Fixed(1), max = Value.Fixed(1))))))))))

    private fun Scenario.pay() {
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    /** [Dawn Word][revealer] cast at the face-down [id] and resolved. */
    private fun Scenario.turnedUp(id: ObjectId) = cast(revealer, id)

    /** The spell [name] from alice's hand cast at [id], which has no name to aim it by, and resolved. */
    private fun Scenario.cast(name: String, id: ObjectId) {
        val spell = state.player(alice.id).hand.first { state.objOrThrow(it).definition?.faces?.firstOrNull()?.name == name }
        perform(Input.Cast(alice.id, spell, arcanum.engine.input.CastWay()))
        answer(Answer.Targets(listOf(arcanum.engine.state.TargetRef.Object(id))))
        pay()
        allPass()
    }

    private fun Scenario.faceDownOne(): ObjectId = state.battlefield.first { state.objOrThrow(it).faceDown }

    /** An invented permanent with the special action [action], as a card definition can hold one. */
    private fun withAction(name: String, action: Ability.SpecialAction, vararg types: CardType) = Cards.define(name, CardDefinition(Layout.Normal,
        listOf(FaceDefinition(name, types = types.toList().ifEmpty { listOf(CardType.Artifact) }, abilities = listOf(action)))))

    private fun Scenario.actionOf(id: ObjectId): AbilityOrigin = AbilityOrigin.Printed(0, 0).also { printed ->
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().any { it.source == id && it.ability == printed }, "offered")
    }

    // ---- Turning face up: every way, one path, the table saying what goes

    // CR 701.40a: "a manifested permanent for as long as it remains face down", turned up by its own special action (701.40b)
    @Test
    fun aManifestedPermanentTurnedUpBySpecialActionIsNoLongerManifested() = scenario {
        alice.battlefield(manifester, "Forest", "Forest")
        alice.library(bear)
        alice.activates(manifester)
        allPass()
        val down = faceDownOne()
        assertTrue(ObjectMark.Manifested in state.objOrThrow(down).marks)
        alice.takesSpecialAction(bear, "manifested", paying = listOf("Forest", "Forest"))
        val up = state.objOrThrow(down)
        assertFalse(up.faceDown)
        assertFalse(ObjectMark.Manifested in up.marks, "still marked: ${up.marks}")
    }

    // CR 701.40a: an effect turning it up ends it the same way
    @Test
    fun aManifestedPermanentTurnedUpByAnEffectIsNoLongerManifested() = scenario {
        alice.battlefield(manifester)
        alice.hand(revealer)
        alice.library(bear)
        alice.activates(manifester)
        allPass()
        val down = faceDownOne()
        turnedUp(down)
        val up = state.objOrThrow(down)
        assertFalse(up.faceDown)
        assertFalse(ObjectMark.Manifested in up.marks, "still marked: ${up.marks}")
    }

    // CR 701.58a, b: "a cloaked permanent for as long as it remains face down", turned up by its own special action
    @Test
    fun aCloakedPermanentTurnedUpBySpecialActionIsNoLongerCloaked() = scenario {
        alice.battlefield(cloaker, "Forest", "Forest")
        alice.library(bear)
        alice.activates(cloaker)
        allPass()
        val down = faceDownOne()
        assertTrue(ObjectMark.Cloaked in state.objOrThrow(down).marks)
        alice.takesSpecialAction(bear, "manifested", paying = listOf("Forest", "Forest"))
        assertFalse(ObjectMark.Cloaked in state.objOrThrow(down).marks)
    }

    // CR 701.58a: an effect turning it up ends it too
    @Test
    fun aCloakedPermanentTurnedUpByAnEffectIsNoLongerCloaked() = scenario {
        alice.battlefield(cloaker)
        alice.hand(revealer)
        alice.library(bear)
        alice.activates(cloaker)
        allPass()
        val down = faceDownOne()
        turnedUp(down)
        assertFalse(ObjectMark.Cloaked in state.objOrThrow(down).marks)
    }

    // CR 701.58a, 708.2a: a cloaked card turned up and later turned face down isn't cloaked: a plain 2/2 with no text, no ward {2}
    @Test
    fun aCloakedCardTurnedFaceDownAgainHasNoWard() = scenario {
        alice.battlefield(cloaker, "Forest", "Forest")
        alice.hand(hider)
        alice.library(bear)
        alice.activates(cloaker)
        allPass()
        val id = faceDownOne()
        assertTrue(view()[id]!!.characteristics!!.hasKeyword(Keyword.Ward), "cloaked: ward {2}")
        alice.takesSpecialAction(bear, "manifested", paying = listOf("Forest", "Forest"))
        alice.castsWithoutCosts(hider, target = bear)
        resolve()
        assertTrue(state.objOrThrow(id).faceDown)
        assertFalse(view()[id]!!.characteristics!!.hasKeyword(Keyword.Ward), "turned face down again: no ward")
        // CR 701.40b, 701.58b: and it can't be turned up for its mana cost again
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().none { it.source == id && it.ability == AbilityOrigin.Rule("manifested") })
    }

    // The table says which marks go as a permanent is turned face up, and the engine does exactly that: a mark without the rule stays
    // CR 701.40a, 701.60a
    @Test
    fun turningFaceUpTakesOnlyTheMarksTheTableSays() = scenario {
        assertEquals(setOf(ObjectMark.Manifested, ObjectMark.Cloaked),
            listOf(ObjectMark.Manifested, ObjectMark.Cloaked, ObjectMark.Suspected, ObjectMark.Monstrous, ObjectMark.Renowned, ObjectMark.Saddled)
                .filter { MarkClear.TurnedFaceUp in MarkRules.of(it).clears }.toSet())
        alice.battlefield(manifester)
        alice.hand(revealer, suspecter)
        alice.library(bear)
        alice.activates(manifester)
        allPass()
        val down = faceDownOne()
        cast(suspecter, down)
        assertTrue(ObjectMark.Suspected in state.objOrThrow(down).marks)
        turnedUp(down)
        assertEquals(listOf<ObjectMark>(ObjectMark.Suspected), state.objOrThrow(down).marks)
    }

    // ---- Any instructions run through the step runner

    // CR 116.1, 116.3: a special action of any instructions: it doesn't use the Stack, happens at once, and its player has priority afterward
    @Test
    fun aSpecialActionRunsItsStepsAtOnceWithoutTheStack() = scenario {
        val action = Ability.SpecialAction(Cost.mana(ManaCost.parse("{1}")!!), Instructions(steps = listOf(
            Step.Do(Effect.GainLife(Players.You, Value.Fixed(3))),
            Step.Do(Effect.Draw(Players.You, Value.Fixed(1))))),
            timing = Timing.Any, functionsIn = listOf(Zone.Battlefield), text = "Pay {1}: gain 3 life and draw a card")
        val odd = withAction("Odd Almanac", action)
        alice.battlefield(odd, "Forest")
        alice.library("Island")
        val id = card(odd)
        perform(Input.Activate(alice.id, id, actionOf(id)))
        pay()
        assertEquals(null, pending, "nothing by hand")
        expect {
            alice.life shouldBe 23
            alice.hand has "Island"
            stack.isEmpty()
        }
        assertEquals(alice.id, state.priority.holder)
    }

    // CR 116.2: instructions that ask something ask it as they run; the action is finished then, still without the Stack
    @Test
    fun aSpecialActionThatAsksAsksItsPlayer() = scenario {
        val action = Ability.SpecialAction(Cost(emptyList()), Instructions(steps = listOf(
            Step.May(listOf(Step.Do(Effect.GainLife(Players.You, Value.Fixed(3)))), Players.You))),
            timing = Timing.Any, functionsIn = listOf(Zone.Battlefield), text = "You may gain 3 life")
        val odd = withAction("Odd Censer", action)
        alice.battlefield(odd)
        val id = card(odd)
        perform(Input.Activate(alice.id, id, actionOf(id)))
        assertIs<Decision.May>(pending)
        answer(Answer.YesNo(true))
        assertEquals(null, pending)
        expect {
            alice.life shouldBe 23
            stack.isEmpty()
        }
        assertEquals(alice.id, state.priority.holder)
    }

    // CR 116.2: "this" is the object it was taken from, also after a step has moved it (CR 702.170a: "It becomes a plotted card")
    @Test
    fun theActionsObjectIsFollowedAcrossAMove() = scenario {
        val action = Ability.SpecialAction(Cost(emptyList()), Instructions(steps = listOf(
            Step.Do(Effect.Move(Objects.Self, arcanum.api.Destination(Zone.Exile))),
            Step.Do(Effect.Mark(Objects.Self, ObjectMark.Warped)))),
            timing = Timing.Any, functionsIn = listOf(Zone.Hand))
        val odd = withAction("Odd Letter", action, CardType.Sorcery)
        alice.hand(odd)
        val id = card(odd)
        perform(Input.Activate(alice.id, id, actionOf(id)))
        expect { alice.exile has odd }
        assertTrue(ObjectMark.Warped in state.objOrThrow(card(odd)).marks)
    }

    // ---- One list of what the rules give

    // CR 116.2b, h, m: what the rules give an object is offered from one list, each legal one in Engine.options, each found again to be taken
    @Test
    fun whatTheRulesGiveIsOfferedFromOneList() = scenario {
        val room = Cards.inventFaces("Split",
            arcanum.engine.cards.face("Quiet Foyer", "{1}{W}", "Enchantment — Room", "", null, null, null),
            arcanum.engine.cards.face("Quiet Pantry", "{2}{B}", "Enchantment — Room", "", null, null, null))
        val tidings = Cards.invent("Quiet Tidings", "{3}{U}", "Instant", """
            Draw a card.
            Foretell {U} (During your turn, you may pay {2} and exile this card from your hand face down. Cast it on a later turn for its foretell cost.)
        """)
        val morph = Cards.invent("Quiet Mask", "{2}{G}", "Creature — Shapeshifter", "Morph {G}", "2/2")
        alice.battlefield(manifester, room, "Plains", "Swamp", "Forest", "Island", "Island", "Island")
        alice.library(bear)
        alice.hand(tidings)
        val masked = put(alice, listOf(morph), Zone.Battlefield, faceDown = true).single()
        alice.activates(manifester)
        allPass()
        val manifested = state.battlefield.first { state.objOrThrow(it).faceDown && it != masked }
        val offered = Engine.options(state, alice.id).filterIsInstance<Input.Activate>().filter { it.ability is AbilityOrigin.Rule }
        assertEquals(setOf(
            manifested to AbilityOrigin.Rule("manifested"),
            card(room) to AbilityOrigin.Rule("unlock-left"), card(room) to AbilityOrigin.Rule("unlock-right"),
            card(tidings) to Foretell.RULE,
        ), offered.map { it.source to it.ability }.toSet())
        val snap = Snapshot(state)
        offered.forEach { input ->
            assertEquals(input.ability, Legality.instance(snap, input.source, input.ability)?.origin)
            assertEquals(input.ability, SpecialActions.of(snap, input.source).single { it.origin == input.ability }.origin)
        }
        // CR 702.37e, 708.2a: a face-down permanent's card offers its own: its characteristics show no abilities
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().any { it.source == masked && it.ability !is AbilityOrigin.Rule })
    }

    // CR 708.7, 116.2j: off the battlefield only a conspiracy in the command zone is turned face up (HiddenAgendaTest): a card elsewhere isn't
    @Test
    fun anEffectTurnsNothingFaceUpInAHand() = scenario {
        val spell = Cards.invent("Quiet Word", "{U}", "Instant", "Draw a card.")
        val id = put(alice, listOf(spell), Zone.Hand, faceDown = true).single()
        val action = Ability.SpecialAction(Cost(emptyList()), Instructions(steps = listOf(Step.Do(Effect.TurnFaceUp(Objects.Each(ObjectFilter.InZone(Zone.Hand)))))),
            timing = Timing.Any, functionsIn = listOf(Zone.Battlefield))
        val odd = withAction("Odd Lantern", action)
        alice.battlefield(odd)
        val lantern = card(odd)
        perform(Input.Activate(alice.id, lantern, actionOf(lantern)))
        assertTrue(state.objOrThrow(id).faceDown)
    }

    // ---- Nothing is skipped: what used to stop for "by hand"

    private val monitor = Cards.invent("Quiet Monitor", "{0}", "Creature — Golem", "When this creature enters, you may collect evidence 4. If you do, draw a card.", "2/2")
    private val four = Cards.invent("Quiet Four", "{2}{G}{G}", "Creature — Bear", "", "4/4")
    private val one = Cards.invent("Quiet One", "{G}", "Creature — Bear", "", "1/1")
    private val two = Cards.invent("Quiet Two", "{1}{G}", "Creature — Bear", "", "2/2")

    private fun Scenario.toTheChoice() {
        alice.casts(monitor)
        while (pending == null && state.stack.isNotEmpty()) allPass()
    }

    // CR 701.59a: collecting evidence as an ability resolves exiles the cards, and only then does "if you do" follow
    @Test
    fun collectingEvidenceAsItResolvesExilesTheCards() = scenario {
        alice.hand(monitor)
        alice.graveyard(one, four, two)
        alice.library("Forest")
        toTheChoice()
        assertIs<Decision.May>(pending)
        answer(Answer.YesNo(true))
        assertFalse(pending is Decision.Manual, "nothing by hand: $pending")
        answer(Answer.Objects(listOf(card(one))))
        // 1 of 4: one more is needed
        assertEquals(1, decision<Decision.ChooseObjects>().min)
        expect { alice.hand lacks "Forest" }
        answer(Answer.Objects(listOf(card(four))))
        answer(Answer.Objects(emptyList()))
        expect {
            alice.exile has one
            alice.exile has four
            alice.graveyard has two
            alice.hand has "Forest"
        }
    }

    // CR 701.59a: "any number of cards": more than the total asks may be exiled, and needn't be
    @Test
    fun collectingEvidenceMayExileMore() = scenario {
        alice.hand(monitor)
        alice.graveyard(four, one)
        alice.library("Forest")
        toTheChoice()
        answer(Answer.YesNo(true))
        answer(Answer.Objects(listOf(card(four))))
        assertEquals(0, decision<Decision.ChooseObjects>().min)
        answer(Answer.Objects(emptyList()))
        expect {
            alice.exile has four
            alice.graveyard has one
            alice.hand has "Forest"
        }
    }

    // CR 701.59b: a player who can't reach the total can't choose to collect evidence
    @Test
    fun evidenceThatCantBeCollectedIsNotOffered() = scenario {
        alice.hand(monitor)
        alice.graveyard(one)
        alice.library("Forest")
        toTheChoice()
        assertEquals(null, pending)
        expect {
            alice.graveyard has one
            alice.hand lacks "Forest"
        }
    }
}
