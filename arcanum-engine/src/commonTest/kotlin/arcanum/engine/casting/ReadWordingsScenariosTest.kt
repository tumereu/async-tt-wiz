package arcanum.engine.casting

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/** Wordings the parser reads since the second by-hand round, played: spree and tiered modes, partner with, "if this spell was
 * bargained", "if tribute wasn't paid" and a disturb back face's "exile it instead". */
class ReadWordingsScenariosTest {
    private val getaway = Cards.invent("Read Quick Getaway", "{U}", "Instant", """
        Spree (Choose one or more additional costs.)
        + {1} — Draw a card.
        + {2} — Target player loses 2 life.
    """.trimIndent())
    private val graded = Cards.invent("Read Graded Insight", "{U}", "Sorcery", """
        Tiered (Choose one additional cost.)
        + {0} — Draw a card.
        + {2} — Draw two cards.
    """.trimIndent())
    private val vigor = Cards.invent("Read Bargained Vigor", "{G}", "Instant", """
        Bargain (You may sacrifice an artifact, enchantment, or token as you cast this spell.)
        Draw a card. If this spell was bargained, draw two cards.
    """.trimIndent())
    private val relic = Cards.invent("Read Old Relic", "{1}", "Artifact", "")
    private val brute = Cards.invent("Read Tithe Brute", "{2}{R}", "Creature — Minotaur", """
        Tribute 2 (As this creature enters, an opponent of your choice may put two +1/+1 counters on it.)
        When this creature enters, if tribute wasn't paid, you gain 4 life.
    """.trimIndent(), "3/3")
    private val twinA = Cards.invent("Read Ledger Twin", "{1}{U}", "Legendary Creature — Human Wizard",
        "Partner with Read Okto, Eye of Ledgers (When this creature enters, target player may put Okto into their hand from their library, then shuffle.)", "2/2")
    private val twinB = Cards.invent("Read Okto, Eye of Ledgers", "{1}{U}", "Legendary Creature — Human Wizard",
        "Partner with Read Ledger Twin (When this creature enters, target player may put Read Ledger Twin into their hand from their library, then shuffle.)", "2/2")

    // CR 702.172a: spree: one or more modes, each mode's cost added (CR 601.2f)
    @Test
    fun spreePaysForEachModeChosen() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(getaway)
        alice.library("Island")
        alice.casts(getaway, modes = listOf(0, 1), target = "bob")
        allPass()
        expect {
            alice.hand.size shouldBe 1
            bob.life shouldBe 18
        }
        // {U} + {1} + {2}
        assertTrue(state.battlefield.count { state.objOrThrow(it).tapped } == 4)
    }

    // CR 702.183a: tiered: exactly one mode, its cost added
    @Test
    fun tieredChoosesOne() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(graded)
        alice.library("Island", "Island")
        kotlin.test.assertFailsWith<IllegalArgumentException> { alice.casts(graded, modes = listOf(0, 1)) }
    }

    @Test
    fun tieredSecondMode() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(graded)
        alice.library("Island", "Island")
        alice.casts(graded, modes = listOf(1))
        allPass()
        expect { alice.hand.size shouldBe 2 }
        assertTrue(state.battlefield.count { state.objOrThrow(it).tapped } == 3)
    }

    // CR 702.166b: "if this spell was bargained"
    @Test
    fun bargained() = scenario {
        alice.battlefield("Forest", relic)
        alice.hand(vigor)
        alice.library("Island", "Island", "Island")
        alice.casts(vigor, kicked = 1, choosing = listOf(relic))
        allPass()
        expect {
            alice.graveyard has relic
            alice.hand.size shouldBe 3
        }
    }

    @Test
    fun notBargained() = scenario {
        alice.battlefield("Forest", relic)
        alice.hand(vigor)
        alice.library("Island", "Island", "Island")
        alice.casts(vigor, kicked = 0)
        allPass()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 702.104a, b: the opponent declines the tribute, so the trigger's "if tribute wasn't paid" holds
    @Test
    fun tributeNotPaid() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(brute)
        alice.casts(brute)
        allPass()
        while (pending != null) {
            when (pending) {
                is Decision.May -> answer(Answer.YesNo(false))
                is Decision.ChoosePlayer -> answer(Answer.Player(bob.id))
                else -> break
            }
        }
        allPass()
        expect {
            alice.life shouldBe 24
            brute.power shouldBe 3
        }
    }

    // CR 702.124j: partner with: the other one may be searched for as it enters
    @Test
    fun partnerWithSearches() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(twinA)
        alice.library(twinB, "Island")
        alice.casts(twinA)
        allPass()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target("alice"))))
        allPass()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses(twinB)
        expect { alice.hand has twinB }
    }

    // CR 702.146b, 614.1a: the back face of a disturb card is exiled instead of going to a graveyard
    @Test
    fun disturbBackFaceExiled() = scenario {
        val watcher = Cards.inventFaces("Transform",
            arcanum.engine.cards.face("Read Fading Watcher", "{1}{W}", "Creature — Human", "Disturb {1}{W} (You may cast this card transformed from your graveyard for its disturb cost.)", "2/2", null, null),
            arcanum.engine.cards.face("Read Lingering Shade", null, "Creature — Spirit",
                "Flying\nIf Read Lingering Shade would be put into a graveyard from anywhere, exile it instead.", "1/1", null, "W"))
        alice.battlefield("Plains", "Plains", "Mountain")
        alice.graveyard(watcher)
        alice.hand("Storm Spark")
        alice.casts(watcher)
        allPass()
        expect { alice.battlefield has watcher }
        alice.casts("Storm Spark", target = watcher)
        allPass()
        expect { alice.exile has watcher }
    }

    // CR 115.1, 601.2c, 603.3d: "for each isolation counter on it, exile up to one target …": as many targets as counters, chosen as the
    // trigger is put on the Stack, each exiled
    @Test
    fun oneTargetForEachCounter() = scenario {
        val ward = Cards.invent("Read Sealing Ward", "{X}{X}{W}{W}", "Enchantment", """
            This enchantment enters with X isolation counters on it.
            When this enchantment enters, for each isolation counter on it, exile up to one target nonland permanent an opponent controls until this enchantment leaves the battlefield.
        """.trimIndent())
        val bear = Cards.invent("Read Sealed Bear", "{1}{G}", "Creature — Bear", "", "2/2")
        val ox = Cards.invent("Read Sealed Ox", "{2}{G}", "Creature — Ox", "", "3/3")
        alice.battlefield(*Array(6) { "Plains" })
        bob.battlefield(bear, ox)
        alice.hand(ward)
        alice.casts(ward, x = 2)
        allPass()
        val asked = decision<Decision.Targets>()
        kotlin.test.assertEquals(2, asked.max)
        answer(Answer.Targets(listOf(target(bear), target(ox))))
        allPass()
        expect {
            bob.exile has bear
            bob.exile has ox
        }
    }
}
