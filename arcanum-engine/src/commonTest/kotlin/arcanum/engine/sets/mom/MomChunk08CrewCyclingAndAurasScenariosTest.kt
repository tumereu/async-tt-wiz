package arcanum.engine.sets.mom

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.parser.OracleCard
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ecl.say
import arcanum.engine.sets.fin.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** MOM chunk 08: a Vehicle's mana ability usable only while it is a creature, an attack trigger that taps two other creatures of your own (three
 * players), transforming a target Incubator and doubling its counters, an attack trigger only on attacking a battle, and Auras: one that leaves
 * mana abilities usable, one that sets base power and toughness and keeps only defender, one whose "when enchanted creature dies" sees its own
 * -4/-4 kill, and one counting its controller's graveyard after each player mills (three players). */
class MomChunk08CrewCyclingAndAurasScenariosTest {
    private val sprinter = Cards.invent("Gearwheel Sprinter", "{2}", "Artifact — Vehicle", """
        Vigilance
        {T}: Add one mana of any color. Activate only if this permanent is a creature.
        Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "3/2")
    private val terror = Cards.invent("Tidewater Terror", "{4}{U}{U}", "Creature — Octopus", """
        Whenever this creature attacks, you may tap two other untapped creatures you control. If you do, this creature can't be blocked this turn.
        Islandcycling {2} ({2}, Discard this card: Search your library for an Island card, reveal it, put it into your hand, then shuffle.)
    """.trimIndent(), "5/6")
    private val vatwarden = Cards.invent("Elvish Vatwarden", "{1}{B}{G}", "Creature — Phyrexian Elf", """
        When this creature enters, incubate 2. (Create an Incubator token with two +1/+1 counters on it and "{2}: Transform this token." It transforms into a 0/0 Phyrexian artifact creature.)
        {5}: Transform target Incubator token you control. Double the number of +1/+1 counters on it.
    """.trimIndent(), "3/3")
    private val slasher = Cards.invent("Battle-Trained Slasher", "{3}{R}", "Creature — Wolverine Dinosaur", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Whenever this creature attacks a battle, double its power until end of turn.
    """.trimIndent(), "4/3")
    private val grasp = Cards.invent("Realmsplitter's Grasp", "{1}{W}", "Enchantment — Aura", """
        Enchant artifact or creature
        Enchanted permanent can't attack or block, and its activated abilities can't be activated unless they're mana abilities.
    """.trimIndent())
    private val stillness = Cards.invent("Stillness Field", "{1}{U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature has base power and toughness 0/2, has defender, and loses all other abilities.
    """.trimIndent())
    private val botched = Cards.invent("Botched Conversion", "{4}{B}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets -4/-4.
        When enchanted creature dies, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """.trimIndent())
    private val unsettling = Cards.invent("Unsettling Conversion", "{1}{U}", "Enchantment — Aura", """
        Flash
        Enchant creature
        When this Aura enters, each player mills two cards.
        Enchanted creature gets -X/-0, where X is the number of cards in its controller's graveyard.
    """.trimIndent())

