package arcanum.engine.sets.lci

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import arcanum.engine.sets.rtr.targets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** LCI chunk 16 played: a counter artifact, Restless lands (enter tapped, animate, attack triggers), an "as enters" card name that spares mana abilities,
 * "untap during each other player's untap step", a may-sacrifice with a reflexive trigger limited to your own permanents, and a three-player sacrifice.
 * Invented names carry the oracle text. */
class LciChunk16CountersSacrificeSearchScenariosTest {
    private val cache = Cards.invent("Stonewick Cache", "{1}{G}", "Artifact", """
        This artifact enters with two +1/+1 counters on it.
        Whenever a creature you control with a +1/+1 counter on it dies, put a +1/+1 counter on this artifact.
        {T}: Move a +1/+1 counter from this artifact onto target creature. Activate only as a sorcery.
    """)
    private val prairie = Cards.invent("Dusky Prairie", null, "Land", """
        This land enters tapped.
        {T}: Add {G} or {W}.
        {2}{G}{W}: This land becomes a 3/3 green and white Llama creature until end of turn. It's still a land.
        Whenever this land attacks, other creatures you control get +1/+1 until end of turn.
    """)
    private val ridgeline = Cards.invent("Ember Ridgeline", null, "Land", """
        This land enters tapped.
        {T}: Add {R} or {G}.
        {2}{R}{G}: This land becomes a 3/4 red and green Dinosaur creature until end of turn. It's still a land.
        Whenever this land attacks, another target attacking creature gets +2/+0 until end of turn. Untap that creature.
    """)
    private val vents = Cards.invent("Gloomwater Vents", null, "Land", """
        This land enters tapped.
        {T}: Add {B} or {R}.
        {1}{B}{R}: Until end of turn, this land becomes a 2/3 black and red Insect creature with menace. It's still a land.
        Whenever this land attacks, you may discard a card. If you do, draw a card.
    """)
    private val lens = Cards.invent("Spectacle Lens", "{2}", "Artifact", """
        As this artifact enters, look at an opponent's hand, then choose any card name.
        Activated abilities of sources with the chosen name can't be activated unless they're mana abilities.
    """)
    private val tinker = Cards.invent("Brass Tinker", "{1}", "Artifact", """
        {T}: Add {C}.
        {T}: You gain 1 life.
    """)
    private val infantry = Cards.invent("Moonlit Infantry", "{2}{W}", "Creature — Human Soldier",
        "Untap this creature during each other player's untap step.", "2/4")
    private val herald = Cards.invent("Sacrificial Herald", "{2}{W}", "Creature — Vampire Soldier",
        "When this creature enters, you may sacrifice another creature or artifact. When you do, put a +1/+1 counter on each of up to two target creatures.", "3/2")
    private val brute = Cards.invent("Gravetide Brute", "{4}{B}{B}", "Creature — Horror",
        "When this creature enters, each player sacrifices two creatures of their choice.", "6/6")
    private val bear = Cards.invent("Brackenwood Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Cragmaw Hulk", "{3}{R}", "Creature — Ogre", "", "4/3")
    private val wolf = Cards.invent("Hollow Wolf", "{2}{G}", "Creature — Wolf", "", "3/2")
    private val trinketCreature = Cards.invent("Mist Cub", "{1}{U}", "Creature — Cat", "", "1/1")
    private val cog = Cards.invent("Brass Sprocket", "{1}", "Artifact", "")
    private val trinket = Cards.invent("Pewter Trinket", "{1}", "Artifact", "")
    private val bolt = Cards.invent("Brass Bolt", "{R}", "Instant", "Brass Bolt deals 5 damage to target creature.")
    private val boltTwo = Cards.invent("Brass Bolt Two", "{R}", "Instant", "Brass Bolt Two deals 5 damage to target creature.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private fun Scenario.yes() = answer(Answer.YesNo(true))

    // CR 614.1c: it enters with two +1/+1 counters; CR 122.2, 117.1b: the sorcery-speed move puts one on a creature
    @Test
    fun cacheEntersWithCountersAndMovesOne() = scenario {
        alice.battlefield("Forest", "Forest", bear)
        alice.hand(cache)
        alice.casts(cache)
        resolveAll()
        expect { cache.counters(p1p1) shouldBe 2 }
        alice.activates(cache, target = bear)
        resolveAll()
        expect {
            cache.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 1
        }
    }

    // CR 700.4, 603.10a: a creature with a +1/+1 counter dying gives the artifact a counter; one without does not
    @Test
    fun cacheGrowsOnlyWhenACreatureWithACounterDies() = scenario {
        alice.battlefield(cache, wolf, "Mountain", "Mountain")
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.hand(bolt, boltTwo)
        alice.casts(bolt, target = wolf)
        resolveAll()
        expect { cache.counters(p1p1) shouldBe 0 }
        alice.casts(boltTwo, target = bear)
        resolveAll()
        expect { cache.counters(p1p1) shouldBe 1 }
    }

    // CR 614.12 (enters tapped): played from hand it is tapped; CR 613.1d: animated it is a 3/3 creature
    @Test
    fun prairieEntersTappedAndAttackPumpsOthers() = scenario {
        alice.hand(prairie)
        alice.plays(prairie)
        expect { prairie.tapped shouldBe true }
    }

    // CR 508.1, 611.2c: animated and attacking, the other attackers (not itself, not Bob's) get +1/+1
    @Test
    fun prairieAnimatedAttackPumpsOtherCreatures() = scenario {
        alice.battlefield(prairie, bear, "Forest", "Forest", "Plains", "Plains")
        bob.battlefield(wolf)
        alice.activates(prairie, ability = 1, paying = listOf("Forest", "Forest", "Plains", "Plains"))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(prairie, bear)
        resolveAll()
        expect {
            prairie.power shouldBe 3
            bear.power shouldBe 3
            wolf.power shouldBe 3
        }
    }

    // CR 508.1, 506.3: the other attacking creature (not Bob's) gets +2/+0 and untaps
    @Test
    fun ridgelineGivesAnotherAttackerPlusTwoAndUntaps() = scenario {
        alice.battlefield(ridgeline, ogre, "Forest", "Forest", "Mountain", "Mountain")
        alice.activates(ridgeline, ability = 1, paying = listOf("Forest", "Forest", "Mountain", "Mountain"))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ridgeline, ogre)
        resolveAll()
        resolveAll()
        expect {
            ogre.power shouldBe 6
            ogre.tapped shouldBe false
            ridgeline.power shouldBe 3
        }
    }

