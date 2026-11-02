package arcanum.api

/**
 * Whether this ability is a mana ability (CR 605.1), decided from the definition's shape; no flag is stored (D54).
 *
 * - Activated (CR 605.1a): no target in its instructions or any mode (an "up to one" target is still a target,
 *   CR 115.6); could add mana ([Effect.AddMana], or doubling the mana in a pool, in any step it can reach: inside
 *   pays, mays, ifs, loops and modes); not a loyalty ability (no [CostPart.Loyalty]); and neither its cost nor its
 *   effect moves a card to or from a library (draw, mill, surveil, search, reveal until, explore, manifest, cloak,
 *   connive, discover, a move to a library or of library cards, a cost that mills or exiles from a library).
 * - Triggered (CR 605.1b): no target; it triggers only from mana abilities ("whenever you tap a land for mana",
 *   [EventPattern.Tap] with `forMana`); and it could add mana.
 * - Everything else is not (spells, CR 605.5b; static, keyword and special-action abilities).
 *
 * Only the ability's own instructions count, with their self-replacements (CR 605.1a): abilities it creates (reflexive
 * and delayed triggers, granted abilities, tokens, emblems) are abilities of their own.
 */
fun Ability.isManaAbility(): Boolean = when (this) {
    is Ability.Activated -> !instructions.hasTargets() && instructions.couldAddMana() &&
        cost.parts.none { it is CostPart.Loyalty } && !cost.movesLibraryCard() && !instructions.movesLibraryCard()
    is Ability.Triggered -> !instructions.hasTargets() && trigger.isManaTrigger() && instructions.couldAddMana()
    else -> false
}

private fun Instructions.hasTargets(): Boolean =
    targets.isNotEmpty() || modes?.options.orEmpty().any { it.instructions.hasTargets() }

/** Every step this ability's resolution can reach, nested steps and modes included, and its self-replacements. */
private fun Instructions.reachableSteps(): List<Step> {
    val found = mutableListOf<Step>()
    fun visit(steps: List<Step>) {
        steps.forEach { step ->
            found += step
            when (step) {
                is Step.Simultaneously -> visit(step.steps)
                is Step.Pay -> visit(step.steps)
                is Step.May -> visit(step.steps)
                is Step.If -> { visit(step.then); visit(step.otherwise) }
                is Step.ForEach -> visit(step.steps)
                is Step.Repeat -> visit(step.steps)
                is Step.RepeatWhile -> visit(step.steps)
                is Step.Do, is Step.Bind, is Step.Modify, is Step.Choose, is Step.Manual -> Unit
            }
        }
    }
    fun visit(instructions: Instructions) {
        visit(instructions.steps)
        instructions.modes?.options.orEmpty().forEach { visit(it.instructions) }
        instructions.replacements.forEach { replacement -> replacement.instead?.let { visit(it) } }
    }
    visit(this)
    return found
}

private fun Instructions.effects(): List<Effect> = reachableSteps().mapNotNull { (it as? Step.Do)?.effect }

private fun Instructions.couldAddMana(): Boolean = effects().any {
    it is Effect.AddMana || (it is Effect.Action && (it.action as? KeywordAction.Double)?.what is Doubled.Mana)
}

private fun Instructions.movesLibraryCard(): Boolean {
    val selfReplacements = replacements + modes?.options.orEmpty().flatMap { it.instructions.replacements }
    return effects().any { it.movesLibraryCard() } ||
        selfReplacements.any { r -> r.modify.any { it is Modification.ChangeDestination && it.to.zone == Zone.Library } }
}

private fun Effect.movesLibraryCard(): Boolean = when (this) {
    is Effect.Draw, is Effect.Mill, is Effect.Surveil, is Effect.Search, is Effect.RevealUntil -> true
    is Effect.Move -> to.zone == Zone.Library || objects is Objects.LibraryCards
    is Effect.Action -> action.movesLibraryCard()
    else -> false
}

private fun KeywordAction.movesLibraryCard(): Boolean = when (this) {
    is KeywordAction.Explore, is KeywordAction.Manifest, is KeywordAction.Cloak, is KeywordAction.Connive,
    is KeywordAction.Discover -> true
    else -> false
}

private fun Cost.movesLibraryCard(): Boolean = parts.any { part ->
    when (part) {
        is CostPart.Mill -> true
        is CostPart.Exile -> part.from == Zone.Library
        is CostPart.OneOf -> part.options.any { it.movesLibraryCard() }
        is CostPart.Action -> part.action.movesLibraryCard()
        else -> false
    }
}

private fun Trigger.isManaTrigger(): Boolean =
    this is Trigger.On && events.all { it is EventPattern.Tap && it.forMana == true }