    private val siege = Cards.inventFaces("Transform",
        OracleCard("Siege of the Rusted Spire", "{2}{R}", "Battle — Siege", "", defense = "5"),
        OracleCard("Rusted Spire Keeper", null, "Creature — Construct", "", "2", "2", colorIndicator = "R"))
    private val bear = Cards.invent("Vatside Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val elk = Cards.invent("Vatside Elk", "{2}{G}", "Creature — Elk", "", "2/3")
    private val stag = Cards.invent("Vatside Stag", "{1}{G}", "Creature — Elk", "", "2/2")
    private val hound = Cards.invent("Vatside Hound", "{1}{R}", "Creature — Dog", "", "2/2")
    private val hulk = Cards.invent("Vatside Hulk", "{4}{G}", "Creature — Beast", "", "4/4")
    private val brute = Cards.invent("Vatside Brute", "{4}{G}", "Creature — Beast", "", "5/5")
    private val drake = Cards.invent("Vatside Drake", "{2}{U}", "Creature — Drake", """
        Flying
        {1}: This creature gets +1/+0 until end of turn.
    """.trimIndent(), "3/3")
    private val golem = Cards.invent("Vatside Golem", "{3}", "Artifact Creature — Golem", """
        {T}: Add {C}.
        {1}: This creature gets +1/+0 until end of turn.
    """.trimIndent(), "2/2")
    private val zap = Cards.invent("Vatside Zap", "{R}", "Instant", "Vatside Zap deals 1 damage to any target.")

    private val incubator = "Incubator Token"
    private fun Scenario.libraries(count: Int = 6) = players.forEach { p -> p.library(*Array(count) { "Island" }) }

    // CR 602.5b: "Activate only if this permanent is a creature": an uncrewed Vehicle is no creature (CR 301.7b); crewed, it is (CR 702.122a)
    // and its mana ability adds one mana of the chosen colour (CR 106.1)
    @Test
    fun theRacersManaAbilityWorksOnlyWhileItIsACreature() = scenario {
        alice.battlefield(sprinter, bear)
        assertFailsWith<IllegalArgumentException> { alice.activates(sprinter, ability = 0, color = Color.Red) }
        alice.activates(sprinter, ability = 1, choosing = listOf(bear))
        resolveAll()
        expect { sprinter.types.contains(CardType.Creature) shouldBe true }
        alice.activates(sprinter, ability = 0, color = Color.Red)
        expect {
            sprinter.tapped shouldBe true
            alice.pool.map { it.color } shouldBe listOf(Color.Red)
        }
    }

    // CR 118.12, 603.5: the optional cost taps two other untapped creatures of its controller's only, never an opponent's (three players);
    // CR 509.1b: then it can't be blocked
    @Test
    fun theTerrorTapsTwoOfItsControllersCreaturesToGetThrough() = scenario(players = 3) {
        alice.battlefield(terror, bear, elk, stag)
        bob.battlefield(hound)
        carol.battlefield(hulk)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(terror, defender = "bob")
        resolveAll()
        say(true)
        val choice = pending as Decision.ChooseObjects
        assertEquals(setOf(card(bear), card(elk), card(stag)), choice.options.toSet())
        alice.chooses(bear, elk)
        resolveAll()
        expect {
            terror hasMechanic { it is Mechanic.CantBeBlocked }
            bear.tapped shouldBe true
            elk.tapped shouldBe true
            stag.tapped shouldBe false
            hound.tapped shouldBe false
            hulk.tapped shouldBe false
        }
    }

    // CR 701.27a: the target Incubator transforms into a Phyrexian artifact creature; CR 701.10e: its two +1/+1 counters double to four on the
    // same permanent (CR 711.8)
    @Test
    fun theVatwardenTransformsAnIncubatorAndDoublesItsCounters() = scenario {
        alice.battlefield("Swamp", "Forest", *Array(6) { "Forest" })
        alice.hand(vatwarden)
        alice.casts(vatwarden)
        resolveAll()
        expect { incubator.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2 }
        alice.activates(vatwarden, target = incubator)
        resolveAll()
        expect {
            incubator.types shouldBe listOf(CardType.Artifact, CardType.Creature)
            incubator.counters(CounterKind.PowerToughness(1, 1)) shouldBe 4
            incubator.power shouldBe 4
            incubator.toughness shouldBe 4
        }
    }

    // CR 506.2, 508.1b: attacking a battle triggers it; CR 701.10b: +X/+0 where X is its power as the ability resolves
    @Test
    fun theSlasherDoublesItsPowerAttackingABattle() = scenario {
        alice.battlefield(siege, counters = listOf(CounterKind.Defense to 5))
        alice.battlefield(slasher)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(slasher, defender = siege)
        resolveAll()
        expect { slasher.power shouldBe 8 }
    }

    // CR 603.2: attacking a player isn't attacking a battle, so it doesn't trigger
    @Test
    fun theSlasherKeepsItsPowerAttackingAPlayer() = scenario {
        alice.battlefield(siege, counters = listOf(CounterKind.Defense to 5))
        alice.battlefield(slasher)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(slasher, defender = "bob")
        resolveAll()
        expect {
            slasher.power shouldBe 4
            stack.isEmpty()
        }
    }

    // CR 605.1a, 602.5: the enchanted artifact creature's mana ability can still be activated, its other activated ability can't; CR 508.1c:
    // it can't attack
    @Test
    fun theGraspLeavesManaAbilitiesUsable() = scenario {
        alice.battlefield(golem, "Plains", "Plains", "Plains")
        alice.hand(grasp)
        libraries()
        alice.casts(grasp, target = golem)
        resolveAll()
        assertFailsWith<IllegalArgumentException> { alice.activates(golem, ability = 1) }
        alice.activates(golem, ability = 0)
        expect {
            golem.tapped shouldBe true
            alice.pool.size shouldBe 1
        }
    }

    // CR 508.1c: the enchanted creature can't attack
    @Test
    fun theGraspedCreatureCantAttack() = scenario {
        alice.battlefield(golem, "Plains", "Plains", "Plains")
        alice.hand(grasp)
        libraries()
        alice.casts(grasp, target = golem)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(golem, defender = "bob") }
    }

    // CR 613.4b: base 0/2, then its +1/+1 counter in layer 7c (CR 613.4c) makes it 1/3; CR 613.1f: it keeps defender and loses flying and its
    // activated ability (one effect, the granted defender not lost)
    @Test
    fun theFieldSetsBasePowerAndToughnessAndKeepsOnlyDefender() = scenario {
        bob.battlefield(drake, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.battlefield("Island", "Island")
        alice.hand(stillness)
        alice.casts(stillness, target = drake)
        resolveAll()
        expect {
            drake.power shouldBe 1
            drake.toughness shouldBe 3
            drake hasKeyword Keyword.Defender.kind
            drake lacksKeyword Keyword.Flying.kind
        }
    }

    // CR 704.5f, 603.10a: the -4/-4 kills a 4/4 and the Aura goes to the graveyard with it (CR 704.5m), yet its leaves-the-battlefield trigger
    // looks back and surveils 2 (CR 701.25a) for the Aura's controller
    @Test
    fun theConversionSurveilsWhenItsCreatureDies() = scenario {
        bob.battlefield(hulk)
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(botched)
        alice.library(bear, elk, "Island")
        alice.casts(botched, target = hulk)
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        expect {
            bob.graveyard has hulk
            alice.graveyard has botched
            alice.graveyard has bear
            alice.library.names.first() shouldBe elk
        }
    }

    // CR 701.17a: each of three players mills two; CR 611.3a: X counts only the enchanted creature's controller's graveyard, continuously
    @Test
    fun theConversionCountsItsCreatureControllersGraveyard() = scenario(players = 3) {
        libraries()
        bob.battlefield(brute)
        bob.graveyard(zap)
        alice.battlefield("Island", "Island")
        alice.hand(unsettling)
        alice.casts(unsettling, target = brute)
        resolveAll()
        expect {
            alice.graveyard.size shouldBe 2
            bob.graveyard.size shouldBe 3
            carol.graveyard.size shouldBe 2
            brute.power shouldBe 2
            brute.toughness shouldBe 5
        }
    }
}
