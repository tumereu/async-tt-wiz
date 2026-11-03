package arcanum.engine.casting

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.setup.Setup
import arcanum.engine.setup.StartOptions
import arcanum.engine.setup.cards
import arcanum.engine.setup.inOrder
import arcanum.engine.setup.keep
import arcanum.engine.setup.startGame
import arcanum.engine.setup.table
import arcanum.engine.state.TargetRef
import arcanum.engine.view.AbilityOrigin
import arcanum.parser.OracleCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wordings the parser reads since the engine's lanes 1–4, played: exert, costs to attack and block, an action any time you could
 * mulligan, a starting hand size, a static copy effect, keyword actions (explore, monstrosity, adapt, support, goad, manifest, cloak)
 * and a Siege. Every card here is read by the parser.
 */
class ReadLaneWordingsScenariosTest {
    private val striker = Cards.invent("Lane Exert Striker", "{2}{R}", "Creature — Human Warrior",
        "You may exert this creature as it attacks. When you do, it gets +2/+0 until end of turn. " +
            "(An exerted creature won't untap during your next untap step.)", "3/3")
    private val scribe = Cards.invent("Lane Exerting Scribe", "{1}{U}", "Creature — Human Wizard",
        "{T}, Exert this creature: Draw a card. (An exerted creature won't untap during your next untap step.)", "1/1")
    private val wyrm = Cards.invent("Lane Hungry Wyrm", "{4}{R}", "Creature — Dragon",
        "This creature can't attack unless you sacrifice a land. (This cost is paid as attackers are declared.)", "5/5")
    private val toll = Cards.invent("Lane Toll of Thorns", "{1}{B}", "Enchantment",
        "Creatures can't block unless their controller pays 2 life for each blocking creature they control.")
    private val reshuffle = Cards.invent("Lane Ashen Reshuffle", "{2}", "Artifact",
        "Any time you could mulligan and this card is in your hand, you may exile all the cards from your hand, then draw that many cards. " +
            "(You can do this in addition to taking mulligans.)")
    private val opening = Cards.invent("Lane Generous Opening", null, "Conspiracy", "Your starting hand size is increased by one.")
    private val charm = Cards.invent("Lane Mirrorbond Charm", "{2}{U}", "Enchantment — Aura", """
        Enchant creature
        Other creatures you control are copies of enchanted creature.
    """)
    private val seeker = Cards.invent("Lane Canopy Seeker", "{1}{G}", "Creature — Merfolk Scout",
        "When this creature enters, it explores. (Reveal the top card of your library. Put that card into your hand if it's a land. " +
            "Otherwise, put a +1/+1 counter on this creature, then put the card back or put it into your graveyard.)", "2/2")
    private val behemoth = Cards.invent("Lane Gorge Behemoth", "{3}{G}", "Creature — Beast", """
        {2}{G}: Monstrosity 2. (If this creature isn't monstrous, put two +1/+1 counters on it and it becomes monstrous.)
        As long as this creature is monstrous, it has trample.
    """, "3/3")
    private val mutant = Cards.invent("Lane Reef Mutant", "{1}{U}", "Creature — Fish Mutant",
        "{U}: Adapt 2. (If this creature has no +1/+1 counters on it, put two +1/+1 counters on it.)", "1/2")
    private val captain = Cards.invent("Lane Shieldline Captain", "{2}{W}", "Creature — Human Soldier",
        "When this creature enters, support 2. (Put a +1/+1 counter on each of up to two other target creatures.)", "2/2")
    private val taunter = Cards.invent("Lane Taunting Wyrm", "{1}{R}", "Creature — Dragon",
        "When this creature enters, tap target creature an opponent controls. Goad it.", "2/2")
    private val grower = Cards.invent("Lane Veiled Grower", "{1}{G}", "Creature — Elf Druid",
        "When this creature enters, manifest the top card of your library. (Put it onto the battlefield face down as a 2/2 creature. " +
            "Turn it face up any time for its mana cost if it's a creature card.)", "1/1")
    private val entrance = Cards.invent("Lane Shrouded Entrance", "{1}{W}", "Sorcery",
        "Cloak the top card of your library. (Put it onto the battlefield face down as a 2/2 creature with ward {2}. Turn it face up any " +
            "time for its mana cost if it's a creature card.)")
    private val siege = Cards.inventFaces("Transform",
        OracleCard("Lane Siege of Cinder Gate", "{2}{R}", "Battle — Siege", """
            (As a Siege enters, choose an opponent to protect it. You and others can attack it. When it's defeated, exile it, then cast it transformed.)
            When this Siege enters, it deals 3 damage to target creature an opponent controls.
        """.trimIndent(), defense = "4"),
        OracleCard("Lane Cinder Gate Warden", null, "Creature — Elemental", "", power = "4", toughness = "4", colorIndicator = "R"),
    )
    private val bear = Cards.invent("Lane Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Lane Plain Ox", "{2}{G}", "Creature — Ox", "Vigilance", "3/3")

