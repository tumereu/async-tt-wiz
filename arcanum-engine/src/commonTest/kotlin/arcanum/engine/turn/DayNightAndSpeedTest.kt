package arcanum.engine.turn

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.query.Evaluator
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/** Day and night with daybound and nightbound (CR 731, 702.145), speed (CR 702.179), compleated (702.150) and read ahead
 * (702.155): rules the engine used to leave to the players. */
class DayNightAndSpeedTest {
    private val watcher = Cards.inventFaces("Transform",
        face("Auto3 Village Watcher", "{1}{G}", "Creature — Human Werewolf", "Daybound (If a player casts no spells during their own turn, it becomes night next turn.)", "2/2", null, null),
        face("Auto3 Moonlit Howler", null, "Creature — Werewolf", "Nightbound (If a player casts at least two spells during their own turn, it becomes day next turn.)", "4/4", null, null))
    private val kart = Cards.invent("Auto3 Racing Kart", "{1}{R}", "Creature — Goblin Pilot", "Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)", "2/2")
    private val walker = Cards.invent("Auto3 Compleated Seer", "{G/P}{G/P}", "Legendary Planeswalker — Seer", "Compleated ({G/P} can be paid with {G} or 2 life. For each {G/P} paid with life, this planeswalker enters with two fewer loyalty counters.)\n[+1]: You gain 1 life.", null, "5")
    private val saga = Cards.invent("Auto3 Hurried Chronicle", "{1}{W}", "Enchantment — Saga", """
        Read ahead (Choose a chapter and start with that many lore counters. Add one after your draw step. Skipped chapters don't trigger.)
        I — You gain 1 life.
        II — You gain 2 life.
        III — You gain 3 life.
    """)

    private fun dayNight(s: arcanum.engine.state.GameState) = s.ledger[Evaluator.DAY_NIGHT] ?: 0

    // CR 702.145d: a daybound permanent makes it day; CR 731.2a: a turn without spells makes it night, and the daybound
    // permanent transforms (702.145c); CR 731.2b: two spells in a turn make it day again, and it transforms back (702.145f)
    @Test
    fun dayAndNight() = scenario {
        alice.battlefield(watcher)
        bob.battlefield("Mountain", "Mountain")
        bob.hand("Storm Spark", "Storm Spark")
        players.forEach { it.library("Forest", "Forest", "Forest", "Forest") }
        settle()
        assertEquals(Evaluator.DAY, dayNight(state))
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(Evaluator.NIGHT, dayNight(state))
        expect { watcher.power shouldBe 4 }
        assertEquals(1, state.objOrThrow(card(watcher)).face)
        // Bob casts two spells in his turn: day again in Alice's next turn
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts("Storm Spark", target = "alice")
        allPass()
        bob.casts("Storm Spark", target = "alice")
        allPass()
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals(Evaluator.DAY, dayNight(state))
        expect { watcher.power shouldBe 2 }
    }

    // CR 702.145b: at night a daybound card enters transformed
    @Test
    fun daboundEntersTransformedAtNight() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(watcher)
        change(Change.Ledger(null, Evaluator.DAY_NIGHT, 0, Evaluator.NIGHT))
        alice.casts(watcher)
        allPass()
        expect { watcher.power shouldBe 4 }
    }

    // CR 702.179a, d: start your engines! gives speed 1; an opponent losing life in your turn raises it once each turn
    @Test
    fun speed() = scenario {
        alice.battlefield(kart, "Mountain", "Mountain")
        alice.hand("Storm Spark", "Storm Spark")
        settle()
        assertEquals(1, Speed.of(state, alice.id))
        alice.casts("Storm Spark", target = "bob")
        allPass()
        allPass()
        assertEquals(2, Speed.of(state, alice.id))
        alice.casts("Storm Spark", target = "bob")
        allPass()
        allPass()
        assertEquals(2, Speed.of(state, alice.id))
    }

    // CR 702.179e: speed stops at 4 (max speed)
    @Test
    fun maxSpeed() = scenario {
        alice.battlefield(kart, "Mountain")
        alice.hand("Storm Spark")
        settle()
        change(Change.Ledger(alice.id, Speed.KEY, 1, 4))
        alice.casts("Storm Spark", target = "bob")
        allPass()
        allPass()
        assertEquals(4, Speed.of(state, alice.id))
    }

    // CR 702.150a: each Phyrexian symbol paid with life: two fewer loyalty counters
    @Test
    fun compleated() = scenario {
        alice.battlefield("Forest")
        alice.hand(walker)
        perform(Input.Cast(alice.id, card(walker)))
        val payment = decision<Decision.ManaPayment>()
        val forest = payment.suggestion.first { it !is ManaUse.Life }
        answer(Answer.ManaPayment(listOf(forest, ManaUse.Life(1))))
        allPass()
        expect {
            walker.counters(CounterKind.Loyalty) shouldBe 3
            alice.life shouldBe 18
        }
    }

    // CR 702.155a, b, 714.3b: read ahead: it enters with the chosen chapter's lore counters; only that chapter triggers
    @Test
    fun readAhead() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(saga)
        alice.casts(saga)
        allPass()
        alice.answers(Answer.Number(2))
        allPass()
        expect {
            saga.counters(CounterKind.Lore) shouldBe 2
            alice.life shouldBe 22
        }
    }
}
