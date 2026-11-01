package arcanum.engine.setup

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.RandomKind
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.state.CardId
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlin.test.assertEquals
import kotlin.test.fail

/** Helpers for scenarios that start a game from decks (CR 103) and play it to its end (CR 104). */

/** A deck of cube cards by name, top first; card ids are "<player>.<tag><n>". */
internal fun deckOf(owner: PlayerId, names: List<String>, tag: String = "d"): List<DeckCard> =
    names.mapIndexed { i, name -> DeckCard(CardId("${owner.value}.$tag${i + 1}"), Cards.definition(name)) }

/** [size] cards: [top] first, then [land] and [spell] by turns, starting with the land. */
internal fun cards(top: List<String>, land: String, spell: String, size: Int = 40): List<String> =
    top + List(size - top.size) { if (it % 2 == 0) land else spell }

/** A game before it begins with these decks (in seat order) and sideboards (by seat). */
internal fun table(
    vararg decks: List<String>, sideboards: Map<Int, List<String>> = emptyMap(), life: Int = 20,
): (List<PlayerId>) -> GameState = { ids ->
    Engine.newGame(decks.mapIndexed { i, deck -> Seat(ids[i], deckOf(ids[i], deck), deckOf(ids[i], sideboards[i].orEmpty(), "s")) }, life)
}

/** A shuffle that keeps the cards in the order they are (the deck order as listed). */
internal val inOrder: (RandomKind) -> Answer = { Answer.RandomObjects((it as RandomKind.Objects).among) }

/** A shuffle that moves the top [n] cards to the bottom. */
internal fun cutting(n: Int): (RandomKind) -> Answer = { kind ->
    val among = (kind as RandomKind.Objects).among
    Answer.RandomObjects(among.drop(n) + among.take(n))
}

internal fun Scenario.startGame(options: StartOptions = StartOptions()) = perform(Input.StartGame(options))

/** [player] declares to keep their hand (CR 103.5). */
internal fun Scenario.keep(player: ScenarioPlayer) = declare(player, Setup.KEEP)

/** [player] declares a mulligan (CR 103.5). */
internal fun Scenario.mulligan(player: ScenarioPlayer) = declare(player, Setup.MULLIGAN)

private fun Scenario.declare(player: ScenarioPlayer, choice: String) {
    val d = decision<Decision.ChooseOption>()
    assertEquals(player.id, d.player, "It is ${d.player}'s turn to declare")
    answer(Answer.Option(choice))
}

/** [player] puts the cards named [names] from their hand on the bottom of their library (CR 103.5). */
internal fun Scenario.bottom(player: ScenarioPlayer, vararg names: String) {
    val d = decision<Decision.ChooseObjects>()
    assertEquals(player.id, d.player)
    val chosen = mutableListOf<ObjectId>()
    names.forEach { name ->
        chosen += state.player(player.id).hand.firstOrNull { it !in chosen && nameOf(state.objOrThrow(it)) == name }
            ?: fail("No $name in ${player.id}'s hand")
    }
    answer(Answer.Objects(chosen))
}

/** Names of the cards in [player]'s hand, in order. */
internal fun Scenario.handNames(player: ScenarioPlayer): List<String?> = state.player(player.id).hand.map { nameOf(state.objOrThrow(it)) }

/** Names of the cards in [player]'s library, top first. */
internal fun Scenario.libraryNames(player: ScenarioPlayer): List<String?> = state.player(player.id).library.map { nameOf(state.objOrThrow(it)) }

/** Names of the cards [player] owns outside the game. */
internal fun Scenario.outsideNames(player: ScenarioPlayer): List<String?> = state.player(player.id).outside.map { nameOf(state.objOrThrow(it)) }

/** One move of the game as a player would: answer a cleanup discard with the first cards, declare nothing when a
 * declaration is awaited, else pass priority. */
private fun Scenario.nextMove() {
    val d = pending
    when {
        d is Decision.ChooseObjects -> answer(Answer.Objects(d.options.take(d.min)))
        d != null -> fail("Waiting for $d")
        state.priority.holder == null -> declareNone()
        else -> passPriority()
    }
}

/** Players pass (and declare nothing) until [step] of turn [turn] begins or the game is over. */
internal fun Scenario.playUntil(step: TurnStep, turn: Int = state.turn.number) {
    var guard = 300
    while (Engine.result(state) == null && (state.turn.step != step || state.turn.number != turn) && guard-- > 0) nextMove()
    if (guard <= 0) fail("Never reached $step of turn $turn")
}

/**
 * The active player takes a simple turn: plays a land if they can, casts every spell they can (resolving each), attacks
 * with everything that can attack when [attack]; the other players do nothing. Ends at the next turn's upkeep, or when
 * the game is over.
 */
internal fun Scenario.playTurn(attack: Boolean = false, cast: Boolean = true) {
    val number = state.turn.number
    val me = state.turn.active
    playUntil(TurnStep.PrecombatMain, number)
    if (Engine.result(state) != null) return
    Engine.options(state, me).firstOrNull { it is Input.PlayLand }?.let { perform(it) }
    while (cast) {
        val spell = Engine.options(state, me).filterIsInstance<Input.Cast>().firstOrNull() ?: break
        perform(spell)
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        if (pending != null) fail("Casting waits for $pending")
        allPass()
    }
    playUntil(TurnStep.DeclareAttackers, number)
    if (state.combat == null && state.turn.step == TurnStep.DeclareAttackers) {
        val attacks = if (!attack) emptyList() else
            Engine.options(state, me).filterIsInstance<Input.DeclareAttackers>().flatMap { it.attacks }.distinctBy { it.attacker }
        perform(Input.DeclareAttackers(me, attacks))
    }
    playUntil(TurnStep.Upkeep, number + 1)
}
