package arcanum.engine.setup

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.change.GameEvent
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The end of a game (CR 104): winning by damage and by an empty library, conceding, draws, and players leaving a
 * multiplayer game (CR 800.4). */
class GameEndTest {
    private val swamps = cards(listOf("Swamp", "Swamp", "Undead Soothsayer", "Undead Soothsayer", "Swamp", "Undead Soothsayer", "Swamp"),
        "Swamp", "Undead Soothsayer")
    private val mountains = cards(listOf("Mountain", "Glint-Tusk Corsair", "Mountain", "Mountain", "Glint-Tusk Corsair", "Mountain", "Mountain"),
        "Mountain", "Glint-Tusk Corsair")

    /** Once the game is over nobody can do anything (CR 104.1). */
    private fun Scenario.nothingMoreHappens() {
        players.forEach { assertTrue(Engine.options(state, it.id).isEmpty(), "${it.id} still has options") }
        assertNull(state.priority.holder)
        assertNull(pending)
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.PassPriority(players.first().id)) }
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.Concede(players.first().id)) }
    }

    // CR 104.2a, 104.3b, 704.5a: a short game from the start: Alice attacks each turn until Bob is at 0 life
    @Test
    fun aGameIsWonByCombatDamage() = scenario(start = table(swamps, mountains)) {
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        keep(bob)
        var guard = 30
        while (Engine.result(state) == null && guard-- > 0) playTurn(attack = state.turn.active == alice.id)
        assertEquals(GameResult.Won(alice.id), Engine.result(state))
        assertTrue(state.player(bob.id).lost)
        assertTrue(state.player(bob.id).life <= 0)
        // Alice attacked in each of her turns since her first creature came: no turn of hers went by without damage
        val damagedTurns = state.log.filter { it.change is Change.Damage }.map { it.turn }.distinct()
        assertEquals(damagedTurns.indices.map { damagedTurns.first() + 2 * it }, damagedTurns)
        nothingMoreHappens()
    }

    // CR 104.3c, 121.4, 704.5b: drawing from an empty library loses at the next state-based action check
    @Test
    fun aGameIsWonWhenTheOpponentDrawsFromAnEmptyLibrary() = scenario(start = table(List(8) { "Swamp" }, List(8) { "Mountain" })) {
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        keep(bob)
        // Turn 1: Alice skips her draw (CR 103.8a); turn 2: Bob draws his last card; turn 3: Alice draws her last card
        repeat(3) { playTurn() }
        expect {
            alice.library.size shouldBe 0
            bob.library.size shouldBe 0
        }
        assertEquals(null, Engine.result(state))
        // Turn 4: Bob can't draw
        playTurn()
        assertEquals(GameResult.Won(alice.id), Engine.result(state))
        assertEquals(4, state.turn.number)
        assertEquals(TurnStep.Draw, state.turn.step)
        nothingMoreHappens()
    }

    // CR 104.3a, 104.2a: a player who concedes loses; in a two-player game the other player wins at once
    @Test
    fun conceding() = scenario {
        alice.battlefield("Swamp")
        perform(Input.Concede(bob.id))
        assertEquals(GameResult.Won(alice.id), Engine.result(state))
        assertTrue(applied.any { it is Change.Happened && it.event == GameEvent.Conceded(bob.id) })
        // The board stays as it ended
        expect { alice.battlefield has "Swamp" }
        nothingMoreHappens()
    }

    // CR 104.3a: a player can concede at any time, also while a decision of the other player is open
    @Test
    fun concedingWhileTheOpponentDecides() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Undead Soothsayer")
        perform(Input.Cast(alice.id, card("Undead Soothsayer")))
        assertTrue(pending is Decision.ManaPayment)
        perform(Input.Concede(bob.id))
        assertEquals(GameResult.Won(alice.id), Engine.result(state))
        nothingMoreHappens()
    }

    // CR 104.3a: conceding before the game begins
    @Test
    fun concedingBeforeTheGameBegins() = scenario(start = table(swamps, mountains)) {
        startGame(StartOptions(startingPlayer = alice.id))
        perform(Input.Concede(alice.id))
        assertEquals(GameResult.Won(bob.id), Engine.result(state))
        nothingMoreHappens()
    }

    // CR 104.4i, 104.1: the players agree to a draw; the game ends at once
    @Test
    fun anAgreedDraw() = scenario {
        alice.battlefield("Swamp")
        perform(Input.AgreedResult(GameResult.Draw))
        assertEquals(GameResult.Draw, Engine.result(state))
        assertTrue(applied.any { it is Change.Happened && it.event == GameEvent.ResultAgreed(GameResult.Draw) })
        nothingMoreHappens()
    }

    // CR 104.3a, 104.2a: an agreed win: the other player concedes, also while a cast is being announced
    @Test
    fun anAgreedWinWhileACastIsAnnounced() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Undead Soothsayer")
        perform(Input.Cast(alice.id, card("Undead Soothsayer")))
        assertTrue(pending is Decision.ManaPayment)
        perform(Input.AgreedResult(GameResult.Won(bob.id)))
        assertEquals(GameResult.Won(bob.id), Engine.result(state))
        assertTrue(applied.any { it is Change.Happened && it.event == GameEvent.Conceded(alice.id) })
        // The cast was cancelled: the card is back in hand
        expect { alice.hand has "Undead Soothsayer" }
        nothingMoreHappens()
    }

    // CR 104.2a: only a player still in the game can be agreed the winner
    @Test
    fun anAgreedWinnerIsInTheGame() = scenario(players = 3) {
        perform(Input.Concede(carol.id))
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.AgreedResult(GameResult.Won(carol.id))) }
    }

    // CR 104.4a: if all remaining players lose at once, the game is a draw
    @Test
    fun aGameIsADrawWhenEveryoneLosesAtOnce() = scenario {
        change(Changes.life(state, alice.id, -20))
        change(Changes.life(state, bob.id, -20))
        passPriority()
        assertEquals(GameResult.Draw, Engine.result(state))
        nothingMoreHappens()
    }

    // ---- Multiplayer (CR 800.4)

    // CR 104.3a, 800.4a: in a three-player game a player who concedes mid-turn leaves; the game goes on
    @Test
    fun concedingMidTurnInAThreePlayerGame() = scenario(players = 3) {
        alice.battlefield("Swamp")
        bob.battlefield("Undead Soothsayer", "Mountain")
        bob.hand("Plains")
        bob.graveyard("Swamp")
        carol.battlefield("Mountain")
        passPriority()
        assertEquals(bob.id, state.priority.holder)
        perform(Input.Concede(bob.id))
        assertNull(Engine.result(state))
        assertTrue(state.player(bob.id).lost)
        // Everything Bob owned left the game
        expect {
            bob.battlefield.isEmpty()
            bob.hand.isEmpty()
            bob.graveyard.isEmpty()
            alice.battlefield has "Swamp"
            carol.battlefield has "Mountain"
        }
        assertEquals(4, state.player(bob.id).outside.size)
        // CR 800.4a: the priority he held passes to the next player; Alice's pass still counts
        assertEquals(carol.id, state.priority.holder)
        assertEquals(1, state.priority.passes)
        assertTrue(Engine.options(state, bob.id).isEmpty())
        // The turn goes on; the next turn skips Bob (CR 800.4k)
        playUntil(TurnStep.Upkeep, turn = 2)
        assertEquals(carol.id, state.turn.active)
    }

    // CR 800.4j: if the active player leaves, the turn goes on without an active player
    @Test
    fun theActivePlayerConcedes() = scenario(players = 3) {
        alice.battlefield("Undead Soothsayer")
        bob.battlefield("Glint-Tusk Corsair")
        perform(Input.Concede(alice.id))
        assertEquals(bob.id, state.priority.holder)
        assertEquals(alice.id, state.turn.active)
        playUntil(TurnStep.End)
        assertEquals(1, state.turn.number)
        // Nobody declared attackers for Alice
        assertTrue(state.log.none { it.change is Change.Damage })
        playUntil(TurnStep.Upkeep, turn = 2)
        assertEquals(bob.id, state.turn.active)
        assertEquals(bob.id, state.priority.holder)
    }

    // CR 104.3a, 601.2: conceding in the middle of casting a spell cancels the cast first
    @Test
    fun concedingWhileCastingCancelsTheCast() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Undead Soothsayer")
        perform(Input.Cast(alice.id, card("Undead Soothsayer")))
        perform(Input.Concede(alice.id))
        assertNull(pending)
        assertTrue(state.stack.isEmpty())
        assertTrue(state.stackItems.isEmpty())
        assertEquals(3, state.player(alice.id).outside.size)
        assertEquals(bob.id, state.priority.holder)
    }

    // CR 800.4a: a player who loses by a state-based action leaves a game that goes on
    @Test
    fun aPlayerAtZeroLifeLeavesAThreePlayerGame() = scenario(players = 3) {
        alice.battlefield("Undead Soothsayer")
        carol.battlefield("Glint-Tusk Corsair", "Mountain")
        change(Changes.life(state, carol.id, -20))
        passPriority()
        assertTrue(state.player(carol.id).lost)
        assertNull(Engine.result(state))
        expect { carol.battlefield.isEmpty() }
        assertEquals(2, state.player(carol.id).outside.size)
        // Alice passed, Bob has priority; Carol is skipped from now on
        assertEquals(bob.id, state.priority.holder)
        passPriority()
        assertEquals(TurnStep.BeginningOfCombat, state.turn.step)
    }

    // CR 800.4a: objects a leaving player controls but doesn't own are exiled
    @Test
    fun whatALeavingPlayerControlsButDoesNotOwnIsExiled() = scenario(players = 3) {
        bob.battlefield("Undead Soothsayer")
        val stolen = card("Undead Soothsayer")
        state.objects[stolen] = state.objOrThrow(stolen).copy(controller = carol.id)
        passPriority()
        passPriority()
        perform(Input.Concede(carol.id))
        expect { bob.exile has "Undead Soothsayer" }
        assertEquals(alice.id, state.priority.holder)
    }

    // CR 104.2a: the last player left wins
    @Test
    fun theLastPlayerLeftWins() = scenario(players = 3) {
        perform(Input.Concede(bob.id))
        assertNull(Engine.result(state))
        perform(Input.Concede(carol.id))
        assertEquals(GameResult.Won(alice.id), Engine.result(state))
        nothingMoreHappens()
    }

    // CR 800.4e: combat damage isn't assigned to a player who left
    @Test
    fun anAttackedPlayerWhoConcedesTakesNoDamage() = scenario(players = 3) {
        alice.battlefield("Undead Soothsayer")
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card("Undead Soothsayer"), TargetRef.Player(bob.id)))))
        perform(Input.Concede(bob.id))
        playUntil(TurnStep.EndOfCombat)
        assertTrue(state.log.none { it.change is Change.Damage })
        expect { carol.life shouldBe 20 }
    }

    // CR 103.5, 104.3a: a player who concedes during the mulligans leaves; the others start without them
    @Test
    fun concedingDuringTheMulligansInAThreePlayerGame() = scenario(players = 3, start = table(swamps, mountains, swamps)) {
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        keep(bob)
        assertEquals(carol.id, decision<Decision.ChooseOption>().player)
        perform(Input.Concede(carol.id))
        assertTrue(state.started)
        assertEquals(alice.id, state.turn.active)
        assertEquals(alice.id, state.priority.holder)
        assertEquals(40, state.player(carol.id).outside.size)
        // CR 103.8c: still a multiplayer game, but nobody skips: Alice draws
        playUntil(TurnStep.PrecombatMain)
        expect { alice.hand.size shouldBe 8 }
    }

    // CR 103.5, 800.4a: a player whose mulligan is pending concedes; the decision is asked again without them
    @Test
    fun concedingWithACardToPutOnTheBottom() = scenario(players = 3, start = table(swamps, mountains, swamps)) {
        startGame(StartOptions(startingPlayer = alice.id, freeFirstMulligan = false))
        mulligan(alice)
        mulligan(bob)
        keep(carol)
        assertEquals(alice.id, decision<Decision.ChooseObjects>().player)
        perform(Input.Concede(alice.id))
        // Bob's mulligan is taken again, now alone
        bottom(bob, handNames(bob).first()!!)
        keep(bob)
        assertEquals(bob.id, state.turn.active)
        expect { bob.hand.size shouldBe 6 }
    }

    // CR 708.9: a leaving player's face-down permanents are revealed to all players
    @Test
    fun aLeavingPlayersFaceDownPermanentsAreRevealed() = scenario(players = 3) {
        bob.battlefield("Undead Soothsayer", faceDown = true)
        bob.battlefield("Elvish Seer")
        val faceDown = card("Undead Soothsayer")
        perform(Input.Concede(bob.id))
        assertEquals(listOf(GameEvent.Revealed(bob.id, listOf(faceDown))), applied.filterIsInstance<Change.Happened>()
            .map { it.event }.filterIsInstance<GameEvent.Revealed>())
    }
}
