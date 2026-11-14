package arcanum.engine.sets.mh3

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * The MH3 energy cards of chunk 02, played: the invented names carry the oracle text of Solstice Zealot, Tempest Harvester, Thriving
 * Skyclaw, Electrozoa, Galvanic Discharge, Aether Spike, Wrath of the Skies, Chthonian Nightmare, Primal Prayers, Amped Raptor, Unstable
 * Amulet, Voltstorm Angel, Wheel of Potential, Toxic Deluge, Guide of Souls and Scurry of Gremlins (costs cut where they don't change play).
 */
class Mh3Chunk02EnergyScenariosTest {
    private val energy = CounterKind.Energy
    private val bear = Cards.invent("Copperfen Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Copperfen Ox", "{2}{G}", "Creature — Ox", "", "2/4")

    private val devotee = Cards.invent("Equinox Devotee", "{2}{W}", "Creature — Rhino Cleric", """
        When this creature enters, you get {E}{E} (two energy counters).
        {T}, Pay {E}: Tap target creature.
    """, "2/3")
    private val gatherer = Cards.invent("Squall Gatherer", "{1}{U}", "Creature — Merfolk Wizard", """
        When this creature enters, you get {E}{E} (two energy counters).
        {T}, Pay {E}: Draw a card, then discard a card.
    """, "2/1")
    private val cloudfang = Cards.invent("Flourishing Cloudfang", "{2}{R}{R}", "Creature — Cat Dragon", """
        Flying
        When this creature enters, you get {E}{E}{E} (three energy counters).
        Whenever this creature attacks, you may pay {E}{E}{E}. If you do, put a +1/+1 counter on it.
    """, "3/2")
    private val medusa = Cards.invent("Voltaic Medusa", "{2}{U}", "Creature — Jellyfish", """
        Flash
        Flying
        When this creature enters, you get {E}{E} (two energy counters).
        At the beginning of your first main phase, tap this creature unless you pay {E}.
    """, "3/1")
    private val release = Cards.invent("Arcing Release", "{R}", "Instant",
        "Choose target creature or planeswalker. You get {E}{E}{E} (three energy counters), then you may pay any amount of {E}. Arcing Release deals that much damage to that permanent.")
    private val rebuke = Cards.invent("Static Rebuke", "{1}{U}", "Instant",
        "Choose target spell. You get {E}{E} (two energy counters), then you may pay any amount of {E}. Counter that spell unless its controller pays {1} for each {E} paid this way.")
    private val fury = Cards.invent("Fury of the Heavens", "{X}{W}{W}", "Sorcery",
        "You get X {E} (energy counters), then you may pay any amount of {E}. Destroy each artifact, creature, and enchantment with mana value less than or equal to the amount of {E} paid this way.")
    private val dream = Cards.invent("Netherdread Dream", "{1}{B}", "Enchantment", """
        When this enchantment enters, you get {E}{E}{E} (three energy counters).
        Pay X {E}, Sacrifice a creature, Return this enchantment to its owner's hand: Return target creature card with mana value X from your graveyard to the battlefield. Activate only as a sorcery.
    """)
    private val invocations = Cards.invent("Feral Invocations", "{2}{G}{G}", "Enchantment", """
        When this enchantment enters, you get {E}{E} (two energy counters).
        You may cast creature spells with mana value 3 or less by paying {E} rather than paying their mana costs. If you cast a spell this way, you may cast it as though it had flash.
    """)
    private val stormclaw = Cards.invent("Charged Stormclaw", "{1}{R}", "Creature — Dinosaur", """
        First strike
        When this creature enters, you get {E}{E} (two energy counters). Then if you cast it from your hand, exile cards from the top of your library until you exile a nonland card. You may cast that card by paying an amount of {E} equal to its mana value rather than paying its mana cost.
    """, "2/1")
    private val talisman = Cards.invent("Volatile Talisman", "{1}{R}", "Artifact", """
        When this artifact enters, you get {E}{E} (two energy counters).
        Whenever you cast a spell from anywhere other than your hand, this artifact deals 1 damage to each opponent.
        {T}, Pay {E}{E}: Exile the top card of your library. You may play it until you exile another card with this artifact.
    """)
    private val angel = Cards.invent("Thunderveil Angel", "{3}{W}{W}", "Creature — Angel", """
        Flying
        When this creature enters, you get {E}{E}{E} (three energy counters).
        At the beginning of combat on your turn, you may pay {E}{E}. When you do, choose one —
        • This creature gains vigilance and lifelink until end of turn.
        • Other creatures you control get +1/+1 until end of turn.
    """, "4/4")
    private val wheel = Cards.invent("Wheel of Possibility", "{2}{R}", "Sorcery", """
        You get {E}{E}{E} (three energy counters), then you may pay any amount of {E}.
        Each player may exile their hand and draw a number of cards equal to the amount of {E} paid this way. If seven or more {E} was paid this way, you may play cards you own exiled this way until the end of your next turn.
    """)
    private val torrent = Cards.invent("Venom Torrent", "{2}{B}", "Sorcery", """
        As an additional cost to cast this spell, pay X life.
        All creatures get -X/-X until end of turn.
    """)
    private val shepherd = Cards.invent("Shepherd of Spirits", "{W}", "Creature — Human Cleric", """
        Whenever another creature you control enters, you gain 1 life and get {E} (an energy counter).
        Whenever you attack, you may pay {E}{E}{E}. When you do, put two +1/+1 counters and a flying counter on target attacking creature. It becomes an Angel in addition to its other types.
    """, "1/2")
    private val swarm = Cards.invent("Swarm of Sparkmites", "{2}{R}{W}", "Enchantment", """
        When this enchantment enters, create two 1/1 red Gremlin creature tokens. Then you get an amount of {E} (energy counters) equal to the number of creatures you control.
        Pay {E}{E}{E}{E}: Creatures you control get +1/+0 and gain haste until end of turn.
    """)

    /** Answers an open "you may pay …" (or "you may …") with [yes]. */
    private fun Scenario.may(yes: Boolean = true) {
        decision<Decision.May>()
        answer(Answer.YesNo(yes))
    }

    // CR 107.14: energy pays the cost; CR 122.1: the counters are the player's
    @Test
    fun devoteeTapsByPayingEnergy() = scenario {
        alice.battlefield(devotee)
        alice.counters(energy, 1)
        bob.battlefield(bear)
        alice.activates(devotee, target = bear)
        resolveAll()
        expect {
            bear.tapped shouldBe true
            alice.counters(energy) shouldBe 0
        }
    }

    // CR 118.3: a cost can't be paid without the energy
    @Test
    fun devoteeCantActivateWithoutEnergy() = scenario {
        alice.battlefield(devotee)
        bob.battlefield(bear)
        assertFails { alice.activates(devotee, target = bear) }
    }

    // CR 122.1, 603.3: the enters trigger gives energy
    @Test
    fun gathererGivesEnergyAsItEnters() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(gatherer)
        alice.casts(gatherer)
        resolveAll()
        expect { alice.counters(energy) shouldBe 2 }
    }

    // CR 118.12: a cost paid on resolution, then "if you do"
    @Test
    fun cloudfangPaysEnergyAsItAttacks() = scenario {
        alice.battlefield(cloudfang)
        alice.counters(energy, 4)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cloudfang)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            cloudfang.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            alice.counters(energy) shouldBe 1
        }
    }

    // CR 118.12a: without energy to pay it, the creature is tapped
    @Test
    fun medusaIsTappedUnlessEnergyIsPaid() = scenario {
        alice.battlefield(medusa)
        alice.library("Island", "Island")
        turn(number = 1, step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        expect { medusa.tapped shouldBe true }
    }

    // CR 118.12a: paying {E} keeps it untapped
    @Test
    fun medusaStaysUntappedWhenEnergyIsPaid() = scenario {
        alice.battlefield(medusa)
        alice.counters(energy, 1)
        alice.library("Island", "Island")
        turn(number = 1, step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            medusa.tapped shouldBe false
            alice.counters(energy) shouldBe 0
        }
    }

    // CR 107.1b: any amount from 0 up to the energy you have; the damage is the amount paid
    @Test
    fun releaseDealsTheEnergyPaid() = scenario {
        alice.battlefield("Mountain")
        alice.hand(release)
        alice.counters(energy, 1)
        bob.battlefield(ox)
        alice.casts(release, target = ox)
        resolveAll()
        assertEquals(4, decision<Decision.ChooseNumber>().max)
        answer(Answer.Number(3))
        resolveAll()
        expect {
            ox.view.obj?.damage shouldBe 3
            alice.counters(energy) shouldBe 1
        }
    }

    // CR 118.12a: the spell's controller must pay {1} for each energy paid; it can pay only one of two, so the spell is countered
    @Test
    fun rebukeCountersUnlessItsControllerPaysForEachEnergy() = scenario(players = 3) {
        alice.battlefield("Island", "Island")
        alice.hand(rebuke)
        bob.battlefield("Forest", "Forest", "Forest")
        bob.hand(bear)
        turn(number = 1, active = bob)
        bob.casts(bear, paying = listOf("Forest", "Forest#1"))
        bob.passes()
        carol.passes()
        alice.casts(rebuke, target = bear)
        resolve()
        decision<Decision.ChooseNumber>()
        answer(Answer.Number(2))
        resolveAll()
        expect {
            bob.graveyard has bear
            alice.counters(energy) shouldBe 0
        }
    }

    // CR 107.3: X energy; each artifact, creature and enchantment with mana value at most the energy paid is destroyed
    @Test
    fun furyDestroysUpToTheEnergyPaid() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(fury)
        bob.battlefield(bear, ox)
        alice.casts(fury, x = 2)
        resolveAll()
        answer(Answer.Number(2))
        resolveAll()
        expect {
            bob.graveyard has bear
            bob.battlefield has ox
            alice.counters(energy) shouldBe 0
        }
    }

    // CR 107.3a: X announced as it is activated, X energy paid; the target has mana value X
    @Test
    fun dreamReturnsACreatureOfManaValueX() = scenario {
        alice.battlefield(dream, ox)
        alice.graveyard(bear)
        alice.counters(energy, 3)
        alice.activates(dream, target = bear, x = 2, choosing = listOf(ox))
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.graveyard has ox
            alice.hand has dream
            alice.counters(energy) shouldBe 1
        }
    }

    // CR 118.9, 601.3c: paying {E} instead of the mana cost, as though it had flash on an opponent's turn
    @Test
    fun invocationsCastsACreatureForEnergyAtInstantSpeed() = scenario {
        alice.battlefield(invocations)
        alice.counters(energy, 1)
        alice.hand(ox)
        turn(number = 2, active = bob, priority = alice)
        val byEnergy = arcanum.engine.casting.Legality.alternativeCosts(arcanum.engine.casting.Snapshot(state), card(ox), alice.id).single().first.origin
        alice.casts(ox, way = arcanum.engine.input.CastWay(alternative = byEnergy))
        resolveAll()
        expect {
            alice.battlefield has ox
            alice.counters(energy) shouldBe 0
        }
    }

    // CR 307.1: cast for its mana cost, it has no flash
    @Test
    fun invocationsGivesNoFlashToTheManaCost() = scenario {
        alice.battlefield(invocations, "Forest", "Forest", "Forest")
        alice.counters(energy, 1)
        alice.hand(ox)
        turn(number = 2, active = bob, priority = alice)
        assertFails { alice.casts(ox) }
    }

    // CR 608.2g, 118.9: the exiled card is cast while the trigger resolves, for energy equal to its mana value
    @Test
    fun stormclawCastsTheExiledCardForEnergy() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(stormclaw)
        alice.library("Island", ox, "Island")
        alice.counters(energy, 1)
        alice.casts(stormclaw)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            alice.battlefield has ox
            alice.exile has "Island"
            alice.counters(energy) shouldBe 0
        }
    }

    // CR 118.3: two energy can't pay a mana value of three, so the card isn't cast and stays exiled
    @Test
    fun stormclawCantCastWithoutEnoughEnergy() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(stormclaw)
        alice.library("Island", ox, "Island")
        alice.casts(stormclaw)
        resolveAll()
        if (pending is Decision.May) may(true)
        resolveAll()
        expect {
            alice.exile has ox
            alice.counters(energy) shouldBe 2
        }
    }

    // CR 607.2a: the exiled card is playable; cast from exile, it is cast from anywhere other than your hand
    @Test
    fun talismanExilesAPlayableCard() = scenario {
        alice.battlefield(talisman, "Forest", "Forest")
        alice.library(bear, "Island")
        alice.counters(energy, 2)
        alice.activates(talisman)
        resolveAll()
        alice.casts(bear)
        resolveAll()
        expect {
            alice.battlefield has bear
            bob.life shouldBe 19
            alice.counters(energy) shouldBe 0
        }
    }

    // CR 603.12, 700.2: the reflexive trigger's mode
    @Test
    fun angelPumpsTheOthers() = scenario {
        alice.battlefield(angel, bear)
        alice.counters(energy, 2)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        may(true)
        decision<Decision.Modes>()
        answer(Answer.Modes(listOf(1)))
        resolveAll()
        expect {
            bear.power shouldBe 3
            angel.power shouldBe 4
            alice.counters(energy) shouldBe 0
        }
    }

    // CR 101.4: each player chooses in turn order; CR 611.2a: seven paid, you may play your own cards exiled this way
    @Test
    fun wheelExilesHandsAndDrawsTheEnergyPaid() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(wheel, "Swamp")
        alice.counters(energy, 4)
        alice.library(*Array(8) { "Island" })
        bob.hand("Forest")
        bob.library(*Array(8) { "Island" })
        carol.hand("Plains")
        carol.library(*Array(8) { "Island" })
        alice.casts(wheel)
        resolveAll()
        assertEquals(7, decision<Decision.ChooseNumber>().max)
        answer(Answer.Number(7))
        resolveAll()
        val asked = mutableListOf<arcanum.engine.state.PlayerId>()
        repeat(3) {
            val d = decision<Decision.May>()
            asked += d.player
            answer(Answer.YesNo(d.player != carol.id))
        }
        resolveAll()
        assertEquals(listOf(alice.id, bob.id, carol.id), asked)
        alice.plays("Swamp")
        expect {
            alice.battlefield has "Swamp"
            bob.exile has "Forest"
            carol.hand has "Plains"
            alice.hand.names.size shouldBe 7
            bob.hand.names.size shouldBe 7
            alice.counters(energy) shouldBe 0
        }
    }

    // CR 601.2b, 107.3: X life paid as the additional cost; every creature gets -X/-X (CR 613.4c)
    @Test
    fun torrentShrinksEveryCreatureByTheLifePaid() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(torrent)
        bob.battlefield(bear, ox)
        alice.casts(torrent, x = 2)
        resolveAll()
        expect {
            alice.life shouldBe 18
            bob.graveyard has bear
            ox.toughness shouldBe 2
        }
    }

    // CR 603.12: counters and the Angel type on the attacking creature; CR 611.2a: the type stays
    @Test
    fun shepherdMakesAnAttackerAnAngel() = scenario {
        alice.battlefield(shepherd, bear, "Forest", "Forest", "Forest")
        alice.hand(ox)
        alice.counters(energy, 2)
        alice.casts(ox)
        resolveAll()
        expect {
            alice.life shouldBe 21
            alice.counters(energy) shouldBe 3
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            bear hasKeyword Keyword.Flying.kind
            bear.subtypes shouldBe listOf(Subtype("Bear"), Subtype("Angel"))
            alice.counters(energy) shouldBe 0
        }
    }

    // CR 608.2c: counted after the tokens are created
    @Test
    fun swarmCountsItsOwnTokens() = scenario {
        alice.battlefield(bear, "Mountain", "Mountain", "Plains", "Plains")
        alice.hand(swarm)
        alice.casts(swarm)
        resolveAll()
        expect { alice.counters(energy) shouldBe 3 }
    }
}
