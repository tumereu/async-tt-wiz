package arcanum.engine.sets.inr

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.TargetRef
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** INR chunk 01: transforming double-faced cards in play: the werewolves' "last turn" conditions (one player's casts), a mana
 * delayed trigger for this turn, "each player has 10 or less life", look-then-reveal, a planeswalker's state trigger, and a
 * creature target that depends on a player-or-planeswalker target. */
class InrTransformScenariosTest {
    private val folk = Cards.inventFaces("Transform",
        face("Folk of Marrowdale", "{2}{G}", "Creature — Human Werewolf",
            "At the beginning of each upkeep, if no spells were cast last turn, transform this creature.", "2/3", null, null),
        face("Pack of Marrowdale", null, "Creature — Werewolf",
            "At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.", "4/6", null, "G"))
    private val channeler = Cards.inventFaces("Transform",
        face("Stormroad Channeler", "{2}{R}", "Creature — Werewolf Horror", """
            Whenever this creature attacks, add {R} at the beginning of your next main phase this turn.
            {3}{R}{R}: Transform this creature.
        """.trimIndent(), "2/3", null, null),
        face("Channeler of the Void", null, "Creature — Eldrazi Werewolf",
            "Whenever this creature attacks, add {C}{C} at the beginning of your next main phase this turn.", "5/4", null, "R"))
    private val relic = Cards.inventFaces("Transform",
        face("Shardstone Relic", "{3}", "Artifact", """
            This artifact enters tapped.
            {T}: Add one mana of any color. Each player loses 1 life.
            At the beginning of your upkeep, if each player has 10 or less life, transform this artifact.
        """.trimIndent(), null, null, null),
        face("Aurora of the Deep", null, "Creature — Eldrazi Reflection", """
            Flying, deathtouch
            Whenever this creature attacks, each opponent loses 3 life.
        """.trimIndent(), "1/4", null, null))
    private val adept = Cards.inventFaces("Transform",
        face("Tomesifter Adept", "{U}", "Creature — Human Wizard",
            "At the beginning of your upkeep, look at the top card of your library. You may reveal that card. If an instant or sorcery card is revealed this way, transform this creature.",
            "1/1", null, null),
        face("Winged Tomesifter", null, "Creature — Human Insect", "Flying", "3/2", null, "U"))
    private val tovar = Cards.inventFaces("Transform",
        face("Tovar Unchained", "{3}{G}", "Legendary Planeswalker — Garruk", """
            When Tovar has two or fewer loyalty counters on him, transform him.
            [0]: Tovar deals 3 damage to target creature. That creature deals damage equal to its power to him.
            [0]: Create a 2/2 green Wolf creature token.
        """.trimIndent(), null, "3", null),
        face("Tovar, the Grave-Bound", null, "Legendary Planeswalker — Garruk", """
            [+1]: Create a 1/1 black Wolf creature token with deathtouch.
            [−1]: Sacrifice a creature. If you do, search your library for a creature card, reveal it, put it into your hand, then shuffle.
            [−3]: Creatures you control gain trample and get +X/+X until end of turn, where X is the number of creature cards in your graveyard.
        """.trimIndent(), null, null, "BG"))
    private val packmaster = Cards.inventFaces("Transform",
        face("Packmaster of the Moors", "{2}{R}{G}", "Creature — Human Werewolf", """
            Whenever this creature enters or transforms into Packmaster of the Moors, create a 2/2 green Wolf creature token and you gain 2 life.
            At the beginning of each upkeep, if no spells were cast last turn, transform this creature.
        """.trimIndent(), "2/2", null, null),
        face("Despoiler of the Moors", null, "Creature — Werewolf", """
            Trample
            Whenever this creature transforms into Despoiler of the Moors, it deals 2 damage to target opponent or planeswalker and 2 damage to up to one target creature that player or that planeswalker's controller controls.
            At the beginning of each upkeep, if a player cast two or more spells last turn, transform this creature.
        """.trimIndent(), "4/4", null, "RG"))
    private val board = Cards.inventFaces("Transform",
        face("Spiritreader Board", "{1}{U}", "Artifact", """
            This artifact enters with three omen counters on it.
            {1}{U}, {T}: Look at the top two cards of your library. Put one of them into your graveyard.
            Whenever a creature card is put into your graveyard from anywhere, remove an omen counter from this artifact. Then if it has no omen counters on it, transform it.
        """.trimIndent(), null, null, null),
        face("Boardbound Spirit", null, "Creature — Spirit", """
            Flying
            This creature can block only creatures with flying.
            {3}{U}: Draw two cards, then discard a card.
        """.trimIndent(), "3/2", null, "U"))
    private val zap = Cards.invent("Moorland Zap", "{R}", "Instant", "Moorland Zap deals 1 damage to any target.")
    private val idea = Cards.invent("Moorland Notion", "{U}", "Instant", "Draw a card.")
    private val bear = Cards.invent("Moorland Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Moorland Ox", "{3}{G}", "Creature — Ox", "", "2/4")
    private val stag = Cards.invent("Moorland Stag", "{G}", "Creature — Elk", "", "1/1")
    private val seer = Cards.invent("Orla, Moorland Seer", "{2}{U}", "Legendary Planeswalker — Jace", "[+1]: Draw a card.", loyalty = "3")

