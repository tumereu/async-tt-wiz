package arcanum.engine.sets.trk

import arcanum.api.CounterKind
import arcanum.api.Supertype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Star Trek chunk 10 in play: a clone entering with the copied permanent's counters, Equipment tokens becoming copies of an Equipment until
 * end of turn, tokens for each dilemma faced this turn, casting exiled cards by a count of Officers, and a delayed trigger that returns a dying
 * creature at the next end step. */
class TrkChunk10EffectsScenariosTest {
    private val infiltrator = Cards.invent("Founders' Infiltrator", "{4}{U}", "Creature — Shapeshifter",
        "You may have this creature enter as a copy of any artifact or creature on the battlefield, except it isn't legendary. If you do, it enters with additional counters on it equal to the same number and kinds of counters the copied permanent has on it.",
        "3/3")
    private val warlord = Cards.invent("Trk10 Ridge Warlord", "{2}{R}", "Legendary Creature — Human Warrior", "", "2/2")
    private val wyndra = Cards.invent("Wyndra, the Offspring", "{1}{U}", "Legendary Creature — Vau-N'Akat Advisor", """
        When Wyndra enters, create a colorless Equipment artifact token named Neuroflux Keepsake with "Equipped creature gets +1/+1" and equip {2}. Attach it to Wyndra.
        {2}: Each Equipment token you control becomes a copy of target Equipment you control until end of turn.
    """.trimIndent(), "1/1")
    private val rifle = Cards.invent("Trk10 Phase Rifle", "{2}", "Artifact — Equipment", "Equipped creature gets +3/+0.\nEquip {3}")
    private val sisk = Cards.invent("Captain Bertram Sisk", "{1}{W}", "Legendary Creature — Human Officer", """
        At the beginning of your end step, create a 1/1 red Officer creature token for each dilemma you've faced this turn.
        {2}{W}{U}{B}{R}{G}, {T}: Exile the top five cards of your library. You may cast any number of spells with mana value less than or equal to the number of Officers you control from among those cards without paying their mana costs.
    """.trimIndent(), "2/2")
    private val charm = Cards.invent("Trk10 Quandary Charm", "{U}", "Instant", """
        Choose one —
        • You gain 2 life.
        • Each opponent loses 1 life.
    """.trimIndent())
    private val ensign = Cards.invent("Trk10 Eager Ensign", "{W}", "Creature — Human Officer", "", "1/1")
    private val cub = Cards.invent("Trk10 Moss Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Trk10 Plains Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val salve = Cards.invent("Trk10 Field Salve", "{W}", "Instant", "You gain 3 life.")
    private val mcKay = Cards.invent("Dr. Lionel \"Splints\" McKay", "{1}{G}{G}", "Legendary Creature — Human Doctor", """
        Flash
        When Dr. McKay enters, choose one —
        • Return target non-Doctor permanent card from your graveyard to your hand.
        • Choose up to one other target creature. When it dies this turn, return that card to the battlefield under its owner's control with a +1/+1 counter on it at the beginning of the next end step.
    """.trimIndent(), "3/2")
    private val bolt = Cards.invent("Trk10 Searing Bolt", "{R}", "Instant", "Trk10 Searing Bolt deals 3 damage to target creature.")

    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val oil = CounterKind.Named("oil")

    // ---- A clone with the copied permanent's counters

    // CR 707.2, 707.9b (it isn't legendary), 614.1c: the copied creature's +1/+1 and oil counters, each kind and number, on top of its copy
    @Test
    fun aCopyEntersWithTheCopiedCreaturesCounters() = scenario(players = 3) {
        carol.battlefield(warlord, counters = listOf(p1p1 to 2, oil to 1))
        alice.hand(infiltrator)
        alice.castsWithoutCosts(infiltrator)
        resolve()
        alice.chooses(warlord)
        settle()
        expect {
            card(infiltrator).counters(p1p1) shouldBe 2
            card(infiltrator).counters(oil) shouldBe 1
            card(infiltrator).power shouldBe 4
            card(infiltrator).toughness shouldBe 4
            assertTrue(Supertype.Legendary !in card(infiltrator).view.characteristics!!.supertypes)
            carol.battlefield has warlord
            card(warlord).counters(p1p1) shouldBe 2
        }
    }

    // CR 614.1c: "If you do": without a copy it enters as itself, with no counters
    @Test
    fun withoutACopyItEntersWithNoCounters() = scenario(players = 3) {
        carol.battlefield(warlord, counters = listOf(p1p1 to 2))
        alice.hand(infiltrator)
        alice.castsWithoutCosts(infiltrator)
        resolve()
        alice.chooses()
        settle()
        expect {
            card(infiltrator).counters(p1p1) shouldBe 0
            card(infiltrator).power shouldBe 3
        }
    }

    // ---- Equipment tokens becoming copies

    // CR 707.2, 611.2c, 301.5c: the attached Equipment token becomes a copy of the rifle and stays attached, so its creature gets +3/+0 instead
    // of +1/+1 until end of turn (CR 514.2)
    @Test
    fun theEquipmentTokenBecomesACopyUntilEndOfTurn() = scenario(players = 3) {
        alice.battlefield(rifle, "Island", "Island")
        alice.hand(wyndra)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        carol.library("Island", "Island")
        alice.castsWithoutCosts(wyndra)
        resolveAll()
        expect {
            card(wyndra).power shouldBe 2
            card(wyndra).toughness shouldBe 2
        }
        alice.activates(wyndra, target = rifle)
        resolveAll()
        expect {
            card(wyndra).power shouldBe 4
            card(wyndra).toughness shouldBe 1
            alice.battlefield has rifle
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            card(wyndra).power shouldBe 2
            card(wyndra).toughness shouldBe 2
        }
    }

    // ---- Dilemmas and casting from exile

    // CR 700.17: two dilemmas faced this turn make two Officer tokens at the end step (CR 603.2)
    @Test
    fun aTokenForEachDilemmaFacedThisTurn() = scenario(players = 3) {
        alice.battlefield(sisk, "Island", "Island")
        alice.hand(charm, charm)
        alice.casts(charm, modes = listOf(0), paying = listOf("Island"))
        resolveAll()
        alice.casts(charm, modes = listOf(1), paying = listOf("Island#1"))
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Officer Token" } shouldBe 2
            alice.life shouldBe 22
        }
    }

