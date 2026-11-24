package arcanum.engine.sets.vow

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * VOW bug-class sweep: siblings of the engine and parser fixes VOW made one card at a time (a lasting effect's player found live instead of
 * as it resolves, an ability moving its card out of the graveyard working only in some wordings, "this way" finding a card only where it was
 * meant to go, an exile path not keeping its cards as "exiled with" this object, a reflexive or "if you do" step after an optional action
 * inside a condition, "dealt damage this way" counting prevented damage). Invented cards with oracle wordings.
 */
class VowSweepScenariosTest {
    private val ogre = Cards.invent("VowSweep Hill Ogre", "{3}{R}", "Creature — Ogre", "", "3/3")
    private val bear = Cards.invent("VowSweep Moss Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    private val hush = Cards.invent("VowSweep Hushing Grip", "{1}{U}", "Instant", "Tap target creature. Its controller can't cast spells this turn.")
    private val clasp = Cards.invent("VowSweep Borrowed Clasp", "{U}", "Instant", "Gain control of target creature until end of turn.")
    private val spark = Cards.invent("VowSweep Spare Spark", "{R}", "Instant", "VowSweep Spare Spark deals 1 damage to each opponent.")

    // CR 608.2h, 611.2c: "its controller" is the player who controlled the creature as the spell resolved; taking the creature afterwards
    // doesn't move the restriction to its new controller
    @Test
    fun aRestrictionOnTheTargetsControllerStaysWithThatPlayer() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Mountain")
        alice.hand(hush, clasp, spark)
        bob.battlefield(ogre)
        alice.casts(hush, target = ogre)
        resolveAll()
        alice.casts(clasp, target = ogre)
        resolveAll()
        expect { ogre.controller shouldBe alice.id }
        assertEquals(alice.id, state.priority.holder)
        assertEquals(1, Engine.options(state, alice.id).filterIsInstance<Input.Cast>().count { it.card == card(spark) })
    }

    private val revenant = Cards.invent("VowSweep Lantern Revenant", "{2}{B}", "Creature — Spirit",
        "At the beginning of your upkeep, you may put this card from your graveyard on top of your library.", "2/2")

    // CR 113.6m, 603.6: a triggered ability that moves its card out of the graveyard works only there: on the battlefield it doesn't trigger
    @Test
    fun aGraveyardTriggerDoesntTriggerOnTheBattlefield() = scenario {
        alice.battlefield(revenant)
        players.forEach { it.library("Island", "Island", "Island") }
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertTrue(state.stack.isEmpty() && pending == null, "Nothing should trigger: ${state.stack} $pending")
        expect { revenant.zone shouldBe Zone.Battlefield }
    }

    // CR 113.6m: from the graveyard it triggers and the card goes on top of the library
    @Test
    fun aGraveyardTriggerWorksInTheGraveyard() = scenario {
        alice.graveyard(revenant)
        players.forEach { it.library("Island", "Island", "Island") }
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        (pending as? Decision.May)?.let { answer(Answer.YesNo(true)) }
        resolveAll()
        expect { revenant.zone shouldBe Zone.Library }
    }

    private val sift = Cards.invent("VowSweep Sift the Bones", "{U}", "Sorcery", "Discard a card. If a creature card is discarded this way, draw a card.")
    private val ashen = Cards.invent("VowSweep Ashen Seal", "{1}{W}", "Enchantment", "If a card would be put into a graveyard from anywhere, exile it instead.")

