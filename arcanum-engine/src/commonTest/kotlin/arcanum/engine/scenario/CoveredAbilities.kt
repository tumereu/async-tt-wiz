package arcanum.engine.scenario

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Keyword
import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Meaning
import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.view.AbilityOrigin

/** How a scenario met an ability, for `:arcanum-tools:coveredShapes`. */
internal sealed interface Coverage {
    val code: String
    /** It did its work: a spell or ability that resolved, or a mana ability activated. */
    data object Exercised : Coverage { override val code = "exercised" }
    /** Its card was on the battlefield or in a hand when the moves began; whether it mattered can't be told. */
    data object Present : Coverage { override val code = "present" }
}

/** The sink of a recording run; null in a normal test run. */
internal expect fun coveredAbilitySink(): ((Coverage, Ability) -> Unit)?

/** Notes what this scenario, which has passed, covered. Costs one null check in a normal test run. */
internal fun Scenario.recordCoveredAbilities() {
    val sink = coveredAbilitySink() ?: return
    val start = startState
    val definitions = HashMap<CardId, CardDefinition>()
    for (state in listOf(start, state)) for (o in state.objects.values) {
        val card = (o.id as? ObjectId.Card)?.card ?: continue
        definitions[card] = definitionOf(o)
    }
    start.objects.values.filter { it.zone == Zone.Battlefield || it.zone == Zone.Hand }.forEach { o ->
        definitionOf(o).faces.flatMap { it.abilities }.forEach { sink(Coverage.Present, it) }
    }
    /** The ability [key] of `AbilityOrigin.key` names on [source]: a printed one, or the keyword it is an expansion of. */
    fun printed(source: ObjectId?, key: String): Ability? {
        val at = PRINTED.matchEntire(key) ?: return null
        val definition = definitions[(source as? ObjectId.Card)?.card ?: return null] ?: return null
        return definition.faces.getOrNull(at.groupValues[1].toInt())?.abilities?.getOrNull(at.groupValues[2].toInt())
    }
    for (change in applied) when {
        change is Change.StackItemRemoved && Meaning.Resolved in change.meanings -> {
            change.item.ability?.let { sink(Coverage.Exercised, it) }
            val origin = change.item.origin
            if (origin is AbilityOrigin.Expansion) printed(change.item.source, origin.keyword.key)?.takeIf { it is Keyword }?.let { sink(Coverage.Exercised, it) }
        }
        change is Change.Happened -> (change.event as? GameEvent.Activated)?.takeIf { it.manaAbility }?.let { event ->
            printed(event.source, event.ability.substringBefore("/x"))?.let { sink(Coverage.Exercised, it) }
        }
    }
}

private val PRINTED = Regex("""p(\d+)\.(\d+)""")