    // CR 608.2g, 601.2a: with two Officers, the exiled spells of mana value 2 or less are cast free while it resolves; a land and a 3-mana
    // creature stay exiled
    @Test
    fun castsExiledSpellsUpToTheNumberOfOfficers() = scenario(players = 3) {
        alice.battlefield(sisk, ensign)
        alice.library(cub, ox, "Forest", salve, "Plains")
        alice.activatesWithoutCosts(sisk)
        resolve()
        val options = (pending as Decision.ChooseObjects).options
        assertEquals(setOf(card(cub), card(salve)), options.toSet())
        alice.chooses(cub, salve)
        // the order to cast them (CR 608.2g)
        alice.chooses(cub, salve)
        resolveAll()
        expect {
            alice.battlefield has cub
            alice.life shouldBe 23
            alice.exile has ox
            alice.exile has "Forest"
        }
    }

    // ---- A delayed trigger that makes a delayed trigger

    // CR 603.7: when the chosen creature dies this turn, it returns at the beginning of the next end step under its owner's control with a
    // +1/+1 counter (CR 400.7: the card put into the graveyard)
    @Test
    fun theChosenCreatureReturnsAtTheNextEndStep() = scenario(players = 3) {
        alice.battlefield("Mountain")
        bob.battlefield(cub)
        alice.hand(mcKay, bolt)
        alice.castsWithoutCosts(mcKay)
        resolve()
        modes(1)
        targets(cub)
        resolveAll()
        alice.casts(bolt, target = cub)
        resolveAll()
        expect { bob.graveyard has cub }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.battlefield has cub
            card(cub).controller shouldBe bob.id
            card(cub).counters(p1p1) shouldBe 1
        }
    }
}
