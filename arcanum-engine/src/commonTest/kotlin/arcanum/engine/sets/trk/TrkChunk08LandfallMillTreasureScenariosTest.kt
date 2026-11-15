package arcanum.engine.sets.trk

import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Layout
import arcanum.api.PredefinedTokens
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dft.hits
import arcanum.engine.sets.ltr.permitted
import arcanum.engine.sets.spm.loreChapter
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** Star Trek chunk 08 in play: a New Planet land token, mana from a landfall trigger, quest counters with "exile until" and play, the first
 * spell you've cast this game, "If you can't" after a return, Treasure tokens you can't sacrifice, a delayed combat damage trigger for the
 * turn, a cast permission once each turn with a finality counter, and a destruction replacement that heals. */
class TrkChunk08LandfallMillTreasureScenariosTest {
    private val wayfinder = Cards.invent("Saurian Wayfinder", "{3}{G}", "Creature — Dinosaur Scout", """
        Landfall — Whenever a land you control enters, this creature gets +2/+2 until end of turn.
        {6}: Create a tapped Planet land token named New Planet with "{T}: Add one mana of any color." Activate only as a sorcery.
    """, "4/3")
    private val rinn = Cards.invent("Tavo Rinn, Augment Orphan", "{1}{R}{G}", "Legendary Creature — Human Mutant Rogue", """
        Landfall — Whenever a land you control enters, add {R} or {G}.
        {3}, Sacrifice a land: Draw a card.
    """, "3/3")
    private val expedition = Cards.invent("Survey Expedition", "{2}{R}{R}", "Enchantment",
        "Landfall — Whenever a land you control enters, put a quest counter on this enchantment. Then exile cards from the top of your library until you exile a nonland card. You may play that card this turn. If this enchantment has five or more quest counters on it, add {R}{R}.")
    private val greeting = Cards.invent("First Greeting", "{1}{G}", "Instant",
        "Mill four cards. You may put a permanent card from among them into your hand. If this spell is the first spell you've cast this game, you gain 2 life. (To mill four cards, put the top four cards of your library into your graveyard.)")
    private val raider = Cards.invent("Chronal Raider", "{2}{G}", "Creature — Na'kuhl Warrior",
        "When this creature enters, mill three cards. Then return a land card from your graveyard to your hand. If you can't, create a Treasure token. (To mill three cards, put the top three cards of your library into your graveyard.)",
        "2/3")
    private val brint = Cards.invent("Brint, Humble Proprietor", "{2}{R}", "Legendary Creature — Ferengi Rogue", """
        You can't sacrifice Treasure tokens.
        At the beginning of each upkeep, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """, "1/2")
    private val quietLife = Cards.invent("The Quiet Life", "{3}{G}{W}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Return target green or white creature card from your graveyard to the battlefield.
        II — Choose target creature. Whenever it deals combat damage to a player this turn, you draw that many cards.
        III — You gain life equal to the greatest power among creatures you control. Create a Treasure token.
    """)
    private val submatrix = Cards.invent("Spillover Submatrix", "{2}{B}", "Artifact Creature — Borg",
        "Once during each of your turns, you may cast an artifact creature spell from your graveyard. If you cast a spell this way, that artifact creature enters with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)",
        "3/3")
    private val gravecaller = Cards.invent("Trk8 Gravecaller", "{3}{W}", "Creature — Human Cleric",
        "During your turn, you may cast creature spells with power or toughness 1 or less from your graveyard. If you cast a spell this way, that creature enters with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)",
        "3/3")
    private val halvor = Cards.invent("Dr. Halvor, Surly Surgeon", "{2}{W}{W}", "Legendary Creature — Human Doctor", """
        When Dr. Halvor enters, put a +1/+1 counter on another target creature you control.
        If another creature you control with a +1/+1 counter on it would be destroyed, instead remove all +1/+1 counters from it and all damage already dealt to it is healed.
    """, "2/2")
    private val bear = Cards.invent("Trk8 Grove Bear", "{1}{R}", "Creature — Bear", "", "2/2")
    private val cub = Cards.invent("Trk8 Grove Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val golem = Cards.invent("Trk8 Scrap Golem", "{1}", "Artifact Creature — Golem", "", "1/1")
    private val drone = Cards.invent("Trk8 Scrap Drone", "{1}", "Artifact Creature — Construct", "", "1/1")
    private val squire = Cards.invent("Trk8 Sewer Page", "{W}", "Creature — Human Soldier", "", "1/1")
    private val tonic = Cards.invent("Trk8 Tonic", "{W}", "Instant", "You gain 1 life.")
    private val blast = Cards.invent("Trk8 Plasma Blast", "{R}", "Instant", "Trk8 Plasma Blast deals 3 damage to target creature.")
    private val zap = Cards.invent("Trk8 Phaser Zap", "{R}", "Instant", "Trk8 Phaser Zap deals 1 damage to target creature.")
    private val unmake = Cards.invent("Trk8 Unmake", "{B}", "Instant", "Destroy target creature.")
    private val doom = Cards.invent("Trk8 Doom", "{B}", "Instant", "Destroy target creature.")
    private val treasure = Cards.define("Trk8 Treasure Proxy", CardDefinition(Layout.Normal, listOf(PredefinedTokens.face("Treasure")!!.copy(name = "Trk8 Treasure Proxy"))))
    private val relic = Cards.invent("Trk8 Sealed Relic", "{2}", "Artifact", """
        This artifact can't be sacrificed.
        {1}, Sacrifice this artifact: Draw a card.
    """)
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    // ---- Landfall

    // CR 111.4, 205.3i: a tapped colorless Planet land token named New Planet whose ability adds one mana of any color
    @Test
    fun wayfinderCreatesATappedNewPlanet() = scenario {
        alice.battlefield(wayfinder, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(wayfinder)
        resolveAll()
        expect {
            "New Planet".tapped shouldBe true
            "New Planet".types shouldBe listOf(CardType.Land)
            "New Planet".subtypes shouldBe listOf(Subtype("Planet"))
            "New Planet".colors shouldBe emptyList()
            "New Planet".view.characteristics!!.abilities.size shouldBe 1
        }
    }

    // CR 605.1b: the landfall mana trigger uses the Stack; the player chooses red or green as it resolves (CR 106.1)
    @Test
    fun rinnAddsTheChosenColorWhenALandEnters() = scenario {
        alice.battlefield(rinn)
        alice.hand("Forest")
        alice.plays("Forest")
        expect { alice.pool.size shouldBe 0 }
        resolveAll()
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        expect { alice.pool.map { it.color } shouldBe listOf(Color.Green) }
    }

    // CR 122.1, 608.2c: the fifth quest counter goes on first, so the same trigger adds {R}{R}; the exiled nonland card may be played this turn
    @Test
    fun expeditionAddsRedRedAtFiveQuestCounters() = scenario {
        alice.battlefield(expedition, counters = listOf(CounterKind.Named("quest") to 4))
        alice.hand("Mountain")
        alice.library("Island", bear, "Forest")
        alice.plays("Mountain")
        resolveAll()
        expect {
            expedition.counters(CounterKind.Named("quest")) shouldBe 5
            alice.exile has "Island"
            alice.exile has bear
            alice.pool.map { it.color } shouldBe listOf(Color.Red, Color.Red)
        }
        alice.casts(bear)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    // CR 608.2c: with fewer than five quest counters, no mana
    @Test
    fun expeditionAddsNothingBelowFiveQuestCounters() = scenario {
        alice.battlefield(expedition)
        alice.hand("Mountain")
        alice.library(bear, "Forest")
        alice.plays("Mountain")
        resolveAll()
        expect {
            expedition.counters(CounterKind.Named("quest")) shouldBe 1
            alice.pool.size shouldBe 0
        }
    }

    // ---- Mill

    // CR 601.2i: the first spell Alice has cast this game, though Bob cast one before it: she gains 2 life and keeps a permanent card
    @Test
    fun firstGreetingAsYourFirstSpellGainsLife() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(greeting)
        alice.library("Island", bear, "Island", "Island", "Island")
        bob.battlefield("Plains")
        bob.hand(tonic)
        turn(number = 2, active = bob)
        bob.casts(tonic)
        resolveAll()
        bob.passes()
        alice.casts(greeting)
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        expect {
            alice.life shouldBe 22
            bob.life shouldBe 21
            alice.hand has bear
            alice.graveyard.size shouldBe 4
        }
    }

    // CR 601.2i: a spell Alice cast earlier this game, even one that resolved, means no life
    @Test
    fun firstGreetingAfterAnotherSpellGainsNoLife() = scenario {
        alice.battlefield("Forest", "Forest", "Plains")
        alice.hand(tonic, greeting)
        alice.library("Island", "Island", "Island", "Island", "Island")
        alice.casts(tonic)
        resolveAll()
        alice.casts(greeting)
        resolveAll()
        expect {
            alice.life shouldBe 21
            alice.graveyard.names.count { it == "Island" } shouldBe 4
        }
    }

    // CR 608.2c: a land card among the milled ones comes back, so no Treasure
    @Test
    fun raiderReturnsALandCard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(raider)
        alice.library(bear, "Island", cub, "Forest")
        alice.casts(raider)
        resolveAll()
        expect {
            alice.hand has "Island"
            alice.battlefield lacks "Treasure Token"
        }
    }

    // CR 608.2c ("If you can't"): no land card in the graveyard to return: a Treasure
    @Test
    fun raiderWithoutALandCardMakesATreasure() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(raider)
        alice.library(bear, cub, golem, "Forest")
        alice.casts(raider)
        resolveAll()
        expect {
            alice.hand.size shouldBe 0
            alice.battlefield has "Treasure Token"
        }
    }

    // ---- Treasure

    // CR 701.21a, 118.3: Alice can't sacrifice her Treasure token, so she can't pay its cost; a Treasure that isn't a token still can be
    @Test
    fun brintStopsYouFromSacrificingTreasureTokens() = scenario {
        alice.battlefield(brint, treasure)
        alice.library("Mountain", "Mountain")
        bob.library("Plains", "Plains")
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect { alice.battlefield has "Treasure Token" }
        // CR 503.1a: "each upkeep": Bob's too; Alice acts once Bob passes
        bob.passes()
        val token = card("Treasure Token")
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().none { it.source == token })
        assertFails { alice.activates("Treasure Token", color = Color.Red) }
        alice.activates(treasure, color = Color.Red)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Red))
        expect {
            alice.battlefield has "Treasure Token"
            alice.graveyard has treasure
        }
    }

    // CR 701.21a, 118.3: "Sacrifice this artifact" can't be paid by an artifact that can't be sacrificed (it was paid before)
    @Test
    fun aPermanentThatCantBeSacrificedCantPayItsOwnSacrificeCost() = scenario {
        alice.battlefield(relic, "Plains")
        alice.library("Island")
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().none { it.source == card(relic) })
        assertFails { alice.activates(relic) }
        expect {
            alice.battlefield has relic
            alice.hand.size shouldBe 0
        }
    }

    // CR 603.7b, 120.4: chapter II's delayed trigger draws as many cards as the chosen creature dealt combat damage
    @Test
    fun quietLifeDrawsThatManyOnCombatDamage() = scenario {
        alice.battlefield(bear)
        loreChapter(quietLife, 2, "Island", "Island", "Island")
        resolveAll()
        val before = state.player(alice.id).hand.size
        hits(bear)
        expect {
            bob.life shouldBe 18
            alice.hand.size shouldBe before + 2
        }
    }

    // ---- Cast permission, replacement

    // CR 601.2a, 400.7d, 122.1h: one artifact creature spell from the graveyard each of your turns, entering with a finality counter
    @Test
    fun submatrixCastsOneArtifactCreatureEachTurnWithFinality() = scenario {
        alice.battlefield(submatrix, "Swamp", "Swamp")
        alice.graveyard(golem, drone)
        alice.casts(golem, way = permitted(golem))
        resolveAll()
        expect { golem.counters(CounterKind.Finality) shouldBe 1 }
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Cast>().none { it.card == card(drone) })
    }

    // CR 122.1h, 704.5f: a creature cast with a finality counter is exiled instead of dying (the counter was a plain named counter before)
    @Test
    fun aFinalityCounterFromAPermissionExilesTheCreature() = scenario {
        alice.battlefield(gravecaller, "Plains", "Mountain")
        alice.graveyard(squire)
        alice.hand(zap)
        alice.casts(squire, way = permitted(squire))
        resolveAll()
        alice.casts(zap, target = squire)
        resolveAll()
        expect { alice.exile has squire }
    }

    // CR 614.1a, 120.6: lethal damage would destroy it (CR 704.5g); instead its +1/+1 counters go and the damage is healed, so it survives
    @Test
    fun halvorSavesACreatureWithACounterFromLethalDamage() = scenario {
        alice.battlefield(halvor, "Mountain")
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.hand(blast)
        alice.casts(blast, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            bear.counters(p1p1) shouldBe 0
            card(bear).view.obj?.damage shouldBe 0
            bear.power shouldBe 2
        }
    }

    // CR 614.1a: a destroy effect is replaced too; a creature without a +1/+1 counter is destroyed
    @Test
    fun halvorReplacesDestructionOnlyWithACounter() = scenario {
        alice.battlefield(halvor, "Swamp", "Swamp", "Mountain")
        alice.battlefield(bear, counters = listOf(p1p1 to 2))
        alice.battlefield(cub)
        alice.hand(zap, unmake, doom)
        alice.casts(zap, target = bear)
        resolveAll()
        alice.casts(unmake, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            bear.counters(p1p1) shouldBe 0
            card(bear).view.obj?.damage shouldBe 0
        }
        alice.casts(doom, target = cub)
        resolveAll()
        expect { alice.graveyard has cub }
    }
}
