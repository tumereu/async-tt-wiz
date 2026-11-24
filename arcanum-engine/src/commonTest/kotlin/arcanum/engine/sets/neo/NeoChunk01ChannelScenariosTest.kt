package arcanum.engine.sets.neo

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Chunk 1 of NEO: Channel (abilities of a card in hand, CR 113.6) and the Auras that bonus only a creature (CR 303.4e). */
class NeoChunk01ChannelScenariosTest {
    private val bear = Cards.invent("Neo Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val bear2 = Cards.invent("Neo Cub", "{1}{G}", "Creature — Bear", "", "1/1")
    private val trinket = Cards.invent("Neo Trinket", "{1}", "Artifact", "")
    private val cart = Cards.invent("Neo Cart", "{2}", "Artifact — Vehicle", "Crew 1", "3/3")
    private val legend = Cards.invent("Neo Champion, Bold", "{1}{W}", "Legendary Creature — Human Samurai", "", "2/2")
    private val sorcery = Cards.invent("Neo Spark", "{R}", "Sorcery", "Neo Spark deals 1 damage to any target.")
    private val favor = Cards.invent("Favor of the Grove", "{3}{G}", "Enchantment — Aura", """
        Enchant artifact or creature
        As long as enchanted permanent is a creature, it gets +3/+3 and has reach.
        Channel — {1}{G}, Discard this card: Target creature gets +3/+3 and gains reach until end of turn.
    """)
    private val roam = Cards.invent("Born to Roam", "{2}{W}", "Enchantment — Aura", """
        Enchant artifact or creature
        As long as enchanted permanent is a creature, it gets +1/+1 for each creature and/or Vehicle you control.
        Channel — {2}{W}, Discard this card: Create two 1/1 colorless Pilot creature tokens with "This token crews Vehicles as though its power were 2 greater."
    """)
    private val shikoku = Cards.invent("Shikoku, Jukai Seer", "{1}{G}", "Legendary Enchantment Creature — Snake Druid", """
        {1}{G}, {T}, Return Shikoku to its owner's hand: Reveal the top four cards of your library. You may put a land card from among them onto the battlefield tapped. Put the rest into your graveyard.
        Channel — {X}{X}{G}{G}, Discard this card: Return X target nonlegendary cards from your graveyard to your hand.
    """, "1/3")
    private val otawara = Cards.invent("Otawara, Rising City", null, "Legendary Land", """
        {T}: Add {U}.
        Channel — {3}{U}, Discard this card: Return target artifact, creature, enchantment, or planeswalker to its owner's hand. This ability costs {1} less to activate for each legendary creature you control.
    """)
    private val moonsnare = Cards.invent("Moonsnare Model", "{U}", "Artifact", """
        {T}, Tap an untapped artifact or creature you control: Add {C}.
        Channel — {4}{U}, Discard this card: The owner of target nonland permanent puts it on their choice of the top or bottom of their library.
    """)
    private val roaringSoil = Cards.invent("Roaring Soil", "{1}{G}", "Enchantment", """
        Landfall — Whenever a land you control enters, put a +1/+1 counter on target creature or Vehicle you control.
        Channel — {X}{G}{G}, Discard this card: Put X +1/+1 counters on target land you control. It becomes a 0/0 green Spirit creature with haste. It's still a land.
    """)
    private val spiritWorld = Cards.invent("Touch the Spirit World", "{2}{W}", "Enchantment", """
        When this enchantment enters, exile up to one target artifact or creature until this enchantment leaves the battlefield.
        Channel — {1}{W}, Discard this card: Exile target artifact or creature. Return it to the battlefield under its owner's control at the beginning of the next end step.
    """)

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 303.4e, 613.4c: the enchanted creature gets the bonus, not the Aura; CR 205.1a: a non-creature artifact gets nothing
    @Test
    fun favorBonusesTheEnchantedCreatureOnly() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", bear, trinket)
        alice.hand(favor)
        alice.casts(favor, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear.toughness shouldBe 5
            bear hasKeyword Keyword.Reach.kind
            favor.zone shouldBe Zone.Battlefield
            favor.types shouldBe listOf(CardType.Enchantment)
        }
    }

