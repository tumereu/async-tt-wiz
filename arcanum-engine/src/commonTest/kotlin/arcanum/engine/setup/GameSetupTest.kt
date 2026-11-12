package arcanum.engine.setup

import arcanum.api.Ability
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.query.Evaluator
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Starting a game (CR 103): decks, the starting player, companions, shuffles, opening hands, mulligans, turn 1. */
class GameSetupTest {
    /** Two invented 40-card decks: Swamps and Undead Soothsayers, Mountains and Glint-Tusk Corsairs. */
    private val swamps = cards(listOf("Swamp", "Swamp", "Undead Soothsayer", "Undead Soothsayer", "Swamp", "Undead Soothsayer", "Swamp"),
        "Swamp", "Undead Soothsayer")
    private val mountains = cards(listOf("Mountain", "Glint-Tusk Corsair", "Mountain", "Mountain", "Glint-Tusk Corsair", "Mountain", "Mountain"),
        "Mountain", "Glint-Tusk Corsair")

    /** Every event of the scenario that is a [GameEvent] of type [T]. */
    private inline fun <reified T : GameEvent> arcanum.engine.scenario.Scenario.events(): List<T> =
        applied.filterIsInstance<Change.Happened>().map { it.event }.filterIsInstance<T>()

    // CR 103.3, 103.4, 103.2a
    @Test
    fun aNewGameHasDecksAsLibrariesAndSideboardsOutside() = scenario(start = table(swamps, mountains, sideboards = mapOf(0 to listOf("Thorough Study")))) {
        assertEquals(40, state.player(alice.id).library.size)
        assertEquals(listOf<String?>("Thorough Study"), outsideNames(alice))
        assertEquals(20, state.player(bob.id).life)
        assertEquals(20, state.player(bob.id).startingLife)
        assertTrue(!state.started)
        assertNull(state.priority.holder)
        // Before the game only starting it is possible
        assertEquals(listOf<Input>(Input.StartGame()), Engine.options(state, alice.id, prefs))
        assertEquals(listOf<Input>(Input.StartGame()), Engine.options(state, bob.id, prefs))
    }

    // CR 103.4: the starting life total is a setting of the game
    @Test
    fun startingLifeIsAParameter() = scenario(start = table(swamps, mountains, life = 30)) {
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        keep(bob)
        expect {
            alice.life shouldBe 30
            bob.life shouldBe 30
        }
        assertEquals(30, state.player(alice.id).startingLife)
    }

    // CR 103.1: the players determine who chooses; that player chooses the starting player
    @Test
    fun aRandomResultPicksWhoChoosesTheStartingPlayer() = scenario(start = table(swamps, mountains)) {
        random(Answer.DieRolls(listOf(2)))
        startGame()
        val choice = decision<Decision.ChoosePlayer>()
        assertEquals(bob.id, choice.player)
        assertEquals(listOf(alice.id, bob.id), choice.options)
        answer(Answer.Player(alice.id))
        assertEquals(GameEvent.StartingPlayer(alice.id, bob.id), events<GameEvent.StartingPlayer>().single())
        assertEquals(alice.id, state.turn.active)
    }

    // CR 103.1: the chooser may choose themselves or another player
    @Test
    fun theChooserMayLetTheOtherPlayerStart() = scenario(start = table(swamps, mountains)) {
        random(Answer.DieRolls(listOf(1)))
        startGame()
        assertEquals(alice.id, decision<Decision.ChoosePlayer>().player)
        answer(Answer.Player(bob.id))
        // CR 103.5: the starting player declares first
        assertEquals(bob.id, decision<Decision.ChooseOption>().player)
    }

    // CR 103.1: in a match, the loser of the previous game chooses
    @Test
    fun aGivenChooserChoosesWithoutARandomResult() = scenario(start = table(swamps, mountains)) {
        startGame(StartOptions(chooser = bob.id))
        assertEquals(bob.id, decision<Decision.ChoosePlayer>().player)
        answer(Answer.Player(bob.id))
        assertEquals(GameEvent.StartingPlayer(bob.id, bob.id), events<GameEvent.StartingPlayer>().single())
    }

    // CR 103.1
    @Test
    fun aGivenStartingPlayerNeedsNoChoice() = scenario(start = table(swamps, mountains)) {
        startGame(StartOptions(startingPlayer = bob.id))
        assertEquals(GameEvent.StartingPlayer(bob.id, null), events<GameEvent.StartingPlayer>().single())
        assertEquals(bob.id, decision<Decision.ChooseOption>().player)
    }

