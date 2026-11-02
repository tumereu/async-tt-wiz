package arcanum.engine.decision

import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId

/**
 * The one answer-validation path (spec §7.0): every answer, a player's or the host's (P7, D25, D60), is checked against
 * what its decision asked before the engine goes on with it: its kind, and its contents within the allowed values,
 * counts, uniqueness and zones. A wrong answer is refused with [IllegalArgumentException] and changes nothing; the flows
 * check the rules that need the game further (a legal target, a payment that pays, a division of combat damage).
 */
internal object AnswerCheck {
    fun check(state: GameState, decision: Decision, answer: Answer) {
        when (decision) {
            is Decision.Targets -> {
                val chosen = expect<Answer.Targets>(decision, answer).chosen
                distinct(chosen, "target")
                among(chosen, decision.options, "a target offered")
                count(chosen.size, decision.min, decision.max, "targets")
            }
            is Decision.Modes -> {
                val chosen = expect<Answer.Modes>(decision, answer).chosen
                if (!decision.repeatable) distinct(chosen, "mode")
                among(chosen, decision.options.filter { it.available }.map { it.index }, "an available mode")
                count(chosen.size, decision.min, decision.max, "modes")
            }
            is Decision.Announce -> {
                val v = expect<Answer.Number>(decision, answer).value
                if (v < decision.min || (decision.max != null && v > decision.max)) refuse("${decision.name} must be from ${decision.min} to ${decision.max ?: "any"}: $v")
            }
            is Decision.ChooseNumber -> {
                val v = expect<Answer.Number>(decision, answer).value
                if (v < decision.min || (decision.max != null && v > decision.max)) refuse("Choose a number from ${decision.min} to ${decision.max ?: "any"}: $v")
            }
            is Decision.ManaPayment -> expect<Answer.ManaPayment>(decision, answer)
            is Decision.May -> expect<Answer.YesNo>(decision, answer)
            is Decision.ChooseObjects -> {
                val chosen = expect<Answer.Objects>(decision, answer).chosen
                distinct(chosen, "object")
                among(chosen, decision.options, "an object offered")
                count(chosen.size, decision.min, decision.max, "objects")
            }
            is Decision.ChooseOption -> among(listOf(expect<Answer.Option>(decision, answer).chosen), decision.options, "an option offered")
            is Decision.ChooseColor -> among(listOf(expect<Answer.ColorChoice>(decision, answer).color), decision.among, "a colour offered")
            is Decision.ChooseSubtype -> {
                val subtype = expect<Answer.SubtypeChoice>(decision, answer).subtype
                decision.among?.let { among(listOf(subtype), it, "a subtype offered") }
                if (subtype.name.isBlank()) refuse("Name a subtype")
            }
            is Decision.ChooseCardType -> among(listOf(expect<Answer.CardTypeChoice>(decision, answer).cardType), decision.among, "a card type offered")
            is Decision.ChooseCardName -> if (expect<Answer.CardName>(decision, answer).name.isBlank()) refuse("Name a card")
            is Decision.ChoosePlayer -> among(listOf(expect<Answer.Player>(decision, answer).player), decision.options, "a player offered")
            is Decision.Divide -> {
                val shares = expect<Answer.Division>(decision, answer).shares
                distinct(shares.map { it.target }, "share's target")
                among(shares.map { it.target }, decision.among, "one to divide among")
                if (shares.any { it.amount < decision.minEach }) refuse("Each gets at least ${decision.minEach}")
                if (shares.sumOf { it.amount } != decision.amount) refuse("Divide exactly ${decision.amount}")
            }
            is Decision.OrderTriggers -> {
                val order = expect<Answer.Order>(decision, answer).order
                if (order.size != decision.triggers.size || order.toSet() != decision.triggers.map { it.index }.toSet()) {
                    refuse("Order every trigger once: $order")
                }
            }
            is Decision.ChooseReplacement ->
                among(listOf(expect<Answer.Replacement>(decision, answer).index), decision.options.map { it.index }, "a replacement offered")
            is Decision.LegendRule -> among(listOf(expect<Answer.Keep>(decision, answer).kept), decision.among, "one of the legends")
            is Decision.Random -> random(state, decision, answer)
            is Decision.HostFill -> {
                val filled = expect<Answer.Filled>(decision, answer).objects.map { it.id }
                // D60: exactly the objects asked, each once
                if (filled.size != decision.objects.size || filled.toSet() != decision.objects.toSet()) {
                    refuse("Fill exactly ${decision.objects}, each once: $filled")
                }
            }
            is Decision.OtherView -> {
                val a = expect<Answer.OtherView>(decision, answer)
                val filled = a.filled.map { it.id }
                // D60: exactly the objects asked, each once; the answers are checked as the steps ask them
                if (filled.size != decision.objects.size || filled.toSet() != decision.objects.toSet()) {
                    refuse("Fill exactly ${decision.objects}, each once: $filled")
                }
                if (a.answers.any { it is Answer.Filled || it is Answer.OtherView }) refuse("Identities go in filled, not among the answers")
            }
            is Decision.Manual -> expect<Answer.Done>(decision, answer)
        }
    }

    private fun random(state: GameState, decision: Decision.Random, answer: Answer) {
        when (val what = decision.what) {
            RandomKind.CoinFlip -> expect<Answer.CoinFlip>(decision, answer)
            is RandomKind.Die -> {
                val results = expect<Answer.DieRolls>(decision, answer).results
                // CR 706.1a: one result per die, each a side of it
                if (results.size != what.count) refuse("Roll ${what.count} ${if (what.count == 1) "die" else "dice"}: $results")
                if (results.any { it !in 1..what.sides }) refuse("A ${what.sides}-sided die rolls 1 to ${what.sides}: $results")
            }
            is RandomKind.Objects -> {
                val chosen = expect<Answer.RandomObjects>(decision, answer).chosen
                distinct(chosen, "object")
                if (chosen.size != what.count) refuse("Pick ${what.count}: $chosen")
                if (what.newIds) {
                    // D60: new card objects, no card any object in the state has
                    val used = state.objects.keys.mapNotNull { (it as? ObjectId.Card)?.card }.toSet()
                    if (chosen.any { it !is ObjectId.Card || it.card in used }) refuse("Give new card ids, none of a card in the game: $chosen")
                    if (chosen.map { (it as ObjectId.Card).card }.distinct().size != chosen.size) refuse("Give a different card id for each: $chosen")
                } else among(chosen, what.among, "one of those to pick from")
            }
        }
    }

    private inline fun <reified A : Answer> expect(decision: Decision, answer: Answer): A =
        answer as? A ?: wrongKind(decision, answer)

    private fun wrongKind(decision: Decision, answer: Answer): Nothing =
        refuse("${answer::class.simpleName} doesn't answer ${decision::class.simpleName}")

    private fun <T> distinct(values: List<T>, what: String) {
        if (values.distinct().size != values.size) refuse("The same $what twice: $values")
    }

    private fun <T> among(values: List<T>, allowed: Collection<T>, what: String) {
        values.firstOrNull { it !in allowed }?.let { refuse("Not $what: $it") }
    }

    private fun count(n: Int, min: Int, max: Int?, what: String) {
        if (n < min || (max != null && n > max)) refuse("Choose ${if (max == min) "$min" else "$min to ${max ?: "any number of"}"} $what, not $n")
    }

    private fun refuse(why: String): Nothing = throw IllegalArgumentException(why)
}
