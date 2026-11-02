package arcanum.engine.review

import arcanum.api.CardType
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.FilledObject
import arcanum.engine.decision.Share
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.setup.StartOptions
import arcanum.engine.setup.table
import arcanum.engine.setup.cards
import arcanum.engine.state.GameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Review 2, the host's inputs: an async host relays answers from clients and its own random results and fills (D25,
 * D58, D60). Every input is either applied or refused with [IllegalArgumentException] (the refusal [Engine.options]
 * relies on), never a crash, and a refused answer can't make the engine do something the decision didn't ask:
 * malformed shuffles, random picks outside the set, dice beyond their sides, fills of other objects.
 */
class Review2HostInputTest {
    /** [input] on [game] is refused the documented way. */
    private fun refused(game: GameState, input: Input, what: String) {
        val e = try {
            Engine.perform(game, input)
            null
        } catch (t: Throwable) {
            t
        }
        if (e == null) fail("$what was accepted")
        if (e !is IllegalArgumentException) fail("$what crashed with ${e::class.simpleName}: ${e.message}")
    }

    /** The state on a copy after answering the pending decision with [answer]. */
    private fun Scenario.copyAfter(answer: Answer): GameState {
        val game = state.deepCopy()
        Engine.perform(game, Input.Answer(pending!!.id, answer)).changes.forEach { game.apply(it) }
        return game
    }

