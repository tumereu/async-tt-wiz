package arcanum.engine.sets.woe

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ecl.paySuggested
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * WOE chunk 11: the invented names carry the oracle text of Gnawing Crescendo, Tattered Ratter, Territorial Witchstalker, Cheeky
 * House-Mouse, Rat Out and Dream Spoilers.
 */
class WoeChunk11ApplyScenariosTest {
    private val crescendo = Cards.invent("Rousing Crescendo", "{2}{R}", "Instant",
        "Creatures you control get +2/+0 until end of turn. Whenever a nontoken creature you control dies this turn, create a 1/1 black Rat creature token with \"This token can't block.\"")
    private val ratcatcher = Cards.invent("Tattered Ratcatcher", "{1}{R}", "Creature — Human Peasant", "Whenever a Rat you control becomes blocked, it gets +2/+0 until end of turn.", "2/2")
    private val hornstalker = Cards.invent("Territorial Hornstalker", "{1}{G}", "Creature — Wolf", """
        Defender
        At the beginning of combat on your turn, if you control a creature with power 4 or greater, this creature gets +1/+0 until end of turn and can attack this turn as though it didn't have defender.
    """, "2/3")
    private val mouse = Cards.inventFaces("Adventure",
        face("Whisker Quick", "{W}", "Creature — Mouse", "", "2/1", null, null),
        face("Squeak Past", "{W}", "Sorcery — Adventure",
            "Target creature you control gets +1/+1 until end of turn. It can't be blocked by creatures with power 3 or greater this turn.", null, null, null))
    private val ratRun = Cards.invent("Rat Run", "{B}", "Instant",
        "Up to one target creature gets -1/-1 until end of turn. You create a 1/1 black Rat creature token with \"This token can't block.\"")
    private val wreckers = Cards.invent("Dream Wreckers", "{3}{B}", "Creature — Faerie Warlock", """
        Flying
        Whenever you cast a spell during an opponent's turn, up to one target creature an opponent controls gets -1/-1 until end of turn.
    """, "2/2")

    private val jolt = Cards.invent("Quarrybolt Jolt", "{R}", "Instant", "Quarrybolt Jolt deals 3 damage to target creature.")
    private val cellarRat = Cards.invent("Cellar Rat", "{1}", "Creature — Rat", "", "1/1")
    private val squire = Cards.invent("Moss Squire", "{1}{W}", "Creature — Human Knight", "", "2/2")
    private val ogre = Cards.invent("Barrow Ogre", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val boar = Cards.invent("Thicket Boar", "{2}{G}", "Creature — Boar", "", "3/3")
    private val hare = Cards.invent("Briar Hare", "{G}", "Creature — Rabbit", "", "1/1")

    // CR 603.7 (delayed trigger lasts the turn), 603.2 ("whenever": every death), 700.4: two nontoken creatures die, two Rats
    @Test
    fun rousingCrescendoMakesARatForEachNontokenCreatureThatDies() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", squire, hare)
        alice.hand(crescendo, jolt, jolt)
        alice.casts(crescendo)
        resolveAll()
        expect { squire.power shouldBe 4 }
        alice.casts(jolt, target = squire)
        resolveAll()
        alice.casts(jolt, target = hare)
        resolveAll()
        assertEquals(2, tokens("Rat Token").size)
        expect { squire.zone shouldBe Zone.Graveyard }
    }

    // CR 603.7: the delayed trigger ends with the turn: no Rat for a creature dying later
    @Test
    fun rousingCrescendoEndsWithTheTurn() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", squire)
        alice.hand(crescendo, jolt)
        alice.casts(crescendo)
        resolveAll()
        advanceTo(TurnStep.End)
        alice.casts(jolt, target = squire)
        resolveAll()
        assertEquals(1, tokens("Rat Token").size)
    }

    // CR 509.1h, 603.2: a Rat of yours becoming blocked gets +2/+0, so it kills a 3/3 blocker; a non-Rat doesn't trigger
    @Test
    fun tatteredRatcatcherPumpsABlockedRat() = scenario {
        alice.battlefield(ratcatcher, cellarRat)
        bob.battlefield(boar)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cellarRat)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(boar to cellarRat)
        resolveAll()
        expect { cellarRat.power shouldBe 3 }
        advanceTo(TurnStep.EndOfCombat)
        expect { boar.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun tatteredRatcatcherIgnoresABlockedNonRat() = scenario {
        alice.battlefield(ratcatcher, squire)
        bob.battlefield(boar)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(squire)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(boar to squire)
        resolveAll()
        expect { squire.power shouldBe 2 }
    }

    // CR 603.4 (intervening if), 702.3b, 508.1c: with a power-4 creature the Witchstalker may attack as though it had no defender
    @Test
    fun territorialHornstalkerAttacksWithAPowerFourCreature() = scenario {
        alice.battlefield(hornstalker, ogre)
        bob.battlefield(hare)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { hornstalker.power shouldBe 3 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hornstalker)
        playOut()
        expect { hornstalker.view.obj?.tapped shouldBe true }
    }

    @Test
    fun territorialHornstalkerCannotAttackWithoutOne() = scenario {
        alice.battlefield(hornstalker, squire)
        bob.battlefield(hare)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { hornstalker.power shouldBe 2 }
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(hornstalker) }
    }

    // CR 509.1b: a power-3 creature can't block the pumped creature; a power-2 one still can
    @Test
    fun squeakPastKeepsPowerThreeCreaturesFromBlocking() = scenario {
        alice.battlefield("Plains", squire)
        alice.hand(mouse)
        bob.battlefield(boar, hare)
        alice.casts("Squeak Past", target = squire, way = CastWay(face = 1))
        resolveAll()
        expect { squire.power shouldBe 3 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(squire)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(boar to squire) }
        bob.blocks(hare to squire)
    }

    // CR 115.1b, 608.2c: with no target for the "up to one" slot, the token is still created
    @Test
    fun ratRunCreatesTheRatWithoutATarget() = scenario {
        alice.battlefield("Swamp")
        alice.hand(ratRun)
        bob.battlefield(boar)
        alice.casts(ratRun)
        if (pending is Decision.Targets) answer(Answer.Targets(emptyList()))
        paySuggested()
        resolveAll()
        assertEquals(1, tokens("Rat Token").size)
        expect { boar.power shouldBe 3 }
    }

    @Test
    fun ratRunShrinksItsTargetAndCreatesTheRat() = scenario {
        alice.battlefield("Swamp")
        alice.hand(ratRun)
        bob.battlefield(hare)
        alice.casts(ratRun, target = hare)
        resolveAll()
        assertEquals(1, tokens("Rat Token").size)
        expect { hare.zone shouldBe Zone.Graveyard }
    }

    // CR 603.2, 102.2 (three players): only a spell cast during an opponent's turn triggers it; the target is an opponent's creature
    @Test
    fun dreamWreckersShrinksOnlyDuringAnOpponentsTurn() = scenario(players = 3) {
        alice.battlefield("Mountain", wreckers)
        alice.hand(jolt)
        bob.battlefield(hare, boar)
        turn(number = 2, active = bob)
        bob.passes()
        carol.passes()
        alice.casts(jolt, target = boar)
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(hare))))
        resolveAll()
        expect {
            hare.zone shouldBe Zone.Graveyard
            boar.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun dreamWreckersIgnoresSpellsOnYourOwnTurn() = scenario {
        alice.battlefield("Mountain", wreckers)
        alice.hand(jolt)
        bob.battlefield(hare, boar)
        alice.casts(jolt, target = boar)
        resolveAll()
        expect { hare.zone shouldBe Zone.Battlefield }
    }
}
