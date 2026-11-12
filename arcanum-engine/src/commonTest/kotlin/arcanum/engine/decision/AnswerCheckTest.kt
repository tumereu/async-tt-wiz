package arcanum.engine.decision

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.setup.StartOptions
import arcanum.engine.setup.cards
import arcanum.engine.setup.table
import arcanum.engine.state.CardId
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * The one answer-validation path ([AnswerCheck], spec §7.0): every answer, a player's or the host's, is checked against
 * what its decision asked, and refused with [IllegalArgumentException] otherwise; nothing changes, nothing crashes.
 */
class AnswerCheckTest {
    private fun refused(game: GameState, answer: Answer, what: String) {
        val d = game.pending?.decision ?: fail("Nothing is open")
        val e = try {
            Engine.perform(game, Input.Answer(d.id, answer))
            null
        } catch (t: Throwable) {
            t
        }
        if (e == null) fail("$what was accepted")
        if (e !is IllegalArgumentException) fail("$what crashed with ${e::class.simpleName}: ${e.message}")
    }

    private fun accepted(game: GameState, answer: Answer) {
        Engine.perform(game, Input.Answer(game.pending!!.decision.id, answer))
    }

    /** The shuffle a search ends with, on a copy. */
    private fun Scenario.shuffleAfterSearch(): Pair<GameState, RandomKind.Objects> {
        alice.battlefield("Sunbaked Mesa")
        alice.library("Storm Spark", "Mountain", "Plains")
        alice.activates("Sunbaked Mesa")
        allPass()
        val game = state.deepCopy()
        Engine.perform(game, Input.Answer(game.pending!!.decision.id, Answer.Objects(listOf(card("Mountain"))))).changes.forEach { game.apply(it) }
        val shuffle = game.pending?.decision as? Decision.Random ?: fail("No shuffle asked: ${game.pending}")
        return game to shuffle.what as RandomKind.Objects
    }

    /** A shuffle is answered with the library's own cards, each once; new ids are refused. */
    // CR 701.24a, D25
    @Test
    fun aShuffleWithFullInformationTakesTheLibrarysCards() = scenario {
        val (game, what) = shuffleAfterSearch()
        refused(game, Answer.RandomObjects(List(what.count) { ObjectId.Card(CardId("fresh.$it"), 0) }), "new ids")
        refused(game, Answer.RandomObjects(what.among.take(1)), "one card of two")
        accepted(game, Answer.RandomObjects(what.among.reversed()))
    }

    /** The card the host gives for a card name ([Decision.NamedCard]) must have that name. */
    // CR 707.12
    @Test
    fun aNamedCardOfAnotherNameIsRefused() = scenario {
        val asked = Decision.NamedCard(DecisionId("named"), alice.id, null, "Forest")
        AnswerCheck.check(state, asked, Answer.NamedCard(Cards.definition("Forest")))
        kotlin.test.assertFailsWith<IllegalArgumentException> { AnswerCheck.check(state, asked, Answer.NamedCard(Cards.definition("Storm Spark"))) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { AnswerCheck.check(state, asked, Answer.Done) }
    }

    /** The starting player's die: one result per die, within its sides. */
    // CR 706.1a, 103.1
    @Test
    fun dieRollsMustMatchTheDice() = scenario(start = table(
        cards(emptyList(), "Forest", "Elvish Seer"), cards(emptyList(), "Island", "Storm Spark"),
    )) {
        val game = state.deepCopy()
        Engine.perform(game, Input.StartGame(StartOptions())).changes.forEach { game.apply(it) }
        refused(game, Answer.DieRolls(listOf(1, 2)), "two results for one die")
        refused(game, Answer.DieRolls(emptyList()), "no result")
        refused(game, Answer.DieRolls(listOf(0)), "a 0")
        refused(game, Answer.CoinFlip(true), "a coin flip for a die")
        accepted(game, Answer.DieRolls(listOf(2)))
    }

    /** A random discard of the wrong number of cards, or of one card twice, is refused. */
    // CR 701.9b, D25
    @Test
    fun aRandomDiscardTakesItsCountOfDistinctCards() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Reckless Wager", "Forest", "Island")
        alice.library("Storm Spark", "Swamp")
        alice.casts("Reckless Wager")
        allPass()
        val game = state.deepCopy()
        Engine.perform(game, Input.Answer(game.pending!!.decision.id, Answer.Objects(listOf(card("Storm Spark"))))).changes.forEach { game.apply(it) }
        val pick = (game.pending?.decision as? Decision.Random)?.what as? RandomKind.Objects ?: fail("No random pick: ${game.pending}")
        refused(game, Answer.RandomObjects(listOf(pick.among[0], pick.among[0])), "one card twice")
        refused(game, Answer.RandomObjects(pick.among.take(2)), "two cards for one")
        refused(game, Answer.RandomObjects(emptyList()), "none")
        accepted(game, Answer.RandomObjects(pick.among.take(1)))
    }

    /** A choice of objects outside its options, of one object twice, or of too many is refused. */
    // CR 608.2d, 701.23a
    @Test
    fun aChoiceOfObjectsStaysWithinItsOptions() = scenario {
        alice.battlefield("Sunbaked Mesa", "Swamp")
        alice.library("Storm Spark", "Mountain", "Plains")
        alice.activates("Sunbaked Mesa")
        allPass()
        val d = decision<Decision.ChooseObjects>()
        refused(state, Answer.Objects(listOf(card("Storm Spark"))), "a card that doesn't match")
        refused(state, Answer.Objects(listOf(card("Swamp"))), "a permanent")
        refused(state, Answer.Objects(listOf(card("Mountain"), card("Mountain"))), "the Mountain twice")
        refused(state, Answer.Objects(d.options + d.options), "more than the maximum")
        refused(state, Answer.YesNo(true), "yes for a choice")
        accepted(state, Answer.Objects(emptyList()))
    }

    /** Any answer of any kind with odd contents, to every decision of a whole game, is accepted or refused the
     * documented way, never a crash (LongGame with malformed answers). */
    // D58
    @Test
    fun malformedAnswersNeverCrash() {
        arcanum.engine.review.LongGame(7L, jsonEvery = 50, malformed = true).play()
    }
}