    /** An answer of the wrong kind (yes/no to a target choice) is refused, not a crash. */
    // D58
    @Test
    fun anAnswerOfTheWrongKindIsRefused() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        perform(Input.Cast(alice.id, card("Storm Spark")))
        refused(state, Input.Answer(pending!!.id, Answer.YesNo(true)), "yes/no to a target choice")
    }

    /** A shuffle answered with a card twice (and so another missing) is refused. */
    // CR 701.24a, D25
    @Test
    fun aShuffleWithACardTwiceIsRefused() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Fiendish Mentor")
        alice.library("Forest", "Storm Spark", "Island", "Swamp")
        alice.casts("Fiendish Mentor")
        allPass()
        val game = copyAfter(Answer.Objects(listOf(card("Storm Spark"))))
        val shuffle = game.pending?.decision as? Decision.Random ?: fail("No shuffle asked: ${game.pending}")
        val among = (shuffle.what as arcanum.engine.decision.RandomKind.Objects).among
        refused(game, Input.Answer(shuffle.id, Answer.RandomObjects(listOf(among[0]) + among)), "a shuffle with a card twice")
    }

    /** A random discard answered with an object outside the hand is refused. */
    // CR 701.9b, D25
    @Test
    fun aRandomDiscardOutsideTheHandIsRefused() = scenario {
        alice.battlefield("Mountain", "Elvish Seer")
        alice.hand("Reckless Wager", "Forest", "Island")
        alice.library("Storm Spark", "Swamp")
        alice.casts("Reckless Wager")
        allPass()
        val game = copyAfter(Answer.Objects(listOf(card("Storm Spark"))))
        val pick = game.pending?.decision as? Decision.Random ?: fail("No random pick asked: ${game.pending}")
        refused(game, Input.Answer(pick.id, Answer.RandomObjects(listOf(card("Elvish Seer")))), "a random discard of a permanent")
    }

    /** A die roll beyond the die's sides is refused (the starting player's roll). */
    // CR 706.1a, 103.1, D25
    @Test
    fun aDieRollBeyondItsSidesIsRefused() = scenario(start = table(
        cards(emptyList(), "Forest", "Elvish Seer"), cards(emptyList(), "Island", "Storm Spark"),
    )) {
        val game = state.deepCopy()
        Engine.perform(game, Input.StartGame(StartOptions())).changes.forEach { game.apply(it) }
        val roll = game.pending?.decision as? Decision.Random ?: fail("No roll asked: ${game.pending}")
        refused(game, Input.Answer(roll.id, Answer.DieRolls(listOf(7))), "a 7 on a two-sided die")
    }

    /** A host fill naming other objects than the ones asked is refused. */
    // D60
    @Test
    fun aFillOfOtherObjectsIsRefused() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Fiendish Mentor")
        alice.library("Forest", "Storm Spark", hidden = true)
        alice.casts("Fiendish Mentor")
        val (game, fill) = copyUntil<Decision.HostFill>()
        refused(game, Input.Answer(fill.id, Answer.Filled(listOf(FilledObject(card("Swamp"), Cards.definition("Island"))))),
            "a fill of a known Swamp")
    }

    /** Ordering triggers with an index twice is refused. */
    // CR 603.3b
    @Test
    fun aTriggerOrderWithAnIndexTwiceIsRefused() = scenario {
        Cards.invent("Review Bear", "{1}{G}", "Creature — Bear", "", "2/2")
        alice.battlefield("Review Bear", "Base Exarch", "Highborn Exarch")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Review Bear")
        val d = decision<Decision.OrderTriggers>()
        refused(state, Input.Answer(d.id, Answer.Order(listOf(0, 0))), "an order with an index twice")
    }

    /** Keeping a legend that isn't among the choices is refused. */
    // CR 704.5j
    @Test
    fun keepingAnotherObjectIsRefused() = scenario {
        alice.battlefield("Velo, Master of Ceremonies", "Velo, Master of Ceremonies", "Elvish Seer")
        settle()
        val d = decision<Decision.LegendRule>()
        refused(state, Input.Answer(d.id, Answer.Keep(card("Elvish Seer"))), "keeping a non-legend")
    }

    /** An option that isn't offered is refused (Bestower of Sigils: "colorless" or "a color"). */
    // CR 608.2d
    @Test
    fun anOptionNotOfferedIsRefused() = scenario {
        alice.battlefield("Bestower of Sigils", "Elvish Seer")
        alice.activates("Bestower of Sigils", target = "Elvish Seer")
        allPass()
        refused(state, Input.Answer(pending!!.id, Answer.Option("purple")), "an option that isn't offered")
    }

    /** A combat damage division that doesn't add up is refused. */
    // CR 510.1c
    @Test
    fun aDivisionThatDoesntAddUpIsRefused() = scenario {
        Cards.invent("Review Ox", "{2}{G}", "Creature — Ox", "", "3/3")
        alice.battlefield("Review Ox")
        bob.battlefield("Elvish Hierophant", "Wall of Portents")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Review Ox")
        allPass()
        bob.blocks("Elvish Hierophant" to "Review Ox", "Wall of Portents" to "Review Ox")
        allPass()
        val d = decision<Decision.Divide>()
        refused(state, Input.Answer(d.id, Answer.Division(listOf(Share(target("Elvish Hierophant"), 5)))), "5 damage from a 3/3")
    }

    /** Passing priority for a player who doesn't hold it is refused. */
    // CR 117.3d
    @Test
    fun passingWithoutPriorityIsRefused() = scenario {
        refused(state, Input.PassPriority(bob.id), "bob passing alice's priority")
    }

    /** X below its minimum is refused. */
    // CR 107.3
    @Test
    fun aNegativeXIsRefused() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Doomfire")
        perform(Input.Cast(alice.id, card("Doomfire")))
        while (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        val d = pending
        if (d is Decision.Announce) refused(state, Input.Answer(d.id, Answer.Number(-1)), "X = -1")
    }

    /** Casting an opponent's card from their hand is refused. */
    // CR 601.2, 601.3
    @Test
    fun castingTheOpponentsCardIsRefused() = scenario {
        alice.battlefield("Mountain")
        bob.hand("Storm Spark")
        refused(state, Input.Cast(alice.id, card("Storm Spark")), "alice casting bob's Storm Spark")
    }

    /** Answers to a choice of a card type among the offered (Tidecall Reversal) — another type is refused. */
    // CR 608.2d
    @Test
    fun aCardTypeNotOfferedIsRefused() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand("Tidecall Reversal")
        alice.casts("Tidecall Reversal", target = "bob")
        allPass()
        val d = pending
        if (d is Decision.ChooseCardType) {
            assertEquals(false, CardType.Planeswalker in d.among)
            refused(state, Input.Answer(d.id, Answer.CardTypeChoice(CardType.Planeswalker)), "planeswalker for artifact/creature/land")
        } else if (d is Decision.ChooseOption) {
            refused(state, Input.Answer(d.id, Answer.Option("planeswalker")), "planeswalker for artifact/creature/land")
        }
    }
}