    // CR 701.9, 603.12: may discard, and when you do draw
    @Test
    fun ventsAttackDiscardsToDraw() = scenario {
        alice.battlefield(vents, "Swamp", "Swamp", "Mountain")
        alice.hand(cog)
        alice.library("Forest")
        alice.activates(vents, ability = 1, paying = listOf("Swamp", "Swamp", "Mountain"))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vents)
        resolveAll()
        yes()
        resolveAll()
        expect {
            alice.graveyard has cog
            alice.hand has "Forest"
        }
    }

    // CR 201.3, 605.1a: the named card's mana ability stays, its other ability goes
    @Test
    fun lensStopsOnlyTheNonManaAbilities() = scenario {
        alice.battlefield("Plains", "Plains", tinker)
        alice.hand(lens)
        bob.hand(cog)
        val before = Engine.options(state, alice.id).count { it is Input.Activate && it.source == card(tinker) }
        alice.casts(lens)
        resolve()
        if (pending is Decision.ChoosePlayer) answer(Answer.Player(bob.id))
        answer(Answer.CardName(tinker))
        val after = Engine.options(state, alice.id).count { it is Input.Activate && it.source == card(tinker) }
        assertEquals(2, before)
        assertEquals(1, after)
    }

    // CR 502.3, 502.4: it untaps in the opponent's untap step too
    @Test
    fun infantryUntapsInTheOpponentsUntapStep() = scenario {
        alice.battlefield(infantry, tapped = true)
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { infantry.tapped shouldBe false }
    }

    // CR 502.3, three players: Carol's creature untaps in Bob's untap step, which is not its controller's turn
    @Test
    fun infantryUntapsInEachOtherPlayersUntapStep() = scenario(players = 3) {
        carol.battlefield(infantry, tapped = true)
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { infantry.tapped shouldBe false }
    }

    // CR 603.12, 701.21a (audit): the sacrifice is offered only Alice's other creatures and artifacts; the reflexive trigger then puts counters on two targets
    @Test
    fun heraldSacrificesOnlyYourOwnAndPutsCountersOnTwoTargets() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear, cog)
        alice.hand(herald)
        bob.battlefield(wolf, trinket)
        alice.casts(herald)
        resolveAll()
        yes()
        val offered = decision<Decision.ChooseObjects>().options
        assertEquals(setOf(card(bear), card(cog)), offered.toSet())
        alice.chooses(cog)
        targets("Brackenwood Cub", "Hollow Wolf")
        resolveAll()
        expect {
            alice.graveyard has cog
            bear.counters(p1p1) shouldBe 1
            wolf.counters(p1p1) shouldBe 1
            herald.counters(p1p1) shouldBe 0
        }
    }

    // CR 603.12: declining the sacrifice means no reflexive trigger
    @Test
    fun heraldDecliningLeavesEverythingAlone() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear, cog)
        alice.hand(herald)
        alice.casts(herald)
        resolveAll()
        answer(Answer.YesNo(false))
        resolveAll()
        expect {
            alice.battlefield has cog
            bear.counters(p1p1) shouldBe 0
        }
    }

    // CR 101.4 (APNAP choices), 701.21a: in a three-player game every player sacrifices two of their own creatures
    @Test
    fun gorestalkerMakesEachOfThreePlayersSacrificeTwo() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(brute)
        alice.battlefield(bear, trinketCreature)
        bob.battlefield(wolf, ogre, cog)
        carol.battlefield(Cards.invent("Moss Pup", "{G}", "Creature — Dog", "", "1/1"))
        alice.casts(brute)
        resolveAll()
        var guard = 12
        while (pending is Decision.ChooseObjects && guard-- > 0) {
            val d = decision<Decision.ChooseObjects>()
            answer(Answer.Objects(d.options.filter { it != card(brute) }.take(d.min)))
        }
        resolveAll()
        expect {
            alice.battlefield has brute
            alice.graveyard has bear
            alice.graveyard has trinketCreature
            bob.battlefield.size shouldBe 1
            carol.battlefield.names.contains("Moss Pup") shouldBe false
        }
    }
}