    // CR 103.3, 103.5, 103.8, 117.3a
    @Test
    fun aFullStartEndsInTheFirstUpkeepWithPriority() = scenario(start = table(swamps, mountains)) {
        random(Answer.DieRolls(listOf(1)))
        random(inOrder, inOrder)
        startGame()
        answer(Answer.Player(alice.id))
        keep(alice)
        keep(bob)
        assertTrue(state.started)
        assertEquals(1, state.turn.number)
        assertEquals(alice.id, state.turn.active)
        assertEquals(TurnStep.Upkeep, state.turn.step)
        assertEquals(alice.id, state.priority.holder)
        assertNull(pending)
        expect {
            alice.hand.size shouldBe 7
            bob.hand.size shouldBe 7
            alice.library.size shouldBe 33
            bob.library.size shouldBe 33
        }
        assertEquals(swamps.take(7), handNames(alice))
        assertEquals(listOf(GameEvent.Kept(alice.id, 7), GameEvent.Kept(bob.id, 7)), events<GameEvent.Kept>())
    }

    // CR 103.8a: in a two-player game the starting player skips the draw of their first turn
    @Test
    fun theStartingPlayerSkipsTheirFirstDraw() = scenario(start = table(swamps, mountains)) {
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        keep(bob)
        playUntil(TurnStep.PrecombatMain)
        expect { alice.hand.size shouldBe 7 }
        playUntil(TurnStep.PrecombatMain, turn = 2)
        expect { bob.hand.size shouldBe 8 }
    }

