package arcanum.engine.sets.trk

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test

/** Star Trek chunk 14 in play: quest counters with an "instead" threshold, a total-power attack trigger, counters spread over several creatures, three players. */
class TrkChunk14CountersScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val quest = CounterKind.Named("quest")

    private val vengeance = Cards.invent("Trk14 Vengeance Vow", "{2}{B}", "Enchantment",
        "Whenever a creature dies, put a quest counter on this enchantment. Target opponent loses 1 life. That player loses 2 life instead if there are seven or more quest counters on this enchantment.")
    private val conquest = Cards.invent("Trk14 Conquest Vow", "{2}{W}", "Enchantment",
        "Whenever a nontoken artifact you control enters, put a quest counter on this enchantment. Create a 1/1 colorless Borg artifact creature token. If this enchantment has six or more quest counters on it, create two of those tokens instead.")
    private val officer = Cards.invent("Trk14 Drill Officer", "{1}{W}", "Creature — Human Officer",
        "Whenever you attack while creatures you control have total power 8 or greater, put a +1/+1 counter on this creature. Untap it.", "2/1")
    private val brute = Cards.invent("Trk14 Brute", "{4}{G}", "Creature — Ogre", "", "6/6")
    private val runt = Cards.invent("Trk14 Runt", "{G}", "Creature — Ogre", "", "5/5")
    private val android = Cards.invent("Trk14 Android Scientist", "{2}{U}", "Legendary Artifact Creature — Android Scientist", """
        Whenever a player casts a noncreature spell, put a +1/+1 counter on Trk14 Android Scientist.
        {1}, Remove three counters from among creatures you control: Draw a card.
    """.trimIndent(), "2/2")
    private val healer = Cards.invent("Trk14 Healer", "{3}{G/W}{G/W}", "Legendary Artifact Creature — Illusion Doctor",
        "When Trk14 Healer enters, put a +1/+1 counter on target creature. Then put a +1/+1 counter on each creature you control with a counter on it.", "3/3")
    private val bear = Cards.invent("Trk14 Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val spark = Cards.invent("Trk14 Spark", "{R}", "Instant", "This spell deals 3 damage to any target.")
    private val gadget = Cards.invent("Trk14 Gadget", "{1}", "Artifact", "")
    private val study = Cards.invent("Trk14 Study", "{U}", "Sorcery", "Draw a card.")

    private fun Scenario.count(name: String) = state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == name }
    private fun Scenario.pickTarget(name: String) {
        answer(Answer.Targets(listOf(target(name))))
        resolveAll()
    }

    // CR 122.1, 603.2: the seventh counter is put first, so the loss is 2; with three players only the chosen opponent loses it
    @Test
    fun seventhQuestCounterMakesTheOpponentLoseTwo() = scenario(players = 3) {
        alice.battlefield(vengeance, bear, "Mountain", counters = listOf(quest to 6))
        alice.hand(spark)
        alice.casts(spark, target = bear)
        resolveAll()
        pickTarget("bob")
        expect {
            vengeance.counters(quest) shouldBe 7
            bob.life shouldBe 18
            carol.life shouldBe 20
            alice.life shouldBe 20
        }
    }

    @Test
    fun earlierQuestCounterMakesTheOpponentLoseOne() = scenario(players = 3) {
        alice.battlefield(vengeance, bear, "Mountain", counters = listOf(quest to 5))
        alice.hand(spark)
        alice.casts(spark, target = bear)
        resolveAll()
        pickTarget("carol")
        expect {
            vengeance.counters(quest) shouldBe 6
            carol.life shouldBe 19
            bob.life shouldBe 20
        }
    }

    // CR 122.1, 111.1: the sixth counter makes two tokens instead of one; the Borg tokens entering are not nontoken artifacts
    @Test
    fun sixthQuestCounterMakesTwoTokens() = scenario(players = 3) {
        alice.battlefield(conquest, "Island", counters = listOf(quest to 5))
        alice.hand(gadget)
        alice.casts(gadget)
        resolveAll()
        expect {
            conquest.counters(quest) shouldBe 6
            count("Borg Token") shouldBe 2
        }
    }

    @Test
    fun firstQuestCounterMakesOneToken() = scenario(players = 3) {
        alice.battlefield(conquest, "Island")
        alice.hand(gadget)
        alice.casts(gadget)
        resolveAll()
        expect {
            conquest.counters(quest) shouldBe 1
            count("Borg Token") shouldBe 1
        }
    }

    // CR 508.1, 603.2: total power 8 (2 + 6) when attacking: a counter and an untap; total power 7 (2 + 5): nothing
    @Test
    fun officerGrowsAndUntapsWithTotalPowerEight() = scenario(players = 3) {
        alice.battlefield(officer, brute)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(officer, defender = "bob")
        resolveAll()
        expect {
            officer.counters(p1p1) shouldBe 1
            officer.tapped shouldBe false
        }
    }

    @Test
    fun officerDoesNothingWithTotalPowerSeven() = scenario(players = 3) {
        alice.battlefield(officer, runt)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(officer, defender = "bob")
        resolveAll()
        expect {
            officer.counters(p1p1) shouldBe 0
            officer.tapped shouldBe true
        }
    }

    // CR 122.1, 603.2: any player's noncreature spell (here an opponent's, on their turn) puts a counter on it
    @Test
    fun androidGrowsOnAnOpponentsNoncreatureSpell() = scenario(players = 3) {
        alice.battlefield(android)
        bob.hand(study)
        bob.battlefield("Island")
        bob.library("Island")
        turn(1, bob, TurnStep.PrecombatMain)
        bob.casts(study)
        resolveAll()
        expect { android.counters(p1p1) shouldBe 1 }
    }

    // CR 118.3, 602.2b: the cost takes three counters from among several creatures you control, not an opponent's
    @Test
    fun androidPaysCountersFromSeveralCreatures() = scenario(players = 3) {
        alice.battlefield(android, bear, "Island", counters = listOf(p1p1 to 2))
        alice.battlefield(runt, counters = listOf(p1p1 to 1))
        bob.battlefield(brute, counters = listOf(p1p1 to 4))
        alice.library("Island")
        alice.activates(android, choosing = listOf(android, runt))
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            android.counters(p1p1) + runt.counters(p1p1) shouldBe 0
            brute.counters(p1p1) shouldBe 4
        }
    }

    // CR 122.1, 608.2c: the target gets its counter first, then every creature you control with a counter (it too, if it is yours)
    @Test
    fun healerDoublesUpOnCreaturesWithCounters() = scenario(players = 3) {
        alice.battlefield(bear, runt, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.battlefield(brute, counters = listOf(p1p1 to 1))
        alice.hand(healer)
        alice.casts(healer)
        resolveAll()
        pickTarget("Trk14 Bear")
        expect {
            bear.counters(p1p1) shouldBe 2
            brute.counters(p1p1) shouldBe 2
            runt.counters(p1p1) shouldBe 0
            healer.counters(p1p1) shouldBe 0
        }
    }
}
