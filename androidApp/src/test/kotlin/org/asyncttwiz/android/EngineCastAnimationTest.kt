package org.asyncttwiz.android

import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.MtgStackKind
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-04: casting a creature made things flicker and the creature didn't fly. The boards the engine
 * game publishes for a cast and what follows it by itself (the passes, the spell resolving, its enters trigger and the
 * token it makes) are drawn as the table draws them: through [EngineMotionQueue] (each step once its animation settles)
 * and [HistorySceneAnimation] (the card motion between two drawn boards).
 */
class EngineCastAnimationTest {
    private val f = EngineGameFixture(heralds = true)

    /** Inline, but an own action is released at its performed point as on the engine thread (SYS-025). */
    private object EarlyDirect : EngineWorker {
        override fun run(work: () -> Unit) = work()
        override fun <T> call(work: () -> T): T = work()
        override fun main(work: () -> Unit) = work()
        override fun early(work: (release: (() -> Unit)?) -> Unit) = work {}
    }

    /** What the player follows on a drawn board: where the Herald is, the Stack, the tokens and the tapped lands. */
    private data class Look(val herald: String, val stack: List<String>, val tokens: Int, val tapped: Int)

    private fun look(play: TableBoard, herald: String): Look {
        val b = play.board
        val card = b.cards.single { it.id == herald }
        val zone = b.containers.single { it.id == card.containerId }.zone
        val spell = b.stackEffects.any { it.sourceId == herald && it.item?.kind == MtgStackKind.Spell }
        return Look(if (spell) "STACK" else zone.name,
            b.stackEffects.map { if (it.item?.kind == MtgStackKind.Spell) "spell" else "trigger" },
            b.cards.count { it.token && b.containers.single { c -> c.id == it.containerId }.zone == PlayZone.BATTLEFIELD },
            b.cards.count { it.tapped })
    }

