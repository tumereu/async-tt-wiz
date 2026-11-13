package arcanum.engine.casting

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.Effect
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.KeywordActionKind
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.MarkClear
import arcanum.api.MarkRules
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.Total
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.CastScreen
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.foretell.Foretell
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.ObjectId
import arcanum.engine.state.TargetRef
import arcanum.engine.view.AbilityOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    // A {0} mana cost: a card with none can't be cast for it (CR 118.6)
    private fun aimed(name: String, effect: Effect) = Cards.define(name, CardDefinition(Layout.Normal, listOf(FaceDefinition(name, ManaCost.parse("{0}"), types = listOf(CardType.Instant),
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
        assertTrue(Engine.options(state, alice.id, prefs).filterIsInstance<Input.Activate>().any { it.source == id && it.ability == printed }, "offered")
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
        assertTrue(Engine.options(state, alice.id, prefs).filterIsInstance<Input.Activate>().none { it.source == id && it.ability == AbilityOrigin.Rule("manifested") })
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
        val offered = Engine.options(state, alice.id, prefs).filterIsInstance<Input.Activate>().filter { it.ability is AbilityOrigin.Rule }
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
        assertTrue(Engine.options(state, alice.id, prefs).filterIsInstance<Input.Activate>().any { it.source == masked && it.ability !is AbilityOrigin.Rule })
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
        // One selection: any number, its total asked of the answer
        val choice = decision<Decision.ChooseObjects>()
        assertEquals(1 to null, choice.min to choice.max)
        assertEquals(Total(NumericProperty.ManaValue, Value.Fixed(4)), choice.total)
        assertEquals(setOf(card(one), card(four), card(two)), choice.options.toSet())
        // 1 of 4 is refused, and nothing has changed
        assertFailsWith<IllegalArgumentException> { answer(Answer.Objects(listOf(card(one)))) }
        expect { alice.hand lacks "Forest" }
        answer(Answer.Objects(listOf(card(one), card(four))))
        assertEquals(null, pending)
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
        answer(Answer.Objects(listOf(card(four), card(one))))
        assertEquals(null, pending)
        expect {
            alice.exile has four
            alice.exile has one
            alice.hand has "Forest"
        }
    }

    // CR 701.59a: enough is enough: one card that reaches the total alone needs no other
    @Test
    fun collectingEvidenceNeedntExileMore() = scenario {
        alice.hand(monitor)
        alice.graveyard(four, one)
        alice.library("Forest")
        toTheChoice()
        answer(Answer.YesNo(true))
        answer(Answer.Objects(listOf(card(four))))
        expect {
            alice.exile has four
            alice.graveyard has one
            alice.hand has "Forest"
        }
    }

    // CR 701.59a, CR 603.2: collecting evidence as it resolves happens once, however many cards go, for "whenever you collect evidence"
    @Test
    fun collectingEvidenceAsItResolvesIsOneEvent() = scenario {
        alice.hand(monitor)
        alice.graveyard(one, two, four)
        alice.library("Forest")
        toTheChoice()
        answer(Answer.YesNo(true))
        answer(Answer.Objects(listOf(card(one), card(two), card(four))))
        val done = applied.filterIsInstance<Change.Happened>().map { it.event }.filterIsInstance<GameEvent.KeywordActionDone>()
            .filter { it.action == KeywordActionKind.CollectEvidence }
        assertEquals(listOf(alice.id), done.map { it.player })
    }

    // CR 701.59a: collecting evidence as a cost is the same choice: its decision carries the total, refused below it
    @Test
    fun evidenceAsACostCarriesItsTotal() = scenario {
        val desk = Cards.invent("Quiet Desk", "{0}", "Artifact", "Collect evidence 4: You gain 3 life.")
        alice.battlefield(desk)
        alice.graveyard(one, four)
        perform(Engine.options(state, alice.id, prefs).filterIsInstance<Input.Activate>().single { it.source == card(desk) })
        val choice = decision<Decision.ChooseObjects>()
        assertEquals(1 to null, choice.min to choice.max)
        assertEquals(Total(NumericProperty.ManaValue, Value.Fixed(4)), choice.total)
        assertFailsWith<IllegalArgumentException> { answer(Answer.Objects(listOf(card(one)))) }
        answer(Answer.Objects(listOf(card(four), card(one))))
        expect {
            alice.exile has four
            alice.exile has one
        }
    }

    // CR 702.122a: so is crew's, with its power
    @Test
    fun crewCarriesItsTotal() = scenario {
        val skiff = Cards.invent("Quiet Skiff", "{0}", "Artifact — Vehicle", "Crew 2", "3/3")
        alice.battlefield(skiff, one, two)
        perform(Engine.options(state, alice.id, prefs).filterIsInstance<Input.Activate>().single { it.source == card(skiff) })
        val choice = decision<Decision.ChooseObjects>()
        assertEquals(Total(NumericProperty.PowerFor(Keyword.Crew.Kind), Value.Fixed(2)), choice.total)
        assertFailsWith<IllegalArgumentException> { answer(Answer.Objects(listOf(card(one)))) }
        answer(Answer.Objects(listOf(card(two))))
        assertTrue(state.objOrThrow(card(two)).tapped)
    }

    // CR 602.2a, 702.122a: costs are chosen with the ability already on the Stack: a creature whose power rises while something is there
    // reaches crew's total only then, and its answer is accepted, though the live state has nothing on the Stack
    @Test
    fun aCostsTotalIsReadWithItsAbilityOnTheStack() = scenario {
        val eager = Cards.define("Quiet Eager Hand", CardDefinition(Layout.Normal, listOf(FaceDefinition("Quiet Eager Hand", ManaCost.parse("{G}"),
            types = listOf(CardType.Creature), power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1),
            abilities = listOf(Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(0))), Affected.Objects(Objects.Self),
                condition = Condition.Exists(ObjectFilter.InZone(Zone.Stack), 1)))))))
        val skiff = Cards.invent("Quiet Skiff", "{0}", "Artifact — Vehicle", "Crew 2", "3/3")
        alice.battlefield(skiff, eager)
        expect { eager.power shouldBe 1 }
        perform(Engine.options(state, alice.id, prefs).filterIsInstance<Input.Activate>().single { it.source == card(skiff) })
        assertTrue(CastScreen.selection(state, listOf(TargetRef.Object(card(eager)))).done)
        answer(Answer.Objects(listOf(card(eager))))
        assertTrue(state.objOrThrow(card(eager)).tapped)
        assertEquals(1, state.stack.size, "crew on the Stack")
    }

    // CR 701.59a: the selection is done only from the total on, more cards too; every card stays a candidate
    @Test
    fun selectionIsDoneOnlyFromTheTotalOn() = scenario {
        alice.hand(monitor)
        alice.graveyard(one, two, four)
        toTheChoice()
        answer(Answer.YesNo(true))
        fun done(vararg names: String) = CastScreen.selection(state, names.map { TargetRef.Object(card(it)) }).done
        assertFalse(done())
        assertFalse(done(one))
        assertFalse(done(one, two))
        assertTrue(done(four))
        assertTrue(done(four, one))
        assertTrue(done(one, two, four))
        val all = setOf(one, two, four).map { TargetRef.Object(card(it)) }.toSet()
        assertEquals(all, CastScreen.selection(state, emptyList()).candidates.toSet())
        assertEquals(all, CastScreen.selection(state, listOf(TargetRef.Object(card(four)))).candidates.toSet())
    }

    // CR 701.59a, I-D18: the graveyard's one card is exactly what is needed: still asked, either way the skip is set, and confirmed.
    // Nothing is hidden in a graveyard, so the setting isn't what asks; the choice is the player's
    @Test
    fun exactlyTheGraveyardNeededIsStillAsked() = listOf(true, false).forEach { skip ->
        scenario {
            alice.skipWhenNoChoice = skip
            alice.hand(monitor)
            alice.graveyard(four)
            alice.library("Forest")
            toTheChoice()
            answer(Answer.YesNo(true))
            val choice = decision<Decision.ChooseObjects>()
            assertEquals(listOf(card(four)), choice.options)
            assertTrue(CastScreen.selection(state, listOf(TargetRef.Object(card(four)))).done)
            answer(Answer.Objects(listOf(card(four))))
            expect {
                alice.exile has four
                alice.hand has "Forest"
            }
        }
    }

    // CR 701.59a: a total only all the cards reach: asked, refused short of all, and the engine lists that one working answer
    @Test
    fun aTotalOnlyAllTheCardsReachIsStillAsked() = scenario {
        val three = Cards.invent("Quiet Three", "{2}{G}", "Creature — Bear", "", "3/3")
        alice.hand(monitor)
        alice.graveyard(one, three)
        alice.library("Forest")
        toTheChoice()
        answer(Answer.YesNo(true))
        val choice = decision<Decision.ChooseObjects>()
        val all = setOf(card(one), card(three))
        assertEquals(all, Engine.options(state, alice.id, prefs).filterIsInstance<Input.Answer>().map { (it.answer as Answer.Objects).chosen.toSet() }.single())
        assertFailsWith<IllegalArgumentException> { answer(Answer.Objects(listOf(card(three)))) }
        assertEquals(choice, pending)
        answer(Answer.Objects(listOf(card(three), card(one))))
        expect {
            alice.exile has one
            alice.exile has three
            alice.hand has "Forest"
        }
    }

    // CR 701.59a: the listed answers hold a working one when no single card reaches the total
    @Test
    fun theListedAnswersReachTheTotal() = scenario {
        alice.hand(monitor)
        alice.graveyard(one, two, two, four)
        toTheChoice()
        answer(Answer.YesNo(true))
        val listed = Engine.options(state, alice.id, prefs).filterIsInstance<Input.Answer>().map { (it.answer as Answer.Objects).chosen }
        assertTrue(listed.isNotEmpty())
        val mv = mapOf(card(one) to 1, card(four) to 4).withDefault { 2 }
        assertTrue(listed.all { answer -> answer.sumOf { mv.getValue(it) } >= 4 }, "$listed")
    }

    // CR 701.59b: collecting evidence no choice offers, with no way to reach the total: nothing is exiled, and it hasn't happened
    @Test
    fun evidenceThatCantBeReachedExilesNothing() = scenario {
        val rite = Cards.define("Quiet Rite", CardDefinition(Layout.Normal, listOf(FaceDefinition("Quiet Rite", ManaCost.parse("{0}"),
            types = listOf(CardType.Sorcery), abilities = listOf(Ability.Spell(Instructions(steps = listOf(
                Step.Do(Effect.Action(KeywordAction.CollectEvidence(Value.Fixed(4))))))))))))
        alice.hand(rite)
        alice.graveyard(one)
        alice.casts(rite)
        allPass()
        assertEquals(null, pending)
        expect { alice.graveyard has one }
        assertTrue(applied.filterIsInstance<Change.Happened>().map { it.event }.filterIsInstance<GameEvent.KeywordActionDone>()
            .none { it.action == KeywordActionKind.CollectEvidence })
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
