package arcanum.engine.sets.lci

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dgm.payIfAsked
import arcanum.engine.sets.ltr.hits
import arcanum.engine.sets.ltr.permitted
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** LCI chunk 09 played: an opponent's life loss doubled only during your turn, a "this way" Cave that gains life, an exile instead of dying
 * for the rest of the turn, a cost of artifacts totalling exactly X, mana of the colours of the cards a land exiled, a land made a creature
 * for good, each player's creatures swapped for their graveyard's, Dinosaurs cast from among the cards a creature exiled, a discarded card
 * cast for free, and mana only for creature spells of the chosen type. Invented names carry the oracle text. */
class LciChunk09ConditionsAndManaScenariosTest {
    private val revenant = Cards.invent("Bloodtithe Revenant", "{1}{B}{B}{B}", "Creature — Vampire Demon", """
        Flying
        If an opponent would lose life during your turn, they lose twice that much life instead. (Damage causes loss of life.)
    """, "2/4")
    private val delving = Cards.invent("Cavern Delving", "{2}{G}", "Enchantment", """
        When this enchantment enters, draw a card, then you may put a land card from your hand onto the battlefield. If you put a Cave onto the battlefield this way, you gain 4 life.
        Lands you control enter untapped.
    """)
    private val umbra = Cards.invent("Spiteful Umbra", "{1}{B}{B}", "Sorcery",
        "All creatures get -2/-2 until end of turn. If a creature an opponent controls would die this turn, exile it instead.")
    private val smeltworks = Cards.invent("Assembly Smeltworks", "{1}{W}", "Artifact", """
        {T}: Add {W}. Spend this mana only to cast an artifact spell or activate an ability of an artifact source.
        {2}{W}, {T}, Exile one or more other artifacts you control with total mana value X: Return target artifact card with mana value X or less from your graveyard to the battlefield. Activate only as a sorcery.
    """)
    private val hollow = Cards.invent("Hollow of Tribute", null, "Land — Cave", """
        This land enters tapped.
        When this land enters, exile up to three target cards from graveyards.
        {T}: Add {C}.
        {T}: Add one mana of any of the exiled cards' colors.
    """)
    private val sporeling = Cards.invent("Sporeling of the Deep", "{1}{G}", "Creature — Fungus Wizard",
        "{5}{G}{G}: Put seven +1/+1 counters on target noncreature land you control. It becomes a 0/0 Fungus creature with haste. It's still a land.", "2/2")
    private val herald = Cards.invent("Herald of the Final Offering", "{6}{B}{B}", "Creature — Vampire Demon", """
        Flying
        When this creature enters, if you cast it, each player sacrifices all other creatures they control. Then each player returns all creature cards from their graveyard that weren't put there this way to the battlefield.
    """, "6/6")
    private val fossilwright = Cards.invent("Bold Fossilwright", "{1}{G}", "Creature — Human Druid", """
        {T}: Add one mana of any color.
        {2}: Exile target card from a graveyard.
        You may cast Dinosaur creature spells from among cards you own exiled with this creature. If you cast a spell this way, that creature enters with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
    """, "2/2")
    private val marisol = Cards.invent("Marisol, Siren Corsair", "{1}{U}", "Legendary Creature — Siren Pirate", """
        Flash
        Flying
        Whenever Marisol deals combat damage to a player, put a chorus counter on it. Draw a card, then discard a card. If there are four or more chorus counters on Marisol, you may cast the discarded card without paying its mana cost.
    """, "2/1")
    private val grotto = Cards.invent("Grotto of Kinship", null, "Land", """
        As this land enters, choose a creature type.
        {T}: Add {C}.
        {T}: Add one mana of any color. Spend this mana only to cast a creature spell of the chosen type, and that spell can't be countered.
    """)

    private val lash = Cards.invent("Cinder Lash", "{R}", "Instant", "Cinder Lash deals 3 damage to any target.")
    private val tithe = Cards.invent("Bleak Levy", "{B}", "Sorcery", "Each player loses 2 life.")
    private val cave = Cards.invent("Glowmoss Cave", null, "Land — Cave", "{T}: Add {C}.")
    private val cog = Cards.invent("Brass Cog Idol", "{1}", "Artifact", "")
    private val gearwork = Cards.invent("Iron Gearwork", "{2}", "Artifact", "")
    private val lantern = Cards.invent("Tin Lantern", "{2}", "Artifact", "")
    private val golem = Cards.invent("Copperjaw Golem", "{3}", "Artifact Creature — Golem", "", "3/3")
    private val ox = Cards.invent("Fernwood Ox", "{1}{G}", "Creature — Ox", "", "2/2")
    private val boar = Cards.invent("Bramble Boar", "{2}{G}", "Creature — Boar", "", "3/3")
    private val hound = Cards.invent("Ashen Hound", "{1}{R}", "Creature — Dog", "", "2/2")
    private val wisp = Cards.invent("Tidecall Wisp", "{1}{U}", "Creature — Spirit", "", "1/1")
    private val saur = Cards.invent("Tuskfang Saur", "{1}{G}", "Creature — Dinosaur", "", "3/2")
    private val elf = Cards.invent("Mossglade Elf", "{G}", "Creature — Elf", "", "1/1")
    private val goblin = Cards.invent("Cragline Goblin", "{R}", "Creature — Goblin", "", "1/1")

