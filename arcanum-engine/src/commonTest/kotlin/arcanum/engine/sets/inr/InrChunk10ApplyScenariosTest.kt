package arcanum.engine.sets.inr

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** INR chunk 10: until-end-of-turn effects first played here: a discard cost pump, a pump plus double strike, a choice of modes, an
 * attacking-only target, and a reflexive trigger that gains life only when the Vampire was sacrificed. Invented names carry the oracle text. */
class InrChunk10ApplyScenariosTest {
    private val constrictor = Cards.invent("Hangman Constrictor", "{1}{G}", "Creature — Snake", """
        Reach
        Discard a card: This creature gets +1/+1 until end of turn.
    """, "2/2")
    private val wildStrike = Cards.invent("Wild Strike", "{2}{R}", "Instant", "Target creature gets +1/+1 and gains double strike until end of turn.")
    private val stance = Cards.invent("Resolute Stance", "{1}{W}", "Instant", """
        Choose one —
        • Target creature gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
        • Destroy target creature with toughness 4 or greater.
    """)
    private val pack = Cards.invent("Ulrich's Pack", "{2}{R}", "Creature — Wolf", """
        Trample
        {3}{G}: Target attacking Wolf or Werewolf gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
    """, "3/2")
    private val corin = Cards.invent("Corin, Imperious Bloodlord", "{2}{B}", "Legendary Planeswalker — Corin", """
        [+1]: Target creature you control gains deathtouch and lifelink until end of turn. If it's a Vampire, put a +1/+1 counter on it.
        [+1]: You may sacrifice a Vampire. When you do, Corin deals 3 damage to any target and you gain 3 life.
        [−3]: You may put a Vampire creature card from your hand onto the battlefield.
    """, null)
    private val bat = Cards.invent("Gloam Vampire Bat", "{1}{B}", "Creature — Vampire", "", "2/2")
    private val ox = Cards.invent("Tarnished Ox", "{2}", "Creature — Ox", "", "2/2")
    private val wall = Cards.invent("Stone Wall", "{2}", "Creature — Wall", "", "0/5")
    private val wolfCub = Cards.invent("Grey Cub", "{1}", "Creature — Wolf", "", "1/1")

    // CR 602.2b (discard as a cost), 611.2a (until end of turn)
    @Test
    fun constrictorDiscardsACardToGrow() = scenario {
        alice.battlefield(constrictor)
        alice.hand(ox)
        alice.activates(constrictor, choosing = listOf("Tarnished Ox"))
        resolveAll()
        expect {
            constrictor.power shouldBe 3
            constrictor.toughness shouldBe 3
            alice.graveyard has ox
        }
    }

    // CR 702.4 (double strike), 613.4c (+1/+1)
    @Test
    fun wildStrikePumpsAndGrantsDoubleStrike() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", ox)
        alice.hand(wildStrike)
        alice.casts(wildStrike, target = ox)
        resolveAll()
        expect {
            ox.power shouldBe 3
            ox.toughness shouldBe 3
            ox hasKeyword Keyword.DoubleStrike.kind
        }
    }

    // CR 700.2: the second mode's target needs toughness 4 or greater, so the Ox is no legal target for it
    @Test
    fun stanceGivesIndestructibleOrDestroysABigCreature() = scenario {
        alice.battlefield("Plains", "Plains", ox)
        bob.battlefield(wall)
        alice.hand(stance)
        alice.casts(stance, modes = listOf(1), target = wall)
        resolveAll()
        expect { bob.graveyard has wall }
    }

    @Test
    fun stanceFirstModeProtectsFromDestruction() = scenario {
        alice.battlefield("Plains", "Plains", ox)
        alice.hand(stance)
        alice.casts(stance, modes = listOf(0), target = ox)
        resolveAll()
        expect { ox hasKeyword Keyword.Indestructible.kind }
    }

    // CR 508.1: only an attacking Wolf is a legal target for the ability; after it attacks the cub can be targeted
    @Test
    fun packTargetsOnlyAttackingWolves() = scenario {
        alice.battlefield(pack, wolfCub, "Forest", "Forest", "Forest", "Forest")
        assertFailsWith<Throwable> { alice.activates(pack, target = wolfCub) }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wolfCub)
        resolveAll()
        alice.activates(pack, target = wolfCub)
        resolveAll()
        expect { wolfCub hasKeyword Keyword.Indestructible.kind }
    }

    // CR 603.12: "when you do" happens only if the Vampire was sacrificed, and so does the life gain with it
    @Test
    fun corinSacrificesAVampireForDamageAndLife() = scenario {
        alice.battlefield(corin, bat, counters = listOf(CounterKind.Loyalty to 4))
        bob.battlefield(wall)
        alice.activates(corin, ability = 1)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses(bat)
        resolveAll()
        if (pending is Decision.Targets) {
            alice.answers(Answer.Targets(listOf(target("bob"))))
            resolveAll()
        }
        expect {
            alice.graveyard has bat
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    @Test
    fun corinWithoutASacrificeGainsNoLife() = scenario {
        alice.battlefield(corin, bat, counters = listOf(CounterKind.Loyalty to 4))
        alice.activates(corin, ability = 1)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(false))
        resolveAll()
        expect {
            alice.battlefield has bat
            alice.life shouldBe 20
            bob.life shouldBe 20
        }
    }
}