    // CR 701.9c, 614.6: a card discarded into exile instead of the graveyard was still discarded; it is found where it went
    @Test
    fun aCreatureDiscardedIntoExileStillCounts() = scenario {
        alice.battlefield(ashen, "Island")
        alice.hand(sift, bear)
        alice.library("Plains", "Plains")
        alice.casts(sift)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Exile
            alice.hand.size shouldBe 1
        }
    }

    private val linked = "As long as there are two or more cards exiled with this creature, it gets +2/+2."
    private val graveRobber = Cards.invent("VowSweep Grave Robber", "{2}{B}", "Creature — Zombie", "{T}: Exile target player's graveyard.\n$linked", "1/1")
    private val topTaker = Cards.invent("VowSweep Top Taker", "{2}{U}", "Creature — Human Rogue", "{T}: Exile the top card of each player's library.\n$linked", "1/1")
    private val handTaker = Cards.invent("VowSweep Hand Taker", "{2}{B}", "Creature — Vampire", "{T}: Each opponent exiles a card from their hand.\n$linked", "1/1")
    private val boneTaker = Cards.invent("VowSweep Bone Taker", "{2}{B}", "Creature — Zombie", "{T}: Exile all creature cards from target player's graveyard.\n$linked", "1/1")

    // CR 607.2a: the cards its activated ability exiles are linked to its "cards exiled with this creature"
    @Test
    fun exilingAGraveyardKeepsTheCardsAsExiledWithIt() = scenario {
        alice.battlefield(graveRobber)
        bob.graveyard("Island", "Plains")
        alice.activates(graveRobber, target = "bob")
        resolveAll()
        expect { graveRobber.power shouldBe 3 }
    }

    // CR 607.2a: the same for the top card of each player's library
    @Test
    fun exilingEachPlayersTopCardKeepsThemAsExiledWithIt() = scenario {
        alice.battlefield(topTaker)
        players.forEach { it.library("Island", "Island") }
        alice.activates(topTaker)
        resolveAll()
        expect { topTaker.power shouldBe 3 }
    }

    // CR 607.2a: the same for a card each opponent exiles from their hand
    @Test
    fun eachOpponentsExiledCardIsExiledWithIt() = scenario(players = 3) {
        alice.battlefield(handTaker)
        bob.hand("Island")
        carol.hand("Plains")
        alice.activates(handTaker)
        resolveAll()
        while (pending is Decision.ChooseObjects) {
            val d = pending as Decision.ChooseObjects
            answer(Answer.Objects(d.options.take(1)))
            resolveAll()
        }
        expect { handTaker.power shouldBe 3 }
    }

    // CR 607.2a: the same for every creature card of a graveyard
    @Test
    fun exilingAGraveyardsCreatureCardsKeepsThemAsExiledWithIt() = scenario {
        alice.battlefield(boneTaker)
        bob.graveyard(ogre, bear, "Island")
        alice.activates(boneTaker, target = "bob")
        resolveAll()
        expect { boneTaker.power shouldBe 3 }
    }

    private val wager = Cards.invent("VowSweep Grim Wager", "{B}", "Sorcery", "If you control an artifact, you may sacrifice a creature. When you do, draw two cards.")
    private val study = Cards.invent("VowSweep Restless Study", "{U}", "Sorcery", "Draw a card. If you control an artifact, you may discard a card. If you do, draw a card.")
    private val trinket = Cards.invent("VowSweep Brass Trinket", "{1}", "Artifact", "")

    // CR 603.12: the reflexive trigger needs the optional sacrifice done; declined, nothing is drawn
    @Test
    fun decliningTheConditionalSacrificeDrawsNothing() = scenario {
        alice.battlefield(trinket, bear, "Swamp")
        alice.hand(wager)
        alice.library("Island", "Island", "Island")
        alice.casts(wager)
        resolveAll()
        (pending as? Decision.May)?.let { answer(Answer.YesNo(false)) }
        resolveAll()
        expect {
            alice.hand.size shouldBe 0
            bear.zone shouldBe Zone.Battlefield
        }
    }

    // CR 608.2c: "If you do" needs the optional discard done; declined, only the first card is drawn
    @Test
    fun decliningTheConditionalDiscardDrawsOnlyOne() = scenario {
        alice.battlefield(trinket, "Island")
        alice.hand(study)
        alice.library("Plains", "Plains", "Plains")
        alice.casts(study)
        resolveAll()
        (pending as? Decision.May)?.let { answer(Answer.YesNo(false)) }
        (pending as? Decision.ChooseObjects)?.let { answer(Answer.Objects(emptyList())) }
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    private val brand = Cards.invent("VowSweep Searing Brand", "{R}", "Instant",
        "VowSweep Searing Brand deals 2 damage to target creature. If a creature dealt damage this way would die this turn, exile it instead.")
    private val warded = Cards.invent("VowSweep Warded Ox", "{2}{W}", "Creature — Ox", "Prevent all damage that would be dealt to this creature.", "1/1")
    private val doom = Cards.invent("VowSweep Swift Doom", "{B}", "Instant", "Destroy target creature.")

    // CR 615.1a, 120.1: the prevented damage wasn't dealt, so the creature dying later this turn goes to the graveyard
    @Test
    fun aCreatureWhoseDamageWasPreventedIsntExiledWhenItDies() = scenario {
        alice.battlefield("Mountain", "Swamp")
        alice.hand(brand, doom)
        bob.battlefield(warded)
        alice.casts(brand, target = warded)
        resolveAll()
        alice.casts(doom, target = warded)
        resolveAll()
        expect { warded.zone shouldBe Zone.Graveyard }
    }

    // CR 614.1a: a creature dealt the damage is exiled when it dies later this turn
    @Test
    fun aCreatureDealtTheDamageIsExiledWhenItDies() = scenario {
        alice.battlefield("Mountain", "Swamp")
        alice.hand(brand, doom)
        bob.battlefield(ogre)
        alice.casts(brand, target = ogre)
        resolveAll()
        alice.casts(doom, target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Exile }
    }
}