    private fun scene(play: TableBoard) = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false)

    // TBL-125, STK-006, STK-045 (owner playtest 2026-11-04): one cast plays as the player can follow it: the card flies from
    // the hand to the Stack, then from the Stack to its place, then the trigger's token flies out to its place, one after
    // another in event order; the Herald is drawn exactly once all the way (no ghost left in the hand, no copy popping in)
    @Test fun aCastCreatureAndItsTriggerPlayInEventOrder() {
        val published = mutableListOf<EngineGame.Shown>()
        var open = false
        lateinit var game: EngineGame
        game = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true), worker = EarlyDirect, onShown = { if (open) published += game.shown!! })
        open = true
        game.tick(live = false, now = 1_000)
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        game.tick(live = false, now = 2_000)
        val me = PlayerId(f.a)
        val hand = game.read { d -> d.model.engine.player(me).hand.toList() }
        assertTrue(hand.any { game.act(Input.PlayLand(me, it)).let { a -> a is GameDevice.Acted.Sent || a == GameDevice.Acted.Made } })
        val s = game.read { it.model.engine.deepCopy() }
        val cast = Engine.options(s, me).filterIsInstance<Input.Cast>().first { c ->
            s.objects[c.card]?.definition?.faces?.firstOrNull()?.name == "Ember Herald" }
        val inputs = mutableListOf<Input>(cast)
        val copy = s.deepCopy()
        var input: Input = cast
        while (true) {
            Engine.perform(copy, input).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: break
            input = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion)) else break
            inputs += input
        }
        val herald = (cast.card as ObjectId.Card).card.value
        val before = assertNotNull(game.shown)
        published.clear()
        game.send(inputs)
        // The trigger resolves (the player's own pass when their mode keeps priority for it)
        repeat(3) { if (game.shown!!.engine.stack.isNotEmpty() && game.shown!!.status == GameDevice.Status.YourMove) game.pass() }
        assertTrue(game.shown!!.engine.stack.isEmpty(), "the trigger resolved")

        // The table: each published board through the motion queue, every step drawn until its animation settles; on a
        // phone the cast's own board may be replaced by its outcome before a frame draws it
        for (previewDrawn in listOf(true, false)) check(published.filter { previewDrawn || it !== published.first() }, before, herald)
    }

    private fun check(published: List<EngineGame.Shown>, before: EngineGame.Shown, herald: String) {
        val queue = EngineMotionQueue()
        val drawn = mutableListOf(queue.board(before.version, before.transition, before.board))
        for (shown in published) {
            drawn += queue.board(shown.version, shown.transition, shown.board)
            while (queue.playing) { queue.settled(); drawn += queue.board(shown.version, shown.transition, shown.board) }
        }
        val boards = drawn.fold(mutableListOf<TableBoard>()) { kept, b ->
            if (kept.isEmpty() || EngineMotion.key(kept.last().board) != EngineMotion.key(b.board)) kept += b; kept }
        // Later steps of the other player's turn (their draw) look the same here
        val looks = boards.map { look(it, herald) }
        assertTrue(looks.drop(4).all { it == looks[3] }, "then only the other player's turn: $looks")
        assertEquals(listOf(
            Look("HAND", emptyList(), 0, 0),
            Look("STACK", listOf("spell"), 0, 1),
            Look("BATTLEFIELD", listOf("trigger"), 0, 1),
            Look("BATTLEFIELD", emptyList(), 1, 1),
        ), looks.take(4), "hand → Stack → its place with its trigger → the token, each shown in turn")

        val spellEntries = boards.flatMap { b -> b.board.stackEffects.filter { it.item?.kind == MtgStackKind.Spell }.map { it.id } }.toSet()
        for (i in 0 until 3) {
            val from = scene(boards[i]); val to = scene(boards[i + 1])
            val animation = HistorySceneAnimation(from)
            assertTrue(animation.retarget(to, 1f), "step $i moves something")
            // Never hidden, never twice: the Herald (its card or its spell's entry) is drawn once at every moment
            for (p in listOf(0f, .1f, .25f, .5f, .75f, .9f, 1f)) {
                val drawings = animation.frame(p).filter { it.alpha > 0f && it.item.kind == TableItemKind.CARD &&
                    (it.item.cardId == herald && !it.item.projection || it.item.cardId in spellEntries) }
                assertEquals(1, drawings.size, "step $i at $p: the Herald drawn once, not ${drawings.map { it.item.id }}")
            }
            when (i) {
                0 -> { // Hand → Stack: the entry takes off from the hand card's place
                    val start = animation.frame(0f).single { it.item.cardId in spellEntries }
                    assertEquals(from.items.single { it.cardId == herald && it.zone == PlayZone.HAND }.bounds, start.bounds)
                }
                1 -> { // Stack → its place: the card itself flies from the entry's place to its battlefield place
                    val entry = from.items.single { it.cardId in spellEntries }
                    val start = animation.frame(0f).single { it.item.cardId == herald && !it.item.projection }
                    assertEquals(entry.bounds.center, start.bounds.center)
                    val place = to.items.single { it.cardId == herald && it.zone == PlayZone.BATTLEFIELD }
                    assertEquals(place.bounds, animation.frame(1f).single { it.item.cardId == herald && !it.item.projection }.bounds)
                    assertTrue(animation.frame(.5f).single { it.item.cardId == herald && !it.item.projection }.item.inTransit)
                }
                2 -> { // The trigger resolves: its token flies out of the trigger's entry to its place
                    val trigger = from.stackProjections.single { it.cardId !in spellEntries }
                    val token = to.items.single { it.token && it.zone == PlayZone.BATTLEFIELD }
                    assertEquals(trigger.bounds, animation.frame(0f).single { it.item.id == token.id }.bounds)
                    assertEquals(token.bounds, animation.frame(1f).single { it.item.id == token.id }.bounds)
                }
            }
        }
    }

    // TBL-125 (owner playtest 2026-11-04): a cast publishes its own board at once and then one more with all that followed;
    // the panels (whose move, the waiting bar) change once, never to an in-between state
    @Test fun aCastShowsItsOutcomeWithoutAnInBetweenWait() {
        val published = mutableListOf<EngineGame.Shown>()
        var open = false
        lateinit var game: EngineGame
        game = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true), worker = EarlyDirect, onShown = { if (open) published += game.shown!! })
        open = true
        game.tick(live = false, now = 1_000)
        game.keep(emptyList())
        game.tick(live = false, now = 2_000)
        val me = PlayerId(f.a)
        val hand = game.read { d -> d.model.engine.player(me).hand.toList() }
        assertTrue(hand.any { game.act(Input.PlayLand(me, it)).let { a -> a is GameDevice.Acted.Sent || a == GameDevice.Acted.Made } })
        published.clear()
        val s = game.read { it.model.engine.deepCopy() }
        val cast = Engine.options(s, me).filterIsInstance<Input.Cast>().first { c ->
            s.objects[c.card]?.definition?.faces?.firstOrNull()?.name == "Ember Herald" }
        val d = s.deepCopy().also { Engine.perform(it, cast).changes.forEach(it::apply) }.pending?.decision as Decision.ManaPayment
        game.send(listOf(cast, Input.Answer(d.id, Answer.ManaPayment(d.suggestion))))
        assertEquals(2, published.size, "the cast at once, then its outcome: ${published.map { it.status }}")
        assertEquals(GameDevice.Status.YourMove, published.first().status, "the cast's own board keeps the panels as they were")
        assertEquals(1, published.first().engine.stack.size, "and shows the spell on the Stack")
        assertTrue(published.last().transition!!.steps.isNotEmpty(), "its outcome plays step by step")
    }
}
