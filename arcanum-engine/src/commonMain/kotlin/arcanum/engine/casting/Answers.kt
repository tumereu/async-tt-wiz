package arcanum.engine.casting

import arcanum.api.CostPart
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Choices
import arcanum.engine.state.DividedShare
import arcanum.engine.state.NamedValue

/**
 * Reading an answer to a cast's question into its choices, checked against what was asked (D32: a rule-breaking
 * answer is refused), and the questions that answer themselves because there is only one way (P5).
 */
internal object Answers {
    fun apply(plan: Plan, q: Question, answer: Answer): Choices {
        val c = plan.choices
        fun refuse(why: String): Nothing = throw IllegalArgumentException(why)
        return when (q) {
            is Question.Modes -> {
                val chosen = (answer as? Answer.Modes)?.chosen ?: refuse("Expected modes")
                val d = plan.decision(q, arcanum.engine.decision.DecisionId("check")) as arcanum.engine.decision.Decision.Modes
                // CR 700.2: the number of modes, each available, and the same one again only when allowed
                if (chosen.size !in d.min..d.max) refuse("Choose ${d.min} to ${d.max} modes")
                if (chosen.any { i -> d.options.getOrNull(i)?.available != true }) refuse("Not an available mode")
                if (!d.repeatable && chosen.distinct().size != chosen.size) refuse("A mode can be chosen only once")
                // blb: CR 700.2i: at most the limit's worth of pawprints
                d.pawprintLimit?.let { limit -> if (chosen.sumOf { i -> d.options[i].pawprints } > limit) refuse("More than $limit {P} worth of modes") }
                c.copy(modes = chosen, names = c.names.with(Plan.MODES_ASKED, NamedValue.Number(1)))
            }
            is Question.OptionalCost -> {
                val times = when (answer) {
                    is Answer.YesNo -> if (answer.yes) 1 else 0
                    is Answer.Number -> answer.value
                    else -> refuse("Expected yes or no, or a number")
                }
                if (times < 0 || (!q.mechanic.repeatable && times > 1)) refuse("Not a number of times it can be paid")
                // tdm: cast when only an instant could be, "as though it had flash if you [pay this cost]": it must be paid (CR 702.8a, 601.2b)
                if (q.mechanic.flash && times == 0 && !plan.flashWithoutCost()) refuse("Cast as though it had flash only by paying that cost")
                c.copy(costs = c.costs.with(q.key, NamedValue.Number(times)))
            }
            is Question.Splice -> {
                val yes = (answer as? Answer.YesNo)?.yes ?: refuse("Expected yes or no")
                c.copy(costs = c.costs.with(q.key, NamedValue.Number(if (yes) 1 else 0)))
            }
            is Question.Announce -> {
                val v = (answer as? Answer.Number)?.value ?: refuse("Expected a number")
                if (v < q.min) refuse("${q.name} must be at least ${q.min}")
                // ecl: "X can't be greater than …" (CR 107.3a)
                if (q.name == "X") plan.xMaximum()?.let { max -> if (v > max) refuse("X can't be greater than $max") }
                c.copy(announced = c.announced + (q.name to v))
            }
            is Question.Target -> {
                // dgm: a target chosen at random: the die result picks it (CR 115: still targeted)
                val rolled = (answer as? Answer.DieRolls)?.takeIf { q.slot.slot.atRandom }?.results?.singleOrNull()
                    ?.let { r -> listOfNotNull(q.options.getOrNull(r - 1)) }
                val chosen = rolled ?: (answer as? Answer.Targets)?.chosen ?: refuse("Expected targets")
                // CR 601.2c, 115.3: legal targets, the right number, each object or player once per slot
                if (chosen.size < q.min || (q.max != null && chosen.size > q.max)) refuse("Choose ${q.min} to ${q.max ?: "any number of"} targets")
                if (chosen.distinct().size != chosen.size) refuse("The same target twice in one slot")
                if (chosen.any { it !in q.options }) refuse("Not a legal target: ${chosen.filter { it !in q.options }}")
                // "target creatures controlled by the same opponent" (CR 601.2c)
                if (q.slot.slot.sameController &&
                    chosen.mapNotNull { (it as? arcanum.engine.state.TargetRef.Object)?.id?.let { id -> plan.snap.board.controller(id) } }.distinct().size > 1)
                    refuse("The targets must have the same controller")
                // ecl: "two target creature cards that share a creature type" (CR 601.2c)
                q.slot.slot.sharing?.let { quality ->
                    val ids = chosen.mapNotNull { (it as? arcanum.engine.state.TargetRef.Object)?.id }
                    val e = plan.snap.evaluator(plan.player)
                    if (ids.any { a -> ids.any { b -> a != b && !e.shares(quality, a, b) } }) refuse("The targets must share $quality")
                }
                // spm: "up to six target creature cards with different names" (CR 601.2c)
                q.slot.slot.distinct?.let { quality ->
                    val ids = chosen.mapNotNull { (it as? arcanum.engine.state.TargetRef.Object)?.id }
                    val e = plan.snap.evaluator(plan.player)
                    if (ids.any { a -> ids.any { b -> a != b && e.shares(quality, a, b) } }) refuse("The targets must differ in $quality")
                }
                // gtc chunk 10: "from a single graveyard": one owner (CR 601.2c)
                if (q.slot.slot.sameOwner &&
                    chosen.mapNotNull { (it as? arcanum.engine.state.TargetRef.Object)?.id?.let { id -> plan.snap.state.obj(id)?.owner } }.distinct().size > 1)
                    refuse("The targets must have the same owner")
                // fra: "up to one target … of each card type": each target stands for a value of its own
                q.slot.slot.onePer?.let { quality ->
                    val ids = chosen.mapNotNull { (it as? arcanum.engine.state.TargetRef.Object)?.id }
                    if (!plan.snap.evaluator(plan.player).eachStandsForOne(quality, ids)) refuse("Each target must stand for a different $quality")
                }
                // msh: "with total mana value 6 or less" (CR 601.2c)
                q.slot.slot.totalAtMost?.let { limit ->
                    val e = plan.snap.evaluator(plan.player)
                    val ids = chosen.mapNotNull { (it as? arcanum.engine.state.TargetRef.Object)?.id }
                    if (ids.sumOf { e.property(limit.property, it) } > e.value(limit.atMost)) refuse("The targets' total is more than allowed")
                }
                val key = q.slot.key
                if (q.forPlayer == null) c.copy(targets = c.targets + (key to chosen))
                else c.copy(
                    targets = c.targets + (key to (c.targets[key].orEmpty() + chosen)),
                    names = c.names.with(Plan.PER_PLAYER + key + ":" + q.forPlayer.value, NamedValue.Targets(chosen)),
                )
            }
            is Question.Divide -> {
                val shares = (answer as? Answer.Division)?.shares ?: refuse("Expected a division")
                // CR 601.2d: each target gets at least one, and all of it is divided
                if (shares.map { it.target }.toSet() != q.among.toSet() || shares.size != q.among.size) refuse("Divide among exactly the targets")
                if (shares.any { it.amount < 1 }) refuse("Each target gets at least 1")
                if (shares.sumOf { it.amount } != q.amount) refuse("Divide exactly ${q.amount}")
                c.copy(divided = c.divided + shares.map { DividedShare(q.key, it.target, it.amount) })
            }
            is Question.OneOf -> {
                val chosen = (answer as? Answer.Option)?.chosen ?: refuse("Expected an option")
                val index = q.labels.indexOf(chosen).takeIf { it >= 0 } ?: refuse("Not an option: $chosen")
                c.copy(names = c.names.with(Plan.ONE_OF + q.key, NamedValue.Number(index)))
            }
            is Question.Objects -> {
                val chosen = (answer as? Answer.Objects)?.chosen ?: refuse("Expected objects")
                val ch = q.choice
                if (chosen.distinct().size != chosen.size || chosen.any { it !in ch.options }) refuse("Not among the options")
                if (chosen.size < ch.min || (ch.max != null && chosen.size > ch.max)) refuse("Choose ${ch.min} to ${ch.max ?: "any number"}")
                // rtr chunk 07: "with the same name": all of them share it
                ch.shared?.let { quality ->
                    if (chosen.any { a -> chosen.any { b -> a != b && !plan.eval.shares(quality, a, b) } }) refuse("The chosen objects must share $quality")
                }
                ch.total?.let { total ->
                    val sum = chosen.sumOf { plan.eval.property(total.property, it) }
                    if (sum < plan.eval.value(total.atLeast)) refuse("Their total ${total.property} must be at least ${plan.eval.value(total.atLeast)}")
                }
                c.copy(names = c.names.with(Plan.OBJECTS + q.key, NamedValue.Objects(chosen)))
            }
            is Question.ManaColor -> {
                val color = (answer as? Answer.ColorChoice)?.color ?: refuse("Expected a colour")
                if (color !in q.among) refuse("It can't make $color")
                val before = (c.names[q.key] as? NamedValue.Colors)?.colors.orEmpty()
                c.copy(names = c.names.with(q.key, NamedValue.Colors(before + color)))
            }
            is Question.Pay -> refuse("Mana is paid when the cast finishes")
            // ecl: a creature type chosen with the costs, kept under its name for the parts and the spell that read it
            // eoe: objects chosen with the costs, kept under the choice's name for the spell
            is Question.CostChoice -> if (q.part.choice is arcanum.api.Choice.Objects) {
                val choice = q.part.choice as arcanum.api.Choice.Objects
                val ids = (answer as? Answer.Objects)?.chosen ?: refuse("Expected objects")
                val options = plan.costChoiceObjects(choice)
                if (ids.any { it !in options } || ids.distinct().size != ids.size) refuse("Not objects to choose")
                if (ids.size < plan.eval.value(choice.min) || choice.max?.let { ids.size > plan.eval.value(it) } == true) refuse("Wrong number of objects")
                c.copy(names = c.names.with(q.part.bind, NamedValue.Objects(ids)))
            } else {
                val subtype = (answer as? Answer.SubtypeChoice)?.subtype ?: refuse("Expected a creature type")
                (q.part.choice as? arcanum.api.Choice.CreatureTypeAmong)?.let { if (subtype !in it.among) refuse("Not one of the types offered") }
                c.copy(names = c.names.with(q.part.bind, NamedValue.SubtypeValue(subtype)))
            }
            is Question.Player -> {
                val chosen = (answer as? Answer.Player)?.player ?: refuse("Expected a player")
                if (chosen !in q.among) refuse("Not a player to choose: $chosen")
                c.copy(names = c.names.with(Plan.PLAYER + q.key, NamedValue.Players(listOf(chosen))))
            }
        }
    }

