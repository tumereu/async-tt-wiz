package arcanum.engine.sets.otj

import arcanum.api.CounterKind
import arcanum.api.ObjectMark
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.ecl.paySuggested
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.unblocked
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OTJ chunk 05: what was cast and how (CR 601.2a, 601.2f), a cost to plot (CR 702.170a), a cast while an ability resolves (CR 608.2g). */
class OtjChunk05CastScenariosTest {
    private val tess = Cards.invent("Tess Skyrider, Cloud Scout", "{2}{W}{U}", "Legendary Creature — Human Scout",
        "Flying, vigilance\nAt the beginning of your end step, if you haven't cast a spell from your hand this turn, draw a card.", "3/3")
    private val bramble = Cards.invent("Doc Bramble, Wily Healer", "{G}{U}", "Legendary Creature — Bear Druid",
        "Spells you cast from your graveyard or from exile cost {2} less to cast.\nPlotting cards from your hand costs {2} less.", "2/3")
    private val ledger = Cards.invent("Snatch the Ledger", "{2}{U}", "Sorcery",
        "This spell costs {1} less to cast if you've committed a crime this turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)\n" +
            "Draw two cards.")
    private val dread = Cards.invent("Dread of the Crags", "{3}{R}{R}", "Creature — Dragon",
        "Flying\nSpells your opponents cast that target this creature cost an additional 3 life to cast.\n" +
            "Whenever another creature you control enters, this creature deals damage equal to that creature's power to any target.", "5/4")
    private val twigbones = Cards.invent("Twigbones, the Filcher", "{B}", "Legendary Creature — Skeleton Rogue",
        "Deathtouch\nWhenever Twigbones deals combat damage to a player, you may cast target nonland permanent card from that player's graveyard, " +
            "and mana of any type can be spent to cast that spell.", "1/1")
    private val kip = Cards.invent("Kip, the Drifter", "{G}{W}{U}", "Legendary Creature — Human Faerie Rogue",
        "Flying, lifelink\nWhenever you cast a spell from anywhere other than your hand, you may cast a permanent spell with equal or lesser mana value " +
            "from your hand without paying its mana cost. If you don't, you may put a land card from your hand onto the battlefield.", "3/3")
    private val marmot = Cards.invent("Meadow Marmot", "{1}{W}", "Creature — Squirrel",
        "Lifelink\nAt the beginning of your end step, if you haven't cast a spell from your hand this turn, put a +1/+1 counter on this creature.\n" +
            "{4}{W}: Until end of turn, if you would put one or more +1/+1 counters on a creature you control, put that many plus one +1/+1 counters on it instead.", "2/2")

    private val spark = Cards.invent("Tumbleweed Spark", "{R}", "Instant", "Tumbleweed Spark deals 1 damage to any target.")
    private val recollection = Cards.invent("Dusty Recollection", "{R}", "Instant",
        "Dusty Recollection deals 1 damage to any target.\nFlashback {R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)")
    private val wrangler = Cards.invent("Canyon Pup", "{G}", "Creature — Dog", "", "1/1")
    private val longhorn = Cards.invent("Mesa Longhorn", "{2}{G}", "Creature — Ox", "", "3/3")
    private val bushwhack = Cards.invent("Brushfire Bushwhacker", "{1}{G}", "Creature — Elf", "", "2/2")
    private val cactus = Cards.invent("Saguaro Rustler", "{1}{G}", "Creature — Plant", "Plot {2}{G} (You may pay {2}{G} and exile this card from your hand. " +
        "Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)", "2/2")
    private val blessing = Cards.invent("Trail Blessing", "{W}", "Instant", "Put a +1/+1 counter on target creature.")

