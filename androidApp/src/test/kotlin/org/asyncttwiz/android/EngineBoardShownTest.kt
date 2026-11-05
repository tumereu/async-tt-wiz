package org.asyncttwiz.android

import arcanum.api.CounterKind
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the table shows of a game played on engine state (R4b's replacements of the old model's board tests): the boards
 * Alice's and Bob's games publish ([EngineGame.play]) on the simulated server ([EngineTableKit]), their scenes and the
 * values the engine thread works out with them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class EngineBoardShownTest {
    @After fun forget() = AutomatumBoards.reset()

    /** A game loaded on [viewer]'s device from the server's head of [kit]'s game with its state changed by [edit] (both
     * local heads there), as a device loads a head. */
    private fun edited(kit: EngineTableKit, viewer: String, edit: (GameState) -> Unit): EngineGame {
        val sim = kit.scenario.sim
        val model = sim.state()
        edit(model.engine)
        val snapshot = EngineSnapshot(kit.f.codec.encodeDefinitions(), sim.store.rootId, sim.head, kit.f.codec.encodeModel(model),
            sim.heads.mapValues { sim.head })
        return EngineGame(kit.f.game.id, viewer, kit.f.base, snapshot)
    }

    private fun battlefieldObject(s: GameState, card: String): ObjectId =
        s.battlefield.first { (it as ObjectId.Card).card.value == card }

    // TBL-018: real cards are drawn as cards, tokens with their own outline, and Stack copies dashed (projections)
    @Test fun cardsTokensAndCopiesLookDifferent() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(heralds = true)))
        kit.opening()
        kit.playLand()
        val herald = kit.atw(kit.hand("Ember Herald").first())
        assertTrue(kit.toolkit.cast.start(herald), "${kit.toolkit.error}")
        val copy = kit.scene().items.filter { it.kind == TableItemKind.CARD && it.zone == PlayZone.STACK && it.sourceId == herald }
        assertTrue(copy.isNotEmpty() && copy.all { it.projection }, "its Stack copy is dashed: $copy")
        kit.playOn("the Herald and its token on the battlefield") {
            if (kit.alice.device.status == GameDevice.Status.YourMove && kit.state().stack.isNotEmpty() &&
                kit.state().priority.holder == kit.me) kit.alice.pass()
            kit.state().battlefield.any { kit.state().obj(it)?.token == true } && kit.state().stack.isEmpty()
        }
        val items = kit.scene().items.filter { it.kind == TableItemKind.CARD && it.zone == PlayZone.BATTLEFIELD }
        val real = items.single { it.sourceId == herald }
        assertFalse(real.token); assertFalse(real.projection)
        val token = kit.state().battlefield.single { kit.state().obj(it)?.token == true }.let(kit::atw)
        assertTrue(items.single { it.sourceId == token }.token, "a token has the token outline")
    }

    // TBL-097: a card's owner is whose deck it came from, its controller the battlefield it is on
    @Test fun aCardKeepsItsOwnerOnTheOtherPlayersBattlefield() {
        val kit = EngineTableKit()
        kit.opening()
        val mountain = kit.playLand()
        // Alice controls a Mountain Bob owns (as a card that gains control of a permanent leaves it)
        val game = edited(kit, kit.f.a) { s ->
            val id = battlefieldObject(s, mountain)
            s.objects[id] = s.objects.getValue(id).copy(owner = kit.other, controller = kit.me)
        }
        val play = game.play()
        assertEquals(kit.f.b, play.board.cards.single { it.id == mountain }.owner, "owned by Bob")
        val scene = TablePresentation.scene(kit.f.game, play, kit.f.a, 412f, 892f, false)
        val item = scene.items.single { it.cardId == mountain && it.kind == TableItemKind.CARD }
        assertEquals(kit.f.b, item.owner, "drawn in Bob's colour")
        val field = play.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == kit.f.a }
        assertTrue(mountain in field.cards, "on Alice's battlefield, which she controls")
        assertEquals(field.id, scene.fields.single { it.owner == kit.f.a }.containerId)
    }

    // TBL-113, TBL-112: what Automatum changed on a battlefield card besides P/T is its chip's marks: an Ember Cub with two
    // +1/+1 counters shows "+1/+1 ×2" (and 4/3 in its corner)
    @Test fun aCardsCountersAreItsMarks() {
        val kit = EngineTableKit()
        kit.opening()
        kit.playLand()
        val cub = kit.castCub()
        val game = edited(kit, kit.f.a) { s ->
            val id = battlefieldObject(s, cub)
            s.objects[id] = s.objects.getValue(id).copy(counters = listOf(CounterEntry(CounterKind.PowerToughness(1, 1), 2, 1)))
        }
        val computed = assertNotNull(game.play().values).computed.cards.getValue(cub)
        assertEquals("4/3", computed.corner)
        assertEquals(listOf("+1/+1 ×2"), computed.marks)
    }

    // SYS-037: the only differences between the players' tables: what fits the acting player, and hidden information the
    // other player owns. At the same point both see the same battlefield, life and turn; each sees their own hand only
    @Test fun bothPlayersSeeTheSameTableButTheOthersHand() {
        val kit = EngineTableKit()
        kit.opening()
        kit.playLand()
        kit.settle()
        assertEquals(kit.alice.device.session.tip, kit.bob.device.session.tip, "both at the same point")
        val alice = kit.alice.play().board
        val bob = kit.bob.play().board
        assertEquals(alice.turn, bob.turn)
        assertEquals(alice.players.map { it.accountId to it.life }, bob.players.map { it.accountId to it.life })
        fun field(board: org.asyncttwiz.protocol.BoardState) = board.containers.filter { it.zone == PlayZone.BATTLEFIELD }
            .associate { it.accountId to it.cards.map { id -> board.cards.single { c -> c.id == id }.let { c -> c.id to c.tapped } } }
        assertEquals(field(alice), field(bob))
        val aliceHand = alice.containers.single { it.zone == PlayZone.HAND && it.accountId == kit.f.a }.cards
        assertTrue(aliceHand.none { id -> alice.cards.single { it.id == id }.covered }, "Alice sees her hand")
        assertTrue(aliceHand.all { id -> bob.cards.single { it.id == id }.covered }, "Bob doesn't")
    }

    // STK-042, SYS-025: a cast shows on Alice's device at once (its Stack entry, its Mountain tapped) before the server
    // has it; refused by the server, the table goes back as it was
    @Test fun aCastShowsAtOnceAndGoesBackWhenRefused() {
        val kit = EngineTableKit()
        kit.opening()
        val mountain = kit.playLand()
        val cub = kit.atw(kit.hand("Ember Cub").first())
        val head = kit.scenario.sim.head
        assertTrue(kit.toolkit.cast.start(cub), "${kit.toolkit.error}")
        val shown = kit.alice.play().board
        assertTrue(shown.stackEffects.any { it.sourceId == cub }, "its Stack entry")
        assertTrue(shown.cards.single { it.id == mountain }.tapped, "its Mountain tapped")
        assertEquals(head, kit.scenario.sim.head, "the server doesn't have it yet")
        val cast = kit.alice.take(kit.scenario.sim.now).filterIsInstance<GameAction.Submit>()
        assertTrue(cast.isNotEmpty(), "the cast's step goes to the server")
        kit.alice.refused(cast.first(), "inapplicable")
        val back = kit.alice.play().board
        assertTrue(back.stackEffects.none { it.sourceId == cub }, "the table goes back")
        assertFalse(back.cards.single { it.id == mountain }.tapped)
        assertTrue(cub in back.containers.single { it.zone == PlayZone.HAND && it.accountId == kit.f.a }.cards)
    }

    // TRN-028, TRN-062, TRN-066, TRN-070: Alice's turn-1 controls wait while Bob (online) is still deciding on a mulligan:
    // no step arrow and nothing in its place (his keep is never assumed, TRN-027); once he keeps she plays her first turn
    // TRN-004: the step arrow shows only while the viewer may act
    @Test fun theStartingPlayersControlsWaitForTheOtherKeep() {
        val kit = EngineTableKit()
        kit.alice.tick(true, 1_000)
        assertTrue(kit.toolkit.opening(TableActionIntent.Keep(kit.scope)), "Alice keeps")
        kit.alice.tick(true, 2_000)
        val waiting = assertIs<GameDevice.Status.Waiting>(kit.alice.device.status)
        assertEquals(GameDevice.Wait.Keep, waiting.reason)
        assertEquals(null, waiting.offer)
        assertFalse(kit.routes.arrowShown(), "no step arrow")
        assertEquals(null, kit.routes.waitAction(), "no Assume keep")
        // Long after Bob left: still his keep to make
        kit.alice.tick(false, 2_000 + 10 * GameDevice.GRACE_MS)
        assertEquals(GameDevice.Status.Waiting(GameDevice.Wait.Keep), kit.alice.device.status)
        assertFalse(kit.routes.canAct())
        kit.bob.keep(emptyList())
        kit.playOn("Alice's Main 1") { kit.aliceMain() }
        assertTrue(kit.routes.arrowShown())
    }

    // TRN-023, TRN-062, HIS-083: a quick action is one Undo step however many changes it makes: Alice's Mulligan (her hand
    // back, a shuffle, seven drawn, its count) is undone by one Undo (a request: it showed her new cards, I-D56 §10)
    @Test fun aMulliganIsOneUndo() {
        val kit = EngineTableKit()
        // Bob kept first, so Alice's Mulligan is made at once (the mulligans are simultaneous)
        kit.bob.keep(emptyList())
        kit.settle(bobActs = false)
        kit.scenario.online[kit.f.b] = false
        fun hand() = kit.state().player(kit.me).hand.toList()
        val seven = hand()
        assertTrue(kit.toolkit.opening(TableActionIntent.OpeningMulligan(kit.scope)), "${kit.toolkit.error}")
        kit.settle(bobActs = false)
        assertNotEquals(seven, hand())
        val undo = assertIs<GameDevice.Undo.Request>(kit.alice.undo(), "one Undo")
        kit.alice.requestUndo(undo.target, "")
        kit.scenario.online[kit.f.b] = true
        kit.settle(bobActs = false)
        kit.bob.answerRequest(true)
        kit.settle(bobActs = false)
        assertEquals(seven, hand(), "the hand she had")
        assertEquals(0, kit.alice.play().board.players.single { it.accountId == kit.f.a }.mulligans)
    }
}
