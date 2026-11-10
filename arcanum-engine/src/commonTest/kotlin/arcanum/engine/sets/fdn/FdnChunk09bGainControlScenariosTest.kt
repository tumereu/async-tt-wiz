package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Chunk 09b played: the invented names carry the oracle text of Tinybones, Ashroot Animist, Bloodthirsty Conqueror, Fumigate, Harmless Offering, Inspiring Call, Sphinx of Forgotten Lore and Venom Connoisseur. */
class FdnChunk09bGainControlScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val pilferer = Cards.invent("Stashbone Pilferer", "{1}{B}", "Legendary Creature — Skeleton Rogue", """
        Whenever an opponent discards a card, exile it from their graveyard with a stash counter on it.
        During your turn, you may play cards you don't own with stash counters on them from exile, and mana of any type can be spent to cast those spells.
        {3}{B}, {T}: Each opponent discards a card. Activate only as a sorcery.
    """, "1/3")
    private val animist = Cards.invent("Rootwhip Animist", "{2}{R}{G}", "Creature — Lizard Druid", """
        Trample
        Whenever this creature attacks, another target creature you control gains trample and gets +X/+X until end of turn, where X is this creature's power.
    """, "4/4")
    private val usurper = Cards.invent("Bloodbound Usurper", "{3}{B}{B}", "Creature — Vampire Knight", """
        Flying, deathtouch
        Whenever an opponent loses life, you gain that much life. (Damage causes loss of life.)
    """, "5/5")
    private val reckoning = Cards.invent("Pyre of Reckoning", "{3}{W}{W}", "Sorcery", "Destroy all creatures. You gain 1 life for each creature destroyed this way.")
    private val tribute = Cards.invent("Cursed Tribute", "{2}{R}", "Sorcery", "Target opponent gains control of target permanent you control.")
    private val cry = Cards.invent("Rallying Cry of Growth", "{2}{G}", "Instant",
        "Draw a card for each creature you control with a +1/+1 counter on it. Those creatures gain indestructible until end of turn.")
    private val sphinx = Cards.invent("Sphinx of Buried Memory", "{2}{U}{U}", "Creature — Sphinx", """
        Flash
        Flying
        Whenever this creature attacks, target instant or sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to that card's mana cost.
    """, "3/3")
    private val brewer = Cards.invent("Fangtooth Brewer", "{1}{G}", "Creature — Human Druid",
        "Alliance — Whenever another creature you control enters, this creature gains deathtouch until end of turn. If this is the second time this ability has resolved this turn, all creatures you control gain deathtouch until end of turn.", "2/2")
    private val dart = Cards.invent("Siphon Dart", "{B}", "Instant", "Target player loses 3 life.")
    private val spark = Cards.invent("Spark Bauble", "{R}", "Sorcery", "Target player loses 2 life.")
    private val golem = Cards.invent("Ironhide Golem", "{2}", "Artifact Creature — Golem", "Indestructible", "2/2")
    private val lurker = Cards.invent("Mossback Lurker", "{1}{G}", "Creature — Beast", "", "2/2")
    private val squire = Cards.invent("Pale Squire", "{W}", "Creature — Human Knight", "", "1/1")
    private val bolt = Cards.invent("Echo Bolt", "{R}", "Instant", "Target player loses 2 life.")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 701.9a, 400.7, 122.1, 118.14, 609.4b, 601.2: the discarded card is exiled with a counter; Alice casts Bob's card with Swamp mana
    @Test
    fun tinybonesStealsTheDiscardedCardAndPaysWithAnyMana() = scenario {
        alice.battlefield(pilferer, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        bob.hand(spark)
        alice.activates(pilferer)
        resolveAll()
        expect {
            bob.exile has spark
            card(spark).counters(CounterKind.Named("stash")) shouldBe 1
        }
        alice.casts(spark, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            bob.graveyard has spark
        }
    }

    // CR 118.14: the exiled card is playable only with a stash counter, so a card exiled without one stays put
    @Test
    fun tinybonesCardsWithoutACounterCantBePlayed() = scenario {
        alice.battlefield(pilferer, "Swamp", "Mountain")
        alice.exile(bolt)
        assertFailsWith<IllegalArgumentException> { alice.casts(bolt, target = "bob") }
    }

    // CR 508.1m, 208.1, 702.19: the target gets the animist's power as +X/+X and trample
    @Test
    fun animistGivesAnotherCreatureTrampleAndPower() = scenario {
        alice.battlefield(animist, lurker)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(animist)
        allPass()
        expect {
            card(lurker).power shouldBe 6
            card(lurker).toughness shouldBe 6
            card(lurker) hasKeyword Keyword.Trample.kind
        }
    }

    // CR 120.4a, 603.2: the opponent's loss triggers; the controller's own loss doesn't
    @Test
    fun conquerorGainsWhatTheOpponentLoses() = scenario {
        alice.battlefield(usurper, "Swamp", "Swamp")
        alice.hand(dart, dart)
        alice.casts(dart, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
        alice.casts(dart, target = "alice")
        resolveAll()
        expect { alice.life shouldBe 20 }
    }

    // CR 608.2c, 702.12b: only the creatures actually destroyed count, so the indestructible one isn't
    @Test
    fun fumigateGainsOneLifePerCreatureDestroyed() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", lurker, golem)
        bob.battlefield(squire, squire)
        alice.hand(reckoning)
        alice.casts(reckoning)
        resolveAll()
        expect {
            alice.life shouldBe 23
            card(golem).zone shouldBe Zone.Battlefield
            card(lurker).zone shouldBe Zone.Graveyard
        }
    }

    // CR 613.1b: Bob controls the permanent for good
    @Test
    fun harmlessOfferingGivesThePermanentAway() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", lurker)
        alice.hand(tribute)
        alice.casts(tribute, targets = listOf("bob", lurker))
        resolveAll()
        expect { bob.battlefield has lurker }
    }

    // CR 611.2c: only the creatures with a counter draw and gain indestructible
    @Test
    fun callDrawsAndProtectsThoseWithCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", golem)
        alice.battlefield(lurker, counters = listOf(p1p1 to 1))
        alice.battlefield(squire, counters = listOf(p1p1 to 1))
        alice.library("Forest", "Forest", "Forest")
        alice.hand(cry)
        alice.casts(cry)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            card(lurker) hasKeyword Keyword.Indestructible.kind
            card(squire) hasKeyword Keyword.Indestructible.kind
        }
    }

    // CR 702.34a: Alice attacks, the trigger gives her instant flashback, and she casts it from the graveyard for its mana cost
    @Test
    fun sphinxGivesFlashbackToAnInstant() = scenario {
        alice.battlefield(sphinx, "Mountain")
        alice.graveyard(bolt)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(sphinx)
        resolveAll()
        alice.casts(bolt, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.exile has bolt
        }
    }

    // CR 603.2, 107.3c: the first resolution only the brewer, the second every creature
    @Test
    fun brewerSecondResolutionGivesEveryCreatureDeathtouch() = scenario {
        alice.battlefield(brewer, lurker, "Plains", "Plains")
        alice.hand(squire, squire)
        alice.casts(squire)
        resolveAll()
        expect {
            card(brewer) hasKeyword Keyword.Deathtouch.kind
            card(lurker) lacksKeyword Keyword.Deathtouch.kind
        }
        alice.casts(squire)
        resolveAll()
        expect { card(lurker) hasKeyword Keyword.Deathtouch.kind }
    }
}