    // CR 205.1a: the same Aura on a noncreature artifact does nothing until it is a creature
    @Test
    fun favorOnANonCreatureArtifactDoesNothing() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", trinket)
        alice.hand(favor)
        alice.casts(favor, target = trinket)
        resolveAll()
        expect {
            favor.zone shouldBe Zone.Battlefield
            trinket.power shouldBe null
            trinket lacksKeyword Keyword.Reach.kind
        }
    }

    // CR 613.4c: +1/+1 for each creature and/or Vehicle you control; the Vehicle counts while not a creature (CR 301.7)
    @Test
    fun roamCountsCreaturesAndVehicles() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear, bear2, cart)
        bob.battlefield(bear2.let { Cards.invent("Neo Guard", "{1}{G}", "Creature — Bear", "", "2/2") })
        alice.hand(roam)
        alice.casts(roam, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear.toughness shouldBe 5
            cart.types shouldBe listOf(CardType.Artifact)
        }
    }

    // CR 113.6, 602.2: a channel ability is activated from hand, its cost discards the card; CR 111.1: two tokens
    @Test
    fun roamChannelMakesTwoPilots() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(roam)
        alice.activates(roam)
        resolveAll()
        expect {
            roam.zone shouldBe Zone.Graveyard
            "Pilot Token".power shouldBe 1
        }
    }

    // CR 107.3a, 115.1: X targets, nonlegendary only (CR 205.4); the legendary card can't be chosen
    @Test
    fun shikokuChannelReturnsXNonlegendaryCards() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(shikoku)
        alice.graveyard(bear, bear2, legend)
        alice.activates(shikoku, ability = 1, x = 2, targets = listOf(bear, bear2))
        resolveAll()
        expect {
            alice.hand has bear
            alice.hand has bear2
            alice.graveyard has legend
            alice.graveyard has shikoku
        }
    }

    @Test
    fun shikokuChannelCantTargetALegendaryCard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(shikoku)
        alice.graveyard(legend)
        assertFailsWith<IllegalArgumentException> { alice.activates(shikoku, ability = 1, x = 1, targets = listOf(legend)) }
    }

    // CR 601.2f, 602.2b: the cost of the ability drops by {1} for each legendary creature you control
    @Test
    fun otawaraCostsLessWithALegend() = scenario {
        alice.battlefield("Island", "Island", "Island", legend)
        alice.hand(otawara)
        bob.battlefield(bear)
        alice.activates(otawara, ability = 1, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Hand
            otawara.zone shouldBe Zone.Graveyard
        }
    }

    // CR 118.3: without a legendary creature the full {3}{U} can't be paid with three lands
    @Test
    fun otawaraCostsFullWithoutALegend() = scenario {
        alice.battlefield("Island", "Island", "Island", bear2)
        alice.hand(otawara)
        bob.battlefield(bear)
        alice.activates(otawara, ability = 1, target = bear)
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    // CR 602.2b, 118.3 (audit): "an untapped artifact or creature you control" is the activator's own permanent
    @Test
    fun moonsnareCantTapAnOpponentsCreature() = scenario {
        alice.battlefield(moonsnare)
        bob.battlefield(bear)
        assertFailsWith<IllegalArgumentException> { alice.activates(moonsnare) }
    }

    @Test
    fun moonsnareTapsYourOwnCreatureForColorlessMana() = scenario {
        alice.battlefield(moonsnare, bear)
        alice.activates(moonsnare, choosing = listOf(bear))
        expect {
            moonsnare.tapped shouldBe true
            bear.tapped shouldBe true
            alice.pool.size shouldBe 1
        }
    }

    // CR 701.20 n/a; CR 401.4: the owner chooses the top or the bottom of their library
    @Test
    fun moonsnareChannelLetsTheOwnerChoose() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(moonsnare)
        bob.battlefield(bear)
        bob.library("Forest")
        alice.activates(moonsnare, ability = 1, target = bear)
        resolveAll()
        bob.answers(Answer.Option("top"))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Library
        }
    }

    // CR 205.1b ("still a land"), 122.1: X counters make the 0/0 Spirit land a 2/2 creature with haste
    @Test
    fun roaringSoilChannelAnimatesALand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Mountain")
        alice.hand(roaringSoil)
        alice.activates(roaringSoil, x = 2, target = "Mountain")
        resolveAll()
        expect {
            "Mountain".power shouldBe 2
            "Mountain".types shouldBe listOf(CardType.Land, CardType.Creature)
            "Mountain" hasKeyword Keyword.Haste.kind
            "Mountain".counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
        }
    }

    // CR 603.7: the exiled permanent returns under its owner's control at the beginning of the next end step
    @Test
    fun spiritWorldChannelExilesThenReturnsAtEndStep() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(spiritWorld)
        bob.battlefield(bear)
        alice.activates(spiritWorld, target = bear)
        resolveAll()
        expect { bear.zone shouldBe Zone.Exile }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { bear.zone shouldBe Zone.Battlefield }
    }
}