    // CR 614.1a, 120.3a: during Alice's turn each opponent loses twice as much (damage and life loss alike); Alice's own loss isn't doubled
    @Test
    fun revenantDoublesEachOpponentsLossDuringYourTurn() = scenario(players = 3) {
        alice.battlefield(revenant, "Mountain", "Swamp")
        alice.hand(lash, tithe)
        alice.casts(lash, target = "bob")
        resolveAll()
        alice.casts(tithe)
        resolveAll()
        expect {
            bob.life shouldBe 10
            carol.life shouldBe 16
            alice.life shouldBe 18
        }
    }

    // CR 614.1a: during an opponent's turn the loss isn't doubled
    @Test
    fun revenantDoesNothingDuringAnotherTurn() = scenario {
        alice.battlefield(revenant, "Mountain")
        alice.hand(lash)
        turn(number = 2, active = bob, priority = alice)
        alice.casts(lash, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    // CR 608.2c: the Cave put onto the battlefield this way gains 4 life; it enters untapped (CR 614.1d)
    @Test
    fun delvingGainsLifeForACave() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(delving, cave)
        alice.library("Forest")
        alice.casts(delving)
        resolveAll()
        decision<Decision.ChooseObjects>()
        alice.chooses(cave)
        resolveAll()
        expect {
            cave.zone shouldBe Zone.Battlefield
            cave.tapped shouldBe false
            alice.life shouldBe 24
            alice.hand has "Forest"
        }
    }

    // CR 608.2c: a land that isn't a Cave gains nothing
    @Test
    fun delvingGainsNothingForAnotherLand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(delving, cave)
        alice.library("Forest")
        alice.casts(delving)
        resolveAll()
        decision<Decision.ChooseObjects>()
        alice.chooses("Forest")
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.hand has cave
        }
    }

    // CR 614.1a, 611.2c: Bob's creature is exiled instead of dying, Alice's dies; the replacement lasts the turn, so Bob's Boar that survived
    // the -2/-2 is exiled when it dies later
    @Test
    fun umbraExilesOpponentsCreaturesForTheTurn() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain", hound)
        alice.hand(umbra, lash)
        bob.battlefield(ox, boar)
        alice.casts(umbra)
        resolveAll()
        expect {
            bob.exile has ox
            alice.graveyard has hound
            boar.power shouldBe 1
        }
        alice.casts(lash, target = boar)
        resolveAll()
        expect { bob.exile has boar }
    }

    // CR 107.3a, 601.2h: X = 3 paid with artifacts of mana value 1 and 2, returning the 3-mana artifact card
    @Test
    fun smeltworksExilesArtifactsTotallingX() = scenario {
        alice.battlefield(smeltworks, cog, gearwork, "Plains", "Plains", "Plains")
        alice.graveyard(golem)
        alice.activates(smeltworks, ability = 1, x = 3, target = golem, choosing = listOf(cog, gearwork))
        resolveAll()
        expect {
            golem.zone shouldBe Zone.Battlefield
            alice.exile has cog
            alice.exile has gearwork
        }
    }

    // CR 601.2h: the exiled artifacts must total exactly X: a total of 3 doesn't pay X = 2, but 2 does
    @Test
    fun smeltworksRefusesATotalOtherThanX() = scenario {
        alice.battlefield(smeltworks, cog, gearwork, "Plains", "Plains", "Plains")
        alice.graveyard(lantern)
        assertFailsWith<IllegalArgumentException> { alice.activates(smeltworks, ability = 1, x = 2, target = lantern, choosing = listOf(cog, gearwork)) }
        // the refused answer leaves the choice open
        alice.chooses(gearwork)
        payIfAsked()
        resolveAll()
        expect {
            lantern.zone shouldBe Zone.Battlefield
            cog.zone shouldBe Zone.Battlefield
        }
    }

    // CR 607.2a: the cards it exiled as it entered give it their colours (red or blue, not green, CR 106.7)
    @Test
    fun hollowMakesTheExiledCardsColors() = scenario {
        alice.hand(hollow)
        alice.library("Plains", "Plains")
        bob.library("Island", "Island")
        bob.graveyard(lash, wisp)
        alice.plays(hollow)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(lash), target(wisp))))
        resolveAll()
        expect {
            bob.exile has lash
            bob.exile has wisp
        }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates(hollow, ability = 1, color = Color.Blue)
        expect { alice.pool.map { it.color } shouldBe listOf(Color.Blue) }
    }

    // CR 611.2a: for good; CR 205.1b: still a Cave land, now also a Fungus creature; 7/7 from the counters (CR 613.4b, 613.4c); haste
    @Test
    fun sporelingMakesALandACreatureForGood() = scenario {
        alice.battlefield(sporeling, cave, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.library("Forest", "Forest")
        bob.library("Island", "Island")
        alice.activates(sporeling, target = cave)
        resolveAll()
        expect {
            assertTrue(cave.types.containsAll(listOf(CardType.Land, CardType.Creature)))
            assertTrue(cave.subtypes.containsAll(listOf(Subtype("Cave"), Subtype("Fungus"))))
            cave.power shouldBe 7
            cave hasKeyword Keyword.Haste.kind
        }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        expect { assertTrue(CardType.Creature in cave.types) }
    }

    // CR 701.21a: each player sacrifices their own other creatures; CR 400.7: those cards are new objects and stay; the others return under
    // their owners' control (CR 110.2a)
    @Test
    fun heraldSwapsCreaturesForTheGraveyards() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", ox)
        alice.hand(herald)
        alice.graveyard(boar)
        bob.battlefield(hound)
        bob.graveyard(golem)
        carol.graveyard(wisp)
        alice.casts(herald)
        resolveAll()
        expect {
            herald.zone shouldBe Zone.Battlefield
            alice.graveyard has ox
            bob.graveyard has hound
            boar.controller shouldBe alice.id
            golem.controller shouldBe bob.id
            wisp.controller shouldBe carol.id
            boar.zone shouldBe Zone.Battlefield
            golem.zone shouldBe Zone.Battlefield
            wisp.zone shouldBe Zone.Battlefield
        }
    }

    // CR 607.2a: a Dinosaur card Alice owns exiled with it can be cast; CR 122.1h: it enters with a finality counter
    @Test
    fun fossilwrightCastsAnExiledDinosaur() = scenario {
        alice.battlefield(fossilwright, "Forest", "Forest", "Forest", "Forest")
        alice.graveyard(saur)
        alice.activates(fossilwright, ability = 1, target = saur, paying = listOf("Forest", "Forest"))
        resolveAll()
        expect { alice.exile has saur }
        alice.casts(saur, way = permitted(saur))
        resolveAll()
        expect {
            saur.zone shouldBe Zone.Battlefield
            saur.counters(CounterKind.Named("finality")) shouldBe 1
        }
    }

    // CR 608.2g: with a fourth chorus counter the discarded card is cast without paying its mana cost while the trigger resolves
    @Test
    fun marisolCastsTheDiscardedCard() = scenario {
        alice.battlefield(marisol, counters = listOf(CounterKind.Named("chorus") to 3))
        alice.hand(lash)
        alice.library("Island")
        hits(marisol)
        resolveAll()
        decision<Decision.ChooseObjects>()
        alice.chooses(lash)
        decision<Decision.May>()
        alice.answers(Answer.YesNo(true))
        alice.answers(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        expect {
            marisol.counters(CounterKind.Named("chorus")) shouldBe 4
            bob.life shouldBe 15
            alice.graveyard has lash
        }
    }

    // CR 608.2c: with three chorus counters after the trigger, no cast is offered
    @Test
    fun marisolNeedsFourCounters() = scenario {
        alice.battlefield(marisol, counters = listOf(CounterKind.Named("chorus") to 2))
        alice.hand(lash)
        alice.library("Island")
        hits(marisol)
        resolveAll()
        decision<Decision.ChooseObjects>()
        alice.chooses(lash)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.graveyard has lash
        }
    }

    // CR 106.6: its coloured mana pays for a creature spell of the chosen type (Elf), not another (Goblin); CR 106.6a: that spell can't be countered
    @Test
    fun grottoManaOnlyForTheChosenType() = scenario {
        alice.hand(grotto, elf, goblin)
        alice.plays(grotto)
        decision<Decision.ChooseSubtype>()
        alice.answers(Answer.SubtypeChoice(Subtype("Elf")))
        alice.casts(goblin)
        expect { decision<Decision.ManaPayment>().suggestion.isEmpty() shouldBe true }
    }

    // CR 106.6: the Elf is paid for with it
    @Test
    fun grottoPaysForTheChosenType() = scenario {
        alice.hand(grotto, elf)
        alice.plays(grotto)
        decision<Decision.ChooseSubtype>()
        alice.answers(Answer.SubtypeChoice(Subtype("Elf")))
        alice.casts(elf)
        resolveAll()
        expect { elf.zone shouldBe Zone.Battlefield }
    }
}
