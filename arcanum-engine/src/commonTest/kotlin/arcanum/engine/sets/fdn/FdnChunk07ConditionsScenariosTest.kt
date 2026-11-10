package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The FDN cards whose first unread clause was a condition, played: the invented names carry the oracle text of Doubling Season,
 * Painful Quandary, Muldrotha, the Gravetide, Hidetsugu's Second Rite, Cemetery Recruitment, Kellan, Planar Trailblazer,
 * Soul-Shackled Zombie, Angel of Vitality, Twinblade Paladin, Darksteel Colossus, Deadly Brew, Dropkick Bomber and Garna, Bloodfist of Keld.
 */
class FdnChunk07ConditionsScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val bear = Cards.invent("Sevenfold Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ghoul = Cards.invent("Sevenfold Ghoul", "{1}{B}", "Creature — Zombie", "", "2/2")
    private val idol = Cards.invent("Sevenfold Idol", "{1}", "Artifact", "")
    private val goblin = Cards.invent("Sevenfold Goblin", "{R}", "Creature — Goblin", "", "1/1")
    private val insight = Cards.invent("Sevenfold Insight", "{U}", "Instant", "Draw a card.")
    private val sprouts = Cards.invent("Sevenfold Sprouts", "{G}", "Sorcery", "Create two 1/1 green Saproling creature tokens.")
    private val growth = Cards.invent("Sevenfold Growth", "{G}", "Sorcery", "Put two +1/+1 counters on target creature.")
    private val balm = Cards.invent("Sevenfold Balm", "{W}", "Sorcery", "You gain 1 life.")
    private val walker = Cards.invent("Sevenfold Sage", "{2}{G}", "Legendary Planeswalker — Sage", "[+1]: You gain 1 life.", loyalty = "3")
    private val ox = Cards.invent("Sevenfold Ox", "{2}{G}", "Creature — Ox", "", "3/4")
    private val mulch = Cards.invent("Sevenfold Mulch", "{G}", "Sorcery", "Mill a card.")

    private val springtide = Cards.invent("Twofold Springtide", "{4}{G}", "Enchantment", """
        If an effect would create one or more tokens under your control, it creates twice that many of those tokens instead.
        If an effect would put one or more counters on a permanent you control, it puts twice that many of those counters on that permanent instead.
    """)
    private val dilemma = Cards.invent("Bitter Dilemma", "{3}{B}{B}", "Enchantment",
        "Whenever an opponent casts a spell, that player loses 5 life unless they discard a card.")
    private val vashka = Cards.invent("Vashka, the Barrowtide", "{3}{B}{G}{U}", "Legendary Creature — Elemental Avatar",
        "During each of your turns, you may play a land and cast a permanent spell of each permanent type from your graveyard.", "6/6")
    private val rite = Cards.invent("Ormo's Tenfold Rite", "{3}{R}", "Instant",
        "If target player has exactly 10 life, Ormo's Tenfold Rite deals 10 damage to that player.")
    private val muster = Cards.invent("Boneyard Muster", "{1}{B}", "Sorcery",
        "Return target creature card from your graveyard to your hand. If it's a Zombie card, draw a card.")
    private val tamsin = Cards.invent("Tamsin, Planar Wayfarer", "{R}", "Legendary Creature — Human Faerie Scout", """
        {1}{R}: If Tamsin is a Scout, it becomes a Human Faerie Detective and gains "Whenever Tamsin deals combat damage to a player, exile the top card of your library. You may play that card this turn."
        {2}{R}: If Tamsin is a Detective, it becomes a 3/2 Human Faerie Rogue and gains double strike.
    """, "2/1")
    private val fettered = Cards.invent("Spirit-Fettered Ghoul", "{3}{B}", "Creature — Zombie",
        "When this creature enters, exile up to two target cards from a single graveyard. If at least one creature card was exiled this way, each opponent loses 2 life and you gain 2 life.", "4/2")
    private val seraph = Cards.invent("Seraph of Vigor", "{2}{W}", "Creature — Angel", """
        Flying
        If you would gain life, you gain that much life plus 1 instead.
        This creature gets +2/+2 as long as you have 25 or more life.
    """, "2/2")
    private val templar = Cards.invent("Dualsword Templar", "{3}{W}", "Creature — Human Knight", """
        Whenever you gain life, put a +1/+1 counter on this creature.
        As long as you have 25 or more life, this creature has double strike.
    """, "3/3")
    private val juggernaut = Cards.invent("Ironheart Juggernaut", "{11}", "Artifact Creature — Golem", """
        Trample
        Indestructible
        If Ironheart Juggernaut would be put into a graveyard from anywhere, reveal Ironheart Juggernaut and shuffle it into its owner's library instead.
    """, "11/11")
    private val tincture = Cards.invent("Lethal Tincture", "{B}{G}", "Sorcery",
        "Each player sacrifices a creature or planeswalker of their choice. If you sacrificed a permanent this way, you may return another permanent card from your graveyard to your hand.")
    private val rocketKick = Cards.invent("Rocket-Kick Goblin", "{2}{R}", "Creature — Goblin Warrior", """
        Other Goblins you control get +1/+1.
        {R}: Until end of turn, another target Goblin you control gains flying and "When this creature deals combat damage, sacrifice it."
    """, "2/3")
    private val varna = Cards.invent("Varna, Bloodhand of Sel", "{1}{B}{R}{R}", "Legendary Creature — Human Berserker",
        "Whenever another creature you control dies, draw a card if it was attacking. Otherwise, Varna deals 1 damage to each opponent.", "4/3")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 614.1a: two tokens become four
    @Test
    fun springtideDoublesTokens() = scenario {
        alice.battlefield(springtide, "Forest")
        alice.hand(sprouts)
        alice.casts(sprouts)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Saproling Token" } shouldBe 4 }
    }

    // CR 614.1a: an effect's two counters become four
    @Test
    fun springtideDoublesCountersFromAnEffect() = scenario {
        alice.battlefield(springtide, bear, "Forest")
        alice.hand(growth)
        alice.casts(growth, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 4 }
    }

    // CR 118.3, 606.4: a loyalty ability's +1 is a cost, not an effect, so it isn't doubled
    @Test
    fun springtideDoesNotDoubleALoyaltyCost() = scenario {
        alice.battlefield(springtide)
        alice.battlefield(walker, counters = listOf(CounterKind.Loyalty to 3))
        alice.activates(walker)
        resolveAll()
        expect { walker.counters(CounterKind.Loyalty) shouldBe 4 }
    }

    // CR 118.12a: Bob doesn't discard, so he loses 5 life
    @Test
    fun dilemmaBobLosesFiveWithoutDiscarding() = scenario {
        alice.battlefield(dilemma)
        bob.battlefield("Island")
        bob.hand(insight, "Forest")
        bob.library("Plains")
        alice.passes()
        bob.casts(insight)
        allPass()
        bob.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            bob.life shouldBe 15
            bob.hand has "Forest"
        }
    }

    // CR 118.12a: Bob discards a card and keeps his life
    @Test
    fun dilemmaBobDiscardsInstead() = scenario {
        alice.battlefield(dilemma)
        bob.battlefield("Island")
        bob.hand(insight, "Forest")
        bob.library("Plains")
        alice.passes()
        bob.casts(insight)
        allPass()
        bob.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.life shouldBe 20
            bob.graveyard has "Forest"
        }
    }

    // CR 110.4, 601.3: one creature and one artifact from the graveyard in a turn, but not a second creature
    @Test
    fun vashkaCastsOneSpellOfEachPermanentType() = scenario {
        alice.battlefield(vashka, "Forest", "Forest", "Forest", "Swamp", "Swamp")
        alice.graveyard(bear, ghoul, idol)
        alice.casts(bear)
        resolveAll()
        alice.casts(idol)
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.battlefield has idol
        }
        assertFailsWith<Throwable> { alice.casts(ghoul) }
    }

    // CR 305.2: a land from the graveyard, with the turn's land play
    @Test
    fun vashkaPlaysALandFromTheGraveyard() = scenario {
        alice.battlefield(vashka)
        alice.graveyard("Forest")
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    // CR 608.2b: at exactly 10 life Bob is dealt 10 damage
    @Test
    fun riteDealsTenAtExactlyTen() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(rite)
        bob.life = 10
        alice.casts(rite, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 0 }
    }

    // CR 608.2b: at 11 life nothing happens
    @Test
    fun riteDoesNothingAtElevenLife() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(rite)
        bob.life = 11
        alice.casts(rite, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 11 }
    }

    // CR 608.2c: a Zombie card returned draws a card
    @Test
    fun musterDrawsForAZombie() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(muster)
        alice.graveyard(ghoul)
        alice.library("Island")
        alice.casts(muster, target = ghoul)
        resolveAll()
        expect {
            alice.hand has ghoul
            alice.hand has "Island"
        }
    }

    // CR 608.2c: another creature card draws nothing
    @Test
    fun musterDoesNotDrawForABear() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(muster)
        alice.graveyard(bear)
        alice.library("Island")
        alice.casts(muster, target = bear)
        resolveAll()
        expect {
            alice.hand has bear
            alice.hand lacks "Island"
        }
    }

    // CR 205.1b, 613.4b: Scout to Detective, then Detective to a 3/2 Rogue with double strike
    @Test
    fun tamsinBecomesADetectiveThenARogue() = scenario {
        alice.battlefield(tamsin, *Array(5) { "Mountain" })
        alice.activates(tamsin, ability = 0)
        resolveAll()
        expect { tamsin.subtypes shouldBe listOf(Subtype("Human"), Subtype("Faerie"), Subtype("Detective")) }
        alice.activates(tamsin, ability = 1)
        resolveAll()
        expect {
            tamsin.subtypes shouldBe listOf(Subtype("Human"), Subtype("Faerie"), Subtype("Rogue"))
            tamsin.power shouldBe 3
            tamsin.toughness shouldBe 2
            tamsin hasKeyword Keyword.DoubleStrike
        }
    }

    // CR 608.2b: a Scout isn't a Detective, so the second ability does nothing
    @Test
    fun tamsinStaysAScoutWithTheSecondAbility() = scenario {
        alice.battlefield(tamsin, "Mountain", "Mountain", "Mountain")
        alice.activates(tamsin, ability = 1)
        resolveAll()
        expect {
            tamsin.subtypes shouldBe listOf(Subtype("Human"), Subtype("Faerie"), Subtype("Scout"))
            tamsin.power shouldBe 2
            tamsin lacksKeyword Keyword.DoubleStrike
        }
    }

    // CR 608.2c: a creature card among those exiled drains 2
    @Test
    fun fetteredGhoulDrainsWhenItExilesACreatureCard() = scenario {
        alice.battlefield(*Array(4) { "Swamp" })
        alice.hand(fettered)
        bob.graveyard(bear, "Island")
        alice.casts(fettered)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(bear), target("Island"))))
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
            bob.graveyard lacks bear
        }
    }

    // CR 608.2c: only lands exiled, no drain
    @Test
    fun fetteredGhoulDoesNotDrainForLands() = scenario {
        alice.battlefield(*Array(4) { "Swamp" })
        alice.hand(fettered)
        bob.graveyard("Island", "Forest")
        alice.casts(fettered)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target("Island"), target("Forest"))))
        resolveAll()
        expect {
            bob.life shouldBe 20
            alice.life shouldBe 20
        }
    }

    // CR 614.1a, 611.3a: 24 + 1 + 1 = 26, so the Seraph gets +2/+2
    @Test
    fun seraphGainsOneMoreAndGrows() = scenario {
        alice.battlefield(seraph, "Plains")
        alice.hand(balm)
        alice.life = 24
        expect { seraph.power shouldBe 2 }
        alice.casts(balm)
        resolveAll()
        expect {
            alice.life shouldBe 26
            seraph.power shouldBe 4
            seraph.toughness shouldBe 4
        }
    }

    // CR 611.3a: double strike at 25 life, not at 24
    @Test
    fun templarHasDoubleStrikeAtTwentyFive() = scenario {
        alice.battlefield(templar, "Plains")
        alice.hand(balm)
        alice.life = 24
        expect { templar lacksKeyword Keyword.DoubleStrike }
        alice.casts(balm)
        resolveAll()
        expect {
            alice.life shouldBe 25
            templar hasKeyword Keyword.DoubleStrike
            templar.counters(p1p1) shouldBe 1
        }
    }

    // CR 614.1a, 113.6b: milled from the library, it is shuffled back instead
    @Test
    fun juggernautMilledGoesBackIntoTheLibrary() = scenario {
        alice.battlefield("Forest")
        alice.hand(mulch)
        alice.library(juggernaut, "Island")
        alice.casts(mulch)
        resolveAll()
        expect {
            alice.graveyard lacks juggernaut
            alice.library has juggernaut
            alice.library.size shouldBe 2
        }
    }

    // CR 614.1a, 101.4: sacrificed, it is shuffled into the library instead of dying
    @Test
    fun juggernautSacrificedGoesIntoTheLibrary() = scenario {
        alice.battlefield(juggernaut, "Swamp", "Forest")
        alice.hand(tincture)
        alice.casts(tincture)
        resolveAll()
        expect {
            alice.graveyard lacks juggernaut
            alice.library has juggernaut
        }
    }

    // CR 101.4, 608.2c: each sacrifices; Alice returns another permanent card to her hand
    @Test
    fun tinctureReturnsAnotherPermanentCard() = scenario {
        alice.battlefield(bear, "Swamp", "Forest")
        alice.hand(tincture)
        alice.graveyard(idol)
        bob.battlefield(ghoul)
        alice.casts(tincture)
        resolveAll()
        alice.chooses(idol)
        resolveAll()
        expect {
            alice.graveyard has bear
            bob.graveyard has ghoul
            alice.hand has idol
        }
    }

    // CR 603.2, 611.2a: the Goblin flies, deals combat damage and is sacrificed
    @Test
    fun rocketKickGoblinFliesAndIsSacrificed() = scenario {
        alice.battlefield(rocketKick, goblin, "Mountain")
        expect { goblin.power shouldBe 2 }
        alice.activates(rocketKick, target = goblin)
        resolveAll()
        expect { goblin hasKeyword Keyword.Flying }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(goblin)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.graveyard has goblin
        }
    }

    // CR 115.1: "another target Goblin": not itself
    @Test
    fun rocketKickGoblinCantTargetItself() = scenario {
        alice.battlefield(rocketKick, "Mountain")
        assertFailsWith<Throwable> { alice.activates(rocketKick, target = rocketKick) }
    }

    // CR 603.10a: an attacking creature died, so Alice draws
    @Test
    fun varnaDrawsForAnAttackerThatDied() = scenario {
        alice.battlefield(varna, bear)
        alice.library("Island")
        bob.battlefield(ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(ox to bear)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.hand has "Island"
            bob.life shouldBe 20
        }
    }

    // CR 608.2c: a creature that wasn't attacking died, so Varna deals 1 damage to Bob
    @Test
    fun varnaPingsWhenANonattackerDies() = scenario {
        alice.battlefield(varna, bear, "Swamp", "Forest")
        alice.hand(tincture)
        alice.library("Island")
        alice.casts(tincture)
        resolveAll()
        if (pending != null) alice.chooses(bear)
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.hand lacks "Island"
            bob.life shouldBe 19
        }
    }
}
