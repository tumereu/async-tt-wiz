package arcanum.engine.sets.tmt

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.view.AbilityOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Teenage Mutant Ninja Turtles mechanics in play: Mutagen tokens, sneak and what checks that its cost was paid, disappear, the card types
 * among spells cast this turn, creatures that crewed a Vehicle, an equip cost paid once each turn, and other new wordings.
 */
class TmtMechanicsScenariosTest {
    private val bear = Cards.invent("Sewer Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Sewer Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val zap = Cards.invent("Sewer Zap", "{R}", "Instant", "Sewer Zap deals 2 damage to any target.")
    private val trinket = Cards.invent("Sewer Trinket", "{1}", "Artifact", "")
    private val draw = Cards.invent("Sewer Notes", "{U}", "Sorcery", "Draw a card.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private val conscript = Cards.invent("Shellback Conscript", "{1}{U}", "Creature — Crab Mutant Soldier",
        "When this creature enters, create a Mutagen token. (It's an artifact with \"{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.\")", "0/3")
    private val vanguard = Cards.invent("Vanguard, Masked Leader", "{W}", "Legendary Creature — Mutant Ninja Turtle", """
        Sneak {3}{W}{W} (You may cast this spell for {3}{W}{W} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
        When Vanguard enters, if his sneak cost was paid, creatures you control get +2/+0 until end of turn.
        {1}{W}: Vanguard gains first strike until end of turn.
    """, "2/1")
    private val wanderer = Cards.invent("Lone Wanderer's Technique", "{3}{W}", "Instant", """
        Sneak {1}{W} (You may cast this spell for {1}{W} if you also return an unblocked attacker you control to hand during the declare blockers step.)
        Create three 1/1 white Ninja Turtle Spirit creature tokens. If this spell's sneak cost was paid, they enter tapped and attacking.
    """)
    private val defector = Cards.invent("Defector Kunoichi", "{2}{W}", "Creature — Mutant Ninja Fox", """
        Sneak {2}{W}{B} (You may cast this spell for {2}{W}{B} if you also return an unblocked attacker you control to hand during the declare blockers step. It enters tapped and attacking.)
        When this creature enters, choose target creature an opponent controls. Exile that creature until this creature leaves the battlefield. If this creature's sneak cost was paid, instead exile the chosen creature.
    """, "3/2")
    private val dice = Cards.invent("Dice, Game Master", "{2}{G}", "Legendary Creature — Mutant Ninja Turtle",
        "Disappear — At the beginning of your end step, if a permanent left the battlefield under your control this turn, put a +1/+1 counter on Dice.", "3/3")
    private val wren = Cards.invent("Wren, Firewall Hacktivist", "{3}{U}", "Legendary Creature — Human Scientist",
        "At the beginning of your end step, draw a card for each card type among spells you've cast this turn.", "1/5")
    private val robot = Cards.invent("Sewer Robot", "{2}", "Artifact Creature — Robot", "", "1/1")
    private val van = Cards.invent("Shell Van", "{3}", "Artifact — Vehicle", """
        Whenever this Vehicle attacks, put a +1/+1 counter on target creature that crewed it this turn. Then if that creature is a Mutant, Ninja, or Turtle, double the number of +1/+1 counters on it.
        Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """, "4/4")
    private val turtle = Cards.invent("Sewer Turtle", "{1}{G}", "Creature — Turtle", "", "2/2")
    private val armor = Cards.invent("Warlord's Bladed Armor", "{1}{B}", "Artifact — Equipment", """
        Equipped creature gets +2/+1.
        When this Equipment enters, attach it to target creature you control.
        Equip—Sacrifice another nonland permanent. Activate only once each turn.
    """)
    private val hawkwing = Cards.invent("Hawkwing, Death from Above", "{2}{W}", "Legendary Creature — Mutant Ninja Bird", """
        Flying
        When Hawkwing enters, exile up to one other target creature. At the beginning of the next end step, you may pay {3}{B}. If you don't, return that card to the battlefield under its owner's control.
    """, "2/1")
    private val shinobi = Cards.invent("Apprentice Shinobi", "{2}{B}", "Enchantment — Class", """
        (Gain the next level as a sorcery to add its ability.)
        Whenever a creature you control leaves the battlefield, each opponent loses 1 life.
        {1}{B}: Level 2
        Creatures you control get +1/+0 and have menace.
        {B}: Level 3
        Creature cards in your graveyard have sneak {3}{B}.
        You may cast creature spells from your graveyard using their sneak abilities.
    """)

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    /** The sneak alternative cost of [name] as it is now (CR 702.190a). */
    private fun Scenario.sneakOf(name: String): AbilityOrigin = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? arcanum.api.Ability.Static)?.mechanics?.any { it is arcanum.api.Mechanic.AlternativeCost && it.keyword == arcanum.api.Keyword.Sneak.Kind } == true
    }.origin

    /** Alice attacks with [attacker], Bob doesn't block: the declare blockers step, Alice holding priority. */
    private fun Scenario.unblocked(attacker: String) {
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(attacker)
        allPass()
        if (state.priority.holder == null) bob.blocks()
    }

    /** A Mutagen token is created; its ability puts a +1/+1 counter on a creature, only as a sorcery. */
    // CR 111.10v, 602.5d
    @Test
    fun mutagenTokenPutsACounter() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(conscript)
        bob.battlefield(bear)
        alice.casts(conscript)
        resolveAll()
        expect { alice.battlefield has "Mutagen Token" }
        alice.activates("Mutagen Token", target = conscript)
        resolveAll()
        expect {
            conscript.counters(p1p1) shouldBe 1
            alice.battlefield lacks "Mutagen Token"
        }
    }

    /** Sneak paid: the enters trigger's "if his sneak cost was paid" holds and creatures you control get +2/+0. */
    // CR 702.190a, 603.4
    @Test
    fun sneakPaidPumpsTheTeam() = scenario {
        alice.battlefield(bear, ogre, "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(vanguard)
        unblocked(bear)
        alice.casts(vanguard, way = CastWay(alternative = sneakOf(vanguard)), choosing = listOf(bear))
        resolveAll()
        expect {
            vanguard.tapped shouldBe true
            ogre.power shouldBe 5
            vanguard.power shouldBe 4
        }
    }

    /** Cast for its mana cost, the intervening "if" fails: no pump. */
    // CR 603.4
    @Test
    fun noSneakNoPump() = scenario {
        alice.battlefield(ogre, "Plains")
        alice.hand(vanguard)
        alice.casts(vanguard)
        resolveAll()
        expect { ogre.power shouldBe 3 }
    }

    /** The tokens enter tapped and attacking only when the sneak cost was paid. */
    // CR 508.4, 702.190a
    @Test
    fun sneakTechniqueTokensAttack() = scenario {
        alice.battlefield(bear, "Plains", "Plains")
        alice.hand(wanderer)
        unblocked(bear)
        alice.casts(wanderer, way = CastWay(alternative = sneakOf(wanderer)), choosing = listOf(bear))
        resolveAll()
        val spirits = state.battlefield.filter { nameOf(state.obj(it)!!) == "Ninja Turtle Spirit Token" }
        assertEquals(3, spirits.size)
        assertTrue(spirits.all { id -> state.obj(id)!!.tapped && state.combat!!.attackers.any { it.id == id } })
    }

    /** Sneak paid: the opponent's creature is exiled for good, and stays exiled when the Kunoichi leaves. */
    // CR 610.3
    @Test
    fun sneakExilesForGood() = scenario {
        alice.battlefield(bear, "Plains", "Plains", "Plains", "Swamp", "Mountain")
        alice.hand(defector, zap)
        bob.battlefield(ogre)
        unblocked(bear)
        alice.casts(defector, way = CastWay(alternative = sneakOf(defector)), choosing = listOf(bear))
        resolveAll()
        expect { ogre.zone shouldBe Zone.Exile }
        advanceTo(TurnStep.PostcombatMain)
        alice.casts(zap, target = defector)
        resolveAll()
        expect {
            defector.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Exile
        }
    }

    /** Cast normally, the creature returns when the Kunoichi leaves the battlefield. */
    // CR 610.3
    @Test
    fun withoutSneakTheExileEnds() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Mountain")
        alice.hand(defector, zap)
        bob.battlefield(ogre)
        alice.casts(defector)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Exile }
        alice.casts(zap, target = defector)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Battlefield }
    }

    /** Disappear: a permanent left the battlefield under your control this turn, so the end step trigger puts a counter. */
    // CR 603.4
    @Test
    fun disappearAfterAPermanentLeft() = scenario {
        alice.battlefield(dice, bear)
        move(bear, Zone.Graveyard)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { dice.counters(p1p1) shouldBe 1 }
    }

    /** Disappear: nothing left, no trigger. */
    // CR 603.4
    @Test
    fun noDisappearWhenNothingLeft() = scenario {
        alice.battlefield(dice, bear)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { dice.counters(p1p1) shouldBe 0 }
    }

    /** Card types among spells cast this turn: an artifact creature (two types) and a sorcery make three cards. */
    // CR 601.2i, 205.2a
    @Test
    fun cardTypesAmongSpellsCast() = scenario {
        alice.battlefield(wren, "Island", "Island", "Island")
        alice.hand(robot, draw)
        alice.library(bear, bear, bear, bear, bear)
        alice.casts(robot)
        resolveAll()
        alice.casts(draw)
        resolveAll()
        val before = state.player(alice.id).hand.size
        advanceTo(TurnStep.End)
        resolveAll()
        assertEquals(before + 3, state.player(alice.id).hand.size)
    }

    /** The creature that crewed the Van gets a counter as it attacks, doubled because it's a Turtle. */
    // CR 702.122c, 701.10e
    @Test
    fun counterOnTheCreatureThatCrewed() = scenario {
        alice.battlefield(van, turtle, bear)
        alice.activates(van, choosing = listOf(turtle))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(van)
        // the only legal target is the Turtle that crewed it, chosen at once
        resolveAll()
        expect {
            turtle.counters(p1p1) shouldBe 2
            bear.counters(p1p1) shouldBe 0
        }
    }

    /** Equip by sacrificing another nonland permanent, only once each turn. */
    // CR 702.6a, 602.5b
    @Test
    fun equipOnceEachTurn() = scenario {
        alice.battlefield(armor, bear, ogre, trinket, trinket)
        alice.activates(armor, target = bear, choosing = listOf(trinket))
        resolveAll()
        expect { bear.power shouldBe 4 }
        assertFailsWith<IllegalArgumentException> { alice.activates(armor, target = ogre, choosing = listOf(trinket)) }
    }

    /** Not paying {3}{B} at the next end step returns the exiled creature; paying keeps it exiled. */
    // CR 603.7, 118.12
    @Test
    fun payOrItReturns() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(hawkwing)
        bob.battlefield(ogre)
        alice.casts(hawkwing)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(ogre))))
        resolveAll()
        expect { ogre.zone shouldBe Zone.Exile }
        // CR 118.3: with no black mana the cost can't be paid, so nothing is asked and the creature returns
        advanceTo(TurnStep.End)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Battlefield }
    }

    /** Level 3: a creature card in the graveyard has sneak and can be cast from there with it. */
    // CR 702.190a, 601.2a
    @Test
    fun castFromTheGraveyardWithSneak() = scenario {
        alice.battlefield(shinobi, bear, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(ogre)
        alice.activates(shinobi, ability = 0)
        resolveAll()
        alice.activates(shinobi, ability = 1)
        resolveAll()
        unblocked(bear)
        alice.casts(ogre, way = CastWay(alternative = sneakOf(ogre)), choosing = listOf(bear))
        resolveAll()
        assertEquals(null, pending)
        expect {
            ogre.zone shouldBe Zone.Battlefield
            ogre.tapped shouldBe true
        }
    }
}