    // CR 603.4, 601.2a: a spell cast from your hand this turn stops it
    @Test
    fun aSpellCastFromYourHandStopsTheEndStepDraw() = scenario {
        alice.battlefield(tess)
        alice.library("Island", "Island")
        alice.hand(spark)
        alice.castsWithoutCosts(spark, target = "bob")
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.hand.size shouldBe 0
        }
    }

    // CR 603.4, 601.2a: a spell cast from a graveyard isn't cast from your hand
    @Test
    fun aSpellCastFromYourGraveyardDoesntStopTheEndStepDraw() = scenario {
        alice.battlefield(tess, "Mountain")
        alice.library("Island", "Island")
        alice.graveyard(recollection)
        alice.casts(recollection, target = "bob")
        paySuggested()
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.hand.size shouldBe 1
        }
    }

    // CR 702.170a, 601.2f, 118.7a: plotting from your hand costs {2} less: {2}{G} comes to {G}
    @Test
    fun plottingCostsLess() = scenario {
        alice.battlefield(bramble, "Forest")
        alice.hand(cactus)
        perform(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().single { it.source == card(cactus) })
        paySuggested()
        val o = state.objOrThrow(card(cactus))
        assertEquals(Zone.Exile, o.zone)
        assertTrue(ObjectMark.Plotted in o.marks)
    }

    // CR 601.2f, 700.13: without a crime this turn it costs {2}{U}
    @Test
    fun withoutACrimeTheSpellCostsFull() = scenario {
        alice.battlefield("Island", "Island")
        alice.library("Island", "Island")
        alice.hand(ledger)
        alice.casts(ledger)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty(), "two lands can't pay {2}{U}")
    }

    // CR 601.2f, 700.13: after a crime this turn it costs {1}{U}
    @Test
    fun afterACrimeTheSpellCostsLess() = scenario {
        alice.battlefield("Island", "Island")
        alice.library("Island", "Island")
        alice.hand(spark, ledger)
        alice.castsWithoutCosts(spark, target = "bob")
        resolveAll()
        alice.casts(ledger)
        paySuggested()
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 601.2f: an opponent's spell targeting it costs 3 life more; one targeting another creature doesn't
    @Test
    fun opponentsSpellsTargetingItCostLife() = scenario {
        alice.battlefield(dread, longhorn)
        bob.battlefield("Mountain", "Mountain")
        bob.hand(spark, spark)
        turn(number = 2, active = bob)
        bob.casts(spark, target = dread)
        paySuggested()
        resolveAll()
        expect { bob.life shouldBe 17 }
        bob.casts(spark, target = longhorn)
        paySuggested()
        resolveAll()
        expect {
            bob.life shouldBe 17
            longhorn.view.obj?.damage shouldBe 1
            dread.view.obj?.damage shouldBe 1
        }
    }

    // CR 608.2h: the entering creature's power
    @Test
    fun anotherCreatureEnteringDealsItsPower() = scenario(players = 3) {
        alice.battlefield(dread)
        alice.hand(longhorn)
        alice.castsWithoutCosts(longhorn)
        playOut("carol")
        expect {
            carol.life shouldBe 17
            bob.life shouldBe 20
        }
    }

    // CR 608.2g, 601.2h: only the damaged player's graveyard; cast with mana of any type, under your control
    @Test
    fun combatDamageCastsFromThatPlayersGraveyard() = scenario(players = 3) {
        alice.battlefield(twigbones, "Swamp", "Swamp")
        bob.graveyard(bushwhack, wrangler, "Forest")
        carol.graveyard(longhorn)
        unblocked(twigbones)
        advanceTo(TurnStep.CombatDamage)
        val d = decision<Decision.Targets>()
        assertEquals(setOf(target(bushwhack), target(wrangler)), d.options.toSet(), "only nonland permanent cards in the damaged player's graveyard")
        targets(bushwhack)
        resolveAll()
        may(true)
        paySuggested()
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.battlefield has bushwhack
        }
    }

    // CR 601.2a, 608.2g: a spell cast from a graveyard lets a permanent card of equal or lesser mana value be cast free from the hand
    @Test
    fun aSpellFromTheGraveyardCastsAPermanentFree() = scenario {
        alice.battlefield(kip, "Mountain")
        alice.graveyard(recollection)
        alice.hand(wrangler, longhorn, "Forest")
        alice.casts(recollection, target = "bob")
        paySuggested()
        resolveAll()
        val choice = decision<Decision.ChooseObjects>()
        assertEquals(listOf(card(wrangler)), choice.options, "only the permanent card of mana value 1 or less")
        answer(Answer.Objects(listOf(card(wrangler))))
        resolveAll()
        expect {
            alice.battlefield has wrangler
            alice.hand has "Forest"
            bob.life shouldBe 19
        }
    }

    // CR 608.2c: "if you don't" cast one, a land card may be put onto the battlefield
    @Test
    fun withoutAFreeCastALandMayBePutOntoTheBattlefield() = scenario {
        alice.battlefield(kip, "Mountain")
        alice.graveyard(recollection)
        alice.hand(longhorn, "Forest")
        alice.casts(recollection, target = "bob")
        paySuggested()
        resolveAll()
        answer(Answer.Objects(listOf(card("Forest"))))
        resolveAll()
        expect {
            alice.battlefield has "Forest"
            alice.hand has longhorn
        }
    }

    // CR 614.1a, 611.2a: until end of turn, counters you put on a creature you control are one more; an opponent's aren't
    @Test
    fun countersYouPutAreOneMoreUntilEndOfTurn() = scenario {
        alice.battlefield(marmot, wrangler)
        alice.hand(blessing)
        alice.activatesWithoutCosts(marmot, ability = 0)
        resolveAll()
        alice.castsWithoutCosts(blessing, target = wrangler)
        resolveAll()
        expect { wrangler.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2 }
    }
}
