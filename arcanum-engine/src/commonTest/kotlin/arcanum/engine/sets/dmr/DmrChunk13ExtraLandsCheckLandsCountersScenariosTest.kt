package arcanum.engine.sets.dmr

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertFails

/** Dominaria Remastered chunk 13 in play: an extra land play, the check lands, a creature that removes its own counters. */
class DmrChunk13ExtraLandsCheckLandsCountersScenariosTest {
    private val exploring = Cards.invent("Wandering Growth", "{G}", "Enchantment", "You may play an additional land on each of your turns.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val triskele = Cards.invent("Three-Eyed Triskele", "{6}", "Artifact Creature — Construct", """
        This creature enters with three +1/+1 counters on it.
        Remove a +1/+1 counter from this creature: It deals 1 damage to any target.
    """, "1/1")

    private val chapel = Cards.invent("Sodden Chapel", null, "Land", """
        This land enters tapped unless you control a Plains or a Swamp.
        {T}: Add {W} or {B}.
    """)
    private val harbor = Cards.invent("Verdant Harbor", null, "Land", """
        This land enters tapped unless you control a Forest or an Island.
        {T}: Add {G} or {U}.
    """)
    private val falls = Cards.invent("Scorched Falls", null, "Land", """
        This land enters tapped unless you control an Island or a Mountain.
        {T}: Add {U} or {R}.
    """)
    private val cemetery = Cards.invent("Blighted Cemetery", null, "Land", """
        This land enters tapped unless you control a Swamp or a Forest.
        {T}: Add {B} or {G}.
    """)
    private val retreat = Cards.invent("Sunlit Retreat", null, "Land", """
        This land enters tapped unless you control a Mountain or a Plains.
        {T}: Add {R} or {W}.
    """)

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 305.2, 305.2a: one land play a turn, plus one more from the enchantment; a third is refused
    @Test
    fun extraLandPlayAllowsASecondLandButNotAThird() = scenario {
        alice.battlefield(exploring)
        alice.hand("Forest", "Island", "Plains")
        alice.plays("Forest")
        alice.plays("Island")
        assertFails { alice.plays("Plains") }
        expect { alice.battlefield.names.count { it == "Island" || it == "Forest" } shouldBe 2 }
    }

    // CR 305.2: without the enchantment the second land play is refused
    @Test
    fun noExtraLandPlayWithoutTheEnchantment() = scenario {
        alice.hand("Forest", "Island")
        alice.plays("Forest")
        assertFails { alice.plays("Island") }
    }

    // CR 305.2a: the extra land play is for its controller only; the opponent still gets one on their own turn
    @Test
    fun extraLandPlayIsOnlyForItsController() = scenario(players = 3) {
        alice.battlefield(exploring)
        bob.hand("Forest", "Island")
        turn(number = 2, active = bob)
        bob.plays("Forest")
        assertFails { bob.plays("Island") }
    }

    // CR 614.1c, 614.12: "enters tapped unless you control ..." looks at the battlefield as the land enters; each of the five
    private fun entersTappedUnless(land: String, first: String, second: String, other: String) {
        scenario {
            alice.battlefield(first)
            alice.hand(land)
            alice.plays(land)
            expect { land.tapped shouldBe false }
        }
        scenario {
            alice.battlefield(second)
            alice.hand(land)
            alice.plays(land)
            expect { land.tapped shouldBe false }
        }
        scenario {
            alice.battlefield(other)
            alice.hand(land)
            alice.plays(land)
            expect { land.tapped shouldBe true }
        }
    }

    @Test fun sodden() = entersTappedUnless(chapel, "Plains", "Swamp", "Forest")
    @Test fun verdant() = entersTappedUnless(harbor, "Forest", "Island", "Plains")
    @Test fun scorched() = entersTappedUnless(falls, "Island", "Mountain", "Swamp")
    @Test fun blighted() = entersTappedUnless(cemetery, "Swamp", "Forest", "Mountain")
    @Test fun sunlit() = entersTappedUnless(retreat, "Mountain", "Plains", "Island")

    // CR 614.12: an opponent's land of the right type doesn't count as "you control"
    @Test
    fun anOpponentsBasicDoesNotCount() = scenario(players = 3) {
        bob.battlefield("Plains")
        carol.battlefield("Swamp")
        alice.hand(chapel)
        alice.plays(chapel)
        expect { chapel.tapped shouldBe true }
    }

    // CR 614.1c, 122.1, 602.2: three counters on entering make it a 4/4; each removal is a cost and deals 1 damage
    @Test
    fun triskeleEntersWithThreeCountersAndShootsThemOff() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(triskele)
        alice.casts(triskele)
        resolveAll()
        expect { triskele.counters(p1p1) shouldBe 3 }
        expect { triskele.power shouldBe 4 }
        alice.activates(triskele)
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(TargetRef.Player(carol.id))))
        resolveAll()
        expect {
            triskele.counters(p1p1) shouldBe 2
            triskele.power shouldBe 3
            carol.life shouldBe 19
            bob.life shouldBe 20
        }
    }
}