    // CR 103.3: the host shuffles each deck (D25)
    @Test
    fun theHostShufflesEachDeck() = scenario(start = table(swamps, mountains)) {
        random(cutting(33), inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        // Alice's library was cut so its last seven cards came on top
        assertEquals(swamps.takeLast(7), handNames(alice))
        assertEquals(mountains.take(7), handNames(bob))
    }

    // CR 103.5: each player draws their starting hand; the starting hand size can be another number
    @Test
    fun theStartingHandSizeIsAnOption() = scenario(start = table(swamps, mountains)) {
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id, handSize = 5))
        keep(alice)
        keep(bob)
        expect {
            alice.hand.size shouldBe 5
            bob.hand.size shouldBe 5
        }
    }

    // CR 103.5: the starting player declares first, then the others in turn order
    @Test
    fun declarationsGoInTurnOrderFromTheStartingPlayer() = scenario(start = table(swamps, mountains)) {
        startGame(StartOptions(startingPlayer = bob.id))
        assertEquals(bob.id, decision<Decision.ChooseOption>().player)
        assertEquals(listOf(Setup.KEEP, Setup.MULLIGAN), decision<Decision.ChooseOption>().options)
        keep(bob)
        assertEquals(alice.id, decision<Decision.ChooseOption>().player)
    }

    // CR 103.5: a mulligan shuffles the hand back, draws seven and puts one on the bottom
    @Test
    fun aMulliganToSixPutsOneCardOnTheBottom() = scenario(start = table(swamps, mountains)) {
        random(inOrder, inOrder, cutting(7))
        startGame(StartOptions(startingPlayer = alice.id))
        mulligan(alice)
        keep(bob)
        // The new hand is the next seven cards of the deck
        assertEquals(swamps.drop(7).take(7), handNames(alice))
        assertEquals(1, decision<Decision.ChooseObjects>().min)
        bottom(alice, "Undead Soothsayer")
        keep(alice)
        expect {
            alice.hand.size shouldBe 6
            alice.library.size shouldBe 34
            bob.hand.size shouldBe 7
        }
        assertEquals("Undead Soothsayer", libraryNames(alice).last())
        assertEquals(listOf(GameEvent.Mulliganed(alice.id, 1)), events<GameEvent.Mulliganed>())
        assertEquals(listOf(GameEvent.Kept(bob.id, 7), GameEvent.Kept(alice.id, 6)), events<GameEvent.Kept>())
        assertEquals(TurnStep.Upkeep, state.turn.step)
    }

    // CR 103.5: each further mulligan puts one more card on the bottom
    @Test
    fun aSecondMulliganPutsTwoCardsOnTheBottom() = scenario(start = table(swamps, mountains)) {
        random(inOrder, inOrder, cutting(7), cutting(7))
        startGame(StartOptions(startingPlayer = alice.id))
        mulligan(alice)
        keep(bob)
        bottom(alice, "Swamp")
        mulligan(alice)
        assertEquals(2, decision<Decision.ChooseObjects>().min)
        bottom(alice, "Swamp", "Undead Soothsayer")
        keep(alice)
        expect {
            alice.hand.size shouldBe 5
            alice.library.size shouldBe 35
        }
        // In the order chosen: the last chosen is lowest
        assertEquals(listOf<String?>("Swamp", "Undead Soothsayer"), libraryNames(alice).takeLast(2))
        assertEquals(listOf(GameEvent.Mulliganed(alice.id, 1), GameEvent.Mulliganed(alice.id, 2)), events<GameEvent.Mulliganed>())
    }

    // CR 103.5: a player who kept isn't asked again
    @Test
    fun aPlayerWhoKeptIsNotAskedAgain() = scenario(start = table(swamps, mountains)) {
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        mulligan(bob)
        bottom(bob, handNames(bob).first()!!)
        // Only Bob declares in the second round
        assertEquals(bob.id, decision<Decision.ChooseOption>().player)
        keep(bob)
        assertTrue(state.started)
    }

    // CR 103.5: everyone who declared a mulligan takes it at the same time
    @Test
    fun bothPlayersMulliganAtTheSameTime() = scenario(start = table(swamps, mountains)) {
        random(inOrder, inOrder, cutting(7), cutting(7))
        startGame(StartOptions(startingPlayer = alice.id))
        mulligan(alice)
        // Nothing happens before Bob has declared too
        expect { alice.hand.size shouldBe 7 }
        assertEquals(swamps.take(7), handNames(alice))
        mulligan(bob)
        // Both new hands are drawn before anyone puts cards on the bottom
        assertEquals(swamps.drop(7).take(7), handNames(alice))
        assertEquals(mountains.drop(7).take(7), handNames(bob))
        bottom(alice, "Swamp")
        bottom(bob, "Mountain")
        keep(alice)
        keep(bob)
        expect {
            alice.hand.size shouldBe 6
            bob.hand.size shouldBe 6
        }
    }

    // CR 103.5: mulligans can go on until the hand would be zero cards; then the player keeps
    @Test
    fun aHandOfZeroCardsIsKept() = scenario(start = table(swamps, mountains)) {
        startGame(StartOptions(startingPlayer = alice.id, handSize = 2))
        mulligan(alice)
        keep(bob)
        bottom(alice, handNames(alice).first()!!)
        mulligan(alice)
        // Both cards go to the bottom without a choice
        assertTrue(state.started)
        expect { alice.hand.size shouldBe 0 }
        assertEquals(listOf(GameEvent.Kept(bob.id, 2), GameEvent.Kept(alice.id, 0)), events<GameEvent.Kept>())
    }

    // CR 103.5c: in a multiplayer game the first mulligan is free
    @Test
    fun theFirstMulliganIsFreeInMultiplayer() = scenario(players = 3, start = table(swamps, mountains, swamps)) {
        startGame(StartOptions(startingPlayer = alice.id))
        mulligan(alice)
        keep(bob)
        keep(carol)
        // No card to put on the bottom
        assertEquals(alice.id, decision<Decision.ChooseOption>().player)
        expect { alice.hand.size shouldBe 7 }
        mulligan(alice)
        bottom(alice, handNames(alice).first()!!)
        keep(alice)
        expect { alice.hand.size shouldBe 6 }
    }

    // CR 103.5c: the free first mulligan can be chosen for two players too (Brawl)
    @Test
    fun theFreeFirstMulliganIsAnOption() = scenario(start = table(swamps, mountains)) {
        startGame(StartOptions(startingPlayer = alice.id, freeFirstMulligan = true))
        mulligan(alice)
        keep(bob)
        keep(alice)
        expect { alice.hand.size shouldBe 7 }
    }

    // CR 103.1, 103.5, 103.8c: three players: turn order from the starting player, nobody skips the first draw
    @Test
    fun aThreePlayerGameStartsInTurnOrderFromTheStartingPlayer() = scenario(players = 3, start = table(swamps, mountains, swamps)) {
        random(Answer.DieRolls(listOf(3)))
        random(inOrder, inOrder, inOrder)
        startGame()
        assertEquals(carol.id, decision<Decision.ChoosePlayer>().player)
        answer(Answer.Player(bob.id))
        keep(bob)
        keep(carol)
        keep(alice)
        assertEquals(bob.id, state.turn.active)
        assertEquals(bob.id, state.priority.holder)
        playUntil(TurnStep.PrecombatMain)
        expect { bob.hand.size shouldBe 8 }
        playUntil(TurnStep.Upkeep, turn = 2)
        assertEquals(carol.id, state.turn.active)
        playUntil(TurnStep.Upkeep, turn = 3)
        assertEquals(alice.id, state.turn.active)
    }

    // ---- Companions (CR 103.2b, 702.139)

    /** Alice's deck meets Velmora's condition: every permanent card has mana value 2 or less (lands too). */
    private val lowCurve = cards(listOf("Plains", "Swamp", "Ghoulish Reveler", "Plains", "Swamp", "Thorough Study", "Plains"),
        "Swamp", "Ghoulish Reveler")
    private val velmora = mapOf(0 to listOf("Velmora of the Sleep-Den"))

    // CR 103.2b, 702.139a
    @Test
    fun aCompanionIsRevealedBeforeTheGame() = scenario(start = table(lowCurve, mountains, sideboards = velmora)) {
        startGame(StartOptions(startingPlayer = alice.id))
        val reveal = decision<Decision.ChooseObjects>()
        assertEquals(alice.id, reveal.player)
        assertEquals(0, reveal.min)
        assertEquals(1, reveal.max)
        answer(Answer.Objects(reveal.options))
        val companion = state.player(alice.id).outside.single()
        assertTrue(arcanum.engine.state.ObjectStatus.RevealedCompanion in state.objOrThrow(companion).statuses)
        assertEquals(GameEvent.Revealed(alice.id, listOf(companion)), events<GameEvent.Revealed>().single())
        keep(alice)
        keep(bob)
        // CR 103.2b: the revealed card stays outside the game
        assertEquals(listOf<String?>("Velmora of the Sleep-Den"), outsideNames(alice))
    }

    // CR 702.139a, 116.2g: once during the game, in a main phase of your turn with the Stack empty, pay {3} to put it
    // into your hand
    @Test
    fun theRevealedCompanionIsPutIntoHandForThree() = scenario(start = table(lowCurve, cards(emptyList(), "Mountain", "Mountain"), sideboards = velmora)) {
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        answer(Answer.Objects(decision<Decision.ChooseObjects>().options))
        keep(alice)
        keep(bob)
        val companion = state.player(alice.id).outside.single()
        fun action() = Engine.options(state, alice.id, prefs).filterIsInstance<Input.Activate>().firstOrNull { it.source == companion }
        // Not in the upkeep (only in a main phase)
        assertNull(action())
        repeat(4) { playTurn(cast = false) }
        playUntil(TurnStep.PrecombatMain)
        // Turn 5: a third land
        perform(Engine.options(state, alice.id, prefs).first { it is Input.PlayLand })
        perform(assertNotNull(action()))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect { alice.hand has "Velmora of the Sleep-Den" }
        assertTrue(outsideNames(alice).isEmpty())
        assertEquals(3, state.battlefield.count { state.objOrThrow(it).tapped })
    }

    // CR 702.139a: the companion can be put into the hand only if it was revealed
    @Test
    fun aCompanionNotRevealedStaysOutside() = scenario(start = table(lowCurve, cards(emptyList(), "Mountain", "Mountain"), sideboards = velmora)) {
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        answer(Answer.Objects(emptyList()))
        keep(alice)
        keep(bob)
        repeat(4) { playTurn(cast = false) }
        playUntil(TurnStep.PrecombatMain)
        perform(Engine.options(state, alice.id, prefs).first { it is Input.PlayLand })
        val companion = state.player(alice.id).outside.single()
        assertTrue(arcanum.engine.state.ObjectStatus.RevealedCompanion !in state.objOrThrow(companion).statuses)
        assertTrue(Engine.options(state, alice.id, prefs).none { it is Input.Activate && it.source == companion })
        assertTrue(events<GameEvent.Revealed>().isEmpty())
    }

    // CR 103.2b, 702.139a: a deck that doesn't meet the condition can't reveal the companion
    @Test
    fun aCompanionWhoseConditionFailsIsNotOffered() = scenario(
        start = table(lowCurve.dropLast(1) + "Ranger-Marshal of Dawn", mountains, sideboards = velmora),
    ) {
        startGame(StartOptions(startingPlayer = alice.id))
        // The first decision is already the mulligan declaration
        assertEquals(alice.id, decision<Decision.ChooseOption>().player)
        assertTrue(arcanum.engine.state.ObjectStatus.RevealedCompanion !in state.objOrThrow(state.player(alice.id).outside.single()).statuses)
    }

    // CR 103.2b, I-D18: a player asked even when nothing could be chosen (Skip when I have no choice off) is asked about companions
    // only when a card of theirs outside the game is one (a condition that fails stays their secret); with none (a sideboard of
    // other cards) the first decision is the mulligan declaration
    @Test
    fun noCompanionQuestionWithoutACompanion() = scenario(start = table(swamps, mountains, sideboards = mapOf(0 to listOf("Thorough Study")))) {
        alice.skipWhenNoChoice = false
        startGame(StartOptions(startingPlayer = alice.id))
        assertEquals(alice.id, decision<Decision.ChooseOption>().player)
    }

    // CR 103.2b, I-D18: with a companion whose condition fails, the question is still asked (with nothing to choose)
    @Test
    fun aFailedCompanionIsStillAskedWhenNoChoiceIsntSkipped() = scenario(
        start = table(lowCurve.dropLast(1) + "Ranger-Marshal of Dawn", mountains, sideboards = velmora),
    ) {
        alice.skipWhenNoChoice = false
        startGame(StartOptions(startingPlayer = alice.id))
        val reveal = decision<Decision.ChooseObjects>()
        assertEquals(alice.id, reveal.player)
        assertTrue(reveal.options.isEmpty())
    }

    // CR 103.2a: other sideboard cards stay outside the game and out of the library
    @Test
    fun sideboardCardsStayOutside() = scenario(start = table(swamps, mountains, sideboards = mapOf(1 to listOf("Thorough Study", "Faithless Rummage")))) {
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        keep(bob)
        assertEquals(listOf<String?>("Thorough Study", "Faithless Rummage"), outsideNames(bob))
        assertTrue("Thorough Study" !in libraryNames(bob) && "Thorough Study" !in handNames(bob))
        // Velmora's special action belongs to companions only: nothing outside the game is offered
        assertTrue(Engine.options(state, bob.id, prefs).none { it is Input.Activate })
    }

    // ---- The engine API around the start

    // D56, D58: while a setup decision is open its answers are the options
    @Test
    fun optionsDuringTheStartAreTheAnswers() = scenario(start = table(swamps, mountains)) {
        startGame(StartOptions(startingPlayer = alice.id))
        val id = decision<Decision.ChooseOption>().id
        assertEquals(
            listOf<Input>(Input.Answer(id, Answer.Option(Setup.KEEP)), Input.Answer(id, Answer.Option(Setup.MULLIGAN))),
            Engine.options(state, bob.id, prefs),
        )
    }

    // D58: the host stores the state with the open setup decision as JSON and passes it back
    @Test
    fun anOpenSetupDecisionSurvivesJson() = scenario(start = table(swamps, mountains)) {
        startGame(StartOptions(startingPlayer = alice.id))
        mulligan(alice)
        mulligan(bob)
        val json = kotlinx.serialization.json.Json.encodeToString(arcanum.engine.state.GameState.serializer(), state)
        assertEquals(state, kotlinx.serialization.json.Json.decodeFromString(arcanum.engine.state.GameState.serializer(), json))
        assertTrue(state.pending?.continuation is arcanum.engine.decision.Continuation.Setup)
    }

    // CR 103: a game starts once
    @Test
    fun aGameStartsOnlyOnce() = scenario(start = table(swamps, mountains)) {
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        keep(bob)
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.StartGame()) }
        assertTrue(Engine.options(state, alice.id, prefs).none { it is Input.StartGame })
    }

    // Velmora as a normal card in hand is a creature spell like any other (CR 702.139c)
    @Test
    fun specialActionIsVelmorasOnlyActivatableAbility() {
        val definition = arcanum.engine.cards.Cards.definition("Velmora of the Sleep-Den")
        assertTrue(definition.faces.single().abilities.any { it is arcanum.api.Keyword.CompanionAbility })
        assertTrue(arcanum.api.KeywordExpansions.expand(definition.faces.single().abilities.filterIsInstance<arcanum.api.Keyword.CompanionAbility>().single())
            .orEmpty().single() is Ability.SpecialAction)
    }
}