    /** The answer when there is only one possible one (P5): an option of "A or B" that alone can be paid, objects that
     * must all be used. Targets, modes and payments are always asked. */
    fun automatic(plan: Plan, q: Question): Answer? = when (q) {
        is Question.OneOf -> {
            val payable = q.part.options.indices.filter { i -> payable(plan, q.part.options[i].parts) }
            if (payable.size == 1) Answer.Option(q.labels[payable.single()]) else null
        }
        is Question.Objects -> {
            val ch = q.choice
            if (ch.total == null && ch.shared == null && ch.max != null && ch.min == ch.max && ch.options.size == ch.min) Answer.Objects(ch.options) else null
        }
        is Question.Target -> if (q.options.isEmpty() && q.min == 0) Answer.Targets(emptyList()) else null
        // blb: the only opponent there is (CR 702.174a, two players)
        is Question.Player -> q.among.singleOrNull()?.let { Answer.Player(it) }
        else -> null
    }

    /** Whether cost [parts] could be paid: enough objects to choose, enough life (CR 119.4), energy, cards to mill,
     * counters to remove from the objects it names (CR 118.3). */
    fun payable(plan: Plan, parts: List<CostPart>): Boolean = parts.all { part ->
        val you = plan.snap.state.player(plan.player)
        when (part) {
            is CostPart.PayLife -> you.life >= plan.eval.value(part.amount)
            is CostPart.PayEnergy -> you.counters(arcanum.api.CounterKind.Energy) >= plan.eval.value(part.amount)
            is CostPart.Mill -> you.library.size >= plan.eval.value(part.count)
            is CostPart.RemoveCounters -> when (val from = part.from) {
                is arcanum.api.CostObjects.Choose -> Costs.objectChoice(plan.snap, plan.player, if (plan.isSpell) plan.itemId else null, part, plan.eval)
                    .let { choice -> choice == null || choice.options.size >= choice.min }
                arcanum.api.CostObjects.Self -> enoughCounters(plan, part, listOf(plan.sourceId))
                is arcanum.api.CostObjects.These -> enoughCounters(plan, part, plan.eval.objects(from.objects))
            }
            else -> {
                val choice = Costs.objectChoice(plan.snap, plan.player, if (plan.isSpell) plan.itemId else null, part, plan.eval)
                choice == null || choice.options.size >= choice.min
            }
        }
    }

    /** Whether each of [ids] has the counters [part] removes (of its kind, else of the kind it has first, as paid). */
    private fun enoughCounters(plan: Plan, part: CostPart.RemoveCounters, ids: List<arcanum.engine.state.ObjectId>): Boolean {
        val n = plan.eval.value(part.amount)
        return ids.all { id ->
            val o = plan.snap.state.obj(id) ?: return@all false
            val kind = part.kind ?: o.counters.firstOrNull()?.kind ?: return@all n <= 0
            o.counters(kind) >= n
        }
    }
}
