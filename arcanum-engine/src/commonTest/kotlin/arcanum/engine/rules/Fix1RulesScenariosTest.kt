package arcanum.engine.rules

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rules fixes (fix1): Auras put onto the battlefield by effects, and "if you do" after an action that did nothing. */
class Fix1RulesScenariosTest {
    private val mantle = Cards.invent("Gleaming Mantle", "{1}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2.
    """)
    private val lonely = Cards.invent("Solitary Mantle", "{1}{W}", "Enchantment — Aura", """
        Enchant creature you control
        Enchanted creature gets +2/+2.
    """)
    private val hex = Cards.invent("Watchful Hex", "{1}{B}", "Enchantment — Aura", "Enchant player")
    private val rite = Cards.invent("Graveward Rite", "{2}{W}", "Sorcery", "Return target enchantment card from your graveyard to the battlefield.")
    private val charm = Cards.invent("Recalled Charm", "{1}{W}", "Sorcery", "Return target Aura card from your graveyard to the battlefield.")
    private val ridge = Cards.invent("Ridge Grazer", "{1}{G}", "Creature — Ox", "", "2/2")
    private val marsh = Cards.invent("Marsh Grazer", "{1}{G}", "Creature — Ox", "", "2/2")
    private val dune = Cards.invent("Dune Grazer", "{1}{G}", "Creature — Ox", "", "2/2")
    private val keeper = Cards.invent("Gravewatch Keeper", "{2}{W}", "Creature — Human Cleric",
        "Whenever another creature you control dies, you may exile it. If you do, create a 1/1 white Spirit creature token with flying.", "1/1")
    private val ruin = Cards.invent("Sudden Ruin", "{1}{B}", "Instant", "Destroy target creature.")
    private val sentry = Cards.invent("Tidewatch Sentry", "{4}{U}", "Creature — Merfolk Soldier", """
        Flash
        When this creature enters, you may exile target historic permanent you control. If you do, return that card to the battlefield under its owner's control at the beginning of the next end step. (Artifacts, legendaries, and Sagas are historic.)
    """, "3/3")
    private val idol = Cards.invent("Tidewatch Idol", "{2}", "Artifact", "")

    private fun Scenario.attachedTo(name: String): TargetRef? = state.objOrThrow(card(name, zone = Zone.Battlefield)).attachedTo

    // CR 303.4f: an Aura put onto the battlefield by an effect isn't targeted: as it enters, the player putting it there
    // chooses what it enchants, here another player's creature
    @Test
    fun reanimatedAuraEnchantsWhatItsControllerChooses() = scenario(players = 3) {
        alice.graveyard(mantle)
        alice.hand(rite)
        bob.battlefield(ridge)
        carol.battlefield(marsh)
        alice.castsWithoutCosts(rite, target = mantle)
        resolve()
        val choice = decision<Decision.ChooseObjects>()
        assertEquals(alice.id, choice.player)
        assertEquals(setOf(card(ridge), card(marsh)), choice.options.toSet())
        answer(Answer.Objects(listOf(card(marsh))))
        assertEquals(TargetRef.Object(card(marsh)), attachedTo(mantle))
        expect {
            marsh.power shouldBe 4
            ridge.power shouldBe 2
            mantle.zone shouldBe Zone.Battlefield
        }
    }

    // CR 303.4f: "Return target Aura card": an Aura enchanting a player chooses among the players in the game
    @Test
    fun returnedAuraCardEnchantsAChosenPlayer() = scenario(players = 3) {
        alice.graveyard(hex)
        alice.hand(charm)
        alice.castsWithoutCosts(charm, target = hex)
        resolve()
        val choice = decision<Decision.ChoosePlayer>()
        assertEquals(players.map { it.id }.toSet(), choice.options.toSet())
        answer(Answer.Player(carol.id))
        assertEquals(TargetRef.Player(carol.id), attachedTo(hex))
        expect { hex.zone shouldBe Zone.Battlefield }
    }

    // CR 303.4f: it must be a legal object for its enchant ability ("you control": not another player's creature, with one
    // legal choice nothing is asked)
    @Test
    fun returnedAuraOnlyChoosesAmongLegalObjects() = scenario(players = 3) {
        alice.graveyard(lonely)
        alice.hand(charm)
        alice.battlefield(dune)
        bob.battlefield(ridge)
        carol.battlefield(marsh)
        alice.castsWithoutCosts(charm, target = lonely)
        resolve()
        assertNull(pending)
        assertEquals(TargetRef.Object(card(dune)), attachedTo(lonely))
        expect { dune.power shouldBe 4 }
    }

    // CR 303.4g: with nothing it could legally enchant, the Aura stays in its current zone
    @Test
    fun auraWithNothingLegalToEnchantStaysInTheGraveyard() = scenario(players = 3) {
        alice.graveyard(lonely)
        alice.hand(charm)
        bob.battlefield(ridge)
        carol.battlefield(marsh)
        val before = card(lonely)
        alice.castsWithoutCosts(charm, target = lonely)
        resolve()
        assertNull(pending)
        // the same object: it never entered and died (CR 400.7)
        assertEquals(before, card(lonely))
        expect { lonely.zone shouldBe Zone.Graveyard }
    }

    private fun Scenario.keeperTrigger() {
        alice.battlefield(keeper, dune)
        alice.hand(ruin)
        alice.castsWithoutCosts(ruin, target = dune)
        resolve()
        assertEquals(1, state.stack.size, "Gravewatch Keeper's trigger waits")
    }

    private fun Scenario.spirits() = state.battlefield.count { state.obj(it)?.token == true }

    // CR 608.2c: "you may exile it. If you do": yes, and the card is exiled, so the token is created
    @Test
    fun ifYouDoFollowsAnActionThatHappened() = scenario(players = 3) {
        keeperTrigger()
        resolve()
        answer(Answer.YesNo(true))
        expect { dune.zone shouldBe Zone.Exile }
        assertEquals(1, spirits())
    }

    // CR 608.2c, 400.7: the card left the graveyard in response; choosing yes exiles nothing, so "if you do" is false
    @Test
    fun ifYouDoIsFalseWhenTheChosenActionDidNothing() = scenario(players = 3) {
        keeperTrigger()
        move(dune, Zone.Hand)
        resolve()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        expect { dune.zone shouldBe Zone.Hand }
        assertEquals(0, spirits())
    }

    // CR 400.7, 603.7c: "that card" is the exiled card: the artifact comes back at the next end step, the creature stays
    @Test
    fun exiledHistoricPermanentReturnsAtTheNextEndStep() = scenario {
        alice.battlefield(idol)
        alice.hand(sentry)
        alice.castsWithoutCosts(sentry)
        resolve()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(TargetRef.Object(card(idol)))))
        resolve()
        answer(Answer.YesNo(true))
        expect { idol.zone shouldBe Zone.Exile }
        beginStep(TurnStep.End)
        resolveStack()
        expect {
            idol.zone shouldBe Zone.Battlefield
            sentry.zone shouldBe Zone.Battlefield
        }
        assertTrue(state.stack.isEmpty())
    }
}