    private fun Scenario.libraries() {
        alice.library("Island", "Island", "Island", "Island")
        bob.library("Island", "Island", "Island", "Island")
    }

    /** Answers the trigger's target decision with [names], if it asks. */
    private fun Scenario.targeting(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }

    // CR 701.43d, 508.1g, 603.12: exerted as it attacks; the "when you do" trigger resolves
    @Test
    fun exertAsItAttacks() = scenario {
        alice.battlefield(striker)
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(striker), TargetRef.Player(bob.id), paying = listOf(AbilityOrigin.Printed(0, 0))))))
        allPass()
        expect { striker.power shouldBe 5 }
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 15 }
    }

    // CR 701.43a, b: exert as an activated ability's cost: it doesn't untap during its controller's next untap step
    @Test
    fun exertAsACost() = scenario {
        alice.battlefield(scribe)
        libraries()
        alice.activates(scribe)
        allPass()
        expect { alice.hand.size shouldBe 1 }
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { scribe.tapped shouldBe true }
    }

    // CR 508.1h: the sacrifice is paid as it is declared an attacker
    @Test
    fun sacrificeToAttack() = scenario {
        alice.battlefield(wyrm, "Forest", "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wyrm)
        alice.chooses("Forest")
        expect {
            alice.graveyard.names shouldBe listOf("Forest")
            card("Forest", 1).zone shouldBe Zone.Battlefield
        }
        assertEquals(listOf(card(wyrm)), state.combat?.attackers?.map { it.id })
    }

    // CR 509.1d: 2 life for each blocker, paid as blockers are declared
    @Test
    fun payLifeToBlock() = scenario {
        alice.battlefield(bear)
        bob.battlefield(toll, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(ox to bear)
        expect { bob.life shouldBe 18 }
        assertEquals(listOf(card(ox)), state.combat?.blockers?.map { it.id })
    }

    // CR 103.5b: the action is offered where alice would declare; the whole hand is exiled and as many cards drawn
    @Test
    fun actionAnyTimeYouCouldMulligan() = scenario(start = table(cards(listOf(reshuffle), "Swamp", "Undead Soothsayer"),
        cards(listOf("Mountain"), "Mountain", "Glint-Tusk Corsair"))) {
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        val use = decision<Decision.ChooseOption>().options.single { it.startsWith(Setup.USE) }
        answer(Answer.Option(use))
        expect {
            alice.hand.size shouldBe 7
            alice.exile.size shouldBe 7
            alice.exile has reshuffle
        }
        // the card left the hand with it: nothing more to use
        assertTrue(decision<Decision.ChooseOption>().options.none { it.startsWith(Setup.USE) })
    }

    // CR 103.5: the conspiracy in bob's command zone increases his starting hand size
    @Test
    fun startingHandSize() = scenario(start = table(cards(listOf("Swamp"), "Swamp", "Undead Soothsayer"),
        cards(listOf("Mountain"), "Mountain", "Glint-Tusk Corsair"))) {
        put(bob, listOf(opening), Zone.Command)
        random(inOrder, inOrder)
        startGame(StartOptions(startingPlayer = alice.id))
        keep(alice)
        expect {
            alice.hand.size shouldBe 7
            bob.hand.size shouldBe 8
        }
    }

    // CR 613.2a, 707.2: the other creature has the enchanted creature's copiable values
    @Test
    fun othersAreCopies() = scenario {
        alice.battlefield(bear, ox, charm)
        attach(charm, ox)
        val c = view()[card(bear)]?.characteristics
        assertEquals(ox, c?.name)
        assertEquals(3, c?.power)
        assertTrue(c?.hasKeyword(Keyword.Vigilance) == true)
    }

    // CR 701.44a: a land revealed goes to the hand, without a counter
    @Test
    fun explores() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(seeker)
        alice.library("Mountain", "Island")
        alice.casts(seeker)
        allPass()
        allPass()
        expect {
            alice.hand has "Mountain"
            seeker.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 701.37a, b: two counters and monstrous, so it has trample; a second activation does nothing
    @Test
    fun monstrosity() = scenario {
        alice.battlefield(behemoth, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        expect { behemoth lacksKeyword Keyword.Trample }
        alice.activates(behemoth)
        allPass()
        expect {
            behemoth.power shouldBe 5
            behemoth hasKeyword Keyword.Trample
        }
        alice.activates(behemoth)
        allPass()
        expect { behemoth.power shouldBe 5 }
    }

    // CR 701.46a: counters only while it has none
    @Test
    fun adapt() = scenario {
        alice.battlefield(mutant, "Island", "Island")
        alice.activates(mutant)
        allPass()
        alice.activates(mutant)
        allPass()
        expect { mutant.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2 }
    }

    // CR 701.41a: a counter on each of up to two other target creatures
    @Test
    fun support() = scenario {
        alice.battlefield(bear, ox, "Plains", "Plains", "Plains")
        alice.hand(captain)
        alice.casts(captain)
        allPass()
        targeting(bear, ox)
        allPass()
        expect {
            bear.power shouldBe 3
            ox.power shouldBe 4
            captain.power shouldBe 2
        }
    }

    // CR 701.15a: "Goad it." goads the creature just tapped
    @Test
    fun tapAndGoadIt() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(taunter)
        bob.battlefield(ox)
        alice.casts(taunter)
        allPass()
        targeting(ox)
        allPass()
        expect { ox.tapped shouldBe true }
        assertEquals(listOf(alice.id), state.objOrThrow(card(ox)).goadedBy)
    }

    // CR 701.40a: the top card onto the battlefield face down as a 2/2 creature
    @Test
    fun manifest() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(grower)
        alice.library("Undead Soothsayer", "Island")
        alice.casts(grower)
        allPass()
        allPass()
        val manifested = card("Undead Soothsayer")
        assertTrue(state.objOrThrow(manifested).faceDown)
        assertEquals(Zone.Battlefield, state.objOrThrow(manifested).zone)
        assertEquals(2, view()[manifested]?.characteristics?.power)
    }

    // CR 701.58a: cloaked: face down as a 2/2 creature with ward {2}
    @Test
    fun cloak() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(entrance)
        alice.library("Undead Soothsayer", "Island")
        alice.casts(entrance)
        allPass()
        val cloaked = card("Undead Soothsayer")
        assertTrue(state.objOrThrow(cloaked).faceDown)
        assertTrue(view()[cloaked]?.characteristics?.abilities.orEmpty().any { it.ability is Keyword.Ward })
    }

    // CR 310.4b, 310.12a: the Siege enters with its defense, protected by bob; its enters trigger deals the damage
    @Test
    fun siege() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(siege)
        bob.battlefield(bear)
        alice.casts(siege)
        allPass()
        targeting(bear)
        allPass()
        expect {
            siege.counters(CounterKind.Defense) shouldBe 4
            bob.graveyard has bear
        }
        assertEquals(bob.id, state.obj(card(siege))?.protector)
    }
}
