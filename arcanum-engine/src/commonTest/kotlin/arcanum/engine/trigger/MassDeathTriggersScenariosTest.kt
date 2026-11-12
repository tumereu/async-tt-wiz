package arcanum.engine.trigger

import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.state.ObjectId
import arcanum.engine.state.StackItemKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Commander readiness (owner, 2026-11-11): a board wipe killing about 30 creatures with death triggers on the battlefield,
 * copies of such triggers and doubled ones, with two and four players, resolve correctly. Their speed is printed by
 * `MassDeathTriggersBenchmarkJvmTest` (never asserted: the owner's rule is no timing tests).
 */
class MassDeathTriggersScenariosTest {
    /** Each death makes each opponent lose 1 and its controller gain 1 (no target, no decision). */
    private val ledger = MassTriggerCards.ledger
    /** Each death: target player loses 1, its controller gains 1 (one target decision each, as Blood Artist). */
    private val bloodLedger = MassTriggerCards.bloodLedger
    private val wipe = MassTriggerCards.wipe
    private val pup = MassTriggerCards.pup
    private val echo = MassTriggerCards.echo
    private val mirror = MassTriggerCards.mirror

    private fun ScenarioPlayer.pups(n: Int) = repeat(n) { battlefield(pup) }

    /** Alice casts the wipe and every player passes: it resolves and the triggers wait or go on the Stack. */
    private fun Scenario.wipeResolves() {
        alice.casts(wipe)
        allPass()
    }

    /** Every player passes until the Stack is empty, choosing [target] whenever a trigger asks for one. */
    private fun Scenario.resolveAll(target: String = "bob") {
        var guard = 2_000
        while (state.stack.isNotEmpty() && guard-- > 0) {
            if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(target)))) else passPriority()
        }
        assertTrue(guard > 0, "the Stack never emptied")
    }

    // CR 700.4, 603.2, 603.3b, 603.10a: 30 creatures dying at once trigger "whenever a creature dies" 30 times; one source,
    // one ability: put on the Stack without asking for an order (the engine's P5), each opponent loses 1 per trigger
    @Test
    fun thirtyDeathsTriggerThirtyTimes() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", ledger)
        alice.hand(wipe)
        alice.pups(15)
        bob.pups(15)
        alice.life = 40
        bob.life = 40
        wipeResolves()
        expect {
            stack.size shouldBe 30
            alice.graveyard.size shouldBe 16
            bob.graveyard.size shouldBe 15
        }
        assertTrue(state.stack.all { state.stackItems[it]?.kind == StackItemKind.Triggered && state.stackItems[it]?.controller == alice.id })
        resolveAll()
        expect {
            bob.life shouldBe 10
            alice.life shouldBe 70
            stack.isEmpty()
        }
    }

    // CR 603.3d: a trigger's target is chosen as it is put on the Stack: 30 target choices (Blood Artist's case)
    @Test
    fun thirtyTargetedTriggersAskThirtyTargets() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bloodLedger)
        alice.hand(wipe)
        alice.pups(15)
        bob.pups(15)
        alice.life = 40
        bob.life = 40
        wipeResolves()
        var asked = 0
        while (pending is Decision.Targets) { asked++; answer(Answer.Targets(listOf(target("bob")))) }
        assertEquals(30, asked, "one target per trigger")
        expect { stack.size shouldBe 30 }
        resolveAll()
        expect {
            bob.life shouldBe 10
            alice.life shouldBe 70
        }
    }

    // CR 603.3b: two different sources' triggers: their controller orders all 60 (one decision), then targets each
    @Test
    fun sixtyTriggersOfTwoSourcesAreOrderedOnce() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", ledger, bloodLedger)
        alice.hand(wipe)
        alice.pups(15)
        bob.pups(15)
        alice.life = 40
        bob.life = 100
        wipeResolves()
        val order = pending as? Decision.OrderTriggers ?: error("expected the order of the triggers, got $pending")
        assertEquals(60, order.triggers.size)
        answer(Answer.Order(order.triggers.indices.reversed().toList()))
        var asked = 0
        while (pending is Decision.Targets) { asked++; answer(Answer.Targets(listOf(target("bob")))) }
        assertEquals(30, asked)
        expect { stack.size shouldBe 60 }
        resolveAll()
        expect {
            bob.life shouldBe 40
            alice.life shouldBe 100
        }
    }

    // CR 603.2d: "triggers an additional time": 30 deaths, 60 triggers
    @Test
    fun aDoublerMakesSixty() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", ledger, echo)
        alice.hand(wipe)
        alice.pups(15)
        bob.pups(15)
        alice.life = 40
        bob.life = 80
        wipeResolves()
        expect { stack.size shouldBe 60 }
        resolveAll()
        expect {
            bob.life shouldBe 20
            alice.life shouldBe 100
        }
    }

    // CR 707.10, 602.2: copying one of the 30 triggers on the Stack makes 31 resolve
    @Test
    fun aCopiedTriggerAmongThirty() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", ledger, mirror)
        alice.hand(wipe)
        alice.pups(15)
        bob.pups(15)
        alice.life = 40
        bob.life = 40
        wipeResolves()
        expect { stack.size shouldBe 30 }
        val top = state.stack.last()
        copies(alice, top)
        expect { stack.size shouldBe 31 }
        allPass()
        expect { stack.size shouldBe 31 }
        resolveAll()
        expect {
            bob.life shouldBe 9
            alice.life shouldBe 71
        }
    }

    // CR 603.3b, 101.4: four players each with a Ledger: 32 deaths trigger each of them 32 times (128 triggers), put on
    // the Stack in APNAP order (the active player's first, so the last player's resolve first); each opponent loses 1 per
    // trigger: each player loses 96 and gains 32
    @Test
    fun fourPlayersApnap() = scenario(players = 4) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(wipe)
        for (p in players) {
            p.battlefield(ledger)
            p.pups(8)
            p.life = 200
        }
        wipeResolves()
        expect { stack.size shouldBe 128 }
        val controllers = state.stack.map { state.stackItems[it]?.controller }
        assertEquals(players.flatMap { p -> List(32) { p.id } }, controllers, "APNAP: Alice's at the bottom, Dave's on top")
        resolveAll()
        for (p in players) assertEquals(136, p.life, "${p.id}'s life")
        expect { stack.isEmpty() }
    }

    // CR 603.2d, 707.10 with four players: a doubler of Bob's doubles only his own triggers, and a copy of his
    @Test
    fun fourPlayersWithADoublerAndACopy() = scenario(players = 4) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(wipe)
        bob.battlefield("Plains", mirror, echo)
        for (p in players) {
            p.battlefield(ledger)
            p.pups(7)
            p.life = 300
        }
        // 28 deaths (7 pups each, the Ledgers aren't creatures): 28 triggers each, Bob's twice: 140
        wipeResolves()
        expect { stack.size shouldBe 28 * 3 + 56 }
        val bobs = state.stack.filter { state.stackItems[it]?.controller == bob.id }
        assertEquals(56, bobs.size)
        // Dave's are on top: Bob's priority comes when Alice and the others pass; Bob copies one of his own when he can
        while (state.priority.holder != bob.id) passPriority()
        copies(bob, bobs.last())
        resolveAll()
        // Each player loses 1 per trigger of each opponent: Alice, Carol and Dave 28 each, Bob 57 (56 and the copy)
        val gained = mapOf(alice to 28, bob to 57, carol to 28, dave to 28)
        for (p in players) {
            val lost = gained.filterKeys { it != p }.values.sum()
            assertEquals(300 - lost + gained.getValue(p), p.life, "${p.id}'s life")
        }
    }

    /** [player] activates the Mirror at [target] on the Stack, paying with the engine's suggestion. */
    private fun Scenario.copies(player: ScenarioPlayer, target: ObjectId) {
        player.activates(mirror)
        while (pending != null) when (val d = pending) {
            is Decision.Targets -> answer(Answer.Targets(listOf(arcanum.engine.state.TargetRef.Object(target))))
            is Decision.ManaPayment -> answer(Answer.ManaPayment(d.suggestion))
            else -> error("unexpected $d")
        }
    }
}

/** The invented cards of the mass trigger scenarios (oracle templates of real cards, our names). */
object MassTriggerCards {
    val ledger = Cards.invent("Ledger of Debts", "{1}{B}{B}", "Enchantment",
        "Whenever a creature dies, each opponent loses 1 life and you gain 1 life.")
    val bloodLedger = Cards.invent("Ledger of Blood", "{1}{B}{B}", "Enchantment",
        "Whenever a creature dies, target player loses 1 life and you gain 1 life.")
    val wipe = Cards.invent("Last Reckoning", "{2}{W}{W}", "Sorcery", "Destroy all creatures.")
    val pup = Cards.invent("Cinder Pup", "{R}", "Creature — Elemental Dog", "", "1/1")
    val echo = Cards.invent("Echoing Vigil", "{3}{U}", "Enchantment",
        "If a triggered ability of a permanent you control triggers, that ability triggers an additional time.")
    val mirror = Cards.invent("Mirror Reliquary", "{2}", "Artifact",
        "{1}, {T}: Copy target activated or triggered ability you control. You may choose new targets for the copy.")
}