    private fun Scenario.face(name: String): Int? = state.obj(card(name))?.face

    // CR 603.4: "if no spells were cast last turn" holds after a turn without spells
    @Test
    fun aWerewolfTransformsAfterATurnWithoutSpells() = scenario {
        alice.battlefield(folk)
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        assertEquals(1, face(folk))
        expect { folk.power shouldBe 4 }
    }

    // CR 603.4: a spell cast last turn by anyone, on either player's turn, keeps it from transforming
    @Test
    fun aWerewolfStaysAfterASpellLastTurn() = scenario {
        alice.battlefield(folk, "Island")
        alice.hand(idea)
        alice.library(bear, bear, bear)
        bob.library(bear, bear)
        alice.casts(idea)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        assertEquals(0, face(folk))
    }

    /** The back face transforms back after one player cast two spells. */
    // CR 603.4, 701.27a
    @Test
    fun theBackTransformsAfterOnePlayerCastTwoSpells() = scenario(players = 3) {
        alice.battlefield(folk, "Island", "Island")
        alice.hand(idea, idea)
        alice.library(bear, bear, bear, bear)
        bob.library(bear, bear)
        carol.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        assertEquals(1, face(folk))
        advanceTo(TurnStep.Upkeep, turn = 4)
        resolveAll()
        advanceTo(TurnStep.PrecombatMain, turn = 4)
        alice.casts(idea)
        resolveAll()
        alice.casts(idea)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 5)
        resolveAll()
        assertEquals(0, face(folk))
    }

    /** Two spells last turn by two different players are not "a player cast two or more spells". */
    // CR 603.4
    @Test
    fun twoPlayersCastingOneSpellEachDontTransformTheBack() = scenario(players = 3) {
        alice.battlefield(folk, "Island")
        bob.battlefield("Mountain")
        alice.hand(idea)
        bob.hand(zap)
        alice.library(bear, bear, bear, bear)
        bob.library(bear, bear)
        carol.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        assertEquals(1, face(folk))
        advanceTo(TurnStep.PrecombatMain, turn = 4)
        alice.casts(idea)
        resolveAll()
        alice.passes()
        bob.casts(zap, target = "carol")
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 5)
        resolveAll()
        assertEquals(1, face(folk))
    }

    // CR 603.7b, 106.4: the delayed trigger adds the mana when the next main phase of this turn begins
    @Test
    fun theChannelerAddsManaInTheNextMainPhase() = scenario {
        alice.battlefield(channeler)
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(channeler)
        resolveAll()
        expect { alice.pool.size shouldBe 0 }
        advanceTo(TurnStep.PostcombatMain, turn = 3)
        resolveAll()
        expect { alice.pool.map { it.color } shouldBe listOf(Color.Red) }
    }

    private fun Scenario.relicAtUpkeep(carolLife: Int) {
        alice.battlefield(relic)
        alice.library(bear, bear)
        bob.library(bear, bear)
        carol.library(bear, bear)
        alice.life = 10
        bob.life = 9
        carol.life = carolLife
        advanceTo(TurnStep.Upkeep, turn = 4)
        resolveAll()
    }

    /** One player above 10 life keeps the relic from transforming. */
    // CR 603.4
    @Test
    fun theRelicStaysWhileOnePlayerHasMoreThanTen() = scenario(players = 3) {
        relicAtUpkeep(carolLife = 11)
        assertEquals(0, face(relic))
    }

    /** With each player at 10 or less, it transforms. */
    // CR 603.4
    @Test
    fun theRelicTransformsWhenEachPlayerHasTenOrLess() = scenario(players = 3) {
        relicAtUpkeep(carolLife = 10)
        assertEquals(1, face(relic))
    }

    // CR 701.20a: a revealed instant transforms it
    @Test
    fun theAdeptTransformsWhenItRevealsAnInstant() = scenario {
        alice.battlefield(adept)
        alice.library(idea, bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        assertEquals(1, face(adept))
        expect { alice.library.names.first() shouldBe idea }
    }

    /** A revealed creature card doesn't transform it. */
    // CR 701.20a
    @Test
    fun theAdeptStaysWhenItRevealsACreature() = scenario {
        alice.battlefield(adept)
        alice.library(bear, bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        assertEquals(0, face(adept))
    }

    /** The creature's damage takes loyalty below three, so the state trigger transforms Tovar. */
    // CR 603.8, 120.3c
    @Test
    fun tovarTransformsAfterTheCreatureHitsBack() = scenario {
        alice.battlefield(tovar, counters = listOf(CounterKind.Loyalty to 3))
        bob.battlefield(ox)
        alice.activates(tovar, ability = 0, target = ox)
        resolveAll()
        expect {
            ox.zone shouldBe arcanum.api.Zone.Battlefield
            tovar.counters(CounterKind.Loyalty) shouldBe 1
        }
        assertEquals(1, face(tovar))
    }

    /** Tovar transformed by his own [0] ability, then the next turn of his controller. */
    private fun Scenario.graveBoundOnTurnThree(creature: Boolean) {
        alice.battlefield(tovar, counters = listOf(CounterKind.Loyalty to 3))
        if (creature) alice.battlefield(bear)
        bob.battlefield(ox)
        alice.library(idea, idea, stag)
        bob.library(bear, bear)
        alice.activates(tovar, ability = 0, target = ox)
        resolveAll()
        assertEquals(1, face(tovar))
        advanceTo(TurnStep.PrecombatMain, turn = 3)
    }

    /** "Sacrifice a creature. If you do, search …": the search follows the sacrifice. */
    // CR 701.21a, 606.3
    @Test
    fun theGraveBoundSearchesAfterASacrifice() = scenario {
        graveBoundOnTurnThree(creature = true)
        alice.activates(tovar, ability = 1)
        resolveAll()
        alice.chooses(stag)
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.hand has stag
        }
    }

    /** With no creature to sacrifice, there is no search. */
    // CR 701.21a
    @Test
    fun theGraveBoundDoesntSearchWithoutASacrifice() = scenario {
        graveBoundOnTurnThree(creature = false)
        alice.activates(tovar, ability = 1)
        resolveAll()
        expect {
            alice.hand lacks stag
            alice.library has stag
        }
    }

    /** The creature target is chosen among the creatures of the targeted opponent only. */
    // CR 115.1, 701.27e
    @Test
    fun theDespoilerHitsAnOpponentAndOneOfTheirCreatures() = scenario(players = 3) {
        alice.battlefield(packmaster)
        bob.battlefield(bear, stag)
        carol.battlefield(ox)
        alice.library(idea, idea)
        bob.library(idea, idea)
        carol.library(idea, idea)
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        alice.answers(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        val creatures = decision<Decision.Targets>()
        assertEquals(setOf(card(bear), card(stag)), creatures.options.map { (it as TargetRef.Object).id }.toSet())
        alice.answers(Answer.Targets(listOf(TargetRef.Object(card(bear)))))
        resolveAll()
        assertEquals(1, face(packmaster))
        expect {
            bob.life shouldBe 18
            bob.graveyard has bear
        }
    }

    /** With a planeswalker as the first target, the creature is chosen among its controller's creatures. */
    // CR 115.1, 120.3c
    @Test
    fun theDespoilerHitsAPlaneswalkerAndOneOfItsControllersCreatures() = scenario(players = 3) {
        alice.battlefield(packmaster)
        bob.battlefield(bear)
        carol.battlefield(ox, stag)
        carol.battlefield(seer, counters = listOf(CounterKind.Loyalty to 3))
        alice.library(idea, idea)
        bob.library(idea, idea)
        carol.library(idea, idea)
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        alice.answers(Answer.Targets(listOf(TargetRef.Object(card(seer)))))
        val creatures = decision<Decision.Targets>()
        assertEquals(setOf(card(ox), card(stag)), creatures.options.map { (it as TargetRef.Object).id }.toSet())
        alice.answers(Answer.Targets(listOf(TargetRef.Object(card(stag)))))
        resolveAll()
        expect {
            seer.counters(CounterKind.Loyalty) shouldBe 1
            carol.graveyard has stag
            carol.life shouldBe 20
        }
    }

    /** One of the two looked-at cards goes to the graveyard, the other stays on top. */
    // CR 701.20e
    @Test
    fun theBoardPutsOneOfTwoIntoTheGraveyard() = scenario {
        alice.battlefield(board, "Island", "Island")
        alice.library(idea, zap, bear)
        alice.activates(board)
        resolveAll()
        alice.chooses(zap)
        resolveAll()
        expect {
            alice.graveyard has zap
            alice.library.names.first() shouldBe idea
        }
    }
}
