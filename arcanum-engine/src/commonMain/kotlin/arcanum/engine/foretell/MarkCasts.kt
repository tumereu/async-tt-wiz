package arcanum.engine.foretell

import arcanum.api.Cost
import arcanum.api.HasCost
import arcanum.api.Keyword
import arcanum.api.KeywordKind
import arcanum.api.MarkCost
import arcanum.api.MarkPermission
import arcanum.api.MarkRule
import arcanum.api.MarkRules
import arcanum.api.Mechanic
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Timing
import arcanum.api.Zone
import arcanum.engine.prepare.Prepare
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.AppliedMechanic

/**
 * The one path of what marks let be cast from exile ([MarkRule.permission]): a card that gained a mark may be cast by its owner after that
 * turn, as the table says (foretold, CR 702.143a; plotted, 702.170d, even an instant and even if it no longer has plot), and a prepared
 * permanent's controller may cast the copy of its prepare spell (CR 722.3c). Each card's permission has the origin
 * [AbilityOrigin.Rule] of the mark's name, with the cost's place after a colon from the second on, so each cost is its own way to cast it.
 */
internal object MarkCasts {
    private const val COST_MARK = ":"
    private val CAST_COPY: Mechanic = Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You)

    /** What marks let be cast from exile now, by card. [controllerOf]: who controls a permanent, as the layers have it; [abilitiesOf]: the
     * abilities an object has now, as the layers have it (foretell given to a card already in exile, CR 702.143a: any foretell cost it has). */
    fun permissions(
        state: GameState, controllerOf: (ObjectId) -> PlayerId?, abilitiesOf: (ObjectId) -> List<arcanum.api.Ability> = { emptyList() },
    ): Map<ObjectId, List<AppliedMechanic>> = state.exile.mapNotNull { id ->
        val o = state.obj(id) ?: return@mapNotNull null
        val own = o.marks.flatMap { mark -> o.facts.gained(mark).let { ownPermissions(state, o, MarkRules.of(mark), it?.turn, it?.costs.orEmpty(), abilitiesOf(id)) } }
        val all = copyPermission(state, o, controllerOf) + own
        if (all.isEmpty()) null else id to all
    }.toMap()

    /** Whether [origin] is a permission a mark gives that holds the spell to sorcery timing (a plotted card, CR 702.170d). */
    fun asSorcery(origin: AbilityOrigin?): Boolean {
        val name = (origin as? AbilityOrigin.Rule)?.name?.substringBefore(COST_MARK) ?: return false
        return (MarkRules.named(name)?.permission as? MarkPermission.CastCard)?.timing == Timing.Sorcery
    }

    /** CR 722.3c: a copy kept in exile may be cast by its permanent's controller. */
    private fun copyPermission(state: GameState, o: GameObject, controllerOf: (ObjectId) -> PlayerId?): List<AppliedMechanic> {
        if (!Prepare.keptInExile(state, o)) return emptyList()
        val permanent = state.obj(o.prepareSpellOf!!)!!
        return listOf(AppliedMechanic(CAST_COPY, controllerOf(permanent.id) ?: permanent.owner, permanent.timestamp, source = permanent.id))
    }

    /** The permissions [rule]'s mark gives [o] itself: after the turn it was gained in; [recorded]: the costs kept as it was gained; [now]:
     * the abilities it has now, whose costs of the keyword count too. */
    private fun ownPermissions(state: GameState, o: GameObject, rule: MarkRule, turn: Int?, recorded: List<Cost>, now: List<arcanum.api.Ability>): List<AppliedMechanic> {
        val permission = rule.permission as? MarkPermission.CastCard ?: return emptyList()
        if (turn == null || turn >= state.turn.number) return emptyList()
        val mechanics = when (val cost = permission.cost) {
            MarkCost.Free -> listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, withoutPaying = true, holder = PlayerFilter.You))
            is MarkCost.OfKeyword -> (recorded + printedCosts(o, cost.keyword) + costsOf(now, cost.keyword)).distinct()
                .map { Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, cost = it, holder = PlayerFilter.You) }
        }
        return mechanics.mapIndexed { i, mechanic ->
            AppliedMechanic(mechanic, o.owner, o.timestamp, source = o.id, origin = AbilityOrigin.Rule(if (i == 0) rule.name else "${rule.name}$COST_MARK$i"))
        }
    }

    /** The costs of [keyword] printed on [o]'s card, read through its definition as a face-down card shows none (CR 702.143a). */
    private fun printedCosts(o: GameObject, keyword: KeywordKind) = costsOf(o.definition.faces.firstOrNull()?.abilities.orEmpty(), keyword)

    private fun costsOf(abilities: List<arcanum.api.Ability>, keyword: KeywordKind) =
        abilities.filterIsInstance<Keyword>().filter { it.kind == keyword }.filterIsInstance<HasCost>().map { it.cost }
}
