package arcanum.engine.casting

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.view.View
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [Engine.refusal] gives [Engine.perform]'s own refusal of a land play, cast or activation without performing it: on
 * seeded random games of cube cards, every hand card's cast or land play and every activated ability and special action
 * of every object, for each player, is asked both ways. A reason given is word for word the one `perform` throws; none
 * given, `perform` begins it (or refuses it later, while it is announced, never with one of the checks made before
 * anything is done). With or without the state's view, the same reason.
 */
class RefusalTest {
    private var refused = 0
    private var begun = 0

    @Test
    fun theRefusalIsPerformsOwnWithoutPerforming() {
        repeat(GAMES) { game(SEED + it) }
        assertTrue(refused > 0 && begun > 0, "both kinds were tried: $refused refused, $begun begun")
    }

    // CR 117.1: a spell cast without priority is refused before anything is done, with the reason perform gives; other
    // inputs have none
    @Test
    fun aCastWithoutPriorityIsRefusedWithPerformsReason() = scenario {
        alice.hand("Storm Spark")
        bob.hand("Storm Spark")
        val input = Input.Cast(bob.id, state.player(bob.id).hand.single())
        val reason = Engine.refusal(state, input)
        assertEquals(runCatching { Engine.perform(state, input) }.exceptionOrNull()?.message, reason)
        assertTrue(reason != null && reason.startsWith("Can't cast that: "), "$reason")
        assertNull(Engine.refusal(state, Input.PassPriority(alice.id)), "only inputs that begin something")
    }

    private fun game(seed: Long) = scenario(seed = seed) {
        val rnd = Random(seed)
        val pool = Cards.cubeNames
        players.forEach { p ->
            p.battlefield(*(List(4) { BASICS.random(rnd) } + List(3) { pool.random(rnd) }).toTypedArray())
            p.hand(*List(5) { pool.random(rnd) }.toTypedArray())
            p.library(*List(12) { (pool + BASICS).random(rnd) }.toTypedArray())
        }
        repeat(STEPS) {
            if (Engine.result(state) != null) return@scenario
            val view = Engine.view(state)
            for (p in players) tried(state, view, p.id).forEach { check(state, view, it) }
            val listed = players.flatMap { p -> Engine.options(state, p.id) }.distinct()
            if (listed.isEmpty()) return@scenario
            val doing = listed.filterNot { it is Input.PassPriority }
            perform(if (doing.isNotEmpty() && rnd.nextInt(3) > 0) doing.random(rnd) else listed.random(rnd))
        }
    }

    /** What a host's card menus ask about for [player]: their hand cards' casts and land plays, every object's activated
     * abilities and special actions. */
    private fun tried(state: GameState, view: View, player: PlayerId): List<Input> = buildList {
        for (o in state.objects.values) {
            val characteristics = view[o.id]?.characteristics ?: continue
            if (o.zone == Zone.Hand && o.owner == player)
                add(if (CardType.Land in characteristics.types) Input.PlayLand(player, o.id) else Input.Cast(player, o.id))
            for (instance in characteristics.abilities)
                if (instance.ability is Ability.Activated || instance.ability is Ability.SpecialAction) add(Input.Activate(player, o.id, instance.origin))
        }
    }

    private fun check(state: GameState, view: View, input: Input) {
        val reason = Engine.refusal(state, input, view)
        assertEquals(reason, Engine.refusal(state, input), "with and without the view: $input")
        val thrown = try { Engine.perform(state, input); null } catch (e: IllegalArgumentException) { e.message.orEmpty() }
            catch (e: IllegalStateException) { "(state) ${e.message}" }
        if (reason != null) {
            refused++
            assertEquals(reason, thrown, "perform's refusal: $input")
        } else {
            begun++
            if (thrown != null) assertFalse(UP_FRONT.any { thrown.startsWith(it) } || " has no ability " in thrown,
                "refused before anything was done, but no reason was given: $input: $thrown")
        }
    }

    private companion object {
        const val GAMES = 3
        const val STEPS = 30
        const val SEED = 4_400L
        val BASICS = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")
        val UP_FRONT = listOf("Can't play that land: ", "Can't cast that: ", "Can't activate that: ", "Waiting for ", "The game is over")
    }
}
