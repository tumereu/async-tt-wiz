package arcanum.engine.setup

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.LoopStop
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Why each player lost ([LossReason], kept as [arcanum.engine.state.PlayerState.lossReason]): one test per way to lose. */
class LossReasonTest {
    /** "Target player loses the game." (written by hand, as HandWrittenEffectsTest's) */
    private val doom = Cards.define("Test Doom", CardDefinition(Layout.Normal, listOf(FaceDefinition("Test Doom", ManaCost.parse("{1}"),
        types = listOf(CardType.Sorcery), abilities = listOf(Ability.Spell(Instructions(
            targets = listOf(TargetSlot("player", TargetKind.Players(PlayerFilter.Any), Value.Fixed(1), Value.Fixed(1))),
            steps = listOf(Step.Do(Effect.LoseGame(Players.Target("player")))))))))))

    /** "You win the game." */
    private val triumph = Cards.define("Test Triumph", CardDefinition(Layout.Normal, listOf(FaceDefinition("Test Triumph",
        ManaCost.parse("{1}"), types = listOf(CardType.Sorcery),
        abilities = listOf(Ability.Spell(Instructions(steps = listOf(Step.Do(Effect.WinGame(Players.You))))))))))

    // CR 104.3b, 704.5a: a player at 0 or less life loses: their reason is their life
    @Test
    fun zeroLife() = scenario {
        change(Changes.life(state, bob.id, -20))
        passPriority()
        assertEquals(LossReason.ZeroLife, state.player(bob.id).lossReason)
        assertNull(state.player(alice.id).lossReason)
        assertEquals(LossReason.ZeroLife, applied.filterIsInstance<Change.Lost>().single().reason)
    }

    // CR 104.3c, 704.5b: a player who drew from an empty library loses: their reason is the empty library
    @Test
    fun emptyLibrary() = scenario(start = table(List(8) { "Swamp" }, List(8) { "Mountain" })) {
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        keep(bob)
        repeat(4) { if (Engine.result(state) == null) playTurn() }
        assertEquals(GameResult.Won(alice.id), Engine.result(state))
        assertEquals(LossReason.EmptyLibrary, state.player(bob.id).lossReason)
    }

    // CR 104.3d, 704.5c: a player with ten or more poison counters loses: their reason is poison
    @Test
    fun poison() = scenario {
        bob.counters(CounterKind.Poison, 10)
        passPriority()
        assertEquals(LossReason.Poison, state.player(bob.id).lossReason)
    }

    // CR 704.5a, 704.5c: with 0 life and ten poison counters at once, the first state-based action listed names the loss
    @Test
    fun theFirstStateBasedActionListedNamesTheLoss() = scenario {
        bob.counters(CounterKind.Poison, 10)
        change(Changes.life(state, bob.id, -20))
        passPriority()
        assertEquals(LossReason.ZeroLife, state.player(bob.id).lossReason)
    }

    // CR 104.3e: an effect that says a player loses names its card
    @Test
    fun aCardsEffect() = scenario {
        alice.hand(doom)
        alice.castsWithoutCosts(doom, target = "bob")
        resolve()
        assertEquals(LossReason.Effect("Test Doom"), state.player(bob.id).lossReason)
    }

    // CR 104.2b, 104.3e: an effect that says a player wins makes the others lose, naming its card
    @Test
    fun anotherPlayerWinsByAnEffect() = scenario {
        alice.hand(triumph)
        alice.castsWithoutCosts(triumph)
        resolve()
        assertEquals(GameResult.Won(alice.id), Engine.result(state))
        assertEquals(LossReason.Effect("Test Triumph"), state.player(bob.id).lossReason)
    }

    // CR 104.3a: a player who concedes loses: their reason is the concession
    @Test
    fun conceding() = scenario {
        perform(Input.Concede(bob.id))
        assertEquals(LossReason.Conceded, state.player(bob.id).lossReason)
        assertNull(state.player(alice.id).lossReason)
    }

    // CR 104.3a: an agreed win: the other players leave as agreed
    @Test
    fun anAgreedWin() = scenario {
        perform(Input.AgreedResult(GameResult.Won(alice.id)))
        assertEquals(LossReason.Agreed, state.player(bob.id).lossReason)
        assertNull(state.player(alice.id).lossReason)
    }

    // CR 104.4i: an agreed draw: everyone leaves as agreed
    @Test
    fun anAgreedDraw() = scenario {
        perform(Input.AgreedResult(GameResult.Draw))
        assertEquals(GameResult.Draw, Engine.result(state))
        assertEquals(listOf(LossReason.Agreed, LossReason.Agreed), state.players.map { it.lossReason })
    }

    // CR 104.4a: everyone losing at once is a draw: each keeps their own reason
    @Test
    fun everyoneLosesAtOnce() = scenario {
        bob.counters(CounterKind.Poison, 10)
        change(Changes.life(state, alice.id, -20))
        passPriority()
        assertEquals(GameResult.Draw, Engine.result(state))
        assertEquals(LossReason.ZeroLife, state.player(alice.id).lossReason)
        assertEquals(LossReason.Poison, state.player(bob.id).lossReason)
    }

    // CR 104.4b: a loop of mandatory actions ended as a draw: everyone's reason is the loop
    @Test
    fun aLoopDraw() = scenario {
        alice.battlefield(sprite, twin, "Island")
        alice.hand(glance)
        alice.casts(glance, target = sprite)
        assertEquals(listOf(LoopStop.CONTINUE, LoopStop.DRAW), decision<Decision.ChooseOption>().options)
        answer(Answer.Option(LoopStop.DRAW))
        assertEquals(GameResult.Draw, Engine.result(state))
        assertEquals(listOf(LossReason.Loop, LossReason.Loop), state.players.map { it.lossReason })
    }

    // CR 104.3: undoing a loss takes its reason back
    @Test
    fun undoingALossTakesItsReasonBack() = scenario {
        perform(Input.Concede(bob.id))
        val lost = applied.filterIsInstance<Change.Lost>().single()
        state.revert(lost)
        assertNull(state.player(bob.id).lossReason)
        state.apply(lost)
        assertIs<LossReason.Conceded>(state.player(bob.id).lossReason)
    }

    /** "Whenever this creature becomes the target of a spell or ability, tap another target creature." (as LoopTest's) */
    private fun mirror(name: String): String {
        val base = Cards.definition(Cards.invent("Mirrorglass Base", "{U}", "Creature — Faerie",
            "Whenever this creature attacks, tap another target creature.", "1/1"))
        val face = base.faces.single()
        val attacks = face.abilities.single() as Ability.Triggered
        val targeted = attacks.copy(trigger = Trigger.On(EventPattern.BecomesTarget(ObjectFilter.Is(Objects.Self), ObjectFilter.Any)))
        return Cards.define(name, CardDefinition(base.layout, listOf(face.copy(name = name, abilities = listOf(targeted)))))
    }
    private val sprite = mirror("Mirrorglass Sprite")
    private val twin = mirror("Mirrorglass Twin")
    private val glance = Cards.invent("Prying Glance", "{U}", "Instant", "Tap target creature.")
}
